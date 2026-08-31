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
import com.paperscrape.livewallpaper.engine.SceneTheme
import com.paperscrape.livewallpaper.engine.SeasonalThemeRules
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.update.UpdateCheckResult
import com.paperscrape.livewallpaper.update.UpdateChecker
import com.paperscrape.livewallpaper.update.UpdateInfo
import com.paperscrape.livewallpaper.update.UpdatePrefs
import kotlinx.coroutines.launch

/**
 * The five places settings live, plus the theme gallery.
 *
 * v2.8 had one home screen holding every wallpaper preference inline plus two full-screen
 * dialogs; the home screen alone was about four and a half screens of scrolling, and weather had
 * no section of its own -- it lived inside "Behavior" and disappeared entirely when "Follow real
 * time" was switched off. Each destination below owns one kind of decision, and the home screen
 * owns none of them: it says which theme is showing, who chose it, and where everything else is.
 */
private enum class SettingsDestination { HOME, THEME_GALLERY, WEATHER, SEASONS, WORLD, ADVANCED }

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
    onRequestLocationPermission: (permission: String, onResult: (Boolean) -> Unit) -> Unit,
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

    // Checked once per app launch (LaunchedEffect(Unit) runs exactly once for this composition),
    // never as a background/recurring check -- this is deliberately an in-app-only prompt, not a
    // system notification, per the requirement that it must not nag the user outside the app.
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var showSnoozeChoice by remember { mutableStateOf(false) }
    // Set when the update dialog's "Install update" is tapped: Advanced & about opens with this
    // release already being downloaded, so the one tap starts the flow rather than dropping the
    // user on a screen where they have to find it and start it again.
    var pendingInstall by remember { mutableStateOf<UpdateInfo?>(null) }

    // **No automatic check.** Opening the settings screen used to reach the network every time,
    // which is a request the user never made, for a feature they may not want. The check now runs
    // only if they have opted in, and the manual button in Advanced works whether they have or not.
    LaunchedEffect(settings.automaticUpdateCheckEnabled) {
        if (!settings.automaticUpdateCheckEnabled) return@LaunchedEffect
        val snooze = updatePrefs.readSnoozeState()
        // Deliberately only the one outcome. A check nobody asked for has nothing to say about a
        // network that was not there -- the button in Advanced & about is what reports that (see
        // `AdvancedScreen.checkForUpdate`), and reporting it here would turn opening the settings
        // screen on a train into an error message.
        val update = (UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME) as? UpdateCheckResult.Available)
            ?.info ?: return@LaunchedEffect
        val isSnoozedForThisVersion = snooze.versionTag == update.tagName && System.currentTimeMillis() < snooze.untilMillis
        if (!isSnoozedForThisVersion) {
            availableUpdate = update
        }
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

    val calendarThemeId = if (settings.autoThemeByDate) SeasonalThemeRules.themeForDate() else null
    val effectiveThemeId = calendarThemeId ?: settings.themeId
    val effectiveTheme = ThemeCatalog.byId(effectiveThemeId)
    val customization = CustomThemeRegistry.resolveActiveCustomization(
        themeId = effectiveThemeId,
        pendingCustomization = settings.pendingCustomization,
        pendingThemeId = settings.pendingCustomizationThemeId,
        themeCustomizations = settings.themeCustomizations,
    )

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
            // requires scrolling past anything.
            Button(
                onClick = onApplyWallpaper,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 16.dp),
            ) {
                Text("Set as wallpaper")
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
                    supporting = "The calendar picks a theme for every day of the year, overriding your own pick",
                    icon = Icons.Filled.Event,
                    checked = settings.autoThemeByDate,
                    onCheckedChange = { scope.launch { prefs.setAutoThemeByDate(it) } },
                )
                if (settings.autoThemeByDate) {
                    val label = SeasonalThemeRules.labelForDate()
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
                SettingsRow(
                    title = "Shuffle a random theme",
                    supporting = if (RandomSceneGenerator.isRandomThemeId(settings.themeId)) {
                        "Random theme active - tap again to generate another one"
                    } else {
                        "Builds a new theme from scratch and selects it"
                    },
                    icon = Icons.Filled.Casino,
                    onClick = { scope.launch { prefs.setTheme(RandomSceneGenerator.newThemeId()) } },
                )
            }

            SettingsSectionHeader("Customise this theme")
            SettingsGroup {
                SettingsNavigationRow(
                    title = "Weather & time",
                    supporting = weatherRowSummary(settings),
                    icon = Icons.Outlined.WbSunny,
                    supportingIsAccent = settings.liveWeatherEnabled,
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
                    onClick = { destination = SettingsDestination.WORLD },
                )
            }

            SettingsSectionHeader("App")
            SettingsGroup {
                SettingsNavigationRow(
                    title = "Advanced & about",
                    supporting = "Custom themes, updates, version ${BuildConfig.VERSION_NAME}",
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
            onOpenWeatherEffects = { destination = SettingsDestination.WORLD },
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
            prefs = prefs,
            customThemeStore = customThemeStore,
            customThemeData = customThemeData,
            scope = scope,
            onBack = { destination = SettingsDestination.HOME },
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
            startInstallFor = pendingInstall,
            onInstallStarted = { pendingInstall = null },
            onBack = { destination = SettingsDestination.HOME },
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
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 340.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(notes, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                // Three actions, and the one that reads like the main one now installs.
                //
                // Until v2.13 "Update now" opened the release page, which was the whole update
                // path before there was an in-app one and stayed the default afterwards -- so the
                // download/verify/install flow that v2.11 built was reachable only by finding it
                // in Advanced & about. Installing is the primary action; the project page is
                // available for anyone who wants to read the release on GitHub, and is now what it
                // says it is rather than a redirect standing in for an update.
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
                        ) { Text("Check project page") }
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
                            scope.launch { updatePrefs.snoozeUntilNextLaunch() }
                            showSnoozeChoice = false
                            availableUpdate = null
                        },
                    ) { Text("Next app launch") }
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
 * switch further down. The artwork itself is unchanged in this release; a more representative
 * preview is separate, separately-approved work.
 */
