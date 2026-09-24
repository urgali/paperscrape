package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * One sprite of a preview object: the resource, the offset the renderer blits it at, and the tint
 * (null for fixed art). Offsets are in scene units and are copied from `SceneObjectRenderer`'s own
 * draw functions, so a preview house is assembled out of exactly the parts, at exactly the
 * positions, the wallpaper assembles one from.
 */
/**
 * One blit of a preview object.
 *
 * [added] is the people's and the neighbourhood's blend: the mask's contribution is **summed**
 * into the frame over a fixed layer rather than drawn over it, which is what makes a weight mask
 * interpolate. A preview that drew those masks with a plain tint would show the wall colour
 * covering the ink it is supposed to be added to -- the same mistake, in the gallery, that
 * `SpriteBlitter.drawTintedAdded` exists to prevent in the scene.
 */
data class PreviewSprite(
    val resId: Int,
    val ox: Float,
    val oy: Float,
    val tint: Int? = null,
    val alpha: Int = 255,
    val added: Boolean = false,
)

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
    /**
     * What floats on the lake, drawn in a pass of its own immediately after the water.
     *
     * A list rather than more entries in [backdrop], because [backdrop] is split at the horizon by
     * the painter -- everything above it goes behind the hills, everything below in front of them
     * -- and the water band can sit on either side of that line. Beach's sea starts above the
     * horizon, so a boat put in [backdrop] there is painted *under* the water and disappears.
     */
    val water: List<PreviewItem> = emptyList(),
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
 * grey mush; a preview is a dozen objects, one per family, standing in the order of depth the
 * scene stands them in -- towers, restaurant, large house, school, small house, bar, then the
 * trees, the pavement and the road. See [forTheme].
 */
object ThemePreviewScenes {

    /**
     * The depth rows the card's street is built on, far to near.
     *
     * v5.7 replaced four reading bands with the scene's own order. `SceneSpace` deals the
     * neighbourhood by `depthFraction`, and the families land in a fixed sequence: the towers on
     * the skyline (0.00-0.27), the restaurant at 0.4444, the large house, the school at 0.6222,
     * the small house, and the bar at 0.8000 as the nearest building of all -- every one of those
     * three shop depths is the single value `SceneObjectCatalog` emits in all twelve built-ins,
     * measured, not chosen here. A row is a ground line rather than a band: an object on a nearer
     * row is appended later and is therefore drawn in front, which is the whole of the ordering.
     *
     * What this replaced put the restaurant and the bar side by side on one row with the two
     * houses in front of them, so the bar -- the building a user stands closest to in the scene --
     * was 88 % hidden behind the small house on ten of the twelve cards.
     */
    private const val ROW_TOWERS = 168f
    private const val ROW_RESTAURANT = 178f
    private const val ROW_HOUSE_LARGE = 182f
    private const val ROW_SCHOOL = 190f
    private const val ROW_HOUSE_SMALL = 194f
    private const val ROW_BAR = 198f
    private const val ROW_TREES = 200f
    private const val ROW_PEOPLE = 203f
    private const val ROW_GROUND = 205f
    private const val ROW_CARS = 214f

    /**
     * Where a seasonal decoration that used to stand at the right edge stands now.
     *
     * The bar is the nearest building and it sits on the right edge, so a snowman, a pumpkin or a
     * rabbit left at 288-296 would cover the one building this layout exists to reveal. They move
     * in front of the small house instead, which is the next gap going left.
     */
    private const val DECOR_RIGHT_X = 232f

    /**
     * The water's lower edge, and how far it climbs when the lake is at full height.
     *
     * **This is the correction the round is named after.** `PaperRenderer.updateLakeBandY` anchors
     * the lake's *bottom* to the guaranteed ground line and grows it *upward*; the card anchored
     * its *top* to the horizon and grew it *downward*, and then stood boats and dolphins at
     * constant offsets from that top. The two disagree for every lake shorter than about two
     * thirds, which is every built-in lake but Beach's -- and the default is 0.33 -- so the
     * animals were placed below the water they were supposed to be in. On Beach, the one card
     * with no town in front to hide the mistake, a dolphin had 44 % of its body on the sand.
     *
     * 164 is the ground line the towers stand behind; 56 is the climb that puts a full-height
     * lake's surface just under the horizon, where the scene's is.
     *
     * Named `CARD_` and not `LAKE_` on purpose: these are lengths on the card's own 320x240
     * canvas, not lengths of the lake's sprites, and `UnitFrameTest`'s prefix table resolves
     * `LAKE_` to the sprite frame. A number that declares the wrong frame is the one thing that
     * test exists to stop.
     */
    private const val CARD_LAKE_BOTTOM_UNITS = 164f
    private const val CARD_LAKE_FULL_HEIGHT_UNITS = 56f

