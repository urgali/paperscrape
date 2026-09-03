package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * One sprite of a preview object: the resource, the offset the renderer blits it at, and the tint
 * (null for fixed art). Offsets are in scene units and are copied from `SceneObjectRenderer`'s own
 * draw functions, so a preview house is assembled out of exactly the parts, at exactly the
 * positions, the wallpaper assembles one from.
 */
data class PreviewSprite(val resId: Int, val ox: Float, val oy: Float, val tint: Int? = null, val alpha: Int = 255)

/** An object standing at [x] on the ground line [y], drawn at [scale]. */
data class PreviewItem(val x: Float, val y: Float, val scale: Float, val parts: List<PreviewSprite>)

/** A filled triangle: a mountain peak, or (flattened) a dune. */
data class PreviewPeak(val x: Float, val peakY: Float, val halfWidth: Float, val colour: Int, val dune: Boolean = false)

/** A horizontal band of water. */
data class PreviewBand(val top: Float, val bottom: Float, val colour: Int)

/** A single filled dot: a star, a snowflake, a falling leaf. */
data class PreviewDot(val x: Float, val y: Float, val radius: Float, val colour: Int, val alpha: Int = 255)

/**
 * Everything one theme's gallery preview draws, in the order it draws it.
 *
 * Deliberately a plain data description with no Android type in it beyond resource ids (which are
 * `Int`s): what a preview contains is a question about the theme, answerable and testable without a
 * `Canvas`. [ThemePreviewPainter] is the only thing that knows how to put it on screen.
 */
data class ThemePreviewScene(
    val skyTop: Int,
    val skyBottom: Int,
    val groundColour: Int,
    val peaks: List<PreviewPeak>,
    val lake: PreviewBand,
    val hasLake: Boolean,
    val hasRoad: Boolean,
    val roadColour: Int,
    val backdrop: List<PreviewItem>,
    val items: List<PreviewItem>,
    val cars: List<PreviewItem>,
    val ground: List<PreviewItem>,
    val dots: List<PreviewDot>,
) {
    companion object {
        /** The preview's own coordinate space, 4:3 like the gallery card. */
        const val WIDTH_UNITS = 320f
        const val HEIGHT_UNITS = 240f
        const val HORIZON_UNITS = 150f
    }
}

/**
 * The one place a preview's size on screen is decided.
 *
 * Both places that show a preview -- the gallery card and the strip at the top of World & scene --
 * go through this, so they cannot drift into different aspect ratios, different crops or different
 * per-object fitting factors. That drift is exactly what v2.9 shipped: the gallery previews were
 * composed in scene units while the World & scene strip still magnified the size table with
 * per-item fitting factors so three objects of very different heights would fit a 120 dp band, and
 * the two sat next to each other looking like different products.
 */
object ThemePreviewGeometry {

    /** 4:3. The scene is composed once, at one shape, and never cropped to fit a container. */
    const val ASPECT_RATIO = ThemePreviewScene.WIDTH_UNITS / ThemePreviewScene.HEIGHT_UNITS

    /**
     * Scene units to pixels for a container [widthPx] wide.
     *
     * Uniform: the same factor on both axes, so nothing is stretched and no object needs a fitting
     * factor of its own. A container is expected to hold the whole scene at [ASPECT_RATIO]; the
     * height that requires is [heightFor].
     */
    fun scaleFor(widthPx: Float): Float = widthPx / ThemePreviewScene.WIDTH_UNITS

    /** The height a container [widthPx] wide must have to show the whole scene. */
    fun heightFor(widthPx: Float): Float = widthPx / ASPECT_RATIO
}

/**
 * Builds a theme's preview scene out of the theme's own palette and the customization it actually
 * ships with.
 *
 * **Nothing here decides what a theme contains.** Every object is conditional on the same flags the
 * wallpaper reads -- `lake.visible`, `snowmen.visible`, `winterColorsEnabled`, `halloweenEnabled`,
 * `mountainsFront.visible` and so on -- so a preview cannot show something the scene would not, and
 * cannot miss something it would. The gallery passes the customization a theme is actually saved
 * with, which is `defaultCustomizationFor(id)` for an untouched built-in and the stored override
 * for a customised one.
 *
 * What lives here instead is *composition*: which slots exist and where they stand. The real scene
 * generates hundreds of objects across a screen five times this wide, and shrinking that produces a
 * grey mush; a preview is a dozen objects in four reading bands -- skyline, tree line, the house
 * row with its people, and the road.
 */
object ThemePreviewScenes {

    private const val ROW_BUILDINGS = 168f
    private const val ROW_TREES = 179f
    private const val ROW_HOUSES = 191f
    private const val ROW_PEOPLE = 199f
    private const val ROW_GROUND = 203f
    private const val ROAD_TOP = 207f
    private const val ROAD_BOTTOM = 229f
    private const val ROW_CARS = 214f

