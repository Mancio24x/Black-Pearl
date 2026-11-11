package org.qosp.notes.components.links

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import org.qosp.notes.data.repo.NoteRepository

/** The current [LinkIndex] of all notes, rebuilt in the background a moment after notes change. */
class LinkIndexRepository(notes: NoteRepository, scope: CoroutineScope) {

    @OptIn(FlowPreview::class)
    val index: StateFlow<LinkIndex> = notes.getNonDeleted()
        .debounce(300)
        .map { LinkIndex(it) }
        .flowOn(Dispatchers.Default)
        .stateIn(scope, SharingStarted.Eagerly, LinkIndex(emptyList()))
}
