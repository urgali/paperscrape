package com.paperscrape.livewallpaper.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.net.ServerSocket

/**
 * What "Check for updates" is allowed to tell the user.
 *
 * The v3.0 checker returned a nullable, so every failure -- aeroplane mode, DNS, a timeout, a 403,
 * a body that was not JSON -- arrived at the settings screen as the same `null` that means "nothing
 * newer exists", and the screen rendered it as **"You're up to date (v3.0)"**. That was verified on
 * a device with the radios off: the app made a claim it had no information to support.
 *
 * These run against a real `HttpServer` on a loopback port rather than a stub of the checker,
 * because the thing being pinned is which *exception* becomes which outcome, and a stub of
 * `checkForUpdate` would simply assert the mapping back at itself.
 */
class UpdateCheckOutcomeTest {

    private var server: HttpServer? = null

    @After
    fun stop() {
        server?.stop(0)
        server = null
    }

    /** Starts a server that answers every request with [status] and [body], and returns its URL. */
    private fun serve(status: Int, body: String): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/releases") { exchange ->
            val bytes = body.toByteArray()
            exchange.sendResponseHeaders(status, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}/releases"
    }

    /** A port with nothing listening on it: the connection is refused, exactly as offline. */
    private fun deadUrl(): String {
        val socket = ServerSocket(0)
        val port = socket.localPort
        socket.close()
        return "http://127.0.0.1:$port/releases"
    }

    private fun release(tag: String, apk: Boolean = true) = """
        {
          "tag_name": "$tag",
          "html_url": "https://github.com/urgali/paperscrape/releases/tag/$tag",
          "body": "what changed in $tag",
          "assets": ${if (apk) assets(tag) else "[]"}
        }
    """.trimIndent()

    private fun assets(tag: String) = """
        [
          {"name": "PaperScrape-$tag.apk", "browser_download_url": "https://github.com/urgali/paperscrape/releases/download/$tag/PaperScrape-$tag.apk", "size": 19000000},
          {"name": "PaperScrape-$tag.apk.sha256", "browser_download_url": "https://github.com/urgali/paperscrape/releases/download/$tag/PaperScrape-$tag.apk.sha256", "size": 96}
        ]
    """.trimIndent()

    private fun check(url: String, version: String = "3.1") =
        runBlocking { UpdateChecker.checkForUpdate(version, url) }

    // -- The three outcomes ---------------------------------------------------------------------

    @Test
    fun `a newer release is offered`() {
        val result = check(serve(200, "[${release("v3.2")}, ${release("v3.1")}]"))

        val available = result as UpdateCheckResult.Available
        assertEquals("v3.2", available.info.tagName)
        assertEquals(AppVersion(3, 2), available.info.version)
        assertTrue("the release carries both files, so it installs in-app", available.info.isInstallable)
    }

    @Test
    fun `nothing newer is up to date`() {
        assertEquals(
            UpdateCheckResult.UpToDate,
            check(serve(200, "[${release("v3.1")}, ${release("v3.0")}]")),
        )
    }

    /** A repository with releases, none of them under the release scheme, is still "nothing newer". */
    @Test
    fun `no readable release is up to date rather than an error`() {
        assertEquals(UpdateCheckResult.UpToDate, check(serve(200, "[]")))
        assertEquals(
            UpdateCheckResult.UpToDate,
            check(serve(200, """[{"tag_name": "v73", "html_url": "https://github.com/urgali/paperscrape"}]""")),
        )
    }

    // -- The outcome that used to be a lie ------------------------------------------------------

    @Test
    fun `no connection is not up to date`() {
        val result = check(deadUrl())

        assertEquals(
            UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.NO_CONNECTION),
            result,
        )
    }

    @Test
    fun `an http error is not up to date`() {
        for (status in listOf(403, 404, 429, 500, 503)) {
            assertEquals(
                "HTTP $status must not be reported as up to date",
                UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR),
                check(serve(status, """{"message":"rate limit exceeded"}""")),
            )
            stop()
        }
    }

    @Test
    fun `a body that is not the expected json is not up to date`() {
        assertEquals(
            UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.UNREADABLE_RESPONSE),
            check(serve(200, "<html>GitHub is having a moment</html>")),
        )
    }

    @Test
    fun `an unreadable installed version cannot conclude anything`() {
        assertEquals(
            UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.UNREADABLE_RESPONSE),
            check(serve(200, "[${release("v3.2")}]"), version = "not-a-version"),
        )
    }

    // -- What the user is shown -----------------------------------------------------------------

    @Test
    fun `no failure message ever claims the app is current`() {
        for (reason in UpdateCheckResult.Unreachable.Reason.entries) {
            val message = UpdateCheckResult.Unreachable(reason).message
            assertTrue("$reason must say the check failed", message.startsWith("Couldn't check"))
            assertFalse("$reason must not claim currency", message.contains("up to date", ignoreCase = true))
        }
    }

    @Test
    fun `each reason says something different`() {
        val messages = UpdateCheckResult.Unreachable.Reason.entries.map {
            UpdateCheckResult.Unreachable(it).message
        }
        assertEquals("three reasons, three sentences", messages.size, messages.toSet().size)
    }
}