    /** See [car]: where a wheelless preview car's painted floor sits, kept from v4.18. */
    private const val PREVIEW_CAR_FLOOR_DROP = 28f

    /** The bottom edge of every v4.19 body, plus the half-unit of paper rim below it. */
    private const val CAR_PAINTED_FLOOR_UNITS = 30.5f

    /** `SceneObjectCatalog` maps these two themes' tree slots to `PALM_TREE`. */
    private val PALM_THEMES = setOf("beach", "desert")

    /** The carved moon's own tint in `PaperRenderer`; not the theme's `moonColor`. */
    private const val HALLOWEEN_MOON_COLOUR = 0xFFFF8C2A.toInt()

    /**
     * [forceNight] overrides the time of day the theme would otherwise be shown at. The gallery
     * never passes it -- a card shows the theme's own hour -- but the World & scene strip has
     * always had a day/night toggle, because half the colours a user edits there are night
     * colours and a preview that cannot show them is not much of a preview.
     */
    fun forTheme(
        theme: SceneTheme,
        customization: SceneCustomization,
        forceNight: Boolean? = null,
    ): ThemePreviewScene {
        val c = customization
        val winter = c.winterColorsEnabled
        val halloween = c.halloweenEnabled
        // Night for the two themes whose subject *is* the night: the fireworks theme and the
        // horror sky. Everything else reads its day palette, which is what a gallery is for.
        val night = forceNight ?: (c.horrorSkyEnabled || theme.hasFireworks)
        val palms = theme.id in PALM_THEMES

        val skyTop: Int
        val skyBottom: Int
        when {
            c.horrorSkyEnabled -> {
                skyTop = if (night) HORROR_SKY_TOP_NIGHT else HORROR_SKY_TOP_DAY
                skyBottom = if (night) HORROR_SKY_LOW_NIGHT else HORROR_SKY_LOW_DAY
            }
            night -> {
                skyTop = theme.skyNight.first()
                skyBottom = theme.skyNight.last()
            }
            // Sunset is the one theme named after a phase of the day, so it shows that phase.
            theme.id == "sunset" -> {
                skyTop = theme.skyDusk.first()
                skyBottom = theme.skyDusk.last()
            }
            else -> {
                skyTop = theme.skyDay.first()
                skyBottom = theme.skyDay.last()
            }
        }

        val ground = when {
            night -> c.hillsColorNight
            theme.id == "sunset" -> blendRgb(c.hillsColorDay, c.hillsColorNight, 0.40f)
            else -> c.hillsColorDay
        }

        val peaks = buildPeaks(theme, c, night)
        val lakeBand = PreviewBand(
            top = HORIZON + if (theme.id == "beach") 9f else 2f,
            bottom = HORIZON + 2f + 36f * c.lake.height.coerceIn(0.1f, 1f),
            colour = if (night) c.lake.colorNight else c.lake.colorDay,
        )

        val backdrop = mutableListOf<PreviewItem>()
        val items = mutableListOf<PreviewItem>()
        val cars = mutableListOf<PreviewItem>()
        val groundItems = mutableListOf<PreviewItem>()
        val dots = mutableListOf<PreviewDot>()

        // --- sun or moon ----------------------------------------------------------------------
        if (night || halloween) {
            val moonSprite = if (halloween) R.drawable.moon_jack_o_lantern else R.drawable.moon_full
            val moonTint = if (halloween) HALLOWEEN_MOON_COLOUR else c.moon.color
            backdrop += PreviewItem(
                if (halloween) 250f else 60f, 44f, 0.55f,
                listOf(PreviewSprite(moonSprite, -40f, -40f, moonTint)),
            )
        } else if (c.sun.visible) {
            val sunY = if (theme.id == "sunset") 118f else 46f
            val sunX = if (theme.id == "sunset") 262f else 248f
            backdrop += PreviewItem(
                sunX, sunY, 0.55f,
                listOf(
                    PreviewSprite(R.drawable.sun_glow, -66f, -66f, c.sun.color, alpha = 110),
                    PreviewSprite(R.drawable.sun_body, -40f, -40f, c.sun.color),
                ),
            )
        }

        // --- clouds ---------------------------------------------------------------------------
        if (c.clouds.visible && !night) {
            val cloudTint = if (night) c.clouds.colorNight else c.clouds.colorDay
            val heavy = c.precipitation.visible
            backdrop += PreviewItem(70f, 40f, if (heavy) 0.30f else 0.22f,
                listOf(PreviewSprite(R.drawable.cloud_body, -128f, -85f, cloudTint, alpha = 235)))
            backdrop += PreviewItem(206f, 30f, if (heavy) 0.26f else 0.18f,
                listOf(PreviewSprite(R.drawable.cloud_body, -128f, -85f, cloudTint, alpha = 225)))
        }

        // --- stars ----------------------------------------------------------------------------
        if (night && c.stars.visible) {
            var seed = theme.id.hashCode()
            repeat(34) {
                seed = seed * 1664525 + 1013904223
                val x = ((seed ushr 8) % 3160) / 10f
                seed = seed * 1664525 + 1013904223
                val y = ((seed ushr 8) % 1080) / 10f
                // StarsConfig has no colour of its own: the stars are the theme's.
                dots += PreviewDot(x, y, 0.55f, theme.starColor, alpha = 215)
            }
        }

        // --- skyline --------------------------------------------------------------------------
        val cityLike = c.buildings.density >= 0.9f
        // A lake this tall is a shore, not a pond: the town moves back to one building at the
        // waterline so the water, the boats and the palms are what the card shows. Beach is the
        // only built-in theme that reaches it (height 0.9); any custom theme that raises its lake
        // that far gets the same treatment for the same reason.
        val shoreline = c.lake.visible && c.lake.height >= 0.8f
        if (c.buildings.visible) {
            if (shoreline) {
                items += PreviewItem(252f, ROW_GROUND, 0.38f, bar(blendRgb(c.buildings.colorDay2, 0xFFFFFFFF.toInt(), 0.55f), winter))
            } else if (cityLike) {
                items += PreviewItem(40f, ROW_BUILDINGS, 0.44f, skyscraper(c.buildings.colorDay1, 170f, winter, lit = true))
                items += PreviewItem(108f, ROW_BUILDINGS, 0.42f, skyscraper(c.buildings.colorDay2, 200f, winter, lit = true))
                items += PreviewItem(176f, ROW_BUILDINGS, 0.44f, skyscraper(blendRgb(c.buildings.colorDay1, 0xFFFFFFFF.toInt(), 0.15f), 150f, winter, lit = true))
                items += PreviewItem(250f, ROW_BUILDINGS, 0.42f, skyscraper(c.buildings.colorDay2, 185f, winter, lit = true))
                items += PreviewItem(96f, ROW_HOUSES, 0.44f, restaurant(blendRgb(c.buildings.colorDay2, 0xFFFFFFFF.toInt(), 0.30f), winter))
                items += PreviewItem(232f, ROW_HOUSES, 0.44f, bar(c.buildings.colorDay1, winter))
            } else {
                items += PreviewItem(58f, ROW_BUILDINGS, 0.40f, skyscraper(c.buildings.colorDay1, 140f, winter, lit = true))
                items += PreviewItem(152f, ROW_BUILDINGS, 0.42f, restaurant(blendRgb(c.buildings.colorDay2, 0xFFFFFFFF.toInt(), 0.25f), winter))
                items += PreviewItem(236f, ROW_BUILDINGS, 0.42f, bar(blendRgb(c.buildings.colorDay2, 0xFF000000.toInt(), 0.15f), winter))
            }
        }

        // --- houses ---------------------------------------------------------------------------
        if (c.houses.visible && !cityLike && !shoreline) {
            items += PreviewItem(96f, ROW_HOUSES, 0.46f, largeHouse(c.houses.colorDay1, winter, lit = true))
            items += PreviewItem(250f, ROW_HOUSES, 0.46f, smallHouse(c.houses.colorDay2, winter, lit = true))
        }

        // --- trees ----------------------------------------------------------------------------
        if (c.trees.visible) {
            // Tundra thins its woodland to a scattering rather than removing it, and that is
            // exactly what its density says; the preview reads the same number.
            val sparse = c.trees.density <= 0.25f
            val xs = when {
                sparse -> listOf(178f, 300f)
                c.trees.density >= 0.7f -> listOf(24f, 120f, 200f, 300f)
                else -> listOf(24f, 190f, 300f)
            }
            xs.forEachIndexed { index, x ->
                val leaf = if (c.fallColorsEnabled) FALL_LEAF_COLOURS[index % FALL_LEAF_COLOURS.size] else c.trees.colorDay1
                val row = if (palms) ROW_TREES + 22f else ROW_TREES
                val parts = when {
                    palms -> palmTree(dead = halloween, frost = winter)
                    // Christmas is the theme that puts firs among the trees.
                    c.christmasDecorationsEnabled && index % 2 == 0 -> fir(snow = winter, lights = true)
                    sparse -> fir(snow = winter, lights = false)
                    else -> tree(leaf, winter = winter, halloween = halloween)
                }
                items += PreviewItem(x, row, if (palms) 0.50f else 0.46f, parts)
            }
        }

        // --- people ---------------------------------------------------------------------------
        if (c.people.visible) {
            val row = if (theme.id == "beach") ROW_GROUND else ROW_PEOPLE
            items += PreviewItem(118f, row, 0.34f, person("man", winter, 1))
            items += PreviewItem(142f, row, 0.34f, person("woman", winter, 2))
            items += PreviewItem(268f, row, 0.34f, person("girl", winter, 0))
        }

        // --- cars -----------------------------------------------------------------------------
        if (c.cars.visible) {
            // One of each body, so the gallery shows the variety the road now has.
            cars += PreviewItem(76f, ROW_CARS, 0.42f, car(CarShell.SALOON, c.cars.colorDay1))
            cars += PreviewItem(236f, ROW_CARS, 0.42f, car(CarShell.ESTATE, c.cars.colorDay2))
            if (cityLike) {
                cars += PreviewItem(
                    168f, ROW_CARS, 0.40f,
                    car(CarShell.COMPACT, blendRgb(c.cars.colorDay1, 0xFFC1443B.toInt(), 0.6f)),
                )
            }
        }

        // --- lake life ------------------------------------------------------------------------
        if (c.lake.visible) {
            if (c.lake.sailboatsVisible) {
                backdrop += PreviewItem(60f, lakeBand.top + 14f, 0.34f, sailboat())
                backdrop += PreviewItem(246f, lakeBand.top + 10f, 0.28f, sailboat())
            }
            if (c.lake.dolphinsVisible) {
                backdrop += PreviewItem(108f, lakeBand.top + 24f, 0.32f,
                    listOf(PreviewSprite(R.drawable.dolphin_body, -57.3f, -29f)))
                backdrop += PreviewItem(212f, lakeBand.top + 19f, 0.24f,
                    listOf(PreviewSprite(R.drawable.dolphin_body, -57.3f, -29f)))
            }
        }

        // --- seasonal decorations ---------------------------------------------------------------
        if (c.snowmen.visible) {
            groundItems += PreviewItem(52f, ROW_GROUND, 0.56f, snowman(c.snowmen.colorDay1))
            if (c.snowmen.density >= 0.45f) groundItems += PreviewItem(292f, ROW_GROUND, 0.50f, snowman(c.snowmen.colorDay2))
        }
        if (c.gifts.visible) {
            groundItems += PreviewItem(200f, ROW_GROUND, 0.55f, gift(c.gifts.colorDay1))
            groundItems += PreviewItem(222f, ROW_GROUND, 0.48f, gift(c.gifts.colorDay2))
            groundItems += PreviewItem(296f, ROW_GROUND, 0.50f, gift(c.gifts.colorDay1))
        }
        if (c.penguins.visible) {
            groundItems += PreviewItem(120f, ROW_GROUND + 4f, 0.60f, penguin(c.penguins.colorDay1))
            groundItems += PreviewItem(148f, ROW_GROUND + 4f, 0.54f, penguin(c.penguins.colorDay2))
            groundItems += PreviewItem(236f, ROW_GROUND, 0.50f, penguin(c.penguins.colorDay1))
        }
        if (c.bunnies.visible) {
            groundItems += PreviewItem(50f, ROW_GROUND, 0.60f, bunny(c.bunnies.colorDay1))
            groundItems += PreviewItem(296f, ROW_GROUND, 0.52f, bunny(c.bunnies.colorDay2))
        }
        if (c.easterEggs.visible) {
            groundItems += PreviewItem(196f, ROW_GROUND, 0.55f, easterEgg(c.easterEggs.colorDay1))
            groundItems += PreviewItem(218f, ROW_GROUND, 0.48f, easterEgg(c.easterEggs.colorDay2))
            groundItems += PreviewItem(92f, ROW_GROUND, 0.46f, easterEgg(c.easterEggs.colorDay1))
        }
        if (c.pumpkins.visible) {
            groundItems += PreviewItem(52f, ROW_GROUND, 0.56f, pumpkin(c.pumpkins.colorDay1))
            groundItems += PreviewItem(204f, ROW_GROUND, 0.50f, pumpkin(c.pumpkins.colorDay2))
            groundItems += PreviewItem(288f, ROW_GROUND, 0.48f, pumpkin(c.pumpkins.colorDay1))
        }
        // Parasols are deliberately absent. The renderer draws them procedurally -- there is no
        // parasol sprite in the library -- and standing in a differently-shaped sprite would be a
        // preview showing something the scene does not contain, which is the one thing this file
        // must not do.
        if (c.flowersEnabled) {
            var seed = theme.id.hashCode() xor 0x5EED
            repeat(10) { i ->
                seed = seed * 1103515245 + 12345
                val x = 24f + i * 31f + ((seed ushr 9) % 12)
                val y = ROW_GROUND - ((seed ushr 5) % 14)
                groundItems += PreviewItem(x, y, 0.95f, listOf(PreviewSprite(R.drawable.ground_flowers, -18f, -12f)))
            }
        }
        if (theme.hasFireworks) {
            backdrop += PreviewItem(212f, 46f, 0.55f, listOf(PreviewSprite(R.drawable.firework, -40f, -40f, 0xFFFFD166.toInt())))
            backdrop += PreviewItem(258f, 66f, 0.42f, listOf(PreviewSprite(R.drawable.firework, -40f, -40f, 0xFFEF7DA8.toInt())))
            backdrop += PreviewItem(172f, 70f, 0.34f, listOf(PreviewSprite(R.drawable.firework, -40f, -40f, 0xFF8AD6F0.toInt())))
        }

        // --- weather --------------------------------------------------------------------------
        if (c.precipitation.visible) {
            var seed = theme.id.hashCode() xor 0x50F1
            val snow = c.precipitation.type == PrecipitationType.SNOW
            val colour = if (snow) c.precipitation.snowColorDay else c.precipitation.rainColorDay
            val count = (120f * c.precipitation.intensity.coerceIn(0.2f, 1f)).toInt()
            repeat(count) {
                seed = seed * 1664525 + 1013904223
                val x = ((seed ushr 8) % 3200) / 10f
                seed = seed * 1664525 + 1013904223
                val y = ((seed ushr 8) % 2100) / 10f
                dots += PreviewDot(x, y, 0.9f, colour, alpha = 230)
            }
        }
        if (c.fallColorsEnabled) {
            var seed = theme.id.hashCode() xor 0x1EAF
            repeat(22) {
                seed = seed * 1664525 + 1013904223
                val x = ((seed ushr 8) % 3200) / 10f
                seed = seed * 1664525 + 1013904223
                val y = 110f + ((seed ushr 8) % 900) / 10f
                dots += PreviewDot(x, y, 1.4f, FALL_LEAF_COLOURS[(seed ushr 3).toInt().mod(FALL_LEAF_COLOURS.size)], alpha = 235)
            }
        }

        return ThemePreviewScene(
            skyTop = skyTop,
            skyBottom = skyBottom,
            groundColour = ground,
            peaks = peaks,
            lake = lakeBand,
            hasLake = c.lake.visible,
            hasRoad = true,
            roadColour = if (night) 0xFF24242C.toInt() else 0xFF3A3A40.toInt(),
            backdrop = backdrop,
            items = items,
            cars = cars,
            ground = groundItems,
            dots = dots,
        )
    }

