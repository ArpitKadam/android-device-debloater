package dev.redmi9i.grandline.theme

import org.junit.Assert.assertEquals
import org.junit.Test

class PaletteTest {
    @Test fun constantsMatchTheDesign() {
        assertEquals(0xFFD9232D.toInt(), Palette.VestRed)
        assertEquals(0xFFF2B632.toInt(), Palette.StrawGold)
        assertEquals(0xFF0B3C6E.toInt(), Palette.SeaBlue)
        assertEquals(0xFF06223F.toInt(), Palette.DeepSea)
    }

    @Test fun withAlphaKeepsRgbAndSetsAlpha() {
        val c = Palette.withAlpha(Palette.StrawGold, 0.5f)
        assertEquals(0xF2B632, c and 0xFFFFFF)
        assertEquals(128, (c ushr 24) and 0xFF)
    }

    @Test fun withAlphaClampsOutOfRange() {
        assertEquals(0, Palette.withAlpha(Palette.Foam, -1f) ushr 24)
        assertEquals(255, Palette.withAlpha(Palette.Foam, 2f) ushr 24)
    }
}
