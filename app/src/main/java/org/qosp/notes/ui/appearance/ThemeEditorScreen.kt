package org.qosp.notes.ui.appearance

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import org.qosp.notes.R
import org.qosp.notes.preferences.CustomThemeColors
import org.qosp.notes.ui.theme.CustomTheme
import org.qosp.notes.ui.theme.ThemeRoles
import kotlin.math.roundToInt

private fun Color.hsv(): FloatArray = FloatArray(3).also { android.graphics.Color.colorToHSV(toArgb(), it) }
private fun hsv(h: Float, s: Float, v: Float) =
    Color(android.graphics.Color.HSVToColor(floatArrayOf(h.coerceIn(0f, 360f), s.coerceIn(0f, 1f), v.coerceIn(0f, 1f))))

/** Quick shades per slot: background, cards, accent (light and dark ones). */
private val PRESETS = listOf(
    listOf(0xFFFFFFFF, 0xFFF6F3EC, 0xFFF4ECDD, 0xFFEAF3F7, 0xFFEFF5EF, 0xFFFFF3F5, 0xFFF5F1FC, 0xFFFFF1EC, 0xFF2A2F36, 0xFF171C24, 0xFF0E1720, 0xFF191724, 0xFF1C1A17, 0xFF000000),
    listOf(0xFFFFFFFF, 0xFFFFFDF8, 0xFFFFFBF3, 0xFFFBFEFF, 0xFFFBFEFA, 0xFFFFFCFD, 0xFFFDFCFF, 0xFFFFFBF9, 0xFF3A414B, 0xFF232C38, 0xFF18232E, 0xFF282337, 0xFF2A2622, 0xFF101012),
    listOf(0xFF1B4F72, 0xFF006A8E, 0xFF416D53, 0xFF8E2F25, 0xFF99496C, 0xFF76539C, 0xFFB5472C, 0xFF8A5035, 0xFFC9A227, 0xFFE6C068, 0xFFADCFFF, 0xFFD3B6FF, 0xFFFFB4B4, 0xFF8FD4B8),
).map { row -> row.map { Color(it) } }

@Composable
fun ThemeEditorScreen(initial: CustomThemeColors, onApply: (CustomThemeColors) -> Unit) {
    val colors = remember { mutableStateListOf(Color(initial.paper), Color(initial.surface), Color(initial.accent)) }
    var editing by remember { mutableIntStateOf(2) }
    val labels = listOf(
        stringResource(R.string.custom_theme_background),
        stringResource(R.string.custom_theme_cards),
        stringResource(R.string.custom_theme_accent),
    )
    val current = CustomThemeColors(colors[0].toArgb(), colors[1].toArgb(), colors[2].toArgb())
    val roles = CustomTheme.roles(current)
    fun role(name: String) = Color(roles.getValue(name))
    val worst = minOf(
        ThemeRoles.contrast(roles.getValue("colorOnSurface"), roles.getValue("colorSurfaceContainerLow")),
        ThemeRoles.contrast(roles.getValue("colorOnSurface"), roles.getValue("android:colorBackground")),
        ThemeRoles.contrast(roles.getValue("colorOnPrimary"), roles.getValue("colorPrimary")),
    )
    val scheme = MaterialTheme.colorScheme

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Button(onClick = { onApply(current) }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp)) {
            Text(stringResource(R.string.custom_theme_apply))
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            labels.forEachIndexed { i, label ->
                val on = editing == i
                Row(
                    Modifier.weight(1f).heightIn(min = 48.dp).clip(RoundedCornerShape(24.dp)).clickable { editing = i }
                        .background(if (on) scheme.secondaryContainer else Color.Transparent)
                        .border(if (on) 2.dp else 1.dp, if (on) scheme.primary else scheme.outlineVariant, RoundedCornerShape(24.dp))
                        .padding(horizontal = 10.dp)
                        .semantics { contentDescription = label; selected = on },
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                ) {
                    ColorDot(colors[i])
                    Text(label, style = MaterialTheme.typography.labelLarge, maxLines = 1)
                }
            }
        }

        HsvPicker(colors[editing], labels[editing]) { colors[editing] = it }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(PRESETS[editing]) { c ->
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(c)
                        .border(if (c == colors[editing]) 3.dp else 1.dp, if (c == colors[editing]) scheme.primary else scheme.outlineVariant, CircleShape)
                        .clickable { colors[editing] = c }
                        .semantics { contentDescription = "#%06X".format(c.toArgb() and 0xFFFFFF) }
                )
            }
        }

        val ratio = "%.1f".format(worst)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (worst >= 4.5) Color(0xFF2E7D4F) else scheme.error))
            Text(
                stringResource(if (worst >= 4.5) R.string.custom_theme_contrast_ok else R.string.custom_theme_contrast_low, ratio),
                style = MaterialTheme.typography.bodySmall, color = scheme.onSurfaceVariant,
            )
        }

        // Live preview with the exact roles the app will use
        Column(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(role("android:colorBackground"))
                .border(1.dp, scheme.outlineVariant, RoundedCornerShape(28.dp)).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(stringResource(R.string.custom_theme_preview).uppercase(), style = MaterialTheme.typography.labelSmall, color = role("colorOnSurfaceVariant"))
            Text(stringResource(R.string.custom_theme_sample_title), style = MaterialTheme.typography.headlineSmall, color = role("colorOnSurface"))
            Column(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(role("colorSurfaceContainerLow")).padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(stringResource(R.string.custom_theme_sample_note), style = MaterialTheme.typography.titleSmall, color = role("colorOnSurface"))
                Text(stringResource(R.string.custom_theme_sample_body), style = MaterialTheme.typography.bodySmall, color = role("colorOnSurfaceVariant"))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.clip(RoundedCornerShape(14.dp)).background(role("colorSecondaryContainer")).padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Text(stringResource(R.string.custom_theme_accent), style = MaterialTheme.typography.labelMedium, color = role("colorOnSecondaryContainer"))
                }
                Box(Modifier.weight(1f))
                Box(Modifier.size(56.dp).clip(CircleShape).background(role("colorPrimary")), contentAlignment = Alignment.Center) {
                    Text("+", style = MaterialTheme.typography.headlineMedium, color = role("colorOnPrimary"))
                }
            }
        }
    }
}

