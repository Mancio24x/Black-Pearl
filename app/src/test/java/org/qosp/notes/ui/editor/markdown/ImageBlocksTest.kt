package org.qosp.notes.ui.editor.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.ui.editor.markdown.ImageBlocks.Align
import org.qosp.notes.ui.editor.markdown.ImageBlocks.Layout
import org.qosp.notes.ui.editor.markdown.ImageBlocks.Ref

class ImageBlocksTest {

    private val img = "![](img_1.jpg)"

    @Test
    fun insertPutsImageOnItsOwnLineWithoutTouchingText() {
        val (out, cursor) = ImageBlocks.insert("Hello world", 5, "img_1.jpg")
        assertEquals("Hello\n$img\n world", out)
        assertEquals("Hello\n$img\n".length, cursor)
    }

    @Test
    fun insertAtLineBoundariesAddsNoExtraBlankLines() {
        assertEquals("a\n$img\nb", ImageBlocks.insert("a\nb", 2, "img_1.jpg").first)
        assertEquals("$img\n", ImageBlocks.insert("", 0, "img_1.jpg").first)
        assertEquals("a\n$img\n", ImageBlocks.insert("a", 99, "img_1.jpg").first)
    }

    @Test
    fun layoutRoundTrip() {
        val content = "p1\n$img\np2"
        val out = ImageBlocks.setLayout(content, Ref("img_1.jpg"), Layout(Align.CENTER, 60))
        assertEquals("p1\n![](img_1.jpg \"=center,60\")\np2", out)
        assertEquals(Layout(Align.CENTER, 60), ImageBlocks.blocks(out).single().layout)
        // back to default layout removes the title
        assertEquals(content, ImageBlocks.setLayout(out, Ref("img_1.jpg"), Layout()))
    }

    @Test
    fun customTitleIsKeptWhenLayoutIsDefault() {
        val content = "![cat](a.png \"my cat\")"
        assertEquals(content, ImageBlocks.setLayout(content, Ref("a.png"), Layout()))
        assertEquals(Layout(), ImageBlocks.blocks(content).single().layout)
    }

    @Test
    fun widthIsClamped() {
        assertEquals(100, ImageBlocks.parseLayout("=right,500").widthPercent)
        assertEquals(10, ImageBlocks.parseLayout("=right,1").widthPercent)
        assertEquals(Align.RIGHT, ImageBlocks.parseLayout("=right").align)
    }

    @Test
    fun moveUpAndDownSkipsBlankLinesAndKeepsText() {
        val content = "title\n\nparagraph\n$img\nend"
        val up = ImageBlocks.move(content, Ref("img_1.jpg"), up = true)
        assertEquals("title\n\n$img\nparagraph\nend", up)
        val up2 = ImageBlocks.move(up, Ref("img_1.jpg"), up = true)
        assertEquals("$img\ntitle\n\nparagraph\nend", up2)
        // already first: no change
        assertEquals(up2, ImageBlocks.move(up2, Ref("img_1.jpg"), up = true))
        val down = ImageBlocks.move(content, Ref("img_1.jpg"), up = false)
        assertEquals("title\n\nparagraph\nend\n$img", down)
        // same multiset of lines: no text lost
        assertEquals(content.split('\n').sorted(), down.split('\n').sorted())
    }

    @Test
    fun reorderTwoImages() {
        val content = "![](a.jpg)\n![](b.jpg)"
        assertEquals("![](b.jpg)\n![](a.jpg)", ImageBlocks.move(content, Ref("b.jpg"), up = true))
    }

    @Test
    fun removeDeletesOnlyTheImageLine() {
        val content = "keep this\n$img\nand this ![inline](x.png) too"
        assertEquals("keep this\nand this ![inline](x.png) too", ImageBlocks.remove(content, Ref("img_1.jpg")))
    }

    @Test
    fun occurrenceSelectsTheRightDuplicate() {
        val content = "$img\ntext\n$img"
        val out = ImageBlocks.setLayout(content, Ref("img_1.jpg", occurrence = 1), Layout(Align.RIGHT))
        assertEquals("$img\ntext\n![](img_1.jpg \"=right\")", out)
    }

    @Test
    fun inlineImagesAndUnknownRefsAreIgnored() {
        val content = "text ![a](a.png) more"
        assertTrue(ImageBlocks.blocks(content).isEmpty())
        assertEquals(content, ImageBlocks.remove(content, Ref("a.png")))
        assertEquals(content, ImageBlocks.move(content, Ref("missing.png"), up = true))
    }

    @Test
    fun attachmentPathDetection() {
        assertTrue(ImageBlocks.isAttachmentPath("img_1.jpg"))
        assertFalse(ImageBlocks.isAttachmentPath("content://x/y.jpg"))
        assertFalse(ImageBlocks.isAttachmentPath("https://example.org/a.png"))
    }
}
