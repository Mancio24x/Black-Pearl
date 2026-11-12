package org.qosp.notes.components.links

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteOutlineTest {
    private fun titles(md: String) = NoteOutline.headings(md).map { it.level to it.text }

    @Test fun listsAtxHeadingsInOrderWithLevelAndLineOffset() {
        val md = "# One\ntext\n## Two\n###### v16\n"
        val h = NoteOutline.headings(md)
        assertEquals(listOf(1 to "One", 2 to "Two", 6 to "v16"), h.map { it.level to it.text })
        assertEquals(listOf(0, md.indexOf("## Two"), md.indexOf("###### v16")), h.map { it.offset })
    }

    @Test fun tagsSevenHashesAndEmptyHeadingsAreNotHeadings() {
        assertEquals(emptyList<Pair<Int, String>>(), titles("#tag\n####### seven\n#\n##   \n### ###\n"))
    }

    @Test fun closingHashesAndUpToThreeLeadingSpaces() {
        assertEquals(listOf(2 to "Two", 1 to "C#", 3 to "Three"), titles("## Two ##\n# C#\n   ### Three\n    # code\n"))
    }

    @Test fun skipsFencedCodeAndFrontMatter() {
        val md = "---\ntitle: x\n# not a heading\n---\n# Real\n```\n# in code\n```\n~~~\n## also code\n~~~\n## After"
        assertEquals(listOf(1 to "Real", 2 to "After"), titles(md))
    }

    @Test fun unclosedFrontMatterIsNormalText() {
        assertEquals(listOf(1 to "Yes"), titles("---\n# Yes"))
    }

    @Test fun inlineMarkupIsRemovedLikeThePreview() {
        val md = "## **Gospels**\n## The `foo` *bar* _baz_\n## [[John-03|John 3]] and [[Romans]]\n" +
            "## [WEB](https://x.org) Bible\n## snake_case_name\n## Verse ^v16\n## 2 \\* 3\n"
        assertEquals(
            listOf("Gospels", "The foo bar baz", "John 3 and Romans", "WEB Bible", "snake_case_name", "Verse", "2 * 3"),
            titles(md).map { it.second },
        )
    }

    @Test fun windowsLineEndings() {
        assertEquals(listOf(1 to "A", 2 to "B"), titles("# A\r\ntext\r\n## B\r\n"))
    }

    @Test fun noHeadingsIsEmpty() {
        assertTrue(NoteOutline.headings("just text, no hash").isEmpty())
        assertTrue(NoteOutline.headings("").isEmpty())
    }

    @Test fun matchKeyIgnoresCaseAndSpacing() {
        assertEquals(NoteOutline.matchKey("Pentateuch (or the Law)"), NoteOutline.matchKey("  pentateuch   (OR the law) "))
    }
}
