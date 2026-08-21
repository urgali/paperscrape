package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The weather's effect on the sky, the clouds and the sun.
 *
 * The property under test throughout is an *ordering*, not a set of numbers. What the brief asks
 * for is that worse weather always looks worse and never looks like a different scene: a clear sky
 * is untouched, a storm is the darkest thing the scale can produce, everything between rises
 * smoothly, and none of it turns an afternoon into a night or a theme into a different theme.
 */
class StormAtmosphereTest {

    private fun rain(intensity: Float, cloud: Float = 1f) =
        StormAtmosphere.strength(PrecipitationType.RAIN, intensity, false, cloud)

    private fun storm(intensity: Float, cloud: Float = 1f) =
        StormAtmosphere.strength(PrecipitationType.RAIN, intensity, true, cloud)

    private fun dry(cloud: Float) = StormAtmosphere.strength(null, 0f, false, cloud)

    // -- the scale ---------------------------------------------------------------------------------

    @Test
    fun `a clear sky is untouched`() {
        assertEquals(0f, dry(0f), 0.0001f)
    }

    @Test
    fun `an overcast dry sky is barely attenuated`() {
        val overcast = dry(1f)
        assertTrue("overcast should register at all", overcast > 0f)
        assertTrue("an overcast day is still a day", overcast <= 0.15f)
    }

    @Test
    fun `rain darkens, heavier rain darkens more, and a storm is the worst of all`() {
        val light = rain(0.15f)
        val moderate = rain(0.40f)
        val heavy = rain(1f)

        assertTrue("light rain must be worse than a dry overcast sky", light > dry(1f))
        assertTrue(moderate > light)
        assertTrue(heavy > moderate)
        assertTrue("a storm must be worse than the heaviest rain", storm(0.15f) > heavy)
        assertEquals("a full storm is the top of the scale", 1f, storm(1f), 0.0001f)
    }

    /**
     * The correction a device run forced. With a linear rain term, light rain landed on 0.11
     * against a dry overcast sky's 0.10 -- a difference nobody could see -- and an everyday
     * 1.8 mm/h reading landed on 0.17. Both now clear the overcast sky by a visible margin.
     */
    @Test
    fun `everyday rain is visibly worse than a dry overcast sky`() {
        val overcast = dry(1f)
        // 1.8 mm/h against WeatherSnapshotMapper's 8 mm/h full scale.
        val everyday = rain(1.8f / 8f)
        assertTrue("an ordinary rain reads as no worse than dry cloud", everyday - overcast >= 0.15f)
        assertTrue("...but is still nowhere near a storm", everyday < storm(0f))
    }

    @Test
    fun `the scale is monotone in intensity with no jump inside a state`() {
        var previous = -1f
        var largestStep = 0f
        for (i in 0..100) {
            val value = rain(i / 100f)
            assertTrue("not monotone at $i", value >= previous)
            if (previous >= 0f) largestStep = maxOf(largestStep, value - previous)
            previous = value
        }
        assertTrue("a 1 % change in intensity should not jump the scale", largestStep < 0.06f)
    }

    @Test
    fun `the scale is monotone in cloud cover`() {
        var previous = -1f
        for (i in 0..100) {
            val value = dry(i / 100f)
            assertTrue("not monotone at $i", value >= previous)
            previous = value
        }
    }

    @Test
    fun `a storm rises smoothly from its own floor`() {
        var previous = -1f
        for (i in 0..100) {
            val value = storm(i / 100f)
            assertTrue("not monotone at $i", value >= previous)
            previous = value
        }
        // The floor meets the heaviest rain rather than clearing it, and any storm that is
        // actually reported passes it: `isThunderstorm` requires precipitation, and the mapper's
        // own floor puts that at 0.15 at the very least, so a zero-intensity storm is not a state
        // the providers can produce.
        assertTrue("the storm floor must at least meet the heaviest rain", storm(0f) >= rain(1f))
        assertTrue("any reportable storm must clear it", storm(0.15f) > rain(1f))
    }

    /**
     * Snow keeps the presentation verified on a device earlier in this same release: it picks up
     * the cloud term, because snow arrives under cloud, and nothing more.
     */
    @Test
    fun `snow is flattened by its cloud but not darkened as rain`() {
        val snow = StormAtmosphere.strength(PrecipitationType.SNOW, 1f, false, 1f)
        assertEquals(dry(1f), snow, 0.0001f)
        assertTrue(snow < rain(1f))
    }

    @Test
    fun `out of range inputs are clamped rather than extrapolated`() {
        assertEquals(rain(1f), rain(5f), 0.0001f)
        assertEquals(dry(1f), dry(5f), 0.0001f)
        assertEquals(dry(0f), dry(-5f), 0.0001f)
    }

    // -- what it does to a colour ---------------------------------------------------------------------

