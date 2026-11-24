package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The snow's colour under the winter palette ([GroundSnow], decision 117) and the autumn crowns' night tone (decision 115)
 * ([SceneObjectRenderer.fallCrownColour]): the rules the wallpaper, the gallery card and the settings all ask.
 */
class GroundSnowTest {

    private fun channels(c: Int) = intArrayOf(c ushr 16 and 0xFF, c ushr 8 and 0xFF, c and 0xFF)

    @Test
    fun `a theme whose hills are snow keeps its own pair`() {
        for (id in listOf("winter", "christmas", "tundra")) {
            val c = defaultCustomizationFor(id)
            assertTrue("$id ships the winter palette", c.winterColorsEnabled)
            assertTrue("$id's ground is snow", GroundSnow.groundIsSnow(c.hillsColorDay))
            assertEquals("$id: the hills by day", c.hillsColorDay, GroundSnow.hillsDay(c))
            assertEquals("$id: the hills by night", c.hillsColorNight, GroundSnow.hillsNight(c))
        }
    }

    @Test
    fun `a theme with no snowy ground takes Winter's`() {
        val winter = defaultCustomizationFor("winter")
        assertEquals("the constant is the Winter theme's ground by day", winter.hillsColorDay, GroundSnow.WINTER_GROUND_DAY)
        assertEquals("and by night", winter.hillsColorNight, GroundSnow.WINTER_GROUND_NIGHT)
        // Autumn with the palette chosen by hand, and New Year, which ships it over a grey ground.
        val autumn = defaultCustomizationFor("autumn").copy(fallColorsEnabled = false, winterColorsEnabled = true)
        val newYear = defaultCustomizationFor("new_year")
        assertTrue(newYear.winterColorsEnabled)
        for ((label, c) in listOf("autumn" to autumn, "new year" to newYear)) {
            assertEquals("$label by day", GroundSnow.WINTER_GROUND_DAY, GroundSnow.hillsDay(c))
            assertEquals("$label by night", GroundSnow.WINTER_GROUND_NIGHT, GroundSnow.hillsNight(c))
            assertEquals("$label's snow at noon", GroundSnow.snowAt(winter, 1f), GroundSnow.snowAt(c, 1f))
            assertEquals("$label's snow at midnight", GroundSnow.snowAt(winter, 0f), GroundSnow.snowAt(c, 0f))
        }
    }

    /**
     * Which built-in grounds the rule reads as snow: the three snow themes' only. Easter's and Spring's light greens are
     * as light, but green: with the palette chosen by hand they take Winter's snow, as every theme without snow does.
     */
    @Test
    fun `the built-in grounds the rule reads as snow`() {
        val snowy = ThemeCatalog.ALL.filter { GroundSnow.groundIsSnow(defaultCustomizationFor(it.id).hillsColorDay) }.map { it.id }.toSet()
        assertEquals(setOf("winter", "christmas", "tundra"), snowy)
        assertFalse("a pale sand is not snow", GroundSnow.groundIsSnow(0xFFEFD9A3.toInt()))
        assertFalse("a light green is not snow", GroundSnow.groundIsSnow(0xFFB8E0A0.toInt()))
    }

    @Test
    fun `the lying snow is the ground a quarter of the way to white`() {
        val winter = defaultCustomizationFor("winter")
        // #27364A at midnight: (39, 54, 74) three quarters of it and a quarter of white, (93, 104, 119).
        assertEquals(0xFF5D6877.toInt(), GroundSnow.snowAt(winter, 0f))
        // #E9F1F7 at noon: (238, 244, 249).
        assertEquals(0xFFEEF4F9.toInt(), GroundSnow.snowAt(winter, 1f))
        // And everywhere between, opaque, never darker than the ground under it.
        for (step in 0..20) {
            val blend = step / 20f
            val snow = GroundSnow.snowAt(winter, blend)
            assertEquals("opaque at $blend", 0xFF, snow ushr 24)
            val ground = SceneColour.blendArgb(winter.hillsColorNight, winter.hillsColorDay, blend)
            val s = channels(snow)
            val g = channels(ground)
            for (k in 0..2) assertTrue("lighter than the ground at $blend", s[k] >= g[k])
        }
        // The settings' pair is the scene's at noon and at midnight.
        assertEquals(GroundSnow.snowAt(winter, 1f), GroundSnow.frontMountainsDay(winter))
        assertEquals(GroundSnow.snowAt(winter, 0f), GroundSnow.frontMountainsNight(winter))
    }

    @Test
    fun `the back mountains are twelve percent darker`() {
        // (93, 104, 119) times 0.88, each channel truncated: (81, 91, 104).
        assertEquals(0xFF515B68.toInt(), GroundSnow.backMountains(0xFF5D6877.toInt()))
        val back = channels(GroundSnow.backMountains(0xFFFFFFFF.toInt()))
        for (k in 0..2) assertEquals(224, back[k])
        assertEquals(0xFF, GroundSnow.backMountains(0x00FFFFFF) ushr 24)
    }

