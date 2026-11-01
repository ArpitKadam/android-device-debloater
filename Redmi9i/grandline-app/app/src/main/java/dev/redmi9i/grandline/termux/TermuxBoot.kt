package dev.redmi9i.grandline.termux

import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * Termux:Boot is unreliable on MIUI. The launcher starts on every boot, so it asks Termux to run
 * the idempotent script that takes the wake lock and starts crond (rclone sync).
 * Needs `allow-external-apps = true` in termux.properties and the RUN_COMMAND permission granted.
 */
object TermuxBoot {
    private const val HOME = "/data/data/com.termux/files/home"
    private var done = false

    fun ensureServices(ctx: Context) {
        if (done) return
        done = true
        try {
            val i = Intent("com.termux.RUN_COMMAND")
                .setComponent(ComponentName("com.termux", "com.termux.app.RunCommandService"))
                .putExtra("com.termux.RUN_COMMAND_PATH", "$HOME/bin/ensure-services.sh")
                .putExtra("com.termux.RUN_COMMAND_WORKDIR", HOME)
                .putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            ctx.startService(i)
        } catch (_: Exception) {
            // Termux missing, permission not granted or background start refused: the cron job simply isn't started.
        }
    }
}
