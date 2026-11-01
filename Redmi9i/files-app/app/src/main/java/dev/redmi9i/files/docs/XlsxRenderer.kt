package dev.redmi9i.files.docs

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.File
import java.math.BigDecimal
import java.time.LocalDate
import java.util.zip.ZipFile

/**
 * Renders each worksheet of an .xlsx as an HTML table. Formulas are not evaluated; the cached
 * value stored in the file is shown. Rows and columns are capped so a huge sheet cannot exhaust memory.
 */
object XlsxRenderer {
    fun toHtml(file: File, maxRows: Int = 2000, maxCols: Int = 100): String = ZipFile(file).use { zip ->
        val workbook = zip.getEntry("xl/workbook.xml")?.let { e -> zip.getInputStream(e).use { XmlDom.parse(it) } }
            ?: throw IllegalArgumentException("Not an Excel workbook")
        val rels = zip.getEntry("xl/_rels/workbook.xml.rels")?.let { e ->
            zip.getInputStream(e).use { XmlDom.parse(it) }.childrenNamed("Relationship")
                .mapNotNull { r -> r.attr("Id")?.let { id -> id to (r.attr("Target") ?: "") } }.toMap()
        }.orEmpty()
        val shared = zip.getEntry("xl/sharedStrings.xml")?.let { e -> zip.getInputStream(e).use { readShared(XmlDom.parse(it)) } }.orEmpty()
        val dateStyles = zip.getEntry("xl/styles.xml")?.let { e -> zip.getInputStream(e).use { readDateStyles(XmlDom.parse(it)) } }.orEmpty()

        val sb = StringBuilder()
        sb.append("<!doctype html><html><head><meta charset=\"utf-8\">")
        sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
        sb.append("<style>").append(CSS).append("</style></head><body>")
        val sheets = workbook.child("sheets")?.childrenNamed("sheet").orEmpty()
        if (sheets.isEmpty()) sb.append("<p>This workbook has no sheets.</p>")
        for ((i, s) in sheets.withIndex()) {
            val name = s.attr("name") ?: "Sheet ${i + 1}"
            sb.append("<h2>").append(escapeHtml(name)).append("</h2>")
            val target = rels[s.attr("id") ?: ""]
            val path = target?.let { if (it.startsWith("/")) it.trimStart('/') else "xl/" + it }
            val entry = path?.let { zip.getEntry(it) }
            if (entry == null) { sb.append("<p class=\"note\">Sheet data not found.</p>"); continue }
            zip.getInputStream(entry).use { renderSheet(it, shared, dateStyles, maxRows, maxCols, sb) }
        }
        sb.append("</body></html>")
        sb.toString()
    }

    private fun readShared(root: XNode): List<String> = root.childrenNamed("si").map { si ->
        val sb = StringBuilder()
        fun walk(n: XNode) { if (n.name == "t") sb.append(n.text) else n.children.filter { it.name != "rPh" }.forEach(::walk) }
        walk(si)
        sb.toString()
    }

    private val BUILTIN_DATE_IDS = setOf(14, 15, 16, 17, 18, 19, 20, 21, 22, 45, 46, 47)

    /** Style indexes (cellXfs positions) whose number format shows a date or time. */
    private fun readDateStyles(root: XNode): Set<Int> {
        val custom = HashMap<Int, String>()
        root.child("numFmts")?.childrenNamed("numFmt")?.forEach { n ->
            n.attr("numFmtId")?.toIntOrNull()?.let { custom[it] = n.attr("formatCode").orEmpty() }
        }
        val out = HashSet<Int>()
        root.child("cellXfs")?.childrenNamed("xf")?.forEachIndexed { i, xf ->
            val id = xf.attr("numFmtId")?.toIntOrNull() ?: return@forEachIndexed
            if (id in BUILTIN_DATE_IDS || custom[id]?.let(::looksLikeDateFormat) == true) out += i
        }
        return out
    }

    private fun looksLikeDateFormat(code: String): Boolean {
        val stripped = code.replace(Regex("\"[^\"]*\"|\\[[^\\]]*]|\\\\."), "")
        return stripped.any { it.lowercaseChar() in "dmyhs" }
    }

