package com.paperscrape.livewallpaper.update

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The download half of the updater, exercised against a real HTTP server on localhost.
 *
 * Everything here used to be untestable: `downloadAndVerify` took a `Context` purely to decide
 * where the file goes, which pulled the whole thing onto a device. `downloadAndVerifyTo` takes a
 * `File`, so the parts that actually fail -- an error response, a truncated body, a checksum that
 * does not match, a server that sends no `Content-Length`, cancellation -- are ordinary JVM tests.
 *
 * A real socket rather than a mocked `HttpURLConnection` is deliberate: the bug this suite was
 * written alongside lived in how the transfer was *driven*, not in how it was mocked, and a fake
 * that returns bytes on demand cannot express "the server stopped early" or "the caller was
 * cancelled mid-stream".
 */
class ApkDownloadPathTest {

    private lateinit var server: HttpServer
    private lateinit var tempDir: File
    private var port = 0

    /** Body handed out for `/apk`; set per test. */
    private var apkBody = ByteArray(0)

    /** Body handed out for `/checksum`; set per test. */
    private var checksumBody = ""

    private var apkStatus = 200
    private var checksumStatus = 200

    /** When true, `/apk` promises `apkBody.size` bytes and then sends only half of them. */
    private var truncate = false

    /** When true, `/apk` answers with chunked encoding, so there is no `Content-Length`. */
    private var withoutContentLength = false

    /** When > 0, `/apk` sleeps this many ms between 1 KB slices. */
    private var slicePauseMs = 0L

    private val served = AtomicBoolean(false)

    @Before
    fun start() {
        tempDir = Files.createTempDirectory("apk-download-test").toFile()
        server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        port = server.address.port
        server.createContext("/checksum") { exchange -> respondChecksum(exchange) }
        server.createContext("/apk") { exchange -> respondApk(exchange) }
        server.executor = null
        server.start()
    }

    @After
    fun stop() {
        server.stop(0)
        tempDir.deleteRecursively()
    }

    private fun respondChecksum(exchange: HttpExchange) {
        val bytes = checksumBody.toByteArray()
        if (checksumStatus != 200) {
            exchange.sendResponseHeaders(checksumStatus, -1)
            exchange.close()
            return
        }
        exchange.sendResponseHeaders(200, bytes.size.toLong())
        exchange.responseBody.use { it.write(bytes) }
    }

    private fun respondApk(exchange: HttpExchange) {
        served.set(true)
        if (apkStatus != 200) {
            exchange.sendResponseHeaders(apkStatus, -1)
            exchange.close()
            return
        }
        // 0 means "chunked, length unknown"; a positive value is a promise the client will check.
        val declared = if (withoutContentLength) 0L else apkBody.size.toLong()
        exchange.sendResponseHeaders(200, declared)
        val toSend = if (truncate) apkBody.size / 2 else apkBody.size
        try {
            exchange.responseBody.use { out ->
                var offset = 0
                while (offset < toSend) {
                    val slice = minOf(1024, toSend - offset)
                    out.write(apkBody, offset, slice)
                    out.flush()
                    offset += slice
                    if (slicePauseMs > 0) Thread.sleep(slicePauseMs)
                }
            }
        } catch (_: Exception) {
            // The client hung up (truncation or cancellation). Nothing to do.
        }
    }

    private fun apkUrl() = "http://127.0.0.1:$port/apk"
    private fun checksumUrl() = "http://127.0.0.1:$port/checksum"
    private fun target() = File(tempDir, "PaperScrape-v9.9.apk")

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun givenApk(sizeBytes: Int = 40_000): ByteArray {
        val body = ByteArray(sizeBytes) { (it % 251).toByte() }
        apkBody = body
        checksumBody = "${sha256(body)}  PaperScrape-v9.9.apk\n"
        return body
    }

    private fun download(phases: MutableList<DownloadPhase> = mutableListOf()): UpdateDownloadResult =
        runBlocking {
            ApkDownloader.downloadAndVerifyTo(apkUrl(), checksumUrl(), target()) { phases.add(it) }
        }

    @Test
    fun `a good download verifies against the published checksum`() {
        val body = givenApk()
        val result = download()
        assertTrue("expected Verified, got $result", result is UpdateDownloadResult.Verified)
        assertArrayEquals(body, target().readBytes())
    }

    @Test
    fun `verifying is reported after the last byte, exactly once, and last`() {
        givenApk()
        val phases = mutableListOf<DownloadPhase>()
        download(phases)

        assertEquals(
            "Verifying must be the final phase, so the UI stops saying 'Downloading'",
            DownloadPhase.Verifying,
            phases.last(),
        )
        assertEquals(
            "Verifying must be reported once, not once per read",
            1,
            phases.count { it is DownloadPhase.Verifying },
        )
        assertTrue("some download progress should be reported first", phases.size > 1)
        assertTrue(
            "no progress may be reported after verification starts",
            phases.dropLast(1).all { it is DownloadPhase.Downloading },
        )
    }