/** Saturation/brightness square and hue bar, by tap or drag. */
@Composable
private fun HsvPicker(color: Color, label: String, onColor: (Color) -> Unit) {
    val start = color.hsv()
    var hue by remember(label) { mutableFloatStateOf(start[0]) }
    var sat by remember(label) { mutableFloatStateOf(start[1]) }
    var value by remember(label) { mutableFloatStateOf(start[2]) }
    // a preset tap changes the color from outside: follow it
    val now = color.hsv()
    if (hsv(hue, sat, value).toArgb() != color.toArgb()) { hue = now[0]; sat = now[1]; value = now[2] }
    fun emit() = onColor(hsv(hue, sat, value))

    val shades = stringResource(R.string.custom_theme_shades)
    Canvas(
        Modifier.fillMaxWidth().height(170.dp).clip(RoundedCornerShape(20.dp))
            .semantics {
                contentDescription = "$shades · $label"
                stateDescription = "${(sat * 100).roundToInt()}% · ${(value * 100).roundToInt()}%"
            }
            .pointerInput(label) {
                fun set(p: Offset) { sat = (p.x / size.width).coerceIn(0f, 1f); value = 1f - (p.y / size.height).coerceIn(0f, 1f); emit() }
                detectTapGestures { set(it) }
            }
            .pointerInput(label) {
                fun set(p: Offset) { sat = (p.x / size.width).coerceIn(0f, 1f); value = 1f - (p.y / size.height).coerceIn(0f, 1f); emit() }
                detectDragGestures(onDragStart = { set(it) }) { change, _ -> change.consume(); set(change.position) }
            }
    ) {
        drawRect(Brush.horizontalGradient(listOf(Color.White, hsv(hue, 1f, 1f))))
        drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        val p = Offset(sat * size.width, (1f - value) * size.height)
        drawCircle(Color.White, 12.dp.toPx(), p, style = Stroke(3.dp.toPx()))
        drawCircle(Color.Black.copy(alpha = 0.4f), 13.5.dp.toPx(), p, style = Stroke(1.dp.toPx()))
    }

    val hueLabel = stringResource(R.string.custom_theme_hue)
    Canvas(
        Modifier.fillMaxWidth().height(36.dp).clip(RoundedCornerShape(18.dp))
            .semantics { contentDescription = "$hueLabel · $label"; stateDescription = "${hue.roundToInt()}°" }
            .pointerInput(label) { detectTapGestures { hue = (it.x / size.width * 360f).coerceIn(0f, 360f); emit() } }
            .pointerInput(label) {
                detectDragGestures(onDragStart = { hue = (it.x / size.width * 360f).coerceIn(0f, 360f); emit() }) { change, _ ->
                    change.consume(); hue = (change.position.x / size.width * 360f).coerceIn(0f, 360f); emit()
                }
            }
    ) {
        drawRect(Brush.horizontalGradient((0..12).map { hsv(it * 30f, 1f, 1f) }))
        val x = hue / 360f * size.width
        drawCircle(Color.White, size.height / 2.6f, Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
    }
}
