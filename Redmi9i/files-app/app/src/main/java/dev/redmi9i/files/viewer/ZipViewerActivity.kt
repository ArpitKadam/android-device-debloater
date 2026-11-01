package dev.redmi9i.files.viewer

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.redmi9i.files.ops.Op
import dev.redmi9i.files.ops.OperationService
import dev.redmi9i.files.storage.FileInfo
import dev.redmi9i.files.storage.FileRef
import dev.redmi9i.files.ui.BrowserAdapter
import dev.redmi9i.files.ui.ThumbnailLoader
import dev.redmi9i.files.ui.sortItems
import dev.redmi9i.files.ui.SortMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipFile

/** Browse a zip like a folder. Opening a file extracts only that file to the cache first. */
class ZipViewerActivity : ViewerActivity() {
    private var zip: ZipFile? = null
    private lateinit var adapter: BrowserAdapter
    private var prefix = ""

    override fun onReady(savedInstanceState: Bundle?) {
        val z = try { ZipFile(File(ref.path)) } catch (e: Exception) {
            Toast.makeText(this, "Cannot open this zip", Toast.LENGTH_LONG).show(); finish(); return
        }
        zip = z
        File(cacheDir, "zipview").deleteRecursively()
        adapter = BrowserAdapter(ThumbnailLoader(lifecycleScope), ::onClick, { onClick(it) })
        content.addView(RecyclerView(this).apply { layoutManager = LinearLayoutManager(context); this.adapter = this@ZipViewerActivity.adapter })
        refresh()
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (prefix.isEmpty()) finish()
                else { prefix = prefix.trimEnd('/').substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }; refresh() }
            }
        })
    }

    private fun refresh() {
        val z = zip ?: return
        toolbar.subtitle = "/$prefix"
        val children = LinkedHashMap<String, FileInfo>()
        for (e in z.entries()) {
            val name = e.name.replace('\\', '/')
            if (!name.startsWith(prefix) || name == prefix) continue
            val rest = name.removePrefix(prefix)
            val first = rest.substringBefore('/')
            if (first.isEmpty()) continue
            val isDir = rest.contains('/')
            val ref = FileRef("zip", prefix + first + if (isDir) "/" else "")
            val existing = children[first]
            if (existing == null || (isDir && !existing.isDir)) {
                children[first] = FileInfo(ref, first, isDir, if (isDir) 0L else e.size, e.time.coerceAtLeast(0), false)
            }
        }
        adapter.items = sortItems(children.values.toList(), SortMode.NAME, false)
    }

    private fun onClick(info: FileInfo) {
        if (info.isDir) { prefix = info.ref.path; refresh(); return }
        val z = zip ?: return
        lifecycleScope.launch {
            val out = withContext(Dispatchers.IO) {
                runCatching {
                    val dir = File(cacheDir, "zipview/${System.nanoTime()}").apply { mkdirs() }
                    val target = File(dir, info.name) // name only: an entry can never escape this folder
                    z.getInputStream(z.getEntry(info.ref.path)).use { i -> target.outputStream().use { o -> i.copyTo(o) } }
                    target
                }
            }
            out.onSuccess { Opener.open(this@ZipViewerActivity, FileRef("local", it.path)) }
                .onFailure { Toast.makeText(this@ZipViewerActivity, it.message ?: "Cannot extract", Toast.LENGTH_LONG).show() }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_EXTRACT, 0, "Extract all here").setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == MENU_EXTRACT) {
            val dest = ref.parent?.path ?: return true
            OperationService.start(this, Op.EXTRACT, listOf(ref.path), dest)
            Toast.makeText(this, "Extracting…", Toast.LENGTH_SHORT).show()
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onDestroy() {
        runCatching { zip?.close() }
        super.onDestroy()
    }

    private companion object { const val MENU_EXTRACT = 2 }
}
