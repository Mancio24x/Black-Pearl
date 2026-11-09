package org.qosp.notes.components.importer

import org.jsoup.Jsoup

/**
 * Joplin (.jex archive or "RAW" export folder): one Markdown file per item, "title, blank line, body, blank line,
 * metadata lines" (`id: …`, `parent_id: …`, `type_: 1` note / 2 notebook / 4 resource / 5 tag / 6 note-tag).
 * Attachments are in resources/<id>.<ext> and linked from notes as `:/<id>`.
 */
object JoplinFormat {

    class Item(val title: String, val body: String, val meta: Map<String, String>) {
        val id: String get() = meta.getValue("id")
        val type: Int get() = meta["type_"]?.toIntOrNull() ?: 0
    }

    private val metaLine = Regex("""^([a-z_]+):(?: (.*))?$""")
    private val resourceLink = Regex("""(!?)\[([^\]]*)]\(:/([0-9a-f]{32})\)""")
    private val htmlResource = Regex("""src=["']:/([0-9a-f]{32})["']""")

    fun parseItem(text: String): Item? {
        val lines = PlainTextMarkdown.normalize(text).split('\n').toMutableList()
        while (lines.isNotEmpty() && lines.last().isBlank()) lines.removeAt(lines.lastIndex)
        val meta = LinkedHashMap<String, String>()
        var index = lines.size
        while (index > 0) {
            val match = metaLine.matchEntire(lines[index - 1]) ?: break
            meta[match.groupValues[1]] = match.groupValues[2]
            index--
        }
        if (meta["id"]?.matches(Regex("[0-9a-f]{32}")) != true || "type_" !in meta) return null
        val content = lines.subList(0, index)
        val title = content.firstOrNull().orEmpty().trim()
        val body = content.drop(2).joinToString("\n").trimEnd()
        return Item(title, body, meta)
    }

    fun build(items: List<Pair<ImportEntry, Item>>, context: ImportContext): List<ImportedNote> {
        val all = items.map { it.second }
        val folders = all.filter { it.type == 2 }.associate { it.id to it.title }
        val tags = all.filter { it.type == 5 }.associate { it.id to it.title }
        val resources = all.filter { it.type == 4 }.associateBy { it.id }
        val noteTags = all.filter { it.type == 6 }.groupBy({ it.meta["note_id"].orEmpty() }, { it.meta["tag_id"].orEmpty() })

        fun resourceFile(id: String): ImportedFile? {
            val resource = resources[id] ?: return null
            val entry = context.entries.firstOrNull { it.stem == id && it.folder.endsWith("resources") }
                ?: context.entries.firstOrNull { it.stem == id && it.extension != "md" } ?: return null
            val name = resource.title.ifBlank { entry.name }
            return ImportedFile(name, resource.meta["mime"]?.ifBlank { null } ?: mimeTypeOf(entry.name)) { entry.open() }
        }

        return all.filter { it.type == 1 }.map { item ->
            val files = NoteFiles()
            val cache = mutableMapOf<String, ImportedFile?>()
            fun file(id: String) = cache.getOrPut(id) { resourceFile(id) }

            var body = item.body
            if (item.meta["markup_language"] == "2") { // HTML note
                body = HtmlMarkdown { element ->
                    val id = htmlResource.find(element.outerHtml())?.groupValues?.get(1)
                    id?.let(::file)?.let { files.image(it, element.attr("alt")) }
                }.convert(Jsoup.parseBodyFragment(body).body())
            }
            body = resourceLink.replace(body) { match ->
                val (bang, label, id) = match.destructured
                val f = file(id) ?: return@replace label // link to another note: keep its text
                if (bang == "!" && isImage(f.mimeType)) files.image(f, label) else {
                    files.add(f)
                    label.ifBlank { f.name }
                }
            }
            val deletedTime = item.meta["deleted_time"]?.trim()
            ImportedNote(
                title = item.title,
                content = body,
                notebook = folders[item.meta["parent_id"]],
                tags = noteTags[item.id].orEmpty().mapNotNull { tags[it] },
                deleted = !deletedTime.isNullOrEmpty() && deletedTime != "0",
                created = Dates.parse(item.meta["user_created_time"] ?: item.meta["created_time"]),
                modified = Dates.parse(item.meta["user_updated_time"] ?: item.meta["updated_time"]),
                files = files.files,
                format = "Joplin",
            )
        }
    }
}
