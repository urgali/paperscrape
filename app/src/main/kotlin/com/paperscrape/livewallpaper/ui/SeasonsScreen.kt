package com.paperscrape.livewallpaper.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Egg
import androidx.compose.material.icons.outlined.LocalFlorist
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
import com.paperscrape.livewallpaper.engine.ObjectVariantConfig
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.prefs.ObjectCategory
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The five seasons decorations are grouped under. Presentation only; no flag knows about it. */
private enum class Season(val title: String) {
    WINTER("Winter"),
    CHRISTMAS("Christmas"),
    HALLOWEEN("Halloween"),
    EASTER("Easter"),
    SPRING("Spring"),
}

/**
 * Seasonal palette and decorations for the theme currently showing.
 *
 * v2.8 put all of this on one flat screen: two palette switches that silently cancelled each
 * other, a "Christmas" heading with the Flowers switch underneath it (a spring decoration), a
 * "Halloween" heading, and then six fully expanded category blocks -- roughly sixty controls in
 * one scroll, with each block's season named thousands of pixels above it.
 *
 * The flags are untouched. The palette is one choice because the two flags were always mutually
 * exclusive (see [SettingsUiModel]); each decoration keeps its own switch, in the season a user
 * would look for it under; and density and colours are one level down, which is how every Scene
 * Objects category has always worked.
 */
