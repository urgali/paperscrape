package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.DecorationExclusion
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
import com.paperscrape.livewallpaper.engine.SceneObjectType
import com.paperscrape.livewallpaper.engine.standsAny
import com.paperscrape.livewallpaper.engine.nightOverDayActive
import com.paperscrape.livewallpaper.engine.coverage
import com.paperscrape.livewallpaper.engine.StorefrontGroup
import com.paperscrape.livewallpaper.engine.StorefrontStreet
import com.paperscrape.livewallpaper.engine.keepsOnlyFirsUnderPalms
import com.paperscrape.livewallpaper.engine.palmsShown
import com.paperscrape.livewallpaper.location.DeviceLocationAccess
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import com.paperscrape.livewallpaper.weather.WeatherProviderId

/**
 * Which location source is in use, as one choice instead of two switches. **Presentation only**: what is stored is the
 * pair `useLocationForSunTimes` / `useCustomLocation`, never both true (`WallpaperPrefs.setUseLocation` /
 * `setUseCustomLocation`), the stored positioning kind splitting the device state into GPS and NETWORK.
 */
enum class LocationMode { OFF, GPS, NETWORK, CUSTOM }

/**
 * Which seasonal palette the current theme wears, as one choice instead of two switches. **Presentation only**:
 * `fallColorsEnabled` and `winterColorsEnabled` are never both true (each `WallpaperPrefs` setter clears the other).
 * Christmas lights are not part of it: they hang on whatever the trees look like.
 */
enum class SeasonalPalette { NONE, AUTUMN, WINTER }


/**
 * Why Live Weather cannot drive the scene right now, though the user may have turned it on -- the
 * first thing to put right, in the order a user can put them right.
 */
enum class LiveWeatherBlocker {
    /** Nothing is missing. */
    NONE,

    /** "Follow real time" is off: a fixed hour has no "now" to fetch for. A tap turns it back on. */
    FIXED_HOUR,

    /** No location is chosen. A tap brings the Location choice into view. */
    NO_LOCATION,

    /** The selected provider needs a key and none is set. A tap opens its key. */
    MISSING_KEY,

    /** PaperScrape is not the phone's wallpaper, and the fetch runs inside it. A tap sets it. */
    NOT_THE_WALLPAPER,

    /** The provider answered and refused the key (`LiveWeatherStatus.REJECTED_API_KEY`). A tap opens it. */
    REJECTED_KEY,

    /** The engine reports no position for the chosen source (`LiveWeatherStatus.NO_LOCATION`). Tap: the Location choice. */
    LOCATION_UNAVAILABLE,

    /**
     * GPS or Network is chosen and the phone does not let PaperScrape use its position (no permission, or only the
     * approximate one for GPS: `DeviceLocationAccess.mayUsePosition`, read each time the screen comes back). A tap
     * brings the Location choice into view, where the row says which and leads to the fix.
     */
    LOCATION_NOT_ALLOWED,
}

/**
 * The line under the GPS / Network choice that says whether the phone gives the position
 * (`DESIGN_NOTES.md` §12, decision 40).
 */
enum class DeviceLocationLine {
    /** The phone gives it: the row shows the position. */
    WORKING,

    /** "GPS - tap to allow": no permission. Nothing from the phone is used until it is given. */
    NOT_ALLOWED,

    /** "GPS - tap to allow the precise location": only the approximate one is allowed. Nothing is used. */
    APPROXIMATE_ONLY,

    /** "GPS - tap to turn on location": the phone's location is off; the last position it gave is used. */
    LOCATION_OFF,
}

/** What a tap on that row does. */
enum class DeviceLocationTap {
    /** The position is there: the row is not a button. */
    NOTHING,

    /**
     * The permission dialog for the chosen kind (`DeviceLocationAccess.permissionsToRequest`); where the system will
     * not show it any more, PaperScrape's page in the phone's settings ([SettingsUiModel.afterLocationRequest]).
     */
    ASK_PERMISSION,

    /** The phone's location settings page. */
    OPEN_LOCATION_SETTINGS,
}

/**
 * The Location row under GPS or Network: [working] only while the phone gives the position; otherwise the [line] says
 * what is missing and the [tap] goes where it is put right. [usesLastPosition]: whether the scene meanwhile keeps the
 * last position the phone gave (location switched off) or uses none (no permission).
 */
data class DeviceLocationRowState(
    val working: Boolean,
    val line: DeviceLocationLine,
    val tap: DeviceLocationTap,
    val usesLastPosition: Boolean,
)

/** What the settings screen does with the answer to a location permission request. */
enum class LocationRequestOutcome {
    /** Granted: the choice is stored. */
    STORE,

    /** Granted, and the phone's location is off: the choice is stored and the location page opened. */
    STORE_AND_OPEN_LOCATION_SETTINGS,

    /** Refused, and the system would ask again: the previous choice stays. */
    KEEP_PREVIOUS,

    /**
     * Refused for good (the system no longer shows the dialog) after a refusal already seen on this screen, or for a
     * choice already stored: PaperScrape's page in the phone's settings opens. **Nothing is written**, so a working
     * Custom or Off is never replaced by a GPS that cannot work; the new choice is made once the permission is there.
     */
    OPEN_APP_PAGE,
}

/** What a tap on the Live Weather switch does: whatever is missing, it goes there. */
enum class LiveWeatherTap {
    /** Shown on: the stored flag off. */
    TURN_OFF,

    /** Nothing missing, stored off: the stored flag on. */
    TURN_ON,

    /** The flag on, and the scene back on real time, in one write. */
    FOLLOW_REAL_TIME,

    /** The flag on, and the Location choice brought into view. */
    SHOW_LOCATION,

    /** The flag on, and the selected provider's key opened. */
    OPEN_KEY,

    /** The flag on, and the system's wallpaper preview put up. */
    SET_AS_WALLPAPER,
}

/**
 * The Live Weather switch: drawn **on only while real weather can drive the scene** ([shownOn]); otherwise off, with
 * the reason under it ([blocker]) and a tap that goes where it is put right (`DESIGN_NOTES.md` §12, decision 40). It
 * always accepts a tap, and the stored choice is never written to make the switch look right.
 *
 * @property configuredOn what the user asked for -- the stored `liveWeatherEnabled` flag.
 * @property canBeTurnedOn the two prerequisites the engine checks first: the scene follows real time and a location is
 *   chosen (`LiveWeatherSchedule.runs`, the loop's `fix`). The status banner's "waiting" and "not running" are about it.
 * @property blocker the first reason real weather cannot drive the scene, or [LiveWeatherBlocker.NONE].
 * @property shownOn what the switch is drawn as: [configuredOn] with no [blocker].
 * @property drivingTheScene whether real conditions are what the scene draws: this, not [configuredOn], leaves the
 *   theme's own cloud and precipitation controls out ([SettingsUiModel.forecastOwnsTheWeatherControls]) and is what
 *   "Driven by Live Weather" may claim. **Only while
 *   [shownOn]**: with the switch off, what the engine last published is about a Live Weather that cannot run (another
 *   wallpaper, or a fixed hour set while the settings screen covered the wallpaper), and a stale "driving" would lock
 *   World & scene's clouds behind a switch that reads off. `SettingsUiModelTest` keeps that unreachable.
 */
data class LiveWeatherUiState(
    val configuredOn: Boolean,
    val canBeTurnedOn: Boolean,
    val blocker: LiveWeatherBlocker,
    val shownOn: Boolean,
    val drivingTheScene: Boolean,
)

/**
 * What the "Realistic Moon Phases" control shows, and whether it accepts a tap. It follows the renderer: with
 * `halloweenEnabled` on, `PaperRenderer.drawMoonWithPhase` draws the carved moon, always full, and never reads
 * `moon.realisticPhases`; with *Show Moon* off, `drawCelestialBody` draws no moon. Then the switch shows off and locked.
 * **Nothing writes the difference back**: the stored value is what [shownOn] returns once both allow it again.
 *
 * @property shownOn the stored value, or `false` while overridden.
 * @property interactive whether the switch accepts a tap.
 * @property overriddenByHalloween whether Halloween holds it, so the row owes the sentence that says why.
 * @property needsMoon whether the hidden moon is what holds it (Halloween, when both, is said first).
 */
data class MoonPhasesUiState(
    val shownOn: Boolean,
    val interactive: Boolean,
    val overriddenByHalloween: Boolean,
    val needsMoon: Boolean = false,
)

/**
 * *Laughing moon*, *Night sky by day* or *Moon stays in place* ([SettingsUiModel.laughingMoon],
 * [SettingsUiModel.nightSkyByDay], [SettingsUiModel.stillMoon]): the
 * [DependentSwitchUiState] rule, with what holds it named -- [needsHalloween] while *Halloween* is off, else [needsMoon]
 * while *Moon* is.
 */
data class HalloweenMoonSwitchUiState(
    val shownOn: Boolean,
    val interactive: Boolean,
    val needsHalloween: Boolean,
    val needsMoon: Boolean,
)

/**
 * A decoration switch of one side of the Halloween/Christmas rule (`DecorationExclusion`): [shownOn] its stored
 * value, [interactive] unless the other side is on and this one is off -- then [blockedByTheOther], greyed, and its line
 * says the other's are already on.
 */
data class ExclusiveSwitchUiState(
    val shownOn: Boolean,
    val interactive: Boolean,
    val blockedByTheOther: Boolean,
    /**
     * Nothing stands to carry it -- no tree for *Lights on the trees* or *Christmas firs*, no house or building for *Lights
     * on the buildings* or *Halloween decorations* --: off and locked, the stored choice kept, and the line says what to
     * turn on ([needsLine]).
     */
    val needsSomethingToHangOn: Boolean,
    /** What the line under it says while [needsSomethingToHangOn]; null otherwise. */
    val needsLine: String?,
)

/**
 * A switch that only does something while another control is on -- *Night Birds* under *Show Birds*, *Thunderstorm*
 * under rain: the [MoonPhasesUiState] rule, off and locked while that is off, **the stored value untouched**.
 */
data class DependentSwitchUiState(
    val shownOn: Boolean,
    val interactive: Boolean,
)

