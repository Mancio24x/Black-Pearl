package org.qosp.notes.components

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.components.importer.ImportEntry
import org.qosp.notes.data.AppDatabase
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.SortMethod

class BibleNotebookUpgradeTest {
    @Test
    fun mergesOnlyVerifiedNotebooksAndKeepsEditsAndHomeStable() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        try {
            val main = db.notebookDao.insert(Notebook(BibleStudyKit.NOTEBOOK))
            val legacy = db.notebookDao.insert(Notebook("Scripture (WEB)"))
            val privateBook = db.notebookDao.insert(Notebook("My Bible journal"))
            val prefs = mockk<PreferenceRepository>(relaxed = true)
            every { prefs.getBibleKitNotebookId() } returns flowOf(main)
            coEvery { prefs.isBibleKitReconciled() } returns true
            val kit = BibleNotebook(context, db, prefs)
            val roots = BibleStudyKit.files(context.assets)
            fun file(title: String): ImportEntry {
                val path = roots.single { BibleStudyKit.titleOf(it) == title }
                return ImportEntry(path) { context.assets.open("${BibleStudyKit.DIR}/$path") }
            }
            suspend fun add(title: String, book: Long?) =
                db.noteDao.insert(BibleStudyKit.noteFrom(file(title).path, file(title).readText(), book).toEntity())
            add("Gen-01", main)
            add("Gen-01", legacy)
            add("Genesis", legacy)
            val loose = add("Gen-02", null)
            val edited = db.noteDao.insert(Note(title = "My annotations", content = "Private marginalia", notebookId = legacy).toEntity())
            val mine = db.noteDao.insert(Note(title = "Genesis", content = "My own study", notebookId = privateBook).toEntity())
            val personal = db.noteDao.insert(Note(title = "Shopping").toEntity())
            assertEquals(main, kit.reconcile(99999))
            assertEquals(main, kit.reconcile(main))
            assertNull(db.notebookDao.getById(legacy).first())
            assertNotNull(db.notebookDao.getById(privateBook).first())
            assertEquals(main, db.noteDao.getById(loose).first()!!.notebookId)
            assertEquals("Private marginalia", db.noteDao.getById(edited).first()!!.content)
            assertEquals(main, db.noteDao.getById(edited).first()!!.notebookId)
            assertEquals(setOf(mine, personal), db.noteDao.getNonDeletedOrArchivedExcept(main, SortMethod.TITLE_ASC).first().map { it.id }.toSet())
            assertEquals(1, kit.inReadingOrder(db.noteDao.getByNotebook(main, SortMethod.TITLE_ASC).first()).count { it.title == "Gen-01" })

            val partial = listOf(file(BibleStudyKit.START), file("The Bible (WEB)"), file("Genesis"), file("Gen-01"))
            val report = kit.importArchive(partial)!!.first
            assertEquals(1, report.books)
            assertEquals(65, report.missing.size)
            assertEquals(0, kit.importArchive(partial)!!.first.added)
            val chapter = file("Gen-01")
            val annotation = ImportEntry(chapter.path) { (chapter.readText() + "\nMy added verse note").byteInputStream() }
            val annotated = partial.dropLast(1) + annotation
            assertEquals(1, kit.importArchive(annotated)!!.first.added)
            assertEquals(0, kit.importArchive(annotated)!!.first.added)
            assertTrue(db.noteDao.getByNotebook(main, SortMethod.TITLE_ASC).first().any { "My added verse note" in it.content })
        } finally {
            db.close()
        }
    }
}
