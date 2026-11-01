package dev.redmi9i.files.docs

import java.io.File
import java.util.zip.ZipFile

/**
 * Renders the body of a .docx as simple HTML: headings, paragraphs, bold/italic/underline/strike,
 * bullet and numbered lists, tables and embedded images. Headers, footers, comments, tracked changes
 * and floating shapes are ignored. All text is HTML-escaped.
 */
object DocxRenderer {
    private const val MAX_XML = 20L * 1024 * 1024
    private const val MAX_IMAGE = 1_500_000L
    private const val MAX_IMAGES_TOTAL = 8_000_000L

    fun toHtml(file: File): String = ZipFile(file).use { zip ->
        val docEntry = zip.getEntry("word/document.xml") ?: throw IllegalArgumentException("Not a Word document")
        if (docEntry.size > MAX_XML) throw IllegalArgumentException("Document is too large to preview")
        val doc = zip.getInputStream(docEntry).use { XmlDom.parse(it) }
        val rels = zip.getEntry("word/_rels/document.xml.rels")?.let { e -> zip.getInputStream(e).use { parseRels(XmlDom.parse(it)) } }.orEmpty()
        val numbering = zip.getEntry("word/numbering.xml")?.let { e -> zip.getInputStream(e).use { parseNumbering(XmlDom.parse(it)) } }.orEmpty()
        Renderer(zip, rels, numbering).render(doc)
    }

    private fun parseRels(root: XNode): Map<String, String> =
        root.childrenNamed("Relationship").mapNotNull { r -> r.attr("Id")?.let { it to (r.attr("Target") ?: "") } }.toMap()

    /** numId to "is this list ordered", read from the first level of its abstract definition. */
    private fun parseNumbering(root: XNode): Map<String, Boolean> {
        val abstractOrdered = HashMap<String, Boolean>()
        for (a in root.childrenNamed("abstractNum")) {
            val id = a.attr("abstractNumId") ?: continue
            val fmt = a.childrenNamed("lvl").firstOrNull()?.child("numFmt")?.attr("val")
            abstractOrdered[id] = fmt != null && fmt != "bullet" && fmt != "none"
        }
        val out = HashMap<String, Boolean>()
        for (n in root.childrenNamed("num")) {
            val numId = n.attr("numId") ?: continue
            val abs = n.child("abstractNumId")?.attr("val") ?: continue
            out[numId] = abstractOrdered[abs] ?: false
        }
        return out
    }

    private class Renderer(val zip: ZipFile, val rels: Map<String, String>, val numbering: Map<String, Boolean>) {
        val sb = StringBuilder()
        var imageBytes = 0L
        var openList: String? = null // "ul" or "ol"

