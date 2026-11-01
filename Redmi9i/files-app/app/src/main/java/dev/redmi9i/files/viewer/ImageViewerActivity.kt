package dev.redmi9i.files.viewer

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ImageViewerActivity : ViewerActivity() {
    override fun onReady(savedInstanceState: Bundle?) {
        content.setBackgroundColor(Color.BLACK)
        val view = ZoomImageView(this)
        content.addView(view)
        lifecycleScope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { decode(File(ref.path)) }.getOrNull() }
            if (bmp != null) view.setImageBitmap(bmp) else showError()
        }
    }

    /** Decode no larger than about 2048 px on the long side so a 12 MP photo cannot exhaust memory. */
    private fun decode(f: File): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, bounds)
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > 2048) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun showError() {
        content.addView(
            TextView(this).apply { text = "Cannot show this image"; setTextColor(Color.WHITE); gravity = Gravity.CENTER },
        )
    }
}
