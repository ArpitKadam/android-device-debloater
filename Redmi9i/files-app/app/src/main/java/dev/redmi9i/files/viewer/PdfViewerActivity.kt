package dev.redmi9i.files.viewer

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.LruCache
import android.view.Gravity
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.util.concurrent.Executors

/** PDF pages in a scrolling list. PdfRenderer is single-threaded, so renders run on one worker. */
class PdfViewerActivity : ViewerActivity() {
    private var pfd: ParcelFileDescriptor? = null
    private var renderer: PdfRenderer? = null
    private val worker = Executors.newSingleThreadExecutor()

    override fun onReady(savedInstanceState: Bundle?) {
        content.setBackgroundColor(0xFF06223F.toInt())
        val r = try {
            val d = ParcelFileDescriptor.open(File(ref.path), ParcelFileDescriptor.MODE_READ_ONLY)
            pfd = d
            PdfRenderer(d)
        } catch (e: Exception) {
            content.addView(
                TextView(this).apply { text = "Cannot open this PDF"; setTextColor(Color.WHITE); gravity = Gravity.CENTER },
            )
            return
        }
        renderer = r
        toolbar.subtitle = "${r.pageCount} page(s)"
        val list = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@PdfViewerActivity)
            adapter = PageAdapter(r)
            setItemViewCacheSize(2)
        }
        content.addView(list)
    }

    private inner class PageAdapter(private val r: PdfRenderer) : RecyclerView.Adapter<PageAdapter.VH>() {
        private val width = minOf(resources.displayMetrics.widthPixels, 1080)
        private val cache = object : LruCache<Int, Bitmap>(16 * 1024 * 1024) {
            override fun sizeOf(key: Int, value: Bitmap) = value.byteCount
        }

        inner class VH(val image: ImageView) : RecyclerView.ViewHolder(image)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val iv = ImageView(parent.context).apply {
                isForceDarkAllowed = false // page bitmaps are white-on-purpose; do not let the system invert them
                layoutParams = RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (width * 1.4f).toInt())
                setBackgroundColor(Color.WHITE)
                scaleType = ImageView.ScaleType.FIT_CENTER
                adjustViewBounds = true
            }
            return VH(iv)
        }

        override fun getItemCount() = r.pageCount

        override fun onBindViewHolder(h: VH, position: Int) {
            h.image.tag = position
            val hit = cache.get(position)
            if (hit != null) { show(h, hit); return }
            h.image.setImageDrawable(null)
            worker.execute {
                val bmp = runCatching { render(position) }.getOrNull() ?: return@execute
                cache.put(position, bmp)
                h.image.post { if (h.image.tag == position) show(h, bmp) }
            }
        }

        private fun show(h: VH, bmp: Bitmap) {
            h.image.layoutParams = h.image.layoutParams.apply { height = ViewGroup.LayoutParams.WRAP_CONTENT }
            h.image.setImageBitmap(bmp)
        }

        private fun render(index: Int): Bitmap = synchronized(r) {
            r.openPage(index).use { page ->
                val h = (width.toFloat() * page.height / page.width).toInt()
                val bmp = Bitmap.createBitmap(width, h, Bitmap.Config.ARGB_8888)
                bmp.eraseColor(Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
    }

    override fun onDestroy() {
        worker.shutdown()
        runCatching { renderer?.close() }
        runCatching { pfd?.close() }
        super.onDestroy()
    }
}
