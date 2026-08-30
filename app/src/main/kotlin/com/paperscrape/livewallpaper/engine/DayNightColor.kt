package com.paperscrape.livewallpaper.engine

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

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
 * Android dependency -- it reimplements `ColorUtils.blendARGB` for exactly that reason), and the
 * renderer. Three implementations of "a bit darker" is three chances for the card, the preview and
 * the wallpaper to disagree about what the user just picked. So the transform lives here, in plain
 * Kotlin with no `android.*` import, and everything reads it.
 *
 * It is applied **once**, in [CustomThemeRegistry.resolveActiveCustomization], which is already the
 * single choke point every consumer resolves a customization through. Nothing derives a colour per
 * frame, and nothing derives one twice.
 *
 * ### Where the numbers come from
 *
 * They are measured from this project's own artwork, not chosen. Across the 41 day/night pairs the
 * built-in themes and the default customization already author by hand -- every theme's
 * `hillColorsDay`/`hillColorsNight` and `skyDay`/`skyNight`, plus every literal `colorDay*`/
 * `colorNight*` pair in `SceneCustomization` -- converted to HSL:
 *
 * | quantity | measurement |
 * |---|---|
 * | hue shift | median **+0.7 degrees**, quartiles -1.6 to +3.3 |
 * | night lightness / day lightness | median **0.635** (least squares through the pairs: 0.647L - 0.006) |
 * | night saturation / day saturation | median **0.725** |
 *
 * So: **keep the hue, scale the lightness, scale the saturation.** The hue result is the
 * interesting one -- a night palette that rotates towards blue is the obvious guess and it is not
 * what this project's own art does. Guessing would have introduced a colour cast into every
 * automatic pair.
 *
 * The spread around those medians is wide (the lightness residual averages 0.117), because some
 * authored pairs are deliberate departures -- white mountain snow becomes pale blue-grey rather
 * than mid grey. A fitted curve cannot recover an artistic decision, and this does not try to. It
 * reproduces the family the pairs belong to, which is what an automatic mode is for; a user who
 * wants the departure still has [AutoColorMode.MANUAL], which is the default.
 */
object DayNightColor {

    /**
     * Night lightness as a fraction of day lightness.
     *
     * Multiplicative rather than subtractive, which is what the measurement says: the fitted
     * intercept is -0.006, i.e. zero within the noise, so the relationship passes through the
     * origin. A subtractive rule would have crushed dark colours to black and left light ones
     * barely touched.
     */
    const val NIGHT_LIGHTNESS_FACTOR = 0.635f

    /** Night saturation as a fraction of day saturation: night is dimmer *and* calmer. */
    const val NIGHT_SATURATION_FACTOR = 0.725f

    /** The daytime colour that would produce [night] under [nightFromDay]. */
    fun dayFromNight(night: Int): Int = scale(night, 1f / NIGHT_LIGHTNESS_FACTOR, 1f / NIGHT_SATURATION_FACTOR)

    /** The night colour for a daytime [day]. */
    fun nightFromDay(day: Int): Int = scale(day, NIGHT_LIGHTNESS_FACTOR, NIGHT_SATURATION_FACTOR)

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

    /**
     * Hue held, lightness and saturation scaled, alpha carried through untouched.
     *
     * Both factors clamp into 0..1 rather than wrapping, so the transform saturates instead of
     * folding over: brightening an already-white colour leaves it white, and darkening black
     * leaves it black. Those are the two ends a user will actually reach by dragging a picker into
     * a corner, and "nothing further happens" is the only answer that does not surprise.
     */
    private fun scale(color: Int, lightnessFactor: Float, saturationFactor: Float): Int {
        val alpha = color ushr 24 and 0xFF
        val (hue, saturation, lightness) = toHsl(color)
        return fromHsl(
            hue = hue,
            saturation = (saturation * saturationFactor).coerceIn(0f, 1f),
            lightness = (lightness * lightnessFactor).coerceIn(0f, 1f),
            alpha = alpha,
        )
    }

    /** Hue in 0..360 (0 for greys), saturation and lightness in 0..1. */
    internal fun toHsl(color: Int): Triple<Float, Float, Float> {
        val r = (color shr 16 and 0xFF) / 255f
        val g = (color shr 8 and 0xFF) / 255f
        val b = (color and 0xFF) / 255f
        val maximum = max(r, max(g, b))
        val minimum = min(r, min(g, b))
        val chroma = maximum - minimum
        val lightness = (maximum + minimum) / 2f
        if (chroma < 1e-6f) return Triple(0f, 0f, lightness)
        val saturation = chroma / (1f - abs(2f * lightness - 1f)).coerceAtLeast(1e-6f)
        val hue = when (maximum) {
            r -> 60f * (((g - b) / chroma) % 6f)
            g -> 60f * (((b - r) / chroma) + 2f)
            else -> 60f * (((r - g) / chroma) + 4f)
        }
        return Triple((hue + 360f) % 360f, saturation.coerceIn(0f, 1f), lightness)
    }

    internal fun fromHsl(hue: Float, saturation: Float, lightness: Float, alpha: Int): Int {
        val chroma = (1f - abs(2f * lightness - 1f)) * saturation
        val huePrime = ((hue % 360f) + 360f) % 360f / 60f
        val second = chroma * (1f - abs((huePrime % 2f) - 1f))
        val (r1, g1, b1) = when (huePrime.toInt()) {
            0 -> Triple(chroma, second, 0f)
            1 -> Triple(second, chroma, 0f)
            2 -> Triple(0f, chroma, second)
            3 -> Triple(0f, second, chroma)
            4 -> Triple(second, 0f, chroma)
            else -> Triple(chroma, 0f, second)
        }
        val match = lightness - chroma / 2f
        fun channel(value: Float) = ((value + match) * 255f).roundToInt().coerceIn(0, 255)
        return (alpha and 0xFF shl 24) or
            (channel(r1) shl 16) or
            (channel(g1) shl 8) or
            channel(b1)
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
