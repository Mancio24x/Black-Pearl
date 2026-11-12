package org.qosp.notes.ui.reminders

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.data.model.Note

class ReminderManagerTest {
    @Test
    fun noReminderForAMissingOrBinnedNote() {
        assertFalse(ReminderManager.shouldNotify(null))
        assertFalse(ReminderManager.shouldNotify(Note(id = 1, isDeleted = true)))
    }

    @Test
    fun reminderForALiveOrArchivedNote() {
        assertTrue(ReminderManager.shouldNotify(Note(id = 1)))
        assertTrue(ReminderManager.shouldNotify(Note(id = 1, isArchived = true)))
    }
}
