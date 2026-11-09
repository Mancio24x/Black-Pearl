package org.qosp.notes.ui.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import io.noties.markwon.Markwon
import org.qosp.notes.data.model.Note
import org.qosp.notes.ui.attachments.getAttachmentUri
import org.qosp.notes.ui.editor.markdown.ImageBlocks
import java.io.OutputStream

/**
 * Writes a note as an A4 PDF with Android's PdfDocument (no extra library):
 * title, Markdown text (same renderer as the app), checklists, images with their layout, page breaks.
 * Print-friendly: white pages, black text, whatever the app theme.
 */
class NotePdfExporter(private val context: Context, private val markwon: Markwon) {

    companion object {
        const val PAGE_WIDTH = 595 // A4 in PostScript points
        const val PAGE_HEIGHT = 842
        private const val MARGIN = 48f
        private const val FOOTER = 24f
        private const val BLOCK_SPACING = 10f
    }

    private val contentWidth = (PAGE_WIDTH - 2 * MARGIN).toInt()
    private val contentHeight = PAGE_HEIGHT - 2 * MARGIN - FOOTER

    private val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 11f }
    private val titlePaint = TextPaint(bodyPaint).apply { textSize = 20f; typeface = Typeface.DEFAULT_BOLD }
    private val footerPaint = TextPaint(bodyPaint).apply { textSize = 8f; color = Color.DKGRAY; textAlign = Paint.Align.CENTER }

    /** @return number of pages written. */
    fun write(note: Note, out: OutputStream): Int {
        val document = PdfDocument()
        val paginator = Paginator(contentHeight)
        var page: PdfDocument.Page? = null
        var pageIndex = -1

        fun canvasFor(slot: Paginator.Slot): Canvas {
            while (pageIndex < slot.page) {
                page?.let { finish(document, it, pageIndex) }
                pageIndex++
                page = document.startPage(PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex + 1).create())
            }
            return page!!.canvas
        }

        fun drawLayout(layout: StaticLayout, spaceBefore: Float) {
            for (line in 0 until layout.lineCount) {
                val top = layout.getLineTop(line).toFloat()
                val bottom = layout.getLineBottom(line).toFloat()
                val slot = paginator.place(bottom - top, if (line == 0) spaceBefore else 0f)
                val canvas = canvasFor(slot)
                canvas.save()
                canvas.translate(MARGIN, MARGIN + slot.y - top)
                canvas.clipRect(0f, top, contentWidth.toFloat(), bottom)
                layout.draw(canvas)
                canvas.restore()
            }
        }

        if (note.title.isNotBlank()) drawLayout(layout(note.title, titlePaint), 0f)

        PdfBlocks.of(note).forEach { block ->
            when (block) {
                is PdfBlock.Text -> drawLayout(layout(markwon.toMarkdown(block.markdown), bodyPaint), BLOCK_SPACING)
                is PdfBlock.Task -> drawLayout(layout((if (block.checked) "☑ " else "☐ ") + block.text, bodyPaint), 4f)
                is PdfBlock.Image -> {
                    val bitmap = loadBitmap(block.destination)
                    if (bitmap == null) {
                        drawLayout(layout("[${block.alt.ifBlank { block.destination }}]", bodyPaint), BLOCK_SPACING)
                    } else {
                        val maxWidth = contentWidth * (block.layout.widthPercent ?: 100) / 100f
                        var w = minOf(maxWidth, bitmap.width.toFloat())
                        var h = w * bitmap.height / bitmap.width
                        if (h > contentHeight) { h = contentHeight; w = h * bitmap.width / bitmap.height }
                        val slot = paginator.place(h, BLOCK_SPACING)
                        val left = MARGIN + when (block.layout.align) {
                            ImageBlocks.Align.LEFT -> 0f
                            ImageBlocks.Align.CENTER -> (contentWidth - w) / 2
                            ImageBlocks.Align.RIGHT -> contentWidth - w
                        }
                        val top = MARGIN + slot.y
                        canvasFor(slot).drawBitmap(bitmap, null, RectF(left, top, left + w, top + h), Paint(Paint.FILTER_BITMAP_FLAG))
                        bitmap.recycle()
                    }
                }
            }
        }

        if (page == null) canvasFor(Paginator.Slot(0, 0f)) // empty note: one blank page
        finish(document, page!!, pageIndex)
        val pages = document.pages.size
        document.writeTo(out)
        document.close()
        return pages
    }

    private fun finish(document: PdfDocument, page: PdfDocument.Page, index: Int) {
        page.canvas.drawText("${index + 1}", PAGE_WIDTH / 2f, PAGE_HEIGHT - MARGIN / 2, footerPaint)
        document.finishPage(page)
    }

    private fun layout(text: CharSequence, paint: TextPaint): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, contentWidth)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(2f, 1f)
            .setIncludePad(true)
            .build()

    /** Local images only (attachments, content:// and file://): PDF export never uses the network. */
    private fun loadBitmap(destination: String): Bitmap? = runCatching {
        val uri = when {
            ImageBlocks.isAttachmentPath(destination) -> getAttachmentUri(context, destination)
            destination.startsWith("content://") || destination.startsWith("file://") -> Uri.parse(destination)
            else -> null
        } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0) return null
        // ~2x the printed width is enough for a sharp image and keeps the PDF small
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= contentWidth * 2) sample *= 2
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }.getOrNull()
}
