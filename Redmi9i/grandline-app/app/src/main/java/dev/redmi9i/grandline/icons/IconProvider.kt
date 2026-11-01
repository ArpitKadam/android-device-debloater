package dev.redmi9i.grandline.icons

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import dev.redmi9i.grandline.data.AppEntry
import dev.redmi9i.grandline.theme.Art
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.theme.Shapes

/** Where a cell gets its icon from. */
interface IconProvider {
    fun iconFor(entry: AppEntry): Drawable
}

/** The user-drawn icon when one exists, else the app's own icon clipped to a rounded medallion with a thin gold ring. Cached, about 6 MB at most. */
class MedallionIconProvider(private val ctx: Context, private val sizePx: Int, private val art: Art) : IconProvider {
    /** App package to the user-drawn file in /sdcard/GrandLine/icons/. Any other app gets the medallion below. */
    private val themed = mapOf(
        "com.brave.browser" to "browser", "com.android.camera" to "camera", "dev.redmi9i.files" to "files",
        "com.miui.securitycenter" to "security", "com.android.settings" to "settings",
        "org.telegram.messenger.web" to "telegram", "com.termux" to "termux",
        "com.termux.api" to "termux", "com.termux.boot" to "termux",
    )

    private val pm = ctx.packageManager
    private val cache = object : LruCache<String, Bitmap>(6 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    override fun iconFor(entry: AppEntry): Drawable {
        val bmp = cache.get(entry.key) ?: render(entry).also { cache.put(entry.key, it) }
        return BitmapDrawable(ctx.resources, bmp)
    }

    private fun render(entry: AppEntry): Bitmap {
        themed[entry.key.substringBefore('/')]?.let { name ->
            art.artOrNull("icons/$name.png", sizePx * 2)?.let { return Bitmap.createScaledBitmap(it, sizePx, sizePx, true) }
        }
        val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val s = sizePx.toFloat()
        val radius = s * Shapes.ICON_RADIUS_FRACTION
        val shape = RectF(s * 0.03f, s * 0.03f, s * 0.97f, s * 0.97f)
        val clip = Path().apply { addRoundRect(shape, radius, radius, Path.Direction.CW) }

        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.shader = LinearGradient(0f, 0f, 0f, s, Palette.SeaBlue, Palette.DeepSea, Shader.TileMode.CLAMP)
        c.drawPath(clip, paint)
        paint.shader = null

        val icon = runCatching { pm.getActivityIcon(entry.component) }.getOrNull()
        if (icon != null) {
            c.save()
            c.clipPath(clip)
            if (icon is AdaptiveIconDrawable) {
                // The adaptive icon fills the medallion; its own mask is replaced by ours.
                val pad = (s * -0.18f).toInt() // adaptive layers are 108dp with a 72dp safe zone
                icon.setBounds(pad, pad, sizePx - pad, sizePx - pad)
            } else {
                val inset = (s * 0.14f).toInt()
                icon.setBounds(inset, inset, sizePx - inset, sizePx - inset)
            }
            icon.draw(c)
            c.restore()
        }

        paint.style = Paint.Style.STROKE
        paint.strokeWidth = s * 0.035f
        paint.color = Palette.withAlpha(Palette.StrawGold, 0.9f)
        c.drawPath(clip, paint)
        return bmp
    }
}
