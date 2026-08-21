package com.paperscrape.livewallpaper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.DirectionsWalk
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
import androidx.compose.material3.Card
import com.paperscrape.livewallpaper.engine.SceneTheme
import com.paperscrape.livewallpaper.engine.ThemePreviewGeometry
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.PrecipitationType
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.sunCloudHeightForFraction
import com.paperscrape.livewallpaper.engine.sunCloudHeightFraction
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.ObjectCategory
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Everything the scene is made of, plus how it moves.
 *
 * The fifteen categories and their sub-screens are v2.8's "Scene Objects", unchanged in content
 * and in every write. What changed: they are grouped the way the scene is built (sky, landscape,
 * things that move) instead of listed in arrival order with a divider after each row; each row
 * reports its own state, so the scene is readable without opening fifteen screens; and the four
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
    prefs: WallpaperPrefs,
    customThemeStore: CustomThemeStore,
    customThemeData: CustomThemeData,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var activeSection by remember { mutableStateOf<String?>(null) }
    val liveWeatherEnabled = settings.liveWeatherEnabled

    SettingsSubScreen(title = "World & scene", onBack = onBack) {
        WorldScenePreview(theme = theme, customization = customization)
        SettingsBanner(
            "These apply to $themeName, the theme showing now, and follow whichever theme you are on. " +
                "Keep this look by saving the theme from Advanced & about.",
        )

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
                supporting = densitySummary(customization.stars.visible, customization.stars.density),
                icon = Icons.Outlined.StarBorder,
                onClick = { activeSection = "stars" },
            )
            SettingsNavigationRow(
                title = "Clouds",
                supporting = if (liveWeatherEnabled) {
                    "Driven by Live Weather"
                } else {
                    densitySummary(customization.clouds.visible, customization.clouds.density)
                },
                icon = Icons.Outlined.Cloud,
                supportingIsAccent = liveWeatherEnabled,
                onClick = { activeSection = "clouds" },
            )
            SettingsNavigationRow(
                title = "Rain and snow",
                supporting = if (liveWeatherEnabled) {
                    "Driven by Live Weather"
                } else {
                    densitySummary(customization.precipitation.visible, customization.precipitation.intensity)
                },
                icon = Icons.Outlined.WaterDrop,
                supportingIsAccent = liveWeatherEnabled,
                onClick = { activeSection = "precipitation" },
            )
            SettingsNavigationRow(
                title = "Rainbow",
                supporting = onOffSummary(customization.rainbow.visible, "Rainbow"),
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
                supporting = densitySummary(customization.trees.visible, customization.trees.density),
                icon = Icons.Outlined.Park,
                onClick = { activeSection = "trees" },
            )
            SettingsNavigationRow(
                title = "Umbrellas",
                supporting = densitySummary(customization.parasols.visible, customization.parasols.density),
                icon = Icons.Outlined.FilterDrama,
                onClick = { activeSection = "umbrellas" },
            )
            SettingsNavigationRow(
                title = "Lake",
                supporting = "Water, sailboats and dolphins",
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
                icon = Icons.Outlined.DirectionsWalk,
                onClick = { activeSection = "people" },
            )
            SettingsNavigationRow(
                title = "Birds",
                supporting = densitySummary(customization.birds.visible, customization.birds.density),
                icon = Icons.Filled.Air,
                onClick = { activeSection = "birds" },
            )
        }

        SettingsSectionHeader("Motion")
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
                supporting = "How far apart near and far layers move relative to each other while scrolling.",
                value = settings.parallaxStrength,
                onCommit = { committed -> scope.launch { prefs.setParallaxStrength(committed) } },
                valueRange = 0.5f..2f,
            )
            SettingsSwitchRow(
                title = "Scroll the background too",
                supporting = "Whether the sky, sun and moon scroll as well, or stay fixed while only the ground moves",
                checked = settings.scrollBackground,
                onCheckedChange = { scope.launch { prefs.setScrollBackground(it) } },
            )
            SettingsSwitchRow(
                title = "Swipe scroll",
                supporting = "Whether swiping between home screens also scrolls the wallpaper",
                icon = Icons.Filled.SwipeLeft,
                checked = settings.swipeScroll,
                onCheckedChange = { scope.launch { prefs.setSwipeScroll(it) } },
            )
        }
        SettingsCaption("Motion applies to every theme, not only this one.")

        OutlinedButton(
            onClick = {
                scope.launch {
                    prefs.resetAllCategories()
                    // "Reset everything to defaults" clearing only the in-progress scratch edit
                    // wasn't enough on its own: resolveActiveCustomization() checks a *saved*
                    // override for this theme *before* the scratch space, so if this built-in
                    // theme was ever overridden, the reset appeared to do nothing at all -- the
                    // saved override kept winning. Clear that too, if one exists.
                    if (customThemeData.overrides.containsKey(forThemeId)) {
                        customThemeStore.clearOverride(forThemeId)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
        ) {
            Text("Reset this theme's scene to defaults")
        }
    }

    when (activeSection) {
        "sunmoon" -> SunMoonSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "sky" -> SkySubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "stars" -> StarsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "clouds" -> CloudsSubScreen(customization, forThemeId, prefs, scope, liveWeatherEnabled) { activeSection = null }
        "precipitation" -> PrecipitationSubScreen(customization, forThemeId, prefs, scope, liveWeatherEnabled) { activeSection = null }
        "rainbow" -> RainbowSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "cities" -> CitiesSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "hills" -> HillsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "mountains" -> MountainsSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "trees" -> TreesSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
        "umbrellas" -> UmbrellasSubScreen(customization, forThemeId, prefs, scope) { activeSection = null }
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

// ---------------------------------------------------------------------------------------------
// Category screens -- unchanged from v2.8 apart from the shell they are drawn in
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
        SettingSwitchRow(
            title = "Realistic Moon Phases", subtitle = "Show real moon phases at night",
            checked = customization.moon.realisticPhases,
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
        // Shown as a plain 0-100%, like every other slider, and mapped onto the arc-height range
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
        ColorSwatchRow("Day Color High", customization.sky.colorDayHigh) {
            editingTarget = ColorEditTarget("Sky - Day Color High", customization.sky.colorDayHigh) { c -> scope.launch { prefs.setSkyColorDayHigh(c, forThemeId) } }
        }
        ColorSwatchRow("Day Color Low", customization.sky.colorDayLow) {
            editingTarget = ColorEditTarget("Sky - Day Color Low", customization.sky.colorDayLow) { c -> scope.launch { prefs.setSkyColorDayLow(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color High", customization.sky.colorNightHigh) {
            editingTarget = ColorEditTarget("Sky - Night Color High", customization.sky.colorNightHigh) { c -> scope.launch { prefs.setSkyColorNightHigh(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color Low", customization.sky.colorNightLow) {
            editingTarget = ColorEditTarget("Sky - Night Color Low", customization.sky.colorNightLow) { c -> scope.launch { prefs.setSkyColorNightLow(c, forThemeId) } }
        }
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
        SettingSwitchRow(
            title = "Show Stars", subtitle = "",
            checked = customization.stars.visible,
            onCheckedChange = { scope.launch { prefs.setStarsVisible(it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("# of Stars: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.stars.density,
            onCommit = { committed -> scope.launch { prefs.setStarsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun CloudsSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, liveWeatherEnabled: Boolean, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Clouds", onBack) {
        // Live Weather (Weather & time) fully drives cloud density from real conditions while it
        // is on -- see PaperRenderer.drawClouds' own doc comment on exactly how that override
        // works. Visibility/density read-only here so a manual edit can't silently do nothing (or
        // worse, look like it worked and then get overwritten on the next hourly fetch); colors
        // stay editable since Live Weather never touches those.
        if (liveWeatherEnabled) {
            Text(
                "Live Weather is on, so cloud density is driven by real conditions. Turn Live Weather off in Weather & time to set this manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingSwitchRow(
            title = "Show Clouds", subtitle = "",
            checked = customization.clouds.visible,
            enabled = !liveWeatherEnabled,
            onCheckedChange = { scope.launch { prefs.setCloudsVisible(it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("# of Clouds: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.clouds.density,
            onCommit = { committed -> scope.launch { prefs.setCloudsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = !liveWeatherEnabled,
        )
        ColorSwatchRow("Day Color", customization.clouds.colorDay) {
            editingTarget = ColorEditTarget("Clouds - Day Color", customization.clouds.colorDay) { c -> scope.launch { prefs.setCloudsColorDay(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color", customization.clouds.colorNight) {
            editingTarget = ColorEditTarget("Clouds - Night Color", customization.clouds.colorNight) { c -> scope.launch { prefs.setCloudsColorNight(c, forThemeId) } }
        }
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun PrecipitationSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, liveWeatherEnabled: Boolean, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    val precip = customization.precipitation
    SettingsFormSubScreen("Rain and snow", onBack) {
        // See CloudsSubScreen's own comment on this same pattern -- Live Weather fully drives
        // visibility/type/intensity/thunderstorm here (PaperRenderer.drawPrecipitation's own doc
        // comment), so those controls are read-only while it is on. Colors stay editable.
        if (liveWeatherEnabled) {
            Text(
                "Live Weather is on, so rain/snow/thunderstorm are driven by real conditions. Turn Live Weather off in Weather & time to set this manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingSwitchRow(
            title = "Show Rain/Snow", subtitle = "",
            checked = precip.visible,
            enabled = !liveWeatherEnabled,
            onCheckedChange = { scope.launch { prefs.setPrecipitationVisible(it, forThemeId) } },
        )
        Text("Type", style = MaterialTheme.typography.bodyMedium)
        SettingsSegmentedChoice(
            options = listOf("Rain", "Snow"),
            selectedIndex = if (precip.type == PrecipitationType.SNOW) 1 else 0,
            enabled = !liveWeatherEnabled,
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
            enabled = !liveWeatherEnabled,
        )
        SectionTitle("Rain Colors")
        ColorSwatchRow("Day Color", precip.rainColorDay) {
            editingTarget = ColorEditTarget("Rain - Day Color", precip.rainColorDay) { c -> scope.launch { prefs.setPrecipitationRainColorDay(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color", precip.rainColorNight) {
            editingTarget = ColorEditTarget("Rain - Night Color", precip.rainColorNight) { c -> scope.launch { prefs.setPrecipitationRainColorNight(c, forThemeId) } }
        }
        SectionTitle("Snow Colors")
        ColorSwatchRow("Day Color", precip.snowColorDay) {
            editingTarget = ColorEditTarget("Snow - Day Color", precip.snowColorDay) { c -> scope.launch { prefs.setPrecipitationSnowColorDay(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color", precip.snowColorNight) {
            editingTarget = ColorEditTarget("Snow - Night Color", precip.snowColorNight) { c -> scope.launch { prefs.setPrecipitationSnowColorNight(c, forThemeId) } }
        }
        SectionTitle("Storms")
        SettingSwitchRow(
            title = "Thunderstorm", subtitle = "Occasional lightning flashes (only while Rain is selected)",
            checked = precip.thunderstorm,
            enabled = !liveWeatherEnabled,
            onCheckedChange = { scope.launch { prefs.setPrecipitationThunderstorm(it, forThemeId) } },
        )
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
        SettingSwitchRow(
            title = "Show Rainbow", subtitle = "",
            checked = rainbow.visible,
            onCheckedChange = { scope.launch { prefs.setRainbowVisible(it, forThemeId) } },
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
        )
    }
}

@Composable
private fun CitiesSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Cities", onBack) {
        ObjectCategorySection(
            title = "Houses", config = customization.houses, category = ObjectCategory.HOUSES,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
        ObjectCategorySection(
            title = "Buildings", config = customization.buildings, category = ObjectCategory.BUILDINGS,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun HillsSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Hills", onBack) {
        ColorSwatchRow("Day Color", customization.hillsColorDay) {
            editingTarget = ColorEditTarget("Hills - Day Color", customization.hillsColorDay) { c -> scope.launch { prefs.setHillsColorDay(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color", customization.hillsColorNight) {
            editingTarget = ColorEditTarget("Hills - Night Color", customization.hillsColorNight) { c -> scope.launch { prefs.setHillsColorNight(c, forThemeId) } }
        }
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

@Composable
private fun TreesSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Trees", onBack) {
        ObjectCategorySection(
            title = "Trees", config = customization.trees, category = ObjectCategory.TREES,
            forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
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
 * Deliberately not an [ObjectCategorySection]: that lays out four colour swatches, and the walk
 * sprites are finished art in four kinds across two seasons with nothing for a colour to reach.
 * Offering swatches that did nothing would be worse than offering none.
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
        )
        PreferenceSlider(
            label = { shown -> Text("Night density: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.peopleNightDensity,
            onCommit = { committed -> scope.launch { prefs.setPeopleNightDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
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
private fun UmbrellasSubScreen(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SettingsFormSubScreen("Umbrellas", onBack) {
        ObjectCategorySection(
            title = "Umbrellas", config = customization.parasols, category = ObjectCategory.PARASOLS,
            forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
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
        ObjectCategorySection(
            title = "Cars", config = customization.cars, category = ObjectCategory.CARS,
            forThemeId = forThemeId, prefs = prefs, scope = scope, showTitle = false,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
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
        SettingSwitchRow(
            title = "Show Birds", subtitle = "",
            checked = customization.birds.visible,
            onCheckedChange = { scope.launch { prefs.setBirdsVisible(it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("# of Birds: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.birds.density,
            onCommit = { committed -> scope.launch { prefs.setBirdsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
        )
        SettingSwitchRow(
            title = "Night Birds", subtitle = "Allow birds to fly at night",
            checked = customization.birds.nightBirds,
            onCheckedChange = { scope.launch { prefs.setBirdsNight(it, forThemeId) } },
        )
        SectionTitle("Bird Colors")
        customization.birds.colors.forEachIndexed { index, colorWeight ->
            ColorSwatchRow("Bird Color ${index + 1}", colorWeight.color) {
                editingTarget = ColorEditTarget("Bird Color ${index + 1}", colorWeight.color) { c -> scope.launch { prefs.setBirdColor(index, c, forThemeId) } }
            }
        }
        Text(
            "Bird Color Frequencies - change how often each color appears",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        customization.birds.colors.forEachIndexed { index, colorWeight ->
            PreferenceSlider(
                label = { shown -> Text("Color ${index + 1}: ${(shown * 100).toInt()}", style = MaterialTheme.typography.bodySmall) },
                value = colorWeight.weight,
                onCommit = { committed -> scope.launch { prefs.setBirdWeight(index, committed, forThemeId) } },
                valueRange = 0f..1f,
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
        SettingSwitchRow(
            title = "Show Lake", subtitle = "A body of water in the middle distance",
            checked = customization.lake.visible,
            onCheckedChange = { scope.launch { prefs.setLakeVisible(it, forThemeId) } },
        )
        ColorSwatchRow("Day Color", customization.lake.colorDay) {
            editingTarget = ColorEditTarget("Lake - Day Color", customization.lake.colorDay) { c -> scope.launch { prefs.setLakeColorDay(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color", customization.lake.colorNight) {
            editingTarget = ColorEditTarget("Lake - Night Color", customization.lake.colorNight) { c -> scope.launch { prefs.setLakeColorNight(c, forThemeId) } }
        }
        PreferenceSlider(
            label = { shown -> Text("Lake Height: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.lake.height,
            onCommit = { committed -> scope.launch { prefs.setLakeHeight(committed, forThemeId) } },
            valueRange = 0f..1f,
        )
        SettingSwitchRow(
            title = "Show Sailboats", subtitle = "",
            checked = customization.lake.sailboatsVisible,
            onCheckedChange = { scope.launch { prefs.setLakeSailboatsVisible(it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("# of Sailboats: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.lake.sailboatsDensity,
            onCommit = { committed -> scope.launch { prefs.setLakeSailboatsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
        )
        SettingSwitchRow(
            title = "Show Dolphins", subtitle = "",
            checked = customization.lake.dolphinsVisible,
            onCheckedChange = { scope.launch { prefs.setLakeDolphinsVisible(it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("# of Dolphins: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.lake.dolphinsDensity,
            onCommit = { committed -> scope.launch { prefs.setLakeDolphinsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
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
