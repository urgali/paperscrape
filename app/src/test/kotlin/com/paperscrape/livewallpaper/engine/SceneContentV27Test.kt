package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What v2.7 added, removed and re-proportioned.
 *
 * Three unrelated changes share a file because each is small and each is the kind of thing that
 * decays quietly: a toggle that stops reaching its theme, a removal that leaves a stub behind, and
 * a size relation that a later tuning pass inverts without noticing.
 */
class SceneContentV27Test {

    // --- Flowers ------------------------------------------------------------------------------

    @Test
    fun `flowers are off by default and on in spring`() {
        assertFalse(
            "flowers must be opt-in like every other decoration",
            SceneCustomization.DEFAULT.flowersEnabled,
        )
        assertTrue(
            "spring without flowers is a green summer",
            defaultCustomizationFor("spring").flowersEnabled,
        )
    }

    @Test
    fun `no other theme turns flowers on`() {
        for (theme in ThemeCatalog.ALL.filter { it.id != "spring" }) {
            assertFalse(
                "${theme.id} enables flowers by default",
                defaultCustomizationFor(theme.id).flowersEnabled,
            )
        }
    }

    @Test
    fun `flowers can be turned off on spring and on anywhere else`() {
        // The theme seeds the value; the user owns it afterwards. Both directions have to be
        // reachable or the preset has quietly become a property of the theme.
        assertFalse(defaultCustomizationFor("spring").copy(flowersEnabled = false).flowersEnabled)
        assertTrue(defaultCustomizationFor("winter").copy(flowersEnabled = true).flowersEnabled)
    }

    @Test
    fun `flowers survive a theme json round trip and default off in older payloads`() {
        val saved = defaultCustomizationFor("spring")
        assertTrue(sceneCustomizationFromJson(org.json.JSONObject(saved.toJson().toString())).flowersEnabled)

        val older = org.json.JSONObject(saved.toJson().toString())
        older.remove("flowersEnabled")
        assertFalse(
            "a theme saved before flowers existed must not acquire them",
            sceneCustomizationFromJson(older).flowersEnabled,
        )
    }

    @Test
    fun `flowers reach none of the other decoration flags`() {
        val plain = defaultCustomizationFor("sunset")
        val flowered = plain.copy(flowersEnabled = true)
        assertEquals(plain.winterColorsEnabled, flowered.winterColorsEnabled)
        assertEquals(plain.fallColorsEnabled, flowered.fallColorsEnabled)
        assertEquals(plain.christmasDecorationsEnabled, flowered.christmasDecorationsEnabled)
        assertEquals(plain.halloweenEnabled, flowered.halloweenEnabled)
    }

    // --- Which clump, v5.1 --------------------------------------------------------------------

    @Test
    fun `the seasonal palette picks the clump and the flowers switch does not`() {
        val plain = defaultCustomizationFor("spring")
        assertEquals(
            "no seasonal palette means the meadow is in flower",
            R.drawable.ground_flowers_bloom,
            SceneObjectRenderer.groundFlowerSprite(plain),
        )
        assertEquals(
            "an autumn scene with flowers on gets the clump gone over, not midsummer blooms",
            R.drawable.ground_flowers_dry,
            SceneObjectRenderer.groundFlowerSprite(plain.copy(fallColorsEnabled = true)),
        )
        assertEquals(
            "winter takes the dry clump too: seed heads out of the snow, not a third drawing",
            R.drawable.ground_flowers_dry,
            SceneObjectRenderer.groundFlowerSprite(plain.copy(winterColorsEnabled = true)),
        )
    }

