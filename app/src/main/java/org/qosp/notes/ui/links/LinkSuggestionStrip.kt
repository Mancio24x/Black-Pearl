package org.qosp.notes.ui.links

import android.view.KeyEvent
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.chip.Chip
import org.qosp.notes.R
import org.qosp.notes.components.links.LinkIndex
import org.qosp.notes.ui.utils.views.ExtendedEditText

class LinkSuggestionStrip(
    private val editor: ExtendedEditText,
    private val scroller: HorizontalScrollView,
    private val buttons: LinearLayout,
    private val formatting: android.view.View,
    private val index: () -> LinkIndex,
    private val noteId: () -> Long?,
) {
    private var lastChoices = emptyList<Suggestion>()
    private var dismissedAt: Pair<Int, String>? = null
    private var enabled = false

    init {
        editor.onCaretChanged = { refresh(enabled) }
        editor.doAfterTextChanged { refresh(enabled) }
        editor.setOnKeyListener { _, key, event ->
            if (key == KeyEvent.KEYCODE_ESCAPE && event.action == KeyEvent.ACTION_DOWN && scroller.isVisible) {
                val cursor = editor.selectionStart
                dismissedAt = cursor to LinkSuggestions.query(editor.text.orEmpty(), cursor).orEmpty()
                refresh(enabled)
                true
            } else false
        }
    }

    fun refresh(showToolbar: Boolean) {
        enabled = showToolbar
        val cursor = editor.selectionStart
        val query = if (enabled && cursor == editor.selectionEnd)
            LinkSuggestions.query(editor.text.orEmpty(), cursor) else null
        if (query == null || dismissedAt != (cursor to query)) dismissedAt = null
        val choices = if (query != null && dismissedAt == null)
            LinkSuggestions.suggest(query, index(), noteId()) else emptyList()
        formatting.isVisible = choices.isEmpty()
        scroller.isVisible = choices.isNotEmpty()
        if (choices == lastChoices) return
        lastChoices = choices
        buttons.removeAllViews()
        for (choice in choices) {
            buttons.addView(Chip(editor.context).apply {
                text = when (choice.kind) {
                    Suggestion.Kind.CREATE -> context.getString(R.string.link_suggest_create, choice.title)
                    Suggestion.Kind.GHOST -> context.getString(R.string.link_suggest_missing, choice.title)
                    Suggestion.Kind.NOTE -> choice.title
                }
                setOnClickListener {
                    val editable = editor.text ?: return@setOnClickListener
                    val caret = editor.selectionStart
                    if (LinkSuggestions.query(editable, caret) == null) return@setOnClickListener
                    val (start, end) = LinkSuggestions.replaceRange(editable, caret)
                    val link = "[[${choice.title}]]"
                    editable.replace(start, end, link)
                    editor.setSelection(start + link.length)
                }
            })
        }
        scroller.scrollTo(0, 0)
    }
}
