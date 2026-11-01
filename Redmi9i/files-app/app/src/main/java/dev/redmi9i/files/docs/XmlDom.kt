package dev.redmi9i.files.docs

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream

/** Tiny DOM. Names and attribute names have their namespace prefix stripped ("w:val" becomes "val"). */
class XNode(val name: String, val attrs: Map<String, String>) {
    val children = ArrayList<XNode>()
    val text = StringBuilder()

    fun attr(key: String): String? = attrs[key]
    fun child(name: String): XNode? = children.firstOrNull { it.name == name }
    fun childrenNamed(name: String): List<XNode> = children.filter { it.name == name }

    fun descendant(name: String): XNode? {
        for (c in children) {
            if (c.name == name) return c
            c.descendant(name)?.let { return it }
        }
        return null
    }
}

object XmlDom {
    fun strip(name: String) = name.substringAfter(':')

    fun parse(input: InputStream): XNode {
        val p = XmlPullParserFactory.newInstance().newPullParser()
        p.setInput(input, null)
        val stack = ArrayList<XNode>()
        var root: XNode? = null
        var ev = p.eventType
        while (ev != XmlPullParser.END_DOCUMENT) {
            when (ev) {
                XmlPullParser.START_TAG -> {
                    val attrs = HashMap<String, String>(p.attributeCount)
                    for (i in 0 until p.attributeCount) attrs[strip(p.getAttributeName(i))] = p.getAttributeValue(i)
                    val n = XNode(strip(p.name), attrs)
                    stack.lastOrNull()?.children?.add(n)
                    if (root == null) root = n
                    stack.add(n)
                }
                XmlPullParser.TEXT, XmlPullParser.IGNORABLE_WHITESPACE -> stack.lastOrNull()?.text?.append(p.text)
                XmlPullParser.END_TAG -> if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
            }
            ev = p.nextToken().let { if (it == XmlPullParser.ENTITY_REF || it == XmlPullParser.CDSECT) XmlPullParser.TEXT else it }
        }
        return root ?: throw IllegalArgumentException("Empty XML")
    }
}

fun escapeHtml(s: String): String {
    val sb = StringBuilder(s.length + 8)
    for (c in s) when (c) {
        '&' -> sb.append("&amp;")
        '<' -> sb.append("&lt;")
        '>' -> sb.append("&gt;")
        '"' -> sb.append("&quot;")
        else -> sb.append(c)
    }
    return sb.toString()
}
