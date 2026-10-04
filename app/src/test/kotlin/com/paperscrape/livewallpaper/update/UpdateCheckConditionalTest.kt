package com.paperscrape.livewallpaper.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetSocketAddress
import java.util.concurrent.CopyOnWriteArrayList

/**
 * **"Has anything changed?"** -- the update check asks GitHub with the `ETag` of its last answer, and
 * while nothing has changed it downloads nothing (v5.10D, the maintainer's *sì* of 2026-09-30 to row 16
 * of the v5.10A table, inventory I-244).
 *
 * Until v5.10D every check downloaded the whole first page of the release list -- 252 KB to read,
 * ~51 KB on the wire, measured 2026-10-03 -- and the engine checks every three hours since the same
 * round. GitHub's real reply to a conditional request is a 304 with no body
 * (`consegna_v5_10d/registri/rete/`); these tests stand a loopback server that keeps the same
 * contract, so what is asserted is the bytes the client asks for and what it concludes, not a mock of
 * the checker.
 */
class UpdateCheckConditionalTest {

    private var server: HttpServer? = null

    /** What each request carried as `If-None-Match` (null: none), and how many body bytes it got. */
    private val conditions = CopyOnWriteArrayList<String?>()
    private val bodyBytes = CopyOnWriteArrayList<Int>()

    @After
    fun stop() {
        server?.stop(0)
        server = null
    }

    /**
     * A server holding one page of releases under [etag], answering 304 with no body when the request
     * carries that tag -- GitHub's contract. [releases] can be swapped between requests.
     */
    private inner class Releases(var etag: String, var body: String) {
        val url: String

        init {
            val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
            http.createContext("/releases") { exchange ->
                val sent = exchange.requestHeaders.getFirst("If-None-Match")
                conditions += sent
                // Recorded before the reply goes out: the client may read it and the test assert on
                // these lists before this thread runs another line (a mutation run caught the race).
                if (sent == etag) {
                    bodyBytes += 0
                    exchange.responseHeaders.add("ETag", etag)
                    exchange.sendResponseHeaders(304, -1)
                    exchange.close()
                } else {
                    val bytes = body.toByteArray()
                    bodyBytes += bytes.size
                    exchange.responseHeaders.add("ETag", etag)
                    exchange.sendResponseHeaders(200, bytes.size.toLong())
                    exchange.responseBody.use { it.write(bytes) }
                }
            }
            http.start()
            server = http
            url = "http://127.0.0.1:${http.address.port}/releases"
        }
    }

    /** The store the app keeps in `UpdatePrefs`, kept in a field. */
    private class MemoryStore : UpdateReplyStore {
        var reply: SavedUpdateReply? = null
        var writes = 0
        override suspend fun readReply(): SavedUpdateReply? = reply
        override suspend fun writeReply(reply: SavedUpdateReply) {
            this.reply = reply
            writes++
        }
    }

    private fun release(tag: String) = """
        {"tag_name": "$tag", "html_url": "https://github.com/urgali/paperscrape/releases/tag/$tag",
         "body": "what changed in $tag",
         "assets": [
           {"name": "PaperScrape-$tag.apk", "browser_download_url": "https://github.com/urgali/paperscrape/releases/download/$tag/PaperScrape-$tag.apk", "size": 19000000},
           {"name": "PaperScrape-$tag.apk.sha256", "browser_download_url": "https://github.com/urgali/paperscrape/releases/download/$tag/PaperScrape-$tag.apk.sha256", "size": 96}
         ]}
    """.trimIndent()

    private fun check(url: String, store: UpdateReplyStore?, version: String = "5.9") =
        runBlocking { UpdateChecker.checkForUpdate(version, store, url) }

    @Test
    fun `the second check asks with the first answer's ETag, and an unchanged list downloads nothing`() {
        val github = Releases(etag = "W/\"abc\"", body = "[${release("v5.9")}, ${release("v5.8")}]")
        val store = MemoryStore()

        assertEquals(UpdateCheckResult.UpToDate, check(github.url, store))
        assertEquals(UpdateCheckResult.UpToDate, check(github.url, store))

        assertEquals("the first asks without a condition, the second with the tag", listOf(null, "W/\"abc\""), conditions.toList())
        assertTrue("the first downloads the list", bodyBytes[0] > 0)
        assertEquals("the second downloads nothing", 0, bodyBytes[1])
    }

    @Test
    fun `an update found once is still offered whole when GitHub says nothing changed`() {
        val github = Releases(etag = "W/\"v59\"", body = "[${release("v5.9")}, ${release("v5.8")}, ${release("v5.7")}]")
        val store = MemoryStore()

        val first = check(github.url, store, version = "5.7") as UpdateCheckResult.Available
        val second = check(github.url, store, version = "5.7")

        assertEquals("the kept answer is the same answer", first, second)
        assertEquals(0, bodyBytes[1])
        val info = (second as UpdateCheckResult.Available).info
        assertTrue("its notes survive the round trip", info.releaseNotes!!.contains("what changed in v5.8"))
        assertTrue("and it still installs in-app", info.isInstallable)
    }

    @Test
    fun `a changed list is downloaded again and its answer replaces the kept one`() {
        val github = Releases(etag = "W/\"one\"", body = "[${release("v5.9")}]")
        val store = MemoryStore()
        assertEquals(UpdateCheckResult.UpToDate, check(github.url, store))

        github.etag = "W/\"two\""
        github.body = "[${release("v5.10")}, ${release("v5.9")}]"
        val result = check(github.url, store)

        assertEquals("v5.10", (result as UpdateCheckResult.Available).info.tagName)
        assertEquals("asked with the old tag", "W/\"one\"", conditions[1])
        assertTrue("and got the new list", bodyBytes[1] > 0)
        assertEquals("the new tag is kept for next time", "W/\"two\"", store.reply?.etag)
    }

