package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The automatic day/night colour rule, and the promises the settings screen makes about it.
 *
 * The transform is deliberately testable without Android: [DayNightColor] imports nothing from the
 * platform precisely so that the rule the wallpaper draws, the rule the theme cards draw and the
 * rule asserted here are the same code rather than three that agree today.
 *
 * The assertions are mostly **relations** rather than pinned values -- "a night colour is darker",
 * "the hue survives", "black stays black" -- because the two factors are a design decision that
 * will be re-tuned. The three that *are* pinned are pinned on purpose and say why.
 */
class DayNightColorTest {

    private fun rgb(color: Int) = Triple(color shr 16 and 0xFF, color shr 8 and 0xFF, color and 0xFF)
    private fun lightness(color: Int) = DayNightColor.lightnessOf(color)
    private fun chroma(color: Int) = DayNightColor.chromaOf(color)
    private fun hue(color: Int) = DayNightColor.hueOf(color)

    private val white = 0xFFFFFFFF.toInt()
    private val black = 0xFF000000.toInt()
    private val red = 0xFFE53935.toInt()
    private val green = 0xFF43A047.toInt()
    private val blue = 0xFF1E88E5.toInt()
    private val yellow = 0xFFFDD835.toInt()

    /** Every colour a user might realistically drag a picker to, including the corners. */
    private val spread = listOf(
        white, black, red, green, blue, yellow,
        0xFFFF0000.toInt(), 0xFF00FF00.toInt(), 0xFF0000FF.toInt(),
        0xFFE0E0E0.toInt(), 0xFF808080.toInt(), 0xFF202020.toInt(),
        0xFFF7FAFC.toInt(), 0xFF2E2A55.toInt(), 0xFFFB8C00.toInt(),
    )

    // --- Manual: the behaviour that shipped before this existed ------------------------------

    @Test
    fun `manual returns the pair untouched`() {
        val (day, night) = DayNightColor.resolve(red, blue, AutoColorMode.MANUAL)
        assertEquals(red, day)
        assertEquals(blue, night)
    }

    @Test
    fun `a customization with every pair manual is returned as the same instance`() {
        // Not merely equal: the resolver short-circuits per config, so a default customization must
        // cost nothing at all. This is what keeps the feature free for users who never open it, and
        // what keeps MANUAL byte-identical to the release before the transform changed.
        val defaults = SceneCustomization.DEFAULT
        assertEquals(defaults, defaults.withResolvedDayNightColors())
        assertSame(defaults.houses, defaults.withResolvedDayNightColors().houses)
        assertSame(defaults.sky, defaults.withResolvedDayNightColors().sky)
    }

    // --- Automatic, both directions -----------------------------------------------------------

    @Test
    fun `from day derives the night half and leaves the day half alone`() {
        val (day, night) = DayNightColor.resolve(red, blue, AutoColorMode.FROM_DAY)
        assertEquals("the source half is the user's and must not move", red, day)
        assertNotEquals(blue, night)
        assertEquals(DayNightColor.nightFromDay(red), night)
    }

    @Test
    fun `from night derives the day half and leaves the night half alone`() {
        val (day, night) = DayNightColor.resolve(red, blue, AutoColorMode.FROM_NIGHT)
        assertEquals("the source half is the user's and must not move", blue, night)
        assertEquals(DayNightColor.dayFromNight(blue), day)
    }

    // --- The rule itself ----------------------------------------------------------------------

    @Test
    fun `every night colour is markedly darker than the day it came from`() {
        // "Markedly" is the whole point of the v4.13 rewrite: v4.12 was reported as producing night
        // colours that still read as daytime ones. Anything above 0.6 of the original lightness is
        // that failure coming back.
        for (day in spread) {
            val night = DayNightColor.nightFromDay(day)
            val before = lightness(day)
            if (before < 1f) continue
            val ratio = lightness(night) / before
            assertTrue("$day: night must be darker, was ratio $ratio", ratio < 0.6f)
        }
    }

    @Test
    fun `white goes clearly darker and clearly cooler`() {
        val night = DayNightColor.nightFromDay(white)
        val (r, g, b) = rgb(night)
        assertTrue("white must lose at least a third of its lightness", lightness(night) < 66f)
        assertTrue("and it must not stay a neutral grey: blue should lead", b > r)
        assertTrue("but it must not become blue paint either", chroma(night) < 20f)
    }

