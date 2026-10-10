package com.paperscrape.livewallpaper.engine

/**
 * Reusable per-category settings: visibility, density, and 2 color variants (each with a
 * day/night version) -- the same customization "shape" applied uniformly to every object
 * category below rather than duplicated per type.
 *
 * Colors: each individual instance of the category is deterministically assigned variant 1 or 2
 * (stable, based on its position -- see `variantIndexFor` in this file), and blends
 * between that variant's day and night color exactly like the rest of the scene does.
 */
data class ObjectVariantConfig(
    val visible: Boolean,
    /**
     * 0f..1f — how populated the category is.
     *
     * For static categories, the fraction of a theme's candidate slots that actually render
     * (each slot keeps or drops itself by a stable per-slot hash — [keepCandidate]). For cars
     * the same 0..1 maps onto an explicit **count** instead, 1 car at 0f and every slot at 1f,
     * because ten candidates are too few for independent thresholds to approximate a density —
     * see [CarSelection].
     */
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
) {
    /**
     * Whether the layer draws a mountain: switched on and above 0 %. At 0 % `CandidateThreshold` keeps
     * no candidate of the pool, so the layer draws none (v5.11, inventory I-414: its switch read on
     * there, and the gallery card drew it).
     */
    val drawsAny: Boolean get() = visible && density > 0f
}

/**
 * The distant houses: little houses standing on the mountains (v5.11, the maintainer's choice of
 * 2026-10-06 from the photographs: on the mountains, off as it ships, the switch on World & scene
 * under *Mountains*, every theme, 50 % when turned on, three drawings mixed).
 *
 * A switch and an amount, nothing else: their colours are the houses' own ([SceneCustomization.houses],
 * one coin per house), their snow is the winter palette's, their windows light at night as a
 * house's do -- see [DistantHouses]. They stand on the mountains, so with both mountain layers off
 * there is nothing to stand on and the switch says so (`SettingsUiModel.distantHouses`).
 *
 * **Off when absent.** Nothing written before v5.11 carries it, and a user who updates must not
 * find houses on the mountains they did not ask for: [OFF] is what every older payload, preference
 * store and backup reads as.
 */
data class DistantHousesConfig(
    val visible: Boolean,
    /** 0f..1f -- the share of the places on the mountains that hold a house. */
    val density: Float,
) {
    /** Whether any house is drawn: switched on and above 0 %, as every other amount at 0 is. */
    val drawsAny: Boolean get() = visible && density > 0f

    companion object {
        /** Where the amount starts when the switch is first turned on, and what a tap at 0 % restores. */
        const val STARTING_DENSITY = 0.5f

        /** What a theme ships with, and what data written before v5.11 reads as. */
        val OFF = DistantHousesConfig(visible = false, density = STARTING_DENSITY)
    }
}

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
) {
    /**
     * Whether there is any water: switched on, **and above 0 % height** (v5.10E, inventory I-293, the
     * maintainer's *«2 - si»* of 2026-10-04). At 0 % the band was zero pixels tall and *Show Lake* still
     * read on, and the sailboats and dolphins were still laid out on lanes of a band with no height; now
     * 0 % is off, as every other amount at 0 is (v5.10C, row 8): the wallpaper draws no lake and nothing
     * on it (`PaperRenderer.updateLakeBandY`), the gallery card none (`ThemePreviewScene`), and the
     * switch says so, with a tap that puts the theme's own height back. The mountains stand where they
     * did: on a band of no height their base was the water's top edge, which is the hills' own line --
     * the one they take with the lake off -- to within a float's rounding.
     */
    val drawsWater: Boolean get() = visible && height > 0f
}

/** One of a bird's 4 selectable colors, with a relative weight controlling how often it's picked
 * (not a uniform 1-in-4 -- the colour sliders under "Bird Colors" in the UI, which since v5.10E print each
 * colour's share of the flock rather than the weight). Weights don't need to sum to
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

/** Falling rain or snow, drawn in front of the whole scene except the falling leaves and the
 * lightning flash (see [PaperRenderer.draw]'s call order) -- real precipitation reads as being
 * right in front of the "camera", in front of even houses and cars, not part of the backdrop the
 * way clouds/mountains are. [type] picks which of the two actually renders; both keep their own
 * independent color pair ([rainColorDay]/`Night` vs [snowColorDay]/`Night`) so switching types
 * doesn't force a user to re-pick colors that made sense for the other one. [thunderstorm] adds
 * occasional lightning flashes (see [PaperRenderer.drawLightningFlash]) -- kept as its own flag
 * rather than a third [type] value so toggling storms on/off doesn't lose the user's rain
 * settings, though it's only meaningful while [type] is [PrecipitationType.RAIN].
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
        // By index, twice: `sumOf` and a `for (c in colors)` each built an iterator, two objects per
        // bird per frame on the draw path (v5.10A). The sum runs in the same order `sumOf` did, in
        // `Double`, so it is the same number.
        var weights = 0.0
        for (i in colors.indices) weights += colors[i].weight.toDouble()
        val totalWeight = weights.toFloat()
        if (totalWeight <= 0f) return colors.firstOrNull()?.color ?: 0xFFFFFFFF.toInt()
        var target = randomFraction.coerceIn(0f, 1f) * totalWeight
        for (i in colors.indices) {
            val c = colors[i]
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
 * Per-theme rendering settings -- every customizable object category plus the sky, hills, water,
 * weather and seasonal switches -- edited from the "World & scene" and "Seasons & decorations"
 * screens. These apply on top of whichever theme/custom theme is active:
 * a theme's [SceneObjectLayout] defines *candidate* slots (see [SceneObjectCatalog]), and this
 * config decides how many of them actually show up and what colors they use.
 */
