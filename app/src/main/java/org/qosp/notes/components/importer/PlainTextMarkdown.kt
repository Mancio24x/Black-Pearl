package org.qosp.notes.components.importer

/**
 * Converts plain text (what many apps export as .txt) into Markdown that shows the same way in Black Pearl:
 * "☐ / [ ] / ☑ / [x]" become checkboxes, "• ◦ ▪" bullets become lists, "1)" becomes "1.",
 * indentation that Markdown would turn into a code block is kept as visible indentation instead.
 * Text that is already Markdown passes through unchanged.
 */
object PlainTextMarkdown {
    private val markdownTask = Regex("""^\s*[-*+]\s+\[[ xX]]\s""")
    private val checked = Regex("""^([ \t]*)(?:[☑☒✅✓✔🗹▣]|\[[xX✓✔]])[ \t]*(.*)$""")
    private val unchecked = Regex("""^([ \t]*)(?:[☐□❏⬜◻]|\[ ?])[ \t]*(.*)$""")
    private val bullet = Regex("""^([ \t]*)[•◦▪▫‣●○∙·■][ \t]+(.*)$""")
    private val markdownList = Regex("""^[ \t]*(?:[-*+]|\d{1,3}[.)])[ \t]+""")
    private val numberedParen = Regex("""^([ \t]*)(\d{1,3})\)[ \t]+(.*)$""")
    private val yearLike = Regex("""^([ \t]*)(\d{4,})\.([ \t])""")
    private val leadingSpace = Regex("""^[ \t]+""")
    private const val NBSP = '\u00A0'

    fun convert(text: String): String {
        val out = normalize(text).split('\n').map { raw ->
            val line = raw.trimEnd()
            checked.matchEntire(line)?.takeUnless { markdownTask.containsMatchIn(line) }?.let { m ->
                return@map "${indent(m.groupValues[1])}- [x] ${m.groupValues[2]}"
            }
            unchecked.matchEntire(line)?.takeUnless { markdownTask.containsMatchIn(line) }?.let { m ->
                return@map "${indent(m.groupValues[1])}- [ ] ${m.groupValues[2]}"
            }
            bullet.matchEntire(line)?.let { m -> return@map "${indent(m.groupValues[1])}- ${m.groupValues[2]}" }
            numberedParen.matchEntire(line)?.let { m ->
                return@map "${indent(m.groupValues[1])}${m.groupValues[2]}. ${m.groupValues[3]}"
            }
            when {
                markdownList.containsMatchIn(line) -> indent(leadingSpace.find(line)?.value.orEmpty()) + line.trimStart()
                // 4+ leading spaces or a tab would become a code block: keep the indentation visible instead
                leadingSpace.containsMatchIn(line) -> {
                    val lead = leadingSpace.find(line)!!.value
                    NBSP.toString().repeat(lead.sumOf { if (it == '\t') 4 else 1 }) + line.substring(lead.length)
                }
                // "2024. A year" would become a numbered list starting at 2024
                else -> yearLike.replace(line) { m -> m.groupValues[1] + m.groupValues[2] + "\\." + m.groupValues[3] }
            }
        }
        return out.joinToString("\n").trim('\n')
    }

    /**
     * When every line is a checkbox, the note is a checklist: returns its items (Black Pearl list note),
     * otherwise null.
     */
    fun asChecklist(markdown: String): List<ImportedTask>? {
        val lines = markdown.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val task = Regex("""^- \[([ xX])] ?(.*)$""")
        val items = lines.map { task.matchEntire(it.trim()) ?: return null }
        return items.map { ImportedTask(it.groupValues[2].trim(), it.groupValues[1] != " ") }
    }

    fun normalize(text: String): String = text.removePrefix("\uFEFF")
        .replace("\r\n", "\n").replace('\r', '\n').replace('\u000C', '\n')

    private fun indent(lead: String) = lead.replace("\t", "    ")
}
