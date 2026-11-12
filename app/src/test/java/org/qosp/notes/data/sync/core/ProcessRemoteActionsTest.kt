package org.qosp.notes.data.sync.core

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.qosp.notes.data.dao.IdMappingDao
import org.qosp.notes.data.dao.NoteDao
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.di.SyncScope
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.ui.utils.Toaster

class ProcessRemoteActionsTest {
    private val backend = mockk<ISyncBackend>()
    private val backendProvider = mockk<BackendProvider>()
    private val idMappingDao = mockk<IdMappingDao>(relaxed = true)
    private val noteDao = mockk<NoteDao>(relaxed = true)
    private val toaster = mockk<Toaster>(relaxed = true)

    private val note = Note(id = 1L, title = "T", content = "c", modifiedDate = 10L)
    private val mapping = IdMapping(
        localNoteId = 1L, remoteNoteId = null, provider = CloudService.FILE_STORAGE, extras = null,
        isDeletedLocally = false, storageUri = "uri1"
    )

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { backend.type } returns CloudService.FILE_STORAGE
        coEvery { backend.isAvailable() } returns AvailabilityStatus.Available
        every { backendProvider.isSyncing } returns true
        every { backendProvider.syncProvider } returns MutableStateFlow(backend)
    }

    private fun TestScope.process(operation: RemoteOperation) {
        ProcessRemoteActions(SyncScope(this), backendProvider, idMappingDao, noteDao, toaster)(1L, operation)
        advanceUntilIdle()
    }

    @Test
    fun `failed remote delete keeps the mapping`() = runTest {
        coEvery { idMappingDao.getByLocalIdAndProvider(1L, CloudService.FILE_STORAGE) } returns mapping
        coEvery { backend.deleteNote(mapping) } returns false

        process(RemoteOperation.Delete(note))

        coVerify(exactly = 0) { idMappingDao.deleteByLocalId(*anyLongVararg()) }
    }

    @Test
    fun `successful remote delete removes the mapping`() = runTest {
        coEvery { idMappingDao.getByLocalIdAndProvider(1L, CloudService.FILE_STORAGE) } returns mapping
        coEvery { backend.deleteNote(mapping) } returns true

        process(RemoteOperation.Delete(note))

        coVerify(exactly = 1) { idMappingDao.deleteByLocalId(1L) }
    }

    @Test
    fun `update without mapping creates the remote note`() = runTest {
        coEvery { idMappingDao.getByLocalIdAndProvider(1L, CloudService.FILE_STORAGE) } returns null
        coEvery { backend.createNote(note) } returns
            SyncNote(id = 0L, idStr = "uri1", content = "c", title = "T", lastModified = 11L)

        process(RemoteOperation.Update(note))

        coVerify(exactly = 1) { backend.createNote(note) }
        coVerify(exactly = 1) { idMappingDao.insert(any()) }
    }

    @Test
    fun `update with mapping updates the remote note`() = runTest {
        coEvery { idMappingDao.getByLocalIdAndProvider(1L, CloudService.FILE_STORAGE) } returns mapping
        coEvery { backend.updateNote(note, mapping) } returns mapping

        process(RemoteOperation.Update(note))

        coVerify(exactly = 1) { backend.updateNote(note, mapping) }
        coVerify(exactly = 0) { backend.createNote(any()) }
    }

    @Test
    fun `binned or local-only notes are never created by an update`() {
        assertTrue(updateNeedsCreate(note))
        assertFalse(updateNeedsCreate(note.copy(isDeleted = true)))
        assertFalse(updateNeedsCreate(note.copy(isLocalOnly = true)))
    }
}
