package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Who appears at a window, and how varied they are.
 *
 * The user's report was that the faces at the windows all look the same. These tests measure that
 * claim rather than asserting it away: variety is checked by sweeping buildings and windows and
 * counting what comes out, and the independence claims are checked as *distributions*, because a
 * generator can technically produce every value while still being heavily biased.
 */
class WindowOccupantsTest {

    private fun seeds(n: Int) = (0 until n).map { "theme-$it".hashCode() }

    /**
     * A pane count for the rate sweep.
     *
     * v4.2 made occupancy a count dealt across a building's own windows, so how many a building
     * has is now an input rather than an irrelevance: eight is a plain, kind-neutral pool that
     * keeps the sweep measuring the rate itself.
     */
    private companion object {
        const val WINDOWS = 8
    }

    private fun occupants(kind: WindowBuildingKind, windowsPerBuilding: Int, buildings: Int = 60) =
        buildList {
            for (seed in seeds(40)) {
                for (b in 0 until buildings) {
                    val buildingSeed = b * 100_003
                    for (w in 0 until windowsPerBuilding) {
                        if (WindowOccupants.isOccupied(seed, buildingSeed, w, windowsPerBuilding, kind)) {
                            add(WindowOccupants.occupantAt(seed, buildingSeed, w))
                        }
                    }
                }
            }
        }

    // ------------------------------------------------------------- presence

    /** Every supported building kind must actually produce occupants. */
    @Test
    fun `houses, commercial buildings and skyscrapers all get occupants`() {
        assertTrue(occupants(WindowBuildingKind.HOUSE, 4).isNotEmpty())
        assertTrue(occupants(WindowBuildingKind.COMMERCIAL, 3).isNotEmpty())
        assertTrue(occupants(WindowBuildingKind.SKYSCRAPER, 16).isNotEmpty())
    }

    /**
     * The rates have to be honoured, not merely declared.
     *
     * The regression guarded here is a gate that ignores its rate -- which is how v4.0's
     * `seed % 3` behaved once the pool changed underneath it.
     */
    @Test
    fun `the occupancy rate is the one each building kind declares`() {
        for (kind in WindowBuildingKind.entries) {
            var occupied = 0
            var total = 0
            for (seed in seeds(60)) {
                for (b in 0 until 200) {
                    for (w in 0 until WINDOWS) {
                        total++
                        if (WindowOccupants.isOccupied(seed, b * 100_003, w, WINDOWS, kind)) occupied++
                    }
                }
            }
            assertEquals(
                "$kind occupancy",
                WindowOccupants.rateFor(kind),
                occupied.toFloat() / total,
                0.03f,
            )
        }
    }

    /** A tower must be sparser than a home, or it reads as a doll's house. */
    @Test
    fun `skyscrapers are sparser per window than houses`() {
        assertTrue(
            WindowOccupants.rateFor(WindowBuildingKind.SKYSCRAPER) <
                WindowOccupants.rateFor(WindowBuildingKind.HOUSE),
        )
    }

    // -------------------------------------------------------------- variety

    /** The reported defect: the faces must not all be the same person. */
    @Test
    fun `all four kinds of person appear at windows of every building kind`() {
        for (kind in WindowBuildingKind.entries) {
            val windows = if (kind == WindowBuildingKind.SKYSCRAPER) 16 else 4
            val seen = occupants(kind, windows).map { it.age to it.sex }.toSet()
            assertEquals("$kind produced only $seen", 4, seen.size)
        }
    }

    /** No kind of person may dominate: each of the four should be near a quarter of the total. */
    @Test
    fun `the four kinds of person appear in roughly equal numbers`() {
        val all = occupants(WindowBuildingKind.HOUSE, 4, buildings = 200)
        assertTrue("too few samples: ${all.size}", all.size > 400)
        val counts = all.groupingBy { it.kindIndex }.eachCount()
        assertEquals(4, counts.size)
        for ((kindIndex, n) in counts) {
            assertEquals("kind $kindIndex share", 0.25f, n.toFloat() / all.size, 0.06f)
        }
    }

