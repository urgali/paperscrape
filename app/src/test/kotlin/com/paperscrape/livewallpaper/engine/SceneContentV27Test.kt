package com.paperscrape.livewallpaper.engine

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

    @Test
    fun `commercial buildings out-top the houses`() {
        val smallHouse = SceneSpace.SceneVariant.HOUSE_SMALL.metresTall
        val largeHouse = SceneSpace.SceneVariant.HOUSE_LARGE.metresTall
        for (shop in listOf(SceneSpace.SceneVariant.BAR, SceneSpace.SceneVariant.RESTAURANT)) {
            assertTrue("$shop should out-top a small house", shop.metresTall > smallHouse)
            assertTrue("$shop should out-top a large house", shop.metresTall > largeHouse)
        }
        assertTrue(
            "a tower should out-top the shops in front of it",
            SceneSpace.SceneVariant.TOWER.metresTall > SceneSpace.SceneVariant.RESTAURANT.metresTall,
        )
    }

    @Test
    fun `the hierarchy holds in drawn pixels, not only in metres`() {
        // Height in metres is the input; what the eye compares is the drawn extent, which is
        // metres times the base scale times the sprite's own unit height. Stated separately
        // because a sprite redraw changes the second factor without touching the first.
        fun drawnUnits(v: SceneSpace.SceneVariant) = v.baseScale * v.spriteUnitsTall
        assertTrue(
            drawnUnits(SceneSpace.SceneVariant.BAR) > drawnUnits(SceneSpace.SceneVariant.HOUSE_LARGE),
        )
        assertTrue(
            drawnUnits(SceneSpace.SceneVariant.RESTAURANT) > drawnUnits(SceneSpace.SceneVariant.BAR),
        )
        assertTrue(
            drawnUnits(SceneSpace.SceneVariant.TOWER) > drawnUnits(SceneSpace.SceneVariant.RESTAURANT) * 2f,
        )
    }
}
