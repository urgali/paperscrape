package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import com.paperscrape.livewallpaper.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws one frame of the paper-cutout landscape into the given canvas: the sky gradient, the stars, the sun or
 * the moon on its arc, the mountains, the hills with their parallax, the lake, the weather and the sky's
 * visitors; the town is [SceneObjectRenderer]'s.
 *
 * The renderer keeps state between frames: the scroll accumulator, the cloud-cover fade, the lightning timer,
 * the wave slots, the firework, sleigh and Headless Horseman effects and the night by day's phase, plus caches
 * (the star field per screen size and density, so stars do not jump, and the hill silhouettes).
 *
 * Every element is a flat, exact colour fill, with no paper-grain overlay (`DESIGN_NOTES.md` §12, decision 2):
 * a multiply grain dulls the colours the user picked and costs CPU every frame.
 */
class PaperRenderer(
    private var screenWidth: Int,
    private var screenHeight: Int,
    private val context: android.content.Context,
) {
    var theme: SceneTheme = ThemeCatalog.SUNSET
    var homeScreenOffset: Float = 0f // 0..1 across all home screen pages
    var swipeScrollEnabled: Boolean = true // whether homeScreenOffset contributes to scrollProgress at all
    var scrollSpeed: Float = 0.15f // 0..1, continuous auto-scroll rate, independent of swiping
    private var continuousScrollAccum = 0.0 // grows without bound in one direction; see scrollProgress

    /**
     * The one scroll position every layer scrolls by: the continuous drift, which advances in one direction
     * at [scrollSpeed] and is never reset, plus the home-screen swipe offset when [swipeScrollEnabled].
     * [parallaxStrength] scales how far each layer moves against it. The objects wrap on the same tile width
     * and shift as the hills they stand on ([drawHillLayers]), so the drift can grow without bound.
     *
     * [continuousScrollAccum] is a `Double`: in a `Float` the per-frame increment falls below the
     * accumulator's precision within hours to days of uptime, and each layer's different parallax product
     * rounds it differently, so the hills break apart with objects floating in the sky.
     */
    private val scrollProgress: Double
        get() {
            val swipe = if (swipeScrollEnabled) homeScreenOffset else 0f
            return continuousScrollAccum + swipe
        }

    /** The candidate seed for one effect in the current theme ([Companion.seedFor]). */
    private fun seedFor(effectOrdinal: Int): Int = PaperRenderer.seedFor(theme, effectOrdinal)

    /**
     * One layer's parallax shift, wrapped into `(-tileWidth, 0]`. The product and the wrap are done in `Double`
     * and only the wrapped result, under one tile, is narrowed to `Float`: narrowed first, the unbounded
     * [scrollProgress] would quantise the scroll into steps. [scrollProgress] itself cannot be wrapped: each
     * layer multiplies it by its own parallax and by [parallaxStrength] (continuous over `0.5..2`), so no
     * period is a whole number of tiles for all of them.
     */
    private fun wrappedScrollShift(parallax: Double, tileWidth: Float): Float {
        var shift = (-scrollProgress * screenWidth * parallax) % tileWidth
        if (shift > 0.0) shift -= tileWidth
        return shift.toFloat()
    }

    var parallaxStrength: Float = 1f
    var hillsVariation: Float = 1f // 0..1, see buildBaseHillPath's own doc comment
    var scrollBackground: Boolean = false // whether sky/sun/moon/stars scroll with the parallax hills
    var sceneCustomization: SceneCustomization = SceneCustomization.DEFAULT
    // Live Weather: when non-null, overrides the precipitation's visibility, kind, intensity and storm and the
    // clouds' density (drawPrecipitation and drawClouds say how). Set only from what LiveWeatherSchedule.decide
    // authorises, the rule the settings screen is told by, so the two agree. Null means the theme's own weather:
    // Live Weather off, nowhere to check, a missing or rejected key, or nothing held within
    // LiveWeatherSchedule.SNAPSHOT_MAX_AGE_MILLIS. A dropped request does not clear it (LiveWeatherStatus.STALE):
    // the last good conditions stay up to that age, never longer.
    var liveWeatherOverride: com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot? = null

    /**
     * Whether a null [liveWeatherOverride] means the conditions **aged out** rather than Live Weather off
     * (`LiveWeatherDecision.lapsed`, set and read with the override). Then the cloud cover eases from the
     * last forecast to the theme's ([CloudCoverFade.coverLapsingTo]), and the theme's rain, which arrives in
     * the frame of the lapse, falls through the theme's own clouds, not the fading forecast's
     * ([CloudCoverage.rainField]). Precipitation and the storm are never eased.
     */
    var liveWeatherLapsed: Boolean = false

    /**
     * Eases the drawn cloud cover, and each candidate's opacity, toward what the forecast reports. Held by the
     * renderer, not the snapshot, which the engine replaces whole whenever a fetch lands.
     */
    private val cloudCoverFade = CloudCoverFade(CLOUD_POOL_SIZE)

    /**
     * This frame's cloud cover as drawn, or `NaN` when Live Weather is not driving (not a `Float?`, which would box
     * every frame). Set once a frame by [updateWeatherPredicates]; read by [drawClouds] and [stormStrength].
     */
    private var drawnCloudCover: Float = Float.NaN

    // **Where the light is, this frame**, for the water, which mirrors the sky: whether a body was drawn, sun or
    // moon, and where, and the sky's colours. Written by [drawSky] and [drawCelestialBody], read later in the same
    // frame by [drawLake] and [drawPrecipitation]: a value handed forward in one back-to-front pass.
    private var celestialShownNow = false
    private var celestialIsSunNow = false
    private var celestialCxNow = 0f
    private var skyTopColorNow = 0
    private var skyHorizonColorNow = 0

    // Gradients (the sky, the hill highlight, the glows) go to SceneCanvas as explicit stops, not as a Paint's
    // Shader, which a backend cannot read back (`DESIGN_NOTES.md` §12, decision 21).
    private val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mountainShape = SceneShape()
    private val lakePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /**
     * The sprite-blitting path ([SpriteBlitter]). This renderer draws in **both** scale conventions, so each call
     * names its sprite's: the lake's sprites, the clouds, the stars, the rainbow, the bolt, the firework and the
     * sleigh are [SpriteScale.SCENE_UNITS], the sun, the moon and the birds [SpriteScale.CANVAS_PIXELS]. The wrong
     * one is a silent [SpriteBlitter.SPRITE_PIXELS_PER_UNIT]x size error.
     */
    private val sprites = SpriteBlitter(context)

    private val precipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightningPaint = Paint()
    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /** The falling-leaf palette blended to this frame's light, one per entry; see [drawFallingLeaves]. */
    private val fallingLeafBlended = IntArray(FALLING_LEAF_PALETTE.size)

    // Lightning: one screen-wide flash with one number to fade, so a timer here rather than an effect class.
    private var lightningTimer = 4f + Random.nextFloat() * 6f
    private var lightningFlashAlpha = 0f

    /**
     * Whether the strike timer may fire. **Always true in the wallpaper**; nothing in `src/main` writes it.
     *
     * A switch for the golden harness (`GoldenScene.pinLightning`, which holds the rule for when a golden may use
     * it): `updateLightning` rolls the next strike from the global `Random`, which a test cannot seed (the sky's
     * visitors roll from [SceneRandom.source], which it can), so a warmed-up storm carries a flash on about one
     * frame in thirty-two. Deriving the strike from the scene clock instead would change the sky every user sees,
     * and that sky stays as it is. Only the firing is gated: a flash in flight still fades.
     */
    var lightningStrikesEnabled: Boolean = true

    /**
     * Where the current bolt hangs (a fraction of the width) and how tall it is (of the height): rolled once a
     * strike, since a bolt moving while it fades reads as several strikes; kept here so a strike allocates nothing.
     */
    private var lightningBoltXFraction = 0.5f
    private var lightningBoltHeightFraction = LIGHTNING_BOLT_MIN_HEIGHT_FRACTION

    private data class Star(
        val x: Float,
        val y: Float,
        val radius: Float,
        val phase: Float,
        /** Drawn with the sparkle sprite rather than as a plain point. See [drawStars]. */
        val sparkle: Boolean,
    )

    private var stars: List<Star> = emptyList()
    private var cachedStarsDensity = -1f

    // Unshifted hill silhouettes, one per layer, rebuilt only when the theme, the screen size or [hillsVariation]
    // changes (rebuildHillPathsIfNeeded); the parallax is a translate at draw time, so a swipe rebuilds nothing.
    private val baseHillShapes = arrayOfNulls<SceneShape>(1) // sized to layerCount (declared further down)
    private var cachedPathsThemeId: String? = null
    private var cachedPathsWidth = -1
    private var cachedPathsHeight = -1
    private var cachedPathsVariation = -1f

    /** Where the sky is covered: refilled by [drawClouds] each frame, read by [drawPrecipitation] ([CloudCoverage]). */
    private val cloudCoverage = CloudCoverage()

    private var objectRenderer = SceneObjectRenderer(SceneObjectCatalog.layoutFor(theme.id, theme.accentColor), sceneCustomization, context, theme.id)
    private var objectRendererThemeId = theme.id
    private var objectRendererGeneration = CustomThemeRegistry.generation()
    private var objectRendererConfig = sceneCustomization
    // Written in place by drawHillLayers before every objectRenderer.draw, so a scrolling frame allocates none.
    // tileWidth starts at 0, "no tiling period", which is also what an unsized surface gives (tileWidth is
    // screenWidth * 2), so SceneObjectRenderer.draw's one `tileWidth <= 0f` guard covers both; a small positive
    // placeholder would pass the guard and enumerate a tile copy per pixel.
    private val objectGroundGeometry = GroundGeometry(0f, 0f)
    private val fireworkEffect = FireworkEffect()
    private val santaSleighEffect = SantaSleighEffect()

    /** Santa's sleigh group, drawn from its pieces. */
    private val santaSleighDrawer = SantaSleighDrawer(sprites)

    /**
     * The sleigh group's scale on this screen ([SantaSleighScale.pxPerUnit]) and the screen height it was worked out
     * for: worked out again, with the effect's margins and gifts, when the height changes.
     */
    private var sleighPxPerUnit = 0f
    private var sleighGeometryHeight = -1

    /** For the tests that count a frame with the sleigh in flight: see [SantaSleighEffect.heldProgress]. */
    internal fun holdSantaSleighForTest(progress: Float) {
        santaSleighEffect.heldProgress = progress
    }

    /** How many gifts are falling from the sleigh now, for the GL sweeps that hold it in flight. */
    internal fun santaGiftsFallingForTest(): Int = santaSleighEffect.giftsFalling

    /** The Headless Horseman's flight and his falling pumpkins ([HeadlessHorsemanEffect]). */
    private val headlessHorsemanEffect = HeadlessHorsemanEffect()

    /** The Headless Horseman, drawn from his pieces. */
    private val headlessHorsemanDrawer = HeadlessHorsemanDrawer(sprites)

    /** The Horseman's scale on this screen ([HeadlessHorsemanScale.pxPerUnit]) and the height it was worked out for. */
    private var horsemanPxPerUnit = 0f
    private var horsemanGeometryHeight = -1

    /** A flight of the Horseman set off now, throwing his first pumpkin at once: see [HorsemanFlight.startForTest]. */
    internal fun startHeadlessHorsemanFlightForTest() {
        headlessHorsemanEffect.flight.startForTest(screenHeight.toFloat())
    }

    /** How many pumpkins the Horseman has thrown are in the air now, for the tests that sweep him in flight. */
    internal fun horsemanPumpkinsFallingForTest(): Int = headlessHorsemanEffect.dropsFalling

    /**
     * The night by day ([nightOverDayActive]): the phase every layer is drawn with while it is in force, made from the
     * frame's own -- full night (`dayBlend` 0, no sun), the moon on the sun's path ([SunPositionCalculator.DayPhase.celestialX],
     * `celestialY`) and the clock's hour ([SunPositionCalculator.DayPhase.hour24], for the opening hours and the windows).
     * One instance per phase the engine gives (a new one when the hour moves, once a minute), so a frame makes none.
     */
    private var nightOverDaySource: SunPositionCalculator.DayPhase? = null
    private var nightOverDayPhase: SunPositionCalculator.DayPhase? = null

    /** The phase a frame is drawn with: [phase] itself, or -- while the night by day is in force and it is not night -- the night over it. */
    internal fun nightOverDay(phase: SunPositionCalculator.DayPhase): SunPositionCalculator.DayPhase {
        if (!sceneCustomization.nightOverDayActive()) return phase
        if (phase.dayBlend <= 0f && !phase.isSunVisible) return phase
        val cached = nightOverDayPhase
        if (cached != null && nightOverDaySource === phase) {
            cached.moonPhase = phase.moonPhase
            return cached
        }
        val night = NightOverDay.of(phase)
        nightOverDaySource = phase
        nightOverDayPhase = night
        return night
    }

    /**
     * The still moon ([StillMoon]): the phase that puts the carved moon at its place, made from the frame's own (after
     * [nightOverDay]), one instance per phase the engine gives, and how much of it is out ([StillMoonFrame.alpha]).
     */
    private val stillMoonFrame = StillMoonFrame()

    /** The phase a frame is drawn with while the still moon is in force and it is the moon that is up ([StillMoon]); [phase] otherwise. */
    internal fun stillMoon(phase: SunPositionCalculator.DayPhase): SunPositionCalculator.DayPhase =
        stillMoonFrame.phaseFor(
            phase,
            sceneCustomization.stillMoonActive(),
            sceneCustomization.nightOverDayActive(),
            screenWidth,
            screenHeight,
            sceneCustomization.sky.sunCloudHeight,
        )

    /**
     * Keeps [objectRenderer] in step with the theme and configuration, every frame, as cheaply as possible:
     * a reference comparison settles the common case (the engine assigns [sceneCustomization] a new instance
     * only when a preference changed); a new renderer only when the layout changes (another theme, or the
     * registry generation after a custom theme's edit, reset or delete); otherwise
     * [SceneObjectRenderer.customization] is updated in place and rebuilds only what the change affects, so a
     * colour change does not restart every car.
     */
    private fun syncObjectRendererWithTheme() {
        val currentGeneration = CustomThemeRegistry.generation()
        val existing = objectRenderer
        if (objectRendererThemeId == theme.id &&
            objectRendererGeneration == currentGeneration &&
            objectRendererConfig === sceneCustomization
        ) {
            return
        }

        if (objectRendererThemeId != theme.id || objectRendererGeneration != currentGeneration) {
            objectRenderer = SceneObjectRenderer(
                SceneObjectCatalog.layoutFor(theme.id, theme.accentColor),
                sceneCustomization,
                context,
                theme.id,
            )
            objectRendererThemeId = theme.id
            objectRendererGeneration = currentGeneration
        } else {
            existing.customization = sceneCustomization
        }
        objectRendererConfig = sceneCustomization
    }

    // The hill layers: how far each shifts with the scroll (farther is slower), how tall it is and where its
    // top is, as fractions of the screen height. One layer, one silhouette with one scroll rate
    // (`DESIGN_NOTES.md` §12, decision 5); buildBaseHillPath says how its top edge wobbles.
    private val layerCount = 1
    private val parallaxFactors = floatArrayOf(HILL_PARALLAX)
    private val heightFractions = floatArrayOf(SceneSpace.HILL_LAYER_HEIGHT_FRACTION)
    private val yOffsets = floatArrayOf(SceneSpace.HILL_LAYER_TOP_FRACTION) // top of each layer, as fraction of height

    /**
     * The Y line (a fraction of the screen height) below which the hill is solid paper at every x, wherever
     * [buildBaseHillPath]'s crest puts its top edge: [SceneSpace.HILL_SOLID_TOP_DEPTH_FRACTION] of the layer's
     * band. [drawMountains] and [updateLakeBandY] anchor to it so the mountains and the lake meet the hills with
     * no gap.
     */
    private val hillGuaranteedTopFraction = SceneSpace.GROUND_SOLID_TOP_Y_FRACTION

    companion object {
        // ---- Fixed candidate pools -------------------------------------------------------
        //
        // Each effect draws from a pool of a constant size and its density selects a share of it, so a
        // candidate's `(i + 0.5) / poolSize` place never moves with the slider. The sizes are part of the
        // visual contract (`DESIGN_NOTES.md` §12, decision 10): changing one re-arranges that effect in every theme.
        const val CLOUD_POOL_SIZE = 41
        /**
         * How many precipitation slots exist, of which [PrecipitationConfig.intensity] selects a share. A metre is
         * a fixed share of the screen height, so a fixed pool is a fixed density per square metre on every device.
         * 240 is the knee of a sweep at 1080x2424: it fills 86 % of a 6x12 grid of the band the drops fall
         * through, against 72 % at 180 and 89 % at 300, and the frame cost was flat across 90 to 400.
         */
        const val PRECIPITATION_POOL_SIZE = 240

        /**
         * A raindrop's size **in scene metres**, like every other size in the scene ([SceneSpace]), so it can be
         * compared with the world: stylised, not physical (a 2 mm drop would be invisible), and held to **at most
         * 0.34 of an adult** (1.75 m). At 0.58 m the longest streak reads as rain; 0.87 m, half an adult, reads as
         * a falling stick. `PrecipitationScaleTest` holds the ceiling and the floor.
         */
        const val RAIN_LENGTH_MIN_METRES = 0.36f
        const val RAIN_LENGTH_MAX_METRES = 0.58f
        const val RAIN_STROKE_WIDTH_METRES = 0.044f

        /**
         * A flake's diameter, judged against a head (about 0.23 m), since a disc reads by area: the largest is about
         * 1.3 heads across (`PrecipitationScaleTest` allows up to 1.35).
         */
        const val SNOW_DIAMETER_MIN_METRES = 0.13f
        const val SNOW_DIAMETER_MAX_METRES = 0.30f

        /** How far a flake drifts sideways as it falls, the same for every flake whatever its size. */
        const val SNOW_SWAY_METRES = 0.31f

        /** How far past the bottom edge a drop keeps falling before it wraps. */
        const val PRECIPITATION_BOTTOM_MARGIN_METRES = 2.67f

        /**
         * How solidly a drop and a flake are painted at the middle of their fall; named because
         * [PRECIPITATION_MIN_LUMA_GAP], stated on the composited stroke, divides by them.
         */
        const val RAIN_ALPHA = 190
        const val SNOW_ALPHA = 220

        /**
         * **How far a drop has to stand off the sky it falls through, in Rec. 601 luma of the stroke as
         * composited** (`DESIGN_NOTES.md` §12, decisions 32 and 33).
         *
         * The rain's colour is the theme's pair blended by the day phase, while the sky changes with the hour,
         * the twilight branch and the weather, and in eleven of the twelve themes the two meet at the same
         * brightness at some hour: a hairline that differs in hue only is invisible. Luma, not dE, because at
         * hairline width the eye keeps luminance and loses hue, as for [WATERLINE_MIN_LUMA_GAP].
         *
         * 13.47 is the midpoint of the floor, the worst case of the sweep (0.00), and the signal, the same drop
         * over the hills where it is seen (median 26.94). The colour is lifted only as far as needed, and toward
         * the cheaper of white and black, so it flips when the sky's band crosses the drop: twice a day per
         * theme, at the day's two ends, which `PrecipitationContrastTest` bounds. Snow clears the gate everywhere
         * (worst 19.13), so the correction is zero for it on the shared path. `PrecipitationContrastTest` carries
         * the sweep and fails if any of it moves.
         */
        const val PRECIPITATION_MIN_LUMA_GAP = 13.47f
        const val BIRD_POOL_SIZE = 6
        /**
         * The ceiling of [SceneObjectRenderer.leafSourceLeafCount], used as the candidate stride: `id * MAX + slot`
         * keeps each copy's slots apart from its neighbour's while the count varies with the crown's drawn size.
         */
        const val MAX_LEAVES_PER_SOURCE = 13

        /**
         * The candidate of leaf [slot] of the crown copy [copyId] ([SceneObjectRenderer.leafCopyId]): `copyId x
         * MAX_LEAVES_PER_SOURCE + slot`, exact in a `Long` for every copy of every object a theme can hold. The noise
         * reads its low 32 bits, which are the leaf's whatever the size of the scene.
         */
        internal fun fallingLeafCandidate(copyId: Long, slot: Int): Long = copyId * MAX_LEAVES_PER_SOURCE + slot

        /**
         * Which of [paletteSize] colours leaf [candidate] takes: its floored remainder, in `0 until paletteSize`. An `Int`
         * `%` is negative for a copy numbered -1 or a candidate past `Int.MAX_VALUE` (a theme of 167 objects or more:
         * 166 x 1 000 003 x 13), and a negative palette index ends the wallpaper's process.
         */
        internal fun fallingLeafColourIndex(candidate: Long, paletteSize: Int): Int =
            Math.floorMod(candidate, paletteSize.toLong()).toInt()

        const val MOUNTAIN_POOL_SIZE = 4
        const val LAKE_DECORATION_POOL_SIZE = 4

        /** Lake sparkles have no density control -- a fixed handful, always drawn. */
        const val LAKE_SPARKLE_POOL_SIZE = 5

        /**
         * **The water is a mirror** (`DESIGN_NOTES.md` §12, decision 31): how far the surface is carried toward the
         * sky's horizon colour at the band's far edge, the near edge staying the theme's lake colour, so the band is
         * one vertical ramp between the two.
         */
        const val LAKE_MIRROR_SKY_SHARE = 0.55f

        /**
         * **The waterline.** A mirror reflects the sky, so where a theme's sky and lake agree the water's top edge
         * vanishes: against the sky just above each shore, over the twelve themes and the whole day, the worst luma
         * gap is 0.1. The edge cannot be made wavy (a jittered edge dips below the mountains' anchor and opens bare
         * sky, [drawLakeBand]), so a line is struck along the band's top in a tone pushed off the sky's luma by this
         * gap: 14.5, the median the twelve themes already show, so the worst is lifted to the typical and none louder.
         */
        const val WATERLINE_MIN_LUMA_GAP = 14.5f
        const val WATERLINE_THICKNESS_PX = 2f

        /** The light's path on the water: how many slivers are cut under the sun or the moon. */
        const val LAKE_GLITTER_POOL_SIZE = 9

        // ---- The waves ----------------------------------------------------------------------
        //
        // Three slots, one breaker each, drifting +x across the near lanes in rain and in a thunderstorm only:
        // the water is a mirror and is not recoloured for the weather, so what moves is on it. While the weather
        // holds, a slot's membership changes only off screen ([CarSelection.offScreen]). The waves go and come back in
        // the frame the rain does, wherever their drift has carried them: the rain and the storm change in a frame,
        // at a forecast's expiry too.
        const val WAVE_POOL = 3

        /**
         * How far a wave's two papers stand off the water in Rec. 601 luma, by day: halfway between the floor (9.80,
         * the mirror's own gradient over a wave's height at its worst) and the signal (40 body, 60 foam, the gaps of
         * the frame that reads as a wave), decision 32. `WaveContrastTest` sweeps every lake theme, hour and
         * weather and fails if these drift. [WaveTint.gapAt] aims at the signal by night, where a sea at the gate is
         * seen but weaker than a moving sea should be.
         */
        const val WAVE_BODY_LUMA_GAP = 24.9f
        const val WAVE_FOAM_LUMA_GAP = 34.9f

        /** The night target: the signal itself, the far end of the same derivation. */
        const val WAVE_BODY_LUMA_GAP_NIGHT = 40f
        const val WAVE_FOAM_LUMA_GAP_NIGHT = 60f

        /** The wave's canvas, in scene units, and the length it stands for on the water. */
        const val WAVE_UNITS_WIDE = 120f
        const val WAVE_UNITS_TALL = 44f
        const val WAVE_METRES_LONG = 8f

        /**
         * How far under a sailboat's placement point its visible waterline sits, in boat units: `drawSailboat` hangs
         * the 17-unit hull 8 units below the point, so it meets the water 25 under it, where
         * [LakeLanes.visibleWaterline] keys the boat.
         */
        const val SAILBOAT_HULL_WATERLINE_UNITS = 25f

        /** The reflected glow's radius in band heights, which is also its depth ([drawLakeMirrorGlow] says why). */
        const val LAKE_MIRROR_GLOW_RADIUS_BANDS = 0.8f

        // The lake decorations carry no tint: their PNGs are fixed art in their own paper colours (decision 25),
        // and a second colour multiplied over finished art would darken it. `SpriteTintClassTest` holds the class.

        /** Opacity at the centre of the sun's and the moon's ambient glow, falling to 0 at its outer radius. */
        const val CELESTIAL_GLOW_CENTRE_ALPHA = 90

        /**
         * The four cloud depth tiers: farther tiers drift slower, sit higher and draw smaller. Constants, so a
         * frame with clouds allocates no arrays.
         */
        private val CLOUD_TIER_PARALLAX = floatArrayOf(0.035f, 0.05f, 0.065f, 0.08f)
        private val CLOUD_TIER_Y_OFFSET = floatArrayOf(-0.02f, 0f, 0.015f, 0.03f)
        private val CLOUD_TIER_SIZE_MULTIPLIER = floatArrayOf(0.85f, 0.95f, 1.05f, 1.15f)



        // ---- Background layer geometry ---------------------------------------------------
        //
        // The sky holds two things that tile oppositely: the star field wraps, the sun and the moon must not
        // leave the screen ([celestialParallaxOffset], `DESIGN_NOTES.md` §12, decision 19).

        /**
         * The band at each side of the screen, a fraction of its width, that the celestial body's rest position
         * never enters. [drawCelestialBody] places the body by it and [celestialParallaxOffset] bounds its travel by
         * it, so the two cannot disagree.
         */
        const val CELESTIAL_MARGIN_FRACTION = 0.12f

        /**
         * The celestial disc's radius as a fraction of screen width, before [drawCelestialBody] doubles it: kept
         * undoubled and multiplied by 2f at each use (`screenWidth * 0.055f * 2f`), which a folded 0.11f would
         * round differently.
         */
        const val CELESTIAL_RADIUS_FRACTION = 0.055f

        /** The horizon the sun's and the moon's arc rises from, a share of the screen's height ([drawCelestialBody]). */
        const val HORIZON_Y_FRACTION_OF_ARC = 0.62f

        /** Largest star radius [regenerateStars] can produce, in canvas pixels. */
        const val MAX_STAR_RADIUS_PX = 2.4f + 3.2f

        /**
         * What [drawStars] divides a star's radius by before blitting the sparkle: **the sprite's scale, not the
         * star's size.** At 16 the largest sparkle reaches `30/16 x 5.6 = 10.5` px, large enough for its four
         * unequal points to read as a star and not a one-pixel cross.
         */
        const val STAR_SPRITE_RADIUS_DIVISOR = 16f

        /**
         * Half the sparkle bitmap's span in local units: `star_sparkle.png` is 180 px, 60 units, centred on the
         * star by [STAR_SPRITE_ORIGIN_UNITS], its negation; `SkySpriteAnchoringTest` checks the pair against the
         * PNG's header.
         */
        const val STAR_SPRITE_HALF_UNITS = 30f

        /**
         * How far a sparkle reaches left and right of its star: the bitmap's half-span scaled as [drawStars] scales
         * it, `1.875 x` the largest radius, 10.5 px. The star field's tile range is derived from it, since reserving
         * less than is drawn drops a sparkle where the field wraps. Reserved on the bitmap, not on its ink (which
         * reaches 9.57 px): over-reserving costs a comparison, under-reserving a visible clip.
         */
        const val STAR_SPRITE_LEFT_EXTENT_PX =
            STAR_SPRITE_HALF_UNITS / STAR_SPRITE_RADIUS_DIVISOR * MAX_STAR_RADIUS_PX
        const val STAR_SPRITE_RIGHT_EXTENT_PX =
            STAR_SPRITE_HALF_UNITS / STAR_SPRITE_RADIUS_DIVISOR * MAX_STAR_RADIUS_PX

        // ---- Sky sprite blit geometry ----------------------------------------------------
        //
        // Each sky sprite's convention and the origin that centres it, as constants a test can reach: the PNG's
        // size, the convention it is read with and the origin are only right together, and nothing in a PNG says
        // which convention applies. `SkySpriteAnchoringTest` checks them against the PNG headers.

        /**
         * `firework` is 240x240 -- 80x80 local units -- anchored on its own centre; a fully expanded burst reaches
         * [FIREWORK_REACH_UNITS].
         */
        const val FIREWORK_SPRITE_HALF_UNITS = 40f
        const val FIREWORK_REACH_UNITS = 120f

        /**
         * The arc's radius in local units, which the on-screen radius is divided by: a scale reference, not the
         * cropped canvas's half-width (99 units), which would draw the rainbow a percent larger.
         */
        const val RAINBOW_SPRITE_HALF_WIDTH_UNITS = 100f

        /** Where the cropped sprite is blitted: centred across, its bottom edge on the arc's centre. */
        const val RAINBOW_SPRITE_ORIGIN_X_UNITS = -99f
        const val RAINBOW_SPRITE_ORIGIN_Y_UNITS = -99f

        /** The rainbow's peak alpha. */
        const val RAINBOW_MAX_ALPHA = 200f

        /**
         * `lightning_bolt` is 90x252, so 30x84 local units, hanging from its own top edge; the width only centres
         * the bolt, the height is the scale reference.
         */
        const val LIGHTNING_BOLT_WIDTH_UNITS = 30f
        const val LIGHTNING_BOLT_HEIGHT_UNITS = 84f

        /**
         * How far down a bolt reaches, a fraction of the screen height. **A bolt never out-tops the skyline it
         * strikes behind**, which is the painted buildings, not the size table (perspective shrinks a far tower): on
         * a 1080x2424 frame the tallest painted building is 288 px, and 0.065 to 0.095 gives 154 to about 225 px,
         * 0.53 to 0.78 of it. The spread is kept because identical strikes read as a loop.
         */
        const val LIGHTNING_BOLT_MIN_HEIGHT_FRACTION = 0.065f
        const val LIGHTNING_BOLT_HEIGHT_SPREAD_FRACTION = 0.030f

        /**
         * The white wash a strike puts over the whole frame at its brightest: 71 % opacity, fading at 3.0/s, so a
         * strike blanks the scene for a third of a second. Strong, and kept so.
         */
        const val LIGHTNING_VEIL_MAX_ALPHA = 180f

        /**
         * Where the light inside the Halloween moon's carved face blits, from the moon's centre, in px of the moon's
         * 240-px frame: the corner of `moon_jack_o_lantern_light`, which `tools/assets/moon/build_jack_moon.py` trims
         * to the face. `tests/test_jack_moon.py` compares these with what the generator works out.
         */
        const val HALLOWEEN_LIGHT_ORIGIN_X = -68f
        const val HALLOWEEN_LIGHT_ORIGIN_Y = -42f

        /**
         * The same for the laughing face's light, `moon_jack_o_lantern_laugh_light`, which
         * `tools/assets/moon/build_jack_moon_laugh.py` trims to its face; `tests/test_jack_moon_laugh.py` compares.
         */
        const val HALLOWEEN_LAUGH_LIGHT_ORIGIN_X = -70f
        const val HALLOWEEN_LAUGH_LIGHT_ORIGIN_Y = -45f

        /**
         * Half the width of the three flames a pumpkin the Headless Horseman has thrown carries, in the group's units:
         * as wide as the sleigh's gift ([SANTA_GIFT_HALF_UNITS]).
         */
        const val HORSEMAN_FLAME_HALF_UNITS = 4.5f

        /**
         * The blood rain's colours (`DESIGN_NOTES.md` §12, decision 78): a night and a day red, blended on the hour, in
         * place of the rain's colours while *Blood rain* is on, then carried off the sky by [PRECIPITATION_MIN_LUMA_GAP]
         * as the rain's own are; and its drops [BLOOD_RAIN_THICKER] times as thick, since at the rain's own width
         * (about 1.2 px on the BV6600) the red does not read on a dark sky.
         */
        const val BLOOD_RAIN_NIGHT = 0xFFB8142A.toInt()
        const val BLOOD_RAIN_DAY = 0xFFD41C2C.toInt()
        const val BLOOD_RAIN_THICKER = 2f

        /**
         * The horror sky's four corners: near-black overhead, hard orange at the horizon, flat paper tones with the
         * blend doing the work. The day pair is only light enough to keep sunrise and sunset legible: this sky is
         * meant to look wrong at noon.
         */
        const val HORROR_SKY_TOP_NIGHT = 0xFF07060A.toInt()
        const val HORROR_SKY_TOP_DAY = 0xFF1A1020.toInt()
        const val HORROR_SKY_LOW_NIGHT = 0xFFB03A06.toInt()
        const val HORROR_SKY_LOW_DAY = 0xFFF07A10.toInt()

        /**
         * Half the side of a gift falling from Santa's sleigh, in the group's units: 9 units across, as large as the
         * presents piled behind him, and at the group's scale so it is in proportion on every phone.
         */
        const val SANTA_GIFT_HALF_UNITS = 4.5f

        /**
         * The dolphin's re-entry splash. The leap is `sin(DOLPHIN_LEAP_RATE * t + phase * 6.28)`, one cycle about
         * every 7 s, drawn while positive, so the animal meets the water when the cycle passes 0.5;
         * [SPLASH_WINDOW_CYCLES] of the cycle after that, about 0.42 s, reads as an impact. Nothing is stored: the
         * splash is timed off the same phase, so no frame can miss it, a frame drawn as the wallpaper resumes mid-leap
         * included.
         */
        const val DOLPHIN_LEAP_RATE = 0.9f
        const val TWO_PI = 6.2831855f
        const val SPLASH_WINDOW_CYCLES = 0.06f
        const val SPLASH_FRAME_SPLIT = 0.4f
        const val SPLASH_ORIGIN_X_UNITS = -27f
        const val SPLASH_ORIGIN_Y_UNITS = -18f


        /** One star in this many is a sparkle, the rest points: about a dozen in a full field, bright stars, not a motif. */
        const val STAR_SPARKLE_EVERY = 5

        /** The four leaf colours; a constant rather than an array built every frame. */
        private val FALLING_LEAF_PALETTE = intArrayOf(
            0xFFD2691E.toInt(), // orange
            0xFFB5451B.toInt(), // rust red
            0xFFE0A93A.toInt(), // gold/yellow
            0xFF8F3B1B.toInt(), // deep brown-red
        )

        /** How wide a distant house's Christmas bulb is on screen, in pixels: 2 px and a little over. */
        const val DISTANT_BULB_PX = 2.2f

        /** How much each hill layer is darkened towards black; one layer, not darkened. */
        private val HILL_LAYER_DARKEN = floatArrayOf(0f)

        /** How far each hill tile reaches past its own edges, so two copies overlap at the seam. */
        const val HILL_TILE_SEAM_OVERLAP_PX = 1f

        /**
         * How fast each far layer slides with the scroll, slower the farther: the back mountains, the front
         * mountains, the lake (between the front mountains on its far edge and the hills over its near one), the hill.
         */
        const val MOUNTAINS_BACK_PARALLAX = 0.04f
        const val MOUNTAINS_FRONT_PARALLAX = 0.08f
        const val HILL_PARALLAX = 0.15f
        const val LAKE_PARALLAX = (MOUNTAINS_FRONT_PARALLAX + HILL_PARALLAX) / 2f

        /**
         * The candidate seed for one effect ([EffectId]) of [theme], which every candidate pool draws its noise from;
         * internal so the tests measure this one. The ordinals are small and consecutive, so each is multiplied by a
         * large odd constant before the `xor` to spread it over the whole word and keep effects uncorrelated.
         * `String.hashCode` is fixed by the language: a theme is the same scene on every device and run.
         */
        internal fun seedFor(theme: SceneTheme, effectOrdinal: Int): Int =
            theme.id.hashCode() xor (effectOrdinal * -0x61c88647)

        /** [x] folded into `[0, tileWidth)`: the position of the copy of a repeating thing that lies in the first tile. */
        internal fun foldIntoTile(x: Float, tileWidth: Float): Float {
            val r = x % tileWidth
            return if (r < 0f) r + tileWidth else r
        }

        /** Which of these stars sparkle: the `ceil(n / STAR_SPARKLE_EVERY)` largest, ties to the lower index. */
        internal fun starSparkleIndices(radii: FloatArray): Set<Int> {
            val n = (radii.size + STAR_SPARKLE_EVERY - 1) / STAR_SPARKLE_EVERY
            return radii.indices.sortedWith(compareByDescending<Int> { radii[it] }.thenBy { it }).take(n).toSet()
        }

        /**
         * The cream the sparkle art is drawn in, so a point and a sparkle are the same star: `StarFieldColourTest`
         * reads the one fully opaque colour of `star_sparkle.png` and holds this equal to it.
         */
        const val STAR_POINT_COLOR = 0xFFFBF4E6.toInt()

        /** How many stars a full *# of Stars* places. */
        const val STAR_COUNT_AT_FULL_DENSITY = 70

        /**
         * How many stars the field holds at [density], asked by `regenerateStars` and by the settings screen: below
         * 1/70 it is none, and *Show Stars* reads off there rather than on over an empty sky.
         */
        fun starCountFor(density: Float): Int = (STAR_COUNT_AT_FULL_DENSITY * density.coerceIn(0f, 1f)).toInt()

        /**
         * How much of a star's radius a point covers. The sparkle is much narrower between its four tips than at
         * them, so a point matches its apparent weight, not its 1.875-radius reach, and the field stays mostly faint
         * points with a few bright stars.
         */
        const val STAR_POINT_RADIUS_SCALE = 0.55f

        /**
         * How far a dolphin noses up and down along its arc; how far it rises is
         * [SceneSpace.DOLPHIN_LEAP_METRES].
         */
        const val DOLPHIN_LEAP_TILT_DEGREES = 26f

        /**
         * Where `dolphin_body`'s pixel (0,0) goes: the animal's ink centred on the point its leap arc is computed
         * for. The sprite is 342x171 px -- 114x57 local units -- with its ink at `1,3..342,171`, so the drawing's
         * centre is (57.167, 29.0) units, and the pair is its negative; `DolphinLeapOriginTest` re-measures it off
         * the alpha channel.
         */
        const val DOLPHIN_ORIGIN_X_UNITS = -57.166668f
        const val DOLPHIN_ORIGIN_Y_UNITS = -29f

        /**
         * Where the bird bitmap is blitted, in raw pixels. The wing-flap mirrors the canvas about y = 0, so the y
         * origin puts the flap's axis on canvas row 15: changing it, or the canvas, without the other moves the bird.
         */
        const val BIRD_SPRITE_ORIGIN_X_PX = -25f
        const val BIRD_SPRITE_ORIGIN_Y_PX = -15f

        /** Centres a 240px raw-pixel disc in the `radius / 120f` space of the sun and moon. */
        const val CELESTIAL_DISC_ORIGIN_UNITS = -120f
        /**
         * Where `cloud_body.png` is blitted so its content centre lands on `(cx, laneY)`, the point the placement and
         * coverage use: `cloud_body.png` is 798x396 px = 266x132 units with content filling it, and the origin is
         * derived from [CloudCoverage]'s half-sizes, so a re-crop cannot strand it.
         */
        val CLOUD_BLIT_X = -CloudCoverage.CLOUD_CONTENT_HALF_UNITS

        /** See [CLOUD_BLIT_X]. */
        val CLOUD_BLIT_Y = -CloudCoverage.CLOUD_CONTENT_HALF_HEIGHT_UNITS

        val CELESTIAL_DISC_SCALE = SpriteScale.CANVAS_PIXELS

        /**
         * Centres the 396 px sunburst in the same space: its ring, at 154..166 units, starts past the disc's 120,
         * which is what makes it read as a sunburst rather than a fatter sun.
         */
        const val SUN_GLOW_ORIGIN_UNITS = -198f
        val SUN_GLOW_SCALE = SpriteScale.CANVAS_PIXELS

        /**
         * Centres the sparkle in the `star.radius / STAR_SPRITE_RADIUS_DIVISOR` space of a star; derived from
         * [STAR_SPRITE_HALF_UNITS], so the origin and the tile extents agree on the bitmap's size.
         */
        const val STAR_SPRITE_ORIGIN_UNITS = -STAR_SPRITE_HALF_UNITS
        val STAR_SPRITE_SCALE = SpriteScale.SCENE_UNITS

        /**
         * First tile index of the star field that can reach the viewport, given [shiftXWrapped] in `(-tileWidth, 0]`.
         * The field repeats every screen width ([regenerateStars] lays it over `[0, screenWidth)`), so the
         * neighbouring tiles are drawn too, or a starless band opens and the field snaps at the wrap: a range derived
         * from the geometry, as [SceneObjectRenderer.firstVisibleTileOffset]. Pure, so it can be unit tested.
         */
        fun firstStarTileOffset(
            shiftXWrapped: Float,
            tileWidth: Float,
            leftExtentPx: Float,
            rightExtentPx: Float,
        ): Int {
            if (tileWidth <= 0f) return 0
            // Tile k spans [shift + k*tile - left, shift + k*tile + tile + right).
            // Its right end is past x = 0 when k > (-shift - tile - right) / tile.
            val firstAbove = (-shiftXWrapped - tileWidth - rightExtentPx) / tileWidth
            return kotlin.math.floor(firstAbove).toInt() + 1
        }

        /**
         * Exclusive upper bound of the star-field tile range ([firstStarTileOffset]); 0 with no width yet, so the
         * caller's `until` loop is empty.
         */
        fun starTileOffsetLimit(
            shiftXWrapped: Float,
            tileWidth: Float,
            viewportWidth: Float,
            leftExtentPx: Float,
            rightExtentPx: Float,
        ): Int {
            if (tileWidth <= 0f) return 0
            // Tile k's left end is before x = viewport when k < (viewport + left - shift) / tile.
            val lastBelow = (viewportWidth + leftExtentPx - shiftXWrapped) / tileWidth
            return kotlin.math.ceil(lastBelow).toInt()
        }

        /**
         * The celestial body's horizontal parallax offset: bounded, never cyclic. The sun and the moon are single
         * objects: an unwrapped shift walks them off screen, a wrapped one makes them vanish and pop back, a tiled
         * one shows two. So the offset is bounded by the body's own geometry:
         *
         * ```
         * restCx    = margin + celestialX * (screenWidth - 2 * margin)
         * radius    = screenWidth * CELESTIAL_RADIUS_FRACTION * 2
         * slackLeft = restCx - radius          // px before the disc's left edge reaches x = 0
         * travel    = min(2 * parallax * screenWidth, slackLeft)
         * offset    = -((sway + swipe) / 2) * travel
         * ```
         *
         * [CELESTIAL_MARGIN_FRACTION] (0.12) exceeds the doubled [CELESTIAL_RADIUS_FRACTION] (0.11), so the rest
         * position always leaves a gap to the left edge, and the body uses exactly that gap (0.01 of the width at the
         * arc's ends, where it barely moves). The two inputs are averaged, each entitled to the full
         * `parallax * screenWidth`, hence the 2 in `travel`: [swipeOffset], coerced into `0..1` so the disc stays on
         * screen whatever a launcher reports; and [driftAccum], which grows without bound, through a cosine of the
         * background's own wrap phase, 0 with zero slope at both ends of the cycle, so it crosses the wrap seam with
         * no step in position or speed. Allocation-free.
         */
        fun celestialParallaxOffset(
            celestialX: Float,
            screenWidth: Float,
            parallax: Double,
            driftAccum: Double,
            swipeOffset: Float,
        ): Float {
            if (screenWidth <= 0f) return 0f
            val margin = screenWidth * CELESTIAL_MARGIN_FRACTION
            val restCx = margin + celestialX * (screenWidth - 2f * margin)
            val radius = screenWidth * CELESTIAL_RADIUS_FRACTION * 2f
            val slackLeft = (restCx - radius).coerceAtLeast(0f)
            val demand = 2.0 * parallax * screenWidth
            val travel = minOf(demand, slackLeft.toDouble())
            if (travel <= 0.0) return 0f
            val sway = (1.0 - cos(2.0 * Math.PI * parallax * driftAccum)) * 0.5
            val u = (sway + swipeOffset.coerceIn(0f, 1f)) * 0.5
            return (-u * travel).toFloat()
        }
    }

    // groundY for a given depthFraction comes from SceneSpace.groundYFraction, which is the one
    // place the ground plane is defined.

    fun onSizeChanged(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        regenerateStars()
    }

    private fun regenerateStars() {
        val rnd = Random(42)
        val count = starCountFor(sceneCustomization.stars.density)
        val placed = List(count) {
            Star(
                x = rnd.nextFloat() * screenWidth,
                y = rnd.nextFloat() * screenHeight * 0.55f,
                // 2.4 to 5.6 px: smaller, the sparkle sprite reads as a blur.
                radius = 2.4f + rnd.nextFloat() * 3.2f,
                phase = rnd.nextFloat() * 6.28f,
                sparkle = false,
            )
        }
        // One star in [STAR_SPARKLE_EVERY] is a sparkle, the largest ones: the few brighter stars of a night sky,
        // at the sizes the sparkle sprite reads at.
        val sparkles = starSparkleIndices(FloatArray(count) { placed[it].radius })
        stars = placed.mapIndexed { i, star -> if (i in sparkles) star.copy(sparkle = true) else star }
        cachedStarsDensity = sceneCustomization.stars.density
    }

    // ---- The weather this frame is actually in, evaluated once at the top of the frame ---------
    //
    // Read by the umbrellas and the waves, so the two cannot disagree. "Raining" is what `drawPrecipitation`
    // paints rain on (it derives it for itself): **snow is not rain**, so no umbrella in the snow and no waves,
    // not even in a live thundersnow, where the lightning flashes over a calm lake ([LiveWeatherSceneRules.wavesOnLake]).
    private var rainingNow = false
    private var snowingNow = false
    private var rainIntensityNow = 0f
    /** The storm gate the lightning already uses, so the two cannot disagree about a thunderstorm. */
    private var stormActiveNow = false

    private fun updateWeatherPredicates(deltaSeconds: Float) {
        val live = liveWeatherOverride
        val precip = sceneCustomization.precipitation
        // The cover the scene draws, eased toward the forecast's ([CloudCoverFade]): worked out once here because
        // [drawSky] dims by [stormStrength] before [drawClouds] runs, and a cover eased there would leave the sky
        // a frame behind its clouds.
        drawnCloudCover = if (live == null && liveWeatherLapsed) {
            // The theme's own cover as `LiveWeatherSceneRules.cloudDensity` would draw it: its
            // slider when its cloud switch is on, no clouds when it is off.
            val clouds = sceneCustomization.clouds
            cloudCoverFade.coverLapsingTo(if (clouds.visible) clouds.density else 0f, deltaSeconds)
        } else {
            cloudCoverFade.coverToward(if (live == null) Float.NaN else live.cloudCoverFraction, deltaSeconds)
        }
        if (live != null) {
            rainingNow = live.precipitationType == PrecipitationType.RAIN && live.precipitationIntensity > 0f
            snowingNow = live.precipitationType == PrecipitationType.SNOW && live.precipitationIntensity > 0f
            rainIntensityNow = if (rainingNow) live.precipitationIntensity else 0f
        } else {
            rainingNow = precip.visible && precip.intensity > 0f && precip.type == PrecipitationType.RAIN
            snowingNow = precip.visible && precip.intensity > 0f && precip.type == PrecipitationType.SNOW
            rainIntensityNow = if (rainingNow) precip.intensity else 0f
        }
        stormActiveNow = LiveWeatherSceneRules.stormActive(
            liveIsThunderstorm = live?.isThunderstorm,
            themePrecipitationVisible = precip.visible,
            themePrecipitationIsRain = precip.type == PrecipitationType.RAIN,
            themeThunderstorm = precip.thunderstorm,
        )
    }

    // ---- The waves ---------------------------------------------------------------------------
    /** Which wave slots are on the water: while it rains a slot changes only off screen ([WAVE_POOL]). */
    private val waveActive = BooleanArray(WAVE_POOL)
    /** This frame's two derived tints, computed once in [gatherWaves] and used by [drawWaveItem]. */
    private var waveBodyTint = 0
    private var waveFoamTint = 0

    /**
     * Places this frame's waves into the lake's item slots, so they are painted in the same far-to-near pass as
     * the boats and the dolphins ([LakeLanes]): drawn before the boats, every wave would sit behind every boat,
     * a breaker on the near water cut off by a farther hull. Every kind is keyed by where its own drawing meets
     * the water ([LakeLanes.visibleWaterline]): a wave's base, a boat's hull [SAILBOAT_HULL_WATERLINE_UNITS] under
     * its placement point, a dolphin's lane, so the three compare waterline to waterline.
     */
    private fun gatherWaves(from: Int, top: Float, bandHeight: Float, dayBlend: Float, elapsedSeconds: SceneTime): Int {
        var count = from
        // A thunderstorm is a full sea; plain rain scales the pool by how hard it is raining.
        val waveDensity = if (stormActiveNow && !snowingNow) 1f else rainIntensityNow
        val effectOffset = CandidateThreshold.offsetFor(EffectId.LAKE_SPARKLES)
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(waveDensity, WAVE_POOL, effectOffset)
        val waveSeed = seedFor(EffectId.LAKE_SPARKLES) xor 0x7A7
        val hillNeverCoveredAbsY = (yOffsets[0] + heightFractions[0] * 0.02f) * screenHeight
        val waveLaneMax = if (bandHeight > 1f) ((hillNeverCoveredAbsY - top) / bandHeight).coerceIn(0.06f, 0.9f) else 0.5f

        // The two papers, derived from the water they will lie on: the same mirror colour
        // `drawLakeBand` paints, carried to the lumas [WaveTint] places.
        val waveSurface = ColorUtils.blendARGB(lakePaint.color, skyHorizonColorNow, LAKE_MIRROR_SKY_SHARE)
        val waveSurfaceLuma = rec601Luma(waveSurface)
        val bodyGap = WaveTint.gapAt(dayBlend, WAVE_BODY_LUMA_GAP, WAVE_BODY_LUMA_GAP_NIGHT)
        val foamGap = WaveTint.gapAt(dayBlend, WAVE_FOAM_LUMA_GAP, WAVE_FOAM_LUMA_GAP_NIGHT)
        waveBodyTint = WaveTint.carryTo(waveSurface, waveSurfaceLuma, WaveTint.bodyLuma(waveSurfaceLuma, bodyGap, foamGap))
        waveFoamTint = WaveTint.carryTo(waveSurface, waveSurfaceLuma, WaveTint.foamLuma(waveSurfaceLuma, bodyGap, foamGap))

        val waveBase = WAVE_METRES_LONG * SceneSpace.LAKE_PIXELS_PER_METRE / WAVE_UNITS_WIDE *
            SceneSpace.sceneScale(screenHeight.toFloat())
        for (i in 0 until WAVE_POOL) {
            val waveWanted = waveDensity > 0f && CandidateThreshold.isPresent(i, waveDensity, effectOffset, fallbackIndex)
            val waveLane = waveLaneMax * (0.42f + 0.5f * (i + CandidateNoise.value(waveSeed, i, CandidateNoise.CH_Y)) / WAVE_POOL)
            val waveScale = waveBase * (0.85f + 0.3f * waveLane / waveLaneMax)
            val waveHalfWidth = WAVE_UNITS_WIDE * waveScale / 2f
            val waveSpeed = CandidateNoise.range(waveSeed, i, CandidateNoise.CH_SPEED, 0.018f, 0.026f)
            val wavePhase = CandidateNoise.value(waveSeed, i, CandidateNoise.CH_PHASE)
            val waveX = elapsedSeconds.cycle(waveSpeed, wavePhase) * (screenWidth + 2f * waveHalfWidth + 20f) - waveHalfWidth - 10f
            val waveOffScreen = waveX + waveHalfWidth < 0f || waveX - waveHalfWidth > screenWidth
            // Membership changes only out of sight -- the car rule, applied to the sea.
            if (waveActive[i] != waveWanted && waveOffScreen) waveActive[i] = waveWanted
            if (!waveActive[i] || waveOffScreen) continue
            val waveY = top + bandHeight * waveLane + elapsedSeconds.sinAt(0.6f, wavePhase * 6.28f) * 1.5f
            lakeItemX[count] = waveX
            lakeItemY[count] = waveY
            lakeItemScale[count] = waveScale
            lakeItemPhase[count] = 0f
            lakeItemIsDolphin[count] = false
            lakeItemIsWave[count] = true
            lakeItemDepth[count] = LakeLanes.visibleWaterline(LakeLanes.Kind.WAVE, waveY, 0f, screenHeight.toFloat())
            count++
        }
        return count
    }

    private fun drawWaveItem(canvas: SceneCanvas, x: Float, y: Float, spriteScale: Float) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(spriteScale, spriteScale)
        sprites.drawTinted(canvas, R.drawable.wave_tube_body, -WAVE_UNITS_WIDE / 2f, -WAVE_UNITS_TALL, SpriteScale.SCENE_UNITS, waveBodyTint)
        sprites.drawTinted(canvas, R.drawable.wave_tube_crest, -WAVE_UNITS_WIDE / 2f, -WAVE_UNITS_TALL, SpriteScale.SCENE_UNITS, waveFoamTint)
        canvas.restore()
    }

    /** The canvas and clock of the frame being drawn, for the three effect lambdas below; null between frames. */
    private var effectCanvas: SceneCanvas? = null
    private var effectElapsed = SceneTime(0.0)

    /** The frame's scene clock, for what is drawn before the effects and moves on it: the moon's laugh. */
    private var frameTime = SceneTime(0.0)

    /** Whether the Halloween moon shows its laughing face this frame, and how far it is shaking ([LaughingMoon]). */
    private var laughingNow = false
    private var moonShakeDegrees = 0f

    /** Built once: see the comment where the effects are drawn. */
    private val drawFireworkBurst = FireworkEffect.BurstBlit { x, y, burstScale, alpha ->
        val canvas = effectCanvas
        if (canvas != null) {
            // The sprite's centre on the burst point (origin -40, half of its 80 units); [FIREWORK_REACH_UNITS] over
            // that half-width is the scale of a fully expanded burst.
            canvas.save()
            canvas.translate(x, y)
            val s = burstScale * FIREWORK_REACH_UNITS / FIREWORK_SPRITE_HALF_UNITS
            canvas.scale(s, s)
            sprites.draw(
                canvas,
                R.drawable.firework,
                -FIREWORK_SPRITE_HALF_UNITS,
                -FIREWORK_SPRITE_HALF_UNITS,
                SpriteScale.SCENE_UNITS,
                (alpha * 255).toInt().coerceIn(0, 255),
            )
            canvas.restore()
        }
    }

    /**
     * Built once, like [drawFireworkBurst]: the group from its pieces at this screen's scale ([SantaSleighDrawer]),
     * sized with the people, and drawn at night as by day.
     */
    private val drawSleigh = SantaSleighEffect.SleighBlit { x, y, dir, alpha ->
        val canvas = effectCanvas
        if (canvas != null) santaSleighDrawer.draw(canvas, x, y, dir, alpha, effectElapsed, sleighPxPerUnit)
    }

    /**
     * Built once, as [drawSleigh]: the Headless Horseman from his pieces at this screen's scale, his arm and the pumpkin
     * in his hand as the effect has them; and where he sits, handed back to the flight so a pumpkin leaves his hand
     * where it was drawn.
     */
    private val drawHorseman = HeadlessHorsemanEffect.RiderBlit { x, y, dir, alpha, armDegrees, handPumpkin ->
        val canvas = effectCanvas
        if (canvas != null) {
            headlessHorsemanDrawer.draw(canvas, x, y, dir, alpha, effectElapsed, horsemanPxPerUnit, armDegrees, handPumpkin)
            val flight = headlessHorsemanEffect.flight
            flight.seatX = headlessHorsemanDrawer.seatX(effectElapsed)
            flight.seatY = headlessHorsemanDrawer.seatY(effectElapsed)
        }
    }

    /** Built once, as [drawHorseman]: a pumpkin he has thrown. */
    private val drawThrownPumpkin = HeadlessHorsemanEffect.LanternBlit { x, y, dir, degrees, alpha ->
        val canvas = effectCanvas
        if (canvas != null) headlessHorsemanDrawer.drawThrownPumpkin(canvas, x, y, dir, degrees, alpha, horsemanPxPerUnit)
    }

    /** The Horseman's scale, his pumpkins and his margins for this screen's height; nothing when it has not changed. */
    private fun updateHorsemanGeometry() {
        if (horsemanGeometryHeight == screenHeight) return
        horsemanGeometryHeight = screenHeight
        val sc = HeadlessHorsemanScale.pxPerUnit(screenHeight.toFloat())
        horsemanPxPerUnit = sc
        headlessHorsemanEffect.setGroupGeometry(
            flameHalf = HORSEMAN_FLAME_HALF_UNITS * sc,
            groupHalfWidth = headlessHorsemanDrawer.halfWidthPx(sc),
            pxPerUnit = sc,
        )
    }

    /** The sleigh's scale, its gifts and its margins for this screen's height; nothing when it has not changed. */
    private fun updateSleighGeometry() {
        if (sleighGeometryHeight == screenHeight) return
        sleighGeometryHeight = screenHeight
        val sc = SantaSleighScale.pxPerUnit(screenHeight.toFloat())
        sleighPxPerUnit = sc
        santaSleighEffect.setGroupGeometry(
            sackDx = santaSleighDrawer.sackDxPx(sc),
            sackDy = santaSleighDrawer.sackDyPx(sc),
            giftHalf = SANTA_GIFT_HALF_UNITS * sc,
            groupHalfWidth = santaSleighDrawer.halfWidthPx(sc),
        )
    }

    fun draw(canvas: SceneCanvas, framePhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime, deltaSeconds: Float) {
        // Halloween's night sky by day: while it is in force every layer below is drawn at night, the sky, the
        // landscape and the town, and the opening hours keep the clock's hour ([nightOverDay]).
        val dayPhase = stillMoon(nightOverDay(framePhase))
        frameTime = elapsedSeconds
        updateWeatherPredicates(deltaSeconds)
        // One direction only: a screen width at parallax 1.0 every ~25 s at scrollSpeed 1.0 (the hills, at 0.15,
        // take ~167 s), frozen at 0. Never reset: each layer wraps its own shift by its own tile, and a reset of
        // the shared value would jump every layer whose parallax is not 1.0 by a different amount at once.
        continuousScrollAccum += deltaSeconds * scrollSpeed * 0.04

        // The field is built again only when the density changes (and on a new size, `onSizeChanged`): the first frame
        // finds `cachedStarsDensity` at -1. An empty field is a field -- the one *# of Stars* at 0 asks for -- and is
        // not built again for being empty: that would be eight objects at every frame (`FrameAllocationTest`).
        if (cachedStarsDensity != sceneCustomization.stars.density) regenerateStars()
        syncObjectRendererWithTheme()
        rebuildHillPathsIfNeeded()
        drawSky(canvas, dayPhase)
        // The sky, when the user scrolls it (off by default), moves at the hill layer's rate: the farthest things
        // in the scene, read as farther than the hill. The stars tile and the sun or the moon does not.
        if (scrollBackground) {
            val bgTileWidth = screenWidth.toFloat()
            val bgParallax = parallaxFactors[0].toDouble() * parallaxStrength
            val bgShift = wrappedScrollShift(bgParallax, bgTileWidth)

            // Stars: a tiled pattern a screen wide, drawn over the tile range its geometry implies, two copies,
            // three when a sparkle pokes across a seam.
            val firstTile = firstStarTileOffset(
                bgShift, bgTileWidth, STAR_SPRITE_LEFT_EXTENT_PX, STAR_SPRITE_RIGHT_EXTENT_PX,
            )
            val tileLimit = starTileOffsetLimit(
                bgShift, bgTileWidth, screenWidth.toFloat(),
                STAR_SPRITE_LEFT_EXTENT_PX, STAR_SPRITE_RIGHT_EXTENT_PX,
            )
            canvas.save()
            canvas.translate(bgShift + firstTile * bgTileWidth, 0f)
            for (tile in firstTile until tileLimit) {
                drawStars(canvas, dayPhase, elapsedSeconds)
                canvas.translate(bgTileWidth, 0f)
            }
            canvas.restore()

            // Sun or moon: one object, moved by a bounded offset ([celestialParallaxOffset]).
            drawCelestialBody(
                canvas,
                dayPhase,
                celestialParallaxOffset(
                    celestialX = dayPhase.celestialX,
                    screenWidth = screenWidth.toFloat(),
                    parallax = bgParallax,
                    driftAccum = continuousScrollAccum,
                    swipeOffset = if (swipeScrollEnabled) homeScreenOffset else 0f,
                ),
            )
        } else {
            drawStars(canvas, dayPhase, elapsedSeconds)
            drawCelestialBody(canvas, dayPhase)
        }
        drawClouds(canvas, dayPhase, elapsedSeconds, deltaSeconds)
        // Behind the mountains and hills, which occlude its base ([drawRainbow]).
        drawRainbow(canvas, dayPhase)
        drawMountains(canvas, dayPhase)
        drawBirds(canvas, dayPhase, elapsedSeconds)
        // Depth order: the lake before the hills, whose wavy silhouette paints over the water's flat edge.
        drawLake(canvas, dayPhase, elapsedSeconds)
        drawHillLayers(canvas, dayPhase)
        // The blend (the dusk crossfade of the cars) and the hour (the business hours) come from the frame's
        // dayPhase, never from a clock of their own, so a fixed hour moves them as it moves the sun.
        objectRenderer.update(deltaSeconds, dayPhase.dayBlend)
        objectRenderer.rainingNow = rainingNow   // who has an umbrella up
        objectRenderer.draw(canvas, objectGroundGeometry, dayPhase.dayBlend, elapsedSeconds, screenWidth.toFloat(), screenHeight.toFloat(), dayPhase.hour24)

        val fireworksEnabled = theme.hasFireworks && dayPhase.dayBlend < 0.35f
        fireworkEffect.update(deltaSeconds, fireworksEnabled, screenWidth.toFloat(), screenHeight.toFloat())
        // The three effects draw through member lambdas reading [effectCanvas] and [effectElapsed]: a lambda that
        // captures `canvas` is a new object every frame, and nothing may be allocated on the frame path.
        effectCanvas = canvas
        effectElapsed = elapsedSeconds
        fireworkEffect.draw(drawFireworkBurst)

        updateSleighGeometry()
        santaSleighEffect.update(deltaSeconds, sceneCustomization.santaEnabled, screenWidth.toFloat(), screenHeight.toFloat())
        santaSleighEffect.draw(canvas, elapsedSeconds, screenWidth.toFloat(), drawSleigh)
        // The Headless Horseman, as the sleigh: his own switch and flights, by night as by day; updated after the
        // sleigh, he keeps out of its way, a turn waiting while the sleigh is in the sky or due.
        updateHorsemanGeometry()
        val sleighDueIn = if (sceneCustomization.santaEnabled) santaSleighEffect.secondsToFlight else Float.POSITIVE_INFINITY
        headlessHorsemanEffect.update(
            deltaSeconds, sceneCustomization.headlessHorsemanEnabled, sleighDueIn,
            screenWidth.toFloat(), screenHeight.toFloat(),
        )
        headlessHorsemanEffect.draw(canvas, elapsedSeconds, screenWidth.toFloat(), drawHorseman, drawThrownPumpkin)
        effectCanvas = null

        // The nearest layers, in front of the houses and cars: the precipitation, the falling leaves, the flash.
        drawPrecipitation(canvas, dayPhase, elapsedSeconds)
        drawFallingLeaves(canvas, dayPhase, elapsedSeconds)
        // A forecast's thunderstorm flashes whatever the theme's own switch; the precedence is
        // LiveWeatherSceneRules', shared with the clouds and the rain so the layers agree.
        val stormActive = LiveWeatherSceneRules.stormActive(
            liveIsThunderstorm = liveWeatherOverride?.isThunderstorm,
            themePrecipitationVisible = sceneCustomization.precipitation.visible,
            themePrecipitationIsRain = sceneCustomization.precipitation.type == PrecipitationType.RAIN,
            themeThunderstorm = sceneCustomization.precipitation.thunderstorm,
        )
        updateLightning(deltaSeconds, stormActive)
        drawLightningFlash(canvas)
    }

    private fun blendColor(night: Int, day: Int, blend: Float): Int =
        ColorUtils.blendARGB(night, day, blend.coerceIn(0f, 1f))

    /**
     * How bad the weather looks, 0..1 ([StormAtmosphere]), from the Live Weather snapshot only: the theme's own
     * rain is a scene setting, not a forecast, and does not darken the sky. Cheap, read by each layer that needs it.
     */
    private fun stormStrength(): Float {
        val live = liveWeatherOverride ?: return 0f
        return StormAtmosphere.strength(
            precipitationType = live.precipitationType,
            precipitationIntensity = live.precipitationIntensity,
            isThunderstorm = live.isThunderstorm,
            // The drawn cover, so the sky darkens with the clouds as they ease; the reported one before
            // [updateWeatherPredicates] has run.
            cloudCoverFraction = if (drawnCloudCover.isNaN()) live.cloudCoverFraction else drawnCloudCover,
        )
    }

    private fun drawSky(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase) {
        val sky = sceneCustomization.sky
        // Night, twilight and day blended by dayBlend from the six user colours, with a twilight bump near the
        // terminator: [SkyGradient]'s rule, which the gallery card paints with too, so the two show one sky.
        val top = SkyGradient.top(sky, dayPhase.dayBlend)
        val bottom = SkyGradient.bottom(sky, dayPhase.dayBlend, dayPhase.progress)

        // The horror sky replaces the six user colours without editing them, so turning it off gives them back.
        // It keeps the day/night blend, so the sun and the moon still mean something, within near-black
        // overhead and a hard orange at the horizon.
        if (sceneCustomization.horrorSkyEnabled) {
            val horrorTop = SkyGradient.horrorTop(dayPhase.dayBlend)
            val horrorBottom = SkyGradient.horrorBottom(dayPhase.dayBlend)
            // Recorded before the return: they are the sky this frame drew, read by the water's mirror, the
            // waterline, the waves' tints and the rain's colour, under a horror sky in any theme
            // (a switch of its own, not the Halloween theme's alone), where a stale or zero value would be mirrored.
            skyTopColorNow = horrorTop
            skyHorizonColorNow = horrorBottom
            canvas.drawVerticalGradientRect(
                0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), horrorTop, horrorBottom,
            )
            return
        }

        // The weather dims the finished day/night colours rather than replacing them, so 18:30 in heavy rain is
        // a dim sunset, not a night: bad weather is a blend over the day, not a different sky.
        val storm = stormStrength()
        // Both ends kept, weathered, for the water's mirror and the waterline, which reads the sky above the shore.
        skyTopColorNow = StormAtmosphere.dimSky(top, storm)
        skyHorizonColorNow = StormAtmosphere.dimSky(bottom, storm)
        canvas.drawVerticalGradientRect(
            0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(),
            StormAtmosphere.dimSky(top, storm),
            StormAtmosphere.dimSky(bottom, storm),
        )
    }

    /**
     * The star field: four in five stars are points, one disc each ([SpriteBlitter.drawDisc]: a quad of the atlas on
     * the GL path, a circle on a Canvas), and the largest one in [STAR_SPARKLE_EVERY] a rotating four-pointed sparkle,
     * so the sky is mostly points with a few bright stars, not a pattern.
     */
    private fun drawStars(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime) {
        if (!sceneCustomization.stars.visible) return
        val visibility = (1f - dayPhase.dayBlend * 1.6f).coerceIn(0f, 1f)
        if (visibility <= 0f) return
        for (starIndex in stars.indices) {
            val star = stars[starIndex]
            val twinkle = 0.5f + 0.5f * elapsedSeconds.sinAt(1.5f, star.phase)
            val alpha = (255 * visibility * twinkle).toInt().coerceIn(0, 255)
            if (alpha <= 0) continue
            if (!star.sparkle) {
                sprites.drawDisc(canvas, star.x, star.y, star.radius * STAR_POINT_RADIUS_SCALE, STAR_POINT_COLOR, alpha)
                continue
            }
            canvas.save()
            canvas.translate(star.x, star.y)
            canvas.rotate(elapsedSeconds.cycleOf(12f, star.phase * 60f, 360f))
            val s = star.radius / STAR_SPRITE_RADIUS_DIVISOR
            canvas.scale(s, s)
            // Oversampled (SCENE_UNITS): 180 px cover 60 local units, so [STAR_SPRITE_ORIGIN_UNITS] centres it on
            // the star and it reaches 1.875 of the star's radius, deliberately wider than the star; the tiled
            // field's range is derived from that reach ([STAR_SPRITE_LEFT_EXTENT_PX]). Untinted fixed art, cream
            // with a warmer core (decision 26): `theme.starColor` is kept on `SceneTheme` for custom themes' JSON,
            // and not read.
            sprites.draw(
                canvas,
                R.drawable.star_sparkle,
                STAR_SPRITE_ORIGIN_UNITS,
                STAR_SPRITE_ORIGIN_UNITS,
                STAR_SPRITE_SCALE,
                alpha,
            )
            canvas.restore()
        }
    }

    /**
     * @param offsetX horizontal parallax offset in canvas pixels, `0f` when the background does not scroll; added
     *   here rather than by a translate, so its bound is against the rest position ([celestialParallaxOffset]).
     */
    private fun drawCelestialBody(
        canvas: SceneCanvas,
        dayPhase: SunPositionCalculator.DayPhase,
        offsetX: Float = 0f,
    ) {
        val isSun = dayPhase.isSunVisible
        celestialShownNow = false // see the field: the water reads this after the sky is drawn
        if (isSun && !sceneCustomization.sun.visible) return
        if (!isSun && !sceneCustomization.moon.visible) return

        val margin = screenWidth * CELESTIAL_MARGIN_FRACTION
        val cx = margin + dayPhase.celestialX * (screenWidth - 2 * margin) + offsetX
        val horizonY = screenHeight * HORIZON_Y_FRACTION_OF_ARC
        val riseHeight = screenHeight * sceneCustomization.sky.sunCloudHeight
            .coerceIn(SUN_CLOUD_HEIGHT_MIN, SUN_CLOUD_HEIGHT_MAX)
        var cy = horizonY - dayPhase.celestialY * riseHeight
        // The Halloween moon laughing ([LaughingMoon]) hops a little, its glow with it; the lake's light reads its x,
        // which the laugh does not move.
        val laughing = !isSun && sceneCustomization.laughingMoonActive() && LaughingMoon.laughing(frameTime)
        laughingNow = laughing && LaughingMoon.faceOpen(frameTime)
        moonShakeDegrees = if (laughing) LaughingMoon.shakeDegrees(frameTime) else 0f
        if (laughing) cy += LaughingMoon.hop(frameTime) * screenWidth * CELESTIAL_RADIUS_FRACTION * 2f
        celestialShownNow = true
        celestialIsSunNow = isSun
        celestialCxNow = cx

        val radius = screenWidth * CELESTIAL_RADIUS_FRACTION * 2f
        val color = if (isSun) sceneCustomization.sun.color else sceneCustomization.moon.color

        // A storm only makes the sun fainter, never moves it. The moon is not dimmed: a rainy night is already
        // dark, and it is the scene's last light.
        val sunStorm = if (isSun) stormStrength() else 0f
        canvas.drawRadialGlow(
            cx, cy, radius * 3.2f, color,
            // The still moon comes out and fades where it stands, its glow with it ([StillMoon.alpha]).
            (StormAtmosphere.sunAlpha(CELESTIAL_GLOW_CENTRE_ALPHA, sunStorm) * (if (isSun) 1f else stillMoonFrame.alpha)).toInt(),
        )

        if (isSun) {
            val sunAlpha = StormAtmosphere.sunAlpha(255, sunStorm)
            canvas.save()
            canvas.translate(cx, cy)
            val s = radius / 120f
            canvas.scale(s, s)
            // A sunburst ring behind the disc, the glow above giving the ambient falloff.
            // sun_glow.png is 396x396 and its ring sits 154..166px from its own centre, so as a
            // raw-pixel sprite it covers 396 local units and is anchored at -396/2 for the ring to land at
            // 154..166 units, outside the disc's 120 (oversampled, it would sit hidden behind the disc).
            // Both drawings are fixed art and take no sun colour: a colour multiplied over finished art turns
            // the sun near-black for anything but a warm pick. `Sun Color` drives the glow (decision 26).
            sprites.draw(
                canvas,
                R.drawable.sun_glow,
                SUN_GLOW_ORIGIN_UNITS,
                SUN_GLOW_ORIGIN_UNITS,
                SUN_GLOW_SCALE,
                sunAlpha,
            )
            sprites.draw(
                canvas,
                R.drawable.sun_body,
                CELESTIAL_DISC_ORIGIN_UNITS,
                CELESTIAL_DISC_ORIGIN_UNITS,
                CELESTIAL_DISC_SCALE,
                sunAlpha,
            )
            canvas.restore()
        } else {
            drawMoonWithPhase(canvas, cx, cy, radius, color, dayPhase.moonPhase)
        }
    }

    /**
     * The moon: one of four drawn phase silhouettes (crescent, half, gibbous, full; `DESIGN_NOTES.md` §12, decision
     * 7), the waning half reusing them turned 180°, a new moon only the dark disc; thresholds on `illuminated` pick one.
     */
    private fun drawMoonWithPhase(
        canvas: SceneCanvas,
        cx: Float,
        cy: Float,
        radius: Float,
        litColor: Int,
        phase: Float,
    ) {
        val darkColor = ColorUtils.blendARGB(litColor, 0xFF10101A.toInt(), 0.82f)
        val s = radius / 120f

        // Halloween replaces the disc, always full: a carved face that waxed and waned would read as a fault.
        // It is the moon lit from inside (`DESIGN_NOTES.md` §12, decision 74): the full moon's disc and craters in
        // the theme's moon colour with the face cut out, and the light inside the cuts drawn over it untinted, a
        // warm yellow with an orange rim, one sprite more in the same batch.
        if (sceneCustomization.halloweenEnabled) {
            canvas.save()
            canvas.translate(cx, cy)
            // Laughing ([LaughingMoon]): the open face, and the moon shaking with it.
            if (moonShakeDegrees != 0f) canvas.rotate(moonShakeDegrees)
            canvas.scale(s, s)
            // Under 255 only while the still moon comes out or fades ([StillMoon.alpha]).
            val moonAlpha = (255f * stillMoonFrame.alpha).toInt()
            sprites.drawTinted(
                canvas,
                if (laughingNow) R.drawable.moon_jack_o_lantern_laugh else R.drawable.moon_jack_o_lantern,
                CELESTIAL_DISC_ORIGIN_UNITS,
                CELESTIAL_DISC_ORIGIN_UNITS,
                CELESTIAL_DISC_SCALE,
                litColor,
                moonAlpha,
            )
            if (laughingNow) {
                sprites.draw(
                    canvas,
                    R.drawable.moon_jack_o_lantern_laugh_light,
                    HALLOWEEN_LAUGH_LIGHT_ORIGIN_X,
                    HALLOWEEN_LAUGH_LIGHT_ORIGIN_Y,
                    CELESTIAL_DISC_SCALE,
                    moonAlpha,
                )
            } else {
                sprites.draw(
                    canvas,
                    R.drawable.moon_jack_o_lantern_light,
                    HALLOWEEN_LIGHT_ORIGIN_X,
                    HALLOWEEN_LIGHT_ORIGIN_Y,
                    CELESTIAL_DISC_SCALE,
                    moonAlpha,
                )
            }
            canvas.restore()
            return
        }

        if (!sceneCustomization.moon.realisticPhases) {
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(s, s)
            sprites.drawTinted(
                canvas,
                R.drawable.moon_full,
                CELESTIAL_DISC_ORIGIN_UNITS,
                CELESTIAL_DISC_ORIGIN_UNITS,
                CELESTIAL_DISC_SCALE,
                litColor,
            )
            canvas.restore()
            return
        }

        // Always-visible faint dark disc (the unlit hemisphere, like real earthshine).
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(s, s)
        sprites.drawTinted(
            canvas,
            R.drawable.moon_full,
            CELESTIAL_DISC_ORIGIN_UNITS,
            CELESTIAL_DISC_ORIGIN_UNITS,
            CELESTIAL_DISC_SCALE,
            darkColor,
        )
        canvas.restore()

        // **[phase] is an argument and must stay one**: a clock read here would make the frame depend on the wall
        // clock, invisibly for days and then by 17 px when the real moon crosses a threshold below
        // (`RenderPathReadsNoWallClockTest`).
        val angle = phase * 2f * kotlin.math.PI.toFloat()
        val cosA = kotlin.math.cos(angle)
        val illuminated = (1f - cosA) / 2f
        if (illuminated <= 0.02f) return // new moon: dark disc only, nothing further to draw

        val waxing = phase < 0.5f
        val phaseSprite = when {
            illuminated < 0.35f -> R.drawable.moon_crescent
            illuminated < 0.65f -> R.drawable.moon_half
            illuminated < 0.98f -> R.drawable.moon_gibbous
            else -> R.drawable.moon_full
        }
        canvas.save()
        canvas.translate(cx, cy)
        // The waning half is the waxing shapes turned 180°: no second set of art to keep in step.
        if (!waxing) canvas.rotate(180f)
        canvas.scale(s, s)
        sprites.drawTinted(
            canvas,
            phaseSprite,
            CELESTIAL_DISC_ORIGIN_UNITS,
            CELESTIAL_DISC_ORIGIN_UNITS,
            CELESTIAL_DISC_SCALE,
            litColor,
        )
        canvas.restore()
    }

    private fun rebuildHillPathsIfNeeded() {
        if (cachedPathsThemeId == theme.id && cachedPathsWidth == screenWidth &&
            cachedPathsHeight == screenHeight && cachedPathsVariation == hillsVariation
        ) {
            return
        }
        for (layer in 0 until layerCount) {
            val layerTop = screenHeight * yOffsets[layer]
            val layerHeight = screenHeight * heightFractions[layer]
            val path = baseHillShapes[layer] ?: SceneShape().also { baseHillShapes[layer] = it }
            buildBaseHillPath(path, layer, layerTop, layerHeight)
        }
        cachedPathsThemeId = theme.id
        cachedPathsWidth = screenWidth
        cachedPathsHeight = screenHeight
        cachedPathsVariation = hillsVariation
    }

    /**
     * A flock of birds crossing the upper sky with their own drift, anchored to no terrain row. Each bird's colour
     * is a stable weighted pick from [BirdsConfig.colors] ([BirdsConfig.pickColor]). The wing flap is the one
     * "wings up" sprite mirrored vertically for the down half of the cycle: one texture, and the flip costs nothing.
     */
    private fun drawBirds(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime) {
        val birds = sceneCustomization.birds
        if (!birds.visible) return
        // Present while the sun is up, gone after dark unless the user wants night birds ([BirdsConfig.presenceAt]).
        val nightVisibility = birds.presenceAt(dayPhase.dayBlend)
        if (nightVisibility <= 0f) return

        val effectOffset = CandidateThreshold.offsetFor(EffectId.BIRDS)
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(birds.density, BIRD_POOL_SIZE, effectOffset)
        val seed = seedFor(EffectId.BIRDS)
        for (i in 0 until BIRD_POOL_SIZE) {
            if (!CandidateThreshold.isPresent(i, birds.density, effectOffset, fallbackIndex)) continue

            val laneFraction = CandidateNoise.range(seed, i, CandidateNoise.CH_Y, 0.08f, 0.38f) // upper portion of the sky
            val y = screenHeight * laneFraction
            val speed = CandidateNoise.range(seed, i, CandidateNoise.CH_SPEED, 0.025f, 0.045f)
            val phase = CandidateNoise.value(seed, i, CandidateNoise.CH_PHASE)
            val colorPickFraction = (i * 0.37f + phase) % 1f
            val drift = elapsedSeconds.cycle(speed, phase)
            val x = drift * (screenWidth + 200f) - 100f
            val bob = elapsedSeconds.sinAt(2.2f, phase * 6.28f) * 6f
            val flap = elapsedSeconds.sinAt(9f, phase * 6.28f) // -1..1

            val color = birds.pickColor(colorPickFraction)
            val alpha = (255 * nightVisibility).toInt().coerceIn(0, 255)

            // One candidate is one bird: `bird_body` is a 51x21 px sprite blitted at its own size, a wingspan of
            // about 48 px on screen. The origin puts the flip axis on the bird, or the flap makes it hop.
            canvas.save()
            canvas.translate(x, y + bob)
            canvas.scale(1f, if (flap < 0f) -1f else 1f)
            sprites.drawTinted(
                canvas,
                R.drawable.bird_body,
                BIRD_SPRITE_ORIGIN_X_PX,
                BIRD_SPRITE_ORIGIN_Y_PX,
                SpriteScale.CANVAS_PIXELS,
                color,
                alpha,
            )
            canvas.restore()
        }
    }

    /** [CloudBand]'s arithmetic, which three layers must agree on. */
    private fun cloudBandTopFor(screenHeight: Int, sunCloudHeight: Float): Float =
        CloudBand.topFor(screenHeight, sunCloudHeight)

    private fun cloudBandHeightFor(screenHeight: Int): Float = CloudBand.heightFor(screenHeight)

    /**
     * Puffy clouds drifting across the cloud band, from their own candidate pool with their own parallax and density,
     * anchored to nothing below. **A cloud's colour is the theme's day/night pair whatever the density**; darkening is
     * the weather's ([StormAtmosphere]). A full sky is the pool's 41 large lobed clouds overlapping into one band, in
     * four depth tiers by index, each with its own parallax, size and height. While Live Weather drives the scene, its
     * eased cover is the density and the theme's Clouds switch is not asked, as for the rain
     * (`LiveWeatherSceneRules.cloudDensity`).
     */
    private fun drawClouds(
        canvas: SceneCanvas,
        dayPhase: SunPositionCalculator.DayPhase,
        elapsedSeconds: SceneTime,
        deltaSeconds: Float,
    ) {
        val clouds = sceneCustomization.clouds
        // The override is asked before the theme's own switch, as in [drawPrecipitation], so a forecast's rain never
        // falls from a sky whose forecast clouds the switch hid.
        val density = LiveWeatherSceneRules.cloudDensity(
            // The eased cover ([CloudCoverFade]); which clouds a density admits, and where, are unchanged by it.
            liveCloudCover = drawnCloudCover,
            themeCloudsVisible = clouds.visible,
            themeCloudDensity = clouds.density,
        )
        // A cover easing to nothing reaches zero before the last clouds finish fading: leaving now would cut the fade.
        val showing = cloudCoverFade.anythingVisible()
        val placing = !density.isNaN() || showing
        // **Where the rain may fall this frame** ([CloudCoverage.rainField]): under the clouds drawn; everywhere when none
        // is placed or showing (the cloud switch does not turn the rain off); once the forecast has lapsed, under the
        // theme's own clouds, everywhere if it has none, since the theme's rain arrives in the frame of the lapse
        // (the rain is never eased, the clouds are) and must not fall only under the forecast's clouds while they fade.
        val themeDensity = LiveWeatherSceneRules.cloudDensity(Float.NaN, clouds.visible, clouds.density)
        val rainField = CloudCoverage.rainField(
            placedDensity = density,
            cloudsShowing = showing,
            lapsed = liveWeatherOverride == null && liveWeatherLapsed,
            themeDensity = themeDensity,
        )
        // Beyond the clouds, the rain spreads to the whole sky while the last clouds leave it and narrows under the first
        // ones while they come, over their own fade ([CloudCoverFade.rainEverywhereToward]), never in one frame.
        val rainEverywhere = cloudCoverFade.rainEverywhereToward(nothingPlaced = CloudCoverage.placesNone(density), deltaSeconds = deltaSeconds)
        cloudCoverage.beginFrame(rainField)
        if (!placing && rainField != CloudCoverage.RAIN_UNDER_THEME_CLOUDS) {
            cloudCoverFade.clearSkyDrawn()
            return
        }
        // Dimmed by the storm as the sky is, more heavily, so the clouds are the darkest thing above the horizon;
        // still the theme's pair blended by the day phase.
        cloudPaint.color = StormAtmosphere.dimCloud(
            blendColor(clouds.colorNight, clouds.colorDay, dayPhase.dayBlend),
            stormStrength(),
        )
        cloudPaint.setAlphaWithoutAllocating(255)

        // Zero while the layer only finishes its fade: `isPresent` is false for every candidate there.
        val placementDensity = if (density.isNaN()) 0f else density
        val effectOffset = CandidateThreshold.offsetFor(EffectId.CLOUDS)
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(placementDensity, CLOUD_POOL_SIZE, effectOffset)
        val seed = seedFor(EffectId.CLOUDS)
        val tileWidth = screenWidth * 2f

        val bandTop = cloudBandTopFor(screenHeight, sceneCustomization.sky.sunCloudHeight)
        val bandHeight = cloudBandHeightFor(screenHeight)
        // The theme's own clouds, for the rain after a lapse: the candidates its density admits, where they are drawn.
        val underTheme = rainField == CloudCoverage.RAIN_UNDER_THEME_CLOUDS
        val themeFallbackIndex = if (underTheme) CandidateThreshold.fallbackIndexFor(themeDensity, CLOUD_POOL_SIZE, effectOffset) else -1

        for (i in 0 until CLOUD_POOL_SIZE) {
            // **Every candidate's opacity is asked every frame**, present or not, while anything is placed or showing,
            // or a half-faded cloud would freeze on screen. With nothing placed or showing none is asked: a candidate
            // never drawn stays so, and its first sight snaps ([CloudCoverFade]).
            val present = CandidateThreshold.isPresent(i, placementDensity, effectOffset, fallbackIndex)
            val opacity = if (placing) cloudCoverFade.opacityOf(i, present, deltaSeconds) else 0f
            val rainsFrom = when (rainField) {
                CloudCoverage.RAIN_UNDER_DRAWN_CLOUDS -> opacity > 0f
                CloudCoverage.RAIN_UNDER_THEME_CLOUDS -> CandidateThreshold.isPresent(i, themeDensity, effectOffset, themeFallbackIndex)
                else -> false
            }
            if (opacity <= 0f && !rainsFrom) continue
            val cloudAlpha = (opacity * 255f).toInt().coerceIn(0, 255)

            val tier = i % 4
            val parallax = (CLOUD_TIER_PARALLAX[tier] * parallaxStrength).coerceAtMost(1f)
            val wrappedShift = wrappedScrollShift(parallax.toDouble(), tileWidth)

            val tileFractionX = (i + 0.5f) / CLOUD_POOL_SIZE +
                (CandidateNoise.value(seed, i, CandidateNoise.CH_X) - 0.5f) * (1f / CLOUD_POOL_SIZE) * 0.5f
            val laneY = bandTop + bandHeight * (0.5f + CLOUD_TIER_Y_OFFSET[tier]) +
                (CandidateNoise.value(seed, i, CandidateNoise.CH_Y) - 0.5f) * bandHeight * 0.25f
            val scale = CandidateNoise.range(seed, i, CandidateNoise.CH_SCALE, 0.85f, 1.25f) * CLOUD_TIER_SIZE_MULTIPLIER[tier]
            val driftSpeed = CandidateNoise.range(seed, i, CandidateNoise.CH_SPEED, 0.004f, 0.008f)
            val phase = CandidateNoise.value(seed, i, CandidateNoise.CH_PHASE)
            val ownDrift = elapsedSeconds.cycle(driftSpeed, phase) * tileWidth
            val baseX = tileFractionX * tileWidth + wrappedShift + ownDrift

            // The cloud repeats every tile width: its copies are the one folded into [0, tileWidth) and the one a
            // tile to its left; a tile is two screens wide, so no third copy can reach the screen, and no two
            // copies share an x (a cloud blitted twice would show heavier edges and a faster fade).
            val folded = foldIntoTile(baseX, tileWidth)
            for (copy in 0..1) {
                val x = folded - copy * tileWidth
                // A cull margin wider than the sprite's half-width (133 units x scale): never culled while visible.
                if (x < -160f * scale || x > screenWidth + 160f * scale) continue
                if (opacity > 0f) drawPuffyCloud(canvas, x, laneY, scale, cloudAlpha)
                if (!rainsFrom) continue
                // A fading cloud adds its whole geometry to the rain field, not its opacity: the field only says
                // where rain may fall, and a settled frame's field is the same whichever way it was reached.
                val cloudHalfWidth = CloudCoverage.CLOUD_CONTENT_HALF_UNITS * scale
                cloudCoverage.addCloud(
                    centerX = x,
                    coreHalfWidth = cloudHalfWidth,
                    spreadHalfWidth = cloudHalfWidth * CloudCoverage.RAIN_SPREAD_FACTOR,
                    screenWidth = screenWidth.toFloat(),
                )
            }
        }
        if (rainField == CloudCoverage.RAIN_UNDER_DRAWN_CLOUDS) cloudCoverage.setFloor(rainEverywhere)
    }

    /**
     * One cloud: a single tinted blit of `cloud_body.png` (from `tools/assets/sources/svg/cloud_body.svg`, one lobed mass
     * with a vertical tone ramp and a feathered edge), the look baked into the sprite (decision 3).
     */
    private fun drawPuffyCloud(canvas: SceneCanvas, cx: Float, cy: Float, scale: Float, alpha: Int) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        // [alpha] is how far through its fade the candidate is ([CloudCoverFade]): 255 in a settled sky.
        sprites.drawTinted(canvas, R.drawable.cloud_body, CLOUD_BLIT_X, CLOUD_BLIT_Y, SpriteScale.SCENE_UNITS, cloudPaint.color, alpha)
        canvas.restore()
    }

    /**
     * A paper-cutout rainbow: the `rainbow_arc` sprite, six concentric bands, scaled to the screen's width. Its base
     * stands on [SceneSpace.GROUND_SOLID_TOP_Y_FRACTION], the mountains' base without a lake, and it is drawn before
     * the mountains and hills so their silhouettes cover its base, as a rainbow rises from behind distant terrain.
     */
    private fun drawRainbow(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase) {
        val rainbow = sceneCustomization.rainbow
        if (!rainbow.visible) return
        // A daylight thing: it fades out toward night as the stars fade in.
        val visibility = (dayPhase.dayBlend * 1.4f).coerceIn(0f, 1f) * rainbow.opacity.coerceIn(0f, 1f)
        if (visibility <= 0f) return

        val baseYFraction = SceneSpace.GROUND_SOLID_TOP_Y_FRACTION
        val baseY = screenHeight * baseYFraction
        val cx = screenWidth * 0.5f
        // The arc's outer radius, onto which [RAINBOW_SPRITE_HALF_WIDTH_UNITS] maps.
        val maxRadius = screenWidth * 0.62f
        val scale = maxRadius / RAINBOW_SPRITE_HALF_WIDTH_UNITS
        canvas.save()
        canvas.translate(cx, baseY)
        canvas.scale(scale, scale)
        sprites.draw(
            canvas,
            R.drawable.rainbow_arc,
            RAINBOW_SPRITE_ORIGIN_X_UNITS,
            RAINBOW_SPRITE_ORIGIN_Y_UNITS,
            SpriteScale.SCENE_UNITS,
            (RAINBOW_MAX_ALPHA * visibility).toInt().coerceIn(0, 255),
        )
        canvas.restore()
    }

    /**
     * Falling rain or snow, the nearest thing in the scene but the falling leaves and the flash. Stateless: each
     * candidate's fall is a function of [elapsedSeconds], wrapping from top to bottom, so no drop is spawned or
     * removed. A drop comes out of the clouds, from the middle of their band ([CloudBand.precipitationOriginY]), fading
     * in over the first 10 % of its fall and out over the last 10 %. While Live Weather drives the scene, the forecast
     * says whether, what and how hard, and the theme's colours are kept. Every length is in scene metres
     * ([SceneSpace.pixelsPerMetre]), as a house's or a car's.
     */
    private fun drawPrecipitation(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime) {
        val precip = sceneCustomization.precipitation
        // What falls -- the forecast's while Live Weather drives the scene, else the theme's -- and how it looks: one rule,
        // [PrecipitationLook], which the JVM suite runs on a forecast.
        val kind = PrecipitationLook.kind(precip, liveWeatherOverride)
        if (kind == PrecipitationLook.NONE) return
        val isRain = kind == PrecipitationLook.RAIN
        val intensity = PrecipitationLook.intensity(precip, liveWeatherOverride)
        // The theme's colour, the user's pick, only carried far enough off the sky's brightness to be seen
        // ([PRECIPITATION_MIN_LUMA_GAP]); under *Blood rain* the rain's red pair instead, the user's colours kept
        // stored for when it is off, and carried off the sky by the same rule. Snow stays snow.
        val blood = PrecipitationLook.blood(kind, sceneCustomization)
        val themeColour = PrecipitationLook.colour(kind, blood, precip, dayPhase.dayBlend)
        val baseAlpha = if (isRain) RAIN_ALPHA else SNOW_ALPHA
        // The gap is stated on the composited stroke and luma is linear, so the colour carries the gap over the alpha.
        // Not over the end fades: a drop is meant to be faint as it leaves the cloud and as it lands.
        val neededColourGap = PRECIPITATION_MIN_LUMA_GAP * 255f / baseAlpha
        val dropLuma = rec601Luma(themeColour)

        val effectOffset = CandidateThreshold.offsetFor(EffectId.PRECIPITATION)
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(intensity, PRECIPITATION_POOL_SIZE, effectOffset)
        val seed = seedFor(EffectId.PRECIPITATION)
        // Rain falls briskly; snow drifts gently.
        val fallSpeed = if (isRain) 1.3f else 0.09f
        // The band's middle, the line drawClouds' `laneY` is built around: [CloudBand.precipitationOriginY], which the
        // tests of the band and of the rain's contrast measure.
        val fallStartY = CloudBand.precipitationOriginY(screenHeight, sceneCustomization.sky.sunCloudHeight)
        // The sky's brightness at the two ends of the stretch a drop crosses with sky behind it, from the band's
        // middle to the top of the hills: `skyAbove` is a lerp and luma is linear, so these two bound the stretch.
        val precipHorizonY = screenHeight * SceneSpace.HILL_LAYER_TOP_FRACTION
        val skyLumaAtFallStart = rec601Luma(skyAbove(fallStartY))
        val skyLumaAtHorizon = rec601Luma(skyAbove(precipHorizonY))
        val skyLumaLow = kotlin.math.min(skyLumaAtFallStart, skyLumaAtHorizon)
        val skyLumaHigh = kotlin.math.max(skyLumaAtFallStart, skyLumaAtHorizon)
        // One colour for the whole fall, clear of the whole band ([PrecipitationContrast.standOffFromSky]: no
        // per-height correction can be continuous).
        precipPaint.color = PrecipitationContrast.standOffFromSky(themeColour, dropLuma, skyLumaLow, skyLumaHigh, neededColourGap)
        val metrePx = SceneSpace.pixelsPerMetre(screenHeight.toFloat())
        val fallRange = (screenHeight + PRECIPITATION_BOTTOM_MARGIN_METRES * metrePx) - fallStartY
        // Out of the loop, which runs [PRECIPITATION_POOL_SIZE] times a frame.
        val rainLengthMin = RAIN_LENGTH_MIN_METRES * metrePx
        val rainLengthMax = RAIN_LENGTH_MAX_METRES * metrePx
        val snowRadiusMin = SNOW_DIAMETER_MIN_METRES * metrePx / 2f
        val snowRadiusMax = SNOW_DIAMETER_MAX_METRES * metrePx / 2f
        val snowSway = SNOW_SWAY_METRES * metrePx
        // The paint's style, width and cap are the same for every drop, set once; only the alpha and the geometry
        // change per drop. A flake is a disc ([SpriteBlitter.drawDisc]) in this colour made opaque, its alpha its own.
        if (isRain) {
            precipPaint.style = Paint.Style.STROKE
            // The blood rain's drops are twice as thick ([BLOOD_RAIN_THICKER]); as many, as long, as fast.
            precipPaint.strokeWidth = RAIN_STROKE_WIDTH_METRES * metrePx * PrecipitationLook.strokeFactor(blood)
            // **Square ends** (`DESIGN_NOTES.md` §12, decision 87): on the GPU a round end is a fan of eight slices at the
            // least ([GlSceneTarget.drawLine]), 48 of a drop's 54 corners, and heavy rain alone would pass a batch. A
            // square end is straight and reaches half a stroke past the drop's point, as far as a round one: a drop is
            // one quad, as long as with round ends.
            precipPaint.strokeCap = Paint.Cap.SQUARE
        }
        val snowColour = precipPaint.color or 0xFF000000.toInt()
        for (i in 0 until PRECIPITATION_POOL_SIZE) {
            // Absent under any cover at this intensity: the cover is in 0..1 (`CloudCoverage.at`), so the drop is
            // skipped before it is looked up.
            if (CandidateThreshold.absentUpTo(i, intensity, effectOffset, fallbackIndex)) continue
            val xFraction = CandidateNoise.value(seed, i, CandidateNoise.CH_X)

            // Local density: the candidate is tested against `intensity x coverage(x)`, so no drop falls over open
            // sky (decision 13). Sampled at the base x, so a flake's sway cannot flicker it at a coverage edge.
            val localDensity = intensity * cloudCoverage.at(xFraction * screenWidth, screenWidth.toFloat())
            if (localDensity <= 0f) continue
            if (!CandidateThreshold.isPresent(i, localDensity, effectOffset, fallbackIndex)) continue

            val speedVariance = CandidateNoise.range(seed, i, CandidateNoise.CH_VARIANCE, 0.7f, 1.3f)
            val phase = CandidateNoise.value(seed, i, CandidateNoise.CH_PHASE)
            val fallFraction = elapsedSeconds.cycle(fallSpeed * speedVariance, phase)
            val y = fallStartY + fallFraction * fallRange
            // Snow sways gently as it falls; rain falls in a straight diagonal line (wind-angled).
            val sway = if (isRain) 0f else elapsedSeconds.sinAt(1.3f, phase * 6.28f) * snowSway
            val x = xFraction * screenWidth + sway

            // Fade in over the first 10 % of the fall and out over the last 10 %: out of the cloud, not out of nowhere.
            val fadeRange = 0.1f
            val fadeAlpha = when {
                fallFraction < fadeRange -> fallFraction / fadeRange
                fallFraction > 1f - fadeRange -> (1f - fallFraction) / fadeRange
                else -> 1f
            }.coerceIn(0f, 1f)

            // The drops below the bottom edge (the last [PRECIPITATION_BOTTOM_MARGIN_METRES] of the fall) are drawn
            // like the others: skipping them changes no pixel and saves about 0.2 points of a core under full rain on
            // the BV6600, inside the noise, so it is not done: a change made for speed has to be measured outside it.
            if (isRain) {
                precipPaint.setAlphaWithoutAllocating((RAIN_ALPHA * fadeAlpha).toInt())
                val len = CandidateNoise.range(seed, i, CandidateNoise.CH_LENGTH, rainLengthMin, rainLengthMax)
                canvas.drawLine(x, y, x - len * 0.25f, y + len, precipPaint)
            } else {
                // A disc: one quad of the atlas on the GL path, six corners where a circle's fan is 24 or more, so a
                // Christmas night with everything on stays one draw call (decision 70).
                val r = CandidateNoise.range(seed, i, CandidateNoise.CH_WIDTH, snowRadiusMin, snowRadiusMax)
                sprites.drawDisc(canvas, x, y, r, snowColour, (SNOW_ALPHA * fadeAlpha).toInt())
            }
        }
    }

    /**
     * Fall Colors' falling leaves: tied to the switch, with no density or colour of their own, stateless as
     * [drawPrecipitation] (each candidate re-evaluated from [elapsedSeconds]). They drift slowly and tumble as rotating
     * ovals, which reads as a leaf rather than as precipitation. Each leaf starts at a crown on screen
     * (`SceneObjectRenderer.leafSourceX`/`leafSourceY`) and falls only to **its own tree's ground line**.
     */
    private fun drawFallingLeaves(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime) {
        if (!sceneCustomization.fallColorsEnabled) return
        // No density filter: the leaves are all or nothing with the switch.
        val seed = seedFor(EffectId.FALLING_LEAVES)
        val palette = FALLING_LEAF_PALETTE
        // **Every leaf comes off a crown on screen**, as `SceneObjectRenderer.draw`, just before this, recorded them
        // (depth, ground line, scale and tile offset resolved). No trees on screen, no leaves.
        val sources = objectRenderer.leafSourceCount
        if (sources == 0) return
        // **A leaf belongs to its tree, not to a slot of this frame's source array**, which is refilled in visibility
        // order and changes as a swipe moves trees across the edges: each visible crown derives
        // [SceneObjectRenderer.leafSourceLeafCount] candidates (3 to 13, by its drawn size) from its own stable
        // identity ([SceneObjectRenderer.leafSourceId]), so its leaves fall undisturbed while it is on screen and
        // leave with it. Nothing is stored between frames.
        val fallSpeed = 0.06f
        // The palette's day/night blend is the same for every leaf of a frame: worked out once per entry; the style
        // set once; colour and alpha written together (`setAlpha` allocates on Android 10: [setAlphaWithoutAllocating]).
        val blended = fallingLeafBlended
        for (k in palette.indices) {
            val c = palette[k]
            blended[k] = blendColor(ColorUtils.blendARGB(c, 0xFF000000.toInt(), 0.35f), c, dayPhase.dayBlend)
        }
        leafPaint.style = Paint.Style.FILL
        for (source in 0 until sources) {
            val id = objectRenderer.leafSourceId[source]
            val perTree = objectRenderer.leafSourceLeafCount[source]
            val fallStartY = objectRenderer.leafSourceY[source]
            // A leaf lands at the foot of its own tree, not on the road among the cars ([SceneObjectRenderer.leafSourceGroundY]).
            val fallEndY = objectRenderer.leafSourceGroundY[source]
            val fallRange = fallEndY - fallStartY
            if (fallRange <= 0f) continue
            for (j in 0 until perTree) {
                // The tree copy's identity and the leaf's slot ([fallingLeafCandidate]); the noise reads its low 32 bits.
                val candidate = fallingLeafCandidate(id, j)
                val i = candidate.toInt()
                val speedVariance = CandidateNoise.range(seed, i, CandidateNoise.CH_VARIANCE, 0.7f, 1.3f)
                val phase = CandidateNoise.value(seed, i, CandidateNoise.CH_PHASE)
                val fallFraction = elapsedSeconds.cycle(fallSpeed * speedVariance, phase)
                val y = fallStartY + fallFraction * fallRange
                val sway = elapsedSeconds.sinAt(0.9f, phase * 6.28f) * 26f
                // Across the crown it left, a fraction of that crown's half-width; the sway carries it off as it falls.
                val acrossCrown = (CandidateNoise.value(seed, i, CandidateNoise.CH_X) - 0.5f) * 2f
                val x = objectRenderer.leafSourceX[source] +
                    acrossCrown * objectRenderer.leafSourceHalfWidth[source] + sway * fallFraction
                val spin = elapsedSeconds.cycleOf(60f * (0.5f + speedVariance * 0.5f), phase * 360f, 360f)
                val leafColor = blended[fallingLeafColourIndex(candidate, palette.size)]
                // Fade in leaving the canopy, out settling near the ground, as the rain does.
                val fadeRange = 0.12f
                val fadeAlpha = when {
                    fallFraction < fadeRange -> fallFraction / fadeRange
                    fallFraction > 1f - fadeRange -> (1f - fallFraction) / fadeRange
                    else -> 1f
                }.coerceIn(0f, 1f)
                val leafAlpha = (220 * fadeAlpha).toInt().coerceIn(0, 255)
                leafPaint.color = (leafAlpha shl 24) or (leafColor and 0x00FFFFFF)
                // A fully faded leaf is not drawn: it would cost a draw and count as a leaf on the crown in
                // FallingLeafContinuityTest's pixel accounting.
                if (leafAlpha == 0) continue
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(spin)
                canvas.drawOval(-4f, -6f, 4f, 6f, leafPaint)
                canvas.restore()
            }
        }
    }


    /**
     * Advances the lightning's timer and fade. The timer ticks and fires only while [enabled] (a storm, by
     * `LiveWeatherSceneRules.stormActive`) and [lightningStrikesEnabled]; otherwise a flash in flight fades out.
     */
    private fun updateLightning(deltaSeconds: Float, enabled: Boolean) {
        if (enabled && lightningStrikesEnabled) {
            lightningTimer -= deltaSeconds
            if (lightningTimer <= 0f) {
                lightningFlashAlpha = 1f
                lightningTimer = 4f + Random.nextFloat() * 8f
                lightningBoltXFraction = 0.15f + Random.nextFloat() * 0.7f
                lightningBoltHeightFraction = LIGHTNING_BOLT_MIN_HEIGHT_FRACTION +
                    Random.nextFloat() * LIGHTNING_BOLT_HEIGHT_SPREAD_FRACTION
            }
        }
        if (lightningFlashAlpha > 0f) {
            lightningFlashAlpha = (lightningFlashAlpha - deltaSeconds * 3f).coerceAtLeast(0f)
        }
    }

    /**
     * The strike: a full-screen white veil, then the bolt over it (under a 70 %-opaque wash it would not be seen).
     * Both fade on `lightningFlashAlpha`, so the bolt goes with the veil.
     */
    private fun drawLightningFlash(canvas: SceneCanvas) {
        if (lightningFlashAlpha <= 0f) return
        lightningPaint.color = 0xFFFFFFFF.toInt()
        lightningPaint.setAlphaWithoutAllocating((LIGHTNING_VEIL_MAX_ALPHA * lightningFlashAlpha).toInt().coerceIn(0, 255))
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), lightningPaint)

        // The sprite hangs from its top edge: scaled to the rolled height over its 84 units, centred on the rolled
        // x, its top inside the cloud band past the middle ([CloudBand.lightningOriginY]), so only the fork shows.
        val boltHeight = screenHeight * lightningBoltHeightFraction
        val scale = boltHeight / LIGHTNING_BOLT_HEIGHT_UNITS
        val boltTop = CloudBand.lightningOriginY(screenHeight, sceneCustomization.sky.sunCloudHeight)
        canvas.save()
        canvas.translate(screenWidth * lightningBoltXFraction, boltTop)
        canvas.scale(scale, scale)
        sprites.draw(
            canvas,
            R.drawable.lightning_bolt,
            -LIGHTNING_BOLT_WIDTH_UNITS / 2f,
            0f,
            SpriteScale.SCENE_UNITS,
            (255 * lightningFlashAlpha).toInt().coerceIn(0, 255),
        )
        canvas.restore()
    }

    /**
     * Two background silhouette layers behind the hills, each with its own parallax, much slower than the hill's;
     * backdrop shapes outside the hill and object placement ([SceneSpace.groundYFraction]).
     */
    private fun drawMountains(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase) {
        // The base is the lake's top edge, or without a lake the hill's always-covered line
        // ([hillGuaranteedTopFraction]), the deepest its wavy top edge reaches: anchored to a peak, bare sky would
        // open below the mountains wherever the edge dips.
        val effectiveBaseYFraction =
            if (updateLakeBandY()) lakeBandTopY / screenHeight else hillGuaranteedTopFraction

        // Height and width are both fractions of the screen's height, one unit, so their ratio holds on every
        // screen: the back mountains `[0.75,1.25] * 0.15` tall and `[0.75,1.25] * 0.25` wide, the front ones 0.7
        // of that.
        drawMountainLayer(
            canvas, dayPhase, sceneCustomization.mountainsBack, parallaxFactor = MOUNTAINS_BACK_PARALLAX, seedSalt = EffectId.MOUNTAINS_BACK,
            baseYFraction = effectiveBaseYFraction, peakHeightFraction = 0.15f, widthOfHeightFraction = 0.25f,
        )
        drawMountainLayer(
            canvas, dayPhase, sceneCustomization.mountainsFront, parallaxFactor = MOUNTAINS_FRONT_PARALLAX, seedSalt = EffectId.MOUNTAINS_FRONT,
            baseYFraction = effectiveBaseYFraction + 0.015f, peakHeightFraction = 0.105f, widthOfHeightFraction = 0.175f,
        )
    }

    private fun drawMountainLayer(
        canvas: SceneCanvas,
        dayPhase: SunPositionCalculator.DayPhase,
        config: MountainLayerConfig,
        parallaxFactor: Float,
        seedSalt: Int,
        baseYFraction: Float,
        peakHeightFraction: Float,
        widthOfHeightFraction: Float,
    ) {
        if (!config.visible) return
        mountainPaint.color = blendColor(config.colorNight, config.colorDay, dayPhase.dayBlend)
        // Fully opaque: the sun, the moon and the sky are drawn behind the mountains and must not show through. The
        // two layers' depth is in their colours, sizes and heights.
        mountainPaint.setAlphaWithoutAllocating(255)

        val effectOffset = CandidateThreshold.offsetFor(seedSalt)
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(config.density, MOUNTAIN_POOL_SIZE, effectOffset)
        val seed = seedFor(seedSalt)
        val tileWidth = screenWidth * 2f
        val parallax = (parallaxFactor * parallaxStrength).coerceAtMost(1f)
        val wrappedShift = wrappedScrollShift(parallax.toDouble(), tileWidth)

        val baseY = screenHeight * baseYFraction
        val peakHeight = screenHeight * peakHeightFraction
        val baseWidth = screenHeight * widthOfHeightFraction

        // The layer's mountains across the tile, once a frame: where each stands, how wide and tall it is (on two noise
        // channels, so the widths vary too), the seed of its houses; read by the cover rule and the drawing below.
        var perTile = 0
        for (i in 0 until MOUNTAIN_POOL_SIZE) {
            val present = CandidateThreshold.isPresent(i, config.density, effectOffset, fallbackIndex)
            mountainPresent[i] = present
            if (!present) continue
            perTile++
            val tileFractionX = (i + 0.5f) / MOUNTAIN_POOL_SIZE +
                (CandidateNoise.value(seed, i, CandidateNoise.CH_X) - 0.5f) * (1f / MOUNTAIN_POOL_SIZE) * 0.5f
            mountainTileX[i] = tileFractionX * tileWidth
            mountainHeight[i] = peakHeight * CandidateNoise.range(seed, i, CandidateNoise.CH_HEIGHT, 0.75f, 1.25f)
            mountainWidth[i] = baseWidth * CandidateNoise.range(seed, i, CandidateNoise.CH_WIDTH, 0.75f, 1.25f)
            mountainSeed[i] = DistantHouses.seedFor(seed, i)
        }
        // Where a distant house would not be wholly seen, a later mountain of the layer over it ([DistantHouses.hiddenPlaces]):
        // worked out when the layer, the screen or its base line change, not per frame.
        val frontLayer = seedSalt == EffectId.MOUNTAINS_FRONT
        val hidden = if (frontLayer) frontHiddenPlaces else backHiddenPlaces
        if (sceneCustomization.distantHouses.drawsAny) hidden.refresh(seed, config.density, baseY, tileWidth)

        // Which of the layer's drawn mountains this is along the whole strip (its place in the tile and the tile's copy):
        // its distant houses start from the left or the right by it, alternately ([DistantHouses.startsLeft]). The copy
        // is counted as the street's are, unwrapped shift against wrapped in Double, so it keeps its number on screen.
        val tileBias = Math.round((-scrollProgress * screenWidth * parallax - wrappedShift) / tileWidth).toInt()
        var shown = 0
        for (i in 0 until MOUNTAIN_POOL_SIZE) {
            if (!mountainPresent[i]) continue
            val place = shown++
            val baseX = mountainTileX[i] + wrappedShift
            val width = mountainWidth[i]

            // At the tile position and its two neighbours, so a shape near a seam is never cut off.
            for (tileOffset in -1..1) {
                val x = baseX + tileOffset * tileWidth
                if (x < -width || x > screenWidth + width) continue
                drawSoftMountain(canvas, x, baseY, width, mountainHeight[i])
                // Its distant houses, before the next mountain is drawn over them, none where a later one would cover it.
                val leftFirst = DistantHouses.startsLeft(tileOffset - tileBias, place, perTile, frontLayer)
                drawDistantHouses(canvas, x, baseY, width, mountainHeight[i], mountainSeed[i], leftFirst, hidden.masks[i], dayPhase.dayBlend)
            }
        }
    }

    /** The layer being drawn's mountains across its tile ([drawMountainLayer]): filled once a frame, nothing allocated. */
    private val mountainPresent = BooleanArray(MOUNTAIN_POOL_SIZE)
    private val mountainTileX = FloatArray(MOUNTAIN_POOL_SIZE)
    private val mountainWidth = FloatArray(MOUNTAIN_POOL_SIZE)
    private val mountainHeight = FloatArray(MOUNTAIN_POOL_SIZE)
    private val mountainSeed = IntArray(MOUNTAIN_POOL_SIZE)

    /**
     * The places of each of a layer's mountains where a distant house would not be wholly seen: [DistantHouses.hiddenPlaces]
     * of the mountains [drawMountainLayer] has just laid out, kept until the layer's seed, its amount, its base line or the
     * screen change, compared field by field: a hash could collide and skip a rebuild the layer needs.
     */
    private inner class HiddenPlaces {
        val masks = IntArray(MOUNTAIN_POOL_SIZE)
        private var seed = 0
        private var density = Float.NaN
        private var baseY = Float.NaN
        private var width = -1
        private var height = -1

        fun refresh(seed: Int, density: Float, baseY: Float, tileWidth: Float) {
            if (seed == this.seed && density == this.density && baseY == this.baseY && screenWidth == width && screenHeight == height) return
            for (i in 0 until MOUNTAIN_POOL_SIZE) {
                masks[i] = if (mountainPresent[i]) {
                    DistantHouses.hiddenPlaces(
                        i, mountainTileX, mountainWidth, mountainHeight, mountainSeed, mountainPresent, baseY, tileWidth, screenHeight.toFloat(),
                    )
                } else {
                    0
                }
            }
            this.seed = seed
            this.density = density
            this.baseY = baseY
            width = screenWidth
            height = screenHeight
        }
    }

    private val backHiddenPlaces = HiddenPlaces()
    private val frontHiddenPlaces = HiddenPlaces()

    /**
     * A rounded mountain: [MountainSilhouette]'s parabolic arch (the gallery card draws with it too), sampled evenly in
     * width so the points bunch at the peak and the tip is round. Two halves sharing the peak and base points, each
     * closed down the vertical centre axis (a diagonal back to the base would fill only a thin crescent), both in the
     * layer's one colour: a lightened and a darkened half read as two mountains meeting at a seam.
     */
    private fun drawSoftMountain(canvas: SceneCanvas, cx: Float, baseY: Float, width: Float, height: Float) {
        MountainSilhouette.leftHalf(mountainShape, cx, baseY, width, height)
        canvas.drawShape(mountainShape, mountainPaint)
        MountainSilhouette.rightHalf(mountainShape, cx, baseY, width, height)
        canvas.drawShape(mountainShape, mountainPaint)
    }

    /**
     * A distant house's Christmas lights: three bulbs on its roofline -- at the eaves, the ridge and the
     * other eaves ([BuildingPiece.roofX] of the drawing, its first, middle and last) -- each [DISTANT_BULB_PX]
     * across on screen whatever the house's [scale], under the house's own transform, in the Christmas lights'
     * colours and still: at 14 px a house, a blink would be a flicker. Discs ([SpriteBlitter.drawDisc]), a quad
     * each on the GL path; where they hang is [ChristmasDecorations.forEachDistantHouseBulb], the gallery card's too.
     */
    private fun drawDistantHouseLights(canvas: SceneCanvas, piece: BuildingPiece, scale: Float, slot: Int) {
        val radius = DISTANT_BULB_PX / 2f / scale
        val colours = SceneObjectRenderer.CHRISTMAS_LIGHT_COLOURS
        ChristmasDecorations.forEachDistantHouseBulb(piece, radius, slot) { x, y, colour ->
            sprites.drawDisc(canvas, x, y, radius, colours[colour])
        }
    }

    /**
     * The distant houses on one mountain ([DistantHouses]: where, how many, how large; `DESIGN_NOTES.md` §12, decision
     * 52). Drawn from the village's pieces, in the same paper: the wall a weight mask summed in one of the houses' two
     * colours, the glass in the scene's glass, lit at night in four houses of five and dark glass in the fifth, and the
     * snow over the roof only with the winter palette. Nothing while the switch is off or at 0 %, as every theme ships.
     */
    private fun drawDistantHouses(
        canvas: SceneCanvas,
        cx: Float,
        baseY: Float,
        width: Float,
        height: Float,
        seed: Int,
        leftFirst: Boolean,
        hidden: Int,
        dayBlend: Float,
    ) {
        val config = sceneCustomization.distantHouses
        if (!config.drawsAny) return
        val pieces = NeighbourhoodTable.DISTANT_HOUSES
        val halfWidth = width / 2f
        val night = (1f - dayBlend).coerceIn(0f, 1f)
        val winter = sceneCustomization.winterColorsEnabled
        val christmas = sceneCustomization.christmasBuildingLightsEnabled
        val standing = DistantHouses.standingMask(seed, config.density, leftFirst, hidden)
        for (slot in DistantHouses.SLOTS.indices) {
            if (standing and (1 shl slot) == 0) continue
            val along = DistantHouses.along(seed, slot)
            val surface = DistantHouses.surfaceY(baseY, height, along)
            val pixelsTall = SceneSpace.distantHousePixelsTall(surface / screenHeight, screenHeight.toFloat())
            val footY = DistantHouses.footY(surface, DistantHouses.slope(height, halfWidth, along), pixelsTall)
            val piece = pieces[DistantHouses.design(seed, slot, pieces.size)]
            val wall = sceneCustomization.houses.colorAt(DistantHouses.colourVariant(seed, slot), dayBlend)
            val glass = DistantHouses.glassColour(seed, slot, night)
            val s = pixelsTall / piece.height
            canvas.save()
            canvas.translate(cx + along * halfWidth, footY)
            canvas.scale(s, s)
            val parts = piece.parts
            for (index in parts.indices) {
                val part = parts[index]
                when (part.role) {
                    PartRole.WALL_MASK -> sprites.drawTintedAdded(canvas, part.res, part.x, part.y, SpriteScale.SCENE_UNITS, wall)
                    PartRole.GLASS_MASK -> sprites.drawTintedAdded(canvas, part.res, part.x, part.y, SpriteScale.SCENE_UNITS, glass)
                    PartRole.FIXED -> sprites.draw(canvas, part.res, part.x, part.y, SpriteScale.SCENE_UNITS)
                    PartRole.SNOW -> if (winter) sprites.draw(canvas, part.res, part.x, part.y, SpriteScale.SCENE_UNITS)
                    PartRole.LAMP, PartRole.OCCUPANTS -> Unit
                }
            }
            if (christmas) drawDistantHouseLights(canvas, piece, s, slot)
            canvas.restore()
        }
    }

    /**
     * The lake's top Y in pixels, valid only when [updateLakeBandY] last returned `true`: two fields, not a `Pair` of
     * boxed floats, since it is worked out twice a frame.
     */
    private var lakeBandTopY = 0f

    /** The lake's bottom Y in pixels. See [lakeBandTopY]. */
    private var lakeBandBottomY = 0f

    /**
     * Recomputes [lakeBandTopY] and [lakeBandBottomY], returning whether there is water. Shared by [drawLake] and
     * [drawMountains], whose base is the water's top edge when there is a lake; on `false` the fields are stale.
     */
    private fun updateLakeBandY(): Boolean {
        val lake = sceneCustomization.lake
        // Off, or at 0 % height: no water, and nothing laid out on it.
        if (!lake.drawsWater) return false
        // The band sits just above the hills, its bottom on their always-covered line ([hillGuaranteedTopFraction]),
        // so the hills drawn over it dip into its edge with no bare sky between. Its height grows with Lake Height
        // up to 0.16 of the screen, so its top (0.544 at most) stays below the tallest back mountain's peak (0.5165).
        val bottom = screenHeight * hillGuaranteedTopFraction
        val bandHeight = screenHeight * 0.16f * lake.height.coerceIn(0f, 1f)
        val top = bottom - bandHeight
        lakeBandTopY = top
        lakeBandBottomY = bottom
        return true
    }

    /**
     * The lake, a horizontal band in the middle distance (0.544 to 0.704 of the screen height at full
     * [LakeConfig.height]), well above the road ([SceneSpace.roadTopYFraction]), outside the object placement as the
     * mountains are.
     */
    private fun drawLake(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime) {
        if (!updateLakeBandY()) return
        val top = lakeBandTopY
        val bottom = lakeBandBottomY
        val lake = sceneCustomization.lake
        val bandHeight = bottom - top
        lakePaint.color = blendColor(lake.colorNight, lake.colorDay, dayPhase.dayBlend)
        // The water scrolls at [LAKE_PARALLAX], between the front mountains on its far edge and the hills in front of
        // it: faster than the hills, its sparkles (the only part this shift moves) would read nearer than the land.
        val lakeParallax = (LAKE_PARALLAX * parallaxStrength).coerceAtMost(1f)
        val lakeWrapped = wrappedScrollShift(lakeParallax.toDouble(), screenWidth.toFloat())

        canvas.save()
        canvas.translate(lakeWrapped, 0f)
        // Copies a screen wide side by side so the translate never exposes a gap, only those that reach the screen:
        // `lakeWrapped` is in (-screenWidth, 0], so the copy at -1 is skipped except at a wrap of exactly zero, where
        // a sparkle at its end still reaches past its right edge.
        for (tileOffset in -1..1) {
            val x0 = tileOffset * screenWidth + lakeWrapped
            if (x0 + screenWidth < 0f || x0 > screenWidth) continue
            drawLakeBand(canvas, (tileOffset * screenWidth).toFloat(), top, bottom, bandHeight, elapsedSeconds)
        }
        canvas.restore()
        // Untiled: the waterline is uniform along x, and the glow and the light's path sit under the celestial body,
        // which does not scroll with the water.
        drawWaterline(canvas, top)
        drawLakeMirrorGlow(canvas, dayPhase, top, bandHeight)
        drawLakeGlitter(canvas, dayPhase, top, bandHeight, elapsedSeconds, stormStrength())

        // **One pass over the water, painted far to near**: everything on it placed first, then drawn in
        // [LakeLanes.orderByDepth] order, so the nearer thing is always in front.
        var lakeItems = 0
        if (lake.sailboatsVisible) {
            lakeItems = gatherLakeDecorations(
                lakeItems, top, bandHeight, elapsedSeconds, lake.sailboatsDensity,
                seedSalt = EffectId.SAILBOATS, isDolphin = false,
            )
        }
        if (lake.dolphinsVisible) {
            lakeItems = gatherLakeDecorations(
                lakeItems, top, bandHeight, elapsedSeconds, lake.dolphinsDensity,
                seedSalt = EffectId.DOLPHINS, isDolphin = true,
            )
        }
        // The waves join the same pass, keyed like everything on it by where they meet the water.
        if (LiveWeatherSceneRules.wavesOnLake(rainingNow, stormActiveNow, snowingNow)) lakeItems = gatherWaves(lakeItems, top, bandHeight, dayPhase.dayBlend, elapsedSeconds)
        LakeLanes.orderByDepth(lakeItemDepth, lakeItems, lakeDrawOrder)
        for (n in 0 until lakeItems) {
            val slot = lakeDrawOrder[n]
            if (lakeItemIsWave[slot]) {
                drawWaveItem(canvas, lakeItemX[slot], lakeItemY[slot], lakeItemScale[slot])
            } else if (lakeItemIsDolphin[slot]) {
                drawDolphin(canvas, lakeItemX[slot], lakeItemY[slot], lakeItemPhase[slot], elapsedSeconds)
            } else {
                drawSailboat(canvas, lakeItemX[slot], lakeItemY[slot])
            }
        }
    }

    // Slots for one frame of lake surface, sized for every candidate: fields, so the draw path allocates nothing.
    private val lakeItemX = FloatArray(LakeLanes.LANE_COUNT + WAVE_POOL)
    private val lakeItemY = FloatArray(LakeLanes.LANE_COUNT + WAVE_POOL)

    /**
     * What each item is sorted on: where its drawing meets the water ([LakeLanes.visibleWaterline]), so a hull further
     * down the band is nearer and painted later; for a dolphin in the air, its lane less how far it has risen.
     */
    private val lakeItemDepth = FloatArray(LakeLanes.LANE_COUNT + WAVE_POOL)
    private val lakeItemPhase = FloatArray(LakeLanes.LANE_COUNT + WAVE_POOL)
    private val lakeItemIsDolphin = BooleanArray(LakeLanes.LANE_COUNT + WAVE_POOL)
    /** A wave rather than a boat or a dolphin: the pass draws all three, sorted together. */
    private val lakeItemIsWave = BooleanArray(LakeLanes.LANE_COUNT + WAVE_POOL)
    /** Only a wave carries one: boats and dolphins are drawn at their category's fixed scale. */
    private val lakeItemScale = FloatArray(LakeLanes.LANE_COUNT + WAVE_POOL)
    private val lakeDrawOrder = IntArray(LakeLanes.LANE_COUNT + WAVE_POOL)

    /**
     * One screen-wide copy of the water, offset by [xOffset] ([drawLake] lays the copies side by side).
     *
     * **The water is a mirror** (decision 31): one vertical gradient from a tone [LAKE_MIRROR_SKY_SHARE] of the way to
     * the sky's horizon colour at the far edge into the theme's lake colour at the near edge, plus the drifting
     * glints; the waves are items laid on it in bad weather. **The top edge is a flat straight line and must stay
     * one**: the mountains stand on the band's top Y, and an edge dipping below it would open bare sky between them;
     * where the mirror leaves it too quiet, [drawWaterline] strikes it.
     */
    private fun drawLakeBand(canvas: SceneCanvas, xOffset: Float, top: Float, bottom: Float, bandHeight: Float, elapsedSeconds: SceneTime) {
        canvas.drawVerticalGradientRect(
            xOffset, top, xOffset + screenWidth, bottom,
            ColorUtils.blendARGB(lakePaint.color, skyHorizonColorNow, LAKE_MIRROR_SKY_SHARE),
            lakePaint.color,
        )
        drawLakeSparkles(canvas, xOffset, top, bandHeight, elapsedSeconds)
    }

    /**
     * The cut edge of the water, struck along the top of the band ([WATERLINE_MIN_LUMA_GAP]): the surface tone carried
     * toward white or black, away from the sky, just far enough to stand that gap off the sky above the shore
     * ([skyAbove] at the band's top). Water already that far clear draws no line.
     */
    private fun drawWaterline(canvas: SceneCanvas, top: Float) {
        val surface = ColorUtils.blendARGB(lakePaint.color, skyHorizonColorNow, LAKE_MIRROR_SKY_SHARE)
        val skyLuma = rec601Luma(skyAbove(top))
        val surfaceLuma = rec601Luma(surface)
        val away = if (surfaceLuma >= skyLuma) 1f else 0f          // white or black, away from the sky
        val target = if (surfaceLuma >= skyLuma) skyLuma + WATERLINE_MIN_LUMA_GAP else skyLuma - WATERLINE_MIN_LUMA_GAP
        val anchorLuma = away * 255f
        val t = ((target - surfaceLuma) / (anchorLuma - surfaceLuma)).coerceIn(0f, 1f)
        if (t <= 0f) return
        ripplePaint.style = Paint.Style.FILL
        ripplePaint.color = ColorUtils.blendARGB(surface, if (away > 0f) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), t)
        canvas.drawRect(0f, top, screenWidth.toFloat(), top + WATERLINE_THICKNESS_PX, ripplePaint)
    }

    /**
     * The sky's colour at [y]. **Not the horizon colour**: the sky is one gradient from `skyTopColorNow` at y = 0 to
     * `skyHorizonColorNow` at the bottom of the screen, and the water's top edge sits well above that, so a line
     * derived against the horizon colour would fall between the sky and the water instead of clear of both.
     */
    private fun skyAbove(y: Float): Int =
        ColorUtils.blendARGB(skyTopColorNow, skyHorizonColorNow, (y / screenHeight).coerceIn(0f, 1f))

    /**
     * Rec. 601 luma, the weighting [StormAtmosphere.dim] and this renderer's contrast work (rain,
     * waterline, waves) use; SceneColour's night shading uses Rec. 709.
     */
    private fun rec601Luma(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000f
    }

    /** The drifting glints on the surface: a fixed handful, stateless. */
    private fun drawLakeSparkles(canvas: SceneCanvas, xOffset: Float, top: Float, bandHeight: Float, elapsedSeconds: SceneTime) {
        val sparkleSeed = seedFor(EffectId.LAKE_SPARKLES)
        ripplePaint.style = Paint.Style.STROKE
        ripplePaint.strokeWidth = 1.5f
        ripplePaint.strokeCap = Paint.Cap.ROUND
        ripplePaint.color = ColorUtils.blendARGB(lakePaint.color, 0xFFFFFFFF.toInt(), 0.5f)
        for (i in 0 until LAKE_SPARKLE_POOL_SIZE) {
            val phase = CandidateNoise.value(sparkleSeed, i, CandidateNoise.CH_PHASE)
            val laneFraction = CandidateNoise.range(sparkleSeed, i, CandidateNoise.CH_Y, 0.15f, 0.85f)
            val sy = top + bandHeight * laneFraction
            val drift = elapsedSeconds.cycle(CandidateNoise.range(sparkleSeed, i, CandidateNoise.CH_SPEED, 0.03f, 0.05f), phase)
            val sx = xOffset + drift * screenWidth
            val twinkle = (elapsedSeconds.sinAt(3f, phase * 6.28f) * 0.5f + 0.5f)
            ripplePaint.setAlphaWithoutAllocating((140 * twinkle).toInt().coerceIn(0, 255))
            canvas.drawLine(sx - 5f, sy, sx + 5f, sy, ripplePaint)
        }
        ripplePaint.setAlphaWithoutAllocating(255)
    }

    /** One scratch shape for every sliver of the light's path, so the glitter allocates nothing on the frame path. */
    private val lakeScratchShape = SceneShape(8)

    /**
     * The sun or the moon reflected as a soft glow on the mirror, under the body. **The centre is one radius below the
     * waterline**: [SceneCanvas] has no clip, so a glow centred on the band's top would spill into the sky above the
     * shore; at `top + radius` its zero alpha lies on the waterline. Its lower half runs under the hills, drawn after.
     */
    private fun drawLakeMirrorGlow(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, top: Float, bandHeight: Float) {
        if (!celestialShownNow) return
        val low = (1f - dayPhase.celestialY).coerceIn(0f, 1f)
        val gain = (if (celestialIsSunNow) 0.35f + 0.65f * low else (0.3f + 0.7f * low) * (1f - dayPhase.dayBlend) * stillMoonFrame.alpha) *
            StormAtmosphere.sunVisibility(stormStrength())
        val bodyColor = if (celestialIsSunNow) sceneCustomization.sun.color else sceneCustomization.moon.color
        val radius = bandHeight * LAKE_MIRROR_GLOW_RADIUS_BANDS
        canvas.drawRadialGlow(celestialCxNow, top + radius, radius, bodyColor, (90f * gain).toInt().coerceIn(0, 255))
    }

    /**
     * **The light's path**: slivers cut on the water under the sun or the moon, widening and lengthening toward the
     * near edge; not tiled, since the body does not scroll with the water.
     */
    private fun drawLakeGlitter(
        canvas: SceneCanvas,
        dayPhase: SunPositionCalculator.DayPhase,
        top: Float,
        bandHeight: Float,
        elapsedSeconds: SceneTime,
        storm: Float,
    ) {
        if (!celestialShownNow) return
        val low = (1f - dayPhase.celestialY).coerceIn(0f, 1f)
        val gain = if (celestialIsSunNow) {
            (0.3f + 0.7f * low * low) * StormAtmosphere.sunVisibility(storm)
        } else {
            (0.25f + 0.75f * low * low) * kotlin.math.sqrt((1f - dayPhase.dayBlend).coerceIn(0f, 1f)) * stillMoonFrame.alpha
        } * (1f - 0.6f * storm)
        if (gain <= 0.02f) return
        val bodyColor = if (celestialIsSunNow) sceneCustomization.sun.color else sceneCustomization.moon.color
        val seed = seedFor(EffectId.LAKE_SPARKLES) xor 0x511
        ripplePaint.style = Paint.Style.FILL
        ripplePaint.color = ColorUtils.blendARGB(
            ColorUtils.blendARGB(lakePaint.color, bodyColor, 0.7f), 0xFFFFFFFF.toInt(), 0.25f,
        )
        for (k in 0 until LAKE_GLITTER_POOL_SIZE) {
            val lane = 0.035f + k * 0.055f + (CandidateNoise.value(seed, k, CandidateNoise.CH_Y) - 0.5f) * 0.024f
            val y = top + bandHeight * lane
            val spread = 4f + 70f * lane
            val phase = CandidateNoise.value(seed, k, CandidateNoise.CH_PHASE)
            val x = celestialCxNow + (CandidateNoise.value(seed, k, CandidateNoise.CH_X) - 0.5f) * 2f * spread +
                elapsedSeconds.sinAt(0.7f, phase * 6.28f) * 3f
            val half = 4f + 26f * lane
            val thick = 1.2f + 2f * lane
            val twinkle = elapsedSeconds.sinAt(2.4f, phase * 6.28f) * 0.5f + 0.5f
            ripplePaint.setAlphaWithoutAllocating((255f * gain * (0.45f + 0.55f * twinkle)).toInt().coerceIn(0, 255))
            lakeScratchShape.moveTo(x - half, y)
            lakeScratchShape.lineTo(x - half * 0.3f, y - thick)
            lakeScratchShape.lineTo(x + half, y)
            lakeScratchShape.lineTo(x + half * 0.3f, y + thick)
            lakeScratchShape.close()
            canvas.drawShape(lakeScratchShape, ripplePaint)
        }
        ripplePaint.setAlphaWithoutAllocating(255)
    }

    /**
     * Places one category's present candidates into the frame's lake slots from [from] and returns the new count;
     * draws nothing, since what is in front of what is decided once every category is placed.
     */
    private fun gatherLakeDecorations(
        from: Int,
        bandTop: Float,
        bandHeight: Float,
        elapsedSeconds: SceneTime,
        density: Float,
        seedSalt: Int,
        isDolphin: Boolean,
    ): Int {
        var count = from
        val effectOffset = CandidateThreshold.offsetFor(seedSalt)
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(density, LAKE_DECORATION_POOL_SIZE, effectOffset)
        val seed = seedFor(seedSalt)

        // The hills paint over the lower water, so boats and dolphins are kept above the line the hill's top edge
        // never reaches (its crest is never above 0.13 - 0.09 = 0.04 of its band, whatever hillsVariation): exposed at
        // every column, as far as the band's height allows.
        val hillNeverCoveredAbsY = (yOffsets[0] + heightFractions[0] * 0.02f) * screenHeight
        // A near-zero band height would make the division NaN, which coerceIn does not reliably clamp.
        val safeLaneFractionMax = if (bandHeight > 1f) {
            ((hillNeverCoveredAbsY - bandTop) / bandHeight).coerceIn(0.06f, 0.9f)
        } else {
            0.5f
        }

        for (i in 0 until LAKE_DECORATION_POOL_SIZE) {
            if (!CandidateThreshold.isPresent(i, density, effectOffset, fallbackIndex)) continue

            // **Both categories use the whole lake and never share a lane**: the band is cut into
            // [LakeLanes.LANE_COUNT] lanes top to bottom, boats on the even ones and dolphins on the odd, one candidate a
            // lane, so no two drift through each other on one line; where in its lane a candidate sits is its own
            // noise, so nothing reads as a grid.
            val laneSpan = safeLaneFractionMax - 0.02f
            val laneHeight = laneSpan / LakeLanes.LANE_COUNT
            val laneIndex = LakeLanes.laneIndex(i, isDolphin)
            val laneBase = 0.02f + laneIndex * laneHeight
            val laneFraction = CandidateNoise.range(
                seed,
                i,
                CandidateNoise.CH_Y,
                laneBase + laneHeight * 0.1f,
                laneBase + laneHeight * 0.9f,
            )
            val y = bandTop + bandHeight * laneFraction
            val speed = CandidateNoise.range(seed, i, CandidateNoise.CH_SPEED, 0.03f, 0.05f)
            val phase = CandidateNoise.value(seed, i, CandidateNoise.CH_PHASE) * 6.28f
            val drift = elapsedSeconds.cycle(speed, phase / 6.28f)
            val x = drift * (screenWidth + 160f) - 80f

            lakeItemX[count] = x
            lakeItemY[count] = y
            lakeItemPhase[count] = phase
            lakeItemIsDolphin[count] = isDolphin
            lakeItemIsWave[count] = false
            // **Keyed where each drawing meets the water** ([LakeLanes.visibleWaterline]): a boat at its hull, 25 of
            // its units below this point; a dolphin in the air at its belly, 29 of its units below its body's centre,
            // so it recedes as it rises and slips behind a sail it climbs past instead of flying through it; under
            // water only its splash shows, on the lane. `LakeLanesTest` holds that boats keep their order, that the
            // climb only ever moves a dolphin back, and that a farther dolphin cannot pass a nearer one.
            lakeItemDepth[count] = LakeLanes.visibleWaterline(
                kind = if (isDolphin) LakeLanes.Kind.DOLPHIN else LakeLanes.Kind.SAILBOAT,
                placementY = y,
                heightAboveLane = if (isDolphin) dolphinClimb(phase, elapsedSeconds) else 0f,
                screenHeight = screenHeight.toFloat(),
            )
            count++
        }
        return count
    }

    /**
     * How far above its lane a dolphin's body is now, in screen pixels, zero under water: one function, so the sort in
     * [gatherLakeDecorations] and the placement in [drawDolphin] agree exactly.
     */
    private fun dolphinClimb(phase: Float, elapsedSeconds: SceneTime): Float {
        val arc = elapsedSeconds.sinAt(DOLPHIN_LEAP_RATE, phase * 6.28f)
        if (arc <= 0f) return 0f
        return arc * SceneSpace.DOLPHIN_LEAP_METRES * SceneSpace.LAKE_PIXELS_PER_METRE *
            SceneSpace.sceneScale(screenHeight.toFloat())
    }

    /** One dolphin, mid-leap or mid-splash, at the point [gatherLakeDecorations] placed it. */
    private fun drawDolphin(canvas: SceneCanvas, x: Float, y: Float, phase: Float, elapsedSeconds: SceneTime) {
            // `dolphin_body.png`, untinted fixed art in its own greys and blues, drawn only while the animal is **out of
            // the water**: `arc` is a sine, its positive half the leap, 0 at the surface, 1 at the top; below zero
            // there is nothing to draw, and no clip is needed. The rotation follows the arc's slope, nose up on the way
            // out and down on the way in, the sprite's centre line on the waterline as it leaves and enters.
            val arc = elapsedSeconds.sinAt(DOLPHIN_LEAP_RATE, phase * 6.28f)
            val lakeScale = SceneSpace.sceneScale(screenHeight.toFloat())

            // **A splash at both crossings, derived from the leap's phase**: in a 0..1 cycle the body breaks the surface
            // at 0 and meets it again at 0.5, each opening a window of [SPLASH_WINDOW_CYCLES], too short to overlap. A
            // frame inside a window draws the splash at its size there, one outside draws nothing: no state, so nothing
            // is lost across a surface change, a pause or a dropped frame.
            val cyclePosition = elapsedSeconds.cycle(
                DOLPHIN_LEAP_RATE / TWO_PI,
                phase * 6.28f / TWO_PI,
            )
            val splashProgress = when {
                cyclePosition < SPLASH_WINDOW_CYCLES -> cyclePosition / SPLASH_WINDOW_CYCLES
                cyclePosition >= 0.5f && cyclePosition < 0.5f + SPLASH_WINDOW_CYCLES ->
                    (cyclePosition - 0.5f) / SPLASH_WINDOW_CYCLES
                else -> -1f
            }
            if (arc <= 0f && splashProgress < 0f) return

            if (arc > 0f) {
                val climb = dolphinClimb(phase, elapsedSeconds)
                val slope = elapsedSeconds.cosAt(DOLPHIN_LEAP_RATE, phase * 6.28f)
                canvas.save()
                canvas.translate(x, y - climb)
                canvas.rotate(-slope * DOLPHIN_LEAP_TILT_DEGREES)
                // Sized in metres over the lake's one metric, as the sailboat is ([SceneSpace]), so a dolphin is never
                // longer than the boat beside it.
                canvas.scale(SceneSpace.DOLPHIN_BASE_SCALE * lakeScale, SceneSpace.DOLPHIN_BASE_SCALE * lakeScale)
                sprites.draw(canvas, R.drawable.dolphin_body, DOLPHIN_ORIGIN_X_UNITS, DOLPHIN_ORIGIN_Y_UNITS, SpriteScale.SCENE_UNITS)
                canvas.restore()
            }

            // After the animal, so it rises through its own splash on the way out.
            if (splashProgress >= 0f) {
                canvas.save()
                canvas.translate(x, y)
                // At the animal's own DOLPHIN_BASE_SCALE, so the two are sized together.
                val splashScale = SceneSpace.DOLPHIN_BASE_SCALE * lakeScale
                canvas.scale(splashScale, splashScale)
                sprites.draw(
                    canvas,
                    if (splashProgress < SPLASH_FRAME_SPLIT) R.drawable.water_splash0
                    else R.drawable.water_splash1,
                    SPLASH_ORIGIN_X_UNITS,
                    SPLASH_ORIGIN_Y_UNITS,
                    SpriteScale.SCENE_UNITS,
                    (255f * (1f - splashProgress * splashProgress)).toInt().coerceIn(0, 255),
                )
                canvas.restore()
            }
    }

    /** One sailboat at the point [gatherLakeDecorations] placed it. */
    private fun drawSailboat(canvas: SceneCanvas, x: Float, y: Float) {
            // Hull and sail are two sprites of fixed art (wood browns; off-white with a red band), scaled by the lake's
            // metric as the dolphin is. **Sail first, hull over it**: the hull's gunwale covers the sail's foot, so the
            // mast reads as stepped into the deck. The origins centre the sail's content over the hull's, the mast
            // amidships, and the hull's content on the placement point; the hull's y of 8 is what
            // `SAILBOAT_HULL_WATERLINE_UNITS` derives its 25 from.
            val boatScale = SceneSpace.SAILBOAT_BASE_SCALE * SceneSpace.sceneScale(screenHeight.toFloat())
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(boatScale, boatScale)
            sprites.draw(canvas, R.drawable.sailboat_sail, -27f, -50f, SpriteScale.SCENE_UNITS)
            sprites.draw(canvas, R.drawable.sailboat_hull, -40f, 8f, SpriteScale.SCENE_UNITS)
            canvas.restore()
    }

    private fun drawHillLayers(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase) {
        for (layer in 0 until layerCount) {
            val color = blendColor(
                hillLayerColor(sceneCustomization.hillsColorNight, layer),
                hillLayerColor(sceneCustomization.hillsColorDay, layer),
                dayPhase.dayBlend,
            )

            val parallax = parallaxFactors[layer] * parallaxStrength
            val layerTop = screenHeight * yOffsets[layer]
            val layerHeight = screenHeight * heightFractions[layer]

            val tileWidth = screenWidth * 2f
            val wrappedShift = wrappedScrollShift(parallax.toDouble(), tileWidth)

            // The objects wrap on exactly the hills' tile width and shift: two periods applied to one growing shift
            // would drift apart once either wraps.
            val objectTileWidth = tileWidth
            val objectShiftWrapped = wrappedShift
            // objectGroundGeometry carries only the horizontal half of the ground, the part that varies per frame (the
            // vertical half is SceneSpace's). The copy bias is worked out in Double from the wrap's own unwrapped
            // product, so the two never disagree by an ulp.
            val unwrappedShift = -scrollProgress * screenWidth * parallax
            val scrollTileBias = Math.round((unwrappedShift - wrappedShift) / tileWidth).toInt()
            objectGroundGeometry.shiftXWrapped = objectShiftWrapped
            objectGroundGeometry.tileWidth = objectTileWidth
            objectGroundGeometry.scrollTileBias = scrollTileBias

            val path = baseHillShapes[layer] ?: continue

            // A subtle vertical gradient, lighter at the ridge and the configured colour from 35 % down the layer:
            // paper catching the light, on flat vertex-coloured geometry. Worked out once per layer, not per copy.
            val hillHighlight = ColorUtils.blendARGB(color, 0xFFFFFFFF.toInt(), 0.12f)
            val gradientBottom = layerTop + layerHeight * 0.35f

            // The tile and its two neighbours, as every layer: the path is one tile wide and the shift ranges over a
            // whole tile, so one copy leaves bare sky at the right for most of the wrap.
            for (tileOffset in -1..1) {
                val offsetShift = wrappedShift + tileOffset * tileWidth
                // The path spans [-0.5, 1.5] screen widths (buildBaseHillPath): skipped only when wholly off screen.
                if (offsetShift + 1.5f * screenWidth < 0f || offsetShift - 0.5f * screenWidth > screenWidth) continue

                // No shadow under the hills: the opaque fill would cover it, but for the slivers a GPU fan spills
                // above the ridge where the wave dips ([SceneShape]).
                canvas.save()
                canvas.translate(offsetShift, 0f)
                canvas.drawVerticalGradientShape(
                    path, layerTop, gradientBottom, hillHighlight, color, 255,
                )
                canvas.restore()
            }
        }
    }

    /** A layer's colour darkened by [HILL_LAYER_DARKEN]: with one layer, darkened by 0, the user's colour itself. */
    private fun hillLayerColor(baseColor: Int, layer: Int): Int {
        val darkenAmount = HILL_LAYER_DARKEN.getOrElse(layer) { 0f }
        return ColorUtils.blendARGB(baseColor, 0xFF000000.toInt(), darkenAmount)
    }

    /**
     * Builds one hill layer's skyline, two screens wide, at the shift-0 position: a sine, `0.13 +/- 0.09 x
     * hillsVariation` of the band with two cycles a tile and a phase from the theme, smooth and periodic by
     * construction, sampled densely. Its range `[0.04, 0.22]` at variation 1 is what
     * [SceneSpace.HILL_SOLID_TOP_DEPTH_FRACTION] is derived from. The parallax is a translate at draw time, so this
     * runs only when the theme, the size or [hillsVariation] change.
     */
    private fun buildBaseHillPath(path: SceneShape, layer: Int, top: Float, height: Float) {
        path.reset()
        val width = screenWidth * 2f
        val startX = -screenWidth * 0.5f

        // *Hills Variation* (0..1) scales the amplitude, flat at 0, never past the derived range at 1. The wave is
        // [SceneSpace.hillCrestFraction], the one statement of the crest, which the towers are planted by too
        // ([SceneSpace.towerDrawnDepth]); the phase gives each theme its own hill line.
        val phase = SceneSpace.hillCrestPhase(theme.id, layer)

        // One pixel of overlap past each end of the tile: two copies meet at the tile edge, and on the `Canvas`
        // backend their anti-aliased edges would each cover the seam's column by half, showing a faint line of what
        // is behind the hills. The ridge is periodic, so the extra points carry the end samples' heights.
        val seam = HILL_TILE_SEAM_OVERLAP_PX
        path.moveTo(startX - seam, top + height)
        path.lineTo(startX - seam, top + height * SceneSpace.hillCrestFraction(0f, hillsVariation, phase))
        // 64 samples for two cycles: cheap, since the path is cached.
        val samples = 64
        var lastY = top + height
        for (i in 0..samples) {
            val f = i / samples.toFloat()
            val heightFrac = SceneSpace.hillCrestFraction(f, hillsVariation, phase)
            val x = startX + f * width
            val y = top + height * heightFrac
            path.lineTo(x, y)
            lastY = y
        }
        path.lineTo(startX + width + seam, lastY)
        path.lineTo(startX + width + seam, top + height)
        path.close()
    }
}
