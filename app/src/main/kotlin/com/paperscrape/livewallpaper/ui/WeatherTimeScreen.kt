package com.paperscrape.livewallpaper.ui

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.layout.size
import com.paperscrape.livewallpaper.location.CityGeocoder
import com.paperscrape.livewallpaper.location.CitySearchResult
import com.paperscrape.livewallpaper.location.GeocodedCity
import kotlinx.coroutines.delay
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.paperscrape.livewallpaper.R
import com.paperscrape.livewallpaper.location.LocationLabelResolver
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import com.paperscrape.livewallpaper.weather.WeatherProviderId
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Time of day, location and Live Weather -- the settings that are global rather than part of a
 * theme, which is why they are one destination of their own rather than a heading inside
 * "World & scene".
 *
 * Nothing here is a new preference. What changed in v2.9 is the shape of two of them:
 *
 * - The two mutually exclusive location switches ("phone location" / "custom location"), whose
 *   titles differed by three words and whose subtitles each had to explain the other, are one
 *   three-way choice. See [SettingsUiModel] for the mapping; the writes are the same two setters.
 * - Live Weather, the location choice and the API key used to be *inside* the "Follow real time"
 *   branch, so switching the clock to a fixed hour removed them from the screen with no
 *   explanation. They now stay put and go disabled, with the reason stated. Disabled is exactly
 *   what v2.8 already did to Live Weather when no location was set, so no state that was
 *   previously unreachable becomes reachable.
 */
