package com.paperscrape.livewallpaper.engine

/**
 * Reusable per-category settings: visibility, density, and 2 color variants (each with a
 * day/night version) -- the same customization "shape" applied uniformly to every object
 * category below rather than duplicated per type.
 *
 * Colors: each individual instance of the category is deterministically assigned variant 1 or 2
 * (stable, based on its position -- see [SceneObjectRenderer]'s `variantIndexFor`), and blends
 * between that variant's day and night color exactly like the rest of the scene does.
 */
data class ObjectVariantConfig(
    val visible: Boolean,
    /** 0f..1f — fraction of a theme's candidate slots for this category that actually render. */
    val density: Float,
    val colorDay1: Int,
    val colorNight1: Int,
    val colorDay2: Int,
    val colorNight2: Int,
    /** Who owns variant 1's pair -- see [AutoColorMode]. */
    val autoMode1: AutoColorMode = AutoColorMode.MANUAL,
    /** Who owns variant 2's pair. Independent of [autoMode1]: the two variants are two colours. */
    val autoMode2: AutoColorMode = AutoColorMode.MANUAL,
)

/** Lighter-weight sibling of [ObjectVariantConfig] for background silhouette layers (mountains)
 * that want a single day/night color pair (like hills) rather than 2 color variants -- mountain
 * peaks all share one consistent silhouette tone, they don't need per-instance color variety the
 * way discrete objects like houses do. */
data class MountainLayerConfig(
    val visible: Boolean,
    val density: Float,
    val colorDay: Int,
    val colorNight: Int,
    val autoMode: AutoColorMode = AutoColorMode.MANUAL,
)

/** A body of water, drawn as its own independent backdrop band (not part of the hill/object
 * row-placement system, for the same safety reasons as [MountainLayerConfig]), plus two nested
 * decorations (sailboats, dolphins) that appear within it. */
data class LakeConfig(
    val visible: Boolean,
    val colorDay: Int,
    val colorNight: Int,
    /** 0f..1f — how tall a band the lake occupies. */
    val height: Float,
    val sailboatsVisible: Boolean,
    val sailboatsDensity: Float,
    val dolphinsVisible: Boolean,
    val dolphinsDensity: Float,
    val autoMode: AutoColorMode = AutoColorMode.MANUAL,
)

/** One of a bird's 4 selectable colors, with a relative weight controlling how often it's picked
 * (not a uniform 1-in-4 -- "Bird Color Frequencies" in the UI). Weights don't need to sum to
 * anything in particular; a bird's color is picked by weighted-random draw across all 4 (see
 * [BirdsConfig.pickColor]). */
data class BirdColorWeight(val color: Int, val weight: Float)

/** Stars: same show/hide + density shape as everything else, but no color -- stars have always
 * used a single fixed twinkle color per theme (see [SunConfig]/[MoonConfig] for the celestial
 * bodies, which *do* get their own color). */
data class StarsConfig(val visible: Boolean, val density: Float)

/**
 * The sky gradient's 6 color stops. Deliberately not the same shape as the old
 * `theme.skyDay`/`skyNight`/`skyDawn`/`skyDusk` (4 arrays of 2 colors each, blended with a
 * "twilight bump" near the terminator) -- that model doesn't map onto anything a user could
 * reasonably edit by hand. This is the simpler, user-facing version: a top ("High") and bottom
 * ("Low") color for day and for night, blended continuously by [SunPositionCalculator.DayPhase]'s
 * `dayBlend` the same way everything else in the scene already blends -- plus two *dedicated*
 * near-horizon colors (`colorSunriseLow`/`colorSunsetLow`) that briefly show through only near
 * their respective terminator, using the exact same twilight-weighting math the old 4-array model
 * used, just with these single colors instead of a whole separate palette. Only the bottom needs
 * dedicated sunrise/sunset colors -- the top of the sky doesn't change much during a
 * sunrise/sunset in reality, the warm glow is a near-horizon phenomenon.
 */
data class SkyConfig(
    val colorDayHigh: Int,
    val colorDayLow: Int,
    val colorNightHigh: Int,
    val colorNightLow: Int,
    val colorSunriseLow: Int,
    val colorSunsetLow: Int,
    /**
     * How high the sun and moon's arc rises, as a fraction of screen height, and with it how high
     * the cloud band sits. Stored in scene terms, [SUN_CLOUD_HEIGHT_MIN]..[SUN_CLOUD_HEIGHT_MAX];
     * the settings slider shows that range as a plain 0-100%.
     *
     * One value for both is deliberate and always was: the clouds belong to the same sky the sun
     * crosses, and a scene whose sun peaks low while its clouds sit high reads as two skies. The
     * v2.11 coupling was real but nearly invisible -- the whole slider moved the cloud band by
     * about 7% of screen height -- which is why it was reported as "the slider doesn't move the
     * clouds". See `PaperRenderer.cloudBandTop`.
     */
    val sunCloudHeight: Float,
    /** Who owns the High pair. The two sky bands are two colours, so they toggle separately. */
    val autoModeHigh: AutoColorMode = AutoColorMode.MANUAL,
    /**
     * Who owns the Low pair.
     *
     * `colorSunriseLow`/`colorSunsetLow` deliberately have no mode: they are single colours with
     * no day/night twin to derive from or for, and inventing one would mean inventing what "the
     * night version of a sunrise" is.
     */
    val autoModeLow: AutoColorMode = AutoColorMode.MANUAL,
)

data class SunConfig(val visible: Boolean, val color: Int)

data class MoonConfig(
    val visible: Boolean,
    val color: Int,
    /** When false, always draws a plain full disc instead of the real astronomical phase
     * (new/crescent/quarter/gibbous/full) -- some users may prefer a simple decorative moon over
     * one that's occasionally a sliver or fully dark. */
    val realisticPhases: Boolean,
)

/** Puffy clouds drifting slowly across the upper sky -- same independent-candidate-pool
 * philosophy as mountains/birds (own parallax, own density filter, no interaction with the
 * hill/object row-placement system). */
data class CloudsConfig(
    val visible: Boolean,
    val density: Float,
    val colorDay: Int,
    val colorNight: Int,
    val autoMode: AutoColorMode = AutoColorMode.MANUAL,
)

/** Which of the two mutually-exclusive precipitation looks [PrecipitationConfig] renders --
 * matches how real weather works (it's either raining or snowing, not both at once). */
