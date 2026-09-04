package com.paperscrape.livewallpaper.engine

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The cars' night density (v4.22 Fase 3): the pedestrians' model, replicated exactly.
 *
 * Three halves are pinned. The **blend**: the density in force crossfades linearly on `dayBlend`
 * through the same [PeopleDensity.at] the pedestrians use, and feeds the count — there is no
 * parallel threshold. The **upgrade rule**: a payload or preference store from before the field
 * existed resolves the night density to its own daytime value, so the scene after the update is
 * the scene before it. And the **selection consequence**: because the blended density enters
 * [CarSelection.countFor], the night-time road is always a *subset* of the day-time road at any
 * blend (nesting), never a reshuffle.
 */
class CarNightDensityTest {

    private fun cars(themeId: String = "sunset") =
        SceneObjectCatalog.layoutFor(themeId, accentColor = 0xFF335577.toInt()).cars

    // ---------------------------------------------------------------- the blend

    @Test
    fun `the blended density is the pedestrians' own crossfade`() {
        var blend = 0f
        while (blend <= 1f) {
            assertEquals(
                PeopleDensity.at(0.8f, 0.2f, blend),
                CarSelection.densityAt(0.8f, 0.2f, blend),
                0f,
            )
            blend += 0.05f
        }
        // The two endpoints mean exactly the two sliders.
        assertEquals(0.2f, CarSelection.densityAt(0.8f, 0.2f, 0f), 1e-6f)
        assertEquals(0.8f, CarSelection.densityAt(0.8f, 0.2f, 1f), 1e-6f)
    }

    /** A quieter night never rearranges the day's traffic: at every blend the kept set is a
     * subset of the day's, shrinking monotonically as the light goes. */
    @Test
    fun `dusk drains the road monotonically and by nesting, never by reshuffle`() {
        val inventory = cars()
        val seed = "sunset".hashCode()
        var previous: BooleanArray? = null
        var blend = 1f
        while (blend >= 0f) {
            val density = CarSelection.densityAt(dayDensity = 1f, nightDensity = 0f, dayBlend = blend)
            val mask = CarSelection.keptMask(inventory, density, seed)
            previous?.let { day ->
                for (i in inventory.indices) {
                    assertFalse(
                        "blend $blend added car $i that brighter light did not have",
                        mask[i] && !day[i],
                    )
                }
            }
            previous = mask
            blend -= 0.02f
        }
        // And the two ends are the two counts the sliders ask for.
        assertEquals(10, CarSelection.keptMask(inventory, CarSelection.densityAt(1f, 0f, 1f), seed).count { it })
        assertEquals(1, CarSelection.keptMask(inventory, CarSelection.densityAt(1f, 0f, 0f), seed).count { it })
    }

    // ---------------------------------------------------------------- the upgrade rule

    @Test
    fun `a customization payload from before v4_22 keeps its own daytime traffic at night`() {
        val defaults = SceneCustomization.DEFAULT
        val saved = defaults.copy(cars = defaults.cars.copy(density = 0.4f))
        val json = JSONObject(saved.toJson().toString())
        // What a pre-v4.22 build wrote: no night key, no business keys.
        json.remove("carsNightDensity")
        json.remove("businessHoursEnabled")
        json.remove("businessOpenHour")
        json.remove("businessCloseHour")

        val loaded = sceneCustomizationFromJson(json)
        assertEquals(
            "the night density must resolve to the payload's own daytime value",
            0.4f, loaded.carsNightDensity, 1e-6f,
        )
        assertFalse("the business hours must arrive switched off", loaded.businessHoursEnabled)
    }

    @Test
    fun `a v4_22 payload round-trips all four new fields`() {
        val defaults = SceneCustomization.DEFAULT
        val set = defaults.copy(
            cars = defaults.cars.copy(density = 0.9f),
            carsNightDensity = 0.15f,
            businessHoursEnabled = true,
            businessOpenHour = 8.25f,
            businessCloseHour = 1.75f,
        )
        val loaded = sceneCustomizationFromJson(JSONObject(set.toJson().toString()))
        assertEquals(0.15f, loaded.carsNightDensity, 1e-6f)
        assertTrue(loaded.businessHoursEnabled)
        assertEquals(8.25f, loaded.businessOpenHour, 1e-6f)
        assertEquals(1.75f, loaded.businessCloseHour, 1e-6f)
    }

    // ---------------------------------------------------------------- structural consequence

    /** The night density changes which cars render, so it is structural -- a config differing
     * only there must not compare as producing the same traffic. */
    @Test
    fun `the night density is part of the cars' structural comparison`() {
        val base = SceneCustomization.DEFAULT
        val quieterNights = base.copy(carsNightDensity = 0.2f)
        assertFalse(base.carsStructurallyEquals(quieterNights))
        assertTrue(base.carsStructurallyEquals(base.copy(cars = base.cars.copy(colorDay1 = 0x123456))))
    }
}
