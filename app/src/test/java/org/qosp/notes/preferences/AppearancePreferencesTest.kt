package org.qosp.notes.preferences

import me.msoul.datastore.defaultOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Preferences are stored by enum name: renaming a constant would silently reset users' choices. */
class AppearancePreferencesTest {

    @Test
    fun legacyColorSchemeNamesAreKept() {
        val names = ColorScheme.entries.map { it.name }.toSet()
        listOf("BLUE", "TRUE_RED", "TRUE_YELLOW", "GREEN", "PINK", "YELLOW", "RED", "SYSTEM").forEach {
            assertTrue("missing $it", it in names)
        }
    }

    @Test
    fun defaults() {
        assertEquals(ColorScheme.BLACK_PEARL, defaultOf<ColorScheme>())
        assertEquals(CardShape.EXTRA_ROUNDED, defaultOf<CardShape>())
        assertEquals(NoteFont.INTER, defaultOf<NoteFont>())
    }

    @Test
    fun eachStaticPaletteHasItsOwnStyle() {
        val dynamic = setOf(ColorScheme.SYSTEM, ColorScheme.CUSTOM)
        val styles = ColorScheme.entries.filter { it !in dynamic }.map { it.styleResource }
        assertEquals(styles.size, styles.toSet().size)
    }
}
