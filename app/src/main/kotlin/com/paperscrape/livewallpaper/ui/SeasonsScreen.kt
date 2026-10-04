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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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

/**
 * The five seasons decorations are grouped under. Presentation only; no flag knows about it.
 *
 * There was a sixth, Summer, and the Palms switch was all it held. The maintainer moved the switch
 * to the Trees page of World & scene on 2026-10-03 (*«il flag non deve stare in summer ma in
 * tree»*, v5.10C2): the palms stand in the trees' places, so they are set where the trees are. A
 * Summer row left behind would have counted nothing and opened an empty page.
 */
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
    // "Reset decorations to defaults" asks first, as the scene's reset does (v5.10E, inventory I-214).
    var confirmDecorationsReset by remember { mutableStateOf(false) }
    val palette = SettingsUiModel.seasonalPalette(customization.fallColorsEnabled, customization.winterColorsEnabled)

    SettingsSubScreen(title = "Seasons & decorations", onBack = onBack) {
        // What happens to an edit, said as it is (v5.10E, inventory I-219): kept with this theme.
        SettingsBanner(SettingsUiModel.themeEditsBanner(themeName))

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
                // Each slider appears only under the palette it belongs to, rather than sitting
                // greyed out under the other one: snow on an autumn lawn is not a setting a user
                // should have to find in order to leave alone.
                when (palette) {
                    SeasonalPalette.WINTER -> {
                        Text(
                            "Drifts of settled snow on the open ground. None at all at 0%.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        PreferenceSlider(
                            label = { shown ->
                                Text("Snow piles: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                            },
                            value = customization.snowPiles,
                            onCommit = { committed -> scope.launch { prefs.setSnowPiles(committed, forThemeId) } },
                            valueRange = 0f..1f,
                        )
                    }
                    SeasonalPalette.AUTUMN -> {
                        Text(
                            "Heaps of fallen leaves on the open ground. Separate from the leaves drifting " +
                                "down off the trees. None at all at 0%.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                        PreferenceSlider(
                            label = { shown ->
                                Text("Leaf piles: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                            },
                            value = customization.leafPiles,
                            onCommit = { committed -> scope.launch { prefs.setLeafPiles(committed, forThemeId) } },
                            valueRange = 0f..1f,
                        )
                    }
                    SeasonalPalette.NONE -> {}
                }
            }
        }

        SettingsSectionHeader("Decorations")
        SettingsGroup {
            // Each decoration as its switch shows it: at 0 % density none is drawn, so it is off
            // here too (v5.10C, row 8).
            fun shown(c: ObjectVariantConfig) = SettingsUiModel.amountSwitch(c.visible, c.density).shownOn
            SeasonRow(Season.WINTER, Icons.Outlined.AcUnit, listOf(
                "Snowmen" to shown(customization.snowmen),
                "Penguins" to shown(customization.penguins),
            )) { openSeason = Season.WINTER }
            SeasonRow(Season.CHRISTMAS, Icons.Outlined.CardGiftcard, listOf(
                "Christmas lights" to customization.christmasDecorationsEnabled,
                "Santa" to customization.santaEnabled,
                "Gifts" to shown(customization.gifts),
            )) { openSeason = Season.CHRISTMAS }
            SeasonRow(Season.HALLOWEEN, Icons.Outlined.DarkMode, listOf(
                "Halloween" to customization.halloweenEnabled,
                "Horror sky" to customization.horrorSkyEnabled,
                "Pumpkins" to shown(customization.pumpkins),
            )) { openSeason = Season.HALLOWEEN }
            SeasonRow(Season.EASTER, Icons.Outlined.Egg, listOf(
                "Bunnies" to shown(customization.bunnies),
                "Eggs" to shown(customization.easterEggs),
            )) { openSeason = Season.EASTER }
            SeasonRow(Season.SPRING, Icons.Outlined.LocalFlorist, listOf(
                "Flowers" to customization.flowersEnabled,
            )) { openSeason = Season.SPRING }
        }

        OutlinedButton(
            onClick = { confirmDecorationsReset = true },
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 24.dp),
        ) {
            Text("Reset decorations to defaults")
        }
    }

    if (confirmDecorationsReset) {
        // **What this screen shows, and nothing else** (v5.10E, inventory I-214 and I-294): the palette
        // and the piles under it, the five seasons' decorations with their densities and colours, and
        // their switches -- in one write (`WallpaperPrefs.resetDecorations`). Until v5.10E the button
        // reset without asking, left the snow and leaf piles where they were, and took the palms, whose
        // switch is on the Trees page of World & scene since v5.10C2: "Reset Trees to default" takes
        // them now. resetAllCategories() would also wipe this theme's houses, trees and the rest, which
        // is not what "reset" means here; that one lives on World & scene.
        AlertDialog(
            onDismissRequest = { confirmDecorationsReset = false },
            title = { Text("Reset $themeName's decorations?") },
            text = { Text(SettingsUiModel.decorationsResetMessage(themeName)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { prefs.resetDecorations(forThemeId) }
                    confirmDecorationsReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmDecorationsReset = false }) { Text("Cancel") } },
        )
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

/**
 * One season's row: its decorations, and how many of them the wallpaper is drawing. Each pair's
 * flag is what the switch shows, not what is stored -- a decoration at 0 % density counts as off,
 * as its switch does (v5.10C, [SettingsUiModel.amountSwitch]).
 *
 * It had a second form until v5.10C, a sentence in place of the count for a season whose
 * decorations could not appear on the theme at all: the palms, which grew only where the layout
 * planted them. They can be switched on anywhere now, and since v5.10C2 from the Trees page.
 */
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
                        // What the switch does, all of it (v5.10E, inventory I-224): the lights on the
                        // trees *and* the strings along the buildings' windowsills, and one tree in three
                        // standing as a Christmas fir with presents at its foot
                        // (`SceneObjectRenderer.standsAsFir`), on any theme. Until v5.10E it said only
                        // "Blinking lights on the trees".
                        supporting = CHRISTMAS_LIGHTS_LINE,
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

/**
 * The line under *Christmas lights* (v5.10E, inventory I-224): everything the switch does --
 * `SceneObjectRenderer`'s tree, palm and fir lights, the strings on the buildings' sills
 * (`drawNeighbourhoodBuilding`), and the firs.
 */
internal const val CHRISTMAS_LIGHTS_LINE =
    "Blinking lights on the trees and along the windowsills, and one tree in three becomes a " +
        "Christmas fir with presents at its foot. Independent of the seasonal palette - you can have " +
        "one without the other."

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
    // At 0 % density none is drawn: off, and said, and a tap brings them back at the theme's own
    // density (v5.10C, row 8; inventory I-211).
    val shown = SettingsUiModel.amountSwitch(config.visible, config.density)
    SettingsSwitchRow(
        title = title,
        supporting = if (shown.noneAtZero) NONE_AT_ZERO_LINE else "Density ${(config.density * 100).toInt()}%",
        checked = shown.shownOn,
        onCheckedChange = { wanted ->
            scope.applyAmountTap(
                SettingsUiModel.amountTap(wanted, config.density, defaultDensityOf(category, forThemeId)),
                setVisible = { prefs.setCategoryVisible(category, it, forThemeId) },
                setAmount = { prefs.setCategoryDensity(category, it, forThemeId) },
            )
        },
    )
    SettingsNavigationRow(
        title = "$title options",
        supporting = "Density and colours",
        icon = Icons.Filled.Tune,
        onClick = { onOpenOptions(category) },
    )
}

/**
 * The v2.8 category block on a screen of its own: density, colours and the category's own reset.
 *
 * **Without the "Show X" switch**, which [DecorationRows] has already put on the season's screen
 * one tap back, against the same `config.visible` and the same `setCategoryVisible`. Two switches
 * for one flag is not a choice, and the row that leads here says what this screen is for --
 * "Density and colours".
 */
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
            showVisibilitySwitch = false,
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
