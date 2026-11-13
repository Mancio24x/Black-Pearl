package org.qosp.notes.ui.editor.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class TableFormulasTest {
    private fun eval(vararg rows: List<String>) = TableFormulas.evaluate(rows.toList())

    @Test fun nothingToDoWithoutFormulas() {
        val grid = listOf(listOf("Spesa", "Euro"), listOf("Pane", "2"))
        assertEquals(grid, TableFormulas.evaluate(grid))
    }

    @Test fun sumOfCellsKeepsTheCommaOfTheTable() {
        val out = eval(listOf("Spesa", "Euro"), listOf("Pane", "2"), listOf("Latte", "1,50"), listOf("Totale", "=B2+B3"))
        assertEquals("3,5", out[3][1])
        assertEquals("Totale", out[3][0])
    }

    @Test fun precedenceParenthesesAndUnaryMinus() {
        assertEquals("14", eval(listOf("=2+3*4"))[0][0])
        assertEquals("20", eval(listOf("=(2+3)*4"))[0][0])
        assertEquals("-2", eval(listOf("=-(1+1)"))[0][0])
        assertEquals("2.5", eval(listOf("=5/2"))[0][0])
        assertEquals("6", eval(listOf("= 2 × 3"))[0][0])
        assertEquals("3", eval(listOf("=6÷2"))[0][0])
        assertEquals("10", eval(listOf("=2x5"))[0][0])
    }

    @Test fun formulasCanUseOtherFormulasAndLowercaseRefs() {
        val out = eval(listOf("A", "B"), listOf("4", "=a2*2"), listOf("=B2+1", "=(A2+B2)/A3"))
        assertEquals("8", out[1][1])
        assertEquals("9", out[2][0])
        assertEquals("1.3333333333", out[2][1])
    }

    @Test fun errorsDoNotBreakTheTable() {
        assertEquals(TableFormulas.DIV_ZERO, eval(listOf("=1/0"))[0][0])
        assertEquals(TableFormulas.ERROR, eval(listOf("=A1"))[0][0]) // refers to itself
        assertEquals(TableFormulas.ERROR, eval(listOf("x", "=A1+1"))[0][1]) // "x" is not a number
        assertEquals(TableFormulas.ERROR, eval(listOf("=Z9"))[0][0]) // outside the table
        assertEquals(TableFormulas.ERROR, eval(listOf("=(1+2"))[0][0])
        assertEquals(TableFormulas.ERROR, eval(listOf("=1+"))[0][0])
    }

    @Test fun emptyCellsCountAsZeroAndEuroSignsAreIgnored() {
        assertEquals("5", eval(listOf("", "€ 5", "=A1+B1"))[0][2])
    }

    @Test fun numbersWithThousandSeparators() {
        assertEquals("1234.5", TableFormulas.parseNumber("1.234,5")!!.toPlainString())
        assertEquals("1234.5", TableFormulas.parseNumber("1,234.5")!!.toPlainString())
        assertEquals("1.5", TableFormulas.parseNumber("1,5")!!.toPlainString())
    }

    @Test fun columnNames() {
        assertEquals(listOf("A", "B", "Z", "AA", "AB"), listOf(0, 1, 25, 26, 27).map(TableFormulas::columnName))
    }
}
