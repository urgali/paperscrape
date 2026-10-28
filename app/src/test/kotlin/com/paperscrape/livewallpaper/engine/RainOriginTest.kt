package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The rain starts in the middle of the cloud band, and the renderer starts it there through the band's own function.**
 *
 * The clouds are hung on the band's middle line, and the rain comes out of them from that line
 * ([CloudBand.precipitationOriginY]); the tests of the band and the sweep of the rain's contrast (`CloudBandTest`,
 * `PrecipitationContrastTest`) measure that function, and so nothing the renderer draws if `drawPrecipitation` works
 * the line out again for itself: its own `0.5f` moved to `0.2f` -- the rain starting above the clouds -- would leave
 * every one of them green.
 */
class RainOriginTest {

    private val screenHeight = 2424

    @Test
    fun `the rain starts on the middle line of the cloud band at every height of the arc`() {
        for (step in 0..20) {
            val arc = SUN_CLOUD_HEIGHT_MIN + (SUN_CLOUD_HEIGHT_MAX - SUN_CLOUD_HEIGHT_MIN) * step / 20f
            val top = CloudBand.topFor(screenHeight, arc)
            val bottom = top + CloudBand.heightFor(screenHeight)
            assertEquals("the rain's start at arc $arc", (top + bottom) / 2f, CloudBand.precipitationOriginY(screenHeight, arc), 0.01f)
        }
    }

    /** Read off the renderer, whole lines with their comments taken off: the start is the band's function and no copy. */
    @Test
    fun `the renderer starts the rain where the band's function says`() {
        val body = rendererSource().readText()
            .substringAfter("private fun drawPrecipitation(")
            .substringBefore("\n    }\n")
        val lines = body.lines().map { it.substringBefore("//").trim() }.filter { it.isNotEmpty() }
        assertTrue(
            "drawPrecipitation does not start its rain at CloudBand.precipitationOriginY",
            "val fallStartY = CloudBand.precipitationOriginY(screenHeight, sceneCustomization.sky.sunCloudHeight)" in lines,
        )
        assertEquals("fallStartY is worked out more than once", 1, lines.count { it.startsWith("val fallStartY") })
        assertTrue("the renderer works the band out again for its rain", lines.none { "cloudBandTopFor(" in it || "cloudBandHeightFor(" in it })
    }

    private fun rendererSource(): File {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/PaperRenderer.kt"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "$prefix$suffix")
                if (candidate.isFile) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate $suffix")
    }
}
