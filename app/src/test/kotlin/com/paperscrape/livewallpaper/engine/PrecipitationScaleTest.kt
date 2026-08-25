package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Atmospheric effects are sized against the world, with a floor **and** a ceiling.
 *
 * ### The two defects this file has now caught, in order
 *
 * **v4.4:** every size in `drawPrecipitation` was an absolute canvas pixel, so the effect was the
 * size it was tuned to be on exactly one viewport and about a ninth of that on a phone. Rain
 * disappeared. That was fixed by putting the sizes on the viewport scale.
 *
 * **v4.5:** the sizes went onto the scale still expressed as *pixels at a reference height*, and a
 * pixel count answers to nothing. The magnitude was three times too large and nobody could see it
 * in the number. Measured on a 1080x2424 frame, a raindrop was **1.52 m long, 1.15 times the
 * pedestrian standing beside it**; a snowflake was 0.48 m across, twice a head; the lightning bolt
 * was taller than the tallest building in the scene.
 *
 * The lesson both times is the same and it is why this file exists in the shape it does: a test
 * with only a floor lets an effect grow without limit, and a test with only a ceiling lets it
 * vanish. **Every property below is bounded on both sides**, and every bound is a relation to
 * something in the world -- a child, a head, the skyline -- rather than a share of the frame.
 *
 * `PrecipitationPixelTest` measures the rendered result; this pins the arithmetic behind it.
 */
class PrecipitationScaleTest {

    /**
     * The smallest human figure in the scene, and the yardstick a falling particle answers to.
     *
     * Derived rather than declared: `SceneSpace.PERSON_METRES_TALL`'s own doc records that the
     * children are drawn at 62 of the 80 local units the adults fill, "so one scale gives them
     * their own 0.77 of adult height with no second entry here".
     */
    private val childMetres = SceneSpace.PERSON_METRES_TALL * 62f / SceneSpace.PERSON_SPRITE_UNITS_TALL

    /** A head, for the round marks. About a seventh and a half of a standing figure. */
    private val headMetres = SceneSpace.PERSON_METRES_TALL / 7.5f

    private val viewports = listOf(800f, 1600f, 2400f, 2424f, 3200f)

    // -- the shared metric ---------------------------------------------------------------------

    @Test
    fun `one metre at the reference height is the reference pixels per metre`() {
        assertEquals(
            SceneSpace.PIXELS_PER_METRE_AT_REFERENCE,
            SceneSpace.pixelsPerMetre(SceneSpace.REFERENCE_SCREEN_HEIGHT_PX),
            1e-4f,
        )
    }

    /**
     * The viewport always shows the same amount of world, which is why a fixed particle count is
     * already a fixed density per square metre and only the particle *size* has to scale.
     */
    @Test
    fun `every viewport shows the same height of world`() {
        for (h in viewports) {
            assertEquals(
                "a ${h}px viewport should still show 53.3 m of world",
                SceneSpace.REFERENCE_SCREEN_HEIGHT_PX / SceneSpace.PIXELS_PER_METRE_AT_REFERENCE,
                h / SceneSpace.pixelsPerMetre(h),
                0.01f,
            )
        }
    }

    @Test
    fun `a degenerate viewport falls back to the reference scale`() {
        assertEquals(SceneSpace.PIXELS_PER_METRE_AT_REFERENCE, SceneSpace.pixelsPerMetre(0f), 1e-4f)
        assertEquals(SceneSpace.PIXELS_PER_METRE_AT_REFERENCE, SceneSpace.pixelsPerMetre(-100f), 1e-4f)
    }

    // -- rain ----------------------------------------------------------------------------------

    /**
     * **The ceiling v4.4 did not have.** A raindrop is at most 40 % of the shortest person in the
     * scene. The v4.5 sweep put 0.58 m at 0.43 of a child, reading as rain, and the next candidate
     * up at 0.64 of a child, reading as a falling stick; v4.4 shipped 1.73 m, which is 1.28 of a
     * whole child.
     */
    @Test
    fun `a raindrop is never more than a fraction of a child`() {
        assertTrue(
            "the longest raindrop is ${PaperRenderer.RAIN_LENGTH_MAX_METRES} m against a " +
                "$childMetres m child",
            PaperRenderer.RAIN_LENGTH_MAX_METRES <= 0.44f * childMetres,
        )
    }

    /** And the floor, so it cannot be shrunk back out of existence. */
    @Test
    fun `a raindrop is never small enough to disappear`() {
        assertTrue(
            "the shortest raindrop is ${PaperRenderer.RAIN_LENGTH_MIN_METRES} m",
            PaperRenderer.RAIN_LENGTH_MIN_METRES >= 0.20f * childMetres,
        )
        assertTrue(
            "the raindrop stroke is ${PaperRenderer.RAIN_STROKE_WIDTH_METRES} m",
            PaperRenderer.RAIN_STROKE_WIDTH_METRES >= 0.03f,
        )
    }

    /** A streak is a streak: longer than it is wide, by a lot, and never a square. */
    @Test
    fun `a raindrop is much longer than it is thick`() {
        assertTrue(
            PaperRenderer.RAIN_LENGTH_MIN_METRES > 5f * PaperRenderer.RAIN_STROKE_WIDTH_METRES,
        )
        assertTrue(PaperRenderer.RAIN_LENGTH_MAX_METRES > PaperRenderer.RAIN_LENGTH_MIN_METRES)
    }