    @Test
    fun `a bright red goes to a deep red rather than to brown or magenta`() {
        val night = DayNightColor.nightFromDay(red)
        val (r, g, b) = rgb(night)
        assertTrue("red must stay the dominant channel", r > g && r > b)
        assertTrue("it must be markedly darker", lightness(night) < lightness(red) * 0.6f)
        assertTrue("and still be colourful, not a brown", chroma(night) > 25f)
        // The v4.12 failure this replaces: chroma pushed out of gamut and clipped, dragging the
        // hue towards magenta. Gamut mapping trades saturation, never hue.
        assertEquals("the hue must survive", hue(red), hue(night), 12f)
    }

    @Test
    fun `nothing collapses to grey`() {
        for (day in spread) {
            if (chroma(day) < 5f) continue
            val night = DayNightColor.nightFromDay(day)
            assertTrue("$day lost all its colour: chroma ${chroma(night)}", chroma(night) > 4f)
        }
    }

    @Test
    fun `nothing collapses to black`() {
        for (day in spread) {
            if (lightness(day) < 15f) continue
            val night = DayNightColor.nightFromDay(day)
            assertTrue("$day went black", lightness(night) > 4f)
        }
    }

    @Test
    fun `hue survives in both directions`() {
        for (source in spread) {
            if (chroma(source) < 8f) continue
            assertEquals("night hue drifted", hue(source), hue(DayNightColor.nightFromDay(source)), 15f)
            assertEquals("day hue drifted", hue(source), hue(DayNightColor.dayFromNight(source)), 15f)
        }
    }

    @Test
    fun `darkening is monotone in lightness`() {
        // A lighter colour must not produce a darker night than a darker one of the same hue,
        // or a gradient would fold over on itself as the sun goes down.
        val ramp = listOf(0xFF202020, 0xFF505050, 0xFF808080, 0xFFB0B0B0, 0xFFE0E0E0, 0xFFFFFFFF)
            .map { it.toInt() }
        val nights = ramp.map { lightness(DayNightColor.nightFromDay(it)) }
        for (i in 1 until nights.size) {
            assertTrue("the ramp folded at index $i: $nights", nights[i] > nights[i - 1])
        }
    }

    @Test
    fun `the two directions are reasonably reciprocal`() {
        // Gamut mapping is not invertible, so this is a lightness round trip rather than an exact
        // one. Anything worse than a couple of L* units would make the toggle feel lossy.
        for (day in spread) {
            val back = DayNightColor.dayFromNight(DayNightColor.nightFromDay(day))
            assertEquals("round trip lost lightness for $day", lightness(day), lightness(back), 2.5f)
        }
    }

    // --- Edge cases ---------------------------------------------------------------------------

    @Test
    fun `black stays black in both directions`() {
        assertEquals(black, DayNightColor.nightFromDay(black))
        // Brightening zero cannot invent light, and an unlit thing must not acquire a colour cast
        // because the sun went down -- which is why the blue shift is scaled by lightness.
        assertEquals(black, DayNightColor.dayFromNight(black))
    }

    @Test
    fun `white cannot be brightened past white`() {
        assertEquals(white, DayNightColor.dayFromNight(white))
    }

    @Test
    fun `greys stay neutral apart from the deliberate cool shift`() {
        for (grey in listOf(0xFF202020.toInt(), 0xFF808080.toInt(), 0xFFD0D0D0.toInt())) {
            val night = DayNightColor.nightFromDay(grey)
            val (r, _, b) = rgb(night)
            assertTrue("a grey must not warm up", b >= r)
            assertTrue("and must not turn into a colour", chroma(night) < 20f)
        }
    }

    @Test
    fun `fully saturated primaries stay themselves`() {
        for ((name, c) in listOf("red" to 0xFFFF0000.toInt(), "green" to 0xFF00FF00.toInt(), "blue" to 0xFF0000FF.toInt())) {
            val night = DayNightColor.nightFromDay(c)
            assertEquals("$name changed hue", hue(c), hue(night), 15f)
            assertTrue("$name did not darken", lightness(night) < lightness(c))
        }
    }

    @Test
    fun `alpha is carried through untouched`() {
        val translucent = 0x80E53935.toInt()
        assertEquals(0x80, DayNightColor.nightFromDay(translucent) ushr 24 and 0xFF)
        assertEquals(0x80, DayNightColor.dayFromNight(translucent) ushr 24 and 0xFF)
    }

