package org.qosp.notes.ui.settings

import androidx.fragment.app.Fragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import me.msoul.datastore.EnumPreference
import org.qosp.notes.R
import org.qosp.notes.preferences.HasNameResource
import org.qosp.notes.preferences.HasSupportRequirement

inline fun <reified T> Fragment.showPreferenceDialog(
    titleRes: Int,
    selected: T,
    dismiss: Boolean = true,
    items: Array<String>? = null,
    crossinline onClick: (T) -> Unit,
) where T : Enum<T>, T : EnumPreference {
    // Only supported values are listed; map the clicked row back to the same filtered list.
    val enumValues = enumValues<T>().filter { it !is HasSupportRequirement || it.isSupported() }
    val selectedIndex = enumValues.indexOf(selected)
    val items = items ?: enumValues
        .map {
            if (it is HasNameResource) requireContext().getString(it.nameResource) else ""
        }
        .toTypedArray()

    MaterialAlertDialogBuilder(requireContext())
        .setTitle(requireContext().getString(titleRes))
        .setSingleChoiceItems(items, selectedIndex) { dialogInterface, which ->
            if (dismiss) dialogInterface.dismiss()
            onClick(enumValues[which])
        }
        .setPositiveButton(getString(R.string.action_done)) { dialogInterface, i ->
            dialogInterface.dismiss()
        }
        .show()
}
