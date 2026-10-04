package com.paperscrape.livewallpaper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.DirectionsWalk
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.Filter
import androidx.compose.material.icons.outlined.FilterDrama
import androidx.compose.material.icons.outlined.Flare
import androidx.compose.material.icons.outlined.Landscape
import androidx.compose.material.icons.outlined.LocationCity
import androidx.compose.material.icons.outlined.Park
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Terrain
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import com.paperscrape.livewallpaper.engine.SceneTheme
import com.paperscrape.livewallpaper.engine.ThemePreviewGeometry
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.LakeConfig
import com.paperscrape.livewallpaper.engine.PaperRenderer
import com.paperscrape.livewallpaper.engine.PrecipitationType
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.WallpaperEngineCensus
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.sunCloudHeightForFraction
import com.paperscrape.livewallpaper.engine.sunCloudHeightFraction
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.ObjectCategory
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.weather.WeatherRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Everything the scene is made of, plus how it moves.
 *
 * The fifteen categories and their sub-screens grew out of v2.8's "Scene Objects", and later
 * releases have added to them (automatic colour modes, business hours, night densities, the
 * lake-gated boat controls). What changed: they are grouped the way the scene is built (sky,
 * landscape, things that move) instead of listed in arrival order with a divider after each row;
 * most rows report their own state (Sky, Cities, Hills and Mountains describe their contents
 * instead), so the scene is readable without opening fifteen screens; and the four
 * scrolling controls that used to sit between the weather settings and the version number on the
 * home screen now live here, under Motion, labelled as what they are -- global, not per-theme.
 */
