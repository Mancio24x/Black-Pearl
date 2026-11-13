package org.qosp.notes.components

import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.qosp.notes.App
import org.qosp.notes.data.repo.NoteRepository
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * The periodic clean-up removes media no note uses, but not a file that was just written: it may belong to a photo
 * being taken, a recording in progress or a backup being restored, which are added to their note a moment later.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MediaStorageCleanupTest {
    @Test
    fun unusedOldFilesAreDeletedAndFreshOnesAreKept() = runTest {
        val context = RuntimeEnvironment.getApplication()
        val notes = mockk<NoteRepository> { every { getAll(any()) } returns flowOf(emptyList()) }
        val dir = File(context.filesDir, App.MEDIA_FOLDER).apply { mkdirs() }
        val now = System.currentTimeMillis()
        val old = File(dir, "old.jpg").apply { writeText("x"); setLastModified(now - 2 * MediaStorageManager.RECENT_FILE_MILLIS) }
        val fresh = File(dir, "fresh.jpg").apply { writeText("x") }

        MediaStorageManager(context, notes, App.MEDIA_FOLDER).cleanUpStorage(now = now)

        assertFalse("a file no note uses is removed", old.exists())
        assertTrue("a file written a moment ago is kept", fresh.exists())
    }
}