    private fun buildPeaks(theme: SceneTheme, c: SceneCustomization, night: Boolean): List<PreviewPeak> {
        val out = mutableListOf<PreviewPeak>()
        // Desert's "mountains" read as dunes: same two layers, same colours, flattened.
        val dunes = theme.id == "desert"
        if (c.mountainsBack.visible) {
            val colour = if (night) c.mountainsBack.colorNight else c.mountainsBack.colorDay
            for ((x, y, w) in BACK_PEAKS) out += PreviewPeak(x, y, w, colour, dunes)
        }
        if (c.mountainsFront.visible) {
            val colour = if (night) c.mountainsFront.colorNight else c.mountainsFront.colorDay
            for ((x, y, w) in FRONT_PEAKS) out += PreviewPeak(x, y, w, colour, dunes)
        }
        return out
    }

    private val BACK_PEAKS = listOf(
        Triple(50f, 108f, 46f), Triple(120f, 116f, 40f), Triple(205f, 104f, 52f), Triple(280f, 118f, 44f),
    )
    private val FRONT_PEAKS = listOf(
        Triple(20f, 126f, 40f), Triple(95f, 122f, 44f), Triple(170f, 130f, 40f),
        Triple(245f, 120f, 46f), Triple(305f, 130f, 38f),
    )

