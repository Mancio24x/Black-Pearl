package org.qosp.notes.components.importer

import org.qosp.notes.data.model.NoteColor
import java.net.URLDecoder
import java.security.MessageDigest
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Reads the notes of other apps from the files of one pick (a zip / tar / .jex, or a single file).
 * Recognised by content, not only by extension:
 * Google Keep (Takeout), Simplenote, Standard Notes, Fossify / Simple Notes, Evernote (.enex), Joplin (.jex and raw
 * export), generic note JSON (Notally, Omni Notes…), Markdown (Obsidian, Notesnook, Bear, Joplin…), plain text
 * (converted to Markdown) and HTML (converted to Markdown).
 */
object NoteImporter {
    private val textExtensions = setOf("md", "markdown", "mdown", "mkd", "txt", "text")
    private val htmlExtensions = setOf("html", "htm", "xhtml")
    val noteExtensions = textExtensions + htmlExtensions + setOf("json", "enex")

    fun read(entries: List<ImportEntry>): ImportResult {
        val visible = entries.filterNot { isHidden(it.path) }
        val context = ImportContext(visible)
        val notes = mutableListOf<ImportedNote>()
        val formats = linkedSetOf<String>()
        val skipped = mutableListOf<String>()
        val duplicates = mutableSetOf<String>() // plain files that a structured export also contains
        val joplinItems = mutableListOf<Pair<ImportEntry, JoplinFormat.Item>>()
        val plain = mutableListOf<Pair<ImportEntry, String>>()

        fun json(entry: ImportEntry, text: String): Boolean {
            val result = JsonFormats.read(entry, text, context) ?: return false
            notes += result.notes
            if (result.notes.isNotEmpty()) formats += result.format
            duplicates += result.duplicates
            if (result.notes.isEmpty()) skipped += entry.path
            return true
        }

        for (entry in visible) {
            val extension = entry.extension
            when {
                extension == "json" -> {
                    val text = entry.readText()
                    if (!json(entry, text)) skipped += entry.path
                }
                extension == "enex" -> {
                    val read = runCatching { EnexFormat.read(entry) }.getOrElse { emptyList() }
                    if (read.isEmpty()) skipped += entry.path else formats += "Evernote"
                    notes += read
                }
                extension in textExtensions -> {
                    val text = entry.readText()
                    val item = if (extension == "md") JoplinFormat.parseItem(text) else null
                    when {
                        item != null -> joplinItems += entry to item
                        text.trimStart().startsWith("{") && json(entry, text) -> Unit // e.g. Standard Notes' .txt backup
                        else -> plain += entry to text
                    }
                }
                extension in htmlExtensions -> plain += entry to entry.readText()
            }
        }

        if (joplinItems.isNotEmpty()) {
            val read = JoplinFormat.build(joplinItems, context)
            if (read.isNotEmpty()) formats += "Joplin"
            notes += read
        }

        val plainNotes = plain.filterNot { it.first.extension in duplicates }
        context.useNotebookRoot(plainNotes.map { it.first })
        for ((entry, text) in plainNotes) {
            val note = runCatching { TextFormats.read(entry, text, context) }.getOrNull() ?: continue
            notes += note
            formats += note.format
        }

        return ImportResult(notes.map(::asChecklistIfOnlyTasks).filterNot { it.isEmpty }, formats, skipped)
    }

    /** A note made only of checkboxes becomes a Black Pearl checklist. */
    private fun asChecklistIfOnlyTasks(note: ImportedNote): ImportedNote {
        if (note.tasks != null || note.files.isNotEmpty()) return note
        val tasks = PlainTextMarkdown.asChecklist(note.content) ?: return note
        return note.copy(content = "", tasks = tasks)
    }

    private fun isHidden(path: String): Boolean {
        val segments = path.split('/')
        return segments.any { it.startsWith(".") || it == "__MACOSX" } ||
            segments.last().let { it.startsWith("._") || it.equals("Thumbs.db", true) || it.equals("desktop.ini", true) }
    }
}

/** Looks up the files of one pick: links in notes are relative to the note, or just a file name (Obsidian). */
class ImportContext(val entries: List<ImportEntry>) {
    private val byPath = entries.associateBy { it.path.lowercase() }
    private val byName = entries.groupBy { it.name.lowercase() }
    private var notebookRoot = ""

    fun find(path: String): ImportEntry? = byPath[normalizePath(path).lowercase()]

