package org.qosp.notes.components.links

import java.util.Locale

/** One Markdown heading of a note: [level] 1–6, [text] as shown (inline markup removed), [offset] = start of its line. */
data class OutlineHeading(val level: Int, val text: String, val offset: Int)

/**
 * The note's table of contents, like Obsidian's Outline: every ATX heading (`#` … `######`) in order.
 * Headings inside fenced code, indented code and the YAML front matter are skipped; setext headings
 * (`Title` + `===`) are not listed.
 */
object NoteOutline {
    private val CLOSING = Regex("""(^|[ \t])#+$""")
    private val BLOCK_ID = Regex("""[ \t]\^[A-Za-z0-9-]+$""")
    private val MD_LINK = Regex("""(?<!!)\[([^\[\]\n]*)]\([^()\n]*\)""")
    private val ESCAPE = Regex("""\\([\\`*_{}\[\]()#+\-.!|~=])""")
    private val MARKS = Regex("""\*\*|__|~~|==|`""")
    private val STAR = Regex("""(?<![\w*])\*(?=\S)(.+?)(?<=\S)\*(?![\w*])""")
    private val UNDERSCORE = Regex("""(?<![\w_])_(?=\S)(.+?)(?<=\S)_(?![\w_])""")
    private val SPACES = Regex("""\s+""")

    fun headings(markdown: String): List<OutlineHeading> {
        if (!markdown.contains('#')) return emptyList()
        val fences = WikiLinks.fencedRanges(markdown)
        val result = ArrayList<OutlineHeading>()
        var lineStart = frontMatterEnd(markdown)
        while (lineStart < markdown.length) {
            var lineEnd = markdown.indexOf('\n', lineStart)
            if (lineEnd < 0) lineEnd = markdown.length
            if (fences.none { lineStart in it }) {
                parse(markdown.substring(lineStart, lineEnd).removeSuffix("\r"))?.let { (level, text) ->
                    result.add(OutlineHeading(level, text, lineStart))
                }
            }
            lineStart = lineEnd + 1
        }
        return result
    }

    /** Key used to find a heading in the rendered preview: spaces collapsed, lowercase. */
    fun matchKey(text: String): String = text.replace(SPACES, " ").trim().lowercase(Locale.ROOT)

    private fun parse(line: String): Pair<Int, String>? {
        var i = 0
        while (i < line.length && line[i] == ' ') i++
        if (i > 3) return null
        val hashes = i
        while (i < line.length && line[i] == '#') i++
        val level = i - hashes
        if (level !in 1..6) return null
        if (i < line.length && line[i] != ' ' && line[i] != '\t') return null // "#tag", not a heading
        val raw = line.substring(i).trim().replace(CLOSING, "").trim()
        val text = plain(raw)
        return if (text.isEmpty()) null else level to text
    }

    /** Heading text as the preview shows it: links become their label, emphasis and code marks are dropped. */
    internal fun plain(raw: String): String {
        var s = raw.replace(BLOCK_ID, "")
        WikiLinks.find(s).asReversed().forEach { s = s.replaceRange(it.range, it.label?.trim()?.ifBlank { null } ?: it.target) }
        s = s.replace(MD_LINK, "$1")
        // escaped characters are kept aside so they are not taken for markup
        val escaped = ArrayList<String>()
        s = ESCAPE.replace(s) { escaped.add(it.groupValues[1]); "\u0000${escaped.size - 1}\u0000" }
        s = s.replace(MARKS, "").replace(STAR, "$1").replace(UNDERSCORE, "$1")
        s = Regex("\u0000(\\d+)\u0000").replace(s) { escaped[it.groupValues[1].toInt()] }
        return s.replace(SPACES, " ").trim()
    }

    /** Offset just after a YAML front matter block (`---` on the first line … `---` or `...`), else 0. */
    private fun frontMatterEnd(text: String): Int {
        val first = text.indexOf('\n')
        if (first < 0 || text.substring(0, first).trimEnd('\r', ' ') != "---") return 0
        var lineStart = first + 1
        while (lineStart < text.length) {
            var lineEnd = text.indexOf('\n', lineStart)
            if (lineEnd < 0) lineEnd = text.length
            val line = text.substring(lineStart, lineEnd).trimEnd('\r', ' ')
            if (line == "---" || line == "...") return lineEnd + 1
            lineStart = lineEnd + 1
        }
        return 0 // never closed: not front matter
    }
}
