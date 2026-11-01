package dev.redmi9i.grandline.lock

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import dev.redmi9i.grandline.data.LayoutStore
import dev.redmi9i.grandline.theme.Art
import dev.redmi9i.grandline.theme.Crew
import kotlin.random.Random

/**
 * Hosts the poster lock screen. When the screen turns off it puts a full-screen overlay window up with the next
 * poster, so the poster is already there when you wake the phone. As an enabled accessibility service the system
 * itself restarts it after a kill or a reboot, which a plain background service would not get on MIUI.
 * It listens for nothing on screen and reads nothing.
 */
class LockService : AccessibilityService() {
    private var view: LockView? = null
    private var menu: QuickMenuView? = null
    private val ui = Handler(Looper.getMainLooper())
    private val autoHideMenu = Runnable { hideMenu() }
    private val buttonCallback = object : AccessibilityButtonController.AccessibilityButtonCallback() {
        override fun onClicked(controller: AccessibilityButtonController) { if (menu != null) hideMenu() else showMenu() }
    }
    private val art by lazy { Art(applicationContext) }
    private val screenOff = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { showLock() }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        registerReceiver(screenOff, IntentFilter(Intent.ACTION_SCREEN_OFF))
        accessibilityButtonController.registerAccessibilityButtonCallback(buttonCallback)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    private fun showLock() {
        val store = LayoutStore(applicationContext)
        if (!store.lockEnabled) return
        hideMenu()
        hideLock()
        val name = Crew.nextFor(store.lastLockPoster, Random.nextInt())
        store.lastLockPoster = name
        val v = LockView(this, art.poster(name, 1100)) { hideLock() }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val real = android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
        val top = statusBarHeight()
        // Start above the status bar and reach the bottom of the physical screen so no wallpaper shows at either end.
        val lp = WindowManager.LayoutParams(
            real.widthPixels, real.heightPixels + top,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_FULLSCREEN or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.OPAQUE,
        ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START; x = 0; y = -top }
        runCatching { wm.addView(v, lp); view = v }
    }

    /** The nav-bar accessibility button: a small menu of useful actions (the stock one had Google Assistant in it). */
    private fun showMenu() {
        if (view != null) return
        val actions = listOf<Pair<QuickKind, () -> Unit>>(
            QuickKind.POWER to { global(GLOBAL_ACTION_POWER_DIALOG) },
            QuickKind.RECENTS to { global(GLOBAL_ACTION_RECENTS) },
            QuickKind.LOCK to { global(GLOBAL_ACTION_LOCK_SCREEN) },
            QuickKind.NOTIFICATIONS to { global(GLOBAL_ACTION_NOTIFICATIONS) },
            QuickKind.QUICK_SETTINGS to { global(GLOBAL_ACTION_QUICK_SETTINGS) },
            QuickKind.SCREENSHOT to { global(GLOBAL_ACTION_TAKE_SCREENSHOT) },
            QuickKind.ACCESSIBILITY to {
                hideMenu()
                startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            },
        )
        val v = QuickMenuView(this, actions) { hideMenu() }
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val real = android.util.DisplayMetrics().also { wm.defaultDisplay.getRealMetrics(it) }
        val nav = resources.getIdentifier("navigation_bar_height", "dimen", "android").let { if (it > 0) resources.getDimensionPixelSize(it) else 0 }
        val lp = WindowManager.LayoutParams(
            real.widthPixels, real.heightPixels - nav,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
            PixelFormat.TRANSLUCENT,
        ).apply { gravity = android.view.Gravity.TOP or android.view.Gravity.START }
        runCatching { wm.addView(v, lp); menu = v; ui.postDelayed(autoHideMenu, 8000) } // nothing tells us when Home or Back was pressed, so it also times out
    }

    private fun hideMenu() {
        ui.removeCallbacks(autoHideMenu)
        val v = menu ?: return
        menu = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
    }

    /** Close the menu first so it is not in the screenshot or in the way, then run the system action. */
    private fun global(action: Int) {
        hideMenu()
        ui.postDelayed({ performGlobalAction(action) }, 150)
    }

    private fun statusBarHeight(): Int {
        val id = resources.getIdentifier("status_bar_height", "dimen", "android")
        return if (id > 0) resources.getDimensionPixelSize(id) else 0
    }

    private fun hideLock() {
        val v = view ?: return
        view = null
        runCatching { (getSystemService(WINDOW_SERVICE) as WindowManager).removeView(v) }
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(screenOff) }
        runCatching { accessibilityButtonController.unregisterAccessibilityButtonCallback(buttonCallback) }
        hideMenu()
        hideLock()
        super.onDestroy()
    }
}
