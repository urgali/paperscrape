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
import com.paperscrape.livewallpaper.ui.icons.filled.Casino
import com.paperscrape.livewallpaper.ui.icons.filled.Event
import com.paperscrape.livewallpaper.ui.icons.filled.Palette
import com.paperscrape.livewallpaper.ui.icons.filled.Tune
import com.paperscrape.livewallpaper.ui.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.Info
import com.paperscrape.livewallpaper.ui.icons.outlined.Landscape
import com.paperscrape.livewallpaper.ui.icons.outlined.WbSunny
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.core.net.toUri
import androidx.compose.ui.unit.dp
import com.paperscrape.livewallpaper.BuildConfig
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.FirstFrameGate
import com.paperscrape.livewallpaper.engine.RandomSceneGenerator
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
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
import com.paperscrape.livewallpaper.update.UpdateCheckJobs
import com.paperscrape.livewallpaper.update.UpdateCheckResult
import com.paperscrape.livewallpaper.update.UpdateChecker
import com.paperscrape.livewallpaper.update.UpdateInfo
import com.paperscrape.livewallpaper.update.UpdateNotificationPolicy
import com.paperscrape.livewallpaper.update.UpdateNotifier
import com.paperscrape.livewallpaper.update.UpdatePrefs
import com.paperscrape.livewallpaper.weather.WeatherRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.ZonedDateTime

/**
 * The home screen, the five screens settings live on, and the theme gallery. Each screen owns one kind of
 * decision; the home screen keeps only the theme choice itself -- which theme is showing, whether the
 * calendar picks it ("Automatic theme by date"), a random shuffle -- and says where everything else is.
 */
private enum class SettingsDestination { HOME, THEME_GALLERY, WEATHER, SEASONS, CALENDAR, WORLD, ADVANCED }

/**
 * The saved themes, as state the settings tree can read -- **published on every emission, not only on
 * the ones Compose considers a change** ([neverEqualPolicy]).
 *
 * [com.paperscrape.livewallpaper.engine.SceneTheme] compares by id alone and a `CustomThemeEntry` is a
 * data class holding one, so two themes with the same id and different colours, names and flags compare
 * equal: under the default structural policy a backup restoring a renamed theme would leave the open
 * screen showing the old name. Widening `SceneTheme.equals` would touch every `==` in the app; the
 * policy costs a recomposition per restore, save and rename.
 *
 * The same collector updates [CustomThemeRegistry] *before* publishing, so a composable that reads both
 * (`ThemeCatalog.byId` goes to the registry, the theme grid to this state) cannot see one of them stale.
 * Null until the store's first answer, which the screen waits for before it draws.
 */
