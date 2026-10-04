package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.coverage
import com.paperscrape.livewallpaper.engine.palmsShown
import com.paperscrape.livewallpaper.location.DeviceLocationAccess
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import com.paperscrape.livewallpaper.weather.WeatherProviderId

/**
 * Which location source is in use, as one choice instead of two switches.
 *
 * **Presentation only.** Nothing is stored under this name. The wallpaper has always had two
 * independent, mutually exclusive boolean preferences -- `useLocationForSunTimes` and
 * `useCustomLocation` -- and `WallpaperPrefs.setUseLocation` / `setUseCustomLocation` are what
 * enforce that only one of them is ever true. Three booleans-worth of states were expressed as
 * two switches whose subtitles each had to explain the other; this enum names the three states
 * the pair can actually be in, with the device state split by the stored positioning kind into
 * GPS and NETWORK, and [SettingsUiModel.locationFlags] maps back to exactly the same pair of
 * writes the two switches performed.
 */
enum class LocationMode { OFF, GPS, NETWORK, CUSTOM }

/**
 * Which seasonal palette the current theme is wearing, as one choice instead of two switches.
 *
 * **Presentation only**, for the same reason as [LocationMode]: `fallColorsEnabled` and
 * `winterColorsEnabled` are two readings of the same leaves and cannot both be true (see
 * `WallpaperPrefs.setFallColorsEnabled` / `setWinterColorsEnabled`, each of which clears the
 * other). Christmas lights are deliberately *not* part of this choice -- they hang on top of
 * whatever the trees look like and stay an independent switch.
 */
enum class SeasonalPalette { NONE, AUTUMN, WINTER }


/**
 * Why Live Weather cannot drive the scene right now, though the user may have turned it on -- the
 * first thing to put right, in the order a user can put them right (v5.10C).
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

    /**
     * A location source is chosen but the phone has given no position (`LiveWeatherStatus.NO_LOCATION`
     * with GPS or Network chosen). A tap brings the Location choice into view.
     */
    LOCATION_UNAVAILABLE,

    /**
     * GPS or Network is chosen and the phone does not let PaperScrape use a position from it
     * (`DeviceLocationAccess.mayUsePosition`, read from the phone each time the screen comes back,
     * v5.10D): no permission, or only the approximate one for GPS. A tap brings the Location choice into
     * view, where the row says which and leads to the fix.
     */
    LOCATION_NOT_ALLOWED,
}

/**
 * The line under the GPS / Network choice that says whether the phone gives the position (v5.10D, the
 * maintainer's decision of 2026-09-30 on row 5 of the v5.10A table, inventory I-206).
 */
enum class DeviceLocationLine {
    /** The phone gives it: the row shows the position, as before v5.10D. */
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
     * The permission dialog for the chosen kind (`DeviceLocationAccess.permissionsToRequest`); where the
     * system will not show it any more, PaperScrape's page in the phone's settings
     * ([SettingsUiModel.afterLocationRequest]).
     */
    ASK_PERMISSION,

    /** The phone's location settings page. */
    OPEN_LOCATION_SETTINGS,
}

/**
 * The Location row under GPS or Network: [working] only while the phone gives the position; otherwise
 * the [line] says what is missing and the [tap] goes where it is put right. [usesLastPosition] says
 * whether the scene meanwhile keeps the last position the phone gave (location switched off) or uses
 * none (no permission).
 */
data class DeviceLocationRowState(
    val working: Boolean,
    val line: DeviceLocationLine,
    val tap: DeviceLocationTap,
    val usesLastPosition: Boolean,
)

/** What the settings screen does with the answer to a location permission request (v5.10D). */
enum class LocationRequestOutcome {
    /** Granted: the choice is stored, as it always was. */
    STORE,

    /** Granted, and the phone's location is off: the choice is stored and the location page opened. */
    STORE_AND_OPEN_LOCATION_SETTINGS,

    /** Refused, and the system would ask again: the previous choice stays, as it always did. */
    KEEP_PREVIOUS,

    /**
     * Refused for good -- the system no longer shows the dialog -- after a refusal already seen on this
     * screen, or for a choice already stored: PaperScrape's page in the phone's settings opens, where
     * the permission is given. Until v5.10D such a tap did nothing at all. **Nothing is written**: a
     * choice already stored stays, and a new one is made once the permission is there, so a working
     * Custom or Off is never replaced by a GPS that cannot work (the read-only review of v5.10D).
     */
    OPEN_APP_PAGE,
}

