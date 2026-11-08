package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ***Sun/Cloud Height* spread over the sky the screen has** (`DESIGN_NOTES.md` §12, decision 106; the maintainer: *«B»*,
 * 2026-11-07, and shown, *«Sì, così (consigliata)»*, 2026-11-08): from 0 to 100 % the arc rises from 0.1 of the screen to
 * where the sun stays whole with its ring under the status bar, on every screen, in line.
 */
class SunArcTest {

    private val screens = listOf(720 to 1440, 360 to 800, 1080 to 2340, 1080 to 2400, 1440 to 3200)

    @Test
    fun `from the lowest to the highest the screen has room for, in line`() {
        for ((w, h) in screens) {
            assertEquals("$w x $h at 0 %", SUN_CLOUD_HEIGHT_MIN, SunArc.rise(SUN_CLOUD_HEIGHT_MIN, w, h), 1e-6f)
            assertEquals("$w x $h at 100 %", SunArc.highest(w, h), SunArc.rise(SUN_CLOUD_HEIGHT_MAX, w, h), 1e-6f)
            val half = SunArc.rise((SUN_CLOUD_HEIGHT_MIN + SUN_CLOUD_HEIGHT_MAX) / 2f, w, h)
            assertEquals("$w x $h at 50 %", (SUN_CLOUD_HEIGHT_MIN + SunArc.highest(w, h)) / 2f, half, 1e-5f)
        }
        assertEquals("the BV6600", 0.423f, SunArc.highest(720, 1440), 0.001f)
    }

    @Test
    fun `at every value the sun and its ring stay under the status bar`() {
        for ((w, h) in screens) for (k in 0..50) {
            val stored = SUN_CLOUD_HEIGHT_MIN + (SUN_CLOUD_HEIGHT_MAX - SUN_CLOUD_HEIGHT_MIN) * k / 50f
            // The top of the arc: the sun's middle at the horizon less the rise; the ring out to 1.4 radii.
            val middle = h * PaperRenderer.HORIZON_Y_FRACTION_OF_ARC - SunArc.rise(stored, w, h) * h
            val ringTop = middle - SunArc.RING_RADII * SunArc.radiusPx(w)
            assertTrue("$w x $h at $stored: the ring's top at $ringTop", ringTop >= SunArc.TOP_ROOM * h - 0.5f)
        }
    }

    @Test
    fun `the slider as it comes draws the sun lower, by about 160 px on the BV6600`() {
        val drop = (0.42f - SunArc.rise(0.42f, 720, 1440)) * 1440f
        assertEquals(163f, drop, 3f)
    }

    @Test
    fun `the sun, the moon and the still moon read the spread arc`() {
        val text = java.io.File(sequenceOf("src/main", "app/src/main").map { java.io.File(it) }.first { it.isDirectory },
            "kotlin/com/paperscrape/livewallpaper/engine/PaperRenderer.kt").readText()
        assertTrue("val riseHeight = screenHeight * SunArc.rise(sceneCustomization.sky.sunCloudHeight, screenWidth, screenHeight)" in codeLines(text))
        for (k in 0..10) {
            val stored = SUN_CLOUD_HEIGHT_MIN + (SUN_CLOUD_HEIGHT_MAX - SUN_CLOUD_HEIGHT_MIN) * k / 10f
            val y = StillMoon.arcY(720, 1440, stored)
            assertEquals("the still moon turned back into px", StillMoon.centreY(720, 1440, stored),
                1440 * PaperRenderer.HORIZON_Y_FRACTION_OF_ARC - y * SunArc.rise(stored, 720, 1440) * 1440, 0.01f)
        }
    }

    /** [text]'s lines, trimmed, with comment lines, trailing `//` comments and blank lines left out. */
    private fun codeLines(text: String): List<String> =
        text.lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }
}
