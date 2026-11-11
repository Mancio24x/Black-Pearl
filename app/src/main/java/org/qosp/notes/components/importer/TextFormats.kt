package org.qosp.notes.components.importer

import org.jsoup.Jsoup
import org.jsoup.nodes.Element

/**
 * One note per file: Markdown (Obsidian, Notesnook, Bear, Joplin "MD" export, Markor…), plain text (converted to
 * Markdown) and HTML (converted to Markdown). Folders become notebooks; pictures linked from the note are imported.
 */
object TextFormats {

    fun read(entry: ImportEntry, text: String, context: ImportContext): ImportedNote? = when (entry.extension) {
        "html", "htm", "xhtml" -> html(entry, text, context)
        "txt", "text" -> plainText(entry, text, context)
        else -> markdown(entry, text, context)
    }

    private fun plainText(entry: ImportEntry, text: String, context: ImportContext): ImportedNote {
        val (title, body) = Titles.split(entry.stem, PlainTextMarkdown.convert(text))
        return ImportedNote(title, body, notebook = context.notebookFor(entry), modified = entry.modified, format = "TXT")
    }

    private fun markdown(entry: ImportEntry, text: String, context: ImportContext): ImportedNote {
        val front = FrontMatter.parse(PlainTextMarkdown.normalize(text))
        val files = NoteFiles()
        val linked = MarkdownLinks.resolve(front.body, entry, context, files)
        val (title, body) = front.title?.let { it to Titles.dropRepeatedTitle(linked, it) } ?: Titles.split(entry.stem, linked)
        val extra = front.other.joinToString("\n") { (key, value) -> "$key: $value" }
        return ImportedNote(
            title = title,
            content = listOf(body, extra).filter { it.isNotBlank() }.joinToString("\n\n"),
            color = Colors.of(front.color),
            pinned = front.pinned,
            archived = front.archived,
            tags = front.tags,
            notebook = context.notebookFor(entry),
            created = front.created,
            modified = front.modified ?: entry.modified,
            files = files.files,
            format = "Markdown",
        )
    }

    private fun html(entry: ImportEntry, text: String, context: ImportContext): ImportedNote {
        val document = Jsoup.parse(text)
        val files = NoteFiles()
        val markdown = HtmlMarkdown { image(it, entry, context, files) }.convert(document.body())
        val htmlTitle = document.title().trim()
        val (title, body) = if (htmlTitle.isNotEmpty() && !Titles.isGeneric(htmlTitle)) {
            htmlTitle to Titles.dropRepeatedTitle(markdown, htmlTitle)
        } else {
            Titles.split(entry.stem, markdown)
        }
        return ImportedNote(title, body, notebook = context.notebookFor(entry), modified = entry.modified, files = files.files, format = "HTML")
    }

    /** <img src> of an HTML note: embedded (data:), next to the file, or on the web (kept as a link). */
    private fun image(element: Element, entry: ImportEntry, context: ImportContext, files: NoteFiles): String? {
        val src = element.attr("src").trim()
        val alt = element.attr("alt").trim()
        return when {
            src.isEmpty() -> null
            src.startsWith("data:", true) -> Base64Data.dataUrl(src, "image")?.let { files.image(it, alt) }
            src.startsWith("http://", true) || src.startsWith("https://", true) -> "![${alt}]($src)"
            else -> context.resolve(entry, src)?.let { files.image(ImportedFile.of(it), alt) }
        }
    }
}

/** Links inside Markdown notes that point to files of the same export. */
object MarkdownLinks {
    private val wikiEmbed = Regex("""!\[\[([^\]]+)]]""")
    private val image = Regex("""!\[([^\]]*)]\(\s*<?([^)<>\s]+)>?(?:\s+["'][^"']*["'])?\s*\)""")
    private val fileLink = Regex("""(?<!!)\[([^\]]*)]\(\s*<?([^)<>\s]+)>?\s*\)""")
    private val scheme = Regex("""^[a-zA-Z][a-zA-Z0-9+.-]*:""")

