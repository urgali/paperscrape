package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * The people behind a windscreen, measured on the pixels a device paints (**v4.6, P0**).
 *
 * ### Why this exists next to `VehicleScalePixelTest`
 *
 * That class measures whole objects at the goldens' 360x800, which is the right frame for
 * "is a car the height its lane implies" and the wrong one for a head: a driver's face is five
 * pixels tall there, and the difference this release is about is three of them. Everything here
 * renders at **1080x2400**, a real phone's viewport, where a face is fifteen pixels and a
 * measurement means something.
 *
 * ### What is measured, and why it is not the arithmetic again
 *
 * A face, by its colour. The three shipped skin tones are painted by nothing in this scene except
 * a person, the busts are blitted untinted, and a face is a single connected region of them — so
 * finding the largest such region inside a car is finding the driver, with no help from the model
 * being checked. The same method finds a pedestrian's face in a frame that has only pedestrians in
 * it. Both numbers come out of the finished bitmap.
 *
 * ### The defect, as an assertion
 *
 * The near traffic lane is at 0.862 of screen height and the near pavement at 0.807, so a driver
 * is **nearer the viewer than any pedestrian**. In v4.5 the driver's face measured 11 px against a
 * pedestrian's 13: the nearer person was drawn smaller, which is what "the people in the cars look
 * like children" was. [aDriversFaceIsAtLeastAsBigAsTheNearestPedestriansFace] is that comparison,
 * and it fails on v4.5's constants.
 */