@Composable
internal fun WorldSceneScreen(
    customization: SceneCustomization,
    settings: WallpaperSettings,
    theme: SceneTheme,
    forThemeId: String,
    themeName: String,
    /**
     * Whether the layout this theme draws plants palms of its own -- `SceneObjectLayout.hasPalmSlots`,
     * asked by the caller of the same layout the wallpaper uses. It decides which half of the Palms
     * switch on the Trees page this theme reads (`SceneCustomization.palmsShown`, v5.10C): on Beach and
     * Desert the switch keeps or removes their palms, on every other theme it puts palms in the
     * ordinary trees' places.
     */
    themeHasPalms: Boolean,
    /**
     * Whether every tree this theme keeps would be a Christmas fir with the palms on
     * (`SceneObjectLayout.keepsOnlyFirsUnderPalms`, v5.10C2): the Palms switch then has no palm to put
     * anywhere, and reads off and locked with the reason.
     */
    palmsOnlyFirs: Boolean,
    prefs: WallpaperPrefs,
    customThemeStore: CustomThemeStore,
    customThemeData: CustomThemeData,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var activeSection by remember { mutableStateOf<String?>(null) }
    // **N1 (v5.7C): the whole-scope reset now asks first, and names what goes.**
    //
    // `resetAllCategories` removes this theme's entire customization record -- which is what its
    // author meant, and is more than the button's name says. "Scene" is this app's own name for
    // *this* screen, while the seasonal decorations live on `SeasonsScreen` behind their own,
    // narrower "Reset decorations to defaults"; so a button named after one screen silently
    // cleared the other. It was also the only whole-scope reset in the app with no confirmation:
    // "Reset all customised themes" in Advanced & about has one, and so does a theme card's
    // "Reset to default". This is that missing third dialog, in the same shape as those two.
    //
    // Nothing moved and nothing was renamed: the button's label, its position and what it does
    // once confirmed are byte for byte what they were.
    var confirmSceneReset by remember { mutableStateOf(false) }
    // **What the forecast is actually doing, not what the switch says.** These two rows and their
    // sub-screens used to read `settings.liveWeatherEnabled`, so with the switch on and no
    // location they announced "Driven by Live Weather" and went read-only while Weather & time's
    // own banner said the scene was running on this theme's weather -- and the theme's weather was
    // the truthful one. See [LiveWeatherUiState.drivingTheScene], which since v5.10C is never true
    // while Weather & time's switch reads off: another wallpaper, a fixed hour, no location or key.
    //
    // **And while it does, the theme's own weather controls are not drawn** (v5.10D, inventory I-220):
    // greyed, *Show Clouds* read off with the forecast's clouds on screen.
    // See [SettingsUiModel.forecastOwnsTheWeatherControls].
    val isTheWallpaper by WallpaperEngineCensus.isTheWallpaper.collectAsState()
    val deviceAccess = rememberDeviceLocationAccess(settings)
    val liveWeatherDriving = SettingsUiModel.forecastOwnsTheWeatherControls(
        SettingsUiModel.liveWeather(
            liveWeatherEnabled = settings.liveWeatherEnabled,
            followRealTime = settings.syncWithRealTime,
            locationMode = SettingsUiModel.locationMode(
                settings.useLocationForSunTimes,
                settings.useCustomLocation,
                settings.deviceLocationKind,
            ),
            devicePositionUsable = deviceAccess.positionUsable(),
            keyMissing = WeatherRepository.providerFor(settings.weatherProvider).requiresApiKey &&
                settings.apiKeyForWeatherProvider.isBlank(),
            isTheWallpaper = isTheWallpaper,
            status = settings.liveWeather,
        ),
    )

    SettingsSubScreen(title = "World & scene", onBack = onBack) {
        WorldScenePreview(theme = theme, customization = customization)
        // What happens to an edit, said as it is (v5.10E, inventory I-219): it stays with this theme.
        // It said "Keep this look by saving the theme", which read as "unsaved changes are lost".
        SettingsBanner(SettingsUiModel.themeEditsBanner(themeName))

        SettingsSectionHeader("Sky")
        SettingsGroup {
            SettingsNavigationRow(
                title = "Sun and moon",
                supporting = onOffSummary(customization.sun.visible, "Sun") + ", " +
                    onOffSummary(customization.moon.visible, "moon").lowercase(),
                icon = Icons.Outlined.WbSunny,
                onClick = { activeSection = "sunmoon" },
            )
            SettingsNavigationRow(
                title = "Sky",
                supporting = "Day, night, sunrise and sunset colours",
                icon = Icons.Outlined.Flare,
                onClick = { activeSection = "sky" },
            )
            SettingsNavigationRow(
                title = "Stars",
                supporting = amountSummary(customization.stars.visible, starsDrawnAmount(customization.stars.density)),
                icon = Icons.Outlined.StarBorder,
                onClick = { activeSection = "stars" },
            )
            SettingsNavigationRow(
                title = "Clouds",
                supporting = if (liveWeatherDriving) {
                    "Driven by Live Weather"
                } else {
                    amountSummary(customization.clouds.visible, customization.clouds.density)
                },
                icon = Icons.Outlined.Cloud,
                supportingIsAccent = liveWeatherDriving,
                onClick = { activeSection = "clouds" },
            )
            SettingsNavigationRow(
                title = "Rain and snow",
                supporting = if (liveWeatherDriving) {
                    "Driven by Live Weather"
                } else {
                    amountSummary(customization.precipitation.visible, customization.precipitation.intensity)
                },
                icon = Icons.Outlined.WaterDrop,
                supportingIsAccent = liveWeatherDriving,
                onClick = { activeSection = "precipitation" },
            )
            SettingsNavigationRow(
                title = "Rainbow",
                supporting = if (customization.rainbow.visible && customization.rainbow.opacity <= 0f) {
                    NONE_AT_ZERO_SUMMARY
                } else {
                    onOffSummary(customization.rainbow.visible, "Rainbow")
                },
                icon = Icons.Outlined.Filter,
                onClick = { activeSection = "rainbow" },
            )
        }

        SettingsSectionHeader("Landscape")
        SettingsGroup {
            SettingsNavigationRow(
                title = "Cities",
                supporting = "Houses and buildings",
                icon = Icons.Outlined.LocationCity,
                onClick = { activeSection = "cities" },
            )
            SettingsNavigationRow(
                title = "Hills",
                supporting = "Colours and variation",
                icon = Icons.Outlined.Landscape,
                onClick = { activeSection = "hills" },
            )
            SettingsNavigationRow(
                title = "Mountains",
                supporting = "Front and back layers",
                icon = Icons.Outlined.Terrain,
                onClick = { activeSection = "mountains" },
            )
            SettingsNavigationRow(
                title = "Trees",
                supporting = amountSummary(customization.trees.visible, customization.trees.density),
                icon = Icons.Outlined.Park,
                onClick = { activeSection = "trees" },
            )
            SettingsNavigationRow(
                // **N2 (v5.7C): "Umbrellas" was two different objects.** This row has always
                // meant `ObjectCategory.PARASOLS` -- the garden parasol standing beside a house.
                // The app also draws a *carried* umbrella in a walker's hand whenever it rains
                // (`PedestrianCarry`), which has no control anywhere, so a user who turned
                // "Umbrellas" off and then saw umbrellas in the rain was reading the row
                // correctly and the row was wrong. The name now says which object it is; the
                // sub-screen says what happens to the other one.
                title = "Parasols",
                supporting = amountSummary(customization.parasols.visible, customization.parasols.density),
                icon = Icons.Outlined.FilterDrama,
                onClick = { activeSection = "parasols" },
            )
            SettingsNavigationRow(
                title = "Lake",
                // **N4 (v5.7C): the one of the five silent rows that has a state to report.**
                // The screen's own intent is that "each row reports its own state", and ten of
                // the fifteen did before this one; with it, eleven do. Lake is the row that
                // mattered: with the lake off, the screen below it shows four boat-and-dolphin
                // controls, so a row that only said "Water, sailboats and dolphins" was the reason
                // that screen read as broken. Sky, Cities, Hills and Mountains are deliberately
                // left as they are -- none of them has a single on/off to report, and inventing
                // one would be worse than a description.
                supporting = lakeRowSummary(customization.lake),
                icon = Icons.Outlined.Waves,
                onClick = { activeSection = "lake" },
            )
        }

        SettingsSectionHeader("Life and traffic")
        SettingsGroup {
            SettingsNavigationRow(
                title = "Cars",
                supporting = densitySummary(customization.cars.visible, customization.cars.density),
                icon = Icons.Outlined.DirectionsCar,
                onClick = { activeSection = "cars" },
            )
            SettingsNavigationRow(
                title = "People",
                supporting = densitySummary(customization.people.visible, customization.people.density),
                icon = Icons.AutoMirrored.Outlined.DirectionsWalk,
                onClick = { activeSection = "people" },
            )
            SettingsNavigationRow(
                title = "Birds",
                supporting = amountSummary(customization.birds.visible, customization.birds.density),
                icon = Icons.Filled.Air,
                onClick = { activeSection = "birds" },
            )
        }

        SettingsSectionHeader("Motion")
        // **On only while it happens** (v5.10E, inventories I-222 and I-226; `AI_PROJECT_RULES.md` 8.7).
        // *Swipe scroll* waits for the home screen to move the wallpaper with a swipe once -- many never
        // do, this project's phone among them -- and *Parallax strength* and *Scroll the background too*
        // act only on a scene that scrolls: the drift above 0 %, or a swipe that arrives. Off and
        // locked otherwise, with the reason; the stored values kept.
        val swipe = SettingsUiModel.swipeScroll(settings.swipeScroll, settings.swipeReported)
        val scrolls = SettingsUiModel.sceneScrolls(settings.scrollSpeed, swipe)
        val stillNote = if (settings.swipeReported) {
            "Nothing scrolls: Scroll speed is at 0% and Swipe scroll is off. Raise Scroll speed or turn " +
                "Swipe scroll on to set this; your choice is kept until then."
        } else {
            "Nothing scrolls: Scroll speed is at 0% and no swipe moves the wallpaper. Raise Scroll speed " +
                "to set this; your choice is kept until then."
        }
        SettingsGroup {
            SettingsSliderRow(
                title = "Scroll speed",
                valueLabel = { shown -> "${(shown * 100).toInt()}%" },
                supporting = "The scenery drifts by itself at this speed, all the time - separate from swiping.",
                value = settings.scrollSpeed,
                onCommit = { committed -> scope.launch { prefs.setScrollSpeed(committed) } },
                valueRange = 0f..1f,
            )
            SettingsSliderRow(
                title = "Parallax strength",
                valueLabel = { shown -> "%.1fx".format(shown) },
                supporting = if (scrolls) {
                    "How far apart near and far layers move relative to each other while scrolling."
                } else {
                    stillNote
                },
                value = settings.parallaxStrength,
                onCommit = { committed -> scope.launch { prefs.setParallaxStrength(committed) } },
                valueRange = 0.5f..2f,
                enabled = scrolls,
            )
            val background = SettingsUiModel.dependentSwitch(settings.scrollBackground, available = scrolls)
            SettingsSwitchRow(
                title = "Scroll the background too",
                supporting = if (scrolls) {
                    "Whether the sky, sun and moon scroll as well, or stay fixed while only the ground moves"
                } else {
                    stillNote
                },
                checked = background.shownOn,
                enabled = background.interactive,
                onCheckedChange = { scope.launch { prefs.setScrollBackground(it) } },
            )
            SettingsSwitchRow(
                title = "Swipe scroll",
                supporting = if (swipe.interactive) {
                    "Whether swiping between home screens also scrolls the wallpaper"
                } else {
                    "Your home screen has not moved the wallpaper with a swipe so far - many never do, and " +
                        "then there is nothing to follow. " + if (settings.swipeScroll) {
                            "This comes on by itself the first time one does."
                        } else {
                            "You can turn it on the first time one does."
                        }
                },
                icon = Icons.Filled.SwipeLeft,
                checked = swipe.shownOn,
                enabled = swipe.interactive,
                onCheckedChange = { scope.launch { prefs.setSwipeScroll(it) } },
            )
        }
        SettingsCaption("Motion applies to every theme, not only this one.")

        OutlinedButton(
            onClick = { confirmSceneReset = true },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
        ) {
            Text("Reset this theme's scene to defaults")
        }
    }

    if (confirmSceneReset) {
        AlertDialog(
            onDismissRequest = { confirmSceneReset = false },
            title = { Text("Reset $themeName to defaults?") },
            // The point of the dialog is this sentence: it names the screen whose work also goes.
            //
            // Named by **season**, not by object, and deliberately. `resetAllCategories` removes
            // every customization key this theme has, which on the decorations side is more than
            // the six `ObjectCategory` values -- it also takes `halloweenEnabled`,
            // `horrorSkyEnabled`, `christmasDecorationsEnabled`, `santaEnabled`, `flowersEnabled`
            // and the two palette flags -- and the two fields of the Palms switch, which since
            // v5.10C2 is on this screen's Trees page and so is "everything on this screen".
            // (`palmsEnabled` only since v5.8: until then `clearAllThemeCustomizationKeys` skipped
            // it, and the reset's "off" came back on the theme's next edit; `palmsInsteadOfTrees`
            // since it exists, v5.10C.) A list of objects would therefore be both
            // wrong today and one decoration away from being wrong again; the five seasons are the
            // whole of that screen and cannot go stale. There were six until the Palms switch, all
            // the Summer season held, moved to the Trees page.
            //
            // **And Motion stays** (v5.10E, inventory I-213): it is every theme's, the reset has never
            // touched it, and the sentence said "everything on this screen" with Motion on it. See
            // [SettingsUiModel.sceneResetMessage].
            text = { Text(SettingsUiModel.sceneResetMessage(themeName)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        prefs.resetAllCategories(forThemeId)
                        // "Reset everything to defaults" clearing only the in-progress scratch
                        // edit wasn't enough on its own: resolveActiveCustomization() falls back
                        // to a *saved* override for this theme once the scratch space and the
                        // theme's archived edit are gone -- which is exactly what the reset
                        // removes -- so if this built-in theme was ever overridden, the reset
                        // appeared to do nothing at all: the saved override came straight back.
                        // Clear that too, if one exists.
                        if (customThemeData.overrides.containsKey(forThemeId)) {
                            customThemeStore.clearOverride(forThemeId)
                        }
                    }
                    confirmSceneReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmSceneReset = false }) { Text("Cancel") } },
        )
    }

    when (activeSection) {
        "sunmoon" -> SunMoonSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "sky" -> SkySubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "stars" -> StarsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "clouds" -> CloudsSubScreen(customization, forThemeId, prefs, scope, liveWeatherDriving) { activeSection = null }
        "precipitation" -> PrecipitationSubScreen(customization, forThemeId, prefs, scope, liveWeatherDriving) { activeSection = null }
        "rainbow" -> RainbowSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "cities" -> CitiesSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "hills" -> HillsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "mountains" -> MountainsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "trees" -> TreesSubScreen(customization, themeHasPalms, palmsOnlyFirs, forThemeId, prefs, scope) { activeSection = null }
        "parasols" -> ParasolsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "lake" -> LakeSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "cars" -> CarsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "people" -> PeopleSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "birds" -> BirdsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
    }
}