/** What a tap on the Live Weather switch does (v5.10C): whatever is missing, it goes there. */
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
 * The separate things "Live Weather" used to mean at once.
 *
 * v3.0 had one boolean doing all of these jobs, and the jobs disagreed. `Weather & time` disabled the
 * switch whenever a live fetch was impossible, `World & scene` locked clouds and precipitation
 * whenever the switch was *on*, and neither asked what the scene was actually drawing. The
 * reachable, persistent result was a switch stuck on, greyed out, that the user was told to turn
 * off -- with the weather controls locked behind it and a banner on the other screen already
 * admitting the forecast was not in effect.
 *
 * **v5.10C, the maintainer's decision of 2026-09-30 (row 4):** the switch is drawn **on only while
 * real weather can drive the scene** -- [shownOn] -- and, when it cannot, off, with the reason under
 * it ([blocker]) and a tap that goes where the reason is put right. Until then it showed the stored
 * flag, so a fixed hour, no location, no key or another wallpaper left it reading on over a scene
 * that ran on the theme's own weather (inventory I-204). It always accepts a tap: there is no state
 * left in which a tap does nothing, so the old dead end (the switch stuck on and greyed) cannot come
 * back, and the stored choice is never written to make the switch look right.
 *
 * @property configuredOn what the user asked for -- the stored `liveWeatherEnabled` flag.
 * @property canBeTurnedOn whether the two prerequisites of a fetch the engine checks first are in
 *   place: the scene follows real time and a location is chosen (`LiveWeatherSchedule.runs`, and the
 *   loop's `fix`). It is what the status banner's "waiting" and "not running" are about.
 * @property blocker the first reason real weather cannot drive the scene, or
 *   [LiveWeatherBlocker.NONE].
 * @property shownOn what the switch is drawn as: [configuredOn] with no [blocker].
 * @property drivingTheScene whether real conditions are what the scene is drawing. This -- not
 *   [configuredOn] -- is what makes the theme's own cloud and precipitation controls read-only,
 *   and what the "Driven by Live Weather" label is allowed to claim. **Only while [shownOn]**
 *   (v5.10C): with the switch drawn off, whatever the engine last published is about a Live Weather
 *   that cannot run -- another wallpaper, where no engine draws, or a fixed hour set while the
 *   settings screen covered the wallpaper, so the engine has not been on screen to stop -- and a
 *   stale "driving" would lock World & scene's clouds behind a switch that reads off and whose tap
 *   goes elsewhere: a way out that no longer exists. `SettingsUiModelTest` keeps that unreachable.
 */
data class LiveWeatherUiState(
    val configuredOn: Boolean,
    val canBeTurnedOn: Boolean,
    val blocker: LiveWeatherBlocker,
    val shownOn: Boolean,
    val drivingTheScene: Boolean,
)

/**
 * What the "Realistic Moon Phases" control shows, and whether it accepts a tap at all.
 *
 * **The renderer already decides this; the control was the only part that had not been told.**
 * `PaperRenderer.drawMoonWithPhase` draws `moon_jack_o_lantern`, always full, and returns before
 * it ever reads `moon.realisticPhases` while `halloweenEnabled` is on -- because a carved face
 * that waxed and waned would be a lit fraction of a grin, which reads as a rendering fault rather
 * than as a decoration. The switch stayed live and did nothing, and a control that moves without
 * effect teaches the user that the setting is broken.
 *
 * **[shownOn] is not what is stored, and nothing writes the difference back.** While Halloween is
 * on the control shows off and locked; the user's own value is untouched in the DataStore and is
 * what [shownOn] returns again the moment Halloween goes off. Writing `false` into the preference
 * to make the switch look right would destroy a setting the user chose, which is exactly what
 * `PeopleDensity.resolveNightDensity`'s own derivation forbids: a settings change is not entitled
 * to silently alter what an existing user had set up.
 *
 * **And with the moon itself hidden** (v5.10C, row 7 of the maintainer's table of 2026-09-30):
 * `PaperRenderer.drawCelestialBody` returns before the moon is drawn at all while *Show Moon* is off, so
 * its phases were a switch with nothing under it. Same rule, same refusal to write: off and locked,
 * the stored value back the moment the moon is.
 *
 * @property shownOn what the switch is drawn as. The stored value, or `false` while overridden.
 * @property interactive whether the switch accepts a tap.
 * @property overriddenByHalloween whether the override is in force, and therefore whether the row
 *   owes the user the sentence that says why.
 * @property needsMoon whether the hidden moon is what holds it (Halloween, when both, is said first).
 */
data class MoonPhasesUiState(
    val shownOn: Boolean,
    val interactive: Boolean,
    val overriddenByHalloween: Boolean,
    val needsMoon: Boolean = false,
)

/**
 * A switch that only does something while another control is on -- *Night Birds* under *Show Birds*,
 * *Thunderstorm* under rain (v5.10C, row 7).
 *
 * The [MoonPhasesUiState] rule, generalised: off and locked while what it depends on is off, and
 * **the stored value is not touched**, so it is back the moment that thing is.
 *
 * @property shownOn the stored value while [available], off otherwise.
 * @property interactive [available].
 */
data class DependentSwitchUiState(
    val shownOn: Boolean,
    val interactive: Boolean,
)

/**
 * A *Show X* switch with an amount slider beside it -- density, intensity, opacity, number -- for a
 * thing the wallpaper draws none of at 0 (v5.10C, row 8 of the maintainer's table).
 *
 * At 0 % the trees, houses, parasols, the six seasonal decorations, the stars, birds, clouds, rain and
 * snow, the rainbow, the sailboats and the dolphins are all absent from the scene, and until v5.10C
 * their switch went on reading on, and the home screen and the Seasons screen counted them among the
 * "decorations on" (inventory I-211). Now the switch reads off and says so, and the counts follow the
 * switch. The stored visibility is kept; raising the slider brings it back, and so does a tap
 * ([amountTap]).
 *
 * Not for the categories that keep something at 0: the buildings (the three shops), the cars (one
 * car, `CarSelection`), the people (their night density is a slider of its own) and the mountains
 * (at least one).
 *
 * @property shownOn visible and the amount above 0.
 * @property noneAtZero the amount is 0, and that -- not the switch -- is why nothing is drawn.
 */
data class AmountSwitchUiState(
    val shownOn: Boolean,
    val noneAtZero: Boolean,
)

/** What a tap on an [AmountSwitchUiState] switch writes. */
sealed interface AmountTap {
    /** The ordinary case: the switch's own flag. */
    data class SetVisible(val visible: Boolean) : AmountTap

    /**
     * On, at 0 %: the flag on and the amount back to [amount] -- the theme's own, or
     * [SettingsUiModel.FALLBACK_AMOUNT] where that is 0 too -- because "on" at 0 % is a tap that
     * changes nothing, which is the defect this row repairs.
     */
    data class Restore(val amount: Float) : AmountTap
}

/**
 * How the four boat-and-dolphin controls on *Lake, boats and dolphins* must be drawn, given
 * whether the lake itself is on.
 *
 * **The renderer already decided this; the controls were the only part that had not been told.**
 * `PaperRenderer.updateLakeBandY` returns early while `lake.visible` is false (since v5.10E while
 * `LakeConfig.drawsWater` is false: off, or at 0 % height), so `drawLake`
 * never reaches the sailboats or the dolphins: with the lake off there is no water and nothing
 * floats on it. The two switches nevertheless read **on** and both density sliders stayed fully
 * live -- four controls that move without effect, on the one screen where the default themes make
 * it easy to meet (Sunset ships with the lake off).
 *
 * The same rule as [MoonPhasesUiState], for the same reason and with the same refusal:
 * **[sailboatsShownOn] and [dolphinsShownOn] are not what is stored, and nothing writes the
 * difference back.** While the lake is off the switches show off and locked; the user's own
 * values sit untouched in the DataStore and come back the moment the lake goes on. Writing
 * `false` into the preferences to make the switches look right would destroy a choice the user
 * made -- which is exactly the bug this shape exists to avoid.
 *
 * @property sailboatsShownOn what the sailboat switch is drawn as. The stored value, or `false`.
 * @property dolphinsShownOn what the dolphin switch is drawn as. The stored value, or `false`.
 * @property interactive whether the four controls accept a touch.
 * @property blockedByLakeOff whether the lake is what is holding them, and therefore whether the
 *   rows owe the user the sentence that says why.
 */
data class LakeContentsUiState(
    val sailboatsShownOn: Boolean,
    val dolphinsShownOn: Boolean,
    val interactive: Boolean,
    val blockedByLakeOff: Boolean,
)

/**
 * The translation layer between the settings UI's grouped choices and the preference flags that
 * have always backed them.
 *
 * Deliberately free of Compose and Android imports so the mapping is unit-testable on the JVM.
 * The read direction (`locationMode`, `seasonalPalette`) is what the screens use, and it is pinned
 * by `SettingsUiModelTest`; the write direction (`locationFlags`, `deviceKindFor`,
 * `seasonalPaletteFlags`) is pinned there too, but no screen calls it -- `WeatherTimeScreen` and
 * `SeasonsScreen` write the preferences directly.
 */
/**
 * What the two reset rows of *Advanced & about* say and whether each can be pressed (v5.8C).
 *
 * **Two kinds of thing a user does to a built-in theme, and until v5.8C one button that counted
 * only one of them.** A *saved version* is a copy made with "Replace with current" in the gallery
 * (`CustomThemeData.overrides`); *current edits* are the changes made in World & scene and
 * Seasons & decorations, which the app keeps per theme (`WallpaperSettings.themeCustomizations`)
 * and which win over a saved version. The one button reset saved versions, and its line counted
 * only them: a user who had edited two themes from the menus read "No built-in theme has your
 * edits" beside a disabled button (assessment v5.7 row 4). The maintainer decided on 2026-09-25:
 * two buttons, each saying what it resets, each with its own line and its own enabled state.
 *
 * Both lists are display names of built-in themes, in the gallery's order, so the line and the
 * confirmation can name exactly what goes.
 */
data class ThemeResetUiState(
    /** Built-in themes with a saved version ("Replace with current"), by display name. */
    val savedVersions: List<String>,
    /** Built-in themes with edits made in these settings that differ from what they would show without them. */
    val currentEdits: List<String>,
    /** The ids of [currentEdits], which is what the reset is handed. */
    val currentEditIds: List<String> = emptyList(),
)

object SettingsUiModel {

    /**
     * Which built-in themes each reset would change.
     *
     * A theme counts as edited only if its current customization differs from what it would show
     * with the edits gone -- its saved version's customization if it has one, otherwise its
     * defaults -- so a control moved and moved back does not light the button, and a theme whose
     * only change is a saved version is listed once, under saved versions. [themeCustomizations]
     * is `WallpaperSettings.themeCustomizations`, which already folds in the theme under live edit.
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
     * The line under *Automatic theme by date*, on the home screen ([overridingYourPick]) and in the
     * gallery, and the gallery's caption under it ([autoThemeCaption]).
     *
     * **A calendar may have a gap** -- a stretch no season covers, which the maintainer allowed in
     * v5.1 and the calendar screen names with its dates -- and on those days the wallpaper shows the
     * theme picked by hand, unless a holiday covers them (`SeasonalThemeRules.themeForDate` answers
     * null). Until v5.9F both lines were fixed text saying the calendar picks a theme "for every day
     * of the year", which a gap makes false (inventory I-06). The count is the calendar row's own,
     * the days no season covers (`SeasonalCalendar.coverage`).
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
     * Reads the stored flags and the stored positioning kind as one mode.
     *
     * The device flag is checked first only as a tie-break for a state the preferences layer does
     * not produce (both true); it cannot arise through either setter.
     *
     * An install from before v3.0 has no stored kind, and `WallpaperSettings` defaults it to
     * `NETWORK` -- which is what the single old "Phone" mode already used in practice, so those
     * users find the control on Network rather than on a mode they never chose.
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
     * The flag pair a given mode means: `(useDeviceLocation, useCustomLocation)`.
     *
     * Both device modes set the same device flag -- which of the two systems it means is
     * [deviceKindFor], stored alongside rather than folded into this pair. `OFF` clears both,
     * which is the state a fresh install starts in.
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
     * Reads the stored Live Weather flag, its prerequisites, the wallpaper and the status the
     * wallpaper service published as the distinct answers the UI needs. See [LiveWeatherUiState].
     *
     * @param followRealTime `syncWithRealTime`. A fixed-hour scene has no "now" to fetch for.
     * @param devicePositionUsable for GPS and Network, whether the phone lets PaperScrape use a position
     *   (`DeviceLocationAccess.mayUsePosition`, read from the phone each time the screen comes back,
     *   v5.10D); ignored for Custom and Off.
     * @param keyMissing the selected provider needs a key and none is set
     *   (`WeatherProvider.requiresApiKey`), worked out from the settings rather than from the
     *   status, which only an engine on screen updates.
     * @param isTheWallpaper whether PaperScrape is the phone's wallpaper (`WallpaperEngineCensus`).
     * @param status what the engine last reported it was doing. [LiveWeatherStatus.OFF] here means
     *   "the engine has not said yet" as well as "it is off", which is why the scene is treated as
     *   theme-driven until it says otherwise -- the honest answer while nothing is known. Only two of
     *   its answers can hold the switch off, a refused key and a phone that gives no position: they
     *   are facts the screen cannot work out on its own. The engine forgets them when the inputs they
     *   were about change (`PaperWallpaperService`, v5.10C), so a key typed in a moment ago is not
     *   read as refused. A network that did not answer ([LiveWeatherStatus.FAILED]) leaves it on: the
     *   app retries by itself and there is nothing for the user to put right.
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
            // engine has next been on screen and reported no position (v5.10D, row 5).
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

    /**
     * How the "Realistic Moon Phases" row must be drawn, given the stored value, Halloween and the
     * moon itself.
     *
     * Deliberately a pure function of the flags rather than an expression inlined in the
     * composable: this is the whole of the rule, it is the thing that must not start writing to
     * the preference, and here it is readable and testable on the JVM. See [MoonPhasesUiState].
     */
    fun moonPhases(storedRealisticPhases: Boolean, halloweenEnabled: Boolean, moonVisible: Boolean = true): MoonPhasesUiState =
        MoonPhasesUiState(
            shownOn = storedRealisticPhases && !halloweenEnabled && moonVisible,
            interactive = !halloweenEnabled && moonVisible,
            overriddenByHalloween = halloweenEnabled,
            needsMoon = !moonVisible && !halloweenEnabled,
        )

    /**
     * What a tap on the Live Weather switch does. Every state leads somewhere, so the switch always
     * accepts a tap; the stored flag is turned on with the step that puts right what holds it, so the
     * choice is made once and shows the moment nothing is missing any more.
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
     * The Palms switch, as the Trees page of World & scene draws it (Seasons & decorations until
     * v5.10C2): the half of the switch the theme reads ([palmsShown], v5.10C), and **only while the
     * trees stand** -- the palms take the trees' places, so
     * with *Show Trees* off, or the trees at 0 %, there is nowhere for one to stand
     * (`palmSpeciesApplied` swaps only the slots `keepCandidate` kept). Off and locked then, the stored
     * value kept, the rule of [dependentSwitch]. Beach and Desert read on over no palm the same way
     * until v5.10C; the other ten could not, until their palms could be switched on.
     *
     * **And only while a palm would stand** (v5.10C2): the Christmas firs stay firs among the palms, so
     * a wood thinned under the Christmas layer can keep nothing but firs ([onlyFirsWouldStand], which is
     * `SceneObjectLayout.keepsOnlyFirsUnderPalms` of the theme's layout). Off and locked then too, the
     * same rule: the switch would put no palm anywhere.
     */
    fun palms(
        c: com.paperscrape.livewallpaper.engine.SceneCustomization,
        layoutPlantsPalms: Boolean,
        onlyFirsWouldStand: Boolean,
    ): DependentSwitchUiState =
        dependentSwitch(
            stored = c.palmsShown(layoutPlantsPalms),
            available = amountSwitch(c.trees.visible, c.trees.density).shownOn && !onlyFirsWouldStand,
        )

    /** See [DependentSwitchUiState]: [stored] while [available], off and locked otherwise. */
    fun dependentSwitch(stored: Boolean, available: Boolean): DependentSwitchUiState =
        DependentSwitchUiState(shownOn = stored && available, interactive = available)

    /**
     * *Thunderstorm*, from the theme's own weather (v5.10C): it flashes only while *Show Rain/Snow* is
     * on and Rain is the type -- exactly `LiveWeatherSceneRules.stormActive`'s theme path, which
     * `SwitchesTellTheTruthTest` holds it to. **Not the intensity**: the renderer flashes at 0 %
     * intensity too, so a switch that read off there would be the defect the other way round. The
     * flash over a dry sky (inventory I-292) is meant: the maintainer, 2026-10-04, *«nel mondo vero può
     * esistere un temporale senza pioggia, quindi va bene»*. While Live
     * Weather drives the scene the forecast decides instead, and since v5.10D the row is not drawn
     * then ([forecastOwnsTheWeatherControls]): until then it stood locked with the stored value, which
     * could read on under a clear sky.
     */
    fun thunderstorm(
        storedThunderstorm: Boolean,
        precipitationVisible: Boolean,
        precipitationIsRain: Boolean,
    ): DependentSwitchUiState =
        dependentSwitch(storedThunderstorm, available = precipitationVisible && precipitationIsRain)

    /**
     * Whether the Clouds and the Rain and snow pages of World & scene leave out the theme's own
     * weather controls -- *Show Clouds* and its amount, *Show Rain/Snow*, the type, the intensity and
     * *Thunderstorm* -- and say instead that the forecast decides them (v5.10D, inventory I-220, the
     * maintainer's *sì* of 2026-09-30 to row 11).
     *
     * While real weather drives the sky those controls hold the theme's values, not the sky's: until
     * v5.10D they stood there greyed, so *Show Clouds* could read **off** with the forecast's clouds on
     * screen (`LiveWeatherSceneRules.cloudDensity` gives the theme's switch no vote), and *Show
     * Rain/Snow* off in the rain. A greyed switch still says on or off, and that was the false part.
     * The colours stay: the forecast does not choose them. Exactly [LiveWeatherUiState.drivingTheScene],
     * so the controls come back the moment the theme's weather is what shows.
     */
    fun forecastOwnsTheWeatherControls(liveWeather: LiveWeatherUiState): Boolean = liveWeather.drivingTheScene

    /**
     * The line under a weather provider's key row in Weather & time's Advanced group (v5.10D,
     * inventory I-221): **"Required - not set" only for the provider chosen as the source**. Until
     * v5.10D the two keyed providers both read "Required - not set" whatever was chosen, so a user on
     * Open-Meteo, which needs no key, read two requirements that did not apply to them; and the
     * Open-Meteo row said "using Open-Meteo's free service" while another provider was the one in use.
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
     * The Location row under GPS or Network (v5.10D, row 5 of the v5.10A table): what the phone gives,
     * read from the phone each time the page comes to the front, never from the stored choice.
     *
     * The stored choice is not rewritten (`AI_PROJECT_RULES.md` 8.7): GPS stays chosen, and the row
     * under it says it is not working, why, and where a tap puts it right. Until v5.10D the row showed
     * the last position the phone had given, "Resolved from the GPS receiver", whatever the phone said
     * now -- with the permission taken away, the location switched off, or an app backup restored on a
     * phone that never gave the permission -- and the wallpaper used that position for good.
     *
     * Null for Off and Custom, which use nothing of the phone's.
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
     * What to do with the answer to a location permission request for [kind] (v5.10D).
     *
     * [fineGranted] and [coarseGranted] are the phone's answer; [systemWouldAskAgain] is
     * `shouldShowRequestPermissionRationale` after it. **That says "no" both after a refusal for good
     * and after a dialog closed without an answer**, so the app's page is opened only where the first
     * is meant: after a refusal already seen on this screen ([refusalSeenHere]), or for a choice
     * already stored ([choiceAlreadyStored] -- "GPS - tap to allow", where a refusal for good returns
     * at once, with no dialog). The same reading `UpdateNotificationPolicy`'s row makes of the same
     * signal for notifications (v5.10C).
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

    /** See [AmountSwitchUiState]. */
    fun amountSwitch(visible: Boolean, amount: Float): AmountSwitchUiState =
        AmountSwitchUiState(shownOn = visible && amount > 0f, noneAtZero = amount <= 0f)

    /**
     * What a tap that moves an [AmountSwitchUiState] switch to [wanted] writes. Turning it on at 0 %
     * restores [defaultAmount] -- the theme's own amount -- so the thing appears; anything else is the
     * switch's own flag, as it always was.
     */
    fun amountTap(wanted: Boolean, amount: Float, defaultAmount: Float): AmountTap =
        if (wanted && amount <= 0f) AmountTap.Restore(if (defaultAmount > 0f) defaultAmount else FALLBACK_AMOUNT)
        else AmountTap.SetVisible(wanted)

    /**
     * The amount a tap restores where the theme's own is 0 as well. No built-in theme ships one of
     * these controls at 0 today, so this is a backstop, not a choice anyone will see: the middle of
     * the slider.
     */
    const val FALLBACK_AMOUNT = 0.5f

    /**
     * The first half of the home screen's *Weather & time* line: what the Live Weather switch shows,
     * in words -- "on" only when the switch is (v5.10C, row 4: the line said "Live Weather on" over a
     * fixed hour), and while the stored "on" is held, why.
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
     * How many decorations the home screen's *Seasons & decorations* line counts as on: each one the
     * way its own switch shows it (v5.10C).
     *
     * A decoration with a density counts only above 0 % ([amountSwitch]), because at 0 % none is drawn
     * (inventory I-211: "Snowmen" at 0 % was a decoration on).
     *
     * **The palms are not counted, since v5.10C2.** v5.10C counted them on a theme that plants none,
     * while their switch was Seasons & decorations' Summer row; the maintainer moved it to the Trees
     * page of World & scene on 2026-10-03 (*«il flag non deve stare in summer ma in tree»*), and a line
     * that counts what its screen does not show is a count of something else.
     */
    fun decorationsOn(c: com.paperscrape.livewallpaper.engine.SceneCustomization): Int =
        listOf(
            c.christmasDecorationsEnabled,
            c.santaEnabled,
            c.halloweenEnabled,
            c.horrorSkyEnabled,
            c.flowersEnabled,
            amountSwitch(c.snowmen.visible, c.snowmen.density).shownOn,
            amountSwitch(c.gifts.visible, c.gifts.density).shownOn,
            amountSwitch(c.penguins.visible, c.penguins.density).shownOn,
            amountSwitch(c.bunnies.visible, c.bunnies.density).shownOn,
            amountSwitch(c.easterEggs.visible, c.easterEggs.density).shownOn,
            amountSwitch(c.pumpkins.visible, c.pumpkins.density).shownOn,
        ).count { it }

    /**
     * Whether choosing [pickedThemeId] -- a gallery card, or *Shuffle a random theme* -- must ask
     * first (v5.10C, row 9): while *Automatic theme by date* is on and the calendar is showing
     * [calendarThemeId] today, a different pick is saved and **not shown**, because the calendar wins
     * (`PaperWallpaperService` and the gallery resolve the same way). The tap changed nothing on the
     * wallpaper, and the row then read "Random theme active" over an Autumn scene (inventory I-212).
     * The question names the calendar's theme and offers to turn the automatic theme off.
     *
     * [pickedThemeId] null is *Shuffle*, whose theme does not exist until it is made: always a
     * different one. Picking the calendar's own theme asks nothing: it is already what shows.
     */
    fun pickNeedsCalendarQuestion(calendarThemeId: String?, pickedThemeId: String?): Boolean =
        calendarThemeId != null && pickedThemeId != calendarThemeId

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
     * The banner at the top of World & scene and Seasons & decorations (v5.10E, inventory I-219, the
     * maintainer's *sì* of 2026-09-30 to row 11). It said "Keep this look by saving the theme from
     * Advanced & about", which read as "unsaved changes are lost": they are not. Since v4.3 an edit is
     * kept with its theme -- archived when another theme is edited, back when this one is
     * (`WallpaperPrefs.ensureFreshPendingTheme`) -- and saving makes a separate copy. Now it says so.
     */
    fun themeEditsBanner(themeName: String): String =
        "These change $themeName, the theme showing now, and your changes stay with it when you switch " +
            "themes. To keep this look as a theme of its own, save it from Advanced & about."

    /**
     * The question before "Reset this theme's scene to defaults" (v5.10E, inventory I-213). It said
     * "everything on this screen", and the screen ends with **Motion** -- scroll speed, parallax, the
     * background scrolling, swipe scroll -- which is every theme's, not this one's, and which the reset
     * has never touched (`WallpaperPrefs.resetAllCategories` removes only this theme's keys). Resetting
     * Motion from here would change every other theme, against the sentence that closes this one, so
     * the sentence says what the reset does instead [the choice the v5.10A table left between the two].
     */
    fun sceneResetMessage(themeName: String): String =
        "This puts the sky, the landscape, the life and the traffic on this screen back to how $themeName " +
            "ships - and its Seasons & decorations too: every winter, Christmas, Halloween, Easter and " +
            "spring decoration, and the autumn and winter palettes. Motion stays as it is: it is shared by " +
            "every theme. Your other themes are not affected."

    /**
     * The question before "Reset decorations to defaults" (v5.10E, inventory I-214): until then there
     * was none, and the reset left the snow and leaf piles where they were. What
     * `WallpaperPrefs.resetDecorations` removes, named; and the palms, which it no longer takes
     * (I-294: "Reset Trees to default" does, on the page their switch is on).
     */
    fun decorationsResetMessage(themeName: String): String =
        "This puts everything on this screen back to how $themeName ships: the seasonal palette and its " +
            "snow or leaf piles, and every winter, Christmas, Halloween, Easter and spring decoration, " +
            "with its density and colours. The rest of the scene and your other themes are not affected."

    /**
     * The gallery's "Reset to default" for a built-in theme with a saved version (v5.10E, inventory
     * I-215). It said "restores the original" and removed only the saved version: the edits made to
     * the theme in World & scene and Seasons & decorations, which win over a saved version
     * (`CustomThemeRegistry.resolveActiveCustomization`), stayed on top, and the card went on showing
     * them. Now the reset takes both, and the question says so.
     */
    fun galleryResetMessage(themeName: String): String =
        "This removes your saved version of \"$themeName\" and the changes made to it in World & scene and " +
            "Seasons & decorations, so it looks as it ships. Your other themes are not affected."

    /**
     * The question before "Replace with current" on a gallery card (v5.10E, inventory I-215): until then
     * none, and the tap copied the theme **showing now** -- its colours, its scene and your changes --
     * onto the card's theme. [targetName] is the card's theme, [showingName] the one showing, [sameTheme]
     * whether they are one theme (by id: a theme of your own may share a built-in's name);
     * [targetIsBuiltIn] says whether "Reset to default" can undo it (a saved version of a built-in) or
     * nothing can (a theme of your own, overwritten). Returns the title and the text.
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
     * Each bird colour's real share of the flock, in whole per cent, for the line beside its slider
     * (v5.10E, inventory I-218, the maintainer's *sì* to row 11). The sliders are weights, and they
     * printed "Color 1: 100" -- which read as "all the birds", though three other colours at 100 share
     * the flock four ways. The share is what [com.paperscrape.livewallpaper.engine.BirdsConfig.pickColor]
     * draws: each weight over their sum, and with every weight at 0, all of them the first colour.
     * Largest-remainder rounding, so the four always add up to 100.
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
     * *Swipe scroll* (v5.10E, inventory I-222; the rule of `AI_PROJECT_RULES.md` 8.7): on only once the
     * home screen has moved the wallpaper with a swipe at least once ([swipeReported],
     * `WallpaperSettings.swipeReported`, written by the wallpaper the first time it does). Many home
     * screens never tell the wallpaper about swipes -- the project's own phone never has (`CLAUDE.md`
     * §7) -- and there the switch was on over nothing. Off and locked until one arrives, with the
     * reason; the stored choice kept, and back by itself then. Nothing on this screen can make a home
     * screen send swipes, so the tap has nowhere to go.
     */
    fun swipeScroll(stored: Boolean, swipeReported: Boolean): DependentSwitchUiState =
        dependentSwitch(stored, available = swipeReported)

    /**
     * Whether anything scrolls the scene -- the drift at [scrollSpeed], or a swipe the home screen
     * reports with *Swipe scroll* on ([swipeScroll]) -- which is what *Parallax strength* and *Scroll
     * the background too* act on (v5.10E, inventory I-226). With neither, the two moved and changed
     * nothing: the slider is locked and the switch off and locked, the stored values kept.
     */
    fun sceneScrolls(scrollSpeed: Float, swipeScroll: DependentSwitchUiState): Boolean =
        scrollSpeed > 0f || swipeScroll.shownOn

    /**
     * Whether an amount slider -- a density, an intensity, an opacity, a number, a height -- accepts a
     * touch (v5.10E, inventory I-226): **only while its own switch is on**, as stored. With the switch
     * off it moved and changed nothing; locked now, its value kept. Not "at 0 %": there the switch reads
     * off because of the slider ([amountSwitch]), and the slider is how the amount comes back.
     */
    fun amountSliderEnabled(storedVisible: Boolean): Boolean = storedVisible

    /**
     * How the sailboat and dolphin rows must be drawn, given the stored values and the lake --
     * [lakeVisible] is the lake as its switch shows it: on, and since v5.10E above 0 % height
     * ([com.paperscrape.livewallpaper.engine.LakeConfig.drawsWater], inventory I-293).
     *
     * Pure, and stated here rather than inlined in the composable for the same three reasons
     * [moonPhases] is: this is the whole of the rule, it is the thing that must not start writing
     * to the preferences, and here it is readable and testable on the JVM without a device.
     * See [LakeContentsUiState].
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
