package dev.redmi9i.grandline.lock

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.theme.Type

/** The actions of the accessibility-button menu. [label] is the small name under the icon and what screen readers say. */
enum class QuickKind(val label: String) {
    POWER("Power"), RECENTS("Recents"), LOCK("Lock"), NOTIFICATIONS("Notifications"),
    QUICK_SETTINGS("Quick settings"), SCREENSHOT("Screenshot"), ACCESSIBILITY("Accessibility"),
}

/** A tile: a gold line icon drawn on a 24 x 24 grid and scaled to the view (no image files), with its name under it. */
class QuickIcon(ctx: Context, private val kind: QuickKind) : View(ctx) {
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1.9f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        color = Palette.StrawGold
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Palette.StrawGold }
    private val box = RectF()
    private val name = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Palette.Parchment; textAlign = Paint.Align.CENTER
        typeface = Type.title(ctx); textSize = 11f * ctx.resources.displayMetrics.scaledDensity
    }

    init { contentDescription = kind.label }

    private fun rect(l: Float, t: Float, r: Float, b: Float) = box.apply { set(l, t, r, b) }

    override fun onDraw(c: Canvas) {
        val glyph = height * 0.40f // icon in the upper part of the tile, its name below
        c.drawText(kind.label, width / 2f, height * 0.88f, name)
        c.save()
        c.translate((width - glyph) / 2f, height * 0.12f)
        c.scale(glyph / 24f, glyph / 24f)
        when (kind) {
            QuickKind.POWER -> {
                c.drawArc(rect(4.5f, 4.5f, 19.5f, 19.5f), -60f, 300f, false, line)
                c.drawLine(12f, 3f, 12f, 11f, line)
            }
            QuickKind.RECENTS -> {
                c.drawRoundRect(rect(4f, 4f, 20f, 20f), 3f, 3f, line)
                c.drawRoundRect(rect(9f, 9f, 15f, 15f), 1.5f, 1.5f, line)
            }
            QuickKind.LOCK -> {
                c.drawRoundRect(rect(6f, 11f, 18f, 20f), 2f, 2f, line)
                c.drawArc(rect(8.5f, 4f, 15.5f, 14f), 180f, 180f, false, line)
                c.drawLine(8.5f, 9f, 8.5f, 11f, line)
                c.drawLine(15.5f, 9f, 15.5f, 11f, line)
            }
            QuickKind.NOTIFICATIONS -> {
                c.drawArc(rect(7f, 5f, 17f, 15f), 180f, 180f, false, line)
                c.drawLine(7f, 10f, 5.5f, 17f, line)
                c.drawLine(17f, 10f, 18.5f, 17f, line)
                c.drawLine(5.5f, 17f, 18.5f, 17f, line)
                c.drawArc(rect(10f, 17.5f, 14f, 21.5f), 0f, 180f, false, line)
            }
            QuickKind.QUICK_SETTINGS -> {
                for ((y, knob) in listOf(7f to 9f, 12f to 15f, 17f to 8f)) {
                    c.drawLine(4f, y, 20f, y, line)
                    c.drawCircle(knob, y, 2.4f, fill)
                }
            }
            QuickKind.SCREENSHOT -> {
                for ((cx, cy) in listOf(5f to 5f, 19f to 5f, 5f to 19f, 19f to 19f)) {
                    val dx = if (cx < 12f) 5f else -5f
                    val dy = if (cy < 12f) 5f else -5f
                    c.drawLine(cx, cy, cx + dx, cy, line)
                    c.drawLine(cx, cy, cx, cy + dy, line)
                }
                c.drawCircle(12f, 12f, 2.2f, fill)
            }
            QuickKind.ACCESSIBILITY -> {
                c.drawCircle(12f, 12f, 9f, line)
                c.drawCircle(12f, 8f, 1.4f, fill)
                c.drawLine(7.5f, 10.5f, 16.5f, 10.5f, line)
                c.drawLine(12f, 10.5f, 12f, 14f, line)
                c.drawLine(12f, 14f, 9.5f, 18f, line)
                c.drawLine(12f, 14f, 14.5f, 18f, line)
            }
        }
        c.restore()
    }
}