private fun onOffSummary(visible: Boolean, subject: String): String =
    if (visible) "$subject on" else "$subject off"

private fun densitySummary(visible: Boolean, density: Float): String =
    if (visible) "On - ${(density * 100).toInt()}%" else "Off"

/**
 * The line under the buildings' slider (v5.10E, inventory I-216): what it thins, and what stays.
 */
internal const val BUILDINGS_DENSITY_LINE =
    "Thins out the towers behind the houses. The shops among the houses always stay while Show " +
        "Buildings is on."

/**
 * A car density as the slider prints it (v5.10E, inventory I-217): the per cent, and at the bottom
 * "one car", because `CarSelection.countFor` keeps one car driving at 0 %.
 */
internal fun carDensityValue(density: Float): String {
    val percent = (density * 100).toInt()
    return if (percent <= 0) "0% - one car" else "$percent%"
}

/** The stars' density, or 0 where it places no star at all (`PaperRenderer.starCountFor`). */
private fun starsDrawnAmount(density: Float): Float = if (PaperRenderer.starCountFor(density) == 0) 0f else density

/** What a row says for a thing drawn not at all at 0 % ([SettingsUiModel.amountSwitch]). */
private const val NONE_AT_ZERO_SUMMARY = "None at 0%"

/**
 * [densitySummary] for the things the wallpaper draws none of at 0 % -- not "On - 0%", which is what
 * the row said over a scene without one (v5.10C, row 8). The cars and the people keep
 * [densitySummary]: at 0 % there is still a car, and the people have their night density.
 */
private fun amountSummary(visible: Boolean, amount: Float): String =
    if (SettingsUiModel.amountSwitch(visible, amount).noneAtZero && visible) NONE_AT_ZERO_SUMMARY
    else densitySummary(visible, amount)

/**
 * The Lake row's state line (N4, v5.7C).
 *
 * The lake is not a density category, so [densitySummary] does not fit it: what a reader of this
 * row needs is whether there is water and, if there is, what is on it -- which is exactly the
 * question the screen below makes confusing when the answer is "no water". With the lake off the
 * boats and dolphins are deliberately **not** listed: they are not drawn and their controls are
 * locked, so reporting them would re-create the contradiction this round removed.
 */
private fun lakeRowSummary(lake: LakeConfig): String {
    if (!lake.visible) return "Off"
    // At 0 % height there is no water (v5.10E, inventory I-293): "None at 0%", like the other amounts.
    if (!lake.drawsWater) return NONE_AT_ZERO_SUMMARY
    // As their switches show them: at 0 % none floats (v5.10C).
    val floating = buildList {
        if (SettingsUiModel.amountSwitch(lake.sailboatsVisible, lake.sailboatsDensity).shownOn) add("sailboats")
        if (SettingsUiModel.amountSwitch(lake.dolphinsVisible, lake.dolphinsDensity).shownOn) add("dolphins")
    }
    return when (floating.size) {
        0 -> "On - water only"
        1 -> "On - ${floating[0]}"
        else -> "On - ${floating[0]} and ${floating[1]}"
    }
}

// ---------------------------------------------------------------------------------------------
// Category screens -- v2.8's, in the new shell, plus what later releases added to them
// ---------------------------------------------------------------------------------------------

