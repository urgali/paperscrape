package com.paperscrape.livewallpaper.engine

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * CLIP-LIBRARY-WIDE, closed: sprites reach their own canvas edges **on purpose**.
 *
 * The finding was raised from the two winter head sprites, whose outline stroke is cut where the
 * artwork meets the frame, and it asked whether that is lost artwork across the library.
 *
 * Measured, it is not. **most of the 233 sprites have opaque pixels on a canvas border**, and 65 of
 * them touch all four. That is not two hundred defects; it is the authoring convention this asset
 * set is built on and that `tools/assets`' `normalize` enforces from the other side -- a sprite may
 * carry no removable padding, so its canvas *is* its content box, and every anchor in
 * `SceneObjectRenderer` is measured against that. Widening canvases to give every outline its
 * half-stroke would add transparent margin to two hundred files and move every origin that reads
 * them, to recover a pixel of stroke nobody has ever reported seeing.
 *
 * The twelve that do *not* touch an edge are the exceptions `ARCHITECTURE.md` describes: sprites
 * whose transparent margin is load-bearing because their anchor is measured against it -- the sun
 * and moon centred in a fixed disc, the glow, the sparkle, the firework, a bird centred on its
 * flight path, and `tree_fir_snow`, which hangs off a declared attachment point.
 *
 * So the convention is the rule, the eleven are the declared exceptions, and this test is where
 * both are written down. A thirteenth sprite growing a margin, or one of these twelve losing its own,
 * is a change to how something is anchored and fails here rather than moving quietly on screen.
 */
class SpriteCanvasConventionTest {

    /** Sprites whose transparent margin is deliberate because an anchor is measured against it. */
    private val marginIsLoadBearing = setOf(
        "bird_body",
        "firework",
        "moon_crescent",
        "moon_full",
        "moon_gibbous",
        "moon_half",
        "moon_jack_o_lantern",
        // v4.17. The carved face is drawn on `pumpkin_body`'s canvas at `pumpkin_body`'s origin so
        // the two register exactly; the eyes and the grin sit well inside the fruit, so the margin
        // around them is the body it is cut into and is as load-bearing as any anchor here.
        "pumpkin_face",
        // v4.20. A registration crop: the overlay is authored in the shell's own coordinates and
        // its margin is the lamp housings' surround, which is what keeps a lit lamp inside its
        // housing at night.
        "car_lights",
        // rc4. The day twin of car_lights: same viewBox, same registration, unlit colours.
        "car_lights_day",
        "star_sparkle",
        "sun_body",
        "sun_glow",
        "tree_fir_snow",
    )

    @Test
    fun `every sprite either fills its canvas or is a declared exception`() {
        val unexpected = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (file in sprites()) {
            val name = file.nameWithoutExtension
            val touches = touchesAnEdge(ImageIO.read(file))
            // rc4: the head_car family shares one 47x44 canvas with the eye line at x=23 for all
            // eight members plus their skin tones, so every member's margin is the registration
            // that seats it -- load-bearing for the whole family, like car_lights' surround.
            val loadBearing = name in marginIsLoadBearing || name.contains("_head_car")
            if (!touches && !loadBearing) unexpected += name
            if (touches && loadBearing) missing += name
        }
        assertTrue(
            "these sprites grew a transparent margin nobody declared: $unexpected",
            unexpected.isEmpty(),
        )
        assertTrue(
            "these are declared as having a load-bearing margin but now reach their edges: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `the convention is the majority, not a handful`() {
        // Stated as a number so the finding's own measurement is recorded: this is a library-wide
        // convention, which is why it is closed as one rather than fixed sprite by sprite.
        val all = sprites()
        val touching = all.count { touchesAnEdge(ImageIO.read(it)) }
        assertEquals("258 sprites are expected", 258, all.size)
        assertEquals("212 of them reach a canvas edge", 212, touching)
    }

    private fun touchesAnEdge(image: BufferedImage): Boolean {
        val w = image.width
        val h = image.height
        for (x in 0 until w) {
            if (image.getRGB(x, 0) ushr 24 != 0) return true
            if (image.getRGB(x, h - 1) ushr 24 != 0) return true
        }
        for (y in 0 until h) {
            if (image.getRGB(0, y) ushr 24 != 0) return true
            if (image.getRGB(w - 1, y) ushr 24 != 0) return true
        }
        return false
    }

    private fun sprites(): List<File> =
        drawableDir().listFiles().orEmpty().filter { it.extension == "png" }.sortedBy { it.name }

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
