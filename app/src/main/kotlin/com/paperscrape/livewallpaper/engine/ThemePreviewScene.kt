package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * One blit of a preview object: the resource, the offset the renderer blits it at, and the tint
 * (null for fixed art). Offsets are in scene units and come from `SceneObjectRenderer`'s own draw
 * functions (its constants where it has them, copied where it does not), so a preview house is
 * assembled from the parts, at the positions, the wallpaper assembles one from.
 *
 * [added] is the people's and the neighbourhood's blend: the mask is **summed** into the frame over
 * a fixed layer, which is what makes a weight mask interpolate; drawn with a plain tint it would
 * cover the ink it is added to (`SpriteBlitter.drawTintedAdded`).
 *
 * [shade] is how lit a piece of fixed art is, the neutral grey `SpriteBlitter.draw` multiplies it
 * by, only for a part with no [tint]: the palm and the fir, which the wallpaper shades rather than
 * tints (`SceneObjectRenderer.drawPalmTree`, `drawFir`, [nightShadeFor]). Full daylight, the default,
 * draws the art as authored.
 *
 * A part with a [rectWidth] is no sprite: a flat card [rectWidth] x [rectHeight] at ([ox], [oy]) in
 * [tint], a house's lit window over its dark glass at night (`SceneObjectRenderer.drawLitPanes`) or a
 * shut building's blinds (`SceneObjectRenderer.drawShut`). A part with an [outline] is a flat card of
 * that shape in [tint], its points `x0, y0, x1, y1, ...` in the item's units: a band of a shut
 * building's shutter, cut from its door's outline as the wallpaper cuts it ([DoorShutter.band]), or a
 * tower's star. A part with a [discRadius] is a disc centred on ([ox], [oy]) in [tint] at [alpha], a
 * Christmas bulb, drawn as the wallpaper's preview draws one (`SpriteBlitter.drawDisc`, a circle on a
 * Canvas). [resId] is 0 for the three ([isFlat]).
 *
 * [scale] draws a sprite that many times its size from ([ox], [oy]): a tower's wreath, half as large
 * again as a house's (`SceneObjectRenderer.drawWreath`), and the card's decorations a little larger
 * again (`decorations`). [decoration] marks the parts of the Christmas decorations: the bulbs, the
 * stars and the wreaths, not the building's own paper nor its shut sign.
 */
data class PreviewSprite(
    val resId: Int,
    val ox: Float,
    val oy: Float,
    val tint: Int? = null,
    val alpha: Int = 255,
    val added: Boolean = false,
    val shade: Int = SpriteBlitter.UNTINTED,
    val rectWidth: Float = 0f,
    val rectHeight: Float = 0f,
    val outline: List<Float>? = null,
    val discRadius: Float = 0f,
    val scale: Float = 1f,
    val decoration: Boolean = false,
    /** A sprite drawn mirrored left to right about its origin, at [scale]: the right-hand cobweb of a roof. */
    val mirrored: Boolean = false,
) {
    /** A flat card -- a rectangle, an outline or a disc -- and not a sprite. */
    val isFlat: Boolean get() = rectWidth > 0f || outline != null || discRadius > 0f
}

/** An object standing at [x] on the ground line [y], drawn at [scale]. */
data class PreviewItem(val x: Float, val y: Float, val scale: Float, val parts: List<PreviewSprite>)

/**
 * One mountain: the peak at ([x], [peakY]) and the base [halfWidth] either side of it, on the
 * card's ground line. The painter gives it the wallpaper's own silhouette -- the parabolic arch of
 * [MountainSilhouette] -- in every theme; there is no desert variant, because the wallpaper has none.
 */
data class PreviewPeak(
    val x: Float,
    val peakY: Float,
    val halfWidth: Float,
    val colour: Int,
    /**
     * The distant houses standing on this mountain, painted right after it and before the next one,
     * as the wallpaper paints them: empty unless the theme turns them on above 0 %.
     */
    val houses: List<PreviewItem> = emptyList(),
)

/** A horizontal band of water. */
data class PreviewBand(val top: Float, val bottom: Float, val colour: Int)

/**
 * A single filled dot: a star, a snowflake, a raindrop, a falling leaf. [front] is where it is
 * painted: in the sky behind everything (a star), or over the whole card (rain, snow and falling
 * leaves, which the wallpaper draws last). Said here, not read from the radius: rain and snow are
 * smaller than a leaf and still fall in front of the town.
 */
data class PreviewDot(
    val x: Float,
    val y: Float,
    val radius: Float,
    val colour: Int,
    val alpha: Int = 255,
    val front: Boolean = false,
)

/**
 * Everything one theme's gallery preview draws, in the order it draws it: plain data with no
 * Android type beyond resource ids, so what a preview contains is testable without a `Canvas`.
 * `ThemeScenePreview` in `ui/ThemePreview.kt` is the only thing that puts it on screen.
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
     * What floats on the lake, drawn in a pass of its own right after the water. Not in [backdrop],
     * which the painter splits at the horizon (above it behind the hills, below in front), while the
     * water can sit on either side: Beach's sea starts above the horizon, and a boat in [backdrop]
     * there would be painted under the water.
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
 * The one place a preview's size on screen is decided. Every place that shows a preview (the gallery
 * card, the settings home card and the strip at the top of World & scene) takes its scale from this
 * and sizes itself to [ASPECT_RATIO], so none grows fitting factors of its own, another aspect ratio,
 * or a crop that hides the pavement, the road and the cars.
 */
object ThemePreviewGeometry {

    /** 4:3. The scene is composed once, at one shape, and never cropped to fit a container. */
    const val ASPECT_RATIO = ThemePreviewScene.WIDTH_UNITS / ThemePreviewScene.HEIGHT_UNITS

    /**
     * Scene units to pixels for a container [widthPx] wide, the same factor on both axes, so nothing
     * is stretched. A container holds the whole scene at [ASPECT_RATIO], [heightFor] high.
     */
    fun scaleFor(widthPx: Float): Float = widthPx / ThemePreviewScene.WIDTH_UNITS

    /** The height a container [widthPx] wide must have to show the whole scene. */
    fun heightFor(widthPx: Float): Float = widthPx / ASPECT_RATIO
}

/**
 * Whether the wallpaper draws any of a thing at all: switched on **and above 0 %**, since at a
 * density, an intensity or a number of 0 the wallpaper draws none and the switch reads off. Not for
 * the buildings, whose shops stay at 0 %, nor the cars, one of which drives, nor the people, who have
 * their night density: those keep their switch alone, as the wallpaper does.
 */
private fun drawn(visible: Boolean, amount: Float): Boolean = visible && amount > 0f

/**
 * Builds a theme's preview scene out of the customization the wallpaper would draw it with.
 *
 * **Nothing here decides what a theme contains.** Every object is conditional on the flags the
 * wallpaper reads (`lake.drawsWater`, `snowmen.visible` with its density ([drawn]),
 * `winterColorsEnabled`, `halloweenEnabled`, `mountainsFront.visible` and so on), a fir included
 * ([SceneObjectRenderer.drawsFirs]). The gallery passes what
 * `CustomThemeRegistry.resolveActiveCustomization` resolves for the theme, as the wallpaper does.
 *
 * **Nor does anything here decide a colour or a shape of the landscape.** The sky comes from
 * [SkyGradient], the road from `SceneObjectRenderer.roadColor` and `drawsRoad`, every other day/night
 * pair is blended on the same `dayBlend`, and whether trees are palms from the layout's own slots, all
 * at a real moment of the wallpaper's day ([cardPhase]); `ThemePreviewTruthTest`'s R4 holds it.
 *
 * What lives here is *composition*: the real scene spreads hundreds of objects over a screen five
 * times this wide, and shrunk that is a grey mush, so a preview is a dozen objects, one per family,
 * far to near: towers, restaurant, large house, school, small house, bar, then the trees, the
 * pavement and the road. It follows the scene's shop depths, not its houses, which stand in two
 * bands ([forTheme]).
 */
object ThemePreviewScenes {

    /**
     * The depth rows the card's street is built on, far to near. The three building rows follow the
     * scene's rows of the size table (`SceneSpace`): the card's places are the nearer place of each
     * row on all twelve built-ins (0.4444, 0.6222 and 0.8000), and which building stands in a place is
     * the street's ([StorefrontStreet]); the towers are on the skyline (0.00-0.27). The scene's houses
     * stand in two bands (0.28-0.48 and 0.62-0.95), so the card's two house rows are a choice. A row is
     * a ground line: an object on a nearer row is appended later and drawn in front. The bar, the
     * nearest shop, stands in front of the small house, not hidden behind it.
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
     * Where the card's right-hand seasonal decoration stands: in front of the small house, the next
     * gap left of the bar, so a snowman, a pumpkin or a rabbit never covers the bar on the right edge.
     */
    private const val DECOR_RIGHT_X = 232f

    /**
     * The water's lower edge, and how far it climbs at full height. As in
     * `PaperRenderer.updateLakeBandY`, the lake's bottom is anchored to the ground and it grows
     * upward, so the boats and dolphins, placed from the surface, stay in the water at every height.
     * 164 is the ground line the towers stand behind; 56 takes the surface at full height to 108, 42
     * units above the card's horizon ([ThemePreviewScene.HORIZON_UNITS]) and among the back
     * mountains' peaks (104..118).
     *
     * Named `CARD_` and not `LAKE_` on purpose: these are lengths on the card's own 320x240
     * canvas, not lengths of the lake's sprites, and `UnitFrameTest`'s prefix table resolves
     * `LAKE_` to the sprite frame.
     */
    private const val CARD_LAKE_BOTTOM_UNITS = 164f
    private const val CARD_LAKE_FULL_HEIGHT_UNITS = 56f

    /** A lake this tall is open water rather than a pond, and its life gets the middle of it. */
    private const val OPEN_WATER_HEIGHT = 0.8f

    private const val ROAD_TOP = 207f
    private const val ROAD_BOTTOM = 229f

    /** See [car]: where a wheelless preview car's painted floor sits. */
    internal const val PREVIEW_CAR_FLOOR_DROP = 28f

