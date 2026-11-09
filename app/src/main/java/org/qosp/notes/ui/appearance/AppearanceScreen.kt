package org.qosp.notes.ui.appearance

import android.content.Context
import android.graphics.Color as AndroidColor
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.google.android.material.color.DynamicColors
import com.google.android.material.color.MaterialColors
import org.qosp.notes.R
import org.qosp.notes.preferences.AppPreferences
import org.qosp.notes.preferences.CardShape
import org.qosp.notes.preferences.ColorScheme
import org.qosp.notes.preferences.CustomThemeColors
import org.qosp.notes.preferences.DarkThemeMode
import org.qosp.notes.preferences.FabSize
import org.qosp.notes.preferences.NoteFont
import org.qosp.notes.preferences.ThemeGroup
import org.qosp.notes.preferences.ThemeMode
import org.qosp.notes.ui.theme.CustomTheme

/** Colors of a theme used for its preview card. */
data class ThemeSwatch(val paper: Color, val surface: Color, val ink: Color, val muted: Color, val accent: Color)

/** Reads a palette's colors from its generated style, for the current light/dark configuration. */
fun swatchOf(context: Context, scheme: ColorScheme, custom: CustomThemeColors): ThemeSwatch {
    if (scheme == ColorScheme.CUSTOM) {
        val r = CustomTheme.roles(custom)
        fun c(name: String) = Color(r.getValue(name))
        return ThemeSwatch(c("android:colorBackground"), c("colorSurfaceContainerLow"), c("colorOnSurface"), c("colorOnSurfaceVariant"), c("colorPrimary"))
    }
    val themed: Context = if (scheme == ColorScheme.SYSTEM) {
        DynamicColors.wrapContextIfAvailable(context)
    } else {
        ContextThemeWrapper(context, R.style.AppTheme).apply { theme.applyStyle(scheme.styleResource, true) }
    }
    fun c(attr: Int) = Color(MaterialColors.getColor(themed, attr, AndroidColor.GRAY))
    return ThemeSwatch(
        c(android.R.attr.colorBackground), c(R.attr.colorSurfaceContainerLow), c(R.attr.colorOnSurface),
        c(R.attr.colorOnSurfaceVariant), c(R.attr.colorPrimary),
    )
}

@Composable
fun AppearanceScreen(
    prefs: AppPreferences,
    custom: CustomThemeColors,
    onThemeMode: (ThemeMode) -> Unit,
    onAmoled: (Boolean) -> Unit,
    onScheme: (ColorScheme) -> Unit,
    onOpenCustom: () -> Unit,
    onShape: (CardShape) -> Unit,
    onFont: (NoteFont) -> Unit,
    onFab: (FabSize) -> Unit,
) {
    val context = LocalContext.current
    val uiMode = LocalConfiguration.current.uiMode // previews follow light/dark changes
    val scheme = MaterialTheme.colorScheme
    val forcedMode = prefs.colorScheme.alwaysDark || (prefs.colorScheme == ColorScheme.CUSTOM && CustomTheme.isSupported)
    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        SectionTitle(stringResource(R.string.appearance_mode))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
            modes.forEachIndexed { i, mode ->
                SegmentedButton(
                    selected = prefs.themeMode == mode,
                    onClick = { onThemeMode(mode) },
                    enabled = !forcedMode,
                    shape = SegmentedButtonDefaults.itemShape(i, modes.size),
                ) { Text(stringResource(mode.nameResource), maxLines = 1) }
            }
        }
        if (forcedMode) {
            Text(stringResource(R.string.appearance_always_dark).takeIf { prefs.colorScheme.alwaysDark }
                ?: stringResource(R.string.theme_desc_custom), style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant)
        }
        Row(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(scheme.surfaceContainerLow).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(stringResource(R.string.preferences_theme_dark_mode_black), Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(checked = prefs.darkThemeMode == DarkThemeMode.BLACK, onCheckedChange = onAmoled)
        }

        if (CustomTheme.isSupported) {
            CustomThemeCard(custom, prefs.colorScheme == ColorScheme.CUSTOM, onOpenCustom)
        }

        val groups = listOf(ThemeGroup.DESIGNED, ThemeGroup.DARK, ThemeGroup.SPECIAL, ThemeGroup.VIVID)
        groups.forEach { group ->
            val items = ColorScheme.entries.filter { it.group == group && it.isSupported() && it != ColorScheme.CUSTOM }
            if (items.isEmpty()) return@forEach
            SectionTitle(stringResource(group.nameResource))
            items.chunked(2).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    row.forEach { item ->
                        val swatch = remember(item, uiMode) { swatchOf(context, item, custom) }
                        ThemeCard(item, swatch, prefs.colorScheme == item, Modifier.weight(1f)) { onScheme(item) }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }

        SectionTitle(stringResource(R.string.appearance_shape_font))
        ChoiceRow(stringResource(R.string.preferences_card_shape), CardShape.entries, prefs.cardShape, onShape)
        ChoiceRow(stringResource(R.string.preferences_note_font), NoteFont.entries, prefs.noteFont, onFont)
        ChoiceRow(stringResource(R.string.preferences_fab_size), FabSize.entries, prefs.fabSize, onFab)
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(), Modifier.padding(start = 4.dp, top = 8.dp),
        style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<T>, selected: T, onPick: (T) -> Unit) where T : Enum<T>, T : org.qosp.notes.preferences.HasNameResource {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                FilterChip(
                    selected = option == selected,
                    onClick = { onPick(option) },
                    label = { Text(stringResource(option.nameResource), maxLines = 1) },
                    shape = RoundedCornerShape(50),
                )
            }
        }
    }
}