enum class PrecipitationType { RAIN, SNOW }

/** Falling rain or snow, drawn as the closest thing in the whole scene (see
 * [PaperRenderer.draw]'s call order) -- real precipitation reads as being right in front of the
 * "camera", in front of even houses and cars, not part of the backdrop the way clouds/mountains
 * are. [type] picks which of the two actually renders; both keep their own independent color pair
 * ([rainColorDay]/`Night` vs [snowColorDay]/`Night`) so switching types doesn't force a user to
 * re-pick colors that made sense for the other one. [thunderstorm] adds occasional lightning
 * flashes (see [PaperRenderer.drawLightningFlash]) -- kept as its own flag rather than a third
 * [type] value so toggling storms on/off doesn't lose the user's rain settings, though it's only
 * meaningful while [type] is [PrecipitationType.RAIN].
 */
data class PrecipitationConfig(
    val visible: Boolean,
    val type: PrecipitationType,
    /** 0f..1f — how many drops/flakes fall at once. */
    val intensity: Float,
    val rainColorDay: Int,
    val rainColorNight: Int,
    val snowColorDay: Int,
    val snowColorNight: Int,
    val thunderstorm: Boolean,
    /** Rain keeps its own mode, for the same reason it keeps its own colour pair. */
    val rainAutoMode: AutoColorMode = AutoColorMode.MANUAL,
    val snowAutoMode: AutoColorMode = AutoColorMode.MANUAL,
)

/** A decorative paper-cutout rainbow arc. Deliberately independent of [PrecipitationConfig] --
 * unlike real weather (which Phase 1d's Random/Live Weather will eventually simulate), this is a
 * manual per-theme toggle, so a user can put a rainbow on a sunny theme without needing rain
 * turned on first, the same freedom every other decorative category in this app already has.
 * Fades out toward night in [PaperRenderer.drawRainbow] (rainbows are a daylight phenomenon)
 * rather than a hard on/off cut. */
data class RainbowConfig(
    val visible: Boolean,
    /** 0f..1f — how vivid the bands render at full daylight. */
    val opacity: Float,
)

/** An ambient flock of birds crossing the sky, independent of the hill/object row-placement
 * system (birds fly, they don't stand on the ground). */
data class BirdsConfig(    val visible: Boolean,
    val density: Float,
    val nightBirds: Boolean,
    val colors: List<BirdColorWeight>,
) {
    /** Weighted-random pick among [colors] using [randomFraction] (caller-supplied so the same
     * fraction can be reused deterministically for a given bird instance rather than re-rolling
     * every frame). */
    fun pickColor(randomFraction: Float): Int {
        val totalWeight = colors.sumOf { it.weight.toDouble() }.toFloat()
        if (totalWeight <= 0f) return colors.firstOrNull()?.color ?: 0xFFFFFFFF.toInt()
        var target = randomFraction.coerceIn(0f, 1f) * totalWeight
        for (c in colors) {
            target -= c.weight
            if (target <= 0f) return c.color
        }
        return colors.last().color
    }

    /**
     * How present the flock is at this `dayBlend`, 1 while the sun is up and 0 once night is in.
     *
     * **This used to be `dayBlend` itself, and that was the bug.** `dayBlend` holds at 1 across the
     * middle of the daylight arc -- `SunPositionCalculator.smoothEdge` only eases its first and
     * last 12% -- and then slides down to `TERMINATOR_BLEND` (0.5) at the moment the sun sets. So
     * multiplying the birds' alpha by it left them solid all day and then **bled them out through
     * the whole of the golden hour**: with the default 06:00/20:00 arc, 90% opaque at 18:40, 80% at
     * 19:00, and **half transparent exactly at sunset**, with the sky behind them showing through
     * the whole time. Measured on a OnePlus 6T at a fixed 20:00: alpha 0.47-0.53 across six frames
     * before this function existed, 1.00-1.02 after.
     *
     * That is dusk, which is when the flock is most visible against a bright sky and most worth
     * looking at, and it is the one time of day the birds were least there. Nothing else in this
     * scene is see-through as a *state*: windows crossfade their colour, precipitation fades only
     * at the two ends of its fall. A paper cutout you can see the sky through is a rendering
     * artefact, not a dusk.
     *
     * So the flock is fully opaque for the whole time the sun is above the horizon and leaves over
     * the first half of the below-horizon range -- about 35 minutes on that same arc, done well
     * before the moon is up. The intent -- no birds after dark unless [nightBirds] -- is unchanged;
     * only the shape is.
     */
    fun presenceAt(dayBlend: Float): Float {
        if (nightBirds) return 1f
        return ((dayBlend - GONE_BELOW) / (FULL_ABOVE - GONE_BELOW)).coerceIn(0f, 1f)
    }

    companion object {
        /**
         * `dayBlend` at sunrise and sunset. It is `SunPositionCalculator.TERMINATOR_BLEND`, which is
         * private there; the value is restated rather than the field opened up, because what this
         * needs is "the horizon", and the horizon is what that constant means.
         */
        const val FULL_ABOVE = 0.5f

        /** Halfway down the below-horizon range: the flock is gone before full dark. */
        const val GONE_BELOW = 0.25f
    }
}

/**
 * Global (theme-independent) rendering settings for every customizable object category, editable
 * from the "Scene Objects" screen. These apply on top of whichever theme/custom theme is active:
 * a theme's [SceneObjectLayout] defines *candidate* slots (see [SceneObjectCatalog]), and this
 * config decides how many of them actually show up and what colors they use.
 */
