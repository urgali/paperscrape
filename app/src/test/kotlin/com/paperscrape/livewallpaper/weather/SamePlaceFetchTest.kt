package com.paperscrape.livewallpaper.weather

import com.paperscrape.livewallpaper.location.DeviceLocationFix
import org.junit.Assert.assertEquals
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
    fun `a phone's fix up to 3 km from the last request's is the same place, and waits for the hour`() {
        for (metres in listOf(0.0, 5.0, 50.0, 500.0, 2_000.0, 2_990.0)) {
            assertFalse("$metres m", LiveWeatherSchedule.movedToAnotherPlace(north(metres), florence))
        }
    }

    @Test
    fun `a phone's fix farther than 3 km away is another place, and is asked for at once`() {
        for (metres in listOf(3_010.0, 5_000.0, 20_000.0, 300_000.0)) {
            assertTrue("$metres m", LiveWeatherSchedule.movedToAnotherPlace(north(metres), florence))
        }
        // Milan, about 250 km from Florence, is another place; a fix 2 km away is not (the test above).
        assertTrue(LiveWeatherSchedule.movedToAnotherPlace(DeviceLocationFix(45.4642, 9.19), florence))
    }

    /** A written place moved more than 10 m is a change the user made: asked at once, however near (decision 101). */
    @Test
    fun `a written place moved more than 10 m is another place, asked at once`() {
        for (metres in listOf(11.0, 200.0, 2_000.0, 300_000.0)) {
            assertTrue("$metres m", LiveWeatherSchedule.movedToAnotherPlace(north(metres), florence, written = true))
        }
        for (metres in listOf(0.0, 5.0, 9.0)) {
            assertFalse("$metres m", LiveWeatherSchedule.movedToAnotherPlace(north(metres), florence, written = true))
        }
        val saved = DeviceLocationFix(florence.latitude.toFloat().toDouble(), florence.longitude.toFloat().toDouble())
        assertFalse("read back as a float, written", LiveWeatherSchedule.movedToAnotherPlace(saved, florence, written = true))
    }

    /**
     * A pass takes the position the phone holds (decision 101) when it is 30 minutes old or less and 100 m or more from the
     * one kept, or none is kept.
     */
    @Test
    fun `a pass takes the position the phone holds, fresh and moved`() {
        val min = 60_000L
        assertTrue(LiveWeatherSchedule.takesHeldPosition(north(5_000.0), 30 * min, florence))
        assertFalse("too old", LiveWeatherSchedule.takesHeldPosition(north(5_000.0), 30 * min + 1, florence))
        assertTrue(LiveWeatherSchedule.takesHeldPosition(north(101.0), 0L, florence))
        assertFalse("too near", LiveWeatherSchedule.takesHeldPosition(north(99.0), 0L, florence))
        assertTrue("none kept", LiveWeatherSchedule.takesHeldPosition(north(0.0), 0L, null))
        assertEquals(3.0, LiveWeatherSchedule.SAME_PLACE_KM, 0.0)
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
