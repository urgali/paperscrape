package com.paperscrape.livewallpaper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.paperscrape.livewallpaper.ui.icons.automirrored.outlined.DirectionsWalk
import com.paperscrape.livewallpaper.ui.icons.filled.Air
import com.paperscrape.livewallpaper.ui.icons.filled.SwipeLeft
import com.paperscrape.livewallpaper.ui.icons.outlined.AccountBalance
import com.paperscrape.livewallpaper.ui.icons.outlined.Apartment
import com.paperscrape.livewallpaper.ui.icons.outlined.Cloud
import com.paperscrape.livewallpaper.ui.icons.outlined.Cottage
import com.paperscrape.livewallpaper.ui.icons.outlined.DirectionsCar
import com.paperscrape.livewallpaper.ui.icons.outlined.Filter
import com.paperscrape.livewallpaper.ui.icons.outlined.FilterDrama
import com.paperscrape.livewallpaper.ui.icons.outlined.Flare
import com.paperscrape.livewallpaper.ui.icons.outlined.House
import com.paperscrape.livewallpaper.ui.icons.outlined.Landscape
import com.paperscrape.livewallpaper.ui.icons.outlined.Park
import com.paperscrape.livewallpaper.ui.icons.outlined.StarBorder
import com.paperscrape.livewallpaper.ui.icons.outlined.Storefront
import com.paperscrape.livewallpaper.ui.icons.outlined.Terrain
import com.paperscrape.livewallpaper.ui.icons.outlined.WaterDrop
import com.paperscrape.livewallpaper.ui.icons.outlined.Waves
import com.paperscrape.livewallpaper.ui.icons.outlined.WbSunny
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
import com.paperscrape.livewallpaper.engine.StorefrontGroup
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
 * Everything the scene is made of, plus how it moves: the categories grouped the way the scene is
 * built (sky, landscape, things that move), most rows reporting their own state (Sky, Hills and
 * Mountains describe their contents instead) so the scene reads without opening every screen, and the
 * scrolling controls under Motion, labelled as what they are: global, not per-theme.
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
     * switch on the Trees page this theme reads (`SceneCustomization.palmsShown`): on Beach and
     * Desert the switch keeps or removes their palms, on every other theme it puts palms in the
     * ordinary trees' places.
     */
    themeHasPalms: Boolean,
    /**
     * Whether every tree this theme keeps would be a Christmas fir with the palms on
     * (`SceneObjectLayout.keepsOnlyFirsUnderPalms`): the Palms switch then has no palm to put
     * anywhere, and reads off and locked with the reason.
     */
    palmsOnlyFirs: Boolean,
    /**
     * What the theme's street stands under [customization], category by category (`SettingsUiModel.standing`): the
     * houses, the towers, the trees and the parasols read on only where one stands, at 0 % or above, and say so where
     * the street has none at all.
     */
    standing: StandingObjects,
    prefs: WallpaperPrefs,
    customThemeStore: CustomThemeStore,
    customThemeData: CustomThemeData,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var activeSection by remember { mutableStateOf<String?>(null) }
    // **The whole-scope reset asks first, and names what goes** (`DESIGN_NOTES.md` §12, decision 42):
    // `resetAllCategories` removes this theme's entire customization record, Seasons & decorations'
    // too, which is more than the button's name says.
    var confirmSceneReset by remember { mutableStateOf(false) }
    // **What the forecast is doing, not what the switch says** ([LiveWeatherUiState.drivingTheScene],
    // never true while Weather & time's switch reads off). While it does, the theme's own weather
    // controls are not drawn: greyed, *Show Clouds* would read off under the forecast's clouds
    // ([SettingsUiModel.forecastOwnsTheWeatherControls]).
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
        // What happens to an edit, said as it is: it stays with this theme (decision 45).
        SettingsBanner(SettingsUiModel.themeEditsBanner(themeName))

        SettingsSectionHeader("Sky")
        SettingsGroup {
            SettingsNavigationRow(
                title = "Sun and moon",
                supporting = onOffSummary(SettingsUiModel.showSun(customization).shownOn, "Sun") + ", " +
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
                    onOffSummary(SettingsUiModel.showRainbow(customization).shownOn, "Rainbow")
                },
                icon = Icons.Outlined.Filter,
                onClick = { activeSection = "rainbow" },
            )
        }

        SettingsSectionHeader("Landscape")
        SettingsGroup {
            // **The buildings in four rows** (`DESIGN_NOTES.md` §12, decision 63): the houses, the towers,
            // the shops and the public buildings, each a page of its own one tap from here.
            SettingsNavigationRow(
                title = "Houses",
                supporting = streetSummary("houses", customization.houses.visible, customization.houses.density, standing.houses, standing.themeHasHouses),
                icon = Icons.Outlined.House,
                onClick = { activeSection = "houses" },
            )
            SettingsNavigationRow(
                title = SettingsUiModel.TOWERS_PAGE,
                supporting = SettingsUiModel.towersRowSummary(
                    customization.buildings.visible, customization.buildings.density,
                    customization.towerHoursEnabled, customization.towerOpenHour, customization.towerCloseHour,
                    towersStand = standing.towers,
                    towersOnTheme = standing.themeHasTowers,
                    placesOnTheme = standing.themeHasPlaces,
                ),
                icon = Icons.Outlined.Apartment,
                onClick = { activeSection = "towers" },
            )
            SettingsNavigationRow(
                title = storefrontGroupTitle(StorefrontGroup.SHOPS),
                supporting = SettingsUiModel.storefrontRowSummary(
                    StorefrontGroup.SHOPS, customization.buildings.visible,
                    customization.shopHoursEnabled, customization.shopOpenHour, customization.shopCloseHour,
                    placesOnTheme = standing.themeHasPlaces,
                ),
                icon = Icons.Outlined.Storefront,
                onClick = { activeSection = "shops" },
            )
            SettingsNavigationRow(
                title = storefrontGroupTitle(StorefrontGroup.PUBLIC),
                supporting = SettingsUiModel.storefrontRowSummary(
                    StorefrontGroup.PUBLIC, customization.buildings.visible,
                    customization.publicHoursEnabled, customization.publicOpenHour, customization.publicCloseHour,
                    placesOnTheme = standing.themeHasPlaces,
                ),
                icon = Icons.Outlined.AccountBalance,
                onClick = { activeSection = "publics" },
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
            // Under Mountains because they stand on them (decision 52). The row says what the switch
            // would: on only while houses stand.
            SettingsNavigationRow(
                title = "Distant houses",
                supporting = SettingsUiModel.distantHouses(
                    customization.distantHouses.visible,
                    customization.distantHouses.density,
                    mountainsShown(customization),
                ).summary,
                icon = Icons.Outlined.Cottage,
                onClick = { activeSection = "distanthouses" },
            )
            SettingsNavigationRow(
                title = "Trees",
                supporting = streetSummary("trees", customization.trees.visible, customization.trees.density, standing.trees, standing.themeHasTrees),
                icon = Icons.Outlined.Park,
                onClick = { activeSection = "trees" },
            )
            SettingsNavigationRow(
                // "Parasols", not "Umbrellas": this row is `ObjectCategory.PARASOLS`, the garden
                // parasol beside a house. The umbrella a walker carries in the rain
                // (`PedestrianCarry`) has no control; the sub-screen says so.
                title = "Parasols",
                supporting = streetSummary("parasols", customization.parasols.visible, customization.parasols.density, standing.parasols, standing.themeHasParasols),
                icon = Icons.Outlined.FilterDrama,
                onClick = { activeSection = "parasols" },
            )
            SettingsNavigationRow(
                title = "Lake",
                // The Lake row reports its state: with the lake off the screen below shows four locked
                // boat-and-dolphin controls, and this line says why. (Sky, Hills and Mountains have no
                // single on/off to report, so they describe their contents.)
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
                supporting = peopleSummary(customization.people.visible, customization.people.density, customization.peopleNightDensity),
                icon = Icons.AutoMirrored.Outlined.DirectionsWalk,
                onClick = { activeSection = "people" },
            )
            SettingsNavigationRow(
                title = "Birds",
                supporting = if (SettingsUiModel.showBirds(customization).interactive) {
                    amountSummary(customization.birds.visible, customization.birds.density)
                } else {
                    onOffSummary(false, "Birds")
                },
                icon = Icons.Filled.Air,
                onClick = { activeSection = "birds" },
            )
        }

        SettingsSectionHeader("Motion")
        // **On only while it happens** (decision 40).
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
            // The sentence names the screen whose work also goes, by **season**, not by object:
            // `resetAllCategories` removes every customization key of this theme, on the decorations
            // side more than the `ObjectCategory` values (the Halloween, Christmas, Santa, flowers and
            // palette switches), and the Palms switch's two fields, on this screen's Trees page. A list
            // of objects would go stale one decoration later; the seasons are the whole of that screen.
            // **Motion stays**: it is every theme's. See [SettingsUiModel.sceneResetMessage].
            text = { Text(SettingsUiModel.sceneResetMessage(themeName)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        prefs.resetAllCategories(forThemeId)
                        // resolveActiveCustomization() falls back to a *saved* override once the
                        // edits are gone, so on a theme ever overridden the reset would seem to do
                        // nothing: the override goes too, if one exists.
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
        "houses" -> HousesSubScreen(customization, standing.houses, standing.themeHasHouses, forThemeId, prefs, scope) { activeSection = null }
        "towers" -> TowersSubScreen(customization, standing.towers, standing.themeHasTowers, standing.themeHasPlaces, forThemeId, prefs, scope) { activeSection = null }
        "shops" -> StorefrontGroupSubScreen(StorefrontGroup.SHOPS, customization, standing.themeHasPlaces, forThemeId, prefs, scope,
            onGoToTowers = { activeSection = "towers" }) { activeSection = null }
        "publics" -> StorefrontGroupSubScreen(StorefrontGroup.PUBLIC, customization, standing.themeHasPlaces, forThemeId, prefs, scope,
            onGoToTowers = { activeSection = "towers" }) { activeSection = null }
        "hills" -> HillsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "mountains" -> MountainsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "distanthouses" -> DistantHousesSubScreen(
            customization, forThemeId, prefs, scope,
            onOpenMountains = { activeSection = "mountains" },
            onOpenHouses = { activeSection = "houses" },
        ) { activeSection = null }
        "trees" -> TreesSubScreen(customization, themeHasPalms, palmsOnlyFirs, standing.trees, standing.themeHasTrees, standing.onlyFirsEvenAtFull, forThemeId, prefs, scope) { activeSection = null }
        "parasols" -> ParasolsSubScreen(customization, standing.parasols, standing.themeHasParasols, forThemeId, prefs, scope) { activeSection = null }
        "lake" -> LakeSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "cars" -> CarsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "people" -> PeopleSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "birds" -> BirdsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
    }
}

/** Whether either mountain layer draws a mountain -- on and above 0 % -- which is what the distant houses stand on. */
private fun mountainsShown(c: SceneCustomization): Boolean = c.mountainsFront.drawsAny || c.mountainsBack.drawsAny

private fun onOffSummary(visible: Boolean, subject: String): String =
    if (visible) "$subject on" else "$subject off"

private fun densitySummary(visible: Boolean, density: Float): String =
    if (visible) "On - ${(density * 100).toInt()}%" else "Off"

/** The line under the buildings' slider (decision 45): what it thins, and what stays. */
internal const val BUILDINGS_DENSITY_LINE =
    "Thins out the towers behind the houses. The shops and the public buildings among the houses always " +
        "stay while Show Buildings is on."

/** What *Show Buildings* shows and hides: every building that is not a house, the three groups. */
internal const val BUILDINGS_SWITCH_LINE =
    "All the buildings that are not houses: the towers, the shops and the public buildings"

/**
 * The line under *Show Buildings*: [BUILDINGS_SWITCH_LINE], or on a street that has only towers ([placesOnTheme] false)
 * or only places among the houses ([towersOnTheme] false), what it does show there and what the theme lacks. With
 * neither, the switch is locked and says so itself.
 */
internal fun buildingsSwitchLine(placesOnTheme: Boolean, towersOnTheme: Boolean): String = when {
    placesOnTheme && towersOnTheme -> BUILDINGS_SWITCH_LINE
    towersOnTheme -> "The towers: this theme has no shops and no public buildings"
    else -> "The shops and the public buildings: this theme has no towers"
}

/**
 * The line under the Towers amount: [BUILDINGS_DENSITY_LINE], less what the street does not have -- no place among the
 * houses, nothing about the shops standing; no tower, nothing to thin.
 */
internal fun buildingsDensityLine(placesOnTheme: Boolean, towersOnTheme: Boolean): String = when {
    placesOnTheme && towersOnTheme -> BUILDINGS_DENSITY_LINE
    towersOnTheme -> "Thins out the towers behind the houses."
    placesOnTheme -> "This theme has no towers. The shops and the public buildings among the houses always stay while " +
        "Show Buildings is on."
    else -> SettingsUiModel.noneOnThisThemeLine("towers")
}

/** The line over the Buildings colours: they are the towers'; the shops and the public buildings have their own. */
internal const val BUILDINGS_COLOUR_LINE =
    "These are the towers' colours - the shops and the public buildings have their own, on their pages. " +
        "Each tower randomly uses Color 1 or Color 2, and blends into its night version as it gets dark."

/** The line over the Houses colours: the distant houses on the mountains wear them too. */
internal const val HOUSES_COLOUR_LINE =
    "Each house randomly uses Color 1 or Color 2, and blends into its night version as it gets dark. " +
        "The distant houses on the mountains wear these colours too."

/**
 * A car density as the slider prints it (decision 45): the per cent, and at the bottom "one car",
 * because `CarSelection.countFor` keeps one car driving at 0 %.
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
 * [densitySummary] for the things the wallpaper draws none of at 0 % or, for a category of the street, at an
 * amount that keeps none on this theme ([stands], [StandingObjects]). The cars keep [densitySummary]: at 0 %
 * there is still a car; the people have [peopleSummary].
 */
private fun amountSummary(visible: Boolean, amount: Float, stands: Boolean = amount > 0f): String {
    val shown = SettingsUiModel.amountSwitch(visible, amount, stands)
    return when {
        shown.noneAtZero && visible -> NONE_AT_ZERO_SUMMARY
        shown.noneAtThisAmount -> "None at ${(amount * 100).toInt()}% on this theme"
        else -> densitySummary(visible, amount)
    }
}

/**
 * [amountSummary] for a category of the street, whose theme may have none of [things] at all ([onTheme],
 * [StandingObjects.themeHasTrees] and the three like it): then the row says so, whatever is stored, as its switch does.
 */
private fun streetSummary(things: String, visible: Boolean, amount: Float, stands: Boolean, onTheme: Boolean): String =
    if (SettingsUiModel.amountSwitch(visible, amount, stands, onTheme).noneOnThisTheme) {
        SettingsUiModel.noneOnThisThemeSummary(things)
    } else {
        amountSummary(visible, amount, stands)
    }

/** The People row: their day density, or -- both densities at 0 % -- that nobody walks ([SettingsUiModel.people]). */
private fun peopleSummary(visible: Boolean, dayDensity: Float, nightDensity: Float): String =
    if (visible && SettingsUiModel.people(visible, dayDensity, nightDensity).noneAtZero) "None at 0% by day and by night"
    else densitySummary(visible, dayDensity)

/**
 * The Lake row's state line: whether there is water and, if there is, what floats on it. With no
 * water the boats and dolphins are **not** listed: they are not drawn and their controls are locked.
 */
private fun lakeRowSummary(lake: LakeConfig): String {
    if (!lake.visible) return "Off"
    // At 0 % height there is no water: "None at 0%", like the other amounts.
    if (!lake.drawsWater) return NONE_AT_ZERO_SUMMARY
    // As their switches show them: at 0 % none floats.
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
// Category screens
// ---------------------------------------------------------------------------------------------

@Composable
private fun SunMoonSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Sun and moon", onBack) {
        SectionTitle("Sun")
        // With Halloween's night sky by day the sun is never up: off and locked, the line says
        // why, the stored choice kept ([SettingsUiModel.showSun], decision 40).
        val sun = SettingsUiModel.showSun(customization)
        SettingSwitchRow(
            title = "Show Sun", subtitle = if (sun.interactive) "" else SettingsUiModel.NIGHT_BY_DAY_LOCK_LINE,
            checked = sun.shownOn,
            enabled = sun.interactive,
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
        // Off and locked, **the stored preference untouched**, while Halloween is on (its carved moon
        // is always full: `PaperRenderer.drawMoonWithPhase` never reads `realisticPhases` then) or the
        // moon is hidden (`drawCelestialBody` draws none). See [SettingsUiModel.moonPhases].
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
        // A plain 0-100 %, like the density sliders, mapped onto the renderer's arc-height range: the
        // stored value keeps its own scale, so nothing saved needs migrating.
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
        // Locked with the switch off, where it would move and change nothing.
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
        // While a forecast drives the sky (`LiveWeatherSceneRules.cloudDensity`) the switch and the amount
        // are not drawn: an edit would do nothing, and greyed they would say "Show Clouds" off under the
        // forecast's clouds ([SettingsUiModel.forecastOwnsTheWeatherControls]). With the switch on and no
        // forecast in effect (no location, no key, a failed fetch) this theme's settings are what the
        // scene draws, so they stay. The colours stay editable: Live Weather never touches them.
        if (liveWeatherDriving) {
            Text(
                "Live Weather is drawing the clouds from the real sky right now: the forecast decides whether " +
                    "there are any and how many. Turn Live Weather off in Weather & time to set them here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // At 0 % no cloud is placed.
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
        // As on the Clouds page: while a forecast drives the sky it decides visibility, type, intensity
        // (`PaperRenderer.drawPrecipitation`) and the flashes (`LiveWeatherSceneRules.stormActive`), so
        // those controls are not drawn ([SettingsUiModel.forecastOwnsTheWeatherControls]). Colours stay.
        if (liveWeatherDriving) {
            Text(
                "Live Weather is drawing rain, snow and thunderstorms from the real sky right now: the forecast " +
                    "decides whether anything falls, which, and how hard. Turn Live Weather off in Weather & time " +
                    "to set them here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            // At 0 % intensity nothing falls.
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
            // The type and the intensity follow the switch: with nothing falling they would change nothing.
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
        // (`LiveWeatherSceneRules.stormActive`): off and locked otherwise, the stored value kept. Under
        // Live Weather not drawn: the forecast decides the flashes.
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
        // A daylight thing: never shown while Halloween's night sky by day keeps the scene night.
        val byDay = SettingsUiModel.showRainbow(customization)
        SettingSwitchRow(
            title = "Show Rainbow",
            subtitle = when {
                !byDay.interactive -> SettingsUiModel.NIGHT_BY_DAY_LOCK_LINE
                shown.noneAtZero -> "None at 0% opacity - tap to bring it back"
                else -> ""
            },
            checked = byDay.shownOn,
            enabled = byDay.interactive,
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
private fun HousesSubScreen(customization: SceneCustomization, housesStand: Boolean, housesOnTheme: Boolean, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Houses", onBack) {
        ObjectCategorySection(
            title = "Houses", config = customization.houses, category = ObjectCategory.HOUSES,
            forThemeId = forThemeId, prefs = prefs, scope = scope, noneAtZero = true, stands = housesStand, onTheme = housesOnTheme, showTitle = false,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
            colourLine = HOUSES_COLOUR_LINE,
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

/**
 * The towers' page: *Show Buildings*, which
 * shows or hides every building that is not a house -- the towers and the six buildings among the
 * houses, one candidate pool -- the Towers amount, the towers' opening hours and their colours.
 */
@Composable
private fun TowersSubScreen(customization: SceneCustomization, towersStand: Boolean, towersOnTheme: Boolean, placesOnTheme: Boolean, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen(SettingsUiModel.TOWERS_PAGE, onBack) {
        // **The slider thins the towers only**: the buildings among the houses stand whatever it says
        // (`SceneCustomization.keepCandidate` keeps every building candidate below
        // `SceneSpace.BUILDING_TOWER_MAX_DEPTH`'s line), so it is called what it moves and the line under
        // it says what stays (decision 45). **Its colours are the towers'**: the shops and the public
        // buildings have their own pages.
        ObjectCategorySection(
            title = "Buildings", config = customization.buildings, category = ObjectCategory.BUILDINGS,
            forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false, stands = towersStand,
            // A street with no tower and no place has no building for the switch to show.
            onTheme = towersOnTheme || placesOnTheme,
            switchSubtitle = buildingsSwitchLine(placesOnTheme, towersOnTheme),
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
            densityLabel = "Towers",
            afterDensity = {
                Text(
                    buildingsDensityLine(placesOnTheme, towersOnTheme),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // The towers' own opening hours, under the amount of what keeps them.
                OpeningHoursGroup(
                    title = "Tower opening hours",
                    line = TOWER_HOURS_LINE,
                    // A street with no tower at all: neither the switch nor the amount would help. Else Show Buildings
                    // on and the Towers above 0 %, and still no tower kept on this theme: the amount is why.
                    lockedLine = if (!towersOnTheme) {
                        SettingsUiModel.noneOnThisThemeLine("towers")
                    } else if (customization.buildings.visible && customization.buildings.density > 0f && !towersStand) {
                        TOWER_HOURS_NO_TOWER_LINE
                    } else {
                        TOWER_HOURS_LOCKED_LINE
                    },
                    state = SettingsUiModel.towerHours(
                        customization.towerHoursEnabled, customization.buildings.visible, customization.buildings.density,
                        towersStand = towersStand,
                    ),
                    openHour = customization.towerOpenHour,
                    closeHour = customization.towerCloseHour,
                    onEnabled = { scope.launch { prefs.setTowerHoursEnabled(it, forThemeId) } },
                    onOpen = { scope.launch { prefs.setTowerOpenHour(it, forThemeId) } },
                    onClose = { scope.launch { prefs.setTowerCloseHour(it, forThemeId) } },
                )
            },
            colourLine = BUILDINGS_COLOUR_LINE,
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

/**
 * The page of the shops or of the public buildings: [StorefrontGroupSection] -- the line, the
 * group's opening hours, its colours and its reset. With *Show Buildings* off the line says so and a tap
 * opens the *Towers & offices* page, where that switch is ([onGoToTowers]).
 */
@Composable
private fun StorefrontGroupSubScreen(
    group: StorefrontGroup,
    customization: SceneCustomization,
    /** Whether the street has a place among the houses, where the group's buildings stand ([StandingObjects.themeHasPlaces]). */
    placesOnTheme: Boolean,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onGoToTowers: () -> Unit,
    onBack: () -> Unit,
) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    val shops = group == StorefrontGroup.SHOPS
    SettingsFormSubScreen(storefrontGroupTitle(group), onBack) {
        StorefrontGroupSection(
            group = group,
            config = if (shops) customization.shops else customization.publics,
            buildingsVisible = customization.buildings.visible,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
            onGoToBuildings = onGoToTowers,
            placesOnTheme = placesOnTheme,
            openingHours = {
                OpeningHoursGroup(
                    title = if (shops) "Shop opening hours" else "Public building opening hours",
                    line = if (shops) SHOP_HOURS_LINE else PUBLIC_HOURS_LINE,
                    // A street with no place among the houses: no switch would bring the group.
                    lockedLine = if (placesOnTheme) {
                        STOREFRONT_HOURS_LOCKED_LINE
                    } else {
                        SettingsUiModel.noneOnThisThemeLine(SettingsUiModel.groupThings(group))
                    },
                    state = if (shops) {
                        SettingsUiModel.shopHours(customization.shopHoursEnabled, customization.buildings.visible, placesOnTheme)
                    } else {
                        SettingsUiModel.publicHours(customization.publicHoursEnabled, customization.buildings.visible, placesOnTheme)
                    },
                    openHour = if (shops) customization.shopOpenHour else customization.publicOpenHour,
                    closeHour = if (shops) customization.shopCloseHour else customization.publicCloseHour,
                    onEnabled = { scope.launch { if (shops) prefs.setShopHoursEnabled(it, forThemeId) else prefs.setPublicHoursEnabled(it, forThemeId) } },
                    onOpen = { scope.launch { if (shops) prefs.setShopOpenHour(it, forThemeId) else prefs.setPublicOpenHour(it, forThemeId) } },
                    onClose = { scope.launch { if (shops) prefs.setShopCloseHour(it, forThemeId) else prefs.setPublicCloseHour(it, forThemeId) } },
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
 * One group of opening hours (`DESIGN_NOTES.md` §12, decision 62): its switch and, while it is on, its two
 * hours -- the towers', the shops' (the restaurant, the bar and the shop) or the public buildings' (the
 * school's; the fire and the police station never close), each on its page and reset with it.
 *
 * The switch reads on only while there is a building of its group to keep the hours
 * ([SettingsUiModel.towerHours], [SettingsUiModel.shopHours], [SettingsUiModel.publicHours];
 * decision 40); locked, it says what is missing, and the stored choice and hours are kept.
 */
@Composable
private fun OpeningHoursGroup(
    title: String,
    line: String,
    lockedLine: String,
    state: DependentSwitchUiState,
    openHour: Float,
    closeHour: Float,
    onEnabled: (Boolean) -> Unit,
    onOpen: (Float) -> Unit,
    onClose: (Float) -> Unit,
) {
    SettingSwitchRow(
        title = title,
        subtitle = if (state.interactive) line else lockedLine,
        checked = state.shownOn,
        enabled = state.interactive,
        onCheckedChange = onEnabled,
    )
    if (state.shownOn) {
        PreferenceSlider(
            label = { shown -> Text("Open from: ${formatHour(shown)}", style = MaterialTheme.typography.bodyMedium) },
            value = openHour,
            onCommit = { committed -> onOpen(quantiseToQuarterHour(committed)) },
            storedAs = ::quantiseToQuarterHour,
            valueRange = 0f..24f,
        )
        PreferenceSlider(
            label = { shown -> Text("Until: ${formatHour(shown)}", style = MaterialTheme.typography.bodyMedium) },
            value = closeHour,
            onCommit = { committed -> onClose(quantiseToQuarterHour(committed)) },
            storedAs = ::quantiseToQuarterHour,
            valueRange = 0f..24f,
        )
        Text(
            HOURS_CLOCK_LINE,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** The towers' group's line, with its switch interactive. */
private const val TOWER_HOURS_LINE =
    "The towers keep opening hours: outside them the blinds are down, the door's shutter is down and " +
        "nobody stands at the windows; at night the glass stays dark."

/** The towers' group's line while there is no tower to keep the hours. */
private const val TOWER_HOURS_LOCKED_LINE =
    "Needs Show Buildings on and Towers above 0%; your choice is kept until then."

/** The towers' group's line while Show Buildings is on and the Towers amount above 0 % keeps no tower on this theme. */
private const val TOWER_HOURS_NO_TOWER_LINE =
    "No tower stands at this density on this theme. Raise Towers above; your choice is kept until then."

/** The shops' group's line, with its switch interactive. */
private const val SHOP_HOURS_LINE =
    "The restaurant, the bar and the shop keep opening hours: outside them the blinds are down, the " +
        "door's shutter is down and nobody stands at the windows; at night the glass stays dark. Houses " +
        "are homes and keep no hours."

/** The public buildings' group's line, with its switch interactive: the hours are the school's, the stations never close. */
private const val PUBLIC_HOURS_LINE =
    "These hours are the school's: outside them the blinds are down, the door's shutter is down and " +
        "nobody stands at the windows; at night the glass stays dark. " +
        "The fire station and the police station never close. Houses are homes and keep no hours."

/** The shops' or the public buildings' line while no building of the group stands to keep the hours. */
private const val STOREFRONT_HOURS_LOCKED_LINE =
    "Needs Show Buildings on, on the ${SettingsUiModel.TOWERS_PAGE} page; your choice is kept until then."

/** Under each group's two hours: kept to the minute (`BusinessHours`, decision 68). */
private const val HOURS_CLOCK_LINE =
    "At the opening time the lights come on and the people walk in; by the closing time the last have " +
        "walked out and the lights go off. The hours follow the scene's own clock, so a frozen time of " +
        "day obeys them too. Closing past midnight works (09:00 until 02:00). Setting both to the same " +
        "time means always open."

/** A decimal hour as the clock text the opening-hours sliders print, quantised to 15 minutes. */
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

/**
 * The distant houses (`DESIGN_NOTES.md` §12, decision 52): a switch, an amount, and a line saying their
 * colours come from another page. The switch follows decision 40 through
 * [SettingsUiModel.distantHouses]: off at 0 % with the tap bringing back 50 %, off with no mountain
 * layer on with the tap going to Mountains; the stored choice is never rewritten.
 */
@Composable
private fun DistantHousesSubScreen(
    customization: SceneCustomization,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onOpenMountains: () -> Unit,
    onOpenHouses: () -> Unit,
    onBack: () -> Unit,
) {
    val config = customization.distantHouses
    val mountains = mountainsShown(customization)
    val state = SettingsUiModel.distantHouses(config.visible, config.density, mountains)
    SettingsFormSubScreen("Distant houses", onBack) {
        SettingSwitchRow(
            title = "Show distant houses",
            subtitle = when {
                state.needsMountains ->
                    "They stand on the mountains, and no mountain is shown (both layers off or at 0%). Tap to open Mountains" +
                        if (config.visible) " - your choice is kept." else "."
                state.noneAtZero -> "None at 0% - tap to bring them back"
                else -> "Little houses far away on the mountains, with snow on their roofs when the winter " +
                    "palette is on and lit windows at night"
            },
            checked = state.shownOn,
            onCheckedChange = { wanted ->
                when (val tap = SettingsUiModel.distantHousesTap(wanted, config.visible, config.density, mountains)) {
                    is DistantHousesTap.SetVisible -> scope.launch { prefs.setDistantHousesVisible(tap.visible, forThemeId) }
                    is DistantHousesTap.Restore -> scope.launch {
                        prefs.setDistantHousesDensity(tap.amount, forThemeId)
                        prefs.setDistantHousesVisible(true, forThemeId)
                    }
                    is DistantHousesTap.OpenMountains -> {
                        if (tap.storeOn) scope.launch { prefs.setDistantHousesVisible(true, forThemeId) }
                        onOpenMountains()
                    }
                }
            },
        )
        PreferenceSlider(
            label = { shown -> Text("Amount: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = config.density,
            onCommit = { committed -> scope.launch { prefs.setDistantHousesDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = SettingsUiModel.amountSliderEnabled(config.visible) && mountains,
        )
        SettingsRow(
            title = "Colours: from Houses",
            supporting = "The distant houses wear the houses' Color 1 and Color 2, day and night. " +
                "Tap to change them in Houses.",
            onClick = onOpenHouses,
        )
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
 * The trees, and the Palms switch beside *Show Trees* and the density: the palms stand in the trees'
 * places, so it is off and locked while there is no tree to stand in, and on only where a palm stands
 * ([SettingsUiModel.palms]).
 */
@Composable
private fun TreesSubScreen(
    customization: SceneCustomization,
    themeHasPalms: Boolean,
    palmsOnlyFirs: Boolean,
    treesStand: Boolean,
    treesOnTheme: Boolean,
    /** Every tree the street keeps at 100 % would stand as a Christmas fir under the palms ([StandingObjects.onlyFirsEvenAtFull]). */
    onlyFirsEvenAtFull: Boolean,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Trees", onBack) {
        ObjectCategorySection(
            title = "Trees", config = customization.trees, category = ObjectCategory.TREES,
            forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false, noneAtZero = true, stands = treesStand, onTheme = treesOnTheme,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
            afterDensity = {
                // On every theme (`DESIGN_NOTES.md` §12, decision 39): on the two that plant palms it keeps
                // or removes them, on every other it puts palms where the ordinary trees stand, a Christmas
                // fir staying a fir. It starts off there (`SceneCustomization.palmsInsteadOfTrees`), and is
                // off and locked while no tree stands or every tree left is a Christmas fir; on a street with
                // no tree at all its line says so, since neither Show Trees nor the density would help, and where
                // every tree is a fir even at 100 % it names the firs alone.
                val palms = SettingsUiModel.palms(customization, themeHasPalms, onlyFirsWouldStand = palmsOnlyFirs, treesStand = treesStand)
                SettingSwitchRow(
                    title = "Palms",
                    subtitle = if (!palms.interactive && !treesOnTheme) {
                        SettingsUiModel.PALMS_NO_TREES_ON_THIS_THEME_LINE
                    } else if (!palms.interactive && palmsOnlyFirs && onlyFirsEvenAtFull) {
                        SettingsUiModel.PALMS_ONLY_FIRS_ON_THIS_THEME_LINE
                    } else if (!palms.interactive && palmsOnlyFirs) {
                        "Every tree left at this density is a Christmas fir, and the firs stay. Raise the " +
                            "density above or turn the Christmas firs off; your choice is kept until then."
                    } else if (!palms.interactive && customization.trees.visible && customization.trees.density > 0f) {
                        "No tree stands at this density on this theme, and palms stand where the trees do. Raise " +
                            "the density above; your choice is kept until then."
                    } else if (!palms.interactive) {
                        "Needs Show Trees above: palms stand where the trees do. Your choice is kept until then."
                    } else if (themeHasPalms) {
                        "Palm trees are this theme's own trees. Turn them off and it draws the same " +
                            "broadleaf trees as everywhere else - same places, same number, so the shore " +
                            "does not go bare."
                    } else if (customization.christmasFirsEnabled) {
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
 * Visibility and density for the pedestrians, and nothing else: not an [ObjectCategorySection], whose
 * four colour swatches nothing here would take. The people are recoloured by the scene itself, a new
 * combination at every crossing (`DESIGN_NOTES.md` §12, decision 30), not by a colour the user picks.
 */
@Composable
private fun PeopleSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    val config = customization.people
    SettingsFormSubScreen("People", onBack) {
        // With the day and the night density both at 0 % nobody walks at any hour: off, and said, the stored values kept;
        // a tap brings the day density back to the theme's own (`SettingsUiModel.people`).
        val peopleShown = SettingsUiModel.people(config.visible, config.density, customization.peopleNightDensity)
        SettingSwitchRow(
            title = "Show people",
            subtitle = if (config.visible && peopleShown.noneAtZero) {
                SettingsUiModel.NO_PEOPLE_AT_ZERO_LINE
            } else {
                "People walk along the ground between the buildings and the road, and dress for the season."
            },
            checked = peopleShown.shownOn,
            onCheckedChange = { wanted ->
                scope.applyAmountTap(
                    SettingsUiModel.peopleTap(wanted, config.density, customization.peopleNightDensity, defaultDensityOf(ObjectCategory.PEOPLE, forThemeId)),
                    setVisible = { prefs.setCategoryVisible(ObjectCategory.PEOPLE, it, forThemeId) },
                    setAmount = { prefs.setCategoryDensity(ObjectCategory.PEOPLE, it, forThemeId) },
                )
            },
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
private fun ParasolsSubScreen(customization: SceneCustomization, parasolsStand: Boolean, parasolsOnTheme: Boolean, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Parasols", onBack) {
        ObjectCategorySection(
            title = "Parasols", config = customization.parasols, category = ObjectCategory.PARASOLS,
            forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false, noneAtZero = true, stands = parasolsStand, onTheme = parasolsOnTheme,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
        // The umbrella this screen does *not* control, named (see the Parasols row).
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
        // **What the two ends do, said** (decision 45): at 0 % one car still drives (`CarSelection.countFor`
        // is 1 at the bottom), so the slider says "one car" there; and *Show Cars* off takes the road away
        // with the cars (`SceneObjectRenderer.drawsRoad`).
        ObjectCategorySection(
            title = "Cars", config = customization.cars, category = ObjectCategory.CARS,
            forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false,
            // Not one of the street's categories a theme can lack ([StandingObjects]): a plain switch.
            stands = true, onTheme = true,
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
        // With Halloween's night sky by day only night birds fly: off and locked unless Night Birds is on, which
        // stays a switch -- it is the way out.
        val byDay = SettingsUiModel.showBirds(customization)
        SettingSwitchRow(
            title = "Show Birds",
            subtitle = when {
                !byDay.interactive -> SettingsUiModel.NIGHT_BY_DAY_BIRDS_LINE
                birds.noneAtZero -> NONE_AT_ZERO_LINE
                else -> ""
            },
            checked = byDay.shownOn,
            enabled = byDay.interactive,
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
        // `PaperRenderer.drawBirds` returns before the night is asked while the birds are off: off and
        // locked then, the stored value kept, until the birds are back.
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
        // **Each colour's real share of the flock** (decision 45): the sliders are weights, so the label
        // works the share out from the weight being dragged and the other three as stored -- what
        // `BirdsConfig.pickColor` draws ([SettingsUiModel.birdColorShares]).
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
        // **At 0 % height there is no lake** (decision 45): off, and said, as every other amount at 0, and
        // a tap puts the theme's own height back. The wallpaper draws no water and nothing on it then
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
        // **The four controls below follow the lake**: with no water nothing floats on it
        // (`PaperRenderer.updateLakeBandY` returns before `drawLake` reaches the boats), so they are
        // greyed, say why, and **the stored values are not touched**. See [SettingsUiModel.lakeContents].
        val lakeContents = SettingsUiModel.lakeContents(
            lakeVisible = lake.shownOn,
            storedSailboatsVisible = customization.lake.sailboatsVisible,
            storedDolphinsVisible = customization.lake.dolphinsVisible,
        )
        // Worded like the "Realistic Moon Phases" line: what holds the control, how to release it, and
        // that the stored value is kept.
        val lakeOffNote = if (lake.noneAtZero && customization.lake.visible) {
            "The lake is at 0% height, so nothing floats on it. Raise Lake Height to set this; your " +
                "choice is kept until then."
        } else {
            "The lake is off, so nothing floats on it. Turn Show Lake on to set " +
                "this; your choice is kept until then."
        }
        // And at 0 % none floats: the switch reads off and says so.
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
 * The scene as it currently stands, at the top of the screen that edits it: **the theme gallery's
 * preview**, through the same [ThemeScenePreview] and [ThemePreviewGeometry], one preview system. The
 * day/night control is this call site's own: half the values edited below are night colours.
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
