package dev.redmi9i.files.storage

import android.content.Context
import android.content.Intent
import android.net.Uri
import java.io.InputStream
import java.io.OutputStream

/** Thrown when a write targets a removable volume that has no folder grant yet. */
class NeedsGrantException(val volumeUuid: String) : java.io.IOException("Grant access to this drive first")

/** `/storage/1234-ABCD/x` gives `1234-ABCD`; internal storage and non-volume paths give null. */
fun volumeUuidOf(path: String): String? {
    val parts = path.replace('\\', '/').split('/')
    if (parts.size < 3 || parts[0].isNotEmpty() || parts[1] != "storage") return null
    val v = parts[2]
    return if (v.isEmpty() || v == "emulated" || v == "self") null else v
}

/**
 * The one provider the app talks to (id `local`). Reads always use plain files for speed.
 * Writes under a removable volume are redirected to [SafFs] using the stored folder grant.
 */
class FsRouter(private val ctx: Context) {
    private val prefs = ctx.getSharedPreferences("grants", Context.MODE_PRIVATE)
    private val local = LocalFs()
    private val saf = SafFs(ctx) { uuid -> prefs.getString(uuid, null)?.let(Uri::parse) }
    val fs: FsProvider = Routing()

    fun fsOf(@Suppress("UNUSED_PARAMETER") id: String): FsProvider = fs

    fun hasGrant(uuid: String) = prefs.getString(uuid, null) != null

    /** Accepts only the root folder of the given volume; anything else is rejected. */
    fun grantTree(uuid: String, tree: Uri): Boolean {
        val docId = android.provider.DocumentsContract.getTreeDocumentId(tree)
        if (docId != "$uuid:") return false
        ctx.contentResolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        prefs.edit().putString(uuid, tree.toString()).apply()
        return true
    }

    private inner class Routing : FsProvider {
        override val id = "local"

        private fun writer(path: String): FsProvider {
            val uuid = volumeUuidOf(path) ?: return local
            if (!hasGrant(uuid)) throw NeedsGrantException(uuid)
            return saf
        }

        override suspend fun list(dir: FileRef) = local.list(dir)
        override suspend fun stat(ref: FileRef) = local.stat(ref)
        override fun openRead(ref: FileRef): InputStream = local.openRead(ref)
        override fun openWrite(ref: FileRef): OutputStream = writer(ref.path).openWrite(ref)
        override suspend fun mkdir(parent: FileRef, name: String) = writer(parent.path).mkdir(parent, name)
        override suspend fun createFile(parent: FileRef, name: String) = writer(parent.path).createFile(parent, name)
        override suspend fun delete(ref: FileRef) = writer(ref.path).delete(ref)
        override suspend fun rename(ref: FileRef, newName: String) = writer(ref.path).rename(ref, newName)
        override fun child(dir: FileRef, name: String) = local.child(dir, name)

        override fun supportsFastMove(from: FileRef, toDir: FileRef): Boolean =
            if (volumeUuidOf(from.path) == null && volumeUuidOf(toDir.path) == null) local.supportsFastMove(from, toDir)
            else saf.supportsFastMove(from, toDir)

        override suspend fun fastMove(from: FileRef, toDir: FileRef): FileRef =
            if (volumeUuidOf(from.path) == null) local.fastMove(from, toDir) else writer(from.path).fastMove(from, toDir)
    }
}
