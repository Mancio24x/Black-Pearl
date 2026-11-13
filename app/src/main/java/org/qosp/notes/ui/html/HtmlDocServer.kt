package org.qosp.notes.ui.html

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import org.qosp.notes.components.html.HtmlDocument
import org.qosp.notes.components.html.HtmlLibraryStore
import org.qosp.notes.components.html.HtmlRules
import java.io.ByteArrayInputStream
import java.io.FileInputStream

/**
 * Serves one document from private storage under its own https origin (`d<id>.docs.blackpearl.invalid`), so web
 * storage is isolated per document. Everything outside that origin is refused, except plain https sub-resources
 * (fonts, scripts the page asks for) which WebView loads itself.
 */
class HtmlDocServer(
    private val store: HtmlLibraryStore,
    private val doc: HtmlDocument,
    private val bootJs: String,
    private val progress: () -> String?,
    private val onMissing: (String) -> Unit,
) {
    val host = HtmlRules.hostFor(doc.id)

    fun entryUrl(): String =
        "https://$host/" + doc.entry.split('/').joinToString("/") { android.net.Uri.encode(it) }

    fun intercept(request: WebResourceRequest): WebResourceResponse? {
        val uri = request.url
        val scheme = uri.scheme
        if (scheme == "https" || scheme == "http") {
            val h = uri.host.orEmpty()
            if (h == host) return serveOwn(uri.path ?: "/")
            if (h.endsWith(".docs.blackpearl.invalid")) return blocked() // another document's origin
            return null // ordinary web sub-resource
        }
        // data: and blob: never reach us; file:, content:, anything else is refused.
        return if (scheme == "data" || scheme == "blob" || scheme == "about") null else blocked()
    }

    private fun serveOwn(path: String): WebResourceResponse {
        if (path == HtmlRules.BOOT_PATH) {
            val js = HtmlRules.bootScript(bootJs, progress())
            return response("application/javascript", "UTF-8", ByteArrayInputStream(js.toByteArray(Charsets.UTF_8)))
        }
        val rel = path.trimStart('/').ifEmpty { doc.entry }
        val file = store.resolve(doc.id, rel)
        if (file == null) {
            onMissing(rel)
            return WebResourceResponse("text/plain", "UTF-8", 404, "Not Found", emptyMap(), ByteArrayInputStream(ByteArray(0)))
        }
        val mime = HtmlRules.mimeFor(file.name)
        if (mime == "text/html") {
            val bytes = file.readBytes()
            val cs = HtmlRules.detectCharset(bytes)
            val html = HtmlRules.injectBoot(String(bytes, cs))
            return response(mime, cs.name(), ByteArrayInputStream(html.toByteArray(cs)))
        }
        val enc = if (mime.startsWith("text/") || mime.endsWith("javascript") || mime.endsWith("json")) "UTF-8" else null
        return response(mime, enc, FileInputStream(file))
    }

    private fun response(mime: String, enc: String?, data: java.io.InputStream) =
        WebResourceResponse(mime, enc, 200, "OK", mapOf("Cache-Control" to "no-store"), data)

    private fun blocked() =
        WebResourceResponse("text/plain", "UTF-8", 403, "Forbidden", emptyMap(), ByteArrayInputStream(ByteArray(0)))
}
