package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

/**
 * The A/B evidence for the vehicle-occupant scale change, as frames and as numbers.
 *
 * **Not a test.** It asserts nothing and, unless it is asked for, does nothing: every method
 * returns immediately without `-e captureAb true`, the same shape `SceneGolden`'s
 * `-e updateGoldens true` uses and for the same reason. It ships because the release it belongs to
 * is a judgement about how something looks, and the maintainer has to be able to regenerate the
 * pictures that judgement was made from.
 *
 * ### What it produces
 *
 * One 1080x2400 PNG per case into the app's own files directory, and one line per case to logcat
 * under [TAG] carrying every measurement in units the two builds can be compared in. The cases are
 * every vehicle type on every lane, plus one street with all four types and pedestrians together.
 *
 * ### Why the frames are comparable between two builds
 *
 * Everything that could differ is pinned. The layout is built here rather than taken from a theme,
 * so the same four vehicles stand in the same places; `speedFraction` is zero and the start delay
 * fixes each car's `progress`, so no warm-up and no clock drift; the scene clock, the sun position,
 * the scroll offset and the parallax are constants. Run it on v4.5 and on v4.6 and the only thing
 * that moved is what the release changed.
 *
 * ### Running it
 *
 * ```
 * ./gradlew installDebug installDebugAndroidTest
 * adb shell am instrument -w -e captureAb true \
 *   -e class com.paperscrape.livewallpaper.engine.VehicleOccupantAbCapture \
 *   com.paperscrape.livewallpaper.debug.test/androidx.test.runner.AndroidJUnitRunner
 * adb shell run-as com.paperscrape.livewallpaper.debug ls files/ab-capture
 * ```
 *
 * `connectedAndroidTest` would uninstall the app and take the directory with it, which is why the
 * instrumentation is driven directly and why the files go to the *internal* directory `run-as` can
 * still reach.
 */
@RunWith(AndroidJUnit4::class)
class VehicleOccupantAbCapture {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private fun asked(): Boolean =
        InstrumentationRegistry.getArguments().getString("captureAb")?.toBoolean() == true

    // ------------------------------------------------------------------ the scenes

    @Test
    fun captureEveryVehicleOnEveryLane() {
        if (!asked()) return
        val dir = outputDir()
        for (type in CarType.entries) {
            for ((laneName, lane) in LANES) {
                val name = "${type.name.lowercase()}_$laneName"
                val frame = render(listOf(car(type, lane, PROGRESS_CENTRE)), peopleDensity = 0f)
                write(frame, dir, name)
                reportVehicle(name, frame, type, lane, PROGRESS_CENTRE)
                frame.recycle()
            }
        }
    }

    /**
     * The same vehicles in winter, which is the season the occupant heads disagree about.
     *
     * The scale that puts a bust in a window is one number per family, derived from one
     * representative content height. The winter members of both families are taller than their
     * representative -- the hat -- so this is the case where a head can reach above the glass it
     * is supposed to sit behind, and the summer capture cannot show it.
     */
    @Test
    fun captureEveryVehicleInWinter() {
        if (!asked()) return
        val dir = outputDir()
        for (type in CarType.entries) {
            for ((laneName, lane) in LANES) {
                val name = "winter_${type.name.lowercase()}_$laneName"
                val frame = render(listOf(car(type, lane, PROGRESS_CENTRE)), peopleDensity = 0f, winter = true)
                write(frame, dir, name)
                reportVehicle(name, frame, type, lane, PROGRESS_CENTRE)
                frame.recycle()
            }
        }
    }

    /** The picture the whole change is about: people in cars and people on the pavement at once. */
    @Test
    fun captureAStreetWithTrafficAndPedestrians() {
        if (!asked()) return
        val dir = outputDir()
        val cars = listOf(
            car(CarType.PLAIN, SceneSpace.ROAD_LANE_FAR_Y_FRACTION, 0.22f),
            car(CarType.TAXI, SceneSpace.ROAD_LANE_FAR_Y_FRACTION, 0.66f),
            car(CarType.POLICE, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, 0.32f),
            car(CarType.FIRE_TRUCK, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, 0.76f),
        )
        val frame = render(cars, peopleDensity = 1f)
        write(frame, dir, "street_day")
        for (spec in cars) {
            reportVehicle(
                "street_day/${spec.type.name.lowercase()}",
                frame,
                spec.type,
                spec.laneYFraction,
                -spec.startDelaySeconds,
            )
        }
        frame.recycle()

        val night = render(cars, peopleDensity = 1f, day = false)
        write(night, dir, "street_night")
        night.recycle()
    }

