package org.qosp.notes.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeRolesTest {

    private val light = ThemeRoles.custom(0xFFF4ECDD.toInt(), 0xFFFFFBF3.toInt(), 0xFF8E2F25.toInt())
    private val dark = ThemeRoles.custom(0xFF0E1720.toInt(), 0xFF18232E.toInt(), 0xFFE6C068.toInt())

    @Test
    fun chosenColorsAreUsedExactly() {
        assertEquals(0xFFF4ECDD.toInt(), light["android:colorBackground"])
        assertEquals(0xFFFFFBF3.toInt(), light["colorSurfaceContainerLow"]) // note cards
        assertEquals(0xFF8E2F25.toInt(), light["colorPrimary"])
    }

    @Test
    fun darkAndLightAreDetected() {
        assertFalse(ThemeRoles.isDark(0xFFF4ECDD.toInt()))
        assertTrue(ThemeRoles.isDark(0xFF0E1720.toInt()))
        assertTrue(ThemeRoles.luminance(dark.getValue("colorOnSurface")) > 0.6) // light text on dark
        assertTrue(ThemeRoles.luminance(light.getValue("colorOnSurface")) < 0.05) // dark text on light
    }

    @Test
    fun textIsReadableInBothThemes() {
        for (r in listOf(light, dark)) {
            assertTrue(ThemeRoles.contrast(r.getValue("colorOnSurface"), r.getValue("colorSurfaceContainerLow")) >= 7.0)
            assertTrue(ThemeRoles.contrast(r.getValue("colorOnPrimary"), r.getValue("colorPrimary")) >= 4.5)
        }
    }

    @Test
    fun everyRoleOfTheCustomPaletteHasAResource() {
        assertEquals(light.keys, CustomThemeResources.byAttribute.keys)
    }
}