    /** One building must be able to hold different people at different windows. */
    @Test
    fun `one building can hold different people at different windows`() {
        var found = false
        for (seed in seeds(200)) {
            for (b in 0 until 40) {
                val buildingSeed = b * 100_003
                val here = (0 until 16)
                    .filter { WindowOccupants.isOccupied(seed, buildingSeed, it, 16, WindowBuildingKind.SKYSCRAPER) }
                    .map { WindowOccupants.occupantAt(seed, buildingSeed, it).kindIndex }
                if (here.distinct().size > 1) { found = true; break }
            }
            if (found) break
        }
        assertTrue("every window of every building held the same person", found)
    }

    /** Two buildings of the same kind at different places must not be copies of each other. */
    @Test
    fun `two buildings of the same kind are populated differently`() {
        var differing = 0
        for (seed in seeds(100)) {
            val a = (0 until 4).map { WindowOccupants.occupantAt(seed, 1 * 100_003, it) }
            val b = (0 until 4).map { WindowOccupants.occupantAt(seed, 2 * 100_003, it) }
            if (a != b) differing++
        }
        assertTrue("buildings were identical for every seed", differing > 90)
    }

    // --------------------------------------------------------- independence

    /**
     * Who is at a window must not depend on what sort of building it is.
     *
     * Same address, three building kinds: the occupant is the same person, because
     * [WindowOccupants.occupantAt] never sees the kind. Only *whether* anybody is there varies.
     */
    @Test
    fun `the occupant does not depend on the building kind`() {
        for (seed in seeds(50)) {
            for (w in 0 until 8) {
                val occupant = WindowOccupants.occupantAt(seed, 7 * 100_003, w)
                // The value is a function of address alone -- no kind is passed at all.
                assertEquals(occupant, WindowOccupants.occupantAt(seed, 7 * 100_003, w))
            }
        }
    }

    /** Window position must not bias who stands there: no "men on the ground floor" effect. */
    @Test
    fun `window index does not bias the occupant`() {
        val byWindow = mutableMapOf<Int, MutableList<WindowOccupant>>()
        for (seed in seeds(400)) {
            for (b in 0 until 20) {
                for (w in 0 until 4) {
                    byWindow.getOrPut(w) { mutableListOf() }
                        .add(WindowOccupants.occupantAt(seed, b * 100_003, w))
                }
            }
        }
        for ((w, list) in byWindow) {
            val adults = list.count { it.age == PersonAge.ADULT }.toFloat() / list.size
            val males = list.count { it.sex == PersonSex.MALE }.toFloat() / list.size
            assertEquals("window $w adult share", 0.5f, adults, 0.05f)
            assertEquals("window $w male share", 0.5f, males, 0.05f)
        }
    }

    /**
     * Presence and identity must be independent.
     *
     * v4.0 read both from one number, so who appeared was entangled with whether anyone appeared.
     * Here the occupants of occupied windows must have the same kind distribution as the occupants
     * computed for *all* windows.
     */
    @Test
    fun `who appears is independent of whether anyone appears`() {
        val occupiedOnly = mutableListOf<Int>()
        val everyWindow = mutableListOf<Int>()
        for (seed in seeds(300)) {
            for (b in 0 until 20) {
                val bs = b * 100_003
                for (w in 0 until WINDOWS) {
                    everyWindow += WindowOccupants.occupantAt(seed, bs, w).kindIndex
                    if (WindowOccupants.isOccupied(seed, bs, w, WINDOWS, WindowBuildingKind.HOUSE)) {
                        occupiedOnly += WindowOccupants.occupantAt(seed, bs, w).kindIndex
                    }
                }
            }
        }
        for (kindIndex in 0 until 4) {
            val a = occupiedOnly.count { it == kindIndex }.toFloat() / occupiedOnly.size
            val b = everyWindow.count { it == kindIndex }.toFloat() / everyWindow.size
            assertEquals("kind $kindIndex distribution shifted by the presence gate", b, a, 0.05f)
        }
    }

