package com.paperscrape.livewallpaper.ui

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: WallpaperPrefs,
    customThemeStore: CustomThemeStore,
    updatePrefs: UpdatePrefs,
    onApplyWallpaper: () -> Unit,
    onRequestLocationPermission: (onResult: (Boolean) -> Unit) -> Unit,
) {
    val settings by prefs.settingsFlow.collectAsState(initial = WallpaperSettings())
    val customThemeData by customThemeStore.dataFlow.collectAsState(initial = CustomThemeData())
    val scope = rememberCoroutineScope()
    var destination by remember { mutableStateOf(SettingsDestination.HOME) }

    // Checked once per app launch (LaunchedEffect(Unit) runs exactly once for this composition),
    // never as a background/recurring check -- this is deliberately an in-app-only prompt, not a
    // system notification, per the requirement that it must not nag the user outside the app.
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var showSnoozeChoice by remember { mutableStateOf(false) }

    // **No automatic check.** Opening the settings screen used to reach the network every time,
    // which is a request the user never made, for a feature they may not want. The check now runs
    // only if they have opted in, and the manual button in Advanced works whether they have or not.
    LaunchedEffect(settings.automaticUpdateCheckEnabled) {
        if (!settings.automaticUpdateCheckEnabled) return@LaunchedEffect
        val snooze = updatePrefs.readSnoozeState()
        val update = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME) ?: return@LaunchedEffect
        val isSnoozedForThisVersion = snooze.versionTag == update.tagName && System.currentTimeMillis() < snooze.untilMillis
        if (!isSnoozedForThisVersion) {
            availableUpdate = update
        }
    }

    val calendarThemeId = if (settings.autoThemeByDate) SeasonalThemeRules.themeForDate() else null
    val effectiveThemeId = calendarThemeId ?: settings.themeId
    val effectiveTheme = ThemeCatalog.byId(effectiveThemeId)
    val customization = CustomThemeRegistry.resolveActiveCustomization(
        themeId = effectiveThemeId,
        pendingCustomization = settings.pendingCustomization,
        pendingThemeId = settings.pendingCustomizationThemeId,
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
            forThemeId = effectiveThemeId,
            themeName = effectiveTheme.displayName,
            prefs = prefs,
            customThemeStore = customThemeStore,
            customThemeData = customThemeData,
            scope = scope,
            onBack = { destination = SettingsDestination.HOME },
        )
        SettingsDestination.ADVANCED -> AdvancedScreen(
            settings = settings,
            customThemeData = customThemeData,
            effectiveThemeId = effectiveThemeId,
            prefs = prefs,
            customThemeStore = customThemeStore,
            scope = scope,
            onUpdateFound = { availableUpdate = it },
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
                confirmButton = {
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, update.releasePageUrl.toUri()))
                            availableUpdate = null
                        },
                    ) { Text("Update now") }
                },
                dismissButton = {
                    TextButton(onClick = { showSnoozeChoice = true }) { Text("Remind me later") }
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
    val location = when (SettingsUiModel.locationMode(settings.useLocationForSunTimes, settings.useCustomLocation)) {
        LocationMode.OFF -> "no location"
        LocationMode.PHONE -> "phone location"
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