    private const val HORIZON = ThemePreviewScene.HORIZON_UNITS

    private val FALL_LEAF_COLOURS = intArrayOf(
        0xFFD2691E.toInt(), 0xFFB5451B.toInt(), 0xFFE0A93A.toInt(), 0xFF8F3B1B.toInt(),
    )

    private const val HORROR_SKY_TOP_NIGHT = 0xFF07060A.toInt()
    private const val HORROR_SKY_TOP_DAY = 0xFF1A1020.toInt()
    private const val HORROR_SKY_LOW_NIGHT = 0xFFB03A06.toInt()
    private const val HORROR_SKY_LOW_DAY = 0xFFF07A10.toInt()

    // --- object part lists, offsets as in SceneObjectRenderer ---------------------------------

    private fun smallHouse(wall: Int, snow: Boolean, lit: Boolean): List<PreviewSprite> {
        val roof = blendRgb(wall, 0xFF1A1410.toInt(), 0.45f)
        val trim = blendRgb(wall, 0xFF000000.toInt(), 0.35f)
        val parts = mutableListOf(
            PreviewSprite(R.drawable.house_small_wall, -48f, -70f, wall),
            PreviewSprite(R.drawable.house_small_roof, -53f, -110f, roof),
        )
        if (snow) parts += PreviewSprite(R.drawable.house_small_roof_snow, -34f, -114f)
        parts += PreviewSprite(R.drawable.house_small_trim, -53f, -71f, trim)
        parts += PreviewSprite(R.drawable.house_small_chimney, 8f, -115f, trim)
        parts += PreviewSprite(R.drawable.house_shared_window, -37f, -45f)
        parts += PreviewSprite(R.drawable.house_shared_window, 15f, -45f)
        if (lit) {
            parts += PreviewSprite(R.drawable.house_window_lit, -37f, -46f)
            parts += PreviewSprite(R.drawable.house_window_lit, 15f, -46f)
        }
        parts += PreviewSprite(R.drawable.house_shared_planter, -39f, -29f)
        parts += PreviewSprite(R.drawable.house_small_door, -10f, -38f, blendRgb(wall, 0xFF000000.toInt(), 0.55f))
        return parts
    }