    @Test
    fun `with the winter palette off the hills are the user's`() {
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id).copy(winterColorsEnabled = false)
            assertEquals(theme.id, c.hillsColorDay, GroundSnow.hillsDay(c))
            assertEquals(theme.id, c.hillsColorNight, GroundSnow.hillsNight(c))
        }
        val edited = defaultCustomizationFor("autumn").copy(hillsColorDay = 0xFF102030.toInt(), hillsColorNight = 0xFF010203.toInt())
        assertEquals(0xFF102030.toInt(), GroundSnow.hillsDay(edited))
        assertEquals(0xFF010203.toInt(), GroundSnow.hillsNight(edited))
    }

    @Test
    fun `the card's ground and mountains are the wallpaper's snow under the winter palette`() {
        val autumn = ThemeCatalog.byId("autumn")
        val c = defaultCustomizationFor("autumn").copy(
            fallColorsEnabled = false, winterColorsEnabled = true,
            mountainsBack = defaultCustomizationFor("autumn").mountainsBack.copy(visible = true, density = 1f),
            mountainsFront = defaultCustomizationFor("autumn").mountainsFront.copy(visible = true, density = 1f),
        )
        for (night in listOf(false, true)) {
            val scene = ThemePreviewScenes.forTheme(autumn, c, night)
            val d = ThemePreviewScenes.cardPhase(autumn, night).dayBlend
            val snow = GroundSnow.snowAt(c, d)
            assertEquals(SceneColour.blendArgb(GroundSnow.WINTER_GROUND_NIGHT, GroundSnow.WINTER_GROUND_DAY, d), scene.groundColour)
            assertTrue(scene.peaks.isNotEmpty())
            assertEquals(setOf(snow, GroundSnow.backMountains(snow)), scene.peaks.map { it.colour }.toSet())
            // Every snow on the card -- roofs, caps, the cars' roofs -- multiplied by it.
            val snowParts = (scene.items + scene.cars + scene.ground).flatMap { it.parts }
                .filter { it.resId != 0 && nameOf(it.resId).let { n -> "snow" in n && !n.startsWith("snowman_") } }
            assertTrue("night $night: snow on the card", snowParts.isNotEmpty())
            assertTrue("night $night: the cars' roofs", snowParts.any { nameOf(it.resId).startsWith("car_body_") })
            for (part in snowParts) assertEquals("${nameOf(part.resId)} at night $night", snow, part.tint)
        }
    }

    @Test
    fun `an autumn crown darkens at night as the falling leaves do`() {
        for (tone in SceneObjectRenderer.FALL_LEAF_TREE_PALETTE) {
            assertEquals("by day the tone itself", tone, SceneObjectRenderer.fallCrownColour(tone, 1f))
            val night = SceneObjectRenderer.fallCrownColour(tone, 0f)
            assertEquals("opaque", 0xFF, night ushr 24)
            assertNotEquals("darker at night", tone, night)
            val t = channels(tone)
            val n = channels(night)
            for (k in 0..2) {
                val expected = t[k] * (1f - SceneObjectRenderer.FALL_CROWN_NIGHT_DARKEN)
                assertTrue("channel $k: ${n[k]} for $expected", kotlin.math.abs(n[k] - expected) <= 1f)
            }
            // Between, never lighter than by day nor darker than at night.
            var previous = night
            for (step in 1..10) {
                val now = SceneObjectRenderer.fallCrownColour(tone, step / 10f)
                val p = channels(previous)
                val c = channels(now)
                for (k in 0..2) assertTrue("rises toward day", c[k] >= p[k])
                previous = now
            }
        }
        // The card's crowns wear the same at the card's midnight.
        val autumn = ThemeCatalog.byId("autumn")
        val c = defaultCustomizationFor("autumn")
        val scene = ThemePreviewScenes.forTheme(autumn, c, forceNight = true)
        val d = ThemePreviewScenes.cardPhase(autumn, true).dayBlend
        val crowns = scene.items.flatMap { it.parts }.filter { it.resId == com.paperscrape.livewallpaper.R.drawable.tree_canopy }.map { it.tint }
        assertTrue(crowns.isNotEmpty())
        val allowed = SceneObjectRenderer.FALL_LEAF_TREE_PALETTE.map { SceneObjectRenderer.fallCrownColour(it, d) }.toSet()
        assertTrue("$crowns in $allowed", crowns.all { it in allowed })
    }

    @Test
    fun `nothing on the card stands below its road`() {
        // The wallpaper's near row, below the road, is not on the card: every item's ground line is at or above the
        // card road's lower edge, on every built-in by day and by night.
        for (theme in ThemeCatalog.ALL) {
            for (night in listOf(false, true)) {
                val scene = ThemePreviewScenes.forTheme(theme, defaultCustomizationFor(theme.id), night)
                for (item in scene.backdrop + scene.items + scene.cars + scene.ground + scene.water) {
                    assertTrue("${theme.id} (night $night): an item at y=${item.y}", item.y <= ThemePreviewScenes.ROAD_BOTTOM)
                }
            }
        }
    }

    private fun nameOf(res: Int): String =
        com.paperscrape.livewallpaper.R.drawable::class.java.fields
            .first { it.type == Int::class.javaPrimitiveType && it.getInt(null) == res }.name
}
