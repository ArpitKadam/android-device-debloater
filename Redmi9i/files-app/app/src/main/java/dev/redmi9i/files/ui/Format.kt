package dev.redmi9i.files.ui

import dev.redmi9i.files.storage.FileInfo
import dev.redmi9i.files.storage.kindOf
import java.text.DateFormat
import java.util.Date

object Format {
    fun size(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val units = arrayOf("KB", "MB", "GB", "TB")
        var v = bytes.toDouble()
        var i = -1
        while (v >= 1024 && i < units.lastIndex) { v /= 1024; i++ }
        return if (v >= 100) "%.0f %s".format(v, units[i]) else "%.1f %s".format(v, units[i])
    }

    fun date(ms: Long): String = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(ms))
}

enum class SortMode(val label: String) { NAME("Name"), DATE("Date"), SIZE("Size"), TYPE("Type") }

/** Folders first, then the chosen key. */
fun sortItems(items: List<FileInfo>, mode: SortMode, descending: Boolean): List<FileInfo> {
    val key: Comparator<FileInfo> = when (mode) {
        SortMode.NAME -> compareBy(String.CASE_INSENSITIVE_ORDER) { it.name }
        SortMode.DATE -> compareBy { it.modified }
        SortMode.SIZE -> compareBy { it.size }
        SortMode.TYPE -> compareBy<FileInfo> { it.name.substringAfterLast('.', "").lowercase() }
            .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name }
    }
    val ordered = if (descending) key.reversed() else key
    return items.sortedWith(compareByDescending<FileInfo> { it.isDir }.then(ordered))
}

fun FileInfo.kind() = kindOf(name, isDir)
