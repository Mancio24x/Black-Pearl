package org.qosp.notes.components.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.data.model.Note

class LinkIndexTest {

    private fun note(id: Long, title: String, content: String = "", modified: Long = id, deleted: Boolean = false, hidden: Boolean = false) =
        Note(id = id, title = title, content = content, modifiedDate = modified, isDeleted = deleted, isHidden = hidden)

    @Test
    fun backlinksAndGhosts() {
        val a = note(1, "A", "see [[b]] and [[Missing]]")
        val b = note(2, "B", "x")
        val index = LinkIndex(listOf(a, b))
        assertEquals(b, index.resolve("B"))
        assertEquals("see [[b]] and [[Missing]]", index.backlinks(b.id).single().line)
        assertEquals(a, index.backlinks(b.id).single().from)
        assertEquals(setOf("Missing"), index.ghostTargets())
        assertEquals(listOf(a), index.ghostCitedBy("missing"))
        assertTrue(index.isGhost("Missing"))
        assertFalse(index.isGhost("B"))
        assertEquals(listOf("b", "Missing"), index.outgoing(a.id).map { it.target })
    }

    @Test
    fun duplicateTitlesPickNewest() {
        val old = note(1, "Same", modified = 10)
        val new = note(2, "same", modified = 20)
        val index = LinkIndex(listOf(old, new))
        assertEquals(2L, index.resolve("SAME")?.id)
    }

    @Test
    fun binAndHiddenExcluded() {
        val bin = note(1, "Bin", deleted = true)
        val hid = note(2, "Hid", hidden = true)
        val src = note(3, "Src", "[[Bin]] [[Hid]]")
        val index = LinkIndex(listOf(bin, hid, src))
        assertNull(index.resolve("Bin"))
        assertEquals(listOf(src), index.titles)
        assertEquals(setOf("Bin", "Hid"), index.ghostTargets())
    }

    @Test
    fun blankTitleIsNeverATarget() {
        val blank = note(1, "")
        val src = note(2, "Src", "[[ ]] [[x]]")
        val index = LinkIndex(listOf(blank, src))
        assertNull(index.resolve(""))
        assertEquals(listOf(src), index.titles)
        assertEquals(setOf("x"), index.ghostTargets())
    }

    @Test
    fun buildsTwoThousandNotesQuickly() {
        val notes = (1L..2000L).map { i ->
            note(i, "Note $i", (1..5).joinToString(" ") { "[[Note ${(i * 7 + it * 13) % 2000 + 1}]]" })
        }
        val start = System.nanoTime()
        val index = LinkIndex(notes)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 1000)
        assertEquals(2000, index.titles.size)
    }

    @Test
    fun titleThatNoLinkCanNameIsNeverATarget() {
        val index = LinkIndex(listOf(Note(id = 1, title = "/", modifiedDate = 1), Note(id = 2, title = ".md", modifiedDate = 2)))
        assertEquals(emptyList<Note>(), index.titles)
        assertEquals(null, index.resolve(""))
        assertEquals(null, index.resolve("/"))
    }
}
