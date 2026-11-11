package org.qosp.notes.ui.theme

import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import com.google.android.material.color.MaterialColors
import org.qosp.notes.R

private val lightScheme = lightColorScheme(
    primary = primaryLight,
    onPrimary = onPrimaryLight,
    primaryContainer = primaryContainerLight,
    onPrimaryContainer = onPrimaryContainerLight,
    secondary = secondaryLight,
    onSecondary = onSecondaryLight,
    secondaryContainer = secondaryContainerLight,
    onSecondaryContainer = onSecondaryContainerLight,
    tertiary = tertiaryLight,
    onTertiary = onTertiaryLight,
    tertiaryContainer = tertiaryContainerLight,
    onTertiaryContainer = onTertiaryContainerLight,
    error = errorLight,
    onError = onErrorLight,
    errorContainer = errorContainerLight,
    onErrorContainer = onErrorContainerLight,
    background = backgroundLight,
    onBackground = onBackgroundLight,
    surface = surfaceLight,
    onSurface = onSurfaceLight,
    surfaceVariant = surfaceVariantLight,
    onSurfaceVariant = onSurfaceVariantLight,
    outline = outlineLight,
    outlineVariant = outlineVariantLight,
    scrim = scrimLight,
    inverseSurface = inverseSurfaceLight,
    inverseOnSurface = inverseOnSurfaceLight,
    inversePrimary = inversePrimaryLight,
    surfaceDim = surfaceDimLight,
    surfaceBright = surfaceBrightLight,
    surfaceContainerLowest = surfaceContainerLowestLight,
    surfaceContainerLow = surfaceContainerLowLight,
    surfaceContainer = surfaceContainerLight,
    surfaceContainerHigh = surfaceContainerHighLight,
    surfaceContainerHighest = surfaceContainerHighestLight,
)

private val darkScheme = darkColorScheme(
    primary = primaryDark,
    onPrimary = onPrimaryDark,
    primaryContainer = primaryContainerDark,
    onPrimaryContainer = onPrimaryContainerDark,
    secondary = secondaryDark,
    onSecondary = onSecondaryDark,
    secondaryContainer = secondaryContainerDark,
    onSecondaryContainer = onSecondaryContainerDark,
    tertiary = tertiaryDark,
    onTertiary = onTertiaryDark,
    tertiaryContainer = tertiaryContainerDark,
    onTertiaryContainer = onTertiaryContainerDark,
    error = errorDark,
    onError = onErrorDark,
    errorContainer = errorContainerDark,
    onErrorContainer = onErrorContainerDark,
    background = backgroundDark,
    onBackground = onBackgroundDark,
    surface = surfaceDark,
    onSurface = onSurfaceDark,
    surfaceVariant = surfaceVariantDark,
    onSurfaceVariant = onSurfaceVariantDark,
    outline = outlineDark,
    outlineVariant = outlineVariantDark,
    scrim = scrimDark,
    inverseSurface = inverseSurfaceDark,
    inverseOnSurface = inverseOnSurfaceDark,
    inversePrimary = inversePrimaryDark,
    surfaceDim = surfaceDimDark,
    surfaceBright = surfaceBrightDark,
    surfaceContainerLowest = surfaceContainerLowestDark,
    surfaceContainerLow = surfaceContainerLowDark,
    surfaceContainer = surfaceContainerDark,
    surfaceContainerHigh = surfaceContainerHighDark,
    surfaceContainerHighest = surfaceContainerHighestDark,
)

/**
 * Compose theme built from the current View theme (Material 3 palette chosen in settings, or dynamic colors
 * when the activity applied them), so Compose and View screens always match.
 */
@Composable
fun QuillpadTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable() () -> Unit
) {
    val context = LocalContext.current
    val fallback = if (darkTheme) darkScheme else lightScheme
    val colorScheme = colorSchemeFromViewTheme(context, fallback)

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content
    )
}

private fun colorSchemeFromViewTheme(context: Context, f: ColorScheme): ColorScheme {
    fun c(attr: Int, default: Color) = Color(MaterialColors.getColor(context, attr, default.toArgb()))
    return f.copy(
        primary = c(R.attr.colorPrimary, f.primary),
        onPrimary = c(R.attr.colorOnPrimary, f.onPrimary),
        primaryContainer = c(R.attr.colorPrimaryContainer, f.primaryContainer),
        onPrimaryContainer = c(R.attr.colorOnPrimaryContainer, f.onPrimaryContainer),
        inversePrimary = c(R.attr.colorPrimaryInverse, f.inversePrimary),
        secondary = c(R.attr.colorSecondary, f.secondary),
        onSecondary = c(R.attr.colorOnSecondary, f.onSecondary),
        secondaryContainer = c(R.attr.colorSecondaryContainer, f.secondaryContainer),
        onSecondaryContainer = c(R.attr.colorOnSecondaryContainer, f.onSecondaryContainer),
        tertiary = c(R.attr.colorTertiary, f.tertiary),
        onTertiary = c(R.attr.colorOnTertiary, f.onTertiary),
        tertiaryContainer = c(R.attr.colorTertiaryContainer, f.tertiaryContainer),
        onTertiaryContainer = c(R.attr.colorOnTertiaryContainer, f.onTertiaryContainer),
        error = c(R.attr.colorError, f.error),
        onError = c(R.attr.colorOnError, f.onError),
        errorContainer = c(R.attr.colorErrorContainer, f.errorContainer),
        onErrorContainer = c(R.attr.colorOnErrorContainer, f.onErrorContainer),
        background = c(android.R.attr.colorBackground, f.background),
        onBackground = c(R.attr.colorOnBackground, f.onBackground),
        surface = c(R.attr.colorSurface, f.surface),
        onSurface = c(R.attr.colorOnSurface, f.onSurface),
        surfaceVariant = c(R.attr.colorSurfaceVariant, f.surfaceVariant),
        onSurfaceVariant = c(R.attr.colorOnSurfaceVariant, f.onSurfaceVariant),
        inverseSurface = c(R.attr.colorSurfaceInverse, f.inverseSurface),
        inverseOnSurface = c(R.attr.colorOnSurfaceInverse, f.inverseOnSurface),
        outline = c(R.attr.colorOutline, f.outline),
        outlineVariant = c(R.attr.colorOutlineVariant, f.outlineVariant),
        surfaceDim = c(R.attr.colorSurfaceDim, f.surfaceDim),
        surfaceBright = c(R.attr.colorSurfaceBright, f.surfaceBright),
        surfaceContainerLowest = c(R.attr.colorSurfaceContainerLowest, f.surfaceContainerLowest),
        surfaceContainerLow = c(R.attr.colorSurfaceContainerLow, f.surfaceContainerLow),
        surfaceContainer = c(R.attr.colorSurfaceContainer, f.surfaceContainer),
        surfaceContainerHigh = c(R.attr.colorSurfaceContainerHigh, f.surfaceContainerHigh),
        surfaceContainerHighest = c(R.attr.colorSurfaceContainerHighest, f.surfaceContainerHighest),
    )
}
