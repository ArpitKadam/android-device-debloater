package dev.redmi9i.files.viewer

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.webkit.WebView
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import dev.redmi9i.files.docs.DocxRenderer
import dev.redmi9i.files.docs.XlsxRenderer
import dev.redmi9i.files.storage.Kind
import dev.redmi9i.files.storage.kindOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** docx and xlsx, rendered to HTML and shown in a locked-down WebView (no JavaScript, no network, no file access). */
class HtmlDocViewerActivity : ViewerActivity() {
    override fun onReady(savedInstanceState: Bundle?) {
        val web = WebView(this).apply {
            setBackgroundColor(Color.WHITE)
            settings.apply {
                javaScriptEnabled = false
                allowFileAccess = false
                allowContentAccess = false
                blockNetworkLoads = true
                builtInZoomControls = true
                displayZoomControls = false
                useWideViewPort = true
                loadWithOverviewMode = true
            }
        }
        content.addView(web)
        val file = File(ref.path)
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { if (kindOf(ref.name) == Kind.XLSX) XlsxRenderer.toHtml(file) else DocxRenderer.toHtml(file) }
            }
            result.onSuccess { web.loadDataWithBaseURL(null, it, "text/html", "utf-8", null) }
                .onFailure { showError(it) }
        }
    }

    private fun showError(e: Throwable) {
        content.removeAllViews()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(48, 48, 48, 48) }
        box.addView(TextView(this).apply {
            text = "Cannot preview this file.\n${e.message ?: e.javaClass.simpleName}"
            gravity = Gravity.CENTER; textSize = 16f
        })
        box.addView(Button(this).apply { text = "Open with another app"; setOnClickListener { Opener.openExternal(this@HtmlDocViewerActivity, ref) } })
        content.addView(box)
    }
}
