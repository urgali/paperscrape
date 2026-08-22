package com.paperscrape.livewallpaper.location

import java.util.Locale

/**
 * How a latitude/longitude pair is written for the user to read back.
 *
 * **Always [Locale.US], and never the device's locale.** `"%.3f, %.3f".format(lat, lon)` uses the
 * default locale, and on an Italian, French or German device that produces `45,464, 9,190`: the
 * comma is simultaneously the decimal separator and the separator between the two numbers, and the
 * string stops being readable as a coordinate at all -- there is no way to tell whether that is two
 * numbers or four.
 *
 * A coordinate is a technical identifier rather than a quantity being reported, which is what makes
 * a fixed representation the right answer rather than a shortcut: it is the value a user copies
 * into a map, checks against a GPS reading, or reads out. The scene's own numbers are the opposite
 * case and are deliberately left alone -- a speed multiplier or a percentage is a quantity, it is
 * read as language, and `WorldSceneScreen`'s `"%.1fx"` must keep following the device's locale.
 */
object Coordinates {

    /** Three decimals: about 100 m, which is finer than any weather grid this app asks about. */
    fun format(latitude: Double, longitude: Double): String =
        String.format(Locale.US, "%.3f, %.3f", latitude, longitude)

    fun format(latitude: Float, longitude: Float): String =
        format(latitude.toDouble(), longitude.toDouble())

    /**
     * Two decimals -- about 1 km -- for the row that reports where the device thinks it is.
     *
     * Deliberately coarser than [format]: this one stands in for a place name when reverse
     * geocoding could not produce one, and a fix quoted to the metre implies a precision the
     * network provider does not have.
     */
    fun formatCoarse(latitude: Float, longitude: Float): String =
        String.format(Locale.US, "%.2f, %.2f", latitude, longitude)
}
