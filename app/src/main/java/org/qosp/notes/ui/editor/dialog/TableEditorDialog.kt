package org.qosp.notes.ui.editor.dialog

import android.content.DialogInterface
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.os.bundleOf
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.setFragmentResult
import org.qosp.notes.R
import org.qosp.notes.ui.editor.markdown.TableFormulas
import org.qosp.notes.ui.editor.markdown.TableGrid
import org.qosp.notes.ui.theme.QuillpadTheme

/**
 * Black Pearl: a Markdown table edited as a spreadsheet grid, full screen. Thin letters (A, B, C) above the columns,
 * thin numbers beside the rows, "+ Row" / "+ Column", long press on a letter or number to delete. Closing it in
 * any way (Done, ✕, back) sends the table as Markdown in [RESULT]; nothing is sent if nothing changed, or for a new
 * table left empty. The editor replaces the table only if the note still holds [ARG_MARKDOWN] at [ARG_START].
 */
class TableEditorDialog : DialogFragment() {
    private var latest: TableGrid? = null
    private var sent = false

    private val original: String? get() = arguments?.getString(ARG_MARKDOWN)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, 0)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                QuillpadTheme {
                    var grid by rememberSaveable(stateSaver = GridSaver) {
                        mutableStateOf(original?.let(TableGrid::parse) ?: TableGrid.empty())
                    }
                    latest = grid
                    TableEditor(
                        grid = grid,
                        onChange = { grid = it; latest = it },
                        onClose = { dismissAllowingStateLoss() },
                    )
                }
            }
        }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
    }

    override fun onDismiss(dialog: DialogInterface) {
        sendResult()
        super.onDismiss(dialog)
    }

    private fun sendResult() {
        if (sent) return
        val grid = latest ?: return
        val before = original
        val changed = if (before == null) !grid.isBlank() else grid != TableGrid.parse(before)
        if (!changed || isStateSaved) return
        sent = true
        setFragmentResult(
            RESULT,
            bundleOf(
                ARG_MARKDOWN to grid.toMarkdown(),
                ARG_ORIGINAL to before,
                ARG_START to (arguments?.getInt(ARG_START) ?: -1),
            )
        )
    }

    companion object {
        const val RESULT = "table_editor_result"
        const val ARG_MARKDOWN = "markdown"
        const val ARG_ORIGINAL = "original"
        const val ARG_START = "start"

        /** [markdown] null = a new table, to insert at [start] (-1 = at the cursor or the end). */
        fun newInstance(markdown: String?, start: Int) = TableEditorDialog().apply {
            arguments = bundleOf(ARG_MARKDOWN to markdown, ARG_START to start)
        }

        private val GridSaver = Saver<TableGrid, String>(save = { it.toMarkdown() }, restore = { TableGrid.parse(it) })
    }
}

private val CELL_WIDTH = 120.dp
private val LABEL_WIDTH = 28.dp
private val LABEL_COLOR = androidx.compose.ui.graphics.Color(0x8C888888)

@Composable
private fun TableEditor(grid: TableGrid, onChange: (TableGrid) -> Unit, onClose: () -> Unit) {
    var focused by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val results = remember(grid) { TableFormulas.evaluate(grid.rows) }
    val border = MaterialTheme.colorScheme.outlineVariant

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.fillMaxWidth().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(painterResource(R.drawable.ic_close), stringResource(R.string.action_close_table)) }
                Text(stringResource(R.string.table_editor_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = onClose) { Text(stringResource(R.string.table_editor_done)) }
            }

            Box(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()).horizontalScroll(rememberScrollState())) {
                Column(Modifier.padding(start = 4.dp, end = 16.dp, bottom = 16.dp)) {
                    Row {
                        Spacer(Modifier.width(LABEL_WIDTH))
                        for (c in 0 until grid.columns) {
                            Label(
                                text = TableFormulas.columnName(c),
                                deleteText = stringResource(R.string.table_delete_column),
                                canDelete = grid.columns > 1,
                                onDelete = { onChange(grid.deleteColumn(c)) },
                                modifier = Modifier.width(CELL_WIDTH).padding(start = 8.dp, bottom = 2.dp),
                            )
                        }
                    }
                    grid.rows.forEachIndexed { r, row ->
                        Row(Modifier.height(IntrinsicSize.Min)) {
                            Label(
                                text = "${r + 1}",
                                deleteText = stringResource(R.string.table_delete_row),
                                canDelete = r > 0 && grid.rows.size > 2,
                                onDelete = { onChange(grid.deleteRow(r)) },
                                modifier = Modifier.width(LABEL_WIDTH).fillMaxHeight().padding(top = 12.dp),
                            )
                            row.forEachIndexed { c, value ->
                                Cell(
                                    value = value,
                                    title = r == 0,
                                    result = results[r][c].takeIf { TableFormulas.isFormula(value) && focused != r to c },
                                    onValue = { onChange(grid.set(r, c, it)) },
                                    onFocus = { has -> if (has) focused = r to c else if (focused == r to c) focused = null },
                                    modifier = Modifier.width(CELL_WIDTH).fillMaxHeight().border(0.5.dp, border)
                                        .background(if (r == 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface),
                                )
                            }
                        }
                    }
                }
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                FilledTonalButton(onClick = { onChange(grid.addRow()) }, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
                    Text(stringResource(R.string.table_add_row), Modifier.padding(start = 8.dp))
                }
                FilledTonalButton(onClick = { onChange(grid.addColumn()) }, modifier = Modifier.weight(1f)) {
                    Icon(painterResource(R.drawable.ic_add), null, Modifier.size(18.dp))
                    Text(stringResource(R.string.table_add_column), Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}

/** A thin grey column letter or row number; a long press offers to delete that column or row. */
@Composable
private fun Label(text: String, deleteText: String, canDelete: Boolean, onDelete: () -> Unit, modifier: Modifier) {
    var menu by remember { mutableStateOf(false) }
    Box(modifier.combinedClickable(enabled = canDelete, onClick = { menu = true }, onLongClick = { menu = true })) {
        Text(text, fontSize = 11.sp, color = LABEL_COLOR, maxLines = 1)
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
            DropdownMenuItem(
                text = { Text(deleteText) },
                leadingIcon = { Icon(painterResource(R.drawable.ic_bin), null) },
                onClick = { menu = false; onDelete() },
            )
        }
    }
}

@Composable
private fun Cell(
    value: String,
    title: Boolean,
    result: String?,
    onValue: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    modifier: Modifier,
) {
    val color = MaterialTheme.colorScheme.onSurface
    Column(modifier.padding(horizontal = 8.dp, vertical = 10.dp)) {
        BasicTextField(
            value = value,
            onValueChange = onValue,
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(
                color = color,
                fontWeight = if (title) FontWeight.Bold else FontWeight.Normal,
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            modifier = Modifier.fillMaxWidth().onFocusChanged { onFocus(it.isFocused) },
        )
        if (result != null) {
            Text(result, fontSize = 12.sp, color = LABEL_COLOR, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}
