package org.qosp.notes.ui.main

import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import org.qosp.notes.R
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
) : AbstractNotesViewModel(preferenceRepository, backendProvider) {

    private val notebookIdFlow: MutableStateFlow<Long?> = MutableStateFlow(null)

    override val provideNotes = { sortMethod: SortMethod ->
        notebookIdFlow.flatMapLatest { id ->
            when (id) {
                // the Bible Study Kit has its own notebook and stays out of "All notes"
                null -> combine(
                    noteRepository.getNonDeletedOrArchived(sortMethod),
                    preferenceRepository.getBibleKitNotebookId(),
                ) { notes, kitId -> if (kitId == null) notes else notes.filter { it.notebookId != kitId } }
                R.id.nav_default_notebook.toLong() -> noteRepository.getNotesWithoutNotebook(sortMethod)
                else -> noteRepository.getByNotebook(id, sortMethod)
            }
        }
    }

    suspend fun notebookExists(notebookId: Long) = notebookRepository.getById(notebookId).firstOrNull() != null

    fun initialize(notebookId: Long?) {
        viewModelScope.launch { notebookIdFlow.emit(notebookId) }
    }
}
