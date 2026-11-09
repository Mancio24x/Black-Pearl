package org.qosp.notes.ui.settings

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.text.InputFilter
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.view.ContextThemeWrapper
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import org.qosp.notes.R
import org.qosp.notes.preferences.ColorScheme

/** Preset seeds offered by "My color"; any other color can be typed as hex. */
private val CUSTOM_COLOR_PRESETS = longArrayOf(
    0xFF1B4F72, 0xFF0277BD, 0xFF4FC3F7, 0xFF00897B, 0xFF4DB6AC, 0xFF2E7D32,
    0xFF7D9A7E, 0xFF827717, 0xFFC9A227, 0xFFFF8F00, 0xFFFF7043, 0xFFBF5B3B,
    0xFFB71C1C, 0xFF7B1F3A, 0xFFD81B60, 0xFF8E24AA, 0xFF3949AB, 0xFF546E7A,
    0xFF6D4C41, 0xFF424242,
).map { it.toInt() }.toIntArray()

private fun Context.dp(v: Int) = (v * resources.displayMetrics.density).toInt()

private fun Context.dot(color: Int, sizeDp: Int = 24): View = View(this).apply {
    background = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(color)
        setStroke(dp(1), MaterialColors.getColor(this@dot, com.google.android.material.R.attr.colorOutlineVariant, Color.GRAY))
    }
    layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)).apply { marginEnd = dp(6) }
}

/** Primary, primary container and tertiary of a palette, used as preview dots. */
private fun Context.swatches(scheme: ColorScheme, customSeed: Int): List<Int> = when {
    scheme == ColorScheme.CUSTOM -> listOf(customSeed)
    scheme == ColorScheme.SYSTEM && Build.VERSION.SDK_INT >= 31 -> listOf(
        ContextCompat.getColor(this, android.R.color.system_accent1_600),
        ContextCompat.getColor(this, android.R.color.system_accent1_200),
        ContextCompat.getColor(this, android.R.color.system_accent3_500),
    )
    else -> ContextThemeWrapper(this, scheme.styleResource).let { c ->
        listOf(
            androidx.appcompat.R.attr.colorPrimary,
            com.google.android.material.R.attr.colorPrimaryContainer,
            com.google.android.material.R.attr.colorTertiary,
        ).map { MaterialColors.getColor(c, it, Color.GRAY) }
    }
}

/** Palette picker showing color dots next to each name. */
fun Fragment.showColorSchemeDialog(selected: ColorScheme, customSeed: Int, onPick: (ColorScheme) -> Unit) {
    val ctx = requireContext()
    val schemes = ColorScheme.entries.filter { it.isSupported() }
    val adapter = object : ArrayAdapter<ColorScheme>(ctx, 0, schemes) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val scheme = schemes[position]
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = ctx.dp(56)
                setPadding(ctx.dp(24), ctx.dp(8), ctx.dp(24), ctx.dp(8))
                ctx.swatches(scheme, customSeed).forEach { addView(ctx.dot(it)) }
                addView(TextView(ctx).apply {
                    text = ctx.getString(scheme.nameResource) + if (scheme == selected) "  ✓" else ""
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                    setPadding(ctx.dp(10), 0, 0, 0)
                    if (scheme == selected) setTypeface(typeface, android.graphics.Typeface.BOLD)
                })
                contentDescription = ctx.getString(scheme.nameResource)
            }
        }
    }
    MaterialAlertDialogBuilder(ctx)
        .setTitle(R.string.preferences_color_scheme)
        .setAdapter(adapter) { d, which -> d.dismiss(); onPick(schemes[which]) }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}

/** "My color": preset seeds + free hex input. The whole M3 scheme is generated from the seed. */
fun Fragment.showCustomColorDialog(current: Int, onPick: (Int) -> Unit) {
    val ctx = requireContext()
    var chosen = current
    val preview = ctx.dot(current, 40)
    val inputLayout = TextInputLayout(ctx).apply {
        hint = ctx.getString(R.string.preferences_custom_color_hex)
        prefixText = "#"
    }
    val input = TextInputEditText(inputLayout.context).apply {
        setText(String.format("%06X", current and 0xFFFFFF))
        filters = arrayOf(InputFilter.LengthFilter(6), InputFilter.AllCaps())
        doAfterTextChanged { t ->
            val c = runCatching { Color.parseColor("#" + t.toString()) }.getOrNull()
            if (c != null && t?.length == 6) {
                chosen = c
                (preview.background as GradientDrawable).setColor(c)
                inputLayout.error = null
            } else {
                inputLayout.error = ctx.getString(R.string.preferences_custom_color_invalid)
            }
        }
    }
    inputLayout.addView(input)
    val grid = GridLayout(ctx).apply {
        columnCount = 5
        CUSTOM_COLOR_PRESETS.forEach { color ->
            addView(ctx.dot(color, 40).apply {
                (layoutParams as LinearLayout.LayoutParams).let {
                    layoutParams = GridLayout.LayoutParams().apply {
                        width = it.width; height = it.height
                        setMargins(ctx.dp(6), ctx.dp(6), ctx.dp(6), ctx.dp(6))
                    }
                }
                contentDescription = String.format("#%06X", color and 0xFFFFFF)
                setOnClickListener { input.setText(String.format("%06X", color and 0xFFFFFF)) }
            })
        }
    }
    val content = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(ctx.dp(24), ctx.dp(8), ctx.dp(24), 0)
        addView(TextView(ctx).apply {
            setText(R.string.preferences_custom_color_summary)
            setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyMedium)
        })
        addView(grid)
        addView(LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, ctx.dp(8), 0, 0)
            addView(preview)
            addView(inputLayout, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        })
    }
    MaterialAlertDialogBuilder(ctx)
        .setTitle(R.string.preferences_custom_color)
        .setView(content)
        .setPositiveButton(R.string.preferences_custom_color_apply) { _, _ -> onPick(chosen) }
        .setNegativeButton(R.string.action_cancel, null)
        .show()
}
