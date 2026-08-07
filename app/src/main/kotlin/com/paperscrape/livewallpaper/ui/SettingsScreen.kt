package com.paperscrape.livewallpaper.ui

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.HouseBuildingConfig
import com.paperscrape.livewallpaper.engine.RandomSceneGenerator
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneTheme
import com.paperscrape.livewallpaper.engine.SeasonalThemeRules
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: WallpaperPrefs,
    customThemeStore: CustomThemeStore,
    onApplyWallpaper: () -> Unit,
    onRequestLocationPermission: (onResult: (Boolean) -> Unit) -> Unit,
) {
    val settings by prefs.settingsFlow.collectAsState(initial = WallpaperSettings())
    val customThemeData by customThemeStore.dataFlow.collectAsState(initial = CustomThemeData())
    val scope = rememberCoroutineScope()
    var showThemeManager by remember { mutableStateOf(false) }
    var showHousesBuildings by remember { mutableStateOf(false) }

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
                .verticalScroll(rememberScrollState())
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
            OutlinedButton(
                onClick = { showThemeManager = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("🖼️ Manage themes (${ThemeCatalog.ALL.size + customThemeData.customThemes.size})")
            }
            OutlinedButton(
                onClick = { showHousesBuildings = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("🏘️ Houses & buildings")
            }

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

    if (showThemeManager) {
        ThemeManagerDialog(
            currentSelectedId = settings.themeId,
            effectiveThemeId = effectiveThemeId,
            customThemeData = customThemeData,
            onDismiss = { showThemeManager = false },
            onSelectTheme = { id -> scope.launch { prefs.setTheme(id) } },
            onSaveCurrentAsNew = { name ->
                scope.launch {
                    val id = CustomThemeStore.newCustomThemeId()
                    customThemeStore.upsertCustomTheme(snapshotEntry(id, name, effectiveThemeId))
                }
            },
            onReplaceBuiltinWithCurrent = { builtinId ->
                scope.launch {
                    val name = ThemeCatalog.byId(builtinId).displayName
                    customThemeStore.setOverride(builtinId, snapshotEntry(builtinId, name, effectiveThemeId))
                }
            },
            onResetBuiltin = { builtinId -> scope.launch { customThemeStore.clearOverride(builtinId) } },
            onReplaceCustomWithCurrent = { id, name ->
                scope.launch { customThemeStore.upsertCustomTheme(snapshotEntry(id, name, effectiveThemeId)) }
            },
            onRenameCustom = { id, newName -> scope.launch { customThemeStore.renameCustomTheme(id, newName) } },
            onDeleteCustom = { id -> scope.launch { customThemeStore.deleteCustomTheme(id) } },
        )
    }

    if (showHousesBuildings) {
        HousesBuildingsDialog(
            config = settings.houseBuildingConfig,
            onDismiss = { showHousesBuildings = false },
            onShowHousesChange = { scope.launch { prefs.setShowHouses(it) } },
            onShowBuildingsChange = { scope.launch { prefs.setShowBuildings(it) } },
            onDensityChange = { scope.launch { prefs.setHouseBuildingDensity(it) } },
            onHouseColorDay1Change = { scope.launch { prefs.setHouseColorDay1(it) } },
            onHouseColorNight1Change = { scope.launch { prefs.setHouseColorNight1(it) } },
            onHouseColorDay2Change = { scope.launch { prefs.setHouseColorDay2(it) } },
            onHouseColorNight2Change = { scope.launch { prefs.setHouseColorNight2(it) } },
            onBuildingColorDay1Change = { scope.launch { prefs.setBuildingColorDay1(it) } },
            onBuildingColorNight1Change = { scope.launch { prefs.setBuildingColorNight1(it) } },
            onBuildingColorDay2Change = { scope.launch { prefs.setBuildingColorDay2(it) } },
            onBuildingColorNight2Change = { scope.launch { prefs.setBuildingColorNight2(it) } },
            onResetToDefaults = { scope.launch { prefs.resetHouseBuildingConfig() } },
        )
    }
}

/** Snapshots whatever theme+layout [sourceThemeId] currently resolves to, relabeled as
 * [targetId]/[targetName] -- the basis for both "save as new theme" and "replace with current". */
private fun snapshotEntry(targetId: String, targetName: String, sourceThemeId: String): CustomThemeEntry {
    val theme = ThemeCatalog.byId(sourceThemeId).copy(id = targetId, displayName = targetName)
    val layout = SceneObjectCatalog.layoutFor(sourceThemeId, theme.accentColor)
    return CustomThemeEntry(id = targetId, name = targetName, theme = theme, layout = layout)
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
 * deliberately lives here rather than on the [SceneTheme] data model. Unknown/custom ids fall
 * back to a generic palette icon. */
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

// --- Theme manager -------------------------------------------------------------------------

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeManagerDialog(
    currentSelectedId: String,
    effectiveThemeId: String,
    customThemeData: CustomThemeData,
    onDismiss: () -> Unit,
    onSelectTheme: (String) -> Unit,
    onSaveCurrentAsNew: (name: String) -> Unit,
    onReplaceBuiltinWithCurrent: (builtinId: String) -> Unit,
    onResetBuiltin: (builtinId: String) -> Unit,
    onReplaceCustomWithCurrent: (id: String, name: String) -> Unit,
    onRenameCustom: (id: String, newName: String) -> Unit,
    onDeleteCustom: (id: String) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<CustomThemeEntry?>(null) }
    var confirmReset by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<CustomThemeEntry?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Manage Themes") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    SectionTitle("Current look")
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(16.dp)),
                    ) {
                        ThemeScenePreview(theme = ThemeCatalog.byId(effectiveThemeId), modifier = Modifier.fillMaxSize())
                    }
                    OutlinedButton(
                        onClick = { showSaveDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("💾 Save current look as new theme")
                    }

                    SectionTitle("Built-in themes")
                    GalleryGrid(items = ThemeCatalog.ALL) { builtin ->
                        val overrideEntry = customThemeData.overrides[builtin.id]
                        val displayedTheme = overrideEntry?.theme ?: builtin
                        ManagedThemeCard(
                            theme = displayedTheme,
                            selected = currentSelectedId == builtin.id,
                            isCustomized = overrideEntry != null,
                            onSelect = { onSelectTheme(builtin.id) },
                            onReplaceWithCurrent = { onReplaceBuiltinWithCurrent(builtin.id) },
                            onReset = if (overrideEntry != null) {
                                { confirmReset = builtin.id }
                            } else null,
                            onRename = null,
                            onDelete = null,
                        )
                    }

                    if (customThemeData.customThemes.isNotEmpty()) {
                        SectionTitle("Your custom themes")
                        GalleryGrid(items = customThemeData.customThemes) { entry ->
                            ManagedThemeCard(
                                theme = entry.theme,
                                selected = currentSelectedId == entry.id,
                                isCustomized = false,
                                onSelect = { onSelectTheme(entry.id) },
                                onReplaceWithCurrent = { onReplaceCustomWithCurrent(entry.id, entry.name) },
                                onReset = null,
                                onRename = { renameTarget = entry },
                                onDelete = { confirmDelete = entry },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSaveDialog) {
        NameInputDialog(
            title = "Save current look as...",
            onConfirm = { name -> onSaveCurrentAsNew(name); showSaveDialog = false },
            onDismiss = { showSaveDialog = false },
        )
    }

    renameTarget?.let { entry ->
        NameInputDialog(
            title = "Rename theme",
            initialName = entry.name,
            onConfirm = { name -> onRenameCustom(entry.id, name); renameTarget = null },
            onDismiss = { renameTarget = null },
        )
    }

    confirmReset?.let { builtinId ->
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text("Reset to default?") },
            text = { Text("This removes your custom version of \"${ThemeCatalog.byId(builtinId).displayName}\" and restores the original.") },
            confirmButton = {
                TextButton(onClick = { onResetBuiltin(builtinId); confirmReset = null }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = null }) { Text("Cancel") }
            },
        )
    }

    confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete theme?") },
            text = { Text("\"${entry.name}\" will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = { onDeleteCustom(entry.id); confirmDelete = null }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun NameInputDialog(
    title: String,
    initialName: String = "",
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var text by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { if (text.isNotBlank()) onConfirm(text.trim()) },
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

/** Lays items out 2-per-row using plain Row/Column chunks (not LazyVerticalGrid) so this can
 * live inside an already-vertically-scrolling Column without nested-scroll conflicts -- the
 * theme count here is always small (tens at most), so the lack of lazy virtualization is fine. */
@Composable
private fun <T> GalleryGrid(items: List<T>, content: @Composable (T) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        items.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                row.forEach { item ->
                    Box(modifier = Modifier.weight(1f)) { content(item) }
                }
                if (row.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun ManagedThemeCard(
    theme: SceneTheme,
    selected: Boolean,
    isCustomized: Boolean,
    onSelect: () -> Unit,
    onReplaceWithCurrent: () -> Unit,
    onReset: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column {
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
                .clickable(onClick = onSelect),
        ) {
            ThemeScenePreview(theme = theme, modifier = Modifier.fillMaxSize())

            Text(
                iconHintFor(theme.id),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp),
            )

            if (isCustomized) {
                Surface(
                    color = MaterialTheme.colorScheme.primary,
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(4.dp),
                ) {
                    Text(
                        "Customized",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Default.MoreVert,
                        contentDescription = "Theme actions",
                        tint = Color.White,
                    )
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Replace with current") },
                        onClick = { menuExpanded = false; onReplaceWithCurrent() },
                    )
                    if (onReset != null) {
                        DropdownMenuItem(
                            text = { Text("Reset to default") },
                            onClick = { menuExpanded = false; onReset() },
                        )
                    }
                    if (onRename != null) {
                        DropdownMenuItem(
                            text = { Text("Rename") },
                            onClick = { menuExpanded = false; onRename() },
                        )
                    }
                    if (onDelete != null) {
                        DropdownMenuItem(
                            text = { Text("Delete theme") },
                            onClick = { menuExpanded = false; onDelete() },
                        )
                    }
                }
            }
        }
        Text(
            theme.displayName,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

// --- Houses & buildings ---------------------------------------------------------------------

private data class ColorEditTarget(val label: String, val color: Int, val onChange: (Int) -> Unit)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HousesBuildingsDialog(
    config: HouseBuildingConfig,
    onDismiss: () -> Unit,
    onShowHousesChange: (Boolean) -> Unit,
    onShowBuildingsChange: (Boolean) -> Unit,
    onDensityChange: (Float) -> Unit,
    onHouseColorDay1Change: (Int) -> Unit,
    onHouseColorNight1Change: (Int) -> Unit,
    onHouseColorDay2Change: (Int) -> Unit,
    onHouseColorNight2Change: (Int) -> Unit,
    onBuildingColorDay1Change: (Int) -> Unit,
    onBuildingColorNight1Change: (Int) -> Unit,
    onBuildingColorDay2Change: (Int) -> Unit,
    onBuildingColorNight2Change: (Int) -> Unit,
    onResetToDefaults: () -> Unit,
) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Houses & Buildings") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                    )
                }
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(
                        "These settings apply across every theme that includes houses or buildings, not just the current one.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    SettingSwitchRow(
                        title = "Show houses",
                        subtitle = "Houses appear in the themes that have them",
                        checked = config.showHouses,
                        onCheckedChange = onShowHousesChange,
                    )
                    SettingSwitchRow(
                        title = "Show buildings",
                        subtitle = "Commercial buildings/skyscrapers appear in the themes that have them",
                        checked = config.showBuildings,
                        onCheckedChange = onShowBuildingsChange,
                    )

                    Column {
                        Text("Density: ${(config.density * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "How many of a theme's house/building spots are actually filled",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Slider(value = config.density, onValueChange = onDensityChange, valueRange = 0f..1f)
                    }

                    SectionTitle("House colors")
                    Text(
                        "Each house randomly uses Color 1 or Color 2, and blends into its night version as it gets dark.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ColorSwatchRow("Day Color 1", config.houseColorDay1) {
                        editingTarget = ColorEditTarget("House — Day Color 1", config.houseColorDay1, onHouseColorDay1Change)
                    }
                    ColorSwatchRow("Night Color 1", config.houseColorNight1) {
                        editingTarget = ColorEditTarget("House — Night Color 1", config.houseColorNight1, onHouseColorNight1Change)
                    }
                    ColorSwatchRow("Day Color 2", config.houseColorDay2) {
                        editingTarget = ColorEditTarget("House — Day Color 2", config.houseColorDay2, onHouseColorDay2Change)
                    }
                    ColorSwatchRow("Night Color 2", config.houseColorNight2) {
                        editingTarget = ColorEditTarget("House — Night Color 2", config.houseColorNight2, onHouseColorNight2Change)
                    }

                    SectionTitle("Building colors")
                    Text(
                        "Each building randomly uses Color 1 or Color 2, and blends into its night version as it gets dark.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    ColorSwatchRow("Day Color 1", config.buildingColorDay1) {
                        editingTarget = ColorEditTarget("Building — Day Color 1", config.buildingColorDay1, onBuildingColorDay1Change)
                    }
                    ColorSwatchRow("Night Color 1", config.buildingColorNight1) {
                        editingTarget = ColorEditTarget("Building — Night Color 1", config.buildingColorNight1, onBuildingColorNight1Change)
                    }
                    ColorSwatchRow("Day Color 2", config.buildingColorDay2) {
                        editingTarget = ColorEditTarget("Building — Day Color 2", config.buildingColorDay2, onBuildingColorDay2Change)
                    }
                    ColorSwatchRow("Night Color 2", config.buildingColorNight2) {
                        editingTarget = ColorEditTarget("Building — Night Color 2", config.buildingColorNight2, onBuildingColorNight2Change)
                    }

                    OutlinedButton(onClick = onResetToDefaults, modifier = Modifier.fillMaxWidth()) {
                        Text("↺ Reset colors to defaults")
                    }
                }
            }
        }
    }

    editingTarget?.let { target ->
        ColorPickerDialog(
            title = target.label,
            initialColor = target.color,
            onConfirm = { color ->
                target.onChange(color)
                editingTarget = null
            },
            onDismiss = { editingTarget = null },
        )
    }
}

