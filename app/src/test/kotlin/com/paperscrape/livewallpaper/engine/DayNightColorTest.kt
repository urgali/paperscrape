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
 */
class DayNightColorTest {

    private fun rgb(color: Int) = Triple(color shr 16 and 0xFF, color shr 8 and 0xFF, color and 0xFF)
    private fun lightnessOf(color: Int) = DayNightColor.toHsl(color).third
    private fun saturationOf(color: Int) = DayNightColor.toHsl(color).second
    private fun hueOf(color: Int) = DayNightColor.toHsl(color).first

    private val red = 0xFFE53935.toInt()
    private val green = 0xFF43A047.toInt()
    private val blue = 0xFF1E88E5.toInt()

    // --- Manual: the behaviour that shipped before this existed ------------------------------

    @Test
    fun `manual returns the pair untouched`() {
        val (day, night) = DayNightColor.resolve(red, blue, AutoColorMode.MANUAL)
        assertEquals(red, day)
        assertEquals(blue, night)
    }

    @Test
    fun `a customization with every pair manual is returned as the same instance`() {
        // Not merely equal: the resolver short-circuits per config, and a default customization
        // must cost nothing at all. This is what keeps the feature free for users who never open
        // the toggle.
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

    @Test
    fun `night is darker and calmer than the day it came from`() {
        for (day in listOf(red, green, blue, 0xFF7F7F7F.toInt(), 0xFFFFC107.toInt())) {
            val night = DayNightColor.nightFromDay(day)
            assertTrue("night must be darker than $day", lightnessOf(night) < lightnessOf(day))
            assertTrue("night must be no more saturated", saturationOf(night) <= saturationOf(day) + 1e-3f)
        }
    }

    @Test
    fun `day is lighter than the night it came from`() {
        for (night in listOf(0xFF3A1F1F.toInt(), 0xFF1B2A3A.toInt(), 0xFF2E2A55.toInt())) {
            val day = DayNightColor.dayFromNight(night)
            assertTrue("day must be lighter than $night", lightnessOf(day) > lightnessOf(night))
        }
    }

    @Test
    fun `hue survives the trip in both directions`() {
        // The measured rule keeps hue: a night palette that drifted towards blue would put a
        // colour cast on every automatic pair, and this project's own artwork does not do that.
        for (source in listOf(red, green, blue, 0xFFFF00FF.toInt())) {
            assertEquals(hueOf(source), hueOf(DayNightColor.nightFromDay(source)), 0.5f)
            assertEquals(hueOf(source), hueOf(DayNightColor.dayFromNight(source)), 0.5f)
        }
    }

    @Test
    fun `the factors are the ones measured from the project's own pairs`() {
        // Pinned because they are evidence, not taste: 41 authored day/night pairs put the
        // lightness ratio at 0.635 and the saturation ratio at 0.725. Re-measure before changing
        // either, and say so here.
        assertEquals(0.635f, DayNightColor.NIGHT_LIGHTNESS_FACTOR, 1e-6f)
        assertEquals(0.725f, DayNightColor.NIGHT_SATURATION_FACTOR, 1e-6f)
        val day = 0xFF808080.toInt()
        assertEquals(
            lightnessOf(day) * DayNightColor.NIGHT_LIGHTNESS_FACTOR,
            lightnessOf(DayNightColor.nightFromDay(day)),
            0.01f,
        )
    }

    // --- Edge cases ---------------------------------------------------------------------------

    @Test
    fun `black stays black in both directions`() {
        val black = 0xFF000000.toInt()
        assertEquals(black, DayNightColor.nightFromDay(black))
        // Brightening zero cannot invent light. Predictable beats clever: a user who picks black
        // and switches on "night sets day" gets black, not a surprise grey.
        assertEquals(black, DayNightColor.dayFromNight(black))
    }

    @Test
    fun `white darkens to a neutral grey and does not overshoot coming back`() {
        val white = 0xFFFFFFFF.toInt()
        val night = DayNightColor.nightFromDay(white)
        assertEquals(0f, saturationOf(night), 1e-3f)
        assertTrue(lightnessOf(night) in 0.60f..0.66f)
        // Already at the ceiling: brightening white leaves white rather than wrapping round.
        assertEquals(white, DayNightColor.dayFromNight(white))
    }

    @Test
    fun `greys stay grey`() {
        for (grey in listOf(0xFF202020.toInt(), 0xFF808080.toInt(), 0xFFD0D0D0.toInt())) {
            val night = DayNightColor.nightFromDay(grey)
            val (r, g, b) = rgb(night)
            assertEquals("a grey must not gain a hue", r, g)
            assertEquals("a grey must not gain a hue", g, b)
        }
    }

    @Test
    fun `a fully saturated colour stays recognisably itself`() {
        val pureRed = 0xFFFF0000.toInt()
        val night = DayNightColor.nightFromDay(pureRed)
        val (r, g, b) = rgb(night)
        assertTrue("red must stay the dominant channel", r > g && r > b)
        assertTrue("and it must actually be darker", r < 0xFF)
        assertEquals(hueOf(pureRed), hueOf(night), 0.5f)
    }

    @Test
    fun `alpha is carried through untouched`() {
        val translucent = 0x80E53935.toInt()
        assertEquals(0x80, DayNightColor.nightFromDay(translucent) ushr 24 and 0xFF)
        assertEquals(0x80, DayNightColor.dayFromNight(translucent) ushr 24 and 0xFF)
    }

    @Test
    fun `the transform is deterministic`() {
        repeat(3) { assertEquals(DayNightColor.nightFromDay(red), DayNightColor.nightFromDay(red)) }
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
        // What the scene draws while automatic is on.
        assertNotEquals(blue, stored.withResolvedDayNightColors().houses.colorNight1)
        // The stored value is untouched, which is the whole reversibility guarantee...
        assertEquals(blue, stored.houses.colorNight1)
        // ...so turning the toggle off hands back exactly the colour that was picked by hand.
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