    private val skyBlue = 0xFF4FA3E3.toInt()
    private val sunsetOrange = 0xFFE8703A.toInt()

    private fun red(c: Int) = (c shr 16) and 0xFF
    private fun green(c: Int) = (c shr 8) and 0xFF
    private fun blue(c: Int) = c and 0xFF
    private fun luma(c: Int) = (red(c) * 299 + green(c) * 587 + blue(c) * 114) / 1000f

    @Test
    fun `zero strength returns the colour bit for bit`() {
        assertEquals(skyBlue, StormAtmosphere.dimSky(skyBlue, 0f))
        assertEquals(skyBlue, StormAtmosphere.dimCloud(skyBlue, 0f))
    }

    @Test
    fun `the sky gets darker and less saturated as the weather worsens`() {
        var previousLuma = Float.MAX_VALUE
        var previousSpread = Float.MAX_VALUE
        for (i in 0..20) {
            val dimmed = StormAtmosphere.dimSky(skyBlue, i / 20f)
            val spread = maxOf(red(dimmed), green(dimmed), blue(dimmed)) -
                minOf(red(dimmed), green(dimmed), blue(dimmed))
            assertTrue("luminance rose at $i", luma(dimmed) <= previousLuma + 0.001f)
            assertTrue("saturation rose at $i", spread <= previousSpread)
            previousLuma = luma(dimmed)
            previousSpread = spread.toFloat()
        }
    }

    /** A blend, not a palette substitution: the theme's hue has to survive it. */
    @Test
    fun `a theme keeps its own hue under the worst weather`() {
        val stormySunset = StormAtmosphere.dimSky(sunsetOrange, 1f)
        assertTrue("a warm sky must stay warm", red(stormySunset) > blue(stormySunset))

        val stormyBlue = StormAtmosphere.dimSky(skyBlue, 1f)
        assertTrue("a cool sky must stay cool", blue(stormyBlue) > red(stormyBlue))

        // And the two must not have converged on one storm colour.
        assertTrue(red(stormySunset) != red(stormyBlue) || blue(stormySunset) != blue(stormyBlue))
    }

    @Test
    fun `clouds go darker than the sky they hang in`() {
        for (i in 1..20) {
            val s = i / 20f
            assertTrue(
                "cloud not darker than sky at $s",
                luma(StormAtmosphere.dimCloud(skyBlue, s)) < luma(StormAtmosphere.dimSky(skyBlue, s)),
            )
        }
    }

    /** No black clouds, and no black sky: this is a blend, and the scene stays legible. */
    @Test
    fun `nothing is driven to black`() {
        assertTrue(luma(StormAtmosphere.dimSky(skyBlue, 1f)) > 40f)
        assertTrue(luma(StormAtmosphere.dimCloud(skyBlue, 1f)) > 30f)
    }

    @Test
    fun `alpha is carried through untouched`() {
        val translucent = 0x80FFAA33.toInt()
        assertEquals(0x80, (StormAtmosphere.dimSky(translucent, 1f) ushr 24) and 0xFF)
        assertEquals(0x80, (StormAtmosphere.dimCloud(translucent, 0.5f) ushr 24) and 0xFF)
    }

    // -- the sun --------------------------------------------------------------------------------------

    @Test
    fun `the sun dims progressively and never goes out`() {
        var previous = Float.MAX_VALUE
        for (i in 0..20) {
            val visibility = StormAtmosphere.sunVisibility(i / 20f)
            assertTrue("visibility rose at $i", visibility <= previous)
            assertTrue("the sun went out at $i", visibility >= StormAtmosphere.MINIMUM_SUN_VISIBILITY)
            previous = visibility
        }
        assertEquals(1f, StormAtmosphere.sunVisibility(0f), 0.0001f)
        assertEquals(StormAtmosphere.MINIMUM_SUN_VISIBILITY, StormAtmosphere.sunVisibility(1f), 0.0001f)
    }

    /**
     * The line the brief draws: a storm must stay recognisably daytime. A scene with no light
     * source at all reads as night, so the worst weather still leaves a sun on screen.
     */
    @Test
    fun `the worst weather still leaves a visible sun`() {
        assertTrue(StormAtmosphere.sunAlpha(255, 1f) > 0)
        assertTrue("a storm must not read as night", StormAtmosphere.sunAlpha(255, 1f) >= 40)
    }

    @Test
    fun `sun alpha stays inside the channel`() {
        for (i in 0..20) {
            val alpha = StormAtmosphere.sunAlpha(255, i / 20f)
            assertTrue(alpha in 0..255)
        }
        assertEquals(255, StormAtmosphere.sunAlpha(255, 0f))
        assertEquals(0, StormAtmosphere.sunAlpha(0, 0.5f))
    }
}
