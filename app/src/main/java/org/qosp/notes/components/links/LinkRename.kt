package org.qosp.notes.components.links

import org.qosp.notes.data.model.Note

/** Keeps `[[links]]` working when a note is renamed, like Obsidian's "update internal links". */
object LinkRename {

    /** True when links can follow a rename from [oldTitle] to [newTitle] (both linkable, really different). */
    fun canRename(oldTitle: String, newTitle: String): Boolean {
        val old = WikiLinks.key(oldTitle)
        val new = WikiLinks.key(newTitle)
        return old.isNotEmpty() && new.isNotEmpty() && old != new && newTitle.none { it in "[]|#\n" }
    }

    /**
     * [content] with the target of every `[[oldTitle…]]` link (outside code) replaced by [newTitle]; headings, labels,
     * a folder prefix and a `.md` ending are kept. Null when no link points to [oldTitle].
     */
    fun rewrite(content: String, oldTitle: String, newTitle: String): String? {
        val key = WikiLinks.key(oldTitle)
        val links = WikiLinks.find(content).filter { WikiLinks.key(it.target) == key }
        if (links.isEmpty()) return null
        val out = StringBuilder(content)
        for (link in links.sortedByDescending { it.range.first }) {
            val start = link.range.first + 2
            var end = start
            while (end < content.length && content[end] != '#' && content[end] != '|' && content[end] != ']') end++
            out.replace(start, end, retarget(content.substring(start, end), newTitle.trim()))
        }
        return out.toString()
    }

    private fun retarget(raw: String, newTitle: String): String {
        val lead = raw.takeWhile { it.isWhitespace() }
        val trail = raw.takeLastWhile { it.isWhitespace() }
        var core = raw.trim()
        val md = if (core.endsWith(".md", ignoreCase = true)) core.takeLast(3) else ""
        core = core.dropLast(md.length)
        val prefix = core.substring(0, core.lastIndexOf('/') + 1)
        return lead + prefix + newTitle + md + trail
    }

    /**
     * Notes (not in the bin, other than [renamedId]) whose links must follow the rename, already rewritten with
     * [now] as modified date. Empty when nothing to do, or when another note still has the old title (the links
     * then keep pointing to that one).
     */
    fun plan(notes: List<Note>, renamedId: Long, oldTitle: String, newTitle: String, now: Long): List<Note> {
        if (!canRename(oldTitle, newTitle)) return emptyList()
        val key = WikiLinks.key(oldTitle)
        val live = notes.filter { !it.isDeleted && it.id != renamedId }
        if (live.any { WikiLinks.key(it.title) == key }) return emptyList()
        return live.mapNotNull { note ->
            rewrite(note.content, oldTitle, newTitle)?.let { note.copy(content = it, modifiedDate = now) }
        }
    }
}
