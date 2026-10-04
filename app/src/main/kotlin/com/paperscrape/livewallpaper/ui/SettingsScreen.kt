package com.paperscrape.livewallpaper.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.neverEqualPolicy
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp
import com.paperscrape.livewallpaper.BuildConfig
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.RandomSceneGenerator
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneTheme
import com.paperscrape.livewallpaper.engine.ThemePreviewGeometry
import com.paperscrape.livewallpaper.engine.hasPalmSlots
import com.paperscrape.livewallpaper.engine.keepsOnlyFirsUnderPalms
import com.paperscrape.livewallpaper.engine.CalendarWindow
import com.paperscrape.livewallpaper.engine.EasterSpan
import com.paperscrape.livewallpaper.engine.SeasonalCalendar
import com.paperscrape.livewallpaper.engine.SeasonalThemeRules
import com.paperscrape.livewallpaper.engine.coverage
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.WallpaperEngineCensus
import com.paperscrape.livewallpaper.location.DeviceLocationAccess
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.update.UpdateCheckResult
import com.paperscrape.livewallpaper.update.UpdateChecker
import com.paperscrape.livewallpaper.update.UpdateInfo
import com.paperscrape.livewallpaper.update.UpdateNotificationPolicy
import com.paperscrape.livewallpaper.update.UpdateNotifier
import com.paperscrape.livewallpaper.update.UpdatePrefs
import com.paperscrape.livewallpaper.weather.WeatherRepository
import kotlinx.coroutines.launch

/**
 * The five places settings live, plus the theme gallery.
 *
 * v2.8 had one home screen holding every wallpaper preference inline plus two full-screen
 * dialogs; the home screen alone was about four and a half screens of scrolling, and weather had
 * no section of its own -- it lived inside "Behavior" and disappeared entirely when "Follow real
 * time" was switched off. Each destination below owns one kind of decision; the home screen keeps
 * only the theme choice itself -- which theme is showing, whether the calendar picks it (the
 * "Automatic theme by date" switch), a random shuffle -- and says where everything else is.
 */
private enum class SettingsDestination { HOME, THEME_GALLERY, WEATHER, SEASONS, CALENDAR, WORLD, ADVANCED }

/**
 * The saved themes, as state the settings tree can read -- **published on every emission, not
 * only on the ones Compose considers a change** (v4.6).
 *
 * ### The defect
 *
 * `collectAsState` stores into a `mutableStateOf` with the default [structuralEqualityPolicy], so
 * an emission that is `==` to the value already held is not a change and nothing recomposes. That
 * is normally exactly right. It is wrong here because [com.paperscrape.livewallpaper.engine.SceneTheme]
 * declares
 *
 * ```
 * override fun equals(other: Any?): Boolean = other is SceneTheme && other.id == id
 * ```
 *
 * and a `CustomThemeEntry` is a data class holding one. So two themes with the same id and
 * *completely different colours, names and flags* compare equal, the `CustomThemeData` containing
 * them compares equal, and the settings screen never repaints.
 *
 * Reproduced on a device before the fix: restore a backup whose saved theme has the same id and a
 * different `displayName`, and the DataStore holds the new name while the open settings screen
 * keeps showing the old one until the Activity is recreated. The backup was never the problem --
 * it had already written both stores correctly.
 *
 * ### Why the fix is here and not on `SceneTheme`
 *
 * Widening `SceneTheme.equals` to compare content would touch every `==` in the app, on a class
 * whose fields are `IntArray`s, to solve a problem that belongs to one screen's state holder.
 * [neverEqualPolicy] states the actual requirement -- *this* store's emissions are always news --
 * in the one place that needs it. A DataStore write is a rare event, so the cost is a recomposition
 * per restore, per theme save and per rename.
 *
 * Updating [CustomThemeRegistry] from the same collector is the second half: it used to be a
 * separate collector in `SettingsActivity`, which left the order of the two undefined. Here the
 * registry is current *before* the state that triggers the recomposition is published, so a
 * composable that reads both -- `ThemeCatalog.byId` goes to the registry, the theme grid to this
 * state -- cannot see one of them stale.
 */
