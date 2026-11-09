package org.qosp.notes.components.importer

import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.Reader

/**
 * Evernote export (.enex): XML with one <note> per note, ENML content (XHTML with <en-todo> and <en-media>) and
 * attachments as base64 <resource>s matched to <en-media hash> by MD5. Read one note at a time, so big
 * notebooks do not need to fit in memory at once. A file with several notes becomes a notebook of the same name.
 */
object EnexFormat {

    fun read(entry: ImportEntry): List<ImportedNote> {
        val notes = mutableListOf<ImportedNote>()
        entry.open().reader(Charsets.UTF_8).buffered().use { reader ->
            eachNote(reader) { xml -> runCatching { note(xml) }.getOrNull()?.let { notes += it } }
        }
        val notebook = entry.stem.takeIf { notes.size > 1 && !Titles.isGeneric(it) }
        return notes.map { it.copy(notebook = notebook) }
    }

    /** Streams [reader] and hands out each "<note>…</note>" block. */
    fun eachNote(reader: Reader, onNote: (String) -> Unit) {
        val buffer = StringBuilder()
        val chunk = CharArray(64 * 1024)
        var inside = false
        while (true) {
            val read = reader.read(chunk)
            if (read < 0) break
            buffer.append(chunk, 0, read)
            while (true) {
                if (!inside) {
                    val start = buffer.indexOf("<note>")
                    if (start < 0) {
                        if (buffer.length > 16) buffer.delete(0, buffer.length - 16) // keep a possible partial tag
                        break
                    }
                    buffer.delete(0, start)
                    inside = true
                }
                val end = buffer.indexOf("</note>")
                if (end < 0) break
                onNote(buffer.substring(0, end + "</note>".length))
                buffer.delete(0, end + "</note>".length)
                inside = false
            }
        }
    }

    private fun note(xml: String): ImportedNote {
        val note = Jsoup.parse(xml, "", Parser.xmlParser()).selectFirst("note")!!
        val files = NoteFiles()
        val resources = linkedMapOf<String, ImportedFile>()
        note.select("resource").forEachIndexed { index, resource ->
            val data = resource.selectFirst("data")?.wholeText() ?: return@forEachIndexed
            val bytes = Base64Data.decode(data)
            if (bytes.isEmpty()) return@forEachIndexed
            val mime = resource.selectFirst("mime")?.text()?.trim()?.ifEmpty { null } ?: "application/octet-stream"
            val name = resource.selectFirst("resource-attributes > file-name")?.text()?.trim()?.ifEmpty { null }
                ?: "attachment-${index + 1}.${mime.substringAfter('/').substringBefore('+').replace("jpeg", "jpg")}"
            resources[md5Hex(bytes)] = ImportedFile.of(name, mime, bytes)
        }
        val enml = note.selectFirst("content")?.wholeText().orEmpty()
        val body: Element = Jsoup.parse(enml, "", Parser.xmlParser()).let { it.selectFirst("en-note") ?: it }
        val markdown = HtmlMarkdown { element ->
            val file = resources[element.attr("hash").lowercase()]
            when {
                file == null -> null
                isImage(file.mimeType) -> files.image(file, file.name)
                else -> {
                    files.add(file)
                    null
                }
            }
        }.convert(body)
        resources.values.forEach { files.add(it) } // attachments not placed in the text
        return ImportedNote(
            title = note.selectFirst("title")?.text().orEmpty().trim(),
            content = markdown,
            tags = note.select("tag").map { it.text().trim() }.filter { it.isNotEmpty() },
            created = Dates.parse(note.selectFirst("created")?.text()),
            modified = Dates.parse(note.selectFirst("updated")?.text()),
            files = files.files,
            format = "Evernote",
        )
    }
}
