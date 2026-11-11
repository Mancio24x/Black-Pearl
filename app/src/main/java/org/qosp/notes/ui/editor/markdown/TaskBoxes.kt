package org.qosp.notes.ui.editor.markdown

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.Editable
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.ReplacementSpan
import android.text.style.StrikethroughSpan
import android.view.MotionEvent
import android.widget.EditText
import io.noties.markwon.editor.MarkwonEditorTextWatcher
import kotlin.math.abs

/**
 * Black Pearl: in the editor, a Markdown task prefix ("- [ ]" / "- [x]") is drawn as a round-cornered square that
 * can be tapped. The note text is not changed: it stays plain Markdown ("- [ ] milk"), only its look changes.
 */
data class TaskBox(val start: Int, val end: Int, val checked: Boolean, val lineEnd: Int) {
    /** Index of the character between the brackets (" " or "x"). */
    val markIndex get() = end - 2
}

private val TASK_PREFIX = Regex("(?m)^([ \\t]*)[-+*] \\[([ xX])](?=[ \\t]|$)")

/** Every task prefix in [text] (pure, unit tested). */
fun findTaskBoxes(text: CharSequence): List<TaskBox> = TASK_PREFIX.findAll(text).map { m ->
    val start = m.range.first + m.groupValues[1].length
    val end = m.range.last + 1
    val lineEnd = text.indexOf('\n', end).let { if (it < 0) text.length else it }
    TaskBox(start, end, m.groupValues[2] != " ", lineEnd)
}.toList()

/** Ticks or unticks the box: only the character between the brackets changes. */
fun Editable.toggleTaskBox(box: TaskBox) {
    replace(box.markIndex, box.markIndex + 1, if (box.checked) " " else "x")
}

class TaskBoxColors(val outline: Int, val fill: Int, val check: Int, val doneText: Int)

/** Draws "- [ ]" as an empty square and "- [x]" as a filled square with a tick. */
class TaskBoxSpan(val checked: Boolean, private val colors: TaskBoxColors) : ReplacementSpan() {
    private val rect = RectF()
    private val path = Path()

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int =
        (paint.textSize * 1.35f).toInt()

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
    ) {
        val size = paint.textSize * 0.95f
        val fm = paint.fontMetrics
        val centerY = y + (fm.ascent + fm.descent) / 2f
        val left = x + paint.textSize * 0.05f
        rect.set(left, centerY - size / 2f, left + size, centerY + size / 2f)
        val radius = size * 0.28f
        val oldStyle = paint.style
        val oldColor = paint.color
        val oldStroke = paint.strokeWidth
        val oldCap = paint.strokeCap
        val oldJoin = paint.strokeJoin
        paint.isAntiAlias = true
        if (checked) {
            paint.style = Paint.Style.FILL
            paint.color = colors.fill
            canvas.drawRoundRect(rect, radius, radius, paint)
            paint.style = Paint.Style.STROKE
            paint.color = colors.check
            paint.strokeWidth = size * 0.13f
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeJoin = Paint.Join.ROUND
            path.reset()
            path.moveTo(rect.left + size * 0.24f, rect.top + size * 0.52f)
            path.lineTo(rect.left + size * 0.43f, rect.top + size * 0.70f)
            path.lineTo(rect.left + size * 0.77f, rect.top + size * 0.32f)
            canvas.drawPath(path, paint)
        } else {
            paint.style = Paint.Style.STROKE
            paint.color = colors.outline
            paint.strokeWidth = size * 0.11f
            val inset = paint.strokeWidth / 2f
            rect.inset(inset, inset)
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
        paint.style = oldStyle
        paint.color = oldColor
        paint.strokeWidth = oldStroke
        paint.strokeCap = oldCap
        paint.strokeJoin = oldJoin
    }
}

/** Subclasses so that Markwon's editor (which manages plain StrikethroughSpan) does not remove them. */
private class DoneTaskStrikeSpan : StrikethroughSpan()
private class DoneTaskColorSpan(color: Int) : ForegroundColorSpan(color)

/** Puts the boxes (and the struck-through look of done tasks) on [text]. */
fun applyTaskBoxes(text: Editable, colors: TaskBoxColors) {
    listOf(TaskBoxSpan::class.java, DoneTaskStrikeSpan::class.java, DoneTaskColorSpan::class.java).forEach { type ->
        text.getSpans(0, text.length, type).forEach { text.removeSpan(it) }
    }
    findTaskBoxes(text).forEach { box ->
        text.setSpan(TaskBoxSpan(box.checked, colors), box.start, box.end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        if (box.checked && box.lineEnd > box.end) {
            text.setSpan(DoneTaskStrikeSpan(), box.end, box.lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            text.setSpan(DoneTaskColorSpan(colors.doneText), box.end, box.lineEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}

/**
 * Wraps Markwon's editor watcher, so the boxes are drawn exactly when Markdown highlighting is on (also during
 * `withOnlyTextWatcher<MarkwonEditorTextWatcher>`, which the editor uses when it sets the text).
 */
class TaskBoxTextWatcher(
    private val inner: MarkwonEditorTextWatcher,
    private val colors: () -> TaskBoxColors,
) : MarkwonEditorTextWatcher() {
    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) =
        inner.beforeTextChanged(s, start, count, after)

    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) =
        inner.onTextChanged(s, start, before, count)

    override fun afterTextChanged(s: Editable) {
        applyTaskBoxes(s, colors())
        inner.afterTextChanged(s)
    }
}

/** Taps on a box tick / untick it; any other touch goes to the text field as usual. */
@SuppressLint("ClickableViewAccessibility")
fun EditText.enableTaskBoxTaps(isEnabled: () -> Boolean) {
    var pressed: TaskBox? = null
    var downX = 0f
    var downY = 0f
    setOnTouchListener { _, event ->
        if (!isEnabled()) return@setOnTouchListener false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                pressed = taskBoxAt(event.x, event.y)
                downX = event.x
                downY = event.y
                pressed != null
            }
            MotionEvent.ACTION_UP -> {
                val box = pressed ?: return@setOnTouchListener false
                pressed = null
                val slop = textSize
                if (abs(event.x - downX) < slop && abs(event.y - downY) < slop && taskBoxAt(event.x, event.y) == box) {
                    text?.toggleTaskBox(box)
                    performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK)
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> (pressed != null).also { pressed = null }
            else -> pressed != null
        }
    }
}

private fun EditText.taskBoxAt(x: Float, y: Float): TaskBox? {
    val layout = layout ?: return null
    val content = text ?: return null
    val localY = (y - totalPaddingTop + scrollY).toInt()
    val localX = x - totalPaddingLeft + scrollX
    if (localY < 0 || localY > layout.height) return null
    val line = layout.getLineForVertical(localY)
    val lineStart = layout.getLineStart(line)
    val lineEnd = layout.getLineEnd(line)
    val box = findTaskBoxes(content).firstOrNull { it.start in lineStart until lineEnd } ?: return null
    if (content.getSpans(box.start, box.end, TaskBoxSpan::class.java).isEmpty()) return null
    val boxLeft = layout.getPrimaryHorizontal(box.start)
    val boxRight = boxLeft + textSize * 1.35f
    val tolerance = textSize * 0.4f
    return box.takeIf { localX >= boxLeft - tolerance && localX <= boxRight + tolerance }
}
