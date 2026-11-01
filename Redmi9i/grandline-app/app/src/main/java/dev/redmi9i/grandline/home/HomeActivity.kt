package dev.redmi9i.grandline.home

import android.Manifest
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.DragEvent
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import dev.redmi9i.grandline.data.AppEntry
import dev.redmi9i.grandline.data.AppRepository
import dev.redmi9i.grandline.data.Layout
import dev.redmi9i.grandline.data.LayoutOps
import dev.redmi9i.grandline.data.LayoutStore
import dev.redmi9i.grandline.data.filterApps
import dev.redmi9i.grandline.data.reconcile
import dev.redmi9i.grandline.icons.MedallionIconProvider
import dev.redmi9i.grandline.settings.SettingsActivity
import dev.redmi9i.grandline.theme.Art
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.lock.LockEnabler
import dev.redmi9i.grandline.termux.TermuxBoot
import dev.redmi9i.grandline.theme.Shapes
import dev.redmi9i.grandline.theme.SystemWallpaper
import dev.redmi9i.grandline.theme.Type
import dev.redmi9i.grandline.widgets.WidgetHostView
import dev.redmi9i.grandline.widgets.WidgetRegistry
import kotlinx.coroutines.launch
import kotlin.math.abs

class HomeActivity : AppCompatActivity() {
    private lateinit var repo: AppRepository
    private lateinit var store: LayoutStore
    private lateinit var art: Art
    private lateinit var icons: MedallionIconProvider
    private lateinit var wallpaper: WallpaperView
    private lateinit var widgetHost: WidgetHostView
    private lateinit var pager: ViewPager2
    private lateinit var dots: PageDots
    private lateinit var dock: DockView
    private lateinit var drawer: DrawerView
    private lateinit var video: VideoWallpaperView
    private lateinit var still: android.widget.ImageView // the video's first frame, shown until the video draws
    private lateinit var removeZone: TextView
    private lateinit var edgeLeft: View
    private lateinit var edgeRight: View

    private var layout: Layout = LayoutOps.empty()
    private var apps: List<AppEntry> = emptyList()
    private var byKey: Map<String, AppEntry> = emptyMap()
    private val pageAdapter = PageAdapter()
    private val ui = Handler(Looper.getMainLooper())

    private var dragKey: String? = null
    private var dragFrom = ""
    private var dragging = false
    private var videoFile: java.io.File? = null

    private val dockPrefer = listOf("com.brave.browser", "dev.redmi9i.files", "org.telegram.messenger.web", "com.termux")
    private val skipFromHome = setOf("app.lawnchair", "com.miui.home")
    /** Not removed (the system needs some, Lawnchair is the fallback launcher), only kept out of the drawer. */
    private val hiddenByDefault = setOf("com.android.providers.downloads.ui", "com.miui.securitycenter", "com.mi.android.globalFileexplorer", "app.lawnchair")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.navigationBarColor = Color.TRANSPARENT // the wallpaper shows behind the nav buttons
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION

        repo = AppRepository(applicationContext)
        store = LayoutStore(applicationContext)
        art = Art(applicationContext)
        val iconPx = Shapes.dp(this, 54f)
        icons = MedallionIconProvider(applicationContext, iconPx, art)
        val statusH = statusBarHeight()

        val root = FrameLayout(this)
        still = android.widget.ImageView(this).apply { scaleType = android.widget.ImageView.ScaleType.CENTER_CROP }
        root.addView(still, ViewGroup.LayoutParams(-1, -1))
        video = VideoWallpaperView(this)
        root.addView(video, ViewGroup.LayoutParams(-1, -1))
        wallpaper = WallpaperView(this)
        root.addView(wallpaper, ViewGroup.LayoutParams(-1, -1))

