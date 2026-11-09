package org.qosp.notes.tests

import android.net.Uri
import android.util.Base64
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.components.backup.AttachmentHandler
import org.qosp.notes.components.backup.BackupManager
import org.qosp.notes.components.importer.OtherAppsImport
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteTask
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.repo.TagRepository
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** The whole import on a device: picked file → notes, notebooks, tags and pictures in the app. */
class ImportOtherAppsTest : KoinComponent {
    private val notes: NoteRepository by inject()
    private val notebooks: NotebookRepository by inject()
    private val tags: TagRepository by inject()
    private val backupManager: BackupManager by inject()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val png = Base64.decode(
        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==", Base64.DEFAULT
    )

    @After
    fun cleanUp() = runBlocking {
        notes.deleteNotes(*notes.getAll().first().toTypedArray(), sync = false)
        notebooks.delete(*notebooks.getAll().first().toTypedArray())
        tags.delete(*tags.getAll().first().toTypedArray(), shouldSync = false)
    }

    private fun zip(name: String, vararg files: Pair<String, ByteArray>): Uri {
        val file = File(context.cacheDir, name)
        ZipOutputStream(file.outputStream()).use { out ->
            files.forEach { (path, bytes) ->
                out.putNextEntry(ZipEntry(path))
                out.write(bytes)
                out.closeEntry()
            }
        }
        return Uri.fromFile(file)
    }

    @Test
    fun notesFromAnotherAppAreAddedWithPicturesNotebooksAndTags() = runBlocking {
        val existing = notes.insertNote(Note(title = "Già qui", content = "non toccare"), sync = false)
        val export = zip(
            "vault.zip",
            "Vault/Scuola/Storia.md" to "---\ntags: [scuola]\n---\n# Storia\n\nTesto\n\n![[mappa.png]]\n".toByteArray(),
            "Vault/allegati/mappa.png" to png,
            "Vault/Spesa.txt" to "☐ Pane\n☑ Latte".toByteArray(),
        )

        val outcome = OtherAppsImport(context, backupManager).import(listOf(export))

        assertEquals(2, outcome.notes)
        val all = notes.getAll().first()
        assertEquals(3, all.size)
        assertEquals("non toccare", all.single { it.id == existing }.content)

        val history = all.single { it.title == "Storia" }
        assertEquals("Scuola", notebooks.getById(history.notebookId!!).first()?.name)
        assertEquals(listOf("scuola"), tags.getByNoteId(history.id).first().map { it.name })
        val picture = history.attachments.single()
        assertEquals(Attachment.Type.IMAGE, picture.type)
        assertEquals("Testo\n\n![mappa.png](${picture.path})", history.content)
        // the picture was really copied into the app
        context.contentResolver.openInputStream(Uri.parse(picture.path))!!.use { assertArrayEquals(png, it.readBytes()) }

        val shopping = all.single { it.title == "Spesa" }
        assertTrue(shopping.isList)
        assertEquals(listOf(NoteTask(0, "Pane", false), NoteTask(1, "Latte", true)), shopping.taskList)
    }

    @Test
    fun aBlackPearlBackupIsRestored() = runBlocking {
        notes.insertNote(Note(title = "Dal backup", content = "testo"), sync = false)
        val json = backupManager.createBackup(null, AttachmentHandler.KeepInfoOnly).serialize()
        val backup = zip("backup.zip", "backup.json" to json.toByteArray())

        val outcome = OtherAppsImport(context, backupManager).import(listOf(backup))

        assertEquals(1, outcome.notes)
        assertEquals(setOf("Black Pearl"), outcome.formats)
        assertEquals(2, notes.getAll().first().count { it.title == "Dal backup" })
    }

    @Test
    fun filesWithoutNotesImportNothing() = runBlocking {
        val photo = File(context.cacheDir, "foto.png").apply { writeBytes(png) }
        val outcome = OtherAppsImport(context, backupManager).import(listOf(Uri.fromFile(photo)))
        assertEquals(0, outcome.notes)
        assertTrue(notes.getAll().first().isEmpty())
    }
}
