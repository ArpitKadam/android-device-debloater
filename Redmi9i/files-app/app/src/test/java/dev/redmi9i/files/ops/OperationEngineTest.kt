package dev.redmi9i.files.ops

import dev.redmi9i.files.storage.FileInfo
import dev.redmi9i.files.storage.FileRef
import dev.redmi9i.files.storage.FsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.file.FileAlreadyExistsException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** In-memory provider. Directories are tracked in [dirs], file bytes in [files]. */
class FakeFs(override val id: String, private val fast: Boolean = true) : FsProvider {
    val dirs = mutableSetOf("/")
    val files = mutableMapOf<String, ByteArray>()

    private fun ref(p: String) = FileRef(id, p)
    private fun info(p: String): FileInfo {
        val isDir = p in dirs
        return FileInfo(ref(p), p.substringAfterLast('/'), isDir, if (isDir) 0 else files.getValue(p).size.toLong(), 0, false)
    }

    fun put(path: String, data: ByteArray) {
        var d = path.substringBeforeLast('/')
        while (d.isNotEmpty()) { dirs += d; d = d.substringBeforeLast('/', "") }
        files[path] = data
    }

    override suspend fun list(dir: FileRef): List<FileInfo> {
        val prefix = dir.path.trimEnd('/') + "/"
        val names = (dirs + files.keys).filter { it.startsWith(prefix) && it != dir.path && !it.removePrefix(prefix).contains('/') }
        return names.map(::info)
    }

    override suspend fun stat(ref: FileRef) = if (ref.path in dirs || ref.path in files) info(ref.path) else null
    override fun openRead(ref: FileRef): InputStream = ByteArrayInputStream(files.getValue(ref.path))
    override fun openWrite(ref: FileRef): OutputStream = object : ByteArrayOutputStream() {
        override fun write(b: ByteArray, off: Int, len: Int) { super.write(b, off, len); files[ref.path] = toByteArray() }
        override fun close() { files[ref.path] = toByteArray() }
    }
    override suspend fun mkdir(parent: FileRef, name: String): FileRef {
        val p = child(parent, name).path
        if (p in dirs || p in files) throw FileAlreadyExistsException(p)
        dirs += p; return ref(p)
    }
    override suspend fun createFile(parent: FileRef, name: String): FileRef {
        val p = child(parent, name).path
        if (p in dirs || p in files) throw FileAlreadyExistsException(p)
        files[p] = ByteArray(0); return ref(p)
    }
    override suspend fun delete(ref: FileRef) {
        val prefix = ref.path + "/"
        dirs.removeAll { it == ref.path || it.startsWith(prefix) }
        files.keys.removeAll { it == ref.path || it.startsWith(prefix) }
    }
    override suspend fun rename(ref: FileRef, newName: String): FileRef {
        val np = ref.path.substringBeforeLast('/') + "/" + newName
        files.remove(ref.path)?.let { files[np] = it }
        return ref(np)
    }
    override fun child(dir: FileRef, name: String) = ref(dir.path.trimEnd('/') + "/" + name)
    override fun supportsFastMove(from: FileRef, toDir: FileRef) = fast && from.provider == id && toDir.provider == id
    override suspend fun fastMove(from: FileRef, toDir: FileRef): FileRef {
        val np = toDir.path.trimEnd('/') + "/" + from.name
        if (from.path in files) { files[np] = files.remove(from.path)!! } else {
            val prefix = from.path + "/"
            dirs.filter { it == from.path || it.startsWith(prefix) }.forEach { dirs.remove(it); dirs += np + it.removePrefix(from.path) }
            files.keys.filter { it.startsWith(prefix) }.forEach { files[np + it.removePrefix(from.path)] = files.remove(it)!! }
            dirs += np
        }
        return ref(np)
    }
}

class OperationEngineTest {
    private val a = FakeFs("a")
    private val b = FakeFs("b")
    private val engine = OperationEngine { id -> if (id == "a") a else b }
    private val never = ConflictResolver { _, _ -> error("no conflict expected") }
    private val noProgress: (Progress) -> Unit = {}

