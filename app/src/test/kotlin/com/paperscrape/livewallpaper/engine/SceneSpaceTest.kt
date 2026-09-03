package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The invariants that make the scene's geometry one system rather than several.
 *
 * These are deliberately relations rather than magic numbers. Every value in [SceneSpace] is tuned
 * and will be tuned again; what must not change is that a person stays taller than a car, that the
 * object band stays clear of the road, and that the road, the pavement and the ground the houses
 * stand on keep coming from the same projection. A test that pinned the numbers themselves would
 * fail on every re-tune and would be ignored within two releases.
 */
class SceneSpaceTest {

    private val eps = 0.0005f

    // --- The projection ------------------------------------------------------------------

    @Test
    fun `perspective is exactly 1 at the reference line and grows toward the viewer`() {
        assertEquals(1f, SceneSpace.perspectiveScaleAt(SceneSpace.REFERENCE_Y_FRACTION), eps)
        assertTrue(
            "the far lane must read as smaller than the near one",
            SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_FAR_Y_FRACTION) <
                SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION),
        )
        assertTrue(
            "the pavement is behind the road, so smaller than it",
            SceneSpace.perspectiveScaleAt(SceneSpace.PAVEMENT_NEAR_Y_FRACTION) <
                SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION),
        )
        assertTrue(
            "the near pavement row must read larger than the far one",
            SceneSpace.perspectiveScaleAt(SceneSpace.PAVEMENT_NEAR_Y_FRACTION) >
                SceneSpace.perspectiveScaleAt(SceneSpace.PAVEMENT_FAR_Y_FRACTION),
        )
    }

    @Test
    fun `perspective is proportional to the distance below the horizon`() {
        // The whole model in one assertion: doubling an object's distance below the vanishing
        // line doubles how large it reads. If this stops holding, the ground plane has stopped
        // being a ground plane and each category will start needing its own correction again.
        val h = SceneSpace.HORIZON_Y_FRACTION
        val a = SceneSpace.perspectiveScaleAt(h + 0.05f)
        val b = SceneSpace.perspectiveScaleAt(h + 0.10f)
        assertEquals(2f, b / a, 0.001f)
    }

    @Test
    fun `nothing on the ground plane is ever drawn mirrored or inside out`() {
        // A y above the horizon is not on this plane at all. Clamping rather than returning a
        // negative keeps a caller's mistake visible as a missing object rather than as a sprite
        // flipped upside down.
        assertEquals(0f, SceneSpace.perspectiveScaleAt(SceneSpace.HORIZON_Y_FRACTION - 0.2f), eps)
        assertEquals(0f, SceneSpace.perspectiveScaleAt(SceneSpace.HORIZON_Y_FRACTION), eps)
    }

    @Test
    fun `the depth band recedes and covers a usable range`() {
        assertEquals(SceneSpace.OBJECT_BAND_TOP_Y_FRACTION, SceneSpace.groundYFraction(0f), eps)
        assertEquals(SceneSpace.OBJECT_BAND_BOTTOM_Y_FRACTION, SceneSpace.groundYFraction(1f), eps)
        assertEquals(SceneSpace.groundYFraction(0f), SceneSpace.groundYFraction(-3f), eps)
        assertEquals(SceneSpace.groundYFraction(1f), SceneSpace.groundYFraction(9f), eps)

        // The defect Group 4 exists to fix: the scene used to span 1.51x between its farthest and
        // nearest object, which is not enough for depth to read at all. Anything under 2x means
        // the band has collapsed again.
        val range = SceneSpace.depthScale(1f) / SceneSpace.depthScale(0f)
        assertTrue("depth range collapsed to ${range}x", range >= 2f)
    }

    @Test
    fun `the object band starts on ground the hill is guaranteed to cover`() {
        // Above this line the hill's own wavy top edge can leave open sky, and an object placed
        // there would be standing on nothing.
        assertEquals(SceneSpace.GROUND_SOLID_TOP_Y_FRACTION, SceneSpace.OBJECT_BAND_TOP_Y_FRACTION, eps)
        assertTrue(SceneSpace.GROUND_SOLID_TOP_Y_FRACTION > SceneSpace.HORIZON_Y_FRACTION)
    }

    // --- The road ------------------------------------------------------------------------

    @Test
    fun `pedestrians walk on the ground between the buildings and the road`() {
        // The scene's vertical order, stated once: buildings, then the strip people walk on, then
        // the road. This is also what replaced `ROAD_SAFE_DEPTH_MAX` -- that constant capped every
        // category's depth at a value re-derived by hand whenever the road moved, where the
        // ordering is now a property of the geometry and the depth range is free to use all of
        // itself.
        assertTrue(
            "an object at depth 1 would be painted over by the road",
            SceneSpace.groundYFraction(1f) < SceneSpace.roadTopYFraction(),
        )
        assertTrue(
            "the far pavement row must be in front of the nearest buildings",
            SceneSpace.PAVEMENT_FAR_Y_FRACTION > SceneSpace.OBJECT_BAND_BOTTOM_Y_FRACTION,
        )
        assertTrue(
            "the two pavement rows must be ordered",
            SceneSpace.PAVEMENT_NEAR_Y_FRACTION > SceneSpace.PAVEMENT_FAR_Y_FRACTION,
        )
        assertTrue(
            "pedestrians must walk behind the road, not on it",
            SceneSpace.PAVEMENT_NEAR_Y_FRACTION < SceneSpace.roadTopYFraction(),
        )
        assertTrue(
            "the road must stay clear of the bottom of the screen",
            SceneSpace.roadBottomYFraction() < 1f,
        )
    }

    @Test
    fun `a pedestrian standing on the near row clears the far lane's traffic`() {
        // **This used to assert that the two bands never overlap, on the premise that people are
        // drawn after the cars. That premise stopped being true in v4.6**, which moved `drawPeople`
        // ahead of the vehicle loop precisely so that a car -- always the nearer object -- paints
        // over a pedestrian. v4.19's taller bodies do now reach up past the near pavement line,
        // and that is correct occlusion rather than a defect: `PeopleTrafficDepthTest` measures
        // the overlap and pins the ordering that resolves it.
        //
        // What is still worth asserting here is the depth relation the ordering rests on: every
        // pavement row is *behind* every lane, so drawing people first can never put a figure in
        // front of traffic.
        val personTop = SceneSpace.PAVEMENT_NEAR_Y_FRACTION -
            SceneSpace.PERSON_SPRITE_UNITS_TALL * SceneSpace.PERSON_BASE_SCALE *
            SceneSpace.perspectiveScaleAt(SceneSpace.PAVEMENT_NEAR_Y_FRACTION) /
            SceneSpace.REFERENCE_SCREEN_HEIGHT_PX
        val carTop = SceneSpace.ROAD_LANE_FAR_Y_FRACTION -
            SceneSpace.CAR_SPRITE_UNITS_TALL * SceneSpace.CAR_BASE_SCALE *
            SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_FAR_Y_FRACTION) /
            SceneSpace.REFERENCE_SCREEN_HEIGHT_PX
        assertTrue(
            "the near pavement must stand behind the far lane, which is what makes drawing " +
                "people before traffic correct",
            SceneSpace.PAVEMENT_NEAR_Y_FRACTION < SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
        )
        assertTrue(
            "and a car must be tall enough to reach the pavement row it stands in front of, " +
                "otherwise the ordering would be untested by any real frame",
            carTop < SceneSpace.PAVEMENT_NEAR_Y_FRACTION,
        )
        assertTrue("sanity: the person is drawn above their own feet", personTop < SceneSpace.PAVEMENT_NEAR_Y_FRACTION)
    }

    @Test
    fun `the reference line is the projection's own, not a lane's`() {
        // It was defined as the near lane until v76.7, which meant moving the road one step down
        // rescaled every object in the scene: the denominator of the projection moved with it.
        // Nothing about a road's position should change how tall a house is.
        val houseAtDepthOne = SceneSpace.SceneVariant.HOUSE_SMALL.baseScale * SceneSpace.depthScale(1f)
        assertEquals(1f, SceneSpace.perspectiveScaleAt(SceneSpace.REFERENCE_Y_FRACTION), eps)
        assertTrue(
            "the reference line must not sit on a lane by definition",
            houseAtDepthOne > 0f,
        )
    }

    @Test
    fun `the painted road is symmetric about its centre line`() {
        val top = SceneSpace.roadTopYFraction()
        val bottom = SceneSpace.roadBottomYFraction()
        val laneMid = (SceneSpace.ROAD_LANE_FAR_Y_FRACTION + SceneSpace.ROAD_LANE_NEAR_Y_FRACTION) / 2f
        // The dashed line is drawn halfway between the lanes; if the strip's own midpoint were
        // somewhere else, the marking would divide nothing. One lane read as a road and the other
        // as a verge for exactly this reason before v76.4.
        assertEquals(laneMid, (top + bottom) / 2f, eps)
        assertTrue("the road must contain both lanes", top < SceneSpace.ROAD_LANE_FAR_Y_FRACTION)
        assertTrue("the road must contain both lanes", bottom > SceneSpace.ROAD_LANE_NEAR_Y_FRACTION)
    }

    @Test
    fun `a legacy lane pair still gets a road centred on its own traffic`() {
        // A custom theme saved before Group 4 that somehow escapes the migration must still render
        // a coherent road, not one positioned for lanes it does not have.
        val margin = SceneSpace.roadEdgeMarginFraction(0.771f, 0.803f)
        assertTrue(margin > 0f)
        assertEquals(0.787f, (0.771f - margin + 0.803f + margin) / 2f, eps)
    }

    @Test
    fun `lane speed follows lane depth`() {
        // Two vehicles moving at the same real speed cross the screen at rates their distances
        // decide. Rolling the far lane's speed by hand is how it previously ended up merely
        // looking different rather than being consistent.
        val expected = SceneSpace.CAR_SPEED_NEAR *
            SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_FAR_Y_FRACTION) /
            SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION)
        assertEquals(expected, SceneSpace.CAR_SPEED_FAR, 1e-6f)
        assertTrue(SceneSpace.CAR_SPEED_FAR < SceneSpace.CAR_SPEED_NEAR)
        assertTrue(SceneSpace.PEDESTRIAN_SPEED_FAR < SceneSpace.PEDESTRIAN_SPEED_NEAR)
    }

    // --- The size table ------------------------------------------------------------------

    @Test
    fun `a category's drawn height is its declared real height`() {
        // The derivation that makes the table mean anything: whatever internal scale a sprite was
        // authored at, multiplying its own unit height by its base scale has to give back the same
        // pixels-per-metre for every category.
        SceneSpace.SceneVariant.entries.forEach { v ->
            val drawnPx = v.spriteUnitsTall * v.baseScale
            assertEquals(
                "${v.name} does not resolve to the shared metric",
                v.metresTall * SceneSpace.PIXELS_PER_METRE_AT_REFERENCE,
                drawnPx,
                0.01f,
            )
        }
    }

    @Test
    fun `the sprites really are authored at incompatible internal scales`() {
        // The reason the table has to exist rather than a single global multiplier: measured on
        // its own artwork, a person is drawn at roughly three and a half times the units per metre
        // a shop front is. Any future change that makes these agree would mean the artwork had
        // been re-authored on one grid, at which point this test should be deleted deliberately
        // rather than found to be failing.
        fun unitsPerMetre(v: SceneSpace.SceneVariant) = v.spriteUnitsTall / v.metresTall
        val person = SceneSpace.PERSON_SPRITE_UNITS_TALL / SceneSpace.PERSON_METRES_TALL
        val shop = unitsPerMetre(SceneSpace.SceneVariant.BAR)
        assertTrue("the artwork's internal scales have converged", person / shop > 2.5f)
    }

    @Test
    fun `a person is taller than a car and shorter than a house`() {
        // The reported defect, stated as an invariant. All three are compared at one ground line,
        // so this is about the size table alone and not about where anything is placed.
        val personPx = SceneSpace.PERSON_SPRITE_UNITS_TALL * SceneSpace.PERSON_BASE_SCALE
        val carPx = SceneSpace.CAR_SPRITE_UNITS_TALL * SceneSpace.CAR_BASE_SCALE
        val cottagePx = SceneSpace.SceneVariant.HOUSE_SMALL.let { it.spriteUnitsTall * it.baseScale }

        assertTrue("a car is drawn taller than a person", carPx < personPx)
        assertTrue("a cottage is not clearly taller than a person", cottagePx > personPx * 2.5f)
        assertTrue("a fire engine must be taller than a car", SceneSpace.FIRE_TRUCK_BASE_SCALE > 0f)
        assertTrue(
            "a fire engine is drawn no taller than a car",
            SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL * SceneSpace.FIRE_TRUCK_BASE_SCALE > carPx,
        )
    }

    @Test
    fun `the size table is ordered the way the real objects are`() {
        // **The shops moved above the houses in v2.7, and that is the fix rather than a
        // relaxation.** They were measured as a single domestic storey, which put a restaurant
        // below a cottage and a bar below that -- so on a device a parade of shops read as
        // outbuildings behind the houses instead of as the commercial frontage they draw. A shop
        // storey is taller than a domestic one and carries a parapet above it.
        val ordered = listOf(
            // **The pumpkin left the bottom of this list in v4.17.** It declared 0.5 m through
            // v4.15 -- half an Easter egg, for a prop a foot across -- and on a OnePlus 6T it read
            // as an orange bead beside the gifts and the snowmen. v4.16 took it to 0.85 and the
            // maintainer still reported it small; 0.85, 1.00 and 1.10 were then rendered on the
            // phone beside the gift, the snowman, the penguin and the egg, and 1.00 is the one that
            // reads as a pumpkin without swallowing the penguin standing behind it, which 1.10
            // does. It now sits level with the egg rather than under the bunny, which is why the
            // chain starts at BUNNY and the pumpkin has its own relations below.
            SceneSpace.SceneVariant.BUNNY,
            SceneSpace.SceneVariant.EASTER_EGG,
            SceneSpace.SceneVariant.PARASOL,
            SceneSpace.SceneVariant.HOUSE_SMALL,
            SceneSpace.SceneVariant.HOUSE_LARGE,
            SceneSpace.SceneVariant.BAR,
            SceneSpace.SceneVariant.RESTAURANT,
            // **A fir is taller than a leafy tree, and always has been drawn that way.** This
            // list had FIR below TREE because the size table declared 9.3 m against 9.8 -- but
            // FIR is unreachable (`variantFor` never returns it; a fir is a state of a TREE
            // candidate), so a fir is drawn at TREE's scale over a 122-unit sprite where the tree
            // occupies 118, and reads 9.8 m against 9.479. v2.8 introduced the pair saying "FIR
            // shares TREE's 122 units so one metre governs both", which is what the renderer does
            // and what makes the fir the taller of the two. The order below is the drawn one.
            SceneSpace.SceneVariant.TREE,
            SceneSpace.SceneVariant.FIR,
            SceneSpace.SceneVariant.TOWER,
        )
        ordered.zipWithNext { a, b ->
            assertTrue("${a.name} should be shorter than ${b.name}", a.metresTall < b.metresTall)
        }
        // The Easter pair sits above the smallest decorations and well below a person: big
        // enough to read at the size the projection draws them, not big enough to compete.
        assertTrue(
            SceneSpace.SceneVariant.EASTER_EGG.metresTall < SceneSpace.PERSON_METRES_TALL / 1.5f,
        )
        // **The floor the pumpkin may not fall back through.** It was 0.5 m for eleven releases and
        // read as a bead; the relation that matters is that it is not smaller than the props it
        // stands among, so it is stated against them rather than as a bare number. The ceiling is
        // the penguin: a pumpkin taller than one stands in front of it and hides it.
        assertTrue(
            "a pumpkin must not be smaller than a gift",
            SceneSpace.SceneVariant.PUMPKIN.metresTall >= SceneSpace.SceneVariant.GIFT.metresTall,
        )
        assertTrue(
            "a pumpkin must not be smaller than an Easter bunny",
            SceneSpace.SceneVariant.PUMPKIN.metresTall >= SceneSpace.SceneVariant.BUNNY.metresTall,
        )
        assertTrue(
            "a pumpkin must not out-top the penguin it stands in front of",
            SceneSpace.SceneVariant.PUMPKIN.metresTall < SceneSpace.SceneVariant.PENGUIN.metresTall,
        )
        assertEquals(
            "the size chosen on the phone in v4.17",
            1.0f,
            SceneSpace.SceneVariant.PUMPKIN.metresTall,
            0.0001f,
        )
        // A snowman and a gift are smaller than the person who built and wrapped them.
        assertTrue(SceneSpace.SceneVariant.SNOWMAN.metresTall < SceneSpace.PERSON_METRES_TALL)
        assertTrue(SceneSpace.SceneVariant.GIFT.metresTall < SceneSpace.SceneVariant.SNOWMAN.metresTall)
        // Stated as its own relation as well as through the chain above, because "a commercial
        // building out-tops a house" is the property the v2.6 device pass reported missing, and a
        // chain can be satisfied by moving either end of it.
        assertTrue(
            "a bar should out-top the largest house",
            SceneSpace.SceneVariant.BAR.metresTall > SceneSpace.SceneVariant.HOUSE_LARGE.metresTall,
        )
        // **The margin is 1.90, and it always was.** This asked for 2.0 and passed on a TOWER that
        // declared 16.8 m, which v4.15 found was measured to the tip of its aerial: the building it
        // draws is 182 units, not 196, and reads as 15.6 m. Nothing about the picture changed when
        // that was corrected -- the metres-per-unit is identical -- so 2.0 was never a property of
        // the scene, only of a number that was 7.1% too large. 1.85 is the same judgement ("a
        // different class of building, not a taller one of the same class") stated against what is
        // actually drawn, and `BuildingHeightDeclarationTest` is what now keeps the declaration
        // honest.
        assertTrue(
            "a tower should out-top a shop by a clear margin",
            SceneSpace.SceneVariant.TOWER.metresTall > SceneSpace.SceneVariant.RESTAURANT.metresTall * 1.85f,
        )
    }

    @Test
    fun `the lake's inhabitants are sized against each other`() {
        // The dolphin used to be drawn longer than the boat beside it, because both were blitted
        // at their own native size and nothing related the two.
        val boatPx = SceneSpace.SAILBOAT_SPRITE_UNITS_LONG * SceneSpace.SAILBOAT_BASE_SCALE
        val dolphinPx = SceneSpace.DOLPHIN_SPRITE_UNITS_LONG * SceneSpace.DOLPHIN_BASE_SCALE
        assertTrue("a dolphin is drawn longer than a sailboat", dolphinPx < boatPx)
        assertEquals(
            SceneSpace.DOLPHIN_METRES_LONG / SceneSpace.SAILBOAT_METRES_LONG,
            dolphinPx / boatPx,
            0.001f,
        )
    }

    // --- The viewport --------------------------------------------------------------------

    @Test
    fun `scene scale tracks screen height and never returns zero`() {
        assertEquals(1f, SceneSpace.sceneScale(SceneSpace.REFERENCE_SCREEN_HEIGHT_PX), eps)
        assertEquals(0.5f, SceneSpace.sceneScale(SceneSpace.REFERENCE_SCREEN_HEIGHT_PX / 2f), eps)
        // The surface is 0 px tall until it has been sized. Returning 0 there would collapse every
        // object to nothing on the first frames; returning 1 draws them at reference size, which
        // is discarded anyway because the tiling period is 0 too.
        assertEquals(1f, SceneSpace.sceneScale(0f), eps)
        assertEquals(1f, SceneSpace.sceneScale(-100f), eps)
    }

    @Test
    fun `size variation is a spread around one, not a size`() {
        assertTrue(SceneSpace.SIZE_VARIATION_SPREAD > 0f)
        assertTrue("the spread must stay a variation, not a resize", SceneSpace.SIZE_VARIATION_SPREAD < 0.5f)
        assertTrue(SceneSpace.MIN_SIZE_VARIATION < 1f)
    }
}
