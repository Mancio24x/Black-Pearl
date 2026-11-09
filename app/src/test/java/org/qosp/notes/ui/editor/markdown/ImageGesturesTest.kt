package org.qosp.notes.ui.editor.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageGesturesTest {

    private val img = ImageBlocks.Ref("img.jpg")
    private val source = "Primo\n\nSecondo\nterzo\n![foto](img.jpg)\n\n- uno\n- due"

    @Test
    fun unitsAreTheNonBlankLines() {
        val rendered = "Primo\n\nSecondo\nterzo\n\uFFFC\n\nuno\ndue"
        assertEquals(listOf("Primo", "Secondo", "terzo", "\uFFFC", "uno", "due"), ImageGestures.units(rendered).map { rendered.substring(it.first, it.last + 1) })
        assertEquals(emptyList<IntRange>(), ImageGestures.units("\n \n"))
    }

    @Test
    fun dropPositionsBecomeSteps() {
        // picture is unit 3 (Primo, Secondo, terzo, picture, uno, due)
        assertEquals(0, ImageGestures.steps(3, 3)) // right above itself
        assertEquals(0, ImageGestures.steps(3, 4)) // right below itself
        assertEquals(-3, ImageGestures.steps(3, 0)) // to the top
        assertEquals(-1, ImageGestures.steps(3, 2))
        assertEquals(1, ImageGestures.steps(3, 5)) // between "uno" and "due"
        assertEquals(2, ImageGestures.steps(3, 6)) // to the end
    }

    @Test
    fun movingBySeveralStepsMatchesTheDropPosition() {
        assertEquals("![foto](img.jpg)\nPrimo\n\nSecondo\nterzo\n\n- uno\n- due", ImageGestures.moveBy(source, img, -3))
        assertEquals("Primo\n\nSecondo\nterzo\n\n- uno\n![foto](img.jpg)\n- due", ImageGestures.moveBy(source, img, 1))
        assertEquals("Primo\n\nSecondo\nterzo\n\n- uno\n- due\n![foto](img.jpg)", ImageGestures.moveBy(source, img, 2))
        // past the end: stops at the last position
        assertEquals(ImageGestures.moveBy(source, img, 2), ImageGestures.moveBy(source, img, 9))
        assertEquals(source, ImageGestures.moveBy(source, img, 0))
    }

    @Test
    fun widthSnapsToCommonSizes() {
        assertEquals(50, ImageGestures.snapPercent(47.5f))
        assertEquals(25, ImageGestures.snapPercent(28f))
        assertEquals(100, ImageGestures.snapPercent(130f))
        assertEquals(10, ImageGestures.snapPercent(2f))
        assertEquals(60, ImageGestures.snapPercent(61f))
        assertEquals(40, ImageGestures.snapPercent(38f))
    }
}
