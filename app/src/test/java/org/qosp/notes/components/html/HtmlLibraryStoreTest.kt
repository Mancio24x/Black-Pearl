package org.qosp.notes.components.html

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HtmlLibraryStoreTest {
    private lateinit var root: File
    private lateinit var store: HtmlLibraryStore

    @Before fun setUp() {
        root = Files.createTempDirectory("html-lib").toFile()
        store = HtmlLibraryStore(root)
    }

    @After fun tearDown() { root.deleteRecursively() }

    private fun html(text: String) = ByteArrayInputStream(text.toByteArray())

    private fun zip(vararg entries: Pair<String, String>): ByteArrayInputStream {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> entries.forEach { (n, c) -> z.putNextEntry(ZipEntry(n)); z.write(c.toByteArray()); z.closeEntry() } }
        return ByteArrayInputStream(out.toByteArray())
    }

    @Test fun importKeepsPrivateCopyAndSurvivesRestart() {
        val doc = store.importHtml("Lezione 1.html", html("<p>è</p>"), 100)
        assertEquals("Lezione 1", doc.title)
        assertEquals("<p>è</p>", store.resolve(doc.id, doc.entry)!!.readText())
        // a new store on the same folder = app restart
        assertEquals(listOf(doc), HtmlLibraryStore(root).list())
    }

    @Test fun progressIsPerDocumentAndSurvivesRestart() {
        val a = store.importHtml("a.html", html("a"), 1)
        val b = store.importHtml("b.html", html("b"), 2)
        val c = store.importHtml("c.html", html("c"), 3)
        assertTrue(store.writeProgress(a.id, "{\"q\":15}", 10))
        assertTrue(store.writeProgress(b.id, "{\"q\":7}", 11))
        val again = HtmlLibraryStore(root)
        assertEquals("{\"q\":15}", again.readProgress(a.id))
        assertEquals("{\"q\":7}", again.readProgress(b.id))
        assertNull(again.readProgress(c.id))
        assertEquals(10L, again.get(a.id)!!.progressUpdatedAt)
    }

    @Test fun resetErasesOnlyThatDocument() {
        val a = store.importHtml("a.html", html("a"), 1)
        val b = store.importHtml("b.html", html("b"), 2)
        store.writeProgress(a.id, "A", 5); store.writeProgress(b.id, "B", 5)
        store.resetProgress(a.id)
        assertNull(store.readProgress(a.id))
        assertEquals("B", store.readProgress(b.id))
        assertEquals(0L, store.get(a.id)!!.progressUpdatedAt)
        assertNotNull(store.resolve(a.id, a.entry)) // document itself stays
    }

    @Test fun renameKeepsIdAndProgress() {
        val a = store.importHtml("a.html", html("a"), 1)
        store.writeProgress(a.id, "A", 5)
        store.rename(a.id, "  Nuovo nome ")
        assertEquals("Nuovo nome", store.get(a.id)!!.title)
        assertEquals("A", store.readProgress(a.id))
        store.rename(a.id, "   ")
        assertEquals("Nuovo nome", store.get(a.id)!!.title)
    }

    @Test fun deleteRemovesDocumentAndProgress() {
        val a = store.importHtml("a.html", html("a"), 1)
        store.writeProgress(a.id, "A", 5)
        store.delete(a.id)
        assertTrue(store.list().isEmpty())
        assertNull(store.readProgress(a.id))
        assertFalse(File(root, "docs/${a.id}").exists())
        assertFalse(store.writeProgress(a.id, "late save", 9)) // a late save of a deleted document is dropped
    }

    @Test fun oversizeProgressIsRejectedAndOldKept() {
        val a = store.importHtml("a.html", html("a"), 1)
        store.writeProgress(a.id, "ok", 5)
        assertFalse(store.writeProgress(a.id, "x".repeat(HtmlRules.MAX_PROGRESS_BYTES + 1), 6))
        assertEquals("ok", store.readProgress(a.id))
    }

    @Test fun zipImportKeepsResourcesAndSkipsEscapes() {
        val doc = store.importZip(
            "corso.zip",
            zip("corso/index.html" to "<img src=a.png>", "corso/a.png" to "PNG", "../evil.txt" to "x", "/abs.txt" to "y"),
            1,
        )
        assertEquals("corso/index.html", doc.entry)
        assertEquals("PNG", store.resolve(doc.id, "corso/a.png")!!.readText())
        assertFalse(File(root, "evil.txt").exists())
        assertFalse(File(root, "docs/evil.txt").exists())
    }

    @Test fun zipWithoutHtmlFailsAndLeavesNothing() {
        val r = runCatching { store.importZip("x.zip", zip("a.png" to "x"), 1) }
        assertTrue(r.isFailure)
        assertTrue(store.list().isEmpty())
        assertFalse(File(root, "docs").listFiles().orEmpty().isNotEmpty())
    }

    @Test fun resolveNeverLeavesTheDocumentFolder() {
        val a = store.importHtml("a.html", html("a"), 1)
        val b = store.importHtml("b.html", html("secret"), 2)
        assertNull(store.resolve(a.id, "../${b.id}/${b.entry}"))
        assertNull(store.resolve(a.id, "missing.png"))
    }

    @Test fun progressFileIsNotServedAsDocumentResource() {
        val a = store.importHtml("a.html", html("a"), 1)
        store.writeProgress(a.id, "A", 5)
        assertNull(store.resolve(a.id, "progress.json"))
    }

    @Test fun touchOpenedUpdatesDate() {
        val a = store.importHtml("a.html", html("a"), 1)
        store.touchOpened(a.id, 99)
        assertEquals(99L, store.get(a.id)!!.lastOpenedAt)
    }
}
