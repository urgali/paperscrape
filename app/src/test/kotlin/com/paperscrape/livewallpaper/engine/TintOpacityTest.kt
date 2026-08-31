package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REN-05: every tint this app produces is opaque, which is what lets GL ignore the alpha byte.
 *
 * `GlSceneTarget` drops the tint colour's alpha and the comment beside it used to justify that by
 * claiming `PorterDuffColorFilter(tint, MULTIPLY)` takes opacity from the paint rather than from the
 * filter colour. It does not: MULTIPLY multiplies the filter's alpha in too, so a non-opaque tint
 * would draw differently on the two backends.
 *
 * The behaviour is still right, for a different reason — no tint is ever non-opaque. That was true
 * by accident and is now true by assertion, which is the whole point of this file: the GL shortcut
 * rests on a property of the colours, so the property is the thing to pin.
 */
class TintOpacityTest {

    private fun alphaOf(colour: Int) = (colour ushr 24) and 0xFF

    private fun lerpArgb(from: Int, to: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val a = (from shr shift) and 0xFF
            val b = (to shr shift) and 0xFF
            return (a + (b - a) * t).toInt().coerceIn(0, 255)
        }
        return (channel(24) shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }

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
    fun `the window glass crossfade stays opaque at every point`() {
        // The one tint that is computed per frame rather than chosen once.
        for (step in 0..20) {
            // The blend done here rather than through ColorUtils, which is Android and not
            // available to a JVM test. What is being asserted is the property of the two ends: a
            // linear interpolation between two opaque colours cannot be non-opaque, and the ends
            // are what a future edit would change.
            val blended = lerpArgb(
                SceneObjectRenderer.WINDOW_GLASS_DAY,
                SceneObjectRenderer.WINDOW_GLASS_NIGHT,
                step / 20f,
            )
            assertTrue(
                "the glass tint at ${step / 20f} is not opaque",
                alphaOf(blended) == 0xFF,
            )
        }
    }
}
