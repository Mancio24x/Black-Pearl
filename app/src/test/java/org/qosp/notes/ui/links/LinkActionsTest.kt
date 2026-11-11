package org.qosp.notes.ui.links

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.qosp.notes.components.links.LinkIndex
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository

class LinkActionsTest {

    @Test
    fun createOrOpenOpensExisting() = runTest {
        val repo = mockk<NoteRepository>()
        val index = LinkIndex(listOf(Note(title = "Exodus", id = 7L)))

        assertEquals(7L, LinkActions.createOrOpen("exodus", 3L, repo, index))
        coVerify(exactly = 0) { repo.insertNote(any(), any()) }
    }

    @Test
    fun createOrOpenInserts() = runTest {
        val repo = mockk<NoteRepository>()
        val inserted = slot<Note>()
        coEvery { repo.insertNote(capture(inserted), any()) } returns 42L

        assertEquals(42L, LinkActions.createOrOpen("Leviticus", 3L, repo, LinkIndex(emptyList())))
        assertEquals("Leviticus", inserted.captured.title)
        assertEquals(3L, inserted.captured.notebookId)
    }
}
