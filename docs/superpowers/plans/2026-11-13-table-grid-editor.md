# Table Grid Editor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Edit Markdown tables in a full-screen spreadsheet-like grid (add/delete rows and columns), opened by tapping a table or by "Inserisci tabella".

**Architecture:** Pure-Kotlin `TableGrid` (parse/serialize/edit) and `MarkdownTables` (find tables in a note) carry all logic and are unit-tested. A Compose `TableEditorDialog` edits a `TableGrid` and returns Markdown through a fragment result. `EditorFragment` opens it from a preview click span, an edit-mode tap, or the insert action, and writes back through `editContent`.

**Tech Stack:** Kotlin, Compose Material 3 in a `DialogFragment`, Markwon 4.6.2 (TablePlugin `TableRowSpan`), JUnit.

**Spec:** `docs/superpowers/specs/2026-11-13-table-grid-editor-design.md`

## Global Constraints
- Note text stays a plain GFM table; no new syntax; nothing outside the table's range changes.
- No Room schema change.
- Strings in `values/strings_blackpearl.xml` (English) and `values-it/strings_blackpearl.xml` (Italian).
- Closing the grid in any way saves; a new table is inserted only if some cell has text.

## Review Focus
- A `|` typed in a cell → stored as `\|`, read back as `|` (TableGridTest.pipeRoundTrips).
- A table inside a ``` fenced block is not a table (MarkdownTablesTest.skipsFencedCode).
- Ragged rows (fewer/more cells than the title row) → padded/trimmed to the title width, no crash (TableGridTest.raggedRows).
- Note text changed between opening and closing the grid → the old range no longer holds the table → nothing written (MarkdownTablesTest.replaceOnlyIfUnchanged).
- Tables wider than the preview limit (15 columns) are not rendered as tables → indexes skip them (MarkdownTablesTest.indexSkipsTooWide).

---

### Task 1: TableGrid model
**Files:** Create `app/src/main/java/org/qosp/notes/ui/editor/markdown/TableGrid.kt`, Test `app/src/test/java/org/qosp/notes/ui/editor/markdown/TableGridTest.kt`

**Produces:**
`data class TableGrid(val rows: List<List<String>>, val aligns: List<Align>)` (row 0 = titles; every row has `aligns.size` cells),
`enum class Align { NONE, LEFT, CENTER, RIGHT }`,
`companion fun parse(markdown: String): TableGrid?` (null if not a table), `companion fun empty(columns: Int = 3, bodyRows: Int = 2)`,
`fun toMarkdown(): String` (ends with `\n`; columns padded to equal width, min 3), `fun set(r, c, text)`, `addRow()`, `addColumn()`, `deleteRow(r)` (r ≥ 1, keeps ≥ 1 body row), `deleteColumn(c)` (keeps ≥ 1 column), all returning a new `TableGrid`.

- [ ] Tests: `parsesTitleAndRows` (`| a | b |\n|---|:-:|\n| 1 | 2 |` → rows `[[a,b],[1,2]]`, aligns `[NONE, CENTER]`); `serializesPadded` (`toMarkdown()` of that grid == `"| a   | b   |\n| --- | :-: |\n| 1   | 2   |\n"`); `pipeRoundTrips` (`set(1,0,"x|y")` → markdown contains `x\|y`, parse back gives `x|y`); `raggedRows`; `newlinesBecomeSpaces`; `addAndDelete` (addRow/addColumn sizes; deleteRow(0) and deleting the last body row/column are no-ops); `emptyIsThreeByThree`.
- [ ] Run `./gradlew testDebugUnitTest --tests '*TableGridTest*'` → FAIL, implement, → PASS. Commit `feat: TableGrid model`.

### Task 2: MarkdownTables finder
**Files:** Create `.../markdown/MarkdownTables.kt`, Test `.../markdown/MarkdownTablesTest.kt`

**Produces:** `object MarkdownTables { fun find(content: String, maxColumns: Int = Int.MAX_VALUE): List<IntRange> }` (ranges cover whole lines incl. trailing `\n` if present; a table = a line with `|` followed by a delimiter row, continuing until a blank line; tables wider than `maxColumns` skipped; fenced code skipped), `fun at(content: String, offset: Int): IntRange?`, `fun replace(content: String, range: IntRange, expected: String, markdown: String): String?` (null if `content.substring(range) != expected`).

- [ ] Tests: `findsTwoTables`, `skipsFencedCode`, `indexSkipsTooWide`, `atOffset` (offset in second table → its range; offset in prose → null), `replaceOnlyIfUnchanged`.
- [ ] FAIL → implement → PASS. Commit `feat: find tables in note text`.

### Task 3: Preview click target
**Files:** Modify `.../markdown/TableSheetPlugin.kt`

**Produces:** `class TableClickSpan(val index: Int) : ClickableSpan` (no link styling) + `fun interface TableClickListener { fun onTableClick(index: Int) }` read from tag `R.id.tag_table_click_listener` (add id in `res/values/ids.xml` or wherever `tag_image_click_listener` is declared). `afterSetText(textView)`: collect `io.noties.markwon.ext.tables.TableRowSpan`s, sort by start, group rows separated only by `\n`, set one `TableClickSpan(k)` per group (k = order). Only if the text is `Spannable`.
- [ ] Build `./gradlew compileDebugKotlin`. Commit with Task 5.

### Task 4: TableEditorDialog
**Files:** Create `app/src/main/java/org/qosp/notes/ui/editor/dialog/TableEditorDialog.kt`; strings.

**Consumes:** `TableGrid`. **Produces:** `TableEditorDialog.newInstance(markdown: String?, start: Int)` (null = new table); result key `TableEditorDialog.RESULT` with bundle `markdown`, `original` (String?), `start` (Int).
- Full-screen dialog (theme `Theme.Material3.DayNight.NoActionBar`-like full screen, `SOFT_INPUT_ADJUST_RESIZE`), Compose content in `QuillpadTheme`. Grid state in `rememberSaveable` as the markdown string.
- Top bar: close icon + title "Tabella" + "Fatto" text button. Both, back and dismiss all call `finish()` once: emit result if grid changed (existing) or any cell non-blank (new).
- Grid: horizontal + vertical scroll; label row of letters (`TableFormulas.columnName`), label column of numbers, 11 sp grey `0x8C888888`; cells 120 dp wide, 1 dp outline border, title row shaded `surfaceVariant`; `BasicTextField` single line; unfocused formula cell shows `TableFormulas.evaluate(rows)[r][c]` small and grey under the text.
- Long press on letter/number → small `DropdownMenu` "Elimina colonna"/"Elimina riga".
- Bottom: `FilledTonalButton` "+ Riga", "+ Colonna".
- Strings: `table_editor_title` Tabella/Table, `table_add_row` Riga/Row, `table_add_column` Colonna/Column, `table_delete_row` Elimina riga/Delete row, `table_delete_column` Elimina colonna/Delete column, `action_done` (reuse if present).

### Task 5: Wire into EditorFragment
**Files:** Modify `EditorFragment.kt` (insert-table action ~1513, setup near `setupImageLayout`), delete `InsertTableDialog.kt` + its layout + `tableMarkdown` if unused.
- `openTableEditor(index or range)`: preview listener gives index → `MarkdownTables.find(content, 15)[index]`; edit mode: `GestureDetector.onSingleTapUp` on `editTextContent`, post → `MarkdownTables.at(content, selectionStart)`; insert action → `newInstance(null, cursor or end)`.
- Result: existing table → `editContent { MarkdownTables.replace(it, start until start+original.length, original, markdown) ?: it }`; new → insert at `start` (clamped), prefixed with `\n` if not at line start, then a `\n` if next char isn't one.
- [ ] Build debug, run Task 1–2 tests + `TableSheetTest`. Update `docs/CODEMAP.md`, `docs/CHANGELOG-AGENTS.md`, `scripts/capture-ui.sh` (screenshot of grid if feasible). Commit `feat: table grid editor`, push, PR.
