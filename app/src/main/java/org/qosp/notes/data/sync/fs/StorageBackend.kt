package org.qosp.notes.data.sync.fs

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.AvailabilityStatus
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.data.sync.nextcloud.BackendValidationResult
import org.qosp.notes.preferences.CloudService
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import kotlin.time.measureTimedValue

class StorageBackend(private val context: Context, private val config: StorageConfig) : ISyncBackend {

    companion object {
        private const val TAG = "StorageBackend"
    }

    override val type: CloudService = CloudService.FILE_STORAGE

    override suspend fun isAvailable(): AvailabilityStatus {
        return try {
            val root = getRootDocumentFile()
            when {
                root == null -> AvailabilityStatus.Unavailable("Storage location not accessible")
                !hasPermissionsAt(config.location) -> AvailabilityStatus.Unavailable("Missing storage permissions - please reconfigure sync")
                !root.canRead() -> AvailabilityStatus.Unavailable("Cannot read from storage location")
                !root.canWrite() -> AvailabilityStatus.Unavailable("Cannot write to storage location")
                else -> AvailabilityStatus.Available
            }
        } catch (e: Exception) {
            AvailabilityStatus.Unavailable("Storage access error: ${e.message ?: "Unknown error"}")
        }
    }

    private val Note.filename: String
        get() {
            val titleToSet = title.ifBlank { "Untitled" }
            val ext = if (isMarkdownEnabled) "md" else "txt"
            return "${titleToSet.trim()}.$ext"
        }

    override suspend fun createNote(note: Note): SyncNote {
        val root = getRootDocumentFile() ?: throw IOException("Unable to access storage location")

        Log.d(TAG, "createNote: $note")
        val mimeType = if (note.isMarkdownEnabled) "text/markdown" else "text/plain"
        val newDoc = root.createFile(mimeType, note.filename)

        return newDoc?.let {
            writeNoteToFile(it, note.toStorableContent())
            SyncNote(
                idStr = newDoc.uri.toString(),
                title = note.title,
                content = note.toStorableContent(),
                lastModified = newDoc.lastModified() / 1000, // Epoch milliseconds to seconds
                id = 0
            )
        } ?: throw IOException("Unable to create file for ${note.filename}")
    }

    override suspend fun updateNote(note: Note, mapping: IdMapping): IdMapping {
        val uri = mapping.storageUri?.toUri() ?: throw IllegalArgumentException("URI cannot be null")
        val file = DocumentFile.fromSingleUri(context, uri) ?: throw FileNotFoundException("URI not found")

        writeNoteToFile(file, content = note.toStorableContent())
        val newUri = if (note.filename != file.name) renameFile(file, note.filename) else uri
        return mapping.copy(storageUri = newUri.toString())
    }

    override suspend fun deleteNote(mapping: IdMapping): Boolean {
        val uri = mapping.storageUri?.toUri() ?: return false
        val deletionResult = inStorage {
            val result = DocumentsContract.deleteDocument(context.contentResolver, uri)
            Log.d(TAG, "deleteNote: Deleted (${result}) the file: ${uri.pathSegments.last()}")
            result
        }
        return deletionResult == true
    }

    override suspend fun getNote(mapping: IdMapping): SyncNote? {
        return try {
            val uri = mapping.storageUri?.toUri() ?: return null
            val file = DocumentFile.fromSingleUri(context, uri) ?: return null
            getFile(file)
        } catch (e: Exception) {
            Log.e(TAG, "getNote: Error getting note with id ${mapping.localNoteId}", e)
            null
        }
    }

    private fun getFile(file: DocumentFile) = SyncNote(
        id = 0,
        idStr = file.uri.toString(),
        content = readFileContent(file),
        title = getTitleFromUri(file.uri),
        lastModified = file.lastModified() / 1000, // Milliseconds to seconds
    )

