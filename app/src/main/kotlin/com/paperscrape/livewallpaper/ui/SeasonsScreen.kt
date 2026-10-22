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
                        "trees and rooftops and dresses people for the cold. One at a time - the Christmas lights " +
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
                "Lights on the buildings" to SettingsUiModel.christmasBuildingDecoration(customization.christmasBuildingLightsEnabled, customization).shownOn,
                "Lights on the trees" to SettingsUiModel.christmasTreeDecoration(customization.christmasTreeLightsEnabled, customization).shownOn,
                "Christmas firs" to SettingsUiModel.christmasTreeDecoration(customization.christmasFirsEnabled, customization).shownOn,
                "Santa" to customization.santaEnabled,
                "Gifts" to shown(customization.gifts),
            )) { openSeason = Season.CHRISTMAS }
            SeasonRow(Season.HALLOWEEN, Icons.Outlined.DarkMode, listOf(
                "Halloween" to customization.halloweenEnabled,
                "Laughing moon" to SettingsUiModel.laughingMoon(customization).shownOn,
                "Night sky by day" to SettingsUiModel.nightSkyByDay(customization).shownOn,
                "Horror sky" to customization.horrorSkyEnabled,
                "Halloween decorations" to SettingsUiModel.halloweenDecorations(customization).shownOn,
                "Pumpkins" to shown(customization.pumpkins),
                "Scarecrows" to shown(customization.scarecrows),
                "Headless Horseman" to customization.headlessHorsemanEnabled,
                "Blood rain" to customization.bloodRainEnabled,
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
                    // The three switches *Christmas lights* became (v5.16, the maintainer's *«interruttori per ogni cosa;
                    // lo stesso dobbiamo avere anche per natale»*, 2026-10-21), each line all it does; each greyed with the
                    // reason while the Halloween decorations are on (`DecorationExclusion`: the first on wins).
                    ChristmasDecorationRow(
                        title = "Lights on the buildings",
                        line = CHRISTMAS_BUILDING_LIGHTS_LINE,
                        state = SettingsUiModel.christmasBuildingDecoration(customization.christmasBuildingLightsEnabled, customization),
                        onCheckedChange = { scope.launch { prefs.setChristmasBuildingLightsEnabled(it, forThemeId) } },
                    )
                    ChristmasDecorationRow(
                        title = "Lights on the trees",
                        line = CHRISTMAS_TREE_LIGHTS_LINE,
                        state = SettingsUiModel.christmasTreeDecoration(customization.christmasTreeLightsEnabled, customization),
                        onCheckedChange = { scope.launch { prefs.setChristmasTreeLightsEnabled(it, forThemeId) } },
                    )
                    ChristmasDecorationRow(
                        title = "Christmas firs",
                        line = CHRISTMAS_FIRS_LINE,
                        state = SettingsUiModel.christmasTreeDecoration(customization.christmasFirsEnabled, customization),
                        onCheckedChange = { scope.launch { prefs.setChristmasFirsEnabled(it, forThemeId) } },
                    )
                    SettingsSwitchRow(
                        title = "Santa",
                        // v5.15, the maintainer's «Tieni il ritmo, cambia la scritta» (2026-10-19): a flight starts 25 to
                        // 60 s after the last one ended (`SantaSleighEffect`), so "occasionally" said less than the truth.
                        supporting = "Santa's sleigh flies across the sky about once a minute, dropping gifts",
                        checked = customization.santaEnabled,
                        onCheckedChange = { scope.launch { prefs.setSantaEnabled(it, forThemeId) } },
                    )
                    DecorationRows("Gifts", customization.gifts, ObjectCategory.GIFTS, forThemeId, prefs, scope) { openOptions = it }
                }
                Season.HALLOWEEN -> {
                    SettingsSwitchRow(
                        title = "Halloween",
                        // What the switch does, all of it (`DESIGN_NOTES.md` §16): the moon, the trees -- not one standing
                        // as a Christmas fir -- the palms and the pumpkins. Until v5.15 the line named the first two only;
                        // the line the maintainer approved (2026-10-20), and still the whole of it: what v5.16 added has a
                        // switch of its own below (*«interruttori per ogni cosa»*, 2026-10-21).
                        supporting = HALLOWEEN_LINE,
                        checked = customization.halloweenEnabled,
                        onCheckedChange = { scope.launch { prefs.setHalloweenEnabled(it, forThemeId) } },
                    )
                    // The two that are the carved moon's: greyed with the reason while Halloween or Moon is off
                    // (`SettingsUiModel.laughingMoon`, the rule of Realistic Moon Phases), the stored choice kept.
                    val laughing = SettingsUiModel.laughingMoon(customization)
                    SettingsSwitchRow(
                        title = "Laughing moon",
                        supporting = halloweenMoonLine(LAUGHING_MOON_LINE, laughing),
                        checked = laughing.shownOn,
                        enabled = laughing.interactive,
                        onCheckedChange = { scope.launch { prefs.setLaughingMoonEnabled(it, forThemeId) } },
                    )
                    val nightByDay = SettingsUiModel.nightSkyByDay(customization)
                    SettingsSwitchRow(
                        title = "Night sky by day",
                        supporting = halloweenMoonLine(NIGHT_SKY_BY_DAY_LINE, nightByDay),
                        checked = nightByDay.shownOn,
                        enabled = nightByDay.interactive,
                        onCheckedChange = { scope.launch { prefs.setNightSkyByDayEnabled(it, forThemeId) } },
                    )
                    SettingsSwitchRow(
                        title = "Horror sky",
                        supporting = "Near-black overhead with a hard orange horizon. A separate switch, so you can have either on its own.",
                        checked = customization.horrorSkyEnabled,
                        onCheckedChange = { scope.launch { prefs.setHorrorSkyEnabled(it, forThemeId) } },
                    )
                    val decorations = SettingsUiModel.halloweenDecorations(customization)
                    SettingsSwitchRow(
                        title = "Halloween decorations",
                        supporting = when {
                            decorations.needsLine != null -> decorations.needsLine
                            decorations.blockedByTheOther -> SettingsUiModel.CHRISTMAS_ALREADY_ON_LINE
                            else -> HALLOWEEN_DECORATIONS_LINE
                        },
                        checked = decorations.shownOn,
                        enabled = decorations.interactive,
                        onCheckedChange = { scope.launch { prefs.setHalloweenDecorationsEnabled(it, forThemeId) } },
                    )
                    DecorationRows("Pumpkins", customization.pumpkins, ObjectCategory.PUMPKINS, forThemeId, prefs, scope) { openOptions = it }
                    DecorationRows(
                        "Scarecrows", customization.scarecrows, ObjectCategory.SCARECROWS, forThemeId, prefs, scope,
                        about = SCARECROWS_LINE, optionsLine = "Density",
                    ) { openOptions = it }
                    SettingsSwitchRow(
                        title = "Headless Horseman",
                        // The sleigh's rhythm (`HeadlessHorsemanEffect`), a turn held while the sleigh flies or is due.
                        supporting = HEADLESS_HORSEMAN_LINE,
                        checked = customization.headlessHorsemanEnabled,
                        onCheckedChange = { scope.launch { prefs.setHeadlessHorsemanEnabled(it, forThemeId) } },
                    )
                    SettingsSwitchRow(
                        title = "Blood rain",
                        supporting = BLOOD_RAIN_LINE,
                        checked = customization.bloodRainEnabled,
                        onCheckedChange = { scope.launch { prefs.setBloodRainEnabled(it, forThemeId) } },
                    )
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
                Season.CHRISTMAS -> "The Christmas lights and firs work with any palette, including none. They and the " +
                    "Halloween decorations are never on together: the first turned on stays."
                Season.HALLOWEEN -> "Halloween and Horror sky are independent of each other and of the palette. The " +
                    "Halloween decorations and the Christmas ones are never on together: the first turned on stays."
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
 * The lines under the three switches *Christmas lights* became (v5.16), each everything its switch does: the buildings'
 * -- the bulbs along the roofs and the strings under the sills, the wreath on every front door, the star on every tower,
 * the three bulbs of every distant house (`drawDecorations`, `drawWindowLights`, `PaperRenderer.drawDistantHouseLights`);
 * the trees' -- the lights of the leafy trees and of the palms (`drawTree`, `drawPalmTree`); the firs' -- one tree in
 * three a fir with its ten lights, its star and three presents (`standsAsFir`, decision 66). `LinesSayWhatTheyDoTest`
 * holds each to what the renderer draws under its switch.
 */
internal const val CHRISTMAS_BUILDING_LIGHTS_LINE =
    "Lights along the roofs and under the windowsills, a wreath on every front door, a star on every tower and three " +
        "lights on each house on the mountains"
internal const val CHRISTMAS_TREE_LIGHTS_LINE = "Blinking lights on the trees and the palms; none on the bare branches Halloween leaves"
internal const val CHRISTMAS_FIRS_LINE =
    "One tree in three becomes a Christmas fir, with its lights, a star on top and presents at its foot"

/** The line under *Halloween* (v5.15, approved 2026-10-20): its four things, and no more (`DESIGN_NOTES.md` §16). */
internal const val HALLOWEEN_LINE =
    "The moon with a carved face lit from inside, bare branches on the leafy trees, withered palms, and a face carved on every pumpkin"

/**
 * The lines under the six switches v5.16 added to Halloween beside *Halloween*, each everything it does: the laugh
 * (`LaughingMoon`: once a minute of wallpaper on screen, for two seconds), the night by day
 * (`PaperRenderer.nightOverDay`: the whole scene night at every hour, the moon on the sun's path, the opening hours the
 * clock's), the decorations (`SceneObjectRenderer.drawHalloweenDecorations`: the houses and the three shops, open or
 * shut), the scarecrows (the snowmen's places, `SceneObjectCatalog.scarecrowsAt`), the Horseman (`HeadlessHorsemanEffect`:
 * the sleigh's rhythm, out of the sleigh's way) and the blood rain (`PaperRenderer.drawPrecipitation`).
 */
internal const val LAUGHING_MOON_LINE =
    "About once a minute the Halloween moon throws its face open and laughs for a moment, shaking with it"
internal const val NIGHT_SKY_BY_DAY_LINE =
    "The whole scene stays night all day: the Halloween moon takes the sun's place, lit windows and night colours. " +
        "The shops keep their real opening hours."
internal const val HALLOWEEN_DECORATIONS_LINE =
    "Cobwebs at the ends of the roofs, purple bats under them and a jack-o'-lantern by every front door, lit at night, " +
        "on the houses and the shops, open or shut"
internal const val SCARECROWS_LINE = "Scarecrows with a carved pumpkin for a head, where the snowmen stand"
internal const val HEADLESS_HORSEMAN_LINE =
    "The Headless Horseman gallops across the sky about once a minute, dropping pumpkins on fire"
internal const val BLOOD_RAIN_LINE =
    "When it rains, the rain is red and its drops twice as thick. Your rain colours are kept for when this is off; " +
        "snow stays snow."

/** The line under *Laughing moon* or *Night sky by day*: what it does, or -- locked -- what holds it. */
private fun halloweenMoonLine(line: String, state: HalloweenMoonSwitchUiState): String = when {
    state.needsHalloween -> "Needs Halloween above: it is the carved moon's. Your choice is kept until then."
    state.needsMoon -> "Needs Show Moon in World & scene, Sun and moon. Your choice is kept until then."
    else -> line
}

/**
 * A Christmas decoration's switch: greyed, with the reason, while the Halloween decorations are on (v5.16) -- or while
 * nothing stands to carry it (no tree for the trees' two, no house or building for the buildings' lights).
 */
@Composable
private fun ChristmasDecorationRow(
    title: String,
    line: String,
    state: ExclusiveSwitchUiState,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(
        title = title,
        supporting = when {
            state.needsLine != null -> state.needsLine
            state.blockedByTheOther -> SettingsUiModel.HALLOWEEN_ALREADY_ON_LINE
            else -> line
        },
        checked = state.shownOn,
        enabled = state.interactive,
        onCheckedChange = onCheckedChange,
    )
}

/**
 * A decoration that has density and colours: its switch, then a way into those. [about], when given, says what it is
 * before its density (the scarecrows', v5.16), and [optionsLine] what its options hold -- "Density" for one with no colours.
 */
@Composable
private fun DecorationRows(
    title: String,
    config: ObjectVariantConfig,
    category: ObjectCategory,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    about: String? = null,
    optionsLine: String = "Density and colours",
    onOpenOptions: (ObjectCategory) -> Unit,
) {
    // At 0 % density none is drawn: off, and said, and a tap brings them back at the theme's own
    // density (v5.10C, row 8; inventory I-211).
    val shown = SettingsUiModel.amountSwitch(config.visible, config.density)
    SettingsSwitchRow(
        title = title,
        supporting = if (shown.noneAtZero) {
            NONE_AT_ZERO_LINE
        } else {
            (about?.let { "$it. " } ?: "") + "Density ${(config.density * 100).toInt()}%"
        },
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
        supporting = optionsLine,
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
        ObjectCategory.SCARECROWS -> "Scarecrows" to customization.scarecrows
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
            // The scarecrows' drawing is fixed art (v5.16): their options are their density.
            showColours = category != ObjectCategory.SCARECROWS,
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
