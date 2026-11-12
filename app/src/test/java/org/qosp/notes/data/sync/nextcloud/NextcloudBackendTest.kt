package org.qosp.notes.data.sync.nextcloud

import android.util.Base64
import android.util.Log
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.qosp.notes.data.model.IdMapping
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.sync.core.AvailabilityStatus
import org.qosp.notes.data.sync.nextcloud.model.NextcloudNotesCapabilities
import org.qosp.notes.preferences.CloudService
import kotlin.time.Duration.Companion.seconds
import kotlin.time.TestTimeSource

class NextcloudBackendTest {

    private val apiProvider = mockk<NextcloudAPIProvider>()
    private val api = mockk<NextcloudAPI>()
    private lateinit var config: NextcloudConfig
    private val timeSource = TestTimeSource()

    private lateinit var backend: NextcloudBackend

    @Before
    fun setup() {
        mockkStatic(Base64::class)
        every { Base64.encodeToString(any<ByteArray>(), any()) } returns "base64"

        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0

        config = NextcloudConfig("http://localhost", "user", "pass")
        backend = NextcloudBackend(apiProvider, config, timeSource)
        coEvery { apiProvider.getAPI() } returns api
    }

    @Test
    fun `isAvailable fetches from API on first call`() = runTest {
        val capabilities = NextcloudNotesCapabilities(listOf("1.0"), "1.0.0")
        coEvery {
            api.getNotesAPI(
                any(),
                any()
            )
        } returns emptyList() // Not used by getNotesCapabilities but good to have
        // getNotesCapabilities is an extension function, mocking it might be tricky if it's not handled by mockk properly
        // Actually, it calls getAllCapabilitiesAPI internally.
        coEvery { api.getAllCapabilitiesAPI(any(), any()) } returns mockk {
            every { ocs.data.capabilities.notes } returns capabilities
        }

        val status = backend.isAvailable()

        assertEquals(AvailabilityStatus.Available, status)
        coVerify(exactly = 1) { api.getAllCapabilitiesAPI(any(), any()) }
    }

    @Test
    fun `isAvailable returns cached value within expiration period`() = runTest {
        val capabilities = NextcloudNotesCapabilities(listOf("1.0"), "1.0.0")
        coEvery { api.getAllCapabilitiesAPI(any(), any()) } returns mockk {
            every { ocs.data.capabilities.notes } returns capabilities
        }

        // First call
        backend.isAvailable()

        // Advance time by 10 seconds (less than 30s expiration)
        timeSource += 10.seconds

        // Second call
        val status = backend.isAvailable()

        assertEquals(AvailabilityStatus.Available, status)
        coVerify(exactly = 1) { api.getAllCapabilitiesAPI(any(), any()) }
    }

    @Test
    fun `isAvailable refetches after expiration period`() = runTest {
        val capabilities = NextcloudNotesCapabilities(listOf("1.0"), "1.0.0")
        coEvery { api.getAllCapabilitiesAPI(any(), any()) } returns mockk {
            every { ocs.data.capabilities.notes } returns capabilities
        }

        // First call
        backend.isAvailable()

        // Advance time by 31 seconds (more than 30s expiration)
        timeSource += 31.seconds

        // Second call
        val status = backend.isAvailable()

        assertEquals(AvailabilityStatus.Available, status)
        coVerify(exactly = 2) { api.getAllCapabilitiesAPI(any(), any()) }
    }

    @Test
    fun `updateNote leaves the category out so the server keeps it`() = runTest {
        val sent = slot<NextcloudNote>()
        coEvery { api.updateNoteAPI(capture(sent), any(), any(), any()) } answers {
            sent.captured.copy(etag = "e2", category = "Work")
        }
        val mapping = IdMapping(
            localNoteId = 1L, remoteNoteId = 7L, provider = CloudService.NEXTCLOUD, extras = "e1",
            isDeletedLocally = false
        )

        val updated = backend.updateNote(Note(id = 1L, title = "T", content = "c"), mapping)

        assertNull(sent.captured.category)
        assertFalse(Json { ignoreUnknownKeys = true }.encodeToString(sent.captured).contains("category"))
        assertEquals("e2", updated.extras)
    }

    @Test
    fun `server category is still decoded`() {
        val json = Json { ignoreUnknownKeys = true }
        val note = json.decodeFromString<NextcloudNote>(
            """{"id":1,"content":"c","title":"t","category":"Work","favorite":false,"modified":5}"""
        )
        assertEquals("Work", note.category)
    }
}