/**
 * A *Show X* switch with an amount slider beside it -- density, intensity, opacity, number -- for a thing the wallpaper
 * draws none of at 0 (`DESIGN_NOTES.md` §12, decision 40): at 0 % the switch reads off and says so, and the "decorations
 * on" counts follow it. The stored visibility is kept; raising the slider brings it back, and so does a tap ([amountTap]).
 * Not for the categories that keep something at 0: the buildings (the places among the houses), the cars (one car,
 * `CarSelection`), the people ([people]) and the mountains (at least one).
 *
 * **Above 0 % a category of the street can keep nothing**: `keepCandidate` has no minimum fraction, so at a few per cent
 * a theme may keep none (trees at 4 %, towers at 10 %). Their switch is told whether one stands ([StandingObjects]) and
 * reads off, with its line, when none does (decision 40).
 *
 * **A theme's street can have none of a category at all**: a random theme rolls three to six objects, and a saved theme
 * keeps only what stood when it was saved, so no amount stands one ([StandingObjects.themeHasTrees] and the three like
 * it). There the switch reads off and locked whatever is stored, its line says the theme has none, and a tap writes
 * nothing ([AmountTap.NoneOnThisTheme]): the stored visibility and amount are left as they are.
 *
 * @property shownOn visible, and something drawn: the amount above 0 and, for a category of the street, one standing.
 * @property noneAtZero the amount is 0, and that -- not the switch -- is why nothing is drawn.
 * @property noneAtThisAmount visible and above 0, and nothing stands at that amount on this theme.
 * @property noneOnThisTheme the theme's street has none at any amount. It wins over [noneAtZero] and [noneAtThisAmount],
 *   both false then: they name an amount, and no amount would help.
 */
data class AmountSwitchUiState(
    val shownOn: Boolean,
    val noneAtZero: Boolean,
    val noneAtThisAmount: Boolean = false,
    val noneOnThisTheme: Boolean = false,
)

/**
 * Which of a theme's categories of the street would stand at least one object at the amount a customization gives it,
 * its switch on -- `keepCandidate` over the layout the theme draws ([com.paperscrape.livewallpaper.engine.standsAny]), the
 * wallpaper's own rule: the houses, the towers (the buildings' candidates that are not places among the houses), the trees
 * and palms, the parasols, and the seven decorations. What [SettingsUiModel.amountSwitch] is given; whether the switch is
 * on is its own question, so a category switched off still says whether a tap would show one.
 *
 * [themeHasHouses], [themeHasTowers], [themeHasTrees] and [themeHasParasols] say whether the street has one at all: one
 * stands at 100 %. [themeHasPlaces] says whether it has a place among the houses, where the six buildings -- the shops
 * and the public buildings -- take turns. Every built-in street has all five; a random theme rolls three to six objects
 * and a saved one keeps only what stood when it was saved, so either can have none, and then no amount brings one. The
 * seven decorations are not asked: every street the catalogue lays has all their candidates (`SceneObjectCatalog.layoutFor`).
 * [onlyFirsEvenAtFull]: with the Palms switch on, every tree the street keeps at 100 % would stand as a Christmas fir, so
 * no density puts a palm anywhere ([com.paperscrape.livewallpaper.engine.keepsOnlyFirsUnderPalms]).
 *
 * **The rows and switches that read these take them without a default** (`towerHours`, `towersRowSummary`,
 * `storefrontRowSummary`, `storefrontGroupLine`, `shopHours`, `publicHours`, `ObjectCategorySection`,
 * `StorefrontGroupSection`): a screen that left one out would read a row on over a street that stands none, so the
 * compiler holds every screen to passing the street's.
 */
data class StandingObjects(
    val houses: Boolean,
    val towers: Boolean,
    val trees: Boolean,
    val parasols: Boolean,
    val snowmen: Boolean,
    val gifts: Boolean,
    val penguins: Boolean,
    val bunnies: Boolean,
    val easterEggs: Boolean,
    val pumpkins: Boolean,
    val scarecrows: Boolean,
    val themeHasHouses: Boolean = true,
    val themeHasTowers: Boolean = true,
    val themeHasTrees: Boolean = true,
    val themeHasParasols: Boolean = true,
    val themeHasPlaces: Boolean = true,
    val onlyFirsEvenAtFull: Boolean = false,
)

/**
 * The *Distant houses* switch and its row on World & scene (`DESIGN_NOTES.md` §12, decision 52): on only while houses
 * stand (decision 40) -- the switch on, an amount above 0 % and a mountain layer to stand on. Otherwise
 * the line says which and a tap goes where it is put right; the stored choice is never rewritten.
 *
 * @property shownOn on, above 0 % and with mountains to stand on.
 * @property noneAtZero on, with the mountains, and at 0 %: the amount is why none stands.
 * @property needsMountains both mountain layers off: there is nowhere to stand, whatever is stored.
 * @property summary the row's state on World & scene.
 */
data class DistantHousesUiState(
    val shownOn: Boolean,
    val noneAtZero: Boolean,
    val needsMountains: Boolean,
    val summary: String,
)

/** What a tap on the *Distant houses* switch does. */
sealed interface DistantHousesTap {
    /** The switch's own flag. */
    data class SetVisible(val visible: Boolean) : DistantHousesTap

    /** On, at 0 %: the amount back to [amount] and the flag on, so the houses appear. */
    data class Restore(val amount: Float) : DistantHousesTap

    /**
     * No mountain to stand on: the Mountains page, where that is put right -- with the switch stored
     * on first when the tap asked for it ([storeOn]), so the houses are there once a layer is.
     */
    data class OpenMountains(val storeOn: Boolean) : DistantHousesTap
}

/** What a tap on an [AmountSwitchUiState] switch writes. */
sealed interface AmountTap {
    /** The ordinary case: the switch's own flag. */
    data class SetVisible(val visible: Boolean) : AmountTap

    /**
     * On, at 0 %: the flag on and the amount back to [amount] -- the theme's own, or
     * [SettingsUiModel.FALLBACK_AMOUNT] where that is 0 too -- because "on" at 0 % alone would be a tap
     * that changes nothing.
     */
    data class Restore(val amount: Float) : AmountTap

    /**
     * The theme's street has none of the category ([AmountSwitchUiState.noneOnThisTheme]): nothing is written, neither
     * the flag nor the amount, since no write would make one appear.
     */
    data object NoneOnThisTheme : AmountTap
}

/**
 * How the four boat-and-dolphin controls on *Lake, boats and dolphins* are drawn. They follow the renderer:
 * `PaperRenderer.updateLakeBandY` returns early while `LakeConfig.drawsWater` is false (the lake off, or at 0 % height),
 * so `drawLake` never reaches the sailboats or the dolphins. The [MoonPhasesUiState] rule: off and locked then, and
 * nothing writes the difference back.
 *
 * @property sailboatsShownOn the stored value, or `false`.
 * @property dolphinsShownOn the stored value, or `false`.
 * @property interactive whether the four controls accept a touch.
 * @property blockedByLakeOff whether the lake holds them, so the rows owe the sentence that says why.
 */
data class LakeContentsUiState(
    val sailboatsShownOn: Boolean,
    val dolphinsShownOn: Boolean,
    val interactive: Boolean,
    val blockedByLakeOff: Boolean,
)

/**
 * What the two reset rows of *Advanced & about* say and whether each can be pressed: one for *saved versions* (copies
 * made with "Replace with current", `CustomThemeData.overrides`), one for *current edits* (World & scene and Seasons &
 * decorations, kept per theme in `WallpaperSettings.themeCustomizations`, which win over a saved version). The lists
 * are display names in the gallery's order, so the line and the confirmation name exactly what goes.
 */
data class ThemeResetUiState(
    /** Built-in themes with a saved version ("Replace with current"), by display name. */
    val savedVersions: List<String>,
    /** Built-in themes with edits made in these settings that differ from what they would show without them. */
    val currentEdits: List<String>,
    /** The ids of [currentEdits], which is what the reset is handed. */
    val currentEditIds: List<String> = emptyList(),
)

/**
 * What the settings screens show and what their taps write, worked out from the stored preferences: free of Compose and
 * Android so `SettingsUiModelTest` runs it on the JVM. The write direction (`locationFlags`, `deviceKindFor`,
 * `seasonalPaletteFlags`) is tested but called by no screen: `WeatherTimeScreen` and `SeasonsScreen` write the
 * preferences directly.
 */
object SettingsUiModel {

    /**
     * Which built-in themes each reset would change. A theme counts as edited only if its customization differs from
     * what it would show with the edits gone (its saved version's, else its defaults), so a control moved and moved back
     * does not light the button. [themeCustomizations] already folds in the theme under live edit.
     */
    fun themeResetState(
        builtIns: List<com.paperscrape.livewallpaper.engine.SceneTheme>,
        overrides: Map<String, com.paperscrape.livewallpaper.engine.CustomThemeEntry>,
        themeCustomizations: Map<String, com.paperscrape.livewallpaper.engine.SceneCustomization>,
        defaultFor: (String) -> com.paperscrape.livewallpaper.engine.SceneCustomization =
            { com.paperscrape.livewallpaper.engine.defaultCustomizationFor(it) },
    ): ThemeResetUiState {
        val edited = builtIns.filter { theme ->
            val current = themeCustomizations[theme.id] ?: return@filter false
            current != (overrides[theme.id]?.customization ?: defaultFor(theme.id))
        }
        return ThemeResetUiState(
            savedVersions = builtIns.filter { overrides.containsKey(it.id) }.map { it.displayName },
            currentEdits = edited.map { it.displayName },
            currentEditIds = edited.map { it.id },
        )
    }

