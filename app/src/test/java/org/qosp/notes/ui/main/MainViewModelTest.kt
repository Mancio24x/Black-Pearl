package org.qosp.notes.ui.main

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.preferences.AppPreferences
import org.qosp.notes.preferences.PreferenceRepository

/** "All notes" leaves the Bible Study Kit notebook out in the query, not after reading it. */
class MainViewModelTest {

    private val notes = mockk<NoteRepository>()
    private val prefs = mockk<PreferenceRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { prefs.getAll() } returns flowOf(AppPreferences())
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun viewModel() = MainViewModel(notes, mockk(), prefs, mockk())

    @Test
    fun allNotesExcludesTheKitNotebookInTheQuery() = runTest(UnconfinedTestDispatcher()) {
        val mine = listOf(Note(id = 1, title = "mine"))
        every { prefs.getBibleKitNotebookId() } returns flowOf(7L)
        every { notes.getNonDeletedOrArchivedExcept(7L, any()) } returns flowOf(mine)

        val vm = viewModel()
        val job = launch { vm.data.collect {} }
        vm.initialize(null)

        assertEquals(mine, vm.data.value.notes)
        verify { notes.getNonDeletedOrArchivedExcept(7L, any()) }
        verify(exactly = 0) { notes.getNonDeletedOrArchived(any()) }
        job.cancel()
    }

    @Test
    fun withoutAKitAllNotesUsesThePlainQuery() = runTest(UnconfinedTestDispatcher()) {
        val all = listOf(Note(id = 1, title = "a"), Note(id = 2, title = "b"))
        every { prefs.getBibleKitNotebookId() } returns flowOf(null)
        every { notes.getNonDeletedOrArchived(any()) } returns flowOf(all)

        val vm = viewModel()
        val job = launch { vm.data.collect {} }
        vm.initialize(null)

        assertEquals(all, vm.data.value.notes)
        verify(exactly = 0) { notes.getNonDeletedOrArchivedExcept(any(), any()) }
        job.cancel()
    }
}
