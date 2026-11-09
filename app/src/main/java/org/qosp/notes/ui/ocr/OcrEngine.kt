package org.qosp.notes.ui.ocr

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import com.googlecode.tesseract.android.TessBaseAPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Offline OCR with Tesseract (Black Pearl feature 3). Models (ita + eng, tessdata_fast) ship in
 * assets/tessdata and are copied once to the app's private files: no network, no external service.
 */
class OcrEngine(private val context: Context) {

    sealed interface Result {
        data class Text(val text: String) : Result
        data object Unreadable : Result
        data class Error(val cause: Throwable) : Result
    }

    suspend fun recognize(image: Uri, languages: String = "ita+eng"): Result = withContext(Dispatchers.Default) {
        runCatching {
            val bitmap = decode(image) ?: return@runCatching Result.Error(IllegalArgumentException("Cannot decode image"))
            val dataPath = installModels()
            val api = TessBaseAPI()
            try {
                check(api.init(dataPath.absolutePath, languages)) { "Tesseract init failed" }
                api.pageSegMode = TessBaseAPI.PageSegMode.PSM_AUTO
                api.setImage(bitmap)
                val text = OcrText.clean(api.utF8Text.orEmpty())
                if (OcrText.isUnreadable(text)) Result.Unreadable else Result.Text(text)
            } finally {
                api.recycle()
                bitmap.recycle()
            }
        }.getOrElse { Result.Error(it) }
    }

    /** Copies bundled models to files/tesseract/tessdata (once per app version size change). */
    private fun installModels(): File {
        val root = File(context.filesDir, "tesseract")
        val dir = File(root, "tessdata").apply { mkdirs() }
        context.assets.list("tessdata").orEmpty().filter { it.endsWith(".traineddata") }.forEach { name ->
            val target = File(dir, name)
            val size = runCatching { context.assets.openFd("tessdata/$name").use { it.length } }.getOrNull()?.takeIf { it > 0 }
            if (!target.exists() || (size != null && target.length() != size)) {
                val tmp = File(dir, "$name.tmp")
                context.assets.open("tessdata/$name").use { input -> tmp.outputStream().use { input.copyTo(it) } }
                tmp.renameTo(target)
            }
        }
        return root
    }

    /** Decodes the image downsampled to at most [maxSide] px and rotated upright using EXIF. */
    private fun decode(uri: Uri, maxSide: Int = 2400): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val bitmap = resolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        } ?: return null
        val rotation = runCatching {
            resolver.openInputStream(uri)?.use {
                when (ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                    ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                    ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                    ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                    else -> 0f
                }
            }
        }.getOrNull() ?: 0f
        if (rotation == 0f) return bitmap
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, Matrix().apply { postRotate(rotation) }, true)
            .also { if (it !== bitmap) bitmap.recycle() }
    }
}
