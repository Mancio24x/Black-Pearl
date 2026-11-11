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
        assertEquals(edges.size, edges.toSet().size)
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
        val (nodes, edges) = LinkGraph.build(index, focusKey = "b")
        assertEquals(setOf("a", "b", "c"), nodes.map { it.key }.toSet())
        assertEquals(setOf(GraphEdge("a", "b"), GraphEdge("b", "c")), edges.toSet())
    }

    @Test
    fun selfLinkNoEdge() {
        val index = LinkIndex(listOf(note(1, "A", "[[A]] [[B]] [[b]]"), note(2, "B")))
        val (nodes, edges) = LinkGraph.build(index)
        assertEquals(listOf(GraphEdge("a", "b")), edges)
        assertEquals(2, nodes.size)
        assertNull(nodes.firstOrNull { it.degree == 0 })
    }
}
