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
        val spec = carSpecFor(type, lane, reverse, progress, shell)
        val layout = SceneObjectLayout(staticObjects = emptyList(), cars = listOf(spec))
        // A car whose identity had to be chosen starts at its own queue slot and is *driven* to
        // where the measurement wants it, at one unit of progress per second, so it arrives at
        // exactly [progress]. A car whose identity does not matter is simply placed there.
        return render(layout, peopleVisible = false, advanceBy = progress + spec.startDelaySeconds)
    }

    /**
     * The vehicle a case describes: its type, its lane, its direction, and -- when the case is
     * about a particular body -- an identity that actually produces that body.
     *
     * **This used to nudge `startDelaySeconds` by a millionth of a second until the hash landed
     * where it was wanted, and v4.20 made that impossible on purpose.** A candidate's identity is
     * its lane and its *queue slot*, and the slot is one of [SceneObjectCatalog.CAR_SLOTS_PER_LANE]
     * points on the loop -- so a millionth of a second no longer changes anything, because
     * `SceneObjectCatalog.candidateIndexOf` quantises to the grid the catalogue actually generates
     * on. The road has ten identities and a test may pick among those ten; it may not conjure an
     * eleventh.
     *
     * That leaves the fixture's real problem, which the old approach hid: `startDelaySeconds` was
     * doing two jobs at once. It set the identity *and*, with `speedFraction = 0`, the car's
     * position on screen. Those two cannot both be free. So they are separated the way the app
     * separates them -- the delay is the identity, and the *progress* is where the car has driven
     * to since -- and [frameWithOneCar] advances the scene to put the car exactly where every
     * measurement here already expects it. No production seam, and the car is a car the catalogue
     * could have generated.
     */
    private fun carSpecFor(
        type: CarType,
        lane: Float,
        reverse: Boolean,
        progress: Float,
        shell: CarShell?,
    ): CarObject {
        val delay = if (shell == null) -progress else gridDelayForShell(shell, lane, type)
        return CarObject(
            laneYFraction = lane,
            // One unit of progress per second, so `advanceBy` reads as "how far it still has to
            // go". Zero when the identity was not chosen, which leaves those cases exactly as they
            // were: the car is placed by its delay and never moves.
            speedFraction = if (shell == null) 0f else 1f,
            startDelaySeconds = delay,
            color = 0xFFB4513C.toInt(),
            reverse = reverse,
            type = type,
        )
    }

    /** The canonical queue slot, of the five the road has, that puts [type] on [shell] in [lane]. */
    private fun gridDelayForShell(shell: CarShell, lane: Float, type: CarType): Float {
        for (slot in 0 until SceneObjectCatalog.CAR_SLOTS_PER_LANE) {
            val delay = SceneObjectCatalog.CAR_LOOP_ENTRY_PROGRESS +
                SceneObjectCatalog.CAR_LOOP_SPAN * slot / SceneObjectCatalog.CAR_SLOTS_PER_LANE
            val spec = CarObject(lane, 0f, delay, 0, true, type)
            if (CarShell.forCar(spec) == shell) return delay
        }
        error("no queue slot on lane $lane puts a $type on the $shell")
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

    private fun render(layout: SceneObjectLayout, peopleVisible: Boolean, advanceBy: Float = 0f): Bitmap {
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
        if (advanceBy != 0f) renderer.update(advanceBy)
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

    /**
     * The driver's face, identified by **where it is** rather than by how big it is.
     *
     * These tests used to take the tallest face in the frame, and that worked for one release by
     * accident: the driver was a woman in every car the app could produce (see [SeatedOccupants]),
     * and a woman's face is the taller of the two. With the seats dealt properly a man drives half
     * the cars and the tallest face in the frame is then the *passenger*, so the measurement
     * silently changed subject.
     *
     * The vehicle artwork faces left when `reverse` is set, and every frame here is built with it
     * set, so the driver is the leading -- leftmost -- face. That is the same rule
     * `aCivilianCarSeatsADriverForwardAndAPassengerBehind` asserts the renderer obeys, which is
     * what makes it safe to rely on here.
     */
    private fun driverFace(frame: Bitmap): Blob {
        val faces = skinBlobs(frame)
        check(faces.isNotEmpty()) { "no face in the frame at all" }
        return faces.minByOrNull { it.centreX }!!
    }

    /** One connected run of skin-coloured pixels: a face, a hand, or a bare leg. */
    private class Blob(val minX: Int, val maxX: Int, val minY: Int, val maxY: Int, val area: Int) {
        val height get() = maxY - minY + 1
        val width get() = maxX - minX + 1
        val centreX get() = (minX + maxX) / 2f
    }

    /**
     * The **head block** on the rendered frame: the crown of the hair down to the jaw, in pixels.
     * Null when the figure does not stand clear -- see the cap below.
     *
     * ### Why this and not the face
     *
     * Everything in this class used to be measured on the visible skin -- the face's own run of
     * skin rows -- and that is not a dimension. It is a property of the haircut. Measured on the
     * v4.25 family, the skin's share of the head block is **0.861** for a walking man, **0.794**
     * for the same man seated, **0.623** for a walking woman and **0.733** for her seated: a third
     * of the quantity is hair, so no scale can make two poses agree on it and a test that asks
     * them to is asking the wrong question. It was only ever satisfiable while the four drawings
     * happened to leave similar amounts of face uncovered, and B "Rilievo" -- fuller hair on both
     * adults, a lock across the woman's cheek -- ended that.
     *
     * The head block does not move: it is what [SceneObjectRenderer.PERSON_HEAD_SPRITE_UNITS] and
     * [SceneObjectRenderer.HEAD_CAR_HEAD_UNITS] declare, what every occupant scale is derived
     * from, and it is the same for both adults -- which is why nothing here has to know which of
     * them is driving any more.
     *
     * ### The rule, which is `OccupantHeadFitTest`'s rule
     *
     * That JVM test measures the same block on the shipped PNG: content top for the crown, and for
     * the jaw *the width* -- the last row of the skin's first run still at least half as wide as
     * the widest. The neck is drawn in skin since B "Rilievo", so a rule that took the end of the
     * run would put the jaw below the collar. This reads the same block off painted pixels: the
     * crown by walking up the face's own columns while [isInk] still finds the figure, the jaw by
     * that same half-width test on the face blob's rows.
     *
     * ### Why the crown is found against a background and not against a palette
     *
     * The first attempt matched hair colours and measured one driver right and the next one
     * thirteen pixels short, because the palette it matched against still had the previous
     * family's hair in it. A palette written into a test is a copy of the artwork that goes stale
     * silently. [isInk] asks the question the eye asks instead: for a driver, *not the glass
     * showing through*; for a pedestrian, *not what this frame looks like with the people switched
     * off*. Neither has a colour in it.
     *
     * ### The cap, and why a figure can decline to be measured
     *
     * A pedestrian standing in front of another is one silhouette, and no rule reading painted
     * pixels can say where the front one's hair ends and the back one's shoulder begins: measured
     * that way the near woman's head came out 134 px against an expected 38. So the walk is capped
     * at [CROWN_WALK_CAP] times the face it started from -- the widest head block this artwork
     * draws is 1.7 faces -- and a figure whose crown is still inked at the cap is reported as
     * unmeasurable rather than measured wrongly.
     */
    private fun headBlock(pixels: IntArray, face: Blob, isInk: (Int) -> Boolean): Int? {
        val cap = (face.height * CROWN_WALK_CAP).toInt()
        var up = 0
        var y = face.minY - 1
        // Two adjacent inked pixels, not one: a single one is the anti-aliased rim of whatever is
        // behind the figure, and one such pixel per row would walk this measurement to the horizon.
        while (y >= 0 && (face.minX until face.maxX).any { isInk(y * WIDTH + it) && isInk(y * WIDTH + it + 1) }) {
            up++
            if (up > cap) return null
            y--
        }
        val crown = face.minY - up
        val widths = IntArray(face.height)
        for (row in face.minY..face.maxY) {
            var n = 0
            for (x in face.minX..face.maxX) if (isSkin(pixels[row * WIDTH + x])) n++
            widths[row - face.minY] = n
        }
        val widest = widths.max()
        var jaw = face.minY
        for (i in widths.indices) {
            if (widths[i] != 0 && widths[i] >= widest / 2f) jaw = face.minY + i
        }
        return jaw - crown + 1
    }

    /**
     * A driver's head block: inside the pane, ink is everything the glass is not.
     *
     * `theOccupantsFillHalfTheGlass` measures its ink the same way and says why: the window is
     * glass where you can see through it and occupant where you cannot. The walk is floored at the
     * pane's own top row so that it stops at the glass instead of climbing into the roof.
     */
    private fun driverHeadBlock(frame: Bitmap, pixels: IntArray): Int {
        val glass = glassBox(frame) ?: error("no glass in the frame")
        val top = glass.minY
        return headBlock(pixels, driverFace(frame)) { i ->
            i / WIDTH >= top && (pixels[i] ushr 24) >= 200 && !nearGlass(pixels[i])
        } ?: error("the driver's head reaches the top of its own pane")
    }

    /** Glass, or close enough to it to be the pane's own anti-aliased edge. */
    private fun nearGlass(pixel: Int): Boolean {
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        return abs(r - GLASS[0]) <= INK_DISTANCE &&
            abs(g - GLASS[1]) <= INK_DISTANCE &&
            abs(b - GLASS[2]) <= INK_DISTANCE
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

    /**
     * Inside one of the wheel discs, which are vehicle artwork and not a person.
     *
     * **The tyre is `#2B2A33` since v5.6F, which is the scene's ink -- and the scene's ink is
     * also `PeopleColours.HAIR[0]`, black hair.** A colour scan cannot tell them apart and should
     * not try: the discs are drawn in code at centres and radii this file can read, so the
     * exclusion is geometric, exactly as the taxi sign's, the livery band's and the lamp seats'
     * are. Without it every tyre pixel counts as an occupant painted outside the glass -- measured
     * at 4 736 of them on one estate.
     *
     * The margin is a unit and a half, the same the lamp seats take, for the disc's own
     * anti-aliased rim.
     */
    private fun inWheelDisc(type: CarType, shell: CarShell?, localX: Float, localY: Float): Boolean {
        val isTruck = type == CarType.FIRE_TRUCK
        val radius = if (isTruck) {
            SceneObjectRenderer.FIRE_TRUCK_WHEEL_RADIUS_UNITS
        } else {
            SceneObjectRenderer.CAR_WHEEL_RADIUS_UNITS
        }
        val centres = if (isTruck) {
            listOf(
                -SceneObjectRenderer.FIRE_TRUCK_WHEEL_X_UNITS,
                SceneObjectRenderer.FIRE_TRUCK_INNER_WHEEL_X_UNITS,
                SceneObjectRenderer.FIRE_TRUCK_WHEEL_X_UNITS,
            )
        } else {
            listOf(shell!!.wheelFrontXUnits, shell.wheelRearXUnits)
        }
        val cy = SceneObjectRenderer.VEHICLE_GROUND_Y_UNITS - radius
        return centres.any { cx ->
            val dx = localX - cx
            val dy = localY - cy
            dx * dx + dy * dy <= (radius + 1.5f) * (radius + 1.5f)
        }
    }

    /**
     * The appliance's cream band, the one across its flank at local y 8..12.
     *
     * `#F2E4C9` with its shadow paper under it, and the cream trousers an occupant can wear are
     * `#EFDFC4`: three, five and five levels apart, so the band itself stays outside the
     * tolerance of 4 and the anti-aliased pixels where it meets its own shadow do not. Measured
     * on this build: **two pixels** of a whole appliance. Excluded the way the taxi sign and the
     * livery band already are -- it is a piece of vehicle artwork that happens to be painted in a
     * cream the outfits also deal, and the scan's job is to find a person outside the glass.
     *
     * The rectangle is the drawing's, read off `firetruck_body.svg`: the band spans x -46..46 at
     * y 8..12 and its shadow paper reaches 47.42 and 13.77, with half a unit for the rim.
     */
    private fun inTruckBand(localX: Float, localY: Float): Boolean =
        localX >= -46.5f && localX <= 48f && localY >= 7.5f && localY <= 14.3f

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

    /**
     * The glass **and the shadow the body paper casts on it**, which is also glass.
     *
     * v5.6F: «Ritaglio» lays the glass sheet *behind* the body and cuts the panes out of it, and
     * paints a band of `mix(glass, ink, 0.34)` along the top and the front edge of every hole --
     * the shadow of the paper's own cut edge falling on the sheet below. That band is the one
     * mark in the drawing that says the glass is behind rather than stuck on top, and a scan that
     * did not count it read the pane as 22.8 units of a 25-unit hole and put its top edge 2.6
     * units too low. It is a second colour and not a wider tolerance: [GLASS_SHADE] is the
     * drawing's own value, and stretching the tolerance far enough to reach it would have
     * swallowed half the body palette on the way.
     */
    private fun isGlass(pixel: Int): Boolean {
        if ((pixel ushr 24) < 200) return false
        val r = (pixel shr 16) and 0xFF
        val g = (pixel shr 8) and 0xFF
        val b = pixel and 0xFF
        if (abs(r - GLASS[0]) <= 6 && abs(g - GLASS[1]) <= 6 && abs(b - GLASS[2]) <= 6) return true
        return abs(r - GLASS_SHADE[0]) <= 6 && abs(g - GLASS_SHADE[1]) <= 6 &&
            abs(b - GLASS_SHADE[2]) <= 6
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
            // The pane's own two colours: the glass, and the shadow the body paper's cut edge
            // casts on it. See [isGlass] -- a box drawn round the first alone measures the hole
            // minus its shadow band, which is 22.8 units of a 25-unit pane.
            val plain = abs(r - GLASS[0]) <= 4 && abs(g - GLASS[1]) <= 4 && abs(b - GLASS[2]) <= 4
            val shade = abs(r - GLASS_SHADE[0]) <= 4 && abs(g - GLASS_SHADE[1]) <= 4 &&
                abs(b - GLASS_SHADE[2]) <= 4
            if (!plain && !shade) continue
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

    // ------------------------------------------------------------------ the glass

    /**
     * The pane is drawn [SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS] tall, on both lanes --
     * 23 units since rc2, measured off the glass's own colour so it is the *drawn* height.
     */
    /**
     * Every colour this class scans for is one the scene can really put on an occupant.
     *
     * **The check had to change shape in v4.30, and the reason is the point of it.** It used to
     * scan the shipped people for each colour on the list, because the list was a hand-written copy
     * of the artwork's palette and a copy goes stale silently -- twice, in this file's history. The
     * artwork no longer carries those colours at all: a person ships as fixed art plus weight
     * masks, and the head and the garments arrive at the blit from `PeopleColours`. Scanning the
     * PNGs for a shirt colour would now find nothing and would be measuring the change rather than
     * guarding against it.
     *
     * So the list is derived from `PeopleColours` and what is left to check is the two things a
     * derived list can still get wrong: that no two palettes deal the same colour, which would make
     * a scan unable to say what it had found, and that the **one** entry still written by hand --
     * the seatbelt, which is painted into the fixed art rather than dealt -- is really painted.
     */
    @Test
    fun everyColourThisClassScansForIsOneThePeopleCanBeDrawnIn() {
        val duplicated = OCCUPANT_EXTRA_COLOURS
            .groupBy { "#%02X%02X%02X".format(it[0], it[1], it[2]) }
            .filterValues { it.size > 1 }
            .keys.sorted()
        assertEquals(
            "the scan list holds the same colour twice, so it compares the same thing twice: " +
                "$duplicated",
            emptyList<String>(), duplicated,
        )

        // The seatbelt: painted rather than dealt, so it is the one thing on a seated bust that a
        // derived list could miss. It happens to be a colour the outfits deal too, so it is in the
        // list either way -- but only counting it where it is drawn proves that.
        val belt = channelsOf(SEATBELT_COLOUR)
        val options = android.graphics.BitmapFactory.Options().apply { inScaled = false }
        var found = 0
        for (resId in PeopleLayerTable.CAR.flatMap { it.map { shape -> shape[PeopleLayerTable.FIXED] } }) {
            val bitmap = android.graphics.BitmapFactory.decodeResource(
                InstrumentationRegistry.getInstrumentation().targetContext.resources, resId, options,
            ) ?: error("$resId could not be decoded")
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if ((pixel ushr 24) < 200) continue
                    if (abs(((pixel shr 16) and 0xFF) - belt[0]) <= 4 &&
                        abs(((pixel shr 8) and 0xFF) - belt[1]) <= 4 &&
                        abs((pixel and 0xFF) - belt[2]) <= 4
                    ) {
                        found++
                    }
                }
            }
            bitmap.recycle()
        }
        assertTrue(
            "the seatbelt colour is scanned for but the seated busts no longer paint it ($found px)",
            found >= 100,
        )
        assertTrue(
            "and it must be in the list the scan uses, however it got there",
            OCCUPANT_EXTRA_COLOURS.any { it.contentEquals(belt) },
        )
    }

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
                SceneObjectRenderer.CAR_GLASS_TOP_Y_UNITS,
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

    /*
     * **The pedestrian guard that used to sit here was retired in v4.25, and it was not relaxed.**
     *
     * `aDriversFaceMatchesAPedestriansOnceDepthIsRemoved` divided a driver's face and a
     * pedestrian's by their own family's visible skin and asked the two to agree within 10%. The
     * quantity is not a dimension -- see [headBlock] -- so it was rebuilt on the head block, and
     * rebuilt it stopped being able to say anything a pixel can support:
     *
     *  * **a pedestrian's depth is not in the frame.** Its ground row would have to be the bottom
     *    of its own ink, and the scene draws a soft shadow under everything, so that walk ends
     *    thirty pixels below the heel -- a person read as standing a metre nearer than they are.
     *    The pavement is a band and its two edges are 8% of projection apart, which is most of the
     *    criterion's own 10% before anything has been measured.
     *  * **the two crowns are not measured against the same thing.** A driver's is found against
     *    the pane, a pedestrian's against the street, and the two backgrounds cost a different
     *    number of anti-aliased rows -- worth about 5% on a 35-pixel head, in a direction that
     *    does not cancel.
     *  * **the crowd hides the figures worth measuring.** At full density this frame paints
     *    seventeen faces, and the two nearest adults are standing in front of somebody else.
     *
     * What it was for is covered where it can be answered exactly: `VehiclePedestrianScaleTest`
     * asserts *an occupant's head stays in a sane relation to a pedestrian's at the same depth*
     * on the arithmetic, and `OccupantHeadFitTest` re-measures both heads on the shipped PNGs.
     * Mutating [SceneObjectRenderer.PERSON_HEAD_SPRITE_UNITS] by the 17% this release's first
     * build was wrong by fails **six** assertions across those two classes -- and passed the
     * rebuilt pixel version, which is why that version is not here.
     */

    /**
     * Every vehicle type carries a driver of the size its own glass implies, on either lane.
     *
     * The predicted head is the artwork's own head over the scale the bust is drawn at over the
     * lane's projection -- three separate things, all of which have to be right for the
     * measurement to land.
     *
     * **v4.25: the head block, not the face.** The prediction used to be the driver's *visible
     * skin*, which meant asking [SeatedOccupants] which adult had been dealt this car and using a
     * different constant for each -- and a quantity that differs between two people of the same
     * size cannot check a size. [SceneObjectRenderer.HEAD_CAR_HEAD_UNITS] is the same for both
     * adults, so the prediction no longer has to know who is driving; and because that constant is
     * also the divisor inside [SceneObjectRenderer.CAR_OCCUPANT_SCALE], what this asserts is the
     * statement the size table makes: **a driver's head is the scene's one head, at 97%**.
     *
     * ### Two figures, because one vehicle is a pixel grid and twelve are a scale
     *
     * Measured across all twelve cases, the drawn head lands between 1.3 px under and 2.7 px over
     * the prediction, and the sign follows the vehicle rather than the lane -- it is where each
     * bust's crown and jaw fall between two pixel rows, on a head that is 43 to 53 px tall. Two
     * rasterised edges cannot be averaged away in one frame, so the **mean** carries the check
     * that matters -- a wrong constant moves all twelve the same way, and the mean is held to
     * 1.5 px, tighter in relative terms than the 1.5 px this test used on a 31 px face -- while
     * each individual case is held to 4 px, which no rounding can reach but a misplaced seat can.
     */
    @Test
    fun everyVehicleTypeDrawsItsDriverAtTheSizeItsGlassImplies() {
        val deviations = ArrayList<Pair<String, Float>>()
        for ((type, shell) in typesAndShells()) {
            for (lane in LANES) {
                val frame = frameWithOneCar(type, lane, shell = shell)
                val pixels = IntArray(WIDTH * HEIGHT)
                frame.getPixels(pixels, 0, WIDTH, 0, 0, WIDTH, HEIGHT)
                val scale = if (type == CarType.FIRE_TRUCK) {
                    SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE
                } else {
                    SceneObjectRenderer.CAR_OCCUPANT_SCALE
                }
                val predicted = SceneObjectRenderer.HEAD_CAR_HEAD_UNITS * scale * unitPx(lane, type)
                val measured = driverHeadBlock(frame, pixels)
                deviations += "$type/$shell on lane $lane: $measured px against ${"%.2f".format(predicted)}" to
                    (measured - predicted)
                frame.recycle()
            }
        }
        for ((what, deviation) in deviations) {
            assertTrue("$what -- ${"%.2f".format(deviation)} px out", abs(deviation) <= 4f)
        }
        val mean = deviations.map { it.second }.sum() / deviations.size
        assertEquals(
            "the drawn heads are ${"%.2f".format(mean)} px from the size table on average: $deviations",
            0f,
            mean,
            1.5f,
        )
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
     * back in a different shape. rc5 seats two, which puts it over half.
     *
     * **v4.25 measured 42.9-44.5% here for one build, and the answer was the artwork, not this
     * number.** The seated head had been drawn 20.7 units wide against the 37.0 of the family it
     * replaced, at the same height, because the band rule that keeps two occupants clear of each
     * other was reading the bust's units as though they were the car's -- a constraint twice as
     * tight as the car actually is (`build_people_concepts.py`, `SEATED_HALF_BAND`). The floors
     * were briefly re-derived to 40/35% to match what that build drew; the maintainer reversed
     * that, the head was given back its proportion, and **the criterion is the one it always
     * was**. A head narrowed to fit a window is the per-asset correction `AI_PROJECT_RULES.md`
     * forbids, and the cause was on the car's side of the comparison.
     *
     * ### How it is measured
     *
     * The summed width of the occupants' ink on a row, over the width of the glass on that same
     * row, across every row of the pane band, in both lanes. Two figures, because one row is not a
     * picture:
     *
     *  * at the **head band**, the row where the occupants' ink is widest, which is what "the head
     *    fills X% of the glass" means when someone looks at the car;
     *  * **averaged over the head's own rows** (those carrying at least half the band's ink), so a
     *    single flattering row cannot carry the criterion.
     *
     * The value at the very bottom of the band, four units above the sill, is *reported* rather
     * than asserted: that row crosses the neck, and a neck is narrower than a head at any seat
     * count -- rc4 measured 28% there and the arithmetic in
     * [SceneObjectRenderer.CAR_HEAD_X_UNITS] shows that forcing 50% at the neck and 15% of pillar
     * light cannot both hold in any pane width. Where the two criteria met, the pane went.
     */
    @Test
    fun theOccupantsFillHalfTheGlass() {
        // Every combination is measured before anything is asserted: a criterion the maintainer
        // re-reads off a delivered frame is worth reporting in full, and a failure on the first
        // lane would otherwise hide what the other five do.
        val measured = ArrayList<String>()
        val short = ArrayList<String>()
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
                val line = "$type lane $lane: band ${"%.1f".format(bandFill * 100)}%, " +
                    "mean ${"%.1f".format(mean * 100)}%, neck ${"%.1f".format(lowest * 100)}%"
                measured += line
                if (bandFill < BAND_FILL_FLOOR || mean < MEAN_FILL_FLOOR) short += line
                frame.recycle()
            }
        }
        assertTrue(
            "the heads must fill ${(BAND_FILL_FLOOR * 100).toInt()}% of the glass at the head band " +
                "and ${(MEAN_FILL_FLOOR * 100).toInt()}% over the head's rows. Short: $short. " +
                "All six: $measured",
            short.isEmpty(),
        )
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
                val pane = glassPane(type, shell)
                val paneL = pane[0]
                val paneR = pane[1]
                val paneT = pane[2]
                val paneB = pane[3]
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
                            localY < SceneObjectRenderer.CAR_GLASS_TOP_Y_UNITS &&
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
                        if (isTruck && inTruckBand(localX, localY)) continue
                        // The wheels: ink discs, and the scene's ink is also the black hair.
                        if (inWheelDisc(type, shell, localX, localY)) continue
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
        // Every cabin measured before anything is asserted: three bodies plus the appliance carry
        // three different panes, and a failure on the first hides what the others do -- which is
        // exactly how the compact's 20% masked the police saloon's 10% for a whole round.
        val report = ArrayList<String>()
        val tight = ArrayList<String>()
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
                SceneObjectRenderer.CAR_GLASS_TOP_Y_UNITS
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
                if (minOf(gapL, gapR) < worst) {
                    worst = minOf(gapL, gapR)
                    val u = unitPx(lane, type)
                    worstAt = "row $y (L=$gapL R=$gapR) | in car units: pane " +
                        "${"%.1f".format((paneLeft - centreX) / u)}..${"%.1f".format((paneRight - centreX) / u)}, " +
                        "ink ${"%.1f".format((headMin - centreX) / u)}..${"%.1f".format((headMax - centreX) / u)}, " +
                        "runs ${runs.map { "%.1f..%.1f".format((it[0] - centreX) / u, (it[1] - centreX) / u) }}, " +
                        "headWidth ${"%.1f".format(headWidth / u)}"
                }
            }
            android.util.Log.i("v419-light", "$type/$shell worst pillar light ${"%.2f".format(worst * 100)}% of head at $worstAt")
            report += "$type/$shell ${"%.1f".format(worst * 100)}%"
            if (worst < 0.15f) {
                tight += "$type/$shell: the narrowest head-to-pillar light is " +
                    "${"%.1f".format(worst * 100)}% of the head's own width at $worstAt"
            }
            frame.recycle()
        }
        assertTrue(
            "every occupant must keep 15% of its own head's width between it and the pillar. " +
                "Tight: $tight. All cabins: $report",
            tight.isEmpty(),
        )
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
     *  * the ink on the row must form **exactly two runs** -- one run means the heads have
     *    merged, which is the rc5 defect;
     *  * what lies between them must be at least **3% of the pane's width**.
     *
     * **v5.6F: what separates two heads may be glass or it may be the car.** Until v5.5 a cabin
     * was one hole and the only thing that could stand between the two busts was clear glass, so
     * "ink" was simply "not glass" and the pillar question did not arise. «Ritaglio» cuts *two*
     * holes with 3.5 units of body paper between them, and the two occupants sit in different
     * windows -- which is a *stronger* separation than clear glass and read as a merge to a scan
     * that called everything non-glass an occupant. So the complement is taken against glass
     * **and** the body's own tint, sampled from the roof band of the very car being measured
     * rather than assumed, and the criterion is unchanged in what it asserts: two marks, and
     * something of the car between them. The old wording is kept above because the defect it was
     * written for -- two busts pressed into one mass -- is exactly as fatal behind two panes as
     * behind one.
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

                // The body's own tint, read off the roof band of this very car: two units above
                // the pane's top edge and on the centre line, which is painted shell on every
                // body and under both roof accessories. Sampled rather than assumed, because a
                // plain car wears one of the two editable colours and a taxi and a police car
                // wear their own.
                val tint = pixels[
                    screenY(SceneObjectRenderer.CAR_GLASS_TOP_Y_UNITS - 2f) * WIDTH + centreX.toInt()
                ]
                fun isShellTint(p: Int): Boolean {
                    if ((p ushr 24) < 200) return false
                    return abs(((p shr 16) and 0xFF) - ((tint shr 16) and 0xFF)) <= 6 &&
                        abs(((p shr 8) and 0xFF) - ((tint shr 8) and 0xFF)) <= 6 &&
                        abs((p and 0xFF) - (tint and 0xFF)) <= 6
                }

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
                        val p = pixels[y * WIDTH + x]
                        if (!isGlass(p) && !isShellTint(p)) {
                            if (runStart < 0) runStart = x
                        } else if (runStart >= 0) {
                            if (x - runStart >= MIN_RUN_PX) runs.add(intArrayOf(runStart, x - 1))
                            runStart = -1
                        }
                    }
                    if (runStart >= 0 && glassMax - runStart >= MIN_RUN_PX) runs.add(intArrayOf(runStart, glassMax))
                    // **A person is at least two units of car wide; a seam between two pieces of
                    // car is a pixel or two.** v5.6F: the pillar that «Ritaglio» leaves between
                    // the two panes meets the glass's shadow band in a two-pixel blend which is
                    // neither glass, nor the shell's tint, nor anybody -- and a
                    // complement-of-glass scan counted it as a third occupant. Taking the
                    // complement is still right, because the palette misses the brown hair and
                    // every anti-aliased fringe; what it must not do is *invent* a person out of
                    // the seam where two pieces of car meet. Measured on this build at both
                    // lanes: the phantom run is 0.4 units wide and the narrowest real head run in
                    // the scanned band is 13.
                    val minRunUnits = 2f * px
                    runs.retainAll { r -> (r[1] - r[0] + 1) >= minRunUnits }
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
                    // By construction of the runs, everything between them is glass or the
                    // pillar's own paper -- either way it is the car and not a person.
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
            // **2.5 px of measurement slop plus the cut edge's own wobble**, which is v5.6F's
            // addition and not a loosening. `unitsTall` is the height the size table *governs* --
            // roof line to wheel contact -- and «Ritaglio» cuts both of those edges with a
            // per-vertex wobble of up to 0.6 units, so the drawn extent legitimately overshoots
            // the governed one by up to that much at each end. Measured on this build: the estate
            // on the far lane reads 138 px against a governed 135.0, which is 1.2 units of
            // scissor-cut and anti-aliased rim. Expressed as the wobble rather than as a bigger
            // number, so a body redrawn with a steadier hand tightens this by itself.
            val wobble = CAR_CUT_EDGE_WOBBLE_UNITS * unitPx(lane, CarType.PLAIN)
            assertEquals(
                "a plain $shell on lane $lane measured $height px",
                predicted,
                height,
                2.5f + wobble,
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
         * **The visible skin these tests used to measure, and why the numbers are only a comment.**
         *
         * The four adult drawings show, crown of the hairline to chin, 62 and 43 rows of skin
         * walking and 77 and 74 seated -- and the head each of them sits on is the same size in
         * all four. As a share of the head block that is 0.861, 0.623, 0.794 and 0.733: a third of
         * the quantity is haircut. Two of these tests were built on it, and B "Rilievo" is where
         * that stopped working; they measure the head block now (see [headBlock]). The numbers are
         * kept here because they are the reason, and `OccupantHeadFitTest` re-measures the artwork
         * itself so nothing here has to hold a copy of it.
         */

        /**
         * The skin an occupant can be drawn in.
         *
         * **Read out of `PeopleColours` since v4.30 rather than copied off the artwork.** It used
         * to be four colours because each family was painted in its own and the tone copies moved
         * that one colour; the tone is now a number the engine multiplies a weight mask by, so the
         * set of skins a frame can contain is exactly the set `PeopleColours` deals from. A copy of
         * a palette is the thing this class has already been caught holding -- see
         * [OCCUPANT_EXTRA_COLOURS] -- and this is the version of it that cannot go stale.
         */
        val SKIN_TONES = PeopleColours.SKIN.map { channelsOf(it) }

        /**
         * The occupants' non-skin colours, for the scan that checks nothing of an occupant is
         * painted outside its own pane.
         *
         * **This was a hand-written copy of the artwork's palette, and it had been wrong twice.**
         * v4.25 re-measured it on the shipped PNGs and found two of its seven entries matched
         * nothing at all: the woman's hair had been recorded as `F7CE64`, which is really her
         * hairband, and the boy's shirt as `6BA84F`. Neither was a *missing* pixel to the scan --
         * it was a pixel the scan could not see, which makes a gate quietly weaker rather than
         * noisily wrong. Between them they covered 113 621 pixels of the shipped summer people.
         *
         * v4.30 removed the copy. The head and the garments are dealt from `PeopleColours`, so the
         * colours a frame can contain are that object's own palettes -- and the one colour that is
         * still painted rather than dealt, the seatbelt, is named here because it is the only one.
         */
        //
        // **Deduplicated, because the palettes legitimately share colours.** A cap and a shirt can
        // both be the parasol's red, and a pair of trousers and the seatbelt can both be the
        // shipped charcoal; those are one colour the scene can put on an occupant, and listing it
        // twice would only make the scan do the same comparison twice. What the scan asks is "is
        // this pixel an occupant colour", never "which region is it".
        //
        // **And there is no hand-written entry left.** The seventh entry of the old list was
        // `#EFDFC4` and was labelled "the seatbelt (rc4 frontal busts)"; the seatbelt is drawn
        // `#3A3F4A` and always has been -- `#EFDFC4` is the man's summer trousers. That is the
        // *third* wrong entry this list has been caught with: v4.25 re-measured it and found two
        // (the woman's hair and the boy's shirt), and this one survived that pass because a wrong
        // colour is invisible to a scan rather than noisy. The list is derived now, and the one
        // colour that is painted rather than dealt turns out to be one the outfits already deal.
        val OCCUPANT_EXTRA_COLOURS =
            (PeopleColours.HAIR + PeopleColours.CAP + PeopleColours.OUTFITS)
                .distinct().map { channelsOf(it) }

        /** The seatbelt, painted into every seated bust's fixed art by the generator. */
        const val SEATBELT_COLOUR = 0xFF3A3F4A.toInt()

        /** `[r, g, b]` of a packed colour, which is the shape every scan in this class wants. */
        fun channelsOf(argb: Int): IntArray =
            intArrayOf((argb shr 16) and 0xFF, (argb shr 8) and 0xFF, argb and 0xFF)

        /** `car_window`'s glass, which nothing else in the scene is painted in. */
        val GLASS = intArrayOf(185, 216, 228)

        /**
         * The shadow the body paper's cut edge casts on the glass behind it: `mix(GLASS, ink,
         * 0.34)`, the project's one `RELIEF_T`. Measured on the shipped frame at (137, 157, 168),
         * which is what `#B9D8E4` blended 34 % towards `#2B2A33` comes to.
         */
        val GLASS_SHADE = intArrayOf(137, 157, 168)

        /**
         * The per-vertex wobble «Ritaglio» cuts every paper edge of a car with: 0.6 units of
         * **car**, the value
         * `genera_mezzi.py` authored the fleet at and the same order as the neighbourhood's own
         * 0.7. It is what makes a scissor cut a scissor cut, and it is why a drawn extent is not
         * exactly the extent the size table governs.
         */
        const val CAR_CUT_EDGE_WOBBLE_UNITS = 0.6f

        /** Small enough to keep a face, large enough to drop an anti-aliased speck. */
        const val MIN_BLOB_AREA = 40

        /**
         * How much taller than its own face a head block may be before the figure is treated as
         * overlapped rather than measured.
         *
         * Measured on this frame: a pedestrian standing clear needs 0.6 to 0.8 of a face above it
         * to reach the crown, and the two that stand in front of somebody else need 3.2 -- they
         * walk into the head behind. Anywhere in between separates them; 1.5 is the middle.
         */
        const val CROWN_WALK_CAP = 1.5f

        /**
         * How far a colour must move to count as ink rather than as a rasteriser's edge.
         *
         * The pane's own anti-aliased rim and the soft edge of a drop shadow sit within a few
         * levels of the glass, and counting them put a driver's crown two rows above the hair.
         * 40 of 255 is past every such fringe and far below any colour the artwork actually
         * paints against either background.
         */
        const val INK_DISTANCE = 40

        /** rc5's criterion, unmoved: the heads fill half the glass at the head band... */
        const val BAND_FILL_FLOOR = 0.50f

        /** ...and half of it averaged over the head's own rows. */
        const val MEAN_FILL_FLOOR = 0.50f

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
         * B-pillar.
         *
         * **v5.6F: both numbers come off the shell, and neither is the sprite's.** «Ritaglio» puts
         * the glass behind the body paper and cuts the panes out of it, so `car_window_*` is a
         * unit wider than the hole on every side and the estate's exclusion stopped being the
         * only reason these two differed. [CarShell.paneXUnits] and [CarShell.paneWidthUnits] are
         * the hole an occupant is seen through, which is what every criterion below means by the
         * pane; `ESTATE_CABIN_PANE_WIDTH_UNITS` moved into that declaration with them.
         */
        fun cabinPane(shell: CarShell): Pair<Float, Float> {
            val left = shell.paneXUnits
            return left to left + shell.paneWidthUnits
        }

        /**
         * The whole glass pane a body's occupants sit behind — left, right, top, bottom — in that
         * body's own local units.
         *
         * **This is `cabinPane` plus the two vertical edges, and it exists as a function on
         * purpose.** The four numbers used to be computed inline inside
         * `noOccupantPixelLeavesTheGlass`, whose body is large enough that the BV6600's runtime
         * evaluated the two vertical ones as `0.0` while the constants they are assigned from read
         * -16.0 and 9.0 correctly on the same line — a degenerate, zero-height pane that counted
         * every occupant pixel as being outside the glass. The arithmetic is unchanged, every
         * constant is the same one, and the branch is the same branch; only where it is evaluated
         * moved. See `BACKLOG_v4_22.md` item 35.
         */
        fun glassPane(type: CarType, shell: CarShell?): FloatArray {
            val isTruck = type == CarType.FIRE_TRUCK
            val (left, right) = if (isTruck) TRUCK_PANE else cabinPane(shell!!)
            val top = if (isTruck) {
                SceneObjectRenderer.FIRE_TRUCK_SILL_Y_UNITS -
                    SceneObjectRenderer.FIRE_TRUCK_GLASS_HEIGHT_UNITS
            } else {
                SceneObjectRenderer.CAR_GLASS_TOP_Y_UNITS
            }
            val bottom = if (isTruck) {
                SceneObjectRenderer.FIRE_TRUCK_SILL_Y_UNITS
            } else {
                SceneObjectRenderer.CAR_SILL_Y_UNITS
            }
            return floatArrayOf(left, right, top, bottom)
        }

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
