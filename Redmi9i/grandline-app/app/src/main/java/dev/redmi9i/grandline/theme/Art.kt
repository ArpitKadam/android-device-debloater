package dev.redmi9i.grandline.theme

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Shader
import android.os.Environment
import java.io.File
import kotlin.math.sin

/**
 * The user's own images live in /sdcard/GrandLine/ (posters/, marks/, icons/, wallpapers/).
 * Anything missing or unreadable falls back to art drawn here, so nothing ever crashes on a bad file.
 */
class Art(@Suppress("unused") private val ctx: Context) {
    private val root = File(Environment.getExternalStorageDirectory(), "GrandLine")
    private val cache = HashMap<String, Bitmap>()

    fun file(relativePath: String): File = File(root, relativePath)

    /** Forget cached images, for example after storage permission was just granted. */
    @Synchronized fun clear() { cache.clear() }

    /** Names of the image and video files in wallpapers/, for the settings screen and the quick switcher. */
    fun wallpaperNames(): List<String> =
        File(root, "wallpapers").listFiles { f -> f.isFile && (f.extension.lowercase() in IMAGE_EXT || isVideo(f.name)) }
            ?.map { it.name }?.sorted().orEmpty()

    /** A crew poster (white line art on near-black, 720 px wide), or null when the file is missing. */
    fun poster(name: String, maxSize: Int = 1100): Bitmap? = artOrNull("posters/$name.jpg", maxSize)

    /** The transparent line-art version of a poster, used as a faint watermark. */
    fun mark(name: String): Bitmap? = artOrNull("marks/$name.png", 400)

    /** [name] such as "home.jpg"; returns the user image cover-cropped to size, or a drawn sea scene. */
    @Synchronized
    fun wallpaper(name: String, width: Int, height: Int): Bitmap {
        val key = "wp:$name:$width:$height"
        cache[key]?.let { return it }
        val bmp = runCatching {
            if (isVideo(name)) videoFrame(file("wallpapers/$name"), width, height) else decodeCover(file("wallpapers/$name"), width, height)
        }.getOrNull() ?: drawnSea(width, height)
        if (cache.size >= 3) cache.clear() // at most three wallpapers in memory (about 14 MB)
        cache[key] = bmp
        return bmp
    }

    /** A user art file decoded no larger than [maxSize] px, or null when absent or unreadable. */
    fun artOrNull(relativePath: String, maxSize: Int): Bitmap? = runCatching {
        val f = file(relativePath)
        if (!f.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > maxSize) sample *= 2
        BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    /** A small uncached preview of a wallpaper (image or video still) for the switcher, or null if unreadable. Call off the main thread. */
    fun thumb(name: String, w: Int, h: Int): Bitmap? = runCatching {
        val f = file("wallpapers/$name")
        if (isVideo(name)) videoFrame(f, w, h) else decodeCover(f, w, h)
    }.getOrNull()

    /** A still from a video (one second in, or the first frame), cover-cropped. Shown while the video starts and in the switcher. */
    private fun videoFrame(f: File, w: Int, h: Int): Bitmap? {
        if (!f.isFile) return null
        val r = android.media.MediaMetadataRetriever()
        return try {
            r.setDataSource(f.path)
            val src = r.getFrameAtTime(1_000_000L, android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
                ?: r.getFrameAtTime(0L) ?: return null
            coverCrop(src, w, h)
        } finally { r.release() }
    }

    private fun decodeCover(f: File, w: Int, h: Int): Bitmap? {
        if (!f.isFile) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= w && bounds.outHeight / (sample * 2) >= h) sample *= 2
        val src = BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample }) ?: return null
        return coverCrop(src, w, h)
    }

    private fun coverCrop(src: Bitmap, w: Int, h: Int): Bitmap {
        val scale = maxOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val sw = (src.width * scale).toInt().coerceAtLeast(w)
        val sh = (src.height * scale).toInt().coerceAtLeast(h)
        val scaled = Bitmap.createScaledBitmap(src, sw, sh, true)
        val out = Bitmap.createBitmap(scaled, (sw - w) / 2, (sh - h) / 2, w, h)
        if (scaled !== src) src.recycle()
        if (out !== scaled) scaled.recycle()
        return out
    }

    /** Fallback: a sunset horizon over a wavy sea in the Straw Hat colours. */
    private fun drawnSea(w: Int, h: Int): Bitmap {
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(bmp)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val horizon = h * 0.62f
        p.shader = LinearGradient(0f, 0f, 0f, horizon, intArrayOf(Palette.DeepSea, Palette.SeaBlue, 0xFFE59A3C.toInt()), floatArrayOf(0f, 0.6f, 1f), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w.toFloat(), horizon, p)
        p.shader = LinearGradient(0f, horizon, 0f, h.toFloat(), Palette.SeaBlue, Palette.DeepSea, Shader.TileMode.CLAMP)
        c.drawRect(0f, horizon, w.toFloat(), h.toFloat(), p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.004f
        for (i in 0 until 9) {
            val y = horizon + (h - horizon) * (0.08f + i * 0.1f)
            p.color = Palette.withAlpha(Palette.StrawGold, 0.10f + 0.03f * i)
            val path = Path().apply {
                moveTo(0f, y)
                var x = 0f
                while (x <= w) { lineTo(x, y + sin((x / w) * 6.28f * (2 + i * 0.3f) + i) * w * 0.012f); x += w / 48f }
            }
            c.drawPath(path, p)
        }
        return bmp
    }

    companion object {
        private val IMAGE_EXT = setOf("jpg", "jpeg", "png")
        private val VIDEO_EXT = setOf("mp4", "webm")

        fun isVideo(name: String): Boolean = name.substringAfterLast('.', "").lowercase() in VIDEO_EXT
    }
}