@Composable
private fun SunMoonSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Sun and moon", onBack) {
        SectionTitle("Sun")
        SettingSwitchRow(
            title = "Show Sun", subtitle = "",
            checked = customization.sun.visible,
            onCheckedChange = { scope.launch { prefs.setSunVisible(it, forThemeId) } },
        )
        ColorSwatchRow("Sun Color", customization.sun.color) {
            editingTarget = ColorEditTarget("Sun Color", customization.sun.color) { c -> scope.launch { prefs.setSunColor(c, forThemeId) } }
        }
        SectionTitle("Moon")
        SettingSwitchRow(
            title = "Show Moon", subtitle = "",
            checked = customization.moon.visible,
            onCheckedChange = { scope.launch { prefs.setMoonVisible(it, forThemeId) } },
        )
        // **Halloween's moon is always full, so this control says so instead of pretending.**
        // `PaperRenderer.drawMoonWithPhase` blits `moon_jack_o_lantern` and returns before it
        // reads `realisticPhases` while the flag is on -- the renderer's own comment carries the
        // reason -- and until v4.23 this switch stayed live and did nothing.
        //
        // Shown off and locked, **and the stored preference is not touched**: nothing here writes
        // `false`, so turning Halloween back off restores whatever the user had. The rule is
        // `PeopleDensity.resolveNightDensity`'s -- a settings change may not silently alter what
        // an existing user set up. See [SettingsUiModel.moonPhases], which is where the whole
        // derivation lives so it can be read and tested without Compose.
        //
        // And with the moon itself hidden (v5.10C, row 7): `drawCelestialBody` returns before it is
        // drawn, so its phases are a switch with nothing under it -- off and locked, the same rule.
        val moonPhases = SettingsUiModel.moonPhases(
            storedRealisticPhases = customization.moon.realisticPhases,
            halloweenEnabled = customization.halloweenEnabled,
            moonVisible = customization.moon.visible,
        )
        SettingSwitchRow(
            title = "Realistic Moon Phases",
            subtitle = if (moonPhases.overriddenByHalloween) {
                "Halloween's moon is a carved lantern and is always full. Turn Halloween off in " +
                    "Seasons & decorations to set this; your choice is kept until then."
            } else if (moonPhases.needsMoon) {
                "Needs Show Moon above; your choice is kept until then."
            } else {
                "Show real moon phases at night"
            },
            checked = moonPhases.shownOn,
            enabled = moonPhases.interactive,
            onCheckedChange = { scope.launch { prefs.setMoonRealisticPhases(it, forThemeId) } },
        )
        ColorSwatchRow("Moon Color", customization.moon.color) {
            editingTarget = ColorEditTarget("Moon Color", customization.moon.color) { c -> scope.launch { prefs.setMoonColor(c, forThemeId) } }
        }
        SectionTitle("Arc")
        Text(
            "How high the sun and moon's arc rises across the sky.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        // Shown as a plain 0-100%, like the density sliders, and mapped onto the arc-height range
        // the renderer has always used. The stored value keeps its own scale, so nothing saved
        // needs migrating -- what changed is that the slider no longer prints an internal number
        // as if it were a percentage, which is what made "60%" look like it was near the middle
        // when it was the maximum.
        PreferenceSlider(
            label = { shown -> Text("Sun/Cloud Height: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = sunCloudHeightFraction(customization.sky.sunCloudHeight),
            onCommit = { fraction ->
                scope.launch { prefs.setSkySunCloudHeight(sunCloudHeightForFraction(fraction), forThemeId) }
            },
            storedAs = { fraction -> sunCloudHeightFraction(sunCloudHeightForFraction(fraction)) },
            valueRange = 0f..1f,
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun SkySubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Sky", onBack) {
        DayNightColorPair(
            dayLabel = "Day Color High", nightLabel = "Night Color High",
            dayColor = customization.sky.colorDayHigh, nightColor = customization.sky.colorNightHigh, mode = customization.sky.autoModeHigh,
            onEditDay = { editingTarget = ColorEditTarget("Sky - Day Color High", customization.sky.colorDayHigh) { c -> scope.launch { prefs.setSkyColorDayHigh(c, forThemeId) } } },
            onEditNight = { editingTarget = ColorEditTarget("Sky - Night Color High", customization.sky.colorNightHigh) { c -> scope.launch { prefs.setSkyColorNightHigh(c, forThemeId) } } },
            onModeChange = { scope.launch { prefs.setSkyAutoModeHigh(it, forThemeId) } },
        )
        DayNightColorPair(
            dayLabel = "Day Color Low", nightLabel = "Night Color Low",
            dayColor = customization.sky.colorDayLow, nightColor = customization.sky.colorNightLow, mode = customization.sky.autoModeLow,
            onEditDay = { editingTarget = ColorEditTarget("Sky - Day Color Low", customization.sky.colorDayLow) { c -> scope.launch { prefs.setSkyColorDayLow(c, forThemeId) } } },
            onEditNight = { editingTarget = ColorEditTarget("Sky - Night Color Low", customization.sky.colorNightLow) { c -> scope.launch { prefs.setSkyColorNightLow(c, forThemeId) } } },
            onModeChange = { scope.launch { prefs.setSkyAutoModeLow(it, forThemeId) } },
        )
        ColorSwatchRow("Sunrise Color Low", customization.sky.colorSunriseLow) {
            editingTarget = ColorEditTarget("Sky - Sunrise Color Low", customization.sky.colorSunriseLow) { c -> scope.launch { prefs.setSkyColorSunriseLow(c, forThemeId) } }
        }
        ColorSwatchRow("Sunset Color Low", customization.sky.colorSunsetLow) {
            editingTarget = ColorEditTarget("Sky - Sunset Color Low", customization.sky.colorSunsetLow) { c -> scope.launch { prefs.setSkyColorSunsetLow(c, forThemeId) } }
        }
        Text(
            "\"High\" is the top of the sky, \"Low\" is near the horizon. Sunrise/Sunset colors only show briefly near the horizon around those times.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun StarsSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    SettingsFormSubScreen("Stars", onBack) {
        // Below 1/70 the field holds no star (`PaperRenderer.starCountFor`), so that is "none" too.
        val drawn = starsDrawnAmount(customization.stars.density)
        val stars = SettingsUiModel.amountSwitch(customization.stars.visible, drawn)
        SettingSwitchRow(
            title = "Show Stars",
            subtitle = when {
                !stars.noneAtZero -> ""
                customization.stars.density <= 0f -> NONE_AT_ZERO_LINE
                else -> "Too few to draw a single star - tap to bring them back"
            },
            checked = stars.shownOn,
            onCheckedChange = { wanted ->
                scope.applyAmountTap(
                    SettingsUiModel.amountTap(wanted, drawn, defaultCustomizationFor(forThemeId).stars.density),
                    setVisible = { prefs.setStarsVisible(it, forThemeId) },
                    setAmount = { prefs.setStarsDensity(it, forThemeId) },
                )
            },
        )
        // Locked with the switch off (v5.10E, inventory I-226): it moved and changed nothing.
        PreferenceSlider(
            label = { shown -> Text("# of Stars: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.stars.density,
            onCommit = { committed -> scope.launch { prefs.setStarsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = SettingsUiModel.amountSliderEnabled(customization.stars.visible),
        )
    }
}

@Composable
private fun CloudsSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, liveWeatherDriving: Boolean, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Clouds", onBack) {
        // Live Weather (Weather & time) fully drives cloud density from real conditions while a
        // forecast is actually in effect -- see LiveWeatherSceneRules.cloudDensity (and the inline
        // comment at the top of PaperRenderer.drawClouds) on exactly how that override works.
        // Visibility/density out of reach *then* (greyed until v5.10D, not drawn since), so a manual
        // edit can't silently do nothing (or worse, look like it worked and then get overwritten on
        // the next hourly fetch); colors stay editable since Live Weather never touches those.
        //
        // v3.1: "then", not "whenever the switch is on". With the switch on but no forecast in
        // effect -- no location, no API key, a fetch that failed with nothing cached -- this
        // theme's own settings *are* what the scene is drawing, so locking them locked the only
        // controls that still did anything.
        //
        // v5.10D: while the forecast drives the sky the switch and the amount are not drawn at all: they
        // hold this theme's values, and greyed they said "Show Clouds" off with the forecast's clouds on
        // screen (inventory I-220). [SettingsUiModel.forecastOwnsTheWeatherControls].
        if (liveWeatherDriving) {
            Text(
                "Live Weather is drawing the clouds from the real sky right now: the forecast decides whether " +
                    "there are any and how many. Turn Live Weather off in Weather & time to set them here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // At 0 % no cloud is placed (v5.10C, row 8).
            val clouds = SettingsUiModel.amountSwitch(customization.clouds.visible, customization.clouds.density)
            SettingSwitchRow(
                title = "Show Clouds", subtitle = if (clouds.noneAtZero) NONE_AT_ZERO_LINE else "",
                checked = clouds.shownOn,
                onCheckedChange = { wanted ->
                    scope.applyAmountTap(
                        SettingsUiModel.amountTap(wanted, customization.clouds.density, defaultCustomizationFor(forThemeId).clouds.density),
                        setVisible = { prefs.setCloudsVisible(it, forThemeId) },
                        setAmount = { prefs.setCloudsDensity(it, forThemeId) },
                    )
                },
            )
            PreferenceSlider(
                label = { shown -> Text("# of Clouds: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
                value = customization.clouds.density,
                onCommit = { committed -> scope.launch { prefs.setCloudsDensity(committed, forThemeId) } },
                valueRange = 0f..1f,
                enabled = SettingsUiModel.amountSliderEnabled(customization.clouds.visible),
            )
        }
        DayNightColorPair(
            dayLabel = "Day Color", nightLabel = "Night Color",
            dayColor = customization.clouds.colorDay, nightColor = customization.clouds.colorNight, mode = customization.clouds.autoMode,
            onEditDay = { editingTarget = ColorEditTarget("Clouds - Day Color", customization.clouds.colorDay) { c -> scope.launch { prefs.setCloudsColorDay(c, forThemeId) } } },
            onEditNight = { editingTarget = ColorEditTarget("Clouds - Night Color", customization.clouds.colorNight) { c -> scope.launch { prefs.setCloudsColorNight(c, forThemeId) } } },
            onModeChange = { scope.launch { prefs.setCloudsAutoMode(it, forThemeId) } },
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun PrecipitationSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, liveWeatherDriving: Boolean, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    val precip = customization.precipitation
    SettingsFormSubScreen("Rain and snow", onBack) {
        // See CloudsSubScreen's own comment on this same pattern -- Live Weather fully drives
        // visibility/type/intensity here (PaperRenderer.drawPrecipitation's own doc comment) and
        // thunderstorm (LiveWeatherSceneRules.stormActive), so those controls are out of reach while
        // a forecast is actually in effect. Colors stay editable.
        //
        // v5.10D: while the forecast drives, the switch, the type, the intensity and *Thunderstorm* are
        // not drawn (inventory I-220): greyed, they showed this theme's values -- "Show Rain/Snow" off in
        // the rain, Thunderstorm on under a clear sky. [SettingsUiModel.forecastOwnsTheWeatherControls].
        if (liveWeatherDriving) {
            Text(
                "Live Weather is drawing rain, snow and thunderstorms from the real sky right now: the forecast " +
                    "decides whether anything falls, which, and how hard. Turn Live Weather off in Weather & time " +
                    "to set them here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // At 0 % intensity nothing falls (v5.10C, row 8).
            val precipitation = SettingsUiModel.amountSwitch(precip.visible, precip.intensity)
            SettingSwitchRow(
                title = "Show Rain/Snow",
                subtitle = if (precipitation.noneAtZero) "None at 0% intensity - tap to bring it back" else "",
                checked = precipitation.shownOn,
                onCheckedChange = { wanted ->
                    scope.applyAmountTap(
                        SettingsUiModel.amountTap(wanted, precip.intensity, defaultCustomizationFor(forThemeId).precipitation.intensity),
                        setVisible = { prefs.setPrecipitationVisible(it, forThemeId) },
                        setAmount = { prefs.setPrecipitationIntensity(it, forThemeId) },
                    )
                },
            )
            // The type and the intensity follow the switch (v5.10E, inventory I-226): with nothing
            // falling they moved and changed nothing.
            val falling = SettingsUiModel.amountSliderEnabled(precip.visible)
            Text("Type", style = MaterialTheme.typography.bodyMedium)
            SettingsSegmentedChoice(
                options = listOf("Rain", "Snow"),
                selectedIndex = if (precip.type == PrecipitationType.SNOW) 1 else 0,
                enabled = falling,
                onSelect = { index ->
                    val type = if (index == 1) PrecipitationType.SNOW else PrecipitationType.RAIN
                    scope.launch { prefs.setPrecipitationType(type, forThemeId) }
                },
            )
            PreferenceSlider(
                label = { shown -> Text("Intensity: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
                value = precip.intensity,
                onCommit = { committed -> scope.launch { prefs.setPrecipitationIntensity(committed, forThemeId) } },
                valueRange = 0f..1f,
                enabled = falling,
            )
        }
        SectionTitle("Rain Colors")
        DayNightColorPair(
            dayLabel = "Day Color", nightLabel = "Night Color",
            dayColor = precip.rainColorDay, nightColor = precip.rainColorNight, mode = precip.rainAutoMode,
            onEditDay = { editingTarget = ColorEditTarget("Rain - Day Color", precip.rainColorDay) { c -> scope.launch { prefs.setPrecipitationRainColorDay(c, forThemeId) } } },
            onEditNight = { editingTarget = ColorEditTarget("Rain - Night Color", precip.rainColorNight) { c -> scope.launch { prefs.setPrecipitationRainColorNight(c, forThemeId) } } },
            onModeChange = { scope.launch { prefs.setPrecipitationRainAutoMode(it, forThemeId) } },
        )
        SectionTitle("Snow Colors")
        DayNightColorPair(
            dayLabel = "Day Color", nightLabel = "Night Color",
            dayColor = precip.snowColorDay, nightColor = precip.snowColorNight, mode = precip.snowAutoMode,
            onEditDay = { editingTarget = ColorEditTarget("Snow - Day Color", precip.snowColorDay) { c -> scope.launch { prefs.setPrecipitationSnowColorDay(c, forThemeId) } } },
            onEditNight = { editingTarget = ColorEditTarget("Snow - Night Color", precip.snowColorNight) { c -> scope.launch { prefs.setPrecipitationSnowColorNight(c, forThemeId) } } },
            onModeChange = { scope.launch { prefs.setPrecipitationSnowAutoMode(it, forThemeId) } },
        )
        // The lightning flashes only with Show Rain/Snow on and Rain selected
        // (`LiveWeatherSceneRules.stormActive`): with snow, or the switch above off, it was on over a
        // sky that never flashed (v5.10C, row 7). Off and locked there, the stored value kept. While
        // Live Weather drives, not drawn (v5.10D): the forecast decides the flashes.
        if (!liveWeatherDriving) {
            SectionTitle("Storms")
            val storm = SettingsUiModel.thunderstorm(
                storedThunderstorm = precip.thunderstorm,
                precipitationVisible = precip.visible,
                precipitationIsRain = precip.type == PrecipitationType.RAIN,
            )
            SettingSwitchRow(
                title = "Thunderstorm",
                subtitle = if (storm.interactive) {
                    "Occasional lightning flashes (only while Rain is selected)"
                } else {
                    "Needs Show Rain/Snow on, with Rain selected; your choice is kept until then."
                },
                checked = storm.shownOn,
                enabled = storm.interactive,
                onCheckedChange = { scope.launch { prefs.setPrecipitationThunderstorm(it, forThemeId) } },
            )
        }
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun RainbowSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    val rainbow = customization.rainbow
    SettingsFormSubScreen("Rainbow", onBack) {
        val shown = SettingsUiModel.amountSwitch(rainbow.visible, rainbow.opacity)
        SettingSwitchRow(
            title = "Show Rainbow", subtitle = if (shown.noneAtZero) "None at 0% opacity - tap to bring it back" else "",
            checked = shown.shownOn,
            onCheckedChange = { wanted ->
                scope.applyAmountTap(
                    SettingsUiModel.amountTap(wanted, rainbow.opacity, defaultCustomizationFor(forThemeId).rainbow.opacity),
                    setVisible = { prefs.setRainbowVisible(it, forThemeId) },
                    setAmount = { prefs.setRainbowOpacity(it, forThemeId) },
                )
            },
        )
        Text(
            "How vivid the rainbow is at full daylight - it fades out toward night.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreferenceSlider(
            label = { shown -> Text("Opacity: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = rainbow.opacity,
            onCommit = { committed -> scope.launch { prefs.setRainbowOpacity(committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = SettingsUiModel.amountSliderEnabled(rainbow.visible),
        )
    }
}

@Composable
private fun CitiesSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Cities", onBack) {
        ObjectCategorySection(
            title = "Houses", config = customization.houses, category = ObjectCategory.HOUSES,
            forThemeId = forThemeId, prefs = prefs, scope = scope, noneAtZero = true,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
        // **The slider thins the towers only** (v5.10E, inventory I-216): the shops among the houses
        // stand whatever it says (`SceneCustomization.keepCandidate` keeps every building candidate
        // below `SceneSpace.BUILDING_TOWER_MAX_DEPTH`'s line), so "Density: 0%" left them all there.
        // It is called what it moves, and the line under it says what stays.
        ObjectCategorySection(
            title = "Buildings", config = customization.buildings, category = ObjectCategory.BUILDINGS,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
            densityLabel = "Towers",
            afterDensity = {
                Text(
                    BUILDINGS_DENSITY_LINE,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
        SettingSwitchRow(
            title = "Business hours",
            subtitle = "Shops and towers keep opening hours: outside them nobody stands at " +
                "their windows and the glass stays dark even at night. Houses are homes and " +
                "keep their evening lights.",
            checked = customization.businessHoursEnabled,
            onCheckedChange = { scope.launch { prefs.setBusinessHoursEnabled(it, forThemeId) } },
        )
        if (customization.businessHoursEnabled) {
            PreferenceSlider(
                label = { shown -> Text("Open from: ${formatHour(shown)}", style = MaterialTheme.typography.bodyMedium) },
                value = customization.businessOpenHour,
                onCommit = { committed -> scope.launch { prefs.setBusinessOpenHour(quantiseToQuarterHour(committed), forThemeId) } },
                storedAs = ::quantiseToQuarterHour,
                valueRange = 0f..24f,
            )
            PreferenceSlider(
                label = { shown -> Text("Until: ${formatHour(shown)}", style = MaterialTheme.typography.bodyMedium) },
                value = customization.businessCloseHour,
                onCommit = { committed -> scope.launch { prefs.setBusinessCloseHour(quantiseToQuarterHour(committed), forThemeId) } },
                storedAs = ::quantiseToQuarterHour,
                valueRange = 0f..24f,
            )
            Text(
                "The hours follow the scene's own clock, so a frozen time of day obeys them " +
                    "too. Closing past midnight works (09:00 until 02:00). Setting both to the " +
                    "same time means always open.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

/** A decimal hour as the clock text the two business sliders print, quantised to 15 minutes. */
private fun formatHour(hour: Float): String {
    val q = quantiseToQuarterHour(hour)
    val h = q.toInt() % 24
    val m = ((q - q.toInt()) * 60).toInt()
    return "%02d:%02d".format(h, m)
}

/** The stored value matches the printed one: both round to the same quarter hour. */
private fun quantiseToQuarterHour(hour: Float): Float =
    (Math.round(hour.coerceIn(0f, 24f) * 4f) / 4f)

@Composable
private fun HillsSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Hills", onBack) {
        DayNightColorPair(
            dayLabel = "Day Color", nightLabel = "Night Color",
            dayColor = customization.hillsColorDay, nightColor = customization.hillsColorNight, mode = customization.hillsAutoMode,
            onEditDay = { editingTarget = ColorEditTarget("Hills - Day Color", customization.hillsColorDay) { c -> scope.launch { prefs.setHillsColorDay(c, forThemeId) } } },
            onEditNight = { editingTarget = ColorEditTarget("Hills - Night Color", customization.hillsColorNight) { c -> scope.launch { prefs.setHillsColorNight(c, forThemeId) } } },
            onModeChange = { scope.launch { prefs.setHillsAutoMode(it, forThemeId) } },
        )
        Text(
            "How wavy the hill silhouette is. Lower it for flatter, calmer hills.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreferenceSlider(
            label = { shown -> Text("Variation: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.hillsVariation,
            onCommit = { committed -> scope.launch { prefs.setHillsVariation(committed, forThemeId) } },
            valueRange = 0f..1f,
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun MountainsSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Mountains", onBack) {
        Text(
            "Two independent background layers behind the hills.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MountainLayerSection(
            title = "Front Mountains", config = customization.mountainsFront, front = true,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
        MountainLayerSection(
            title = "Back Mountains", config = customization.mountainsBack, front = false,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

/**
 * The trees, and since v5.10C2 the Palms switch: the maintainer, 2026-10-03, *«il flag non deve stare
 * in summer ma in tree»*. The palms stand in the trees' places, so they are set beside *Show Trees* and
 * the density, under the same two rules they had in Seasons & decorations (v5.10C): off and locked
 * while there is no tree to stand in, on only where a palm stands ([SettingsUiModel.palms]).
 */
@Composable
private fun TreesSubScreen(
    customization: SceneCustomization,
    themeHasPalms: Boolean,
    palmsOnlyFirs: Boolean,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Trees", onBack) {
        ObjectCategorySection(
            title = "Trees", config = customization.trees, category = ObjectCategory.TREES,
            forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false, noneAtZero = true,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
            afterDensity = {
                // On every theme since v5.10C, the maintainer's decision of 2026-09-30: on the two
                // that plant palms it keeps or removes them, as it always did, and on every other
                // theme it puts palms where the ordinary trees stand -- a Christmas fir stays a fir
                // (v5.10C2). It starts off there, so nobody finds palms they did not ask for
                // (`SceneCustomization.palmsInsteadOfTrees`). And they stand where the trees do: off
                // and locked while there are none, or while every tree left is a Christmas fir (v5.10C2),
                // the stored choice kept.
                val palms = SettingsUiModel.palms(customization, themeHasPalms, onlyFirsWouldStand = palmsOnlyFirs)
                SettingSwitchRow(
                    title = "Palms",
                    subtitle = if (!palms.interactive && palmsOnlyFirs) {
                        "Every tree left at this density is a Christmas fir, and the firs stay. Raise the " +
                            "density above or turn the Christmas lights off; your choice is kept until then."
                    } else if (!palms.interactive) {
                        "Needs Show Trees above: palms stand where the trees do. Your choice is kept until then."
                    } else if (themeHasPalms) {
                        "Palm trees are this theme's own trees. Turn them off and it draws the same " +
                            "broadleaf trees as everywhere else - same places, same number, so the shore " +
                            "does not go bare."
                    } else if (customization.christmasDecorationsEnabled) {
                        "Palm trees in place of this theme's trees - same places, same number. " +
                            "The Christmas firs stay firs."
                    } else {
                        "Palm trees in place of this theme's trees - same places, same number."
                    },
                    checked = palms.shownOn,
                    enabled = palms.interactive,
                    onCheckedChange = { scope.launch { prefs.setPalmsEnabled(it, forThemeId) } },
                )
            },
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

/**
 * Visibility and density for the pedestrians, and nothing else.
 *
 * Deliberately not an [ObjectCategorySection]: that lays out four colour swatches, and nothing
 * here would take them. The people *are* recoloured at run time since v4.30 -- clothes, skin, hair
 * and hat drawn from their region masks -- but by the scene itself, a different combination at
 * every crossing, not by a colour the user picks; the maintainer decided on 2026-09-28 that they
 * stay that way (inventory I-73).
 */
@Composable
private fun PeopleSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    val config = customization.people
    SettingsFormSubScreen("People", onBack) {
        SettingSwitchRow(
            title = "Show people",
            subtitle = "People walk along the ground between the buildings and the road, and dress for the season.",
            checked = config.visible,
            onCheckedChange = { scope.launch { prefs.setCategoryVisible(ObjectCategory.PEOPLE, it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("Day density: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = config.density,
            onCommit = { committed -> scope.launch { prefs.setCategoryDensity(ObjectCategory.PEOPLE, committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = SettingsUiModel.amountSliderEnabled(config.visible),
        )
        PreferenceSlider(
            label = { shown -> Text("Night density: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.peopleNightDensity,
            onCommit = { committed -> scope.launch { prefs.setPeopleNightDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = SettingsUiModel.amountSliderEnabled(config.visible),
        )
        Text(
            "The street fills and empties across dusk and dawn, following the same light the " +
                "colours do. Their clothing follows the Winter palette, like the trees do.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ParasolsSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Parasols", onBack) {
        ObjectCategorySection(
            title = "Parasols", config = customization.parasols, category = ObjectCategory.PARASOLS,
            forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false, noneAtZero = true,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
        // The other half of N2: the object this screen does *not* control, named here so the
        // rename is an answer rather than half of one.
        SettingsCaption(
            "These are the garden parasols that stand beside the houses. The umbrellas people " +
                "carry are not one of the scene's objects: they go up by themselves whenever it " +
                "rains, and this switch does not turn them off.",
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun CarsSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Cars", onBack) {
        // **What the two ends do, said** (v5.10E, inventory I-217): at 0 % one car still drives
        // (`CarSelection.countFor` is 1 at the bottom), so the slider says "one car" there; and *Show
        // Cars* off takes the road away with the cars (`SceneObjectRenderer.drawsRoad`), which the line
        // under it said was "an empty road".
        ObjectCategorySection(
            title = "Cars", config = customization.cars, category = ObjectCategory.CARS,
            forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
            densityLabel = "Day density",
            densityValue = ::carDensityValue,
            switchSubtitle = "Off takes the cars and the road away",
            afterDensity = {
                // The night half of the pair, in the People screen's own shape: the twin slider
                // directly under the day one, and a line saying how the two meet.
                PreferenceSlider(
                    label = { shown -> Text("Night density: ${carDensityValue(shown)}", style = MaterialTheme.typography.bodyMedium) },
                    value = customization.carsNightDensity,
                    onCommit = { committed -> scope.launch { prefs.setCarsNightDensity(committed, forThemeId) } },
                    valueRange = 0f..1f,
                    enabled = SettingsUiModel.amountSliderEnabled(customization.cars.visible),
                )
                Text(
                    "The road fills and empties across dusk and dawn, one car at a time and " +
                        "always off screen - a car never pops into the middle of the road. The " +
                        "bottom of either slider keeps one last car driving; for no cars at all, " +
                        "turn Show Cars off, which takes the road away too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun BirdsSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Birds", onBack) {
        val birds = SettingsUiModel.amountSwitch(customization.birds.visible, customization.birds.density)
        SettingSwitchRow(
            title = "Show Birds", subtitle = if (birds.noneAtZero) NONE_AT_ZERO_LINE else "",
            checked = birds.shownOn,
            onCheckedChange = { wanted ->
                scope.applyAmountTap(
                    SettingsUiModel.amountTap(wanted, customization.birds.density, defaultCustomizationFor(forThemeId).birds.density),
                    setVisible = { prefs.setBirdsVisible(it, forThemeId) },
                    setAmount = { prefs.setBirdsDensity(it, forThemeId) },
                )
            },
        )
        val flying = SettingsUiModel.amountSliderEnabled(customization.birds.visible)
        PreferenceSlider(
            label = { shown -> Text("# of Birds: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.birds.density,
            onCommit = { committed -> scope.launch { prefs.setBirdsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = flying,
        )
        // `PaperRenderer.drawBirds` returns before the night is asked while the birds are off, so
        // this was on over a sky with no bird in it (v5.10C, row 7): off and locked, the stored
        // value kept, until the birds are back.
        val nightBirds = SettingsUiModel.dependentSwitch(customization.birds.nightBirds, available = birds.shownOn)
        SettingSwitchRow(
            title = "Night Birds",
            subtitle = if (nightBirds.interactive) {
                "Allow birds to fly at night"
            } else {
                "Needs Show Birds above; your choice is kept until then."
            },
            checked = nightBirds.shownOn,
            enabled = nightBirds.interactive,
            onCheckedChange = { scope.launch { prefs.setBirdsNight(it, forThemeId) } },
        )
        SectionTitle("Bird Colors")
        customization.birds.colors.forEachIndexed { index, colorWeight ->
            ColorSwatchRow("Bird Color ${index + 1}", colorWeight.color) {
                editingTarget = ColorEditTarget("Bird Color ${index + 1}", colorWeight.color) { c -> scope.launch { prefs.setBirdColor(index, c, forThemeId) } }
            }
        }
        // **Each colour's real share of the flock** (v5.10E, inventory I-218): the sliders are weights,
        // and "Color 1: 100" read as "all the birds" while three other colours at 100 shared the flock
        // four ways. The label works the share out from the weight being dragged and the other three as
        // stored -- what `BirdsConfig.pickColor` draws ([SettingsUiModel.birdColorShares]).
        Text(
            "How often each colour appears: each one's share of the birds is shown beside it, and moving " +
                "one changes the others' shares.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        val weights = customization.birds.colors.map { it.weight }
        customization.birds.colors.forEachIndexed { index, colorWeight ->
            PreferenceSlider(
                label = { shown ->
                    val share = SettingsUiModel.birdColorShares(weights.mapIndexed { i, w -> if (i == index) shown else w })[index]
                    Text("Color ${index + 1}: $share% of the birds", style = MaterialTheme.typography.bodySmall)
                },
                value = colorWeight.weight,
                onCommit = { committed -> scope.launch { prefs.setBirdWeight(index, committed, forThemeId) } },
                valueRange = 0f..1f,
                enabled = flying,
            )
        }
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun LakeSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Lake, boats and dolphins", onBack) {
        // **At 0 % height there is no lake** (v5.10E, inventory I-293, the maintainer's *«2 - si»* of
        // 2026-10-04): off, and said, as every other amount at 0 is (v5.10C, row 8), and a tap puts the
        // theme's own height back. The wallpaper draws no water and nothing on it then
        // (`LakeConfig.drawsWater`).
        val lake = SettingsUiModel.amountSwitch(customization.lake.visible, customization.lake.height)
        SettingSwitchRow(
            title = "Show Lake",
            subtitle = if (lake.noneAtZero) "None at 0% height - tap to bring it back" else "A body of water in the middle distance",
            checked = lake.shownOn,
            onCheckedChange = { wanted ->
                scope.applyAmountTap(
                    SettingsUiModel.amountTap(wanted, customization.lake.height, defaultCustomizationFor(forThemeId).lake.height),
                    setVisible = { prefs.setLakeVisible(it, forThemeId) },
                    setAmount = { prefs.setLakeHeight(it, forThemeId) },
                )
            },
        )
        DayNightColorPair(
            dayLabel = "Day Color", nightLabel = "Night Color",
            dayColor = customization.lake.colorDay, nightColor = customization.lake.colorNight, mode = customization.lake.autoMode,
            onEditDay = { editingTarget = ColorEditTarget("Lake - Day Color", customization.lake.colorDay) { c -> scope.launch { prefs.setLakeColorDay(c, forThemeId) } } },
            onEditNight = { editingTarget = ColorEditTarget("Lake - Night Color", customization.lake.colorNight) { c -> scope.launch { prefs.setLakeColorNight(c, forThemeId) } } },
            onModeChange = { scope.launch { prefs.setLakeAutoMode(it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("Lake Height: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.lake.height,
            onCommit = { committed -> scope.launch { prefs.setLakeHeight(committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = SettingsUiModel.amountSliderEnabled(customization.lake.visible),
        )
        // **The four controls below follow the lake (v5.7C).** With the lake off nothing floats
        // on it -- `PaperRenderer.updateLakeBandY` returns before `drawLake` ever reaches the
        // boats -- and until now both switches still read "on" and both sliders still moved. This
        // is the app's own pattern for exactly that, the one "Realistic Moon Phases" and the Live
        // Weather controls already use: grey the control, say why in its subtitle, and **do not
        // touch the stored value**. See [SettingsUiModel.lakeContents].
        val lakeContents = SettingsUiModel.lakeContents(
            lakeVisible = lake.shownOn,
            storedSailboatsVisible = customization.lake.sailboatsVisible,
            storedDolphinsVisible = customization.lake.dolphinsVisible,
        )
        // Worded on the "Realistic Moon Phases" subtitle, which is the sentence this pattern
        // already uses: what is holding the control, how to release it, and the promise that the
        // stored value is still there.
        val lakeOffNote = if (lake.noneAtZero && customization.lake.visible) {
            "The lake is at 0% height, so nothing floats on it. Raise Lake Height to set this; your " +
                "choice is kept until then."
        } else {
            "The lake is off, so nothing floats on it. Turn Show Lake on to set " +
                "this; your choice is kept until then."
        }
        // And at 0 % none floats (v5.10C, row 8): the switch reads off and says so.
        val sailboats = SettingsUiModel.amountSwitch(lakeContents.sailboatsShownOn, customization.lake.sailboatsDensity)
        SettingSwitchRow(
            title = "Show Sailboats",
            subtitle = when {
                lakeContents.blockedByLakeOff -> lakeOffNote
                sailboats.noneAtZero -> NONE_AT_ZERO_LINE
                else -> ""
            },
            checked = sailboats.shownOn,
            enabled = lakeContents.interactive,
            onCheckedChange = { wanted ->
                scope.applyAmountTap(
                    SettingsUiModel.amountTap(wanted, customization.lake.sailboatsDensity, defaultCustomizationFor(forThemeId).lake.sailboatsDensity),
                    setVisible = { prefs.setLakeSailboatsVisible(it, forThemeId) },
                    setAmount = { prefs.setLakeSailboatsDensity(it, forThemeId) },
                )
            },
        )
        PreferenceSlider(
            label = { shown -> Text("# of Sailboats: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.lake.sailboatsDensity,
            onCommit = { committed -> scope.launch { prefs.setLakeSailboatsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = lakeContents.interactive && SettingsUiModel.amountSliderEnabled(customization.lake.sailboatsVisible),
        )
        val dolphins = SettingsUiModel.amountSwitch(lakeContents.dolphinsShownOn, customization.lake.dolphinsDensity)
        SettingSwitchRow(
            title = "Show Dolphins",
            subtitle = when {
                lakeContents.blockedByLakeOff -> lakeOffNote
                dolphins.noneAtZero -> NONE_AT_ZERO_LINE
                else -> ""
            },
            checked = dolphins.shownOn,
            enabled = lakeContents.interactive,
            onCheckedChange = { wanted ->
                scope.applyAmountTap(
                    SettingsUiModel.amountTap(wanted, customization.lake.dolphinsDensity, defaultCustomizationFor(forThemeId).lake.dolphinsDensity),
                    setVisible = { prefs.setLakeDolphinsVisible(it, forThemeId) },
                    setAmount = { prefs.setLakeDolphinsDensity(it, forThemeId) },
                )
            },
        )
        PreferenceSlider(
            label = { shown -> Text("# of Dolphins: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.lake.dolphinsDensity,
            onCommit = { committed -> scope.launch { prefs.setLakeDolphinsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = lakeContents.interactive && SettingsUiModel.amountSliderEnabled(customization.lake.dolphinsVisible),
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

/**
 * The scene as it currently stands, at the top of the screen that edits it.
 *
 * **The same preview the theme gallery draws**, through the same [ThemeScenePreview] and the same
 * [ThemePreviewGeometry] -- one preview system, not two. What was here before was a 120 dp strip
 * showing three sample objects, each magnified by its own fitting factor so that a house, a tree
 * and a tower of very different real heights would all fit the band. It answered "what colour is
 * my tree" and nothing else, and sitting a tap away from the gallery's mini scenes it read as a
 * leftover from a different app.
 *
 * The day/night control is kept, and is the one thing this call site adds: half the values edited
 * on the screens below are night colours, and a preview fixed at midday cannot show them.
 */
@Composable
private fun WorldScenePreview(theme: SceneTheme, customization: SceneCustomization) {
    var showNight by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            ThemeScenePreview(
                theme = theme,
                customization = customization,
                forceNight = showNight,
                modifier = Modifier
                    .fillMaxWidth()
                    // The gallery's shape, from the same constant, so the two cannot drift.
                    .aspectRatio(ThemePreviewGeometry.ASPECT_RATIO),
            )
        }
        SettingsSegmentedChoice(
            options = listOf("Day", "Night"),
            selectedIndex = if (showNight) 1 else 0,
            onSelect = { index -> showNight = index == 1 },
        )
    }
}
