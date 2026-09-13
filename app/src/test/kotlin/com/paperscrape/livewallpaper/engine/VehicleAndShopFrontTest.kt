package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The artistic pass's invariants, each tied to the thing it is a statement about.
 *
 * Every one of the defects this release fixes was a number that had drifted away from the drawing
 * it described: a shadow drawn 37 units above the road because the vehicle frame's origin is not
 * the vehicle's ground, a light bar centred nine units off a roof whose span nobody had measured,
 * an awning blitted before the pane it shades. Freezing the *look* would be worthless -- the look
 * is a judgement and it is allowed to change. What these pin is the relationship: where the roof
 * is, the accessory goes; where the glass is, the canopy goes above it; what the cab window
 * measures, the occupant constants say.
 *
 * So the artwork is read (the SVG sources and the shipped PNGs) rather than restated, and the call
 * sites are read out of the renderer rather than duplicated as a list here.
 */
class VehicleAndShopFrontTest {

    // ---------------------------------------------------------------- vehicles

    /**
     * The wheels touch the road at [SceneObjectRenderer.VEHICLE_GROUND_Y_UNITS], and the shadow is
     * drawn there.
     *
     * The constant is only trustworthy if it is the wheels' own number, so that is derived from
     * the wheel call rather than asserted: centre 28 plus radius 9. The shadow's placement is read
     * off the source because *where the call sits* is the whole property -- `drawGroundShadow`
     * draws on the origin, and in `drawCar` the origin is the beltline.
     */
    @Test
    fun `a vehicle's shadow is drawn on the road and not at its beltline`() {
        val body = drawCarSource()
        // The wheel is now drawn from a radius and a centre that the ground line itself defines,
        // which is the property: whatever radius a family's wheel has, its bottom is the road.
        assertTrue(
            "the wheel centre must be derived from the ground line and the radius",
            body.contains("val wheelY = VEHICLE_GROUND_Y_UNITS - wheelRadius"),
        )
        for (radius in listOf(
            SceneObjectRenderer.CAR_WHEEL_RADIUS_UNITS,
            SceneObjectRenderer.FIRE_TRUCK_WHEEL_RADIUS_UNITS,
        )) {
            assertEquals(
                "a wheel of radius $radius must touch the road",
                SceneObjectRenderer.VEHICLE_GROUND_Y_UNITS,
                (SceneObjectRenderer.VEHICLE_GROUND_Y_UNITS - radius) + radius,
                0.001f,
            )
        }
        val lift = body.indexOf("canvas.translate(0f, -VEHICLE_GROUND_Y_UNITS)")
        val drop = body.indexOf("canvas.translate(0f, VEHICLE_GROUND_Y_UNITS)")
        val shadow = body.indexOf("drawGroundShadow(")
        assertTrue("drawCar must still lift the vehicle onto its own frame", lift >= 0)
        assertTrue("the shadow must be put back down on the road first", drop in (lift + 1) until shadow)
    }

    /**
     * The three bodies are the drawings the criteria were measured against.
     *
     * v4.19 replaced one saloon with three, and every occupant number in the pass -- pillar
     * light, head gap, pane fill, zero pixels outside the glass -- was measured on *these*
     * pixels. Pinning them by content hash is what makes those measurements mean something
     * later: a redraw that moves a pillar by two units would keep every constant valid and
     * every criterion stale.
     *
     * If this fails because a body was redrawn on purpose, re-hash it here in the same change
     * and re-run the criteria sweep -- that is the point of the test, not an obstacle to it.
     */
    @Test
    fun `the three bodies are the drawings the criteria were measured against`() {
        val expected = mapOf(
            "car_body_compact.png" to "e0a0bbfb6977e122ec8cd7f63c8acaadfaee988ca58d1f81f7b7d449ee28422f",
            "car_body_saloon.png" to "f7055bb05c5e528799c024ff60da00dc0978013cfae62a4eae819f9b3c426fd8",
            "car_body_estate.png" to "85050d39b6086e33912ec85c177324f09ba32fcd6c111d4d0014d9cc0a5fbbad",
        )
        for ((name, sha) in expected) {
            val bytes = File(drawableDir(), name).readBytes()
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(bytes)
            assertEquals("$name changed", sha, digest.joinToString("") { "%02x".format(it) })
        }
    }

