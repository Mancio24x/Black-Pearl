package org.qosp.notes.ui.editor.markdown

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.TextUtils
import android.text.style.ClickableSpan
import android.text.style.ReplacementSpan
import android.text.style.URLSpan
import android.view.View
import android.widget.TextView
import com.google.android.material.color.MaterialColors
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.LinkResolver
import io.noties.markwon.LinkResolverDef
import org.qosp.notes.R
import org.qosp.notes.components.links.LinkIndex
import org.qosp.notes.components.links.WikiLinks
import java.net.URLDecoder
import java.net.URLEncoder
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Obsidian links `[[Target#Heading|label]]` in the preview: each one becomes a Markdown link to
 * `blackpearl-link:<target>#<heading>` (the stored text is never changed), then, in a TextView, a tappable pill
 * ([WikiPillSpan]; dashed outline when the target note does not exist). Other renderings (PDF export) keep the
 * plain label. A screen that handles taps sets a [WikiLinkClick] as tag [R.id.wiki_link_listener] on the TextView.
 */
class WikiLinkPlugin(private val index: () -> LinkIndex) : AbstractMarkwonPlugin() {

    fun interface WikiLinkClick {
        fun onWikiLink(target: String, heading: String?)
    }

    override fun processMarkdown(markdown: String): String {
        val links = WikiLinks.find(markdown)
        if (links.isEmpty()) return markdown
        val out = StringBuilder(markdown.length + links.size * 24)
        var last = 0
        for (link in links) {
            out.append(markdown, last, link.range.first)
            out.append('[').append(escape(link.shown)).append("](").append(url(link.target, link.heading)).append(')')
            last = link.range.last + 1
        }
        out.append(markdown, last, markdown.length)
        return out.toString()
    }

    // Done before the text is set, so the TextView lays the pills out with the text (no second layout pass)
    override fun beforeSetText(textView: TextView, markdown: Spanned) {
        val text = markdown as? Spannable ?: return
        val links = text.getSpans(0, text.length, URLSpan::class.java).filter { it.url?.startsWith(SCHEME) == true }
        if (links.isEmpty()) return
        val index = index()
        val context = textView.context
        val colors = PillColors(
            fill = MaterialColors.getColor(context, com.google.android.material.R.attr.colorPrimaryContainer, FILL),
            onFill = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOnPrimaryContainer, ON_FILL),
            outline = MaterialColors.getColor(context, com.google.android.material.R.attr.colorOutline, OUTLINE),
        )
        val density = context.resources.displayMetrics.density
        val maxWidth = { textView.width - textView.totalPaddingLeft - textView.totalPaddingRight }
        for (span in links) {
            val start = text.getSpanStart(span)
            val end = text.getSpanEnd(span)
            val (target, heading) = parse(span.url) ?: continue
            text.removeSpan(span)
            if (start < 0 || start >= end) continue
            text.setSpan(WikiPillSpan(index.isGhost(target), colors, density, maxWidth), start, end, FLAGS)
            text.setSpan(WikiLinkClickSpan(target, heading), start, end, FLAGS)
        }
    }

    class PillColors(val fill: Int, val onFill: Int, val outline: Int)

    /** A rounded pill drawn in place of the link label; ellipsized when wider than the text view. */
    class WikiPillSpan(
        val ghost: Boolean,
        private val colors: PillColors,
        private val density: Float,
        private val maxWidth: () -> Int,
    ) : ReplacementSpan() {
        private val rect = RectF()
        private val dash = DashPathEffect(floatArrayOf(4 * density, 3 * density), 0f)

        private fun width(paint: Paint, text: CharSequence?, start: Int, end: Int): Float {
            val natural = (text?.let { paint.measureText(it, start, end) } ?: 0f) + 2 * PAD_H * density
            val limit = maxWidth()
            return if (limit > 0) min(natural, limit.toFloat()) else natural
        }

        override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
            if (fm != null) paint.getFontMetricsInt(fm)
            return ceil(width(paint, text, start, end)).toInt()
        }

        override fun draw(
            canvas: Canvas, text: CharSequence?, start: Int, end: Int, x: Float, top: Int, y: Int, bottom: Int, paint: Paint
        ) {
            if (text == null) return
            val width = width(paint, text, start, end)
            val metrics = paint.fontMetrics
            val padV = PAD_V * density
            rect.set(x, max(top.toFloat(), y + metrics.ascent - padV), x + width, min(bottom.toFloat(), y + metrics.descent + padV))
            val radius = rect.height() / 2

            val textColor = paint.color
            val style = paint.style
            val stroke = paint.strokeWidth
            val effect = paint.pathEffect
            if (ghost) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = density
                paint.pathEffect = dash
                paint.color = colors.outline
                rect.inset(density / 2, density / 2)
            } else {
                paint.style = Paint.Style.FILL
                paint.color = colors.fill
            }
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.FILL
            paint.strokeWidth = stroke
            paint.pathEffect = effect
            paint.color = if (ghost) textColor else colors.onFill

            val avail = width - 2 * PAD_H * density
            val label = TextUtils.ellipsize(text.subSequence(start, end), TextPaint(paint), avail, TextUtils.TruncateAt.END)
            canvas.drawText(label, 0, label.length, x + PAD_H * density, y.toFloat(), paint)

            paint.color = textColor
            paint.style = style
        }
    }

    class WikiLinkClickSpan(val target: String, val heading: String?) : ClickableSpan() {
        override fun onClick(widget: View) = WikiLinkPlugin.onClick(widget, target, heading)

        override fun updateDrawState(ds: TextPaint) = Unit // the pill does the styling
    }

    /** Link resolver of the app's Markwon: `blackpearl-link:` URLs go to [onClick], the others to [fallback]. */
    class Resolver(private val fallback: LinkResolver = LinkResolverDef()) : LinkResolver {
        override fun resolve(view: View, link: String) {
            val parsed = parse(link)
            if (parsed == null) fallback.resolve(view, link) else onClick(view, parsed.first, parsed.second)
        }
    }

    companion object {
        const val SCHEME = "blackpearl-link:"
        private const val FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        private const val PAD_H = 8f
        private const val PAD_V = 2f
        private const val FILL = 0xFFD8E2FF.toInt()
        private const val ON_FILL = 0xFF001A41.toInt()
        private const val OUTLINE = 0xFF74777F.toInt()
        private const val ESCAPED = "\\`*_~=<>[]!&|\$"

        fun onClick(view: View, target: String, heading: String?) {
            (view.getTag(R.id.wiki_link_listener) as? WikiLinkClick)?.onWikiLink(target, heading)
        }

        fun url(target: String, heading: String?): String = SCHEME + encode(target) + "#" + encode(heading.orEmpty())

        /** (target, heading) of a `blackpearl-link:` URL, or null for any other URL. */
        fun parse(url: String?): Pair<String, String?>? {
            if (url == null || !url.startsWith(SCHEME)) return null
            val rest = url.substring(SCHEME.length)
            val target = decode(rest.substringBefore('#'))
            val heading = decode(rest.substringAfter('#', "")).ifEmpty { null }
            return target to heading
        }

        private fun encode(s: String) = URLEncoder.encode(s, "UTF-8")

        private fun decode(s: String) = runCatching { URLDecoder.decode(s, "UTF-8") }.getOrDefault(s)

        /** Backslash-escapes the characters that would make the label Markdown (all are valid CommonMark escapes). */
        private fun escape(label: String): String = buildString(label.length) {
            for (c in label) {
                if (c in ESCAPED) append('\\')
                append(c)
            }
        }
    }
}