@Composable
internal fun WeatherTimeScreen(
    settings: WallpaperSettings,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    onRequestLocationPermission: (onResult: (Boolean) -> Unit) -> Unit,
    onOpenWeatherEffects: () -> Unit,
    onBack: () -> Unit,
) {
    var showApiKey by remember { mutableStateOf(false) }
    var showVisualCrossingKey by remember { mutableStateOf(false) }
    val provider = settings.weatherProvider
    val locationMode = SettingsUiModel.locationMode(settings.useLocationForSunTimes, settings.useCustomLocation)
    val locationEnabled = settings.syncWithRealTime
    val liveWeatherAvailable = locationEnabled && locationMode != LocationMode.OFF

    SettingsSubScreen(title = "Weather & time", onBack = onBack) {
        SettingsSectionHeader("Time of day")
        SettingsGroup {
            SettingsSwitchRow(
                title = "Follow real time",
                supporting = "The sun and moon move according to your device's clock",
                icon = Icons.Outlined.Schedule,
                checked = settings.syncWithRealTime,
                onCheckedChange = { scope.launch { prefs.setSyncWithRealTime(it) } },
            )
            if (!settings.syncWithRealTime) {
                SettingsSliderRow(
                    title = "Fixed time",
                    valueLabel = { shown -> "${shown.toInt()}:00" },
                    value = settings.fixedHour,
                    onCommit = { committed -> scope.launch { prefs.setFixedHour(committed) } },
                    valueRange = 0f..23f,
                    steps = 22,
                )
            }
        }
        if (settings.syncWithRealTime) {
            SettingsCaption("Turn this off to freeze the scene at a fixed hour instead.")
        }

        SettingsSectionHeader("Location")
        SettingsGroup {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                SettingsSegmentedChoice(
                    options = listOf("Off", "Phone", "Custom"),
                    selectedIndex = locationMode.ordinal,
                    enabled = locationEnabled,
                    onSelect = { index ->
                        when (LocationMode.entries[index]) {
                            LocationMode.OFF -> scope.launch {
                                prefs.setUseLocation(false)
                                prefs.setUseCustomLocation(false)
                            }
                            // Same call, same permission prompt, same "only if granted" write the
                            // phone-location switch has always performed.
                            LocationMode.PHONE -> onRequestLocationPermission { granted ->
                                scope.launch { prefs.setUseLocation(granted) }
                            }
                            LocationMode.CUSTOM -> scope.launch { prefs.setUseCustomLocation(true) }
                        }
                    },
                )
                Text(
                    text = if (locationEnabled) {
                        "Used for precise sunrise and sunset times, and for Live Weather. One source at a time."
                    } else {
                        "Available while the scene follows real time."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (locationEnabled && locationMode == LocationMode.PHONE) {
                LocationRow(
                    latitude = settings.resolvedGpsLatitude,
                    longitude = settings.resolvedGpsLongitude,
                    loadingText = "Finding your location...",
                    supporting = "Resolved from your device's position",
                )
            }
            if (locationEnabled && locationMode == LocationMode.CUSTOM) {
                // The place in use, above the ways of changing it: what is set now is the first
                // question, and it stays answered while a search is in progress.
                SelectedCustomLocationRow(
                    label = settings.customLocationLabel,
                    latitude = settings.customLocationLatitude,
                    longitude = settings.customLocationLongitude,
                )
                CustomLocationFields(
                    latitude = settings.customLocationLatitude,
                    longitude = settings.customLocationLongitude,
                    label = settings.customLocationLabel,
                    onApply = { lat, lon, label -> scope.launch { prefs.setCustomLocation(lat, lon, label) } },
                )
            }
        }

        SettingsSectionHeader("Live weather")
        SettingsGroup {
            SettingsSwitchRow(
                title = stringResource(R.string.live_weather_title),
                supporting = when {
                    !settings.syncWithRealTime ->
                        "Needs the scene to follow real time, and a location to check the weather for."
                    locationMode == LocationMode.OFF -> stringResource(R.string.live_weather_needs_location)
                    else -> stringResource(R.string.live_weather_desc)
                },
                icon = Icons.Outlined.Cloud,
                checked = settings.liveWeatherEnabled,
                enabled = liveWeatherAvailable,
                onCheckedChange = { scope.launch { prefs.setLiveWeatherEnabled(it) } },
            )
            SettingsNavigationRow(
                title = "Weather effects",
                supporting = "Clouds, rain and snow, rainbow",
                icon = Icons.Outlined.WaterDrop,
                onClick = onOpenWeatherEffects,
            )
        }
        if (settings.liveWeatherEnabled) {
            // Published by the wallpaper service through the same settings flow this screen
            // already collects, so it appears and clears as the service changes it -- no polling,
            // no restart. Shown only while Live Weather is on: with it off there is no state to
            // report.
            val status = settings.liveWeather
            when (status) {
                LiveWeatherStatus.MISSING_API_KEY -> SettingsBanner(
                    text = "${provider.displayName} needs an API key. No requests are being made " +
                        "until one is entered; the scene is running on this theme's own weather. " +
                        "Enter a key below, or switch back to Open-Meteo, which needs none.",
                    isError = true,
                )
                LiveWeatherStatus.NO_LOCATION -> SettingsBanner(
                    text = stringResource(R.string.live_weather_fallback_notice),
                    isError = true,
                )
                LiveWeatherStatus.FAILED -> SettingsBanner(
                    text = "${provider.displayName} could not be reached, and there are no earlier " +
                        "conditions to fall back on, so the scene is running on this theme's own " +
                        "weather. It will try again on the next refresh.",
                    isError = true,
                )
                LiveWeatherStatus.STALE -> SettingsBanner(
                    text = "${provider.displayName} could not be reached. The scene is still showing " +
                        "the last conditions it fetched.",
                    isError = true,
                )
                LiveWeatherStatus.OK, LiveWeatherStatus.OFF -> SettingsBanner(
                    "While Live Weather is on, cloud and precipitation amounts come from the forecast and " +
                        "their screens are read-only. Their colours stay editable.",
                )
            }
        }

        SettingsSectionHeader("Weather provider")
        SettingsGroup {
            SettingsRow(
                title = "Source",
                supporting = "Where current conditions are fetched from. Changing it keeps your " +
                    "location and every other weather setting.",
                icon = Icons.Outlined.Cloud,
            )
            Column(modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
                SettingsSegmentedChoice(
                    options = WeatherProviderId.entries.map { it.displayName },
                    selectedIndex = WeatherProviderId.entries.indexOf(provider),
                    onSelect = { index ->
                        scope.launch { prefs.setWeatherProvider(WeatherProviderId.entries[index]) }
                    },
                )
            }
        }

        SettingsSectionHeader("Advanced")
        SettingsGroup {
            SettingsNavigationRow(
                title = "Open-Meteo API key",
                supporting = if (settings.liveWeatherApiKey.isBlank()) {
                    "Optional - using the app's built-in key"
                } else {
                    "Using your own key"
                },
                icon = Icons.Filled.Key,
                onClick = { showApiKey = true },
            )
            SettingsNavigationRow(
                title = "Visual Crossing API key",
                supporting = if (settings.visualCrossingApiKey.isBlank()) {
                    "Required - not set"
                } else {
                    "Set"
                },
                icon = Icons.Filled.Key,
                onClick = { showVisualCrossingKey = true },
            )
        }
    }

    if (showApiKey) {
        LiveWeatherApiKeyScreen(
            apiKey = settings.liveWeatherApiKey,
            onApply = { key -> scope.launch { prefs.setLiveWeatherApiKey(key) } },
            onBack = { showApiKey = false },
        )
    }

    if (showVisualCrossingKey) {
        VisualCrossingApiKeyScreen(
            apiKey = settings.visualCrossingApiKey,
            onApply = { key -> scope.launch { prefs.setVisualCrossingApiKey(key) } },
            onBack = { showVisualCrossingKey = false },
        )
    }
}

/**
 * Reverse-geocodes a lat/long into a short city label ("Florence, Italy") and shows it as a
 * standing confirmation under the location choice -- aa's own explicit ask was that the location
 * settings visibly report *which place* they resolved to, not just that some coordinates are set.
 * Re-resolves whenever the coordinates actually change (`latitude`/`longitude` as the
 * LaunchedEffect key), not on every recomposition.
 */
@Composable
private fun LocationRow(latitude: Float?, longitude: Float?, loadingText: String, supporting: String) {
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
        label != null -> label
        // geocoding failed -- raw coordinates as a fallback, never a blank row
        else -> "%.2f, %.2f".format(latitude, longitude)
    }
    if (text != null) {
        SettingsRow(title = text, supporting = supporting, icon = Icons.Filled.LocationOn)
    }
}

/**
 * The custom location currently in force: its name, with the coordinates kept available
 * underneath rather than made the headline. A user who searched for "Milano" should see Milano;
 * a user who typed coordinates should still be able to check them.
 */
@Composable
private fun SelectedCustomLocationRow(label: String, latitude: Float, longitude: Float) {
    val title = label.ifBlank { "%.3f, %.3f".format(latitude, longitude) }
    SettingsRow(
        title = title,
        supporting = "Selected location - %.3f, %.3f".format(latitude, longitude),
        icon = Icons.Filled.LocationOn,
    )
}

/**
 * Setting a custom location: search for a city by name, or type coordinates.
 *
 * The search is a *convenience for filling the same fields*, not a second location system. A
 * selected result writes latitude, longitude and label through `prefs.setCustomLocation` -- the
 * one call the manual Apply button has always made -- so Live Weather, the sunrise/sunset
 * calculation, the cache and the fallback cannot tell the two apart, and there is nothing new for
 * them to handle.
 *
 * Nothing is written until a result is tapped. A failed search, an empty one, or a cancelled one
 * leaves the current custom location exactly as it was.
 */
@Composable
private fun CustomLocationFields(
    latitude: Float,
    longitude: Float,
    label: String,
    onApply: (Float, Float, String) -> Unit,
) {
    var query by remember { mutableStateOf("") }
    var searchState by remember { mutableStateOf<CitySearchUiState>(CitySearchUiState.Idle) }
    var lastSearched by remember { mutableStateOf("") }
    val keyboard = LocalSoftwareKeyboardController.current

    suspend fun runSearch(text: String) {
        val trimmed = text.trim()
        if (!CityGeocoder.isQuerySearchable(trimmed)) return
        lastSearched = trimmed
        searchState = CitySearchUiState.Searching
        searchState = when (val result = CityGeocoder.search(trimmed)) {
            is CitySearchResult.Found -> CitySearchUiState.Results(result.cities)
            CitySearchResult.NoMatches -> CitySearchUiState.NoMatches
            CitySearchResult.Failed -> CitySearchUiState.Failed
        }
    }

    // Typing settles before anything is asked. 500 ms is long enough that a whole city name is one
    // request rather than one per letter, and the search action on the keyboard is there for
    // anyone who does not want to wait for it.
    LaunchedEffect(query) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            searchState = CitySearchUiState.Idle
            return@LaunchedEffect
        }
        if (!CityGeocoder.isQuerySearchable(trimmed) || trimmed == lastSearched) return@LaunchedEffect
        delay(SEARCH_DEBOUNCE_MS)
        runSearch(trimmed)
    }
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            label = { Text("Search for a city") },
            placeholder = { Text("Milano") },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = ""; searchState = CitySearchUiState.Idle }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear search")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = {
                keyboard?.hide()
                scope.launch { runSearch(query) }
            }),
            modifier = Modifier.fillMaxWidth(),
        )

        when (val state = searchState) {
            CitySearchUiState.Idle -> Unit
            CitySearchUiState.Searching -> Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(vertical = 8.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                Text("Searching...", style = MaterialTheme.typography.bodyMedium)
            }

            CitySearchUiState.NoMatches -> Text(
                "No place found for \"$lastSearched\". Check the spelling, or enter coordinates below.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            CitySearchUiState.Failed -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Couldn't reach the city search - check your connection and try again. Your " +
                        "current location is unchanged.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                OutlinedButton(onClick = { scope.launch { runSearch(lastSearched) } }) { Text("Try again") }
            }

            is CitySearchUiState.Results -> Column {
                // Never picked automatically, even when there is only one match: three Springfields
                // differ by region and country alone, and choosing for the user is how the wrong
                // continent's weather ends up on the wallpaper.
                Text(
                    if (state.cities.size == 1) "1 result" else "${state.cities.size} results - pick one",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column {
                        state.cities.forEach { city ->
                            CityResultRow(city) {
                                keyboard?.hide()
                                onApply(city.latitude.toFloat(), city.longitude.toFloat(), city.label)
                                query = ""
                                lastSearched = ""
                                searchState = CitySearchUiState.Idle
                            }
                        }
                    }
                }
            }
        }

        Text(
            "Or enter coordinates directly",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
        ManualCoordinateFields(latitude, longitude, label, onApply)
    }
}