@Composable
private fun rememberCustomThemeData(store: CustomThemeStore): State<CustomThemeData> {
    val state = remember(store) { mutableStateOf(CustomThemeData.EMPTY, neverEqualPolicy()) }
    LaunchedEffect(store) {
        store.dataFlow.collect { data ->
            CustomThemeRegistry.update(data)
            state.value = data
        }
    }
    return state
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: WallpaperPrefs,
    customThemeStore: CustomThemeStore,
    updatePrefs: UpdatePrefs,
    onApplyWallpaper: () -> Unit,
    onRequestLocationPermission: (kind: DeviceLocationKind, onResult: (LocationPermissionAnswer) -> Unit) -> Unit,
    /** See `AdvancedScreen`'s parameter of the same name (A1, v5.7D). */
    onRequestNotificationPermission: (onResult: (granted: Boolean, canAskAgain: Boolean) -> Unit) -> Unit =
        { it(true, true) },
    /**
     * The release tag carried by a tapped update notification, or null on an ordinary open (A3).
     *
     * Set by `SettingsActivity` from [UpdateNotifier.EXTRA_SHOW_UPDATE_TAG]. Its presence is what
     * makes the check below run and the dialog appear **whatever the opt-in and the snooze say**:
     * the tap is the request, exactly as the button in *Advanced & about* is, and both are the user
     * asking rather than the app volunteering.
     *
     * Only its presence is tested; the tag itself is not used -- the check that follows produces
     * the real [UpdateInfo], with release notes and assets, and the notification is cancelled by
     * the tag that check returns.
     */
    openUpdateForTag: String? = null,
) {
    val settings by prefs.settingsFlow.collectAsState(initial = WallpaperSettings())
    val savedThemes = rememberCustomThemeData(customThemeStore)
    val scope = rememberCoroutineScope()

    /**
     * The update flow's state, held **here** rather than inside `AdvancedScreen` (ARC-08).
     *
     * The download is launched into [scope], which belongs to this composable; its state used to be
     * `remember`ed one level down, in the screen the user can navigate away from. So the job and the
     * thing it reports to had different lifetimes: walking back to the settings home mid-download
     * left the transfer running with nowhere to report, and returning showed `Idle` for a download
     * that had already finished into the cache.
     *
     * Hoisting it here is the whole fix for that half: the state now lives exactly as long as the
     * coroutine that writes it. The other half -- the Activity being torn down under both of them --
     * is the `configChanges` on `SettingsActivity`.
     */
    val updateState = remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var destination by remember { mutableStateOf(SettingsDestination.HOME) }
    /**
     * Where **World & scene** was opened from, so back returns there (N3, v5.7C).
     *
     * `destination` is a single value, not a stack, which is right for a tree of screens that are
     * all one tap from home -- except that World & scene has *two* doors. The second is Weather &
     * time's "Weather effects" row, and because every sub-screen's `onBack` was hard-wired to
     * HOME, going through that door and coming back landed on the settings home: the screen the
     * user had been on was gone, and the one back press they expected to undo the tap undid two.
     *
     * One field rather than a back stack because this is the only screen with a second door on
     * another sub-screen. Advanced & about has a second door too, the update dialog's "Install
     * update", which can open over any screen; back from there always goes home. It is set on every
     * route *into* WORLD, including the home one, so it can never be left pointing at a screen the
     * user did not come from.
     */
    var worldOpenedFrom by remember { mutableStateOf(SettingsDestination.HOME) }

    // Checked once each time this screen is composed -- which is once per app launch, since
    // `SettingsActivity` holds the composition across every configuration change it can (see its
    // `configChanges`) -- and again whenever the opt-in below is switched on. It runs only while
    // that opt-in is on, or when a tapped update notification asks for it (A3). Never as a
    // background or recurring check: this is an in-app-only prompt, not a system notification.
    //
    // The keyed `LaunchedEffect` below is what runs it, **not** `LaunchedEffect(Unit)`, which is
    // what this comment used to claim. The key matters: `settings` arrives from a flow with a
    // defaults-shaped initial value, so the first pass always sees the switch off, and it is the
    // key changing to `true` when the stored value lands that runs the check at all.
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var showSnoozeChoice by remember { mutableStateOf(false) }
    // Set when the update dialog's "Install update" is tapped: Advanced & about opens with this
    // release already being downloaded, so the one tap starts the flow rather than dropping the
    // user on a screen where they have to find it and start it again. A second tap for the same
    // release starts again once the first download has failed (`mayStartDownload`, v5.8C).
    var pendingInstall by remember { mutableStateOf<UpdateInfo?>(null) }

    // **No automatic check.** Opening the settings screen used to reach the network every time,
    // which is a request the user never made, for a feature they may not want. The check now runs
    // only if they have opted in (or tapped an update notification, below), and the manual button
    // in Advanced works whether they have or not.
    // Held here rather than read inside the effect: `LocalContext` is a composition local and the
    // effect's body is a coroutine that outlives the composition pass that started it.
    val screenContext = LocalContext.current
    LaunchedEffect(settings.automaticUpdateCheckEnabled, openUpdateForTag) {
        // A tapped notification is a request, so it runs whatever the opt-in says -- the user has
        // just asked, the same way the button in Advanced & about is an ask (A3, v5.7D).
        val askedByNotification = openUpdateForTag != null
        if (!askedByNotification && !settings.automaticUpdateCheckEnabled) return@LaunchedEffect
        val snooze = updatePrefs.readSnoozeState()
        // Deliberately only the one outcome for a check nobody asked for: it has nothing to say
        // about a network that was not there, and reporting it would turn opening the settings
        // screen on a train into an error message. A tapped notification **did** ask, so there the
        // other outcomes are shown too -- Advanced & about with "could not check" or "up to date",
        // the same state its own button reports (v5.8C; until then an offline tap showed nothing
        // at all, and the notification looked broken).
        // With the reply kept from the last check, so an unchanged list costs no download (v5.10D).
        val result = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME, updatePrefs)
        val update = (result as? UpdateCheckResult.Available)?.info
        if (update == null) {
            if (askedByNotification) {
                updateState.value = updateStateFor(result)
                destination = SettingsDestination.ADVANCED
            }
            return@LaunchedEffect
        }
        // The same rule the engine's notification consults, read from one place so the two cannot
        // drift -- see UpdateNotificationPolicy.isSnoozed, which is this expression moved.
        val isSnoozedForThisVersion = UpdateNotificationPolicy.isSnoozed(
            tagName = update.tagName,
            snoozedTag = snooze.versionTag,
            untilMillis = snooze.untilMillis,
            nowMillis = System.currentTimeMillis(),
        )
        if (askedByNotification || !isSnoozedForThisVersion) {
            availableUpdate = update
        }
        // The dialog is now open on this release, so the notification about it has done its job.
        // Left in the shade it would be a second, now-redundant copy of what is on screen.
        if (askedByNotification) UpdateNotifier.cancel(screenContext, update.tagName)
    }

    // **Read here, in this scope, deliberately.**
    //
    // `ThemeCatalog.byId` and `resolveActiveCustomization` both resolve through
    // `CustomThemeRegistry`, which is an `AtomicReference` and not Compose state -- so neither call
    // below creates a dependency Compose can see, and neither will run again just because a saved
    // theme changed. `savedThemes` is the state that moves in step with the registry:
    // [rememberCustomThemeData] refreshes the registry first and publishes this second.
    //
    // So this line is the subscription for everything under it. Inline it into its use sites and
    // the home screen goes back to showing a restored theme's old name and old colours until the
    // Activity is recreated, which is exactly v4.6's P2 defect -- and it comes back *narrower* than
    // before, because the theme grid reads this state directly and would update while the row above
    // it did not.
    val customThemeData = savedThemes.value

    val calendarThemeId = if (settings.autoThemeByDate) {
        SeasonalThemeRules.themeForDate(calendar = settings.seasonalCalendar)
    } else {
        null
    }
    val effectiveThemeId = calendarThemeId ?: settings.themeId
    val effectiveTheme = ThemeCatalog.byId(effectiveThemeId)
    val customization = CustomThemeRegistry.resolveActiveCustomization(
        themeId = effectiveThemeId,
        pendingCustomization = settings.pendingCustomization,
        pendingThemeId = settings.pendingCustomizationThemeId,
        themeCustomizations = settings.themeCustomizations,
    )
    // Whether the theme showing plants palms of its own, which decides which half of the Palms switch
    // it reads (`SceneCustomization.palmsShown`). Keyed on `customThemeData` for the reason given where
    // it is read: a saved theme's layout comes from the registry, which Compose cannot see.
    val themeLayout = remember(effectiveThemeId, customThemeData) {
        SceneObjectCatalog.layoutFor(effectiveThemeId, effectiveTheme.accentColor)
    }
    val themeHasPalms = themeLayout.hasPalmSlots()
    // Whether the trees this theme keeps would all be Christmas firs with the palms on (v5.10C2): then the
    // Palms switch has no palm to put anywhere, and reads off. See `SettingsUiModel.palms`.
    val palmsOnlyFirs = themeLayout.keepsOnlyFirsUnderPalms(customization)
    // See [WallpaperEngineCensus] for why the engines are counted rather than WallpaperManager asked.
    val isTheWallpaper by WallpaperEngineCensus.isTheWallpaper.collectAsState()
    // What the phone gives for GPS or Network, read each time the screen comes back (v5.10D, row 5).
    val deviceAccess = rememberDeviceLocationAccess(settings)
    // *Shuffle* while the calendar is choosing: the question first (v5.10C, row 9).
    var confirmShuffle by remember { mutableStateOf(false) }

    ProvideSettingsBottomInset {
    Scaffold(
        topBar = { TopAppBar(title = { Text("PaperScrape") }) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState()),
        ) {
            HomeThemePreview(
                theme = effectiveTheme,
                customization = customization,
                pickedByDate = calendarThemeId != null,
            )

            // Directly under the preview, as it has always been: applying the wallpaper never
            // requires scrolling past anything. The label says what the phone is showing (assessment
            // v5.7, row 13: it used to invite a user to set a wallpaper they had just set); see
            // [WallpaperEngineCensus] for why it counts the engines rather than asking WallpaperManager.
            // The tap does the same either way -- the system's preview, where it can be set again,
            // which is also the way back when the home screen is blank after a force-stop.
            Button(
                onClick = onApplyWallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            ) {
                Text(if (isTheWallpaper) "PaperScrape is your wallpaper" else "Set as wallpaper")
            }

            SettingsSectionHeader("Theme")
            SettingsGroup {
                SettingsNavigationRow(
                    title = "Theme",
                    supporting = themeRowSummary(effectiveTheme.displayName, customThemeData),
                    icon = Icons.Filled.Palette,
                    onClick = { destination = SettingsDestination.THEME_GALLERY },
                )
                SettingsSwitchRow(
                    title = "Automatic theme by date",
                    supporting = SettingsUiModel.autoThemeLine(settings.seasonalCalendar, overridingYourPick = true),
                    icon = Icons.Filled.Event,
                    checked = settings.autoThemeByDate,
                    onCheckedChange = { scope.launch { prefs.setAutoThemeByDate(it) } },
                )
                if (settings.autoThemeByDate) {
                    val label = SeasonalThemeRules.labelForDate(calendar = settings.seasonalCalendar)
                    SettingsRow(
                        title = if (label != null) "Today: $label" else "Today: your own pick",
                        supporting = if (label != null) {
                            "Turn the switch off to keep ${ThemeCatalog.byId(settings.themeId).displayName} instead."
                        } else {
                            "No seasonal window is active right now, so your manually selected theme is showing."
                        },
                        icon = Icons.Outlined.Info,
                    )
                }
                // Reachable whether or not the switch is on: the dates are worth looking at before
                // deciding to turn it on, and an edit made with it off is kept.
                SettingsNavigationRow(
                    title = "Holiday calendar",
                    supporting = calendarRowSummary(settings.seasonalCalendar),
                    icon = holidayCalendarIcon,
                    supportingIsAccent = !settings.seasonalCalendar.isFactory,
                    onClick = { destination = SettingsDestination.CALENDAR },
                )
                SettingsRow(
                    title = "Shuffle a random theme",
                    supporting = if (RandomSceneGenerator.isRandomThemeId(settings.themeId)) {
                        "Random theme active - tap again to generate another one"
                    } else {
                        "Builds a new theme from scratch and selects it"
                    },
                    icon = Icons.Filled.Casino,
                    // While the calendar is choosing, a new random theme would be saved and not
                    // shown: the tap changed nothing on the wallpaper and the line above it read
                    // "Random theme active" over the calendar's scene (inventory I-212). So it asks.
                    onClick = {
                        if (SettingsUiModel.pickNeedsCalendarQuestion(calendarThemeId, pickedThemeId = null)) {
                            confirmShuffle = true
                        } else {
                            scope.launch { prefs.setTheme(RandomSceneGenerator.newThemeId()) }
                        }
                    },
                )
            }

            SettingsSectionHeader("Customise this theme")
            SettingsGroup {
                // Live Weather "on" here only when its switch is (v5.10C, row 4): the same rule, from the
                // same inputs, as Weather & time's own switch.
                val liveWeather = SettingsUiModel.liveWeather(
                    liveWeatherEnabled = settings.liveWeatherEnabled,
                    followRealTime = settings.syncWithRealTime,
                    locationMode = homeLocationMode(settings),
                    devicePositionUsable = deviceAccess.positionUsable(),
                    keyMissing = WeatherRepository.providerFor(settings.weatherProvider).requiresApiKey &&
                        settings.apiKeyForWeatherProvider.isBlank(),
                    isTheWallpaper = isTheWallpaper,
                    status = settings.liveWeather,
                )
                SettingsNavigationRow(
                    title = "Weather & time",
                    supporting = weatherRowSummary(settings, liveWeather, deviceAccess),
                    icon = Icons.Outlined.WbSunny,
                    supportingIsAccent = liveWeather.shownOn,
                    onClick = { destination = SettingsDestination.WEATHER },
                )
                SettingsNavigationRow(
                    title = "Seasons & decorations",
                    supporting = seasonsRowSummary(customization),
                    icon = Icons.Outlined.AcUnit,
                    onClick = { destination = SettingsDestination.SEASONS },
                )
                SettingsNavigationRow(
                    title = "World & scene",
                    supporting = "Sky, landscape, people, traffic, motion",
                    icon = Icons.Outlined.Landscape,
                    onClick = {
                        worldOpenedFrom = SettingsDestination.HOME
                        destination = SettingsDestination.WORLD
                    },
                )
            }

            SettingsSectionHeader("App")
            SettingsGroup {
                SettingsNavigationRow(
                    title = "Advanced & about",
                    supporting = "Custom themes, updates, backup, version ${BuildConfig.VERSION_NAME}",
                    icon = Icons.Filled.Tune,
                    onClick = { destination = SettingsDestination.ADVANCED },
                )
            }
            SettingsCaption(
                "PaperScrape is an open-source live wallpaper inspired by classic \"paper cutout\" " +
                    "animated backgrounds.",
            )
            SettingsBottomSpacer()
        }
    }

    when (destination) {
        SettingsDestination.HOME -> Unit
        SettingsDestination.THEME_GALLERY -> ThemeGalleryScreen(
            settings = settings,
            customThemeData = customThemeData,
            effectiveThemeId = effectiveThemeId,
            calendarThemeId = calendarThemeId,
            prefs = prefs,
            customThemeStore = customThemeStore,
            scope = scope,
            onBack = { destination = SettingsDestination.HOME },
        )
        SettingsDestination.WEATHER -> WeatherTimeScreen(
            settings = settings,
            prefs = prefs,
            scope = scope,
            onRequestLocationPermission = onRequestLocationPermission,
            onOpenWeatherEffects = {
                worldOpenedFrom = SettingsDestination.WEATHER
                destination = SettingsDestination.WORLD
            },
            onApplyWallpaper = onApplyWallpaper,
            onBack = { destination = SettingsDestination.HOME },
        )
        SettingsDestination.CALENDAR -> HolidayCalendarScreen(
            calendar = settings.seasonalCalendar,
            autoThemeEnabled = settings.autoThemeByDate,
            prefs = prefs,
            scope = scope,
            onBack = { destination = SettingsDestination.HOME },
        )
        SettingsDestination.SEASONS -> SeasonsScreen(
            customization = customization,
            forThemeId = effectiveThemeId,
            themeName = effectiveTheme.displayName,
            prefs = prefs,
            scope = scope,
            onBack = { destination = SettingsDestination.HOME },
        )
        SettingsDestination.WORLD -> WorldSceneScreen(
            customization = customization,
            settings = settings,
            theme = effectiveTheme,
            forThemeId = effectiveThemeId,
            themeName = effectiveTheme.displayName,
            themeHasPalms = themeHasPalms,
            palmsOnlyFirs = palmsOnlyFirs,
            prefs = prefs,
            customThemeStore = customThemeStore,
            customThemeData = customThemeData,
            scope = scope,
            // Not HOME: back goes to whichever of the two doors this screen was entered by.
            onBack = { destination = worldOpenedFrom },
        )
        SettingsDestination.ADVANCED -> AdvancedScreen(
            updateState = updateState,
            settings = settings,
            customThemeData = customThemeData,
            effectiveThemeId = effectiveThemeId,
            prefs = prefs,
            customThemeStore = customThemeStore,
            scope = scope,
            onUpdateFound = { availableUpdate = it },
            onRequestNotificationPermission = onRequestNotificationPermission,
            onApplyWallpaper = onApplyWallpaper,
            startInstallFor = pendingInstall,
            onInstallStarted = { pendingInstall = null },
            onBack = { destination = SettingsDestination.HOME },
        )
    }

    // The calendar stopped choosing while the question was up (midnight): nothing to ask any more,
    // and a question left pending would come back unasked the next time it chooses.
    if (confirmShuffle && calendarThemeId == null) LaunchedEffect(Unit) { confirmShuffle = false }
    if (confirmShuffle && calendarThemeId != null) {
        CalendarPickQuestion(
            calendarThemeName = ThemeCatalog.byId(calendarThemeId).displayName,
            onShowIt = {
                confirmShuffle = false
                scope.launch { prefs.setThemeTurningAutoThemeOff(RandomSceneGenerator.newThemeId()) }
            },
            onCancel = { confirmShuffle = false },
        )
    }

    availableUpdate?.let { update ->
        val context = LocalContext.current
        if (!showSnoozeChoice) {
            AlertDialog(
                onDismissRequest = { /* not dismissible by tapping outside -- must pick an option */ },
                title = { Text("Update available") },
                text = {
                    Column {
                        Text("${update.tagName} is available (you have v${BuildConfig.VERSION_NAME}).")
                        val notes = update.releaseNotes
                        if (!notes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("What's new:", style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            // **One release a block, laid out only when scrolled to** (v5.10D). The notes
                            // are every newer release's, whole: from 1.0 that is 68 releases and 211 558
                            // characters, and as one Text in a scrolling column the BV6600 froze for ~3 s
                            // (175 frames skipped) laying it out before the dialog appeared. A lazy list
                            // lays out the blocks on screen; the text is the same, block for block.
                            val blocks = remember(notes) { UpdateChecker.notesByRelease(notes) }
                            LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                                items(blocks) { block ->
                                    Text(block, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))
                                }
                            }
                        }
                    }
                },
                // Three actions, and the one that reads like the main one now installs.
                //
                // Until v2.13 "Update now" opened the release page, which was the whole update
                // path before there was an in-app one and stayed the default afterwards -- so the
                // download/verify/install flow that v2.11 built was reachable only by finding it
                // in Advanced & about. Installing is the primary action; the release's page is
                // available for anyone who wants to read the release on GitHub, and is now what it
                // says it is rather than a redirect standing in for an update. It was called "Check
                // project page" and opened the page of this one release, not the project's (v5.10E,
                // inventory I-225): "Open release page" names what opens.
                confirmButton = {
                    TextButton(
                        onClick = {
                            availableUpdate = null
                            pendingInstall = update
                            destination = SettingsDestination.ADVANCED
                        },
                    ) { Text("Install update") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = { showSnoozeChoice = true }) { Text("Remind me later") }
                        TextButton(
                            onClick = {
                                context.startActivity(Intent(Intent.ACTION_VIEW, update.releasePageUrl.toUri()))
                            },
                        ) { Text("Open release page") }
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { showSnoozeChoice = false },
                title = { Text("Remind me...") },
                text = { Text("When should PaperScrape ask again about ${update.tagName}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch { updatePrefs.snoozeForOneMonth(update.tagName) }
                            showSnoozeChoice = false
                            availableUpdate = null
                        },
                    ) { Text("In a month") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            scope.launch { updatePrefs.dismissWithoutSnoozing() }
                            showSnoozeChoice = false
                            availableUpdate = null
                        },
                        // **"Not now", not "Next app launch"** (A4, v5.7D). The old label promised
                        // a schedule the app does not keep: with the automatic check off -- the
                        // default -- nothing asks again next launch, so the button named a thing
                        // that never happened. This one is true in both configurations.
                    ) { Text("Not now") }
                },
            )
        }
    }
    }
}

