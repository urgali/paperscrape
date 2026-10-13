package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The towers', the shops' and the public buildings' starting colours are not too like the ground behind
 * them (v5.11, v5.13; the maintainer's *«che i colori non siano troppo uguali al terreno altrimenti non si
 * capisce niente»* of 2026-10-06). The measure, the two gates and the cases they come from are
 * [BuildingGroundContrast]'s.
 *
 * Measured on the host, every theme at both ends of its day (`AI_PROJECT_RULES.md` 12.20); the phone
 * confirms it on the photographs of the round.
 */
class BuildingGroundContrastTest {

    /**
     * The three rows of the size table the places among the houses stand in (five places on a built-in street
     * since v5.13B2, two in the restaurant's row and two in the bar's): any of the six buildings stands in any.
     */
    private val places = listOf(
        SceneSpace.SceneVariant.RESTAURANT,
        SceneSpace.SceneVariant.SCHOOL,
        SceneSpace.SceneVariant.BAR,
    )

    /**
     * **A place fails when either of its groups melts into the ground** (v5.13): the shops' colours or the
     * public buildings' the colour of the hills, and the place's check -- the one the random themes' starting
     * colours and the users' own resets are held to -- says so. Without this the check could answer "clear"
     * for every place whatever the colours were, and the tests that lean on it would still pass (a mutation
     * of v5.13B did exactly that).
     */
    @Test
    fun `a place whose shops or public buildings wear the hills' colour does not pass`() {
        val c = defaultCustomizationFor("autumn")
        val hills = c.shops.copy(
            colorDay1 = c.hillsColorDay, colorNight1 = c.hillsColorNight,
            colorDay2 = c.hillsColorDay, colorNight2 = c.hillsColorNight,
        )
        for (v in places) {
            assertTrue("$v as Autumn ships", BuildingGroundContrast.passes(c, v))
            assertFalse("$v with the shops in the hills' colour", BuildingGroundContrast.passes(c.copy(shops = hills), v))
            assertFalse("$v with the public buildings in the hills' colour", BuildingGroundContrast.passes(c.copy(publics = hills), v))
        }
    }

    @Test
    fun `every built-in theme's towers, shops and public buildings stand clear of their ground, in every place`() {
        val report = StringBuilder()
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            val readings = mutableListOf<Triple<String, Float, Float>>()
            readings += Triple(
                "TOWER",
                BuildingGroundContrast.worstDeltaE(c, SceneSpace.SceneVariant.TOWER, day = true),
                BuildingGroundContrast.worstDeltaE(c, SceneSpace.SceneVariant.TOWER, day = false),
            )
            for (place in places) for (group in StorefrontGroup.values()) {
                readings += Triple(
                    "$group in the $place's place",
                    BuildingGroundContrast.worstDeltaE(c, group, place, day = true),
                    BuildingGroundContrast.worstDeltaE(c, group, place, day = false),
                )
            }
            for ((what, day, night) in readings) {
                report.append("%-10s %-32s day %5.1f night %5.1f\n".format(theme.id, what, day, night))
                assertTrue("${theme.id} $what by day: dE $day", day >= BuildingGroundContrast.DAY_GATE)
                assertTrue("${theme.id} $what by night: dE $night", night >= BuildingGroundContrast.NIGHT_GATE)
            }
        }
        println(report)
    }

    /**
     * **The three groups tell apart** (v5.13): each built-in theme's public buildings' two colours stand
     * at least dE 18 by day and 8 by night from each of its shops' colours, and 15 and 6 from each of its
     * towers' -- the distances the pairs were chosen by ([builtInPublicColours]).
     */
    @Test
    fun `every built-in theme's public buildings stand apart from its shops and its towers`() {
        fun day(c: ObjectVariantConfig) = listOf(c.colorDay1, c.colorDay2)
        fun night(c: ObjectVariantConfig) = listOf(c.colorNight1, c.colorNight2)
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            for ((other, dayGap, nightGap) in listOf(Triple(c.shops, 18f, 8f), Triple(c.buildings, 15f, 6f))) {
                for (a in day(c.publics)) for (b in day(other)) {
                    assertTrue("${theme.id}: a public colour by day %.1f from ${Integer.toHexString(b)}".format(BuildingGroundContrast.deltaE(a, b)),
                        BuildingGroundContrast.deltaE(a, b) >= dayGap)
                }
                for (a in night(c.publics)) for (b in night(other)) {
                    assertTrue("${theme.id}: a public colour at night %.1f from ${Integer.toHexString(b)}".format(BuildingGroundContrast.deltaE(a, b)),
                        BuildingGroundContrast.deltaE(a, b) >= nightGap)
                }
            }
        }
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
