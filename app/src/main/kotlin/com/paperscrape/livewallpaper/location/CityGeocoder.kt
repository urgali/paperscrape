package com.paperscrape.livewallpaper.location

import com.paperscrape.livewallpaper.MAX_HTTP_BODY_CHARS
import com.paperscrape.livewallpaper.readAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * One place a city search matched, in this app's own vocabulary rather than the provider's
 * response shape -- the same separation [DeviceLocationFix] keeps between "where is the device"
 * and what that is used for.
 *
 * [admin1] is the first-level division (region, state, province) and [admin2] the second where the
 * provider has one; both are frequently absent, which is why they are nullable and why nothing
 * here assumes a fixed "City, Region, Country" shape.
 */
data class GeocodedCity(
    val name: String,
    val admin1: String?,
    val admin2: String?,
    val country: String?,
    val countryCode: String?,
    val latitude: Double,
    val longitude: Double,
) {
    /**
     * What gets stored as the custom location's label, and shown as the selected city.
     *
     * Kept to the two parts that identify a place to its own resident -- the city and the country
     * -- because this string has to fit in a settings row next to an icon.
     */
    val label: String
        get() = if (country.isNullOrBlank()) name else "$name, $country"

    /**
     * The line under the name in a results list: everything that distinguishes two places sharing
     * a name. Three Springfields differ by region and country, never by their own name, so this is
     * the line the user actually chooses on.
     */
    val disambiguation: String
        get() = listOfNotNull(
            admin1?.takeIf { it.isNotBlank() && it != name },
            admin2?.takeIf { it.isNotBlank() && it != name && it != admin1 },
            country?.takeIf { it.isNotBlank() },
        ).joinToString(", ")

    /** The coordinates, for the user to verify -- shown small, never as the primary label. */
    val coordinatesText: String
        get() = Coordinates.format(latitude, longitude)
}

/** What a search produced. Failure and emptiness are different answers and are kept apart. */
sealed interface CitySearchResult {
    data class Found(val cities: List<GeocodedCity>) : CitySearchResult

    /** The provider answered, and knows no place by that name. */
    data object NoMatches : CitySearchResult

    /** Nothing was learned: offline, timeout, an error response, an unreadable body. */
    data object Failed : CitySearchResult
}

/**
 * Reads Open-Meteo's geocoding response.
 *
 * Separated from the network call and free of Android and of `HttpURLConnection` so that the part
 * that can silently go wrong -- a field renamed, a result missing its coordinates, a body that is
 * not the expected shape at all -- is unit-testable against real response text.
 */
object CityGeocodingParser {

    /**
     * Returns the places in the body, skipping any entry without usable coordinates rather than
     * failing the whole search for one bad row. An unparseable body yields an empty list, which
     * the caller reports as [CitySearchResult.Failed] rather than as "no such city": those two
     * must not look alike to the user.
     */
    fun parse(body: String): List<GeocodedCity> {
        val results = try {
            JSONObject(body).optJSONArray("results")
        } catch (_: Exception) {
            null
        } ?: return emptyList()

        val cities = mutableListOf<GeocodedCity>()
        for (index in 0 until results.length()) {
            val entry = results.optJSONObject(index) ?: continue
            val name = entry.optString("name").takeIf { it.isNotBlank() } ?: continue
            // Open-Meteo always sends both, but a result that cannot be turned into a position is
            // useless here and must not become a custom location of (0, 0) off West Africa.
            if (!entry.has("latitude") || !entry.has("longitude")) continue
            val latitude = entry.optDouble("latitude", Double.NaN)
            val longitude = entry.optDouble("longitude", Double.NaN)
            if (latitude.isNaN() || longitude.isNaN()) continue
            if (latitude !in -90.0..90.0 || longitude !in -180.0..180.0) continue

            cities += GeocodedCity(
                name = name,
                admin1 = entry.optString("admin1").takeIf { it.isNotBlank() },
                admin2 = entry.optString("admin2").takeIf { it.isNotBlank() },
                country = entry.optString("country").takeIf { it.isNotBlank() },
                countryCode = entry.optString("country_code").takeIf { it.isNotBlank() },
                latitude = latitude,
                longitude = longitude,
            )
        }
        return cities
    }
}