/**
 * The preview at the top of the home screen, with the theme's own name on it and a badge when the
 * calendar is the one that chose it.
 *
 * v2.8 drew the same preview with no label at all, so the one question the screen exists to
 * answer -- which theme am I looking at -- was answered only indirectly, by a caption under a
 * switch further down.
 *
 * **4:3, the shape the scene is composed at** ([ThemePreviewGeometry.ASPECT_RATIO]), like the
 * gallery's cards and the World & scene strip. It was 16:9 until v5.9F, which showed only the top
 * 180 of the scene's 240 units: the road, the cars, the gifts, pumpkins and eggs on the ground
 * were cut off and the people showed only their heads (inventory I-88; changed on the maintainer's
 * decision of 2026-09-28, after the photograph of the two). The card is a sixth of a screen taller,
 * and the top button still fits on the first screen.
 */
@Composable
private fun HomeThemePreview(theme: SceneTheme, customization: SceneCustomization, pickedByDate: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(ThemePreviewGeometry.ASPECT_RATIO),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            ThemeScenePreview(theme = theme, modifier = Modifier.fillMaxSize(), customization = customization)
            Surface(
                color = MaterialTheme.colorScheme.scrim.copy(alpha = 0.45f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp),
            ) {
                Text(
                    theme.displayName,
                    style = MaterialTheme.typography.titleSmall,
                    color = androidx.compose.ui.graphics.Color.White,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                )
            }
            if (pickedByDate) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(12.dp),
                ) {
                    Text(
                        "Picked by date",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }
    }
}

