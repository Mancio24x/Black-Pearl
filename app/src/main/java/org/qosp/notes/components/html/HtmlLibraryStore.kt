package org.qosp.notes.components.html

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * File based store of imported HTML documents, in the app's private storage (`<root>/<id>/...`).
 * It is deliberately separate from the notes database: no schema change, nothing to restore or back up
 * together with notes, and the viewer never touches note data. All methods block: call them off the main thread.
 */
class HtmlLibraryStore(private val root: File) {
    private val lock = Any()
    private val indexFile get() = File(root, "index.json")

    fun list(): List<HtmlDocument> = synchronized(lock) { readIndex() }

    fun get(id: String): HtmlDocument? = list().firstOrNull { it.id == id }

    fun importHtml(name: String, input: InputStream, now: Long): HtmlDocument = synchronized(lock) {
        val id = newId()
        val dir = docDir(id).also { it.mkdirs() }
        try {
            val entry = "document.html"
            File(dir, entry).outputStream().use { out -> input.copyTo(out) }
            add(HtmlDocument(id, HtmlRules.titleFromName(name), entry, now))
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    /** Extracts a zip with safe paths and size limits; the page to open is picked by [HtmlRules.pickEntry]. */
    fun importZip(name: String, input: InputStream, now: Long): HtmlDocument = synchronized(lock) {
        val id = newId()
        val dir = docDir(id).also { it.mkdirs() }
        try {
            val paths = mutableListOf<String>()
            var total = 0L
            ZipInputStream(input).use { zip ->
                while (true) {
                    val e = zip.nextEntry ?: break
                    val rel = HtmlRules.safeEntryPath(e.name) ?: continue
                    if (e.isDirectory) continue
                    if (paths.size >= HtmlRules.MAX_ZIP_ENTRIES) throw IOException("zip has too many files")
                    val target = File(dir, rel)
                    if (!target.canonicalPath.startsWith(dir.canonicalPath + File.separator)) continue
                    target.parentFile?.mkdirs()
                    target.outputStream().use { out ->
                        val buf = ByteArray(16 * 1024)
                        while (true) {
                            val n = zip.read(buf)
                            if (n < 0) break
                            total += n
                            if (total > HtmlRules.MAX_ZIP_BYTES) throw IOException("zip is too large")
                            out.write(buf, 0, n)
                        }
                    }
                    paths += rel
                }
            }
            val entry = HtmlRules.pickEntry(paths) ?: throw IOException("no html file in zip")
            add(HtmlDocument(id, HtmlRules.titleFromName(name), entry, now))
        } catch (e: Exception) {
            dir.deleteRecursively()
            throw e
        }
    }

    fun rename(id: String, title: String) = update(id) { it.copy(title = title.trim().ifBlank { it.title }) }

    fun touchOpened(id: String, now: Long) = update(id) { it.copy(lastOpenedAt = now) }

    fun delete(id: String) = synchronized(lock) {
        writeIndex(readIndex().filterNot { it.id == id })
        docDir(id).deleteRecursively()
        progressFile(id).delete()
        Unit
    }

    fun readProgress(id: String): String? = synchronized(lock) {
        progressFile(id).takeIf { it.isFile }?.readText()
    }

    /** Atomic: the old progress stays intact if the process dies while writing. */
    fun writeProgress(id: String, json: String, now: Long): Boolean = synchronized(lock) {
        if (json.length > HtmlRules.MAX_PROGRESS_BYTES) return false
        if (!docDir(id).isDirectory) return false
        val target = progressFile(id)
        target.parentFile?.mkdirs()
        val tmp = File(target.parentFile, target.name + ".tmp")
        tmp.writeText(json)
        if (!tmp.renameTo(target)) {
            target.delete()
            tmp.renameTo(target)
        }
        writeIndex(readIndex().map { if (it.id == id) it.copy(progressUpdatedAt = now) else it })
        true
    }

    fun resetProgress(id: String) = synchronized(lock) {
        progressFile(id).delete()
        writeIndex(readIndex().map { if (it.id == id) it.copy(progressUpdatedAt = 0L) else it })
    }

    /** The file of [path] inside document [id], or null when it would leave the document folder. */
    fun resolve(id: String, path: String): File? {
        val dir = docDir(id)
        if (!dir.isDirectory) return null
        val rel = HtmlRules.safeEntryPath(path) ?: return null
        val f = File(dir, rel)
        return f.takeIf { it.canonicalPath.startsWith(dir.canonicalPath + File.separator) && it.isFile }
    }

    private fun docDir(id: String) = File(root, "docs/$id")

    private fun progressFile(id: String) = File(root, "progress/$id.json")

    private fun newId() = UUID.randomUUID().toString().replace("-", "")

    private fun add(doc: HtmlDocument): HtmlDocument {
        writeIndex(readIndex() + doc)
        return doc
    }

    private fun update(id: String, change: (HtmlDocument) -> HtmlDocument) = synchronized(lock) {
        writeIndex(readIndex().map { if (it.id == id) change(it) else it })
    }

    private fun readIndex(): List<HtmlDocument> {
        val f = indexFile
        if (!f.isFile) return emptyList()
        return runCatching {
            val arr = JSONArray(f.readText())
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                HtmlDocument(
                    id = o.getString("id"),
                    title = o.getString("title"),
                    entry = o.getString("entry"),
                    importedAt = o.optLong("importedAt"),
                    lastOpenedAt = o.optLong("lastOpenedAt"),
                    progressUpdatedAt = o.optLong("progressUpdatedAt"),
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writeIndex(docs: List<HtmlDocument>) {
        root.mkdirs()
        val arr = JSONArray()
        docs.forEach {
            arr.put(
                JSONObject().put("id", it.id).put("title", it.title).put("entry", it.entry)
                    .put("importedAt", it.importedAt).put("lastOpenedAt", it.lastOpenedAt)
                    .put("progressUpdatedAt", it.progressUpdatedAt),
            )
        }
        val tmp = File(root, "index.json.tmp")
        tmp.writeText(arr.toString())
        if (!tmp.renameTo(indexFile)) {
            indexFile.delete()
            tmp.renameTo(indexFile)
        }
    }
}
