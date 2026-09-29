package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Nothing on the render path may ask what time it is.**
 *
 * `PaperRenderer` composes a frame and `SceneObjectRenderer` draws the objects in it, and
 * everything they need is handed to them --- the size, the theme, the customisation, the scene
 * clock, the day phase. Until v5.4G one line was not: `drawMoonWithPhase` called
 * `SunPositionCalculator.moonPhase()`, whose default argument is `System.currentTimeMillis()`, and
 * the moon in a golden frame was therefore chosen by the phone rather than by the scene. The
 * goldens `night` and `shops-closed-night` passed at 19:02 and failed by 17 and 39 pixels at 22:25
 * on the same build, because the real moon had crossed from crescent to half.
 *
 * ### The render path, and why these files (v5.9B, inventory I-45)
 *
 * Until v5.9B this read the two renderers and nothing else, while the frame is drawn by more than
 * two files: the colours come from `SkyGradient`, `SceneColour` and `DayNightColor`, the geometry
 * from `SceneSpace`, the effects from `FireworkEffect` and `SantaSleighEffect`, the pixels from
 * `CanvasSceneTarget`, `GlSceneTarget`, `SpriteBlitter`, `SpriteCache` and the GL texture cache. A
 * clock read in any of those reaches a golden exactly as the moon's did, and this test could not
 * see it. So the render path is now **every Kotlin file of the `engine` package** -- which is where
 * everything that composes or draws a frame lives, and where the scene model the renderers read
 * lives too -- **plus `ui/ThemePreview.kt`**, the one UI file that paints a scene (the gallery and
 * settings cards, from `ThemePreviewScene`, which is in `engine`). Asked of the tree, not listed:
 * a new file in `engine` is on the render path until somebody declares otherwise, here, with a
 * reason.
 *
 * **What is not on it** is [boundary]: the three files whose job is to read the clock and hand the
 * answer in -- the wallpaper service, which is where this app is allowed to read a clock, and the two
 * calendars it asks for today's theme and today's sunrise. Each carries its reason, and each has to
 * still read a clock or the entry is stale and fails. And inside `SunPositionCalculator`, which the
 * renderers do use (`DayPhase`, `compute`), the two functions that are clocks -- [doors] -- are cut
 * out of the scan, and no render-path file may call them.
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
     * two render loops (`GlRenderThread`, `VsyncGrid`) legitimately use it to pace frames and to
     * measure how long one took.
     */
    private val wallClockReads = listOf(
        "System.currentTimeMillis",
        "SystemClock.elapsedRealtime",
        "SystemClock.uptimeMillis",
        "LocalDate.now",
        "LocalDateTime.now",
        "LocalTime.now",
        "ZonedDateTime.now",
        "OffsetDateTime.now",
        "Instant.now",
        "Calendar.getInstance",
        "SunPositionCalculator.moonPhase(",
        "SunPositionCalculator.currentHour24(",
    )

    /** The engine files that are not on the render path, and why. Checked in both directions. */
    private val boundary = mapOf(
        "PaperWallpaperService.kt" to
            "the wallpaper service: the one place this app is allowed to read the clock, which it " +
            "hands to the renderer as the scene clock, the day phase and the moon phase",
        "SeasonalThemeRules.kt" to
            "the holiday calendar: its functions default to LocalDate.now() because they answer " +
            "'which theme is today', asked by the service and the settings screen; a renderer is " +
            "handed the theme and never asks",
        "SolarDaySchedule.kt" to
            "today's sunrise and sunset, recomputed by the service once a day and handed in inside " +
            "the day phase; its Calendar.getInstance(zone) is overwritten at once with the instant " +
            "the service passes",
    )

    /** The functions inside the render path that are clocks: cut out of the scan, and never called from it. */
    private val doors = mapOf(
        "SunPositionCalculator.kt" to listOf("fun currentHour24(", "fun moonPhase("),
    )

    /** And the boundary files' own entry points, which no render-path file may call either. */
    private val boundaryCalls = listOf("SeasonalThemeRules.", "SolarDaySchedule.")

    @Test
    fun theRenderPathTakesTheClockAsAnInputOrNotAtAll() {
        val found = mutableListOf<String>()
        for (file in renderPath()) {
            for ((number, code) in codeLines(file)) {
                for (read in wallClockReads + boundaryCalls) {
                    if (code.contains(read)) found += "${file.name}:$number reads the wall clock ($read):\n    $code"
                }
            }
        }
        assertEquals(
            "A frame has to be a function of what the renderer was handed, or a golden of it can go " +
                "red on its own at three in the morning and green again by lunchtime -- which is what " +
                "`moonPhase()` did from the renderer until v5.4G. Take the value as a parameter (the " +
                "moon phase travels in SunPositionCalculator.DayPhase) and let PaperWallpaperService, " +
                "which is where this app is allowed to read a clock, fill it in",
            emptyList<String>(),
            found,
        )
    }

    /** The render path is what this class says it is, so the test above cannot pass by reading nothing. */
    @Test
    fun theRenderPathIsTheWholeDrawingPackageAndTheCardPainter() {
        val names = renderPath().map { it.name }.toSet()
        for (must in listOf(
            "PaperRenderer.kt", "SceneObjectRenderer.kt", "ThemePreviewScene.kt", "ThemePreview.kt",
            "CanvasSceneTarget.kt", "GlSceneTarget.kt", "SpriteBlitter.kt", "SkyGradient.kt",
            "FireworkEffect.kt", "SantaSleighEffect.kt", "SunPositionCalculator.kt",
        )) {
            assertTrue("$must is not on the render path", must in names)
        }
        for (name in boundary.keys) assertTrue("$name is declared a boundary and is still scanned", name !in names)
        val engine = moduleRoot().resolve(ENGINE).listFiles { f -> f.extension == "kt" }!!.size
        assertEquals("every engine file but the boundary, plus the card painter", engine - boundary.size + 1, names.size)
    }

    /** Checked in both directions: a boundary file that no longer reads a clock does not need to be one. */
    @Test
    fun everyBoundaryFileStillNeedsToBeOne() {
        for ((name, reason) in boundary) {
            val file = moduleRoot().resolve("$ENGINE/$name")
            assertTrue("$name is declared a boundary and does not exist", file.isFile)
            assertTrue("$name's reason is too short to be a reason", reason.length > 60)
            val reads = codeLines(file).any { (_, code) -> wallClockReads.any { code.contains(it) } }
            assertTrue("$name no longer reads a clock: take it off the boundary, so it is scanned", reads)
        }
        for ((name, functions) in doors) {
            val text = moduleRoot().resolve("$ENGINE/$name").readText()
            for (f in functions) assertTrue("$name no longer has $f: remove the door", text.contains(f))
        }
    }

    /**
     * **The rule bites, on a file the old two-file list could not see.** The scan is run over a copy
     * of `SkyGradient.kt` with one clock read planted in it, and must find it on its line.
     */
    @Test
    fun aClockReadAnywhereOnThePathIsFound() {
        val sky = moduleRoot().resolve("$ENGINE/SkyGradient.kt")
        val planted = File.createTempFile("SkyGradient", ".kt").apply {
            deleteOnExit()
            writeText(sky.readText() + "\nprivate val plantedToday = java.time.LocalDate.now()\n")
        }
        val hits = codeLines(planted).filter { (_, code) -> wallClockReads.any { code.contains(it) } }
        assertEquals("the planted read must be found, and only it", 1, hits.size)
        assertTrue(hits.single().second.contains("LocalDate.now"))
    }

    /**
     * And the function the goldens call to describe a sky must not read a clock either --- it is
     * the other half of the same rule, on the other side of the call.
     */
    @Test
    fun computeDoesNotReachForAClockOfItsOwn() {
        val body = moduleRoot().resolve("$ENGINE/SunPositionCalculator.kt").readText()
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

    private fun renderPath(): List<File> {
        val root = moduleRoot()
        val engine = root.resolve(ENGINE).listFiles { f -> f.extension == "kt" && f.name !in boundary }!!.toList()
        return (engine + root.resolve("src/main/kotlin/com/paperscrape/livewallpaper/ui/ThemePreview.kt")).sortedBy { it.name }
    }

    /**
     * The file's code lines with their numbers: comments dropped, and a door function's whole
     * declaration (signature and body) dropped too, for the file that has doors.
     */
    private fun codeLines(file: File): List<Pair<Int, String>> {
        val lines = file.readLines()
        val skip = BooleanArray(lines.size)
        for (door in doors[file.name].orEmpty()) {
            val start = lines.indexOfFirst { it.contains(door) }
            if (start < 0) continue
            var depth = 0
            var opened = false
            var i = start
            while (i < lines.size) {
                skip[i] = true
                depth += lines[i].count { it == '{' } - lines[i].count { it == '}' }
                if (lines[i].contains('{')) opened = true
                if (opened && depth <= 0) break
                i++
            }
        }
        var inBlock = false
        val out = mutableListOf<Pair<Int, String>>()
        for ((index, raw) in lines.withIndex()) {
            val line = raw.trim()
            if (inBlock) {
                if (line.contains("*/")) inBlock = false
                continue
            }
            if (line.startsWith("/*")) {
                if (!line.contains("*/")) inBlock = true
                continue
            }
            if (skip[index] || line.startsWith("*") || line.startsWith("//")) continue
            out += (index + 1) to line.substringBefore("//")
        }
        return out
    }

    /** The `app` module directory, found the way the other source-reading tests find theirs. */
    private fun moduleRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            val root = if (File(dir, ENGINE).isDirectory) dir else File(dir, "app")
            if (File(root, ENGINE).isDirectory) return root
            dir = dir.parentFile
        }
        error("app module root not found from ${File(".").absolutePath}")
    }

    private companion object {
        const val ENGINE = "src/main/kotlin/com/paperscrape/livewallpaper/engine"
    }
}
