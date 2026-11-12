package org.qosp.notes.components.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Test
import org.qosp.notes.data.model.Note

class LinkParseCacheTest {

    @Test
    fun sameTextIsNotParsedAgain() {
        val cache = LinkParseCache()
        val first = cache.links(1, "see [[A]] #tag")
        assertSame(first, cache.links(1, String("see [[A]] #tag".toCharArray())))
        assertEquals(listOf("tag"), cache.tags(1, "see [[A]] #tag"))
    }

    @Test
    fun changedTextIsParsedAgain() {
        val cache = LinkParseCache()
        val first = cache.links(1, "[[A]]")
        val second = cache.links(1, "[[A]] [[B]]")
        assertNotSame(first, second)
        assertEquals(listOf("A", "B"), second.map { it.target })
    }

    @Test
    fun indexWithCacheEqualsIndexWithout() {
        val notes = listOf(Note(id = 1, title = "A", content = "[[B]] #x"), Note(id = 2, title = "B", content = "[[A]]"))
        val cache = LinkParseCache()
        LinkIndex(notes, cache)
        val cached = LinkIndex(notes, cache)
        val plain = LinkIndex(notes)
        for (id in listOf(1L, 2L)) {
            assertEquals(plain.outgoing(id), cached.outgoing(id))
            assertEquals(plain.tagsOf(id), cached.tagsOf(id))
            assertEquals(plain.backlinks(id).map { it.from.id }, cached.backlinks(id).map { it.from.id })
        }
    }

    @Test
    fun retainForgetsDeletedNotes() {
        val cache = LinkParseCache()
        val first = cache.links(1, "[[A]]")
        cache.retain(emptySet())
        assertNotSame(first, cache.links(1, "[[A]]"))
    }
}