    /** A lake this tall is open water rather than a pond, and its life gets the middle of it. */
    private const val OPEN_WATER_HEIGHT = 0.8f

    private const val ROAD_TOP = 207f
    private const val ROAD_BOTTOM = 229f

    /** See [car]: where a wheelless preview car's painted floor sits, kept from v4.18. */
    private const val PREVIEW_CAR_FLOOR_DROP = 28f

    /** The bottom edge of every v4.19 body, plus the half-unit of paper rim below it. */
    private const val CAR_PAINTED_FLOOR_UNITS = 30.5f

    /** `SceneObjectCatalog` maps these two themes' tree slots to `PALM_TREE`. */
    internal val PALM_THEMES = setOf("beach", "desert")

    /** The carved moon's own tint in `PaperRenderer`; not the theme's `moonColor`. */
    private const val HALLOWEEN_MOON_COLOUR = 0xFFFF8C2A.toInt()

    /**
     * [forceNight] overrides the time of day the theme would otherwise be shown at. The gallery
     * never passes it -- a card shows the theme's own hour -- but the World & scene strip has
     * always had a day/night toggle, because half the colours a user edits there are night
     * colours and a preview that cannot show them is not much of a preview.
     */
    /**
     * [forceNight] overrides the time of day the theme would otherwise be shown at. The gallery
     * never passes it -- a card shows the theme's own hour -- but the World & scene strip has
     * always had a day/night toggle, because half the colours a user edits there are night
     * colours and a preview that cannot show them is not much of a preview.
     *
     * ### v5.7: the card shows the street, in the scene's own order
     *
     * The composition this replaced was arranged for a picture rather than after the scene, and a
     * census of all twelve built-ins against the catalogue found forty-nine places where the card
     * said something the wallpaper does not do: no card showed the school, which every theme
     * builds; the bar was 88 % behind the small house on ten of them; Beach omitted its entire
     * town; Big City omitted its three houses (and a test asserted it must); the birds nobody
     * drew; and every boat and dolphin stood outside the water (see [CARD_LAKE_BOTTOM_UNITS]).
     *
     * So the six building families are laid out far to near exactly as `SceneSpace` deals them --
     * towers, restaurant, large house, school, small house, bar -- the road carries the fire
     * appliance the traffic mix really contains one time in ten, gulls fly by day, and the lake's
     * life is placed on lanes that are fractions of the band rather than at constant offsets.
     *
     * **Nothing here decides what a theme contains** -- that was true before and is still true.
     * Every object stays conditional on the flag the wallpaper reads. What changed is only where
     * the objects stand, and `ThemePreviewTruthTest` is the measure that keeps it honest: it
     * re-runs the census on every built-in and on a custom customization, and fails if a family
     * the scene draws is missing from the card, if the card invents one, if a boat or a dolphin
     * has ink outside the water, or if anything is more than half covered by what follows it.
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
        // The palms switch, read the same way the wallpaper reads it: with it off, those two
        // themes' tree slots draw the ordinary tree, so the card has to show that and not palms.
        val palms = theme.id in PALM_THEMES && c.palmsEnabled

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
        val lake = lakeBand(c, night)

        val backdrop = mutableListOf<PreviewItem>()
        val items = mutableListOf<PreviewItem>()
        val cars = mutableListOf<PreviewItem>()
        val groundItems = mutableListOf<PreviewItem>()
        val water = mutableListOf<PreviewItem>()
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
            val cloudTint = c.clouds.colorDay
            val heavy = c.precipitation.visible
            backdrop += PreviewItem(70f, 40f, if (heavy) 0.30f else 0.22f,
                listOf(PreviewSprite(R.drawable.cloud_body, -128f, -85f, cloudTint, alpha = 235)))
            backdrop += PreviewItem(206f, 30f, if (heavy) 0.26f else 0.18f,
                listOf(PreviewSprite(R.drawable.cloud_body, -128f, -85f, cloudTint, alpha = 225)))
        }

        // --- birds ------------------------------------------------------------------------------
        // The flock the card never drew. `BirdsConfig.nightBirds` is off in every built-in, so a
        // night card owes none -- and reading the flag rather than the theme's name means a custom
        // theme that turns night birds on gets them here too.
        if (c.birds.visible && (!night || c.birds.nightBirds)) {
            backdrop += PreviewItem(128f, 62f, 0.9f, bird(c))
            backdrop += PreviewItem(148f, 72f, 0.7f, bird(c))
            if (c.birds.density >= 0.4f) backdrop += PreviewItem(112f, 78f, 0.6f, bird(c))
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

        // --- the street, far to near ------------------------------------------------------------
        // The identities are `PreviewIdentity`'s, not invented here: each one is a position the
        // deal reads, and it decides both the silhouette the slot composer produces and which of
        // the category's two colours the building wears. Picking them rather than a colour is the
        // whole difference from the flat-facade version -- see [neighbourhood].
        val cityLike = c.buildings.density >= 0.9f
        // Tundra thins its woodland to a scattering rather than removing it, and that is exactly
        // what its density says; the card reads the same number.
        val sparse = c.trees.density <= 0.25f

        fun tower(x: Float, y: Float, fit: Float, index: Int) = buildingItem(
            x, y, fit, SceneSpace.SceneVariant.TOWER, SceneObjectType.SKYSCRAPER,
            PreviewIdentity.TOWER_X[index], PreviewIdentity.TOWER_DEPTH[index], c, night, winter,
        )

        if (c.buildings.visible) {
            if (cityLike) {
                // A city's skyline is what it is for, so it keeps all four towers.
                for ((index, x) in listOf(40f, 108f, 176f, 250f).withIndex()) {
                    items += tower(x, ROW_TOWERS, if (index % 2 == 0) 0.44f else 0.42f, index)
                }
            } else {
                // Two towers frame the street: the scene's towers are its farthest objects and
                // stand at both ends of the tile, not in a row in the middle.
                items += tower(40f, ROW_TOWERS, 0.38f, 0)
                items += tower(296f, ROW_TOWERS, 0.34f, 2)
            }
            items += buildingItem(128f, ROW_RESTAURANT, 0.38f,
                SceneSpace.SceneVariant.RESTAURANT, SceneObjectType.SKYSCRAPER,
                PreviewIdentity.RESTAURANT_X, PreviewIdentity.RESTAURANT_DEPTH, c, night, winter)
        }
        if (c.houses.visible) {
            items += buildingItem(80f, ROW_HOUSE_LARGE, 0.40f,
                SceneSpace.SceneVariant.HOUSE_LARGE, SceneObjectType.HOUSE,
                PreviewIdentity.HOUSE_LARGE_X, PreviewIdentity.HOUSE_LARGE_DEPTH, c, night, winter)
        }
        if (c.buildings.visible) {
            items += buildingItem(176f, ROW_SCHOOL, 0.40f,
                SceneSpace.SceneVariant.SCHOOL, SceneObjectType.SKYSCRAPER,
                PreviewIdentity.SCHOOL_X, PreviewIdentity.SCHOOL_DEPTH, c, night, winter)
        }
        if (c.houses.visible) {
            items += buildingItem(236f, ROW_HOUSE_SMALL, 0.40f,
                SceneSpace.SceneVariant.HOUSE_SMALL, SceneObjectType.HOUSE,
                PreviewIdentity.HOUSE_SMALL_X, PreviewIdentity.HOUSE_SMALL_DEPTH, c, night, winter)
        }
        if (c.buildings.visible) {
            items += buildingItem(300f, ROW_BAR, 0.42f,
                SceneSpace.SceneVariant.BAR, SceneObjectType.SKYSCRAPER,
                PreviewIdentity.BAR_X, PreviewIdentity.BAR_DEPTH, c, night, winter)
        }

        // --- trees ------------------------------------------------------------------------------
        if (c.trees.visible) {
            // Two, whatever the density, and one where the woodland is a scattering: a third has
            // no place on this row that does not stand in front of a shop.
            val xs = if (sparse) listOf(262f) else listOf(70f, 262f)
            xs.forEachIndexed { index, x ->
                val leaf = if (c.fallColorsEnabled) FALL_LEAF_COLOURS[index % FALL_LEAF_COLOURS.size] else c.trees.colorDay1
                val parts = when {
                    palms -> palmTree(dead = halloween, frost = winter)
                    // Christmas is the theme that puts firs among the trees.
                    c.christmasDecorationsEnabled && index % 2 == 0 -> fir(snow = winter, lights = true)
                    sparse -> fir(snow = winter, lights = false)
                    else -> tree(leaf, winter = winter, halloween = halloween)
                }
                items += PreviewItem(
                    x,
                    if (palms) ROW_TREES + 4f else ROW_TREES,
                    if (palms) 0.44f else 0.38f,
                    parts,
                )
            }
        }

        // --- people -----------------------------------------------------------------------------
        if (c.people.visible) {
            val kinds = listOf("man", "woman", "girl")
            listOf(118f, 142f, 268f).forEachIndexed { index, x ->
                items += PreviewItem(x, ROW_PEOPLE, 0.34f, person(kinds[index], winter, (index + 1) % 3))
            }
        }

        // --- cars -------------------------------------------------------------------------------
        if (c.cars.visible) {
            // The traffic the scene really carries: it keeps ten vehicles at the default density
            // and one in ten of them is the appliance, which is the loudest thing on the road and
            // was the one body the card never showed.
            cars += PreviewItem(60f, ROW_CARS, 0.42f, car(CarShell.SALOON, c.cars.colorDay1))
            cars += PreviewItem(160f, ROW_CARS, 0.40f, fireTruck())
            cars += PreviewItem(262f, ROW_CARS, 0.42f, car(CarShell.ESTATE, c.cars.colorDay2))
        }

        // --- what is on the water ---------------------------------------------------------------
        if (c.lake.visible) lakeLife(c, lake, water)

        // --- the ground, then what stands on it ---------------------------------------------------
        // **Wildflowers first.** `SceneObjectRenderer.drawGroundFlowers` says of itself that it
        // draws "before anything that stands on it", and the card had them last -- after every
        // seasonal decoration. No built-in showed it, because Spring is the only theme with
        // flowers on and it has no snowmen, eggs or pumpkins; a customization that turns both on
        // buried the decoration under a clump of grass, and the rule below caught it.
        if (c.flowersEnabled) {
            // Which clump, from the renderer's own function rather than from a copy of its rule:
            // the card is the one place a user sees the two side by side as they flip the seasonal
            // palette, so a preview drawing the blooming clump under autumn leaves would be
            // advertising a scene the wallpaper does not draw.
            val flowers = SceneObjectRenderer.groundFlowerSprite(c)
            var seed = theme.id.hashCode() xor 0x5EED
            repeat(10) { i ->
                seed = seed * 1103515245 + 12345
                val x = 12f + i * 30f + ((seed ushr 9) % 12)
                val y = ROW_GROUND - ((seed ushr 5) % 10)
                groundItems += PreviewItem(x, y, 0.95f, listOf(PreviewSprite(flowers, -18f, -12f)))
            }
        }
        if (c.snowmen.visible) {
            groundItems += PreviewItem(52f, ROW_GROUND, 0.56f, snowman(c.snowmen.colorDay1))
            if (c.snowmen.density >= 0.45f) groundItems += PreviewItem(DECOR_RIGHT_X, ROW_GROUND, 0.50f, snowman(c.snowmen.colorDay2))
        }
        if (c.gifts.visible) {
            groundItems += PreviewItem(200f, ROW_GROUND, 0.55f, gift(c.gifts.colorDay1))
            groundItems += PreviewItem(222f, ROW_GROUND, 0.48f, gift(c.gifts.colorDay2))
            groundItems += PreviewItem(60f, ROW_GROUND, 0.50f, gift(c.gifts.colorDay1))
        }
        if (c.penguins.visible) {
            // 120 and 148 stood on the two adults at 118 and 142 and covered one of them by two
            // thirds; 72 and 98 stand beside them.
            groundItems += PreviewItem(72f, ROW_GROUND + 2f, 0.60f, penguin(c.penguins.colorDay1))
            groundItems += PreviewItem(98f, ROW_GROUND + 2f, 0.54f, penguin(c.penguins.colorDay2))
            groundItems += PreviewItem(236f, ROW_GROUND, 0.50f, penguin(c.penguins.colorDay1))
        }
        if (c.bunnies.visible) {
            groundItems += PreviewItem(50f, ROW_GROUND, 0.60f, bunny(c.bunnies.colorDay1))
            groundItems += PreviewItem(DECOR_RIGHT_X, ROW_GROUND, 0.52f, bunny(c.bunnies.colorDay2))
        }
        if (c.easterEggs.visible) {
            groundItems += PreviewItem(196f, ROW_GROUND, 0.55f, easterEgg(c.easterEggs.colorDay1))
            groundItems += PreviewItem(218f, ROW_GROUND, 0.48f, easterEgg(c.easterEggs.colorDay2))
            groundItems += PreviewItem(92f, ROW_GROUND, 0.46f, easterEgg(c.easterEggs.colorDay1))
        }
        if (c.pumpkins.visible) {
            groundItems += PreviewItem(52f, ROW_GROUND, 0.56f, pumpkin(c.pumpkins.colorDay1))
            groundItems += PreviewItem(204f, ROW_GROUND, 0.50f, pumpkin(c.pumpkins.colorDay2))
            groundItems += PreviewItem(DECOR_RIGHT_X, ROW_GROUND, 0.48f, pumpkin(c.pumpkins.colorDay1))
        }
        // Parasols are deliberately absent. The renderer draws them procedurally -- there is no
        // parasol sprite in the library -- and standing in a differently-shaped sprite would be a
        // preview showing something the scene does not contain, which is the one thing this file
        // must not do. `ThemePreviewTruthTest` carries the same exception, named.
        if (theme.hasFireworks) {
            backdrop += PreviewItem(212f, 46f, 0.55f, listOf(PreviewSprite(R.drawable.firework, -40f, -40f, 0xFFFFD166.toInt())))
            backdrop += PreviewItem(258f, 66f, 0.42f, listOf(PreviewSprite(R.drawable.firework, -40f, -40f, 0xFFEF7DA8.toInt())))
            backdrop += PreviewItem(160f, 70f, 0.34f, listOf(PreviewSprite(R.drawable.firework, -40f, -40f, 0xFF8AD6F0.toInt())))
        }
        // The sleigh, for the same reason the fireworks are three lines above it.
        //
        // Both are periodic flybys on a random interval rather than populations, and the card drew
        // one and not the other; `ThemePreviewTruthTest` exempted the sleigh and named the
        // inconsistency in the exemption's own text. The two cannot both be right, and this is the
        // half that was missing: `santaEnabled` is a plain per-theme flag with no hour and no theme
        // gate on it -- `PaperRenderer` hands it straight to `SantaSleighEffect.update` -- so a day
        // card carrying it says exactly what the scene does.
        //
        // Read from the flag, not from the theme's name: `defaultCustomizationFor` seeds it from
        // `theme.hasSantaSleigh` (Christmas alone), but the Seasons screen's switch reaches every
        // theme, and a saved theme that turns it on gets the sleigh here too.
        //
        // **Placed where the effect flies it and where the sky is free.** `startFlight` picks
        // `screenHeight * (0.10..0.26)`, which is 24..62 on this 240-unit canvas; 52 is inside that
        // band. 0.26 puts 51 of the card's 320 units across it -- a shade under the near cloud's
        // 58 -- and the span 116..167 is the gap between the two clouds, clear of the sun on the
        // right and a unit clear of the nearest gull below. Fixed art, untinted, like the scene's.
        //
        // The two origins are `PaperRenderer`'s own, referenced rather than re-typed -- the same
        // way the dolphin takes hers. -99.67 is knowingly not the centre of the drawing and that
        // KDoc explains why; a copy here would be a second place for that to be "corrected".
        // `santa_sleigh_scene` is the still frame of the two the scene alternates.
        if (c.santaEnabled) {
            backdrop += PreviewItem(
                142f, 52f, 0.26f,
                listOf(
                    PreviewSprite(
                        R.drawable.santa_sleigh_scene,
                        PaperRenderer.SANTA_SLEIGH_ORIGIN_X_UNITS,
                        PaperRenderer.SANTA_SLEIGH_ORIGIN_Y_UNITS,
                    ),
                ),
            )
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
            lake = lake,
            hasLake = c.lake.visible,
            hasRoad = true,
            roadColour = if (night) 0xFF24242C.toInt() else 0xFF3A3A40.toInt(),
            backdrop = backdrop,
            items = items,
            cars = cars,
            ground = groundItems,
            dots = dots,
            water = water,
        )
    }

    /**
     * The card's water band, anchored the way `PaperRenderer.updateLakeBandY` anchors the scene's.
     *
     * Bottom fixed, growing upward with the height the user sets. The version this replaced pinned
     * the *top* to the horizon and grew downward, which put the surface in the right place only at
     * full height and everywhere else left the lake's life below its own floor.
     */
    private fun lakeBand(c: SceneCustomization, night: Boolean): PreviewBand {
        val height = c.lake.height.coerceIn(0.1f, 1f)
        return PreviewBand(
            top = CARD_LAKE_BOTTOM_UNITS - CARD_LAKE_FULL_HEIGHT_UNITS * height,
            bottom = CARD_LAKE_BOTTOM_UNITS,
            colour = if (night) c.lake.colorNight else c.lake.colorDay,
        )
    }

