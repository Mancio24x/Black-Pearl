package org.qosp.notes.tests

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.ui.ocr.OcrEngine
import java.io.File

/** Runs the real Tesseract engine offline on rendered text (Italian + English). */
class OcrEngineTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun image(lines: List<String>, name: String): Uri {
        val bitmap = Bitmap.createBitmap(1400, 120 + lines.size * 90, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 56f }
        lines.forEachIndexed { i, line -> canvas.drawText(line, 60f, 110f + i * 90, paint) }
        val file = File(context.cacheDir, name)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return Uri.fromFile(file)
    }

    @Test
    fun recognizesMultilineItalianAndEnglish() = runBlocking {
        val uri = image(listOf("La città è bella perché", "il mare è vicino.", "Hello world"), "ocr_test.png")
        val result = OcrEngine(context).recognize(uri)
        assertTrue("unexpected $result", result is OcrEngine.Result.Text)
        val text = (result as OcrEngine.Result.Text).text
        assertTrue(text, text.contains("città") && text.contains("perché"))
        assertTrue(text, text.contains("Hello world"))
        assertEquals(text, 3, text.lines().count { it.isNotBlank() })
    }

    @Test
    fun blankImageIsUnreadable() = runBlocking {
        val result = OcrEngine(context).recognize(image(emptyList(), "ocr_blank.png"))
        assertEquals(OcrEngine.Result.Unreadable, result)
    }

    @Test
    fun invalidImageIsAnError() = runBlocking {
        val file = File(context.cacheDir, "not_an_image.png").apply { writeText("nope") }
        assertTrue(OcrEngine(context).recognize(Uri.fromFile(file)) is OcrEngine.Result.Error)
    }
}
