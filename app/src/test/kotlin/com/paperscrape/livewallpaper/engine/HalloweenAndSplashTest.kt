package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The two Halloween flags, and the arithmetic the dolphin's splash is triggered from.
 *
 * **Why the flags need a test at all.** Winter and Christmas hung off one boolean for a whole
 * release, and nothing failed: each was internally consistent, and the only way to see the defect
 * was to want a snowy January without fairy lights and find it unreachable. Halloween and the
 * horror sky are the same shape of thing — a decoration layer and a palette — so the property
 * worth pinning is not that either works but that **all four combinations of the two exist, and
 * that neither reaches any of the existing seasonal flags**.
 *
 * **Why the splash needs one.** Its trigger is derived from the leap's own phase rather than
 * remembered between frames, which is the whole reason it survives a surface change and a resume
 * mid-leap. That derivation is a piece of arithmetic with an off-by-a-half in it, and an off-by-a-
 * half puts the splash at the top of the arc instead of at the water.
 */
class HalloweenAndSplashTest {

    // --- The flags -----------------------------------------------------------------------------

    private fun customization(
        halloween: Boolean = false,
        horrorSky: Boolean = false,
        winter: Boolean = false,
        christmas: Boolean = false,
        fall: Boolean = false,
    ) = defaultCustomizationFor(ThemeCatalog.ALL.first().id).copy(
        halloweenEnabled = halloween,
        horrorSkyEnabled = horrorSky,
        winterColorsEnabled = winter,
        christmasDecorationsEnabled = christmas,
        fallColorsEnabled = fall,
    )

    @Test
    fun `both halloween flags are off by default`() {
        val defaults = SceneCustomization.DEFAULT
        assertFalse("Halloween must be opt-in, like every other seasonal decoration", defaults.halloweenEnabled)
        assertFalse("the horror sky must be opt-in too", defaults.horrorSkyEnabled)
    }

    @Test
    fun `only the halloween theme turns either flag on`() {
        for (theme in ThemeCatalog.ALL.filter { it.id != "halloween" }) {
            val defaults = defaultCustomizationFor(theme.id)
            assertFalse("${theme.id} enables Halloween by default", defaults.halloweenEnabled)
            assertFalse("${theme.id} enables the horror sky by default", defaults.horrorSkyEnabled)
        }
    }

    @Test
    fun `the halloween theme presents as halloween without touching a switch`() {
        val defaults = defaultCustomizationFor("halloween")
        assertTrue("choosing the theme must carve the moon", defaults.halloweenEnabled)
        assertTrue("choosing the theme must blacken the sky", defaults.horrorSkyEnabled)
    }

    @Test
    fun `presetting the two flags is not coupling them`() {
        // The theme seeds both; the user still owns both afterwards. Each has to be reachable in
        // either state from the theme's own starting point, or the preset has quietly become a
        // dependency.
        val seeded = defaultCustomizationFor("halloween")
        assertFalse(seeded.copy(halloweenEnabled = false).halloweenEnabled)
        assertTrue(seeded.copy(halloweenEnabled = false).horrorSkyEnabled)
        assertFalse(seeded.copy(horrorSkyEnabled = false).horrorSkyEnabled)
        assertTrue(seeded.copy(horrorSkyEnabled = false).halloweenEnabled)
    }

    @Test
    fun `the halloween theme leaves winter and christmas alone`() {
        val defaults = defaultCustomizationFor("halloween")
        assertFalse("bare branches are not a snowfall", defaults.winterColorsEnabled)
        assertFalse("October is not December", defaults.christmasDecorationsEnabled)
        assertFalse("bare branches are not autumn leaves", defaults.fallColorsEnabled)
        assertFalse(defaults.santaEnabled)
    }

    @Test
    fun `all four combinations of halloween and horror sky are expressible`() {
        val seen = mutableSetOf<Pair<Boolean, Boolean>>()
        for (halloween in listOf(false, true)) {
            for (horrorSky in listOf(false, true)) {
                val c = customization(halloween = halloween, horrorSky = horrorSky)
                seen += c.halloweenEnabled to c.horrorSkyEnabled
            }
        }
        assertEquals(
            "each flag must be reachable independently of the other, which is the reason there " +
                "are two of them",
            4, seen.size,
        )
    }

