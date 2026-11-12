package org.qosp.notes.ui

import android.os.Build
import android.view.KeyEvent
import android.view.ViewGroup
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import org.qosp.notes.R
import org.qosp.notes.ui.lock.AppLock
import org.qosp.notes.ui.lock.LockScreen
import org.qosp.notes.ui.theme.QuillpadTheme
import org.qosp.notes.ui.utils.hideKeyboard
import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.view.WindowCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.koin.android.ext.android.inject
import com.google.android.material.color.DynamicColors
import org.qosp.notes.preferences.AppPreferences
import org.qosp.notes.preferences.ColorScheme
import org.qosp.notes.preferences.CustomThemeColors
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.ThemeMode
import org.qosp.notes.ui.theme.CustomTheme
import org.qosp.notes.ui.utils.Toaster
import org.qosp.notes.ui.utils.collect

open class BaseActivity : AppCompatActivity() {
    val preferenceRepository: PreferenceRepository by inject()
    private val toaster: Toaster by inject()

    private var startPreferences: Pair<AppPreferences, CustomThemeColors>? = null
    private val appLock: AppLock by inject()

    private fun readPreferences() = runBlocking(Dispatchers.IO) {
        preferenceRepository.getAll().first() to preferenceRepository.getCustomTheme().first()
    }

    // Night mode is decided here, before AppCompat sets up the activity's configuration. Changing it later (in
    // onCreate) makes AppCompat re-apply the manifest theme over the chosen palette: a light "My theme" or "Abyss"
    // opened on a phone in the other mode came up with the Black Pearl colors.
    override fun attachBaseContext(newBase: Context) {
        val (prefs, custom) = readPreferences().also { startPreferences = it }
        val mode = nightMode(prefs, custom)
        if (mode != AppCompatDelegate.getDefaultNightMode()) {
            AppCompatDelegate.setDefaultNightMode(mode)
        }
        super.attachBaseContext(newBase)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()

        // Theme first, BEFORE super.onCreate (like Material's DynamicColors): palette, shapes, fonts and
        // "My theme" color resources must be in place before AppCompat sets up the activity.
        val (prefs, custom) = startPreferences ?: readPreferences()
        val colorScheme = supportedScheme(prefs)

        theme.applyStyle(colorScheme.styleResource, true)
        theme.applyStyle(prefs.cardShape.styleResource, true)
        theme.applyStyle(prefs.noteFont.styleResource, true)
        when (colorScheme) {
            // Material You from the wallpaper (Android 12+)
            ColorScheme.SYSTEM -> DynamicColors.applyToActivityIfAvailable(this)
            // "My theme": three colors chosen by the user (Android 11+)
            ColorScheme.CUSTOM -> if (!CustomTheme.apply(this, custom)) {
                theme.applyStyle(ColorScheme.BLACK_PEARL.styleResource, true)
            }
            else -> Unit
        }

        val themeMode = nightMode(prefs, custom)
        val isNight = themeMode == AppCompatDelegate.MODE_NIGHT_YES ||
            (themeMode == ThemeMode.SYSTEM.mode && (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES)

        // AMOLED black on top of a dark theme (not on "My theme", whose colors are the user's)
        if (isNight && colorScheme != ColorScheme.CUSTOM) {
            prefs.darkThemeMode.styleResource?.let { theme.applyStyle(it, true) }
        }

        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Observe toast messages with lifecycle awareness
        toaster.messages.collect(this) { (message, duration) ->
            Toast.makeText(this@BaseActivity, message, duration).show()
        }
    }

    // ---------- App lock (Black Pearl): a PIN screen over every activity while the app is locked ----------

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        val overlay = ComposeView(this).apply {
            id = R.id.app_lock_overlay
            isVisible = false
            isClickable = true // nothing below can be touched
            setContent {
                QuillpadTheme {
                    // composed again at every lock, so it always uses the current PIN length and settings
                    val locked by appLock.locked.collectAsState()
                    if (locked) {
                        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
                            LockScreen(appLock, onBiometric = if (canUseBiometric()) ::showBiometricPrompt else null)
                        }
                    }
                }
            }
        }
        addContentView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // no preview of the notes in screenshots or the recent apps while the lock is on; follows the setting at once
        appLock.secure.collect(this) { applySecureScreen(it) }
        appLock.locked.collect(this) { locked ->
            overlay.isVisible = locked
            if (locked) {
                currentFocus?.hideKeyboard()
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) offerBiometric()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applySecureScreen(appLock.secure.value)
        if (appLock.locked.value) offerBiometric()
    }

    private fun applySecureScreen(secure: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) setRecentsScreenshotEnabled(!secure)
        // before Android 13 the only way to hide the recents preview is FLAG_SECURE (it also blocks screenshots)
        if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    // Back does not close the lock screen: it leaves the app
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (appLock.locked.value && event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP) moveTaskToBack(true)
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    private fun canUseBiometric() = appLock.settings.biometric &&
        BiometricManager.from(this).canAuthenticate(LOCK_AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS

    /** Shows the fingerprint prompt once per lock; later the user can ask for it with the key on the pad. */
    private fun offerBiometric() {
        if (!appLock.biometricPending || !canUseBiometric()) return
        appLock.biometricPending = false
        showBiometricPrompt()
    }

    private fun showBiometricPrompt() {
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = appLock.unlock()
        }
        runCatching {
            BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(
                BiometricPrompt.PromptInfo.Builder()
                    .setTitle(getString(R.string.lock_title))
                    .setAllowedAuthenticators(LOCK_AUTHENTICATORS)
                    .build()
            )
        }
    }

    private fun supportedScheme(prefs: AppPreferences) =
        prefs.colorScheme.takeIf { it.isSupported() } ?: ColorScheme.BLACK_PEARL

    /** Designed dark themes and a dark "My theme" are always dark; a light "My theme" is always light. */
    private fun nightMode(prefs: AppPreferences, custom: CustomThemeColors): Int {
        val colorScheme = supportedScheme(prefs)
        return when {
            colorScheme.alwaysDark -> AppCompatDelegate.MODE_NIGHT_YES
            colorScheme == ColorScheme.CUSTOM ->
                if (CustomTheme.isDark(custom)) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
            else -> prefs.themeMode.mode
        }
    }

    companion object {
        /** Fingerprint or face, with the phone's own screen lock as a fallback (so a forgotten PIN is not fatal). */
        const val LOCK_AUTHENTICATORS = BiometricManager.Authenticators.BIOMETRIC_WEAK or
            BiometricManager.Authenticators.DEVICE_CREDENTIAL
    }
}
