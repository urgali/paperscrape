package com.paperscrape.livewallpaper.engine

import android.content.Context
import android.graphics.Paint
import androidx.core.graphics.ColorUtils
import com.paperscrape.livewallpaper.R
import kotlin.math.sin

/**
 * Horizontal ground snapshot, computed by [PaperRenderer] each frame and shared with this renderer
 * so static objects (house, tree, building) scroll in perfect sync with the ground they're
 * anchored to.
 *
 * It used to carry the hill layer's top and height as well, and [draw] turned those into an
 * object's Y. They are gone: the vertical half of the ground plane is [SceneSpace]'s, and passing
 * a second copy of it per frame made it possible for the two to disagree. What is left is the only
 * part that genuinely varies frame to frame -- where the scroll currently is.
 */
data class GroundGeometry(
    val shiftXWrapped: Float, // parallax shift, wrapped using a *screen-width* period (see PaperRenderer.drawHillLayers)
    val tileWidth: Float,     // screen width -- objects' own tiling period, decoupled from the hill's wider one
)

private class StaticRuntime(val spec: StaticSceneObject) {
    val idleSeed = (spec.tileFractionX * 97f) % 6.28f
}

private class CarRuntime(val spec: CarObject) {
    var progress = -spec.startDelaySeconds // negative = still waiting to start
}