    /**
     * The line under *Automatic theme by date*, on the home screen ([overridingYourPick]) and in the gallery. **A
     * calendar may have a gap**, days no season covers, where the theme picked by hand shows unless a holiday covers
     * them (`SeasonalThemeRules.themeForDate` answers null): "every day of the year" is said only without one. The count
     * is the calendar row's own (`SeasonalCalendar.coverage`).
     */
    fun autoThemeLine(calendar: com.paperscrape.livewallpaper.engine.SeasonalCalendar, overridingYourPick: Boolean): String {
        val gaps = calendar.coverage().uncoveredDays.size
        val overriding = if (overridingYourPick) ", overriding your own pick" else ""
        if (gaps == 0) return "The calendar picks a theme for every day of the year$overriding"
        val days = if (gaps == 1) "the day" else "the $gaps days"
        return "The calendar picks a theme for every day a season or a holiday covers$overriding. " +
            "On $days no season covers, your own pick shows unless a holiday covers them"
    }

    /** The gallery's caption under the switch; see [autoThemeLine]. */
    fun autoThemeCaption(calendar: com.paperscrape.livewallpaper.engine.SeasonalCalendar): String =
        if (calendar.coverage().hasGaps) {
            "While this is on, the calendar picks the theme on the days it covers. The theme you choose " +
                "below is the one used on the other days, and whenever you turn it off."
        } else {
            "While this is on, the calendar picks the theme. The theme you choose below is the one used " +
                "whenever you turn it off."
        }

    /** "Autumn", "Autumn and Beach", "Autumn, Beach and Winter": how a line names its themes. */
    fun namesInProse(names: List<String>): String = when (names.size) {
        0 -> ""
        1 -> names[0]
        else -> names.dropLast(1).joinToString(", ") + " and " + names.last()
    }


    /**
     * Reads the stored flags and the stored positioning kind as one mode. The device flag wins the tie (both true) that
     * neither setter produces. With no stored kind `WallpaperSettings` reads `NETWORK`, the positioning an install that
     * never chose one uses.
     */
    fun locationMode(
        useDeviceLocation: Boolean,
        useCustomLocation: Boolean,
        deviceKind: DeviceLocationKind = DeviceLocationKind.NETWORK,
    ): LocationMode = when {
        useDeviceLocation -> when (deviceKind) {
            DeviceLocationKind.GPS -> LocationMode.GPS
            DeviceLocationKind.NETWORK -> LocationMode.NETWORK
        }
        useCustomLocation -> LocationMode.CUSTOM
        else -> LocationMode.OFF
    }

    /**
     * The flag pair a mode means: `(useDeviceLocation, useCustomLocation)`. Both device modes set the device flag, the
     * system being [deviceKindFor], stored alongside; `OFF`, a fresh install's state, clears both.
     */
    fun locationFlags(mode: LocationMode): Pair<Boolean, Boolean> = when (mode) {
        LocationMode.OFF -> false to false
        LocationMode.GPS, LocationMode.NETWORK -> true to false
        LocationMode.CUSTOM -> false to true
    }

    /** The positioning system a mode means, or `null` for the two that use no device sensor. */
    fun deviceKindFor(mode: LocationMode): DeviceLocationKind? = when (mode) {
        LocationMode.GPS -> DeviceLocationKind.GPS
        LocationMode.NETWORK -> DeviceLocationKind.NETWORK
        LocationMode.OFF, LocationMode.CUSTOM -> null
    }


    /**
     * The Live Weather switch's state ([LiveWeatherUiState]) from the stored flag, its prerequisites, the wallpaper and
     * the status the engine published.
     *
     * @param followRealTime `syncWithRealTime`: a fixed-hour scene has no "now" to fetch for.
     * @param devicePositionUsable for GPS and Network, `DeviceLocationAccess.mayUsePosition`, read from the phone each
     *   time the screen comes back; ignored for Custom and Off.
     * @param keyMissing the selected provider needs a key and none is set, from the settings: the status is updated
     *   only by an engine on screen.
     * @param isTheWallpaper whether PaperScrape is the phone's wallpaper (`WallpaperEngineCensus`).
     * @param status what the engine last reported. [LiveWeatherStatus.OFF] also means "not said yet", so the scene counts
     *   as theme-driven until it says otherwise. Only a refused key and no position can hold the switch off, facts the
     *   screen cannot work out itself; the engine forgets them when their inputs change (`PaperWallpaperService`), so a
     *   key typed a moment ago is not read as refused. A network that did not answer ([LiveWeatherStatus.FAILED]) leaves
     *   it on: the app retries by itself.
     */
    fun liveWeather(
        liveWeatherEnabled: Boolean,
        followRealTime: Boolean,
        locationMode: LocationMode,
        devicePositionUsable: Boolean,
        keyMissing: Boolean,
        isTheWallpaper: Boolean,
        status: LiveWeatherStatus,
    ): LiveWeatherUiState {
        val canBeTurnedOn = followRealTime && locationMode != LocationMode.OFF
        val deviceMode = locationMode == LocationMode.GPS || locationMode == LocationMode.NETWORK
        val blocker = when {
            !followRealTime -> LiveWeatherBlocker.FIXED_HOUR
            locationMode == LocationMode.OFF -> LiveWeatherBlocker.NO_LOCATION
            // Read from the phone by the screen, so it holds the switch off at once -- not after the
            // engine has next been on screen and reported no position.
            deviceMode && !devicePositionUsable -> LiveWeatherBlocker.LOCATION_NOT_ALLOWED
            keyMissing -> LiveWeatherBlocker.MISSING_KEY
            !isTheWallpaper -> LiveWeatherBlocker.NOT_THE_WALLPAPER
            // What the engine reported is only about the Live Weather it ran: with the switch off
            // it published OFF, and an older answer is not a reason now.
            !liveWeatherEnabled -> LiveWeatherBlocker.NONE
            status == LiveWeatherStatus.REJECTED_API_KEY -> LiveWeatherBlocker.REJECTED_KEY
            status == LiveWeatherStatus.NO_LOCATION -> LiveWeatherBlocker.LOCATION_UNAVAILABLE
            else -> LiveWeatherBlocker.NONE
        }
        return LiveWeatherUiState(
            configuredOn = liveWeatherEnabled,
            canBeTurnedOn = canBeTurnedOn,
            blocker = blocker,
            shownOn = liveWeatherEnabled && blocker == LiveWeatherBlocker.NONE,
            drivingTheScene = liveWeatherEnabled && blocker == LiveWeatherBlocker.NONE && status.isDrivingTheScene,
        )
    }

    /** How the "Realistic Moon Phases" row is drawn, given the stored value, Halloween and the moon. See [MoonPhasesUiState]. */
    fun moonPhases(storedRealisticPhases: Boolean, halloweenEnabled: Boolean, moonVisible: Boolean = true): MoonPhasesUiState =
        MoonPhasesUiState(
            shownOn = storedRealisticPhases && !halloweenEnabled && moonVisible,
            interactive = !halloweenEnabled && moonVisible,
            overriddenByHalloween = halloweenEnabled,
            needsMoon = !moonVisible && !halloweenEnabled,
        )

    /**
     * *Laughing moon* and *Night sky by day*: both are the Halloween moon's -- the carved face laughing, the carved
     * moon kept up all day -- so each is off and locked while *Halloween* or *Moon* is off, the stored choice kept
     * ([dependentSwitch], the rule of [moonPhases]); [needsHalloween] says which line the row owes (Halloween first).
     */
    fun laughingMoon(c: com.paperscrape.livewallpaper.engine.SceneCustomization): HalloweenMoonSwitchUiState =
        halloweenMoonSwitch(c.laughingMoonEnabled, c)

    fun nightSkyByDay(c: com.paperscrape.livewallpaper.engine.SceneCustomization): HalloweenMoonSwitchUiState =
        halloweenMoonSwitch(c.nightSkyByDayEnabled, c)

    /** *Moon stays in place*: the carved moon kept still, so the same rule ([com.paperscrape.livewallpaper.engine.stillMoonActive]). */
    fun stillMoon(c: com.paperscrape.livewallpaper.engine.SceneCustomization): HalloweenMoonSwitchUiState =
        halloweenMoonSwitch(c.stillMoonEnabled, c)

    private fun halloweenMoonSwitch(stored: Boolean, c: com.paperscrape.livewallpaper.engine.SceneCustomization) =
        HalloweenMoonSwitchUiState(
            shownOn = stored && c.halloweenEnabled && c.moon.visible,
            interactive = c.halloweenEnabled && c.moon.visible,
            needsHalloween = !c.halloweenEnabled,
            needsMoon = c.halloweenEnabled && !c.moon.visible,
        )

    /**
     * *Show Sun*, *Show Birds* and *Show Rainbow* while the night by day is in force
     * ([com.paperscrape.livewallpaper.engine.nightOverDayActive]): the scene is night at every hour, so the sun is never
     * up, the birds fly only as night birds and the rainbow, a daylight thing, never shows -- *Show Sun* and *Show Rainbow*
     * off and locked, *Show Birds* as [showBirds] says, the stored choice kept, and each line says why (decision 40).
     */
    fun showSun(c: com.paperscrape.livewallpaper.engine.SceneCustomization): DependentSwitchUiState =
        dependentSwitch(c.sun.visible, available = !c.nightOverDayActive())

    /**
     * *Show Birds*, as its amount shows it ([amountSwitch]): locked by the night by day while the birds are on and *Night
     * Birds* off -- the birds would fly and the night by day keeps them down, and *Night Birds*, available while the birds are
     * on, is the way out. With the birds off it stays a switch: locked too, it and *Night Birds* ("Needs Show Birds above")
     * would each wait for the other.
     */
    fun showBirds(c: com.paperscrape.livewallpaper.engine.SceneCustomization): DependentSwitchUiState {
        val stored = amountSwitch(c.birds.visible, c.birds.density).shownOn
        return dependentSwitch(stored, available = !c.nightOverDayActive() || c.birds.nightBirds || !stored)
    }

    /** *Show Rainbow*, as its opacity shows it ([amountSwitch]). */
    fun showRainbow(c: com.paperscrape.livewallpaper.engine.SceneCustomization): DependentSwitchUiState =
        dependentSwitch(amountSwitch(c.rainbow.visible, c.rainbow.opacity).shownOn, available = !c.nightOverDayActive())

    /** The line under a switch [showSun], [showBirds] or [showRainbow] locked: what holds it, and where it is set. */
    const val NIGHT_BY_DAY_LOCK_LINE =
        "Halloween's night sky by day is on, so it is night all day. Turn it off in Seasons & decorations, Halloween, " +
            "to set this; your choice is kept until then."

