package org.qosp.notes.ui.editor.markdown

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TaskBoxesTest {

    @Test
    fun findsEmptyAndTickedBoxes() {
        val text = "Shopping\n- [ ] milk\n- [x] bread\n  * [X] nested"
        val boxes = findTaskBoxes(text)
        assertEquals(3, boxes.size)
        assertEquals(TaskBox(9, 14, checked = false, lineEnd = 19), boxes[0])
        assertEquals("- [ ]", text.substring(boxes[0].start, boxes[0].end))
        assertEquals(' ', text[boxes[0].markIndex])
        assertTrue(boxes[1].checked)
        assertEquals('x', text[boxes[1].markIndex])
        assertEquals("* [X]", text.substring(boxes[2].start, boxes[2].end))
        assertTrue(boxes[2].checked)
    }

    @Test
    fun boxJustCreatedByEnterIsFound() {
        // the editor continues a task list with "- [ ] " and the cursor at the end
        assertEquals(1, findTaskBoxes("- [ ] milk\n- [ ] ").size)
        assertEquals(1, findTaskBoxes("- [ ]").size)
    }

    @Test
    fun ignoresTextThatIsNotATask() {
        assertTrue(findTaskBoxes("see [x] here").isEmpty())
        assertTrue(findTaskBoxes("- [y] no").isEmpty())
        assertTrue(findTaskBoxes("- [ ]milk").isEmpty())
        assertTrue(findTaskBoxes("-[ ] milk").isEmpty())
        assertTrue(findTaskBoxes("- [link](https://example.com)").isEmpty())
    }
}
