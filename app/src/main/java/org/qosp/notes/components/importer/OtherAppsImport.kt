package org.qosp.notes.components.importer

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.qosp.notes.App
import org.qosp.notes.components.backup.BackupManager
import org.qosp.notes.components.backup.DefaultMigrationHandler
import org.qosp.notes.data.Backup
import org.qosp.notes.data.model.Attachment
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteTagJoin
import org.qosp.notes.data.model.NoteTask
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.model.Tag
import org.qosp.notes.ui.attachments.getAttachmentUri
import java.io.File
import java.time.Instant
import java.util.UUID

/**
 * Imports the files the user picked (from other note apps, or a Black Pearl / Quillpad backup).
 * Notes are added, never replaced: existing notes, notebooks and tags are kept and reused by name.
 */
class OtherAppsImport(private val context: Context, private val backupManager: BackupManager) {

    class Outcome(val notes: Int, val formats: Set<String>)

    suspend fun import(uris: List<Uri>): Outcome = withContext(Dispatchers.IO) {
        val work = File(context.cacheDir, "import").apply { deleteRecursively(); mkdirs() }
        try {
            var count = 0
            val formats = linkedSetOf<String>()
            uris.forEachIndexed { index, uri ->
                val name = displayName(uri) ?: "file$index"
                val copy = File(work, "$index.bin")
                val input = context.contentResolver.openInputStream(uri) ?: return@forEachIndexed
                input.use { source -> copy.outputStream().use { source.copyTo(it) } }
                val entries = ImportArchives.entriesOf(copy, name, work)
                if (entries.any { it.path == "backup.json" }) { // Black Pearl / Quillpad backup
                    val backup = backupManager.backupFromZipFile(uri, DefaultMigrationHandler()).getOrThrow()
                    backupManager.restoreNotesFromBackup(backup)
                    count += backup.notes.size
                    formats += "Black Pearl"
                } else {
                    val result = NoteImporter.read(entries)
                    count += write(result.notes)
                    formats += result.formats
                }
            }
            Outcome(count, formats)
        } finally {
            work.deleteRecursively()
        }
    }

    /** Saves [notes]: copies their files into the media folder, then adds everything like a backup restore. */
    suspend fun write(notes: List<ImportedNote>): Int {
        if (notes.isEmpty()) return 0
        val mediaDir = File(context.filesDir, App.MEDIA_FOLDER).apply { mkdirs() }
        val now = Instant.now().epochSecond

        val notebooks = notes.mapNotNull { it.notebook?.trim()?.ifEmpty { null } }.distinct()
            .mapIndexed { i, name -> Notebook(name = name, id = i + 1L) }
        val notebookIds = notebooks.associate { it.name to it.id }
        fun tagNames(note: ImportedNote) = note.tags.map { it.trim().removePrefix("#").trim() }.filter { it.isNotEmpty() }.distinct()
        val tags = notes.flatMap(::tagNames).distinct().mapIndexed { i, name -> Tag(name = name, id = i + 1L) }
        val tagIds = tags.associate { it.name to it.id }
        val joins = mutableSetOf<NoteTagJoin>()

        val saved = notes.mapIndexed { index, imported ->
            val id = index + 1L
            val stored = imported.files.map { store(it, mediaDir) }
            tagNames(imported).forEach { name -> tagIds[name]?.let { joins += NoteTagJoin(tagId = it, noteId = id) } }
            Note(
                title = imported.title,
                content = placeImages(imported.content, stored.map { name -> name?.let { getAttachmentUri(context, it)?.toString() } }),
                isList = imported.tasks != null,
                taskList = imported.tasks.orEmpty().mapIndexed { i, task -> NoteTask(i.toLong(), task.text, task.done) },
                isArchived = imported.archived,
                isDeleted = imported.deleted,
                isPinned = imported.pinned,
                creationDate = imported.created ?: imported.modified ?: now,
                modifiedDate = imported.modified ?: imported.created ?: now,
                deletionDate = if (imported.deleted) now else null,
                attachments = imported.files.zip(stored).mapNotNull { (file, name) ->
                    name?.let { Attachment(attachmentType(file.mimeType), description = file.name, fileName = it) }
                },
                color = imported.color,
                notebookId = imported.notebook?.trim()?.let { notebookIds[it] },
                id = id,
            )
        }
        backupManager.restoreNotesFromBackup(
            Backup(version = 0, notes = saved.toSet(), notebooks = notebooks.toSet(), tags = tags.toSet(), joins = joins)
        )
        return saved.size
    }

    private fun store(file: ImportedFile, dir: File): String? = runCatching {
        var safe = file.name.substringAfterLast('/').replace(Regex("[^A-Za-z0-9._-]"), "_").takeLast(80).trimStart('.')
        if (safe.isBlank()) safe = "file"
        if ('.' !in safe) safe += "." + file.mimeType.substringAfter('/').substringBefore('+').replace("jpeg", "jpg")
        val name = "import_${UUID.randomUUID().toString().take(8)}_$safe"
        file.open().use { input -> File(dir, name).outputStream().use { input.copyTo(it) } }
        name
    }.getOrNull()

    private fun displayName(uri: Uri): String? = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }.getOrNull() ?: uri.lastPathSegment?.substringAfterLast('/')

    companion object {
        private val placeholder = Regex("""!\[([^\]]*)]\(${Regex.escape(IMPORT_PLACEHOLDER)}(\d+)\)""")

        /** Swaps `![alt](bp-import:N)` for the stored file's path (or just the text if the file could not be copied). */
        fun placeImages(content: String, paths: List<String?>): String = placeholder.replace(content) { match ->
            val (alt, index) = match.destructured
            paths.getOrNull(index.toInt())?.let { "![$alt]($it)" } ?: alt
        }

        fun attachmentType(mimeType: String) = when {
            mimeType.startsWith("image/") -> Attachment.Type.IMAGE
            mimeType.startsWith("audio/") -> Attachment.Type.AUDIO
            mimeType.startsWith("video/") -> Attachment.Type.VIDEO
            else -> Attachment.Type.GENERIC
        }
    }
}
