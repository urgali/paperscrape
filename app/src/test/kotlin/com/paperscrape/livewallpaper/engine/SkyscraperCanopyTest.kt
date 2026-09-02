package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The tower's entrance canopy stays inside the tower: rc2 criterion.
 *
 * The first drawing was a 110x8 capsule blitted at (-55,-6) -- wider than the 90-unit tower,
 * rounded ends 10 units clear of each wall, lower half below the ground line. Lit amber at night
 * it read as a glowing shelf floating in front of the building, crossed by whatever stood on the
 * pavement. The criterion: zero frontage pixels outside the building's side edges, and the base
 * ON the ground line, not straddling it. Asserted off the shipped artwork and the layout
 * constants, which is exactly what the renderer multiplies.
 */
class SkyscraperCanopyTest {

    @Test
    fun `the canopy stays inside the tower's walls and sits on the ground`() {
        val png = ImageIO.read(File(drawableDir, "skyscraper_canopy.png"))
        val widthUnits = png.width / 3f
        val heightUnits = png.height / 3f
        val left = SkyscraperSpriteLayout.CANOPY_X
        val right = left + widthUnits
        val wallHalf = SkyscraperSpriteLayout.WIDTH / 2f
        assertTrue(
            "the canopy's left edge $left must not leave the tower (-$wallHalf)",
            left >= -wallHalf,
        )
        assertTrue(
            "the canopy's right edge $right must not leave the tower ($wallHalf)",
            right <= wallHalf,
        )
        assertEquals(
            "the canopy's base sits exactly on the ground line",
            0f,
            SkyscraperSpriteLayout.CANOPY_Y + heightUnits,
            0.001f,
        )
        // And it still spans the entrance it is an awning for: 32 units at -16..16.
        assertTrue("the canopy must cover the entrance", left <= -16f && right >= 16f)
    }

    private val drawableDir: File by lazy {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "${prefix}src/main/res/drawable-nodpi")
                if (candidate.isDirectory) return@lazy candidate
            }
            dir = dir.parentFile
        }
        error("could not locate src/main/res/drawable-nodpi")
    }
}
