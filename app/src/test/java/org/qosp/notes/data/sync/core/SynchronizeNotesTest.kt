package org.qosp.notes.data.sync.core

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.InjectMockKs
import io.mockk.impl.annotations.MockK
import io.mockk.junit4.MockKRule
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.IdMappingRepository
import org.qosp.notes.preferences.CloudService

class SynchronizeNotesTest {

    @get:Rule
    val mockkRule = MockKRule(this)

    @MockK
    private lateinit var idMappingRepository: IdMappingRepository

    @InjectMockKs
    private lateinit var synchronizeNotes: SynchronizeNotes

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
    }

    @Test
    fun `empty local and remote notes returns empty result`() = runTest {
        // Given
        val localNotes = emptyList<Note>()
        val remoteNotes = emptyList<SyncNote>()
        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns emptyList()

        // When
        val result = synchronizeNotes(localNotes, remoteNotes, CloudService.NEXTCLOUD)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(0, result.remoteUpdates.size)
        coVerify { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) }
    }

    @Test
    fun `local notes without mapping should be created remotely`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Local Note", modifiedDate = 100L)
        val localNotes = listOf(localNote)
        val remoteNotes = emptyList<SyncNote>()
        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns emptyList()

        // When
        val result = synchronizeNotes(localNotes, remoteNotes, CloudService.NEXTCLOUD)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(1, result.remoteUpdates.size)
        val action = result.remoteUpdates[0] as NoteAction.Create
        assertEquals(localNote, action.note)
        assertEquals("", action.remoteNote.idStr)
        assertEquals(localNote.title, action.remoteNote.title)
        assertEquals(localNote.modifiedDate, action.remoteNote.lastModified)
    }

    @Test
    fun `remote notes without mapping should be created locally`() = runTest {
        // Given
        val remoteNote =
            SyncNote(id = 0L, idStr = "remote1", title = "Remote Note", lastModified = 100L, content = null)
        val localNotes = emptyList<Note>()
        val remoteNotes = listOf(remoteNote)
        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns emptyList()

        // When
        val result = synchronizeNotes(localNotes, remoteNotes, CloudService.NEXTCLOUD)

        // Then
        assertEquals(1, result.localUpdates.size)
        assertEquals(0, result.remoteUpdates.size)
        val action = result.localUpdates[0] as NoteAction.Create
        assertEquals(remoteNote.title, action.note.title)
        assertEquals(remoteNote.lastModified, action.note.modifiedDate)
        assertEquals(remoteNote, action.remoteNote)
    }

    @Test
    fun `local note newer than remote note should update remote`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Local Note", modifiedDate = 200L)
        val remoteNote = SyncNote(id = 0L, idStr = "2", title = "Remote Note", lastModified = 100L, content = null)
        val mapping = IdMapping(
            localNoteId = 1L,
            remoteNoteId = 2L,
            provider = CloudService.NEXTCLOUD,
            extras = null,
            isDeletedLocally = false
        )

        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns listOf(mapping)

        // When
        val result = synchronizeNotes(listOf(localNote), listOf(remoteNote), CloudService.NEXTCLOUD)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(1, result.remoteUpdates.size)
        val action = result.remoteUpdates[0] as NoteAction.Update
        assertEquals(localNote, action.note)
        assertEquals(remoteNote, action.remoteNote)
    }

    @Test
    fun `remote note newer than local note should update local`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Local Note", modifiedDate = 100L)
        val remoteNote = SyncNote(id = 0L, idStr = "2", title = "Remote Note", lastModified = 200L, content = null)
        val mapping = IdMapping(
            localNoteId = 1L,
            remoteNoteId = 2L,
            provider = CloudService.NEXTCLOUD,
            extras = null,
            isDeletedLocally = false
        )

        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns listOf(mapping)

        // When
        val result = synchronizeNotes(listOf(localNote), listOf(remoteNote), CloudService.NEXTCLOUD)

        // Then
        assertEquals(1, result.localUpdates.size)
        assertEquals(0, result.remoteUpdates.size)
        val action = result.localUpdates[0] as NoteAction.Update
        assertEquals(localNote, action.note)
        assertEquals(remoteNote, action.remoteNote)
    }

    @Test
    fun `deleted local note should delete remote note`() = runTest {
        // Given
        val remoteNote = SyncNote(id = 0L, idStr = "2", title = "Remote Note", lastModified = 100L, content = null)
        val mapping = IdMapping(
            localNoteId = 1L,
            remoteNoteId = 2L,
            provider = CloudService.NEXTCLOUD,
            extras = null,
            isDeletedLocally = false
        )

        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns listOf(mapping)

        // When
        val result = synchronizeNotes(emptyList(), listOf(remoteNote), CloudService.NEXTCLOUD)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(1, result.remoteUpdates.size)
        val action = result.remoteUpdates[0] as NoteAction.Delete
        assertEquals(1L, action.note.id)
        assertEquals(remoteNote, action.remoteNote)
    }

    @Test
    fun `deleted remote note should trigger local delete action`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Local Note", modifiedDate = 100L, content = "Some content")
        val mapping = IdMapping(
            localNoteId = 1L,
            remoteNoteId = 2L,
            provider = CloudService.NEXTCLOUD,
            extras = null,
            isDeletedLocally = false
        )

        // A second, unchanged note keeps the listing non-empty (an empty listing aborts the sync, see SyncSafetyGuard)
        val otherLocal = Note(id = 3L, title = "Other", modifiedDate = 100L)
        val otherMapping = IdMapping(
            localNoteId = 3L, remoteNoteId = 4L, provider = CloudService.NEXTCLOUD, extras = null,
            isDeletedLocally = false
        )
        val otherRemote = SyncNote(id = 4L, idStr = "4", title = "Other", lastModified = 100L, content = null)

        coEvery { idMappingRepository.getAllByProvider(CloudService.NEXTCLOUD) } returns listOf(mapping, otherMapping)

        // When
        val result =
            synchronizeNotes(listOf(localNote, otherLocal), listOf(otherRemote), CloudService.NEXTCLOUD)

        // Then
        assertEquals(1, result.localUpdates.size)
        assertEquals(0, result.remoteUpdates.size)
        val action = result.localUpdates[0] as NoteAction.Delete
        assertEquals(localNote, action.note)
        // Check that the remoteNote in the action is a placeholder, as the actual remote note is gone
        assertEquals("", action.remoteNote.idStr)
        assertEquals(localNote.title, action.remoteNote.title)
        assertEquals(localNote.modifiedDate, action.remoteNote.lastModified)
        assertEquals(localNote.content, action.remoteNote.content)
    }

    @Test
    fun `file storage service should use storageUri instead of remoteNoteId`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Local Note", modifiedDate = 200L)
        val remoteNote = SyncNote(
            id = 0L,
            idStr = "file://path/to/note.txt",
            title = "Remote Note",
            lastModified = 100L,
            content = null
        )
        val mapping = IdMapping(
            localNoteId = 1L,
            remoteNoteId = null,
            provider = CloudService.FILE_STORAGE,
            extras = null,
            isDeletedLocally = false,
            storageUri = "file://path/to/note.txt"
        )

        coEvery { idMappingRepository.getAllByProvider(CloudService.FILE_STORAGE) } returns listOf(mapping)

        // When
        val result = synchronizeNotes(listOf(localNote), listOf(remoteNote), CloudService.FILE_STORAGE)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(1, result.remoteUpdates.size)
        val action = result.remoteUpdates[0] as NoteAction.Update
        assertEquals(localNote, action.note)
        assertEquals(remoteNote, action.remoteNote)
    }
    // Tests for TITLE sync method

    @Test
    fun `title sync - empty local and remote notes returns empty result`() = runTest {
        // Given
        val localNotes = emptyList<Note>()
        val remoteNotes = emptyList<SyncNote>()

        // When
        val result = synchronizeNotes(localNotes, remoteNotes, CloudService.NEXTCLOUD, SyncMethod.TITLE)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(0, result.remoteUpdates.size)
    }

    @Test
    fun `title sync - local notes without matching remote title should be created remotely`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Local Note", modifiedDate = 100L)
        val localNotes = listOf(localNote)
        val remoteNotes = emptyList<SyncNote>()

        // When
        val result = synchronizeNotes(localNotes, remoteNotes, CloudService.NEXTCLOUD, SyncMethod.TITLE)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(1, result.remoteUpdates.size)
        val action = result.remoteUpdates[0] as NoteAction.Create
        assertEquals(localNote, action.note)
        assertEquals("", action.remoteNote.idStr)
        assertEquals(localNote.title, action.remoteNote.title)
        assertEquals(localNote.modifiedDate, action.remoteNote.lastModified)
    }

    @Test
    fun `title sync - remote notes without matching local title should be created locally`() = runTest {
        // Given
        val remoteNote =
            SyncNote(id = 0L, idStr = "remote1", title = "Remote Note", lastModified = 100L, content = null)
        val localNotes = emptyList<Note>()
        val remoteNotes = listOf(remoteNote)

        // When
        val result = synchronizeNotes(localNotes, remoteNotes, CloudService.NEXTCLOUD, SyncMethod.TITLE)

        // Then
        assertEquals(1, result.localUpdates.size)
        assertEquals(0, result.remoteUpdates.size)
        val action = result.localUpdates[0] as NoteAction.Create
        assertEquals(remoteNote.title, action.note.title)
        assertEquals(remoteNote.lastModified, action.note.modifiedDate)
        assertEquals(remoteNote, action.remoteNote)
    }

    @Test
    fun `title sync - local note newer than remote note with same title should update remote`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Same Title", modifiedDate = 200L)
        val remoteNote = SyncNote(id = 0L, idStr = "remote1", title = "Same Title", lastModified = 100L, content = null)

        // When
        val result = synchronizeNotes(listOf(localNote), listOf(remoteNote), CloudService.NEXTCLOUD, SyncMethod.TITLE)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(1, result.remoteUpdates.size)
        val action = result.remoteUpdates[0] as NoteAction.Update
        assertEquals(localNote, action.note)
        assertEquals(remoteNote, action.remoteNote)
    }

    @Test
    fun `title sync - remote note newer than local note with same title should update local`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Same Title", modifiedDate = 100L)
        val remoteNote = SyncNote(id = 0L, idStr = "remote1", title = "Same Title", lastModified = 200L, content = null)

        // When
        val result = synchronizeNotes(listOf(localNote), listOf(remoteNote), CloudService.NEXTCLOUD, SyncMethod.TITLE)

        // Then
        assertEquals(1, result.localUpdates.size)
        assertEquals(0, result.remoteUpdates.size)
        val action = result.localUpdates[0] as NoteAction.Update
        assertEquals(localNote, action.note)
        assertEquals(remoteNote, action.remoteNote)
    }

    @Test
    fun `title sync - notes with same title and similar timestamps should not create actions`() = runTest {
        // Given
        val localNote = Note(id = 1L, title = "Same Title", modifiedDate = 100L)
        val remoteNote = SyncNote(id = 0L, idStr = "remote1", title = "Same Title", lastModified = 100L, content = null)

        // When
        val result = synchronizeNotes(listOf(localNote), listOf(remoteNote), CloudService.NEXTCLOUD, SyncMethod.TITLE)

        // Then
        assertEquals(0, result.localUpdates.size)
        assertEquals(0, result.remoteUpdates.size)
    }

    // --- Black Pearl: safety guard against short / empty remote listings ---

    private fun mapped(n: Int, service: CloudService = CloudService.FILE_STORAGE) = (1..n).map { i ->
        Note(id = i.toLong(), title = "N$i", modifiedDate = 100L) to
            IdMapping(localNoteId = i.toLong(), remoteNoteId = i.toLong(), provider = service, extras = null,
                isDeletedLocally = false, storageUri = "uri$i")
    }

    private fun remoteFor(i: Int) =
        SyncNote(id = i.toLong(), idStr = "uri$i", title = "N$i", lastModified = 100L, content = "")

    @Test
    fun `empty remote listing with mapped notes aborts the sync and bins nothing`() = runTest {
        val pairs = mapped(1)
        coEvery { idMappingRepository.getAllByProvider(CloudService.FILE_STORAGE) } returns pairs.map { it.second }

        val result = synchronizeNotes(pairs.map { it.first }, emptyList(), CloudService.FILE_STORAGE)

        assertNotNull(result.abortReason)
        assertTrue(result.localUpdates.isEmpty())
        assertTrue(result.remoteUpdates.isEmpty())
    }

    @Test
    fun `more than half of mapped notes missing aborts the sync`() = runTest {
        val pairs = mapped(5)
        coEvery { idMappingRepository.getAllByProvider(CloudService.FILE_STORAGE) } returns pairs.map { it.second }

        // only 2 of 5 files listed: 3 missing > half
        val result = synchronizeNotes(pairs.map { it.first }, listOf(remoteFor(1), remoteFor(2)), CloudService.FILE_STORAGE)

        assertNotNull(result.abortReason)
        assertTrue(result.localUpdates.isEmpty())
        assertTrue(result.remoteUpdates.isEmpty())
    }

    @Test
    fun `a few missing notes are still binned`() = runTest {
        val pairs = mapped(5)
        coEvery { idMappingRepository.getAllByProvider(CloudService.FILE_STORAGE) } returns pairs.map { it.second }

        // 2 of 5 missing: below the threshold
        val listed = listOf(remoteFor(1), remoteFor(2), remoteFor(3))
        val result = synchronizeNotes(pairs.map { it.first }, listed, CloudService.FILE_STORAGE)

        assertNull(result.abortReason)
        assertEquals(2, result.localUpdates.filterIsInstance<NoteAction.Delete>().size)
        assertTrue(result.remoteUpdates.isEmpty())
    }

    @Test
    fun `file reappearing without mapping is imported, never deleted remotely`() = runTest {
        // the note was binned by the sync and its mapping removed: the file comes back
        val pairs = mapped(1)
        coEvery { idMappingRepository.getAllByProvider(CloudService.FILE_STORAGE) } returns pairs.map { it.second }
        val back = SyncNote(id = 0L, idStr = "uri-old", title = "Back", lastModified = 100L, content = "x")

        val result = synchronizeNotes(pairs.map { it.first }, listOf(remoteFor(1), back), CloudService.FILE_STORAGE)

        assertTrue(result.remoteUpdates.none { it is NoteAction.Delete })
        assertEquals(back, (result.localUpdates.single() as NoteAction.Create).remoteNote)
    }

    @Test
    fun `guard thresholds`() {
        assertNull(SyncSafetyGuard.abortReason(mappedCount = 0, missingCount = 0, remoteCount = 0))
        assertNotNull(SyncSafetyGuard.abortReason(mappedCount = 1, missingCount = 1, remoteCount = 0))
        assertNull(SyncSafetyGuard.abortReason(mappedCount = 2, missingCount = 2, remoteCount = 3)) // < 3 missing
        assertNull(SyncSafetyGuard.abortReason(mappedCount = 6, missingCount = 3, remoteCount = 3)) // exactly half
        assertNotNull(SyncSafetyGuard.abortReason(mappedCount = 5, missingCount = 3, remoteCount = 2))
        assertNull(SyncSafetyGuard.abortReason(mappedCount = 100, missingCount = 10, remoteCount = 90))
    }
}