/** One search result: the name, then everything that tells it from a place with the same name. */
@Composable
private fun CityResultRow(city: GeocodedCity, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            Icons.Filled.LocationOn,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(city.name, style = MaterialTheme.typography.bodyLarge)
            if (city.disambiguation.isNotBlank()) {
                Text(
                    city.disambiguation,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                city.coordinatesText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
    }
}

/**
 * Latitude/longitude/label entry, unchanged.
 *
 * Local text state (not committed to prefs on every keystroke, unlike this screen's usual pattern
 * of firing a prefs write per Slider/Switch change) because a lat/long is only valid once fully
 * typed -- writing "4" then "45" then "45." as separate coordinate values as the user types would
 * spam invalid, incomplete fixes through to the sunrise/sunset and Live Weather calculation on
 * every keystroke. Committed via the explicit "Apply" button instead, the same reasoning as why a
 * hex colour field in [ColorPickerDialog] commits on "Apply" rather than per-keystroke.
 */
@Composable
private fun ManualCoordinateFields(
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
                    // the row above is a *persistent* on-screen confirmation (reverse-geocoding
                    // these same coordinates) -- the Toast is the immediate "yes, that tap
                    // registered" feedback, the row is the lasting proof once resolved.
                    Toast.makeText(context, "Location applied", Toast.LENGTH_SHORT).show()
                }
            },
            enabled = isValid,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Apply coordinates")
        }
    }
}

