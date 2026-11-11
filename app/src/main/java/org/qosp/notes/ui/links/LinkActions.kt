package org.qosp.notes.ui.links

import org.qosp.notes.components.links.LinkIndex
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository

object LinkActions {

    /**
     * Id of the note titled [title]: the existing one when [index] knows it, else a new ordinary note in [notebookId]
     * ([isLocalOnly] follows the "new notes syncable" setting, like notes created in the editor).
     */
    suspend fun createOrOpen(
        title: String,
        notebookId: Long?,
        isLocalOnly: Boolean,
        repo: NoteRepository,
        index: LinkIndex,
    ): Long {
        index.resolve(title)?.let { return it.id }
        return repo.insertNote(Note(title = title.trim(), notebookId = notebookId, isLocalOnly = isLocalOnly))
    }
}
