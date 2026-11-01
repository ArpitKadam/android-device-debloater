package dev.redmi9i.files.search

import dev.redmi9i.files.storage.FileInfo
import dev.redmi9i.files.storage.FileRef
import dev.redmi9i.files.storage.FsProvider
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Breadth-first, case-insensitive filename search. Results stream out as they are found. */
class SearchEngine(private val fs: FsProvider, private val maxDepth: Int = 32) {

    fun search(root: FileRef, query: String): Flow<FileInfo> = flow {
        val q = query.trim().lowercase()
        if (q.isEmpty()) return@flow
        val queue = ArrayDeque<Pair<FileRef, Int>>()
        queue.add(root to 0)
        while (queue.isNotEmpty()) {
            val (dir, depth) = queue.removeFirst()
            val children = try {
                fs.list(dir)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                continue // unreadable folder: skip it, keep searching the rest
            }
            for (c in children) {
                if (c.name.lowercase().contains(q)) emit(c)
                if (c.isDir && depth < maxDepth) queue.add(c.ref to depth + 1)
            }
        }
    }
}
