package org.qosp.notes.components

import android.content.Context
import androidx.room.withTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.qosp.notes.components.importer.ImportContext
import org.qosp.notes.components.importer.ImportEntry
import org.qosp.notes.components.importer.TextFormats
import org.qosp.notes.data.AppDatabase
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.Notebook
import org.qosp.notes.preferences.PreferenceRepository

/** Recognise the actual WEB vault on upgrades, including copies made by the old ZIP importer. */
class BibleNotebook(
    private val context: Context,
    private val db: AppDatabase,
    private val preferences: PreferenceRepository,
) {
    private val mutex = Mutex()
    private val startupReady = MutableStateFlow(true)
    val ready = startupReady.asStateFlow()
    fun starting() { startupReady.value = false }
    fun started() { startupReady.value = true }

    private val paths by lazy { BibleStudyKit.files(context.assets) }
    private val entries by lazy {
        paths.map { path -> ImportEntry(path) { context.assets.open("${BibleStudyKit.DIR}/$path") } }
    }
    private val importContext by lazy { ImportContext(entries) }
    private val order by lazy { readingOrder(paths) }

    private fun original(path: String) = context.assets.open("${BibleStudyKit.DIR}/$path")
        .bufferedReader().use { it.readText() }.replace("\r\n", "\n")

    private fun variants(path: String): Pair<String, Set<String>> {
        val text = original(path)
        val entry = importContext.find(path)!!
        val old = TextFormats.read(entry, text, importContext)!!
        // 0.8.10 promoted the section labels in the main index from bold text to headings.
        val previous = if (path == INDEX_PATH) text.replace(groupHeading, "**$1**") else text
        return old.title to setOf(text.trimEnd(), old.content.trimEnd(), previous.trimEnd())
    }

    suspend fun prepare(): Long = withContext(Dispatchers.IO) {
        mutex.withLock {
            val saved = preferences.getBibleKitNotebookId().first()
            if (saved != null && preferences.isBibleKitReconciled() && db.notebookDao.getById(saved).first() != null) {
                return@withLock saved
            }
            val id = reconcile(saved)
            preferences.setBibleKitReconciled(id)
            id
        }
    }

    /** A single commit moves existing IDs. Tags, attachments, dates and Markdown are untouched. */
    internal suspend fun reconcile(saved: Long?): Long = db.withTransaction {
        val books = db.notebookDao.getAll().first()
        val names = books.associateBy { it.id }
        val known = mutableMapOf<String, String>()
        val rootVariants = mutableMapOf<String, Pair<String, Set<String>>>()
        for (path in paths) {
            known[BibleStudyKit.titleOf(path)] = path
            if ('/' !in path) {
                val variant = variants(path)
                known[variant.first] = path
                rootVariants[path] = variant
            }
        }
        known["The Bible"] = INDEX_PATH
        // Only matching content establishes provenance. A personal notebook called "Bible" is insufficient.
        val moves = mutableMapOf<Long?, MutableList<Long>>()
        val matchedTitles = mutableMapOf<Long, MutableSet<String>>()
        for (titles in known.keys.chunked(BibleStudyKit.CHUNK)) {
            val candidates = db.noteDao.kitCandidates(titles)
            for ((title, rows) in candidates.groupBy { it.title }) {
                val path = known.getValue(title)
                val comparable = (rootVariants[path] ?: variants(path)).second.let { texts ->
                    if (title == "The Bible") texts.map { it.replace("\n# The Bible\n", "\n") }.toSet() else texts
                }
                for (row in rows) {
                    if (row.content.trimEnd() !in comparable) continue
                    moves.getOrPut(row.notebookId) { ArrayList() }.add(row.id)
                    row.notebookId?.let { matchedTitles.getOrPut(it) { mutableSetOf() }.add(title) }
                }
            }
        }
        fun isKitName(name: String) = listOf("bible", "bibbia", "scripture (web)").any { name.contains(it, true) }
        val identified = books.filter { book ->
            val count = matchedTitles[book.id]?.size ?: 0
            (book.name == BibleStudyKit.NOTEBOOK && count > 0) ||
                ((book.id == saved || isKitName(book.name)) && count >= 2)
        }.map { it.id }.toSet()
        val canonical = saved?.takeIf { it in identified || names[it]?.name == BibleStudyKit.NOTEBOOK }
            ?: identified.filter { names[it]?.name == BibleStudyKit.NOTEBOOK }
                .sortedWith(compareByDescending<Long> { matchedTitles[it]?.size ?: 0 }.thenBy { it }).firstOrNull()
            ?: books.firstOrNull { it.name == BibleStudyKit.NOTEBOOK && db.noteDao.notebookTitles(it.id).isEmpty() }?.id
            ?: db.notebookDao.insert(Notebook(name = BibleStudyKit.NOTEBOOK))

        moves[null].orEmpty().chunked(BibleStudyKit.CHUNK).forEach { db.noteDao.moveIntoNotebook(it, canonical) }
        for (book in books) {
            if (book.id == canonical) continue
            if (book.id in identified) {
                // All contents of a verified legacy notebook travel together, including edited or renamed notes.
                db.noteDao.mergeNotebook(book.id, canonical)
            }
            if (book.id in identified || book.name == BibleStudyKit.NOTEBOOK) db.notebookDao.deleteIfEmpty(book.id)
        }
        canonical
    }

    data class ImportSummary(val added: Int, val books: Int, val missing: List<String>)

    /** Import only known paths from the WEB vault. Other selected files continue through the normal importer. */
    suspend fun importArchive(files: List<ImportEntry>): Pair<ImportSummary, Set<ImportEntry>>? {
        val index = files.singleOrNull { it.name == INDEX_PATH && it.path.split('/').none { part -> part.startsWith(".") || part == "__MACOSX" } }
            ?: return null
        val prefix = index.path.removeSuffix(INDEX_PATH)
        if (files.none { it.path == prefix + BibleStudyKit.START + ".md" }) return null
        val known = paths.toSet()
        val selected = files.filter { it.path.startsWith(prefix) && it.path.removePrefix(prefix) in known }
        if (selected.none { it.path.removePrefix(prefix).startsWith("Scripture (WEB)/") }) return null
        ready.first { it }
        val notebook = prepare()
        val selectedPaths = selected.mapTo(HashSet()) { it.path.removePrefix(prefix) }
        val bookIndexes = paths.filter { it.startsWith("Scripture (WEB)/") && chapterNumber.find(BibleStudyKit.titleOf(it)) == null }
        val missing = bookIndexes.filterNot { it in selectedPaths }.map(BibleStudyKit::titleOf)
        val added = db.withTransaction {
            var count = 0
            for (chunk in selected.chunked(BibleStudyKit.CHUNK)) {
                val stored = db.noteDao.kitCandidates(chunk.map { it.stem }).filter { it.notebookId == notebook }
                    .groupBy { it.title }
                val fresh = mutableListOf<Note>()
                for (entry in chunk) {
                    val path = entry.path.removePrefix(prefix)
                    val note = BibleStudyKit.noteFrom(path, entry.readText(), notebook)
                    val text = note.content.trimEnd()
                    val existing = stored[note.title].orEmpty()
                    if (existing.any { it.content.trimEnd() == text }) continue
                    if (path == INDEX_PATH && existing.isNotEmpty()) {
                        val editions = variants(path).second
                        if (text in editions && existing.any { it.content.trimEnd() in editions }) continue
                    }
                    fresh += note
                }
                db.noteDao.insertAll(fresh.map(Note::toEntity))
                count += fresh.size
            }
            count
        }
        return ImportSummary(added, bookIndexes.size - missing.size, missing) to selected.toSet()
    }

    suspend fun inReadingOrder(notes: List<Note>): List<Note> = withContext(Dispatchers.Default) {
        val seen = HashSet<Note>()
        notes.sortedWith(compareBy<Note> { order[it.title] ?: Int.MAX_VALUE }
            .thenBy { it.title }.thenBy { it.id }).filter { note ->
            // An untouched duplicate is shown once. Retain both IDs in Room and show all differing annotations.
            seen.add(note.copy(id = 0, creationDate = 0, modifiedDate = 0, content = note.content.trimEnd()))
        }
    }

    companion object {
        const val INDEX_PATH = "The Bible (WEB).md"
        private val groupHeading = Regex("^### (.+)$", RegexOption.MULTILINE)
        private val chapterNumber = Regex("""-(\d+)$""")
        fun readingOrder(paths: List<String>): Map<String, Int> {
            fun book(path: String) = when (path) {
                INDEX_PATH -> -2
                "${BibleStudyKit.START}.md" -> -1
                else -> path.substringBeforeLast('/', "").substringAfterLast('/').substringBefore(" - ")
                    .toIntOrNull() ?: Int.MAX_VALUE
            }
            return paths.sortedWith(compareBy<String>(::book).thenBy {
                chapterNumber.find(BibleStudyKit.titleOf(it))?.groupValues?.get(1)?.toIntOrNull() ?: 0
            }.thenBy { it }).mapIndexed { index, path -> BibleStudyKit.titleOf(path) to index }.toMap()
        }
    }
}