    private fun largeHouse(wall: Int, snow: Boolean, lit: Boolean): List<PreviewSprite> {
        val roof = blendRgb(wall, 0xFF1A1410.toInt(), 0.45f)
        val trim = blendRgb(wall, 0xFF000000.toInt(), 0.35f)
        val parts = mutableListOf(
            PreviewSprite(R.drawable.house_large_wall, -70f, -95f, wall),
            PreviewSprite(R.drawable.house_large_roof, -75f, -145f, roof),
        )
        if (snow) parts += PreviewSprite(R.drawable.house_large_roof_snow, -50f, -149f)
        parts += PreviewSprite(R.drawable.house_large_trim, -75f, -97f, trim)
        parts += PreviewSprite(R.drawable.house_large_chimney, 20f, -150f, trim)
        for (wx in listOf(-46f, 24f)) {
            for (wy in listOf(-84f, -44f)) {
                parts += PreviewSprite(R.drawable.house_shared_window, wx, wy)
                if (lit) parts += PreviewSprite(R.drawable.house_window_lit, wx, wy - 1f)
            }
        }
        parts += PreviewSprite(R.drawable.house_shared_planter, -48f, -22f)
        parts += PreviewSprite(R.drawable.house_large_door, -11f, -45f, blendRgb(wall, 0xFF000000.toInt(), 0.55f))
        return parts
    }

