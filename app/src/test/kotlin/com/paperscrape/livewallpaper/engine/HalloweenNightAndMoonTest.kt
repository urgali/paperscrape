package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.ui.SettingsUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Halloween's night sky by day and the laughing moon** (v5.16): the maintainer's *«va bene 3 ma deve diventare il nuovo
 * default del tema halloween»* and *«2 risata (consigliata)»* (2026-10-21).
 *
 * The night by day in every combination of what it needs -- *Halloween*, *Moon*, its own switch -- and of what it lives
 * beside -- *Show Sun*, *Horror sky*, the real hour or a fixed one, the dawn and the dusk --: in force exactly when its
 * three are on, a full night then at every hour that is not night already, with the clock's hour kept for the opening
 * hours; the switches it stops say so. The laugh on the scene's clock: once a minute for two seconds.
 */
class HalloweenNightAndMoonTest {

    private val hours = listOf(0f, 5.5f, 6f, 6.5f, 9f, 12f, 15f, 19.5f, 20f, 20.5f, 23f)

    @Test
    fun `the night by day is in force only with Halloween, the moon and its own switch, whatever the sun and the sky`() {
        val base = defaultCustomizationFor("autumn")
        for (halloween in listOf(false, true)) for (moon in listOf(false, true)) for (own in listOf(false, true))
            for (sun in listOf(false, true)) for (horror in listOf(false, true)) {
                val c = base.copy(
                    halloweenEnabled = halloween, nightSkyByDayEnabled = own, horrorSkyEnabled = horror,
                    moon = base.moon.copy(visible = moon), sun = base.sun.copy(visible = sun),
                )
                val label = "halloween $halloween moon $moon own $own sun $sun horror $horror"
                assertEquals(label, halloween && moon && own, c.nightOverDayActive())
                val row = SettingsUiModel.nightSkyByDay(c)
                assertEquals(label, own && halloween && moon, row.shownOn)
                assertEquals(label, halloween && moon, row.interactive)
                assertEquals(label, !halloween, row.needsHalloween)
                assertEquals(label, halloween && !moon, row.needsMoon)
                val sunRow = SettingsUiModel.showSun(c)
                assertEquals("$label: Show Sun reads off with the reason while it is night all day", sun && !c.nightOverDayActive(), sunRow.shownOn)
                assertEquals(label, !c.nightOverDayActive(), sunRow.interactive)
            }
    }

    /**
     * *Show Birds* and *Night Birds* under the night by day never wait for each other (v5.16B's final review): *Show Birds*
     * is locked only while the birds are on and *Night Birds* off, and then *Night Birds* -- available while the birds are on
     * (`WorldSceneScreen`) -- is the way out; with the birds off *Show Birds* stays a switch.
     */
    @Test
    fun `under the night by day the birds' two switches always leave a way out`() {
        val base = defaultCustomizationFor("halloween")
        for (inForce in listOf(false, true)) for (visible in listOf(false, true)) for (night in listOf(false, true)) {
            val c = base.copy(
                nightSkyByDayEnabled = inForce, halloweenEnabled = true, moon = base.moon.copy(visible = true),
                birds = base.birds.copy(visible = visible, density = 0.5f, nightBirds = night),
            )
            val label = "in force $inForce, birds $visible, night birds $night"
            val show = SettingsUiModel.showBirds(c)
            val nightBirdsAvailable = SettingsUiModel.amountSwitch(c.birds.visible, c.birds.density).shownOn
            assertEquals(label, inForce && visible && !night, !show.interactive)
            assertEquals(label, visible && (!inForce || night), show.shownOn)
            if (!show.interactive) assertTrue("$label: locked with no way out", nightBirdsAvailable)
            if (!nightBirdsAvailable) assertTrue("$label: Night Birds waits for Show Birds, which waits for it", show.interactive)
        }
        val screen = source("ui/WorldSceneScreen.kt")
        assertTrue(screen.contains("SettingsUiModel.dependentSwitch(customization.birds.nightBirds, available = birds.shownOn)"))
        assertTrue(screen.contains("val birds = SettingsUiModel.amountSwitch(customization.birds.visible, customization.birds.density)"))
    }

    @Test
    fun `in force, every hour that is not night is a full night with the clock's own hour, and the night is left as it is`() {
        for (hour in hours) for ((sunrise, sunset) in listOf(6f to 20f, 7.25f to 17.5f)) {
            val phase = SunPositionCalculator.compute(hour, sunrise, sunset)
            val night = NightOverDay.of(phase)
            val label = "%.2f, sun %.2f-%.2f".format(hour, sunrise, sunset)
            if (phase.dayBlend <= 0f && !phase.isSunVisible) {
                assertSame("$label: the night already is", phase, night)
                continue
            }
            assertEquals(label, 0f, night.dayBlend, 0f)
            assertFalse(label, night.isSunVisible)
            assertEquals("$label: the opening hours keep the clock's hour", phase.hour24, night.hour24, 0f)
            assertEquals(label, phase.progress, night.progress, 0f)
            assertEquals("$label: the moon on the sun's path", phase.celestialX, night.celestialX, 0f)
            assertEquals(label, phase.celestialY, night.celestialY, 0f)
        }
    }

