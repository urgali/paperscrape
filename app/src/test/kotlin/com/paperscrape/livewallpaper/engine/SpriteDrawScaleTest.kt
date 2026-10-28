package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How much bigger a sprite is in its file than it ever is on the screen -- measured for every
 * shipped PNG, on the paths that actually blit it.
 *
 * ### The question this answers
 *
 * `SpriteBlitter.SPRITE_PIXELS_PER_UNIT` is an oversample: artwork is authored at 3 px per local
 * unit and divided back down at the blit, so the downscale gives clean antialiased edges. Lowering
 * it to 2 would take the whole decoded set to four ninths of its size -- more than any margin
 * `SpriteGeometryTest`'s ceiling is argued over -- and it costs nothing visible *as long as every
 * sprite still arrives on screen smaller than its own file*. That is a measurement, not a
 * judgement, and this test takes it.
 *
 * **Headroom** here is the file's pixels over the screen's: `authored px / drawn px` at the largest
 * size the sprite is ever drawn. Headroom 3 means the file has three pixels for every pixel the
 * screen shows. Headroom 1 means the blit is already 1:1 and there is nothing spare. Headroom below
 * 1 means the sprite is *magnified* today. Dropping the grid from 3 to 2 multiplies every
 * `SCENE_UNITS` sprite's drawn-to-authored ratio by 1.5, so it multiplies headroom by 2/3: a family
 * needs headroom of at least **1.5** to survive the change at 1:1, and more than that to keep any
 * margin.
 *
 * ### Why it is computed rather than rendered
 *
 * `PaperRenderer` allocates `android.graphics.Paint` in its field initialisers, and this module
 * deliberately does not enable `isReturnDefaultValues` (`app/build.gradle.kts`), so the renderer
 * cannot be driven on the JVM at all. What *can* be driven is the arithmetic: every scale in the
 * draw paths below is built out of the same constants the renderer multiplies, read from
 * `SceneSpace`, `SceneObjectRenderer` and `PaperRenderer` rather than copied as numbers, so a
 * retuned constant moves this measurement with it. The gallery preview is the exception and is
 * better than that: [ThemePreviewScenes] is a plain data description with no `Canvas` in it, so the
 * preview's own scales are read from the real scenes, per theme, not modelled.
 *
 * ### What it deliberately does not do
 *
 * It measures the **largest** size each sprite reaches, because that is the one the artwork has to
 * survive. A sprite is drawn smaller than this most of the time -- that is what
 * [SpriteDetailLevel] exists for -- and the smaller instances are not the constraint.
 */
class SpriteDrawScaleTest {

    // ---------------------------------------------------------------- viewports

    /**
     * The screens the measurement is taken on.
     *
     * Headroom is **not** device-independent: `SceneSpace.sceneScale` is `screenHeight / 2400`, so
     * a taller screen draws every scene object proportionally larger against the same artwork.
     * That makes the reference device the load-bearing choice, and this project has one --
     * `ShopFrontVisibilityTest` states 1080x2340 as "where every visual judgement in this project
     * is made". The BV6600 is the bench the photographs are taken on and is *kinder* than the
     * reference, which is exactly why it cannot be the one a decision is made against; the 1440p
     * column is there because phones that shape ship today and a decision taken at 2340 should
     * know what it does at 3200.
     */
    private data class Viewport(val label: String, val widthPx: Float, val heightPx: Float)

    private val reference = Viewport("reference 1080x2340", 1080f, 2340f)
    private val bench = Viewport("BV6600 720x1440", 720f, 1440f)
    private val tall = Viewport("1440x3200", 1440f, 3200f)

    private val viewports = listOf(bench, reference, tall)

    // ---------------------------------------------------------------- the draw paths

    /**
     * One blit path: the largest canvas scale it ever applies, and which convention the sprite is
     * authored in.
     *
     * For [SpriteScale.SCENE_UNITS] the blit divides by `SPRITE_PIXELS_PER_UNIT`, so drawn pixels
     * per authored pixel is `canvasScale / SPRITE_PIXELS_PER_UNIT`. For
     * [SpriteScale.CANVAS_PIXELS] the bitmap goes straight through and the canvas scale *is* that
     * ratio.
     */
    private class DrawPath(
        val label: String,
        val convention: SpriteScale,
        val maxCanvasScale: (Viewport) -> Float,
    ) {
        /** Authored px per drawn px at the largest size this path reaches. */
        fun headroom(v: Viewport): Float {
            val drawnPerAuthored = when (convention) {
                SpriteScale.SCENE_UNITS -> maxCanvasScale(v) / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
                SpriteScale.CANVAS_PIXELS -> maxCanvasScale(v)
            }
            return 1f / drawnPerAuthored
        }
    }

    /**
     * The per-candidate size variation a generated object carries.
     *
     * `SceneObject.kt` rolls `1 - spread/2 .. 1 + spread/2`, so the largest a static object is ever
     * generated at is `1 + SIZE_VARIATION_SPREAD / 2`. A theme imported from a file can hold any
     * finite value here (`CustomThemeData` reads it with `optFinite` and does not clamp it), which
     * is a real hole but not one the shipped artwork can be sized against; the generator's own
     * ceiling is what the set is drawn for.
     */
    private val maxSizeVariation = 1f + SceneSpace.SIZE_VARIATION_SPREAD / 2f

    /** The deepest, and therefore largest, ground line a static object can stand on. */
    private val maxStaticDepthScale = SceneSpace.depthScale(1f)

