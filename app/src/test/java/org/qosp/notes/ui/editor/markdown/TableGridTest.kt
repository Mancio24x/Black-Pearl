package org.qosp.notes.ui.editor.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TableGridTest {
    private val sample = "| a | b |\n|---|:-:|\n| 1 | 2 |"

    @Test
    fun parsesTitleAndRows() {
        val grid = TableGrid.parse(sample)!!
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), grid.rows)
        assertEquals(listOf(TableGrid.Align.NONE, TableGrid.Align.CENTER), grid.aligns)
    }

    @Test
    fun notATable() {
        assertNull(TableGrid.parse("just | text"))
        assertNull(TableGrid.parse(""))
    }

    @Test
    fun serializesPadded() {
        assertEquals("| a   | b   |\n| --- | :-: |\n| 1   | 2   |\n", TableGrid.parse(sample)!!.toMarkdown())
    }

    @Test
    fun alignmentsRoundTrip() {
        val md = "| x | y | z |\n|:--|--:|:-:|\n| 1 | 2 | 3 |\n"
        val grid = TableGrid.parse(md)!!
        assertEquals(listOf(TableGrid.Align.LEFT, TableGrid.Align.RIGHT, TableGrid.Align.CENTER), grid.aligns)
        assertEquals(grid, TableGrid.parse(grid.toMarkdown()))
    }

    @Test
    fun pipeRoundTrips() {
        val grid = TableGrid.parse(sample)!!.set(1, 0, "x|y")
        assertTrue(grid.toMarkdown().contains("x\\|y"))
        assertEquals("x|y", TableGrid.parse(grid.toMarkdown())!!.rows[1][0])
    }

    @Test
    fun raggedRows() {
        val grid = TableGrid.parse("| a | b |\n|---|---|\n| 1 |\n| 1 | 2 | 3 |")!!
        assertEquals(listOf(listOf("a", "b"), listOf("1", ""), listOf("1", "2")), grid.rows)
    }

    @Test
    fun withoutOuterPipes() {
        val grid = TableGrid.parse("a | b\n--- | ---\n1 | 2")!!
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), grid.rows)
    }

    @Test
    fun newlinesBecomeSpaces() {
        assertEquals("x y", TableGrid.parse(sample)!!.set(1, 1, "x\ny").rows[1][1])
    }

    @Test
    fun addAndDelete() {
        val grid = TableGrid.parse(sample)!!
        val bigger = grid.addRow().addColumn()
        assertEquals(3, bigger.rows.size)
        assertTrue(bigger.rows.all { it.size == 3 })
        assertEquals(3, bigger.aligns.size)
        assertEquals(grid, grid.deleteRow(0)) // the title row stays
        assertEquals(grid, grid.deleteRow(1)) // the last body row stays
        assertEquals(listOf(listOf("b"), listOf("2")), grid.deleteColumn(0).rows)
        val one = grid.deleteColumn(0)
        assertEquals(one, one.deleteColumn(0)) // the last column stays
        assertEquals(listOf(listOf("a", "b"), listOf("1", "2")), bigger.deleteRow(2).deleteColumn(2).rows)
    }

    @Test
    fun emptyIsThreeByThree() {
        val grid = TableGrid.empty()
        assertEquals(3, grid.rows.size)
        assertTrue(grid.rows.all { it.size == 3 && it.all(String::isEmpty) })
        assertTrue(grid.isBlank())
        assertEquals(grid, TableGrid.parse(grid.toMarkdown()))
    }
}
