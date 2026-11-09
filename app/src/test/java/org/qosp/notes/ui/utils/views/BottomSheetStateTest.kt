package org.qosp.notes.ui.utils.views

import android.os.Parcel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Android saves the screen, with its open sheets and their arguments, when the app goes to the background (file
 * picker for "Save PDF", share sheet, camera…). The sheets' actions used to be in the arguments and could not be
 * written ("Parcelable encountered IOException writing serializable object"): the app crashed.
 */
@RunWith(RobolectricTestRunner::class)
class BottomSheetStateTest {

    @Test
    fun anOpenSheetCanBeSavedWhenTheAppGoesToTheBackground() {
        val screen = Any() // what the actions use: the screen that opened the sheet, which cannot be saved
        val sheet = BottomSheet.create("Export") {
            action("Save PDF", null) { screen.hashCode() }
            action("Share PDF", null) { screen.hashCode() }
        }
        val parcel = Parcel.obtain()
        try {
            sheet.requireArguments().writeToParcel(parcel, 0) // threw before the fix
        } finally {
            parcel.recycle()
        }
    }
}
