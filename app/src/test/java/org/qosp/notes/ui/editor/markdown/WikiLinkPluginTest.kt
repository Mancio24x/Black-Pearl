package org.qosp.notes.ui.editor.markdown

import android.text.Spanned
import android.text.style.ClickableSpan
import android.text.style.URLSpan
import android.widget.TextView
import io.noties.markwon.LinkResolver
import io.noties.markwon.Markwon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qosp.notes.R
import org.qosp.notes.components.links.LinkIndex
import org.qosp.notes.data.model.Note
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WikiLinkPluginTest {

    private val context = RuntimeEnvironment.getApplication()

    private fun render(markdown: String, index: LinkIndex = LinkIndex(emptyList())): TextView {
        val markwon = Markwon.builder(context).usePlugin(WikiLinkPlugin { index }).build()
        return TextView(context).also { markwon.setMarkdown(it, markdown) }
    }

    private fun TextView.spanned() = text as Spanned

    private fun TextView.pills() = spanned().getSpans(0, text.length, WikiLinkPlugin.WikiPillSpan::class.java)

    @Test
    fun rewritesOutsideCodeOnly() {
        val plugin = WikiLinkPlugin { LinkIndex(emptyList()) }
        assertEquals("x [b](blackpearl-link:A#) `[[c]]`", plugin.processMarkdown("x [[A|b]] `[[c]]`"))
    }

    @Test
    fun ghostPillForMissing() {
        val view = render("[[Nope]]")
        val pills = view.pills()
        assertEquals(1, pills.size)
        assertTrue(pills[0].ghost)
        assertEquals("Nope", view.text.toString().trim())
        assertEquals(0, view.spanned().getSpans(0, view.text.length, URLSpan::class.java).size)
    }

    @Test
    fun filledPillForExistingNote() {
        val view = render("see [[Exodus|the exodus]]", LinkIndex(listOf(Note(title = "Exodus", id = 1L))))
        val pills = view.pills()
        assertEquals(1, pills.size)
        assertFalse(pills[0].ghost)
        assertEquals("see the exodus", view.text.toString().trim())
    }

    @Test
    fun clickCallsListener() {
        val view = render("[[A#Part]]")
        var clicked: Pair<String, String?>? = null
        view.setTag(R.id.wiki_link_listener, WikiLinkPlugin.WikiLinkClick { target, heading -> clicked = target to heading })

        view.spanned().getSpans(0, view.text.length, ClickableSpan::class.java).single().onClick(view)

        assertEquals("A" to "Part", clicked)
    }

    @Test
    fun resolverHandlesOnlyWikiLinks() {
        val view = TextView(context)
        var clicked: Pair<String, String?>? = null
        view.setTag(R.id.wiki_link_listener, WikiLinkPlugin.WikiLinkClick { target, heading -> clicked = target to heading })
        val other = mutableListOf<String>()
        val resolver = WikiLinkPlugin.Resolver(LinkResolver { _, link -> other += link })

        resolver.resolve(view, WikiLinkPlugin.url("My Note", "Héading 1"))
        resolver.resolve(view, "https://example.com")

        assertEquals("My Note" to "Héading 1", clicked)
        assertEquals(listOf("https://example.com"), other)
    }
}