    /**
     * Where the card's two bodies stop: the last row of ink of `car_body_saloon` and
     * `car_body_estate` is row 129, so the ink ends 130 / 3 units below the blit origin at
     * `bodyYUnits` = -17, which is 26.33 (`car_body_compact` ends at 26.00 and is not on any card).
     * `ThemePreviewTruthTest` R5 measures it off the PNGs, so a redrawn body that moves its last row
     * fails there instead of floating above the floor [PREVIEW_CAR_FLOOR_DROP] gives it.
     */
    private const val CAR_PAINTED_FLOOR_UNITS = -17f + 130f / 3f

    /**
     * How wide the card's sleigh is, in the card's 320 units: the size the maintainer kept for it
     * (2026-10-19: *«Scheda slitta: lascia così»*).
     */
    const val SLEIGH_CARD_WIDTH = 51f

    /**
     * Where the card's Headless Horseman flies when Santa's sleigh is on the card too: lower on the sky's right,
     * under the far cloud and clear of the Halloween moon, where the two groups do not cover each other.
     */
    const val HORSEMAN_CARD_X_BESIDE_SANTA = 194f
    const val HORSEMAN_CARD_Y_BESIDE_SANTA = 60f

    /**
     * The card's scarecrow: at the Easter eggs' third point, between the pumpkins, at their scale, its drawing brought to
     * a snowman's height in units ([scarecrow]), so a little shorter than the card's snowmen (0.56 and 0.50), as the
     * maintainer was shown it.
     */
    const val SCARECROW_CARD_X = 92f
    const val SCARECROW_CARD_SCALE = 0.46f

    /**
     * The clock hours the card shows, on the default day `SunPositionCalculator.compute` assumes
     * (sunrise 06:00, sunset 20:00): noon for a day card, midnight for a night one, and for Sunset,
     * the one theme named after a moment of the day, 19:00, an hour before sunset with the sun low.
     * Whole hours on purpose: *Weather & time*'s fixed-time slider can set them, so the wallpaper can
     * be put at the card's moment and the two compared.
     */
    internal const val CARD_DAY_HOUR = 12f
    internal const val CARD_NIGHT_HOUR = 0f
    internal const val CARD_DUSK_HOUR = 19f

    /** The clock hour a card for [theme] shows: see [CARD_DUSK_HOUR]. */
    internal fun cardHour(theme: SceneTheme, night: Boolean): Float = when {
        night -> CARD_NIGHT_HOUR
        theme.id == "sunset" -> CARD_DUSK_HOUR
        else -> CARD_DAY_HOUR
    }

    /** The moment of the day a card for [theme] shows: see [CARD_DUSK_HOUR]. */
    internal fun cardPhase(theme: SceneTheme, night: Boolean): SunPositionCalculator.DayPhase =
        SunPositionCalculator.compute(cardHour(theme, night))

