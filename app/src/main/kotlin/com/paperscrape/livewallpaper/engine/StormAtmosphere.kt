package com.paperscrape.livewallpaper.engine

import kotlin.math.pow

/**
 * How much the weather darkens the scene, and what that darkening does to a colour.
 *
 * **The problem this exists to solve.** Before v2.15 the forecast reached exactly two things: how
 * many cloud sprites were placed, and how many raindrops fell. The sky's colour, the clouds' colour
 * and the sun's brightness came only from the theme's palette and the time of day, with no weather
 * input at all. A heavy thunderstorm at two in the afternoon therefore rendered as bright blue sky,
 * a full sun with its rays, a band of cloud, and heavy rain — four things that cannot all be true
 * at once. Rain was falling out of a summer afternoon.
 *
 * **Why this is not the old behaviour returning.** An earlier release had clouds darken toward
 * black as their density slider climbed, and that was removed on purpose: the reference app uses a
 * flat day/night colour pair with no density blending, and density is not weather. What is
 * reinstated here is different in all three respects — it is driven by the *forecast* rather than
 * by a slider, it is a blend rather than a palette substitution, and it is derived from the theme's
 * own colour rather than from a fixed storm palette, so a theme keeps its identity while getting
 * visibly worse weather.
 *
 * Everything here is pure integer and float arithmetic returning primitives: no allocation, no
 * texture, no new draw call. The renderer calls [strength] once per frame and [dim] a handful of
 * times, on values it was already computing.
 */
object StormAtmosphere {

    // -- how strong ------------------------------------------------------------------------------

    /**
     * The share of the range that rain alone can reach.
     *
     * Rain tops out below the maximum so that a thunderstorm always reads as worse than even the
     * heaviest rain, which is the ordering [strength]'s own doc explains.
     */
    private const val RAIN_SPAN = 0.75f

    /**
     * Where a thunderstorm starts, however little is falling at this instant.
     *
     * A storm's darkness comes from the depth of the cloud above it, not from the millimetres
     * arriving in the current quarter-hour — a squall line is black before the first drop. This is
     * the one deliberate step in an otherwise continuous scale, and it is a step *between weather
     * states*, not a discontinuity inside one: within "thunderstorm" the strength still rises
     * smoothly with intensity, from this floor to the maximum.
     */
    private const val STORM_FLOOR = 0.75f

    /**
     * What a fully overcast sky is worth on its own, with nothing falling.
     *
     * Small on purpose. An overcast day is flatter and greyer than a clear one, but it is still a
     * day, and the brief asks for "normal or barely attenuated" here. It also keeps the scale
     * continuous at the bottom: clear sky, overcast, light rain, rain and heavier all step up
     * without a gap appearing at the first drop.
     */
    private const val CLOUD_SPAN = 0.10f

    /**
     * The exponent the rain's intensity is raised to before it is scaled by [RAIN_SPAN].
     *
     * **Measured, not chosen.** With the rain term linear, the six-level ramp was walked on a
     * device and the bottom half of it did not read: light rain was indistinguishable from a dry
     * overcast sky, and a rain the forecast calls moderate looked like a bright blue afternoon with
     * some drops in it. The cause is upstream and out of scope to change -- `FULL_INTENSITY_MM` is
     * 8 mm/h, a genuinely torrential rate, so the everyday 1-2 mm/h that most real forecasts report
     * lands around 0.2 of the intensity range and, multiplied linearly, produced barely more
     * darkening than cloud alone.
     *
     * Bending the response fixes that where it belongs -- in how weather maps to *appearance*,
     * which is what this object is for -- without touching what the millimetres mean. Below 1 the
     * curve lifts the low and middle of the range while pinning both ends: 0 is still 0 and 1 is
     * still [RAIN_SPAN], so nothing above is rescaled and monotonicity is untouched. The values it
     * produces are in [strength]'s own table.
     */
    private const val RAIN_RESPONSE_EXPONENT = 0.65f

