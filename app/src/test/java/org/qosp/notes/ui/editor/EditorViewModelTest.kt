package org.qosp.notes.ui.editor

import androidx.lifecycle.viewModelScope
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository

@OptIn(ExperimentalCoroutinesApi::class)
class EditorViewModelTest {
    @Before
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @After
    fun tearDown() = Dispatchers.resetMain()

    /** Typing and closing the note at once: the text typed last is still written. */
    @Test
    fun aWriteAlreadyAskedForIsFinishedWhenTheEditorCloses() {
        val stored = MutableSharedFlow<Note?>() // the database answers only after the editor has closed
        val repo = mockk<NoteRepository>(relaxed = true) { every { getById(7L) } returns stored }
        val model = EditorViewModel(repo, mockk(relaxed = true), mockk(relaxed = true))
        EditorViewModel::class.java.getDeclaredField("noteIdFlow").apply { isAccessible = true }
            .let { @Suppress("UNCHECKED_CAST") (it.get(model) as MutableStateFlow<Long?>).value = 7L }

        model.setNoteContent("last words")
        model.viewModelScope.cancel() // what closing the editor does
        runBlocking { stored.emit(Note(id = 7L, content = "last")) }

        coVerify(timeout = 3000) { repo.updateNotes(match { it.content == "last words" }, sync = any()) }
    }
}
