package com.paperscrape.livewallpaper.engine

/**
 * One object category's settings: visibility, density and 2 colour variants, each a day/night pair that blends
 * like the rest of the scene. Each instance takes variant 1 or 2 by a stable coin of its position
 * (`variantIndexFor`).
 */
data class ObjectVariantConfig(
    val visible: Boolean,
    /**
     * 0f..1f: for static categories the fraction of candidate slots that render, each slot keeping itself by a stable
     * hash ([keepCandidate]); for cars an explicit **count**, 1 car at 0f and every slot at 1f, since ten candidates
     * are too few for independent thresholds to approximate a density ([CarSelection]).
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

/** A mountain layer's settings: one day/night colour pair, like the hills, since its peaks share one
 * silhouette tone. */
data class MountainLayerConfig(
    val visible: Boolean,
    val density: Float,
    val colorDay: Int,
    val colorNight: Int,
    val autoMode: AutoColorMode = AutoColorMode.MANUAL,
) {
    /** Whether the layer draws a mountain: switched on and above 0 % (at 0 % `CandidateThreshold` keeps none). */
    val drawsAny: Boolean get() = visible && density > 0f
}

/**
 * The distant houses on the mountains (`DESIGN_NOTES.md` decision 52): a switch and an amount, nothing else. Their
 * colours are the houses' own ([SceneCustomization.houses]), their snow the winter palette's, their windows lit as
 * a house's ([DistantHouses]); with both mountain layers off there is nothing to stand on, and the switch says so
 * (`SettingsUiModel.distantHouses`). **Off when absent**: a payload, store or backup without it reads [OFF], so
 * nobody finds houses on the mountains they did not ask for.
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

        /** What a theme ships with, and what data without the setting reads as. */
        val OFF = DistantHousesConfig(visible = false, density = STARTING_DENSITY)
    }
}

/** The lake: a backdrop band of its own, outside the hill/object placement, with the sailboats and dolphins on it. */
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
     * Whether there is any water: switched on **and above 0 % height**, 0 % being off (`DESIGN_NOTES.md` decision
     * 45). Then the wallpaper draws no lake and nothing on it
     * (`PaperRenderer.updateLakeBandY`), nor does the gallery card (`ThemePreviewScene`), and the switch says so. The
     * mountains stand on the hills' own line, where a band of no height would put them too.
     */
    val drawsWater: Boolean get() = visible && height > 0f
}

/** One of a bird's 4 colours and its relative weight, which needs not sum to anything: a bird's colour is a weighted
 * draw across all 4 ([BirdsConfig.pickColor]), and the "Bird Colors" sliders show each colour's share of the flock. */
data class BirdColorWeight(val color: Int, val weight: Float)

/** Stars: a switch and a density, no colour (a fixed twinkle colour per theme). */
data class StarsConfig(val visible: Boolean, val density: Float)

/**
 * The sky gradient's 6 colour stops, the shape a user can edit: a top ("High") and bottom ("Low") colour for day and
 * for night, blended by [SunPositionCalculator.DayPhase]'s `dayBlend` like the rest of the scene, and two
 * near-horizon colours (`colorSunriseLow`/`colorSunsetLow`) that show through only near their terminator. Only the
 * bottom has them: the warm glow is a near-horizon phenomenon.
 */
data class SkyConfig(
    val colorDayHigh: Int,
    val colorDayLow: Int,
    val colorNightHigh: Int,
    val colorNightLow: Int,
    val colorSunriseLow: Int,
    val colorSunsetLow: Int,
    /**
     * How high the sun and moon's arc rises, as a fraction of screen height, and with it how high the cloud band sits
     * (`PaperRenderer.cloudBandTop`): one value, because a sun peaking low under high clouds reads as two skies.
     * Stored in scene terms, [SUN_CLOUD_HEIGHT_MIN]..[SUN_CLOUD_HEIGHT_MAX]; the slider shows it as 0-100%.
     */
    val sunCloudHeight: Float,
    /** Who owns the High pair. The two sky bands are two colours, so they toggle separately. */
    val autoModeHigh: AutoColorMode = AutoColorMode.MANUAL,
    /**
     * Who owns the Low pair. `colorSunriseLow`/`colorSunsetLow` have no mode: they are single colours with no
     * day/night twin to derive from or for.
     */
    val autoModeLow: AutoColorMode = AutoColorMode.MANUAL,
)

data class SunConfig(val visible: Boolean, val color: Int)

data class MoonConfig(
    val visible: Boolean,
    val color: Int,
    /** When false, a plain full disc instead of the real astronomical phase. */
    val realisticPhases: Boolean,
)

/** Clouds drifting across the upper sky: their own candidate pool, parallax and density, outside the hill/object
 * placement. */
data class CloudsConfig(
    val visible: Boolean,
    val density: Float,
    val colorDay: Int,
    val colorNight: Int,
    val autoMode: AutoColorMode = AutoColorMode.MANUAL,
)

/** Which of the two precipitation looks [PrecipitationConfig] renders: rain or snow, never both. */
enum class PrecipitationType { RAIN, SNOW }

/**
 * Falling rain or snow, drawn in front of the whole scene but the falling leaves and the lightning flash
 * ([PaperRenderer.draw]'s order): precipitation reads as right in front of the camera. Rain and snow each keep their
 * own colour pair, so switching [type] does not lose the other's. [thunderstorm] adds lightning
 * ([PaperRenderer.drawLightningFlash]), a flag of its own so switching storms keeps the rain's settings; it means
 * something only with [PrecipitationType.RAIN].
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

/** A paper-cutout rainbow arc, a switch of its own independent of [PrecipitationConfig], so a sunny theme can have
 * one; it fades out toward night ([PaperRenderer.drawRainbow]). */
data class RainbowConfig(
    val visible: Boolean,
    /** 0f..1f — how vivid the bands render at full daylight. */
    val opacity: Float,
)

