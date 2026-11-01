package dev.redmi9i.grandline.home

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.theme.Shapes

class PageDots(ctx: Context) : View(ctx) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val radius = Shapes.dpf(ctx, 3.5f)
    private val gap = Shapes.dpf(ctx, 10f)

    var count = 2
        set(v) { field = v; requestLayout(); invalidate() }
    var selected = 0
        set(v) { field = v; invalidate() }

    override fun onMeasure(w: Int, h: Int) {
        val width = ((count - 1) * gap + count * radius * 2).toInt()
        setMeasuredDimension(resolveSize(width, w), resolveSize((radius * 2).toInt() + 4, h))
    }

    override fun onDraw(c: Canvas) {
        val total = (count - 1) * gap + count * radius * 2
        var x = (width - total) / 2f + radius
        for (i in 0 until count) {
            paint.color = if (i == selected) Palette.StrawGold else Palette.withAlpha(Palette.Foam, 0.4f)
            c.drawCircle(x, height / 2f, radius, paint)
            x += radius * 2 + gap
        }
    }
}
