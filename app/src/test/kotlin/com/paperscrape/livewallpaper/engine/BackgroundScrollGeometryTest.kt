package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

/**
 * Tests for the two halves of the `scrollBackground` layer.
 *
 * The layer holds a tiled pattern (the star field) and a single object (the sun or the moon), and
 * the shipped code applied one wrapped shift to both. A wrap is seamless only for something that
 * is also tiled, so for the star field it opened a starless band up to a full screen wide, and for
 * the celestial body it produced a periodic disappearance followed by a pop back to the rest
 * position -- roughly every 18 minutes of visible uptime at default settings.
 *
 * The requirement is not symmetric between the two:
 *
 *  - **The star field must never have a hole.** Every point of the viewport must be covered by
 *    some tile copy, at every reachable shift. Painting one copy more than strictly necessary
 *    costs a pass over 70 cached sprites; leaving one out is a visible band of empty sky.
 *  - **The celestial body must never leave the viewport, at any input.** That is an invariant, not
 *    a tendency, so it is asserted over the whole reachable parameter space rather than at sample
 *    points.
 *
 * Every function under test is pure and lives in `PaperRenderer`'s companion for exactly that
 * reason: `draw` needs a `Canvas`, so anything left as a condition inside it cannot be tested here.
 */
class BackgroundScrollGeometryTest {

    // --- The real parameter space -----------------------------------------------------------

    /** Real display widths, from the smallest supported phone to a tablet. */
    private val screenWidths = floatArrayOf(320f, 480f, 540f, 720f, 1080f, 1440f, 2160f, 3200f)

    /** `parallaxStrength` is a user slider over `0.5 .. 2.0`; the layer factor is fixed at 0.15. */
    private val parallaxStrengths = doubleArrayOf(0.5, 0.75, 1.0, 1.25, 1.5, 1.75, 2.0)

    private fun parallaxFor(strength: Double) = 0.15 * strength

    /** `celestialX` is `SunPositionCalculator.DayPhase.celestialX`, which is `arcT` in `0 .. 1`. */
    private fun celestialXs(steps: Int) = (0..steps).map { it.toFloat() / steps }

    private fun restCx(celestialX: Float, screenWidth: Float): Float {
        val margin = screenWidth * PaperRenderer.CELESTIAL_MARGIN_FRACTION
        return margin + celestialX * (screenWidth - 2f * margin)
    }

    private fun discRadius(screenWidth: Float) =
        screenWidth * PaperRenderer.CELESTIAL_RADIUS_FRACTION * 2f

    private fun offset(
        celestialX: Float,
        screenWidth: Float,
        parallax: Double,
        accum: Double,
        swipe: Float,
    ) = PaperRenderer.celestialParallaxOffset(celestialX, screenWidth, parallax, accum, swipe)

    // --- Viewport invariant -------------------------------------------------------------------