    fun resolve(markdown: String, entry: ImportEntry, context: ImportContext, files: NoteFiles): String {
        var out = wikiEmbed.replace(markdown) { m -> // Obsidian: ![[photo.png]] or ![[photo.png|300]]
            val target = m.groupValues[1].substringBefore('|').substringBefore('#').trim()
            val found = context.resolve(entry, target) ?: return@replace m.value
            val file = ImportedFile.of(found)
            if (isImage(file.mimeType)) files.image(file, found.name) else {
                files.add(file)
                found.name
            }
        }
        out = image.replace(out) { m ->
            val (alt, url) = m.destructured
            when {
                url.startsWith("data:", true) -> Base64Data.dataUrl(url, "image")?.let { files.image(it, alt) } ?: m.value
                scheme.containsMatchIn(url) -> m.value // web image, or a Black Pearl path
                else -> context.resolve(entry, url)?.let { files.image(ImportedFile.of(it), alt) } ?: m.value
            }
        }
        out = fileLink.replace(out) { m -> // links to other notes or to files of the export
            val (label, url) = m.destructured
            if (scheme.containsMatchIn(url) || url.startsWith("#")) return@replace m.value
            val found = context.resolve(entry, url) ?: return@replace m.value
            if (found.extension !in NoteImporter.noteExtensions) files.add(ImportedFile.of(found))
            label.ifBlank { found.stem }
        }
        return out
    }
}

/** YAML front matter of Markdown notes (Obsidian, Notesnook, Joplin, Hugo…), simple keys only. */
class FrontMatter private constructor(
    val body: String,
    val title: String?,
    val tags: List<String>,
    val created: Long?,
    val modified: Long?,
    val pinned: Boolean,
    val archived: Boolean,
    val color: String?,
    val other: List<Pair<String, String>>,
) {
    companion object {
        private val known = setOf("title", "tags", "tag", "keywords", "labels", "created", "created_at", "createdat", "date",
            "creation_date", "updated", "updated_at", "updatedat", "modified", "lastmod", "date_modified", "pinned",
            "archived", "color", "colour", "aliases", "id", "uid", "favorite")

        fun parse(text: String): FrontMatter {
            val lines = text.split('\n')
            val end = if (lines.firstOrNull()?.trim() == "---") {
                lines.drop(1).indexOfFirst { it.trim() == "---" || it.trim() == "..." }.takeIf { it >= 0 }?.plus(1)
            } else null
            if (end == null) return FrontMatter(text, null, emptyList(), null, null, false, false, null, emptyList())

            val values = linkedMapOf<String, MutableList<String>>()
            var current: String? = null
            for (line in lines.subList(1, end)) {
                val item = Regex("""^\s+-\s+(.*)$""").matchEntire(line)
                if (item != null && current != null) {
                    values.getValue(current) += unquote(item.groupValues[1])
                    continue
                }
                val pair = Regex("""^([A-Za-z_][\w-]*)\s*:\s*(.*)$""").matchEntire(line) ?: continue
                current = pair.groupValues[1]
                val value = pair.groupValues[2].trim()
                values[current] = when {
                    value.startsWith("[") && value.endsWith("]") ->
                        value.trim('[', ']').split(',').map(::unquote).filter { it.isNotEmpty() }.toMutableList()
                    value.isEmpty() -> mutableListOf()
                    else -> mutableListOf(unquote(value))
                }
            }
            fun first(vararg keys: String) = keys.firstNotNullOfOrNull { k -> values.entries.firstOrNull { it.key.equals(k, true) }?.value?.firstOrNull() }
            fun all(vararg keys: String) = keys.flatMap { k -> values.entries.filter { it.key.equals(k, true) }.flatMap { it.value } }
            fun flag(key: String) = first(key)?.lowercase() in setOf("true", "yes", "1")
            return FrontMatter(
                body = lines.drop(end + 1).joinToString("\n").trim('\n'),
                title = first("title")?.takeIf { it.isNotBlank() },
                tags = all("tags", "tag", "keywords", "labels").flatMap { it.split(' ') }.map { it.trim().removePrefix("#") }.filter { it.isNotEmpty() },
                created = Dates.parse(first("created", "created_at", "createdAt", "date", "creation_date")),
                modified = Dates.parse(first("updated", "updated_at", "updatedAt", "modified", "lastmod", "date_modified")),
                pinned = flag("pinned"),
                archived = flag("archived"),
                color = first("color", "colour"),
                other = values.filterKeys { it.lowercase() !in known }.map { (k, v) -> k to v.joinToString(", ") }
                    .filter { it.second.isNotBlank() },
            )
        }

        private fun unquote(text: String) = text.trim().removeSurrounding("\"").removeSurrounding("'").trim()
    }
}