    private fun skyscraper(wall: Int, height: Float, snow: Boolean, lit: Boolean): List<PreviewSprite> {
        // Read from [SkyscraperSpriteLayout] rather than copied, which is the v3.8 Filone 4 fix.
        // Two of these were wrong: the lit facade sat 6 units right and 6 down of the wall it is
        // meant to lie exactly on top of, and the roof snow carried the *sum* of the renderer's
        // four-term offset instead of the terms. See that object for both.
        val parts = mutableListOf(
            PreviewSprite(
                R.drawable.skyscraper_canopy,
                SkyscraperSpriteLayout.CANOPY_X, SkyscraperSpriteLayout.CANOPY_Y,
            ),
            PreviewSprite(R.drawable.skyscraper_wall, SkyscraperSpriteLayout.WALL_X, -height, wall),
        )
        if (lit) {
            parts += PreviewSprite(
                R.drawable.skyscraper_wall_lit,
                SkyscraperSpriteLayout.WALL_LIT_X, -height + SkyscraperSpriteLayout.WALL_LIT_DY,
            )
        }
        parts += PreviewSprite(
            R.drawable.skyscraper_entrance,
            SkyscraperSpriteLayout.ENTRANCE_X, SkyscraperSpriteLayout.ENTRANCE_Y,
        )
        parts += PreviewSprite(
            R.drawable.skyscraper_setback,
            SkyscraperSpriteLayout.SETBACK_X, -height + SkyscraperSpriteLayout.SETBACK_DY, wall,
        )
        if (snow) {
            parts += PreviewSprite(
                R.drawable.skyscraper_roof_snow,
                SkyscraperSpriteLayout.ROOF_SNOW_X, -height + SkyscraperSpriteLayout.ROOF_SNOW_DY,
            )
        }
        return parts
    }