    /**
     * The whole point of the change: over every screen width, every parallax strength, every
     * position on the day arc, two full drift cycles and the entire swipe range, the disc's own
     * edges stay inside the viewport.
     *
     * Two full drift cycles rather than one, so a failure that only appears after the sway has
     * wrapped once cannot hide.
     */
    @Test
    fun `celestial disc never leaves the viewport`() {
        for (screenWidth in screenWidths) {
            for (strength in parallaxStrengths) {
                val parallax = parallaxFor(strength)
                val cycle = 1.0 / parallax
                for (celestialX in celestialXs(40)) {
                    val rest = restCx(celestialX, screenWidth)
                    val radius = discRadius(screenWidth)
                    for (step in 0..80) {
                        val accum = 2.0 * cycle * step / 80.0
                        for (swipeStep in 0..4) {
                            val swipe = swipeStep / 4f
                            val cx = rest + offset(celestialX, screenWidth, parallax, accum, swipe)
                            assertTrue(
                                "left edge escaped: w=$screenWidth strength=$strength " +
                                    "celestialX=$celestialX accum=$accum swipe=$swipe " +
                                    "left=${cx - radius}",
                                cx - radius >= -TOLERANCE_PX,
                            )
                            assertTrue(
                                "right edge escaped: w=$screenWidth strength=$strength " +
                                    "celestialX=$celestialX accum=$accum swipe=$swipe " +
                                    "right=${cx + radius}",
                                cx + radius <= screenWidth + TOLERANCE_PX,
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * The invariant must survive an offset outside the `0..1` the `onOffsetsChanged` contract
     * promises, because the contract is a promise about launchers, not a guarantee from the
     * platform.
     */
    @Test
    fun `celestial disc stays in the viewport for out-of-contract swipe offsets`() {
        val screenWidth = 1080f
        val parallax = parallaxFor(2.0)
        val radius = discRadius(screenWidth)
        for (celestialX in celestialXs(20)) {
            val rest = restCx(celestialX, screenWidth)
            for (swipe in floatArrayOf(-5f, -1f, -0.001f, 1.001f, 2f, 17f)) {
                val cx = rest + offset(celestialX, screenWidth, parallax, 3.3, swipe)
                assertTrue("left edge escaped at swipe=$swipe", cx - radius >= -TOLERANCE_PX)
                assertTrue("right edge escaped at swipe=$swipe", cx + radius <= screenWidth + TOLERANCE_PX)
            }
        }
    }

    /** An unsized surface must not produce a position, let alone a NaN one. */
    @Test
    fun `celestial offset is zero before the surface has a width`() {
        assertEquals(0f, offset(0.5f, 0f, 0.15, 12.3, 0.4f), 0f)
        assertEquals(0f, offset(0.5f, -1f, 0.15, 12.3, 0.4f), 0f)
    }

    // --- Seam continuity ----------------------------------------------------------------------

    /**
     * The defect being closed is a discontinuity, so this asserts the absence of one directly:
     * across the drift seam the offset must be continuous in position *and* in slope.
     *
     * The sway is `(1 - cos(2*pi*parallax*accum)) / 2`, which is 0 with zero slope at phase 0 and
     * again at phase 1. That is why the body can cross the seam the star field wraps at without
     * inheriting the star field's step.
     */
    @Test
    fun `celestial offset is continuous across the drift seam`() {
        val screenWidth = 1080f
        for (strength in parallaxStrengths) {
            val parallax = parallaxFor(strength)
            val cycle = 1.0 / parallax
            for (celestialX in celestialXs(20)) {
                val justBefore = offset(celestialX, screenWidth, parallax, cycle - 1e-6, 0f)
                val atSeam = offset(celestialX, screenWidth, parallax, cycle, 0f)
                val justAfter = offset(celestialX, screenWidth, parallax, cycle + 1e-6, 0f)
                assertEquals(
                    "position stepped at the seam (celestialX=$celestialX)",
                    justBefore.toDouble(), atSeam.toDouble(), 0.01,
                )
                assertEquals(
                    "position stepped after the seam (celestialX=$celestialX)",
                    atSeam.toDouble(), justAfter.toDouble(), 0.01,
                )
                assertEquals(
                    "slope stepped at the seam (celestialX=$celestialX)",
                    (atSeam - justBefore).toDouble(), (justAfter - atSeam).toDouble(), 0.01,
                )
            }
        }
    }

    /**
     * Continuity everywhere, not only at the seam: no two consecutive frames may move the body
     * further than the drift itself can account for.
     *
     * The bound is the sway's own maximum slope, `pi * parallax * travel` per unit of accumulator,
     * which is derived rather than measured -- a per-frame budget picked by observation would pass
     * for whatever the code happens to do.
     */
    @Test
    fun `celestial offset never steps between frames`() {
        val screenWidth = 1080f
        val parallax = parallaxFor(2.0)
        // A generous frame: 33ms at scrollSpeed 1.0 is 0.00132 of accumulator.
        val perFrame = 0.0014
        val travelCap = 2.0 * parallax * screenWidth
        val maxStep = Math.PI * parallax * travelCap * perFrame * 0.5 + TOLERANCE_PX
        for (celestialX in celestialXs(10)) {
            var previous = offset(celestialX, screenWidth, parallax, 0.0, 0f)
            var accum = 0.0
            repeat(4000) {
                accum += perFrame
                val current = offset(celestialX, screenWidth, parallax, accum, 0f)
                assertTrue(
                    "frame-to-frame step ${abs(current - previous)} exceeded $maxStep " +
                        "at accum=$accum celestialX=$celestialX",
                    abs(current - previous) <= maxStep,
                )
                previous = current
            }
        }
    }

    // --- Direction and swipe responsiveness ---------------------------------------------------

    /**
     * The body may only ever be at, or left of, its rest position. Moving right of rest would put
     * it out of step with every other layer, all of which drift left.
     */
    @Test
    fun `celestial offset is never rightward`() {
        for (screenWidth in screenWidths) {
            for (strength in parallaxStrengths) {
                val parallax = parallaxFor(strength)
                for (celestialX in celestialXs(20)) {
                    for (step in 0..60) {
                        val accum = step / 60.0 * (2.0 / parallax)
                        for (swipeStep in 0..4) {
                            assertTrue(
                                "offset went rightward",
                                offset(celestialX, screenWidth, parallax, accum, swipeStep / 4f) <= 0f,
                            )
                        }
                    }
                }
            }
        }
    }

    /** A larger home-screen offset must always move the body further left, never back. */
    @Test
    fun `celestial offset is monotonically leftward in the swipe offset`() {
        val screenWidth = 1080f
        for (strength in parallaxStrengths) {
            val parallax = parallaxFor(strength)
            for (celestialX in celestialXs(20)) {
                for (accumStep in 0..8) {
                    val accum = accumStep / 8.0 * (1.0 / parallax)
                    var previous = Float.POSITIVE_INFINITY
                    for (swipeStep in 0..40) {
                        val current = offset(celestialX, screenWidth, parallax, accum, swipeStep / 40f)
                        assertTrue(
                            "swipe moved the body back to the right at celestialX=$celestialX",
                            current <= previous + TOLERANCE_PX,
                        )
                        previous = current
                    }
                }
            }
        }
    }

    /**
     * Where the geometry allows it, a full swipe must move the body exactly as far as an
     * unbounded parallax would have -- `parallax * screenWidth`. This is what keeps the fix from
     * being a freeze: the response is unchanged over most of the day.
     *
     * The slack runs out below `celestialX` of about 0.38 at `parallaxStrength` 1, which is the
     * declared cost of the bound; above it the response is full.
     */
    @Test
    fun `a full swipe moves the body by the full parallax where there is room`() {
        val screenWidth = 1080f
        for (strength in parallaxStrengths) {
            val parallax = parallaxFor(strength)
            val demand = parallax * screenWidth
            for (celestialX in celestialXs(50)) {
                val slackLeft = restCx(celestialX, screenWidth) - discRadius(screenWidth)
                if (slackLeft < 2.0 * demand) continue // geometry-limited, covered below
                val travel = offset(celestialX, screenWidth, parallax, 0.0, 0f) -
                    offset(celestialX, screenWidth, parallax, 0.0, 1f)
                assertEquals(
                    "swipe travel was not the full parallax at celestialX=$celestialX",
                    demand, travel.toDouble(), 0.5,
                )
            }
        }
    }

    /** Where the slack does run out, the response tapers smoothly rather than stopping dead. */
    @Test
    fun `swipe response tapers continuously as the slack runs out`() {
        val screenWidth = 1080f
        val parallax = parallaxFor(1.0)
        var previous = 0f
        for (step in 0..400) {
            val celestialX = step / 400f
            val travel = offset(celestialX, screenWidth, parallax, 0.0, 0f) -
                offset(celestialX, screenWidth, parallax, 0.0, 1f)
            assertTrue("travel went backwards at celestialX=$celestialX", travel >= previous - TOLERANCE_PX)
            assertTrue(
                "travel jumped at celestialX=$celestialX",
                travel - previous < screenWidth * 0.01f,
            )
            previous = travel
        }
    }

    // --- Non-fixed motion ---------------------------------------------------------------------

    /**
     * With no swipe at all the body must still move over a drift cycle, or the fix would have
     * traded a disappearing sun for a pinned one.
     *
     * The threshold is derived from the geometry rather than chosen: over half a cycle the sway
     * spans its full `0..1`, so the excursion must be half the travel the geometry allows.
     */
    @Test
    fun `the body still drifts with no swipe`() {
        val screenWidth = 1080f
        for (strength in parallaxStrengths) {
            val parallax = parallaxFor(strength)
            val cycle = 1.0 / parallax
            for (celestialX in celestialXs(20)) {
                val slackLeft = restCx(celestialX, screenWidth) - discRadius(screenWidth)
                val travel = minOf(2.0 * parallax * screenWidth, slackLeft.toDouble())
                var lowest = 0f
                for (step in 0..200) {
                    lowest = minOf(lowest, offset(celestialX, screenWidth, parallax, cycle * step / 200.0, 0f))
                }
                assertEquals(
                    "drift excursion was not half the available travel at celestialX=$celestialX",
                    travel * 0.5, (-lowest).toDouble(), 0.5,
                )
            }
        }
    }

    /** The sway must be the value the doc comment claims, not merely something bounded. */
    @Test
    fun `the sway is the cosine of the background wrap phase`() {
        val screenWidth = 1080f
        val parallax = parallaxFor(1.0)
        val celestialX = 0.5f
        val slackLeft = restCx(celestialX, screenWidth) - discRadius(screenWidth)
        val travel = minOf(2.0 * parallax * screenWidth, slackLeft.toDouble())
        for (step in 0..50) {
            val accum = step / 50.0 * (2.0 / parallax)
            val sway = (1.0 - cos(2.0 * Math.PI * parallax * accum)) * 0.5
            val expected = -(sway * 0.5) * travel
            assertEquals(
                expected, offset(celestialX, screenWidth, parallax, accum, 0f).toDouble(), 0.01,
            )
        }
    }

    // --- scrollBackground = false --------------------------------------------------------------

    /**
     * With the background not scrolling, `drawCelestialBody` is called with its default offset and
     * the star field is drawn once, untranslated. Neither path may depend on the drift.
     *
     * The offset function itself is not even called on that path; this asserts the value it would
     * have to produce for the call site's default to be the right one.
     */
    @Test
    fun `the default offset is exactly zero`() {
        // A zero-length drift and a zero swipe is the state the non-scrolling path is equivalent to.
        for (screenWidth in screenWidths) {
            for (celestialX in celestialXs(20)) {
                assertEquals(
                    0f, offset(celestialX, screenWidth, parallaxFor(1.0), 0.0, 0f), 0f,
                )
            }
        }
    }

    // --- Star field coverage ------------------------------------------------------------------

    /**
     * No hole, anywhere, ever: for every wrapped shift the union of the drawn tile copies must
     * cover the whole viewport.
     *
     * `wrappedScrollShift` produces `(-tileWidth, 0]` and the star field's period is exactly one
     * screen width, so the shift and the tile width are the same number here -- which is precisely
     * why a single copy could leave up to a full screen uncovered.
     */
    @Test
    fun `star tiles cover the whole viewport at every shift`() {
        for (screenWidth in screenWidths) {
            val tileWidth = screenWidth
            for (step in 0..2000) {
                // Exclusive of -tileWidth, inclusive of 0, matching wrappedScrollShift's range.
                val shift = -tileWidth * (1f - step / 2000f)
                val first = PaperRenderer.firstStarTileOffset(
                    shift, tileWidth, LEFT_EXTENT, RIGHT_EXTENT,
                )
                val limit = PaperRenderer.starTileOffsetLimit(
                    shift, tileWidth, screenWidth, LEFT_EXTENT, RIGHT_EXTENT,
                )
                assertTrue("empty tile range at shift=$shift", limit > first)
                // Each copy covers [shift + k*tile, shift + k*tile + tile) of star positions.
                val coveredFrom = shift + first * tileWidth
                val coveredTo = shift + limit * tileWidth
                assertTrue(
                    "left of the viewport uncovered at shift=$shift (from $coveredFrom)",
                    coveredFrom <= 0f,
                )
                assertTrue(
                    "right of the viewport uncovered at shift=$shift (to $coveredTo)",
                    coveredTo >= screenWidth,
                )
            }
        }
    }

    /**
     * Coverage alone would be satisfied by drawing a hundred copies. The count must also stay at
     * what the geometry actually requires, because each copy is a pass over the whole field.
     */
    @Test
    fun `star tile count stays at two, or three only across a sprite seam`() {
        val screenWidth = 1080f
        var threes = 0
        val samples = 2000
        for (step in 0..samples) {
            val shift = -screenWidth * (1f - step.toFloat() / samples)
            val count = PaperRenderer.starTileOffsetLimit(
                shift, screenWidth, screenWidth, LEFT_EXTENT, RIGHT_EXTENT,
            ) - PaperRenderer.firstStarTileOffset(shift, screenWidth, LEFT_EXTENT, RIGHT_EXTENT)
            assertTrue("tile count $count at shift=$shift", count == 2 || count == 3)
            if (count == 3) threes++
        }
        // The three-copy window is only as wide as the sprite extents that cause it.
        val expectedFraction = (LEFT_EXTENT + RIGHT_EXTENT) / screenWidth
        assertTrue(
            "three-copy window is wider than the sprite extents justify: " +
                "${threes.toFloat() / samples} vs $expectedFraction",
            threes.toFloat() / samples <= expectedFraction * 1.5f,
        )
    }

    /** An unsized surface must produce an empty range, not an infinite or negative one. */
    @Test
    fun `star tile range is empty before the surface has a width`() {
        assertEquals(0, PaperRenderer.firstStarTileOffset(0f, 0f, LEFT_EXTENT, RIGHT_EXTENT))
        assertEquals(0, PaperRenderer.starTileOffsetLimit(0f, 0f, 0f, LEFT_EXTENT, RIGHT_EXTENT))
    }

    /**
     * The extents the renderer passes must be the ones this file reasons about.
     *
     * They were asymmetric while `star_sparkle.png` was blitted with the wrong scale convention,
     * and this assertion is what forced the tile range to be re-derived when that was corrected
     * rather than silently staying wider than it needs to be. It keeps doing that job: a change
     * to the asset or to the convention has to come back through these constants.
     */
    @Test
    fun `star sprite extents match the values the tile range is derived from`() {
        assertEquals(LEFT_EXTENT, PaperRenderer.STAR_SPRITE_LEFT_EXTENT_PX, 0f)
        assertEquals(RIGHT_EXTENT, PaperRenderer.STAR_SPRITE_RIGHT_EXTENT_PX, 0f)
        assertEquals(2.4f + 3.2f, PaperRenderer.MAX_STAR_RADIUS_PX, 0f)
        assertEquals(
            "the sprite is centred on the star, so the two extents are the same",
            PaperRenderer.STAR_SPRITE_LEFT_EXTENT_PX,
            PaperRenderer.STAR_SPRITE_RIGHT_EXTENT_PX,
            0f,
        )
    }

    // --- The geometry the bound rests on --------------------------------------------------------

    /**
     * The bound is only meaningful because the keep-out margin is wider than the disc. If that
     * ever stops being true the rest position itself would clip, and `slackLeft` would be
     * negative -- so it is asserted here rather than assumed by a comment.
     */
    @Test
    fun `the keep-out margin is wider than the disc radius`() {
        assertTrue(
            PaperRenderer.CELESTIAL_MARGIN_FRACTION >
                PaperRenderer.CELESTIAL_RADIUS_FRACTION * 2f,
        )
    }

    private companion object {
        /** Half a pixel: below what any renderer can express, so an escape of less is not one. */
        const val TOLERANCE_PX = 0.5f

        /** Written as the same expressions the renderer uses, not as their decimal values:
         * `2.4f + 3.2f` is 5.6000004f in binary32, so a literal `5.6f` would not be the same
         * number and the comparison below would be asserting rounding rather than agreement. */
        const val LEFT_EXTENT = 2.4f + 3.2f
        const val RIGHT_EXTENT = 2.4f + 3.2f
    }
}
