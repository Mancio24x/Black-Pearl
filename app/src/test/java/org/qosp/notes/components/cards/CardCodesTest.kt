package org.qosp.notes.components.cards

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CardCodesTest {
    /** Draws a code and reads it back from the drawn pixels. */
    private fun roundTrip(code: String, format: String, w: Int, h: Int): CardCodes.Scan? {
        val matrix = CardCodes.encode(code, format, w, h)!!
        return CardCodes.decode(CardCodes.toPixels(matrix), matrix.width, matrix.height)
    }

    @Test fun ean13IsDrawnAndReadBack() {
        assertEquals(CardCodes.Scan("4006381333931", "EAN_13"), roundTrip("4006381333931", "EAN_13", 900, 300))
    }

    @Test fun code128IsDrawnAndReadBack() {
        assertEquals(CardCodes.Scan("ABC-12345", "CODE_128"), roundTrip("ABC-12345", "CODE_128", 900, 300))
    }

    @Test fun qrIsDrawnAndReadBack() {
        assertEquals(CardCodes.Scan("https://example.com/c/42", "QR_CODE"), roundTrip("https://example.com/c/42", "QR_CODE", 600, 600))
    }

    @Test fun invertedPictureIsRead() {
        val matrix = CardCodes.encode("ABC-12345", "CODE_128", 900, 300)!!
        val inverted = CardCodes.toPixels(matrix).map { it xor 0x00FFFFFF }.toIntArray()
        assertEquals("ABC-12345", CardCodes.decode(inverted, matrix.width, matrix.height)?.code)
    }

    @Test fun pictureWithoutCodeGivesNull() {
        assertNull(CardCodes.decode(IntArray(200 * 200) { 0xFFFFFFFF.toInt() }, 200, 200))
    }

    @Test fun typedNumberPicksFormat() {
        assertEquals("EAN_13", CardCodes.formatForTyped("4006381333931"))
        assertEquals("CODE_128", CardCodes.formatForTyped("12345"))
        assertEquals("CODE_128", CardCodes.formatForTyped("AB12345678901"))
    }

    @Test fun wrongCheckDigitOrUnknownFormatStillDraws() {
        // 13 digits with a wrong check digit cannot be EAN-13: shown as Code 128 with the same digits.
        assertEquals(CardCodes.Scan("1234567890123", "CODE_128"), roundTrip("1234567890123", "EAN_13", 900, 300))
        assertNotNull(CardCodes.encode("12345", "RSS_14", 900, 300))
        assertNotNull(CardCodes.encode("12345", "not a format", 900, 300))
    }

    @Test fun textCode128CannotHoldBecomesQr() {
        assertEquals(CardCodes.Scan("tessera è", "QR_CODE"), roundTrip("tessera è", "CODE_128", 600, 600))
    }
}