    /**
     * 0 for a clear sky, 1 for the worst weather the scene can report.
     *
     * Monotone in every argument. The ordering it produces, with the intensity floor of 0.15 that
     * any precipitation carries:
     *
     * | State | intensity | strength |
     * |---|---|---|
     * | Clear | — | 0.00 |
     * | Overcast, dry | — | 0.10 |
     * | Light rain | 0.15 | 0.22 |
     * | A real 1.8 mm/h | 0.23 | 0.29 |
     * | Rain | 0.40 | 0.41 |
     * | Heavy rain | 1.00 | 0.75 |
     * | Light thunderstorm | 0.15 | 0.79 |
     * | Heavy thunderstorm | 1.00 | 1.00 |
     *
     * The middle rows are what [RAIN_RESPONSE_EXPONENT] exists for: with a linear rain term they
     * read 0.11, 0.17 and 0.30, and on a device the first two were indistinguishable from the dry
     * overcast row above them.
     *
     * **Snow is deliberately not included in the precipitation term.** The snow path was verified
     * on a device in this same release and this batch is scoped to rain, heavy rain and
     * thunderstorm; darkening a snowfall would change a presentation that is known good and was
     * not asked about. Snow still picks up the cloud term, because snow arrives under cloud, so a
     * snowy scene is mildly flattened rather than untouched.
     */
    fun strength(
        precipitationType: PrecipitationType?,
        precipitationIntensity: Float,
        isThunderstorm: Boolean,
        cloudCoverFraction: Float,
    ): Float {
        val cloud = cloudCoverFraction.coerceIn(0f, 1f) * CLOUD_SPAN
        val rainy = precipitationType == PrecipitationType.RAIN
        val intensity = precipitationIntensity.coerceIn(0f, 1f)
        val weather = when {
            isThunderstorm -> STORM_FLOOR + (1f - STORM_FLOOR) * intensity
            rainy -> RAIN_SPAN * intensity.pow(RAIN_RESPONSE_EXPONENT)
            else -> 0f
        }
        return maxOf(cloud, weather).coerceIn(0f, 1f)
    }

    // -- what it does to a colour -----------------------------------------------------------------

    /** How far toward its own luminance a colour is pulled at full strength. */
    private const val SKY_DESATURATION = 0.45f

    /** How far a colour's luminance is pulled down at full strength. */
    private const val SKY_DARKENING = 0.42f

    /** Clouds go further than the sky: a storm cloud is the darkest thing in a paper sky. */
    private const val CLOUD_DESATURATION = 0.35f
    private const val CLOUD_DARKENING = 0.52f

    /**
     * The theme's colour, weathered.
     *
     * Two moves, both relative to the colour given rather than toward any fixed storm palette:
     * pull it toward its own luminance (grey it, without choosing which grey), then pull that
     * luminance down (darken it). A theme's hue therefore survives — a warm sunset stays warm as
     * it goes dull and dark — which is what "keep the theme's chromatic identity" requires and
     * what substituting a palette could not give.
     *
     * Alpha is carried through untouched. Returns the input unchanged at zero strength, so the
     * clear-sky path is bit-for-bit what it was before this existed.
     */
    fun dim(color: Int, strength: Float, desaturation: Float, darkening: Float): Int {
        val s = strength.coerceIn(0f, 1f)
        if (s <= 0f) return color
        val a = (color ushr 24) and 0xFF
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        // Rec. 601 luma, the same weighting the rest of the app's colour work uses.
        val luma = (r * 299 + g * 587 + b * 114) / 1000f
        val desaturate = desaturation * s
        val scale = 1f - darkening * s
        val nr = ((r + (luma - r) * desaturate) * scale).toInt().coerceIn(0, 255)
        val ng = ((g + (luma - g) * desaturate) * scale).toInt().coerceIn(0, 255)
        val nb = ((b + (luma - b) * desaturate) * scale).toInt().coerceIn(0, 255)
        return (a shl 24) or (nr shl 16) or (ng shl 8) or nb
    }

    /** [dim] with the sky's own amounts. */
    fun dimSky(color: Int, strength: Float): Int = dim(color, strength, SKY_DESATURATION, SKY_DARKENING)

    /** [dim] with the clouds' own, heavier, amounts. */
    fun dimCloud(color: Int, strength: Float): Int = dim(color, strength, CLOUD_DESATURATION, CLOUD_DARKENING)

    // -- the sun -----------------------------------------------------------------------------------

    /**
     * How much of the sun is left, 1 down to [MINIMUM_SUN_VISIBILITY].
     *
     * The sun keeps its position, its arc and its part in the day/night blend; only how strongly it
     * is painted changes. It never reaches zero: a sun that vanished would turn an overcast
     * afternoon into a scene with no light source at all, which reads as night, and the brief is
     * explicit that a storm must stay recognisably daytime.
     */
    const val MINIMUM_SUN_VISIBILITY = 0.18f

    fun sunVisibility(strength: Float): Float =
        1f - (1f - MINIMUM_SUN_VISIBILITY) * strength.coerceIn(0f, 1f)

    /** The same curve applied to an alpha channel, for the glow and the two sun sprites. */
    fun sunAlpha(baseAlpha: Int, strength: Float): Int =
        (baseAlpha * sunVisibility(strength)).toInt().coerceIn(0, 255)
}
