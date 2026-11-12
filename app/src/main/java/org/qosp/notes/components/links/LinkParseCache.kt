package org.qosp.notes.components.links

/**
 * Remembers the `[[links]]` and inline `#tags` found in each note, so rebuilding the [LinkIndex] after one note
 * changes only re-reads that note (the shipped Bible Study Kit alone is ~1300 notes, ~4.6 MB of text).
 */
class LinkParseCache {
    private class Entry(val content: String, val links: List<WikiLink>, val tags: List<String>)

    private val entries = HashMap<Long, Entry>()

    @Synchronized
    fun links(noteId: Long, content: String): List<WikiLink> = entry(noteId, content).links

    @Synchronized
    fun tags(noteId: Long, content: String): List<String> = entry(noteId, content).tags

    /** Forgets notes that are gone. */
    @Synchronized
    fun retain(noteIds: Set<Long>) {
        entries.keys.retainAll(noteIds)
    }

    private fun entry(noteId: Long, content: String): Entry {
        val old = entries[noteId]
        if (old != null && old.content == content) return old
        return Entry(content, WikiLinks.find(content), InlineTags.find(content)).also { entries[noteId] = it }
    }
}
