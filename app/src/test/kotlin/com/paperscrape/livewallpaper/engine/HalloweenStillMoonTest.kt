package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.ui.SettingsUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The still Halloween moon** (v5.17, *Moon stays in place*): the maintainer's *«aggiungi toggle per halloween di mettere la
 * luna zucca in posizione fixed in cielo senza farle fare il giro di sole e luna»* (2026-10-23), *«L3: bassa sui tetti»* and,
 * to the table of v5.17A with its rows 12 to 18, *«Sì a tutte»* (2026-10-24).
 *
 * In force exactly with *Halloween*, *Moon* and its own switch, whatever *Show Sun*, *Horror sky* and the night by day; its
 * row greyed with the reason otherwise, the choice kept. Where it stands: in the middle, under the cloud band and over the
 * hills, following *Sun/Cloud Height* from its lowest to its highest; within the screen at every scroll (decision 19).
 * When: with the night by day at every hour, all out; without, never with the sun, coming out after sunset and fading
 * before sunrise where it stands, with the sun's times of a fixed hour (6:00 and 20:00) and of a short day of a real place
 * (7:15 and 17:30). Off, the moon crosses the sky as before: the phase is left as it is.
 */
class HalloweenStillMoonTest {

    private val w = 720
    private val h = 1440

    /** Every quarter of an hour, and the minutes about the sun's two edges of both places below. */
    private val hours = (0 until 96).map { it / 4f } + listOf(5.9f, 6.05f, 6.2f, 7.3f, 7.45f, 17.4f, 17.6f, 19.8f, 19.95f, 20.1f, 20.3f)
    private val places = listOf(6f to 20f, 7.25f to 17.5f)

    @Test
    fun `it is in force only with Halloween, the moon and its own switch, and its row says what holds it`() {
        val base = defaultCustomizationFor("autumn")
        for (halloween in listOf(false, true)) for (moon in listOf(false, true)) for (own in listOf(false, true))
            for (sun in listOf(false, true)) for (horror in listOf(false, true)) for (nightByDay in listOf(false, true)) {
                val c = base.copy(
                    halloweenEnabled = halloween, stillMoonEnabled = own, horrorSkyEnabled = horror, nightSkyByDayEnabled = nightByDay,
                    moon = base.moon.copy(visible = moon), sun = base.sun.copy(visible = sun),
                )
                val label = "halloween $halloween moon $moon own $own sun $sun horror $horror night by day $nightByDay"
                assertEquals(label, halloween && moon && own, c.stillMoonActive())
                val row = SettingsUiModel.stillMoon(c)
                assertEquals(label, own && halloween && moon, row.shownOn)
                assertEquals(label, halloween && moon, row.interactive)
                assertEquals(label, !halloween, row.needsHalloween)
                assertEquals(label, halloween && !moon, row.needsMoon)
            }
    }

    @Test
    fun `it stands in the middle under the clouds and above the hills' horizon, following Sun and cloud height`() {
        val radius = w * PaperRenderer.CELESTIAL_RADIUS_FRACTION * 2f
        assertEquals("0.37 of the height as the slider comes", 0.37f, StillMoon.centreY(w, h, 0.42f) / h, 0.005f)
        assertEquals("0.53 at its lowest", 0.53f, StillMoon.centreY(w, h, SUN_CLOUD_HEIGHT_MIN) / h, 0.005f)
        assertEquals("0.28 at its highest", 0.28f, StillMoon.centreY(w, h, SUN_CLOUD_HEIGHT_MAX) / h, 0.005f)
        var last = Float.MAX_VALUE
        for (k in 0..50) {
            val height = SUN_CLOUD_HEIGHT_MIN + (SUN_CLOUD_HEIGHT_MAX - SUN_CLOUD_HEIGHT_MIN) * k / 50f
            for ((sw, sh) in listOf(w to h, 360 to 800, 1080 to 2340, 1440 to 3200)) {
                val cy = StillMoon.centreY(sw, sh, height)
                val r = sw * PaperRenderer.CELESTIAL_RADIUS_FRACTION * 2f
                val label = "${sw}x$sh at $height"
                assertTrue("$label: its top under the cloud band", cy - r >= CloudBand.topFor(sh, height) + CloudBand.heightFor(sh) - 0.5f)
                assertTrue("$label: its foot over the hills' horizon", cy + r < sh * PaperRenderer.HORIZON_Y_FRACTION_OF_ARC)
                // The renderer turns the arc's height back into px as `horizon - y x rise`: the same place.
                val rise = height.coerceIn(SUN_CLOUD_HEIGHT_MIN, SUN_CLOUD_HEIGHT_MAX) * sh
                assertEquals(label, cy, sh * PaperRenderer.HORIZON_Y_FRACTION_OF_ARC - StillMoon.arcY(sw, sh, height) * rise, 0.01f)
            }
            val cy = StillMoon.centreY(w, h, height)
            assertTrue("higher with the slider", cy <= last)
            last = cy
        }
        assertEquals("in the middle across", 0.5f, StillMoon.X, 0f)
        assertTrue(radius > 0f)
    }

