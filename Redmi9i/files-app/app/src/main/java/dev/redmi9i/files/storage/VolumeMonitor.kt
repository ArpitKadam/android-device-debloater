package dev.redmi9i.files.storage

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

data class Volume(
    val id: String,
    val label: String,
    val root: FileRef,
    val removable: Boolean,
    val total: Long,
    val free: Long,
    val writable: Boolean,
)

/** Lists mounted volumes and refreshes the list when an SD card or USB drive is mounted or removed. */
class VolumeMonitor(private val ctx: Context, private val router: FsRouter) {
    private val sm = ctx.getSystemService(Context.STORAGE_SERVICE) as StorageManager
    private val state = MutableStateFlow(load())
    val volumes: StateFlow<List<Volume>> = state

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) = refresh()
    }

    fun start() {
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_MEDIA_MOUNTED)
            addAction(Intent.ACTION_MEDIA_UNMOUNTED)
            addAction(Intent.ACTION_MEDIA_EJECT)
            addAction(Intent.ACTION_MEDIA_REMOVED)
            addAction(Intent.ACTION_MEDIA_BAD_REMOVAL)
            addDataScheme("file")
        }
        ctx.registerReceiver(receiver, f)
        refresh()
    }

    fun stop() {
        runCatching { ctx.unregisterReceiver(receiver) }
    }

    fun refresh() {
        state.value = load()
    }

    private fun load(): List<Volume> = sm.storageVolumes
        .filter { it.state == Environment.MEDIA_MOUNTED || it.state == Environment.MEDIA_MOUNTED_READ_ONLY }
        .mapNotNull { v ->
            val uuid = v.uuid
            val path = if (v.isPrimary) Environment.getExternalStorageDirectory().path else uuid?.let { "/storage/$it" }
            path ?: return@mapNotNull null
            val stat = runCatching { StatFs(path) }.getOrNull()
            Volume(
                id = uuid ?: "primary",
                label = v.getDescription(ctx),
                root = FileRef("local", path),
                removable = v.isRemovable,
                total = stat?.totalBytes ?: 0L,
                free = stat?.availableBytes ?: 0L,
                writable = !v.isRemovable || (uuid != null && router.hasGrant(uuid)),
            )
        }
}
