package com.paperscrape.livewallpaper.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paperscrape.livewallpaper.engine.RandomSceneGenerator
import com.paperscrape.livewallpaper.engine.SceneTheme
import com.paperscrape.livewallpaper.engine.SeasonalThemeRules
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: WallpaperPrefs,
    onApplyWallpaper: () -> Unit,
    onRequestLocationPermission: (onResult: (Boolean) -> Unit) -> Unit,
) {
    val settings by prefs.settingsFlow.collectAsState(initial = WallpaperSettings())
    val scope = rememberCoroutineScope()

    val effectiveThemeId = if (settings.autoThemeByDate) {
        SeasonalThemeRules.themeForDate() ?: settings.themeId
    } else {
        settings.themeId
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("PaperScrape") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState()) // BUGFIX: without this, content taller
                // than the screen (e.g. "Touch effects" and everything below it) was simply
                // unreachable -- there was no way to scroll down to it.
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LivePreview(theme = ThemeCatalog.byId(effectiveThemeId))

            // Placed right under the preview -- always reachable without scrolling, so applying
            // the wallpaper never requires digging through the rest of the settings first.
            Button(
                onClick = onApplyWallpaper,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("📱 Set as wallpaper")
            }

            SectionTitle("Theme")
            ThemeGallery(
                selectedId = settings.themeId,
                onSelect = { theme -> scope.launch { prefs.setTheme(theme.id) } },
            )

            Column {
                OutlinedButton(
                    onClick = { scope.launch { prefs.setTheme(RandomSceneGenerator.newThemeId()) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("🎲 Generate random theme")
                }
                if (RandomSceneGenerator.isRandomThemeId(settings.themeId)) {
                    Text(
                        "Random theme active — tap again to generate another one",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            Column {
                SettingSwitchRow(
                    title = "Automatic theme by date",
                    subtitle = "Switches to Christmas, New Year's Eve, Easter, or Beach automatically during their season, overriding your manual pick",
                    checked = settings.autoThemeByDate,
                    onCheckedChange = { scope.launch { prefs.setAutoThemeByDate(it) } },
                )
                if (settings.autoThemeByDate) {
                    val label = SeasonalThemeRules.labelForDate()
                    val statusText = if (label != null) {
                        "Today's automatic theme: $label"
                    } else {
                        "No seasonal window active right now — showing your manually selected theme"
                    }
                    Text(
                        statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            SectionTitle("Behavior")

            SettingSwitchRow(
                title = "Follow real time",
                subtitle = "The sun and moon move according to your device's clock",
                checked = settings.syncWithRealTime,
                onCheckedChange = { scope.launch { prefs.setSyncWithRealTime(it) } },
            )

            if (!settings.syncWithRealTime) {
                Column {
                    Text("Fixed time: ${settings.fixedHour.toInt()}:00", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.fixedHour,
                        onValueChange = { scope.launch { prefs.setFixedHour(it) } },
                        valueRange = 0f..23f,
                        steps = 22,
                    )
                }
            } else {
                SettingSwitchRow(
                    title = "Use location for sunrise/sunset",
                    subtitle = "Calculate precise times based on your area",
                    checked = settings.useLocationForSunTimes,
                    onCheckedChange = { enabled ->
                        if (enabled) {
                            onRequestLocationPermission { granted ->
                                scope.launch { prefs.setUseLocation(granted) }
                            }
                        } else {
                            scope.launch { prefs.setUseLocation(false) }
                        }
                    },
                )
            }

            SettingSwitchRow(
                title = "Touch effects",
                subtitle = "Tap dogs, penguins, gifts, or cars to make them react (with sound); tap the background to make a paper bird fly",
                checked = settings.touchEffectsEnabled,
                onCheckedChange = { scope.launch { prefs.setTouchEffects(it) } },
            )

            Column {
                Text("Parallax strength", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.parallaxStrength,
                    onValueChange = { scope.launch { prefs.setParallaxStrength(it) } },
                    valueRange = 0.5f..2f,
                )
            }

            Text(
                "PaperScrape is an open-source live wallpaper inspired by classic \"paper cutout\" animated backgrounds. Source code on GitHub.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

@Composable
private fun LivePreview(theme: SceneTheme) {
    // A lightweight static preview (sky gradient + hill silhouette + sun) so the user gets
    // instant visual feedback without spinning up the real WallpaperService renderer here.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(16.dp),
    ) {
        ThemeScenePreview(theme = theme, modifier = Modifier.fillMaxSize())
    }
}

/**
 * Draws a small but honest preview of a theme's *actual* look: sky gradient, layered hill
 * silhouette (using the theme's real day colors, darkest/nearest layer last), and the sun —
 * instead of a flat color swatch that hides what the theme really looks like once applied.
 */
@Composable
private fun ThemeScenePreview(theme: SceneTheme, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val skyTop = Color(theme.skyDay[0])
        val skyBottom = Color(theme.skyDay.getOrElse(1) { theme.skyDay[0] })
        drawRect(brush = Brush.verticalGradient(listOf(skyTop, skyBottom)))

        drawCircle(
            color = Color(theme.sunColor),
            radius = size.minDimension * 0.09f,
            center = Offset(size.width * 0.80f, size.height * 0.22f),
        )

        val hillColors = theme.hillColorsDay
        val layerCount = hillColors.size
        for (i in 0 until layerCount) {
            val topFraction = 0.45f + i * 0.16f
            val top = size.height * topFraction
            drawRect(
                color = Color(hillColors[i]),
                topLeft = Offset(0f, top),
                size = androidx.compose.ui.geometry.Size(size.width, size.height - top),
            )
        }
    }
}

/** Emoji hints for what each theme's scene actually contains -- a cheap, asset-free way to show
 * more about a theme's content than color alone can. Keyed by themeId; UI-only concern, so it
 * deliberately lives here rather than on the [SceneTheme] data model. */
private val THEME_ICON_HINTS: Map<String, String> = mapOf(
    "sunset" to "🌅🐕",
    "autumn" to "🍂🐕",
    "winter" to "❄️⛄",
    "desert" to "🏜️🐕",
    "christmas" to "🎄🎁",
    "new_year" to "🎆🎈",
    "beach" to "🌴⛱️",
    "city" to "🏙️🚗",
    "tundra" to "🐧⛄",
    "easter" to "🐰🥚",
)

private fun iconHintFor(themeId: String): String = THEME_ICON_HINTS[themeId] ?: "🎨"

/**
 * A proper gallery of theme previews (2 per row) so the user can see roughly what each theme
 * actually looks like -- sky colors, hill colors, and a couple of signature objects via emoji --
 * before applying it, rather than judging from a small flat color swatch.
 *
 * Built as plain Row/Column chunks rather than LazyVerticalGrid: the theme list is short (10
 * items) and this avoids nested-scroll conflicts with the screen's own vertical scroll.
 */
@Composable
private fun ThemeGallery(selectedId: String, onSelect: (SceneTheme) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        ThemeCatalog.ALL.chunked(2).forEach { rowThemes ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                rowThemes.forEach { theme ->
                    ThemeGalleryCard(
                        theme = theme,
                        selected = theme.id == selectedId,
                        onClick = { onSelect(theme) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowThemes.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ThemeGalleryCard(
    theme: SceneTheme,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(4f / 3f)
                .clip(RoundedCornerShape(14.dp))
                .border(
                    width = if (selected) 3.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(14.dp),
                )
        ) {
            ThemeScenePreview(theme = theme, modifier = Modifier.fillMaxSize())
            Text(
                iconHintFor(theme.id),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            )
        }
        Text(
            theme.displayName,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
