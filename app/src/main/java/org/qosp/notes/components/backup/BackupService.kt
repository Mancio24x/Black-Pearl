package org.qosp.notes.components.backup

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.qosp.notes.App
import org.qosp.notes.R
import org.qosp.notes.components.importer.OtherAppsImport
import org.qosp.notes.data.model.Note
import org.qosp.notes.preferences.BackupStrategy
import org.qosp.notes.preferences.PreferenceRepository

class BackupService : LifecycleService() {
    private var nextId = 0
        get() {
            field += 1
            return "backup_$field".hashCode()
        }

    private val jobs = mutableListOf<Job>()

    val preferenceRepository: PreferenceRepository by inject()
    val backupManager: BackupManager by inject()

    private var notificationManager: NotificationManager? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        intent?.let { i ->
            val action = i.extras?.getSerializable(ACTION) as? Action ?: return@let
            if (action == Action.IMPORT) {
                val uris = i.extras?.getParcelableArrayList<Uri>(URIS_EXTRA).orEmpty()
                if (uris.isNotEmpty()) importNotes(uris)
                return@let
            }
            val uri = i.extras?.getParcelable<Uri>(URI_EXTRA) ?: return@let

            when (action) {
                Action.RESTORE -> {
                    restoreNotes(uri)
                }
                Action.BACKUP -> {
                    // Only ids travel in the Intent (whole notes can exceed the ~1 MB Binder limit)
                    val noteIds = i.extras?.getLongArray(NOTE_IDS)
                    backupNotes(noteIds, uri)
                }
                Action.IMPORT -> Unit
            }
        }
        return START_STICKY
    }

    override fun onTimeout(startId: Int) {
        super.onTimeout(startId)
        jobs.forEach { it.cancel() }
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        lifecycleScope.cancel()
    }

    private fun startJob(block: suspend CoroutineScope.() -> Unit) {
        val job = lifecycleScope.launch(Dispatchers.IO, block = block)
        job.invokeOnCompletion {
            if (jobs.all { it.isCompleted }) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_DETACH)
                stopSelf()
            }
            jobs.remove(job)
        }
        jobs.add(job)
    }

    /**
     * Backs up the notes with ids [noteIds] to a file with URI [outputUri] or all notes if [noteIds] is null.
     */
    private fun backupNotes(noteIds: LongArray? = null, outputUri: Uri) = startJob {
        val notes = noteIds?.let { backupManager.notesByIds(it) }
        val handler = when (preferenceRepository.get<BackupStrategy>().first()) {
            BackupStrategy.INCLUDE_FILES -> AttachmentHandler.IncludeFiles(applicationContext)
            BackupStrategy.KEEP_INFO -> AttachmentHandler.KeepInfoOnly
            BackupStrategy.KEEP_NOTHING -> AttachmentHandler.KeepNothing
        }

        val backup = backupManager.createBackup(
            notes = notes,
            attachmentHandler = handler
        )

        val notificationId = nextId
        val notificationBuilder = NotificationCompat.Builder(applicationContext, App.BACKUPS_CHANNEL_ID)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.notification_backing_up))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setOngoing(true)

        val progressHandler = object : ProgressHandler {
            override fun onProgressChanged(current: Int, max: Int) {
                notificationManager?.notify(
                    notificationId,
                    notificationBuilder.setProgress(max, current, false).build()
                )
            }

            override fun onCompletion() {
                notificationManager?.notify(
                    notificationId,
                    notificationBuilder
                        .setContentTitle(getString(R.string.notification_backup_complete))
                        .setProgress(0, 0, false)
                        .setAutoCancel(true)
                        .setOngoing(false)
                        .build()
                )
            }

            override fun onFailure(e: Throwable) {
                notificationManager?.notify(
                    notificationId,
                    notificationBuilder
                        .setContentTitle(getString(R.string.notification_backup_failed))
                        .setProgress(0, 0, false)
                        .setAutoCancel(true)
                        .setOngoing(false)
                        .build()
                )
            }
        }

        startForeground(notificationId, notificationBuilder.build())

        backupManager.createBackupZipFile(
            backup.serialize(),
            handler,
            outputUri,
            progressHandler
        )
    }

    private fun restoreNotes(backupUri: Uri) = startJob {
        val notificationId = nextId
        val notificationBuilder = NotificationCompat.Builder(applicationContext, App.BACKUPS_CHANNEL_ID)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.notification_restoring_notes))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setOngoing(true)
        val notification = notificationBuilder.build()

        startForeground(notificationId, notification)

        val backup = backupManager.backupFromZipFile(backupUri, DefaultMigrationHandler()).fold(
            onSuccess = { it },
            onFailure = {
                val notification = notificationBuilder
                    .setContentTitle(getString(R.string.notification_restore_failed))
                    .setContentText(it.message)
                    .setOngoing(false)
                    .setAutoCancel(true)
                    .build()

                notificationManager?.notify(notificationId, notification)
                return@startJob
            },
        )

        val restored = runCatching { backupManager.restoreNotesFromBackup(backup) }
        // A cancelled job (service timeout) must still stop; the transaction has already been rolled back
        restored.exceptionOrNull()?.let { if (it is CancellationException) throw it }

        notificationManager?.notify(
            notificationId,
            notificationBuilder
                .setContentTitle(
                    getString(
                        if (restored.isSuccess) R.string.notification_restore_complete
                        else R.string.notification_restore_failed
                    )
                )
                .setContentText(restored.exceptionOrNull()?.message)
                .setOngoing(false)
                .setAutoCancel(true)
                .build()
        )
    }

    /** Black Pearl: notes exported by other apps (and Black Pearl / Quillpad backups), see OtherAppsImport. */
    private fun importNotes(uris: List<Uri>) = startJob {
        val notificationId = nextId
        val notificationBuilder = NotificationCompat.Builder(applicationContext, App.BACKUPS_CHANNEL_ID)
            .setOnlyAlertOnce(true)
            .setContentTitle(getString(R.string.import_in_progress))
            .setSmallIcon(R.drawable.ic_notification_icon)
            .setProgress(0, 0, true)
            .setOngoing(true)
        startForeground(notificationId, notificationBuilder.build())

        val outcome = runCatching { OtherAppsImport(applicationContext, backupManager).import(uris) }
        val message = outcome.fold(
            onSuccess = {
                if (it.notes == 0) getString(R.string.import_nothing_found)
                else resources.getQuantityString(R.plurals.import_done, it.notes, it.notes)
            },
            onFailure = { getString(R.string.import_failed) },
        )
        notificationManager?.notify(
            notificationId,
            notificationBuilder
                .setContentTitle(message)
                .setContentText(outcome.exceptionOrNull()?.message ?: outcome.getOrNull()?.formats?.joinToString(", "))
                .setProgress(0, 0, false)
                .setOngoing(false)
                .setAutoCancel(true)
                .build()
        )
        // Also visible when notifications are off
        withContext(Dispatchers.Main) { Toast.makeText(applicationContext, message, Toast.LENGTH_LONG).show() }
    }

    enum class Action {
        RESTORE,
        BACKUP,
        IMPORT,
    }

    companion object {
        private const val URI_EXTRA = "URI_EXTRA"
        private const val URIS_EXTRA = "URIS_EXTRA"
        private const val NOTE_IDS = "NOTE_IDS"
        private const val ACTION = "ACTION"

        fun restoreNotes(context: Context, backupUri: Uri) {
            Intent(context, BackupService::class.java).also { intent ->
                intent.putExtra(ACTION, Action.RESTORE)
                intent.putExtra(URI_EXTRA, backupUri)
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun importNotes(context: Context, uris: List<Uri>) {
            Intent(context, BackupService::class.java).also { intent ->
                intent.putExtra(ACTION, Action.IMPORT)
                intent.putParcelableArrayListExtra(URIS_EXTRA, ArrayList(uris))
                ContextCompat.startForegroundService(context, intent)
            }
        }

        fun backupNotes(context: Context, notes: Set<Note>? = null, outputUri: Uri) {
            Intent(context, BackupService::class.java).also { intent ->
                intent.putExtra(ACTION, Action.BACKUP)
                if (notes != null) intent.putExtra(NOTE_IDS, notes.map { it.id }.toLongArray())
                intent.putExtra(URI_EXTRA, outputUri)
                ContextCompat.startForegroundService(context, intent)
            }
        }
    }
}
