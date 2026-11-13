package org.qosp.notes.components

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.qosp.notes.BuildConfig
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.ui.attachments.getAttachmentUri
import java.io.File

class MediaStorageManager(
    private val context: Context,
    private val noteRepository: NoteRepository,
    private val mediaFolder: String,
) {
    private val directory get() = File(context.filesDir, mediaFolder)
        .also { it.mkdir() }

    fun listMediaFiles(): List<String> {
        return directory
            .list()
            .orEmpty()
            .toList()
    }

    fun deleteAllMedia() {
        directory.deleteRecursively()
    }

    /**
     * Deletes the media files no note uses. Files changed less than [minAgeMillis] ago are kept: a file just written
     * may not be in a note yet (photo being taken, recording in progress, backup being restored).
     */
    suspend fun cleanUpStorage(
        minAgeMillis: Long = RECENT_FILE_MILLIS,
        now: Long = System.currentTimeMillis(),
    ) = runCatching {
        val filesUsed = noteRepository
            .getAll()
            .first()
            .flatMap { it.attachments }
            .map { it.path }

        val files = directory
            .list()
            .orEmpty()

        for (file in files) {
            if (now - File(directory, file).lastModified() < minAgeMillis) continue
            if (getAttachmentUri(context, file, mediaFolder).toString() !in filesUsed) {
                File(directory, file).delete()
            }
        }
    }

    /***
     * Creates a media file in local storage.
     *
     * @return The file's [Uri] and [File] objects.
     */
    suspend fun createMediaFile(type: MediaType, extension: String = type.defaultExtension): Pair<Uri, File>? {
        return withContext(Dispatchers.IO) {
            runCatching {
                val prefix = when (type) {
                    MediaType.IMAGE -> "img_"
                    MediaType.AUDIO -> "audio_"
                    MediaType.VIDEO -> "video_"
                }

                val file = File.createTempFile(prefix, extension, directory)
                FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.provider", file) to file
            }.getOrNull()
        }
    }

    companion object {
        const val RECENT_FILE_MILLIS = 24L * 60 * 60 * 1000
    }

    enum class MediaType(val defaultExtension: String) {
        IMAGE(".jpg"),
        VIDEO(".mp4"),
        AUDIO(".mp3")
    }
}
