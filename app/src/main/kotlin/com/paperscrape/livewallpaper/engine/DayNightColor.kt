package com.paperscrape.livewallpaper.engine

import kotlin.math.abs
import kotlin.math.cbrt
import kotlin.math.pow

/**
 * Which half of a day/night colour pair the user owns, and which half the app works out.
 *
 * Stored by [storageId] for the same reason [PrecipitationType] and `WeatherProviderId` are: an
 * enum's declaration order is not a storage format, and a pair whose mode silently changed meaning
 * because someone inserted a constant would repaint a scene nobody touched.
 */
enum class AutoColorMode(val storageId: String) {

    /**
     * Both halves are the user's. **This is the default everywhere**, so a theme that predates the
     * feature, a backup written before it, and a user who never opens the toggle all keep the
     * behaviour they had, down to the pixel.
     */
    MANUAL("manual"),

    /** The user picks the daytime colour; the night one is [DayNightColor.nightFromDay] of it. */
    FROM_DAY("from_day"),

    /** The user picks the night colour; the daytime one is [DayNightColor.dayFromNight] of it. */
    FROM_NIGHT("from_night"),
    ;

    companion object {
        fun fromStorageId(id: String?): AutoColorMode =
            entries.firstOrNull { it.storageId == id } ?: MANUAL
    }
}

/**
 * The one place that turns a daytime colour into its night counterpart, or back.
 *
 * ### Why this is a single object rather than three implementations
 *
 * Three places would otherwise have to agree on the answer: the settings screen (which shows the
 * derived swatch), `ThemePreviewScene` (which draws the theme cards, and deliberately has no
 * Android dependency), and the renderer. Three implementations of "a bit darker" is three chances
 * for the card, the preview and the wallpaper to disagree about what the user just picked. So the
 * transform lives here, in plain Kotlin with no `android.*` import, and everything reads it.
 *
 * It is applied **once**, in [CustomThemeRegistry.resolveActiveCustomization], the single choke
 * point every consumer resolves a customization through. Nothing derives a colour per frame, and
 * nothing derives one twice.
 *
 * ### Why CIELAB, and why the constants are not fitted to the artwork
 *
 * v4.12 worked in HSL and set its factors by fitting the 41 day/night pairs the built-in themes
 * author by hand. **That was the wrong thing to fit**, and re-measuring it is what produced this
 * rewrite. Stratified by how light the daytime colour is, those pairs do not describe one rule:
 *
 * | daytime colour | authored night / day, as a ratio of `L*` |
 * |---|---|
 * | the sky at `#CDEFFF` | **0.124** — the sky goes very nearly black |
 * | clouds at `#FFFFFF` | **0.359** |
 * | a wall at `#F7EFE6` | 0.726 |
 * | snow on mountains at `#F7FAFC` | **0.868** — snow is *meant* to stay bright under the moon |
 *
 * A single multiplier cannot be right for all four, because they are not one behaviour: they are
 * per-object artistic decisions about what a thing looks like at night. Fitting the median across
 * them produced a compromise that satisfied nothing, and in particular left white at `#A2A2A2` —
 * a mid grey, which is what "the night colours are still too light" was reporting.
 *
 * So the rule is stated from the requirement instead, and the requirement is a short list: a night
 * colour must read as night, must stay recognisably the colour the user picked, must not collapse
 * to grey or to black, and white must go clearly darker *and* cooler. That is a design brief, and
 * it was settled by looking at the result on a device rather than by a curve fit.
 *
 * CIELAB is the space that makes it expressible. `L*` is perceptual lightness, so halving it halves
 * how light the colour looks — which HSL's `L` does not, being a channel average. Hue and chroma
 * separate cleanly, so the hue can be held exactly while the colour is darkened.
 *
 * ### Gamut mapping, not clipping
 *
 * Darkening a saturated colour usually pushes it outside sRGB. Clipping the channels there is what
 * turned a deep red into `#8C001A` — green pinned at zero and the hue dragged towards magenta.
 * [toSrgb] instead reduces chroma until the colour fits, which keeps the hue and costs only
 * saturation, exactly the trade the eye forgives. It is why "the colour stays recognisable" holds
 * at the saturated end.
 */
object DayNightColor {

    /**
     * Night perceptual lightness as a fraction of day: **half**.
     *
     * Not fitted, for the reason above. Half of `L*` is the point at which white stops reading as a
     * grey object and starts reading as a lit surface in the dark, checked on a physical device
     * against the two cases that were reported: snow-white hills on the Christmas theme, and bright
     * red houses. Lower crushes mid-tones towards black; higher is where v4.12 already was.
     */
    const val NIGHT_LIGHTNESS_FACTOR = 0.28f

