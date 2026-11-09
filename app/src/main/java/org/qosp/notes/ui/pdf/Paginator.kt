package org.qosp.notes.ui.pdf

/**
 * Places items of given heights top-to-bottom on pages of [pageHeight] content height.
 * An item that does not fit in the remaining space starts a new page (unless the page is still empty:
 * a single item taller than a page stays on it and the caller scales or clips it).
 */
class Paginator(private val pageHeight: Float) {
    var page = 0
        private set
    var y = 0f
        private set

    data class Slot(val page: Int, val y: Float)

    fun place(height: Float, spaceBefore: Float = 0f): Slot {
        var top = if (y == 0f) y else y + spaceBefore
        if (top + height > pageHeight && y > 0f) {
            page++
            top = 0f
        }
        y = top + height
        return Slot(page, top)
    }

    /** Remaining height on the current page. */
    val remaining: Float get() = pageHeight - y

    fun newPage() {
        if (y > 0f) { page++; y = 0f }
    }
}