    /**
     * A boat and a dolphin, placed the way `PaperRenderer.gatherLakeDecorations` places them: on
     * lanes that are **fractions of the band's own height**, at a scale the band can hold.
     *
     * One of each, because one of each is what the pool deals at the default densities
     * (`CandidateThreshold` keeps 1 of the 6 sailboat candidates and 1 of the 6 dolphins in every
     * built-in that has them). The scale is derived from the band rather than fixed, so a lake a
     * user shrinks to a tenth gets a boat that still fits inside it; the caps stop a full-height
     * sea from showing a dolphin the size of the bar.
     */
    private fun lakeLife(c: SceneCustomization, band: PreviewBand, into: MutableList<PreviewItem>) {
        val height = band.bottom - band.top
        val open = c.lake.height >= OPEN_WATER_HEIGHT
        if (c.lake.sailboatsVisible) {
            val scale = (height / 60f).coerceIn(0.14f, if (open) 0.30f else 0.24f)
            // The hull's ink runs from oy 8 to oy 25 in the sprite's own space, so its middle is
            // 16.5 units below the item's origin: subtracting that sits the waterline on the lane.
            val lane = band.top + height * 0.38f
            into += PreviewItem(if (open) 130f else 13f, lane - 16.5f * scale, scale, sailboat())
        }
        if (c.lake.dolphinsVisible) {
            val scale = (0.75f * height / 57f).coerceIn(0.12f, if (open) 0.30f else 0.20f)
            val lane = band.top + height * 0.42f
            into += PreviewItem(if (open) 200f else 207f, lane - scale, scale, dolphin())
        }
    }

