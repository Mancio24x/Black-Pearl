package org.qosp.notes.components.html

import java.nio.charset.Charset
import java.util.Locale

/** Pure helpers of the HTML viewer (no Android classes): import rules, script injection, sorting. */
object HtmlRules {
    const val BOOT_PATH = "/__bp/boot.js"
    const val MAX_PROGRESS_BYTES = 2_000_000
    const val MAX_ZIP_BYTES = 200L * 1024 * 1024
    const val MAX_ZIP_ENTRIES = 5_000

    /** Every document gets its own web origin, so localStorage / IndexedDB are never shared. */
    fun hostFor(id: String) = "d$id.docs.blackpearl.invalid"

    fun isHtmlName(name: String) = name.lowercase(Locale.ROOT).let { it.endsWith(".html") || it.endsWith(".htm") }

    fun isZipName(name: String) = name.lowercase(Locale.ROOT).endsWith(".zip")

    fun titleFromName(name: String): String =
        name.substringAfterLast('/').substringBeforeLast('.').ifBlank { name }.trim().ifBlank { "HTML" }

    /** Returns the normalized relative path of a zip entry, or null if it could escape the folder (zip-slip). */
    fun safeEntryPath(raw: String): String? {
        val clean = raw.replace('\\', '/')
        if (clean.startsWith("/") || clean.contains('\u0000') || Regex("^[A-Za-z]:").containsMatchIn(clean)) return null
        val parts = clean.split('/').filter { it.isNotEmpty() && it != "." }
        if (parts.isEmpty() || parts.any { it == ".." }) return null
        if (parts.first() == "__bp") return null
        return parts.joinToString("/")
    }

    /** Main page of an extracted zip: index.html at the top, else the shallowest, then alphabetical, HTML file. */
    fun pickEntry(paths: List<String>): String? =
        paths.filter { isHtmlName(it) }
            .sortedWith(
                compareBy<String>(
                    { !it.substringAfterLast('/').lowercase(Locale.ROOT).startsWith("index.") },
                    { it.count { c -> c == '/' } },
                    { it.lowercase(Locale.ROOT) },
                ),
            ).firstOrNull()

    fun mimeFor(path: String): String = when (path.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
        "html", "htm" -> "text/html"
        "css" -> "text/css"
        "js", "mjs" -> "application/javascript"
        "json" -> "application/json"
        "svg" -> "image/svg+xml"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "ico" -> "image/x-icon"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "mp3" -> "audio/mpeg"
        "mp4" -> "video/mp4"
        "webm" -> "video/webm"
        "txt" -> "text/plain"
        "xml" -> "application/xml"
        else -> "application/octet-stream"
    }

    /** UTF-8 unless the page itself declares another charset in its first KB. */
    fun detectCharset(bytes: ByteArray): Charset {
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return Charsets.UTF_8
        }
        val head = String(bytes, 0, minOf(bytes.size, 1024), Charsets.ISO_8859_1)
        val name = Regex("""<meta[^>]+charset\s*=\s*["']?\s*([A-Za-z0-9_\-]+)""", RegexOption.IGNORE_CASE)
            .find(head)?.groupValues?.get(1)
        return runCatching { Charset.forName(name ?: "UTF-8") }.getOrDefault(Charsets.UTF_8)
    }

    /**
     * Adds our bootstrap `<script src>` as the first thing the browser sees, so it runs before the page's own
     * scripts. The page itself is not otherwise modified.
     */
    fun injectBoot(html: String): String {
        val tag = """<script src="$BOOT_PATH"></script>"""
        val head = Regex("<head(\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(html)
        if (head != null) return html.substring(0, head.range.last + 1) + tag + html.substring(head.range.last + 1)
        val root = Regex("<html(\\s[^>]*)?>", RegexOption.IGNORE_CASE).find(html)
        if (root != null) return html.substring(0, root.range.last + 1) + tag + html.substring(root.range.last + 1)
        val doctype = Regex("^\\s*<!doctype[^>]*>", RegexOption.IGNORE_CASE).find(html)
        val at = doctype?.range?.last?.plus(1) ?: 0
        return html.substring(0, at) + tag + html.substring(at)
    }

    /** True for urls the viewer may load as the page or sub-resource of document [host]. */
    fun isOwnUrl(url: String, host: String) = url.startsWith("https://$host/")

    enum class Sort { NAME, IMPORTED, OPENED }

    fun sortAndFilter(docs: List<HtmlDocument>, sort: Sort, query: String): List<HtmlDocument> {
        val q = query.trim().lowercase(Locale.ROOT)
        val filtered = if (q.isEmpty()) docs else docs.filter { it.title.lowercase(Locale.ROOT).contains(q) }
        return when (sort) {
            Sort.NAME -> filtered.sortedBy { it.title.lowercase(Locale.ROOT) }
            Sort.IMPORTED -> filtered.sortedByDescending { it.importedAt }
            Sort.OPENED -> filtered.sortedWith(compareByDescending<HtmlDocument> { it.lastOpenedAt }.thenByDescending { it.importedAt })
        }
    }

    /** Wraps stored progress JSON for the bootstrap script. Empty progress becomes `null`. */
    fun bootScript(script: String, progressJson: String?): String =
        "window.__BP_STATE__=" + (progressJson?.takeIf { it.isNotBlank() } ?: "null") + ";\n" + script
}
