package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
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
 * stands nearer the viewer than any pedestrian -- and is still drawn smaller, because a head seen
 * through a pane is set back inside a body. In v4.5 the driver's face measured 11 px against a
 * pedestrian's 13 and read as a child; in v4.15 it measured 16 against 15 with the head against the
 * roof line. The proportion this release is judged on is the occupant's share of its own pane, and
 * [everyOccupantHasGlassAboveTheirHead] is that read off the rendered frame.
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
    /**
     * One car on an otherwise empty street, optionally on a chosen body.
     *
     * v4.19 gives a plain car one of three bodies, picked from its own immutable identity
     * ([CarShell.forCar]) so that nothing per-frame can move it. A test cannot therefore *ask*
     * for a body -- it has to find a vehicle that is one. [delayForShell] does that by nudging
     * `startDelaySeconds` by a millionth of a second at a time until the hash lands where it is
     * wanted, which moves the car by well under a pixel and leaves the production rule untouched.
     * That is deliberately harder than exposing a setter: a seam that let a test choose the body
     * would be a seam that let anything else choose it too.
     */
    private fun frameWithOneCar(
        type: CarType,
        lane: Float,
        reverse: Boolean = true,
        progress: Float = CAR_PROGRESS,
        shell: CarShell? = null,
    ): Bitmap {
        val delay = if (shell == null) -progress else delayForShell(shell, lane, type, progress)
        val layout = SceneObjectLayout(
            staticObjects = emptyList(),
            cars = listOf(
                CarObject(
                    laneYFraction = lane,
                    speedFraction = 0f,
                    startDelaySeconds = delay,
                    color = 0xFFB4513C.toInt(),
                    reverse = reverse,
                    type = type,
                ),
            ),
        )
        return render(layout, peopleVisible = false)
    }

    private fun delayForShell(shell: CarShell, lane: Float, type: CarType, progress: Float): Float {
        for (i in 0 until 20_000) {
            val candidate = -progress - i * 1e-6f
            val spec = CarObject(lane, 0f, candidate, 0, true, type)
            if (CarShell.forCar(spec) == shell) return candidate
        }
        error("no start delay within a hair of $progress puts a $type on the $shell")
    }

    /**
     * Every (type, body) pair the road can produce: three bodies for a plain car, one each for
     * the taxi and the police car, and the appliance, which has no [CarShell] at all.
     *
     * Flattened into one list rather than nested loops so that a criterion written for "every
     * vehicle" keeps covering every vehicle when a body is added.
     */
    private fun typesAndShells(): List<Pair<CarType, CarShell?>> =
        CarType.entries.flatMap { type ->
            when (type) {
                CarType.PLAIN -> CarShell.entries.map { type to it }
                CarType.FIRE_TRUCK -> listOf(type to null)
                else -> listOf(type to CarShell.forCar(CarObject(0f, 0f, 0f, 0, true, type)))
            }
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

    private fun isOccupantColour(pixel: Int): Boolean {
        if ((pixel ushr 24) < 200) return false
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // Tolerance 4, not 6: the scene's own anti-aliasing manufactures occupant look-alikes
        // at 6 -- the tyre-against-hub blend lands within 6 of the dark hair (measured 47,47,47)
        // and the tyre-against-terrain blend within 6 of the boy's skin (measured 168,119,70).
        // At 4 both stay out and every genuine occupant area still matches exactly.
        if (SKIN_TONES.any { abs(r - it[0]) <= 4 && abs(g - it[1]) <= 4 && abs(b - it[2]) <= 4 }) return true
        if (abs(r - 0x2B) <= 4 && abs(g - 0x2A) <= 4 && abs(b - 0x33) <= 4 && b - g >= 4) return true
        return OCCUPANT_EXTRA_COLOURS.any {
            it[0] != 0x2B && abs(r - it[0]) <= 4 && abs(g - it[1]) <= 4 && abs(b - it[2]) <= 4
        }
    }

    /** The colour a fixed-art sprite is, read off the shipped drawable rather than restated. */
    private fun dominantColour(resId: Int): IntArray {
        val opts = android.graphics.BitmapFactory.Options().apply { inScaled = false }
        val bmp = android.graphics.BitmapFactory.decodeResource(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
                .targetContext.resources,
            resId, opts,
        )
        val counts = HashMap<Int, Int>()
        for (y in 0 until bmp.height) {
            for (x in 0 until bmp.width) {
                val p = bmp.getPixel(x, y)
                if ((p ushr 24) < 250) continue
                counts[p or (0xFF shl 24)] = (counts[p or (0xFF shl 24)] ?: 0) + 1
            }
        }
        bmp.recycle()
        val top = counts.maxByOrNull { it.value }!!.key
        return intArrayOf((top shr 16) and 0xFF, (top shr 8) and 0xFF, top and 0xFF)
    }

    private fun near(r: Int, g: Int, b: Int, want: IntArray, tol: Int = 12): Boolean =
        abs(r - want[0]) < tol && abs(g - want[1]) < tol && abs(b - want[2]) < tol

    /** The lamp seats a body bakes, with a unit of margin for the antialiased rim. */
    private fun inLampSeat(shell: CarShell, localX: Float, localY: Float): Boolean {
        val front = localX >= shell.lampFrontXUnits - 1.5f &&
            localX <= shell.lampFrontXUnits + LAMP_FRONT_W_UNITS + 1.5f &&
            localY >= shell.lampFrontYUnits - 1.5f &&
            localY <= shell.lampFrontYUnits + LAMP_H_UNITS + 1.5f
        val rear = localX >= shell.lampRearXUnits - 1.5f &&
            localX <= shell.lampRearXUnits + LAMP_REAR_W_UNITS + 1.5f &&
            localY >= shell.lampRearYUnits - 1.5f &&
            localY <= shell.lampRearYUnits + LAMP_H_UNITS + 1.5f
        return front || rear
    }

    private fun inTruckLampSeat(localX: Float, localY: Float): Boolean {
        val front = localX >= SceneObjectRenderer.FIRE_TRUCK_LAMP_FRONT_X_UNITS - 1.5f &&
            localX <= SceneObjectRenderer.FIRE_TRUCK_LAMP_FRONT_X_UNITS + LAMP_FRONT_W_UNITS + 1.5f &&
            localY >= SceneObjectRenderer.FIRE_TRUCK_LAMP_FRONT_Y_UNITS - 1.5f &&
            localY <= SceneObjectRenderer.FIRE_TRUCK_LAMP_FRONT_Y_UNITS + LAMP_H_UNITS + 1.5f
        val rear = localX >= SceneObjectRenderer.FIRE_TRUCK_LAMP_REAR_X_UNITS - 1.5f &&
            localX <= SceneObjectRenderer.FIRE_TRUCK_LAMP_REAR_X_UNITS + LAMP_REAR_W_UNITS + 1.5f &&
            localY >= SceneObjectRenderer.FIRE_TRUCK_LAMP_REAR_Y_UNITS - 1.5f &&
            localY <= SceneObjectRenderer.FIRE_TRUCK_LAMP_REAR_Y_UNITS + LAMP_H_UNITS + 1.5f
        return front || rear
    }

    private fun isGlass(pixel: Int): Boolean {
        if ((pixel ushr 24) < 200) return false
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return abs(r - GLASS[0]) <= 6 && abs(g - GLASS[1]) <= 6 && abs(b - GLASS[2]) <= 6
    }

    private fun isSkin(pixel: Int): Boolean {
        if ((pixel ushr 24) < 200) return false
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        // 4, matching isOccupantColour: both sides of the parity ratio must gain the same
        // anti-aliased fringe or the smaller face gains proportionally more.
        return SKIN_TONES.any { abs(r - it[0]) <= 4 && abs(g - it[1]) <= 4 && abs(b - it[2]) <= 4 }
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
     * The pane is drawn [SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS] tall, on both lanes --
     * 23 units since rc2, measured off the glass's own colour so it is the *drawn* height.
     */
    @Test
    fun theGlassIsDrawnAtItsAuthoredHeight() {
        for (lane in LANES) {
            val glass = glassBox(frameWithOneCar(CarType.PLAIN, lane))
                ?: error("no glass found on lane $lane")
            val units = glass.height / unitPx(lane, CarType.PLAIN)
            // The coloured pane loses about a unit to its own rounded top corners and the
            // anti-aliased edge against the roof band, so the drawn bbox reads ~22 of 23 units.
            assertEquals(
                "the glass on lane $lane measured $units units tall",
                SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS,
                units,
                1.5f,
            )
            // v4.20 retired the stretch by authoring the pane at its drawn size, so the drawn
            // and authored heights are the same number and the frame must measure it directly:
            // a pane coming out taller than authored would mean the stretch crept back.
            assertEquals(
                "the pane is drawn at the size it is authored",
                SceneObjectRenderer.CAR_GLASS_SPRITE_HEIGHT_UNITS,
                units,
                1.5f,
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
                1.5f,
            )
            val bottomUnits = localY(glass.maxY, lane, CarType.PLAIN)
            // The bottom edge loses a row or two to anti-aliasing against the door colour.
            assertEquals(
                "the sill on lane $lane is at local y $bottomUnits",
                SceneObjectRenderer.CAR_SILL_Y_UNITS,
                bottomUnits,
                1.5f,
            )
        }
    }

    // ------------------------------------------------------------------ the occupants

    /**
     * **The v4.15 defect, as pixels: there was no air above anybody's head.**
     *
     * A bust was scaled so its content was exactly as tall as the glass and anchored on the sill,
     * so the top of the head coincided with the top of the pane by construction -- on every vehicle
     * type, on both lanes, in both seasons. That is what "the people in the cars are too big for
     * the cars" looks like from the inside, and no arithmetic test could see it because the scale
     * was *defined* to make it true.
     *
     * Measured here on the rendered frame rather than predicted: the topmost band of the drawn
     * glass must still be glass. [SceneObjectRenderer.OCCUPANT_HEAD_PANE_SHARE] puts a head at
     * 51.9% of its pane, so the band is comfortably clear; the assertion asks only for a tenth of
     * the pane, which v4.15 fails on every frame and this release passes on all of them.
     */
    @Test
    fun everyOccupantHasGlassAboveTheirHead() {
        for ((type, shell) in typesAndShells()) {
            for (lane in LANES) {
                val frame = frameWithOneCar(type, lane, shell = shell)
                val glass = glassBox(frame) ?: error("$type on $lane has no glass")
                val band = maxOf(1, ((glass.maxY - glass.minY + 1) * 0.10f).toInt())
                val pixels = IntArray(WIDTH * HEIGHT)
                frame.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                var intruders = 0
                for (y in glass.minY until glass.minY + band) {
                    for (x in glass.minX..glass.maxX) {
                        if (isSkin(pixels[y * WIDTH + x])) intruders++
                    }
                }
                assertEquals(
                    "$type on lane $lane has a head in the top $band rows of its own glass",
                    0,
                    intruders,
                )
                frame.recycle()
            }
        }
    }

    /**
     * The pedestrians, kept as a **secondary** and deliberately wide guard.
     *
     * An occupant and a pedestrian stand on different ground lines and the projection is supposed
     * to draw the nearer one larger, so requiring any particular ordering between them as they are
     * drawn is not a valid test -- v4.6's `driver >= pedestrian` was that mistake, and it is only
     * satisfiable by a bust that fills its window. What is asserted is that a driver has not become
     * absurd in either direction; the proportions this release is chosen on are the occupant's
     * share of its pane and of its vehicle, in `OneOccupantRuleTest` and
     * `VehiclePedestrianScaleTest`.
     */
    @Test
    fun aDriversFaceMatchesAPedestriansOnceDepthIsRemoved() {
        // The rc2 acceptance criterion, measured off rendered pixels for every vehicle type on
        // both lanes: headPx(occupant) / depthScale(vehicle lane) must equal
        // headPx(adult pedestrian) / depthScale(pavement row) within +/-10%. The tallest face on
        // a people-only street is the nearest adult, standing on the near pavement row.
        val pedestrian = tallestFace(frameWithPeopleOnly())
        val pedestrianNormalised =
            pedestrian.height / SceneSpace.perspectiveScaleAt(SceneSpace.PAVEMENT_NEAR_Y_FRACTION)
        for ((type, shell) in typesAndShells()) {
            for (lane in LANES) {
                val frame = frameWithOneCar(type, lane, shell = shell)
                val driver = tallestFace(frame)
                val driverNormalised = driver.height / SceneSpace.perspectiveScaleAt(lane)
                val ratio = driverNormalised / pedestrianNormalised
                assertTrue(
                    "$type on lane $lane: driver face ${driver.height} px (${"%.1f".format(driverNormalised)} " +
                        "normalised) vs pedestrian ${pedestrian.height} px " +
                        "(${"%.1f".format(pedestrianNormalised)}) -- ratio ${"%.3f".format(ratio)}",
                    ratio in 0.9f..1.1f,
                )
                frame.recycle()
            }
        }
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
        for ((type, shell) in typesAndShells()) {
            for (lane in LANES) {
                val frame = frameWithOneCar(type, lane, shell = shell)
                val faces = skinBlobs(frame)
                assertTrue("$type on $lane has nobody in it", faces.isNotEmpty())
                val scale = if (type == CarType.FIRE_TRUCK) {
                    SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE
                } else {
                    SceneObjectRenderer.CAR_OCCUPANT_SCALE
                }
                val predicted = driverFaceUnits(lane, -CAR_PROGRESS) * scale * unitPx(lane, type)
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
     * A civilian car carries two people, both inside the one pane, the driver forward.
     *
     * **rc5, and the two criteria this replaces one of.** rc4 asserted a seat count of one,
     * because a table-sized frontal head is 17.9-18.4 units wide against 42 nominal units of
     * glasshouse and the 15% pillar-light criterion capped the car there. rc5 lengthened the
     * pane to 47 units out of the bonnet, and the seat count is two again -- on the saloon, the
     * taxi and the police car alike.
     *
     * Counting faces rather than trusting the constants: two skin blobs of head size, not one and
     * not three. They are separate blobs because the two heads only touch across the hair, which
     * is not skin; if the seats were ever moved close enough to merge the faces this test would
     * see one blob and fail, which is the read it exists to protect.
     *
     * The driver's own criterion is here too: **the centre of the driver's head falls in the
     * forward half of the glass**, in both travel directions. rc4 seated its one occupant at the
     * pane's centre and the car read as though nobody were driving it.
     */
    @Test
    fun aCivilianCarSeatsADriverForwardAndAPassengerBehind() {
        for (type in listOf(CarType.PLAIN, CarType.TAXI, CarType.POLICE)) {
            for (reverse in listOf(true, false)) {
                for (lane in LANES) {
                    val frame = frameWithOneCar(type, lane, reverse = reverse)
                    val glass = glassBox(frame) ?: error("no glass on lane $lane")
                    val faces = skinBlobs(frame).filter { it.area >= MIN_FACE_AREA }
                    assertEquals(
                        "$type on lane $lane (reverse=$reverse) seats two: found ${faces.size} faces",
                        2, faces.size,
                    )
                    for (f in faces) {
                        assertTrue(
                            "$type on lane $lane: a face at ${f.minX}..${f.maxX} leaves the glass " +
                                "at ${glass.minX}..${glass.maxX}",
                            f.minX >= glass.minX && f.maxX <= glass.maxX,
                        )
                    }
                    // The leading half of the pane, in screen terms: reverse=true drives leftward,
                    // so the front of the car is the low-x side.
                    val paneCentre = (glass.minX + glass.maxX) / 2f
                    val driver = if (reverse) faces.minByOrNull { it.centreX }!! else faces.maxByOrNull { it.centreX }!!
                    val driverLeads = if (reverse) driver.centreX < paneCentre else driver.centreX > paneCentre
                    assertTrue(
                        "$type on lane $lane (reverse=$reverse): the leading face sits at " +
                            "${driver.centreX} against a pane centre of $paneCentre -- nobody is driving",
                        driverLeads,
                    )
                    frame.recycle()
                }
            }
        }
    }

    /**
     * **rc5 criterion: the heads fill at least half the glass.**
     *
     * The complaint this closes: rc4's single occupant left a big pane with one head in it, and
     * measured on the delivered frame the head filled 26% of the glass on the row the coordinator
     * sampled. "The cabin is empty" was the complaint that opened this whole arc, and it had come
     * back in a different shape.
     *
     * Measured literally as the criterion is worded -- the summed width of the occupants' ink on
     * a row, over the width of the glass on that same row -- across every row of the pane band, in
     * both lanes. Two figures are asserted, because one row is not a picture:
     *
     *  * at the **head band**, the row where the occupants' ink is widest, which is what "the head
     *    fills X% of the glass" means when someone looks at the car;
     *  * **averaged over the head's own rows** (those carrying at least half the band's ink), so a
     *    single flattering row cannot carry the criterion.
     *
     * Both must reach 50%. The value at the very bottom of the band, four units above the sill, is
     * *reported* rather than asserted: that row crosses the neck, and a neck is narrower than a
     * head at any seat count -- rc4 measured 28% there and the arithmetic in
     * [SceneObjectRenderer.CAR_HEAD_X_UNITS] shows that forcing 50% at the neck and 15% of pillar
     * light cannot both hold in any pane width. Where the two criteria met, the pane went.
     */
    @Test
    fun theOccupantsFillHalfTheGlass() {
        for (type in listOf(CarType.PLAIN, CarType.TAXI, CarType.POLICE)) {
            for (lane in LANES) {
                val frame = frameWithOneCar(type, lane)
                val px = unitPx(lane, type)
                val centreX = CAR_PROGRESS * (WIDTH + 2 * CAR_TRAVEL_MARGIN) - CAR_TRAVEL_MARGIN
                val groundY = lane * HEIGHT
                val pixels = IntArray(WIDTH * HEIGHT)
                frame.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                fun screenY(local: Float) =
                    (groundY + (local - CAR_LOCAL_ORIGIN_ABOVE_CONTACT_UNITS) * px).toInt()

                var bandInk = 0
                var bandFill = 0f
                var sum = 0f
                var rows = 0
                var lowest = 0f
                val perRow = ArrayList<Pair<Int, Float>>()
                for (y in screenY(-11f)..screenY(SceneObjectRenderer.CAR_SILL_Y_UNITS - 4f)) {
                    if (y < 0 || y >= HEIGHT) continue
                    // The pane's own extent on this row: where glass shows and where an occupant
                    // stands in front of it. Then the ink is the **complement** -- every pixel of
                    // that span which is not glass showing through -- rather than a count of
                    // palette matches. A palette count drops the anti-aliased pixels along every
                    // hair and shoulder edge while the span keeps them, which on the far lane,
                    // where a head is fifteen pixels of a hundred-pixel pane, is worth two points
                    // of a criterion measured to one. The complement is also what the eye does:
                    // the window is glass where you can see through it and occupant where you
                    // cannot.
                    var glassMin = Int.MAX_VALUE
                    var glassMax = -1
                    val xFrom = (centreX - 30f * px).toInt().coerceIn(0, WIDTH - 1)
                    val xTo = (centreX + 30f * px).toInt().coerceIn(0, WIDTH - 1)
                    var occupied = false
                    for (x in xFrom..xTo) {
                        val p = pixels[y * WIDTH + x]
                        if (isOccupantColour(p)) {
                            occupied = true
                            if (x < glassMin) glassMin = x
                            if (x > glassMax) glassMax = x
                        } else if (isGlass(p)) {
                            if (x < glassMin) glassMin = x
                            if (x > glassMax) glassMax = x
                        }
                    }
                    if (glassMax < 0 || !occupied) continue
                    val width = (glassMax - glassMin + 1).toFloat()
                    if (width < 8f) continue
                    var ink = 0
                    for (x in glassMin..glassMax) {
                        if (!isGlass(pixels[y * WIDTH + x])) ink++
                    }
                    perRow.add(ink to ink / width)
                }
                assertTrue("$type on lane $lane: no occupied glass rows found", perRow.isNotEmpty())
                for ((ink, fill) in perRow) {
                    if (ink > bandInk) { bandInk = ink; bandFill = fill }
                    lowest = fill
                }
                for ((ink, fill) in perRow) {
                    if (ink >= bandInk / 2) { sum += fill; rows++ }
                }
                val mean = sum / rows
                // Logged as well as asserted: the criterion is a number the maintainer re-measures
                // by hand off a delivered frame, and a pass/fail alone does not tell them which
                // number they are re-measuring.
                android.util.Log.i(
                    "rc5-fill",
                    "$type lane=$lane band=${"%.1f".format(bandFill * 100)}% " +
                        "mean=${"%.1f".format(mean * 100)}% neck=${"%.1f".format(lowest * 100)}%",
                )
                assertTrue(
                    "$type on lane $lane: the heads fill ${"%.1f".format(bandFill * 100)}% of the " +
                        "glass at the head band (rc4: 50% at its best row), mean over the head's " +
                        "rows ${"%.1f".format(mean * 100)}%, at the neck row " +
                        "${"%.1f".format(lowest * 100)}%",
                    bandFill >= 0.50f,
                )
                assertTrue(
                    "$type on lane $lane: mean fill over the head's rows is " +
                        "${"%.1f".format(mean * 100)}%",
                    mean >= 0.50f,
                )
                frame.recycle()
            }
        }
    }

    /**
     * The day lamps tell the direction (rc4).
     *
     * With frontal occupants the bust no longer says which way a car drives, so the two lamps
     * must: amber glass at the nose, brake red at the tail, drawn as an untinted overlay so the
     * user's body colour cannot swallow them. Measured on the rendered pixels at both ends of a
     * saloon on both lanes: the amber lamp lives only at the leading end, the red only at the
     * trailing one -- which is what makes the direction deducible from the car alone.
     */
    @Test
    fun theDayLampsTellTheDirection() {
        for (reverse in listOf(true, false)) {
            val lane = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION
            val frame = frameWithOneCar(CarType.PLAIN, lane, reverse = reverse)
            val px = unitPx(lane, CarType.PLAIN)
            val centreX = CAR_PROGRESS * (WIDTH + 2 * CAR_TRAVEL_MARGIN) - CAR_TRAVEL_MARGIN
            val groundY = lane * HEIGHT
            // The two lens colours come from the shipped sprites rather than being copied here:
            // v4.19 shares one amber and one red lens across three bodies and the appliance, and
            // a literal in a test is exactly the sort of second copy that goes stale when the
            // artwork moves. (v4.18's literals were the previous overlay's colours.)
            val amberLens = dominantColour(R.drawable.car_lamp_front)
            val redLens = dominantColour(R.drawable.car_lamp_rear)
            var amberMeanX = 0f; var amberN = 0
            var redMeanX = 0f; var redN = 0
            // The band the lamps live in, across all three bodies, with a unit of margin.
            val lampTop = CarShell.entries.minOf { minOf(it.lampFrontYUnits, it.lampRearYUnits) } - 1f
            val lampBottom = CarShell.entries.maxOf { maxOf(it.lampFrontYUnits, it.lampRearYUnits) } + 5f
            val yFrom = (groundY + (lampTop - CAR_LOCAL_ORIGIN_ABOVE_CONTACT_UNITS) * px).toInt()
            val yTo = (groundY + (lampBottom - CAR_LOCAL_ORIGIN_ABOVE_CONTACT_UNITS) * px).toInt()
            for (y in yFrom..yTo) {
                for (x in (centreX - 50f * px).toInt()..(centreX + 50f * px).toInt()) {
                    if (x < 0 || x >= WIDTH || y < 0 || y >= HEIGHT) continue
                    val p = frame.getPixel(x, y)
                    val r = (p shr 16) and 0xFF; val g = (p shr 8) and 0xFF; val b = p and 0xFF
                    if (near(r, g, b, amberLens)) {
                        amberMeanX += x; amberN++
                    } else if (near(r, g, b, redLens)) {
                        redMeanX += x; redN++
                    }
                }
            }
            assertTrue("no amber lamp found (reverse=$reverse)", amberN > 0)
            assertTrue("no red lamp found (reverse=$reverse)", redN > 0)
            amberMeanX /= amberN; redMeanX /= redN
            // reverse=true drives leftward: the amber nose lamp must sit left of the red tail.
            val amberLeads = if (reverse) amberMeanX < redMeanX else amberMeanX > redMeanX
            assertTrue(
                "reverse=$reverse: amber at $amberMeanX, red at $redMeanX -- the lamps do not " +
                    "say the direction",
                amberLeads,
            )
            frame.recycle()
        }
    }

    /**
     * The drivers rotate through the pedestrians' skin tones (rc4).
     *
     * "Carnagione segue le stesse regole dei pedoni": the walkers rotate three shipped tones,
     * and so must the seats -- a fleet with one complexion is exactly the regression the skin
     * axis exists to prevent. Three loop positions per lane give six deterministic candidates;
     * their faces must land on at least two distinct tones (the seed arithmetic actually spans
     * all three, but two is what proves the channel is alive without pinning the roll).
     */
    @Test
    fun theDriversRotateThroughTheSkinTones() {
        val tones = mutableSetOf<Int>()
        for (lane in LANES) {
            for (progress in listOf(0.3f, 0.5f, 0.7f)) {
                val frame = frameWithOneCar(CarType.PLAIN, lane, progress = progress)
                val centreX = progress * (WIDTH + 2 * CAR_TRAVEL_MARGIN) - CAR_TRAVEL_MARGIN
                val px = unitPx(lane, CarType.PLAIN)
                val face = skinBlobs(frame).minByOrNull { abs(it.centreX - centreX) / px }
                if (face != null) {
                    val p = frame.getPixel(face.centreX.toInt(), (face.minY + face.maxY) / 2)
                    val r = (p shr 16) and 0xFF
                    val g = (p shr 8) and 0xFF
                    val b = p and 0xFF
                    val tone = SKIN_TONES.indexOfFirst {
                        abs(r - it[0]) < 20 && abs(g - it[1]) < 20 && abs(b - it[2]) < 20
                    }
                    if (tone >= 0) tones.add(tone)
                }
                frame.recycle()
            }
        }
        assertTrue("every driver came out in one tone: $tones", tones.size >= 2)
    }

    /**
     * **rc2 criterion: not one pixel of an occupant outside the glass.**
     *
     * The rc1 frames showed a police driver's shirt continuing below the pane onto the door --
     * a torso through sheet metal. There is no canvas clip in the SceneCanvas contract, so the
     * fix is constructional (a bust must FIT its pane at its drawn scale) and the proof has to
     * be pixels: every occupant-coloured pixel inside the vehicle's own box must lie inside the
     * pane rectangle, with half a unit of tolerance for the outline's anti-aliasing.
     */
    @Test
    fun noOccupantPixelLeavesTheGlass() {
        for ((type, shell) in typesAndShells()) {
            for (lane in LANES) {
                val frame = frameWithOneCar(type, lane, shell = shell)
                val px = unitPx(lane, type)
                val centreX = CAR_PROGRESS * (WIDTH + 2 * CAR_TRAVEL_MARGIN) - CAR_TRAVEL_MARGIN
                val groundY = lane * HEIGHT
                val isTruck = type == CarType.FIRE_TRUCK
                val (paneL, paneR) = if (isTruck) TRUCK_PANE else cabinPane(shell!!)
                val paneT = if (isTruck) {
                    SceneObjectRenderer.FIRE_TRUCK_SILL_Y_UNITS -
                        SceneObjectRenderer.FIRE_TRUCK_GLASS_HEIGHT_UNITS
                } else {
                    SceneObjectRenderer.CAR_GLASS_ORIGIN_Y_UNITS
                }
                val paneB = if (isTruck) {
                    SceneObjectRenderer.FIRE_TRUCK_SILL_Y_UNITS
                } else {
                    SceneObjectRenderer.CAR_SILL_Y_UNITS
                }
                val pixels = IntArray(WIDTH * HEIGHT)
                frame.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                var outside = 0
                var firstOutside = ""
                for (y in 0 until HEIGHT) {
                    val localY = (y - groundY) / px + CAR_LOCAL_ORIGIN_ABOVE_CONTACT_UNITS
                    if (localY < -35f || localY > 40f) continue
                    // Only runs count. Isolated pixels along an anti-aliased seam between two
                    // pieces of vehicle artwork can fall inside the skin tolerance -- the
                    // appliance's cream stripe against its red body produces a dozen of them --
                    // and a person is never one pixel wide anywhere in this scene.
                    var runLen = 0
                    for (x in 0 until WIDTH) {
                        val localX = (x - centreX) / px
                        if (localX < -55f || localX > 55f) continue
                        val p = pixels[y * WIDTH + x]
                        if (!isOccupantColour(p)) { runLen = 0; continue }
                        runLen++
                        if (runLen < MIN_RUN_PX) continue
                        // The taxi's roof sign, the police light bar and the taxi's chequer band
                        // letter their art in the same dark ink as the hair (0x2B2A33 exactly),
                        // so those rectangles -- the roof accessories, the livery band on the
                        // doors -- are excluded: vehicle artwork, not a person out of the glass.
                        // Roof accessories stand above the glass; the livery bands the doors
                        // below the sill. Both windows are derived from the constants that place
                        // them, so a body change moves the exclusion with the artwork instead of
                        // leaving a stale rectangle behind -- which is exactly how v4.18's
                        // windows went out of date when the sill moved from 12 to 9.
                        if ((type == CarType.TAXI || type == CarType.POLICE) &&
                            localY < SceneObjectRenderer.CAR_GLASS_ORIGIN_Y_UNITS &&
                            abs(localX) <= 24f
                        ) continue
                        if ((type == CarType.TAXI || type == CarType.POLICE) &&
                            localY >= SceneObjectRenderer.CAR_SILL_Y_UNITS - 0.5f &&
                            localY <= SceneObjectRenderer.CAR_SILL_Y_UNITS + LIVERY_HEIGHT_UNITS + 0.5f &&
                            abs(localX) <= SceneObjectRenderer.CAR_LIVERY_WIDTH_UNITS / 2f + 0.5f
                        ) continue
                        // The day lamps (rc4) letter their art in ambers and reds whose
                        // anti-aliased rims can blend into the skin palette's tolerance: the two
                        // lamp housings are vehicle artwork, excluded exactly as the taxi sign
                        // and the livery band above are.
                        if (shell != null && inLampSeat(shell, localX, localY)) continue
                        if (isTruck && inTruckLampSeat(localX, localY)) continue
                        val inside = localX >= paneL - 0.5f && localX <= paneR + 0.5f &&
                            localY >= paneT - 0.5f && localY <= paneB + 0.5f
                        if (!inside) {
                            if (outside == 0) firstOutside = "($localX, $localY)"
                            outside++
                        }
                    }
                }
                assertEquals(
                    "$type on lane $lane draws $outside occupant pixels outside its glass, first at $firstOutside",
                    0,
                    outside,
                )
                frame.recycle()
            }
        }
    }

    /**
     * **15% of the pane's width of visible glass between a head and each pillar -- 13% since 4.18 closed.**
     *
     * Measured as light, literally: on each row the head occupies, the run of glass-coloured
     * pixels between the head and the pane's own edge. The shoulders are excluded -- the
     * criterion is about the head, and a seated figure's shoulders legitimately sit close to the
     * door frame -- by limiting the scan to rows more than four units above the sill.
     *
     * Two seats do not soften it: the outermost occupant ink on each row is what is measured
     * against each pillar, so a second head cannot buy the first one any slack.
     *
     * **The threshold moved from 15% to 13% in the closing pass, and that is a decision rather
     * than a test fix.** 15% was chosen in rc2 for a single profile bust and was never derived
     * from anything; holding it while also opening clear glass between two frontal heads needs
     * about seven more units of pane than the saloon's cabin can hold. The maintainer lowered it
     * to 13% and spent the recovered width on the gap, which is what
     * [theTwoHeadsAreSeparatedByClearGlass] measures. The full arithmetic is at
     * [SceneObjectRenderer.CAR_HEAD_X_UNITS].
     */
    @Test
    fun everyOccupantClearsItsPillarsByFifteenPercentOfItsHead() {
        for ((type, shell) in typesAndShells()) {
            val lane = SceneSpace.ROAD_LANE_NEAR_Y_FRACTION
            val frame = frameWithOneCar(type, lane, shell = shell)
            val px = unitPx(lane, type)
            val centreX = CAR_PROGRESS * (WIDTH + 2 * CAR_TRAVEL_MARGIN) - CAR_TRAVEL_MARGIN
            val groundY = lane * HEIGHT
            val isTruck = type == CarType.FIRE_TRUCK
            val sill = if (isTruck) {
                SceneObjectRenderer.FIRE_TRUCK_SILL_Y_UNITS
            } else {
                SceneObjectRenderer.CAR_SILL_Y_UNITS
            }
            val paneTop = if (isTruck) {
                SceneObjectRenderer.FIRE_TRUCK_SILL_Y_UNITS -
                    SceneObjectRenderer.FIRE_TRUCK_GLASS_HEIGHT_UNITS
            } else {
                SceneObjectRenderer.CAR_GLASS_ORIGIN_Y_UNITS
            }
            val pixels = IntArray(WIDTH * HEIGHT)
            frame.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)

            fun screenY(local: Float) = (groundY + (local - CAR_LOCAL_ORIGIN_ABOVE_CONTACT_UNITS) * px).toInt()
            val yFrom = screenY(paneTop)
            val yTo = screenY(sill - 4f)
            // The pane windows come from the glass geometry constants. The saloon's glasshouse is
            // one pane, so the criterion is what the words say for one pane: the light between the
            // occupants and the pane's two pillars, and there are only two of those. rc2 listed
            // two windows here because it had a mullion and each head owed 15% of its own half;
            // rc5 measured that arrangement at 53 units of glass against 47 for a single pane, and
            // six units of bonnet is what a mullion would have cost. See
            // [SceneObjectRenderer.CAR_HEAD_X_UNITS].
            val (paneL, paneR) = if (isTruck) TRUCK_PANE else cabinPane(shell!!)
            val xFrom = (centreX + paneL * px).toInt().coerceIn(0, WIDTH - 1)
            val xTo = (centreX + paneR * px).toInt().coerceIn(0, WIDTH - 1)

            /** The head's own ink and the glass either side of it, on one row. */
            fun rowRuns(y: Int): Triple<List<IntArray>, Int, List<Int>>? {
                // A head is a *run* of occupant ink, not a stray pixel. The anti-aliased edge
                // where a raked A-pillar meets the glass blends the body's tint toward the
                // occupant palette, and on a warm-tinted car a few of those pixels land inside
                // the skin tolerance -- which read as a head pressed against the pillar when
                // the scan took the leftmost matching pixel. MIN_RUN_PX is the same floor the
                // head-gap scan already uses for the same reason.
                val glassXs = mutableListOf<Int>()
                val runs = mutableListOf<IntArray>()
                var runStart = -1
                for (x in xFrom..xTo + 1) {
                    val ink = x <= xTo && isOccupantColour(pixels[y * WIDTH + x])
                    if (ink) {
                        if (runStart < 0) runStart = x
                    } else {
                        if (runStart >= 0 && x - runStart >= MIN_RUN_PX) runs.add(intArrayOf(runStart, x - 1))
                        runStart = -1
                        if (x <= xTo && isGlass(pixels[y * WIDTH + x])) glassXs.add(x)
                    }
                }
                return if (runs.isEmpty() || glassXs.isEmpty()) null else Triple(runs, 0, glassXs)
            }

            // **The head's own width, which is what the light is a share of.** v4.18 divided by
            // the pane, so the criterion moved every time the glass did and had to be lowered
            // from 15% to 13% to pay for a wider cabin. Item 6 of BACKLOG_v4_19.md asked for it
            // against the head instead; the floor is 15%, derived in VehiclePedestrianScaleTest
            // from what a band of glass under ~3 px reads as at the far lane.
            // **One head's width, not the pair's span.** The rows where both occupants show
            // carry two runs; taking min-to-max across the row would make the denominator the
            // whole seated pair, which halves every ratio and is not what "a share of the head"
            // means. The widest single run is one head at its widest row.
            var headWidth = 0f
            for (y in yFrom..yTo) {
                if (y < 0 || y >= HEIGHT) continue
                val (runs, _, _) = rowRuns(y) ?: continue
                headWidth = maxOf(headWidth, runs.maxOf { (it[1] - it[0] + 1).toFloat() })
            }
            assertTrue("$type/$shell: no occupant found behind the glass at all", headWidth > 0f)

            var worst = Float.MAX_VALUE
            var worstAt = ""
            for (y in yFrom..yTo) {
                if (y < 0 || y >= HEIGHT) continue
                val (runs, _, glassXs) = rowRuns(y) ?: continue
                val headMin = runs.first()[0]
                val headMax = runs.last()[1]
                val paneLeft = glassXs.filter { it < headMin }.minOrNull() ?: continue
                val paneRight = glassXs.filter { it > headMax }.maxOrNull() ?: continue
                val gapL = (headMin - paneLeft) / headWidth
                val gapR = (paneRight - headMax) / headWidth
                if (minOf(gapL, gapR) < worst) { worst = minOf(gapL, gapR); worstAt = "row $y (L=$gapL R=$gapR)" }
            }
            android.util.Log.i("v419-light", "$type/$shell worst pillar light ${"%.2f".format(worst * 100)}% of head at $worstAt")
            assertTrue(
                "$type/$shell: the narrowest head-to-pillar light is " +
                    "${"%.1f".format(worst * 100)}% of the head's own width at $worstAt",
                worst >= 0.15f,
            )
            frame.recycle()
        }
    }

    /**
     * **The two heads are separated by clear glass, and neither occludes the other.**
     *
     * The defect this closes: rc5 seated two people 11.5 units apart when the widest head band
     * is 18.08 units, so the front bust cut the rear one's hair and the pair read as one mass
     * with two faces rather than as two seats. Nothing in rc5's criteria caught it -- the fill
     * criterion is *satisfied* by pressing the two together, which is the cheapest way to fill
     * glass, and the pillar light only looks at the outer edges.
     *
     * Measured on the rendered pixels, every row from the crown down to the chin line, both
     * lanes, all three civilian types:
     *
     *  * the non-glass ink on the row must form **exactly two runs** -- one run means the heads
     *    have merged, which is the rc5 defect;
     *  * the glass between them must be at least **3% of the pane's width**, and it is
     *    contiguous by construction, the runs being the complement of the glass.
     *
     * **Below the chin the busts are allowed to meet, and are meant to**: two people sitting one
     * behind the other occlude at the shoulders, and that contact is the depth cue that says
     * there are two seats rather than a bench. So the scan stops at [CHIN_LOCAL_Y], safely above
     * every seasonal chin (the lowest is the winter woman's at local y 9.15).
     */
    @Test
    fun theTwoHeadsAreSeparatedByClearGlass() {
        for ((type, shell) in typesAndShells().filter { it.first != CarType.FIRE_TRUCK }) {
            for (lane in LANES) {
                val frame = frameWithOneCar(type, lane, shell = shell)
                val px = unitPx(lane, type)
                val centreX = CAR_PROGRESS * (WIDTH + 2 * CAR_TRAVEL_MARGIN) - CAR_TRAVEL_MARGIN
                val groundY = lane * HEIGHT
                val pixels = IntArray(WIDTH * HEIGHT)
                frame.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                fun screenY(local: Float) =
                    (groundY + (local - CAR_LOCAL_ORIGIN_ABOVE_CONTACT_UNITS) * px).toInt()

                var worstGap = Float.MAX_VALUE
                var worstAt = ""
                var rowsChecked = 0
                // **From four units below the crown, not from the pane's ceiling.** The band the
                // criterion is about is the head band, and its top two units are the tip of the
                // hair, where a head is a couple of pixels wide and two crowns are legitimately
                // close: what the criterion is about is whether two *people* read as two, and
                // that is decided below the dome. v4.18's pane happened to start below the
                // crown so the question never arose; v4.19's is 25 units and starts four above
                // it, and scanning those rows asks whether two hair-tips are separated rather
                // than whether two people are.
                val crown = SceneObjectRenderer.CAR_SILL_Y_UNITS -
                    SceneObjectRenderer.HEAD_CAR_ANCHOR_Y_UNITS * SceneObjectRenderer.CAR_OCCUPANT_SCALE
                for (y in screenY(crown + 4f)..screenY(CHIN_LOCAL_Y)) {
                    if (y < 0 || y >= HEIGHT) continue
                    val (cabL, cabR) = cabinPane(shell!!)
                    val xFrom = (centreX + cabL * px).toInt().coerceIn(0, WIDTH - 1)
                    val xTo = (centreX + cabR * px).toInt().coerceIn(0, WIDTH - 1)
                    // The pane's own extent is where glass is; inside it, every run that is *not*
                    // glass is an occupant. Taking the complement rather than matching the
                    // occupant palette is deliberate: the palette misses the woman's brown hair
                    // and the anti-aliased fringe of every silhouette, and at the crown -- where
                    // a head is three pixels wide -- that reads as one head missing entirely.
                    // The pillar light guarantees glass at both ends of every scanned row, so
                    // the extent is always well defined here.
                    var glassMin = Int.MAX_VALUE
                    var glassMax = -1
                    for (x in xFrom..xTo) {
                        if (isGlass(pixels[y * WIDTH + x])) {
                            if (x < glassMin) glassMin = x
                            if (x > glassMax) glassMax = x
                        }
                    }
                    if (glassMax < 0) continue
                    val paneWidth = (glassMax - glassMin + 1).toFloat()
                    if (paneWidth < 12f) continue
                    val runs = ArrayList<IntArray>()
                    var runStart = -1
                    for (x in glassMin..glassMax) {
                        if (!isGlass(pixels[y * WIDTH + x])) {
                            if (runStart < 0) runStart = x
                        } else if (runStart >= 0) {
                            if (x - runStart >= MIN_RUN_PX) runs.add(intArrayOf(runStart, x - 1))
                            runStart = -1
                        }
                    }
                    if (runStart >= 0 && glassMax - runStart >= MIN_RUN_PX) runs.add(intArrayOf(runStart, glassMax))
                    if (runs.isEmpty()) continue
                    // A few pixels of glass showing through the anti-aliased edge of a hairline is
                    // not a gap between two people. The criterion itself says what counts as a
                    // separation -- 3% of the pane -- so anything narrower is closed up before the
                    // runs are counted. Without this the driver's own hair edge splits into two
                    // runs on the rows where it blends to within tolerance of the glass.
                    val minGapPx = kotlin.math.ceil(GAP_FRACTION * paneWidth).toInt()
                    val merged = ArrayList<IntArray>()
                    for (r in runs) {
                        val last = merged.lastOrNull()
                        if (last != null && r[0] - last[1] - 1 < minGapPx) last[1] = r[1] else merged.add(r)
                    }
                    runs.clear()
                    runs.addAll(merged)
                    rowsChecked++
                    assertEquals(
                        "$type on lane $lane, row $y (local y " +
                            "${"%.1f".format(localY(y, lane, type))}): the occupants form " +
                            "${runs.size} run(s), not two -- the heads have merged",
                        2, runs.size,
                    )
                    // by construction of the runs, everything between them is glass
                    val clear = runs[1][0] - runs[0][1] - 1
                    val frac = clear / paneWidth
                    if (frac < worstGap) {
                        worstGap = frac
                        worstAt = "row $y (local y ${"%.1f".format(localY(y, lane, type))}, " +
                            "$clear px of $paneWidth)"
                    }
                }
                assertTrue("$type on lane $lane: no rows carried occupant ink", rowsChecked > 0)
                android.util.Log.i(
                    "rc6-gap",
                    "$type lane=$lane narrowest head-to-head gap ${"%.2f".format(worstGap * 100)}% " +
                        "over $rowsChecked rows",
                )
                assertTrue(
                    "$type on lane $lane: the narrowest clear glass between the two heads is " +
                        "${"%.2f".format(worstGap * 100)}% of the pane at $worstAt",
                    worstGap >= GAP_FRACTION,
                )
                frame.recycle()
            }
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
            // v4.19: a plain car wears one of three bodies and they are deliberately not the
            // same height, so the prediction has to ask which one this vehicle is rather than
            // assume the family's reference.
            val shell = CarShell.forCar(
                CarObject(lane, 0f, -CAR_PROGRESS, 0, true, CarType.PLAIN),
            )
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
                    // The ground shadow is translucent and the car itself is opaque, which is what
                    // separates them -- but the frame is composited, so every pixel in it has
                    // alpha 255 and the test this line used to be could never fire. It did not
                    // show while the shadow was drawn 37 units up, inside the body's own outline;
                    // on the road it straddles the wheel contact and this measurement became the
                    // car plus half an oval. See [SceneGolden.isGroundShadowOnly].
                    if (SceneGolden.isGroundShadowOnly(rowA[x], rowB[x])) continue
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                    break
                }
            }
            val height = (bottom - top + 1).toFloat()
            val predicted = shell.unitsTall * unitPx(lane, CarType.PLAIN)
            assertEquals(
                "a plain $shell on lane $lane measured $height px",
                predicted,
                height,
                2.5f,
            )
        }
    }

    private companion object {
        /** Twice a real phone's viewport. The goldens' 360x800 is too coarse to measure a face
         * on, and rc4 doubled the ruler again: at 1080x2400 a far-lane face is ~15 px, where one
         * pixel of blob quantisation is 7% of a measurement judged against a 10% band. The scene
         * is resolution-independent (every proportion this file asserts is a ratio), so the
         * doubling sharpens the ruler without touching what is measured. */
        const val WIDTH = 2160
        const val HEIGHT = 4800

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
         * The adult summer frontal faces, in the sprite's own local units: the skin blob of
         * `person_man_summer_head_car` is 77 px tall and the woman's 82 -- unlike the profiles
         * they are not equal, because her fringe and his flat-top cut different hairlines.
         * Which of the two drives is a pure function of the car's lane and start delay, so the
         * prediction picks the same way `drawCar` does.
         */
        const val MAN_FACE_UNITS = 77f / 3f
        const val WOMAN_FACE_UNITS = 82f / 3f

        fun driverFaceUnits(lane: Float, startDelaySeconds: Float): Float {
            val seed = kotlin.math.abs((lane * 7919f + startDelaySeconds * 131f).toInt())
            return if (seed % 2 == 0) MAN_FACE_UNITS else WOMAN_FACE_UNITS
        }

        /** The shipped skin palette, from `tools/generate_skin_variants.py`. */
        val SKIN_TONES = listOf(
            intArrayOf(240, 201, 166), // woman F0C9A6
            intArrayOf(220, 169, 124), // man DCA97C
            intArrayOf(169, 113, 75),  // boy A9714B
            intArrayOf(239, 185, 148), // girl EFB994
        )

        /** The occupants' non-skin colours: hair and summer shirts, for the outside-glass scan. */
        val OCCUPANT_EXTRA_COLOURS = listOf(
            intArrayOf(0x2B, 0x2A, 0x33), // dark hair (man, boy)
            intArrayOf(0xF7, 0xCE, 0x64), // woman's hair / girl's shirt
            intArrayOf(0xC9, 0x8F, 0x5A), // girl's hair
            intArrayOf(0x4E, 0x9F, 0xB5), // man's shirt
            intArrayOf(0xE4, 0x62, 0x3E), // woman's shirt
            intArrayOf(0x6B, 0xA8, 0x4F), // boy's shirt
            intArrayOf(0xEF, 0xDF, 0xC4), // the seatbelt (rc4 frontal busts)
        )

        /** `car_window`'s glass, which nothing else in the scene is painted in. */
        val GLASS = intArrayOf(185, 216, 228)

        /** Small enough to keep a face, large enough to drop an anti-aliased speck. */
        const val MIN_BLOB_AREA = 40

        /**
         * Where the head-to-head gap stops being measured: the chin line, less a margin.
         *
         * **Derived in v4.19 rather than stated.** It was the literal 7.5, chosen against a sill
         * of 12; the sill is 9 now and the chin sits at 4.72, so the old number was three units
         * *below* the chin -- inside the shoulder band, where the busts are meant to meet and
         * where the seat back between them is legitimately visible. Scanning there asked the
         * two-runs question of three objects.
         *
         * The chin is the bust canvas's own geometry: the head is [HEAD_CAR_HEAD_UNITS] of the
         * [HEAD_CAR_ANCHOR_Y_UNITS]-unit canvas, so the rest of the canvas below it is neck and
         * shoulders, and the chin is that much above the sill.
         */
        val CHIN_LOCAL_Y = SceneObjectRenderer.CAR_SILL_Y_UNITS -
            (SceneObjectRenderer.HEAD_CAR_ANCHOR_Y_UNITS - SceneObjectRenderer.HEAD_CAR_HEAD_UNITS) *
            SceneObjectRenderer.CAR_OCCUPANT_SCALE - 0.5f

        /** Anti-aliased fringe is not an occupant. Two pixels of run is. */
        const val MIN_RUN_PX = 2

        /** The clear glass the closing pass requires between the two heads, as a share of the pane. */
        const val GAP_FRACTION = 0.03f

        /**
         * Large enough to keep only the two seated faces, in either lane.
         *
         * A seated face is roughly 14x11 units of skin at [SceneObjectRenderer.CAR_OCCUPANT_SCALE];
         * on the far lane one unit is about 2.6 px here, so the smaller of the two runs to a few
         * hundred pixels. 250 keeps both and drops the odd fleck of an ear or a hand that
         * [MIN_BLOB_AREA] lets through, which is what makes the seat *count* countable.
         */
        const val MIN_FACE_AREA = 250

        /**
         * The cabin pane a body's occupants sit in, in that body's own local units.
         *
         * Derived from [CarShell] rather than restated. The estate's glass sprite also carries
         * the third window over the load bay, which is not cabin glazing: measuring against it
         * would flatter the pillar light and flatten the fill, so the cabin pane stops at the
         * B-pillar. The number is the sprite's first pane, read off the artwork.
         */
        fun cabinPane(shell: CarShell): Pair<Float, Float> {
            val left = shell.glassXUnits
            val width = if (shell == CarShell.ESTATE) ESTATE_CABIN_PANE_WIDTH_UNITS else shell.glassWidthUnits
            return left to left + width
        }

        /** The estate's cabin pane alone, sill to sill, without the third window. */
        const val ESTATE_CABIN_PANE_WIDTH_UNITS = 60f

        /** The appliance's cab glass, painted into its body: see `firetruck_body.svg`. */
        val TRUCK_PANE = -33.5f to -4f

        /** The two shared lamp lenses, in local units: `car_lamp_front` and `car_lamp_rear`. */
        const val LAMP_FRONT_W_UNITS = 6f
        const val LAMP_REAR_W_UNITS = 4f
        const val LAMP_H_UNITS = 4f

        /** `police_stripe` / `taxi_checker` are 120x27 px: 40 x 9 local units. */
        const val LIVERY_HEIGHT_UNITS = 9f
    }
}
