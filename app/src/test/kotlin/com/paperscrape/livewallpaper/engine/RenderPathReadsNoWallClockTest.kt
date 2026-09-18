package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The two files that paint the scene may not ask what time it is.**
 *
 * `PaperRenderer` composes a frame and `SceneObjectRenderer` draws the objects in it; between them
 * they are the render path, and everything they need is handed to them --- the size, the theme, the
 * customisation, the scene clock, the day phase. Until v5.4G one line was not:
 * `drawMoonWithPhase` called `SunPositionCalculator.moonPhase()`, whose default argument is
 * `System.currentTimeMillis()`, and the moon in a golden frame was therefore chosen by the phone
 * rather than by the scene. The goldens `night` and `shops-closed-night` passed at 19:02 and
 * failed by 17 and 39 pixels at 22:25 on the same build, because the real moon had crossed from
 * crescent to half.
 *
 * ### Why this exists beside the behavioural check
 *
 * `SceneGolden.assertReproducesOverTime` is the check that actually catches a leak: it renders
 * every Canvas golden twice with the device clock 191 days apart and requires the frames to be
 * identical. It is strictly stronger than this test *for anything that reaches a pixel* --- and
 * strictly weaker for everything else. A clock read that only shows up in a theme, a weather or a
 * season no golden covers passes it and lands in the app; this one names the rule at the place the
 * rule is about, and fails the JVM suite in two seconds rather than the instrumented one in an
 * hour.
 *
 * Neither replaces the other, and neither is a grep for a bug: they are both a grep for the
 * *shape* --- a frame that is not a function of the scene it was handed.
 */
class RenderPathReadsNoWallClockTest {

    /**
     * The calls that ask the platform what time it is now.
     *
     * `System.nanoTime` is deliberately absent: it is a monotonic stopwatch, not a date, and the
     * two render loops legitimately use it to measure how long a frame took. It is also absent
     * from both files below, which is why leaving it out costs nothing here.
     */
    private val wallClockReads = listOf(
        "System.currentTimeMillis",
        "SystemClock.elapsedRealtime",
        "SystemClock.uptimeMillis",
        "LocalDate.now",
        "LocalDateTime.now",
        "ZonedDateTime.now",
        "Instant.now",
        "Calendar.getInstance",
        "SunPositionCalculator.moonPhase(",
        "SunPositionCalculator.currentHour24(",
    )

    /**
     * The files that draw. Named rather than derived, and short on purpose: this is the render
     * path, and if it grows a third file the person who adds it should have to add it here and say
     * so.
     */
    private val renderPath = listOf("PaperRenderer.kt", "SceneObjectRenderer.kt")

    @Test
    fun theRenderPathTakesTheClockAsAnInputOrNotAtAll() {
        for (name in renderPath) {
            val file = engineSource(name)
            assertTrue("$name is not where this test thinks it is: $file", file.isFile)
            for (line in file.readText().lines().withIndex()) {
                val code = line.value.trim()
                if (code.startsWith("*") || code.startsWith("//") || code.startsWith("/*")) continue
                for (read in wallClockReads) {
                    assertTrue(
                        "$name:${line.index + 1} reads the wall clock:\n    $code\n" +
                            "A frame has to be a function of what the renderer was handed, or a " +
                            "golden of it can go red on its own at three in the morning and green " +
                            "again by lunchtime -- which is what `moonPhase()` on this line did " +
                            "until v5.4G. Take the value as a parameter (the moon phase travels in " +
                            "SunPositionCalculator.DayPhase) and let PaperWallpaperService, which " +
                            "is where this app is allowed to read a clock, fill it in.",
                        !code.contains(read),
                    )
                }
            }
        }
    }

    /**
     * And the function the goldens call to describe a sky must not read a clock either --- it is
     * the other half of the same rule, on the other side of the call.
     */
    @Test
    fun computeDoesNotReachForAClockOfItsOwn() {
        val body = engineSource("SunPositionCalculator.kt").readText()
            .substringAfter("    fun compute(")
            .substringBefore("\n    /**")
        for (read in wallClockReads) {
            assertTrue(
                "SunPositionCalculator.compute() reads '$read'. It is the one function every " +
                    "golden scene builds its sky with, so a clock in it makes every golden " +
                    "irreproducible at once.",
                !body.contains(read),
            )
        }
    }

    private fun engineSource(name: String): File =
        moduleRoot().resolve("src/main/kotlin/com/paperscrape/livewallpaper/engine/$name")

    /** The `app` module directory, found the way the other source-reading tests find theirs. */
    private fun moduleRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val root = if (File(dir, "src/main/kotlin/com/paperscrape/livewallpaper/engine").isDirectory) {
                dir
            } else {
                File(dir, "app")
            }
            if (File(root, "src/main/kotlin/com/paperscrape/livewallpaper/engine").isDirectory) return root
            dir = dir.parentFile
        }
        error("app module root not found from ${File(".").absolutePath}")
    }
}
