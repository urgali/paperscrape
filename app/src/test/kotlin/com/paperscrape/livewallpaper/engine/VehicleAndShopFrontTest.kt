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
            // v5.6F: the three «Ritaglio» bodies, and the criteria sweep was re-run on them --
            // see `proposte_v5_6b/registri/vano_vetro/` for the nine readings it produced.
            "car_body_compact.png" to "f69bfd5730502ebee0a4db404defe7af0d4071051b31855d5eeda12c4b741382",
            "car_body_saloon.png" to "c3f96f055e13f74bb964bdb0dd4dee6bfb1935436722cef32ebbb0e587b35916",
            "car_body_estate.png" to "7a30fe5464262eb2493bd08740c183a2d9a5b8f1923234a35f85385c759916f9",
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
     * **The body is one sheet of paper, and the only thing baked into it is the wheels' shadow.**
     *
     * This method asserted the opposite shape of drawing until v5.6F: that each shell carried a
     * near-white *lamp housing* at each end and nothing lamp-sized in the middle, which was how a
     * v4.19 body said where its lenses went. «Ritaglio» has no housings -- the lens is a card
     * glued on the corner -- and no panels, no door lines, no beltline spear and no sill band: at
     * the scale a car is drawn on this road none of them is a pixel wide, and they are most of
     * what the redraw removed.
     *
     * So the property is now the absence, stated as something that can fail: the shell is white
     * from end to end except for the crescent each disc casts on it, and **every** non-white
     * opaque pixel has to be within a wheel's own reach. Measured on the shipped drawings: 226 to
     * 314 such pixels per body, 100 % of them inside 13 units of a wheel centre or of that
     * wheel's shadow centre, against a mean grey of 254 over the whole sheet. A panel line
     * creeping back in fails here rather than costing bytes nobody sees.
     */
    @Test
    fun `the body is one sheet whose only baked shading is its wheels' shadow`() {
        val px = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        // The disc's own radius plus the shadow paper's offset: the crescent cannot reach further
        // than the disc it is cast by, displaced.
        val reach = SceneObjectRenderer.CAR_WHEEL_RADIUS_UNITS + 1f
        for (shell in CarShell.entries) {
            val image = ImageIO.read(File(drawableDir(), spriteFileName(shell.bodyRes)))
            var shaded = 0
            var strays = 0
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    val argb = image.getRGB(x, y)
                    if ((argb ushr 24) < 200) continue
                    val r = (argb shr 16) and 0xFF
                    val g = (argb shr 8) and 0xFF
                    val b = argb and 0xFF
                    if (r >= 248 && g >= 248 && b >= 248) continue
                    shaded++
                    val lx = shell.bodyXUnits + x / px
                    val ly = shell.bodyYUnits + y / px
                    val near = listOf(shell.wheelFrontXUnits, shell.wheelRearXUnits).any { wx ->
                        // The disc's centre, and the same centre displaced by the shadow paper.
                        val cy = SceneObjectRenderer.VEHICLE_GROUND_Y_UNITS -
                            SceneObjectRenderer.CAR_WHEEL_RADIUS_UNITS
                        kotlin.math.hypot((lx - wx).toDouble(), (ly - cy).toDouble()) <= reach ||
                            kotlin.math.hypot((lx - wx - 2f).toDouble(), (ly - cy - 2.5f).toDouble()) <= reach
                    }
                    if (!near) strays++
                }
            }
            assertTrue("$shell: the wheels' shadow must actually be baked in, found $shaded px", shaded > 100)
            assertEquals(
                "$shell: $strays shaded pixels are nowhere near a wheel -- the sheet has grown " +
                    "detail the road cannot resolve",
                0, strays,
            )
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
        // v5.6F: the cab window is a **hole in the red paper**, not a pane painted on top of
        // it, so it is the second subpath of the body's own even-odd outline. Reading the glass
        // sheet behind it instead would be half a unit out on every side, because the sheet is
        // grown so no seam can open along the cut -- the same construction the cars use.
        val svg = svgSource("firetruck_body.svg").readText()
        val outline = Regex("""<path fill-rule="evenodd" d="([^"]+)" fill="#D6362E"/>""").find(svg)
        assertTrue("the appliance's outline must be in the drawing", outline != null)
        val subpaths = outline!!.groupValues[1].split("M").filter { it.isNotBlank() }
        assertEquals("the outline must be a shell with one hole in it", 2, subpaths.size)
        val g = Regex("""(-?[\d.]+) (-?[\d.]+)""").findAll(subpaths[1])
            .flatMap { m -> sequenceOf(m.groupValues[1].toFloat(), m.groupValues[2].toFloat()) }
            .toList()
        assertTrue("the cab window path must be in the drawing", g.size >= 8)
        val cabYs = g.filterIndexed { i, _ -> i % 2 == 1 }
        val sill = cabYs.max()
        val top = cabYs.min()
        assertEquals(
            "the sill is the pane's bottom edge",
            sill, SceneObjectRenderer.FIRE_TRUCK_SILL_Y_UNITS, 0.001f,
        )
        assertEquals(
            "the pane's own height is the glass height the bust is scaled against",
            sill - top, SceneObjectRenderer.FIRE_TRUCK_GLASS_HEIGHT_UNITS, 0.001f,
        )
        val cabXs = g.filterIndexed { i, _ -> i % 2 == 0 }
        val cabLeft = cabXs.min()
        val cabRight = cabXs.max()
        assertTrue(
            "the driver must sit inside the pane, forward of its centre",
            SceneObjectRenderer.FIRE_TRUCK_HEAD_X_UNITS in cabLeft..cabRight &&
                SceneObjectRenderer.FIRE_TRUCK_HEAD_X_UNITS < (cabLeft + cabRight) / 2f,
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
                "drawWheel(canvas, FIRE_TRUCK_INNER_WHEEL_X_UNITS, wheelY, wheelRadius, hubRadius)",
            ),
        )
        val spacing = SceneObjectRenderer.FIRE_TRUCK_WHEEL_X_UNITS -
            SceneObjectRenderer.FIRE_TRUCK_INNER_WHEEL_X_UNITS
        val diameter = 2f * SceneObjectRenderer.FIRE_TRUCK_WHEEL_RADIUS_UNITS
        // **v5.6F: 1.12 diameters, not 1.15, and the floor moved on a measurement.** The fleet's
        // tyres grew half a unit with the «Ritaglio» redraw while the axle centres stayed where
        // `firetruck_body.svg` bakes the wheels' own shadows, so the ratio fell from 1.175 to
        // 1.121. A ratio copied from real lorries is a proxy anyway; what it stands for is the
        // daylight, and that is asserted twice below -- in units, and in the pixels the daylight
        // is actually worth in the near lane, which is the reading that decides whether the twin
        // axle is visible at all.
        assertTrue(
            "centre spacing ${spacing}u must be at least 1.12 diameters (${1.12f * diameter}u)",
            spacing >= 1.12f * diameter,
        )
        assertTrue(
            "and the tyres must show daylight: gap ${(spacing - diameter)}u",
            spacing - diameter >= 2f,
        )
        val nearLanePx = (spacing - diameter) * SceneSpace.FIRE_TRUCK_BASE_SCALE *
            SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION) *
            SceneSpace.sceneScale(1440f)
        assertTrue(
            "and it must survive the reduction: ${nearLanePx}px of daylight in the near lane",
            nearLanePx >= 2f,
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
     * the wheels drawn under it: a slab on two discs. v4.19 cut two holes in the paper and stood
     * the tyres in them.
     *
     * **v5.6F cut the holes back out, and this test is the other half of that sentence.** In
     * «Ritaglio» a wheel is a whole disc of card glued *in front of* the body paper, the way the
     * reference photograph builds one, so a hole would show road through the middle of the car
     * wherever the disc did not cover it. The floor line therefore has to be **unbroken from nose
     * to tail** -- which is a hole's exact negation and, like a hole, cannot be faked by shading.
     *
     * It is still the drawing that is measured and not a constant: the two tests this replaces
     * read the shipped PNG's own alpha, and so does this one.
     */
    @Test
    fun `no shell is cut away over a wheel, because the discs lie in front of it`() {
        for (shell in CarShell.entries) {
            val image = ImageIO.read(File(drawableDir(), spriteFileName(shell.bodyRes)))
            // One unit above the painted floor: inside the paper, clear of the antialiased rim.
            val row = ((25f - shell.bodyYUnits) * SpriteBlitter.SPRITE_PIXELS_PER_UNIT).toInt()
            val opaque = { x: Int -> (image.getRGB(x, row) ushr 24) >= 200 }
            val runs = mutableListOf<Pair<Int, Int>>()
            var x = 0
            while (x < image.width) {
                if (!opaque(x)) { x++; continue }
                val start = x
                while (x < image.width && opaque(x)) x++
                runs.add(start to x - 1)
            }
            assertEquals(
                "$shell: the floor line must be one unbroken run of paper, found $runs",
                1, runs.size,
            )
            assertTrue(
                "$shell: and it must be the whole car, not a stub",
                (runs[0].second - runs[0].first) / SpriteBlitter.SPRITE_PIXELS_PER_UNIT >
                    shell.lengthUnits * 0.9f,
            )
        }
    }

    /**
     * Each wheel is a **whole disc standing on the road**, with about 45 % of it below the paper.
     *
     * That share is the silhouette difference between v5.5's wheel and this one, and it is what
     * the reference photograph's wheels do: a complete circle whose centre sits on the body's own
     * floor line, so half of it hangs below the car. Measured at 0.37-0.50 on six photographed
     * cars; the shipped drawing has to land in that band rather than near either end of it.
     *
     * The floor is read off the **drawing**, at the wheel's own column, so a redraw that raised
     * or dropped the body's bottom edge is caught here rather than in a constant that would have
     * gone on agreeing with itself.
     */
    @Test
    fun `each wheel is a whole disc with its lower half below the shell`() {
        for (shell in CarShell.entries) {
            val image = ImageIO.read(File(drawableDir(), spriteFileName(shell.bodyRes)))
            val px = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
            val radius = SceneObjectRenderer.CAR_WHEEL_RADIUS_UNITS
            assertEquals(
                "$shell: whatever its radius, a wheel stands on the road",
                SceneObjectRenderer.VEHICLE_GROUND_Y_UNITS,
                (SceneObjectRenderer.VEHICLE_GROUND_Y_UNITS - radius) + radius,
                0.001f,
            )
            for (wx in listOf(shell.wheelFrontXUnits, shell.wheelRearXUnits)) {
                val cx = ((wx - shell.bodyXUnits) * px).toInt().coerceIn(0, image.width - 1)
                val floorPx = (image.height - 1 downTo 0)
                    .first { (image.getRGB(cx, it) ushr 24) >= 200 }
                val floor = shell.bodyYUnits + (floorPx + 1) / px
                val below = (SceneObjectRenderer.VEHICLE_GROUND_Y_UNITS - floor) / (2f * radius)
                assertTrue(
                    "$shell: the disc at $wx hangs $below of its diameter below the paper's floor " +
                        "at $floor, wanted 0.37..0.50",
                    below in 0.37f..0.50f,
                )
            }
        }
    }

    /**
     * The livery band lies on paper for its whole width.
     *
     * `police_stripe` and `taxi_checker` are blitted onto the shell, and a band running past the
     * paper would hang over the road. It was the two arch holes that could swallow it until v5.6F
     * and it is the chamfered nose and tail that can now -- the band is 40 units on a compact
     * whose floor is 92 -- so the measurement is unchanged and only what it guards against has
     * moved. Checked on the two bodies that actually wear a livery, the saloon (police) and the
     * compact (taxi), because those are the only door lines the two sprites have to fit.
     */
    @Test
    fun `the livery band lies on the shell for its whole width`() {
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
            assertEquals("$shell glass x", glass[0], shell.glassSpriteXUnits, 0.001f)
            assertEquals(
                "$shell glass y", glass[1],
                SceneObjectRenderer.CAR_GLASS_SPRITE_Y_UNITS, 0.001f,
            )
            // And the sprite really is the sheet behind the paper, not the pane: a full unit
            // wider than the hole on each side, which is the half unit the sheet is grown by plus
            // the half unit of canvas padding. If a redraw ever made them equal again the glass
            // would stop covering the cut edge and a seam would open along it.
            assertEquals(
                "$shell: the glass sheet starts a unit outside the pane",
                shell.paneXUnits - 1f, shell.glassSpriteXUnits, 0.001f,
            )
        }
    }

    /**
     * The lamp lenses are cards glued on the corners, mostly on the paper and slightly proud.
     *
     * v4.19 shares one amber sprite and one red sprite across three bodies and the fire engine,
     * so registration is not a property of one file pair: each body says where the lenses go, and
     * a lens in the wrong place would sit in the middle of a door. Measured on the shipped pixels
     * rather than on the numbers, so a redraw of either part is caught.
     *
     * **v5.6F: "entirely on painted shell" became "mostly on it, and reaching the corner".** The
     * shells bake no housing any more, and «Ritaglio» sets each lens half a unit past the nose or
     * the tail so it reads as the lamp *on* the corner rather than as a sticker behind it --
     * measured, 14 % to 33 % of a lens's ink is over the chamfer and off the paper, which at 0.88
     * px per unit in the near lane is the corner pixel and not a floating lamp. What must still
     * hold is that the lens is a lamp and not a decal: most of it on the paper, and its inner edge
     * well inside the body.
     */
    @Test
    fun `both lamp lenses are glued on the corner the body ends at`() {
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
                var ink = 0
                var outside = 0
                var innerEdgeOnShell = true
                for (y in 0 until lens.height) {
                    for (x in 0 until lens.width) {
                        if ((lens.getRGB(x, y) ushr 24) < 128) continue
                        ink++
                        val bx = x0 + x
                        val by = y0 + y
                        val onShell = bx in 0 until body.width && by in 0 until body.height &&
                            (body.getRGB(bx, by) ushr 24) >= 200
                        if (!onShell) {
                            outside++
                            // The half of the lens that faces into the car must be on paper: a
                            // lens hanging off there would be a lamp in mid-air rather than a
                            // lamp on a corner.
                            val facesInward = if (ox < 0f) x >= lens.width / 2 else x < lens.width / 2
                            if (facesInward) innerEdgeOnShell = false
                        }
                    }
                }
                assertTrue(
                    "$shell: ${outside * 100 / ink}% of a lens is off the paper -- a lamp is a card " +
                        "on the corner, not one beside the car",
                    outside * 2 < ink,
                )
                assertTrue("$shell: the lens's inner half must lie on the paper", innerEdgeOnShell)
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
        // **Within one unit of the highest row, not exactly on it.** A «Ritaglio» roof is a cut
        // edge: it carries a per-vertex wobble and the saloon's own roof line falls 0.34 units
        // from front to back, so the single topmost *row* is reached by one end of the run and
        // not by the other -- measured, the saloon's exact-row reading is -10.3..24.3 for a roof
        // the drawing runs -20.4..23.6. One unit is the wobble's own amplitude, and reading the
        // run at that tolerance gives -21.3..26.7, which contains the declaration.
        val tolerance = SpriteBlitter.SPRITE_PIXELS_PER_UNIT.toInt()
        val columns = tops.indices.filter { tops[it] <= highest + tolerance }
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