private fun themeRowSummary(themeName: String, customThemeData: CustomThemeData): String {
    val builtIns = ThemeCatalog.ALL.size
    val saved = customThemeData.customThemes.size
    return if (saved > 0) {
        "$themeName - $builtIns built-in, $saved saved"
    } else {
        "$themeName - $builtIns built-in themes"
    }
}

/**
 * The one-line summary under "Holiday calendar".
 *
 * Names the count of moved windows rather than today's window: the row is about the calendar's
 * shape, and, while the automatic switch is on, the row above it already says what today resolved
 * to.
 */
private fun calendarRowSummary(calendar: SeasonalCalendar): String {
    val moved = calendar.spans.size + if (calendar.easter != EasterSpan.FACTORY) 1 else 0
    val gaps = calendar.coverage().uncoveredDays.size
    return when {
        gaps > 0 -> "$moved changed - $gaps days with no season under them"
        moved == 0 -> "${CalendarWindow.entries.size} windows, all on their default dates"
        moved == 1 -> "1 window moved from its default dates"
        else -> "$moved windows moved from their default dates"
    }
}

private fun homeLocationMode(settings: WallpaperSettings): LocationMode =
    SettingsUiModel.locationMode(
        settings.useLocationForSunTimes,
        settings.useCustomLocation,
        settings.deviceLocationKind,
    )

