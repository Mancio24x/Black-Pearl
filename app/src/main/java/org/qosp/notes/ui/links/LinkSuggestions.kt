package org.qosp.notes.ui.links

import org.qosp.notes.components.links.LinkIndex
import org.qosp.notes.components.links.WikiLinks

data class Suggestion(val title: String, val kind: Kind) {
    enum class Kind { NOTE, GHOST, CREATE }
}

/** Black Pearl: what to suggest after typing `[[` in the editor, and how to complete the link. Pure logic. */
object LinkSuggestions {

    /**
     * The text typed after the last unclosed `[[` on the cursor's line, or null when the cursor is not inside an
     * open link (no `[[`, a `]` or a newline in between, an embed `![[`, or a `|` / `#` already typed).
     */
    fun query(text: CharSequence, cursor: Int): String? = openStart(text, cursor)?.let {
        text.subSequence(it + 2, cursor).toString()
    }

    fun suggest(query: String, index: LinkIndex, selfId: Long?, max: Int = 8): List<Suggestion> {
        val q = WikiLinks.key(query)
        val seen = HashSet<String>()
        val notes = index.titles
            .filter { it.id != selfId && seen.add(WikiLinks.key(it.title)) }
            .map { it.title }
        val ghosts = index.ghostTargets().filter { seen.add(WikiLinks.key(it)) }

        fun prefix(title: String) = WikiLinks.key(title).startsWith(q)
        fun contains(title: String) = !prefix(title) && WikiLinks.key(title).contains(q)

        val matches = notes.filter(::prefix).map { Suggestion(it, Suggestion.Kind.NOTE) } +
            ghosts.filter(::prefix).map { Suggestion(it, Suggestion.Kind.GHOST) } +
            notes.filter(::contains).map { Suggestion(it, Suggestion.Kind.NOTE) } +
            ghosts.filter(::contains).map { Suggestion(it, Suggestion.Kind.GHOST) }

        val exact = matches.any { WikiLinks.key(it.title) == q }
        val create = query.isNotBlank() && !exact
        val result = matches.take(if (create) max - 1 else max)
        return if (create) result + Suggestion(query.trim(), Suggestion.Kind.CREATE) else result
    }

    /** Replaces `[[query` (and a `]]` right after the cursor) with `[[title]]`; returns new text and cursor. */
    fun insert(text: String, cursor: Int, title: String): Pair<String, Int> {
        val (start, end) = replaceRange(text, cursor)
        val link = "[[$title]]"
        return text.substring(0, start) + link + text.substring(end) to start + link.length
    }

    /** The range [start, end) that [insert] replaces; just the cursor when no link is open. */
    fun replaceRange(text: CharSequence, cursor: Int): Pair<Int, Int> {
        val start = openStart(text, cursor) ?: return cursor to cursor
        val end = if (text.length >= cursor + 2 && text[cursor] == ']' && text[cursor + 1] == ']') cursor + 2 else cursor
        return start to end
    }

    private fun openStart(text: CharSequence, cursor: Int): Int? {
        if (cursor < 2 || cursor > text.length) return null
        val lineStart = text.lastIndexOf('\n', cursor - 1) + 1
        val open = text.lastIndexOf("[[", cursor - 2)
        if (open < lineStart) return null
        if (open > 0 && text[open - 1] == '!') return null
        val typed = text.subSequence(open + 2, cursor)
        if (typed.any { it == ']' || it == '|' || it == '#' }) return null
        return open
    }
}
