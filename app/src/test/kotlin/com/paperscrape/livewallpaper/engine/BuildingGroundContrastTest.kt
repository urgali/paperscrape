package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The towers' and the shops' starting colours are not too like the ground behind them (v5.11; the
 * maintainer's *«che i colori non siano troppo uguali al terreno altrimenti non si capisce niente»* of
 * 2026-10-06). The measure, the two gates and the cases they come from are [BuildingGroundContrast]'s.
 *
 * Measured on the host, every theme at both ends of its day (`AI_PROJECT_RULES.md` 12.20); the phone
 * confirms it on the photographs of the round.
 */
class BuildingGroundContrastTest {

    private val buildings = listOf(
        SceneSpace.SceneVariant.TOWER,
        SceneSpace.SceneVariant.RESTAURANT,
        SceneSpace.SceneVariant.SCHOOL,
        SceneSpace.SceneVariant.BAR,
    )

    @Test
    fun `every built-in theme's towers and shops stand clear of their ground, by day and by night`() {
        val report = StringBuilder()
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            for (variant in buildings) {
                val day = BuildingGroundContrast.worstDeltaE(c, variant, day = true)
                val night = BuildingGroundContrast.worstDeltaE(c, variant, day = false)
                report.append("%-10s %-10s day %5.1f night %5.1f\n".format(theme.id, variant, day, night))
                assertTrue("${theme.id} $variant by day: dE $day", day >= BuildingGroundContrast.DAY_GATE)
                assertTrue("${theme.id} $variant by night: dE $night", night >= BuildingGroundContrast.NIGHT_GATE)
            }
        }
        println(report)
    }

    /**
     * The gate bites on the case it was made for: the slate every theme's towers were until v5.11, on
     * Big City's grey hills (inventory I-410), is 3.8 by day and fails -- and so does the first brick
     * the proposals photographed on Autumn's orange hill (12.9).
     */
    @Test
    fun `the gate fails the colours that vanished into the ground`() {
        val city = defaultCustomizationFor("city")
        val oldSlate = city.copy(buildings = SceneCustomization.DEFAULT.buildings)
        assertFalse(BuildingGroundContrast.passes(oldSlate, SceneSpace.SceneVariant.TOWER))
        assertEquals(3.8f, BuildingGroundContrast.deltaE(0xFF5C6A78.toInt(), city.hillsColorDay), 0.05f)
        val autumn = defaultCustomizationFor("autumn")
        val firstBrick = autumn.copy(buildings = autumn.buildings.copy(colorDay1 = 0xFFB9714A.toInt()))
        assertTrue(BuildingGroundContrast.worstDeltaE(firstBrick, SceneSpace.SceneVariant.TOWER, day = true) < BuildingGroundContrast.DAY_GATE)
    }

    /**
     * A tower is measured against the mountains it rises in front of, and only the ones shown: Desert's
     * sandy mountains are what turned its first proposal, a sand tower, down (5.7 from them).
     */
    @Test
    fun `a tower is measured against the mountains shown, a shop against the hills alone`() {
        val desert = defaultCustomizationFor("desert")
        val sand = desert.copy(buildings = desert.buildings.copy(colorDay1 = 0xFFC88B55.toInt()))
        assertTrue(BuildingGroundContrast.worstDeltaE(sand, SceneSpace.SceneVariant.TOWER, day = true) < 6f)
        val noMountains = sand.copy(
            mountainsFront = sand.mountainsFront.copy(visible = false),
            mountainsBack = sand.mountainsBack.copy(visible = false),
        )
        assertTrue(BuildingGroundContrast.worstDeltaE(noMountains, SceneSpace.SceneVariant.TOWER, day = true) > 19f)
        assertEquals(1, BuildingGroundContrast.groundBehind(desert, SceneSpace.SceneVariant.BAR, day = true).size)
        assertEquals(3, BuildingGroundContrast.groundBehind(desert, SceneSpace.SceneVariant.TOWER, day = true).size)
    }

    /** The starting colours are the start only: a colour the user picks is drawn, gate or no gate. */
    @Test
    fun `the user's own colours are not corrected`() {
        val city = defaultCustomizationFor("city")
        val mine = city.copy(buildings = city.buildings.copy(colorDay1 = city.hillsColorDay, colorDay2 = city.hillsColorDay))
        val tower = StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = 0.1f, tileFractionX = 0.3f)
        assertEquals(city.hillsColorDay, mine.wallColourFor(tower, SceneSpace.SceneVariant.TOWER, 1f))
    }
}
