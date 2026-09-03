package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
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

    // ------------------------------------------------------------------ the one assertion

    /**
     * The harness renders the direction it is asked for -- the one thing in this file that
     * asserts, and the only one that runs without `-e captureAb true`.
     *
     * Until v4.20 [car] hardcoded `reverse = true`, so every frame this harness had ever produced
     * faced left **by construction**. That is not a cosmetic limit: the frames were read as
     * evidence about which way the traffic drives, and they could not have shown anything else.
     * Item 8 of `BACKLOG_v4_19.md`, and the reason it is closed by a parameter *plus* a test
     * rather than by a parameter: a default argument is exactly the kind of thing that quietly
     * goes back to one value, and the next reader would have no way to tell.
     *
     * It checks the picture, not the flag. Each direction is rendered on an empty street -- no
     * scenery, so the only cabin glass in the frame is the car's -- and the glass has to be found
     * where `drawCar`'s own x mapping puts it for that direction, which is on opposite sides of
     * the screen. Asserting `spec.reverse == reverse` would pass on a build that ignored the flag.
     */
    @Test
    fun bothDirectionsAreActuallyRendered() {
        val lane = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION
        val progress = 0.3f
        val unitPx = SceneSpace.CAR_BASE_SCALE *
            SceneSpace.perspectiveScaleAt(lane) * SceneSpace.sceneScale(HEIGHT.toFloat())
        for ((facing, reverse) in DIRECTIONS) {
            val frame = render(
                listOf(car(CarType.PLAIN, lane, progress, reverse)),
                peopleDensity = 0f,
                scenery = false,
            )
            // Looked for inside the car's own rectangle and inside the rectangle a car facing the
            // other way would occupy. The frame is searched nowhere else on purpose: cabin glass
            // is a pale blue and so is a good deal of the sky, which is exactly why `reportVehicle`
            // crops before it measures.
            val here = glassInVehicleBox(frame, centreXOf(progress, reverse), lane, unitPx)
            val there = glassInVehicleBox(frame, centreXOf(progress, !reverse), lane, unitPx)
            frame.recycle()
            assertTrue(
                "no cabin glass where drawCar puts a '$facing'-facing car " +
                    "(centre ${fmt(centreXOf(progress, reverse))} px)",
                here > 0,
            )
            assertTrue(
                "a '$facing'-facing car put more glass where a car facing the other way belongs " +
                    "($there px against $here) -- the direction is being ignored",
                here > there * 4,
            )
        }
    }

    /** Glass pixels inside the rectangle a vehicle centred at [centreX] would occupy. */
    private fun glassInVehicleBox(frame: Bitmap, centreX: Float, lane: Float, unitPx: Float): Int {
        val ground = lane * HEIGHT
        val left = (centreX - CAR_HALF_WIDTH_UNITS * unitPx).toInt().coerceIn(0, WIDTH - 1)
        val right = (centreX + CAR_HALF_WIDTH_UNITS * unitPx).toInt().coerceIn(0, WIDTH - 1)
        val top = (ground - CAR_TALLEST_UNITS * unitPx).toInt().coerceIn(0, HEIGHT - 1)
        val bottom = ground.toInt().coerceIn(0, HEIGHT - 1)
        if (right <= left || bottom <= top) return 0
        val pixels = crop(frame, left, top, right - left + 1, bottom - top + 1)
        return pixels.count { near(it, GLASS) }
    }

    // ------------------------------------------------------------------ the scenes

    @Test
    fun captureEveryVehicleOnEveryLane() {
        if (!asked()) return
        val dir = outputDir()
        for (type in CarType.entries) {
            for ((laneName, lane) in LANES) {
                for ((facing, reverse) in DIRECTIONS) {
                    val name = "${type.name.lowercase()}_${laneName}_$facing"
                    val frame = render(listOf(car(type, lane, PROGRESS_CENTRE, reverse)), peopleDensity = 0f)
                    write(frame, dir, name)
                    reportVehicle(name, frame, type, lane, PROGRESS_CENTRE, reverse)
                    frame.recycle()
                }
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
                for ((facing, reverse) in DIRECTIONS) {
                    val name = "winter_${type.name.lowercase()}_${laneName}_$facing"
                    val frame = render(
                        listOf(car(type, lane, PROGRESS_CENTRE, reverse)),
                        peopleDensity = 0f,
                        winter = true,
                    )
                    write(frame, dir, name)
                    reportVehicle(name, frame, type, lane, PROGRESS_CENTRE, reverse)
                    frame.recycle()
                }
            }
        }
    }

    /** The picture the whole change is about: people in cars and people on the pavement at once. */
    @Test
    fun captureAStreetWithTrafficAndPedestrians() {
        if (!asked()) return
        val dir = outputDir()
        // Direction follows the lane here, exactly as `SceneObjectCatalog` sets it: the near lane
        // drives right and the far lane left. The old capture drove all four the same way, which
        // is what made it useless as direction evidence.
        val cars = listOf(
            car(CarType.PLAIN, SceneSpace.ROAD_LANE_FAR_Y_FRACTION, 0.22f, reverse = true),
            car(CarType.TAXI, SceneSpace.ROAD_LANE_FAR_Y_FRACTION, 0.66f, reverse = true),
            car(CarType.POLICE, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, 0.32f, reverse = false),
            car(CarType.FIRE_TRUCK, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, 0.76f, reverse = false),
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
                spec.reverse,
            )
        }
        frame.recycle()

        val night = render(cars, peopleDensity = 1f, day = false)
        write(night, dir, "street_night")
        night.recycle()
    }

    /**
     * What the two sliders actually cost, measured on the device rather than reasoned about.
     *
     * Renders the same scene many times at 0% and at 100% and reports both. The piles are N extra
     * sprite blits a frame and nothing else -- no allocation, no state, no timer -- so the honest
     * question is whether N=18 is measurable at all, and this is where that gets answered instead
     * of asserted.
     */
    @Test
    fun timeTheGroundPiles() {
        if (!asked()) return
        for ((label, piles) in listOf("0" to 0f, "100" to 1f)) {
            // One warm-up pass so sprite decoding and caches are not being timed.
            render(emptyList(), peopleDensity = 1f, winter = true, piles = piles).recycle()
            val started = System.nanoTime()
            for (i in 0 until TIMED_FRAMES) {
                render(emptyList(), peopleDensity = 1f, winter = true, piles = piles, atSeconds = 100.0 + i)
                    .recycle()
            }
            val perFrameMs = (System.nanoTime() - started) / 1e6 / TIMED_FRAMES
            Log.i(TAG, "piles $label%: ${"%.2f".format(perFrameMs)} ms per frame over $TIMED_FRAMES frames")
        }
    }

    /**
     * The two ground-pile sliders at the three positions that matter.
     *
     * 0% has to draw nothing at all -- that is what makes the feature free for anyone who never
     * turns it on -- and 100% has to still read as drifts lying on the ground rather than as a
     * white or orange floor. Both are judgements about a picture, so this writes the pictures.
     */
    @Test
    fun captureTheGroundPiles() {
        if (!asked()) return
        val dir = outputDir()
        for (pct in intArrayOf(0, 50, 100)) {
            val snow = render(emptyList(), peopleDensity = 1f, winter = true, piles = pct / 100f)
            write(snow, dir, "piles_snow_$pct")
            snow.recycle()
            val leaf = render(emptyList(), peopleDensity = 1f, fall = true, piles = pct / 100f)
            write(leaf, dir, "piles_leaf_$pct")
            leaf.recycle()
        }
    }

    /**
     * Autumn, which is the only season the falling leaves exist in.
     *
     * Written to look at where a leaf ends up. They used to fall to one global
     * `screenHeight * 0.88`, below both traffic lanes, so leaves from every tree crossed the
     * hillside and settled on the road among the cars; they now stop at their own tree's foot.
     */
    @Test
    fun captureAnAutumnStreet() {
        if (!asked()) return
        val cars = listOf(
            car(CarType.PLAIN, SceneSpace.ROAD_LANE_FAR_Y_FRACTION, 0.22f, reverse = true),
            car(CarType.TAXI, SceneSpace.ROAD_LANE_FAR_Y_FRACTION, 0.66f, reverse = true),
            car(CarType.POLICE, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, 0.32f, reverse = false),
            car(CarType.FIRE_TRUCK, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, 0.76f, reverse = false),
        )
        val dir = outputDir()
        for (step in 0 until 4) {
            val frame = render(cars, peopleDensity = 1f, fall = true, atSeconds = 30.0 + step * 7.0)
            write(frame, dir, "street_autumn_$step")
            frame.recycle()
        }
    }

    /**
     * The seasonal props side by side with the people, which is the only way to judge their sizes.
     *
     * A pumpkin, a gift, a snowman and a penguin all stand on the same ground as the pedestrians,
     * so one frame carrying all of them is what "is the pumpkin too small?" can actually be asked
     * of. Written when the pumpkin measured 0.5 m against a gift's 0.90 and an Easter egg's 1.00 --
     * half the size of an egg, for a prop that is a foot across in the world.
     */
    @Test
    fun captureTheSeasonalProps() {
        if (!asked()) return
        val dir = outputDir()
        val plain = render(emptyList(), peopleDensity = 1f, seasonalProps = true)
        write(plain, dir, "props_plain")
        plain.recycle()
        val spooky = render(emptyList(), peopleDensity = 1f, seasonalProps = true, halloween = true)
        write(spooky, dir, "props_halloween")
        spooky.recycle()
    }

    /**
     * The two street-level businesses, standing in a row with the houses they have to be told
     * apart from.
     *
     * A shop front is judged by one question -- *do I know at a glance that this is a shop?* --
     * and that question cannot be asked of a building on its own, because "commercial" is a
     * comparison with the dwellings either side of it. So the row is fixed here rather than taken
     * from a theme: a small house, the restaurant, the bar, a large house, all at one depth so
     * they are drawn at one scale and any difference between them is the artwork's.
     *
     * The two variants are chosen the way `variantFor` chooses them -- a SKYSCRAPER past
     * [SceneSpace.BUILDING_TOWER_MAX_DEPTH] is a shop, and the position hash picks which -- so
     * these are the drawings the scene really produces and not a private arrangement of sprites.
     */
    @Test
    fun captureTheCommercialFront() {
        if (!asked()) return
        val dir = outputDir()
        val row = listOf(
            // The object layer tiles at twice the screen width, so only the first half of the
            // fraction range is on screen -- a row spread over 0..1 puts two of its four buildings
            // outside the frame. Each fraction is also what picks the variant: `variantFor` hashes
            // it, so these four are a small house, the restaurant, the bar and a large house.
            StaticSceneObject(SceneObjectType.HOUSE, SHOP_ROW_DEPTH, 0.05f),
            StaticSceneObject(SceneObjectType.SKYSCRAPER, SHOP_ROW_DEPTH, 0.18f),
            StaticSceneObject(SceneObjectType.SKYSCRAPER, SHOP_ROW_DEPTH, 0.30f),
            StaticSceneObject(SceneObjectType.HOUSE, SHOP_ROW_DEPTH, 0.43f),
        )
        val day = render(emptyList(), peopleDensity = 0f, layout = row)
        write(day, dir, "shops_day")
        day.recycle()
        val night = render(emptyList(), peopleDensity = 0f, day = false, layout = row)
        write(night, dir, "shops_night")
        night.recycle()
        val winter = render(emptyList(), peopleDensity = 0f, layout = row, winter = true)
        write(winter, dir, "shops_winter")
        winter.recycle()
        // Christmas hangs string lights in the same panes the occupants stand in, so it is the
        // season most likely to collide with a frontage that has just been rebuilt.
        val christmas = render(
            emptyList(), peopleDensity = 0f, day = false, layout = row,
            winter = true, christmas = true,
        )
        write(christmas, dir, "shops_christmas")
        christmas.recycle()
    }

    /**
     * What the night lamps cost, measured rather than reasoned about.
     *
     * They are one faded blit and at most two rectangles per vehicle, all of it behind a zero
     * alpha, so the honest questions are whether midday still costs what it cost and whether
     * midnight costs measurably more. Same scene, same vehicles, same frame count, two hours.
     */
    @Test
    fun timeTheVehicleLights() {
        if (!asked()) return
        val cars = listOf(
            car(CarType.PLAIN, SceneSpace.ROAD_LANE_FAR_Y_FRACTION, 0.22f),
            car(CarType.TAXI, SceneSpace.ROAD_LANE_FAR_Y_FRACTION, 0.66f),
            car(CarType.POLICE, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, 0.32f),
            car(CarType.FIRE_TRUCK, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, 0.76f),
        )
        for ((label, hour) in listOf("noon" to 13f, "night" to 1f)) {
            render(cars, peopleDensity = 1f, hour = hour).recycle()
            val started = System.nanoTime()
            for (i in 0 until TIMED_FRAMES) {
                render(cars, peopleDensity = 1f, hour = hour, atSeconds = 100.0 + i).recycle()
            }
            val perFrameMs = (System.nanoTime() - started) / 1e6 / TIMED_FRAMES
            Log.i(TAG, "lights $label: ${"%.2f".format(perFrameMs)} ms per frame over $TIMED_FRAMES frames")
        }
    }

    /**
     * The one thing in the scene that used to look the same at midnight as at midday.
     *
     * The houses light their windows and the shops light their frontages on a ramp that starts a
     * third of the way into the evening; the traffic did not move at all. This renders the same
     * four vehicles at midday, at seven in the evening and at one in the morning, which is the only
     * way to ask the two questions that matter: are they off when they should be off, and are they
     * lamps rather than neon when they are on.
     */
    @Test
    fun captureTheVehicleLights() {
        if (!asked()) return
        val dir = outputDir()
        val cars = listOf(
            car(CarType.PLAIN, SceneSpace.ROAD_LANE_FAR_Y_FRACTION, 0.22f),
            car(CarType.TAXI, SceneSpace.ROAD_LANE_FAR_Y_FRACTION, 0.66f),
            car(CarType.POLICE, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, 0.32f),
            car(CarType.FIRE_TRUCK, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, 0.76f),
        )
        for ((label, hour) in listOf("noon" to 13f, "dusk" to 19f, "night" to 1f)) {
            val frame = render(cars, peopleDensity = 1f, hour = hour)
            write(frame, dir, "lights_$label")
            frame.recycle()
        }
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

    /**
     * One vehicle, standing still at [progress] across the screen, facing whichever way is asked.
     *
     * [reverse] used to be hardcoded `true`, which made every frame this harness has ever produced
     * a left-facing one **by construction** -- and that is not a small blemish: it was read as
     * evidence about which way the traffic drives and cost a wrong conclusion. Item 8 of
     * `BACKLOG_v4_19.md`. The parameter is the whole fix; `bothDirectionsAreActuallyRendered`
     * is what stops it silently reverting to one direction again.
     */
    private fun car(type: CarType, lane: Float, progress: Float, reverse: Boolean = true) = CarObject(
        laneYFraction = lane,
        speedFraction = 0f,
        startDelaySeconds = -progress,
        color = 0xFFB4513C.toInt(),
        reverse = reverse,
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
        seasonalProps: Boolean = false,
        halloween: Boolean = false,
        fall: Boolean = false,
        atSeconds: Double = 120.0,
        piles: Float = 0f,
        layout: List<StaticSceneObject>? = null,
        hour: Float? = null,
        christmas: Boolean = false,
    ): Bitmap {
        val theme = ThemeCatalog.byId(THEME_ID)
        val defaults = defaultCustomizationFor(THEME_ID)
        val customization = defaults.copy(
            cars = defaults.cars.copy(visible = true, density = 1f),
            people = defaults.people.copy(visible = peopleDensity > 0f, density = peopleDensity),
            peopleNightDensity = peopleDensity,
            winterColorsEnabled = winter,
            christmasDecorationsEnabled = christmas,
            fallColorsEnabled = fall,
            snowPiles = if (winter) piles else 0f,
            leafPiles = if (fall) piles else 0f,
            halloweenEnabled = halloween,
            pumpkins = if (seasonalProps) defaults.pumpkins.copy(visible = true, density = 1f) else defaults.pumpkins,
            gifts = if (seasonalProps) defaults.gifts.copy(visible = true, density = 1f) else defaults.gifts,
            snowmen = if (seasonalProps) defaults.snowmen.copy(visible = true, density = 1f) else defaults.snowmen,
            penguins = if (seasonalProps) defaults.penguins.copy(visible = true, density = 1f) else defaults.penguins,
            bunnies = if (seasonalProps) defaults.bunnies.copy(visible = true, density = 1f) else defaults.bunnies,
            easterEggs = if (seasonalProps) defaults.easterEggs.copy(visible = true, density = 1f) else defaults.easterEggs,
            // A hand-built row is a list of the buildings the frame is *about*, so none of it may
            // be thinned away: `keepCandidate` drops a candidate whose stable fraction lands above
            // the category's density, and at a theme's own density that silently removed three of
            // the four buildings the shop row is a comparison between.
            houses = if (layout != null) defaults.houses.copy(visible = true, density = 1f) else defaults.houses,
            buildings =
                if (layout != null) defaults.buildings.copy(visible = true, density = 1f) else defaults.buildings,
        )
        // The theme's own scenery, read before the override goes in so it is the catalogue's and
        // not the one this method is about to install.
        val objects = when {
            layout != null -> layout
            scenery -> SceneObjectCatalog.layoutFor(THEME_ID, theme.accentColor).staticObjects
            else -> emptyList()
        }
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
            // `day` is the two-state switch every earlier case uses; `hour` is for the cases that need
        // the evening in between, which is where the vehicle lamps come up.
        val phase = SunPositionCalculator.compute(hour24 = hour ?: if (day) 13f else 1f)
            renderer.draw(target, phase, SceneTime(atSeconds), 0f)
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
     * Where `drawCar` puts a vehicle's centre, for a given progress and direction.
     *
     * The same expression the renderer uses, and the reason it is a function rather than a line
     * inside the report: both directions are captured now, so both have to be located.
     */
    private fun centreXOf(progress: Float, reverse: Boolean): Float {
        val raw = progress * (WIDTH + 2 * CAR_MARGIN) - CAR_MARGIN
        return if (reverse) WIDTH - raw else raw
    }

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
    private fun reportVehicle(
        name: String,
        frame: Bitmap,
        type: CarType,
        lane: Float,
        progress: Float,
        reverse: Boolean = true,
    ) {
        val base = if (type == CarType.FIRE_TRUCK) SceneSpace.FIRE_TRUCK_BASE_SCALE else SceneSpace.CAR_BASE_SCALE
        val unitPx = base * SceneSpace.perspectiveScaleAt(lane) * SceneSpace.sceneScale(HEIGHT.toFloat())
        val centreX = centreXOf(progress, reverse)
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
                "carOccupantScale=${fmt(SceneObjectRenderer.CAR_OCCUPANT_SCALE)} " +
                "fireTruckOccupantScale=${fmt(SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE)}",
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

        /**
         * Near enough for a shop front to be read, far enough that the row still fits four
         * buildings. Past [SceneSpace.BUILDING_TOWER_MAX_DEPTH], which is what makes a SKYSCRAPER
         * a shop rather than a tower.
         */
        const val SHOP_ROW_DEPTH = 0.55f
        const val TIMED_FRAMES = 40

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

        /**
         * Both ways a vehicle can face, which is the axis this harness used to be blind to.
         *
         * `reverse` is the renderer's own flag and `true` is the leftward one -- see `drawCar`,
         * where it both mirrors the x mapping and flips the blit, because the artwork faces left.
         */
        val DIRECTIONS = listOf("left" to true, "right" to false)


        val SKIN_TONES = listOf(
            intArrayOf(240, 201, 166),
            intArrayOf(220, 169, 124),
            intArrayOf(169, 113, 75),
        )
        val GLASS = intArrayOf(185, 216, 228)
    }
}
