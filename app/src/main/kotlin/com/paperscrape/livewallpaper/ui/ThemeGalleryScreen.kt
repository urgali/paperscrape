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
import com.paperscrape.livewallpaper.ui.icons.filled.Event
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
import androidx.compose.runtime.LaunchedEffect
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
import com.paperscrape.livewallpaper.engine.ThemePreviewGeometry
import com.paperscrape.livewallpaper.engine.asSaved
import com.paperscrape.livewallpaper.engine.keepCandidate
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.paperscrape.livewallpaper.prefs.uniqueThemeName
import com.paperscrape.livewallpaper.prefs.cutToName
import com.paperscrape.livewallpaper.prefs.toJsonString
import com.paperscrape.livewallpaper.prefs.parseThemeShare
import com.paperscrape.livewallpaper.prefs.ThemeShare
import com.paperscrape.livewallpaper.prefs.ThemeParseResult
import com.paperscrape.livewallpaper.prefs.ThemeImportError
import com.paperscrape.livewallpaper.prefs.BoundedImport
import com.paperscrape.livewallpaper.BuildConfig
import androidx.compose.ui.platform.LocalContext
import com.paperscrape.livewallpaper.ui.icons.outlined.FileOpen
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.rememberLauncherForActivityResult
import android.net.Uri

