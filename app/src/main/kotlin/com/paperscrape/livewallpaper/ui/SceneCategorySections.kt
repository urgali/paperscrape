package com.paperscrape.livewallpaper.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.paperscrape.livewallpaper.engine.MountainLayerConfig
import com.paperscrape.livewallpaper.engine.ObjectVariantConfig
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.prefs.ObjectCategory
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The line under a *Show X* switch whose amount is at 0 %, where nothing is drawn (v5.10C, row 8:
 * the maintainer's table said it in these words, «None at 0% density»). See
 * [SettingsUiModel.amountSwitch]; the tap that brings them back is [applyAmountTap].
 */
internal const val NONE_AT_ZERO_LINE = "None at 0% density - tap to bring them back"

/**
 * Writes what [SettingsUiModel.amountTap] decided: the switch's own flag, or -- turning it on at
 * 0 % -- the amount back to the theme's own and then the flag, so the thing appears instead of the
 * tap changing nothing. Two writes, in that order: in between, the scene is at most what it was.
 */
internal fun CoroutineScope.applyAmountTap(
    tap: AmountTap,
    setVisible: suspend (Boolean) -> Unit,
    setAmount: suspend (Float) -> Unit,
) = launch {
    when (tap) {
        is AmountTap.SetVisible -> setVisible(tap.visible)
        is AmountTap.Restore -> {
            setAmount(tap.amount)
            setVisible(true)
        }
    }
}

/** The theme's own density for [category], which is what a tap at 0 % restores. */
internal fun defaultDensityOf(category: ObjectCategory, forThemeId: String): Float {
    val d = defaultCustomizationFor(forThemeId)
    return when (category) {
        ObjectCategory.HOUSES -> d.houses
        ObjectCategory.BUILDINGS -> d.buildings
        ObjectCategory.CARS -> d.cars
        ObjectCategory.PARASOLS -> d.parasols
        ObjectCategory.TREES -> d.trees
        ObjectCategory.PEOPLE -> d.people
        ObjectCategory.SNOWMEN -> d.snowmen
        ObjectCategory.GIFTS -> d.gifts
        ObjectCategory.PENGUINS -> d.penguins
        ObjectCategory.BUNNIES -> d.bunnies
        ObjectCategory.EASTER_EGGS -> d.easterEggs
        ObjectCategory.PUMPKINS -> d.pumpkins
    }.density
}

/**
 * Visibility, density, two day/night colour pairs and a reset for one object category.
 *
 * Unchanged from v2.8 in what it is for; since then it has gained the automatic day/night colour
 * modes and the optional switch, label and night-twin slot below. What changed is *where* it is
 * shown: the six seasonal categories used to be expanded inline, one after another, on the single
 * "Seasonal Decorations" screen -- about sixty controls in one scroll, with each category's own
 * season named in a heading far above it. Each one now lives behind its season, reached
 * deliberately, exactly like every Scene Objects category always has been.
 */
@Composable
internal fun ObjectCategorySection(
    title: String,
    config: ObjectVariantConfig,
    category: ObjectCategory,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onEditColor: (label: String, color: Int, onChange: (Int) -> Unit) -> Unit,
    showTitle: Boolean = true,
    /**
     * Whether to draw the "Show X" switch, for the same reason [showTitle] exists: the caller has
     * already drawn one.
     *
     * The six seasonal decorations reach this block through `SeasonsScreen.DecorationRows`, which
     * puts the category's switch on the season's own screen and a "X options -- Density and
     * colours" row directly under it. So the switch was rendered twice for one `config.visible`
     * and one `setCategoryVisible`, one tap apart, and the second one contradicted the row that
     * led to it. The World & scene categories have no such outer switch -- there the parent row
     * only summarises -- so they keep it, and the default is `true`.
     */
    showVisibilitySwitch: Boolean = true,
    /** What the density slider calls itself -- "Day density" where a night twin sits beside it. */
    densityLabel: String = "Density",
    /**
     * How the density slider prints its value: the per cent, or the caller's words for an end that
     * means something else -- the cars' "0% - one car" (v5.10E, inventory I-217).
     */
    densityValue: (Float) -> String = { "${(it * 100).toInt()}%" },
    /**
     * The line under *Show X* when it is not the 0 % one: "X can appear in every theme", or the
     * caller's -- the cars', which take the road with them (v5.10E, inventory I-217).
     */
    switchSubtitle: String? = null,
    /**
     * Whether at 0 % density the wallpaper draws none of this category -- houses, trees, parasols, the
     * six decorations -- so the switch reads off there and says so (v5.10C, row 8). Not the
     * buildings, whose three shops stay at 0 %, nor the cars, one of which does.
     */
    noneAtZero: Boolean = false,
    /** Rendered directly under the density slider -- the cars put their night twin here, so the
     * pair reads as a pair instead of being split by the colour section. */
    afterDensity: @Composable () -> Unit = {},
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showTitle) SectionTitle(title)

        if (showVisibilitySwitch) {
            val shown = SettingsUiModel.amountSwitch(config.visible, config.density)
            val atZero = noneAtZero && shown.noneAtZero
            SettingSwitchRow(
                title = "Show $title",
                subtitle = if (atZero) NONE_AT_ZERO_LINE else switchSubtitle ?: "$title can appear in every theme",
                checked = if (noneAtZero) shown.shownOn else config.visible,
                onCheckedChange = { wanted ->
                    if (noneAtZero) {
                        scope.applyAmountTap(
                            SettingsUiModel.amountTap(wanted, config.density, defaultDensityOf(category, forThemeId)),
                            setVisible = { prefs.setCategoryVisible(category, it, forThemeId) },
                            setAmount = { prefs.setCategoryDensity(category, it, forThemeId) },
                        )
                    } else {
                        scope.launch { prefs.setCategoryVisible(category, wanted, forThemeId) }
                    }
                },
            )
        }

        // **Locked while the switch is off** (v5.10E, inventory I-226): it moved and drew nothing. Not
        // at 0 %, where the switch reads off because of it and it is how the amount comes back.
        val densityLive = SettingsUiModel.amountSliderEnabled(config.visible)
        PreferenceSlider(
            label = { shown -> Text("$densityLabel: ${densityValue(shown)}", style = MaterialTheme.typography.bodyMedium) },
            value = config.density,
            onCommit = { committed -> scope.launch { prefs.setCategoryDensity(category, committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = densityLive,
        )
        if (!densityLive && !showVisibilitySwitch) {
            // The switch is a screen back (the season's), so the reason is said here.
            Text(
                "$title are off on the season's screen. Turn them on there to set this; your choice is kept until then.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        afterDensity()

        Text(
            "Each one randomly uses Color 1 or Color 2, and blends into its night version as it gets dark.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DayNightColorPair(
            dayLabel = "Day Color 1", nightLabel = "Night Color 1",
            dayColor = config.colorDay1, nightColor = config.colorNight1, mode = config.autoMode1,
            onEditDay = { onEditColor("$title - Day Color 1", config.colorDay1) { c -> scope.launch { prefs.setCategoryColorDay1(category, c, forThemeId) } } },
            onEditNight = { onEditColor("$title - Night Color 1", config.colorNight1) { c -> scope.launch { prefs.setCategoryColorNight1(category, c, forThemeId) } } },
            onModeChange = { scope.launch { prefs.setCategoryAutoMode1(category, it, forThemeId) } },
        )
        DayNightColorPair(
            dayLabel = "Day Color 2", nightLabel = "Night Color 2",
            dayColor = config.colorDay2, nightColor = config.colorNight2, mode = config.autoMode2,
            onEditDay = { onEditColor("$title - Day Color 2", config.colorDay2) { c -> scope.launch { prefs.setCategoryColorDay2(category, c, forThemeId) } } },
            onEditNight = { onEditColor("$title - Night Color 2", config.colorNight2) { c -> scope.launch { prefs.setCategoryColorNight2(category, c, forThemeId) } } },
            onModeChange = { scope.launch { prefs.setCategoryAutoMode2(category, it, forThemeId) } },
        )

        // Also the palms, on the Trees page (v5.10E, inventory I-294): `WallpaperPrefs.resetCategory`.
        OutlinedButton(
            onClick = { scope.launch { prefs.resetCategory(category, forThemeId) } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset $title to default")
        }
    }
}

/** Visibility, day/night colour and density for one of the two mountain layers. */
@Composable
internal fun MountainLayerSection(
    title: String,
    config: MountainLayerConfig,
    front: Boolean,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onEditColor: (label: String, color: Int, onChange: (Int) -> Unit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingSwitchRow(
            title = title,
            subtitle = "Show/hide this layer",
            checked = config.visible,
            onCheckedChange = { scope.launch { prefs.setMountainVisible(front, it, forThemeId) } },
        )
        DayNightColorPair(
            dayLabel = "Day Color", nightLabel = "Night Color",
            dayColor = config.colorDay, nightColor = config.colorNight, mode = config.autoMode,
            onEditDay = { onEditColor("$title - Day Color", config.colorDay) { c -> scope.launch { prefs.setMountainColorDay(front, c, forThemeId) } } },
            onEditNight = { onEditColor("$title - Night Color", config.colorNight) { c -> scope.launch { prefs.setMountainColorNight(front, c, forThemeId) } } },
            onModeChange = { scope.launch { prefs.setMountainAutoMode(front, it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("Density: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = config.density,
            onCommit = { committed -> scope.launch { prefs.setMountainDensity(front, committed, forThemeId) } },
            valueRange = 0f..1f,
            // With the layer hidden it moved and drew nothing (v5.10E, inventory I-226).
            enabled = SettingsUiModel.amountSliderEnabled(config.visible),
        )
    }
}