    @Test
    fun `progress climbs to 100 when the server declares a size`() {
        givenApk()
        val phases = mutableListOf<DownloadPhase>()
        download(phases)
        val percents = phases.filterIsInstance<DownloadPhase.Downloading>().map { it.percent }
        assertEquals("progress should end at 100%", 100, percents.last())
        assertEquals("progress must never go backwards", percents.sorted(), percents)
    }

    @Test
    fun `a server that declares no size still verifies, reporting unknown progress`() {
        givenApk()
        withoutContentLength = true
        val phases = mutableListOf<DownloadPhase>()
        val result = download(phases)
        assertTrue("expected Verified, got $result", result is UpdateDownloadResult.Verified)
        assertTrue(
            "unknown size is reported as -1, not as a fake percentage",
            phases.filterIsInstance<DownloadPhase.Downloading>().all { it.percent == -1 },
        )
    }

    /**
     * A server that promises N bytes and sends N/2.
     *
     * What this pins is the *outcome*: `Failed` rather than `ChecksumMismatch`, and no partial file
     * left behind. It does **not** prove the explicit `downloaded != total` guard runs -- a
     * mutation that removes that guard leaves this test green, because `HttpURLConnection` detects
     * the premature end of a fixed-length body and throws first. The guard is kept as the backstop
     * for a transport that ends a body quietly, and is honestly unproven here.
     */
    @Test
    fun `a truncated body fails rather than reporting a corrupt release`() {
        givenApk()
        truncate = true
        val result = download()
        assertEquals(
            "a short transfer is a failed download, not a checksum mismatch",
            UpdateDownloadResult.Failed,
            result,
        )
        assertFalse("a partial file must not survive", target().exists())
    }

    @Test
    fun `an error response on the APK fails without leaving a file`() {
        givenApk()
        apkStatus = 500
        assertEquals(UpdateDownloadResult.Failed, download())
        assertFalse(target().exists())
    }

    @Test
    fun `an error response on the checksum fails before the APK is fetched`() {
        givenApk()
        checksumStatus = 404
        assertEquals(UpdateDownloadResult.Failed, download())
        assertFalse("the APK must not be downloaded when its checksum is unreachable", served.get())
    }

    @Test
    fun `an unreadable checksum file is treated as no checksum at all`() {
        givenApk()
        checksumBody = "this is not a hash\n"
        assertEquals(UpdateDownloadResult.NoChecksumAsset, download())
    }

    @Test
    fun `bytes that do not match the published hash are rejected and deleted`() {
        givenApk()
        checksumBody = "${"0".repeat(64)}  PaperScrape-v9.9.apk\n"
        val result = download()
        assertTrue("expected ChecksumMismatch, got $result", result is UpdateDownloadResult.ChecksumMismatch)
        assertFalse("a file that failed verification must not stay on disk", target().exists())
    }

    @Test
    fun `an unreachable host fails instead of hanging`() {
        givenApk()
        val result = runBlocking {
            // Port 1 on loopback: refused immediately, so this asserts the failure path rather
            // than waiting out a timeout.
            ApkDownloader.downloadAndVerifyTo("http://127.0.0.1:1/apk", checksumUrl(), target()) {}
        }
        assertEquals(UpdateDownloadResult.Failed, result)
    }

    /**
     * Cancellation must not look like failure.
     *
     * Returning `Failed` here would have the screen say the download failed when the user simply
     * left it, and -- worse for the bug this suite exists for -- swallowing the
     * `CancellationException` would let a caller believe the flow ended normally.
     */
    @Test
    fun `cancelling mid-download throws CancellationException and leaves no partial file`() {
        givenApk(sizeBytes = 400_000)
        slicePauseMs = 5

        var thrown: Throwable? = null
        runBlocking {
            val job: Job = launch {
                try {
                    ApkDownloader.downloadAndVerifyTo(apkUrl(), checksumUrl(), target()) {}
                } catch (t: Throwable) {
                    thrown = t
                }
            }
            delay(200)
            job.cancelAndJoin()
        }

        assertTrue(
            "cancellation must propagate, not be reported as a failed download (got $thrown)",
            thrown is CancellationException,
        )
        assertFalse("a cancelled download must not leave a partial file", target().exists())
    }

    private fun assertArrayEquals(expected: ByteArray, actual: ByteArray) {
        assertEquals("downloaded size", expected.size, actual.size)
        assertTrue("downloaded bytes differ from what the server sent", expected.contentEquals(actual))
    }
}

private suspend fun Job.cancelAndJoin() {
    cancel()
    join()
}
