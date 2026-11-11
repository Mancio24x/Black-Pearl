package org.qosp.notes.components

import android.content.Context
import android.content.res.AssetManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.components.links.WikiLinks
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.preferences.PreferenceRepository
import java.io.File
import java.io.FileInputStream

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

    private fun context(): Context {
        val assets = mockk<AssetManager>()
        every { assets.open(DemoLinksNote.ASSET) } answers { FileInputStream(File("src/main/assets/${DemoLinksNote.ASSET}")) }
        return mockk<Context>().also { every { it.assets } returns assets }
    }

    private fun preferences(initial: Boolean): PreferenceRepository {
        var added = initial
        val preferences = mockk<PreferenceRepository>()
        coEvery { preferences.isDemoLinksNoteAdded() } answers { added }
        coEvery { preferences.setDemoLinksNoteAdded() } answers { added = true }
        return preferences
    }

    @Test
    fun addsOnlyOnce() = runTest {
        val notes = mockk<NoteRepository>()
        coEvery { notes.insertNote(any(), any()) } returns 1L
        val context = context()
        val preferences = preferences(initial = false)

        DemoLinksNote.addOnce(context, notes, preferences)
        DemoLinksNote.addOnce(context, notes, preferences)

        coVerify(exactly = 1) { notes.insertNote(match { it.isLocalOnly && it.title == "The Bible" }, false) }
    }

    @Test
    fun skipsWhenAlreadyAdded() = runTest {
        val notes = mockk<NoteRepository>()
        coEvery { notes.insertNote(any(), any()) } returns 1L

        DemoLinksNote.addOnce(context(), notes, preferences(initial = true))

        coVerify(exactly = 0) { notes.insertNote(any(), any()) }
    }
}
