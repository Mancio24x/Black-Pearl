package org.qosp.notes.ui.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.qosp.notes.R
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.ui.BaseActivity
import org.qosp.notes.ui.common.SheetGroup
import org.qosp.notes.ui.common.SheetRow
import org.qosp.notes.ui.common.SheetTitle
import org.qosp.notes.ui.theme.QuillpadTheme

/**
 * Shown when the checklist widget is added (or reconfigured): pick which list it shows.
 * A BaseActivity, so the app lock protects it like the rest of the app.
 */
class ChecklistWidgetConfigureActivity : BaseActivity() {
    private val notes: NoteRepository by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val widgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Back without choosing: no widget is added
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) return finish()

        val lists = notes.getNonDeleted().map { all -> all.filter { it.isList } }
        setContent {
            QuillpadTheme {
                Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                    val items by lists.collectAsState(initial = emptyList())
                    Column(Modifier.systemBarsPadding().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                        SheetTitle(stringResource(R.string.checklist_widget_choose))
                        if (items.isEmpty()) {
                            Text(
                                stringResource(R.string.checklist_widget_no_lists),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                            )
                        } else {
                            SheetGroup {
                                items.forEach { note ->
                                    val done = note.taskList.count { it.isDone }
                                    SheetRow(
                                        R.drawable.ic_box_checked_outline,
                                        "${note.title.ifBlank { getString(R.string.indicator_untitled) }}  ·  $done/${note.taskList.size}",
                                    ) { choose(widgetId, note.id) }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun choose(widgetId: Int, noteId: Long) {
        ChecklistWidgets.setNoteId(this, widgetId, noteId)
        lifecycleScope.launch {
            val note = notes.getById(noteId).first()
            ChecklistWidgets.update(this@ChecklistWidgetConfigureActivity, AppWidgetManager.getInstance(this@ChecklistWidgetConfigureActivity), widgetId, note)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId))
            finish()
        }
    }
}