    private fun restaurant(wall: Int, snow: Boolean): List<PreviewSprite> {
        val parts = mutableListOf(PreviewSprite(R.drawable.restaurant_wall, -50f, -96f, wall))
        if (snow) parts += PreviewSprite(R.drawable.restaurant_roof_snow, -48f, -102f)
        parts += PreviewSprite(R.drawable.restaurant_awning, -34f, -46f)
        parts += PreviewSprite(R.drawable.restaurant_window, -35f, -45f, blendRgb(wall, 0xFFFFFFFF.toInt(), 0.35f))
        parts += PreviewSprite(R.drawable.restaurant_door, 8f, -28f, blendRgb(wall, 0xFF000000.toInt(), 0.35f))
        parts += PreviewSprite(R.drawable.restaurant_sign, -17f, -96f)
        return parts
    }

    private fun bar(wall: Int, snow: Boolean): List<PreviewSprite> {
        val parts = mutableListOf(PreviewSprite(R.drawable.bar_wall, -45f, -92f, wall))
        if (snow) parts += PreviewSprite(R.drawable.bar_roof_snow, -43f, -98f)
        parts += PreviewSprite(R.drawable.bar_door, -10f, -28f, blendRgb(wall, 0xFF000000.toInt(), 0.35f))
        parts += PreviewSprite(R.drawable.house_shared_window, -34f, -82f)
        parts += PreviewSprite(R.drawable.house_shared_window, 14f, -82f)
        parts += PreviewSprite(R.drawable.bar_sign, -12f, -84f)
        return parts
    }

    /**
     * Read from [TreeSpriteLayout] rather than copied, which is the v3.7 Filone C fix.
     *
     * These were four hand-copied numbers, and the snow cap's pair had drifted: it was
     * `(-38,-116)` against the wallpaper's `(-41,-118)`, so a winter preview drew its snow 3 units
     * right and 2 down from where the wallpaper puts it. The renderer's numbers are unchanged; the
     * preview now takes them from the same place instead of restating them.
     */
    private fun tree(leaf: Int, winter: Boolean, halloween: Boolean): List<PreviewSprite> {
        val parts = mutableListOf(
            PreviewSprite(R.drawable.tree_trunk, TreeSpriteLayout.TRUNK_X, TreeSpriteLayout.TRUNK_Y),
        )
        if (halloween) {
            parts += PreviewSprite(
                R.drawable.tree_dead_branches,
                TreeSpriteLayout.FLAT_DEAD_BRANCHES_X, TreeSpriteLayout.FLAT_DEAD_BRANCHES_Y,
            )
            return parts
        }
        parts += PreviewSprite(
            R.drawable.tree_canopy,
            TreeSpriteLayout.FLAT_CANOPY_X, TreeSpriteLayout.FLAT_CANOPY_Y, leaf,
        )
        if (winter) {
            parts += PreviewSprite(
                R.drawable.tree_canopy_snowcap,
                TreeSpriteLayout.FLAT_SNOWCAP_X, TreeSpriteLayout.FLAT_SNOWCAP_Y,
            )
        }
        return parts
    }

    private fun fir(snow: Boolean, lights: Boolean): List<PreviewSprite> {
        val parts = mutableListOf(PreviewSprite(R.drawable.tree_fir, -39f, -122f))
        if (snow) parts += PreviewSprite(R.drawable.tree_fir_snow, -39f, -122f)
        if (lights) {
            parts += PreviewSprite(R.drawable.star_sparkle, -26f, -110f, 0xFFF2C14E.toInt(), alpha = 220)
            parts += PreviewSprite(R.drawable.star_sparkle, 2f, -84f, 0xFFE8483C.toInt(), alpha = 220)
            parts += PreviewSprite(R.drawable.star_sparkle, -22f, -58f, 0xFF5BC0EB.toInt(), alpha = 220)
        }
        return parts
    }

    private fun palmTree(dead: Boolean, frost: Boolean): List<PreviewSprite> {
        val parts = mutableListOf(PreviewSprite(R.drawable.palmtree_trunk, -6f, -58f))
        parts += if (dead) {
            PreviewSprite(R.drawable.palmtree_fronds_dead, -20f, -90.33f)
        } else {
            PreviewSprite(R.drawable.palmtree_fronds, -20f, -90.33f)
        }
        if (frost) parts += PreviewSprite(R.drawable.palmtree_fronds_frost, -20f, -90.33f)
        return parts
    }

    private fun snowman(c: Int) = listOf(
        PreviewSprite(R.drawable.snowman_body, -19f, -74f, c),
        PreviewSprite(R.drawable.snowman_nose, 4f, -51f),
        PreviewSprite(R.drawable.snowman_scarf, -12f, -40f),
    )

    private fun gift(c: Int) = listOf(
        PreviewSprite(R.drawable.gift_box, -20f, -30f, c),
        PreviewSprite(R.drawable.gift_ribbon, -20f, -40f),
    )

