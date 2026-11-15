package org.qosp.notes.components.cards

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.LuminanceSource
import com.google.zxing.MultiFormatReader
import com.google.zxing.MultiFormatWriter
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.ReaderException
import com.google.zxing.common.BitMatrix
import com.google.zxing.common.HybridBinarizer

/** Reads barcodes out of pictures and draws them again (ZXing, fully offline). Pure JVM: no Android classes. */
object CardCodes {
    data class Scan(val code: String, val format: String)

    private val twoDimensional = setOf("QR_CODE", "AZTEC", "DATA_MATRIX", "PDF_417", "MAXICODE")

    fun isTwoDimensional(format: String) = format in twoDimensional

    /** Format to use for a number typed by hand: 13 digits are almost always a shop card in EAN-13. */
    fun formatForTyped(code: String) =
        if (code.length == 13 && code.all { it.isDigit() }) BarcodeFormat.EAN_13.name else BarcodeFormat.CODE_128.name

    /** Finds a barcode in an image given as ARGB pixels; also tries the inverted image (light bars on dark). */
    fun decode(pixels: IntArray, width: Int, height: Int): Scan? {
        val source: LuminanceSource = RGBLuminanceSource(width, height, pixels)
        val hints = mapOf(DecodeHintType.TRY_HARDER to true)
        for (candidate in listOf(source, source.invert())) {
            try {
                val result = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(candidate)), hints)
                return Scan(result.text, result.barcodeFormat.name)
            } catch (_: ReaderException) {
            }
        }
        return null
    }

    /**
     * Draws [code] in [format]. If that format cannot hold the text (wrong check digit, a format ZXing cannot
     * write) it falls back to Code 128, then to a QR code, so a saved card can always be shown.
     */
    fun encode(code: String, format: String, width: Int, height: Int): BitMatrix? {
        val wanted = runCatching { BarcodeFormat.valueOf(format) }.getOrDefault(BarcodeFormat.CODE_128)
        for (f in listOf(wanted, BarcodeFormat.CODE_128, BarcodeFormat.QR_CODE).distinct()) {
            try {
                return MultiFormatWriter().encode(code, f, width, height)
            } catch (_: Exception) {
            }
        }
        return null
    }

    /** Black on white ARGB pixels of [matrix], row by row. */
    fun toPixels(matrix: BitMatrix): IntArray {
        val w = matrix.width
        return IntArray(w * matrix.height) { i -> if (matrix.get(i % w, i / w)) BLACK else WHITE }
    }

    private const val BLACK = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()
}
