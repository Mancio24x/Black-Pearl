package org.qosp.notes.components.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.data.model.Note
import java.io.File

class LinkGraphTest {

    private fun note(id: Long, title: String, content: String = "") =
        Note(id = id, title = title, content = content, modifiedDate = id)

    private fun demoNotes(): List<Note> {
        val text = File("../docs/superpowers/specs/linked-notes/The Bible.md").readText()
        return listOf(note(1, "The Bible", text), note(2, "Genesis", "[[The Bible]]"))
    }

    @Test
    fun demoHub() {
        val (nodes, edges) = LinkGraph.build(LinkIndex(demoNotes()))
        assertEquals(70, nodes.size)
        val hub = nodes.single { it.title == "The Bible" }
        assertEquals(69, hub.degree)
        assertNotNull(hub.noteId)
        assertNotNull(nodes.single { it.title == "Genesis" }.noteId)
        assertEquals(68, nodes.count { it.noteId == null })
        assertEquals(69, edges.size) // Genesis links back to the hub: still one edge between them
    }

    @Test
    fun notesLinkingToEachOtherShareOneEdge() {
        val index = LinkIndex(listOf(note(1, "A", "[[B]]"), note(2, "B", "[[A]] [[a]]")))
        val (nodes, edges) = LinkGraph.build(index)
        assertEquals(1, edges.size)
        assertEquals(listOf(1, 1), nodes.map { it.degree })
    }

    @Test
    fun unlinkedHiddenUnlessAsked() {
        val index = LinkIndex(listOf(note(1, "A", "[[B]]"), note(2, "B"), note(3, "Lonely")))
        assertFalse(LinkGraph.build(index).first.any { it.title == "Lonely" })
        assertTrue(LinkGraph.build(index, showUnlinked = true).first.any { it.title == "Lonely" })
    }

    @Test
    fun focusShowsNeighboursOnly() {
        val index = LinkIndex(listOf(note(1, "A", "[[B]]"), note(2, "B", "[[C]]"), note(3, "C"), note(4, "D", "[[C]]")))
        val (nodes, edges) = LinkGraph.build(index, focusKey = "note:2")
        assertEquals(setOf("A", "B", "C"), nodes.map { it.title }.toSet())
        assertEquals(setOf(GraphEdge("note:1", "note:2"), GraphEdge("note:2", "note:3")), edges.toSet())
    }

    @Test
    fun eachVisibleNoteHasItsOwnNodeAndOutgoingEdges() {
        val index = LinkIndex(listOf(note(1, "Same", "[[Other]]"), note(2, "same"),
            note(3, "Other", "[[Same]]"), note(4, "", "[[Other]]"), note(5, "Alone"),
            note(6, "Hidden").copy(isHidden = true), note(7, "Deleted").copy(isDeleted = true)))
        val (nodes, edges) = LinkGraph.build(index, showUnlinked = true)
        assertEquals(setOf(1L, 2L, 3L, 4L, 5L), nodes.map { it.noteId }.toSet())
        val ids = nodes.associate { it.key to it.noteId }
        assertEquals(setOf(1L to 3L, 3L to 2L, 4L to 3L), edges.map { ids[it.from] to ids[it.to] }.toSet())
    }

    @Test
    fun selfLinkNoEdge() {
        val index = LinkIndex(listOf(note(1, "A", "[[A]] [[B]] [[b]]"), note(2, "B")))
        val (nodes, edges) = LinkGraph.build(index)
        val source = nodes.single { it.noteId == 1L }
        val target = nodes.single { it.noteId == 2L }
        assertEquals(listOf(GraphEdge(source.key, target.key)), edges)
        assertEquals(2, nodes.size)
        assertNull(nodes.firstOrNull { it.degree == 0 })
    }
}
