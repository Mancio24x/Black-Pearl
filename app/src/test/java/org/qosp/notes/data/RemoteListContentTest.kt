package org.qosp.notes.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.model.NoteTask

/** Pulling a synced checklist: never drop or glue remote lines that are not tasks. */
class RemoteListContentTest {
    private val list = Note(isList = true, taskList = listOf(NoteTask(0, "old", false)))

    @Test
    fun pureTaskListStaysAChecklist() {
        val note = list.withRemoteListContent("- [ ] a\n- [x] b\n")
        assertTrue(note.isList)
        assertEquals("", note.content)
        assertEquals(listOf("a" to false, "b" to true), note.taskList.map { it.content to it.isDone })
    }

    @Test
    fun headingBeforeTasksTurnsIntoTextWithAllLines() {
        val remote = "# Shopping\n- [ ] a\n- [ ] b"
        val note = list.withRemoteListContent(remote)
        assertFalse(note.isList)
        assertEquals(remote, note.content)
        assertTrue(note.taskList.isEmpty())
    }

    @Test
    fun textLineBetweenTasksTurnsIntoText() {
        val remote = "- [ ] a\nSome text\n- [x] b"
        val note = list.withRemoteListContent(remote)
        assertFalse(note.isList)
        assertEquals(remote, note.content)
    }

    @Test
    fun blankLinesDoNotCount() {
        assertTrue(list.isMdTaskListOnly("- [ ] a\n\n   \n* [X] b"))
        assertFalse(list.isMdTaskListOnly("- [ ] a\n- plain bullet"))
        assertTrue(list.withRemoteListContent("- [ ] a\n\n- [ ] b").isList)
    }

    @Test
    fun mdToTaskListParsesTasks() {
        assertEquals(
            listOf(NoteTask(0, "a", false), NoteTask(1, "b", true)),
            list.mdToTaskList("- [ ] a\n+ [x] b"),
        )
    }
}
