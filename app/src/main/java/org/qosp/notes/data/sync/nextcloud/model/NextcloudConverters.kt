package org.qosp.notes.data.sync.nextcloud.model

import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.nextcloud.NextcloudNote

/** [category] null leaves the category out of the request, so the server keeps the note's current one. */
fun Note.asNextcloudNote(id: Long, category: String?): NextcloudNote = NextcloudNote(
    id = id,
    title = title,
    content = toStorableContent(),
    category = category,
    favorite = isPinned,
    modified = modifiedDate
)