data class SceneCustomization(
    val houses: ObjectVariantConfig,
    val buildings: ObjectVariantConfig,
    val cars: ObjectVariantConfig,
    val parasols: ObjectVariantConfig,
    /**
     * The pedestrians, as a category with visibility and density like any other.
     *
     * Only two of the six fields mean anything here: the walk sprites are finished art in four
     * kinds across two seasons, so there is nothing for a colour to reach. Which kind and which
     * season a given pedestrian is stays exactly as it was -- density decides how many of the four
     * candidates render, not which ones exist.
     */
    val people: ObjectVariantConfig,
    /**
     * How many pedestrians walk **after dark**, 0f..1f.
     *
     * [people]`.density` is the daytime figure; this is the same thing for the night side, and the
     * renderer crossfades between them with the scene's own `dayBlend` rather than switching at a
     * threshold -- a street that empties over the length of dusk, not one where four people vanish
     * between two frames.
     *
     * Deliberately a field of its own rather than a second density on every
     * [ObjectVariantConfig]: pedestrians are the only category whose population plausibly depends
     * on the hour, and giving houses and mountains a night density would be a preference that can
     * never mean anything. It governs the same pedestrians `people.density` always has -- drivers,
     * passengers and the figures in lit windows are drawn elsewhere and are untouched.
     */
    val peopleNightDensity: Float = DEFAULT_PEOPLE_NIGHT_DENSITY,
    val trees: ObjectVariantConfig,
    // Fall Colors / Winter-Christmas Colors: NOT their own placeable object category (no
    // visibility/density/color-variant shape like the seasonal decorations below) -- they're a
    // seasonal *palette override* applied on top of the existing `trees` category's own leaf
    // rendering (see SceneObjectRenderer.drawTree). Deliberately toggled from the Seasonal
    // Decorations screen, not the Trees screen under Scene Objects: aa's own framing is that the
    // Trees show/density/color toggle is a *structural* scene-object setting (does this theme
    // have trees at all, and what base color), while whether those trees currently look
    // autumnal/snowy is a decoration a user can flip on for *any* theme at *any* time, exactly
    // like turning pumpkins on for a non-Halloween theme. Mutually exclusive (a tree can't
    // simultaneously be shedding red/orange leaves and be snow-dusted) -- enforced in
    // WallpaperPrefs.setFallColorsEnabled/setWinterColorsEnabled by clearing the other flag in
    // the same edit, the same pattern PrecipitationConfig.type already uses for Rain vs Snow.
    // Off by default, like every other opt-in seasonal decoration in this class.
    val fallColorsEnabled: Boolean = false,
    val winterColorsEnabled: Boolean = false,
    /**
     * The Christmas decoration layer: lights on the trees, and whatever is added to it later.
     *
     * **Separate from [winterColorsEnabled], and deliberately so.** Christmas lights used to hang
     * off the winter flag, which made the two words synonyms: a plain snowy January scene could
     * not exist without fairy lights on every tree, and a Christmas scene could not exist without
     * committing to a full winter presentation. They are different statements — one is a season,
     * the other is a fortnight of decorations inside it — and every combination of the two is a
     * scene somebody might want.
     *
     * Neither flag implies the other. Turning this on does not turn winter on.
     *
     * **Scope.** This governs the Christmas dressing that has no category of its own. Santa
     * ([santaEnabled]) and the presents ([gifts]) keep their own switches, because they already
     * had them and folding them in here would give one thing two controls that disagree. A theme's
     * defaults set all three together; a user can still take any of them separately.
     */
    val christmasDecorationsEnabled: Boolean = false,
    /**
     * The Halloween presentation: a skull moon, and every tree stripped to bare branches.
     *
     * **A third independent statement, alongside [winterColorsEnabled] and
     * [christmasDecorationsEnabled], and it implies neither.** The lesson v2.0 recorded about
     * winter and Christmas applies here in advance: a season and a decoration layer are different
     * things, and folding one into the other is what made "winter" and "Christmas" synonyms for a
     * whole release. Halloween is not a temperature and not a fortnight of fairy lights, so it
     * gets its own flag rather than a shared one, and turning it on changes nothing about winter,
     * Christmas, New Year or the fall palette.
     *
     * **Scope, deliberately narrow.** Two things follow from it: the moon becomes
     * `moon_jack_o_lantern`, and every tree drops its canopy for `tree_dead_branches`. The pumpkins already
     * have their own switch and keep it, for the same reason Santa keeps his -- one thing with two
     * controls that can disagree is worse than two things with one each.
     *
     * The sky is **not** part of this. See [horrorSkyEnabled].
     */
    /**
     * Flowers on the open ground: on or off, and nothing else.
     *
     * **A plain boolean rather than an `ObjectVariantConfig`, on purpose.** Every other decoration
     * in this class carries visibility, density and a day/night colour pair, and that is right for
     * a snowman or a gift, which are objects a theme can restyle. A meadow is not: flowers whose
     * colour follows a theme's building tint are a meadow of the wrong flowers. The artwork is
     * fixed -- three kinds at three sizes on one canvas -- and the only decision left to make is
     * whether they are there.
     */
    val flowersEnabled: Boolean = false,
    val halloweenEnabled: Boolean = false,
    /**
     * The horror sky: near-black overhead, a hard orange band at the horizon.
     *
     * **Separate from [halloweenEnabled] on purpose, and all four combinations are reachable.** A
     * scene can be a bare-tree, skull-moon Halloween under an ordinary night sky, and an ordinary
     * scene can sit under a lurid orange one -- neither reading is wrong, and tying them together
     * would repeat exactly the mistake winter and Christmas were split to undo.
     *
     * It overrides the six user sky colours while it is on rather than editing them, so switching
     * it off returns the palette the user chose, untouched.
     */
    val horrorSkyEnabled: Boolean = false,
    // Previously hardcoded per-theme via SceneTheme.hasSantaSleigh with no user control at all --
    // aa asked for an actual toggle. Kept as a per-theme customization (not a global setting)
    // specifically so it fits the same defaultCustomizationFor() pattern every other per-theme
    // toggle already follows: its default seeds from theme.hasSantaSleigh (true only for the
    // Christmas theme) so nothing changes for a user who's never touched this setting, but it can
    // now be flipped independently per theme just like Fall Colors/Winter Colors above.
    val santaEnabled: Boolean = false,
    // Seasonal decorations -- opt-in extras, off by default (see SeasonalDecorations screen),
    // placeable on any theme regardless of "traditional" season. Same ObjectVariantConfig shape
    // as everything above, just defaulting to visible=false since these are meant to be
    // deliberately turned on, not part of a theme's base look.
    val snowmen: ObjectVariantConfig,
    val gifts: ObjectVariantConfig,
    val penguins: ObjectVariantConfig,
    val bunnies: ObjectVariantConfig,
    val easterEggs: ObjectVariantConfig,
    val pumpkins: ObjectVariantConfig,
    // Not an "object" category (no visibility/density/color-variant shape) -- a single
    // theme-scoped float for how wavy the hill silhouette is. Reuses the exact same per-theme
    // pendingCustomization/save-to-theme machinery as everything else in this class, since it's
    // a piece of a *theme's* look just like everything above, not a global rendering preference
    // (see PaperRenderer.buildBaseHillPath's own doc comment for how it's applied safely).
    val hillsVariation: Float = 1f,

    /**
     * How much settled snow lies about, 0..1, and only while [winterColorsEnabled] is on.
     *
     * A slider rather than a switch because "some" is the interesting answer: a hard 0 is the
     * default, so a scene nobody has touched is exactly the scene v4.16 drew, and 1 is as much as
     * the ground can carry before it stops reading as drifts and starts reading as a white floor.
     *
     * Gated on winter for the same reason `tree_canopy_snowcap` is: snow on the ground of a summer
     * theme is not a decoration, it is a mistake. Christmas inherits it, because Christmas turns
     * winter colours on.
     */
    val snowPiles: Float = 0f,

    /**
     * The autumn counterpart, 0..1, and only while [fallColorsEnabled] is on.
     *
     * **Independent of the falling leaves, and deliberately so.** `drawFallingLeaves` animates
     * leaves coming off the crowns; this lies heaps on the ground. Turning one off does not touch
     * the other, and the two are separate settings because "I want the ground covered" and "I want
     * leaves in the air" are separate wishes.
     */
    val leafPiles: Float = 0f,
    // Same idea, two more theme-scoped plain fields for the hills' base color (day and night) --
    // the 3 layers auto-derive their own shade from a single color (see PaperRenderer's
    // hillLayerColor()) rather than needing 3 separate color pickers, matching how every other
    // customizable category in this app exposes exactly one color pair, not one per depth layer.
    val hillsColorDay: Int = 0xFFF2A65A.toInt(),
    val hillsColorNight: Int = 0xFF2E2A55.toInt(),
    /** Who owns the hills pair. Lives here because the hills colours do, not inside a config. */
    val hillsAutoMode: AutoColorMode = AutoColorMode.MANUAL,
    // Mountains: two independent background silhouette layers, drawn behind the hills with a
    // slower parallax than even the farthest hill layer -- entirely separate from the hill/object
    // row-placement system (SceneSpace's own depth band) on purpose, to avoid any risk
    // to that already-tuned safety geometry. Visible by default (unlike seasonal decorations --
    // these read as a normal part of the landscape, not an opt-in extra).
    val mountainsFront: MountainLayerConfig = MountainLayerConfig(
        visible = true, density = 0.5f, colorDay = 0xFF4CAF7C.toInt(), colorNight = 0xFFA9C2B8.toInt(),
    ),
    val mountainsBack: MountainLayerConfig = MountainLayerConfig(
        visible = true, density = 0.5f, colorDay = 0xFF3E8F68.toInt(), colorNight = 0xFF8FA69C.toInt(),
    ),
    // Off by default -- unlike mountains, not every theme's landscape should have a lake
    // appearing in it unless the user actually wants one.
    val lake: LakeConfig = LakeConfig(
        visible = false,
        colorDay = 0xFF2FA8D8.toInt(),
        colorNight = 0xFF1F4A5C.toInt(),
        height = 0.33f,
        sailboatsVisible = true,
        sailboatsDensity = 0.3f,
        dolphinsVisible = true,
        dolphinsDensity = 0.3f,
    ),
    // Visible by default with a modest density -- a light scattering of birds reads as a normal
    // part of an outdoor scene, the same way houses/trees do, not an opt-in extra.
    val birds: BirdsConfig = BirdsConfig(
        visible = true,
        density = 0.5f,
        nightBirds = false,
        colors = listOf(
            BirdColorWeight(0xFFFFFFFF.toInt(), 0.4f),
            BirdColorWeight(0xFF2E323C.toInt(), 0.3f),
            BirdColorWeight(0xFFE8564F.toInt(), 0.15f),
            BirdColorWeight(0xFF4F8FBF.toInt(), 0.15f),
        ),
    ),
    val stars: StarsConfig = StarsConfig(visible = true, density = 1f),
    // Sky/sun/moon defaults below are generic placeholders -- defaultCustomizationFor() always
    // overrides them per-theme (derived from that theme's own existing skyDay/skyNight/sunColor/
    // moonColor), so these only matter as a fallback for unknown/custom theme ids.
    val sky: SkyConfig = SkyConfig(
        colorDayHigh = 0xFF6EC6FF.toInt(),
        colorDayLow = 0xFFCDEFFF.toInt(),
        colorNightHigh = 0xFF0B0E2E.toInt(),
        colorNightLow = 0xFF1B1B3A.toInt(),
        colorSunriseLow = 0xFFFFD59E.toInt(),
        colorSunsetLow = 0xFFFFC98B.toInt(),
        sunCloudHeight = 0.42f,
    ),
    val sun: SunConfig = SunConfig(visible = true, color = 0xFFFFE3B0.toInt()),
    val moon: MoonConfig = MoonConfig(visible = true, color = 0xFFE8ECF5.toInt(), realisticPhases = true),
    val clouds: CloudsConfig = CloudsConfig(
        visible = true, density = 0.4f, colorDay = 0xFFFFFFFF.toInt(), colorNight = 0xFF4A5568.toInt(),
    ),
    // Off by default -- like the lake, this is weather a user opts into rather than something
    // that should permanently rain/snow on every theme out of the box.
    val precipitation: PrecipitationConfig = PrecipitationConfig(
        visible = false,
        type = PrecipitationType.RAIN,
        intensity = 0.5f,
        rainColorDay = 0xFF7FB3E0.toInt(),
        rainColorNight = 0xFF3F5C78.toInt(),
        snowColorDay = 0xFFFFFFFF.toInt(),
        snowColorNight = 0xFFB8C4D0.toInt(),
        thunderstorm = false,
    ),
    val rainbow: RainbowConfig = RainbowConfig(visible = false, opacity = 0.8f),
) {
    companion object {
        val DEFAULT = SceneCustomization(
            houses = ObjectVariantConfig(
                visible = true,
                // aa reported the placement band feeling too crowded and asked to compare
                // against the reference. Confirmed: houses/buildings/parasols/trees all
                // defaulted to density=1f (every one of CANDIDATES_PER_CATEGORY's 10 slots
                // shown), stacking across every depth band at once. Lowered to a more open
                // 0.65 -- still user-adjustable via each category's own density slider in either
                // direction, this only changes what a *fresh, untouched* theme looks like.
                density = 0.65f,
                // Matches the wall color PaperScrape always used before this became configurable.
                colorDay1 = 0xFFF3E6D0.toInt(),
                colorNight1 = 0xFF6B5F52.toInt(),
                colorDay2 = 0xFFE9D6C7.toInt(),
                colorNight2 = 0xFF5C4A45.toInt(),
            ),
            buildings = ObjectVariantConfig(
                visible = true,
                density = 0.65f, // see houses' own comment on this same default-density change
                // Matches the wall color PaperScrape always used before this became configurable.
                colorDay1 = 0xFF454B57.toInt(),
                colorNight1 = 0xFF262A31.toInt(),
                colorDay2 = 0xFF5C6A78.toInt(),
                colorNight2 = 0xFF303842.toInt(),
            ),
            cars = ObjectVariantConfig(
                visible = true,
                density = 1f,
                colorDay1 = 0xFFF2A65A.toInt(),
                colorNight1 = 0xFFB5651D.toInt(),
                colorDay2 = 0xFF6FA8DC.toInt(),
                colorNight2 = 0xFF3D6B94.toInt(),
            ),
            people = ObjectVariantConfig(
                visible = true,
                density = 1f,
                colorDay1 = 0, colorNight1 = 0, colorDay2 = 0, colorNight2 = 0,
            ),
            peopleNightDensity = DEFAULT_PEOPLE_NIGHT_DENSITY,
            parasols = ObjectVariantConfig(
                visible = true,
                density = 0.65f, // see houses' own comment on this same default-density change
                // Matches the fixed colors PaperScrape always used before this became configurable.
                colorDay1 = 0xFFFF7043.toInt(),
                colorNight1 = 0xFFB5502E.toInt(),
                colorDay2 = 0xFFF7FAFC.toInt(),
                colorNight2 = 0xFFAEB4B8.toInt(),
            ),
            trees = ObjectVariantConfig(
                visible = true,
                density = 0.65f, // see houses' own comment on this same default-density change
                // Matches the fixed foliage color PaperScrape always used before this became configurable.
                colorDay1 = 0xFF8AA25C.toInt(),
                colorNight1 = 0xFF3F4A2A.toInt(),
                colorDay2 = 0xFF3F9E6B.toInt(),
                colorNight2 = 0xFF244A34.toInt(),
            ),
            snowmen = ObjectVariantConfig(
                visible = false,
                density = 0.5f,
                // Matches the fixed snow color previously hardcoded in drawSnowman.
                colorDay1 = 0xFFF7FAFC.toInt(),
                colorNight1 = 0xFFAEB4B8.toInt(),
                colorDay2 = 0xFFEAF3FA.toInt(),
                colorNight2 = 0xFF9BA7B0.toInt(),
            ),
            gifts = ObjectVariantConfig(
                visible = false,
                density = 0.5f,
                // Matches 2 of the fixed colors previously hardcoded in giftColors.
                colorDay1 = 0xFFC1443B.toInt(),
                colorNight1 = 0xFF7A2B26.toInt(),
                colorDay2 = 0xFF4F8FBF.toInt(),
                colorNight2 = 0xFF335E7D.toInt(),
            ),
            penguins = ObjectVariantConfig(
                visible = false,
                density = 0.5f,
                // Matches the fixed body color previously hardcoded in penguinBodyColor.
                colorDay1 = 0xFF2E3138.toInt(),
                colorNight1 = 0xFF1A1C20.toInt(),
                colorDay2 = 0xFF3A3E47.toInt(),
                colorNight2 = 0xFF23262B.toInt(),
            ),
            bunnies = ObjectVariantConfig(
                visible = false,
                density = 0.5f,
                // Matches the fixed body color previously hardcoded in bunnyBodyColor.
                colorDay1 = 0xFFF7EFE6.toInt(),
                colorNight1 = 0xFFAFA79C.toInt(),
                colorDay2 = 0xFFE8D5C4.toInt(),
                colorNight2 = 0xFF9C8B77.toInt(),
            ),
            easterEggs = ObjectVariantConfig(
                visible = false,
                density = 0.5f,
                // Matches 2 of the fixed colors previously hardcoded in easterEggColors.
                colorDay1 = 0xFFE8A6C4.toInt(),
                colorNight1 = 0xFF9C6A82.toInt(),
                colorDay2 = 0xFFA6D8E8.toInt(),
                colorNight2 = 0xFF6A93A0.toInt(),
            ),
            pumpkins = ObjectVariantConfig(
                visible = false,
                density = 0.5f,
                colorDay1 = 0xFFE8802E.toInt(),
                colorNight1 = 0xFF9C5A1F.toInt(),
                colorDay2 = 0xFFD16A1F.toInt(),
                colorNight2 = 0xFF8A4715.toInt(),
            ),
        )
    }
}

