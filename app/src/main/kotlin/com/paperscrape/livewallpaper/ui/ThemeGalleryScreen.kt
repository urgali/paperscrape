package com.paperscrape.livewallpaper.ui

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Event
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
import com.paperscrape.livewallpaper.engine.SceneTheme
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.keepCandidate
import com.paperscrape.livewallpaper.engine.keepCar
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Every theme, built-in and saved, as a grid of previews.
 *
 * Reached from the home screen's Theme row, which is where a user looks for it -- not from
 * Advanced, where only the maintenance actions live. Two things are new here and both are about
 * making the calendar comprehensible rather than changing it:
 *
 * - the automatic switch is repeated at the top of this screen, because this is where the choice
 *   it overrides is being made. It is the same preference as the home screen's row, not a copy;
 * - a card is badged "Today" when the calendar picked it and outlined when it is the user's own
 *   selection, so "selected" and "showing right now" stop being the same word.
 */
@Composable
internal fun ThemeGalleryScreen(
    settings: WallpaperSettings,
    customThemeData: CustomThemeData,
    effectiveThemeId: String,
    calendarThemeId: String?,
    prefs: WallpaperPrefs,
    customThemeStore: CustomThemeStore,
    scope: CoroutineScope,
    onBack: () -> Unit,
) {
    var renameTarget by remember { mutableStateOf<CustomThemeEntry?>(null) }
    var confirmReset by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<CustomThemeEntry?>(null) }

    SettingsSubScreen(title = "Themes", onBack = onBack) {
        SettingsGroup(modifier = Modifier.padding(top = 12.dp)) {
            SettingsSwitchRow(
                title = "Automatic theme by date",
                supporting = if (calendarThemeId != null) {
                    "On - the calendar is choosing ${ThemeCatalog.byId(calendarThemeId).displayName} today"
                } else {
                    "The calendar picks a theme for every day of the year"
                },
                icon = Icons.Filled.Event,
                checked = settings.autoThemeByDate,
                onCheckedChange = { scope.launch { prefs.setAutoThemeByDate(it) } },
            )
        }
        SettingsCaption(
            "While this is on, the calendar picks the theme. The theme you choose below is the one used " +
                "whenever you turn it off.",
        )

        SettingsSectionHeader("Built-in")
        ThemeGrid(items = ThemeCatalog.ALL) { builtin ->
            val overrideEntry = customThemeData.overrides[builtin.id]
            ThemeCard(
                theme = overrideEntry?.theme ?: builtin,
                customization = overrideEntry?.customization,
                selected = settings.themeId == builtin.id,
                showingToday = effectiveThemeId == builtin.id,
                isCustomized = overrideEntry != null,
                onSelect = { scope.launch { prefs.setTheme(builtin.id) } },
                onReplaceWithCurrent = {
                    scope.launch {
                        customThemeStore.setOverride(
                            builtin.id,
                            snapshotEntry(
                                builtin.id,
                                ThemeCatalog.byId(builtin.id).displayName,
                                effectiveThemeId,
                                settings.pendingCustomization,
                                settings.pendingCustomizationThemeId,
                            ),
                        )
                    }
                },
                onReset = if (overrideEntry != null) ({ confirmReset = builtin.id }) else null,
                onRename = null,
                onDelete = null,
            )
        }

        if (customThemeData.customThemes.isNotEmpty()) {
            SettingsSectionHeader("Saved by you")
            ThemeGrid(items = customThemeData.customThemes) { entry ->
                ThemeCard(
                    theme = entry.theme,
                    customization = entry.customization,
                    selected = settings.themeId == entry.id,
                    showingToday = effectiveThemeId == entry.id,
                    isCustomized = false,
                    onSelect = { scope.launch { prefs.setTheme(entry.id) } },
                    onReplaceWithCurrent = {
                        scope.launch {
                            customThemeStore.upsertCustomTheme(
                                snapshotEntry(
                                    entry.id,
                                    entry.name,
                                    effectiveThemeId,
                                    settings.pendingCustomization,
                                    settings.pendingCustomizationThemeId,
                                ),
                            )
                        }
                    },
                    onReset = null,
                    onRename = { renameTarget = entry },
                    onDelete = { confirmDelete = entry },
                )
            }
        }
        SettingsCaption("Saving a new theme and resetting customised ones live in Advanced & about.")
    }

    renameTarget?.let { entry ->
        NameInputDialog(
            title = "Rename theme",
            initialName = entry.name,
            onConfirm = { name ->
                scope.launch { customThemeStore.renameCustomTheme(entry.id, name) }
                renameTarget = null
            },
            onDismiss = { renameTarget = null },
        )
    }

    confirmReset?.let { builtinId ->
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text("Reset to default?") },
            text = { Text("This removes your custom version of \"${ThemeCatalog.byId(builtinId).displayName}\" and restores the original.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { customThemeStore.clearOverride(builtinId) }
                    confirmReset = null
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Cancel") } },
        )
    }

    confirmDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("Delete theme?") },
            text = { Text("\"${entry.name}\" will be permanently deleted. This can't be undone.") },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch { customThemeStore.deleteCustomTheme(entry.id) }
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = null }) { Text("Cancel") } },
        )
    }
}