/** A flock of birds crossing the sky, outside the hill/object placement. */
data class BirdsConfig(    val visible: Boolean,
    val density: Float,
    val nightBirds: Boolean,
    val colors: List<BirdColorWeight>,
) {
    /** Weighted pick among [colors] by [randomFraction], which the caller keeps per bird so a bird keeps its colour. */
    fun pickColor(randomFraction: Float): Int {
        // By index: an iterator would allocate on the draw path, per bird and per frame. Summed in `Double`.
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
     * How present the flock is at this `dayBlend`: 1 while the sun is up, then leaving over the first half of the
     * below-horizon range (about 35 minutes on a 06:00/20:00 arc), 0 after dark unless [nightBirds]. Not `dayBlend`
     * itself, which slides to `TERMINATOR_BLEND` (0.5) through the golden hour and would leave the birds half
     * transparent at sunset, when they are most worth seeing: a paper cutout the sky shows through is an artefact.
     */
    fun presenceAt(dayBlend: Float): Float {
        if (nightBirds) return 1f
        return ((dayBlend - GONE_BELOW) / (FULL_ABOVE - GONE_BELOW)).coerceIn(0f, 1f)
    }

    companion object {
        /** `dayBlend` at sunrise and sunset: `SunPositionCalculator.TERMINATOR_BLEND`, private there, restated. */
        const val FULL_ABOVE = 0.5f

        /** Halfway down the below-horizon range: the flock is gone before full dark. */
        const val GONE_BELOW = 0.25f
    }
}

/**
 * Per-theme rendering settings -- every object category, the sky, hills, water, weather and seasonal switches --
 * edited from "World & scene" and "Seasons & decorations". A theme's [SceneObjectLayout] holds *candidate* slots
 * ([SceneObjectCatalog]); this decides how many of them show and in what colours.
 */
data class SceneCustomization(
    val houses: ObjectVariantConfig,
    /**
     * The Buildings category: whether the towers and the buildings among the houses stand, how many towers, and **the
     * towers' colours only** (the shops wear [shops], the public buildings [publics]; `DESIGN_NOTES.md` decision 62).
     */
    val buildings: ObjectVariantConfig,
    /**
     * The colours of the shops -- the restaurant, the bar and the shop (`DESIGN_NOTES.md` decision 48). **Colours
     * only**: whether they stand is [buildings]' switch, since they are one candidate pool with the towers and two
     * switches for the same objects could disagree; [ObjectVariantConfig.visible] and [ObjectVariantConfig.density]
     * here are read by nothing ([keepCandidate], [staticStructurallyEquals]) and have no control. Data without the
     * field reads the Buildings colours stored beside it, which its shops wore (`sceneCustomizationFromJson`,
     * `WallpaperPrefs.readFlatCustomization`), so a theme keeps its look.
     */
    val shops: ObjectVariantConfig,
    /**
     * The colours of the public buildings -- the school, the fire station and the police station
     * (`DESIGN_NOTES.md` decision 62): a pair of each built-in theme's own ([builtInPublicColours]), apart from the
     * ground and from the other groups'. **Colours only**, like [shops]. Data without the field, whose school wore the
     * shops' colours, gives shop colours changed by hand to the publics too and otherwise the theme's pair
     * (`publicsCarriedOver`, `sceneCustomizationFromJson`, `WallpaperPrefs.readFlatCustomization`); with no theme to
     * compare against, the shops' (this default).
     */
    val publics: ObjectVariantConfig = shops,
    val cars: ObjectVariantConfig,
    val parasols: ObjectVariantConfig,
    /**
     * The pedestrians: only visibility and density mean anything, the walk sprites being finished art. Density
     * decides how many of the four candidates render, not which kinds exist.
     */
    val people: ObjectVariantConfig,
    /**
     * How many pedestrians walk **after dark**, 0f..1f: [people]`.density` is the daytime figure, and the renderer
     * crossfades the two on the scene's `dayBlend`, so the street empties over dusk rather than between two frames. A
     * field of its own, not a night density on every [ObjectVariantConfig]: it means something only for a population
     * that comes and goes (the cars have [carsNightDensity]), while houses dissolving at dusk would not. Drivers,
     * passengers and the figures in windows are drawn elsewhere.
     */
    val peopleNightDensity: Float = DEFAULT_PEOPLE_NIGHT_DENSITY,
    /**
     * How much traffic drives **after dark**, 0f..1f: the count in force is the blend of [cars]`.density` and this on
     * the scene's `dayBlend` (`CarSelection.densityAt` -> `countFor`), so the road fills and empties over dusk, car by
     * car and only off screen. Data with one slider reads its daytime setting here ([PeopleDensity.resolveNightDensity]),
     * so its scene does not change until the user moves this.
     */
    val carsNightDensity: Float = DEFAULT_CARS_NIGHT_DENSITY,
    /**
     * Whether the shops -- the restaurant, the bar and the shop -- keep opening hours. **Off by default, and off is
     * every shop always open**, its glass lit at night and its people at their windows, the hours below inert (editable
     * only while this is on). See [BusinessHours] for what open and closed do, and why the houses never keep hours.
     * One of three groups (`DESIGN_NOTES.md` decision 62): the shops here, the towers [towerHoursEnabled], the public
     * buildings [publicHoursEnabled]; a building follows its group, as its colours do ([opennessFor],
     * [storefrontColours]). Data with one setting for all reads it into every group (`WallpaperPrefs`,
     * `sceneCustomizationFromJson`).
     */
    val shopHoursEnabled: Boolean = false,
    /**
     * When the shops open, decimal hours 0..24. `open == close` is always open ("always closed" is the buildings'
     * switch, not an hour); the pair may wrap midnight (09:00-02:00). The default is where the sliders start, an
     * ordinary shop day, and means nothing until [shopHoursEnabled] is on.
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
    /**
     * Whether the public buildings keep opening hours, which are the school's: the fire station and the police station
     * never close ([Storefront.keepsHours]; `DESIGN_NOTES.md` decision 62). Data without it reads the shops' setting
     * and hours, the ones its school kept (`WallpaperPrefs`, `sceneCustomizationFromJson`).
     */
    val publicHoursEnabled: Boolean = false,
    /** When the public buildings open, by [shopOpenHour]'s rules. */
    val publicOpenHour: Float = DEFAULT_BUSINESS_OPEN_HOUR,
    /** When the public buildings close, by [shopOpenHour]'s rules. */
    val publicCloseHour: Float = DEFAULT_BUSINESS_CLOSE_HOUR,
    val trees: ObjectVariantConfig,
    // Fall Colors / Winter Colors: a seasonal palette over the `trees` category's leaves (SceneObjectRenderer.drawTree),
    // not a placeable category. On "Seasons & decorations", not the Trees page: whether a theme has trees is structure,
    // whether they look autumnal or snowy a decoration for any theme. Mutually exclusive, enforced in
    // WallpaperPrefs.setFallColorsEnabled/setWinterColorsEnabled by clearing the other flag in the same edit.
    val fallColorsEnabled: Boolean = false,
    val winterColorsEnabled: Boolean = false,
    /**
     * *Lights on the buildings*, one of the three Christmas switches (`DESIGN_NOTES.md` decision 76): bulbs along every
     * roof and under the sills, a wreath on every front door, a star on every tower, three bulbs on every distant house
     * (`SceneObjectRenderer.drawDecorations`, `drawWindowLights`, `PaperRenderer.drawDistantHouseLights`). **Stored under
     * the one switch's name the three replace** (`christmasDecorationsEnabled`, `christmas_decorations_enabled`), which
     * the other two read where they have no value. Neither this nor [winterColorsEnabled] implies the other; Santa
     * ([santaEnabled]) and the presents ([gifts]) keep their own switches; never on with the Halloween decorations
     * ([DecorationExclusion]).
     */
    val christmasBuildingLightsEnabled: Boolean = false,
    /**
     * *Lights on the trees*: the blinking lights on the leafy trees and the palms (`SceneObjectRenderer.drawTree`,
     * `drawPalmTree`). Data without it reads [christmasBuildingLightsEnabled].
     */
    val christmasTreeLightsEnabled: Boolean = false,
    /**
     * *Christmas firs*: one tree in three standing as a fir, with its ten lights, its star and three presents at its
     * foot (`SceneObjectRenderer.standsAsFir`, `DESIGN_NOTES.md` decision 66). Data without it reads
     * [christmasBuildingLightsEnabled].
     */
    val christmasFirsEnabled: Boolean = false,
    /**
     * Flowers on the open ground: on or off, and nothing else. A plain boolean, not an `ObjectVariantConfig`: the
     * artwork is fixed (three kinds at three sizes), and flowers tinted by a theme are a meadow of the wrong flowers.
     */
    val flowersEnabled: Boolean = false,
    /**
     * Palms on a layout that plants palms of its own (Beach, Desert, their copies, shuffled themes that deal palms).
     * **The other half of the one Palms switch is [palmsInsteadOfTrees]**; which half a theme reads is its layout's
     * ([palmsShown]). Off, those slots draw the broadleaf tree in the same places, an oak on sand rather than a bare
     * beach, swapped once as the renderer builds its list ([palmSpeciesApplied], `DESIGN_NOTES.md` decision 37).
     * **On by default**: [sceneCustomizationFromJson] fills an absent field from [SceneCustomization.DEFAULT], and
     * `false` would fell the palms of every Beach and Desert saved without it; so it is `true` in every theme ever
     * written, and the themes without palms read [palmsInsteadOfTrees].
     */
    val palmsEnabled: Boolean = true,
    /**
     * Palms in place of the trees, on a layout that plants no palm of its own: every ordinary tree slot drawn as a palm
     * in the same place, [palmsEnabled]'s swap run the other way (`DESIGN_NOTES.md` decision 39). **A Christmas fir
     * stays a fir** ([palmSpeciesApplied]). A field of its own, off by default, because [palmsEnabled] is `true` in
     * every theme ever written: data without it reads off, so nobody finds palms they did not ask for.
     * `WallpaperPrefs.setPalmsEnabled` writes both, so from the first move of the one switch ([palmsShown]) they agree.
     */
    val palmsInsteadOfTrees: Boolean = false,
    /**
     * The Halloween presentation, a flag of its own that implies nothing about winter, Christmas or the fall palette
     * (a season and a decoration are different statements). Four things follow from it: the moon becomes
     * `moon_jack_o_lantern`, every broadleaf tree `tree_dead_branches`, every palm `palmtree_fronds_dead`, every pumpkin
     * gets its carved `pumpkin_face`. Whether a pumpkin stands is the pumpkins' switch, so nothing has two controls;
     * the sky is [horrorSkyEnabled]'s.
     */
    val halloweenEnabled: Boolean = false,
    /**
     * The horror sky: near-black overhead, a hard orange band at the horizon. Separate from [halloweenEnabled], all four
     * combinations being scenes. It overrides the six sky colours while on rather than editing them, so switching it
     * off returns the user's palette.
     */
    val horrorSkyEnabled: Boolean = false,
    /**
     * *Laughing moon*: now and then the Halloween moon throws its face open and laughs ([LaughingMoon],
     * `DESIGN_NOTES.md` decision 81). It is the carved moon's, so it needs [halloweenEnabled] and *Moon*; without them
     * the switch reads off with the reason and the stored choice comes back with them (decision 40,
     * `SettingsUiModel.laughingMoon`).
     */
    val laughingMoonEnabled: Boolean = false,
    /**
     * *Night sky by day* (`DESIGN_NOTES.md` decision 82): with the Halloween moon up, the sky, the landscape and the town
     * are night at every hour, the moon full on the sun's path; the shops and the windows keep the real hour
     * (`PaperRenderer.nightOverDay`). It needs [halloweenEnabled] and *Moon*. On as the Halloween theme ships.
     */
    val nightSkyByDayEnabled: Boolean = false,
    /**
     * *Moon stays in place* (`DESIGN_NOTES.md` decision 86): the Halloween moon stands still mid-sky, low over the roofs
     * under the clouds, instead of crossing it -- at every hour with the night by day, otherwise from after sunset to
     * before sunrise, coming out and fading where it stands (`PaperRenderer.stillMoon`). It needs [halloweenEnabled]
     * and *Moon*. On as the Halloween theme ships, off on every other.
     */
    val stillMoonEnabled: Boolean = false,
    /**
     * *Halloween decorations* (`DESIGN_NOTES.md` decisions 83 and 85): a cobweb in a corner of the roof and a
     * jack-o'-lantern by the front door, lit at night, on the houses and the three shops, open or shut
     * (`SceneObjectRenderer.drawHalloweenDecorations`). Never on together with a theme's Christmas decorations
     * ([DecorationExclusion]).
     */
    val halloweenDecorationsEnabled: Boolean = false,
    /**
     * *Blood rain* (`DESIGN_NOTES.md` decision 78): while it rains, the theme's rain or the forecast's, the drops are red
     * ([PaperRenderer.BLOOD_RAIN_NIGHT], [PaperRenderer.BLOOD_RAIN_DAY]) and twice as thick; the user's rain colours stay
     * stored and come back with the switch off. Snow stays snow.
     */
    val bloodRainEnabled: Boolean = false,
    /**
     * *Headless Horseman* (`DESIGN_NOTES.md` decisions 79 and 84): he gallops across the upper sky about once a minute,
     * throwing pumpkins on fire from his hand (`HeadlessHorsemanEffect`). A per-theme switch, as [santaEnabled] is.
     */
    val headlessHorsemanEnabled: Boolean = false,
    // Santa's sleigh, a per-theme switch whose default is the theme's `hasSantaSleigh` (defaultCustomizationFor).
    val santaEnabled: Boolean = false,
    // The seasonal decorations, any of them on any theme ("Seasons & decorations").
    val snowmen: ObjectVariantConfig,
    val gifts: ObjectVariantConfig,
    val penguins: ObjectVariantConfig,
    val bunnies: ObjectVariantConfig,
    val easterEggs: ObjectVariantConfig,
    val pumpkins: ObjectVariantConfig,
    /**
     * The scarecrows (`DESIGN_NOTES.md` decision 80): on a pole, a pointed black hat, an old patched coat and a carved
     * pumpkin for a head, lit at night, **in the snowmen's places** (the same x and depth, [SceneObjectType.SCARECROW]),
     * kept by a coin of their own ([keepCandidate]); where a snowman is kept in the same place, the scarecrow stands to
     * one side. **Visibility and density only**, as [people]: the drawing is fixed art. On at 50 % as the Halloween
     * theme ships, off on every other; data without it reads its theme's (`sceneCustomizationFromJson`,
     * `WallpaperPrefs`), off where the theme is not known.
     */
    val scarecrows: ObjectVariantConfig = SCARECROWS_OFF,
    // How wavy the hill silhouette is: part of a theme's look, saved with it (PaperRenderer.buildBaseHillPath).
    val hillsVariation: Float = 1f,

    /**
     * How much settled snow lies about, 0..1 (0 by default; 1 is as much as the ground carries before it reads as a
     * white floor), only while [winterColorsEnabled] is on: snow on a summer theme's ground is a mistake, not a
     * decoration.
     */
    val snowPiles: Float = 0f,

    /**
     * The autumn counterpart, 0..1, only while [fallColorsEnabled] is on: heaps on the ground. The falling leaves
     * (`drawFallingLeaves`) follow [fallColorsEnabled] alone.
     */
    val leafPiles: Float = 0f,
    // The hills' colour pair: one layer, one pair (PaperRenderer's hillLayerColor() passes it through).
    val hillsColorDay: Int = 0xFFF2A65A.toInt(),
    val hillsColorNight: Int = 0xFF2E2A55.toInt(),
    /** Who owns the hills pair. Lives here because the hills colours do, not inside a config. */
    val hillsAutoMode: AutoColorMode = AutoColorMode.MANUAL,
    // Mountains: two background layers behind the hills, with a slower parallax, outside SceneSpace's depth band so
    // they cannot disturb its geometry. Visible by default, as part of the landscape.
    val mountainsFront: MountainLayerConfig = MountainLayerConfig(
        visible = true, density = 0.5f, colorDay = 0xFF4CAF7C.toInt(), colorNight = 0xFFA9C2B8.toInt(),
    ),
    val mountainsBack: MountainLayerConfig = MountainLayerConfig(
        visible = true, density = 0.5f, colorDay = 0xFF3E8F68.toInt(), colorNight = 0xFF8FA69C.toInt(),
    ),
    /** The little houses on the mountains, off as every theme ships: see [DistantHousesConfig]. */
    val distantHouses: DistantHousesConfig = DistantHousesConfig.OFF,
    // Off by default: a lake is not every landscape's.
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
    // Visible by default, at a modest density.
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
    // Placeholders: defaultCustomizationFor() derives the sky, sun and moon from each theme; these serve unknown ids.
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
    // Off by default: weather a user opts into.
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
        /**
         * The scarecrows as every theme but Halloween ships them, and as data without them reads there: off, at the 50 % a
         * tap starts them at. The colours are never read ([scarecrows]).
         *
         * **In the companion, before [DEFAULT], not a value of the file**: the constructor reads it for its default, and a
         * value of the file is initialised with the file, whose own values read [DEFAULT]; the engine, touching this class
         * first, would read the [DEFAULT] still being built and crash (`FirstClassTouchedTest`).
         */
        val SCARECROWS_OFF = ObjectVariantConfig(
            visible = false,
            density = 0.5f,
            colorDay1 = 0, colorNight1 = 0, colorDay2 = 0, colorNight2 = 0,
        )

        val DEFAULT = SceneCustomization(
            houses = ObjectVariantConfig(
                visible = true,
                // 0.65, not every slot: all ten in every depth band at once read as crowded.
                density = 0.65f,
                colorDay1 = 0xFFF3E6D0.toInt(),
                colorNight1 = 0xFF6B5F52.toInt(),
                colorDay2 = 0xFFE9D6C7.toInt(),
                colorNight2 = 0xFF5C4A45.toInt(),
            ),
            buildings = ObjectVariantConfig(
                visible = true,
                density = 0.65f, // as the houses'
                // The slate a payload without a Buildings block reads; the twelve built-ins start from a pair of
                // their own ([builtInTowerColours]), any other theme from [withStartingBuildingColours].
                colorDay1 = 0xFF454B57.toInt(),
                colorNight1 = 0xFF262A31.toInt(),
                colorDay2 = 0xFF5C6A78.toInt(),
                colorNight2 = 0xFF303842.toInt(),
            ),
            // The Buildings slate, what a payload with neither block reads; the twelve built-ins start from their own
            // ([builtInShopColours]). Visibility and density are inert ([SceneCustomization.shops]).
            shops = ObjectVariantConfig(
                visible = true,
                density = 1f,
                colorDay1 = 0xFF454B57.toInt(),
                colorNight1 = 0xFF262A31.toInt(),
                colorDay2 = 0xFF5C6A78.toInt(),
                colorNight2 = 0xFF303842.toInt(),
            ),
            // The shops', which a school without colours of its own wore; the twelve built-ins start from their own
            // ([builtInPublicColours]), any other theme by their rule ([withStartingBuildingColours]).
            publics = ObjectVariantConfig(
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
                density = 0.65f, // as the houses'
                colorDay1 = 0xFFFF7043.toInt(),
                colorNight1 = 0xFFB5502E.toInt(),
                colorDay2 = 0xFFF7FAFC.toInt(),
                colorNight2 = 0xFFAEB4B8.toInt(),
            ),
            trees = ObjectVariantConfig(
                visible = true,
                density = 0.65f, // as the houses'
                colorDay1 = 0xFF8AA25C.toInt(),
                colorNight1 = 0xFF3F4A2A.toInt(),
                colorDay2 = 0xFF3F9E6B.toInt(),
                colorNight2 = 0xFF244A34.toInt(),
            ),
            snowmen = ObjectVariantConfig(
                visible = false,
                density = 0.5f,
                colorDay1 = 0xFFF7FAFC.toInt(),
                colorNight1 = 0xFFAEB4B8.toInt(),
                colorDay2 = 0xFFEAF3FA.toInt(),
                colorNight2 = 0xFF9BA7B0.toInt(),
            ),
            gifts = ObjectVariantConfig(
                visible = false,
                density = 0.5f,
                colorDay1 = 0xFFC1443B.toInt(),
                colorNight1 = 0xFF7A2B26.toInt(),
                colorDay2 = 0xFF4F8FBF.toInt(),
                colorNight2 = 0xFF335E7D.toInt(),
            ),
            penguins = ObjectVariantConfig(
                visible = false,
                density = 0.5f,
                colorDay1 = 0xFF2E3138.toInt(),
                colorNight1 = 0xFF1A1C20.toInt(),
                colorDay2 = 0xFF3A3E47.toInt(),
                colorNight2 = 0xFF23262B.toInt(),
            ),
            bunnies = ObjectVariantConfig(
                visible = false,
                density = 0.5f,
                colorDay1 = 0xFFF7EFE6.toInt(),
                colorNight1 = 0xFFAFA79C.toInt(),
                colorDay2 = 0xFFE8D5C4.toInt(),
                colorNight2 = 0xFF9C8B77.toInt(),
            ),
            easterEggs = ObjectVariantConfig(
                visible = false,
                density = 0.5f,
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

/** The night-time pedestrian density a fresh install starts with: the daytime default, so the night changes only when
 * the user moves its slider. */
const val DEFAULT_PEOPLE_NIGHT_DENSITY = 1f

/** The night-time car density a fresh install starts with: the cars' daytime default, as [DEFAULT_PEOPLE_NIGHT_DENSITY]. */
const val DEFAULT_CARS_NIGHT_DENSITY = 1f

/**
 * Whether the night by day is in force: *Night sky by day* on, with the Halloween moon it is drawn with (*Halloween* and
 * *Moon* on). The renderer draws by it (`PaperRenderer.nightOverDay`) and the settings say so by it
 * (`SettingsUiModel.nightSkyByDay`, and *Show Sun*, *Show Birds* and *Show Rainbow*, which it stops by day).
 */
fun SceneCustomization.nightOverDayActive(): Boolean = nightSkyByDayEnabled && halloweenEnabled && moon.visible

/** The three Christmas switches all [on] or all off: what data with the one *Christmas lights* switch reads as. */
fun SceneCustomization.withChristmasLights(on: Boolean): SceneCustomization =
    copy(christmasBuildingLightsEnabled = on, christmasTreeLightsEnabled = on, christmasFirsEnabled = on)

/** Whether the Halloween moon laughs: *Laughing moon* on, with the carved moon up -- *Halloween* and *Moon* on. */
fun SceneCustomization.laughingMoonActive(): Boolean = laughingMoonEnabled && halloweenEnabled && moon.visible

/** Whether the Halloween moon stands still: *Moon stays in place* on, with the carved moon -- *Halloween* and *Moon* on. */
fun SceneCustomization.stillMoonActive(): Boolean = stillMoonEnabled && halloweenEnabled && moon.visible

/**
 * **The Halloween decorations and the Christmas ones are never on together in a theme** (`DESIGN_NOTES.md` decision 77:
 * the first turned on wins): the one statement of the rule, for the settings (`SettingsUiModel`), the stores' setters
 * (`WallpaperPrefs`) and every reader of stored data ([resolved]). The Christmas side is the three Christmas switches
 * ([christmasOn]), the Halloween side *Halloween decorations* ([halloweenOn]). **Each counts only while it has something
 * to hang on** ([treesStand], [buildingLightsHang], [halloweenDecorationsHang]): with nothing there a switch reads off
 * and locked (decision 40) and holds the other side off neither. Whether a tree or a house stands is the theme's
 * street's ([standsAny]: a category above 0 % can keep none); without the layout, the amount above 0 % stands for it.
 */
object DecorationExclusion {

    /** Whether any of the Christmas decorations the rule covers is on in [c], each only where it has something to hang on. */
    fun christmasOn(c: SceneCustomization, layout: SceneObjectLayout? = null): Boolean =
        (buildingLightsHang(c, layout) && c.christmasBuildingLightsEnabled) ||
            (treesStand(c, layout) && (c.christmasTreeLightsEnabled || (c.christmasFirsEnabled && firsCanStand(c, layout))))

    /**
     * Whether a Christmas fir can stand on [layout]: a fir takes a leafy tree's place only (`SceneObjectRenderer.standsAsFir`),
     * and on a street that plants palms of its own its palm slots stay palms while the Palms switch is on
     * ([palmSpeciesApplied]), so a street of palms alone grows no fir then, at any amount. Without the layout, one can.
     */
    fun firsCanStand(c: SceneCustomization, layout: SceneObjectLayout? = null): Boolean =
        layout == null || !(c.palmsEnabled && layout.hasPalmSlots()) || layout.staticObjects.any { it.type == SceneObjectType.TREE }

    /**
     * Whether any tree stands to carry the Christmas lights of the trees or to become a fir: one of [layout]'s trees or
     * palms kept ([standsAny]; the palms are the trees' too, `SceneCustomization.configFor`), or without the layout *Show
     * Trees* on and above 0 %.
     */
    fun treesStand(c: SceneCustomization, layout: SceneObjectLayout? = null): Boolean =
        if (layout == null) c.trees.visible && c.trees.density > 0f else c.standsAny(layout, TREE_TYPES)

    /** Whether the Halloween decorations are on in [c], while a house or a shop stands to carry them. */
    fun halloweenOn(c: SceneCustomization, layout: SceneObjectLayout? = null): Boolean =
        halloweenDecorationsHang(c, layout) && c.halloweenDecorationsEnabled

    /**
     * Whether a house or one of the three shops stands to carry the Halloween decorations (`HalloweenDecorations.decorates`):
     * a house standing ([housesStand]), or the places among the houses, where the shops take turns ([placesStand]).
     */
    fun halloweenDecorationsHang(c: SceneCustomization, layout: SceneObjectLayout? = null): Boolean =
        housesStand(c, layout) || placesStand(c, layout)

    /**
     * Whether the places among the houses stand, where the six buildings take turns: *Show Buildings* on -- they stand while
     * it is, whatever the towers' amount -- on a street that has one ([StorefrontStreet.isPlace]). A random theme or a
     * saved one can have none; without the layout, *Show Buildings* stands for it.
     */
    fun placesStand(c: SceneCustomization, layout: SceneObjectLayout? = null): Boolean =
        c.buildings.visible && (layout == null || layout.staticObjects.any { StorefrontStreet.isPlace(it) })

    /** Whether a house stands: one of [layout]'s kept ([standsAny]), or without the layout the houses on and above 0 %. */
    fun housesStand(c: SceneCustomization, layout: SceneObjectLayout? = null): Boolean =
        if (layout == null) c.houses.visible && c.houses.density > 0f else c.standsAny(layout, HOUSE_TYPES)

    /**
     * Whether anything stands to carry the lights on the buildings: a house or a place, as [halloweenDecorationsHang], a tower
     * kept (the star on its roof, [standsAny]), or a house on the mountains (its three bulbs: on, above 0 %, and a mountain
     * to stand on).
     */
    fun buildingLightsHang(c: SceneCustomization, layout: SceneObjectLayout? = null): Boolean =
        halloweenDecorationsHang(c, layout) || (layout != null && c.standsAny(layout, setOf(SceneObjectType.SKYSCRAPER))) ||
            (c.distantHouses.drawsAny && (c.mountainsFront.drawsAny || c.mountainsBack.drawsAny))

    /** Whether the Halloween decorations' switch can be turned on in [c]: not while a Christmas one is on. */
    fun halloweenAllowed(c: SceneCustomization, layout: SceneObjectLayout? = null): Boolean = !christmasOn(c, layout)

    /** Whether a Christmas decoration's switch can be turned on in [c]: not while the Halloween decorations are on. */
    fun christmasAllowed(c: SceneCustomization, layout: SceneObjectLayout? = null): Boolean = !halloweenOn(c, layout)

    /**
     * [c] with at most one side on, by what stands on [layout] (the amounts without it). Data holds both only where one had
     * nothing to hang on when the other was turned on, or a payload was written by hand.
     *
     * **Whether a side goes is read with *Show Buildings* on standing for the places, whatever the street has**
     * ([christmasDrawn], [halloweenDrawn]): the gallery card draws its three shops while *Show Buildings* is on, on any
     * street (`ThemePreviewScene`), so it would draw both sides there. **Which side stays is the one that hangs on the
     * street** ([christmasOn], [halloweenOn], the settings' and the setters' rule), so a switch the settings let the user
     * turn on reads on, and the other, which hangs on nothing there, reads off with its reason and holds nothing. When both
     * hang, or neither, the side the theme ships on stays ([themeDefaults]: Halloween on the Halloween theme), and on
     * every other theme the Christmas side.
     */
    fun resolved(c: SceneCustomization, themeDefaults: SceneCustomization, layout: SceneObjectLayout? = null): SceneCustomization {
        if (!christmasDrawn(c, layout) || !halloweenDrawn(c, layout)) return c
        val christmasHangs = christmasOn(c, layout)
        val halloweenHangs = halloweenOn(c, layout)
        val keepHalloween = if (christmasHangs != halloweenHangs) {
            halloweenHangs
        } else {
            halloweenDrawn(themeDefaults, layout) && !christmasDrawn(themeDefaults, layout)
        }
        return if (keepHalloween) {
            c.copy(christmasBuildingLightsEnabled = false, christmasTreeLightsEnabled = false, christmasFirsEnabled = false)
        } else {
            c.copy(halloweenDecorationsEnabled = false)
        }
    }

    /**
     * [christmasOn] with *Show Buildings* on carrying the lights on any street, as on the gallery card. The firs count where
     * one can stand ([firsCanStand]), as there: the gallery card draws the street's own species, so no fir among palms.
     */
    private fun christmasDrawn(c: SceneCustomization, layout: SceneObjectLayout?): Boolean =
        ((housesStand(c, layout) || c.buildings.visible ||
            (c.distantHouses.drawsAny && (c.mountainsFront.drawsAny || c.mountainsBack.drawsAny))) && c.christmasBuildingLightsEnabled) ||
            (treesStand(c, layout) && (c.christmasTreeLightsEnabled || (c.christmasFirsEnabled && firsCanStand(c, layout))))

    /** [halloweenOn] with *Show Buildings* on carrying the decorations on any street, as on the gallery card. */
    private fun halloweenDrawn(c: SceneCustomization, layout: SceneObjectLayout?): Boolean =
        (housesStand(c, layout) || c.buildings.visible) && c.halloweenDecorationsEnabled

    /**
     * [resolved] on the street [themeId] draws ([streetOf]), worked out only where both sides read on by their amounts --
     * the one case it can change anything, since a side that hangs on the street hangs by its amounts too.
     */
    fun resolvedOn(c: SceneCustomization, themeDefaults: SceneCustomization, themeId: String): SceneCustomization =
        if (christmasOn(c) && halloweenOn(c)) resolved(c, themeDefaults, streetOf(themeId)) else c

    /** The street [themeId] draws, the one its decorations hang on (`SceneObjectCatalog.layoutFor`). */
    fun streetOf(themeId: String): SceneObjectLayout =
        SceneObjectCatalog.layoutFor(themeId, ThemeCatalog.byId(themeId).accentColor)

    /** The trees' category: the leafy trees and the palms. */
    val TREE_TYPES: Set<SceneObjectType> = setOf(SceneObjectType.TREE, SceneObjectType.PALM_TREE)

    /** The houses' category. */
    val HOUSE_TYPES: Set<SceneObjectType> = setOf(SceneObjectType.HOUSE)
}

/**
 * Where the three groups' opening-hours editors start: an ordinary shop day, a seed and nothing more. With a group's
 * switch off the scene is the same whatever these hold (`BusinessHours.opennessAt` returns 1 before reading them).
 */
const val DEFAULT_BUSINESS_OPEN_HOUR = 9f
const val DEFAULT_BUSINESS_CLOSE_HOUR = 20f

/**
 * The arc-height range, in fractions of screen height: the bounds the renderer clamps to, shared with the settings
 * slider, which shows them as 0-100% ([sunCloudHeightFraction]).
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
 * A per-instance value from an object's fixed position (never Random()), the same across frames and [SceneObjectRenderer]
 * rebuilds.
 *
 * **It is a grid, not a fraction in [0, 1)**: the sum runs in `Float`, and the depth term reaches ~985 000 for a
 * front-band object, where a `Float` holds 1/16 and nothing finer, so it takes sixteen values in front and a few dozen
 * behind. As a density threshold it keeps whole steps (11 of 16 at 0.65). [densityFraction] is what the threshold reads;
 * this is the part of it that says which step an object is on, and the whole of it for [DENSITY_SCHEME_GRID].
 */
private fun stableFraction(spec: StaticSceneObject, salt: Float): Float {
    val raw = spec.tileFractionX * 7919f + spec.depthFraction * 7919f * 131f + salt
    return raw - kotlin.math.floor(raw)
}

/**
 * The value [keepCandidate] compares against the density: a real fraction in `[0, 1)`, so the slider's number and the
 * share of objects standing are the same number in front and behind alike.
 *
 * [stableFraction] says which step of its grid an object is on; the step's width is the `Float`'s spacing there
 * (`Math.ulp`), and a second draw -- an integer hash of both coordinates at full precision, on a channel nothing else
 * reads -- says where inside the step it lies. **Refined, not replaced**: a fresh hash would re-deal every scene, while
 * this keeps the grid's order, so the objects standing at a density are a subset of those [DENSITY_SCHEME_GRID] keeps.
 * In `Double`, so the value cannot round up onto the next step's lower edge.
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
 * Which of the snowmen's places a scarecrow keeps: the place's fraction (`NeighbourhoodComposer.stableFraction`) shifted by
 * [SCARECROW_SALT], the draw of the photographs the scarecrows were chosen from. At 50 % each, the snowmen and the
 * scarecrows keep about half their places, some of them both. A fixed number per place compared with the density, so
 * moving the density adds or removes scarecrows and moves none, where a draw from one shared stream would move them all.
 */
internal fun scarecrowFraction(spec: StaticSceneObject): Float =
    NeighbourhoodComposer.stableFraction(spec.tileFractionX, spec.depthFraction, SCARECROW_SALT)

/** The shift of [scarecrowFraction]: the photographed one. */
private const val SCARECROW_SALT = 0.618f

/**
 * Whether a kept scarecrow [spec] stands aside: where the snowman of its place stands too (the same x and depth, the
 * snowman's own keep), both are drawn, the scarecrow to one side (`SceneObjectRenderer.SCARECROW_ASIDE_METRES`).
 */
fun SceneCustomization.scarecrowStandsAside(spec: StaticSceneObject, scheme: Int = DENSITY_SCHEME_FRACTION): Boolean =
    spec.type == SceneObjectType.SCARECROW && keepCandidate(spec.copy(type = SceneObjectType.SNOWMAN), scheme)

/**
 * How a layout's objects are thinned by density: [DENSITY_SCHEME_FRACTION] ([densityFraction]) for every generated layout
 * and every theme saved with it, [DENSITY_SCHEME_GRID] ([stableFraction]) for a saved theme that carries no scheme.
 * **A saved theme keeps what it looked like**: it stores the objects standing, thinned by its rule, and the renderer
 * thins them again by that rule; by the other rule some would drop out.
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
    SceneObjectType.SCARECROW -> scarecrows
    else -> null
}

/**
 * Whether the Palms switch is on, for a theme whose layout does or does not plant palms ([layoutPlantsPalms],
 * [SceneObjectLayout.hasPalmSlots]): [palmsEnabled] or [palmsInsteadOfTrees] (which says why two). The one reading
 * of the switch, for the settings, the gallery card and the wallpaper alike.
 */
fun SceneCustomization.palmsShown(layoutPlantsPalms: Boolean): Boolean =
    if (layoutPlantsPalms) palmsEnabled else palmsInsteadOfTrees

/**
 * The species this slot is drawn as, given whether its layout plants palms ([layoutPlantsPalms]):
 *  - on a layout that plants palms, a palm slot is a palm while [palmsEnabled] is on and an ordinary tree while it is
 *    off; its tree slots (a shuffled theme may deal both) stay trees;
 *  - on a layout that plants none, a tree slot is a palm while [palmsInsteadOfTrees] is on -- **except a fir**: a slot
 *    standing as a Christmas fir (`SceneObjectRenderer.standsAsFir`) stays a tree, and the drawing makes it the fir
 *    (`DESIGN_NOTES.md` decision 39).
 *
 * **Applied once, on the way from the layout to the renderer's object list, not at the blit**: everything downstream
 * reads `spec.type` (`SceneObjectRenderer.variantFor`, `SceneVariant.baseScale`, `spriteUnitsTall`,
 * `SceneObjectCatalog.occluderBoxes`, `recordLeafSource`), so an oak is never drawn at a palm's height inside a palm's
 * box. The shop-visibility pass runs before any customization exists, so a tree, wider than a palm, can cover a shop
 * front more than the pass allowed, as any density setting can; palms in a street laid out for trees, whose crowns
 * reach less far, are measured by `ShopFrontVisibilityTest`.
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
 * Whether this layout plants any palm of its own, which decides the field behind the Palms switch its theme reads
 * ([palmsShown]). Asked of the layout as planted, not of the theme's id: a theme saved from Beach or Desert keeps its
 * palm slots under a `custom:` id, and a shuffled theme may deal palms.
 */
fun SceneObjectLayout.hasPalmSlots(): Boolean =
    staticObjects.any { it.type == SceneObjectType.PALM_TREE }

/**
 * Whether, with the Palms switch on, every tree this layout keeps under [c] would still stand as a Christmas fir, so the
 * switch would put no palm anywhere: a wood thinned far enough under *Christmas firs* can keep only firs (Christmas at
 * 20 % keeps two trees, both firs). The settings ask it so the switch does not read on over a scene with no palm
 * (`DESIGN_NOTES.md` decision 40). False when no tree is kept at all, the trees' own reason. Read through [keepCandidate]
 * and [palmSpeciesApplied], as the renderer builds its list, so it cannot drift from the scene.
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

/** Whether this candidate slot renders under the current config. Types with no category (CAR, whose membership is a
 * count: [keptCars], [CarSelection]) are always kept.
 *
 * The places among the houses are exempt from density thinning, not from the category's switch: five a tile where the
 * six buildings take turns (`SceneObjectCatalog.buildingCandidates`), they anchor the street, and a coin over them can
 * remove the whole commercial street at the default. The buildings' density governs the towers. */
fun SceneCustomization.keepCandidate(spec: StaticSceneObject, scheme: Int = DENSITY_SCHEME_FRACTION): Boolean {
    val config = configFor(spec.type) ?: return true
    if (!config.visible) return false
    if (spec.type == SceneObjectType.SKYSCRAPER &&
        spec.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH
    ) {
        return true
    }
    // The scarecrows stand in the snowmen's places, so by the snowmen's fraction they would keep exactly the snowmen's:
    // a coin of their own decides ([scarecrowFraction]).
    if (spec.type == SceneObjectType.SCARECROW) return scarecrowFraction(spec) < config.density
    // See [densityFraction] and [DENSITY_SCHEME_GRID].
    return if (scheme == DENSITY_SCHEME_GRID) {
        stableFraction(spec, salt = 0f) < config.density
    } else {
        densityFraction(spec) < config.density.toDouble()
    }
}

/**
 * **Whether [layout] stands at least one of its objects of [types]** under this customization: [keepCandidate], the
 * wallpaper's own rule, over the layout's candidates. Not the amount alone: a candidate's fraction is spread over `[0, 1)`
 * with no minimum, unlike the effects' (`CandidateThreshold.fallbackIndexFor`), so a category above 0 % can keep none --
 * ten trees at 4 % keep none on most themes. The places among the houses, which stand at every amount, are not counted:
 * for the buildings' category this asks after the towers.
 */
fun SceneCustomization.standsAny(layout: SceneObjectLayout, types: Set<SceneObjectType>): Boolean =
    layout.staticObjects.any { it.type in types && !StorefrontStreet.isPlace(it) && keepCandidate(it, layout.densityScheme) }

/**
 * A car selection by an independent threshold on a stable per-candidate fraction, the predicate an older save path
 * filtered with. **Frozen, and not a render-time path** (rendering selects cars by count: [CarSelection], [keptCars]):
 * its one caller, `oldSaveWouldHaveWritten` in `CustomThemeData.kt`, reconstructs byte for byte the car list that save
 * path wrote into a damaged override, the repair's proof of authorship. Changing it breaks that repair.
 */
internal fun SceneCustomization.legacyKeepCar(spec: CarObject): Boolean =
    cars.visible && stableFraction(spec, salt = 0f) < cars.density

/**
 * Which of the category's two colours (0 = Color 1, 1 = Color 2) this instance wears: *"Each one randomly uses Color 1
 * or Color 2"*, as the menu says.
 *
 * **A coin of its own**: an integer hash of both coordinates at full precision ([CandidateNoise.value] over their raw
 * bits), unrelated to the density's fraction by construction. A coin taken from that fraction (a salt only shifts it)
 * would follow which objects stand, and the survivors, whose number is low, would wear one colour far more often. It
 * decides the colour and nothing else: [keepCandidate] does not read it.
 *
 * [COLOUR_COIN_CHANNEL] is the one number here that is a choice. The gallery card asks this about seven fixed positions
 * (`ThemePreviewScenes.PreviewIdentity`), picked so each row of the approved cards shows both colours; of channels
 * 1..400, three (111, 118, 262) give the cards' answers at all seven, and 118 splits the twelve shipped scenes nearest
 * half-and-half in every category. `ColourCoinTest` pins the seven answers. A fair coin, not a balanced deal: twenty
 * objects can come out twelve to eight.
 */
private fun variantIndexFor(spec: StaticSceneObject): Int = colourCoin(spec.tileFractionX, spec.depthFraction)

/** [spec]'s colour coin, for the plan of the places (`SceneObjectCatalog`): a tree or a parasol it moves keeps its colour. */
internal fun colourCoinOf(spec: StaticSceneObject): Int = variantIndexFor(spec)

/** The same coin for a car, over the two numbers that identify it on the road. */
private fun variantIndexFor(spec: CarObject): Int = colourCoin(spec.laneYFraction, spec.startDelaySeconds)

private fun colourCoin(first: Float, second: Float): Int =
    if (CandidateNoise.value(first.toRawBits(), second.toRawBits(), COLOUR_COIN_CHANNEL) < 0.5f) 0 else 1

/** See [variantIndexFor] for why 118, and why it is not a free parameter. */
private const val COLOUR_COIN_CHANNEL = 118

private fun blend(config: ObjectVariantConfig, variant: Int, dayBlend: Float): Int {
    val day = if (variant == 0) config.colorDay1 else config.colorDay2
    val night = if (variant == 0) config.colorNight1 else config.colorNight2
    // [SceneColour.blendArgb], not `ColorUtils.blendARGB`: the same arithmetic without `android.graphics.Color`, so
    // the JVM suite can answer which colour a building wears.
    return SceneColour.blendArgb(night, day, dayBlend.coerceIn(0f, 1f))
}

/**
 * The pair a house or a tower drawn as [variant] wears: the houses', or the towers' (Buildings). Null for anything else,
 * the buildings among the houses included, which wear their own group's pair ([storefrontColours]). Asked by the
 * drawing, not by the type: towers and places are one candidate pool ([SceneObjectType.SKYSCRAPER]), told apart by
 * depth (`SceneObjectRenderer.variantFor`) or, on the gallery card, by row (`ThemePreviewScenes.PreviewIdentity`).
 */
fun SceneCustomization.buildingColoursFor(variant: SceneSpace.SceneVariant): ObjectVariantConfig? = when (variant) {
    SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE -> houses
    SceneSpace.SceneVariant.TOWER -> buildings
    else -> null
}

/**
 * The pair a building among the houses wears, by what it is in whichever place it stands: the shops' for the
 * restaurant, the bar and the shop, the public buildings' for the school and the two stations.
 */
fun SceneCustomization.storefrontColours(storefront: Storefront): ObjectVariantConfig = when (storefront.group) {
    StorefrontGroup.SHOPS -> shops
    StorefrontGroup.PUBLIC -> publics
}

/** The pair of a group of the buildings among the houses: [SceneCustomization.shops] or [SceneCustomization.publics]. */
fun SceneCustomization.groupColours(group: StorefrontGroup): ObjectVariantConfig = when (group) {
    StorefrontGroup.SHOPS -> shops
    StorefrontGroup.PUBLIC -> publics
}

/**
 * Whether [other] keeps the same opening hours as this, all three groups' switches and hours. A change of them is a cut
 * for the people at the windows (`WindowWalk.Doorway.advance`): switched on at night, a shop's glass goes dark at once
 * and its people would walk out behind it. Any other change leaves whoever is walking to go on.
 */
fun SceneCustomization.sameOpeningHours(other: SceneCustomization): Boolean =
    shopHoursEnabled == other.shopHoursEnabled && shopOpenHour == other.shopOpenHour && shopCloseHour == other.shopCloseHour &&
        towerHoursEnabled == other.towerHoursEnabled && towerOpenHour == other.towerOpenHour && towerCloseHour == other.towerCloseHour &&
        publicHoursEnabled == other.publicHoursEnabled && publicOpenHour == other.publicOpenHour &&
        publicCloseHour == other.publicCloseHour

/**
 * Whether a house or a tower drawn as [variant] is open at [hour24], 1 or 0: a tower by the towers' hours, a house
 * always ([BusinessHours]). The buildings among the houses keep their group's ([opennessFor] of a [Storefront]): hours
 * and colours follow one rule, on the wallpaper and on the card. The doorway eases the glass toward this over two
 * seconds (`WindowWalk.Doorway.glassAt`).
 */
fun SceneCustomization.opennessFor(variant: SceneSpace.SceneVariant, hour24: Float): Float = when (variant) {
    SceneSpace.SceneVariant.TOWER -> BusinessHours.opennessAt(towerHoursEnabled, towerOpenHour, towerCloseHour, hour24)
    else -> 1f
}

/**
 * Whether a building among the houses is open at [hour24]: the shops by the shops' hours, the school by the public
 * buildings'; **the fire station and the police station are always open**
 * ([Storefront.keepsHours], `DESIGN_NOTES.md` decision 62).
 */
fun SceneCustomization.opennessFor(storefront: Storefront, hour24: Float): Float = when {
    !storefront.keepsHours -> 1f
    storefront.group == StorefrontGroup.SHOPS -> BusinessHours.opennessAt(shopHoursEnabled, shopOpenHour, shopCloseHour, hour24)
    else -> BusinessHours.opennessAt(publicHoursEnabled, publicOpenHour, publicCloseHour, hour24)
}

/**
 * How many of the [people] of a house or a tower drawn as [variant] are at its windows at [hour24]
 * ([BusinessHours.peopleAt]): a tower's none from the minute before its closing, a house's always all.
 */
fun SceneCustomization.peopleFor(variant: SceneSpace.SceneVariant, people: Int, hour24: Float): Int = when (variant) {
    SceneSpace.SceneVariant.TOWER -> BusinessHours.peopleAt(people, towerHoursEnabled, towerOpenHour, towerCloseHour, hour24)
    else -> people
}

/** How many of the [people] of a building among the houses are at its windows at [hour24], by [opennessFor]'s rule. */
fun SceneCustomization.peopleFor(storefront: Storefront, people: Int, hour24: Float): Int = when {
    !storefront.keepsHours -> people
    storefront.group == StorefrontGroup.SHOPS -> BusinessHours.peopleAt(people, shopHoursEnabled, shopOpenHour, shopCloseHour, hour24)
    else -> BusinessHours.peopleAt(people, publicHoursEnabled, publicOpenHour, publicCloseHour, hour24)
}

/**
 * The colour [spec] wears at [dayBlend]: one of its category's two colours, by its own coin. A house
 * or a tower takes the pair of what it is drawn as ([buildingColoursFor]); a building among the houses
 * is drawn with [storefrontWall].
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
 * The wall colour of [spec] drawn as the house or tower [variant], at [dayBlend]: [colorFor]'s coin
 * over the pair [buildingColoursFor] names. For a caller that decides the drawing itself -- the gallery
 * card, whose towers stand at depths the scene keeps for the street's places -- so its colour follows
 * the drawing as the wallpaper's does.
 */
fun SceneCustomization.wallColourFor(spec: StaticSceneObject, variant: SceneSpace.SceneVariant, dayBlend: Float): Int {
    val config = buildingColoursFor(variant) ?: return colorFor(spec, dayBlend)
    return blend(config, variantIndexFor(spec), dayBlend)
}

/**
 * The wall colour of [storefront] at [dayBlend]: its group's pair ([storefrontColours]), by the coin of its [identity],
 * the object it takes its identity from in every place ([StorefrontStreet.identities]), so it keeps its colour.
 */
fun SceneCustomization.storefrontWall(identity: StaticSceneObject, storefront: Storefront, dayBlend: Float): Int =
    blend(storefrontColours(storefront), variantIndexFor(identity), dayBlend)

fun SceneCustomization.colorFor(spec: CarObject, dayBlend: Float): Int = blend(cars, variantIndexFor(spec), dayBlend)

/**
 * Colour [variant] of this category (0 = Color 1, 1 = Color 2) at [dayBlend], as [colorFor] paints an instance whose
 * coin picked it: for the gallery card, which stands objects where no layout dealt them and names the variant
 * (`ThemePreviewScenes`), at its own hour.
 */
internal fun ObjectVariantConfig.colorAt(variant: Int, dayBlend: Float): Int = blend(this, variant, dayBlend)

/**
 * How lit [spec]'s fixed art is at [dayBlend], as the neutral grey a blit multiplies by: [colorFor]'s companion for a
 * sprite with no tint, by the same category, variant and pair, so a palm and the tree beside it lose their light
 * together ([SceneColour.neutralShade] says why a ratio). A type with no category is drawn as authored.
 */
fun SceneCustomization.nightShadeFor(spec: StaticSceneObject, dayBlend: Float): Int {
    val config = configFor(spec.type) ?: return SpriteBlitter.UNTINTED
    return config.nightShadeAt(variantIndexFor(spec), dayBlend)
}

/**
 * The shade of variant [variant] of this category at [dayBlend], as [nightShadeFor] gives it: the gallery card's palm
 * and fir read it, for the reason [colorAt] gives.
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
 * The starting [SceneCustomization] for a theme, what its settings show before the user changes anything:
 * [SceneCustomization.DEFAULT] with the theme's own hill, sky, sun and moon colours and Santa default, and on the twelve
 * built-ins their own towers', shops' and public buildings' colours ([builtInTowerColours], [builtInShopColours],
 * [builtInPublicColours]); the themes listed below then adjust structural and seasonal categories alike. A theme that is
 * not built in starts its three groups from colours chosen against its own ground ([withStartingBuildingColours]).
 */
fun defaultCustomizationFor(themeId: String): SceneCustomization = defaultCustomizationFor(themeId, ThemeCatalog.byId(themeId))

/**
 * [defaultCustomizationFor] with the [theme] given rather than looked up: for a payload read with its own
 * theme beside it (a theme file, a saved theme, a backup's entry), whose id the catalogue may not know yet.
 */
internal fun defaultCustomizationFor(themeId: String, theme: SceneTheme): SceneCustomization {
    // The theme's own hill, sky, sun and moon colours, so editing them starts from the theme's look.
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
        publics = builtInPublicColours(themeId) ?: SceneCustomization.DEFAULT.publics,
    )
    return when (themeId) {
        "winter" -> base.copy(
            // The winter presentation: tree snow caps, roof snow, winter clothing.
            winterColorsEnabled = true,
            // Winter is not Christmas: the plain season, no fairy lights and no presents.
            christmasBuildingLightsEnabled = false,
            christmasTreeLightsEnabled = false,
            christmasFirsEnabled = false,
            // A shade umbrella has no business standing in snow.
            parasols = base.parasols.copy(visible = false),
            snowmen = base.snowmen.copy(visible = true, density = 0.3f),
            mountainsFront = base.mountainsFront.copy(colorDay = 0xFFF7FAFC.toInt(), colorNight = 0xFFC9D6E8.toInt()),
            mountainsBack = base.mountainsBack.copy(colorDay = 0xFFE3ECF5.toInt(), colorNight = 0xFFA9BDD6.toInt()),
            // It snows: precipitation is opt-in everywhere but the two snow themes, whose subject it is.
            precipitation = base.precipitation.copy(visible = true, type = PrecipitationType.SNOW, intensity = 0.45f),
        )
        "christmas" -> base.copy(
            winterColorsEnabled = true,
            // All three Christmas switches on.
            christmasBuildingLightsEnabled = true,
            christmasTreeLightsEnabled = true,
            christmasFirsEnabled = true,
            parasols = base.parasols.copy(visible = false),
            snowmen = base.snowmen.copy(visible = true, density = 0.5f),
            gifts = base.gifts.copy(visible = true, density = 0.4f),
            mountainsFront = base.mountainsFront.copy(colorDay = 0xFFF7FAFC.toInt(), colorNight = 0xFFC9D6E8.toInt()),
            mountainsBack = base.mountainsBack.copy(colorDay = 0xFFE3ECF5.toInt(), colorNight = 0xFFA9BDD6.toInt()),
            // The same exception as Winter's -- see that block.
            precipitation = base.precipitation.copy(visible = true, type = PrecipitationType.SNOW, intensity = 0.45f),
            // **67 %, not the generic 65 %**: on this seed 65 % drops exactly the tower slots dealt a spire, leaving
            // domes only. The lowest threshold above 0.65 is a spire's at 0.6602 and the next is 0.6719, so 0.67 keeps
            // that one tower more and changes nothing else (this slider governs the towers alone, [keepCandidate]).
            // Repairing the deal instead would re-deal other themes' towers. `RealThemeSilhouetteDistributionTest`
            // fails if the layout breaks this.
            buildings = base.buildings.copy(density = 0.67f),
        )
        // Winter, but not Christmas: the season's snow and clothing, without the lights, the presents and Santa;
        // this theme is its night of fireworks, and no parasols.
        "new_year" -> base.copy(
            winterColorsEnabled = true,
            christmasBuildingLightsEnabled = false,
            christmasTreeLightsEnabled = false,
            christmasFirsEnabled = false,
            parasols = base.parasols.copy(visible = false),
            precipitation = base.precipitation.copy(type = PrecipitationType.SNOW),
        )
        "tundra" -> base.copy(
            winterColorsEnabled = true,
            christmasBuildingLightsEnabled = false,
            christmasTreeLightsEnabled = false,
            christmasFirsEnabled = false,
            parasols = base.parasols.copy(visible = false),
            // Tundra is where trees stop: a snow-laden scattering, not a woodland, nor an empty plain.
            trees = base.trees.copy(density = 0.2f),
            snowmen = base.snowmen.copy(visible = true, density = 0.3f),
            penguins = base.penguins.copy(visible = true, density = 0.4f),
            mountainsFront = base.mountainsFront.copy(colorDay = 0xFFF7FAFC.toInt(), colorNight = 0xFFC9D6E8.toInt()),
            mountainsBack = base.mountainsBack.copy(colorDay = 0xFFE3ECF5.toInt(), colorNight = 0xFFA9BDD6.toInt()),
            // Meltwater at the edge of the ice: no sailboats, no dolphins.
            lake = base.lake.copy(
                visible = true, colorDay = 0xFFBFE3EE.toInt(), colorNight = 0xFF2A4550.toInt(), height = 0.25f,
                sailboatsVisible = false, dolphinsVisible = false,
            ),
            precipitation = base.precipitation.copy(type = PrecipitationType.SNOW),
        )
        // The one theme that presets the Halloween flags, so choosing it shows the whole presentation at once. A
        // preset, not a coupling: the user can turn any of them off or on in any combination. Its pumpkins come with
        // it, the season's own decoration; winter, Christmas and the fall palette are untouched.
        "halloween" -> base.copy(
            halloweenEnabled = true,
            horrorSkyEnabled = true,
            pumpkins = base.pumpkins.copy(visible = true, density = 0.5f),
            parasols = base.parasols.copy(visible = false),
            // Every switch of the Halloween page on, off in every other theme (`DESIGN_NOTES.md` decision 75): the
            // laughing moon, the night sky by day, the still moon, the decorations, the blood rain, the Horseman and the
            // scarecrows at the snowmen's 50 %.
            laughingMoonEnabled = true,
            nightSkyByDayEnabled = true,
            stillMoonEnabled = true,
            halloweenDecorationsEnabled = true,
            bloodRainEnabled = true,
            headlessHorsemanEnabled = true,
            scarecrows = base.scarecrows.copy(visible = true, density = 0.5f),
        )
        // Spring: no other season's dressing, a dense canopy, no parasols yet, and the meltwater lake.
        "spring" -> base.copy(
            // The one theme that starts with flowers: without them spring is a green summer.
            flowersEnabled = true,
            trees = base.trees.copy(visible = true, density = 0.7f),
            parasols = base.parasols.copy(visible = false),
            lake = base.lake.copy(visible = true),
        )
        "easter" -> base.copy(
            bunnies = base.bunnies.copy(visible = true, density = 0.3f),
            easterEggs = base.easterEggs.copy(visible = true, density = 0.5f),
        )
        // Autumn: the leaves turn and fall (`fallColorsEnabled`), and the pumpkins, the season's decoration, stand.
        "autumn" -> base.copy(
            fallColorsEnabled = true,
            parasols = base.parasols.copy(visible = false),
            pumpkins = base.pumpkins.copy(visible = true, density = 0.35f),
        )
        // Each theme's factory values, what a fresh install shows, kept as they stand.
        "beach" -> base.copy(
            // Sand: the scene draws one hill layer and reads `SceneTheme.hillColorsDay`'s entry 0, which on Beach is
            // the water's teal; this is the array's second entry.
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
            // A city: towers over cottages, not a village with a skyline behind it.
            buildings = base.buildings.copy(density = 1f),
            houses = base.houses.copy(density = 0.3f),
        )
        // Any other id (the user's own, random, imported): its groups start from colours chosen against its ground.
        else -> if (themeId in BUILT_IN_TOWER_COLOURS) base else base.withStartingBuildingColours(theme)
    }
}

/**
 * The towers' starting colours on a built-in theme, or null for any other id (`DESIGN_NOTES.md` decision 46): two
 * colours of the theme's own palette, **each passing the ground rule** -- CIE76 dE at least
 * [BuildingGroundContrast.DAY_GATE] from the hills and every mountain layer the theme shows by day,
 * [BuildingGroundContrast.NIGHT_GATE] by night (`BuildingGroundContrastTest`, which says where the gates come from).
 * The night colour is the day colour carried 55 % of the way to the scene's ink, `#15161C`, written out so the user
 * edits it like any other. **Only the start**: a theme whose look is stored keeps its colours until *Reset Buildings to
 * default*. The density and the switch are [SceneCustomization.DEFAULT]'s.
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
 * The shops' starting colours on a built-in theme, or null for any other id (`DESIGN_NOTES.md` decision 48): **the
 * houses' pair**, the shops in the houses' paper, except where it fails [builtInTowerColours]' ground rule against the
 * hills a shop stands on ([BuildingGroundContrast]): on the three snow themes the houses' cream is 15.7 to 16.8 from the
 * snow by day, so the shops take a warmer stone; on Beach it is 17.7 from the sand by day and 2.3 by night, so a whiter
 * paper and a darker night. `BuildingGroundContrastTest` holds all twelve to it. **Only the start**, like the towers'.
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

/**
 * The public buildings' starting colours on a built-in theme, or null for any other id (`DESIGN_NOTES.md` decision 62):
 * two paper tones each, the first two of twelve -- sage, blue-grey, lavender, mint, sky, teal, ochre, olive, rose,
 * slate, moss, plum -- that pass [builtInTowerColours]' ground rule against the hills behind any place
 * ([BuildingGroundContrast.groundBehind]) and stand at least CIE76 dE 18 by day and 8 by night from each of the
 * theme's shop colours, 15 and 6 from its towers'. `BuildingGroundContrastTest` holds all twelve to it. Each night
 * colour is its day colour carried 60 % of the way to the scene's ink. **Only the start**; data without them reads
 * `publicsCarriedOver`.
 */
internal fun builtInPublicColours(themeId: String): ObjectVariantConfig? {
    val pair = BUILT_IN_PUBLIC_COLOURS[themeId] ?: return null
    return SceneCustomization.DEFAULT.publics.copy(
        colorDay1 = pair[0], colorNight1 = pair[1], colorDay2 = pair[2], colorNight2 = pair[3],
    )
}

private val BLUE_GREY = intArrayOf(0xFFAFC2D1.toInt(), 0xFF46535E.toInt())
private val LAVENDER = intArrayOf(0xFFC2B8D0.toInt(), 0xFF554D60.toInt())
private val TEAL = intArrayOf(0xFF8FB0B0.toInt(), 0xFF3D4E4E.toInt())
private val OCHRE = intArrayOf(0xFFD8B878.toInt(), 0xFF5E4E30.toInt())
private val SAGE = intArrayOf(0xFFB7C9B0.toInt(), 0xFF4F5C4C.toInt())
private val SKY = intArrayOf(0xFFA9C8DE.toInt(), 0xFF3F5566.toInt())
private val MOSS = intArrayOf(0xFF8FA383.toInt(), 0xFF3E4A39.toInt())

private fun tones(first: IntArray, second: IntArray) = intArrayOf(first[0], first[1], second[0], second[1])

/** Day 1, night 1, day 2, night 2 for each built-in theme. See [builtInPublicColours]. */
private val BUILT_IN_PUBLIC_COLOURS: Map<String, IntArray> = mapOf(
    "sunset" to tones(BLUE_GREY, LAVENDER),
    "autumn" to tones(BLUE_GREY, LAVENDER),
    "winter" to tones(TEAL, OCHRE),
    "desert" to tones(BLUE_GREY, LAVENDER),
    "christmas" to tones(TEAL, OCHRE),
    "new_year" to tones(BLUE_GREY, TEAL),
    "beach" to tones(SAGE, BLUE_GREY),
    "city" to tones(LAVENDER, SKY),
    "tundra" to tones(OCHRE, MOSS),
    "easter" to tones(LAVENDER, OCHRE),
    "halloween" to tones(BLUE_GREY, LAVENDER),
    "spring" to tones(BLUE_GREY, TEAL),
)

/**
 * The public buildings' colours of data without them, whose school wore the shops' colours: **shop colours changed by
 * hand go to the school and the two stations too, unchanged ones give way to the theme's own pair**
 * (`DESIGN_NOTES.md` decision 62). [shops] are the shops' colours as stored and [theme] their theme, null when not
 * known; a pair is changed by hand when any of its four colours or two modes is not the theme's starting one. Without
 * a theme, the shops' colours, the look the school had.
 */
internal fun publicsCarriedOver(shops: ObjectVariantConfig, theme: SceneTheme?): ObjectVariantConfig =
    if (theme == null) shops else publicsCarriedOver(shops, defaultCustomizationFor(theme.id, theme))

/**
 * The theme the catalogue knows as [id] -- a built-in, a random one, a saved version of a built-in, one of
 * the user's own -- or null when it knows no theme by that id (it would answer with Sunset): what a payload
 * of a theme's own is read against ([publicsCarriedOver]).
 */
internal fun themeKnownAs(id: String): SceneTheme? = ThemeCatalog.byId(id).takeIf { it.id == id }

/** [publicsCarriedOver] against the starting customization [defaults] of the theme the shops belong to. */
internal fun publicsCarriedOver(shops: ObjectVariantConfig, defaults: SceneCustomization): ObjectVariantConfig =
    if (shops.sameColoursAs(defaults.shops)) defaults.publics else shops

/** Whether two pairs paint the same: their four colours and their two automatic modes. */
private fun ObjectVariantConfig.sameColoursAs(other: ObjectVariantConfig): Boolean =
    colorDay1 == other.colorDay1 && colorNight1 == other.colorNight1 &&
        colorDay2 == other.colorDay2 && colorNight2 == other.colorNight2 &&
        autoMode1 == other.autoMode1 && autoMode2 == other.autoMode2

/** Stone rather than cream on snow: 23.5 to 25.0 from it by day. The nights are the houses'. */
private val SNOW_THEME_SHOP_COLOURS =
    intArrayOf(0xFFE2CDAE.toInt(), 0xFF6B5F52.toInt(), 0xFFD8BFAE.toInt(), 0xFF5C4A45.toInt())

/** Whiter paper on the sand by day, and nights darker than the night sand: 22.9-24.0 and 14.3-15.6. */
private val BEACH_SHOP_COLOURS =
    intArrayOf(0xFFF2EBE0.toInt(), 0xFF524440.toInt(), 0xFFF5E0D6.toInt(), 0xFF4E403B.toInt())

/**
 * The three groups' starting colours on a theme that is **not built in**, by the twelve's rule, so its towers do not
 * vanish into its hills (`DESIGN_NOTES.md` decision 53). **A copy of a built-in**
 * (the source's [SceneTheme] under a new id), recognised by its sky and hills, starts from that theme's pairs; **any
 * other theme** takes the first candidate that clears [BuildingGroundContrast]'s gates against its own hills and, for a
 * tower, mountains ([clearestPair] over [TOWER_CANDIDATES], [SHOP_CANDIDATES], [PUBLIC_CANDIDATES]). **Only the start**,
 * what its *Reset ... to default* bring back; only the colours, not the switch or the amount.
 */
internal fun SceneCustomization.withStartingBuildingColours(theme: SceneTheme): SceneCustomization {
    val copied = ThemeCatalog.ALL.firstOrNull { it.sharesPaletteWith(theme) }
    val towerPair = copied?.let { BUILT_IN_TOWER_COLOURS.getValue(it.id) }
        ?: clearestPair(TOWER_CANDIDATES, this, SceneSpace.SceneVariant.TOWER)
    val shopPair = copied?.let { builtInShopColours(it.id) }
        ?.let { intArrayOf(it.colorDay1, it.colorNight1, it.colorDay2, it.colorNight2) }
        ?: clearestPair(SHOP_CANDIDATES, this, SceneSpace.SceneVariant.RESTAURANT)
    val publicPair = copied?.let { BUILT_IN_PUBLIC_COLOURS.getValue(it.id) }
        ?: clearestPair(PUBLIC_CANDIDATES, this, SceneSpace.SceneVariant.SCHOOL)
    return copy(
        buildings = buildings.withPair(towerPair),
        shops = shops.withPair(shopPair),
        publics = publics.withPair(publicPair),
    )
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

/** The public buildings' candidates for a theme that is not built in: the twelve's public pairs, Autumn's first, then the shops'. */
private val PUBLIC_CANDIDATES: List<IntArray> by lazy {
    listOf(BUILT_IN_PUBLIC_COLOURS.getValue("autumn")) +
        BUILT_IN_PUBLIC_COLOURS.values.distinctBy { it.toList() }.filterNot { it.contentEquals(BUILT_IN_PUBLIC_COLOURS.getValue("autumn")) } +
        SHOP_CANDIDATES
}

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
 * Whether two configs would produce the *same set of rendered candidate slots* for static scene objects, so
 * [SceneObjectRenderer] keeps its runtime state across any other change. [keepCandidate] reads only visibility and
 * density; the palms' two fields decide a kept tree slot's species, resolved once as the runtime list is built
 * ([palmSpeciesApplied]), and so, **while palms stand in the trees' places**, do the Christmas firs (otherwise a fir is
 * decided at the draw). Everything else is read at draw time. Field by field, since a hash collision would silently
 * skip a rebuild, and allocation-free on a per-frame path. **A new [ObjectVariantConfig] category goes here**:
 * `SceneCustomizationStructureTest` fails if the count of such fields changes.
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
        scarecrows.structurallyEquals(other.scarecrows) &&
        (!palmsInsteadOfTrees || christmasFirsEnabled == other.christmasFirsEnabled)

/**
 * Whether two configs would produce the same set of rendered cars **at every hour**: the two densities are the two ends
 * of the dusk crossfade, so both are structural. Not what decides a rebuild (only a visibility flip rebuilds; a density
 * is picked up per frame by `SceneObjectRenderer.update`, per car and off screen, [CarSelection]): it states, and its
 * test pins, which fields of [cars] can change the picture's structure at all.
 */
fun SceneCustomization.carsStructurallyEquals(other: SceneCustomization): Boolean =
    cars.structurallyEquals(other.cars) && carsNightDensity == other.carsNightDensity

/** The subset of a category config that [keepCandidate] and the car selection actually read. */
private fun ObjectVariantConfig.structurallyEquals(other: ObjectVariantConfig): Boolean =
    visible == other.visible && density == other.density
