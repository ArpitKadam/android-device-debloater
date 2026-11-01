package dev.redmi9i.grandline.home

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.redmi9i.grandline.data.AppEntry
import dev.redmi9i.grandline.icons.IconProvider
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.theme.Shapes

/** Four fixed slots at the bottom of every page. Each slot is a drop target. */
class DockView(ctx: Context, private val iconPx: Int) : LinearLayout(ctx) {
    val slots: List<FrameLayout> = List(4) { FrameLayout(ctx) }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = Shapes.dp(ctx, 6f)
        setPadding(pad, pad, pad, pad)
        background = GradientDrawable().apply {
            cornerRadius = Shapes.dpf(ctx, 24f)
            setColor(Palette.withAlpha(Palette.DeepSea, 0.62f))
            setStroke(Shapes.dp(ctx, 1f), Palette.withAlpha(Palette.StrawGold, 0.55f))
        }
        slots.forEach { addView(it, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)) }
    }

    /** [apps] maps a component key to its entry; a null or unknown key leaves the slot empty. */
    fun bind(keys: List<String?>, apps: Map<String, AppEntry>, provider: IconProvider, attach: (IconCellView, String) -> Unit) {
        slots.forEachIndexed { i, slot ->
            slot.removeAllViews()
            val entry = keys.getOrNull(i)?.let { apps[it] } ?: return@forEachIndexed
            val cell = IconCellView(context, iconPx, labels = false)
            cell.bind(entry, provider)
            attach(cell, entry.key)
            slot.addView(cell, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
        }
    }
}
