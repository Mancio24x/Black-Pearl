package org.qosp.notes.components.html

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HtmlRulesTest {
    @Test fun browserFaviconRequestIsNotAMissingFile() {
        // WebView asks for /favicon.ico on its own; a document without one is complete
        assertFalse(HtmlRules.warnsWhenMissing("favicon.ico"))
        assertTrue(HtmlRules.warnsWhenMissing("img/photo.png"))
        assertTrue(HtmlRules.warnsWhenMissing("img/favicon.ico"))
    }

    @Test fun zipSlipPathsAreRejected() {
        assertNull(HtmlRules.safeEntryPath("../evil.html"))
        assertNull(HtmlRules.safeEntryPath("a/../../evil.html"))
        assertNull(HtmlRules.safeEntryPath("/etc/passwd"))
        assertNull(HtmlRules.safeEntryPath("C:\\win\\x.html"))
        assertNull(HtmlRules.safeEntryPath("..\\evil.html"))
        assertNull(HtmlRules.safeEntryPath("__bp/boot.js"))
        assertEquals("img/a.png", HtmlRules.safeEntryPath("./img//a.png"))
        assertEquals("a/b.html", HtmlRules.safeEntryPath("a\\b.html"))
    }

    @Test fun entryPrefersIndexThenShallowest() {
        assertEquals("index.html", HtmlRules.pickEntry(listOf("b.html", "index.html", "x/index.html")))
        assertEquals("a.html", HtmlRules.pickEntry(listOf("z/q.html", "a.html", "img/x.png")))
        assertEquals("x/index.htm", HtmlRules.pickEntry(listOf("x/index.htm", "y/z/other.html")))
        assertNull(HtmlRules.pickEntry(listOf("a.png")))
    }

    @Test fun bootScriptIsFirstInHead() {
        val out = HtmlRules.injectBoot("<!DOCTYPE html><html><head><title>t</title></head><body></body></html>")
        assertTrue(out.contains("<head><script src=\"/__bp/boot.js\"></script><title>"))
        val attrs = HtmlRules.injectBoot("<HTML><HEAD lang=\"it\"><meta charset=utf-8></HEAD></HTML>")
        assertTrue(attrs.contains("<HEAD lang=\"it\"><script src=\"/__bp/boot.js\"></script><meta"))
    }

    @Test fun bootScriptWithoutHead() {
        assertTrue(HtmlRules.injectBoot("<html><body>x</body></html>").startsWith("<html><script src="))
        assertTrue(HtmlRules.injectBoot("<!doctype html><p>x</p>").startsWith("<!doctype html><script src="))
        assertTrue(HtmlRules.injectBoot("<p>x</p>").startsWith("<script src="))
    }

    @Test fun pageContentIsOtherwiseUntouched() {
        val src = "<html><head><style>a{color:red}</style></head><body><button onclick=\"x()\">ok</button></body></html>"
        val out = HtmlRules.injectBoot(src)
        assertEquals(src, out.replace("<script src=\"/__bp/boot.js\"></script>", ""))
    }

    @Test fun charsetFromMetaOrUtf8() {
        assertEquals(Charsets.UTF_8, HtmlRules.detectCharset("<meta charset=\"utf-8\">è".toByteArray()))
        assertEquals("windows-1252", HtmlRules.detectCharset("<meta charset=windows-1252>".toByteArray()).name().lowercase())
        assertEquals(Charsets.UTF_8, HtmlRules.detectCharset("<meta charset=nonsense-xyz>".toByteArray()))
        assertEquals(Charsets.UTF_8, HtmlRules.detectCharset("plain".toByteArray()))
    }

    @Test fun eachDocumentHasItsOwnOrigin() {
        assertNotEquals(HtmlRules.hostFor("aaa"), HtmlRules.hostFor("bbb"))
        assertTrue(HtmlRules.isOwnUrl("https://${HtmlRules.hostFor("aaa")}/x.html", HtmlRules.hostFor("aaa")))
        assertFalse(HtmlRules.isOwnUrl("https://${HtmlRules.hostFor("bbb")}/x.html", HtmlRules.hostFor("aaa")))
        assertFalse(HtmlRules.isOwnUrl("https://${HtmlRules.hostFor("aaa")}.evil.com/", HtmlRules.hostFor("aaa")))
    }

    @Test fun sortAndSearch() {
        val a = HtmlDocument("1", "Economia 2", "d.html", importedAt = 10, lastOpenedAt = 50)
        val b = HtmlDocument("2", "Matematica", "d.html", importedAt = 30, lastOpenedAt = 0)
        val c = HtmlDocument("3", "economia 1", "d.html", importedAt = 20, lastOpenedAt = 90)
        val all = listOf(a, b, c)
        assertEquals(listOf("3", "1", "2"), HtmlRules.sortAndFilter(all, HtmlRules.Sort.NAME, "").map { it.id })
        assertEquals(listOf("2", "3", "1"), HtmlRules.sortAndFilter(all, HtmlRules.Sort.IMPORTED, "").map { it.id })
        assertEquals(listOf("3", "1", "2"), HtmlRules.sortAndFilter(all, HtmlRules.Sort.OPENED, "").map { it.id })
        assertEquals(listOf("3", "1"), HtmlRules.sortAndFilter(all, HtmlRules.Sort.NAME, " ECONOMIA ").map { it.id })
    }

    @Test fun titleAndNames() {
        assertEquals("Lezione 1", HtmlRules.titleFromName("Lezione 1.html"))
        assertEquals("a.b", HtmlRules.titleFromName("a.b.htm"))
        assertTrue(HtmlRules.isHtmlName("X.HTM"))
        assertFalse(HtmlRules.isHtmlName("x.txt"))
    }

    @Test fun bootScriptCarriesState() {
        assertTrue(HtmlRules.bootScript("run();", null).startsWith("window.__BP_STATE__=null;"))
        assertTrue(HtmlRules.bootScript("run();", "{\"v\":1}").startsWith("window.__BP_STATE__={\"v\":1};"))
    }
}