    private fun renderSheet(
        input: java.io.InputStream, shared: List<String>, dateStyles: Set<Int>, maxRows: Int, maxCols: Int, sb: StringBuilder,
    ) {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        val rows = ArrayList<Pair<Int, Map<Int, String>>>()
        var truncatedRows = false
        var truncatedCols = false
        var maxCol = 0

        var rowNum = 0
        var cells = HashMap<Int, String>()
        var colIdx = 0; var type = ""; var style = -1
        var inV = false; var inT = false; var inIs = false
        var v = StringBuilder(); var isText = StringBuilder()

        var ev = p.eventType
        loop@ while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> when (XmlDom.strip(p.name)) {
                    "row" -> {
                        if (rows.size >= maxRows) { truncatedRows = true; break@loop }
                        rowNum = p.getAttributeValue(null, "r")?.toIntOrNull() ?: (rowNum + 1)
                        cells = HashMap()
                    }
                    "c" -> {
                        colIdx = columnIndex(p.getAttributeValue(null, "r") ?: "")
                        type = p.getAttributeValue(null, "t").orEmpty()
                        style = p.getAttributeValue(null, "s")?.toIntOrNull() ?: -1
                        v = StringBuilder(); isText = StringBuilder()
                    }
                    "v" -> inV = true
                    "is" -> inIs = true
                    "t" -> if (inIs) inT = true
                }
                XmlPullParser.TEXT -> { if (inV) v.append(p.text) else if (inT) isText.append(p.text) }
                XmlPullParser.END_TAG -> when (XmlDom.strip(p.name)) {
                    "v" -> inV = false
                    "t" -> inT = false
                    "is" -> inIs = false
                    "c" -> {
                        if (colIdx in 1..maxCols) {
                            val text = cellText(type, v.toString(), isText.toString(), style, shared, dateStyles)
                            if (text.isNotEmpty()) { cells[colIdx] = text; if (colIdx > maxCol) maxCol = colIdx }
                        } else if (colIdx > maxCols) truncatedCols = true
                    }
                    "row" -> rows += rowNum to cells
                }
            }
            ev = p.next()
        }

        if (rows.isEmpty()) { sb.append("<p class=\"note\">This sheet is empty.</p>"); return }
        sb.append("<div class=\"wrap\"><table><tr><th></th>")
        for (c in 1..maxCol) sb.append("<th>").append(columnName(c)).append("</th>")
        sb.append("</tr>")
        for ((num, row) in rows) {
            sb.append("<tr><th>").append(num).append("</th>")
            for (c in 1..maxCol) sb.append("<td>").append(escapeHtml(row[c].orEmpty())).append("</td>")
            sb.append("</tr>")
        }
        sb.append("</table></div>")
        if (truncatedRows || truncatedCols) {
            sb.append("<p class=\"note\">Preview truncated: showing at most ").append(maxRows).append(" rows and ")
                .append(maxCols).append(" columns.</p>")
        }
    }

    private fun cellText(type: String, v: String, inline: String, style: Int, shared: List<String>, dateStyles: Set<Int>): String = when (type) {
        "s" -> v.trim().toIntOrNull()?.let { shared.getOrNull(it) }.orEmpty()
        "inlineStr" -> inline
        "b" -> if (v.trim() == "1") "TRUE" else "FALSE"
        "str", "e" -> v
        else -> {
            val d = v.trim().toDoubleOrNull()
            when {
                v.isBlank() -> ""
                d == null -> v
                style in dateStyles -> excelDate(d)
                else -> plainNumber(v.trim())
            }
        }
    }

    private fun plainNumber(raw: String): String = try {
        BigDecimal(raw).stripTrailingZeros().toPlainString()
    } catch (e: NumberFormatException) { raw }

    private fun excelDate(serial: Double): String {
        val day = LocalDate.of(1899, 12, 30).plusDays(Math.floor(serial).toLong())
        val secs = Math.round((serial - Math.floor(serial)) * 86400).toInt()
        return if (secs == 0) day.toString() else "%s %02d:%02d:%02d".format(day, secs / 3600, secs / 60 % 60, secs % 60)
    }

    /** "B3" gives 2, "AA1" gives 27; 0 when the reference is malformed. */
    internal fun columnIndex(ref: String): Int {
        var n = 0
        for (ch in ref) {
            if (!ch.isLetter()) break
            n = n * 26 + (ch.uppercaseChar() - 'A' + 1)
        }
        return n
    }

    internal fun columnName(index: Int): String {
        var n = index
        val sb = StringBuilder()
        while (n > 0) { n--; sb.insert(0, ('A' + n % 26)); n /= 26 }
        return sb.toString()
    }

    private const val CSS = """
        body{font-family:sans-serif;margin:12px;color:#1b1b1b;background:#fff}
        @media (prefers-color-scheme: dark){body{color:#e6e6e6;background:#202020}th{background:#333}td,th{border-color:#555}}
        .wrap{overflow-x:auto}
        table{border-collapse:collapse;font-size:14px}
        td,th{border:1px solid #bbb;padding:3px 8px;white-space:nowrap}
        th{background:#eee;font-weight:normal;text-align:center}
        .note{opacity:.7;font-size:13px}
    """
}