    /**
     * The dolphin, at the renderer's origin rather than at a copy of an older one.
     *
     * The literals here were `(-56.3, -28)`, the pair v4.31 measured as **(+0.87, +1.0) units off
     * the animal's centre** and replaced in `PaperRenderer`; `DolphinLeapOriginTest` asserts the
     * renderer is rid of them, and the card was still carrying them. Reading the constants is the
     * fix that cannot drift again.
     */
    private fun dolphin() = listOf(
        PreviewSprite(R.drawable.dolphin_body, PaperRenderer.DOLPHIN_ORIGIN_X_UNITS, PaperRenderer.DOLPHIN_ORIGIN_Y_UNITS),
    )

    /**
     * A gull, in the heaviest of the theme's own bird colours.
     *
     * The offsets are the centre of `bird_body`'s 51x21 px canvas in scene units -- (8.5, 3.5) --
     * because the renderer centres the same sprite on that axis to mirror the wing-flap about it.
     * The card has no flap, so it needs the centre and nothing else. The size is pinned by
     * `SpriteMeasurementClaimTest`.
     */
    private fun bird(c: SceneCustomization): List<PreviewSprite> {
        val colour = c.birds.colors.maxByOrNull { it.weight }?.color ?: 0xFFFFFFFF.toInt()
        return listOf(PreviewSprite(R.drawable.bird_body, -8.5f, -3.5f, colour))
    }

