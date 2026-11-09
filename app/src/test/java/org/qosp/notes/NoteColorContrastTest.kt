package org.qosp.notes

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.pow

/**
 * Note text must stay readable on every note color, in light and dark mode, with every palette:
 * WCAG AA contrast (>= 4.5:1) between each colorNote* and the palettes' colorOnSurface.
 */
class NoteColorContrastTest {

    private val res = listOf("src/main/res", "app/src/main/res").map(::File).first { it.isDirectory }

    private fun luminance(hex: String): Double {
        val v = hex.removePrefix("#").takeLast(6)
        val (r, g, b) = (0..2).map { v.substring(it * 2, it * 2 + 2).toInt(16) / 255.0 }
            .map { if (it <= 0.03928) it / 12.92 else ((it + 0.055) / 1.055).pow(2.4) }
        return 0.2126 * r + 0.7152 * g + 0.0722 * b
    }

    private fun contrast(a: String, b: String): Double {
        val (hi, lo) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun check(themes: String, palettes: String, light: Boolean) {
        val noteColors = Regex("""name="colorNote(Red|Orange|Yellow|Green|Teal|Cyan|Blue|Purple|Pink|Brown|Gray)">(#[0-9A-Fa-f]+)<""")
            .findAll(File(res, themes).readText()).map { it.groupValues[1] to it.groupValues[2] }.toList()
        // Always-dark themes force night mode (BaseActivity), so only their dark note colors are ever used.
        val styles = Regex("""<!--([^>]*)-->\s*<style name="[^"]+"(.*?)</style>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(File(res, palettes).readText()).toList()
        val onSurface = styles
            .filterNot { light && it.groupValues[1].contains("always dark") }
            .mapNotNull { Regex("""name="colorOnSurface">(#[0-9A-Fa-f]+)<""").find(it.groupValues[2])?.groupValues?.get(1) }
        assertTrue(styles.size >= 20)
        assertTrue(noteColors.size == 11 && onSurface.isNotEmpty())
        for ((name, color) in noteColors) for (text in onSurface) {
            val c = contrast(color, text)
            assertTrue("$themes: $name $color vs text $text = %.2f".format(c), c >= 4.5)
        }
    }

    @Test
    fun lightNoteColorsAreReadable() = check("values/themes.xml", "values/palettes.xml", light = true)

    @Test
    fun darkNoteColorsAreReadable() = check("values-night/themes.xml", "values-night/palettes.xml", light = false)
}
