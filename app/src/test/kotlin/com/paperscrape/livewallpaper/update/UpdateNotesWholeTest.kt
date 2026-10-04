package com.paperscrape.livewallpaper.update

import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger

/**
 * **Every release between the installed one and the newest, whole** -- the maintainer's words of
 * 2026-09-30 on row 16: *«deve scaricare tutto il changelog di diff tra la versione installata e
 * l'ultima disponibile come fix, non solo l'ultima»* (inventory I-290).
 *
 * Until v5.10D the combined notes were cut at 6 000 characters, and `release-notes/v5.8.md` alone is
 * 7 186: a user on 5.7 read 5.8's notes cut in the middle and never saw where they ended. And only the
 * list's first page was read -- GitHub gives 30 releases a page, 69 releases were three pages on
 * 2026-10-03 -- so a user more than 29 releases behind never saw the oldest of theirs.
 *
 * The bodies here are the repository's own `release-notes/vMAJOR.MINOR.md` files (declared as an input of the unit
 * tests in `app/build.gradle.kts`, so a change to them runs this again) followed by a footer of the workflow's
 * shape -- a `---` rule and one line about the APK's checksum -- whose wording is this test's, not the workflow's.
 */
class UpdateNotesWholeTest {

    private var server: HttpServer? = null

    @After
    fun stop() {
        server?.stop(0)
        server = null
    }

    private fun notes(tag: String): String = repoFile("release-notes/$tag.md").readText().trim()

    /** A release body shaped as GitHub returns it: the note, then a stand-in for the verification footer. */
    private fun body(tag: String, note: String = notes(tag)) =
        "$note\n\n---\nSHA-256 of PaperScrape-$tag.apk: see PaperScrape-$tag.apk.sha256 attached below."

    private fun release(tag: String, body: String) = JSONObject()
        .put("tag_name", tag)
        .put("html_url", "https://github.com/urgali/paperscrape/releases/tag/$tag")
        .put("body", body)
        .put("assets", JSONArray())

