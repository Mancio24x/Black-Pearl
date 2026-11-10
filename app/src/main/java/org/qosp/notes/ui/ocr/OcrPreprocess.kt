package org.qosp.notes.ui.ocr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint

/**
 * Makes a phone photo readable for Tesseract, which only does a single global threshold on its own: a page shot
 * under a lamp has a bright side and a dark side, and half the words come out blank.
 * Steps: grey, upscale small images, local (adaptive) threshold to clean black on white, white-on-dark inverted.
 */
object OcrPreprocess {

    /** Returns a new black-and-white bitmap; the input is left untouched. */
    fun binarize(source: Bitmap): Bitmap {
        val scaled = upscaleIfSmall(source)
        val w = scaled.width
        val h = scaled.height
        val pixels = IntArray(w * h)
        scaled.getPixels(pixels, 0, w, 0, 0, w, h)
        if (scaled !== source) scaled.recycle()

        val grey = IntArray(w * h) { i ->
            val p = pixels[i]
            (((p shr 16) and 0xFF) * 299 + ((p shr 8) and 0xFF) * 587 + (p and 0xFF) * 114) / 1000
        }
        // integral image for fast local means
        val integral = LongArray((w + 1) * (h + 1))
        for (y in 1..h) {
            var row = 0L
            val base = y * (w + 1)
            val prev = (y - 1) * (w + 1)
            for (x in 1..w) {
                row += grey[(y - 1) * w + (x - 1)]
                integral[base + x] = integral[prev + x] + row
            }
        }
        // window about a few letters wide: adapts to shadows, keeps strokes
        val half = (maxOf(w, h) / 40).coerceIn(12, 60)
        var black = 0
        for (y in 0 until h) {
            val y0 = (y - half).coerceAtLeast(0)
            val y1 = (y + half + 1).coerceAtMost(h)
            for (x in 0 until w) {
                val x0 = (x - half).coerceAtLeast(0)
                val x1 = (x + half + 1).coerceAtMost(w)
                val area = (x1 - x0) * (y1 - y0)
                val sum = integral[y1 * (w + 1) + x1] - integral[y0 * (w + 1) + x1] -
                    integral[y1 * (w + 1) + x0] + integral[y0 * (w + 1) + x0]
                val mean = sum / area
                // a pixel is ink when clearly darker than its surroundings (Bradley-Roth style)
                val ink = grey[y * w + x] < mean * (100 - CONTRAST_PERCENT) / 100
                if (ink) black++
                pixels[y * w + x] = if (ink) BLACK else WHITE
            }
        }
        // white text on a dark background comes out as mostly black: flip it
        if (black > w * h / 2) for (i in pixels.indices) pixels[i] = if (pixels[i] == BLACK) WHITE else BLACK
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).also { it.setPixels(pixels, 0, w, 0, 0, w, h) }
    }

    /** Tesseract wants letters at least ~20 px tall: small pictures (screenshots, thumbnails) are doubled. */
    private fun upscaleIfSmall(source: Bitmap): Bitmap {
        if (minOf(source.width, source.height) >= MIN_SIDE) return source
        val scale = 2f
        val out = Bitmap.createBitmap((source.width * scale).toInt(), (source.height * scale).toInt(), Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(source, Matrix().apply { setScale(scale, scale) }, Paint(Paint.FILTER_BITMAP_FLAG))
        return out
    }

    private const val CONTRAST_PERCENT = 12
    private const val MIN_SIDE = 1000
    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
