package dev.redmi9i.files.storage

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException

/** [FsProvider] over `java.io.File`. */
class LocalFs : FsProvider {
    override val id = "local"

    private fun file(ref: FileRef) = File(ref.path)
    private fun ref(f: File) = FileRef(id, f.path.replace('\\', '/'))

    private fun info(f: File) = FileInfo(
        ref = ref(f),
        name = f.name,
        isDir = f.isDirectory,
        size = if (f.isDirectory) 0L else f.length(),
        modified = f.lastModified(),
        hidden = f.name.startsWith("."),
    )

    override suspend fun list(dir: FileRef): List<FileInfo> = withContext(Dispatchers.IO) {
        val children = file(dir).listFiles() ?: throw IOException("Cannot read ${dir.path}")
        children.map(::info)
    }

    override suspend fun stat(ref: FileRef): FileInfo? = withContext(Dispatchers.IO) {
        file(ref).takeIf { it.exists() }?.let(::info)
    }

    override fun openRead(ref: FileRef): InputStream = FileInputStream(file(ref))

    override fun openWrite(ref: FileRef): OutputStream = FileOutputStream(file(ref))

    override suspend fun mkdir(parent: FileRef, name: String): FileRef = withContext(Dispatchers.IO) {
        val target = File(file(parent), name)
        if (target.exists()) throw FileAlreadyExistsException(target.path)
        if (!target.mkdir()) throw IOException("Cannot create folder $name")
        ref(target)
    }

    override suspend fun createFile(parent: FileRef, name: String): FileRef = withContext(Dispatchers.IO) {
        val target = File(file(parent), name)
        if (!target.createNewFile()) throw FileAlreadyExistsException(target.path)
        ref(target)
    }

    override suspend fun delete(ref: FileRef): Unit = withContext(Dispatchers.IO) {
        val f = file(ref)
        if (f.exists() && !f.deleteRecursively()) throw IOException("Cannot delete ${f.name}")
    }

    override suspend fun rename(ref: FileRef, newName: String): FileRef = withContext(Dispatchers.IO) {
        val src = file(ref)
        val dst = File(src.parentFile, newName)
        if (dst.exists()) throw FileAlreadyExistsException(dst.path)
        if (!src.renameTo(dst)) throw IOException("Cannot rename ${src.name}")
        ref(dst)
    }

    override fun child(dir: FileRef, name: String) = FileRef(id, dir.path.trimEnd('/') + "/" + name)

    override fun supportsFastMove(from: FileRef, toDir: FileRef) =
        from.provider == id && toDir.provider == id && sameVolume(from.path, toDir.path)

    override suspend fun fastMove(from: FileRef, toDir: FileRef): FileRef = withContext(Dispatchers.IO) {
        val dst = File(file(toDir), file(from).name)
        if (dst.exists()) throw FileAlreadyExistsException(dst.path)
        if (!file(from).renameTo(dst)) throw IOException("Cannot move ${file(from).name}")
        ref(dst)
    }

    companion object {
        /** Internal storage and each SD/USB volume are separate filesystems; compare their roots. */
        fun volumeRoot(path: String): String {
            val p = path.replace('\\', '/')
            val parts = p.split('/')
            return when {
                p.startsWith("/storage/emulated/") && parts.size > 3 -> parts.take(4).joinToString("/")
                p.startsWith("/storage/") && parts.size > 2 -> parts.take(3).joinToString("/")
                else -> "/"
            }
        }

        fun sameVolume(a: String, b: String) = volumeRoot(a) == volumeRoot(b)
    }
}