class SceneObjectRenderer(
    private val layout: SceneObjectLayout,
    customization: SceneCustomization = SceneCustomization.DEFAULT,
    private val context: Context,
) {

    /**
     * The active per-category configuration.
     *
     * Assigning a new value applies it **in place** and rebuilds only the runtime lists whose
     * membership actually changed. This used to be a `val`, which meant the only way to apply any
     * change at all was to construct a whole new [SceneObjectRenderer] -- so adjusting a colour,
     * or a slider belonging to an entirely different part of the scene, discarded every car's
     * in-flight position along the road and restarted it from its start delay.
     *
     * Three cases, in increasing cost:
     *  - **cosmetic** (any colour, seasonal palette flag, or a section drawn by `PaperRenderer`
     *    such as clouds or the lake): nothing is rebuilt, every runtime keeps its state;
     *  - **static structure** (a category's visibility or density): the static runtime list is
     *    rebuilt, cars are untouched and keep running;
     *  - **car structure** (car visibility or density): the car list is rebuilt, which
     *    legitimately restarts cars because the set of cars itself changed.
     *
     * Rebuilding the static list is visually free: [StaticRuntime] holds only `idleSeed`, derived
     * deterministically from its spec, so a rebuilt object resumes exactly where the old one was.
     */
    var customization: SceneCustomization = customization
        set(value) {
            val previous = field
            field = value
            if (!previous.staticStructurallyEquals(value)) rebuildStaticRuntimes()
            if (!previous.carsStructurallyEquals(value)) rebuildCarRuntimes()
        }

    /**
     * Draw order is back-to-front by depth, so nearer objects overlap farther ones.
     *
     * Sorted in [buildStaticRuntimes], not in `draw()`. It used to be
     * `staticRuntimes.sortedBy { it.spec.depthFraction }` inside the per-frame loop, which
     * allocated a fresh list and re-sorted it on every single frame -- for a value that cannot
     * change while the list is alive, since `depthFraction` is a `val` on an immutable spec.
     *
     * The list is now rebuilt only when the set of rendered objects genuinely changes (a
     * category's visibility or density -- see [customization]), and the sort runs as part of
     * that rebuild. `sortedBy` is stable, so objects sharing a depth keep their original
     * relative order and the draw order matches what the per-frame sort produced.
     */
    private var staticRuntimes: List<StaticRuntime> = buildStaticRuntimes()
    private var carRuntimes: List<CarRuntime> = buildCarRuntimes()

    private fun buildStaticRuntimes(): List<StaticRuntime> = layout.staticObjects
        .filter { spec -> customization.keepCandidate(spec) }
        .map { StaticRuntime(it) }
        .sortedBy { it.spec.depthFraction }

    /**
     * Sorted by lane, far first, for the same reason [staticRuntimes] is sorted by depth: draw
     * order is depth order. The two lanes overlap vertically by design, so a near car has to
     * paint over a far one -- and candidate index alternates lanes, which would otherwise put a
     * far car on top of the near car it is passing.
     */
    private fun buildCarRuntimes(): List<CarRuntime> = layout.cars
        .filter { spec -> customization.keepCar(spec) }
        .map { CarRuntime(it) }
        .sortedBy { it.spec.laneYFraction }

    private fun rebuildStaticRuntimes() {
        staticRuntimes = buildStaticRuntimes()
    }

    /**
     * Rebuilding cars resets each car's `progress`, so this runs only when the *set* of cars
     * changed -- never for a colour edit or an unrelated slider.
     */
    private fun rebuildCarRuntimes() {
        carRuntimes = buildCarRuntimes()
    }

    /**
     * The lane pair the road is painted around, taken from the theme's **whole** car list.
     *
     * This is the fix for a defect reported from a device: moving the Cars density slider resized
     * the road. `drawRoad` read its lane span from [carRuntimes], which is the list *after*
     * density thinning, so the road's own geometry was a function of how many cars happened to
     * survive -- at a low setting only one lane was left, its span collapsed to zero and the strip
     * with it; at zero the road disappeared entirely.
     *
     * A density control decides how much traffic there is. It has no business deciding how wide
     * the road is, and nothing else about the scene's geometry may be derived from a runtime list
     * a slider filters. Computed once from the immutable [SceneObjectLayout], so it is also off
     * the frame path.
     */
    private val roadLaneMinFraction: Float = layout.cars.minOfOrNull { it.laneYFraction } ?: 0f
    private val roadLaneMaxFraction: Float = layout.cars.maxOfOrNull { it.laneYFraction } ?: 0f

    /** Whether this theme has a road at all. A theme with no cars in its layout has none. */
    private val hasRoad: Boolean = layout.cars.isNotEmpty()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0x33000000
    }
    private val sprites = SpriteBlitter(context)

    companion object {
        /**
         * Which drawing a static object resolves to, and therefore which entry of the size table
         * governs it.
         *
         * Two categories cover more than one drawing: a house is small or large, and the buildings
         * category is a tower, a restaurant or a bar. Both used to decide that inside their own
         * `draw*` function, after [draw] had already computed a scale and a cull extent for
         * "a house" -- so the two halves of one object were derived from different assumptions,
         * and a per-drawing size could only be applied as a `canvas.scale` correction bolted on
         * afterwards. Resolving the variant once, here, is what lets the scale come from the size
         * table and lets those corrections be deleted.
         *
         * Pure and free of Android types, so the mapping is unit-testable.
         */
        fun variantFor(spec: StaticSceneObject): SceneSpace.SceneVariant = when (spec.type) {
            SceneObjectType.HOUSE ->
                if (kotlin.math.abs((spec.tileFractionX * 7561f).toInt()) % 2 == 1) {
                    SceneSpace.SceneVariant.HOUSE_LARGE
                } else {
                    SceneSpace.SceneVariant.HOUSE_SMALL
                }
            // A tower belongs on the skyline and a shop front among the houses, so the choice is
            // made by depth rather than by a hash of the horizontal position. Restaurant and bar
            // are interchangeable at any depth, so those two still split on the position hash.
            SceneObjectType.SKYSCRAPER ->
                if (spec.depthFraction < SceneSpace.BUILDING_TOWER_MAX_DEPTH) {
                    SceneSpace.SceneVariant.TOWER
                } else if (kotlin.math.abs((spec.tileFractionX * 5237f).toInt()) % 2 == 0) {
                    SceneSpace.SceneVariant.RESTAURANT
                } else {
                    SceneSpace.SceneVariant.BAR
                }
            SceneObjectType.TREE -> SceneSpace.SceneVariant.TREE
            SceneObjectType.PALM_TREE -> SceneSpace.SceneVariant.PALM_TREE
            SceneObjectType.PARASOL -> SceneSpace.SceneVariant.PARASOL
            SceneObjectType.SNOWMAN -> SceneSpace.SceneVariant.SNOWMAN
            SceneObjectType.GIFT -> SceneSpace.SceneVariant.GIFT
            SceneObjectType.PENGUIN -> SceneSpace.SceneVariant.PENGUIN
            SceneObjectType.BUNNY -> SceneSpace.SceneVariant.BUNNY
            SceneObjectType.EASTER_EGG -> SceneSpace.SceneVariant.EASTER_EGG
            SceneObjectType.PUMPKIN -> SceneSpace.SceneVariant.PUMPKIN
            // Cars are drawn by drawCar, from their lane, and never reach drawStaticObject.
            SceneObjectType.CAR -> SceneSpace.SceneVariant.HOUSE_SMALL
        }

        /**
         * The scale one static object is drawn at, in full.
         *
         * The single place the four stages of `SceneSpace`'s own doc comment are multiplied
         * together for a static object. Pure, so the whole size pipeline is testable without a
         * `Canvas`.
         */
        fun effectiveScaleFor(spec: StaticSceneObject, screenHeightPx: Float): Float =
            variantFor(spec).baseScale *
                spec.scale *
                SceneSpace.depthScale(spec.depthFraction) *
                SceneSpace.sceneScale(screenHeightPx)

        /**
         * Where a driver's bust sits, and how big it is drawn, in [drawCar]'s local space.
         *
         * The point is the bottom-centre of the vehicle's glass, and the sprite's declared
         * `CONTENT_BOTTOM_CENTRE` anchor is subtracted from it at the call site, so the bust
         * stands on the window sill instead of being centred on its own canvas. The scales are
         * the glass height over the bust's own 47 units of content: a car's window is 16 units
         * tall, the fire truck's cab 14.
         */
        /**
         * Where a walking person's sprite origin goes, so its content bottom-centre lands on the
         * pavement line it was placed at.
         *
         * All twenty-four walk sprites are 129x252 px -- 43x84 local units -- and every one of
         * them has its content reaching the canvas's bottom edge, so one pair of numbers covers
         * the set rather than a per-sprite table.
         */
        /**
         * How many pedestrians the scene carries.
         *
         * Not a density control. People are the one category with no visibility or density
         * setting of their own -- that is decision D3, still open -- so this is a constant rather
         * than a preference, named here instead of sitting inline in the draw loop.
         */
        const val PEDESTRIAN_COUNT = 4

        /**
         * Decorrelates the pedestrians' density thresholds from every other category's.
         *
         * Without a salt of its own the same candidate index would drop out of two categories at
         * the same setting, which reads as the scene emptying in steps rather than thinning.
         */
        const val PEDESTRIAN_THRESHOLD_SALT = 6151

        /** Half a walk sprite's own width, in local units, for the wrap-tile cull. */
        const val PERSON_HALF_WIDTH_UNITS = 21.5f

        const val PERSON_ANCHOR_X_UNITS = -20.5f
        const val PERSON_ANCHOR_Y_UNITS = -84f

        const val CAR_HEAD_X_UNITS = -8f
        const val CAR_HEAD_Y_UNITS = 10f
        const val CAR_HEAD_SCALE = 0.30f
        const val FIRE_TRUCK_HEAD_X_UNITS = -28f
        const val FIRE_TRUCK_HEAD_Y_UNITS = 4f
        const val FIRE_TRUCK_HEAD_SCALE = 0.28f

        /**
         * The `CONTENT_BOTTOM_CENTRE` anchor the four car-driver head sprites declare, in local
         * units.
         *
         * All four are 171x162 and anchor at y=162; on x they declare 86 px for the summer pair
         * and 84 for the winter one, so this is the midpoint of the two. The 2 px spread is 0.67
         * local units, which at [CAR_HEAD_SCALE] and the vehicle's own scale is well under a
         * pixel on screen -- below the point where a per-sprite table would buy anything, and
         * the same reasoning that lets a lookup group share one crop rectangle.
         */
        const val CAR_HEAD_ANCHOR_X_UNITS = 20.5f
        const val CAR_HEAD_ANCHOR_Y_UNITS = 48f

        /**
         * Where a passenger sits, and the anchor of the head they are drawn with.
         *
         * The rear pane of the glass, on the far side of its pillar from the driver. Passengers
         * use the 180x162 window-occupant heads rather than the 171x162 driving ones, because
         * there is no child driving head and inventing one would mean a child could be drawn in
         * a driving seat by a later edit; the two sets are deliberately not interchangeable. The
         * anchor is the midpoint of the four heads' declared `CONTENT_BOTTOM_CENTRE` x, which
         * spread over 8 px -- 2.7 units, under a pixel on screen after the scale.
         */
        const val CAR_PASSENGER_X_UNITS = 17f
        const val CAR_PASSENGER_Y_UNITS = 10f
        const val CAR_PASSENGER_SCALE = 0.29f
        const val WINDOW_HEAD_ANCHOR_X_UNITS = 26.8f
        const val WINDOW_HEAD_ANCHOR_Y_UNITS = 54f

        /**
         * Conservative upper bound on how far, in local units, any static scene object extends
         * horizontally either side of its own origin, before its category scale and the depth
         * scale are applied.
         *
         * Derived by measuring, not guessed: across all 54 sprite blit sites in this class the
         * widest local span is `house_large_roof`/`house_large_trim` at x from -75 to +75 (origin
         * -75, sprite 450px wide, divided by [SpriteBlitter.SPRITE_PIXELS_PER_UNIT]); the widest
         * procedural primitive is the skyscraper at 90 units wide, i.e. +/-45, with its ground
         * shadow at +/-54. 96 rounds the measured 75 up with roughly 28% of headroom, so a missed
         * detail or a slightly wider future sprite still cannot cause premature clipping at a
         * screen edge.
         *
         * Raise this if an object is ever drawn wider than 96 units either side of its origin.
         * Getting it too large only costs a few needless draws at the edges; too small would
         * clip visible objects, so err high.
         */
        /** Radius of one blinking light on a winter tree, in local units. */
        const val CHRISTMAS_LIGHT_RADIUS_UNITS = 2.6f

        const val MAX_OBJECT_HALF_WIDTH_UNITS = 96f

        /**
         * How many canopies a frame can offer the falling leaves.
         *
         * A fixed ceiling because the arrays are refilled every frame and must not grow: a
         * densely-treed theme across a wide screen with wrap tiles can present more crowns than
         * there are leaves, and the leaves only need somewhere plausible to come from.
         */
        const val MAX_LEAF_SOURCES = 24

        /** The presents under a fir, in the tree lights' own palette. */
        val GIFT_COLOURS = intArrayOf(0xFFE8564F.toInt(), 0xFF4F8FBF.toInt(), 0xFF6FCF6F.toInt())

        /** How many flower clumps the ground carries, and where they may stand. */
        const val FLOWER_CLUMP_COUNT = 22
        const val FLOWER_DEPTH_MIN = 0.06f
        const val FLOWER_DEPTH_MAX = 0.92f
        const val FLOWER_METRES_TALL = 0.55f
        const val FLOWER_SPRITE_UNITS_TALL = 12f

        /** Bulbs on the string under one window. */
        const val WINDOW_LIGHT_COUNT = 4

        /**
         * The preview strip height the fitting factors in [drawPreviewPair] were chosen against.
         *
         * The preview box is a fixed 120 dp, so its pixel height varies with display density and
         * the objects in it must scale with that rather than being drawn at a fixed size.
         */
        const val PREVIEW_REFERENCE_HEIGHT_PX = 1400f

        /**
         * Whether an object whose origin sits at [x] can contribute any pixel to a viewport
         * [screenWidth] wide, given its scaled half-width [halfWidth].
         *
         * Replaces a hardcoded `x < -200f || x > 3000f` test. That constant was unrelated to the
         * actual viewport: on a 1080px phone it kept drawing objects almost 2000px past the
         * right edge every frame, and on a display wider than 3000px it would have culled
         * objects that were genuinely visible.
         *
         * Pure and free of Android types so the entry/exit behaviour at both edges can be unit
         * tested directly.
         */
        fun isHorizontallyVisible(x: Float, halfWidth: Float, screenWidth: Float): Boolean =
            x + halfWidth >= 0f && x - halfWidth <= screenWidth

        /**
         * The tile index to start scanning from when looking for the copies of an object that are
         * actually on screen.
         *
         * An object anchored at [x] also exists at every `x + k * tileWidth` for integer `k` — the
         * scene tiles horizontally, so the wrap seam never shows a gap. Only the copies that
         * intersect the viewport need drawing. This returns a `k` that is guaranteed to be **at or
         * before** the first such copy, so a caller stepping forward by one tile at a time and
         * stopping once the left edge passes the right of the screen visits every visible copy and
         * nothing beyond them.
         *
         * `floor`, not `ceil`, is deliberate and is the whole safety property here. The exact first
         * visible index is `ceil((-halfWidth - x) / tileWidth)`; taking the floor instead can only
         * ever start one tile *early*, never one tile *late*. Starting early costs one rejected
         * iteration, which is free; starting late would drop a copy that should have been drawn,
         * which is a visible pop at a screen edge. Float rounding at an exact tile boundary can
         * move this quotient either way, so the direction of the error is chosen rather than
         * assumed.
         *
         * This replaces a hardcoded `-1..1` copy loop. Three copies happen to be enough for the
         * current tile width (`2 x screenWidth`) and object extents, but only by a margin of about
         * 0.4 of a tile: the count was never derived from the geometry, so a wider object or a
         * different tiling period would have started silently dropping copies at the seam. Deriving
         * the range makes that impossible by construction, the same way
         * [isHorizontallyVisible] replaced a hardcoded pixel constant.
         *
         * Pure and free of Android types so the enumeration can be unit tested directly against the
         * loop it replaces. [tileWidth] must be positive; callers guard the degenerate case.
         */
        fun firstVisibleTileOffset(x: Float, halfWidth: Float, tileWidth: Float): Int =
            kotlin.math.floor((-halfWidth - x) / tileWidth).toInt()

        /**
         * One past the last tile index worth considering: the first copy whose *left* edge has
         * passed the right of the screen. Together with [firstVisibleTileOffset] this gives the
         * half-open range `first until limit` that [draw] walks.
         *
         * The range is deliberately expressed as two pure functions rather than as a loop
         * condition inside [draw]. [draw] needs a `Canvas`, so anything written as a condition
         * there can only be tested by reimplementing it in the test — which tests the copy, not
         * the code. Both bounds being ordinary functions means the enumeration itself is what the
         * tests exercise.
         *
         * A copy exactly touching the right edge is inside the range, matching
         * [isHorizontallyVisible]'s own inclusive `<=`. The two must agree: if this excluded a
         * copy the predicate calls visible, that copy would be dropped and pop at the edge.
         *
         * The range is an upper bound on what to *consider*, not a claim about visibility --
         * [isHorizontallyVisible] still decides each copy. [tileWidth] must be positive.
         */
        fun tileOffsetLimit(x: Float, halfWidth: Float, tileWidth: Float, screenWidth: Float): Int =
            kotlin.math.floor((screenWidth + halfWidth - x) / tileWidth).toInt() + 1
    }

    /**
     * The accent colours that are still applied as a runtime tint.
     *
     * There used to be a dozen of these. They existed because the shipped artwork for a beak, an
     * inner ear, a ribbon or a planter was a white mask, so the only place its colour could live
     * was a constant multiplied over it at the call site. The V2 asset set draws those accents in
     * their own paper colours, which makes the constant a *second* colour compounded over
     * finished art rather than the art's only colour -- the failure mode `SpriteTintClassTest`
     * now guards in both directions. Every accent whose sprite became fixed art was deleted here
     * and its call site moved to an untinted blit in the same change.
     *
     * What is left is the two colours that never belonged to a sprite at all: the parasol's pole
     * and the penguin's belly. The pole is a `drawRect`, and the belly's sprite is still a
     * greyscale mask in V2, so both are tints in the sense the word is meant to carry.
     */
    private val parasolPoleColor = 0xFFEFE0CE.toInt()
    private val penguinBellyColor = 0xFFF3F7FB.toInt()

    // Road (drawn under any cars the theme has)
    private val roadColorDay = 0xFF5B5650.toInt()
    private val roadColorNight = 0xFF29271F.toInt()
    private val roadEdgeColor = 0xFF3D3A33.toInt()
    private val roadLineColor = 0xFFF3E6D0.toInt()

    fun update(deltaSeconds: Float) {
        for (c in carRuntimes) {
            c.progress += deltaSeconds * c.spec.speedFraction
            // Wrap by subtracting the span, never by assigning a fixed value. Snapping every car
            // back to exactly -0.3 discarded the head start it had over the car behind it, so a
            // lane's cars re-synchronised on their first lap and thereafter travelled as a pack --
            // the congestion reported from the device. Subtracting keeps the phase, so a queue
            // laid out evenly at generation time stays evenly spaced indefinitely.
            if (c.progress > 1.3f) c.progress -= SceneObjectCatalog.CAR_LOOP_SPAN
        }
    }

    private fun currentCarX(c: CarRuntime, screenWidth: Float): Float {
        val margin = 120f
        val travel = screenWidth + margin * 2f
        val rawX = c.progress * travel - margin
        return if (c.spec.reverse) screenWidth - rawX else rawX
    }

    /**
     * A soft translucent ground-shadow ellipse beneath an object's base, drawn in the object's
     * own local coordinate space (so it always sits exactly at that object's own y=0 regardless
     * of the caller's scale/translate) -- the same "doesn't look like it's floating" technique
     * [drawHouse]'s foundation strip and [drawParasol]'s shadow oval already used individually,
     * pulled out into one shared helper and now applied consistently to every other
     * ground-anchored object that didn't have one yet (buildings, cars, trees, and the smaller
     * seasonal decorations). Part of the aesthetic pass aa asked for across every editable/moving
     * element except clouds: a visible ground shadow was the single most consistent thing missing
     * across roughly half of them, and it's cheap (one extra oval fill, no new Path work).
     */
    private fun drawGroundShadow(canvas: SceneCanvas, halfWidth: Float, halfHeight: Float = 5f) {
        fillPaint.color = 0x2E000000
        canvas.drawOval(-halfWidth, -halfHeight, halfWidth, halfHeight, fillPaint)
    }

    /**
     * Blits a cached sprite bitmap tinted to [tintColor], positioned so that the bitmap's own
     * local origin (pixel 0,0) lands at world-space [originXUnits]/[originYUnits] -- i.e. the
     * same "local unit coordinates" every vector-drawn shape in this file already used, so a
     * caller migrating a shape from `canvas.drawShape(...)` to this just needs the same bounding
     * box numbers it already had, not a new coordinate system to reason about.
     *
     * Every sprite this class draws is authored in the [SpriteScale.SCENE_UNITS] convention, so
     * this binds that convention once, here, rather than repeating it at each of the 60 call
     * sites. A sprite authored at literal on-screen pixel size must **not** go through this: call
     * [SpriteBlitter.drawTinted] with [SpriteScale.CANVAS_PIXELS] directly, or it renders
     * [SpriteBlitter.SPRITE_PIXELS_PER_UNIT] times too small.
     *
     * See [SpriteBlitter] for the tint mode and the colour-fidelity trade-off it carries.
     */
    private fun drawTintedSprite(canvas: SceneCanvas, resId: Int, originXUnits: Float, originYUnits: Float, tintColor: Int) {
        sprites.drawTinted(canvas, resId, originXUnits, originYUnits, SpriteScale.SCENE_UNITS, tintColor)
    }

    /** Same as [drawTintedSprite], but blits the bitmap's own baked-in colors as-is (no
     * `PorterDuffColorFilter` at all, see [TintFilterCache]) -- for sprites like the palm tree
     * trunk that use a fixed, non-user-customizable color baked directly into the PNG at
     * generation time rather than a white/alpha mask meant to be tinted. */
    private fun drawSprite(canvas: SceneCanvas, resId: Int, originXUnits: Float, originYUnits: Float) {
        sprites.draw(canvas, resId, originXUnits, originYUnits, SpriteScale.SCENE_UNITS)
    }

    /**
     * Same as [drawSprite], faded to [alpha].
     *
     * Used for the two night overlays the V2 asset set introduced: a lit house window and a lit
     * skyscraper wall are separate drawings laid over their daytime counterpart, and the
     * day-to-night crossfade is the alpha. Both are fixed art, so the fade cannot be expressed as
     * a tint the way the old procedural window colour was.
     */
    private fun drawSpriteFaded(canvas: SceneCanvas, resId: Int, originXUnits: Float, originYUnits: Float, alpha: Int) {
        if (alpha <= 0) return
        sprites.draw(canvas, resId, originXUnits, originYUnits, SpriteScale.SCENE_UNITS, alpha)
    }

    /**
     * An object's horizontal anchor in the tile the shared [GroundGeometry] currently places it
     * in. `shiftXWrapped`/`tileWidth` are computed per-frame in `PaperRenderer.drawHillLayers`
     * from the same values the hills themselves scroll by, so objects can never desync from the
     * ground they stand on. This maps `tileFractionX` linearly across one tile period, so every
     * object at every position is reachable and nothing overlaps another object that happens to
     * sit a whole tile-period away.
     *
     * The result is one representative copy, not *the* position: the object also exists at every
     * whole tile either side of it, and [draw] uses [firstVisibleTileOffset] to find which of
     * those copies are on screen. Y is not computed here -- it comes from the shared
     * [SceneSpace.groundYFraction], evaluated on the object's own continuous
     * [StaticSceneObject.depthFraction].
     *
     * Returns a bare `Float`. It used to return `Pair<Float, Float>` whose second element was the
     * `groundY` the caller had just passed in, which boxed two `Float`s and allocated a `Pair` for
     * every static object of every frame -- three allocations per object per frame on a path
     * `AI_PROJECT_RULES.md` 5.1 requires to be allocation-free.
     */
    private fun anchorX(spec: StaticSceneObject, geom: GroundGeometry): Float {
        var x = geom.shiftXWrapped + spec.tileFractionX * geom.tileWidth
        if (x < -geom.tileWidth * 0.5f) x += geom.tileWidth
        return x
    }

    /**
     * Draws every static object, then the road, the cars and the people.
     *
     * The scene tiles horizontally, so each object exists at every whole `tileWidth` either side
     * of its anchor and the copies that land on screen must all be drawn or the wrap seam shows a
     * gap. This used to walk a fixed `x`, `x - tileWidth`, `x + tileWidth` and let each copy cull
     * itself, which meant the scale and extent the cull decides on -- properties of the *object*,
     * not of the copy -- were recomputed three times per object per frame, and two of the three
     * calls did nothing but that work and return.
     *
     * Now the per-object values are computed once and the range of copies is derived from the
     * geometry: [firstVisibleTileOffset] gives a starting tile at or before the first visible one,
     * and the loop walks forward until the copy's left edge clears the right of the screen. That
     * visits only copies that can be seen, and unlike the fixed three it stays correct for any
     * tile width and any object extent rather than happening to have enough margin.
     *
     * [isHorizontallyVisible] still decides every copy, so the set of objects painted is exactly
     * the set that passed the same predicate before.
     */
    /**
     * Where this frame's canopies are, in screen pixels, for anything that needs to come *off* a
     * tree rather than out of the sky.
     *
     * **Filled here because here is the only place that knows.** A tree's screen position is its
     * depth fraction, its ground line, its effective scale and its wrap-tile offset combined, and
     * all four are resolved inside [draw]. `PaperRenderer` draws the falling leaves and had none
     * of them, so it spawned every leaf at one fixed height across the whole width -- which is
     * what read on a device as leaves appearing out of mid-air, most of them nowhere near a tree.
     *
     * Three parallel arrays and a count rather than a list of points: this is refilled every
     * frame, and a list of objects would allocate every frame.
     */
    val leafSourceX = FloatArray(MAX_LEAF_SOURCES)
    val leafSourceY = FloatArray(MAX_LEAF_SOURCES)
    val leafSourceHalfWidth = FloatArray(MAX_LEAF_SOURCES)
    var leafSourceCount = 0
        private set

    private fun recordLeafSource(variant: SceneSpace.SceneVariant, x: Float, groundY: Float, scale: Float) {
        if (leafSourceCount >= MAX_LEAF_SOURCES) return
        // The centre of the crown as each is actually blitted: the leafy canopy hangs at -38 with
        // its own content centred another 43 above that, and the palm's fan at -90.33 with its
        // content centre 18 below its origin. Derived from the two call sites rather than guessed,
        // so a change to either moves the leaves with it.
        val centreUnits = when (variant) {
            SceneSpace.SceneVariant.TREE -> -81f
            SceneSpace.SceneVariant.PALM_TREE -> -72f
            else -> return
        }
        val halfWidthUnits = if (variant == SceneSpace.SceneVariant.TREE) 41f else 20f
        leafSourceX[leafSourceCount] = x
        leafSourceY[leafSourceCount] = groundY + centreUnits * scale
        leafSourceHalfWidth[leafSourceCount] = halfWidthUnits * scale
        leafSourceCount++
    }

    /**
     * Wildflowers on the open ground, drawn before anything that stands on it.
     *
     * **Placed on the same ground line and the same perspective as everything else**, so a clump
     * near the road is larger than one at the back and both sit where their stems meet the earth.
     * The positions come from a fixed integer hash of the clump index rather than from a stored
     * list, so nothing is allocated per frame and the scatter is identical every frame -- flowers
     * that shimmered from frame to frame would be worse than none.
     *
     * The scatter is stratified rather than uniform: each clump gets its own band of depth and its
     * own slice of the width, and jitters inside them. A purely uniform draw clusters and leaves
     * bald patches, which is what makes a scatter read as random rather than as ground cover.
     */
    private fun drawGroundFlowers(canvas: SceneCanvas, geom: GroundGeometry, screenWidth: Float, screenHeight: Float) {
        if (!customization.flowersEnabled) return
        val sceneScale = SceneSpace.sceneScale(screenHeight)
        for (i in 0 until FLOWER_CLUMP_COUNT) {
            val h = (i * 2654435761L.toInt()) xor (i shl 7)
            val jitterX = ((h ushr 3) and 0xFF) / 255f
            val jitterDepth = ((h ushr 13) and 0xFF) / 255f
            // **Stratified across the width, free in depth.** The first version banded depth by
            // the clump index as well, which correlated depth with x and laid every clump on one
            // straight diagonal -- caught in the preview, not in a test. Only the horizontal
            // slice is stratified now; how far back a clump stands is its own hash.
            val depth = FLOWER_DEPTH_MIN + (FLOWER_DEPTH_MAX - FLOWER_DEPTH_MIN) * jitterDepth
            val groundY = screenHeight * SceneSpace.groundYFraction(depth)
            val scale = SceneSpace.scaleForHeight(FLOWER_METRES_TALL, FLOWER_SPRITE_UNITS_TALL) *
                SceneSpace.depthScale(depth) * sceneScale
            val slice = screenWidth / FLOWER_CLUMP_COUNT
            val baseX = (i + jitterX) * slice
            val tile = if (geom.tileWidth > 0f) geom.tileWidth else screenWidth
            var x = (baseX + geom.shiftXWrapped) % tile
            while (x < screenWidth + slice) {
                if (x > -slice) {
                    canvas.save()
                    canvas.translate(x, groundY)
                    canvas.scale(scale, scale)
                    drawSprite(canvas, R.drawable.ground_flowers, -18f, -12f)
                    canvas.restore()
                }
                x += tile
            }
        }
    }

    fun draw(canvas: SceneCanvas, geom: GroundGeometry, dayBlend: Float, elapsedSeconds: SceneTime, screenWidth: Float, screenHeight: Float) {
        leafSourceCount = 0
        drawGroundFlowers(canvas, geom, screenWidth, screenHeight)
        // staticRuntimes is already depth-sorted at construction -- see its declaration.
        for (r in staticRuntimes) {
            val groundY = screenHeight * SceneSpace.groundYFraction(r.spec.depthFraction)
            // Both are properties of the object, so they are computed once here rather than once
            // per tile copy: drawStaticObject is handed the scale it must draw at.
            val effectiveScale = effectiveScaleFor(r.spec, screenHeight)
            val halfWidth = MAX_OBJECT_HALF_WIDTH_UNITS * effectiveScale
            val x = anchorX(r.spec, geom)

            if (geom.tileWidth <= 0f) {
                // No tiling period, so there are no copies to step through -- draw the object once
                // if it is visible. Reached when the surface has not been sized yet, where
                // screenWidth and therefore tileWidth are still 0; in the running scene
                // PaperRenderer fills GroundGeometry from drawHillLayers immediately before
                // calling this. The enumeration below divides by tileWidth, so this is a
                // correctness guard, not an optimisation.
                if (isHorizontallyVisible(x, halfWidth, screenWidth)) {
                    drawStaticObject(canvas, r, x, groundY, effectiveScale, dayBlend, elapsedSeconds)
                    recordLeafSource(variantFor(r.spec), x, groundY, effectiveScale)
                }
                continue
            }

            // Each copy's x is recomputed from its tile index rather than accumulated by adding
            // tileWidth. Accumulating would drift by a few ULP away from the `x - tileWidth` /
            // `x + tileWidth` the fixed loop produced, which is far below a pixel but would make
            // "identical output" an argument about tolerances instead of a bit-for-bit fact.
            val firstTile = firstVisibleTileOffset(x, halfWidth, geom.tileWidth)
            val tileLimit = tileOffsetLimit(x, halfWidth, geom.tileWidth, screenWidth)
            for (tileIndex in firstTile until tileLimit) {
                val copyX = x + tileIndex * geom.tileWidth
                if (isHorizontallyVisible(copyX, halfWidth, screenWidth)) {
                    drawStaticObject(canvas, r, copyX, groundY, effectiveScale, dayBlend, elapsedSeconds)
                    recordLeafSource(variantFor(r.spec), copyX, groundY, effectiveScale)
                }
            }
        }

        drawRoad(canvas, dayBlend, screenWidth, screenHeight, geom.shiftXWrapped, geom.tileWidth)

        for (c in carRuntimes) {
            if (c.progress < -0.05f || c.progress > 1.05f) continue
            drawCar(canvas, c, screenWidth, screenHeight, dayBlend)
        }

        drawPeople(canvas, geom, screenWidth, screenHeight, elapsedSeconds)
    }

    private val personKinds = arrayOf("man", "woman", "boy", "girl")

    // Aesthetic-pass batch 5 fix: these were `when ("${kind}_${season}_$frame")` string-concat
    // lookups, building a new String object on every single call -- with 4 walking candidates
    // plus up to ~1/3 of houses' window occupants plus every car's driver head evaluating this
    // every single frame, that's a lot of avoidable per-frame garbage for something that's just
    // picking one of a fixed, known-at-compile-time set of drawable IDs. Flat arrays indexed by
    // int (kind/season/frame) instead -- no allocation, no string comparison.
    //
    // **Frame 3 names `walk1` deliberately, and that is not a typo.** This is a four-frame cycle
    // of two poses: frames 0 and 2 are the contacts, one per leading leg, and frames 1 and 3 are
    // the passing pose between them. At the passing pose the legs are together, so a flat
    // silhouette draws the same picture whichever leg is in front -- and the two frames shipped
    // as byte-identical PNGs for exactly that reason. Phase 3.4 removed the eight redundant
    // `..._walk3.png` files and pointed the slot at the drawing they duplicated. The animation is
    // unchanged frame for frame; what changed is that the cycle no longer decodes and uploads the
    // passing pose twice per kind and season.
    //
    // If a future art pass gives the two passing frames different artwork -- mirrored arm swing,
    // say -- restore `..._walk3.png` and this slot together. `SpriteVariantTest` declares the
    // sharing, so re-adding one without the other fails there.
    private val personWalkDrawables = arrayOf(
        // man
        arrayOf(
            intArrayOf(R.drawable.person_man_summer_walk0, R.drawable.person_man_summer_walk1, R.drawable.person_man_summer_walk2, R.drawable.person_man_summer_walk1),
            intArrayOf(R.drawable.person_man_winter_walk0, R.drawable.person_man_winter_walk1, R.drawable.person_man_winter_walk2, R.drawable.person_man_winter_walk1),
        ),
        // woman
        arrayOf(
            intArrayOf(R.drawable.person_woman_summer_walk0, R.drawable.person_woman_summer_walk1, R.drawable.person_woman_summer_walk2, R.drawable.person_woman_summer_walk1),
            intArrayOf(R.drawable.person_woman_winter_walk0, R.drawable.person_woman_winter_walk1, R.drawable.person_woman_winter_walk2, R.drawable.person_woman_winter_walk1),
        ),
        // boy
        arrayOf(
            intArrayOf(R.drawable.person_boy_summer_walk0, R.drawable.person_boy_summer_walk1, R.drawable.person_boy_summer_walk2, R.drawable.person_boy_summer_walk1),
            intArrayOf(R.drawable.person_boy_winter_walk0, R.drawable.person_boy_winter_walk1, R.drawable.person_boy_winter_walk2, R.drawable.person_boy_winter_walk1),
        ),
        // girl
        arrayOf(
            intArrayOf(R.drawable.person_girl_summer_walk0, R.drawable.person_girl_summer_walk1, R.drawable.person_girl_summer_walk2, R.drawable.person_girl_summer_walk1),
            intArrayOf(R.drawable.person_girl_winter_walk0, R.drawable.person_girl_winter_walk1, R.drawable.person_girl_winter_walk2, R.drawable.person_girl_winter_walk1),
        ),
    )

    /**
     * Window occupants and car drivers, per kind, summer then winter.
     *
     * **The two columns currently hold byte-identical artwork**, and that is a recorded gap
     * (`ROADMAP.md` decision D2, resolved in Phase 3.5), not something to collapse. The seasonal
     * distinction is real and visible on the walking sprites -- the winter set has a beanie
     * instead of hair, long sleeves, a snowflake motif, and trousers where the summer girl has a
     * skirt -- but it was never drawn for the heads, so a window occupant looks the same in
     * January as in July. Giving them real winter artwork is asset redesign against sources that
     * do not exist, so the gap is declared rather than invented.
     *
     * The table stays two columns wide precisely so that drawing those six sprites is the whole
     * fix: no code here changes. `SpriteVariantTest` fails the moment they stop being identical,
     * which is the signal to move the declaration in `sources/sprites.json` from `IDENTICAL_GAP`
     * to `DISTINCT`.
     */
    private val personWindowHeadDrawables = arrayOf(
        intArrayOf(R.drawable.person_man_summer_head_window, R.drawable.person_man_winter_head_window),
        intArrayOf(R.drawable.person_woman_summer_head_window, R.drawable.person_woman_winter_head_window),
        intArrayOf(R.drawable.person_boy_summer_head_window, R.drawable.person_boy_winter_head_window),
        intArrayOf(R.drawable.person_girl_summer_head_window, R.drawable.person_girl_winter_head_window),
    )
    private val personCarHeadDrawables = arrayOf(
        intArrayOf(R.drawable.person_man_summer_head_car, R.drawable.person_man_winter_head_car),
        intArrayOf(R.drawable.person_woman_summer_head_car, R.drawable.person_woman_winter_head_car),
    )

    /**
     * Ambient pedestrians walking along the sidewalk in front of the road, independent of the
     * car/house placement system -- same self-contained "own drift timer, own candidate pool"
     * approach [PaperRenderer.drawBirds] and [PaperRenderer.drawSantaSleigh] use for objects that
     * don't need to persist across theme saves. Each of the 4 candidates picks a stable kind
     * (man/woman/boy/girl) and direction from its own index; season (summer/winter sprite set)
     * follows [SceneCustomization.winterColorsEnabled] the same way every other seasonal
     * decoration does. The 4 walk frames are stepped through by elapsed time, not by distance
     * traveled, matching how [PaperRenderer.drawBirds]'s own wing-flap is time-driven too.
     */
    /**
     * The pedestrians, walking along the ground rather than across the screen.
     *
     * Their position used to be a fraction of *screen* width, so a swipe between home screens
     * scrolled the village past them while they stayed almost still -- the only thing in the scene
     * outside the parallax, and since v76.7 put them among the buildings it was the most visible
     * place to be outside it.
     *
     * A pedestrian now has a position on the tiling ground exactly like a house: its walk advances
     * that position, and [GroundGeometry] then scrolls it with everything else standing on the same
     * ground. The two motions compose instead of competing, which is also what makes the walk read
     * as walking -- a figure that slides against a static background is a figure on a treadmill.
     *
     * Tiled the same way static objects are, for the same reason: the ground repeats every
     * `tileWidth`, so a pedestrian near the seam exists on both sides of it and both copies have to
     * be drawn or one of them pops.
     */
    private fun drawPeople(
        canvas: SceneCanvas,
        geom: GroundGeometry,
        screenWidth: Float,
        screenHeight: Float,
        elapsedSeconds: SceneTime,
    ) {
        if (geom.tileWidth <= 0f) return
        val config = customization.people
        if (!config.visible) return
        val seasonIdx = if (customization.winterColorsEnabled) 1 else 0
        val candidateCount = PEDESTRIAN_COUNT
        val sceneScale = SceneSpace.sceneScale(screenHeight)
        // Density thins the same candidate pool the same way every other category's does, through
        // the shared threshold rather than by rounding a count -- so lowering it removes a
        // particular pedestrian and leaves the rest exactly where they were, instead of
        // reshuffling everybody.
        val effectOffset = CandidateThreshold.offsetFor(PEDESTRIAN_THRESHOLD_SALT)
        val density = config.density.coerceIn(0f, 1f)
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(density, candidateCount, effectOffset)
        for (i in 0 until candidateCount) {
            if (!CandidateThreshold.isPresent(i, density, effectOffset, fallbackIndex)) continue
            val reverse = i % 2 == 1
            // Two pavement rows, on the strip of ground between the buildings and the road. Both
            // the row's y and the speed at it come
            // from [SceneSpace]: a pedestrian on the near row is nearer than one on the far row,
            // so it is drawn larger and crosses the screen faster, by the same ratio the two
            // ground lines imply. People used to sit at a hardcoded 0.83 of screen height at a
            // fixed scale with a hand-rolled speed ladder -- outside the projection entirely,
            // which is the failure class `DESIGN_NOTES.md` 6 records against them.
            val near = i % 2 == 0
            val rowYFraction = if (near) SceneSpace.PAVEMENT_NEAR_Y_FRACTION else SceneSpace.PAVEMENT_FAR_Y_FRACTION
            val speed = if (near) SceneSpace.PEDESTRIAN_SPEED_NEAR else SceneSpace.PEDESTRIAN_SPEED_FAR
            val phase = i * 0.27f
            val y = screenHeight * rowYFraction
            val dir = if (reverse) -1f else 1f
            val s = SceneSpace.PERSON_BASE_SCALE * SceneSpace.perspectiveScaleAt(rowYFraction) * sceneScale

            // The walk, as a position on the ground rather than on the screen. `cycle` returns
            // 0..1 over one loop, which is a whole tile of walking; the start offset spreads the
            // four of them out so they are not a column.
            val walk = elapsedSeconds.cycle(speed, phase)
            val startFraction = (i + 0.5f) / candidateCount
            var tileFraction = (startFraction + dir * walk) % 1f
            if (tileFraction < 0f) tileFraction += 1f

            var x = geom.shiftXWrapped + tileFraction * geom.tileWidth
            if (x < -geom.tileWidth * 0.5f) x += geom.tileWidth

            val kindIdx = i % personKinds.size
            val frame = elapsedSeconds.frameIndex(3.2f, i.toFloat(), 4)
            val resId = personWalkDrawables[kindIdx][seasonIdx][frame]
            val halfWidth = PERSON_HALF_WIDTH_UNITS * s

            val firstTile = firstVisibleTileOffset(x, halfWidth, geom.tileWidth)
            val tileLimit = tileOffsetLimit(x, halfWidth, geom.tileWidth, screenWidth)
            for (tileIndex in firstTile until tileLimit) {
                val copyX = x + tileIndex * geom.tileWidth
                if (!isHorizontallyVisible(copyX, halfWidth, screenWidth)) continue
                canvas.save()
                canvas.translate(copyX, y)
                canvas.scale(dir * s, s)
                // Anchored on the sprite's own content box rather than on its canvas. Every walk
                // sprite is 43x84 local units with its content reaching the bottom edge, so the
                // feet land on the ground line at -84 and the figure is centred at -21.5.
                drawSprite(canvas, resId, PERSON_ANCHOR_X_UNITS, PERSON_ANCHOR_Y_UNITS)
                canvas.restore()
            }
        }
    }

    /**
     * Draws a compact row of sample objects (house, building, tree) colored from this renderer's
     * current [customization] -- independent of the normal layered-scene/parallax machinery, so
     * the settings screen can show an immediate, faithful (same drawing code as the real
     * wallpaper) live preview without needing a full scene around it. Cars and parasols are left
     * out of this compact preview (their drawing code depends on lane/road-position and
     * multi-wedge geometry that doesn't suit a small static row) — their colors are still fully
     * live on the actual wallpaper.
     */
    fun drawPreviewPair(canvas: SceneCanvas, screenWidth: Float, screenHeight: Float, dayBlend: Float) {
        val houseRuntime = StaticRuntime(StaticSceneObject(SceneObjectType.HOUSE, depthFraction = 0f, tileFractionX = 0f))
        val buildingRuntime = StaticRuntime(StaticSceneObject(SceneObjectType.SKYSCRAPER, depthFraction = 0f, tileFractionX = 1f))
        val treeRuntime = StaticRuntime(StaticSceneObject(SceneObjectType.TREE, depthFraction = 0.2f, tileFractionX = 0.25f))

        // The preview is a colour swatch, not a proportion reference: it has to fit three objects
        // of very different real heights into a 120 dp strip, so it magnifies the size table
        // rather than reproducing the scene's own projection. The *relative* sizes are still the
        // table's -- a tower is still taller than a house -- but each item carries its own fitting
        // factor so all three stay inside the box at any preview size.
        val previewScale = screenHeight / PREVIEW_REFERENCE_HEIGHT_PX

        drawPreviewItem(canvas, screenWidth * 0.22f, screenHeight * 0.88f, SceneSpace.SceneVariant.HOUSE_SMALL, previewScale, 1f) {
            drawSmallHouse(canvas, houseRuntime, SceneTime.ZERO, dayBlend)
        }
        drawPreviewItem(canvas, screenWidth * 0.52f, screenHeight * 0.94f, SceneSpace.SceneVariant.TREE, previewScale, 0.55f) {
            drawTree(canvas, treeRuntime, elapsed = SceneTime.ZERO, dayBlend = dayBlend)
        }
        drawPreviewItem(canvas, screenWidth * 0.80f, screenHeight * 0.96f, SceneSpace.SceneVariant.TOWER, previewScale, 0.34f) {
            drawSkyscraperBuilding(canvas, buildingRuntime, SceneTime.ZERO, dayBlend)
        }
    }

    private inline fun drawPreviewItem(
        canvas: SceneCanvas,
        x: Float,
        y: Float,
        variant: SceneSpace.SceneVariant,
        previewScale: Float,
        fit: Float,
        body: () -> Unit,
    ) {
        canvas.save()
        canvas.translate(x, y)
        val s = variant.baseScale * previewScale * fit
        canvas.scale(s, s)
        body()
        canvas.restore()
    }

    /**
     * A simple two-lane road band spanning the full screen width at the cars' lane height.
     * Like the cars themselves, it's independent of home-screen parallax (it belongs to the
     * "road" the cars drive on, not to any particular hill layer) -- matching the reference
     * app's own `Road` model (decompiled: `Road extends Sky`, `mScrolls` unset/false, i.e. its
     * *position* never moves). But the reference's `Road` also sets `mTextureScroll = true`, and
     * (decompiled `Sky.onUpdate`) advances its surface pattern using the exact same shared
     * `mParams.scrollSpeed` every other scrolling element uses -- not an independent rate. This
     * used to advance the dashed line by its own fixed `elapsedSeconds`-driven speed, unrelated to
     * [scrollSpeed]/swipe scroll entirely -- both wrong in the same direction the reference
     * corrects (a made-up independent rate) and the reported bug (dashes racing at a fixed pace no
     * matter how fast or slow -- or whether at all -- the rest of the scene was actually
     * scrolling). [shiftXWrapped]/[tileWidth] are the *exact* same values the nearest object row
     * already scrolls by (they're identical across every row now that there's a single hill
     * layer -- see `PaperRenderer.drawHillLayers`'s own doc comment), so the dashes are
     * guaranteed to read as flowing at the same rate as everything else around them, including
     * responding immediately to swipes and the scroll-speed setting, with zero new state to keep
     * in sync.
     */
    private fun drawRoad(canvas: SceneCanvas, dayBlend: Float, screenWidth: Float, screenHeight: Float, shiftXWrapped: Float, tileWidth: Float) {
        // Drawn whenever the theme has a road and the Cars category is switched on. Density is
        // deliberately not consulted: the road is terrain, and terrain does not change shape or
        // disappear because a slider moved. This used to return early on an empty [carRuntimes],
        // which conflated "this theme has no road" with "the density slider is at zero".
        if (!hasRoad || !customization.cars.visible) return

        // From the layout's own lane span, computed once at construction -- never from
        // [carRuntimes], which density filters. See [roadLaneMinFraction].
        val minLaneY = roadLaneMinFraction * screenHeight
        val maxLaneY = roadLaneMaxFraction * screenHeight
        // **The strip is symmetric about the centre line, and its edges come from the lanes.**
        // Each lane owns half of it, plus a shoulder stated as a fraction of the lane spacing --
        // so a road built from a custom theme's own saved lane pair stays in proportion instead
        // of gaining a verge sized for a different road. For the canonical lanes this is exactly
        // [SceneSpace.roadTopYFraction]/[SceneSpace.roadBottomYFraction].
        //
        // The old margins were 55 local units above and 12 below, chosen to keep a cabin inside
        // the painted strip. That is not what a road edge is for: a car is taller than the road
        // is wide and its roof is *supposed* to rise above the far edge. Group 4 removed the
        // reason the margin existed by making the vehicles the right size in the first place.
        val margin = screenHeight * SceneSpace.roadEdgeMarginFraction(roadLaneMinFraction, roadLaneMaxFraction)
        val top = minLaneY - margin
        val bottom = maxLaneY + margin
        val sceneScale = SceneSpace.sceneScale(screenHeight)

        fillPaint.color = ColorUtils.blendARGB(roadColorNight, roadColorDay, dayBlend.coerceIn(0f, 1f))
        canvas.drawRect(0f, top, screenWidth, bottom, fillPaint)

        strokePaint.style = Paint.Style.STROKE
        strokePaint.color = roadEdgeColor
        strokePaint.strokeWidth = SceneSpace.ROAD_EDGE_STROKE_PX * sceneScale
        canvas.drawLine(0f, top, screenWidth, top, strokePaint)
        canvas.drawLine(0f, bottom, screenWidth, bottom, strokePaint)

        // Dashed center line separating the two lanes -- offset by the same wrapped shift the
        // nearest object row scrolls by, so it reads as flowing "forward" together with
        // everything else under the cars, not painted static or drifting at its own made-up pace.
        //
        // Halfway between the two lanes' own ground lines, which is now also the midpoint of the
        // painted strip -- the two agree because the edges are derived from the lanes. A near car
        // painting over part of it is correct: it is standing between the viewer and the road
        // behind it.
        // Halfway between the layout's own two lanes, so the marking stays put at every density
        // exactly as the strip around it does.
        val midY = (minLaneY + maxLaneY) / 2f
        strokePaint.color = roadLineColor
        strokePaint.strokeWidth = SceneSpace.ROAD_CENTRE_LINE_STROKE_PX * sceneScale
        val dashLen = SceneSpace.ROAD_DASH_LENGTH_PX * sceneScale
        val gapLen = SceneSpace.ROAD_DASH_GAP_PX * sceneScale
        val period = dashLen + gapLen
        var offset = shiftXWrapped % period
        if (offset < 0f) offset += period
        var x = -period + offset
        while (x < screenWidth) {
            val segStart = x.coerceAtLeast(0f)
            val segEnd = (x + dashLen).coerceIn(0f, screenWidth)
            if (segEnd > segStart) canvas.drawLine(segStart, midY, segEnd, midY, strokePaint)
            x += period
        }
    }

    /**
     * Paints one tile copy of an object at [x]/[y], at the [effectiveScale] its depth and spec
     * call for.
     *
     * Culling is **not** done here. [draw] owns it, because the extent the decision needs is a
     * property of the object rather than of the copy, and deriving it per copy meant computing it
     * three times per object per frame to throw two away. Callers must therefore have established
     * that this copy is visible ([isHorizontallyVisible]) and must pass the same
     * [effectiveScale] the extent was derived from, or the object would be culled against one
     * size and drawn at another.
     */
    private fun drawStaticObject(canvas: SceneCanvas, r: StaticRuntime, x: Float, y: Float, effectiveScale: Float, dayBlend: Float, elapsed: SceneTime) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(effectiveScale, effectiveScale)

        // Dispatched on the same variant the scale was derived from, so an object can never be
        // sized as one drawing and painted as another.
        when (variantFor(r.spec)) {
            SceneSpace.SceneVariant.HOUSE_SMALL ->
                if (r.spec.type == SceneObjectType.HOUSE) drawSmallHouse(canvas, r, elapsed, dayBlend) else Unit
            SceneSpace.SceneVariant.HOUSE_LARGE -> drawLargeHouse(canvas, r, elapsed, dayBlend)
            // Never dispatched: a fir is a state a TREE candidate takes on while the Christmas
            // layer is on, not a placeable type of its own -- see [standsAsFir]. It exists in the
            // variant table only so the fir's height is governed by the same metre as the tree's.
            SceneSpace.SceneVariant.FIR -> Unit
            SceneSpace.SceneVariant.TOWER -> drawSkyscraperBuilding(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.RESTAURANT -> drawRestaurantBuilding(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.BAR -> drawBarBuilding(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.TREE -> drawTree(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.PALM_TREE -> drawPalmTree(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.PARASOL -> drawParasol(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.SNOWMAN -> drawSnowman(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.GIFT -> drawGift(canvas, r, dayBlend)
            SceneSpace.SceneVariant.PENGUIN -> drawPenguin(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.BUNNY -> drawBunny(canvas, r, elapsed, dayBlend)
            SceneSpace.SceneVariant.EASTER_EGG -> drawEasterEgg(canvas, r, dayBlend)
            SceneSpace.SceneVariant.PUMPKIN -> drawPumpkin(canvas, r, dayBlend)
        }
        canvas.restore()
    }

    /**
     * Reworked after aa reported v62's added detail (chimney, shingle lines, arched door,
     * foundation strip) as reading worse, not better -- compared directly against the
     * reference's actual house sprites (house1/house2/house3) instead of just tuning by eye, and
     * they're deliberately bold and flat: one flat-colored wall block, one flat-colored roof
     * triangle with barely any overhang, a plain rectangular door, and 1-2 plain rectangular
     * windows -- no shingle texture, no chimney, no arch, no foundation band. Stripped this back
     * to that same simplicity. Kept the ground shadow and a thin outline stroke -- those match
     * the "paper cutout" treatment this app's hills/clouds/mountains already established and
     * don't add fussy detail the way the removed elements did.
     */
    /**
     * Sprite-blit pilot conversion (see `SpriteCache`'s own doc comment for why): previously ~15
     * `canvas.drawRect`/`drawPath` calls re-walked and re-rasterized every frame, now 4 bitmap
     * blits (wall/roof/trim/window) each tinted via [drawTintedSprite] to the *exact same* color
     * values this function already computed -- no change to the color/day-night-blend logic
     * itself, only how the final pixels get painted.
     */
    /**
     * A string of Christmas lights hung under a window.
     *
     * **Under the window, not near the building.** The existing `drawChristmasLights` scatters
     * bulbs around a canopy's ellipse, which is the right shape for a tree and the wrong one for a
     * facade: on a wall it produced a cloud of dots beside the glass. This draws a slack cord
     * between two points on the window's own sill and hangs the bulbs off it, so the string is
     * where a real one is and moves with the window rather than with the building.
     *
     * Geometry only, and no new sprite: four bulbs and a two-segment cord per window is cheaper
     * than a blit, and the colours are the tree lights' own array so a house and its tree agree.
     * The window is not touched -- this is drawn after it and adds nothing to its box.
     */
    /**
     * Whether window [index] of [count] carries a light string, chosen once and for good.
     *
     * **Deterministic, spread, and capped.** The first version lit the three lowest floors of a
     * tower in one block, which is not how a building looks at Christmas and is not what anybody
     * asked for. This hashes the object's own seed with the window's index, so the pattern is
     * fixed for a given scene, differs between two buildings standing side by side, and does not
     * flicker frame to frame -- and [lit] caps how many any one facade may light, which is what
     * keeps the draw calls where they were.
     */
    private fun litWindowChosen(r: StaticRuntime, index: Int, count: Int, lit: Int): Boolean {
        if (lit >= count) return true
        val seed = (r.idleSeed * 100000f).toInt()
        var rank = 0
        val mine = hashWindow(seed, index)
        for (other in 0 until count) {
            if (other != index && hashWindow(seed, other) < mine) rank++
        }
        return rank < lit
    }

    private fun hashWindow(seed: Int, index: Int): Int {
        var h = seed * 0x9E3779B1.toInt() + index * 0x85EBCA77.toInt()
        h = h xor (h ushr 15)
        h *= 0xC2B2AE35.toInt()
        return h xor (h ushr 13)
    }

    private fun drawWindowLights(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, x: Float, sillY: Float, width: Float) {
        val left = x
        val right = x + width
        val sag = width * 0.16f
        strokePaint.color = 0xB0203528.toInt()
        strokePaint.strokeWidth = 1.1f
        canvas.drawLine(left, sillY, (left + right) / 2f, sillY + sag, strokePaint)
        canvas.drawLine((left + right) / 2f, sillY + sag, right, sillY, strokePaint)
        for (bulb in 0 until WINDOW_LIGHT_COUNT) {
            val along = (bulb + 1f) / (WINDOW_LIGHT_COUNT + 1f)
            val bx = left + width * along
            // The cord's own height at this point, so a bulb hangs off the string rather than
            // floating beside it: two straight segments meeting at the middle.
            val drop = sag * (1f - kotlin.math.abs(along - 0.5f) * 2f)
            val phase = r.idleSeed * 3.1f + bulb * 1.7f
            val blink = 0.55f + 0.45f * elapsed.sinAt(1.6f, phase)
            fillPaint.color = christmasLightColors[bulb % christmasLightColors.size]
            fillPaint.alpha = (255 * blink).toInt().coerceIn(70, 255)
            canvas.drawCircle(bx, sillY + drop + 1.6f, 1.5f, fillPaint)
        }
        fillPaint.alpha = 255
    }

    private fun drawSmallHouse(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        val wallColor = customization.colorFor(r.spec, dayBlend)
        val roofColor = ColorUtils.blendARGB(wallColor, 0xFF1A1410.toInt(), 0.45f)
        val trimColor = ColorUtils.blendARGB(wallColor, 0xFF000000.toInt(), 0.35f)
        val nightGlow = (1f - dayBlend).coerceIn(0f, 1f)

        // No `canvas.scale` correction here any more. There used to be one, shrinking the whole
        // house by 0.83 because the V2 wall is drawn at a larger native unit size than the sprite
        // the old `baseScale` was tuned against -- the per-asset patch `AI_PROJECT_RULES.md` 7.3
        // forbids, and the reason a house could not be compared with a bar. The house's size now
        // comes from [SceneSpace.SceneVariant.HOUSE_SMALL], which states its real height against
        // the 110 local units this function actually draws, so an unusual native size is absorbed
        // where it is declared rather than corrected where it is painted.
        drawGroundShadow(canvas, 40f)
        // wall: local bbox (-35,-70)-(35,0)
        drawTintedSprite(canvas, R.drawable.house_small_wall, -48f, -70f, wallColor)
        // roof: local bbox (-40,-110)-(40,-70)
        drawTintedSprite(canvas, R.drawable.house_small_roof, -53f, -110f, roofColor)
        // Snow settles on the roof in the winter and Christmas themes -- a layer *on* the roof,
        // cut to that roof's own outline, never the roof tinted white. Tinting it would repaint
        // the building rather than cover it, and `winterColorsEnabled` is already a palette
        // override, so the two would be indistinguishable. That shortcut was rejected when this
        // was defect D-8.
        //
        // Blitted between the roof and the chimney, so the chimney stands out of the drift rather
        // than under it. The origin is the roof's own, less the four units of crest the cap adds
        // above the ridge -- derived from the roof, so the two move together if either is redrawn.
        if (customization.winterColorsEnabled) {
            drawSprite(canvas, R.drawable.house_small_roof_snow, -34f, -114f)
        }
        drawTintedSprite(canvas, R.drawable.house_small_trim, -53f, -71f, trimColor)
        // chimney: local bbox (8,-115)-(20,-85) -- base sits on the roof slope (off-center,
        // right side) with enough of it above the ridge line to read as poking through, was
        // floating past the roof's edge entirely at the old centered position.
        drawTintedSprite(canvas, R.drawable.house_small_chimney, 8f, -115f, trimColor)
        drawChimneySmoke(canvas, r, x = 14f, topY = -115f)
        // window (left side) + flower planter beneath it.
        //
        // `house_shared_*` rather than a `house_small_*` pair: the small and large houses were
        // authored from the same window and planter drawing, and shipped as four PNGs holding two
        // pictures. Both variants now name the one drawable, so it is decoded once, occupies one
        // atlas entry, and cannot drift apart in one variant only. The two houses still differ
        // where they actually differ -- wall, roof, trim, chimney, door -- and the size difference
        // comes from the `canvas.scale` above, not from the artwork.
        // Window and door mirrored about the wall's own centre: a 22-unit window at -28 and a
        // 20-unit door at 8 put their centres at -17 and +18 on a wall running -35..35.
        // **Wider, after a device pass.** The facade was 70 local units across and the windows
        // reached to within two of each edge, so at the size a Pixel 9 draws it the pair read as
        // about to fall off the front. The wall is 86 now and the roof and eaves 96, keeping the
        // same five-unit overhang; the height is untouched, because the height is what
        // [SceneSpace.SceneVariant.HOUSE_SMALL] governs and it was already right. Six units of
        // facade either side of a window instead of two.
        //
        // **A window on each side of a centred door.** With one window and a door pushed to the
        // right, the elevation was asymmetric and short, and read as a cabin rather than a house.
        // The door now sits on the wall's own centre and the windows are mirrored about it, which
        // is what a small house actually looks like from the road.
        //
        // The second window is the same drawable at the same size, mirrored in position and not in
        // artwork: `house_shared_window` is one drawing used by both house variants, so a second
        // one cannot drift from the first. A 22-unit window centred at -22 and at +22, and a
        // 20-unit door centred on 0, all sit clear of each other on a wall running -35..35.
        drawSprite(canvas, R.drawable.house_shared_window, -37f, -45f)
        drawSpriteFaded(canvas, R.drawable.house_window_lit, -37f, -46f, litWindowAlpha(nightGlow))
        drawWindowOccupant(canvas, r, -37f, -46f, 22f, 22f)
        drawSprite(canvas, R.drawable.house_shared_window, 15f, -45f)
        drawSpriteFaded(canvas, R.drawable.house_window_lit, 15f, -46f, litWindowAlpha(nightGlow))
        if (customization.christmasDecorationsEnabled) {
            drawWindowLights(canvas, r, elapsed, -37f, -24f, 22f)
            drawWindowLights(canvas, r, elapsed, 15f, -24f, 22f)
        }
        drawSprite(canvas, R.drawable.house_shared_planter, -39f, -29f)
        drawFlowerDots(canvas, -33f, -29f)
        // door, centred between the two windows, with the porch light beside it
        drawTintedSprite(canvas, R.drawable.house_small_door, -10f, -38f, ColorUtils.blendARGB(wallColor, 0xFF000000.toInt(), 0.55f))
        drawPorchLight(canvas, x = 16f, y = -20f, nightGlow = nightGlow)
    }

    private fun drawLargeHouse(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        val wallColor = customization.colorFor(r.spec, dayBlend)
        val roofColor = ColorUtils.blendARGB(wallColor, 0xFF1A1410.toInt(), 0.45f)
        val trimColor = ColorUtils.blendARGB(wallColor, 0xFF000000.toInt(), 0.35f)
        val nightGlow = (1f - dayBlend).coerceIn(0f, 1f)

        // No `canvas.scale` correction here either -- see [drawSmallHouse]. The large house's
        // 145 local units are declared by [SceneSpace.SceneVariant.HOUSE_LARGE], so the two
        // houses are now sized against the same metre rather than against each other.
        drawGroundShadow(canvas, 70f)
        // wall: local bbox (-70,-95)-(70,0)
        drawTintedSprite(canvas, R.drawable.house_large_wall, -70f, -95f, wallColor)
        // roof: local bbox (-75,-145)-(75,-95)
        drawTintedSprite(canvas, R.drawable.house_large_roof, -75f, -145f, roofColor)
        // See [drawSmallHouse] for why this is a layer and not a tint.
        if (customization.winterColorsEnabled) {
            drawSprite(canvas, R.drawable.house_large_roof_snow, -50f, -149f)
        }
        // The V2 trim is 18px tall where the shipped one was 12, widened to the asset library's
        // 6-authoring-unit minimum for an internal border. The origin drops by one unit so the
        // border stays centred on the wall/roof seam instead of growing downward into the wall.
        drawTintedSprite(canvas, R.drawable.house_large_trim, -75f, -97f, trimColor)
        // chimney: local bbox (20,-150)-(33,-115) -- base sits on the roof slope off-center.
        drawTintedSprite(canvas, R.drawable.house_large_chimney, 20f, -150f, trimColor)
        drawChimneySmoke(canvas, r, x = 26f, topY = -150f)
        // Four windows across two floors, door centered between them on the ground floor.
        // Four windows in two columns, **symmetric about the door**. They sat at -55 and 15,
        // which is 15 units of wall to the left of the pair and 33 to the right on a wall that
        // runs -70..70: the whole facade read as pushed to one side. A 22-unit window at -46 and
        // 24 leaves 24 either side and centres the pair on the door, which is already at 0.
        val litAlpha = litWindowAlpha(nightGlow)
        drawSprite(canvas, R.drawable.house_shared_window, -46f, -84f)
        drawSpriteFaded(canvas, R.drawable.house_window_lit, -46f, -85f, litAlpha)
        drawSprite(canvas, R.drawable.house_shared_window, 24f, -84f)
        drawSpriteFaded(canvas, R.drawable.house_window_lit, 24f, -85f, litAlpha)
        drawWindowOccupant(canvas, r, -46f, -85f, 22f, 22f)
        drawSprite(canvas, R.drawable.house_shared_window, -46f, -44f)
        drawSpriteFaded(canvas, R.drawable.house_window_lit, -46f, -45f, litAlpha)
        drawSprite(canvas, R.drawable.house_shared_window, 24f, -44f)
        drawSpriteFaded(canvas, R.drawable.house_window_lit, 24f, -45f, litAlpha)
        if (customization.christmasDecorationsEnabled) {
            val sills = floatArrayOf(-46f, -63f, 24f, -63f, -46f, -23f, 24f, -23f)
            for (i in 0 until 4) {
                if (litWindowChosen(r, i, 4, 3)) {
                    drawWindowLights(canvas, r, elapsed, sills[i * 2], sills[i * 2 + 1], 22f)
                }
            }
        }
        drawSprite(canvas, R.drawable.house_shared_planter, -48f, -22f)
        drawFlowerDots(canvas, -42f, -22f)
        drawTintedSprite(canvas, R.drawable.house_large_door, -11f, -45f, ColorUtils.blendARGB(wallColor, 0xFF000000.toInt(), 0.55f))
        drawPorchLight(canvas, x = 40f, y = -22f, nightGlow = nightGlow)
    }

    /**
     * How strongly a house's lit-window overlay shows, from the same `nightGlow` the porch light
     * already uses.
     *
     * A house window used to be one greyscale mask multiplied by a colour interpolated from cold
     * daylight blue to warm lamp yellow, which is the only way a single mask can be both states.
     * The V2 asset set draws the two states instead -- `house_shared_window` is the daytime glass,
     * `house_window_lit` the same frame with the light on -- so the interpolation moves from the
     * tint to the overlay's alpha. The ramp is deliberately not linear in `nightGlow`: the lamp
     * reads as switched on rather than slowly dimmed up, so it stays dark through dusk and comes
     * up over the last third, matching how a porch light behaves.
     */
    private fun litWindowAlpha(nightGlow: Float): Int =
        (255f * ((nightGlow - 0.35f) / 0.45f).coerceIn(0f, 1f)).toInt()

    /** Shared cozy detail: a soft porch light glowing warmer at night, next to the door. */
    private fun drawPorchLight(canvas: SceneCanvas, x: Float, y: Float, nightGlow: Float) {
        fillPaint.color = 0xFFFFD97A.toInt()
        fillPaint.alpha = (60 + nightGlow * 90).toInt()
        canvas.drawCircle(x, y, 6f, fillPaint)
        fillPaint.alpha = 255
        canvas.drawCircle(x, y, 2.6f, fillPaint)
    }

    /** Shared cozy detail: 3 tiny flower dots on top of a planter box. */
    private fun drawFlowerDots(canvas: SceneCanvas, x: Float, y: Float) {
        val colors = intArrayOf(0xFFE85D9E.toInt(), 0xFFF2C230.toInt(), 0xFFE85D4A.toInt())
        for (i in 0 until 3) {
            fillPaint.color = colors[i]
            canvas.drawCircle(x + i * 8f, y, 2.5f, fillPaint)
        }
    }

    /**
     * A stable (never-flickering-per-frame) chance that this house instance has someone visible
     * at their window -- picked once from the house's own stable position hash, same technique
     * [drawSkyscraperBuilding]'s per-window lit/dark flicker seed uses, just without the
     * elapsed-time component since a person shouldn't pop in and out every frame the way a
     * lit-window flicker can. About 1 in 3 houses gets an occupant.
     */
    private fun drawWindowOccupant(canvas: SceneCanvas, r: StaticRuntime, winX: Float, winY: Float, winW: Float, winH: Float) {
        val seed = kotlin.math.abs((r.spec.tileFractionX * 9973f + winX).toInt())
        if (seed % 3 != 0) return
        val seasonIdx = if (customization.winterColorsEnabled) 1 else 0
        val kindIdx = seed % personKinds.size
        val resId = personWindowHeadDrawables[kindIdx][seasonIdx]
        // Placed from the sprite's declared anchor, not by centring its canvas -- the same
        // correction v76.1 made to the car driver, applied here for the same reason. The window
        // heads are 60x54 local units anchored CONTENT_BOTTOM_CENTRE, so centring the canvas put
        // the bust's shoulders a third of a pane below the sill. The bust now stands on the
        // window's own lower edge.
        val cx = winX + winW / 2f
        val cy = winY + winH
        canvas.save()
        canvas.translate(cx, cy)
        val s = (winW * 0.85f) / 60f // head sprite canvas is 60 units wide
        canvas.scale(s, s)
        drawSprite(canvas, resId, -WINDOW_HEAD_ANCHOR_X_UNITS, -WINDOW_HEAD_ANCHOR_Y_UNITS)
        canvas.restore()
    }

    /** Shared cozy detail: 3 softly fading smoke puffs rising from a chimney top. */
    private fun drawChimneySmoke(canvas: SceneCanvas, r: StaticRuntime, x: Float, topY: Float) {
        fillPaint.color = 0xFFE4E4DC.toInt()
        fillPaint.alpha = 178
        canvas.drawCircle(x + 3f, topY + 6f, 3f, fillPaint)
        fillPaint.alpha = 127
        canvas.drawCircle(x + 6f, topY - 3f, 4f, fillPaint)
        fillPaint.alpha = 76
        canvas.drawCircle(x + 9f, topY - 13f, 5f, fillPaint)
        fillPaint.alpha = 255
    }

    /**
     * Fall Colors / Winter-Christmas Colors override the normal per-tree leaf color/decoration --
     * see [SceneCustomization.fallColorsEnabled]'s own doc comment for why these live as a
     * palette override here rather than their own placeable category. Mutually exclusive by
     * construction (WallpaperPrefs clears one when the other is set), so at most one branch below
     * ever applies; neither active is the normal, unmodified tree.
     */
    /**
     * Sprite-blit pilot conversion (see `SpriteCache`'s own doc comment): the canopy -- previously
     * 3 `drawCircle` calls plus a `Path.op(UNION)` and a stroked outline pass every frame -- is
     * now one bitmap blit tinted to [leafColor]/the fall palette, with the winter snow-cap as a
     * second small blit only drawn when that's on. Trunk stays a plain `drawRect`: one flat-color
     * rectangle was already about as cheap as a vector draw can be, not worth a sprite for it.
     * No outline anymore -- matches the reference's own flat, unbordered canopy sprite (see
     * v63's own changelog entry on this exact "reference doesn't have one either" correction).
     */
    /**
     * Whether this tree stands as a Christmas fir.
     *
     * **One tree in three, decided from the tree's own seed.** Not a count and not a position: a
     * count would need state to distribute, and a position would put the firs on a line. Hashing
     * the seed gives about a third, differently for each theme's layout, and gives the *same*
     * third on every frame -- a wood that reshuffled itself as you watched would be worse than no
     * firs at all.
     */
    private fun standsAsFir(r: StaticRuntime): Boolean {
        if (!customization.christmasDecorationsEnabled) return false
        var h = (r.idleSeed * 100000f).toInt() * 0x9E3779B1.toInt()
        h = h xor (h ushr 16)
        return (h and 0x7FFFFFFF) % 3 == 0
    }

    /**
     * A fir in the leafy tree's place: tiers, snow while the winter palette is on, the tree
     * lights, and presents at its foot.
     *
     * The lights come from [drawChristmasLights], the same call the leafy tree makes, with the
     * ellipse pulled in to the fir's own narrower crown. The presents are `gift_box`/`gift_ribbon`
     * rather than a new sprite: they are the same objects the Gifts decoration already draws.
     */
    private fun drawFir(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime) {
        drawGroundShadow(canvas, 24f)
        drawSprite(canvas, R.drawable.tree_fir, -39f, -122f)
        if (customization.winterColorsEnabled) {
            drawSprite(canvas, R.drawable.tree_fir_snow, -39f, -122f)
        }
        drawChristmasLights(canvas, r, elapsed, centerY = -66f, radiusX = 22f, radiusY = 34f)
        // Three presents at the foot, sized against the fir rather than against the scene: the
        // gift sprite is 30 units on its own canvas and a fir is 122, so a third of it reads.
        val gifts = floatArrayOf(-19f, -2f, 14f)
        val sizes = floatArrayOf(0.36f, 0.30f, 0.26f)
        for (i in gifts.indices) {
            canvas.save()
            canvas.translate(gifts[i], 0f)
            canvas.scale(sizes[i], sizes[i])
            drawTintedSprite(canvas, R.drawable.gift_box, -20f, -30f, GIFT_COLOURS[i % GIFT_COLOURS.size])
            drawSprite(canvas, R.drawable.gift_ribbon, -20f, -40f)
            canvas.restore()
        }
    }

    private fun drawTree(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float = 1f) {
        if (standsAsFir(r)) {
            drawFir(canvas, r, elapsed)
            return
        }
        val sway = elapsed.sinAt(1.1f, r.idleSeed) * 4f
        drawGroundShadow(canvas, 26f)
        // The trunk was a flat `drawRect` from (-5,-38) to (5,0) with one hardcoded brown. V2
        // supplies it as art with bark banding and a darker side, anchored CONTENT_BOTTOM_CENTRE
        // at (27,132) -- 44 units tall against the rect's 38, which is not a discrepancy to
        // correct: the canopy's own content bottom lands at -44 too, so the two pieces meet
        // exactly where the library drew them to.
        drawSprite(canvas, R.drawable.tree_trunk, -5f, -44f)
        val leafColor = when {
            customization.fallColorsEnabled -> fallLeafColorFor(r)
            else -> customization.colorFor(r.spec, dayBlend)
        }
        canvas.save()
        canvas.translate(0f, -38f)
        canvas.rotate(sway)
        // **Halloween strips the crown.** The bare limbs blit at the canopy's own origin on the
        // canopy's own canvas, so they meet the trunk exactly where the leaves did and sway on the
        // same transform -- the tree is the same tree with a different crown, not a second object
        // placed where the first one was.
        //
        // Everything that decorates a canopy is skipped with it, and each for its own reason
        // rather than as one blanket condition: a snow cap is cut to the leaf silhouette and would
        // hang in mid-air over bare branches, and fairy lights are placed on the crown's ellipse,
        // which no longer has anything in it. Neither the winter flag nor the Christmas flag is
        // read or changed here; they simply have nothing to draw on a tree with no foliage.
        if (customization.halloweenEnabled) {
            drawSprite(canvas, R.drawable.tree_dead_branches, -41f, -80f)
            canvas.restore()
            return
        }
        // canopy: local bbox (-45,-84)-(45,0), refreshed in the aesthetic pass to a 5-lobe
        // silhouette (was a single blob) with its attachment point at local y=0 so it sits
        // flush on the trunk regardless of sway.
        drawTintedSprite(canvas, R.drawable.tree_canopy, -41f, -80f, leafColor)
        if (customization.winterColorsEnabled) {
            // The cap is cut to this canopy's own outline: its top edge repeats the crown's
            // upper vertices exactly, so the snow reaches both shoulders and the ridge instead
            // of sitting inside them. It was a 216x126 cap at (-36,-78), which is 2 units below
            // the crown's ridge and 5 short of each shoulder -- enough to leave a green rim
            // above the snow and bare green corners either side of it. Redrawn at 234x126 with
            // an origin derived from the crown rather than guessed; if the canopy art changes,
            // both move together.
            drawSprite(canvas, R.drawable.tree_canopy_snowcap, -41f, -80f)
        }
        // Inside the canopy's own transform, and scattered across the canopy's own measured
        // content: `tree_canopy` is 270x252 px with content at (12,12)-(258,234), which is
        // (-41,-80)..(41,-6) once blitted at this origin, so its centre is (0,-43) and its half
        // extents are 41 x 37. The radii below are inset from those for the crown's five lobes,
        // which pull the silhouette in near the top and bottom, and for the light's own radius.
        // The Christmas layer, not the winter one. These hung off `winterColorsEnabled`, which
        // made a plain snowy January impossible: every winter tree came with fairy lights.
        if (customization.christmasDecorationsEnabled) {
            drawChristmasLights(canvas, r, elapsed, centerY = -43f, radiusX = 30f, radiusY = 26f)
        }
        canvas.restore()
    }

    /** Deterministic per-tree autumn tone (orange/red/gold/rust) -- stable across frames since
     * it's derived purely from [r.idleSeed] (itself stable per candidate slot), not re-rolled
     * every draw. Independent of [dayBlend] on purpose: unlike the normal leaf color, which
     * blends toward a dedicated night variant, autumn leaves stay a flat warm tone day and night,
     * matching how the falling-leaf particles in [PaperRenderer.drawFallingLeaves] work too. */
    private fun fallLeafColorFor(r: StaticRuntime): Int {
        val palette = intArrayOf(
            0xFFD2691E.toInt(), 0xFFB5451B.toInt(), 0xFFE0A93A.toInt(), 0xFF8F3B1B.toInt(),
        )
        val index = (kotlin.math.abs(r.idleSeed * 1000).toInt()) % palette.size
        return palette[index]
    }

    /** Superseded by the `tree_canopy_snowcap` sprite blit in [drawTree] -- no longer called. */

    /** A handful of small colored dots around the canopy's outline, blinking on/off independently
     * (each light's own `elapsed`-based phase, same stateless-candidate spirit as
     * [PaperRenderer.drawFallingLeaves] -- no per-light list to manage between frames). Drawn
     * *outside* [drawTree]'s own canvas.save()/restore() for the swaying canopy, in the object's
     * own local space (translate(0,-40) matching the canopy's anchor, no rotate) so the lights
     * read as strung around the tree's outline rather than spinning with the sway. */
    /** [centerY]/[radius] let a caller adapt the light positions to its own crown shape --
     * previously hardcoded to a regular tree's canopy (center -40, ~22 radius), which put the
     * lights in the wrong place entirely when [drawPalmTree] started calling this too (a palm's
     * crown is centered at -62, not -40, and spreads wider). */
    /**
     * The light colours, and the unscaled local positions of the six lights, hoisted out of
     * [drawChristmasLights].
     *
     * They were built inside it -- an `intArrayOf`, an `arrayOf(x to y, ...)` and a `.map` over
     * it -- so every call allocated an int array, an array of six boxed `Pair<Float, Float>`, a
     * second array for the mapped result and a `List` wrapper, and boxed fourteen floats on the
     * way. The function runs once per tree and once per palm, for every wrap-tile copy of each,
     * on every frame the winter palette is on. Nothing in it depends on the object being drawn,
     * so it is constant data that was being rebuilt in the render loop.
     *
     * Kept as two parallel `FloatArray`s rather than an array of points, because an array of
     * points is what the boxing came from.
     */
    private val christmasLightColors = intArrayOf(
        0xFFE8564F.toInt(), 0xFFFFD54F.toInt(), 0xFF4F8FBF.toInt(), 0xFF6FCF6F.toInt(),
    )
    /**
     * Where the lights sit, as offsets in a **unit disc** rather than in local units.
     *
     * They used to be absolute offsets around a hand-picked centre, and that is why they hung out
     * of the bottom of the foliage: the tree's cloud reached y=-2 against a canopy whose content
     * stopped at -6, so the lowest lights were below the leaves and out over the trunk, and the
     * highest reached barely half way up a crown twice as tall as the cloud. Neither number was
     * derived from the artwork they were meant to be scattered across.
     *
     * Every offset here is within 0.81 of the centre, so a caller that passes its own foliage's
     * measured half-width and half-height gets lights inside it by construction, whatever shape
     * that foliage is next redrawn to.
     */
    private val christmasLightX = floatArrayOf(-0.80f, -0.34f, 0.06f, 0.40f, 0.78f, -0.06f)
    private val christmasLightY = floatArrayOf(0.10f, -0.52f, 0.46f, -0.52f, 0.14f, -0.08f)

    /**
     * Scatters blinking lights across an ellipse of foliage centred on ([centerY]) in whatever
     * space the caller is currently drawing in.
     *
     * Called from **inside** each plant's own sway transform, so the lights lean with the branches
     * instead of staying rigid while the leaves move around them -- which also removes any need to
     * leave slack at the edges for the sway to swing into.
     */
    private fun drawChristmasLights(
        canvas: SceneCanvas,
        r: StaticRuntime,
        elapsed: SceneTime,
        centerY: Float,
        radiusX: Float,
        radiusY: Float,
    ) {
        canvas.save()
        canvas.translate(0f, centerY)
        for (i in christmasLightX.indices) {
            val phase = ((r.idleSeed + i * 0.37f) * 10f) % 6.283f
            val blink = (elapsed.sinAt(2.4f, phase) * 0.5f + 0.5f)
            if (blink < 0.35f) continue // off phase of the blink cycle
            // Scaled here rather than in a pre-built list: the multiplication is the same one,
            // per light, in the same order, and this way it happens only for the lights that are
            // actually drawn this frame.
            val lx = christmasLightX[i] * radiusX
            val ly = christmasLightY[i] * radiusY
            fillPaint.color = christmasLightColors[i % christmasLightColors.size]
            fillPaint.alpha = 255
            canvas.drawCircle(lx, ly, CHRISTMAS_LIGHT_RADIUS_UNITS, fillPaint)
        }
        canvas.restore()
    }

    /**
     * Sprite-blit conversion (aesthetic-pass batch 4): the 3-circle body is now one bitmap blit
     * (`snowman_body`, local bbox (-22,-78)-(22,0)) tinted to the user's color, replacing 3
     * `drawCircle` + 3 stroked-outline calls every frame. Carrot nose and scarf are fixed-color
     * accent sprites (never user-tintable, matching how the restaurant's awning stayed a fixed
     * accent when its wall/window/door went sprite-based). Twig arms stay vector -- 2 stroked
     * lines are already cheap and their exact angle doesn't warrant a sprite.
     */
    private fun drawSnowman(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        val wobble = elapsed.sinAt(1.4f, r.idleSeed) * 2f
        drawGroundShadow(canvas, 22f)
        canvas.save()
        canvas.rotate(wobble)
        val snow = customization.colorFor(r.spec, dayBlend)
        // -74, not -75: the body's content bottom is 74 units down its 75-unit canvas, so the
        // old origin left the whole snowman standing one unit clear of the ground it casts a
        // shadow on (defect D-9). The face and scarf move by the same unit, because what is
        // being corrected is where the *drawing* sits, not how its pieces register against each
        // other.
        drawTintedSprite(canvas, R.drawable.snowman_body, -19f, -74f, snow)
        // The three accessories are placed against the V2 body's own landmarks, measured off it:
        // hat brim to y=-63, head sphere -61..-39 centred on -50, neck (its narrowest row) at
        // -38, and the lower sphere widest at -20. The shipped origins were tuned for a body
        // built from three plain circles, so the carrot sat level with the hat brim and the scarf
        // lay across the middle of the face.
        drawSprite(canvas, R.drawable.snowman_nose, 4f, -51f)
        drawSprite(canvas, R.drawable.snowman_scarf, -12f, -40f)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 3f
        strokePaint.color = 0xFF7A4B2E.toInt()
        // Twig arms come out of the **torso**, not the head. y=-44 is inside the head sphere,
        // which spans -61..-39 on the V2 body, so the arms appeared to be stuck through his face.
        // The lower sphere is widest around -20 and reaches +/-15.7 at -30, which is where they
        // start from now.
        canvas.drawLine(-14f, -30f, -32f, -40f, strokePaint)
        canvas.drawLine(14f, -30f, 30f, -36f, strokePaint)
        strokePaint.strokeWidth = 2.5f
        canvas.restore()
    }

    /**
     * Sprite-blit conversion (aesthetic-pass batch 4): box is a tinted sprite (`gift_box`, local
     * bbox (-21,-31)-(21,1)); ribbon+bow is a separate sprite kept as its own layer (rather than
     * baked into the box art) so the ribbon stays independent of the user's chosen gift-box
     * colour, exactly as before. The ribbon carries its own gold in the V2 artwork, so it is
     * blitted untinted where it used to be a white mask multiplied by a constant.
     */
    private fun drawGift(canvas: SceneCanvas, r: StaticRuntime, dayBlend: Float) {
        val color = customization.colorFor(r.spec, dayBlend)
        drawGroundShadow(canvas, 22f)
        drawTintedSprite(canvas, R.drawable.gift_box, -20f, -30f, color)
        drawSprite(canvas, R.drawable.gift_ribbon, -20f, -40f)
    }

    /**
     * Fixed after aa reported Fall Colors/Winter Colors having no effect on the Beach theme's
     * trees. Root cause: Beach uses `treeType = PALM_TREE` (see `SceneObjectCatalog.
     * uniformCandidates`'s own `treeType` parameter), routed to this function -- but the
     * fallColorsEnabled/winterColorsEnabled branches only ever existed in [drawTree] (the
     * non-palm variant), so a palm tree never even checked either flag. Added the same two
     * branches here, adapted to a palm's shape: fall tints the fronds with the same autumn
     * palette [fallLeafColorFor] uses for regular trees; winter dusts frost-white tips on the
     * fronds (full snow-covered fronds would look wrong on a palm) and adds the same string of
     * blinking Christmas lights [drawChristmasLights] gives a regular tree, now along the trunk.
     *
     * **The fall branch is gone as of the V2 asset set, deliberately.** The fronds are drawn in
     * their own green rather than as a mask, so there is no tint left for an autumn palette to
     * occupy; multiplying finished art by an orange would compound two colours, not recolour one.
     * The winter treatment survives unchanged because it was never a tint: it is a separate frost
     * sprite laid over the fronds, plus the lights. A palm therefore no longer responds to Fall
     * Colors, which is a consequence of the redesign and not a defect to patch at this call site.
     */
    /**
     * Sprite-blit pilot conversion (see `SpriteCache`'s own doc comment). The old version bent
     * the trunk's own curve control points and rotated each of the 5 fronds independently every
     * frame -- a static bitmap can't bend, so this simplifies the sway to one rigid rotation of
     * the *whole tree* (trunk + frond cluster together) pivoted at the base, rather than trying
     * to reproduce independent per-frond motion. Visually a very close match (the old per-frond
     * rotation was already just each frond's fixed angle plus one shared `sway` term, i.e.
     * already almost a rigid rotation in practice) at a fraction of the per-frame cost: 2 bitmap
     * blits plus one shared `canvas.rotate`, replacing what used to be a trunk path rebuild plus
     * 5 separate frond path rebuilds + 5 independent rotate/restore pairs every frame.
     */
    private fun drawPalmTree(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float = 1f) {
        val sway = elapsed.sinAt(0.9f, r.idleSeed) * 6f
        drawGroundShadow(canvas, 22f)

        canvas.save()
        canvas.rotate(sway) // whole tree leans as one rigid body, pivoted at its base (0,0)
        // trunk: 42x186, anchored CONTENT_BOTTOM_CENTRE at (24,186), so the origin that stands it
        // on the ground with its base centred on the pivot is (-8,-62). It was 33 wide and
        // originated at -6; V2 widened it to carry the new frond fan.
        drawSprite(canvas, R.drawable.palmtree_trunk, -6f, -58f)
        // fronds: the attachment point is no longer measured off the artwork, it is declared.
        // The V2 fan is 120x120 with a DECLARED_ATTACHMENT at (60,102) -- (20,34) in local units
        // -- which is the point where the blades converge. The trunk's own content top sits at
        // -58.33 (11px of the 186 are transparent above the bark), and the attachment is placed
        // two units below that at -56.33 so the fan overlaps the trunk rather than balancing on
        // it. Origin is therefore attachment - (20,34) = (-20,-90.33). The old pair of hand-tuned
        // numbers (-16,-87.45) described a differently shaped sprite and does not transfer.
        //
        // The fronds are fixed art in V2 and no longer follow the tree colour or Fall Colors --
        // see this class's own note on the retired accent constants, and `DESIGN_NOTES.md`.
        // **Halloween reaches the palms too.** The leafy trees lost their canopy from the first
        // release of the flag and the palms did not, so a Halloween beach kept a row of healthy
        // green fans over its bare-branch neighbours. The dead crown is drawn on the live one's
        // canvas with the same content box, so it blits at the same origin and the frost overlay
        // and the light ellipse below keep the geometry they were derived from.
        //
        // Desaturating the live fan was the cheaper option and the wrong one: a grey palm is a
        // palm in bad light, not a dead one. The drooping, splayed fronds are what carry it.
        if (customization.halloweenEnabled) {
            drawSprite(canvas, R.drawable.palmtree_fronds_dead, -20f, -90.33f)
        } else {
            drawSprite(canvas, R.drawable.palmtree_fronds, -20f, -90.33f)
        }
        // Frost is the season; the lights are the decoration. Two flags, tested separately, so a
        // frosted palm without lights and a lit palm without frost are both expressible.
        if (customization.winterColorsEnabled && !customization.halloweenEnabled) {
            drawSprite(canvas, R.drawable.palmtree_fronds_frost, -20f, -90.33f)
        }
        if (customization.christmasDecorationsEnabled) {
            // Same derivation as the leafy tree's, from the fan's own content: 120x120 px with
            // content at (0,0)-(120,110) is (-20,-90.33)..(20,-53.67) at this origin, centred on
            // (0,-72) with half extents 20 x 18.33. Inset further than the leafy tree's because a
            // frond fan is mostly gaps -- a light near its edge would hang in clear air.
            drawChristmasLights(canvas, r, elapsed, centerY = -72f, radiusX = 13f, radiusY = 10f)
        }
        canvas.restore()
    }

    private fun drawParasol(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        // Ground shadow at the pole's base -- same "doesn't look like it's floating" fix
        // drawHouse's own foundation strip already solves for houses (see its doc comment).
        // v53 widened/darkened this shadow after a first "floating" report, but aa reported the
        // exact same thing again in v59. Re-verified the anchoring math itself numerically again
        // (groundY still sits safely within the hill's guaranteed-solid zone -- unrelated to this
        // fix) and found the actual cause was never the shadow at all: the canopy below used to
        // sway with a *vertical* bob (`canvas.translate(0f, -50f + sin(...)*1.5f)`) while the pole
        // was drawn separately and stayed rigid, always ending exactly at y=-50. That let the
        // canopy's attachment point drift up and down relative to the fixed pole tip every frame
        // -- a periodic gap/overlap between pole and canopy that reads exactly as "hovering",
        // regardless of how solid the ground shadow is. A real planted parasol's canopy doesn't
        // bob vertically at its mount point; at most it sways side to side in the wind. Replaced
        // the vertical translate with a small rotation pivoted at the pole tip (0, -50) instead --
        // the canopy now visibly stays attached to the pole at every point in the sway, and still
        // reads as alive/breezy rather than static.
        fillPaint.color = 0x55000000
        canvas.drawOval(-22f, -4f, 22f, 4f, fillPaint)
        fillPaint.color = parasolPoleColor
        canvas.drawRect(-2.5f, -50f, 2.5f, 0f, fillPaint)
        val sway = elapsed.sinAt(1.6f, r.idleSeed) * 2.5f
        canvas.save()
        canvas.translate(0f, -50f)
        canvas.rotate(sway)
        val sweep = 36
        for (i in 0 until 5) {
            fillPaint.color = customization.parasolStripeColor(i, dayBlend)
            // One filled sector per stripe. It was a Path built from moveTo + arcTo + close, which
            // is the same shape said in terms neither backend can share: a sector is a primitive
            // both can generate directly, an arc segment inside a Path is not.
            canvas.drawWedge(0f, 0f, 34f, 180f + i * sweep, sweep.toFloat(), fillPaint)
        }
        canvas.restore()
    }

    /**
     * A stepped setback tier and a rooftop canopy give the silhouette something to read as other
     * than a plain rectangle. The wall and the setback stay tintable so the building follows the
     * category's colour; the canopy is fixed art, the same way the police lightbar and the taxi
     * chequer are fixed accents on a tintable car body.
     *
     * **The window grid is no longer drawn here.** It was a nested loop of `drawRect` calls with
     * a per-window pseudo-random lit/dark roll, kept as vector precisely so each building could
     * light differently. The V2 asset set supplies both states as artwork -- the daytime grid is
     * part of `skyscraper_wall`, the night one is `skyscraper_wall_lit` -- so the loop is gone
     * and with it the per-building variation. See the overlay's own comment below.
     */
    private fun drawSkyscraperBuilding(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        val height = 150f
        val width = 90f
        val wallColor = customization.colorFor(r.spec, dayBlend)
        val trimColor = ColorUtils.blendARGB(wallColor, 0xFF000000.toInt(), 0.35f)

        val nightGlow = (1f - dayBlend).coerceIn(0f, 1f)

        drawGroundShadow(canvas, width * 0.6f)
        drawSprite(canvas, R.drawable.skyscraper_canopy, -55f, -6f)
        drawTintedSprite(canvas, R.drawable.skyscraper_wall, -width / 2f, -height, wallColor)
        // The daytime window grid is drawn into `skyscraper_wall` itself now, and the night one
        // is a second full-wall drawing laid over it at the same origin -- both 270x450, so the
        // origin is shared rather than re-derived. This replaces a nested loop that rebuilt ~24
        // `drawRect` calls per building per wrap-tile per frame, with a per-window pseudo-random
        // lit/dark roll. The roll is what is lost: every building's night facade now shows the
        // same lit pattern. That is the trade the asset set makes, and it removes the last
        // per-frame vector work from this building style.
        drawSpriteFaded(canvas, R.drawable.skyscraper_wall_lit, -width / 2f, -height, litWindowAlpha(nightGlow))
        // **The entrance, on the ground the building and the people stand on.** Blitted after the
        // wall so it sits in the hall band the facade draws, and with its own bottom edge on y=0:
        // the canopy straddles the ground line and is a plinth, not a floor to stand a door on.
        drawSprite(canvas, R.drawable.skyscraper_entrance, -16f, -32f)
        // The tower's windows are painted into its wall, so there is no per-window call site to
        // hang a string from. The grid is stated by the artwork: four rows of four 14-unit windows
        // at a 27 pitch from the top, stopping clear of the 32-unit hall. Twelve of the sixteen
        // are lit, chosen by hash rather than by position, which is the same draw-call ceiling the
        // three-lowest-floors version had.
        if (customization.christmasDecorationsEnabled) {
            for (row in 0 until 4) {
                for (column in 0 until 4) {
                    val index = row * 4 + column
                    if (!litWindowChosen(r, index, 16, 12)) continue
                    drawWindowLights(
                        canvas, r, elapsed,
                        -width / 2f + 5f + column * 20f,
                        -height + 5f + row * 27f + 14f,
                        14f,
                    )
                }
            }
        }
        drawTintedSprite(canvas, R.drawable.skyscraper_setback, -30f, -height - 32f, wallColor)
        // The setback's roof is the only horizontal surface of a tower a viewer sees, so it is
        // where the snow goes. Its own block starts 6 units down its canvas, and the cap carries 8
        // units above the roofline it is cut for, hence the offset. Drawn before the mast, so the
        // mast rises out of the drift. See [drawSmallHouse] for why this is a layer and not a tint.
        if (customization.winterColorsEnabled) {
            drawSprite(canvas, R.drawable.skyscraper_roof_snow, -28f, -height - 32f + 6f - 8f + 3f)
        }
        strokePaint.color = trimColor
        strokePaint.strokeWidth = 2f
        canvas.drawLine(0f, -height - 32f, 0f, -height - 46f, strokePaint)
        strokePaint.strokeWidth = 2.5f
        fillPaint.color = 0xFFE85D4A.toInt()
        canvas.drawCircle(0f, -height - 46f, 2.5f, fillPaint)
    }

    /**
     * Sprite-blit conversion (aesthetic-pass batch 2, refreshed in batch 4): wall/awning/door/
     * window are bitmap blits. Aesthetic-pass batch 4 additionally hangs a fork-and-knife sign
     * (a universally-readable "restaurant" symbol, replacing the awning as the primary
     * identifier since a striped awning alone reads as ambiguous as any other shop) -- a fixed
     * accent sprite, not tinted, same reasoning as the awning's own fixed red/white stripes.
     */
    private fun drawRestaurantBuilding(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        val wallColor = customization.colorFor(r.spec, dayBlend)
        val doorColor = ColorUtils.blendARGB(wallColor, 0xFF000000.toInt(), 0.35f)

        drawGroundShadow(canvas, 50f * 0.58f)

        // wall: local bbox (-50,-60)-(50,0)
        drawTintedSprite(canvas, R.drawable.restaurant_wall, -50f, -96f, wallColor)
        // A flat roof, so the cap is a drift standing proud of the parapet rather than following a
        // pitch. Its canvas puts the wall's own top edge 8 units down. See [drawSmallHouse].
        if (customization.winterColorsEnabled) {
            drawSprite(canvas, R.drawable.restaurant_roof_snow, -48f, -102f)
        }
        // awning: local bbox (-34,-46)-(34,-36), fixed red/white stripes (not tinted)
        drawSprite(canvas, R.drawable.restaurant_awning, -34f, -46f)
        // window, lit warm at night
        val nightGlow = (1f - dayBlend).coerceIn(0f, 1f)
        val windowColor = ColorUtils.blendARGB(0xFFB9CBD9.toInt(), 0xFFFFE79A.toInt(), nightGlow)
        drawTintedSprite(canvas, R.drawable.restaurant_window, -35f, -45f, windowColor)
        if (customization.christmasDecorationsEnabled) {
            drawWindowLights(canvas, r, elapsed, -35f, -22f, 30f)
        }
        // door
        drawTintedSprite(canvas, R.drawable.restaurant_door, 8f, -28f, doorColor)
        // hanging fork-and-knife sign
        strokePaint.color = 0xFF3D2B1F.toInt()
        strokePaint.strokeWidth = 2f
        strokePaint.style = Paint.Style.STROKE
        canvas.drawLine(0f, -60f, 0f, -78f, strokePaint)
        strokePaint.strokeWidth = 2.5f
        drawSprite(canvas, R.drawable.restaurant_sign, -17f, -96f)
    }

    /**
     * Sprite-blit conversion (aesthetic-pass batch 2, refreshed in batch 4): wall and door are
     * bitmap blits. The hanging sign now shows a beer-mug icon (a fixed accent sprite) instead
     * of a plain glowing circle, so "bar" reads immediately instead of depending on the reader
     * already knowing it's a bar. String lights stay vector, unchanged.
     */
    private fun drawBarBuilding(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        val height = 55f
        val width = 90f
        val wallColor = customization.colorFor(r.spec, dayBlend)
        val doorColor = ColorUtils.blendARGB(wallColor, 0xFF000000.toInt(), 0.35f)

        drawGroundShadow(canvas, width * 0.6f)

        // wall: local bbox (-45,-55)-(45,0)
        drawTintedSprite(canvas, R.drawable.bar_wall, -45f, -92f, wallColor)
        // Same construction as the restaurant's, cut to this wall's narrower 90 units.
        if (customization.winterColorsEnabled) {
            drawSprite(canvas, R.drawable.bar_roof_snow, -43f, -98f)
        }
        // door
        drawTintedSprite(canvas, R.drawable.bar_door, -10f, -28f, doorColor)
        // The upper storey's windows, the same drawable the houses use so a shop's first floor
        // cannot drift from a house's.
        val barLit = litWindowAlpha((1f - dayBlend).coerceIn(0f, 1f))
        for (wx in floatArrayOf(-34f, -11f, 12f)) {
            drawSprite(canvas, R.drawable.house_shared_window, wx, -82f)
            drawSpriteFaded(canvas, R.drawable.house_window_lit, wx, -83f, barLit)
        }
        if (customization.christmasDecorationsEnabled) {
            for ((i, wx) in floatArrayOf(-34f, -11f, 12f).withIndex()) {
                if (litWindowChosen(r, i, 3, 2)) drawWindowLights(canvas, r, elapsed, wx, -61f, 22f)
            }
        }

        // Hanging beer-mug sign, glowing warm at night.
        val nightGlow = (1f - dayBlend).coerceIn(0f, 1f)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 1.5f
        strokePaint.color = 0xFF3D2B1F.toInt()
        canvas.drawLine(0f, -height - 10f, 0f, -height, strokePaint)
        strokePaint.strokeWidth = 2.5f
        fillPaint.color = 0xFFFFD54A.toInt()
        fillPaint.alpha = (60 + nightGlow * 90).toInt()
        canvas.drawCircle(0f, -height - 30f, 24f, fillPaint)
        fillPaint.alpha = 255
        // On the facade, at a shop's own scale. It was 36 units hung above the roof, which at
        // v2.7's metres read as a 5.5 m sign floating free of the building; it is 24 units now and
        // sits on the upper storey where a shop sign is.
        drawSprite(canvas, R.drawable.bar_sign, -12f, -84f)

        // String lights along the top edge.
        fillPaint.color = ColorUtils.blendARGB(0xFF8A6A50.toInt(), 0xFFFFE79A.toInt(), nightGlow)
        for (i in 0 until 4) {
            val lx = -width / 2f + 8f + i * ((width - 16f) / 3f)
            canvas.drawCircle(lx, -height + 6f, 2.5f, fillPaint)
        }
    }

    /** Body and belly are tintable masks; the beak and the feet carry their own orange in the V2
     * artwork and are blitted untinted, where they used to be white masks multiplied by a
     * constant. Waddle rotation stays a `canvas.rotate` around the whole group, same as before. */
    private fun drawPenguin(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        val waddle = elapsed.sinAt(4f, r.idleSeed) * 4f
        drawGroundShadow(canvas, 16f)
        canvas.save()
        canvas.rotate(waddle * 0.5f)
        val body = customization.colorFor(r.spec, dayBlend)
        drawTintedSprite(canvas, R.drawable.penguin_body, -14f, -45f, body)
        drawTintedSprite(canvas, R.drawable.penguin_belly, -9f, -38f, penguinBellyColor)
        // -46 is the very top of the V2 body, above the eyes; the face sits around -37.
        drawSprite(canvas, R.drawable.penguin_beak, -6f, -37f)
        drawSprite(canvas, R.drawable.penguin_feet, -10f, 0f)
        canvas.restore()
    }

    /** The shell is a tintable mask so it follows the user's colour; the decorative stripe and
     * dots are fixed art blitted over it. */
    private fun drawEasterEgg(canvas: SceneCanvas, r: StaticRuntime, dayBlend: Float) {
        val shell = customization.colorFor(r.spec, dayBlend)
        drawGroundShadow(canvas, 15f)
        drawTintedSprite(canvas, R.drawable.easteregg_shell, -16f, -40f, shell)
        drawSprite(canvas, R.drawable.easteregg_pattern, -16f, -25f)
    }

    /** Body, head and both ears are one tintable sprite (`bunny_body`, drawn already-assembled so
     * the ears can't drift from the head the way independently-positioned sprites risked); the
     * inner ear and the tail are fixed art blitted on top. Ear wiggle now rotates the whole bunny slightly rather than each ear
     * independently, since the ears are baked into the same bitmap as the body -- a small
     * trade-off for guaranteed attachment, same rationale as the palm tree's rigid-sway
     * conversion documented above. */
    private fun drawBunny(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        val earWiggle = elapsed.sinAt(3f, r.idleSeed) * 2.5f
        val body = customization.colorFor(r.spec, dayBlend)
        drawGroundShadow(canvas, 18f)
        canvas.save()
        canvas.rotate(earWiggle * 0.4f)
        // -61, not -62: same one-unit lift as the snowman (defect D-9). The ears and tail move
        // with it. The horizontal origin is deliberately not the content centre -- see the ear
        // note below -- and is left alone.
        drawTintedSprite(canvas, R.drawable.bunny_body, -14f, -61f, body)
        // The V2 ears occupy x -9.3..15.3, so their centre is at 3 and the two-ear inner patch --
        // 14.7 units of content -- centres on it from here. At 6 it covered the right ear and
        // hung the left patch in mid-air beside the head.
        drawSprite(canvas, R.drawable.bunny_innerear, -4f, -57f)
        drawSprite(canvas, R.drawable.bunny_tail, -21f, -10f)
        canvas.restore()
    }

    /** Sprite-blit conversion (aesthetic-pass batch 4): the 3-lobe body is one tinted sprite
     * (`pumpkin_body`); stem+leaf stay a fixed-color accent sprite on top. */
    private fun drawPumpkin(canvas: SceneCanvas, r: StaticRuntime, dayBlend: Float) {
        val base = customization.colorFor(r.spec, dayBlend)
        drawGroundShadow(canvas, 16f)
        drawTintedSprite(canvas, R.drawable.pumpkin_body, -19f, -30f, base)
        drawSprite(canvas, R.drawable.pumpkin_stem, 2f, -42f)
    }

    /**
     * The fire truck's own body, in the same local space [drawCar] establishes for every vehicle:
     * ground contact at y=37, wheel centres at (+/-38, 28).
     *
     * It exists because the fire truck used to be `car_body` tinted red with a ladder blitted
     * over it, which gave it the same low-sedan outline as the taxi and the police car -- the
     * one thing a fire engine must not have. `firetruck_body` is 300x162: a flat roof at y=-16
     * against the sedan's -11, a cab with its own window, a body divided by a cream stripe over
     * three equipment lockers, and a dark chassis bar that the wheels sit into.
     *
     * The ladder is drawn **first**, so the body's roof line paints over its lower rail and the
     * ladder reads as carried on the roof rather than hovering above it. Its own origin was the
     * other half of the defect: at `-60f, -32f` it sat clear of the sedan roof entirely, which is
     * what made it look detached. The two warning lights are unchanged, and now land on the rack
     * between the ladder's rails.
     */
    private fun drawFireTruck(canvas: SceneCanvas) {
        drawSprite(canvas, R.drawable.firetruck_ladder, -48f, -30f)
        drawSprite(canvas, R.drawable.firetruck_body, -49f, -22f)
        fillPaint.color = 0xFFD6362E.toInt()
        canvas.drawRect(-16f, -22f, -6f, -16f, fillPaint)
        fillPaint.color = 0xFF2B5FCB.toInt()
        canvas.drawRect(-4f, -22f, 6f, -16f, fillPaint)
    }

    /**
     * Sprite-blit conversion (aesthetic-pass batch 3): body/window are now bitmap blits instead
     * of a `Path`+2 `drawRect` calls every frame, and the single generic car now comes in 4
     * vehicle types (see [CarType]) -- [CarType.PLAIN] keeps the exact same user-tintable
     * behavior as before (`customization.colorFor`), the 3 special types use fixed real-world
     * colors plus their own small accessory sprite (police light bar, taxi checker stripe, fire
     * truck roof ladder) blitted on top. Wheels stay vector (2 circles + 2 stroked circles,
     * already cheap, shared unchanged by every type).
     */
    private fun drawCar(canvas: SceneCanvas, c: CarRuntime, screenWidth: Float, screenHeight: Float, dayBlend: Float) {
        val margin = 120f
        val travel = screenWidth + margin * 2f
        val rawX = c.progress * travel - margin
        val x = if (c.spec.reverse) screenWidth - rawX else rawX
        val y = c.spec.laneYFraction * screenHeight
        // **The V2 vehicle artwork faces left**, so the unflipped blit is the one that belongs to
        // a car driving leftward -- the opposite of what the shipped set was authored for. The
        // sign was not touched when the artwork was replaced, which is why every car on the road
        // drove backwards. Read it off the art rather than from this comment if it changes again:
        // `car_body`'s long bonnet is at its left end and `car_window`'s raked edge is on the same
        // side, and a windscreen rakes toward the front.
        val dir = if (c.spec.reverse) 1f else -1f

        // A vehicle is on the ground plane like everything else, so its size comes from the same
        // projection: its own lane's [SceneSpace.perspectiveScaleAt], not a flat global factor.
        // Cars used to be drawn at one fixed scale whatever lane they were in, which is what let
        // a car end up taller than a pedestrian and made the two lanes read as one row at two
        // heights rather than as two depths.
        val vehicleScale = (
            if (c.spec.type == CarType.FIRE_TRUCK) SceneSpace.FIRE_TRUCK_BASE_SCALE else SceneSpace.CAR_BASE_SCALE
            ) * SceneSpace.perspectiveScaleAt(c.spec.laneYFraction) * SceneSpace.sceneScale(screenHeight)

        canvas.save()
        canvas.translate(x, y)
        canvas.scale(dir * vehicleScale, vehicleScale)
        // Aesthetic-pass batch 5 fix: the redrawn car's own coordinates put the wheel-bottom at
        // local y=37 (wheel center 28 + radius 9), not y=0 like the old body did -- every other
        // part of this file (drawRoad's own margin, drawGroundShadow) assumes y=0 is an object's
        // ground contact point, so the car was drawing well below where the road/shadow expected
        // it, which is what let it visually spill outside the road. Shifting the whole car up by
        // that same 37 units here re-aligns it without having to renumber every coordinate below.
        canvas.translate(0f, -37f)

        drawGroundShadow(canvas, 40f, 4f)

        // The fire truck has its own body. Every other type shares the low-sedan silhouette and
        // differs only by colour and an accessory sprite; the fire truck did too, which is why it
        // read as a red car with a ladder floating above it rather than as a fire engine.
        if (c.spec.type == CarType.FIRE_TRUCK) {
            drawFireTruck(canvas)
        } else {
            val bodyColor = when (c.spec.type) {
                CarType.POLICE -> 0xFFF0F0F2.toInt()
                CarType.TAXI -> 0xFFFFC61A.toInt()
                else -> customization.colorFor(c.spec, dayBlend)
            }
            // A single continuous low-sedan silhouette -- chassis capsule and domed cabin merged
            // into one shape. body: local bbox (-50,-12)-(50,28); window: (-31,-10)-(19,8).
            drawTintedSprite(canvas, R.drawable.car_body, -48f, -11f, bodyColor)
            // The glass belongs inside the greenhouse: the cabin's own roof runs from x=-3 to 20
            // at y=-11, with the A-pillar rising from (-22,2) and the C-pillar falling to (36,3).
            // At (-31,-10) the 50x18 window overhung the bonnet by nine units and stood above the
            // roof line on the left. v76.2's -19 centred its content on the greenhouse measured at
            // the glass's own mid-height, which is arithmetically centred and still reads wrong:
            // the greenhouse is not symmetric, so a centred glass runs its vertical rear edge into
            // the roof's rear curve and leaves no C-pillar while the raked front keeps a wide
            // band. Four units forward gives the glass a pillar at each end.
            drawSprite(canvas, R.drawable.car_window, -20f, -6f)

            when (c.spec.type) {
                CarType.POLICE -> {
                    // The livery stripe was blitted at (-70,27): 20 units clear of the body's own
                    // left edge and below its floor, so it drew as a loose bar lying on the road
                    // under the car rather than as a stripe along its side -- and left the white
                    // car itself completely unmarked. It runs along the doors now.
                    drawSprite(canvas, R.drawable.police_stripe, -34f, 13f)
                    drawSprite(canvas, R.drawable.police_lightbar, -11f, -17f)
                }
                // Same defect as the police stripe, one unit less obvious: the chequer straddled
                // the body's floor and the wheels instead of banding the doors.
                CarType.TAXI -> drawSprite(canvas, R.drawable.taxi_checker, -34f, 13f)
                else -> {}
            }
        }

        // A driver's head visible through the glass, man or woman only (only adults drive),
        // picked stably per car instance so it does not change identity frame to frame.
        //
        // **Placed from the sprite's declared anchor, not from its canvas.** The old call site
        // read `-27f, -27f` under `scale(0.24)` at `translate(-6f, 8f)`, which centres a 60x60
        // sprite on that point -- and the V2 head is 171x162 with a `CONTENT_BOTTOM_CENTRE`
        // anchor, so centring its canvas put the bust's shoulders a third of the way down the
        // door, below the window entirely. The origin below is `placement - anchor`: the
        // anchor is subtracted so the bust's own content bottom-centre lands on the point named
        // by [CarType.driverHeadX]/[driverHeadY], which is the bottom-centre of that vehicle's
        // glass. Nothing about the artwork changed to make this work.
        // Seeded from the loop offset rather than the speed: speed is now a property of the lane,
        // so every car in a lane shares it and a speed-derived seed would give them all the same
        // driver. The offset is unique per candidate.
        val driverSeed = kotlin.math.abs((c.spec.laneYFraction * 7919f + c.spec.startDelaySeconds * 131f).toInt())
        val driverKindIdx = driverSeed % 2 // man or woman only (only adults drive)
        val seasonIdx = if (customization.winterColorsEnabled) 1 else 0
        val driverRes = personCarHeadDrawables[driverKindIdx][seasonIdx]
        val isFireTruck = c.spec.type == CarType.FIRE_TRUCK
        val headScale = if (isFireTruck) FIRE_TRUCK_HEAD_SCALE else CAR_HEAD_SCALE
        canvas.save()
        if (isFireTruck) {
            canvas.translate(FIRE_TRUCK_HEAD_X_UNITS, FIRE_TRUCK_HEAD_Y_UNITS)
        } else {
            canvas.translate(CAR_HEAD_X_UNITS, CAR_HEAD_Y_UNITS)
        }
        canvas.scale(headScale, headScale)
        drawSprite(canvas, driverRes, -CAR_HEAD_ANCHOR_X_UNITS, -CAR_HEAD_ANCHOR_Y_UNITS)
        canvas.restore()

        // A passenger in the rear pane, sometimes. Every car used to carry exactly one person.
        //
        // **The driver is always an adult and the seat assignment is not symmetric**: the driver
        // comes from [personCarHeadDrawables], which holds the man and the woman and nobody else,
        // so a child cannot be selected to drive by construction rather than by a check that
        // could be reordered away. The passenger is free to be any of the four, and comes from
        // the window-occupant heads because there is no child driving head to reuse -- the same
        // drawing a child gets when they are looking out of a house window.
        //
        // The two never overlap: the driver sits in the front pane and the passenger in the rear
        // one, either side of the glass's own pillar.
        // **Only civilian cars carry one.** A police car and a fire engine are crewed, not
        // travelled in, and a child in the back of either reads as something being wrong. The
        // rule is written as a property of the vehicle type rather than as a list of exclusions,
        // so a service vehicle added later is excluded by default instead of by remembering.
        if (c.spec.type.carriesPassengers) {
            val passenger = driverSeed / 7 % 4
            if (passenger != 0) {
                val passengerKindIdx = when (passenger) {
                    1 -> driverSeed / 3 % 2      // the other adult
                    2 -> 2                        // boy
                    else -> 3                     // girl
                }
                canvas.save()
                canvas.translate(CAR_PASSENGER_X_UNITS, CAR_PASSENGER_Y_UNITS)
                canvas.scale(CAR_PASSENGER_SCALE, CAR_PASSENGER_SCALE)
                drawSprite(
                    canvas,
                    personWindowHeadDrawables[passengerKindIdx][seasonIdx],
                    -WINDOW_HEAD_ANCHOR_X_UNITS,
                    -WINDOW_HEAD_ANCHOR_Y_UNITS,
                )
                canvas.restore()
            }
        }

        // Wheels: dark tire with a lighter gray hub ring, matching the reference's plain 2-tone
        // wheel treatment (no separate small hubcap disc). Shared by every vehicle type.
        fillPaint.color = 0xFF2B2B2B.toInt()
        canvas.drawCircle(-38f, 28f, 9f, fillPaint)
        canvas.drawCircle(38f, 28f, 9f, fillPaint)
        strokePaint.strokeWidth = 3f
        strokePaint.color = 0xFF8A8A8A.toInt()
        canvas.drawCircle(-38f, 28f, 5.5f, strokePaint)
        canvas.drawCircle(38f, 28f, 5.5f, strokePaint)
        strokePaint.strokeWidth = 2.5f

        canvas.restore()
    }
}
