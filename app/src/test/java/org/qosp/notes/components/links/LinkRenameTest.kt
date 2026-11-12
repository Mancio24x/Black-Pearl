package org.qosp.notes.components.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.data.model.Note

class LinkRenameTest {

    @Test
    fun rewritesEveryFormAndKeepsTheRest() {
        val text = "See [[Genesis]], [[genesis|the first book]], [[Genesis#Creation]] and [[Genesis#Fall|fall]].\n" +
            "Also [[Bible/Genesis.md]] and [[ Genesis ]] but not [[Exodus]] or ![[Genesis]]."
        assertEquals(
            "See [[Genesis 1]], [[Genesis 1|the first book]], [[Genesis 1#Creation]] and [[Genesis 1#Fall|fall]].\n" +
                "Also [[Bible/Genesis 1.md]] and [[ Genesis 1 ]] but not [[Exodus]] or ![[Genesis]].",
            LinkRename.rewrite(text, "Genesis", "Genesis 1"),
        )
    }

    @Test
    fun leavesCodeAloneAndReturnsNullWithoutLinks() {
        assertEquals("`[[Genesis]]` [[Genesis 1]]", LinkRename.rewrite("`[[Genesis]]` [[Genesis]]", "Genesis", "Genesis 1"))
        assertNull(LinkRename.rewrite("No links to [[Exodus]] here", "Genesis", "Genesis 1"))
    }

    @Test
    fun onlyRealRenamesToLinkableTitles() {
        assertTrue(LinkRename.canRename("Genesis", "Genesis 1"))
        assertFalse(LinkRename.canRename("Genesis", "genesis"))
        assertFalse(LinkRename.canRename("", "Genesis"))
        assertFalse(LinkRename.canRename("Genesis", ""))
        assertFalse(LinkRename.canRename("Genesis", "Genesis #1"))
        assertFalse(LinkRename.canRename("Genesis", "Gen|esis"))
    }

    @Test
    fun planUpdatesLinkingNotesExceptBinAndItself() {
        val notes = listOf(
            Note(id = 1, title = "Genesis 1", content = "self [[Genesis]]"),
            Note(id = 2, title = "Bible", content = "[[Genesis]]", modifiedDate = 5),
            Note(id = 3, title = "Old", content = "[[Genesis]]", isDeleted = true),
            Note(id = 4, title = "Other", content = "[[Exodus]]"),
            Note(id = 5, title = "Hidden", content = "[[Genesis|g]]", isHidden = true),
        )
        val planned = LinkRename.plan(notes, renamedId = 1, oldTitle = "Genesis", newTitle = "Genesis 1", now = 99)
        assertEquals(listOf(2L, 5L), planned.map { it.id })
        assertEquals("[[Genesis 1]]", planned[0].content)
        assertEquals(99L, planned[0].modifiedDate)
        assertEquals("[[Genesis 1|g]]", planned[1].content)
    }

    @Test
    fun planDoesNothingWhileAnotherNoteKeepsTheOldTitle() {
        val notes = listOf(
            Note(id = 1, title = "Genesis 1"),
            Note(id = 2, title = "Genesis"),
            Note(id = 3, title = "Bible", content = "[[Genesis]]"),
        )
        assertEquals(emptyList<Note>(), LinkRename.plan(notes, 1, "Genesis", "Genesis 1", 0))
    }
}