    fun findByName(name: String): ImportEntry? = byName[name.lowercase()]?.firstOrNull()

    /** [ref] as written in a note in [from]: relative path, URL-encoded path or bare file name. */
    fun resolve(from: ImportEntry, ref: String): ImportEntry? {
        val decoded = runCatching { URLDecoder.decode(ref.replace("+", "%2B"), "UTF-8") }.getOrDefault(ref)
        for (candidate in listOf(ref, decoded).distinct()) {
            val clean = candidate.substringBefore('?').substringBefore('#').trim()
            if (clean.isEmpty()) continue
            val relative = if (from.folder.isEmpty()) clean else from.folder + "/" + clean
            (find(relative) ?: find(clean))?.let { return it }
        }
        return findByName(decoded.substringAfterLast('/'))
    }

    /** Same file name with another extension (Google Takeout sometimes writes .jpeg for a .jpg). */
    fun findSameStem(folder: String, name: String): ImportEntry? {
        val stem = name.substringAfterLast('/').substringBeforeLast('.')
        return entries.firstOrNull { it.folder == folder && it.stem.equals(stem, true) }
            ?: entries.firstOrNull { it.stem.equals(stem, true) && it.extension !in NoteImporter.noteExtensions }
    }

    /** Folders become notebooks, below the folder that holds everything (e.g. the vault's own folder). */
    fun useNotebookRoot(noteEntries: List<ImportEntry>) {
        val folders = noteEntries.map { it.folder.split('/').filter(String::isNotEmpty) }
        if (folders.isEmpty()) return
        var common = folders.first()
        for (f in folders) common = common.zip(f).takeWhile { (a, b) -> a == b }.map { it.first }
        notebookRoot = common.joinToString("/")
    }

    fun notebookFor(entry: ImportEntry): String? =
        entry.folder.removePrefix(notebookRoot).trim('/').substringBefore('/').trim().takeIf { it.isNotEmpty() }

    companion object {
        fun normalizePath(path: String): String {
            val out = ArrayDeque<String>()
            for (segment in path.replace('\\', '/').split('/')) when (segment) {
                "", "." -> Unit
                ".." -> out.removeLastOrNull()
                else -> out.addLast(segment)
            }
            return out.joinToString("/")
        }
    }
}

object Titles {
    private val generic = Regex(
        """^(?:[0-9a-f]{8}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{4}-?[0-9a-f]{12}|[0-9a-f]{16,}|\d+|""" +
            """(?:note|notes|nota|note senza titolo|untitled|untitled note|senza titolo|new note|nuova nota|document|documento|""" +
            """export|backup)(?:[ _-]*\(?\d+\)?)?|\d{4}-\d{2}-\d{2}[ T_]?[\d:.\-_]*)$""",
        RegexOption.IGNORE_CASE,
    )
    private val heading = Regex("""^#{1,6}\s+(.*?)\s*#*$""")
    private val listItem = Regex("""^(?:[-*+]|\d+\.)\s""")

    fun isGeneric(name: String) = name.isBlank() || generic.matches(name.trim())

    /**
     * Title and body of a text note in a file named [stem]: a first line equal to the file name (or a "# heading"
     * when the name says nothing) is the title and is removed from the body.
     */
    fun split(stem: String, body: String): Pair<String, String> {
        val lines = body.split('\n')
        val index = lines.indexOfFirst { it.isNotBlank() }
        val genericStem = isGeneric(stem)
        if (index < 0) return (if (genericStem) "" else stem.trim()) to ""
        val first = lines[index].trim()
        val headingText = heading.matchEntire(first)?.groupValues?.get(1)?.trim()
        val firstText = headingText ?: first
        val rest = lines.drop(index + 1).joinToString("\n").trim('\n')
        return when {
            !genericStem && simplify(firstText) == simplify(stem) -> firstText to rest
            genericStem && headingText != null -> headingText to rest
            genericStem && first.length <= 80 && !listItem.containsMatchIn(first) -> first to rest
            genericStem -> "" to body.trim('\n')
            else -> stem.trim() to body.trim('\n')
        }
    }

    /** Removes a first line repeating [title] (many exports put the title both outside and inside the text). */
    fun dropRepeatedTitle(body: String, title: String): String {
        val lines = body.split('\n')
        val index = lines.indexOfFirst { it.isNotBlank() }
        if (index < 0) return body
        val first = lines[index].trim()
        val text = heading.matchEntire(first)?.groupValues?.get(1)?.trim() ?: first
        return if (simplify(text) == simplify(title)) lines.drop(index + 1).joinToString("\n").trim('\n') else body
    }

