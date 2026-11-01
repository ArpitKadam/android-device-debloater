package dev.redmi9i.grandline.data

import org.json.JSONArray
import org.json.JSONObject

/** [key] is a flattened ComponentName, for example "com.brave.browser/com.google.android.apps.chrome.Main". */
data class Cell(val r: Int, val c: Int, val key: String)
data class Page(val cells: List<Cell>)
data class Layout(val rows: Int, val cols: Int, val pages: List<Page>, val dock: List<String?>)

/** Pure functions over [Layout]; every function returns a new value and never mutates its input. */
object LayoutOps {
    const val DOCK_SLOTS = 4

    fun empty(rows: Int = 5, cols: Int = 4, pages: Int = 2): Layout =
        Layout(rows, cols, List(pages) { Page(emptyList()) }, List(DOCK_SLOTS) { null })

    fun contains(l: Layout, key: String) = l.dock.contains(key) || l.pages.any { p -> p.cells.any { it.key == key } }

    fun remove(l: Layout, key: String): Layout = l.copy(
        pages = l.pages.map { p -> Page(p.cells.filter { it.key != key }) },
        dock = l.dock.map { if (it == key) null else it },
    )

    /** First free cell as (page, row, column), scanning pages, then rows, then columns. */
    fun firstFree(l: Layout): Triple<Int, Int, Int>? {
        for ((pi, p) in l.pages.withIndex()) for (r in 0 until l.rows) for (c in 0 until l.cols) {
            if (p.cells.none { it.r == r && it.c == c }) return Triple(pi, r, c)
        }
        return null
    }

    private sealed class Origin {
        data class Home(val page: Int, val r: Int, val c: Int) : Origin()
        data class Dock(val slot: Int) : Origin()
        object None : Origin()
    }

    private fun originOf(l: Layout, key: String): Origin {
        l.dock.indexOf(key).takeIf { it >= 0 }?.let { return Origin.Dock(it) }
        for ((pi, p) in l.pages.withIndex()) p.cells.firstOrNull { it.key == key }?.let { return Origin.Home(pi, it.r, it.c) }
        return Origin.None
    }

    private fun setCell(l: Layout, page: Int, r: Int, c: Int, key: String): Layout = l.copy(
        pages = l.pages.mapIndexed { i, p ->
            if (i != page) p else Page(p.cells.filter { !(it.r == r && it.c == c) } + Cell(r, c, key))
        },
    )

    private fun cellKey(l: Layout, page: Int, r: Int, c: Int) = l.pages[page].cells.firstOrNull { it.r == r && it.c == c }?.key

    /** Put [key] on a home cell. The item that was there swaps into [key]'s old spot (or the first free cell). */
    fun place(l: Layout, key: String, page: Int, r: Int, c: Int): Layout {
        if (page !in l.pages.indices || r !in 0 until l.rows || c !in 0 until l.cols) return l
        val origin = originOf(l, key)
        val displaced = cellKey(l, page, r, c)?.takeIf { it != key }
        var out = setCell(remove(l, key), page, r, c, key)
        if (displaced != null) out = putSomewhere(out, displaced, origin)
        return out
    }

    /** Put [key] in a dock slot; the previous occupant swaps into [key]'s old spot (or the first free cell). */
    fun placeDock(l: Layout, key: String, slot: Int): Layout {
        if (slot !in l.dock.indices) return l
        val origin = originOf(l, key)
        val displaced = l.dock[slot]?.takeIf { it != key }
        var out = remove(l, key).let { it.copy(dock = it.dock.toMutableList().also { d -> d[slot] = key }) }
        if (displaced != null) out = putSomewhere(out, displaced, origin)
        return out
    }

    private fun putSomewhere(l: Layout, key: String, origin: Origin): Layout = when (origin) {
        is Origin.Home -> setCell(l, origin.page, origin.r, origin.c, key)
        is Origin.Dock -> l.copy(dock = l.dock.toMutableList().also { it[origin.slot] = key })
        Origin.None -> firstFree(l)?.let { (p, r, c) -> setCell(l, p, r, c, key) } ?: l
    }

    /** Dock first, then pages left to right. Keys that do not fit stay unplaced (drawer only). */
    fun seed(l: Layout, dockKeys: List<String>, keys: List<String>): Layout {
        var out = l
        dockKeys.distinct().take(l.dock.size).forEachIndexed { i, k -> out = placeDock(out, k, i) }
        val rest = keys.distinct().filter { !contains(out, it) }
        for (k in rest) {
            val spot = firstFree(out) ?: break
            out = setCell(out, spot.first, spot.second, spot.third, k)
        }
        return out
    }

    /** Grow to [n] pages, or shrink by dropping empty pages from the end; a page that holds items is never dropped. */
    fun withPageCount(l: Layout, n: Int): Layout {
        var pages = l.pages
        while (pages.size < n) pages = pages + Page(emptyList())
        while (pages.size > n && pages.last().cells.isEmpty()) pages = pages.dropLast(1)
        return l.copy(pages = pages)
    }

    fun prune(l: Layout, installed: Set<String>): Layout = l.copy(
        pages = l.pages.map { p -> Page(p.cells.filter { it.key in installed }) },
        dock = l.dock.map { if (it != null && it in installed) it else null },
    )

    fun toJson(l: Layout): String = JSONObject().apply {
        put("rows", l.rows)
        put("cols", l.cols)
        put("pages", JSONArray().also { pa ->
            l.pages.forEach { p ->
                pa.put(JSONArray().also { ca -> p.cells.forEach { ca.put(JSONObject().put("r", it.r).put("c", it.c).put("k", it.key)) } })
            }
        })
        put("dock", JSONArray().also { da -> l.dock.forEach { da.put(it ?: JSONObject.NULL) } })
    }.toString()

    /** Null when the text is not a valid layout: bad JSON, empty grid, a cell outside the grid, or a key used twice. */
    fun fromJson(s: String): Layout? = try {
        val o = JSONObject(s)
        val rows = o.getInt("rows"); val cols = o.getInt("cols")
        if (rows <= 0 || cols <= 0) null else {
            val seen = HashSet<String>()
            var ok = true
            val pages = o.getJSONArray("pages").let { pa ->
                List(pa.length()) { i ->
                    val ca = pa.getJSONArray(i)
                    Page(List(ca.length()) { j ->
                        val co = ca.getJSONObject(j)
                        val cell = Cell(co.getInt("r"), co.getInt("c"), co.getString("k"))
                        if (cell.r !in 0 until rows || cell.c !in 0 until cols || !seen.add(cell.key)) ok = false
                        cell
                    })
                }
            }
            val dock = o.getJSONArray("dock").let { da ->
                List(da.length()) { i -> if (da.isNull(i)) null else da.getString(i).also { if (!seen.add(it)) ok = false } }
            }
            if (!ok || pages.isEmpty() || dock.isEmpty()) null else Layout(rows, cols, pages, dock)
        }
    } catch (e: Exception) {
        null
    }
}
