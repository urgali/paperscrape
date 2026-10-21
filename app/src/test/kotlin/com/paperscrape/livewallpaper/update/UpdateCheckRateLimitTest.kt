package com.paperscrape.livewallpaper.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * **GitHub's refusal read as what it is, and nothing asked before it lifts** (v5.15, inventory I-909; the maintainer's
 * *«D sì, con la riga»* of 2026-10-19), and **one line in the log for every check** (I-910).
 *
 * Without credentials GitHub answers 60 requests an hour for the network's public address, which the phone shares with
 * every device behind it; it refuses the rest with 403 and `X-RateLimit-Remaining: 0` and `X-RateLimit-Reset`, or with
 * 429 or 403 and `Retry-After`. Until v5.15 every one of those read as "GitHub didn't answer", was asked again after 2, 4,
 * 8 minutes -- refused each time -- and the button said "Try again in a few minutes". Against a real `HttpServer` on a
 * loopback port, as `UpdateCheckOutcomeTest`, because what is pinned is which reply becomes which outcome, what the store
 * keeps, and how many requests reach the server.
 */
class UpdateCheckRateLimitTest {

    private var server: HttpServer? = null
    private val requests = AtomicInteger()
    private val log = mutableListOf<String>()
    private val savedSink = UpdateCheckLog.sink

    init {
        UpdateCheckLog.sink = { log += it }
    }

    @After
    fun stop() {
        server?.stop(0)
        server = null
        UpdateCheckLog.sink = savedSink
    }

    /** A server that answers [status] with [headers], and [body], and counts the requests. */
    private fun serve(status: Int, headers: Map<String, String>, body: String = """{"message":"API rate limit exceeded"}"""): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/releases") { exchange ->
            requests.incrementAndGet()
            for ((k, v) in headers) exchange.responseHeaders.add(k, v)
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}/releases"
    }

    private class Store : UpdateReplyStore {
        var reply: SavedUpdateReply? = null
        var limitedUntil = 0L
        override suspend fun readReply() = reply
        override suspend fun writeReply(reply: SavedUpdateReply) { this.reply = reply }
        override suspend fun readLimitedUntil() = limitedUntil
        override suspend fun writeLimitedUntil(untilMillis: Long) { limitedUntil = untilMillis }
    }

    private val now = 1_800_000_000_000L

    private fun check(url: String, store: UpdateReplyStore? = null, honourLimit: Boolean = true, at: Long = now) =
        runBlocking { UpdateChecker.checkForUpdate("3.1", store, apiUrl = url, honourLimit = honourLimit, nowMillis = { at }) }

    @Test
    fun `403 with no requests left is the limit, until the reset GitHub gives`() {
        val reset = now / 1000 + 1_500
        val result = check(serve(403, mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "$reset")))
        assertEquals(UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.RATE_LIMITED, reset * 1000), result)
    }

    @Test
    fun `403 with no requests left and no reset is the limit, with no moment, and the one run waits GitHub's hour`() {
        // GitHub sends the reset with every refusal; this is the case it did not (v5.15C2, inventory I-975).
        val result = check(serve(403, mapOf("X-RateLimit-Remaining" to "0")))
        assertEquals(UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.RATE_LIMITED, 0L), result)
        assertEquals(UpdateCheckSchedule.Next.AFTER_LIMIT, UpdateCheckSchedule.afterCheck(result))
        assertEquals(UpdateCheckSchedule.UNKNOWN_LIMIT_WAIT_MILLIS, UpdateCheckSchedule.afterLimitDelayMillis(0L, now))
    }

    @Test
    fun `Retry-After counts from now, on a 429 or a 403`() {
        for (status in listOf(429, 403)) {
            val result = check(serve(status, mapOf("Retry-After" to "60")))
            assertEquals("HTTP $status", UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.RATE_LIMITED, now + 60_000), result)
            stop()
            UpdateCheckLog.sink = { log += it }
        }
    }

    @Test
    fun `a 403 that is not the limit is not read as one`() {
        val reasons = listOf(
            mapOf<String, String>(),
            mapOf("X-RateLimit-Remaining" to "12", "X-RateLimit-Reset" to "${now / 1000 + 900}"),
        ).map { headers ->
            check(serve(403, headers)).also { stop(); UpdateCheckLog.sink = { log += it } }
        }
        for (r in reasons) assertEquals(UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR), r)
    }

    @Test
    fun `nothing is asked before the limit lifts, then the next check asks, and an answer clears it`() {
        val store = Store()
        val reset = now / 1000 + 600
        val refused = serve(403, mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "$reset"))
        check(refused, store)
        assertEquals(1, requests.get())
        assertEquals("the store keeps the moment", reset * 1000, store.limitedUntil)
        // Ten minutes not yet gone: the same refusal, and no request.
        val again = check(refused, store, at = now + 300_000)
        assertEquals(UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.RATE_LIMITED, reset * 1000), again)
        assertEquals("no request inside the limit", 1, requests.get())
        // The button the user has just pressed asks anyway.
        check(refused, store, honourLimit = false, at = now + 300_000)
        assertEquals(2, requests.get())
        stop()
        UpdateCheckLog.sink = { log += it }
        // Past the moment GitHub gave, the check asks, and GitHub's answer clears the limit.
        val answered = serve(200, emptyMap(), "[]")
        assertEquals(UpdateCheckResult.UpToDate, check(answered, store, at = reset * 1000 + 1))
        assertEquals(0L, store.limitedUntil)
    }

    @Test
    fun `the row says until when, in the time format it is handed`() {
        val result = UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.RATE_LIMITED, now)
        assertEquals(
            "Couldn't check - GitHub is turning away checks from your network for now. Try again after 13:26.",
            result.message { "13:26" },
        )
        assertEquals(
            "Couldn't check - GitHub is turning away checks from your network for now. Try again later.",
            UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.RATE_LIMITED).message { "13:26" },
        )
        assertTrue(UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR).message.contains("didn't answer"))
    }

    @Test
    fun `every check writes one line, with the outcome, the HTTP code and the requests left, and nothing else`() {
        log.clear()
        val url = serve(200, mapOf("X-RateLimit-Remaining" to "57"), "[]")
        check(url)
        assertEquals(listOf("update check: up to date, HTTP 200, 57 requests left"), log)
        stop()
        log.clear()
        UpdateCheckLog.sink = { log += it }
        check(serve(403, mapOf("X-RateLimit-Remaining" to "0", "X-RateLimit-Reset" to "${now / 1000}")))
        assertEquals(1, log.size)
        assertTrue(log[0], log[0].startsWith("update check: turned away by GitHub's limit until ") && log[0].endsWith(", HTTP 403, 0 requests left"))
        // No address, no version, no identifier: the line's three facts and its fixed words.
        assertNull(Regex("""127\.0\.0\.1|3\.1|http://|urgali""").find(log[0]))
    }

    @Test
    fun `a check not made because of the limit says so in the log`() {
        val store = Store().apply { limitedUntil = now + 60_000 }
        log.clear()
        check(serve(200, emptyMap(), "[]"), store)
        assertEquals(0, requests.get())
        assertEquals(1, log.size)
        assertTrue(log[0], log[0].startsWith("update check: not made, GitHub's limit until "))
    }
}