    /**
     * The fire appliance, at `SceneObjectRenderer.drawFireTruck`'s own two origins, ladder first.
     *
     * Without the beacons and the lamps: those are the two rectangles and two lenses the renderer
     * brightens on the night ramp, and the card has no clock -- the same reason it leaves out
     * porch lights and window occupants.
     */
    private fun fireTruck(): List<PreviewSprite> = listOf(
        PreviewSprite(
            R.drawable.firetruck_ladder,
            SceneObjectRenderer.FIRE_TRUCK_LADDER_X_UNITS, SceneObjectRenderer.FIRE_TRUCK_LADDER_Y_UNITS,
        ),
        PreviewSprite(
            R.drawable.firetruck_body,
            SceneObjectRenderer.FIRE_TRUCK_BODY_X_UNITS, SceneObjectRenderer.FIRE_TRUCK_BODY_Y_UNITS,
        ),
    )

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

    /**
     * Where the card's buildings stand, as far as the deal is concerned.
     *
     * A building's silhouette and its colour both come from `(tileFractionX, depthFraction)` --
     * the slot composer's choices and `variantIndexFor`'s -- so a preview that wants to show a
     * turret rather than a gable asks for a position, not for a turret.
     *
     * They were chosen by dealing them: `spire, dome, spire, dome` across the four towers and one
     * of each category colour on each row, which is what a skyline is for. Change one and the
     * card changes -- `ThemePreviewSceneTest` pins what each currently deals, so the change is
     * visible rather than silent.
     *
     * ### What these are, and what they are not
     *
     * They are **positions, dealt for a picture**. The paragraph that stood here said they were
     * "positions buildings of their family really occupy in a built-in layout (the restaurant's
     * and the bar's are the ones `SceneObjectCatalog` emits per tile)", and a census of all twelve
     * built-ins says otherwise: the catalogue emits the restaurant at depth **0.4444** and the bar
     * at **0.8000**, in every theme, and its towers never go past **0.2667**. So six of the ten
     * numbers below -- both shop depths and three of the four tower depths -- are positions no
     * building of that family stands at. Nothing is wrong with the *card* that follows from them
     * (a dealt silhouette is a real silhouette wherever the hash lands), but the derivation was
     * not what it claimed, and a future edit "restoring" them to the catalogue would move every
     * shop on every card. Whether they should be the catalogue's is a question for a round that
     * can look at the twelve cards it changes; this one only stops the sentence being false.
     *
     * [SCHOOL_DEPTH] is the exception, and is measured: the catalogue puts the school at 0.6222
     * in all twelve, so the card can stand it exactly where the scene does. Its x is the one
     * Tundra's school gets, picked the same way the other nine were.
     */
    internal object PreviewIdentity {
        val TOWER_X = floatArrayOf(0.0521f, 0.1319f, 0.44f, 0.2118f)
        val TOWER_DEPTH = floatArrayOf(0.2667f, 0.4618f, 0.66f, 0.7111f)
        const val RESTAURANT_X = 0.1319f
        const val RESTAURANT_DEPTH = 0.4618f
        const val SCHOOL_X = 0.4458f
        const val SCHOOL_DEPTH = 0.6222f
        const val BAR_X = 0.2118f
        const val BAR_DEPTH = 0.7111f
        const val HOUSE_LARGE_X = 0.2986f
        const val HOUSE_LARGE_DEPTH = 0.785f
        const val HOUSE_SMALL_X = 0.3786f
        const val HOUSE_SMALL_DEPTH = 0.785f
    }

