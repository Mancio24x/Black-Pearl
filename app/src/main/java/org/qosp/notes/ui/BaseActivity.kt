package org.qosp.notes.ui

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
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.DynamicColorsOptions
import org.qosp.notes.preferences.ColorScheme
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.preferences.ThemeMode
import org.qosp.notes.ui.utils.Toaster
import org.qosp.notes.ui.utils.collect

open class BaseActivity : AppCompatActivity() {
    val preferenceRepository: PreferenceRepository by inject()
    private val toaster: Toaster by inject()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        runBlocking {
            val (prefs, customSeed) = withContext(Dispatchers.IO) {
                preferenceRepository.getAll().first() to preferenceRepository.getCustomSeedColor().first()
            }
            val colorScheme = prefs.colorScheme
            val themeMode = prefs.themeMode.mode
            val darkThemeModeStyle = prefs.darkThemeMode.styleResource

            theme.applyStyle(colorScheme.styleResource, true)
            theme.applyStyle(prefs.cardShape.styleResource, true)
            theme.applyStyle(prefs.noteFont.styleResource, true)
            when (colorScheme) {
                // Material You from the wallpaper (Android 12+), no-op elsewhere
                ColorScheme.SYSTEM -> DynamicColors.applyToActivityIfAvailable(this@BaseActivity)
                // Full M3 scheme generated from the personal seed color (Android 12+)
                ColorScheme.CUSTOM -> DynamicColors.applyToActivityIfAvailable(
                    this@BaseActivity,
                    DynamicColorsOptions.Builder().setContentBasedSource(customSeed).build()
                )
                else -> Unit
            }

            if (themeMode != AppCompatDelegate.getDefaultNightMode()) {
                AppCompatDelegate.setDefaultNightMode(themeMode)
            }

            val isAutoDark =
                themeMode == ThemeMode.SYSTEM.mode && (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

            // Check which theme should be used (light, dark, black) and set navbar color accordingly
            // if dark
            if (themeMode == ThemeMode.DARK.mode || isAutoDark) {
                // if black
                darkThemeModeStyle?.let {
                    theme.applyStyle(darkThemeModeStyle, true)
                }
            }
        }

        // Observe toast messages with lifecycle awareness
        toaster.messages.collect(this) { (message, duration) ->
            Toast.makeText(this@BaseActivity, message, duration).show()
        }
    }
}
