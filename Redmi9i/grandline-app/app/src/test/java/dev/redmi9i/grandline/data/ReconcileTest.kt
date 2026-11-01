package dev.redmi9i.grandline.data

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReconcileTest {
    private fun app(pkg: String, label: String = pkg) = AppEntry("$pkg/Main", label, ComponentName(pkg, "Main"), isUser = true)

    private val installed = listOf(app("brave"), app("files"), app("tg"), app("termux"), app("calc"), app("clock"), app("lawn"))

    private fun home(l: Layout) = l.pages.flatMap { p -> p.cells.map { it.key } }

    @Test fun firstRunSeedsDockFromPreferenceThenHome() {
        val r = reconcile(null, installed, known = emptySet(), hidden = emptySet(), dockPrefer = listOf("brave", "files", "tg", "termux"), skipFromHome = setOf("lawn"))
        assertEquals(listOf<String?>("brave/Main", "files/Main", "tg/Main", "termux/Main"), r.layout.dock)
        assertEquals(setOf("calc/Main", "clock/Main"), home(r.layout).toSet())
        assertFalse(home(r.layout).contains("lawn/Main")) // skipped packages live only in the drawer
        assertEquals(installed.map { it.key }.toSet(), r.known)
    }

    @Test fun dockPreferenceOnlyUsesInstalledApps() {
        val r = reconcile(null, installed, emptySet(), emptySet(), listOf("nope", "brave"), emptySet())
        assertEquals("brave/Main", r.layout.dock[0])
        assertEquals(null, r.layout.dock[1])
    }

    @Test fun newlyInstalledAppLandsOnFirstFreeCell() {
        val first = reconcile(null, installed, emptySet(), emptySet(), emptyList(), emptySet())
        val withNew = installed + app("newapp")
        val r = reconcile(first.layout, withNew, first.known, emptySet(), emptyList(), emptySet())
        assertTrue(home(r.layout).contains("newapp/Main"))
        assertTrue(r.known.contains("newapp/Main"))
    }

    @Test fun anAppTheUserRemovedFromHomeDoesNotComeBack() {
        val first = reconcile(null, installed, emptySet(), emptySet(), emptyList(), emptySet())
        val removed = LayoutOps.remove(first.layout, "calc/Main")
        val r = reconcile(removed, installed, first.known, emptySet(), emptyList(), emptySet())
        assertFalse(home(r.layout).contains("calc/Main")) // known, so not auto-placed again
    }

    @Test fun uninstalledAppsArePrunedEverywhere() {
        val first = reconcile(null, installed, emptySet(), emptySet(), listOf("brave"), emptySet())
        val r = reconcile(first.layout, installed.filter { it.key != "brave/Main" && it.key != "calc/Main" }, first.known, emptySet(), emptyList(), emptySet())
        assertFalse(LayoutOps.contains(r.layout, "brave/Main"))
        assertFalse(LayoutOps.contains(r.layout, "calc/Main"))
    }

    @Test fun hiddenAppsAreTakenOffHome() {
        val first = reconcile(null, installed, emptySet(), emptySet(), emptyList(), emptySet())
        val r = reconcile(first.layout, installed, first.known, setOf("clock/Main"), emptyList(), emptySet())
        assertFalse(LayoutOps.contains(r.layout, "clock/Main"))
    }

    @Test fun anEmptyAppListNeverWipesTheLayout() {
        val first = reconcile(null, installed, emptySet(), emptySet(), listOf("brave"), emptySet())
        val r = reconcile(first.layout, emptyList(), first.known, emptySet(), emptyList(), emptySet())
        assertEquals(first.layout, r.layout)
        assertEquals(first.known, r.known)
    }

    @Test fun cleanHomeKeepsEveryAppInTheDrawerOnFirstRun() {
        val r = reconcile(null, installed, emptySet(), emptySet(), listOf("brave"), emptySet(), autoPlace = false)
        assertTrue(home(r.layout).isEmpty())
        assertTrue(r.layout.dock.all { it == null })
        assertEquals(installed.map { it.key }.toSet(), r.known)
    }

    @Test fun cleanHomeDoesNotPlaceNewlyInstalledApps() {
        val first = reconcile(null, installed, emptySet(), emptySet(), emptyList(), emptySet(), autoPlace = false)
        val r = reconcile(first.layout, installed + app("newapp"), first.known, emptySet(), emptyList(), emptySet(), autoPlace = false)
        assertTrue(home(r.layout).isEmpty())
    }

    @Test fun cleanHomeStillKeepsAppsTheUserDraggedOnAndPrunesUninstalled() {
        val first = reconcile(null, installed, emptySet(), emptySet(), emptyList(), emptySet(), autoPlace = false)
        val withApps = LayoutOps.placeDock(LayoutOps.place(first.layout, "brave/Main", 0, 0, 0), "calc/Main", 0)
        val r = reconcile(withApps, installed.filter { it.key != "calc/Main" }, first.known, emptySet(), emptyList(), emptySet(), autoPlace = false)
        assertTrue(LayoutOps.contains(r.layout, "brave/Main"))
        assertFalse(LayoutOps.contains(r.layout, "calc/Main"))
    }

    @Test fun pageCountGrowsAndTrimsOnlyEmptyTrailingPages() {
        val l = LayoutOps.place(LayoutOps.empty(2, 2, 3), "a", 1, 0, 0)
        assertEquals(4, LayoutOps.withPageCount(l, 4).pages.size)
        assertEquals(2, LayoutOps.withPageCount(l, 1).pages.size) // page 2 holds an item, so it stays; empty page 3 goes
    }
}
