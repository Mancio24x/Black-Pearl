package org.qosp.notes.ui.links

import org.junit.Assert.assertSame
import android.view.ContextThemeWrapper
import android.view.View
import android.widget.TextView
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.qosp.notes.R
import org.qosp.notes.components.links.Backlink
import org.qosp.notes.data.model.Note
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class BacklinksViewTest {

    private val context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.BaseAppTheme)

    private fun items() = listOf(
        Backlink(Note(title = "Genesis", id = 1L), "see [[Exodus]] next"),
        Backlink(Note(title = "Leviticus", id = 2L), "[[Exodus]]"),
    )

    @Test
    fun goneWhenEmpty() {
        val view = BacklinksView(context)
        view.show(items()) {}
        view.show(emptyList()) {}
        assertEquals(View.GONE, view.visibility)
        assertEquals(1, view.childCount)
    }

    @Test
    fun showsHeaderCountAndRows() {
        val view = BacklinksView(context)
        view.show(items()) {}
        assertEquals(View.VISIBLE, view.visibility)
        assertEquals("Linked from · 2", (view.getChildAt(0) as TextView).text.toString())
        assertEquals(3, view.childCount)
        assertEquals("Genesis", view.getChildAt(1).findViewById<TextView>(R.id.backlink_title).text.toString())
    }

    @Test
    fun clickReturnsItem() {
        val view = BacklinksView(context)
        var clicked: Backlink? = null
        view.show(items()) { clicked = it }
        view.getChildAt(2).performClick()
        assertEquals(2L, clicked?.from?.id)
    }

    @Test
    fun sameListKeepsTheCards() {
        val view = BacklinksView(context)
        view.show(items()) {}
        val card = view.getChildAt(1)
        view.show(items()) {}
        assertSame(card, view.getChildAt(1))
    }
}
