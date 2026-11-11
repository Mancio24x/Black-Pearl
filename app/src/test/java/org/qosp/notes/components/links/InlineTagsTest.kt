package org.qosp.notes.components.links

import org.junit.Assert.assertEquals
import org.junit.Test

class InlineTagsTest {

    @Test
    fun findsTagsAtStartAfterCommaAndInParentheses() {
        assertEquals(
            listOf("prayer", "Faith", "hope"),
            InlineTags.find("#prayer at start\nand #Faith, then (#hope)."),
        )
    }

    @Test
    fun ignoresPureNumbersHeadingsAndKeepsNested() {
        assertEquals(listOf("2024a", "a/b"), InlineTags.find("#2024 and #2024a and #a/b and # Heading"))
    }

    @Test
    fun ignoresCodeSpansAndFencedBlocks() {
        assertEquals(listOf("ok"), InlineTags.find("`#code` and\n```\n#fenced\n```\n#ok"))
    }

    @Test
    fun ignoresWikiLinkHeadingsUrlFragmentsAndMidWordHashes() {
        assertEquals(emptyList<String>(), InlineTags.find("[[Note#Heading]] https://x.y/#frag word#mid"))
    }

    @Test
    fun dedupesCaseInsensitivelyKeepingFirstSpelling() {
        assertEquals(listOf("Same"), InlineTags.find("#Same and #same"))
    }
}
