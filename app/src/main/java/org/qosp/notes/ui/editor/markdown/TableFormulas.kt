package org.qosp.notes.ui.editor.markdown

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Black Pearl: spreadsheet formulas in Markdown tables, computed only for display (the note text keeps the formula).
 *
 * A cell whose text starts with `=` is a formula: numbers, cell references like `B2`, `+ - * /` (also `×` `÷` `x`),
 * parentheses and unary minus. Columns are A, B, C… and rows 1, 2, 3… counting the header row as 1, like a sheet whose
 * first row holds the titles. Numbers may use a comma or a point for decimals. Results keep the decimal separator
 * the table uses (comma when any number in it has one). Errors show `#ERRORE`, division by zero `#DIV/0`.
 */
object TableFormulas {
    const val ERROR = "#ERRORE"
    const val DIV_ZERO = "#DIV/0"

    /** Column letters for index 0, 1, … : A … Z, AA, AB … */
    fun columnName(index: Int): String {
        var i = index
        val sb = StringBuilder()
        do {
            sb.insert(0, 'A' + i % 26)
            i = i / 26 - 1
        } while (i >= 0)
        return sb.toString()
    }

    fun isFormula(text: String): Boolean = text.trimStart().startsWith("=")

    /**
     * [grid] holds the raw cell texts, row by row (row 0 = the header). Returns the same grid with every formula
     * replaced by its result; other cells are unchanged.
     */
    fun evaluate(grid: List<List<String>>): List<List<String>> {
        if (grid.none { row -> row.any(::isFormula) }) return grid
        val comma = grid.any { row -> row.any { !isFormula(it) && NUMBER_WITH_COMMA.containsMatchIn(it) } }
        val sheet = Sheet(grid)
        return grid.mapIndexed { r, row ->
            row.mapIndexed { c, text ->
                if (!isFormula(text)) text else sheet.display(r, c, comma)
            }
        }
    }

    private val NUMBER_WITH_COMMA = Regex("""\d,\d""")

    private class Failure(val shown: String) : Exception(shown)

    private class Sheet(val grid: List<List<String>>) {
        private val values = HashMap<Pair<Int, Int>, BigDecimal>()
        private val visiting = HashSet<Pair<Int, Int>>()

        fun display(r: Int, c: Int, comma: Boolean): String = try {
            format(value(r, c), comma)
        } catch (e: Failure) {
            e.shown
        }

        /** Numeric value of a cell: a formula's result, a number, or 0 when empty. */
        fun value(r: Int, c: Int): BigDecimal {
            val key = r to c
            values[key]?.let { return it }
            val text = grid.getOrNull(r)?.getOrNull(c) ?: throw Failure(ERROR)
            if (!visiting.add(key)) throw Failure(ERROR) // a formula that refers to itself
            try {
                val v = if (isFormula(text)) Parser(text.trimStart().substring(1), this).parse() else number(text)
                values[key] = v
                return v
            } finally {
                visiting.remove(key)
            }
        }

        private fun number(text: String): BigDecimal {
            val t = text.trim().removeSuffix("€").removePrefix("€").replace(" ", "").replace(" ", "")
            if (t.isEmpty()) return BigDecimal.ZERO
            return parseNumber(t) ?: throw Failure(ERROR)
        }
    }

    /** "1,5" / "1.5" / "1.234,5" / "1,234.5" → BigDecimal, else null. */
    internal fun parseNumber(text: String): BigDecimal? {
        val t = text.trim()
        if (!Regex("""[-+]?[\d.,]*\d[\d.,]*""").matches(t)) return null
        val lastComma = t.lastIndexOf(',')
        val lastDot = t.lastIndexOf('.')
        val normalized = when {
            lastComma >= 0 && lastDot >= 0 ->
                if (lastComma > lastDot) t.replace(".", "").replace(',', '.') else t.replace(",", "")
            lastComma >= 0 -> if (t.count { it == ',' } == 1) t.replace(',', '.') else t.replace(",", "")
            else -> if (t.count { it == '.' } > 1) t.replace(".", "") else t
        }
        return normalized.toBigDecimalOrNull()
    }

    private fun format(v: BigDecimal, comma: Boolean): String {
        val rounded = v.setScale(10, RoundingMode.HALF_UP).stripTrailingZeros()
        val plain = (if (rounded.signum() == 0) BigDecimal.ZERO else rounded).toPlainString()
        return if (comma) plain.replace('.', ',') else plain
    }

    /** Recursive descent: expr = term (('+'|'-') term)*; term = factor (('*'|'/') factor)*; factor = ±factor | atom. */
    private class Parser(val src: String, val sheet: Sheet) {
        private var i = 0

        fun parse(): BigDecimal {
            val v = expr()
            skip()
            if (i != src.length) throw Failure(ERROR)
            return v
        }

        private fun skip() {
            while (i < src.length && src[i].isWhitespace()) i++
        }

        private fun peek(): Char? {
            skip()
            return src.getOrNull(i)
        }

        private fun expr(): BigDecimal {
            var v = term()
            while (true) {
                when (peek()) {
                    '+' -> { i++; v = v.add(term()) }
                    '-', '−' -> { i++; v = v.subtract(term()) }
                    else -> return v
                }
            }
        }

        private fun term(): BigDecimal {
            var v = factor()
            while (true) {
                when (peek()) {
                    '*', '×', 'x' -> {
                        // "x" is a multiplication only between operands (a cell reference would start with a letter)
                        i++; v = v.multiply(factor())
                    }
                    '/', '÷', ':' -> {
                        i++
                        val d = factor()
                        if (d.signum() == 0) throw Failure(DIV_ZERO)
                        v = v.divide(d, 12, RoundingMode.HALF_UP)
                    }
                    else -> return v
                }
            }
        }

        private fun factor(): BigDecimal {
            return when (peek()) {
                '-', '−' -> { i++; factor().negate() }
                '+' -> { i++; factor() }
                '(' -> {
                    i++
                    val v = expr()
                    if (peek() != ')') throw Failure(ERROR)
                    i++
                    v
                }
                null -> throw Failure(ERROR)
                else -> atom()
            }
        }

        private fun atom(): BigDecimal {
            skip()
            val start = i
            if (i < src.length && src[i].isLetter()) {
                while (i < src.length && src[i].isLetter()) i++
                val letters = src.substring(start, i).uppercase()
                val digitsStart = i
                while (i < src.length && src[i].isDigit()) i++
                if (digitsStart == i) throw Failure(ERROR)
                val row = src.substring(digitsStart, i).toInt() - 1
                var col = 0
                for (ch in letters) col = col * 26 + (ch - 'A' + 1)
                return sheet.value(row, col - 1)
            }
            while (i < src.length && (src[i].isDigit() || src[i] == ',' || src[i] == '.')) i++
            return parseNumber(src.substring(start, i)) ?: throw Failure(ERROR)
        }
    }
}