        // The column: widgets, pages, drawer handle, dots, dock.
        val column = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        widgetHost = WidgetHostView(this).also { it.show(WidgetRegistry.all()) }
        column.addView(widgetHost, LinearLayout.LayoutParams(-1, -2))
        pager = ViewPager2(this).apply { adapter = pageAdapter; offscreenPageLimit = 1 }
        column.addView(pager, LinearLayout.LayoutParams(-1, 0, 1f))
        dots = PageDots(this)
        // The page dots stay out of the layout (kept only so the code that updates them keeps working).
        dots.visibility = View.GONE
        column.addView(dots, LinearLayout.LayoutParams(0, 0))
        dock = DockView(this, iconPx)
        column.addView(dock, LinearLayout.LayoutParams(-1, Shapes.dp(this, 84f)).apply {
            val m = Shapes.dp(this@HomeActivity, 14f); setMargins(m, 0, m, Shapes.dp(this@HomeActivity, 10f))
        })
        column.setPadding(0, statusH + Shapes.dp(this, 8f), 0, navBarHeight())
        root.addView(column, ViewGroup.LayoutParams(-1, -1))

        // Drag helpers: remove zone and page-edge hot spots.
        removeZone = TextView(this).apply {
            text = "Remove from home"; setTextColor(Color.WHITE); typeface = Type.title(context); textSize = 18f
            gravity = Gravity.CENTER; setPadding(0, statusH, 0, 0)
            setBackgroundColor(Palette.withAlpha(Palette.VestRed, 0.9f))
            translationY = -Shapes.dp(this@HomeActivity, 140f).toFloat()
        }
        root.addView(removeZone, FrameLayout.LayoutParams(-1, statusH + Shapes.dp(this, 64f), Gravity.TOP))
        val edgeW = Shapes.dp(this, 28f)
        edgeLeft = View(this); edgeRight = View(this)
        root.addView(edgeLeft, FrameLayout.LayoutParams(edgeW, -1, Gravity.START))
        root.addView(edgeRight, FrameLayout.LayoutParams(edgeW, -1, Gravity.END))

        drawer = DrawerView(this, iconPx, icons, statusH, navBarHeight(), onQuery = { refreshDrawer() }, art = art, store = store, attach = { cell, e -> attachGestures(cell, e, "drawer") })
        drawer.onToggle = { open -> if (open) video.stop() else videoFile?.let { video.play(it) } }
        root.addView(drawer, ViewGroup.LayoutParams(-1, -1))
        setContentView(root)

