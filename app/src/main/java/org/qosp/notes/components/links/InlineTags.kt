package org.qosp.notes.components.links

import java.util.Locale

/** Obsidian-style inline tags (`#tag`, `#parent/child`) written in Markdown text. */
object InlineTags {
    // `#` must not follow a word char or one of / # & [ ] : . - which rejects `word#x`, URL fragments and `[[a#h]]`.
    private val TAG = Regex("""(?<![\p{L}\p{N}_/#&\]\[:.-])#([\p{L}\p{N}_/-]+)""")

    /** Tag names (without `#`) in order, distinct case-insensitively (first spelling wins). Code and wiki links are skipped. */
    fun find(markdown: String): List<String> {
        if (!markdown.contains('#')) return emptyList()
        val excluded = WikiLinks.excludedRanges(markdown) + WikiLinks.linkRanges(markdown)
        val seen = HashSet<String>()
        val result = ArrayList<String>()
        for (m in TAG.findAll(markdown)) {
            val name = m.groupValues[1]
            if (name.all { it.isDigit() } || name.endsWith('/')) continue
            if (excluded.any { m.range.first in it }) continue
            if (seen.add(name.lowercase(Locale.ROOT))) result.add(name)
        }
        return result
    }
}