    /**
     * [forceNight] overrides the time of day the theme would otherwise be shown at. The gallery never
     * passes it (a card shows the theme's own hour); the World & scene strip's day/night toggle does,
     * since half the colours edited there are night colours.
     *
     * The road carries the fire appliance, one vehicle in ten of the scene's traffic; gulls fly by day;
     * the lake's life stands on lanes that are fractions of the band. `ThemePreviewTruthTest` runs the
     * census on every built-in and on a custom customization, and fails if a family the scene draws is
     * missing from the card, if the card invents one, if a boat or a dolphin has ink outside the water,
     * or if anything is more than half covered by what follows it.
     */
    fun forTheme(
        theme: SceneTheme,
        customization: SceneCustomization,
        forceNight: Boolean? = null,
        layout: SceneObjectLayout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor),
    ): ThemePreviewScene {
        val c = customization
        val winter = c.winterColorsEnabled
        val halloween = c.halloweenEnabled
        // Night for the two themes whose subject *is* the night: the fireworks theme and the horror
        // sky. Everything else reads its day palette, which is what a gallery is for; but a theme with
        // Halloween's night sky by day in force is night at every hour on the wallpaper, so on its card
        // and on the World & scene strip's "Day" too.
        val night = (forceNight ?: (c.horrorSkyEnabled || theme.hasFireworks)) || c.nightOverDayActive()
        // Every day/night colour below is blended on this one number: the card is one moment of the
        // wallpaper's day, not a mixture.
        val phase = cardPhase(theme, night)
        val dayBlend = phase.dayBlend
        // The card's hour, at which each building is as open as the wallpaper's [BusinessHours] rule
        // makes it, by its group's hours (the towers', the shops' or the public buildings'; the fire and
        // the police station never close), a tower by what it is drawn as and a building among the houses
        // by what it is ([SceneCustomization.opennessFor]). 1 with a group's switch off, the default, so a
        // night card's buildings go dark only for a user who turns opening hours on, as the wallpaper's do.
        val hour = cardHour(theme, night)
        // The theme's seed for who stands at a house's windows, the wallpaper's own (`themeId.hashCode()`
        // in `SceneObjectRenderer`): which of a house's windows the card lights at night follows it.
        val seed = theme.id.hashCode()
        // Palms where the switch puts them, as the wallpaper reads it (`palmsShown`): on a layout that
        // plants palms, with it off the palm slots draw the ordinary tree; on one that plants none, with
        // it on the tree slots draw palms. Asked of the layout, not the theme's name, so a theme saved
        // from Beach keeps its palms.
        val layoutPlantsPalms = layout.hasPalmSlots()
        val palms = c.palmsShown(layoutPlantsPalms)

        val skyTop = if (c.horrorSkyEnabled) {
            SkyGradient.horrorTop(dayBlend)
        } else {
            SkyGradient.top(c.sky, dayBlend)
        }
        val skyBottom = if (c.horrorSkyEnabled) {
            SkyGradient.horrorBottom(dayBlend)
        } else {
            SkyGradient.bottom(c.sky, dayBlend, phase.progress)
        }

        val ground = blendRgb(c.hillsColorNight, c.hillsColorDay, dayBlend)

        val peaks = buildPeaks(c, dayBlend, winter)
        val lake = lakeBand(c, dayBlend)

        val backdrop = mutableListOf<PreviewItem>()
        val items = mutableListOf<PreviewItem>()
        val cars = mutableListOf<PreviewItem>()
        val groundItems = mutableListOf<PreviewItem>()
        val water = mutableListOf<PreviewItem>()
        val dots = mutableListOf<PreviewDot>()

        // --- sun or moon ----------------------------------------------------------------------
        // The moon at night, and by day too on a card with *Halloween* on, only while *Moon* is on, as on
        // the wallpaper (`PaperRenderer.drawCelestialBody`). With *Moon* off such a card has neither moon nor sun.
        if (night || halloween) {
            if (c.moon.visible) {
                // The Halloween moon is the moon in the theme's own moon colour, with the face cut out of it
                // and the light inside the cuts over it, untinted (`PaperRenderer.drawMoonWithPhase`): the
                // light's origin is the wallpaper's, in the card's units.
                val moonSprite = if (halloween) R.drawable.moon_jack_o_lantern else R.drawable.moon_full
                val moon = PreviewSprite(moonSprite, -40f, -40f, c.moon.color)
                backdrop += PreviewItem(
                    if (halloween) 250f else 60f, 44f, 0.55f,
                    if (halloween) {
                        listOf(
                            moon,
                            PreviewSprite(
                                R.drawable.moon_jack_o_lantern_light,
                                PaperRenderer.HALLOWEEN_LIGHT_ORIGIN_X / SpriteBlitter.SPRITE_PIXELS_PER_UNIT,
                                PaperRenderer.HALLOWEEN_LIGHT_ORIGIN_Y / SpriteBlitter.SPRITE_PIXELS_PER_UNIT,
                            ),
                        )
                    } else {
                        listOf(moon)
                    },
                )
            }
        } else if (c.sun.visible) {
            val sunY = if (theme.id == "sunset") 118f else 46f
            val sunX = if (theme.id == "sunset") 262f else 248f
            // Both drawings untinted, as the wallpaper draws them (`PaperRenderer.drawCelestialBody`):
            // finished art, which *Sun Color* would turn near-black for anything but a warm pick. On the
            // wallpaper that colour reaches only the ambient glow around the sun, which the card does not draw.
            backdrop += PreviewItem(
                sunX, sunY, 0.55f,
                listOf(
                    PreviewSprite(R.drawable.sun_glow, -66f, -66f, alpha = 110),
                    PreviewSprite(R.drawable.sun_body, -40f, -40f),
                ),
            )
        }

        // --- clouds ---------------------------------------------------------------------------
        if (drawn(c.clouds.visible, c.clouds.density) && !night) {
            // The wallpaper's pair at the card's moment (`PaperRenderer.drawClouds`, before its storm
            // dimming, which needs a forecast the card does not have).
            val cloudTint = blendRgb(c.clouds.colorNight, c.clouds.colorDay, dayBlend)
            val heavy = drawn(c.precipitation.visible, c.precipitation.intensity)
            backdrop += PreviewItem(70f, 40f, if (heavy) 0.30f else 0.22f,
                listOf(PreviewSprite(R.drawable.cloud_body, -128f, -85f, cloudTint, alpha = 235)))
            backdrop += PreviewItem(206f, 30f, if (heavy) 0.26f else 0.18f,
                listOf(PreviewSprite(R.drawable.cloud_body, -128f, -85f, cloudTint, alpha = 225)))
        }

        // --- birds ------------------------------------------------------------------------------
        // `BirdsConfig.nightBirds` is off in every built-in, so a night card shows none; read from the
        // flag, so a custom theme that turns night birds on gets them here too.
        if (drawn(c.birds.visible, c.birds.density) && (!night || c.birds.nightBirds)) {
            backdrop += PreviewItem(128f, 62f, 0.9f, bird(c))
            backdrop += PreviewItem(148f, 72f, 0.7f, bird(c))
            if (c.birds.density >= 0.4f) backdrop += PreviewItem(112f, 78f, 0.6f, bird(c))
        }

        // --- stars ----------------------------------------------------------------------------
        if (night && c.stars.visible && PaperRenderer.starCountFor(c.stars.density) > 0) {
            var seed = theme.id.hashCode()
            repeat(34) {
                seed = seed * 1664525 + 1013904223
                val x = ((seed ushr 8) % 3160) / 10f
                seed = seed * 1664525 + 1013904223
                val y = ((seed ushr 8) % 1080) / 10f
                // The scene's stars are fixed cream (`PaperRenderer.STAR_POINT_COLOR`), so the card's are too.
                dots += PreviewDot(x, y, 0.55f, PaperRenderer.STAR_POINT_COLOR, alpha = 215)
            }
        }

        // --- the street, far to near ------------------------------------------------------------
        // The identities are `PreviewIdentity`'s: each is a position the deal reads, deciding both the
        // silhouette the slot composer produces and which of the category's two colours the building
        // wears ([neighbourhood]).
        val cityLike = c.buildings.density >= 0.9f
        // A woodland thinned to a scattering (Tundra's), read from the same density as the wallpaper.
        val sparse = c.trees.density <= 0.25f

        fun tower(x: Float, y: Float, fit: Float, index: Int) = buildingItem(
            x, y, fit, SceneSpace.SceneVariant.TOWER, SceneObjectType.SKYSCRAPER,
            PreviewIdentity.TOWER_X[index], PreviewIdentity.TOWER_DEPTH[index], c, dayBlend, winter, hour, seed,
        )
        // The three buildings among the houses: three of the five places of the street's first copy, one in
        // each row of the size table ([StorefrontStreet.firstStretch], [StorefrontStreet.cardPlaceOf]).
        // A street holds six, and the other three come by as the wallpaper scrolls: what the card leaves
        // out (`DESIGN_NOTES.md` §12, decision 65).
        val storefronts = StorefrontStreet.firstStretch(seed, StorefrontStreet.places(layout))
        // **No tower the wallpaper does not draw** (`DESIGN_NOTES.md` §12, decision 45: at 0 % the card
        // draws nothing the wallpaper does not), asked of the wallpaper's own rule.
        val towersDrawn = c.buildings.visible && layout.staticObjects.any {
            it.type == SceneObjectType.SKYSCRAPER && !StorefrontStreet.isPlace(it) && c.keepCandidate(it, layout.densityScheme)
        }

        if (c.buildings.visible) {
            if (!towersDrawn) {
                // The street's buildings stand; no tower does.
            } else if (cityLike) {
                // A city's skyline is what it is for, so it keeps all four towers.
                for ((index, x) in listOf(40f, 108f, 176f, 250f).withIndex()) {
                    items += tower(x, ROW_TOWERS, if (index % 2 == 0) 0.44f else 0.42f, index)
                }
            } else {
                // Two towers frame the street, at the card's two ends; the scene's towers are its
                // farthest buildings and are spread across the whole tile.
                items += tower(40f, ROW_TOWERS, 0.38f, 0)
                items += tower(296f, ROW_TOWERS, 0.34f, 2)
            }
            items += buildingItem(128f, ROW_RESTAURANT, 0.38f,
                SceneSpace.SceneVariant.RESTAURANT, SceneObjectType.SKYSCRAPER,
                PreviewIdentity.RESTAURANT_X, PreviewIdentity.RESTAURANT_DEPTH, c, dayBlend, winter, hour, seed,
                storefronts[0])
        }
        if (drawn(c.houses.visible, c.houses.density)) {
            items += buildingItem(80f, ROW_HOUSE_LARGE, 0.40f,
                SceneSpace.SceneVariant.HOUSE_LARGE, SceneObjectType.HOUSE,
                PreviewIdentity.HOUSE_LARGE_X, PreviewIdentity.HOUSE_LARGE_DEPTH, c, dayBlend, winter, hour, seed)
        }
        if (c.buildings.visible) {
            items += buildingItem(176f, ROW_SCHOOL, 0.40f,
                SceneSpace.SceneVariant.SCHOOL, SceneObjectType.SKYSCRAPER,
                PreviewIdentity.SCHOOL_X, PreviewIdentity.SCHOOL_DEPTH, c, dayBlend, winter, hour, seed,
                storefronts[1])
        }
        if (drawn(c.houses.visible, c.houses.density)) {
            items += buildingItem(236f, ROW_HOUSE_SMALL, 0.40f,
                SceneSpace.SceneVariant.HOUSE_SMALL, SceneObjectType.HOUSE,
                PreviewIdentity.HOUSE_SMALL_X, PreviewIdentity.HOUSE_SMALL_DEPTH, c, dayBlend, winter, hour, seed)
        }
        if (c.buildings.visible) {
            items += buildingItem(300f, ROW_BAR, 0.42f,
                SceneSpace.SceneVariant.BAR, SceneObjectType.SKYSCRAPER,
                PreviewIdentity.BAR_X, PreviewIdentity.BAR_DEPTH, c, dayBlend, winter, hour, seed,
                storefronts[2])
        }

        // --- trees ------------------------------------------------------------------------------
        if (drawn(c.trees.visible, c.trees.density)) {
            // Two, or one where the woodland is a scattering, and a fir and a palm, even in a
            // scattering, where the scene keeps both ([cardTrees]).
            val kinds = cardTrees(c, layout, layoutPlantsPalms, palms, sparse)
            val xs = if (kinds.size == 1) listOf(262f) else listOf(70f, 262f)
            kinds.forEachIndexed { index, kind ->
                val x = xs[index]
                val leaf = if (c.fallColorsEnabled) FALL_LEAF_COLOURS[index % FALL_LEAF_COLOURS.size] else c.trees.colorAt(0, dayBlend)
                val palm = kind == CardTree.PALM
                val parts = when (kind) {
                    CardTree.FIR -> fir(snow = winter, shade = c.trees.nightShadeAt(0, dayBlend))
                    CardTree.PALM -> palmTree(
                        dead = halloween, frost = winter, shade = c.trees.nightShadeAt(0, dayBlend),
                        lights = c.christmasTreeLightsEnabled,
                    )
                    CardTree.TREE -> tree(leaf, winter = winter, halloween = halloween, lights = c.christmasTreeLightsEnabled)
                }
                items += PreviewItem(
                    x,
                    if (palm) ROW_TREES + 4f else ROW_TREES,
                    if (palm) 0.44f else 0.38f,
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
            // The traffic the scene carries: ten vehicles at the default density, one of them the appliance.
            cars += PreviewItem(60f, ROW_CARS, 0.42f, car(CarShell.SALOON, c.cars.colorAt(0, dayBlend)))
            cars += PreviewItem(160f, ROW_CARS, 0.40f, fireTruck())
            cars += PreviewItem(262f, ROW_CARS, 0.42f, car(CarShell.ESTATE, c.cars.colorAt(1, dayBlend)))
        }

        // --- what is on the water ---------------------------------------------------------------
        if (c.lake.drawsWater) lakeLife(c, lake, water)

        // --- the ground, then what stands on it ---------------------------------------------------
        // **Wildflowers first**, as `SceneObjectRenderer.drawGroundFlowers` draws them, before anything
        // that stands on the ground, or a seasonal decoration is buried under a clump of grass.
        if (c.flowersEnabled) {
            // Which clump, from the renderer's own function rather than a copy of its rule, so the
            // card never shows the blooming clump under autumn leaves.
            val flowers = SceneObjectRenderer.groundFlowerSprite(c)
            var seed = theme.id.hashCode() xor 0x5EED
            repeat(10) { i ->
                seed = seed * 1103515245 + 12345
                val x = 12f + i * 30f + ((seed ushr 9) % 12)
                val y = ROW_GROUND - ((seed ushr 5) % 10)
                groundItems += PreviewItem(x, y, 0.95f, listOf(PreviewSprite(flowers, -18f, -12f)))
            }
        }
        if (drawn(c.snowmen.visible, c.snowmen.density)) {
            groundItems += PreviewItem(52f, ROW_GROUND, 0.56f, snowman(c.snowmen.colorAt(0, dayBlend)))
            if (c.snowmen.density >= 0.45f) groundItems += PreviewItem(DECOR_RIGHT_X, ROW_GROUND, 0.50f, snowman(c.snowmen.colorAt(1, dayBlend)))
        }
        if (drawn(c.gifts.visible, c.gifts.density)) {
            groundItems += PreviewItem(200f, ROW_GROUND, 0.55f, gift(c.gifts.colorAt(0, dayBlend)))
            groundItems += PreviewItem(222f, ROW_GROUND, 0.48f, gift(c.gifts.colorAt(1, dayBlend)))
            groundItems += PreviewItem(60f, ROW_GROUND, 0.50f, gift(c.gifts.colorAt(0, dayBlend)))
        }
        if (drawn(c.penguins.visible, c.penguins.density)) {
            // Beside the two adults at 118 and 142, not over them.
            groundItems += PreviewItem(72f, ROW_GROUND + 2f, 0.60f, penguin(c.penguins.colorAt(0, dayBlend)))
            groundItems += PreviewItem(98f, ROW_GROUND + 2f, 0.54f, penguin(c.penguins.colorAt(1, dayBlend)))
            groundItems += PreviewItem(236f, ROW_GROUND, 0.50f, penguin(c.penguins.colorAt(0, dayBlend)))
        }
        if (drawn(c.bunnies.visible, c.bunnies.density)) {
            groundItems += PreviewItem(50f, ROW_GROUND, 0.60f, bunny(c.bunnies.colorAt(0, dayBlend)))
            groundItems += PreviewItem(DECOR_RIGHT_X, ROW_GROUND, 0.52f, bunny(c.bunnies.colorAt(1, dayBlend)))
        }
        if (drawn(c.easterEggs.visible, c.easterEggs.density)) {
            groundItems += PreviewItem(196f, ROW_GROUND, 0.55f, easterEgg(c.easterEggs.colorAt(0, dayBlend)))
            groundItems += PreviewItem(218f, ROW_GROUND, 0.48f, easterEgg(c.easterEggs.colorAt(1, dayBlend)))
            groundItems += PreviewItem(92f, ROW_GROUND, 0.46f, easterEgg(c.easterEggs.colorAt(0, dayBlend)))
        }
        if (drawn(c.pumpkins.visible, c.pumpkins.density)) {
            groundItems += PreviewItem(52f, ROW_GROUND, 0.56f, pumpkin(c.pumpkins.colorAt(0, dayBlend), carved = halloween))
            groundItems += PreviewItem(204f, ROW_GROUND, 0.50f, pumpkin(c.pumpkins.colorAt(1, dayBlend), carved = halloween))
            groundItems += PreviewItem(DECOR_RIGHT_X, ROW_GROUND, 0.48f, pumpkin(c.pumpkins.colorAt(0, dayBlend), carved = halloween))
        }
        // **One scarecrow between the pumpkins** (`DESIGN_NOTES.md` §12, decision 80): in the snowmen's two points it
        // would stand over the pumpkins and hide them. At the Easter eggs' third point and scale; its pumpkin lit by the
        // card's night, as the wallpaper's.
        if (drawn(c.scarecrows.visible, c.scarecrows.density)) {
            groundItems += PreviewItem(SCARECROW_CARD_X, ROW_GROUND, SCARECROW_CARD_SCALE, scarecrow(((1f - dayBlend) * 255f).toInt().coerceIn(0, 255)))
        }
        // Parasols are deliberately absent: the renderer draws them procedurally, with no sprite, and a
        // stand-in would show something the scene does not contain. `ThemePreviewTruthTest` names the exception.
        if (theme.hasFireworks) {
            backdrop += PreviewItem(212f, 46f, 0.55f, listOf(PreviewSprite(R.drawable.firework, -40f, -40f, 0xFFFFD166.toInt())))
            backdrop += PreviewItem(258f, 66f, 0.42f, listOf(PreviewSprite(R.drawable.firework, -40f, -40f, 0xFFEF7DA8.toInt())))
            backdrop += PreviewItem(160f, 70f, 0.34f, listOf(PreviewSprite(R.drawable.firework, -40f, -40f, 0xFF8AD6F0.toInt())))
        }
        // The sleigh, a periodic flyby like the fireworks, read from `santaEnabled`: a flag with no hour
        // and no theme gate (`PaperRenderer` hands it straight to `SantaSleighEffect.update`), so a day
        // card carrying it says what the scene does, and the Seasons screen's switch reaches every theme.
        //
        // **Placed where the effect flies it and the sky is free**: `startFlight` picks
        // `screenHeight * (0.10..0.26)`, 24..62 on this 240-unit canvas, and 52 is inside it; at
        // [SLEIGH_CARD_WIDTH] the span 116..167 is the gap between the two clouds, clear of the sun on
        // the right and a unit clear of the nearest gull below. Fixed art, untinted, like the scene's.
        //
        // The group from its pieces, still, as `SantaSleighDrawer` lays them (`SantaSleighTable`): the
        // two reindeer in their first pose, Rudolph's red nose over his, the sleigh with Santa and the
        // presents; facing left, its middle on the flight point. At this size its details are under 2 px;
        // the cords are left out, a fraction of a pixel on the gallery card and about one on the home
        // card, as the strings' cords are.
        if (c.santaEnabled) {
            val mid = (SantaSleighTable.LEFT + SantaSleighTable.RIGHT) * 0.5f
            backdrop += PreviewItem(
                142f, 52f, SLEIGH_CARD_WIDTH / (SantaSleighTable.RIGHT - SantaSleighTable.LEFT),
                listOf(
                    PreviewSprite(
                        SantaSleighTable.DEER_RES[0],
                        SantaSleighTable.LEAD_X - mid + SantaSleighTable.DEER_X[0],
                        SantaSleighTable.LEAD_Y + SantaSleighTable.DEER_Y[0],
                    ),
                    PreviewSprite(
                        SantaSleighTable.DEER_RES[0],
                        SantaSleighTable.NEAR_X - mid + SantaSleighTable.DEER_X[0],
                        SantaSleighTable.NEAR_Y + SantaSleighTable.DEER_Y[0],
                    ),
                    PreviewSprite(
                        0,
                        SantaSleighTable.LEAD_X - mid + SantaSleighTable.NOSE_X[0],
                        SantaSleighTable.LEAD_Y + SantaSleighTable.NOSE_Y[0],
                        tint = SantaSleighTable.RUDOLPH_NOSE_COLOUR,
                        discRadius = SantaSleighTable.RUDOLPH_NOSE_RADIUS,
                    ),
                    PreviewSprite(SantaSleighTable.SLEIGH_RES, SantaSleighTable.SLEIGH_X - mid, SantaSleighTable.SLEIGH_Y),
                ),
            )
        }
        // The Headless Horseman, for the sleigh's reason: a flyby its switch puts on the scene. Still, in the
        // first pose of his gallop, facing left, as `HeadlessHorsemanDrawer` lays his pieces (`HorsemanTable`): his
        // body, then his near arm at rest with the pumpkin up in its hand (the arm throws only in flight), the
        // reins left out as the sleigh's cords are; in the sleigh's point of the sky, or -- where Santa is on too -- lower on its
        // right, so the two do not cover each other; drawn at the sleigh's card scale times the wallpaper's ratio of the two
        // groups' scales ([HeadlessHorsemanScale.toSleighScale]), so the two keep their sizes against each other.
        if (c.headlessHorsemanEnabled) {
            val mid = HeadlessHorsemanDrawer.MIDDLE
            val pose = 0
            val seatX = HorsemanTable.SEAT_X[pose]
            val seatY = HorsemanTable.SEAT_Y[pose]
            backdrop += PreviewItem(
                if (c.santaEnabled) HORSEMAN_CARD_X_BESIDE_SANTA else 142f,
                if (c.santaEnabled) HORSEMAN_CARD_Y_BESIDE_SANTA else 52f,
                SLEIGH_CARD_WIDTH / (SantaSleighTable.RIGHT - SantaSleighTable.LEFT) * HeadlessHorsemanScale.toSleighScale,
                listOf(
                    PreviewSprite(
                        HorsemanTable.CAPE_RES,
                        seatX + HorsemanTable.SHOULDER_X + HorsemanTable.CAPE_X - mid,
                        seatY + HorsemanTable.SHOULDER_Y + HorsemanTable.CAPE_Y,
                    ),
                    PreviewSprite(HorsemanTable.LEGS_RES[pose], HorsemanTable.LEGS_X[pose] - mid, HorsemanTable.LEGS_Y[pose]),
                    PreviewSprite(HorsemanTable.HORSE_RES[pose], HorsemanTable.HORSE_X[pose] - mid, HorsemanTable.HORSE_Y[pose]),
                    PreviewSprite(HorsemanTable.BODY_RES, seatX + HorsemanTable.BODY_X - mid, seatY + HorsemanTable.BODY_Y),
                    PreviewSprite(
                        HorsemanTable.ARM_RES,
                        seatX + HorsemanTable.ARM_PIVOT_X + HorsemanTable.ARM_X - mid,
                        seatY + HorsemanTable.ARM_PIVOT_Y + HorsemanTable.ARM_Y,
                    ),
                    PreviewSprite(
                        HorsemanTable.PUMPKIN_RES,
                        seatX + HorsemanTable.PUMPKIN_X + HorsemanTable.PUMPKIN_BLIT_X - mid,
                        seatY + HorsemanTable.PUMPKIN_Y + HorsemanTable.PUMPKIN_BLIT_Y,
                    ),
                ),
            )
        }

        // --- weather --------------------------------------------------------------------------
        if (drawn(c.precipitation.visible, c.precipitation.intensity)) {
            var seed = theme.id.hashCode() xor 0x50F1
            val snow = c.precipitation.type == PrecipitationType.SNOW
            // The wallpaper's own rule (`PaperRenderer.drawPrecipitation`): the theme's night colour
            // blended toward its day colour by the day blend, so the night cards (Halloween's and New
            // Year's Eve's) rain and snow in the wallpaper's night colours. The rain is then carried off
            // the card's own sky, as the wallpaper carries it off its own ([cardRainColour]); the snow
            // is not (see there).
            val colour = if (snow) {
                blendRgb(c.precipitation.snowColorNight, c.precipitation.snowColorDay, dayBlend)
            } else {
                // The blood rain's red in place of the rain's colours while it is on, off the sky by the same rule.
                cardRainColour(
                    if (c.bloodRainEnabled) {
                        blendRgb(PaperRenderer.BLOOD_RAIN_NIGHT, PaperRenderer.BLOOD_RAIN_DAY, dayBlend)
                    } else {
                        blendRgb(c.precipitation.rainColorNight, c.precipitation.rainColorDay, dayBlend)
                    },
                    skyTop,
                    skyBottom,
                )
            }
            val count = (120f * c.precipitation.intensity.coerceIn(0.2f, 1f)).toInt()
            repeat(count) {
                seed = seed * 1664525 + 1013904223
                val x = ((seed ushr 8) % 3200) / 10f
                seed = seed * 1664525 + 1013904223
                val y = ((seed ushr 8) % 2100) / 10f
                dots += PreviewDot(x, y, 0.9f, colour, alpha = PRECIPITATION_DOT_ALPHA, front = true)
            }
        }
        if (c.fallColorsEnabled) {
            var seed = theme.id.hashCode() xor 0x1EAF
            repeat(22) {
                seed = seed * 1664525 + 1013904223
                val x = ((seed ushr 8) % 3200) / 10f
                seed = seed * 1664525 + 1013904223
                val y = 110f + ((seed ushr 8) % 900) / 10f
                dots += PreviewDot(x, y, 1.4f, FALL_LEAF_COLOURS[(seed ushr 3).mod(FALL_LEAF_COLOURS.size)], alpha = 235, front = true)
            }
        }

        return ThemePreviewScene(
            skyTop = skyTop,
            skyBottom = skyBottom,
            groundColour = ground,
            peaks = peaks,
            lake = lake,
            // As the wallpaper decides it, 0 % height included (`LakeConfig.drawsWater`).
            hasLake = c.lake.drawsWater,
            hasRoad = SceneObjectRenderer.drawsRoad(layout, c),
            roadColour = SceneObjectRenderer.roadColor(dayBlend),
            backdrop = backdrop,
            items = items,
            cars = cars,
            ground = groundItems,
            dots = dots,
            water = water,
        )
    }

    /**
     * The card's water band, anchored the way `PaperRenderer.updateLakeBandY` anchors the scene's:
     * bottom fixed, growing upward with the height the user sets.
     */
    private fun lakeBand(c: SceneCustomization, dayBlend: Float): PreviewBand {
        val height = c.lake.height.coerceIn(0.1f, 1f)
        return PreviewBand(
            top = CARD_LAKE_BOTTOM_UNITS - CARD_LAKE_FULL_HEIGHT_UNITS * height,
            bottom = CARD_LAKE_BOTTOM_UNITS,
            colour = blendRgb(c.lake.colorNight, c.lake.colorDay, dayBlend),
        )
    }

    /**
     * A boat and a dolphin, placed the way `PaperRenderer.gatherLakeDecorations` places them: on
     * lanes that are **fractions of the band's own height**, at a scale the band can hold.
     *
     * One of each, because one of each is what the pool deals at the default densities
     * (`CandidateThreshold` keeps 1 of the 4 sailboat candidates and 1 of the 4 dolphins in every
     * built-in that has them). The scale is derived from the band rather than fixed, so a lake a
     * user shrinks to a tenth gets a boat that still fits inside it; the caps stop a full-height
     * sea from showing a dolphin the size of the bar.
     */
    private fun lakeLife(c: SceneCustomization, band: PreviewBand, into: MutableList<PreviewItem>) {
        val height = band.bottom - band.top
        val open = c.lake.height >= OPEN_WATER_HEIGHT
        if (drawn(c.lake.sailboatsVisible, c.lake.sailboatsDensity)) {
            val scale = (height / 60f).coerceIn(0.14f, if (open) 0.30f else 0.24f)
            // The hull's ink runs from oy 8 to oy 25 in the sprite's own space, so its middle is
            // 16.5 units below the item's origin: subtracting that sits the waterline on the lane.
            val lane = band.top + height * 0.38f
            into += PreviewItem(if (open) 130f else 13f, lane - 16.5f * scale, scale, sailboat())
        }
        if (drawn(c.lake.dolphinsVisible, c.lake.dolphinsDensity)) {
            val scale = (0.75f * height / 57f).coerceIn(0.12f, if (open) 0.30f else 0.20f)
            val lane = band.top + height * 0.42f
            into += PreviewItem(if (open) 200f else 207f, lane - scale, scale, dolphin())
        }
    }

    /** The dolphin, at the renderer's own origin constants, so the card cannot drift from it. */
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
     * Without the beacons and the lamps, the rectangles and lenses the renderer brightens on the night
     * ramp: the card draws no lamp, porch light or window occupant at any hour.
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

    /**
     * The two mountain layers, in their colours at [dayBlend]. Where they stand is the card's own
     * composition; what they look like is the wallpaper's, the Desert's included.
     */
    private fun buildPeaks(c: SceneCustomization, dayBlend: Float, winter: Boolean): List<PreviewPeak> {
        val out = mutableListOf<PreviewPeak>()
        var index = 0
        // At 0 % the wallpaper draws no mountain of the layer, so neither does the card.
        if (c.mountainsBack.drawsAny) {
            val colour = blendRgb(c.mountainsBack.colorNight, c.mountainsBack.colorDay, dayBlend)
            for ((x, y, w) in BACK_PEAKS) out += PreviewPeak(x, y, w, colour, distantHouses(c, x, y, w, index++, dayBlend, winter))
        }
        if (c.mountainsFront.drawsAny) {
            val colour = blendRgb(c.mountainsFront.colorNight, c.mountainsFront.colorDay, dayBlend)
            for ((x, y, w) in FRONT_PEAKS) out += PreviewPeak(x, y, w, colour, distantHouses(c, x, y, w, index++, dayBlend, winter))
        }
        return out
    }

    /**
     * The distant houses on one of the card's mountains. **The card follows the switch**: none while
     * *Distant houses* is off or at 0 % (every theme as it ships); on, the wallpaper's three drawings,
     * snow with the winter palette, and the wallpaper's own coins for each house's colour (one of the
     * houses' two) and window (lit in four houses of five), from a seed per mountain of the card. One
     * house a mountain, alternating sides from one mountain to the next as the wallpaper's do (the
     * card's first on the right, its second on the left: the card's own count, not
     * [DistantHouses.startsLeft]'s), and from 50 % two, one on each side, at the wallpaper's places
     * across the slope ([DistantHouses.SLOTS]); the drawings take turns, so a card shows all three.
     */
    private fun distantHouses(
        c: SceneCustomization,
        x: Float,
        peakY: Float,
        halfWidth: Float,
        mountain: Int,
        dayBlend: Float,
        winter: Boolean,
    ): List<PreviewItem> {
        if (!drawn(c.distantHouses.visible, c.distantHouses.density)) return emptyList()
        // The painter's own base line for a mountain (`ui/ThemePreview.kt`): the horizon, two units down.
        val base = ThemePreviewScene.HORIZON_UNITS + 2f
        val height = base - peakY
        val places = when {
            c.distantHouses.density >= 0.5f -> CARD_HOUSE_PLACES_TWO
            mountain % 2 == 1 -> CARD_HOUSE_PLACES_ONE_LEFT
            else -> CARD_HOUSE_PLACES_ONE
        }
        val seed = DistantHouses.seedFor(CARD_HOUSE_SEED, mountain)
        val out = mutableListOf<PreviewItem>()
        for ((n, slot) in places.withIndex()) {
            val along = DistantHouses.SLOTS[slot]
            val piece = NeighbourhoodTable.DISTANT_HOUSES[(mountain + n) % NeighbourhoodTable.DISTANT_HOUSES.size]
            val surface = DistantHouses.surfaceY(base, height, along)
            val foot = DistantHouses.footY(surface, DistantHouses.slope(height, halfWidth, along), CARD_HOUSE_UNITS_TALL)
            val wall = c.houses.colorAt(DistantHouses.colourVariant(seed, slot), dayBlend)
            val glass = DistantHouses.glassColour(seed, slot, 1f - dayBlend)
            val parts = mutableListOf<PreviewSprite>()
            for (part in piece.parts) {
                when (part.role) {
                    PartRole.FIXED -> parts += PreviewSprite(part.res, part.x, part.y)
                    PartRole.WALL_MASK -> parts += PreviewSprite(part.res, part.x, part.y, wall, added = true)
                    PartRole.GLASS_MASK -> parts += PreviewSprite(part.res, part.x, part.y, glass, added = true)
                    PartRole.SNOW -> if (winter) parts += PreviewSprite(part.res, part.x, part.y)
                    PartRole.LAMP, PartRole.OCCUPANTS -> Unit
                }
            }
            // Its three bulbs, as the wallpaper hangs them ([ChristmasDecorations.forEachDistantHouseBulb]) -- the
            // share of the house they are on the BV6600's wallpaper ([CARD_DISTANT_BULB_SHARE]), still and lit as the wallpaper's
            // -- and, as the buildings' decorations ([decorations]), drawn large enough to be 2 px on the gallery card: 1.46 times.
            if (c.christmasBuildingLightsEnabled) {
                val radius = CARD_DISTANT_BULB_SHARE * piece.height / 2f
                val px = 2f * radius * (CARD_HOUSE_UNITS_TALL / piece.height) * GALLERY_PX_PER_UNIT
                val drawn = radius * maxOf(1f, CARD_DECORATION_MIN_PX / px)
                ChristmasDecorations.forEachDistantHouseBulb(piece, radius, slot) { bx, by, colour ->
                    parts += PreviewSprite(0, bx, by, SceneObjectRenderer.CHRISTMAS_LIGHT_COLOURS[colour], discRadius = drawn, decoration = true)
                }
            }
            out += PreviewItem(x + along * halfWidth, foot, CARD_HOUSE_UNITS_TALL / piece.height, parts)
        }
        return out
    }

    /** The card's own layer seed for its distant houses' coins: one card, one fixed set of houses. */
    private const val CARD_HOUSE_SEED = 0x0CA4D

    /** How tall a distant house stands on the card, in card units: a fifth of the card's large house. */
    private const val CARD_HOUSE_UNITS_TALL = 9f

    /**
     * How large a distant house's bulb is on the card, as a share of the house's height: the share the wallpaper's is
     * on the BV6600 -- [PaperRenderer.DISTANT_BULB_PX] across on a house 14 px tall ([SceneSpace.distantHousePixelsTall]
     * on its 1440-px screen), so 0.157, and on the card's 9-unit house a bulb 1.41 units across.
     */
    internal val CARD_DISTANT_BULB_SHARE =
        PaperRenderer.DISTANT_BULB_PX / SceneSpace.distantHousePixelsTall(SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION, BV6600_HEIGHT_PX)

    /** The BV6600's screen height, the reference [CARD_DISTANT_BULB_SHARE] is taken on. */
    private const val BV6600_HEIGHT_PX = 1440f

    /**
     * Which of [DistantHouses.SLOTS] the card's houses take on a mountain: below 50 % one, on the right and
     * on the left from one mountain to the next; from 50 % two, one on each side.
     */
    private val CARD_HOUSE_PLACES_ONE = intArrayOf(3)
    private val CARD_HOUSE_PLACES_ONE_LEFT = intArrayOf(1)
    private val CARD_HOUSE_PLACES_TWO = intArrayOf(1, 3)

    private val BACK_PEAKS = listOf(
        Triple(50f, 108f, 46f), Triple(120f, 116f, 40f), Triple(205f, 104f, 52f), Triple(280f, 118f, 44f),
    )
    private val FRONT_PEAKS = listOf(
        Triple(20f, 126f, 40f), Triple(95f, 122f, 44f), Triple(170f, 130f, 40f),
        Triple(245f, 120f, 46f), Triple(305f, 130f, 38f),
    )

    /** How solidly the card paints a raindrop or a flake: the dots over everything, at 230 of 255. */
    const val PRECIPITATION_DOT_ALPHA = 230

    /**
     * The card's rain: [themeRain] carried off the card's own sky by the wallpaper's rule
     * ([PrecipitationContrast.standOffFromSky]) and the wallpaper's gap
     * ([PaperRenderer.PRECIPITATION_MIN_LUMA_GAP], divided by the alpha the dot is painted at), clear
     * of every sky between [skyTop] and [skyBottom].
     *
     * **Why**: the wallpaper carries its rain off the sky it falls through, and on some cards the
     * theme's rain is as bright as the card's own sky (Big City and Beach at noon, Sunset at seven,
     * New Year's Eve on the World & scene strip by day), where it would all but vanish
     * on the card. A colour already clear is returned unchanged, bit for bit.
     *
     * **Why the rain and not the snow**: against the wallpaper's skies the snow's correction comes
     * to nothing, while against the card's it would turn the white snow grey on seven cards, so the
     * snow keeps the theme's colour here.
     */
    internal fun cardRainColour(themeRain: Int, skyTop: Int, skyBottom: Int): Int {
        val top = PrecipitationContrast.rec601Luma(skyTop)
        val bottom = PrecipitationContrast.rec601Luma(skyBottom)
        return PrecipitationContrast.standOffFromSky(
            themeRain,
            PrecipitationContrast.rec601Luma(themeRain),
            kotlin.math.min(top, bottom),
            kotlin.math.max(top, bottom),
            PaperRenderer.PRECIPITATION_MIN_LUMA_GAP * 255f / PRECIPITATION_DOT_ALPHA,
        )
    }

    private val FALL_LEAF_COLOURS = intArrayOf(
        0xFFD2691E.toInt(), 0xFFB5451B.toInt(), 0xFFE0A93A.toInt(), 0xFF8F3B1B.toInt(),
    )

    /**
     * Where the card's buildings stand, as far as the deal is concerned: a card building's silhouette
     * and colour both come from `(tileFractionX, depthFraction)` (the slot composer's position hash for
     * a spec nobody dealt, and `variantIndexFor`'s), so a preview that wants a turret asks for a
     * position, not for a turret.
     *
     * They are **positions dealt for a picture**: spire, dome, spire, dome across the four towers and
     * one of each category colour on each row. They are not where the catalogue stands those families
     * (the restaurant's 0.4618 and three of the four towers' depths are no such position), and they
     * stay as dealt (the maintainer's decision of 2026-09-22): "restoring" them to the catalogue would
     * move every shop on every card. `ThemePreviewSceneTest` pins what each deals.
     *
     * [SCHOOL_DEPTH] is measured: the catalogue puts the school's row at 0.6222 in all twelve, so the
     * card stands it where the scene does; its x is Tundra's school's.
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
     * Most other builders below are hand copies of a call site in `SceneObjectRenderer` (the trees,
     * the palm, the cars, the fire truck and the dolphin read shared constants instead), and
     * `PreviewRendererAgreementTest` exists because hand copies drift. A stack of pieces chosen per
     * instance is too much arithmetic to copy, so [NeighbourhoodComposer] deals it and this reads out
     * the result.
     *
     * The card deals from a position it picks for a picture, through the position hash the wallpaper
     * uses for a spec nobody dealt (an older custom theme, a shuffled layout); a built-in scene
     * building wears the silhouette dealt at generation. The colour comes from the position on both
     * sides and both stack the pieces with the same arithmetic, so the agreement test checks the card
     * against the position deal.
     *
     * ### Two things the preview leaves out
     *
     * `OCCUPANTS` and `LAMP` are behaviours that animate (`WindowOccupants` picks a bust from the
     * scene's people, a porch light glows on the night ramp), and the preview is a still 320x240 card
     * with no people layer: no porch light, car lamp or window occupant at any hour
     * on the card, so a night card's porches are dark where the wallpaper's are lit. Snow is
     * drawn: `winterColorsEnabled` is a palette the user edits.
     */
    private fun neighbourhood(
        variant: SceneSpace.SceneVariant,
        type: SceneObjectType,
        tileX: Float,
        depth: Float,
        c: SceneCustomization,
        dayBlend: Float,
        snow: Boolean,
        hour: Float,
        seed: Int,
        itemScale: Float,
        storefront: Storefront? = null,
    ): List<PreviewSprite> {
        // A row among the houses draws the building the street's first stretch stands in it,
        // at the row's own size ([neighbourhoodScale]), in its group's colours and hours.
        val family = storefront?.family ?: NeighbourhoodTable.FAMILIES[variant] ?: return emptyList()
        // The real object, so the real coin: which of the two colours this building wears is
        // `variantIndexFor`'s answer about this very position, not a choice the preview makes.
        //
        // **Which pair, by what the card draws**: three of the card's four towers stand at depths the
        // scene keeps for shops ([PreviewIdentity]), so asking by depth would paint them in the shops'
        // colours. The card names the drawing, and the drawing names the pair.
        val spec = StaticSceneObject(type, depthFraction = depth, tileFractionX = tileX)
        val wall = if (storefront != null) c.storefrontWall(spec, storefront, dayBlend) else c.wallColourFor(spec, variant, dayBlend)
        // `SceneObjectRenderer.drawNeighbourhoodBuilding`'s glass: a house's windows lit by the night
        // alone, a business's by the night and its opening hours at the card's hour.
        val night = 1f - dayBlend
        val openness = if (storefront != null) c.opennessFor(storefront, hour) else c.opennessFor(variant, hour)
        val glass = if (family.kind == WindowBuildingKind.HOUSE) {
            SceneObjectRenderer.windowGlassColor(night)
        } else {
            SceneObjectRenderer.businessGlassColor(night, openness)
        }
        val deal = NeighbourhoodComposer.Deal()
        NeighbourhoodComposer.deal(family, tileX, depth, deal)
        // A house at night lights half its windows, as the wallpaper's does: the same rule from the same
        // seeds (the theme's and the house's) at the first moment of its roster, a card being one moment.
        // The people are not drawn on the card, but the windows they would stand at are among
        // the lit ones, as on the wallpaper.
        val litPanes = if (family.kind == WindowBuildingKind.HOUSE && night > 0f) {
            val buildingSeed = SceneObjectRenderer.buildingSeedOf(spec)
            val people = WindowOccupants.occupantCount(seed, buildingSeed, deal.peopleWindows, family.kind)
            val roster = WindowRoster.plan(seed, buildingSeed, deal.windowCount, deal.paneCount, people, house = true)
            WindowRoster.litMask(WindowRoster.stateAt(roster, SceneTime.ZERO))
        } else {
            -1
        }
        val maskGlass = if (litPanes != -1) SceneObjectRenderer.unlitWindowGlassColor(night) else glass
        val inset = SceneObjectRenderer.LIT_PANE_INSET
        val parts = mutableListOf<PreviewSprite>()
        for (index in 0 until deal.size) {
            val placed = deal[index]
            for (part in placed.piece.parts) {
                val y = placed.baseY + part.y
                when (part.role) {
                    PartRole.FIXED -> parts += PreviewSprite(part.res, part.x, y)
                    PartRole.WALL_MASK -> parts += PreviewSprite(part.res, part.x, y, wall, added = true)
                    PartRole.GLASS_MASK -> {
                        parts += PreviewSprite(part.res, part.x, y, maskGlass, added = true)
                        if (litPanes != -1) {
                            for ((k, box) in placed.piece.panes.withIndex()) {
                                if (litPanes and (1 shl (placed.firstPane + k)) == 0) continue
                                parts += PreviewSprite(
                                    0, box.x + inset, placed.baseY + box.y + inset, glass,
                                    rectWidth = box.w - 2f * inset, rectHeight = box.h - 2f * inset,
                                )
                            }
                        }
                    }
                    PartRole.SNOW -> if (snow) parts += PreviewSprite(part.res, part.x, y)
                    PartRole.LAMP, PartRole.OCCUPANTS -> Unit
                }
            }
        }
        if (family.kind != WindowBuildingKind.HOUSE && openness < 1f) shut(parts, deal, family.kind, night)
        if (c.christmasBuildingLightsEnabled) decorations(parts, deal, family.kind, SceneObjectRenderer.idleSeedOf(spec), itemScale)
        // The Halloween decorations, on the card's houses and shops where the wallpaper hangs them
        // ([HalloweenDecorations]), still, the jack-o'-lanterns lit as the wallpaper lights them at the card's moment.
        if (c.halloweenDecorationsEnabled && HalloweenDecorations.decorates(family.kind, storefront)) {
            val dayWall = if (storefront != null) c.storefrontWall(spec, storefront, 1f) else c.wallColourFor(spec, variant, 1f)
            val nightWall = if (storefront != null) c.storefrontWall(spec, storefront, 0f) else c.wallColourFor(spec, variant, 0f)
            halloweenDecorations(parts, deal, itemScale, night, dayWall, nightWall, dayBlend)
        }
        return parts
    }

    /**
     * The building's Christmas decorations, as the wallpaper hangs them (`DESIGN_NOTES.md` §12, decision 67):
     * the same points of the same sequence ([ChristmasDecorations]), a bulb on every point
     * of the roofline, a tower's star, a wreath on every front door, the bulbs of the strings under every
     * sill of a building with four or fewer and three in four of a larger one's (the wallpaper's rule,
     * under the card building's own [idleSeed]), in the same order after the shut sign.
     *
     * **Still and lit**: the card does not move, so each bulb is at its full light, where the
     * wallpaper's blink between about a quarter of it and all of it (alpha 70 to 255, `drawDecorations`,
     * `drawWindowLights`). Lit open or shut, by day and by night, as the wallpaper's.
     *
     * **A little larger than the wallpaper's, so they read**: on the gallery card, 316 px across on the
     * BV6600, a bulb or a wreath at the wallpaper's proportions is 1.0 to 1.9 px. So every decoration of
     * a building is drawn [cardDecorationFactor] times its size about the same point, the smallest at
     * 2 px there, 2.04 (`CardDecorationsTest`). The strings' cords are left out: 1.1 units is 0.4 to
     * 0.7 px on the gallery card, a line no factor makes read without making it as thick as its bulbs.
     */
    private fun decorations(
        parts: MutableList<PreviewSprite>,
        deal: NeighbourhoodComposer.Deal,
        kind: WindowBuildingKind,
        idleSeed: Float,
        itemScale: Float,
    ) {
        val colours = SceneObjectRenderer.CHRISTMAS_LIGHT_COLOURS
        var strings = false
        ChristmasDecorations.forEachSillString(deal, idleSeed) { _, _, _ -> strings = true }
        val f = cardDecorationFactor(kind, strings, itemScale)
        ChristmasDecorations.forEachRoofBulb(deal, kind) { i, x, y, radius ->
            parts += PreviewSprite(0, x, y, colours[i % colours.size], discRadius = radius * f, decoration = true)
        }
        ChristmasDecorations.star(deal, kind) { cx, cy, outer, inner ->
            val points = List(20) { n ->
                val i = n / 2
                val radius = (if (i % 2 == 0) outer else inner) * f
                if (n % 2 == 0) cx + radius * SceneObjectRenderer.STAR_COS[i] else cy + radius * SceneObjectRenderer.STAR_SIN[i]
            }
            parts += PreviewSprite(0, 0f, 0f, SceneObjectRenderer.STAR_GOLD, outline = points, decoration = true)
        }
        ChristmasDecorations.forEachWreath(deal, kind) { cx, cy, k ->
            val s = k * f
            parts += PreviewSprite(
                R.drawable.deco_wreath, cx - SceneObjectRenderer.WREATH_HALF * s, cy - SceneObjectRenderer.WREATH_HALF * s,
                scale = s, decoration = true,
            )
        }
        ChristmasDecorations.forEachSillString(deal, idleSeed) { x, sillY, width ->
            ChristmasDecorations.forEachStringBulb(x, sillY, width) { bulb, bx, by ->
                parts += PreviewSprite(
                    0, bx, by, colours[bulb % colours.size],
                    discRadius = SceneObjectRenderer.CHRISTMAS_LIGHT_BULB_RADIUS_UNITS * f, decoration = true,
                )
            }
        }
    }

    /**
     * The card's Halloween decorations of a house or a shop: the cobweb and the jack-o'-lantern by the
     * front door, from the sequences the wallpaper hangs them by ([HalloweenDecorations]), still. The
     * cobweb in the corner the building's drawing chooses, tinted as its walls choose: their colours by
     * day and at night, [dayWall] and [nightWall], at the card's [dayBlend], as the wallpaper's
     * ([HalloweenDecorations.webTintAt]; at the card's hours, noon, midnight and Sunset's 19:00, every
     * built-in wall's web is at rest). The candle lit by the [night], none by day, as the wallpaper's
     * (`SceneObjectRenderer.drawHalloweenDecorations`). Drawn [cardHalloweenFactor] times their size, so
     * the cobweb's threads keep 2 px on the BV6600's gallery card.
     */
    private fun halloweenDecorations(
        parts: MutableList<PreviewSprite>,
        deal: NeighbourhoodComposer.Deal,
        itemScale: Float,
        night: Float,
        dayWall: Int,
        nightWall: Int,
        dayBlend: Float,
    ) {
        val s = cardHalloweenFactor(itemScale)
        val lit = (night * 255f).toInt().coerceIn(0, 255)
        val web = HalloweenDecorations.webTintAt(dayWall, nightWall, dayBlend)
        HalloweenDecorations.forEachCobweb(deal) { x, y, mirror ->
            // The right one is the left one's drawing mirrored, from its corner: [PreviewSprite.mirrored] draws it to the
            // left of its origin.
            parts += PreviewSprite(
                HalloweenDecorationTable.WEB_RES, x + HalloweenDecorationTable.WEB_X * s * mirror, y + HalloweenDecorationTable.WEB_Y * s,
                tint = web, scale = s, decoration = true, mirrored = mirror < 0f,
            )
        }
        HalloweenDecorations.forEachDoorPumpkin(deal) { x, y ->
            parts += PreviewSprite(
                HalloweenDecorationTable.PUMPKIN_RES, x + HalloweenDecorationTable.PUMPKIN_X * s, y + HalloweenDecorationTable.PUMPKIN_Y * s,
                scale = s, decoration = true,
            )
            if (lit > 0) {
                parts += PreviewSprite(
                    HalloweenDecorationTable.PUMPKIN_LIGHT_RES,
                    x + HalloweenDecorationTable.PUMPKIN_LIGHT_X * s, y + HalloweenDecorationTable.PUMPKIN_LIGHT_Y * s,
                    alpha = lit, scale = s, decoration = true,
                )
            }
        }
    }

    /**
     * How many times its size the card draws a decoration of a building of [kind] drawn at [itemScale] card units a unit:
     * enough for its smallest decoration -- the wreath's bow, 2.8 units across (a tower's half as large again), or a string's
     * bulb, 3 units, where it has [strings] -- to be [CARD_DECORATION_MIN_PX] on the BV6600's gallery card, and never less
     * than its size (`CardDecorationsTest` prints each card's, `CARD_DECORATION_PX`).
     */
    internal fun cardDecorationFactor(kind: WindowBuildingKind, strings: Boolean, itemScale: Float): Float {
        val bow = 2f * SceneObjectRenderer.WREATH_BOW_RADIUS * ChristmasDecorations.scaleFor(kind)
        val smallest = if (strings) minOf(bow, 2f * SceneObjectRenderer.CHRISTMAS_LIGHT_BULB_RADIUS_UNITS) else bow
        return maxOf(1f, CARD_DECORATION_MIN_PX / (smallest * itemScale * GALLERY_PX_PER_UNIT))
    }

    /**
     * How many times its size the card draws a building's Halloween decorations at [itemScale] card units a unit:
     * enough for their narrowest band -- the cobweb's threads, [HalloweenDecorationTable.DECORATION_BAND_UNITS]
     * -- to be [CARD_DECORATION_MIN_PX] on the BV6600's gallery card, and never less than their size.
     */
    internal fun cardHalloweenFactor(itemScale: Float): Float =
        maxOf(1f, CARD_DECORATION_MIN_PX / (HalloweenDecorationTable.DECORATION_BAND_UNITS * itemScale * GALLERY_PX_PER_UNIT))

    /** The two pixels every drawn detail is held to. */
    internal const val CARD_DECORATION_MIN_PX = 2f

    /**
     * Pixels a card unit on the BV6600's gallery card, the smallest place the card is shown, taken on the 310 px its road
     * shows: the card is 316 px across, its rounded corners and border covering the road's ends, so this is 2 % under.
     */
    internal const val GALLERY_PX_PER_UNIT = 310f / ThemePreviewScene.WIDTH_UNITS

    /**
     * A building the card shows shut, as the wallpaper draws one (`SceneObjectRenderer.drawShut`): the
     * blinds down in the windows a person may stand at and the shutter down over the front door, in the
     * wallpaper's colours at the card's hour -- flat cards. **The shutter is the wallpaper's**: the same bands
     * ([DoorShutter.forEachBand], fully down) cut from the door's own outline ([DoorShutter.band]) -- its card and
     * the shadow paper under it -- so it stands where the door the card's sprite draws stands, and not on the door's
     * box, from which the scissors' wobble moves the paper up to 0.8 units.
     */
    private fun shut(parts: MutableList<PreviewSprite>, deal: NeighbourhoodComposer.Deal, kind: WindowBuildingKind, night: Float) {
        val k = if (kind == WindowBuildingKind.SKYSCRAPER) SceneObjectRenderer.TOWER_DECORATION_SCALE else 1f
        val inset = SceneObjectRenderer.LIT_PANE_INSET
        val blind = SceneColour.blendArgb(SceneObjectRenderer.BLIND_DAY, SceneObjectRenderer.BLIND_NIGHT, night)
        val rail = SceneColour.blendArgb(SceneObjectRenderer.BLIND_RAIL_DAY, SceneObjectRenderer.BLIND_RAIL_NIGHT, night)
        for (index in 0 until deal.size) {
            val placed = deal[index]
            for (w in placed.piece.windows) {
                val h = w.h * SceneObjectRenderer.BLIND_SHARE
                val top = placed.baseY + w.y + inset
                parts += PreviewSprite(0, w.x + inset, top, blind, rectWidth = w.w - 2f * inset, rectHeight = h)
                val railH = minOf(SceneObjectRenderer.BLIND_RAIL * k, h)
                parts += PreviewSprite(0, w.x + inset, top + h - railH, rail, rectWidth = w.w - 2f * inset, rectHeight = railH)
            }
        }
        val ground = deal[0]
        val door = ground.piece.door ?: return
        val under = SceneColour.blendArgb(SceneObjectRenderer.SHUTTER_UNDER_DAY, SceneObjectRenderer.SHUTTER_UNDER_NIGHT, night)
        val face = SceneColour.blendArgb(SceneObjectRenderer.SHUTTER_DAY, SceneObjectRenderer.SHUTTER_NIGHT, night)
        val slat = SceneColour.blendArgb(SceneObjectRenderer.SHUTTER_SLAT_DAY, SceneObjectRenderer.SHUTTER_SLAT_NIGHT, night)
        val size = 2 * door.outlineX.size + 4
        val xs = FloatArray(size)
        val ys = FloatArray(size)
        val scratchX = FloatArray(size)
        val scratchY = FloatArray(size)
        DoorShutter.forEachBand(door, ground.baseY, 1f, k) { paper, bandTop, bandBottom, dx, dy ->
            val n = DoorShutter.band(door, ground.baseY, bandTop, bandBottom, dx, dy, xs, ys, scratchX, scratchY)
            if (n > 0) {
                val colour = when (paper) {
                    DoorShutter.Paper.UNDER -> under
                    DoorShutter.Paper.FACE -> face
                    DoorShutter.Paper.SLAT -> slat
                }
                parts += PreviewSprite(0, 0f, 0f, colour, outline = List(2 * n) { if (it % 2 == 0) xs[it / 2] else ys[it / 2] })
            }
        }
    }

    /**
     * The scale a dealt building is drawn at in the card. The pieces are authored in the family's own
     * units and the wallpaper scales them by `variant.spriteUnitsTall / family.unitsTall`; the card has
     * no nested scale, so the factor is folded into the item's own. [fit] is how much of the card this
     * object may take.
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
        dayBlend: Float,
        snow: Boolean,
        hour: Float,
        seed: Int,
        storefront: Storefront? = null,
    ): PreviewItem {
        val scale = neighbourhoodScale(variant, fit)
        return PreviewItem(x, y, scale, neighbourhood(variant, type, tileX, depth, c, dayBlend, snow, hour, seed, scale, storefront))
    }

    /**
     * The leafy tree at [TreeSpriteLayout]'s origins, read rather than copied, so the card's crown and
     * snow cap stand where the wallpaper's do.
     */
    private fun tree(leaf: Int, winter: Boolean, halloween: Boolean, lights: Boolean): List<PreviewSprite> {
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
        // *Lights on the trees*: the wallpaper's lights at three of its six points of the crown
        // ([SceneObjectRenderer.CHRISTMAS_LIGHT_X]), as sparkles in the lights' colours, as the card's fir hangs its baubles.
        if (lights) {
            for (i in TREE_CARD_LIGHTS) {
                val x = SceneObjectRenderer.CHRISTMAS_LIGHT_X[i] * SceneObjectRenderer.TREE_LIGHTS_RADIUS_X
                val y = TreeSpriteLayout.CANOPY_LIFT_Y + SceneObjectRenderer.TREE_LIGHTS_CENTRE_Y +
                    SceneObjectRenderer.CHRISTMAS_LIGHT_Y[i] * SceneObjectRenderer.TREE_LIGHTS_RADIUS_Y
                parts += PreviewSprite(
                    R.drawable.star_sparkle, x - SPARKLE_INK_X, y - SPARKLE_INK_Y,
                    SceneObjectRenderer.CHRISTMAS_LIGHT_COLOURS[i % SceneObjectRenderer.CHRISTMAS_LIGHT_COLOURS.size], alpha = 220,
                )
            }
        }
        return parts
    }

    /** Which of the wallpaper's six lights of a crown the card's leafy tree shows: upper left, the middle, the right. */
    private val TREE_CARD_LIGHTS = intArrayOf(1, 2, 4)

    /** What the card stands on its tree row, left to right. */
    private enum class CardTree { TREE, FIR, PALM }

    /**
     * The card's trees: two, whatever the density, and one where the woodland is a scattering; a third
     * has no place on the row that does not stand in front of a shop.
     *
     * The palms are the switch's (`palmsShown`), and *Christmas firs* is the one switch that puts firs
     * among the trees (a sparse wood is no reason for a fir). So with the palms the card shows palms,
     * and without them a fir first under the layer.
     *
     * Under both, on a layout that plants no palm of its own, the scene keeps two species: its firs,
     * and a palm in every other tree's place (`palmSpeciesApplied`; the maintainer, 2026-10-03: *«gli
     * abeti sono del tema e tali devono rimanere»*). Which of them a thinned wood keeps is the layout's
     * deal (Christmas at 20 % keeps two trees, both firs), so the card asks the scene, through the two
     * functions the renderer builds its list with, and shows a fir and a palm when it stands both, even
     * in a scattering. Beach and Desert are not this case: their palm slots are palms whatever the layer
     * says, and with their palms on they stand no fir.
     */
    private fun cardTrees(
        c: SceneCustomization,
        layout: SceneObjectLayout,
        layoutPlantsPalms: Boolean,
        palms: Boolean,
        sparse: Boolean,
    ): List<CardTree> {
        val count = if (sparse) 1 else 2
        if (palms && !layoutPlantsPalms && SceneObjectRenderer.drawsFirs(c)) {
            val kept = layout.staticObjects
                .filter { c.keepCandidate(it, layout.densityScheme) }
                .map { c.palmSpeciesApplied(it, layoutPlantsPalms = false) }
            // A tree slot left a tree here is a fir: every other one became a palm.
            val firs = kept.any { it.type == SceneObjectType.TREE }
            val palmsKept = kept.any { it.type == SceneObjectType.PALM_TREE }
            return when {
                firs && palmsKept -> listOf(CardTree.FIR, CardTree.PALM)
                firs -> List(count) { CardTree.FIR }
                else -> List(count) { CardTree.PALM }
            }
        }
        return List(count) { index ->
            when {
                palms -> CardTree.PALM
                SceneObjectRenderer.drawsFirs(c) && index % 2 == 0 -> CardTree.FIR
                else -> CardTree.TREE
            }
        }
    }

    /**
     * The fir the wallpaper draws (`SceneObjectRenderer.drawFir`): the same three pieces at the same
     * origins, all from [FirTable], so a redraw moves the card and the wallpaper together; the fir under
     * the trees' night shade at the card's hour as the wallpaper's is ([shade]), the snow and the star not.
     *
     * The baubles are not the wallpaper's ten lights, which would be under 2 px on the card:
     * `star_sparkle` blits, which at 46 px of tree read as baubles (the maintainer left them on,
     * 2026-09-28), one over the hem of each of the upper three tiers ([FirTable.BAUBLE_X]). The
     * sparkle's ink is centred about (30, 29) of its 60-unit canvas.
     */
    private fun fir(snow: Boolean, shade: Int): List<PreviewSprite> {
        val parts = mutableListOf(PreviewSprite(R.drawable.tree_fir, FirTable.FIR_X, FirTable.FIR_Y, shade = shade))
        if (snow) parts += PreviewSprite(R.drawable.tree_fir_snow, FirTable.SNOW_X, FirTable.SNOW_Y)
        parts += PreviewSprite(R.drawable.tree_fir_star, FirTable.STAR_X, FirTable.STAR_Y)
        // Always lit: a card fir exists only where the wallpaper's does, under the Christmas decorations.
        for (i in FirTable.BAUBLE_X.indices) {
            parts += PreviewSprite(
                R.drawable.star_sparkle, FirTable.BAUBLE_X[i] - SPARKLE_INK_X, FirTable.BAUBLE_Y[i] - SPARKLE_INK_Y,
                BAUBLE_COLOURS[i % BAUBLE_COLOURS.size], alpha = 220,
            )
        }
        return parts
    }

    /** Where the ink of `star_sparkle`'s 60-unit canvas is centred, for the fir's baubles. */
    private const val SPARKLE_INK_X = 30f
    private const val SPARKLE_INK_Y = 29f

    /** The fir's three baubles: gold, red, blue. */
    private val BAUBLE_COLOURS = intArrayOf(0xFFF2C14E.toInt(), 0xFFE8483C.toInt(), 0xFF5BC0EB.toInt())

    /**
     * The same two blits [SceneObjectRenderer.drawPalmTree] makes, at the same two origins from
     * [PalmSpriteLayout], which the renderer reads too, so a redraw moves both sides or neither. The
     * three crowns share one canvas and one attachment, so the `when` chooses one rather than stacking.
     *
     * **[shade] is the wallpaper's light on it**: [nightShadeAt] of the trees' first colour pair at the
     * card's moment, the grey `drawPalmTree` multiplies both drawings by, while the tree beside it wears
     * the same pair's colour ([colorAt]). Full daylight at noon, so a day card's palm is the untinted blit.
     *
     * **[lights], the Christmas lights the wallpaper strings on a palm** (`drawPalmTree` calls
     * `drawChristmasLights` on the crown's centre, Halloween's dead crown included), as palms can stand
     * on Christmas, lit by default (`ThemePreviewTruthTest`). **One** of the fir's baubles ([fir]), not
     * three: the `star_sparkle` canvas, 60 units with its ink centred about (30, 29), is as big as the
     * whole crown, and three hide the palm on the card. It sits on the crown's top edge, over the centre
     * the wallpaper hangs its string on, mostly in the sky, placed by eye.
     */
    private fun palmTree(dead: Boolean, frost: Boolean, shade: Int, lights: Boolean): List<PreviewSprite> {
        val parts = mutableListOf(
            PreviewSprite(R.drawable.palmtree_trunk, PalmSpriteLayout.TRUNK_X, PalmSpriteLayout.TRUNK_Y, shade = shade),
            PreviewSprite(
                when {
                    dead -> R.drawable.palmtree_fronds_dead
                    frost -> R.drawable.palmtree_fronds_frost
                    else -> R.drawable.palmtree_fronds
                },
                PalmSpriteLayout.CROWN_X, PalmSpriteLayout.CROWN_Y,
                shade = shade,
            ),
        )
        if (lights) {
            parts += PreviewSprite(R.drawable.star_sparkle, -23f, -109f, 0xFFF2C14E.toInt(), alpha = 220)
        }
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

    /**
     * A scarecrow, its drawing brought to a snowman's height in the snowman's units (the card's one stands
     * between the pumpkins at their scale, [SCARECROW_CARD_SCALE]), its pumpkin's candle lit as the
     * wallpaper's, by the card's night ([lit], `(1 - dayBlend) * 255`: at Sunset's 19:00 a little).
     */
    private fun scarecrow(lit: Int): List<PreviewSprite> {
        val k = SceneSpace.SceneVariant.SNOWMAN.spriteUnitsTall / ScarecrowTable.UNITS_TALL
        return buildList {
            add(PreviewSprite(ScarecrowTable.RES, ScarecrowTable.X * k, ScarecrowTable.Y * k, scale = k))
            if (lit > 0) {
                add(PreviewSprite(ScarecrowTable.LIGHT_RES, ScarecrowTable.LIGHT_X * k, ScarecrowTable.LIGHT_Y * k, alpha = lit, scale = k))
            }
        }
    }

    /**
     * The pumpkin as [SceneObjectRenderer.drawPumpkin] builds it: the tinted body, the carved face
     * where `halloweenEnabled` is on ([carved]), the stem.
     */
    private fun pumpkin(c: Int, carved: Boolean) = buildList {
        add(PreviewSprite(R.drawable.pumpkin_body, -19f, -30f, c))
        if (carved) add(PreviewSprite(R.drawable.pumpkin_face, -19f, -30f))
        add(PreviewSprite(R.drawable.pumpkin_stem, 2f, -42f))
    }

    private fun penguin(c: Int) = listOf(
        PreviewSprite(R.drawable.penguin_body, -14f, -45f, c),
        PreviewSprite(R.drawable.penguin_belly, -9f, -38f, SceneObjectRenderer.PENGUIN_BELLY_COLOR),
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
     * A preview car, on one of the three bodies. The gallery draws no wheels, so a car stands on its
     * **painted floor**, [PREVIEW_CAR_FLOOR_DROP] units below the item's ground line whatever the body,
     * and the glass follows the body by the gap the renderer uses: neither offset is a hand copy.
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
        // The wallpaper's own offsets (`PaperRenderer.drawSailboat`): the card is a second call site of
        // these sprites, so a crop that moves one origin moves both.
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

    /**
     * The preview's own name for the scene's blend, kept because a hundred call sites read better with
     * it; the arithmetic is [SceneColour]'s, the renderer's.
     */
    private fun blendRgb(from: Int, to: Int, ratio: Float): Int = SceneColour.blendArgb(from, to, ratio)
}
