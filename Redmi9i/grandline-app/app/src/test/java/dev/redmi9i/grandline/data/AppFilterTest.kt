package dev.redmi9i.grandline.data

import android.content.ComponentName
import org.junit.Assert.assertEquals
import org.junit.Test

class AppFilterTest {
    private fun app(label: String, key: String = "p.$label/c") = AppEntry(key, label, ComponentName("p", "c"), isUser = true)

    private val apps = listOf(app("termux"), app("Brave"), app("Files"), app("Settings"), app("Telegram"))

    @Test fun sortIgnoresCase() {
        assertEquals(listOf("Brave", "Files", "Settings", "Telegram", "termux"), sortApps(apps).map { it.label })
    }

    @Test fun sortBreaksTiesByKey() {
        val a = app("Same", "z/1"); val b = app("same", "a/2")
        assertEquals(listOf("a/2", "z/1"), sortApps(listOf(a, b)).map { it.key })
    }

    @Test fun filterMatchesSubstringCaseInsensitively() {
        assertEquals(listOf("Settings", "Telegram"), filterApps(sortApps(apps), "T", emptySet()).map { it.label }.filter { it in setOf("Settings", "Telegram") })
        assertEquals(listOf("Brave"), filterApps(apps, "rav", emptySet()).map { it.label })
        assertEquals(listOf("termux"), filterApps(apps, "TERM", emptySet()).map { it.label })
    }

    @Test fun hiddenAppsAreRemovedEvenWhenTheyMatch() {
        assertEquals(emptyList<String>(), filterApps(apps, "brave", setOf("p.Brave/c")).map { it.label })
        assertEquals(4, filterApps(apps, "", setOf("p.Brave/c")).size)
    }

    @Test fun emptyQueryReturnsEverythingVisible() {
        assertEquals(5, filterApps(apps, "  ", emptySet()).size)
    }
}
