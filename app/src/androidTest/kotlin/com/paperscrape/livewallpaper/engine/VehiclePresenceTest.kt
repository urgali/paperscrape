package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Debug
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **[VehiclePresence.measure] counts the road band's colours without boxing, and decides what a read
 * pixel by pixel decides** -- the same tarmac, the same pixels not tarmac, the same runs of vehicle
 * columns, on every frame. That nothing is boxed is counted: [theRoadIsMeasuredWithoutBoxing].
 *
 * [pixelByPixel] is the measure written the plain way, one `getPixel` per pixel into a
 * `HashMap<Int, Int>` and the tarmac its `maxByOrNull`: the reference the faster one has to agree with.
 * The two are compared whole ([VehiclePresence.Result] is a data class) on the traffic goldens' frames,
 * on a dusk drained of cars as `CarNightCrossfadeTest` drains it -- where a darkening car sits at the
 * threshold column by column -- and on two frames made so that two colours tie for the tarmac, where
 * `maxByOrNull` keeps the first of the tied colours in the map's own order: the commonest by number,
 * the first met, the smallest or the largest colour would each be another tarmac, and other runs.
 */
@RunWith(AndroidJUnit4::class)
class VehiclePresenceTest {

    @Test
    fun theTrafficFramesAndADuskAreMeasuredAsPixelByPixel() {
        for (scene in listOf(SharedGoldenScenes.trafficDay(), SharedGoldenScenes.trafficNight())) {
            val frame = SceneGolden.render(scene)
            assertEquals(scene.name, pixelByPixel(frame), VehiclePresence.measure(frame))
            frame.recycle()
        }

        // `CarNightCrossfadeTest`'s dusk: the day's traffic, then the night density at 0 draining the road.
        val scene = GoldenScene(
            name = "dusk-crossfade",
            dayPhase = GoldenScene.day(),
            warmUpFrames = SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES,
        )
        val renderer = PaperRenderer(
            SceneGolden.WIDTH,
            SceneGolden.HEIGHT,
            InstrumentationRegistry.getInstrumentation().targetContext,
        )
        scene.configure(renderer)
        renderer.sceneCustomization = renderer.sceneCustomization.let {
            it.copy(cars = it.cars.copy(visible = true, density = 1f), carsNightDensity = 0f)
        }
        var clock = SceneGolden.warmUp(renderer, SceneGolden.BlankTarget, scene)
        val bitmap = Bitmap.createBitmap(SceneGolden.WIDTH, SceneGolden.HEIGHT, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        var withVehicles = 0
        var hour = 19f
        while (hour < 22f) {
            clock += 1f / 30f
            renderer.draw(target, SunPositionCalculator.compute(hour24 = hour), clock, 1f / 30f)
            val expected = pixelByPixel(bitmap)
            assertEquals("dusk at $hour", expected, VehiclePresence.measure(bitmap))
            if (expected.runs.isNotEmpty()) withVehicles++
            hour += 0.05f
        }
        target.unbind()
        bitmap.recycle()
        // The sweep has to have seen cars on the road, or it compared empty roads only.
        assertTrue("no frame of the dusk had a vehicle on the road", withVehicles > 0)
    }

    @Test
    fun aTieForTheTarmacGoesToTheColourTheMapMeetsFirst() {
        // Two colours over 130 columns each of the band and a third over 100: the first two tie. In the
        // first frame the map meets the larger colour first, in the second the smaller; in both the
        // other one is met first reading the band, so each rule but the map's order picks another.
        for ((first, second) in listOf(
            0xFF1120C5.toInt() to 0xFF112044.toInt(),
            0xFF112022.toInt() to 0xFF112044.toInt(),
        )) {
            val frame = tiedRoad(mapsFirst = first, other = second)
            assertEquals(
                "%08X against %08X".format(first, second),
                pixelByPixel(frame),
                VehiclePresence.measure(frame),
            )
            frame.recycle()
        }
    }

    /**
     * **And it counts without boxing**: measuring a traffic frame allocates its arrays, its runs and its
     * result, fewer than [FEWER_OBJECTS_THAN] objects, where a count per pixel into a `HashMap<Int, Int>`
     * boxes an `Integer` for nearly every pixel of the band, some 17 000. Counted by `Debug`'s allocation
     * counter on this thread, after a first measure left uncounted (a method's first run can allocate
     * once), and believed only after the positive control: the counter sees a thousand objects
     * allocated under it.
     */
    @Test
    fun theRoadIsMeasuredWithoutBoxing() {
        val kept = arrayOfNulls<Any>(1_000)
        val control = allocationsDuring { for (i in kept.indices) kept[i] = Any() }
        assertTrue("ART counted $control objects for 1 000 allocated: the count below means nothing", control >= 1_000)
        val frame = SceneGolden.render(SharedGoldenScenes.trafficDay())
        val first = VehiclePresence.measure(frame)
        val objects = allocationsDuring { VehiclePresence.measure(frame) }
        frame.recycle()
        assertTrue("the frame measured has a vehicle on the road: $first", first.runs.isNotEmpty())
        assertTrue("measuring a traffic frame allocated $objects objects", objects < FEWER_OBJECTS_THAN)
    }

    /** Objects this thread allocated while [block] ran. */
    @Suppress("DEPRECATION")
    private inline fun allocationsDuring(block: () -> Unit): Int {
        Debug.startAllocCounting()
        Debug.resetThreadAllocCount()
        block()
        val count = Debug.getThreadAllocCount()
        Debug.stopAllocCounting()
        return count
    }

    /**
     * A frame whose every row is grey for 100 columns, then [other] for 130, then [mapsFirst] for 130:
     * [other] is met first reading the band, and [mapsFirst] comes first in a `HashMap<Int, Int>` of the
     * three (bucket 4 or 3 against 5, the grey 0, at the map's first size of 16).
     */
    private fun tiedRoad(mapsFirst: Int, other: Int): Bitmap {
        val w = SceneGolden.WIDTH
        val h = SceneGolden.HEIGHT
        val pixels = IntArray(w * h) { i ->
            val x = i % w
            when {
                x < 100 -> GREY
                x < 230 -> other
                else -> mapsFirst
            }
        }
        return Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
    }

    /** [VehiclePresence.measure], one pixel at a time and counted into a map: the reference. */
    private fun pixelByPixel(bitmap: Bitmap): VehiclePresence.Result {
        val h = bitmap.height
        val w = bitmap.width
        val margin = SceneSpace.roadEdgeMarginFraction(
            SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
            SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
        )
        val top = ((SceneSpace.ROAD_LANE_FAR_Y_FRACTION - margin) * h).toInt()
        val bottom = ((SceneSpace.ROAD_LANE_NEAR_Y_FRACTION + margin) * h).toInt()

        val counts = HashMap<Int, Int>()
        for (y in top..bottom) for (x in 0 until w) {
            val p = bitmap.getPixel(x, y) or (0xFF shl 24)
            counts[p] = (counts[p] ?: 0) + 1
        }
        val tarmac = counts.maxByOrNull { it.value }!!
        val bandPixels = (bottom - top + 1) * w

        var nonTarmac = 0
        val columnHit = BooleanArray(w)
        for (x in 0 until w) {
            var hits = 0
            for (y in top..bottom) {
                if (delta(bitmap.getPixel(x, y), tarmac.key) > 90) {
                    nonTarmac++
                    hits++
                }
            }
            columnHit[x] = hits >= (bottom - top) * VehiclePresence.VEHICLE_COLUMN_DEPTH_SHARE
        }
        val runs = ArrayList<IntRange>()
        var start = -1
        for (x in 0 until w) {
            if (columnHit[x]) {
                if (start < 0) start = x
            } else if (start >= 0) {
                if (x - start >= 12) runs.add(start until x)
                start = -1
            }
        }
        if (start >= 0 && w - start >= 12) runs.add(start until w)
        return VehiclePresence.Result(top, bottom, tarmac.value / bandPixels.toDouble(), nonTarmac, bandPixels, runs)
    }

    private fun delta(a: Int, b: Int): Int = maxOf(
        Math.abs(((a shr 16) and 0xFF) - ((b shr 16) and 0xFF)),
        Math.abs(((a shr 8) and 0xFF) - ((b shr 8) and 0xFF)),
        Math.abs((a and 0xFF) - (b and 0xFF)),
    ) * 3

    private companion object {
        const val GREY = 0xFF808080.toInt()

        /** A thousand: the arrays, the runs and the result of a measure are a few dozen; a boxed count is thousands. */
        const val FEWER_OBJECTS_THAN = 1_000
    }
}
