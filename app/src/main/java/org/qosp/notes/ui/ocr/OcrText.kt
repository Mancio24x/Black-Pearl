package org.qosp.notes.ui.ocr

/** Post-processing of OCR output: keeps lines and paragraphs, removes noise that OCR typically adds. */
object OcrText {

    private val HYPHEN_BREAK = Regex("""(\p{L})-\n(\p{Ll})""")
    private val MANY_BLANK_LINES = Regex("""\n{3,}""")

    fun clean(raw: String): String = raw
        .replace("\r\n", "\n").replace('\r', '\n')
        .lines().joinToString("\n") { it.trimEnd() }
        // "parola spez-\nzata" -> "parola spezzata" (only when the next line continues in lowercase)
        .replace(HYPHEN_BREAK, "$1$2")
        .replace(MANY_BLANK_LINES, "\n\n")
        .trim()

    /** Inserts [text] as its own paragraph at [cursor]; returns new content and cursor after it. */
    fun insertAsParagraph(content: String, cursor: Int, text: String): Pair<String, Int> {
        val at = if (cursor in 0..content.length) cursor else content.length
        val before = content.substring(0, at)
        val after = content.substring(at)
        val prefix = when {
            before.isEmpty() || before.endsWith("\n\n") -> ""
            before.endsWith('\n') -> "\n"
            else -> "\n\n"
        }
        val suffix = when {
            after.isEmpty() || after.startsWith("\n\n") -> ""
            after.startsWith('\n') -> "\n"
            else -> "\n\n"
        }
        val result = before + prefix + text + suffix + after
        return result to (before.length + prefix.length + text.length)
    }

    /** True if nothing useful was recognized (empty or only symbols). */
    fun isUnreadable(text: String): Boolean = text.count { it.isLetterOrDigit() } < 2
}
