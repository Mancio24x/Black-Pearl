# Table grid editor — design (short)

## Why
Tables now render like a spreadsheet in the preview (0.8.15), but while writing they are raw Markdown
(`|    |    |`, `|----|`), which the owner finds unusable. He wants to *edit* a table as comfortably as he
reads it, and to add rows and columns with a button.

## Goals / non-goals
- Goal: edit any Markdown table in a real grid of cells, with thin A, B, C / 1, 2, 3 labels, "+ Riga",
  "+ Colonna", delete row/column. The note text stays a plain GFM Markdown table (sync-safe, no new syntax).
- Goal: "Inserisci tabella" opens the grid directly (3 columns × title row + 2 rows), no rows/columns dialog.
- Non-goal: drawing the grid inside the text editor itself; cell formatting; merged cells; formulas beyond 0.8.15.
- Anti-goal: losing what was typed (closing the grid in any way keeps the changes) or touching text outside the table.

## Approaches (owner picked A)
- **A. Grid editor in its own full-screen window (picked)** — real cells, room for the buttons; trade-off: the table
  is edited in a separate window, not inline.
- B. Auto-aligned raw text + row/column commands — still text, wraps badly on a phone.
- C. Grid drawn inside the EditText — fragile (cursor, keyboard, undo); rejected.

## Design
- **Model** `TableGrid` (pure Kotlin, `ui/editor/markdown/`): parse a GFM table block (escaped `\|`, alignment row
  `:--`, `:-:`, `--:` kept, ragged rows padded) → title row + body rows; add/delete row/column; serialize with
  columns padded to equal width (so the raw text is tidy too). Cell newlines become spaces, `|` is escaped.
- **Finder** `MarkdownTables.find(content)`: the tables in a note as source ranges (header line with `|` + delimiter
  row, until a blank line), skipping fenced code blocks. The k-th table found = the k-th table rendered.
- **Window** `TableEditorDialog` (full-screen DialogFragment hosting Compose, `adjustResize`): top bar with close and
  "Fatto"; grid scrolls both ways; title row shaded; thin grey letters above and numbers beside; each cell a one-line
  text field; a formula cell shows its result in small grey under it when not focused (uses `TableFormulas`).
  Long-press on a letter/number → delete that column/row (at least 1 column and 1 body row stay). Bottom buttons
  "+ Riga", "+ Colonna". Input/output via arguments + fragment result (survives screen rotation).
- **Opening it**
  - Preview: tapping a table (a click span over the rendered rows, set in `TableSheetPlugin.afterSetText`, listener
    via a tag on the editor preview only, like images).
  - Edit mode: a tap that lands inside a table's text.
  - "Inserisci tabella" (menu "+" and toolbar): new 3×3 grid, inserted at the cursor (or end) on close if any cell
    has text, or on "Fatto".
- **Saving**: close / back / "Fatto" all save. The result replaces the table only if the note's text at that range is
  still the table that was opened (otherwise nothing is overwritten); goes through `editContent` like image edits.
- Old rows/columns dialog (`InsertTableDialog`) is removed.

## Left to the builder
Exact sizes/colours (match the preview), icons, string wording (Italian + English).
