package com.paperscrape.livewallpaper.engine

import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import kotlin.math.floor
import kotlin.math.roundToInt

/**
 * **A theme that is not built in starts its towers and shops by the twelve's rule** (v5.11, inventory
 * I-419; the maintainer's *«Ripararlo prima di pubblicare»* of 2026-10-07): [withStartingBuildingColours].
 * Until then such a theme started from [SceneCustomization.DEFAULT]'s slate, which a random theme's hills
 * swallowed at night about one time in eight and Big City's hills swallowed by day (3.8).
 *
 * Measured on the host over the whole space the random themes are drawn from (`AI_PROJECT_RULES.md`
 * 12.20), and over a copy of every built-in theme.
 */
class StartingBuildingColoursTest {

    @After
    fun clear() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    private val shopVariants = listOf(SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.SCHOOL, SceneSpace.SceneVariant.BAR)

    private fun ObjectVariantConfig.pair() = intArrayOf(colorDay1, colorNight1, colorDay2, colorNight2)

    private fun repoFile(path: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, path)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        error("not found: $path")
    }

    /**
     * `Color.HSVToColor` written out (it is a framework call, not mocked on the JVM): Skia's
     * `SkHSVToColor`, the arithmetic it delegates to, value rounded to a byte first.
     */
    private fun hsv(h: Float, s: Float, v: Float): Int {
        val value = (v.coerceIn(0f, 1f) * 255f).roundToInt()
        val sat = s.coerceIn(0f, 1f)
        if (sat == 0f) return (0xFF shl 24) or (value shl 16) or (value shl 8) or value
        val hue = ((h % 360f) + 360f) % 360f
        val hx = if (hue >= 360f) 0f else hue / 60f
        val w = floor(hx)
        val f = hx - w
        val p = ((1f - sat) * value).roundToInt()
        val q = ((1f - sat * f) * value).roundToInt()
        val t = ((1f - sat * (1f - f)) * value).roundToInt()
        val (r, g, b) = when (w.toInt()) {
            0 -> Triple(value, t, p)
            1 -> Triple(q, value, p)
            2 -> Triple(p, value, t)
            3 -> Triple(p, q, value)
            4 -> Triple(t, p, value)
            else -> Triple(value, p, q)
        }
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    /**
     * The hill colours [RandomSceneGenerator] draws, read from its source so that this test follows the
     * generator: the farthest hill (the one `defaultCustomizationFor` takes) at `hsv(hillHue, S, V)` by
     * day and by night, the hue anywhere on the circle (a uniform sky hue plus one of six offsets).
     */
    private fun generatorHill(night: Boolean): Pair<Float, Float> {
        val source = repoFile("app/src/main/kotlin/com/paperscrape/livewallpaper/engine/RandomSceneGenerator.kt").readText()
        val block = Regex(if (night) """hillColorsNight = intArrayOf\(\s*hsv\(hillHue, ([0-9.]+)f, ([0-9.]+)f\)""" else """hillColorsDay = intArrayOf\(\s*hsv\(hillHue, ([0-9.]+)f, ([0-9.]+)f\)""")
        val m = requireNotNull(block.find(source)) { "RandomSceneGenerator's first hill is not hsv(hillHue, s, v) any more" }
        return m.groupValues[1].toFloat() to m.groupValues[2].toFloat()
    }

    @Test
    fun `every random theme starts its towers and shops clear of its own ground`() {
        val (sd, vd) = generatorHill(night = false)
        val (sn, vn) = generatorHill(night = true)
        val chosen = HashMap<String, Int>()
        var worstTower = Float.MAX_VALUE
        var worstShop = Float.MAX_VALUE
        var hue = 0f
        while (hue < 360f) {
            val day = hsv(hue, sd, vd)
            val night = hsv(hue, sn, vn)
            // What defaultCustomizationFor builds for a random theme before the rule: DEFAULT, the theme's
            // farthest hill, DEFAULT's mountains shown.
            val theme = ThemeCatalog.SUNSET.copy(
                id = "random:test", skyDay = intArrayOf(hsv(hue + 7f, 0.45f, 0.95f)),
                hillColorsDay = intArrayOf(day), hillColorsNight = intArrayOf(night),
            )
            val base = SceneCustomization.DEFAULT.copy(hillsColorDay = day, hillsColorNight = night)
            val c = base.withStartingBuildingColours(theme)
            assertTrue("hue $hue: the towers", BuildingGroundContrast.passes(c, SceneSpace.SceneVariant.TOWER))
            for (v in shopVariants) assertTrue("hue $hue: $v", BuildingGroundContrast.passes(c, v))
            worstTower = minOf(worstTower, BuildingGroundContrast.margin(c, SceneSpace.SceneVariant.TOWER, c.buildings.pair()))
            worstShop = minOf(worstShop, BuildingGroundContrast.margin(c, SceneSpace.SceneVariant.RESTAURANT, c.shops.pair()))
            val key = "%08X".format(c.buildings.colorDay1)
            chosen[key] = (chosen[key] ?: 0) + 1
            hue += 0.5f
        }
        println("random themes, 720 hill hues: tower pairs chosen by day colour 1 $chosen; worst margin towers $worstTower, shops $worstShop")
    }

    @Test
    fun `a copy of every built-in theme starts from that theme's own towers and shops`() {
        val copies = ThemeCatalog.ALL.map { theme ->
            val id = "custom:copy-${theme.id}"
            CustomThemeEntry(id, "Copy", theme.copy(id = id, displayName = "Copy"), SceneObjectCatalog.layoutFor(theme.id, theme.accentColor), defaultCustomizationFor(theme.id))
        }
        CustomThemeRegistry.update(CustomThemeData(customThemes = copies))
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor("custom:copy-${theme.id}")
            assertArrayEquals("towers of a copy of ${theme.id}", builtInTowerColours(theme.id)!!.pair(), c.buildings.pair())
            assertArrayEquals("shops of a copy of ${theme.id}", builtInShopColours(theme.id)!!.pair(), c.shops.pair())
            // Only the colours: the switch and the amount are the base's, not the built-in theme's own.
            assertEquals(SceneCustomization.DEFAULT.buildings.density, c.buildings.density)
        }
    }

    /**
     * The case of the question, by colour: Big City's slate was 3.8 from its hills by day; a copy of Big
     * City starts from its steel and sand, 20.6 or more (the mountains off, as Big City ships them).
     */
    @Test
    fun `a copy of Big City no longer starts from the slate its hills swallowed`() {
        val city = ThemeCatalog.byId("city")
        CustomThemeRegistry.update(
            CustomThemeData(
                customThemes = listOf(
                    CustomThemeEntry("custom:town", "Town", city.copy(id = "custom:town", displayName = "Town"), SceneObjectCatalog.layoutFor("city", city.accentColor), defaultCustomizationFor("city")),
                ),
            ),
        )
        val c = defaultCustomizationFor("custom:town")
        val asSaved = c.copy(mountainsFront = c.mountainsFront.copy(visible = false), mountainsBack = c.mountainsBack.copy(visible = false))
        assertTrue(BuildingGroundContrast.passes(asSaved, SceneSpace.SceneVariant.TOWER))
        assertTrue(BuildingGroundContrast.worstDeltaE(asSaved, SceneSpace.SceneVariant.TOWER, day = true) > 20f)
        assertTrue(c.buildings.colorDay1 != SceneCustomization.DEFAULT.buildings.colorDay1)
    }

    /**
     * A tower is chosen against the mountains it rises in front of, as the twelve's are measured
     * (decision 46), and a shop against the hills alone: a mountain shown in Big City's steel turns
     * Big City's pair down for the towers and not for anything with the mountains hidden.
     */
    @Test
    fun `the towers are chosen against the mountains they rise in front of`() {
        val steel = builtInTowerColours("city")!!.colorDay1
        val theme = ThemeCatalog.SUNSET.copy(
            id = "random:mountains", skyDay = intArrayOf(0xFF102030.toInt()),
            hillColorsDay = intArrayOf(0xFF1E5E2E.toInt()), hillColorsNight = intArrayOf(0xFF061206.toInt()),
        )
        val base = SceneCustomization.DEFAULT.copy(
            hillsColorDay = 0xFF1E5E2E.toInt(), hillsColorNight = 0xFF061206.toInt(),
            mountainsFront = SceneCustomization.DEFAULT.mountainsFront.copy(visible = true, colorDay = steel),
        )
        val hidden = base.copy(
            mountainsFront = base.mountainsFront.copy(visible = false),
            mountainsBack = base.mountainsBack.copy(visible = false),
        )
        assertEquals("with no mountain shown, Big City's pair is the first that clears", steel, hidden.withStartingBuildingColours(theme).buildings.colorDay1)
        val c = base.withStartingBuildingColours(theme)
        assertTrue("a tower the colour of the mountain behind it", c.buildings.colorDay1 != steel)
        assertTrue(BuildingGroundContrast.passes(c, SceneSpace.SceneVariant.TOWER))
    }

    /** The twelve built-in themes are not touched by the rule: their pairs are the ones chosen for them. */
    @Test
    fun `a built-in theme keeps its own pairs`() {
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            assertArrayEquals(theme.id, builtInTowerColours(theme.id)!!.pair(), c.buildings.pair())
            assertArrayEquals(theme.id, builtInShopColours(theme.id)!!.pair(), c.shops.pair())
        }
    }

    /** [clearestPair]: the first pair that clears both gates, in the order given; if none does, the closest. */
    @Test
    fun `the first pair that clears the gates is taken, and the closest when none does`() {
        val grey = 0xFF808080.toInt()
        val ground = SceneCustomization.DEFAULT.copy(
            hillsColorDay = grey, hillsColorNight = grey,
            mountainsFront = SceneCustomization.DEFAULT.mountainsFront.copy(visible = false),
            mountainsBack = SceneCustomization.DEFAULT.mountainsBack.copy(visible = false),
        )
        val onTheGround = intArrayOf(grey, grey, grey, grey)
        val near = intArrayOf(0xFF8C8C8C.toInt(), 0xFF8C8C8C.toInt(), 0xFF8C8C8C.toInt(), 0xFF8C8C8C.toInt())
        val clear = intArrayOf(0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())
        val clearToo = intArrayOf(0xFF000000.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt(), 0xFFFFFFFF.toInt())
        val variant = SceneSpace.SceneVariant.RESTAURANT
        assertTrue(BuildingGroundContrast.margin(ground, variant, clear) >= 1f)
        assertTrue(BuildingGroundContrast.margin(ground, variant, near) < 1f)
        assertTrue(BuildingGroundContrast.margin(ground, variant, near) > BuildingGroundContrast.margin(ground, variant, onTheGround))
        assertTrue("the first that clears, not the clearest", clearestPair(listOf(onTheGround, clear, clearToo), ground, variant) === clear)
        assertTrue("the second when the first does not", clearestPair(listOf(onTheGround, clearToo, clear), ground, variant) === clearToo)
        assertTrue("none clears: the closest", clearestPair(listOf(onTheGround, near), ground, variant) === near)
    }
}
