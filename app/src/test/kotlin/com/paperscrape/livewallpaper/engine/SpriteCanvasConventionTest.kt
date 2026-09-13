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
 * The ones that do *not* touch an edge are the exceptions `ARCHITECTURE.md` describes: sprites
 * whose transparent margin is load-bearing because their anchor is measured against it -- the sun
 * and moon centred in a fixed disc, the glow, the sparkle, the firework, a bird centred on its
 * flight path.
 *
 * So the convention is the rule, the listed few are the declared exceptions, and this test is where
 * both are written down. A sprite growing a margin nobody declared, or a declared one losing its
 * own, is a change to how something is anchored and fails here rather than moving quietly on screen.
 *
 * **v4.21 moved `tree_fir_snow` out of the exceptions**, and it is worth saying why, because the
 * direction is unusual: it did not lose an anchor, it stopped paying for one. The sprite used to be
 * drawn on a full copy of the fir's canvas so that both could blit at one origin, and the 28 units
 * of transparency that cost were the "load-bearing margin". Trimming it to its content and giving
 * it its own origin -- `(-28,-112)` against the fir's `(-40,-122)`, derived from the same viewBox --
 * recovers 211 680 B of decoded memory, the largest single item in this release's sprite budget,
 * and registers the two just as tightly. Its two exceptions here became one derivation there.
 * Both counts below moved with it, in the direction that means less waste, not more.
 */
class SpriteCanvasConventionTest {

    /** Sprites whose transparent margin is deliberate because an anchor is measured against it. */
    private val marginIsLoadBearing = setOf(
        // **v4.28 puts `bird_body` back here, and the reason is the opposite of v4.26's.** v4.26
        // removed it because concept A "Colomba" filled its 51x21 canvas, so the flap axis -- canvas
        // row 15, which `BIRD_SPRITE_ORIGIN_Y_PX -15` blits against -- was carried by the drawing
        // itself. B1 "Rondine" is a swallow: a forked tail and swept-back wings do not reach the
        // canvas corners, and trimming the canvas onto them would move row 15 and with it the axis
        // the wing-beat mirrors about. The margin is the registration, exactly as it is for the
        // moons, so it is declared rather than trimmed away.
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
        // v4.28. The wave's two masks share one 360x132 canvas and blit at one origin, so the body
        // is authored in the foam's coordinates: its margin is the water the lip curls over, and
        // trimming it would slide the face out from under the foam. The same registration crop as
        // `pumpkin_face`. Its twin `wave_tube_crest` does reach the canvas, and must: the spray is
        // thrown to the very front of the shape.
        "wave_tube_body",
        // `tree_fir_snow` was here until v4.21 trimmed it to its content and gave it its own blit
        // origin. See this class's own doc for why that is a recovery rather than a lost anchor.
    )

