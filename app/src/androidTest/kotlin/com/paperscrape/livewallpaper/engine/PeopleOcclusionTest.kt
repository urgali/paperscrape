package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Depth order, measured on the pixels a device actually paints.
 *
 * ### Why this is not the same claim `PedestrianPopulationTest` already makes
 *
 * That class proves the *list* is sorted far-to-near. This proves the *frame* is: that the
 * renderer consumes that order, that a nearer figure lands on top of a farther one it overlaps,
 * and that a farther one never lands on top of a nearer one. A correct sort drawn by a renderer
 * that reordered, batched or z-tested differently would pass there and fail here.
 *
 * ### How a figure is isolated without a hook into the renderer
 *
 * Density is the lever. [CandidateThreshold] admits group slots in a fixed order and guarantees
 * that raising the density only ever *adds* -- the groups already on the street keep every one of
 * their attributes. So rendering the same theme at two densities gives two frames that differ by a
 * known set of figures, and comparing them attributes pixels without the test needing to know
 * where any sprite was drawn:
 *
 *  - `painted(d)` -- pixels where the frame at density `d` differs from the same frame with nobody
 *    on the street. This is exactly what the people at that density paint.
 *  - `disturbed(low, high)` -- pixels of `painted(low)` whose colour *changes* when the extra
 *    figures arrive. A pixel can only end up here if something drawn afterwards covered it.
 *
 * Reading those two together is the whole method: **who is allowed to disturb whom is the depth
 * order.**
 *
 * ### The regression this caught
 *
 * The first run of this measurement returned 153 disturbed pixels for a step whose only new figure
 * was the *farthest* on the street -- an apparent depth-order violation. It was not one. v4.1
 * staggered each pedestrian's walk frame by its index **in the depth-sorted list**, so inserting
 * one figure renumbered everybody behind it and stepped their legs to a different frame. Moving
 * the People slider by one notch re-animated the survivors, which is precisely the stability
 * `CandidateThreshold` exists to provide and which no test over the population could see. The
 * stagger now comes from a figure's own address; `aFartherFigureDisturbsNothing...` is the guard.
 */
class PeopleOcclusionTest {

    private val near = SceneSpace.PAVEMENT_NEAR_Y_FRACTION
    private val far = SceneSpace.PAVEMENT_FAR_Y_FRACTION

    private fun frame(themeId: String, density: Float): Bitmap = SceneGolden.render(
        GoldenScene(
            name = "occlusion-$themeId-$density",
            dayPhase = GoldenScene.day(),
            themeId = themeId,
            customise = {
                it.copy(
                    people = it.people.copy(visible = true, density = density),
                    peopleNightDensity = density,
                )
            },
        ),
    )

    private fun street(themeId: String, density: Float) =
        PedestrianPopulation.build(themeId.hashCode(), density, near, far)

    /** Pixels on which two frames disagree at all -- exact, because both are the same scene. */
    private fun differing(a: Bitmap, b: Bitmap): BooleanArray {
        val mask = BooleanArray(SceneGolden.WIDTH * SceneGolden.HEIGHT)
        val rowA = IntArray(SceneGolden.WIDTH)
        val rowB = IntArray(SceneGolden.WIDTH)
        for (y in 0 until SceneGolden.HEIGHT) {
            a.getPixels(rowA, 0, SceneGolden.WIDTH, 0, y, SceneGolden.WIDTH, 1)
            b.getPixels(rowB, 0, SceneGolden.WIDTH, 0, y, SceneGolden.WIDTH, 1)
            for (x in 0 until SceneGolden.WIDTH) mask[y * SceneGolden.WIDTH + x] = rowA[x] != rowB[x]
        }
        return mask
    }

    private fun countBoth(a: BooleanArray, b: BooleanArray): Int =
        a.indices.count { a[it] && b[it] }

    // ------------------------------------------------------------------- near over far

    /**
     * A nearer figure paints over a farther one it overlaps.
     *
     * `new_year` between 40% and 80% is the case that makes this measurable: the three figures
     * already on the street stand at depths 0.7850, 0.7889 and 0.7989, and every one of the three
     * that arrive stands at 0.8014 or lower down the frame -- **strictly nearer than all of them**.
     * Whatever they cover, they were entitled to cover.
     */
    @Test
    fun aNearerFigurePaintsOverAFartherOne() {
        val themeId = "new_year"
        val existing = street(themeId, 0.4f)
        val arriving = street(themeId, 0.8f).filter { p -> existing.none { it.groupIndex == p.groupIndex } }
        assertTrue("nobody arrives between 40% and 80%", arriving.isNotEmpty())
        assertTrue(
            "the arriving figures are not all nearer than the ones already there",
            arriving.minOf { it.depth } > existing.maxOf { it.depth },
        )

        val nobody = frame(themeId, 0f)
        val low = frame(themeId, 0.4f)
        val high = frame(themeId, 0.8f)
        val painted = differing(low, nobody)
        val changed = differing(high, low)
        val disturbed = countBoth(painted, changed)

        assertTrue("the street at 40% painted nothing", painted.count { it } > 100)
        assertTrue(
            "the nearer figures covered none of the farther ones -- either they do not overlap " +
                "or they were drawn behind",
            disturbed > 0,
        )
    }

