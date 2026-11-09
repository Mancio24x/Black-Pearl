package org.qosp.notes.ui.theme

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Build
import com.google.android.material.color.ColorResourcesOverride
import org.qosp.notes.preferences.CustomThemeColors

/**
 * "My theme" (Black Pearl): the M3 roles computed by [ThemeRoles.custom] from three colors replace, at runtime,
 * the color resources that Palette.Custom points to. Uses Material's resources loader (Android 11+).
 */
object CustomTheme {

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun roles(colors: CustomThemeColors): Map<String, Int> = ThemeRoles.custom(colors.paper, colors.surface, colors.accent)

    fun isDark(colors: CustomThemeColors): Boolean = ThemeRoles.isDark(colors.paper)

    /** Must run in onCreate before the content view is inflated. @return false if not applied. */
    @SuppressLint("RestrictedApi")
    fun apply(activity: Activity, colors: CustomThemeColors): Boolean {
        if (!isSupported) return false
        val overrides = roles(colors).mapNotNull { (attr, color) ->
            CustomThemeResources.byAttribute[attr]?.let { it to color }
        }.toMap()
        return ColorResourcesOverride.getInstance()?.applyIfPossible(activity, overrides) == true
    }
}