@Composable
private fun ColorSwatchRow(label: String, color: Int, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(color))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
        )
    }
}

private fun colorToHex(color: Int): String = String.format("#%06X", color and 0x00FFFFFF)

private fun parseHexColor(text: String): Int? {
    val cleaned = text.trim().removePrefix("#")
    if (cleaned.length != 6) return null
    return try {
        val rgb = cleaned.toLong(16).toInt()
        (0xFF shl 24) or rgb
    } catch (e: NumberFormatException) {
        null
    }
}

/**
 * A simple HSV color editor: preview swatch, Hue/Saturation/Brightness sliders, and an editable
 * hex field kept in sync both ways -- moving a slider updates the hex text, and typing a valid
 * hex value updates the sliders.
 */
@Composable
private fun ColorPickerDialog(
    title: String,
    initialColor: Int,
    onConfirm: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val initialHsv = remember(initialColor) {
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(initialColor, hsv)
        hsv
    }
    var hue by remember { mutableStateOf(initialHsv[0]) }
    var saturation by remember { mutableStateOf(initialHsv[1]) }
    var brightness by remember { mutableStateOf(initialHsv[2]) }
    var hexInput by remember { mutableStateOf(colorToHex(initialColor)) }

    fun updateFromHsv(h: Float, s: Float, v: Float) {
        hue = h
        saturation = s
        brightness = v
        hexInput = colorToHex(android.graphics.Color.HSVToColor(floatArrayOf(h, s, v)))
    }

    val currentColor = android.graphics.Color.HSVToColor(floatArrayOf(hue, saturation, brightness))

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(currentColor))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                )
                Text("Hue", style = MaterialTheme.typography.labelSmall)
                Slider(value = hue, onValueChange = { updateFromHsv(it, saturation, brightness) }, valueRange = 0f..360f)
                Text("Saturation", style = MaterialTheme.typography.labelSmall)
                Slider(value = saturation, onValueChange = { updateFromHsv(hue, it, brightness) }, valueRange = 0f..1f)
                Text("Brightness", style = MaterialTheme.typography.labelSmall)
                Slider(value = brightness, onValueChange = { updateFromHsv(hue, saturation, it) }, valueRange = 0f..1f)
                OutlinedTextField(
                    value = hexInput,
                    onValueChange = { text ->
                        hexInput = text
                        parseHexColor(text)?.let { parsed ->
                            val hsv = FloatArray(3)
                            android.graphics.Color.colorToHSV(parsed, hsv)
                            hue = hsv[0]; saturation = hsv[1]; brightness = hsv[2]
                        }
                    },
                    label = { Text("Hex") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(currentColor) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