/**
 * The night-time pedestrian density a fresh install starts with.
 *
 * Equal to the daytime default, so v2.12 looks exactly like v2.11 until the user moves one of the
 * two sliders. Splitting a setting in two is not a licence to change what it does.
 */
const val DEFAULT_PEOPLE_NIGHT_DENSITY = 1f

/**
 * The arc-height range, in fractions of screen height.
 *
 * These are the bounds the renderer has always clamped to, now named once and shared with the
 * settings slider so the two cannot disagree. They are the reason the old slider looked wrong at
 * "60%": it displayed the stored value directly, so its 60% was `0.6` -- the *top* of the range,
 * not the middle of anything. The stored scale is unchanged, so no saved theme or preference
 * needs migrating; only what the slider prints on top of it changed.
 */
const val SUN_CLOUD_HEIGHT_MIN = 0.1f
const val SUN_CLOUD_HEIGHT_MAX = 0.6f

/** The stored arc height for a slider at [fraction] of the way along 0-100%. */
fun sunCloudHeightForFraction(fraction: Float): Float =
    SUN_CLOUD_HEIGHT_MIN + (SUN_CLOUD_HEIGHT_MAX - SUN_CLOUD_HEIGHT_MIN) * fraction.coerceIn(0f, 1f)

/** Where a stored arc height sits on the slider's 0-100%. */
fun sunCloudHeightFraction(stored: Float): Float =
    ((stored.coerceIn(SUN_CLOUD_HEIGHT_MIN, SUN_CLOUD_HEIGHT_MAX) - SUN_CLOUD_HEIGHT_MIN) /
        (SUN_CLOUD_HEIGHT_MAX - SUN_CLOUD_HEIGHT_MIN))

