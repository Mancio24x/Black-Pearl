package org.qosp.notes.ui.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrTextTest {

    @Test
    fun keepsLinesAndParagraphs() {
        val raw = "Titolo  \r\nprima riga\nseconda riga   \n\n\n\nNuovo paragrafo\n"
        assertEquals("Titolo\nprima riga\nseconda riga\n\nNuovo paragrafo", OcrText.clean(raw))
    }

    @Test
    fun joinsWordsHyphenatedAtLineEnd() {
        assertEquals("una parola spezzata qui", OcrText.clean("una parola spez-\nzata qui"))
        // a real hyphen before a capitalized word is kept
        assertEquals("Emilia-\nRomagna", OcrText.clean("Emilia-\nRomagna"))
    }

    @Test
    fun accentsAreKept() {
        assertEquals("perché città è", OcrText.clean("perché città è"))
    }

    @Test
    fun unreadableDetection() {
        assertTrue(OcrText.isUnreadable(""))
        assertTrue(OcrText.isUnreadable(" ~ - . "))
        assertFalse(OcrText.isUnreadable("Ok"))
    }

    @Test
    fun insertAsParagraphKeepsExistingText() {
        assertEquals("Prima\n\nOCR\n\ndopo" to "Prima\n\nOCR".length, OcrText.insertAsParagraph("Primadopo", 5, "OCR"))
        assertEquals("OCR" to 3, OcrText.insertAsParagraph("", 0, "OCR"))
        assertEquals("a\n\nOCR", OcrText.insertAsParagraph("a\n", 2, "OCR").first)
    }
}
