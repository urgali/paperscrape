package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REN-05: every tint this app chooses is opaque, and a day/night blend of two of them loses at most one level of it -- which
 * is what lets GL ignore the alpha byte.
 *
 * `GlSceneTarget` drops the tint colour's alpha and the comment beside it used to justify that by
 * claiming `PorterDuffColorFilter(tint, MULTIPLY)` takes opacity from the paint rather than from the
 * filter colour. It does not: MULTIPLY multiplies the filter's alpha in too, so a non-opaque tint
 * would draw differently on the two backends.
 *
 * Until the seal of v5.17 this file said no tint is ever non-opaque, and its glass test modelled the blend with an
 * interpolation of its own that cannot lose the alpha; `SceneColour.blendArgb`, which the app blends with, cuts each channel
 * to a level, the alpha too, and two opaque colours come out at 254 at about one ratio in 26: the Canvas backend draws such a
 * tint one level of 255 dimmer than the GL one, at moments of the dusk and the dawn. Not seen, and left so by the
 * maintainer's *«Lascia così (consigliata)»* (2026-10-25). What is pinned is that decision: the colours chosen once opaque,
 * the blend's ends opaque, never more than that one level lost between them.
 */
class TintOpacityTest {

    private fun alphaOf(colour: Int) = (colour ushr 24) and 0xFF

    @Test
    fun `every built-in theme's colours are opaque`() {
        for (theme in ThemeCatalog.ALL) {
            val colours = mapOf(
                "accent" to theme.accentColor,
                "sun" to theme.sunColor,
                "moon" to theme.moonColor,
            )
            for ((name, colour) in colours) {
                assertEquals(
                    "${theme.id} $name is not opaque",
                    0xFF,
                    alphaOf(colour),
                )
            }
        }
    }

    @Test
    fun `every default customization colour is opaque`() {
        for (id in ThemeCatalog.ALL.map { it.id }) {
            val c = defaultCustomizationFor(id)
            val colours = listOf(
                "houses1" to c.houses.colorDay1, "houses2" to c.houses.colorDay2,
                "housesNight1" to c.houses.colorNight1, "housesNight2" to c.houses.colorNight2,
                "buildings1" to c.buildings.colorDay1, "trees1" to c.trees.colorDay1,
                "hills" to c.hillsColorDay, "hillsNight" to c.hillsColorNight,
            )
            for ((name, colour) in colours) {
                assertEquals("$id $name is not opaque", 0xFF, alphaOf(colour))
            }
        }
    }

    @Test
    fun `the day-night transform never produces a transparent colour`() {
        // The other source of a tint: a derived night or day colour. If this could return a
        // non-opaque value the GL shortcut would start to matter.
        val samples = listOf(
            0xFFFFFFFF, 0xFF000000, 0xFFE03A2F, 0xFF7FB3D5, 0xFFF2D06B, 0xFF2E86AB,
        ).map { it.toInt() }
        for (colour in samples) {
            assertEquals(
                "nightFromDay dropped the alpha of %08X".format(colour),
                0xFF,
                alphaOf(DayNightColor.nightFromDay(colour)),
            )
            assertEquals(
                "dayFromNight dropped the alpha of %08X".format(colour),
                0xFF,
                alphaOf(DayNightColor.dayFromNight(colour)),
            )
        }
    }

    @Test
    fun `a day-night blend of two opaque colours is opaque at its ends and loses at most one level between them`() {
        // The tints computed per frame: the window glass's crossfade, the app's own function, and the blend every wall, tree
        // and car takes between its day and its night colour (`SceneColour.blendArgb`).
        var lost = 0
        val steps = 100_000
        for (step in 0..steps) {
            val t = step / steps.toFloat()
            val glass = SceneObjectRenderer.windowGlassColor(t)
            val blend = SceneColour.blendArgb(0xFF6B5F52.toInt(), 0xFFF3E6D0.toInt(), t)
            for (colour in intArrayOf(glass, blend)) {
                assertTrue("at $t the alpha is ${alphaOf(colour)}", alphaOf(colour) >= 0xFE)
                if (alphaOf(colour) == 0xFE) lost++
            }
        }
        for (t in floatArrayOf(0f, 1f)) {
            assertEquals("the glass at its end $t", 0xFF, alphaOf(SceneObjectRenderer.windowGlassColor(t)))
            assertEquals("a blend at its end $t", 0xFF, alphaOf(SceneColour.blendArgb(0xFF6B5F52.toInt(), 0xFFF3E6D0.toInt(), t)))
        }
        println("TintOpacityTest: alpha 254 at $lost of ${2 * (steps + 1)} blends")
    }
}
