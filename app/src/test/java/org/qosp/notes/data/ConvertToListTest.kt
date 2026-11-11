package org.qosp.notes.data

import org.junit.Assert.assertEquals
import org.junit.Test
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteTask

/** "Convert to list": checkboxes written in the text become real checkboxes, not "[ ]" inside the items. */
class ConvertToListTest {
    private fun convert(text: String) = Note(content = text).stringToTaskList().map { it.content to it.isDone }

    @Test
    fun markdownAndSymbolCheckboxesBecomeRealOnes() {
        assertEquals(
            listOf("latte" to false, "pane" to true, "caffè" to false, "uova" to true, "farina" to true, "sale" to false),
            convert("- [ ] latte\n- [x] pane\n[ ] caffè\n[X] uova\n☑ farina\n☐ sale"),
        )
    }

    @Test
    fun bulletsNumbersAndPlainLinesBecomeItems() {
        assertEquals(
            listOf("uno" to false, "due" to false, "tre" to false, "quattro" to false, "cinque" to false),
            convert("- uno\n* due\n• tre\n1. quattro\ncinque"),
        )
    }

    @Test
    fun emptyLinesAreSkippedAndIdsFollow() {
        assertEquals(
            listOf(NoteTask(0, "a", false), NoteTask(1, "b", false)),
            Note(content = "a\n\n   \n- [ ] \nb").stringToTaskList(),
        )
    }
}
