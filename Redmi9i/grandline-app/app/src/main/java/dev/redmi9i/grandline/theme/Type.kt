package dev.redmi9i.grandline.theme

import android.content.Context
import android.graphics.Typeface
import androidx.core.content.res.ResourcesCompat
import dev.redmi9i.grandline.R

/** Pirata One for titles; a bold serif if the bundled font cannot load. */
object Type {
    @Volatile private var cached: Typeface? = null

    fun title(ctx: Context): Typeface = cached ?: synchronized(this) {
        cached ?: (runCatching { ResourcesCompat.getFont(ctx, R.font.pirata_one) }.getOrNull()
            ?: Typeface.create(Typeface.SERIF, Typeface.BOLD)).also { cached = it }
    }
}
