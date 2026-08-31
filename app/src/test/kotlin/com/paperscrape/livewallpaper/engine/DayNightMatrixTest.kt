package com.paperscrape.livewallpaper.engine

import kotlin.math.abs
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The derived night palette, checked across the whole range of colours a theme can hold.
 *
 * ### Why a matrix and not more single cases
 *
 * v4.13 set the night factors from one colour -- a white Christmas hill -- and shipped a night
 * that was still too light everywhere else, because a factor tuned on one sample says nothing
 * about the rest of the space. The cases below are the kinds of surface the twelve built-in themes
 * actually contain: near-white snow, warm cream, foliage, a saturated red house, brick, glass,
 * asphalt, water, and the two achromatic ends. A factor that satisfies all of them at once is a
 * factor, not a fit.
 *
 * The bands are read off the themes' own authored night colours, which sit at L* 10.9 to 29.6, so
 * "believable night" here means literally "where the artist put it".
 */
class DayNightMatrixTest {

    /** day colour to the kind of surface it stands for, for readable failures. */
    private val daylight = linkedMapOf(
        0xFFF3F7FB.toInt() to "neve quasi bianca",
        0xFFEFE4CF.toInt() to "crema calda",
        0xFFB8E0A0.toInt() to "verde chiaro",
        0xFFF2A65A.toInt() to "arancio",
        0xFFE03A2F.toInt() to "rosso saturo",
        0xFFB3453A.toInt() to "mattone",
        0xFFF2D06B.toInt() to "giallo",
        0xFF7FB3D5.toInt() to "azzurro",
        0xFF5B6270.toInt() to "grigio-blu",
        0xFF2E86AB.toInt() to "acqua",
        0xFF2F6B3A.toInt() to "verde scuro",
    )

    @Test
    fun `a daylight colour lands in the band the themes author their nights in`() {
        for ((day, name) in daylight) {
            val night = DayNightColor.lightnessOf(DayNightColor.nightFromDay(day))
            assertTrue(
                "$name: L* $night is outside the authored night band 8..32",
                night in 8f..32f,
            )
        }
    }

    @Test
    fun `night is always darker than day, and never by so little that it reads as dusk`() {
        for ((day, name) in daylight) {
            val before = DayNightColor.lightnessOf(day)
            val after = DayNightColor.lightnessOf(DayNightColor.nightFromDay(day))
            assertTrue("$name: night L* $after is not below day L* $before", after < before)
            assertTrue(
                "$name: only ${before - after} L* darker -- that is a dimmer, not a night",
                before - after >= 20f,
            )
        }
    }

    @Test
    fun `a chromatic colour keeps its hue overnight`() {
        // The point of doing this in Lab. A red house must still be a red house at night; what it
        // must stop being is a *bright* red house. Achromatic colours are excluded because hue is
        // meaningless there -- pure grey's hue is numerical noise and swings tens of degrees.
        for ((day, name) in daylight) {
            if (DayNightColor.chromaOf(day) < 15f) continue
            val night = DayNightColor.nightFromDay(day)
            var drift = abs(DayNightColor.hueOf(night) - DayNightColor.hueOf(day)) % 360f
            if (drift > 180f) drift = 360f - drift
            assertTrue("$name: hue moved $drift degrees overnight", drift <= 12f)
        }
    }

    @Test
    fun `a colour loses saturation overnight, not only lightness`() {
        // Mutation testing put this here: setting NIGHT_CHROMA_FACTOR to 1.0 left the whole matrix
        // green. The reason is that the strongest colours are pulled back into gamut anyway on the
        // way down, so the saturated cases hide the factor -- it is the *middling* ones, the ones
        // that stay in gamut at their night lightness, that actually show whether it is applied.
        //
        // Night reads as night partly because colour vision gives out: a red roof at midnight is a
        // dark roof that is slightly red. Dropping L* alone leaves a fully saturated colour that
        // has merely been dimmed, which is the "coloured photo taken in the dark" look.
        for ((day, name) in daylight) {
            val before = DayNightColor.chromaOf(day)
            if (before < 15f) continue
            val after = DayNightColor.chromaOf(DayNightColor.nightFromDay(day))
            assertTrue(
                "$name: chroma went $before -> $after, which is not a night's worth of desaturation",
                after <= before * 0.85f,
            )
        }
    }

    @Test
    fun `a brighter day is a brighter night, with no crossings`() {
        // Ordering is what keeps a scene readable: if two surfaces swap places in lightness the
        // picture reorganises itself after dark. Sorting by day L* must sort by night L* too.
        val byDay = daylight.keys.sortedBy { DayNightColor.lightnessOf(it) }
        var previous = -1f
        for (day in byDay) {
            val night = DayNightColor.lightnessOf(DayNightColor.nightFromDay(day))
            assertTrue(
                "${daylight[day]}: night L* $night fell below the surface below it ($previous)",
                night >= previous - 0.5f,
            )
            previous = night
        }
    }

    @Test
    fun `the reverse direction undoes the lightness the forward one applied`() {
        // FROM_NIGHT is the same map read backwards, so an authored night must come back out at
        // roughly L* / NIGHT_LIGHTNESS_FACTOR. Colours that would need L* above 100 are the
        // documented gamut case and are excluded by the band chosen here.
        val nights = listOf(0xFF2A3242, 0xFF4A1F1C, 0xFF1B2130, 0xFF13324A, 0xFF15301C).map { it.toInt() }
        for (night in nights) {
            val expected = DayNightColor.lightnessOf(night) / DayNightColor.NIGHT_LIGHTNESS_FACTOR
            val actual = DayNightColor.lightnessOf(DayNightColor.dayFromNight(night))
            assertTrue(
                "expected about L* $expected coming back into daylight, got $actual",
                abs(actual - expected) <= 4f,
            )
        }
    }

    @Test
    fun `the two achromatic ends stay put`() {
        // Black has nowhere to go and must not acquire a colour; white is the case that broke the
        // first attempt at the reverse mapper in v4.13.
        val black = DayNightColor.nightFromDay(0xFF000000.toInt())
        assertTrue("black must stay black, got #%06X".format(black and 0xFFFFFF), black == 0xFF000000.toInt())
        val whiteNight = DayNightColor.lightnessOf(DayNightColor.nightFromDay(0xFFFFFFFF.toInt()))
        assertTrue("white's night at L* $whiteNight is outside the authored band", whiteNight in 8f..32f)
    }
}
