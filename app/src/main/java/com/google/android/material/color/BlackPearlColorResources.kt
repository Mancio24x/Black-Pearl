package com.google.android.material.color

import android.content.Context
import androidx.annotation.RequiresApi

/**
 * Black Pearl "My theme": Material's runtime color-resource loader WITHOUT the theme overlay that
 * [ColorResourcesOverride.applyIfPossible] adds. That overlay (ThemeOverlay.Material3.PersonalizedColors) points every
 * color role and text color to Material You's wallpaper colors, hiding the colors the user chose.
 * Lives in Material's package because ResourcesLoaderUtils is package-private.
 */
object BlackPearlColorResources {
    /** Android 11+: replaces the given color resources (id → ARGB) for [context]'s resources. */
    @RequiresApi(30)
    fun override(context: Context, colors: Map<Int, Int>): Boolean =
        ResourcesLoaderUtils.addResourcesLoaderToContext(context, colors)
}
