package org.qosp.notes.ui.editor.markdown

import android.content.Context
import android.text.Layout
import android.text.TextPaint
import android.text.style.AlignmentSpan
import android.text.style.ClickableSpan
import android.view.View
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.image.AsyncDrawable
import io.noties.markwon.image.AsyncDrawableSpan
import io.noties.markwon.image.ImageSize
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Image
import org.commonmark.node.Link
import org.commonmark.node.Node
import org.commonmark.node.Paragraph
import org.commonmark.node.SoftLineBreak
import org.qosp.notes.R
import org.qosp.notes.ui.attachments.getAttachmentUri

/**
 * Renders Markdown images with the Black Pearl layout stored in the image title (see [ImageBlocks]):
 * width in percent (ratio kept), left/center/right alignment, relative attachment paths, and a click target.
 *
 * A screen that wants image clicks sets a [ImageClickListener] as tag [R.id.tag_image_click_listener]
 * on the TextView; other TextViews (note cards, widget) ignore clicks.
 * Must be registered after the Coil images plugin (it replaces its Image visitor).
 */
class ImageLayoutPlugin(private val context: Context) : AbstractMarkwonPlugin() {

    fun interface ImageClickListener {
        fun onImageClick(ref: ImageBlocks.Ref)
    }

    /** Marks an image so it can be found and clicked. */
    class ImageClickSpan(val ref: ImageBlocks.Ref) : ClickableSpan() {
        override fun onClick(widget: View) {
            (widget.getTag(R.id.tag_image_click_listener) as? ImageClickListener)?.onImageClick(ref)
        }

        override fun updateDrawState(ds: TextPaint) = Unit // no link styling
    }

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(Image::class.java) { visitor, image ->
            val configuration = visitor.configuration()
            val start = visitor.length()
            visitor.visitChildren(image)
            // the image needs at least one character to be drawn on
            if (start == visitor.length()) visitor.builder().append('￼')

            val raw = image.destination
            // Only an image alone on its line is a movable block (same rule as ImageBlocks.blocks): alone in its
            // paragraph, or between line breaks (a picture inserted between two lines of text, without blank lines)
            fun isLineEdge(node: Node?) = node == null || node is SoftLineBreak || node is HardLineBreak
            val standalone = image.parent is Paragraph && isLineEdge(image.previous) && isLineEdge(image.next)
            val occurrence = visitor.builder().getSpans(0, start)
                .count { (it.what as? ImageClickSpan)?.ref?.destination == raw }
            val resolved = if (ImageBlocks.isAttachmentPath(raw)) {
                getAttachmentUri(context, raw)?.toString() ?: raw
            } else {
                configuration.imageDestinationProcessor().process(raw)
            }
            val layout = ImageBlocks.parseLayout(image.title)
            val size = layout.widthPercent?.let { ImageSize(ImageSize.Dimension(it.toFloat(), "%"), null) }

            val drawable = AsyncDrawable(resolved, configuration.asyncDrawableLoader(), configuration.imageSizeResolver(), size)
            val spans = mutableListOf<Any>(
                AsyncDrawableSpan(configuration.theme(), drawable, AsyncDrawableSpan.ALIGN_BOTTOM, image.parent is Link),
            )
            if (standalone) spans += ImageClickSpan(ImageBlocks.Ref(raw, occurrence))
            when (layout.align) {
                ImageBlocks.Align.CENTER -> spans += AlignmentSpan.Standard(Layout.Alignment.ALIGN_CENTER)
                ImageBlocks.Align.RIGHT -> spans += AlignmentSpan.Standard(Layout.Alignment.ALIGN_OPPOSITE)
                ImageBlocks.Align.LEFT -> Unit
            }
            visitor.setSpans(start, spans.toTypedArray())
        }
    }
}
