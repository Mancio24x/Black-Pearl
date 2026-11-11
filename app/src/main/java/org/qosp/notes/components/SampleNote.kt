package org.qosp.notes.components

import android.content.Context
import org.qosp.notes.components.importer.Titles
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.preferences.PreferenceRepository

/**
 * Black Pearl: the sample note shipped inside the APK (a long Markdown notebook, to try the editor and the reader).
 * Every install gets it once among its notes; deleting it is final.
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

    suspend fun addOnce(context: Context, notes: NoteRepository, preferences: PreferenceRepository) {
        if (preferences.isSampleNoteAdded()) return
        val markdown = context.assets.open(ASSET).use { it.readBytes().decodeToString() }
        notes.insertNote(parse(markdown), sync = false)
        preferences.setSampleNoteAdded()
    }
}