    // --- object part lists, offsets as in SceneObjectRenderer ---------------------------------

    /**
     * A building of the neighbourhood, dealt from the same table the wallpaper deals from.
     *
     * ### Why this one is not "offsets as in SceneObjectRenderer"
     *
     * Every other builder below is a hand copy of a call site in `SceneObjectRenderer`, and
     * `PreviewRendererAgreementTest` exists because hand copies drift -- it was written when the
     * winter tree's snow cap had been sitting three units right and two down of the wallpaper's
     * for two releases. A stack of pieces chosen per instance is far more than a snow cap's worth
     * of arithmetic to copy, so it is not copied: [NeighbourhoodComposer] deals it and this reads
     * out the result.
     *
     * The two callers then differ in exactly one thing, which is what they *are*: the wallpaper
     * deals from the building's own position in the scene, the preview from a position it picks
     * for a picture. Give both the same position and they produce the same silhouette in the same
     * colour -- there is no second copy that could disagree, and the agreement test says so
     * directly rather than comparing two lists of literals.
     *
     * ### Two things the preview leaves out, and why
     *
     * `OCCUPANTS` and `LAMP` are call-outs to behaviours that animate (`WindowOccupants` picks a
     * bust from the scene's own people, a porch light glows on the night ramp). The preview is a
     * still 320x240 card with no people layer and no clock; it has never drawn either, and
     * drawing them here would mean giving the card both. Snow is drawn, because `winterColorsEnabled`
     * is a palette the user edits and a preview that cannot show it is not much of a preview.
     */
    private fun neighbourhood(
        variant: SceneSpace.SceneVariant,
        type: SceneObjectType,
        tileX: Float,
        depth: Float,
        c: SceneCustomization,
        night: Boolean,
        snow: Boolean,
    ): List<PreviewSprite> {
        val family = NeighbourhoodTable.FAMILIES[variant] ?: return emptyList()
        // The real object, so the real `colorFor`: which of the category's two colours this
        // building wears is `variantIndexFor`'s answer about this very position, not a choice the
        // preview makes. The preview used to invent its own blends here -- a tower 15 % towards
        // white, a restaurant 30 % -- which showed the user a colour no building of theirs would
        // ever be.
        val spec = StaticSceneObject(type, depthFraction = depth, tileFractionX = tileX)
        val dayBlend = if (night) 0f else 1f
        val wall = c.colorFor(spec, dayBlend)
        val glass = SceneObjectRenderer.windowGlassColor(if (night) 1f else 0f)
        val deal = NeighbourhoodComposer.Deal()
        NeighbourhoodComposer.deal(family, tileX, depth, deal)
        val parts = mutableListOf<PreviewSprite>()
        for (index in 0 until deal.size) {
            val placed = deal[index]
            for (part in placed.piece.parts) {
                val y = placed.baseY + part.y
                when (part.role) {
                    PartRole.FIXED -> parts += PreviewSprite(part.res, part.x, y)
                    PartRole.WALL_MASK -> parts += PreviewSprite(part.res, part.x, y, wall, added = true)
                    PartRole.GLASS_MASK -> parts += PreviewSprite(part.res, part.x, y, glass, added = true)
                    PartRole.SNOW -> if (snow) parts += PreviewSprite(part.res, part.x, y)
                    PartRole.LAMP, PartRole.OCCUPANTS -> Unit
                }
            }
        }
        return parts
    }

