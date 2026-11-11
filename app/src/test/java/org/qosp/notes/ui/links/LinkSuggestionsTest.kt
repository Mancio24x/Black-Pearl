package org.qosp.notes.ui.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.qosp.notes.components.links.LinkIndex
import org.qosp.notes.data.model.Note
import org.qosp.notes.ui.links.Suggestion.Kind.CREATE
import org.qosp.notes.ui.links.Suggestion.Kind.GHOST
import org.qosp.notes.ui.links.Suggestion.Kind.NOTE

class LinkSuggestionsTest {

    private fun note(id: Long, title: String, content: String = "") =
        Note(id = id, title = title, content = content, modifiedDate = id)

    @Test
    fun queryDetection() {
        val a = "Next: [[Ex"
        assertEquals("Ex", LinkSuggestions.query(a, a.length))
        val b = "[[a]] b"
        assertNull(LinkSuggestions.query(b, b.length))
        val c = "[[a\nb"
        assertNull(LinkSuggestions.query(c, c.length))
        assertEquals("", LinkSuggestions.query("[[", 2))
        assertNull(LinkSuggestions.query("no link", 7))
        assertNull(LinkSuggestions.query("![[pho", 6))
    }

    @Test
    fun suggestOrder() {
        // newest first: "Exodus notes" (id 2) before "Index of texts" (id 1)
        val index = LinkIndex(
            listOf(
                note(1, "Index of texts", "see [[Exodus]] and [[Ezekiel]]"),
                note(2, "Exodus notes"),
            )
        )
        val expected = listOf(
            Suggestion("Exodus notes", NOTE),
            Suggestion("Exodus", GHOST),
            Suggestion("Index of texts", NOTE),
            Suggestion("ex", CREATE),
        )
        assertEquals(expected, LinkSuggestions.suggest("ex", index, selfId = null))
    }

    @Test
    fun suggestExcludesSelfAndExactMatchHasNoCreate() {
        val index = LinkIndex(listOf(note(1, "Exodus"), note(2, "Exodus notes")))
        assertEquals(
            listOf(Suggestion("Exodus", NOTE)),
            LinkSuggestions.suggest("EXODUS", index, selfId = 2L)
        )
    }

    @Test
    fun suggestCapsAtMax() {
        val index = LinkIndex((1L..20L).map { note(it, "Note $it") })
        val result = LinkSuggestions.suggest("note", index, selfId = null)
        assertEquals(8, result.size)
        assertEquals(Suggestion("note", CREATE), result.last())
    }

    @Test
    fun insertCompletes() {
        assertEquals("Next: [[Exodus]]" to 16, LinkSuggestions.insert("Next: [[Ex", 10, "Exodus"))
        assertEquals("a [[Exodus]] b" to 12, LinkSuggestions.insert("a [[Ex]] b", 6, "Exodus"))
    }
}
