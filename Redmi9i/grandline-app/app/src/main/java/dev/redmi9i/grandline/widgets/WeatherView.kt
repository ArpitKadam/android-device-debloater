package dev.redmi9i.grandline.widgets

import android.content.Context
import android.view.Gravity
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import dev.redmi9i.grandline.data.LayoutStore
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.theme.Shapes
import dev.redmi9i.grandline.theme.Type
import dev.redmi9i.grandline.weather.Weather
import dev.redmi9i.grandline.weather.WeatherParse
import dev.redmi9i.grandline.weather.WeatherRepo
import java.time.format.TextStyle
import java.util.Locale

/**
 * Weather card: today's sky and temperature, a three-day outlook, and a line of Grand Line flavour.
 * Tap to refresh (or to pick a city the first time); long-press to change the city.
 * [onTemp] gets a short "28°" for the collapsed pill.
 */
class WeatherView(ctx: Context, store: LayoutStore, private val onTemp: (String) -> Unit) : LinearLayout(ctx) {
    private val repo = WeatherRepo(store)
    private val glyph = WeatherGlyph(ctx)
    private val temp = TextView(ctx).apply { typeface = Type.title(ctx); textSize = 34f; setTextColor(Palette.StrawGold) }
    private val label = TextView(ctx).apply { textSize = 14f; setTextColor(Palette.Parchment) }
    private val outlook = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.END }
    private val note = TextView(ctx).apply {
        textSize = 11f; maxLines = 2; setTextColor(Palette.withAlpha(Palette.Parchment, 0.75f))
    }
    private var loading = false

    init {
        orientation = VERTICAL
        val g = Shapes.dp(ctx, 8f)
        val top = LinearLayout(ctx).apply { orientation = HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        top.addView(glyph, LayoutParams(Shapes.dp(ctx, 46f), Shapes.dp(ctx, 46f)))
        val now = LinearLayout(ctx).apply { orientation = VERTICAL; setPadding(g, 0, g, 0) }
        now.addView(temp); now.addView(label)
        top.addView(now, LayoutParams(-2, -2))
        top.addView(outlook, LayoutParams(0, -2, 1f))
        addView(top, LayoutParams(-1, -2))
        addView(note, LayoutParams(-1, -2).apply { topMargin = Shapes.dp(ctx, 4f) })

        setOnClickListener { if (repo.place == null) askCity() else refresh(force = true) }
        setOnLongClickListener { askCity(); true }
        show(repo.cached())
    }

    /** Called whenever the drawer opens: shows the cache at once and fetches again only if it is old. */
    fun refresh(force: Boolean = false) {
        val place = repo.place ?: return show(null)
        show(repo.cached())
        if (loading || (!force && !repo.isStale())) return
        loading = true
        Thread {
            val w = repo.fetch(place)
            post {
                loading = false
                if (w != null) show(w) else if (repo.cached() == null) note.text = "Cannot reach the weather service. Tap to retry."
            }
        }.start()
    }

    private fun show(w: Weather?) {
        val place = repo.place
        outlook.removeAllViews()
        if (place == null) {
            glyph.sky = dev.redmi9i.grandline.weather.Sky.Clear
            temp.text = "--°"; label.text = "No city yet"
            note.text = "Tap to set your city for sea conditions."
            onTemp("")
            return
        }
        if (w == null) {
            temp.text = "--°"; label.text = place.name.substringBefore(',')
            note.text = "Loading the sea conditions..."
            onTemp("")
            return
        }
        val c = WeatherParse.describe(w.code, w.isDay)
        glyph.sky = c.sky
        temp.text = "${w.tempC}°"
        label.text = c.label
        note.text = "${c.flavor}\nFeels ${w.feelsC}°  ·  ${w.humidity}%  ·  ${w.windKmh} km/h  ·  ${place.name.substringBefore(',')}"
        onTemp("${w.tempC}°")
        for (d in w.days.drop(1).take(3)) {
            val col = LinearLayout(context).apply { orientation = VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
            col.addView(TextView(context).apply {
                text = d.date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()); textSize = 11f
                setTextColor(Palette.withAlpha(Palette.Parchment, 0.85f)); gravity = Gravity.CENTER
            })
            col.addView(WeatherGlyph(context).apply { sky = WeatherParse.describe(d.code, true).sky },
                LayoutParams(Shapes.dp(context, 24f), Shapes.dp(context, 24f)))
            col.addView(TextView(context).apply {
                text = "${d.maxC}°/${d.minC}°"; textSize = 11f; setTextColor(Palette.Parchment); gravity = Gravity.CENTER
            })
            outlook.addView(col, LayoutParams(Shapes.dp(context, 54f), -2))
        }
    }

    private fun askCity() {
        val input = EditText(context).apply { hint = "City, for example Pune"; setSingleLine(); setText(repo.place?.name?.substringBefore(',').orEmpty()) }
        val pad = Shapes.dp(context, 20f)
        val box = LinearLayout(context).apply { setPadding(pad, pad / 2, pad, 0); addView(input, LayoutParams(-1, -2)) }
        AlertDialog.Builder(context).setTitle("Weather city").setView(box)
            .setPositiveButton("Set") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                note.text = "Searching for $name..."
                Thread {
                    val p = repo.setCity(name)
                    post {
                        if (p == null) { Toast.makeText(context, "Could not find $name", Toast.LENGTH_SHORT).show(); show(repo.cached()) }
                        else refresh(force = true)
                    }
                }.start()
            }
            .setNegativeButton("Cancel", null).show()
    }
}
