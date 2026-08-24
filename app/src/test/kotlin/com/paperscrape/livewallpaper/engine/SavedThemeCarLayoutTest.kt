package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import com.paperscrape.livewallpaper.ui.snapshotEntry
import org.junit.Test

/**
 * A saved theme keeps its road.
 *
 * ### The defect
 *
 * Saving a theme used to write `rawLayout.cars.filter { keepCar(it) }` into the entry, freezing
 * the *runtime density* into the *terrain*. Saved at 10%, or with Cars switched off, the stored
 * car list came out empty — and `SceneObjectRenderer.hasRoad` is `layout.cars.isNotEmpty()`, so
 * that theme lost its road and every vehicle **permanently**. Raising the density afterwards
 * filters a list with nothing left in it, while the settings screen still reads "On - 100%",
 * because the customization was never the thing that broke.
 *
 * Reported on v4.2 against `beach`, with both the road and the traffic missing at 100%.
 *
 * ### The rule these tests state
 *
 * **A layout is an inventory; a customization is the view of it.** The car list is saved whole and
 * the customization decides, at render time, which of them are on the road. `hasRoad` is then a
 * property of the theme rather than of a slider — which is what `SceneSpace.roadEdgeMarginFraction`
 * already says it must be.
 */
class SavedThemeCarLayoutTest {

    private val beach = ThemeCatalog.byId("beach")
    private val rawBeach = SceneObjectCatalog.layoutFor("beach", beach.accentColor)

    /**
     * **The real save path**, not a copy of it.
     *
     * `snapshotEntry` is a plain `internal fun` rather than a Composable, and everything it
     * touches is pure, so the test calls the shipped function. That is what makes the mutation
     * check meaningful: putting `filter { keepCar(it) }` back into the source has to break these
     * tests, and it cannot if they build the entry themselves.
     *
     * Passing the customization as the *pending* one for `beach` is how the gallery has it when
     * the user presses save: `resolveActiveCustomization` returns the live edit for the theme
     * being edited.
     */
    private fun saveBeachAt(customization: SceneCustomization) = snapshotEntry(
        targetId = "beach",
        targetName = "Beach",
        sourceThemeId = "beach",
        pendingCustomization = customization,
        pendingCustomizationThemeId = "beach",
    )

    /** The pre-fix behaviour, kept only so the tests can say what they are ruling out. */
    private fun saveBeachAtTheOldWay(customization: SceneCustomization) =
        saveBeachAt(customization).let {
            it.copy(layout = it.layout.copy(cars = rawBeach.cars.filter { car -> customization.keepCar(car) }))
        }

    private fun defaults() = defaultCustomizationFor("beach")
    private fun atDensity(d: Float) = defaults().let { it.copy(cars = it.cars.copy(density = d)) }
    private fun carsOff() = defaults().let { it.copy(cars = it.cars.copy(visible = false)) }

    /** What the renderer computes from an entry, without needing a renderer. */
    private fun hasRoad(entry: CustomThemeEntry) = entry.layout.cars.isNotEmpty()
    private fun visibleCars(entry: CustomThemeEntry) = entry.layout.cars.count { entry.customization.keepCar(it) }

    private fun reload(entry: CustomThemeEntry): CustomThemeEntry =
        customThemeDataFromJsonString(CustomThemeData(overrides = mapOf("beach" to entry)).toJsonString())
            .overrides.getValue("beach")

    // ------------------------------------------------------------------ 1, 2, 3

    /**
     * Both halves are asserted separately, and in this order, on purpose.
     *
     * The entry **as `snapshotEntry` produces it** is checked first, because the reload path now
     * repairs a damaged built-in override — which is right for a user and wrong for a test, since
     * it would hide a regression in the save path behind the safety net that exists for the users
     * who already have one.
     */
    @Test
    fun `savingAtLowCarDensityDoesNotRemoveRoad`() {
        val justSaved = saveBeachAt(atDensity(0.1f))
        assertEquals("the save thinned the car inventory", rawBeach.cars.size, justSaved.layout.cars.size)
        assertTrue("the save produced a theme with no road", hasRoad(justSaved))

        val saved = reload(justSaved)
        assertEquals("the car inventory was thinned by the save", rawBeach.cars.size, saved.layout.cars.size)
        assertEquals("the user's density was not preserved", 0.1f, saved.customization.cars.density, 0.0001f)
        assertTrue("the saved theme lost its road", hasRoad(saved))
        // and the look is still the 10% look, not all the traffic
        assertTrue("saving at 10% put every car back on the road", visibleCars(saved) < rawBeach.cars.size)
    }