/** Lays items out 2-per-row using plain Row/Column chunks (not LazyVerticalGrid) so this can
 * live inside an already-vertically-scrolling Column without nested-scroll conflicts -- the
 * theme count here is always small (tens at most), so the lack of lazy virtualization is fine. */
@Composable
private fun <T> ThemeGrid(items: List<T>, content: @Composable (T) -> Unit) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
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
private fun ThemeCard(
    theme: SceneTheme,
    customization: SceneCustomization?,
    selected: Boolean,
    showingToday: Boolean,
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
            ThemeScenePreview(theme = theme, modifier = Modifier.fillMaxSize(), customization = customization)

            val badge = when {
                showingToday -> "Today"
                isCustomized -> "Customised"
                else -> null
            }
            if (badge != null) {
                Surface(
                    color = if (showingToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondaryContainer,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp),
                ) {
                    Text(
                        badge,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (showingToday) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }

            Box(modifier = Modifier.align(Alignment.TopEnd)) {
                IconButton(
                    onClick = { menuExpanded = true },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Theme actions", tint = Color.White)
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
            theme.displayName + if (selected) " - selected" else "",
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Text entry for saving or renaming a theme. */
@Composable
internal fun NameInputDialog(
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
            TextButton(onClick = { if (text.isNotBlank()) onConfirm(text.trim()) }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/**
 * Snapshots whatever theme+layout [sourceThemeId] currently resolves to, relabeled as
 * [targetId]/[targetName] -- the basis for both "save as new theme" and "replace with current".
 */
internal fun snapshotEntry(
    targetId: String,
    targetName: String,
    sourceThemeId: String,
    pendingCustomization: SceneCustomization,
    pendingCustomizationThemeId: String?,
): CustomThemeEntry {
    val theme = ThemeCatalog.byId(sourceThemeId).copy(id = targetId, displayName = targetName)
    val rawLayout = SceneObjectCatalog.layoutFor(sourceThemeId, theme.accentColor)
    // Bake in whatever's currently live for sourceThemeId (density/visibility filtering + the
    // exact colors), so "what you see is what you save" -- the saved theme keeps looking like
    // this even if you later edit scene objects for some other theme.
    val activeCustomization = CustomThemeRegistry.resolveActiveCustomization(
        themeId = sourceThemeId,
        pendingCustomization = pendingCustomization,
        pendingThemeId = pendingCustomizationThemeId,
    )
    val layout = SceneObjectLayout(
        staticObjects = rawLayout.staticObjects.filter { activeCustomization.keepCandidate(it) },
        cars = rawLayout.cars.filter { activeCustomization.keepCar(it) },
    )
    return CustomThemeEntry(id = targetId, name = targetName, theme = theme, layout = layout, customization = activeCustomization)
}
