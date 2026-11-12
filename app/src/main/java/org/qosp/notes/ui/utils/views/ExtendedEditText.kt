package org.qosp.notes.ui.utils.views

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Parcelable
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.getSystemService
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryOwner
import kotlinx.parcelize.Parcelize

class ExtendedEditText : AppCompatEditText {
    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    var onCaretChanged: (() -> Unit)? = null

    override fun onSelectionChanged(selStart: Int, selEnd: Int) {
        super.onSelectionChanged(selStart, selEnd)
        onCaretChanged?.invoke()
    }

    val textWatchers: MutableList<TextWatcher> = mutableListOf()

    private var registry: SavedStateRegistry? = null
    private var history = mutableListOf<TextChange>()
    private var historyIndex = 0

    private val textBeforeSelection get() = text?.substring(0 until selectionStart).orEmpty()
    val currentLineStartPos get() = textBeforeSelection.lastIndexOf("\n") + 1
    val currentLineIndex get() = textBeforeSelection.filter { it == '\n' }.length

    val selectedText get() = text?.substring(selectionStart, selectionEnd)

    var isMarkdownEnabled: Boolean = false
    var onUndoRedoListener: OnCanUndoRedoListener? = null

    // With a regular EditText, users can paste rich text inside which may look out of place.
    // This function prevents that from happening by changing the clip board
    override fun onTextContextMenuItem(id: Int): Boolean {
        if (id == android.R.id.paste) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return super.onTextContextMenuItem(android.R.id.pasteAsPlainText)
            }

            // If device doesn't support paste as plain text
            val manager = context.getSystemService<ClipboardManager>() ?: return super.onTextContextMenuItem(id)
            val clipData = manager.primaryClip ?: return super.onTextContextMenuItem(id)

