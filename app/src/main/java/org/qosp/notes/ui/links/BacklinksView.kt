package org.qosp.notes.ui.links

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import com.google.android.material.color.MaterialColors
import org.qosp.notes.R
import org.qosp.notes.components.links.Backlink

/** Black Pearl: the "Linked from · N" section under a note: one tappable card per note that links to it. */
class BacklinksView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    private val header: TextView

    init {
        orientation = VERTICAL
        header = TextView(context).apply {
            textSize = 14f
            setTypeface(typeface, android.graphics.Typeface.BOLD)
            setTextColor(MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant))
            gravity = android.view.Gravity.CENTER_VERTICAL
            compoundDrawablePadding = (8 * resources.displayMetrics.density).toInt()
            setCompoundDrawablesRelativeWithIntrinsicBounds(
                ContextCompat.getDrawable(context, R.drawable.ic_reply), null, null, null
            )
            val pad = (16 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, (8 * resources.displayMetrics.density).toInt())
        }
        addView(header)
        visibility = View.GONE
    }

    fun show(items: List<Backlink>, onClick: (Backlink) -> Unit) {
        removeViews(1, childCount - 1)
        isVisible = items.isNotEmpty()
        if (items.isEmpty()) return
        header.text = context.getString(R.string.link_backlinks_header, items.size)
        val inflater = LayoutInflater.from(context)
        items.forEach { item ->
            val card = inflater.inflate(R.layout.item_backlink, this, false)
            card.findViewById<TextView>(R.id.backlink_title).text =
                item.from.title.ifBlank { context.getString(R.string.indicator_untitled) }
            card.findViewById<TextView>(R.id.backlink_line).apply {
                text = item.line
                isVisible = item.line.isNotBlank()
            }
            card.setOnClickListener { onClick(item) }
            addView(card)
        }
    }
}
