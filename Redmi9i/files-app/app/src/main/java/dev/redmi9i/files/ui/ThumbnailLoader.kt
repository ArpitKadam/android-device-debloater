package dev.redmi9i.files.ui

import android.graphics.Bitmap
import android.media.ThumbnailUtils
import android.util.LruCache
import android.util.Size
import dev.redmi9i.files.storage.Kind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Small, size-capped thumbnail cache. Three worker threads at most so a big folder cannot starve the phone. */
@OptIn(ExperimentalCoroutinesApi::class)
class ThumbnailLoader(private val scope: CoroutineScope) {
    private val cache = object : LruCache<String, Bitmap>(8 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val workers = Dispatchers.IO.limitedParallelism(3)

    fun cached(path: String): Bitmap? = cache.get(path)

    fun load(path: String, kind: Kind, onLoaded: (Bitmap?) -> Unit): Job = scope.launch(workers) {
        val bmp = runCatching {
            val size = Size(128, 128)
            when (kind) {
                Kind.IMAGE -> ThumbnailUtils.createImageThumbnail(File(path), size, null)
                Kind.VIDEO -> ThumbnailUtils.createVideoThumbnail(File(path), size, null)
                else -> null
            }
        }.getOrNull()
        if (bmp != null) cache.put(path, bmp)
        withContext(Dispatchers.Main) { onLoaded(bmp) }
    }
}