    /** The line under *Show Birds* locked by the night by day: the night birds are the way they fly. */
    const val NIGHT_BY_DAY_BIRDS_LINE =
        "Halloween's night sky by day is on, so it is night all day: turn Night Birds on below to see them, or turn " +
            "the night sky by day off in Seasons & decorations, Halloween. Your choice is kept until then."

    /**
     * *Halloween decorations* and the three Christmas ones are never on together in a theme ([DecorationExclusion],
     * `DESIGN_NOTES.md` §12, decision 77): a switch of the side that is off is greyed while the other side is on, and its
     * line says the other's are already on. One that is on stays a switch that can be turned off.
     *
     * They hang on the houses and on the three shops among them, which stand in the street's places ([hasPlaces]). With
     * nothing to hang on the switch reads off and locked, and **its line names only the ways that would help on this
     * street** ([halloweenDecorationsLine]): a street with neither ([NO_HOUSE_OR_SHOP_ON_THIS_THEME_LINE]) has none.
     */
    fun halloweenDecorations(
        c: com.paperscrape.livewallpaper.engine.SceneCustomization,
        layout: SceneObjectLayout? = null,
    ): ExclusiveSwitchUiState =
        if (DecorationExclusion.halloweenDecorationsHang(c, layout)) {
            exclusive(c.halloweenDecorationsEnabled, DecorationExclusion.halloweenAllowed(c, layout))
        } else {
            nothingToHangOn(halloweenDecorationsLine(c, layout))
        }

    /**
     * The line under *Halloween decorations* while nothing carries them: the houses where the street has them, *Show
     * Buildings* where it has places (the shops stand in them while it is on), what it lacks said. Without the street,
     * both ways.
     */
    private fun halloweenDecorationsLine(
        c: com.paperscrape.livewallpaper.engine.SceneCustomization,
        layout: SceneObjectLayout?,
    ): String {
        val houses = layout == null || themeHas(layout, c, DecorationExclusion.HOUSE_TYPES)
        val places = layout == null || hasPlaces(layout)
        return when {
            !houses && !places -> NO_HOUSE_OR_SHOP_ON_THIS_THEME_LINE
            // Places, and still no shop: Show Buildings is off.
            !houses -> NO_HOUSES_BUILDINGS_OFF_LINE
            !places -> if (housesOnWithNoneStanding(c)) NO_HOUSE_AND_NO_SHOPS_LINE else NEEDS_HOUSES_NO_SHOPS_LINE
            housesOnWithNoneStanding(c) -> NO_HOUSE_FOR_HALLOWEEN_LINE
            else -> NEEDS_HOUSES_LINE
        }
    }

    /**
     * The rules of the decorations that hang on the street are given the street the theme draws ([layout]): a tree or a
     * house stands where `keepCandidate` keeps one, and above 0 % a theme can keep none ([DecorationExclusion]). Without
     * it, the amounts above 0 % stand for it.
     */
    fun christmasDecoration(
        stored: Boolean,
        c: com.paperscrape.livewallpaper.engine.SceneCustomization,
        layout: SceneObjectLayout? = null,
    ): ExclusiveSwitchUiState =
        exclusive(stored, DecorationExclusion.christmasAllowed(c, layout))

    /**
     * *Lights on the buildings*: as [christmasDecoration] while a house, a building or a house on the mountains stands to
     * carry them, and off and locked while none does, the stored choice kept (decision 40), its line naming the ways
     * ([buildingLightsLine]). So does [halloweenDecorations] with no house or shop.
     */
    fun christmasBuildingDecoration(
        stored: Boolean,
        c: com.paperscrape.livewallpaper.engine.SceneCustomization,
        layout: SceneObjectLayout? = null,
    ): ExclusiveSwitchUiState =
        if (DecorationExclusion.buildingLightsHang(c, layout)) {
            christmasDecoration(stored, c, layout)
        } else {
            nothingToHangOn(buildingLightsLine(c, layout))
        }

    /**
     * The line under *Lights on the buildings* while nothing carries them: **only the ways that would help on this
     * street**, and what it lacks said. The houses where it has houses (raised where they are on and none stands, else
     * turned on); the buildings where it has places or towers (turned on, or -- *Show Buildings* on with no place and every
     * tower thinned away -- the Towers raised); the houses on the mountains on every street, by their own switch, or by
     * the mountains where they are on above 0 % and no mountain is shown to stand on. Without the street, the houses and
     * the buildings both. Where every way is a switch it reads "Needs ... on" ([NEEDS_BUILDINGS_LINE]'s form), where one
     * is an amount "No house stands ..." ([NO_HOUSE_FOR_LIGHTS_LINE]'s), which names what does not carry them: the
     * distant houses, whichever of the two is the way.
     */
    internal fun buildingLightsLine(
        c: com.paperscrape.livewallpaper.engine.SceneCustomization,
        layout: SceneObjectLayout?,
    ): String {
        val houses = layout == null || themeHas(layout, c, DecorationExclusion.HOUSE_TYPES)
        val buildings = layout == null || hasPlaces(layout) || themeHas(layout, c, setOf(SceneObjectType.SKYSCRAPER))
        val raiseHouses = houses && housesOnWithNoneStanding(c)
        val raiseTowers = buildings && c.buildings.visible
        val carriers = listOfNotNull(
            "the houses".takeIf { houses && !raiseHouses },
            "the buildings".takeIf { buildings && !c.buildings.visible },
            "the distant houses",
        )
        val noMountain = c.distantHouses.drawsAny && !c.mountainsFront.drawsAny && !c.mountainsBack.drawsAny
        val turnOn = carriers.dropLast(1) + (if (noMountain) "the mountains" else "the distant houses")
        val lacks = listOfNotNull("no houses".takeIf { !houses }, "no buildings".takeIf { !buildings })
        if (!raiseHouses && !raiseTowers) {
            val has = if (lacks.isEmpty()) "" else ": this theme has " + lacks.joinToString(" and ")
            return "Needs ${orList(turnOn)} on in World & scene$has. Your choice is kept until then."
        }
        val stands = when {
            raiseHouses && raiseTowers -> "No house or tower stands at these densities"
            raiseHouses -> "No house stands at this density"
            else -> "No tower stands at this density"
        }
        val carry = if (carriers.size == 1) "${carriers[0]} do not carry them" else "neither ${carriers[0]} nor ${carriers[1]} carry them"
        val raise = "Raise " + listOfNotNull("Houses".takeIf { raiseHouses }, "Towers".takeIf { raiseTowers }).joinToString(" or ")
        val ways = if (raiseHouses && raiseTowers) "$raise, or turn ${orList(turnOn)} on" else "$raise or turn ${orList(turnOn)} on"
        return "$stands on this theme${lacks.joinToString("") { ", it has $it" }}, and $carry. $ways in World & scene; " +
            "your choice is kept until then."
    }

    /** "A", "A or B", "A, B or C": how a line names its ways. */
    private fun orList(items: List<String>): String =
        if (items.size == 1) items[0] else items.dropLast(1).joinToString(", ") + " or " + items.last()

    /**
     * *Lights on the trees* or *Christmas firs*: as [christmasDecoration] while trees stand, and **off and locked while
     * none does** -- *Show Trees* off, at 0 %, or too low for one to stand --, the stored choice kept and back with the
     * trees (decision 40; *Palms* follows the same rule on the Trees page). Its line says what is missing
     * ([NEEDS_TREES_LINE] or [NO_TREE_AT_THIS_DENSITY_LINE]), or -- the trees being the one thing they hang on -- that the
     * theme's street has no tree at all, where neither would help ([noneOnThisThemeLine]).
     */
    fun christmasTreeDecoration(
        stored: Boolean,
        c: com.paperscrape.livewallpaper.engine.SceneCustomization,
        layout: SceneObjectLayout? = null,
    ): ExclusiveSwitchUiState = when {
        DecorationExclusion.treesStand(c, layout) -> christmasDecoration(stored, c, layout)
        layout != null && !themeHas(layout, c, DecorationExclusion.TREE_TYPES) -> nothingToHangOn(noneOnThisThemeLine("trees"))
        c.trees.visible && c.trees.density > 0f -> nothingToHangOn(NO_TREE_AT_THIS_DENSITY_LINE)
        else -> nothingToHangOn(NEEDS_TREES_LINE)
    }

    /**
     * *Christmas firs*: as [christmasTreeDecoration], and also off and locked where trees stand but no fir can
     * ([DecorationExclusion.firsCanStand]: a street of palms alone with the Palms switch on, whose palm slots stay palms),
     * the line saying the Palms switch is the way ([PALMS_KEEP_NO_FIRS_LINE]). The trees' own lines come first: while no
     * tree stands the Palms switch is locked too.
     */
    fun christmasFirs(
        stored: Boolean,
        c: com.paperscrape.livewallpaper.engine.SceneCustomization,
        layout: SceneObjectLayout? = null,
    ): ExclusiveSwitchUiState {
        val trees = christmasTreeDecoration(stored, c, layout)
        return if (trees.needsSomethingToHangOn || DecorationExclusion.firsCanStand(c, layout)) trees else nothingToHangOn(PALMS_KEEP_NO_FIRS_LINE)
    }

    /** The line under *Christmas firs* where the palms take every tree's place ([christmasFirs]). */
    const val PALMS_KEEP_NO_FIRS_LINE =
        "The palms are on, and the firs grow only among the leafy trees. Turn Palms off in World & scene, Trees; your " +
            "choice is kept until then."

    /** The houses on and above 0 %: with none standing, the amount is why ([NO_HOUSE_FOR_HALLOWEEN_LINE]). */
    private fun housesOnWithNoneStanding(c: com.paperscrape.livewallpaper.engine.SceneCustomization): Boolean =
        c.houses.visible && c.houses.density > 0f

    private fun nothingToHangOn(line: String) =
        ExclusiveSwitchUiState(shownOn = false, interactive = false, blockedByTheOther = false, needsSomethingToHangOn = true, needsLine = line)

