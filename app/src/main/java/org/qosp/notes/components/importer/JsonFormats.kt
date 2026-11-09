package org.qosp.notes.components.importer

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull
import org.jsoup.Jsoup

/** JSON exports: Google Keep, Simplenote, Standard Notes, Fossify / Simple Notes and generic note JSON. */
object JsonFormats {

    class Result(val format: String, val notes: List<ImportedNote>, val duplicates: Set<String> = emptySet())

    fun read(entry: ImportEntry, text: String, context: ImportContext): Result? {
        val json = runCatching { Json.parseToJsonElement(text) }.getOrNull() ?: return null
        val obj = json as? JsonObject
        val array = json as? JsonArray
        return when {
            obj != null && isKeep(obj) -> Result("Google Keep", listOf(keep(obj, entry, context)), setOf("html", "htm", "txt"))
            obj != null && obj["activeNotes"] is JsonArray -> Result("Simplenote", simplenote(obj), setOf("txt", "text"))
            obj != null && isStandardNotes(obj) -> Result("Standard Notes", standardNotes(obj), setOf("txt", "text", "md"))
            array != null && isFossify(array) -> Result("Fossify Notes", array.mapNotNull { fossify(it.obj) })
            else -> generic(json, entry)?.let { Result("JSON", it) }
        }
    }

    // ---------- Google Keep (Google Takeout: Takeout/Keep/<note>.json + attachments next to it) ----------

    private fun isKeep(o: JsonObject) = ("textContent" in o || "listContent" in o) &&
        ("userEditedTimestampUsec" in o || "createdTimestampUsec" in o || "isTrashed" in o)

    private fun keep(o: JsonObject, entry: ImportEntry, context: ImportContext): ImportedNote {
        val files = NoteFiles()
        o.array("attachments").forEach { item ->
            val a = item.obj ?: return@forEach
            val path = a.string("filePath") ?: return@forEach
            val found = context.resolve(entry, path) ?: context.findSameStem(entry.folder, path) ?: return@forEach
            files.add(ImportedFile.of(found, a.string("mimetype")))
        }
        val links = o.array("annotations").mapNotNull { item ->
            val a = item.obj ?: return@mapNotNull null
            val url = a.string("url")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val title = a.string("title")?.trim()
            if (title.isNullOrEmpty() || title == url) url else "[$title]($url)"
        }
        val tasks = o.array("listContent").mapNotNull { item ->
            item.obj?.let { ImportedTask(it.string("text").orEmpty().trim(), it.bool("isChecked")) }
        }.takeIf { "listContent" in o }
        val text = PlainTextMarkdown.convert(o.string("textContent").orEmpty())
        return ImportedNote(
            title = o.string("title").orEmpty().trim(),
            content = (listOf(if (tasks == null) text else "") + links).filter { it.isNotBlank() }.joinToString("\n\n"),
            tasks = tasks,
            color = Colors.of(o.string("color")),
            pinned = o.bool("isPinned"),
            archived = o.bool("isArchived"),
            deleted = o.bool("isTrashed"),
            tags = o.array("labels").mapNotNull { it.obj?.string("name") },
            created = o.long("createdTimestampUsec")?.let(Dates::fromNumber),
            modified = o.long("userEditedTimestampUsec")?.let(Dates::fromNumber),
            files = files.files,
            format = "Google Keep",
        )
    }

    // ---------- Simplenote (notes.json: activeNotes / trashedNotes; the first line is the title) ----------

