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
    val shiftXWrapped: Float, // parallax shift, shared verbatim with the hills (see PaperRenderer.drawHillLayers)
    val tileWidth: Float,     // the objects' tiling period: twice the screen width, same as the hills'
    /**
     * How many whole tiles the wrap has removed from the raw parallax shift:
     * `round((shiftUnwrapped - shiftXWrapped) / tileWidth)`.
     *
     * rc2, for the falling leaves. A tile copy's loop index is relative to the *wrapped* shift,
     * so the moment the wrap crosses a period boundary every visible copy's index steps by one --
     * and anything keyed off that index (a tree copy's own leaves) would be re-dealt mid-scroll.
     * `tileIndex - scrollTileBias` is constant for a physical copy for as long as it exists, so
     * it is the copy half of [SceneObjectRenderer.leafSourceId].
     */
    val scrollTileBias: Int = 0,
)

private class StaticRuntime(val spec: StaticSceneObject) {
    val idleSeed = (spec.tileFractionX * 97f) % 6.28f
}

private class CarRuntime(val spec: CarObject) {
    var progress = -spec.startDelaySeconds // negative = still waiting to start

    /**
     * Which of the three bodies this car wears, resolved **once** here.
     *
     * Holding it on the runtime rather than computing it in `drawCar` is the guarantee, not an
     * optimisation: there is then no per-frame expression that could accidentally depend on
     * scroll, on the frame counter, or on how many cars happen to be visible. Rebuilding the
     * runtime list (which only a density change does) recomputes it from the same immutable spec
     * and therefore returns the same body.
     */
    val shell: CarShell = CarShell.forCar(spec)
}