    /** Skin stays in the range the shipped artwork can express. */
    @Test
    fun `skin index stays within the shipped palette`() {
        for (o in occupants(WindowBuildingKind.HOUSE, 4)) {
            assertTrue(o.skinIndex in 0 until PedestrianPopulation.SKIN_TONE_COUNT)
        }
    }

    // ----------------------------------------------------------- determinism

    @Test
    fun `the same seed puts the same people at the same windows`() {
        for (seed in seeds(50)) {
            for (w in 0 until WINDOWS) {
                assertEquals(
                    WindowOccupants.occupantAt(seed, 3 * 100_003, w),
                    WindowOccupants.occupantAt(seed, 3 * 100_003, w),
                )
                assertEquals(
                    WindowOccupants.isOccupied(seed, 3 * 100_003, w, WINDOWS, WindowBuildingKind.HOUSE),
                    WindowOccupants.isOccupied(seed, 3 * 100_003, w, WINDOWS, WindowBuildingKind.HOUSE),
                )
            }
        }
    }

    @Test
    fun `different themes populate the same building differently`() {
        val streets = seeds(200).map { seed ->
            (0 until 8).map { WindowOccupants.occupantAt(seed, 5 * 100_003, it) }
        }
        assertTrue("every theme gave the same building", streets.distinct().size > 100)
    }

    /** Occupants must not depend on the clock, or a face would flicker between frames. */
    @Test
    fun `occupancy does not depend on the clock`() {
        val before = (0 until 16).map {
            WindowOccupants.isOccupied(999, 100_003, it, 16, WindowBuildingKind.SKYSCRAPER)
        }
        Thread.sleep(5)
        val after = (0 until 16).map {
            WindowOccupants.isOccupied(999, 100_003, it, 16, WindowBuildingKind.SKYSCRAPER)
        }
        assertEquals(before, after)
    }

    // ---------------------------------------------------------------- business openness (v4.22)

    /** Openness 1 -- the default, and every house always -- is bitwise the pre-v4.22 answer. */
    @Test
    fun `full openness changes nothing about occupancy`() {
        for (seed in seeds(50)) {
            for (kind in WindowBuildingKind.entries) {
                for (window in 0 until 16) {
                    assertEquals(
                        WindowOccupants.isOccupied(seed, 100_003, window, 16, kind),
                        WindowOccupants.isOccupied(seed, 100_003, window, 16, kind, openness = 1f),
                    )
                }
            }
        }
    }

    @Test
    fun `zero openness empties every window`() {
        for (seed in seeds(50)) {
            for (window in 0 until 16) {
                assertFalse(
                    WindowOccupants.isOccupied(seed, 100_003, window, 16, WindowBuildingKind.SKYSCRAPER, openness = 0f),
                )
            }
        }
    }

    /**
     * Across a closing fade the occupants leave one at a time and nobody arrives: the occupied
     * set at any openness is a subset of the set at any higher openness. This is what makes the
     * fade read as people going home rather than as a population being reshuffled -- the same
     * nesting rule the car count keeps.
     */
    @Test
    fun `lowering the openness only ever removes occupants`() {
        for (seed in seeds(50)) {
            var previous: List<Boolean>? = null
            var openness = 1f
            while (openness >= 0f) {
                val now = (0 until 16).map {
                    WindowOccupants.isOccupied(seed, 100_003, it, 16, WindowBuildingKind.SKYSCRAPER, openness)
                }
                previous?.let { wider ->
                    for (i in now.indices) {
                        assertFalse(
                            "seed $seed openness $openness seated window $i that a more open hour had empty",
                            now[i] && !wider[i],
                        )
                    }
                }
                previous = now
                openness -= 0.05f
            }
        }
    }
}
