package org.qosp.notes.components.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class WikiLinksTest {

    @Test
    fun findsPlainAliasHeading() {
        val links = WikiLinks.find("a [[Genesis]] b [[010 Faith MOC|Faith MOC ]] c [[Note#Part|x]]")
        assertEquals(listOf("Genesis", "010 Faith MOC", "Note"), links.map { it.target })
        assertEquals(listOf(null, "Faith MOC ", "x"), links.map { it.label })
        assertEquals("Part", links[2].heading)
        assertEquals("Faith MOC", links[1].shown)
        assertEquals("Genesis", links[0].shown)
        assertEquals(2..12, links[0].range)
    }

    @Test
    fun skipsEmbedsCodeAndUnclosed() {
        val md = "![[pic.png]] `[[a]]` ```\n[[b]]\n``` [[c"
        assertTrue(WikiLinks.find(md).isEmpty())
    }

    @Test
    fun skipsFencedBlocksButKeepsLinksAfterThem() {
        val md = "```\n[[a]]\n```\n[[b]]"
        assertEquals(listOf("b"), WikiLinks.find(md).map { it.target })
    }

    @Test
    fun keyNormalizes() {
        assertEquals("genesis", WikiLinks.key(" Folder/Genesis.md "))
        assertEquals("genesis", WikiLinks.key("GENESIS"))
    }

    @Test
    fun demoFileHas69Links() {
        val text = File("../docs/superpowers/specs/linked-notes/The Bible.md").readText()
        assertEquals(69, WikiLinks.find(text).size)
    }
}