    // File names cannot hold some characters: compare letters and digits only
    private fun simplify(text: String) = text.lowercase().filter { it.isLetterOrDigit() }
}

object Dates {
    private val compact = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")

    /** Epoch seconds from seconds, milliseconds or microseconds. */
    fun fromNumber(value: Long): Long? = when {
        value <= 0 -> null
        value > 100_000_000_000_000L -> value / 1_000_000
        value > 100_000_000_000L -> value / 1000
        else -> value
    }

    /** ISO 8601 (with or without zone), Evernote's compact form, "yyyy-MM-dd HH:mm:ss", plain dates or numbers. */
    fun parse(value: String?): Long? {
        val text = value?.trim()?.trim('"')?.takeIf { it.isNotEmpty() } ?: return null
        text.toLongOrNull()?.let { return fromNumber(it) }
        text.toDoubleOrNull()?.let { return fromNumber(it.toLong()) }
        val iso = text.replace(' ', 'T').let { if (Regex("""[+-]\d{4}$""").containsMatchIn(it)) it.dropLast(2) + ":" + it.takeLast(2) else it }
        return runCatching { OffsetDateTime.parse(iso).toEpochSecond() }.getOrNull()
            ?: runCatching { Instant.parse(iso).epochSecond }.getOrNull()
            ?: runCatching { LocalDateTime.parse(iso).atZone(ZoneId.systemDefault()).toEpochSecond() }.getOrNull()
            ?: runCatching { LocalDateTime.parse(text, compact).toEpochSecond(ZoneOffset.UTC) }.getOrNull()
            ?: runCatching { LocalDate.parse(text.take(10)).atStartOfDay(ZoneId.systemDefault()).toEpochSecond() }.getOrNull()
    }
}

object Colors {
    /** Color names of Black Pearl, Google Keep ("CERULEAN"…) and other apps ("grey", "white"…). */
    fun of(name: String?): NoteColor {
        val key = name?.trim()?.lowercase()?.takeIf { it.isNotEmpty() } ?: return NoteColor.Default
        NoteColor.entries.firstOrNull { it.name.lowercase() == key }?.let { return it }
        return when (key) {
            "cerulean", "light_blue", "lightblue", "sky" -> NoteColor.Cyan
            "grey", "graphite" -> NoteColor.Gray
            "violet", "lavender" -> NoteColor.Purple
            "mint", "sage" -> NoteColor.Teal
            "sand", "beige" -> NoteColor.Brown
            "dark_blue", "navy", "indigo" -> NoteColor.Blue
            "lime", "olive" -> NoteColor.Green
            "amber", "gold" -> NoteColor.Yellow
            "rose", "magenta" -> NoteColor.Pink
            else -> NoteColor.Default
        }
    }
}

/** Base64 (also with line breaks, as in Evernote files); java.util.Base64 is not available before Android 8. */
object Base64Data {
    private val table = IntArray(128) { -1 }.also { t ->
        "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/".forEachIndexed { i, c -> t[c.code] = i }
        t['-'.code] = 62
        t['_'.code] = 63
    }

    fun decode(text: CharSequence): ByteArray {
        val out = java.io.ByteArrayOutputStream(text.length * 3 / 4)
        var buffer = 0
        var bits = 0
        for (c in text) {
            if (c == '=') break
            val value = if (c.code < 128) table[c.code] else -1
            if (value < 0) continue
            buffer = (buffer shl 6) or value
            bits += 6
            if (bits >= 8) {
                bits -= 8
                out.write((buffer shr bits) and 0xFF)
            }
        }
        return out.toByteArray()
    }

    /** "data:image/png;base64,…" → file, or null. */
    fun dataUrl(url: String, baseName: String): ImportedFile? {
        if (!url.startsWith("data:", true) || !url.contains(";base64,")) return null
        val mime = url.substring(5).substringBefore(';').ifBlank { "application/octet-stream" }
        val bytes = decode(url.substringAfter(";base64,"))
        if (bytes.isEmpty()) return null
        val extension = mime.substringAfter('/').substringBefore('+').replace("jpeg", "jpg")
        return ImportedFile.of("$baseName.$extension", mime, bytes)
    }
}

fun md5Hex(bytes: ByteArray): String =
    MessageDigest.getInstance("MD5").digest(bytes).joinToString("") { "%02x".format(it) }
