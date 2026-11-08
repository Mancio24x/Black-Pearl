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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import com.google.android.material.color.DynamicColors
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
            val (colorScheme, themeMode, darkThemeModeStyle) = withContext(Dispatchers.IO) {
                preferenceRepository
                    .getAll()
                    .map {
                        Triple(
                            it.colorScheme,
                            it.themeMode.mode,
                            it.darkThemeMode.styleResource
                        )
                    }
                    .first()
            }

            theme.applyStyle(colorScheme.styleResource, true)
            if (colorScheme == ColorScheme.SYSTEM) {
                // Material You: full M3 scheme from the wallpaper (Android 12+), no-op elsewhere
                DynamicColors.applyToActivityIfAvailable(this@BaseActivity)
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
