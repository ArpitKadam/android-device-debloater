package dev.redmi9i.grandline.lock

import android.content.ComponentName
import android.content.Context
import android.provider.Settings

/**
 * Android switches an accessibility service off when its app is force-stopped (for example by swiping it away in
 * recents on MIUI). The launcher restarts constantly, so it switches the service back on each time it starts.
 * Needs the WRITE_SECURE_SETTINGS permission: `adb shell pm grant dev.redmi9i.grandline android.permission.WRITE_SECURE_SETTINGS`.
 */
object LockEnabler {
    /** The stock accessibility menu. While it is enabled it owns the navigation-bar button (with Google Assistant in it). */
    const val STOCK_MENU = "com.android.settings/com.android.settings.accessibility.accessibilitymenu.AccessibilityMenuService"

    /** The system rewrites the entry in its short form, so match both spellings. */
    private val STOCK_FORMS = setOf(STOCK_MENU, "com.android.settings/.accessibility.accessibilitymenu.AccessibilityMenuService")

    /** [current] is the colon-separated enabled_accessibility_services value; returns it with [flat] present exactly once and the stock menu removed. */
    fun withService(current: String?, flat: String): String {
        val parts = current.orEmpty().split(':').filter { it.isNotBlank() && it !in STOCK_FORMS }
        return if (flat in parts) parts.joinToString(":") else (parts + flat).joinToString(":")
    }

    fun ensureEnabled(ctx: Context) {
        val flat = ComponentName(ctx, LockService::class.java).flattenToString()
        runCatching {
            val cr = ctx.contentResolver
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            val wanted = withService(current, flat)
            if (wanted != current.orEmpty()) Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, wanted)
            if (Settings.Secure.getInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 0) != 1) {
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
            }
        } // without the permission this throws SecurityException and the service simply stays as the user left it
    }
}
