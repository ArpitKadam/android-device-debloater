package dev.redmi9i.files.storage

/** A location on some [FsProvider]. [path] is always an absolute, '/'-separated path. */
data class FileRef(val provider: String, val path: String) {
    val name: String get() = path.trimEnd('/').substringAfterLast('/')

    val parent: FileRef?
        get() {
            val t = path.trimEnd('/')
            val i = t.lastIndexOf('/')
            return if (i <= 0) null else FileRef(provider, t.substring(0, i))
        }
}

data class FileInfo(
    val ref: FileRef,
    val name: String,
    val isDir: Boolean,
    val size: Long,
    val modified: Long,
    val hidden: Boolean,
)

enum class Kind { DIR, IMAGE, VIDEO, AUDIO, PDF, TEXT, DOCX, XLSX, ZIP, OTHER }

private val IMAGE = setOf("jpg", "jpeg", "png", "gif", "webp", "bmp", "heic")
private val VIDEO = setOf("mp4", "mkv", "webm", "3gp", "avi", "mov", "m4v", "ts")
private val AUDIO = setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "amr")
private val TEXT = setOf(
    "txt", "md", "log", "json", "xml", "csv", "html", "htm", "css", "js", "kt", "java", "py",
    "sh", "yml", "yaml", "ini", "conf", "properties", "gradle", "sql",
)

fun kindOf(name: String, isDir: Boolean = false): Kind {
    if (isDir) return Kind.DIR
    val ext = name.substringAfterLast('.', "").lowercase()
    return when {
        ext in IMAGE -> Kind.IMAGE
        ext in VIDEO -> Kind.VIDEO
        ext in AUDIO -> Kind.AUDIO
        ext == "pdf" -> Kind.PDF
        ext in TEXT -> Kind.TEXT
        ext == "docx" -> Kind.DOCX
        ext == "xlsx" -> Kind.XLSX
        ext == "zip" -> Kind.ZIP
        else -> Kind.OTHER
    }
}