/** What the search area is showing. Failure and emptiness are separate states, deliberately. */
private sealed interface CitySearchUiState {
    data object Idle : CitySearchUiState
    data object Searching : CitySearchUiState
    data class Results(val cities: List<GeocodedCity>) : CitySearchUiState
    data object NoMatches : CitySearchUiState
    data object Failed : CitySearchUiState
}

private const val SEARCH_DEBOUNCE_MS = 500L

/**
 * Optional user-entered Open-Meteo API key for Live Weather -- always takes priority over the
 * app's own baked-in key when set (see WeatherRepository.resolveApiKey). Blank is a perfectly
 * valid, fully-supported state: Open-Meteo's free tier needs no key at all, so this exists purely
 * as an upgrade path for a user who wants Open-Meteo's higher-limit customer endpoint under their
 * own account, not a requirement to make Live Weather work. That is why it is one level down,
 * under "Advanced", rather than in the main flow where v2.8 put it.
 */
/**
 * Visual Crossing's key, which unlike Open-Meteo's is **required**: there is no anonymous tier, so
 * without one the provider makes no request at all and the settings screen says so.
 *
 * The key is stored in this install's own DataStore and sent only to Visual Crossing. Nothing
 * about it is compiled into the app, written to the build, or logged -- the field is masked here
 * for the same reason.
 */
@Composable
private fun VisualCrossingApiKeyScreen(apiKey: String, onApply: (String) -> Unit, onBack: () -> Unit) {
    var text by remember(apiKey) { mutableStateOf(apiKey) }
    SettingsFormSubScreen(title = "Visual Crossing API key", onBack = onBack) {
        Text(
            "Required for the Visual Crossing provider: it has no keyless tier. A free account " +
                "gives 1,000 weather records a day, which is far more than one hourly refresh " +
                "needs. Get one at visualcrossing.com, then paste it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("API key") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
        )
        Button(onClick = { onApply(text); onBack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Save API key")
        }
        Text(
            "Stored on this device only and sent only to Visual Crossing.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun LiveWeatherApiKeyScreen(apiKey: String, onApply: (String) -> Unit, onBack: () -> Unit) {
    var text by remember(apiKey) { mutableStateOf(apiKey) }
    SettingsFormSubScreen(title = "Open-Meteo API key", onBack = onBack) {
        Text(
            "Optional: your own Open-Meteo API key. Leave blank to use the app's built-in key (or " +
                "Open-Meteo's free tier if none is built in) -- entering your own always takes priority.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("API key (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )
        Button(onClick = { onApply(text); onBack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Save API key")
        }
    }
}
