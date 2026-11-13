package org.qosp.notes.ui.editor.markdown

import android.view.MotionEvent
import android.widget.EditText
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * The text field has one touch listener: the task squares share it with the editor's other tap handling (tables),
 * instead of one replacing the other.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TaskBoxTapsTest {
    private fun EditText.touch(action: Int) =
        dispatchTouchEvent(MotionEvent.obtain(0, 0, action, 5f, 5f, 0))

    @Test
    fun touchesOutsideABoxReachTheOtherHandler() {
        val seen = mutableListOf<Int>()
        val edit = EditText(RuntimeEnvironment.getApplication()).apply { setText("- [ ] milk") }
        edit.enableTaskBoxTaps({ true }) { seen += it.actionMasked }
        edit.touch(MotionEvent.ACTION_DOWN) // not laid out: no box under the finger
        edit.touch(MotionEvent.ACTION_UP)
        assertEquals(listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP), seen)
    }

    @Test
    fun otherHandlerStillWorksWhenBoxesAreOff() {
        val seen = mutableListOf<Int>()
        val edit = EditText(RuntimeEnvironment.getApplication())
        edit.enableTaskBoxTaps({ false }) { seen += it.actionMasked }
        edit.touch(MotionEvent.ACTION_DOWN)
        assertEquals(listOf(MotionEvent.ACTION_DOWN), seen)
    }
}
