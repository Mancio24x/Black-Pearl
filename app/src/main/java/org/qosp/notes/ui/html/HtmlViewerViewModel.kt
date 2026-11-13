package org.qosp.notes.ui.html

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.qosp.notes.components.html.HtmlDocument
import org.qosp.notes.components.html.HtmlLibraryStore
import java.util.concurrent.Executors

/** Holds the open document and its newest progress, so a page reload never loses answers. */
class HtmlViewerViewModel(private val store: HtmlLibraryStore) : ViewModel() {
    var doc: HtmlDocument? = null
        private set

    /** Newest progress JSON handed over by the page (also served to the page again after a reload). */
    @Volatile var progress: String? = null
        private set

    /** While true (page being reset or replaced) saves coming from the old page are ignored. */
    @Volatile var blockSaves = false

    val savedAt = MutableStateFlow(0L)

    suspend fun open(id: String): HtmlDocument? = withContext(Dispatchers.IO) {
        if (doc?.id == id) return@withContext doc
        val d = store.get(id) ?: return@withContext null
        store.touchOpened(id, System.currentTimeMillis())
        progress = store.readProgress(id)
        doc = d
        d
    }

    /** Called from the page (any thread). Writes are ordered on one background thread and survive this screen closing. */
    fun onProgress(json: String) {
        val id = doc?.id ?: return
        if (blockSaves) return
        progress = json
        writer.execute {
            val now = System.currentTimeMillis()
            if (store.writeProgress(id, json, now)) savedAt.value = now
        }
    }

    suspend fun reset() = withContext(Dispatchers.IO) {
        val id = doc?.id ?: return@withContext
        progress = null
        store.resetProgress(id)
        HtmlLibraryViewModel.clearWebStorage(id)
        savedAt.value = 0L
    }

    private companion object {
        val writer = Executors.newSingleThreadExecutor()
    }
}
