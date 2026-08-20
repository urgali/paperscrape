package com.paperscrape.livewallpaper.location

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reading Open-Meteo's geocoding response, and turning a result into something a user can choose
 * between.
 *
 * The bodies below are the provider's real response shape. What is being defended is mostly the
 * ambiguous case: a search for a name several places share must produce all of them, each carrying
 * enough to tell it from the others, and never a silent pick of the first.
 */
class CityGeocodingParserTest {

    private val milanBody = """
        {"results":[
          {"id":3173435,"name":"Milano","latitude":45.46427,"longitude":9.18951,
           "country_code":"IT","country":"Italy","admin1":"Lombardy","admin2":"Milan"}
        ],"generationtime_ms":0.5}
    """.trimIndent()

    private val springfieldBody = """
        {"results":[
          {"id":4250542,"name":"Springfield","latitude":39.80172,"longitude":-89.64371,
           "country_code":"US","country":"United States","admin1":"Illinois","admin2":"Sangamon"},
          {"id":4951788,"name":"Springfield","latitude":42.10148,"longitude":-72.58981,
           "country_code":"US","country":"United States","admin1":"Massachusetts","admin2":"Hampden"},
          {"id":2172517,"name":"Springfield","latitude":-27.66667,"longitude":152.91667,
           "country_code":"AU","country":"Australia","admin1":"Queensland"}
        ]}
    """.trimIndent()

    @Test
    fun `a single match is read with its region and country`() {
        val cities = CityGeocodingParser.parse(milanBody)
        assertEquals(1, cities.size)
        val milan = cities.single()
        assertEquals("Milano", milan.name)
        assertEquals("Lombardy", milan.admin1)
        assertEquals("Italy", milan.country)
        assertEquals("IT", milan.countryCode)
        assertEquals(45.46427, milan.latitude, 0.00001)
        assertEquals(9.18951, milan.longitude, 0.00001)
    }

    @Test
    fun `every place sharing a name is returned, in the order the provider gave them`() {
        val cities = CityGeocodingParser.parse(springfieldBody)
        assertEquals(3, cities.size)
        assertTrue(cities.all { it.name == "Springfield" })
        assertEquals(listOf("Illinois", "Massachusetts", "Queensland"), cities.map { it.admin1 })
    }

    @Test
    fun `results sharing a name are told apart by region and country`() {
        val cities = CityGeocodingParser.parse(springfieldBody)
        val lines = cities.map { it.disambiguation }
        assertEquals(lines.size, lines.toSet().size) // no two results read the same
        assertEquals("Illinois, Sangamon, United States", lines[0])
        assertEquals("Queensland, Australia", lines[2])
    }

    @Test
    fun `a known place with no region still identifies itself by country`() {
        val body = """{"results":[{"name":"Monaco","latitude":43.73333,"longitude":7.41667,
            "country":"Monaco","country_code":"MC"}]}"""
        val city = CityGeocodingParser.parse(body).single()
        assertEquals("Monaco", city.disambiguation)
        assertEquals("Monaco, Monaco", city.label)
    }

    @Test
    fun `the stored label is the city and its country`() {
        assertEquals("Milano, Italy", CityGeocodingParser.parse(milanBody).single().label)
    }

    @Test
    fun `coordinates are shown for verification, not as the headline`() {
        assertEquals("45.464, 9.190", CityGeocodingParser.parse(milanBody).single().coordinatesText)
    }

    @Test
    fun `a response with no results parses to nothing rather than failing`() {
        assertTrue(CityGeocodingParser.parse("""{"generationtime_ms":0.3}""").isEmpty())
        assertTrue(CityGeocodingParser.parse("""{"results":[]}""").isEmpty())
    }

    @Test
    fun `an unreadable body parses to nothing rather than throwing`() {
        assertTrue(CityGeocodingParser.parse("").isEmpty())
        assertTrue(CityGeocodingParser.parse("<html>502 Bad Gateway</html>").isEmpty())
        assertTrue(CityGeocodingParser.parse("""{"results":"unexpected"}""").isEmpty())
    }

    /**
     * A result with no usable position must be dropped, not defaulted: a custom location of (0, 0)
     * is a real place in the Gulf of Guinea, and the wallpaper would happily fetch its weather.
     */
    @Test
    fun `a result without usable coordinates is dropped, and the rest survive`() {
        val body = """{"results":[
            {"name":"Nowhere","country":"Testland"},
            {"name":"Somewhere","latitude":1.5,"longitude":2.5,"country":"Testland"},
            {"name":"Impossible","latitude":95.0,"longitude":0.0,"country":"Testland"}
        ]}"""
        val cities = CityGeocodingParser.parse(body)
        assertEquals(listOf("Somewhere"), cities.map { it.name })
    }

