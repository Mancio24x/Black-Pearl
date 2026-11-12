package org.qosp.notes.components

import android.content.Context
import android.content.res.AssetManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.components.links.LinkIndex
import org.qosp.notes.components.links.WikiLinks
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.preferences.PreferenceRepository
import java.io.File
import java.io.FileInputStream

/** The Bible Study Kit shipped in the APK: one local note per file, in its own notebook, added once. */
class BibleStudyKitTest {

    private val root = File("src/main/assets/${BibleStudyKit.DIR}")

    private fun assets(): AssetManager = mockk<AssetManager>().also { a ->
        every { a.list(any()) } answers {
            val dir = File("src/main/assets/${firstArg<String>()}")
            dir.list() ?: emptyArray()
        }
        every { a.open(any()) } answers { FileInputStream(File("src/main/assets/${firstArg<String>()}")) }
    }

    @Test
    fun shipsTheWholeKitWithUniqueTitles() {
        val paths = BibleStudyKit.files(assets())
        assertEquals(1300, paths.size)
        val titles = paths.map { it.substringAfterLast('/').removeSuffix(".md") }
        assertEquals(titles.size, titles.map { it.lowercase() }.toSet().size)
        assertTrue(BibleStudyKit.START in titles)
        assertTrue("Gen-01" in titles)
    }

    @Test
    fun noteKeepsTheTextAndUsesTheFileName() {
        val text = File(root, "The Bible (WEB).md").readText()
        val note = BibleStudyKit.noteFrom("The Bible (WEB).md", text.replace("\n", "\r\n"), 7)
        assertEquals("The Bible (WEB)", note.title)
        assertEquals(text, note.content)
        assertEquals(7L, note.notebookId)
        assertTrue(note.isLocalOnly)
        assertTrue(WikiLinks.find(note.content).size >= 66)
    }

    @Test
    fun theNetworkSeesTheKitsLinks() {
        val a = assets()
        val notes = BibleStudyKit.files(a).mapIndexed { i, path ->
            val text = a.open("${BibleStudyKit.DIR}/$path").use { it.readBytes().decodeToString() }
            BibleStudyKit.noteFrom(path, text, 1).copy(id = i + 1L)
        }
        val index = LinkIndex(notes)
        val start = index.resolve(BibleStudyKit.START)!!
        assertTrue(index.outgoing(start.id).isNotEmpty())
        // chapter notes link to each other, so a chapter has backlinks
        val gen1 = index.resolve("Gen-01")!!
        assertTrue(index.backlinks(gen1.id).isNotEmpty())
    }

    @Test
    fun addedOnceInItsOwnNotebook() = runTest {
        val context = mockk<Context>().also { every { it.assets } returns assets() }
        val notes = mockk<NoteRepository>()
        val inserted = ArrayList<Note>()
        coEvery { notes.insertNote(capture(inserted), any()) } returns 1L
        val notebooks = mockk<NotebookRepository>()
        val notebook = slot<Notebook>()
        coEvery { notebooks.insert(capture(notebook)) } returns 42L
        val preferences = mockk<PreferenceRepository>(relaxed = true)
        coEvery { preferences.isBibleKitAdded() } returns false

        BibleStudyKit.addOnce(context, notes, notebooks, preferences)

        assertEquals(BibleStudyKit.NOTEBOOK, notebook.captured.name)
        assertEquals(1300, inserted.size)
        assertTrue(inserted.all { it.notebookId == 42L && it.isLocalOnly })
        coVerify { preferences.setBibleKitNotebookId(42L) }
        coVerify { preferences.setBibleKitAdded() }
        coVerify(exactly = 1300) { notes.insertNote(any(), false) }
    }

    @Test
    fun notAddedAgain() = runTest {
        val notes = mockk<NoteRepository>()
        val notebooks = mockk<NotebookRepository>()
        val preferences = mockk<PreferenceRepository>()
        coEvery { preferences.isBibleKitAdded() } returns true
        BibleStudyKit.addOnce(mockk(), notes, notebooks, preferences)
        coVerify(exactly = 0) { notes.insertNote(any(), any()) }
        coVerify(exactly = 0) { notebooks.insert(any()) }
    }
}
