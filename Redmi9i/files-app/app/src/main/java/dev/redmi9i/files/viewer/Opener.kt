package dev.redmi9i.files.viewer

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.core.content.FileProvider
import dev.redmi9i.files.storage.FileRef
import dev.redmi9i.files.storage.Kind
import dev.redmi9i.files.storage.kindOf
import java.io.File

/** Routes a file to the matching in-app viewer, or to another app when we have no viewer. */
object Opener {
    const val EXTRA_PATH = "path"

    fun mimeOf(name: String): String =
        MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substringAfterLast('.', "").lowercase()) ?: "*/*"

    fun uriFor(ctx: Context, path: String) =
        FileProvider.getUriForFile(ctx, ctx.packageName + ".fileprovider", File(path))

    fun open(ctx: Context, ref: FileRef) {
        val viewer: Class<out Activity>? = when (kindOf(ref.name)) {
            Kind.IMAGE -> ImageViewerActivity::class.java
            Kind.VIDEO, Kind.AUDIO -> MediaViewerActivity::class.java
            Kind.PDF -> PdfViewerActivity::class.java
            Kind.TEXT -> TextViewerActivity::class.java
            Kind.DOCX, Kind.XLSX -> HtmlDocViewerActivity::class.java
            Kind.ZIP -> ZipViewerActivity::class.java
            else -> null
        }
        if (viewer == null) openExternal(ctx, ref)
        else ctx.startActivity(Intent(ctx, viewer).putExtra(EXTRA_PATH, ref.path))
    }

    fun openExternal(ctx: Context, ref: FileRef) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uriFor(ctx, ref.path), mimeOf(ref.name))
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            ctx.startActivity(Intent.createChooser(intent, "Open with").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (e: Exception) {
            Toast.makeText(ctx, "No app can open this file", Toast.LENGTH_SHORT).show()
        }
    }
}
