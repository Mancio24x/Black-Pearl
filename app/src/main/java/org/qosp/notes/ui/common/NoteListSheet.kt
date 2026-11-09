package org.qosp.notes.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.qosp.notes.R
import org.qosp.notes.data.model.Note

enum class NoteListAction { PIN, ARCHIVE, MOVE, SHARE, DUPLICATE, EXPORT, SELECT, DELETE, RESTORE, DELETE_FOREVER }

enum class NoteListOption { HIDDEN, COMPACT, SCREEN_ON, MARKDOWN }

/**
 * Black Pearl: long press on a note in a list. Same layout as the editor's options: round buttons, a short
 * rounded group, rarely used switches folded under "More settings", Delete last (was a list of up to 13 rows).
 */
@Composable
fun NoteListSheet(
    note: Note,
    canSelect: Boolean,
    onAction: (NoteListAction) -> Unit,
    onSwitch: (NoteListOption, Boolean) -> Unit,
) {
    SheetTitle(note.title.ifBlank { stringResource(R.string.indicator_untitled) })

    if (note.isDeleted) {
        SheetGroup {
            SheetRow(R.drawable.ic_restore, stringResource(R.string.action_restore)) { onAction(NoteListAction.RESTORE) }
            SheetRow(R.drawable.ic_share, stringResource(R.string.action_share)) { onAction(NoteListAction.SHARE) }
        }
        DangerRow(stringResource(R.string.action_delete_permanently)) { onAction(NoteListAction.DELETE_FOREVER) }
        return
    }

    val normal = !note.isArchived
    QuickActions(
        buildList {
            if (normal) {
                add(
                    SheetAction(
                        if (note.isPinned) R.drawable.ic_unpin else R.drawable.ic_pin,
                        stringResource(if (note.isPinned) R.string.action_unpin else R.string.action_pin),
                    ) { onAction(NoteListAction.PIN) }
                )
            }
            add(
                SheetAction(
                    if (note.isArchived) R.drawable.ic_unarchive else R.drawable.ic_archive_action,
                    stringResource(if (note.isArchived) R.string.action_unarchive else R.string.action_archive),
                ) { onAction(NoteListAction.ARCHIVE) }
            )
            if (normal) add(SheetAction(R.drawable.ic_notebook_swap, stringResource(R.string.action_move_to)) { onAction(NoteListAction.MOVE) })
            add(SheetAction(R.drawable.ic_share, stringResource(R.string.action_share)) { onAction(NoteListAction.SHARE) })
        }
    )

    SheetGroup {
        if (normal) SheetRow(R.drawable.ic_duplicate, stringResource(R.string.action_duplicate)) { onAction(NoteListAction.DUPLICATE) }
        SheetRow(R.drawable.ic_export_note, stringResource(R.string.action_export)) { onAction(NoteListAction.EXPORT) }
        if (canSelect) SheetRow(R.drawable.ic_select_more, stringResource(R.string.action_select_more)) { onAction(NoteListAction.SELECT) }
    }

    SheetExpandable(stringResource(R.string.sheet_more_settings)) {
        var hidden by remember { mutableStateOf(note.isHidden) }
        var compact by remember { mutableStateOf(note.isCompactPreview) }
        var screenOn by remember { mutableStateOf(note.screenAlwaysOn) }
        var markdown by remember { mutableStateOf(note.isMarkdownEnabled) }
        SheetSwitch(R.drawable.ic_hidden, stringResource(R.string.option_hidden), hidden) {
            hidden = it
            onSwitch(NoteListOption.HIDDEN, it)
        }
        SheetSwitch(R.drawable.ic_preview, stringResource(R.string.action_compact_preview), compact) {
            compact = it
            onSwitch(NoteListOption.COMPACT, it)
        }
        SheetSwitch(R.drawable.ic_screen_on, stringResource(R.string.action_screen_always_on), screenOn) {
            screenOn = it
            onSwitch(NoteListOption.SCREEN_ON, it)
        }
        SheetSwitch(R.drawable.ic_markdown, stringResource(R.string.option_markdown), markdown) {
            markdown = it
            onSwitch(NoteListOption.MARKDOWN, it)
        }
    }

    DangerRow(stringResource(R.string.action_delete)) { onAction(NoteListAction.DELETE) }
}

/** Red row for destructive actions, aligned with the rounded groups. */
@Composable
fun DangerRow(label: String, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
        SheetRow(R.drawable.ic_bin, label, color = MaterialTheme.colorScheme.error, onClick = onClick)
    }
}
