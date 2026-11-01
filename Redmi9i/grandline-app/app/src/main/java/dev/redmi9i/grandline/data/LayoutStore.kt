package dev.redmi9i.grandline.data

import android.content.Context

/** Saved layout and launcher preferences. A corrupt layout is kept as a backup and rebuilt from defaults. */
class LayoutStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("grandline", Context.MODE_PRIVATE)

    fun load(): Layout? {
        val s = prefs.getString("layout", null) ?: return null
        val l = LayoutOps.fromJson(s)
        if (l == null) prefs.edit().putString("layout_bak", s).remove("layout").apply()
        return l
    }

    fun save(l: Layout) { prefs.edit().putString("layout", LayoutOps.toJson(l)).apply() }

    var hidden: Set<String>
        get() = prefs.getStringSet("hidden", emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet("hidden", v).apply() }

    /** Packages already hidden by default once, so unhiding one in Settings sticks while new defaults still apply. */
    var seededHidden: Set<String>
        get() = prefs.getStringSet("hidden_seeded_pkgs", null)
            ?: if (prefs.getBoolean("hidden_seeded", false)) setOf("com.android.providers.downloads.ui", "com.miui.securitycenter") else emptySet()
        set(v) { prefs.edit().putStringSet("hidden_seeded_pkgs", v).apply() }

    /** Every app key the launcher has already seen, so an app removed from home is not auto-added again. */
    var known: Set<String>
        get() = prefs.getStringSet("known", emptySet()) ?: emptySet()
        set(v) { prefs.edit().putStringSet("known", v).apply() }

    /** Off by default: the home screen stays clean and every app lives in the drawer. */
    var autoPlace: Boolean
        get() = prefs.getBoolean("auto_place", false)
        set(v) { prefs.edit().putBoolean("auto_place", v).apply() }

    /** The poster lock screen draws only while this is on (and its accessibility service is enabled). */
    var lockEnabled: Boolean
        get() = prefs.getBoolean("lock_enabled", true)
        set(v) { prefs.edit().putBoolean("lock_enabled", v).apply() }

    /** The poster the lock screen showed last, so the next wake picks a different one. */
    var lastLockPoster: String?
        get() = prefs.getString("lock_last", null)
        set(v) { prefs.edit().putString("lock_last", v).apply() }

    /** The wallpaper file the home screen is showing right now, so the recents cover can show the same one. */
    var currentWallpaper: String?
        get() = prefs.getString("wp_current", null)
        set(v) { prefs.edit().putString("wp_current", v).apply() }

    /** Identifies the picture last written to the system wallpaper (see SystemWallpaper). */
    var systemWallpaperKey: String?
        get() = prefs.getString("wp_system", null)
        set(v) { prefs.edit().apply { if (v == null) remove("wp_system") else putString("wp_system", v) }.apply() }

    /** The weather city as "name|lat|lon" (see Place.encode), the last good forecast JSON and when it arrived. */
    var weatherPlace: String?
        get() = prefs.getString("weather_place", null)
        set(v) { prefs.edit().apply { if (v == null) remove("weather_place") else putString("weather_place", v) }.apply() }

    var weatherJson: String?
        get() = prefs.getString("weather_json", null)
        set(v) { prefs.edit().apply { if (v == null) remove("weather_json") else putString("weather_json", v) }.apply() }

    var weatherAt: Long
        get() = prefs.getLong("weather_at", 0L)
        set(v) { prefs.edit().putLong("weather_at", v).apply() }

    var pageCount: Int
        get() = prefs.getInt("pages", 2).coerceIn(1, 4)
        set(v) { prefs.edit().putInt("pages", v.coerceIn(1, 4)).apply() }

    /** File name in wallpapers/ chosen for a page; page 0 defaults to home.jpg, others to pageN.jpg. */
    fun wallpaperName(page: Int): String =
        prefs.getString("wp_$page", null) ?: if (page == 0) "home.jpg" else "page${page + 1}.jpg"

    fun setWallpaperName(page: Int, name: String?) {
        prefs.edit().apply { if (name == null) remove("wp_$page") else putString("wp_$page", name) }.apply()
    }

    fun drawerWallpaperName(): String = prefs.getString("wp_drawer", null) ?: "drawer.jpg"
}
