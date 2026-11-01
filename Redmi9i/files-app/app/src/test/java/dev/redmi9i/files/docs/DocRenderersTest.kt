package dev.redmi9i.files.docs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class DocRenderersTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun zip(name: String, entries: Map<String, String>): File {
        val f = tmp.newFile(name)
        ZipOutputStream(f.outputStream()).use { z ->
            for ((n, c) in entries) { z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() }
        }
        return f
    }

    private val w = "xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\""

    private fun docx(body: String, numbering: String? = null): File {
        val parts = mutableMapOf("word/document.xml" to "<w:document $w><w:body>$body</w:body></w:document>")
        if (numbering != null) parts["word/numbering.xml"] = "<w:numbering $w>$numbering</w:numbering>"
        return zip("t.docx", parts)
    }

    @Test fun docxHeadingAndFormatting() {
        val html = DocxRenderer.toHtml(docx(
            """<w:p><w:pPr><w:pStyle w:val="Heading1"/></w:pPr><w:r><w:t>Report</w:t></w:r></w:p>
               <w:p><w:r><w:rPr><w:b/></w:rPr><w:t>bold</w:t></w:r><w:r><w:rPr><w:i/></w:rPr><w:t xml:space="preserve"> it</w:t></w:r></w:p>""",
        ))
        assertTrue(html, html.contains("<h1>Report</h1>"))
        assertTrue(html.contains("<strong>bold</strong>"))
        assertTrue(html.contains("<em> it</em>"))
    }

    @Test fun docxEscapesMarkup() {
        val html = DocxRenderer.toHtml(docx("""<w:p><w:r><w:t>&lt;script&gt;alert(1)&lt;/script&gt;</w:t></w:r></w:p>"""))
        assertTrue(html.contains("&lt;script&gt;alert(1)&lt;/script&gt;"))
        assertFalse(html.contains("<script>"))
    }

    @Test fun docxBoldFalseIsNotBold() {
        val html = DocxRenderer.toHtml(docx("""<w:p><w:r><w:rPr><w:b w:val="0"/></w:rPr><w:t>plain</w:t></w:r></w:p>"""))
        assertFalse(html.contains("<strong>"))
    }

    @Test fun docxBulletAndNumberedLists() {
        val numbering = """
            <w:abstractNum w:abstractNumId="0"><w:lvl w:ilvl="0"><w:numFmt w:val="bullet"/></w:lvl></w:abstractNum>
            <w:abstractNum w:abstractNumId="1"><w:lvl w:ilvl="0"><w:numFmt w:val="decimal"/></w:lvl></w:abstractNum>
            <w:num w:numId="1"><w:abstractNumId w:val="0"/></w:num>
            <w:num w:numId="2"><w:abstractNumId w:val="1"/></w:num>"""
        fun item(id: Int, t: String) =
            """<w:p><w:pPr><w:numPr><w:ilvl w:val="0"/><w:numId w:val="$id"/></w:numPr></w:pPr><w:r><w:t>$t</w:t></w:r></w:p>"""
        val html = DocxRenderer.toHtml(docx(item(1, "a") + item(1, "b") + item(2, "one"), numbering))
        assertTrue(html, Regex("<ul><li[^>]*>a</li><li[^>]*>b</li></ul><ol><li[^>]*>one</li></ol>").containsMatchIn(html))
    }

    @Test fun docxTable() {
        val html = DocxRenderer.toHtml(docx(
            """<w:tbl><w:tr><w:tc><w:p><w:r><w:t>A</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>B</w:t></w:r></w:p></w:tc></w:tr></w:tbl>""",
        ))
        assertTrue(html, html.contains("<table><tr><td><p>A</p></td><td><p>B</p></td></tr></table>"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun docxRejectsNonWordZip() { DocxRenderer.toHtml(zip("x.docx", mapOf("hello.txt" to "hi"))) }

    // ---- xlsx

    private val s = "xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\""

    private fun xlsx(sheet1: String, extra: Map<String, String> = emptyMap(), sheets: String = """<sheet name="Data" sheetId="1" r:id="rId1"/>"""): File =
        zip(
            "t.xlsx",
            mapOf(
                "xl/workbook.xml" to "<workbook $s><sheets>$sheets</sheets></workbook>",
                "xl/_rels/workbook.xml.rels" to """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                    <Relationship Id="rId1" Target="worksheets/sheet1.xml"/><Relationship Id="rId2" Target="worksheets/sheet2.xml"/></Relationships>""",
                "xl/sharedStrings.xml" to "<sst $s><si><t>Name</t></si><si><t>a &lt;b&gt; &amp; c</t></si></sst>",
                "xl/worksheets/sheet1.xml" to "<worksheet $s><sheetData>$sheet1</sheetData></worksheet>",
            ) + extra,
        )

    @Test fun xlsxSharedStringsNumbersAndBooleans() {
        val html = XlsxRenderer.toHtml(xlsx(
            """<row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c></row>
               <row r="2"><c r="A2"><v>120</v></c><c r="B2"><v>95.50</v></c><c r="C2" t="b"><v>1</v></c></row>""",
        ))
        assertTrue(html.contains("<h2>Data</h2>"))
        assertTrue(html.contains("<td>Name</td><td>a &lt;b&gt; &amp; c</td>"))
        assertTrue(html, html.contains("<td>120</td><td>95.5</td><td>TRUE</td>"))
    }

    @Test fun xlsxDateStyleShowsDate() {
        val styles = """<styleSheet $s><cellXfs count="2"><xf numFmtId="0"/><xf numFmtId="14"/></cellXfs></styleSheet>"""
        val html = XlsxRenderer.toHtml(xlsx("""<row r="1"><c r="A1" s="1"><v>45000</v></c></row>""", mapOf("xl/styles.xml" to styles)))
        assertTrue(html, html.contains("<td>2023-03-15</td>"))
    }

    @Test fun xlsxCapsRowsAndShowsNotice() {
        val rows = (1..5).joinToString("") { """<row r="$it"><c r="A$it"><v>$it</v></c></row>""" }
        val html = XlsxRenderer.toHtml(xlsx(rows), maxRows = 3)
        assertTrue(html.contains("<td>3</td>"))
        assertFalse(html.contains("<td>4</td>"))
        assertTrue(html.contains("Preview truncated"))
    }

    @Test fun xlsxTwoSheetsInOrder() {
        val html = XlsxRenderer.toHtml(
            xlsx(
                """<row r="1"><c r="A1"><v>1</v></c></row>""",
                mapOf("xl/worksheets/sheet2.xml" to "<worksheet $s><sheetData><row r=\"1\"><c r=\"A1\"><v>2</v></c></row></sheetData></worksheet>"),
                sheets = """<sheet name="First" sheetId="1" r:id="rId1"/><sheet name="Second" sheetId="2" r:id="rId2"/>""",
            ),
        )
        assertTrue(html.indexOf("<h2>First</h2>") in 0 until html.indexOf("<h2>Second</h2>"))
    }

    @Test fun columnNamingRoundTrips() {
        assertEquals(1, XlsxRenderer.columnIndex("A1"))
        assertEquals(27, XlsxRenderer.columnIndex("AA10"))
        assertEquals("AA", XlsxRenderer.columnName(27))
        assertEquals("Z", XlsxRenderer.columnName(26))
    }
}