/**
 * Stable per-instance fraction in [0, 1), derived purely from an object's fixed position (never
 * from Random()) so the same object always gets the same value -- both across frames (no
 * flicker) and across [SceneObjectRenderer] rebuilds (no reshuffling when e.g. a density slider
 * moves, only slots crossing the new threshold change).
 */
private fun stableFraction(spec: StaticSceneObject, salt: Float): Float {
    val raw = spec.tileFractionX * 7919f + spec.depthFraction * 7919f * 131f + salt
    return raw - kotlin.math.floor(raw)
}

private fun stableFraction(spec: CarObject, salt: Float): Float {
    val raw = spec.laneYFraction * 7919f + spec.startDelaySeconds * 131f + salt
    return raw - kotlin.math.floor(raw)
}

/** Which category config governs a given object type, or null for types with no customization. */
private fun SceneCustomization.configFor(type: SceneObjectType): ObjectVariantConfig? = when (type) {
    SceneObjectType.HOUSE -> houses
    SceneObjectType.SKYSCRAPER -> buildings
    SceneObjectType.PARASOL -> parasols
    SceneObjectType.TREE, SceneObjectType.PALM_TREE -> trees
    SceneObjectType.SNOWMAN -> snowmen
    SceneObjectType.GIFT -> gifts
    SceneObjectType.PENGUIN -> penguins
    SceneObjectType.BUNNY -> bunnies
    SceneObjectType.EASTER_EGG -> easterEggs
    SceneObjectType.PUMPKIN -> pumpkins
    else -> null
}

