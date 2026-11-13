package org.qosp.notes.ui.editor.markdown

/**
 * Black Pearl: where the Markdown tables are in a note's text, so the table editor can open and replace one.
 * A table is a line with a pipe followed by a delimiter row (`|---|---|`), up to the next blank line. Tables
 * inside fenced or indented code blocks, or glued under a paragraph line, are not tables (as for the preview's
 * parser). Tables inside a quote (`> | a |`) are found so the count matches, but [TableGrid.parse] rejects their
 * text, so they are not edited. The k-th range found is the k-th table the preview draws, given the preview's
 * column limit as `maxColumns`.
 */
object MarkdownTables {
    private val QUOTE = Regex("^( {0,3}> ?)+")

    /** Ranges cover whole lines, including the last line's line break if there is one. */
    fun find(content: String, maxColumns: Int = Int.MAX_VALUE): List<IntRange> {
        val lines = lineStarts(content)
        val out = mutableListOf<IntRange>()
        var fence: String? = null
        var i = 0
        while (i < lines.size) {
            val text = lineText(content, lines, i)
            val trimmed = text.trimStart()
            if (fence != null) {
                if (trimmed.startsWith(fence)) fence = null
                i++
                continue
            }
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                fence = trimmed.take(3)
                i++
                continue
            }
            val quote = QUOTE.find(text)?.value.orEmpty()
            val inner = { k: Int -> lineText(content, lines, k).let { if (it.startsWith(quote)) it.substring(quote.length) else null } }
            val prev = if (i > 0) inner(i - 1) else ""
            val titles = TableGrid.splitRow(text.substring(quote.length))
            val aligns = if (titles != null && i + 1 < lines.size) inner(i + 1)?.let(TableGrid::delimiterRow) else null
            val startsBlock = prev == null || prev.isBlank() || prev.trimStart().startsWith("#") // not glued to a paragraph
            val indented = text.substring(quote.length).let { it.startsWith("    ") || it.startsWith("\t") } // code block
            if (titles == null || aligns == null || aligns.size != titles.size || !startsBlock || indented) {
                i++
                continue
            }
            var end = i + 2
            while (end < lines.size && inner(end)?.isNotBlank() == true) end++
            if (titles.size <= maxColumns) {
                val last = end - 1
                val endOffset = lines[last] + lineText(content, lines, last).length // exclusive, before the break
                val withBreak = if (endOffset < content.length) endOffset + 1 else endOffset
                out += lines[i] until withBreak
            }
            i = end
        }
        return out
    }

    /** The table that holds [offset] (a cursor just after a table's last character counts too), or null. */
    fun at(content: String, offset: Int): IntRange? = find(content).firstOrNull { range ->
        val end = if (content[range.last] == '\n') range.last else range.last + 1 // just after the last character
        offset in range.first..end
    }

    /** [content] with [range] replaced by [markdown], or null if [range] no longer holds [expected]. */
    fun replace(content: String, range: IntRange, expected: String, markdown: String): String? {
        if (range.first < 0 || range.last >= content.length) return null
        if (content.substring(range) != expected) return null
        return content.replaceRange(range, markdown)
    }

    /**
     * [content] with a new table [markdown] (ending in a line break) at [at] (-1 or out of range = the end), on
     * its own lines with a blank line before and after, so it does not merge with the text around it.
     * Also returns the offset just after the table's last character.
     */
    fun insert(content: String, at: Int, markdown: String): Pair<String, Int> {
        val pos = if (at in 0..content.length) at else content.length
        val before = content.substring(0, pos)
        val after = content.substring(pos)
        val lead = when {
            before.isEmpty() || before.endsWith("\n\n") -> ""
            before.endsWith("\n") -> "\n"
            else -> "\n\n"
        }
        val trail = if (after.isEmpty() || after.startsWith("\n")) "" else "\n"
        val table = lead + markdown + trail
        return before + table + after to pos + lead.length + markdown.trimEnd('\n').length
    }

    private fun lineStarts(content: String): List<Int> {
        val starts = mutableListOf(0)
        content.forEachIndexed { i, ch -> if (ch == '\n' && i + 1 < content.length) starts += i + 1 }
        return starts
    }

    private fun lineText(content: String, starts: List<Int>, i: Int): String {
        val start = starts[i]
        val end = content.indexOf('\n', start).let { if (it < 0) content.length else it }
        return content.substring(start, end)
    }
}
