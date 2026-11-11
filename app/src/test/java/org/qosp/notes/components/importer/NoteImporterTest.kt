package org.qosp.notes.components.importer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.qosp.notes.data.model.NoteColor
import java.io.File
import java.time.Instant
import java.util.Base64
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Imports the sample exports in src/test/resources/import, one per app, each written in that app's export format
 * (structure, field names, encodings and quirks such as Takeout's wrong image extensions).
 */
class NoteImporterTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val root = File(javaClass.classLoader!!.getResource("import")!!.toURI())
    private val png = Base64.getDecoder()
        .decode("iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==")
    private val png2 = Base64.getDecoder()
        .decode("iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAYAAABytg0kAAAAFElEQVR42mP8z8DwnwEJMDIwMAAAKkMDAZBpKz4AAAAASUVORK5CYII=")

    /** Zips a sample folder the way a phone or Google Takeout would (paths relative to it). */
    private fun zipOf(folder: String): File {
        val dir = File(root, folder)
        val zip = tmp.newFile("$folder.zip")
        ZipOutputStream(zip.outputStream()).use { out ->
            dir.walkTopDown().filter { it.isFile }.sortedBy { it.path }.forEach { file ->
                out.putNextEntry(ZipEntry(file.relativeTo(dir).invariantSeparatorsPath))
                file.inputStream().use { it.copyTo(out) }
                out.closeEntry()
            }
        }
        return zip
    }

    /** Several loose files picked at once (no folders). */
    private fun looseFiles(folder: String): List<ImportEntry> =
        File(root, folder).listFiles()!!.filter { it.isFile }.map { f -> ImportEntry(f.name) { f.inputStream() } }

    private fun read(file: File, name: String = file.name) = NoteImporter.read(ImportArchives.entriesOf(file, name, tmp.root))

    private fun ImportResult.note(title: String) = notes.single { it.title == title }

    private fun ImportedFile.bytes() = open().use { it.readBytes() }

    private fun epoch(iso: String) = Instant.parse(iso).epochSecond

    @Test
    fun googleKeepTakeoutZip() = checkKeep(read(zipOf("keep"), "takeout-20231101.zip"))

    @Test
    fun googleKeepTakeoutTgz() = checkKeep(read(File(root, "keep-takeout.tgz")))

    private fun checkKeep(result: ImportResult) {
        assertEquals(setOf("Google Keep"), result.formats)
        // Takeout's .html copies, Labels.txt and archive_browser.html are not notes
        assertEquals(listOf("", "Spesa", "Viaggio in Sardegna"), result.notes.map { it.title }.sorted())

        val list = result.note("Spesa")
        assertEquals(listOf(ImportedTask("Latte", true), ImportedTask("Pane", false)), list.tasks)
        assertEquals(NoteColor.Green, list.color)
        assertTrue(list.pinned)
        assertEquals(listOf("Casa"), list.tags)
        assertEquals(1699990000L, list.created)
        assertEquals(1699999999L, list.modified)

        val trip = result.note("Viaggio in Sardegna")
        assertTrue(trip.archived)
        assertEquals(NoteColor.Cyan, trip.color)
        assertTrue(trip.content.startsWith("Cala Goloritzé all'alba\n- costume\n- crema"))
        assertTrue(trip.content.contains(NBSP.repeat(4) + "indirizzo indentato"))
        assertTrue(trip.content.endsWith("[Orosei](https://it.wikipedia.org/wiki/Orosei)"))
        // Takeout says "1a2b3c4d.jpeg" but ships "1a2b3c4d.jpg"
        assertEquals("1a2b3c4d.jpg", trip.files.single().name)
        assertArrayEquals(png, trip.files.single().bytes())

        val bin = result.note("")
        assertTrue(bin.deleted)
        assertEquals("nota nel cestino", bin.content)
    }

    @Test
    fun simplenoteExportZip() {
        val result = read(zipOf("simplenote"), "notes.zip")
        assertEquals(setOf("Simplenote"), result.formats)
        assertEquals(3, result.notes.size) // the notes/*.txt copies are skipped

        val shopping = result.note("Lista della spesa")
        assertEquals(listOf(ImportedTask("uova", false), ImportedTask("farina", true)), shopping.tasks)
        assertEquals(listOf("casa"), shopping.tags)
        assertEquals(epoch("2023-05-01T10:00:00Z"), shopping.created)
        assertEquals(epoch("2023-05-02T11:30:00Z"), shopping.modified)

        val ideas = result.note("Idee")
        assertTrue(ideas.pinned)
        assertEquals("- una\n- due", ideas.content)

        val old = result.note("Vecchia")
        assertTrue(old.deleted)
        assertEquals("da buttare", old.content)
    }

    @Test
    fun standardNotesBackup() {
        val result = read(File(root, "standardnotes/Standard Notes Backup and Import File.txt"))
        assertEquals(setOf("Standard Notes"), result.formats)
        assertEquals(listOf("Archiviata", "Ricette", "Super nota"), result.notes.map { it.title }.sorted()) // encrypted one skipped

        val recipes = result.note("Ricette")
        assertTrue(recipes.pinned)
        assertEquals(listOf("cucina"), recipes.tags)
        assertEquals("Pasta al pesto\n- basilico\n- pinoli", recipes.content)
        assertEquals(epoch("2023-03-02T09:00:00Z"), recipes.modified)

        assertEquals("## Piano\n\n- [x] fatto\n- [ ] **da fare**", result.note("Super nota").content)
        assertTrue(result.note("Archiviata").archived)
    }

    @Test
    fun fossifyNotesExport() {
        val result = read(File(root, "fossify/notes_export.json"))
        assertEquals(setOf("Fossify Notes"), result.formats)
        assertEquals("Prima riga\n- [ ] comprare\n- [x] fatto", result.note("Generale").content)
        assertEquals(listOf(ImportedTask("matematica", true), ImportedTask("storia", false)), result.note("Compiti").tasks)
        assertEquals("tipo numerico", result.note("Vecchio formato").content)
    }

    @Test
    fun genericNoteJson() {
        val notally = read(File(root, "generic/notally.json"))
        val todo = notally.note("Da fare")
        assertEquals(listOf(ImportedTask("email", true), ImportedTask("riunione", false)), todo.tasks)
        assertEquals(NoteColor.Blue, todo.color)
        assertTrue(todo.pinned)
        assertEquals(listOf("lavoro"), todo.tags)
        assertEquals(1700000000L, todo.created)
        assertEquals(1700000500L, todo.modified)
        assertEquals("testo libero\ncon due righe", notally.note("Pensieri").content)

        val omni = read(File(root, "generic/omni.json")).note("Omni")
        assertEquals(listOf(ImportedTask("uno", false), ImportedTask("due", true)), omni.tasks)
        assertEquals("Lavoro", omni.notebook)

        val settings = read(File(root, "generic/settings.json"))
        assertTrue(settings.notes.isEmpty())
        assertEquals(listOf("settings.json"), settings.skipped)
    }

    @Test
    fun evernoteEnex() {
        val result = read(File(root, "evernote/Viaggi.enex"))
        assertEquals(setOf("Evernote"), result.formats)
        assertTrue(result.notes.all { it.notebook == "Viaggi" })

        val packing = result.note("Lista valigia")
        assertEquals(listOf("vacanze", "estate"), packing.tags)
        assertEquals(epoch("2023-07-01T08:00:00Z"), packing.created)
        assertEquals(epoch("2023-07-02T09:00:00Z"), packing.modified)
        assertEquals("- [x] Costume\n- [ ] Crema\n\nFoto:\n![spiaggia.png](bp-import:0)", packing.content)
        assertEquals("image/png", packing.files.single().mimeType)
        assertArrayEquals(png, packing.files.single().bytes())

        val notes = result.note("Appunti").content
        for (line in listOf(
            "# Titolo interno",
            "Testo **grassetto** e *corsivo* con [un link](https://example.com).",
            "- uno", "- due", "    - sotto", "1. primo",
            "| A | B |", "| --- | --- |", "| 1 | 2 |",
            "Riga con spazi",
        )) assertTrue("missing «$line» in:\n$notes", notes.lines().contains(line))
    }

    @Test
    fun joplinJex() {
        val result = read(File(root, "export.jex"))
        assertEquals(setOf("Joplin"), result.formats)
        assertEquals(listOf("Altra", "Storia"), result.notes.map { it.title }.sorted())

        val history = result.note("Storia")
        assertEquals("Scuola", history.notebook)
        assertEquals(listOf("storia"), history.tags)
        assertEquals("Il Rinascimento a Firenze.\n\n![mappa.png](bp-import:0)\n\nVedi Altra", history.content)
        assertArrayEquals(png, history.files.single().bytes())
        assertEquals(epoch("2023-02-01T10:00:00Z"), history.created)
        assertFalse(history.deleted)

        val other = result.note("Altra")
        assertNull(other.notebook)
        assertEquals(listOf(ImportedTask("uno", false), ImportedTask("due", true)), other.tasks)
    }

    @Test
    fun obsidianVaultZip() {
        val result = read(zipOf("obsidian"), "Vault.zip")
        assertEquals(setOf("Markdown"), result.formats)
        assertEquals(listOf("Diario", "Storia del Rinascimento"), result.notes.map { it.title }.sorted()) // .obsidian, .trash skipped

        val history = result.note("Storia del Rinascimento")
        assertEquals("Scuola", history.notebook)
        assertEquals(listOf("scuola", "storia"), history.tags)
        assertEquals(java.time.LocalDateTime.parse("2023-02-01T10:00").atZone(java.time.ZoneId.systemDefault()).toEpochSecond(), history.created)
        assertEquals(
            "Firenze e i Medici.\n\n![mappa.png](bp-import:0)\n\nVedi [[Diario|il diario]] e ![schema](bp-import:1)\n\nsource: libro di testo",
            history.content,
        )
        assertEquals(listOf("mappa.png", "schema uno.png"), history.files.map { it.name })
        assertArrayEquals(png2, history.files[1].bytes())

        val diary = result.note("Diario")
        assertNull(diary.notebook)
        assertEquals("Oggi ho studiato.\n- [ ] ripassare", diary.content)
    }

    @Test
    fun plainTextIsConvertedToMarkdown() {
        val result = NoteImporter.read(looseFiles("txt"))
        assertEquals(setOf("TXT"), result.formats)

        // only checkboxes: a Black Pearl checklist; the title repeated as first line is not kept twice
        assertEquals(listOf(ImportedTask("Pane", false), ImportedTask("Latte", true)), result.note("Lista spesa").tasks)
        // Windows-1252 and UTF-16 files
        assertEquals("Perché è così\nseconda riga", result.note("Appunti").content)
        assertEquals("Cara Anna,\nciao!", result.note("Lettera").content)
        // a file name that says nothing: the first line is the title
        assertEquals("martedì alle 15", result.note("Promemoria dentista").content)

        assertEquals(
            listOf(
                "- uno", "- due", "1. primo", "2024\\. anno speciale",
                NBSP.repeat(4) + "indentato con tab", NBSP.repeat(4) + "quattro spazi",
                "- già markdown", "- [ ] casella", "- [x] fatta",
            ),
            result.note("Formati").content.lines(),
        )
    }

    @Test
    fun htmlIsConvertedToMarkdown() {
        val result = read(File(root, "html/Ricetta.html"))
        assertEquals(setOf("HTML"), result.formats)
        val recipe = result.note("Ricetta tiramisù")
        val lines = recipe.content.lines()
        assertFalse("title repeated", lines.first().contains("Ricetta"))
        for (line in listOf(
            "Per **6 persone**.", "- [x] mascarpone", "- [ ] savoiardi", "![foto](bp-import:0)",
            "> Buon appetito!", "| Ingrediente | Grammi |", "| zucchero | 100 |",
        )) assertTrue("missing «$line» in:\n${recipe.content}", lines.contains(line))
        assertEquals("image/png", recipe.files.single().mimeType)
        assertArrayEquals(png, recipe.files.single().bytes())
    }

    @Test
    fun picturesArePlacedOrDroppedWhenTheyCannotBeCopied() {
        val text = "a\n![x](bp-import:0)\n![y](bp-import:1)\n![z](bp-import:10)"
        assertEquals(
            "a\n![x](content://p/0)\ny\nz",
            OtherAppsImport.placeImages(text, listOf("content://p/0", null)),
        )
    }

    @Test
    fun datesFromEveryApp() {
        assertEquals(epoch("2023-07-01T08:00:00Z"), Dates.parse("20230701T080000Z")) // Evernote
        assertEquals(epoch("2023-02-01T10:00:00Z"), Dates.parse("2023-02-01T10:00:00.000Z")) // Joplin, Simplenote
        assertEquals(epoch("2023-02-01T10:00:00Z"), Dates.parse("2023-02-01T12:00:00+02:00"))
        assertEquals(epoch("2023-02-01T10:00:00Z"), Dates.parse("2023-02-01T12:00:00+0200"))
        assertEquals(1700000000L, Dates.parse("1700000000000")) // milliseconds
        assertEquals(1700000000L, Dates.parse("1700000000000000")) // Keep microseconds
        assertNull(Dates.parse("ieri"))
        assertNull(Dates.parse("0"))
    }

    @Test
    fun genericFileNames() {
        for (name in listOf("note_123", "Untitled", "Senza titolo 2", "2024-01-31", "3f2a9c1e-77b0-4c1e-9f0e-1a2b3c4d5e6f", "12345")) {
            assertTrue(name, Titles.isGeneric(name))
        }
        for (name in listOf("Spesa", "Note di viaggio", "Lista 2024")) assertFalse(name, Titles.isGeneric(name))
    }

    @Test
    fun tarWithLongNamesFromPaxHeaders() {
        // bsdtar writes pax headers for long names: the reader must use them
        val long = "cartella-" + "x".repeat(120) + "/Nota lunga.md"
        val dir = tmp.newFolder("long")
        File(dir, long).apply { parentFile!!.mkdirs(); writeText("# Nota lunga\n\ntesto") }
        val tar = File(tmp.root, "long.tar")
        val process = ProcessBuilder("tar", "-cf", tar.path, long).directory(dir).redirectErrorStream(true)
            .apply { environment()["COPYFILE_DISABLE"] = "1" } // no macOS "._" companion files
            .start()
        if (process.waitFor() != 0) return // no tar command on this machine
        val entries = ImportArchives.entriesOf(tar, "long.tar", tmp.root)
        assertEquals(long, entries.single().path)
        assertEquals("testo", NoteImporter.read(entries).note("Nota lunga").content)
    }

    private companion object {
        const val NBSP = "\u00A0"
    }
}
