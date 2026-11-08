package org.qosp.notes.ui.common

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import org.qosp.notes.R
import org.qosp.notes.data.model.NoteColor
import org.qosp.notes.ui.utils.resId
import org.qosp.notes.ui.utils.resolveAttribute

/** Localized name of a note color ("No color" for [NoteColor.Default]). */
fun NoteColor.label(context: Context): String = context.getString(
    when (this) {
        NoteColor.Default -> R.string.note_color_none
        NoteColor.Red -> R.string.preferences_color_scheme_red
        NoteColor.Orange -> R.string.preferences_color_scheme_orange
        NoteColor.Yellow -> R.string.preferences_color_scheme_yellow
        NoteColor.Green -> R.string.preferences_color_scheme_green
        NoteColor.Teal -> R.string.preferences_color_scheme_teal
        NoteColor.Cyan -> R.string.preferences_color_scheme_cyan
        NoteColor.Blue -> R.string.preferences_color_scheme_blue
        NoteColor.Purple -> R.string.preferences_color_scheme_purple
        NoteColor.Pink -> R.string.preferences_color_scheme_pink
        NoteColor.Brown -> R.string.preferences_color_scheme_brown
        NoteColor.Gray -> R.string.preferences_color_scheme_gray
    }
)

/**
 * Note color picker with color swatches (current theme, light or dark).
 * [onPick] is called on every tap, so the editor background previews the color immediately;
 * the dialog stays open until "Done". [selected] null = several notes with different colors.
 */
fun Fragment.showNoteColorDialog(selected: NoteColor?, onPick: (NoteColor) -> Unit) {
    val ctx = requireContext()
    val colors = NoteColor.entries
    var current = selected
    val dp = ctx.resources.displayMetrics.density
    val adapter = object : ArrayAdapter<NoteColor>(ctx, 0, colors) {
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val color = colors[position]
            val fill = color.resId(ctx) ?: ctx.resolveAttribute(R.attr.colorNoteDefault) ?: Color.WHITE
            return LinearLayout(ctx).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = (52 * dp).toInt()
                setPadding((24 * dp).toInt(), 0, (24 * dp).toInt(), 0)
                addView(View(ctx).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(fill)
                        val outline = MaterialColors.getColor(ctx, com.google.android.material.R.attr.colorOutline, Color.GRAY)
                        setStroke((if (color == current) 3 * dp else 1 * dp).toInt(), outline)
                    }
                    layoutParams = LinearLayout.LayoutParams((28 * dp).toInt(), (28 * dp).toInt())
                })
                addView(TextView(ctx).apply {
                    text = color.label(ctx) + if (color == current) "  ✓" else ""
                    setTextAppearance(com.google.android.material.R.style.TextAppearance_Material3_BodyLarge)
                    setPadding((16 * dp).toInt(), 0, 0, 0)
                    if (color == current) setTypeface(typeface, Typeface.BOLD)
                })
                contentDescription = color.label(ctx)
            }
        }
    }
    MaterialAlertDialogBuilder(ctx)
        .setTitle(R.string.action_change_color)
        .setAdapter(adapter, null)
        .setPositiveButton(R.string.action_done, null)
        .create()
        .apply {
            listView.setOnItemClickListener { _, _, which, _ ->
                current = colors[which]
                adapter.notifyDataSetChanged()
                onPick(colors[which])
            }
        }
        .show()
}
