package dev.redmi9i.files.ops

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import dev.redmi9i.files.R
import dev.redmi9i.files.storage.FileRef
import dev.redmi9i.files.storage.FsRouter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

enum class Op { COPY, MOVE, DELETE, EXTRACT, COMPRESS }

data class OpResult(val id: Long, val message: String, val ok: Boolean)

/** Process-wide channel from the service back to whichever screen is showing. */
object OperationBus {
    private val _result = MutableStateFlow<OpResult?>(null)
    val result: StateFlow<OpResult?> = _result
    @Volatile var busy = false
    fun post(message: String, ok: Boolean) { _result.value = OpResult(System.nanoTime(), message, ok) }
}

/** Runs one file operation at a time in the foreground, with a progress notification and Cancel. */
class OperationService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var job: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_CANCEL) { job?.cancel(); return START_NOT_STICKY }
        if (intent == null) { stopSelf(); return START_NOT_STICKY }
        if (job?.isActive == true) {
            OperationBus.post("Another operation is still running", false)
            return START_NOT_STICKY
        }
        val op = Op.valueOf(intent.getStringExtra(EXTRA_OP)!!)
        val sources = intent.getStringArrayListExtra(EXTRA_SOURCES).orEmpty().map { FileRef("local", it) }
        val dest = intent.getStringExtra(EXTRA_DEST)?.let { FileRef("local", it) }
        val policy = Conflict.valueOf(intent.getStringExtra(EXTRA_POLICY) ?: Conflict.KEEP_BOTH.name)
        val zipName = intent.getStringExtra(EXTRA_ZIP_NAME)

        startForeground(NOTIF_ID, notification("Starting…", 0, 0))
        OperationBus.busy = true
        val router = FsRouter(applicationContext)
        val engine = OperationEngine { router.fsOf(it) }
        job = scope.launch {
            var lastEmit = 0L
            val progress: (Progress) -> Unit = { p ->
                val now = System.currentTimeMillis()
                if (now - lastEmit > 300) {
                    lastEmit = now
                    val pct = if (p.totalBytes > 0) (p.doneBytes * 100 / p.totalBytes).toInt() else 0
                    notify(notification("${p.currentName}  (${p.doneFiles}/${p.totalFiles})", pct, 100))
                }
            }
            try {
                when (op) {
                    Op.COPY -> engine.copy(sources, dest!!, { _, _ -> policy }, progress)
                    Op.MOVE -> engine.move(sources, dest!!, { _, _ -> policy }, progress)
                    Op.DELETE -> engine.delete(sources, progress)
                    Op.EXTRACT -> engine.extractZip(sources.single(), dest!!, progress)
                    Op.COMPRESS -> engine.compressZip(sources, FileRef("local", dest!!.path.trimEnd('/') + "/" + zipName), progress)
                }
                OperationBus.post(doneText(op, sources.size), true)
            } catch (e: CancellationException) {
                OperationBus.post("Cancelled", false)
            } catch (e: Exception) {
                OperationBus.post(e.message ?: e.javaClass.simpleName, false)
            }
        }
        job!!.invokeOnCompletion {
            OperationBus.busy = false
            stopForeground(true)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun doneText(op: Op, n: Int) = when (op) {
        Op.COPY -> "Copied $n item(s)"
        Op.MOVE -> "Moved $n item(s)"
        Op.DELETE -> "Deleted $n item(s)"
        Op.EXTRACT -> "Extracted"
        Op.COMPRESS -> "Zip created"
    }

    private fun notify(n: Notification) =
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(NOTIF_ID, n)

    private fun notification(text: String, progress: Int, max: Int): Notification {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "File operations", NotificationManager.IMPORTANCE_LOW))
        val cancel = PendingIntent.getService(
            this, 0, Intent(this, OperationService::class.java).setAction(ACTION_CANCEL), PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_file)
            .setContentTitle("Files")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(max, progress, max == 0)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    override fun onDestroy() { scope.coroutineContext[Job]?.cancel(); super.onDestroy() }

    companion object {
        private const val CHANNEL = "ops"
        private const val NOTIF_ID = 42
        private const val ACTION_CANCEL = "dev.redmi9i.files.CANCEL"
        private const val EXTRA_OP = "op"
        private const val EXTRA_SOURCES = "sources"
        private const val EXTRA_DEST = "dest"
        private const val EXTRA_POLICY = "policy"
        private const val EXTRA_ZIP_NAME = "zip"

        fun start(ctx: Context, op: Op, sources: List<String>, dest: String?, policy: Conflict = Conflict.KEEP_BOTH, zipName: String? = null) {
            val i = Intent(ctx, OperationService::class.java)
                .putExtra(EXTRA_OP, op.name)
                .putStringArrayListExtra(EXTRA_SOURCES, ArrayList(sources))
                .putExtra(EXTRA_DEST, dest)
                .putExtra(EXTRA_POLICY, policy.name)
                .putExtra(EXTRA_ZIP_NAME, zipName)
            ctx.startForegroundService(i)
        }
    }
}
