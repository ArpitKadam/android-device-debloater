package dev.redmi9i.files.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.storage.StorageManager
import android.text.Editable
import android.text.TextWatcher
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.redmi9i.files.R
import dev.redmi9i.files.ops.Conflict
import dev.redmi9i.files.ops.Op
import dev.redmi9i.files.ops.OperationBus
import dev.redmi9i.files.ops.OperationService
import dev.redmi9i.files.search.SearchEngine
import dev.redmi9i.files.storage.FileInfo
import dev.redmi9i.files.storage.FileRef
import dev.redmi9i.files.storage.FsRouter
import dev.redmi9i.files.storage.Kind
import dev.redmi9i.files.storage.Volume
import dev.redmi9i.files.storage.VolumeMonitor
import dev.redmi9i.files.storage.volumeUuidOf
import dev.redmi9i.files.viewer.Opener
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : AppCompatActivity() {

    private data class Clip(val items: List<FileRef>, val move: Boolean)

    private lateinit var router: FsRouter
    private lateinit var monitor: VolumeMonitor
    private lateinit var thumbs: ThumbnailLoader
    private lateinit var toolbar: Toolbar
    private lateinit var list: RecyclerView
    private lateinit var empty: TextView
    private lateinit var loading: View
    private lateinit var pasteBar: View
    private lateinit var pasteText: TextView
    private lateinit var searchBar: View
    private lateinit var searchInput: EditText
    private lateinit var searchScope: Button
    private lateinit var fileAdapter: BrowserAdapter
    private lateinit var homeAdapter: HomeAdapter
    private val prefs by lazy { getSharedPreferences("ui", MODE_PRIVATE) }

    /** null means the home screen (volume list). */
    private var current: FileRef? = null
    private var rawItems: List<FileInfo> = emptyList()
    private var sortMode = SortMode.NAME
    private var descending = false
    private var showHidden = false
    private var clip: Clip? = null
    private var actionMode: androidx.appcompat.view.ActionMode? = null
    private var loadJob: Job? = null
    private var searchJob: Job? = null
    private var searching = false
    private var searchWholeDrive = false
    private var lastResultId = 0L
    private var knownVolumeIds: Set<String> = emptySet()

    private var pendingGrantUuid: String? = null
    private var afterGrant: (() -> Unit)? = null

    private val grantLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val uuid = pendingGrantUuid
        val tree = res.data?.data
        if (uuid != null && tree != null && router.grantTree(uuid, tree)) {
            monitor.refresh()
            afterGrant?.invoke()
        } else {
            toast("Pick the top level of the drive, then tap \"Use this folder\"")
        }
        pendingGrantUuid = null; afterGrant = null
    }

    private val permLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        if (hasStoragePermission()) startBrowsing() else showPermissionNeeded()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        toolbar = findViewById(R.id.toolbar)
        setSupportActionBar(toolbar)
        list = findViewById(R.id.list)
        empty = findViewById(R.id.empty)
        loading = findViewById(R.id.loading)
        pasteBar = findViewById(R.id.paste_bar)
        pasteText = findViewById(R.id.paste_text)
        searchBar = findViewById(R.id.search_bar)
        searchInput = findViewById(R.id.search_input)
        searchScope = findViewById(R.id.search_scope)
        Crew.todaysMark()?.let { findViewById<android.widget.ImageView>(R.id.watermark).setImageBitmap(it) }

        router = FsRouter(applicationContext)
        monitor = VolumeMonitor(applicationContext, router)
        thumbs = ThumbnailLoader(lifecycleScope)

        sortMode = SortMode.valueOf(prefs.getString("sort", SortMode.NAME.name)!!)
        descending = prefs.getBoolean("desc", false)
        showHidden = prefs.getBoolean("hidden", false)

        fileAdapter = BrowserAdapter(thumbs, ::onItemClick, ::onItemLongClick)
        homeAdapter = HomeAdapter({ openDir(it.root) }, { openDir(it) }, { confirmUnpin(it) })
        list.layoutManager = LinearLayoutManager(this)
        if (!prefs.getBoolean("seeded_phonesync", false)) {
            // First run: pin the folder that syncs with Google Drive.
            prefs.edit().putStringSet("pinned", pinned().toSet() + "/storage/emulated/0/PhoneSync")
                .putBoolean("seeded_phonesync", true).apply()
        }

        findViewById<Button>(R.id.paste_btn).setOnClickListener { paste() }
        findViewById<Button>(R.id.paste_cancel).setOnClickListener { clip = null; updatePasteBar() }
        searchScope.setOnClickListener {
            searchWholeDrive = !searchWholeDrive
            searchScope.text = if (searchWholeDrive) "Whole drive" else "This folder"
            runSearch()
        }
        searchInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { runSearch() }
        })

        onBackPressedDispatcher.addCallback(this) { handleBack() }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { OperationBus.result.collect { onOpResult(it) } }
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) { monitor.volumes.collect { onVolumes(it) } }
        }

        if (hasStoragePermission()) startBrowsing()
        else permLauncher.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
    }

    override fun onStart() { super.onStart(); monitor.start() }
    override fun onStop() { monitor.stop(); super.onStop() }

    // ---------- startup, volumes, results ----------

    private fun hasStoragePermission() =
        checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

    private fun showPermissionNeeded() {
        empty.text = "Storage permission is needed to show your files.\nGrant it in Settings > Apps > Files."
        empty.visibility = View.VISIBLE
    }

    private fun startBrowsing() {
        empty.visibility = View.GONE
        if (current == null) showHome()
    }

    private var lastVolumes: List<Volume> = emptyList()

    private fun pinned(): List<String> = (prefs.getStringSet("pinned", emptySet()) ?: emptySet()).sorted()

    private fun setPinned(paths: Set<String>) {
        prefs.edit().putStringSet("pinned", paths).apply()
        refreshHome()
    }

    private fun refreshHome() {
        val entries = ArrayList<HomeEntry>(lastVolumes.map { HomeEntry.Vol(it) })
        val pins = pinned()
        if (pins.isNotEmpty()) {
            entries += HomeEntry.Header("Pinned folders")
            pins.forEach { p ->
                val note = if (p.endsWith("/PhoneSync")) "Two-way sync with Google Drive every 30 min" else null
                entries += HomeEntry.Pin(FileRef("local", p), p.substringAfterLast('/'), note)
            }
        }
        homeAdapter.entries = entries
    }

    private fun confirmUnpin(ref: FileRef) {
        Dialogs.confirm(this, "Unpin folder", "Remove \"${ref.name}\" from the home screen? The folder and its files stay where they are.", "Unpin") {
            setPinned(pinned().toSet() - ref.path)
        }
    }

    private fun onVolumes(volumes: List<Volume>) {
        lastVolumes = volumes
        refreshHome()
        val ids = volumes.map { it.id }.toSet()
        if (knownVolumeIds.isNotEmpty()) {
            volumes.filter { it.id !in knownVolumeIds }.forEach { toast("Drive added: ${it.label}") }
            val gone = knownVolumeIds - ids
            val cur = current
            if (gone.isNotEmpty() && cur != null && volumes.none { cur.path.startsWith(it.root.path) }) {
                toast("Drive removed")
                showHome()
            }
        }
        knownVolumeIds = ids
    }

    private fun onOpResult(r: dev.redmi9i.files.ops.OpResult?) {
        if (r == null || r.id == lastResultId) return
        lastResultId = r.id
        toast(r.message)
        monitor.refresh()
        current?.let { if (!searching) load(it) }
    }

    // ---------- navigation ----------

    private fun showHome() {
        current = null
        closeSearch()
        actionMode?.finish()
        list.adapter = homeAdapter
        toolbar.title = "Files"
        toolbar.subtitle = null
        empty.visibility = View.GONE
        updatePasteBar()
        invalidateOptionsMenu()
    }

    private fun openDir(ref: FileRef) {
        closeSearch()
        actionMode?.finish()
        current = ref
        list.adapter = fileAdapter
        fileAdapter.showPath = false
        val vol = monitor.volumes.value.firstOrNull { it.root.path == ref.path }
        toolbar.title = vol?.label ?: ref.name
        toolbar.subtitle = ref.path
        updatePasteBar()
        invalidateOptionsMenu()
        load(ref)
    }

    private fun parentOf(ref: FileRef): FileRef? {
        if (monitor.volumes.value.any { it.root.path == ref.path }) return null
        return ref.parent
    }

    private fun handleBack() {
        when {
            actionMode != null -> actionMode?.finish()
            searching -> closeSearch(restore = true)
            else -> {
                val cur = current ?: return finish()
                val up = parentOf(cur)
                if (up == null) showHome() else openDir(up)
            }
        }
    }

    private fun load(ref: FileRef) {
        loadJob?.cancel()
        loading.visibility = View.VISIBLE
        empty.visibility = View.GONE
        loadJob = lifecycleScope.launch {
            val result = runCatching { router.fs.list(ref) }
            loading.visibility = View.GONE
            result.onSuccess { rawItems = it; applySort() }
                .onFailure { rawItems = emptyList(); fileAdapter.items = emptyList(); showEmpty(it.message ?: "Cannot open this folder") }
        }
    }

    private fun applySort() {
        val visible = rawItems.filter { showHidden || !it.hidden }
        fileAdapter.items = sortItems(visible, sortMode, descending)
        if (visible.isEmpty()) showEmpty("This folder is empty") else empty.visibility = View.GONE
    }

    private fun showEmpty(text: String) { empty.text = text; empty.visibility = View.VISIBLE }

    // ---------- clicks and selection ----------

    private fun onItemClick(info: FileInfo) {
        if (actionMode != null) { toggle(info); return }
        if (info.isDir) { openDir(info.ref); return }
        Opener.open(this, info.ref)
    }

    private fun onItemLongClick(info: FileInfo) {
        if (actionMode == null) actionMode = startSupportActionMode(selectionCallback)
        toggle(info)
    }

    private fun toggle(info: FileInfo) {
        fileAdapter.toggle(info)
        if (fileAdapter.selected.isEmpty()) actionMode?.finish() else actionMode?.invalidate()
    }

    private fun selectedInfos(): List<FileInfo> = fileAdapter.items.filter { it.ref.path in fileAdapter.selected }

    private val selectionCallback = object : androidx.appcompat.view.ActionMode.Callback {
        override fun onCreateActionMode(mode: androidx.appcompat.view.ActionMode, menu: Menu): Boolean {
            mode.menuInflater.inflate(R.menu.menu_select, menu); return true
        }

        override fun onPrepareActionMode(mode: androidx.appcompat.view.ActionMode, menu: Menu): Boolean {
            val sel = selectedInfos()
            mode.title = "${sel.size} selected"
            menu.findItem(R.id.sel_rename).isVisible = sel.size == 1
            menu.findItem(R.id.sel_extract).isVisible = sel.size == 1 && sel[0].kind() == Kind.ZIP
            menu.findItem(R.id.sel_share).isVisible = sel.isNotEmpty() && sel.none { it.isDir }
            return true
        }

        override fun onActionItemClicked(mode: androidx.appcompat.view.ActionMode, item: MenuItem): Boolean {
            val sel = selectedInfos()
            when (item.itemId) {
                R.id.sel_copy -> { clip = Clip(sel.map { it.ref }, false); mode.finish(); updatePasteBar() }
                R.id.sel_cut -> { clip = Clip(sel.map { it.ref }, true); mode.finish(); updatePasteBar() }
                R.id.sel_delete -> confirmDelete(sel, mode)
                R.id.sel_rename -> rename(sel.single(), mode)
                R.id.sel_share -> share(sel)
                R.id.sel_compress -> compress(sel, mode)
                R.id.sel_extract -> extract(sel.single(), mode)
                R.id.sel_props -> properties(sel)
                R.id.sel_all -> { fileAdapter.selectAll(); mode.invalidate() }
            }
            return true
        }

        override fun onDestroyActionMode(mode: androidx.appcompat.view.ActionMode) {
            fileAdapter.clearSelection(); actionMode = null
        }
    }

    // ---------- file actions ----------

    private fun confirmDelete(sel: List<FileInfo>, mode: androidx.appcompat.view.ActionMode) {
        val what = if (sel.size == 1) "\"${sel[0].name}\"" else "${sel.size} items"
        Dialogs.confirm(this, "Delete", "Delete $what permanently? This cannot be undone.", "Delete") {
            ensureGrant(sel[0].ref.path) {
                OperationService.start(this, Op.DELETE, sel.map { it.ref.path }, null)
                mode.finish()
            }
        }
    }

    private fun rename(info: FileInfo, mode: androidx.appcompat.view.ActionMode) {
        Dialogs.prompt(this, "Rename", info.name, "Rename") { name ->
            ensureGrant(info.ref.path) {
                lifecycleScope.launch {
                    runCatching { router.fs.rename(info.ref, name) }
                        .onFailure { toast(it.message ?: "Rename failed") }
                    mode.finish(); current?.let { load(it) }
                }
            }
        }
    }

    private fun newFolder() {
        val dir = current ?: return
        Dialogs.prompt(this, "New folder", "", "Create") { name ->
            ensureGrant(dir.path) {
                lifecycleScope.launch {
                    runCatching { router.fs.mkdir(dir, name) }.onFailure { toast(it.message ?: "Cannot create folder") }
                    load(dir)
                }
            }
        }
    }

    private fun compress(sel: List<FileInfo>, mode: androidx.appcompat.view.ActionMode) {
        val dir = current ?: return
        val suggested = if (sel.size == 1) sel[0].name.substringBeforeLast('.') + ".zip" else "Archive.zip"
        Dialogs.prompt(this, "Compress to zip", suggested, "Create") { name ->
            val zip = if (name.endsWith(".zip", true)) name else "$name.zip"
            ensureGrant(dir.path) {
                OperationService.start(this, Op.COMPRESS, sel.map { it.ref.path }, dir.path, zipName = zip)
                mode.finish()
            }
        }
    }

    private fun extract(zip: FileInfo, mode: androidx.appcompat.view.ActionMode) {
        val dir = current ?: return
        ensureGrant(dir.path) {
            OperationService.start(this, Op.EXTRACT, listOf(zip.ref.path), dir.path)
            mode.finish()
        }
    }

    private fun share(sel: List<FileInfo>) {
        val uris = ArrayList(sel.map { Opener.uriFor(this, it.ref.path) })
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0]).setType(Opener.mimeOf(sel[0].name))
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris).setType("*/*")
        }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        startActivity(Intent.createChooser(intent, "Share"))
    }

    private fun properties(sel: List<FileInfo>) {
        val first = sel.first()
        val base = if (sel.size == 1) {
            "Name: ${first.name}\nPath: ${first.ref.path}\nModified: ${Format.date(first.modified)}\n"
        } else "${sel.size} items\n"
        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setTitle("Properties").setMessage(base + "Size: calculating…")
            .setPositiveButton("OK", null).show()
        lifecycleScope.launch {
            val total = sel.sumOf { sizeOf(it) }
            dialog.setMessage(base + "Size: ${Format.size(total)} ($total bytes)")
        }
    }

    private suspend fun sizeOf(info: FileInfo): Long =
        if (!info.isDir) info.size
        else runCatching { router.fs.list(info.ref).sumOf { sizeOf(it) } }.getOrDefault(0L)

    // ---------- clipboard and paste ----------

    private fun updatePasteBar() {
        val c = clip
        val show = c != null && current != null
        pasteBar.visibility = if (show) View.VISIBLE else View.GONE
        if (c != null) pasteText.text = "${c.items.size} item(s) to ${if (c.move) "move" else "copy"}"
    }

    private fun paste() {
        val c = clip ?: return
        val dest = current ?: return
        lifecycleScope.launch {
            val existing = runCatching { router.fs.list(dest).map { it.name }.toSet() }.getOrDefault(emptySet())
            val clashes = c.items.count { it.name in existing }
            val go = { policy: Conflict ->
                ensureGrant(dest.path) {
                    OperationService.start(this@MainActivity, if (c.move) Op.MOVE else Op.COPY, c.items.map { it.path }, dest.path, policy)
                    clip = null; updatePasteBar()
                }
            }
            if (clashes == 0) go(Conflict.KEEP_BOTH) else Dialogs.conflictPolicy(this@MainActivity, clashes) { go(it) }
        }
    }

    // ---------- removable drive write access ----------

    /** Runs [then] right away, or after the user has granted write access to the drive that holds [path]. */
    private fun ensureGrant(path: String, then: () -> Unit) {
        val uuid = volumeUuidOf(path)
        if (uuid == null || router.hasGrant(uuid)) { then(); return }
        val sm = getSystemService(STORAGE_SERVICE) as StorageManager
        val intent = sm.getStorageVolume(File(path))?.createOpenDocumentTreeIntent()
        if (intent == null) { toast("Cannot request access to this drive"); return }
        Dialogs.confirm(
            this, "Allow access to this drive",
            "Android needs your permission once to change files on this drive. On the next screen tap \"Use this folder\" at the top level, then Allow.",
            "Continue",
        ) {
            pendingGrantUuid = uuid; afterGrant = then
            grantLauncher.launch(intent)
        }
    }

    // ---------- search ----------

    private fun openSearch() {
        if (current == null) { toast("Open a folder first"); return }
        searching = true
        searchBar.visibility = View.VISIBLE
        searchInput.requestFocus()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(searchInput, 0)
    }

    private fun closeSearch(restore: Boolean = false) {
        searchJob?.cancel()
        if (!searching) return
        searching = false
        searchBar.visibility = View.GONE
        searchInput.setText("")
        fileAdapter.showPath = false
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(searchInput.windowToken, 0)
        if (restore) applySort()
    }

    private fun runSearch() {
        searchJob?.cancel()
        val q = searchInput.text.toString()
        val cur = current ?: return
        if (q.isBlank()) { fileAdapter.showPath = false; applySort(); return }
        val root = if (searchWholeDrive) {
            monitor.volumes.value.firstOrNull { cur.path.startsWith(it.root.path) }?.root ?: cur
        } else cur
        fileAdapter.showPath = true
        fileAdapter.items = emptyList()
        showEmpty("Searching…")
        searchJob = lifecycleScope.launch {
            delay(250) // debounce typing
            val found = ArrayList<FileInfo>()
            var lastPush = 0L
            SearchEngine(router.fs).search(root, q).collect {
                found += it
                val now = System.currentTimeMillis()
                if (now - lastPush > 200) { lastPush = now; fileAdapter.items = found.toList(); empty.visibility = View.GONE }
            }
            fileAdapter.items = found.toList()
            if (found.isEmpty()) showEmpty("No matches") else empty.visibility = View.GONE
        }
    }

    // ---------- menu ----------

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu); return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        val inFolder = current != null
        listOf(R.id.action_search, R.id.action_new_folder, R.id.action_sort, R.id.action_hidden, R.id.action_pin).forEach {
            menu.findItem(it).isVisible = inFolder
        }
        menu.findItem(R.id.action_pin).title = if (current?.path in pinned()) "Unpin from home" else "Pin to home"
        menu.findItem(R.id.action_hidden).isChecked = showHidden
        menu.findItem(R.id.sort_desc).isChecked = descending
        menu.findItem(
            when (sortMode) { SortMode.NAME -> R.id.sort_name; SortMode.DATE -> R.id.sort_date; SortMode.SIZE -> R.id.sort_size; SortMode.TYPE -> R.id.sort_type },
        ).isChecked = true
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_pin -> current?.let { c ->
                val set = pinned().toSet()
                setPinned(if (c.path in set) set - c.path else set + c.path)
                toast(if (c.path in set) "Unpinned" else "Pinned to home")
            }
            R.id.action_search -> openSearch()
            R.id.action_new_folder -> newFolder()
            R.id.action_refresh -> { monitor.refresh(); current?.let { load(it) } }
            R.id.action_hidden -> { showHidden = !showHidden; prefs.edit().putBoolean("hidden", showHidden).apply(); applySort() }
            R.id.sort_desc -> { descending = !descending; prefs.edit().putBoolean("desc", descending).apply(); applySort() }
            R.id.sort_name -> setSort(SortMode.NAME)
            R.id.sort_date -> setSort(SortMode.DATE)
            R.id.sort_size -> setSort(SortMode.SIZE)
            R.id.sort_type -> setSort(SortMode.TYPE)
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setSort(mode: SortMode) {
        sortMode = mode
        prefs.edit().putString("sort", mode.name).apply()
        applySort()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
