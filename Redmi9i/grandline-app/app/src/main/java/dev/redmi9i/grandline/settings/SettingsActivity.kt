package dev.redmi9i.grandline.settings

import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import dev.redmi9i.grandline.data.AppRepository
import dev.redmi9i.grandline.data.LayoutOps
import dev.redmi9i.grandline.data.LayoutStore
import dev.redmi9i.grandline.theme.Art
import dev.redmi9i.grandline.theme.Crew
import dev.redmi9i.grandline.theme.Palette
import dev.redmi9i.grandline.theme.Shapes
import dev.redmi9i.grandline.theme.Type
import java.time.LocalDate

/** Launcher settings, reached by long-pressing empty space on the home screen. */
class SettingsActivity : AppCompatActivity() {
    private lateinit var store: LayoutStore
    private lateinit var art: Art
    private lateinit var body: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = LayoutStore(applicationContext)
        art = Art(applicationContext)
        window.statusBarColor = Palette.DeepSea
        body = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = Shapes.dp(context, 18f)
            setPadding(p, Shapes.dp(context, 36f), p, p)
        }
        val scroll = ScrollView(this).apply { addView(body, ViewGroup.LayoutParams(-1, -2)) }
        // The crew member of the day as a faint gold line-art watermark behind the settings.
        val mark = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_END
            Art(applicationContext).mark(Crew.ofDay(LocalDate.now().toEpochDay()))?.let { setImageBitmap(it) }
            setColorFilter(Palette.StrawGold, PorterDuff.Mode.SRC_IN)
            imageAlpha = 30
        }
        setContentView(FrameLayout(this).apply {
            setBackgroundColor(Palette.DeepSea)
            addView(mark, FrameLayout.LayoutParams(Shapes.dp(context, 230f), Shapes.dp(context, 322f), Gravity.BOTTOM or Gravity.END).apply {
                setMargins(0, 0, Shapes.dp(context, 8f), Shapes.dp(context, 8f))
            })
            addView(scroll, FrameLayout.LayoutParams(-1, -1))
        })
        render()
    }

    private fun render() {
        body.removeAllViews()
        title("Grand Line")

        section("Home pages")
        row("Number of pages: ${store.pageCount}",
            "−" to { store.pageCount = store.pageCount - 1; render() },
            "+" to { store.pageCount = store.pageCount + 1; render() })

        row("Add new apps to home: ${if (store.autoPlace) "On" else "Off"}",
            (if (store.autoPlace) "Turn off" else "Turn on") to { store.autoPlace = !store.autoPlace; render() })
        note("Off keeps the home screen clean: every app lives in the drawer until you drag it out.")

        row("Take every icon off the home screen and dock", "Clear" to {
            AlertDialog.Builder(this).setTitle("Clear home screen")
                .setMessage("All icons leave the home pages and the dock. The apps stay in the drawer.")
                .setPositiveButton("Clear") { _, _ -> store.save(LayoutOps.empty(pages = store.pageCount)); render() }
                .setNegativeButton("Cancel", null).show()
        })

        section("Lock screen")
        row("Poster lock screen: ${if (store.lockEnabled) "On" else "Off"}",
            (if (store.lockEnabled) "Turn off" else "Turn on") to { store.lockEnabled = !store.lockEnabled; render() })
        note("A different wanted poster each time the screen wakes. Swipe up to open. It has no PIN, so it is decoration, not security. Needs the Grand Line accessibility service switched on.")
        row("Accessibility settings", "Open" to { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) })

        section("Wallpapers")
        for (page in 0 until store.pageCount) {
            row("Page ${page + 1}: ${store.wallpaperName(page)}", "Change" to { pickWallpaper("Page ${page + 1}") { store.setWallpaperName(page, it); render() } })
        }
        row("Drawer: ${store.drawerWallpaperName()}", "Change" to {
            pickWallpaper("Drawer") { getSharedPreferences("grandline", MODE_PRIVATE).edit().apply { if (it == null) remove("wp_drawer") else putString("wp_drawer", it) }.apply(); render() }
        })
        note("Put images (jpg, png) and videos (mp4) in /sdcard/GrandLine/wallpapers/. Long-press empty home for a one-tap switcher.")

        section("Hidden apps")
        val labels = AppRepository(applicationContext).apps.value.associate { it.key to it.label }
        val hidden = store.hidden
        if (hidden.isEmpty()) note("No hidden apps.") else hidden.sorted().forEach { key ->
            row(labels[key] ?: key.substringBefore('/'), "Unhide" to { store.hidden = store.hidden - key; render() })
        }

        section("Launcher")
        row("Choose the default home app", "Switch" to { startActivity(Intent(Settings.ACTION_HOME_SETTINGS)) })
        note("Lawnchair and MIUI Home stay installed, so you can always switch back.")
    }

    private fun pickWallpaper(title: String, onPick: (String?) -> Unit) {
        val names = art.wallpaperNames()
        val items = (listOf("Default") + names).toTypedArray()
        AlertDialog.Builder(this).setTitle("Wallpaper for $title")
            .setItems(items) { _, i -> onPick(if (i == 0) null else names[i - 1]) }
            .setNegativeButton("Cancel", null).show()
    }

    private fun title(t: String) = body.addView(TextView(this).apply {
        text = t; typeface = Type.title(context); textSize = 34f; setTextColor(Palette.StrawGold)
    })

    private fun section(t: String) = body.addView(TextView(this).apply {
        text = t; typeface = Type.title(context); textSize = 22f; setTextColor(Palette.Parchment)
        setPadding(0, Shapes.dp(context, 22f), 0, Shapes.dp(context, 6f))
    })

    private fun note(t: String) = body.addView(TextView(this).apply {
        text = t; textSize = 13f; setTextColor(Palette.withAlpha(Palette.Parchment, 0.7f))
        setPadding(0, Shapes.dp(context, 4f), 0, Shapes.dp(context, 4f))
    })

    private fun row(text: String, vararg buttons: Pair<String, () -> Unit>) {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            val p = Shapes.dp(context, 12f)
            setPadding(p, p / 2, p / 2, p / 2)
            background = GradientDrawable().apply { cornerRadius = Shapes.dpf(context, 14f); setColor(Palette.withAlpha(Palette.SeaBlue, 0.6f)) }
        }
        r.addView(TextView(this).apply { this.text = text; textSize = 15f; setTextColor(Palette.Parchment) }, LinearLayout.LayoutParams(0, -2, 1f))
        buttons.forEach { (label, action) ->
            r.addView(Button(this).apply {
                this.text = label; setTextColor(Palette.StrawGold); isAllCaps = false
                background = null; setOnClickListener { action() }
            })
        }
        body.addView(r, LinearLayout.LayoutParams(-1, -2).apply { topMargin = Shapes.dp(this@SettingsActivity, 6f) })
    }
}