/** Ready-made theme: a small picture of the app in its colors, name and description. */
@Composable
private fun ThemeCard(item: ColorScheme, s: ThemeSwatch, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val name = stringResource(item.nameResource)
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { contentDescription = name; this.selected = selected; role = Role.RadioButton },
        shape = RoundedCornerShape(24.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) scheme.primary else scheme.outlineVariant),
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(18.dp)).background(s.paper)) {
                Box(Modifier.padding(start = 10.dp, top = 10.dp, end = 34.dp).fillMaxWidth().height(36.dp).clip(RoundedCornerShape(12.dp)).background(s.surface)) {
                    Box(Modifier.padding(10.dp).width(54.dp).height(7.dp).clip(RoundedCornerShape(4.dp)).background(s.ink))
                    Box(Modifier.padding(start = 10.dp, top = 22.dp).width(36.dp).height(5.dp).clip(RoundedCornerShape(3.dp)).background(s.muted))
                }
                Box(Modifier.align(Alignment.BottomEnd).padding(10.dp).size(26.dp).clip(CircleShape).background(s.accent))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(name, Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleSmall, maxLines = 1)
                if (selected) Box(Modifier.size(10.dp).clip(CircleShape).background(scheme.primary))
            }
            Text(
                stringResource(item.descriptionResource), Modifier.padding(start = 4.dp, bottom = 4.dp),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant, maxLines = 2,
            )
        }
    }
}

/** "My theme" entry: a circle in three slices (background, cards, accent), like Atlante. */
@Composable
private fun CustomThemeCard(colors: CustomThemeColors, active: Boolean, onOpen: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val title = stringResource(R.string.preferences_color_scheme_custom)
    Surface(
        onClick = onOpen,
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerLow,
        border = BorderStroke(if (active) 2.dp else 1.dp, if (active) scheme.primary else scheme.outlineVariant),
        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).semantics { contentDescription = title; selected = active; role = Role.Button },
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ThreeSlices(Color(colors.paper), Color(colors.surface), Color(colors.accent), scheme.outlineVariant, Modifier.size(64.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(if (active) R.string.appearance_open_custom else R.string.theme_desc_custom),
                    style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall, color = scheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ThreeSlices(paper: Color, surface: Color, accent: Color, outline: Color, modifier: Modifier) {
    Canvas(modifier) {
        listOf(paper, surface, accent).forEachIndexed { i, c -> drawArc(c, -90f + i * 120f, 120f, true) }
        drawCircle(outline, size.minDimension / 2, style = Stroke(1.dp.toPx()))
        drawCircle(accent, size.minDimension / 5)
        drawCircle(Color.White, size.minDimension / 5, style = Stroke(2.dp.toPx()))
    }
}

/** Small round color sample with an outline. */
@Composable
fun ColorDot(color: Color, size: Int = 18) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(color).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape))
}