    @Test fun keepBothNamePicksNextFreeSuffix() {
        assertEquals("a.txt", keepBothName("a.txt", emptySet()))
        assertEquals("a (1).txt", keepBothName("a.txt", setOf("a.txt")))
        assertEquals("a (2).txt", keepBothName("a.txt", setOf("a.txt", "a (1).txt")))
        assertEquals("notes (1)", keepBothName("notes", setOf("notes")))
        assertEquals(".hidden (1)", keepBothName(".hidden", setOf(".hidden")))
    }

    @Test fun copiesFileAcrossProviders() = runBlocking {
        a.put("/src/x.bin", byteArrayOf(1, 2, 3))
        b.dirs += "/dst"
        engine.copy(listOf(FileRef("a", "/src/x.bin")), FileRef("b", "/dst"), never, noProgress)
        assertArrayEquals(byteArrayOf(1, 2, 3), b.files["/dst/x.bin"])
        assertTrue("/src/x.bin" in a.files)
    }

    @Test fun copiesFolderTree() = runBlocking {
        a.put("/src/t/one.txt", "1".toByteArray())
        a.put("/src/t/sub/two.txt", "22".toByteArray())
        b.dirs += "/dst"
        engine.copy(listOf(FileRef("a", "/src/t")), FileRef("b", "/dst"), never, noProgress)
        assertEquals("1", String(b.files.getValue("/dst/t/one.txt")))
        assertEquals("22", String(b.files.getValue("/dst/t/sub/two.txt")))
    }

    @Test fun conflictSkipKeepsOriginal() = runBlocking {
        a.put("/s/f.txt", "new".toByteArray()); b.put("/d/f.txt", "old".toByteArray())
        engine.copy(listOf(FileRef("a", "/s/f.txt")), FileRef("b", "/d"), { _, _ -> Conflict.SKIP }, noProgress)
        assertEquals("old", String(b.files.getValue("/d/f.txt")))
    }

    @Test fun conflictReplaceOverwrites() = runBlocking {
        a.put("/s/f.txt", "new".toByteArray()); b.put("/d/f.txt", "old".toByteArray())
        engine.copy(listOf(FileRef("a", "/s/f.txt")), FileRef("b", "/d"), { _, _ -> Conflict.REPLACE }, noProgress)
        assertEquals("new", String(b.files.getValue("/d/f.txt")))
    }

    @Test fun conflictKeepBothAddsSuffix() = runBlocking {
        a.put("/s/f.txt", "new".toByteArray()); b.put("/d/f.txt", "old".toByteArray())
        engine.copy(listOf(FileRef("a", "/s/f.txt")), FileRef("b", "/d"), { _, _ -> Conflict.KEEP_BOTH }, noProgress)
        assertEquals("old", String(b.files.getValue("/d/f.txt")))
        assertEquals("new", String(b.files.getValue("/d/f (1).txt")))
    }

