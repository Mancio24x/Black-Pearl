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

    @Test
    fun copiesOfTheKitOutsideItsNotebookAreRemovedButChangedOnesStay() = runTest {
        val context = mockk<Context>().also { every { it.assets } returns assets() }
        val gen = File(root, "Scripture (WEB)/01 - Genesis/Gen-01.md").readText()
        val john = File(root, "Scripture (WEB)/43 - John/John-03.md").readText()
        val copy = BibleStudyKit.noteFrom("Gen-01.md", gen, null).copy(id = 11)
        val changed = BibleStudyKit.noteFrom("John-03.md", john + "\nmy note", null).copy(id = 12)
        val withPhoto = BibleStudyKit.noteFrom("x/John-03.md", john, null)
            .copy(id = 13, attachments = listOf(org.qosp.notes.data.model.Attachment(path = "p")))
        val mine = Note(title = "Shopping", content = "milk", id = 14)
        val notes = mockk<NoteRepository>(relaxed = true)
        coEvery { notes.getTitlesIn(7L) } returns setOf("Gen-01", "John-03")
        coEvery { notes.getTitlesIn(8L) } returns emptySet()
        coEvery { notes.getIdTitlesOutside(7L) } returns listOf(copy, changed, withPhoto, mine)
            .map { org.qosp.notes.data.model.NoteIdTitle(it.id, it.title) }
        for (n in listOf(copy, changed, withPhoto, mine)) every { notes.getById(n.id) } returns flowOf(n)
        val notebooks = mockk<NotebookRepository>(relaxed = true)
        every { notebooks.getById(7L) } returns flowOf(Notebook(BibleStudyKit.NOTEBOOK, 7L))
        val extra = Notebook(BibleStudyKit.NOTEBOOK, 8L)
        every { notebooks.getAll() } returns flowOf(listOf(Notebook(BibleStudyKit.NOTEBOOK, 7L), extra, Notebook("Mine", 9L)))
        val preferences = mockk<PreferenceRepository>()
        coEvery { preferences.isBibleKitAdded() } returns true
        every { preferences.getBibleKitNotebookId() } returns flowOf(7L)

        assertEquals(1, BibleStudyKit.removeCopies(context, notes, notebooks, preferences))

        coVerify(exactly = 1) { notes.deleteNotes(copy) }
        coVerify(exactly = 1) { notes.deleteNotes(any()) }
        coVerify(exactly = 1) { notebooks.delete(extra) }
        coVerify(exactly = 1) { notebooks.delete(any()) }
    }

    @Test
    fun nothingIsRemovedBeforeTheKitIsAdded() = runTest {
        val notes = mockk<NoteRepository>(relaxed = true)
        val preferences = mockk<PreferenceRepository>()
        coEvery { preferences.isBibleKitAdded() } returns false
        assertEquals(0, BibleStudyKit.removeCopies(mockk(), notes, mockk(), preferences))
        coVerify(exactly = 0) { notes.deleteNotes(any()) }
    }
}
