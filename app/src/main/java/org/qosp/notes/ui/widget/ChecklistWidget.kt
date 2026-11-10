package org.qosp.notes.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Paint
import android.os.Build
import android.widget.RemoteViews
import android.widget.RemoteViewsService
import androidx.core.content.edit
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.R
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.ui.MainActivity
import org.qosp.notes.ui.lock.AppLock

/**
 * Black Pearl checklist widget: one list note on the home screen, items ticked straight from there.
 * The list is chosen when the widget is added (ChecklistWidgetConfigureActivity) and stored per widget id.
 */
object ChecklistWidgets {
    private const val PREFS = "checklist_widgets"
    const val ACTION_TOGGLE = "io.github.mancio24x.blackpearl.CHECKLIST_TOGGLE"
    const val EXTRA_NOTE_ID = "noteId"
    const val EXTRA_TASK_ID = "taskId"

    fun noteId(context: Context, widgetId: Int): Long =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getLong("note_$widgetId", -1L)

    fun setNoteId(context: Context, widgetId: Int, noteId: Long) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putLong("note_$widgetId", noteId) }

    fun forget(context: Context, widgetIds: IntArray) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { widgetIds.forEach { remove("note_$it") } }

    /** Ticks / unticks one item of a list note; false if the note is gone. */
    suspend fun toggle(notes: NoteRepository, noteId: Long, taskId: Long): Boolean {
        val note = notes.getById(noteId).first() ?: return false
        val tasks = note.taskList.map { if (it.id == taskId) it.copy(isDone = !it.isDone) else it }
        notes.updateNotes(note.copy(taskList = tasks, modifiedDate = java.time.Instant.now().epochSecond))
        return true
    }

    fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context).getAppWidgetIds(ComponentName(context, ChecklistWidgetProvider::class.java))

    /** Draws the widget frame (title, list adapter, clicks) for [widgetId]. */
    fun update(context: Context, manager: AppWidgetManager, widgetId: Int, note: Note?) {
        val views = RemoteViews(context.packageName, R.layout.widget_checklist)
        val hidden = AppLock.hidesNotesOutsideTheApp()
        val title = when {
            hidden -> context.getString(R.string.app_name)
            note == null -> context.getString(R.string.checklist_widget_missing)
            else -> note.title.ifBlank { context.getString(R.string.indicator_untitled) }
        }
        views.setTextViewText(R.id.widget_checklist_title, title)
        if (note != null && !hidden) {
            val done = note.taskList.count { it.isDone }
            views.setTextViewText(R.id.widget_checklist_count, "$done/${note.taskList.size}")
        } else {
            views.setTextViewText(R.id.widget_checklist_count, "")
        }

        val service = Intent(context, ChecklistWidgetService::class.java).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            data = toUri(Intent.URI_INTENT_SCHEME).toUri()
        }
        views.setRemoteAdapter(R.id.widget_checklist_items, service)
        views.setEmptyView(R.id.widget_checklist_items, R.id.widget_checklist_empty)
        views.setTextViewText(
            R.id.widget_checklist_empty,
            context.getString(
                when {
                    hidden -> R.string.lock_widget_hidden
                    note == null -> R.string.checklist_widget_missing
                    else -> R.string.checklist_widget_empty
                }
            ),
        )

        // Title: opens the list in the app
        val open = Intent(context, MainActivity::class.java).apply {
            putExtra(EXTRA_NOTE_ID, note?.id ?: -1L)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        views.setOnClickPendingIntent(
            R.id.widget_checklist_header,
            PendingIntent.getActivity(context, widgetId, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE),
        )
        // Items: a tick goes to the provider (fill-in intents need a mutable template on Android 12+)
        val toggle = Intent(context, ChecklistWidgetProvider::class.java).apply { action = ACTION_TOGGLE }
        val mutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0
        views.setPendingIntentTemplate(
            R.id.widget_checklist_items,
            PendingIntent.getBroadcast(context, widgetId, toggle, PendingIntent.FLAG_UPDATE_CURRENT or mutable),
        )
        manager.updateAppWidget(widgetId, views)
        manager.notifyAppWidgetViewDataChanged(widgetId, R.id.widget_checklist_items)
    }
}

class ChecklistWidgetProvider : AppWidgetProvider(), KoinComponent {
    private val notes: NoteRepository by inject()

    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { id ->
                    val note = notes.getById(ChecklistWidgets.noteId(context, id)).first()?.takeIf { !it.isDeleted }
                    ChecklistWidgets.update(context, manager, id, note)
                }
            } finally {
                pending?.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) = ChecklistWidgets.forget(context, appWidgetIds)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ChecklistWidgets.ACTION_TOGGLE) return super.onReceive(context, intent)
        if (AppLock.hidesNotesOutsideTheApp()) return
        val noteId = intent.getLongExtra(ChecklistWidgets.EXTRA_NOTE_ID, -1L)
        val taskId = intent.getLongExtra(ChecklistWidgets.EXTRA_TASK_ID, -1L)
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (ChecklistWidgets.toggle(notes, noteId, taskId)) WidgetUpdateHelper.updateAllWidgets(context)
            } finally {
                pending?.finish()
            }
        }
    }
}

class ChecklistWidgetService : RemoteViewsService() {
    override fun onGetViewFactory(intent: Intent): RemoteViewsFactory = ChecklistWidgetFactory(
        applicationContext,
        intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID),
    )
}

class ChecklistWidgetFactory(private val context: Context, private val widgetId: Int) :
    RemoteViewsService.RemoteViewsFactory, KoinComponent {
    private val notes: NoteRepository by inject()
    private var note: Note? = null

    override fun onCreate() = Unit

    override fun onDataSetChanged() {
        note = if (AppLock.hidesNotesOutsideTheApp()) null else runBlocking {
            notes.getById(ChecklistWidgets.noteId(context, widgetId)).first()?.takeIf { it.isList && !it.isDeleted }
        }
    }

    override fun onDestroy() {
        note = null
    }

    override fun getCount(): Int = note?.taskList?.size ?: 0

    override fun getViewAt(position: Int): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_checklist_item)
        val current = note ?: return views
        val task = current.taskList.getOrNull(position) ?: return views
        views.setImageViewResource(
            R.id.widget_checklist_box,
            if (task.isDone) R.drawable.widget_box_checked else R.drawable.widget_box,
        )
        views.setTextViewText(R.id.widget_checklist_text, task.content)
        views.setInt(
            R.id.widget_checklist_text,
            "setPaintFlags",
            if (task.isDone) Paint.STRIKE_THRU_TEXT_FLAG or Paint.ANTI_ALIAS_FLAG else Paint.ANTI_ALIAS_FLAG,
        )
        views.setFloat(R.id.widget_checklist_text, "setAlpha", if (task.isDone) 0.6f else 1f)
        views.setOnClickFillInIntent(
            R.id.widget_checklist_row,
            Intent().apply {
                putExtra(ChecklistWidgets.EXTRA_NOTE_ID, current.id)
                putExtra(ChecklistWidgets.EXTRA_TASK_ID, task.id)
            },
        )
        return views
    }

    override fun getLoadingView(): RemoteViews? = null
    override fun getViewTypeCount(): Int = 1
    override fun getItemId(position: Int): Long = note?.taskList?.getOrNull(position)?.id ?: position.toLong()
    override fun hasStableIds(): Boolean = true
}
