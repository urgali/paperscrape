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
     * contributing inputs, matching a reference app's own decompiled source (its `scrollSpeed`
     * multiplies a per-frame time delta in a classic `onUpdate(float f)` game-loop pattern, a
     * genuinely different mechanism from swipe-driven parallax): a continuous drift that always
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
    // comments for exactly how each is blended with the theme's own manual settings. Set from
    // PaperWallpaperService's hourly WeatherRepository fetch; null whenever Live Weather is off,
    // no location is available yet, or the last fetch failed (falls back to the theme's own
    // manual precipitation/clouds settings in all of those cases, never leaves the scene showing
    // stale weather from hours ago).
    var liveWeatherOverride: com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot? = null

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

    private var objectRenderer = SceneObjectRenderer(SceneObjectCatalog.layoutFor(theme.id, theme.accentColor), sceneCustomization, context)
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
    // A single layer now, not three -- the reference app's own `Hills` is one silhouette with one
    // scroll rate, and its own randomized top edge only ever wobbles gently within the topmost
    // slice of its own height (see buildBaseHillPath's doc comment below for the exact numbers,
    // measured from the decompiled reference source). The previous 3-stacked-band version, with
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
     * hills with no gap, at every x -- see [drawMountains]'s own doc comment for the reasoning and
     * the reference app comparison. */
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
        const val PRECIPITATION_POOL_SIZE = 90
        const val BIRD_POOL_SIZE = 6
        const val FALLING_LEAF_POOL_SIZE = 26
        const val MOUNTAIN_POOL_SIZE = 4
        const val LAKE_DECORATION_POOL_SIZE = 4

        /** Lake sparkles have no density control -- a fixed handful, always drawn. */
        const val LAKE_SPARKLE_POOL_SIZE = 5

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
         * nearer tiers the reverse. Matching the reference's own four-layer rotation in spirit
         * rather than reusing this file's mountain/hill layer identities, which carry no
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

        /** How far a star sprite reaches left of, and right of, the star's own x.
         *
         * Symmetric, because `star_sparkle.png` is drawn as a [SpriteScale.SCENE_UNITS] sprite:
         * its 180px cover `180 / SPRITE_PIXELS_PER_UNIT = 60` local units, and the origin `-30`
         * puts the bitmap's centre on the star, so the sprite reaches `0.9375 * radius`. These
         * constants deliberately reserve the full radius instead: over-reserving costs a
         * comparison, and under-reserving would drop a tile copy at a seam. The sprite has no
         * transparent margin left to distinguish bitmap from content, so the two readings that
         * used to differ now coincide.
         *
         * They exist as named constants, rather than being folded into the tile bounds, because
         * the tile range must be derived from what is actually drawn: they were asymmetric while
         * the sprite was blitted with the wrong scale convention, and a test asserts them so a
         * future change to either the asset or the convention has to come back through here. */
        const val STAR_SPRITE_LEFT_EXTENT_PX = MAX_STAR_RADIUS_PX
        const val STAR_SPRITE_RIGHT_EXTENT_PX = MAX_STAR_RADIUS_PX

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
         * The sleigh's scale and origin, in the units its own `canvas.scale` establishes.
         *
         * `santa_sleigh_scene` is 624x168 with a content box of (12,12)-(610,159), so 199.33 x 49
         * local units of drawing inside a 208 x 56 canvas. The scale keeps the on-screen width
         * the shipped release had; the origins put the content's centre on the flight point.
         */
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

        /** Where a bolt starts and how far down it reaches, as fractions of screen height. */
        const val LIGHTNING_BOLT_TOP_FRACTION = 0.08f
        const val LIGHTNING_BOLT_MIN_HEIGHT_FRACTION = 0.26f
        const val LIGHTNING_BOLT_HEIGHT_SPREAD_FRACTION = 0.14f

        /**
         * The horror sky's four corners: near-black overhead, hard orange at the horizon.
         *
         * Flat paper colours, not a photographic gradient -- the orange is one saturated tone and
         * the black is one, with the blend between them doing all the work. The day pair is
         * lighter than the night pair only enough to keep sunrise and sunset legible; this sky is
         * meant to look wrong at noon, which is the point of it.
         */
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

        const val SANTA_SLEIGH_SCALE = 1.5f
        const val SANTA_SLEIGH_ORIGIN_X_UNITS = -99.67f
        const val SANTA_SLEIGH_ORIGIN_Y_UNITS = -25.5f

        /**
         * How fast the sleigh's two leg poses alternate, in poses per second.
         *
         * `SceneTime.frameIndex` multiplies by this, so it is a rate and not a duration: 4.5
         * holds each pose for about a fifth of a second. Fast enough to read as a trot, slow
         * enough not to blur into one shape at the 30 fps the renderer paces at.
         */
        const val SANTA_TROT_FRAMES_PER_SECOND = 4.5f

        /**
         * Centres the 90x42 bird on its own position, in canvas pixels.
         *
         * The sprite is authored at its on-screen size, so there is no divisor here and none is
         * wanted: the 90 px width *is* the wingspan.
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
        /**
         * How many lanes the lake's usable band is cut into.
         *
         * Even, so alternating them splits cleanly between the two categories; six gives each
         * three lanes spread across the whole surface rather than three bunched in half of it.
         */
        const val LAKE_LANE_COUNT = 6

        const val STAR_SPARKLE_EVERY = 5

        /** The cream the sparkle art is drawn in, so a point and a sparkle are the same star. */
        const val STAR_POINT_COLOR = 0xFFFFF6DC.toInt()

        /**
         * How much of a star's radius a point covers.
         *
         * The sparkle sprite reaches 0.94 of the radius at its four tips but is much narrower
         * between them, so a disc of the same radius would read as a noticeably fatter star. This
         * matches its apparent weight instead of its extent.
         */
        const val STAR_POINT_RADIUS_SCALE = 0.55f

        const val DOLPHIN_LEAP_TILT_DEGREES = 26f

        /**
         * Where `dolphin_body`'s pixel (0,0) goes so the animal's own content is centred on the
         * point its leap arc is computed for.
         *
         * The sprite is 360x225 px -- 120x75 local units -- with content spanning x 0..114.7 and
         * y 12..70, so its content centre sits at (57.3, 41).
         */
        const val DOLPHIN_ORIGIN_X_UNITS = -57.3f
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
        const val BIRD_SPRITE_ORIGIN_X_PX = -45f
        const val BIRD_SPRITE_ORIGIN_Y_PX = -15f

        /** Centres a 240px raw-pixel disc in the `radius / 120f` space of the sun and moon. */
        const val CELESTIAL_DISC_ORIGIN_UNITS = -120f
        val CELESTIAL_DISC_SCALE = SpriteScale.CANVAS_PIXELS

        /** Centres the 396px sunburst in that same space, putting its ray ring at 150..198
         * units -- outside the disc's own 120, which is what makes it read as a sunburst.
         *
         * The sprite was 444px with 24px of transparent margin per side until the padding
         * normalisation; the rays are in the same place, because the origin moved by exactly the
         * margin that was removed. */
        const val SUN_GLOW_ORIGIN_UNITS = -198f
        val SUN_GLOW_SCALE = SpriteScale.CANVAS_PIXELS

        /** Centres the 180px sparkle in the `star.radius / 32f` space of a star. Read as an
         * oversampled sprite it covers 60 units, so it reaches 0.9375 of the star's own radius --
         * which is what the artwork always reached: the sprite was 192px with 6px of transparent
         * margin per side, and the padding normalisation removed the margin and moved the origin
         * by the same 2 units. */
        const val STAR_SPRITE_ORIGIN_UNITS = -30f
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

    fun draw(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime, deltaSeconds: Float) {
        // One direction only, per explicit request (matches the reference app's own always-
        // forward drift) -- full screen-width drift every ~25s at scrollSpeed=1.0;
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
        drawClouds(canvas, dayPhase, elapsedSeconds)
        // Behind the mountains/hills on purpose (see drawRainbow's own doc comment) -- drawn
        // right after clouds, before anything that should occlude its base.
        drawRainbow(canvas, dayPhase)
        drawMountains(canvas, dayPhase)
        drawBirds(canvas, dayPhase, elapsedSeconds)
        // Lake drawn *before* hills now -- matching the reference app's own depth order
        // (mountains, farthest -> water -> hills/ground, nearest). Hills (and everything
        // standing on them, drawn right after) now naturally paint over whatever part of the
        // water their own wavy silhouette covers in a given column, which is what makes the
        // water read as sitting *behind* the hills instead of a flat rectangle slicing across
        // them (the previously reported "hills look cut" bug -- the old order drew the lake
        // *after* hills, so the lake's flat edge cut across the hills' organic one instead of
        // the other way around).
        drawLake(canvas, dayPhase, elapsedSeconds)
        drawHillLayers(canvas, dayPhase)
        objectRenderer.update(deltaSeconds)
        objectRenderer.draw(canvas, objectGroundGeometry, dayPhase.dayBlend, elapsedSeconds, screenWidth.toFloat(), screenHeight.toFloat())

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
            // 60 % transparent. V2 redraws it at 624x168 on the authoring grid, which makes it a
            // SCENE_UNITS sprite and retires both numbers. **The manifest's SCENE_UNITS is right
            // and the shipped call site's CANVAS_PIXELS was the stale half**; the manifest's
            // declared anchor is taken as given here.
            //
            // [SANTA_SLEIGH_SCALE] holds the on-screen width where it was: the content box is
            // 598px wide, so 199.33 local units at 1.5 is the 298.8px the old pair produced.
            // The origin centres the *content* on the flight point, which the old one did not --
            // it sat 95px right and 130px below it, so the gifts this effect drops appeared to
            // spawn above the sleigh rather than out of it.
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
        // Live Weather override applies here too -- a real thunderstorm should flash regardless
        // of the theme's own manual "Thunderstorm" toggle, same reasoning as drawPrecipitation's
        // own override (see its doc comment).
        val stormActive = liveWeatherOverride?.isThunderstorm ?: (
            sceneCustomization.precipitation.visible &&
                sceneCustomization.precipitation.type == PrecipitationType.RAIN &&
                sceneCustomization.precipitation.thunderstorm
            )
        updateLightning(deltaSeconds, stormActive)
        drawLightningFlash(canvas)
    }

    private fun blendColor(night: Int, day: Int, blend: Float): Int =
        ColorUtils.blendARGB(night, day, blend.coerceIn(0f, 1f))

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
            canvas.drawVerticalGradientRect(
                0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), horrorTop, horrorBottom,
            )
            return
        }

        canvas.drawVerticalGradientRect(
            0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), top, bottom,
        )
    }

    /**
     * Sprite-blit conversion (batch 4 part 3): the reference's own `Star` class genuinely blits
     * a texture (a 4-pointed sparkle for most stars, a plain small dot for the rest -- see
     * `SpriteSheet.Sprite.starsmall`/`starcircle`), not a plain filled circle. Bumped the radius
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
            val s = star.radius / 32f
            canvas.scale(s, s)
            // star_sparkle.png is authored at the SPRITE_PIXELS_PER_UNIT oversample: 180px cover
            // 60 local units, so [STAR_SPRITE_ORIGIN_UNITS] centres it on the star and it reaches
            // 0.9375 of the star's radius -- the extent the artwork always had, once its
            // transparent margin per side is discounted. It was blitted as CANVAS_PIXELS until
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
        if (isSun && !sceneCustomization.sun.visible) return
        if (!isSun && !sceneCustomization.moon.visible) return

        val margin = screenWidth * CELESTIAL_MARGIN_FRACTION
        val cx = margin + dayPhase.celestialX * (screenWidth - 2 * margin) + offsetX
        val horizonY = screenHeight * 0.62f
        val riseHeight = screenHeight * sceneCustomization.sky.sunCloudHeight.coerceIn(0.1f, 0.6f)
        val cy = horizonY - dayPhase.celestialY * riseHeight

        // doubled -- was too small to read clearly
        val radius = screenWidth * CELESTIAL_RADIUS_FRACTION * 2f
        val color = if (isSun) sceneCustomization.sun.color else sceneCustomization.moon.color

        canvas.drawRadialGlow(cx, cy, radius * 3.2f, color, CELESTIAL_GLOW_CENTRE_ALPHA)

        if (isSun) {
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
            // sun_glow.png is 396x396 and its rays sit in a ring 150..198px from its own centre,
            // so as a raw-pixel sprite it covers 396 local units and has to be anchored at
            // -396/2 for that ring to land at 150..198 units -- outside the disc's own 120, which
            // is what makes it read as a sunburst. It was 444x444 with 24px of transparent margin
            // per side until the padding normalisation: the ring is measured from the sprite's
            // own centre, so removing a symmetric margin left it exactly where it was.
            // It was anchored at -74 until v73.7: that is -(444/2)/SPRITE_PIXELS_PER_UNIT, the
            // origin an oversampled sprite would want, and it hung the rays off the disc's lower
            // right. The oversampled reading is not the alternative it looks like: it would put
            // the ring at 50..66 units, entirely hidden behind the disc.
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
            )
            sprites.draw(
                canvas,
                R.drawable.sun_body,
                CELESTIAL_DISC_ORIGIN_UNITS,
                CELESTIAL_DISC_ORIGIN_UNITS,
                CELESTIAL_DISC_SCALE,
            )
            canvas.restore()
        } else {
            drawMoonWithPhase(canvas, cx, cy, radius, color)
        }
    }

    /**
     * Sprite-blit conversion (batch 4 part 3): the reference's own `Moon` class assigns one of 8
     * distinct hand-drawn phase silhouettes (`moonnew`/`mooncres`/`moonhalf`/`moongib`/
     * `moonfull`, the waning half reusing the waxing shapes rotated 180°) rather than computing
     * an ellipse-width approximation at runtime. Replaces the old "half-disc + variable-width
     * terminator ellipse" geometric technique with 4 baked shapes (crescent/half/gibbous/full)
     * reused the same way for the waning side via a 180° rotation -- same trick the reference
     * itself uses, not just visually similar. Thresholds on `illuminated` (already computed
     * below, unchanged from the old technique) stand in for the reference's 8-bucket enum.
     */
    private fun drawMoonWithPhase(canvas: SceneCanvas, cx: Float, cy: Float, radius: Float, litColor: Int) {
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
                litColor,
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

        val phase = SunPositionCalculator.moonPhase()
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
        // 180° reuse for the waning half -- same shapes, mirrored, exactly like the reference's
        // own MoonPhase enum does (ThirdQuarter/WaningCrescent/WaningGibbous all reuse the
        // waxing sprites with `angle = 180f`).
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
     * Sprite-blit conversion (batch 4 part 3): the reference's own `Bird` class genuinely blits
     * a texture (`SpriteSheet.Sprite.birdup`) and animates the wing flap by flipping `sy`'s sign
     * every few frames (a mirror flip, not a continuously-bent curve) -- replaced the old
     * per-frame quad-bezier wing path with a single baked "wings up" sprite, vertically flipped
     * for the "wings down" half of the flap cycle via the same sign-flip trick the reference
     * itself uses, instead of a separate second frame.
     */

    private fun drawBirds(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime) {
        val birds = sceneCustomization.birds
        if (!birds.visible) return
        // Fade out toward night unless the user explicitly wants birds flying after dark too.
        val nightVisibility = if (birds.nightBirds) 1f else dayPhase.dayBlend.coerceIn(0f, 1f)
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
     * clouds never fully covering the sky even at 100% density. The reference's own decompiled
     * `Cloud` class settles the first one directly: it's a plain `TwoColorModel` with a fixed
     * day/night color pair and no density-dependent blending at all -- so the "darken toward
     * black as density climbs" behaviour this used to have (meant to read as a storm) was never
     * how the reference does it, and is removed below in favor of the same flat color at any
     * density.
     *
     * The second bug's *exact* reference formula couldn't be recovered -- the method that
     * actually creates and places clouds (`Scene.addCloudsAndBalloons`) is present in the
     * decompiled source but its body wasn't decompilable (tried twice, once with a
     * deobfuscation pass, both times jadx reported "Method not decompiled" for it) -- so this
     * isn't a literal port for the coverage fix, just an improvement built from what *was*
     * recoverable from `Cloud`'s own constructor (a `mHeightRand`-driven vertical jitter within a
     * band, `mUnmoving` clouds evenly spread by index, no other count/spacing formula visible),
     * plus a real reference screenshot aa provided showing a full-density sky: a handful of large
     * lobed cloud masses overlapping into one continuous band, not many small separate puffs
     * across several rows. An earlier version of this fix went the opposite direction (36 small
     * candidates across 3 stacked rows) before that screenshot was available -- fewer, larger
     * candidates in a single row reproduces the reference's actual look much more directly, and
     * still closes gaps at high density since each candidate is bigger and overlaps its
     * neighbors more, not because there are more of them.
     */
    /**
     * Cloud placement, ported from the reference's real decompiled `Scene.addCloudsAndBalloons`
     * (recovered via CFR after jadx reported "Method not decompiled" for it -- see this file's
     * own notes on that): count is `numClouds * 40 + 1` there, and each cloud gets assigned one
     * of 4 depth layers in rotation (`{Clouds, Mountain1, Mountain2, Hills}`, cycling by index),
     * not one shared depth for all of them.
     *
     * Count used to be scaled down from that literal formula for a concrete, learned-the-hard-way
     * reason: each cloud was a hand-built path via 4 `Path.op(..., UNION)` boolean operations
     * plus a clip and an outline stroke, all real per-frame `Canvas` cost -- cheap on the
     * reference's actual GPU sprite pipeline (drawing a single textured quad per cloud), not
     * cheap doing dozens of path booleans a frame here. Batch 4 part 2 converted [drawPuffyCloud]
     * to a single tinted sprite blit (see its own doc comment) -- the same category of cost the
     * sprite-blit pilot eliminated for houses/trees/buildings/cars -- so that constraint no
     * longer applies here either; count now goes right up to the reference's own ~41-cloud
     * maximum instead of staying deliberately capped below it.
     * The depth-layer *variety* is kept (4 tiers below, each with its own parallax/size/vertical
     * offset), matching the reference's own 4-layer rotation.
     */
    /**
     * Live Weather override: only blends into the *density* when the theme's own Clouds toggle
     * is already on -- unlike precipitation (a much stronger "is it raining or not" weather
     * signal), whether a given theme shows clouds *at all* is treated as an artistic per-theme
     * decision aa is free to keep off (e.g. a deliberately clear desert theme), so Live Weather
     * only adjusts how many clouds show once that decision has already opted in, not whether any
     * appear.
     */
    private fun drawClouds(canvas: SceneCanvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime) {
        val clouds = sceneCustomization.clouds
        cloudCoverage.beginFrame()
        if (!clouds.visible) {
            // Turning the cloud layer off must not also turn precipitation off. With no clouds to
            // derive a field from, treat the sky as uniformly covered so intensity governs alone,
            // exactly as it did before coverage existed.
            cloudCoverage.setUniform()
            return
        }
        val density = (liveWeatherOverride?.cloudCoverFraction ?: clouds.density).coerceIn(0f, 1f)
        cloudPaint.color = blendColor(clouds.colorNight, clouds.colorDay, dayPhase.dayBlend)
        cloudPaint.alpha = 255

        // aa reported clouds too small and, even at 100% density, not actually covering the sky.
        // Compared against the reference's decompiled Cloud/Scene.addCloudsAndBalloons: at full
        // density it places ~41 heavily-overlapping clouds spread evenly across the *whole*
        // width, deliberately overlapping enough to form a solid blanket. Radius (68f*scale)
        // already matches that; count now goes to 41 too (was capped at 36) now that
        // [drawPuffyCloud] is a cheap sprite blit instead of 4 per-frame Path.op booleans -- see
        // this function's own doc comment above for why that cap no longer needs to exist.
        // Fixed pool: density now selects from a constant set of slots instead of also deciding
        // how many slots exist. When the count moved with the slider, every cloud's
        // `(i + 0.5) / candidateCount` position moved with it, so adjusting density relocated the
        // whole sky rather than thinning it.
        val effectOffset = CandidateThreshold.offsetFor(EffectId.CLOUDS)
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(density, CLOUD_POOL_SIZE, effectOffset)
        val seed = seedFor(EffectId.CLOUDS)
        val tileWidth = screenWidth * 2f

        val bandTop = screenHeight * (0.08f + (1f - sceneCustomization.sky.sunCloudHeight) * 0.15f)
        val bandHeight = screenHeight * 0.16f

        for (i in 0 until CLOUD_POOL_SIZE) {
            if (!CandidateThreshold.isPresent(i, density, effectOffset, fallbackIndex)) continue

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
                drawPuffyCloud(canvas, x, laneY, scale)
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
    private fun drawPuffyCloud(canvas: SceneCanvas, cx: Float, cy: Float, scale: Float) {
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(scale, scale)
        sprites.drawTinted(canvas, R.drawable.cloud_body, -128f, -85f, SpriteScale.SCENE_UNITS, cloudPaint.color)
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
     * Compared against the reference's decompiled RainDrop.java: it resets each drop to
     * `mMaxDropHeight = scene.baseCloudY` -- the clouds' own anchor line, not some point above
     * them -- and fades alpha in over the first 10% of the fall and out over the last 10%
     * (`FADE_RANGE = 0.1f`) rather than popping in/out at full opacity. This file's own
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
        precipPaint.color = if (isRain) {
            blendColor(precip.rainColorNight, precip.rainColorDay, dayPhase.dayBlend)
        } else {
            blendColor(precip.snowColorNight, precip.snowColorDay, dayPhase.dayBlend)
        }

        val effectOffset = CandidateThreshold.offsetFor(EffectId.PRECIPITATION)
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(intensity, PRECIPITATION_POOL_SIZE, effectOffset)
        val seed = seedFor(EffectId.PRECIPITATION)
        // Rain still falls at a brisk pace; snow was falling noticeably faster than real snow
        // drifts (0.35 meant a full screen-height fall in under 3 seconds) -- slowed down
        // significantly so it reads as a gentle drift instead of a downpour.
        val fallSpeed = if (isRain) 1.3f else 0.09f
        // Same band-center line drawClouds' own `laneY` is built around (bandTop + bandHeight*0.5)
        // -- see this function's own doc comment for why this replaced the old bandTop-only origin.
        val cloudBandTop = screenHeight * (0.08f + (1f - sceneCustomization.sky.sunCloudHeight) * 0.15f)
        val cloudBandHeight = screenHeight * 0.16f
        val fallStartY = cloudBandTop + cloudBandHeight * 0.5f
        val fallRange = (screenHeight + 40f) - fallStartY
        // Paint state that does not vary between drops, set once instead of once per drop.
        // `isRain` is fixed for the whole call, so the style, stroke width and cap were being
        // rewritten identically up to PRECIPITATION_POOL_SIZE times a frame. Only the alpha and
        // the geometry actually change per drop, and those stay in the loop.
        if (isRain) {
            precipPaint.style = Paint.Style.STROKE
            precipPaint.strokeWidth = 2f
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
            val sway = if (isRain) 0f else elapsedSeconds.sinAt(1.3f, phase * 6.28f) * 14f
            val x = xFraction * screenWidth + sway

            // Fade in over the first 10% of the fall and out over the last 10%, matching the
            // reference's own FADE_RANGE -- this alone is most of what sells "emerging from the
            // cloud layer" rather than popping into existence mid-air.
            val fadeRange = 0.1f
            val fadeAlpha = when {
                fallFraction < fadeRange -> fallFraction / fadeRange
                fallFraction > 1f - fadeRange -> (1f - fallFraction) / fadeRange
                else -> 1f
            }.coerceIn(0f, 1f)

            if (isRain) {
                precipPaint.alpha = (190 * fadeAlpha).toInt()
                val len = CandidateNoise.range(seed, i, CandidateNoise.CH_LENGTH, 16f, 26f)
                canvas.drawLine(x, y, x - len * 0.25f, y + len, precipPaint)
            } else {
                precipPaint.alpha = (220 * fadeAlpha).toInt()
                val r = CandidateNoise.range(seed, i, CandidateNoise.CH_WIDTH, 2f, 4.5f)
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
     * Falls only as far as the road/ground level (not off the bottom of the screen) since that's
     * as far as an actual falling leaf needs to travel to "land".
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
        val candidateCount = FALLING_LEAF_POOL_SIZE
        val fallSpeed = 0.06f
        val fallStartY = screenHeight * (yOffsets[0] - 0.03f)
        val fallEndY = screenHeight * 0.88f
        val fallRange = fallEndY - fallStartY
        for (i in 0 until candidateCount) {
            val xFraction = CandidateNoise.value(seed, i, CandidateNoise.CH_X)
            val speedVariance = CandidateNoise.range(seed, i, CandidateNoise.CH_VARIANCE, 0.7f, 1.3f)
            val phase = CandidateNoise.value(seed, i, CandidateNoise.CH_PHASE)
            val fallFraction = elapsedSeconds.cycle(fallSpeed * speedVariance, phase)
            val y = fallStartY + fallFraction * fallRange
            val sway = elapsedSeconds.sinAt(0.9f, phase * 6.28f) * 26f
            val x = xFraction * screenWidth + sway
            val spin = elapsedSeconds.cycleOf(60f * (0.5f + speedVariance * 0.5f), phase * 360f, 360f)
            val color = palette[i % palette.size]
            // Fade in leaving the canopy, fade out settling near the ground -- same polish
            // drawPrecipitation's own fade already uses, for the same "doesn't just pop into/out
            // of existence" reason.
            val fadeRange = 0.12f
            val fadeAlpha = when {
                fallFraction < fadeRange -> fallFraction / fadeRange
                fallFraction > 1f - fadeRange -> (1f - fallFraction) / fadeRange
                else -> 1f
            }.coerceIn(0f, 1f)
            leafPaint.color = blendColor(ColorUtils.blendARGB(color, 0xFF000000.toInt(), 0.35f), color, dayPhase.dayBlend)
            leafPaint.style = Paint.Style.FILL
            leafPaint.alpha = (220 * fadeAlpha).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(x, y)
            canvas.rotate(spin)
            canvas.drawOval(-4f, -6f, 4f, 6f, leafPaint)
            canvas.restore()
        }
    }


    /** Advances the thunderstorm's lightning timer/fade. Only ticks (and can fire) while [enabled]
     * -- when precipitation is off, not raining, or the storm toggle is off, the flash simply
     * fades out and stops, it never fires while disabled. */
    private fun updateLightning(deltaSeconds: Float, enabled: Boolean) {
        if (enabled) {
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
        lightningPaint.alpha = (180 * lightningFlashAlpha).toInt().coerceIn(0, 255)
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), lightningPaint)

        // The sprite is 102x252 -- 34x84 local units -- hanging from its own top edge, so the
        // scale that gives it the rolled height is that height over 84, and the origin puts its
        // top at the cloud band and centres it on the rolled x.
        val boltHeight = screenHeight * lightningBoltHeightFraction
        val scale = boltHeight / LIGHTNING_BOLT_HEIGHT_UNITS
        canvas.save()
        canvas.translate(screenWidth * lightningBoltXFraction, screenHeight * LIGHTNING_BOLT_TOP_FRACTION)
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
        // wherever the hill's own wavy edge happened to dip lower that frame (confirmed against
        // the reference app's own decompiled source: `Mountain.onSceneSizeChanged` sets
        // `y = mScene.mountainBottomY`, and `mountainBottomY = max(hillsVisibleBottomY,
        // waterVisibleTopY-if-lake)` -- `hillsVisibleBottomY` there is deliberately the hill's own
        // *worst-case-covered* line, not its peak, i.e. the deepest the top edge can ever reach).
        // [SceneSpace.HILL_SOLID_TOP_DEPTH_FRACTION] is exactly that same "always-solid, whatever the roll"
        // fraction already derived and proven for object row placement -- reusing it here (instead
        // of inventing a second, inconsistent constant) guarantees mountains/lake always connect
        // directly into the hills with zero gap, at every x, matching the reference app's own
        // approach. Verified with a rendered mock of both the old and new anchor before this edit.
        val effectiveBaseYFraction =
            if (updateLakeBandY()) lakeBandTopY / screenHeight else hillGuaranteedTopFraction

        // Sized directly from the reference app's own decompiled `Scene.java` (the code that
        // creates its `Mountain` objects), not guessed from screenshots: back mountains there get
        // `sy` (height) in `[0.8,1.2] * 0.15` and `sx` (width) in `[0.8,1.2] * 0.25` of `mSizeH`
        // (its own normalized unit, which equals screen *height* in portrait -- confirmed from
        // `SceneBase.setupScreenSizes()`). The previous version of this comment converted that
        // 0.25/0.15≈1.67 width:height ratio into PaperScrape's own widthFraction-of-*screen-width*
        // convention by reusing the *old* (too-tall) 0.60/0.29≈2.07 ratio -- which was wrong,
        // baked in the exact same error that made the old mountains too tall, and produced
        // mountains far narrower than the reference (the reported "too narrow" bug). Fixed by
        // computing width the same way height already is -- as a fraction of screenHeight, since
        // that's what the reference's `sx`/`sy` both actually are (fractions of the *same* unit)
        // -- removing the error-prone width-of-screenWidth conversion entirely rather than
        // re-deriving it correctly by hand. `widthOfHeightFraction` below is `sx`'s own average
        // (0.25 back, 0.175 front, both *0.7 for front matching the reference's own scaling)
        // directly, no conversion needed.
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
        // Fully opaque -- the back layer used to render at alpha 200 as a cheap depth cue, but
        // that's not how the reference app does it (its own `Mountain` model is a plain solid
        // two-color shape, no alpha blending at all) and it caused a real, reported bug: with the
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
     * mountains got smaller (matching the reference's own real proportions), the same 8-segment
     * discretization became a visibly angular "shoulder" instead of the reference's smooth round
     * cap. Fixed by sampling evenly spaced in *width* instead (`x` from 0 to `halfWidth`, deriving
     * `t = x²` since that's `√t`'s own inverse) -- points naturally bunch up near the peak where
     * the curve bends fastest, giving a properly round tip at the same segment count.
     *
     * **Batch 4 aesthetic pass**: filled as two halves sharing the exact same peak/base points
     * (so there's no seam) rather than one flat-color fill -- the reference's own `Mountain`
     * class is genuine vertex-colored GL geometry with no texture (decompiled and confirmed, see
     * this file's own [drawMountains] doc comment), so there's no sprite to convert to here, but
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
     * Shared by [drawLake] and [drawMountains] -- matching the reference app's own architecture,
     * where mountains' base is dynamically computed as `max(hillsReference, waterTopIfLakesOn)`
     * rather than a fixed guess independent of wherever the water actually is. `false` means the
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
        // from 0.20) -- v49 shrank the mountains to match the reference's real, much smaller
        // proportions, but left this cap alone, so at max Lake Height the lake's top edge could
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
        // at either edge, for any wrap value in (-screenWidth, 0].
        for (tileOffset in -1..1) {
            drawLakeBand(canvas, (tileOffset * screenWidth).toFloat(), top, bottom, bandHeight, elapsedSeconds)
        }
        canvas.restore()

        if (lake.sailboatsVisible) {
            drawLakeDecorations(canvas, top, bandHeight, elapsedSeconds, lake.sailboatsDensity, seedSalt = EffectId.SAILBOATS, isDolphin = false)
        }
        if (lake.dolphinsVisible) {
            drawLakeDecorations(canvas, top, bandHeight, elapsedSeconds, lake.dolphinsDensity, seedSalt = EffectId.DOLPHINS, isDolphin = true)
        }
    }

    /** One screen-width-wide copy of the lake's water band + ripple lines, offset horizontally
     * by [xOffset] -- see [drawLake], which draws 3 of these side by side under one translate.
     *
     * Reworked after aa reported the lake reading as too plain/flat (a solid rectangle with 4
     * faint lines). Top edge stays a flat straight line -- that's deliberate, not part of what
     * needed fixing (see the comment kept below on why: it's what keeps the mountains' fixed
     * base line from ever opening a sky gap against the lake's edge) -- but the water's own
     * surface now reads as actual moving water: alternating light/dark horizontal bands (like
     * light catching ripples at different depths) instead of one flat fill, plus small drifting
     * sparkle glints near the surface.
     */
    private fun drawLakeBand(canvas: SceneCanvas, xOffset: Float, top: Float, bottom: Float, bandHeight: Float, elapsedSeconds: SceneTime) {
        // A plain flat rectangle now, not the wavy top edge an earlier version had. That waviness
        // was meant to blend the lake's far edge into the hills' own organic silhouette -- but
        // once the lake moved to sit *above* the hills entirely (v46), its top edge borders plain
        // sky, not hills, so the waviness no longer served that purpose. Worse, it actively
        // caused a new bug: mountains anchor to the lake's own *nominal* top Y (a single fixed
        // value), but the wavy edge's random per-segment jitter could dip *below* that nominal
        // value at some x positions -- opening a thin sliver of bare sky between the mountain's
        // fixed base and the lake's actual (jittered) edge right there. A flat edge and a fixed
        // mountain base line up exactly, everywhere, always.
        canvas.drawRect(xOffset, top, xOffset + screenWidth, bottom, lakePaint)

        // Alternating light/dark horizontal bands, each gently undulating and drifting sideways
        // at its own slow rate -- reads as actual depth/movement in the water rather than a flat
        // fill with a couple of lines drawn over it.
        val bandCount = 6
        for (i in 0 until bandCount) {
            val fraction = (i + 0.5f) / bandCount
            val y = top + bandHeight * fraction
            val bandThickness = bandHeight / bandCount * 0.55f
            val lighten = if (i % 2 == 0) 0.10f else -0.06f
            ripplePaint.style = Paint.Style.FILL
            ripplePaint.color = if (lighten > 0f) {
                ColorUtils.blendARGB(lakePaint.color, 0xFFFFFFFF.toInt(), lighten)
            } else {
                ColorUtils.blendARGB(lakePaint.color, 0xFF000000.toInt(), -lighten)
            }
            ripplePaint.alpha = 90
            val drift = elapsedSeconds.sinAt(0.25f + i * 0.05f, i * 1.3f) * 18f
            canvas.drawRect(xOffset - 20f + drift, y - bandThickness / 2f, xOffset + screenWidth + 20f + drift, y + bandThickness / 2f, ripplePaint)
        }
        ripplePaint.alpha = 255

        // Thin bright ripple lines on top of the bands, same wobble as before.
        ripplePaint.style = Paint.Style.STROKE
        ripplePaint.color = ColorUtils.blendARGB(lakePaint.color, 0xFFFFFFFF.toInt(), 0.16f)
        ripplePaint.strokeWidth = 2f
        val rippleCount = 4
        for (i in 0 until rippleCount) {
            val fraction = (i + 1f) / (rippleCount + 1)
            val y = top + bandHeight * fraction
            val wobble = elapsedSeconds.sinAt(0.6f, i * 1.7f) * 6f
            canvas.drawLine(xOffset + screenWidth * 0.08f + wobble, y, xOffset + screenWidth * 0.92f + wobble, y, ripplePaint)
        }

        // Small sparkle glints drifting across the surface -- a handful of short bright dashes,
        // stateless deterministic candidates same as precipitation/clouds.
        val sparkleCount = LAKE_SPARKLE_POOL_SIZE
        val sparkleSeed = seedFor(EffectId.LAKE_SPARKLES)
        ripplePaint.style = Paint.Style.STROKE
        ripplePaint.strokeWidth = 1.5f
        ripplePaint.strokeCap = Paint.Cap.ROUND
        ripplePaint.color = ColorUtils.blendARGB(lakePaint.color, 0xFFFFFFFF.toInt(), 0.5f)
        for (i in 0 until sparkleCount) {
            val phase = CandidateNoise.value(sparkleSeed, i, CandidateNoise.CH_PHASE)
            val laneFraction = CandidateNoise.range(sparkleSeed, i, CandidateNoise.CH_Y, 0.15f, 0.85f)
            val sy = top + bandHeight * laneFraction
            val drift = elapsedSeconds.cycle(
                CandidateNoise.range(sparkleSeed, i, CandidateNoise.CH_SPEED, 0.03f, 0.05f),
                phase,
            )
            val sx = xOffset + drift * screenWidth
            val twinkle = (elapsedSeconds.sinAt(3f, phase * 6.28f) * 0.5f + 0.5f)
            ripplePaint.alpha = (140 * twinkle).toInt().coerceIn(0, 255)
            canvas.drawLine(sx - 5f, sy, sx + 5f, sy, ripplePaint)
        }
        ripplePaint.alpha = 255
    }

    private fun drawLakeDecorations(
        canvas: SceneCanvas,
        bandTop: Float,
        bandHeight: Float,
        elapsedSeconds: SceneTime,
        density: Float,
        seedSalt: Int,
        isDolphin: Boolean,
    ) {
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
            // The band is instead cut into [LAKE_LANE_COUNT] lanes spanning it top to bottom, and
            // each category draws from alternate ones. Boats take the even lanes and dolphins the
            // odd, so both reach the near edge and the far edge, and two of them can never be
            // placed on the same line. Where inside its lane a candidate sits is still its own
            // noise, so nothing reads as a grid.
            val laneSpan = safeLaneFractionMax - 0.02f
            val laneHeight = laneSpan / LAKE_LANE_COUNT
            val laneIndex = (i * 2 + if (isDolphin) 1 else 0) % LAKE_LANE_COUNT
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

            if (isDolphin) {
                // Batch 4 (terrain sub-group) -- sprite-converted, matching the reference app's
                // own architecture: unlike mountains/hills (procedural vertex-colored geometry
                // even in the decompiled reference, see drawMountains'/drawHillLayers' own doc
                // comments), the reference's real `Dolphin`/`SailboatBottom`/`SailboatSails`
                // classes genuinely blit sprite textures -- so this one (and the sailboat below)
                // get the same treatment batches 1-3 already gave houses/buildings/cars. Same
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
                if (arc <= 0f) {
                    // **The splash is drawn from the same phase the leap is, not from a stored
                    // "was it above water last frame" flag.** `arc` is `sin(theta)` with
                    // `theta = 0.9 * t + phase * 6.28`, so the animal is above the surface for the
                    // first half of each turn of that angle and meets the water again exactly when
                    // the turn passes its half. Expressing the same angle as a 0..1 position gives
                    // the instant directly: everything from 0.5 onwards is time since re-entry.
                    //
                    // Deriving it rather than remembering it is what makes it correct at the
                    // seams. A remembered flag has to be allocated per dolphin, kept across a
                    // surface change and a visibility pause, and is wrong for one frame whenever
                    // the wallpaper resumes mid-leap. This has no state to lose, allocates
                    // nothing in the draw path, and costs one modulo on the frames that are
                    // already skipping the animal.
                    val cyclePosition = elapsedSeconds.cycle(
                        DOLPHIN_LEAP_RATE / TWO_PI,
                        phase * 6.28f / TWO_PI,
                    )
                    val sinceEntry = cyclePosition - 0.5f
                    if (sinceEntry < 0f || sinceEntry >= SPLASH_WINDOW_CYCLES) continue
                    val progress = sinceEntry / SPLASH_WINDOW_CYCLES
                    canvas.save()
                    canvas.translate(x, y)
                    // Sized against the animal that made it, so a far dolphin throws a small
                    // splash and a near one a larger, and the two can only be wrong together.
                    val splashScale = SceneSpace.DOLPHIN_BASE_SCALE * lakeScale
                    canvas.scale(splashScale, splashScale)
                    sprites.draw(
                        canvas,
                        if (progress < SPLASH_FRAME_SPLIT) R.drawable.water_splash0
                        else R.drawable.water_splash1,
                        SPLASH_ORIGIN_X_UNITS,
                        SPLASH_ORIGIN_Y_UNITS,
                        SpriteScale.SCENE_UNITS,
                        (255f * (1f - progress * progress)).toInt().coerceIn(0, 255),
                    )
                    canvas.restore()
                    continue
                }
                val climb = arc * SceneSpace.DOLPHIN_LEAP_METRES * SceneSpace.LAKE_PIXELS_PER_METRE * lakeScale
                val slope = elapsedSeconds.cosAt(DOLPHIN_LEAP_RATE, phase * 6.28f)
                canvas.save()
                canvas.translate(x, y - climb)
                canvas.rotate(-slope * DOLPHIN_LEAP_TILT_DEGREES)
                // Sized against the sailboat rather than against nothing. Both were blitted at
                // their own native size, which made the animal 115 local units long and the boat
                // 84 -- a dolphin longer than the vessel beside it. [SceneSpace] states both in
                // metres over one lake metric, so the two can only be wrong together.
                //
                // Centred on its own content box too. The shipped origin put a 120x75 sprite at
                // (-28,-14), which is neither its canvas centre nor its content centre, so the
                // body sat down and to the right of the point the leap arc was computed for and
                // broke the surface off-centre.
                canvas.scale(SceneSpace.DOLPHIN_BASE_SCALE * lakeScale, SceneSpace.DOLPHIN_BASE_SCALE * lakeScale)
                sprites.draw(canvas, R.drawable.dolphin_body, DOLPHIN_ORIGIN_X_UNITS, DOLPHIN_ORIGIN_Y_UNITS, SpriteScale.SCENE_UNITS)
                canvas.restore()
            } else {
                // Sprite-converted the same way as the dolphin above -- hull and sail are two
                // separate sprites (matching the reference's own `SailboatBottom`/`SailboatSails`
                // being two independent models) so a future delivery could animate them
                // independently (e.g. sail luffing) without touching the hull.
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
                sprites.draw(canvas, R.drawable.sailboat_sail, -35f, -50f, SpriteScale.SCENE_UNITS)
                sprites.draw(canvas, R.drawable.sailboat_hull, -42f, 8f, SpriteScale.SCENE_UNITS)
                canvas.restore()
            }
        }
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
            objectGroundGeometry = GroundGeometry(objectShiftWrapped, objectTileWidth)

            val path = baseHillShapes[layer] ?: continue

            // Batch 4 aesthetic pass: a subtle vertical gradient (lighter near the wavy top
            // ridge, settling to the exact configured color by ~35% down the layer) instead of
            // one flat fill -- same "paper catching light at the fold" idea as the mountains'
            // two-face split just above, adapted for a continuous wavy shape where a left/right
            // split doesn't apply. Built once per layer (not per tile-offset copy below) since
            // layerTop/layerHeight don't change across those copies and only X gets translated.
            // Reference's own `Hills` class (decompiled `SegmentedPlane` subclass) is flat
            // vertex-colored geometry too, same as mountains -- no texture to convert here,
            // this is a procedural stand-in for the same visual effect batches 1-3's baked
            // sprite mottling gives everything else.
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
     * screen-widths, anchored at the wrappedShift=0 reference position -- matching the reference
     * app's own decompiled `Hills` class exactly: `getHeightData()` there is
     * `(1 - amp) + amp * sin(f * 4π)`, a perfectly smooth, perfectly periodic wave (2 full cycles
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

        // "Hills Variation" (user-editable, 0..1) scales the sine's amplitude, exactly matching
        // the reference's own `ampNormalized` being a user-adjustable parameter there --
        // variation=1 reproduces the full [0.04, 0.22] range, variation=0 collapses the amplitude
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
