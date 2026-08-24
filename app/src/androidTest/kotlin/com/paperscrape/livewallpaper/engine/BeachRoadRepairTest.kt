package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The repaired road, on the pixels.
 *
 * `SavedThemeCarLayoutTest` proves the data is repaired. This proves the repair is the thing a
 * user sees: a `beach` override with no cars renders without a road, the same override after the
 * load-time repair renders with one, and the only thing that still legitimately takes the road
 * away is the user switching Cars off.
 *
 * The road is measured rather than compared against a golden, for the reason the goldens
 * themselves demonstrated in v4.3: a whole-frame tolerance is too coarse to be trusted with a
 * question this specific.
 */
class BeachRoadRepairTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val beach = ThemeCatalog.byId("beach")
    private val rawBeach = SceneObjectCatalog.layoutFor("beach", beach.accentColor)

    @Before fun clean() = CustomThemeRegistry.update(CustomThemeData.EMPTY)
    @After fun tidy() = CustomThemeRegistry.update(CustomThemeData.EMPTY)

    /** A `beach` override in the state the v4.2 save produced: every static object, no cars. */
    private fun damagedBeach(
        customization: SceneCustomization = defaultCustomizationFor("beach"),
    ) = CustomThemeData(
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

    private fun render(carsVisible: Boolean = true, carsDensity: Float = 1f): Bitmap =
        SceneGolden.render(
            GoldenScene(
                name = "beach-road-repair",
                dayPhase = GoldenScene.day(),
                warmUpFrames = SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES,
                themeId = "beach",
                customise = { it.copy(cars = it.cars.copy(visible = carsVisible, density = carsDensity)) },
            ),
        )

    /**
     * Whether a road band is on screen, as the colour distance between the middle of the lanes and
     * the ground just above them. Terrain either differs from the tarmac or there is no tarmac.
     */
    private fun roadIsOnScreen(bmp: Bitmap): Boolean {
        fun rowAverage(y: Int): IntArray {
            val acc = IntArray(3)
            for (x in 0 until SceneGolden.WIDTH) {
                val p = bmp.getPixel(x, y)
                acc[0] += (p shr 16) and 0xFF
                acc[1] += (p shr 8) and 0xFF
                acc[2] += p and 0xFF
            }
            return IntArray(3) { acc[it] / SceneGolden.WIDTH }
        }
        val lane = rowAverage(
            ((SceneSpace.ROAD_LANE_FAR_Y_FRACTION + SceneSpace.ROAD_LANE_NEAR_Y_FRACTION) / 2f *
                SceneGolden.HEIGHT).toInt(),
        )
        val ground = rowAverage((SceneSpace.PAVEMENT_FAR_Y_FRACTION * SceneGolden.HEIGHT).toInt() - 6)
        return (0..2).maxOf { kotlin.math.abs(lane[it] - ground[it]) } > 12
    }

    /** How many pixels the vehicles paint, isolated by density -- never by visibility. */
    private fun vehiclePixels(): Int {
        val none = render(carsDensity = 0f)
        val all = render(carsDensity = 1f)
        var n = 0
        for (y in 0 until SceneGolden.HEIGHT) {
            for (x in 0 until SceneGolden.WIDTH) if (none.getPixel(x, y) != all.getPixel(x, y)) n++
        }
        return n
    }

    // ------------------------------------------------------------------ the defect, as pixels

    @Test
    fun aDamagedBeachOverrideRendersWithoutARoad() {
        CustomThemeRegistry.update(damagedBeach())
        assertFalse(
            "a beach override with no cars still drew a road -- the fixture is not reproducing the defect",
            roadIsOnScreen(render()),
        )
    }

    @Test
    fun theSameOverrideRendersWithARoadOnceItHasBeenLoadedAndRepaired() {
        // Exactly what the next start of the app does: read the stored bytes back.
        val repaired = customThemeDataFromJsonString(damagedBeach().toJsonString())
        CustomThemeRegistry.update(repaired)
        assertEquals(
            "the repair did not restore the built-in's cars",
            rawBeach.cars.size,
            repaired.overrides.getValue("beach").layout.cars.size,
        )
        assertTrue("the repaired beach still has no road on screen", roadIsOnScreen(render()))
        assertTrue("the repaired beach shows no traffic at 100%", vehiclePixels() > 500)
    }

    /** And the one thing that is still allowed to take the road away is the user's own switch. */
    @Test
    fun aRepairedBeachStillLosesItsRoadWhenTheUserSwitchesCarsOff() {
        CustomThemeRegistry.update(customThemeDataFromJsonString(damagedBeach().toJsonString()))
        assertTrue("cars on: no road", roadIsOnScreen(render(carsVisible = true)))
        assertFalse("cars off: the road is still drawn", roadIsOnScreen(render(carsVisible = false)))
        // ...and not when they merely turn the density down, which is the v4.2 rule this keeps
        assertTrue("density 10%: the road went away", roadIsOnScreen(render(carsDensity = 0.1f)))
        assertTrue("density 0%: the road went away", roadIsOnScreen(render(carsDensity = 0f)))
    }

    /** The user's own settings are still theirs after the repair. */
    @Test
    fun theRepairLeavesTheUsersCustomizationExactlyAsItWas() {
        val mine = defaultCustomizationFor("beach").let {
            it.copy(
                cars = it.cars.copy(density = 0.1f),
                hillsColorDay = 0x11223344,
                winterColorsEnabled = true,
            )
        }
        val repaired = customThemeDataFromJsonString(damagedBeach(mine).toJsonString())
            .overrides.getValue("beach")
        assertEquals("the repair rewrote the customization", mine, repaired.customization)
        CustomThemeRegistry.update(customThemeDataFromJsonString(damagedBeach(mine).toJsonString()))
        assertTrue("a repaired theme saved at 10% still has no road", roadIsOnScreen(render(carsDensity = 0.1f)))
    }

    // ------------------------------------------------------------------ reset still works

    /**
     * The repair must not make "Reset to default" harder to reach or undo it.
     *
     * Against the real store: a damaged override is loaded and repaired, then cleared, and beach
     * must go back to resolving as the plain built-in with nobody's override in the way.
     */
    @Test
    fun resetStillRemovesBuiltinOverride() { runBlocking {
        val store = CustomThemeStore(context)
        store.replaceAll(CustomThemeData.EMPTY)
        store.setOverride("beach", damagedBeach().overrides.getValue("beach"))

        val loaded = store.dataFlow.first()
        CustomThemeRegistry.update(loaded)
        assertTrue("the override was not repaired on load", CustomThemeRegistry.hasOverride("beach"))
        assertEquals(rawBeach.cars.size, loaded.overrides.getValue("beach").layout.cars.size)
        assertTrue("the repaired override has no road", roadIsOnScreen(render()))

        store.clearOverride("beach")
        val afterReset = store.dataFlow.first()
        CustomThemeRegistry.update(afterReset)
        assertFalse("reset did not remove the override", CustomThemeRegistry.hasOverride("beach"))
        assertTrue("the override came back after a reset", afterReset.overrides.isEmpty())
        assertTrue("beach lost its road after a reset", roadIsOnScreen(render()))

        store.replaceAll(CustomThemeData.EMPTY)
    } }
}