@RunWith(AndroidJUnit4::class)
class VehicleOccupantScaleTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    // ------------------------------------------------------------------ rendering

    /**
     * One vehicle of [type] on [lane], drawn alone at [WIDTH] x [HEIGHT].
     *
     * A hand-built layout rather than a theme's, because the point is to see each vehicle type on
     * each lane and no theme offers that. `speedFraction = 0` and a negative start delay put the
     * car at a known `progress` and hold it there, so the frame needs no warm-up and cannot drift.
     */
    private fun frameWithOneCar(
        type: CarType,
        lane: Float,
        reverse: Boolean = true,
        progress: Float = CAR_PROGRESS,
    ): Bitmap {
        val layout = SceneObjectLayout(
            staticObjects = emptyList(),
            cars = listOf(
                CarObject(
                    laneYFraction = lane,
                    speedFraction = 0f,
                    startDelaySeconds = -progress,
                    color = 0xFFB4513C.toInt(),
                    reverse = reverse,
                    type = type,
                ),
            ),
        )
        return render(layout, peopleVisible = false)
    }

    /** The street with nobody driving on it: pedestrians only, so a face found is a pedestrian's. */
    private fun frameWithPeopleOnly(): Bitmap =
        render(SceneObjectLayout(staticObjects = emptyList(), cars = emptyList()), peopleVisible = true)

    private fun render(layout: SceneObjectLayout, peopleVisible: Boolean): Bitmap {
        val defaults = defaultCustomizationFor(THEME_ID)
        val customization = defaults.copy(
            cars = defaults.cars.copy(visible = true, density = 1f),
            people = defaults.people.copy(visible = peopleVisible, density = 1f),
            peopleNightDensity = 1f,
            // Summer, so the faces are the uncovered ones. A winter hat and scarf cover most of a
            // walking figure's face and the measurement would be about headwear.
            winterColorsEnabled = false,
            christmasDecorationsEnabled = false,
        )
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        val renderer = SceneObjectRenderer(layout, customization, context, THEME_ID)
        renderer.draw(
            target,
            GroundGeometry(shiftXWrapped = 0f, tileWidth = WIDTH.toFloat()),
            dayBlend = 1f,
            elapsedSeconds = SceneTime(120.0),
            screenWidth = WIDTH.toFloat(),
            screenHeight = HEIGHT.toFloat(),
        )
        target.unbind()
        return bitmap
    }

    // ------------------------------------------------------------------ measuring

    /** One connected run of skin-coloured pixels: a face, a hand, or a bare leg. */
    private class Blob(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int, val area: Int) {
        val height get() = maxY - minY + 1
        val centreX get() = (minX + maxX) / 2f
    }

    private fun isSkin(pixel: Int): Boolean {
        if ((pixel ushr 24) < 200) return false
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return SKIN_TONES.any { abs(r - it[0]) <= 6 && abs(g - it[1]) <= 6 && abs(b - it[2]) <= 6 }
    }

    /** Every connected region of skin in [bitmap], largest first, specks discarded. */
    private fun skinBlobs(bitmap: Bitmap): List<Blob> {
        val pixels = IntArray(WIDTH * HEIGHT)
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        val skin = BooleanArray(pixels.size) { isSkin(pixels[it]) }
        val seen = BooleanArray(pixels.size)
        val out = ArrayList<Blob>()
        val stack = ArrayDeque<Int>()
        for (start in skin.indices) {
            if (!skin[start] || seen[start]) continue
            seen[start] = true
            stack.addLast(start)
            var minX = WIDTH; var maxX = 0; var minY = HEIGHT; var maxY = 0; var area = 0
            while (stack.isNotEmpty()) {
                val p = stack.removeLast()
                val x = p % WIDTH
                val y = p / WIDTH
                area++
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
                if (x > 0 && skin[p - 1] && !seen[p - 1]) { seen[p - 1] = true; stack.addLast(p - 1) }
                if (x < WIDTH - 1 && skin[p + 1] && !seen[p + 1]) { seen[p + 1] = true; stack.addLast(p + 1) }
                if (y > 0 && skin[p - WIDTH] && !seen[p - WIDTH]) { seen[p - WIDTH] = true; stack.addLast(p - WIDTH) }
                if (y < HEIGHT - 1 && skin[p + WIDTH] && !seen[p + WIDTH]) { seen[p + WIDTH] = true; stack.addLast(p + WIDTH) }
            }
            if (area >= MIN_BLOB_AREA) out.add(Blob(minX, maxX, minY, maxY, area))
        }
        return out.sortedByDescending { it.area }
    }

    /** Every pixel of `car_window`'s own glass colour, as a bounding box. */
    private fun glassBox(bitmap: Bitmap): Blob? {
        val pixels = IntArray(WIDTH * HEIGHT)
        bitmap.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
        var minX = WIDTH; var maxX = -1; var minY = HEIGHT; var maxY = -1; var area = 0
        for (i in pixels.indices) {
            val p = pixels[i]
            if ((p ushr 24) < 200) continue
            val r = (p shr 16) and 0xFF
            val g = (p shr 8) and 0xFF
            val b = p and 0xFF
            if (abs(r - GLASS[0]) > 4 || abs(g - GLASS[1]) > 4 || abs(b - GLASS[2]) > 4) continue
            val x = i % WIDTH
            val y = i / WIDTH
            area++
            if (x < minX) minX = x
            if (x > maxX) maxX = x
            if (y < minY) minY = y
            if (y > maxY) maxY = y
        }
        return if (area == 0) null else Blob(minX, maxX, minY, maxY, area)
    }

    /** How many screen pixels one of the vehicle's own local units is worth on [lane]. */
    private fun unitPx(lane: Float, type: CarType): Float {
        val base = if (type == CarType.FIRE_TRUCK) SceneSpace.FIRE_TRUCK_BASE_SCALE else SceneSpace.CAR_BASE_SCALE
        return base * SceneSpace.perspectiveScaleAt(lane) * SceneSpace.sceneScale(HEIGHT.toFloat())
    }

    /**
     * A screen row, read back as the vehicle's own local y.
     *
     * `drawCar` translates to the lane and then **up by 37 units** so that y=0 is the object's
     * ground contact the way every other renderer in this file means it, which is why the wheel
     * line is at local y=37 and not at zero. Forgetting that offset is the whole difference
     * between the glass being at -6 and appearing to be at -43.
     */
    private fun localY(screenY: Int, lane: Float, type: CarType): Float =
        (screenY - lane * HEIGHT) / unitPx(lane, type) + CAR_LOCAL_ORIGIN_ABOVE_CONTACT_UNITS

    /** The tallest face in a frame, which for the street is the nearest adult. */
    private fun tallestFace(bitmap: Bitmap): Blob =
        skinBlobs(bitmap).maxByOrNull { it.height } ?: error("no face found in the frame")

    // ------------------------------------------------------------------ the glass

    /**
     * The pane is drawn [SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS] tall, on both lanes.
     *
     * Measured off the glass's own colour, so it is the *drawn* height and not the sprite's: the
     * stretch v4.6 applies is a `scale` around the blit, and this is what proves it landed. On
     * v4.5's constants the same measurement returns 16 units.
     */
    @Test
    fun theGlassIsDrawnNineteenUnitsTall() {
        for (lane in LANES) {
            val glass = glassBox(frameWithOneCar(CarType.PLAIN, lane))
                ?: error("no glass found on lane $lane")
            val units = glass.height / unitPx(lane, CarType.PLAIN)
            // The measurement is of the *coloured* pane, which the artwork insets inside its own
            // 48-px canvas, so it comes out a fraction of a unit short of the drawn height.
            assertEquals(
                "the glass on lane $lane measured $units units tall",
                SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS,
                units,
                1.0f,
            )
            assertTrue(
                "the glass is still the 16 units the sprite is authored at -- the stretch is gone",
                units > SceneObjectRenderer.CAR_GLASS_SPRITE_HEIGHT_UNITS + 1f,
            )
        }
    }

    /** And it did not move up: the roof line is the one edge that had to stay put. */
    @Test
    fun theGlassGrewDownwardsAndTheRoofLineDidNotMove() {
        for (lane in LANES) {
            val glass = glassBox(frameWithOneCar(CarType.PLAIN, lane))!!
            val topUnits = localY(glass.minY, lane, CarType.PLAIN)
            assertEquals(
                "the glass top on lane $lane is at local y $topUnits",
                SceneObjectRenderer.CAR_GLASS_ORIGIN_Y_UNITS,
                topUnits,
                1.0f,
            )
            val bottomUnits = localY(glass.maxY, lane, CarType.PLAIN)
            assertEquals(
                "the sill on lane $lane is at local y $bottomUnits",
                SceneObjectRenderer.CAR_SILL_Y_UNITS,
                bottomUnits,
                1.0f,
            )
        }
    }

    // ------------------------------------------------------------------ the occupants

    /**
     * **The defect, as pixels.**
     *
     * A driver on the near lane stands at 0.862 of screen height; the nearest pedestrian the street
     * can produce stands at 0.807 plus its jitter. The driver is nearer, so their face cannot be
     * the smaller of the two. Measured on v4.5: 11 px against 13. Measured on v4.6: 15 against 13.
     */
    @Test
    fun aDriversFaceIsAtLeastAsBigAsTheNearestPedestriansFace() {
        val pedestrian = tallestFace(frameWithPeopleOnly())
        val driver = tallestFace(frameWithOneCar(CarType.PLAIN, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION))
        assertTrue(
            "a near-lane driver's face is ${driver.height} px and the nearest pedestrian's is " +
                "${pedestrian.height} px -- the nearer person is drawn smaller",
            driver.height >= pedestrian.height,
        )
        assertTrue(
            "and it should not have overshot into a caricature",
            driver.height <= pedestrian.height * 1.6f,
        )
    }

    /**
     * Every vehicle type carries a driver of the size its own glass implies, on either lane.
     *
     * The predicted face height is the sprite's own measured face over the scale the bust is drawn
     * at over the lane's projection -- three separate things, all of which have to be right for the
     * measurement to land. A tolerance of a pixel and a half absorbs anti-aliasing on a face whose
     * edge is a curve.
     */
    @Test
    fun everyVehicleTypeDrawsItsDriverAtTheSizeItsGlassImplies() {
        for (type in CarType.entries) {
            for (lane in LANES) {
                val frame = frameWithOneCar(type, lane)
                val faces = skinBlobs(frame)
                assertTrue("$type on $lane has nobody in it", faces.isNotEmpty())
                val scale = if (type == CarType.FIRE_TRUCK) {
                    SceneObjectRenderer.FIRE_TRUCK_HEAD_SCALE
                } else {
                    SceneObjectRenderer.CAR_HEAD_SCALE
                }
                val predicted = DRIVER_FACE_UNITS * scale * unitPx(lane, type)
                val driver = faces.maxByOrNull { it.height }!!
                assertEquals(
                    "$type on lane $lane: driver face measured ${driver.height} px",
                    predicted,
                    driver.height.toFloat(),
                    1.5f,
                )
            }
        }
    }

    /**
     * A civilian car carries two people, and they are in different panes of the glass.
     *
     * Both busts grew by a third in v4.6 and the rear pane has a quarter of a unit of slack, so
     * "they still fit and still do not touch" is worth measuring rather than deriving. The pane
     * each one is in is recovered from the frame: the car's own local x, back through the lane's
     * scale and the horizontal flip the artwork needs.
     */
    @Test
    fun aCivilianCarSeatsItsTwoOccupantsEitherSideOfTheMullion() {
        val lane = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION
        val frame = frameWithOneCar(CarType.PLAIN, lane, reverse = true)
        val faces = skinBlobs(frame)
        assertEquals("a plain car should seat a driver and a passenger", 2, faces.size)

        val carCentreX = CAR_PROGRESS * (WIDTH + 2 * CAR_TRAVEL_MARGIN) - CAR_TRAVEL_MARGIN
        val px = unitPx(lane, CarType.PLAIN)
        val localX = faces.map { (it.centreX - carCentreX) / px }
        assertTrue(
            "both occupants are on the same side of the mullion: $localX",
            localX.any { it < MULLION_LEFT_UNITS } && localX.any { it > MULLION_RIGHT_UNITS },
        )
        for (x in localX) {
            assertTrue(
                "an occupant's face centre is at local x $x, outside the glass",
                x > GLASS_LEFT_UNITS && x < GLASS_RIGHT_UNITS,
            )
        }
    }

    // ------------------------------------------------------------------ what did not change

    /**
     * The car is exactly the size v4.5 drew it.
     *
     * The batch was allowed to enlarge the vehicle if the measurements demanded it, and they did
     * not: the projection was already right and only its occupants were wrong. Measured from the
     * frame rather than from the constants, so a change to `CAR_METRES_TALL`,
     * `PIXELS_PER_METRE_AT_REFERENCE` or the lane fractions all fail here.
     */
    @Test
    fun theVehicleItselfIsTheSameSizeItWas() {
        for (lane in LANES) {
            // The same road with the car held off screen: `progress` below -0.05 is culled by the
            // draw loop, and the road's own width comes from the layout rather than from the cars
            // that survive, so the two frames differ by exactly one vehicle.
            val empty = frameWithOneCar(CarType.PLAIN, lane, progress = -0.5f)
            val frame = frameWithOneCar(CarType.PLAIN, lane)
            val rowA = IntArray(WIDTH)
            val rowB = IntArray(WIDTH)
            var top = HEIGHT
            var bottom = -1
            for (y in 0 until HEIGHT) {
                empty.getPixels(rowA, 0, WIDTH, 0, y, WIDTH, 1)
                frame.getPixels(rowB, 0, WIDTH, 0, y, WIDTH, 1)
                for (x in 0 until WIDTH) {
                    if (rowA[x] == rowB[x]) continue
                    // The ground shadow is translucent and reaches well above the roof; the car
                    // itself is opaque, which is what separates them.
                    if ((rowB[x] ushr 24) < 250) continue
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                    break
                }
            }
            val height = (bottom - top + 1).toFloat()
            val predicted = SceneSpace.CAR_SPRITE_UNITS_TALL * unitPx(lane, CarType.PLAIN)
            assertEquals(
                "a plain car on lane $lane measured $height px",
                predicted,
                height,
                2.5f,
            )
        }
    }

    private companion object {
        /** A real phone's viewport. The goldens' 360x800 is too coarse to measure a face on. */
        const val WIDTH = 1080
        const val HEIGHT = 2400

        /** A theme with a road, so `drawRoad` and the lane pair behave normally. */
        const val THEME_ID = "sunset"

        /** Where the single car is held: mid-screen, and never advanced. */
        const val CAR_PROGRESS = 0.5f

        /** `drawCar`'s own off-screen margin, which its x mapping is expressed against. */
        const val CAR_TRAVEL_MARGIN = 120f

        /** `drawCar`'s `canvas.translate(0f, -37f)`: local y=37 is the wheel contact. */
        const val CAR_LOCAL_ORIGIN_ABOVE_CONTACT_UNITS = 37f

        val LANES = listOf(SceneSpace.ROAD_LANE_FAR_Y_FRACTION, SceneSpace.ROAD_LANE_NEAR_Y_FRACTION)

        /**
         * `person_man_summer_head_car`'s face, in the sprite's own local units.
         *
         * The largest connected region of skin in the artwork is 77 px tall. Which of the two adult
         * heads is chosen is a pure function of the car's lane and start delay, and both lanes used
         * here select the man.
         */
        const val DRIVER_FACE_UNITS = 77f / 3f

        /** The shipped skin palette, from `tools/generate_skin_variants.py`. */
        val SKIN_TONES = listOf(
            intArrayOf(240, 201, 166),
            intArrayOf(220, 169, 124),
            intArrayOf(169, 113, 75),
        )

        /** `car_window`'s glass, which nothing else in the scene is painted in. */
        val GLASS = intArrayOf(185, 216, 228)

        /** Small enough to keep a face, large enough to drop an anti-aliased speck. */
        const val MIN_BLOB_AREA = 40

        const val GLASS_LEFT_UNITS = -20f
        const val GLASS_RIGHT_UNITS = 26f
        const val MULLION_LEFT_UNITS = 4f
        const val MULLION_RIGHT_UNITS = 7.34f
    }
}
