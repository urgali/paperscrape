package com.paperscrape.livewallpaper.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.paperscrape.livewallpaper.engine.RandomSceneGenerator
import com.paperscrape.livewallpaper.engine.SceneTheme
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

    Scaffold(
        topBar = { TopAppBar(title = { Text("PaperScrape") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            LivePreview(theme = ThemeCatalog.byId(settings.themeId))

            SectionTitle("Tema")
            ThemeRow(
                selectedId = settings.themeId,
                onSelect = { theme -> scope.launch { prefs.setTheme(theme.id) } },
            )

            Column {
                OutlinedButton(
                    onClick = { scope.launch { prefs.setTheme(RandomSceneGenerator.newThemeId()) } },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("🎲 Genera tema casuale")
                }
                if (RandomSceneGenerator.isRandomThemeId(settings.themeId)) {
                    Text(
                        "Tema casuale attivo — premi di nuovo per generarne un altro",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }

            SectionTitle("Comportamento")

            SettingSwitchRow(
                title = "Segui l'ora reale",
                subtitle = "Sole e luna si muovono secondo l'orario del dispositivo",
                checked = settings.syncWithRealTime,
                onCheckedChange = { scope.launch { prefs.setSyncWithRealTime(it) } },
            )

            if (!settings.syncWithRealTime) {
                Column {
                    Text("Ora fissa: ${settings.fixedHour.toInt()}:00", style = MaterialTheme.typography.bodyMedium)
                    Slider(
                        value = settings.fixedHour,
                        onValueChange = { scope.launch { prefs.setFixedHour(it) } },
                        valueRange = 0f..23f,
                        steps = 22,
                    )
                }
            } else {
                SettingSwitchRow(
                    title = "Usa la posizione per alba/tramonto",
                    subtitle = "Calcola l'orario preciso in base alla tua zona",
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
                title = "Effetti al tocco",
                subtitle = "Tocca cani, pinguini, regali o auto per farli reagire (con suono); tocca lo sfondo per far volare uccellini di carta",
                checked = settings.touchEffectsEnabled,
                onCheckedChange = { scope.launch { prefs.setTouchEffects(it) } },
            )

            Column {
                Text("Intensità parallasse", style = MaterialTheme.typography.bodyMedium)
                Slider(
                    value = settings.parallaxStrength,
                    onValueChange = { scope.launch { prefs.setParallaxStrength(it) } },
                    valueRange = 0.5f..2f,
                )
            }

            Button(
                onClick = onApplyWallpaper,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Imposta come sfondo")
            }

            Text(
                "PaperScrape è un live wallpaper open source ispirato ai classici sfondi \"in carta ritagliata\". Codice sorgente su GitHub.",
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
    // A lightweight static preview swatch (top sky color -> hill color) so the user gets
    // instant visual feedback without spinning up the real WallpaperService renderer here.
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f),
        shape = RoundedCornerShape(16.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(theme.skyDay[0]))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color(theme.hillColorsDay.last()))
            )
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(theme.sunColor))
            )
        }
    }
}

@Composable
private fun ThemeRow(selectedId: String, onSelect: (SceneTheme) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(ThemeCatalog.ALL) { theme ->
            ThemeSwatch(theme = theme, selected = theme.id == selectedId, onClick = { onSelect(theme) })
        }
    }
}

@Composable
private fun ThemeSwatch(theme: SceneTheme, selected: Boolean, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(theme.skyDay[0]))
                .border(
                    width = if (selected) 3.dp else 0.dp,
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(12.dp),
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(24.dp)
                    .align(Alignment.BottomCenter)
                    .background(Color(theme.hillColorsDay.last()))
            )
        }
        Text(theme.displayName, style = MaterialTheme.typography.labelSmall)
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