    @Test
    fun `flowersEnabled says whether, never which`() {
        // The whole point of hanging the choice on the palette: v5.1 added no preference, no menu
        // entry and no backup field, so `flowersEnabled` has to stay a pure on/off. If turning the
        // flowers off and on again could change *which* clump is drawn, it would have quietly
        // become a second seasonal control.
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            assertEquals(
                "${theme.id}: the switch moved the drawing",
                SceneObjectRenderer.groundFlowerSprite(c.copy(flowersEnabled = false)),
                SceneObjectRenderer.groundFlowerSprite(c.copy(flowersEnabled = true)),
            )
        }
    }

    @Test
    fun `the two clumps are two drawings`() {
        // A pair whose members are the same resource is a feature that does nothing and fails
        // nothing -- which is exactly what the registry's IDENTICAL_GAP state exists to record,
        // and what the seasonal person art shipped for a whole release.
        assertTrue(R.drawable.ground_flowers_bloom != R.drawable.ground_flowers_dry)
    }

    // --- Balloons, removed --------------------------------------------------------------------

    @Test
    fun `no object type is a balloon`() {
        assertTrue(
            "the balloon type must be gone, not merely unused",
            SceneObjectType.entries.none { it.name.contains("BALLOON") },
        )
    }

    @Test
    fun `no scene variant is a balloon`() {
        assertTrue(
            SceneSpace.SceneVariant.entries.none { it.name.contains("BALLOON") },
        )
    }

    @Test
    fun `no preference category is a balloon`() {
        assertTrue(
            "a category with no field behind it is a toggle that does nothing",
            com.paperscrape.livewallpaper.prefs.ObjectCategory.entries
                .none { it.name.contains("BALLOON") },
        )
    }

    @Test
    fun `no theme lays out a balloon`() {
        for (theme in ThemeCatalog.ALL) {
            val types = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
                .staticObjects.map { it.type }
            assertTrue("${theme.id} still places balloons", types.none { it.name.contains("BALLOON") })
        }
    }

    @Test
    fun `a saved theme carrying a balloons block loads without one`() {
        // Payloads written before the removal still have the key. Reading has to ignore it rather
        // than fail, and nothing may come back carrying it.
        val payload = org.json.JSONObject(SceneCustomization.DEFAULT.toJson().toString())
        payload.put("balloons", org.json.JSONObject().put("visible", true).put("density", 0.9))
        val restored = sceneCustomizationFromJson(payload)
        assertFalse(restored.toJson().has("balloons"))
    }

    // --- The building hierarchy ---------------------------------------------------------------

    /**
     * **v5.4: this method used to assert the v2.7 decision and it asserts the v5.0 drawing now.**
     *
     * What it said was *"commercial buildings out-top the houses"*, and it read that off
     * `SceneVariant.metresTall`. Item 113 found that number describing, for both shops, the
     * two-storey facade the v5.0 neighbourhood redraw replaced with a single-storey pavilion: the
     * restaurant declared 8.2 m and draws **4.78 m**, the bar declared 7.7 m and draws 4.53 or
     * **6.24 m**, against a large house's 7.17 to 10.85 m.
     *
     * So the decision is not satisfied by the artwork and has not been for a release, and the
     * declaration was the only place it survived. The maintainer's ruling on item 113 was to
     * correct the declaration and leave the drawing alone -- the drawing is what was chosen from
     * the phase-4 photographs -- so what is asserted here is the hierarchy the scene actually
     * shows, with the one it was supposed to show named beside it.
     *
     * `BuildingHeightDeclarationTest` has carried the drawn figures since v5.0, in the same suite
     * as this method's old claim. Nobody put the two together; this comment is the join.
     */
    @Test
    fun `the tower out-tops the shops, and the shops no longer out-top the houses`() {
        val largeHouse = SceneSpace.SceneVariant.HOUSE_LARGE.metresTall
        for (shop in listOf(SceneSpace.SceneVariant.BAR, SceneSpace.SceneVariant.RESTAURANT)) {
            assertTrue(
                "$shop is a single-storey pavilion since v5.0 and is shorter than a large house",
                shop.metresTall < largeHouse,
            )
        }
        assertTrue(
            "a tower should out-top the shops in front of it",
            SceneSpace.SceneVariant.TOWER.metresTall > SceneSpace.SceneVariant.RESTAURANT.metresTall,
        )
    }

    @Test
    fun `the hierarchy holds in drawn pixels, not only in metres`() {
        // **This is only about drawn pixels because another test makes it so.** `baseScale` is
        // `metres * pixelsPerMetre / spriteUnitsTall`, so `baseScale * spriteUnitsTall` reduces to
        // `metres * pixelsPerMetre` -- the metres again, times a constant. It says nothing about the
        // drawing on its own, which is how TOWER went on declaring 196 units for a building that
        // blits 182. What makes the reduction legitimate is `BuildingHeightDeclarationTest`, which
        // reads every variant's blits and fails if `spriteUnitsTall` stops being the extent the
        // renderer actually draws. Delete that test and this one goes back to being a tautology.
        fun drawnUnits(v: SceneSpace.SceneVariant) = v.baseScale * v.spriteUnitsTall
        // v5.4: both of these used to point the other way, off the stale declaration -- see the
        // method above. The corner bar out-draws the pavilion, and neither out-draws a large house.
        assertTrue(
            drawnUnits(SceneSpace.SceneVariant.BAR) < drawnUnits(SceneSpace.SceneVariant.HOUSE_LARGE),
        )
        assertTrue(
            drawnUnits(SceneSpace.SceneVariant.BAR) > drawnUnits(SceneSpace.SceneVariant.RESTAURANT),
        )
        assertTrue(
            "a tower out-tops a shop by 1.90 as drawn; see SceneSpaceTest for why not 2.0",
            drawnUnits(SceneSpace.SceneVariant.TOWER) > drawnUnits(SceneSpace.SceneVariant.RESTAURANT) * 1.85f,
        )
    }
}
