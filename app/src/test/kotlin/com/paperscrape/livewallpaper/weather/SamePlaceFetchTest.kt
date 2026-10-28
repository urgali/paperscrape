package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.location.DeviceLocationFix
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A new weather request outside the hour only for another place: farther than 11 km from the last request's.** For a
 * nearer fix, new conditions can arrive up to an hour later.
 *
 * Compared bit for bit, any new fix -- a try after a search that found nothing, another app's newer cached position, the
 * saved position read back as a float -- would send a request for coordinates that, rounded to two decimals
 * ([WeatherRequest.coordinate]), are the same cell: about one more request for every failed search that a later one puts
 * right. [LiveWeatherSchedule.isSamePlace], the weather's "same place", decides which conditions a failed fetch keeps,
 * and the request too ([LiveWeatherSchedule.movedToAnotherPlace]); `ServiceWiringTest` holds the service to it on its
 * source.
 */
class SamePlaceFetchTest {

    private val florence = DeviceLocationFix(43.77925, 11.24626)

    /** [florence] moved [northMetres] north, at about 111.2 km a degree. */
    private fun north(northMetres: Double) = DeviceLocationFix(florence.latitude + northMetres / 111_200.0, florence.longitude)

    @Test
    fun `a fix a few metres from the last request's is the same place, and waits for the hour`() {
        for (metres in listOf(0.0, 5.0, 50.0, 500.0, 5_000.0, 10_900.0)) {
            assertFalse("$metres m", LiveWeatherSchedule.movedToAnotherPlace(north(metres), florence))
        }
    }

    @Test
    fun `a fix farther than 11 km away is another place, and is asked for at once`() {
        for (metres in listOf(11_200.0, 20_000.0, 300_000.0)) {
            assertTrue("$metres m", LiveWeatherSchedule.movedToAnotherPlace(north(metres), florence))
        }
        // Milan, about 250 km from Florence, is another place; a fix 5 km away, as Fiesole is, is not (the test above).
        assertTrue(LiveWeatherSchedule.movedToAnotherPlace(DeviceLocationFix(45.4642, 9.19), florence))
    }

    @Test
    fun `with no request made yet any fix is asked for, and with no fix nothing is`() {
        assertTrue(LiveWeatherSchedule.movedToAnotherPlace(florence, null))
        assertFalse(LiveWeatherSchedule.movedToAnotherPlace(null, florence))
        assertFalse(LiveWeatherSchedule.movedToAnotherPlace(null, null))
    }

    @Test
    fun `the saved position read back as a float is the same place as the fix it was saved from`() {
        val saved = DeviceLocationFix(florence.latitude.toFloat().toDouble(), florence.longitude.toFloat().toDouble())
        assertTrue("the case the bit-for-bit comparison read as a move", saved != florence)
        assertFalse(LiveWeatherSchedule.movedToAnotherPlace(saved, florence))
    }
}
