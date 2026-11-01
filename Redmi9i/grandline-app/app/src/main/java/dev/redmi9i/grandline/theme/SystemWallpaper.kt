package dev.redmi9i.grandline.theme

import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import dev.redmi9i.grandline.data.LayoutStore

/**
 * Keeps Android's own wallpaper equal to the launcher's current one. The launcher draws its wallpaper itself, so
 * the system wallpaper is only ever seen through see-through system screens such as the stock recents, which would
 * otherwise flash the MIUI default for a moment before our recents cover appears.
 */
object SystemWallpaper {
    /** [key] changes whenever the picture does (name plus file time), so the system wallpaper is rewritten only then. */
    fun sync(ctx: Context, store: LayoutStore, key: String, bmp: Bitmap) {
        val wm = WallpaperManager.getInstance(ctx)
        if (wm.wallpaperInfo != null) return // a live wallpaper is set: leave it alone
        if (store.systemWallpaperKey == key) return
        store.systemWallpaperKey = key
        Thread {
            val ok = runCatching { wm.setBitmap(bmp, null, true, WallpaperManager.FLAG_SYSTEM) }.isSuccess
            if (!ok) store.systemWallpaperKey = null // try again next time
        }.start()
    }
}
