package org.qosp.notes.ui.editor.markdown

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TableRow
import org.commonmark.node.Code
import org.commonmark.node.CustomNode
import org.commonmark.node.Emphasis
import org.commonmark.node.HardLineBreak
import org.commonmark.node.Node
import org.commonmark.node.SoftLineBreak
import org.commonmark.node.StrongEmphasis
import org.commonmark.node.Text

/** A small grey column letter or row number drawn inside a table cell (preview only, never in the note text). */
class GridLabel(val label: String) : CustomNode()

/**
 * Black Pearl: a Markdown table shown like a spreadsheet. Formulas (`=B2+B3*2`, see [TableFormulas]) show their
 * result; with [labels] each column gets a thin letter above its title and each row a thin number in its first cell.
 * Only the rendered tree changes: the note text keeps the formulas.
 */
object TableSheet {
    fun apply(table: TableBlock, labels: Boolean) {
        val rows = rowsOf(table)
        if (rows.isEmpty()) return
        val cells = rows.map { cellsOf(it) }
        val grid = cells.map { row -> row.map { rawText(it).trim() } }
        val shown = TableFormulas.evaluate(grid)
        cells.forEachIndexed { r, row ->
            row.forEachIndexed { c, cell ->
                if (TableFormulas.isFormula(grid[r][c])) {
                    removeChildren(cell)
                    cell.appendChild(Text(shown[r][c]))
                }
            }
        }
        if (!labels) return
        cells.forEachIndexed { r, row ->
            row.forEachIndexed { c, cell ->
                val first = cell.firstChild
                if (r == 0) {
                    insertBefore(cell, first, GridLabel(TableFormulas.columnName(c)))
                    insertBefore(cell, first, HardLineBreak())
                }
                if (c == 0) {
                    insertBefore(cell, first, GridLabel("${r + 1}"))
                    insertBefore(cell, first, Text(" "))
                }
            }
        }
    }

    private fun insertBefore(parent: Node, anchor: Node?, node: Node) {
        if (anchor == null) parent.appendChild(node) else anchor.insertBefore(node)
    }

    private fun removeChildren(node: Node) {
        var child = node.firstChild
        while (child != null) {
            val next = child.next
            child.unlink()
            child = next
        }
    }

    private fun rowsOf(table: Node): List<TableRow> {
        val out = ArrayList<TableRow>()
        fun walk(n: Node) {
            var child = n.firstChild
            while (child != null) {
                if (child is TableRow) out.add(child) else walk(child)
                child = child.next
            }
        }
        walk(table)
        return out
    }

    private fun cellsOf(row: TableRow): List<TableCell> {
        val out = ArrayList<TableCell>()
        var child = row.firstChild
        while (child != null) {
            if (child is TableCell) out.add(child)
            child = child.next
        }
        return out
    }

    /** The cell as typed: emphasis markers come back, so `=B2*B3*2` is read as written. */
    internal fun rawText(node: Node): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            when (n) {
                is Text -> sb.append(n.literal)
                is Code -> sb.append(n.literal)
                is SoftLineBreak, is HardLineBreak -> sb.append(' ')
                is Emphasis -> { sb.append(n.openingDelimiter); children(n, ::walk); sb.append(n.closingDelimiter) }
                is StrongEmphasis -> { sb.append(n.openingDelimiter); children(n, ::walk); sb.append(n.closingDelimiter) }
                else -> children(n, ::walk)
            }
        }
        children(node, ::walk)
        return sb.toString()
    }

    private inline fun children(n: Node, f: (Node) -> Unit) {
        var child = n.firstChild
        while (child != null) {
            val next = child.next
            f(child)
            child = next
        }
    }
}
