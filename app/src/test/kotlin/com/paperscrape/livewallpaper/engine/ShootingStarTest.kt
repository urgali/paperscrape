package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A shooting star crosses the night sky once a period, for one second, and only while the stars show**
 * ([PaperRenderer.drawShootingStar]'s rules, all pure functions of the scene clock): at most one in each period of
 * [PaperRenderer.SHOOTING_STAR_PERIOD_SECONDS], lasting [PaperRenderer.SHOOTING_STAR_SECONDS], none by day, none with
 * *Show Stars* or *Shooting stars* off, the same star for the same moment; on in every theme as it ships (decision
 * 114), and in a theme file that does not carry the switch.
 */
class ShootingStarTest {

    private val period = PaperRenderer.SHOOTING_STAR_PERIOD_SECONDS

    /** The periods swept: the first ones, where a fresh engine's clock is, and some far along. */
    private val periods: List<Long> = (0L until 60L) + listOf(1_000L, 54_321L, 9_999_999L)

    /** The moments of [index]'s period, every [STEP] s, counted rather than summed so none drifts. */
    private fun momentsOf(index: Long): Sequence<Double> {
        val steps = (period / STEP).toInt()
        return (0 until steps).asSequence().map { index * period + it * STEP }
    }

    @Test
    fun `at most one star a period, and it lasts one second`() {
        for (index in periods) {
            var runs = 0
            var inRun = false
            var samples = 0
            for (t in momentsOf(index)) {
                val p = PaperRenderer.shootingStarProgress(t)
                val crossing = p >= 0f
                if (crossing) {
                    assertTrue("period $index at $t: progress $p", p <= 1f)
                    samples++
                    if (!inRun) runs++
                }
                inRun = crossing
            }
            assertEquals("period $index: one crossing, not $runs", 1, runs)
            // One second sampled every STEP: 1 / STEP samples, one more where both ends land on a sample.
            val expected = (PaperRenderer.SHOOTING_STAR_SECONDS / STEP).toInt()
            assertTrue("period $index: the crossing took $samples samples of $STEP s", samples in expected..expected + 1)
        }
    }

    @Test
    fun `the star sets off between 15 and 75 percent of its period and ends inside it`() {
        for (index in periods) {
            val start = PaperRenderer.shootingStarStartSeconds(index)
            val into = start - index * period
            assertTrue("period $index: sets off ${into}s in", into >= 0.15 * period - 1e-6 && into <= 0.75 * period + 1e-6)
            assertTrue("period $index: ends past its period", into + PaperRenderer.SHOOTING_STAR_SECONDS < period)
            assertEquals(index, PaperRenderer.shootingStarPeriod(start))
            assertEquals("at its start it is at the start", 0f, PaperRenderer.shootingStarProgress(start), 1e-6f)
            assertEquals(0.5f, PaperRenderer.shootingStarProgress(start + 0.5 * PaperRenderer.SHOOTING_STAR_SECONDS), 1e-4f)
            assertEquals(-1f, PaperRenderer.shootingStarProgress(start - 0.01), 0f)
            assertEquals(-1f, PaperRenderer.shootingStarProgress(start + PaperRenderer.SHOOTING_STAR_SECONDS + 0.01), 0f)
        }
    }

    /**
     * The first periods' deal, pinned, so a change to the deal is seen: a fresh engine's first star 27 s in, heading
     * right from the screen's upper left; the second at 305.43 s, heading left; the third at 475.86 s, heading right.
     */
    @Test
    fun `the first periods deal the stars they always have`() {
        assertEquals(27.0, PaperRenderer.shootingStarStartSeconds(0L), 1e-9)
        assertFalse(PaperRenderer.shootingStarLeftward(0L))
        assertEquals(0.15f * 720f, PaperRenderer.shootingStarStartX(0L, 720f), 1e-3f)
        assertEquals(0.06f * 1440f, PaperRenderer.shootingStarStartY(0L, 1440f), 1e-3f)
        assertEquals(305.4286, PaperRenderer.shootingStarStartSeconds(1L), 1e-3)
        assertTrue(PaperRenderer.shootingStarLeftward(1L))
        assertEquals(475.8571, PaperRenderer.shootingStarStartSeconds(2L), 1e-3)
        assertFalse(PaperRenderer.shootingStarLeftward(2L))
    }

    @Test
    fun `the same moment draws the same star`() {
        for (index in periods) {
            val t = PaperRenderer.shootingStarStartSeconds(index) + 0.37
            assertEquals(PaperRenderer.shootingStarProgress(t), PaperRenderer.shootingStarProgress(t), 0f)
            assertEquals(PaperRenderer.shootingStarLeftward(index), PaperRenderer.shootingStarLeftward(index))
            assertEquals(PaperRenderer.shootingStarStartX(index, 720f), PaperRenderer.shootingStarStartX(index, 720f), 0f)
            assertEquals(PaperRenderer.shootingStarStartY(index, 1440f), PaperRenderer.shootingStarStartY(index, 1440f), 0f)
        }
    }

    /** Its head, from where it sets off to where it ends, stays across the screen, high in the sky. */
    @Test
    fun `the head crosses the screen and stays on it`() {
        val width = 720f
        val height = 1440f
        for (index in 0L until 2_000L) {
            val x0 = PaperRenderer.shootingStarStartX(index, width)
            val y0 = PaperRenderer.shootingStarStartY(index, height)
            val sign = if (PaperRenderer.shootingStarLeftward(index)) -1f else 1f
            val x1 = x0 + sign * PaperRenderer.SHOOTING_STAR_HEADING_X * PaperRenderer.SHOOTING_STAR_TRAVEL
            val y1 = y0 + PaperRenderer.SHOOTING_STAR_HEADING_Y * PaperRenderer.SHOOTING_STAR_TRAVEL
            for (x in listOf(x0, x1)) assertTrue("period $index: the head at x $x", x in 0f..width)
            assertTrue("period $index: the head down to y $y1", y0 >= 0f && y1 < height * 0.35f)
        }
    }

    @Test
    fun `never by day, brightest at night halfway across`() {
        for (step in 0..20) {
            val p = step / 20f
            assertEquals("by day at $p", 0f, PaperRenderer.shootingStarFade(p, dayBlend = 1f), 0f)
            // The stars' own visibility: gone from a dayBlend of 1 / 1.6 up.
            assertEquals("past dusk at $p", 0f, PaperRenderer.shootingStarFade(p, dayBlend = 0.625f), 0f)
        }
        assertEquals(1f, PaperRenderer.shootingStarFade(0.5f, dayBlend = 0f), 1e-6f)
        assertTrue(PaperRenderer.shootingStarFade(0.25f, dayBlend = 0f) in 0.7f..0.71f)
        assertEquals("outside its second", 0f, PaperRenderer.shootingStarFade(-1f, dayBlend = 0f), 0f)
        assertTrue("half as bright at dayBlend 0.3125", PaperRenderer.shootingStarFade(0.5f, dayBlend = 0.3125f) in 0.49f..0.51f)
    }

    @Test
    fun `never with Show Stars off, no star in the field, or Shooting stars off`() {
        assertTrue(PaperRenderer.shootingStarsDrawn(StarsConfig(visible = true, density = 1f, shooting = true)))
        assertFalse("Show Stars off", PaperRenderer.shootingStarsDrawn(StarsConfig(visible = false, density = 1f, shooting = true)))
        assertFalse("Shooting stars off", PaperRenderer.shootingStarsDrawn(StarsConfig(visible = true, density = 1f, shooting = false)))
        assertFalse("# of Stars at 0", PaperRenderer.shootingStarsDrawn(StarsConfig(visible = true, density = 0f, shooting = true)))
        // Below 1/70 the field holds no star, and Show Stars reads off there.
        assertFalse("too few to draw one", PaperRenderer.shootingStarsDrawn(StarsConfig(visible = true, density = 0.01f, shooting = true)))
        assertTrue(PaperRenderer.shootingStarsDrawn(StarsConfig(visible = true, density = 0.02f, shooting = true)))
    }

    @Test
    fun `on in every theme as it ships, built-in and random`() {
        assertTrue(SceneCustomization.DEFAULT.stars.shooting)
        for (theme in ThemeCatalog.ALL) {
            assertTrue("${theme.id} ships without shooting stars", defaultCustomizationFor(theme.id).stars.shooting)
        }
        // A random theme's colours need Android's HSV on the JVM: a built-in's theme under the random id.
        val randomId = "random:12345"
        assertTrue(defaultCustomizationFor(randomId, ThemeCatalog.byId("autumn").copy(id = randomId)).stars.shooting)
    }

    @Test
    fun `a theme file carries the switch, and one without it reads it on`() {
        val off = SceneCustomization.DEFAULT.copy(stars = SceneCustomization.DEFAULT.stars.copy(shooting = false))
        val json = off.toJson()
        assertFalse(json.getJSONObject("stars").getBoolean("shooting"))
        assertFalse("written off, read back off", sceneCustomizationFromJson(org.json.JSONObject(json.toString())).stars.shooting)
        assertTrue("written on, read back on", sceneCustomizationFromJson(SceneCustomization.DEFAULT.toJson()).stars.shooting)
        // A file written before the switch: the stars' object without it, or no stars' object at all.
        val withoutField = org.json.JSONObject(json.toString()).apply { getJSONObject("stars").remove("shooting") }
        val read = sceneCustomizationFromJson(withoutField).stars
        assertTrue("a file without the switch reads it on", read.shooting)
        assertEquals("and the rest of the stars as written", off.stars.copy(shooting = true), read)
        val withoutStars = org.json.JSONObject(json.toString()).apply { remove("stars") }
        assertTrue(sceneCustomizationFromJson(withoutStars).stars.shooting)
    }

    private companion object {
        const val STEP = 0.01
    }
}
