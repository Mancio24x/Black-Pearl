package org.qosp.notes.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.components.links.WikiLinks
import java.io.File

/** The demo note shipped in the APK becomes a local note titled "The Bible", with its links untouched. */
class DemoLinksNoteTest {

    private val markdown = File("src/main/assets/${DemoLinksNote.ASSET}").readText()

    @Test
    fun titleIsFixedAndTheHeadingLineIsDropped() {
        val note = DemoLinksNote.parse(markdown)
        assertEquals("The Bible", note.title)
        assertTrue(note.content.startsWith("links: [[010 Faith MOC|Faith MOC ]]"))
        assertFalse(note.content.lines().any { it == "# The Bible" })
        assertTrue(note.content.contains("– [[God's unified story]] –"))
    }

    @Test
    fun keepsAllTheLinks() {
        assertEquals(69, WikiLinks.find(DemoLinksNote.parse(markdown).content).size)
    }

    @Test
    fun localOnlyMarkdownNote() {
        val note = DemoLinksNote.parse(markdown)
        assertTrue(note.isMarkdownEnabled)
        assertTrue(note.isLocalOnly)
        assertFalse(note.isList)
    }

    @Test
    fun windowsLineEndings() {
        val windows = DemoLinksNote.parse(markdown.replace("\n", "\r\n"))
        assertEquals("The Bible", windows.title)
        assertEquals(DemoLinksNote.parse(markdown).content, windows.content)
    }
}
