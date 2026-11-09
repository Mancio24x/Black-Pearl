package org.qosp.notes.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.qosp.notes.R
import org.qosp.notes.data.model.Note
import org.qosp.notes.ui.common.QuickActions
import org.qosp.notes.ui.common.SheetAction
import org.qosp.notes.ui.common.SheetChoice
import org.qosp.notes.ui.common.SheetExpandable
import org.qosp.notes.ui.common.SheetGroup
import org.qosp.notes.ui.common.SheetRow
import org.qosp.notes.ui.common.SheetSwitch
import org.qosp.notes.ui.common.SheetTitle
import org.qosp.notes.ui.editor.markdown.ImageBlocks

/**
 * Black Pearl: everything that was in the editor's long "⋮" list, in one sheet. Add / color / tags / share / export
 * are round buttons, a few rows follow, rarely used switches are folded under "More settings", Delete is last.
 * [onAction] runs an editor action (menu id) and closes the sheet; [onSwitch] sets an option and keeps it open.
 */
@Composable
fun NoteOptionsSheet(note: Note, onAction: (Int) -> Unit, onSwitch: (id: Int, on: Boolean) -> Unit) {
    SheetTitle(note.title.ifBlank { stringResource(R.string.indicator_untitled) })

    if (note.isDeleted) {
        SheetGroup {
            SheetRow(R.drawable.ic_restore, stringResource(R.string.action_restore)) { onAction(R.id.action_restore_note) }
        }
        DeleteRow(stringResource(R.string.action_delete_permanently)) { onAction(R.id.action_delete_permanently_note) }
        return
    }

    QuickActions(
        listOf(
            SheetAction(R.drawable.ic_camera, stringResource(R.string.sheet_photo)) { onAction(R.id.action_take_photo) },
            SheetAction(R.drawable.ic_file, stringResource(R.string.sheet_file)) { onAction(R.id.action_attach_file) },
            SheetAction(R.drawable.ic_microphone, stringResource(R.string.sheet_audio)) { onAction(R.id.action_record_audio) },
            SheetAction(R.drawable.ic_scan_text, stringResource(R.string.ocr_scan_text)) { onAction(R.id.action_scan_text) },
        )
    )
    QuickActions(
        listOf(
            SheetAction(R.drawable.ic_color, stringResource(R.string.sheet_color)) { onAction(R.id.action_change_color) },
            SheetAction(R.drawable.ic_tag, stringResource(R.string.nav_tags)) { onAction(R.id.action_view_tags) },
            SheetAction(R.drawable.ic_share, stringResource(R.string.action_share)) { onAction(R.id.action_share) },
            SheetAction(R.drawable.ic_export_note, stringResource(R.string.action_export)) { onAction(R.id.action_export_options) },
        )
    )

    SheetGroup {
        SheetRow(
            if (note.isList) R.drawable.ic_notes else R.drawable.ic_box_checked_outline,
            stringResource(if (note.isList) R.string.action_convert_to_note else R.string.action_convert_to_list),
        ) { onAction(R.id.action_convert_note) }
        if (note.isList) {
            SheetRow(R.drawable.ic_box, stringResource(R.string.action_uncheck_all_tasks)) { onAction(R.id.action_uncheck_all_tasks) }
            SheetRow(R.drawable.ic_box_checked, stringResource(R.string.action_remove_all_checked_tasks)) {
                onAction(R.id.action_remove_all_checked_tasks)
            }
        }
        SheetRow(R.drawable.ic_archive, stringResource(if (note.isArchived) R.string.action_unarchive else R.string.action_archive)) {
            onAction(R.id.action_archive_note)
        }
    }

    SheetExpandable(stringResource(R.string.sheet_more_settings)) {
        var markdown by remember { mutableStateOf(note.isMarkdownEnabled) }
        var hidden by remember { mutableStateOf(note.isHidden) }
        var screenOn by remember { mutableStateOf(note.screenAlwaysOn) }
        var localOnly by remember { mutableStateOf(note.isLocalOnly) }
        SheetSwitch(R.drawable.ic_markdown, stringResource(R.string.option_markdown), markdown) {
            markdown = it
            onSwitch(R.id.action_enable_disable_markdown, it)
        }
        SheetSwitch(R.drawable.ic_hidden, stringResource(R.string.option_hidden), hidden) {
            hidden = it
            onSwitch(R.id.action_hide_note, it)
        }
        SheetSwitch(R.drawable.ic_screen_on, stringResource(R.string.action_screen_always_on), screenOn) {
            screenOn = it
            onSwitch(R.id.action_screen_always_on, it)
        }
        SheetSwitch(R.drawable.ic_sync, stringResource(R.string.action_do_not_sync), localOnly) {
            localOnly = it
            onSwitch(R.id.action_do_not_sync, it)
        }
    }

    DeleteRow(stringResource(R.string.action_delete)) { onAction(R.id.action_delete_note) }
}

@Composable
private fun DeleteRow(label: String, onClick: () -> Unit) {
    Column(Modifier.padding(horizontal = 10.dp, vertical = 4.dp)) {
        SheetRow(R.drawable.ic_bin, label, color = MaterialTheme.colorScheme.error, onClick = onClick)
    }
}

/**
 * Black Pearl: a picture placed in the text. Alignment and size are segmented choices applied at once (the sheet
 * stays open to try them), then move and remove.
 */
@Composable
fun ImageOptionsSheet(
    layout: ImageBlocks.Layout,
    onLayout: (ImageBlocks.Layout) -> Unit,
    onMove: (up: Boolean) -> Unit,
    onRemove: () -> Unit,
) {
    var current by remember { mutableStateOf(layout) }
    fun update(new: ImageBlocks.Layout) {
        current = new
        onLayout(new)
    }
    SheetTitle(stringResource(R.string.image_actions_title))

    val aligns = listOf(ImageBlocks.Align.LEFT, ImageBlocks.Align.CENTER, ImageBlocks.Align.RIGHT)
    SheetChoice(
        stringResource(R.string.image_alignment),
        listOf(
            stringResource(R.string.image_align_left) to R.drawable.ic_align_left,
            stringResource(R.string.image_align_center) to R.drawable.ic_align_center,
            stringResource(R.string.image_align_right) to R.drawable.ic_align_right,
        ),
        aligns.indexOf(current.align),
    ) { update(current.copy(align = aligns[it])) }

    val sizes = listOf(25, 50, 75, null)
    SheetChoice(
        stringResource(R.string.image_size),
        sizes.map { (it ?: 100).toString() + "%" to null },
        sizes.indexOf(current.widthPercent).coerceAtLeast(0),
    ) { update(current.copy(widthPercent = sizes[it])) }

    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        listOf(true to R.string.image_move_up, false to R.string.image_move_down).forEach { (up, label) ->
            FilledTonalButton(onClick = { onMove(up) }, modifier = Modifier.weight(1f)) {
                Icon(painterResource(if (up) R.drawable.ic_up else R.drawable.ic_down), null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(label))
            }
        }
    }
    DeleteRow(stringResource(R.string.image_remove_from_note), onRemove)
}