    @Test
    fun `halloween reaches none of the existing seasonal flags`() {
        val plain = customization()
        val spooky = customization(halloween = true, horrorSky = true)
        assertEquals(plain.winterColorsEnabled, spooky.winterColorsEnabled)
        assertEquals(plain.christmasDecorationsEnabled, spooky.christmasDecorationsEnabled)
        assertEquals(plain.fallColorsEnabled, spooky.fallColorsEnabled)
        assertEquals(plain.santaEnabled, spooky.santaEnabled)
    }

    @Test
    fun `winter and christmas stay reachable while halloween is on`() {
        val both = customization(halloween = true, winter = true, christmas = true)
        assertTrue(both.halloweenEnabled)
        assertTrue("a snowy Halloween is a scene somebody might want", both.winterColorsEnabled)
        assertTrue(both.christmasDecorationsEnabled)
    }

    @Test
    fun `a theme carries both flags through a json round trip`() {
        val original = customization(halloween = true, horrorSky = true)
        val restored = sceneCustomizationFromJson(
            org.json.JSONObject(original.toJson().toString()),
        )
        assertTrue(restored.halloweenEnabled)
        assertTrue(restored.horrorSkyEnabled)
    }

    @Test
    fun `a theme saved before halloween existed loads with both flags off`() {
        val payload = org.json.JSONObject(customization().toJson().toString())
        payload.remove("halloweenEnabled")
        payload.remove("horrorSkyEnabled")
        val restored = sceneCustomizationFromJson(payload)
        assertFalse(restored.halloweenEnabled)
        assertFalse(restored.horrorSkyEnabled)
    }

    // --- The horror sky's own colours ----------------------------------------------------------

    /**
     * The carved face is drawn **only** while the flag is on, and off the same switch that strips
     * the tree crowns.
     *
     * v4.17 added `pumpkin_face` without adding a setting for it: `halloweenEnabled` already
     * existed, was already per-theme and was already in the backups. That is only true while the
     * blit stays inside that branch -- a face drawn unconditionally would be a pumpkin that is
     * always a lantern and a flag that no longer says what it says. Read off the renderer rather
     * than asserted about a constant, because the property is where the call sits.
     */
    @Test
    fun `the carved face is drawn only under the halloween flag`() {
        val body = rendererSource().readText()
            .substringAfter("private fun drawPumpkin(")
            .substringBefore("\n    private fun ")
        val faceAt = body.indexOf("R.drawable.pumpkin_face")
        val gateAt = body.indexOf("customization.halloweenEnabled")
        assertTrue("drawPumpkin must blit the face", faceAt >= 0)
        assertTrue("drawPumpkin must read the halloween flag", gateAt >= 0)
        assertTrue("the face must sit inside the halloween branch", gateAt < faceAt)
        // And on the body's own origin, which is what keeps the two registered without a second
        // constant to keep in step.
        assertTrue(
            "the face must be blitted at the body's own origin",
            body.contains("R.drawable.pumpkin_body, -19f, -30f") &&
                body.contains("R.drawable.pumpkin_face, -19f, -30f"),
        )
    }

    private fun rendererSource(): java.io.File {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = java.io.File(dir, "$prefix$suffix")
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate $suffix")
    }

    @Test
    fun `the horror sky is dark overhead and warm at the horizon at every hour`() {
        for (lift in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val top = blend(PaperRenderer.HORROR_SKY_TOP_NIGHT, PaperRenderer.HORROR_SKY_TOP_DAY, lift)
            val low = blend(PaperRenderer.HORROR_SKY_LOW_NIGHT, PaperRenderer.HORROR_SKY_LOW_DAY, lift)
            assertTrue("the top of the horror sky must stay near black at lift=$lift", luminance(top) < 0.14f)
            assertTrue("the horizon must stay bright at lift=$lift", luminance(low) > 0.25f)
            assertTrue(
                "the horizon must read as orange at lift=$lift, not as a neutral glow",
                red(low) > green(low) && green(low) > blue(low),
            )
        }
    }

