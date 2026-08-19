package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [CloudCoverage] and for the rain-follows-cloud rule built on it.
 *
 * The renderer itself needs a `Canvas`, so these exercise the coverage field directly and then
 * reproduce the exact presence test `drawPrecipitation` performs —
 * `intensity × coverage(x)` fed to the unchanged [CandidateThreshold.isPresent] — over the real
 * candidate positions. That is the whole of the new logic; everything else in the draw function
 * was left alone.
 */
class CloudCoverageTest {

    private val screenWidth = 1080f
    private val precipitationOffset = CandidateThreshold.offsetFor(EffectId.PRECIPITATION)
    private val precipitationSeed = "sunset".hashCode() xor (EffectId.PRECIPITATION * -0x61c88647)

    /** Base x of precipitation candidate [i], exactly as the renderer computes it. */
    private fun dropX(i: Int): Float =
        CandidateNoise.value(precipitationSeed, i, CandidateNoise.CH_X) * screenWidth

    /** The drops the renderer would draw, given a coverage field and an intensity. */
    private fun drops(coverage: CloudCoverage, intensity: Float): List<Int> {
        val fallback = CandidateThreshold.fallbackIndexFor(
            intensity, PaperRenderer.PRECIPITATION_POOL_SIZE, precipitationOffset,
        )
        return (0 until PaperRenderer.PRECIPITATION_POOL_SIZE).filter { i ->
            val local = intensity * coverage.at(dropX(i), screenWidth)
            local > 0f && CandidateThreshold.isPresent(i, local, precipitationOffset, fallback)
        }
    }

    /** A coverage field with clouds evenly covering [fraction] of the width. */
    private fun coverageCovering(fraction: Float): CloudCoverage {
        val coverage = CloudCoverage()
        coverage.beginFrame()
        if (fraction <= 0f) return coverage
        val halfWidth = screenWidth * fraction / 2f
        coverage.addCloud(screenWidth / 2f, halfWidth * 0.6f, halfWidth, screenWidth)
        return coverage
    }

    // --- The field itself -------------------------------------------------------------------

    @Test
    fun `a fresh frame is completely clear`() {
        val coverage = CloudCoverage()
        coverage.beginFrame()
        for (x in 0..1080 step 10) assertEquals(0f, coverage.at(x.toFloat(), screenWidth), 0f)
        assertFalse(coverage.isUniform())
    }

    @Test
    fun `coverage peaks at a cloud's centre and falls to zero at its edge`() {
        val coverage = CloudCoverage()
        coverage.beginFrame()
        coverage.addCloud(centerX = 540f, coreHalfWidth = 120f, spreadHalfWidth = 200f, screenWidth = screenWidth)

        assertTrue("centre should be near full", coverage.at(540f, screenWidth) > 0.95f)
        assertEquals("inside the silhouette should be full", 1f, coverage.at(600f, screenWidth), 0f)
        assertTrue("the margin should be partial", coverage.at(720f, screenWidth) in 0.1f..0.9f)
        assertEquals("beyond the span should be clear", 0f, coverage.at(60f, screenWidth), 0f)
        assertEquals("beyond the span should be clear", 0f, coverage.at(1020f, screenWidth), 0f)
    }

    @Test
    fun `coverage falls off monotonically away from a cloud centre`() {
        // This gradual falloff is what softens the edge without any diffuse floor.
        val coverage = CloudCoverage()
        coverage.beginFrame()
        coverage.addCloud(540f, 150f, 240f, screenWidth)
        var previous = coverage.at(540f, screenWidth)
        var x = 540f
        while (x <= 800f) {
            val current = coverage.at(x, screenWidth)
            assertTrue("coverage rose again at x=$x", current <= previous + 1e-5f)
            previous = current
            x += 10f
        }
        assertEquals(0f, coverage.at(800f, screenWidth), 0f)
    }

