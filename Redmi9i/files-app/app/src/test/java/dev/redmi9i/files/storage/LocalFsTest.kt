package dev.redmi9i.files.storage

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.file.FileAlreadyExistsException

class LocalFsTest {
    @get:Rule val tmp = TemporaryFolder()
    private val fs = LocalFs()
    private fun root() = FileRef("local", tmp.root.path.replace('\\', '/'))

    @Test fun listShowsDirsAndFilesWithSizes() = runBlocking {
        File(tmp.root, "a.txt").writeText("hello")
        File(tmp.root, "sub").mkdir()
        val items = fs.list(root()).associateBy { it.name }
        assertEquals(2, items.size)
        assertEquals(5L, items.getValue("a.txt").size)
        assertTrue(items.getValue("sub").isDir)
    }

    @Test fun hiddenFlagFollowsDotPrefix() = runBlocking {
        File(tmp.root, ".secret").writeText("x")
        assertTrue(fs.list(root()).single().hidden)
    }

    @Test fun mkdirCreateRenameDelete() = runBlocking {
        val dir = fs.mkdir(root(), "d")
        val file = fs.createFile(dir, "f.txt")
        val renamed = fs.rename(file, "g.txt")
        assertEquals("g.txt", renamed.name)
        assertNotNull(fs.stat(renamed))
        fs.delete(dir)
        assertNull(fs.stat(dir))
    }

    @Test(expected = FileAlreadyExistsException::class)
    fun renameRefusesExistingName(): Unit = runBlocking {
        val a = fs.createFile(root(), "a")
        fs.createFile(root(), "b")
        fs.rename(a, "b")
        Unit
    }

    @Test fun deleteIsRecursive() = runBlocking {
        val dir = fs.mkdir(root(), "tree")
        val inner = fs.mkdir(dir, "inner")
        fs.createFile(inner, "x")
        fs.delete(dir)
        assertFalse(File(tmp.root, "tree").exists())
    }

    @Test fun fastMoveRelocatesFile() = runBlocking {
        val dst = fs.mkdir(root(), "dst")
        val src = fs.createFile(root(), "m.txt")
        assertTrue(fs.supportsFastMove(src, dst))
        val moved = fs.fastMove(src, dst)
        assertEquals("m.txt", moved.name)
        assertTrue(File(tmp.root, "dst/m.txt").exists())
        assertFalse(File(tmp.root, "m.txt").exists())
    }

    @Test fun volumeRootSeparatesStorages() {
        assertTrue(LocalFs.sameVolume("/storage/emulated/0/a", "/storage/emulated/0/b/c"))
        assertFalse(LocalFs.sameVolume("/storage/emulated/0/a", "/storage/1234-ABCD/a"))
        assertTrue(LocalFs.sameVolume("/storage/1234-ABCD/x", "/storage/1234-ABCD/y/z"))
    }

    @Test fun parentAndNameOfRef() {
        val r = FileRef("local", "/storage/emulated/0/DCIM/a.jpg")
        assertEquals("a.jpg", r.name)
        assertEquals("/storage/emulated/0/DCIM", r.parent!!.path)
        assertNull(FileRef("local", "/").parent)
    }

    @Test fun kindDetectionIsCaseInsensitive() {
        assertEquals(Kind.IMAGE, kindOf("X.JPG"))
        assertEquals(Kind.DOCX, kindOf("r.docx"))
        assertEquals(Kind.XLSX, kindOf("r.XLSX"))
        assertEquals(Kind.ZIP, kindOf("a.zip"))
        assertEquals(Kind.OTHER, kindOf("a.bin"))
        assertEquals(Kind.DIR, kindOf("whatever", isDir = true))
    }
}