    @Test
    fun `every sprite either fills its canvas or is a declared exception`() {
        val unexpected = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (file in sprites()) {
            val name = file.nameWithoutExtension
            val touches = touchesAnEdge(ImageIO.read(file))
            // **v4.25 removed the `_head_car` exemption, and it is a recovery rather than a
            // relaxation.** rc4 exempted that family because it shared one 47x44 canvas whose
            // margin was the registration seating all eight members plus their tones. The v4.25
            // people are generated with each shared canvas already trimmed onto what the family
            // drawn on it covers -- the same crop for every member, so the registration is
            // untouched -- and the family now reaches its own edges like everything else. The
            // exemption would have gone on hiding a margin nobody needed.
            // **v4.30's region masks are exempt as a class, and it is the one exemption in this
            // file that is about a *kind* of sprite rather than a named one.**
            //
            // A mask carries the weight of one region of a figure -- the hair, the trousers -- so
            // its ink is a few tenths of the canvas by construction and the rest is transparent.
            // That margin is not padding anybody could trim: it is the registration. A mask is
            // blitted at its shape's own origin so that it lands exactly on the fixed art, and a
            // crop of the PNG would have to be paid for by the engine sliding it back, on a grid
            // that does not divide -- `SpriteDetailLevel.reduced` truncates, so a 96-wide crop of a
            // 117-wide canvas reduces to texels of a different size and the two layers drift apart
            // across the sprite (measured: dE 5.6 at the device's own level).
            //
            // The transparency is not shipped to the GPU either. `GlTextureCache` crops it away
            // **after** the reduction, where the texels are the canvas's own texels and there is no
            // grid to match -- which is why `SpriteDrawScaleTest`'s budget fell while
            // `SpriteGeometryTest`'s rose. Naming them one by one would be 167 lines that say the
            // same sentence.
            val isRegionMask = name.matches(MASK_NAME) || name.matches(NEIGHBOURHOOD_LAYER)
            val loadBearing = name in marginIsLoadBearing || isRegionMask
            if (!touches && !loadBearing) unexpected += name
            if (touches && isRegionMask) continue
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
        // 305 until v4.30 replaced 168 skin-tone copies and four unreachable winter window busts
        // with 195 layer files: a shape now ships as fixed art plus one weight mask per colourable
        // region, twenty of which turned out to be bytes another shape had already written and are
        // shared rather than written twice.
        // 370 in v5.0: the 34 flat-facade building sprites left and the 72 pieces of the redrawn
        // neighbourhood arrived. See `SpriteGeometryTest.decodedByteBudget`, v5.0.
        assertEquals("370 sprites are expected", 370, all.size)
        // 216 until v4.21 trimmed `tree_fir_snow` onto its own content, 217 until v4.25 redrew the
        // people on canvases trimmed to their own families: the 166 person sprites went from
        // carrying a margin apiece to reaching an edge, which is why this jumped by 38. 255 until
        // v4.26 redrew the bird on a canvas its content fills. 293 in v4.28: the 36 carrying
        // frames and the umbrella's canopy reach their edges as the families they belong to do, the
        // wave's foam reaches three of its own, and the bird goes back to a registration margin
        // while the wave's body takes one -- both declared above.
        // 293 in v4.28. v4.30's fixed layers reach their shapes' edges exactly as the drawings
        // they were cut from do -- but 168 tone copies that did left the set, so the count falls
        // even though the convention did not move. Its 167 masks do not reach an edge and cannot;
        // see the exemption above.
        // 193 until v5.0. The 34 flat facades reached their edges, as a facade drawn to its own
        // outline does; the 72 pieces that replace them are layers, and 48 of them reach an edge
        // while the rest carry the registration margin the exemption above describes.
        assertEquals("207 of them reach a canvas edge", 207, touching)
    }

    private companion object {
        /** A v4.30 region mask: `<shape>_m` plus the region's letter. */
        val MASK_NAME = Regex("""person_.*_m[shtb]""")

        /**
         * A v5.0 neighbourhood layer: a building piece's fixed art or one of its two masks.
         *
         * **The same exemption as the people's, for the same reason, and it covers the fixed layer
         * too.** A piece is drawn once and decomposed into `_fx` plus `_mw` and `_mg`, all three
         * blitted at the piece's own origin so they land on each other exactly. Their margins are
         * that registration: a mask's ink is the few tenths of the canvas its region covers, and
         * the fixed layer's is whatever the tinted weights took out of the drawing. Cropping any
         * of them would have to be paid for by sliding it back on a grid that does not divide --
         * `SpriteDetailLevel.reduced` truncates -- and the three layers would drift apart across
         * the piece.
         *
         * The transparency is not shipped to the GPU: `GlTextureCache` crops it away **after** the
         * reduction, where the texels are the canvas's own. A `_snow_fx` is one of these as well;
         * it is a drift cut to a roof it must land on, which is the same registration.
         */
        val NEIGHBOURHOOD_LAYER = Regex("""(house|tower|restaurant|bar)_.*_(fx|mw|mg)""")
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
