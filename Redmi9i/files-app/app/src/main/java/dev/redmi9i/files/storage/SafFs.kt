package dev.redmi9i.files.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import android.webkit.MimeTypeMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/**
 * Write access to removable volumes through the Storage Access Framework. The external storage
 * provider uses document ids of the form `<volume uuid>:<relative/path>`, so a path such as
 * `/storage/1234-ABCD/Movies/a.mp4` maps straight to a document without walking the tree.
 */
class SafFs(private val ctx: Context, private val grants: (String) -> Uri?) : FsProvider {
    override val id = "saf"
    private val resolver: ContentResolver get() = ctx.contentResolver

    private class Target(val uuid: String, val tree: Uri, val rel: String) {
        val docId get() = "$uuid:$rel"
    }

    private fun target(ref: FileRef): Target {
        val uuid = volumeUuidOf(ref.path) ?: throw IOException("Not a removable volume: ${ref.path}")
        val tree = grants(uuid) ?: throw NeedsGrantException(uuid)
        val rel = ref.path.removePrefix("/storage/$uuid").trim('/')
        return Target(uuid, tree, rel)
    }

    private fun docUri(t: Target): Uri = DocumentsContract.buildDocumentUriUsingTree(t.tree, t.docId)

    private fun refOf(t: Target) = FileRef("local", "/storage/${t.uuid}" + if (t.rel.isEmpty()) "" else "/${t.rel}")

    override suspend fun list(dir: FileRef): List<FileInfo> = withContext(Dispatchers.IO) {
        val t = target(dir)
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(t.tree, t.docId)
        val out = mutableListOf<FileInfo>()
        resolver.query(uri, COLUMNS, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1)
                val isDir = c.getString(2) == Document.MIME_TYPE_DIR
                out += FileInfo(
                    FileRef("local", dir.path.trimEnd('/') + "/" + name), name, isDir,
                    if (isDir || c.isNull(3)) 0L else c.getLong(3), if (c.isNull(4)) 0L else c.getLong(4), name.startsWith("."),
                )
            }
        } ?: throw IOException("Cannot read ${dir.path}")
        out
    }

    override suspend fun stat(ref: FileRef): FileInfo? = withContext(Dispatchers.IO) {
        val t = target(ref)
        resolver.query(docUri(t), COLUMNS, null, null, null)?.use { c ->
            if (!c.moveToFirst()) return@use null
            val isDir = c.getString(2) == Document.MIME_TYPE_DIR
            FileInfo(
                refOf(t), c.getString(1), isDir, if (isDir || c.isNull(3)) 0L else c.getLong(3),
                if (c.isNull(4)) 0L else c.getLong(4), c.getString(1).startsWith("."),
            )
        }
    }

    override fun openRead(ref: FileRef): InputStream =
        resolver.openInputStream(docUri(target(ref))) ?: throw FileNotFoundException(ref.path)

    override fun openWrite(ref: FileRef): OutputStream =
        resolver.openOutputStream(docUri(target(ref)), "wt") ?: throw FileNotFoundException(ref.path)

    override suspend fun mkdir(parent: FileRef, name: String): FileRef = create(parent, name, Document.MIME_TYPE_DIR)

    override suspend fun createFile(parent: FileRef, name: String): FileRef {
        val ext = name.substringAfterLast('.', "").lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
        return create(parent, name, mime)
    }

    private suspend fun create(parent: FileRef, name: String, mime: String): FileRef = withContext(Dispatchers.IO) {
        val t = target(parent)
        val created = DocumentsContract.createDocument(resolver, docUri(t), mime, name)
            ?: throw IOException("Cannot create $name")
        // The provider may adjust the name; read the real one back.
        val real = resolver.query(created, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: name
        child(parent, real)
    }

    override suspend fun delete(ref: FileRef): Unit = withContext(Dispatchers.IO) {
        if (!DocumentsContract.deleteDocument(resolver, docUri(target(ref)))) throw IOException("Cannot delete ${ref.name}")
    }

    override suspend fun rename(ref: FileRef, newName: String): FileRef = withContext(Dispatchers.IO) {
        DocumentsContract.renameDocument(resolver, docUri(target(ref)), newName) ?: throw IOException("Cannot rename ${ref.name}")
        FileRef("local", ref.path.substringBeforeLast('/') + "/" + newName)
    }

    override fun child(dir: FileRef, name: String) = FileRef("local", dir.path.trimEnd('/') + "/" + name)

    override fun supportsFastMove(from: FileRef, toDir: FileRef): Boolean {
        val a = volumeUuidOf(from.path)
        return a != null && a == volumeUuidOf(toDir.path)
    }

    override suspend fun fastMove(from: FileRef, toDir: FileRef): FileRef = withContext(Dispatchers.IO) {
        val src = target(from)
        val srcParent = target(from.parent ?: throw IOException("No parent"))
        val dst = target(toDir)
        DocumentsContract.moveDocument(resolver, docUri(src), docUri(srcParent), docUri(dst))
            ?: throw IOException("Cannot move ${from.name}")
        child(toDir, from.name)
    }

    companion object {
        private val COLUMNS = arrayOf(
            Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE,
            Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED,
        )
    }
}