    private fun simplenote(o: JsonObject): List<ImportedNote> {
        fun note(item: JsonElement, deleted: Boolean): ImportedNote? {
            val n = item.obj ?: return null
            val systemTags = n.array("systemTags").mapNotNull { it.primitiveContent }
            val content = PlainTextMarkdown.normalize(n.string("content").orEmpty())
            val title = content.lineSequence().firstOrNull { it.isNotBlank() }?.trim()?.removePrefix("#")?.trim().orEmpty()
            val body = content.trimStart('\n').substringAfter('\n', "").trim('\n')
            val markdown = n.bool("markdown") || "markdown" in systemTags
            return ImportedNote(
                title = title,
                content = if (markdown) body else PlainTextMarkdown.convert(body),
                pinned = n.bool("pinned") || "pinned" in systemTags,
                deleted = deleted,
                tags = n.array("tags").mapNotNull { it.primitiveContent },
                created = Dates.parse(n.string("creationDate")),
                modified = Dates.parse(n.string("lastModified")),
                format = "Simplenote",
            )
        }
        return o.array("activeNotes").mapNotNull { note(it, false) } + o.array("trashedNotes").mapNotNull { note(it, true) }
    }

    // ---------- Standard Notes (decrypted backup: items with content_type Note / Tag) ----------

    private fun isStandardNotes(o: JsonObject) = o.array("items").any { it.obj?.containsKey("content_type") == true }

    private fun standardNotes(o: JsonObject): List<ImportedNote> {
        val items = o.array("items").mapNotNull { it.obj }
        val tagsByNote = mutableMapOf<String, MutableList<String>>()
        items.filter { it.string("content_type") == "Tag" }.forEach { tag ->
            val content = tag["content"] as? JsonObject ?: return@forEach
            val title = content.string("title") ?: return@forEach
            content.array("references").mapNotNull { it.obj?.string("uuid") }
                .forEach { tagsByNote.getOrPut(it) { mutableListOf() } += title }
        }
        return items.filter { it.string("content_type") == "Note" }.mapNotNull { item ->
            val c = item["content"] as? JsonObject ?: return@mapNotNull null // a string here = encrypted backup
            val app = (c["appData"] as? JsonObject)?.get("org.standardnotes.sn") as? JsonObject
            val text = c.string("text").orEmpty()
            val type = (c.string("noteType") ?: c.string("editorIdentifier")).orEmpty().lowercase()
            val body = when {
                type == "super" || text.trimStart().startsWith("{\"root\"") -> lexical(text)
                type.contains("rich") || type.contains("bold") || type.contains("plus") || looksLikeHtml(text) ->
                    HtmlMarkdown().convert(Jsoup.parseBodyFragment(text).body())
                type.contains("markdown") -> text
                else -> PlainTextMarkdown.convert(text)
            }
            ImportedNote(
                title = c.string("title").orEmpty().trim(),
                content = body,
                pinned = c.bool("pinned") || app?.bool("pinned") == true,
                archived = c.bool("archived") || app?.bool("archived") == true,
                deleted = c.bool("trashed"),
                tags = tagsByNote[item.string("uuid")].orEmpty(),
                created = Dates.parse(item.string("created_at")),
                modified = Dates.parse(item.string("updated_at") ?: app?.string("client_updated_at")),
                format = "Standard Notes",
            )
        }
    }

    /** Standard Notes "Super" notes are stored as a Lexical editor tree. */
    private fun lexical(text: String): String {
        val root = runCatching { Json.parseToJsonElement(text).obj?.get("root")?.obj }.getOrNull() ?: return text
        fun inline(node: JsonObject): String = when (node.string("type")) {
            "text" -> {
                val t = node.string("text").orEmpty()
                val format = node.long("format") ?: 0
                var out = t
                if (format and 16L != 0L) out = "`$out`"
                if (format and 1L != 0L) out = "**$out**"
                if (format and 2L != 0L) out = "*$out*"
                if (format and 4L != 0L) out = "~~$out~~"
                out
            }
            "linebreak" -> "\n"
            "link", "autolink" -> {
                val label = node.array("children").mapNotNull { it.obj }.joinToString("") { inline(it) }
                val url = node.string("url").orEmpty()
                if (label.isBlank() || label == url) url else "[$label]($url)"
            }
            else -> node.array("children").mapNotNull { it.obj }.joinToString("") { inline(it) }
        }
        fun block(node: JsonObject, depth: Int): String {
            val children = node.array("children").mapNotNull { it.obj }
            return when (node.string("type")) {
                "heading" -> "#".repeat(node.string("tag")?.drop(1)?.toIntOrNull() ?: 1) + " " + children.joinToString("") { inline(it) }
                "quote" -> "> " + children.joinToString("") { inline(it) }
                "code" -> "```\n" + children.joinToString("") { inline(it) } + "\n```"
                "horizontalrule" -> "---"
                "list" -> {
                    val kind = node.string("listType")
                    var number = 1
                    children.joinToString("\n") { li ->
                        val nested = li.array("children").mapNotNull { it.obj }.filter { it.string("type") == "list" }
                        val marker = when (kind) {
                            "number" -> "${number++}. "
                            "check" -> if (li.bool("checked")) "- [x] " else "- [ ] "
                            else -> "- "
                        }
                        val line = "    ".repeat(depth) + marker +
                            li.array("children").mapNotNull { it.obj }.filter { it.string("type") != "list" }.joinToString("") { inline(it) }
                        (listOf(line) + nested.map { block(it, depth + 1) }).joinToString("\n")
                    }
                }
                else -> children.joinToString("") { inline(it) }
            }
        }
        return root.array("children").mapNotNull { it.obj }.joinToString("\n\n") { block(it, 0) }.trim()
    }

