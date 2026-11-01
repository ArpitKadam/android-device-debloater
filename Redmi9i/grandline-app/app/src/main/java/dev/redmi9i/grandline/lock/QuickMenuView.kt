package dev.redmi9i.grandline.lock

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.theme.Shapes

/**
 * Replaces the stock accessibility menu (the little person button in the navigation bar): a Grand Line card with
 * only the actions that are useful here, and no Google Assistant. A tap outside the card calls [onDismiss].
 */
class QuickMenuView(ctx: Context, actions: List<Pair<QuickKind, () -> Unit>>, private val onDismiss: () -> Unit) : FrameLayout(ctx) {
    init {
        setBackgroundColor(0x99000000.toInt())
        val side = Shapes.dp(ctx, 14f)
        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(side, side, side, side)
            background = GradientDrawable().apply {
                cornerRadius = Shapes.dpf(ctx, 22f)
                setColor(Palette.withAlpha(Palette.DeepSea, 0.97f))
                setStroke(Shapes.dp(ctx, 1f), Palette.withAlpha(Palette.StrawGold, 0.6f))
            }
            isClickable = true // taps on the card itself must not count as "outside"
        }
        for (rowItems in actions.chunked(3)) {
            val row = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
            for ((kind, run) in rowItems) {
                row.addView(QuickIcon(ctx, kind).apply {
                    background = GradientDrawable().apply { cornerRadius = Shapes.dpf(ctx, 14f); setColor(Palette.withAlpha(Palette.SeaBlue, 0.85f)) }
                    setOnClickListener { run() }
                }, LinearLayout.LayoutParams(0, Shapes.dp(ctx, 72f), 1f).apply { setMargins(Shapes.dp(ctx, 4f), Shapes.dp(ctx, 4f), Shapes.dp(ctx, 4f), Shapes.dp(ctx, 4f)) })
            }
            repeat(3 - rowItems.size) { row.addView(android.view.View(ctx), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(Shapes.dp(ctx, 4f), 0, Shapes.dp(ctx, 4f), 0) }) }
            card.addView(row, LinearLayout.LayoutParams(-1, -2))
        }
        addView(card, LayoutParams(-1, -2, Gravity.BOTTOM).apply { setMargins(side, 0, side, side) })
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP) onDismiss()
        return true
    }
}
