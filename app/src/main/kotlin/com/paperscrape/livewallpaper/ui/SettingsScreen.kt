package com.paperscrape.livewallpaper.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.paperscrape.livewallpaper.BuildConfig
import com.paperscrape.livewallpaper.R
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.CustomThemeRegistry
import com.paperscrape.livewallpaper.engine.ObjectVariantConfig
import com.paperscrape.livewallpaper.engine.MountainLayerConfig
import com.paperscrape.livewallpaper.engine.LakeConfig
import com.paperscrape.livewallpaper.engine.BirdsConfig
import com.paperscrape.livewallpaper.engine.BirdColorWeight
import com.paperscrape.livewallpaper.engine.StarsConfig
import com.paperscrape.livewallpaper.engine.SkyConfig
import com.paperscrape.livewallpaper.engine.SunConfig
import com.paperscrape.livewallpaper.engine.MoonConfig
import com.paperscrape.livewallpaper.engine.CloudsConfig
import com.paperscrape.livewallpaper.engine.PrecipitationType
import com.paperscrape.livewallpaper.engine.RandomSceneGenerator
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
import com.paperscrape.livewallpaper.engine.CanvasSceneTarget
import com.paperscrape.livewallpaper.engine.SceneObjectRenderer
import com.paperscrape.livewallpaper.engine.SceneTheme
import com.paperscrape.livewallpaper.engine.SeasonalThemeRules
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.keepCandidate
import com.paperscrape.livewallpaper.engine.keepCar
import com.paperscrape.livewallpaper.location.LocationLabelResolver
import com.paperscrape.livewallpaper.prefs.CustomThemeStore
import com.paperscrape.livewallpaper.prefs.ObjectCategory
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.update.UpdateChecker
import com.paperscrape.livewallpaper.update.UpdateInfo
import com.paperscrape.livewallpaper.update.UpdatePrefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    prefs: WallpaperPrefs,
    customThemeStore: CustomThemeStore,
    updatePrefs: UpdatePrefs,
    onApplyWallpaper: () -> Unit,
    onRequestLocationPermission: (onResult: (Boolean) -> Unit) -> Unit,
) {
    val settings by prefs.settingsFlow.collectAsState(initial = WallpaperSettings())
    val customThemeData by customThemeStore.dataFlow.collectAsState(initial = CustomThemeData())
    val scope = rememberCoroutineScope()
    var showThemeManager by remember { mutableStateOf(false) }
    var showSceneObjects by remember { mutableStateOf(false) }
    var showSeasonalDecorations by remember { mutableStateOf(false) }
    // Checked once per app launch (LaunchedEffect(Unit) runs exactly once for this composition),
    // never as a background/recurring check -- this is deliberately an in-app-only prompt, not a
    // system notification, per the requirement that it must not nag the user outside the app.
    var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }
    var showSnoozeChoice by remember { mutableStateOf(false) }
    var manualCheckInProgress by remember { mutableStateOf(false) }
    var manualCheckUpToDateMessage by remember { mutableStateOf<String?>(null) }
    // **No automatic check.** Opening the settings screen used to reach the network every time,
    // which is a request the user never made, for a feature they may not want. The check now runs
    // only if they have opted in, and the manual button below works whether they have or not.
    LaunchedEffect(settings.automaticUpdateCheckEnabled) {
        if (!settings.automaticUpdateCheckEnabled) return@LaunchedEffect
        val snooze = updatePrefs.readSnoozeState()
        val update = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME) ?: return@LaunchedEffect
        val isSnoozedForThisVersion = snooze.versionTag == update.tagName && System.currentTimeMillis() < snooze.untilMillis
        if (!isSnoozedForThisVersion) {
            availableUpdate = update
        }
    }

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
                onClick = { showSceneObjects = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("🎨 Scene objects (houses, cars, trees...)")
            }
            OutlinedButton(
                onClick = { showSeasonalDecorations = true },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("🎃 Seasonal decorations (pumpkins, snowmen, gifts...)")
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
                    PreferenceSlider(
                        label = { shown -> Text("Fixed time: ${shown.toInt()}:00", style = MaterialTheme.typography.bodyMedium) },
                        value = settings.fixedHour,
                        onCommit = { committed -> scope.launch { prefs.setFixedHour(committed) } },
                        valueRange = 0f..23f,
                        steps = 22,
                    )
                }
            } else {
                SettingSwitchRow(
                    title = "🛰️ Weather and sunrise/sunset (phone location)",
                    subtitle = "Uses your device's real position for precise sun/moon times, and " +
                        "will drive Live Weather once that's set up too. Mutually exclusive with " +
                        "a manually-entered custom location.",
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
                if (settings.useLocationForSunTimes) {
                    LocationLabel(
                        latitude = settings.resolvedGpsLatitude,
                        longitude = settings.resolvedGpsLongitude,
                        loadingText = "Finding your location…",
                    )
                }
                SettingSwitchRow(
                    title = "📍 Weather and sunrise/sunset (custom location)",
                    subtitle = "Uses a location you enter below instead of the device's real " +
                        "position. Mutually exclusive with the phone-location toggle above.",
                    checked = settings.useCustomLocation,
                    onCheckedChange = { enabled -> scope.launch { prefs.setUseCustomLocation(enabled) } },
                )
                if (settings.useCustomLocation) {
                    CustomLocationFields(
                        latitude = settings.customLocationLatitude,
                        longitude = settings.customLocationLongitude,
                        label = settings.customLocationLabel,
                        onApply = { lat, lon, label -> scope.launch { prefs.setCustomLocation(lat, lon, label) } },
                    )
                    LocationLabel(
                        latitude = settings.customLocationLatitude,
                        longitude = settings.customLocationLongitude,
                        loadingText = "Looking up this location…",
                    )
                }
                SettingSwitchRow(
                    title = stringResource(R.string.live_weather_title),
                    subtitle = if (settings.useLocationForSunTimes || settings.useCustomLocation) {
                        stringResource(R.string.live_weather_desc)
                    } else {
                        stringResource(R.string.live_weather_needs_location)
                    },
                    checked = settings.liveWeatherEnabled,
                    enabled = settings.useLocationForSunTimes || settings.useCustomLocation,
                    onCheckedChange = { scope.launch { prefs.setLiveWeatherEnabled(it) } },
                )
                if (settings.liveWeatherEnabled && settings.liveWeatherFallbackActive) {
                    // Published by the wallpaper service through the same settings flow this
                    // screen already collects, so it appears and clears as the service changes it
                    // -- no polling, no restart. Shown only while Live Weather is on: with it off
                    // there is no fallback to be in.
                    Text(
                        text = stringResource(R.string.live_weather_fallback_notice),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                if (settings.liveWeatherEnabled) {
                    LiveWeatherApiKeyField(
                        apiKey = settings.liveWeatherApiKey,
                        onApply = { key -> scope.launch { prefs.setLiveWeatherApiKey(key) } },
                    )
                }
            }

            Column {
                Text("Parallax strength", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "How far apart near and far layers move relative to each other while scrolling.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PreferenceSlider(
                    value = settings.parallaxStrength,
                    onCommit = { committed -> scope.launch { prefs.setParallaxStrength(committed) } },
                    valueRange = 0.5f..2f,
                )
            }

            Column {
                Text("Scroll speed", style = MaterialTheme.typography.bodyMedium)
                Text(
                    "The scenery drifts by itself at this speed, all the time -- separate from swiping.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                PreferenceSlider(
                    value = settings.scrollSpeed,
                    onCommit = { committed -> scope.launch { prefs.setScrollSpeed(committed) } },
                    valueRange = 0f..1f,
                )
            }

            SettingSwitchRow(
                title = "Scroll background",
                subtitle = "Whether the sky, sun, and moon scroll too, or stay fixed while only the ground and objects move",
                checked = settings.scrollBackground,
                onCheckedChange = { scope.launch { prefs.setScrollBackground(it) } },
            )

            SettingSwitchRow(
                title = "Swipe scroll",
                subtitle = "Whether swiping between home screens also scrolls the wallpaper (on top of the constant drift above)",
                checked = settings.swipeScroll,
                onCheckedChange = { scope.launch { prefs.setSwipeScroll(it) } },
            )

            Text(
                "PaperScrape is an open-source live wallpaper inspired by classic \"paper cutout\" animated backgrounds. Source code on GitHub.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                // The release, then the install counter in brackets. It read "v1 (1.0)" under the
                // semver scheme, which names the release with the wrong number.
                "Version v${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            SettingSwitchRow(
                title = stringResource(R.string.settings_auto_update_check_title),
                subtitle = stringResource(R.string.settings_auto_update_check_subtitle),
                checked = settings.automaticUpdateCheckEnabled,
                onCheckedChange = { scope.launch { prefs.setAutomaticUpdateCheckEnabled(it) } },
            )
            OutlinedButton(
                onClick = {
                    manualCheckUpToDateMessage = null
                    manualCheckInProgress = true
                    scope.launch {
                        val update = UpdateChecker.checkForUpdate(BuildConfig.VERSION_NAME)
                        manualCheckInProgress = false
                        if (update != null) {
                            availableUpdate = update
                        } else {
                            manualCheckUpToDateMessage = "You're up to date (v${BuildConfig.VERSION_NAME})"
                        }
                    }
                },
                enabled = !manualCheckInProgress,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (manualCheckInProgress) "Checking…" else "🔄 Check for updates")
            }
            manualCheckUpToDateMessage?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
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
                    customThemeStore.upsertCustomTheme(
                        snapshotEntry(id, name, effectiveThemeId, settings.pendingCustomization, settings.pendingCustomizationThemeId),
                    )
                }
            },
            onReplaceBuiltinWithCurrent = { builtinId ->
                scope.launch {
                    val name = ThemeCatalog.byId(builtinId).displayName
                    customThemeStore.setOverride(
                        builtinId,
                        snapshotEntry(builtinId, name, effectiveThemeId, settings.pendingCustomization, settings.pendingCustomizationThemeId),
                    )
                }
            },
            onResetBuiltin = { builtinId -> scope.launch { customThemeStore.clearOverride(builtinId) } },
            onResetAllOverrides = { scope.launch { customThemeStore.clearAllOverrides() } },
            onReplaceCustomWithCurrent = { id, name ->
                scope.launch {
                    customThemeStore.upsertCustomTheme(
                        snapshotEntry(id, name, effectiveThemeId, settings.pendingCustomization, settings.pendingCustomizationThemeId),
                    )
                }
            },
            onRenameCustom = { id, newName -> scope.launch { customThemeStore.renameCustomTheme(id, newName) } },
            onDeleteCustom = { id -> scope.launch { customThemeStore.deleteCustomTheme(id) } },
        )
    }

    if (showSceneObjects) {
        SceneObjectsMenuDialog(
            customization = CustomThemeRegistry.resolveActiveCustomization(
                themeId = effectiveThemeId,
                pendingCustomization = settings.pendingCustomization,
                pendingThemeId = settings.pendingCustomizationThemeId,
            ),
            forThemeId = effectiveThemeId,
            prefs = prefs,
            scope = scope,
            liveWeatherEnabled = settings.liveWeatherEnabled,
            onResetTheme = {
                scope.launch {
                    prefs.resetAllCategories()
                    // "Reset everything to defaults" clearing only the in-progress scratch edit
                    // wasn't enough on its own: resolveActiveCustomization() checks a *saved*
                    // override for this theme *before* the scratch space, so if this built-in
                    // theme was ever overridden via "Manage Themes", the reset appeared to do
                    // nothing at all -- the saved override kept winning regardless. Clear that
                    // too, if one exists, so "reset" actually reverts to the theme's true
                    // built-in look.
                    if (customThemeData.overrides.containsKey(effectiveThemeId)) {
                        customThemeStore.clearOverride(effectiveThemeId)
                    }
                }
            },
            onDismiss = { showSceneObjects = false },
        )
    }

    if (showSeasonalDecorations) {
        SeasonalDecorationsDialog(
            customization = CustomThemeRegistry.resolveActiveCustomization(
                themeId = effectiveThemeId,
                pendingCustomization = settings.pendingCustomization,
                pendingThemeId = settings.pendingCustomizationThemeId,
            ),
            forThemeId = effectiveThemeId,
            prefs = prefs,
            scope = scope,
            onDismiss = { showSeasonalDecorations = false },
        )
    }

    availableUpdate?.let { update ->
        val context = LocalContext.current
        if (!showSnoozeChoice) {
            AlertDialog(
                onDismissRequest = { /* not dismissible by tapping outside -- must pick an option */ },
                title = { Text("Update available") },
                text = {
                    Column {
                        Text("${update.tagName} is available (you have v${BuildConfig.VERSION_NAME}).")
                        val notes = update.releaseNotes
                        if (!notes.isNullOrBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text("What's new:", style = MaterialTheme.typography.labelLarge)
                            Spacer(modifier = Modifier.height(4.dp))
                            Column(
                                modifier = Modifier
                                    .heightIn(max = 340.dp)
                                    .verticalScroll(rememberScrollState()),
                            ) {
                                Text(notes, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(update.releasePageUrl)))
                            availableUpdate = null
                        },
                    ) { Text("Update now") }
                },
                dismissButton = {
                    TextButton(onClick = { showSnoozeChoice = true }) { Text("Remind me later") }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { showSnoozeChoice = false },
                title = { Text("Remind me...") },
                text = { Text("When should PaperScrape ask again about ${update.tagName}?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            scope.launch { updatePrefs.snoozeForOneMonth(update.tagName) }
                            showSnoozeChoice = false
                            availableUpdate = null
                        },
                    ) { Text("In a month") }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            scope.launch { updatePrefs.snoozeUntilNextLaunch() }
                            showSnoozeChoice = false
                            availableUpdate = null
                        },
                    ) { Text("Next app launch") }
                },
            )
        }
    }
}

