package dev.redmi9i.grandline.home

import android.app.Dialog
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewGroup
import android.view.Window
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.redmi9i.grandline.theme.Art
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.theme.Shapes
import dev.redmi9i.grandline.theme.Type

/** Bottom sheet with a thumbnail of every wallpaper (images and videos). One tap applies it. */
class WallpaperSheet(
    ctx: Context,
    private val art: Art,
    private val current: String,
    private val onPick: (String) -> Unit,
    private val onSettings: () -> Unit,
) : Dialog(ctx) {

    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        val c = context
        val col = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            val p = Shapes.dp(c, 16f)
            setPadding(p, p, p, p)
            background = GradientDrawable().apply {
                val r = Shapes.dpf(c, 22f)
                cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
                setColor(Palette.DeepSea)
                setStroke(Shapes.dp(c, 1f), Palette.withAlpha(Palette.StrawGold, 0.5f))
            }
        }
        col.addView(TextView(c).apply {
            text = "Wallpaper"; typeface = Type.title(c); textSize = 22f; setTextColor(Palette.StrawGold)
        })
        val row = LinearLayout(c).apply { orientation = LinearLayout.HORIZONTAL }
        val tw = Shapes.dp(c, 86f); val th = Shapes.dp(c, 150f)
        val names = art.wallpaperNames()
        for (name in names) {
            val img = ImageView(c).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                setBackgroundColor(Palette.SeaBlue)
                setOnClickListener { onPick(name); dismiss() }
            }
            val badge = if (Art.isVideo(name)) "▶ " else ""
            val label = TextView(c).apply {
                text = badge + name.substringBeforeLast('.'); textSize = 11f; maxLines = 1
                setTextColor(if (name == current) Palette.StrawGold else Palette.Parchment); gravity = Gravity.CENTER
            }
            val cell = LinearLayout(c).apply {
                orientation = LinearLayout.VERTICAL
                if (name == current) background = GradientDrawable().apply {
                    cornerRadius = Shapes.dpf(c, 8f); setStroke(Shapes.dp(c, 2f), Palette.StrawGold)
                }
                val m = Shapes.dp(c, 3f); setPadding(m, m, m, m)
                addView(img, LinearLayout.LayoutParams(tw, th))
                addView(label, LinearLayout.LayoutParams(tw, -2))
            }
            row.addView(cell, LinearLayout.LayoutParams(-2, -2).apply { rightMargin = Shapes.dp(c, 8f) })
            Thread { // decoding a video still takes a moment, so keep it off the main thread
                val bmp: Bitmap? = art.thumb(name, tw, th)
                img.post { if (bmp != null) img.setImageBitmap(bmp) }
            }.start()
        }
        col.addView(HorizontalScrollView(c).apply { isHorizontalScrollBarEnabled = false; addView(row) },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = Shapes.dp(c, 10f) })
        col.addView(TextView(c).apply {
            text = "Launcher settings"; textSize = 15f; setTextColor(Palette.StrawGold)
            setPadding(0, Shapes.dp(c, 14f), 0, 0)
            setOnClickListener { onSettings(); dismiss() }
        })
        setContentView(FrameLayout(c).apply { addView(col, ViewGroup.LayoutParams(-1, -2)) })
        window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setGravity(Gravity.BOTTOM)
        }
    }
}
