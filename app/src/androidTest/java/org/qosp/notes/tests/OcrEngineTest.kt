package org.qosp.notes.tests

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Shader
import kotlin.random.Random
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

    /** A page photographed with the phone: lamp on one side, slight tilt, sensor noise, JPEG. */
    private fun photo(lines: List<String>, name: String): Uri {
        val w = 2600
        val h = 1900
        val page = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(page).apply { drawColor(Color.rgb(236, 232, 222)) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.rgb(40, 40, 48); textSize = 64f }
        canvas.save()
        canvas.rotate(2.5f, w / 2f, h / 2f)
        lines.forEachIndexed { i, line -> canvas.drawText(line, 180f, 300f + i * 110, paint) }
        canvas.restore()
        // light falling off from the top left to a dark bottom right corner
        canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), Paint().apply {
            shader = LinearGradient(0f, 0f, w.toFloat(), h.toFloat(), Color.WHITE, Color.rgb(90, 90, 90), Shader.TileMode.CLAMP)
            xfermode = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
        })
        val pixels = IntArray(w * h).also { page.getPixels(it, 0, w, 0, 0, w, h) }
        val random = Random(7)
        for (i in pixels.indices) {
            val n = (random.nextFloat() * 36 - 18).toInt()
            val p = pixels[i]
            fun c(v: Int) = (v + n).coerceIn(0, 255)
            pixels[i] = Color.rgb(c((p shr 16) and 0xFF), c((p shr 8) and 0xFF), c(p and 0xFF))
        }
        page.setPixels(pixels, 0, w, 0, 0, w, h)
        val file = File(context.cacheDir, name)
        file.outputStream().use { page.compress(Bitmap.CompressFormat.JPEG, 70, it) }
        return Uri.fromFile(file)
    }

    @Test
    fun readsAPhotoOfAPageWithShadowsAndNoise() = runBlocking {
        val uri = photo(
            listOf(
                "Lista della spesa per domenica:",
                "pane, latte, caffè e zucchero.",
                "Ricordarsi di chiamare Anna alle 15.",
                "The meeting starts at nine thirty.",
            ),
            "ocr_photo.jpg",
        )
        val result = OcrEngine(context).recognize(uri)
        assertTrue("unexpected $result", result is OcrEngine.Result.Text)
        val text = (result as OcrEngine.Result.Text).text.lowercase()
        for (word in listOf("spesa", "domenica", "latte", "zucchero", "chiamare", "anna", "meeting", "thirty")) {
            assertTrue("missing «$word» in:\n$text", text.contains(word))
        }
    }

    @Test
    fun readsWhiteTextOnADarkSign() = runBlocking {
        val bitmap = Bitmap.createBitmap(1600, 500, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.rgb(25, 30, 40)) }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textSize = 90f }
        canvas.drawText("USCITA DI EMERGENZA", 120f, 200f, paint)
        canvas.drawText("Emergency exit", 120f, 360f, paint)
        val file = File(context.cacheDir, "ocr_sign.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val result = OcrEngine(context).recognize(Uri.fromFile(file))
        assertTrue("unexpected $result", result is OcrEngine.Result.Text)
        val text = (result as OcrEngine.Result.Text).text.lowercase()
        assertTrue(text, text.contains("emergenza") && text.contains("emergency exit"))
    }
}
