package org.qosp.notes.components.links

import java.util.Locale

/** Obsidian-style inline tags (`#tag`, `#parent/child`) written in Markdown text. */
object InlineTags {
    // `#` must not follow a word char or one of / # & [ ] : . - ? = " ' which rejects `word#x`, URL fragments
    // (`/#x`, `?#x`, `=#x`), `[[a#h]]` and HTML attributes. Combining marks keep decomposed accents in the name.
    private val TAG = Regex("""(?<![\p{L}\p{M}\p{N}_/#&\]\[:.?="'-])#([\p{L}\p{M}\p{N}_/-]+)""")
    private val EMBED = Regex("""!\[\[[^\]\n]*]]""")

    /** Tag names (without `#`) in order, distinct case-insensitively (first spelling wins). Code and wiki links are skipped. */
    fun find(markdown: String): List<String> {
        if (!markdown.contains('#')) return emptyList()
        val excluded = WikiLinks.excludedRanges(markdown) + WikiLinks.linkRanges(markdown) +
            EMBED.findAll(markdown).map { it.range }
        val seen = HashSet<String>()
        val result = ArrayList<String>()
        for (m in TAG.findAll(markdown)) {
            val start = m.range.first
            // `[text](#section)` is a Markdown anchor link, not a tag
            if (start >= 2 && markdown[start - 1] == '(' && markdown[start - 2] == ']') continue
            val name = m.groupValues[1].trim('/')
            if (!isTagName(name)) continue
            if (excluded.any { start in it }) continue
            if (seen.add(name.lowercase(Locale.ROOT))) result.add(name)
        }
        return result
    }

    /** At least one letter or digit, and not only digits: `#2024a`, `#_1`, `#a/b` yes; `#2024`, `#-`, `#_`, `#//` no. */
    private fun isTagName(name: String): Boolean =
        name.any { it.isLetterOrDigit() } && !name.all { it.isDigit() }
}