            for (i in 0 until clipData.itemCount) {
                val text = clipData.getItemAt(i).coerceToText(context).toString()
                manager.setPrimaryClip(ClipData.newPlainText("", text))
            }
        }

        return super.onTextContextMenuItem(id)
    }

    /**
     * Executes the given block without notifying any TextWatchers.
     */
    inline fun withoutTextWatchers(block: ExtendedEditText.() -> Unit) {
        val watchers = textWatchers.toList()

        watchers.forEach { removeTextChangedListener(it) }
        block(this)
        watchers.forEach { addTextChangedListener(it) }
    }

    /**
     * Executes the given block with only notifying TextWatchers which are instances of T.
     */
    inline fun <reified T> withOnlyTextWatcher(block: ExtendedEditText.() -> Unit) {
        val watchers = textWatchers
            .filterNot { it is T }
            .toList()

        watchers.forEach { removeTextChangedListener(it) }
        block(this)
        watchers.forEach { addTextChangedListener(it) }
    }

    /**
     * Executes the given block without notifying TextWatchers which are instances of T.
     */
    inline fun <reified T> withoutTextWatcher(block: ExtendedEditText.() -> Unit) {
        val watchers = textWatchers
            .filter { it is T }
            .toList()

        watchers.forEach { removeTextChangedListener(it) }
        block(this)
        watchers.forEach { addTextChangedListener(it) }
    }

    override fun addTextChangedListener(watcher: TextWatcher?) {
        // Crashes if textWatcher is null during instantiation
        if (textWatchers != null) {
            if (watcher != null) textWatchers.add(watcher)
        }
        super.addTextChangedListener(watcher)
    }

    override fun removeTextChangedListener(watcher: TextWatcher?) {
        // Crashes if textWatcher is null during instantiation
        if (textWatchers != null) {
            if (watcher != null) textWatchers.remove(watcher)
        }
        super.removeTextChangedListener(watcher)
    }

    fun requestFocusAndMoveCaret(): Boolean {
        return requestFocus().also { tookFocus ->
            if (tookFocus && text != null) setSelection(length())
        }
    }

    fun enableUndoRedo(owner: SavedStateRegistryOwner) {
        registry = owner.savedStateRegistry

        addTextChangedListener(UndoRedoTextWatcher())

        val bundle = registry?.consumeRestoredStateForKey(HISTORY) ?: return
        val savedHistory = bundle.getParcelableArrayList<TextChange>(HISTORY) ?: return
        val savedIndex = bundle.getInt(HISTORY_INDEX)

        historyIndex = savedIndex
        history.clear()
        history.addAll(savedHistory)
    }

    fun setOnCanUndoRedoListener(listener: OnCanUndoRedoListener) {
        onUndoRedoListener = listener
    }

    /** Forgets the undo/redo history, e.g. after a programmatic edit that the history did not record. */
    fun clearUndoRedoHistory() {
        history.clear()
        historyIndex = 0
        onUndoRedoListener?.listen(canUndo(), canRedo())
        saveHistory()
    }

    fun canUndo(): Boolean = historyIndex > 0

    fun canRedo(): Boolean = historyIndex < history.size

    fun undo() {
        if (!canUndo()) return

        val change = history[--historyIndex]
        val end: Int = change.start + change.textAfter.length

        withoutTextWatcher<UndoRedoTextWatcher> {
            text?.replace(change.start, end, change.textBefore)
        }

        requestFocusAndMoveCaret()
        onUndoRedoListener?.listen(canUndo(), canRedo())

        saveHistory()
    }

    fun redo() {
        if (!canRedo()) return

        val change = history[historyIndex++]
        val end: Int = change.start + change.textBefore.length

        withoutTextWatcher<UndoRedoTextWatcher> {
            text?.replace(change.start, end, change.textAfter)
        }

        requestFocusAndMoveCaret()
        onUndoRedoListener?.listen(canUndo(), canRedo())

        saveHistory()
    }

    private fun saveHistory() {
        registry?.unregisterSavedStateProvider(HISTORY)
        registry?.registerSavedStateProvider(HISTORY) {
            Bundle().apply {
                // an oversized history could exceed the saved-state size limit: drop it instead of crashing
                val size = history.sumOf { it.textBefore.length + it.textAfter.length }
                val keep = size <= MAX_SAVED_HISTORY_CHARS
                putParcelableArrayList(HISTORY, ArrayList(if (keep) history else emptyList()))
                putInt(HISTORY_INDEX, if (keep) historyIndex else 0)
            }
        }
    }

    inner class UndoRedoTextWatcher : TextWatcher {
        private var textBefore: CharSequence? = null
        private var textSetAtLeastOnce = false

        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {
            textBefore = s.subSequence(start, start + count)

            if (history.isEmpty()) {
                textSetAtLeastOnce = true
            }
        }

        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
            val textAfter = s.subSequence(start, start + count)
            val change = TextChange(start, textBefore ?: return, textAfter)

            if (canRedo()) {
                history = history.dropLast(history.size - historyIndex).toMutableList()
            }

            if (textSetAtLeastOnce) {
                history.add(change)
                historyIndex++
                if (history.size > MAX_HISTORY_ENTRIES) {
                    history.removeAt(0)
                    historyIndex--
                }
            }

            onUndoRedoListener?.listen(canUndo(), canRedo())
            saveHistory()
        }

        override fun afterTextChanged(s: Editable) {
            textSetAtLeastOnce = true
        }
    }

    fun interface OnCanUndoRedoListener {
        fun listen(canUndo: Boolean, canRedo: Boolean)
    }

    companion object {
        const val HISTORY = "HISTORY"
        const val HISTORY_INDEX = "HISTORY_INDEX"
        private const val MAX_HISTORY_ENTRIES = 500
        private const val MAX_SAVED_HISTORY_CHARS = 100_000
    }
}

@Parcelize
data class TextChange(
    val start: Int,
    val textBefore: CharSequence,
    val textAfter: CharSequence,
) : Parcelable