    @Test
    fun `savingWithCarsOffDoesNotRemoveRoad`() {
        val justSaved = saveBeachAt(carsOff())
        assertEquals("the save emptied the car inventory", rawBeach.cars.size, justSaved.layout.cars.size)
        assertTrue("the save produced a theme with no road", hasRoad(justSaved))

        val saved = reload(justSaved)
        assertEquals("the car inventory was emptied by the save", rawBeach.cars.size, saved.layout.cars.size)
        assertFalse("the user's Cars-off choice was not preserved", saved.customization.cars.visible)
        assertTrue("the saved theme lost its road", hasRoad(saved))
        assertEquals("cars are drawn although the user switched them off", 0, visibleCars(saved))
    }

    @Test
    fun `raisingDensityAfterSaveShowsTrafficAgain`() {
        val justSaved = saveBeachAt(atDensity(0.1f))
        assertEquals(
            "the whole car pool is not reachable from the entry the save produced",
            rawBeach.cars.size,
            visibleCars(justSaved.copy(customization = atDensity(1f))),
        )

        val saved = reload(justSaved)
        val restored = saved.copy(customization = atDensity(1f))
        assertEquals(
            "the whole car pool is not reachable after raising the density again",
            rawBeach.cars.size,
            visibleCars(restored),
        )
        assertTrue(hasRoad(restored))
    }

    /**
     * The saved theme looks exactly like what was on screen when it was saved.
     *
     * This is the claim the fix has to keep, and it is asserted as an equality of the actual car
     * set rather than of a count: the built-in generator already emits the canonical lane, speed
     * and loop-slot values, so `canonicaliseTraffic` is a no-op on a whole list and the reloaded
     * theme filters down to precisely the cars that were visible at save time.
     */
    @Test
    fun `a theme saved at a given density shows exactly the traffic it had when saved`() {
        for (density in listOf(1f, 0.65f, 0.4f, 0.2f, 0.1f)) {
            val customization = atDensity(density)
            val onScreenBefore = rawBeach.cars.filter { customization.keepCar(it) }
            val saved = reload(saveBeachAt(customization))
            val onScreenAfter = saved.layout.cars.filter { saved.customization.keepCar(it) }
            assertEquals(
                "the traffic changed when the theme was saved at density $density",
                onScreenBefore,
                onScreenAfter,
            )
        }
    }

    /** The old behaviour, named so the tests above cannot quietly stop ruling anything out. */
    @Test
    fun `the old save really did produce a theme with no road`() {
        assertFalse("the pre-fix save no longer reproduces the defect", hasRoad(saveBeachAtTheOldWay(atDensity(0.1f))))
        assertFalse(hasRoad(saveBeachAtTheOldWay(carsOff())))
        assertTrue("even the old save kept a road at full density", hasRoad(saveBeachAtTheOldWay(atDensity(1f))))
    }

    // ------------------------------------------------------------------ 4, 5, 6, 7

    /** A distinctive customization, so "it kept the defaults" cannot pass for "it kept mine". */
    private fun distinctiveCustomization() = defaults().copy(
        cars = defaults().cars.copy(visible = true, density = 1f, colorDay1 = 0x11223344),
        hillsColorDay = 0x55667788,
        houses = defaults().houses.copy(density = 0.37f),
        winterColorsEnabled = true,
    )

    private fun damagedBeachOverride(customization: SceneCustomization = distinctiveCustomization()) =
        CustomThemeData(
            overrides = mapOf(
                "beach" to CustomThemeEntry(
                    id = "beach",
                    name = "Beach",
                    theme = beach,
                    layout = SceneObjectLayout(staticObjects = rawBeach.staticObjects, cars = emptyList()),
                    customization = customization,
                ),
            ),
        )

    @Test
    fun `corruptedBuiltinOverrideIsRepaired`() {
        val damaged = damagedBeachOverride()
        assertFalse("the fixture is not actually damaged", hasRoad(damaged.overrides.getValue("beach")))

        val repaired = damaged.repairBuiltInOverrides().overrides.getValue("beach")
        assertEquals("the built-in's cars were not restored", rawBeach.cars.size, repaired.layout.cars.size)
        assertTrue("the repaired theme still has no road", hasRoad(repaired))
        assertEquals("the repaired theme shows no traffic at 100%", rawBeach.cars.size, visibleCars(repaired))
    }