        wireDrag()
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) { dots.selected = position; showWallpaper(position) }
        })
        onBackPressedDispatcher.addCallback(this) { if (drawer.isOpen) drawer.close() }

        lifecycleScope.launch { repeatOnLifecycle(Lifecycle.State.STARTED) { repo.apps.collect { onApps(it) } } }
        if (checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE), 1)
        }
        showWallpaper(0)
        TermuxBoot.ensureServices(this)
        LockEnabler.ensureEnabled(this)
    }

    override fun onStart() { super.onStart(); repo.start() }

    /** Coming back from settings: pick up page count and hidden-app changes. */
    override fun onResume() {
        super.onResume()
        if (apps.isNotEmpty()) onApps(apps)
        showWallpaper(pager.currentItem)
    }
    /** The video decoder only runs while the home screen is visible and the drawer is closed. */
    override fun onPause() { video.stop(); super.onPause() }
    override fun onStop() { repo.stop(); super.onStop() }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        art.clear()
        showWallpaper(pager.currentItem)
    }

    /** The Home button: go back to page 1 and close the drawer. */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.action == Intent.ACTION_MAIN && intent.hasCategory(Intent.CATEGORY_HOME)) {
            drawer.close()
            pager.setCurrentItem(0, true)
        }
    }

    // ---- swipe up for the drawer ----

    private val flingDetector by lazy {
        GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                if (dragging || abs(vy) < 900 || abs(vy) < abs(vx) * 1.5f) return false
                if (vy < 0 && !drawer.isOpen) { drawer.open(); return true }
                if (vy > 0 && drawer.isOpen && drawer.atTop()) { drawer.close(); return true }
                return false
            }
        })
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        flingDetector.onTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    // ---- data ----

    private fun onApps(list: List<AppEntry>) {
        apps = list
        byKey = list.associateBy { it.key }
        val fresh = hiddenByDefault - store.seededHidden
        if (fresh.isNotEmpty() && list.isNotEmpty()) {
            store.hidden = store.hidden + list.filter { it.key.substringBefore('/') in fresh }.map { it.key }
            store.seededHidden = store.seededHidden + fresh
        }
        val rec = reconcile(store.load(), list, store.known, store.hidden, dockPrefer, skipFromHome, store.pageCount, store.autoPlace)
        layout = LayoutOps.withPageCount(rec.layout, store.pageCount)
        store.save(layout)
        store.known = rec.known
        refresh()
    }

    private fun refresh() {
        pageAdapter.notifyDataSetChanged()
        dock.bind(layout.dock, byKey, icons) { cell, key -> byKey[key]?.let { attachGestures(cell, it, "dock") } }
        dots.count = layout.pages.size
        dots.selected = pager.currentItem.coerceAtMost(layout.pages.size - 1)
        updateDockVisibility()
        wallpaper.dim = if (layout.dock.any { it != null } || layout.pages.any { it.cells.isNotEmpty() }) 0.30f else 0f
        refreshDrawer()
    }

    /** An empty dock is invisible (a clean home); it fades in while an icon is being dragged so it can still receive a drop. */
    private fun updateDockVisibility() {
        val show = dragging || layout.dock.any { it != null }
        dock.animate().alpha(if (show) 1f else 0f).setDuration(120).start()
    }

    private fun refreshDrawer() { drawer.setApps(filterApps(apps, drawer.query, store.hidden)) }

    private fun commit(l: Layout) { layout = l; store.save(l); refresh() }

    private fun showWallpaper(page: Int) {
        val dm = resources.displayMetrics
        var name = store.wallpaperName(page)
        if (!art.file("wallpapers/$name").isFile) name = store.wallpaperName(0)
        if (!art.file("wallpapers/$name").isFile) name = art.wallpaperNames().firstOrNull() ?: name // nothing chosen or the file is gone
        val bmp = art.wallpaper(name, dm.widthPixels, dm.heightPixels)
        store.currentWallpaper = name
        SystemWallpaper.sync(applicationContext, store, "v2:$name:${art.file("wallpapers/$name").lastModified()}", bmp)
        if (Art.isVideo(name)) {
            // The video sits over a still frame and under the scrim.
            still.setImageBitmap(bmp)
            wallpaper.bitmap = null
            videoFile = art.file("wallpapers/$name")
            if (!drawer.isOpen) video.play(videoFile!!)
        } else {
            videoFile = null
            video.stop()
            still.setImageBitmap(null)
            wallpaper.bitmap = bmp
        }
        val dName = store.drawerWallpaperName()
        drawer.setBackdrop(if (art.file("wallpapers/$dName").isFile) art.wallpaper(dName, dm.widthPixels, dm.heightPixels) else bmp)
    }

    private fun navBarHeight(): Int {
        val id = resources.getIdentifier("navigation_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else Shapes.dp(this, 24f)
    }

    private fun openSettings() { startActivity(Intent(this, SettingsActivity::class.java)) }

    /** Long-press on empty home: pick a wallpaper for this page with one tap. */
    private fun openWallpaperSheet() {
        val page = pager.currentItem
        WallpaperSheet(this, art, store.wallpaperName(page), onPick = { name ->
            store.setWallpaperName(page, name)
            showWallpaper(page)
        }, onSettings = { openSettings() }).show()
    }

    // ---- icons: tap, menu, drag ----

    private fun attachGestures(cell: IconCellView, e: AppEntry, source: String) {
        CellGestures(cell, onTap = { repo.launch(e) }, onMenu = { showMenu(cell, e) }, onDrag = { beginDrag(cell, e.key, source) }).install()
    }

    private fun showMenu(anchor: View, e: AppEntry) {
        val onHome = LayoutOps.contains(layout, e.key)
        val menu = PopupMenu(this, anchor)
        if (onHome) menu.menu.add(0, 1, 0, "Remove from home") else menu.menu.add(0, 2, 0, "Add to home")
        menu.menu.add(0, 3, 1, "App info")
        if (e.isUser) menu.menu.add(0, 4, 2, "Uninstall")
        menu.menu.add(0, 5, 3, "Hide")
        menu.setOnMenuItemClickListener {
            when (it.itemId) {
                1 -> commit(LayoutOps.remove(layout, e.key))
                2 -> LayoutOps.firstFree(layout)?.let { (p, r, c) -> commit(LayoutOps.place(layout, e.key, p, r, c)) }
                    ?: toast("Home screen is full")
                3 -> startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${e.component.packageName}")))
                4 -> startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${e.component.packageName}")))
                5 -> { store.hidden = store.hidden + e.key; onApps(apps) }
            }
            true
        }
        menu.show()
    }

    private fun beginDrag(v: View, key: String, source: String) {
        dragKey = key; dragFrom = source
        val ok = v.startDragAndDrop(ClipData.newPlainText("app", key), View.DragShadowBuilder(v), null, 0)
        if (!ok) return
        dragging = true
        updateDockVisibility()
        if (source == "drawer") drawer.close() else removeZone.animate().translationY(0f).setDuration(150).start()
    }

    private fun endDragUi() {
        dragging = false; dragKey = null
        updateDockVisibility()
        removeZone.animate().translationY(-Shapes.dp(this, 140f).toFloat()).setDuration(150).start()
        ui.removeCallbacksAndMessages(null)
    }

    private fun wireDrag() {
        removeZone.setOnDragListener { v, ev ->
            when (ev.action) {
                DragEvent.ACTION_DRAG_STARTED -> true
                DragEvent.ACTION_DROP -> {
                    val k = dragKey
                    if (k != null && dragFrom != "drawer") commit(LayoutOps.remove(layout, k))
                    true
                }
                DragEvent.ACTION_DRAG_ENDED -> { endDragUi(); true }
                DragEvent.ACTION_DRAG_ENTERED -> { v.alpha = 1f; true }
                else -> true
            }
        }
        listOf(edgeLeft to -1, edgeRight to 1).forEach { (view, dir) ->
            view.setOnDragListener { _, ev ->
                when (ev.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DRAG_ENTERED -> { flipLater(dir); true }
                    DragEvent.ACTION_DRAG_EXITED, DragEvent.ACTION_DROP -> { ui.removeCallbacksAndMessages(null); true }
                    else -> true
                }
            }
        }
        dock.slots.forEachIndexed { i, slot ->
            slot.setOnDragListener { _, ev ->
                when (ev.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DROP -> { dragKey?.let { commit(LayoutOps.placeDock(layout, it, i)) }; true }
                    else -> true
                }
            }
        }
    }

    /** Hold a dragged icon at a screen edge for half a second to move to the next page. */
    private fun flipLater(dir: Int) {
        ui.postDelayed({
            val next = pager.currentItem + dir
            if (dragging && next in 0 until layout.pages.size) { pager.setCurrentItem(next, true); flipLater(dir) }
        }, 550)
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    // ---- pages ----
    // Each page is rebuilt from the layout whenever it changes; a page is cheap (20 cells at most).

    private inner class PageAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        override fun getItemCount() = layout.pages.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val grid = PageGridView(parent.context, layout.rows, layout.cols)
            grid.layoutParams = RecyclerView.LayoutParams(-1, -1)
            return object : RecyclerView.ViewHolder(grid) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            val grid = h.itemView as PageGridView
            val iconPx = Shapes.dp(grid.context, 54f)
            val cells = layout.pages[position].cells.mapNotNull { cell ->
                byKey[cell.key]?.let { e ->
                    val v = IconCellView(grid.context, iconPx)
                    v.bind(e, icons)
                    attachGestures(v, e, "home")
                    cell to (v as View)
                }
            }
            grid.setCells(cells)
            grid.setOnLongClickListener { openWallpaperSheet(); true } // long-press empty space
            grid.setOnDragListener { _, ev ->
                when (ev.action) {
                    DragEvent.ACTION_DRAG_STARTED -> true
                    DragEvent.ACTION_DROP -> {
                        val (r, c) = grid.cellAt(ev.x, ev.y)
                        dragKey?.let { commit(LayoutOps.place(layout, it, position, r, c)) }
                        true
                    }
                    else -> true
                }
            }
        }
    }
}