    @Test
    fun `every result is a valid opaque colour`() {
        for (day in spread) {
            for (c in listOf(DayNightColor.nightFromDay(day), DayNightColor.dayFromNight(day))) {
                val (r, g, b) = rgb(c)
                assertTrue("channel out of range in ${Integer.toHexString(c)}",
                    r in 0..255 && g in 0..255 && b in 0..255)
                assertEquals(0xFF, c ushr 24 and 0xFF)
            }
        }
    }

    @Test
    fun `the transform is deterministic`() {
        repeat(3) { assertEquals(DayNightColor.nightFromDay(red), DayNightColor.nightFromDay(red)) }
    }

    @Test
    fun `the factors are the ones the design brief settled on`() {
        // Pinned so that changing them is a decision rather than a slip. They are not fitted to the
        // authored pairs -- see the KDoc for why that fit is the thing v4.12 got wrong -- so a
        // future change means looking at a device again, not re-running a regression.
        assertEquals(0.50f, DayNightColor.NIGHT_LIGHTNESS_FACTOR, 1e-6f)
        assertEquals(0.80f, DayNightColor.NIGHT_CHROMA_FACTOR, 1e-6f)
        assertEquals(6.0f, DayNightColor.NIGHT_BLUE_SHIFT, 1e-6f)
        assertEquals(
            "half the perceptual lightness, by construction",
            lightness(0xFF808080.toInt()) * 0.50f,
            lightness(DayNightColor.nightFromDay(0xFF808080.toInt())),
            1.5f,
        )
    }

    // --- The whole customization --------------------------------------------------------------

    @Test
    fun `resolving touches every pair-bearing config and nothing else`() {
        val stored = SceneCustomization.DEFAULT.copy(
            houses = SceneCustomization.DEFAULT.houses.copy(
                colorDay1 = red, colorNight1 = blue, autoMode1 = AutoColorMode.FROM_DAY,
            ),
            lake = SceneCustomization.DEFAULT.lake.copy(
                colorDay = green, colorNight = blue, autoMode = AutoColorMode.FROM_NIGHT,
            ),
            hillsColorDay = red, hillsColorNight = blue, hillsAutoMode = AutoColorMode.FROM_DAY,
        )
        val drawn = stored.withResolvedDayNightColors()

        assertEquals(red, drawn.houses.colorDay1)
        assertEquals(DayNightColor.nightFromDay(red), drawn.houses.colorNight1)
        assertEquals("variant 2 is a separate pair and was left manual", stored.houses.colorNight2, drawn.houses.colorNight2)
        assertEquals(blue, drawn.lake.colorNight)
        assertEquals(DayNightColor.dayFromNight(blue), drawn.lake.colorDay)
        assertEquals(DayNightColor.nightFromDay(red), drawn.hillsColorNight)
        assertEquals("an untouched category must come through unchanged", stored.trees, drawn.trees)
    }

    @Test
    fun `resolving never writes back, so switching to manual restores what the user chose`() {
        val stored = SceneCustomization.DEFAULT.copy(
            houses = SceneCustomization.DEFAULT.houses.copy(
                colorDay1 = red, colorNight1 = blue, autoMode1 = AutoColorMode.FROM_DAY,
            ),
        )
        assertNotEquals(blue, stored.withResolvedDayNightColors().houses.colorNight1)
        assertEquals(blue, stored.houses.colorNight1)
        val backToManual = stored.copy(houses = stored.houses.copy(autoMode1 = AutoColorMode.MANUAL))
        assertEquals(blue, backToManual.withResolvedDayNightColors().houses.colorNight1)
    }

    @Test
    fun `resolving is idempotent for the half that is not derived`() {
        val stored = SceneCustomization.DEFAULT.copy(
            clouds = SceneCustomization.DEFAULT.clouds.copy(
                colorDay = red, colorNight = blue, autoMode = AutoColorMode.FROM_DAY,
            ),
        )
        val once = stored.withResolvedDayNightColors()
        val twice = once.withResolvedDayNightColors()
        assertEquals(once.clouds.colorDay, twice.clouds.colorDay)
        assertEquals(once.clouds.colorNight, twice.clouds.colorNight)
    }

    @Test
    fun `every mode round-trips through its storage id`() {
        for (mode in AutoColorMode.entries) {
            assertEquals(mode, AutoColorMode.fromStorageId(mode.storageId))
        }
        assertEquals("an absent key is the old behaviour", AutoColorMode.MANUAL, AutoColorMode.fromStorageId(null))
        assertEquals("and so is one nobody recognises", AutoColorMode.MANUAL, AutoColorMode.fromStorageId("nonsense"))
    }
}
