package org.qosp.notes.ui.theme

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.util.Log
import com.google.android.material.color.BlackPearlColorResources
import org.qosp.notes.preferences.CustomThemeColors

/**
 * "My theme" (Black Pearl): the M3 roles computed by [ThemeRoles.custom] from three colors replace, at runtime,
 * the color resources that Palette.Custom points to. Uses Material's resources loader (Android 11+).
 */
object CustomTheme {

    val isSupported: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun roles(colors: CustomThemeColors): Map<String, Int> = ThemeRoles.custom(colors.paper, colors.surface, colors.accent)

    fun isDark(colors: CustomThemeColors): Boolean = ThemeRoles.isDark(colors.paper)

    /**
     * Must run in onCreate before the content view is inflated, on a theme that already has Palette.Custom.
     * Only the color resources change: the theme keeps pointing to them (no Material You overlay).
     * @return false if not applied.
     */
    @SuppressLint("RestrictedApi")
    fun apply(context: Context, colors: CustomThemeColors): Boolean {
        if (!isSupported) return false
        val overrides = roles(colors).mapNotNull { (attr, color) ->
            CustomThemeResources.byAttribute[attr]?.let { it to color }
        }.toMap()
        return BlackPearlColorResources.override(context, overrides).also { applied ->
            if (!applied) Log.w("BlackPearlTheme", "My theme colors could not be applied")
        }
    }
}