/**
 * Every theme, built-in and saved, as a grid of previews, reached from the home screen's Theme row.
 *
 * The automatic switch (the home screen row's own preference) is repeated at the top, because this is
 * where the choice it overrides is made. A card is badged "Today" when it is the theme showing now (the
 * calendar's pick, or the user's own when the calendar is not choosing) and outlined when it is the
 * user's own selection: "selected" and "showing now" are two things.
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
    /** "Replace with current" waiting for its answer: the card's theme, its name, and the write that replaces it. */
    var confirmReplace by remember { mutableStateOf<PendingReplace?>(null) }
    // (themeId, displayName) of the theme the user asked to export, held until the file is chosen.
    var exportTarget by remember { mutableStateOf<Pair<String, String>?>(null) }
    var pendingThemeImport by remember { mutableStateOf<Pair<Uri, ThemeShare>?>(null) }
    var themeMessage by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    /**
     * A card tapped while the calendar is choosing another theme today, held until the user answers the
     * question: picked silently, it would be saved and not shown. See [CalendarPickQuestion].
     */
    var pendingPick by remember { mutableStateOf<String?>(null) }
    fun pick(themeId: String) {
        if (SettingsUiModel.pickNeedsCalendarQuestion(calendarThemeId, themeId)) {
            pendingPick = themeId
        } else {
            scope.launch { prefs.setTheme(themeId) }
        }
    }

    val themeExportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri == null || target == null) return@rememberLauncherForActivityResult
        scope.launch {
            themeMessage = runCatching {
                val (id, name) = target
                // Package what this theme looks like *now*, resolved the way the wallpaper resolves
                // it, so the file is the theme the user is actually looking at.
                val look = CustomThemeRegistry.resolveActiveCustomization(
                    id, settings.pendingCustomization, settings.pendingCustomizationThemeId,
                    settings.themeCustomizations,
                )
                // Off the main thread: the file is written through its provider, which for a file on Drive is the network.
                withContext(Dispatchers.IO) {
                    val text = ThemeShare
                        .of(id, name, look, BuildConfig.VERSION_NAME, System.currentTimeMillis())
                        .toJsonString()
                    context.contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                        ?: error("could not open the chosen file for writing")
                }
                "Theme exported. The file contains only the look -- no settings, no location, no API keys."
            }.getOrElse { "Could not export the theme: ${it.message}" }
        }
    }

    val themeImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            // Bounded (BoundedImport), and read and parsed off the main thread: a file on Drive is downloaded inside the read.
            val parsed = withContext(Dispatchers.IO) { parseThemeShare(BoundedImport.readText(context, uri)) }
            when (parsed) {
                is ThemeParseResult.Ok -> pendingThemeImport = uri to parsed.share
                is ThemeParseResult.Failed -> themeMessage = describeTheme(parsed.error)
            }
        }
    }

    SettingsSubScreen(title = "Themes", onBack = onBack) {
        SettingsGroup(modifier = Modifier.padding(top = 12.dp)) {
            SettingsSwitchRow(
                title = "Automatic theme by date",
                supporting = if (calendarThemeId != null) {
                    "On - the calendar is choosing ${ThemeCatalog.byId(calendarThemeId).displayName} today"
                } else {
                    SettingsUiModel.autoThemeLine(settings.seasonalCalendar, overridingYourPick = false)
                },
                icon = Icons.Filled.Event,
                checked = settings.autoThemeByDate,
                onCheckedChange = { scope.launch { prefs.setAutoThemeByDate(it) } },
            )
        }
        SettingsCaption(SettingsUiModel.autoThemeCaption(settings.seasonalCalendar))

        // Each card shows what the wallpaper would draw for that theme: the engine's resolution (an edit in
        // progress, then the theme's own edits, then a saved copy, then the defaults).
        fun cardCustomization(id: String) = CustomThemeRegistry.resolveActiveCustomization(
            id,
            settings.pendingCustomization,
            settings.pendingCustomizationThemeId,
            settings.themeCustomizations,
        )

        SettingsSectionHeader("Built-in")
        ThemeGrid(items = ThemeCatalog.ALL) { builtin ->
            val overrideEntry = customThemeData.overrides[builtin.id]
            ThemeCard(
                theme = overrideEntry?.theme ?: builtin,
                customization = cardCustomization(builtin.id),
                selected = settings.themeId == builtin.id,
                showingToday = effectiveThemeId == builtin.id,
                isCustomized = overrideEntry != null,
                onSelect = { pick(builtin.id) },
                onReplaceWithCurrent = {
                    confirmReplace = PendingReplace(
                        targetId = builtin.id,
                        targetName = overrideEntry?.name ?: builtin.displayName,
                        targetIsBuiltIn = true,
                    ) {
                        customThemeStore.setOverride(
                            builtin.id,
                            snapshotEntry(
                                builtin.id,
                                ThemeCatalog.byId(builtin.id).displayName,
                                effectiveThemeId,
                                settings.pendingCustomization,
                                settings.pendingCustomizationThemeId,
                                settings.themeCustomizations,
                            ),
                        )
                    }
                },
                onReset = if (overrideEntry != null) ({ confirmReset = builtin.id }) else null,
                onRename = null,
                onDelete = null,
                onExport = {
                    val name = overrideEntry?.name ?: builtin.displayName
                    exportTarget = builtin.id to name
                    if (!themeExportLauncher.launchOrSay(themeFileName(name), context, MissingApp.FILE_SAVER)) exportTarget = null
                },
            )
        }

        SettingsSectionHeader("Saved by you")
        SettingsGroup {
            SettingsRow(
                title = "Import theme",
                supporting = "Opens a theme file somebody shared and adds it here as a new theme of " +
                    "your own. Nothing you already have is replaced.",
                icon = Icons.Outlined.FileOpen,
                onClick = { themeImportLauncher.launchOrSay(arrayOf("application/json", "text/plain", "*/*"), context, MissingApp.FILE_PICKER) },
            )
        }
        if (customThemeData.customThemes.isNotEmpty()) {
            ThemeGrid(items = customThemeData.customThemes) { entry ->
                ThemeCard(
                    theme = entry.theme,
                    customization = cardCustomization(entry.id),
                    selected = settings.themeId == entry.id,
                    showingToday = effectiveThemeId == entry.id,
                    isCustomized = false,
                    onSelect = { pick(entry.id) },
                    onReplaceWithCurrent = {
                        confirmReplace = PendingReplace(targetId = entry.id, targetName = entry.name, targetIsBuiltIn = false) {
                            customThemeStore.upsertCustomTheme(
                                snapshotEntry(
                                    entry.id,
                                    entry.name,
                                    effectiveThemeId,
                                    settings.pendingCustomization,
                                    settings.pendingCustomizationThemeId,
                                    settings.themeCustomizations,
                                ),
                            )
                        }
                    },
                    onReset = null,
                    onRename = { renameTarget = entry },
                    onDelete = { confirmDelete = entry },
                    onExport = {
                        exportTarget = entry.id to entry.name
                        if (!themeExportLauncher.launchOrSay(themeFileName(entry.name), context, MissingApp.FILE_SAVER)) exportTarget = null
                    },
                )
            }
        }
        SettingsCaption(
            "Saving a new theme, and resetting the saved versions or the current edits of built-in " +
                "themes, live in Advanced & about.",
        )
    }

    pendingPick?.let { themeId ->
        if (calendarThemeId != null) {
            CalendarPickQuestion(
                calendarThemeName = ThemeCatalog.byId(calendarThemeId).displayName,
                onShowIt = {
                    pendingPick = null
                    scope.launch { prefs.setThemeTurningAutoThemeOff(themeId) }
                },
                onCancel = { pendingPick = null },
            )
        } else {
            // The calendar stopped choosing while the question was up (midnight, or the switch
            // turned off from the card above): nothing left to ask, so the pick goes through.
            LaunchedEffect(themeId) {
                prefs.setTheme(themeId)
                pendingPick = null
            }
        }
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

    pendingThemeImport?.let { (_, share) ->
        AlertDialog(
            onDismissRequest = { pendingThemeImport = null },
            title = { Text("Import this theme?") },
            text = {
                Text(
                    share.summary() +
                        "\n\nIt will be added as a new theme of your own. Nothing you already have " +
                        "is changed or replaced.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingThemeImport = null
                    scope.launch {
                        val name = uniqueThemeName(share.name, customThemeData.customThemes.map { it.name })
                        customThemeStore.upsertCustomTheme(
                            share.asNewCustomTheme(CustomThemeStore.newCustomThemeId(), name),
                        )
                        themeMessage = "\"$name\" added to your themes."
                    }
                }) { Text("Import") }
            },
            dismissButton = { TextButton(onClick = { pendingThemeImport = null }) { Text("Cancel") } },
        )
    }

    themeMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { themeMessage = null },
            title = { Text("Themes") },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { themeMessage = null }) { Text("OK") } },
        )
    }

    confirmReset?.let { builtinId ->
        // The saved version and the theme's edits both go, the edits by World & scene's own reset
        // ([resetBuiltinToDefault]): they win over a saved version, so left there they would still show.
        AlertDialog(
            onDismissRequest = { confirmReset = null },
            title = { Text("Reset to default?") },
            text = { Text(SettingsUiModel.galleryResetMessage(ThemeCatalog.byId(builtinId).displayName)) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        resetBuiltinToDefault(
                            builtinId,
                            clearSavedVersion = { customThemeStore.clearOverride(it) },
                            clearEdits = { prefs.resetAllCategories(it) },
                        )
                    }
                    confirmReset = null
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = null }) { Text("Cancel") } },
        )
    }

    confirmReplace?.let { pending ->
        val (title, text) = SettingsUiModel.replaceWithCurrentQuestion(
            targetName = pending.targetName,
            showingName = ThemeCatalog.byId(effectiveThemeId).displayName,
            sameTheme = pending.targetId == effectiveThemeId,
            targetIsBuiltIn = pending.targetIsBuiltIn,
        )
        AlertDialog(
            onDismissRequest = { confirmReplace = null },
            title = { Text(title) },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        replaceWithCurrent(
                            targetId = pending.targetId,
                            showingId = effectiveThemeId,
                            writeSavedVersion = pending.replace,
                            clearEdits = { prefs.resetAllCategories(it) },
                        )
                    }
                    confirmReplace = null
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { confirmReplace = null }) { Text("Cancel") } },
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