    @Test
    fun `a result without a name is dropped`() {
        val body = """{"results":[{"latitude":1.0,"longitude":2.0,"country":"Testland"}]}"""
        assertTrue(CityGeocodingParser.parse(body).isEmpty())
    }

    @Test
    fun `a region equal to the city name is not repeated in the disambiguation`() {
        val body = """{"results":[{"name":"Luxembourg","latitude":49.61,"longitude":6.13,
            "country":"Luxembourg","admin1":"Luxembourg"}]}"""
        assertEquals("Luxembourg", CityGeocodingParser.parse(body).single().disambiguation)
    }

    // -- what a selection becomes ---------------------------------------------------------------

    /**
     * A searched city and a typed one must be indistinguishable downstream: the same three values,
     * written through the same setter, so Live Weather, the sunrise/sunset calculation, the cache
     * and the fallback have nothing new to handle.
     */
    @Test
    fun `a selected city converts to the same three values a manual entry writes`() {
        val city = CityGeocodingParser.parse(milanBody).single()
        val latitude: Float = city.latitude.toFloat()
        val longitude: Float = city.longitude.toFloat()
        val label: String = city.label

        assertEquals(45.46427f, latitude, 0.0001f)
        assertEquals(9.18951f, longitude, 0.0001f)
        assertEquals("Milano, Italy", label)
        assertTrue("a stored latitude must be in range", latitude in -90f..90f)
        assertTrue("a stored longitude must be in range", longitude in -180f..180f)
    }
}

/** The search cache: small, in-memory, and only ever a shortcut. */
class CitySearchCacheTest {

    @Test
    fun `a repeated search is answered without asking again`() {
        val cache = CitySearchCache()
        val results = CityGeocodingParser.parse("""{"results":[{"name":"Oslo","latitude":59.91,"longitude":10.75}]}""")
        cache.put("Oslo", results)
        assertNotNull(cache.get("Oslo"))
        assertEquals(1, cache.get("Oslo")!!.size)
    }

    @Test
    fun `case and surrounding space do not create separate entries`() {
        val cache = CitySearchCache()
        cache.put("Oslo", emptyList())
        assertNotNull(cache.get("  oslo "))
        assertEquals(1, cache.size())
    }

    @Test
    fun `an unsearched query is a miss, not an empty result`() {
        assertNull(CitySearchCache().get("Reykjavik"))
    }

    /** "No such place" is worth remembering too: retyping it must not re-ask the provider. */
    @Test
    fun `a search that found nothing is remembered as having found nothing`() {
        val cache = CitySearchCache()
        cache.put("qqqqqq", emptyList())
        assertEquals(emptyList<GeocodedCity>(), cache.get("qqqqqq"))
    }

    @Test
    fun `the cache stays bounded and drops the oldest entry first`() {
        val cache = CitySearchCache(maxEntries = 3)
        listOf("a1", "b2", "c3", "d4").forEach { cache.put(it, emptyList()) }
        assertEquals(3, cache.size())
        assertNull("the oldest entry should have been dropped", cache.get("a1"))
        assertNotNull(cache.get("d4"))
    }

    @Test
    fun `re-searching an entry keeps it from being dropped`() {
        val cache = CitySearchCache(maxEntries = 2)
        cache.put("a1", emptyList())
        cache.put("b2", emptyList())
        cache.put("a1", emptyList()) // touched again, so b2 is now the oldest
        cache.put("c3", emptyList())
        assertNotNull(cache.get("a1"))
        assertNull(cache.get("b2"))
    }
}

/** When a query is worth sending at all. */
class CityQueryTest {

    @Test
    fun `a query shorter than two characters is not sent`() {
        assertTrue(!CityGeocoder.isQuerySearchable(""))
        assertTrue(!CityGeocoder.isQuerySearchable("M"))
        assertTrue(!CityGeocoder.isQuerySearchable("   "))
    }

    @Test
    fun `two characters or more is searchable`() {
        assertTrue(CityGeocoder.isQuerySearchable("Mi"))
        assertTrue(CityGeocoder.isQuerySearchable(" Milano "))
    }
}
