package dev.redmi9i.grandline.data

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.widget.Toast
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** [key] is the component flattened to a string, the same key the layout stores. */
data class AppEntry(val key: String, val label: String, val component: ComponentName, val isUser: Boolean)

fun sortApps(apps: List<AppEntry>): List<AppEntry> =
    apps.sortedWith(compareBy<AppEntry, String>(String.CASE_INSENSITIVE_ORDER) { it.label }.thenBy { it.key })

/** Hidden apps never show; a blank query shows everything else. */
fun filterApps(apps: List<AppEntry>, query: String, hidden: Set<String>): List<AppEntry> {
    val q = query.trim().lowercase()
    return apps.filter { it.key !in hidden && (q.isEmpty() || it.label.lowercase().contains(q)) }
}

/** Launchable apps, kept fresh as packages are installed, removed or updated. */
class AppRepository(private val ctx: Context) {
    private val pm = ctx.packageManager
    private val state = MutableStateFlow(load())
    val apps: StateFlow<List<AppEntry>> = state

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    fun start() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ctx.registerReceiver(receiver, f)
        refresh()
    }

    fun stop() { runCatching { ctx.unregisterReceiver(receiver) } }

    fun refresh() { state.value = load() }

    private fun load(): List<AppEntry> {
        val query = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return sortApps(
            pm.queryIntentActivities(query, 0)
                .filter { it.activityInfo.packageName != ctx.packageName }
                .map { ri ->
                    val cn = ComponentName(ri.activityInfo.packageName, ri.activityInfo.name)
                    val flags = ri.activityInfo.applicationInfo.flags
                    val system = flags and ApplicationInfo.FLAG_SYSTEM != 0 && flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                    AppEntry(cn.flattenToString(), ri.loadLabel(pm).toString(), cn, isUser = !system)
                },
        )
    }

    fun launch(entry: AppEntry) {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
            .setComponent(entry.component)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        try {
            ctx.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(ctx, "Cannot open ${entry.label}", Toast.LENGTH_SHORT).show()
        }
    }
}
