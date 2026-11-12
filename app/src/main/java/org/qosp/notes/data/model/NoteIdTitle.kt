package org.qosp.notes.data.model

/** Id and title of a note, for light queries that must not load the note text. */
data class NoteIdTitle(val id: Long, val title: String)