    /**
     * The scale a dealt building is drawn at in the card.
     *
     * The pieces are authored in the family's own units and the wallpaper scales them by
     * `variant.spriteUnitsTall / family.unitsTall` before it blits; the card has no nested scale,
     * so the same factor is folded into the item's own. [fit] is what it always was -- how much
     * of the 320x240 card this object may take -- and it is unchanged from the flat-facade
     * version, because the old artwork's local height *was* `spriteUnitsTall`.
     */
    private fun neighbourhoodScale(variant: SceneSpace.SceneVariant, fit: Float): Float {
        val family = NeighbourhoodTable.FAMILIES[variant] ?: return fit
        return fit * variant.spriteUnitsTall / family.unitsTall
    }

    private fun buildingItem(
        x: Float,
        y: Float,
        fit: Float,
        variant: SceneSpace.SceneVariant,
        type: SceneObjectType,
        tileX: Float,
        depth: Float,
        c: SceneCustomization,
        night: Boolean,
        snow: Boolean,
    ): PreviewItem = PreviewItem(
        x, y, neighbourhoodScale(variant, fit),
        neighbourhood(variant, type, tileX, depth, c, night, snow),
    )

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

    /**
     * v4.21 moved both of these, and they are the reason this function is worth a comment.
     *
     * The fir's own origin went from (-39,-122) to (-40,-122) because the new sprite is 80 units
     * wide rather than 78. The snow's went from the fir's origin to (-28,-112) of its own, because
     * `tree_fir_snow` is no longer a full-canvas copy of the fir: it is trimmed to its content and
     * carries the offset of the cut. **The two are one derivation** — the same pair of numbers
     * appears in `SceneObjectRenderer.drawFir`, and a redraw that moves one and not the other
     * slides the snow off the shoulders it was cut for.
     *
     * The baubles below are left where they were: they are `star_sparkle` blits placed by eye on
     * a flat 320x240 card, not the wallpaper's `drawChristmasLights` ellipse, and this release
     * changes no decoration artwork.
     */
    private fun fir(snow: Boolean, lights: Boolean): List<PreviewSprite> {
        val parts = mutableListOf(PreviewSprite(R.drawable.tree_fir, -40f, -122f))
        if (snow) parts += PreviewSprite(R.drawable.tree_fir_snow, -28f, -112f)
        if (lights) {
            parts += PreviewSprite(R.drawable.star_sparkle, -26f, -110f, 0xFFF2C14E.toInt(), alpha = 220)
            parts += PreviewSprite(R.drawable.star_sparkle, 2f, -84f, 0xFFE8483C.toInt(), alpha = 220)
            parts += PreviewSprite(R.drawable.star_sparkle, -22f, -58f, 0xFF5BC0EB.toInt(), alpha = 220)
        }
        return parts
    }

