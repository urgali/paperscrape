package com.paperscrape.livewallpaper.prefs

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ARC-09: a preference write is not abandoned because the screen went away.
 *
 * Every write is launched from `rememberCoroutineScope()`, whose lifetime is the composition. An
 * Activity recreation -- rotation, a light/dark switch, a font-size change -- cancels that scope, so
 * a tap landing in the same frame had its write cancelled on the way to disk. Nothing is corrupted,
 * because DataStore's own write is transactional; the switch just bounces back, which reads as the
 * app ignoring the user.
 *
 * The fix is one helper rather than eighty-eight call sites, and this is the test that says so: the
 * coupling is "no write bypasses it", which is a property of the file rather than of any one write.
 */
class DurablePrefsWriteTest {

    private val source: String by lazy { prefsSource().readText() }

    @Test
    fun `no preference write goes straight to DataStore edit`() {
        val direct = Regex("""dataStore\.edit\b""").findAll(source).count()
        assertEquals(
            "a write is bypassing editDurably, so a rotation can still cancel it",
            0,
            direct,
        )
    }

    @Test
    fun `the durable helper is the one that reaches DataStore, and it is uncancellable`() {
        val start = source.indexOf("private suspend fun DataStore<Preferences>.editDurably(")
        assertTrue("editDurably must exist", start > 0)
        val body = source.substring(start, source.indexOf("\n\n", start))
        assertTrue("it must use NonCancellable:\n" + body, body.contains("NonCancellable"))
        assertTrue("and it must actually edit:\n" + body, body.contains("edit(transform)"))
    }

    @Test
    fun `there are still plenty of writes going through it`() {
        // Guards against the first test passing because somebody deleted the writes.
        assertTrue(
            "expected many editDurably call sites",
            Regex("""editDurably""").findAll(source).count() > 50,
        )
    }

    private fun prefsSource(): File {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/prefs/WallpaperPrefs.kt"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, prefix + suffix)
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate " + suffix)
    }
}
