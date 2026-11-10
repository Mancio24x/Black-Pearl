package org.qosp.notes.tests

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteTask
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.ui.widget.ChecklistWidgetFactory
import org.qosp.notes.ui.widget.ChecklistWidgets

/** Checklist widget: shows the chosen list's items and ticks them in the real database. */
class ChecklistWidgetTest : KoinComponent {
    private val notes: NoteRepository by inject()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val widgetId = 4242
    private var noteId = 0L

    @After
    fun cleanUp() = runBlocking {
        ChecklistWidgets.forget(context, intArrayOf(widgetId))
        notes.getById(noteId).first()?.let { notes.deleteNotes(it, sync = false) }
    }

    @Test
    fun showsTheChosenListAndTicksItems() = runBlocking {
        noteId = notes.insertNote(
            Note(title = "Spesa", isList = true, taskList = listOf(NoteTask(0, "latte", false), NoteTask(1, "pane", true))),
            sync = false,
        )
        ChecklistWidgets.setNoteId(context, widgetId, noteId)
        assertEquals(noteId, ChecklistWidgets.noteId(context, widgetId))

        val factory = ChecklistWidgetFactory(context, widgetId)
        factory.onDataSetChanged()
        assertEquals(2, factory.count)

        assertTrue(ChecklistWidgets.toggle(notes, noteId, taskId = 0))
        assertEquals(listOf(true, true), notes.getById(noteId).first()!!.taskList.map { it.isDone })
        assertTrue(ChecklistWidgets.toggle(notes, noteId, taskId = 1))
        assertEquals(listOf(true, false), notes.getById(noteId).first()!!.taskList.map { it.isDone })
    }

    @Test
    fun aDeletedListShowsNothing() = runBlocking {
        noteId = notes.insertNote(Note(title = "Vecchia", isList = true, taskList = listOf(NoteTask(0, "x", false))), sync = false)
        ChecklistWidgets.setNoteId(context, widgetId, noteId)
        notes.deleteNotes(notes.getById(noteId).first()!!, sync = false)
        val factory = ChecklistWidgetFactory(context, widgetId)
        factory.onDataSetChanged()
        assertEquals(0, factory.count)
    }
}
