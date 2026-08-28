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
import com.paperscrape.livewallpaper.prefs.ObjectCategory
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Visibility, density, two day/night colour pairs and a reset for one object category.
 *
 * Unchanged from v2.8 in content and in every write it performs. What changed is *where* it is
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
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (showTitle) SectionTitle(title)

        SettingSwitchRow(
            title = "Show $title",
            subtitle = "$title can appear in every theme",
            checked = config.visible,
            onCheckedChange = { scope.launch { prefs.setCategoryVisible(category, it, forThemeId) } },
        )

        PreferenceSlider(
            label = { shown -> Text("Density: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = config.density,
            onCommit = { committed -> scope.launch { prefs.setCategoryDensity(category, committed, forThemeId) } },
            valueRange = 0f..1f,
        )

        Text(
            "Each one randomly uses Color 1 or Color 2, and blends into its night version as it gets dark.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ColorSwatchRow("Day Color 1", config.colorDay1) {
            onEditColor("$title - Day Color 1", config.colorDay1) { c -> scope.launch { prefs.setCategoryColorDay1(category, c, forThemeId) } }
        }
        ColorSwatchRow("Night Color 1", config.colorNight1) {
            onEditColor("$title - Night Color 1", config.colorNight1) { c -> scope.launch { prefs.setCategoryColorNight1(category, c, forThemeId) } }
        }
        ColorSwatchRow("Day Color 2", config.colorDay2) {
            onEditColor("$title - Day Color 2", config.colorDay2) { c -> scope.launch { prefs.setCategoryColorDay2(category, c, forThemeId) } }
        }
        ColorSwatchRow("Night Color 2", config.colorNight2) {
            onEditColor("$title - Night Color 2", config.colorNight2) { c -> scope.launch { prefs.setCategoryColorNight2(category, c, forThemeId) } }
        }

        OutlinedButton(
            onClick = { scope.launch { prefs.resetCategory(category, forThemeId) } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Reset $title to default")
        }
    }
}

/** Visibility, day/night colour and density for one of the two mountain layers. Unchanged. */
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
        ColorSwatchRow("Day Color", config.colorDay) {
            onEditColor("$title - Day Color", config.colorDay) { c -> scope.launch { prefs.setMountainColorDay(front, c, forThemeId) } }
        }
        ColorSwatchRow("Night Color", config.colorNight) {
            onEditColor("$title - Night Color", config.colorNight) { c -> scope.launch { prefs.setMountainColorNight(front, c, forThemeId) } }
        }
        PreferenceSlider(
            label = { shown -> Text("Density: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = config.density,
            onCommit = { committed -> scope.launch { prefs.setMountainDensity(front, committed, forThemeId) } },
            valueRange = 0f..1f,
        )
    }
}
