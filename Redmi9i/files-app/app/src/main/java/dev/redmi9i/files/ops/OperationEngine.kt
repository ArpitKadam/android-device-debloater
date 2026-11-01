package dev.redmi9i.files.ops

import dev.redmi9i.files.storage.FileInfo
import dev.redmi9i.files.storage.FileRef
import dev.redmi9i.files.storage.FsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.FilterInputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

enum class Conflict { SKIP, REPLACE, KEEP_BOTH }

fun interface ConflictResolver {
    suspend fun resolve(existing: FileInfo, incoming: FileInfo): Conflict
}

data class Progress(
    val doneBytes: Long,
    val totalBytes: Long,
    val currentName: String,
    val doneFiles: Int,
    val totalFiles: Int,
)

/** "a.txt" with {"a.txt"} taken becomes "a (1).txt"; picks the first free suffix. */
fun keepBothName(name: String, taken: Set<String>): String {
    if (name !in taken) return name
    val dot = name.lastIndexOf('.')
    val (stem, ext) = if (dot > 0) name.substring(0, dot) to name.substring(dot) else name to ""
    var n = 1
    while (true) {
        val candidate = "$stem ($n)$ext"
        if (candidate !in taken) return candidate
        n++
    }
}

/**
 * Copy, move, delete, zip and unzip across any [FsProvider]s. Every function is cancellable;
 * a cancelled or failed copy removes the partially written file.
 */
class OperationEngine(private val fsOf: (String) -> FsProvider) {

    private class Tracker(var totalBytes: Long, var totalFiles: Int, val onProgress: (Progress) -> Unit) {
        var doneBytes = 0L
        var doneFiles = 0
        var current = ""
        fun emit() = onProgress(Progress(doneBytes, totalBytes, current, doneFiles, totalFiles))
    }

    suspend fun copy(
        sources: List<FileRef>,
        destDir: FileRef,
        resolver: ConflictResolver,
        onProgress: (Progress) -> Unit,
    ) {
        val roots = sources.map { statOrThrow(it) }
        roots.forEach { rejectIntoItself(it, destDir) }
        val t = Tracker(0, 0, onProgress)
        roots.forEach { count(it, t) }
        for (root in roots) copyNode(root, destDir, resolver, t)
        t.emit()
    }

    suspend fun move(
        sources: List<FileRef>,
        destDir: FileRef,
        resolver: ConflictResolver,
        onProgress: (Progress) -> Unit,
    ) {
        val roots = sources.map { statOrThrow(it) }
        roots.forEach { rejectIntoItself(it, destDir) }
        val t = Tracker(0, 0, onProgress)
        roots.forEach { count(it, t) }
        val destFs = fsOf(destDir.provider)
        for (root in roots) {
            val srcFs = fsOf(root.ref.provider)
            val target = destFs.child(destDir, root.name)
            val free = destFs.stat(target) == null
            val moved = free && srcFs.supportsFastMove(root.ref, destDir) && try {
                t.current = root.name
                srcFs.fastMove(root.ref, destDir)
                t.doneFiles += fileCount(root)
                t.doneBytes += byteCount(root)
                t.emit()
                true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                false // provider cannot move in place (e.g. a document provider); copy instead
            }
            if (!moved && copyNode(root, destDir, resolver, t)) srcFs.delete(root.ref)
        }
        t.emit()
    }

    suspend fun delete(targets: List<FileRef>, onProgress: (Progress) -> Unit) {
        val t = Tracker(0, targets.size, onProgress)
        for (ref in targets) {
            currentCoroutineContext().ensureActive()
            t.current = ref.name
            fsOf(ref.provider).delete(ref)
            t.doneFiles++
            t.emit()
        }
    }

