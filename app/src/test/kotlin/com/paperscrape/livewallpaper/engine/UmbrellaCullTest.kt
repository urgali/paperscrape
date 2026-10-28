package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A walker with an umbrella is cut at the screen's edge where its canopy ends, not where its body does.**
 *
 * The canopy is hung on the hand, ahead of the figure on the side it walks to, and reaches past the walk sprite's own
 * half width: an adult's 12 units ahead of the figure's centre and half its 48-unit canvas on, 36 units against the
 * sprite's 19.5. Cut at 19.5, a walker coming in at the edge would bring up to 16.5 units of canopy in one frame,
 * where it should slide in; and the changes made out of sight -- the umbrella going up or down, the colours dealt
 * again -- could fall while that piece shows. Measured here on the shipped canopy's ink, family by family, at the
 * renderer's own grip and size.
 */
class UmbrellaCullTest {

    @Test
    fun `the cut of a walker with an umbrella holds the whole canopy`() {
        val canopy = ImageIO.read(File(drawableDir(), "umbrella_canopy.png"))
        var inkLeft = canopy.width
        var inkRight = -1
        for (x in 0 until canopy.width) {
            for (y in 0 until canopy.height) {
                if ((canopy.getRGB(x, y) ushr 24) != 0) {
                    if (x < inkLeft) inkLeft = x
                    if (x > inkRight) inkRight = x
                }
            }
        }
        assertTrue("umbrella_canopy.png has no ink", inkRight >= 0)
        val unitsPerPixel = 1f / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        val halfCanvas = canopy.width * unitsPerPixel / 2f
        val cut = SceneObjectRenderer.personCullHalfWidthUnits(carrying = true)
        for (kind in SceneObjectRenderer.carryHandX.indices) {
            // `drawUmbrella`: the canvas starts half its width behind the hand, at the family's own scale.
            val hand = SceneObjectRenderer.carryHandX[kind] + SceneObjectRenderer.PERSON_ANCHOR_X_UNITS
            val scale = SceneObjectRenderer.carryCanopyScale[kind]
            val canvasLeft = hand - halfCanvas * scale
            val ahead = canvasLeft + (inkRight + 1) * unitsPerPixel * scale
            val behind = canvasLeft + inkLeft * unitsPerPixel * scale
            assertTrue("family $kind: the canopy reaches $ahead units ahead, past the cut of $cut", ahead <= cut)
            assertTrue("family $kind: the canopy reaches ${-behind} units behind, past the cut of $cut", -behind <= cut)
        }
        assertEquals(
            "a walker without an umbrella is cut at the walk sprite's own half width",
            SceneObjectRenderer.PERSON_HALF_WIDTH_UNITS, SceneObjectRenderer.personCullHalfWidthUnits(carrying = false), 0f,
        )
    }

    /** And the walkers are cut by that rule, read off `drawPeople` as a whole line, its comment taken off. */
    @Test
    fun `the walkers are cut by the rule`() {
        val body = File(drawableDir().parentFile!!.parentFile, "kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt").readText()
            .substringAfter("private fun drawPeople(")
            .substringBefore("\n    private fun ")
        val lines = body.lines().map { it.substringBefore("//").trim() }
        assertTrue(
            "drawPeople does not cut a walker by personCullHalfWidthUnits, wide while an umbrella is up or wanted",
            "val halfWidth = personCullHalfWidthUnits(carrying || wantsUmbrella) * s" in lines,
        )
        // And that width is the one the copies are walked and cut by, and the only cut width in the function: a second
        // width handed to the cull would leave the line above true and the canopy cut narrow.
        assertEquals("personCullHalfWidthUnits in drawPeople", 1, lines.count { "personCullHalfWidthUnits(" in it })
        for (line in listOf(
            "val firstTile = firstVisibleTileOffset(x, halfWidth, geom.tileWidth)",
            "val tileLimit = tileOffsetLimit(x, halfWidth, geom.tileWidth, screenWidth)",
            "if (!isHorizontallyVisible(copyX, halfWidth, screenWidth)) continue",
        )) {
            assertTrue("drawPeople does not have: $line", line in lines)
        }
    }

    /**
     * **An umbrella is never opened where its canopy would show**: a walker coming in at the
     * left edge when the rain starts, a step a frame, is cut wide from the moment it wants the umbrella, so the change
     * that opens it -- made only while no copy is drawn (`PedestrianCarry.nextCarrying`) -- comes while the canopy is
     * wholly past the edge, and the canopy then slides in. Cut by the figure alone until the umbrella is up, a walker
     * between the figure's cut and the canopy's would open it a frame before its canopy stands on the screen.
     */
    @Test
    fun `an umbrella opens only while its whole canopy is off the screen`() {
        val canopy = SceneObjectRenderer.PERSON_CARRYING_HALF_WIDTH_UNITS
        var start = -60f
        while (start < 0f) {
            var centre = start
            var carrying = false
            while (centre < 40f) {
                val cut = SceneObjectRenderer.personCullHalfWidthUnits(true) // the rain wants it: cut as wide as the canopy
                val onScreen = centre + cut > 0f
                val next = PedestrianCarry.nextCarrying(current = carrying, wanted = true, onScreen = onScreen)
                if (next && !carrying) {
                    assertTrue("from $start, opened at $centre, its canopy reaching ${centre + canopy}", centre + canopy <= 0f)
                }
                carrying = next
                centre += 0.7f
            }
            start += 0.5f
        }
    }

    private fun drawableDir(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, prefix + "src/main/res/drawable-nodpi")
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate src/main/res/drawable-nodpi")
    }
}
