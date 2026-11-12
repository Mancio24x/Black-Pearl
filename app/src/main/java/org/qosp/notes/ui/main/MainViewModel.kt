package org.qosp.notes.ui.main

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.qosp.notes.R
import org.qosp.notes.components.BibleNotebook
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.sync.core.BackendProvider
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.SortMethod
import org.qosp.notes.ui.common.AbstractNotesViewModel

class MainViewModel(
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    preferenceRepository: PreferenceRepository,
    backendProvider: BackendProvider,
    private val library: BibleNotebook,
) : AbstractNotesViewModel(preferenceRepository, backendProvider) {

    private val notebookIdFlow: MutableStateFlow<Long?> = MutableStateFlow(null)

    override val provideNotes = { sortMethod: SortMethod ->
        notebookIdFlow.flatMapLatest { id ->
            library.ready.filter { it }.flatMapLatest {
                preferenceRepository.getBibleKitNotebookId().distinctUntilChanged().flatMapLatest { kitId ->
                    when {
                        id == null && kitId != null -> noteRepository.getNonDeletedOrArchivedExcept(kitId, sortMethod)
                        id == null -> noteRepository.getNonDeletedOrArchived(sortMethod)
                        id == R.id.nav_default_notebook.toLong() -> noteRepository.getNotesWithoutNotebook(sortMethod)
                        id == kitId -> noteRepository.getByNotebook(id, sortMethod).map { library.inReadingOrder(it) }
                        else -> noteRepository.getByNotebook(id, sortMethod)
                    }
                }
            }
        }
    }

    suspend fun notebookExists(notebookId: Long) = notebookRepository.getById(notebookId).firstOrNull() != null

    fun initialize(notebookId: Long?) {
        viewModelScope.launch { notebookIdFlow.emit(notebookId) }
    }
}