    private fun exclusive(stored: Boolean, allowed: Boolean) = ExclusiveSwitchUiState(
        shownOn = stored, interactive = stored || allowed, blockedByTheOther = !stored && !allowed,
        needsSomethingToHangOn = false, needsLine = null,
    )

    /** The line under *Halloween decorations* while a Christmas decoration is on ([halloweenDecorations]). */
    const val CHRISTMAS_ALREADY_ON_LINE =
        "Christmas decorations are already on. Turn them off in Christmas to hang these."

    /** The line under a Christmas decoration while the Halloween decorations are on ([christmasDecoration]). */
    const val HALLOWEEN_ALREADY_ON_LINE =
        "Halloween decorations are already on. Turn them off in Halloween to use this."

    /** The line under *Lights on the trees* or *Christmas firs* while the trees are off or at 0 % ([christmasTreeDecoration]). */
    const val NEEDS_TREES_LINE =
        "Needs Show Trees on in World & scene. Your choice is kept until then."

    /** The line under *Halloween decorations* while the houses are off or at 0 % and no shop stands ([halloweenDecorations]). */
    const val NEEDS_HOUSES_LINE =
        "Needs the houses or the buildings on in World & scene. Your choice is kept until then."

    /** The line under *Lights on the buildings* while nothing stands to carry them ([christmasBuildingDecoration]). */
    const val NEEDS_BUILDINGS_LINE =
        "Needs the houses, the buildings or the distant houses on in World & scene. Your choice is kept until then."

    /** The line under *Lights on the trees* or *Christmas firs* while *Show Trees* is on and no tree stands at its density. */
    const val NO_TREE_AT_THIS_DENSITY_LINE =
        "No tree stands at this density on this theme. Raise Trees in World & scene; your choice is kept until then."

    /** The line under *Halloween decorations* while the houses are on and none stands, and the buildings are off. */
    const val NO_HOUSE_FOR_HALLOWEEN_LINE =
        "No house stands at this density on this theme, and the buildings are off. Raise Houses or turn the " +
            "buildings on in World & scene; your choice is kept until then."

    /** The line under *Lights on the buildings* while the houses are on and none stands, and nothing else carries them. */
    const val NO_HOUSE_FOR_LIGHTS_LINE =
        "No house stands at this density on this theme, and neither the buildings nor the distant houses carry them. " +
            "Raise Houses or turn the buildings or the distant houses on in World & scene; your choice is kept until then."

    /** The line under *Halloween decorations* on a street with no house and no place among the houses ([halloweenDecorations]). */
    const val NO_HOUSE_OR_SHOP_ON_THIS_THEME_LINE = "This theme has no houses and no shops."

    /** The line under *Halloween decorations* on a street with places and no house, *Show Buildings* off. */
    const val NO_HOUSES_BUILDINGS_OFF_LINE =
        "This theme has no houses, and the buildings are off. Turn the buildings on in World & scene; your choice is kept " +
            "until then."

    /** The line under *Halloween decorations* on a street with houses and no place, the houses on and none standing. */
    const val NO_HOUSE_AND_NO_SHOPS_LINE =
        "No house stands at this density on this theme, and it has no shops. Raise Houses in World & scene; your choice " +
            "is kept until then."

    /** The line under *Halloween decorations* on a street with houses and no place, the houses off or at 0 %. */
    const val NEEDS_HOUSES_NO_SHOPS_LINE =
        "Needs the houses on in World & scene: this theme has no shops. Your choice is kept until then."

    /**
     * The line under *Palms* where every tree the street keeps would stand as a Christmas fir even at 100 %
     * ([StandingObjects.onlyFirsEvenAtFull]): raising the density would put no palm anywhere.
     */
    const val PALMS_ONLY_FIRS_ON_THIS_THEME_LINE =
        "Every tree on this theme is a Christmas fir, and the firs stay. Turn the Christmas firs off; your choice is kept " +
            "until then."

    /** The line under *Palms* on a street with no tree: the palms take the trees' places. */
    const val PALMS_NO_TREES_ON_THIS_THEME_LINE = "This theme has no trees, and palms stand where the trees do."

    /**
     * The line under a switch whose [things] the theme's street has none of at any amount -- *Show Houses*, *Show Trees*,
     * *Show Parasols*, *Show Buildings* ([AmountSwitchUiState.noneOnThisTheme]), *Tower opening hours* with no tower, *Lights
     * on the trees* and *Christmas firs* with no tree, the shops' and the public buildings' hours and pages with no place
     * among the houses: "This theme has no trees."
     */
    fun noneOnThisThemeLine(things: String): String = "This theme has no $things."

    /** [noneOnThisThemeLine] as a row of World & scene says it: with no full stop, like the rows' other states. */
    fun noneOnThisThemeSummary(things: String): String = "This theme has no $things"

    /**
     * What a tap on the Live Weather switch does. Every state leads somewhere; the stored flag is turned on with the step
     * that puts right what holds it, so the choice shows the moment nothing is missing.
     */
    fun liveWeatherTap(state: LiveWeatherUiState): LiveWeatherTap = when {
        state.shownOn -> LiveWeatherTap.TURN_OFF
        else -> when (state.blocker) {
            LiveWeatherBlocker.NONE -> LiveWeatherTap.TURN_ON
            LiveWeatherBlocker.FIXED_HOUR -> LiveWeatherTap.FOLLOW_REAL_TIME
            LiveWeatherBlocker.NO_LOCATION, LiveWeatherBlocker.LOCATION_UNAVAILABLE, LiveWeatherBlocker.LOCATION_NOT_ALLOWED ->
                LiveWeatherTap.SHOW_LOCATION
            LiveWeatherBlocker.MISSING_KEY, LiveWeatherBlocker.REJECTED_KEY -> LiveWeatherTap.OPEN_KEY
            LiveWeatherBlocker.NOT_THE_WALLPAPER -> LiveWeatherTap.SET_AS_WALLPAPER
        }
    }

    /**
     * The Palms switch on the Trees page of World & scene: the half of the switch the theme reads ([palmsShown]), off and
     * locked ([dependentSwitch]) unless a palm would stand -- the palms take the trees' places (`palmSpeciesApplied` swaps
     * only the slots `keepCandidate` kept), so they need a tree standing, and the Christmas firs stay firs, so a wood
     * thinned under *Christmas firs* may keep nothing but firs ([onlyFirsWouldStand],
     * `SceneObjectLayout.keepsOnlyFirsUnderPalms`).
     */
    fun palms(
        c: com.paperscrape.livewallpaper.engine.SceneCustomization,
        layoutPlantsPalms: Boolean,
        onlyFirsWouldStand: Boolean,
        /** Whether a tree stands at all ([StandingObjects.trees]): at a few per cent a theme can keep none. */
        treesStand: Boolean = c.trees.density > 0f,
    ): DependentSwitchUiState =
        dependentSwitch(
            stored = c.palmsShown(layoutPlantsPalms),
            available = amountSwitch(c.trees.visible, c.trees.density, treesStand).shownOn && !onlyFirsWouldStand,
        )

    /** See [DependentSwitchUiState]: [stored] while [available], off and locked otherwise. */
    fun dependentSwitch(stored: Boolean, available: Boolean): DependentSwitchUiState =
        DependentSwitchUiState(shownOn = stored && available, interactive = available)

    /**
     * *Thunderstorm*, from the theme's own weather: it flashes only while *Show Rain/Snow* is on and Rain is the type,
     * `LiveWeatherSceneRules.stormActive`'s theme path (`SwitchesTellTheTruthTest`). **Not the intensity**: at 0 % it is a
     * storm without rain, and meant (`DESIGN_NOTES.md` §12, decision 44). Under Live Weather the row is not drawn
     * ([forecastOwnsTheWeatherControls]).
     */
    fun thunderstorm(
        storedThunderstorm: Boolean,
        precipitationVisible: Boolean,
        precipitationIsRain: Boolean,
    ): DependentSwitchUiState =
        dependentSwitch(storedThunderstorm, available = precipitationVisible && precipitationIsRain)

    /**
     * Whether the Clouds and the Rain and snow pages leave out the theme's own weather controls (*Show Clouds* and its
     * amount, *Show Rain/Snow*, the type, the intensity, *Thunderstorm*) and say the forecast decides them: they hold the
     * theme's values, and even greyed a switch says on or off -- *Show Clouds* off under the forecast's clouds
     * (`LiveWeatherSceneRules.cloudDensity` gives it no vote). The colours stay: the forecast does not choose them.
     */
    fun forecastOwnsTheWeatherControls(liveWeather: LiveWeatherUiState): Boolean = liveWeather.drivingTheScene

    /**
     * The line under a weather provider's key row in Weather & time's Advanced group: **"Required - not
     * set" only for the provider chosen as the source**, so a user on Open-Meteo, which needs no key,
     * reads no requirement that does not apply, and the Open-Meteo row says it is in use only while it is.
     */
    fun apiKeyLine(row: WeatherProviderId, selected: WeatherProviderId, keySet: Boolean): String {
        val chosen = row == selected
        return when (row) {
            WeatherProviderId.OPEN_METEO -> when {
                keySet && chosen -> "Using your own key"
                keySet -> "Set - used when Open-Meteo is the source"
                chosen -> "Optional - using Open-Meteo's free service, no key needed"
                else -> "Optional - Open-Meteo works without a key"
            }
            WeatherProviderId.WEATHER_API_COM, WeatherProviderId.OPEN_WEATHER -> when {
                keySet && chosen -> "Set"
                keySet -> "Set - used when ${row.displayName} is the source"
                chosen -> "Required - not set"
                else -> "Not set - needed only if you choose ${row.displayName} as the source"
            }
        }
    }