    // ---------- Fossify Notes / Simple Notes (export: array of {title, value, type}) ----------

    private fun isFossify(array: JsonArray) = array.isNotEmpty() &&
        array.all { item -> item.obj?.let { "value" in it && "type" in it && "title" in it } == true }

    private fun fossify(o: JsonObject?): ImportedNote? {
        o ?: return null
        val type = o.string("type").orEmpty()
        val value = o.string("value").orEmpty()
        val checklist = type == "1" || type.equals("TYPE_CHECKLIST", true)
        val tasks = if (checklist) {
            runCatching { Json.parseToJsonElement(value) as JsonArray }.getOrNull()?.mapNotNull { item ->
                item.obj?.let { ImportedTask(it.string("title").orEmpty().trim(), it.bool("isDone")) }
            }
        } else null
        return ImportedNote(
            title = o.string("title").orEmpty().trim(),
            content = if (tasks != null) "" else PlainTextMarkdown.convert(value),
            tasks = tasks,
            format = "Fossify Notes",
        )
    }

    // ---------- Any other note JSON (Notally, Omni Notes, Material Notes, custom exports…) ----------

    private val titleKeys = listOf("title", "name", "subject", "heading", "caption")
    private val bodyKeys = listOf("content", "text", "body", "textContent", "note", "noteText", "plainText", "markdown", "description")
    private val itemKeys = listOf("items", "listContent", "tasks", "checklistItems", "checklist", "todos", "list")
    private val itemTextKeys = listOf("text", "title", "body", "content", "name", "value", "description")
    private val itemDoneKeys = listOf("checked", "isChecked", "done", "isDone", "completed", "isCompleted", "complete")
    private val createdKeys = listOf("created", "createdAt", "created_at", "creation", "creationDate", "dateCreated",
        "createdTime", "created_time", "createdTimestamp", "timestamp", "date")
    private val modifiedKeys = listOf("modified", "modifiedAt", "modified_at", "updated", "updatedAt", "updated_at",
        "lastModified", "lastModification", "dateModified", "modifiedDate", "modifiedTimestamp", "editedAt")
    private val tagKeys = listOf("tags", "labels", "label", "keywords", "categories")
    private val notebookKeys = listOf("notebook", "folder", "category", "notebookName", "folderName", "parent")

    private fun generic(json: JsonElement, entry: ImportEntry): List<ImportedNote>? {
        val candidates: List<JsonObject> = when (json) {
            is JsonArray -> json.mapNotNull { it.obj }
            is JsonObject -> listOf("notes", "items", "data", "entries", "records").firstNotNullOfOrNull { key ->
                (json[key] as? JsonArray)?.mapNotNull { it.obj }
            } ?: listOf(json)
            else -> emptyList()
        }
        val notes = candidates.filter(::isNoteLike)
        if (notes.isEmpty() || notes.size * 2 < candidates.size) return null
        return notes.map { genericNote(it, entry) }
    }