    suspend fun extractZip(zip: FileRef, destDir: FileRef, onProgress: (Progress) -> Unit) {
        val srcFs = fsOf(zip.provider)
        val destFs = fsOf(destDir.provider)
        val total = srcFs.stat(zip)?.size ?: 0L
        val t = Tracker(total, 0, onProgress)
        val counting = CountingInputStream(srcFs.openRead(zip)) { t.doneBytes = it }
        ZipInputStream(counting).use { zin ->
            var entry: ZipEntry? = zin.nextEntry
            while (entry != null) {
                currentCoroutineContext().ensureActive()
                val segments = safeSegments(entry.name)
                t.current = entry.name
                if (entry.isDirectory) {
                    ensureDirs(destFs, destDir, segments)
                } else {
                    val dir = ensureDirs(destFs, destDir, segments.dropLast(1))
                    val taken = destFs.list(dir).map { it.name }.toSet()
                    val name = keepBothName(segments.last(), taken)
                    val out = destFs.createFile(dir, name)
                    writeStream(destFs, out, zin, t)
                    t.doneFiles++
                }
                t.emit()
                zin.closeEntry()
                entry = zin.nextEntry
            }
        }
    }

    suspend fun compressZip(sources: List<FileRef>, destZip: FileRef, onProgress: (Progress) -> Unit) {
        val destFs = fsOf(destZip.provider)
        val roots = sources.map { statOrThrow(it) }
        val t = Tracker(0, 0, onProgress)
        roots.forEach { count(it, t) }
        var created = false
        try {
            destFs.createFile(destZip.parent!!, destZip.name)
            created = true
            ZipOutputStream(destFs.openWrite(destZip)).use { zout ->
                for (root in roots) addToZip(root, root.name, zout, t)
            }
        } catch (e: Throwable) {
            if (created) withContext(NonCancellable) { runCatching { destFs.delete(destZip) } }
            throw e
        }
        t.emit()
    }

    // ---- internals ----

    private suspend fun addToZip(node: FileInfo, entryName: String, zout: ZipOutputStream, t: Tracker) {
        currentCoroutineContext().ensureActive()
        val fs = fsOf(node.ref.provider)
        if (node.isDir) {
            zout.putNextEntry(ZipEntry("$entryName/"))
            zout.closeEntry()
            for (child in fs.list(node.ref)) addToZip(child, "$entryName/${child.name}", zout, t)
        } else {
            t.current = node.name
            zout.putNextEntry(ZipEntry(entryName))
            fs.openRead(node.ref).use { copyBytes(it, zout, t) }
            zout.closeEntry()
            t.doneFiles++
            t.emit()
        }
    }

    /** Returns true when the whole node was copied (nothing skipped), so a move may delete the source. */
    private suspend fun copyNode(src: FileInfo, destDir: FileRef, resolver: ConflictResolver, t: Tracker): Boolean {
        currentCoroutineContext().ensureActive()
        val srcFs = fsOf(src.ref.provider)
        val destFs = fsOf(destDir.provider)
        val existing = destFs.stat(destFs.child(destDir, src.name))

        if (src.isDir) {
            val target = when {
                existing != null && existing.isDir -> existing.ref
                existing != null -> when (resolver.resolve(existing, src)) {
                    Conflict.SKIP -> { skipCount(src, t); return false }
                    Conflict.REPLACE -> { destFs.delete(existing.ref); destFs.mkdir(destDir, src.name) }
                    Conflict.KEEP_BOTH -> destFs.mkdir(destDir, keepBothName(src.name, destFs.list(destDir).map { it.name }.toSet()))
                }
                else -> destFs.mkdir(destDir, src.name)
            }
            var all = true
            for (child in srcFs.list(src.ref)) if (!copyNode(child, target, resolver, t)) all = false
            return all
        }

        var name = src.name
        if (existing != null) {
            when (resolver.resolve(existing, src)) {
                Conflict.SKIP -> { skipCount(src, t); return false }
                Conflict.REPLACE -> destFs.delete(existing.ref)
                Conflict.KEEP_BOTH -> name = keepBothName(src.name, destFs.list(destDir).map { it.name }.toSet())
            }
        }
        t.current = src.name
        val out = destFs.createFile(destDir, name)
        try {
            srcFs.openRead(src.ref).use { input ->
                destFs.openWrite(out).use { output -> copyBytes(input, output, t) }
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) { runCatching { destFs.delete(out) } }
            throw e
        }
        t.doneFiles++
        t.emit()
        return true
    }

