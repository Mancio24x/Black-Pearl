package org.qosp.notes.components

import android.content.Context
import android.content.res.AssetManager
import kotlinx.coroutines.flow.first
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.data.repo.NotebookRepository
import org.qosp.notes.preferences.PreferenceRepository

/**
 * Black Pearl: the "Bible Study Kit" (an Obsidian vault, free licence; World English Bible text) shipped in the APK
 * to try linked notes and the Network. Added once, local only, in its own notebook, which "All notes" leaves out
 * so the home list keeps the user's own notes. One note per file: titles are the file names, text is unchanged.
 */
object BibleStudyKit {
    const val DIR = "sample/bible-study-kit"
    const val NOTEBOOK = "Bible Study Kit"
    const val START = "+Start here"

    /** Notes added per database transaction. */
    const val CHUNK = 200

    private fun titleOf(path: String) = path.substringAfterLast('/').removeSuffix(".md")

    /** A kit file (asset path relative to [DIR]) as a note of the kit notebook. */
    fun noteFrom(path: String, markdown: String, notebookId: Long?): Note = Note(
        title = titleOf(path),
        content = markdown.replace("\r\n", "\n"),
        notebookId = notebookId,
        isLocalOnly = true,
    )

    /** Every `.md` file under [dir], as paths relative to it, in a stable order. */
    fun files(assets: AssetManager, dir: String = DIR): List<String> {
        val out = ArrayList<String>()
        fun walk(rel: String) {
            val full = if (rel.isEmpty()) dir else "$dir/$rel"
            for (name in assets.list(full).orEmpty().sorted()) {
                val child = if (rel.isEmpty()) name else "$rel/$name"
                if (name.endsWith(".md")) out.add(child) else walk(child)
            }
        }
        walk("")
        return out
    }

    suspend fun addOnce(
        context: Context,
        notes: NoteRepository,
        notebooks: NotebookRepository,
        preferences: PreferenceRepository,
    ) {
        if (preferences.isBibleKitAdded()) return
        val assets = context.assets
        val paths = files(assets)
        if (paths.isEmpty()) return
        // An earlier start may have been stopped half way (app closed while the 1300 notes were being added):
        // go on in the same notebook and skip the notes already there, instead of adding a second copy.
        val savedId = preferences.getBibleKitNotebookId().first()
        val notebookId = savedId?.takeIf { notebooks.getById(it).first() != null }
            ?: notebooks.insert(Notebook(name = NOTEBOOK)).also {
                // remembered first: if the app is killed half way, the notes already added still stay out of "All notes"
                preferences.setBibleKitNotebookId(it)
            }
        val present = if (savedId == notebookId) notes.getByNotebook(notebookId).first().mapTo(HashSet()) { it.title }
        else emptySet()
        // Chunks of [CHUNK] notes, one transaction each: open note lists re-query a few times instead of 1300,
        // and only one chunk of file text is in memory at a time.
        paths.filter { titleOf(it) !in present }.chunked(CHUNK).forEach { chunk ->
            notes.insertLocalNotes(
                chunk.map { path ->
                    noteFrom(path, assets.open("$DIR/$path").use { it.readBytes().decodeToString() }, notebookId)
                },
            )
        }
        preferences.setBibleKitAdded()
    }

    /**
     * Removes copies of kit notes that ended up outside the kit notebook (for example restored from a backup made
     * while the kit was already there): they filled "All notes" with a second kit. A copy goes only when it is still
     * exactly the shipped text, has no attachments and no tags, and the kit notebook has a note with the same title,
     * so nothing the user wrote or changed is touched. Also removes empty extra "Bible Study Kit" notebooks.
     * Returns how many notes were removed.
     */
    suspend fun removeCopies(
        context: Context,
        notes: NoteRepository,
        notebooks: NotebookRepository,
        preferences: PreferenceRepository,
    ): Int {
        if (!preferences.isBibleKitAdded()) return 0
        val kitId = preferences.getBibleKitNotebookId().first() ?: return 0
        if (notebooks.getById(kitId).first() == null) return 0
        val assets = context.assets
        val pathByTitle = files(assets).associateBy { titleOf(it) }
        val inKit = notes.getTitlesIn(kitId)
        var removed = 0
        for (row in notes.getIdTitlesOutside(kitId)) {
            val path = pathByTitle[row.title] ?: continue
            if (row.title !in inKit) continue
            val note = notes.getById(row.id).first() ?: continue
            val shipped = assets.open("$DIR/$path").use { it.readBytes().decodeToString() }.replace("\r\n", "\n")
            if (note.content != shipped || note.attachments.isNotEmpty() || note.tags.isNotEmpty() || note.isList) continue
            notes.deleteNotes(note)
            removed++
        }
        for (extra in notebooks.getAll().first()) {
            if (extra.id != kitId && extra.name == NOTEBOOK && notes.getTitlesIn(extra.id).isEmpty()) notebooks.delete(extra)
        }
        return removed
    }
}