    /**
     * The Location row under GPS or Network: what the phone gives, read each time the page comes to the front, never
     * from the stored choice. The choice is not rewritten (decision 40): GPS stays chosen, and the row says
     * it is not working, why, and where a tap puts it right. Null for Off and Custom, which use nothing of the phone's.
     */
    fun deviceLocationRow(mode: LocationMode, access: DeviceLocationAccess): DeviceLocationRowState? {
        if (mode != LocationMode.GPS && mode != LocationMode.NETWORK) return null
        return when (access) {
            DeviceLocationAccess.ALLOWED ->
                DeviceLocationRowState(true, DeviceLocationLine.WORKING, DeviceLocationTap.NOTHING, usesLastPosition = true)
            DeviceLocationAccess.NOT_ALLOWED ->
                DeviceLocationRowState(false, DeviceLocationLine.NOT_ALLOWED, DeviceLocationTap.ASK_PERMISSION, usesLastPosition = false)
            DeviceLocationAccess.APPROXIMATE_ONLY ->
                DeviceLocationRowState(false, DeviceLocationLine.APPROXIMATE_ONLY, DeviceLocationTap.ASK_PERMISSION, usesLastPosition = false)
            DeviceLocationAccess.LOCATION_OFF ->
                DeviceLocationRowState(false, DeviceLocationLine.LOCATION_OFF, DeviceLocationTap.OPEN_LOCATION_SETTINGS, usesLastPosition = true)
        }
    }

    /**
     * What to do with the answer to a location permission request for [kind]. [systemWouldAskAgain] is
     * `shouldShowRequestPermissionRationale`, which says "no" **both after a refusal for good and after a dialog closed
     * without an answer**, so the app's page opens only where the first is meant: after a refusal already seen here
     * ([refusalSeenHere]), or for a choice already stored ([choiceAlreadyStored], where a refusal for good returns at
     * once, with no dialog). `UpdateNotificationPolicy` reads the same signal the same way.
     */
    fun afterLocationRequest(
        kind: DeviceLocationKind,
        fineGranted: Boolean,
        coarseGranted: Boolean,
        locationOn: Boolean,
        systemWouldAskAgain: Boolean,
        refusalSeenHere: Boolean,
        choiceAlreadyStored: Boolean,
    ): LocationRequestOutcome = when (DeviceLocationAccess.of(kind, fineGranted, coarseGranted, locationOn)) {
        DeviceLocationAccess.ALLOWED -> LocationRequestOutcome.STORE
        DeviceLocationAccess.LOCATION_OFF -> LocationRequestOutcome.STORE_AND_OPEN_LOCATION_SETTINGS
        DeviceLocationAccess.NOT_ALLOWED, DeviceLocationAccess.APPROXIMATE_ONLY ->
            if (!systemWouldAskAgain && (refusalSeenHere || choiceAlreadyStored)) {
                LocationRequestOutcome.OPEN_APP_PAGE
            } else {
                LocationRequestOutcome.KEEP_PREVIOUS
            }
    }

    /** See [DistantHousesUiState]. [mountainsShown]: either mountain layer's switch on. */
    fun distantHouses(visible: Boolean, density: Float, mountainsShown: Boolean): DistantHousesUiState {
        val needsMountains = !mountainsShown
        val noneAtZero = visible && mountainsShown && density <= 0f
        val shownOn = visible && mountainsShown && density > 0f
        return DistantHousesUiState(
            shownOn = shownOn,
            noneAtZero = noneAtZero,
            needsMountains = needsMountains,
            summary = when {
                shownOn -> "On - ${(density * 100).toInt()}%"
                noneAtZero -> "None at 0%"
                visible && needsMountains -> "Off - needs Mountains"
                else -> "Off"
            },
        )
    }

    /**
     * What a tap that moves the *Distant houses* switch to [wanted] does: the Mountains page while there is no mountain
     * to stand on, the amount back to [com.paperscrape.livewallpaper.engine.DistantHousesConfig.STARTING_DENSITY] when
     * turned on at 0 %, otherwise the flag.
     */
    fun distantHousesTap(wanted: Boolean, visible: Boolean, density: Float, mountainsShown: Boolean): DistantHousesTap =
        when {
            !mountainsShown -> DistantHousesTap.OpenMountains(storeOn = wanted && !visible)
            wanted && density <= 0f ->
                DistantHousesTap.Restore(com.paperscrape.livewallpaper.engine.DistantHousesConfig.STARTING_DENSITY)
            else -> DistantHousesTap.SetVisible(wanted)
        }

    /**
     * The line at the top of the Shops and the Public buildings pages: which buildings the group is, that the six take
     * turns in the street's places and that the colours are the group's -- or, with *Show Buildings* off, where that
     * switch is, since they stand by it (decision 40). On a street with no place among the houses
     * ([placesOnTheme], [StandingObjects.themeHasPlaces]) none of the six stands at any setting, and the line says so.
     */
    fun storefrontGroupLine(group: StorefrontGroup, buildingsVisible: Boolean, placesOnTheme: Boolean): String =
        if (!placesOnTheme) {
            noneOnThisThemeLine(groupThings(group))
        } else if (buildingsVisible) {
            "${groupBuildings(group)}. Along the street the six buildings among the houses take turns in its " +
                "places as the scenery scrolls. Each randomly uses Color 1 or Color 2, and blends into " +
                "its night version as it gets dark."
        } else {
            "Off - they stand while Show Buildings is on, on the $TOWERS_PAGE page. Tap here to go to it; " +
                "your choices below are kept."
        }

    /** Who a group of the buildings among the houses is, as its page and its row say it. */
    fun groupBuildings(group: StorefrontGroup): String = when (group) {
        StorefrontGroup.SHOPS -> "The restaurant, the bar and the shop"
        StorefrontGroup.PUBLIC -> "The school, the fire station and the police station"
    }

    /** A group, as a line that says a theme has none of it says it: "shops", "public buildings". */
    fun groupThings(group: StorefrontGroup): String = when (group) {
        StorefrontGroup.SHOPS -> "shops"
        StorefrontGroup.PUBLIC -> "public buildings"
    }

    /** The name of the page *Show Buildings* is on: the towers'. */
    const val TOWERS_PAGE = "Towers & offices"

    /**
     * *Shop opening hours* and *Public building opening hours* (`DESIGN_NOTES.md` §12, decision 62): on only while the
     * group's buildings stand (decision 40). They stand while *Show Buildings* is on, whatever the Towers
     * amount (`SceneCustomization.keepCandidate`), on a street with places among the houses ([placesOnTheme]): so that
     * switch alone holds them, and on a street with none nothing does. The stored choice is kept.
     */
    fun shopHours(stored: Boolean, buildingsVisible: Boolean, placesOnTheme: Boolean): DependentSwitchUiState =
        dependentSwitch(stored, available = placesOnTheme && buildingsVisible)

    /** The public buildings' hours, by [shopHours]' rule. */
    fun publicHours(stored: Boolean, buildingsVisible: Boolean, placesOnTheme: Boolean): DependentSwitchUiState =
        dependentSwitch(stored, available = placesOnTheme && buildingsVisible)

    /**
     * A group's hours as its row in *Landscape* says them: "always open", or "open 09:00-20:00".
     * The same quarter hours the sliders print.
     */
    fun hoursSummary(enabled: Boolean, open: Float, close: Float): String =
        if (!enabled || open == close) "always open" else "open ${clockText(open)}-${clockText(close)}"

    /**
     * The *Towers & offices* row: off, or the amount and the hours -- or, above 0 % with no tower kept on this
     * theme ([towersStand], [StandingObjects.towers]), that none stands; on a street with no tower at all
     * ([towersOnTheme], [StandingObjects.themeHasTowers]), that, the amount and the hours being about nothing; and on one
     * with no building at all -- no tower and no place ([placesOnTheme]) -- that, whatever *Show Buildings* says, since
     * its switch is locked.
     */
    fun towersRowSummary(
        visible: Boolean,
        amount: Float,
        hoursEnabled: Boolean,
        open: Float,
        close: Float,
        towersStand: Boolean,
        towersOnTheme: Boolean,
        placesOnTheme: Boolean,
    ): String = when {
        !towersOnTheme && !placesOnTheme -> noneOnThisThemeSummary("buildings")
        !visible -> "Off"
        !towersOnTheme -> noneOnThisThemeSummary("towers")
        amount > 0f && !towersStand -> "${(amount * 100).toInt()}%, no tower at this density on this theme"
        else -> "${(amount * 100).toInt()}%, ${hoursSummary(hoursEnabled && amount > 0f, open, close)}"
    }

    /**
     * The *Shops* and *Public buildings* rows: the group's buildings and hours, or -- *Show
     * Buildings* off -- that they are off and why; on a street with no place among the houses ([placesOnTheme]), that the
     * theme has none of them.
     */
    fun storefrontRowSummary(
        group: StorefrontGroup,
        buildingsVisible: Boolean,
        hoursEnabled: Boolean,
        open: Float,
        close: Float,
        placesOnTheme: Boolean,
    ): String =
        if (!placesOnTheme) {
            noneOnThisThemeSummary(groupThings(group))
        } else if (!buildingsVisible) {
            "Off - Show Buildings is off"
        } else {
            when (group) {
                StorefrontGroup.SHOPS -> "Restaurant, bar and shop, ${hoursSummary(hoursEnabled, open, close)}"
                // The hours are the school's; the fire and the police station never close.
                StorefrontGroup.PUBLIC -> if (!hoursEnabled || open == close) {
                    "School, fire and police station, always open"
                } else {
                    "School ${hoursSummary(true, open, close)}, fire and police station always open"
                }
            }
        }

    /** A decimal hour as "HH:MM", quantised to the quarter hour the sliders store. */
    private fun clockText(hour: Float): String {
        val q = Math.round(hour.coerceIn(0f, 24f) * 4f) / 4f
        return "%02d:%02d".format(q.toInt() % 24, ((q - q.toInt()) * 60).toInt())
    }

    /**
     * *Tower opening hours*: on only while there are towers to keep them -- *Show Buildings* on and a tower kept
     * at the Towers amount ([towersStand], [StandingObjects.towers]): at a few per cent a theme can keep none, and at
     * 0 % none does. See [shopHours].
     */
    fun towerHours(
        stored: Boolean,
        buildingsVisible: Boolean,
        towersAmount: Float,
        towersStand: Boolean,
    ): DependentSwitchUiState =
        dependentSwitch(stored, available = buildingsVisible && towersAmount > 0f && towersStand)

