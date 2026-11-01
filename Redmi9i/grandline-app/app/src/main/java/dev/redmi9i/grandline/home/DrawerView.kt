package dev.redmi9i.grandline.home

import android.animation.TimeInterpolator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import dev.redmi9i.grandline.data.AppEntry
import dev.redmi9i.grandline.data.LayoutStore
import dev.redmi9i.grandline.icons.IconProvider
import dev.redmi9i.grandline.theme.Art
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.theme.Shapes
import dev.redmi9i.grandline.theme.Type

/** Full-screen app list with a search box. Slides up from the bottom. */
class DrawerView(
    ctx: Context,
    private val iconPx: Int,
    private val icons: IconProvider,
    topInset: Int,
    bottomInset: Int,
    private val onQuery: (String) -> Unit,
    art: Art,
    store: LayoutStore,
    private val attach: (IconCellView, AppEntry) -> Unit,
) : FrameLayout(ctx) {

    private val wallpaper = WallpaperView(ctx).apply { dim = 0.82f }
    private val panel = CrewPanel(ctx, art, store)
    private val search = EditText(ctx)
    private val list = RecyclerView(ctx)
    private val adapter = AppsAdapter()
    private val slide: TimeInterpolator = DecelerateInterpolator()

    var isOpen = false
        private set

    /** Called with true when the drawer starts opening and false when it starts closing. */
    var onToggle: ((Boolean) -> Unit)? = null

    init {
        visibility = GONE
        setBackgroundColor(Palette.withAlpha(Palette.DeepSea, 0.96f))
        addView(wallpaper, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))

        search.apply {
            hint = "Search the Grand Line"
            setHintTextColor(Palette.withAlpha(Palette.Parchment, 0.6f))
            setTextColor(Palette.Parchment)
            typeface = Type.title(ctx)
            textSize = 18f
            maxLines = 1
            isSingleLine = true
            val pad = Shapes.dp(ctx, 14f)
            setPadding(pad, pad / 2, pad, pad / 2)
            background = GradientDrawable().apply {
                cornerRadius = Shapes.dpf(ctx, 22f)
                setColor(Palette.withAlpha(Palette.SeaBlue, 0.75f))
                setStroke(Shapes.dp(ctx, 1f), Palette.withAlpha(Palette.StrawGold, 0.6f))
            }
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                override fun afterTextChanged(s: Editable?) { onQuery(s?.toString().orEmpty()) }
            })
        }
        val side = Shapes.dp(ctx, 16f)
        list.layoutManager = GridLayoutManager(ctx, 4)
        list.adapter = adapter
        list.clipToPadding = false
        list.setPadding(side / 2, 0, side / 2, side + bottomInset)
        val column = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
        column.addView(panel, LinearLayout.LayoutParams(-1, -2).apply { setMargins(side, 0, side, Shapes.dp(ctx, 10f)) })
        column.addView(search, LinearLayout.LayoutParams(-1, Shapes.dp(ctx, 46f)).apply { setMargins(side, 0, side, Shapes.dp(ctx, 10f)) })
        column.addView(list, LinearLayout.LayoutParams(-1, 0, 1f))
        column.setPadding(0, topInset + Shapes.dp(ctx, 10f), 0, 0)
        addView(column, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    fun setBackdrop(bmp: android.graphics.Bitmap?) { wallpaper.bitmap = bmp }

    fun setApps(apps: List<AppEntry>) { adapter.items = apps }

    val query: String get() = search.text.toString()

    fun open() {
        if (isOpen) return
        isOpen = true
        onToggle?.invoke(true)
        panel.refresh()
        visibility = VISIBLE
        translationY = height.toFloat().coerceAtLeast(1f)
        animate().translationY(0f).setDuration(240).setInterpolator(slide).start()
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        onToggle?.invoke(false)
        panel.reset() // the widgets are open by default
        search.setText("")
        search.clearFocus()
        (context.getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(windowToken, 0)
        animate().translationY(height.toFloat()).setDuration(200).setInterpolator(slide)
            .withEndAction { if (!isOpen) visibility = GONE }.start()
    }

    /** True when the list is scrolled to its top, so a downward fling should close the drawer. */
    fun atTop() = !list.canScrollVertically(-1)

    private inner class AppsAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        var items: List<AppEntry> = emptyList()
            set(v) { field = v; notifyDataSetChanged() }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val cell = IconCellView(parent.context, iconPx)
            cell.layoutParams = RecyclerView.LayoutParams(RecyclerView.LayoutParams.MATCH_PARENT, Shapes.dp(parent.context, 92f))
            return object : RecyclerView.ViewHolder(cell) {}
        }

        override fun onBindViewHolder(h: RecyclerView.ViewHolder, position: Int) {
            val cell = h.itemView as IconCellView
            val e = items[position]
            cell.bind(e, icons)
            attach(cell, e)
        }
    }
}