    @Test
    fun `an answer is kept only for the version it was worked out for`() {
        val github = Releases(etag = "W/\"same\"", body = "[${release("v5.9")}, ${release("v5.8")}]")
        val store = MemoryStore()
        assertTrue(check(github.url, store, version = "5.8") is UpdateCheckResult.Available)

        // 5.9 installed: "an update to 5.9" is no longer true, whatever the list says.
        assertEquals(UpdateCheckResult.UpToDate, check(github.url, store, version = "5.9"))
        assertNull("no condition sent for an answer about another version", conditions[1])
        assertEquals("5.9", store.reply?.forVersionName)
    }

    @Test
    fun `a failure keeps nothing and is still reported as a failure`() {
        val store = MemoryStore()
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/releases") { exchange ->
            exchange.sendResponseHeaders(403, -1)
            exchange.close()
        }
        http.start()
        server = http
        val result = check("http://127.0.0.1:${http.address.port}/releases", store)
        assertEquals(UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR), result)
        assertEquals(0, store.writes)
    }

    @Test
    fun `a failure on an older page keeps nothing, and is a failure rather than half the notes`() {
        val store = MemoryStore()
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/releases") { exchange ->
            val page2 = exchange.requestURI.query?.contains("page=2") == true
            if (page2) {
                exchange.sendResponseHeaders(502, -1)
                exchange.close()
            } else {
                val port = http.address.port
                val bytes = "[${release("v5.9")}, ${release("v5.8")}]".toByteArray()
                exchange.responseHeaders.add("ETag", "W/\"p1\"")
                exchange.responseHeaders.add("Link", "<http://127.0.0.1:$port/releases?page=2>; rel=\"next\"")
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.use { it.write(bytes) }
            }
        }
        http.start()
        server = http
        // Installed 5.1: not on the first page, so the second is needed for the whole notes.
        val result = check("http://127.0.0.1:${http.address.port}/releases", store, version = "5.1")
        assertEquals(UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR), result)
        assertEquals("nothing kept: the next check reads the pages again", 0, store.writes)
    }

    @Test
    fun `an answer kept for another address is not used`() {
        val github = Releases(etag = "W/\"x\"", body = "[${release("v5.9")}]")
        val store = MemoryStore()
        store.reply = SavedUpdateReply("W/\"x\"", "5.9", "https://api.github.com/repos/someone/else/releases", UpdateCheckResult.UpToDate)
        assertEquals(UpdateCheckResult.UpToDate, check(github.url, store))
        assertNull("no condition sent with another address's tag", conditions[0])
        assertTrue("so the list was read", bodyBytes[0] > 0)
    }

    @Test
    fun `a 304 with no answer kept to back it is not read as up to date`() {
        // A reply this client cannot have asked for: a 304 to a request with no condition.
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/releases") { exchange ->
            exchange.sendResponseHeaders(304, -1)
            exchange.close()
        }
        http.start()
        server = http
        assertEquals(
            UpdateCheckResult.Unreachable(UpdateCheckResult.Unreachable.Reason.SERVER_ERROR),
            check("http://127.0.0.1:${http.address.port}/releases", MemoryStore()),
        )
    }

    @Test
    fun `the kept reply survives its own serialisation, and nonsense reads as nothing kept`() {
        val info = UpdateInfo(
            tagName = "v5.10",
            version = AppVersion(5, 10),
            releasePageUrl = "https://github.com/urgali/paperscrape/releases/tag/v5.10",
            releaseNotes = "v5.10\nline one\n\nv5.9\nline two \"quoted\"",
            apkAsset = ReleaseAsset("PaperScrape-v5.10.apk", "https://github.com/a.apk", 3_000_000),
            checksumAsset = ReleaseAsset("PaperScrape-v5.10.apk.sha256", "https://github.com/a.sha256", 87),
        )
        for (result in listOf(UpdateCheckResult.UpToDate, UpdateCheckResult.Available(info))) {
            val reply = SavedUpdateReply("W/\"t\"", "5.9", "https://api.github.com/repos/urgali/paperscrape/releases", result)
            assertEquals(reply, SavedUpdateReply.fromJson(reply.toJson()))
        }
        assertNull(SavedUpdateReply.fromJson(null))
        assertNull(SavedUpdateReply.fromJson("not json"))
        assertNull(SavedUpdateReply.fromJson("{}"))
        assertNull("an unanswered check is never kept", SavedUpdateReply.fromJson("""{"etag":"x","forVersionName":"5.9","apiUrl":"u"}"""))
    }

    @Test
    fun `the three callers in the app pass the store`() {
        val engine = source("engine/PaperWallpaperService.kt")
        val home = source("ui/SettingsScreen.kt")
        val advanced = source("ui/AdvancedScreen.kt")
        assertNotNull(Regex("""UpdateChecker\.checkForUpdate\(BuildConfig\.VERSION_NAME, updatePrefs\)""").find(engine))
        assertNotNull(Regex("""UpdateChecker\.checkForUpdate\(BuildConfig\.VERSION_NAME, updatePrefs\)""").find(home))
        assertNotNull(Regex("""UpdateChecker\.checkForUpdate\(BuildConfig\.VERSION_NAME, store\)""").find(advanced))
        assertTrue("UpdatePrefs is the store", source("update/UpdatePrefs.kt").contains("class UpdatePrefs(private val context: Context) : UpdateReplyStore"))
        for (text in listOf(engine, home, advanced)) {
            assertEquals("no caller checks without the store", 0, Regex("""checkForUpdate\(BuildConfig\.VERSION_NAME\)""").findAll(text).count())
        }
    }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