    @Test
    fun `repairDoesNotChangeCustomization`() {
        val before = distinctiveCustomization()
        val repaired = damagedBeachOverride(before).repairBuiltInOverrides().overrides.getValue("beach")
        assertEquals("the repair rewrote the user's customization", before, repaired.customization)
        // and nothing else about the entry moved either
        val original = damagedBeachOverride(before).overrides.getValue("beach")
        assertEquals("name", original.name, repaired.name)
        assertEquals("id", original.id, repaired.id)
        assertEquals("theme", original.theme, repaired.theme)
        assertEquals("theme colours", original.theme.skyDay.toList(), repaired.theme.skyDay.toList())
        assertEquals("static objects", original.layout.staticObjects, repaired.layout.staticObjects)
    }

    /** A repair at a low density restores the road without restoring the traffic. */
    @Test
    fun `a repaired theme keeps the density the user chose`() {
        val thinned = distinctiveCustomization().let { it.copy(cars = it.cars.copy(density = 0.1f)) }
        val repaired = damagedBeachOverride(thinned).repairBuiltInOverrides().overrides.getValue("beach")
        assertEquals("the density was not preserved", 0.1f, repaired.customization.cars.density, 0.0001f)
        assertTrue("the road did not come back", hasRoad(repaired))
        assertTrue("the repair put all the traffic back", visibleCars(repaired) < rawBeach.cars.size)
    }

    @Test
    fun `repairIsIdempotent`() {
        val once = damagedBeachOverride().repairBuiltInOverrides()
        val twice = once.repairBuiltInOverrides()
        assertEquals("a second repair changed the data", once, twice)
        // and a third, and on a payload that never needed repairing at all
        assertEquals(once, twice.repairBuiltInOverrides())
        val healthy = CustomThemeData(overrides = mapOf("beach" to saveBeachAt(defaults())))
        assertSame("a healthy payload was rebuilt for no reason", healthy, healthy.repairBuiltInOverrides())
    }

    @Test
    fun `standaloneCustomThemeWithZeroCarsIsNotSpeculativelyRepaired`() {
        val standalone = CustomThemeEntry(
            id = "custom:1700000000000",
            name = "My own",
            theme = beach.copy(id = "custom:1700000000000", displayName = "My own"),
            layout = SceneObjectLayout(staticObjects = rawBeach.staticObjects, cars = emptyList()),
            customization = distinctiveCustomization(),
        )
        val data = CustomThemeData(customThemes = listOf(standalone))
        assertEquals("a standalone theme was repaired on a guess", data, data.repairBuiltInOverrides())
        assertTrue(data.repairBuiltInOverrides().customThemes.single().layout.cars.isEmpty())
    }

    /** And an override of an id that is not a built-in has nothing to be repaired from. */
    @Test
    fun `an override with no built-in behind it is left alone`() {
        val data = CustomThemeData(
            overrides = mapOf(
                "not-a-built-in" to CustomThemeEntry(
                    id = "not-a-built-in",
                    name = "Ghost",
                    theme = beach.copy(id = "not-a-built-in"),
                    layout = SceneObjectLayout(staticObjects = emptyList(), cars = emptyList()),
                    customization = defaults(),
                ),
            ),
        )
        assertEquals(data, data.repairBuiltInOverrides())
    }

    // ------------------------------------------------------------------ the load path

    /**
     * The repair happens where every reader of this data goes through, including a wallpaper
     * service that starts with no UI at all.
     */
    @Test
    fun `a damaged override is repaired simply by being loaded`() {
        val onDisk = damagedBeachOverride().toJsonString()
        val loaded = customThemeDataFromJsonString(onDisk).overrides.getValue("beach")
        assertTrue("loading did not repair the damaged override", hasRoad(loaded))
        assertEquals(rawBeach.cars.size, loaded.layout.cars.size)
        assertEquals(distinctiveCustomization(), loaded.customization)
    }

    @Test
    fun `three different built-in themes are all repairable`() {
        for (id in listOf("beach", "city", "winter")) {
            val theme = ThemeCatalog.byId(id)
            val raw = SceneObjectCatalog.layoutFor(id, theme.accentColor)
            val data = CustomThemeData(
                overrides = mapOf(
                    id to CustomThemeEntry(
                        id = id,
                        name = theme.displayName,
                        theme = theme,
                        layout = SceneObjectLayout(staticObjects = raw.staticObjects, cars = emptyList()),
                        customization = defaultCustomizationFor(id),
                    ),
                ),
            )
            val repaired = data.repairBuiltInOverrides().overrides.getValue(id)
            assertEquals("$id was not repaired", raw.cars.size, repaired.layout.cars.size)
            assertNotEquals("$id has no cars to repair from", 0, raw.cars.size)
        }
    }
}
