package dev.redmi9i.grandline.home

import android.content.Context
import android.graphics.Color
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.redmi9i.grandline.data.AppEntry
import dev.redmi9i.grandline.icons.IconProvider
import dev.redmi9i.grandline.theme.Shapes

/** One app: its icon and (optionally) its name. Used on home pages, in the dock and in the drawer. */
class IconCellView(ctx: Context, private val iconPx: Int, labels: Boolean = true) : LinearLayout(ctx) {
    private val icon = ImageView(ctx)
    private val label = TextView(ctx)
    var key: String? = null
        private set

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        addView(icon, LayoutParams(iconPx, iconPx))
        if (labels) {
            label.apply {
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 11f)
                setTextColor(Color.WHITE)
                maxLines = 1
                ellipsize = TextUtils.TruncateAt.END
                gravity = Gravity.CENTER
                setShadowLayer(Shapes.dpf(ctx, 2f), 0f, Shapes.dpf(ctx, 1f), 0xB3000000.toInt())
            }
            addView(label, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply { topMargin = Shapes.dp(ctx, 4f) })
        }
    }

    fun bind(e: AppEntry, provider: IconProvider) {
        key = e.key
        icon.setImageDrawable(provider.iconFor(e))
        label.text = e.label
        contentDescription = e.label
    }
}