private fun weatherRowSummary(
    settings: WallpaperSettings,
    liveWeather: LiveWeatherUiState,
    deviceAccess: DeviceLocationAccess?,
): String {
    // What the phone gives, read from the phone (v5.10D, row 5): "GPS location" was said over a
    // permission taken away, with nothing from the phone in use.
    val notGiven = when (deviceAccess) {
        DeviceLocationAccess.NOT_ALLOWED, DeviceLocationAccess.APPROXIMATE_ONLY -> ", not allowed"
        DeviceLocationAccess.LOCATION_OFF -> ", phone location off"
        DeviceLocationAccess.ALLOWED, null -> ""
    }
    val location = when (homeLocationMode(settings)) {
        LocationMode.OFF -> "no location"
        LocationMode.GPS -> "GPS location$notGiven"
        LocationMode.NETWORK -> "network location$notGiven"
        LocationMode.CUSTOM -> "custom location"
    }
    val weather = SettingsUiModel.homeLiveWeatherLine(liveWeather)
    // "Live Weather off: no location chosen - no location" says the same thing twice.
    if (liveWeather.configuredOn && liveWeather.blocker == LiveWeatherBlocker.NO_LOCATION) return weather
    return "$weather - $location"
}

private fun seasonsRowSummary(customization: SceneCustomization): String {
    val palette = when (SettingsUiModel.seasonalPalette(customization.fallColorsEnabled, customization.winterColorsEnabled)) {
        SeasonalPalette.NONE -> "No seasonal palette"
        SeasonalPalette.AUTUMN -> "Autumn palette"
        SeasonalPalette.WINTER -> "Winter palette"
    }
    // Each decoration the way its own switch shows it: at 0 % density it is off (v5.10C). The palms
    // are not one of them since v5.10C2: their switch is on the Trees page of World & scene. See
    // [SettingsUiModel.decorationsOn].
    return when (val decorations = SettingsUiModel.decorationsOn(customization)) {
        0 -> "$palette - no decorations on"
        1 -> "$palette - 1 decoration on"
        else -> "$palette - $decorations decorations on"
    }
}