    @Test
    fun `the renderer draws every layer and the business hours with the one phase`() {
        val renderer = source("engine/PaperRenderer.kt")
        val draw = renderer.substringAfter("    fun draw(canvas: SceneCanvas, framePhase: SunPositionCalculator.DayPhase").substringBefore("\n    }\n")
        assertTrue(draw.contains("val dayPhase = stillMoon(nightOverDay(framePhase))"))
        assertEquals("framePhase is read once, by the night by day and the still moon", 1, Regex("""framePhase\b""").findAll(draw).count())
        assertTrue("the opening hours read the phase's hour, the clock's", draw.contains("screenHeight.toFloat(), dayPhase.hour24)"))
        // And the night by day is the one the settings say: the renderer's gate is the same function.
        assertTrue(renderer.contains("if (!sceneCustomization.nightOverDayActive()) return phase"))
    }

    @Test
    fun `the Halloween theme ships it on, so its card is night, and no other theme does`() {
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            assertEquals(theme.id, theme.id == "halloween", c.nightSkyByDayEnabled)
        }
        // A theme with it in force is night on its card at every hour, as on its wallpaper.
        val autumn = defaultCustomizationFor("autumn").copy(halloweenEnabled = true, nightSkyByDayEnabled = true)
        val card = ThemePreviewScenes.forTheme(ThemeCatalog.byId("autumn"), autumn)
        val day = ThemePreviewScenes.forTheme(ThemeCatalog.byId("autumn"), defaultCustomizationFor("autumn"))
        assertTrue("the night card's sky is darker than the day's", luma(card.skyTop) < luma(day.skyTop))
        // And the World & scene strip's "Day" with it in force is the night too: the wallpaper never shows that day
        // (until the mid-round review of v5.16B it drew one).
        val halloween = ThemeCatalog.byId("halloween")
        val shipped = defaultCustomizationFor("halloween")
        assertEquals(ThemePreviewScenes.forTheme(halloween, shipped, forceNight = true).skyTop, ThemePreviewScenes.forTheme(halloween, shipped, forceNight = false).skyTop)
        val off = shipped.copy(nightSkyByDayEnabled = false, horrorSkyEnabled = false)
        assertTrue("with it off the strip's Day is day", luma(ThemePreviewScenes.forTheme(halloween, off, forceNight = false).skyTop) > luma(ThemePreviewScenes.forTheme(halloween, off, forceNight = true).skyTop))
    }

    @Test
    fun `the moon laughs once a minute for two seconds, on the scene's clock`() {
        assertFalse(LaughingMoon.laughing(SceneTime(0.0)))
        assertTrue(LaughingMoon.laughing(SceneTime(LaughingMoon.FIRST_SECONDS + 0.5)))
        var laughingSeconds = 0.0
        var faceSeconds = 0.0
        var laughs = 0
        var was = false
        val step = 0.01
        var t = 0.0
        while (t < 600.0) {
            val now = LaughingMoon.laughing(SceneTime(t))
            if (now) laughingSeconds += step
            if (now && !was) laughs++
            was = now
            if (LaughingMoon.faceOpen(SceneTime(t))) faceSeconds += step
            t += step
        }
        assertEquals("ten minutes: ten laughs", 10, laughs)
        assertEquals("ten minutes: ten laughs of two seconds", 20.0, laughingSeconds, 0.05)
        assertTrue("the face open for most of each", faceSeconds in 16.5..18.5)
        for (sec in listOf(6.2, 7.0, 7.9)) {
            val shake = LaughingMoon.shakeDegrees(SceneTime(sec))
            assertTrue("shaking at most ${LaughingMoon.SHAKE_DEGREES}°", kotlin.math.abs(shake) <= LaughingMoon.SHAKE_DEGREES)
            val hop = LaughingMoon.hop(SceneTime(sec))
            assertTrue("hopping up, at most ${LaughingMoon.HOP_OF_RADIUS} of the radius", hop <= 0f && hop >= -LaughingMoon.HOP_OF_RADIUS)
        }
        // And it does shake and hop while it laughs, not only stays within the bounds.
        assertTrue("shaking", (60..79).any { kotlin.math.abs(LaughingMoon.shakeDegrees(SceneTime(it / 10.0))) > 1f })
        assertTrue("hopping", (60..79).any { LaughingMoon.hop(SceneTime(it / 10.0)) < -0.01f })
        assertEquals(0f, LaughingMoon.shakeDegrees(SceneTime(30.0)), 0f)
        assertEquals(0f, LaughingMoon.hop(SceneTime(30.0)), 0f)
    }

    @Test
    fun `the laugh needs the carved moon up, and its switch says so`() {
        val base = defaultCustomizationFor("autumn")
        for (halloween in listOf(false, true)) for (moon in listOf(false, true)) for (own in listOf(false, true)) {
            val c = base.copy(halloweenEnabled = halloween, laughingMoonEnabled = own, moon = base.moon.copy(visible = moon))
            assertEquals(halloween && moon && own, c.laughingMoonActive())
            val row = SettingsUiModel.laughingMoon(c)
            assertEquals(halloween && moon && own, row.shownOn)
            assertEquals(halloween && moon, row.interactive)
        }
        // The renderer laughs by the same function, and never on a card.
        assertTrue(source("engine/PaperRenderer.kt").contains("sceneCustomization.laughingMoonActive() && LaughingMoon.laughing(frameTime)"))
        assertFalse(source("engine/ThemePreviewScene.kt").contains("moon_jack_o_lantern_laugh"))
    }

    private fun luma(argb: Int): Float =
        0.299f * ((argb shr 16) and 0xFF) + 0.587f * ((argb shr 8) and 0xFF) + 0.114f * (argb and 0xFF)

    private fun source(path: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/$path")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        throw AssertionError("$path not found")
    }
}
