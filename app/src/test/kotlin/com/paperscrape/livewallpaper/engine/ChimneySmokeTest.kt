package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The chimney smoke of v5.12** (inventory I-501 to I-504, I-507): from the top of the chimney, a
 * column that rises slowly and fades, light grey by day and a middle grey at night, and only where the
 * autumn or the winter palette is on. The numbers are [ChimneySmoke]'s; the draw call that reads them
 * is pinned at the bottom.
 */
class ChimneySmokeTest {

    private val lives = (0..1000).map { it / 1000f }

    @Test
    fun `a puff is born on the cap and never covers the chimney's side`() {
        // `vocab.chimney`'s cap is a card from 1.2 units above the point the table declares (the middle
        // of the chimney's top) to 0.6 below it. Until v5.12 the first puff's centre was 6 units below
        // it, on the chimney's flank (I-501).
        val capBottom = 0.6f
        val newborn = ChimneySmoke.dy(0f) + ChimneySmoke.radius(0f)
        assertTrue("a newborn puff's lower edge ($newborn) is inside the cap", newborn in -1.2f..capBottom)
        for (life in lives) {
            assertTrue("at life $life the puff's lower edge is above the cap's foot", ChimneySmoke.dy(life) + ChimneySmoke.radius(life) <= capBottom)
            assertTrue("and centred on the chimney or to its right", ChimneySmoke.dx(life) >= 0f)
        }
    }

    @Test
    fun `a puff rises, bends with the wind, grows and fades, and never pops`() {
        for (i in 1 until lives.size) {
            val a = lives[i - 1]; val b = lives[i]
            assertTrue("rises", ChimneySmoke.dy(b) < ChimneySmoke.dy(a))
            assertTrue("drifts right", ChimneySmoke.dx(b) > ChimneySmoke.dx(a))
            assertTrue("grows", ChimneySmoke.radius(b) > ChimneySmoke.radius(a))
        }
        assertEquals("transparent when born", 0f, ChimneySmoke.alpha(0f), 0f)
        assertEquals("transparent when gone", 0f, ChimneySmoke.alpha(1f), 1e-4f)
        assertEquals("rises its whole height", ChimneySmoke.SMOKE_RISE_UNITS, ChimneySmoke.dy(0f) - ChimneySmoke.dy(1f), 1e-4f)
        assertEquals("bent its whole drift", ChimneySmoke.SMOKE_DRIFT_UNITS, ChimneySmoke.dx(1f), 1e-4f)
        // No jump of more than a few alpha between two frames at 30 fps, across the whole life and its
        // wrap: the steepest is the fade-in, 190 over a second, under 7 a frame.
        val step = 1f / (30f * ChimneySmoke.PERIOD_SECONDS)
        var life = 0f
        while (life < 1f) {
            val next = (life + step) % 1f
            assertTrue("alpha jumps at $life", kotlin.math.abs(ChimneySmoke.alpha(next) - ChimneySmoke.alpha(life)) < 8f)
            life += step
        }
    }

    @Test
    fun `it moves as slowly as the scene does`() {
        // Units a second at the fastest point of a life: rise and drift together.
        var fastest = 0f
        for (i in 1 until lives.size) {
            val dl = lives[i] - lives[i - 1]
            val dx = ChimneySmoke.dx(lives[i]) - ChimneySmoke.dx(lives[i - 1])
            val dy = ChimneySmoke.dy(lives[i]) - ChimneySmoke.dy(lives[i - 1])
            fastest = maxOf(fastest, kotlin.math.sqrt(dx * dx + dy * dy) / (dl * ChimneySmoke.PERIOD_SECONDS))
        }
        assertTrue("fastest $fastest units/s: about 3, slow", fastest < 5f)
    }

    @Test
    fun `the puffs of one chimney are evenly spaced and each chimney keeps its own beat`() {
        val t = SceneTime(37.25)
        val beat = ChimneySmoke.beatOf(1.3f)
        val lifeOf = (0 until ChimneySmoke.PUFFS).map { ChimneySmoke.life(t, beat, it) }.sorted()
        for (i in 1 until lifeOf.size) assertEquals(1f / ChimneySmoke.PUFFS, lifeOf[i] - lifeOf[i - 1], 1e-4f)
        assertNotEquals("two houses do not puff in step", ChimneySmoke.life(t, ChimneySmoke.beatOf(1.3f), 0), ChimneySmoke.life(t, ChimneySmoke.beatOf(2.9f), 0), 1e-3f)
        assertEquals("a function of the scene's time alone", ChimneySmoke.life(t, beat, 2), ChimneySmoke.life(SceneTime(37.25), beat, 2), 0f)
    }

    @Test
    fun `light grey by day as it always was, a middle grey at night`() {
        assertEquals("by day the grey of before", 0xFFE4E4DC.toInt(), ChimneySmoke.colour(1f))
        assertEquals("at night the middle grey", 0xFF9AA0AA.toInt(), ChimneySmoke.colour(0f))
        // On the scene's ramp: every channel moves one way from noon to midnight.
        var previous = ChimneySmoke.colour(1f)
        for (i in 9 downTo 0) {
            val c = ChimneySmoke.colour(i / 10f)
            for (shift in listOf(16, 8, 0)) assertTrue("darker toward night", (c ushr shift and 0xFF) <= (previous ushr shift and 0xFF))
            previous = c
        }
    }

    @Test
    fun `it smokes only with the autumn or the winter palette`() {
        val base = SceneCustomization.DEFAULT
        assertFalse(ChimneySmoke.smokes(base.copy(fallColorsEnabled = false, winterColorsEnabled = false)))
        assertTrue(ChimneySmoke.smokes(base.copy(fallColorsEnabled = true, winterColorsEnabled = false)))
        assertTrue(ChimneySmoke.smokes(base.copy(fallColorsEnabled = false, winterColorsEnabled = true)))
        // As the twelve themes ship: the five the table named smoke, the seven it named do not.
        val smoking = ThemeCatalog.ALL.filter { ChimneySmoke.smokes(defaultCustomizationFor(it.id)) }.map { it.id }.toSet()
        assertEquals(setOf("autumn", "winter", "christmas", "new_year", "tundra"), smoking)
    }

    @Test
    fun `the wallpaper draws the smoke from these numbers, at the scene's time, and only where it smokes`() {
        val renderer = source()
        val body = renderer.substringAfter("private fun drawChimneySmoke(").substringBefore("\n    }\n")
        assertTrue(body.contains("fillPaint.color = ChimneySmoke.colour(dayBlend)"))
        assertTrue(body.contains("val life = ChimneySmoke.life(elapsed, beat, puff)"))
        assertTrue(body.contains("canvas.drawCircle(x + ChimneySmoke.dx(life), topY + ChimneySmoke.dy(life), ChimneySmoke.radius(life), fillPaint)"))
        val building = renderer.substringAfter("private fun drawNeighbourhoodBuilding(").substringBefore("\n    private fun ")
        assertTrue(building.contains("val smokes = ChimneySmoke.smokes(customization)"))
        assertTrue(building.contains("if (smokes && family.kind == WindowBuildingKind.HOUSE && piece.smokeY != 0f)"))
        assertTrue(building.contains("drawChimneySmoke(canvas, r, elapsed, dayBlend, x = piece.smokeX, topY = placed.baseY + piece.smokeY)"))
    }

    private fun source(): String {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("SceneObjectRenderer.kt not found")
    }
}
