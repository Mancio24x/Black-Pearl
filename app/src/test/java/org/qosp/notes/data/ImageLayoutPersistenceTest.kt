package org.qosp.notes.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.ui.editor.markdown.ImageBlocks

/** Image position and layout live in the note text, so they must survive a backup round trip unchanged. */
class ImageLayoutPersistenceTest {

    @Test
    fun imageLayoutSurvivesBackupAndRestore() {
        var content = "Intro\n\nSecond paragraph"
        content = ImageBlocks.insert(content, "Intro".length, "img_42.jpg", "boat").first
        content = ImageBlocks.setLayout(content, ImageBlocks.Ref("img_42.jpg"), ImageBlocks.Layout(ImageBlocks.Align.RIGHT, 50))
        content = ImageBlocks.move(content, ImageBlocks.Ref("img_42.jpg"), up = false)

        val note = Note(
            title = "Trip",
            content = content,
            attachments = listOf(Attachment(Attachment.Type.IMAGE, "img_42.jpg", "boat.jpg")),
            id = 1,
        )
        val restored = Backup.fromString(Backup(version = 1, notes = setOf(note)).serialize()).notes.single()

        assertEquals(note.content, restored.content)
        val block = ImageBlocks.blocks(restored.content).single()
        assertEquals(ImageBlocks.Layout(ImageBlocks.Align.RIGHT, 50), block.layout)
        assertEquals("Second paragraph", restored.content.split('\n')[block.lineIndex - 1])
        assertEquals(note.attachments, restored.attachments)
    }
}
