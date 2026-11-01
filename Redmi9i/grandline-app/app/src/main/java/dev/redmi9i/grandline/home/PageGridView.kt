package dev.redmi9i.grandline.home

import android.content.Context
import android.view.View
import android.view.ViewGroup
import dev.redmi9i.grandline.data.Cell

/** One home page: a rows x cols grid. Each child carries its [Cell] as its tag. */
class PageGridView(ctx: Context, val rows: Int, val cols: Int) : ViewGroup(ctx) {
    fun setCells(entries: List<Pair<Cell, View>>) {
        removeAllViews()
        entries.forEach { (cell, v) -> v.tag = cell; addView(v) }
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        val h = MeasureSpec.getSize(heightSpec)
        setMeasuredDimension(w, h)
        val cw = w / cols
        val ch = h / rows
        for (i in 0 until childCount) {
            getChildAt(i).measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY))
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cw = (r - l) / cols
        val ch = (b - t) / rows
        for (i in 0 until childCount) {
            val v = getChildAt(i)
            val cell = v.tag as? Cell ?: continue
            v.layout(cell.c * cw, cell.r * ch, (cell.c + 1) * cw, (cell.r + 1) * ch)
        }
    }

    /** Grid cell (row, column) under a point in this view's own coordinates, clamped to the grid. */
    fun cellAt(x: Float, y: Float): Pair<Int, Int> {
        val c = (x / (width.coerceAtLeast(1).toFloat() / cols)).toInt().coerceIn(0, cols - 1)
        val r = (y / (height.coerceAtLeast(1).toFloat() / rows)).toInt().coerceIn(0, rows - 1)
        return r to c
    }
}
