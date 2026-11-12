package org.qosp.notes.components.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import org.qosp.notes.App
import org.qosp.notes.data.Backup
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteTagJoin
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.model.Reminder
import org.qosp.notes.data.model.Tag
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.data.repo.ReminderRepository
import org.qosp.notes.data.repo.TagRepository
import org.qosp.notes.ui.attachments.getAttachmentUri
import org.qosp.notes.ui.reminders.ReminderManager
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Runs [block] in one database transaction. A named interface, not a `suspend` function type: Koin's
 * `verify()` (KoinModulesTest) cannot read a suspend function type by reflection and fails with an NPE.
 */
fun interface TransactionRunner {
    suspend fun run(block: suspend () -> Unit)
}

class BackupManager(
    private val currentVersion: Int,
    private val noteRepository: NoteRepository,
    private val notebookRepository: NotebookRepository,
    private val tagRepository: TagRepository,
    private val reminderRepository: ReminderRepository,
    private val idMappingRepository: IdMappingRepository,
    private val reminderManager: ReminderManager,
    private val context: Context,
    /** Runs the database part of a restore atomically (Room `withTransaction` in the app, see UtilModule). */
    private val runInTransaction: TransactionRunner = TransactionRunner { it() },
) {
    private val BUFFER = 2048

    /** Loads the notes with these [ids] (notes deleted in the meantime are skipped). */
    suspend fun notesByIds(ids: LongArray): Set<Note> =
        ids.distinct().mapNotNull { noteRepository.getById(it).first() }.toSet()

    /**
     * Creates a backup which contains [notes] or the whole database if [notes] is null.
     */
    suspend fun createBackup(
        notes: Set<Note>?,
        attachmentHandler: AttachmentHandler,
    ): Backup {
        val notes = notes ?: noteRepository
            .getAll()
            .first()
            .toSet()

        val notebooks = mutableSetOf<Notebook>()
        val reminders = mutableSetOf<Reminder>()
        val tags = mutableSetOf<Tag>()
        val joins = mutableSetOf<NoteTagJoin>()
        val idMappings = mutableSetOf<IdMapping>()

        val newNotes = notes.map { note ->
            note.notebookId?.let { notebookId ->
                val notebook = notebookRepository.getById(notebookId).first() ?: return@let
                notebooks.add(notebook)
            }

            val noteReminders = reminderRepository.getByNoteId(note.id).first()
            reminders.addAll(noteReminders)

            val noteTags = tagRepository.getByNoteId(note.id).first()
            tags.addAll(noteTags)

            val noteTagJoins = noteTags.map { tag -> NoteTagJoin(tag.id, note.id) }
            joins.addAll(noteTagJoins)

            val mappings = idMappingRepository.getAllByLocalId(note.id)
            idMappings.addAll(mappings)

            val newAttachments = mutableListOf<Attachment>()
            note.attachments.forEach { old ->
                attachmentHandler
                    .handle(old)
                    ?.let { newAttachments.add(it) }
            }
            note.copy(attachments = newAttachments)
        }

        return Backup(currentVersion, newNotes.toSet(), notebooks, reminders, tags, joins, idMappings)
    }

    /**
     * Adds the content of [backup] as NEW local notes. The database part is all-or-nothing.
     * The backup's sync id mappings are deliberately NOT restored: they point to remote notes that may be gone or
     * still belong to the original notes, and the next sync would then bin or overwrite the restored copies.
     * Restored notes have no mapping, so the next sync uploads them as new notes.
     */
    suspend fun restoreNotesFromBackup(backup: Backup) {
        val notebooksMap = mutableMapOf<Long, Long>()
        val tagsMap = mutableMapOf<Long, Long>()
        val notesMap = mutableMapOf<Long, Long>()
        val toSchedule = mutableListOf<Reminder>()

        runInTransaction.run { restoreInDatabase(backup, notebooksMap, tagsMap, notesMap, toSchedule) }

        // Alarms only after the data is committed
        toSchedule.forEach { reminder ->
            reminderManager.schedule(reminderId = reminder.id, noteId = reminder.noteId, dateTime = reminder.date)
        }
    }

    private suspend fun restoreInDatabase(
        backup: Backup,
        notebooksMap: MutableMap<Long, Long>,
        tagsMap: MutableMap<Long, Long>,
        notesMap: MutableMap<Long, Long>,
        toSchedule: MutableList<Reminder>,
    ) {
        backup.notebooks.forEach { notebook ->
            val existingNotebook = notebookRepository.getByName(notebook.name).firstOrNull()
            if (existingNotebook != null) {
                notebooksMap[notebook.id] = existingNotebook.id
            } else {
                notebooksMap[notebook.id] = notebookRepository.insert(notebook.copy(id = 0L))
            }
        }

        backup.tags.forEach { tag ->
            val existingTag = tagRepository.getByName(tag.name).firstOrNull()
            if (existingTag != null) {
                tagsMap[tag.id] = existingTag.id
                return@forEach
            }
            tagsMap[tag.id] = tagRepository.insert(tag.copy(id = 0L))
        }

        backup.notes.forEach { note ->
            val newNote = note.copy(
                id = 0L,
                notebookId = notebooksMap[note.notebookId],
                attachments = note.attachments.map { attachment ->
                    if (attachment.fileName.isNotEmpty()) {
                        attachment.copy(
                            path = getAttachmentUri(context, attachment.fileName).toString(),
                            fileName = ""
                        )
                    } else {
                        attachment
                    }
                }
            )
            // sync = false: a queued remote Create could still run if the transaction is rolled back.
            // Notes without a mapping are uploaded by the next sync anyway.
            notesMap[note.id] = noteRepository.insertNote(newNote, sync = false)
        }

        backup.joins.forEach { join ->
            val tagId = tagsMap[join.tagId] ?: return@forEach
            val noteId = notesMap[join.noteId] ?: return@forEach
            tagRepository.addTagToNote(tagId, noteId, shouldSync = false)
        }

        backup.reminders.forEach { reminder ->
            if (reminder.hasExpired()) return@forEach

            val noteId = notesMap[reminder.noteId] ?: return@forEach
            val reminderId = reminderRepository.insert(reminder.copy(id = 0L, noteId = noteId))
            toSchedule.add(reminder.copy(id = reminderId, noteId = noteId))
        }
    }

    fun backupFromZipFile(
        uri: Uri,
        migrationHandler: MigrationHandler,
    ): Result<Backup> = runCatching {
        var backup: Backup? = null
        val nameMap = mutableMapOf<String, String>()
        // URI-encoded name in the backup -> URI of the restored file, to fix inline links in note content
        val linkMap = mutableMapOf<String, String>()

        ZipInputStream(BufferedInputStream(context.contentResolver.openInputStream(uri))).use { input ->
            while (true) {
                val entry = runCatching { input.nextEntry }.getOrNull() ?: break
                val mediaName = BackupRules.mediaFileName(entry.name, entry.isDirectory, App.MEDIA_FOLDER)
                when {
                    entry.name == BackupRules.BACKUP_JSON -> {
                        // Create backup class
                        val reader = input.reader()
                        val builder = StringBuilder()
                        val buffer = CharArray(BUFFER)
                        var length = 0
                        while (reader.read(buffer).also { length = it } > 0) {
                            builder.append(String(buffer, 0, length))
                        }
                        val deserialized = migrationHandler.migrate(builder.toString())

                        backup = Backup.fromString(deserialized)
                    }

                    // Skip folders, files outside media/ (e.g. __MACOSX/) and unsafe names
                    mediaName == null -> continue

                    // Copy media files to local storage
                    else -> {
                        val dir = File(context.filesDir, App.MEDIA_FOLDER).also { it.mkdirs() }

                        val originalName: String = mediaName
                        // If a file with the same name already exists in app storage give the new file a unique name
                        val fileName = BackupRules.uniqueName(originalName) { File(dir, it).exists() }
                        // Map the old name to the new name so we can change the notes later to use the new name
                        if (fileName != originalName) nameMap[originalName] = fileName
                        getAttachmentUri(context, fileName)?.let { linkMap[Uri.encode(originalName)] = it.toString() }

                        FileOutputStream(File(dir, fileName)).use { out ->
                            val buffer = ByteArray(BUFFER)
                            var length = 0
                            while (input.read(buffer).also { length = it } > 0) {
                                out.write(buffer, 0, length)
                            }
                        }
                        input.closeEntry()
                    }
                }
            }
        }

        backup.run {
            if (this == null || (nameMap.isEmpty() && linkMap.isEmpty())) return@run this
            val newNotes = notes
                .map { note ->
                    val newAttachments = note.attachments.map { attachment ->
                        attachment.copy(fileName = nameMap[attachment.fileName] ?: attachment.fileName)
                    }
                    note.copy(
                        content = BackupRules.remapMediaLinks(note.content, linkMap),
                        attachments = newAttachments,
                    )
                }
                .toSet()

            copy(notes = newNotes)
        } ?: throw IOException()
    }

    fun createBackupZipFile(
        noteJson: String,
        handler: AttachmentHandler,
        uri: Uri,
        progressHandler: ProgressHandler,
    ) {
        runCatching {
            ZipOutputStream(BufferedOutputStream(context.contentResolver.openOutputStream(uri))).use { out ->
                var current = 0
                var max = 1

                if (handler is AttachmentHandler.IncludeFiles) {
                    val attachments = handler.attachmentsMap
                    max = attachments.size + 1

                    if (attachments.isNotEmpty()) out.putNextEntry(ZipEntry("${App.MEDIA_FOLDER}/"))
                    for ((fileName, inputUri) in attachments) {
                        progressHandler.onProgressChanged(++current, max)

                        out.putNextEntry(ZipEntry("${App.MEDIA_FOLDER}/$fileName"))
                        context.contentResolver.openInputStream(inputUri)?.use { input ->
                            input.copyTo(out, BUFFER)
                        }
                    }
                }

                progressHandler.onProgressChanged(++current, max)
                out.putNextEntry(ZipEntry("backup.json"))
                out.write(noteJson.toByteArray())
                out.finish()
            }
        }.fold(
            onSuccess = { progressHandler.onCompletion() },
            onFailure = { progressHandler.onFailure(it) }
        )
    }
}
