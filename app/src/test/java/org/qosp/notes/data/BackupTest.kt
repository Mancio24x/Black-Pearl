package org.qosp.notes.data

import org.junit.Assert.*
import org.junit.Test
import org.qosp.notes.data.model.*
import org.qosp.notes.preferences.CloudService

class BackupTest {

    @Test
    fun serializer() {
        val backup = Backup(
            1,
            setOf(
                Note(
                    title = "title",
                    content = "content",
                    isList = false,
                    taskList = listOf(NoteTask(33L, "reminder", false)),
                    attachments = listOf(Attachment(type = Attachment.Type.AUDIO, path = "sdt")),
                    tags = listOf(Tag("tag", 33L)),
                    reminders = listOf(Reminder("reminder", 2442L, 234)),
                    color = NoteColor.Blue
                )
            ),
            setOf(Notebook("nb")),
            joins = setOf(NoteTagJoin(33, 44)),
            idMappings = setOf(
                IdMapping(
                    33, 44, 12,
                    CloudService.NEXTCLOUD, null, false, false
                )
            )
        )
        assertNotNull(backup.serialize())
    }

    @Test
    fun serializedBackupRestoresUnchanged() {
        val backup = Backup(
            5,
            setOf(Note(title = "t", content = "c", color = NoteColor.Red, attachments = listOf(Attachment(fileName = "a.jpg")))),
            setOf(Notebook("nb", 2)),
            setOf(Reminder("r", 1, 99, 3)),
            setOf(Tag("tag", 4)),
            setOf(NoteTagJoin(4, 1)),
            setOf(IdMapping(1, 1, 12, CloudService.NEXTCLOUD, null, false)),
        )
        assertEquals(backup, Backup.fromString(backup.serialize()))
    }

    @Test
    fun oldMinimalBackupStillRestores() {
        // Older Quillpad backup: only a few fields, no idMappings / storageUri / isBeingUpdated
        val json = """{"version":1,"notes":[{"title":"Old","content":"text","id":7}],
            |"idMappings":[{"mappingId":1,"localNoteId":7,"remoteNoteId":3,"provider":"NEXTCLOUD","extras":null,
            |"isDeletedLocally":false}]}""".trimMargin()
        val backup = Backup.fromString(json)
        assertEquals(1, backup.version)
        assertEquals("Old", backup.notes.single().title)
        assertEquals(NoteColor.Default, backup.notes.single().color)
        assertNull(backup.idMappings.single().storageUri)
    }

    @Test
    fun newerBackupWithUnknownKeysAndEnumValuesStillRestores() {
        val json = """{"version":999,"futureField":{"a":1},
            |"notes":[{"title":"New","content":"text","color":"Ultraviolet","futureNoteField":true,
            |"attachments":[{"type":"HOLOGRAM","fileName":"a.bin","futureAttachmentField":1}],"id":1}],
            |"tags":[{"name":"t","id":2,"futureTagField":"x"}]}""".trimMargin()
        val backup = Backup.fromString(json)
        val note = backup.notes.single()
        assertEquals("New", note.title)
        assertEquals(NoteColor.Default, note.color)
        assertEquals(Attachment.Type.IMAGE, note.attachments.single().type)
        assertEquals("a.bin", note.attachments.single().fileName)
        assertEquals("t", backup.tags.single().name)
    }
}
