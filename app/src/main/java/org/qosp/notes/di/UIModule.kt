package org.qosp.notes.di

import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module
import org.qosp.notes.components.html.HtmlLibraryStore
import org.qosp.notes.ui.ActivityViewModel
import org.qosp.notes.ui.archive.ArchiveViewModel
import org.qosp.notes.ui.attachments.dialog.AttachmentDialogViewModel
import org.qosp.notes.ui.deleted.DeletedViewModel
import org.qosp.notes.ui.editor.EditorViewModel
import org.qosp.notes.ui.html.HtmlLibraryViewModel
import org.qosp.notes.ui.html.HtmlViewerViewModel
import org.qosp.notes.ui.links.NetworkViewModel
import org.qosp.notes.ui.lock.AppLock
import org.qosp.notes.ui.main.MainViewModel
import org.qosp.notes.ui.notebooks.ManageNotebooksViewModel
import org.qosp.notes.ui.notebooks.dialog.NotebookDialogViewModel
import org.qosp.notes.ui.reminders.EditReminderViewModel
import org.qosp.notes.ui.search.SearchViewModel
import org.qosp.notes.ui.settings.SettingsViewModel
import org.qosp.notes.ui.sync.nextcloud.NextcloudViewModel
import org.qosp.notes.ui.tags.TagsViewModel
import org.qosp.notes.ui.tags.dialog.TagDialogViewModel
import java.io.File

object UIModule {
    val uiModule = module {
        single { AppLock(wallClock = { System.currentTimeMillis() }) }
        single { HtmlLibraryStore(File(androidContext().filesDir, "html")) }
        viewModelOf(::HtmlLibraryViewModel)
        viewModelOf(::HtmlViewerViewModel)
        viewModelOf(::EditorViewModel)
        viewModelOf(::NetworkViewModel)
        viewModelOf(::ActivityViewModel)
        viewModelOf(::ArchiveViewModel)
        viewModelOf(::TagsViewModel)
        viewModelOf(::TagDialogViewModel)
        viewModelOf(::NextcloudViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::SearchViewModel)
        viewModelOf(::EditReminderViewModel)
        viewModelOf(::ManageNotebooksViewModel)
        viewModelOf(::NotebookDialogViewModel)
        viewModelOf(::MainViewModel)
        viewModelOf(::DeletedViewModel)
        viewModelOf(::AttachmentDialogViewModel)
    }
}
