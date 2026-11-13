package org.qosp.notes.components

import android.content.Context
import org.qosp.notes.components.importer.Titles
import kotlinx.coroutines.flow.first
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.preferences.PreferenceRepository

/**
 * Black Pearl: the sample note shipped inside the APK (a long Markdown notebook, to try the editor and the reader).
 * Every install gets it once, in its own notebook that "All notes" leaves out (like the Bible Study Kit);
 * deleting it is final.
 */
object SampleNote {
    const val ASSET = "sample/Quaderno delle Ricette Sburrate.md"

    /**
     * The first heading is the title, the rest is the text. Local only: with sync on, every device of the same
     * person would otherwise upload its own copy.
     */
    fun parse(markdown: String): Note {
        val (title, body) = Titles.split("", markdown.replace("\r\n", "\n"))
        return Note(title = title, content = body, isLocalOnly = true)
    }

    const val NOTEBOOK = "Quaderno delle Ricette"

    suspend fun addOnce(
        context: Context,
        notes: NoteRepository,
        notebooks: NotebookRepository,
        preferences: PreferenceRepository,
    ) {
        if (preferences.isSampleNoteAdded()) return
        val markdown = context.assets.open(ASSET).use { it.readBytes().decodeToString() }
        val notebookId = notebookFor(notebooks, preferences)
        notes.insertNote(parse(markdown).copy(notebookId = notebookId), sync = false)
        preferences.setSampleNoteAdded()
        preferences.setSampleNoteMoved() // already in its notebook: nothing to move later
    }

    /**
     * Installs from before the recipe notebook have the note among "All notes": once, move it into the notebook.
     * Only a note with the sample's title that is in no notebook moves (its text is not touched); one the user put
     * in a notebook stays there. Returns true when a note was moved.
     */
    suspend fun moveIntoNotebook(
        context: Context,
        notes: NoteRepository,
        notebooks: NotebookRepository,
        preferences: PreferenceRepository,
    ): Boolean {
        if (!preferences.isSampleNoteAdded() || preferences.isSampleNoteMoved()) return false
        val title = parse(context.assets.open(ASSET).use { it.readBytes().decodeToString() }).title
        val candidates = notes.getIdTitlesOutside(Long.MIN_VALUE).filter { it.title == title }
            .mapNotNull { notes.getById(it.id).first() }
            .filter { it.notebookId == null }
        var moved = false
        if (candidates.isNotEmpty()) {
            val notebookId = notebookFor(notebooks, preferences)
            notes.updateNotes(*candidates.map { it.copy(notebookId = notebookId) }.toTypedArray(), sync = false)
            moved = true
        }
        preferences.setSampleNoteMoved()
        return moved
    }

    /** The recipe notebook, created (and remembered) the first time it is needed. */
    private suspend fun notebookFor(notebooks: NotebookRepository, preferences: PreferenceRepository): Long {
        preferences.getSampleNotebookId()?.let { id -> if (notebooks.getById(id).first() != null) return id }
        return notebooks.insert(Notebook(name = NOTEBOOK)).also { preferences.setSampleNotebookId(it) }
    }
}
