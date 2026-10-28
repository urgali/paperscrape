package com.paperscrape.livewallpaper.engine

import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Sprites reach their own canvas edges **on purpose**.
 *
 * Most shipped sprites have opaque pixels on a canvas border, an outline stroke cut where the
 * artwork meets the frame among them. That is the authoring convention this asset set is built on,
 * and `tools/assets`' `normalize` enforces it from the other side: a sprite may carry no removable
 * padding, so its canvas *is* its content box, and every anchor in `SceneObjectRenderer` is
 * measured against that. Widening canvases to give every outline its half-stroke would add
 * transparent margin to hundreds of files and move every origin that reads them, for a pixel of
 * stroke.
 *
 * The ones that do *not* touch an edge are the exceptions `ARCHITECTURE.md` §5 describes: sprites
 * whose transparent margin is load-bearing because their anchor is measured against it -- the sun
 * and moon centred in a fixed disc, the glow, the sparkle, the firework, a bird centred on its
 * flight path -- and the layers and grid-trimmed pieces exempted as classes below.
 *
 * So the convention is the rule, the declared exceptions are listed, and this test is where both
 * are written down. A sprite growing a margin nobody declared, or a declared one losing its own,
 * is a change to how something is anchored and fails here rather than moving quietly on screen.
 */
class SpriteCanvasConventionTest {

    /** Sprites whose transparent margin is deliberate because an anchor is measured against it. */
    private val marginIsLoadBearing = setOf(
        // The swallow's flap axis is canvas row 15 of its 51x21 canvas, which `BIRD_SPRITE_ORIGIN_Y_PX`
        // (-15) blits against. A forked tail and swept-back wings do not reach the canvas corners, and
        // trimming the canvas onto them would move row 15 and with it the axis the wing-beat mirrors
        // about. The margin is the registration, as it is for the moons.
        "bird_body",
        "firework",
        "moon_crescent",
        "moon_full",
        "moon_gibbous",
        "moon_half",
        "moon_jack_o_lantern",
        // The laughing face is the carved moon's disc on the moons' 240-px canvas, blitted at the moons' origin, so its
        // margin is the same registration.
        "moon_jack_o_lantern_laugh",
        // The carved face is drawn on `pumpkin_body`'s canvas at `pumpkin_body`'s origin so the two
        // register exactly; the eyes and the grin sit well inside the fruit, so the margin around
        // them is the body it is cut into and is as load-bearing as any anchor here.
        "pumpkin_face",
        "star_sparkle",
        "sun_body",
        "sun_glow",
        // The wave's two masks share one 360x132 canvas and blit at one origin, so the body is
        // authored in the foam's coordinates: its margin is the water the lip curls over, and
        // trimming it would slide the face out from under the foam. The same registration crop as
        // `pumpkin_face`. Its twin `wave_tube_crest` does reach the canvas, and must: the spray is
        // thrown to the very front of the shape.
        "wave_tube_body",
        // **The palm crowns, whose margin is the declared attachment itself.** All three are drawn
        // on one 168x144 canvas around the point at (84, 78) px where the blades converge, because
        // the live crown's blades fall *below* their own convergence and the frosted one's do too --
        // that is the shape of a palm. `drawPalmTree` blits all three at that attachment negated,
        // so a crop onto either one's ink would move it against the other two and against the
        // trunk. The same registration crop as `pumpkin_face` and `wave_tube_body`.
        //
        // `palmtree_fronds_dead` shares that canvas and that attachment and is deliberately *not*
        // here: a crown that has collapsed back down the trunk reaches the canvas's own bottom
        // edge, so it satisfies the convention without an exemption. The three are one drawing in
        // three states -- if one of them is ever recut, all three are, and this list is part of
        // what has to be re-read when that happens.
        "palmtree_fronds",
        "palmtree_fronds_frost",
        // **For the grid's reason, not an anchor's**: the fir's snow is trimmed to whole units of the
        // 3-px grid (`tools/assets/fir/build_fir.py`, the grid every sprite is authored on), and its cut
        // edge falls one and two pixels inside them, on every side -- a margin under a unit, the grid's,
        // which no crop on the grid can take off. Its origin is the generator's (`FirTable.SNOW_X`/`SNOW_Y`),
        // derived with the fir's from one frame.
        "tree_fir_snow",
    )

