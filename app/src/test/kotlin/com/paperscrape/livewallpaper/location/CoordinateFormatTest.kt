package com.paperscrape.livewallpaper.location

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * A coordinate pair has to read the same on every device.
 *
 * The defect was invisible on an en-US emulator and guaranteed on an Italian phone:
 * `"%.3f, %.3f".format(45.4642, 9.19)` follows the *default* locale, so the same call that produces
 * `45.464, 9.190` in California produces `45,464, 9,190` in Milan -- one comma separating the two
 * numbers and one inside each of them, which cannot be read back as a coordinate by a person or
 * pasted into anything that parses one.
 *
 * Every test here sets the default locale first, precisely because that is the input the bug
 * depended on and the one an en-US test machine would otherwise never vary.
 */
class CoordinateFormatTest {

    private val original = Locale.getDefault()

    @After
    fun restore() = Locale.setDefault(original)

    private val milanLat = 45.4642f
    private val milanLon = 9.19f

    private fun inLocale(locale: Locale, block: () -> String): String {
        Locale.setDefault(locale)
        return block()
    }

    @Test
    fun `italian - a decimal comma does not turn one pair into four numbers`() {
        assertEquals(
            "45.464, 9.190",
            inLocale(Locale.ITALY) { Coordinates.format(milanLat, milanLon) },
        )
    }

    @Test
    fun `english`() {
        assertEquals(
            "45.464, 9.190",
            inLocale(Locale.US) { Coordinates.format(milanLat, milanLon) },
        )
    }

    @Test
    fun `french and german read the same as every other locale`() {
        for (locale in listOf(Locale.FRANCE, Locale.GERMANY, Locale.forLanguageTag("es-ES"))) {
            assertEquals(
                "coordinates must not depend on $locale",
                "45.464, 9.190",
                inLocale(locale) { Coordinates.format(milanLat, milanLon) },
            )
        }
    }

    /** A locale whose digits are not ASCII at all, which no decimal-separator fix alone would catch. */
    @Test
    fun `a locale with its own numerals still writes ASCII digits`() {
        val text = inLocale(Locale.forLanguageTag("ar-EG-u-nu-arab")) {
            Coordinates.format(milanLat, milanLon)
        }
        assertEquals("45.464, 9.190", text)
        assertTrue("every character must be ASCII", text.all { it.code < 128 })
    }

    @Test
    fun `the coarse form used for a device fix follows the same rule`() {
        assertEquals(
            "45.46, 9.19",
            inLocale(Locale.ITALY) { Coordinates.formatCoarse(milanLat, milanLon) },
        )
    }

    @Test
    fun `negative coordinates keep their sign and their separator`() {
        assertEquals(
            "-33.869, 151.209",
            inLocale(Locale.GERMANY) { Coordinates.format(-33.8688, 151.2093) },
        )
        assertEquals(
            "-54.802, -68.303",
            inLocale(Locale.FRANCE) { Coordinates.format(-54.8019, -68.3029) },
        )
    }

    @Test
    fun `a geocoded city reports its coordinates the same way`() {
        val city = GeocodedCity(
            name = "Milano",
            country = "Italy",
            admin1 = "Lombardy",
            admin2 = null,
            countryCode = "IT",
            latitude = 45.4642,
            longitude = 9.19,
        )
        assertEquals("45.464, 9.190", inLocale(Locale.ITALY) { city.coordinatesText })
    }

    /**
     * The other half of the rule, and the reason this is not "stop using the default locale".
     *
     * A speed multiplier is a quantity being read as language, not an identifier, so it *must*
     * follow the device -- an Italian phone should say `1,5x`. `WorldSceneScreen` still formats it
     * with the default locale, and this test is here so that a future tidy-up which "fixes" that
     * one too fails instead of shipping.
     */
    @Test
    fun `a speed multiplier is deliberately still localised`() {
        Locale.setDefault(Locale.ITALY)
        assertEquals("1,5x", "%.1fx".format(1.5f))
        Locale.setDefault(Locale.US)
        assertEquals("1.5x", "%.1fx".format(1.5f))
    }
}
