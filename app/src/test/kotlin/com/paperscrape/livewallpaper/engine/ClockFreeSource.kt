package com.paperscrape.livewallpaper.engine

import java.io.File

/**
 * Whether a source file can read a clock: what a "does not depend on the clock" test has to look at.
 *
 * Three tests used to prove it by calling the rule, sleeping 5 ms, calling it again and comparing.
 * A rule that read the clock through anything coarser than 5 ms -- the second, the minute, the
 * day, which is how a scene rule would read it -- gives the same answer twice and passes. The
 * clock is an input the rule cannot have if its file never names one, and that the file's text can
 * show; so the three tests now read it (comments stripped, so a KDoc explaining why the rule is
 * clock-free does not count against it).
 */
internal object ClockFreeSource {

    private val CLOCK = Regex(
        """currentTimeMillis|nanoTime|elapsedRealtime|uptimeMillis|SystemClock|\bCalendar\b|""" +
            """\bInstant\b|\bLocalDate|\bLocalTime|\bZonedDateTime\b|\bClock\.|java\.util\.Date""",
    )

    /** Every clock the file at [path] (under `livewallpaper/`) names outside its comments. */
    fun clockReadsIn(path: String): List<String> {
        val code = source(path)
            .replace(Regex("""/\*.*?\*/""", RegexOption.DOT_MATCHES_ALL), " ")
            .replace(Regex("""//[^\n]*"""), " ")
        return CLOCK.findAll(code).map { it.value }.toList()
    }

    private fun source(path: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/$path")
                if (candidate.isFile) return candidate.readText()
            }
            dir = dir.parentFile
        }
        error("could not locate $path")
    }
}