    /**
     * The same two blits [SceneObjectRenderer.drawPalmTree] makes, at the same two origins.
     *
     * v5.1 moved both and turned the frost from an overlay into a third crown, so the `when` here
     * mirrors that one rather than stacking: all three crowns share one canvas and one declared
     * attachment, and choosing between them is the whole of it. Both origins come from
     * [PalmSpriteLayout], which the renderer reads too, so a redraw moves both sides or neither.
     *
     * **No shade, and that is consistent rather than an omission.** The wallpaper's palm dims with
     * the hour as of v5.1; this one does not, because *nothing in this row does* -- the leafy tree
     * two branches up is drawn with `c.trees.colorDay1` whether or not the card is a night one, so
     * a palm that dimmed here would be the one plant on a night card that had noticed. Whether the
     * tree line should read its night colours at all is a question about this file, not about the
     * palm, and it is older than this release.
     */
    private fun palmTree(dead: Boolean, frost: Boolean): List<PreviewSprite> = listOf(
        PreviewSprite(R.drawable.palmtree_trunk, PalmSpriteLayout.TRUNK_X, PalmSpriteLayout.TRUNK_Y),
        PreviewSprite(
            when {
                dead -> R.drawable.palmtree_fronds_dead
                frost -> R.drawable.palmtree_fronds_frost
                else -> R.drawable.palmtree_fronds
            },
            PalmSpriteLayout.CROWN_X, PalmSpriteLayout.CROWN_Y,
        ),
    )

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
                shell.glassRes, shell.glassSpriteXUnits,
                oy + (SceneObjectRenderer.CAR_GLASS_SPRITE_Y_UNITS - shell.bodyYUnits),
            ),
        )
    }

    private fun sailboat() = listOf(
        // v4.31: the same compensation the wallpaper's own blit carries -- see
        // `PaperRenderer.drawLakeSailboats`. The preview is a second call site for every one of
        // these three sprites, and `normalize --apply` reported "single call site" for all
        // three because it could not resolve them; compensating only the renderer would have
        // left the gallery card's boats shifted.
        PreviewSprite(R.drawable.sailboat_sail, -27f, -50f),
        PreviewSprite(R.drawable.sailboat_hull, -40f, 8f),
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
    /**
     * The preview's own name for the scene's blend, kept because a hundred call sites read better
     * with it. The arithmetic moved to [SceneColour] in v5.0: this file used to *define* it, which
     * made it a second answer to "what is a colour between two colours" living beside the
     * renderer's.
     */
    private fun blendRgb(from: Int, to: Int, ratio: Float): Int = SceneColour.blendArgb(from, to, ratio)
}
