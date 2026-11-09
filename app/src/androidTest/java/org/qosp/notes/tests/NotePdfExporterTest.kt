package org.qosp.notes.tests

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import io.noties.markwon.Markwon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteTask
import org.qosp.notes.ui.pdf.NotePdfExporter
import java.io.File

class NotePdfExporterTest : KoinComponent {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val markwon: Markwon by inject()

    private fun export(note: Note): Pair<Int, File> {
        val file = File(context.cacheDir, "test_${note.title}.pdf")
        val pages = file.outputStream().use { NotePdfExporter(context, markwon).write(note, it) }
        return pages to file
    }

    private fun inkOnFirstPage(file: File): Int {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
            PdfRenderer(fd).use { renderer ->
                renderer.openPage(0).use { page ->
                    assertEquals(NotePdfExporter.PAGE_WIDTH, page.width)
                    assertEquals(NotePdfExporter.PAGE_HEIGHT, page.height)
                    val bmp = Bitmap.createBitmap(page.width, page.height, Bitmap.Config.ARGB_8888)
                    bmp.eraseColor(Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    val px = IntArray(bmp.width * bmp.height).also { bmp.getPixels(it, 0, bmp.width, 0, 0, bmp.width, bmp.height) }
                    return px.count { Color.red(it) < 128 }
                }
            }
        }
    }

    @Test
    fun longMarkdownNoteIsPaginatedA4() {
        val body = (1..200).joinToString("\n") { "Paragrafo **$it** con testo _formattato_ e ~~barrato~~." }
        val (pages, file) = export(Note(title = "Lunga", content = "# Titolo\n- uno\n- due\n\n$body"))
        assertTrue("pages=$pages", pages >= 3)
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { PdfRenderer(it).use { r -> assertEquals(pages, r.pageCount) } }
        assertTrue(inkOnFirstPage(file) > 500)
    }

    @Test
    fun checklistAndMissingImageExport() {
        val note = Note(
            title = "Lista",
            isList = true,
            taskList = listOf(NoteTask(1, "latte", true), NoteTask(2, "pane", false)),
        )
        assertEquals(1, export(note).first)
        val withMissingImage = Note(title = "Img", content = "Prima\n![foto](missing.jpg \"=center,50\")\nDopo")
        val (pages, file) = export(withMissingImage)
        assertEquals(1, pages)
        assertTrue(inkOnFirstPage(file) > 50)
    }

    @Test
    fun emptyNoteGivesOneBlankPage() {
        assertEquals(1, export(Note(title = "", content = "")).first)
    }
}
