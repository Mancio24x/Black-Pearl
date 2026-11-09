package org.qosp.notes.components.importer

import org.jsoup.nodes.Element
import org.jsoup.nodes.Node
import org.jsoup.nodes.TextNode

/**
 * HTML to Markdown, for notes exported as HTML (Notesnook, Joplin, Standard Notes rich text, Keep .html…) and
 * Evernote ENML. Covers what notes use: headings, paragraphs and line breaks, bold / italic / strikethrough,
 * links, bullet / numbered / check lists (also Evernote's <en-todo>), quotes, code, tables, rules and images.
 * [image] turns an <img> or <en-media> into a Markdown image (registering the attachment) or null to drop it.
 */
class HtmlMarkdown(private val image: (Element) -> String? = { null }) {

    fun convert(root: Element): String = finish(render(root, 0, pre = false))

    private fun render(node: Node, depth: Int, pre: Boolean): String = when (node) {
        is TextNode -> if (pre) node.wholeText else escape(collapse(node.wholeText))
        is Element -> element(node, depth, pre)
        else -> ""
    }

    private fun children(el: Element, depth: Int, pre: Boolean = false) =
        el.childNodes().joinToString("") { render(it, depth, pre) }

    private fun element(el: Element, depth: Int, pre: Boolean): String {
        val tag = el.tagName().lowercase()
        return when (tag) {
            "script", "style", "head", "title", "meta", "link", "noscript", "template", "button", "select",
            "iframe", "object", "input" -> ""
            "br" -> "\n"
            "hr" -> "${PARA}---$PARA"
            "p" -> PARA + children(el, depth) + PARA
            "h1", "h2", "h3", "h4", "h5", "h6" -> {
                val text = oneLine(children(el, depth))
                if (text.isEmpty()) "" else PARA + "#".repeat(tag[1].digitToInt()) + " " + text + PARA
            }
            "strong", "b" -> wrap(children(el, depth), "**")
            "em", "i", "cite" -> wrap(children(el, depth), "*")
            "s", "strike", "del" -> wrap(children(el, depth), "~~")
            "code", "kbd", "samp" -> if (pre) el.wholeText() else wrap(el.wholeText().replace("`", "'"), "`")
            "pre" -> "$PARA```$PRE_NL" + el.wholeText().trim('\n').replace("\n", PRE_NL.toString()) + "$PRE_NL```$PARA"
            "a" -> link(el, depth)
            "img", "en-media" -> image(el)?.let { LINE + it + LINE }.orEmpty()
            "en-todo" -> if (el.attr("checked").equals("true", ignoreCase = true)) "- [x] " else "- [ ] "
            "en-crypt" -> "🔒"
            "ul", "ol" -> list(el, depth)
            "blockquote" -> PARA + finish(children(el, depth)).lines().joinToString("\n") { "> $it" } + PARA
            "table" -> table(el, depth)
            "span", "font" -> styled(el, children(el, depth))
            "div", "section", "article", "header", "footer", "main", "aside", "nav", "figure", "figcaption",
            "address", "center", "details", "summary", "dt", "dd", "li", "tr", "en-note", "body", "html" -> {
                val inner = children(el, depth)
                // An empty block (Evernote's <div><br/></div>) is an empty line
                if (inner.all { it.isWhitespace() || it in MARKERS }) "$LINE$EMPTY$LINE" else LINE + inner + LINE
            }
            else -> children(el, depth, pre)
        }
    }

    private fun list(el: Element, depth: Int): String {
        val ordered = el.tagName().equals("ol", ignoreCase = true)
        var number = el.attr("start").toIntOrNull() ?: 1
        val checklist = el.className().contains("checklist", true) || el.className().contains("task", true) ||
            el.attr("data-type").contains("task", true)
        val lines = mutableListOf<String>()
        for (li in el.children().filter { it.tagName().equals("li", ignoreCase = true) }) {
            val nested = li.children().filter { it.tagName().lowercase() in setOf("ul", "ol") }
            val text = finish(li.childNodes().filterNot { it in nested }.joinToString("") { render(it, depth + 1, false) })
            val marker = if (ordered) "${number++}. " else "- "
            lines += indent(depth) + marker + checkbox(li, checklist) + text.replace("\n", "\n" + indent(depth + 1))
            nested.forEach { lines += list(it, depth + 1) }
        }
        val joined = lines.joinToString("\n")
        return if (depth == 0) PARA + joined + PARA else joined
    }

