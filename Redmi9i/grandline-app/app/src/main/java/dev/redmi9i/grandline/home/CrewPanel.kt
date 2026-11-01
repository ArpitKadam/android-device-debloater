package dev.redmi9i.grandline.home

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextClock
import android.widget.TextView
import dev.redmi9i.grandline.data.LayoutStore
import dev.redmi9i.grandline.theme.Art
import dev.redmi9i.grandline.theme.Crew
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.theme.Shapes
import dev.redmi9i.grandline.theme.Type
import dev.redmi9i.grandline.widgets.WeatherView
import java.time.LocalDate

/**
 * Sits at the top of the drawer and is open by default: the wanted poster of the day, a big clock, and the weather.
 * One tap on the poster or clock folds it into a slim pill (time, temperature, crew member); another tap opens it again.
 * Tapping the weather refreshes it instead, and long-pressing it changes the city.
 */
class CrewPanel(ctx: Context, private val art: Art, store: LayoutStore) : FrameLayout(ctx) {
    private val collapsedH = Shapes.dp(ctx, 46f)
    private val topH = Shapes.dp(ctx, 224f)
    private val weatherH = Shapes.dp(ctx, 116f)
    private val expandedH = topH + weatherH

    private val thumb = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val poster = ImageView(ctx).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
    private val smallTime = clock(ctx, 22f)
    private val smallTemp = TextView(ctx).apply { textSize = 15f; setTextColor(Palette.Parchment); setPadding(0, 0, Shapes.dp(ctx, 10f), 0) }
    private val bigTime = clock(ctx, 52f).apply { format12Hour = "h:mm" }
    private val ampm = TextClock(ctx).apply {
        format12Hour = "a"; format24Hour = ""; textSize = 16f; setTextColor(Palette.StrawGold); typeface = Type.title(ctx)
    }
    private val smallName = TextView(ctx).apply { textSize = 13f; setTextColor(Palette.withAlpha(Palette.Parchment, 0.8f)) }
    private val bigName = TextView(ctx).apply { typeface = Type.title(ctx); textSize = 22f; setTextColor(Palette.Parchment) }
    private val date = TextClock(ctx).apply {
        format12Hour = "EEEE, d MMMM"; format24Hour = "EEEE, d MMMM"
        textSize = 14f; setTextColor(Palette.withAlpha(Palette.Parchment, 0.85f))
    }
    private val weather = WeatherView(ctx, store) { smallTemp.text = it }
    private val collapsedRow = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private val expandedBox = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }

    var isExpanded = true
        private set
    private var member = ""
    private var anim: ValueAnimator? = null
    private var heightPx = expandedH

    init {
        background = GradientDrawable().apply {
            cornerRadius = Shapes.dpf(ctx, 23f)
            setColor(Palette.withAlpha(Palette.SeaBlue, 0.78f))
            setStroke(Shapes.dp(ctx, 1f), Palette.withAlpha(Palette.StrawGold, 0.6f))
        }
        clipToOutline = true
        outlineProvider = ViewOutlineProvider.BACKGROUND
        val pad = Shapes.dp(ctx, 10f)

        collapsedRow.setPadding(pad, 0, pad + pad / 2, 0)
        collapsedRow.addView(thumb, LinearLayout.LayoutParams(Shapes.dp(ctx, 30f), Shapes.dp(ctx, 30f)).apply { rightMargin = pad })
        collapsedRow.addView(smallTime, LinearLayout.LayoutParams(0, -2, 1f))
        collapsedRow.addView(smallTemp)
        collapsedRow.addView(smallName)
        collapsedRow.alpha = 0f
        addView(collapsedRow, LayoutParams(-1, collapsedH))

        val top = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; setPadding(pad, pad, pad, pad / 2) }
        val pw = (topH - pad * 2) * 5 / 7
        top.addView(poster, LinearLayout.LayoutParams(pw, -1))
        val right = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad + pad / 2, 0, 0, 0) }
        right.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.BOTTOM
            addView(bigTime)
            addView(ampm, LinearLayout.LayoutParams(-2, -2).apply { leftMargin = Shapes.dp(ctx, 6f); bottomMargin = Shapes.dp(ctx, 10f) })
        })
        right.addView(date)
        right.addView(bigName, LinearLayout.LayoutParams(-2, -2).apply { topMargin = pad })
        top.addView(right, LinearLayout.LayoutParams(0, -1, 1f))
        top.setOnClickListener { toggle() } // the weather below handles its own taps
        expandedBox.addView(top, LinearLayout.LayoutParams(-1, topH))
        weather.setPadding(pad + pad / 2, 0, pad + pad / 2, pad)
        expandedBox.addView(weather, LinearLayout.LayoutParams(-1, weatherH))
        addView(expandedBox, LayoutParams(-1, expandedH))

        setOnClickListener { if (!isExpanded) toggle() } // anywhere on the pill opens it
    }

    private fun clock(ctx: Context, sp: Float) = TextClock(ctx).apply {
        format12Hour = "h:mm a"; format24Hour = "HH:mm"
        typeface = Type.title(ctx); textSize = sp; setTextColor(Palette.StrawGold)
    }

    /** Loads the crew member of the day and the weather. Cheap to call whenever the drawer opens. */
    fun refresh() {
        weather.refresh()
        val today = Crew.ofDay(LocalDate.now().toEpochDay())
        if (today == member) return
        member = today
        val label = Crew.label(today)
        smallName.text = label; bigName.text = label
        Thread {
            val bmp = art.poster(today, 600)
            post { if (member == today) { thumb.setImageBitmap(bmp); poster.setImageBitmap(bmp) } }
        }.start()
    }

    fun toggle() = set(!isExpanded)

    /** Back to the default (open) with no animation, for when the drawer closes. */
    fun reset() {
        anim?.cancel()
        isExpanded = true
        heightPx = expandedH
        collapsedRow.alpha = 0f; expandedBox.alpha = 1f
        requestLayout()
    }

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        super.onMeasure(widthSpec, MeasureSpec.makeMeasureSpec(heightPx, MeasureSpec.EXACTLY))
    }

    private fun set(expand: Boolean) {
        isExpanded = expand
        anim?.cancel()
        val to = if (expand) expandedH else collapsedH
        anim = ValueAnimator.ofInt(heightPx, to).apply {
            duration = 220
            addUpdateListener {
                heightPx = it.animatedValue as Int
                val f = (heightPx - collapsedH).toFloat() / (expandedH - collapsedH)
                collapsedRow.alpha = 1f - f; expandedBox.alpha = f
                requestLayout()
            }
            start()
        }
    }
}
