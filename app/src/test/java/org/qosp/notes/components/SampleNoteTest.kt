package org.qosp.notes.components

import android.content.Context
import android.content.res.AssetManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteIdTitle
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.preferences.PreferenceRepository
import java.io.File
import java.io.FileInputStream

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

    private fun context(): Context = mockk<Context>().also { c ->
        val assets = mockk<AssetManager>()
        every { assets.open(any()) } answers { FileInputStream(File("src/main/assets/${firstArg<String>()}")) }
        every { c.assets } returns assets
    }

    @Test
    fun addedOnceInItsOwnNotebook() = runTest {
        val notes = mockk<NoteRepository>(relaxed = true)
        val inserted = slot<Note>()
        coEvery { notes.insertNote(capture(inserted), any()) } returns 1L
        val notebooks = mockk<NotebookRepository>()
        val notebook = slot<Notebook>()
        coEvery { notebooks.insert(capture(notebook)) } returns 5L
        val prefs = mockk<PreferenceRepository>(relaxed = true)
        coEvery { prefs.isSampleNoteAdded() } returns false
        coEvery { prefs.getSampleNotebookId() } returns null

        SampleNote.addOnce(context(), notes, notebooks, prefs)

        assertEquals(SampleNote.NOTEBOOK, notebook.captured.name)
        assertEquals(5L, inserted.captured.notebookId)
        coVerify { prefs.setSampleNotebookId(5L) }
        coVerify { prefs.setSampleNoteAdded() }
        coVerify { prefs.setSampleNoteMoved() }
    }

    @Test
    fun anOlderInstallsNoteMovesIntoTheNotebookButOneInAnotherNotebookStays() = runTest {
        val loose = SampleNote.parse(markdown).copy(id = 11)
        val filed = SampleNote.parse(markdown).copy(id = 12, notebookId = 3)
        val other = Note(id = 13, title = "Shopping")
        val notes = mockk<NoteRepository>(relaxed = true)
        coEvery { notes.getIdTitlesOutside(any()) } returns listOf(loose, filed, other).map { NoteIdTitle(it.id, it.title) }
        every { notes.getById(11) } returns flowOf(loose)
        every { notes.getById(12) } returns flowOf(filed)
        val notebooks = mockk<NotebookRepository>()
        coEvery { notebooks.insert(any()) } returns 5L
        val prefs = mockk<PreferenceRepository>(relaxed = true)
        coEvery { prefs.isSampleNoteAdded() } returns true
        coEvery { prefs.isSampleNoteMoved() } returns false
        coEvery { prefs.getSampleNotebookId() } returns null

        assertTrue(SampleNote.moveIntoNotebook(context(), notes, notebooks, prefs))

        coVerify(exactly = 1) { notes.updateNotes(loose.copy(notebookId = 5L), sync = false) }
        coVerify { prefs.setSampleNoteMoved() }
    }

    @Test
    fun movedOnlyOnce() = runTest {
        val notes = mockk<NoteRepository>()
        val prefs = mockk<PreferenceRepository>()
        coEvery { prefs.isSampleNoteAdded() } returns true
        coEvery { prefs.isSampleNoteMoved() } returns true
        assertFalse(SampleNote.moveIntoNotebook(context(), notes, mockk(), prefs))
        coVerify(exactly = 0) { notes.updateNotes(any(), sync = any()) }
    }
}
