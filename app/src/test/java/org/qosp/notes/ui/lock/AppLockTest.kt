package org.qosp.notes.ui.lock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.preferences.AppLockSettings

class AppLockTest {
    private var now = 1_000_000L
    private val pin = "2580"
    private fun settings(timeout: Int = 1) = AppLockSettings(pinHash = PinHasher.hash(pin), pinLength = 4, timeoutMinutes = timeout)
    private fun lock(settings: AppLockSettings = settings()) = AppLock { now }.apply { start(settings) }
    private fun minutes(n: Double) { now += (n * 60_000).toLong() }

    @Test
    fun pinIsStoredAsASaltedHash() {
        val a = PinHasher.hash("123456")
        val b = PinHasher.hash("123456")
        assertFalse(a.contains("123456"))
        assertNotEquals(a, b) // random salt
        assertTrue(PinHasher.verify("123456", a))
        assertTrue(PinHasher.verify("123456", b))
        assertFalse(PinHasher.verify("123457", a))
        assertFalse(PinHasher.verify("123456", null))
        assertFalse(PinHasher.verify("123456", "garbage"))
    }

    @Test
    fun onlyFourToSixDigitsAreAccepted() {
        for (ok in listOf("1234", "12345", "123456")) assertTrue(ok, PinHasher.isValid(ok))
        for (bad in listOf("", "123", "1234567", "12a4", "12 34")) assertFalse(bad, PinHasher.isValid(bad))
    }

    @Test
    fun lockedAtStartOnlyWhenAPinIsSet() {
        assertTrue(lock().locked.value)
        assertFalse(lock(AppLockSettings()).locked.value)
    }

    @Test
    fun rightPinUnlocksWrongPinDoesNot() {
        val lock = lock()
        assertEquals(AppLock.PinResult.Wrong(4), lock.tryPin("0000"))
        assertTrue(lock.locked.value)
        assertEquals(AppLock.PinResult.Unlocked, lock.tryPin(pin))
        assertFalse(lock.locked.value)
    }

    @Test
    fun locksAgainOnlyAfterTheTimeInTheBackground() {
        val lock = lock(settings(timeout = 1))
        lock.unlock()
        lock.onAppBackground(); minutes(0.5); lock.onAppForeground() // e.g. taking a photo
        assertFalse(lock.locked.value)
        lock.onAppBackground(); minutes(1.0); lock.onAppForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun timeoutZeroLocksAtOnce() {
        val lock = lock(settings(timeout = 0))
        lock.unlock()
        lock.onAppBackground(); lock.onAppForeground()
        assertTrue(lock.locked.value)
    }

    @Test
    fun withoutAPinTheAppNeverLocks() {
        val lock = lock(AppLockSettings())
        lock.onAppBackground(); minutes(60.0); lock.onAppForeground()
        assertFalse(lock.locked.value)
    }

    @Test
    fun fiveWrongPinsMeanAThirtySecondWait() {
        val lock = lock()
        repeat(4) { assertTrue(lock.tryPin("0000") is AppLock.PinResult.Wrong) }
        assertEquals(AppLock.PinResult.Blocked(30), lock.tryPin("0000"))
        // even the right PIN has to wait
        assertEquals(AppLock.PinResult.Blocked(30), lock.tryPin(pin))
        now += 29_500
        assertEquals(1, lock.blockedSeconds())
        now += 600
        assertEquals(0, lock.blockedSeconds())
        assertEquals(AppLock.PinResult.Unlocked, lock.tryPin(pin))
    }

    @Test
    fun wrongPinCounterSurvivesARestart() {
        var saved = 0 to 0L
        val first = lock().apply { onAttemptsChanged = { f, b -> saved = f to b } }
        repeat(5) { first.tryPin("0000") }
        assertEquals(5 to now + 30_000L, saved)
        // the process is killed and started again: the wait is still there
        val second = AppLock { now }.apply { start(settings(), saved.first, saved.second) }
        assertEquals(AppLock.PinResult.Blocked(30), second.tryPin(pin))
        now += 31_000
        assertEquals(AppLock.PinResult.Unlocked, second.tryPin(pin))
    }

    @Test
    fun savedFailuresKeepCountingAfterARestart() {
        val lock = AppLock { now }.apply { start(settings(), failures = 4, blockedUntil = 0L) }
        assertEquals(AppLock.PinResult.Blocked(30), lock.tryPin("0000"))
    }

    @Test
    fun aWaitFarInTheFutureIsCutToThirtySeconds() {
        val lock = AppLock { now }.apply { start(settings(), failures = 5, blockedUntil = now + 10_000_000L) }
        assertEquals(30, lock.blockedSeconds())
    }

    @Test
    fun verifyPinSharesTheLimitAndDoesNotChangeTheLock() {
        val lock = lock()
        lock.unlock()
        repeat(4) { assertTrue(lock.verifyPin("0000") is AppLock.PinResult.Wrong) }
        assertEquals(AppLock.PinResult.Blocked(30), lock.verifyPin("0000"))
        assertEquals(AppLock.PinResult.Blocked(30), lock.verifyPin(pin))
        now += 31_000
        assertEquals(AppLock.PinResult.Unlocked, lock.verifyPin(pin))
        assertFalse(lock.locked.value)
    }

    @Test
    fun secureFollowsTheSettingAtOnce() {
        val lock = AppLock { now }
        assertFalse(lock.secure.value)
        lock.settings = settings()
        assertTrue(lock.secure.value)
        lock.settings = AppLockSettings()
        assertFalse(lock.secure.value)
    }

    @Test
    fun unreadableSettingsStartLockedAndMatchNoPin() {
        val lock = lock(AppLockSettings(pinHash = org.qosp.notes.preferences.UNREADABLE_PIN_HASH))
        assertTrue(lock.locked.value)
        assertTrue(lock.tryPin(pin) is AppLock.PinResult.Wrong)
    }
}
