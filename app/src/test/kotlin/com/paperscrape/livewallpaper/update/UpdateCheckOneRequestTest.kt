package com.paperscrape.livewallpaper.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/**
 * **One request for one moment** (v5.15C2, inventory I-956, I-967): the settings screen opening, the job the wallpaper
 * puts back, a run of the job and the button can each want GitHub at one instant, and until v5.15C2 each asked -- two
 * requests 2 ms apart on the BV6600 when the settings put the job back. [UpdateChecker.checkForUpdate] keeps one check
 * on the network at a time: one that finds another there takes its answer, and one nobody asked for
 * (`takeRecentAnswer`) takes an answer GitHub gave less than [UpdateChecker.RECENT_ANSWER_MILLIS] ago.
 *
 * Against a real `HttpServer` on a loopback port, as `UpdateCheckRateLimitTest`, counting the requests that reach it.
 * Each test serves its list at a path of its own: the last answer is the checker's, kept across the tests of this JVM.
 */
class UpdateCheckOneRequestTest {

    private var server: HttpServer? = null
    private val requests = AtomicInteger()
    private val log = mutableListOf<String>()
    private val savedSink = UpdateCheckLog.sink

    init {
        UpdateCheckLog.sink = { synchronized(log) { log += it } }
    }

    @After
    fun stop() {
        server?.stop(0)
        server = null
        UpdateCheckLog.sink = savedSink
    }

    /** A list with nothing newer than 3.1, answered after [hold] lets it go; a path no other test serves. */
    private fun serve(hold: CountDownLatch? = null, arrived: CountDownLatch? = null): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val path = "/one-request-${UUID.randomUUID()}/releases"
        http.createContext(path) { exchange ->
            requests.incrementAndGet()
            arrived?.countDown()
            hold?.await(10, TimeUnit.SECONDS)
            val bytes = """[{"tag_name":"v3.1","html_url":"https://github.com/urgali/paperscrape/releases/tag/v3.1"}]""".toByteArray()
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        http.executor = java.util.concurrent.Executors.newCachedThreadPool()
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}$path"
    }

    private val now = 1_900_000_000_000L

    private fun check(url: String, recent: Boolean, at: Long = now, version: String = "3.1") =
        runBlocking { UpdateChecker.checkForUpdate(version, null, apiUrl = url, takeRecentAnswer = recent, nowMillis = { at }) }

    @Test
    fun `a check that finds another on the network takes its answer, whoever asked`() {
        val hold = CountDownLatch(1)
        val arrived = CountDownLatch(1)
        val url = serve(hold, arrived)
        val (first, second) = runBlocking {
            // The screen's check, held on the network...
            val first = async(Dispatchers.Default) { UpdateChecker.checkForUpdate("3.1", null, apiUrl = url, takeRecentAnswer = true, nowMillis = { now }) }
            assertTrue("the first check never reached the server", arrived.await(10, TimeUnit.SECONDS))
            // ...and the button pressed meanwhile, which takes no recent answer of its own accord.
            val second = async(Dispatchers.Default) { UpdateChecker.checkForUpdate("3.1", null, apiUrl = url, takeRecentAnswer = false, nowMillis = { now }) }
            Thread.sleep(500)
            hold.countDown()
            first.await() to second.await()
        }
        assertEquals("two checks of one moment, one request", 1, requests.get())
        assertEquals(UpdateCheckResult.UpToDate, first)
        assertEquals("the waiting check has the other's answer", first, second)
        val lines = synchronized(log) { log.toList() }
        assertEquals(lines.toString(), 1, lines.count { it.startsWith("update check: up to date, HTTP 200") })
        assertEquals(lines.toString(), 1, lines.count { it.startsWith("update check: not made, answered by the check of ") })
    }

    @Test
    fun `an answer of less than a minute stands for a check nobody asked for, and not for one that asks`() {
        val url = serve()
        check(url, recent = false)
        assertEquals(1, requests.get())
        // The job's run a moment later, or the screen opening: no request.
        assertEquals(UpdateCheckResult.UpToDate, check(url, recent = true, at = now + UpdateChecker.RECENT_ANSWER_MILLIS - 1))
        assertEquals("an answer of 59.999 s stands", 1, requests.get())
        // A minute on, it does not.
        check(url, recent = true, at = now + UpdateChecker.RECENT_ANSWER_MILLIS)
        assertEquals("an answer of a minute is not this moment's", 2, requests.get())
        // The button, at once after: it asks.
        check(url, recent = false, at = now + UpdateChecker.RECENT_ANSWER_MILLIS + 1)
        assertEquals("the button asks", 3, requests.get())
        // A clock put back before the answer: not this moment's either.
        check(url, recent = true, at = now - 1)
        assertEquals(4, requests.get())
    }

    @Test
    fun `another list or another installed version is not the same answer`() {
        val url = serve()
        check(url, recent = false)
        check(url, recent = true, version = "3.2")
        assertEquals("another installed version asks", 2, requests.get())
        val other = url.replace("/releases", "/other/releases")
        server!!.createContext(other.substringAfter(server!!.address.port.toString())) { exchange ->
            requests.incrementAndGet()
            exchange.sendResponseHeaders(200, 2)
            exchange.responseBody.use { it.write("[]".toByteArray()) }
        }
        // The same installed version as the last answer (3.2), so only the list differs (v5.15D: with 3.1 it differed twice).
        check(other, recent = true, version = "3.2")
        assertEquals("another list asks", 3, requests.get())
    }

    @Test
    fun `a check the limit kept from asking is no answer for the next`() {
        val url = serve()
        val limited = object : UpdateReplyStore {
            override suspend fun readReply(): SavedUpdateReply? = null
            override suspend fun writeReply(reply: SavedUpdateReply) {}
            override suspend fun readLimitedUntil() = now + 60_000
        }
        runBlocking { UpdateChecker.checkForUpdate("3.1", limited, apiUrl = url, takeRecentAnswer = true, nowMillis = { now }) }
        assertEquals("inside the limit, no request", 0, requests.get())
        // The next check of the moment, without the limit, asks: the one before made none to take.
        assertEquals(UpdateCheckResult.UpToDate, check(url, recent = true, at = now + 1))
        assertEquals(1, requests.get())
    }
}
