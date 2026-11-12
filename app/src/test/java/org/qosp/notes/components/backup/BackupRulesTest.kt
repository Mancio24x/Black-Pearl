package org.qosp.notes.components.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupRulesTest {

    private fun media(name: String, dir: Boolean = false) = BackupRules.mediaFileName(name, dir, "media")

    @Test
    fun acceptsOnlyPlainFilesDirectlyInMedia() {
        assertEquals("a.jpg", media("media/a.jpg"))
        assertEquals("1_a b.jpg", media("media/1_a b.jpg"))
        assertNull(media("media/", dir = true))
        assertNull(media("media/sub/", dir = true))
        assertNull(media("media/sub/a.jpg"))
        assertNull(media("__MACOSX/media/._a.jpg"))
        assertNull(media("media/._a.jpg"))
        assertNull(media("other.txt"))
        assertNull(media("backup.json"))
    }

    @Test
    fun rejectsPathTraversalAndAbsolutePaths() {
        assertNull(media("media/../a.jpg"))
        assertNull(media("media/.."))
        assertNull(media("media/."))
        assertNull(media("../media/a.jpg"))
        assertNull(media("/media/a.jpg"))
        assertNull(media("/etc/passwd"))
        assertNull(media("media/..\\..\\a.jpg"))
        assertEquals("C:a.jpg", media("media/C:a.jpg")) // valid file name on Android
    }

    @Test
    fun uniqueNameNeverReturnsATakenName() {
        val taken = mutableSetOf<String>()
        fun add(name: String) = BackupRules.uniqueName(name) { it in taken }.also { taken += it }

        assertEquals("a.jpg", add("a.jpg"))
        assertEquals("1_a.jpg", add("1_a.jpg")) // a real file name that looks like a generated one
        assertEquals("2_a.jpg", add("a.jpg"))   // must not overwrite "1_a.jpg"
        assertEquals("3_a.jpg", add("a.jpg"))
        assertEquals("b.jpg", add("b.jpg"))
    }

    @Test
    fun includeFilesStyleCounterSkipsTakenNames() {
        // Same calls as AttachmentHandler.IncludeFiles: start after the last index used for that name
        val map = mutableMapOf<String, String>()
        val count = mutableMapOf<String, Int>()
        fun handle(name: String, uri: String) {
            val fileName = BackupRules.uniqueName(name, (count[name] ?: 0) + 1) { map.containsKey(it) }
            if (fileName != name) count[name] = fileName.substringBefore('_').toInt()
            map[fileName] = uri
        }
        handle("a.jpg", "A")
        handle("1_a.jpg", "X")
        handle("a.jpg", "B")
        handle("a.jpg", "C")
        assertEquals(mapOf("a.jpg" to "A", "1_a.jpg" to "X", "2_a.jpg" to "B", "3_a.jpg" to "C"), map)
    }

    @Test
    fun remapsInlineMediaLinksOfRenamedFiles() {
        val app = "content://io.github.mancio24x.blackpearl.provider/media_files"
        val content = "Foto ![](${app}/a.jpg) e ![x](${app}/a.jpg.png) e ![](${app}/b%20c.jpg)\n" +
            "Quillpad: ![](content://org.qosp.notes.provider/media_files/a.jpg)"
        val links = mapOf(
            "a.jpg" to "$app/1_a.jpg",
            "b%20c.jpg" to "$app/1_b%20c.jpg",
        )
        val expected = "Foto ![](${app}/1_a.jpg) e ![x](${app}/a.jpg.png) e ![](${app}/1_b%20c.jpg)\n" +
            "Quillpad: ![](${app}/1_a.jpg)"
        assertEquals(expected, BackupRules.remapMediaLinks(content, links))
    }

    @Test
    fun leavesOtherLinksAndPlainTextAlone() {
        val content = "[web](https://example.com/media_files/a.jpg) media_files/a.jpg ![](file:///x/a.jpg)"
        assertEquals(content, BackupRules.remapMediaLinks(content, mapOf("a.jpg" to "content://p/media_files/1_a.jpg")))
        assertEquals("no links", BackupRules.remapMediaLinks("no links", mapOf("a.jpg" to "x")))
    }
}