    /**
     * The light bar stands on the roof `car_body` actually draws.
     *
     * The roof is measured rather than restated: the run of columns whose shell reaches the
     * drawing's highest row. That is the cabin top plus the shoulder pixels either side of it,
     * which is exactly the surface something can be mounted on, and it moves if the artwork's
     * cabin moves. The bar used to span -11..9 with its centre nine and a half units ahead of that
     * run's, eight units of it hanging over the windscreen.
     */
    @Test
    fun `the police light bar lies on the cabin roof`() {
        // A police car is always the saloon, so the bar is measured against that body's roof.
        val (front, rear) = carRoofSpanFromArtwork(CarShell.SALOON)
        assertTrue(
            "the declared roof front must be on the roof the artwork draws",
            CarShell.SALOON.roofFrontXUnits in front..rear,
        )
        assertTrue(
            "and so must the declared roof rear",
            CarShell.SALOON.roofRearXUnits in front..rear,
        )
        val left = SceneObjectRenderer.POLICE_LIGHTBAR_X_UNITS
        val right = left + SceneObjectRenderer.POLICE_LIGHTBAR_WIDTH_UNITS
        assertTrue("the bar must not overhang the windscreen", left >= front)
        assertTrue("the bar must not overhang the boot", right <= rear)
        assertEquals(
            "and it must sit centred on the roof rather than at one end",
            (front + rear) / 2f,
            (left + right) / 2f,
            1f,
        )
        assertTrue(
            "the call site must place it from the derived origin, not a literal",
            drawCarSource().contains("R.drawable.police_lightbar,\n                        POLICE_LIGHTBAR_X_UNITS"),
        )
    }