/** Whether this candidate slot should actually render, given the current config. Types with no
 * customization category (e.g. CAR, which uses [keepCar] instead) are always kept.
 *
 * The two storefronts are exempt from density thinning (not from the category's visibility
 * toggle): since rc3 the catalogue emits exactly one restaurant and one bar per tile
 * (`SceneObjectCatalog.singleShopPerVariant`), so they are singular compositional anchors -- a
 * fractional density over two one-of-a-kind buildings is a coin flip that on Sunset's layout
 * removed the entire commercial street at the default setting. The buildings density slider
 * governs the towers, which are the category's crowd. */
fun SceneCustomization.keepCandidate(spec: StaticSceneObject): Boolean {
    val config = configFor(spec.type) ?: return true
    if (!config.visible) return false
    if (spec.type == SceneObjectType.SKYSCRAPER &&
        spec.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH
    ) {
        return true
    }
    return stableFraction(spec, salt = 0f) < config.density
}

fun SceneCustomization.keepCar(spec: CarObject): Boolean =
    cars.visible && stableFraction(spec, salt = 0f) < cars.density

/** Which of the 2 color variants (0 or 1) this instance uses. Salted differently from
 * [keepCandidate]'s threshold so density thinning and color-variant assignment don't correlate. */
private fun variantIndexFor(spec: StaticSceneObject): Int =
    if (stableFraction(spec, salt = 17.3f) < 0.5f) 0 else 1

private fun variantIndexFor(spec: CarObject): Int =
    if (stableFraction(spec, salt = 17.3f) < 0.5f) 0 else 1

private fun blend(config: ObjectVariantConfig, variant: Int, dayBlend: Float): Int {
    val day = if (variant == 0) config.colorDay1 else config.colorDay2
    val night = if (variant == 0) config.colorNight1 else config.colorNight2
    return androidx.core.graphics.ColorUtils.blendARGB(night, day, dayBlend.coerceIn(0f, 1f))
}

fun SceneCustomization.colorFor(spec: StaticSceneObject, dayBlend: Float): Int {
    val config = configFor(spec.type) ?: return 0xFFFFFFFF.toInt()
    return blend(config, variantIndexFor(spec), dayBlend)
}

fun SceneCustomization.colorFor(spec: CarObject, dayBlend: Float): Int = blend(cars, variantIndexFor(spec), dayBlend)

/** The parasol's 5 wedges alternate between the two configured colors (not a per-instance
 * variant pick like other categories, since a single parasol shows both colors as stripes). */
fun SceneCustomization.parasolStripeColor(wedgeIndex: Int, dayBlend: Float): Int =
    blend(parasols, wedgeIndex % 2, dayBlend)

/**
 * The starting-point [SceneCustomization] for a given built-in theme -- specifically, what a
 * user sees the *first* time they open "Scene Objects" or "Seasonal Decorations" for that theme,
 * before they've changed anything themselves. Structural categories (houses/buildings/etc.) are
 * the same [SceneCustomization.DEFAULT] everywhere, matching the "every theme offers the same
 * customization range" design principle -- but seasonal decorations *should* differ: Christmas
 * traditionally has snowmen and gifts, Easter traditionally has bunnies and eggs, and so on. This
 * doesn't lock anything in -- the user is still free to turn any of it off, turn on something
 * else instead, and either overwrite the built-in theme or save their own custom theme from
 * "Manage Themes", exactly like they can with structural categories today. Themes not listed here
 * (including custom/random ones) get the fully "everything off" [SceneCustomization.DEFAULT].
 */
