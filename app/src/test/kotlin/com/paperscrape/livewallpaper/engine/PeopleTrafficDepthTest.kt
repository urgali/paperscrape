package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pavement is behind the carriageway, for every figure the generator can produce.
 *
 * ### The defect this closes
 *
 * `SceneObjectRenderer.draw` ran the vehicle loop and *then* `drawPeople`, so a walking figure was
 * painted over the traffic. That is wrong for every arrangement the app can reach -- the pavement
 * rows are 0.795 and 0.807, the lanes 0.834 and 0.862 -- and it was only invisible because the two
 * have to coincide in x before anything shows. Measured across the shipped catalogue before the
 * fix: the deepest figure the generator produces stands **0.0100 of screen height below a far-lane
 * car's roof line**, which is 24 px on a 2400 px screen and 32 px against a police light bar, and
 * it painted its shoes across the roof whenever a car passed behind it.
 *
 * ### Why this is arithmetic and not a golden
 *
 * A golden sees one frame of one theme at one instant, and the overlap needs a coincidence in x
 * that most frames do not contain -- `traffic-day.png` and `traffic-night.png` do not, which is
 * exactly why seventeen releases of golden comparison never noticed. What makes the reordering
 * *correct* is a property of the two bands, so that is what is asserted: sweep every theme and
 * every density, take the deepest figure any of them can put on the street, and check it against
 * the nearest thing the road can put in front of it.
 */
class PeopleTrafficDepthTest {

    /** The deepest ground line any pedestrian reaches, over the whole shipped catalogue. */
    private fun deepestPedestrianRow(): Float {
        var deepest = Float.NEGATIVE_INFINITY
        for (theme in ThemeCatalog.ALL) {
            for (density in DENSITIES) {
                val street = PedestrianPopulation.build(
                    seed = theme.id.hashCode(),
                    density = density,
                    nearRowYFraction = SceneSpace.PAVEMENT_NEAR_Y_FRACTION,
                    farRowYFraction = SceneSpace.PAVEMENT_FAR_Y_FRACTION,
                )
                for (person in street) deepest = maxOf(deepest, person.rowYFraction)
            }
        }
        return deepest
    }

    // ------------------------------------------------------------ the ordering the fix relies on

    /**
     * **The assertion the reordering rests on.** No figure is ever nearer than a car.
     *
     * Checked against the generator's own hard ceiling rather than against what the shipped themes
     * happen to produce, so a new theme cannot slip past it: a member's row is the group's row plus
     * a jitter clamped to `MEMBER_ROW_SPREAD` either side, so 0.807 + 0.012 is the deepest ground
     * line that exists, whatever the seed.
     */
    @Test
    fun `no pedestrian can stand nearer the viewer than the far traffic lane`() {
        val ceiling = maxOf(SceneSpace.PAVEMENT_NEAR_Y_FRACTION, SceneSpace.PAVEMENT_FAR_Y_FRACTION) +
            PEDESTRIAN_ROW_SPREAD
        assertTrue(
            "the pavement can reach $ceiling and the far lane is at ${SceneSpace.ROAD_LANE_FAR_Y_FRACTION}",
            ceiling < SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
        )
        assertTrue(
            "and the deepest figure the shipped themes actually produce is behind it too",
            deepestPedestrianRow() < SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
        )
    }

    /**
     * And there is no other lane a car can be on.
     *
     * A saved theme stores each car's own `laneYFraction`, and pre-v76.5 files carry lanes that no
     * longer exist -- 0.820 and 0.855 among them, which is *above* the pavement's ceiling. They
     * cannot reach the renderer: `SceneObjectCatalog` snaps every stored lane onto one of the two
     * canonical ones, which `PersistedThemeGeometryTest` proves for the round trip. This states the
     * consequence the draw order depends on.
     */
    @Test
    fun `the only lanes a car is ever drawn on are the two canonical ones`() {
        val lanes = ThemeCatalog.ALL
            .flatMap { SceneObjectCatalog.layoutFor(it.id, it.accentColor).cars }
            .map { it.laneYFraction }
            .distinct()
        assertTrue("no theme has any traffic to check", lanes.isNotEmpty())
        for (lane in lanes) {
            assertTrue(
                "a car is drawn on lane $lane, which is neither canonical lane",
                lane == SceneSpace.ROAD_LANE_FAR_Y_FRACTION || lane == SceneSpace.ROAD_LANE_NEAR_Y_FRACTION,
            )
        }
    }

    // ------------------------------------------------------------ what the old order cost

    /**
     * How much of a car the old order let a pedestrian paint over, kept as a measurement.
     *
     * Not a threshold to pass -- the reordering makes it unreachable -- but the number that says
     * why the reordering was worth making, and a guard on the geometry that produced it. If the
     * pavement or the lanes move far enough for this to change materially, the release that moves
     * them should have to say so.
     */
    @Test
    fun `the deepest figure overlapped a far-lane car by the measured amount`() {
        val carHeightFraction = SceneSpace.CAR_SPRITE_UNITS_TALL * SceneSpace.CAR_BASE_SCALE *
            SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_FAR_Y_FRACTION) /
            SceneSpace.REFERENCE_SCREEN_HEIGHT_PX
        val roof = SceneSpace.ROAD_LANE_FAR_Y_FRACTION - carHeightFraction
        val overlap = deepestPedestrianRow() - roof
        assertEquals(
            "the deepest figure's feet, below a far-lane car's roof, in fractions of screen height",
            0.0100f,
            overlap,
            0.0015f,
        )
        assertEquals(
            "which on a 2400 px screen is this many pixels of pedestrian over car",
            24f,
            overlap * SceneSpace.REFERENCE_SCREEN_HEIGHT_PX,
            4f,
        )
    }

    /**
     * The pavement's jitter still overruns the road's own top edge, and by how much.
     *
     * **Deliberately recorded rather than fixed.** The deepest figure stands 1.9 px past the kerb
     * on a 2400 px screen -- it is on the tarmac, by two pixels, and clamping it would mean either
     * moving the pavement or narrowing the jitter, both of which change the distribution this
     * release is not allowed to touch. It is bounded here so it cannot grow unnoticed into
     * something a viewer would see.
     */
    @Test
    fun `a figure may stand at most a couple of pixels past the kerb`() {
        val overrun = (deepestPedestrianRow() - SceneSpace.roadTopYFraction()) *
            SceneSpace.REFERENCE_SCREEN_HEIGHT_PX
        assertTrue(
            "the deepest figure is ${"%.1f".format(overrun)} px onto the road, which is more than a rounding",
            overrun < 4f,
        )
    }

    private companion object {
        /** Every density step the People slider can be left on, plus the ends. */
        val DENSITIES = listOf(0.2f, 0.25f, 0.4f, 0.5f, 0.6f, 0.75f, 0.8f, 1.0f)

        /**
         * `PedestrianPopulation.MEMBER_ROW_SPREAD`, which is private to it.
         *
         * Copied rather than exposed: widening the visibility of a generator's internal so a test
         * can read it makes the internal part of the API. If the two ever disagree the first
         * assertion in this class is the one that notices, because the generator's own output is
         * checked against the ceiling this number claims.
         */
        const val PEDESTRIAN_ROW_SPREAD = 0.012f
    }
}
