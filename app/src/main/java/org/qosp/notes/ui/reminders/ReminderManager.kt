package org.qosp.notes.ui.reminders

import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.AlarmManagerCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.os.bundleOf
import androidx.navigation.NavDeepLinkBuilder
import kotlinx.coroutines.flow.first
import org.qosp.notes.App
import org.qosp.notes.R
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.ui.MainActivity
import org.qosp.notes.ui.lock.AppLock


class ReminderManager(
    private val context: Context,
    private val reminderRepository: ReminderRepository,
    private val noteRepository: NoteRepository,
) {
    private fun requestBroadcast(reminderId: Long, noteId: Long, flag: Int = PendingIntent.FLAG_UPDATE_CURRENT): PendingIntent? {
        val defaultFlag = PendingIntent.FLAG_IMMUTABLE

        val notificationIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtras(
                bundleOf(
                    "noteId" to noteId,
                    "reminderId" to reminderId,
                )
            )
            action = ReminderReceiver.REMINDER_HAS_FIRED
        }
        return PendingIntent.getBroadcast(
            context,
            reminderId.toInt(),
            notificationIntent,
            flag or defaultFlag
        )
    }

    fun isReminderSet(reminderId: Long, noteId: Long): Boolean {
        val defaultFlag = PendingIntent.FLAG_IMMUTABLE
        return requestBroadcast(reminderId, noteId, PendingIntent.FLAG_NO_CREATE or defaultFlag) != null
    }

    fun schedule(reminderId: Long, dateTime: Long, noteId: Long) {
        val alarmManager = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        val broadcast = requestBroadcast(reminderId, noteId) ?: return

        cancel(reminderId, noteId, keepIntent = true)

        val triggerAtMillis = dateTime * 1000 // convert seconds to millis

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
            AlarmManagerCompat.setAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                broadcast
            )
        } else {
            AlarmManagerCompat.setExactAndAllowWhileIdle(
                alarmManager,
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                broadcast
            )
        }
    }

    fun cancel(reminderId: Long, noteId: Long, keepIntent: Boolean = false) {
        val alarmManager = ContextCompat.getSystemService(context, AlarmManager::class.java) ?: return
        val defaultFlag = PendingIntent.FLAG_IMMUTABLE
        val broadcast = requestBroadcast(reminderId, noteId, PendingIntent.FLAG_NO_CREATE or defaultFlag) ?: return
        alarmManager.cancel(broadcast)
        if (!keepIntent) broadcast.cancel()
    }

    suspend fun cancelAllRemindersForNote(noteId: Long) {
        val reminders = reminderRepository.getByNoteId(noteId).first()
        reminders.forEach { cancel(it.id, noteId) }
    }

    suspend fun rescheduleAll() {
        reminderRepository
            .getAll()
            .first()
            .forEach { reminder ->
                if (reminder.hasExpired()) {
                    // came due while the phone was off: tell the user now (this also removes the row)
                    sendNotification(reminder.id, reminder.noteId)
                    return@forEach
                }
                schedule(reminder.id, reminder.date, reminder.noteId)
            }
    }

    suspend fun sendNotification(reminderId: Long, noteId: Long) {
        val notificationManager = ContextCompat.getSystemService(context, NotificationManager::class.java) ?: return

        var notificationTitle = ""

        // The alarm outlives its reminder when the note was binned or deleted (e.g. by sync): show nothing then.
        val reminder = reminderRepository.getById(reminderId).first()
        val note = noteRepository.getById(noteId).first()
        if (reminder == null || !shouldNotify(note)) {
            if (reminder != null) reminderRepository.deleteById(reminderId)
            return
        }
        notificationTitle = reminder.name
        if (notificationTitle.isEmpty()) notificationTitle = note?.title.orEmpty()
        // Black Pearl: with the app lock on, the notification does not say what the note is about
        if (AppLock.hidesNotesOutsideTheApp()) notificationTitle = context.getString(R.string.lock_notification_hidden)

        val pendingIntent = NavDeepLinkBuilder(context)
            .setGraph(R.navigation.nav_graph)
            .setDestination(R.id.fragment_editor)
            .setArguments(
                bundleOf(
                    "noteId" to noteId,
                    "transitionName" to ""
                )
            )
            .setComponentName(MainActivity::class.java)
            .createPendingIntent()

        val notification = NotificationCompat.Builder(context, App.REMINDERS_CHANNEL_ID)
            .setContentText(notificationTitle)
            .setContentTitle(context.getString(R.string.notification_reminder_fired))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()

        // Without the permission (Android 13+) nothing would be shown: keep the row, it is tried again at boot.
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return
        try {
            notificationManager.notify(reminderId.toInt(), notification)
        } catch (e: Exception) {
            return
        }
        // Only now the reminder is used up.
        reminderRepository.deleteById(reminderId)
    }

    companion object {
        /** A reminder is shown only for a note that still exists and is not in the bin. */
        fun shouldNotify(note: Note?): Boolean = note != null && !note.isDeleted
    }
}