class SceneObjectRenderer(
    private val layout: SceneObjectLayout,
    customization: SceneCustomization = SceneCustomization.DEFAULT,
    private val context: Context,
    /**
     * The theme this scene belongs to, used only to seed the people system.
     *
     * Hashed exactly the way every other effect's seed is (`theme.id.hashCode()`, whose value the
     * Java language specifies exactly), so a theme produces the same street on every device and
     * every run. Defaulted so that the many tests constructing a renderer directly keep compiling;
     * the two production call sites in [PaperRenderer] both pass the real id.
     */
    private val themeId: String = "",
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
            // made by depth rather than by a hash of the horizontal position — and since rc3 the
            // restaurant/bar split is by depth too (see SceneSpace.SHOP_VARIANT_DEPTH_SPLIT): the
            // old position hash made a shop's identity a function of where its jitter landed it,
            // which is how rc2 delivered two identical trattorias in one screen. Depth never
            // changes after generation, so the identity survives the visibility pass's x moves.
            SceneObjectType.SKYSCRAPER ->
                if (spec.depthFraction < SceneSpace.BUILDING_TOWER_MAX_DEPTH) {
                    SceneSpace.SceneVariant.TOWER
                } else if (spec.depthFraction < SceneSpace.SHOP_VARIANT_DEPTH_SPLIT) {
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
         * Where a walking person's sprite origin goes, so its content bottom-centre lands on the
         * pavement line it was placed at.
         *
         * All ninety-six walk sprites are 123x255 px -- 41x85 local units -- and every one of them
         * has its content reaching the canvas's bottom edge, so one pair of numbers covers the set
         * rather than a per-sprite table.
         *
         * (REN-07: this said "twenty-four sprites, 129x252". The count predates the skin axis, which
         * multiplied the set by four, and the size predates a crop. `SpriteMeasurementClaimTest`
         * reads every size quoted in this file back off the artwork, so a third stale measurement
         * cannot be written here without failing.)
         */
        // v4.1 removed `PEDESTRIAN_COUNT` and `PEDESTRIAN_THRESHOLD_SALT` from here. The pool size
        // is now [PedestrianPopulation.GROUP_COUNT] -- the same four slots, but each one yields a
        // group rather than a single person -- and the threshold offset is
        // [PedestrianPopulation.THRESHOLD_OFFSET], which documents why the old salt was wrong.
        // The stale doc comment on the old count claimed people had no density setting; they have
        // had one (`config.density` plus `peopleNightDensity`) since well before this release.

        // How many windows each building kind offers an occupant, which v4.2's occupancy needs
        // because it deals a count across a building's panes instead of flipping one coin at each.
        // These are counts of the `drawWindowOccupant` call sites below, not of drawn windows: the
        // restaurant draws one wide pane, the bar three, the tower sixteen painted into its wall.
        const val SMALL_HOUSE_WINDOWS = 2
        const val LARGE_HOUSE_WINDOWS = 4
        const val BAR_WINDOWS = 3
        const val RESTAURANT_WINDOWS = 2

        /**
         * The restaurant's first-floor openings, and the canopy and sign over its shop front.
         *
         * `house_shared_window` is 22 x 21 units, so the pair sits clear of the 34-unit sign
         * between them; the wall's own upper storey runs from -96 to -60, which is what the y is
         * measured against. The awning's canvas is 68 x 10 units and the frontage it caps runs
         * from the glass at -35 to the far edge of the door at 26, so -40 starts it a whisker
         * outside the glass and it ends two units past the door.
         */
        const val RESTAURANT_UPPER_WINDOW_LEFT_X = -42f
        const val RESTAURANT_UPPER_WINDOW_RIGHT_X = 20f
        const val RESTAURANT_UPPER_WINDOW_Y = -82f

        /**
         * The trattoria frontage, stacked top to bottom: fascia board (with the emblem badge
         * standing proud of it), canopy, then the glass and the door the canopy shades.
         *
         * The fascia canvas is 92 x 13 units with the board in its bottom 8, so blitting it at
         * -66 puts the board at -61..-53 and the badge tip at -66, clear of the upper windows
         * that end at -61. The awning canvas is 92 x 9 with the scallop lobes at its bottom edge,
         * so -53 hangs the lobes to -44, one unit over the glass at -45 -- a canopy over a
         * window, drawn after the glass for the reason recorded in v4.18. The planters flank the
         * door on the ground line; the right one slips two units under the door's own frame and
         * the door is drawn after it.
         */
        const val RESTAURANT_AWNING_X = -46f
        const val RESTAURANT_AWNING_Y = -53f
        const val RESTAURANT_SIGN_X = -46f
        const val RESTAURANT_SIGN_Y = -66f
        const val RESTAURANT_PLANTER_LEFT_X = -42f
        const val RESTAURANT_PLANTER_RIGHT_X = 24f
        const val RESTAURANT_PLANTER_Y = -6f

        /**
         * The coronation that makes the two shops read as shops before their signs are read.
         *
         * A house in this library is a rectangle with a pitched roof, a tower is a rectangle with a
         * setback and a mast, and a shop was a rectangle: at silhouette level "commercial" was
         * indistinguishable from "unfinished". These are blitted above the wall the way the tower's
         * mast is, so neither building's declared height moves -- `SceneVariant` still measures the
         * wall it draws, and the cap hangs over the top of it the way the sign hangs off the front.
         *
         * The two are deliberately different shapes: one raised block over the restaurant, a
         * stepped false front over the bar. Two buildings that read as commercial, and as two
         * different businesses, from the outline alone.
         */
        const val RESTAURANT_CORNICE_X = -55f
        const val RESTAURANT_CORNICE_Y = -108f
        const val BAR_CORNICE_X = -50f
        const val BAR_CORNICE_Y = -108f

        /**
         * The bar's street-level glazing and the height its sign hangs at.
         *
         * The panes are the restaurant's frontage drawable -- one shop-window drawing shared by
         * the two shops, the same argument that already has both of them sharing a house's window
         * upstairs. 30 units wide each, either side of the 20-unit door at -10..10, inside a wall
         * that runs -45..45.
         */
        /**
         * The pub frontage: a painted field the renderer draws as two rectangles, with the
         * fascia, the panes, the door and the corner lantern packed across its 86 units.
         *
         * The field is primitive paint rather than a sprite for two reasons: a flat colour field
         * is exactly what a drawRect is for, and being renderer paint lets it darken with the
         * night the way the tinted walls around it do -- a fixed-art texture would glow pale at
         * midnight. The row packs exactly: lantern -43..-37, pane -37..-7, door -7..13, pane
         * 13..43, against the field's own -43..43.
         */
        const val BAR_FRONT_FIELD_LEFT_X = -43f
        const val BAR_FRONT_FIELD_RIGHT_X = 43f
        const val BAR_FRONT_FIELD_TOP_Y = -53f
        const val BAR_FRONT_EDGE_HEIGHT = 1.6f
        val BAR_FRONT_DAY = 0xFF3E5F4E.toInt()
        val BAR_FRONT_NIGHT = 0xFF243A2D.toInt()
        val BAR_FRONT_EDGE_DAY = 0xFF33503F.toInt()
        val BAR_FRONT_EDGE_NIGHT = 0xFF1D2F25.toInt()
        const val BAR_FRONT_PANE_LEFT_X = -37f
        const val BAR_FRONT_PANE_RIGHT_X = 13f
        const val BAR_FRONT_PANE_Y = -45f
        const val BAR_DOOR_X = -7f
        const val BAR_SIGN_X = -41f
        const val BAR_SIGN_Y = -61f
        const val BAR_LANTERN_X = -43f
        const val BAR_LANTERN_Y = -51f

        /** Where the lantern's glass is, for the glow the renderer stands behind it at night. */
        const val BAR_LANTERN_GLOW_X = -40f
        const val BAR_LANTERN_GLOW_Y = -46f
        const val BAR_LANTERN_GLOW_RADIUS = 5.5f
        /**
         * Cool glass by day, warm light at night: the two ends every window in the scene
         * crossfades between, on the scene's own `nightGlow`.
         *
         * Both values were already in the file -- the day one as the restaurant's inline literal,
         * the night one both there and as the colour `skyscraper_wall_lit` and `house_window_lit`
         * were drawn in. Naming them is what lets the tower join the convention instead of
         * inventing a second pair beside it. See [windowGlassColor].
         */
        const val WINDOW_GLASS_DAY = 0xFFB9CBD9.toInt()
        const val WINDOW_GLASS_NIGHT = 0xFFFFE79A.toInt()

        const val SKYSCRAPER_WINDOWS = 16

        /**
         * Where a bust stands behind the restaurant's shopfront.
         *
         * `restaurant_window` is blitted at (-35, -45) and is 30x22 local units, and its artwork
         * is **two glass panes** either side of a mullion -- glass at sprite pixels 8..39 and
         * 50..81, which is local x -32.3..-22.0 and -18.7..-8.0. Their centres are the two numbers
         * below, so an occupant stands behind a pane rather than behind the frame between them.
         *
         * The occupant *box* stays [OCCUPANT_BOX_UNITS] wide, the same as a house's and the bar's,
         * because that box drives the bust's size: scaling it to a single 10.7-unit pane instead
         * would draw a head half the height of the glass it is behind. The two busts therefore
         * touch at the mullion, which is what two people at a restaurant window do.
         */
        const val RESTAURANT_PANE_A_CENTRE_X = -27.2f
        const val RESTAURANT_PANE_B_CENTRE_X = -13.3f
        const val RESTAURANT_WINDOW_Y = -45f

        /**
         * The occupant box every populatable window except the tower's uses, in local units.
         *
         * A house window is 22 units and the bar's are the same drawable, so this is not a new
         * number -- it is the one those call sites already pass, named here because the restaurant
         * now has to state it away from its own pane width.
         */
        const val OCCUPANT_BOX_UNITS = 22f

        /** Half a walk sprite's own width, in local units, for the wrap-tile cull. */
        const val PERSON_HALF_WIDTH_UNITS = 21.5f

        const val PERSON_ANCHOR_X_UNITS = -20.5f
        const val PERSON_ANCHOR_Y_UNITS = -85f

        // ---- The people behind a windscreen (v4.6) ---------------------------------------
        //
        // ### The defect this block is the fix for
        //
        // A pedestrian's head is **31% of their own height** -- 25.00 of the 80.67 local units a
        // walk sprite's content occupies, which at [SceneSpace.PERSON_METRES_TALL] is 0.547 m.
        // That is a paper-cutout proportion and it is the one the whole scene is drawn in.
        //
        // The busts behind glass were not drawn in it. They were sized to fit *inside* the window
        // with their shoulders, which put a driver's head at 0.320 m -- 59% of the head of the
        // pedestrian walking past, on a plane that is **nearer the viewer than the pavement**. In
        // the reported picture the people in the cars read as children, and no amount of moving
        // the pedestrians could fix it because the pedestrians were right.
        //
        // ### Why it could not be fixed by scaling the bust alone
        //
        // A car's glass was 16 local units, which at 1.45 m over 48 units is **0.483 m of world**
        // -- smaller than the 0.547 m head it had to contain. Filling the old window completely
        // reached 0.358 m, 65%, and there was nothing left to give.
        //
        // ### What v4.6 does
        //
        // The glass is drawn 19 units tall instead of the 16 the artwork is authored at, growing
        // *downward into the door* to [CAR_SILL_Y_UNITS], which is exactly where the police
        // stripe and the taxi chequer begin -- so no accessory is covered and none covers it. The
        // roof line is untouched. Then one rule replaces three tuned numbers:
        //
        //   **a bust's content is exactly as tall as the glass it sits behind, standing on the
        //   sill.**
        //
        // That is [CAR_HEAD_SCALE], [CAR_PASSENGER_SCALE] and [FIRE_TRUCK_HEAD_SCALE] below: each
        // is its glass height over its own sprite's content height, so none of them is a number
        // anybody chose. A driver's head becomes 0.425 m, 78% of a pedestrian's -- a head seen
        // through glass reading slightly smaller than the same head in the open, which is what it
        // should do. `VehiclePedestrianScaleTest` and `VehicleScalePixelTest` pin the ratio.
        //
        // **[SceneSpace.CAR_METRES_TALL] is deliberately unchanged.** The vehicle's own height was
        // measured against the projection and is right; enlarging the car to make its occupants
        // fit would have been fixing the wrong object, and 1.45 m over the lane spacing has no
        // room to grow (see `PIXELS_PER_METRE_AT_REFERENCE`).

        /** The blit origin of `car_window`, and therefore the top edge of the glass. */
        /** Half of `firetruck_body`'s 98-unit canvas: the widest vehicle the road carries. */
        const val FIRE_TRUCK_HALF_WIDTH_UNITS = 49.5f

        /**
         * Where a vehicle's wheels touch the road, in the local space [drawCar] establishes.
         *
         * Every vehicle is drawn from a frame whose origin sits 37 units above the tarmac, because
         * the redrawn artwork puts the wheel bottom at y=37 (centre 28 + radius 9) rather than at
         * y=0 the way every static object does. That shift is what lets the sprites keep their
         * authored coordinates; the cost is that **y=0 is the beltline here, not the ground**, and
         * anything that belongs on the road has to say so.
         *
         * It was not said once: `drawGroundShadow` draws its oval centred on the origin, so the
         * shadow of every car, taxi, police car and fire engine was painted 37 units up, level
         * with the bonnet. The oval is 80 units wide against a 97-unit body, so it should have
         * been hidden -- but at that height the body is not there yet (the bonnet's top edge is at
         * y=14 and the boot's at y=17), and the two ends of the oval came out over the bonnet and
         * over the boot as a pair of brown smears. On every vehicle, in every lane, in every
         * theme, since the artwork was redrawn.
         */
        const val VEHICLE_GROUND_Y_UNITS = 37f

        /**
         * **Every per-body number moved to [CarShell] in v4.19.** Blit origins, roof runs, wheel
         * positions, glass spans, lamp seats and shadow lengths are properties of *which* car is
         * being drawn now that there are three of them, and a constant here could only ever have
         * described one. What stays in this file is what the three genuinely share: the cabin's
         * vertical layout, the seats, the wheel treatment and the liveries.
         */

        /** The fire engine's ground shadow; each car body carries its own, see [CarShell]. */
        const val FIRE_TRUCK_SHADOW_HALF_LENGTH_UNITS = 44f

        const val FIRE_TRUCK_WHEEL_X_UNITS = 34f

        /**
         * The inner wheel of the rear pair. A twin rear axle is the one silhouette cue that says
         * heavy vehicle and nothing else at scene scale, and it was chosen on the device against
         * two chassis-arch concepts that broke the frame bar a truck visibly rides on.
         *
         * rc2 moved it from 28.5 to 13.5. At 28.5 the two rear centres were 8.5 units apart on
         * 20-unit wheels: an overlap of 40% of the diameter, so the inner wheel read as a
         * crescent of tyre with its hub eaten -- two wheels drawn, one visible. A real tandem
         * stands at 1.15-1.3 diameters with daylight between the tyres; 13.5 puts the centres at
         * 23.5 units, 1.175 diameters, with a 3.5-unit gap. The wheels are no longer drawn
         * overlapping, and `TwinAxleSpacingTest` measures the spacing off the rendered PNG --
         * the presence-only test this replaces is exactly how the overlap shipped.
         */
        const val FIRE_TRUCK_INNER_WHEEL_X_UNITS = 9.325f

        /**
         * The tyre, and the hub ring inside it.
         *
         * The appliance's wheel is bigger than the saloon's, which is the other half of what stops
         * it reading as a large car; the ratio between hub and tyre is the same for both, because
         * that is the treatment and not the size. Whatever the radius, the centre sits exactly one
         * radius above [VEHICLE_GROUND_Y_UNITS], so a wheel of any size touches the same road.
         */
        const val CAR_WHEEL_RADIUS_UNITS = 11f
        const val FIRE_TRUCK_WHEEL_RADIUS_UNITS = 10.5f
        const val WHEEL_HUB_RATIO = 5.5f / 9f

        /**
         * The air between a tyre and the arch cut over it, and the one number that made v4.19's
         * wheels stop reading as castors bolted under a slab.
         *
         * Every shell punches its arches as a circle **concentric with the wheel** at
         * [CAR_WHEEL_RADIUS_UNITS] + this, so the gap is the same all the way round instead of
         * closing at the top the way a chord-and-arc arch does. The fire engine divides it by its
         * own metre-per-unit so the *rendered* gap matches -- see `firetruck_body.svg`.
         */
        const val WHEEL_ARCH_AIR_UNITS = 1f

        /**
         * The livery band on the doors, for the police car and the taxi.
         *
         * v4.19 narrowed it from 44 units to 40. A livery is worn by the compact (taxi) and the
         * saloon (police), and the compact's wheels are closer together: measured on the shipped
         * artwork at the band's own row, the run of shell between its two arch cuts is 42 units,
         * so 44 hung a unit over each hole with nothing behind it but road. 40 clears both bodies
         * -- the saloon's run is 54 -- with a unit of margin at each end, and
         * `VehicleAndShopFrontTest` measures it on the pixels rather than trusting this note.
         */
        const val CAR_LIVERY_WIDTH_UNITS = 40f
        const val CAR_LIVERY_X_UNITS = -CAR_LIVERY_WIDTH_UNITS / 2f

        /**
         * `taxi_sign` is 42x18 px: 14 x 6 local units, centred on the roof of the body a taxi
         * actually is -- [CarShell.COMPACT] -- and standing on it rather than through it.
         *
         * The two roof accessories are the reason the liveried types do not rotate their body
         * ([CarShell.forCar]): a bar centred on one roof is off-centre on another.
         */
        const val TAXI_SIGN_WIDTH_UNITS = 14f
        val TAXI_SIGN_X_UNITS =
            (CarShell.COMPACT.roofFrontXUnits + CarShell.COMPACT.roofRearXUnits - TAXI_SIGN_WIDTH_UNITS) / 2f
        const val TAXI_SIGN_HEIGHT_UNITS = 6f
        val TAXI_SIGN_Y_UNITS = -CarShell.COMPACT.unitsTall + VEHICLE_GROUND_Y_UNITS - TAXI_SIGN_HEIGHT_UNITS

        /**
         * The lit parts of a vehicle, in the local units their unlit artwork already occupies.
         *
         * Each is the inside of a shape that is drawn whatever the hour: the two halves of the
         * police bar, the taxi sign's box, the appliance's headlight. Painting inside them at night
         * costs one rectangle each and nothing at all by day.
         */
        val TAXI_SIGN_BOX_LEFT_X = TAXI_SIGN_X_UNITS + 1.4f
        val TAXI_SIGN_BOX_RIGHT_X = TAXI_SIGN_X_UNITS + 12.6f
        val TAXI_SIGN_BOX_TOP_Y = TAXI_SIGN_Y_UNITS + 0.4f
        val TAXI_SIGN_BOX_BOTTOM_Y = TAXI_SIGN_Y_UNITS + 4.4f

        /**
         * The fire engine's lamp seats. It takes the *same two lenses* the cars do -- one amber
         * sprite and one red sprite for the whole fleet -- which is both the memory lever that
         * paid for three bodies and the reason a fire engine now reads as the same set.
         *
         * v4.18's appliance had a headlight painted by a rectangle in code and **no rear lamp at
         * all**, so its direction was readable only from the cab; now it says which way it is
         * going the way every car does.
         */
        const val FIRE_TRUCK_LAMP_FRONT_X_UNITS = -47f
        const val FIRE_TRUCK_LAMP_FRONT_Y_UNITS = -0.6f
        const val FIRE_TRUCK_LAMP_REAR_X_UNITS = 43.8f
        const val FIRE_TRUCK_LAMP_REAR_Y_UNITS = 3.8f

        /** What a lamp is when it is lit. Warm for a headlight, saturated for a beacon. */
        const val HEADLIGHT_LIT = 0xFFFFF0BE.toInt()
        const val TAXI_SIGN_LIT = 0xFFFFE9A8.toInt()
        val BEACON_RED_LIT = 0xFFFF6A57.toInt()
        val BEACON_BLUE_LIT = 0xFF6E9BFF.toInt()

        /** `police_lightbar` is 60x18 px: 20 x 6 local units. */
        const val POLICE_LIGHTBAR_WIDTH_UNITS = 20f

        /** Centred on the roof of the body a police car actually is, [CarShell.SALOON]. */
        val POLICE_LIGHTBAR_X_UNITS =
            (CarShell.SALOON.roofFrontXUnits + CarShell.SALOON.roofRearXUnits - POLICE_LIGHTBAR_WIDTH_UNITS) / 2f

        /** The light bar's own height, so its base sits on the roof line rather than through it. */
        const val POLICE_LIGHTBAR_HEIGHT_UNITS = 6f
        val POLICE_LIGHTBAR_Y_UNITS =
            -CarShell.SALOON.unitsTall + VEHICLE_GROUND_Y_UNITS - POLICE_LIGHTBAR_HEIGHT_UNITS

        /**
         * The lit beacons, derived from the bar's own blit origin and from where
         * `police_lightbar.svg` paints its two domes (red 0..9.6, blue 10.4..20, both 0..4.5 in
         * the bar's 20x6 local units) -- inset ~0.8 so the dark surround survives at night.
         * They used to be absolute coordinates, tuned when the bar sat at a roof the rc5 pass
         * then moved: the bar followed the roof by arithmetic, the lamps did not, and every
         * night beacon since rc5 has glowed 7 units to the right of its own dome.
         */
        val POLICE_LAMP_TOP_Y_UNITS = POLICE_LIGHTBAR_Y_UNITS + 0.7f
        val POLICE_LAMP_BOTTOM_Y_UNITS = POLICE_LIGHTBAR_Y_UNITS + 3.8f
        val POLICE_LAMP_RED_LEFT_X = POLICE_LIGHTBAR_X_UNITS + 0.8f
        val POLICE_LAMP_RED_RIGHT_X = POLICE_LIGHTBAR_X_UNITS + 8.8f
        val POLICE_LAMP_BLUE_LEFT_X = POLICE_LIGHTBAR_X_UNITS + 11.2f
        val POLICE_LAMP_BLUE_RIGHT_X = POLICE_LIGHTBAR_X_UNITS + 19.2f

        /**
         * The flat margin REN-06 replaced, kept as a floor so the current picture does not move.
         *
         * At the reference viewport the derived margin is smaller than this, so this is what is
         * used and every vehicle enters and leaves exactly where it always has.
         */
        const val LEGACY_EDGE_MARGIN_PX = 120f

        /**
         * The divisor `drawWindowOccupant` scales a bust by, which is **not** the sprite's width.
         *
         * REN-07. It was written as "the head sprite canvas is 60 units wide" and the canvas is 53:
         * it was 180 px when the line was written and lost a column in the SCL-01 pass. The bust is
         * therefore drawn at 53/60 of the nominal 85% of the pane, about 75%, and that is what has
         * shipped since v4.2 and what the artwork was judged against. Kept, and named for what it
         * actually is.
         */
        const val WINDOW_OCCUPANT_DIVISOR_UNITS = 60f

        /**
         * The window busts' CONTENT_BOTTOM_CENTRE anchor, in their own 53x57-unit canvas: the
         * midpoint of the eight heads' declared x, and the canvas bottom. Buildings only since
         * rc2; the vehicles' profile family carries its own anchor below.
         */
        const val WINDOW_HEAD_ANCHOR_X_UNITS = 26.8f
        const val WINDOW_HEAD_ANCHOR_Y_UNITS = 57f

        /**
         * **The cabin's vertical layout, identical on all three bodies.**
         *
         * The horizontal plan is what distinguishes a compact from an estate; the vertical does
         * not have to, and making it shared buys something specific: one seated-occupant scale,
         * one pair of seat positions, and therefore an occupant who is exactly the same size
         * whichever car they are riding in. Per-body glazing heights would have given three
         * occupant scales and three sets of criteria to re-derive.
         *
         * 25 units, top -16 to sill 9, is what the height table asks for rather than what the
         * roof allowed. A table-sized bust is 44 canvas units at [CAR_OCCUPANT_SCALE] = 20.93
         * units of drawn content, so a 25-unit pane leaves **16.3% of air** above the crown --
         * inside the 10-25% band v4.18 established, where its own 23-unit pane would have left
         * 9% and needed the occupant shrunk to fit. The bodies were drawn around this number,
         * which is the whole difference from v4.18: there the pane was bent to fit the shell.
         */
        const val CAR_GLASS_ORIGIN_Y_UNITS = -16f
        const val CAR_GLASS_SPRITE_HEIGHT_UNITS = 25f
        const val CAR_GLASS_HEIGHT_UNITS = CAR_GLASS_SPRITE_HEIGHT_UNITS

        /** The window sill: the bottom edge of the drawn glass, and where a bust stands. */
        const val CAR_SILL_Y_UNITS = CAR_GLASS_ORIGIN_Y_UNITS + CAR_GLASS_HEIGHT_UNITS

        /**
         * The head part of a window bust, in its own local units: hair crown down to the neck.
         * Still what the house, shop and tower occupants are proportioned by; the vehicles no
         * longer share it (see below).
         */
        const val WINDOW_HEAD_HEAD_UNITS = 110f / SpriteBlitter.SPRITE_PIXELS_PER_UNIT

        /**
         * The share of a building's pane a head takes: what `drawWindowOccupant` has drawn house,
         * shop and tower occupants at since v4.2, read back out of the expression that produces
         * it (`0.85 * 36.667 / 60` of the pane = 51.9%). rc2 note: this is now a rule about
         * *buildings only*. The vehicles used to inherit it and their people came out 22% smaller
         * than a child pedestrian at the same depth; they are sized off the height table now.
         */
        const val OCCUPANT_HEAD_PANE_SHARE =
            0.85f * WINDOW_HEAD_HEAD_UNITS / WINDOW_OCCUPANT_DIVISOR_UNITS

        // ---- The occupants, sized off the height table -------------------------------------
        //
        // rc2 retires the pane-share rule and its multiplier. Every other human in the scene is
        // sized by the height table -- PERSON_METRES_TALL times the projection at their ground
        // line -- and the occupants were the one exception, sized as a share of whatever glass
        // happened to be behind them. Measured on the rc1 frames, that made a driver's head 22%
        // smaller than a *child* pedestrian's once depth was normalised out, and no multiplier on
        // the wrong parameter could close a gap the parameterisation itself created.

        /**
         * A pedestrian's head, measured off the shipped walking artwork: `person_man_summer_walk0`
         * carries its hair crown at row 15 and its jaw at row 86 of a 240-row canvas, 71 px = 23.7
         * of the figure's 80 units. `OccupantTableTest` re-measures the PNG so this cannot drift
         * from the artwork silently.
         */
        const val PERSON_HEAD_SPRITE_UNITS = 23.7f

        /** That head in scene metres: the one size every head in the scene now derives from. */
        const val OCCUPANT_HEAD_METRES =
            SceneSpace.PERSON_METRES_TALL * PERSON_HEAD_SPRITE_UNITS / SceneSpace.PERSON_SPRITE_UNITS_TALL

        /**
         * How much of the standing head a seated occupant is drawn at: 97%, and the reason is the
         * roof. At 100% the tallest winter hat needs a 24.8-unit pane and the 50-unit shell tops
         * out at 23 (beltline 12 against arch tops at 15; glass top -11 against the roof at -13),
         * leaving 7.5% of air against the 10% floor. 0.97 is the largest factor that keeps every
         * seasonal bust inside the 10-25% air band of the pane the silhouette can actually carry,
         * and it sits well inside the +/-10% band the occupant-vs-pedestrian criterion allows.
         */
        const val OCCUPANT_SEATED_FIT = 0.97f

        /**
         * The head's share of the frontal bust artwork: the adult members of
         * `person_*_head_car` carry their head (crown to chin) as 35 of the shared 47x44 canvas's local
         * units, the children as 31.5 -- the same 18/20 a child always is. The scales below
         * divide by the adult head, so an adult's head lands exactly on the table and a child's
         * comes out 10% shorter, which is what a child is.
         *
         * rc4: the artwork is rc1's frontal family -- the pedestrians' own face with a seatbelt
         * on the chest -- with its torso baseline raised from 49 to 43 canvas units, because a
         * bust anchored on the sill must fit its whole content into the 23-unit pane the shell
         * can carry (there is no clip in [SceneCanvas], so "zero occupant pixels outside the
         * glass" has to hold by authored geometry, exactly as it did for the profiles, which
         * carried only 5 units of shoulder for the same reason).
         */
        const val HEAD_CAR_HEAD_UNITS = 35f

        /** The frontal family's CONTENT_BOTTOM_CENTRE anchor, in the family's shared
         * 47x44-unit canvas: x between the eyes (a seat centres the face, not the content box --
         * the woman's side-swept hair would pull a content-centred seat sideways; every member
         * is authored with its eye line centred here, which is what lets one origin serve all
         * eight), y the shared canvas bottom -- the co-registration rule every person family
         * anchors by (SpriteGeometryTest) -- which the torso baseline's ink sits a third of a
         * unit above. */
        const val HEAD_CAR_ANCHOR_X_UNITS = 23f
        const val HEAD_CAR_ANCHOR_Y_UNITS = 44f

        /**
         * One scale per vehicle family, each the same rule: the table head, seat-fitted, in the
         * vehicle's own units, over the artwork's head. Not a share of the pane: the pane was
         * sized to fit the head (see [CAR_GLASS_ORIGIN_Y_UNITS]), not the head to fit the pane.
         */
        val CAR_OCCUPANT_SCALE: Float
            get() = OCCUPANT_SEATED_FIT * OCCUPANT_HEAD_METRES /
                SceneSpace.CAR_UNIT_METRES / HEAD_CAR_HEAD_UNITS
        val FIRE_TRUCK_OCCUPANT_SCALE: Float
            get() = OCCUPANT_SEATED_FIT * OCCUPANT_HEAD_METRES /
                (SceneSpace.FIRE_TRUCK_METRES_TALL / SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL) /
                HEAD_CAR_HEAD_UNITS

        /**
         * The two seats, **shared by all three bodies**: driver at -8.5, passenger at 14.5, a
         * pitch of 23 units about a cabin centre of 3.
         *
         * ### Why the pitch is 23 and not 20
         *
         * v4.19 seats children as well as adults, and the widest head in the set is the winter
         * girl's -- 22 units across the bunches against an adult's 18. At the 20-unit pitch the
         * concept pass used, that pair left **0.33 units** of clear glass between the two heads:
         * two people reading as one mass, which is the defect the gap criterion exists to catch.
         * At 23 it is 3.34 units on the same pair.
         *
         * The pitch could not simply grow, because a wider pair meets the pillars: at 24 units
         * the crown of an outer head crossed the raked A-pillar on the compact and the saloon
         * (25 and 50 stray pixels, measured). **The bodies were redrawn rather than the pitch
         * clipped** -- the three glasshouses were stood up by 3 units at the top corners and the
         * roofs lengthened to match, which is what §4 of the concept brief meant by "the answer
         * is the cabin". A sweep of pitch x cabin width is in the pass report; 23 is the value
         * where the pillar light and the head gap are both comfortably clear with zero occupant
         * pixels outside the glass, on every body, for every family, in both seasons.
         *
         * ### What is measured, on the shipped artwork, over 12 combinations
         *
         * Three bodies x {two adults, adult+boy, adult+girl} x {summer, winter}, on the rendered
         * pixels, in the crown-to-chin band and against the **occupied** pane only:
         *  * occupant pixels outside the glass: **0**, everywhere;
         *  * pillar light **18.2-63.0%** of the head's own width (floor 12%, derived below);
         *  * clear glass between the heads **15.2-38.6%** of head width (same 12% floor);
         *  * glass filled by occupant on the head rows **50.8-66.3%** (floor 50%);
         *  * driver forward of the cabin centre on all three.
         *
         * **The floors are derived from legibility, not inherited.** v4.18's 15%-then-13% pillar
         * light was measured against the *pane*, so it moved every time the glass did -- item 6
         * of `BACKLOG_v4_19.md` asked for it against the head instead, and this is that. The
         * number: a car in the far lane draws at about 1.44 px per local unit on the reference
         * device, and a band of glass narrower than ~3 px reads as an antialiasing seam rather
         * than as daylight, so the floor is 3/1.44 = 2.1 units, which is 11.6% of an adult head
         * and is stated as **12%**. The same argument gives the same floor to the head gap.
         *
         * **Below the chin the busts still meet, and that is wanted**: two people one behind the
         * other occlude at the shoulders, and that contact is the depth cue that separates the
         * seats. The gap is measured crown-to-chin for exactly that reason -- and the neck row,
         * which v4.18 measured fill on and could never satisfy (item 2 of the backlog), is not
         * part of any band here.
         */
        const val CAR_HEAD_X_UNITS = -8.5f
        const val CAR_HEAD_Y_UNITS = CAR_SILL_Y_UNITS
        const val CAR_PASSENGER_X_UNITS = 14.5f
        const val CAR_PASSENGER_Y_UNITS = CAR_SILL_Y_UNITS

        /**
         * The seat back between the two occupants — the element that says *two seats* rather
         * than a bench.
         *
         * **Where it can live, and why nowhere else.** The pane is 54 units and two heads take
         * 36.16 of it; what is left pays for 3% of clear glass between them and 13% of light to
         * each pillar, with about a fifth of a unit to spare. There is no room for a mullion in
         * the head gap — drawing one there would eat the very glass the gap criterion measures.
         * Below the chin line the arithmetic is completely different: the busts narrow to about
         * 13 units at the jaw, so the gap there is five units wide and nothing is measured in it
         * (`theTwoHeadsAreSeparatedByClearGlass` stops at the chin, deliberately, because two
         * people sitting one behind the other are *meant* to meet at the shoulders).
         *
         * So the seat back rises out of that gap: 2.6 units wide, from local y 5.4 — below where
         * the head-gap scan stops (the chin sits at 4.72 with the sill at 9) — down to the sill.
         * It is drawn **after the glass and before either bust**, so both occupants sit in front
         * of it and it is visible only in the daylight between them, which is what a seat back
         * seen through a side window looks like. Nothing occludes a person; the shoulders close
         * over its lower half by themselves.
         */
        const val CAR_SEAT_BACK_X_UNITS = (CAR_HEAD_X_UNITS + CAR_PASSENGER_X_UNITS) / 2f
        const val CAR_SEAT_BACK_HALF_WIDTH_UNITS = 1.3f
        const val CAR_SEAT_BACK_TOP_Y_UNITS = 5.4f

        /** Upholstery seen through glass: dark enough to read against the pane at lane scale. */
        val CAR_SEAT_BACK_COLOUR = 0xFF5A6068.toInt()

        /**
         * The fire engine's cab glass, painted into `firetruck_body`: 19 units from -13 to the
         * sill at 6, following the redrawn windscreen instead of sitting on top of it.
         *
         * v4.19 stood the appliance's windscreen up and lengthened its cab. The v4.18 cab was
         * short enough that a table-sized head could not keep daylight to the A-pillar at any
         * seat position -- the head simply crossed the rake -- which is why the cab grew rather
         * than the driver shrinking. At 19 units a 14.82-unit bust leaves 22% of air, inside the
         * same 10-25% band the cars use.
         */
        const val FIRE_TRUCK_GLASS_HEIGHT_UNITS = 19f
        const val FIRE_TRUCK_SILL_Y_UNITS = 6f
        const val FIRE_TRUCK_HEAD_X_UNITS = -21f
        const val FIRE_TRUCK_HEAD_Y_UNITS = FIRE_TRUCK_SILL_Y_UNITS

        /**
         * The v4.19 appliance, redrawn in the language of the three cars.
         *
         * §3 of the pass brief called it out from the concept captures: it stood next to the new
         * cars with hard corners and small wheels poking out from under a flat floor -- the
         * castor look the cars had just lost -- so two generations of drawing were side by side
         * on the largest, loudest vehicle in the scene. The redraw takes the same vocabulary:
         * arches **concentric** with the tyre at [WHEEL_ARCH_AIR_UNITS] of air, the same corner
         * radii and the same darker lower band, both lamp lenses shared with the cars, and a
         * cab-over nose with an upright windscreen that has room for a table-sized head.
         *
         * The canvas now reaches the ladder, so the body's own origin is the sprite's top-left
         * at (-49.5, -31) and [SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL] is still 68 -- the
         * vehicle did not change size, only drawing. The cab roof stays eight units below the
         * body roof, which is the one line that says truck rather than scaled-up car.
         */
        const val FIRE_TRUCK_BODY_X_UNITS = -49.5f
        const val FIRE_TRUCK_BODY_Y_UNITS = -24.5f
        const val FIRE_TRUCK_CAB_ROOF_Y_UNITS = -16f
        const val FIRE_TRUCK_LADDER_X_UNITS = -2f
        const val FIRE_TRUCK_LADDER_Y_UNITS = -31f

        /** The two beacons on the cab roof, in the redrawn cab's own coordinates. */
        const val FIRE_TRUCK_BEACON_TOP_Y_UNITS = -19.4f
        const val FIRE_TRUCK_BEACON_RED_LEFT_X = -24f
        const val FIRE_TRUCK_BEACON_RED_RIGHT_X = -16f
        const val FIRE_TRUCK_BEACON_BLUE_LEFT_X = -14f
        const val FIRE_TRUCK_BEACON_BLUE_RIGHT_X = -6f

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

        /**
         * Screen pixels of crown half-width per shed leaf. 7 calibrated against v4.17's fixed
         * pool of 26 on the delivered two-crown Autumn frame: two near crowns at ~85 px of
         * half-width shed 12 each, which is the pool's own 13-per-crown on that frame.
         */
        const val LEAF_SOURCE_PX_PER_LEAF = 7f

        /** The presents under a fir, in the tree lights' own palette. */
        val GIFT_COLOURS = intArrayOf(0xFFE8564F.toInt(), 0xFF4F8FBF.toInt(), 0xFF6FCF6F.toInt())

        /** How many flower clumps the ground carries, and where they may stand. */
        const val FLOWER_CLUMP_COUNT = 22
        const val FLOWER_DEPTH_MIN = 0.06f
        const val FLOWER_DEPTH_MAX = 0.92f
        const val FLOWER_METRES_TALL = 0.55f
        const val FLOWER_SPRITE_UNITS_TALL = 12f

        /**
         * The most drifts or heaps the ground carries at 100%, and how big one is.
         *
         * Fewer than [FLOWER_CLUMP_COUNT] because a drift is a chunkier object than a clump of
         * flowers and the same count reads as a covered floor rather than as scattered snow. The
         * slider multiplies this and rounds, so 0% draws nothing at all -- not one pile -- and the
         * scene a user has never touched is exactly the scene v4.16 drew.
         */
        const val PILE_MAX_COUNT = 18
        const val PILE_METRES_TALL = 0.42f
        const val PILE_SPRITE_UNITS_TALL = 7f

        /** Distinct hash salts, so a drift, a heap and a clump of flowers never coincide. */
        const val PILE_SALT_SNOW = 733
        const val PILE_SALT_LEAF = 1471

        /**
         * How many piles a slider position draws. Truncating rather than rounding is what makes
         * **0% draw nothing at all** rather than one lonely drift, and it is why a scene nobody has
         * touched costs exactly what it cost before the feature existed.
         */
        fun pileCount(density: Float): Int = (density.coerceIn(0f, 1f) * PILE_MAX_COUNT).toInt()

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

    /**
     * How far off each edge a vehicle starts and finishes, in canvas pixels.
     *
     * **REN-06: this was a flat `120f`, and 120 px is not a distance the scene owns.** A vehicle's
     * width scales with the viewport, the margin did not, and at a tall enough screen the two cross:
     * the fire engine's scaled half-width passes 120 px at about 3900 px of screen height, so the
     * far end of a vehicle would still be on screen when its copy was declared gone, and it would
     * pop at the edge. No shipping phone is that tall -- the OnePlus 6T is 2340 and the tallest
     * flagships are near 3200 -- so nothing was ever visibly wrong, and the finding is real anyway:
     * a bound expressed in the wrong unit is one resolution away from being wrong.
     *
     * Derived from the widest vehicle at the nearest lane, which is the largest a vehicle is ever
     * drawn, plus a tenth for its shadow and the outline stroke. The old 120 stays as a floor so
     * nothing about the current picture moves: at the reference viewport the derived value is 100
     * px, so 120 is what is used, exactly as before.
     */
    private fun vehicleEdgeMarginPx(screenHeight: Float): Float {
        val widest = FIRE_TRUCK_HALF_WIDTH_UNITS * SceneSpace.FIRE_TRUCK_BASE_SCALE
        val scaled = widest *
            SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION) *
            SceneSpace.sceneScale(screenHeight)
        return maxOf(LEGACY_EDGE_MARGIN_PX, scaled * 1.1f)
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
     * Draws every static object, then the road, then the people, then the cars.
     *
     * The last two are in that order because the pavement is behind the carriageway; see the call
     * to [drawPeople] below.
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

    /**
     * The ground line of the tree each crown belongs to, which is where its leaves land.
     *
     * `PaperRenderer` used to end every fall at one global `screenHeight * 0.88`, below **both**
     * traffic lanes (0.834 and 0.862), so a leaf shed by a tree standing high on the hill drifted
     * down across the whole hillside, over the far lane and over the near one, and settled on the
     * road in front of the traffic. Reported as leaves carrying on past the tree as far as the
     * cars. A leaf lands at the foot of its own tree, and this is that foot -- already known here,
     * simply not recorded until now.
     */
    val leafSourceGroundY = FloatArray(MAX_LEAF_SOURCES)

    /**
     * The stable identity of the tree each recorded crown belongs to: its index in
     * [staticRuntimes], which is fixed for the lifetime of a layout.
     *
     * The identity is what makes a falling leaf *belong* to its tree rather than to a slot in
     * this frame's array. The arrays above are refilled every frame with only the crowns that
     * are currently on screen, so their indices shuffle whenever a home-screen swipe scrolls a
     * tree across a screen edge -- and `PaperRenderer` used to hand candidate `i` to source
     * `i % count`, which reassigned **every** leaf to a different tree each time that happened.
     * Measured on a OnePlus 6T from a 30 fps screen recording: leaves teleported mid-fall in the
     * exact frames the visible set changed, and only in those. The maintainer reported it as the
     * scene "rebuilding" on swipe. With the identity recorded, `PaperRenderer` derives each
     * tree's leaves from this number instead of from the slot, so a tree keeps its own leaves
     * while it is on screen no matter what the rest of the scene does.
     */
    val leafSourceId = IntArray(MAX_LEAF_SOURCES)

    /** How many leaves each recorded crown sheds: its drawn half-width over [LEAF_SOURCE_PX_PER_LEAF]. */
    val leafSourceLeafCount = IntArray(MAX_LEAF_SOURCES)
    var leafSourceCount = 0
        private set

    /**
     * The stable identity of one tile copy of one tree: the runtime index for the tree half, and
     * `tileIndex - scrollTileBias` for the copy half, which is constant for a physical copy for
     * as long as it stays on screen -- including across the parallax wrap, where the raw
     * tileIndex of every copy steps by one and the bias steps with it. Without the copy half,
     * two simultaneously visible copies of one tree would shed five identical leaves; without
     * the bias, every leaf in the scene would be re-dealt each time the scroll crossed a period.
     */
    private fun leafCopyId(runtimeIndex: Int, tileIndex: Int, geom: GroundGeometry): Int =
        runtimeIndex * 1_000_003 + (tileIndex - geom.scrollTileBias)

    private fun recordLeafSource(variant: SceneSpace.SceneVariant, id: Int, x: Float, groundY: Float, scale: Float) {
        if (leafSourceCount >= MAX_LEAF_SOURCES) return
        // The crown as each is actually blitted: the leafy canopy hangs at -38 with its own
        // content centred another 43 above that and 74 units tall; the palm's fan at -90.33 with
        // its content centre 18 below its origin and 37 units tall. Derived from the two call
        // sites and the two sprites rather than guessed, so a change to either moves the leaves
        // with it.
        val centreUnits: Float
        val halfHeightUnits: Float
        val halfWidthUnits: Float
        when (variant) {
            SceneSpace.SceneVariant.TREE -> { centreUnits = -81f; halfHeightUnits = 37f; halfWidthUnits = 41f }
            SceneSpace.SceneVariant.PALM_TREE -> { centreUnits = -72f; halfHeightUnits = 18.5f; halfWidthUnits = 20f }
            else -> return
        }
        leafSourceX[leafSourceCount] = x
        // rc2: a leaf detaches at the crown's *bottom edge*, not its centre. Spawned at the
        // centre, the first 40% of every fall happened inside the canopy, where the leaf was
        // either invisible or read as a dark blot lying on the foliage -- two of those were
        // pointed at on the delivered night frame. The margin is four units plus the leaf
        // oval's own eight-pixel extent (leaves are drawn unscaled): the four covers the canopy
        // canvas's own outline slack past the measured content, the eight the rotated oval, so
        // even the first frame of a fall touches no pixel of the crown's bounding box --
        // FallingLeafContinuityTest drives a whole cycle through the recorder to hold it there.
        leafSourceY[leafSourceCount] = groundY + (centreUnits + halfHeightUnits + 4f) * scale + 8f
        leafSourceHalfWidth[leafSourceCount] = halfWidthUnits * scale
        leafSourceGroundY[leafSourceCount] = groundY
        leafSourceId[leafSourceCount] = id
        // How many leaves this crown sheds: proportional to its drawn size, so a near crown sheds
        // a canopy's worth and a distant one a few, and the total tracks how much tree is
        // actually on screen instead of collapsing when the viewport holds few crowns (the fixed
        // per-tree count did exactly that on the delivered two-crown frame). Constant per tree
        // while it is on screen -- scale is depth, not viewport -- so nothing pops during a swipe.
        leafSourceLeafCount[leafSourceCount] =
            (halfWidthUnits * scale / LEAF_SOURCE_PX_PER_LEAF).toInt().coerceIn(3, 13)
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

    /**
     * Drifts of snow and heaps of leaves on the open ground, drawn with the flowers and for the
     * same reasons.
     *
     * **The scatter is [drawGroundFlowers]'s, salted differently.** Same stratified hash -- a slice
     * of the width each, depth free within the ground band -- so a heap sits on its own ground line
     * at its own perspective, the set is identical every frame, and nothing is allocated per frame.
     * The salt is what stops a drift and a clump of flowers being the same object twice.
     *
     * **The count is the slider, rounded.** `0%` draws nothing: the loop does not run, so a scene
     * that has never been touched costs exactly what it cost before this existed. `100%` is
     * [PILE_MAX_COUNT], which is where the ground still reads as ground with things lying on it.
     *
     * Each is gated on its own season rather than on a switch of its own -- snow on a summer lawn
     * and leaf heaps under a snow-capped tree are both wrong, and the seasons already say which.
     */
    private fun drawGroundPiles(canvas: SceneCanvas, geom: GroundGeometry, screenWidth: Float, screenHeight: Float) {
        if (customization.winterColorsEnabled) {
            scatterPiles(canvas, geom, screenWidth, screenHeight, customization.snowPiles, R.drawable.snow_pile, PILE_SALT_SNOW)
        }
        if (customization.fallColorsEnabled) {
            scatterPiles(canvas, geom, screenWidth, screenHeight, customization.leafPiles, R.drawable.leaf_pile, PILE_SALT_LEAF)
        }
    }

    private fun scatterPiles(
        canvas: SceneCanvas,
        geom: GroundGeometry,
        screenWidth: Float,
        screenHeight: Float,
        density: Float,
        drawable: Int,
        salt: Int,
    ) {
        val count = pileCount(density)
        if (count <= 0) return
        val sceneScale = SceneSpace.sceneScale(screenHeight)
        for (i in 0 until count) {
            val h = ((i + salt) * 2654435761L.toInt()) xor ((i + salt) shl 7)
            val jitterX = ((h ushr 3) and 0xFF) / 255f
            val jitterDepth = ((h ushr 13) and 0xFF) / 255f
            val depth = FLOWER_DEPTH_MIN + (FLOWER_DEPTH_MAX - FLOWER_DEPTH_MIN) * jitterDepth
            val groundY = screenHeight * SceneSpace.groundYFraction(depth)
            val scale = SceneSpace.scaleForHeight(PILE_METRES_TALL, PILE_SPRITE_UNITS_TALL) *
                SceneSpace.depthScale(depth) * sceneScale
            // Sliced across the full width whatever the count is, so lowering the slider thins the
            // scatter out evenly instead of emptying one end of the scene.
            val slice = screenWidth / count
            val baseX = (i + jitterX) * slice
            val tile = if (geom.tileWidth > 0f) geom.tileWidth else screenWidth
            var x = (baseX + geom.shiftXWrapped) % tile
            while (x < screenWidth + slice) {
                if (x > -slice) {
                    canvas.save()
                    canvas.translate(x, groundY)
                    canvas.scale(scale, scale)
                    // The pile canvases are 36x7 units and every lobe stands on their bottom edge,
                    // so this origin puts a drift on the ground line the same way the flowers'
                    // -18,-12 does for their own 36x12.
                    drawSprite(canvas, drawable, -18f, -7f)
                    canvas.restore()
                }
                x += tile
            }
        }
    }

    fun draw(canvas: SceneCanvas, geom: GroundGeometry, dayBlend: Float, elapsedSeconds: SceneTime, screenWidth: Float, screenHeight: Float) {
        leafSourceCount = 0
        drawGroundFlowers(canvas, geom, screenWidth, screenHeight)
        drawGroundPiles(canvas, geom, screenWidth, screenHeight)
        // staticRuntimes is already depth-sorted at construction -- see its declaration. The
        // index is each object's stable identity for the frame-independent state that hangs off
        // it (a tree's falling leaves); an indexed loop rather than withIndex() so the hot path
        // allocates no iterator.
        for (runtimeIndex in staticRuntimes.indices) {
            val r = staticRuntimes[runtimeIndex]
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
                    recordLeafSource(variantFor(r.spec), leafCopyId(runtimeIndex, 0, geom), x, groundY, effectiveScale)
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
                    recordLeafSource(variantFor(r.spec), leafCopyId(runtimeIndex, tileIndex, geom), x = copyX, groundY = groundY, scale = effectiveScale)
                }
            }
        }

        drawRoad(canvas, dayBlend, screenWidth, screenHeight, geom.shiftXWrapped, geom.tileWidth)

        // **People before traffic, because every pedestrian is farther away than every car.**
        //
        // The pavement rows are 0.795 and 0.807 and a group member's own jitter can move one down
        // by at most [PedestrianPopulation.MEMBER_ROW_SPREAD]; the lanes are 0.834 and 0.862, and
        // a persisted layout cannot bring a car anywhere else because `SceneObjectCatalog` snaps
        // every stored `laneYFraction` onto one of those two (see `PersistedThemeGeometryTest`).
        // So the deepest pedestrian the generator can produce still stands behind the nearest edge
        // of the road, and there is no arrangement in which a walking figure is in front of a
        // vehicle. `PeopleTrafficDepthTest` asserts exactly that, so the day one of those
        // constants moves the test fails instead of the picture.
        //
        // Until v4.6 this call came last. Nothing but the two orders differ: the deepest figure
        // sits 0.0100 of screen height below a far-lane car's roof line -- 24 px on a 2400 px
        // screen, 32 px against a police light bar -- and painted its shoes across the roof
        // whenever the two happened to coincide in x. `drawPeople` draws walking pedestrians and
        // nothing else (window occupants belong to their building, drivers and passengers to
        // their car), so moving it changes that one relationship and no other.
        drawPeople(canvas, geom, screenWidth, screenHeight, elapsedSeconds, dayBlend)

        for (c in carRuntimes) {
            if (c.progress < -0.05f || c.progress > 1.05f) continue
            drawCar(canvas, c, screenWidth, screenHeight, dayBlend)
        }
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

    /**
     * The walking sprites again, with a skin-tone axis: `[kind][season][skin][frame]`.
     *
     * Parallel to [personWalkDrawables] rather than a replacement for it, and deliberately so.
     * That table is still what `ThemePreviewScene` draws, and leaving it alone is what makes
     * "the settings preview is unchanged" a fact about the code rather than a claim.
     *
     * The frames are the same four slots with the same third-slot sharing; only the paint
     * differs. `tools/generate_skin_variants.py` produced every entry from the sprite in
     * [personWalkDrawables] by moving one flat colour and nothing else, and verifies that every
     * other colour keeps its exact pixel mask -- so clothes, hair, eyes, outlines, silhouette and
     * animation are identical across the skin axis by construction.
     *
     * A tone is chosen once per person and then indexes this table like any other sprite lookup.
     * There is no per-pixel work anywhere in the draw path.
     */
    private val personWalkSkinDrawables = arrayOf(
        // man
        arrayOf(
            arrayOf(
                intArrayOf(R.drawable.person_man_summer_walk0_skin0, R.drawable.person_man_summer_walk1_skin0, R.drawable.person_man_summer_walk2_skin0, R.drawable.person_man_summer_walk1_skin0),
                intArrayOf(R.drawable.person_man_summer_walk0_skin1, R.drawable.person_man_summer_walk1_skin1, R.drawable.person_man_summer_walk2_skin1, R.drawable.person_man_summer_walk1_skin1),
                intArrayOf(R.drawable.person_man_summer_walk0_skin2, R.drawable.person_man_summer_walk1_skin2, R.drawable.person_man_summer_walk2_skin2, R.drawable.person_man_summer_walk1_skin2),
            ),
            arrayOf(
                intArrayOf(R.drawable.person_man_winter_walk0_skin0, R.drawable.person_man_winter_walk1_skin0, R.drawable.person_man_winter_walk2_skin0, R.drawable.person_man_winter_walk1_skin0),
                intArrayOf(R.drawable.person_man_winter_walk0_skin1, R.drawable.person_man_winter_walk1_skin1, R.drawable.person_man_winter_walk2_skin1, R.drawable.person_man_winter_walk1_skin1),
                intArrayOf(R.drawable.person_man_winter_walk0_skin2, R.drawable.person_man_winter_walk1_skin2, R.drawable.person_man_winter_walk2_skin2, R.drawable.person_man_winter_walk1_skin2),
            ),
        ),
        // woman
        arrayOf(
            arrayOf(
                intArrayOf(R.drawable.person_woman_summer_walk0_skin0, R.drawable.person_woman_summer_walk1_skin0, R.drawable.person_woman_summer_walk2_skin0, R.drawable.person_woman_summer_walk1_skin0),
                intArrayOf(R.drawable.person_woman_summer_walk0_skin1, R.drawable.person_woman_summer_walk1_skin1, R.drawable.person_woman_summer_walk2_skin1, R.drawable.person_woman_summer_walk1_skin1),
                intArrayOf(R.drawable.person_woman_summer_walk0_skin2, R.drawable.person_woman_summer_walk1_skin2, R.drawable.person_woman_summer_walk2_skin2, R.drawable.person_woman_summer_walk1_skin2),
            ),
            arrayOf(
                intArrayOf(R.drawable.person_woman_winter_walk0_skin0, R.drawable.person_woman_winter_walk1_skin0, R.drawable.person_woman_winter_walk2_skin0, R.drawable.person_woman_winter_walk1_skin0),
                intArrayOf(R.drawable.person_woman_winter_walk0_skin1, R.drawable.person_woman_winter_walk1_skin1, R.drawable.person_woman_winter_walk2_skin1, R.drawable.person_woman_winter_walk1_skin1),
                intArrayOf(R.drawable.person_woman_winter_walk0_skin2, R.drawable.person_woman_winter_walk1_skin2, R.drawable.person_woman_winter_walk2_skin2, R.drawable.person_woman_winter_walk1_skin2),
            ),
        ),
        // boy
        arrayOf(
            arrayOf(
                intArrayOf(R.drawable.person_boy_summer_walk0_skin0, R.drawable.person_boy_summer_walk1_skin0, R.drawable.person_boy_summer_walk2_skin0, R.drawable.person_boy_summer_walk1_skin0),
                intArrayOf(R.drawable.person_boy_summer_walk0_skin1, R.drawable.person_boy_summer_walk1_skin1, R.drawable.person_boy_summer_walk2_skin1, R.drawable.person_boy_summer_walk1_skin1),
                intArrayOf(R.drawable.person_boy_summer_walk0_skin2, R.drawable.person_boy_summer_walk1_skin2, R.drawable.person_boy_summer_walk2_skin2, R.drawable.person_boy_summer_walk1_skin2),
            ),
            arrayOf(
                intArrayOf(R.drawable.person_boy_winter_walk0_skin0, R.drawable.person_boy_winter_walk1_skin0, R.drawable.person_boy_winter_walk2_skin0, R.drawable.person_boy_winter_walk1_skin0),
                intArrayOf(R.drawable.person_boy_winter_walk0_skin1, R.drawable.person_boy_winter_walk1_skin1, R.drawable.person_boy_winter_walk2_skin1, R.drawable.person_boy_winter_walk1_skin1),
                intArrayOf(R.drawable.person_boy_winter_walk0_skin2, R.drawable.person_boy_winter_walk1_skin2, R.drawable.person_boy_winter_walk2_skin2, R.drawable.person_boy_winter_walk1_skin2),
            ),
        ),
        // girl
        arrayOf(
            arrayOf(
                intArrayOf(R.drawable.person_girl_summer_walk0_skin0, R.drawable.person_girl_summer_walk1_skin0, R.drawable.person_girl_summer_walk2_skin0, R.drawable.person_girl_summer_walk1_skin0),
                intArrayOf(R.drawable.person_girl_summer_walk0_skin1, R.drawable.person_girl_summer_walk1_skin1, R.drawable.person_girl_summer_walk2_skin1, R.drawable.person_girl_summer_walk1_skin1),
                intArrayOf(R.drawable.person_girl_summer_walk0_skin2, R.drawable.person_girl_summer_walk1_skin2, R.drawable.person_girl_summer_walk2_skin2, R.drawable.person_girl_summer_walk1_skin2),
            ),
            arrayOf(
                intArrayOf(R.drawable.person_girl_winter_walk0_skin0, R.drawable.person_girl_winter_walk1_skin0, R.drawable.person_girl_winter_walk2_skin0, R.drawable.person_girl_winter_walk1_skin0),
                intArrayOf(R.drawable.person_girl_winter_walk0_skin1, R.drawable.person_girl_winter_walk1_skin1, R.drawable.person_girl_winter_walk2_skin1, R.drawable.person_girl_winter_walk1_skin1),
                intArrayOf(R.drawable.person_girl_winter_walk0_skin2, R.drawable.person_girl_winter_walk1_skin2, R.drawable.person_girl_winter_walk2_skin2, R.drawable.person_girl_winter_walk1_skin2),
            ),
        ),
    )

    /**
     * Where a person is standing, which is what decides whether they dressed for the weather.
     *
     * The person lookup tables all carry a season axis, and until v4.15 every call site chose its
     * column the same way: `if (winterColorsEnabled) 1 else 0`. That is right for anyone the
     * weather can reach and wrong for anyone it cannot. A figure leaning out of their own kitchen
     * window was putting on a woolly hat because it had started snowing **outside their house**.
     *
     * Stated as where the person is rather than as which sprite to use, so the next figure added to
     * the scene answers the question by saying where it stands. [seasonIndexFor] is the only place
     * that turns the answer into a column, which is what keeps this from becoming a third
     * `if (winterColorsEnabled)` somewhere else.
     */
    private enum class Exposure {
        /** Pedestrians, and people in cars: the car is a coat, not a house. */
        OUTDOORS,

        /** Behind a pane, in a room with its own weather. Always the summer artwork. */
        INDOORS,
    }

    /** The season column [exposure] reads. The scene's winter clothing stops at the window. */
    private fun seasonIndexFor(exposure: Exposure): Int =
        if (exposure == Exposure.OUTDOORS && customization.winterColorsEnabled) 1 else 0

    /**
     * Window occupants, with the same skin axis: `[kind][season][skin]`.
     *
     * Separate from [personWindowHeadDrawables], the base artwork the recolours derive from.
     * (An rc2-era version of this comment said car passengers still read that table; the
     * vehicles have carried their own family since -- profiles in rc2, the frontal
     * [personCarHeadSkinDrawables] since rc4.)
     */
    private val personWindowHeadSkinDrawables = arrayOf(
        arrayOf(
            intArrayOf(R.drawable.person_man_summer_head_window_skin0, R.drawable.person_man_summer_head_window_skin1, R.drawable.person_man_summer_head_window_skin2),
            intArrayOf(R.drawable.person_man_winter_head_window_skin0, R.drawable.person_man_winter_head_window_skin1, R.drawable.person_man_winter_head_window_skin2),
        ),
        arrayOf(
            intArrayOf(R.drawable.person_woman_summer_head_window_skin0, R.drawable.person_woman_summer_head_window_skin1, R.drawable.person_woman_summer_head_window_skin2),
            intArrayOf(R.drawable.person_woman_winter_head_window_skin0, R.drawable.person_woman_winter_head_window_skin1, R.drawable.person_woman_winter_head_window_skin2),
        ),
        arrayOf(
            intArrayOf(R.drawable.person_boy_summer_head_window_skin0, R.drawable.person_boy_summer_head_window_skin1, R.drawable.person_boy_summer_head_window_skin2),
            intArrayOf(R.drawable.person_boy_winter_head_window_skin0, R.drawable.person_boy_winter_head_window_skin1, R.drawable.person_boy_winter_head_window_skin2),
        ),
        arrayOf(
            intArrayOf(R.drawable.person_girl_summer_head_window_skin0, R.drawable.person_girl_summer_head_window_skin1, R.drawable.person_girl_summer_head_window_skin2),
            intArrayOf(R.drawable.person_girl_winter_head_window_skin0, R.drawable.person_girl_winter_head_window_skin1, R.drawable.person_girl_winter_head_window_skin2),
        ),
    )

    /**
     * The vehicle occupants: frontal busts in the pedestrians' own face language, with the
     * seatbelt that says "person in a car" -- `[kind][season][skin]`, the same three axes the
     * walkers carry.
     *
     * rc4, the maintainer's direction call: one scene, one human style. rc2's profile family
     * (side view, nose and jaw) was the only face in the frame drawn in a second language, and
     * it is retired; the frontal `head_car` artwork is the rc1 face -- which was always the
     * pedestrian's face -- re-authored only in its torso depth (see [HEAD_CAR_HEAD_UNITS]) and
     * extended to the full family x season x skin coverage the pedestrians have, children
     * included. Both seats index only the first two rows (adults are seated, by construction);
     * the child rows exist so the coverage is the pedestrians' and stand ready for the day a
     * pane can light one -- the saloon's cannot, at 11-15% against the 15% criterion, and the
     * measurement is at [CAR_PASSENGER_X_UNITS]. The window
     * table above still serves the buildings, whose artwork carries the smile this family and
     * the walkers deliberately do not.
     */
    /**
     * **The four adult base busts were deleted in v4.19, and this table is why they could be.**
     *
     * It listed all eight `person_*_head_car` bases and was never read by anything -- its only
     * effect was to keep the files referenced so lint would not report them unused. The three
     * `_skinN` copies of each carry all three tones (the base *is* one of them), so the four
     * adult bases were 297 792 B of decoded-set budget that no draw path could ever reach:
     * item 7 of `BACKLOG_v4_19.md`, which called deleting them "free and safe". They paid for
     * more than half of what the three new bodies cost.
     *
     * The four child bases are still here and still shipped. They are the same kind of
     * redundancy and would free the same 297 792 B, but the pass brief put the children's
     * artwork behind a hard "never", and 0.141 MiB of headroom did not make it necessary.
     */
    private val personCarHeadDrawables = arrayOf(
        intArrayOf(R.drawable.person_boy_summer_head_car, R.drawable.person_boy_winter_head_car),
        intArrayOf(R.drawable.person_girl_summer_head_car, R.drawable.person_girl_winter_head_car),
    )

    private val personCarHeadSkinDrawables = arrayOf(
        arrayOf(
            intArrayOf(R.drawable.person_man_summer_head_car_skin0, R.drawable.person_man_summer_head_car_skin1, R.drawable.person_man_summer_head_car_skin2),
            intArrayOf(R.drawable.person_man_winter_head_car_skin0, R.drawable.person_man_winter_head_car_skin1, R.drawable.person_man_winter_head_car_skin2),
        ),
        arrayOf(
            intArrayOf(R.drawable.person_woman_summer_head_car_skin0, R.drawable.person_woman_summer_head_car_skin1, R.drawable.person_woman_summer_head_car_skin2),
            intArrayOf(R.drawable.person_woman_winter_head_car_skin0, R.drawable.person_woman_winter_head_car_skin1, R.drawable.person_woman_winter_head_car_skin2),
        ),
        arrayOf(
            intArrayOf(R.drawable.person_boy_summer_head_car_skin0, R.drawable.person_boy_summer_head_car_skin1, R.drawable.person_boy_summer_head_car_skin2),
            intArrayOf(R.drawable.person_boy_winter_head_car_skin0, R.drawable.person_boy_winter_head_car_skin1, R.drawable.person_boy_winter_head_car_skin2),
        ),
        arrayOf(
            intArrayOf(R.drawable.person_girl_summer_head_car_skin0, R.drawable.person_girl_summer_head_car_skin1, R.drawable.person_girl_summer_head_car_skin2),
            intArrayOf(R.drawable.person_girl_winter_head_car_skin0, R.drawable.person_girl_winter_head_car_skin1, R.drawable.person_girl_winter_head_car_skin2),
        ),
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
        dayBlend: Float,
    ) {
        if (geom.tileWidth <= 0f) return
        val config = customization.people
        if (!config.visible) return
        val seasonIdx = seasonIndexFor(Exposure.OUTDOORS)
        val sceneScale = SceneSpace.sceneScale(screenHeight)
        // Density thins the same candidate pool the same way every other category's does, through
        // the shared threshold rather than by rounding a count -- so lowering it removes a
        // particular pedestrian and leaves the rest exactly where they were, instead of
        // reshuffling everybody.
        // v4.1: the offset is the people system's own constant. See
        // [PedestrianPopulation.THRESHOLD_OFFSET] for why `offsetFor(PEDESTRIAN_THRESHOLD_SALT)`
        // was wrong -- it returned 683.5, and its fractional part was MOUNTAINS_BACK's offset.
        // Day and night have their own populations, crossfaded by the scene's own dayBlend --
        // the same value the colours blend with, so the street empties over the length of dusk
        // instead of four people vanishing between two frames. Because the threshold below is
        // stable per pedestrian, a falling density removes particular people and leaves the rest
        // exactly where they were.
        val density = PeopleDensity.at(
            dayDensity = config.density,
            nightDensity = customization.peopleNightDensity,
            dayBlend = dayBlend,
        )
        // The population arrives already sorted far-to-near, so drawing it in order puts the
        // nearest figure on top. v4.0 walked candidate indices instead, and because the pavement
        // row alternated with that index the far figure was drawn *after* the near one and covered
        // it -- the reported overlap defect. Depth now comes from the figure's own baseline; see
        // [PedestrianPopulation].
        val population = PedestrianPopulation.build(
            seed = themeId.hashCode(),
            density = density,
            nearRowYFraction = SceneSpace.PAVEMENT_NEAR_Y_FRACTION,
            farRowYFraction = SceneSpace.PAVEMENT_FAR_Y_FRACTION,
        )
        for (person in population) {
            // Both the row's y and the speed at it come from [SceneSpace]: a pedestrian on the
            // near row is nearer than one on the far row, so it is drawn larger and crosses the
            // screen faster, by the same ratio the two ground lines imply. People used to sit at a
            // hardcoded 0.83 of screen height at a fixed scale with a hand-rolled speed ladder --
            // outside the projection entirely, which is the failure class `DESIGN_NOTES.md` 6
            // records against them.
            val rowYFraction = person.rowYFraction
            val nearer = rowYFraction > (SceneSpace.PAVEMENT_NEAR_Y_FRACTION + SceneSpace.PAVEMENT_FAR_Y_FRACTION) / 2f
            val speed = if (nearer) SceneSpace.PEDESTRIAN_SPEED_NEAR else SceneSpace.PEDESTRIAN_SPEED_FAR
            val y = screenHeight * rowYFraction
            val dir = person.direction
            val s = SceneSpace.PERSON_BASE_SCALE * SceneSpace.perspectiveScaleAt(rowYFraction) * sceneScale

            // The walk, as a position on the ground rather than on the screen. `cycle` returns
            // 0..1 over one loop, which is a whole tile of walking; the start offset spreads them
            // out so they are not a column.
            val walk = elapsedSeconds.cycle(speed, person.phase)
            var tileFraction = (person.startFraction + dir * walk) % 1f
            if (tileFraction < 0f) tileFraction += 1f

            var x = geom.shiftXWrapped + tileFraction * geom.tileWidth
            if (x < -geom.tileWidth * 0.5f) x += geom.tileWidth

            // **v4.2: the walk frame is staggered by the figure's own address, not by where it
            // happens to sit in the sorted list.** v4.1 passed the list position, and the list is
            // sorted by depth -- so inserting one pedestrian shifted the position of every
            // pedestrian sorted behind it and stepped all of their legs to a different frame. That
            // made moving the People slider by one notch re-animate the survivors, which is the
            // stability contract `CandidateThreshold` exists to keep and which
            // `PedestrianPopulationTest` could not see, being a test about the population rather
            // than about the frame. The address is `groupIndex * MAX_GROUP_SIZE + memberIndex`,
            // the same one the population itself is addressed by, so it is fixed for a figure
            // whatever else is on the street.
            val walkStagger = person.groupIndex * PedestrianPopulation.MAX_GROUP_SIZE + person.memberIndex
            val frame = elapsedSeconds.frameIndex(3.2f, walkStagger.toFloat(), 4)
            val resId = personWalkSkinDrawables[person.kindIndex][seasonIdx][person.skinIndex][frame]
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
     * "road" the cars drive on, not to any particular hill layer): its *position* never moves.
     * Its surface pattern does advance, though, and it does so on the same shared
     * `scrollSpeed` every other scrolling element uses -- not an independent rate. This
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
        drawWindowOccupant(canvas, r, -37f, -46f, 22f, 22f, WindowBuildingKind.HOUSE, 0, SMALL_HOUSE_WINDOWS)
        drawSprite(canvas, R.drawable.house_shared_window, 15f, -45f)
        drawSpriteFaded(canvas, R.drawable.house_window_lit, 15f, -46f, litWindowAlpha(nightGlow))
        // v4.1: the second window was drawn but never populated -- one of the two panes on this
        // elevation could not hold anybody at all.
        drawWindowOccupant(canvas, r, 15f, -46f, 22f, 22f, WindowBuildingKind.HOUSE, 1, SMALL_HOUSE_WINDOWS)
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
        // v4.1: all four panes are candidates now. Only the upper-left one ever was.
        drawWindowOccupant(canvas, r, -46f, -85f, 22f, 22f, WindowBuildingKind.HOUSE, 0, LARGE_HOUSE_WINDOWS)
        drawWindowOccupant(canvas, r, 24f, -85f, 22f, 22f, WindowBuildingKind.HOUSE, 1, LARGE_HOUSE_WINDOWS)
        drawSprite(canvas, R.drawable.house_shared_window, -46f, -44f)
        drawSpriteFaded(canvas, R.drawable.house_window_lit, -46f, -45f, litAlpha)
        drawSprite(canvas, R.drawable.house_shared_window, 24f, -44f)
        drawSpriteFaded(canvas, R.drawable.house_window_lit, 24f, -45f, litAlpha)
        drawWindowOccupant(canvas, r, -46f, -45f, 22f, 22f, WindowBuildingKind.HOUSE, 2, LARGE_HOUSE_WINDOWS)
        drawWindowOccupant(canvas, r, 24f, -45f, 22f, 22f, WindowBuildingKind.HOUSE, 3, LARGE_HOUSE_WINDOWS)
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
    /**
     * What a window is, as a colour: cool glass by day, warm light at night.
     *
     * One pair, because there is one answer. It was already the restaurant's, written inline; the
     * tower now reads the same two constants rather than a second pair that could drift from it,
     * and a future window has somewhere to look. The night value is the one the lit-window artwork
     * was drawn in, so nothing about the existing night look moves.
     */
    private fun windowGlassColor(nightGlow: Float): Int =
        ColorUtils.blendARGB(WINDOW_GLASS_DAY, WINDOW_GLASS_NIGHT, nightGlow.coerceIn(0f, 1f))

    /**
     * How lit a vehicle's lamps are, on the same ramp the windows use.
     *
     * Vehicles were the one thing in the scene that did not change between noon and midnight: the
     * houses lit their windows, the shops lit their frontages, and the traffic stayed exactly as
     * bright as it had been at midday, police beacon included. This reuses `litWindowAlpha`'s
     * curve so a car lights up when a window does, and takes 80% of it so that a lamp reads as a
     * lamp rather than as a light source: at a hundred and forty pixels a car is two small warm
     * marks, and anything stronger is a neon toy.
     *
     * Zero for the whole first third of the evening, which is what makes this free by day: every
     * call site is behind `if (alpha > 0)`, so at noon the vehicles cost exactly what they cost
     * before.
     */
    private fun litVehicleAlpha(nightGlow: Float): Int = (litWindowAlpha(nightGlow) * 0.8f).toInt()

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
    private fun drawWindowOccupant(
        canvas: SceneCanvas,
        r: StaticRuntime,
        winX: Float,
        winY: Float,
        winW: Float,
        winH: Float,
        kind: WindowBuildingKind,
        windowIndex: Int,
        windowCount: Int,
    ) {
        // The building's own stable identity. `tileFractionX` is its position along the ground
        // tile, which is fixed for the life of the scene, so an occupant does not move house
        // between frames.
        val buildingSeed = (r.spec.tileFractionX * 100_003f).toInt()
        val seed = themeId.hashCode()
        // v4.2 passes how many windows this building has, because occupancy is now a count dealt
        // across the building's own panes rather than a coin flipped at each one. See
        // [WindowOccupants.occupantCount] for the tail that removes.
        if (!WindowOccupants.isOccupied(seed, buildingSeed, windowIndex, windowCount, kind)) return
        // Indoors: see [Exposure]. The hat belongs to the street, not to the room behind the pane.
        val seasonIdx = seasonIndexFor(Exposure.INDOORS)
        val occupant = WindowOccupants.occupantAt(seed, buildingSeed, windowIndex)
        val resId = personWindowHeadSkinDrawables[occupant.kindIndex][seasonIdx][occupant.skinIndex]
        // Placed from the sprite's declared anchor, not by centring its canvas -- the same
        // correction v76.1 made to the car driver, applied here for the same reason. The window
        // heads are 53x57 local units anchored CONTENT_BOTTOM_CENTRE, so centring the canvas put
        // the bust's shoulders a third of a pane below the sill. The bust now stands on the
        // window's own lower edge.
        //
        // **REN-07: this said 60x54, and the divisor below still says 60.** The canvas is 159x171
        // px, which is 53x57 units; it was 180 px wide when the divisor was written and lost a
        // column in the SCL-01 pass. Dividing by 60 therefore draws the bust at 53/60 of the
        // intended 85% of the pane -- about 75% -- so the occupants are a little smaller than the
        // rule says. Left as it is deliberately: 75% of the pane is what has shipped since v4.2 and
        // is what the artwork was tuned against by eye, so the number is the record of a decision
        // even though the reasoning written beside it was wrong. `WindowOccupantScaleTest` pins
        // both halves so neither can drift again.
        val cx = winX + winW / 2f
        val cy = winY + winH
        canvas.save()
        canvas.translate(cx, cy)
        // Not the canvas width -- see the note above. This is the tuned divisor, and 60 is what it
        // has always been; the canvas is 53 units.
        val s = (winW * 0.85f) / WINDOW_OCCUPANT_DIVISOR_UNITS
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
     * No outline anymore: the canopy is flat and unbordered, which is what the paper-cutout look
     * calls for (see v63's own changelog entry on that correction).
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
        drawSprite(canvas, R.drawable.tree_trunk, TreeSpriteLayout.TRUNK_X, TreeSpriteLayout.TRUNK_Y)
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
            drawSprite(
                canvas, R.drawable.tree_dead_branches,
                TreeSpriteLayout.DEAD_BRANCHES_X, TreeSpriteLayout.DEAD_BRANCHES_Y,
            )
            canvas.restore()
            return
        }
        // canopy: local bbox (-45,-84)-(45,0), refreshed in the aesthetic pass to a 5-lobe
        // silhouette (was a single blob) with its attachment point at local y=0 so it sits
        // flush on the trunk regardless of sway.
        drawTintedSprite(
            canvas, R.drawable.tree_canopy,
            TreeSpriteLayout.CANOPY_X, TreeSpriteLayout.CANOPY_Y, leafColor,
        )
        if (customization.winterColorsEnabled) {
            // The cap is cut to this canopy's own outline: its top edge repeats the crown's
            // upper vertices exactly, so the snow reaches both shoulders and the ridge instead
            // of sitting inside them. It was a 216x126 cap at (-36,-78), which is 2 units below
            // the crown's ridge and 5 short of each shoulder -- enough to leave a green rim
            // above the snow and bare green corners either side of it. Redrawn at 234x126 with
            // an origin derived from the crown rather than guessed; if the canopy art changes,
            // both move together.
            drawSprite(
                canvas, R.drawable.tree_canopy_snowcap,
                TreeSpriteLayout.SNOWCAP_X, TreeSpriteLayout.SNOWCAP_Y,
            )
        }
        // Inside the canopy's own transform, and scattered across the canopy's own measured
        // content: `tree_canopy` is 246x222 px with content filling it, which is
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
    /**
     * The two shop fronts' repeated blit positions, held as fields for the same reason
     * [christmasLightX] is: a `floatArrayOf(...)` written inside a `draw*` function is a new array
     * every frame, and the draw path allocates nothing.
     */
    private val restaurantUpperWindowX =
        floatArrayOf(RESTAURANT_UPPER_WINDOW_LEFT_X, RESTAURANT_UPPER_WINDOW_RIGHT_X)
    private val barFrontPaneX = floatArrayOf(BAR_FRONT_PANE_LEFT_X, BAR_FRONT_PANE_RIGHT_X)

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
        val height = SkyscraperSpriteLayout.HEIGHT
        val width = SkyscraperSpriteLayout.WIDTH
        val wallColor = customization.colorFor(r.spec, dayBlend)
        val trimColor = ColorUtils.blendARGB(wallColor, 0xFF000000.toInt(), 0.35f)

        val nightGlow = (1f - dayBlend).coerceIn(0f, 1f)

        drawGroundShadow(canvas, width * 0.6f)
        drawSprite(
            canvas, R.drawable.skyscraper_canopy,
            SkyscraperSpriteLayout.CANOPY_X, SkyscraperSpriteLayout.CANOPY_Y,
        )
        drawTintedSprite(
            canvas, R.drawable.skyscraper_wall,
            SkyscraperSpriteLayout.WALL_X, -height, wallColor,
        )
        // **The window grid, tinted, exactly the way the restaurant's window is.** `skyscraper_wall`
        // paints a grid of its own, but it takes the wall's tint with it, so the tower's daytime
        // windows were whatever colour the user had picked for its bricks -- which is not what a
        // window looks like anywhere else in this scene. Houses show cool glass by day and warm
        // light at night; so does the restaurant; the tower did not.
        //
        // `skyscraper_wall_lit` is now a white mask rather than warm artwork (the convention every
        // tintable window asset in this set follows -- see `restaurant_window`), so one blit
        // carries both halves of the day: [windowGlassColor] crossfades cool to warm on the same
        // `nightGlow` the restaurant uses. The alpha ramp this call used to have is gone with it,
        // and so is the tower's private answer to "when does a window light up".
        //
        // It stays one blit per building per wrap-tile: the nested `drawRect` loop this style used
        // before the V2 asset set is not coming back, and the colour is computed once per call
        // from a value the frame already has.
        drawTintedSprite(
            canvas, R.drawable.skyscraper_wall_lit,
            SkyscraperSpriteLayout.WALL_LIT_X, -height + SkyscraperSpriteLayout.WALL_LIT_DY,
            windowGlassColor(nightGlow),
        )
        // **The entrance, on the ground the building and the people stand on.** Blitted after the
        // wall so it sits in the hall band the facade draws, and with its own bottom edge on y=0:
        // the canopy straddles the ground line and is a plinth, not a floor to stand a door on.
        drawSprite(
            canvas, R.drawable.skyscraper_entrance,
            SkyscraperSpriteLayout.ENTRANCE_X, SkyscraperSpriteLayout.ENTRANCE_Y,
        )
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
        // **v4.1: people at tower windows.** The grid is the one the comment above states, and it
        // is read here rather than redefined: four rows of four 14-unit windows at a 27 pitch from
        // the top, at a 20 pitch across from `-width/2 + 5`. Nothing about the window changes --
        // the panes are painted into `skyscraper_wall` and are not redrawn here. This only stands
        // a bust on a sill, at a low per-window rate so a tower does not become a wall of faces.
        for (row in 0 until 4) {
            for (column in 0 until 4) {
                val index = row * 4 + column
                drawWindowOccupant(
                    canvas, r,
                    -width / 2f + 5f + column * 20f,
                    -height + 5f + row * 27f,
                    14f, 14f,
                    WindowBuildingKind.SKYSCRAPER, index, SKYSCRAPER_WINDOWS,
                )
            }
        }
        drawTintedSprite(
            canvas, R.drawable.skyscraper_setback,
            SkyscraperSpriteLayout.SETBACK_X, -height + SkyscraperSpriteLayout.SETBACK_DY, wallColor,
        )
        // The setback's roof is the only horizontal surface of a tower a viewer sees, so it is
        // where the snow goes. Its own block starts 6 units down its canvas, and the cap carries 8
        // units above the roofline it is cut for, hence the offset. Drawn before the mast, so the
        // mast rises out of the drift. See [drawSmallHouse] for why this is a layer and not a tint.
        if (customization.winterColorsEnabled) {
            drawSprite(
                canvas, R.drawable.skyscraper_roof_snow,
                SkyscraperSpriteLayout.ROOF_SNOW_X, -height + SkyscraperSpriteLayout.ROOF_SNOW_DY,
            )
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

        drawGroundShadow(canvas, 50f * 0.58f)

        // wall: local bbox (-50,-60)-(50,0)
        drawTintedSprite(canvas, R.drawable.restaurant_wall, -50f, -96f, wallColor)
        drawTintedSprite(
            canvas, R.drawable.restaurant_cornice,
            RESTAURANT_CORNICE_X, RESTAURANT_CORNICE_Y, wallColor,
        )
        // A flat roof, so the cap is a drift standing proud of the parapet rather than following a
        // pitch. Its canvas puts the wall's own top edge 8 units down. See [drawSmallHouse].
        if (customization.winterColorsEnabled) {
            drawSprite(canvas, R.drawable.restaurant_roof_snow, -26f, -111.5f)
        }
        val nightGlow = (1f - dayBlend).coerceIn(0f, 1f)
        // **The storey over the shop was a blank slab.** `restaurant_wall` carries no openings
        // above its string course -- the bar's carries three, a large house four -- so in a row
        // with two houses the restaurant was the one building with a dead first floor, and at
        // night it was a black rectangle between two lit ones. These are the same drawable the
        // bar's upper storey and every house use, for the reason already written there: a shop's
        // first floor must not be able to drift from a house's. They take no occupant, which is
        // deliberate -- the frontage panes below are the restaurant's two occupant slots and
        // adding more would change who stands where.
        for (wx in restaurantUpperWindowX) {
            drawSprite(canvas, R.drawable.house_shared_window, wx, RESTAURANT_UPPER_WINDOW_Y)
            drawSpriteFaded(
                canvas, R.drawable.house_window_lit, wx, RESTAURANT_UPPER_WINDOW_Y - 1f,
                litWindowAlpha(nightGlow),
            )
        }
        // window, lit warm at night
        drawTintedSprite(canvas, R.drawable.restaurant_window, -35f, -45f, windowGlassColor(nightGlow))
        // **v4.2: the restaurant's frontage can hold somebody.** This call site is the whole of
        // the reported "no people in commercial buildings": a restaurant is one of the two
        // non-residential street-level buildings the scene draws, it is the *more* common of the
        // two -- two to four per theme against roughly one bar, and `beach`, `new_year` and
        // `spring` have no bar at all -- and v4.1 gave it no occupant call at all. Its window was
        // therefore unpopulatable on every theme, which no count of "3/3 populatable panes" on the
        // bar could reveal.
        //
        // The frontage is one 30x22-unit sprite carrying two glass panes, so it takes two
        // occupants -- see [RESTAURANT_PANE_A_CENTRE_X] for the measurement and for why the
        // occupant box is not the pane's own width. Two slots also matter for *how often* anybody
        // is there: occupancy is a count dealt across a building's panes, and a one-pane building
        // degenerates back to the single coin flip v4.2 exists to remove. The window drawing above
        // is untouched -- this only stands busts behind glass that was already being painted.
        drawWindowOccupant(
            canvas, r,
            RESTAURANT_PANE_A_CENTRE_X - OCCUPANT_BOX_UNITS / 2f, RESTAURANT_WINDOW_Y,
            OCCUPANT_BOX_UNITS, OCCUPANT_BOX_UNITS,
            WindowBuildingKind.COMMERCIAL, 0, RESTAURANT_WINDOWS,
        )
        drawWindowOccupant(
            canvas, r,
            RESTAURANT_PANE_B_CENTRE_X - OCCUPANT_BOX_UNITS / 2f, RESTAURANT_WINDOW_Y,
            OCCUPANT_BOX_UNITS, OCCUPANT_BOX_UNITS,
            WindowBuildingKind.COMMERCIAL, 1, RESTAURANT_WINDOWS,
        )
        // The full-width canopy, still drawn after the glass it shades and above it -- see the
        // v4.18 note for why that order is load-bearing. It spans the whole frontage now.
        drawSprite(canvas, R.drawable.restaurant_awning, RESTAURANT_AWNING_X, RESTAURANT_AWNING_Y)
        if (customization.christmasDecorationsEnabled) {
            drawWindowLights(canvas, r, elapsed, -35f, -22f, 30f)
        }
        // **The frontage, rebuilt as a trattoria's.** The old composition was a 34x35 billboard
        // hung across the upper storey, a token awning, and a door two shades darker than an
        // already dark wall: the ground floor -- the storey a shop is about -- was the emptiest,
        // lowest-contrast part of the building. Now the identity lives where a restaurant carries
        // it: the fascia board over the shopfront (with the fork-and-knife badge in its middle),
        // planters on the pavement, and a framed wood-and-glass entrance. The planters are the
        // houses' own planter sprite, and the right one is drawn before the door so the door's
        // frame covers their two-unit overlap.
        drawSprite(canvas, R.drawable.house_shared_planter, RESTAURANT_PLANTER_LEFT_X, RESTAURANT_PLANTER_Y)
        drawSprite(canvas, R.drawable.house_shared_planter, RESTAURANT_PLANTER_RIGHT_X, RESTAURANT_PLANTER_Y)
        drawSprite(canvas, R.drawable.restaurant_door, 8f, -28f)
        drawSprite(canvas, R.drawable.restaurant_sign, RESTAURANT_SIGN_X, RESTAURANT_SIGN_Y)
    }

    /**
     * Sprite-blit conversion (aesthetic-pass batch 2, refreshed in batch 4): wall and door are
     * bitmap blits. The hanging sign now shows a beer-mug icon (a fixed accent sprite) instead
     * of a plain glowing circle, so "bar" reads immediately instead of depending on the reader
     * already knowing it's a bar. String lights stay vector, unchanged.
     */
    private fun drawBarBuilding(canvas: SceneCanvas, r: StaticRuntime, elapsed: SceneTime, dayBlend: Float) {
        val width = 90f
        val wallColor = customization.colorFor(r.spec, dayBlend)

        drawGroundShadow(canvas, width * 0.6f)

        // wall: local bbox (-45,-55)-(45,0)
        drawTintedSprite(canvas, R.drawable.bar_wall, -45f, -92f, wallColor)
        drawTintedSprite(canvas, R.drawable.bar_cornice, BAR_CORNICE_X, BAR_CORNICE_Y, wallColor)
        // Same construction as the restaurant's, cut to this wall's narrower 90 units.
        if (customization.winterColorsEnabled) {
            drawSprite(canvas, R.drawable.bar_roof_snow, -50f, -110.5f)
        }
        val barNight = (1f - dayBlend).coerceIn(0f, 1f)
        // **The painted pub front.** v4.18 glazed the street level and it still read as a slab,
        // because the slab itself was the problem: the frontage was the same tinted wall as the
        // storey above it. A pub's ground floor is a painted joinery front in its own colour, so
        // that is what this is -- a deep green field the width of the frontage, drawn as two
        // rectangles rather than as a sprite so that it darkens into the night on the same ramp
        // as everything around it, with the fascia board lapped over its top edge. The panes are
        // the shopfront glazing both businesses now share, and they still carry nobody: the three
        // upstairs slots are this building's occupancy and are untouched.
        fillPaint.color = ColorUtils.blendARGB(BAR_FRONT_DAY, BAR_FRONT_NIGHT, barNight)
        canvas.drawRect(BAR_FRONT_FIELD_LEFT_X, BAR_FRONT_FIELD_TOP_Y, BAR_FRONT_FIELD_RIGHT_X, 0f, fillPaint)
        fillPaint.color = ColorUtils.blendARGB(BAR_FRONT_EDGE_DAY, BAR_FRONT_EDGE_NIGHT, barNight)
        canvas.drawRect(
            BAR_FRONT_FIELD_LEFT_X, BAR_FRONT_FIELD_TOP_Y,
            BAR_FRONT_FIELD_RIGHT_X, BAR_FRONT_FIELD_TOP_Y + BAR_FRONT_EDGE_HEIGHT, fillPaint,
        )
        for (wx in barFrontPaneX) {
            drawTintedSprite(canvas, R.drawable.restaurant_window, wx, BAR_FRONT_PANE_Y, windowGlassColor(barNight))
        }
        drawSprite(canvas, R.drawable.bar_door, BAR_DOOR_X, -28f)
        // The upper storey's windows, the same drawable the houses use so a shop's first floor
        // cannot drift from a house's.
        val barLit = litWindowAlpha((1f - dayBlend).coerceIn(0f, 1f))
        for ((wi, wx) in floatArrayOf(-34f, -11f, 12f).withIndex()) {
            drawSprite(canvas, R.drawable.house_shared_window, wx, -82f)
            drawSpriteFaded(canvas, R.drawable.house_window_lit, wx, -83f, barLit)
            // v4.1: commercial frontage can hold somebody. The window drawing above is byte for
            // byte what it was; this only adds a bust standing at its sill.
            drawWindowOccupant(canvas, r, wx, -83f, 22f, 22f, WindowBuildingKind.COMMERCIAL, wi, BAR_WINDOWS)
        }
        if (customization.christmasDecorationsEnabled) {
            for ((i, wx) in floatArrayOf(-34f, -11f, 12f).withIndex()) {
                if (litWindowChosen(r, i, 3, 2)) drawWindowLights(canvas, r, elapsed, wx, -61f, 22f)
            }
        }

        // The fascia board, and the carriage lantern on the front's corner. The lantern replaced
        // the four string-light dots that read as brown rivets by day; at night a soft glow stands
        // behind its glass on the same ramp the windows use, and by day the glow is not drawn.
        drawSprite(canvas, R.drawable.bar_sign, BAR_SIGN_X, BAR_SIGN_Y)
        val lanternGlow = litWindowAlpha(barNight)
        if (lanternGlow > 0) {
            fillPaint.color = 0xFFFFD54A.toInt()
            fillPaint.alpha = lanternGlow / 2
            canvas.drawCircle(BAR_LANTERN_GLOW_X, BAR_LANTERN_GLOW_Y, BAR_LANTERN_GLOW_RADIUS, fillPaint)
            fillPaint.alpha = 255
        }
        drawSprite(canvas, R.drawable.bar_lantern, BAR_LANTERN_X, BAR_LANTERN_Y)
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
        // **The same switch that strips the tree crowns carves the pumpkins.** `halloweenEnabled`
        // already exists, is already per-theme, already persists and is already in the backups, so
        // a lantern needs no new setting -- only a face. It is drawn on the body's own canvas at
        // the body's own origin, so it registers with the fruit rather than being positioned
        // against it, and it is fixed art: a carved hole is not a colour a theme gets to pick.
        if (customization.halloweenEnabled) {
            drawSprite(canvas, R.drawable.pumpkin_face, -19f, -30f)
        }
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
    private fun drawFireTruck(canvas: SceneCanvas, nightGlow: Float) {
        drawSprite(canvas, R.drawable.firetruck_ladder, FIRE_TRUCK_LADDER_X_UNITS, FIRE_TRUCK_LADDER_Y_UNITS)
        drawSprite(canvas, R.drawable.firetruck_body, FIRE_TRUCK_BODY_X_UNITS, FIRE_TRUCK_BODY_Y_UNITS)
        // The beacons brighten toward night rather than gaining a layer: a colour that is already
        // being painted costs nothing to change, and two lamps that stay the same shade at midnight
        // as at noon are what made the traffic read as switched off.
        fillPaint.color = ColorUtils.blendARGB(0xFFD6362E.toInt(), BEACON_RED_LIT, nightGlow)
        canvas.drawRect(
            FIRE_TRUCK_BEACON_RED_LEFT_X, FIRE_TRUCK_BEACON_TOP_Y_UNITS,
            FIRE_TRUCK_BEACON_RED_RIGHT_X, FIRE_TRUCK_CAB_ROOF_Y_UNITS, fillPaint,
        )
        fillPaint.color = ColorUtils.blendARGB(0xFF2B5FCB.toInt(), BEACON_BLUE_LIT, nightGlow)
        canvas.drawRect(
            FIRE_TRUCK_BEACON_BLUE_LEFT_X, FIRE_TRUCK_BEACON_TOP_Y_UNITS,
            FIRE_TRUCK_BEACON_BLUE_RIGHT_X, FIRE_TRUCK_CAB_ROOF_Y_UNITS, fillPaint,
        )
        // The same two lenses every car carries, at the appliance's own seats.
        drawVehicleLamps(
            canvas, nightGlow,
            FIRE_TRUCK_LAMP_FRONT_X_UNITS, FIRE_TRUCK_LAMP_FRONT_Y_UNITS,
            FIRE_TRUCK_LAMP_REAR_X_UNITS, FIRE_TRUCK_LAMP_REAR_Y_UNITS,
        )
    }

    /**
     * The amber-forward, red-aft pair that makes a vehicle's direction readable from the vehicle
     * alone, drawn from the four sprites the whole fleet shares.
     *
     * The day pair is drawn first and always: a shell's own baked lens multiplies with the body
     * colour and comes out whatever the paint is, so a fixed-art overlay is what keeps amber
     * amber on a red car. The lit pair fades in over it after dark.
     */
    private fun drawVehicleLamps(
        canvas: SceneCanvas, nightGlow: Float,
        frontX: Float, frontY: Float, rearX: Float, rearY: Float,
    ) {
        drawSprite(canvas, R.drawable.car_lamp_front, frontX, frontY)
        drawSprite(canvas, R.drawable.car_lamp_rear, rearX, rearY)
        val lit = litVehicleAlpha(nightGlow)
        if (lit > 0) {
            drawSpriteFaded(canvas, R.drawable.car_lamp_front_lit, frontX, frontY, lit)
            drawSpriteFaded(canvas, R.drawable.car_lamp_rear_lit, rearX, rearY, lit)
        }
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
    /**
     * One seated bust, anchored on the sill at [x] and scaled by the vehicle's own occupant
     * scale. A named method rather than a lambda: the draw path allocates nothing, and two seats
     * would otherwise be two copies of the same five lines.
     */
    private fun drawSeatedOccupant(canvas: SceneCanvas, x: Float, y: Float, scale: Float, occupantRes: Int) {
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(scale, scale)
        drawSprite(canvas, occupantRes, -HEAD_CAR_ANCHOR_X_UNITS, -HEAD_CAR_ANCHOR_Y_UNITS)
        canvas.restore()
    }

    private fun drawCar(canvas: SceneCanvas, c: CarRuntime, screenWidth: Float, screenHeight: Float, dayBlend: Float) {
        val margin = vehicleEdgeMarginPx(screenHeight)
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

        // Which of the three bodies this car is. Resolved once, when the runtime was built, from
        // the spec's own immutable fields -- never here, and never from a list index. See
        // [CarShell.forCar].
        val shell = c.shell
        val isFireTruck = c.spec.type == CarType.FIRE_TRUCK

        // A vehicle is on the ground plane like everything else, so its size comes from the same
        // projection: its own lane's [SceneSpace.perspectiveScaleAt], not a flat global factor.
        // Cars used to be drawn at one fixed scale whatever lane they were in, which is what let
        // a car end up taller than a pedestrian and made the two lanes read as one row at two
        // heights rather than as two depths.
        //
        // All three car bodies share one base scale, because they share one metre-per-unit
        // ([SceneSpace.CAR_UNIT_METRES]) -- that is what keeps a unit the same pixel across the
        // family even though the three are different heights.
        val vehicleScale = (
            if (isFireTruck) SceneSpace.FIRE_TRUCK_BASE_SCALE else SceneSpace.CAR_BASE_SCALE
            ) * SceneSpace.perspectiveScaleAt(c.spec.laneYFraction) * SceneSpace.sceneScale(screenHeight)

        val nightGlow = (1f - dayBlend).coerceIn(0f, 1f)

        canvas.save()
        canvas.translate(x, y)
        canvas.scale(dir * vehicleScale, vehicleScale)
        // Aesthetic-pass batch 5 fix: the redrawn car's own coordinates put the wheel-bottom at
        // local y=37 (wheel center 28 + radius 9), not y=0 like the old body did -- every other
        // part of this file (drawRoad's own margin, drawGroundShadow) assumes y=0 is an object's
        // ground contact point, so the car was drawing well below where the road/shadow expected
        // it, which is what let it visually spill outside the road. Shifting the whole car up by
        // that same 37 units here re-aligns it without having to renumber every coordinate below.
        canvas.translate(0f, -VEHICLE_GROUND_Y_UNITS)

        // On the road, not on the bonnet. See [VEHICLE_GROUND_Y_UNITS].
        canvas.save()
        canvas.translate(0f, VEHICLE_GROUND_Y_UNITS)
        // Each body's own footprint: an estate is 32 units longer than a compact and used to
        // cast the same shadow as it.
        drawGroundShadow(
            canvas,
            if (isFireTruck) FIRE_TRUCK_SHADOW_HALF_LENGTH_UNITS else shell.shadowHalfLengthUnits,
            4f,
        )
        canvas.restore()

        // The fire truck has its own body. Every other type shares the low-sedan silhouette and
        // differs only by colour and an accessory sprite; the fire truck did too, which is why it
        // read as a red car with a ladder floating above it rather than as a fire engine.
        if (isFireTruck) {
            drawFireTruck(canvas, nightGlow)
        } else {
            val bodyColor = when (c.spec.type) {
                CarType.POLICE -> 0xFFF0F0F2.toInt()
                CarType.TAXI -> 0xFFFFC61A.toInt()
                else -> customization.colorFor(c.spec, dayBlend)
            }
            // One of the three v4.19 bodies, with its glass. Both come from [shell], so a car
            // cannot end up wearing one body's shell and another's glazing.
            drawTintedSprite(canvas, shell.bodyRes, shell.bodyXUnits, shell.bodyYUnits, bodyColor)
            drawSprite(canvas, shell.glassRes, shell.glassXUnits, CAR_GLASS_ORIGIN_Y_UNITS)

            // The seat back between the two occupants, drawn on the glass and under both of
            // them. See [CAR_SEAT_BACK_X_UNITS] for why it starts below the chin line.
            if (c.spec.type.seatsTwo) {
                fillPaint.color = CAR_SEAT_BACK_COLOUR
                // A rect with a disc for its crown: `SceneCanvas` carries no round-rect, and the
                // two primitives it does carry are what every other rounded shape here is built
                // from.
                canvas.drawCircle(
                    CAR_SEAT_BACK_X_UNITS, CAR_SEAT_BACK_TOP_Y_UNITS + CAR_SEAT_BACK_HALF_WIDTH_UNITS,
                    CAR_SEAT_BACK_HALF_WIDTH_UNITS, fillPaint,
                )
                canvas.drawRect(
                    CAR_SEAT_BACK_X_UNITS - CAR_SEAT_BACK_HALF_WIDTH_UNITS,
                    CAR_SEAT_BACK_TOP_Y_UNITS + CAR_SEAT_BACK_HALF_WIDTH_UNITS,
                    CAR_SEAT_BACK_X_UNITS + CAR_SEAT_BACK_HALF_WIDTH_UNITS,
                    CAR_SILL_Y_UNITS, fillPaint,
                )
            }

            when (c.spec.type) {
                CarType.POLICE -> {
                    // The livery stripe was blitted at (-70,27): 20 units clear of the body's own
                    // left edge and below its floor, so it drew as a loose bar lying on the road
                    // under the car rather than as a stripe along its side -- and left the white
                    // car itself completely unmarked. It runs along the doors now.
                    drawSprite(canvas, R.drawable.police_stripe, CAR_LIVERY_X_UNITS, CAR_SILL_Y_UNITS)
                    drawSprite(
                        canvas, R.drawable.police_lightbar,
                        POLICE_LIGHTBAR_X_UNITS, POLICE_LIGHTBAR_Y_UNITS,
                    )
                    val lit = litVehicleAlpha(nightGlow)
                    if (lit > 0) {
                        fillPaint.alpha = lit
                        fillPaint.color = BEACON_RED_LIT
                        canvas.drawRect(
                            POLICE_LAMP_RED_LEFT_X, POLICE_LAMP_TOP_Y_UNITS,
                            POLICE_LAMP_RED_RIGHT_X, POLICE_LAMP_BOTTOM_Y_UNITS, fillPaint,
                        )
                        fillPaint.color = BEACON_BLUE_LIT
                        canvas.drawRect(
                            POLICE_LAMP_BLUE_LEFT_X, POLICE_LAMP_TOP_Y_UNITS,
                            POLICE_LAMP_BLUE_RIGHT_X, POLICE_LAMP_BOTTOM_Y_UNITS, fillPaint,
                        )
                        fillPaint.alpha = 255
                    }
                }
                // Same defect as the police stripe, one unit less obvious: the chequer straddled
                // the body's floor and the wheels instead of banding the doors.
                CarType.TAXI -> {
                    drawSprite(canvas, R.drawable.taxi_checker, CAR_LIVERY_X_UNITS, CAR_SILL_Y_UNITS)
                    // The roof sign is what tells a yellow car from a yellow car. It stands on the
                    // same roof the police light bar does and is centred on it the same way.
                    drawSprite(canvas, R.drawable.taxi_sign, TAXI_SIGN_X_UNITS, TAXI_SIGN_Y_UNITS)
                    val lit = litVehicleAlpha(nightGlow)
                    if (lit > 0) {
                        // The "for hire" light, which is the one part of a taxi that is meant to be
                        // seen from down the street after dark.
                        fillPaint.color = TAXI_SIGN_LIT
                        fillPaint.alpha = lit
                        canvas.drawRect(
                            TAXI_SIGN_BOX_LEFT_X, TAXI_SIGN_BOX_TOP_Y,
                            TAXI_SIGN_BOX_RIGHT_X, TAXI_SIGN_BOX_BOTTOM_Y, fillPaint,
                        )
                        fillPaint.alpha = 255
                    }
                }
                else -> {}
            }
            // The lamps at both ends, at this body's own seats. v4.18 had one full-car-width
            // overlay per state, almost entirely transparent and valid for one shell only; the
            // fleet now shares four small lenses and each body says where they land.
            drawVehicleLamps(
                canvas, nightGlow,
                shell.lampFrontXUnits, shell.lampFrontYUnits,
                shell.lampRearXUnits, shell.lampRearYUnits,
            )
        }

        // The occupants: frontal busts in the pedestrians' own style, sized off the height
        // table, seated in the glasshouse.
        //
        // rc2 rebuilt the sizing (the height table, [CAR_OCCUPANT_SCALE]) and rc4 rebuilt the
        // face: the maintainer chose one human language for the whole scene, so a person in a
        // car is the same frontal bust a person on the pavement is, seatbelt on the chest, from
        // the same family/season/skin axes ([personCarHeadSkinDrawables]).
        //
        // **v4.19: children ride.** The driver is always an adult; the passenger is any of the
        // four families, boy and girl included. v4.18 could not seat a child -- a child's bust
        // is wider across the shoulders and scarf than an adult's (19.5 and 21.6 units against
        // 18.4), which dropped the pillar light to 11-15% on the one cabin that shell could
        // hold, and item 4 of `BACKLOG_v4_19.md` recorded it as a decision rather than a defect.
        // The three v4.19 cabins were drawn around the widest of them instead, and the seat
        // pitch was chosen on the winter girl rather than on the adults: see [CAR_HEAD_X_UNITS]
        // for the twelve measured combinations. So the 16 child busts, 1.136 MiB that had never
        // been decoded in any release, are content now.
        //
        // **The passenger is drawn before the driver**, because the person in front is the one
        // who occludes: the two heads overlap across the hair at this seat spacing, and drawing
        // them the other way round would put the rear passenger in front of the driver.
        //
        // Drawn AFTER the livery deliberately: the stripe and the chequer band the doors below
        // the sill, the busts stand on the sill inside the glass, and `VehicleDrawOrderTest`
        // asserts the sequence so a reorder cannot quietly put paint over a person again.
        //
        // Seeded from the loop offset rather than the speed: speed is a property of the lane, so
        // every car in a lane shares it and a speed-derived seed would give them all the same
        // driver. The offset is unique per candidate. Skin draws from its own seed channel, the
        // same three-tone table the pedestrians rotate through, and the passenger draws from
        // channels of its own so a car is not two copies of one person -- the last line forces
        // the tone apart in the one case where both channels land on the driver.
        val driverSeed = kotlin.math.abs((c.spec.laneYFraction * 7919f + c.spec.startDelaySeconds * 131f).toInt())
        val driverKindIdx = driverSeed % 2 // the driver is always an adult: man or woman
        val driverSkinIdx = driverSeed / 11 % 3
        val seasonIdx = seasonIndexFor(Exposure.OUTDOORS)
        val occupantScale = if (isFireTruck) FIRE_TRUCK_OCCUPANT_SCALE else CAR_OCCUPANT_SCALE
        if (isFireTruck) {
            drawSeatedOccupant(
                canvas, FIRE_TRUCK_HEAD_X_UNITS, FIRE_TRUCK_HEAD_Y_UNITS, occupantScale,
                personCarHeadSkinDrawables[driverKindIdx][seasonIdx][driverSkinIdx],
            )
        } else {
            if (c.spec.type.seatsTwo) {
                // **The passenger is never the driver's own family**, which is a rule rather than
                // a roll of the seed. In this artwork a family carries its hairstyle *and* its
                // clothing -- every woman bust is the red top with the yellow band, every man
                // bust the blue one -- so two same-family occupants would be identical in family,
                // hair and clothing whatever the skin tone did, and the car would read as one
                // person drawn twice.
                //
                // v4.18 could only alternate the two adults, so every car carried exactly one man
                // and one woman. With children seated the choice is over the other three families
                // instead: the pairing is still never a duplicate, and the traffic loses the last
                // uniformity it had. A clothing-colour axis is still the fuller answer and is
                // still in the backlog with its arithmetic; this pass did not have the memory for
                // it (0.141 MiB free after the three bodies).
                //
                // The tone draws from its own channel on both seats, so cars differ from each
                // other as well as within themselves.
                val passengerKindIdx = (driverKindIdx + 1 + driverSeed / 7 % 3) % 4
                val passengerSkinIdx = driverSeed / 3 % 3
                drawSeatedOccupant(
                    canvas, CAR_PASSENGER_X_UNITS, CAR_PASSENGER_Y_UNITS, occupantScale,
                    personCarHeadSkinDrawables[passengerKindIdx][seasonIdx][passengerSkinIdx],
                )
            }
            drawSeatedOccupant(
                canvas, CAR_HEAD_X_UNITS, CAR_HEAD_Y_UNITS, occupantScale,
                personCarHeadSkinDrawables[driverKindIdx][seasonIdx][driverSkinIdx],
            )
        }

        // Wheels: dark tire with a lighter gray hub ring -- a plain 2-tone treatment with no
        // separate hubcap disc, which is all the paper-cutout look wants. The treatment is shared
        // by every vehicle in the fleet; where the wheels stand is not, because each body has its
        // own wheelbase and cuts its own arches around them. The estate's are asymmetric (-42 and
        // +38), which is what a long load bay behind a short rear overhang looks like.
        val wheelFrontX = if (isFireTruck) -FIRE_TRUCK_WHEEL_X_UNITS else shell.wheelFrontXUnits
        val wheelRearX = if (isFireTruck) FIRE_TRUCK_WHEEL_X_UNITS else shell.wheelRearXUnits
        val wheelRadius = if (isFireTruck) FIRE_TRUCK_WHEEL_RADIUS_UNITS else CAR_WHEEL_RADIUS_UNITS
        val wheelY = VEHICLE_GROUND_Y_UNITS - wheelRadius
        val hubRadius = wheelRadius * WHEEL_HUB_RATIO
        if (isFireTruck) {
            // The rear pair's inner wheel, under the outer one. See FIRE_TRUCK_INNER_WHEEL_X_UNITS.
            fillPaint.color = 0xFF2B2B2B.toInt()
            canvas.drawCircle(FIRE_TRUCK_INNER_WHEEL_X_UNITS, wheelY, wheelRadius, fillPaint)
            strokePaint.strokeWidth = 3f
            strokePaint.color = 0xFF8A8A8A.toInt()
            canvas.drawCircle(FIRE_TRUCK_INNER_WHEEL_X_UNITS, wheelY, hubRadius, strokePaint)
        }
        fillPaint.color = 0xFF2B2B2B.toInt()
        canvas.drawCircle(wheelFrontX, wheelY, wheelRadius, fillPaint)
        canvas.drawCircle(wheelRearX, wheelY, wheelRadius, fillPaint)
        strokePaint.strokeWidth = 3f
        strokePaint.color = 0xFF8A8A8A.toInt()
        canvas.drawCircle(wheelFrontX, wheelY, hubRadius, strokePaint)
        canvas.drawCircle(wheelRearX, wheelY, hubRadius, strokePaint)
        strokePaint.strokeWidth = 2.5f

        canvas.restore()
    }
}
