package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R

/**
 * One blit of a preview object: the resource, the offset the renderer blits it at, and the tint
 * (null for fixed art). Offsets are in scene units and come from `SceneObjectRenderer`'s own draw
 * functions -- read from its constants where it has them, copied where it does not -- so a preview
 * house is assembled out of the parts, at the positions, the wallpaper assembles one from.
 *
 * [added] is the people's and the neighbourhood's blend: the mask's contribution is **summed**
 * into the frame over a fixed layer rather than drawn over it, which is what makes a weight mask
 * interpolate. A preview that drew those masks with a plain tint would show the wall colour
 * covering the ink it is supposed to be added to -- the same mistake, in the gallery, that
 * `SpriteBlitter.drawTintedAdded` exists to prevent in the scene.
 *
 * [shade] is how lit a piece of fixed art is, the neutral grey `SpriteBlitter.draw` multiplies it
 * by, and only for a part with no [tint]: the palm, which the wallpaper shades rather than tints
 * (`SceneObjectRenderer.drawPalmTree`, [nightShadeFor]). Full daylight, the default, draws the art
 * as authored.
 *
 * A part with a [rectWidth] is no sprite: a flat card [rectWidth] x [rectHeight] at ([ox], [oy]) in
 * [tint] -- a house's lit window over its dark glass at night (v5.12), as the wallpaper lays it
 * (`SceneObjectRenderer.drawLitPanes`). [resId] is 0 then.
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
)

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
     * The distant houses standing on this mountain (v5.11), painted right after it and before the
     * next one, as the wallpaper paints them: empty unless the theme turns them on above 0 %.
     */
    val houses: List<PreviewItem> = emptyList(),
)

/** A horizontal band of water. */
data class PreviewBand(val top: Float, val bottom: Float, val colour: Int)

/**
 * A single filled dot: a star, a snowflake, a raindrop, a falling leaf.
 *
 * [front] is where it is painted: behind everything, in the sky (a star), or over the whole card
 * (rain, snow and falling leaves, which the wallpaper draws after everything else). The painter
 * used to decide by radius, and rain and snow are smaller than a leaf, so both ended up in the
 * sky pass and a snowy card snowed only behind the town.
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
 * Everything one theme's gallery preview draws, in the order it draws it.
 *
 * Deliberately a plain data description with no Android type in it beyond resource ids (which are
 * `Int`s): what a preview contains is a question about the theme, answerable and testable without a
 * `Canvas`. `ThemeScenePreview` in `ui/ThemePreview.kt` is the only thing that knows how to put it
 * on screen.
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
 * Every place that shows a preview -- the gallery card, the settings home card and the strip at
 * the top of World & scene -- takes its scale from this, so none of them can drift into per-object
 * fitting factors of its own, and all three size themselves to [ASPECT_RATIO], so none can drift
 * into a different aspect ratio or crop. That drift is exactly what v2.9 shipped: the gallery
 * previews were composed in scene units while the World & scene strip still magnified the size
 * table with per-item fitting factors so three objects of very different heights would fit a
 * 120 dp band, and the two sat next to each other looking like different products. The settings
 * home card (`SettingsScreen`'s `HomeThemePreview`) was the last one out of step: 16:9 until v5.9F,
 * it showed only the top 180 of the scene's 240 units and cropped the pavement, the road and the
 * cars (inventory I-88).
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
 * Whether the wallpaper draws any of a thing at all: switched on **and above 0 %** (v5.10E). The card
 * read the switches alone, so with a density, an intensity or a number at 0 -- where the wallpaper draws
 * none (v5.10C, row 8: the switch reads off there) -- it went on showing trees, houses, the decorations,
 * clouds, birds, rain and boats over a scene that had none of them; the v5.10C2 round saw it for the trees.
 * Not for the buildings, whose shops stay at 0 %, nor the cars, one of which drives, nor the people, who
 * have their night density: those keep their switch alone, as the wallpaper does.
 */
private fun drawn(visible: Boolean, amount: Float): Boolean = visible && amount > 0f