    /**
     * The same street in winter, which is the season the `Exposure` rule is about.
     *
     * Pedestrians and the people in the cars are OUTDOORS and take the winter drawings; the
     * occupants of houses, shops and towers are INDOORS and must not. `IndoorClothingTest` pins
     * that deterministically off the drawable tables; this writes the frame it is a statement
     * about, so the rule can be looked at rather than only asserted.
     */
    @Test
    fun captureAWinterStreet() {
        if (!asked()) return
        val cars = listOf(
            car(CarType.PLAIN, SceneSpace.ROAD_LANE_FAR_Y_FRACTION, 0.22f),
            car(CarType.TAXI, SceneSpace.ROAD_LANE_FAR_Y_FRACTION, 0.66f),
            car(CarType.POLICE, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, 0.32f),
            car(CarType.FIRE_TRUCK, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, 0.76f),
        )
        val frame = render(cars, peopleDensity = 1f, winter = true)
        write(frame, outputDir(), "street_winter")
        frame.recycle()
    }

    /**
     * The other half of the comparison: a street with nobody driving on it.
     *
     * No scenery either, because a house's lit window paints an occupant in the same skin tones
     * and a whole-frame search would find one of those instead of a pedestrian. What is left is
     * the pavement, so the tallest face in the frame is the nearest adult walking on it -- the
     * figure a driver's head has to hold its own against.
     */
    @Test
    fun captureThePedestriansToCompareAgainst() {
        if (!asked()) return
        val frame = render(emptyList(), peopleDensity = 1f, scenery = false)
        write(frame, outputDir(), "pedestrians")
        reportPedestrians("pedestrians", frame)
        frame.recycle()
    }

    // ------------------------------------------------------------------ rendering

    private fun car(type: CarType, lane: Float, progress: Float) = CarObject(
        laneYFraction = lane,
        speedFraction = 0f,
        startDelaySeconds = -progress,
        color = 0xFFB4513C.toInt(),
        reverse = true,
        type = type,
    )

    /**
     * A whole scene -- sky, hills, road, traffic, people -- at a real phone's viewport.
     *
     * The custom layout reaches the renderer through the same override path a user's saved theme
     * uses, which is the only supported way to make `SceneObjectCatalog.layoutFor` return something
     * the catalogue did not build. The registry is put back afterwards.
     */
    private fun render(
        cars: List<CarObject>,
        peopleDensity: Float,
        day: Boolean = true,
        scenery: Boolean = true,
        winter: Boolean = false,
    ): Bitmap {
        val theme = ThemeCatalog.byId(THEME_ID)
        val defaults = defaultCustomizationFor(THEME_ID)
        val customization = defaults.copy(
            cars = defaults.cars.copy(visible = true, density = 1f),
            people = defaults.people.copy(visible = peopleDensity > 0f, density = peopleDensity),
            peopleNightDensity = peopleDensity,
            winterColorsEnabled = winter,
        )
        // The theme's own scenery, read before the override goes in so it is the catalogue's and
        // not the one this method is about to install.
        val objects =
            if (scenery) SceneObjectCatalog.layoutFor(THEME_ID, theme.accentColor).staticObjects else emptyList()
        CustomThemeRegistry.update(
            CustomThemeData(
                overrides = mapOf(
                    THEME_ID to CustomThemeEntry(
                        id = THEME_ID,
                        name = theme.displayName,
                        theme = theme,
                        layout = SceneObjectLayout(staticObjects = objects, cars = cars),
                        customization = customization,
                    ),
                ),
            ),
        )
        try {
            val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
            val target = CanvasSceneTarget()
            target.bind(Canvas(bitmap))
            val renderer = PaperRenderer(WIDTH, HEIGHT, context)
            renderer.theme = theme
            renderer.sceneCustomization = customization
            renderer.liveWeatherOverride = null
            renderer.homeScreenOffset = 0f
            renderer.swipeScrollEnabled = false
            renderer.scrollSpeed = 0f
            renderer.parallaxStrength = 1f
            val phase = if (day) SunPositionCalculator.compute(hour24 = 13f) else SunPositionCalculator.compute(hour24 = 1f)
            renderer.draw(target, phase, SceneTime(120.0), 0f)
            target.unbind()
            return bitmap
        } finally {
            // Back to what a fresh process has. Nothing else in the suite depends on an override
            // being present, and leaving one behind would change whatever ran next.
            CustomThemeRegistry.update(CustomThemeData.EMPTY)
        }
    }