    /**
     * See [AmountSwitchUiState]. [stands] is whether the category keeps one object at [amount] on this theme
     * ([StandingObjects]) -- for the effects, which keep one at any amount above 0, the amount above 0 -- and [onTheme]
     * whether the theme's street has one at all ([StandingObjects.themeHasTrees] and the three like it); the effects,
     * the mountains and the decorations always do.
     */
    fun amountSwitch(visible: Boolean, amount: Float, stands: Boolean = amount > 0f, onTheme: Boolean = true): AmountSwitchUiState =
        AmountSwitchUiState(
            shownOn = onTheme && visible && amount > 0f && stands,
            noneAtZero = onTheme && amount <= 0f,
            noneAtThisAmount = onTheme && visible && amount > 0f && !stands,
            noneOnThisTheme = !onTheme,
        )

    /**
     * What a tap that moves an [AmountSwitchUiState] switch to [wanted] writes. On a street with none of the category at
     * all ([onTheme] false) nothing: no amount would show one, so the tap promises nothing and the stored flag and amount
     * stay as they are. Turning it on where nothing would stand -- at 0 %, or at an amount that keeps none on this theme
     * ([stands] false) -- restores [defaultAmount], the theme's own, so the thing appears; where the theme's own is no
     * higher than the amount it is at, all of them, 100 %. Anything else is the switch's own flag.
     */
    fun amountTap(
        wanted: Boolean,
        amount: Float,
        defaultAmount: Float,
        stands: Boolean = amount > 0f,
        onTheme: Boolean = true,
    ): AmountTap = when {
        !onTheme -> AmountTap.NoneOnThisTheme
        wanted && amount <= 0f -> AmountTap.Restore(if (defaultAmount > 0f) defaultAmount else FALLBACK_AMOUNT)
        wanted && !stands -> AmountTap.Restore(if (defaultAmount > amount) defaultAmount else 1f)
        else -> AmountTap.SetVisible(wanted)
    }

    /**
     * What [layout] -- the street the theme draws -- would stand at the amounts [c] gives, category by category, each
     * switch on (`keepCandidate`, the wallpaper's rule, [com.paperscrape.livewallpaper.engine.standsAny]), and which of
     * the houses, towers, trees, parasols and places it has at all ([themeHas], [hasPlaces]).
     */
    fun standing(layout: SceneObjectLayout, c: com.paperscrape.livewallpaper.engine.SceneCustomization): StandingObjects {
        val on = c.copy(
            houses = c.houses.copy(visible = true),
            buildings = c.buildings.copy(visible = true),
            trees = c.trees.copy(visible = true),
            parasols = c.parasols.copy(visible = true),
            snowmen = c.snowmen.copy(visible = true),
            gifts = c.gifts.copy(visible = true),
            penguins = c.penguins.copy(visible = true),
            bunnies = c.bunnies.copy(visible = true),
            easterEggs = c.easterEggs.copy(visible = true),
            pumpkins = c.pumpkins.copy(visible = true),
            scarecrows = c.scarecrows.copy(visible = true),
        )
        return StandingObjects(
            houses = on.standsAny(layout, DecorationExclusion.HOUSE_TYPES),
            towers = on.standsAny(layout, setOf(SceneObjectType.SKYSCRAPER)),
            trees = on.standsAny(layout, DecorationExclusion.TREE_TYPES),
            parasols = on.standsAny(layout, setOf(SceneObjectType.PARASOL)),
            snowmen = on.standsAny(layout, setOf(SceneObjectType.SNOWMAN)),
            gifts = on.standsAny(layout, setOf(SceneObjectType.GIFT)),
            penguins = on.standsAny(layout, setOf(SceneObjectType.PENGUIN)),
            bunnies = on.standsAny(layout, setOf(SceneObjectType.BUNNY)),
            easterEggs = on.standsAny(layout, setOf(SceneObjectType.EASTER_EGG)),
            pumpkins = on.standsAny(layout, setOf(SceneObjectType.PUMPKIN)),
            scarecrows = on.standsAny(layout, setOf(SceneObjectType.SCARECROW)),
            themeHasHouses = themeHas(layout, c, DecorationExclusion.HOUSE_TYPES),
            themeHasTowers = themeHas(layout, c, setOf(SceneObjectType.SKYSCRAPER)),
            themeHasTrees = themeHas(layout, c, DecorationExclusion.TREE_TYPES),
            themeHasParasols = themeHas(layout, c, setOf(SceneObjectType.PARASOL)),
            themeHasPlaces = hasPlaces(layout),
            onlyFirsEvenAtFull = layout.keepsOnlyFirsUnderPalms(c.copy(trees = c.trees.copy(visible = true, density = 1f))),
        )
    }

    /**
     * Whether [layout] has one of [types] at all: one stands with the houses, the buildings, the trees and the parasols on
     * and at 100 % -- `keepCandidate` itself, so what no amount can change. Not the places among the houses, which
     * [com.paperscrape.livewallpaper.engine.standsAny] leaves out: for the buildings this asks after the towers.
     */
    private fun themeHas(
        layout: SceneObjectLayout,
        c: com.paperscrape.livewallpaper.engine.SceneCustomization,
        types: Set<SceneObjectType>,
    ): Boolean = c.copy(
        houses = c.houses.copy(visible = true, density = 1f),
        buildings = c.buildings.copy(visible = true, density = 1f),
        trees = c.trees.copy(visible = true, density = 1f),
        parasols = c.parasols.copy(visible = true, density = 1f),
    ).standsAny(layout, types)

    /**
     * Whether [layout] has a place among the houses ([StorefrontStreet.isPlace]), where the six buildings take turns --
     * the three shops among them. The places stand at every amount while *Show Buildings* is on (`keepCandidate`), so
     * this is whether a shop can stand at all.
     */
    private fun hasPlaces(layout: SceneObjectLayout): Boolean = layout.staticObjects.any { StorefrontStreet.isPlace(it) }

    /** The line under a *Show X* switch of the street at an amount that keeps none on this theme ([amountSwitch]). */
    fun noneAtThisAmountLine(amount: Float): String =
        "None at ${(amount * 100).toInt()}% density on this theme - tap to bring them back"

    /**
     * *Show people*: the people walk by their day density by day and their night density at night
     * (`PeopleDensity.at`), so with both at 0 % nobody walks at any hour -- off then, and said, the stored values kept; a
     * tap brings the day density back to the theme's own. With either above 0 % some hour has people: on.
     */
    fun people(visible: Boolean, dayDensity: Float, nightDensity: Float): AmountSwitchUiState =
        AmountSwitchUiState(
            shownOn = visible && (dayDensity > 0f || nightDensity > 0f),
            noneAtZero = dayDensity <= 0f && nightDensity <= 0f,
        )

    /**
     * What a tap on *Show people* writes: turning it on with both densities at 0 % brings the day density back to
     * [defaultDayDensity], the theme's own, so people walk; anything else is the switch's own flag.
     */
    fun peopleTap(wanted: Boolean, dayDensity: Float, nightDensity: Float, defaultDayDensity: Float): AmountTap =
        if (wanted && dayDensity <= 0f && nightDensity <= 0f) {
            AmountTap.Restore(if (defaultDayDensity > 0f) defaultDayDensity else FALLBACK_AMOUNT)
        } else {
            AmountTap.SetVisible(wanted)
        }

    /** The line under *Show people* with both densities at 0 % ([people]). */
    const val NO_PEOPLE_AT_ZERO_LINE = "None at 0% by day and by night - tap to bring them back"

    /**
     * The amount a tap restores where the theme's own is 0 as well: the middle of the slider. No
     * built-in theme ships one of these controls at 0, so it is a backstop.
     */
    const val FALLBACK_AMOUNT = 0.5f

    /**
     * The first half of the home screen's *Weather & time* line: what the Live Weather switch shows,
     * in words -- "on" only when the switch is, and while the stored "on" is held, why.
     */
    fun homeLiveWeatherLine(state: LiveWeatherUiState): String = when {
        state.shownOn -> "Live Weather on"
        !state.configuredOn -> "Live Weather off"
        else -> "Live Weather off: " + when (state.blocker) {
            LiveWeatherBlocker.FIXED_HOUR -> "the scene is at a fixed hour"
            LiveWeatherBlocker.NO_LOCATION -> "no location chosen"
            LiveWeatherBlocker.MISSING_KEY -> "needs an API key"
            LiveWeatherBlocker.NOT_THE_WALLPAPER -> "PaperScrape is not your wallpaper"
            LiveWeatherBlocker.REJECTED_KEY -> "the API key was not accepted"
            LiveWeatherBlocker.LOCATION_UNAVAILABLE -> "no position from the phone yet"
            LiveWeatherBlocker.LOCATION_NOT_ALLOWED -> "the phone does not allow its location"
            LiveWeatherBlocker.NONE -> "starting" // not reached: shownOn above
        }
    }

    /**
     * How many decorations the home screen's *Seasons & decorations* line counts as on: each the way its own switch
     * shows it, by the functions that draw the switches -- so the Christmas lights and the Halloween decorations count
     * only with something to hang them on, and one with a density only while one stands
     * ([amountSwitch]). **Not the palms**: their switch is on World & scene's Trees page, not on the screen counted.
     */
    fun decorationsOn(
        c: com.paperscrape.livewallpaper.engine.SceneCustomization,
        /** The street the theme draws, and what it stands under [c] ([standing]); without them, the amounts above 0 %. */
        layout: SceneObjectLayout? = null,
    ): Int {
        val s = layout?.let { standing(it, c) }
        return listOf(
            christmasBuildingDecoration(c.christmasBuildingLightsEnabled, c, layout).shownOn,
            christmasTreeDecoration(c.christmasTreeLightsEnabled, c, layout).shownOn,
            christmasFirs(c.christmasFirsEnabled, c, layout).shownOn,
            c.santaEnabled,
            c.halloweenEnabled,
            laughingMoon(c).shownOn,
            nightSkyByDay(c).shownOn,
            stillMoon(c).shownOn,
            halloweenDecorations(c, layout).shownOn,
            c.bloodRainEnabled,
            c.headlessHorsemanEnabled,
            c.horrorSkyEnabled,
            c.flowersEnabled,
            amountSwitch(c.scarecrows.visible, c.scarecrows.density, s?.scarecrows ?: (c.scarecrows.density > 0f)).shownOn,
            amountSwitch(c.snowmen.visible, c.snowmen.density, s?.snowmen ?: (c.snowmen.density > 0f)).shownOn,
            amountSwitch(c.gifts.visible, c.gifts.density, s?.gifts ?: (c.gifts.density > 0f)).shownOn,
            amountSwitch(c.penguins.visible, c.penguins.density, s?.penguins ?: (c.penguins.density > 0f)).shownOn,
            amountSwitch(c.bunnies.visible, c.bunnies.density, s?.bunnies ?: (c.bunnies.density > 0f)).shownOn,
            amountSwitch(c.easterEggs.visible, c.easterEggs.density, s?.easterEggs ?: (c.easterEggs.density > 0f)).shownOn,
            amountSwitch(c.pumpkins.visible, c.pumpkins.density, s?.pumpkins ?: (c.pumpkins.density > 0f)).shownOn,
        ).count { it }
    }

