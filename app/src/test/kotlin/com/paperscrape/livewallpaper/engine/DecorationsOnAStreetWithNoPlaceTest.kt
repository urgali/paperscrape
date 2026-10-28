package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **On a street with no place among the houses, *Show Buildings* carries no decoration** (`DESIGN_NOTES.md` §12, decisions
 * 40 and 77): the shops stand in the places, so where a random theme rolled none, the Halloween decorations hang on the
 * houses alone, and the lights on the buildings on the houses, the towers and the houses on the mountains. A side that
 * hangs on nothing holds the other side off neither. On the twelve built-in streets, which all have places, nothing moves.
 *
 * **The reading of stored data agrees** ([DecorationExclusion.resolved]): it turns a side off wherever *Show Buildings*
 * on would show both -- the gallery card draws its shops by that switch on any street -- and keeps the side that hangs
 * on the street, so a switch the settings let the user turn on reads on. It keeps another side than the theme's in one
 * case only, said in the last test.
 */
class DecorationsOnAStreetWithNoPlaceTest {

    private fun randomStreet(n: Int): SceneObjectLayout? = runCatching { SceneObjectCatalog.layoutFor("random:$n", 0) }.getOrNull()

    @Test
    fun `on a random street with no house and no building, Show Buildings holds the Christmas side off neither`() {
        val (id, street) = (1..2000).asSequence().mapNotNull { n -> randomStreet(n)?.let { "random:$n" to it } }.first { (_, s) ->
            s.staticObjects.none { it.type == SceneObjectType.HOUSE || it.type == SceneObjectType.SKYSCRAPER }
        }
        val d = SceneCustomization.DEFAULT
        val c = d.copy(buildings = d.buildings.copy(visible = true), halloweenDecorationsEnabled = true, christmasBuildingLightsEnabled = true)
        assertFalse("$id: the Halloween decorations hang on nothing", DecorationExclusion.halloweenDecorationsHang(c, street))
        assertFalse(id, DecorationExclusion.placesStand(c, street))
        assertFalse(id, DecorationExclusion.halloweenOn(c, street))
        assertTrue("$id: and hold the Christmas side off", DecorationExclusion.christmasAllowed(c, street))
        assertFalse("$id: the lights on the buildings hang on nothing", DecorationExclusion.buildingLightsHang(c, street))
        // Without the street, Show Buildings still stands for the places.
        assertTrue(DecorationExclusion.halloweenDecorationsHang(c))
        assertTrue(DecorationExclusion.placesStand(c))
    }

    /** A street with towers and no place: a tower standing carries the lights, a tower thinned away does not. */
    @Test
    fun `on a random street with towers and no place, the lights hang on a tower that stands`() {
        val (id, street) = (1..2000).asSequence().mapNotNull { n -> randomStreet(n)?.let { "random:$n" to it } }.first { (_, s) ->
            s.staticObjects.none { it.type == SceneObjectType.HOUSE || StorefrontStreet.isPlace(it) } &&
                s.staticObjects.any { it.type == SceneObjectType.SKYSCRAPER }
        }
        val d = SceneCustomization.DEFAULT
        val full = d.copy(buildings = d.buildings.copy(visible = true, density = 1f))
        assertTrue("$id: a tower at 100 %", DecorationExclusion.buildingLightsHang(full, street))
        assertFalse("$id: the decorations have no shop", DecorationExclusion.halloweenDecorationsHang(full, street))
        val none = d.copy(buildings = d.buildings.copy(visible = true, density = 0f))
        assertFalse("$id: no tower at 0 %", DecorationExclusion.buildingLightsHang(none, street))
    }

    @Test
    fun `on the twelve built-in streets the rule is what it was`() {
        for (theme in ThemeCatalog.ALL) {
            val street = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
            val base = defaultCustomizationFor(theme.id)
            for (buildingsOn in listOf(true, false)) for (housesOn in listOf(true, false)) for (towers in listOf(0f, 0.1f, 1f)) {
                val c = base.copy(buildings = base.buildings.copy(visible = buildingsOn, density = towers), houses = base.houses.copy(visible = housesOn))
                assertEquals(theme.id, DecorationExclusion.housesStand(c, street) || c.buildings.visible, DecorationExclusion.halloweenDecorationsHang(c, street))
                assertEquals(
                    theme.id,
                    DecorationExclusion.housesStand(c, street) || c.buildings.visible ||
                        (c.distantHouses.drawsAny && (c.mountainsFront.drawsAny || c.mountainsBack.drawsAny)),
                    DecorationExclusion.buildingLightsHang(c, street),
                )
            }
        }
    }

