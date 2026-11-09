package org.qosp.notes.ui.pdf

import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.ui.editor.markdown.ImageBlocks

/** A note split into what the PDF draws, in order. */
sealed interface PdfBlock {
    data class Text(val markdown: String) : PdfBlock
    data class Image(val destination: String, val alt: String, val layout: ImageBlocks.Layout) : PdfBlock
    data class Task(val text: String, val checked: Boolean) : PdfBlock
}

object PdfBlocks {

    /** Text and standalone image lines (feature 1), checklist items, then image attachments not placed in the text. */
    fun of(note: Note): List<PdfBlock> {
        val blocks = mutableListOf<PdfBlock>()
        if (note.isList) {
            note.taskList.forEach { blocks += PdfBlock.Task(it.content, it.isDone) }
        } else {
            val images = ImageBlocks.blocks(note.content).associateBy { it.lineIndex }
            val text = StringBuilder()
            fun flush() {
                if (text.isNotBlank()) blocks += PdfBlock.Text(text.toString().trim('\n'))
                text.clear()
            }
            note.content.split('\n').forEachIndexed { i, line ->
                val image = images[i]
                if (image == null) {
                    text.append(line).append('\n')
                } else {
                    flush()
                    blocks += PdfBlock.Image(image.destination, image.alt, image.layout)
                }
            }
            flush()
        }
        val placed = blocks.filterIsInstance<PdfBlock.Image>().map { it.destination }.toSet()
        note.attachments
            .filter { it.type == Attachment.Type.IMAGE && it.path !in placed }
            .forEach { blocks += PdfBlock.Image(it.path, it.description, ImageBlocks.Layout(ImageBlocks.Align.CENTER)) }
        return blocks
    }
}