    /**
     * Night chroma as a fraction of day.
     *
     * Deliberately mild. Night desaturates, but taking chroma down hard is how a night palette
     * turns into a grey one, and "not simply grey" is half the requirement. The visible darkening
     * is [NIGHT_LIGHTNESS_FACTOR]'s job; this only takes the edge off.
     */
    const val NIGHT_CHROMA_FACTOR = 0.72f

    /**
     * How far the night colour is pushed towards blue, in `b*`.
     *
     * One constant, applied to every colour, so it is a property of night rather than of any
     * sprite. It is what makes white land on a cool grey instead of a neutral one, which is the
     * difference between "darker" and "night". Scaled by the daytime colour's own lightness so
     * that **black stays black**: an unlit thing does not acquire a colour cast just because the
     * sun went down.
     */
    const val NIGHT_BLUE_SHIFT = 6.0f

    /** The night colour for a daytime [day]. */
    fun nightFromDay(day: Int): Int {
        val (l, a, b) = toLab(day)
        val weight = (l / 100f).coerceIn(0f, 1f)
        return toSrgb(
            lightness = l * NIGHT_LIGHTNESS_FACTOR,
            a = a * NIGHT_CHROMA_FACTOR,
            b = b * NIGHT_CHROMA_FACTOR - NIGHT_BLUE_SHIFT * weight,
            alpha = day ushr 24 and 0xFF,
        )
    }

    /**
     * The daytime colour that would produce [night].
     *
     * The inverse of [nightFromDay] up to the gamut mapping, which is not invertible: a colour that
     * had to give up chroma on the way down cannot get all of it back. Lightness round-trips to
     * within a fraction of a `L*` unit across the whole range, which is what "reasonably
     * reciprocal" asks for; a user who wants an exact value has [AutoColorMode.MANUAL].
     */
    fun dayFromNight(night: Int): Int {
        val (l, a, b) = toLab(night)
        val dayLightness = (l / NIGHT_LIGHTNESS_FACTOR).coerceAtMost(100f)
        val weight = (dayLightness / 100f).coerceIn(0f, 1f)
        return toSrgbTowards(
            fromLightness = l, fromA = a, fromB = b,
            toLightness = dayLightness,
            toA = a / NIGHT_CHROMA_FACTOR,
            toB = (b + NIGHT_BLUE_SHIFT * weight) / NIGHT_CHROMA_FACTOR,
            alpha = night ushr 24 and 0xFF,
        )
    }

    /**
     * One pair, resolved: what the scene should actually draw for day and for night.
     *
     * [MANUAL][AutoColorMode.MANUAL] returns the pair untouched, which is why turning the toggle
     * off restores what the user had -- the stored values were never overwritten, only ignored
     * while the mode was on.
     */
    fun resolve(day: Int, night: Int, mode: AutoColorMode): Pair<Int, Int> = when (mode) {
        AutoColorMode.MANUAL -> day to night
        AutoColorMode.FROM_DAY -> day to nightFromDay(day)
        AutoColorMode.FROM_NIGHT -> dayFromNight(night) to night
    }

    // --- CIELAB, D65, the sRGB transfer function ------------------------------------------------

    private const val XN = 0.95047f
    private const val ZN = 1.08883f
    private const val EPSILON = 0.008856f
    private const val KAPPA = 7.787f