    // ------------------------------------------------------------------ measuring

    /**
     * Every number the A/B turns on, for one vehicle, on one line.
     *
     * **Measured inside the vehicle's own rectangle and nowhere else.** The scene is full -- houses
     * with lit windows, a bar, a restaurant -- and every one of those paints occupants in the same
     * skin tones and glass in the same blue, so a whole-frame search finds the village rather than
     * the car. The rectangle comes from the car's placement and the lane's projection, both of
     * which are inputs here and not results.
     *
     * Faces are found by colour and connectivity, the glass by colour; neither is read back from
     * the constants this release moved. The line is plain text so two builds can be diffed by eye.
     */
    private fun reportVehicle(name: String, frame: Bitmap, type: CarType, lane: Float, progress: Float) {
        val base = if (type == CarType.FIRE_TRUCK) SceneSpace.FIRE_TRUCK_BASE_SCALE else SceneSpace.CAR_BASE_SCALE
        val unitPx = base * SceneSpace.perspectiveScaleAt(lane) * SceneSpace.sceneScale(HEIGHT.toFloat())
        // `reverse = true`, so drawCar mirrors the progress across the screen.
        val centreX = WIDTH - (progress * (WIDTH + 2 * CAR_MARGIN) - CAR_MARGIN)
        val ground = lane * HEIGHT
        val left = (centreX - CAR_HALF_WIDTH_UNITS * unitPx).toInt().coerceIn(0, WIDTH - 1)
        val right = (centreX + CAR_HALF_WIDTH_UNITS * unitPx).toInt().coerceIn(0, WIDTH - 1)
        val top = (ground - CAR_TALLEST_UNITS * unitPx).toInt().coerceIn(0, HEIGHT - 1)
        val bottom = ground.toInt().coerceIn(0, HEIGHT - 1)

        val width = right - left + 1
        val pixels = crop(frame, left, top, width, bottom - top + 1)
        val faces = blobs(pixels, width) { isSkin(it) }.sortedByDescending { it.height }
        val glass = box(pixels, width) { near(it, GLASS) }
        Log.i(
            TAG,
            "$name | unitPx=${fmt(unitPx)} faces=${faces.size} " +
                "driverFacePx=${faces.firstOrNull()?.height ?: -1} " +
                "passengerFacePx=${faces.getOrNull(1)?.height ?: -1} " +
                "driverFaceUnits=${fmt((faces.firstOrNull()?.height ?: 0) / unitPx)} " +
                "glassPx=${glass?.height ?: -1} glassUnits=${fmt((glass?.height ?: 0) / unitPx)} " +
                "driverScale=${fmt(SceneObjectRenderer.CAR_HEAD_SCALE)} " +
                "passengerScale=${fmt(SceneObjectRenderer.CAR_PASSENGER_SCALE)} " +
                "fireTruckScale=${fmt(SceneObjectRenderer.FIRE_TRUCK_HEAD_SCALE)}",
        )
    }

    /** The tallest face on a street that has nothing on it but people. */
    private fun reportPedestrians(name: String, frame: Bitmap) {
        val pixels = crop(frame, 0, 0, WIDTH, HEIGHT)
        val faces = blobs(pixels, WIDTH) { isSkin(it) }.sortedByDescending { it.height }
        val unitPx = SceneSpace.PERSON_BASE_SCALE *
            SceneSpace.perspectiveScaleAt(SceneSpace.PAVEMENT_NEAR_Y_FRACTION) *
            SceneSpace.sceneScale(HEIGHT.toFloat())
        Log.i(
            TAG,
            "$name | faces=${faces.size} " +
                "tallestFacePx=${faces.firstOrNull()?.height ?: -1} " +
                "tallestFaceUnits=${fmt((faces.firstOrNull()?.height ?: 0) / unitPx)} " +
                "nearRowUnitPx=${fmt(unitPx)}",
        )
    }

