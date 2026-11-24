package com.paperscrape.livewallpaper.engine

/**
 * The snow's colour under the winter palette (decision 117): with *Winter* chosen as the seasonal palette the hills
 * and the mountains are snow, and every snow lying in the scene takes that ground's colour, by day and by night.
 *
 * ### The snow's pair
 *
 * The theme's own hills when they are already snow -- a ground light by day, [SNOWY_GROUND_MIN_LUMINANCE] or more of
 * [SceneColour.encodedLuminance], as Winter's, Christmas's and Tundra's are ([SNOWY_GROUND_MIN_LUMINANCE] says which
 * others) -- else the Winter theme's ground,
 * [WINTER_GROUND_DAY] by day and [WINTER_GROUND_NIGHT] by night: a theme with no snowy ground of its own (New Year's, or
 * Autumn with the palette chosen by hand) takes Winter's snow (decision 117).
 *
 * ### What is drawn in it
 *
 * - **The hills** are drawn in that pair ([hillsDay], [hillsNight]), their three bands darkened as the user's colours are.
 * - **The lying snow** -- the roofs' snow, the crowns' caps, the fir's, the drifts, the distant houses' roofs, the cars'
 *   roofs -- is that pair at the frame's day blend, carried a quarter of the way to white ([snowAt]): the ground's own
 *   colour would melt a cap into the dark ground at night, a step lighter still reads as snow (decision 117). The
 *   sprites stay fixed art, multiplied by this colour as a tint multiplies a mask; their three tones keep the relief.
 * - **Both mountain layers** are that same snow colour, the back one [BACK_MOUNTAINS_SHADE] of it ([backMountains]) so
 *   the two ranges stay apart.
 *
 * Not the falling snow (the precipitation's own colours), the snowmen (a category with its own colours) nor the frosted
 * palms (a whole crown drawn in place of the green one, not snow lying on it).
 *
 * **Nothing is stored.** The user's hills and mountain colours stay as they are in the store, the backup, the theme files
 * and the saved themes; with the palette off they are drawn again as they were. The settings show the snow while it is
 * on (`SettingsUiModel.winterLandscape`).
 *
 * Pure arithmetic over [SceneColour], so the rule runs on the JVM and the wallpaper, the gallery card and the settings
 * all ask it the same question.
 */
internal object GroundSnow {

    /** The Winter theme's ground by day, the snow of a theme whose own ground is not snow. */
    const val WINTER_GROUND_DAY = 0xFFE9F1F7.toInt()

    /** The Winter theme's ground by night. */
    const val WINTER_GROUND_NIGHT = 0xFF27364A.toInt()

    /**
     * How light a day ground must be to be snow already: Winter's, Christmas's and Tundra's are 240 to 246; New Year's
     * (109) and most themes' are far below.
     */
    const val SNOWY_GROUND_MIN_LUMINANCE = 200f

    /**
     * How far apart a snowy ground's channels may be: snow is a white with a cold tint (Winter's, Christmas's and
     * Tundra's 8 to 14). A light ground of another colour -- Easter's and Spring's greens, a pale sand -- is not snow,
     * and takes Winter's (decision 117: a theme without snow takes Winter's).
     */
    const val SNOWY_GROUND_MAX_SPREAD = 24

    /** How far toward white the lying snow is carried from the ground's colour: a quarter (decision 117). */
    const val SNOW_TOWARD_WHITE = 0.25f

    /** What the back mountains keep of the snow's colour, each channel: 12 % darker than the front ones. */
    const val BACK_MOUNTAINS_SHADE = 0.88f

    private const val OPAQUE = 0xFF000000.toInt()
    private const val WHITE = 0xFFFFFFFF.toInt()

    /** Whether a theme's day ground [hillsDay] is snow already. */
    fun groundIsSnow(hillsDay: Int): Boolean {
        val r = (hillsDay ushr 16) and 0xFF
        val g = (hillsDay ushr 8) and 0xFF
        val b = hillsDay and 0xFF
        val spread = maxOf(r, g, b) - minOf(r, g, b)
        return SceneColour.encodedLuminance(hillsDay) >= SNOWY_GROUND_MIN_LUMINANCE && spread <= SNOWY_GROUND_MAX_SPREAD
    }

    /** The snow's day colour for a theme whose hills are [hillsDay] by day. */
    fun snowGroundDay(hillsDay: Int): Int = if (groundIsSnow(hillsDay)) hillsDay else WINTER_GROUND_DAY

    /** The snow's night colour for a theme whose hills are [hillsDay] by day and [hillsNight] by night. */
    fun snowGroundNight(hillsDay: Int, hillsNight: Int): Int = if (groundIsSnow(hillsDay)) hillsNight else WINTER_GROUND_NIGHT

    /** The hills' day colour as drawn: the snow's with the winter palette on, the user's otherwise. */
    fun hillsDay(c: SceneCustomization): Int =
        if (c.winterColorsEnabled) snowGroundDay(c.hillsColorDay) else c.hillsColorDay

    /** The hills' night colour as drawn: the snow's with the winter palette on, the user's otherwise. */
    fun hillsNight(c: SceneCustomization): Int =
        if (c.winterColorsEnabled) snowGroundNight(c.hillsColorDay, c.hillsColorNight) else c.hillsColorNight

    /** [ground] a quarter of the way to white ([SNOW_TOWARD_WHITE]), opaque. */
    fun lighter(ground: Int): Int = SceneColour.blendArgb(ground, WHITE, SNOW_TOWARD_WHITE) or OPAQUE

    /**
     * The colour of the snow lying in the scene at [dayBlend], and of the front mountains: the snow's pair blended on the
     * day blend, then [lighter]. Opaque. Asked once a frame by each renderer, where its frame starts, and only drawn with the winter
     * palette on.
     */
    fun snowAt(c: SceneCustomization, dayBlend: Float): Int {
        val ground = SceneColour.blendArgb(
            snowGroundNight(c.hillsColorDay, c.hillsColorNight),
            snowGroundDay(c.hillsColorDay),
            dayBlend.coerceIn(0f, 1f),
        ) or OPAQUE
        return lighter(ground)
    }

    /** The back mountains' colour for the snow colour [snow]: each channel [BACK_MOUNTAINS_SHADE] of it, opaque. */
    fun backMountains(snow: Int): Int =
        OPAQUE or
            (((snow ushr 16 and 0xFF) * BACK_MOUNTAINS_SHADE).toInt() shl 16) or
            (((snow ushr 8 and 0xFF) * BACK_MOUNTAINS_SHADE).toInt() shl 8) or
            ((snow and 0xFF) * BACK_MOUNTAINS_SHADE).toInt()

    /** The front mountains' day colour with the winter palette on: the snow's day colour, [lighter]. */
    fun frontMountainsDay(c: SceneCustomization): Int = lighter(snowGroundDay(c.hillsColorDay))

    /** The front mountains' night colour with the winter palette on. */
    fun frontMountainsNight(c: SceneCustomization): Int = lighter(snowGroundNight(c.hillsColorDay, c.hillsColorNight))
}
