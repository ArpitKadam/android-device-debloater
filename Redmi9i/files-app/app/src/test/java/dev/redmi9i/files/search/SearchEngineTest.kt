package dev.redmi9i.files.search

import dev.redmi9i.files.ops.FakeFs
import dev.redmi9i.files.storage.FileInfo
import dev.redmi9i.files.storage.FileRef
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SearchEngineTest {
    private val fs = FakeFs("a").apply {
        put("/r/Report.txt", ByteArray(1))
        put("/r/sub/deep/annual REPORT.pdf", ByteArray(1))
        put("/r/sub/other.txt", ByteArray(1))
    }
    private val root = FileRef("a", "/r")

    @Test fun findsNestedMatchesCaseInsensitively() = runBlocking {
        val names = SearchEngine(fs).search(root, "report").toList().map { it.name }
        assertEquals(setOf("Report.txt", "annual REPORT.pdf"), names.toSet())
    }

    @Test fun shallowResultsComeBeforeDeepOnes() = runBlocking {
        val names = SearchEngine(fs).search(root, "report").toList().map { it.name }
        assertEquals("Report.txt", names.first())
    }

    @Test fun emptyQueryYieldsNothing() = runBlocking {
        assertTrue(SearchEngine(fs).search(root, "  ").toList().isEmpty())
    }

    @Test fun collectingOnlyFirstStopsTheSearch() = runBlocking {
        assertEquals("Report.txt", SearchEngine(fs).search(root, "report").first().name)
    }

    @Test fun unreadableFolderIsSkipped() = runBlocking {
        val flaky = object : dev.redmi9i.files.storage.FsProvider by fs {
            override suspend fun list(dir: FileRef): List<FileInfo> =
                if (dir.path == "/r/sub") throw IOException("denied") else fs.list(dir)
        }
        val names = SearchEngine(flaky).search(root, "report").toList().map { it.name }
        assertEquals(listOf("Report.txt"), names)
    }
}
