package org.qosp.notes.components.links

import org.qosp.notes.data.model.Note
import java.util.Locale

data class Backlink(val from: Note, val line: String)

/** Indexes visible notes. Only notes with a linkable title can be wiki-link targets. */
class LinkIndex(notes: List<Note>, cache: LinkParseCache? = null) {
    val visibleNotes = notes.filter { !it.isDeleted && !it.isHidden }
        .sortedWith(compareByDescending<Note> { it.modifiedDate }.thenByDescending { it.id })
    val titles = visibleNotes.filter { WikiLinks.key(it.title).isNotEmpty() }

    private val byKey: Map<String, Note>
    private val tagMap = HashMap<Long, List<String>>()
    private val tagSpelling = LinkedHashMap<String, String>() // lowercase -> spelling
    private val outMap = HashMap<Long, List<WikiLink>>()
    private val backMap = HashMap<Long, MutableList<Backlink>>()
    private val ghostNames = LinkedHashMap<String, String>() // key -> first-seen spelling
    private val ghostCiters = HashMap<String, MutableList<Note>>()

    init {
        // titles is sorted newest first, so the first note per key is the newest one
        val map = LinkedHashMap<String, Note>()
        for (n in titles) map.putIfAbsent(WikiLinks.key(n.title), n)
        byKey = map

        // One spelling per tag across all notes; the spelling of an app tag wins over an inline one.
        for (note in visibleNotes) for (tag in note.tags) {
            val name = tag.name.trim()
            if (name.isNotEmpty()) tagSpelling.putIfAbsent(name.lowercase(Locale.ROOT), name)
        }
        val inlineTags = HashMap<Long, List<String>>()
        for (note in visibleNotes) {
            val found = cache?.tags(note.id, note.content) ?: InlineTags.find(note.content)
            inlineTags[note.id] = found
            for (name in found) tagSpelling.putIfAbsent(name.lowercase(Locale.ROOT), name)
        }
        for (note in visibleNotes) {
            val links = cache?.links(note.id, note.content) ?: WikiLinks.find(note.content)
            outMap[note.id] = links
            tagMap[note.id] = (note.tags.map { it.name.trim() } + inlineTags.getValue(note.id))
                .filter { it.isNotEmpty() }
                .map { tagSpelling.getValue(it.lowercase(Locale.ROOT)) }
                .distinct()
            val seenLines = HashSet<Pair<Long, String>>()
            for (link in links) {
                val key = WikiLinks.key(link.target)
                if (key.isEmpty()) continue
                val dest = byKey[key]
                if (dest != null) {
                    if (dest.id == note.id) continue
                    val line = lineAround(note.content, link.range.first)
                    if (seenLines.add(dest.id to line)) {
                        backMap.getOrPut(dest.id) { ArrayList() }.add(Backlink(note, line))
                    }
                } else {
                    ghostNames.putIfAbsent(key, link.target)
                    val citers = ghostCiters.getOrPut(key) { ArrayList() }
                    if (citers.none { it.id == note.id }) citers.add(note)
                }
            }
        }
    }

    fun resolve(target: String): Note? = byKey[WikiLinks.key(target)]

    /** Tag names (no `#`) of a visible note: app tags plus inline `#tags`, merged case-insensitively. */
    fun tagsOf(noteId: Long): List<String> = tagMap[noteId].orEmpty()

    fun outgoing(noteId: Long): List<WikiLink> = outMap[noteId].orEmpty()

    fun backlinks(noteId: Long): List<Backlink> = backMap[noteId].orEmpty()

    /** Targets that match no note, with the spelling of their first occurrence. */
    fun ghostTargets(): Set<String> = ghostNames.values.toSet()

    fun ghostCitedBy(target: String): List<Note> = ghostCiters[WikiLinks.key(target)].orEmpty()

    fun isGhost(target: String): Boolean {
        val key = WikiLinks.key(target)
        return key.isNotEmpty() && key !in byKey
    }

    private fun lineAround(text: String, index: Int): String {
        val start = text.lastIndexOf('\n', index - 1) + 1
        val end = text.indexOf('\n', index).let { if (it < 0) text.length else it }
        return text.substring(start, end).trim()
    }
}
