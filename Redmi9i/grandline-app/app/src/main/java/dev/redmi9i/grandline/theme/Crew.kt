package dev.redmi9i.grandline.theme

/** The ten crew posters in /sdcard/GrandLine/posters/ (file stem = name) and the rules for picking one. */
object Crew {
    val members = listOf(
        "Luffy", "Zoro", "Nami", "Usopp", "Vinsmoke_Sanji",
        "Tony_Tony_Chopper", "Nico_Robin", "Franky", "Brook", "Jinbe",
    )

    /** The same crew member all day, so the drawer panel and every other spot agree. */
    fun ofDay(epochDay: Long): String = members[Math.floorMod(epochDay, members.size.toLong()).toInt()]

    /** A random member that is never [last], for the lock screen. [roll] is any random int. */
    fun nextFor(last: String?, roll: Int): String {
        val pool = members.filter { it != last }
        return pool[Math.floorMod(roll, pool.size)]
    }

    fun label(stem: String): String = stem.replace('_', ' ')
}