@Composable
private fun HomeThemePreview(theme: SceneTheme, customization: SceneCustomization, pickedByDate: Boolean) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .aspectRatio(16f / 9f),
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

private fun weatherRowSummary(settings: WallpaperSettings): String {
    val location = when (
        SettingsUiModel.locationMode(
            settings.useLocationForSunTimes,
            settings.useCustomLocation,
            settings.deviceLocationKind,
        )
    ) {
        LocationMode.OFF -> "no location"
        LocationMode.GPS -> "GPS location"
        LocationMode.NETWORK -> "network location"
        LocationMode.CUSTOM -> "custom location"
    }
    val weather = if (settings.liveWeatherEnabled) "Live Weather on" else "Live Weather off"
    return "$weather - $location"
}

private fun seasonsRowSummary(customization: SceneCustomization): String {
    val palette = when (SettingsUiModel.seasonalPalette(customization.fallColorsEnabled, customization.winterColorsEnabled)) {
        SeasonalPalette.NONE -> "No seasonal palette"
        SeasonalPalette.AUTUMN -> "Autumn palette"
        SeasonalPalette.WINTER -> "Winter palette"
    }
    val decorations = listOf(
        customization.christmasDecorationsEnabled,
        customization.santaEnabled,
        customization.halloweenEnabled,
        customization.horrorSkyEnabled,
        customization.flowersEnabled,
        customization.snowmen.visible,
        customization.gifts.visible,
        customization.penguins.visible,
        customization.bunnies.visible,
        customization.easterEggs.visible,
        customization.pumpkins.visible,
    ).count { it }
    return when (decorations) {
        0 -> "$palette - no decorations on"
        1 -> "$palette - 1 decoration on"
        else -> "$palette - $decorations decorations on"
    }
}
