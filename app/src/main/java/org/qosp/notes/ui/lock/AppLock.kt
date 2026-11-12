package org.qosp.notes.ui.lock

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.koin.core.context.GlobalContext
import org.qosp.notes.preferences.AppLockSettings
import org.qosp.notes.preferences.AppLockState
import org.qosp.notes.preferences.UNREADABLE_PIN_HASH

/**
 * App lock state (Black Pearl), one for the whole app. The app is locked when it starts and when it comes back
 * after [AppLockSettings.timeoutMinutes] in the background (camera, file picker and share sheet also send it to the
 * background: the default of one minute keeps those from asking for the PIN again).
 * Pure logic: the screens are in LockScreen / BaseActivity, the settings in PreferenceRepository.
 */
class AppLock(
    /** Clock for the PIN wait. It must survive a restart of the app, so production passes the wall clock. */
    wallClock: (() -> Long)? = null,
    private val clock: () -> Long = { android.os.SystemClock.elapsedRealtime() },
) {
    private val wall: () -> Long = wallClock ?: clock

    /** Called when the wrong-PIN counter changes, so it can be saved (killing the app must not reset it). */
    var onAttemptsChanged: ((failures: Int, blockedUntil: Long) -> Unit)? = null

    /**
     * Reads the stored lock settings again; null while they still cannot be read. Used only after the settings
     * could not be read at start, so a passing storage error heals without restarting the app.
     */
    var reloadSettings: (() -> AppLockState?)? = null

    /** True while the settings read at start were unreadable and have not been read since. */
    private var settingsUnreadable = false

    sealed interface PinResult {
        data object Unlocked : PinResult
        data class Wrong(val attemptsLeft: Int) : PinResult
        data class Blocked(val seconds: Int) : PinResult
    }

    var settings = AppLockSettings()
        set(value) {
            field = value
            _secure.value = value.enabled
        }

    private val _secure = MutableStateFlow(false)

    /** True while the app lock is on: the screen must not appear in screenshots or the recent apps. */
    val secure: StateFlow<Boolean> = _secure

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked

    /** True until the fingerprint prompt has been offered once for the current lock. */
    var biometricPending = false

    private var backgroundSince: Long? = null
    private var failures = 0
    private var blockedUntil = 0L

    /**
     * Called once when the app process starts. [deviceCanVerifyOwner]: fingerprint, face or the phone's screen lock
     * is set up, so Android can still open the app when the PIN cannot be read.
     */
    fun start(
        settings: AppLockSettings,
        failures: Int = 0,
        blockedUntil: Long = 0L,
        deviceCanVerifyOwner: Boolean = true,
    ) {
        settingsUnreadable = settings.pinHash == UNREADABLE_PIN_HASH
        this.settings = startSettings(settings, deviceCanVerifyOwner)
        this.failures = failures.coerceAtLeast(0)
        // a clock moved back must not make the wait longer than it can ever be
        this.blockedUntil = blockedUntil.coerceAtMost(wall() + BLOCK_SECONDS * 1000L)
        if (this.settings.enabled) lock() // the settings in use, not the parameter
    }

    fun onAppBackground() {
        backgroundSince = clock()
    }

    fun onAppForeground() {
        retryUnreadableSettings()
        val since = backgroundSince ?: return
        backgroundSince = null
        if (settings.enabled && clock() - since >= settings.timeoutMinutes * 60_000L) lock()
    }

    fun lock() {
        _locked.value = true
        biometricPending = true
    }

    fun tryPin(pin: String): PinResult = check(pin, unlockOnSuccess = true)

    /**
     * Checks the PIN without touching the lock (the "current PIN" step in Settings). Shares the attempt counter
     * with [tryPin], so Settings is not a way around the limit.
     */
    fun verifyPin(pin: String): PinResult = check(pin, unlockOnSuccess = false)

    private fun check(pin: String, unlockOnSuccess: Boolean): PinResult {
        retryUnreadableSettings()
        blockedSeconds().takeIf { it > 0 }?.let { return PinResult.Blocked(it) }
        if (PinHasher.verify(pin, settings.pinHash)) {
            if (unlockOnSuccess) unlock() else resetAttempts()
            return PinResult.Unlocked
        }
        failures++
        if (failures % MAX_ATTEMPTS == 0) {
            blockedUntil = wall() + BLOCK_SECONDS * 1000L
            onAttemptsChanged?.invoke(failures, blockedUntil)
            return PinResult.Blocked(BLOCK_SECONDS)
        }
        onAttemptsChanged?.invoke(failures, blockedUntil)
        return PinResult.Wrong(MAX_ATTEMPTS - failures % MAX_ATTEMPTS)
    }

    /** After an unreadable start, tries to read the real settings again; when it works they replace the stand-in. */
    private fun retryUnreadableSettings() {
        if (!settingsUnreadable) return
        val state = runCatching { reloadSettings?.invoke() }.getOrNull() ?: return
        if (state.settings.pinHash == UNREADABLE_PIN_HASH) return
        settingsUnreadable = false
        settings = state.settings
        failures = state.failures.coerceAtLeast(0)
        blockedUntil = state.blockedUntil.coerceAtMost(wall() + BLOCK_SECONDS * 1000L)
        if (!settings.enabled) unlock() // no PIN was ever set: nothing to lock
    }

    /** Seconds to wait after too many wrong PINs (0 = can try). */
    fun blockedSeconds(): Int = ((blockedUntil - wall() + 999) / 1000).coerceAtLeast(0).toInt()

    private fun resetAttempts() {
        if (failures == 0 && blockedUntil == 0L) return
        failures = 0
        blockedUntil = 0
        onAttemptsChanged?.invoke(0, 0L)
    }

    /** Fingerprint, face or the phone's own screen lock was accepted by Android. */
    fun unlock() {
        resetAttempts()
        biometricPending = false
        _locked.value = false
    }

    companion object {
        const val MAX_ATTEMPTS = 5
        const val BLOCK_SECONDS = 30

        /**
         * Settings to start with. Unreadable settings keep the app locked only when Android can still verify the
         * owner (fingerprint, face, screen lock); otherwise nothing could ever open the app, and the user's only way
         * out would be "Clear data", which deletes the notes. So in that case the app starts unlocked.
         */
        fun startSettings(loaded: AppLockSettings, deviceCanVerifyOwner: Boolean): AppLockSettings =
            if (loaded.pinHash == UNREADABLE_PIN_HASH && !deviceCanVerifyOwner) AppLockSettings() else loaded

        /**
         * With the lock on, places outside the app (home-screen widget, reminder notifications) do not show what
         * the notes say. Read from anywhere in the app process; false if the app is not set up (some tests).
         */
        fun hidesNotesOutsideTheApp(): Boolean =
            runCatching { GlobalContext.get().get<AppLock>().settings.enabled }.getOrDefault(false)
    }
}
