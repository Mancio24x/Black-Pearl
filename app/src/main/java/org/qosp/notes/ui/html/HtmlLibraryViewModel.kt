package org.qosp.notes.ui.html

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.WebStorage
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.qosp.notes.R
import org.qosp.notes.components.html.HtmlDocument
import org.qosp.notes.components.html.HtmlLibraryStore
import org.qosp.notes.components.html.HtmlRules

class HtmlLibraryViewModel(
    private val context: Context,
    private val store: HtmlLibraryStore,
) : ViewModel() {
    private val all = MutableStateFlow<List<HtmlDocument>>(emptyList())
    val sort = MutableStateFlow(HtmlRules.Sort.OPENED)
    val query = MutableStateFlow("")
    val docs = combine(all, sort, query) { d, s, q -> HtmlRules.sortAndFilter(d, s, q) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val messages = MutableSharedFlow<Int>(extraBufferCapacity = 4)

    fun refresh() {
        viewModelScope.launch { all.value = withContext(Dispatchers.IO) { store.list() } }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { doImport(uri) } }
            refresh()
            messages.emit(
                result.fold(
                    onSuccess = { if (it) R.string.html_imported else R.string.html_import_unsupported },
                    onFailure = { R.string.html_import_failed },
                ),
            )
        }
    }

    /** @return false when the file type is not supported. */
    private fun doImport(uri: Uri): Boolean {
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
            if (it.moveToFirst()) it.getString(0) else null
        } ?: uri.lastPathSegment.orEmpty()
        val type = resolver.getType(uri).orEmpty()
        val now = System.currentTimeMillis()
        val isHtml = HtmlRules.isHtmlName(name) || type == "text/html"
        val isZip = HtmlRules.isZipName(name) || type == "application/zip"
        if (!isHtml && !isZip) return false
        resolver.openInputStream(uri)?.use { input ->
            if (isHtml) store.importHtml(name, input, now) else store.importZip(name, input, now)
        } ?: throw java.io.IOException("cannot open $uri")
        return true
    }

    fun rename(doc: HtmlDocument, title: String) = mutate { store.rename(doc.id, title) }

    fun delete(doc: HtmlDocument) = mutate {
        store.delete(doc.id)
        clearWebStorage(doc.id)
    }

    fun resetProgress(doc: HtmlDocument) = mutate {
        store.resetProgress(doc.id)
        clearWebStorage(doc.id)
        messages.emit(R.string.html_reset_done)
    }

    private fun mutate(block: suspend () -> Unit) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { block() }
            refresh()
        }
    }

    companion object {
        /** Removes localStorage / IndexedDB the page kept under its own origin. Must run on the main thread. */
        suspend fun clearWebStorage(id: String) = withContext(Dispatchers.Main) {
            WebStorage.getInstance().deleteOrigin("https://${HtmlRules.hostFor(id)}")
        }
    }
}
