package org.qosp.notes.components.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Tag
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
        val index = LinkIndex(listOf(note(1, "A", "[[B]] [[C]]"), note(2, "B", "[[C]]"), note(3, "C"), note(4, "D", "[[C]]")))
        val (nodes, edges) = LinkGraph.build(index, focusKey = "note:2")
        assertEquals(setOf("A", "B", "C"), nodes.map { it.title }.toSet())
        // every edge between kept nodes, including A-C between two neighbours
        assertEquals(
            setOf(GraphEdge("note:1", "note:2"), GraphEdge("note:1", "note:3"), GraphEdge("note:2", "note:3")),
            edges.toSet(),
        )
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

    @Test
    fun tagsOffGivesTodaysGraph() {
        val notes = listOf(
            note(1, "A", "[[B]] #x").copy(tags = listOf(Tag("Prayer")), attachments = listOf(Attachment(fileName = "f.png"))),
            note(2, "B"),
        )
        val plain = LinkGraph.build(LinkIndex(notes))
        assertEquals(setOf("note:1", "note:2"), plain.first.map { it.key }.toSet())
        assertEquals(listOf(GraphEdge("note:1", "note:2")), plain.second)
        assertTrue(plain.first.all { it.kind == NodeKind.NOTE })
    }

    @Test
    fun appAndInlineTagsMerge() {
        val index = LinkIndex(listOf(note(1, "A").copy(tags = listOf(Tag("Prayer"))), note(2, "B", "#prayer")))
        val (nodes, edges) = LinkGraph.build(index, tags = true)
        val tag = nodes.single { it.kind == NodeKind.TAG }
        assertEquals("tag:prayer", tag.key)
        assertEquals("#Prayer", tag.title)
        assertEquals(2, tag.degree)
        assertNull(tag.noteId)
        assertEquals(setOf(GraphEdge("note:1", "tag:prayer"), GraphEdge("note:2", "tag:prayer")), edges.toSet())
    }

    @Test
    fun tagOnlyNoteCountsAsLinked() {
        val index = LinkIndex(listOf(note(1, "A", "#solo")))
        assertTrue(LinkGraph.build(index).first.isEmpty())
        assertEquals(setOf("note:1", "tag:solo"), LinkGraph.build(index, tags = true).first.map { it.key }.toSet())
    }

    @Test
    fun attachmentsBecomeNodes() {
        val index = LinkIndex(listOf(note(1, "A").copy(attachments = listOf(Attachment(Attachment.Type.AUDIO, "p", "d", ""), Attachment()))))
        val (nodes, edges) = LinkGraph.build(index, attachments = true)
        val file = nodes.single { it.kind == NodeKind.ATTACHMENT }
        assertEquals("file:1:0", file.key)
        assertEquals(1L, file.noteId)
        assertEquals("d", file.title)
        assertEquals(Attachment.Type.AUDIO, file.attachmentType)
        assertEquals(listOf(GraphEdge("note:1", file.key)), edges)
    }

    @Test
    fun hiddenAndBinNotesGiveNoTagsOrFiles() {
        val index = LinkIndex(listOf(
            note(1, "H", "#x").copy(isHidden = true, attachments = listOf(Attachment(fileName = "a"))),
            note(2, "D", "#x").copy(isDeleted = true),
        ))
        val (nodes, edges) = LinkGraph.build(index, showUnlinked = true, tags = true, attachments = true)
        assertTrue(nodes.isEmpty())
        assertTrue(edges.isEmpty())
    }

    private fun chain() = LinkIndex(listOf(
        note(1, "A", "[[B]]"), note(2, "B", "[[C]]"), note(3, "C", "[[D]]"),
        note(4, "D", "[[E]]"), note(5, "E", "[[F]]"), note(6, "F"),
    ))

    @Test
    fun depthFollowsLinks() {
        fun titles(depth: Int) = LinkGraph.build(chain(), focusKey = "note:1", depth = depth).first.map { it.title }.toSet()
        assertEquals(setOf("A", "B"), titles(1))
        assertEquals(setOf("A", "B", "C", "D"), titles(3))
        assertEquals(setOf("A", "B", "C", "D", "E", "F"), titles(9))
        val (_, edges) = LinkGraph.build(chain(), focusKey = "note:1", depth = 3)
        assertEquals(3, edges.size)
    }

    @Test
    fun depthCrossesTags() {
        val index = LinkIndex(listOf(note(1, "A", "#x"), note(2, "B", "#x")))
        val (nodes, _) = LinkGraph.build(index, focusKey = "note:1", depth = 2, tags = true)
        assertEquals(setOf("note:1", "tag:x", "note:2"), nodes.map { it.key }.toSet())
        val (shallow, _) = LinkGraph.build(index, focusKey = "note:1", depth = 1, tags = true)
        assertEquals(setOf("note:1", "tag:x"), shallow.map { it.key }.toSet())
    }
}