    /**
     * **The reading turns a side off where *Show Buildings* on would show both, and keeps the one that hangs on the
     * street** ([DecorationExclusion.resolved]), against the rule written out, over random streets and the twelve
     * built-in ones, each switch on and off. **It keeps another side than the theme's in one case only**: both sides
     * stored on, *Show Buildings* on over a street with no place among the houses, no house standing -- so the Halloween
     * decorations hang on nothing --, the Christmas side hanging on the street, on a theme that ships the Halloween
     * decorations. There the Christmas side stays and the Halloween decorations, drawn nowhere on the wallpaper, go, where
     * keeping the theme's side would draw neither. Everywhere else the reading keeps the theme's side (`byTheTheme`).
     */
    @Test
    fun `the reading of stored data keeps the side that hangs on the street, and moves only where the other hangs on nothing`() {
        fun lightsCarried(c: SceneCustomization, s: SceneObjectLayout) = DecorationExclusion.housesStand(c, s) || c.buildings.visible ||
            (c.distantHouses.drawsAny && (c.mountainsFront.drawsAny || c.mountainsBack.drawsAny))
        fun christmas(c: SceneCustomization, s: SceneObjectLayout) = (lightsCarried(c, s) && c.christmasBuildingLightsEnabled) ||
            (DecorationExclusion.treesStand(c, s) && (c.christmasTreeLightsEnabled || c.christmasFirsEnabled))
        fun halloween(c: SceneCustomization, s: SceneObjectLayout) =
            (DecorationExclusion.housesStand(c, s) || c.buildings.visible) && c.halloweenDecorationsEnabled
        fun christmasOff(c: SceneCustomization) =
            c.copy(christmasBuildingLightsEnabled = false, christmasTreeLightsEnabled = false, christmasFirsEnabled = false)
        // Show Buildings on taken for the places on any street, and the theme's side kept.
        fun byTheTheme(c: SceneCustomization, defaults: SceneCustomization, s: SceneObjectLayout): SceneCustomization = when {
            !christmas(c, s) || !halloween(c, s) -> c
            halloween(defaults, s) && !christmas(defaults, s) -> christmasOff(c)
            else -> c.copy(halloweenDecorationsEnabled = false)
        }
        // The same sides turned off, and the one that hangs on the street kept where only one does.
        fun expected(c: SceneCustomization, defaults: SceneCustomization, s: SceneObjectLayout): SceneCustomization {
            if (!christmas(c, s) || !halloween(c, s)) return c
            val christmasHangs = DecorationExclusion.christmasOn(c, s)
            val halloweenHangs = DecorationExclusion.halloweenOn(c, s)
            return when {
                christmasHangs && !halloweenHangs -> c.copy(halloweenDecorationsEnabled = false)
                halloweenHangs && !christmasHangs -> christmasOff(c)
                else -> byTheTheme(c, defaults, s)
            }
        }
        val streets = (1..80).mapNotNull { n -> randomStreet(n)?.let { "random:$n" to it } } +
            ThemeCatalog.ALL.map { it.id to SceneObjectCatalog.layoutFor(it.id, it.accentColor) }
        val noPlace = streets.count { (_, s) -> s.staticObjects.none { StorefrontStreet.isPlace(it) } }
        assertTrue("too few streets with no place: $noPlace", noPlace >= 5)
        val d = SceneCustomization.DEFAULT
        val defaults = listOf(d, defaultCustomizationFor("halloween"))
        var otherSide = 0
        for ((id, s) in streets) for (bits in 0 until 128) {
            val c = d.copy(
                houses = d.houses.copy(visible = bits and 1 != 0),
                buildings = d.buildings.copy(visible = bits and 2 != 0),
                trees = d.trees.copy(visible = bits and 4 != 0),
                christmasBuildingLightsEnabled = bits and 8 != 0,
                christmasTreeLightsEnabled = bits and 16 != 0,
                halloweenDecorationsEnabled = bits and 32 != 0,
                distantHouses = DistantHousesConfig(visible = bits and 64 != 0, density = 0.5f),
            )
            for (def in defaults) {
                val got = DecorationExclusion.resolved(c, def, s)
                val want = expected(c, def, s)
                assertEquals("$id, $bits", want.halloweenDecorationsEnabled, got.halloweenDecorationsEnabled)
                assertEquals("$id, $bits", want.christmasBuildingLightsEnabled, got.christmasBuildingLightsEnabled)
                assertEquals("$id, $bits", want.christmasTreeLightsEnabled, got.christmasTreeLightsEnabled)
                val was = byTheTheme(c, def, s)
                if (got == was) continue
                otherSide++
                val what = "$id, $bits: another side than the theme's kept outside the one case"
                assertTrue(what, c.buildings.visible && s.staticObjects.none { StorefrontStreet.isPlace(it) })
                assertFalse(what, DecorationExclusion.housesStand(c, s))
                assertTrue(what, c.halloweenDecorationsEnabled && DecorationExclusion.christmasOn(c, s))
                assertTrue(what, halloween(def, s) && !christmas(def, s))
                assertEquals(what, christmasOff(c), was)
                assertEquals(what, c.copy(halloweenDecorationsEnabled = false), got)
            }
        }
        assertTrue("the one case never came", otherSide > 0)
    }
}
