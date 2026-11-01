package dev.redmi9i.grandline.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.view.View
import dev.redmi9i.grandline.theme.Palette

/** The launcher's own background: the chosen image plus a dark scrim so icons stay readable. */
class WallpaperView(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val src = Rect()
    private val dst = Rect()
    private val scrim = Paint().apply { color = Palette.withAlpha(Palette.DeepSea, 0.38f) }

    var bitmap: Bitmap? = null
        set(v) { field = v; invalidate() }

    /** 0f clear to 1f almost black; the drawer darkens it further. */
    var dim = 0.38f
        set(v) { field = v; scrim.color = Palette.withAlpha(Palette.DeepSea, v); invalidate() }

    override fun onDraw(c: Canvas) {
        bitmap?.let {
            src.set(0, 0, it.width, it.height)
            dst.set(0, 0, width, height)
            c.drawBitmap(it, src, dst, paint)
        }
        c.drawRect(0f, 0f, width.toFloat(), height.toFloat(), scrim)
    }
}
