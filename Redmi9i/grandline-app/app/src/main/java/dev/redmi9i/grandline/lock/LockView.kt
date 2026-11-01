package dev.redmi9i.grandline.lock

import android.content.Context
import android.graphics.Bitmap
import android.view.Gravity
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.theme.Shapes
import dev.redmi9i.grandline.theme.Type

/**
 * The lock screen: a wanted poster on its own near-black, with the time above it. Drag or fling up to open.
 * It is decoration, not security: there is no PIN.
 */
class LockView(ctx: Context, poster: Bitmap?, private val onUnlock: () -> Unit) : FrameLayout(ctx) {
    private var downY = 0f
    private var tracker: VelocityTracker? = null
    private val content = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }

    init {
        setBackgroundColor(POSTER_BLACK) // same black as the poster art; stays put while only the content slides, so no trails
        val col = content
        col.setPadding(0, Shapes.dp(ctx, 52f), 0, Shapes.dp(ctx, 12f))
        col.addView(TextClock(ctx).apply {
            format12Hour = "h:mm"; format24Hour = "HH:mm"; gravity = Gravity.CENTER
            typeface = Type.title(ctx); textSize = 64f; setTextColor(Palette.StrawGold)
        }, LinearLayout.LayoutParams(-1, -2))
        col.addView(TextClock(ctx).apply {
            format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"; gravity = Gravity.CENTER
            textSize = 15f; setTextColor(Palette.Parchment)
        }, LinearLayout.LayoutParams(-1, -2))
        col.addView(ImageView(ctx).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setImageBitmap(poster)
        }, LinearLayout.LayoutParams(-1, 0, 1f).apply { topMargin = Shapes.dp(ctx, 12f) })
        col.addView(TextView(ctx).apply {
            text = "⌃  swipe up"; textSize = 14f; setTextColor(Palette.withAlpha(Palette.StrawGold, 0.85f)); gravity = Gravity.CENTER
            setPadding(0, Shapes.dp(ctx, 10f), 0, Shapes.dp(ctx, 26f))
        })
        addView(col, ViewGroup.LayoutParams(-1, -1))
        systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
            View.SYSTEM_UI_FLAG_FULLSCREEN or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { downY = e.rawY; tracker = VelocityTracker.obtain().also { it.addMovement(e) } }
            MotionEvent.ACTION_MOVE -> { tracker?.addMovement(e); content.translationY = (e.rawY - downY).coerceAtMost(0f) }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                tracker?.addMovement(e)
                tracker?.computeCurrentVelocity(1000)
                val vy = tracker?.yVelocity ?: 0f
                tracker?.recycle(); tracker = null
                if (shouldOpen(-content.translationY, height.toFloat(), -vy)) {
                    content.animate().translationY(-height.toFloat()).setDuration(160).withEndAction(onUnlock).start()
                } else content.animate().translationY(0f).setDuration(160).start()
            }
        }
        return true
    }

    companion object {
        const val POSTER_BLACK = 0xFF181818.toInt()

        /** Open when dragged up a quarter of the screen, or flung up fast enough ([upSpeed] in px/s, positive = upward). */
        fun shouldOpen(draggedUp: Float, screenH: Float, upSpeed: Float): Boolean =
            draggedUp > screenH * 0.25f || (upSpeed > 1200f && draggedUp > 24f)
    }
}