    @Test fun rejectsCopyingFolderIntoItself() = runBlocking {
        a.put("/t/x.txt", "x".toByteArray())
        try {
            engine.copy(listOf(FileRef("a", "/t")), FileRef("a", "/t/inner"), never, noProgress)
            fail("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message!!.contains("itself"))
        }
    }

    @Test fun sameProviderMoveUsesFastMove() = runBlocking {
        a.put("/s/m.txt", "m".toByteArray()); a.dirs += "/d"
        engine.move(listOf(FileRef("a", "/s/m.txt")), FileRef("a", "/d"), never, noProgress)
        assertEquals("m", String(a.files.getValue("/d/m.txt")))
        assertNull(a.files["/s/m.txt"])
    }

    @Test fun crossProviderMoveCopiesThenDeletesSource() = runBlocking {
        a.put("/s/m.txt", "m".toByteArray()); b.dirs += "/d"
        engine.move(listOf(FileRef("a", "/s/m.txt")), FileRef("b", "/d"), never, noProgress)
        assertEquals("m", String(b.files.getValue("/d/m.txt")))
        assertNull(a.files["/s/m.txt"])
    }

    @Test fun skippedMoveKeepsSource() = runBlocking {
        a.put("/s/m.txt", "m".toByteArray()); b.put("/d/m.txt", "old".toByteArray())
        engine.move(listOf(FileRef("a", "/s/m.txt")), FileRef("b", "/d"), { _, _ -> Conflict.SKIP }, noProgress)
        assertTrue("/s/m.txt" in a.files)
    }

    @Test fun deleteRemovesTargets() = runBlocking {
        a.put("/s/d/x", "x".toByteArray()); a.put("/s/y", "y".toByteArray())
        engine.delete(listOf(FileRef("a", "/s/d"), FileRef("a", "/s/y")), noProgress)
        assertTrue(a.files.isEmpty())
    }

    @Test fun cancelMidCopyRemovesPartialFile() = runBlocking {
        a.put("/s/big.bin", ByteArray(1024 * 1024)); b.dirs += "/d"
        lateinit var job: Job
        job = launch(Dispatchers.Default) {
            engine.copy(listOf(FileRef("a", "/s/big.bin")), FileRef("b", "/d"), never) { p ->
                if (p.doneBytes > 0) job.cancel()
            }
        }
        job.join()
        assertTrue(job.isCancelled)
        assertFalse("/d/big.bin" in b.files)
    }

    @Test fun zipRoundTrip() = runBlocking {
        a.put("/s/one.txt", "one".toByteArray())
        a.put("/s/dir/two.txt", "two".toByteArray())
        a.dirs += "/out"
        val zip = FileRef("a", "/out/pack.zip")
        engine.compressZip(listOf(FileRef("a", "/s/one.txt"), FileRef("a", "/s/dir")), zip, noProgress)
        a.dirs += "/x"
        engine.extractZip(zip, FileRef("a", "/x"), noProgress)
        assertEquals("one", String(a.files.getValue("/x/one.txt")))
        assertEquals("two", String(a.files.getValue("/x/dir/two.txt")))
    }

    @Test fun extractRejectsZipSlip() = runBlocking {
        val bytes = ByteArrayOutputStream().also { bo ->
            ZipOutputStream(bo).use { z -> z.putNextEntry(ZipEntry("../evil.txt")); z.write(1); z.closeEntry() }
        }.toByteArray()
        a.put("/in/bad.zip", bytes); a.dirs += "/x/inner"
        try {
            engine.extractZip(FileRef("a", "/in/bad.zip"), FileRef("a", "/x/inner"), noProgress)
            fail("expected SecurityException")
        } catch (e: SecurityException) {
            assertFalse("/x/evil.txt" in a.files)
        }
    }

    @Test fun extractKeepsExistingFilesByRenaming() = runBlocking {
        val bytes = ByteArrayOutputStream().also { bo ->
            ZipOutputStream(bo).use { z -> z.putNextEntry(ZipEntry("f.txt")); z.write("new".toByteArray()); z.closeEntry() }
        }.toByteArray()
        a.put("/in/p.zip", bytes); a.put("/x/f.txt", "old".toByteArray())
        engine.extractZip(FileRef("a", "/in/p.zip"), FileRef("a", "/x"), noProgress)
        assertEquals("old", String(a.files.getValue("/x/f.txt")))
        assertEquals("new", String(a.files.getValue("/x/f (1).txt")))
    }

    @Test fun progressReachesTotals() = runBlocking {
        a.put("/s/p.bin", ByteArray(200_000)); b.dirs += "/d"
        var last: Progress? = null
        engine.copy(listOf(FileRef("a", "/s/p.bin")), FileRef("b", "/d"), never) { last = it }
        assertEquals(200_000L, last!!.totalBytes)
        assertEquals(200_000L, last!!.doneBytes)
        assertEquals(1, last!!.doneFiles)
    }
}