    @Test
    fun `every sprite either fills its canvas or is a declared exception`() {
        val unexpected = mutableListOf<String>()
        val missing = mutableListOf<String>()
        for (file in sprites()) {
            val name = file.nameWithoutExtension
            val touches = touchesAnEdge(ImageIO.read(file))
            // **Layers are exempt as a class**: a person's region masks, a building piece's three
            // layers and the vehicle sheets (see each pattern below).
            //
            // A mask carries the weight of one region of a figure -- the hair, the trousers -- so
            // its ink is a few tenths of the canvas by construction and the rest is transparent.
            // That margin is not padding anybody could trim: it is the registration. A mask is
            // blitted at its shape's own origin so that it lands exactly on the fixed art, and a
            // crop of the PNG would have to be paid for by the engine sliding it back, on a grid
            // that does not divide -- `SpriteDetailLevel.reduced` truncates, so a 96-wide crop of a
            // 117-wide canvas reduces to texels of a different size and the two layers drift apart
            // across the sprite.
            //
            // The transparency is not shipped to the GPU either. `GlTextureCache` crops it away
            // **after** the reduction, where the texels are the canvas's own texels and there is no
            // grid to match. Matched by pattern, because naming them one by one would be a line each
            // that says the same sentence.
            val isRegionMask = name.matches(MASK_NAME) || name.matches(NEIGHBOURHOOD_LAYER) ||
                name.matches(VEHICLE_SHEET)
            // **The generated pieces trimmed on the grid are exempt as a class too, for the grid's reason
            // `tree_fir_snow` gives**: Santa's sleigh and its reindeer (`tools/assets/sleigh/build_sleigh.py`), the
            // Headless Horseman's, the scarecrow's and the Halloween decorations' pieces are trimmed to whole units
            // of the 3-px grid, so each side keeps the 0 to 2 px between its ink and the unit's edge -- whether a
            // given pose's ink reaches a side is where its cut happens to fall, and it moves with any redraw; the
            // Halloween moons' lights (`moon/build_jack_moon.py`) keep a guard pixel on every side they trim
            // (ARCHITECTURE.md §5, "The transparent margin is part of the drawing"). Their origins are the
            // generators', derived with their drawings.
            val isGridTrimmed = name.matches(GRID_TRIMMED)
            val loadBearing = name in marginIsLoadBearing || isRegionMask || isGridTrimmed
            if (!touches && !loadBearing) unexpected += name
            if (touches && (isRegionMask || isGridTrimmed)) continue
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
        // Stated as numbers so the convention's size is on record: it is a library-wide convention,
        // not a handful of sprites that happen to fill their canvases.
        val all = sprites()
        val touching = all.count { touchesAnEdge(ImageIO.read(it)) }
        // How many PNGs ship: a sprite added or removed moves it.
        assertEquals("486 sprites are expected", 486, all.size)
        // How many of them reach an edge: it moves when a sprite gains or loses a margin, and when a
        // sprite is added or removed. Many layers and grid-trimmed pieces do not reach one, and are
        // exempted above; the rest of the set does.
        assertEquals("258 of them reach a canvas edge", 258, touching)
    }

    private companion object {
        /** A person's region mask: `<shape>_m` plus the region's letter. */
        val MASK_NAME = Regex("""person_.*_m[shtb]""")

        /**
         * A neighbourhood layer: a building piece's fixed art or one of its two masks.
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
         *
         * `(.*_)?` and not `.*_`: the school's figure is one piece with no sub-name, so its layers
         * are `school_fx` and `school_mg` with nothing between, and so are the shop's and the two
         * stations', whose production names are their pieces' own.
         */
        val NEIGHBOURHOOD_LAYER = Regex("""(house|tower|restaurant|bar|school|shop|fire_station|police_station)_(.*_)?(fx|mw|mg)""")

        /** Generated pieces trimmed on the grid, or with a guard pixel: see the exemption where it is read. */
        val GRID_TRIMMED = Regex(
            """santa_sleigh|santa_reindeer_\d|moon_jack_o_lantern_light|moon_jack_o_lantern_laugh_light|""" +
                """horseman_(horse_\d|legs_\d|rider_body|rider_arm|rider_pumpkin|cape)|scarecrow|scarecrow_light|""" +
                """deco_(cobweb|jack_o_lantern|jack_o_lantern_light)""",
        )

        /**
         * A vehicle sheet: a body's paper, a body's glass, or the appliance's.
         *
         * **The margin is the frame the whole fleet is registered in, and it is the same kind of
         * exemption as the neighbourhood's.** Every vehicle sprite is authored in one coordinate
         * system -- the road at y=37, the nose and tail at the body's own half-length -- and every
         * one of them is blitted at its own viewBox minimum, so sixteen PNGs and about thirty
         * constants in `CarShell` and `SceneObjectRenderer` all read the same numbers. The cut-paper
         * drawing is what puts a margin round them: a body's shell is drawn a unit inside its canvas
         * so the cut edge's wobble has room, and a glass sheet is *deliberately* half a unit larger
         * than the hole it fills on every side plus half a unit of canvas, because the sheet lies
         * behind the paper and a seam would open the moment the two were the same size. Cropping
         * either one would move its origin and every number written against that frame.
         */
        val VEHICLE_SHEET = Regex("""(car_body|car_window|firetruck_body).*""")
    }

    /**
     * **The grid-trimmed pieces' exemption, held to what it says**: the exemption above takes them out of both checks,
     * so a margin grown past the grid's would pass there. The sleigh's, the Horseman's, the scarecrow's and the Halloween
     * decorations' pieces keep 0 to 2 px between their ink and the unit's edge on every side; the two moons' lights keep
     * their guard pixel at their left and top and 1 to 3 px at their right and bottom, where the box was rounded up to the
     * grid.
     */
    @Test
    fun `the grid-trimmed pieces keep only the grid's margin`() {
        val seen = mutableListOf<String>()
        for (file in sprites()) {
            val name = file.nameWithoutExtension
            if (!name.matches(GRID_TRIMMED)) continue
            seen += name
            val (left, top, right, bottom) = margins(ImageIO.read(file))
            if (name == "moon_jack_o_lantern_light" || name == "moon_jack_o_lantern_laugh_light") {
                assertEquals("$name left: its guard pixel", 1, left)
                assertEquals("$name top: its guard pixel", 1, top)
                assertTrue("$name right: $right", right in 1..3)
                assertTrue("$name bottom: $bottom", bottom in 1..3)
            } else {
                for ((side, m) in listOf("left" to left, "top" to top, "right" to right, "bottom" to bottom)) {
                    assertTrue("$name $side: $m px between its ink and the unit's edge", m in 0..2)
                }
            }
        }
        assertEquals(24, seen.size)
    }

    /** Transparent columns and rows between the ink and each edge: left, top, right, bottom. */
    private fun margins(image: BufferedImage): List<Int> {
        val w = image.width
        val h = image.height
        fun ink(x: Int, y: Int) = image.getRGB(x, y) ushr 24 != 0
        val left = (0 until w).first { x -> (0 until h).any { ink(x, it) } }
        val right = w - 1 - (w - 1 downTo 0).first { x -> (0 until h).any { ink(x, it) } }
        val top = (0 until h).first { y -> (0 until w).any { ink(it, y) } }
        val bottom = h - 1 - (h - 1 downTo 0).first { y -> (0 until w).any { ink(it, y) } }
        return listOf(left, top, right, bottom)
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