        fun render(doc: XNode): String {
            sb.append("<!doctype html><html><head><meta charset=\"utf-8\">")
            sb.append("<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">")
            sb.append("<style>").append(CSS).append("</style></head><body>")
            doc.child("body")?.let { blocks(it) }
            closeList()
            sb.append("</body></html>")
            return sb.toString()
        }

        fun blocks(parent: XNode) {
            for (c in parent.children) when (c.name) {
                "p" -> paragraph(c)
                "tbl" -> { closeList(); table(c) }
                "sdt" -> c.child("sdtContent")?.let { blocks(it) }
            }
        }

        fun closeList() { openList?.let { sb.append("</").append(it).append('>') }; openList = null }

        fun paragraph(p: XNode) {
            val pPr = p.child("pPr")
            val style = pPr?.child("pStyle")?.attr("val").orEmpty()
            val numPr = pPr?.child("numPr")
            val inner = StringBuilder()
            inlineInto(p, inner)

            if (numPr?.child("numId")?.attr("val").let { it != null && it != "0" }) {
                val ordered = numbering[numPr!!.child("numId")!!.attr("val")!!] ?: false
                val tag = if (ordered) "ol" else "ul"
                if (openList != tag) { closeList(); sb.append('<').append(tag).append('>'); openList = tag }
                val level = numPr.child("ilvl")?.attr("val")?.toIntOrNull() ?: 0
                sb.append("<li style=\"margin-left:").append(level * 1.5).append("em\">").append(inner).append("</li>")
                return
            }
            closeList()
            val heading = when {
                style.equals("Title", true) -> 1
                style.startsWith("Heading", true) -> style.drop(7).toIntOrNull()?.coerceIn(1, 6)
                else -> null
            }
            when {
                heading != null -> sb.append("<h$heading>").append(inner).append("</h$heading>")
                inner.isEmpty() -> sb.append("<p>&nbsp;</p>")
                else -> sb.append("<p>").append(inner).append("</p>")
            }
        }

        /** Walks runs inside a paragraph, hyperlink or similar wrapper. */
        fun inlineInto(node: XNode, out: StringBuilder) {
            for (c in node.children) when (c.name) {
                "r" -> run(c, out)
                "hyperlink", "ins", "smartTag", "sdt", "sdtContent", "fldSimple" -> inlineInto(c, out)
            }
        }

        fun flag(rPr: XNode?, name: String): Boolean {
            val n = rPr?.child(name) ?: return false
            val v = n.attr("val")
            return v == null || (v != "0" && v != "false")
        }

        fun run(r: XNode, out: StringBuilder) {
            val rPr = r.child("rPr")
            val open = StringBuilder(); val close = StringBuilder()
            fun wrap(tag: String) { open.append('<').append(tag).append('>'); close.insert(0, "</$tag>") }
            if (flag(rPr, "b")) wrap("strong")
            if (flag(rPr, "i")) wrap("em")
            if (rPr?.child("u")?.attr("val").let { it != null && it != "none" }) wrap("u")
            if (flag(rPr, "strike")) wrap("s")

            val text = StringBuilder()
            for (c in r.children) when (c.name) {
                "t" -> text.append(escapeHtml(c.text.toString()))
                "tab" -> text.append("&emsp;")
                "br", "cr" -> text.append("<br>")
                "drawing", "pict" -> image(c)?.let { text.append(it) }
            }
            if (text.isNotEmpty()) out.append(open).append(text).append(close)
        }

        fun image(node: XNode): String? {
            val rid = node.descendant("blip")?.attr("embed") ?: node.descendant("imagedata")?.attr("id") ?: return null
            val target = rels[rid] ?: return null
            val entry = zip.getEntry("word/" + target.trimStart('/').removePrefix("word/")) ?: return "<em>[image missing]</em>"
            if (entry.size > MAX_IMAGE || imageBytes + entry.size > MAX_IMAGES_TOTAL) return "<em>[image too large to show]</em>"
            imageBytes += entry.size
            val bytes = zip.getInputStream(entry).use { it.readBytes() }
            val mime = when (target.substringAfterLast('.').lowercase()) {
                "png" -> "image/png"; "jpg", "jpeg" -> "image/jpeg"; "gif" -> "image/gif"; "bmp" -> "image/bmp"; "webp" -> "image/webp"
                else -> return "<em>[unsupported image]</em>"
            }
            return "<img src=\"data:$mime;base64," + java.util.Base64.getEncoder().encodeToString(bytes) + "\">"
        }

        fun table(t: XNode) {
            sb.append("<table>")
            for (row in t.childrenNamed("tr")) {
                sb.append("<tr>")
                for (cell in row.childrenNamed("tc")) {
                    sb.append("<td>")
                    val saved = openList
                    openList = null
                    val cellStart = sb.length
                    blocks(cell)
                    closeList()
                    openList = saved
                    if (sb.length == cellStart) sb.append("&nbsp;")
                    sb.append("</td>")
                }
                sb.append("</tr>")
            }
            sb.append("</table>")
        }
    }

    private const val CSS = """
        body{font-family:sans-serif;line-height:1.5;margin:16px;max-width:46em;color:#1b1b1b;background:#fff}
        @media (prefers-color-scheme: dark){body{color:#e6e6e6;background:#202020}td{border-color:#555}}
        img{max-width:100%;height:auto}
        table{border-collapse:collapse;margin:12px 0;max-width:100%}
        td{border:1px solid #bbb;padding:4px 8px;vertical-align:top}
        h1,h2,h3{line-height:1.25}
    """
}
