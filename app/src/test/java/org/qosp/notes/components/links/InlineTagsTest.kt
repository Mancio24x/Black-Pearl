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

    @Test
    fun ignoresMarkdownAnchorLinks() {
        assertEquals(listOf("real"), InlineTags.find("see [the part](#section) and (#real)"))
    }

    @Test
    fun ignoresTagsMadeOnlyOfSymbols() {
        assertEquals(listOf("_1", "x-y"), InlineTags.find("#- #_ #-- #// #_1 #x-y"))
    }

    @Test
    fun trimsLeadingAndTrailingSlashes() {
        assertEquals(listOf("a", "b/c"), InlineTags.find("#a/ and #/b/c/"))
    }

    @Test
    fun ignoresQueryFragmentsEmbedsAndHtmlAttributes() {
        assertEquals(
            emptyList<String>(),
            InlineTags.find("x.y/?#q a=#v ![[pic.png|#label]] <a href=\"#top\">"),
        )
    }

    @Test
    fun keepsDecomposedAccents() {
        assertEquals(listOf("cafe\u0301"), InlineTags.find("#cafe\u0301 time"))
    }

    @Test
    fun findsTagsWithCrLfAndUnicodeLetters() {
        assertEquals(listOf("uno", "città"), InlineTags.find("#uno\r\n#città"))
    }
}
