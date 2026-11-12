package org.qosp.notes.di

import androidx.room.withTransaction
import org.koin.android.ext.koin.androidContext
import org.koin.androidx.workmanager.dsl.workerOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module
import org.qosp.notes.App
import org.qosp.notes.BuildConfig
import org.qosp.notes.components.BibleNotebook
import org.qosp.notes.components.MediaStorageManager
import org.qosp.notes.components.backup.BackupManager
import org.qosp.notes.components.workers.BinCleaningWorker
import org.qosp.notes.components.workers.SyncWorker
import org.qosp.notes.data.AppDatabase
import org.qosp.notes.ui.reminders.ReminderManager
import org.qosp.notes.ui.utils.ConnectionManager
import org.qosp.notes.ui.utils.Toaster

object UtilModule {

    val utilModule = module {
        includes(RepositoryModule.repoModule, SyncModule.syncModule)

        workerOf(::BinCleaningWorker)
        workerOf(::SyncWorker)

        single {
            MediaStorageManager(
                context = androidContext(),
                noteRepository = get(),
                mediaFolder = App.MEDIA_FOLDER
            )
        }

        single {
            val database = get<AppDatabase>()
            BackupManager(
                BuildConfig.VERSION_CODE,
                noteRepository = get(),
                notebookRepository = get(),
                tagRepository = get(),
                reminderRepository = get(),
                idMappingRepository = get(),
                reminderManager = get(),
                context = androidContext(),
                runInTransaction = { block -> database.withTransaction { block() } },
            )
        }
        single { BibleNotebook(androidContext(), get(), get()) }
        singleOf(::ReminderManager)
        singleOf(::ConnectionManager)
        singleOf(::Toaster)
    }
}
