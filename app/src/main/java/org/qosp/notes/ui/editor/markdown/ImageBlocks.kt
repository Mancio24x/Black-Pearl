package org.qosp.notes.ui.editor.markdown

/**
 * Repositionable images (Black Pearl feature 1).
 *
 * An image placed in a note is a line of standard Markdown on its own:
 *
 *     ![caption](img_123.jpg "=center,60")
 *
 * - destination: an attachment path (relative = file in the app media folder) or any URL;
 * - title "=ALIGN[,WIDTH]": layout, ALIGN in left|center|right, WIDTH in percent of the text width (ratio kept).
 *   Any other title is kept untouched. Other Markdown apps simply show the image (the title is a tooltip).
 *
 * All functions are pure: they only rewrite the image line and never touch other text.
 * An image is identified by its destination and occurrence index (same image may appear twice).
 */
object ImageBlocks {

    enum class Align { LEFT, CENTER, RIGHT }

    data class Layout(val align: Align = Align.LEFT, val widthPercent: Int? = null) {
        fun toTitle(): String? = if (this == Layout()) null else buildString {
            append('=').append(align.name.lowercase())
            widthPercent?.let { append(',').append(it) }
        }
    }

    data class Block(val lineIndex: Int, val alt: String, val destination: String, val title: String?) {
        val layout: Layout get() = parseLayout(title)
    }

    data class Ref(val destination: String, val occurrence: Int = 0)

    private val LINE = Regex("""^\s*!\[([^\]]*)]\((\S+?)(?:\s+"([^"]*)")?\)\s*$""")

    fun parseLayout(title: String?): Layout {
        if (title == null || !title.startsWith("=")) return Layout()
        val parts = title.substring(1).split(',').map { it.trim().lowercase() }
        val align = when (parts.getOrNull(0)) {
            "center" -> Align.CENTER
            "right" -> Align.RIGHT
            else -> Align.LEFT
        }
        val width = parts.getOrNull(1)?.removeSuffix("%")?.toIntOrNull()?.coerceIn(10, 100)
        return Layout(align, width)
    }

    fun format(alt: String, destination: String, title: String?): String {
        // brackets or line breaks in a caption (e.g. a file named "photo [1].jpg") would break the Markdown
        val safeAlt = alt.replace(Regex("[\\[\\]\\n\\r]"), " ").trim()
        return if (title.isNullOrEmpty()) "![$safeAlt]($destination)" else "![$safeAlt]($destination \"$title\")"
    }

    fun blocks(content: String): List<Block> = content.split('\n').mapIndexedNotNull { i, line ->
        LINE.matchEntire(line)?.let { m -> Block(i, m.groupValues[1], m.groupValues[2], m.groups[3]?.value) }
    }

    private fun find(content: String, ref: Ref): Block? =
        blocks(content).filter { it.destination == ref.destination }.getOrNull(ref.occurrence)

    /**
     * Inserts an image on its own line at [cursor] (end of text if out of range).
     * @return new content and the cursor position right after the inserted line.
     */
    fun insert(content: String, cursor: Int, destination: String, alt: String = ""): Pair<String, Int> {
        val at = if (cursor in 0..content.length) cursor else content.length
        val before = content.substring(0, at)
        val after = content.substring(at)
        val prefix = if (before.isEmpty() || before.endsWith('\n')) "" else "\n"
        val suffix = if (after.startsWith('\n')) "" else "\n"
        val line = format(alt, destination, null)
        val result = before + prefix + line + suffix + after
        return result to (before.length + prefix.length + line.length + suffix.length)
    }

    /** Rewrites the layout (alignment / width) of an image. */
    fun setLayout(content: String, ref: Ref, layout: Layout): String {
        val block = find(content, ref) ?: return content
        val keepTitle = block.title?.takeUnless { it.startsWith("=") }
        val title = layout.toTitle() ?: keepTitle
        return replaceLine(content, block.lineIndex, format(block.alt, block.destination, title))
    }

    /** Moves the image line above the previous non-blank line ([up]) or below the next one. */
    fun move(content: String, ref: Ref, up: Boolean): String {
        val block = find(content, ref) ?: return content
        val lines = content.split('\n').toMutableList()
        val i = block.lineIndex
        val range = if (up) (i - 1 downTo 0) else (i + 1 until lines.size)
        val j = range.firstOrNull { lines[it].isNotBlank() } ?: return content
        val line = lines.removeAt(i)
        // Up: j < i, insert before line j. Down: after removal line j shifted to j - 1, so index j is right after it.
        lines.add(j, line)
        return lines.joinToString("\n")
    }

    /** Removes only the image line from the text (the attachment file is kept). */
    fun remove(content: String, ref: Ref): String {
        val block = find(content, ref) ?: return content
        val lines = content.split('\n').toMutableList()
        lines.removeAt(block.lineIndex)
        return lines.joinToString("\n")
    }

    /** True if [destination] is a relative attachment path (no URI scheme). */
    fun isAttachmentPath(destination: String) = !destination.contains("://") && !destination.startsWith("data:")

    private fun replaceLine(content: String, index: Int, newLine: String): String =
        content.split('\n').toMutableList().also { it[index] = newLine }.joinToString("\n")
}