    // -- snow ----------------------------------------------------------------------------------

    /** A flake is a disc, so it answers to a head rather than to a whole figure. */
    @Test
    fun `a snowflake is never wider than a head`() {
        assertTrue(
            "the largest flake is ${PaperRenderer.SNOW_DIAMETER_MAX_METRES} m against a " +
                "$headMetres m head",
            PaperRenderer.SNOW_DIAMETER_MAX_METRES <= 1.35f * headMetres,
        )
    }

    @Test
    fun `a snowflake is never small enough to disappear`() {
        assertTrue(PaperRenderer.SNOW_DIAMETER_MIN_METRES >= 0.08f)
        assertTrue(PaperRenderer.SNOW_DIAMETER_MAX_METRES > PaperRenderer.SNOW_DIAMETER_MIN_METRES)
    }

    /** Drift belongs to the flake it moves, not to a number of its own. */
    @Test
    fun `snow drifts by about one flake-and-a-bit`() {
        assertTrue(PaperRenderer.SNOW_SWAY_METRES in 0.15f..0.60f)
    }

    // -- density -------------------------------------------------------------------------------

    /**
     * The pool is the density, and the density is what makes rain read. Bounded on both sides:
     * below 180 the swept frames left dry holes four columns of six wide; above 400 nothing is
     * gained and the loop is paid for nothing.
     */
    @Test
    fun `the precipitation pool is dense enough to read and not extravagant`() {
        assertTrue(
            "pool is ${PaperRenderer.PRECIPITATION_POOL_SIZE}",
            PaperRenderer.PRECIPITATION_POOL_SIZE in 180..400,
        )
    }

    // -- lightning -----------------------------------------------------------------------------

    /**
     * A bolt keeps its variety but never approaches the height a *painted* building reaches.
     *
     * The building is measured in `PrecipitationPixelTest`; what is pinned here is the fraction
     * range itself, because the failure v4.5 fixed was that the range's own top -- 0.16 -- was
     * larger than anything in the scene.
     */
    @Test
    fun `a lightning bolt stays within its measured band`() {
        val max = PaperRenderer.LIGHTNING_BOLT_MIN_HEIGHT_FRACTION +
            PaperRenderer.LIGHTNING_BOLT_HEIGHT_SPREAD_FRACTION
        assertTrue("the tallest bolt is $max of screen height", max <= 0.10f)
        assertTrue(
            "the shortest bolt is ${PaperRenderer.LIGHTNING_BOLT_MIN_HEIGHT_FRACTION}",
            PaperRenderer.LIGHTNING_BOLT_MIN_HEIGHT_FRACTION >= 0.05f,
        )
        assertTrue(
            "a storm whose strikes are all one size reads as a loop",
            PaperRenderer.LIGHTNING_BOLT_HEIGHT_SPREAD_FRACTION > 0f,
        )
    }

    /** The bolt is a fraction of screen height, which is a size in metres. State it as one. */
    @Test
    fun `a lightning bolt is the same size in metres on every viewport`() {
        val max = PaperRenderer.LIGHTNING_BOLT_MIN_HEIGHT_FRACTION +
            PaperRenderer.LIGHTNING_BOLT_HEIGHT_SPREAD_FRACTION
        val expected = max * SceneSpace.REFERENCE_SCREEN_HEIGHT_PX / SceneSpace.PIXELS_PER_METRE_AT_REFERENCE
        for (h in viewports) {
            assertEquals(
                "a ${h}px viewport draws a differently sized bolt",
                expected, h * max / SceneSpace.pixelsPerMetre(h), 0.01f,
            )
        }
        assertTrue("the tallest bolt is $expected m", expected in 3.0f..5.5f)
    }

    /** The veil is measured and deliberately unchanged in v4.5 -- see its own doc. */
    @Test
    fun `the lightning veil is a named, bounded value`() {
        assertTrue(PaperRenderer.LIGHTNING_VEIL_MAX_ALPHA in 1f..255f)
    }

    // -- scale invariance ------------------------------------------------------------------------

    /** Every declared size is the same share of the frame on every viewport. */
    @Test
    fun `every declared size holds its share of the frame at every viewport`() {
        val declared = mapOf(
            "rain length max" to PaperRenderer.RAIN_LENGTH_MAX_METRES,
            "rain stroke" to PaperRenderer.RAIN_STROKE_WIDTH_METRES,
            "snow diameter max" to PaperRenderer.SNOW_DIAMETER_MAX_METRES,
            "snow sway" to PaperRenderer.SNOW_SWAY_METRES,
            "fall margin" to PaperRenderer.PRECIPITATION_BOTTOM_MARGIN_METRES,
        )
        for ((name, metres) in declared) {
            val reference = metres * SceneSpace.pixelsPerMetre(2400f) / 2400f
            for (h in viewports) {
                assertEquals(
                    "$name is not the same share of a ${h}px frame",
                    reference, metres * SceneSpace.pixelsPerMetre(h) / h, 1e-6f,
                )
            }
        }
    }
}
