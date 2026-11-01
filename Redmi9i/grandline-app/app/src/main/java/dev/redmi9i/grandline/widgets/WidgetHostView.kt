package dev.redmi9i.grandline.widgets

import android.content.Context
import android.view.View
import android.widget.LinearLayout

/** A home-screen widget. Stage 4 onwards adds implementations by registering them in [WidgetRegistry]. */
interface HomeWidget {
    val id: String
    val title: String
    fun createView(ctx: Context): View
}

object WidgetRegistry {
    private val items = LinkedHashMap<String, HomeWidget>()
    fun register(w: HomeWidget) { items[w.id] = w }
    fun all(): List<HomeWidget> = items.values.toList()
}

/** The area at the top of page 1. Hidden while no widget is enabled, so stage 2 shows an unchanged grid. */
class WidgetHostView(ctx: Context) : LinearLayout(ctx) {
    init { orientation = VERTICAL; visibility = GONE }

    fun show(widgets: List<HomeWidget>) {
        removeAllViews()
        widgets.forEach { addView(it.createView(context)) }
        visibility = if (widgets.isEmpty()) GONE else VISIBLE
    }
}