    /** Serves [pages] in order, each with a `Link` to the next as GitHub does; counts requests. */
    private fun serve(pages: List<List<JSONObject>>, requests: AtomicInteger = AtomicInteger()): String {
        val http = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        http.createContext("/releases") { exchange ->
            requests.incrementAndGet()
            val page = exchange.requestURI.query?.substringAfter("page=")?.toIntOrNull() ?: 1
            val bytes = JSONArray(pages[page - 1]).toString().toByteArray()
            if (page < pages.size) {
                val port = http.address.port
                exchange.responseHeaders.add(
                    "Link",
                    "<http://127.0.0.1:$port/releases?page=${page + 1}>; rel=\"next\", <http://127.0.0.1:$port/releases?page=${pages.size}>; rel=\"last\"",
                )
            }
            exchange.responseHeaders.add("ETag", "W/\"p$page\"")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        http.start()
        server = http
        return "http://127.0.0.1:${http.address.port}/releases"
    }

    private fun notesFor(installed: String, url: String): String =
        ((runBlocking { UpdateChecker.checkForUpdate(installed, null, url) }) as UpdateCheckResult.Available).info.releaseNotes!!

    @Test
    fun `from 5_7 the notes of 5_9 and 5_8 come whole, newest first`() {
        val v58 = notes("v5.8")
        assertEquals("the note that was cut: 7 186 characters on its own", 7186, repoFile("release-notes/v5.8.md").readText().length)
        val url = serve(listOf(listOf(release("v5.9", body("v5.9")), release("v5.8", body("v5.8")), release("v5.7", body("v5.7")))))

        val shown = notesFor("5.7", url)

        assertTrue("5.9's note, whole", shown.contains(body("v5.9")))
        assertTrue("5.8's note, whole -- it ended past 6 000 characters", shown.contains(body("v5.8")))
        assertTrue("newest first", shown.indexOf("v5.9\n") < shown.indexOf("v5.8\n"))
        assertFalse("not the user's own release", shown.contains(body("v5.7")))
        assertTrue("longer than the old cut", shown.length > 6000)
        assertTrue("and the end of 5.8's note is there", shown.contains(v58.takeLast(200)))
    }

    @Test
    fun `from 5_8 only 5_9's note comes, whole`() {
        val url = serve(listOf(listOf(release("v5.9", body("v5.9")), release("v5.8", body("v5.8")))))
        assertEquals("v5.9\n" + body("v5.9"), notesFor("5.8", url))
    }

    @Test
    fun `a user many releases behind gets every release's note, across pages`() {
        // 69 releases, newest first, thirty a page, as GitHub listed them on 2026-10-03.
        val tags = (9 downTo 0).map { "v5.$it" } + (31 downTo 0).map { "v4.$it" } + (26 downTo 0).map { "v3.$it" }
        assertEquals(69, tags.size)
        val releases = tags.map { release(it, "what changed in $it, " + "x".repeat(400)) }
        val pages = releases.chunked(30)
        assertEquals(3, pages.size)
        val requests = AtomicInteger()
        val url = serve(pages, requests)

        val oldest = tags.last()
        val installed = oldest.removePrefix("v")
        val shown = notesFor(installed, url)

        for (tag in tags.dropLast(1)) assertTrue("$tag's note is missing", shown.contains("what changed in $tag,"))
        assertFalse(shown.contains("what changed in $oldest,"))
        assertEquals("all three pages read", 3, requests.get())
    }

    @Test
    fun `older pages are read only until the installed version appears`() {
        val tags = (40 downTo 0).map { "v4.$it" }
        val pages = tags.map { release(it, "notes $it") }.chunked(30)
        val requests = AtomicInteger()
        val url = serve(pages, requests)

        val shown = notesFor("4.20", url)

        assertEquals("4.20 is on the first page: the second is not asked for", 1, requests.get())
        assertTrue(shown.contains("notes v4.21") && shown.contains("notes v4.40"))
        assertFalse(shown.contains("notes v4.20"))
    }

    @Test
    fun `the pages read are bounded`() {
        // A reply that names a next page for ever stops at MAX_PAGES, and answers from what it read.
        val requests = AtomicInteger()
        val pages = (0 until 50).map { listOf(release("v9.${100 - it}", "notes $it")) }
        val url = serve(pages, requests)
        notesFor("1.0", url)
        assertEquals(UpdateChecker.MAX_PAGES, requests.get())
    }

    @Test
    fun `the Link header's next page is read, and the last page has none`() {
        assertEquals(
            "https://api.github.com/repositories/1339749702/releases?page=2",
            UpdateChecker.nextPageUrl(
                "<https://api.github.com/repositories/1339749702/releases?page=2>; rel=\"next\", " +
                    "<https://api.github.com/repositories/1339749702/releases?page=3>; rel=\"last\"",
            ),
        )
        assertEquals(
            null,
            UpdateChecker.nextPageUrl(
                "<https://api.github.com/repositories/1339749702/releases?page=1>; rel=\"prev\", " +
                    "<https://api.github.com/repositories/1339749702/releases?page=1>; rel=\"first\"",
            ),
        )
        assertEquals(null, UpdateChecker.nextPageUrl(null))
    }

    @Test
    fun `nothing in the update dialog cuts the notes, and it scrolls`() {
        val checker = source("update/UpdateChecker.kt")
        assertFalse("the old cap", checker.contains("MAX_COMBINED_NOTES_CHARS"))
        assertFalse("no .take( on the notes", Regex("""joinToString\("\\n\\n"\)\s*\n\s*\.take\(""").containsMatchIn(checker))
        val dialog = source("ui/SettingsScreen.kt")
        val notesAt = dialog.indexOf("val notes = update.releaseNotes")
        assertTrue(notesAt > 0)
        val block = dialog.substring(notesAt, notesAt + 1400)
        // v5.10D: one block per release in a lazy list, so 211 558 characters do not freeze the phone.
        assertTrue("the blocks are the notes, cut by release", block.contains("UpdateChecker.notesByRelease(notes)"))
        assertTrue("in a scrolling lazy list of bounded height", block.contains("LazyColumn(modifier = Modifier.heightIn(max = 340.dp))"))
        assertTrue("each shown as it is", block.contains("Text(block, style = MaterialTheme.typography.bodyMedium"))
    }

    @Test
    fun `the dialog's blocks are the releases, and together they are the notes whole`() {
        val tags = (9 downTo 0).map { "v5.$it" } + (31 downTo 0).map { "v4.$it" }
        val releases = tags.map { release(it, body(it, note = "## $it\n\nFirst paragraph.\n\nv9.9 is not a header here, it is mid-line.\n\n- a list")) }
        val url = serve(releases.chunked(30))
        val notes = notesFor("4.0", url)
        val blocks = UpdateChecker.notesByRelease(notes)
        assertEquals("one block per release newer than 4.0", tags.size - 1, blocks.size)
        assertEquals("lossless", notes, blocks.joinToString("\n\n"))
        for ((i, b) in blocks.withIndex()) assertTrue("block $i starts at its tag", b.startsWith(tags[i] + "\n"))
        // The real notes from 5.7 are two blocks, 5.9's and 5.8's, each whole.
        val real = UpdateChecker.notesByRelease("v5.9\n" + body("v5.9") + "\n\nv5.8\n" + body("v5.8"))
        assertEquals(listOf("v5.9\n" + body("v5.9"), "v5.8\n" + body("v5.8")), real)
    }

    private fun repoFile(path: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val f = File(dir, path)
            if (f.isFile) return f
            dir = dir.parentFile
        }
        error("not found: $path")
    }

    private fun source(path: String): String =
        repoFile("app/src/main/kotlin/com/paperscrape/livewallpaper/$path").takeIf { it.isFile }?.readText()
            ?: error("source not found: $path")
}
