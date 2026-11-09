package org.qosp.notes.tests

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.components.backup.AttachmentHandler
import org.qosp.notes.components.backup.BackupManager
import org.qosp.notes.data.Backup
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteColor
import org.qosp.notes.data.model.NoteTask
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.ui.editor.markdown.ImageBlocks

/**
 * Note life cycle on the real (in-memory) Room database: create, edit, checklist, attachments, image layout,
 * color, bin/restore, permanent delete, empty-note discard, backup and restore.
 */
class NoteLifecycleTest : KoinComponent {

    private val repo: NoteRepository by inject()
    private val backupManager: BackupManager by inject()

    @After
    fun cleanUp() = runBlocking {
        repo.deleteNotes(*repo.getAll().first().toTypedArray(), sync = false)
    }

    private fun sample(): Note {
        var content = "Primo paragrafo\n\nSecondo paragrafo"
        content = ImageBlocks.insert(content, "Primo paragrafo".length, "img_1.jpg", "barca").first
        content = ImageBlocks.setLayout(content, ImageBlocks.Ref("img_1.jpg"), ImageBlocks.Layout(ImageBlocks.Align.CENTER, 50))
        return Note(
            title = "Viaggio",
            content = content,
            color = NoteColor.Teal,
            attachments = listOf(Attachment(Attachment.Type.IMAGE, "img_1.jpg", "barca.jpg")),
        )
    }

    @Test
    fun createAndReadBackEverything() = runBlocking {
        val id = repo.insertNote(sample(), sync = false)
        val saved = repo.getById(id).first()
        assertNotNull(saved)
        saved!!
        assertEquals("Viaggio", saved.title)
        assertEquals(sample().content, saved.content)
        assertEquals(NoteColor.Teal, saved.color)
        assertEquals(sample().attachments, saved.attachments)
        assertEquals(ImageBlocks.Layout(ImageBlocks.Align.CENTER, 50), ImageBlocks.blocks(saved.content).single().layout)
    }

    @Test
    fun editKeepsOtherFields() = runBlocking {
        val id = repo.insertNote(sample(), sync = false)
        val note = repo.getById(id).first()!!
        val moved = ImageBlocks.move(note.content, ImageBlocks.Ref("img_1.jpg"), up = false)
        repo.updateNotes(note.copy(title = "Viaggio 2", content = moved, color = NoteColor.Default), sync = false)
        val edited = repo.getById(id).first()!!
        assertEquals("Viaggio 2", edited.title)
        assertEquals(moved, edited.content)
        assertEquals(NoteColor.Default, edited.color)
        assertEquals(note.attachments, edited.attachments)
        assertEquals(note.creationDate, edited.creationDate)
    }

    @Test
    fun checklistPersists() = runBlocking {
        val tasks = listOf(NoteTask(0, "latte", true), NoteTask(1, "pane", false))
        val id = repo.insertNote(Note(title = "Spesa", isList = true, taskList = tasks), sync = false)
        assertEquals(tasks, repo.getById(id).first()!!.taskList)
        val toggled = tasks.map { it.copy(isDone = !it.isDone) }
        repo.updateNotes(repo.getById(id).first()!!.copy(taskList = toggled), sync = false)
        assertEquals(toggled, repo.getById(id).first()!!.taskList)
    }

    @Test
    fun binRestoreAndPermanentDelete() = runBlocking {
        val id = repo.insertNote(sample(), sync = false)
        repo.moveNotesToBin(repo.getById(id).first()!!, sync = false)
        assertTrue(repo.getDeleted().first().any { it.id == id })
        assertFalse(repo.getNonDeleted().first().any { it.id == id })

        repo.restoreNotes(repo.getById(id).first()!!)
        assertFalse(repo.getById(id).first()!!.isDeleted)

        repo.deleteNotes(repo.getById(id).first()!!, sync = false)
        assertNull(repo.getById(id).first())
    }

    @Test
    fun emptyNotesAreDiscardedButNotOthers() = runBlocking {
        val empty = repo.insertNote(Note(), sync = false)
        val full = repo.insertNote(sample(), sync = false)
        repo.discardEmptyNotes()
        assertNull(repo.getById(empty).first())
        assertNotNull(repo.getById(full).first())
    }

    @Test
    fun backupAndRestoreKeepContentColorsAndImages() = runBlocking {
        val id = repo.insertNote(sample(), sync = false)
        val list = repo.insertNote(Note(title = "Lista", isList = true, taskList = listOf(NoteTask(0, "x", true)), color = NoteColor.Red), sync = false)
        val json = backupManager.createBackup(null, AttachmentHandler.KeepInfoOnly).serialize()

        repo.deleteNotes(*repo.getAll().first().toTypedArray(), sync = false)
        assertTrue(repo.getAll().first().isEmpty())

        backupManager.restoreNotesFromBackup(Backup.fromString(json))
        val restored = repo.getAll().first()
        assertEquals(2, restored.size)
        val note = restored.single { it.title == "Viaggio" }
        assertEquals(sample().content, note.content)
        assertEquals(NoteColor.Teal, note.color)
        assertEquals(sample().attachments, note.attachments)
        val restoredList = restored.single { it.title == "Lista" }
        assertEquals(NoteColor.Red, restoredList.color)
        assertEquals(listOf(NoteTask(0, "x", true)), restoredList.taskList)
        assertTrue(id > 0 && list > 0)
    }
}
