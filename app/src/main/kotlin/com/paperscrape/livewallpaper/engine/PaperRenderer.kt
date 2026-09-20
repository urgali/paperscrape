package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import com.paperscrape.livewallpaper.R
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws one frame of the paper-cutout landscape into the given canvas.
 *
 * The scene is built from:
 *  - a vertical sky gradient that blends across day/night phases
 *  - a scattering of stars (visible at night only, twinkling)
 *  - a sun or moon disc following an arc across the sky
 *  - N layers of "paper" hills, each with its own parallax speed and a soft drop shadow,
 *    which together create the classic layered paper-cutout look.
 *
 * The renderer is stateless between frames except for the star field, which is generated
 * once per screen size and reused (so stars don't jump around every frame).
 *
 * No longer applies a paper-grain texture (removed in v58): a real-device test found it made
 * colors read noticeably duller/grayer than what the user actually picked (the whole point of
 * a multiply blend is to darken, however subtly) and kept 1-2 CPU cores pegged even after v56
 * scoped it down to just 3 large elements -- `BitmapShader`/`Matrix`/clip-path work is real
 * per-frame `Canvas` cost with no cheap way around it on this rendering API. Flat, exact color
 * fills read as more faithful to what the user configures and cost nothing extra, so that's
 * what every element uses now. See CHANGELOG.md's v58 entry for the full account, including why
 * an earlier attempt (v55-v57) to make this work is still worth reading before trying again.
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
     * The single combined scroll position everything below actually scrolls by -- two
     * contributing inputs, because they are genuinely different mechanisms -- one multiplies a
     * per-frame time delta, the other is a swipe offset: a continuous drift that always
     * advances in *one direction* at [scrollSpeed], plus (only if [swipeScrollEnabled]) the
     * home-screen swipe offset on top of it. [parallaxStrength] then scales how far each *layer*
     * moves relative to this one shared position, same as before.
     *
     * An earlier version of this made the continuous part a bounded back-and-forth sway instead
     * of true one-directional motion, specifically to avoid a real desync bug: hills (wrapping
     * every 2x screen width, for organic non-repeating variety) and static objects *used to* wrap
     * the same shiftX modulo a separate, narrower 1x screen width -- two different moduli applied
     * to the same growing value only agree while neither has wrapped, so anything beyond one
     * screen width of drift visibly desynced them. That's fixed now at the root: objects wrap on
     * the exact same tileWidth and wrapped shift as the hills they sit on (see `drawHillLayers`),
     * so there are no longer two different periods to fall out of sync *at any magnitude* -- true
     * one-directional infinite scroll no longer risks reopening that bug, so it's no longer
     * artificially bounded to a sway.
     *
     * [continuousScrollAccum] is a `Double`, not a `Float` -- a later version of this doc comment
     * claimed a plain Float had "more than enough precision headroom... across weeks of
     * continuous uptime regardless", which was wrong and caused a real, reported bug: hills
     * visibly breaking apart mid-screen with objects left floating in open sky after enough
     * scrolling. A Float32's per-frame increment (`deltaSeconds * scrollSpeed * 0.04f`, typically
     * a few thousandths) becomes smaller than the accumulator's own representable precision (ULP)
     * once the accumulator reaches roughly 10-80 thousand at typical scroll speeds -- reachable in
     * hours to a couple of days of continuous uptime, not weeks -- and from that point on, the
     * *different* multiplication chains each layer uses to derive its own wrapped shift
     * (`-scrollProgress * screenWidth * layer-specific-parallax`, a different parallax per layer)
     * round that same imprecise value differently, so different layers visibly drift out of
     * alignment with each other rather than failing all at once. A `Double` defers that precision
     * cliff by roughly 8 more orders of magnitude, past any realistic device uptime, using the
     * exact same one-directional, never-reset accumulation this comment already establishes is
     * correct -- it does not reintroduce the periodic-reset approach two paragraphs up already
     * explains is wrong.
     */
    private val scrollProgress: Double
        get() {
            val swipe = if (swipeScrollEnabled) homeScreenOffset else 0f
            return continuousScrollAccum + swipe
        }

    /**
     * One layer's parallax shift, already wrapped into `(-tileWidth, 0]`.
     *
     * The multiplication and the wrap both happen in `Double`, and only the wrapped result — which
     * is always smaller than one tile — is narrowed to `Float`. That ordering is the whole point:
     * [scrollProgress] grows without bound, and the previous code narrowed it to `Float` *before*
     * multiplying, so the same precision cliff the `Double` accumulator was introduced to avoid was
     * reintroduced at the point of use. Scrolling would quantise into steps after roughly the same
     * timescale as the old `Float` time base.
     *
     * Wrapping [scrollProgress] itself is not an option: every layer multiplies it by a different
     * parallax factor and by the user-set [parallaxStrength], which is continuous over `0.5..2`, so
     * no wrap period can be a whole number of tiles for all of them at once.
     */
    /**
     * The candidate seed for one effect in the current theme.
     *
     * [EffectId] values are small consecutive ordinals so that their threshold offsets can be
     * spaced evenly, which means a plain `xor` would leave two effects' seeds differing in only
     * the lowest bits. Multiplying the ordinal by a large odd constant first spreads that
     * difference across the whole word before the noise function's own avalanche, so effects stay
     * uncorrelated in their attributes as well as in their selection.
     *
     * `String.hashCode` is specified exactly by the Java language, so a given theme yields the
     * same scene on every device and every run.
     */
    private fun seedFor(effectOrdinal: Int): Int =
        theme.id.hashCode() xor (effectOrdinal * -0x61c88647)

    private fun wrappedScrollShift(parallax: Double, tileWidth: Float): Float {
        var shift = (-scrollProgress * screenWidth * parallax) % tileWidth
        if (shift > 0.0) shift -= tileWidth
        return shift.toFloat()
    }

    var parallaxStrength: Float = 1f
    var hillsVariation: Float = 1f // 0..1, see buildBaseHillPath's own doc comment
    var scrollBackground: Boolean = false // whether sky/sun/moon/stars scroll with the parallax hills
    var sceneCustomization: SceneCustomization = SceneCustomization.DEFAULT
    // Live Weather (Phase 1d point 6): when non-null, overrides precipitation's visible/type/
    // intensity/thunderstorm and clouds' density -- see drawPrecipitation/drawClouds's own doc
    // comments for exactly how each is blended with the theme's own manual settings.
    //
    // **Set only from what LiveWeatherSchedule.decide authorises**, which is the same rule that
    // decides what the settings screen is told, so the two cannot disagree. Null means the theme's
    // own manual precipitation and clouds draw the scene: Live Weather off, nowhere to check, a
    // key that is missing or rejected, or nothing held that is still within
    // LiveWeatherSchedule.SNAPSHOT_MAX_AGE_MILLIS.
    //
    // A dropped request deliberately does *not* clear this: the last known-good conditions stay up
    // rather than flicking the scene back to the theme for one tick (LiveWeatherStatus.STALE).
    // That grace has an end -- the age cap above -- so the scene never draws weather nobody can
    // vouch for any more. Until v4.8 there was no end, and this comment claimed the opposite of
    // what the code did.
    var liveWeatherOverride: com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot? = null

    /**
     * Eases the drawn cloud cover, and each candidate's opacity, toward what the forecast reports.
     *
     * Renderer-owned rather than snapshot-owned: the snapshot is replaced wholesale by the engine
     * whenever a fetch lands, so anything remembered on it would be thrown away at exactly the
     * moment it is needed. See [CloudCoverFade] for the report this answers.
     */
    private val cloudCoverFade = CloudCoverFade(CLOUD_POOL_SIZE)

    /**
     * This frame's cloud cover as actually drawn, or null when Live Weather is not driving.
     *
     * Refreshed once per frame by [updateWeatherPredicates] and read by both [drawClouds] and
     * [stormStrength], which are separated by the whole sky in draw order.
     */
    private var drawnCloudCover: Float? = null

    // **Where the light is, this frame.** The water is a mirror since v4.26, so it has to know
    // three things the sky already worked out: whether a body was drawn at all, whether it was the
    // sun or the moon, and where. Written by [drawSky] and [drawCelestialBody], read by [drawLake],
    // which runs after both in the same frame -- the scene is composed front to back in one pass on
    // one thread, so this is a value handed forward, not shared state.
    private var celestialShownNow = false
    private var celestialIsSunNow = false
    private var celestialCxNow = 0f
    private var skyTopColorNow = 0
    private var skyHorizonColorNow = 0

    // The sky, the hill highlight and the sun/moon glow no longer keep a Paint of their own: a
    // Shader cannot be read back off a Paint, so their gradients are passed to SceneCanvas as
    // explicit stops instead, and each backend realises them its own way.
    private val mountainPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val cloudPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val mountainShape = SceneShape()
    private val lakePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val ripplePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    /**
     * The shared sprite-blitting path (see [SpriteBlitter]), also used by [SceneObjectRenderer].
     *
     * This renderer is the only one that draws in **both** scale conventions, so every call site
     * here names the one its sprite was authored in: the terrain sub-group (dolphin, sailboat) and
     * the clouds are [SpriteScale.SCENE_UNITS]; the sky sub-group (sun, moon, stars, birds) and the
     * sleigh are [SpriteScale.CANVAS_PIXELS]. Passing the wrong one is a silent
     * [SpriteBlitter.SPRITE_PIXELS_PER_UNIT]x size error, which is exactly why it is spelled out at
     * the call rather than implied by which of several similarly named helpers is in scope.
     */
    private val sprites = SpriteBlitter(context)

    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x22000000
    }
    private val precipPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val lightningPaint = Paint()
    private val leafPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * The paint the point stars are drawn with.
     *
     * Its own field rather than a shared one, and hoisted rather than built per star: [drawStars]
     * touches it sixty-odd times a frame and building a `Paint` on a draw path is the allocation
     * `AI_PROJECT_RULES.md` 5.1 forbids.
     */
    private val starPointPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = STAR_POINT_COLOR
    }

    // Lightning flash state -- a tiny, self-contained timer/fade in the same spirit as
    // [continuousScrollPhase] above, not a whole separate Effect class like [FireworkEffect]
    // (that one manages a *pool* of independent bursts with their own particle geometry; this is
    // a single global screen-wide overlay with one number to fade, not worth the extra class).
    private var lightningTimer = 4f + Random.nextFloat() * 6f
    private var lightningFlashAlpha = 0f

    /**
     * Whether the strike timer is allowed to fire. **True in the wallpaper, always.**
     *
     * The one switch in this class that exists for a test, and it is here because of what the
     * lightning is: the only thing the renderer draws that is not a function of [SceneTime]. Every
     * other animated thing is replayable from the clock, so a golden can warm a scene up for eighty
     * seconds and get the same frame every time. A storm cannot: `updateLightning` rolls the next
     * interval from the unseeded global `Random`, so a warmed-up storm carries a flash on about one
     * frame in thirty-two and the golden that warms one up has been a coin flip since v4.28
     * (`BACKLOG_v4_31.md` item 112, measured to a tenth of a grey level).
     *
     * Setting this false is how the golden harness takes the coin out of **its own** frame. It is
     * not a feature, it is not reachable from the settings, and nothing in `src/main` writes it:
     * the wallpaper keeps the lightning it always had, rolled the way it always was. The
     * alternative — deriving the strike time from the scene clock — would have made the goldens
     * deterministic by changing the sky every user looks at, and that is the version the maintainer
     * turned down.
     *
     * Only the *firing* is gated. A flash already in flight still fades on its own, because a
     * switch that also froze the fade would be a second behaviour rather than an absence of one.
     *
     * The harness side of it is `GoldenScene.pinLightning`, which is where the rule that decides
     * when a golden may ask for this lives.
     */
    var lightningStrikesEnabled: Boolean = true

    /**
     * Where the current strike's bolt hangs, as a fraction of screen width, and how tall it is
     * as a fraction of screen height.
     *
     * Rolled once when a strike begins rather than per frame: a bolt that moved while it faded
     * would read as several strikes at once. Both live here rather than in a per-strike object
     * so a strike costs no allocation.
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

    // Cached, unshifted hill silhouettes — one per layer, rebuilt only when the theme or screen
    // size changes (see rebuildHillPathsIfNeeded). Parallax is then applied purely via
    // canvas.translate() at draw time, which is essentially free, instead of recomputing every
    // control point with fresh Random() calls on every single frame. That per-frame rebuild was
    // the main source of stutter during home-screen swipes, when CPU is already busy with the
    // launcher's own transition animation.
    private val baseHillShapes = arrayOfNulls<SceneShape>(1) // sized to layerCount (declared further down)
    private var cachedPathsThemeId: String? = null
    private var cachedPathsWidth = -1
    private var cachedPathsHeight = -1
    private var cachedPathsVariation = -1f

    /**
     * Where the sky is covered, refilled by [drawClouds] each frame and read by
     * [drawPrecipitation]. Allocated once; see [CloudCoverage] for why precipitation reads a
     * local density instead of a global one.
     */
    private val cloudCoverage = CloudCoverage()

    private var objectRenderer = SceneObjectRenderer(SceneObjectCatalog.layoutFor(theme.id, theme.accentColor), sceneCustomization, context, theme.id)
    private var objectRendererThemeId = theme.id
    private var objectRendererGeneration = CustomThemeRegistry.generation()
    private var objectRendererConfig = sceneCustomization
    // Placeholder until the first drawHillLayers fills it in, which happens before every
    // objectRenderer.draw.
    //
    // tileWidth is 0, not some small positive number, because 0 is what "there is no tiling
    // period" actually means -- and it is the same value the real computation produces when
    // screenWidth is still 0, which it is until the surface has been sized (screenWidth comes from
    // holder.surfaceFrame, and tileWidth is screenWidth * 2). One sentinel therefore covers both
    // the pre-first-frame placeholder and a genuine unsized surface, and SceneObjectRenderer.draw
    // needs only its `tileWidth <= 0f` guard to handle them.
    //
    // A positive-but-meaningless value such as 1f would pass that guard while claiming the scene
    // tiles every pixel, which would put the tile enumeration into a range of roughly
    // screenWidth + 2 * halfWidth entries per object.
    private var objectGroundGeometry = GroundGeometry(0f, 0f)
    private val fireworkEffect = FireworkEffect()
    private val santaSleighEffect = SantaSleighEffect()

    /**
     * Keeps [objectRenderer] in step with the active theme and configuration.
     *
     * Runs every frame, so it is written to do as little as possible:
     *
     *  1. **Identity fast path.** The engine assigns [sceneCustomization] a fresh instance only
     *     when a preference actually changed, so a reference comparison settles the common case
     *     without walking the whole config. This used to be a deep `equals()` on every frame.
     *  2. **Full reconstruction** only when the *layout* changes -- a different theme, or a
     *     custom-theme edit/reset/delete signalled by the registry generation. That is the only
     *     situation in which `SceneObjectCatalog.layoutFor` can return something different.
     *  3. **In-place update** for everything else. A configuration change never needs a new
     *     renderer: [SceneObjectRenderer.customization] decides for itself whether the change
     *     affects which objects exist, and rebuilds only that much.
     *
     * Before this, any difference at all -- a colour tweak, an unrelated slider, a cloud density
     * that this renderer does not even draw -- reconstructed the whole object, regenerating every
     * candidate slot and restarting every car from its start delay.
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

    // Layer configuration: baseHeightFraction = how tall the layer is relative to screen height,
    // parallaxFactor = how much the layer shifts with home-screen scrolling (farther = slower).
    // A single layer now, not three: the hillside is one silhouette with one scroll rate, and its
    // randomized top edge only ever wobbles gently within the topmost slice of its own height (see
    // buildBaseHillPath's doc comment below for the exact numbers). The previous 3-stacked-band
    // version, with
    // each band independently colored/darkened and independently (and much more wildly) random,
    // was what read as "3 overlapping colors" instead of one cohesive hillside.
    private val layerCount = 1
    private val parallaxFactors = floatArrayOf(0.15f)
    private val heightFractions = floatArrayOf(SceneSpace.HILL_LAYER_HEIGHT_FRACTION)
    private val yOffsets = floatArrayOf(SceneSpace.HILL_LAYER_TOP_FRACTION) // top of each layer, as fraction of height

    /** The single Y line (as a fraction of screen height) below which the *farthest* hill layer
     * is guaranteed solid paper at every x, regardless of how [buildBaseHillPath]'s per-segment
     * randomness rolls -- i.e. [SceneSpace.HILL_SOLID_TOP_DEPTH_FRACTION] applied to layer 0's own band, the exact same
     * "always-covered" fraction already derived and proven for object row placement. [drawMountains]
     * and [updateLakeBandY] both anchor to this so mountains/lake always connect directly into the
     * hills with no gap, at every x -- see [drawMountains]'s own doc comment for the reasoning. */
    private val hillGuaranteedTopFraction = SceneSpace.GROUND_SOLID_TOP_Y_FRACTION

    companion object {
        // ---- Fixed candidate pools -------------------------------------------------------
        //
        // Each effect draws from a pool of a constant size, and density selects a subset of it.
        // The pool size is part of the visual contract: it sets what "100% density" looks like,
        // and changing it re-arranges that effect in every theme.
        //
        // Clouds previously derived their pool size from the density itself
        // (`(density * 40) + 1`), which meant a cloud's `(i + 0.5) / poolSize` position moved
        // whenever the slider moved. 41 keeps the same full-density sky the old formula produced
        // at density 1.0.
        const val CLOUD_POOL_SIZE = 41
        /**
         * How many precipitation slots exist, of which [PrecipitationConfig.intensity] selects a
         * share. **240 since v4.5, measured rather than chosen.**
         *
         * A metre is `45 x screenHeight / 2400` pixels, so the viewport shows the same 53.3 m of
         * world however tall it is -- a fixed pool is therefore already a fixed density per square
         * metre on every device, and the only question is whether that density reads as weather.
         * At 90 it did not: swept at 1080x2424 with the drop at its corrected size, 90 slots put
         * 39 streaks on the frame, filled 46 % of a 6x12 grid of the band they fall through, and
         * left dry holes four columns of six wide. Raising the pool is what closes those holes,
         * and it is the variable v4.4 never examined -- it inflated each drop ninefold in area
         * instead, which is how a raindrop ended up as long as a pedestrian is tall.
         *
         * | pool | streaks | coverage | grid filled | widest dry hole | frame |
         * |---|---|---|---|---|---|
         * | 90 | 39 | 0.090 % | 46 % | 4 of 6 | 27.0 ms |
         * | 120 | 55 | 0.128 % | 61 % | 2 of 6 | 26.9 ms |
         * | 180 | 80 | 0.190 % | 72 % | 2 of 6 | 30.9 ms |
         * | **240** | **108** | **0.261 %** | **86 %** | **2 of 6** | **26.6 ms** |
         * | 300 | 136 | 0.333 % | 89 % | 2 of 6 | 29.4 ms |
         * | 400 | 181 | 0.446 % | 93 % | 2 of 6 | 27.7 ms |
         *
         * 240 is the knee: the grid gains 14 points from 180 and only 3 more from 300, and the
         * ceiling is about 93 % because the top row of the grid sits above the line drops start
         * falling from. **The frame cost is flat across the whole sweep** -- 26.6 to 30.9 ms at
         * 1080x2424, no trend, well inside the spread of repeated runs of the same pool -- because
         * a `drawLine` is nothing beside the scene it is drawn over. That was measured before the
         * pool was raised, not asserted after.
         */
        const val PRECIPITATION_POOL_SIZE = 240

        /**
         * A raindrop's and a snowflake's own size, **in scene metres**.
         *
         * ### Why metres, and not pixels at some reference height (v4.5)
         *
         * Everything else in the scene declares a real size and lets [SceneSpace] turn it into
         * pixels: a house is 5.76 m, a car 1.45 m, an adult 1.75 m. v4.4 put precipitation on the
         * viewport scale but left it expressed as pixels-at-a-reference-height, and a pixel count
         * cannot be checked against anything -- so the magnitude went unnoticed. Measured on a
         * 1080x2424 frame, a v4.4 raindrop was **1.52 m long, 1.15 times the height of the
         * pedestrian beside it**, and a snowflake was 0.48 m across, twice the width of a head.
         * Reported from a real phone as rain and snow being far too large.
         *
         * Declared in metres the numbers answer for themselves, and the ceiling that keeps them
         * honest is a human figure: a drop is held to **at most 0.34 of an adult**, who is 1.75 m.
         * At 0.58 m the longest streak reads as rain; the sweep's next candidate up, 0.87 m, is
         * half an adult and reads as a falling stick. `PrecipitationScaleTest` asserts the ceiling
         * as well as the floor, which is the half v4.4 did not have.
         *
         * **The figure named here was the child until v4.30**, when the children were redrawn at
         * 0.65 of an adult instead of 0.779 and the same drop went from 0.43 of a child to 0.51 of
         * one. The ceiling is the same length in metres either way; what moved is the drawing it
         * was being read against. See `PrecipitationScaleTest` and `BACKLOG_v4_30.md`.
         *
         * ### The sizes themselves
         *
         * These are stylised, not physical -- a 2 mm raindrop would be invisible -- and the
         * project has the precedent: `SceneVariant.BUNNY` is 0.9 m rather than its real 0.55 m
         * "because at the physical height it did not read". What matters is that the number is
         * declared, comparable with the rest of the world, and defended by a measurement.
         */
        const val RAIN_LENGTH_MIN_METRES = 0.36f
        const val RAIN_LENGTH_MAX_METRES = 0.58f
        const val RAIN_STROKE_WIDTH_METRES = 0.044f

        /**
         * A flake is measured as a diameter, and judged against a head rather than a child: it is
         * a disc, so its mass reads by area. At 0.30 m the largest flake is just under one head
         * (a head is roughly 0.23 m); v4.4's 0.60 m was two.
         *
         * Halved from v4.4 rather than reduced to the pre-v4.4 0.20 m, because snow was the half
         * of this that already read on a device: at 0.13-0.30 m over the new pool it keeps
         * 0.38 % coverage against v4.4's 0.57 %, spread over 109 flakes instead of 39, with the
         * grid filled 83 % against 50 %. Smaller *and* more legible than what shipped.
         */
        const val SNOW_DIAMETER_MIN_METRES = 0.13f
        const val SNOW_DIAMETER_MAX_METRES = 0.30f

        /** How far a flake drifts sideways as it falls. Scaled with the flake it belongs to. */
        const val SNOW_SWAY_METRES = 0.31f

        /** How far past the bottom edge a drop keeps falling before it wraps. */
        const val PRECIPITATION_BOTTOM_MARGIN_METRES = 2.67f

        /**
         * How solidly a drop and a flake are painted at the middle of their fall.
         *
         * Named because [PRECIPITATION_MIN_LUMA_GAP] is stated on the composited stroke and has to
         * divide by them; they were two literals inside the draw loop, which is the shape a number
         * has when it can quietly stop agreeing with the one that depends on it.
         */
        const val RAIN_ALPHA = 190
        const val SNOW_ALPHA = 220

        /**
         * **How far a drop has to stand off the sky it falls through, in Rec. 601 luma of the
         * stroke as it is actually composited.**
         *
         * The rain's colour is the theme's own pair blended by the day phase and nothing else,
         * while the sky it falls through changes with the hour, the twilight branch and the
         * weather. Nothing kept the two apart, and **eleven of the twelve themes have an hour at
         * which they are exactly the same brightness**: measured over the twelve themes, the clock
         * swept in five-minute steps through the real [SunPositionCalculator], clear / live rain /
         * thunderstorm, and thirteen heights down the stretch of screen the drop crosses with sky
         * behind it, the worst separation is **0.00** — Easter at **06:35** under live rain, sky
         * `#878F96` against a drop of `#6A96BE` at 0.54 of the screen — against a median of
         * **28.62**. At that point the drop differs from the sky in hue only, CIELab dE **20.95**,
         * and a **1.19 px** stroke at 720x1440 carries no hue at all. That is the whole defect: the
         * maintainer reported the rain as invisible until it reached the hills.
         *
         * **Why luma and not dE, when the dolphin's gate is dE.** `LakeContrastTest` measures a
         * 40 px animal, where a shared lightness with a different hue still reads. This is a
         * hairline. Chromatic acuity collapses at that width, so the metric has to be the one the
         * eye still has there, which is luminance — the same choice, and the same weighting, as
         * [WATERLINE_MIN_LUMA_GAP], which is the project's other struck hairline.
         *
         * **Where 13.47 comes from** — the v4.22 rule, both arms measured on the same sweep: the
         * floor is the failing case, **0.00**; the signal is the same drop, at the same width and
         * the same alpha, over the background the maintainer reports it becoming visible against —
         * the hills — whose median separation is **26.94**. The gate is the midpoint. It is not a
         * number that looked right: at 13.47 **2 259 of the 10 368 situations measured are corrected
         * at all**, and the user's chosen colour stays the colour they chose: the worst case above
         * goes from `#6A96BE` to `#85A9C9`, the same pale blue lifted. The largest carry anywhere in the
         * sweep is CIELab dE **16.19** — New Year at 08:40, `#7EB2DF` drawn `#6189AB` — and it is
         * that large because that theme's sky spans the drop's own brightness between the cloud band
         * and the hills, so one colour has to clear both ends.
         *
         * **The one consequence worth knowing about**: because the direction is priced and the
         * cheaper one wins, it flips when the sky's band crosses the drop, which happens **twice a
         * day in every theme, at the two ends of it** — Sunset 06:09→06:15, `#81A2C0` to `#4D6D8B`,
         * and again 19:39→19:45 the other way. It is the same impossibility as the one above, moved
         * from space into time: the two branches are disjoint, so no continuous choice exists there
         * either. `PrecipitationContrastTest` bounds it at two per theme per day, so a third would
         * fail rather than be absorbed.
         *
         * **Snow needs none of it and that is measured, not assumed**: over the same sweep the
         * worst snow-against-sky separation is **19.13**, above the gate, so the correction
         * resolves to zero for every snow situation there is. It is left on the shared path
         * because falling snow is precipitation and a second code path would be a second thing to
         * keep true.
         *
         * `PrecipitationContrastTest` carries the whole measurement and fails if any of it moves.
         */
        const val PRECIPITATION_MIN_LUMA_GAP = 13.47f
        const val BIRD_POOL_SIZE = 6
        /**
         * The ceiling of [SceneObjectRenderer.leafSourceLeafCount], used only as the candidate
         * stride: `id * MAX + slot` keeps every copy's slots from colliding with its neighbour's
         * while letting the per-crown count vary with the crown's own drawn size. rc2 replaced
         * the fixed five-per-tree (and, before it, the shared pool of 26) because a fixed count
         * collapsed the effect on scenes with few crowns and poured it on scenes with many.
         */
        const val MAX_LEAVES_PER_SOURCE = 13
        const val MOUNTAIN_POOL_SIZE = 4
        const val LAKE_DECORATION_POOL_SIZE = 4

        /** Lake sparkles have no density control -- a fixed handful, always drawn. */
        const val LAKE_SPARKLE_POOL_SIZE = 5

        /**
         * **The water is a mirror (v4.26, concept S1 "Specchio").**
         *
         * How far the surface is carried toward the sky's own horizon colour at the far edge of
         * the band. The near edge stays the theme's lake colour, so the band is one vertical ramp
         * between the two and the water is the sky, lying down. Chosen over the two wave concepts
         * from photographs on the device: waves in a band this shallow read as stripes, and the
         * scene already carries its motion in the sky.
         */
        const val LAKE_MIRROR_SKY_SHARE = 0.55f

        /**
         * **The waterline, and why the lake needs one at all.**
         *
         * A mirror reflects the sky, so the closer a theme's sky and lake colours are, the more the
         * two agree and the less the water's top edge exists. Measured across the twelve built-in
         * themes at every point of the day/night sweep, both twilight branches, clear and storm,
         * **against the sky immediately above each theme's own shore**: the separation is a CIELab
         * dE of **2.16 in the worst case** (Tundra near midday, sky `#D0E7F2` against water
         * `#D6EAF2`) against a median of 13.93, and in Rec. 601 luma the worst gap is **0.1** --
         * the sky and the water are the same brightness.
         *
         * **The top edge may not be made wavy.** The mountains anchor to the band's own nominal top
         * Y and a jittered edge dips below it, opening a sliver of bare sky -- that is the whole
         * argument in [drawLakeBand] and it has not changed. So the distinction is made with a
         * struck edge instead: a line along the top of the band, in a tone pushed away from the
         * sky's luma until it clears [WATERLINE_MIN_LUMA_GAP].
         *
         * The gap is **14.5**, which is the median separation the twelve themes already produce
         * measured the same way. Deriving it from the distribution rather than picking it means the
         * worst theme is lifted to what a typical one already does, and no theme is given an edge
         * louder than the scene's own habit.
         */
        const val WATERLINE_MIN_LUMA_GAP = 14.5f
        const val WATERLINE_THICKNESS_PX = 2f

        /** The light's path on the water: how many slivers are cut under the sun or the moon. */
        const val LAKE_GLITTER_POOL_SIZE = 9

        // ---- The waves (v4.28) ----------------------------------------------------------------
        //
        // Three slots, one breaker each, drifting +x across the near lanes in rain and in a
        // thunderstorm and nowhere else -- a clear sky draws none, and the frame is then identical
        // to v4.27's. A slot's membership changes only while it is off screen, which is the car
        // rule of v4.22 ([CarSelection.offScreen]) applied to something that also crosses the
        // frame in plain sight.
        const val WAVE_POOL = 3

        /**
         * How far the two papers of a wave stand off the water in Rec. 601 luma, **derived and not
         * chosen** -- `WaveContrastTest` sweeps the eight themes that show a lake x 288 five-minute
         * steps x three weathers = 2 592 situations and fails if these drift from what it derives.
         *
         * The pair below is the **floor**: the v4.22 rule puts a gate halfway between a measured
         * floor (9.80, the luma the mirror's own gradient already varies by over one wave's height,
         * at its worst) and a measured signal (40 for the body and 60 for the foam, the gaps of the
         * phase-2 frame the maintainer read as a wave). Halfway is the least that can be told apart
         * from the gradient underneath.
         *
         * What the renderer *aims* at is [WaveTint.gapAt], which carries the night end up to the
         * signal; see that function for why, and `DESIGN_NOTES` 16 for the photograph that asked.
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
         * How far under a sailboat's own depth key its visible waterline sits, in boat units.
         *
         * A sailboat is keyed by its placement point and `drawSailboat` hangs the hull 8 units
         * below that, 17 units tall, so the hull meets the water 25 units under the key. A wave is
         * keyed by its base, which *is* its waterline, and the two can only be compared once they
         * are said in the same convention -- see [gatherWaves].
         */
        const val SAILBOAT_HULL_WATERLINE_UNITS = 25f

        /** The reflected glow's radius, in band heights. It is also its depth -- see
         * [drawLakeMirrorGlow] for why the two have to be the same number. */
        const val LAKE_MIRROR_GLOW_RADIUS_BANDS = 0.8f

        // The three lake decorations no longer carry a tint constant.
        //
        // `DESIGN_NOTES.md` always classified the dolphin and the sailboat as fixed art, but the
        // shipped PNGs were pure-white masks, so the untinted blits drew white silhouettes --
        // the defect reported as "dolphins and sailboats do not render". v74.1 repaired it by
        // supplying at the blit the colour the artwork was missing. The V2 asset set draws all
        // three in their own paper colours, which honours the classification for the first time
        // and makes the repair actively wrong: multiplying finished art by a second colour is
        // the mirror-image defect the repair's own test was written to catch. `DOLPHIN_COLOR`,
        // `SAILBOAT_HULL_COLOR` and `SAILBOAT_SAIL_COLOR` are deleted with the tinted blits that
        // read them, and `SpriteTintClassTest` now asserts the property from the artwork's side.

        /**
         * The four cloud depth tiers: farther tiers drift slower, sit higher and draw smaller;
         * nearer tiers the reverse. Four tiers of their own rather than reusing this file's
         * mountain/hill layer identities, which carry no
         * meaningful "a cloud is on this layer" concept.
         *
         * Constants rather than three `floatArrayOf` locals inside `drawClouds`, which is where
         * they were: the arrays are fixed data and were being allocated on every frame the cloud
         * layer is visible. Values and index order are unchanged.
         */
        /**
         * Opacity at the centre of the sun/moon's ambient glow, falling to 0 at its outer radius.
         * It was the first stop of the `RadialGradient` this replaced; the falloff is unchanged,
         * only where the number is written down.
         */
        const val CELESTIAL_GLOW_CENTRE_ALPHA = 90

        private val CLOUD_TIER_PARALLAX = floatArrayOf(0.035f, 0.05f, 0.065f, 0.08f)
        private val CLOUD_TIER_Y_OFFSET = floatArrayOf(-0.02f, 0f, 0.015f, 0.03f)
        private val CLOUD_TIER_SIZE_MULTIPLIER = floatArrayOf(0.85f, 0.95f, 1.05f, 1.15f)


        /**
         * The depth and scale model moved to [SceneSpace] in Group 4.
         *
         * `HILL_SAFE_DEPTH_MIN`, `HILL_SAFE_DEPTH_MAX`, `ROAD_SAFE_DEPTH_MAX` and `depthScaleFor`
         * used to live here, and between them they defined half the ground plane while
         * `SceneObjectRenderer` defined the other half and `SceneObjectCatalog` a third. They are
         * now [SceneSpace.HILL_SOLID_TOP_DEPTH_FRACTION], [SceneSpace.OBJECT_BAND_BOTTOM_Y_FRACTION]
         * and [SceneSpace.depthScale]; the road cap has no successor because the object band is
         * placed above the road by construction rather than by a hand-derived depth limit.
         */

        // ---- Background layer geometry ---------------------------------------------------
        //
        // The sky layer holds two things with opposite tiling natures, and treating them as one
        // is what produced the bug this geometry exists to close (see [celestialParallaxOffset]).

        /** Horizontal keep-out band the celestial body's rest position never enters, as a
         * fraction of screen width. Read by [drawCelestialBody] and by
         * [celestialParallaxOffset], which is the whole point of it being a constant: the
         * bound on how far the body may travel is derived from the same number that decides
         * where it sits, so the two can never disagree. */
        const val CELESTIAL_MARGIN_FRACTION = 0.12f

        /** The celestial disc's radius as a fraction of screen width, *before* the doubling
         * [drawCelestialBody] applies. Kept as the undoubled value, and multiplied by 2f at
         * each use, so the arithmetic keeps the exact association it had when this was written
         * inline (`screenWidth * 0.055f * 2f`) and no rendered position shifts by an ulp. */
        const val CELESTIAL_RADIUS_FRACTION = 0.055f

        /** Largest star radius [regenerateStars] can produce, in canvas pixels. */
        const val MAX_STAR_RADIUS_PX = 2.4f + 3.2f

        /**
         * What [drawStars] divides a star's radius by before blitting the sparkle.
         *
         * **This is the sprite's scale, not the star's size.** [MAX_STAR_RADIUS_PX] is untouched
         * by it: halving this divisor doubles how far the *drawing* reaches from the star it
         * marks, and changes nothing about where stars are, how many there are, or how large the
         * point stars are.
         *
         * It was 32 until v4.23, which put the largest sparkle at `30/32 x 5.6 = 5.25` px of
         * reach -- about 10 px across, and measured on the device the shape inside that was a
         * one-pixel cross rather than a star. v4.23 halves it to 16 and ships artwork drawn for
         * the size that produces (`BACKLOG_v4_22.md` item 29's "configuration 1"): the same four
         * unequal points, on a waist wide enough to survive the reduction.
         */
        const val STAR_SPRITE_RADIUS_DIVISOR = 16f

        /**
         * Half the sparkle bitmap's own span, in the local units [SpriteScale.SCENE_UNITS]
         * establishes: `star_sparkle.png` is 180px, which is `180 / SPRITE_PIXELS_PER_UNIT = 60`
         * local units, so its centre sits 30 units from either edge.
         *
         * [STAR_SPRITE_ORIGIN_UNITS] is the negation of this, which is what puts the bitmap's
         * centre on the star, and `SkySpriteAnchoringTest` reads the PNG's own header to check
         * that pairing -- so this 30 is pinned against the asset rather than restated from it.
         */
        const val STAR_SPRITE_HALF_UNITS = 30f

        /** How far a star sprite reaches left of, and right of, the star's own x.
         *
         * Symmetric, because `star_sparkle.png` is centred on the star, and **derived rather than
         * chosen**: the bitmap covers [STAR_SPRITE_HALF_UNITS] local units either side of the
         * centre, and [drawStars] scales local units by `radius / STAR_SPRITE_RADIUS_DIVISOR`, so
         * the widest a sparkle can reach is `30 / 16 x 5.6 = 10.5` canvas pixels.
         *
         * **This is why halving the divisor had to come back here.** The reach is a function of
         * the divisor, not of [MAX_STAR_RADIUS_PX] -- the largest star is the same size it always
         * was -- so doubling the star radius would have been the wrong repair for the right
         * symptom. Until v4.23 these read `MAX_STAR_RADIUS_PX` unqualified: with a divisor of 32
         * the sprite reached `0.9375 x radius`, and reserving the whole radius was a deliberate
         * over-reservation. At 16 it reaches `1.875 x radius`, so reserving the radius would be an
         * *under*-reservation, and an under-reservation drops a tile copy at a seam -- a sparkle
         * clipped where the star field wraps.
         *
         * Reserved on the *bitmap*, not on the artwork inside it: the redrawn sparkle leaves a
         * transparent margin again (its content box is `14,8..168,164`, so the drawing itself
         * reaches at most `82/3` units, `9.57` px), and the two readings that had coincided while
         * the sprite filled its canvas differ once more. Over-reserving those 0.93 px costs one
         * comparison; under-reserving costs a visible clip.
         *
         * They exist as named constants, rather than being folded into the tile bounds, because
         * the tile range must be derived from what is actually drawn: they were asymmetric while
         * the sprite was blitted with the wrong scale convention, and a test asserts them so a
         * future change to either the asset or the convention has to come back through here. */
        const val STAR_SPRITE_LEFT_EXTENT_PX =
            STAR_SPRITE_HALF_UNITS / STAR_SPRITE_RADIUS_DIVISOR * MAX_STAR_RADIUS_PX
        const val STAR_SPRITE_RIGHT_EXTENT_PX =
            STAR_SPRITE_HALF_UNITS / STAR_SPRITE_RADIUS_DIVISOR * MAX_STAR_RADIUS_PX

        // ---- Sky sprite blit geometry ----------------------------------------------------
        //
        // Each sky sprite's authoring convention and the origin that centres it, declared here
        // rather than written as literals at the call site.
        //
        // Those three numbers -- the PNG's pixel size, the convention it is read with, and the
        // origin -- are only correct together, and nothing in a PNG records which convention
        // applies to it. Both halves of defect D-1 came from one of them moving alone:
        // `star_sparkle.png` was replaced by a 3x redraw while its call site kept the raw-pixel
        // convention, and `sun_glow.png` shipped as a raw-pixel sprite carrying the origin an
        // oversampled one would want. Neither failed anything, because a literal at a call site
        // inside a `Canvas`-taking function cannot be asserted.
        //
        // `SkySpriteAnchoringTest` reads the PNG headers off disk and checks these against them,
        // so the pairing is now pinned from both ends. Keeping them here is what makes that
        // possible: constants a test can reach, used by the only code that draws these sprites.

        /**
         * `firework` is 240x240 -- 80x80 local units -- anchored on its own centre, and a fully
         * expanded burst reaches the ~120px radius the old 18-particle spray peaked at.
         */
        const val FIREWORK_SPRITE_HALF_UNITS = 40f
        const val FIREWORK_REACH_UNITS = 120f

        /**
         * The arc's own radius in local units, and what `maxRadius` is expressed against.
         *
         * **This is a scale reference, not the sprite's width, and the two stopped being the same
         * number when D-10 cropped the padding away.** The sprite was 600x312 with its arc
         * spanning x 3..597 and y 15..312; it is now 594x297 holding exactly the same drawing, and
         * the origin below moved by the trim so that every pixel of it lands where it did before.
         * The 100 has to stay 100 regardless: it is the half-width the on-screen radius is divided
         * by, so lowering it to the new canvas would scale the whole rainbow up by a percent.
         */
        const val RAINBOW_SPRITE_HALF_WIDTH_UNITS = 100f

        /** Where the cropped sprite is blitted so its arc keeps the coordinates it always had. */
        const val RAINBOW_SPRITE_ORIGIN_X_UNITS = -99f
        const val RAINBOW_SPRITE_ORIGIN_Y_UNITS = -99f

        /** Matches the peak alpha the stroked bands used, so the fade curve is unchanged. */
        const val RAINBOW_MAX_ALPHA = 200f

        /**
         * `lightning_bolt` is 90x252, so 30x84 local units, hanging from its own top edge.
         *
         * The width was 34 until D-10 cropped six pixels of padding from each side. It is used
         * only to centre the bolt -- the height, which is the scale reference, is untouched -- so
         * halving the new width puts the same drawing on the same axis.
         */
        const val LIGHTNING_BOLT_WIDTH_UNITS = 30f
        const val LIGHTNING_BOLT_HEIGHT_UNITS = 84f

        /**
         * How far down a bolt reaches, as a fraction of screen height.
         *
         * A fraction of screen height *is* a size in scene metres -- a metre is
         * `45 x screenHeight / 2400` pixels -- so this has always scaled with the viewport and was
         * never the pixel-constant defect precipitation had. What was wrong is the magnitude.
         *
         * **The oracle is the skyline, not the metre.** Measured against the size table the bolt
         * looked defensible: 8.5 m against a 16.8 m tower is half a tower. But a tower is drawn
         * far back, where perspective shrinks it, so what a viewer actually compares the bolt with
         * is the *painted* building. Measured on a 1080x2424 frame the tallest painted building is
         * 288 px, and the bolt was **325 px -- taller than anything else in the scene.**
         *
         * | fraction | bolt | vs the tallest painted building |
         * |---|---|---|
         * | 0.160 (was the max) | 381 px, 8.4 m | **1.32x** |
         * | 0.120 | 286 px, 6.3 m | 0.99x |
         * | 0.100 (was the min) | 236 px, 5.2 m | 0.82x |
         * | **0.095 (new max)** | ~225 px, 4.9 m | **0.78x** |
         * | 0.080 | 190 px, 4.2 m | 0.66x |
         * | **0.065 (new min)** | 154 px, 3.4 m | **0.53x** |
         *
         * The rule this settles on is that **a bolt never out-tops the skyline it strikes behind**,
         * with margin: at most about four fifths of the tallest painted building. The old range
         * spanned 0.82x to 1.32x and so failed it at every roll. The spread is kept, because a
         * storm whose every strike is identical reads as a loop.
         */
        const val LIGHTNING_BOLT_MIN_HEIGHT_FRACTION = 0.065f
        const val LIGHTNING_BOLT_HEIGHT_SPREAD_FRACTION = 0.030f

        /**
         * The white wash a strike puts over the whole frame, at its brightest.
         *
         * **Named, not changed (v4.5).** It was an inline `180` and is now measurable: the veil
         * covers 100 % of the frame at 180/255 = 71 % opacity, fading at 3.0/s, so a strike blanks
         * the scene for a third of a second. Rendered beside 120 and 90 it is plainly the
         * strongest of the three, and at 180 the houses and trees lose their colour entirely.
         *
         * It is left alone all the same. This batch corrects effects that are the wrong *size*
         * against the world, and a veil has no size to be wrong -- it is the whole frame by
         * construction, as a real flash is. Nothing measured shows it disproportionate to
         * anything; it is strong, which is a different claim and a decision for the maintainer
         * rather than a defect for this batch to fix. The frames are in the report.
         */
        const val LIGHTNING_VEIL_MAX_ALPHA = 180f

        /**
         * The horror sky's four corners: near-black overhead, hard orange at the horizon.
         *
         * Flat paper colours, not a photographic gradient -- the orange is one saturated tone and
         * the black is one, with the blend between them doing all the work. The day pair is
         * lighter than the night pair only enough to keep sunrise and sunset legible; this sky is
         * meant to look wrong at noon, which is the point of it.
         */
        /**
         * What the carved moon is multiplied by, in place of the theme's own moon colour.
         *
         * The sprite stays a colourless mask -- `SpriteTintClassTest` requires that of every
         * tintable sprite, and multiplying one hue by another compounds them -- so the orange has
         * to arrive at the blit. What the artwork carries instead is *luminance*: three concentric
         * paper rings, dark at the rim and bright at the centre, which this colour turns into a
         * warm lantern without a gradient, a glow or a second draw call.
         *
         * Fixed rather than derived from the theme. A carved lantern is orange in the same way a
         * pumpkin is; letting a theme's cool moon colour through would produce a blue jack-o'-
         * lantern, which is nobody's Halloween.
         */
        const val HALLOWEEN_MOON_COLOUR = 0xFFFF8C2A.toInt()

        const val HORROR_SKY_TOP_NIGHT = 0xFF07060A.toInt()
        const val HORROR_SKY_TOP_DAY = 0xFF1A1020.toInt()
        const val HORROR_SKY_LOW_NIGHT = 0xFFB03A06.toInt()
        const val HORROR_SKY_LOW_DAY = 0xFFF07A10.toInt()

        /**
         * The dolphin's re-entry splash: how long it lasts and how it is timed.
         *
         * The leap is `sin(2*PI*(f*t + phase))` and the animal is drawn only while that is
         * positive, so it meets the water again at the instant the cycle's fraction passes 0.5.
         * [SPLASH_WINDOW_CYCLES] is how much of the cycle after that instant the splash occupies
         * -- 6% of a 0.9 Hz cycle, about a fifteenth of a second at each end of it. That is short
         * enough to read as an impact rather than as a second object in the lake.
         *
         * **Nothing is stored to make this work.** The trigger is the same phase the leap is drawn
         * from, so there is no per-dolphin splash state to allocate, update or lose across a
         * surface change, and no frame can miss the event by arriving late: whatever frame lands
         * inside the window draws the splash at the right size for where it is inside it.
         */
        const val DOLPHIN_LEAP_RATE = 0.9f
        const val TWO_PI = 6.2831855f
        const val SPLASH_WINDOW_CYCLES = 0.06f
        const val SPLASH_FRAME_SPLIT = 0.4f
        const val SPLASH_ORIGIN_X_UNITS = -27f
        const val SPLASH_ORIGIN_Y_UNITS = -18f

        /**
         * The sleigh's scale and the X half of its origin, in the units its own `canvas.scale`
         * establishes.
         *
         * `santa_sleigh_scene` is 594x123 px -- 198 x 41 local units -- and so is
         * `santa_sleigh_trot`. An orphaned KDoc here used to say 624x168 "with a content box of
         * (12,12)-(610,159)", attached to nothing and describing a canvas that stopped existing at
         * the v4.7 redraw; `SANTA_CROP_REPORT.md` reported it as stale and left it, and v4.29 is
         * where it was actually removed.
         *
         * **-99.67 is knowingly not the centre of the drawing**, and that is the one thing not to
         * "fix" here. It is `-598/2/3`, from a content width the sprite had before the v4.7 redraw;
         * the content is 592 px wide, so the group sits about 3 sprite pixels left of the flight
         * point. `SANTA_CROP_REPORT.md` assessed exactly this and recorded the decision **not** to
         * realign, because the sleigh addresses its canvas corner on purpose and the registry
         * anchor is descriptive metadata. Moving it is an artwork judgement, not a tidy-up.
         *
         * [SANTA_SLEIGH_SCALE] keeps the on-screen width the shipped release had: 198 local units
         * at 1.5 is the 297 px the historical raw-pixel pair produced.
         */
        const val SANTA_SLEIGH_SCALE = 1.5f
        const val SANTA_SLEIGH_ORIGIN_X_UNITS = -99.67f

        /**
         * Moved by the trim when the sleigh's canvas was normalised, so the drawing keeps the
         * coordinates it had.
         *
         * These two constants address the sprite's **canvas corner**, not its content, which is
         * what makes a canvas change something they have to follow: the PNG lost 18 rows off the
         * top (and, harmlessly for the origin, 6 columns off the right and 12 rows off the
         * bottom), so pixel (0,0) is now 18 sprite pixels lower in the drawing than it was, and
         * the origin moves down by the same 18 -- six local units -- to cancel it. `-25.5 + 6`.
         *
         * X does not move because the trim removed nothing from the left: the content already
         * started at column 0. That is also why only one of the two numbers changed here, and a
         * future crop that does take columns off the left has to move X by its own trim.
         *
         * `SantaSleighOriginTest` derives this from the shipped PNG rather than trusting the
         * literal, so forgetting the compensation on a later crop fails instead of flying the
         * sleigh six units too high.
         */
        const val SANTA_SLEIGH_ORIGIN_Y_UNITS = -19.5f

        /**
         * How fast the sleigh's two leg poses alternate, in poses per second.
         *
         * `SceneTime.frameIndex` multiplies by this, so it is a rate and not a duration: 4.5
         * holds each pose for about a fifth of a second. Fast enough to read as a trot, slow
         * enough not to blur into one shape at the 30 fps the renderer paces at.
         */
        const val SANTA_TROT_FRAMES_PER_SECOND = 4.5f

        /**
         * Centres the 90x24 bird on its own position, in canvas pixels.
         *
         * The sprite is authored at its on-screen size, so there is no divisor here and none is
         * wanted: the 90 px width *is* the wingspan. (REN-07: this said 90x42, which was the canvas
         * before the strip was cropped to one bird.)
         */
        /**
         * How far a dolphin rises out of the water at the top of its arc, and how far it noses
         * up and down along it.
         */
        /**
         * One star in this many is drawn with the sparkle sprite; the rest are points.
         *
         * Five keeps roughly a dozen sparkles in a full field, which is enough for the sky to
         * read as having bright stars in it without the field looking like a repeated motif.
         */
        const val STAR_SPARKLE_EVERY = 5

        /**
         * The cream the sparkle art is drawn in, so a point and a sparkle are the same star.
         *
         * **It was `#FFF6DC` until v4.23 and the sparkle has never been that colour.** The shipped
         * artwork is `#FBF4E6` and so is the redraw, a difference of 4/2/10 levels -- invisible,
         * which is exactly why the sentence above stayed true-looking for four releases while
         * being false. Two ways to stop it lying were available: correct the sentence, or correct
         * the colour. The colour is corrected, because the sentence states the property that is
         * actually wanted, and `StarFieldColourTest` now reads the one fully-opaque colour out of
         * `star_sparkle.png` and asserts this constant equals it -- so the next redraw either
         * keeps the cream or is made to come back here.
         */
        const val STAR_POINT_COLOR = 0xFFFBF4E6.toInt()

        /**
         * How much of a star's radius a point covers.
         *
         * A star is a position and a brightness; the radius is what both treatments are expressed
         * against, and a point covers 0.55 of it. The sparkle sprite is much narrower between its
         * four tips than at them, so a disc of the same radius would read as a noticeably fatter
         * star. This matches its apparent weight instead of its extent.
         *
         * **The extent it is not matching is no longer 0.94 of the radius.** v4.23 halved
         * [STAR_SPRITE_RADIUS_DIVISOR], so a sparkle reaches 1.875 of the radius, and this number
         * was deliberately not rescaled with it: [drawStars] draws a field that is mostly faint
         * points with a few bright stars in it, and growing the points with the sparkles would
         * give back the uniform field that split was made to avoid.
         */
        const val STAR_POINT_RADIUS_SCALE = 0.55f

        const val DOLPHIN_LEAP_TILT_DEGREES = 26f

        /**
         * Where `dolphin_body`'s pixel (0,0) goes: the animal's own content centred on the point
         * its leap arc is computed for.
         *
         * **v5.0 moved it there.** The sprite is 342x171 px -- 114x57 local units -- with its ink
         * at `1,3..342,171`, so the drawing's centre is at **(57.167, 29.0)** units, and the pair
         * below is the negative of that. Measured off the alpha channel, and `DolphinLeapOriginTest`
         * re-measures it rather than trusting this sentence.
         *
         * *What it was, and the two corrections it took to get here.* The pair was
         * `(-56.3, -28)`, which left the animal **(+0.87, +1.0) units from the leap point** --
         * 0.8 % of its width and 1.8 % of its height. The comment that stood here justified the
         * pair with *"filled edge to edge, so its content centre sits at (57.5, 29)"*: true of the
         * v4.25 drawing and of neither since, because the v4.26 redraw moved the ink inside the
         * canvas. v4.31 measured the displacement, corrected the prose in both this file and the
         * registry -- and left the move itself as a question about the picture, because moving a
         * drawn animal is a change a photograph and the maintainer decide, not a comment pass.
         *
         * *The second correction is to v4.31's own arithmetic.* Its replacement sentence said the
         * canvas was `342x168` with ink at `1,0..342,168` and a centre at `(57.167, 28.0)` -- from
         * which the y displacement is zero, not the `+1.0` the same paragraph reported. The PNG is
         * 342x171 with ink from row 3, and `sources/sprites.json` had it right all along
         * (`contentBox [1,3,342,171]`). The conclusion survived; the derivation under it did not.
         *
         * *What moving it costs.* Every golden scene with a dolphin in it. That is why it was held
         * until a release which re-authored the goldens anyway -- v5.0 redraws the neighbourhood,
         * so the bill was already being paid.
         */
        const val DOLPHIN_ORIGIN_X_UNITS = -57.166668f
        const val DOLPHIN_ORIGIN_Y_UNITS = -29f

        /**
         * Where the bird bitmap is blitted, in raw pixels.
         *
         * The wing-flap mirrors the canvas vertically about y = 0, so what has to stay put is the
         * *drawing's* position in that frame, not the canvas's. D-10 cropped 6 px of padding from
         * the top and 15 from the bottom, and the y origin moved by the top trim so every drawn
         * pixel keeps the coordinate it had; the flip therefore produces exactly the frame it did
         * before. Changing either number without the other moves the bird.
         */
        const val BIRD_SPRITE_ORIGIN_X_PX = -25f
        const val BIRD_SPRITE_ORIGIN_Y_PX = -15f

        /** Centres a 240px raw-pixel disc in the `radius / 120f` space of the sun and moon. */
        const val CELESTIAL_DISC_ORIGIN_UNITS = -120f
        /**
         * Where `cloud_body.png` is blitted so its content centre lands on the point the
         * placement and coverage maths already use, `(cx, laneY)`.
         *
         * **This was `(-128f, -85f)`, and that pair centres a 768x510 px canvas exactly**
         * (-128 + 256/2 = 0, -85 + 170/2 = 0). No such file ships: `cloud_body.png` is
         * 798x396 px = 266x132 units with content filling it, so the drawn cloud's centre sat
         * 5 units right and 19 units above the point everything else measured from. The old
         * numbers are residue from a canvas that was replaced --
         * `RELEASE_HISTORY.md` records `cloud_body` as "the one file that did not go through
         * the automated path", which is why this is the sprite whose constants drifted.
         *
         * Derived from the asset rather than restated, so a re-crop cannot strand it again.
         */
        val CLOUD_BLIT_X = -CloudCoverage.CLOUD_CONTENT_HALF_UNITS

        /** See [CLOUD_BLIT_X]. */
        val CLOUD_BLIT_Y = -CloudCoverage.CLOUD_CONTENT_HALF_HEIGHT_UNITS

        val CELESTIAL_DISC_SCALE = SpriteScale.CANVAS_PIXELS

        /** Centres the 396px sunburst in that same space, putting its ring outside the disc's own
         * 120 units -- which is what makes it read as a sunburst rather than a fatter sun.
         *
         * **The ring is at 154..166 units, not the 150..198 this said until v4.23.** The old
         * artwork was a band of rays spanning 111..190 that overlapped the disc's edge; the
         * concept B glow is a compass-struck ring at full opacity with a soft warm halo behind it
         * reaching 197. Only the outer number was ever load-bearing -- the ring has to start past
         * 120 -- and it still is, by 34 units instead of 30. The canvas, the convention and this
         * origin are unchanged, which is why the redraw needed no call-site change.
         *
         * The sprite was 444px with 24px of transparent margin per side until the padding
         * normalisation; the origin moved by exactly the margin that was removed. */
        const val SUN_GLOW_ORIGIN_UNITS = -198f
        val SUN_GLOW_SCALE = SpriteScale.CANVAS_PIXELS

        /** Centres the 180px sparkle in the `star.radius / STAR_SPRITE_RADIUS_DIVISOR` space of a
         * star. Read as an oversampled sprite it covers 60 units, so with v4.23's divisor of 16 it
         * reaches 1.875 of the star's own radius -- the star being a position and a brightness
         * here, not an outline the drawing has to stay inside.
         *
         * Derived from [STAR_SPRITE_HALF_UNITS] rather than written as its own -30, so the origin
         * and the tile extents cannot disagree about how big the bitmap is. */
        const val STAR_SPRITE_ORIGIN_UNITS = -STAR_SPRITE_HALF_UNITS
        val STAR_SPRITE_SCALE = SpriteScale.SCENE_UNITS

        /**
         * First tile index of the star field that can reach the viewport, given an already
         * wrapped [shiftXWrapped] in `(-tileWidth, 0]`.
         *
         * The star field is a *tiled pattern*: [regenerateStars] lays stars out across
         * `[0, screenWidth)`, so it repeats with a period of exactly one screen width. Wrapping
         * its shift without also drawing the neighbouring tiles is what left a starless band up
         * to a full screen wide, and made the whole field snap back at the wrap. Same shape as
         * [SceneObjectRenderer.firstVisibleTileOffset]: a range derived from the geometry, not a
         * fixed copy count.
         *
         * Pure and in the companion on purpose -- `draw` needs a `Canvas`, so anything left as a
         * condition inside it cannot be unit tested.
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
         * Exclusive upper bound of the star-field tile range. See [firstStarTileOffset].
         *
         * Returns 0 when the surface has no width yet, which with [firstStarTileOffset]'s own 0
         * makes the caller's `until` loop empty rather than looping on a meaningless period.
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
         * The celestial body's horizontal parallax offset: bounded, never cyclic.
         *
         * The sun and the moon are single scene objects, not a tiled pattern, so the wrap every
         * other layer uses is wrong for them in both directions. Applied unwrapped it walks off
         * screen for good; applied wrapped -- which is what shipped -- it walks off the left edge
         * and then reappears at its rest position when the shift wraps, which is a periodic
         * disappearance plus a pop. Tiling it instead would put a second sun on screen.
         *
         * So the offset is bounded by the body's *own* geometry rather than by a period:
         *
         * ```
         * restCx    = margin + celestialX * (screenWidth - 2 * margin)
         * radius    = screenWidth * CELESTIAL_RADIUS_FRACTION * 2
         * slackLeft = restCx - radius          // px before the disc's left edge reaches x = 0
         * travel    = min(2 * parallax * screenWidth, slackLeft)
         * offset    = -((sway + swipe) / 2) * travel
         * ```
         *
         * `slackLeft` is a real distance, not a safety constant: [CELESTIAL_MARGIN_FRACTION]
         * (0.12) is larger than the doubled [CELESTIAL_RADIUS_FRACTION] (0.11), so the rest
         * position always leaves a computable gap to the left edge, and the body is allowed to
         * use exactly that gap. At the arc's extremes the gap is only 0.01 of screen width and
         * the body barely moves -- that is the geometry saying there is no room, not a clamp.
         *
         * Two inputs, combined as their mean and each entitled to the full `parallax *
         * screenWidth` an unbounded parallax would ask for, hence the factor 2 in `travel`:
         *
         *  - [swipeOffset] is already bounded to `0..1` by the `onOffsetsChanged` contract, so it
         *    is used directly and linearly. Below `celestialX ~ 0.38` (at parallaxStrength 1) the
         *    slack runs out first and the response tapers; above it, a full swipe moves the body
         *    exactly as far as it does today.
         *  - [driftAccum] is `continuousScrollAccum`, which grows without bound by design. Any
         *    bounded function of it is either saturating (the body would freeze) or periodic. A
         *    cosine of the background's *own* wrap phase is periodic and smooth: it is 0 with
         *    zero slope at phase 0 and again at phase 1, so it crosses the wrap seam with no step
         *    in position or velocity. The body stays tied to the star field's cycle without
         *    inheriting its sawtooth.
         *
         * `swipeOffset` is coerced into `0..1` because the invariant this function exists to
         * guarantee -- the disc never leaves the viewport -- must hold for whatever a launcher
         * actually reports, not only for what the contract promises.
         *
         * Allocation-free: two `min`/`coerce` calls, one `cos`, all on primitives.
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

    // Deterministic per-layer "noise" seed so the silhouette shape is stable across frames
    // but different per layer/theme.
    private fun layerSeed(layer: Int): Long = (theme.id.hashCode().toLong() * 31 + layer)

    fun onSizeChanged(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        regenerateStars()
    }

    private fun regenerateStars() {
        val rnd = Random(42)
        val count = (70 * sceneCustomization.stars.density.coerceIn(0f, 1f)).toInt()
        stars = List(count) { index ->
            Star(
                x = rnd.nextFloat() * screenWidth,
                y = rnd.nextFloat() * screenHeight * 0.55f,
                // Bumped from 1-2.8px (batch 4 part 3 -- see drawStars' own doc comment: too
                // small for the new sparkle sprite to read as anything but a blur).
                radius = 2.4f + rnd.nextFloat() * 3.2f,
                phase = rnd.nextFloat() * 6.28f,
                // Every fifth star is a sparkle; the rest are points. See [drawStars].
                sparkle = index % STAR_SPARKLE_EVERY == 0,
            )
        }
        cachedStarsDensity = sceneCustomization.stars.density
    }

    // ---- The weather this frame is actually in, evaluated once at the top of the frame ---------
    //
    // Three draw paths now need the same two answers -- the rain's own drawing, the umbrella in a
    // pedestrian's hand and the waves on the lake -- and each of them reading the customization
    // and the live override for itself is how they would come to disagree. "Raining" is exactly
    // the predicate `drawPrecipitation` paints rain on, which is the point: **snow is not rain**,
    // so nobody carries an umbrella in the snow and no wave crosses a frozen lake.
    private var rainingNow = false
    private var rainIntensityNow = 0f
    /** The storm gate the lightning already uses, so the two cannot disagree about a thunderstorm. */
    private var stormActiveNow = false

    private fun updateWeatherPredicates(deltaSeconds: Float) {
        val live = liveWeatherOverride
        val precip = sceneCustomization.precipitation
        // **The cover the scene draws, which is not always the cover the forecast reports.**
        //
        // Computed here, once, at the top of the frame, because two layers read it and they are
        // drawn in the other order: [drawSky] dims by [stormStrength] before [drawClouds] places
        // anything, so a cover eased inside `drawClouds` would leave the sky a frame behind the
        // clouds it is meant to be darkening for. See [CloudCoverFade] for the report.
        drawnCloudCover = cloudCoverFade.coverToward(live?.cloudCoverFraction, deltaSeconds)
        if (live != null) {
            rainingNow = live.precipitationType == PrecipitationType.RAIN && live.precipitationIntensity > 0f
            rainIntensityNow = if (rainingNow) live.precipitationIntensity else 0f
        } else {
            rainingNow = precip.visible && precip.intensity > 0f && precip.type == PrecipitationType.RAIN
            rainIntensityNow = if (rainingNow) precip.intensity else 0f
        }
        stormActiveNow = LiveWeatherSceneRules.stormActive(
            liveIsThunderstorm = live?.isThunderstorm,
            themePrecipitationVisible = precip.visible,
            themePrecipitationIsRain = precip.type == PrecipitationType.RAIN,
            themeThunderstorm = precip.thunderstorm,
        )
    }

    // ---- The waves (v4.28) --------------------------------------------------------------------
    /** Which wave slots are on the water. A slot only changes while it is off screen. */
    private val waveActive = BooleanArray(WAVE_POOL)
    /** This frame's two derived tints, computed once in [gatherWaves] and used by [drawWaveItem]. */
    private var waveBodyTint = 0
    private var waveFoamTint = 0

    /**
     * Places this frame's waves into the lake's own item slots, so they are painted in the same
     * far-to-near pass as the boats and the dolphins.
     *
     * ### Why they are not simply drawn before the boats
     *
     * That is what the phase-2 proposal did, and it made **every** wave sit behind **every** boat
     * however the two were placed: a breaker crossing the near edge of the water cut off behind a
     * hull that was plainly further away. It is the sail-and-dolphin defect of v3.1 in a new pair,
     * and [LakeLanes] already holds its answer -- one pass, one key, sorted by base.
     *
     * ### The key, and why it is not the wave's own base
     *
     * The three categories do not share a reference point. A sailboat's key is its *placement*
     * point and `drawSailboat` hangs the hull [SAILBOAT_HULL_WATERLINE_UNITS] below it, so the boat
     * meets the water 25 boat units under its own key. A dolphin's key is its lane. A wave's base
     * *is* its waterline. Keyed by its bare base the wave was compared against a boat's placement
     * point rather than against the boat's hull, and the first burst of phase-3 frames showed
     * exactly that: a wave cutting the sail of a boat whose hull was plainly nearer. The wave's key
     * is therefore its base **lifted by the boat's own hull offset**, so wave and hull are compared
     * waterline to waterline.
     *
     * That keeps all three properties `LakeLanesTest` fixes: boats are untouched, nothing is ever
     * pulled *forward* (the lift is never negative, so a wave only ever moves back), and one key
     * still orders everything. Against a dolphin the comparison is off by the difference between
     * the boat's convention and the dolphin's -- about 16 px on the reference device, item 82 of
     * `BACKLOG_v4_28.md`; the sail is the large thing a wave can be seen to cut, which is why it
     * is the boat's convention that was adopted here.
     */
    private fun gatherWaves(from: Int, top: Float, bandHeight: Float, dayBlend: Float, elapsedSeconds: SceneTime): Int {
        var count = from
        // A thunderstorm is a full sea; plain rain scales the pool by how hard it is raining.
        val waveDensity = if (stormActiveNow) 1f else rainIntensityNow
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
        val waveHullLift = SAILBOAT_HULL_WATERLINE_UNITS * SceneSpace.SAILBOAT_BASE_SCALE *
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
            lakeItemDepth[count] = LakeLanes.depthOf(laneY = waveY, heightAboveLane = waveHullLift)
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

    fun draw(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime, deltaSeconds: Float) {
        updateWeatherPredicates(deltaSeconds)
        // One direction only, per explicit request -- full screen-width drift every ~25s at
        // scrollSpeed=1.0;
        // scrollSpeed=0 freezes it. Safe to let this grow unbounded now that hills and objects
        // share one wrap period (see scrollProgress's own doc comment) -- no bound needed here
        // the way the old oscillating version required one.
        continuousScrollAccum += deltaSeconds * scrollSpeed * 0.04

        // NOT wrapped/reset periodically -- an earlier version of this line did `%= 2f` as a
        // supposed "long-uptime float-precision safety net", reasoning that every consumer
        // computes its own `% tileWidth` downstream anyway so it would be visually identical.
        // That reasoning was wrong: it's only seamless for a layer whose *own* parallax factor
        // happens to equal exactly 1.0, since only then does `scrollProgress`'s own wrap period
        // (2.0 units) correspond to that layer's actual `tileWidth` in pixels. Every other layer
        // -- hills at 0.15/0.35/0.6, mountains/clouds at 0.04-0.08, the lake at 0.25, sky/stars at
        // parallaxFactors[0] -- has a *different* effective wrap period in pixels, so forcibly
        // resetting the shared accumulator made every one of them jump by a different, nonzero
        // amount at the exact same instant: a visible, synchronized "the whole scene just reset"
        // glitch every ~time it takes scrollProgress to reach 2.0 (well within a few minutes at
        // typical scroll speeds) -- exactly the reported bug. Each layer's own `% tileWidth`
        // already wraps correctly and seamlessly no matter how large the raw accumulator gets, so
        // it doesn't need an artificial reset here at all. [continuousScrollAccum] being a Double
        // (see its own doc comment) is what actually keeps that true across realistic uptimes --
        // not a periodic reset, which this same reasoning already ruled out as strictly worse.
        if (stars.isEmpty() || cachedStarsDensity != sceneCustomization.stars.density) regenerateStars()
        syncObjectRendererWithTheme()
        rebuildHillPathsIfNeeded()
        drawSky(canvas, dayPhase)
        // Sky/sun/moon/stars are the farthest, most "distant" elements in the scene, so when the
        // user opts into scrolling them at all (off by default -- most live wallpapers keep the
        // sky fixed and only scroll the nearer terrain/objects), they use the same gentle rate as
        // the farthest hill layer rather than a separate tunable, since visually they should
        // read as even farther away than that layer, not competing with it for depth.
        if (scrollBackground) {
            // scrollProgress grows unbounded by design (see continuousScrollAccum's own doc
            // comment) -- every other layer in this file wraps its own shift with `% tileWidth`
            // before using it, but this one didn't, so a raw, ever-growing bgShift eventually
            // pushed the sun/moon/stars permanently off-screen after enough uptime and never
            // brought them back (the reported "moon and sun no longer visible during infinite
            // scroll" bug). Wrapping it the same way as everything else fixed the *permanent*
            // loss, but it was only half the fix: a wrap is seamless only for a layer that is
            // also *tiled*, and this one drew a single copy of everything. So the wrap turned a
            // permanent disappearance into a periodic one -- the sun slid off the left edge, the
            // sky went starless from the right, and both snapped back at the seam once per wrap
            // period (roughly every 18 minutes of visible uptime at default settings). The two
            // things in this layer have opposite tiling natures, and now get opposite treatments.
            val bgTileWidth = screenWidth.toFloat()
            val bgParallax = parallaxFactors[0].toDouble() * parallaxStrength
            val bgShift = wrappedScrollShift(bgParallax, bgTileWidth)

            // Stars: a genuinely tiled pattern (regenerateStars lays them out across exactly one
            // screen width), so they get the tile range their own geometry implies -- two copies
            // normally, three only when a sprite's own extent pokes across a seam. Neighbouring
            // copies never draw the same star twice in the same place, so there is nothing to
            // read as a repeat; the field simply stops having a hole in it.
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

            // Sun/moon: a single object, so neither a wrap nor a tiling is correct for it -- a
            // bounded offset derived from the slack its own rest position leaves to the left
            // screen edge. See [celestialParallaxOffset].
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
        // Behind the mountains/hills on purpose (see drawRainbow's own doc comment) -- drawn
        // right after clouds, before anything that should occlude its base.
        drawRainbow(canvas, dayPhase)
        drawMountains(canvas, dayPhase)
        drawBirds(canvas, dayPhase, elapsedSeconds)
        // Lake drawn *before* hills now, which is simply the scene's depth order
        // (mountains, farthest -> water -> hills/ground, nearest). Hills (and everything
        // standing on them, drawn right after) now naturally paint over whatever part of the
        // water their own wavy silhouette covers in a given column, which is what makes the
        // water read as sitting *behind* the hills instead of a flat rectangle slicing across
        // them (the previously reported "hills look cut" bug -- the old order drew the lake
        // *after* hills, so the lake's flat edge cut across the hills' organic one instead of
        // the other way around).
        drawLake(canvas, dayPhase, elapsedSeconds)
        drawHillLayers(canvas, dayPhase)
        // The blend and the hour both come from the frame's own dayPhase: the blend drives the
        // dusk crossfade of the car count, the hour drives the business hours, and neither may be
        // re-derived from a clock of its own -- fixedHour must move them exactly as it moves the sun.
        objectRenderer.update(deltaSeconds, dayPhase.dayBlend)
        objectRenderer.rainingNow = rainingNow   // v4.28: who has an umbrella up
        objectRenderer.draw(canvas, objectGroundGeometry, dayPhase.dayBlend, elapsedSeconds, screenWidth.toFloat(), screenHeight.toFloat(), dayPhase.hour24)

        val fireworksEnabled = theme.hasFireworks && dayPhase.dayBlend < 0.35f
        fireworkEffect.update(deltaSeconds, fireworksEnabled, screenWidth.toFloat(), screenHeight.toFloat())
        fireworkEffect.draw { x, y, burstScale, alpha ->
            // `firework` is 240x240 with a SPRITE_CENTRE anchor -- 80x80 local units, so the
            // origin is -40 on both axes and the sprite's own centre lands on the burst point.
            // [FIREWORK_REACH_UNITS] over that half-width is the scale at which a fully expanded
            // burst reaches the radius the old particle spray did.
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

        santaSleighEffect.update(deltaSeconds, sceneCustomization.santaEnabled, screenWidth.toFloat(), screenHeight.toFloat())
        santaSleighEffect.draw(canvas, elapsedSeconds, screenWidth.toFloat()) { x, y, dir, alpha ->
            // The sleigh was a 1563x434 raw-pixel sprite reduced by a historical 130/680 divisor
            // and anchored at (-283,+244) -- an origin inherited from a 2040x840 canvas that was
            // 60 % transparent. The V2 redraw put it on the authoring grid, which makes it a
            // SCENE_UNITS sprite and retires both numbers. **The manifest's SCENE_UNITS is right
            // and the shipped call site's CANVAS_PIXELS was the stale half**; the manifest's
            // declared anchor is taken as given here.
            //
            // The canvas this paragraph used to quote -- 624x168, content box 598px wide -- is two
            // canvases out of date: `santa_sleigh_scene` is 594x123 px since v4.19's crop. See
            // [SANTA_SLEIGH_SCALE] for the current geometry and for why the X origin is
            // deliberately not the content's centre.
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(dir * SANTA_SLEIGH_SCALE, SANTA_SLEIGH_SCALE)
            // Two frames, alternating on the same clock the walking people use. The team stopped
            // moving its legs when the whole group became one sprite: a single bitmap cannot
            // bend, so the trot has to be a second drawing rather than a transform. The two
            // reindeer are drawn in opposite leg phases within each frame, so the pair never
            // steps in unison.
            val trotting = elapsedSeconds.frameIndex(SANTA_TROT_FRAMES_PER_SECOND, 0f, 2) == 1
            sprites.draw(
                canvas,
                if (trotting) R.drawable.santa_sleigh_trot else R.drawable.santa_sleigh_scene,
                SANTA_SLEIGH_ORIGIN_X_UNITS, SANTA_SLEIGH_ORIGIN_Y_UNITS,
                SpriteScale.SCENE_UNITS,
                (alpha * 255).toInt().coerceIn(0, 255),
            )
            canvas.restore()
        }

        // Precipitation and its lightning flash are the closest things in the whole scene --
        // real rain/snow reads as being right in front of the "camera", in front of even houses
        // and cars, so these are drawn dead last, on top of everything above.
        drawPrecipitation(canvas, dayPhase, elapsedSeconds)
        // Same "closest layer" placement as precipitation, right after it -- falling leaves are
        // just as much a foreground weather-like effect as rain/snow, so they get the same
        // "drawn dead last, in front of everything" treatment. Gated on fallColorsEnabled, not
        // its own separate toggle -- Fall Colors is one feature (autumn-toned canopies + leaves
        // drifting off them), matching how Rain/Snow's own falling-particle effect isn't a
        // separate toggle from the rain/snow color pair either.
        drawFallingLeaves(canvas, dayPhase, elapsedSeconds)
        // Live Weather override applies here too -- a real thunderstorm should flash regardless of
        // the theme's own manual "Thunderstorm" toggle, same reasoning as drawPrecipitation's own
        // override. The precedence itself lives in LiveWeatherSceneRules alongside the cloud one,
        // because the three layers agreeing is the property worth testing, not any one of them.
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
     * How bad the weather looks right now, 0..1 -- see [StormAtmosphere].
     *
     * Read from the Live Weather snapshot only. With Live Weather off there is no forecast to
     * report, and the theme's own manual rain slider is a *scene setting* rather than a statement
     * about the weather, so it does not darken the sky: a user who turns rain on for a sunny theme
     * asked for rain on a sunny theme. One property read and a handful of multiplies, evaluated
     * once per frame rather than per drawn element.
     */
    private fun stormStrength(): Float {
        val live = liveWeatherOverride ?: return 0f
        return StormAtmosphere.strength(
            precipitationType = live.precipitationType,
            precipitationIntensity = live.precipitationIntensity,
            isThunderstorm = live.isThunderstorm,
            // The *drawn* cover, not the reported one: this is what darkens the sky and the
            // clouds, and a sky that stepped to the new weather while the clouds under it were
            // still easing into it would put the two layers a ramp apart. Falls back to the
            // reported value on the frame before [updateWeatherPredicates] has ever run.
            cloudCoverFraction = drawnCloudCover ?: live.cloudCoverFraction,
        )
    }

    private fun drawSky(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase) {
        val sky = sceneCustomization.sky
        // Blend night -> twilight -> day using dayBlend, with a twilight bump near the
        // terminator -- same shape as before, just driven by the 6 user-editable colors instead
        // of the old 4-array theme.sky*/skyDawn/skyDusk model. Only the bottom gets a dedicated
        // sunrise/sunset color (the near-horizon warm glow); the top blends day<->night directly,
        // since in reality the upper sky doesn't shift much across a sunrise/sunset.
        val twilightWeight = (1f - kotlin.math.abs(dayPhase.dayBlend * 2f - 1f)).coerceIn(0f, 1f)
        val top = blendColor(sky.colorNightHigh, sky.colorDayHigh, dayPhase.dayBlend.coerceIn(0f, 1f))

        val twilightBottomColor = if (dayPhase.progress < 0.5f) sky.colorSunriseLow else sky.colorSunsetLow
        val nightToTwilightBot = blendColor(sky.colorNightLow, twilightBottomColor, dayPhase.dayBlend.coerceIn(0f, 1f))
        val bottom = blendColor(nightToTwilightBot, sky.colorDayLow, (dayPhase.dayBlend - twilightWeight * 0.3f).coerceIn(0f, 1f))

        // The horror sky overrides the six user colours rather than editing them, so turning it
        // off gives the palette back exactly as it was. It keeps the day/night blend so the scene
        // still gets lighter and darker across a day -- a sky that never changed would stop the
        // sun and the moon meaning anything -- but it holds the whole range inside near-black
        // overhead and a hard orange at the horizon. Two flat bands and a gradient between them
        // is what the rest of the sky already is; nothing here is a new drawing technique.
        if (sceneCustomization.horrorSkyEnabled) {
            val lift = dayPhase.dayBlend.coerceIn(0f, 1f)
            val horrorTop = blendColor(HORROR_SKY_TOP_NIGHT, HORROR_SKY_TOP_DAY, lift)
            val horrorBottom = blendColor(HORROR_SKY_LOW_NIGHT, HORROR_SKY_LOW_DAY, lift)
            // **Recorded before the early return, not after it.** These two fields mean "the sky
            // this frame actually drew", and three things downstream read them: the water's mirror,
            // the struck waterline and the precipitation's derived colour. The horror branch used to
            // return without writing them, so with the horror sky on they held whatever a previous
            // frame left -- zero on the first frame, which is transparent black. A lake turned on
            // under a horror sky therefore mirrored a colour that was never computed. The flag is
            // independent of the theme (`DESIGN_NOTES.md` §16), so this is reachable in any theme,
            // not only Halloween.
            skyTopColorNow = horrorTop
            skyHorizonColorNow = horrorBottom
            canvas.drawVerticalGradientRect(
                0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), horrorTop, horrorBottom,
            )
            return
        }

        // The weather, applied to the finished day/night colours rather than instead of them: the
        // twilight bump, the sunrise/sunset band and the whole dayBlend above are computed exactly
        // as before and then weathered. That ordering is what keeps "18:30 + heavy rain" a dim
        // sunset instead of a night, and what stops a storm from becoming a palette swap.
        val storm = stormStrength()
        // The sky's own gradient, weathered: the water reflects the horizon end of it, and the
        // waterline has to be judged against the sky *immediately above the shore*, which is
        // neither end. Both are kept rather than recomputed from the theme, which would miss the
        // storm.
        skyTopColorNow = StormAtmosphere.dimSky(top, storm)
        skyHorizonColorNow = StormAtmosphere.dimSky(bottom, storm)
        canvas.drawVerticalGradientRect(
            0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(),
            StormAtmosphere.dimSky(top, storm),
            StormAtmosphere.dimSky(bottom, storm),
        )
    }

    /**
     * Sprite-blit conversion (batch 4 part 3): a star is a blitted texture -- a 4-pointed sparkle
     * for most, a plain small dot for the rest -- rather than a plain filled circle. Bumped the
     * radius
     * range too (was 1-2.8px, a barely-visible dot at any size -- too small for the sparkle
     * shape to read as anything but a blur) so the new shape actually shows.
     */
    private fun drawStars(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime) {
        if (!sceneCustomization.stars.visible) return
        val visibility = (1f - dayPhase.dayBlend * 1.6f).coerceIn(0f, 1f)
        if (visibility <= 0f) return
        // **Most stars are points; a few are sparkles.** Every star used to be the sparkle
        // sprite under its own save/translate/rotate/scale/blit/restore -- six canvas operations
        // each, seventy times a frame, for a field where sixty-odd of them are a couple of pixels
        // across and their rotation is invisible at that size. A point costs one drawCircle.
        //
        // The look is better for it, not merely cheaper: a real night sky is mostly points with a
        // few brighter stars in it, and seventy identical rotating sparkles read as a pattern.
        // The sparkles that remain are the largest ones, so what was legible before still is.
        for (star in stars) {
            val twinkle = 0.5f + 0.5f * elapsedSeconds.sinAt(1.5f, star.phase)
            val alpha = (255 * visibility * twinkle).toInt().coerceIn(0, 255)
            if (alpha <= 0) continue
            if (!star.sparkle) {
                starPointPaint.alpha = alpha
                canvas.drawCircle(star.x, star.y, star.radius * STAR_POINT_RADIUS_SCALE, starPointPaint)
                continue
            }
            canvas.save()
            canvas.translate(star.x, star.y)
            canvas.rotate(elapsedSeconds.cycleOf(12f, star.phase * 60f, 360f))
            val s = star.radius / STAR_SPRITE_RADIUS_DIVISOR
            canvas.scale(s, s)
            // star_sparkle.png is authored at the SPRITE_PIXELS_PER_UNIT oversample: 180px cover
            // 60 local units, so [STAR_SPRITE_ORIGIN_UNITS] centres it on the star and it reaches
            // [STAR_SPRITE_HALF_UNITS] / [STAR_SPRITE_RADIUS_DIVISOR] = 1.875 of the star's
            // radius. **It reached 0.9375 of it until v4.23, and that number is now wrong
            // wherever it survives**: the divisor halved and the artwork was redrawn for the
            // size that produces, so a sparkle is deliberately wider than the star it marks.
            // [STAR_SPRITE_LEFT_EXTENT_PX] carries the same change, because the star field is
            // tiled and the tile range is derived from this reach. It was blitted as CANVAS_PIXELS until
            // v73.7, which made it three times too large and hung it off the star's lower right,
            // because v72's 64px artwork -- which was a raw-pixel sprite, and correct as one --
            // was replaced with a 3x redraw in v73 without the call site following. **The V2
            // manifest declares this sprite CANVAS_PIXELS, which is that same defect written
            // down**; the call site is the source of truth here and the manifest was corrected to
            // agree with it, not the other way round.
            //
            // Untinted, like the sun and for the same reason: V2 declares the sparkle fixed art
            // and draws it in cream with a warmer core, so `theme.starColor` no longer reaches
            // it. The field stays on `SceneTheme` because custom themes persist it and dropping
            // it would break their JSON; it is simply no longer read here.
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
     * @param offsetX horizontal parallax offset in canvas pixels, `0f` when the background does
     *   not scroll. Applied here rather than by translating the canvas so that the body's own
     *   bound can be expressed against its rest position -- see [celestialParallaxOffset]. The
     *   default keeps the `scrollBackground = false` path arithmetically identical to what it
     *   was before the offset existed.
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
        val horizonY = screenHeight * 0.62f
        val riseHeight = screenHeight * sceneCustomization.sky.sunCloudHeight
            .coerceIn(SUN_CLOUD_HEIGHT_MIN, SUN_CLOUD_HEIGHT_MAX)
        val cy = horizonY - dayPhase.celestialY * riseHeight
        celestialShownNow = true
        celestialIsSunNow = isSun
        celestialCxNow = cx

        // doubled -- was too small to read clearly
        val radius = screenWidth * CELESTIAL_RADIUS_FRACTION * 2f
        val color = if (isSun) sceneCustomization.sun.color else sceneCustomization.moon.color

        // **Attenuation only.** cx, cy, the radius and the arc above are untouched by the weather;
        // the sun keeps its position and its role in the day/night blend, and a storm only makes it
        // fainter. The moon is left alone: a rainy night is already dark, and dimming the moon as
        // well would take the last light out of the scene.
        val sunStorm = if (isSun) stormStrength() else 0f
        canvas.drawRadialGlow(
            cx, cy, radius * 3.2f, color,
            StormAtmosphere.sunAlpha(CELESTIAL_GLOW_CENTRE_ALPHA, sunStorm),
        )

        if (isSun) {
            val sunAlpha = StormAtmosphere.sunAlpha(255, sunStorm)
            canvas.save()
            canvas.translate(cx, cy)
            val s = radius / 120f
            canvas.scale(s, s)
            // Aesthetic-pass batch 4 addition, simplified in batch 5: an 8-ray sunburst behind
            // the disc. Originally also had 2 translucent concentric rings here, but on-device
            // testing showed they could read as a second, separate pale disc next to the sun
            // (mistaken for a moon) rather than a soft glow -- removed, the existing
            // RadialGradient glow above already provides the ambient falloff on its own.
            //
            // sun_glow.png is 396x396 and its ring sits 154..166px from its own centre, so as a
            // raw-pixel sprite it covers 396 local units and has to be anchored at -396/2 for
            // that ring to land at 154..166 units -- outside the disc's own 120, which is what
            // makes it read as a sunburst. **The ring was a band of rays at 111..190 until
            // v4.23's redraw**, which replaced them with a compass-struck ring at full opacity
            // over a soft warm halo reaching 197; the number that carries the argument is that
            // the ring starts past 120, and it still does. It was 444x444 with 24px of
            // transparent margin per side until the padding normalisation: the ring is measured
            // from the sprite's own centre, so removing a symmetric margin left it exactly where
            // it was.
            // It was anchored at -74 until v73.7: that is -(444/2)/SPRITE_PIXELS_PER_UNIT, the
            // origin an oversampled sprite would want, and it hung the rays off the disc's lower
            // right. The oversampled reading is not the alternative it looks like: it would put
            // the ring at 51..55 units, entirely hidden behind the disc.
            // Both are fixed art in the V2 asset set -- a two-tone orange disc and a yellow ray
            // ring with its own falloff -- so neither takes the user's sun colour any more.
            // Multiplying finished art by a chosen colour compounds two hues instead of
            // recolouring a mask, and for anything but a warm pick it turns the sun near-black.
            // `Sun Color` still drives the ambient glow above, which is the part of the sun that
            // is still a tintable effect. Recorded in `DESIGN_NOTES.md` as an intended
            // consequence of the redesign, not a regression to work around here.
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
     * Sprite-blit conversion (batch 4 part 3): the moon is one of 8 hand-drawn phase silhouettes
     * (new, crescent, half, gibbous, full -- the waning half reusing the waxing shapes rotated
     * 180°) rather than an ellipse-width approximation computed at runtime. Replaces the old
     * "half-disc + variable-width
     * terminator ellipse" geometric technique with 4 baked shapes (crescent/half/gibbous/full)
     * reused the same way for the waning side via a 180° rotation. Thresholds on `illuminated`
     * (already computed below, unchanged from the old technique) pick between the buckets.
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

        // Halloween replaces the disc outright, phases and all. A carved face that waxed and
        // waned would be a lit fraction of a grin, which reads as a rendering fault rather than as
        // a decoration -- and the phase sprites are a fixed set of four silhouettes, so there is
        // no "jack-o'-lantern crescent" to reach for. One sprite, always full, while the flag is
        // on.
        if (sceneCustomization.halloweenEnabled) {
            canvas.save()
            canvas.translate(cx, cy)
            canvas.scale(s, s)
            sprites.drawTinted(
                canvas,
                R.drawable.moon_jack_o_lantern,
                CELESTIAL_DISC_ORIGIN_UNITS,
                CELESTIAL_DISC_ORIGIN_UNITS,
                CELESTIAL_DISC_SCALE,
                HALLOWEEN_MOON_COLOUR,
            )
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

        // **[phase] is an argument and must stay one.** It was `SunPositionCalculator.moonPhase()`
        // here until v5.4G -- a read of `System.currentTimeMillis()` in the middle of painting a
        // frame whose hour, theme, customisation and scene clock were all pinned by the caller.
        // Everything above this line is a function of the renderer's inputs; this line was not,
        // and it is why `night` and `shops-closed-night` passed at 19:02 and failed at 22:25 on
        // the same build. The buckets below are thresholds, so the leak is invisible for days at
        // a time and then moves 17 pixels the moment the real moon crosses one.
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
        // 180° reuse for the waning half: third quarter, waning crescent and waning gibbous are
        // the waxing shapes mirrored, so there is no second set of art to draw or to keep in
        // step with the first.
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
     * Two independent background silhouette layers, drawn behind the hills with their own
     * (much slower than any hill layer) parallax rate. Deliberately kept entirely separate from
     * the hill/object row-placement system ([SceneSpace.groundYFraction]) --
     * these are simple, non-interactive backdrop shapes with no placement-safety concerns of
     * their own, so there was no reason to risk touching that already-tuned geometry to add them.
     */
    /**
     * An ambient flock of birds crossing the sky -- independent of the hill/object
     * row-placement system (birds fly, they aren't anchored to any terrain row), with their own
     * gentle drift and wing-flap animation. Each bird's color is a stable weighted-random pick
     * from [BirdsConfig.colors] (see [BirdsConfig.pickColor]), not re-rolled every frame.
     */
    /**
     * Sprite-blit conversion (batch 4 part 3): a bird is a blitted texture whose wing flap is a
     * mirror flip of `sy`'s sign every few frames rather than a continuously-bent curve --
     * replaced the old
     * per-frame quad-bezier wing path with a single baked "wings up" sprite, vertically flipped
     * for the "wings down" half of the flap cycle via the same sign-flip trick the reference
     * itself uses, instead of a separate second frame.
     */

    private fun drawBirds(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime) {
        val birds = sceneCustomization.birds
        if (!birds.visible) return
        // Present while the sun is up, gone after dark unless the user wants night birds. See
        // [BirdsConfig.presenceAt] for why this is no longer `dayBlend` itself.
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

            // **One candidate is one bird.** v76 read the asset package's note that `bird_body`
            // had stopped being a three-bird strip as an instruction to place it three times, and
            // drew a flock at a third of the size. The shipped 420x65 sprite was never three
            // birds: it was one wide gull, and the historical `15f / 70f` divisor brought its
            // 420 px down to a 90 px wingspan on screen. The V2 bird is 90 px wide, so it is
            // blitted at its own size and reaches exactly the wingspan the old one did.
            //
            // The origin centres the sprite on the flip axis, because the wing-flap is a vertical
            // mirror and mirroring about anything but the bird's own centre makes it hop.
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

    /**
     * Puffy clouds drifting slowly across the upper sky. Same independent-candidate-pool
     * approach as [drawMountains]/[drawBirds] (own parallax, own density filter, no interaction
     * with the hill/object row-placement system) -- clouds float, they aren't anchored to
     * anything below them either.
     *
     * Two reported bugs fixed here: clouds visibly turning gray/dark as density increased, and
     * clouds never fully covering the sky even at 100% density.
     *
     * The first is a category error, and the rule that settles it is one sentence: **a cloud's
     * colour is the theme's day/night pair, and how many clouds there are is not what colour they
     * are.** The "darken toward black as density climbs" behaviour this used to have was meant to
     * read as a storm; a storm is weather, and weather now has its own path through
     * [StormAtmosphere]. Density is left as a flat colour at any value.
     *
     * The second is about shape, not count. A full sky is a handful of large lobed masses
     * overlapping into one continuous band, not many small separate puffs across several rows --
     * an earlier attempt went the other way (36 small candidates across 3 stacked rows) and read
     * as a texture rather than as cloud. Fewer, larger candidates in a single row close the gaps
     * at high density because each one is bigger and overlaps its neighbours more, not because
     * there are more of them.
     */
    /**
     * Cloud placement: a count that scales with the density setting, and four depth tiers assigned
     * in rotation by index rather than one shared depth for all of them.
     *
     * Count used to be capped well below the maximum for a concrete, learned-the-hard-way reason:
     * each cloud was a hand-built path via 4 `Path.op(..., UNION)` boolean operations plus a clip
     * and an outline stroke, all real per-frame `Canvas` cost, and dozens of path booleans a frame
     * is not something this renderer can afford. Batch 4 part 2 converted [drawPuffyCloud] to a
     * single tinted sprite blit (see its own doc comment) -- the same category of cost the
     * sprite-blit pilot eliminated for houses/trees/buildings/cars -- so that constraint no longer
     * applies and the count now goes right up to its maximum.
     *
     * The depth-tier *variety* is the part that matters visually and is kept: 4 tiers below, each
     * with its own parallax, size and vertical offset.
     */
    /**
     * Live Weather override: only blends into the *density* when the theme's own Clouds toggle
     * is already on -- unlike precipitation (a much stronger "is it raining or not" weather
     * signal), whether a given theme shows clouds *at all* is treated as an artistic per-theme
     * decision aa is free to keep off (e.g. a deliberately clear desert theme), so Live Weather
     * only adjusts how many clouds show once that decision has already opted in, not whether any
     * appear.
     */
    /** See [CloudBand], which owns this arithmetic now that three layers depend on it agreeing. */
    private fun cloudBandTopFor(screenHeight: Int, sunCloudHeight: Float): Float =
        CloudBand.topFor(screenHeight, sunCloudHeight)

    private fun cloudBandHeightFor(screenHeight: Int): Float = CloudBand.heightFor(screenHeight)

    private fun drawClouds(
        canvas: SceneCanvas,
        dayPhase: SunPositionCalculator.DayPhase,
        elapsedSeconds: SceneTime,
        deltaSeconds: Float,
    ) {
        val clouds = sceneCustomization.clouds
        cloudCoverage.beginFrame()
        // **The override is consulted before the theme's own switch, exactly as [drawPrecipitation]
        // does.** Until v2.14 this early return came first, so with the cloud layer switched off
        // Live Weather's cloud cover was discarded while Live Weather's rain was still drawn from
        // the same snapshot -- the asymmetry that produced "rain falling from a cloudless sky".
        // Measured on a clean emulator: `clouds.visible=false clouds.density=0.4
        // override.cloudCover=1.0 -> drawn=false` while precipitation drew. The settings screen
        // promises that real conditions replace the theme's manual cloud setting; this is what
        // makes that true for both layers rather than one.
        val density = LiveWeatherSceneRules.cloudDensity(
            // The eased cover, not the reported one -- [CloudCoverFade], and note it is the *only*
            // argument that changes here: which clouds a given density admits, and where each one
            // sits, are untouched.
            liveCloudCover = drawnCloudCover,
            themeCloudsVisible = clouds.visible,
            themeCloudDensity = clouds.density,
        )
        // A cover easing down to nothing reaches zero before the last clouds have finished fading,
        // so "no clouds to place" is not yet "no clouds on screen": leaving early here would cut
        // the fade off at its last step, which is the one frame this whole class exists to remove.
        if (density == null && !cloudCoverFade.anythingVisible()) {
            // No clouds to place, either because the layer is off or because the forecast reports a
            // clear sky. Turning the cloud layer off must not also turn precipitation off, so with
            // no clouds to derive a field from the sky is treated as uniformly covered and
            // intensity governs alone -- exactly as it did before coverage existed.
            cloudCoverage.setUniform()
            return
        }
        // Same treatment as the sky and from the same strength, with heavier amounts: under a
        // storm the cloud layer should be the darkest thing above the horizon. Still the theme's
        // own colour pair, blended by day phase first -- no second cloud system, no new palette.
        cloudPaint.color = StormAtmosphere.dimCloud(
            blendColor(clouds.colorNight, clouds.colorDay, dayPhase.dayBlend),
            stormStrength(),
        )
        cloudPaint.alpha = 255

        // aa reported clouds too small and, even at 100% density, not actually covering the sky.
        // A full sky is ~41 heavily-overlapping clouds spread evenly across the *whole* width,
        // overlapping enough to form a solid blanket. The radius (68f*scale) was already right;
        // the count now goes to 41 too (was capped at 36) now that
        // [drawPuffyCloud] is a cheap sprite blit instead of 4 per-frame Path.op booleans -- see
        // this function's own doc comment above for why that cap no longer needs to exist.
        // Fixed pool: density now selects from a constant set of slots instead of also deciding
        // how many slots exist. When the count moved with the slider, every cloud's
        // `(i + 0.5) / candidateCount` position moved with it, so adjusting density relocated the
        // whole sky rather than thinning it.
        // Zero when the layer has just been told to place nothing and is only finishing its fade;
        // `isPresent` already answers false for every candidate at zero, which is precisely the
        // "everything is on its way out" the frames after the early return above describe.
        val placementDensity = density ?: 0f
        val effectOffset = CandidateThreshold.offsetFor(EffectId.CLOUDS)
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(placementDensity, CLOUD_POOL_SIZE, effectOffset)
        val seed = seedFor(EffectId.CLOUDS)
        val tileWidth = screenWidth * 2f

        val bandTop = cloudBandTopFor(screenHeight, sceneCustomization.sky.sunCloudHeight)
        val bandHeight = cloudBandHeightFor(screenHeight)

        for (i in 0 until CLOUD_POOL_SIZE) {
            // **Every candidate is asked every frame, present or not.** The opacity of the ones on
            // their way out has to keep advancing, and a `continue` before the question would
            // freeze a half-faded cloud on screen forever. The 41 calls cost an array read and a
            // clamp each; the loop already walked all 41 to ask `isPresent`.
            val present = CandidateThreshold.isPresent(i, placementDensity, effectOffset, fallbackIndex)
            val opacity = cloudCoverFade.opacityOf(i, present, deltaSeconds)
            if (opacity <= 0f) continue
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

            for (tileOffset in -1..1) {
                var x = baseX + tileOffset * tileWidth
                // Fold the extra drift-based wrap back into a single tileWidth period too.
                x %= tileWidth * 2f
                if (x > tileWidth) x -= tileWidth * 2f
                // Margin widened from 120f to 160f to match the bigger r=68f base radius (was
                // 45f) -- otherwise clouds crossing the screen edge get culled before their
                // outermost lobe (up to ~2.1r from center) finishes drawing.
                if (x < -160f * scale || x > screenWidth + 160f * scale) continue
                drawPuffyCloud(canvas, x, laneY, scale, cloudAlpha)
                // **A cloud that is fading contributes its whole geometry to the rain field, not
                // its opacity.** Weighting it would put a second, softer ease inside the rain
                // and buy nothing the eye can see: the coverage field only decides *where* rain
                // may fall, the forecast's own intensity decides how much, and a cloud in flight
                // is on screen for 1.5 s. Leaving it unweighted is also what keeps a settled
                // frame's field bit-identical to the one that shipped.
                val cloudHalfWidth = CloudCoverage.CLOUD_CONTENT_HALF_UNITS * scale
                cloudCoverage.addCloud(
                    centerX = x,
                    coreHalfWidth = cloudHalfWidth,
                    spreadHalfWidth = cloudHalfWidth * CloudCoverage.RAIN_SPREAD_FACTOR,
                    screenWidth = screenWidth.toFloat(),
                )
            }
        }
    }

    /**
     * Sprite-blit conversion (batch 4 part 2) -- replaces the old per-frame `Path.op(UNION)` of
     * 5 primitives (+ a clip, a translated shadow fill, and a stroke) with a single tinted
     * bitmap blit. This one is a genuine architectural match to the reference too (its own
     * `Cloud` class blits a real texture, see this delivery's own CHANGELOG entry), *and* the
     * single biggest per-frame cost this file had among the still-vector-drawn categories --
     * up to 36 candidates/frame each doing 4 boolean path operations was exactly the kind of
     * cost the sprite-blit pilot was meant to eliminate. Mottling, the soft under-shading, and
     * the thin darker rim (previously a runtime clip+shadow+stroke sequence) are now baked into
     * `cloud_body.png` at generation time instead (`gen_cloud_sprite.py`, kept in chat, not
     * committed) -- same "bake it into the sprite" convention batches 1-3 established.
     */
    private fun drawPuffyCloud(canvas: SceneCanvas, cx: Float, cy: Float, scale: Float, alpha: Int) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        // [alpha] is how far through its fade this candidate is -- 255 for every cloud in a settled
        // sky, which is every cloud of every frame until a forecast actually changes. The blitter
        // has always taken the argument; see [CloudCoverFade].
        sprites.drawTinted(canvas, R.drawable.cloud_body, CLOUD_BLIT_X, CLOUD_BLIT_Y, SpriteScale.SCENE_UNITS, cloudPaint.color, alpha)
        canvas.restore()
    }

    /**
     * A decorative paper-cutout rainbow arc, 7 concentric stroked bands. Anchored to the exact
     * same base-Y fraction [drawMountains] derives its own base from
     * ([SceneSpace.GROUND_SOLID_TOP_Y_FRACTION]) so it visually "grows" out of the
     * same horizon band mountains sit on, then is drawn *before* mountains/hills in [draw]'s call
     * order so their silhouettes naturally occlude the rainbow's base -- exactly like a real
     * rainbow appears to rise from behind distant terrain rather than floating in front of it.
     *
     * **Now a sprite.** It was seven stroked `drawArc` bands plus seven highlight arcs, with two
     * `RectF`s allocated per band per frame, and the reason given for keeping it procedural was
     * that its size is derived from `screenWidth` rather than fixed in sprite units, so a
     * fixed-resolution PNG would need its own dynamic-scale path. That path is three lines --
     * a `save`/`scale`/`restore` around the blit -- and the V2 asset set supplies `rainbow_arc`
     * as a five-band arc whose base sits on its own bottom edge. The bands were hardcoded
     * constants here, so nothing user-facing moves into the artwork; what leaves the frame loop
     * is 14 arc strokes and 14 `RectF` allocations.
     */
    private fun drawRainbow(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase) {
        val rainbow = sceneCustomization.rainbow
        if (!rainbow.visible) return
        // Rainbows are a daylight phenomenon -- fade out toward night the same way stars fade in
        // toward night, rather than a hard on/off cut.
        val visibility = (dayPhase.dayBlend * 1.4f).coerceIn(0f, 1f) * rainbow.opacity.coerceIn(0f, 1f)
        if (visibility <= 0f) return

        val baseYFraction = SceneSpace.GROUND_SOLID_TOP_Y_FRACTION
        val baseY = screenHeight * baseYFraction
        val cx = screenWidth * 0.5f
        // The arc's outer radius, unchanged: the geometry is the same, only what fills it moved
        // from stroked bands to artwork. A half-width of 100 units maps onto `maxRadius`, and it
        // stays 100 even though the sprite is now 594x297 -- see the constant.
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
     * Falling rain or snow, the closest thing in the whole scene (see [draw]'s call order --
     * this is drawn dead last). Uses the same stateless deterministic-candidate approach as
     * [drawBirds]/[drawClouds] (no per-drop state to manage between frames): each candidate's
     * fall position is purely a function of [elapsedSeconds], wrapping smoothly from top to
     * bottom, so drops never need to be spawned/removed from a live list.
     *
     * aa reported drops/flakes reading as falling "from above" rather than out of the clouds.
     * Two things fix that, and both are about where a drop begins rather than how it falls: a
     * drop resets to the clouds' own anchor line, not to some point above them, and its alpha
     * fades in over the first 10% of the fall and out over the last 10% rather than popping in
     * and out at full opacity. This file's own
     * `fallStartY` used to sit at the clouds' band *top edge* (above the puffy bodies, which
     * visually center lower, at bandTop+bandHeight*0.5) with zero fade, which is exactly what
     * read as "falling from empty sky" instead of "emerging from the cloud layer" -- moved the
     * origin down to that same band-center line [drawClouds] itself renders clouds around, and
     * added the matching fade-in/out.
     *
     * Live Weather override: when active ([liveWeatherOverride] non-null), real conditions fully
     * drive whether precipitation shows at all, which type, and how intense -- the theme's own
     * manual Rain/Snow toggle and intensity slider are not consulted at all while it's active.
     * The theme's own rain/snow color pairs are still used, though.
     *
     * Sized in scene metres (v4.5): every length here is declared as a real size and converted
     * by [SceneSpace.pixelsPerMetre], the same route a house or a car takes. v4.4 put the effect
     * on the viewport scale but left it expressed in pixels, and the magnitude that hid inside
     * that expression made a raindrop as long as a pedestrian -- see [RAIN_LENGTH_MAX_METRES].
     */
    private fun drawPrecipitation(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime) {
        val precip = sceneCustomization.precipitation
        val liveOverride = liveWeatherOverride
        val isRain: Boolean
        val intensity: Float
        if (liveOverride != null) {
            val liveType = liveOverride.precipitationType ?: return
            isRain = liveType == PrecipitationType.RAIN
            intensity = liveOverride.precipitationIntensity
        } else {
            if (!precip.visible || precip.intensity <= 0f) return
            isRain = precip.type == PrecipitationType.RAIN
            intensity = precip.intensity
        }
        // The theme's own colour, which is what the user picked and what the correction below
        // starts from. It is never replaced -- only carried far enough from the sky's brightness
        // to be seen, and no further. See [PRECIPITATION_MIN_LUMA_GAP].
        val themeColour = if (isRain) {
            blendColor(precip.rainColorNight, precip.rainColorDay, dayPhase.dayBlend)
        } else {
            blendColor(precip.snowColorNight, precip.snowColorDay, dayPhase.dayBlend)
        }
        val baseAlpha = if (isRain) RAIN_ALPHA else SNOW_ALPHA
        // The gap is stated on the stroke as it is composited, and luma is linear in RGB, so the
        // separation the *colour* has to carry is the gap divided by the alpha it is drawn at. The
        // fade at the two ends of the fall is deliberately not part of this: a drop is meant to be
        // faint as it leaves the cloud and as it lands, and dividing by a fading alpha would drive
        // the colour to white or black exactly where it is supposed to disappear.
        val neededColourGap = PRECIPITATION_MIN_LUMA_GAP * 255f / baseAlpha
        val dropLuma = rec601Luma(themeColour)

        val effectOffset = CandidateThreshold.offsetFor(EffectId.PRECIPITATION)
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(intensity, PRECIPITATION_POOL_SIZE, effectOffset)
        val seed = seedFor(EffectId.PRECIPITATION)
        // Rain still falls at a brisk pace; snow was falling noticeably faster than real snow
        // drifts (0.35 meant a full screen-height fall in under 3 seconds) -- slowed down
        // significantly so it reads as a gentle drift instead of a downpour.
        val fallSpeed = if (isRain) 1.3f else 0.09f
        // Same band-center line drawClouds' own `laneY` is built around (bandTop + bandHeight*0.5)
        // -- see this function's own doc comment for why this replaced the old bandTop-only origin.
        val cloudBandTop = cloudBandTopFor(screenHeight, sceneCustomization.sky.sunCloudHeight)
        val cloudBandHeight = cloudBandHeightFor(screenHeight)
        val fallStartY = cloudBandTop + cloudBandHeight * 0.5f
        // The sky's brightness at the two ends of the stretch the drop crosses with sky behind it:
        // the cloud band's own middle, where a drop is born, down to the top of the hills. `skyAbove`
        // is a lerp and Rec. 601 luma is linear in the channels, so these two bound the whole
        // stretch and no third sample can fall outside them.
        val precipHorizonY = screenHeight * SceneSpace.HILL_LAYER_TOP_FRACTION
        val skyLumaAtFallStart = rec601Luma(skyAbove(fallStartY))
        val skyLumaAtHorizon = rec601Luma(skyAbove(precipHorizonY))
        val skyLumaLow = kotlin.math.min(skyLumaAtFallStart, skyLumaAtHorizon)
        val skyLumaHigh = kotlin.math.max(skyLumaAtFallStart, skyLumaAtHorizon)
        // One colour for the whole fall, clear of the whole band -- see [standOffFromSky] for the
        // argument that no per-height correction can be continuous.
        precipPaint.color = standOffFromSky(themeColour, dropLuma, skyLumaLow, skyLumaHigh, neededColourGap)
        // Every size below is a scene metre turned into pixels for this viewport, the same
        // conversion every object in the scene goes through. See the constants' own doc.
        val metrePx = SceneSpace.pixelsPerMetre(screenHeight.toFloat())
        val fallRange = (screenHeight + PRECIPITATION_BOTTOM_MARGIN_METRES * metrePx) - fallStartY
        // Hoisted: the pool is 240 since v4.5, so a product left inside the loop is paid 240
        // times a frame for a value that cannot change between drops.
        val rainLengthMin = RAIN_LENGTH_MIN_METRES * metrePx
        val rainLengthMax = RAIN_LENGTH_MAX_METRES * metrePx
        val snowRadiusMin = SNOW_DIAMETER_MIN_METRES * metrePx / 2f
        val snowRadiusMax = SNOW_DIAMETER_MAX_METRES * metrePx / 2f
        val snowSway = SNOW_SWAY_METRES * metrePx
        // Paint state that does not vary between drops, set once instead of once per drop.
        // `isRain` is fixed for the whole call, so the style, stroke width and cap were being
        // rewritten identically up to PRECIPITATION_POOL_SIZE times a frame. Only the alpha and
        // the geometry actually change per drop, and those stay in the loop.
        if (isRain) {
            precipPaint.style = Paint.Style.STROKE
            precipPaint.strokeWidth = RAIN_STROKE_WIDTH_METRES * metrePx
            precipPaint.strokeCap = Paint.Cap.ROUND
        } else {
            precipPaint.style = Paint.Style.FILL
        }
        for (i in 0 until PRECIPITATION_POOL_SIZE) {
            val xFraction = CandidateNoise.value(seed, i, CandidateNoise.CH_X)

            // Local density, not global: the candidate is tested against
            // `intensity x coverage(x)`, so a drop over open sky simply does not exist while one
            // under full cloud behaves exactly as it did before. Sampled at the drop's base x
            // rather than its swayed x, so a snowflake's own drift cannot make it flicker in and
            // out at a coverage boundary.
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

            // Fade in over the first 10% of the fall and out over the last 10% -- this alone is
            // most of what sells "emerging from the cloud layer" rather than popping into
            // existence mid-air.
            val fadeRange = 0.1f
            val fadeAlpha = when {
                fallFraction < fadeRange -> fallFraction / fadeRange
                fallFraction > 1f - fadeRange -> (1f - fallFraction) / fadeRange
                else -> 1f
            }.coerceIn(0f, 1f)

            if (isRain) {
                precipPaint.alpha = (RAIN_ALPHA * fadeAlpha).toInt()
                val len = CandidateNoise.range(seed, i, CandidateNoise.CH_LENGTH, rainLengthMin, rainLengthMax)
                canvas.drawLine(x, y, x - len * 0.25f, y + len, precipPaint)
            } else {
                precipPaint.alpha = (SNOW_ALPHA * fadeAlpha).toInt()
                val r = CandidateNoise.range(seed, i, CandidateNoise.CH_WIDTH, snowRadiusMin, snowRadiusMax)
                canvas.drawCircle(x, y, r, precipPaint)
            }
        }
    }

    /**
     * Fall Colors' falling-leaves effect -- same stateless deterministic-candidate approach as
     * [drawPrecipitation] (no per-particle list to manage between frames, just each candidate's
     * own phase/seed re-evaluated every frame from [elapsedSeconds]). Not user-configurable
     * (density/color) the way rain/snow are -- this is a fixed, modest scattering tied purely to
     * the Fall Colors toggle, matching how simple aa asked for it ("trees with autumn tones and
     * periodic falling leaves"), not a full new settings category.
     *
     * Leaves fall much slower than rain/snow (real leaves drift, they don't plummet) and tumble
     * with a rotating oval shape rather than a static dot/line, which is what actually reads as
     * "a leaf" instead of just another kind of precipitation.
     */
    /**
     * Fixed after aa reported leaves reading as falling from the sky rather than off the trees.
     * `fallStartY` used to be `-20f` -- literally above the top of the screen, the same "falls
     * from empty space" mistake [drawPrecipitation] had before its own origin was fixed against
     * the clouds -- meaning leaves crossed the *entire* screen height (sky, clouds, everything)
     * before ever reaching tree level, with zero relationship to where any tree canopy actually
     * is. Moved the origin down to the hill band's own top edge (`yOffsets[0]`, the same
     * constant [drawHillLayers] itself uses) -- trees sit within the hill's ground band and their
     * canopies extend a bit above their own base, so starting right at the hill top reads as
     * "coming off the trees poking above the hill line" instead of falling out of open sky.
     * Falls only as far as **its own tree's ground line**, which is as far as an actual falling
     * leaf travels before it lands. It used to fall to one global `screenHeight * 0.88` instead --
     * below both traffic lanes -- so leaves from every tree, however far back it stood, drifted
     * down over the hillside and settled on the road among the cars.
     */
    private fun drawFallingLeaves(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime) {
        if (!sceneCustomization.fallColorsEnabled) return
        // No density filter here -- leaves are all-or-nothing with fallColorsEnabled -- so this
        // conversion is purely about removing the per-frame Random. Every candidate is still
        // drawn, in the same order, with values in the same ranges.
        val seed = seedFor(EffectId.FALLING_LEAVES)
        val palette = intArrayOf(
            0xFFD2691E.toInt(), // orange
            0xFFB5451B.toInt(), // rust red
            0xFFE0A93A.toInt(), // gold/yellow
            0xFF8F3B1B.toInt(), // deep brown-red
        )
        // **Every leaf comes off a crown that is actually on screen.**
        //
        // It used to start at one height across the whole width -- `xFraction * screenWidth` at a
        // fixed `fallStartY` -- so on a device most leaves appeared in clear sky with no tree
        // anywhere near them. The tree positions were never available here: a crown's screen
        // position is its depth, its ground line, its scale and its wrap-tile offset combined, and
        // all four are resolved inside `SceneObjectRenderer.draw`, which runs immediately before
        // this. It now records them, and a leaf is assigned to one of them.
        //
        // No trees on screen means no leaves, which is the correct answer rather than a fallback:
        // a themeless expanse with the tree category switched off should not be shedding foliage.
        val sources = objectRenderer.leafSourceCount
        if (sources == 0) return
        // **A leaf belongs to its tree, not to a slot in this frame's source array.**
        //
        // The candidate index used to be `i % sources` over the visible-crown array, which is
        // refilled every frame in visibility order. A home-screen swipe scrolls trees across the
        // screen edges, the array's membership changes, and that modulo handed every candidate to
        // a different tree: measured on a OnePlus 6T screen recording, leaves teleported mid-fall
        // in exactly the frames the visible set changed -- the "scene rebuilds on swipe" report.
        // The time base was never the problem; `elapsedSeconds` runs straight through a swipe.
        //
        // Now each visible crown derives [FALLING_LEAVES_PER_TREE] candidates from its own stable
        // identity ([SceneObjectRenderer.leafSourceId]), so a leaf's phase, speed, drift and
        // colour are functions of *its tree* and nothing else. While a tree is on screen its
        // leaves fall undisturbed however the viewport moves; when it scrolls off, its leaves go
        // with it -- they were only ever drawn over it. Still stateless: same noise, same
        // channels, re-evaluated from the clock every frame, nothing stored between frames.
        val fallSpeed = 0.06f
        for (source in 0 until sources) {
            val id = objectRenderer.leafSourceId[source]
            val perTree = objectRenderer.leafSourceLeafCount[source]
            val fallStartY = objectRenderer.leafSourceY[source]
            // **A leaf lands at the foot of its own tree.** This was one global
            // `screenHeight * 0.88` for every source, which is below both traffic lanes, so a
            // leaf from a tree on the hillside crossed the whole scene and settled on the road
            // among the cars. See [SceneObjectRenderer.leafSourceGroundY].
            val fallEndY = objectRenderer.leafSourceGroundY[source]
            val fallRange = fallEndY - fallStartY
            if (fallRange <= 0f) continue
            for (j in 0 until perTree) {
                // The candidate index mixes the copy's stable identity with the leaf's own slot,
                // so a leaf's phase, drift and colour are functions of its tree copy and nothing
                // else. MAX_LEAVES_PER_SOURCE rather than the frame's own perTree in the stride,
                // so slot j keeps its noise whatever the count beside it does.
                val i = id * MAX_LEAVES_PER_SOURCE + j
                val speedVariance = CandidateNoise.range(seed, i, CandidateNoise.CH_VARIANCE, 0.7f, 1.3f)
                val phase = CandidateNoise.value(seed, i, CandidateNoise.CH_PHASE)
                val fallFraction = elapsedSeconds.cycle(fallSpeed * speedVariance, phase)
                val y = fallStartY + fallFraction * fallRange
                val sway = elapsedSeconds.sinAt(0.9f, phase * 6.28f) * 26f
                // Across the crown it left, not across the screen: the offset is a fraction of
                // that crown's own half width, so a small tree sheds from a small area and a near
                // one from a wide one. The sway then carries it away as it falls, which is the
                // drift the effect always had.
                val acrossCrown = (CandidateNoise.value(seed, i, CandidateNoise.CH_X) - 0.5f) * 2f
                val x = objectRenderer.leafSourceX[source] +
                    acrossCrown * objectRenderer.leafSourceHalfWidth[source] + sway * fallFraction
                val spin = elapsedSeconds.cycleOf(60f * (0.5f + speedVariance * 0.5f), phase * 360f, 360f)
                val color = palette[i % palette.size]
                // Fade in leaving the canopy, fade out settling near the ground -- same polish
                // drawPrecipitation's own fade already uses, for the same "doesn't just pop
                // into/out of existence" reason.
                val fadeRange = 0.12f
                val fadeAlpha = when {
                    fallFraction < fadeRange -> fallFraction / fadeRange
                    fallFraction > 1f - fadeRange -> (1f - fallFraction) / fadeRange
                    else -> 1f
                }.coerceIn(0f, 1f)
                leafPaint.color =
                    blendColor(ColorUtils.blendARGB(color, 0xFF000000.toInt(), 0.35f), color, dayPhase.dayBlend)
                leafPaint.style = Paint.Style.FILL
                leafPaint.alpha = (220 * fadeAlpha).toInt().coerceIn(0, 255)
                // A fully faded leaf is not drawn at all: an alpha-0 oval costs a draw call and,
                // at the fall's very first frame, would count as "a leaf on the crown" to any
                // honest pixel accounting -- including FallingLeafContinuityTest's.
                if (leafPaint.alpha == 0) continue
                canvas.save()
                canvas.translate(x, y)
                canvas.rotate(spin)
                canvas.drawOval(-4f, -6f, 4f, 6f, leafPaint)
                canvas.restore()
            }
        }
    }


    /** Advances the thunderstorm's lightning timer/fade. Only ticks (and can fire) while [enabled]
     * -- when precipitation is off, not raining, or the storm toggle is off, the flash simply
     * fades out and stops, it never fires while disabled. [lightningStrikesEnabled] is the same
     * "can fire" gate seen from the other side: the weather says whether there is a storm, that
     * says whether anything is allowed to roll a strike out of it. */
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
     * The strike itself: a full-screen white veil, then the bolt on top of it.
     *
     * The veil is what the thunderstorm always had. The bolt is new artwork -- `lightning_bolt`
     * is one of the six V2 sprites drawn for shapes the renderer used to have no drawing for at
     * all -- and it goes *after* the veil deliberately: a bolt painted under a 70 %-opaque white
     * wash is a bolt nobody sees. Both fade on the same `lightningFlashAlpha`, so the bolt is
     * gone by the time the veil is.
     */
    private fun drawLightningFlash(canvas: SceneCanvas) {
        if (lightningFlashAlpha <= 0f) return
        lightningPaint.color = 0xFFFFFFFF.toInt()
        lightningPaint.alpha = (LIGHTNING_VEIL_MAX_ALPHA * lightningFlashAlpha).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), lightningPaint)

        // The sprite hangs from its own top edge, so the scale that gives it the rolled height is
        // that height over 84, and the origin centres it on the rolled x.
        //
        // The y is read from [cloudBandTopFor] rather than from a constant of its own: a bolt is
        // born inside the cloud band, past its midpoint, so its head is behind cloud and only the
        // fork below is seen. The old fixed 0.08 of screen height put it above the band entirely.
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

    private fun drawMountains(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase) {
        // v47 anchored this to the farthest hill layer's absolute *best*-case peak (fraction
        // 0.15 -- the highest point buildBaseHillPath's random top edge can ever reach). That's
        // backwards: 0.15 is only reached at a couple of x positions per screen (the actual
        // top edge is redrawn per-segment with an independent random roll each time, ranging
        // anywhere from 0.15 down to 0.75) -- so anchoring the *fixed* mountain/lake base line to
        // the shallowest possible point left a real gap of bare sky beneath it at almost every x,
        // wherever the hill's own wavy edge happened to dip lower that frame. The anchor has to be
        // the hill's *worst-case-covered* line -- the deepest its top edge can ever reach -- not
        // its peak, or the gap reopens at whichever column dips furthest.
        // [SceneSpace.HILL_SOLID_TOP_DEPTH_FRACTION] is exactly that same "always-solid, whatever the roll"
        // fraction already derived and proven for object row placement -- reusing it here (instead
        // of inventing a second, inconsistent constant) guarantees mountains/lake always connect
        // directly into the hills with zero gap, at every x. Verified with a rendered mock of both
        // the old and new anchor before this edit.
        val effectiveBaseYFraction =
            if (updateLakeBandY()) lakeBandTopY / screenHeight else hillGuaranteedTopFraction

        // Sized in one normalized unit -- screen *height*, in portrait -- rather than guessed per
        // layer: back mountains get a height in `[0.8,1.2] * 0.15` and a width in
        // `[0.8,1.2] * 0.25` of that unit (which equals screen height in portrait -- see
        // `SceneBase.setupScreenSizes()`). The previous version of this comment converted that
        // 0.25/0.15≈1.67 width:height ratio into PaperScrape's own widthFraction-of-*screen-width*
        // convention by reusing the *old* (too-tall) 0.60/0.29≈2.07 ratio -- which was wrong,
        // baked in the exact same error that made the old mountains too tall, and produced
        // mountains far narrower than they should be (the reported "too narrow" bug). Fixed by
        // computing width the same way height already is -- as a fraction of screenHeight, so
        // both are fractions of the *same* unit
        // -- removing the error-prone width-of-screenWidth conversion entirely rather than
        // re-deriving it correctly by hand. `widthOfHeightFraction` below is `sx`'s own average
        // (0.25 back, 0.175 front, the front layer scaled by 0.7) directly, no conversion needed.
        drawMountainLayer(
            canvas, dayPhase, sceneCustomization.mountainsBack, parallaxFactor = 0.04f, seedSalt = EffectId.MOUNTAINS_BACK,
            baseYFraction = effectiveBaseYFraction, peakHeightFraction = 0.15f, widthOfHeightFraction = 0.25f,
        )
        drawMountainLayer(
            canvas, dayPhase, sceneCustomization.mountainsFront, parallaxFactor = 0.08f, seedSalt = EffectId.MOUNTAINS_FRONT,
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
        // Fully opaque -- the back layer used to render at alpha 200 as a cheap depth cue, and it
        // caused a real, reported bug: with the
        // sun/moon drawn *behind* mountains in z-order, a partially transparent back layer let it
        // (and the sky) visibly bleed through the mountain's own silhouette. Depth between the two
        // layers is already communicated by their independently user-editable colors (and, once a
        // layer is picked, its own smaller/larger size and lower/higher position) -- opacity was
        // never needed for that and only introduced this glitch.
        mountainPaint.alpha = 255

        val effectOffset = CandidateThreshold.offsetFor(seedSalt)
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(config.density, MOUNTAIN_POOL_SIZE, effectOffset)
        val seed = seedFor(seedSalt)
        val tileWidth = screenWidth * 2f
        val parallax = (parallaxFactor * parallaxStrength).coerceAtMost(1f)
        val wrappedShift = wrappedScrollShift(parallax.toDouble(), tileWidth)

        val baseY = screenHeight * baseYFraction
        val peakHeight = screenHeight * peakHeightFraction
        val baseWidth = screenHeight * widthOfHeightFraction

        for (i in 0 until MOUNTAIN_POOL_SIZE) {
            if (!CandidateThreshold.isPresent(i, config.density, effectOffset, fallbackIndex)) continue

            val tileFractionX = (i + 0.5f) / MOUNTAIN_POOL_SIZE +
                (CandidateNoise.value(seed, i, CandidateNoise.CH_X) - 0.5f) * (1f / MOUNTAIN_POOL_SIZE) * 0.5f
            val heightJitter = CandidateNoise.range(seed, i, CandidateNoise.CH_HEIGHT, 0.75f, 1.25f)
            // Reference randomizes sx/sy independently per candidate (two separate rand() calls,
            // both over the same [0.8,1.2] range) -- not just height, which is all this used to
            // jitter, giving every mountain of a given layer identical width.
            val widthJitter = CandidateNoise.range(seed, i, CandidateNoise.CH_WIDTH, 0.75f, 1.25f)
            val baseX = tileFractionX * tileWidth + wrappedShift
            val width = baseWidth * widthJitter

            // Draw at the tile position and its immediate wrap-neighbors so shapes near a tile
            // seam are never abruptly cut off mid-scroll.
            for (tileOffset in -1..1) {
                val x = baseX + tileOffset * tileWidth
                if (x < -width || x > screenWidth + width) continue
                drawSoftMountain(canvas, x, baseY, width, peakHeight * heightJitter)
            }
        }
    }

    /**
     * A rounded, parabolic-arch mountain silhouette -- measured directly from the reference
     * app's own "parabola" sprite (`land1.png`, top-left): sampled its width at 15 heights from
     * peak to base (this time with the crop wide enough not to clip the base, after an earlier
     * measurement pass clipped it) and confirmed it closely follows `width ∝ √(fraction from
     * peak)`, i.e. a genuine parabola (matching the sprite's own name) -- accurate to within ~1-2%
     * of the real sprite at every sampled point, so the *curve* itself was never the problem.
     *
     * What *did* need fixing: this used to sample that curve at points evenly spaced by *height*
     * (`t = i/segments`), but `√t` has infinite slope at `t=0` -- the width changes fastest right
     * at the peak, exactly where evenly-height-spaced sampling places its sparsest points. At the
     * mountain sizes this file used before, that faceting was too small to read as a flaw; once
     * mountains got smaller, the same 8-segment discretization became a visibly angular
     * "shoulder" instead of a smooth round cap. Fixed by sampling evenly spaced in *width*
     * instead (`x` from 0 to `halfWidth`, deriving
     * `t = x²` since that's `√t`'s own inverse) -- points naturally bunch up near the peak where
     * the curve bends fastest, giving a properly round tip at the same segment count.
     *
     * **Batch 4 aesthetic pass**: filled as two halves sharing the exact same peak/base points
     * (so there's no seam) rather than one flat-color fill. A mountain is vertex-coloured
     * geometry rather than a sprite -- there is nothing to convert to a blit here -- but
     * a flat single-color silhouette read noticeably flatter than every sprite-converted object
     * elsewhere in the scene now carries its own baked-in "paper fold" shading. A left face
     * lightened and a right face darkened (a fixed light-from-upper-left convention, same side
     * every other shaded element in this file already assumes) sells the same folded-paper look
     * procedurally instead, at effectively no extra per-frame cost.
     */
    private fun drawSoftMountain(canvas: SceneCanvas, cx: Float, baseY: Float, width: Float, height: Float) {
        val halfWidth = width / 2f
        val segments = 16 // up from 8 -- see this function's doc comment for why
        val peakX = cx
        val peakY = baseY - height

        mountainShape.reset()
        mountainShape.moveTo(cx - halfWidth, baseY)
        for (i in segments downTo 0) {
            val xFrac = i / segments.toFloat() // 1=base, 0=peak -- fraction of *width*, not height
            val t = xFrac * xFrac // inverse of width=√t
            val y = baseY - height * (1f - t)
            val x = cx - halfWidth * xFrac
            mountainShape.lineTo(x, y)
        }
        // Close via the *vertical center axis* (peak straight down to (cx, baseY)), not a
        // diagonal straight back to the base-left point -- that diagonal was today's actual bug
        // ("invisible triangle with two stripes around it"): this parabola bulges out sharply
        // near the base (x moves fastest right where the curve bends fastest, per this
        // function's own doc comment on why segments are width-spaced), so a straight line from
        // peak to base-left cuts far inside the curve at every mid-height, leaving only a thin
        // crescent between that diagonal and the curve actually filled -- most of the intended
        // half-mountain area sat *outside* the polygon (background showing through) instead of
        // inside it. Verified with a rendered mock of both the broken and fixed geometry before
        // this edit. The vertical axis is the curve's own true bisector (peakX = cx by
        // construction), so this closes the shape exactly at the mountain's real center line.
        mountainShape.lineTo(cx, baseY)
        mountainShape.close()
        // **One colour per mountain.** The two halves used to be lightened and darkened by 10 %
        // and 8 % to fake a paper fold, and against the V2 palette that reads as two different
        // mountains meeting at a hard vertical seam rather than as one shaded shape -- the split
        // runs straight down the peak, which is exactly where a fold would not be. The silhouette
        // is drawn in the layer's own colour and the only division left is the one the hills make
        // by overlapping it, which is the division the scene is built on.
        canvas.drawShape(mountainShape, mountainPaint)

        mountainShape.reset()
        mountainShape.moveTo(peakX, peakY)
        for (i in 0..segments) {
            val xFrac = i / segments.toFloat() // 0=peak, 1=base
            val t = xFrac * xFrac
            val y = baseY - height * (1f - t)
            val x = cx + halfWidth * xFrac
            mountainShape.lineTo(x, y)
        }
        mountainShape.lineTo(cx, baseY) // same center-axis fix as the left half, mirrored
        mountainShape.close()
        canvas.drawShape(mountainShape, mountainPaint)
    }

    /**
     * A body of water, drawn as its own independent horizontal band -- positioned in the
     * "middle distance" (y 0.58-0.78 of screen height at full [LakeConfig.height]) safely apart
     * from the road/house zone (which stays above [SceneSpace.roadTopYFraction], capping around
     * y=0.86) so it never visually competes with houses, cars, or the road. Same independent,
     * safety-geometry-free approach as [drawMountains].
     */
    /**
     * The lake's current top Y in pixels, valid only when [updateLakeBandY] last returned `true`.
     *
     * Two fields and a boolean rather than the `Pair<Float, Float>?` this used to return. A
     * `Pair` of floats boxes both of them, and this is called twice on every frame -- once by
     * [drawMountains] for its base line and once by [drawLake] -- so it was four boxed floats and
     * a `Pair` per frame for two numbers that never leave this class. Same values, same
     * conditions, no allocation.
     */
    private var lakeBandTopY = 0f

    /** The lake's current bottom Y in pixels. See [lakeBandTopY]. */
    private var lakeBandBottomY = 0f

    /** Recomputes [lakeBandTopY]/[lakeBandBottomY], returning whether the lake is visible at all.
     *
     * Shared by [drawLake] and [drawMountains]: the mountains' base is computed as
     * `max(hillsReference, waterTopIfLakesOn)` rather than being a fixed guess independent of
     * wherever the water actually is. `false` means the
     * lake isn't visible and callers fall back to their own hill-only reference; the two fields
     * are then stale and must not be read. */
    private fun updateLakeBandY(): Boolean {
        val lake = sceneCustomization.lake
        if (!lake.visible) return false
        // The lake now sits *above* where hills begin, not overlapping their body -- verified
        // with an actual rendered mock of the geometry (not just the math): hills are largely
        // opaque, so there's almost no room for anything behind them to show through except right
        // at their very topmost edge. Bottom is anchored to [hillGuaranteedTopFraction] -- the
        // hill layer's own always-covered line, not its best-case peak (see [drawMountains]'s doc
        // comment for why the peak was the wrong choice: it left a gap of bare sky beneath the
        // lake at almost every x). Hills (drawn after, on top) dip slightly into the lake's own
        // bottom edge for a touch of organic overlap, and now do so with zero chance of a gap of
        // bare sky between them at any x.
        //
        // Top extends further up into the sky as Lake Height increases, capped at 0.16 (down
        // from 0.20) -- v49 shrank the mountains considerably but left this cap alone, so at max
        // Lake Height the lake's top edge could
        // rise as high as 0.704-0.20=0.504 while even the *tallest* possible back-mountain
        // candidate (peakHeightFraction 0.15, heightJitter up to 1.25) only reaches
        // 0.704-0.15*1.25≈0.5165 -- the lake could swallow every mountain on screen at high
        // settings. 0.16 keeps the lake's highest possible top (0.704-0.16=0.544) safely below
        // that worst-case mountain peak, with margin.
        val bottom = screenHeight * hillGuaranteedTopFraction
        val bandHeight = screenHeight * 0.16f * lake.height.coerceIn(0f, 1f)
        val top = bottom - bandHeight
        lakeBandTopY = top
        lakeBandBottomY = bottom
        return true
    }

    private fun drawLake(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime) {
        if (!updateLakeBandY()) return
        val top = lakeBandTopY
        val bottom = lakeBandBottomY
        val lake = sceneCustomization.lake
        val bandHeight = bottom - top
        lakePaint.color = blendColor(lake.colorNight, lake.colorDay, dayPhase.dayBlend)
        // The lake used to be drawn at fixed absolute screen coordinates, entirely independent of
        // scrollProgress -- meaning it stayed dead still while hills/houses scrolled past it, a
        // real bug (reported as "buildings feel tied to the ground, everything else feels almost
        // frozen while the terrain moves under it"). Given a parallax factor between the mid and
        // near hill layers' own rates (0.35/0.6), matching roughly where the lake sits vertically
        // among them.
        val lakeParallax = (0.25f * parallaxStrength).coerceAtMost(1f)
        val lakeWrapped = wrappedScrollShift(lakeParallax.toDouble(), screenWidth.toFloat())

        canvas.save()
        canvas.translate(lakeWrapped, 0f)
        // Three screen-width-wide copies side by side so the translate above never exposes a gap
        // at either edge -- **and only the copies that actually reach the screen**. `lakeWrapped`
        // is in (-screenWidth, 0], so at any non-zero wrap the copy at -1 is entirely off the left
        // edge and is skipped; at a wrap of exactly zero it is kept, because a sparkle drifting at
        // the very end of that copy still reaches a few pixels past its right edge. Until v4.26 all
        // three were drawn unconditionally, at every wrap.
        for (tileOffset in -1..1) {
            val x0 = tileOffset * screenWidth + lakeWrapped
            if (x0 + screenWidth < 0f || x0 > screenWidth) continue
            drawLakeBand(canvas, (tileOffset * screenWidth).toFloat(), top, bottom, bandHeight, elapsedSeconds)
        }
        canvas.restore()
        // The waterline is uniform along x, so one call outside the tile loop draws exactly what
        // three inside it would. The glow and the light's path sit under the celestial body, which
        // does not scroll with the water, so neither is tiled either.
        drawWaterline(canvas, top)
        drawLakeMirrorGlow(canvas, dayPhase, top, bandHeight)
        drawLakeGlitter(canvas, dayPhase, top, bandHeight, elapsedSeconds, stormStrength())

        // **One pass over the water, painted far to near.**
        //
        // Boats and dolphins used to be two independent passes in candidate order, which decided
        // what covered what by index instead of by distance. Everything on the surface is now
        // placed first and drawn afterwards in [LakeLanes.orderByDepth] order, so the nearer hull
        // is always the one in front -- see LakeLanes for why that is the whole of the fix.
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
        // The waves join the same pass, keyed by their waterline said in the boat's convention.
        if (rainingNow || stormActiveNow) lakeItems = gatherWaves(lakeItems, top, bandHeight, dayPhase.dayBlend, elapsedSeconds)
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

    // Slots for one frame's worth of lake surface, sized for every candidate of both categories.
    // Fields rather than locals because this is a draw path: a per-frame list here would be a
    // per-frame allocation, which is exactly what the CPU audit exists to keep out.
    private val lakeItemX = FloatArray(LakeLanes.LANE_COUNT + WAVE_POOL)
    private val lakeItemY = FloatArray(LakeLanes.LANE_COUNT + WAVE_POOL)

    /**
     * What each item is sorted on, which is **not** always [lakeItemY].
     *
     * For everything sitting on the water it is the lane: a hull further down the band is nearer
     * and is painted later, and that is what makes two overlapping boats read as one passing in
     * front of the other. For a dolphin *in the air* it is the lane minus how far it has risen,
     * because the animal's body is no longer at its lane -- see [gatherLakeDecorations].
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
     * One screen-width-wide copy of the water, offset horizontally by [xOffset] -- see [drawLake],
     * which draws the copies that reach the screen side by side under one translate.
     *
     * **The water is a mirror, and that is the whole of it (v4.26, concept S1 "Specchio").** It is
     * one vertical gradient from the sky's own horizon colour at the far edge into the theme's lake
     * colour at the near edge, plus the drifting sparkle glints the band has carried since v46, and
     * nothing else: no bands, no ripple lines, no waves. Three ways of drawing the surface were
     * photographed on the device -- a mirror, a long swell and rows of illustrator's strokes -- and
     * the mirror was chosen: at the height this band is actually drawn, waves read as stripes, and
     * the light's path ([drawLakeGlitter]) already gives the surface everything it needs to say it
     * is water rather than a painted rectangle.
     *
     * **The top edge is a flat straight line, and must stay one.** An earlier version made it wavy
     * to blend the lake's far edge into the hills' silhouette -- but once the lake moved to sit
     * above the hills entirely (v46) its top edge borders plain sky, and worse, the mountains
     * anchor to the band's own *nominal* top Y while a jittered edge dips below it at some x,
     * opening a thin sliver of bare sky between the mountain's fixed base and the water. A flat
     * edge and a fixed mountain base line up exactly, everywhere, always. Where the mirror leaves
     * that edge too quiet, [drawWaterline] strikes it instead of bending it.
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
     * The cut edge of the water, struck along the top of the band.
     *
     * See [WATERLINE_MIN_LUMA_GAP] for why the lake needs one and where 19 comes from. The colour
     * is derived per frame rather than declared: the water's own surface tone is carried toward
     * black or white -- whichever is *away* from the sky -- by exactly the fraction that puts it
     * [WATERLINE_MIN_LUMA_GAP] of luma clear of the sky at the horizon, and no further. A theme
     * whose water is already that far clear gets `t = 0`, which is the water's own colour and
     * therefore no visible line at all: the edge appears only where it is needed, and it is never
     * louder than the gap requires.
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
     * [base] carried toward white or black until it is [neededGap] of luma clear of **every** sky
     * between [skyLumaLow] and [skyLumaHigh], by exactly enough and no further.
     *
     * The same shape as [drawWaterline] and for the same reason: a hairline drawn on a surface that
     * moves cannot have a fixed colour, and the honest fix is to state the separation it needs and
     * derive the colour from it. [neededGap] is a gap on the *colour*, already divided by the alpha
     * the stroke is painted at, so what the eye is given is [PRECIPITATION_MIN_LUMA_GAP].
     *
     * ### Why one colour for the whole fall, and not one per height
     *
     * A drop crosses a gradient, so the obvious answer is to correct it against the sky at its own
     * height. **That answer cannot exist.** The sky's luma is monotone down the fall and the drop's
     * is constant, so whenever the two are close the sky crosses the drop somewhere inside it: above
     * the crossing the drop is the brighter of the two, below it the darker. A correction that
     * clears the gap everywhere must therefore sit above the sky at one end of the fall and below it
     * at the other, and those two branches never meet — any such function has a step in it. A step
     * means lighter-than-sky rain above one line and darker-than-sky rain below it, in the same
     * frame, which is a worse artefact than the one being fixed. One colour per frame is the only
     * form that is both continuous and clear of the whole band, and what it costs is that a height
     * where the rain was already fine is carried along with the height where it was not.
     *
     * The direction is the cheaper of the two, priced by how far each has to carry the colour. A
     * drop already clear of the whole band returns [base] unchanged and is therefore bit-identical
     * to what v4.26 drew — which is what lets the golden set move only on the scenes where the sky
     * and the rain actually collided.
     */
    private fun standOffFromSky(
        base: Int,
        baseLuma: Float,
        skyLumaLow: Float,
        skyLumaHigh: Float,
        neededGap: Float,
    ): Int {
        if (baseLuma >= skyLumaHigh + neededGap || baseLuma <= skyLumaLow - neededGap) return base
        val whiteTarget = skyLumaHigh + neededGap
        val blackTarget = skyLumaLow - neededGap
        val whiteSpan = 255f - baseLuma
        val blackSpan = -baseLuma
        val tWhite = if (whiteSpan <= 0f) Float.MAX_VALUE else (whiteTarget - baseLuma) / whiteSpan
        val tBlack = if (blackSpan >= 0f) Float.MAX_VALUE else (blackTarget - baseLuma) / blackSpan
        val towardWhite = tWhite <= tBlack
        val t = (if (towardWhite) tWhite else tBlack).coerceIn(0f, 1f)
        if (t <= 0f) return base
        return ColorUtils.blendARGB(base, if (towardWhite) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), t)
    }

    /**
     * The sky's own colour at [y], which is what the waterline borders.
     *
     * **Not the horizon colour**, and the difference is the whole reason this function exists. The
     * sky is one gradient from `skyTopColorNow` at y = 0 to `skyHorizonColorNow` at the bottom of
     * the *screen*, and the water's top edge sits well above that: on Tundra it is at 0.58 of the
     * screen, where the sky is 25 units of luma away from the colour at the bottom. Deriving the
     * edge against the wrong end of the gradient produced a line that sat *between* the sky and the
     * water instead of clear of both -- measured at the shore: sky 225.1, line 228.8, water 232.9.
     */
    private fun skyAbove(y: Float): Int =
        ColorUtils.blendARGB(skyTopColorNow, skyHorizonColorNow, (y / screenHeight).coerceIn(0f, 1f))

    /** Rec. 601 luma, the weighting [StormAtmosphere.dim] and the rest of the app's colour work use. */
    private fun rec601Luma(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000f
    }

    /** The drifting glints the surface has carried since v46: a fixed handful, stateless. */
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
            ripplePaint.alpha = (140 * twinkle).toInt().coerceIn(0, 255)
            canvas.drawLine(sx - 5f, sy, sx + 5f, sy, ripplePaint)
        }
        ripplePaint.alpha = 255
    }

    /** One scratch shape, reused for every sliver of the light's path: the draw path allocates
     * nothing per frame (AI_PROJECT_RULES 5.1). */
    private val lakeScratchShape = SceneShape(8)

    /**
     * The sun or the moon reflected as a soft glow on the mirror, directly under the body.
     *
     * **The centre is one radius below the waterline, and that is not a look — it is the only way
     * this can be drawn.** [SceneCanvas] has no clip, so a radial glow centred *at* the top of the
     * band spills its upper half into the sky above the shore, which is exactly the edge
     * [drawWaterline] exists to sharpen. The concept build did that: measured against the v4.25
     * frame, `people-skin` changed 11 rows above its own waterline, under the sun's x and nowhere
     * else. Placing the centre at `top + radius` puts the glow's own zero alpha on the waterline,
     * so the reflection reaches the shore and stops there. Its lower half runs under the hills,
     * which are drawn after the water and cover it.
     */
    private fun drawLakeMirrorGlow(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, top: Float, bandHeight: Float) {
        if (!celestialShownNow) return
        val low = (1f - dayPhase.celestialY).coerceIn(0f, 1f)
        val gain = (if (celestialIsSunNow) 0.35f + 0.65f * low else (0.3f + 0.7f * low) * (1f - dayPhase.dayBlend)) *
            StormAtmosphere.sunVisibility(stormStrength())
        val bodyColor = if (celestialIsSunNow) sceneCustomization.sun.color else sceneCustomization.moon.color
        val radius = bandHeight * LAKE_MIRROR_GLOW_RADIUS_BANDS
        canvas.drawRadialGlow(celestialCxNow, top + radius, radius, bodyColor, (90f * gain).toInt().coerceIn(0, 255))
    }

    /**
     * **The light's path**: slivers cut on the water under the sun or the moon, widening and
     * lengthening toward the near edge the way a real glitter path does. Not tiled -- it sits under
     * the body, which does not scroll with the water.
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
            (0.25f + 0.75f * low * low) * kotlin.math.sqrt((1f - dayPhase.dayBlend).coerceIn(0f, 1f))
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
            ripplePaint.alpha = (255f * gain * (0.45f + 0.55f * twinkle)).toInt().coerceIn(0, 255)
            lakeScratchShape.moveTo(x - half, y)
            lakeScratchShape.lineTo(x - half * 0.3f, y - thick)
            lakeScratchShape.lineTo(x + half, y)
            lakeScratchShape.lineTo(x + half * 0.3f, y + thick)
            lakeScratchShape.close()
            canvas.drawShape(lakeScratchShape, ripplePaint)
        }
        ripplePaint.alpha = 255
    }

    /**
     * Places one category's present candidates into the frame's lake slots, starting at [from],
     * and returns the new count. Draws nothing: what is in front of what cannot be decided until
     * both categories have been placed.
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

        // Bug fix: dolphins/sailboats used to be placed anywhere from 25% to 75% down the lake
        // band, but the lake's own bottom edge is deliberately anchored to the hill's
        // guaranteed-covered line (see updateLakeBandY's doc comment, and drawHillLayers'
        // "lake drawn before hills" comment -- hills are meant to naturally paint over the
        // *lower* part of the water for depth). Checked the actual numbers against the default
        // theme's lake height (0.33): every laneFraction in the old 0.25-0.75 range landed at or
        // past the hill's own worst-case reach, i.e. always at least "sometimes hidden behind a
        // hill column", several of them "always hidden" -- exactly matching aa's report that
        // dolphins basically never appeared to be swimming in visible water.
        //
        // Fix: bias placement toward the *top* of the band instead, using the same worst-case
        // hill-geometry derivation [SceneSpace.HILL_SOLID_TOP_DEPTH_FRACTION] already uses (just the opposite
        // direction -- "guaranteed never covered" instead of "guaranteed always covered").
        // `buildBaseHillPath`'s own top-edge heightFrac never goes below `centerFraction -
        // maxAmpFraction` = 0.13-0.09 = 0.04 (at any hillsVariation setting -- lower variation
        // only pulls the range *toward* 0.13, never widens it past that), so anything above that
        // absolute Y, with a small margin, is exposed at every column, always. This can't always
        // reach 100% visible at every lake-height setting (a very thin band can sit entirely
        // below that line no matter where within it something is placed), but it always picks
        // the best achievable position instead of the worst one.
        val hillNeverCoveredAbsY = (yOffsets[0] + heightFractions[0] * 0.02f) * screenHeight
        // Guard against a degenerate near-zero band height (e.g. a custom theme with Lake
        // Height dialed to 0 while still visible) -- the division below would otherwise produce
        // NaN, which coerceIn does not reliably clamp away.
        val safeLaneFractionMax = if (bandHeight > 1f) {
            ((hillNeverCoveredAbsY - bandTop) / bandHeight).coerceIn(0.06f, 0.9f)
        } else {
            0.5f
        }

        for (i in 0 until LAKE_DECORATION_POOL_SIZE) {
            if (!CandidateThreshold.isPresent(i, density, effectOffset, fallbackIndex)) continue

            // **Both categories use the whole lake, and still cannot share a lane.**
            //
            // Boats and dolphins had decorrelated noise but no knowledge of each other, so nothing
            // stopped one being placed on the other's line and drifting through it (D-5). Giving
            // each a half of the water fixed that by taking half the lake away from each, which is
            // the wrong trade: the surface is the scene's only open space and both belong on all
            // of it.
            //
            // The band is instead cut into [LakeLanes.LANE_COUNT] lanes spanning it top to
            // bottom, and each category draws from alternate ones. Boats take the even lanes and
            // dolphins the odd, so both reach the near edge and the far edge, and two of them can
            // never be placed on the same line. Where inside its lane a candidate sits is still
            // its own noise, so nothing reads as a grid.
            //
            // The lane count used to be six for eight candidates, so `% 6` folded candidate 3 back
            // onto candidate 0's lane and two boats of the same category shared a line -- with
            // their own speeds, sliding through each other. See [LakeLanes].
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
            // **A leaping dolphin is sorted by where its body is, not by where its lane is.**
            //
            // The lane is the right depth for everything that stays on the water, and v3.0's
            // far-to-near pass over it is not in question here -- two overlapping hulls read
            // correctly. What it could not answer is that these sprites are not the same height:
            // a sail stands about four lane widths above its own waterline while lanes are one
            // lane width apart, so a dolphin one lane nearer than a sailboat -- painted after it,
            // correctly, by lane -- crossed that sail in mid-air. It did not read as "in front":
            // it read as a dolphin flying through a sail.
            //
            // Subtracting the climb makes the rule uniform instead of special-casing the pair:
            // an item's depth is the rendered height of its own base, so a dolphin recedes as it
            // rises and slips behind the boat whose waterline it has climbed above, then comes
            // back in front as it re-enters the water. Three properties make this safe, and
            // `LakeLanesTest` pins all three:
            //  - boats are untouched, so no boat can ever fall behind a farther anything;
            //  - a dolphin's depth only ever *decreases*, so nothing is pulled forward;
            //  - a farther dolphin cannot pass a nearer one, since climb is never negative.
            lakeItemDepth[count] = LakeLanes.depthOf(
                laneY = y,
                heightAboveLane = if (isDolphin) dolphinClimb(phase, elapsedSeconds) else 0f,
            )
            count++
        }
        return count
    }

    /**
     * How far above its own lane a dolphin's body is right now, in screen pixels; zero whenever it
     * is under water.
     *
     * One function rather than two copies of the arithmetic, because the depth ordering in
     * [gatherLakeDecorations] and the placement in [drawDolphin] have to agree exactly: a sort key
     * that disagreed with where the sprite is actually drawn would be worse than no sort at all.
     */
    private fun dolphinClimb(phase: Float, elapsedSeconds: SceneTime): Float {
        val arc = elapsedSeconds.sinAt(DOLPHIN_LEAP_RATE, phase * 6.28f)
        if (arc <= 0f) return 0f
        return arc * SceneSpace.DOLPHIN_LEAP_METRES * SceneSpace.LAKE_PIXELS_PER_METRE *
            SceneSpace.sceneScale(screenHeight.toFloat())
    }

    /** One dolphin, mid-leap or mid-splash, at the point [gatherLakeDecorations] placed it. */
    private fun drawDolphin(canvas: SceneCanvas, x: Float, y: Float, phase: Float, elapsedSeconds: SceneTime) {
            // Batch 4 (terrain sub-group) -- sprite-converted. Unlike mountains and hills, which
            // are procedural vertex-coloured geometry (see drawMountains'/drawHillLayers' own doc
            // comments), a dolphin and a sailboat are finished pieces of art, so both get the
            // same blit treatment batches 1-3 already gave houses/buildings/cars. Same
            // leap/bob/rotate animation as before, just blitting `dolphin_body.png` instead
            // of walking 5 separate Paths every frame.
            //
            // The tint history of this blit is worth keeping. It shipped untinted on the
            // stated grounds that its colours were baked into the PNG; they were not, the
            // artwork was pure white, and white being the `MULTIPLY` identity the blit drew a
            // white silhouette. v74.1 repaired that with a constant. The V2 asset set draws
            // the dolphin in its own greys and blues, which makes the constant the *second*
            // colour over finished art -- the mirror-image defect its own test warned about
            // -- so the repair is retired and the blit goes back to being untinted, this time
            // because the artwork genuinely carries the colour.
            // A dolphin is only drawn while it is **out of the water**. It used to be drawn
            // every frame with a +/-10 unit bob, so it slid across the surface permanently
            // visible, which is what read as flying rather than breaching.
            //
            // `leap` is the positive half of a sine: 0 at the surface, 1 at the top of the
            // arc. Below zero the animal is under water and there is nothing to draw -- no
            // clip is needed, and none is available on the `SceneCanvas` seam anyway. The
            // rotation follows the arc's own slope, so it noses up on the way out and down on
            // the way back in, and the sprite's own centre line sits on the waterline at the
            // instants it enters and leaves.
            val arc = elapsedSeconds.sinAt(DOLPHIN_LEAP_RATE, phase * 6.28f)
            val lakeScale = SceneSpace.sceneScale(screenHeight.toFloat())

            // **Both crossings of the surface, derived from the leap's own phase.**
            //
            // `arc` is `sin(theta)` with `theta = DOLPHIN_LEAP_RATE * t + phase * 6.28`, so
            // the animal is above water for the first half of every turn of that angle.
            // Written as a position in a 0..1 cycle, the two crossings are the two ends of
            // that half: the body breaks the surface at 0, and meets it again at 0.5. Each
            // opens a window of [SPLASH_WINDOW_CYCLES], and the two cannot overlap because
            // the window is a small fraction of half a cycle.
            //
            // **This is one splash per crossing, not one per phase change.** A frame anywhere
            // inside a window draws the splash at the size and opacity its position calls for;
            // a frame outside both draws nothing. Nothing accumulates, nothing repeats while
            // the animal travels, and a dropped frame costs a frame of the effect rather than
            // the whole event.
            //
            // Deriving it also beats remembering it at exactly the seams that matter. A
            // "was it above water last frame" flag needs allocating per dolphin, keeping
            // across a surface change and a visibility pause, and is wrong for one frame every
            // time the wallpaper resumes mid-leap. This keeps no state at all.
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
                // Sized against the sailboat rather than against nothing. Both were blitted at
                // their own native size, which made the animal 115 local units long and the
                // boat 84 -- a dolphin longer than the vessel beside it. [SceneSpace] states
                // both in metres over one lake metric, so the two can only be wrong together.
                canvas.scale(SceneSpace.DOLPHIN_BASE_SCALE * lakeScale, SceneSpace.DOLPHIN_BASE_SCALE * lakeScale)
                sprites.draw(canvas, R.drawable.dolphin_body, DOLPHIN_ORIGIN_X_UNITS, DOLPHIN_ORIGIN_Y_UNITS, SpriteScale.SCENE_UNITS)
                canvas.restore()
            }

            // **After the animal, so it emerges through its own splash on the way out.** On
            // the way back in there is nothing left to cover, so the order costs nothing
            // there; on the way out, drawing the water first would have put the burst behind
            // a body that is rising out of it.
            if (splashProgress >= 0f) {
                canvas.save()
                canvas.translate(x, y)
                // Sized against the animal that made it, so a far dolphin throws a small
                // splash and a near one a larger, and the two can only be wrong together.
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
            // Sprite-converted the same way as the dolphin above -- hull and sail are two
            // separate sprites, kept independent so a future delivery could animate them
            // separately (e.g. sail luffing) without touching the hull.
            //
            // Both carried the same missing-colour defect as the dolphin above and both are
            // resolved the same way in V2: the hull is drawn in wood browns and the sail in
            // off-white with a red band, so neither needs the v74.1 repair constant any more.
            // **Sail first, hull over it.** The sail was blitted after the hull and four units
            // to the right of it, so its foot sat on top of the deck planking off to one side
            // and the two pieces read as separate objects floating together. Drawn first, the
            // hull's own gunwale covers the foot of the sail and the mast reads as stepped
            // into the deck; the origin centres the sail's 70 units of content on the hull's
            // 84, so the mast stands amidships instead of aft.
            //
            // The two origins keep the relationship v76.4 established between them -- the
            // mast amidships, the sail's foot behind the gunwale -- and are shifted together
            // by 32 units so the hull's own content is centred on the placement point rather
            // than hanging off to its right. The pair is now scaled too, from the same lake
            // metric the dolphin uses.
            val boatScale = SceneSpace.SAILBOAT_BASE_SCALE * SceneSpace.sceneScale(screenHeight.toFloat())
            canvas.save()
            canvas.translate(x, y)
            canvas.scale(boatScale, boatScale)
            // v4.31: both origins carry the compensation for the leading padding cropped off
            // these two sprites -- sail +9 units of x, hull +2. The crop removed only
            // transparent columns and the origin moved by exactly what it removed, so no drawn
            // pixel changed coordinate; `BACKLOG_v4_31.md` item 106 has the proof. The hull's
            // **y** is untouched at 8, which is what `SAILBOAT_HULL_WATERLINE_UNITS` derives
            // its 25 from.
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

            // Objects wrap on this exact same tileWidth/wrappedShift now (see below) -- no cap
            // needed here anymore for the desync reason an earlier version of this comment
            // described; that was fixed at the root (see scrollProgress's doc comment), not by
            // bounding the parallax rate.
            val parallax = parallaxFactors[layer] * parallaxStrength
            val layerTop = screenHeight * yOffsets[layer]
            val layerHeight = screenHeight * heightFractions[layer]

            val tileWidth = screenWidth * 2f
            val wrappedShift = wrappedScrollShift(parallax.toDouble(), tileWidth)

            // Fixes the desync bug for good, not just within a bounded range: objects now use
            // *exactly* the same wide tileWidth and wrapped shift as the hills they sit on
            // (removed the separate, narrower screenWidth-period wrap that used to exist here).
            // That narrower period was chosen to solve a real but *different* problem -- "half
            // the candidates invisible at rest" -- by literally computing a second, independent
            // wrap; but two different moduli applied to the same growing shiftX only agree while
            // neither has wrapped, which is exactly what broke once genuine one-directional
            // infinite auto-scroll needed shiftX to grow without bound (a swipe-only interaction
            // was naturally bounded to [0,1], so this went unnoticed until then). Rather than
            // re-bounding the scroll (which the whole point of this fix is to *stop* doing),
            // fixed the actual root cause: candidates spanning a 2-screen-wide period showing
            // roughly half at any one time isn't a bug at all now that every category has its own
            // density slider (it wasn't, when this was first "fixed", back when density wasn't
            // adjustable) -- it reads the same as any other tiled scene. Sharing one wrap value
            // between hills and objects makes them provably impossible to desync, at any scroll
            // magnitude, not just within a bounded window.
            val objectTileWidth = tileWidth
            val objectShiftWrapped = wrappedShift
            // Continuous depth placement now (see StaticSceneObject.depthFraction's own doc
            // comment) -- objectGroundGeometry carries only the horizontal half of the ground,
            // which is the only part that varies per frame. The vertical half is SceneSpace's.
            // The bias is computed in Double against the same unwrapped product the wrap itself
            // used, so the two cannot disagree by a ULP and step the copy identities spuriously.
            val unwrappedShift = -scrollProgress * screenWidth * parallax
            val scrollTileBias = Math.round((unwrappedShift - wrappedShift) / tileWidth).toInt()
            objectGroundGeometry = GroundGeometry(objectShiftWrapped, objectTileWidth, scrollTileBias)

            val path = baseHillShapes[layer] ?: continue

            // Batch 4 aesthetic pass: a subtle vertical gradient (lighter near the wavy top
            // ridge, settling to the exact configured color by ~35% down the layer) instead of
            // one flat fill -- same "paper catching light at the fold" idea as the mountains'
            // two-face split just above, adapted for a continuous wavy shape where a left/right
            // split doesn't apply. Built once per layer (not per tile-offset copy below) since
            // layerTop/layerHeight don't change across those copies and only X gets translated.
            // The hillside is flat vertex-coloured geometry, same as the mountains -- there is
            // no texture to convert here, so this is a procedural stand-in for the same visual
            // effect batches 1-3's baked sprite mottling gives everything else.
            val hillHighlight = ColorUtils.blendARGB(color, 0xFFFFFFFF.toInt(), 0.12f)
            val gradientBottom = layerTop + layerHeight * 0.35f

            // Draw the shadow + fill at the tile position *and* its immediate wrap-neighbors --
            // same "-1, 0, +1" pattern every other layer in this file already uses (mountains,
            // clouds, objects), which hills never got. That was a real, reproducible bug, not
            // just the long-uptime float-precision one v51 already fixed: this path is *exactly*
            // one tileWidth wide (built to span `screenWidth * 2f` in buildBaseHillPath) and
            // wrappedShift ranges over a full tileWidth too (-tileWidth, 0]. Full-screen coverage
            // from a single copy only holds when wrappedShift >= -0.5*screenWidth -- just the
            // first quarter of every wrap cycle -- so for the other three-quarters (reachable
            // within roughly 10 minutes at the default scroll speed, not some extreme edge case),
            // the path's right edge fell short of the screen's right edge entirely, leaving raw
            // sky/background visible with nothing drawn there -- exactly the reported "hills cut
            // off, sky visible on the right" bug. Three copies, exactly like every other layer,
            // guarantees full coverage at *any* wrappedShift.
            for (tileOffset in -1..1) {
                val offsetShift = wrappedShift + tileOffset * tileWidth
                // The path's own local bounds are [-0.5*screenWidth, 1.5*screenWidth] (built to
                // span tileWidth = screenWidth*2 in buildBaseHillPath) -- skip only when that
                // range, after translation, falls entirely outside the visible screen.
                if (offsetShift + 1.5f * screenWidth < 0f || offsetShift - 0.5f * screenWidth > screenWidth) continue

                canvas.save()
                canvas.translate(offsetShift, 6f)
                shadowPaint.alpha = 30
                canvas.drawShape(path, shadowPaint)
                canvas.restore()

                canvas.save()
                canvas.translate(offsetShift, 0f)
                canvas.drawVerticalGradientShape(
                    path, layerTop, gradientBottom, hillHighlight, color, 255,
                )
                canvas.restore()
            }
        }
    }

    /**
     * Builds one hill layer's skyline as a true sine wave, wide enough to cover two
     * screen-widths, anchored at the wrappedShift=0 reference position. The skyline is
     * `(1 - amp) + amp * sin(f * 4π)`: a perfectly smooth, perfectly periodic wave (2 full cycles
     * across one hill tile), not independent random rolls per segment smoothed with bezier
     * curves. The previous per-segment-random approach, even after narrowing its range in the
     * v49 pass, could still land two adjacent segments' rolls asymmetrically and read as an
     * irregular bump rather than a smooth rolling wave -- a real wave is smooth by construction,
     * an approximation of one built from independent random samples never quite is. This is the
     * "hills not harmonious enough" fix: an actual sine, sampled densely, rather than another
     * attempt to tune randomness into looking like one.
     *
     * `centerFraction`/`maxAmpFraction` reproduce the exact same `[0.04, 0.22]` bounds
     * ([SceneSpace.HILL_SOLID_TOP_DEPTH_FRACTION]'s own derivation depends on this range staying put) at
     * `hillsVariation = 1`: `0.13 ± 0.09`. Parallax scrolling is applied later via
     * canvas.translate() rather than baked into the path coordinates, so this only needs to run
     * once per theme/size change instead of every frame.
     */
    /** Derives one of the 3 hill layers' shade from a single user-chosen base color -- farther
     * layers stay closer to the base color, nearer layers blend progressively toward black,
     * matching the app's existing "farther = lighter" depth convention (and closely approximating
     * the ratios each built-in theme's own original hand-authored 3-color palette already used,
     * e.g. sunset's day palette darkens by roughly 10%/25% from its farthest to nearest layer). */
    /** With [layerCount] now 1, this always darkens by 0 (layer index 0) -- i.e. it's a pass-
     * through to the single user-picked color, not actually darkening anything. Kept as a
     * function (rather than inlined away) only so a future reintroduction of multiple layers has
     * an obvious place to restore per-layer darkening, without it silently doing nothing today. */
    private fun hillLayerColor(baseColor: Int, layer: Int): Int {
        val darkenAmount = floatArrayOf(0f).getOrElse(layer) { 0f }
        return ColorUtils.blendARGB(baseColor, 0xFF000000.toInt(), darkenAmount)
    }

    private fun buildBaseHillPath(path: SceneShape, layer: Int, top: Float, height: Float) {
        path.reset()
        val width = screenWidth * 2f
        val startX = -screenWidth * 0.5f

        // "Hills Variation" (user-editable, 0..1) scales the sine's amplitude:
        // variation=1 gives the full [0.04, 0.22] range, variation=0 collapses the amplitude
        // to 0 (a perfectly flat hill at the center line). Never scaled *up* past 1 here
        // specifically so it can never exceed the proven-safe range -- the UI clamps to 0..1 too.
        val v = hillsVariation.coerceIn(0f, 1f)
        val centerFraction = 0.13f
        val maxAmpFraction = 0.09f
        val amp = maxAmpFraction * v
        // A small per-layer/theme phase offset so a theme with (hypothetically, in the future)
        // more than one layer doesn't render every layer's wave perfectly in sync -- harmless
        // no-op today since layerCount is 1, kept for that reason (same spirit as hillLayerColor).
        val phase = (layerSeed(layer) % 628L) / 100f

        path.moveTo(startX, top + height)
        // 2 full sine cycles per tile, matching the reference exactly, and sampled densely (64
        // points) for a smooth curve -- cheap here since this whole path is cached and only
        // rebuilt on theme/size change, not per frame.
        val samples = 64
        for (i in 0..samples) {
            val f = i / samples.toFloat()
            val heightFrac = centerFraction + amp * sin(f * 4f * kotlin.math.PI.toFloat() + phase)
            val x = startX + f * width
            val y = top + height * heightFrac
            path.lineTo(x, y)
        }
        path.lineTo(startX + width, top + height)
        path.close()
    }
}
