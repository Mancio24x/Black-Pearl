package org.qosp.notes.tests

import android.annotation.SuppressLint
import android.content.res.Configuration
import androidx.appcompat.view.ContextThemeWrapper
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.material.color.ColorResourcesOverride
import com.google.android.material.color.MaterialColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.qosp.notes.R
import org.qosp.notes.preferences.CustomThemeColors
import org.qosp.notes.ui.theme.CustomTheme
import org.qosp.notes.ui.theme.CustomThemeResources

/** "My theme" runtime recoloring: overridden color resources and the theme attributes that point to them. */
@SuppressLint("RestrictedApi")
class CustomThemeTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val colors = CustomThemeColors(0xFF0E1720.toInt(), 0xFF18232E.toInt(), 0xFFE6C068.toInt())

    private fun hex(c: Int) = "#%08X".format(c)

    @Test
    fun overriddenResourcesHaveTheComputedColors() {
        val roles = CustomTheme.roles(colors)
        val map = roles.mapNotNull { (attr, c) -> CustomThemeResources.byAttribute[attr]?.let { it to c } }.toMap()
        val override = ColorResourcesOverride.getInstance()
        assertNotNull("no ColorResourcesOverride on this API level", override)
        val wrapped = override!!.wrapContextIfPossible(context, map)
        for ((attr, resId) in CustomThemeResources.byAttribute) {
            assertEquals("resource for $attr", hex(roles.getValue(attr)), hex(wrapped.getColor(resId)))
        }
        val themed = ContextThemeWrapper(wrapped, R.style.AppTheme).apply { theme.applyStyle(R.style.Palette_Custom, true) }
        assertEquals("colorPrimary", hex(0xFFE6C068.toInt()), hex(MaterialColors.getColor(themed, R.attr.colorPrimary, 0)))
        assertEquals("background", hex(0xFF0E1720.toInt()), hex(MaterialColors.getColor(themed, android.R.attr.colorBackground, 0)))
        assertEquals("onSurface", hex(roles.getValue("colorOnSurface")), hex(MaterialColors.getColor(themed, R.attr.colorOnSurface, 0)))
    }

    /** What the app does (BaseActivity): the theme must show the chosen colors, not Material You's wallpaper colors. */
    @Test
    fun appliedThemeShowsTheChosenColors() {
        val themed = ContextThemeWrapper(context, R.style.AppTheme).apply {
            applyOverrideConfiguration(Configuration()) // own Resources, like an activity
            theme.applyStyle(R.style.Palette_Custom, true)
        }
        assertTrue(CustomTheme.apply(themed, colors))
        val roles = CustomTheme.roles(colors)
        assertEquals("colorPrimary", hex(0xFFE6C068.toInt()), hex(MaterialColors.getColor(themed, R.attr.colorPrimary, 0)))
        assertEquals("background", hex(0xFF0E1720.toInt()), hex(MaterialColors.getColor(themed, android.R.attr.colorBackground, 0)))
        assertEquals("cards", hex(0xFF18232E.toInt()), hex(MaterialColors.getColor(themed, R.attr.colorSurfaceContainerLow, 0)))
        assertEquals("onSurface", hex(roles.getValue("colorOnSurface")), hex(MaterialColors.getColor(themed, R.attr.colorOnSurface, 0)))
        assertEquals("onBackground", hex(roles.getValue("colorOnBackground")), hex(MaterialColors.getColor(themed, R.attr.colorOnBackground, 0)))
    }
}