    override suspend fun getAll(): List<SyncNote>? {
        val root = getRootDocumentFile() ?: return null
        return try {
            // Black Pearl: DocumentFile.listFiles() swallows query errors and returns a short/empty array, which made
            // the sync bin notes. Query the children ourselves and fail (null = abort the sync) on any problem.
            val files = listChildren(root.uri)
                .flatMap {
                    if (it.isDirectory) {
                        if (it.name.startsWith(".")) emptyList() // Skip hidden directories
                        else listChildren(it.uri)
                    } else listOf(it)
                }
                .filter { !it.isDirectory }
                .filter { !it.name.startsWith(".") } // Skip hidden files
                .filter { it.name.endsWith(".md") || it.name.endsWith(".txt") }
            files.map { file ->
                SyncNote(
                    id = 0,
                    idStr = file.uri.toString(),
                    content = readFileContent(file.uri, file.name),
                    title = getTitleFromUri(file.uri),
                    lastModified = file.lastModifiedMs / 1000, // Milliseconds to seconds
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "getAll: Error listing files", e)
            null
        }
    }

    private class Child(val uri: Uri, val name: String, val isDirectory: Boolean, val lastModifiedMs: Long)

    /**
     * Lists the direct children of the document at [parentUri] (a document URI inside the tree). Builds the child
     * URIs exactly like TreeDocumentFile.listFiles(), so existing mappings keep matching.
     * Throws when the provider gives no cursor or says the listing is still loading (incomplete).
     */
    private fun listChildren(parentUri: Uri): List<Child> {
        val childrenUri =
            DocumentsContract.buildChildDocumentsUriUsingTree(parentUri, DocumentsContract.getDocumentId(parentUri))
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val cursor = context.contentResolver.query(childrenUri, projection, null, null, null)
            ?: throw IOException("No cursor when listing $parentUri")
        return cursor.use { c ->
            if (c.extras?.getBoolean(DocumentsContract.EXTRA_LOADING, false) == true) {
                throw IOException("Listing of $parentUri is still loading")
            }
            buildList {
                while (c.moveToNext()) {
                    val docId = c.getString(0) ?: continue
                    add(
                        Child(
                            uri = DocumentsContract.buildDocumentUriUsingTree(parentUri, docId),
                            name = c.getString(1) ?: "",
                            isDirectory = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                            lastModifiedMs = if (c.isNull(3)) 0L else c.getLong(3),
                        )
                    )
                }
            }
        }
    }

    suspend fun validateConfig(): BackendValidationResult {
        return try {
            val root = getRootDocumentFile()
            if (root == null || !hasPermissionsAt(config.location)) {
                BackendValidationResult.InvalidConfig
            } else {
                BackendValidationResult.Success
            }
        } catch (e: Exception) {
            Log.e(TAG, "validateConfig: Error validating config", e)
            BackendValidationResult.InvalidConfig
        }
    }

    private fun getRootDocumentFile(): DocumentFile? {
        return DocumentFile.fromTreeUri(context, config.location)
    }

    private fun hasPermissionsAt(uri: Uri): Boolean {
        val perm = context.contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        return perm?.let { it.isReadPermission && it.isWritePermission } ?: false
    }

    private fun writeNoteToFile(file: DocumentFile, content: String) {
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { output ->
            (output as? FileOutputStream)?.let {
                val bytesWritten = content.encodeToByteArray().inputStream().copyTo(output)
                Log.d(TAG, "writeNote: Wrote $bytesWritten bytes to ${file.name}")
            } ?: run {
                Log.e(TAG, "writeNoteToDocument: ${file.name} is not a file. URI:${file.uri}")
            }
        }
    }

    private fun readFileContent(file: DocumentFile): String? = readFileContent(file.uri, file.name)

    private fun readFileContent(uri: Uri, name: String?): String? {
        Log.d(TAG, "readFileContent: $name")
        return context.contentResolver.openInputStream(uri)?.use { it.bufferedReader().readText() }
    }

    private fun getTitleFromUri(uri: Uri): String {
        val fileName = uri.lastPathSegment?.substringAfterLast('/') ?: ""
        return when {
            fileName.endsWith(".md") -> fileName.removeSuffix(".md")
            fileName.endsWith(".txt") -> fileName.removeSuffix(".txt")
            else -> fileName
        }
    }

    /**
     * Renames the mapped document itself (not a same-named file found in the root) and returns its new URI.
     * On failure the content is already written, so the old URI is kept and the rename is retried next time.
     */
    private fun renameFile(file: DocumentFile, newName: String): Uri {
        Log.d(TAG, "renameFile: Renaming ${file.name} to $newName")
        return try {
            DocumentsContract.renameDocument(context.contentResolver, file.uri, newName) ?: file.uri
        } catch (e: Exception) {
            Log.e(TAG, "renameFile: Could not rename ${file.name} to $newName", e)
            file.uri
        }
    }

    private inline fun <reified T> inStorage(block: () -> T): T? {
        return try {
            val duration = measureTimedValue {
                block()
            }
            Log.i(TAG, "inStorage: That took ${duration.duration} to complete")
            duration.value
        } catch (e: Exception) {
            Log.e(TAG, "Exception while storing: ${e.message}", e)
            null
        }
    }
}
