package org.qosp.notes.ui.editor.markdown

import org.junit.Assert.assertEquals
import org.junit.Test

class ToggleBulletTest {

    @Test
    fun addsABullet() {
        assertEquals("- Portare acqua", toggleBullet("Portare acqua"))
        assertEquals("- ", toggleBullet(""))
    }

    @Test
    fun removesAnExistingBullet() {
        assertEquals("Portare acqua", toggleBullet("- Portare acqua"))
        assertEquals("uno", toggleBullet("* uno"))
        assertEquals("  rientrato", toggleBullet("  + rientrato"))
    }

    @Test
    fun aTaskIsNotTakenForABullet() {
        assertEquals("- - [ ] compito", toggleBullet("- [ ] compito"))
    }

    @Test
    fun boldTextIsNotABullet() {
        assertEquals("- **forte**", toggleBullet("**forte**"))
    }
}
