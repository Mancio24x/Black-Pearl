package org.qosp.notes.tests

import android.view.View
import androidx.core.view.isVisible
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.R
import org.qosp.notes.preferences.AppLockSettings
import org.qosp.notes.ui.MainActivity
import org.qosp.notes.ui.lock.AppLock
import org.qosp.notes.ui.lock.PinHasher
import org.qosp.notes.ui.widget.NotesWidgetViewsFactory

/** App lock in the real app: the PIN screen covers the activity while locked and goes away with the right PIN. */
class AppLockScreenTest : KoinComponent {
    private val appLock: AppLock by inject()

    @After
    fun reset() {
        appLock.settings = AppLockSettings()
        appLock.unlock()
    }

    @Test
    fun pinScreenCoversTheAppUntilTheRightPin() {
        appLock.start(AppLockSettings(pinHash = PinHasher.hash("2580"), pinLength = 4, biometric = false))
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            fun overlayVisible(): Boolean {
                var visible = false
                scenario.onActivity { visible = it.findViewById<View>(R.id.app_lock_overlay).isVisible }
                return visible
            }
            assertTrue(overlayVisible())
            assertEquals(AppLock.PinResult.Wrong(4), appLock.tryPin("0000"))
            assertTrue(overlayVisible())
            assertEquals(AppLock.PinResult.Unlocked, appLock.tryPin("2580"))
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            assertFalse(overlayVisible())
        }
    }

    @Test
    fun widgetAndRemindersHideNotesWhileTheLockIsOn() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val widget = NotesWidgetViewsFactory(context)
        assertFalse(AppLock.hidesNotesOutsideTheApp())
        appLock.start(AppLockSettings(pinHash = PinHasher.hash("2580"), pinLength = 4))
        assertTrue(AppLock.hidesNotesOutsideTheApp())
        widget.onDataSetChanged()
        assertEquals(0, widget.count)
    }
}
