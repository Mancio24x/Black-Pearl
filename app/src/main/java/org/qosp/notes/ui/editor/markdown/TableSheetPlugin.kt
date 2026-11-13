package org.qosp.notes.ui.editor.markdown

import android.graphics.Typeface
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.ClickableSpan
import android.text.style.MetricAffectingSpan
import android.view.View
import android.widget.TextView
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.MarkwonVisitor
import io.noties.markwon.ext.tables.TableRowSpan
import org.qosp.notes.R

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

/**
 * Draws [GridLabel] nodes. On a TextView that has a [TableClickListener] as tag [R.id.tag_table_click_listener]
 * (the editor preview), each table becomes a click target that reports its index (0 = first table drawn).
 */
object TableSheetPlugin : AbstractMarkwonPlugin() {
    fun interface TableClickListener {
        fun onTableClick(index: Int)
    }

    class TableClickSpan(val index: Int) : ClickableSpan() {
        override fun onClick(widget: View) {
            (widget.getTag(R.id.tag_table_click_listener) as? TableClickListener)?.onTableClick(index)
        }

        override fun updateDrawState(ds: TextPaint) = Unit // no link styling
    }

    override fun afterSetText(textView: TextView) {
        if (textView.getTag(R.id.tag_table_click_listener) !is TableClickListener) return
        val text = textView.text as? Spannable ?: return
        tableRanges(text).forEachIndexed { index, range ->
            text.setSpan(TableClickSpan(index), range.first, range.last + 1, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }

    /** Rows drawn one after the other (only line breaks between them) belong to the same table. */
    internal fun tableRanges(text: Spanned): List<IntRange> {
        val rows = text.getSpans(0, text.length, TableRowSpan::class.java)
            .map { text.getSpanStart(it) until text.getSpanEnd(it) }
            .filter { !it.isEmpty() }
            .sortedBy { it.first }
        val out = mutableListOf<IntRange>()
        for (row in rows) {
            val prev = out.lastOrNull()
            if (prev != null && (prev.last + 1 until row.first).all { text[it] == '\n' }) {
                out[out.lastIndex] = prev.first..maxOf(prev.last, row.last)
            } else {
                out += row
            }
        }
        return out
    }

    private const val LABEL_COLOR = 0x8C888888.toInt()

    override fun configureVisitor(builder: MarkwonVisitor.Builder) {
        builder.on(GridLabel::class.java) { visitor, node ->
            val start = visitor.length()
            visitor.builder().append(node.label)
            visitor.setSpans(start, GridLabelSpan(LABEL_COLOR))
        }
    }
}

