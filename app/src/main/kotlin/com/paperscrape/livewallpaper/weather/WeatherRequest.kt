package com.paperscrape.livewallpaper.weather

import java.net.URLEncoder
import kotlin.math.round

/**
 * The two things every provider URL has to get right: the key, and how much of the user it carries.
 *
 * Both providers used to interpolate these straight into a string. That is two separate audit
 * findings with one shape -- a value that belongs to the user being pasted into a URL as if it were
 * a literal -- so they get one answer here rather than a fix each.
 */
internal object WeatherRequest {

    /**
     * Coordinates as they go on the wire: two decimals, about 1.1 km at the equator.
     *
     * SEC-05. The location the app *asks* for is already coarse -- COARSE_LOCATION, cell towers and
     * Wi-Fi, which the manifest justifies as "accurate to a neighbourhood, which is all a weather
     * grid cell needs" -- and the settings screen shows it rounded. The request did not round, so a
     * GPS fix (the mode a user can pick explicitly) left the device at full float precision:
     * several decimal places past anything the answer depends on, sent hourly, to a third party.
     *
     * Nothing is lost by rounding. Open-Meteo resolves to roughly an 11 km grid and the other two
     * are no finer, so two decimals is already well inside one cell -- the same forecast comes
     * back. What changes is only how precisely a request describes where its sender was standing.
     */
    fun coordinate(value: Double): String {
        val rounded = round(value * 100.0) / 100.0
        // Kotlin renders -0.0 as "-0.0"; a coordinate has no signed zero and some parsers dislike
        // it. Everything else formats the way the providers' own docs write it.
        return if (rounded == 0.0) "0.0" else rounded.toString()
    }

    /**
     * An API key as a query-parameter value.
     *
     * SEC-04. Keys are typed by the user and pasted from a provider's dashboard. Neither provider
     * promises an alphanumeric alphabet, and a key holding `&`, `#`, `+` or a stray space silently
     * became *different* query parameters -- so the request went out without a usable key and came
     * back as a rejection the user could not explain, with the tail of their key sitting in the
     * server's logs as a parameter name. `+` is the one that bites quietly: it decodes as a space.
     */
    fun key(value: String): String = URLEncoder.encode(value, "UTF-8")
}