    private fun indent(depth: Int) = INDENT.toString().repeat(4 * depth)

    private fun checkbox(li: Element, checklist: Boolean): String {
        fun Element.isCheckbox() = tagName() == "input" && attr("type").equals("checkbox", true)
        val input = li.children().firstOrNull { it.isCheckbox() }
            ?: li.children().firstOrNull()?.children()?.firstOrNull { it.isCheckbox() }
        if (input != null) return if (input.hasAttr("checked")) "[x] " else "[ ] "
        if (!checklist && !li.hasAttr("data-checked")) return ""
        val checked = li.attr("data-checked") == "true" ||
            li.classNames().any { it.equals("checked", true) || it.endsWith("--checked", true) }
        return if (checked) "[x] " else "[ ] "
    }

    private fun link(el: Element, depth: Int): String {
        val text = oneLine(children(el, depth))
        val href = el.attr("href").trim().replace(" ", "%20").replace(")", "%29")
        return when {
            href.isEmpty() || href.startsWith("#") || href.startsWith("javascript:", true) -> text
            text.isEmpty() || text == href || text == el.attr("href").trim() -> href
            else -> "[$text]($href)"
        }
    }

    private fun table(el: Element, depth: Int): String {
        val rows = el.select("tr").filter { it.closest("table") === el }.map { row ->
            row.children().filter { it.tagName().lowercase() in setOf("td", "th") }
                .map { oneLine(children(it, depth)).replace("|", "\\|") }
        }.filter { it.isNotEmpty() }
        if (rows.isEmpty()) return ""
        val width = rows.maxOf { it.size }
        val lines = rows.map { cells -> "| " + (cells + List(width - cells.size) { "" }).joinToString(" | ") + " |" }
        return PARA + (listOf(lines.first(), "|" + " --- |".repeat(width)) + lines.drop(1)).joinToString("\n") + PARA
    }

    /** Google Keep / Docs style bold and italic set with CSS. */
    private fun styled(el: Element, inner: String): String {
        val style = el.attr("style").lowercase().replace(" ", "")
        var out = inner
        if (Regex("font-weight:(bold|[6-9]00)").containsMatchIn(style)) out = wrap(out, "**")
        if ("font-style:italic" in style) out = wrap(out, "*")
        if ("text-decoration:line-through" in style) out = wrap(out, "~~")
        return out
    }

    private fun wrap(inner: String, mark: String): String {
        if (inner.isBlank() || inner.trim().any { it == '\n' || it in MARKERS }) return inner
        val start = inner.takeWhile { it == ' ' }
        val end = inner.takeLastWhile { it == ' ' }
        return start + mark + inner.trim() + mark + end
    }

    private fun oneLine(text: String) =
        text.map { if (it == '\n' || it in MARKERS) ' ' else it }.joinToString("").replace(Regex(" {2,}"), " ").trim()

    private fun collapse(text: String) = text.replace('\u00A0', ' ').replace(Regex("[ \\t\\r\\n]+"), " ")

    private fun escape(text: String) = text.replace("\\", "\\\\").replace("*", "\\*").replace("`", "\\`")

    private companion object {
        // Layout markers, resolved by finish(): a block on its own line, a paragraph (blank line around),
        // an empty line, list indentation and newlines inside code (both kept as they are)
        const val LINE = '\u0001'
        const val PARA = '\u0002'
        const val EMPTY = '\u0003'
        const val INDENT = '\u0004'
        const val PRE_NL = '\u0005'
        val MARKERS = setOf(LINE, PARA, EMPTY, INDENT, PRE_NL)
        private val breaks = Regex("[ \\t]*(?:[\\u0001\\u0002\\u0003\\n][ \\t]*)+")

        fun finish(text: String): String {
            val resolved = breaks.replace(text) { match ->
                val run = match.value
                val base = when {
                    PARA in run -> 2
                    LINE in run -> 1
                    else -> 0
                }
                "\n".repeat(maxOf(run.count { it == '\n' }, base) + run.count { it == EMPTY })
            }
            return resolved.replace(INDENT, ' ').replace(PRE_NL, '\n')
                .lines().joinToString("\n") { it.trimEnd() }
                .replace(Regex("\n{3,}"), "\n\n")
                .trim('\n', ' ')
        }
    }
}