/** A "Replace with current" asked about and not answered yet: the card's theme, and the write. */
private class PendingReplace(
    val targetId: String,
    val targetName: String,
    val targetIsBuiltIn: Boolean,
    val replace: suspend () -> Unit,
)

/**
 * The gallery's "Reset to default" for a built-in theme (`DESIGN_NOTES.md` §12, decision 42): the saved version
 * ([clearSavedVersion], `CustomThemeStore.clearOverride`) **and** the edits made to the theme in the
 * menus ([clearEdits], `WallpaperPrefs.resetAllCategories`, the call World & scene's reset makes), so
 * what the theme resolves to is its default. `ResetsDoWhatTheySayTest` resolves it on the app's own store.
 */
internal suspend fun resetBuiltinToDefault(
    builtinId: String,
    clearSavedVersion: suspend (String) -> Unit,
    clearEdits: suspend (String) -> Unit,
) {
    clearEdits(builtinId)
    clearSavedVersion(builtinId)
}

/**
 * The gallery's "Replace with current" once asked: [writeSavedVersion] saves the look showing now as the card's
 * theme, and -- when the card's theme is not the one showing -- the edits made to it in the menus go ([clearEdits],
 * `WallpaperPrefs.resetAllCategories`), because they win over a saved version and would hide what was just saved.
 * The theme showing now keeps its edits: they are what was saved.
 */
internal suspend fun replaceWithCurrent(
    targetId: String,
    showingId: String,
    writeSavedVersion: suspend () -> Unit,
    clearEdits: suspend (String) -> Unit,
) {
    writeSavedVersion()
    if (targetId != showingId) clearEdits(targetId)
}

