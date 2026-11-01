package dev.redmi9i.grandline.widgets

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.View
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.weather.Sky

/** A small weather picture drawn in code (sun, moon, clouds, rain...) in the Straw Hat colours. No image files needed. */
class WeatherGlyph(ctx: Context) : View(ctx) {
    var sky: Sky = Sky.Clear
        set(v) { field = v; invalidate() }

    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeCap = Paint.Cap.ROUND }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        if (s <= 0f) return
        c.save()
        c.translate((width - s) / 2f, (height - s) / 2f)
        when (sky) {
            Sky.Clear -> sun(c, 0.5f * s, 0.5f * s, 0.24f * s, s)
            Sky.Moon -> moon(c, s)
            Sky.PartlyCloudy -> { sun(c, 0.62f * s, 0.36f * s, 0.17f * s, s); cloud(c, 0.46f * s, 0.62f * s, 0.62f * s, CLOUD) }
            Sky.Cloudy -> { cloud(c, 0.62f * s, 0.42f * s, 0.46f * s, DARK_CLOUD); cloud(c, 0.44f * s, 0.62f * s, 0.62f * s, CLOUD) }
            Sky.Fog -> { cloud(c, 0.5f * s, 0.4f * s, 0.62f * s, CLOUD); lines(c, s, listOf(0.68f, 0.8f, 0.92f)) }
            Sky.Drizzle -> { cloud(c, 0.5f * s, 0.4f * s, 0.62f * s, CLOUD); rain(c, s, 3, 0.05f) }
            Sky.Rain -> { cloud(c, 0.5f * s, 0.38f * s, 0.62f * s, DARK_CLOUD); rain(c, s, 4, 0.07f) }
            Sky.Snow -> { cloud(c, 0.5f * s, 0.38f * s, 0.62f * s, CLOUD); snow(c, s) }
            Sky.Storm -> { cloud(c, 0.5f * s, 0.36f * s, 0.64f * s, DARK_CLOUD); bolt(c, s) }
        }
        c.restore()
    }

    private fun sun(c: Canvas, cx: Float, cy: Float, r: Float, s: Float) {
        fill.color = Palette.StrawGold
        c.drawCircle(cx, cy, r, fill)
        stroke.color = Palette.StrawGold; stroke.strokeWidth = s * 0.045f
        for (i in 0 until 8) {
            val a = Math.toRadians(i * 45.0)
            val x = Math.cos(a).toFloat(); val y = Math.sin(a).toFloat()
            c.drawLine(cx + x * r * 1.35f, cy + y * r * 1.35f, cx + x * r * 1.75f, cy + y * r * 1.75f, stroke)
        }
    }

    private fun moon(c: Canvas, s: Float) {
        val a = Path().apply { addCircle(0.5f * s, 0.5f * s, 0.3f * s, Path.Direction.CW) }
        val b = Path().apply { addCircle(0.64f * s, 0.42f * s, 0.26f * s, Path.Direction.CW) }
        a.op(b, Path.Op.DIFFERENCE)
        fill.color = Palette.Parchment
        c.drawPath(a, fill)
    }

    /** Three puffs on a flat base, centred on ([cx], [cy]) and [w] wide. */
    private fun cloud(c: Canvas, cx: Float, cy: Float, w: Float, color: Int) {
        fill.color = color
        val h = w * 0.5f
        c.drawCircle(cx - w * 0.22f, cy + h * 0.1f, h * 0.38f, fill)
        c.drawCircle(cx + w * 0.05f, cy - h * 0.12f, h * 0.5f, fill)
        c.drawCircle(cx + w * 0.28f, cy + h * 0.12f, h * 0.34f, fill)
        c.drawRoundRect(cx - w * 0.5f, cy + h * 0.1f, cx + w * 0.5f, cy + h * 0.45f, h * 0.2f, h * 0.2f, fill)
    }

    private fun lines(c: Canvas, s: Float, ys: List<Float>) {
        stroke.color = CLOUD; stroke.strokeWidth = s * 0.05f
        for (y in ys) c.drawLine(0.2f * s, y * s, 0.8f * s, y * s, stroke)
    }

    private fun rain(c: Canvas, s: Float, drops: Int, width: Float) {
        stroke.color = RAIN; stroke.strokeWidth = s * width
        val step = 0.5f / drops
        for (i in 0 until drops) {
            val x = (0.27f + step * i + step / 2) * s
            c.drawLine(x, 0.68f * s, x - 0.06f * s, 0.88f * s, stroke)
        }
    }

    private fun snow(c: Canvas, s: Float) {
        fill.color = Color_White
        for ((x, y) in listOf(0.3f to 0.74f, 0.5f to 0.86f, 0.7f to 0.74f, 0.4f to 0.64f + 0.2f, 0.6f to 0.9f)) c.drawCircle(x * s, y * s, s * 0.035f, fill)
    }

    private fun bolt(c: Canvas, s: Float) {
        fill.color = Palette.StrawGold
        val p = Path().apply {
            moveTo(0.54f * s, 0.58f * s); lineTo(0.38f * s, 0.8f * s); lineTo(0.5f * s, 0.8f * s)
            lineTo(0.44f * s, 0.98f * s); lineTo(0.66f * s, 0.7f * s); lineTo(0.53f * s, 0.7f * s); close()
        }
        c.drawPath(p, fill)
    }

    private companion object {
        val CLOUD = Palette.Parchment
        val DARK_CLOUD = 0xFF9FB3C8.toInt()
        val RAIN = 0xFF7FB8FF.toInt()
        const val Color_White = 0xFFFFFFFF.toInt()
    }
}
