package dev.redmi9i.files.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Environment
import java.io.File
import java.time.LocalDate

/**
 * The crew member of the day, picked by the same rule as the Grand Line launcher so the two apps always agree.
 * Line-art watermarks come from the launcher's /sdcard/GrandLine/marks/ folder; a missing file just means no watermark.
 */
object Crew {
    private val members = listOf(
        "Luffy", "Zoro", "Nami", "Usopp", "Vinsmoke_Sanji",
        "Tony_Tony_Chopper", "Nico_Robin", "Franky", "Brook", "Jinbe",
    )

    fun ofDay(epochDay: Long): String = members[Math.floorMod(epochDay, members.size.toLong()).toInt()]

    fun todaysMark(): Bitmap? = runCatching {
        val f = File(Environment.getExternalStorageDirectory(), "GrandLine/marks/${ofDay(LocalDate.now().toEpochDay())}.png")
        if (f.isFile) BitmapFactory.decodeFile(f.path) else null
    }.getOrNull()
}
