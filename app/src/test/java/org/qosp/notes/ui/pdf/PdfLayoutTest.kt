package org.qosp.notes.ui.pdf

import org.junit.Assert.assertEquals
import org.junit.Test
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteTask
import org.qosp.notes.ui.editor.markdown.ImageBlocks

class PdfLayoutTest {

    @Test
    fun paginatorBreaksPagesAndKeepsOversizedItems() {
        val p = Paginator(pageHeight = 100f)
        assertEquals(Paginator.Slot(0, 0f), p.place(40f))
        assertEquals(Paginator.Slot(0, 45f), p.place(40f, spaceBefore = 5f))
        assertEquals(Paginator.Slot(1, 0f), p.place(40f, spaceBefore = 5f)) // 90 + 40 > 100
        p.newPage()
        assertEquals(Paginator.Slot(2, 0f), p.place(250f)) // taller than a page: stays alone
        assertEquals(Paginator.Slot(3, 0f), p.place(10f))
    }

    @Test
    fun longNoteNeedsManyPages() {
        val p = Paginator(pageHeight = 746f)
        repeat(300) { p.place(16f) }
        assertEquals(6, p.page) // 46 lines per page -> 7 pages (0..6)
    }

    @Test
    fun blocksKeepTextOrderAndImagePositions() {
        val note = Note(
            content = "Intro\nline 2\n![boat](img_1.jpg \"=center,50\")\nAfter",
            attachments = listOf(
                Attachment(Attachment.Type.IMAGE, "img_1.jpg", "boat.jpg"),
                Attachment(Attachment.Type.IMAGE, "img_2.jpg", "extra.jpg"),
                Attachment(Attachment.Type.AUDIO, "a.mp3", "voice"),
            ),
        )
        assertEquals(
            listOf(
                PdfBlock.Text("Intro\nline 2"),
                PdfBlock.Image("img_1.jpg", "boat", ImageBlocks.Layout(ImageBlocks.Align.CENTER, 50)),
                PdfBlock.Text("After"),
                PdfBlock.Image("img_2.jpg", "extra.jpg", ImageBlocks.Layout(ImageBlocks.Align.CENTER)),
            ),
            PdfBlocks.of(note)
        )
    }

    @Test
    fun checklistBecomesTasks() {
        val note = Note(isList = true, taskList = listOf(NoteTask(1, "milk", true), NoteTask(2, "bread", false)))
        assertEquals(listOf(PdfBlock.Task("milk", true), PdfBlock.Task("bread", false)), PdfBlocks.of(note))
    }
}
