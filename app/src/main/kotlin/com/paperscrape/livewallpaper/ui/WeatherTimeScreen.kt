package com.paperscrape.livewallpaper.ui

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.LocationOff
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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.runtime.collectAsState
import com.paperscrape.livewallpaper.engine.WallpaperEngineCensus
import com.paperscrape.livewallpaper.weather.WeatherRepository
import com.paperscrape.livewallpaper.location.CityGeocoder
import com.paperscrape.livewallpaper.location.Coordinates
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
import com.paperscrape.livewallpaper.location.LocalityLabelCache
import com.paperscrape.livewallpaper.location.LocationLabelResolver
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import com.paperscrape.livewallpaper.prefs.WallpaperPrefs
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import com.paperscrape.livewallpaper.weather.WeatherProviderId
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.update.UpdateNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Time of day, location and Live Weather -- the settings that are global rather than part of a
 * theme, which is why they are one destination of their own rather than a heading inside
 * "World & scene".
 *
 * None of this was a new preference in v2.9 (the weather provider, its two extra keys and the
 * GPS/Network split came later). What v2.9 changed is the shape of two of them:
 *
 * - The two mutually exclusive location switches ("phone location" / "custom location"), whose
 *   titles differed by three words and whose subtitles each had to explain the other, are one
 *   three-way choice then, four-way now (Off / GPS / Network / Custom).
 *   [SettingsUiModel.locationMode] reads the stored flags into it; the writes, made inline below,
 *   are `setUseLocation`, `setUseCustomLocation` and `setDeviceLocation`.
 * - Live Weather, the location choice and the API key used to be *inside* the "Follow real time"
 *   branch, so switching the clock to a fixed hour removed them from the screen with no
 *   explanation. They now stay put: the location choice goes disabled with the reason stated,
 *   and the API keys, under Advanced, stay editable. Live Weather went disabled too until v5.10C;
 *   since then it reads off while it cannot run, says why, and its tap goes to what is missing
 *   ([SettingsUiModel.liveWeather]).
 */