    /**
     * Whether choosing [pickedThemeId] -- a gallery card, or *Shuffle a random theme* (null: always a different one) --
     * must ask first: while the calendar shows [calendarThemeId] today, a different pick is saved and **not shown**,
     * because the calendar wins (`PaperWallpaperService` and the gallery resolve the same way).
     */
    fun pickNeedsCalendarQuestion(calendarThemeId: String?, pickedThemeId: String?): Boolean =
        calendarThemeId != null && pickedThemeId != calendarThemeId

    /**
     * How long from [now] until the next day begins where [now] is: when the settings read the date again (the calendar's
     * theme of today). From the start of the next day in its own zone, so a day of 23 or 25 hours -- the change of the
     * clocks -- ends at its midnight; never less than a millisecond.
     */
    fun millisUntilNextDay(now: java.time.ZonedDateTime): Long =
        java.time.Duration.between(now, now.toLocalDate().plusDays(1).atStartOfDay(now.zone)).toMillis().coerceAtLeast(1L)

    /**
     * The line under *Shuffle a random theme*: "active" only while a random theme is what shows -- picked, and no
     * calendar's theme showing over it today ([calendarThemeId]). With *Automatic theme by date* on and a day the
     * calendar covers, a random theme picked earlier is kept but not shown.
     */
    fun shuffleLine(themeId: String, calendarThemeId: String?): String =
        if (com.paperscrape.livewallpaper.engine.RandomSceneGenerator.isRandomThemeId(themeId) && calendarThemeId == null) {
            "Random theme active - tap again to generate another one"
        } else {
            "Builds a new theme from scratch and selects it"
        }

    /**
     * The first line of the Holiday calendar page with *Automatic theme by date* on: "each day" only while the seasons
     * cover the whole year; on a gap's days the theme picked by hand shows.
     */
    fun calendarBanner(hasGaps: Boolean): String =
        if (hasGaps) {
            "The calendar picks a theme for each day a holiday or a season covers. Holidays sit on top of the " +
                "seasons; the days no season covers are named below."
        } else {
            "The calendar picks a theme for each day. Holidays sit on top; the seasons underneath cover the rest " +
                "of the year."
        }

    /** Reads the pair of stored palette flags as one choice. */
    fun seasonalPalette(fallColorsEnabled: Boolean, winterColorsEnabled: Boolean): SeasonalPalette = when {
        winterColorsEnabled -> SeasonalPalette.WINTER
        fallColorsEnabled -> SeasonalPalette.AUTUMN
        else -> SeasonalPalette.NONE
    }

    /** The flag pair a given palette means: `(fallColorsEnabled, winterColorsEnabled)`. */
    fun seasonalPaletteFlags(palette: SeasonalPalette): Pair<Boolean, Boolean> = when (palette) {
        SeasonalPalette.NONE -> false to false
        SeasonalPalette.AUTUMN -> true to false
        SeasonalPalette.WINTER -> false to true
    }

    /**
     * The banner at the top of World & scene and Seasons & decorations (`DESIGN_NOTES.md` §12, decision 45): an edit
     * stays with its theme (`WallpaperPrefs.ensureFreshPendingTheme`), and saving makes a separate copy.
     */
    fun themeEditsBanner(themeName: String): String =
        "These change $themeName, the theme showing now, and your changes stay with it when you switch " +
            "themes. To keep this look as a theme of its own, save it from Advanced & about."

    /**
     * The question before "Reset this theme's scene to defaults" (`DESIGN_NOTES.md` §12, decision 42): what goes, and
     * that **Motion**, every theme's, stays (`WallpaperPrefs.resetAllCategories` removes only this theme's keys).
     */
    fun sceneResetMessage(themeName: String): String =
        "This puts the sky, the landscape, the life and the traffic on this screen back to how $themeName " +
            "ships - and its Seasons & decorations too: every winter, Christmas, Halloween, Easter and " +
            "spring decoration, and the autumn and winter palettes. Motion stays as it is: it is shared by " +
            "every theme. Your other themes are not affected."

    /**
     * The question before "Reset decorations to defaults" (decision 42): what `WallpaperPrefs.resetDecorations`
     * removes. Not the palms: "Reset Trees to default" takes them.
     */
    fun decorationsResetMessage(themeName: String): String =
        "This puts everything on this screen back to how $themeName ships: the seasonal palette and its " +
            "snow or leaf piles, and every winter, Christmas, Halloween, Easter and spring decoration, " +
            "with its density and colours. The rest of the scene and your other themes are not affected."

    /**
     * The gallery's "Reset to default" for a built-in theme with a saved version (decision 42): it takes the saved
     * version and the theme's edits, which win over it (`CustomThemeRegistry.resolveActiveCustomization`).
     */
    fun galleryResetMessage(themeName: String): String =
        "This removes your saved version of \"$themeName\" and the changes made to it in World & scene and " +
            "Seasons & decorations, so it looks as it ships. Your other themes are not affected."

    /**
     * The question before "Replace with current" on a gallery card (decision 42), which copies the theme **showing
     * now** onto the card's: [sameTheme] by id (a theme of your own may share a built-in's name); [targetIsBuiltIn]
     * says whether "Reset to default" can undo it. Returns the title and the text.
     */
    fun replaceWithCurrentQuestion(
        targetName: String,
        showingName: String,
        sameTheme: Boolean,
        targetIsBuiltIn: Boolean,
    ): Pair<String, String> {
        val title = if (sameTheme) {
            "Keep $targetName as it looks now?"
        } else {
            "Replace $targetName with $showingName?"
        }
        val what = if (sameTheme) {
            "\"$targetName\" is saved exactly as it looks now, with your changes."
        } else {
            "\"$targetName\" will look like $showingName as it looks now - its colours, its scene and your " +
                "changes - and keep its own name."
        }
        val undo = if (targetIsBuiltIn) {
            " Reset to default, in the same menu, brings back the original."
        } else {
            " What \"$targetName\" looked like before cannot be brought back."
        }
        return title to what + undo
    }

    /**
     * Each bird colour's real share of the flock, in whole per cent, for the line beside its slider (decision 45): the
     * sliders are weights. What [com.paperscrape.livewallpaper.engine.BirdsConfig.pickColor] draws: each weight over
     * their sum, all the first colour when every weight is 0; largest-remainder rounding, so the four add up to 100.
     */
    fun birdColorShares(weights: List<Float>): List<Int> {
        if (weights.isEmpty()) return emptyList()
        val clean = weights.map { if (it.isFinite() && it > 0f) it.toDouble() else 0.0 }
        val total = clean.sum()
        if (total <= 0.0) return List(weights.size) { if (it == 0) 100 else 0 }
        val exact = clean.map { it * 100.0 / total }
        val floors = exact.map { kotlin.math.floor(it).toInt() }.toMutableList()
        var left = 100 - floors.sum()
        val order = exact.indices.sortedByDescending { exact[it] - floors[it] }
        for (i in order) {
            if (left <= 0) break
            floors[i] += 1
            left -= 1
        }
        return floors
    }

    /**
     * *Swipe scroll* (decision 40): on only once the home screen has moved the wallpaper with a swipe
     * ([swipeReported], written by the wallpaper the first time it does). Many home screens never send swipes -- the
     * BV6600's never calls `onOffsetsChanged` -- so until one arrives it is off and locked, with the reason, the stored
     * choice kept. Nothing here can make a home screen send swipes, so the tap has nowhere to go.
     */
    fun swipeScroll(stored: Boolean, swipeReported: Boolean): DependentSwitchUiState =
        dependentSwitch(stored, available = swipeReported)

    /**
     * Whether anything scrolls the scene -- the drift at [scrollSpeed], or a reported swipe with *Swipe scroll* on --
     * which *Parallax strength* and *Scroll the background too* act on: without it they are locked (decision 45).
     */
    fun sceneScrolls(scrollSpeed: Float, swipeScroll: DependentSwitchUiState): Boolean =
        scrollSpeed > 0f || swipeScroll.shownOn

    /**
     * Whether an amount slider accepts a touch (decision 45): **only while its own switch is on, as stored** -- not "at
     * 0 %", where the switch reads off because of the slider ([amountSwitch]) and the slider brings the amount back.
     */
    fun amountSliderEnabled(storedVisible: Boolean): Boolean = storedVisible

    /**
     * How the sailboat and dolphin rows are drawn ([LakeContentsUiState]); [lakeVisible] is the lake as its switch
     * shows it: on, and above 0 % height ([com.paperscrape.livewallpaper.engine.LakeConfig.drawsWater]).
     */
    fun lakeContents(
        lakeVisible: Boolean,
        storedSailboatsVisible: Boolean,
        storedDolphinsVisible: Boolean,
    ): LakeContentsUiState = LakeContentsUiState(
        sailboatsShownOn = storedSailboatsVisible && lakeVisible,
        dolphinsShownOn = storedDolphinsVisible && lakeVisible,
        interactive = lakeVisible,
        blockedByLakeOff = !lakeVisible,
    )
}
