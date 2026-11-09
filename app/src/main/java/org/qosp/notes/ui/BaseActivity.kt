package org.qosp.notes.ui

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
}