@Composable
internal fun SeasonsScreen(
    customization: SceneCustomization,
    forThemeId: String,
    themeName: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var openSeason by remember { mutableStateOf<Season?>(null) }
    val palette = SettingsUiModel.seasonalPalette(customization.fallColorsEnabled, customization.winterColorsEnabled)

    SettingsSubScreen(title = "Seasons & decorations", onBack = onBack) {
        SettingsBanner(
            "These apply to $themeName, the theme showing now, and follow whichever theme you are on. " +
                "Keep an edit permanently by saving the theme from Advanced & about.",
        )

        SettingsSectionHeader("Seasonal palette")
        SettingsGroup {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                SettingsSegmentedChoice(
                    options = listOf("None", "Autumn", "Winter"),
                    selectedIndex = palette.ordinal,
                    onSelect = { index ->
                        // Exactly the two setters the two switches called, and nothing else --
                        // each one already clears the other, which is why the third state is
                        // "both off" rather than a third flag.
                        when (SeasonalPalette.entries[index]) {
                            SeasonalPalette.NONE -> scope.launch {
                                prefs.setFallColorsEnabled(false, forThemeId)
                                prefs.setWinterColorsEnabled(false, forThemeId)
                            }
                            SeasonalPalette.AUTUMN -> scope.launch { prefs.setFallColorsEnabled(true, forThemeId) }
                            SeasonalPalette.WINTER -> scope.launch { prefs.setWinterColorsEnabled(true, forThemeId) }
                        }
                    },
                )
                Text(
                    "Autumn turns the trees to autumn tones with leaves drifting down. Winter settles snow on " +
                        "trees and rooftops and dresses people for the cold. One at a time - Christmas lights " +
                        "are separate and work with either.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        }

        SettingsSectionHeader("Decorations")
        SettingsGroup {
            SeasonRow(Season.WINTER, Icons.Outlined.AcUnit, listOf(
                "Snowmen" to customization.snowmen.visible,
                "Penguins" to customization.penguins.visible,
            )) { openSeason = Season.WINTER }
            SeasonRow(Season.CHRISTMAS, Icons.Outlined.CardGiftcard, listOf(
                "Christmas lights" to customization.christmasDecorationsEnabled,
                "Santa" to customization.santaEnabled,
                "Gifts" to customization.gifts.visible,
            )) { openSeason = Season.CHRISTMAS }
            SeasonRow(Season.HALLOWEEN, Icons.Outlined.DarkMode, listOf(
                "Halloween" to customization.halloweenEnabled,
                "Horror sky" to customization.horrorSkyEnabled,
                "Pumpkins" to customization.pumpkins.visible,
            )) { openSeason = Season.HALLOWEEN }
            SeasonRow(Season.EASTER, Icons.Outlined.Egg, listOf(
                "Bunnies" to customization.bunnies.visible,
                "Eggs" to customization.easterEggs.visible,
            )) { openSeason = Season.EASTER }
            SeasonRow(Season.SPRING, Icons.Outlined.LocalFlorist, listOf(
                "Flowers" to customization.flowersEnabled,
            )) { openSeason = Season.SPRING }
        }

        OutlinedButton(
            onClick = {
                scope.launch {
                    // Only the 6 seasonal categories plus the palettes and Santa -- byte for byte
                    // the same reset v2.8 performed from this screen. resetAllCategories() would
                    // also wipe this theme's houses/trees/etc, which is not what "reset" means
                    // here; that one lives on World & scene.
                    for (category in listOf(
                        ObjectCategory.SNOWMEN, ObjectCategory.GIFTS,
                        ObjectCategory.PENGUINS, ObjectCategory.BUNNIES, ObjectCategory.EASTER_EGGS,
                        ObjectCategory.PUMPKINS,
                    )) {
                        prefs.resetCategory(category, forThemeId)
                    }
                    prefs.resetSeasonalPalettes(forThemeId)
                    prefs.resetSanta(forThemeId)
                }
            },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
        ) {
            Text("Reset decorations to defaults")
        }
    }

    openSeason?.let { season ->
        SeasonDetailScreen(
            season = season,
            customization = customization,
            forThemeId = forThemeId,
            prefs = prefs,
            scope = scope,
            onBack = { openSeason = null },
        )
    }
}

@Composable
private fun SeasonRow(
    season: Season,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contents: List<Pair<String, Boolean>>,
    onClick: () -> Unit,
) {
    val onCount = contents.count { it.second }
    SettingsNavigationRow(
        title = season.title,
        supporting = contents.joinToString(", ") { it.first } + if (onCount == 0) " - all off" else " - $onCount on",
        icon = icon,
        supportingIsAccent = onCount > 0,
        onClick = onClick,
    )
}

/**
 * One season's own screen: its switches, and a way into the density/colour options of any
 * decoration that has them.
 *
 * No per-season reset: the only two resets this area has ever had are the whole-screen one on
 * [SeasonsScreen] and each category's own "Reset X to default" inside its options, and inventing
 * a third would change what "reset" means here.
 */
@Composable
private fun SeasonDetailScreen(
    season: Season,
    customization: SceneCustomization,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var openOptions by remember { mutableStateOf<ObjectCategory?>(null) }

    SettingsSubScreen(title = season.title, onBack = onBack) {
        SettingsGroup(modifier = Modifier.padding(top = 12.dp)) {
            when (season) {
                Season.WINTER -> {
                    DecorationRows("Snowmen", customization.snowmen, ObjectCategory.SNOWMEN, forThemeId, prefs, scope) { openOptions = it }
                    DecorationRows("Penguins", customization.penguins, ObjectCategory.PENGUINS, forThemeId, prefs, scope) { openOptions = it }
                }
                Season.CHRISTMAS -> {
                    SettingsSwitchRow(
                        title = "Christmas lights",
                        supporting = "Blinking lights on the trees. Independent of the seasonal palette - you can have one without the other.",
                        checked = customization.christmasDecorationsEnabled,
                        onCheckedChange = { scope.launch { prefs.setChristmasDecorationsEnabled(it, forThemeId) } },
                    )
                    SettingsSwitchRow(
                        title = "Santa",
                        supporting = "Santa's sleigh occasionally flies across the sky dropping gifts",
                        checked = customization.santaEnabled,
                        onCheckedChange = { scope.launch { prefs.setSantaEnabled(it, forThemeId) } },
                    )
                    DecorationRows("Gifts", customization.gifts, ObjectCategory.GIFTS, forThemeId, prefs, scope) { openOptions = it }
                }
                Season.HALLOWEEN -> {
                    SettingsSwitchRow(
                        title = "Halloween",
                        supporting = "A carved jack-o'-lantern moon, and every tree stripped to bare branches",
                        checked = customization.halloweenEnabled,
                        onCheckedChange = { scope.launch { prefs.setHalloweenEnabled(it, forThemeId) } },
                    )
                    SettingsSwitchRow(
                        title = "Horror sky",
                        supporting = "Near-black overhead with a hard orange horizon. A separate switch, so you can have either on its own.",
                        checked = customization.horrorSkyEnabled,
                        onCheckedChange = { scope.launch { prefs.setHorrorSkyEnabled(it, forThemeId) } },
                    )
                    DecorationRows("Pumpkins", customization.pumpkins, ObjectCategory.PUMPKINS, forThemeId, prefs, scope) { openOptions = it }
                }
                Season.EASTER -> {
                    DecorationRows("Easter Bunnies", customization.bunnies, ObjectCategory.BUNNIES, forThemeId, prefs, scope) { openOptions = it }
                    DecorationRows("Easter Eggs", customization.easterEggs, ObjectCategory.EASTER_EGGS, forThemeId, prefs, scope) { openOptions = it }
                }
                Season.SPRING -> {
                    SettingsSwitchRow(
                        title = "Flowers",
                        supporting = "Wildflowers scattered on the open ground. On by default in Spring; available on any theme.",
                        checked = customization.flowersEnabled,
                        onCheckedChange = { scope.launch { prefs.setFlowersEnabled(it, forThemeId) } },
                    )
                }
            }
        }
        SettingsCaption(
            when (season) {
                Season.WINTER -> "Snow on trees, roofs and clothing is the Winter palette, on the previous screen."
                Season.CHRISTMAS -> "Christmas lights work with any palette, including none."
                Season.HALLOWEEN -> "Halloween and Horror sky are independent of each other and of the palette."
                Season.EASTER -> "Both are available on any theme, not only the Easter one."
                Season.SPRING -> "Flowers are available on any theme, not only the Spring one."
            },
        )
    }

    openOptions?.let { category ->
        DecorationOptionsScreen(
            category = category,
            customization = customization,
            forThemeId = forThemeId,
            prefs = prefs,
            scope = scope,
            onBack = { openOptions = null },
        )
    }
}

/** A decoration that has density and colours: its switch, then a way into those. */
@Composable
private fun DecorationRows(
    title: String,
    config: ObjectVariantConfig,
    category: ObjectCategory,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onOpenOptions: (ObjectCategory) -> Unit,
) {
    SettingsSwitchRow(
        title = title,
        supporting = "Density ${(config.density * 100).toInt()}%",
        checked = config.visible,
        onCheckedChange = { scope.launch { prefs.setCategoryVisible(category, it, forThemeId) } },
    )
    SettingsNavigationRow(
        title = "$title options",
        supporting = "Density and colours",
        icon = Icons.Filled.Tune,
        onClick = { onOpenOptions(category) },
    )
}

/** The v2.8 category block, unchanged, on a screen of its own. */
@Composable
private fun DecorationOptionsScreen(
    category: ObjectCategory,
    customization: SceneCustomization,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    val (title, config) = when (category) {
        ObjectCategory.SNOWMEN -> "Snowmen" to customization.snowmen
        ObjectCategory.GIFTS -> "Gifts" to customization.gifts
        ObjectCategory.PENGUINS -> "Penguins" to customization.penguins
        ObjectCategory.BUNNIES -> "Easter Bunnies" to customization.bunnies
        ObjectCategory.EASTER_EGGS -> "Easter Eggs" to customization.easterEggs
        ObjectCategory.PUMPKINS -> "Pumpkins" to customization.pumpkins
        else -> "Decoration" to customization.snowmen
    }
    SettingsFormSubScreen(title = title, onBack = onBack) {
        ObjectCategorySection(
            title = title,
            config = config,
            category = category,
            forThemeId = forThemeId,
            prefs = prefs,
            scope = scope,
            showTitle = false,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(
            title = target.label,
            initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null },
            onDismiss = { editingTarget = null },
        )
    }
}