@Composable
private fun rememberCustomThemeData(store: CustomThemeStore): State<CustomThemeData?> {
    val state = remember(store) { mutableStateOf<CustomThemeData?>(null, neverEqualPolicy()) }
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
    /**
     * Which of the Activity's comings to the front are openings ([SettingsOpenings]): `SettingsActivity` tells it of
     * every page of another app it starts, and the screen feeds it the lifecycle.
     */
    settingsOpenings: SettingsOpenings,
    /** See `AdvancedScreen`'s parameter of the same name. */
    onRequestNotificationPermission: (onResult: (granted: Boolean, canAskAgain: Boolean) -> Unit) -> Unit =
        { it(true, true) },
    /**
     * The release tag of a tapped update notification ([UpdateNotifier.EXTRA_SHOW_UPDATE_TAG], set by
     * `SettingsActivity`), or null on an ordinary open. Its presence makes the check below run and the
     * dialog appear **whatever the opt-in and the snooze say**: the tap is the user asking, as the button
     * in *Advanced & about* is. The tag itself is not used: the check returns the real [UpdateInfo], and
     * the notification is cancelled by its tag.
     */
    openUpdateForTag: String? = null,
) {
    // The stored settings, null until the store first answers. Until then everything below runs on a fresh install's
    // settings, as it always has (the update check keyed on its switch, the job put back), and nothing is drawn.
    val storedSettings by prefs.settingsFlow.collectAsState<WallpaperSettings, WallpaperSettings?>(initial = null)
    val freshInstall = remember { WallpaperSettings() }
    val settings = storedSettings ?: freshInstall
    val savedThemes = rememberCustomThemeData(customThemeStore)
    val scope = rememberCoroutineScope()

    /**
     * The update flow's state, held **here**, beside the [scope] the download is launched into, and not in
     * `AdvancedScreen`, which the user can navigate away from mid-download: the state lives exactly as long
     * as the coroutine that writes it. `SettingsActivity`'s `configChanges` keeps the Activity from being
     * torn down under both.
     */
    val updateState = remember { mutableStateOf<UpdateUiState>(UpdateUiState.Idle) }
    var destination by remember { mutableStateOf(SettingsDestination.HOME) }
    /**
     * Where **World & scene** was opened from, so back returns there: it has two doors, the home screen
     * and Weather & time's "Weather effects" row. One field rather than a back stack, because it is the
     * only screen with a door on another sub-screen (the update dialog's "Install update" opens Advanced
     * & about over any screen, and back from there goes home). Set on every route into WORLD, so it never
     * points at a screen the user did not come from.
     */
    var worldOpenedFrom by remember { mutableStateOf(SettingsDestination.HOME) }

    // **The openings after the first** -- the update check below runs again at each. The Activity outlives an
    // opening -- Home leaves it in the background, and from Android 12 Back does too, in the wallpaper's process that
    // lives for days -- so an opening is an ON_START, not this composition: any ON_START but the first (the opening
    // this composition comes up in) and but the return from a page of another app the settings started themselves
    // -- the browser, the file picker, the installer, the phone's settings pages, the wallpaper picker
    // ([SettingsOpenings]).
    var openings by remember { mutableIntStateOf(0) }
    // The date the screen reads -- the calendar's theme of today, "Today: ...", the Holiday calendar's window of today,
    // the theme Seasons and World & scene edit -- read again each time the settings come to the front (every ON_START,
    // the return from a page included) and at midnight, so a screen left open over midnight edits today's theme.
    var today by remember { mutableStateOf(LocalDate.now()) }
    val lifecycleOwner = LocalLifecycleOwner.current
    val power = LocalContext.current.getSystemService(android.os.PowerManager::class.java)
    DisposableEffect(lifecycleOwner, settingsOpenings) {
        val observer = LifecycleEventObserver { _, event ->
            if (settingsOpenings.isOpening(event, screenOn = power?.isInteractive != false)) openings++
            if (event == Lifecycle.Event.ON_START) today = LocalDate.now()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    LaunchedEffect(today) {
        // Until the date moves on: a wait that ends a moment early waits again.
        while (LocalDate.now() == today) delay(SettingsUiModel.millisUntilNextDay(ZonedDateTime.now()))
        today = LocalDate.now()
    }

    // Checked each time the settings open -- this composition, and each later opening (`openings`: "each time you open
    // these settings", the line under the switch says; the return from a page the settings started is not one) -- and
    // whenever the opt-in below is switched on; only while it is on, or when a tapped update notification asks. This
    // is the in-app prompt; the background check is `UpdateCheckJobService`'s.
    //
    // The keyed `LaunchedEffect` below runs it. `settings` arrives with a defaults-shaped first value, so the first
    // pass sees the switch off and it is the key turning `true` when the stored value lands that runs the check;
    // `key(openings)` around it runs it again at each opening.
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var showSnoozeChoice by remember { mutableStateOf(false) }
    // Set when the update dialog's "Install update" is tapped: Advanced & about opens with this
    // release already being downloaded, so the one tap starts the flow. A second tap for the same
    // release starts again once the first download has failed (`mayStartDownload`).
    var pendingInstall by remember { mutableStateOf<UpdateInfo?>(null) }

    // Held here rather than read inside the effect: `LocalContext` is a composition local and the
    // effect's body is a coroutine that outlives the composition pass that started it.
    val screenContext = LocalContext.current
    // **The update job is put back where the switches say whenever this screen opens and whenever they
    // change** (`UpdateCheckJobService`): the screen opening is one of the two moments PaperScrape learns it
    // may run after a force stop took the job away (the wallpaper's service starting is the other), and a
    // backup restored here can change the switches. The job reads the switches from the store, so the
    // defaults-shaped first value of `settings` cannot cancel it. It does not check at once, and its
    // periodic runs start a period later, because this screen checks as it opens (below): GitHub would be
    // asked twice.
    LaunchedEffect(settings.automaticUpdateCheckEnabled, settings.updateNotificationsEnabled) {
        UpdateCheckJobs.reconcile(screenContext, checkAtOnce = false)
    }
    // A tapped notification's check does not depend on the stored settings, so it does not start again (asking
    // GitHub twice) when they replace the first, defaults-shaped value; an opening's own check follows the switch.
    key(openings) { LaunchedEffect(settings.automaticUpdateCheckEnabled.takeIf { openUpdateForTag == null }, openUpdateForTag) {
        // A tapped notification is a request, so it runs whatever the opt-in says, as the button in
        // Advanced & about does. The tap asked once: coming back later is an opening like any other.
        val askedByNotification = openUpdateForTag != null && openings == 0
        if (!askedByNotification && !settings.automaticUpdateCheckEnabled) return@LaunchedEffect
        val snooze = updatePrefs.readSnoozeState()
        // A check nobody asked for shows only an update: a network that was not there is no error to
        // show for opening the settings on a train. A tapped notification **did** ask, so there the other
        // outcomes show too, in Advanced & about ("could not check", "up to date"), as its own button does.
        // With the reply kept from the last check, so an unchanged list costs no download. Unless a tapped
        // notification asked, not inside a limit GitHub set (it would refuse), and an answer GitHub gave
        // less than a minute ago stands for it: the wallpaper bound again after a force stop may just have
        // checked.
        val result = UpdateChecker.checkForUpdate(
            BuildConfig.VERSION_NAME,
            updatePrefs,
            honourLimit = !askedByNotification,
            takeRecentAnswer = !askedByNotification,
        )
        val update = (result as? UpdateCheckResult.Available)?.info
        if (update == null) {
            if (askedByNotification) {
                updateState.value = updateStateFor(result)
                destination = SettingsDestination.ADVANCED
            }
            return@LaunchedEffect
        }
        // The rule the update job's notification consults too (`UpdateCheckJobs.runOnce`), so the two cannot drift.
        val isSnoozedForThisVersion = UpdateNotificationPolicy.isSnoozed(
            tagName = update.tagName,
            snoozedTag = snooze.versionTag,
            untilMillis = snooze.untilMillis,
            nowMillis = System.currentTimeMillis(),
        )
        if (askedByNotification || !isSnoozedForThisVersion) {
            availableUpdate = update
        }
        // The dialog is open on this release: the notification about it would be a second copy.
        if (askedByNotification) UpdateNotifier.cancel(screenContext, update.tagName)
    } }

    // **Read here, in this scope, deliberately**: it is the subscription for everything under it.
    // `ThemeCatalog.byId` and `resolveActiveCustomization` resolve through `CustomThemeRegistry`, an
    // `AtomicReference` Compose cannot see, and `savedThemes` is the state that moves in step with it
    // ([rememberCustomThemeData]). Inlined into its use sites, the home screen would show a restored
    // theme's old name and colours until the Activity is recreated.
    val customThemeData = savedThemes.value ?: CustomThemeData.EMPTY

    val calendarThemeId = if (settings.autoThemeByDate) {
        SeasonalThemeRules.themeForDate(date = today, calendar = settings.seasonalCalendar)
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
    // Whether the trees this theme keeps would all be Christmas firs with the palms on: then the
    // Palms switch has no palm to put anywhere, and reads off. See `SettingsUiModel.palms`.
    val palmsOnlyFirs = themeLayout.keepsOnlyFirsUnderPalms(customization)
    // What the theme's street stands under the edit, category by category: a switch of the street reads on only where one
    // stands (`SettingsUiModel.amountSwitch`).
    val standing = SettingsUiModel.standing(themeLayout, customization)
    // See [WallpaperEngineCensus] for why the engines are counted rather than WallpaperManager asked.
    val isTheWallpaper by WallpaperEngineCensus.isTheWallpaper.collectAsState()
    // What the phone gives for GPS or Network, read each time the screen comes back.
    val deviceAccess = rememberDeviceLocationAccess(settings)
    // *Shuffle* while the calendar is choosing: the question first.
    var confirmShuffle by remember { mutableStateOf(false) }

    // **Nothing is drawn until the stored settings and the saved themes have been read**
    // (`DESIGN_NOTES.md` §12, decision 94): drawn before, the screen would show a fresh install's theme for a moment, then the user's. The
    // window's background shows meanwhile. Past [FirstFrameGate.WAIT_LIMIT_MS] it draws with what has arrived, as the
    // wallpaper does, rather than stay empty on a store that does not answer.
    var waitedTooLong by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(FirstFrameGate.WAIT_LIMIT_MS)
        waitedTooLong = true
    }
    if (!waitedTooLong && (storedSettings == null || savedThemes.value == null)) return

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

            // Directly under the preview, so applying the wallpaper never needs a scroll. The label says
            // what the phone is showing ([WallpaperEngineCensus]); the tap is the same either way -- the
            // system's preview, which is also the way back when the home screen is blank after a force-stop.
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
                    val label = SeasonalThemeRules.labelForDate(date = today, calendar = settings.seasonalCalendar)
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
                    supporting = SettingsUiModel.shuffleLine(settings.themeId, calendarThemeId),
                    icon = Icons.Filled.Casino,
                    // While the calendar is choosing, a new random theme would be saved and not
                    // shown, so it asks.
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
                // Live Weather "on" here only when its switch is: the same rule, from the same inputs,
                // as Weather & time's own switch.
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
                    supporting = seasonsRowSummary(customization, themeLayout),
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
            today = today,
            prefs = prefs,
            scope = scope,
            onBack = { destination = SettingsDestination.HOME },
        )
        SettingsDestination.SEASONS -> SeasonsScreen(
            customization = customization,
            themeLayout = themeLayout,
            standing = standing,
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
            standing = standing,
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
                            // **One release a block, laid out only when scrolled to**: the notes are every
                            // newer release's, whole, and as one Text the BV6600 freezes for seconds laying
                            // them out before the dialog appears. The text is the same, block for block.
                            val blocks = remember(notes) { UpdateChecker.notesByRelease(notes) }
                            LazyColumn(modifier = Modifier.heightIn(max = 340.dp)) {
                                items(blocks) { block ->
                                    Text(block, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(bottom = 16.dp))
                                }
                            }
                        }
                    }
                },
                // Three actions: the main one installs; "Open release page" opens this release's page
                // on GitHub, and says so (`DESIGN_NOTES.md` §12, decision 45).
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
                                context.startActivityOrSay(Intent(Intent.ACTION_VIEW, update.releasePageUrl.toUri()), MissingApp.BROWSER)
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
                        // **"Not now"**, which promises no schedule: with the automatic check off,
                        // nothing asks again at the next launch.
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
 * **4:3, the shape the scene is composed at** ([ThemePreviewGeometry.ASPECT_RATIO]), like the
 * gallery's cards and the World & scene strip, so the road, the cars, what stands on the ground and
 * the people are all on it; the top button still fits on the first screen.
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
 * The one-line summary under "Holiday calendar": the count of moved windows, not today's window -- the
 * row is about the calendar's shape, and the row above already says what today resolved to.
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
    // What the phone gives, read from the phone: a location it does not give is said so.
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

private fun seasonsRowSummary(customization: SceneCustomization, themeLayout: SceneObjectLayout): String {
    val palette = when (SettingsUiModel.seasonalPalette(customization.fallColorsEnabled, customization.winterColorsEnabled)) {
        SeasonalPalette.NONE -> "No seasonal palette"
        SeasonalPalette.AUTUMN -> "Autumn palette"
        SeasonalPalette.WINTER -> "Winter palette"
    }
    // Each decoration the way its own switch shows it: off where none stands on the theme's street, at 0 % or at an
    // amount that keeps none. The palms are not one of them: their switch is on the Trees page of World & scene. See
    // [SettingsUiModel.decorationsOn].
    return when (val decorations = SettingsUiModel.decorationsOn(customization, themeLayout)) {
        0 -> "$palette - no decorations on"
        1 -> "$palette - 1 decoration on"
        else -> "$palette - $decorations decorations on"
    }
}
