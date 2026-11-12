package org.qosp.notes.data.repo

import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.qosp.notes.data.dao.IdMappingDao
import org.qosp.notes.data.dao.NoteDao
import org.qosp.notes.data.dao.ReminderDao
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.AvailabilityStatus
import org.qosp.notes.data.sync.core.BackendProvider
import org.qosp.notes.data.sync.core.ISyncBackend
import org.qosp.notes.data.sync.core.NoteAction
import org.qosp.notes.data.sync.core.ProcessRemoteActions
import org.qosp.notes.data.sync.core.Success
import org.qosp.notes.data.sync.core.SyncNote
import org.qosp.notes.data.sync.core.SyncNotesResult
import org.qosp.notes.data.sync.core.SynchronizeNotes
import org.qosp.notes.di.SyncScope
import org.qosp.notes.preferences.CloudService
import org.qosp.notes.ui.utils.Toaster

/** Sync rules applied by NoteRepositoryImpl.syncNotes (applyLocalUpdates). */
class NoteRepositorySyncTest {
    private val noteDao = mockk<NoteDao>(relaxed = true)
    private val idMappingDao = mockk<IdMappingDao>(relaxed = true)
    private val reminderDao = mockk<ReminderDao>(relaxed = true)
    private val backend = mockk<ISyncBackend>()
    private val backendProvider = mockk<BackendProvider>()
    private val synchronizeNotes = mockk<SynchronizeNotes>()
    private val processRemoteActions = mockk<ProcessRemoteActions>(relaxed = true)
    private val toaster = mockk<Toaster>(relaxed = true)

    private val note = Note(id = 1L, title = "T", content = "c", modifiedDate = 10L)
    private val remote = SyncNote(id = 0L, idStr = "uri1", content = "c", title = "T", lastModified = 10L)

    @Before
    fun setup() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any<String>(), any<String>()) } returns 0
        every { Log.e(any(), any()) } returns 0
        every { Log.e(any(), any(), any()) } returns 0
        every { backend.type } returns CloudService.FILE_STORAGE
        coEvery { backend.isAvailable() } returns AvailabilityStatus.Available
        coEvery { backend.getAll() } returns listOf(remote)
        every { backendProvider.isSyncing } returns true
        every { backendProvider.syncProvider } returns MutableStateFlow(backend)
        coEvery { idMappingDao.getCountByCloudService(CloudService.FILE_STORAGE) } returns 1
        every { noteDao.getAll(any()) } returns flowOf(listOf(note))
    }

    @Test
    fun `note binned because its remote is gone loses its mapping and is not deleted remotely`() = runTest {
        coEvery { synchronizeNotes(any(), any(), any(), any()) } returns
            SyncNotesResult(localUpdates = listOf(NoteAction.Delete(note, remote)), remoteUpdates = emptyList())
        val repository = NoteRepositoryImpl(
            noteDao, idMappingDao, reminderDao, backendProvider, synchronizeNotes, processRemoteActions,
            SyncScope(this), toaster,
        )

        assertEquals(Success, repository.syncNotes())

        coVerify(exactly = 1) { noteDao.update(*varargAll { it.id == 1L && it.isDeleted }) }
        coVerify(exactly = 1) { idMappingDao.deleteByLocalId(1L) }
        verify(exactly = 0) { processRemoteActions(any(), any()) }
    }
}