    private suspend fun writeStream(fs: FsProvider, out: FileRef, input: InputStream, t: Tracker) {
        try {
            fs.openWrite(out).use { copyBytes(input, it, t, countBytes = false) }
        } catch (e: Throwable) {
            withContext(NonCancellable) { runCatching { fs.delete(out) } }
            throw e
        }
    }

    private suspend fun copyBytes(input: InputStream, output: OutputStream, t: Tracker, countBytes: Boolean = true) {
        val buf = ByteArray(64 * 1024)
        while (true) {
            currentCoroutineContext().ensureActive()
            val n = input.read(buf)
            if (n < 0) break
            output.write(buf, 0, n)
            if (countBytes) t.doneBytes += n
            t.emit()
        }
    }

    private suspend fun count(node: FileInfo, t: Tracker) {
        if (node.isDir) {
            for (c in fsOf(node.ref.provider).list(node.ref)) count(c, t)
        } else {
            t.totalFiles++
            t.totalBytes += node.size
        }
    }

    private suspend fun fileCount(node: FileInfo): Int =
        if (node.isDir) fsOf(node.ref.provider).list(node.ref).sumOf { fileCount(it) } else 1

    private suspend fun byteCount(node: FileInfo): Long =
        if (node.isDir) fsOf(node.ref.provider).list(node.ref).sumOf { byteCount(it) } else node.size

    private fun skipCount(node: FileInfo, t: Tracker) {
        // Skipped data still counts as handled so the progress bar reaches 100%.
        t.doneFiles += 1
        t.doneBytes += node.size
        t.emit()
    }

    private suspend fun statOrThrow(ref: FileRef): FileInfo =
        fsOf(ref.provider).stat(ref) ?: throw java.io.FileNotFoundException(ref.path)

    private fun rejectIntoItself(src: FileInfo, destDir: FileRef) {
        if (src.isDir && src.ref.provider == destDir.provider) {
            val s = src.ref.path.trimEnd('/')
            val d = destDir.path.trimEnd('/')
            if (d == s || d.startsWith("$s/")) throw IllegalArgumentException("Cannot copy a folder into itself")
        }
    }

    /** Splits a zip entry name into safe path segments or throws on traversal. */
    private fun safeSegments(name: String): List<String> {
        val normalized = name.replace('\\', '/')
        if (normalized.startsWith("/")) throw SecurityException("Unsafe path in zip: $name")
        val parts = normalized.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.any { it == ".." }) throw SecurityException("Unsafe path in zip: $name")
        if (parts.isEmpty()) throw SecurityException("Empty path in zip")
        return parts
    }

    private suspend fun ensureDirs(fs: FsProvider, base: FileRef, segments: List<String>): FileRef {
        var cur = base
        for (seg in segments) {
            val next = fs.child(cur, seg)
            val st = fs.stat(next)
            cur = when {
                st == null -> fs.mkdir(cur, seg)
                st.isDir -> next
                else -> throw java.io.IOException("$seg exists and is not a folder")
            }
        }
        return cur
    }

    private class CountingInputStream(input: InputStream, val onCount: (Long) -> Unit) : FilterInputStream(input) {
        private var count = 0L
        override fun read(): Int = super.read().also { if (it >= 0) onCount(++count) }
        override fun read(b: ByteArray, off: Int, len: Int): Int =
            super.read(b, off, len).also { if (it > 0) { count += it; onCount(count) } }
    }
}