    @Test
    fun `overlapping clouds never exceed full coverage`() {
        val coverage = CloudCoverage()
        coverage.beginFrame()
        repeat(12) { coverage.addCloud(500f + it * 8f, 140f, 220f, screenWidth) }
        for (column in 0 until coverage.columns()) {
            assertTrue("column $column exceeded 1", coverage.columnValue(column) <= 1f)
        }
    }

    @Test
    fun `clouds off the edges do not wrap around`() {
        val coverage = CloudCoverage()
        coverage.beginFrame()
        coverage.addCloud(-300f, 90f, 150f, screenWidth)
        coverage.addCloud(1400f, 90f, 150f, screenWidth)
        for (x in 0..1080 step 20) assertEquals(0f, coverage.at(x.toFloat(), screenWidth), 0f)
    }

    @Test
    fun `sampling is clamped at both ends`() {
        val coverage = CloudCoverage()
        coverage.beginFrame()
        coverage.addCloud(540f, 400f, 600f, screenWidth)
        coverage.at(-5000f, screenWidth)
        coverage.at(50_000f, screenWidth)
        assertEquals(0f, coverage.at(0f, 0f), 0f)
    }

    @Test
    fun `degenerate clouds are ignored`() {
        val coverage = CloudCoverage()
        coverage.beginFrame()
        coverage.addCloud(540f, 0f, 0f, screenWidth)
        coverage.addCloud(540f, -50f, -50f, screenWidth)
        coverage.addCloud(540f, 60f, 100f, 0f)
        for (x in 0..1080 step 20) assertEquals(0f, coverage.at(x.toFloat(), screenWidth), 0f)
    }

    // --- Requirement 1: clouds visible at zero density means no precipitation -------------------

    @Test
    fun `no clouds means no precipitation`() {
        val coverage = coverageCovering(0f)
        assertTrue("clear sky must produce no drops", drops(coverage, 1f).isEmpty())
        assertTrue(drops(coverage, 0.6f).isEmpty())
        assertTrue(drops(coverage, 0.01f).isEmpty())
    }

    @Test
    fun `the at-least-one guarantee never overrides clear sky`() {
        // D7 keeps a category visible at low settings, but "no rain from a clear sky" wins: the
        // forced candidate is still skipped when its own position has zero coverage.
        val coverage = coverageCovering(0f)
        for (intensity in listOf(0.001f, 0.01f, 0.5f, 1f)) {
            assertTrue(
                "intensity $intensity produced rain from a clear sky",
                drops(coverage, intensity).isEmpty(),
            )
        }
    }

    // --- Requirement 3: clouds switched off falls back to uniform ---------------------------------

    @Test
    fun `hiding the cloud layer falls back to full coverage`() {
        val coverage = CloudCoverage()
        coverage.beginFrame()
        coverage.setUniform()
        assertTrue(coverage.isUniform())
        for (x in 0..1080 step 20) assertEquals(1f, coverage.at(x.toFloat(), screenWidth), 0f)
    }

    @Test
    fun `hiding the cloud layer leaves precipitation exactly as it was`() {
        // Turning clouds off must not turn rain off.
        val uniform = CloudCoverage()
        uniform.beginFrame()
        uniform.setUniform()
        for (intensity in listOf(0.1f, 0.35f, 0.6f, 1f)) {
            assertEquals(
                "intensity $intensity changed when clouds were hidden",
                referenceDrops(intensity),
                drops(uniform, intensity),
            )
        }
    }

    @Test
    fun `beginFrame clears the uniform fallback`() {
        val coverage = CloudCoverage()
        coverage.beginFrame()
        coverage.setUniform()
        coverage.beginFrame()
        assertFalse(coverage.isUniform())
        assertEquals(0f, coverage.at(540f, screenWidth), 0f)
    }

    /** What the previous build drew: presence decided by intensity alone. */
    private fun referenceDrops(intensity: Float): List<Int> {
        val fallback = CandidateThreshold.fallbackIndexFor(
            intensity, PaperRenderer.PRECIPITATION_POOL_SIZE, precipitationOffset,
        )
        return (0 until PaperRenderer.PRECIPITATION_POOL_SIZE).filter {
            CandidateThreshold.isPresent(it, intensity, precipitationOffset, fallback)
        }
    }

