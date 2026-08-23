package com.paperscrape.livewallpaper.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The shape of the place name shown beside a device fix.
 *
 * `LocationLabelResolver.format` is the pure half of the resolver -- the half that decides which of
 * an `Address`'s many fields become the two words on screen. Split out in v4.0 so that the choice is
 * pinned by tests instead of only being visible on a device that happens to be standing somewhere
 * interesting: the interesting cases are all *absences*, and a device standing in a city centre
 * exercises none of them.
 *
 * Nothing here constructs an `android.location.Address` -- it is a framework class with no working
 * implementation on the unit-test classpath, and the fields are all this function ever reads.
 */
class LocationLabelFormatTest {

    // -- the ordinary case ------------------------------------------------------------------------

    /** A town with a country: the two parts a resident would give. */
    @Test
    fun `a locality and a country make the label`() {
        assertEquals(
            "Milano, Italia",
            LocationLabelResolver.format(
                locality = "Milano",
                subAdminArea = "Milano",
                adminArea = "Lombardia",
                countryName = "Italia",
            ),
        )
    }

    /**
     * **The duplication the brief calls out cannot happen**, and this is why.
     *
     * `"Milano, Milano, Italia"` would need two place fields in the label. Exactly one is ever
     * chosen, so the shape is structurally two parts -- even when `locality`, `subAdminArea` and
     * `adminArea` all carry the same word, as they do above and as they routinely do in provinces
     * named after their capital.
     */
    @Test
    fun `a place is never named twice`() {
        val label = LocationLabelResolver.format(
            locality = "Milano",
            subAdminArea = "Milano",
            adminArea = "Milano",
            countryName = "Italia",
        )
        assertEquals("Milano, Italia", label)
        assertEquals("exactly one comma", 1, label!!.count { it == ',' })
    }

    // -- the fallback chain, which is where the real behaviour lives -------------------------------

    /** No town name: the county/province is the next-narrowest true answer. */
    @Test
    fun `without a locality the sub-admin area is used`() {
        assertEquals(
            "Siena, Italia",
            LocationLabelResolver.format(
                locality = null,
                subAdminArea = "Siena",
                adminArea = "Toscana",
                countryName = "Italia",
            ),
        )
    }

    /** Out in the country, sometimes only the region is known. Coarse beats nothing. */
    @Test
    fun `without a locality or sub-admin area the admin area is used`() {
        assertEquals(
            "Toscana, Italia",
            LocationLabelResolver.format(
                locality = null,
                subAdminArea = null,
                adminArea = "Toscana",
                countryName = "Italia",
            ),
        )
    }

    /** Mid-ocean and mid-desert fixes routinely resolve to a country and nothing else. */
    @Test
    fun `a country alone is still a usable label`() {
        assertEquals(
            "Italia",
            LocationLabelResolver.format(locality = null, subAdminArea = null, adminArea = null, countryName = "Italia"),
        )
    }

    /** A place with no country is shown as itself rather than being discarded. */
    @Test
    fun `a place alone is still a usable label`() {
        assertEquals(
            "Milano",
            LocationLabelResolver.format(locality = "Milano", subAdminArea = null, adminArea = null, countryName = null),
        )
    }

    /**
     * Nothing usable is **null**, not an empty string.
     *
     * The row's fallback turns on exactly this: null means "show the coordinates", and a blank
     * label would instead paint an empty title over a position the app knows perfectly well.
     */
    @Test
    fun `an address with nothing in it produces no label`() {
        assertNull(LocationLabelResolver.format(null, null, null, null))
    }

    // -- the cases that produced something ugly ----------------------------------------------------

    /**
     * **City-states.** In Singapore, Monaco or the Vatican the place and the country are the same
     * word, and joining them gives `"Singapore, Singapore"` -- which reads as a bug even though
     * every field was correct. One word.
     */
    @Test
    fun `a city-state is named once, not twice`() {
        assertEquals(
            "Singapore",
            LocationLabelResolver.format(
                locality = "Singapore",
                subAdminArea = null,
                adminArea = "Singapore",
                countryName = "Singapore",
            ),
        )
        assertEquals(
            "Monaco",
            LocationLabelResolver.format(locality = "Monaco", subAdminArea = null, adminArea = null, countryName = "Monaco"),
        )
    }

    /** Case alone does not make two names different. */
    @Test
    fun `the duplicate check ignores case`() {
        assertEquals(
            "SINGAPORE",
            LocationLabelResolver.format(locality = "SINGAPORE", subAdminArea = null, adminArea = null, countryName = "Singapore"),
        )
    }

    /**
     * Blank and whitespace-only fields are absences.
     *
     * Geocoders return `""` about as often as they return null, and an empty string that reached
     * the label would produce a stray leading comma -- `", Italia"`.
     */
    @Test
    fun `blank fields are treated as missing`() {
        assertEquals(
            "Toscana, Italia",
            LocationLabelResolver.format(locality = "", subAdminArea = "   ", adminArea = "Toscana", countryName = "Italia"),
        )
        assertEquals(
            "Milano",
            LocationLabelResolver.format(locality = "Milano", subAdminArea = null, adminArea = null, countryName = "  "),
        )
        assertNull(LocationLabelResolver.format("", " ", "\t", ""))
    }

    /** Surrounding whitespace is trimmed, but the name itself is never rewritten. */
    @Test
    fun `names are trimmed and otherwise left exactly as the geocoder gave them`() {
        assertEquals(
            "Reggio nell'Emilia, Italia",
            LocationLabelResolver.format(
                locality = "  Reggio nell'Emilia  ",
                subAdminArea = null,
                adminArea = null,
                countryName = " Italia ",
            ),
        )
        // No title-casing, no translation, no abbreviation: the device's locale already decided
        // this, and second-guessing it is how "München" becomes "Munchen".
        assertEquals(
            "München, Deutschland",
            LocationLabelResolver.format("München", null, null, "Deutschland"),
        )
    }
}