    @Test
    fun `the horror sky still changes across a day`() {
        val night = blend(PaperRenderer.HORROR_SKY_LOW_NIGHT, PaperRenderer.HORROR_SKY_LOW_DAY, 0f)
        val day = blend(PaperRenderer.HORROR_SKY_LOW_NIGHT, PaperRenderer.HORROR_SKY_LOW_DAY, 1f)
        assertNotEquals(
            "a sky that never changed would stop the sun and the moon meaning anything",
            night, day,
        )
    }

    // --- The splash trigger --------------------------------------------------------------------

    /** The renderer's own expression, restated: the leap angle as a position in a 0..1 cycle. */
    private fun cyclePosition(seconds: Float, phase: Float): Float {
        val rate = PaperRenderer.DOLPHIN_LEAP_RATE / PaperRenderer.TWO_PI
        val offset = phase * 6.28f / PaperRenderer.TWO_PI
        return ((seconds * rate + offset) % 1.0).toFloat()
    }

    private fun arc(seconds: Float, phase: Float): Float =
        kotlin.math.sin(seconds * PaperRenderer.DOLPHIN_LEAP_RATE + phase * 6.28f).toFloat()

    private fun splashProgress(seconds: Float, phase: Float): Float? {
        val since = cyclePosition(seconds, phase) - 0.5f
        if (since < 0f || since >= PaperRenderer.SPLASH_WINDOW_CYCLES) return null
        return since / PaperRenderer.SPLASH_WINDOW_CYCLES
    }

    /** The renderer's own `when`, restated. */
    private fun splashProgressBoth(seconds: Float, phase: Float): Float? {
        val u = cyclePosition(seconds, phase)
        val w = PaperRenderer.SPLASH_WINDOW_CYCLES
        return when {
            u < w -> u / w
            u >= 0.5f && u < 0.5f + w -> (u - 0.5f) / w
            else -> null
        }
    }

    @Test
    fun `the splash fires on the way out as well as on the way back in`() {
        val phase = 0.23f
        val period = PaperRenderer.TWO_PI / PaperRenderer.DOLPHIN_LEAP_RATE
        val step = period / 8000f
        var previousAbove = arc(0f, phase) > 0f
        var exits = 0
        var entries = 0
        var t = step
        while (t < period * 2f) {
            val above = arc(t, phase) > 0f
            if (above != previousAbove) {
                assertTrue(
                    "a splash must be showing at every crossing of the surface",
                    splashProgressBoth(t, phase) != null,
                )
                if (above) exits++ else entries++
            }
            previousAbove = above
            t += step
        }
        assertEquals("two exits in two periods", 2, exits)
        assertEquals("two re-entries in two periods", 2, entries)
    }

    @Test
    fun `each crossing fires exactly one splash and the two never merge`() {
        val phase = 0.71f
        val period = PaperRenderer.TWO_PI / PaperRenderer.DOLPHIN_LEAP_RATE
        var windows = 0
        var showing = false
        var lastProgress = -1f
        var t = 0f
        while (t < period) {
            val progress = splashProgressBoth(t, phase)
            if (progress != null) {
                if (!showing) windows++
                showing = true
                assertTrue("progress runs forwards inside one window", progress > lastProgress)
                lastProgress = progress
                assertTrue(progress in 0f..1f)
            } else {
                showing = false
                lastProgress = -1f
            }
            t += period / 20000f
        }
        assertEquals("one splash out, one splash in, per leap", 2, windows)
    }

    @Test
    fun `the two windows cannot overlap`() {
        assertTrue(
            "a window as long as half a cycle would make the splash continuous",
            PaperRenderer.SPLASH_WINDOW_CYCLES < 0.5f,
        )
    }

