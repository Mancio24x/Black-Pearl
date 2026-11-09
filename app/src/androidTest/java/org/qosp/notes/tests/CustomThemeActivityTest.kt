package org.qosp.notes.tests

import android.app.Activity
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import org.qosp.notes.R
import org.qosp.notes.preferences.ColorScheme
import org.qosp.notes.preferences.CustomThemeColors
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.MainActivity

/** "My theme" in the real app: when the app starts with it, and after "Apply my theme" (activity recreated). */
class CustomThemeActivityTest : KoinComponent {
    private val preferences: PreferenceRepository by inject()

    @After
    fun reset() {
        runBlocking {
            preferences.set(ColorScheme.BLACK_PEARL)
            preferences.setCustomTheme(CustomThemeColors.DEFAULT)
        }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }

    @Test
    fun lightThemeWhenTheAppStartsAndAfterRecreate() =
        check(CustomThemeColors(0xFFF4ECDD.toInt(), 0xFFFFFBF3.toInt(), 0xFF8E2F25.toInt()))

    @Test
    fun darkThemeWhenTheAppStartsAndAfterRecreate() =
        check(CustomThemeColors(0xFF0E1720.toInt(), 0xFF18232E.toInt(), 0xFFE6C068.toInt()))

    private fun check(colors: CustomThemeColors) {
        runBlocking {
            preferences.setCustomTheme(colors)
            preferences.set(ColorScheme.CUSTOM)
        }
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            scenario.onActivity { assertColors("start", it, colors) }
            scenario.recreate()
            scenario.onActivity { assertColors("recreate", it, colors) }
        }
    }

    private fun assertColors(step: String, activity: Activity, colors: CustomThemeColors) {
        fun hex(c: Int) = "#%08X".format(c)
        assertEquals("$step: accent", hex(colors.accent), hex(MaterialColors.getColor(activity, R.attr.colorPrimary, 0)))
        assertEquals("$step: background", hex(colors.paper), hex(MaterialColors.getColor(activity, android.R.attr.colorBackground, 0)))
        assertEquals("$step: cards", hex(colors.surface), hex(MaterialColors.getColor(activity, R.attr.colorSurfaceContainerLow, 0)))
    }
}