    @Test
    fun `scrolled, it moves a little and never leaves the screen`() {
        for (parallax in listOf(0.0, 0.05, 0.12, 0.3)) for (drift in listOf(0.0, 0.25, 0.5, 1.7, 3.3)) for (swipe in listOf(0f, 0.5f, 1f)) {
            val offset = PaperRenderer.celestialParallaxOffset(StillMoon.X, w.toFloat(), parallax, drift, swipe)
            val margin = w * PaperRenderer.CELESTIAL_MARGIN_FRACTION
            val cx = margin + StillMoon.X * (w - 2 * margin) + offset
            val r = w * PaperRenderer.CELESTIAL_RADIUS_FRACTION * 2f
            assertTrue("parallax $parallax drift $drift swipe $swipe: on the screen", cx - r >= -0.5f && cx + r <= w + 0.5f)
        }
        assertTrue("it does move", PaperRenderer.celestialParallaxOffset(StillMoon.X, w.toFloat(), 0.12, 0.5, 1f) < -10f)
    }

    @Test
    fun `with the night by day it stands there at every hour, all of it out`() {
        for (hour in hours) for ((rise, set) in places) {
            val night = NightOverDay.of(SunPositionCalculator.compute(hour, rise, set))
            val label = "%.2f, sun %.2f-%.2f".format(hour, rise, set)
            assertTrue(label, StillMoon.up(night))
            assertEquals(label, 1f, StillMoon.alpha(night, nightOverDay = true), 0f)
            val still = StillMoon.of(night, StillMoon.arcY(w, h, 0.42f))
            assertEquals(label, StillMoon.X, still.celestialX, 0f)
            assertEquals(label, StillMoon.arcY(w, h, 0.42f), still.celestialY, 0f)
            assertFalse(label, still.isSunVisible)
            assertEquals("$label: the hour kept", night.hour24, still.hour24, 0f)
            assertEquals("$label: the light kept", night.dayBlend, still.dayBlend, 0f)
            assertEquals(label, night.progress, still.progress, 0f)
        }
    }

    /**
     * Without the night by day the sun keeps the day: where the sun is up the moon is not (the phase is the sun's), and the
     * moon comes out where it stands after sunset -- from nothing, growing as the light goes -- and fades before sunrise,
     * to nothing as the sun comes up: never the two together, and no jump in between.
     */
    @Test
    fun `without the night by day it comes out after sunset and fades before sunrise, never with the sun`() {
        for ((rise, set) in places) {
            var lastAlpha = -1f
            var lastUp = false
            var steps = 0
            var growing = 0
            var t = 0f
            while (t < 24f) {
                val phase = SunPositionCalculator.compute(t, rise, set)
                val up = StillMoon.up(phase)
                val alpha = if (up) StillMoon.alpha(phase, nightOverDay = false) else 0f
                val label = "%.3f, sun %.2f-%.2f".format(t, rise, set)
                assertEquals("$label: the moon is up exactly where the sun is not", !phase.isSunVisible, up)
                if (lastAlpha >= 0f) {
                    // A minute's change of light, at most: it comes out and fades, it does not jump.
                    assertTrue("$label: $lastAlpha -> $alpha", kotlin.math.abs(alpha - lastAlpha) <= 0.05f)
                }
                if (up != lastUp && steps > 0) assertTrue("$label: the sun and the moon change places with the moon out of sight ($alpha, $lastAlpha)", alpha < 0.05f && lastAlpha < 0.05f)
                if (up && alpha > lastAlpha + 0.001f) growing++
                if (phase.dayBlend <= 0f) assertEquals("$label: all of it out at night", 1f, alpha, 0f)
                lastAlpha = alpha
                lastUp = up
                steps++
                t += 1f / 60f
            }
            assertTrue("it came out by degrees: $growing", growing > 20)
        }
        // Half out at half the light that hides it: `1 - dayBlend / 0.5`.
        for ((blend, out) in listOf(0f to 1f, 0.125f to 0.75f, 0.25f to 0.5f, 0.375f to 0.25f, 0.5f to 0f, 0.8f to 0f)) {
            val phase = SunPositionCalculator.DayPhase(progress = 0.8f, dayBlend = blend, celestialX = 0.9f, celestialY = 0.1f, isSunVisible = false, hour24 = 21f, moonPhase = 0.5f)
            assertEquals("dayBlend $blend", out, StillMoon.alpha(phase, nightOverDay = false), 1e-6f)
            assertEquals("all out with the night by day", 1f, StillMoon.alpha(phase, nightOverDay = true), 0f)
        }
    }