    private fun pumpkin(c: Int) = listOf(
        PreviewSprite(R.drawable.pumpkin_body, -19f, -30f, c),
        PreviewSprite(R.drawable.pumpkin_stem, 2f, -42f),
    )

    private fun penguin(c: Int) = listOf(
        PreviewSprite(R.drawable.penguin_body, -14f, -45f, c),
        PreviewSprite(R.drawable.penguin_belly, -9f, -38f, 0xFFF7FAFC.toInt()),
        PreviewSprite(R.drawable.penguin_beak, -6f, -37f),
        PreviewSprite(R.drawable.penguin_feet, -10f, 0f),
    )

    private fun bunny(c: Int) = listOf(
        PreviewSprite(R.drawable.bunny_body, -14f, -61f, c),
        PreviewSprite(R.drawable.bunny_innerear, -4f, -57f),
        PreviewSprite(R.drawable.bunny_tail, -21f, -10f),
    )

    private fun easterEgg(c: Int) = listOf(
        PreviewSprite(R.drawable.easteregg_shell, -16f, -40f, c),
        PreviewSprite(R.drawable.easteregg_pattern, -16f, -25f),
    )

    /**
     * A preview car, on one of the three v4.19 bodies.
     *
     * The gallery draws no wheels, so a car here stands on its **painted floor** rather than on
     * its tyres, and the two offsets are derived from that rather than hand-copied: v4.18's
     * thumbnails put that floor [PREVIEW_CAR_FLOOR_DROP] units below the item's ground line, and
     * keeping the number keeps every thumbnail where it was while the bodies underneath it
     * change. The glass then follows the body by the gap the renderer itself uses, so the two
     * cannot drift apart the way this file's hand-copied pairs have before.
     */
    private fun car(shell: CarShell, c: Int): List<PreviewSprite> {
        val oy = PREVIEW_CAR_FLOOR_DROP - (CAR_PAINTED_FLOOR_UNITS - shell.bodyYUnits)
        return listOf(
            PreviewSprite(shell.bodyRes, shell.bodyXUnits, oy, c),
            PreviewSprite(
                shell.glassRes, shell.glassXUnits,
                oy + (SceneObjectRenderer.CAR_GLASS_ORIGIN_Y_UNITS - shell.bodyYUnits),
            ),
        )
    }

    private fun sailboat() = listOf(
        PreviewSprite(R.drawable.sailboat_sail, -35f, -50f),
        PreviewSprite(R.drawable.sailboat_hull, -42f, 8f),
    )

    private fun person(kind: String, winter: Boolean, frame: Int): List<PreviewSprite> {
        val resId = when (kind) {
            "man" -> if (winter) WINTER_MAN[frame] else SUMMER_MAN[frame]
            "woman" -> if (winter) WINTER_WOMAN[frame] else SUMMER_WOMAN[frame]
            else -> if (winter) WINTER_GIRL[frame] else SUMMER_GIRL[frame]
        }
        return listOf(PreviewSprite(resId, -20.5f, -84f))
    }

    private val SUMMER_MAN = intArrayOf(R.drawable.person_man_summer_walk0, R.drawable.person_man_summer_walk1, R.drawable.person_man_summer_walk2)
    private val WINTER_MAN = intArrayOf(R.drawable.person_man_winter_walk0, R.drawable.person_man_winter_walk1, R.drawable.person_man_winter_walk2)
    private val SUMMER_WOMAN = intArrayOf(R.drawable.person_woman_summer_walk0, R.drawable.person_woman_summer_walk1, R.drawable.person_woman_summer_walk2)
    private val WINTER_WOMAN = intArrayOf(R.drawable.person_woman_winter_walk0, R.drawable.person_woman_winter_walk1, R.drawable.person_woman_winter_walk2)
    private val SUMMER_GIRL = intArrayOf(R.drawable.person_girl_summer_walk0, R.drawable.person_girl_summer_walk1, R.drawable.person_girl_summer_walk2)
    private val WINTER_GIRL = intArrayOf(R.drawable.person_girl_winter_walk0, R.drawable.person_girl_winter_walk1, R.drawable.person_girl_winter_walk2)

    /** The renderer's own `ColorUtils.blendARGB`, reimplemented so this file needs no Android. */
    private fun blendRgb(from: Int, to: Int, ratio: Float): Int {
        val inverse = 1f - ratio
        val a = ((from ushr 24 and 0xFF) * inverse + (to ushr 24 and 0xFF) * ratio).toInt()
        val r = ((from ushr 16 and 0xFF) * inverse + (to ushr 16 and 0xFF) * ratio).toInt()
        val g = ((from ushr 8 and 0xFF) * inverse + (to ushr 8 and 0xFF) * ratio).toInt()
        val b = ((from and 0xFF) * inverse + (to and 0xFF) * ratio).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