    /** The nearer of the two pavements: pedestrians are drawn largest there. */
    private val maxPavementScale = SceneSpace.perspectiveScaleAt(SceneSpace.PAVEMENT_NEAR_Y_FRACTION)

    /** The near lane: vehicles are drawn largest there. */
    private val maxLaneScale = SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION)

    /** `drawGroundFlowers`/`scatterPiles` stand their scatter anywhere in this depth band. */
    private val maxScatterDepthScale = SceneSpace.depthScale(SceneObjectRenderer.FLOWER_DEPTH_MAX)

    private fun staticPath(label: String, variant: SceneSpace.SceneVariant, inner: Float = 1f) =
        DrawPath(label, SpriteScale.SCENE_UNITS) { v ->
            variant.baseScale * maxSizeVariation * maxStaticDepthScale *
                SceneSpace.sceneScale(v.heightPx) * inner
        }

    /** The sun's and the moon's discs: `radius / 120`, with `radius = screenWidth * 0.11`. */
    private fun celestialScale(v: Viewport): Float =
        v.widthPx * PaperRenderer.CELESTIAL_RADIUS_FRACTION * 2f / 120f

    private fun personScale(v: Viewport): Float =
        SceneSpace.PERSON_BASE_SCALE * maxPavementScale * SceneSpace.sceneScale(v.heightPx)

    private fun carScale(v: Viewport): Float =
        SceneSpace.CAR_BASE_SCALE * maxLaneScale * SceneSpace.sceneScale(v.heightPx)

    private fun fireTruckScale(v: Viewport): Float =
        SceneSpace.FIRE_TRUCK_BASE_SCALE * maxLaneScale * SceneSpace.sceneScale(v.heightPx)

    private val paths: Map<String, DrawPath> = buildMap {
        fun put(path: DrawPath, vararg sprites: String) {
            for (s in sprites) put(s, path)
        }

        // -- static scene objects, all through SceneObjectRenderer.effectiveScaleFor ------------

        // -- the neighbourhood --------------------------------------------------------------
        //
        // A building's pieces are blitted inside the composer's own `canvas.scale(k, k)`, with
        // `k = variant.spriteUnitsTall / family.unitsTall` -- the factor that draws a piece
        // authored in the family's units at the height the variant declares. That is exactly what
        // `inner` is for, and it is read from the table rather than typed, so a family whose
        // reference height is edited cannot leave a stale number here.
        fun neighbourhood(label: String, variant: SceneSpace.SceneVariant, vararg sprites: String) {
            val family = NeighbourhoodTable.FAMILIES.getValue(variant)
            put(
                staticPath(label, variant, inner = variant.spriteUnitsTall / family.unitsTall),
                *sprites,
            )
        }

        neighbourhood(
            "small house", SceneSpace.SceneVariant.HOUSE_SMALL,
            "house_small_ground_fx", "house_small_ground_mg", "house_small_ground_mw",
            "house_small_roof_gable_fx", "house_small_roof_gable_mw",
            "house_small_roof_gable_snow_fx", "house_small_roof_mansard_fx",
            "house_small_roof_mansard_mg", "house_small_roof_mansard_mw",
            "house_small_roof_mansard_snow_fx", "house_small_storey_fx",
            "house_small_storey_mg", "house_small_storey_mw",
        )
        neighbourhood(
            "large house", SceneSpace.SceneVariant.HOUSE_LARGE,
            "house_large_ground_fx", "house_large_ground_mg", "house_large_ground_mw",
            "house_large_roof_gable_fx", "house_large_roof_gable_mg",
            "house_large_roof_gable_mw", "house_large_roof_gable_snow_fx",
            "house_large_roof_mansard_fx", "house_large_roof_mansard_mg",
            "house_large_roof_mansard_mw", "house_large_roof_mansard_snow_fx",
            "house_large_roof_turret_gable_fx", "house_large_roof_turret_gable_mw",
            "house_large_roof_turret_gable_snow_fx", "house_large_roof_turret_tower_fx",
            "house_large_roof_turret_tower_mg", "house_large_roof_turret_tower_mw",
            "house_large_roof_turret_tower_snow_fx", "house_large_storey_fx",
            "house_large_storey_mg", "house_large_storey_mw",
        )
        put(
            staticPath("tree", SceneSpace.SceneVariant.TREE),
            "tree_trunk", "tree_canopy", "tree_canopy_snowcap", "tree_dead_branches",
            "tree_fir", "tree_fir_snow", "tree_fir_star",
        )
        // The white disc of `SpriteBlitter.drawDisc`, drawn wherever a dot is -- the fir's ten lights and every
        // tree's and palm's at the tree lights' radius (inside the TREE's or the PALM_TREE's scale), the roof strings at a
        // building's piece metre (half as large again on a tower), the sill strings at it, every falling snowflake at
        // `SNOW_DIAMETER_MAX_METRES` of the viewport's metre, and every point star at most `STAR_POINT_RADIUS_SCALE` of
        // the largest star -- so its largest draw is the largest of those diameters. The distant houses' are
        // `PaperRenderer.DISTANT_BULB_PX`, smaller than any. A disc is drawn by its diameter, not by a canvas scale: the "scale" here is the diameter over
        // the disc's own units, which makes the headroom the disc's texels over the diameter's pixels.
        put(
            DrawPath("disc: bulbs, snow, stars", SpriteScale.SCENE_UNITS) { v ->
                val trees = listOf(SceneSpace.SceneVariant.TREE, SceneSpace.SceneVariant.PALM_TREE)
                    .map { 2f * SceneObjectRenderer.CHRISTMAS_LIGHT_RADIUS_UNITS * staticPath("", it).maxCanvasScale(v) }
                val roofs = NeighbourhoodTable.FAMILIES.map { (variant, family) ->
                    val k = if (variant == SceneSpace.SceneVariant.TOWER) SceneObjectRenderer.TOWER_DECORATION_SCALE else 1f
                    2f * SceneObjectRenderer.CHRISTMAS_LIGHT_BULB_RADIUS_UNITS * k *
                        staticPath("", variant, inner = variant.spriteUnitsTall / family.unitsTall).maxCanvasScale(v)
                }
                val snow = PaperRenderer.SNOW_DIAMETER_MAX_METRES * SceneSpace.pixelsPerMetre(v.heightPx)
                val star = 2f * PaperRenderer.MAX_STAR_RADIUS_PX * PaperRenderer.STAR_POINT_RADIUS_SCALE
                maxOf(trees.max(), roofs.max(), snow, star) / discUnits
            },
            "deco_bulb",
        )
        // The wreath, on a front door at a building's piece metre, half as large again on a tower.
        put(
            DrawPath("Christmas wreath", SpriteScale.SCENE_UNITS) { v ->
                NeighbourhoodTable.FAMILIES.maxOf { (variant, family) ->
                    val k = if (variant == SceneSpace.SceneVariant.TOWER) SceneObjectRenderer.TOWER_DECORATION_SCALE else 1f
                    staticPath("", variant, inner = variant.spriteUnitsTall / family.unitsTall * k).maxCanvasScale(v)
                }
            },
            "deco_wreath",
        )
        // The Halloween decorations, on a house's or a shop's piece units as the wreath, never on a tower.
        put(
            DrawPath("Halloween decorations", SpriteScale.SCENE_UNITS) { v ->
                NeighbourhoodTable.FAMILIES.filter { (variant, _) -> variant != SceneSpace.SceneVariant.TOWER }.maxOf { (variant, family) ->
                    staticPath("", variant, inner = variant.spriteUnitsTall / family.unitsTall).maxCanvasScale(v)
                }
            },
            "deco_cobweb", "deco_jack_o_lantern", "deco_jack_o_lantern_light",
        )
        put(
            staticPath("palm", SceneSpace.SceneVariant.PALM_TREE),
            "palmtree_trunk", "palmtree_fronds", "palmtree_fronds_dead", "palmtree_fronds_frost",
        )
        neighbourhood(
            "tower", SceneSpace.SceneVariant.TOWER,
            "tower_bay_fx", "tower_bay_mg", "tower_crown_dome_fx", "tower_crown_dome_mw",
            "tower_crown_dome_snow_fx", "tower_crown_spire_fx", "tower_crown_spire_mw",
            "tower_crown_spire_snow_fx", "tower_row_tier1_fx", "tower_row_tier1_mg",
            "tower_row_tier2_fx", "tower_row_tier2_mg", "tower_row_tier3_fx",
            "tower_row_tier3_mg", "tower_snow_left1_fx", "tower_snow_left2_fx",
            "tower_snow_right1_fx", "tower_snow_right2_fx", "tower_snow_top_fx",
            "tower_tier1_fx", "tower_tier1_mg", "tower_tier1_mw", "tower_tier2_fx",
            "tower_tier2_mw", "tower_tier3_fx", "tower_tier3_mw",
            // The short and the tall bodies, the hall's two windows by the door, and the short
            // body's wide snow cap.
            "tower_bay_door_fx", "tower_bay_door_mg", "tower_snow_top_wide_fx",
            "tower_tier1_short_fx", "tower_tier1_short_mg", "tower_tier1_short_mw",
            "tower_tier1_tall_fx", "tower_tier1_tall_mg", "tower_tier1_tall_mw",
            "tower_tier2_tall_fx", "tower_tier2_tall_mw",
        )
        // -- the distant houses on the mountains ----------------------------------------------
        //
        // Each drawing is scaled to `SceneSpace.distantHousePixelsTall` over its own height, and on
        // a mountain that is the floor's size: the mountains stand above
        // `SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION`, where the projection gives less
        // (`DistantHousesTest`). One path per drawing, because each has its own height.
        for ((index, stem) in listOf("house_distant_cottage", "house_distant_chalet", "house_distant_tall").withIndex()) {
            val piece = NeighbourhoodTable.DISTANT_HOUSES[index]
            put(
                DrawPath("distant house", SpriteScale.SCENE_UNITS) { v ->
                    SceneSpace.distantHousePixelsTall(SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION, v.heightPx) / piece.height
                },
                "${stem}_fx", "${stem}_mg", "${stem}_mw", "${stem}_snow_fx",
            )
        }
        neighbourhood(
            "restaurant", SceneSpace.SceneVariant.RESTAURANT,
            "restaurant_pavilion_fx", "restaurant_pavilion_mg", "restaurant_pavilion_mw",
            "restaurant_pavilion_snow_fx",
        )
        // `tower_row_tier3_*` is drawn by the school too and is declared once, under the tower:
        // the two families are drawn at the same metres per unit, `metresTall / unitsTall`, to
        // within 0.02 %, so which of the two owns the declaration cannot change the level it
        // resolves to.
        neighbourhood(
            "school", SceneSpace.SceneVariant.SCHOOL,
            "school_fx", "school_mg", "school_mw", "school_snow_fx",
        )
        neighbourhood(
            "bar", SceneSpace.SceneVariant.BAR,
            "bar_chamfer_fx", "bar_chamfer_mg", "bar_chamfer_mw", "bar_chamfer_snow_fx",
            "bar_signboard_fx", "bar_signboard_mg", "bar_signboard_mw",
            "bar_signboard_snow_fx",
        )
        // The shop and the two stations. Each of the six buildings among the houses can stand in
        // each of the places, drawn at that place's metre ([SceneObjectRenderer]'s
        // `variant.spriteUnitsTall / placeFamily.unitsTall`) -- one metre a unit for every piece, so the
        // same px per unit in all three rows of the size table -- and [staticPath] takes the nearest depth
        // any object stands at: the place a path is declared under, here the bar's, cannot change the
        // level it resolves to.
        neighbourhood(
            "shop, fire station, police station", SceneSpace.SceneVariant.BAR,
            "shop_fx", "shop_mg", "shop_mw", "shop_snow_fx",
            "fire_station_fx", "fire_station_mg", "fire_station_mw", "fire_station_snow_fx",
            "fire_station_tower_fx", "fire_station_tower_mg", "fire_station_tower_mw", "fire_station_tower_snow_fx",
            "police_station_fx", "police_station_mg", "police_station_mw", "police_station_snow_fx",
        )
        put(
            staticPath("snowman", SceneSpace.SceneVariant.SNOWMAN),
            "snowman_body", "snowman_nose", "snowman_scarf",
        )
        // The scarecrow, in a snowman's place at its own row of the size table.
        put(
            staticPath("scarecrow", SceneSpace.SceneVariant.SCARECROW),
            "scarecrow", "scarecrow_light",
        )
        put(
            staticPath("penguin", SceneSpace.SceneVariant.PENGUIN),
            "penguin_body", "penguin_beak", "penguin_belly", "penguin_feet",
        )
        put(
            staticPath("bunny", SceneSpace.SceneVariant.BUNNY),
            "bunny_body", "bunny_innerear", "bunny_tail",
        )
        put(
            staticPath("easter egg", SceneSpace.SceneVariant.EASTER_EGG),
            "easteregg_shell", "easteregg_pattern",
        )
        put(
            staticPath("pumpkin", SceneSpace.SceneVariant.PUMPKIN),
            "pumpkin_body", "pumpkin_face", "pumpkin_stem",
        )
        // A gift is drawn twice: as its own ground object at the GIFT variant's scale, and at the
        // foot of a Christmas fir inside the TREE's, at the largest of SceneObjectRenderer.FIR_GIFT_SCALES.
        // The larger of the two is the one measured.
        put(
            DrawPath("gift", SpriteScale.SCENE_UNITS) { v ->
                maxOf(
                    staticPath("", SceneSpace.SceneVariant.GIFT).maxCanvasScale(v),
                    staticPath("", SceneSpace.SceneVariant.TREE, inner = SceneObjectRenderer.FIR_GIFT_SCALES.max())
                        .maxCanvasScale(v),
                )
            },
            "gift_box", "gift_ribbon",
        )

        // -- the ground scatter ------------------------------------------------------------------

        put(
            DrawPath("ground flowers", SpriteScale.SCENE_UNITS) { v ->
                SceneSpace.scaleForHeight(
                    SceneObjectRenderer.FLOWER_METRES_TALL,
                    SceneObjectRenderer.FLOWER_SPRITE_UNITS_TALL,
                ) * maxScatterDepthScale * SceneSpace.sceneScale(v.heightPx)
            },
            // Both clumps: one canvas, one origin, one scale path -- which is what lets the
            // renderer swap them on the seasonal palette without touching any of the arithmetic.
            "ground_flowers_bloom", "ground_flowers_dry",
        )
        put(
            DrawPath("snow drift / leaf heap", SpriteScale.SCENE_UNITS) { v ->
                SceneSpace.scaleForHeight(
                    SceneObjectRenderer.PILE_METRES_TALL,
                    SceneObjectRenderer.PILE_SPRITE_UNITS_TALL,
                ) * maxScatterDepthScale * SceneSpace.sceneScale(v.heightPx)
            },
            "snow_pile", "leaf_pile",
        )

        // -- the road ----------------------------------------------------------------------------

        put(
            DrawPath("car", SpriteScale.SCENE_UNITS, ::carScale),
            "car_body_saloon", "car_body_estate", "car_body_compact",
            "car_window_saloon", "car_window_estate", "car_window_compact",
            "car_lamp_front", "car_lamp_front_lit", "car_lamp_rear", "car_lamp_rear_lit",
            "taxi_checker", "taxi_sign", "police_lightbar", "police_stripe",
        )
        put(
            DrawPath("fire engine", SpriteScale.SCENE_UNITS, ::fireTruckScale),
            "firetruck_body", "firetruck_ladder",
        )

        // -- people ------------------------------------------------------------------------------
        //
        // The umbrella is blitted inside the walker's own transform with no scale of its own, so it
        // shares the pedestrian path exactly.

        put(DrawPath("pedestrian", SpriteScale.SCENE_UNITS, ::personScale), "umbrella_canopy")

        // -- the lake ----------------------------------------------------------------------------

        put(
            DrawPath("sailboat", SpriteScale.SCENE_UNITS) { v ->
                SceneSpace.SAILBOAT_BASE_SCALE * SceneSpace.sceneScale(v.heightPx)
            },
            "sailboat_hull", "sailboat_sail",
        )
        put(
            DrawPath("dolphin and its splash", SpriteScale.SCENE_UNITS) { v ->
                SceneSpace.DOLPHIN_BASE_SCALE * SceneSpace.sceneScale(v.heightPx)
            },
            "dolphin_body", "water_splash0", "water_splash1",
        )
        // `waveScale = waveBase * (0.85 + 0.3 * waveLane / waveLaneMax)`, so the nearest lane in
        // the pool is 1.15 of the base.
        put(
            DrawPath("wave", SpriteScale.SCENE_UNITS) { v ->
                PaperRenderer.WAVE_METRES_LONG * SceneSpace.LAKE_PIXELS_PER_METRE /
                    PaperRenderer.WAVE_UNITS_WIDE * SceneSpace.sceneScale(v.heightPx) * 1.15f
            },
            "wave_tube_body", "wave_tube_crest",
        )

        // -- the sky and the weather -------------------------------------------------------------

        put(
            DrawPath("sun and moon discs", SpriteScale.CANVAS_PIXELS, ::celestialScale),
            "sun_body", "sun_glow", "moon_full", "moon_crescent", "moon_half", "moon_gibbous",
            "moon_jack_o_lantern", "moon_jack_o_lantern_light",
            // The laughing face and its light, in the carved face's place, at its scale.
            "moon_jack_o_lantern_laugh", "moon_jack_o_lantern_laugh_light",
        )
        // One bird is blitted at `scale(1, +-1)` inside a bare translate: its own pixels, 1:1.
        put(DrawPath("bird", SpriteScale.CANVAS_PIXELS) { 1f }, "bird_body")
        put(
            DrawPath("star sparkle", SpriteScale.SCENE_UNITS) { _ ->
                PaperRenderer.MAX_STAR_RADIUS_PX / PaperRenderer.STAR_SPRITE_RADIUS_DIVISOR
            },
            "star_sparkle",
        )
        put(
            DrawPath("cloud", SpriteScale.SCENE_UNITS) { _ -> MAX_CLOUD_SCALE },
            "cloud_body",
        )
        put(
            DrawPath("rainbow", SpriteScale.SCENE_UNITS) { v ->
                v.widthPx * RAINBOW_MAX_RADIUS_FRACTION /
                    PaperRenderer.RAINBOW_SPRITE_HALF_WIDTH_UNITS
            },
            "rainbow_arc",
        )
        put(
            DrawPath("lightning bolt", SpriteScale.SCENE_UNITS) { v ->
                v.heightPx * (
                    PaperRenderer.LIGHTNING_BOLT_MIN_HEIGHT_FRACTION +
                        PaperRenderer.LIGHTNING_BOLT_HEIGHT_SPREAD_FRACTION
                    ) / PaperRenderer.LIGHTNING_BOLT_HEIGHT_UNITS
            },
            "lightning_bolt",
        )
        // A burst reaches scale 1 at the end of its expansion, and the sprite's own half-width maps
        // onto FIREWORK_REACH_UNITS there.
        put(
            DrawPath("firework burst", SpriteScale.SCENE_UNITS) { _ ->
                PaperRenderer.FIREWORK_REACH_UNITS / PaperRenderer.FIREWORK_SPRITE_HALF_UNITS
            },
            "firework",
        )
        // The group in proportion to the people (`SantaSleighScale`), never past its sprites' 3 px a unit.
        put(
            DrawPath("Santa's sleigh", SpriteScale.SCENE_UNITS) { v -> SantaSleighScale.pxPerUnit(v.heightPx) },
            "santa_sleigh", "santa_reindeer_0", "santa_reindeer_1", "santa_reindeer_2", "santa_reindeer_3",
        )
        // The Headless Horseman, his rider as tall as Santa (`HeadlessHorsemanScale`).
        put(
            DrawPath("Headless Horseman", SpriteScale.SCENE_UNITS) { v -> HeadlessHorsemanScale.pxPerUnit(v.heightPx) },
            "horseman_horse_0", "horseman_horse_1", "horseman_horse_2", "horseman_horse_3",
            "horseman_legs_0", "horseman_legs_1", "horseman_legs_2", "horseman_legs_3",
            "horseman_rider_body", "horseman_rider_arm", "horseman_rider_pumpkin", "horseman_cape",
        )
    }

    private companion object {
        /** `range(0.85, 1.25)` times the largest tier multiplier, `CLOUD_TIER_SIZE_MULTIPLIER[3]`. */
        const val MAX_CLOUD_SCALE = 1.25f * 1.15f

        /**
         * `drawRainbow`'s own `maxRadius = screenWidth * 0.62f`, which is a literal at the call
         * site and has no constant to read. Copied rather than derived: if the arc is resized,
         * this is where the measurement rots.
         */
        const val RAINBOW_MAX_RADIUS_FRACTION = 0.62f

        /** Headroom a family needs to survive a grid of 2 without being magnified. */
        const val GRID_TWO_BREAK_EVEN = 1.5f
    }

    // ---------------------------------------------------------------- the person families

    /**
     * The walk, carry and head sprites, each on the transform that actually blits it.
     *
     * Resolved by name rather than listed: the person variants are most of the shipped PNGs (count the `person_`
     * files of `drawable-nodpi` against all its PNGs), and a hand-written list of them would be a second place for
     * the skin/season axes to be declared.
     */
    private fun personPathFor(name: String): DrawPath? = when {
        name.contains("_head_window") -> DrawPath("bust behind a window", SpriteScale.SCENE_UNITS) { v ->
            SceneSpace.SceneVariant.HOUSE_LARGE.baseScale * maxSizeVariation * maxStaticDepthScale *
                SceneSpace.sceneScale(v.heightPx) *
                (SceneObjectRenderer.OCCUPANT_BOX_UNITS * 0.85f /
                    SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS)
        }
        name.contains("_head_car") -> DrawPath("bust in a car", SpriteScale.SCENE_UNITS) { v ->
            maxOf(
                carScale(v) * SceneObjectRenderer.CAR_OCCUPANT_SCALE,
                fireTruckScale(v) * SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE,
            )
        }
        name.startsWith("person_") -> DrawPath("pedestrian", SpriteScale.SCENE_UNITS, ::personScale)
        else -> null
    }

    private fun pathFor(name: String): DrawPath =
        paths[name] ?: personPathFor(name)
            ?: throw AssertionError(
                "$name has no draw path in this test. Every shipped sprite must be measured: add " +
                    "the path it is blitted on, or this measurement silently stops covering the set.",
            )

    // ---------------------------------------------------------------- the preview

    /**
     * The largest scale each sprite reaches in a gallery/settings preview, read from the real
     * scenes.
     *
     * A preview blits at `item.scale * ThemePreviewGeometry.scaleFor(containerWidthPx)`, and the
     * container is at most the screen's own width -- the two full-width call sites
     * (`SettingsScreen.HomeThemePreview`, `WorldSceneScreen.WorldScenePreview`) inset it by 16 dp a
     * side and the gallery's grid cell is narrower still. Measuring at the full width is therefore
     * an upper bound, deliberately: it cannot understate the case this test exists to find, and the
     * inset it ignores is under 10% on any density.
     */
    private fun previewMaxItemScale(): Map<String, Float> {
        val byResId = mutableMapOf<Int, Float>()
        for (theme in ThemeCatalog.ALL) {
            val base = defaultCustomizationFor(theme.id)
            val customizations = listOf(
                base,
                base.copy(winterColorsEnabled = true),
                base.copy(halloweenEnabled = true),
                base.copy(fallColorsEnabled = true),
            )
            for (customization in customizations) {
                for (night in listOf(null, false, true)) {
                    val scene = ThemePreviewScenes.forTheme(theme, customization, night)
                    // `water` too: the boats and the dolphins have their own pass, and a list that
                    // stopped at `ground` would report no preview path for them at all.
                    val items = scene.backdrop + scene.water + scene.items + scene.cars + scene.ground
                    for (item in items) {
                        for (part in item.parts) {
                            val previous = byResId[part.resId] ?: 0f
                            if (item.scale > previous) byResId[part.resId] = item.scale
                        }
                    }
                }
            }
        }
        val names = drawableNamesByResId()
        return byResId.mapNotNull { (resId, scale) -> names[resId]?.let { it to scale } }.toMap()
    }

    /** name -> id for every `R.drawable`, so a preview part can be reported by its file name. */
    private fun drawableNamesByResId(): Map<Int, String> =
        R.drawable::class.java.fields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.getInt(null) to it.name }

    private fun previewPath(itemScale: Float) = DrawPath("gallery preview", SpriteScale.SCENE_UNITS) { v ->
        itemScale * ThemePreviewGeometry.scaleFor(v.widthPx)
    }

    // ---------------------------------------------------------------- the measurement

    @Test
    fun `every shipped sprite is measured, and the headroom the grid would need is reported`() {
        val sprites = spriteNames()
        assertTrue("no sprites found -- the test is not looking where it thinks", sprites.size > 100)

        val previewScales = previewMaxItemScale()

        for (viewport in viewports) {
            val rows = sprites.map { name ->
                val scenePath = pathFor(name)
                val sceneHeadroom = scenePath.headroom(viewport)
                val previewItemScale = previewScales[name]
                val previewHeadroom = previewItemScale?.let { previewPath(it).headroom(viewport) }
                val worst = if (previewHeadroom != null && previewHeadroom < sceneHeadroom) {
                    Triple(name, previewHeadroom, "gallery preview")
                } else {
                    Triple(name, sceneHeadroom, scenePath.label)
                }
                worst
            }.sortedBy { it.second }

            println("== headroom at ${viewport.label} (authored px per drawn px) ==")
            println("   by draw path, worst member first:")
            rows.groupBy { it.third }
                .mapValues { (_, members) -> members.minOf { it.second } to members.size }
                .entries.sortedBy { it.value.first }
                .forEach { (label, v) ->
                    val (worst, count) = v
                    println("   %-26s %6.3f   (%d sprites)".format(label, worst, count))
                }
            val (worstName, worstHeadroom, worstLabel) = rows.first()
            println(
                "   minimum over the whole set: %.3f (%s, %s)"
                    .format(worstHeadroom, worstName, worstLabel),
            )
            for (threshold in listOf(1f, GRID_TWO_BREAK_EVEN, 2f, 3f)) {
                println(
                    "   under %.1f: %d of %d".format(
                        threshold, rows.count { it.second < threshold }, rows.size,
                    ),
                )
            }
            // What a *selective* reduction would be worth: the decoded set if only the families
            // that still hold `keep` of headroom after the change were redrawn at two thirds of
            // their present linear size, and everything tighter than that left alone.
            val total = sprites.sumOf { decodedBytes(it) }
            for (keep in listOf(1.5f, 2f, 2.5f, 3f)) {
                var moved = 0
                var after = 0L
                for ((name, headroom, _) in rows) {
                    val bytes = decodedBytes(name)
                    if (headroom >= keep) {
                        moved++
                        after += bytes * 4L / 9L
                    } else {
                        after += bytes
                    }
                }
                println(
                    "   redraw only sprites with headroom >= %.2f: %d of %d sprites, set %d B -> %d B (%.1f MiB -> %.1f MiB)"
                        .format(keep, moved, rows.size, total, after, total / 1048576.0, after / 1048576.0),
                )
            }
        }
    }

    private fun uploaded(w: Int, h: Int, level: Int): Long =
        SpriteDetailLevel.reduced(w, level).toLong() * SpriteDetailLevel.reduced(h, level) * 4L

    /**
     * What [name] costs on the GPU at [level], **after the crop `GlTextureCache` takes**.
     *
     * Only the texels that carry ink are uploaded -- the transparent border round a sprite is cut
     * off after the reduction and before the atlas sees it. Modelling that is not optional here:
     * the region masks a person is drawn from cover a few tenths of their canvas each, and a model
     * that charged them for the whole of it would be counting texels the device never allocates.
     *
     * The model is the **authored** alpha box divided down to the level, with one texel of slack on
     * every side for the spread of the filter, so a sprite is charged its ink plus two texels in each
     * axis -- a pad that grows with the perimeter, which an estimate from a content box alone
     * misses. It is deliberately an over-estimate and never an under-estimate: a budget that erred
     * the other way would let a set through and then blow on the device.
     */
    private fun uploadedCropped(name: String, level: Int): Long {
        val (w, h) = pngSize(name)
        val box = contentBox(name) ?: return uploaded(w, h, level)
        val rw = SpriteDetailLevel.reduced(w, level)
        val rh = SpriteDetailLevel.reduced(h, level)
        val left = ((box[0] shr level) - 1).coerceAtLeast(0)
        val top = ((box[1] shr level) - 1).coerceAtLeast(0)
        val right = ((box[2] shr level) + 1).coerceAtMost(rw - 1)
        val bottom = ((box[3] shr level) + 1).coerceAtMost(rh - 1)
        if (right < left || bottom < top) return uploaded(w, h, level)
        return (right - left + 1).toLong() * (bottom - top + 1) * 4L
    }

    /** `[left, top, right, bottom]` of [name]'s non-transparent pixels, or `null` if it has none. */
    private fun contentBox(name: String): IntArray? = contentBoxes.getOrPut(name) {
        val image = ImageIO.read(File(drawableDir, "$name.png"))
        var left = image.width
        var top = image.height
        var right = -1
        var bottom = -1
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr 24 == 0) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                bottom = y
            }
        }
        if (right < 0) return@getOrPut null
        intArrayOf(left, top, right, bottom)
    }

    /** One decode per sprite per run: this is read by the budget and by the sweep below. */
    private val contentBoxes = mutableMapOf<String, IntArray?>()

    private fun pngSize(name: String): Pair<Int, Int> {
        val file = File(drawableDir, "$name.png")
        val header = file.inputStream().use { input ->
            val buffer = ByteArray(24)
            assertEquals("${file.name} is too short to be a PNG", 24, input.read(buffer))
            buffer
        }
        fun intAt(offset: Int) = (0 until 4).fold(0) { acc, i ->
            (acc shl 8) or (header[offset + i].toInt() and 0xFF)
        }
        return intAt(16) to intAt(20)
    }

    /**
     * The gate a grid of 2 has to pass, recorded as an assertion so the answer cannot be mislaid.
     *
     * A grid of 2 needs every sprite to hold headroom of at least [GRID_TWO_BREAK_EVEN], and to
     * hold it *with margin* before the change could be called invisible. The set does not: the
     * measurement below is the reason `SPRITE_PIXELS_PER_UNIT` is 3.
     *
     * The assertion is written as "the set still fails the gate" rather than as a target to reach.
     * If an artwork pass ever does clear it, this test fails and whoever made it clear comes here
     * and says so -- which is the same shape `SpriteGeometryTest`'s budget uses.
     */
    @Test
    fun `the set does not clear the headroom a grid of 2 would need`() {
        val sprites = spriteNames()
        val previewScales = previewMaxItemScale()
        val short = sprites.filter { name ->
            val scene = pathFor(name).headroom(reference)
            val preview = previewScales[name]?.let { previewPath(it).headroom(reference) } ?: Float.MAX_VALUE
            minOf(scene, preview) < GRID_TWO_BREAK_EVEN
        }
        assertTrue(
            "no sprite is inside $GRID_TWO_BREAK_EVEN of break-even any more, so the grid question " +
                "is open again -- re-measure and rewrite this test rather than deleting it",
            short.isNotEmpty(),
        )
    }

    // ---------------------------------------------------------------- the GPU's own ceiling

    /**
     * What the GPU holds, and the second of this project's two sprite-memory limits.
     *
     * ## Why there are two
     *
     * `SpriteGeometryTest.decodedByteBudget` sums width x height x 4 over every shipped PNG: the right
     * number for the **CPU** side and the wrong one for the GPU, and the two do not move together:
     *
     * - `GlTextureCache.register` uploads `reduce(bitmap, SpriteDetailLevel.levelFor(scale))`,
     *   sized against the scale the sprite is about to be **drawn** at, not the size it was
     *   authored at. `SpriteDetailLevel` picks the level whose residual lands nearest 0.5, so the
     *   copy on the GPU is between 1.4x and 2.8x the drawn size whatever the artwork does, and the
     *   transparent border is cropped after the reduction ([uploadedCropped]).
     * - `SpriteBlitter.onSpriteUploaded` releases the decoded bitmap as soon as the upload
     *   succeeds, so on the GPU path the authored-size bitmap is a **transient** and not a
     *   resident.
     *
     * So authoring the set smaller lowers the decoded figure by its area and the uploaded one far
     * less, and a selective reduction can raise the uploaded figure where the level quantisation
     * tips the wrong way. A ceiling that cannot tell those two apart cannot be used to argue about
     * either.
     *
     * ## What each limit is for, said once
     *
     * | | counts | protects |
     * |---|---|---|
     * | `SpriteGeometryTest.decodedByteBudget` | authored px x 4, every PNG | the `Canvas` path, where `SpriteCache` holds the authored bitmap for every frame and nothing releases it -- the settings and gallery previews on **every** device, and the whole wallpaper once GL is given up on (`GlLifecyclePolicy.MAX_CONTEXT_REBUILDS`). Also the APK, and the per-sprite transient decode peak |
     * | this one | the texels actually uploaded, per sprite at the largest scale it reaches | GL texture memory: the atlas plus every standalone texture |
     *
     * **Neither is removable in favour of the other.** The `Canvas` path is not hypothetical:
     * `ThemePreview.kt` builds a `CanvasSceneTarget` unconditionally, so every device that opens
     * the settings screen decodes sprites at their authored size and keeps them.
     *
     * ## The number
     *
     * **21 MiB**, the maintainer's ceiling (`DESIGN_NOTES.md` §12, decision 64). The measured figure
     * and the margin under it are printed on every run. The ceiling sits just above the measured
     * figure, as the decoded one does, so artwork that takes the set past it is argued for rather
     * than let in by slack.
     *
     * ## What the basis is, and what it is not
     *
     * Every sprite at the largest scale any path draws it at, at the reference viewport, summed over
     * the whole set: an upper bound computable on the host from the shipped artwork alone, which is
     * what lets it be a test at all. It is **not a scene**: no frame draws every sprite, and the set
     * holds alternatives of which a scene draws one -- the two meadow clumps, the three palm crowns.
     * A facade, ink to its edges, is charged nearly its whole canvas.
     *
     * **Nothing in the shipped app reads this constant**: it is a build gate, like
     * `SpriteGeometryTest.decodedByteBudget`. The device's real limit is the atlas page this budget
     * is modelled on and is not the same as -- 2048x2048 RGBA, 16 MiB, allocated once -- and whether
     * a set fits it is a packing question this number cannot answer, measured on the device by
     * `GlAtlasOccupancyTest`.
     */
    private val uploadedTexelBudget = 21L * 1024L * 1024L

    @Test
    fun `the shipped sprite set stays inside the texture memory it uploads`() {
        val previewScales = previewMaxItemScale()
        var total = 0L
        var people = 0L
        var uncropped = 0L
        for (name in spriteNames()) {
            val (w, h) = pngSize(name)
            val scene = pathFor(name).headroom(reference)
            val preview = previewScales[name]?.let { previewPath(it).headroom(reference) }
                ?: Float.MAX_VALUE
            val level = SpriteDetailLevel.levelFor(1f / minOf(scene, preview))
            val cost = uploadedCropped(name, level)
            total += cost
            uncropped += uploaded(w, h, level)
            if (name.startsWith("person_")) people += cost
        }
        // **Printed on every run, passing or failing**, as `SpriteGeometryTest` prints the decoded
        // figure: the margin is read from the build output, not found by lowering the constant by
        // hand.
        println(
            "uploadedTexelBudget: %d B of %d (%.3f MiB of %d MiB), %d B of margin, at %s".format(
                total, uploadedTexelBudget, total / 1048576.0,
                uploadedTexelBudget / 1048576L, uploadedTexelBudget - total, reference.label,
            ),
        )
        // The split is in the message rather than in a comment, because all three numbers are
        // things a release has to report and a number in a comment is a number that goes stale.
        assertTrue(
            "the sprite set uploads $total B of texels at ${reference.label} " +
                "($people B of it people, $uncropped B if the transparent border were not cropped " +
                "after the reduction), past the $uploadedTexelBudget budget. This is GL texture " +
                "memory and it is not the same number as SpriteGeometryTest.decodedByteBudget -- " +
                "read this constant's comment before moving either.",
            total <= uploadedTexelBudget,
        )
    }

    // ---------------------------------------------------------------- shipped set

    /** ARGB_8888 bytes this sprite decodes to, straight out of the PNG's IHDR. */
    private fun decodedBytes(name: String): Long {
        val file = File(drawableDir, "$name.png")
        val header = file.inputStream().use { input ->
            val buffer = ByteArray(24)
            assertEquals("${file.name} is too short to be a PNG", 24, input.read(buffer))
            buffer
        }
        fun intAt(offset: Int) = (0 until 4).fold(0) { acc, i ->
            (acc shl 8) or (header[offset + i].toInt() and 0xFF)
        }
        return intAt(16).toLong() * intAt(20).toLong() * 4L
    }

    private fun spriteNames(): List<String> =
        drawableDir.listFiles { file -> file.name.endsWith(".png") }
            .orEmpty()
            .map { it.name.removeSuffix(".png") }
            .sorted()

    /** The disc's own width in units: its PNG's pixels over the oversample (`deco_bulb`, 24 px, 8 units). */
    private val discUnits: Float by lazy {
        ImageIO.read(File(drawableDir, "deco_bulb.png")).width / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
    }

    private val drawableDir: File by lazy {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "${prefix}src/main/res/drawable-nodpi")
                if (candidate.isDirectory) return@lazy candidate
            }
            dir = dir.parentFile
        }
        throw AssertionError("could not locate src/main/res/drawable-nodpi from ${File(".").absolutePath}")
    }
}
