package dev.redmi9i.grandline.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LayoutModelTest {
    private fun empty() = LayoutOps.empty(rows = 2, cols = 2, pages = 2)
    private fun keyAt(l: Layout, page: Int, r: Int, c: Int) = l.pages[page].cells.firstOrNull { it.r == r && it.c == c }?.key

    @Test fun placeIntoEmptyCell() {
        val l = LayoutOps.place(empty(), "a", 0, 0, 1)
        assertEquals("a", keyAt(l, 0, 0, 1))
    }

    @Test fun placeOntoOccupiedCellSwapsWithOrigin() {
        var l = LayoutOps.place(empty(), "a", 0, 0, 0)
        l = LayoutOps.place(l, "b", 0, 1, 1)
        l = LayoutOps.place(l, "a", 0, 1, 1) // drag a onto b
        assertEquals("a", keyAt(l, 0, 1, 1))
        assertEquals("b", keyAt(l, 0, 0, 0))
    }

    @Test fun movingRemovesTheOldPosition() {
        var l = LayoutOps.place(empty(), "a", 0, 0, 0)
        l = LayoutOps.place(l, "a", 1, 1, 1)
        assertNull(keyAt(l, 0, 0, 0))
        assertEquals("a", keyAt(l, 1, 1, 1))
        assertEquals(1, l.pages.sumOf { p -> p.cells.count { it.key == "a" } })
    }

    @Test fun newItemDroppedOnOccupiedCellDisplacesToFirstFree() {
        var l = LayoutOps.place(empty(), "b", 0, 0, 0)
        l = LayoutOps.place(l, "a", 0, 0, 0) // a has no origin (came from the drawer)
        assertEquals("a", keyAt(l, 0, 0, 0))
        assertEquals("b", keyAt(l, 0, 0, 1)) // b moved to the first free cell
    }

    @Test fun outOfRangePlacementIsRejected() {
        val l = empty()
        assertEquals(l, LayoutOps.place(l, "a", 5, 0, 0))
        assertEquals(l, LayoutOps.place(l, "a", 0, 9, 0))
        assertEquals(l, LayoutOps.place(l, "a", 0, 0, -1))
    }

    @Test fun dockPlaceAndSwap() {
        var l = LayoutOps.placeDock(empty(), "a", 0)
        l = LayoutOps.placeDock(l, "b", 1)
        l = LayoutOps.placeDock(l, "a", 1) // a onto b swaps
        assertEquals("a", l.dock[1])
        assertEquals("b", l.dock[0])
    }

    @Test fun movingFromHomeToDockRemovesHomeCopy() {
        var l = LayoutOps.place(empty(), "a", 0, 0, 0)
        l = LayoutOps.placeDock(l, "a", 2)
        assertNull(keyAt(l, 0, 0, 0))
        assertEquals("a", l.dock[2])
    }

    @Test fun removeTakesItOutEverywhere() {
        var l = LayoutOps.place(empty(), "a", 0, 0, 0)
        l = LayoutOps.placeDock(l, "b", 0)
        l = LayoutOps.remove(LayoutOps.remove(l, "a"), "b")
        assertFalse(LayoutOps.contains(l, "a"))
        assertFalse(LayoutOps.contains(l, "b"))
    }

    @Test fun firstFreeIsOrderedByPageRowColumn() {
        var l = empty()
        assertEquals(Triple(0, 0, 0), LayoutOps.firstFree(l))
        l = LayoutOps.place(l, "a", 0, 0, 0)
        assertEquals(Triple(0, 0, 1), LayoutOps.firstFree(l))
        listOf(1 to 0, 1 to 1).forEachIndexed { i, (r, c) -> l = LayoutOps.place(l, "x$i", 0, r, c) }
        l = LayoutOps.place(l, "y", 0, 0, 1)
        assertEquals(Triple(1, 0, 0), LayoutOps.firstFree(l))
    }

    @Test fun firstFreeIsNullWhenFull() {
        var l = LayoutOps.empty(rows = 1, cols = 1, pages = 1)
        l = LayoutOps.place(l, "a", 0, 0, 0)
        assertNull(LayoutOps.firstFree(l))
    }

    @Test fun seedFillsDockFirstThenPages() {
        val l = LayoutOps.seed(LayoutOps.empty(rows = 1, cols = 2, pages = 2), listOf("d1", "d2"), listOf("d1", "a", "b", "c", "d", "e"))
        assertEquals(listOf<String?>("d1", "d2", null, null), l.dock)
        assertEquals("a", keyAt(l, 0, 0, 0))
        assertEquals("b", keyAt(l, 0, 0, 1))
        assertEquals("c", keyAt(l, 1, 0, 0))
        assertEquals("d", keyAt(l, 1, 0, 1))
        assertFalse(LayoutOps.contains(l, "e")) // overflow stays unplaced (drawer only)
        assertEquals(1, (l.pages.flatMap { it.cells.map { c -> c.key } } + l.dock.filterNotNull()).count { it == "d1" })
    }

    @Test fun pruneDropsUninstalledKeys() {
        var l = LayoutOps.place(empty(), "a", 0, 0, 0)
        l = LayoutOps.place(l, "b", 0, 0, 1)
        l = LayoutOps.placeDock(l, "c", 0)
        val pruned = LayoutOps.prune(l, setOf("a"))
        assertTrue(LayoutOps.contains(pruned, "a"))
        assertFalse(LayoutOps.contains(pruned, "b"))
        assertFalse(LayoutOps.contains(pruned, "c"))
    }

    @Test fun jsonRoundTrip() {
        var l = LayoutOps.place(empty(), "pkg/Cls", 1, 1, 0)
        l = LayoutOps.placeDock(l, "d/D", 3)
        assertEquals(l, LayoutOps.fromJson(LayoutOps.toJson(l)))
    }

    @Test fun corruptJsonReturnsNull() {
        assertNull(LayoutOps.fromJson("not json"))
        assertNull(LayoutOps.fromJson("""{"rows":0,"cols":4,"pages":[],"dock":[]}"""))
        assertNull(LayoutOps.fromJson("""{"rows":2,"cols":2,"pages":[[{"r":9,"c":0,"k":"a"}]],"dock":[null,null,null,null]}"""))
    }

    @Test fun duplicateKeysInJsonAreRejected() {
        val dup = """{"rows":2,"cols":2,"pages":[[{"r":0,"c":0,"k":"a"},{"r":0,"c":1,"k":"a"}]],"dock":[null,null,null,null]}"""
        assertNull(LayoutOps.fromJson(dup))
    }

    @Test fun validJsonLoads() {
        val ok = """{"rows":2,"cols":2,"pages":[[{"r":0,"c":0,"k":"a"}],[]],"dock":["b",null,null,null]}"""
        assertNotNull(LayoutOps.fromJson(ok))
    }
}