    private fun isNoteLike(o: JsonObject): Boolean {
        val hasBody = bodyKeys.any { o[it] is JsonPrimitive } || (o["value"] is JsonPrimitive && titleKeys.any { it in o })
        val hasItems = itemKeys.any { (o[it] as? JsonArray)?.any { item -> item is JsonObject } == true }
        return (hasBody || hasItems) && (titleKeys.any { it in o } || createdKeys.any { it in o } || modifiedKeys.any { it in o })
    }

    private fun genericNote(o: JsonObject, entry: ImportEntry): ImportedNote {
        val raw = (bodyKeys + "value").firstNotNullOfOrNull { o.string(it) }.orEmpty()
        val body = if (looksLikeHtml(raw)) HtmlMarkdown().convert(Jsoup.parseBodyFragment(raw).body()) else PlainTextMarkdown.convert(raw)
        val tasks = itemKeys.firstNotNullOfOrNull { key -> (o[key] as? JsonArray)?.takeIf { a -> a.any { it is JsonObject } } }
            ?.mapNotNull { item ->
                val i = item.obj ?: return@mapNotNull null
                val text = itemTextKeys.firstNotNullOfOrNull { i.string(it) } ?: return@mapNotNull null
                ImportedTask(text.trim(), itemDoneKeys.any { i.bool(it) } || i.string("status").equals("done", true))
            }?.takeIf { it.isNotEmpty() }
        return ImportedNote(
            title = titleKeys.firstNotNullOfOrNull { o.string(it) }.orEmpty().trim(),
            content = if (tasks != null && raw.isBlank()) "" else body,
            tasks = tasks,
            color = Colors.of(o.string("color") ?: o.string("colour")),
            pinned = o.bool("pinned") || o.bool("isPinned"),
            archived = o.bool("archived") || o.bool("isArchived"),
            deleted = o.bool("trashed") || o.bool("isTrashed") || o.bool("deleted") || o.bool("isDeleted") || o.bool("inTrash"),
            tags = tagKeys.firstNotNullOfOrNull { key -> o[key]?.let(::names) }.orEmpty(),
            notebook = notebookKeys.firstNotNullOfOrNull { key -> o[key]?.let(::names)?.firstOrNull() },
            created = createdKeys.firstNotNullOfOrNull { Dates.parse(o.string(it)) },
            modified = modifiedKeys.firstNotNullOfOrNull { Dates.parse(o.string(it)) },
            format = "JSON",
        )
    }

    /** Tags / notebooks written as "a", ["a","b"] or [{"name":"a"}]. */
    private fun names(element: JsonElement): List<String> = when (element) {
        is JsonArray -> element.flatMap(::names)
        is JsonObject -> listOfNotNull(element.string("name") ?: element.string("title"))
        is JsonPrimitive -> listOfNotNull(element.contentOrNull?.takeIf { it.isNotBlank() && it != "null" })
        else -> emptyList()
    }.map { it.trim() }.filter { it.isNotEmpty() }

    private fun looksLikeHtml(text: String) =
        Regex("""<(p|div|br|ul|ol|li|h[1-6]|b|strong|em|span|a)[\s>/]""", RegexOption.IGNORE_CASE).containsMatchIn(text)

    // ---------- small JSON helpers ----------

    private val JsonElement.obj: JsonObject? get() = this as? JsonObject
    private val JsonElement.primitiveContent: String? get() = (this as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
    private fun JsonObject.string(key: String): String? = (this[key] as? JsonPrimitive)?.takeUnless { it is JsonNull }?.contentOrNull
    private fun JsonObject.bool(key: String): Boolean = (this[key] as? JsonPrimitive)?.let {
        it.booleanOrNull ?: it.contentOrNull?.let { c -> c == "1" || c.equals("true", true) }
    } == true
    private fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.contentOrNull?.toLongOrNull() }
    private fun JsonObject.array(key: String): List<JsonElement> = (this[key] as? JsonArray).orEmpty()
}