/**
 * A bounded most-recent-first cache of searches already answered.
 *
 * Deliberately tiny and in-memory only: it exists so that backspacing a character and typing it
 * again does not re-ask the provider, not to build a local gazetteer. Nothing here is persisted,
 * so a search is at most one process old.
 */
class CitySearchCache(private val maxEntries: Int = 8) {

    private val entries = LinkedHashMap<String, List<GeocodedCity>>()

    fun get(query: String): List<GeocodedCity>? = entries[normalise(query)]

    fun put(query: String, cities: List<GeocodedCity>) {
        val key = normalise(query)
        entries.remove(key)
        entries[key] = cities
        while (entries.size > maxEntries) {
            val oldest = entries.keys.firstOrNull() ?: break
            entries.remove(oldest)
        }
    }

    fun size(): Int = entries.size

    private fun normalise(query: String) = query.trim().lowercase()
}

/**
 * Finds a city by name so a user can set a custom location without knowing its coordinates.
 *
 * **Open-Meteo's own geocoding API**, which is the same provider Live Weather already uses. That
 * choice is the point: it needs no API key (like the weather endpoint, and unlike Google's
 * Geocoding API or Mapbox), it adds no library, and it reuses
 * [com.paperscrape.livewallpaper.weather.WeatherRepository]'s exact networking style --
 * `HttpURLConnection`, fixed timeouts, every failure becoming a value rather than an exception.
 * The app still has no HTTP client dependency and still ships no secret.
 *
 * The device's own [android.location.Geocoder] was the other candidate and was rejected for
 * *forward* search: `getFromLocationName` is optional on Android, absent on devices without Google
 * Play services, and returns results whose region fields are inconsistently populated -- which is
 * exactly the information a user needs to tell three Springfields apart.
 *
 * Reverse geocoding stays on the platform [LocationLabelResolver]: it runs offline where a device
 * supports it and needs no network at all, so there is no reason to move it.
 */
object CityGeocoder {

    private const val CONNECT_TIMEOUT_MS = 8000
    private const val READ_TIMEOUT_MS = 8000

    /** Enough to show the several places that share a name, few enough to fit a list. */
    const val RESULT_LIMIT = 8

    /** Below this, a search matches most of the world and is not worth asking about. */
    const val MINIMUM_QUERY_LENGTH = 2

    private val cache = CitySearchCache()

    fun isQuerySearchable(query: String): Boolean = query.trim().length >= MINIMUM_QUERY_LENGTH

    /**
     * Returns what was found, what was not found, or that nothing was learned -- never throws, and
     * never returns a partial answer the caller might store.
     */
    suspend fun search(query: String): CitySearchResult = withContext(Dispatchers.IO) {
        val trimmed = query.trim()
        if (!isQuerySearchable(trimmed)) return@withContext CitySearchResult.NoMatches

        cache.get(trimmed)?.let { cached ->
            return@withContext if (cached.isEmpty()) CitySearchResult.NoMatches else CitySearchResult.Found(cached)
        }

        val encoded = URLEncoder.encode(trimmed, "UTF-8")
        val url = "https://geocoding-api.open-meteo.com/v1/search" +
            "?name=$encoded&count=$RESULT_LIMIT&format=json"

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                setRequestProperty("User-Agent", "PaperScrape-CitySearch")
                setRequestProperty("Accept", "application/json")
            }
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext CitySearchResult.Failed

            // Bounded: a handful of candidate places, never a stream (SEC-03).
            val body = connection.inputStream.bufferedReader().use { it.readAtMost(MAX_HTTP_BODY_CHARS) }
                ?: return@withContext CitySearchResult.Failed
            // An empty "results" array and an absent one are the same answer from this provider:
            // it knows no such place. Only a failure to reach or read it is Failed.
            val cities = CityGeocodingParser.parse(body)
            cache.put(trimmed, cities)
            if (cities.isEmpty()) CitySearchResult.NoMatches else CitySearchResult.Found(cities)
        } catch (_: Exception) {
            CitySearchResult.Failed
        } finally {
            connection?.disconnect()
        }
    }
}