    /**
     * **Off, the moon crosses the sky as before; and in every order of the frames** ([StillMoonFrame], the renderer's own):
     * out of force or with the sun up, the frame's own phase and the moon whole -- turned off at dusk with the moon half out,
     * the next frame has it whole and on its arc --; in force, one phase made for each phase the engine hands it, made again
     * when the slider or the screen moves its place, the moon's phase of the month kept current.
     */
    @Test
    fun `off, the moon crosses the sky as before`() {
        val frame = StillMoonFrame()
        val dusk = SunPositionCalculator.compute(hour24 = 20.6f)
        val noon = SunPositionCalculator.compute(hour24 = 12f)
        assertTrue("its own phase off", frame.phaseFor(dusk, active = false, nightOverDay = false, w, h, 0.42f) === dusk)
        assertEquals(1f, frame.alpha, 0f)
        val half = frame.phaseFor(dusk, active = true, nightOverDay = false, w, h, 0.42f)
        assertEquals("half out at dusk", 0.5f, frame.alpha, 0.05f)
        assertEquals(StillMoon.X, half.celestialX, 0f)
        assertTrue("one phase made for one the engine gives", frame.phaseFor(dusk, true, false, w, h, 0.42f) === half)
        assertTrue("turned off at dusk: its own phase", frame.phaseFor(dusk, active = false, nightOverDay = false, w, h, 0.42f) === dusk)
        assertEquals("and the moon whole again, not left half out", 1f, frame.alpha, 0f)
        assertTrue("the sun up: its own phase", frame.phaseFor(noon, active = true, nightOverDay = false, w, h, 0.42f) === noon)
        assertEquals(1f, frame.alpha, 0f)
        val placed = frame.phaseFor(dusk, true, false, w, h, 0.42f)
        val higher = frame.phaseFor(dusk, true, false, w, h, 0.6f)
        assertTrue("the slider moved: its place again", higher.celestialY != placed.celestialY)
        assertEquals(StillMoon.arcY(w, h, 0.6f), higher.celestialY, 0f)
        assertEquals("another screen: its place there", StillMoon.arcY(1080, 2340, 0.6f), frame.phaseFor(dusk, true, false, 1080, 2340, 0.6f).celestialY, 0f)
        dusk.moonPhase = 0.25f
        assertEquals("the month's phase kept current", 0.25f, frame.phaseFor(dusk, true, false, 1080, 2340, 0.6f).moonPhase, 0f)
        frame.phaseFor(dusk, active = true, nightOverDay = true, w, h, 0.42f)
        assertEquals("all out with the night by day", 1f, frame.alpha, 0f)
        // The renderer draws every layer with it, by the settings' rules, and as much of the moon as it says.
        val source = File(sourceRoot(), "engine/PaperRenderer.kt").readLines().map { it.substringBefore("//").trim() }
        val call = source.indexOf("stillMoonFrame.phaseFor(")
        assertTrue("the renderer's still moon", call >= 0)
        assertEquals(
            "with the frame, the settings' rule, the night by day, the screen and the slider",
            listOf("phase,", "sceneCustomization.stillMoonActive(),", "sceneCustomization.nightOverDayActive(),", "screenWidth,", "screenHeight,", "sceneCustomization.sky.sunCloudHeight,"),
            source.subList(call + 1, call + 7),
        )
        assertTrue("every layer is drawn with it", "val dayPhase = stillMoon(nightOverDay(framePhase))" in source)
        // As much of it out as the rule says: the disc and its light, its glow, and its light on the lake.
        assertTrue("the moon's sprites", "val moonAlpha = (255f * stillMoonFrame.alpha).toInt()" in source)
        assertEquals("the disc, and its light still or laughing", 3, source.count { it == "moonAlpha," })
        assertTrue("its glow", source.any { it.startsWith("(StormAtmosphere.sunAlpha(CELESTIAL_GLOW_CENTRE_ALPHA, sunStorm) * (if (isSun) 1f else stillMoonFrame.alpha)).toInt(),") })
        assertEquals("the lake's two lights", 2, source.count { it.contains("stillMoonFrame.alpha") && (it.contains("dayPhase.dayBlend") || it.contains("sqrt")) })
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            assertEquals("${theme.id}: on as the Halloween theme ships, off on every other", theme.id == "halloween", c.stillMoonEnabled)
            assertEquals(theme.id, theme.id == "halloween", c.stillMoonActive())
        }
    }

    private fun sourceRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper")
                if (f.isDirectory) return f
            }
            dir = dir.parentFile
        }
        throw AssertionError("source root not found")
    }
}
