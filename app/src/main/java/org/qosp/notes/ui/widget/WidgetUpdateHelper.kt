package org.qosp.notes.ui.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log

object WidgetUpdateHelper {
    fun updateAllWidgets(context: Context) {
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, NotesWidgetProvider::class.java)
            )

            if (appWidgetIds.isNotEmpty()) {
                appWidgetIds.forEach { appWidgetId ->
                    NotesWidgetProvider.updateAppWidget(context, appWidgetManager, appWidgetId)
                }
                Log.d("WidgetUpdateHelper", "Updated ${appWidgetIds.size} widget(s)")
            }
            // Black Pearl checklist widgets: title, count and items again
            val checklists = ChecklistWidgets.ids(context)
            if (checklists.isNotEmpty()) {
                context.sendBroadcast(
                    Intent(context, ChecklistWidgetProvider::class.java)
                        .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, checklists)
                )
            }
        } catch (e: Exception) {
            Log.e("WidgetUpdateHelper", "Error updating widgets", e)
        }
    }
}