    private fun crop(frame: Bitmap, left: Int, top: Int, width: Int, height: Int): IntArray {
        val out = IntArray(width * height)
        frame.getPixels(out, 0, width, left, top, width, height)
        return out
    }

    private fun fmt(value: Float) = String.format(java.util.Locale.US, "%.3f", value)

    private class Box(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int, val area: Int) {
        val height get() = maxY - minY + 1
    }

    private fun near(pixel: Int, target: IntArray, tolerance: Int = 6): Boolean {
        if ((pixel ushr 24) < 200) return false
        return abs(((pixel shr 16) and 0xFF) - target[0]) <= tolerance &&
            abs(((pixel shr 8) and 0xFF) - target[1]) <= tolerance &&
            abs((pixel and 0xFF) - target[2]) <= tolerance
    }

    private fun isSkin(pixel: Int) = SKIN_TONES.any { near(pixel, it) }

    private fun box(pixels: IntArray, width: Int, match: (Int) -> Boolean): Box? {
        var minX = Int.MAX_VALUE; var maxX = -1; var minY = Int.MAX_VALUE; var maxY = -1; var area = 0
        for (i in pixels.indices) {
            if (!match(pixels[i])) continue
            val x = i % width
            val y = i / width
            area++
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        return if (area == 0) null else Box(minX, maxX, minY, maxY, area)
    }

    private fun blobs(pixels: IntArray, width: Int, match: (Int) -> Boolean): List<Box> {
        val height = pixels.size / width
        val hit = BooleanArray(pixels.size) { match(pixels[it]) }
        val seen = BooleanArray(pixels.size)
        val out = ArrayList<Box>()
        val stack = ArrayDeque<Int>()
        for (start in hit.indices) {
            if (!hit[start] || seen[start]) continue
            seen[start] = true
            stack.addLast(start)
            var minX = Int.MAX_VALUE; var maxX = 0; var minY = Int.MAX_VALUE; var maxY = 0; var area = 0
            while (stack.isNotEmpty()) {
                val p = stack.removeLast()
                val x = p % width
                val y = p / width
                area++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (x > 0 && hit[p - 1] && !seen[p - 1]) { seen[p - 1] = true; stack.addLast(p - 1) }
                if (x < width - 1 && hit[p + 1] && !seen[p + 1]) { seen[p + 1] = true; stack.addLast(p + 1) }
                if (y > 0 && hit[p - width] && !seen[p - width]) { seen[p - width] = true; stack.addLast(p - width) }
                if (y < height - 1 && hit[p + width] && !seen[p + width]) {
                    seen[p + width] = true
                    stack.addLast(p + width)
                }
            }
            if (area >= MIN_BLOB_AREA) out.add(Box(minX, maxX, minY, maxY, area))
        }
        return out
    }

    // ------------------------------------------------------------------ output

    private fun outputDir(): File = File(context.filesDir, "ab-capture").apply { mkdirs() }

    private fun write(bitmap: Bitmap, dir: File, name: String) {
        val file = File(dir, "$name.png")
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i(TAG, "wrote ${file.absolutePath} (${file.length()} bytes)")
    }

    private companion object {
        const val TAG = "ABSCALE"
        const val WIDTH = 1080
        const val HEIGHT = 2400
        const val THEME_ID = "sunset"

        /** Mid-screen, for a single vehicle. */
        const val PROGRESS_CENTRE = 0.5f

        /** `drawCar`'s own off-screen margin, which its x mapping is expressed against. */
        const val CAR_MARGIN = 120f

        /** Wide enough to hold any vehicle's own drawing, in that vehicle's local units. */
        const val CAR_HALF_WIDTH_UNITS = 55f

        /** Tall enough to hold a fire engine and its ladder, measured up from the wheel line. */
        const val CAR_TALLEST_UNITS = 70f

        /** Small enough to keep a face, large enough to drop an anti-aliased speck. */
        const val MIN_BLOB_AREA = 40

        val LANES = listOf(
            "far" to SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
            "near" to SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
        )

        val SKIN_TONES = listOf(
            intArrayOf(240, 201, 166),
            intArrayOf(220, 169, 124),
            intArrayOf(169, 113, 75),
        )
        val GLASS = intArrayOf(185, 216, 228)
    }
}