    @Test
    fun `nothing is drawn between the two crossings`() {
        val phase = 0.44f
        val period = PaperRenderer.TWO_PI / PaperRenderer.DOLPHIN_LEAP_RATE
        val w = PaperRenderer.SPLASH_WINDOW_CYCLES
        var t = 0f
        while (t < period * 3f) {
            val u = cyclePosition(t, phase)
            val insideWindow = u < w || (u >= 0.5f && u < 0.5f + w)
            if (!insideWindow) {
                assertEquals(
                    "the splash must not follow the animal across the lake",
                    null, splashProgressBoth(t, phase),
                )
            }
            t += 0.003f
        }
    }

    @Test
    fun `the splash window opens exactly where the dolphin meets the water`() {
        // The leap is drawn while `arc` is positive. The frame before the window opens must still
        // be above water, and the frame the window opens on must be the first one below it.
        val phase = 0.31f
        val period = PaperRenderer.TWO_PI / PaperRenderer.DOLPHIN_LEAP_RATE
        val step = period / 4000f
        var previousAbove = false
        var crossings = 0
        var t = 0f
        while (t < period * 2f) {
            val above = arc(t, phase) > 0f
            if (previousAbove && !above) {
                crossings++
                assertTrue(
                    "the splash must be showing at the instant the animal goes back under",
                    splashProgress(t, phase) != null,
                )
            }
            previousAbove = above
            t += step
        }
        assertEquals("two re-entries in two periods", 2, crossings)
    }

    @Test
    fun `the splash is never drawn while the dolphin is still out of the water`() {
        for (phase in listOf(0f, 0.17f, 0.5f, 0.83f, 1f)) {
            var t = 0f
            while (t < 40f) {
                if (arc(t, phase) > 0f) {
                    assertEquals(
                        "a splash while the animal is airborne would read as a second object",
                        null, splashProgress(t, phase),
                    )
                }
                t += 0.01f
            }
        }
    }

    @Test
    fun `the splash runs once per leap and fades out`() {
        val phase = 0.62f
        val period = PaperRenderer.TWO_PI / PaperRenderer.DOLPHIN_LEAP_RATE
        var windows = 0
        var showing = false
        var lastProgress = -1f
        var t = 0f
        while (t < period) {
            val progress = splashProgress(t, phase)
            if (progress != null) {
                if (!showing) windows++
                showing = true
                assertTrue("progress must run forwards inside one window", progress > lastProgress)
                lastProgress = progress
                assertTrue(progress in 0f..1f)
            } else {
                showing = false
                lastProgress = -1f
            }
            t += 0.002f
        }
        assertEquals("exactly one splash per leap", 1, windows)
    }

    @Test
    fun `the first frame gives way to the second inside the window`() {
        assertTrue(
            "both frames must get some of the window, or one of the two sprites never shows",
            PaperRenderer.SPLASH_FRAME_SPLIT > 0f && PaperRenderer.SPLASH_FRAME_SPLIT < 1f,
        )
    }

    @Test
    fun `the splash is short enough to read as an impact`() {
        val period = PaperRenderer.TWO_PI / PaperRenderer.DOLPHIN_LEAP_RATE
        val seconds = PaperRenderer.SPLASH_WINDOW_CYCLES * period
        assertTrue("a splash lasting $seconds s would sit in the lake", seconds < 0.6f)
        assertTrue("a splash shorter than a couple of frames would flicker", seconds > 0.1f)
    }

    // --- helpers -------------------------------------------------------------------------------

    private fun red(c: Int) = (c ushr 16) and 0xFF
    private fun green(c: Int) = (c ushr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF
    private fun luminance(c: Int) = (0.299f * red(c) + 0.587f * green(c) + 0.114f * blue(c)) / 255f
    private fun blend(from: Int, to: Int, t: Float): Int {
        fun mix(a: Int, b: Int) = (a + (b - a) * t).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or
            (mix(red(from), red(to)) shl 16) or
            (mix(green(from), green(to)) shl 8) or
            mix(blue(from), blue(to))
    }
}