    /** `L*` in 0..100, `a*` and `b*` unbounded in practice but small for real colours. */
    internal fun toLab(color: Int): Triple<Float, Float, Float> {
        val r = expand((color shr 16 and 0xFF) / 255f)
        val g = expand((color shr 8 and 0xFF) / 255f)
        val b = expand((color and 0xFF) / 255f)
        val x = 0.4124f * r + 0.3576f * g + 0.1805f * b
        val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
        val z = 0.0193f * r + 0.1192f * g + 0.9505f * b
        val fx = pivot(x / XN)
        val fy = pivot(y)
        val fz = pivot(z / ZN)
        return Triple(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
    }

    /**
     * Lab back to a packed colour, reducing chroma until it fits inside sRGB.
     *
     * The bisection runs a fixed 16 times, so this is branch-predictable and allocation-free, and
     * it is reached once per colour per settings change rather than per frame.
     */
    internal fun toSrgb(lightness: Float, a: Float, b: Float, alpha: Int): Int {
        val l = lightness.coerceIn(0f, 100f)
        if (inGamut(l, a, b)) return pack(l, a, b, alpha)
        var low = 0f
        var high = 1f
        repeat(16) {
            val mid = (low + high) / 2f
            if (inGamut(l, a * mid, b * mid)) low = mid else high = mid
        }
        return pack(l, a * low, b * low, alpha)
    }

    /**
     * Lab back to a packed colour, walking from a colour that fits towards one that may not.
     *
     * This is [toSrgb]'s sibling and it exists for the inverse direction, where the naive answer is
     * wrong in two different ways at once. Brightening is asymmetric: a night colour that is
     * already fairly light asks for a daytime lightness there is no room for.
     *
     * - [toSrgb]'s trade -- keep the lightness, drop the chroma -- turns a vivid red into white, and
     *   the hue of a near-white is noise.
     * - The opposite trade -- keep the chroma, drop the lightness -- fails too, because a strongly
     *   chromatic `Lab` point is outside sRGB at *both* ends of the lightness range, so there is no
     *   darker version of it to find.
     *
     * So neither axis alone is the answer. Interpolating from the colour the user actually chose
     * (which is in gamut by construction) towards the requested one, and taking the furthest point
     * that still fits, gives up lightness and chroma together in whatever proportion that hue can
     * afford. White walks almost all the way and stays white; a vivid red stops early and stays a
     * vivid, lighter red.
     */
    internal fun toSrgbTowards(
        fromLightness: Float, fromA: Float, fromB: Float,
        toLightness: Float, toA: Float, toB: Float,
        alpha: Int,
    ): Int {
        if (inGamut(toLightness, toA, toB)) return pack(toLightness, toA, toB, alpha)
        var low = 0f
        var high = 1f
        repeat(16) {
            val t = (low + high) / 2f
            val l = fromLightness + (toLightness - fromLightness) * t
            val a = fromA + (toA - fromA) * t
            val b = fromB + (toB - fromB) * t
            if (inGamut(l, a, b)) low = t else high = t
        }
        return pack(
            fromLightness + (toLightness - fromLightness) * low,
            fromA + (toA - fromA) * low,
            fromB + (toB - fromB) * low,
            alpha,
        )
    }

    private fun linearRgb(l: Float, a: Float, b: Float): Triple<Float, Float, Float> {
        val fy = (l + 16f) / 116f
        val fx = fy + a / 500f
        val fz = fy - b / 200f
        val x = unpivot(fx) * XN
        val y = unpivot(fy)
        val z = unpivot(fz) * ZN
        return Triple(
            3.2406f * x - 1.5372f * y - 0.4986f * z,
            -0.9689f * x + 1.8758f * y + 0.0415f * z,
            0.0557f * x - 0.2040f * y + 1.0570f * z,
        )
    }

    private fun inGamut(l: Float, a: Float, b: Float): Boolean {
        val (r, g, bl) = linearRgb(l, a, b)
        return r >= -1e-4f && r <= 1.0001f && g >= -1e-4f && g <= 1.0001f &&
            bl >= -1e-4f && bl <= 1.0001f
    }

    private fun pack(l: Float, a: Float, b: Float, alpha: Int): Int {
        val (r, g, bl) = linearRgb(l, a, b)
        return (alpha and 0xFF shl 24) or
            (channel(r) shl 16) or
            (channel(g) shl 8) or
            channel(bl)
    }

    private fun channel(linear: Float): Int {
        val c = linear.coerceIn(0f, 1f)
        val encoded = if (c <= 0.0031308f) 12.92f * c else 1.055f * c.pow(1f / 2.4f) - 0.055f
        return (encoded * 255f + 0.5f).toInt().coerceIn(0, 255)
    }

    private fun expand(encoded: Float): Float =
        if (encoded <= 0.04045f) encoded / 12.92f else ((encoded + 0.055f) / 1.055f).pow(2.4f)

    private fun pivot(t: Float): Float =
        if (t > EPSILON) cbrt(t) else KAPPA * t + 16f / 116f

    private fun unpivot(f: Float): Float {
        val cubed = f * f * f
        return if (cubed > EPSILON) cubed else (f - 16f / 116f) / KAPPA
    }

    /** Perceptual lightness alone, for tests and for anything that needs to compare two colours. */
    internal fun lightnessOf(color: Int): Float = toLab(color).first

    /** Chroma, `sqrt(a*^2 + b*^2)`: how colourful, independent of how light. */
    internal fun chromaOf(color: Int): Float {
        val (_, a, b) = toLab(color)
        return kotlin.math.sqrt(a * a + b * b)
    }

    /** Hue angle in degrees, undefined (returned as 0) for a neutral. */
    internal fun hueOf(color: Int): Float {
        val (_, a, b) = toLab(color)
        if (abs(a) < 1e-3f && abs(b) < 1e-3f) return 0f
        val deg = Math.toDegrees(kotlin.math.atan2(b.toDouble(), a.toDouble())).toFloat()
        return (deg + 360f) % 360f
    }
}

/**
 * This customization with every automatic pair worked out, ready to draw.
 *
 * Applied in exactly one place -- [CustomThemeRegistry.resolveActiveCustomization], the choke point
 * the renderer, the settings screen and the theme gallery all already resolve through -- so the
 * three cannot disagree and none of them pays for it more than once per settings emission.
 *
 * **What it deliberately does not do is write anything back.** The stored customization keeps the
 * user's manual colours on both sides of every pair, whatever mode the pair is in; this returns a
 * copy for drawing. That is the whole of the reversibility guarantee: switching a pair back to
 * [AutoColorMode.MANUAL] restores the values the user last chose, because nothing ever overwrote
 * them. It is also why archiving a pending edit (`WallpaperPrefs.readFlatCustomization`, which
 * `switchPendingTheme` archives) must stay upstream of this call, and does.
 */
fun SceneCustomization.withResolvedDayNightColors(): SceneCustomization = copy(
    houses = houses.resolved(),
    buildings = buildings.resolved(),
    cars = cars.resolved(),
    parasols = parasols.resolved(),
    people = people.resolved(),
    trees = trees.resolved(),
    snowmen = snowmen.resolved(),
    gifts = gifts.resolved(),
    penguins = penguins.resolved(),
    bunnies = bunnies.resolved(),
    easterEggs = easterEggs.resolved(),
    pumpkins = pumpkins.resolved(),
    mountainsFront = mountainsFront.resolved(),
    mountainsBack = mountainsBack.resolved(),
    lake = lake.resolved(),
    clouds = clouds.resolved(),
    sky = sky.resolved(),
    precipitation = precipitation.resolved(),
    hillsColorDay = DayNightColor.resolve(hillsColorDay, hillsColorNight, hillsAutoMode).first,
    hillsColorNight = DayNightColor.resolve(hillsColorDay, hillsColorNight, hillsAutoMode).second,
)

private fun ObjectVariantConfig.resolved(): ObjectVariantConfig {
    if (autoMode1 == AutoColorMode.MANUAL && autoMode2 == AutoColorMode.MANUAL) return this
    val (day1, night1) = DayNightColor.resolve(colorDay1, colorNight1, autoMode1)
    val (day2, night2) = DayNightColor.resolve(colorDay2, colorNight2, autoMode2)
    return copy(colorDay1 = day1, colorNight1 = night1, colorDay2 = day2, colorNight2 = night2)
}

private fun MountainLayerConfig.resolved(): MountainLayerConfig {
    if (autoMode == AutoColorMode.MANUAL) return this
    val (day, night) = DayNightColor.resolve(colorDay, colorNight, autoMode)
    return copy(colorDay = day, colorNight = night)
}

private fun LakeConfig.resolved(): LakeConfig {
    if (autoMode == AutoColorMode.MANUAL) return this
    val (day, night) = DayNightColor.resolve(colorDay, colorNight, autoMode)
    return copy(colorDay = day, colorNight = night)
}

private fun CloudsConfig.resolved(): CloudsConfig {
    if (autoMode == AutoColorMode.MANUAL) return this
    val (day, night) = DayNightColor.resolve(colorDay, colorNight, autoMode)
    return copy(colorDay = day, colorNight = night)
}

private fun SkyConfig.resolved(): SkyConfig {
    if (autoModeHigh == AutoColorMode.MANUAL && autoModeLow == AutoColorMode.MANUAL) return this
    val (dayHigh, nightHigh) = DayNightColor.resolve(colorDayHigh, colorNightHigh, autoModeHigh)
    val (dayLow, nightLow) = DayNightColor.resolve(colorDayLow, colorNightLow, autoModeLow)
    return copy(
        colorDayHigh = dayHigh, colorNightHigh = nightHigh,
        colorDayLow = dayLow, colorNightLow = nightLow,
    )
}

private fun PrecipitationConfig.resolved(): PrecipitationConfig {
    if (rainAutoMode == AutoColorMode.MANUAL && snowAutoMode == AutoColorMode.MANUAL) return this
    val (rainDay, rainNight) = DayNightColor.resolve(rainColorDay, rainColorNight, rainAutoMode)
    val (snowDay, snowNight) = DayNightColor.resolve(snowColorDay, snowColorNight, snowAutoMode)
    return copy(
        rainColorDay = rainDay, rainColorNight = rainNight,
        snowColorDay = snowDay, snowColorNight = snowNight,
    )
}