fun defaultCustomizationFor(themeId: String): SceneCustomization {
    // Derived from the theme's own existing (currently fixed, non-user-editable) farthest-layer
    // hill color -- so switching a theme to "custom hills color" for the first time starts from
    // that theme's own authored look, not an unrelated placeholder color.
    val theme = ThemeCatalog.byId(themeId)
    val base = SceneCustomization.DEFAULT.copy(
        hillsColorDay = theme.hillColorsDay.firstOrNull() ?: SceneCustomization.DEFAULT.hillsColorDay,
        hillsColorNight = theme.hillColorsNight.firstOrNull() ?: SceneCustomization.DEFAULT.hillsColorNight,
        sky = SceneCustomization.DEFAULT.sky.copy(
            colorDayHigh = theme.skyDay.getOrElse(0) { SceneCustomization.DEFAULT.sky.colorDayHigh },
            colorDayLow = theme.skyDay.getOrElse(1) { theme.skyDay.getOrElse(0) { SceneCustomization.DEFAULT.sky.colorDayLow } },
            colorNightHigh = theme.skyNight.getOrElse(0) { SceneCustomization.DEFAULT.sky.colorNightHigh },
            colorNightLow = theme.skyNight.getOrElse(1) { theme.skyNight.getOrElse(0) { SceneCustomization.DEFAULT.sky.colorNightLow } },
            colorSunriseLow = theme.skyDawn.getOrElse(1) { theme.skyDawn.getOrElse(0) { SceneCustomization.DEFAULT.sky.colorSunriseLow } },
            colorSunsetLow = theme.skyDusk.getOrElse(1) { theme.skyDusk.getOrElse(0) { SceneCustomization.DEFAULT.sky.colorSunsetLow } },
        ),
        sun = SceneCustomization.DEFAULT.sun.copy(color = theme.sunColor),
        moon = SceneCustomization.DEFAULT.moon.copy(color = theme.moonColor),
        santaEnabled = theme.hasSantaSleigh,
    )
    return when (themeId) {
        "winter" -> base.copy(
            // **The winter presentation itself.** It was off, which left the winter themes with
            // green summer trees, bare roofs and people in shorts standing on snow. The three
            // things it drives -- tree snow caps, roof snow, winter clothing -- are exactly what
            // makes a winter scene a winter scene, and none of them had a switch of their own.
            winterColorsEnabled = true,
            // **Winter is not Christmas.** This theme is the plain season: snow, cold, no fairy
            // lights and no presents. It is the combination the two flags were split apart to
            // make expressible.
            christmasDecorationsEnabled = false,
            // A shade umbrella has no business standing in snow.
            parasols = base.parasols.copy(visible = false),
            snowmen = base.snowmen.copy(visible = true, density = 0.3f),
            mountainsFront = base.mountainsFront.copy(colorDay = 0xFFF7FAFC.toInt(), colorNight = 0xFFC9D6E8.toInt()),
            mountainsBack = base.mountainsBack.copy(colorDay = 0xFFE3ECF5.toInt(), colorNight = 0xFFA9BDD6.toInt()),
            // **It snows, rather than merely having snowed.** Precipitation is opt-in everywhere
            // else, on the same reasoning as the lake, and the two snow themes are the exception:
            // a theme called Winter whose weather is off is a theme whose central subject the
            // user has to go and find in a menu.
            precipitation = base.precipitation.copy(visible = true, type = PrecipitationType.SNOW, intensity = 0.45f),
        )
        "christmas" -> base.copy(
            winterColorsEnabled = true,
            christmasDecorationsEnabled = true,
            parasols = base.parasols.copy(visible = false),
            snowmen = base.snowmen.copy(visible = true, density = 0.5f),
            gifts = base.gifts.copy(visible = true, density = 0.4f),
            mountainsFront = base.mountainsFront.copy(colorDay = 0xFFF7FAFC.toInt(), colorNight = 0xFFC9D6E8.toInt()),
            mountainsBack = base.mountainsBack.copy(colorDay = 0xFFE3ECF5.toInt(), colorNight = 0xFFA9BDD6.toInt()),
            // The same exception as Winter's -- see that block.
            precipitation = base.precipitation.copy(visible = true, type = PrecipitationType.SNOW, intensity = 0.45f),
        )
        // **Winter, but not Christmas, and not a second Christmas theme either.** New Year sits
        // in the same season, so it gets the same snow-laden trees, roof snow and winter
        // clothing -- but the tree lights, the presents and Santa belong to the fortnight that
        // has just ended and stay in Christmas. What makes this theme itself is the night:
        // fireworks, a dusk-purple ground, and no shade umbrellas at a party after dark.
        "new_year" -> base.copy(
            winterColorsEnabled = true,
            christmasDecorationsEnabled = false,
            parasols = base.parasols.copy(visible = false),
            precipitation = base.precipitation.copy(type = PrecipitationType.SNOW),
        )
        "tundra" -> base.copy(
            winterColorsEnabled = true,
            christmasDecorationsEnabled = false,
            parasols = base.parasols.copy(visible = false),
            // Tundra is where trees stop. Not removed outright -- with the winter presentation on
            // they read as snow-laden conifers, and a treeless plain is emptier than it is
            // evocative -- but thinned to a scattering rather than the woodland every other theme
            // gets.
            trees = base.trees.copy(density = 0.2f),
            snowmen = base.snowmen.copy(visible = true, density = 0.3f),
            penguins = base.penguins.copy(visible = true, density = 0.4f),
            mountainsFront = base.mountainsFront.copy(colorDay = 0xFFF7FAFC.toInt(), colorNight = 0xFFC9D6E8.toInt()),
            mountainsBack = base.mountainsBack.copy(colorDay = 0xFFE3ECF5.toInt(), colorNight = 0xFFA9BDD6.toInt()),
            // The lake is meltwater at the edge of the ice. Sailboats and dolphins default to
            // visible and were inherited unchanged, which put a yachting scene and a pod of
            // dolphins in the Arctic.
            lake = base.lake.copy(
                visible = true, colorDay = 0xFFBFE3EE.toInt(), colorNight = 0xFF2A4550.toInt(), height = 0.25f,
                sailboatsVisible = false, dolphinsVisible = false,
            ),
            precipitation = base.precipitation.copy(type = PrecipitationType.SNOW),
        )
        // **The one theme that presets the two Halloween flags.** Choosing it has to show the
        // whole presentation at once -- carved moon, bare trees, black-and-orange sky -- because a
        // theme called Halloween that needs two switches found in a menu before it looks like
        // Halloween is a theme that does not work.
        //
        // Presetting is not coupling. Both flags stay exactly as independent as they were: the
        // user can turn either off, or on, in any combination, and this block only seeds their
        // starting value the same way every other theme seeds `winterColorsEnabled` or
        // `parasols.visible`. Neither flag reads the other, here or anywhere else.
        //
        // The pumpkins come with it for the same reason Autumn's do: they are the season's own
        // decoration, and leaving them to be discovered in a menu would ship a Halloween scene
        // without the one object that says Halloween. Winter, Christmas and the fall palette are
        // untouched -- bare branches are not autumn leaves, and this is not December.
        "halloween" -> base.copy(
            halloweenEnabled = true,
            horrorSkyEnabled = true,
            pumpkins = base.pumpkins.copy(visible = true, density = 0.5f),
            parasols = base.parasols.copy(visible = false),
        )
        // Spring's own defaults, and they are mostly about what is *off*. No winter palette, no
        // fall palette, no Christmas layer, no Halloween: the season is defined here by the
        // absence of every other season's dressing plus a full, dense canopy, which is the one
        // thing spring has that winter and autumn do not. Parasols stay away -- it is not warm
        // yet -- and the lake comes up because meltwater is what early spring looks like.
        "spring" -> base.copy(
            // The one theme that starts with them on. Spring without flowers is a green summer.
            flowersEnabled = true,
            trees = base.trees.copy(visible = true, density = 0.7f),
            parasols = base.parasols.copy(visible = false),
            lake = base.lake.copy(visible = true),
        )
        "easter" -> base.copy(
            bunnies = base.bunnies.copy(visible = true, density = 0.3f),
            easterEggs = base.easterEggs.copy(visible = true, density = 0.5f),
        )
        // Autumn had the palette of autumn and the vegetation of midsummer: `fallColorsEnabled`
        // is what turns the leaves and starts them falling, and it was off. Pumpkins come with
        // it -- they are the season's own decoration, and leaving them to be discovered in a
        // menu meant the Autumn theme shipped without the one object that says autumn.
        "autumn" -> base.copy(
            fallColorsEnabled = true,
            parasols = base.parasols.copy(visible = false),
            pumpkins = base.pumpkins.copy(visible = true, density = 0.35f),
        )
        // A quick, honest first pass -- not the final per-theme design polish (tracked in
        // ROADMAP_OLD.md's Phase 5 "review every built-in theme's defaults" item), just enough that a
        // fresh install's themes actually look different from each other instead of all sharing
        // the exact same lake/mountain defaults regardless of which theme is picked.
        "beach" -> base.copy(
            // **The ground was the sea's own teal.** `SceneTheme.hillColorsDay` is a three-entry
            // array from the days of three hill layers; the scene has drawn one layer for some
            // time, so only entry 0 is ever read and the two sand tones behind it were dead
            // values. Beach's entry 0 is the water colour, so the shore rendered as a green-teal
            // field. Stated here as the sand it is meant to be, which is the same value the
            // array's second entry already held.
            hillsColorDay = 0xFFEFD9A3.toInt(),
            hillsColorNight = 0xFF6E6353.toInt(),
            lake = base.lake.copy(
                visible = true, height = 0.9f,
                colorDay = 0xFF1E9BC4.toInt(), colorNight = 0xFF15495C.toInt(),
                sailboatsVisible = true, sailboatsDensity = 0.4f,
                dolphinsVisible = true, dolphinsDensity = 0.3f,
            ),
            mountainsFront = base.mountainsFront.copy(visible = false),
            mountainsBack = base.mountainsBack.copy(visible = false),
        )
        "desert" -> base.copy(
            lake = base.lake.copy(visible = false),
            mountainsFront = base.mountainsFront.copy(colorDay = 0xFFC98B4A.toInt(), colorNight = 0xFF6E4A2E.toInt()),
            mountainsBack = base.mountainsBack.copy(colorDay = 0xFFD9A868.toInt(), colorNight = 0xFF8A6440.toInt()),
        )
        "city" -> base.copy(
            mountainsFront = base.mountainsFront.copy(visible = false),
            mountainsBack = base.mountainsBack.copy(visible = false),
            birds = base.birds.copy(density = 0.2f),
            // A city theme that draws as many cottages as offices is a village with a skyline
            // behind it. Both categories shared the generic 0.65; here they are what the theme
            // is named after.
            buildings = base.buildings.copy(density = 1f),
            houses = base.houses.copy(density = 0.3f),
        )
        else -> base
    }
}

