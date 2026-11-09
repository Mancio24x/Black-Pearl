package org.qosp.notes.ui.editor.markdown

import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Pure logic behind moving and resizing a picture with the fingers (see ImageHandlesView).
 *
 * Moving: the rendered note is cut into "units", its non-blank lines. With Black Pearl's Markdown rendering (each
 * line break kept) a unit matches a non-blank line of the source, which is exactly the step of [ImageBlocks.move].
 * Dropping a picture before unit k is therefore a number of single moves.
 */
object ImageGestures {

    /** Start/end offsets of the non-blank lines of [rendered] text. */
    fun units(rendered: CharSequence): List<IntRange> {
        val out = mutableListOf<IntRange>()
        var start = 0
        for (i in 0..rendered.length) {
            if (i == rendered.length || rendered[i] == '\n') {
                if ((start until i).any { !rendered[it].isWhitespace() && rendered[it] != '\u00A0' }) out += start until i
                start = i + 1
            }
        }
        return out
    }

    /**
     * Moves needed to bring the picture in unit [imageUnit] before unit [dropBefore] (0..units count):
     * negative = up. Dropping right above or right below itself is no move.
     */
    fun steps(imageUnit: Int, dropBefore: Int): Int = when {
        dropBefore <= imageUnit -> dropBefore - imageUnit
        else -> (dropBefore - imageUnit - 1).coerceAtLeast(0)
    }

    /** [ImageBlocks.move] repeated: a negative [steps] moves up. */
    fun moveBy(content: String, ref: ImageBlocks.Ref, steps: Int): String {
        var out = content
        repeat(abs(steps)) {
            val next = ImageBlocks.move(out, ref, up = steps < 0)
            if (next == out) return out
            out = next
        }
        return out
    }

    val SNAPS = listOf(25, 50, 75, 100)

    /** Width in percent from a dragged size: 10–100, in steps of 5, pulled to 25/50/75/100 when close. */
    fun snapPercent(raw: Float): Int {
        val clamped = raw.coerceIn(10f, 100f)
        SNAPS.firstOrNull { abs(it - clamped) <= 4f }?.let { return it }
        return ((clamped / 5f).roundToInt() * 5).coerceIn(10, 100)
    }
}