/** Snapshots whatever theme+layout [sourceThemeId] currently resolves to, relabeled as
 * [targetId]/[targetName] -- the basis for both "save as new theme" and "replace with current". */
private fun snapshotEntry(
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

@Composable
private fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
}

/**
 * Latitude/longitude/label entry for "Weather and sunrise/sunset (custom location)". Local text
 * state (not committed to prefs on every keystroke, unlike this file's usual pattern of firing a
 * prefs write per Slider/Switch change) because a lat/long is only valid once fully typed --
 * writing "4" then "45" then "45." etc as separate coordinate values as the user types would spam
 * invalid/incomplete fixes through to the sunrise/sunset (and later Live Weather) calculation on
 * every keystroke. Committed via the explicit "Apply" button instead, same reasoning as why a hex
 * color field in [ColorPickerDialog] commits on "OK" rather than per-keystroke.
 */
@Composable
private fun CustomLocationFields(
    latitude: Float,
    longitude: Float,
    label: String,
    onApply: (Float, Float, String) -> Unit,
) {
    var latText by remember(latitude) { mutableStateOf(latitude.toString()) }
    var lonText by remember(longitude) { mutableStateOf(longitude.toString()) }
    var labelText by remember(label) { mutableStateOf(label) }
    val parsedLat = latText.toFloatOrNull()
    val parsedLon = lonText.toFloatOrNull()
    val isValid = parsedLat != null && parsedLat in -90f..90f && parsedLon != null && parsedLon in -180f..180f
    val context = LocalContext.current

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = labelText,
            onValueChange = { labelText = it },
            label = { Text("Location name (optional, just a label)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = latText,
                onValueChange = { latText = it },
                label = { Text("Latitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = parsedLat == null || parsedLat !in -90f..90f,
            )
            OutlinedTextField(
                value = lonText,
                onValueChange = { lonText = it },
                label = { Text("Longitude") },
                modifier = Modifier.weight(1f),
                singleLine = true,
                isError = parsedLon == null || parsedLon !in -180f..180f,
            )
        }
        Button(
            onClick = {
                if (isValid) {
                    onApply(parsedLat!!, parsedLon!!, labelText)
                    // aa reported that applying a manual location gave no confirmation it had
                    // actually taken effect. A Toast is the right fit here specifically because
                    // this Column already renders its own persistent on-screen confirmation right
                    // below (LocationLabel, reverse-geocoding these same coordinates) -- the Toast
                    // is just the *immediate* "yes, that click registered" feedback the moment of
                    // tapping Apply needs, while LocationLabel is the lasting proof once resolved.
                    Toast.makeText(context, "Location applied", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Apply location")
        }
    }
}

/**
 * Reverse-geocodes a lat/long into a short city label ("Florence, Italy") and shows it as a
 * standing confirmation under the corresponding location toggle -- aa's own explicit ask was
 * that both the GPS and custom-location toggles visibly report *which place* they resolved to,
 * not just that some coordinates are set. Re-resolves whenever the coordinates actually change
 * (`latitude`/`longitude` as the LaunchedEffect key), not on every recomposition.
 */
@Composable
private fun LocationLabel(latitude: Float?, longitude: Float?, loadingText: String) {
    val context = LocalContext.current
    var label by remember(latitude, longitude) { mutableStateOf<String?>(null) }
    var isLoading by remember(latitude, longitude) { mutableStateOf(latitude != null && longitude != null) }
    LaunchedEffect(latitude, longitude) {
        if (latitude == null || longitude == null) return@LaunchedEffect
        label = LocationLabelResolver.resolveCityLabel(context, latitude.toDouble(), longitude.toDouble())
        isLoading = false
    }
    val text = when {
        latitude == null || longitude == null -> null
        isLoading -> loadingText
        label != null -> "📌 $label"
        else -> "📌 ${"%.2f".format(latitude)}, ${"%.2f".format(longitude)}" // geocoding failed -- raw coordinates as a fallback, never a blank row
    }
    if (text != null) {
        Text(
            text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
        )
    }
}

/**
 * Optional user-entered Open-Meteo API key for Live Weather -- always takes priority over the
 * app's own baked-in key when set (see WeatherRepository.resolveApiKey). Blank is a perfectly
 * valid, fully-supported state: Open-Meteo's free tier needs no key at all, so this field exists
 * purely as an upgrade path for a user who wants Open-Meteo's higher-limit customer endpoint
 * under their own account, not a requirement to make Live Weather work.
 */
@Composable
private fun LiveWeatherApiKeyField(apiKey: String, onApply: (String) -> Unit) {
    var text by remember(apiKey) { mutableStateOf(apiKey) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            "Optional: your own Open-Meteo API key. Leave blank to use the app's built-in key " +
                "(or Open-Meteo's free tier if none is built in) -- entering your own always takes priority.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("Open-Meteo API key (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(onClick = { onApply(text) }, modifier = Modifier.fillMaxWidth()) {
            Text("Save API key")
        }
    }
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
 * Draws a small but honest preview of a theme's *actual* look: sky gradient, a single hill
 * silhouette (using the theme's real day color -- matches the actual on-screen render, which is
 * one cohesive hill layer, not a stack), and the sun — instead of a flat color swatch that hides
 * what the theme really looks like once applied.
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

        // Only the first color is ever actually used at render time (see
        // SceneCustomization.hillsColorDay = theme.hillColorsDay.firstOrNull()) -- the real scene
        // is one hill layer now, not three, so the preview shouldn't show extra stacked bands
        // that don't exist on screen.
        val top = size.height * 0.60f
        drawRect(
            color = Color(theme.hillColorsDay.firstOrNull() ?: 0xFF8A7355.toInt()),
            topLeft = Offset(0f, top),
            size = androidx.compose.ui.geometry.Size(size.width, size.height - top),
        )
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
    "halloween" to "🎃🌙",
)

private fun iconHintFor(themeId: String): String = THEME_ICON_HINTS[themeId] ?: "🎨"

@Composable
private fun SettingSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
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
    onResetAllOverrides: () -> Unit,
    onReplaceCustomWithCurrent: (id: String, name: String) -> Unit,
    onRenameCustom: (id: String, newName: String) -> Unit,
    onDeleteCustom: (id: String) -> Unit,
) {
    var showSaveDialog by remember { mutableStateOf(false) }
    var renameTarget by remember { mutableStateOf<CustomThemeEntry?>(null) }
    var confirmReset by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf<CustomThemeEntry?>(null) }
    var confirmResetAll by remember { mutableStateOf(false) }

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
                        .navigationBarsPadding()
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

                    if (customThemeData.overrides.isNotEmpty()) {
                        OutlinedButton(
                            onClick = { confirmResetAll = true },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("↺ Reset all customized themes to default (${customThemeData.overrides.size})")
                        }
                        Text(
                            "A customized theme keeps whatever it looked like when you saved it, even after app updates add new objects to that theme. If a theme seems to be missing things it should have, this fixes it.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
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

                    // Extra breathing room at the very bottom so the last row of cards is never
                    // flush with the screen edge / partially hidden behind the system nav bar.
                    Box(modifier = Modifier.height(56.dp))
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

    if (confirmResetAll) {
        AlertDialog(
            onDismissRequest = { confirmResetAll = false },
            title = { Text("Reset all customized themes?") },
            text = { Text("This removes your custom version of every overridden built-in theme (${customThemeData.overrides.size}) and restores each one's current default look. Your independent custom themes are not affected.") },
            confirmButton = {
                TextButton(onClick = { onResetAllOverrides(); confirmResetAll = false }) { Text("Reset all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetAll = false }) { Text("Cancel") }
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
private fun SceneObjectsMenuDialog(
    customization: SceneCustomization,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    liveWeatherEnabled: Boolean,
    onResetTheme: () -> Unit,
    onDismiss: () -> Unit,
) {
    // Which sub-section (if any) is currently open on top of this menu -- null means just the
    // menu itself is showing. "Scene Objects" used to be one long scrolling screen with every
    // category stacked on top of each other; split into a menu + focused per-category screens
    // (matching a reference app's own navigation, discussed in chat) now that Phase 0 alone
    // brought it to 8 categories, with more still to come in later phases.
    var activeSection by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Scene Objects") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding(),
                ) {
                    SceneObjectLivePreview(customization = customization, modifier = Modifier.fillMaxWidth().padding(16.dp))
                    Text(
                        "These settings apply live to your current theme only — switch themes and they follow the one you're on. Want to keep this look? Save it from \"Manage Themes\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp),
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    SceneObjectsMenuRow("☀️ Sun and Moon") { activeSection = "sunmoon" }
                    SceneObjectsMenuRow("🌌 Sky") { activeSection = "sky" }
                    SceneObjectsMenuRow("⭐ Stars") { activeSection = "stars" }
                    SceneObjectsMenuRow("☁️ Clouds") { activeSection = "clouds" }
                    SceneObjectsMenuRow("🌧️ Precipitation") { activeSection = "precipitation" }
                    SceneObjectsMenuRow("🌈 Rainbow") { activeSection = "rainbow" }
                    SceneObjectsMenuRow("🏙️ Cities (Buildings & Houses)") { activeSection = "cities" }
                    SceneObjectsMenuRow("⛰️ Hills") { activeSection = "hills" }
                    SceneObjectsMenuRow("🏔️ Mountains") { activeSection = "mountains" }
                    SceneObjectsMenuRow("🌳 Trees") { activeSection = "trees" }
                    SceneObjectsMenuRow("⛱️ Umbrellas") { activeSection = "umbrellas" }
                    SceneObjectsMenuRow("🌊 Lakes, Boats and Dolphins") { activeSection = "lake" }
                    SceneObjectsMenuRow("🚗 Cars") { activeSection = "cars" }
                    SceneObjectsMenuRow("🚶 People") { activeSection = "people" }
                    SceneObjectsMenuRow("🐦 Birds") { activeSection = "birds" }

                    OutlinedButton(
                        onClick = onResetTheme,
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                    ) {
                        Text("↺ Reset everything to defaults")
                    }

                    Box(modifier = Modifier.height(56.dp))
                }
            }
        }
    }

    when (activeSection) {
        "sunmoon" -> SunMoonSubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
        "sky" -> SkySubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
        "stars" -> StarsSubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
        "clouds" -> CloudsSubDialog(customization, forThemeId, prefs, scope, liveWeatherEnabled) { activeSection = null }
        "precipitation" -> PrecipitationSubDialog(customization, forThemeId, prefs, scope, liveWeatherEnabled) { activeSection = null }
        "rainbow" -> RainbowSubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
        "cities" -> CitiesSubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
        "hills" -> HillsSubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
        "mountains" -> MountainsSubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
        "trees" -> TreesSubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
        "umbrellas" -> UmbrellasSubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
        "lake" -> LakeSubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
        "cars" -> CarsSubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
        "people" -> PeopleSubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
        "birds" -> BirdsSubDialog(customization, forThemeId, prefs, scope) { activeSection = null }
    }
}

@Composable
private fun SceneObjectsMenuRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    HorizontalDivider()
}

/** Shared shell every "Scene Objects" sub-screen uses: full-screen dialog, back arrow (not an X
 * -- this is a drill-down from the menu, not an independent screen), a scrollable column body. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SceneObjectSubScreenShell(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onBack, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(title) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    content = content,
                )
            }
        }
    }
}

@Composable
private fun SunMoonSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SceneObjectSubScreenShell("Sun and Moon", onBack) {
        SectionTitle("Sun")
        SettingSwitchRow(
            title = "Show Sun", subtitle = "",
            checked = customization.sun.visible,
            onCheckedChange = { scope.launch { prefs.setSunVisible(it, forThemeId) } },
        )
        ColorSwatchRow("Sun Color", customization.sun.color) {
            editingTarget = ColorEditTarget("Sun Color", customization.sun.color) { c -> scope.launch { prefs.setSunColor(c, forThemeId) } }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle("Moon")
        SettingSwitchRow(
            title = "Show Moon", subtitle = "",
            checked = customization.moon.visible,
            onCheckedChange = { scope.launch { prefs.setMoonVisible(it, forThemeId) } },
        )
        SettingSwitchRow(
            title = "Realistic Moon Phases", subtitle = "Show real moon phases at night",
            checked = customization.moon.realisticPhases,
            onCheckedChange = { scope.launch { prefs.setMoonRealisticPhases(it, forThemeId) } },
        )
        ColorSwatchRow("Moon Color", customization.moon.color) {
            editingTarget = ColorEditTarget("Moon Color", customization.moon.color) { c -> scope.launch { prefs.setMoonColor(c, forThemeId) } }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text("Sun/Cloud Height: ${(customization.sky.sunCloudHeight * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Text(
            "How high the sun and moon's arc rises across the sky.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreferenceSlider(
            value = customization.sky.sunCloudHeight,
            onCommit = { committed -> scope.launch { prefs.setSkySunCloudHeight(committed, forThemeId) } },
            valueRange = 0.1f..0.6f,
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun SkySubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SceneObjectSubScreenShell("Sky", onBack) {
        ColorSwatchRow("Day Color High", customization.sky.colorDayHigh) {
            editingTarget = ColorEditTarget("Sky — Day Color High", customization.sky.colorDayHigh) { c -> scope.launch { prefs.setSkyColorDayHigh(c, forThemeId) } }
        }
        ColorSwatchRow("Day Color Low", customization.sky.colorDayLow) {
            editingTarget = ColorEditTarget("Sky — Day Color Low", customization.sky.colorDayLow) { c -> scope.launch { prefs.setSkyColorDayLow(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color High", customization.sky.colorNightHigh) {
            editingTarget = ColorEditTarget("Sky — Night Color High", customization.sky.colorNightHigh) { c -> scope.launch { prefs.setSkyColorNightHigh(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color Low", customization.sky.colorNightLow) {
            editingTarget = ColorEditTarget("Sky — Night Color Low", customization.sky.colorNightLow) { c -> scope.launch { prefs.setSkyColorNightLow(c, forThemeId) } }
        }
        ColorSwatchRow("Sunrise Color Low", customization.sky.colorSunriseLow) {
            editingTarget = ColorEditTarget("Sky — Sunrise Color Low", customization.sky.colorSunriseLow) { c -> scope.launch { prefs.setSkyColorSunriseLow(c, forThemeId) } }
        }
        ColorSwatchRow("Sunset Color Low", customization.sky.colorSunsetLow) {
            editingTarget = ColorEditTarget("Sky — Sunset Color Low", customization.sky.colorSunsetLow) { c -> scope.launch { prefs.setSkyColorSunsetLow(c, forThemeId) } }
        }
        Text(
            "\"High\" is the top of the sky, \"Low\" is near the horizon. Sunrise/Sunset colors only show briefly near the horizon around those times.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun StarsSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    SceneObjectSubScreenShell("Stars", onBack) {
        SettingSwitchRow(
            title = "Show Stars", subtitle = "",
            checked = customization.stars.visible,
            onCheckedChange = { scope.launch { prefs.setStarsVisible(it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("# of Stars: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.stars.density,
            onCommit = { committed -> scope.launch { prefs.setStarsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun CloudsSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, liveWeatherEnabled: Boolean, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SceneObjectSubScreenShell("Clouds", onBack) {
        // Live Weather (Settings > Weather and sunrise/sunset) fully drives cloud density from
        // real conditions while it's on -- see PaperRenderer.drawClouds' own doc comment on
        // exactly how that override works. Visibility/density read-only here so a manual edit
        // can't silently do nothing (or worse, look like it worked and then get overwritten on
        // the next hourly fetch); colors stay editable since Live Weather never touches those.
        if (liveWeatherEnabled) {
            Text(
                "☔ Live Weather is on, so cloud density is driven by real conditions. Turn Live Weather off in Weather settings to set this manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingSwitchRow(
            title = "Show Clouds", subtitle = "",
            checked = customization.clouds.visible,
            enabled = !liveWeatherEnabled,
            onCheckedChange = { scope.launch { prefs.setCloudsVisible(it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("# of Clouds: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.clouds.density,
            onCommit = { committed -> scope.launch { prefs.setCloudsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = !liveWeatherEnabled,
        )
        ColorSwatchRow("Day Color", customization.clouds.colorDay) {
            editingTarget = ColorEditTarget("Clouds — Day Color", customization.clouds.colorDay) { c -> scope.launch { prefs.setCloudsColorDay(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color", customization.clouds.colorNight) {
            editingTarget = ColorEditTarget("Clouds — Night Color", customization.clouds.colorNight) { c -> scope.launch { prefs.setCloudsColorNight(c, forThemeId) } }
        }
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun PrecipitationSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, liveWeatherEnabled: Boolean, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    val precip = customization.precipitation
    SceneObjectSubScreenShell("Precipitation", onBack) {
        // See CloudsSubDialog's own comment on this same pattern -- Live Weather fully drives
        // visibility/type/intensity/thunderstorm here (PaperRenderer.drawPrecipitation's own doc
        // comment), so those controls are read-only while it's on. Colors stay editable.
        if (liveWeatherEnabled) {
            Text(
                "☔ Live Weather is on, so rain/snow/thunderstorm are driven by real conditions. Turn Live Weather off in Weather settings to set this manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SettingSwitchRow(
            title = "Show Rain/Snow", subtitle = "",
            checked = precip.visible,
            enabled = !liveWeatherEnabled,
            onCheckedChange = { scope.launch { prefs.setPrecipitationVisible(it, forThemeId) } },
        )
        Text("Type", style = MaterialTheme.typography.bodyMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (precip.type == PrecipitationType.RAIN) {
                Button(enabled = !liveWeatherEnabled, onClick = { scope.launch { prefs.setPrecipitationType(PrecipitationType.RAIN, forThemeId) } }) { Text("🌧️ Rain") }
            } else {
                OutlinedButton(enabled = !liveWeatherEnabled, onClick = { scope.launch { prefs.setPrecipitationType(PrecipitationType.RAIN, forThemeId) } }) { Text("🌧️ Rain") }
            }
            if (precip.type == PrecipitationType.SNOW) {
                Button(enabled = !liveWeatherEnabled, onClick = { scope.launch { prefs.setPrecipitationType(PrecipitationType.SNOW, forThemeId) } }) { Text("❄️ Snow") }
            } else {
                OutlinedButton(enabled = !liveWeatherEnabled, onClick = { scope.launch { prefs.setPrecipitationType(PrecipitationType.SNOW, forThemeId) } }) { Text("❄️ Snow") }
            }
        }
        PreferenceSlider(
            label = { shown -> Text("Intensity: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = precip.intensity,
            onCommit = { committed -> scope.launch { prefs.setPrecipitationIntensity(committed, forThemeId) } },
            valueRange = 0f..1f,
            enabled = !liveWeatherEnabled,
        )
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle("Rain Colors")
        ColorSwatchRow("Day Color", precip.rainColorDay) {
            editingTarget = ColorEditTarget("Rain — Day Color", precip.rainColorDay) { c -> scope.launch { prefs.setPrecipitationRainColorDay(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color", precip.rainColorNight) {
            editingTarget = ColorEditTarget("Rain — Night Color", precip.rainColorNight) { c -> scope.launch { prefs.setPrecipitationRainColorNight(c, forThemeId) } }
        }
        SectionTitle("Snow Colors")
        ColorSwatchRow("Day Color", precip.snowColorDay) {
            editingTarget = ColorEditTarget("Snow — Day Color", precip.snowColorDay) { c -> scope.launch { prefs.setPrecipitationSnowColorDay(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color", precip.snowColorNight) {
            editingTarget = ColorEditTarget("Snow — Night Color", precip.snowColorNight) { c -> scope.launch { prefs.setPrecipitationSnowColorNight(c, forThemeId) } }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        SectionTitle("Storms")
        SettingSwitchRow(
            title = "Thunderstorm", subtitle = "Occasional lightning flashes (only while Rain is selected)",
            checked = precip.thunderstorm,
            enabled = !liveWeatherEnabled,
            onCheckedChange = { scope.launch { prefs.setPrecipitationThunderstorm(it, forThemeId) } },
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun RainbowSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    val rainbow = customization.rainbow
    SceneObjectSubScreenShell("Rainbow", onBack) {
        SettingSwitchRow(
            title = "Show Rainbow", subtitle = "",
            checked = rainbow.visible,
            onCheckedChange = { scope.launch { prefs.setRainbowVisible(it, forThemeId) } },
        )
        Text("Opacity: ${(rainbow.opacity * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium)
        Text(
            "How vivid the rainbow is at full daylight — it fades out toward night.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreferenceSlider(
            value = rainbow.opacity,
            onCommit = { committed -> scope.launch { prefs.setRainbowOpacity(committed, forThemeId) } },
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun CitiesSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SceneObjectSubScreenShell("Cities", onBack) {
        ObjectCategorySection(
            title = "Houses", config = customization.houses, category = ObjectCategory.HOUSES,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
        ObjectCategorySection(
            title = "Buildings", config = customization.buildings, category = ObjectCategory.BUILDINGS,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun HillsSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SceneObjectSubScreenShell("Hills", onBack) {
        ColorSwatchRow("Day Color", customization.hillsColorDay) {
            editingTarget = ColorEditTarget("Hills — Day Color", customization.hillsColorDay) { c -> scope.launch { prefs.setHillsColorDay(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color", customization.hillsColorNight) {
            editingTarget = ColorEditTarget("Hills — Night Color", customization.hillsColorNight) { c -> scope.launch { prefs.setHillsColorNight(c, forThemeId) } }
        }
        Text(
            "How wavy the hill silhouette is. Lower it for flatter, calmer hills.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        PreferenceSlider(
            label = { shown -> Text("Variation: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.hillsVariation,
            onCommit = { committed -> scope.launch { prefs.setHillsVariation(committed, forThemeId) } },
            valueRange = 0f..1f,
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun MountainsSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SceneObjectSubScreenShell("Mountains", onBack) {
        Text(
            "Two independent background layers behind the hills.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        MountainLayerSection(
            title = "Front Mountains", config = customization.mountainsFront, front = true,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
        MountainLayerSection(
            title = "Back Mountains", config = customization.mountainsBack, front = false,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun TreesSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SceneObjectSubScreenShell("Trees", onBack) {
        ObjectCategorySection(
            title = "Trees", config = customization.trees, category = ObjectCategory.TREES,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

/**
 * Visibility and density for the pedestrians, and nothing else.
 *
 * Deliberately not an [ObjectCategorySection]: that lays out four colour swatches, and the walk
 * sprites are finished art in four kinds across two seasons with nothing for a colour to reach.
 * Offering swatches that did nothing would be worse than offering none.
 */
@Composable
private fun PeopleSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    val config = customization.people
    SceneObjectSubScreenShell("People", onBack) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            SectionTitle("People")
            SettingSwitchRow(
                title = "Show people",
                subtitle = "People walk along the ground between the buildings and the road, and dress for the season.",
                checked = config.visible,
                onCheckedChange = { scope.launch { prefs.setCategoryVisible(ObjectCategory.PEOPLE, it, forThemeId) } },
            )
            PreferenceSlider(
                label = { shown -> Text("Density: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
                value = config.density,
                onCommit = { committed -> scope.launch { prefs.setCategoryDensity(ObjectCategory.PEOPLE, committed, forThemeId) } },
                valueRange = 0f..1f,
            )
            Text(
                "Their clothing follows the Winter Colors decoration, like the trees do.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UmbrellasSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SceneObjectSubScreenShell("Umbrellas", onBack) {
        ObjectCategorySection(
            title = "Umbrellas", config = customization.parasols, category = ObjectCategory.PARASOLS,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun CarsSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SceneObjectSubScreenShell("Cars", onBack) {
        ObjectCategorySection(
            title = "Cars", config = customization.cars, category = ObjectCategory.CARS,
            forThemeId = forThemeId, prefs = prefs, scope = scope,
            onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun BirdsSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SceneObjectSubScreenShell("Birds", onBack) {
        SettingSwitchRow(
            title = "Show Birds", subtitle = "",
            checked = customization.birds.visible,
            onCheckedChange = { scope.launch { prefs.setBirdsVisible(it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("# of Birds: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.birds.density,
            onCommit = { committed -> scope.launch { prefs.setBirdsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
        )
        SettingSwitchRow(
            title = "Night Birds", subtitle = "Allow birds to fly at night",
            checked = customization.birds.nightBirds,
            onCheckedChange = { scope.launch { prefs.setBirdsNight(it, forThemeId) } },
        )
        Text("Bird Colors", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        customization.birds.colors.forEachIndexed { index, colorWeight ->
            ColorSwatchRow("Bird Color ${index + 1}", colorWeight.color) {
                editingTarget = ColorEditTarget("Bird Color ${index + 1}", colorWeight.color) { c -> scope.launch { prefs.setBirdColor(index, c, forThemeId) } }
            }
        }
        Text(
            "Bird Color Frequencies — change how often each color appears",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        customization.birds.colors.forEachIndexed { index, colorWeight ->
            Column {
                PreferenceSlider(
                    label = { shown -> Text("Color ${index + 1}: ${(shown * 100).toInt()}", style = MaterialTheme.typography.bodySmall) },
                    value = colorWeight.weight,
                    onCommit = { committed -> scope.launch { prefs.setBirdWeight(index, committed, forThemeId) } },
                    valueRange = 0f..1f,
                )
            }
        }
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}

@Composable
private fun LakeSubDialog(customization: SceneCustomization, forThemeId: String, prefs: WallpaperPrefs, scope: CoroutineScope, onBack: () -> Unit) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }
    SceneObjectSubScreenShell("Lakes, Boats and Dolphins", onBack) {
        SettingSwitchRow(
            title = "Show Lake", subtitle = "A body of water in the middle distance",
            checked = customization.lake.visible,
            onCheckedChange = { scope.launch { prefs.setLakeVisible(it, forThemeId) } },
        )
        ColorSwatchRow("Day Color", customization.lake.colorDay) {
            editingTarget = ColorEditTarget("Lake — Day Color", customization.lake.colorDay) { c -> scope.launch { prefs.setLakeColorDay(c, forThemeId) } }
        }
        ColorSwatchRow("Night Color", customization.lake.colorNight) {
            editingTarget = ColorEditTarget("Lake — Night Color", customization.lake.colorNight) { c -> scope.launch { prefs.setLakeColorNight(c, forThemeId) } }
        }
        PreferenceSlider(
            label = { shown -> Text("Lake Height: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.lake.height,
            onCommit = { committed -> scope.launch { prefs.setLakeHeight(committed, forThemeId) } },
            valueRange = 0f..1f,
        )
        SettingSwitchRow(
            title = "Show Sailboats", subtitle = "",
            checked = customization.lake.sailboatsVisible,
            onCheckedChange = { scope.launch { prefs.setLakeSailboatsVisible(it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("# of Sailboats: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.lake.sailboatsDensity,
            onCommit = { committed -> scope.launch { prefs.setLakeSailboatsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
        )
        SettingSwitchRow(
            title = "Show Dolphins", subtitle = "",
            checked = customization.lake.dolphinsVisible,
            onCheckedChange = { scope.launch { prefs.setLakeDolphinsVisible(it, forThemeId) } },
        )
        PreferenceSlider(
            label = { shown -> Text("# of Dolphins: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = customization.lake.dolphinsDensity,
            onCommit = { committed -> scope.launch { prefs.setLakeDolphinsDensity(committed, forThemeId) } },
            valueRange = 0f..1f,
        )
    }
    editingTarget?.let { target ->
        ColorPickerDialog(title = target.label, initialColor = target.color,
            onConfirm = { c -> target.onChange(c); editingTarget = null }, onDismiss = { editingTarget = null })
    }
}


/**
 * Mirrors "Scene Objects"'s [ObjectCategorySection] editing model but for seasonal decorations (snowmen, gifts, balloons, penguins,
 * bunnies, Easter eggs, pumpkins) -- same per-theme editing model, same [ObjectCategorySection]
 * UI, just a separate screen showing a different subset of categories (opt-in extras rather than
 * every theme's structural building blocks). Unlike the structural categories, each built-in
 * theme's *starting point* here differs -- see [defaultCustomizationFor] -- so e.g. Christmas
 * already has snowmen and gifts turned on by default, Easter already has bunnies and eggs, and so
 * on, while still being fully editable: change anything here and either overwrite the built-in
 * theme or save your own custom theme from "Manage Themes", exactly like Scene Objects.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SeasonalDecorationsDialog(
    customization: SceneCustomization,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onDismiss: () -> Unit,
) {
    var editingTarget by remember { mutableStateOf<ColorEditTarget?>(null) }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(modifier = Modifier.fillMaxSize()) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text("Seasonal Decorations") },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "Close")
                            }
                        },
                    )
                },
            ) { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .verticalScroll(rememberScrollState())
                        .navigationBarsPadding()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                ) {
                    Text(
                        "These apply live to your current theme only, same as Scene Objects — switch themes and each one keeps its own seasonal look. Want to keep an edit permanently? Save it from \"Manage Themes\".",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )

                    Column {
                        SettingSwitchRow(
                            title = "🍂 Fall Colors",
                            subtitle = "Trees turn autumn tones with leaves drifting down periodically",
                            checked = customization.fallColorsEnabled,
                            onCheckedChange = { scope.launch { prefs.setFallColorsEnabled(it, forThemeId) } },
                        )
                        SettingSwitchRow(
                            title = "❄️ Winter Colors",
                            subtitle = "Snow settles on trees and rooftops, and people dress for the cold",
                            checked = customization.winterColorsEnabled,
                            onCheckedChange = { scope.launch { prefs.setWinterColorsEnabled(it, forThemeId) } },
                        )
                        if (customization.fallColorsEnabled || customization.winterColorsEnabled) {
                            Text(
                                "Fall Colors and Winter Colors are mutually exclusive — turning one on turns the other off. Christmas Lights are separate and work with either.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    SectionTitle("Christmas")
                    SettingSwitchRow(
                        title = "🎄 Christmas Lights",
                        subtitle = "Blinking lights on the trees. Independent of Winter Colors — you can have one without the other",
                        checked = customization.christmasDecorationsEnabled,
                        onCheckedChange = { scope.launch { prefs.setChristmasDecorationsEnabled(it, forThemeId) } },
                    )
                    SettingSwitchRow(
                        title = "🎅 Show Santa",
                        subtitle = "Santa's sleigh occasionally flies across the sky dropping gifts. Previously tied to the Christmas theme with no way to turn it off — now a toggle like everything else here.",
                        checked = customization.santaEnabled,
                        onCheckedChange = { scope.launch { prefs.setSantaEnabled(it, forThemeId) } },
                    )

                    SectionTitle("Halloween")
                    SettingSwitchRow(
                        title = "\uD83D\uDC80 Halloween",
                        subtitle = "A carved jack-o'-lantern moon, and every tree stripped to bare branches. Independent of Winter Colors and Christmas Lights — turning it on changes neither.",
                        checked = customization.halloweenEnabled,
                        onCheckedChange = { scope.launch { prefs.setHalloweenEnabled(it, forThemeId) } },
                    )
                    SettingSwitchRow(
                        title = "\uD83C\uDF83 Horror Sky",
                        subtitle = "Near-black overhead with a hard orange horizon. A separate switch from Halloween, so you can have either on its own.",
                        checked = customization.horrorSkyEnabled,
                        onCheckedChange = { scope.launch { prefs.setHorrorSkyEnabled(it, forThemeId) } },
                    )

                    ObjectCategorySection(
                        title = "Snowmen",
                        config = customization.snowmen,
                        category = ObjectCategory.SNOWMEN,
                        forThemeId = forThemeId,
                        prefs = prefs,
                        scope = scope,
                        onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
                    )
                    ObjectCategorySection(
                        title = "Gifts",
                        config = customization.gifts,
                        category = ObjectCategory.GIFTS,
                        forThemeId = forThemeId,
                        prefs = prefs,
                        scope = scope,
                        onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
                    )
                    ObjectCategorySection(
                        title = "Balloons",
                        config = customization.balloons,
                        category = ObjectCategory.BALLOONS,
                        forThemeId = forThemeId,
                        prefs = prefs,
                        scope = scope,
                        onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
                    )
                    ObjectCategorySection(
                        title = "Penguins",
                        config = customization.penguins,
                        category = ObjectCategory.PENGUINS,
                        forThemeId = forThemeId,
                        prefs = prefs,
                        scope = scope,
                        onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
                    )
                    ObjectCategorySection(
                        title = "Easter Bunnies",
                        config = customization.bunnies,
                        category = ObjectCategory.BUNNIES,
                        forThemeId = forThemeId,
                        prefs = prefs,
                        scope = scope,
                        onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
                    )
                    ObjectCategorySection(
                        title = "Easter Eggs",
                        config = customization.easterEggs,
                        category = ObjectCategory.EASTER_EGGS,
                        forThemeId = forThemeId,
                        prefs = prefs,
                        scope = scope,
                        onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
                    )
                    ObjectCategorySection(
                        title = "Pumpkins",
                        config = customization.pumpkins,
                        category = ObjectCategory.PUMPKINS,
                        forThemeId = forThemeId,
                        prefs = prefs,
                        scope = scope,
                        onEditColor = { label, color, onChange -> editingTarget = ColorEditTarget(label, color, onChange) },
                    )

                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                // Only the 7 seasonal categories plus Fall/Winter Colors --
                                // resetAllCategories() would also wipe this theme's houses/
                                // trees/etc, which isn't what "reset everything" means on this
                                // specific screen.
                                for (category in listOf(
                                    ObjectCategory.SNOWMEN, ObjectCategory.GIFTS, ObjectCategory.BALLOONS,
                                    ObjectCategory.PENGUINS, ObjectCategory.BUNNIES, ObjectCategory.EASTER_EGGS,
                                    ObjectCategory.PUMPKINS,
                                )) {
                                    prefs.resetCategory(category)
                                }
                                prefs.resetSeasonalPalettes(forThemeId)
                                prefs.resetSanta(forThemeId)
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("↺ Reset everything to defaults")
                    }

                    Box(modifier = Modifier.height(56.dp))
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
private fun MountainLayerSection(
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
            onEditColor("$title — Day Color", config.colorDay) { c -> scope.launch { prefs.setMountainColorDay(front, c, forThemeId) } }
        }
        ColorSwatchRow("Night Color", config.colorNight) {
            onEditColor("$title — Night Color", config.colorNight) { c -> scope.launch { prefs.setMountainColorNight(front, c, forThemeId) } }
        }
        PreferenceSlider(
            label = { shown -> Text("Density: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
            value = config.density,
            onCommit = { committed -> scope.launch { prefs.setMountainDensity(front, committed, forThemeId) } },
            valueRange = 0f..1f,
        )
    }
}

@Composable
private fun ObjectCategorySection(
    title: String,
    config: ObjectVariantConfig,
    category: ObjectCategory,
    forThemeId: String,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onEditColor: (label: String, color: Int, onChange: (Int) -> Unit) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        SectionTitle(title)

        SettingSwitchRow(
            title = "Show $title",
            subtitle = "$title can appear in every theme",
            checked = config.visible,
            onCheckedChange = { scope.launch { prefs.setCategoryVisible(category, it, forThemeId) } },
        )

        Column {
            PreferenceSlider(
                label = { shown -> Text("Density: ${(shown * 100).toInt()}%", style = MaterialTheme.typography.bodyMedium) },
                value = config.density,
                onCommit = { committed -> scope.launch { prefs.setCategoryDensity(category, committed, forThemeId) } },
                valueRange = 0f..1f,
            )
        }

        Text(
            "Each one randomly uses Color 1 or Color 2, and blends into its night version as it gets dark.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ColorSwatchRow("Day Color 1", config.colorDay1) {
            onEditColor("$title — Day Color 1", config.colorDay1) { c -> scope.launch { prefs.setCategoryColorDay1(category, c, forThemeId) } }
        }
        ColorSwatchRow("Night Color 1", config.colorNight1) {
            onEditColor("$title — Night Color 1", config.colorNight1) { c -> scope.launch { prefs.setCategoryColorNight1(category, c, forThemeId) } }
        }
        ColorSwatchRow("Day Color 2", config.colorDay2) {
            onEditColor("$title — Day Color 2", config.colorDay2) { c -> scope.launch { prefs.setCategoryColorDay2(category, c, forThemeId) } }
        }
        ColorSwatchRow("Night Color 2", config.colorNight2) {
            onEditColor("$title — Night Color 2", config.colorNight2) { c -> scope.launch { prefs.setCategoryColorNight2(category, c, forThemeId) } }
        }

        OutlinedButton(
            onClick = { scope.launch { prefs.resetCategory(category) } },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("↺ Reset $title to default")
        }

        HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
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
 * Touch-and-drag HSV color editor: a saturation/brightness square you drag your finger across
 * (classic palette-picker UX), a hue strip below it, and an editable hex field kept in sync both
 * ways -- dragging updates the hex text, and typing a valid hex value updates the picker.
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
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(currentColor))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)),
                )
                SaturationBrightnessSquare(
                    hue = hue,
                    saturation = saturation,
                    brightness = brightness,
                    onChange = { s, v -> updateFromHsv(hue, s, v) },
                    modifier = Modifier.fillMaxWidth(),
                )
                HueStrip(
                    hue = hue,
                    onChange = { h -> updateFromHsv(h, saturation, brightness) },
                    modifier = Modifier.fillMaxWidth(),
                )
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

/**
 * The classic "drag your finger across the palette" square: horizontal axis is saturation
 * (white -> full hue color), vertical axis is brightness (bright at top, black at bottom).
 * Responds to both a direct tap (jump straight to that color) and dragging.
 */
@Composable
private fun SaturationBrightnessSquare(
    hue: Float,
    saturation: Float,
    brightness: Float,
    onChange: (saturation: Float, brightness: Float) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hueColor = Color(android.graphics.Color.HSVToColor(floatArrayOf(hue, 1f, 1f)))
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .aspectRatio(1.4f)
            .clip(RoundedCornerShape(12.dp)),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        fun reportFromOffset(offset: Offset) {
            val s = (offset.x / widthPx).coerceIn(0f, 1f)
            val v = 1f - (offset.y / heightPx).coerceIn(0f, 1f)
            onChange(s, v)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(hue) {
                    detectDragGestures(
                        onDragStart = { offset -> reportFromOffset(offset) },
                        onDrag = { change, _ -> change.consume(); reportFromOffset(change.position) },
                    )
                }
                .pointerInput(hue) {
                    detectTapGestures { offset -> reportFromOffset(offset) }
                },
        ) {
            drawRect(brush = Brush.horizontalGradient(listOf(Color.White, hueColor)))
            drawRect(brush = Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
        }

        val indicatorSize = 22.dp
        val indicatorX = with(density) { (saturation * widthPx).toDp() } - indicatorSize / 2
        val indicatorY = with(density) { ((1f - brightness) * heightPx).toDp() } - indicatorSize / 2
        Box(
            modifier = Modifier
                .offset(x = indicatorX, y = indicatorY)
                .size(indicatorSize)
                .clip(CircleShape)
                .border(3.dp, Color.White, CircleShape)
                .border(1.dp, Color.Black.copy(alpha = 0.25f), CircleShape),
        )
    }
}

/** A draggable rainbow strip for picking the hue (0-360°). */
@Composable
private fun HueStrip(hue: Float, onChange: (Float) -> Unit, modifier: Modifier = Modifier) {
    val hueColors = remember {
        (0..360 step 30).map { Color(android.graphics.Color.HSVToColor(floatArrayOf(it.toFloat(), 1f, 1f))) }
    }
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .height(28.dp)
            .clip(RoundedCornerShape(14.dp)),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }

        fun reportFromX(x: Float) {
            onChange((x / widthPx).coerceIn(0f, 1f) * 360f)
        }

        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset -> reportFromX(offset.x) },
                        onDrag = { change, _ -> change.consume(); reportFromX(change.position.x) },
                    )
                }
                .pointerInput(Unit) {
                    detectTapGestures { offset -> reportFromX(offset.x) }
                },
        ) {
            drawRect(brush = Brush.horizontalGradient(hueColors))
        }

        val thumbWidth = 4.dp
        val thumbX = with(density) { (hue / 360f * widthPx).toDp() } - thumbWidth / 2
        Box(
            modifier = Modifier
                .offset(x = thumbX)
                .width(thumbWidth)
                .fillMaxHeight()
                .background(Color.White)
                .border(1.dp, Color.Black.copy(alpha = 0.3f)),
        )
    }
}

/**
 * Renders a compact row of sample objects (house, tree, building) using the exact same
 * drawing code as the real wallpaper, so changes made in the Scene Objects screen are visible
 * immediately, right there, instead of only on the actual applied wallpaper. Includes a
 * day/night toggle since colors blend between the two.
 */
@Composable
private fun SceneObjectLivePreview(customization: SceneCustomization, modifier: Modifier = Modifier) {
    var previewIsDay by remember { mutableStateOf(true) }
    val previewContext = LocalContext.current
    // Keyed on the context, not on `customization`: the renderer now accepts a new configuration
    // in place, so there is no reason to build a new one (and a new set of Paint objects) every
    // time a colour changes. Its layout is empty, so no runtime list is ever rebuilt here.
    val previewRenderer = remember(previewContext) {
        SceneObjectRenderer(SceneObjectLayout(staticObjects = emptyList(), cars = emptyList()), customization, previewContext)
    }
    // The preview draws onto a Compose canvas, where there is no EGL surface and no GL context, so
    // it uses the Canvas backend of the same renderer the wallpaper uses. That is the reason the
    // Canvas backend is kept rather than deleted once the GPU renderer took over the wallpaper.
    val previewTarget = remember { CanvasSceneTarget() }
    SideEffect { previewRenderer.customization = customization }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(120.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(if (previewIsDay) Color(0xFFAEE0F2) else Color(0xFF14152B)),
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawIntoCanvas { canvas ->
                    previewTarget.bind(canvas.nativeCanvas)
                    previewRenderer.drawPreviewPair(
                        previewTarget,
                        size.width,
                        size.height,
                        dayBlend = if (previewIsDay) 1f else 0f,
                    )
                    previewTarget.unbind()
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { previewIsDay = true }) { Text(if (previewIsDay) "☀️ Day" else "Day") }
            OutlinedButton(onClick = { previewIsDay = false }) { Text(if (!previewIsDay) "🌙 Night" else "Night") }
        }
    }
}

/**
 * A [Slider] that persists its value **once, when the drag ends**, instead of on every
 * intermediate position.
 *
 * Every slider in this screen used to write straight to DataStore from `onValueChange`. One drag
 * therefore produced dozens of disk writes, dozens of preference-flow emissions, dozens of
 * recompositions, and -- because any configuration difference used to reconstruct the whole
 * scene renderer -- dozens of full scene rebuilds, which restarted every car from its start
 * delay. The thumb also had a disk round trip inside its own feedback loop, which is what made
 * the sliders feel like they stuck near the ends of the track rather than following the finger.
 *
 * The in-flight value lives in local state for the duration of the drag only, so there is no
 * lasting duplicate of the preference. [label] receives the value actually being displayed, so a
 * caption like "Density: 42%" keeps updating live while dragging even though nothing is written
 * until the finger lifts.
 *
 * See [SliderDragState] for the handover rules and why the local value is not dropped the instant
 * the drag ends.
 */
@Composable
private fun PreferenceSlider(
    value: Float,
    onCommit: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    enabled: Boolean = true,
    label: (@Composable (Float) -> Unit)? = null,
) {
    var inFlight by remember { mutableStateOf<Float?>(null) }
    var awaitingCommit by remember { mutableStateOf<Float?>(null) }

    LaunchedEffect(value, awaitingCommit) {
        if (SliderDragState.shouldReleaseLocalValue(value, awaitingCommit)) {
            awaitingCommit = null
        }
    }

    val displayed = SliderDragState.displayValue(value, inFlight, awaitingCommit)
    label?.invoke(displayed)
    Slider(
        value = displayed,
        onValueChange = { inFlight = it },
        onValueChangeFinished = {
            val settled = inFlight
            if (SliderDragState.shouldCommit(value, settled) && settled != null) {
                awaitingCommit = settled
                onCommit(settled)
            }
            inFlight = null
        },
        valueRange = valueRange,
        steps = steps,
        enabled = enabled,
        modifier = modifier,
    )
}
