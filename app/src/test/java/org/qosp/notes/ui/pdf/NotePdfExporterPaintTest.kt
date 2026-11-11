package org.qosp.notes.ui.pdf

import android.graphics.Color
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Links in the PDF (web links, [[wiki links]]) take the paint's linkColor: it must not stay transparent. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class NotePdfExporterPaintTest {

    @Test
    fun linksArePrintedInBlack() {
        val exporter = NotePdfExporter(RuntimeEnvironment.getApplication(), mockk(relaxed = true))
        assertEquals(Color.BLACK, exporter.bodyPaint.linkColor)
    }
}