/** Two items a row in plain Rows, not a LazyVerticalGrid, so it can sit in a scrolling Column; the themes are tens at most. */
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
    customization: SceneCustomization,
    selected: Boolean,
    showingToday: Boolean,
    isCustomized: Boolean,
    onSelect: () -> Unit,
    onReplaceWithCurrent: () -> Unit,
    onReset: (() -> Unit)?,
    onRename: (() -> Unit)?,
    onDelete: (() -> Unit)?,
    /** Packages this theme as a file somebody else can import. Every theme can be exported. */
    onExport: (() -> Unit)? = null,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ThemePreviewGeometry.ASPECT_RATIO)
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
                    if (onExport != null) {
                        DropdownMenuItem(
                            text = { Text("Export theme") },
                            onClick = { menuExpanded = false; onExport() },
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
                onValueChange = { text = cutToName(it) },
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
    themeCustomizations: Map<String, SceneCustomization> = emptyMap(),
): CustomThemeEntry {
    val theme = ThemeCatalog.byId(sourceThemeId).copy(id = targetId, displayName = targetName)
    val rawLayout = SceneObjectCatalog.layoutFor(sourceThemeId, theme.accentColor)
    // What is live for sourceThemeId now (amounts, switches, colours), so the saved theme looks as it does on screen.
    val activeCustomization = CustomThemeRegistry.resolveActiveCustomization(
        themeId = sourceThemeId,
        pendingCustomization = pendingCustomization,
        pendingThemeId = pendingCustomizationThemeId,
        themeCustomizations = themeCustomizations,
    )
    val layout = SceneObjectLayout(
        // Thinned by the rule the source layout is drawn with, and saved with that rule, so what stood on screen is
        // saved. The seasonal decorations' candidates are all saved, standing or not: by them the catalogue finds the
        // theme they were dealt for, and the saved theme's sliders reach every one
        // ([SceneObjectCatalog.isSeasonalDecorationCandidate]; `DESIGN_NOTES.md` §12, decision 89).
        staticObjects = rawLayout.staticObjects.filter {
            SceneObjectCatalog.isSeasonalDecorationCandidate(it) || activeCustomization.keepCandidate(it, rawLayout.densityScheme)
        },
        densityScheme = rawLayout.densityScheme,
        // **The cars are saved whole, not filtered**: the road is terrain (`SceneObjectRenderer.hasRoad` is
        // `layout.cars.isNotEmpty()`, and the lane pair comes from the whole list, `SceneSpace.roadEdgeMarginFraction`),
        // so a list thinned at save would lose the road for good, at 10 % or with Cars off. The saved theme still shows
        // the same traffic: `keptCars` applies `activeCustomization` to the full list at render time
        // (`SavedThemeCarLayoutTest`).
        cars = rawLayout.cars,
        // A place the loader moved on a saved street is saved where that theme stored it: the copy keeps its
        // buildings' colours and people, and its next load moves the place the same way.
        placesAsSaved = rawLayout.placesAsSaved,
    // Only the scarecrows go ([asSaved]): the catalogue lays them again on the snowmen the layout carries.
    ).asSaved()
    return CustomThemeEntry(id = targetId, name = targetName, theme = theme, layout = layout, customization = activeCustomization)
}

/** Each theme-import refusal, as a sentence. */
internal fun describeTheme(error: ThemeImportError): String = when (error) {
    ThemeImportError.NotJson ->
        "That file is not a PaperScrape theme -- it could not be read at all. Nothing changed."
    is ThemeImportError.WrongKind ->
        if (error.found == "paperscrape-app-backup") {
            "That is a whole-app backup, not a single theme. Restore it from Advanced & about " +
                "instead. Nothing changed."
        } else {
            "That file is not a PaperScrape theme. Nothing changed."
        }
    is ThemeImportError.TooNew ->
        "That theme was exported by a newer version of PaperScrape (format ${error.fileVersion}, " +
            "this build reads ${error.supported}). Update the app and try again. Nothing changed."
    is ThemeImportError.Malformed ->
        "That theme file is incomplete or damaged (${error.what}). Nothing changed."
}

/** A file name a user will recognise: the theme's own name, made safe for a filesystem. */
internal fun themeFileName(themeName: String): String {
    val safe = themeName.lowercase().map { if (it.isLetterOrDigit()) it else '-' }
        .joinToString("").trim('-').replace(Regex("-+"), "-")
    return "paperscrape-theme-${safe.ifBlank { "theme" }}.json"
}