/**
 * Builds a theme's preview scene out of the customization the wallpaper would draw it with.
 *
 * **Nothing here decides what a theme contains.** Every object is conditional on the same flags
 * the wallpaper reads -- `lake.drawsWater`, `snowmen.visible` with its density ([drawn], v5.10E),
 * `winterColorsEnabled`, `halloweenEnabled`, `mountainsFront.visible` and so on -- so a preview shows what the scene
 * shows wherever the card reads the same flag. The one exception there was, a sparse wood
 * (`trees.density <= 0.25`) drawn as a fir, went in v5.8E (V3-48): the wallpaper draws a fir only
 * where [SceneObjectRenderer.drawsFirs] says so, and Tundra's card showed one its wallpaper never
 * draws. A sparse wood is now one tree of the kind the wallpaper stands there. The gallery
 * passes what `CustomThemeRegistry.resolveActiveCustomization` resolves for the theme, the same
 * resolution the wallpaper makes: an edit in progress, then the theme's own edits, then a saved
 * copy, then the defaults.
 *
 * **Nor does anything here decide a colour or a shape of the landscape.** The sky comes from
 * [SkyGradient], the road from `SceneObjectRenderer.roadColor` and `drawsRoad`, every other
 * day/night pair is blended on the same `dayBlend`, and whether trees are palms from the layout's
 * own slots -- all at the moment of the day the card shows, which is a real moment of the
 * wallpaper's day ([cardPhase]). `ThemePreviewTruthTest`'s R4 holds the card to it.
 *
 * What lives here instead is *composition*: which slots exist and where they stand. The real scene
 * generates hundreds of objects across a screen five times this wide, and shrinking that produces a
 * grey mush; a preview is a dozen objects, one per family, in a far-to-near order chosen for the
 * card -- towers, restaurant, large house, school, small house, bar, then the trees, the pavement
 * and the road -- which follows the scene's shop depths but not its houses, which stand in both
 * house bands. See [forTheme].
 */
object ThemePreviewScenes {

    /**
     * The depth rows the card's street is built on, far to near.
     *
     * v5.7 replaced four reading bands with an order taken from the scene's. `SceneSpace` deals the
     * shops by `depthFraction` at fixed depths -- the restaurant at 0.4444, the school at 0.6222
     * and the bar at 0.8000, each the single value `SceneObjectCatalog` emits in all twelve
     * built-ins, measured, not chosen here -- with the towers on the skyline (0.00-0.27); the
     * houses of both families stand in two bands (0.28-0.48 and 0.62-0.95), so the card's two
     * house rows are a choice, not the scene's order. A row is a ground line rather than a band:
     * an object on a nearer row is appended later and is therefore drawn in front, which is the
     * whole of the ordering.
     *
     * What this replaced put the restaurant and the bar side by side on one row with the two
     * houses in front of them, so the bar -- the nearest of the scene's shops -- was 88 % hidden
     * behind the small house on ten of the twelve cards.
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
     * 164 is the ground line the towers stand behind; 56 is the climb at full height, which takes
     * the surface to 108, 42 units above the card's horizon line
     * ([ThemePreviewScene.HORIZON_UNITS]) and among the back mountains' peaks (104..118).
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
    internal const val PREVIEW_CAR_FLOOR_DROP = 28f

    /**
     * Where the card's two bodies stop: the last row of ink of `car_body_saloon` and
     * `car_body_estate` is row 129, so the ink ends 130 / 3 units below the blit origin at
     * `bodyYUnits` = -17, which is 26.33 (`car_body_compact` ends at 26.00 and is not on any card).
     *
     * It read 30.5 until v5.8E -- the bottom of the v4.19 bodies plus half a unit of rim -- and the
     * v5.6F bodies stop 4.17 units higher, so every car on every card stood 4.17 x 0.42 = 1.75 card
     * units (1.7 px on the BV6600's 316 px card) above the floor [PREVIEW_CAR_FLOOR_DROP] gives it
     * (V3-47). `ThemePreviewTruthTest` R5 measures the floor off the PNGs, so a redrawn body that
     * moves its last row fails there rather than floating again.
     */
    private const val CAR_PAINTED_FLOOR_UNITS = -17f + 130f / 3f

