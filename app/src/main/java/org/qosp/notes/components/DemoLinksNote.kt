package org.qosp.notes.components

import android.content.Context
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.preferences.PreferenceRepository

/**
 * Black Pearl: the demo note "The Bible" shipped in the APK, full of [[links]] to try linked notes.
 * Every install gets it once among its notes; deleting it is final.
 */
object DemoLinksNote {
    const val ASSET = "sample/The Bible.md"
    private const val TITLE = "The Bible"

    /** The title is fixed; the text is the file without its own `# The Bible` heading line. Local only, like [SampleNote]. */
    fun parse(markdown: String): Note {
        val lines = markdown.replace("\r\n", "\n").split("\n").toMutableList()
        val heading = lines.indexOfFirst { it == "# $TITLE" }
        if (heading >= 0) lines.removeAt(heading)
        return Note(title = TITLE, content = lines.joinToString("\n"), isLocalOnly = true)
    }

    suspend fun addOnce(context: Context, notes: NoteRepository, preferences: PreferenceRepository) {
        if (preferences.isDemoLinksNoteAdded()) return
        val markdown = context.assets.open(ASSET).use { it.readBytes().decodeToString() }
        notes.insertNote(parse(markdown), sync = false)
        preferences.setDemoLinksNoteAdded()
    }
}