    // --- Requirement 4: full coverage matches the previous behaviour --------------------------------

    @Test
    fun `full coverage reproduces the previous drop set exactly`() {
        val coverage = CloudCoverage()
        coverage.beginFrame()
        // Enough overlapping clouds to saturate the width.
        var centre = -100f
        while (centre <= screenWidth + 100f) {
            coverage.addCloud(centre, 150f, 240f, screenWidth)
            centre += 120f
        }
        for (x in 0..1080 step 20) {
            assertTrue("column at $x not saturated", coverage.at(x.toFloat(), screenWidth) > 0.99f)
        }
        for (intensity in listOf(0.2f, 0.6f, 1f)) {
            assertEquals(
                "overcast sky changed at intensity $intensity",
                referenceDrops(intensity),
                drops(coverage, intensity),
            )
        }
    }

    // --- Requirement 2: coherence across cloud densities ----------------------------------------------

    @Test
    fun `more cloud cover means more precipitation`() {
        var previous = -1
        for (fraction in listOf(0f, 0.25f, 0.5f, 0.75f, 1f)) {
            val count = drops(coverageCovering(fraction), 0.6f).size
            assertTrue("cover $fraction gave $count drops, previous step gave $previous", count >= previous)
            previous = count
        }
        assertTrue("full cover should produce a substantial field", previous > 20)
    }

    @Test
    fun `every drop falls under some cloud`() {
        val coverage = coverageCovering(0.4f)
        for (i in drops(coverage, 0.8f)) {
            assertTrue(
                "drop $i falls at x=${dropX(i)} where coverage is zero",
                coverage.at(dropX(i), screenWidth) > 0f,
            )
        }
    }

    // --- Requirement 5: no teleport ----------------------------------------------------------------------

    @Test
    fun `changing cloud cover never moves a surviving drop`() {
        // The Phase 2.1/2.2 guarantee has to survive this change: coverage decides existence only,
        // never position, speed or phase.
        val attributes = mutableMapOf<Int, List<Float>>()
        var moved = 0
        var observations = 0
        for (step in 0..20) {
            val coverage = coverageCovering(step / 20f)
            for (i in drops(coverage, 0.6f)) {
                val attrs = listOf(
                    CandidateNoise.value(precipitationSeed, i, CandidateNoise.CH_X),
                    CandidateNoise.value(precipitationSeed, i, CandidateNoise.CH_PHASE),
                    CandidateNoise.range(precipitationSeed, i, CandidateNoise.CH_VARIANCE, 0.7f, 1.3f),
                )
                attributes[i]?.let {
                    observations++
                    if (it != attrs) moved++
                }
                attributes[i] = attrs
            }
        }
        assertTrue("expected many observations, got $observations", observations > 100)
        assertEquals("$moved of $observations drops moved", 0, moved)
    }

    @Test
    fun `raising cloud cover only adds drops`() {
        var previous = drops(coverageCovering(0f), 0.6f).toSet()
        for (step in 1..20) {
            val current = drops(coverageCovering(step / 20f), 0.6f).toSet()
            assertTrue(
                "cover ${step / 20f} lost drops ${previous - current}",
                current.containsAll(previous),
            )
            previous = current
        }
    }

    // --- Requirement 6: determinism ------------------------------------------------------------------------

    @Test
    fun `the same cloud field always produces the same drops`() {
        repeat(20) {
            assertEquals(drops(coverageCovering(0.45f), 0.6f), drops(coverageCovering(0.45f), 0.6f))
        }
    }

    @Test
    fun `intensity still governs under a fixed cloud field`() {
        val coverage = coverageCovering(1f)
        val low = drops(coverage, 0.2f).size
        val high = drops(coverage, 0.9f).size
        assertTrue("intensity stopped mattering: $low vs $high", high > low)
        assertNotEquals(low, high)
    }
}