    // ------------------------------------------------------------------- far never over near

    /**
     * A farther figure never paints over a nearer one.
     *
     * Swept over every theme and every density step, and applied wherever the step's arrivals are
     * **all** farther than everybody already on the street: nothing they draw may reach a pixel an
     * existing figure had painted. On the shipped catalogue that binds on `winter` between 40% and
     * 80%, where one figure arrives at depth 0.7862 behind five standing at 0.8075 and nearer --
     * the case v4.1 failed with 153 disturbed pixels.
     */
    @Test
    fun aFartherFigureDisturbsNothingAlreadyOnTheStreet() {
        var casesChecked = 0
        for (theme in ThemeCatalog.ALL) {
            var nobody: Bitmap? = null
            for ((low, high) in listOf(0.2f to 0.4f, 0.4f to 0.8f, 0.8f to 1.0f)) {
                val existing = street(theme.id, low)
                val arriving = street(theme.id, high)
                    .filter { p -> existing.none { it.groupIndex == p.groupIndex } }
                if (existing.isEmpty() || arriving.isEmpty()) continue
                if (arriving.maxOf { it.depth } >= existing.minOf { it.depth }) continue

                casesChecked++
                val empty = nobody ?: frame(theme.id, 0f).also { nobody = it }
                val lowFrame = frame(theme.id, low)
                val painted = differing(lowFrame, empty)
                val changed = differing(frame(theme.id, high), lowFrame)
                assertEquals(
                    "${theme.id} $low->$high: a figure behind everybody changed pixels they had painted",
                    0,
                    countBoth(painted, changed),
                )
            }
        }
        assertTrue("no theme offered a farther-only arrival to measure", casesChecked > 0)
    }

    // ------------------------------------------------------------------- inside one group

    /**
     * The members of one group really do overlap on the screen, and are ordered among themselves.
     *
     * Without this the two tests above would be about groups only, and the brief for this release
     * asks for the same guarantee *inside* a group -- which is where it is hardest, because a
     * group's members stand within a fraction of a tile of each other and differ in depth only by
     * the row jitter. `city` at 20% is one group of three and nothing else on the street, so the
     * painted pixels are exactly those three figures: if they form fewer than three separate
     * blobs, they are touching.
     */
    @Test
    fun theMembersOfOneGroupOverlapAndAreDrawnFarToNear() {
        val themeId = "city"
        val group = street(themeId, 0.2f)
        assertEquals("city at 20% is not one group of three", 3, group.size)
        assertEquals("more than one group survived", 1, group.map { it.groupIndex }.distinct().size)

        // The list the renderer draws is depth-ascending, inside a group exactly as between them.
        for (i in 1 until group.size) {
            assertTrue(
                "member ${group[i - 1].memberIndex} at ${group[i - 1].depth} is drawn before " +
                    "${group[i].memberIndex} at ${group[i].depth}",
                group[i].depth >= group[i - 1].depth,
            )
        }

        val painted = differing(frame(themeId, 0.2f), frame(themeId, 0f))
        assertTrue("the group painted nothing", painted.count { it } > 50)
        assertTrue(
            "three figures painted ${blobCount(painted)} separate shapes, so they do not overlap",
            blobCount(painted) < 3,
        )
    }

    /** Four-connected components of a pixel mask, counted with an explicit stack. */
    private fun blobCount(mask: BooleanArray): Int {
        val seen = BooleanArray(mask.size)
        val stack = ArrayDeque<Int>()
        var blobs = 0
        for (start in mask.indices) {
            if (!mask[start] || seen[start]) continue
            blobs++
            seen[start] = true
            stack.addLast(start)
            while (stack.isNotEmpty()) {
                val p = stack.removeLast()
                val x = p % SceneGolden.WIDTH
                val y = p / SceneGolden.WIDTH
                for ((dx, dy) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val nx = x + dx
                    val ny = y + dy
                    if (nx < 0 || ny < 0 || nx >= SceneGolden.WIDTH || ny >= SceneGolden.HEIGHT) continue
                    val q = ny * SceneGolden.WIDTH + nx
                    if (mask[q] && !seen[q]) {
                        seen[q] = true
                        stack.addLast(q)
                    }
                }
            }
        }
        return blobs
    }
}