// --- Structural vs cosmetic change detection -------------------------------------------------

/**
 * Whether two configs would produce the *same set of rendered candidate slots* for static scene
 * objects.
 *
 * Only [ObjectVariantConfig.visible] and [ObjectVariantConfig.density] are read by
 * [keepCandidate], so those are the only fields that can change which objects exist. Everything
 * else in [SceneCustomization] -- every colour, the sky/stars/clouds/precipitation/rainbow/
 * mountain/lake/bird sections, hill variation, the seasonal palette flags -- is consumed at draw
 * time and changes only how the existing objects look.
 *
 * That distinction is what lets [SceneObjectRenderer] keep its runtime state across a colour
 * change instead of rebuilding the whole scene. Comparing whole [SceneCustomization] instances
 * would treat a colour tweak as structural and throw away running animation state for nothing.
 *
 * Deliberately field-by-field rather than a hash: a hash collision here would silently fail to
 * rebuild the scene, which is a visible bug, and the comparison must allocate nothing because it
 * sits on a per-frame path.
 *
 * **Adding a new [ObjectVariantConfig] category means adding it here.**
 * `SceneCustomizationStructureTest` fails if the count of such fields changes, so this cannot be
 * forgotten silently.
 */
fun SceneCustomization.staticStructurallyEquals(other: SceneCustomization): Boolean =
    houses.structurallyEquals(other.houses) &&
        buildings.structurallyEquals(other.buildings) &&
        parasols.structurallyEquals(other.parasols) &&
        people.structurallyEquals(other.people) &&
        trees.structurallyEquals(other.trees) &&
        snowmen.structurallyEquals(other.snowmen) &&
        gifts.structurallyEquals(other.gifts) &&
        penguins.structurallyEquals(other.penguins) &&
        bunnies.structurallyEquals(other.bunnies) &&
        easterEggs.structurallyEquals(other.easterEggs) &&
        pumpkins.structurallyEquals(other.pumpkins)

/**
 * Whether two configs would produce the same set of rendered cars. Separate from
 * [staticStructurallyEquals] so that changing, say, house density rebuilds the static objects
 * without resetting every car's in-flight `progress` along the road.
 */
fun SceneCustomization.carsStructurallyEquals(other: SceneCustomization): Boolean =
    cars.structurallyEquals(other.cars)

/** The subset of a category config that [keepCandidate]/[keepCar] actually read. */
private fun ObjectVariantConfig.structurallyEquals(other: ObjectVariantConfig): Boolean =
    visible == other.visible && density == other.density
