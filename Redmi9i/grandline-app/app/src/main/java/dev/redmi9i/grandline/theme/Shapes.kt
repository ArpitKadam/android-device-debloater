package dev.redmi9i.grandline.theme

import android.content.Context

object Shapes {
    /** Corner radius of an icon medallion as a fraction of its side. */
    const val ICON_RADIUS_FRACTION = 0.28f

    fun dp(ctx: Context, v: Float): Int = (v * ctx.resources.displayMetrics.density + 0.5f).toInt()
    fun dpf(ctx: Context, v: Float): Float = v * ctx.resources.displayMetrics.density
}
