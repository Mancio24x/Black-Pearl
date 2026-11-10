package org.qosp.notes.ui.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.context.GlobalContext
import org.qosp.notes.preferences.AppLockSettings

/**
 * App lock state (Black Pearl), one for the whole app. The app is locked when it starts and when it comes back
 * after [AppLockSettings.timeoutMinutes] in the background (camera, file picker and share sheet also send it to the
 * background: the default of one minute keeps those from asking for the PIN again).
 * Pure logic: the screens are in LockScreen / BaseActivity, the settings in PreferenceRepository.
 */
class AppLock(private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {

    sealed interface PinResult {
        data object Unlocked : PinResult
        data class Wrong(val attemptsLeft: Int) : PinResult
        data class Blocked(val seconds: Int) : PinResult
    }

    var settings = AppLockSettings()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    /** True until the fingerprint prompt has been offered once for the current lock. */
    var biometricPending = false

    private var backgroundSince: Long? = null
    private var failures = 0
    private var blockedUntil = 0L

    /** Called once when the app process starts. */
    fun start(settings: AppLockSettings) {
        this.settings = settings
        if (settings.enabled) lock()
    }

    fun onAppBackground() {
        backgroundSince = clock()
    }

    fun onAppForeground() {
        val since = backgroundSince ?: return
        backgroundSince = null
        if (settings.enabled && clock() - since >= settings.timeoutMinutes * 60_000L) lock()
    }

    fun lock() {
        _locked.value = true
        biometricPending = true
    }

    fun tryPin(pin: String): PinResult {
        blockedSeconds().takeIf { it > 0 }?.let { return PinResult.Blocked(it) }
        if (PinHasher.verify(pin, settings.pinHash)) {
            unlock()
            return PinResult.Unlocked
        }
        failures++
        if (failures % MAX_ATTEMPTS == 0) {
            blockedUntil = clock() + BLOCK_SECONDS * 1000L
            return PinResult.Blocked(BLOCK_SECONDS)
        }
        return PinResult.Wrong(MAX_ATTEMPTS - failures % MAX_ATTEMPTS)
    }

    /** Seconds to wait after too many wrong PINs (0 = can try). */
    fun blockedSeconds(): Int = ((blockedUntil - clock() + 999) / 1000).coerceAtLeast(0).toInt()

    /** Fingerprint, face or the phone's own screen lock was accepted by Android. */
    fun unlock() {
        failures = 0
        blockedUntil = 0
        biometricPending = false
        _locked.value = false
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val BLOCK_SECONDS = 30

        /**
         * With the lock on, places outside the app (home-screen widget, reminder notifications) do not show what
         * the notes say. Read from anywhere in the app process; false if the app is not set up (some tests).
         */
        fun hidesNotesOutsideTheApp(): Boolean =
            runCatching { GlobalContext.get().get<AppLock>().settings.enabled }.getOrDefault(false)
    }
}
