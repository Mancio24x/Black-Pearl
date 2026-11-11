package org.qosp.notes.components.links

import java.util.Locale

/** One Obsidian-style link `[[target#heading|label]]` found in Markdown. [range] covers the whole `[[...]]`. */
data class WikiLink(val target: String, val heading: String?, val label: String?, val range: IntRange) {
    val shown: String get() = label?.trim()?.ifBlank { null } ?: target
}

object WikiLinks {
    private val LINK = Regex("""(?<!!)\[\[([^\[\]|#\n]+)(?:#([^\[\]|\n]*))?(?:\|([^\[\]\n]*))?]]""")

    /** All wiki links in [markdown], in order; embeds (`![[x]]`), code spans and fenced code are ignored. */
    fun find(markdown: String): List<WikiLink> {
        if (!markdown.contains("[[")) return emptyList()
        val excluded = excludedRanges(markdown)
        return LINK.findAll(markdown).mapNotNull { m ->
            val start = m.range.first
            if (excluded.any { start in it }) return@mapNotNull null
            val target = m.groupValues[1].trim()
            if (target.isEmpty()) return@mapNotNull null
            val heading = m.groups[2]?.value?.trim()?.ifEmpty { null }
            val label = m.groups[3]?.value
            WikiLink(target, heading, label, m.range)
        }.toList()
    }

    /** Normalized match key: trimmed, no trailing ".md", last "/" segment, lowercase. */
    fun key(title: String): String =
        title.trim()
            .removeSuffix(".md").removeSuffix(".MD")
            .substringAfterLast('/')
            .trim()
            .lowercase(Locale.ROOT)

    private fun excludedRanges(text: String): List<IntRange> {
        val fences = fencedRanges(text)
        val result = ArrayList<IntRange>(fences)
        var i = 0
        while (i < text.length) {
            val fence = fences.firstOrNull { i in it }
            if (fence != null) {
                i = fence.last + 1
                continue
            }
            if (text[i] != '`') {
                i++
                continue
            }
            var n = 0
            while (i + n < text.length && text[i + n] == '`') n++
            val close = findBacktickRun(text, i + n, n)
            if (close >= 0) {
                result.add(i until close + n)
                i = close + n
            } else {
                i += n
            }
        }
        return result
    }

    /** Index of the next run of exactly [n] backticks at or after [from], or -1. */
    private fun findBacktickRun(text: String, from: Int, n: Int): Int {
        var j = from
        while (j < text.length) {
            if (text[j] != '`') {
                j++
                continue
            }
            var run = 0
            while (j + run < text.length && text[j + run] == '`') run++
            if (run == n) return j
            j += run
        }
        return -1
    }

    /** Fenced code blocks: a line starting with ``` or ~~~ opens, the next such line closes (or end of text). */
    private fun fencedRanges(text: String): List<IntRange> {
        val ranges = ArrayList<IntRange>()
        var lineStart = 0
        var openAt = -1
        while (lineStart <= text.length) {
            var lineEnd = text.indexOf('\n', lineStart)
            if (lineEnd < 0) lineEnd = text.length
            val trimmed = text.substring(lineStart, lineEnd).trimStart()
            val isFence = trimmed.startsWith("```") || trimmed.startsWith("~~~")
            if (isFence) {
                if (openAt < 0) {
                    openAt = lineStart
                } else {
                    ranges.add(openAt until lineEnd)
                    openAt = -1
                }
            }
            lineStart = lineEnd + 1
        }
        if (openAt >= 0) ranges.add(openAt until text.length)
        return ranges
    }
}
