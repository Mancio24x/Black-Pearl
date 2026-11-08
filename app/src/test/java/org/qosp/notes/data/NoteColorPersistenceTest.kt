package org.qosp.notes.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteColor

/** The note color must survive backup/restore and must not alter the note content. */
class NoteColorPersistenceTest {

    @Test
    fun everyColorSurvivesBackupWithContentUntouched() {
        val notes = NoteColor.entries.mapIndexed { i, color ->
            Note(title = "n$i", content = "text **$i**\n- [ ] task", color = color, id = i + 1L)
        }.toSet()
        val restored = Backup.fromString(Backup(version = 1, notes = notes).serialize()).notes
        assertEquals(notes.associate { it.id to (it.color to it.content) }, restored.associate { it.id to (it.color to it.content) })
    }

    @Test
    fun storedNamesAreStable() {
        // DB and backups store the enum name: renaming a constant would lose users' colors
        assertEquals(
            listOf("Default", "Red", "Orange", "Yellow", "Green", "Teal", "Cyan", "Blue", "Purple", "Pink", "Brown", "Gray"),
            NoteColor.entries.map { it.name }
        )
    }
}
