package org.qosp.notes.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The notebook shipped in the APK becomes a note with a clean title and its Markdown untouched. */
class SampleNoteTest {

    private val markdown = File("src/main/assets/${SampleNote.ASSET}").readText()

    @Test
    fun headingIsTheTitleAndTheTextIsKept() {
        val note = SampleNote.parse(markdown)
        assertEquals("Quaderno delle Ricette Sburrate", note.title)
        assertTrue(note.content.startsWith("### Chef Leo\n"))
        assertTrue(markdown.trim('\n').endsWith(note.content))

        val lines = note.content.lines()
        assertEquals(5, lines.count { it.matches(Regex("""## \d\. .+""")) }) // the five recipes
        assertEquals(39, lines.count { it.startsWith("#### ") }) // every step
    }

    @Test
    fun plainLocalMarkdownNote() {
        val note = SampleNote.parse(markdown)
        assertTrue(note.isMarkdownEnabled)
        assertTrue(note.isLocalOnly) // not uploaded once per device when sync is on
        assertFalse(note.isList)
        assertFalse(note.isPinned)
    }

    @Test
    fun windowsLineEndings() {
        val windows = SampleNote.parse(markdown.replace("\n", "\r\n"))
        assertEquals("Quaderno delle Ricette Sburrate", windows.title)
        assertEquals(SampleNote.parse(markdown).content, windows.content)
    }
}
