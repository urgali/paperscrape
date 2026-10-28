package com.paperscrape.livewallpaper.weather

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import java.net.InetSocketAddress

/**
 * **A weather provider that answers with its limit is not said to be unreachable, nor to have refused the key.**
 *
 * WeatherAPI.com answers **403 with its error code 2007** when the month's quota is spent: read as a refused key, the
 * screen would say to check the key, which can take hours to become active -- the wrong place to look. And a **429** of
 * any provider is an answer, not a failure like a dropped connection, *"could not be reached"*. The transport reads the
 * body of a 403 ([WeatherHttp.statusToFailure]), and a limit has a status of its own.
 *
 * **The same reading tells Open-Meteo's refused key from a refused request**: Open-Meteo answers a key it does not know
 * with a 400 whose body gives the reason ("The supplied API key is invalid."), so the transport reads the body of a 400
 * too. That 400 is a refused key; any other is a refused request.
 *
 * Against a server on this machine, through [WeatherHttp.getJson] and [LiveWeatherStatus.of]: a transport or a status
 * that does not tell a limit apart fails here on its assertions, `UNAUTHORIZED` for the spent quota and `FAILED`/`STALE`
 * for the limit, and a transport that does not read a 400's body gives `REQUEST_REFUSED` for Open-Meteo's refused key.
 * What the settings screen says for each status is `WeatherTimeScreen`'s.
 */
class ProviderLimitMessageTest {

    private lateinit var server: HttpServer
    private var status = 200
    private var body = ""

    @Before
    fun start() {
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/current") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, if (bytes.isEmpty()) -1 else bytes.size.toLong())
            if (bytes.isNotEmpty()) exchange.responseBody.use { it.write(bytes) }
            exchange.close()
        }
        server.start()
    }

    @After
    fun stop() = server.stop(0)

    private fun answer(status: Int, body: String = ""): HttpOutcome {
        this.status = status
        this.body = body
        return runBlocking { WeatherHttp.getJson("http://127.0.0.1:${server.address.port}/current") }
    }

    @Test
    fun `WeatherAPI's spent quota is the provider's limit, not a refused key`() {
        assertEquals(
            HttpOutcome.Error(WeatherFailure.RATE_LIMITED),
            answer(403, """{"error":{"code":2007,"message":"API key has exceeded calls per month quota."}}"""),
        )
    }

    @Test
    fun `a 403 that is about the key is still a refused key`() {
        assertEquals("a key disabled", HttpOutcome.Error(WeatherFailure.UNAUTHORIZED), answer(403, """{"error":{"code":2008,"message":"API key has been disabled."}}"""))
        assertEquals("no body", HttpOutcome.Error(WeatherFailure.UNAUTHORIZED), answer(403))
        assertEquals("a body that is not json", HttpOutcome.Error(WeatherFailure.UNAUTHORIZED), answer(403, "<html>Forbidden</html>"))
        assertEquals("a wrong key", HttpOutcome.Error(WeatherFailure.UNAUTHORIZED), answer(401, """{"error":{"code":2006,"message":"API key is invalid."}}"""))
    }

    /** The body of a 400 is read off the connection: Open-Meteo's reason about the key makes it a refused key. */
    @Test
    fun `Open-Meteo's 400 about the key is a refused key, any other 400 a refused request`() {
        val keyRefused = answer(400, """{"error":true,"reason":"The supplied API key is invalid."}""")
        assertEquals("the key's reason", HttpOutcome.Error(WeatherFailure.UNAUTHORIZED), keyRefused)
        assertEquals(
            "said as a rejected key, not as a provider that could not be reached",
            LiveWeatherStatus.REJECTED_API_KEY,
            LiveWeatherStatus.of(
                enabled = true,
                hasLocation = true,
                result = WeatherFetchResult.Failed((keyRefused as HttpOutcome.Error).failure, WeatherProviderId.OPEN_METEO),
                hasSnapshotInEffect = false,
                previous = LiveWeatherStatus.OK,
            ),
        )
        assertEquals(
            "another reason",
            HttpOutcome.Error(WeatherFailure.REQUEST_REFUSED),
            answer(400, """{"reason":"Latitude must be in range of -90 to 90°. Given: 143.77.","error":true}"""),
        )
        assertEquals("no body", HttpOutcome.Error(WeatherFailure.REQUEST_REFUSED), answer(400))
    }

    @Test
    fun `a 429 is the provider's limit, as before`() {
        assertEquals(HttpOutcome.Error(WeatherFailure.RATE_LIMITED), answer(429))
    }

    @Test
    fun `a provider's limit is not said to be a provider that could not be reached`() {
        val limited = WeatherFetchResult.Failed(WeatherFailure.RATE_LIMITED, WeatherProviderId.OPEN_WEATHER)
        for (previous in LiveWeatherStatus.entries) {
            val alone = LiveWeatherStatus.of(enabled = true, hasLocation = true, result = limited, hasSnapshotInEffect = false, previous = previous)
            val withConditions = LiveWeatherStatus.of(enabled = true, hasLocation = true, result = limited, hasSnapshotInEffect = true, previous = previous)
            assertNotEquals("no conditions left, after $previous", LiveWeatherStatus.FAILED, alone)
            assertNotEquals("the last conditions still drawn, after $previous", LiveWeatherStatus.STALE, withConditions)
            assertNotEquals("and not a refused key", LiveWeatherStatus.REJECTED_API_KEY, alone)
            assertEquals("the scene is the theme's when nothing is left", true, alone.isRunningOnThemeWeather)
            assertEquals("and the last conditions go on drawing while they last, as before", true, withConditions.isDrivingTheScene)
        }
    }
}
