package com.paperscrape.livewallpaper.location

import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

/**
 * [LocalityLabelCache] -- the policy that decides when a device fix is worth reverse-geocoding.
 *
 * The lookup itself is injected, so nothing here touches Android, a `Geocoder` or the network: what
 * is under test is *how often the app asks* and *which answer it keeps*, which is the part that can
 * regress silently. A cache that quietly stopped caching would look identical on screen and cost a
 * geocode per fix.
 *
 * Milan and the places around it are used throughout because the distances between them are known
 * and are on the two sides of the threshold that matter.
 */
class LocalityLabelCacheTest {

    private val milanLat = 45.4642
    private val milanLon = 9.1900

    /** Counts calls, so "did it ask again" is an assertion rather than an inference. */
    private class CountingLookup(private val answer: String?) {
        val calls = AtomicInteger(0)
        val lookup: suspend (Double, Double) -> String? = { _, _ ->
            calls.incrementAndGet()
            answer
        }
    }

    // -- the happy path, which is the same path for GPS and for Network ---------------------------

    /**
     * GPS: coordinates in, geocoder answer out, and it is what the row shows as its title.
     *
     * There is deliberately no separate Network case in this suite, and that is the point rather
     * than an omission: both modes write the same `resolvedGpsLatitude`/`resolvedGpsLongitude` and
     * reach this through the same row, so a "Network" test would exercise byte-identical code and
     * would only assert that two constants differ. What is actually mode-specific is the supporting
     * text, which lives in the composable. `both device modes resolve through the same stored fix`
     * below pins the one thing that could make them diverge.
     */
    @Test
    fun `a fix with no cached answer is looked up once`() = runBlocking {
        val cache = LocalityLabelCache()
        val geocoder = CountingLookup("Milano, Italia")

        assertEquals("Milano, Italia", cache.labelFor(milanLat, milanLon, 0L, geocoder.lookup))
        assertEquals(1, geocoder.calls.get())
    }

    /**
     * Both device modes carry their fix in **one** pair of stored fields, which is why one row and
     * one cache serve both.
     *
     * If a mode ever gained its own storage, this cache would be keyed on the wrong thing and a
     * label could outlive a switch between them -- the row would name the place the *other*
     * provider had found. Asserted through `LocationSource` rather than by reading the composable,
     * because that is where the two modes are actually told apart.
     */
    @Test
    fun `both device modes resolve through the same stored fix`() {
        val gps = WallpaperSettings(
            useLocationForSunTimes = true,
            deviceLocationKind = DeviceLocationKind.GPS,
            resolvedGpsLatitude = 45.4642f,
            resolvedGpsLongitude = 9.19f,
        )
        val network = gps.copy(deviceLocationKind = DeviceLocationKind.NETWORK)

        assertEquals(LocationSource.GPS, LocationSource.of(gps))
        assertEquals(LocationSource.NETWORK, LocationSource.of(network))
        // Two different sources, two different providers, one position.
        assertNotEquals(LocationSource.of(gps), LocationSource.of(network))
        assertEquals(gps.resolvedGpsLatitude, network.resolvedGpsLatitude)
        assertEquals(gps.resolvedGpsLongitude, network.resolvedGpsLongitude)

        // And Custom is deliberately not one of them: it keeps its own coordinates and its own
        // label, and never reaches this cache.
        val custom = WallpaperSettings(useCustomLocation = true)
        assertEquals(LocationSource.CUSTOM, LocationSource.of(custom))
        assertNull("Custom needs no device provider", LocationSource.of(custom).deviceKind)
    }

    // -- caching (section 10 of the brief) --------------------------------------------------------

    /** The same coordinates must never cost a second lookup. */
    @Test
    fun `the same fix is not looked up twice`() = runBlocking {
        val cache = LocalityLabelCache()
        val geocoder = CountingLookup("Milano, Italia")

        cache.labelFor(milanLat, milanLon, 0L, geocoder.lookup)
        cache.labelFor(milanLat, milanLon, 1_000L, geocoder.lookup)
        cache.labelFor(milanLat, milanLon, 90_000_000L, geocoder.lookup)

        assertEquals("one lookup, however often the row is rebuilt", 1, geocoder.calls.get())
    }

    /**
     * Re-entering the screen paints the known name on the first frame.
     *
     * `cachedLabel` is the synchronous half of the same policy, and the reason the row does not
     * flash coordinates and then replace them every time the user navigates back.
     */
    @Test
    fun `a known position answers synchronously with no lookup`() = runBlocking {
        val cache = LocalityLabelCache()
        val geocoder = CountingLookup("Milano, Italia")
        cache.labelFor(milanLat, milanLon, 0L, geocoder.lookup)

        assertEquals("Milano, Italia", cache.cachedLabel(milanLat, milanLon))
        assertEquals(1, geocoder.calls.get())
        assertNull("an unknown position has no synchronous answer", cache.cachedLabel(48.85, 2.35))
    }