    /** The carved moon's own tint in `PaperRenderer`; not the theme's `moonColor`. */
    private const val HALLOWEEN_MOON_COLOUR = PaperRenderer.HALLOWEEN_MOON_COLOUR

    /**
     * The clock hours the card shows, on the default day `SunPositionCalculator.compute` assumes
     * (sunrise 06:00, sunset 20:00).
     *
     * Noon for a day card, midnight for a night one, and for Sunset -- the one theme named after
     * a moment of the day -- 19:00, an hour before that day's sunset, with the sun low. The card
     * used to paint Sunset from the theme's old `skyDusk` array, whose coral top the wallpaper
     * never draws at any hour; this is the wallpaper's own sky at a moment it does draw. All three
     * are whole hours on purpose: they are what *Weather & time*'s fixed-time slider can set, so
     * anybody can put the wallpaper at the card's moment and compare the two.
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
     * So the six building families are laid out far to near with the shops in the order
     * `SceneSpace` deals them -- towers, restaurant, school, bar -- and one house of each family
     * between them; the road carries the fire appliance the traffic mix really contains one time
     * in ten, gulls fly by day, and the lake's life is placed on lanes that are fractions of the
     * band rather than at constant offsets.
     *
     * **Nothing here decides what a theme contains**: every object stays conditional on the flag
     * the wallpaper reads, the fir included since v5.8E (V3-48). What changed is only where
     * the objects stand, and `ThemePreviewTruthTest` is the measure that keeps it honest: it
     * re-runs the census on every built-in and on a custom customization, and fails if a family
     * the scene draws is missing from the card, if the card invents one, if a boat or a dolphin
     * has ink outside the water, or if anything is more than half covered by what follows it.
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
        // Night for the two themes whose subject *is* the night: the fireworks theme and the
        // horror sky. Everything else reads its day palette, which is what a gallery is for.
        val night = forceNight ?: (c.horrorSkyEnabled || theme.hasFireworks)
        // Every colour below that the wallpaper blends between day and night is blended here on
        // this one number, so the card is one moment of the wallpaper's day and not a mixture.
        val phase = cardPhase(theme, night)
        val dayBlend = phase.dayBlend
        // The card's hour, at which each building is as open as the wallpaper's own [BusinessHours]
        // rule makes it -- by its group, the shops' or the towers' hours (v5.12), by what it is drawn
        // as ([SceneCustomization.opennessFor]): 1 with a group's switch off, which is the default, so
        // only a user who turns opening hours on sees a night card's shops or towers go dark as the
        // wallpaper's do (v5.9G, I-38).
        val hour = cardHour(theme, night)
        // The theme's seed for who stands at a house's windows, the wallpaper's own (`themeId.hashCode()`
        // in `SceneObjectRenderer`): which of a house's windows the card lights at night follows it.
        val seed = theme.id.hashCode()
        // Palms where the switch puts them, read the way the wallpaper reads it: on a layout that
        // plants palms, with it off the palm slots draw the ordinary tree; on one that plants none,
        // with it on the tree slots draw palms (v5.10C, `palmsShown`). Asked of the layout rather
        // than of the theme's name, which said "oaks" about a theme saved from Beach while the
        // wallpaper drew its palms.
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
            // Both drawings untinted, as the wallpaper draws them (`PaperRenderer.drawCelestialBody`):
            // they are finished art, and multiplied by the user's *Sun Color* anything but a warm
            // pick turns the disc near-black. On the wallpaper that colour reaches only the ambient
            // glow around the sun, which the card does not draw. Until v5.9F the card tinted both
            // (inventory I-05), so with a chosen colour it showed a sun the wallpaper never draws.
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
            // dimming, which needs a forecast the card does not have). Until v5.9G the day colour on
            // every card, which only Sunset's hour tells apart: its dayBlend at 19:00 is 0.80.
            val cloudTint = blendRgb(c.clouds.colorNight, c.clouds.colorDay, dayBlend)
            val heavy = drawn(c.precipitation.visible, c.precipitation.intensity)
            backdrop += PreviewItem(70f, 40f, if (heavy) 0.30f else 0.22f,
                listOf(PreviewSprite(R.drawable.cloud_body, -128f, -85f, cloudTint, alpha = 235)))
            backdrop += PreviewItem(206f, 30f, if (heavy) 0.26f else 0.18f,
                listOf(PreviewSprite(R.drawable.cloud_body, -128f, -85f, cloudTint, alpha = 225)))
        }

        // --- birds ------------------------------------------------------------------------------
        // The flock the card never drew. `BirdsConfig.nightBirds` is off in every built-in, so a
        // night card owes none -- and reading the flag rather than the theme's name means a custom
        // theme that turns night birds on gets them here too.
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
                // StarsConfig has no colour of its own, and the scene's stars are fixed cream
                // (`PaperRenderer.STAR_POINT_COLOR`), so the card's are too. Until v5.8B they took
                // the theme's `starColor`, which the wallpaper stopped reading with the V2 artwork.
                dots += PreviewDot(x, y, 0.55f, PaperRenderer.STAR_POINT_COLOR, alpha = 215)
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
            PreviewIdentity.TOWER_X[index], PreviewIdentity.TOWER_DEPTH[index], c, dayBlend, winter, hour, seed,
        )

        if (c.buildings.visible) {
            if (cityLike) {
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
                PreviewIdentity.RESTAURANT_X, PreviewIdentity.RESTAURANT_DEPTH, c, dayBlend, winter, hour, seed)
        }
        if (drawn(c.houses.visible, c.houses.density)) {
            items += buildingItem(80f, ROW_HOUSE_LARGE, 0.40f,
                SceneSpace.SceneVariant.HOUSE_LARGE, SceneObjectType.HOUSE,
                PreviewIdentity.HOUSE_LARGE_X, PreviewIdentity.HOUSE_LARGE_DEPTH, c, dayBlend, winter, hour, seed)
        }
        if (c.buildings.visible) {
            items += buildingItem(176f, ROW_SCHOOL, 0.40f,
                SceneSpace.SceneVariant.SCHOOL, SceneObjectType.SKYSCRAPER,
                PreviewIdentity.SCHOOL_X, PreviewIdentity.SCHOOL_DEPTH, c, dayBlend, winter, hour, seed)
        }
        if (drawn(c.houses.visible, c.houses.density)) {
            items += buildingItem(236f, ROW_HOUSE_SMALL, 0.40f,
                SceneSpace.SceneVariant.HOUSE_SMALL, SceneObjectType.HOUSE,
                PreviewIdentity.HOUSE_SMALL_X, PreviewIdentity.HOUSE_SMALL_DEPTH, c, dayBlend, winter, hour, seed)
        }
        if (c.buildings.visible) {
            items += buildingItem(300f, ROW_BAR, 0.42f,
                SceneSpace.SceneVariant.BAR, SceneObjectType.SKYSCRAPER,
                PreviewIdentity.BAR_X, PreviewIdentity.BAR_DEPTH, c, dayBlend, winter, hour, seed)
        }

        // --- trees ------------------------------------------------------------------------------
        if (drawn(c.trees.visible, c.trees.density)) {
            // Two, or one where the woodland is a scattering -- and a fir and a palm, even in a
            // scattering, where the scene keeps both (v5.10C2). See [cardTrees].
            val kinds = cardTrees(c, layout, layoutPlantsPalms, palms, sparse)
            val xs = if (kinds.size == 1) listOf(262f) else listOf(70f, 262f)
            kinds.forEachIndexed { index, kind ->
                val x = xs[index]
                val leaf = if (c.fallColorsEnabled) FALL_LEAF_COLOURS[index % FALL_LEAF_COLOURS.size] else c.trees.colorAt(0, dayBlend)
                val palm = kind == CardTree.PALM
                val parts = when (kind) {
                    CardTree.FIR -> fir(snow = winter)
                    CardTree.PALM -> palmTree(
                        dead = halloween, frost = winter, shade = c.trees.nightShadeAt(0, dayBlend),
                        lights = c.christmasDecorationsEnabled,
                    )
                    CardTree.TREE -> tree(leaf, winter = winter, halloween = halloween)
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
            // The traffic the scene really carries: it keeps ten vehicles at the default density
            // and one in ten of them is the appliance, which is the loudest thing on the road and
            // was the one body the card never showed.
            cars += PreviewItem(60f, ROW_CARS, 0.42f, car(CarShell.SALOON, c.cars.colorAt(0, dayBlend)))
            cars += PreviewItem(160f, ROW_CARS, 0.40f, fireTruck())
            cars += PreviewItem(262f, ROW_CARS, 0.42f, car(CarShell.ESTATE, c.cars.colorAt(1, dayBlend)))
        }

        // --- what is on the water ---------------------------------------------------------------
        if (c.lake.drawsWater) lakeLife(c, lake, water)

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
            // 120 and 148 stood on the two adults at 118 and 142 and covered one of them by two
            // thirds; 72 and 98 stand beside them.
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
        if (drawn(c.precipitation.visible, c.precipitation.intensity)) {
            var seed = theme.id.hashCode() xor 0x50F1
            val snow = c.precipitation.type == PrecipitationType.SNOW
            // The wallpaper's own rule (`PaperRenderer.drawPrecipitation`): the theme's night colour
            // blended toward its day colour by the day blend. On this card's one moment that is the
            // day colour at noon and the night colour at midnight, so the night cards -- Halloween's
            // and New Year's Eve's -- rain and snow in the colours the wallpaper uses at night. Until
            // v5.9B (inventory I-03, item 73's residue) it was the day colour on every card. The
            // rain is then carried off the card's own sky, as the wallpaper carries it off its own
            // ([cardRainColour], since v5.9F); the snow is not (see there).
            val colour = if (snow) {
                blendRgb(c.precipitation.snowColorNight, c.precipitation.snowColorDay, dayBlend)
            } else {
                cardRainColour(
                    blendRgb(c.precipitation.rainColorNight, c.precipitation.rainColorDay, dayBlend),
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
            // As the wallpaper decides it, 0 % height included (v5.10E, `LakeConfig.drawsWater`).
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
     * The card's water band, anchored the way `PaperRenderer.updateLakeBandY` anchors the scene's.
     *
     * Bottom fixed, growing upward with the height the user sets. The version this replaced pinned
     * the *top* to the horizon and grew downward, which put the surface in the right place only at
     * full height and everywhere else left the lake's life below its own floor.
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
     * brightens on the night ramp. The card has had an hour since v5.8B ([cardPhase]), so on a night
     * card the wallpaper's are lit where the card has none; it draws no lamp, no porch light and no
     * window occupant at any hour, and stays so on the maintainer's answer of 2026-09-29.
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
     * The two mountain layers, in their colours at [dayBlend].
     *
     * Where they stand is the card's own composition; what they look like is the wallpaper's. The
     * Desert used to get a row of low jagged dunes here, and the wallpaper has no desert branch
     * at all -- it draws the Desert's mountains exactly as every other theme's -- so the card
     * showed a horizon the scene never has.
     */
    private fun buildPeaks(c: SceneCustomization, dayBlend: Float, winter: Boolean): List<PreviewPeak> {
        val out = mutableListOf<PreviewPeak>()
        var index = 0
        // At 0 % the wallpaper draws no mountain of the layer, so neither does the card (v5.11, I-414).
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
     * The distant houses on one of the card's mountains (v5.11): **the card follows the switch**, as
     * everything it draws does -- none while *Distant houses* is off or at 0 %, which is every theme
     * as it ships, and on, the wallpaper's three drawings, snow with the winter palette, and the
     * wallpaper's own coins for each house's colour (one of the houses' two) and window (lit in four
     * houses of five) from a seed per mountain of the card. One house a mountain, two from 50 %, at the
     * wallpaper's own places across the slope ([DistantHouses.SLOTS]); the drawings take turns from
     * mountain to mountain, so a card shows all three.
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
        val places = if (c.distantHouses.density >= 0.5f) CARD_HOUSE_PLACES_TWO else CARD_HOUSE_PLACES_ONE
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
            out += PreviewItem(x + along * halfWidth, foot, CARD_HOUSE_UNITS_TALL / piece.height, parts)
        }
        return out
    }

    /** The card's own layer seed for its distant houses' coins: one card, one fixed set of houses. */
    private const val CARD_HOUSE_SEED = 0x0CA4D

    /** How tall a distant house stands on the card, in card units: a fifth of the card's large house. */
    private const val CARD_HOUSE_UNITS_TALL = 9f

    /** Which of [DistantHouses.SLOTS] the card's houses take on a mountain, below 50 % and from it. */
    private val CARD_HOUSE_PLACES_ONE = intArrayOf(3)
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
     * **Why (inventory I-04, v5.9F, on the maintainer's decision of 2026-09-28).** The wallpaper
     * carries its rain off the sky it falls through; the card painted the theme's colour as it was,
     * and on four cards of twelve that colour is the brightness of the card's own sky -- Big City
     * and Beach at noon, Sunset at seven, New Year's Eve as the World & scene strip shows it by day
     * -- so their rain all but vanished (luma 0.44-1.41 clear of the sky, against the wallpaper's
     * 13.47). Now 13.99-14.19. It moves two more cards a little (Christmas 9.90 -> 13.98, Halloween's
     * night card 0.75 -> 13.01, where the blue drops read on the orange by their hue) and no other:
     * a colour already clear is returned unchanged, bit for bit.
     *
     * **Why the rain and not the snow.** The wallpaper corrects both, and for the snow the correction
     * resolves to nothing against its own skies. Against the card's skies the same rule would turn the
     * white snow grey on seven cards while the wallpaper draws it white, which is the opposite of
     * what this is for, so the snow keeps the theme's colour here.
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
     * Where the card's buildings stand, as far as the deal is concerned.
     *
     * A card building's silhouette and its colour both come from `(tileFractionX, depthFraction)`
     * -- the slot composer's position hash for a spec nobody dealt, and `variantIndexFor`'s -- so a
     * preview that wants to show a turret rather than a gable asks for a position, not for a
     * turret.
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
     * at **0.8000**, in every theme, and its towers never go past **0.2667**. So five of the nine
     * depths below -- the restaurant's, the bar's and three of the four towers' -- are positions no
     * building of that family stands at. Nothing is wrong with the *card* that follows from them
     * (a dealt silhouette is a real silhouette wherever the hash lands), but the derivation was
     * not what it claimed, and a future edit "restoring" them to the catalogue would move every
     * shop on every card. They stay as they are, by the maintainer's decision of 2026-09-22 (the
     * cards' direction D1); this comment only stops the sentence being false.
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
     * Most other builders below are hand copies of a call site in `SceneObjectRenderer` (the
     * trees, the palm, the cars, the fire truck and the dolphin read shared constants instead),
     * and `PreviewRendererAgreementTest` exists because hand copies drift -- it was written when
     * the winter tree's snow cap had been sitting three units right and two down of the
     * wallpaper's for two releases. A stack of pieces chosen per instance is far more than a snow
     * cap's worth of arithmetic to copy, so it is not copied: [NeighbourhoodComposer] deals it and
     * this reads out the result.
     *
     * The card deals from a position it picks for a picture, through the position hash the
     * wallpaper uses for a spec nobody dealt (a pre-v5.5 custom theme, a shuffled layout); a
     * built-in scene building instead wears the silhouette dealt at generation. The colour comes
     * from the position on both sides, and both paths stack the pieces with the same arithmetic,
     * so there is no second copy of that to disagree -- the agreement test checks the card against
     * the position deal rather than comparing two lists of literals.
     *
     * ### Two things the preview leaves out, and why
     *
     * `OCCUPANTS` and `LAMP` are call-outs to behaviours that animate (`WindowOccupants` picks a
     * bust from the scene's own people, a porch light glows on the night ramp). The preview is a
     * still 320x240 card with no people layer; it has never drawn either. It has an hour since
     * v5.8B ([cardPhase]), so a night card's porches are dark where the wallpaper's are lit, and
     * that stays so on the maintainer's answer of 2026-09-29 (no porch lights, car lamps or window
     * occupants on a card, at any hour). Snow is drawn, because `winterColorsEnabled` is a palette
     * the user edits and a preview that cannot show it is not much of a preview.
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
    ): List<PreviewSprite> {
        val family = NeighbourhoodTable.FAMILIES[variant] ?: return emptyList()
        // The real object, so the real coin: which of the two colours this building wears is
        // `variantIndexFor`'s answer about this very position, not a choice the preview makes. The
        // preview used to invent its own blends here -- a tower 15 % towards white, a restaurant
        // 30 % -- which showed the user a colour no building of theirs would ever be.
        //
        // **Which pair, by what the card draws** (v5.11): three of the card's four towers stand at
        // depths the scene keeps for shops ([PreviewIdentity]), so asking by the object's depth would
        // paint them in the shops' colours. The card names the drawing, and the drawing names the pair.
        val spec = StaticSceneObject(type, depthFraction = depth, tileFractionX = tileX)
        val wall = c.wallColourFor(spec, variant, dayBlend)
        // `SceneObjectRenderer.drawNeighbourhoodBuilding`'s glass: a house's windows lit by the night
        // alone, a business's by the night and its opening hours at the card's hour. Until v5.9G the
        // card lit every building by the night alone, so with opening hours on a midnight card's
        // shops and towers glowed where the wallpaper's are dark.
        val night = 1f - dayBlend
        val openness = c.opennessFor(variant, hour)
        val glass = if (family.kind == WindowBuildingKind.HOUSE) {
            SceneObjectRenderer.windowGlassColor(night)
        } else {
            SceneObjectRenderer.businessGlassColor(night, openness)
        }
        val deal = NeighbourhoodComposer.Deal()
        NeighbourhoodComposer.deal(family, tileX, depth, deal)
        // A house at night lights half its windows, as the wallpaper's does (v5.12, inventory I-520):
        // the same rule from the same seeds -- the theme's and the house's -- at the first moment of
        // its roster, since a card is one moment and does not move. The people are not drawn (the card
        // leaves them out, §9 of DESIGN_NOTES.md), but the windows they would stand at are among the
        // lit ones, as on the wallpaper.
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
        dayBlend: Float,
        snow: Boolean,
        hour: Float,
        seed: Int,
    ): PreviewItem = PreviewItem(
        x, y, neighbourhoodScale(variant, fit),
        neighbourhood(variant, type, tileX, depth, c, dayBlend, snow, hour, seed),
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

    /** What the card stands on its tree row, left to right. */
    private enum class CardTree { TREE, FIR, PALM }

    /**
     * The card's trees: two, whatever the density, and one where the woodland is a scattering -- a
     * third has no place on the row that does not stand in front of a shop.
     *
     * **As before v5.10C2, except under palms in the trees' places with the Christmas layer on.** The
     * palms are the switch's (`palmsShown`), and Christmas is the layer that puts firs among the trees,
     * and the only one: a sparse wood is not a reason for a fir (v5.8E, V3-48). So with the palms the
     * card shows palms, and without them a fir first under the layer.
     *
     * Under both, on a layout that plants no palm of its own, the scene keeps two species: its firs,
     * and a palm in every other tree's place (`palmSpeciesApplied`, the maintainer's *«gli abeti sono del
     * tema e tali devono rimanere»*, 2026-10-03). Which of them a thinned wood keeps is the layout's
     * deal -- Christmas at 20 % keeps two trees and both are firs -- so the card asks the scene, through
     * the same two functions the renderer builds its list with, and shows a fir and a palm when it
     * stands both, even in a scattering: one tree cannot be two species, and leaving either out is the
     * card saying less than the scene. Beach and Desert are not this case: their palm slots are palms
     * whatever the layer says, and with their palms on they stand no fir, as before.
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
     * v4.21 moved both of these, and they are the reason this function is worth a comment.
     *
     * The fir's own origin went from (-39,-122) to (-40,-122) because the new sprite is 80 units
     * wide rather than 78. The snow's went from the fir's origin to (-28,-112) of its own, because
     * `tree_fir_snow` is no longer a full-canvas copy of the fir: it is trimmed to its content and
     * carries the offset of the cut. **The two are one derivation** — the same pair of numbers
     * appears in `SceneObjectRenderer.drawFir`, and a redraw that moves one and not the other
     * slides the snow off the shoulders it was cut for.
     *
     * The baubles below stay where they are: `star_sparkle` blits placed by eye on a flat 320x240
     * card, not the wallpaper's `drawChristmasLights` ellipse, whose six lights would be under
     * 2 px here. At 46 px of tree they read as baubles, and the maintainer decided on 2026-09-28
     * to leave them (inventory I-80).
     */
    private fun fir(snow: Boolean): List<PreviewSprite> {
        val parts = mutableListOf(PreviewSprite(R.drawable.tree_fir, -40f, -122f))
        if (snow) parts += PreviewSprite(R.drawable.tree_fir_snow, -28f, -112f)
        // Always lit: a card fir exists only where the wallpaper's does, which is under the
        // Christmas decorations (v5.8E -- the unlit sparse-wood fir was the one without them).
        parts += PreviewSprite(R.drawable.star_sparkle, -26f, -110f, 0xFFF2C14E.toInt(), alpha = 220)
        parts += PreviewSprite(R.drawable.star_sparkle, 2f, -84f, 0xFFE8483C.toInt(), alpha = 220)
        parts += PreviewSprite(R.drawable.star_sparkle, -22f, -58f, 0xFF5BC0EB.toInt(), alpha = 220)
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
     * **[shade] is the wallpaper's light on it**: [nightShadeAt] of the trees' first colour pair at
     * the card's moment, the grey `drawPalmTree` multiplies both drawings by, and the tree beside
     * it wears the same pair's colour ([colorAt]). Full daylight at noon, so a day card's palm is
     * the untinted blit it always was. Until v5.9G neither the palm nor the leafy tree read the
     * hour, and on a night card both kept their noon light (inventory I-38).
     *
     * **[lights], the Christmas lights the wallpaper strings on a palm** (`drawPalmTree` calls
     * `drawChristmasLights` on the crown's centre, Halloween's dead crown included). Until v5.10C the
     * card's palm had none, which was a card saying less than its scene only on a Beach or a Desert
     * someone had lit; since then palms can stand on Christmas, where they are lit by default, and
     * `ThemePreviewTruthTest` found the card's Christmas without a light. **One** of the fir's
     * baubles ([fir]), not its three: the `star_sparkle` canvas is 60 units with its ink centred about
     * (30, 29), as big as the whole 56x48 crown, and three of them hid the palm on the card
     * (photographed on the BV6600, v5.10C). It sits on the crown's top edge, over the centre the
     * wallpaper hangs its string on, mostly in the sky -- placed by eye, like the fir's.
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
     * The pumpkin as [SceneObjectRenderer.drawPumpkin] builds it: the tinted body, the carved face
     * where `halloweenEnabled` is on ([carved]), the stem. Until v5.9G the card never drew the face,
     * so Halloween's card showed smooth pumpkins under a carved moon while its wallpaper carves
     * every one (repaired on the maintainer's answer of 2026-09-29).
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
        // `PaperRenderer.drawSailboat`. v4.31 cropped three sprites -- these two and
        // `dolphin_body` -- and the preview is a second call site for every one of them;
        // `normalize --apply` reported "single call site" for all three because it could not
        // resolve them, and compensating only the renderer would have left the gallery card's
        // boats shifted.
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
     * The preview's own name for the scene's blend, kept because a hundred call sites read better
     * with it. The arithmetic moved to [SceneColour] in v5.0: this file used to *define* it, which
     * made it a second answer to "what is a colour between two colours" living beside the
     * renderer's.
     */
    private fun blendRgb(from: Int, to: Int, ratio: Float): Int = SceneColour.blendArgb(from, to, ratio)
}
