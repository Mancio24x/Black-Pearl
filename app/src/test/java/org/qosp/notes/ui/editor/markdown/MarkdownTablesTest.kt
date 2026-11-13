package org.qosp.notes.ui.editor.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MarkdownTablesTest {
    private val t1 = "| a | b |\n|---|---|\n| 1 | 2 |\n"
    private val t2 = "| x |\n|---|\n| 9 |"
    private val note = "Intro\n\n$t1\nMiddle\n\n$t2"

    @Test
    fun findsTwoTables() {
        val found = MarkdownTables.find(note)
        assertEquals(2, found.size)
        assertEquals(t1, note.substring(found[0]))
        assertEquals(t2, note.substring(found[1]))
    }

    @Test
    fun skipsFencedCode() {
        val content = "```\n$t1```\n\n$t2"
        val found = MarkdownTables.find(content)
        assertEquals(1, found.size)
        assertEquals(t2, content.substring(found[0]))
    }

    @Test
    fun needsADelimiterRow() {
        assertEquals(0, MarkdownTables.find("a | b\nc | d\n").size)
    }

    @Test
    fun indexSkipsTooWide() {
        val wide = "| a | b | c |\n|---|---|---|\n\n"
        val found = MarkdownTables.find(wide + t2, maxColumns = 2)
        assertEquals(1, found.size)
        assertEquals(t2, (wide + t2).substring(found[0]))
    }

    @Test
    fun atOffset() {
        val inSecond = note.indexOf("9")
        assertEquals(t2, note.substring(MarkdownTables.at(note, inSecond)!!))
        assertNull(MarkdownTables.at(note, note.indexOf("Middle")))
        // a cursor right after the table's last character still counts
        assertEquals(t2, note.substring(MarkdownTables.at(note, note.length)!!))
    }

    @Test
    fun replaceOnlyIfUnchanged() {
        val range = MarkdownTables.find(note)[0]
        val replaced = MarkdownTables.replace(note, range, t1, "| new |\n|---|\n")
        assertEquals("Intro\n\n| new |\n|---|\n\nMiddle\n\n$t2", replaced)
        assertNull(MarkdownTables.replace("changed$note", range, t1, "x"))
    }

    @Test
    fun insertKeepsTheTableApart() {
        val table = "| a |\n|---|\n"
        assertEquals("| a |\n|---|\n" to 11, MarkdownTables.insert("", -1, table))
        assertEquals("Text\n\n| a |\n|---|\n" to 17, MarkdownTables.insert("Text", -1, table))
        assertEquals("Text\n\n| a |\n|---|\n\nmore" to 17, MarkdownTables.insert("Text\nmore", 5, table))
        assertEquals("Te\n\n| a |\n|---|\n\nxt" to 15, MarkdownTables.insert("Text", 2, table))
        // every table found where it was put
        val (updated, _) = MarkdownTables.insert("Text\nmore", 5, table)
        assertEquals(table, updated.substring(MarkdownTables.find(updated).single()))
    }

    @Test
    fun countsLikeThePreviewParser() {
        assertEquals(0, MarkdownTables.find("Text\n| a | b |\n|---|---|\n| 1 | 2 |").size) // glued to a paragraph
        assertEquals(1, MarkdownTables.find("# H\n| a |\n|---|\n").size)
        assertEquals(0, MarkdownTables.find("    | a |\n    |---|").size) // indented code
        val quoted = "> | a |\n> |---|"
        assertEquals(1, MarkdownTables.find(quoted).size) // counted, so later tables keep their index
        assertNull(TableGrid.parse(quoted.substring(MarkdownTables.find(quoted).single()))) // but not edited
    }
}
