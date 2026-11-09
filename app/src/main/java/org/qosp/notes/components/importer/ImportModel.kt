package org.qosp.notes.components.importer

import org.qosp.notes.data.model.NoteColor
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.Charset

/** One file of what the user picked: a loose file, or an entry of a zip / tar archive ([modified]: epoch seconds). */
class ImportEntry(val path: String, val modified: Long? = null, private val opener: () -> InputStream) {
    val name: String get() = path.substringAfterLast('/')
    val stem: String get() = name.substringBeforeLast('.').ifEmpty { name }
    val extension: String get() = name.substringAfterLast('.', "").lowercase()
    val folder: String get() = path.substringBeforeLast('/', "")
    fun open(): InputStream = opener()
    fun readBytes(): ByteArray = open().use { it.readBytes() }
    fun readText(): String = TextDecoding.decode(readBytes())
}

/** A file to attach to an imported note: an archive entry, or bytes embedded in the export (Evernote, data: URLs). */
class ImportedFile(val name: String, val mimeType: String, private val opener: () -> InputStream) {
    fun open(): InputStream = opener()

    companion object {
        fun of(entry: ImportEntry, mimeType: String? = null) =
            ImportedFile(entry.name, mimeType ?: mimeTypeOf(entry.name)) { entry.open() }

        fun of(name: String, mimeType: String?, bytes: ByteArray) =
            ImportedFile(name, mimeType ?: mimeTypeOf(name)) { bytes.inputStream() }
    }
}

data class ImportedTask(val text: String, val done: Boolean)

/**
 * A note read from another app, already converted to Markdown.
 * Images placed in the text are written as `![alt](bp-import:N)` (N = index in [files]); the writer swaps in the
 * real attachment path once the file is copied.
 */
data class ImportedNote(
    val title: String,
    val content: String = "",
    val tasks: List<ImportedTask>? = null,
    val color: NoteColor = NoteColor.Default,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val deleted: Boolean = false,
    val tags: List<String> = emptyList(),
    val notebook: String? = null,
    val created: Long? = null, // epoch seconds
    val modified: Long? = null,
    val files: List<ImportedFile> = emptyList(),
    val format: String = "",
) {
    val isEmpty: Boolean get() = title.isBlank() && content.isBlank() && tasks.isNullOrEmpty() && files.isEmpty()
}

data class ImportResult(
    val notes: List<ImportedNote>,
    /** Formats recognised, e.g. "Google Keep", "Markdown". */
    val formats: Set<String>,
    /** Note-like files that could not be read (unknown JSON, encrypted backups…). */
    val skipped: List<String>,
)

const val IMPORT_PLACEHOLDER = "bp-import:"

/** Collects the files of one note and hands out their placeholders. */
class NoteFiles {
    val files = mutableListOf<ImportedFile>()
    fun add(file: ImportedFile): Int = files.indexOfFirst { it === file }.takeIf { it >= 0 } ?: run {
        files += file
        files.lastIndex
    }
    fun image(file: ImportedFile, alt: String): String = "![${alt.ifBlank { file.name }}]($IMPORT_PLACEHOLDER${add(file)})"
}

fun isImage(mimeType: String) = mimeType.startsWith("image/")

fun mimeTypeOf(name: String): String = when (name.substringAfterLast('.', "").lowercase()) {
    "png" -> "image/png"
    "jpg", "jpeg", "jfif" -> "image/jpeg"
    "gif" -> "image/gif"
    "webp" -> "image/webp"
    "heic", "heif" -> "image/heic"
    "bmp" -> "image/bmp"
    "svg" -> "image/svg+xml"
    "mp3" -> "audio/mpeg"
    "m4a", "aac" -> "audio/mp4"
    "ogg", "oga", "opus" -> "audio/ogg"
    "wav" -> "audio/wav"
    "amr" -> "audio/amr"
    "3gp", "3gpp" -> "audio/3gpp"
    "flac" -> "audio/flac"
    "mp4", "m4v" -> "video/mp4"
    "webm" -> "video/webm"
    "mkv" -> "video/x-matroska"
    "mov" -> "video/quicktime"
    "pdf" -> "application/pdf"
    else -> "application/octet-stream"
}

/** Text files from other apps come in many encodings: BOMs (UTF-8/16), plain UTF-8, or old Windows encodings. */
object TextDecoding {
    private val windows1252: Charset = runCatching { Charset.forName("windows-1252") }.getOrDefault(Charsets.ISO_8859_1)

    fun decode(bytes: ByteArray): String {
        fun startsWith(vararg b: Int) = bytes.size >= b.size && b.indices.all { bytes[it] == b[it].toByte() }
        return when {
            startsWith(0xEF, 0xBB, 0xBF) -> String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
            startsWith(0xFF, 0xFE) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
            startsWith(0xFE, 0xFF) -> String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
            else -> runCatching {
                Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString()
            }.getOrElse { String(bytes, windows1252) }
        }
    }
}
