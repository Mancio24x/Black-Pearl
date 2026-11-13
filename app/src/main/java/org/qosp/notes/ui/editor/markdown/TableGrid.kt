package org.qosp.notes.ui.editor.markdown

/**
 * Black Pearl: a Markdown (GFM) table as a grid of cells, for the table editor. Row 0 holds the titles; every row
 * has one cell per column. Edits return a new grid; [toMarkdown] writes a plain table with aligned columns.
 */
data class TableGrid(val rows: List<List<String>>, val aligns: List<Align>) {

    enum class Align { NONE, LEFT, CENTER, RIGHT }

    val columns: Int get() = aligns.size

    fun isBlank(): Boolean = rows.all { row -> row.all(String::isBlank) }

    fun set(r: Int, c: Int, text: String): TableGrid {
        if (r !in rows.indices || c !in 0 until columns) return this
        val clean = text.replace('\n', ' ').replace('\r', ' ')
        return copy(rows = rows.mapIndexed { i, row -> if (i == r) row.mapIndexed { j, v -> if (j == c) clean else v } else row })
    }

    fun addRow(): TableGrid = copy(rows = rows + listOf(List(columns) { "" }))

    fun addColumn(): TableGrid = copy(rows = rows.map { it + "" }, aligns = aligns + Align.NONE)

    /** The title row and the last body row stay. */
    fun deleteRow(r: Int): TableGrid =
        if (r < 1 || r >= rows.size || rows.size <= 2) this else copy(rows = rows.filterIndexed { i, _ -> i != r })

    /** The last column stays. */
    fun deleteColumn(c: Int): TableGrid =
        if (c !in 0 until columns || columns <= 1) this
        else copy(rows = rows.map { row -> row.filterIndexed { j, _ -> j != c } }, aligns = aligns.filterIndexed { j, _ -> j != c })

    fun toMarkdown(): String {
        val escaped = rows.map { row -> row.map { it.trim().replace("|", "\\|") } }
        val widths = (0 until columns).map { c -> maxOf(3, escaped.maxOf { it[c].length }) }
        val sb = StringBuilder()
        fun line(cells: List<String>) {
            sb.append('|')
            cells.forEachIndexed { c, v -> sb.append(' ').append(v.padEnd(widths[c])).append(" |") }
            sb.append('\n')
        }
        line(escaped[0])
        line(aligns.mapIndexed { c, a -> delimiter(a, widths[c]) })
        escaped.drop(1).forEach(::line)
        return sb.toString()
    }

    companion object {
        private val DELIMITER_CELL = Regex("^:?-+:?$")

        fun empty(columns: Int = 3, bodyRows: Int = 2): TableGrid =
            TableGrid(List(bodyRows + 1) { List(columns) { "" } }, List(columns) { Align.NONE })

        /** The table in [markdown] (title line, delimiter line, body lines), or null if it is not one. */
        fun parse(markdown: String): TableGrid? {
            val lines = markdown.lines().filter { it.isNotBlank() }
            if (lines.size < 2) return null
            val titles = splitRow(lines[0]) ?: return null
            val aligns = delimiterRow(lines[1]) ?: return null
            if (aligns.size != titles.size) return null
            val body = lines.drop(2).map { line ->
                val cells = splitRow(line) ?: listOf(line.trim())
                List(titles.size) { cells.getOrElse(it) { "" } }
            }
            return TableGrid(listOf(titles) + body, aligns)
        }

        /** Alignments if [line] is a delimiter row (`|---|:-:|`), else null. */
        internal fun delimiterRow(line: String): List<Align>? {
            val cells = splitRow(line) ?: return null // a delimiter row has a pipe ("---" alone is a rule)
            if (cells.any { !DELIMITER_CELL.matches(it) }) return null
            return cells.map {
                val left = it.startsWith(':')
                val right = it.endsWith(':')
                when {
                    left && right -> Align.CENTER
                    left -> Align.LEFT
                    right -> Align.RIGHT
                    else -> Align.NONE
                }
            }
        }

        /** The cells of a table line (outer pipes optional, `\|` is a pipe), or null if it has no pipe. */
        internal fun splitRow(line: String): List<String>? {
            var s = line.trim()
            if (!hasPipe(s)) return null
            if (s.startsWith('|')) s = s.substring(1)
            if (s.endsWith('|') && !s.endsWith("\\|")) s = s.dropLast(1)
            val cells = mutableListOf<String>()
            val cell = StringBuilder()
            var i = 0
            while (i < s.length) {
                val ch = s[i]
                if (ch == '\\' && i + 1 < s.length && s[i + 1] == '|') {
                    cell.append('|')
                    i += 2
                    continue
                }
                if (ch == '|') {
                    cells += cell.toString().trim()
                    cell.clear()
                } else {
                    cell.append(ch)
                }
                i++
            }
            cells += cell.toString().trim()
            return cells
        }

        private fun hasPipe(s: String): Boolean = s.indices.any { s[it] == '|' && (it == 0 || s[it - 1] != '\\') }

        private fun delimiter(align: Align, width: Int): String = when (align) {
            Align.NONE -> "-".repeat(width)
            Align.LEFT -> ":" + "-".repeat(width - 1)
            Align.RIGHT -> "-".repeat(width - 1) + ":"
            Align.CENTER -> ":" + "-".repeat(width - 2) + ":"
        }
    }
}
