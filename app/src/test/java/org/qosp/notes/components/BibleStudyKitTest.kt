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
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.components.links.LinkIndex
import org.qosp.notes.components.links.NoteOutline
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
    fun theBibleNoteHasItsBookGroupsInTheContents() {
        val headings = NoteOutline.headings(File(root, "The Bible (WEB).md").readText()).map { it.text }
        for (group in listOf("Pentateuch (or the Law)", "Gospels", "Pauline Epistles", "Apocalyptic Writing (Prophecy)")) {
            assertTrue(group, group in headings)
        }
        assertEquals(13, headings.size) // title, Copyright, Content and the 10 book groups
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
        val chunks = ArrayList<List<Note>>()
        coEvery { notes.insertLocalNotes(capture(chunks)) } answers { firstArg<List<Note>>().map { 1L } }
        val notebooks = mockk<NotebookRepository>()
        val notebook = slot<Notebook>()
        coEvery { notebooks.insert(capture(notebook)) } returns 42L
        val preferences = mockk<PreferenceRepository>(relaxed = true)
        coEvery { preferences.isBibleKitAdded() } returns false
        every { preferences.getBibleKitNotebookId() } returns flowOf(null)

        BibleStudyKit.addOnce(context, notes, notebooks, preferences)

        assertEquals(BibleStudyKit.NOTEBOOK, notebook.captured.name)
        val inserted = chunks.flatten()
        assertEquals(1300, inserted.size)
        assertTrue(chunks.all { it.size <= BibleStudyKit.CHUNK })
        assertEquals(7, chunks.size)
        assertTrue(inserted.all { it.notebookId == 42L && it.isLocalOnly })
        coVerify { preferences.setBibleKitNotebookId(42L) }
        coVerify { preferences.setBibleKitAdded() }
        coVerify(exactly = 0) { notes.insertNote(any(), any()) }
    }

    @Test
    fun anInterruptedFirstRunGoesOnInTheSameNotebookWithoutDuplicates() = runTest {
        val context = mockk<Context>().also { every { it.assets } returns assets() }
        val all = BibleStudyKit.files(assets())
        val already = all.take(500).map { BibleStudyKit.noteFrom(it, "", 7L) }
        val notes = mockk<NoteRepository>()
        val chunks = ArrayList<List<Note>>()
        coEvery { notes.insertLocalNotes(capture(chunks)) } answers { firstArg<List<Note>>().map { 1L } }
        every { notes.getByNotebook(7L, any()) } returns flowOf(already)
        val notebooks = mockk<NotebookRepository>()
        every { notebooks.getById(7L) } returns flowOf(Notebook(BibleStudyKit.NOTEBOOK, 7L))
        val preferences = mockk<PreferenceRepository>(relaxed = true)
        coEvery { preferences.isBibleKitAdded() } returns false
        every { preferences.getBibleKitNotebookId() } returns flowOf(7L)

        BibleStudyKit.addOnce(context, notes, notebooks, preferences)

        val inserted = chunks.flatten()
        assertEquals(800, inserted.size)
        assertTrue(chunks.all { it.size <= BibleStudyKit.CHUNK })
        assertTrue(inserted.all { it.notebookId == 7L })
        assertTrue(inserted.none { n -> already.any { it.title == n.title } })
        coVerify(exactly = 0) { notebooks.insert(any()) }
        coVerify(exactly = 0) { notes.insertNote(any(), any()) }
        coVerify { preferences.setBibleKitAdded() }
    }

    @Test
    fun aDeletedKitNotebookIsCreatedAgain() = runTest {
        val context = mockk<Context>().also { every { it.assets } returns assets() }
        val notes = mockk<NoteRepository>()
        val chunks = ArrayList<List<Note>>()
        coEvery { notes.insertLocalNotes(capture(chunks)) } answers { firstArg<List<Note>>().map { 1L } }
        val notebooks = mockk<NotebookRepository>()
        every { notebooks.getById(7L) } returns flowOf(null)
        coEvery { notebooks.insert(any()) } returns 9L
        val preferences = mockk<PreferenceRepository>(relaxed = true)
        coEvery { preferences.isBibleKitAdded() } returns false
        every { preferences.getBibleKitNotebookId() } returns flowOf(7L)

        BibleStudyKit.addOnce(context, notes, notebooks, preferences)

        coVerify { preferences.setBibleKitNotebookId(9L) }
        val inserted = chunks.flatten()
        assertEquals(1300, inserted.size)
        assertTrue(chunks.all { it.size <= BibleStudyKit.CHUNK })
        assertTrue(inserted.all { it.notebookId == 9L })
        coVerify(exactly = 0) { notes.insertNote(any(), any()) }
    }

    @Test
    fun notAddedAgain() = runTest {
        val notes = mockk<NoteRepository>()
        val notebooks = mockk<NotebookRepository>()
        val preferences = mockk<PreferenceRepository>()
        coEvery { preferences.isBibleKitAdded() } returns true
        BibleStudyKit.addOnce(mockk(), notes, notebooks, preferences)
        coVerify(exactly = 0) { notes.insertNote(any(), any()) }
        coVerify(exactly = 0) { notes.insertLocalNotes(any()) }
        coVerify(exactly = 0) { notebooks.insert(any()) }
    }
}
