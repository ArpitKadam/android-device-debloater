package dev.redmi9i.grandline.theme

import kotlin.math.roundToInt

/** Straw Hat crew colours. */
object Palette {
    const val SeaBlue = 0xFF0B3C6E.toInt()
    const val DeepSea = 0xFF06223F.toInt()
    const val StrawGold = 0xFFF2B632.toInt()
    const val VestRed = 0xFFD9232D.toInt()
    const val Parchment = 0xFFF6E9C8.toInt()
    const val Ink = 0xFF1B1B1F.toInt()
    const val Foam = 0xFFFFFFFF.toInt()

    /** Same colour with a new alpha, 0f transparent to 1f opaque. */
    fun withAlpha(color: Int, alpha: Float): Int =
        (color and 0x00FFFFFF) or ((alpha.coerceIn(0f, 1f) * 255).roundToInt() shl 24)
}
