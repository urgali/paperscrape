package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Rain, snow and lightning measured against the world they fall through, on real pixels.
 *
 * ### Why both a floor and a ceiling, on four viewports
 *
 * This suite has now been wrong in both directions, and each time only one bound existed.
 *
 * **v4.4** had a floor and no ceiling: it asserted rain covered *at least* a share of a phone
 * frame, which the fix satisfied by making every drop three times longer. A raindrop ended up
 * **1.15 times the height of the pedestrian beside it** and no test objected.
 *
 * Before that, nothing measured a second viewport at all, so an effect sized in absolute pixels
 * looked correct at 360x800 and was invisible at 1080x2424.
 *
 * So every property here is checked **at four viewport sizes**, and every one is bounded **above
 * and below**, against something in the scene rather than against a share of the frame: a
 * pedestrian for the falling particles, the painted skyline for the bolt.
 *
 * ### What "Location off, Live Weather off" means at this layer
 *
 * Exactly `liveWeatherOverride == null`. The service clears the snapshot when Live Weather is off
 * and never fetches one without a location, so location-off, location-on-but-weather-off and
 * weather-on-with-nowhere-to-check all arrive here as the same state.
 */
class PrecipitationPixelTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /** The four viewports: the golden frame, a mid phone, a Pixel 9, and a tall flagship. */
    private val viewports = listOf(360 to 800, 720 to 1600, 1080 to 2424, 1440 to 3200)

    private fun scene(
        precipitation: Boolean = true,
        type: PrecipitationType = PrecipitationType.RAIN,
        intensity: Float = 0.5f,
        clouds: Boolean = true,
        people: Boolean? = null,
        buildings: Boolean? = null,
        live: LiveWeatherSnapshot? = null,
    ) = GoldenScene(
        name = "precipitation", dayPhase = GoldenScene.day(), themeId = "sunset", weather = live,
        customise = { c ->
            c.copy(
                clouds = c.clouds.copy(visible = clouds),
                people = people?.let { c.people.copy(visible = it, density = 1f) } ?: c.people,
                buildings = buildings?.let { c.buildings.copy(visible = it, density = 1f) } ?: c.buildings,
                precipitation = c.precipitation.copy(
                    visible = precipitation, type = type, intensity = intensity,
                ),
            )
        },
    )

    private fun renderAt(w: Int, h: Int, scene: GoldenScene): Bitmap {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        val renderer = PaperRenderer(w, h, context)
        scene.configure(renderer)
        renderer.draw(target, scene.dayPhase, SceneTime(scene.sceneSeconds), 0f)
        target.unbind()
        return bitmap
    }

    private fun maskOf(a: Bitmap, b: Bitmap, w: Int, h: Int): Array<BooleanArray> {
        val m = Array(h) { BooleanArray(w) }
        for (y in 0 until h) for (x in 0 until w) {
            val p = a.getPixel(x, y)
            val q = b.getPixel(x, y)
            val d = maxOf(
                abs(((p shr 16) and 0xFF) - ((q shr 16) and 0xFF)),
                abs(((p shr 8) and 0xFF) - ((q shr 8) and 0xFF)),
                abs((p and 0xFF) - (q and 0xFF)),
            )
            m[y][x] = d > 6
        }
        return m
    }

    /** The tallest run of changed pixels in any column: a figure's height, a building's height. */
    private fun tallest(m: Array<BooleanArray>, w: Int, h: Int): Int {
        var best = 0
        for (x in 0 until w) {
            var run = 0
            for (y in 0 until h) if (m[y][x]) { run++; if (run > best) best = run } else run = 0
        }
        return best
    }

    private class Particles(val count: Int, val medianHeight: Int, val medianWidth: Int)

    /** Connected components of the effect layer: one blob is one drop or one flake. */
    private fun particles(m: Array<BooleanArray>, w: Int, h: Int): Particles {
        val seen = Array(h) { BooleanArray(w) }
        val heights = ArrayList<Int>()
        val widths = ArrayList<Int>()
        val sy = IntArray(w * h)
        val sx = IntArray(w * h)
        for (y0 in 0 until h) for (x0 in 0 until w) {
            if (!m[y0][x0] || seen[y0][x0]) continue
            var top = 0
            sy[0] = y0; sx[0] = x0; top = 1; seen[y0][x0] = true
            var minY = y0; var maxY = y0; var minX = x0; var maxX = x0; var n = 0
            while (top > 0) {
                top--
                val cy = sy[top]; val cx = sx[top]; n++
                if (cy < minY) minY = cy
                if (cy > maxY) maxY = cy
                if (cx < minX) minX = cx
                if (cx > maxX) maxX = cx
                for (dy in -1..1) for (dx in -1..1) {
                    val ny = cy + dy; val nx = cx + dx
                    if (ny in 0 until h && nx in 0 until w && m[ny][nx] && !seen[ny][nx]) {
                        seen[ny][nx] = true; sy[top] = ny; sx[top] = nx; top++
                    }
                }
            }
            if (n >= 6) { heights.add(maxY - minY + 1); widths.add(maxX - minX + 1) }
        }
        heights.sort(); widths.sort()
        return Particles(
            heights.size,
            if (heights.isEmpty()) 0 else heights[heights.size / 2],
            if (widths.isEmpty()) 0 else widths[widths.size / 2],
        )
    }

    private fun coverage(m: Array<BooleanArray>, w: Int, h: Int): Double {
        var n = 0
        for (y in 0 until h) for (x in 0 until w) if (m[y][x]) n++
        return n.toDouble() / (w * h)
    }

    /** How much of the band the effect falls through actually has something in it. */
    private fun spread(m: Array<BooleanArray>, w: Int, h: Int): Pair<Double, Int> {
        val bandTop = (h * 0.20f).toInt()
        val cols = 6
        val rows = 12
        val cellW = w / cols
        val cellH = (h - bandTop) / rows
        var occupied = 0
        var biggestHole = 0
        for (r in 0 until rows) {
            var run = 0
            for (c in 0 until cols) {
                var any = false
                var y = bandTop + r * cellH
                outer@ while (y < bandTop + (r + 1) * cellH) {
                    var x = c * cellW
                    while (x < (c + 1) * cellW) {
                        if (m[y][x]) { any = true; break@outer }
                        x++
                    }
                    y++
                }
                if (any) { occupied++; run = 0 } else { run++; if (run > biggestHole) biggestHole = run }
            }
        }
        return occupied.toDouble() / (cols * rows) to biggestHole
    }

    /** The tallest pedestrian actually painted: the yardstick a falling particle answers to. */
    private fun pedestrianPx(w: Int, h: Int): Int {
        val without = renderAt(w, h, scene(precipitation = false, people = false))
        val with = renderAt(w, h, scene(precipitation = false, people = true))
        return tallest(maskOf(without, with, w, h), w, h)
    }

    private fun effect(w: Int, h: Int, type: PrecipitationType, clouds: Boolean = true):
        Triple<Particles, Double, Pair<Double, Int>> {
        val dry = renderAt(w, h, scene(precipitation = false, clouds = clouds))
        val wet = renderAt(w, h, scene(type = type, clouds = clouds))
        val m = maskOf(dry, wet, w, h)
        return Triple(particles(m, w, h), coverage(m, w, h), spread(m, w, h))
    }

    // -- rain ------------------------------------------------------------------------------------

    /**
     * **The two bounds together.** A raindrop must be clearly smaller than a pedestrian and
     * clearly not nothing, on every viewport.
     *
     * Measured with v4.5's sizes the median streak is about 0.40 of the tallest painted
     * pedestrian; v4.4 shipped 1.13, and the pre-v4.4 absolute pixels fell to 0.11 on the largest
     * viewport. The band below refuses both.
     */
    @Test
    fun aRaindropIsSmallerThanAPedestrianAndBiggerThanNothing() {
        for ((w, h) in viewports) {
            val person = pedestrianPx(w, h)
            val (p, _, _) = effect(w, h, PrecipitationType.RAIN)
            val ratio = p.medianHeight.toDouble() / person
            println("MEASURE ${w}x$h rain median ${p.medianHeight}px / pedestrian ${person}px = %.3f".format(ratio))
            assertTrue("no rain at all on ${w}x$h", p.count > 0)
            assertTrue(
                "a raindrop is $ratio of a pedestrian on ${w}x$h -- too large",
                ratio <= 0.62,
            )
            assertTrue(
                "a raindrop is $ratio of a pedestrian on ${w}x$h -- too small to see",
                ratio >= 0.20,
            )
        }
    }

    /** Presence, bounded on both sides: enough ink to read, not so much it is a curtain of sticks. */
    @Test
    fun rainCoversAConsistentShareOfEveryViewport() {
        for ((w, h) in viewports) {
            val (_, cov, _) = effect(w, h, PrecipitationType.RAIN)
            println("MEASURE ${w}x$h rain coverage %.4f%%".format(cov * 100))
            assertTrue("rain covers only ${cov * 100}% of ${w}x$h", cov >= 0.0015)
            assertTrue("rain covers ${cov * 100}% of ${w}x$h -- far too much", cov <= 0.0060)
        }
    }

    /**
     * **Density, which is the property v4.4 compensated for with size.** The curtain has to be
     * spread across the frame, not bunched: at the shipped pool the swept frames filled 86 % of a
     * 6x12 grid with no dry hole wider than two of six columns; at a pool of 90 they filled 46 %
     * and left holes four columns wide.
     */
    @Test
    fun rainIsSpreadAcrossTheFrameRatherThanBunched() {
        for ((w, h) in viewports) {
            val (_, _, sp) = effect(w, h, PrecipitationType.RAIN)
            val (occupancy, hole) = sp
            println("MEASURE ${w}x$h rain occupancy %.0f%% biggest hole $hole".format(occupancy * 100))
            assertTrue("rain fills only ${occupancy * 100}% of the grid on ${w}x$h", occupancy >= 0.70)
            assertTrue("rain leaves a dry hole $hole columns wide on ${w}x$h", hole <= 3)
        }
    }

    // -- snow ------------------------------------------------------------------------------------

    /** A flake is judged on width, being round, and answers to a head rather than a whole figure. */
    @Test
    fun aSnowflakeIsSmallerThanAHeadAndBiggerThanNothing() {
        for ((w, h) in viewports) {
            val person = pedestrianPx(w, h)
            val (p, _, _) = effect(w, h, PrecipitationType.SNOW)
            val ratio = p.medianWidth.toDouble() / person
            println("MEASURE ${w}x$h snow median ${p.medianWidth}px / pedestrian ${person}px = %.3f".format(ratio))
            assertTrue("no snow at all on ${w}x$h", p.count > 0)
            assertTrue("a snowflake is $ratio of a pedestrian on ${w}x$h -- too large", ratio <= 0.28)
            assertTrue("a snowflake is $ratio of a pedestrian on ${w}x$h -- too small", ratio >= 0.07)
        }
    }

    @Test
    fun snowCoversAConsistentShareOfEveryViewport() {
        for ((w, h) in viewports) {
            val (_, cov, _) = effect(w, h, PrecipitationType.SNOW)
            println("MEASURE ${w}x$h snow coverage %.4f%%".format(cov * 100))
            assertTrue("snow covers only ${cov * 100}% of ${w}x$h", cov >= 0.0015)
            assertTrue("snow covers ${cov * 100}% of ${w}x$h -- far too much", cov <= 0.0070)
        }
    }

    // -- lightning ---------------------------------------------------------------------------------

    /**
     * **A bolt never out-tops the skyline it strikes behind.**
     *
     * The oracle is the *painted* building, not the size table: a tower is 16.8 m but is drawn far
     * back, where perspective shrinks it, so the metre reading made a bolt that measured taller
     * than anything in the scene look defensible. Measured on 1080x2424 the tallest painted
     * building is 288 px and v4.4's tallest bolt was 325 px.
     *
     * The bolt itself fires off an unseeded timer, so the *constant* is compared rather than a
     * captured strike -- which is the stronger check anyway, since it bounds every roll rather
     * than the one that happened to be caught.
     */
    @Test
    fun aLightningBoltNeverOutTopsThePaintedSkyline() {
        for ((w, h) in viewports) {
            val without = renderAt(w, h, scene(precipitation = false, people = false, buildings = false))
            val with = renderAt(w, h, scene(precipitation = false, people = false, buildings = true))
            val building = tallest(maskOf(without, with, w, h), w, h)
            val tallestBolt = h * (PaperRenderer.LIGHTNING_BOLT_MIN_HEIGHT_FRACTION +
                PaperRenderer.LIGHTNING_BOLT_HEIGHT_SPREAD_FRACTION)
            val ratio = tallestBolt / building
            println("MEASURE ${w}x$h bolt %.0fpx / building ${building}px = %.3f".format(tallestBolt, ratio))
            assertTrue("no building painted on ${w}x$h", building > 0)
            assertTrue(
                "the tallest bolt is $ratio of the tallest painted building on ${w}x$h",
                ratio <= 0.85f,
            )
            assertTrue("the bolt has shrunk to nothing on ${w}x$h", ratio >= 0.25f)
        }
    }

    // -- the state contract, unchanged from v4.4 ---------------------------------------------------

    @Test
    fun rainStillFallsWithTheCloudLayerSwitchedOff() {
        val (p, cov, _) = effect(1080, 2424, PrecipitationType.RAIN, clouds = false)
        assertTrue("no rain with clouds off", p.count > 0 && cov > 0.0015)
    }

    @Test
    fun rainFromLiveWeatherIsOnScreenToo() {
        val raining = LiveWeatherSnapshot(
            precipitationType = PrecipitationType.RAIN, precipitationIntensity = 0.5f,
            cloudCoverFraction = 0.9f, isThunderstorm = false, fetchedAtMillis = 0L,
        )
        val dry = renderAt(1080, 2424, scene(precipitation = false, live = raining.copy(
            precipitationType = null, precipitationIntensity = 0f,
        )))
        val wet = renderAt(1080, 2424, scene(precipitation = false, live = raining))
        val m = maskOf(dry, wet, 1080, 2424)
        assertTrue("Live Weather's rain is not on screen", coverage(m, 1080, 2424) > 0.0015)
    }

    @Test
    fun intensityStillGovernsHowMuchFalls() {
        fun cov(i: Float): Double {
            val dry = renderAt(1080, 2424, scene(precipitation = false))
            val wet = renderAt(1080, 2424, scene(intensity = i))
            return coverage(maskOf(dry, wet, 1080, 2424), 1080, 2424)
        }
        assertTrue("heavier rain is not heavier", cov(1f) > cov(0.15f) * 2)
    }
}