@Composable
internal fun WeatherTimeScreen(
    settings: WallpaperSettings,
    prefs: WallpaperPrefs,
    scope: CoroutineScope,
    /** The permission dialog for a GPS or Network choice (v5.10D: both location permissions for GPS). */
    onRequestLocationPermission: (kind: DeviceLocationKind, onResult: (LocationPermissionAnswer) -> Unit) -> Unit,
    onOpenWeatherEffects: () -> Unit,
    /** The system's preview, where PaperScrape is set as the wallpaper: Live Weather's tap when it is not (v5.10C). */
    onApplyWallpaper: () -> Unit,
    onBack: () -> Unit,
) {
    var showApiKey by remember { mutableStateOf(false) }
    var showWeatherApiComKey by remember { mutableStateOf(false) }
    var showOpenWeatherKey by remember { mutableStateOf(false) }
    val provider = settings.weatherProvider
    val locationMode = SettingsUiModel.locationMode(
        settings.useLocationForSunTimes,
        settings.useCustomLocation,
        settings.deviceLocationKind,
    )
    val locationEnabled = settings.syncWithRealTime
    // What the phone gives for GPS or Network, read from the phone each time this page comes back
    // (v5.10D, row 5): never the stored choice alone.
    val deviceAccess = rememberDeviceLocationAccess(settings)
    val deviceRow = deviceAccess?.let { SettingsUiModel.deviceLocationRow(locationMode, it) }
    val context = LocalContext.current
    // A refusal of the location dialog already seen on this screen: after it, a refusal the system
    // will not ask again sends the user to PaperScrape's page (`SettingsUiModel.afterLocationRequest`).
    var locationRefusalSeen by remember { mutableStateOf(false) }
    fun openPhonePage(intent: Intent) {
        runCatching { context.startActivity(intent) }
    }
    // Asks for exactly what [kind] needs and acts on the answer: the choice stored when granted, kept
    // when refused, and PaperScrape's page opened when the system will not ask again -- which until
    // v5.10D was a tap that did nothing at all.
    fun askLocation(kind: DeviceLocationKind, choiceAlreadyStored: Boolean) =
        onRequestLocationPermission(kind) { answer ->
            when (
                SettingsUiModel.afterLocationRequest(
                    kind = kind,
                    fineGranted = answer.fineGranted,
                    coarseGranted = answer.coarseGranted,
                    locationOn = answer.locationOn,
                    systemWouldAskAgain = answer.systemWouldAskAgain,
                    refusalSeenHere = locationRefusalSeen,
                    choiceAlreadyStored = choiceAlreadyStored,
                )
            ) {
                LocationRequestOutcome.STORE -> scope.launch { prefs.setDeviceLocation(kind) }
                LocationRequestOutcome.STORE_AND_OPEN_LOCATION_SETTINGS -> {
                    scope.launch { prefs.setDeviceLocation(kind) }
                    openPhonePage(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                LocationRequestOutcome.KEEP_PREVIOUS -> locationRefusalSeen = true
                LocationRequestOutcome.OPEN_APP_PAGE -> openPhonePage(UpdateNotifier.appDetailsIntent(context))
            }
        }
    val isTheWallpaper by WallpaperEngineCensus.isTheWallpaper.collectAsState()
    val liveWeather = SettingsUiModel.liveWeather(
        liveWeatherEnabled = settings.liveWeatherEnabled,
        followRealTime = settings.syncWithRealTime,
        locationMode = locationMode,
        devicePositionUsable = deviceAccess.positionUsable(),
        keyMissing = WeatherRepository.providerFor(provider).requiresApiKey && settings.apiKeyForWeatherProvider.isBlank(),
        isTheWallpaper = isTheWallpaper,
        status = settings.liveWeather,
    )
    // Where Live Weather's tap takes a user whose missing piece is the location: the choice above it.
    val locationChoice = remember { BringIntoViewRequester() }
    // ...and, whose missing piece is the key, the key of the provider chosen.
    fun openSelectedProviderKey() = when (provider) {
        WeatherProviderId.OPEN_METEO -> showApiKey = true
        WeatherProviderId.WEATHER_API_COM -> showWeatherApiComKey = true
        WeatherProviderId.OPEN_WEATHER -> showOpenWeatherKey = true
    }

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
        SettingsGroup(modifier = Modifier.bringIntoViewRequester(locationChoice)) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                // Four choices, because "Phone" was two different things wearing one label: the
                // provider it actually used depended on what happened to be enabled, so the cheap
                // option could quietly start the GNSS receiver. Naming them separately is the
                // point -- a user picking Network/Cell has said no to GPS, and the app now has to
                // honour that. Short labels because four segments have to fit a phone's width;
                // the caption underneath carries the meaning.
                SettingsSegmentedChoice(
                    options = listOf("Off", "GPS", "Network", "Custom"),
                    selectedIndex = locationMode.ordinal,
                    enabled = locationEnabled,
                    onSelect = { index ->
                        when (LocationMode.entries[index]) {
                            LocationMode.OFF -> scope.launch {
                                prefs.setUseLocation(false)
                                prefs.setUseCustomLocation(false)
                            }
                            // Each device mode asks for its own permission and is only written if
                            // that permission is actually granted, so a refused prompt leaves the
                            // previous choice in place rather than switching to a mode that
                            // cannot work; where the system will not ask again, PaperScrape's page opens,
                            // where it is given (v5.10D).
                            LocationMode.GPS -> askLocation(DeviceLocationKind.GPS, choiceAlreadyStored = locationMode == LocationMode.GPS)
                            LocationMode.NETWORK -> askLocation(DeviceLocationKind.NETWORK, choiceAlreadyStored = locationMode == LocationMode.NETWORK)
                            LocationMode.CUSTOM -> scope.launch { prefs.setUseCustomLocation(true) }
                        }
                    },
                )
                Text(
                    text = when {
                        // True since v5.10D (the maintainer's row 6): at a fixed hour no position
                        // counts, and the scene uses the default sunrise and sunset.
                        !locationEnabled ->
                            "Available while the scene follows real time. At a fixed hour, sunrise and sunset are " +
                                "6:00 and 20:00."
                        // What `LocationRequestThrottle` does, in its words (v5.10E): once an hour while
                        // the phone answers, and 5, 15 and 30 minutes after a search that finds nothing
                        // -- the maintainer's decision of 2026-10-04. Until v5.10E the two lines said
                        // "at most once an hour", true for v5.10D and not any more.
                        locationMode == LocationMode.GPS ->
                            "Uses the GPS receiver for a precise position, never continuously: once an hour " +
                                "at most. If a search finds nothing, it tries again after 5, 15 and 30 minutes."
                        locationMode == LocationMode.NETWORK ->
                            "Uses cell towers and Wi-Fi for an approximate position - enough to know your " +
                                "town, and the GPS receiver is never started. Once an hour at most; if nothing " +
                                "comes, it tries again after 5, 15 and 30 minutes."
                        locationMode == LocationMode.CUSTOM ->
                            "A place you pick yourself. Costs no battery and needs no location permission."
                        else -> "Used for precise sunrise and sunset times, and for Live Weather. One source at a time."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            if (locationEnabled && deviceRow != null) {
                // **What the phone gives, not what was chosen** (v5.10D, row 5). Not working: a row
                // that says why, and whose tap goes where it is put right -- the permission dialog
                // (PaperScrape's page once the system will not ask), or the phone's location page.
                val kind = settings.deviceLocationKind
                val name = if (kind == DeviceLocationKind.GPS) "GPS" else "Network"
                val hasSaved = settings.resolvedGpsLatitude != null && settings.resolvedGpsLongitude != null
                if (!deviceRow.working) {
                    SettingsNavigationRow(
                        title = when (deviceRow.line) {
                            DeviceLocationLine.APPROXIMATE_ONLY -> "GPS - tap to allow the precise location"
                            DeviceLocationLine.LOCATION_OFF -> "$name - tap to turn on location"
                            else -> "$name - tap to allow"
                        },
                        supporting = when (deviceRow.line) {
                            DeviceLocationLine.APPROXIMATE_ONLY ->
                                "Your phone allows PaperScrape only your approximate location, and the GPS " +
                                    "receiver needs the precise one, so the scene uses none: sunrise and sunset " +
                                    "are 6:00 and 20:00. Network uses the approximate one."
                            DeviceLocationLine.LOCATION_OFF -> if (hasSaved) {
                                "Your phone's location is off. Until it is on, the scene keeps the last position " +
                                    "your phone gave, below."
                            } else {
                                "Your phone's location is off, and it has not given PaperScrape a position yet: " +
                                    "sunrise and sunset are 6:00 and 20:00 until it does."
                            }
                            else ->
                                "PaperScrape is not allowed to use your location, so the scene uses none: " +
                                    "sunrise and sunset are 6:00 and 20:00, and Live Weather has no place to check."
                        },
                        icon = Icons.Filled.LocationOff,
                        onClick = {
                            when (deviceRow.tap) {
                                DeviceLocationTap.ASK_PERMISSION -> askLocation(kind, choiceAlreadyStored = true)
                                DeviceLocationTap.OPEN_LOCATION_SETTINGS ->
                                    openPhonePage(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                                DeviceLocationTap.NOTHING -> Unit
                            }
                        },
                    )
                }
                // The phone gives the position and none has arrived yet: say so, or the Location choice
                // shows nothing at all under the Live Weather line that sends the user here.
                if (deviceRow.working && !hasSaved) {
                    SettingsRow(
                        title = "No position from your phone yet",
                        // The tries of `LocationRequestThrottle` (v5.10E): 5, 15 and 30 minutes after a
                        // search that found nothing, then the hour, and round again until one arrives.
                        supporting = "PaperScrape asks your phone again after 5, 15 and 30 minutes, then after an " +
                            "hour, and the same again until a position arrives. Until then, sunrise and " +
                            "sunset are 6:00 and 20:00.",
                        icon = Icons.Filled.LocationOn,
                    )
                }
                // The position in use: shown while the phone gives it, or keeps the last one it gave.
                if (deviceRow.usesLastPosition) {
                    LocationRow(
                        latitude = settings.resolvedGpsLatitude,
                        longitude = settings.resolvedGpsLongitude,
                        supporting = when {
                            !deviceRow.working -> "Last position from your phone"
                            locationMode == LocationMode.GPS -> "Resolved from the GPS receiver"
                            else -> "Approximate, from cell towers and Wi-Fi"
                        },
                    )
                }
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
            // **On only while real weather can drive the scene** (v5.10C, the maintainer's decision
            // of 2026-09-30, inventory I-204). Until then it showed the stored flag: on over a fixed
            // hour, with no location, with no key, with another wallpaper -- while the scene ran on
            // the theme's own weather. Now it is off there, the line says what is missing, and the
            // tap goes to it: the real time back on, the Location choice above, the provider's key,
            // the system's wallpaper preview. The stored choice is written only by a tap that means
            // it -- turning it off when it shows on, or on when it shows off -- and comes back by
            // itself once nothing is missing. It always accepts a tap, so there is no state with no
            // way out (the dead end `LiveWeatherUiState` records). See [SettingsUiModel.liveWeather].
            SettingsSwitchRow(
                title = stringResource(R.string.live_weather_title),
                supporting = when (liveWeather.blocker) {
                    LiveWeatherBlocker.NONE -> stringResource(R.string.live_weather_desc)
                    LiveWeatherBlocker.FIXED_HOUR ->
                        "Needs the scene to follow real time - it is at a fixed hour. Tap to go back to real time" +
                            if (settings.liveWeatherEnabled) "." else " and turn it on."
                    LiveWeatherBlocker.NO_LOCATION -> stringResource(R.string.live_weather_needs_location)
                    LiveWeatherBlocker.MISSING_KEY ->
                        "${provider.displayName} needs an API key, and none is set. Tap to enter it."
                    LiveWeatherBlocker.NOT_THE_WALLPAPER ->
                        "Works only while PaperScrape is your wallpaper: the weather is checked inside it. " +
                            "Tap to set it as your wallpaper."
                    LiveWeatherBlocker.REJECTED_KEY ->
                        "${provider.displayName} has not accepted this API key. Tap to check it."
                    LiveWeatherBlocker.LOCATION_UNAVAILABLE ->
                        "Your phone has not given a location yet. Tap to check the Location choice above."
                    LiveWeatherBlocker.LOCATION_NOT_ALLOWED ->
                        "Your phone does not let PaperScrape use its location. Tap to see the Location choice above."
                },
                icon = Icons.Outlined.Cloud,
                checked = liveWeather.shownOn,
                onCheckedChange = {
                    when (SettingsUiModel.liveWeatherTap(liveWeather)) {
                        LiveWeatherTap.TURN_OFF -> scope.launch { prefs.setLiveWeatherEnabled(false) }
                        LiveWeatherTap.TURN_ON -> scope.launch { prefs.setLiveWeatherEnabled(true) }
                        LiveWeatherTap.FOLLOW_REAL_TIME -> scope.launch { prefs.setLiveWeatherOnFollowingRealTime() }
                        LiveWeatherTap.SHOW_LOCATION -> scope.launch {
                            prefs.setLiveWeatherEnabled(true)
                            locationChoice.bringIntoView()
                        }
                        LiveWeatherTap.OPEN_KEY -> {
                            scope.launch { prefs.setLiveWeatherEnabled(true) }
                            openSelectedProviderKey()
                        }
                        LiveWeatherTap.SET_AS_WALLPAPER -> {
                            scope.launch { prefs.setLiveWeatherEnabled(true) }
                            onApplyWallpaper()
                        }
                    }
                },
            )
            // **Where it goes, said** (v5.10E, inventory I-225): the row opens World & scene, where
            // Clouds, Rain and snow and Rainbow are three of the Sky rows at the top -- not a page of
            // its own, which "Clouds, rain and snow, rainbow" made it look like.
            SettingsNavigationRow(
                title = "Weather effects",
                supporting = "Opens World & scene: Clouds, Rain and snow and Rainbow are under Sky, at the top",
                icon = Icons.Outlined.WaterDrop,
                onClick = onOpenWeatherEffects,
            )
        }
        // **Shown only while what it reports is the reason, or the switch is on** (v5.10C). The
        // switch's own line names a missing hour, location, key or wallpaper, and the engine's last
        // report says nothing true about any of them: with another wallpaper no engine runs and the
        // report is the last one it made; a key typed in a moment ago has not been tried. What only
        // the engine can know -- a refused key, a phone that gives no position -- still comes here.
        val statusIsTheStory = when (liveWeather.blocker) {
            LiveWeatherBlocker.NONE, LiveWeatherBlocker.REJECTED_KEY, LiveWeatherBlocker.LOCATION_UNAVAILABLE -> true
            else -> false
        }
        if (settings.liveWeatherEnabled && statusIsTheStory) {
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
                // Deliberately not "could not be reached": the provider *was* reached and said
                // no. Saying otherwise sends the user to check their connection over a problem
                // that is entirely in the key -- and for OpenWeather the key is very often not
                // even wrong, just not yet active, which is the one thing this banner has to be
                // able to tell them.
                //
                // What happens next is what the loop does, not what it used to be described as
                // doing (v5.8): a rejection is not transient, so the loop keeps its normal hourly
                // interval and asks again -- which is exactly what lets a key that was merely not
                // yet active start working on its own -- and REJECTED_API_KEY does not drive the
                // scene, so the theme's own weather is what shows (LiveWeatherSchedule.decide).
                LiveWeatherStatus.REJECTED_API_KEY -> SettingsBanner(
                    text = "${provider.displayName} rejected this API key. Check that it is " +
                        "correct - and if you have only just created it, a new key can take a " +
                        "couple of hours to become active, which looks exactly like a wrong one. " +
                        "Until it is accepted the scene runs on this theme's own weather, and " +
                        "PaperScrape tries the key again about once an hour.",
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
                LiveWeatherStatus.OK -> SettingsBanner(
                    "Real conditions are driving this scene's clouds and precipitation, so their " +
                        "screens leave them to the forecast. Their colours stay editable.",
                )
                // OFF while the switch is on means no wallpaper engine has reported yet: the
                // preview never publishes, and the home-screen engine only does once it is
                // visible again -- or, since v5.10C, that the provider or a key has just changed and
                // the engine has forgotten an answer about the old ones. Until a report arrives the
                // theme's own weather is what you are looking at; this branch was once grouped with
                // OK and claimed the forecast was in charge. Its second message, "not running: it
                // needs real time and a location", is the switch's own line since v5.10C, and this
                // banner is not shown then (`statusIsTheStory`).
                LiveWeatherStatus.OFF -> SettingsBanner(
                    "Waiting for the first forecast. Until it arrives the scene is on this " +
                        "theme's own weather.",
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
                    options = WeatherProviderId.entries.map { it.shortName },
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
                // There is no built-in key: v5.3 stopped shipping one (OpenMeteoProvider.resolveApiKey
                // says why). A blank key is Open-Meteo's free, keyless service, which works.
                supporting = SettingsUiModel.apiKeyLine(WeatherProviderId.OPEN_METEO, provider, keySet = settings.liveWeatherApiKey.isNotBlank()),
                icon = Icons.Filled.Key,
                onClick = { showApiKey = true },
            )
            SettingsNavigationRow(
                title = "WeatherAPI.com API key",
                // "Required" only for the provider chosen (v5.10D, inventory I-221).
                supporting = SettingsUiModel.apiKeyLine(WeatherProviderId.WEATHER_API_COM, provider, keySet = settings.weatherApiComApiKey.isNotBlank()),
                icon = Icons.Filled.Key,
                onClick = { showWeatherApiComKey = true },
            )
            SettingsNavigationRow(
                title = "OpenWeather API key",
                supporting = SettingsUiModel.apiKeyLine(WeatherProviderId.OPEN_WEATHER, provider, keySet = settings.openWeatherApiKey.isNotBlank()),
                icon = Icons.Filled.Key,
                onClick = { showOpenWeatherKey = true },
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

    if (showWeatherApiComKey) {
        WeatherApiComApiKeyScreen(
            apiKey = settings.weatherApiComApiKey,
            onApply = { key -> scope.launch { prefs.setWeatherApiComApiKey(key) } },
            onBack = { showWeatherApiComKey = false },
        )
    }

    if (showOpenWeatherKey) {
        OpenWeatherApiKeyScreen(
            apiKey = settings.openWeatherApiKey,
            onApply = { key -> scope.launch { prefs.setOpenWeatherApiKey(key) } },
            onBack = { showOpenWeatherKey = false },
        )
    }
}

/**
 * OpenWeather's key. Required, like WeatherAPI.com's: there is no anonymous tier, so without one
 * the provider makes no request at all and the settings screen says so.
 *
 * Stored in this install's own DataStore, sent over the network only to OpenWeather, and copied
 * into an app backup only when the user exports one (the export row says so). Nothing about it is
 * compiled into the app, written to the build, or logged -- the field is masked here for the same
 * reason.
 */
@Composable
private fun OpenWeatherApiKeyScreen(apiKey: String, onApply: (String) -> Unit, onBack: () -> Unit) {
    var text by remember(apiKey) { mutableStateOf(apiKey) }
    SettingsFormSubScreen(title = "OpenWeather API key", onBack = onBack) {
        Text(
            "Required for the OpenWeather provider: it has no keyless tier. A free account needs " +
                "an email and no payment card, and gives 60 calls a minute on the Current Weather " +
                "API -- far more than one hourly refresh needs. Get one at openweathermap.org, " +
                "then paste it here. A new key can take a little while to become active.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("API key") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
        )
        Button(onClick = { onApply(text); onBack() }, modifier = Modifier.fillMaxWidth()) {
            Text("Save API key")
        }
        Text(
            "Stored on this device only and sent only to OpenWeather.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Where the device says it is: the **place name and the coordinates, together**.
 *
 * v4.0 changed what this row shows, not how it finds it. Before, the resolved name *replaced* the
 * coordinates -- the row's title was the city name when geocoding succeeded and the coordinates
 * when it failed, so the two facts were never on screen at once and a user who wanted to check the
 * numbers lost them the moment the name arrived. Now the name is the title and the coordinates sit
 * under it, which is the shape [SelectedCustomLocationRow] has always used for the manual case.
 *
 * **The coordinates are never lost, at any instant.** Before the name arrives, and if it never
 * does, the title *is* the coordinates -- so there is no state in which this row fails to answer
 * "where does the app think I am". A geocoder that is offline, absent or slow costs the name and
 * nothing else: it is not a location failure and is never reported as one.
 *
 * That is also why there is no longer a "Finding your location..." placeholder. It said the wrong
 * thing -- the location was already found, it was the *name* that was pending -- and the honest
 * alternative is to show the coordinates that were already known.
 *
 * Coordinates stay [Coordinates.formatCoarse], two decimals: the precision this row has always
 * used, and the precision a network fix actually has.
 *
 * Lookups go through [LocalityLabelCache], which is what keeps re-entering this screen or taking a
 * new fix a few metres away from being a new geocode. Re-resolves on the coordinates changing
 * (`latitude`/`longitude` as the `LaunchedEffect` key), never on recomposition.
 */
@Composable
private fun LocationRow(latitude: Float?, longitude: Float?, supporting: String) {
    val context = LocalContext.current
    // Seeded from the cache so that re-entering the screen with a known position paints the name
    // on the first frame instead of flashing the coordinates and replacing them a moment later.
    var label by remember(latitude, longitude) {
        mutableStateOf(
            if (latitude == null || longitude == null) {
                null
            } else {
                LocalityLabelCache.shared.cachedLabel(latitude.toDouble(), longitude.toDouble())
            },
        )
    }
    LaunchedEffect(latitude, longitude) {
        if (latitude == null || longitude == null) return@LaunchedEffect
        if (label != null) return@LaunchedEffect
        label = LocalityLabelCache.shared.labelFor(
            latitude = latitude.toDouble(),
            longitude = longitude.toDouble(),
            nowMillis = System.currentTimeMillis(),
        ) { lat, lon -> LocationLabelResolver.resolveCityLabel(context, lat, lon) }
    }
    if (latitude == null || longitude == null) return
    val coordinates = Coordinates.formatCoarse(latitude, longitude)
    val name = label
    SettingsRow(
        title = name ?: coordinates,
        // With a name, the coordinates move here and keep their place beside what produced them.
        // Without one, they are already the title and repeating them would say nothing.
        supporting = if (name != null) "$coordinates - $supporting" else supporting,
        icon = Icons.Filled.LocationOn,
    )
}

/**
 * The custom location currently in force: its name, with the coordinates kept available
 * underneath rather than made the headline. A user who searched for "Milano" should see Milano;
 * a user who typed coordinates should still be able to check them.
 */
@Composable
private fun SelectedCustomLocationRow(label: String, latitude: Float, longitude: Float) {
    val title = label.ifBlank { Coordinates.format(latitude, longitude) }
    SettingsRow(
        title = title,
        supporting = "Selected location - " + Coordinates.format(latitude, longitude),
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
                    onApply(parsedLat, parsedLon, labelText)
                    // aa reported that applying a manual location gave no confirmation it had
                    // actually taken effect. A Toast is the right fit here specifically because
                    // the row above is a *persistent* on-screen confirmation (the selected-location
                    // row, showing these same coordinates and the label typed with them) -- the
                    // Toast is the immediate "yes, that tap registered" feedback, the row is the
                    // lasting proof.
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
 * WeatherAPI.com's key, which unlike Open-Meteo's is **required**: there is no anonymous tier, so
 * without one the provider makes no request at all and the settings screen says so.
 *
 * The key is stored in this install's own DataStore, sent over the network only to WeatherAPI.com,
 * and copied into an app backup only when the user exports one (the export row says so). Nothing
 * about it is compiled into the app, written to the build, or logged -- the field is masked here
 * for the same reason.
 */
@Composable
private fun WeatherApiComApiKeyScreen(apiKey: String, onApply: (String) -> Unit, onBack: () -> Unit) {
    var text by remember(apiKey) { mutableStateOf(apiKey) }
    SettingsFormSubScreen(title = "WeatherAPI.com API key", onBack = onBack) {
        Text(
            "Required for the WeatherAPI.com provider: it has no keyless tier. A free account " +
                "needs an email and no payment card, and gives 100,000 calls a month -- far more " +
                "than one hourly refresh needs. Get one at weatherapi.com, then paste it here.",
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
            "Stored on this device only and sent only to WeatherAPI.com.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Optional user-entered Open-Meteo API key for Live Weather (see
 * [com.paperscrape.livewallpaper.weather.OpenMeteoProvider.resolveApiKey]). Blank is a perfectly
 * valid, fully-supported state: Open-Meteo's free tier needs no key at all, so this exists purely
 * as an upgrade path for a user who wants Open-Meteo's higher-limit customer endpoint under their
 * own account, not a requirement to make Live Weather work. That is why it is one level down,
 * under "Advanced", rather than in the main flow where v2.8 put it.
 *
 * It used to be described as taking "priority over the app's own baked-in key". v5.3 removed that
 * baked-in key -- it shipped readable in the dex -- so this is now the only key there is, and since
 * v5.9G the line under the field says what it is used instead of: the free service.
 */
@Composable
private fun LiveWeatherApiKeyScreen(apiKey: String, onApply: (String) -> Unit, onBack: () -> Unit) {
    var text by remember(apiKey) { mutableStateOf(apiKey) }
    SettingsFormSubScreen(title = "Open-Meteo API key", onBack = onBack) {
        Text(
            "Optional: your own Open-Meteo API key, for Open-Meteo's higher-limit service. Leave blank " +
                "to use the free service, which needs no key. A key you enter here is used instead of the free service.",
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