    /**
     * Every body carries a lamp seat at each end and none in the middle.
     *
     * A body is tinted by multiply, so a lamp housing is a near-white patch and nothing else in
     * a shell comes near white -- the panels are #ededed and #e6e6e6. v4.18's drawing carried
     * exactly one such patch and it was at mid-height over the rear wheel, which under a tint
     * reads as a blemish rather than as a light. Two patches, one in each end fifth, is the
     * property; their shape is not.
     */
    @Test
    fun `every body carries a lamp seat at each end and none in the middle`() {
        for (shell in CarShell.entries) {
            val image = ImageIO.read(File(drawableDir(), spriteFileName(shell.bodyRes)))
            var minX = image.width
            var maxX = 0
            var middle = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val argb = image.getRGB(x, y)
                    if ((argb ushr 24) < 200) continue
                    val r = (argb shr 16) and 0xFF
                    val g = (argb shr 8) and 0xFF
                    val b = argb and 0xFF
                    if (r < 248 || g < 248 || b < 248) continue
                    if (x < minX) minX = x
                    if (x > maxX) maxX = x
                    if (x > image.width * 0.25 && x < image.width * 0.75) middle++
                }
            }
            assertTrue("$shell: a lamp seat must sit in the front fifth", minX < image.width / 5)
            assertTrue("$shell: and one in the rear fifth", maxX > image.width * 4 / 5)
            // The chrome spear is white and runs the whole beltline, so the middle is not empty;
            // what must not be there is a *lamp-sized* white patch. One unit of spear is 3 px
            // tall, so anything under a few hundred pixels is the spear and nothing else.
            assertTrue("$shell: no lamp-sized white patch in the middle, found $middle px", middle < 900)
        }
    }

    /**
     * The appliance's cab window is the pane its occupant constants are measured from.
     *
     * The cab glass is painted into `firetruck_body` rather than blitted, so the coupling
     * between the drawing and [SceneObjectRenderer.FIRE_TRUCK_GLASS_HEIGHT_UNITS] has nothing
     * enforcing it but this. v4.19 stood the windscreen up and lengthened the cab so a
     * table-sized head could keep daylight to the A-pillar, which moved every number here --
     * so they are read back off the drawing rather than restated.
     */
    @Test
    fun `the appliance cab window is the pane the occupant constants are measured from`() {
        val svg = svgSource("firetruck_body.svg").readText()
        val glass = Regex("""<path d="M(-?[\d.]+) ([\d.-]+) L(-?[\d.]+) (-?[\d.]+) L(-?[\d.]+) (-?[\d.]+) L(-?[\d.]+) (-?[\d.]+) Z" fill="#B9D8E4"""")
            .find(svg)
        assertTrue("the cab window path must be in the drawing", glass != null)
        val g = glass!!.groupValues.drop(1).map { it.toFloat() }
        val sill = maxOf(g[1], g[3], g[5], g[7])
        val top = minOf(g[1], g[3], g[5], g[7])
        assertEquals(
            "the sill is the pane's bottom edge",
            sill, SceneObjectRenderer.FIRE_TRUCK_SILL_Y_UNITS, 0.001f,
        )
        assertEquals(
            "the pane's own height is the glass height the bust is scaled against",
            sill - top, SceneObjectRenderer.FIRE_TRUCK_GLASS_HEIGHT_UNITS, 0.001f,
        )
        val left = minOf(g[0], g[2], g[4], g[6])
        val right = maxOf(g[0], g[2], g[4], g[6])
        assertTrue(
            "the driver must sit inside the pane, forward of its centre",
            SceneObjectRenderer.FIRE_TRUCK_HEAD_X_UNITS in left..right &&
                SceneObjectRenderer.FIRE_TRUCK_HEAD_X_UNITS < (left + right) / 2f,
        )
    }

    /**
     * The ladder is carried on the body roof, and it is what sets the vehicle's height.
     *
     * Its lower rail has to land on the roof rather than hover above it -- that was the v4.18
     * defect this test was written for -- and its top has to be the tallest point, because
     * [SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL] is measured to it.
     */
    @Test
    fun `the ladder sits on the body roof and sets the vehicle's height`() {
        val ladder = ImageIO.read(File(drawableDir(), "firetruck_ladder.png"))
        val heightUnits = ladder.height / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        val bottom = SceneObjectRenderer.FIRE_TRUCK_LADDER_Y_UNITS + heightUnits
        assertEquals(
            "its lower rail lands on the body roof",
            SceneObjectRenderer.FIRE_TRUCK_BODY_Y_UNITS + 0.5f, bottom, 1.5f,
        )
        assertEquals(
            "and its top is the height the height table declares",
            SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL,
            SceneObjectRenderer.VEHICLE_GROUND_Y_UNITS - SceneObjectRenderer.FIRE_TRUCK_LADDER_Y_UNITS,
            0.001f,
        )
        val right = SceneObjectRenderer.FIRE_TRUCK_LADDER_X_UNITS +
            ladder.width / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        assertTrue(
            "nor reach past the widest point the vehicle declares, which is what the cull extent " +
                "and the A/B crop are both measured against",
            right <= SceneObjectRenderer.FIRE_TRUCK_HALF_WIDTH_UNITS,
        )
    }

    /**
     * The appliance rides a twin rear axle, with real daylight between the tyres.
     *
     * **Why the old version of this test passed with the wheels overlapped: it demanded the
     * overlap.** Its third assertion read `gap < 2 * radius` under the comment "they must
     * overlap into a bogie" -- the 40%-of-diameter overlap that made the inner wheel a crescent
     * with its hub eaten was the asserted design, not a case the test missed. rc2 reverses the
     * requirement to a real tandem's geometry: centre spacing at least 1.15 diameters, visible
     * gap between the tyres. The rendered-pixel half of the criterion lives in the instrumented
     * `TwinAxleSpacingTest`, which measures the drawn circles rather than the constants.
     */
    @Test
    fun `the appliance rides a twin rear axle spaced like a tandem`() {
        assertTrue(
            "the fire-truck branch must draw the inner rear wheel",
            drawCarSource().contains(
                "canvas.drawCircle(FIRE_TRUCK_INNER_WHEEL_X_UNITS, wheelY, wheelRadius, fillPaint)",
            ),
        )
        val spacing = SceneObjectRenderer.FIRE_TRUCK_WHEEL_X_UNITS -
            SceneObjectRenderer.FIRE_TRUCK_INNER_WHEEL_X_UNITS
        val diameter = 2f * SceneObjectRenderer.FIRE_TRUCK_WHEEL_RADIUS_UNITS
        assertTrue(
            "centre spacing ${spacing}u must be at least 1.15 diameters (${1.15f * diameter}u)",
            spacing >= 1.15f * diameter,
        )
        assertTrue(
            "and the tyres must show daylight: gap ${(spacing - diameter)}u",
            spacing - diameter >= 2f,
        )
    }

    /** The step that makes it a truck: the body stands proud of the cab. */
    @Test
    fun `the appliance's body roof stands above its cab roof`() {
        assertTrue(
            "a flat roof from nose to tail is a scaled-up car, which is what this was",
            SceneObjectRenderer.FIRE_TRUCK_BODY_Y_UNITS < SceneObjectRenderer.FIRE_TRUCK_CAB_ROOF_Y_UNITS - 2f,
        )
    }

    /**
     * The shell is cut away over each wheel, in the drawing and not only in paint.
     *
     * The saloon used to be a closed outline with a dead straight bottom edge for all 97 units and
     * the wheels drawn under it: a slab on two discs. The previous pass painted a darker ring on
     * that slab, which helped and was still paint. This reads the shipped PNG's bottom row: it has
     * to be missing over each wheel and present at both ends and in the middle, which is a hole and
     * cannot be faked by shading.
     */
    @Test
    fun `every shell is cut away over each wheel`() {
        for (shell in CarShell.entries) {
            val image = ImageIO.read(File(drawableDir(), spriteFileName(shell.bodyRes)))
            // One unit above the painted floor: inside the metal, clear of the half-unit of paper
            // rim below it whose antialiased edge is neither shell nor hole.
            val row = ((29f - shell.bodyYUnits) * SpriteBlitter.SPRITE_PIXELS_PER_UNIT).toInt()
            val opaque = { x: Int -> (image.getRGB(x, row) ushr 24) >= 200 }
            val toLocal = { px: Int ->
                shell.bodyXUnits + px / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
            }
            // The runs of missing shell along the floor line: two of them, one per wheel.
            val gaps = mutableListOf<Pair<Int, Int>>()
            var x = 0
            while (x < image.width) {
                if (opaque(x)) { x++; continue }
                val start = x
                while (x < image.width && !opaque(x)) x++
                if (x - start >= 3) gaps.add(start to x - 1)
            }
            assertEquals("$shell must be cut away over each wheel and nowhere else, found $gaps", 2, gaps.size)
            assertTrue("$shell: the nose must still reach the floor line", opaque(1))
            assertTrue("$shell: so must the tail", opaque(image.width - 2))
            assertTrue("$shell: and the sill between the wheels", opaque(image.width / 2))
            // And each cut must be centred on the wheel that sits in it, which is what stops the
            // wheels sliding back out to the corners the arches were cut to get them away from.
            val centres = gaps.map { (a, b) -> (toLocal(a) + toLocal(b + 1)) / 2f }.sorted()
            assertEquals(
                "$shell: the front arch is centred on the front wheel",
                shell.wheelFrontXUnits, centres[0], 1f,
            )
            assertEquals(
                "$shell: the rear arch is centred on the rear wheel",
                shell.wheelRearXUnits, centres[1], 1f,
            )
        }
    }

    /**
     * The arches are **concentric** with the tyres, with the same air all the way round.
     *
     * v4.18 cut its arches as a chord-and-arc that closed over the top of the wheel, so the gap
     * that read as air at the sides vanished where it mattered and the tyre looked jammed under
     * the shell. Measuring at the widest row of the hole and at its crown is what tells the two
     * constructions apart: a concentric cut gives the same clearance at both.
     */
    @Test
    fun `each wheel arch keeps the same air all the way round its tyre`() {
        for (shell in CarShell.entries) {
            val image = ImageIO.read(File(drawableDir(), spriteFileName(shell.bodyRes)))
            val opaque = { x: Int, y: Int ->
                x in 0 until image.width && y in 0 until image.height &&
                    (image.getRGB(x, y) ushr 24) >= 200
            }
            val px = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
            for (wx in listOf(shell.wheelFrontXUnits, shell.wheelRearXUnits)) {
                // Straight up from the wheel centre: the first opaque row is the arch crown.
                val cx = ((wx - shell.bodyXUnits) * px).toInt()
                val cy = ((SceneObjectRenderer.VEHICLE_GROUND_Y_UNITS -
                    SceneObjectRenderer.CAR_WHEEL_RADIUS_UNITS - shell.bodyYUnits) * px).toInt()
                val crownPx = (cy downTo 0).first { opaque(cx, it) }
                val crownAir = (cy - crownPx) / px - SceneObjectRenderer.CAR_WHEEL_RADIUS_UNITS
                assertEquals(
                    "$shell: the arch over the wheel at $wx must clear the tyre by the declared air",
                    SceneObjectRenderer.WHEEL_ARCH_AIR_UNITS, crownAir, 0.5f,
                )
            }
        }
    }

    /**
     * The livery band never overhangs a wheel arch.
     *
     * `police_stripe` and `taxi_checker` are blitted over a shell that has two holes cut in it,
     * so a band wider than the run between the arches would hang over the road. v4.19 checks it
     * on the two bodies that actually wear a livery -- the saloon (police) and the compact
     * (taxi) -- because those are the only door lines the two sprites have to fit.
     */
    @Test
    fun `the livery band never overhangs a wheel arch`() {
        val bandHeight = ImageIO.read(File(drawableDir(), "police_stripe.png")).height /
            SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        for (shell in listOf(CarShell.SALOON, CarShell.COMPACT)) {
            val image = ImageIO.read(File(drawableDir(), spriteFileName(shell.bodyRes)))
            val px = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
            val left = SceneObjectRenderer.CAR_LIVERY_X_UNITS
            val right = left + SceneObjectRenderer.CAR_LIVERY_WIDTH_UNITS
            val row = (((SceneObjectRenderer.CAR_SILL_Y_UNITS + bandHeight) - shell.bodyYUnits) * px)
                .toInt().coerceIn(0, image.height - 1)
            var x = ((left - shell.bodyXUnits) * px).toInt()
            val end = ((right - shell.bodyXUnits) * px).toInt()
            while (x <= end) {
                assertTrue(
                    "$shell: the shell is missing under the livery band at column $x",
                    (image.getRGB(x.coerceIn(0, image.width - 1), row) ushr 24) >= 200,
                )
                x++
            }
        }
    }

    /** The band is also the width of the doors rather than the width of the car. */
    @Test
    fun `the livery band is the door run, not the whole flank`() {
        val shipped = ImageIO.read(File(drawableDir(), "police_stripe.png")).width /
            SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        assertEquals(
            "the drawing and the constant are the same band",
            SceneObjectRenderer.CAR_LIVERY_WIDTH_UNITS, shipped, 0.001f,
        )
        assertEquals(
            "and the chequer is the same band as the stripe",
            shipped,
            ImageIO.read(File(drawableDir(), "taxi_checker.png")).width / SpriteBlitter.SPRITE_PIXELS_PER_UNIT,
            0.001f,
        )
    }

    /**
     * The taxi has a roof sign, and it stands on the roof rather than over the windscreen.
     *
     * Same rule the light bar is held to, measured off the same artwork, because it is the same
     * roof. Without it a taxi is a yellow car with a chequered band, which at a hundred and forty
     * pixels is a yellow car.
     */
    @Test
    fun `the taxi carries a roof sign centred on the roof`() {
        assertTrue(
            "the taxi branch must blit the sign",
            drawCarSource().contains("R.drawable.taxi_sign, TAXI_SIGN_X_UNITS, TAXI_SIGN_Y_UNITS"),
        )
        // A taxi is always the compact, so the sign is measured against that body's roof.
        val (front, rear) = carRoofSpanFromArtwork(CarShell.COMPACT)
        val left = SceneObjectRenderer.TAXI_SIGN_X_UNITS
        val right = left + SceneObjectRenderer.TAXI_SIGN_WIDTH_UNITS
        assertTrue("the sign must not overhang the windscreen", left >= front)
        assertTrue("nor the boot", right <= rear)
        assertEquals("and must sit centred", (front + rear) / 2f, (left + right) / 2f, 1f)
        assertEquals(
            "the declared width is the drawing's",
            SceneObjectRenderer.TAXI_SIGN_WIDTH_UNITS,
            ImageIO.read(File(drawableDir(), "taxi_sign.png")).width / SpriteBlitter.SPRITE_PIXELS_PER_UNIT,
            0.001f,
        )
    }

    /**
     * The lamps are dark by day, come up with the windows, and stop short of being a light source.
     *
     * The ramp is the windows' own, so a car lights up when a house does; the ceiling is below it,
     * so a lamp stays a lamp. Zero for the whole first third of the evening is what makes the
     * feature free at noon, and every call site is behind that zero.
     */
    @Test
    fun `vehicle lamps are dark by day and lit by night`() {
        val renderer = SceneObjectRenderer::class.java
        // The ramp is private, so it is exercised through the values it must produce.
        assertEquals("nothing at midday", 0, litVehicleAlphaAt(0f))
        assertEquals("still nothing in the early evening", 0, litVehicleAlphaAt(0.3f))
        assertTrue("coming up by mid-evening", litVehicleAlphaAt(0.6f) > 0)
        val full = litVehicleAlphaAt(1f)
        assertTrue("lit at night, $full", full in 150..220)
        assertTrue(
            "and never brighter than the windows behind it",
            full < litWindowAlphaAt(1f),
        )
        assertTrue(
            "a car takes the shared lamp pair too",
            drawCarSource().contains("drawVehicleLamps("),
        )
        assertTrue(
            "the appliance takes the same shared pair, so the gate is the shared one",
            drawSource("drawFireTruck").contains("drawVehicleLamps("),
        )
        assertTrue(
            "and that is where the gate lives",
            drawSource("drawVehicleLamps").contains("if (lit > 0)"),
        )
        assertTrue("renderer class resolved", renderer != null)
    }

    /**
     * The lit lamps land on the unlit patches, by arithmetic.
     *
     * `car_lights` is cropped to its two lamps, so it no longer shares the shell's canvas and the
     * blit origin is doing the registering. That origin is the difference between the two viewBoxes
     * and nothing else; if either drawing is re-cropped without the other, this fails instead of
     * the lamps sliding off the panels at night.
     */
    @Test
    fun `every shell is blitted at its own viewBox minimum`() {
        // Each drawing is authored in local scene coordinates, so its blit origin *is* its own
        // viewBox minimum. Asserting it per body is what keeps a re-crop of any one file from
        // sliding that car sideways while the other two stay put.
        for (shell in CarShell.entries) {
            val body = viewBox(svgSource(spriteFileName(shell.bodyRes).replace(".png", ".svg")).readText())
            assertEquals("$shell body x", body[0], shell.bodyXUnits, 0.001f)
            assertEquals("$shell body y", body[1], shell.bodyYUnits, 0.001f)
            val glass = viewBox(svgSource(spriteFileName(shell.glassRes).replace(".png", ".svg")).readText())
            assertEquals("$shell glass x", glass[0], shell.glassXUnits, 0.001f)
            assertEquals(
                "$shell glass y", glass[1],
                SceneObjectRenderer.CAR_GLASS_ORIGIN_Y_UNITS, 0.001f,
            )
        }
    }

    /**
     * The lamp lenses land inside the housing each body bakes for them.
     *
     * v4.19 shares one amber sprite and one red sprite across three bodies and the fire engine,
     * so registration is no longer a property of one file pair: each body says where the lenses
     * go, and a lens that missed its housing would sit on painted metal. Measured on the shipped
     * pixels rather than on the numbers, so a redraw of either part is caught.
     */
    @Test
    fun `both lamp lenses land on the housing every body bakes for them`() {
        val front = ImageIO.read(File(drawableDir(), "car_lamp_front.png"))
        val rear = ImageIO.read(File(drawableDir(), "car_lamp_rear.png"))
        val px = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        for (shell in CarShell.entries) {
            val body = ImageIO.read(File(drawableDir(), spriteFileName(shell.bodyRes)))
            for ((lens, ox, oy) in listOf(
                Triple(front, shell.lampFrontXUnits, shell.lampFrontYUnits),
                Triple(rear, shell.lampRearXUnits, shell.lampRearYUnits),
            )) {
                val x0 = ((ox - shell.bodyXUnits) * px).toInt()
                val y0 = ((oy - shell.bodyYUnits) * px).toInt()
                var outside = 0
                for (y in 0 until lens.height) {
                    for (x in 0 until lens.width) {
                        if ((lens.getRGB(x, y) ushr 24) < 128) continue
                        val bx = x0 + x
                        val by = y0 + y
                        val onShell = bx in 0 until body.width && by in 0 until body.height &&
                            (body.getRGB(bx, by) ushr 24) >= 200
                        if (!onShell) outside++
                    }
                }
                assertEquals("$shell: a lamp lens must sit entirely on painted shell", 0, outside)
            }
        }
    }

    // ---------------------------------------------------------------- shop fronts

    /**
     * ### What the five tests that stood here were protecting, and where each went
     *
     * They were written against the flat shopfronts of v4.18: a wall, a cornice, an awning, a sign
     * and a window, at literal offsets, read out of `drawRestaurantBuilding` and
     * `drawBarBuilding`. v5.0 replaced both with cut-out figures dealt from
     * [NeighbourhoodTable], so there are no offsets to read and no function to read them from.
     * Each concern is accounted for rather than dropped:
     *
     * - *"each shop is capped above its wall, and the two caps differ"* -- **kept, below**, and
     *   widened: the question was never the cornice, it was whether a shop is told apart from an
     *   unfinished rectangle by its outline, and that is now asked of the silhouettes themselves.
     * - *"the restaurant frontage stacks fascia, canopy, glass and door"* -- **gone with the
     *   drawing.** The pavilion is one cut-out card with its coping, dome sign, awning and steps
     *   drawn into it; there is no stacking order left to get wrong, because there are no longer
     *   four sprites to order.
     * - *"the pub front packs lantern, panes and door inside the painted field"* -- **moved** to
     *   `tools/assets/tests/test_neighbourhood.py`. That is the containment rule, and it is now
     *   enforced where the card is drawn: the generator refuses to render a card that leaves its
     *   host face, for every piece of every family rather than for the bar alone.
     * - *"both shop fronts have a lit upper storey and glazed street level"* -- **half kept**
     *   (below: both shops are glazed and their glass follows opening hours) and **half gone**:
     *   the shops no longer have an upper storey. That is the redraw, not an oversight; see
     *   `BuildingHeightDeclarationTest`, which measures how much shorter they are.
     * - *"the shop window's frame survives being tinted"* -- **structurally impossible to fail
     *   now**, which is why there is no successor. The frame was ink in the same sprite as the
     *   glass, so a tint multiplied both and could wash the frame out; the frame is now in the
     *   piece's fixed layer and the glass is a separate mask summed over it. The two cannot be
     *   multiplied by one colour any more.
     */
    @Test
    fun `the two shops are told apart from each other and from a plain rectangle`() {
        val figures = listOf("restaurant_pavilion_fx", "bar_signboard_fx", "bar_chamfer_fx")
        val shapes = figures.associateWith { ImageIO.read(File(drawableDir(), "$it.png")) }
        for ((name, image) in shapes) {
            // A shop that fills its own bounding box is the rectangle this test exists to refuse.
            var opaque = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if ((image.getRGB(x, y) ushr 24) >= 128) opaque++
                }
            }
            val fill = opaque.toFloat() / (image.width * image.height)
            assertTrue(
                "$name fills ${(fill * 100).toInt()}% of its canvas, so its outline is a rectangle",
                fill < 0.92f,
            )
        }
        // And no two of the three are the same drawing.
        for (a in figures.indices) {
            for (b in a + 1 until figures.size) {
                val x = shapes.getValue(figures[a])
                val y = shapes.getValue(figures[b])
                assertTrue(
                    "${figures[a]} and ${figures[b]} must not be the same drawing",
                    x.width != y.width || x.height != y.height,
                )
            }
        }
    }

    /**
     * Both shops are glazed at street level, and somebody can be behind the glass.
     *
     * The v4.1 defect was a restaurant with no occupant call site at all; the v4.18 one was a
     * frontage that read as a slab with a door in it. Both are the same question asked of the
     * pieces: does this figure have glass, and does it declare a window a bust fits in.
     */
    @Test
    fun `both shop figures are glazed and populatable`() {
        for (variant in listOf(SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.BAR)) {
            val family = NeighbourhoodTable.FAMILIES.getValue(variant)
            assertEquals(
                "a shop follows opening hours, so it must not be classed as a house",
                WindowBuildingKind.COMMERCIAL, family.kind,
            )
            for (slot in family.slots) {
                for (piece in slot.options) {
                    assertTrue(
                        "$variant has a figure with no glass mask: a slab with a door in it",
                        piece.parts.any { it.role == PartRole.GLASS_MASK },
                    )
                    assertTrue(
                        "$variant has a figure nobody can stand in",
                        piece.windows.isNotEmpty(),
                    )
                }
            }
        }
    }
    /**
     * The appliance carries three silver equipment lockers along its body.
     *
     * They are what stops the body reading as one flat red slab, and they are the detail that
     * says "appliance" rather than "van" at scene scale. Counted on the shipped pixels as runs
     * of cool grey along the locker row, so a redraw that dropped one, or filled the row solid,
     * fails here.
     */
    @Test
    fun `the appliance carries three equipment lockers along its body`() {
        val image = ImageIO.read(File(drawableDir(), "firetruck_body.png"))
        val px = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        // The locker row, in the body's own local units, read through its blit origin.
        // The locker row sits ABOVE the cream stripe: below it the wheel arches rise to y=13.3
        // and ate two of the three panels, which is what the v4.19 redraw moved them off.
        val row = ((4f - SceneObjectRenderer.FIRE_TRUCK_BODY_Y_UNITS) * px).toInt()
        var runs = 0
        var inRun = false
        for (x in 0 until image.width) {
            val argb = image.getRGB(x, row)
            val r = (argb shr 16) and 0xFF
            val g = (argb shr 8) and 0xFF
            val b = argb and 0xFF
            // The locker steel is #C9CDD2, a *cool* grey: b is a few points above r. The paper
            // rim is neutral (#dcdcdc), so requiring the blue lift is what tells them apart.
            val silver = (argb ushr 24) >= 200 && r in 185..215 && b > r + 4 && b - r < 20
            if (silver && !inRun) runs++
            inRun = silver
        }
        assertEquals("three lockers along the body, found $runs", 3, runs)
    }


    // ---------------------------------------------------------------- helpers

    /** `litVehicleAlpha` is private; this is its published behaviour, read off the source. */
    private fun litVehicleAlphaAt(nightGlow: Float): Int =
        (litWindowAlphaAt(nightGlow) * 0.8f).toInt()

    private fun litWindowAlphaAt(nightGlow: Float): Int =
        (255f * ((nightGlow - 0.35f) / 0.45f).coerceIn(0f, 1f)).toInt()

    private fun luminanceAt(image: java.awt.image.BufferedImage, x: Int, y: Int): Int {
        val argb = image.getRGB(x, y)
        return ((argb shr 16) and 0xFF) * 30 / 100 + ((argb shr 8) and 0xFF) * 59 / 100 + (argb and 0xFF) * 11 / 100
    }

    private fun spriteUnits(name: String): Pair<Float, Float> {
        val image = ImageIO.read(File(drawableDir(), "$name.png"))
        return image.width / SpriteBlitter.SPRITE_PIXELS_PER_UNIT to
            image.height / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
    }

    private fun contentTopUnits(image: java.awt.image.BufferedImage): Float {
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if ((image.getRGB(x, y) ushr 24) >= 8) return y / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
            }
        }
        error("empty sprite")
    }

    /** `viewBox="minX minY w h"`, which is what an SVG coordinate has to be measured against. */
    private fun viewBox(svg: String): FloatArray {
        val raw = Regex("""viewBox="([-\d. ]+)"""").find(svg)?.groupValues?.get(1)
            ?: error("no viewBox")
        return raw.trim().split(Regex("\\s+")).map { it.toFloat() }.toFloatArray()
    }

    /**
     * The cabin roof, measured off the shipped `car_body`: the columns whose shell reaches the
     * drawing's topmost row, in the local units the renderer places accessories in.
     */
    /**
     * The run of roof a given body actually draws: the columns whose shell reaches the drawing's
     * highest row. That is the cabin top plus the shoulder pixels either side of it, which is
     * exactly the surface something can be mounted on, and it moves if the artwork's cabin moves.
     *
     * The estate is excluded by its callers rather than by this helper: its highest row is the
     * roof rack, not the roof, and it carries no roof accessory anyway.
     */
    private fun carRoofSpanFromArtwork(shell: CarShell): Pair<Float, Float> {
        val image = ImageIO.read(File(drawableDir(), spriteFileName(shell.bodyRes)))
        val tops = IntArray(image.width) { x ->
            (0 until image.height).firstOrNull { (image.getRGB(x, it) ushr 24) >= 200 } ?: image.height
        }
        val highest = tops.min()
        val columns = tops.indices.filter { tops[it] == highest }
        val toLocal = { px: Int ->
            shell.bodyXUnits + px / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        }
        return toLocal(columns.first()) to toLocal(columns.last() + 1)
    }

    /** The three bodies' sprite files, by the resource each [CarShell] declares. */
    private fun spriteFileName(res: Int): String = when (res) {
        R.drawable.car_body_compact -> "car_body_compact.png"
        R.drawable.car_body_saloon -> "car_body_saloon.png"
        R.drawable.car_body_estate -> "car_body_estate.png"
        R.drawable.car_window_compact -> "car_window_compact.png"
        R.drawable.car_window_saloon -> "car_window_saloon.png"
        R.drawable.car_window_estate -> "car_window_estate.png"
        else -> error("no file mapped for resource $res")
    }

    private fun drawCarSource(): String = drawSource("drawCar")

    private fun drawSource(function: String): String =
        rendererSource().readText()
            .substringAfter("private fun $function(")
            .substringBefore("\n    private fun ")

    private fun rendererSource(): File = locate(
        "src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt",
        listOf("", "app/"),
    )

    private fun svgSource(name: String): File = locate("tools/assets/sources/svg/$name", listOf("", "../"))

    private fun drawableDir(): File = locate("src/main/res/drawable-nodpi", listOf("", "app/"))

    private fun locate(suffix: String, prefixes: List<String>): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in prefixes) {
                val candidate = File(dir, "$prefix$suffix")
                if (candidate.exists()) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate $suffix")
    }
}