data class SceneCustomization(
    val houses: ObjectVariantConfig,
    /**
     * The Buildings category: whether the towers and the three shops stand, how many towers, the
     * towers' opening hours ([towerHoursEnabled]; the shops' are the Shops section's since v5.12,
     * [shopHoursEnabled]) -- and, since v5.11, **the towers' colours only**. The shops wear [shops].
     */
    val buildings: ObjectVariantConfig,
    /**
     * The colours of the three shops -- the restaurant, the school and the bar (v5.11, the
     * maintainer's *«voglio che i negozi abbiano colore a se, aggiungiamolo»* of 2026-10-06).
     *
     * **Colours only.** Whether the shops stand is [buildings]' switch, as it always was: they are
     * one candidate pool with the towers, and a second switch for the same objects would be two
     * controls that can disagree. So [ObjectVariantConfig.visible] and [ObjectVariantConfig.density]
     * here are read by nothing -- not by [keepCandidate], not by [staticStructurallyEquals] -- and
     * the settings show no switch or slider for them.
     *
     * **Data written before v5.11 has no such field**, and the shops were drawn in the Buildings
     * colours stored beside it: a saved theme, a theme's archive, a backup or a theme file is read
     * with those (`sceneCustomizationFromJson`), and so is the live edit where it stored a Buildings
     * colour (`WallpaperPrefs.readFlatCustomization`) -- a theme keeps the look it had.
     */
    val shops: ObjectVariantConfig,
    val cars: ObjectVariantConfig,
    val parasols: ObjectVariantConfig,
    /**
     * The pedestrians, as a category with visibility and density like any other.
     *
     * Only two of the eight fields mean anything here: the walk sprites are finished art in four
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
     * [ObjectVariantConfig]: a night density only means something for a category whose population
     * *moves* -- things that come and go, so that "fewer of them after dark" describes behaviour
     * rather than redecorating. Pedestrians move; so does traffic, which is why cars carry the
     * same pair since v4.22 ([carsNightDensity]) -- this comment used to say pedestrians were the
     * only such category, and the traffic feature is exactly the counterexample. Houses and
     * mountains stand still: giving them a night density would make buildings dissolve at dusk,
     * a preference that cannot mean anything about a landscape. It governs the same pedestrians
     * `people.density` always has -- drivers, passengers and the figures in lit windows are drawn
     * elsewhere and are untouched.
     */
    val peopleNightDensity: Float = DEFAULT_PEOPLE_NIGHT_DENSITY,
    /**
     * How much traffic drives **after dark**, 0f..1f.
     *
     * The cars' own half of the pair [peopleNightDensity] describes: [cars]`.density` is the
     * daytime figure, this is the night one, and the count in force is the blend of the two on
     * the scene's own `dayBlend` (`CarSelection.densityAt` -> `countFor`) -- the road fills and
     * empties over the length of dusk, car by car and only off screen, never between two frames.
     * The blended density feeds the same explicit count the day slider does; there is no second
     * threshold and no parallel selection.
     *
     * Defaults equal to the daytime default, and resolves to the daytime *setting* for a user
     * upgrading from a build that had one slider ([PeopleDensity.resolveNightDensity]), so the
     * scene after the update is the scene before it until somebody moves the new control.
     */
    val carsNightDensity: Float = DEFAULT_CARS_NIGHT_DENSITY,
    /**
     * Whether the shops -- the restaurant, the school and the bar -- keep opening hours at all.
     *
     * **Off by default, and off means bitwise-identical to before the feature existed**: every
     * shop is as open as it always was -- its glass lit at night, its people at their windows -- and
     * the two hours below are inert (the settings screen only lets them be edited while this is on). See [BusinessHours] for what
     * "open" and "closed" do, and why the houses never keep hours.
     *
     * **One of two groups since v5.12** (the maintainer's *«vorrei inoltre aggiungere uno slide per
     * orari solo grattacieli e solo negozi»*, 2026-10-09): the shops' hours here, the towers' in
     * [towerHoursEnabled]. Which group a building follows is what it is drawn as -- the rule its
     * colours follow ([opennessFor], [buildingColoursFor]). Data stored before v5.12 had one setting
     * for both, and is read into both (`WallpaperPrefs`, `sceneCustomizationFromJson`).
     */
    val shopHoursEnabled: Boolean = false,
    /**
     * When the shops open, decimal hours 0..24.
     *
     * `open == close` means always open — "always closed" is the buildings' visibility switch,
     * not an hour. The pair may wrap midnight (09:00–02:00 is a valid business day). The default
     * pair is only a seed for the editors: it means nothing until [shopHoursEnabled] is on,
     * and an ordinary shop day is the least surprising place for the sliders to start.
     */
    val shopOpenHour: Float = DEFAULT_BUSINESS_OPEN_HOUR,
    /** When the shops close — see [shopOpenHour] for the boundary rules. */
    val shopCloseHour: Float = DEFAULT_BUSINESS_CLOSE_HOUR,
    /** Whether the towers keep opening hours: [shopHoursEnabled]'s twin for the Buildings category. */
    val towerHoursEnabled: Boolean = false,
    /** When the towers open, by [shopOpenHour]'s rules. */
    val towerOpenHour: Float = DEFAULT_BUSINESS_OPEN_HOUR,
    /** When the towers close, by [shopOpenHour]'s rules. */
    val towerCloseHour: Float = DEFAULT_BUSINESS_CLOSE_HOUR,
    val trees: ObjectVariantConfig,
    // Fall Colors / Winter Colors: NOT their own placeable object category (no
    // visibility/density/color-variant shape like the seasonal decorations below) -- they're a
    // seasonal *palette override* applied on top of the existing `trees` category's own leaf
    // rendering (see SceneObjectRenderer.drawTree). Deliberately toggled from the "Seasons &
    // decorations" screen (its None/Autumn/Winter palette), not the Trees screen of "World &
    // scene": aa's own framing is that the
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
    /**
     * Palms on a layout that plants palms of its own (Beach, Desert, their saved copies, and
     * shuffled themes that deal palms): on or off, and nothing else. **The other half of the one
     * Palms switch is [palmsInsteadOfTrees]**, for every layout that plants none; which half a
     * theme reads is decided by its layout, in [palmsShown].
     *
     * **What it actually switches is which tree the Beach and Desert layouts draw.** Those two
     * themes map their tree slots to [SceneObjectType.PALM_TREE] (`SceneObjectCatalog`), and
     * until v5.1 the only way to be rid of the palms was to turn the whole TREES category off,
     * which left the shore bare. With this off those slots draw the ordinary broadleaf tree
     * instead -- same positions, same depths, same density -- so the scene keeps its vegetation.
     * The swap is made once, where the renderer builds its object list from the layout
     * ([palmSpeciesApplied]), so a slot that has become a tree is a tree to every later
     * question: its size, its occlusion box, its falling leaves, its preview.
     *
     * An oak on sand is a thing the maintainer chose with the objection in front of them. The
     * alternative on the table was a bare beach, and a beach with the wrong tree on it is a
     * scene; a beach with nothing on it is a gap.
     *
     * **A plain boolean rather than an `ObjectVariantConfig`, for the same reason
     * [flowersEnabled] is one.** Density and visibility already belong to TREES and are not
     * being duplicated here -- this is not a second population, it is which species the one
     * population is drawn as -- and a palm's colours are fixed art with no tint for a colour
     * pair to occupy.
     *
     * **On by default, unlike every other switch in this group, and the reason is upgrades.** A
     * theme saved before v5.1 carries no `palmsEnabled` field, and
     * [sceneCustomizationFromJson] fills an absent field from [SceneCustomization.DEFAULT]
     * rather than from the theme's own defaults -- so a `false` here would silently fell the
     * palms of every Beach and Desert theme a user had already saved. On every other built-in
     * it is inert -- no other built-in layout places a palm -- while a saved copy of Beach or
     * Desert, or a shuffled theme that deals palms, is governed by it exactly as they are (see
     * [hasPalmSlots]).
     *
     * **Which is also why it cannot be the switch on the other themes** (v5.10C). Being on by
     * default, it is `true` in every theme anyone has saved, edited or backed up on a theme
     * without palms -- written there by the app, not chosen -- so reading it as "palms here" would
     * have planted palms on every such Autumn and Christmas the day the switch started to work
     * there. Those themes read [palmsInsteadOfTrees] instead, which no build before v5.10 wrote.
     */
    val palmsEnabled: Boolean = true,
    /**
     * Palms in place of the trees, on a layout that plants no palm of its own: every ordinary tree
     * slot of Autumn, Christmas, Winter and the rest drawn as a palm -- same places, same depths,
     * same density, the swap [palmsEnabled] makes on Beach and Desert run the other way. The
     * maintainer's decision of 2026-09-30, in his words *«le palme devono essere attivabili in
     * qualsiasi tema: l'utente deve essere libero di avere anche natale con palme»*, with the
     * switch starting off wherever there were no palms before. **A Christmas fir stays a fir**
     * (v5.10C2, his words of 2026-10-03: *«gli abeti sono del tema e tali devono rimanere»*): the
     * palms take the ordinary trees' places only -- see [palmSpeciesApplied].
     *
     * **A field of its own, off by default, and that is the whole of the upgrade.** The switch
     * the user moves is one ([palmsShown]); it is stored in two fields because the old one,
     * [palmsEnabled], is `true` on every theme the app has ever written -- see there. A payload,
     * a preference store or a backup written before v5.10 carries no value here, so it reads as
     * off: nobody who updates finds palms they did not ask for, and Beach, Desert and every
     * layout that deals palms read [palmsEnabled] exactly as before. `WallpaperPrefs.setPalmsEnabled`
     * writes both, so from the first move of the switch the two say the same thing.
     */
    val palmsInsteadOfTrees: Boolean = false,
    /**
     * The Halloween presentation: a jack-o'-lantern moon, bare trees and palms, carved pumpkins.
     *
     * **A third independent statement, alongside [winterColorsEnabled] and
     * [christmasDecorationsEnabled], and it implies neither.** The lesson v2.0 recorded about
     * winter and Christmas applies here in advance: a season and a decoration layer are different
     * things, and folding one into the other is what made "winter" and "Christmas" synonyms for a
     * whole release. Halloween is not a temperature and not a fortnight of fairy lights, so it
     * gets its own flag rather than a shared one, and turning it on changes nothing about winter,
     * Christmas, New Year or the fall palette.
     *
     * **Scope, deliberately narrow.** Four things follow from it: the moon becomes
     * `moon_jack_o_lantern`, every broadleaf tree drops its canopy for `tree_dead_branches`, every
     * palm wears `palmtree_fronds_dead`, and every pumpkin gets its carved `pumpkin_face`. Whether
     * any pumpkin stands is still the pumpkins' own switch, for the same reason Santa keeps his --
     * one thing with two controls that can disagree is worse than two things with one each.
     *
     * The sky is **not** part of this. See [horrorSkyEnabled].
     */
    val halloweenEnabled: Boolean = false,
    /**
     * The horror sky: near-black overhead, a hard orange band at the horizon.
     *
     * **Separate from [halloweenEnabled] on purpose, and all four combinations are reachable.** A
     * scene can be a bare-tree, lantern-moon Halloween under an ordinary night sky, and an ordinary
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
    // Seasonal decorations -- opt-in extras, off by default (see "Seasons & decorations"),
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
     * **Separate from the falling leaves.** `drawFallingLeaves` animates leaves coming off the
     * crowns whenever [fallColorsEnabled] is on, with no switch of its own; this lies heaps on the
     * ground, and 0 here leaves the falling leaves as they are.
     */
    val leafPiles: Float = 0f,
    // Same idea, two more theme-scoped plain fields for the hills' base color (day and night) --
    // the hills are a single layer now, drawn in this one color (PaperRenderer's hillLayerColor()
    // is a pass-through), matching how every other customizable category in this app exposes
    // exactly one color pair, not one per depth layer.
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
    /** The little houses on the mountains, off as every theme ships: see [DistantHousesConfig]. */
    val distantHouses: DistantHousesConfig = DistantHousesConfig.OFF,
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
                // Houses, buildings, parasols and trees all defaulted to density=1f (every one
                // of CANDIDATES_PER_CATEGORY's 10 slots shown), stacking across every depth band
                // at once, and the placement band read as too crowded. Lowered to a more open
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
                // **Not a built-in theme's towers any more** (v5.11): each of the twelve starts from a
                // pair of its own ([builtInTowerColours]). This stays the slate it always was, as
                // the value a payload without a Buildings block reads, a theme nobody built in
                // (a shuffled one) draws, and the colour every theme's towers had until v5.11.
                colorDay1 = 0xFF454B57.toInt(),
                colorNight1 = 0xFF262A31.toInt(),
                colorDay2 = 0xFF5C6A78.toInt(),
                colorNight2 = 0xFF303842.toInt(),
            ),
            // The shops' own colours since v5.11. Here the Buildings slate, because that is what the
            // shops of a theme nobody built in were drawn in, and what a payload with neither block
            // reads; the twelve built-ins start from their own ([builtInTowerColours]). Visibility
            // and density are inert -- see [SceneCustomization.shops].
            shops = ObjectVariantConfig(
                visible = true,
                density = 1f,
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
            carsNightDensity = DEFAULT_CARS_NIGHT_DENSITY,
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
 * The night-time car density a fresh install starts with. Equal to the cars' daytime default for
 * the same reason [DEFAULT_PEOPLE_NIGHT_DENSITY] equals the pedestrians': until the user moves
 * the new slider, v4.22 nights look exactly like v4.21 nights.
 */
const val DEFAULT_CARS_NIGHT_DENSITY = 1f

/**
 * Where the opening-hours editors start, the shops' and the towers', inert until their group's switch is on.
 *
 * Not derived, and not derivable: a default shop day is a seed for each group's two sliders nobody has moved,
 * behind switches that default to off. 09:00–20:00 is stated as "an ordinary shop day" and
 * carries no other meaning; with a group's switch off the rendered scene is identical whatever these
 * hold, which `BusinessHours.opennessAt` guarantees by returning 1 before reading them.
 */
const val DEFAULT_BUSINESS_OPEN_HOUR = 9f
const val DEFAULT_BUSINESS_CLOSE_HOUR = 20f

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
 * A per-instance value derived purely from an object's fixed position (never from Random()), so
 * the same object always gets the same value -- across frames and across [SceneObjectRenderer]
 * rebuilds.
 *
 * **It is a grid, not a fraction in [0, 1)**, and that is item 134. The sum runs in `Float` and the
 * depth term reaches ~985 000 for a front-band object, where a `Float` holds 1/16 and nothing
 * finer: in front of the scene it takes sixteen values, behind it a few dozen, finer the farther
 * back. Read directly as a density threshold it kept 11 of 16 steps at 0.65 -- 81 houses of 120
 * where the slider asked for about 74. [densityFraction] is what the threshold reads now; this
 * stays, unchanged to the bit, as the part of it that says which step an object is on, and as the
 * whole of it for a saved theme written before v5.8C ([DENSITY_SCHEME_GRID]).
 */
private fun stableFraction(spec: StaticSceneObject, salt: Float): Float {
    val raw = spec.tileFractionX * 7919f + spec.depthFraction * 7919f * 131f + salt
    return raw - kotlin.math.floor(raw)
}

/**
 * The value [keepCandidate] compares against the density: a real fraction in `[0, 1)` (v5.8C,
 * item 134, decided by the maintainer on 2026-09-25).
 *
 * [stableFraction] says which step of its grid an object is on; the step's own width is the
 * `Float`'s spacing at that magnitude (`Math.ulp`), and a second draw -- an integer hash of both
 * coordinates at full precision, on a channel nothing else reads -- says where inside the step it
 * lies. So the value is uniform over `[0, 1)` at every depth, and a density of 0.65 keeps 65 % of
 * the candidates in front and behind alike, which is the re-derived mapping: the slider's number
 * and the fraction of objects standing are the same number.
 *
 * **Why refine rather than replace.** A fresh hash would re-deal every scene: different houses in
 * different places on every theme. Refining keeps the order the grid already gave -- an object on
 * a lower step is always below one on a higher step -- so at any density the objects standing now
 * are a **subset** of those that stood before: none appears, and the only ones that go are some of
 * those on the step the threshold cuts through, which the grid used to keep whole. At a density
 * that falls exactly on a step boundary nothing changes at all. Computed in `Double` so the value
 * cannot round up onto the next step's lower edge.
 */
internal fun densityFraction(spec: StaticSceneObject): Double {
    val raw = spec.tileFractionX * 7919f + spec.depthFraction * 7919f * 131f + 0f
    val step = Math.ulp(raw).toDouble()
    val within = CandidateNoise.value(spec.tileFractionX.toRawBits(), spec.depthFraction.toRawBits(), DENSITY_REFINE_CHANNEL)
    return stableFraction(spec, salt = 0f).toDouble() + within.toDouble() * step
}

/** Nothing else reads it: see [densityFraction]. */
private const val DENSITY_REFINE_CHANNEL = 134

/**
 * How a layout's objects are thinned by density: [DENSITY_SCHEME_FRACTION] for every layout the
 * app generates and every theme saved since v5.8C, [DENSITY_SCHEME_GRID] for a saved theme written
 * before it.
 *
 * **A saved theme keeps what it looked like.** It stores the objects that were *standing* when it
 * was saved -- thinned by the grid -- and the renderer thins them again by the same rule on every
 * frame, which the grid passed idempotently. Thinned by the fraction instead, some of them would
 * drop out, so a theme the user saved last month would lose houses today. So a saved entry carries
 * the scheme it was thinned with, and one that carries none is a grid one.
 */
const val DENSITY_SCHEME_GRID = 1
const val DENSITY_SCHEME_FRACTION = 2

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

/**
 * Whether the Palms switch is on for a theme whose layout does ([layoutPlantsPalms]) or does not
 * plant palms of its own -- [SceneObjectLayout.hasPalmSlots] of the layout the theme draws.
 *
 * The one reading of the switch, for the settings screen, the gallery card and the wallpaper alike
 * (v5.10C): [palmsEnabled] where the layout plants palms, as it always was; [palmsInsteadOfTrees]
 * where it plants none, so a `true` the app wrote there before v5.10, when the switch could do
 * nothing on such a theme, does not count. See [SceneCustomization.palmsInsteadOfTrees].
 */
fun SceneCustomization.palmsShown(layoutPlantsPalms: Boolean): Boolean =
    if (layoutPlantsPalms) palmsEnabled else palmsInsteadOfTrees

/**
 * The species this slot is drawn as, given the current config and whether the layout it comes
 * from plants palms of its own ([layoutPlantsPalms], [SceneObjectLayout.hasPalmSlots]):
 *
 *  - on a layout that plants palms, a palm slot is a palm while [palmsEnabled] is on and an
 *    ordinary tree while it is off; its tree slots, if it has any (a shuffled theme may deal both),
 *    stay trees either way -- exactly the rule of v5.1 to v5.9;
 *  - on a layout that plants none, a tree slot is a palm while [palmsInsteadOfTrees] is on
 *    (v5.10C), and a tree otherwise -- **except a fir**: a slot that stands as a Christmas fir
 *    while the Christmas layer is on (`SceneObjectRenderer.standsAsFir`) stays a tree, and the
 *    drawing makes it the fir it was (v5.10C2). The palms take the place of the ordinary trees
 *    only; the maintainer, 2026-10-03: *«le palme in natale devono sovrascrivere gli alberi
 *    normali, gli abeti sono del tema e tali devono rimanere»*. The Christmas layer puts firs on
 *    any theme, so this holds wherever a fir stands, not on Christmas alone.
 *
 * Every other slot passes through untouched. On a layout that plants palms nothing about the firs
 * changes: its palm slots are palms whatever the Christmas layer says, as they always were, and a
 * slot the switch turns back into a tree is a tree the drawing may make a fir, as it always did.
 *
 * **Applied once, on the way from the layout to the renderer's object list, rather than at the
 * blit.** Everything downstream of that list reads `spec.type` -- `SceneObjectRenderer.variantFor`
 * for the drawing, `SceneVariant.baseScale` and `spriteUnitsTall` for the size,
 * `SceneObjectCatalog.occluderBoxes` for what it hides, `recordLeafSource` for what falls off it
 * -- so making the swap here is what stops an oak being drawn at a palm's height inside a palm's
 * occlusion box. Deciding it per draw call would have meant repeating it in five places that each
 * ask the type a different question, which is the shape of the per-asset constants
 * `CLAUDE.md` forbids.
 *
 * The one thing it does **not** move is the shop-visibility pass, which runs at layout generation
 * where no customization exists (`SceneObjectCatalog.layoutFor`). A tree is wider than a palm, so
 * turning palms off can leave a shop front more covered than the pass allowed for. That is the
 * behaviour every density and visibility setting already has -- the layout is dealt once and the
 * user's switches are read after it -- and it is not made worse here by being said out loud. The
 * other way round, palms in the tree slots of a street laid out for trees, is measured by
 * `ShopFrontVisibilityTest` on all twelve built-ins: a palm's crown reaches less far than an oak's.
 */
fun SceneCustomization.palmSpeciesApplied(spec: StaticSceneObject, layoutPlantsPalms: Boolean): StaticSceneObject =
    when {
        layoutPlantsPalms ->
            if (palmsEnabled || spec.type != SceneObjectType.PALM_TREE) spec
            else spec.copy(type = SceneObjectType.TREE)
        palmsInsteadOfTrees && spec.type == SceneObjectType.TREE && !SceneObjectRenderer.standsAsFir(spec, this) ->
            spec.copy(type = SceneObjectType.PALM_TREE)
        else -> spec
    }

/**
 * Whether this layout plants any palm of its own -- which decides which of the two fields behind
 * the Palms switch the theme drawn from it reads: [SceneCustomization.palmsEnabled] if it does,
 * [SceneCustomization.palmsInsteadOfTrees] if it does not ([palmsShown]).
 *
 * Asked of the layout, not of the theme's id. Beach and Desert are the two built-ins whose tree
 * slots are palms, but a theme saved from either keeps those slots under a `custom:` id, and a
 * shuffled theme may deal palms too; a list of names would say "no palms" about both while the
 * wallpaper draws them. The layout is the one before the switch is applied -- what the theme plants,
 * not what the user turned it into.
 */
fun SceneObjectLayout.hasPalmSlots(): Boolean =
    staticObjects.any { it.type == SceneObjectType.PALM_TREE }

/**
 * Whether, with the Palms switch on, every tree this layout keeps under [c] would still stand as a
 * Christmas fir -- so the switch would put no palm anywhere (v5.10C2). The firs stay firs among the
 * palms ([palmSpeciesApplied]), and a wood thinned far enough under the Christmas layer can keep only
 * fir slots: Christmas at 20 % keeps two trees, and both are firs. The settings ask this so the switch
 * does not read on over a scene with no palm in it (`AI_PROJECT_RULES.md` 8.7).
 *
 * False when the layout keeps no tree at all: that is the trees' own reason (*Show Trees* off or at 0 %),
 * said by the switch in its own words. Read through [keepCandidate] and [palmSpeciesApplied] with the
 * switch on, the two functions the renderer builds its list with, so it cannot drift from the scene.
 */
fun SceneObjectLayout.keepsOnlyFirsUnderPalms(c: SceneCustomization): Boolean {
    val on = c.copy(palmsEnabled = true, palmsInsteadOfTrees = true)
    val plantsPalms = hasPalmSlots()
    var trees = 0
    for (spec in staticObjects) {
        if (spec.type != SceneObjectType.TREE && spec.type != SceneObjectType.PALM_TREE) continue
        if (!on.keepCandidate(spec, densityScheme)) continue
        trees++
        if (on.palmSpeciesApplied(spec, plantsPalms).type == SceneObjectType.PALM_TREE) return false
    }
    return trees > 0
}

/** Whether this candidate slot should actually render, given the current config. Types with no
 * customization category (e.g. CAR, whose membership is a distributed count -- see [keptCars]
 * and [CarSelection]) are always kept.
 *
 * The three storefronts (restaurant, school, bar) are exempt from density thinning (not from the
 * category's visibility toggle): the catalogue emits exactly one of each per tile
 * (`SceneObjectCatalog.singleShopPerVariant`; the restaurant and the bar since rc3, the school
 * since v5.6), so they are singular compositional anchors -- a fractional density over
 * one-of-a-kind buildings is a coin flip that on Sunset's layout
 * removed the entire commercial street at the default setting. The buildings density slider
 * governs the towers, which are the category's crowd. */
fun SceneCustomization.keepCandidate(spec: StaticSceneObject, scheme: Int = DENSITY_SCHEME_FRACTION): Boolean {
    val config = configFor(spec.type) ?: return true
    if (!config.visible) return false
    if (spec.type == SceneObjectType.SKYSCRAPER &&
        spec.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH
    ) {
        return true
    }
    // See [densityFraction] (v5.8C) and, for a theme saved before it, [DENSITY_SCHEME_GRID].
    return if (scheme == DENSITY_SCHEME_GRID) {
        stableFraction(spec, salt = 0f) < config.density
    } else {
        densityFraction(spec) < config.density.toDouble()
    }
}

/**
 * The car selection every release **before v4.22** shipped: an independent threshold on a stable
 * per-candidate fraction.
 *
 * **Frozen, and not a render-time path.** Rendering selects cars by explicit count since v4.22 —
 * see [CarSelection] and [keptCars] for why a threshold over ten candidates deals a fixed hand
 * rather than a distribution. This expression survives for exactly one caller:
 * `oldSaveWouldHaveWritten` in `CustomThemeData.kt` reconstructs, byte for byte, the car list the
 * pre-v4.3 save path wrote into a damaged override, and that save path filtered with *this*
 * predicate. The reconstruction is the repair's proof of authorship, so it must keep reproducing
 * the historical algorithm however the live selection evolves. Changing this function breaks the
 * repair of every install damaged before v4.3.
 */
internal fun SceneCustomization.legacyKeepCar(spec: CarObject): Boolean =
    cars.visible && stableFraction(spec, salt = 0f) < cars.density

/**
 * Which of the category's two colours (0 = Color 1, 1 = Color 2) this instance wears.
 *
 * ### A coin of its own since v5.7F (item 134)
 *
 * The menu promises *"Each one randomly uses Color 1 or Color 2"*, and until v5.7F this was
 * `stableFraction(spec, salt = 17.3f) < 0.5f` under a comment claiming the salt kept it from
 * correlating with [keepCandidate]'s threshold. It did the opposite. Adding a constant to a value
 * and taking the fractional part only shifts it, so the colour was a fixed function of the very
 * number that decides which objects stand -- and the survivors are the objects whose number is
 * *low*, so they wore Color 2 far more often than Color 1. Measured on the twelve built-ins at
 * their defaults: **94 to 206** over every object that wears the pair (houses alone 21 to 60),
 * and **16 to 68** on the plain cars. Carrying the sum in `Double` does not help, because the
 * cause is the correlation and not the precision (v5.7E: 24 to 57).
 *
 * This is an integer hash of both coordinates at full precision ([CandidateNoise.value] over
 * their raw bits), so it has no relation to the density's fraction by construction. **It changes
 * which colour an object wears and nothing else**: [keepCandidate] is untouched, so the same
 * objects stand in every scene of every theme, and the density slider behaves exactly as before.
 * Measured with it: **151 to 149** over the same objects, and 40 to 44 on the plain cars.
 *
 * ### Why this channel
 *
 * [COLOUR_COIN_CHANNEL] is the one number here that is a choice, and the choice is stated so it
 * can be checked. The gallery card asks this function about seven fixed positions
 * (`ThemePreviewScenes.PreviewIdentity`, the same on all twelve cards), which were picked so each
 * row of the card shows both of the user's colours -- and the cards and those positions are
 * approved and not to be touched. Of channels 1..400, exactly **three** (111, 118, 262) give the
 * same answer as the old coin at all seven, so all twelve cards stay pixel-identical; of those,
 * 118 is the one whose split on the twelve shipped scenes is nearest half-and-half in every
 * category (houses 38/43, buildings 52/46, trees 32/35). `ColourCoinTest` pins the seven answers,
 * because nothing else would notice a card changing colour. It is a fair coin, not a balanced deal: a scene of twenty
 * objects can still come out twelve to eight, which is what a coin does.
 */
private fun variantIndexFor(spec: StaticSceneObject): Int = colourCoin(spec.tileFractionX, spec.depthFraction)

/** The same coin for a car, over the two numbers that identify it on the road. */
private fun variantIndexFor(spec: CarObject): Int = colourCoin(spec.laneYFraction, spec.startDelaySeconds)

private fun colourCoin(first: Float, second: Float): Int =
    if (CandidateNoise.value(first.toRawBits(), second.toRawBits(), COLOUR_COIN_CHANNEL) < 0.5f) 0 else 1

/** See [variantIndexFor] for why 118, and why it is not a free parameter. */
private const val COLOUR_COIN_CHANNEL = 118

private fun blend(config: ObjectVariantConfig, variant: Int, dayBlend: Float): Int {
    val day = if (variant == 0) config.colorDay1 else config.colorDay2
    val night = if (variant == 0) config.colorNight1 else config.colorNight2
    // [SceneColour.blendArgb] rather than `ColorUtils.blendARGB`: the same arithmetic, without a
    // call into `android.graphics.Color`, so which colour a building wears is a question the JVM
    // suite can answer. See that object.
    return SceneColour.blendArgb(night, day, dayBlend.coerceIn(0f, 1f))
}

/**
 * The pair a building drawn as [variant] wears: the houses', the towers' (the Buildings category)
 * or, since v5.11, the shops' own -- the restaurant, the school and the bar. Null for anything that
 * is not one of the six building families.
 *
 * Asked by the drawing, not by the object's type: one candidate pool holds the towers and the shops
 * ([SceneObjectType.SKYSCRAPER] both), and which of the two a building is follows from what it is
 * drawn as -- by its depth in the scene (`SceneObjectRenderer.variantFor`), by its row on the gallery
 * card, which stands towers at depths the scene keeps for shops (`ThemePreviewScenes.PreviewIdentity`).
 */
fun SceneCustomization.buildingColoursFor(variant: SceneSpace.SceneVariant): ObjectVariantConfig? = when (variant) {
    SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE -> houses
    SceneSpace.SceneVariant.TOWER -> buildings
    SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.SCHOOL, SceneSpace.SceneVariant.BAR -> shops
    else -> null
}

/**
 * Whether [other] keeps the same opening hours as this -- both groups' switches and hours (v5.12B2). A change of
 * them is a cut for the people at the windows (`WindowWalk.Doorway.advance`): switched on at night, a shop's glass
 * goes dark at once and its people, walked out, would walk behind it. Any other change of the settings leaves
 * whoever is walking out or in to go on.
 */
fun SceneCustomization.sameOpeningHours(other: SceneCustomization): Boolean =
    shopHoursEnabled == other.shopHoursEnabled && shopOpenHour == other.shopOpenHour && shopCloseHour == other.shopCloseHour &&
        towerHoursEnabled == other.towerHoursEnabled && towerOpenHour == other.towerOpenHour && towerCloseHour == other.towerCloseHour

/**
 * How open a building drawn as [variant] is at [hour24], 0 (closed) .. 1 (open): the towers by the
 * towers' hours, the restaurant, the school and the bar by the shops' (v5.12), a house always 1 --
 * houses keep no hours ([BusinessHours]). The same rule as [buildingColoursFor], by what the building
 * is drawn as, so a building's hours and its colours are always the same group's, on the wallpaper
 * and on the gallery card.
 */
fun SceneCustomization.opennessFor(variant: SceneSpace.SceneVariant, hour24: Float): Float = when (variant) {
    SceneSpace.SceneVariant.TOWER -> BusinessHours.opennessAt(towerHoursEnabled, towerOpenHour, towerCloseHour, hour24)
    SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.SCHOOL, SceneSpace.SceneVariant.BAR ->
        BusinessHours.opennessAt(shopHoursEnabled, shopOpenHour, shopCloseHour, hour24)
    else -> 1f
}

/**
 * The colour [spec] wears at [dayBlend]: one of its category's two colours, by its own coin. A
 * building takes the pair of what it is drawn as ([buildingColoursFor]), so a shop wears the shops'
 * colours and a tower the towers'.
 */
fun SceneCustomization.colorFor(spec: StaticSceneObject, dayBlend: Float): Int {
    val config = (if (spec.type == SceneObjectType.SKYSCRAPER) {
        buildingColoursFor(SceneObjectRenderer.variantFor(spec))
    } else {
        configFor(spec.type)
    }) ?: return 0xFFFFFFFF.toInt()
    return blend(config, variantIndexFor(spec), dayBlend)
}

/**
 * The wall colour of [spec] drawn as the building [variant], at [dayBlend]: [colorFor]'s coin over
 * the pair [buildingColoursFor] names. For a caller that decides the drawing itself -- the gallery
 * card, whose towers stand at depths the scene keeps for shops -- so its colour follows the drawing
 * as the wallpaper's does.
 */
fun SceneCustomization.wallColourFor(spec: StaticSceneObject, variant: SceneSpace.SceneVariant, dayBlend: Float): Int {
    val config = buildingColoursFor(variant) ?: return colorFor(spec, dayBlend)
    return blend(config, variantIndexFor(spec), dayBlend)
}

fun SceneCustomization.colorFor(spec: CarObject, dayBlend: Float): Int = blend(cars, variantIndexFor(spec), dayBlend)

/**
 * Colour [variant] of this category (0 = Color 1, 1 = Color 2) at [dayBlend]: exactly what
 * [colorFor] paints an instance whose coin picked that variant.
 *
 * For the gallery card, which stands a tree, a car or a decoration at a position no layout dealt
 * and so names the variant instead of tossing the coin (`ThemePreviewScenes`). Until v5.9G it
 * painted `colorDay1` / `colorDay2` whatever the card's hour, so on the two midnight cards and on
 * the World & scene strip's night the trees, the cars and the decorations kept their noon colours
 * while the wallpaper blends them toward night (inventory I-38).
 */
internal fun ObjectVariantConfig.colorAt(variant: Int, dayBlend: Float): Int = blend(this, variant, dayBlend)

/**
 * How lit [spec]'s fixed art is at [dayBlend], as the neutral grey a blit multiplies by.
 *
 * The companion of [colorFor] for a sprite that has no tint to interpolate: same category, same
 * per-instance variant, same pair -- so a palm and the tree beside it lose their light together,
 * and two palms whose hashes picked different variants differ from each other exactly as two trees
 * would. See [SceneColour.neutralShade] for why it is a ratio and not a constant.
 *
 * A type with no category has no pair to read, and full daylight is the honest answer: the object
 * is drawn as authored, which is what it was doing before there was a shade at all.
 */
fun SceneCustomization.nightShadeFor(spec: StaticSceneObject, dayBlend: Float): Int {
    val config = configFor(spec.type) ?: return SpriteBlitter.UNTINTED
    return config.nightShadeAt(variantIndexFor(spec), dayBlend)
}

/**
 * The shade of variant [variant] of this category at [dayBlend]: exactly what [nightShadeFor]
 * gives an instance whose coin picked that variant. The gallery card's palm reads it for the
 * reason [colorAt] gives.
 */
internal fun ObjectVariantConfig.nightShadeAt(variant: Int, dayBlend: Float): Int {
    val day = if (variant == 0) colorDay1 else colorDay2
    val night = if (variant == 0) colorNight1 else colorNight2
    return SceneColour.neutralShade(day, night, dayBlend)
}

/** The parasol's 5 wedges alternate between the two configured colors (not a per-instance
 * variant pick like other categories, since a single parasol shows both colors as stripes). */
fun SceneCustomization.parasolStripeColor(wedgeIndex: Int, dayBlend: Float): Int =
    blend(parasols, wedgeIndex % 2, dayBlend)

/**
 * The starting-point [SceneCustomization] for a given theme -- what a user sees the *first* time
 * they open "World & scene" or "Seasons & decorations" for it, before they've changed anything
 * themselves. Every theme starts from [SceneCustomization.DEFAULT] with its own hill, sky, sun and
 * moon colours and its own Santa default -- and, on the twelve built-in themes since v5.11, its own
 * towers' and shops' starting colours ([builtInTowerColours], [builtInShopColours]); the themes listed below then adjust structural
 * categories (City's towers, the winter themes' parasols) and seasonal ones (Christmas's snowmen
 * and gifts, Easter's bunnies and eggs) alike. Nothing is locked in: the user can change any of it
 * and save the result as an override (from "Themes") or as a theme of their own (from "Advanced &
 * about"). Themes not listed here get that base unchanged -- except that a theme which is not built in
 * (a custom or random one) starts its towers and shops from colours chosen against its own ground
 * ([withStartingBuildingColours]).
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
        buildings = builtInTowerColours(themeId) ?: SceneCustomization.DEFAULT.buildings,
        shops = builtInShopColours(themeId) ?: SceneCustomization.DEFAULT.shops,
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
            // **67 %, not the generic 65 %, and it is the whole of the repair for item 139.** The
            // three-shop band of v5.6 took one slot off the tower catalogue (8 -> 7) and moved
            // every rank in it, and on this seed the 65 % threshold then dropped exactly the three
            // slots dealt a spire: Christmas stood four domes and no spire, where v5.5 had stood
            // five towers wearing both crowns. The slot with the lowest threshold above 0.65 is a
            // spire at 0.6602, and the next one above it sits at 0.6719, so 0.67 keeps that one
            // tower and no other -- five towers again, both crowns, and not one house, tree or
            // shop different, because this slider governs the towers alone (the three shops are
            // exempt, see [keepCandidate]).
            //
            // Chosen over repairing the deal because every repair of the deal re-deals the
            // towers of other themes too -- measured, the cheapest one changes the crown of 27
            // towers across nine themes -- while this changes one theme and nothing else. It does
            // not touch [SilhouetteDeal]: the deal is still a pure function of `(seed, slot)`,
            // and moving this slider still removes slots without re-drawing any survivor.
            // `RealThemeSilhouetteDistributionTest` is what fails if a later change to the
            // layout breaks it again.
            buildings = base.buildings.copy(density = 0.67f),
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
        // Each theme's factory values: what a fresh install shows. They began as a quick first
        // pass, just enough that the themes look different from each other instead of all sharing
        // the exact same lake/mountain defaults; most have been reworked since, each with its
        // reason written beside it, and they are kept as they stand now (decided 2026-09-28).
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
        // Every other id: a theme of the user's own, a random one, an imported one -- whose towers and
        // shops start from colours chosen against its own ground, by the twelve's rule (v5.11).
        else -> if (themeId in BUILT_IN_TOWER_COLOURS) base else base.withStartingBuildingColours(theme)
    }
}

/**
 * The towers' starting colours on a built-in theme, or null for any other id (v5.11, inventory
 * I-401; the maintainer's *«procedi con B ma che i colori non siano troppo uguali al terreno
 * altrimenti non si capisce niente»* of 2026-10-06).
 *
 * Until v5.11 every theme's towers were the one slate pair of [SceneCustomization.DEFAULT], and on
 * Big City they all but vanished against hills of the same grey (inventory I-410). Each theme now
 * starts from two colours of its own palette -- brick and ochre on Autumn, powder blue and lilac grey
 * on Winter, terracotta and whitewash on Desert, red and blue on Christmas, gold and lavender on New
 * Year's Eve, coral and sea green on Beach, steel and sand on Big City -- **and each passes the
 * ground rule**: CIE76 dE at least [BuildingGroundContrast.DAY_GATE] from the hills and from every
 * mountain layer the theme shows, by day, and [BuildingGroundContrast.NIGHT_GATE] by night
 * (`BuildingGroundContrastTest`, which also says where the two gates come from). The night colour
 * is the day colour carried 55 % of the way to the scene's ink, `#15161C`, written out so the user
 * edits it like any other.
 *
 * **Only the start.** Color 1 and Color 2 of *Buildings* are the user's to change as they always
 * were; a theme whose look is already stored -- edited and archived, saved in the gallery, restored
 * from a backup, imported -- keeps the colours stored with it until *Reset Buildings to default*.
 * The density and the switch are [SceneCustomization.DEFAULT]'s, which a theme below may change.
 */
internal fun builtInTowerColours(themeId: String): ObjectVariantConfig? {
    val pair = BUILT_IN_TOWER_COLOURS[themeId] ?: return null
    return SceneCustomization.DEFAULT.buildings.copy(
        colorDay1 = pair[0], colorNight1 = pair[1], colorDay2 = pair[2], colorNight2 = pair[3],
    )
}

/** Day 1, night 1, day 2, night 2 for each built-in theme. See [builtInTowerColours]. */
private val BUILT_IN_TOWER_COLOURS: Map<String, IntArray> = mapOf(
    "sunset" to intArrayOf(0xFFD98C6E.toInt(), 0xFF6D4B40.toInt(), 0xFFC8A884.toInt(), 0xFF65574A.toInt()),
    "autumn" to intArrayOf(0xFF9A4E3A.toInt(), 0xFF502F29.toInt(), 0xFFDCB07A.toInt(), 0xFF6E5B46.toInt()),
    "winter" to intArrayOf(0xFF8DA6C0.toInt(), 0xFF4B5665.toInt(), 0xFFB0A8C0.toInt(), 0xFF5A5765.toInt()),
    "desert" to intArrayOf(0xFF9E5539.toInt(), 0xFF523229.toInt(), 0xFFEFE3D0.toInt(), 0xFF77726D.toInt()),
    "christmas" to intArrayOf(0xFFA85049.toInt(), 0xFF573030.toInt(), 0xFF8FA3BC.toInt(), 0xFF4B5564.toInt()),
    "new_year" to intArrayOf(0xFFC2A35E.toInt(), 0xFF625539.toInt(), 0xFFA69AC4.toInt(), 0xFF565167.toInt()),
    "beach" to intArrayOf(0xFFE39A80.toInt(), 0xFF715149.toInt(), 0xFF86C2BE.toInt(), 0xFF476364.toInt()),
    "city" to intArrayOf(0xFF8A96AA.toInt(), 0xFF494F5B.toInt(), 0xFFC2A27F.toInt(), 0xFF625548.toInt()),
    "tundra" to intArrayOf(0xFF94ADC3.toInt(), 0xFF4E5967.toInt(), 0xFF7F8FA3.toInt(), 0xFF444C58.toInt()),
    "easter" to intArrayOf(0xFFDDA4B6.toInt(), 0xFF6F5561.toInt(), 0xFFA4C6DC.toInt(), 0xFF556572.toInt()),
    "halloween" to intArrayOf(0xFF6E5A80.toInt(), 0xFF3D3449.toInt(), 0xFFA86A3C.toInt(), 0xFF573B2A.toInt()),
    "spring" to intArrayOf(0xFFB49CC6.toInt(), 0xFF5C5268.toInt(), 0xFFE2BE96.toInt(), 0xFF716152.toInt()),
)

/**
 * The shops' starting colours on a built-in theme, or null for any other id (v5.11, inventory I-403;
 * the maintainer's *«voglio che i negozi abbiano colore a se, aggiungiamolo»* of 2026-10-06).
 *
 * Until v5.11 the restaurant, the school and the bar wore the towers' slate and were the darkest
 * thing in the street. They start from **the houses' pair** -- the look the maintainer chose from the
 * photographs, the shops among the houses in the houses' paper -- except where that pair does not
 * pass the ground rule of [builtInTowerColours] against the hills a shop stands on
 * ([BuildingGroundContrast]): on the three snow themes the houses' cream is 15.7 to 16.8 from the
 * snow by day, so the shops take a warmer stone; on Beach the first cream is 17.7 from the sand by day
 * and its night 2.3 from the night sand, so the shops take a whiter paper and a darker night.
 * `BuildingGroundContrastTest` holds all twelve to it.
 *
 * **Only the start**, like the towers': Color 1 and Color 2 of *Shops* are the user's to change, and
 * a theme stored before v5.11 keeps the Buildings colours its shops were drawn in.
 */
internal fun builtInShopColours(themeId: String): ObjectVariantConfig? {
    if (themeId !in BUILT_IN_TOWER_COLOURS) return null
    val pair = when (themeId) {
        "winter", "christmas", "tundra" -> SNOW_THEME_SHOP_COLOURS
        "beach" -> BEACH_SHOP_COLOURS
        else -> {
            val houses = SceneCustomization.DEFAULT.houses
            intArrayOf(houses.colorDay1, houses.colorNight1, houses.colorDay2, houses.colorNight2)
        }
    }
    return SceneCustomization.DEFAULT.shops.copy(
        colorDay1 = pair[0], colorNight1 = pair[1], colorDay2 = pair[2], colorNight2 = pair[3],
    )
}

/** Stone rather than cream on snow: 23.5 to 25.0 from it by day. The nights are the houses'. */
private val SNOW_THEME_SHOP_COLOURS =
    intArrayOf(0xFFE2CDAE.toInt(), 0xFF6B5F52.toInt(), 0xFFD8BFAE.toInt(), 0xFF5C4A45.toInt())

/** Whiter paper on the sand by day, and nights darker than the night sand: 22.9-24.0 and 14.3-15.6. */
private val BEACH_SHOP_COLOURS =
    intArrayOf(0xFFF2EBE0.toInt(), 0xFF524440.toInt(), 0xFFF5E0D6.toInt(), 0xFF4E403B.toInt())

/**
 * The towers' and the shops' starting colours on a theme that is **not built in**, by the twelve's rule
 * (v5.11, inventory I-419; the maintainer's *«Ripararlo prima di pubblicare»* of 2026-10-07, asked with
 * the photographs of Big City's slate towers vanishing into its hills).
 *
 * Until then such a theme started from [SceneCustomization.DEFAULT]: the slate every tower had before
 * v5.11, and for the shops the same slate. On a random theme ("Shuffle a random theme") the slate towers
 * went under the night gate on about one hill colour in eight, and the shops were the darkest thing in
 * the street again; a theme of the user's own copied from Big City, after "Reset Buildings to default",
 * had the towers of inventory I-410 back, 3.8 from its hills by day.
 *
 * - **A copy of a built-in theme** -- what "Save current look as..." and a saved version write: the
 *   [SceneTheme] of the theme it was saved from, under a new id -- starts from that theme's pairs
 *   ([builtInTowerColours], [builtInShopColours]), recognised by its sky and hills, which a copy carries
 *   unchanged. A copy of Big City reset to default has Big City's steel and sand.
 * - **Any other theme** -- a random one ([RandomSceneGenerator]), or one imported from a file whose sky
 *   and hills are no built-in theme's -- takes the first pair that clears [BuildingGroundContrast]'s
 *   gates against its own ground ([clearestPair]): for the towers, among the twelve towers' pairs, Big
 *   City's first (the plainest of them); for the shops, the houses' pair, then the snow themes' stone,
 *   then Beach's paper, then the towers' pairs. The ground is this customization's: the theme's hills
 *   and, for a tower, the mountains it shows ([SceneCustomization.DEFAULT]'s, on such a theme).
 *
 * **Only the start**, as on the twelve: the colours stored with a theme -- saved, edited and archived,
 * restored, imported -- are its own and are read as stored; this is what "Reset Buildings to default"
 * and "Reset Shops to default" bring back on it. Only the colours: the switch and the amount stay this
 * customization's.
 */
internal fun SceneCustomization.withStartingBuildingColours(theme: SceneTheme): SceneCustomization {
    val copied = ThemeCatalog.ALL.firstOrNull { it.sharesPaletteWith(theme) }
    val towerPair = copied?.let { BUILT_IN_TOWER_COLOURS.getValue(it.id) }
        ?: clearestPair(TOWER_CANDIDATES, this, SceneSpace.SceneVariant.TOWER)
    val shopPair = copied?.let { builtInShopColours(it.id) }
        ?.let { intArrayOf(it.colorDay1, it.colorNight1, it.colorDay2, it.colorNight2) }
        ?: clearestPair(SHOP_CANDIDATES, this, SceneSpace.SceneVariant.RESTAURANT)
    return copy(buildings = buildings.withPair(towerPair), shops = shops.withPair(shopPair))
}

/** Whether [other]'s sky and hills are this theme's: a saved copy carries them unchanged ([SceneTheme.equals] compares ids). */
private fun SceneTheme.sharesPaletteWith(other: SceneTheme): Boolean =
    skyDay.contentEquals(other.skyDay) && skyNight.contentEquals(other.skyNight) &&
        hillColorsDay.contentEquals(other.hillColorsDay) && hillColorsNight.contentEquals(other.hillColorsNight)

/**
 * The first of [candidates] (day 1, night 1, day 2, night 2) that clears both of
 * [BuildingGroundContrast]'s gates against [ground] for a building drawn as [variant]; if none does, the
 * one that comes closest ([BuildingGroundContrast.margin]).
 */
internal fun clearestPair(candidates: List<IntArray>, ground: SceneCustomization, variant: SceneSpace.SceneVariant): IntArray {
    var best = candidates.first()
    var bestMargin = Float.NEGATIVE_INFINITY
    for (pair in candidates) {
        val margin = BuildingGroundContrast.margin(ground, variant, pair)
        if (margin >= 1f) return pair
        if (margin > bestMargin) { best = pair; bestMargin = margin }
    }
    return best
}

private fun ObjectVariantConfig.withPair(pair: IntArray) =
    copy(colorDay1 = pair[0], colorNight1 = pair[1], colorDay2 = pair[2], colorNight2 = pair[3])

/** The towers' candidates for a theme that is not built in: the twelve built-in pairs, Big City's first. */
private val TOWER_CANDIDATES: List<IntArray> =
    listOf(BUILT_IN_TOWER_COLOURS.getValue("city")) + BUILT_IN_TOWER_COLOURS.filterKeys { it != "city" }.values

/** The shops' candidates for a theme that is not built in: the three pairs the twelve's shops start from, then the towers'. */
private val SHOP_CANDIDATES: List<IntArray> = run {
    val houses = SceneCustomization.DEFAULT.houses
    listOf(
        intArrayOf(houses.colorDay1, houses.colorNight1, houses.colorDay2, houses.colorNight2),
        SNOW_THEME_SHOP_COLOURS,
        BEACH_SHOP_COLOURS,
    ) + TOWER_CANDIDATES
}

// --- Structural vs cosmetic change detection -------------------------------------------------

/**
 * Whether two configs would produce the *same set of rendered candidate slots* for static scene
 * objects.
 *
 * Only [ObjectVariantConfig.visible] and [ObjectVariantConfig.density] are read by
 * [keepCandidate], so those are the only fields that can change which objects exist -- plus
 * [SceneCustomization.palmsEnabled] and [SceneCustomization.palmsInsteadOfTrees], which change
 * which *species* a kept tree slot is, and, **while palms stand in the trees' places**, the
 * Christmas layer ([SceneCustomization.christmasDecorationsEnabled]): there it decides which tree
 * slots stay trees to be drawn as firs (v5.10C2). Only there: with the palms off the fir is decided
 * at the draw, the list does not move, and a Christmas toggle rebuilds nothing, as before.
 * [palmSpeciesApplied] resolves it once, when the runtime list is built, so a palms change that
 * did not rebuild that list never reached a running wallpaper: the switch showed "off" and the
 * palms stayed until something else rebuilt the scene (measured on a device, assessment v5.7 M1).
 * Everything else in [SceneCustomization] -- every colour, the sky/stars/clouds/precipitation/
 * rainbow/mountain/lake/bird sections, hill variation, the seasonal palette flags -- is consumed
 * at draw time and changes only how the existing objects look.
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
        pumpkins.structurallyEquals(other.pumpkins) &&
        palmsEnabled == other.palmsEnabled &&
        palmsInsteadOfTrees == other.palmsInsteadOfTrees &&
        (!palmsInsteadOfTrees || christmasDecorationsEnabled == other.christmasDecorationsEnabled)

/**
 * Whether two configs would produce the same set of rendered cars **at every hour** -- the two
 * densities are two ends of the dusk crossfade, so the night one is as structural as the day one.
 * Separate from [staticStructurallyEquals] so that changing, say, house density touches nothing
 * about the traffic at all.
 *
 * The renderer no longer consults this to decide a rebuild: only a visibility flip rebuilds the
 * runtimes, and any density difference -- day or night -- is picked up by the per-frame count in
 * `SceneObjectRenderer.update`, applied per car and off screen (see [CarSelection]). What this
 * predicate still states, and what its test still pins, is which fields can change the picture's
 * *structure* at all: everything else on [cars] is consumed at draw time.
 */
fun SceneCustomization.carsStructurallyEquals(other: SceneCustomization): Boolean =
    cars.structurallyEquals(other.cars) && carsNightDensity == other.carsNightDensity

/** The subset of a category config that [keepCandidate] and the car selection actually read. */
private fun ObjectVariantConfig.structurallyEquals(other: ObjectVariantConfig): Boolean =
    visible == other.visible && density == other.density