    /**
     * A fix that moved less than the threshold is the same place.
     *
     * ~300 m north of the first fix: further than GPS jitter, well inside the town, and exactly the
     * kind of movement that used to cost a lookup.
     */
    @Test
    fun `a small move reuses the label`() = runBlocking {
        val cache = LocalityLabelCache()
        val geocoder = CountingLookup("Milano, Italia")
        cache.labelFor(milanLat, milanLon, 0L, geocoder.lookup)

        val nearby = milanLat + 0.0027 // about 300 m
        assertTrue(LocalityLabelCache.distanceMetres(milanLat, milanLon, nearby, milanLon) < 400.0)
        assertEquals("Milano, Italia", cache.labelFor(nearby, milanLon, 1_000L, geocoder.lookup))
        assertEquals(1, geocoder.calls.get())
    }

    /** A move past the threshold is a new question, and gets asked. */
    @Test
    fun `a significant move is a new lookup`() = runBlocking {
        val cache = LocalityLabelCache()
        val first = CountingLookup("Milano, Italia")
        cache.labelFor(milanLat, milanLon, 0L, first.lookup)

        val far = CountingLookup("Monza, Italia")
        val monzaLat = 45.5845
        val monzaLon = 9.2744
        assertTrue(LocalityLabelCache.distanceMetres(milanLat, milanLon, monzaLat, monzaLon) > 1_000.0)

        assertEquals("Monza, Italia", cache.labelFor(monzaLat, monzaLon, 1_000L, far.lookup))
        assertEquals(1, far.calls.get())
        assertEquals("and the new answer replaces the old one", "Monza, Italia", cache.cachedLabel(monzaLat, monzaLon))
    }

    /**
     * **The threshold is tied to what the row displays, not chosen freely.**
     *
     * The coordinates on this row are `Coordinates.formatCoarse`, two decimals, about 1 km. If the
     * cache's threshold were coarser than that, the row could show two different coordinate strings
     * under one unchanged place name -- a visible inconsistency the user could read off the screen.
     * This pins the relationship rather than the number.
     */
    @Test
    fun `the cache cannot hide a change the row would show`() {
        // One step of the second decimal of latitude, which is the smallest change the row can
        // display, is at least as large as the threshold... at the equator it is ~1.11 km.
        val oneDisplayedStep = LocalityLabelCache.distanceMetres(45.46, 9.19, 45.47, 9.19)
        assertTrue(
            "a displayed latitude step ($oneDisplayedStep m) must reach the threshold",
            oneDisplayedStep >= LocalityLabelCache.SIGNIFICANT_MOVE_METRES,
        )
    }

    // -- failure (sections 12 and 15) -------------------------------------------------------------

    /**
     * A geocoder that cannot answer costs the name and nothing else.
     *
     * The position is already known -- it is the caller's own argument -- so there is nothing here
     * that can invalidate it. Null means "show the coordinates", never "the location failed".
     */
    @Test
    fun `a failed lookup is not cached as a label`() = runBlocking {
        val cache = LocalityLabelCache()
        val offline = CountingLookup(null)

        assertNull(cache.labelFor(milanLat, milanLon, 0L, offline.lookup))
        assertNull("a failure must never become a label", cache.cachedLabel(milanLat, milanLon))
    }

    /** An offline screen must not re-ask on every recomposition. */
    @Test
    fun `a failure is not retried inside the back-off`() = runBlocking {
        val cache = LocalityLabelCache()
        val offline = CountingLookup(null)

        cache.labelFor(milanLat, milanLon, 0L, offline.lookup)
        cache.labelFor(milanLat, milanLon, 1_000L, offline.lookup)
        cache.labelFor(milanLat, milanLon, LocalityLabelCache.RETRY_AFTER_FAILURE_MILLIS - 1, offline.lookup)

        assertEquals("still inside the back-off", 1, offline.calls.get())
    }

    /** ...and must recover once it is over, so coming back online is not a permanent loss. */
    @Test
    fun `a failure is retried after the back-off, and then succeeds`() = runBlocking {
        val cache = LocalityLabelCache()
        cache.labelFor(milanLat, milanLon, 0L, CountingLookup(null).lookup)

        val backOnline = CountingLookup("Milano, Italia")
        val label = cache.labelFor(
            milanLat,
            milanLon,
            LocalityLabelCache.RETRY_AFTER_FAILURE_MILLIS + 1,
            backOnline.lookup,
        )

        assertEquals("Milano, Italia", label)
        assertEquals(1, backOnline.calls.get())
        assertEquals("Milano, Italia", cache.cachedLabel(milanLat, milanLon))
    }

