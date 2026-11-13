package org.qosp.notes.ui.editor.markdown

import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.ext.gfm.tables.TableCell
import org.commonmark.ext.gfm.tables.TablesExtension
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.CustomBlock
import org.commonmark.node.CustomNode
import org.commonmark.node.Node
import org.commonmark.node.Text
import org.commonmark.parser.Parser
import org.junit.Assert.assertEquals
import org.junit.Test

class TableSheetTest {
    private val parser = Parser.builder().extensions(listOf(TablesExtension.create())).build()

    private fun table(md: String): TableBlock {
        var found: TableBlock? = null
        parser.parse(md).accept(object : AbstractVisitor() {
            override fun visit(customBlock: CustomBlock?) {
                if (customBlock is TableBlock) found = customBlock
            }
        })
        return found!!
    }

    private fun cells(t: TableBlock): List<TableCell> {
        val out = ArrayList<TableCell>()
        fun walk(n: Node) {
            var c = n.firstChild
            while (c != null) { if (c is TableCell) out.add(c) else walk(c); c = c.next }
        }
        walk(t)
        return out
    }

    /** Text of a cell with labels shown as [x]. */
    private fun shown(cell: TableCell): String {
        val sb = StringBuilder()
        fun walk(n: Node) {
            var c = n.firstChild
            while (c != null) {
                when (c) {
                    is GridLabel -> sb.append("[${c.label}]")
                    is Text -> sb.append(c.literal)
                    is CustomNode -> {}
                    else -> if (c.firstChild == null) sb.append("|") else walk(c)
                }
                c = c.next
            }
        }
        walk(cell)
        return sb.toString()
    }

    private val md = """
        | Spesa | Euro |
        |---|---|
        | Pane | 2 |
        | Latte | 1,50 |
        | Totale | =B2*2+B3*2 |
    """.trimIndent()

    @Test fun formulasShowTheirResultEvenWithStarsThatLookLikeItalics() {
        val t = table(md)
        TableSheet.apply(t, labels = false)
        assertEquals(listOf("Spesa", "Euro", "Pane", "2", "Latte", "1,50", "Totale", "7"), cells(t).map(::shown))
    }

    @Test fun labelsAddColumnLettersAboveTitlesAndRowNumbers() {
        val t = table(md)
        TableSheet.apply(t, labels = true)
        assertEquals(
            listOf("[A]|[1] Spesa", "[B]|Euro", "[2] Pane", "2", "[3] Latte", "1,50", "[4] Totale", "7"),
            cells(t).map(::shown),
        )
    }
}
