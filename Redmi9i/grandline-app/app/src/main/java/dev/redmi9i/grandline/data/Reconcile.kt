package dev.redmi9i.grandline.data

data class Reconciled(val layout: Layout, val known: Set<String>)

private fun packageOf(key: String) = key.substringBefore('/')

/**
 * Brings the saved layout in line with what is installed.
 * - First run ([current] null): dock from [dockPrefer] (package names, in order), the rest onto the home pages.
 * - Later: uninstalled and hidden apps leave the layout; apps never seen before ([known]) go to the first free cell.
 * Packages in [skipFromHome] (other launchers) are never auto-placed, they stay in the drawer.
 */
fun reconcile(
    current: Layout?,
    installed: List<AppEntry>,
    known: Set<String>,
    hidden: Set<String>,
    dockPrefer: List<String>,
    skipFromHome: Set<String>,
    pages: Int = 2,
    autoPlace: Boolean = true,
): Reconciled {
    // An empty app list is a package-manager hiccup, never real (Settings is always installed): keep what we have.
    if (installed.isEmpty()) return Reconciled(current ?: LayoutOps.empty(pages = pages), known)

    val installedKeys = installed.map { it.key }.toSet()
    val visible = installed.filter { it.key !in hidden && packageOf(it.key) !in skipFromHome }

    if (current == null) {
        // autoPlace off: a clean home, every app stays in the drawer until the user drags it out.
        if (!autoPlace) return Reconciled(LayoutOps.empty(pages = pages), installedKeys)
        val dock = dockPrefer.mapNotNull { pkg -> visible.firstOrNull { packageOf(it.key) == pkg }?.key }
        val seeded = LayoutOps.seed(LayoutOps.empty(pages = pages), dock, visible.map { it.key })
        return Reconciled(seeded, installedKeys)
    }

    var layout = LayoutOps.prune(current, installedKeys)
    hidden.forEach { layout = LayoutOps.remove(layout, it) }
    if (!autoPlace) return Reconciled(layout, installedKeys)
    for (app in visible) {
        if (app.key in known || LayoutOps.contains(layout, app.key)) continue
        val spot = LayoutOps.firstFree(layout) ?: break
        layout = LayoutOps.place(layout, app.key, spot.first, spot.second, spot.third)
    }
    return Reconciled(layout, installedKeys)
}