    /**
     * A timeout reaches here as a null, because that is what [LocationLabelResolver] returns when
     * its own 6-second bound expires. The cache adds no wait of its own: it neither delays the
     * answer nor holds a lock across the lookup, so nothing here can be what blocks a screen.
     */
    @Test
    fun `a timed-out lookup is treated as a failure, not as an answer`() = runBlocking {
        val cache = LocalityLabelCache()
        assertNull(cache.labelFor(milanLat, milanLon, 0L) { _, _ -> null })
        assertNull(cache.cachedLabel(milanLat, milanLon))

        // And the position it could not name is not confused with one it could.
        cache.labelFor(48.8566, 2.3522, 200_000L) { _, _ -> "Paris, France" }
        assertNull(cache.cachedLabel(milanLat, milanLon))
    }

    // -- sequencing (section 15: cancellation) -----------------------------------------------------

    /**
     * **A slow lookup for where the device *was* must not overwrite the answer for where it *is*.**
     *
     * `LaunchedEffect` cancels the superseded coroutine, but the cache outlives the composition, so
     * cancellation alone does not protect it -- a lookup that had already returned could still
     * store its answer. The request counter is what makes the newest position win, and this is the
     * interleaving that proves it: the Milan lookup is gated open *after* the Paris one has already
     * completed and been stored.
     */
    @Test
    fun `a superseded lookup does not overwrite the newer answer`() = runBlocking {
        val cache = LocalityLabelCache()
        val started = CompletableDeferred<Unit>()
        val gate = CompletableDeferred<String?>()

        val slowFirst = launch {
            cache.labelFor(milanLat, milanLon, 0L) { _, _ ->
                // Signalled from *inside* the lookup, so by the time it resolves the Milan request
                // has already claimed its number. Awaiting it below is what makes the order of the
                // two requests deterministic instead of a race with the dispatcher.
                started.complete(Unit)
                gate.await()
            }
        }
        started.await()

        val parisLat = 48.8566
        val parisLon = 2.3522
        cache.labelFor(parisLat, parisLon, 1_000L) { _, _ -> "Paris, France" }
        assertEquals("Paris, France", cache.cachedLabel(parisLat, parisLon))

        // The stale answer finally arrives.
        gate.complete("Milano, Italia")
        slowFirst.join()

        assertEquals(
            "the newest position's answer must survive the older lookup landing late",
            "Paris, France",
            cache.cachedLabel(parisLat, parisLon),
        )
        assertNull(
            "and the superseded answer must not have been stored at all",
            cache.cachedLabel(milanLat, milanLon),
        )
    }

    // -- the distance rule -------------------------------------------------------------------------

    /**
     * Great-circle, not Euclidean.
     *
     * A degree of longitude is ~111 km at the equator and ~55 km at 60°N, so a threshold in metres
     * computed from raw degree differences would mean two different things in Nairobi and in
     * Bergen. These are the numbers that would move if someone "simplified" the haversine away.
     */
    @Test
    fun `distance is measured on the sphere`() {
        val milanToParis = LocalityLabelCache.distanceMetres(45.4642, 9.1900, 48.8566, 2.3522)
        assertEquals("Milan to Paris is about 640 km", 640_000.0, milanToParis, 15_000.0)

        assertEquals("a position is zero from itself", 0.0, LocalityLabelCache.distanceMetres(45.46, 9.19, 45.46, 9.19), 0.0001)

        // One degree of longitude, at the equator and at 60 degrees north. Euclidean arithmetic
        // would make these equal; they differ by half.
        val atEquator = LocalityLabelCache.distanceMetres(0.0, 0.0, 0.0, 1.0)
        val atSixty = LocalityLabelCache.distanceMetres(60.0, 0.0, 60.0, 1.0)
        assertEquals(111_000.0, atEquator, 2_000.0)
        assertEquals(55_500.0, atSixty, 2_000.0)
        assertNotEquals(atEquator, atSixty, 1_000.0)

        // Antipodal points must not produce NaN through the square-root rounding path.
        val antipodal = LocalityLabelCache.distanceMetres(0.0, 0.0, 0.0, 180.0)
        assertTrue("antipodal distance must be finite, was $antipodal", antipodal.isFinite())
    }

    /** The shared instance is the one the row uses, and it can be emptied. */
    @Test
    fun `the shared cache can be cleared`() = runBlocking {
        LocalityLabelCache.shared.clear()
        LocalityLabelCache.shared.labelFor(milanLat, milanLon, 0L) { _, _ -> "Milano, Italia" }
        assertEquals("Milano, Italia", LocalityLabelCache.shared.cachedLabel(milanLat, milanLon))

        LocalityLabelCache.shared.clear()
        assertNull(LocalityLabelCache.shared.cachedLabel(milanLat, milanLon))
    }
}
