package org.qosp.notes.ui.editor.markdown

import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor

/** Small, regular-weight, grey: the header row's bold must not apply to the labels. */
class GridLabelSpan(private val color: Int) : MetricAffectingSpan() {
    override fun updateDrawState(tp: TextPaint) {
        shrink(tp)
        tp.color = color
    }

    override fun updateMeasureState(tp: TextPaint) = shrink(tp)

    private fun shrink(tp: TextPaint) {
        tp.textSize *= 0.68f
        tp.isFakeBoldText = false
        tp.typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
}

/** Draws [GridLabel] nodes. */
object TableSheetPlugin : AbstractMarkwonPlugin() {
    private const val LABEL_COLOR = 0x8C888888.toInt()

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(GridLabel::class.java) { visitor, node ->
            val start = visitor.length()
            visitor.builder().append(node.label)
            visitor.setSpans(start, GridLabelSpan(LABEL_COLOR))
        }
    }
}

