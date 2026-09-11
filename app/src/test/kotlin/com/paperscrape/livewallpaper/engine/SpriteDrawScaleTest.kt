package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import java.io.File
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
 * it to 2 would take the whole decoded set to four ninths of its size -- larger than every saving
 * `SpriteGeometryTest`'s ceiling has ever been argued over -- and it costs nothing visible *as long
 * as every sprite still arrives on screen smaller than its own file*. That is a measurement, not a
 * judgement, and until v4.29 nobody had taken it.
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

        put(
            staticPath("small house", SceneSpace.SceneVariant.HOUSE_SMALL),
            "house_small_chimney", "house_small_door", "house_small_roof", "house_small_roof_snow",
            "house_small_trim", "house_small_wall", "house_shared_planter", "house_shared_window",
            "house_window_lit",
        )
        put(
            staticPath("large house", SceneSpace.SceneVariant.HOUSE_LARGE),
            "house_large_chimney", "house_large_door", "house_large_roof", "house_large_roof_snow",
            "house_large_trim", "house_large_wall",
        )
        put(
            staticPath("tree", SceneSpace.SceneVariant.TREE),
            "tree_trunk", "tree_canopy", "tree_canopy_snowcap", "tree_dead_branches",
            "tree_fir", "tree_fir_snow",
        )
        put(
            staticPath("palm", SceneSpace.SceneVariant.PALM_TREE),
            "palmtree_trunk", "palmtree_fronds", "palmtree_fronds_dead", "palmtree_fronds_frost",
        )
        put(
            staticPath("skyscraper", SceneSpace.SceneVariant.TOWER),
            "skyscraper_wall", "skyscraper_wall_lit", "skyscraper_setback", "skyscraper_entrance",
            "skyscraper_canopy", "skyscraper_roof_snow",
        )
        put(
            staticPath("restaurant", SceneSpace.SceneVariant.RESTAURANT),
            "restaurant_wall", "restaurant_window", "restaurant_door", "restaurant_awning",
            "restaurant_cornice", "restaurant_sign", "restaurant_roof_snow",
        )
        put(
            staticPath("bar", SceneSpace.SceneVariant.BAR),
            "bar_wall", "bar_door", "bar_cornice", "bar_sign", "bar_lantern", "bar_roof_snow",
        )
        put(
            staticPath("snowman", SceneSpace.SceneVariant.SNOWMAN),
            "snowman_body", "snowman_nose", "snowman_scarf",
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
        // foot of a Christmas fir at 0.36 of the TREE's. The standalone one is the larger.
        put(
            staticPath("gift", SceneSpace.SceneVariant.GIFT),
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
            "ground_flowers",
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
            "moon_jack_o_lantern",
        )
        // One bird is blitted at `scale(1, +-1)` inside a bare translate: its own pixels, 1:1.
        put(DrawPath("bird", SpriteScale.CANVAS_PIXELS) { 1f }, "bird_body")
        put(
            DrawPath("star sparkle", SpriteScale.SCENE_UNITS) { _ ->
                MAX_STAR_RADIUS_PX / PaperRenderer.STAR_SPRITE_RADIUS_DIVISOR
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
        put(
            DrawPath("Santa's sleigh", SpriteScale.SCENE_UNITS) { _ -> PaperRenderer.SANTA_SLEIGH_SCALE },
            "santa_sleigh_scene", "santa_sleigh_trot",
        )
    }

    /**
     * Star radii are rolled in `regenerateStars` as `2.4 + rnd * 3.2`; the largest is 5.6.
     * Stated here because the roll is inside a private function with no constant to read.
     */
    private companion object {
        const val MAX_STAR_RADIUS_PX = 5.6f

        /** `range(0.85, 1.25)` times the largest tier multiplier, `CLOUD_TIER_SIZE_MULTIPLIER[3]`. */
        const val MAX_CLOUD_SCALE = 1.25f * 1.15f

        /**
         * `drawRainbow`'s own `maxRadius = screenWidth * 0.62f`, which is a literal at the call
         * site and has no constant to read. Copied rather than derived, and it is the one number
         * in this file that is: if the arc is ever resized, this is where the measurement rots.
         */
        const val RAINBOW_MAX_RADIUS_FRACTION = 0.62f

        /** Headroom a family needs to survive a grid of 2 without being magnified. */
        const val GRID_TWO_BREAK_EVEN = 1.5f
    }

    // ---------------------------------------------------------------- the person families

    /**
     * The walk, carry and head sprites, each on the transform that actually blits it.
     *
     * Resolved by name rather than listed: 202 of the 305 shipped PNGs are person variants, and a
     * hand-written list of them would be a second place for the skin/season axes to be declared.
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
                    val items = scene.backdrop + scene.items + scene.cars + scene.ground
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

    /**
     * What the grid would buy in **GPU texture memory**, which is not what it buys on the ceiling.
     *
     * `GlTextureCache` does not upload the authored bitmap: it uploads `reduce(bitmap, level)`
     * with `level = SpriteDetailLevel.levelFor(drawn px per authored px)`, chosen so the copy on
     * the GPU is between 1.4x and 2.8x the size the sprite is actually drawn at. The stored size is
     * therefore a function of the **drawn** size, and authoring smaller mostly just lowers the
     * level by one -- so the atlas holds very nearly the same texels either way.
     *
     * This prints both totals from the same basis (every sprite at the largest scale it reaches,
     * which is not a real scene but is the same unreal scene on both sides), so the difference
     * between "what the ceiling counts" and "what the GPU holds" is a number rather than an
     * argument.
     */
    @Test
    fun `the grid moves the decoded ceiling, and barely moves the texture memory`() {
        val sprites = spriteNames()
        val previewScales = previewMaxItemScale()
        for (viewport in listOf(bench, reference, tall)) {
            var authoredNow = 0L
            var authoredTwo = 0L
            var uploadedNow = 0L
            var uploadedTwo = 0L
            for (name in sprites) {
                val (w, h) = pngSize(name)
                val scene = pathFor(name).headroom(viewport)
                val preview = previewScales[name]?.let { previewPath(it).headroom(viewport) }
                    ?: Float.MAX_VALUE
                val headroom = minOf(scene, preview)
                val factor = 1f / headroom

                authoredNow += w.toLong() * h * 4L
                uploadedNow += uploaded(w, h, SpriteDetailLevel.levelFor(factor))

                // At a grid of 2 the same drawing occupies two thirds of the pixels on each axis
                // and is drawn at exactly the same size, so its factor rises by 3/2.
                val w2 = w * 2 / 3
                val h2 = h * 2 / 3
                authoredTwo += w2.toLong() * h2 * 4L
                uploadedTwo += uploaded(w2, h2, SpriteDetailLevel.levelFor(factor * 1.5f))
            }
            // And the same pair for the only reduction the headroom actually permits: leave every
            // tight sprite at the grid it has, redraw only the families that still hold `keep`
            // after the change.
            val selective = StringBuilder()
            for (keep in listOf(2f, 3f)) {
                var decoded = 0L
                var gpu = 0L
                var moved = 0
                for (name in sprites) {
                    val (w, h) = pngSize(name)
                    val scene = pathFor(name).headroom(viewport)
                    val preview = previewScales[name]?.let { previewPath(it).headroom(viewport) }
                        ?: Float.MAX_VALUE
                    val headroom = minOf(scene, preview)
                    val factor = 1f / headroom
                    if (headroom >= keep) {
                        moved++
                        val w2 = w * 2 / 3
                        val h2 = h * 2 / 3
                        decoded += w2.toLong() * h2 * 4L
                        gpu += uploaded(w2, h2, SpriteDetailLevel.levelFor(factor * 1.5f))
                    } else {
                        decoded += w.toLong() * h * 4L
                        gpu += uploaded(w, h, SpriteDetailLevel.levelFor(factor))
                    }
                }
                selective.append(
                    "   selective (headroom >= %.0f, %d sprites): decoded %.2f MiB, uploaded %.2f MiB\n"
                        .format(keep, moved, decoded / 1048576.0, gpu / 1048576.0),
                )
            }
            println("== %s ==".format(viewport.label))
            println(
                "   decoded set (what decodedByteBudget counts): %d B -> %d B  (%.2f MiB -> %.2f MiB)"
                    .format(authoredNow, authoredTwo, authoredNow / 1048576.0, authoredTwo / 1048576.0),
            )
            println(
                "   uploaded texels (what the GPU actually holds): %d B -> %d B  (%.2f MiB -> %.2f MiB, %+.1f%%)"
                    .format(
                        uploadedNow, uploadedTwo, uploadedNow / 1048576.0, uploadedTwo / 1048576.0,
                        100.0 * (uploadedTwo - uploadedNow) / uploadedNow,
                    ),
            )
            print(selective)
        }
    }

    private fun uploaded(w: Int, h: Int, level: Int): Long =
        SpriteDetailLevel.reduced(w, level).toLong() * SpriteDetailLevel.reduced(h, level) * 4L

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
     * The gate v4.29 was asked to pass before touching the grid, recorded as an assertion so the
     * answer cannot be mislaid.
     *
     * A grid of 2 needs every sprite to hold headroom of at least [GRID_TWO_BREAK_EVEN], and to
     * hold it *with margin* before the change could be called invisible. The set does not: the
     * measurement below is the reason `SPRITE_PIXELS_PER_UNIT` is still 3.
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
     * What the GPU actually holds, and the second of this project's two sprite-memory limits.
     *
     * ## Why there are two, and why this one is new in v4.29
     *
     * `SpriteGeometryTest.decodedByteBudget` sums width x height x 4 over every shipped PNG. That
     * is the right number for the **CPU** side and the wrong one for the GPU, and until v4.29 the
     * project had only that one and read it as though it covered both. It does not, and the two do
     * not even move together:
     *
     * - `GlTextureCache.register` uploads `reduce(bitmap, SpriteDetailLevel.levelFor(scale))`,
     *   sized against the scale the sprite is about to be **drawn** at, not the size it was
     *   authored at. `SpriteDetailLevel` picks the level whose residual lands nearest 0.5, so the
     *   copy on the GPU is always between 1.4x and 2.8x the drawn size whatever the artwork does.
     * - And `SpriteBlitter.onSpriteUploaded` releases the decoded bitmap as soon as the upload
     *   succeeds, so on the GPU path the authored-size bitmap is a **transient** and not a
     *   resident.
     *
     * Measured here at the reference viewport, the shipped set is **31.74 MiB decoded and 17.09 MiB
     * uploaded**. Author the whole set two thirds the size and the decoded figure falls to 14.11
     * MiB while the uploaded figure falls only to 13.06 -- and for the *selective* reduction the
     * headroom actually permits, the uploaded figure **rises**, because the level quantisation tips
     * the wrong way for exactly those sprites. A ceiling that cannot tell those two apart cannot be
     * used to argue about either.
     *
     * ## What each limit is for, said once
     *
     * | | counts | protects |
     * |---|---|---|
     * | `SpriteGeometryTest.decodedByteBudget` | authored px x 4, every PNG | the `Canvas` path, where `SpriteCache` holds the authored bitmap for every frame and nothing releases it -- the settings and gallery previews on **every** device, and the whole wallpaper once EGL has failed three times. Also the APK, and the per-sprite transient decode peak |
     * | this one | the texels actually uploaded, per sprite at the largest scale it reaches | GL texture memory: the atlas plus every standalone texture |
     *
     * **Neither is removable in favour of the other.** The `Canvas` path is not hypothetical:
     * `ThemePreview.kt` builds a `CanvasSceneTarget` unconditionally, so every device that opens
     * the settings screen decodes sprites at their authored size and keeps them. That is why
     * v4.29 added this limit beside the old one rather than repointing the old one at texels.
     *
     * ## The number
     *
     * **17 921 692 B measured, 18 MiB here**, which is the next figure above it and leaves 952 676
     * B -- the same "just above the measured figure" convention every raise of the decoded ceiling
     * has used, and for the same reason: the pass after this one has to come here and argue too.
     *
     * ## What the basis is, and what it is not
     *
     * Every sprite at the largest scale any path draws it at, summed over the whole set. That is
     * **not a scene** -- no frame draws all 305 sprites, and the device census for the twelve-theme
     * walk at full density measured 7 287 KiB packed in the atlas plus 2 317 KiB standalone, about
     * 9.4 MiB, against the 17.09 MiB here. It is an upper bound, computable on the host from the
     * shipped artwork alone, which is what lets it be a test at all; the device figure is in
     * `release-verification/V4_29_REPORT.md` and cannot be.
     */
    private val uploadedTexelBudget = 18L * 1024L * 1024L

    @Test
    fun `the shipped sprite set stays inside the texture memory it uploads`() {
        val previewScales = previewMaxItemScale()
        var total = 0L
        for (name in spriteNames()) {
            val (w, h) = pngSize(name)
            val scene = pathFor(name).headroom(reference)
            val preview = previewScales[name]?.let { previewPath(it).headroom(reference) }
                ?: Float.MAX_VALUE
            total += uploaded(w, h, SpriteDetailLevel.levelFor(1f / minOf(scene, preview)))
        }
        assertTrue(
            "the sprite set uploads $total B of texels at ${reference.label}, past the " +
                "$uploadedTexelBudget budget. This is GL texture memory and it is not the same " +
                "number as SpriteGeometryTest.decodedByteBudget -- read this constant's comment " +
                "before moving either.",
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
