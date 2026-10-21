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
        // removed it because concept A "Dove" filled its 51x21 canvas, so the flap axis -- canvas
        // row 15, which `BIRD_SPRITE_ORIGIN_Y_PX -15` blits against -- was carried by the drawing
        // itself. B1 "Swallow" is a swallow: a forked tail and swept-back wings do not reach the
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
        "star_sparkle",
        "sun_body",
        "sun_glow",
        // v4.28. The wave's two masks share one 360x132 canvas and blit at one origin, so the body
        // is authored in the foam's coordinates: its margin is the water the lip curls over, and
        // trimming it would slide the face out from under the foam. The same registration crop as
        // `pumpkin_face`. Its twin `wave_tube_crest` does reach the canvas, and must: the spray is
        // thrown to the very front of the shape.
        "wave_tube_body",
        // **v5.1's palm crowns, and their margin is the declared attachment itself.** All three
        // are drawn on one 168x144 canvas around the point at (84, 78) px where the blades
        // converge, because the live crown's blades fall *below* their own convergence and the
        // frosted one's do too -- that is the shape of a palm, and it is what the 120x120 canvas
        // they replace had no room for. `drawPalmTree` blits all three at that attachment negated,
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
        // `tree_fir_snow` was here until v4.21 trimmed it to its content and gave it its own blit
        // origin. See this class's own doc for why that is a recovery rather than a lost anchor.
        // **v5.14 puts it back, for a reason that is not an anchor's**: the fir A's snow is trimmed to
        // whole units of the 3-px grid (`tools/assets/fir/build_fir.py`, DESIGN_NOTES.md §4 rule 3), and
        // its cut edge falls one and two pixels inside them, on every side -- a margin under a unit,
        // the grid's, which no crop on the grid can take off. Its origin is the generator's
        // (`FirTable.SNOW_X`/`SNOW_Y`), derived with the fir's from one frame.
        "tree_fir_snow",
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
            val isRegionMask = name.matches(MASK_NAME) || name.matches(NEIGHBOURHOOD_LAYER) ||
                name.matches(VEHICLE_SHEET)
            // **v5.15's generated pieces are exempt as a class too, for the grid's reason `tree_fir_snow`
            // gives**: Santa's sleigh and its reindeer (`tools/assets/sleigh/build_sleigh.py`) are trimmed to
            // whole units of the 3-px grid, so each side keeps the 0 to 2 px between its ink and the unit's
            // edge -- whether a given pose's ink reaches a side is where its cut happens to fall, and it moves
            // with any redraw; the Halloween moon's light (`moon/build_jack_moon.py`) keeps a guard pixel on
            // every side it trims (ARCHITECTURE.md §5, "The transparent margin is part of the drawing"). Their
            // origins are the generators', derived with their drawings.
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
        // 371 in v5.1: the one flower clump became two, in bloom and gone over, so the scene can
        // stop drawing midsummer blooms under an autumn or a winter palette. One canvas, one
        // origin, one extra 108x36 PNG.
        // 417 in v5.4H: the two child families gained a carrying pose, 2 x 2 x 3 frames as fixed
        // art plus the region masks the pose actually moves -- 12 `fx`, 12 `ms`, 12 `mt`, 8 `mh`
        // and 2 `mb`. **Not 36, which is what the proposal round costed it at**, and the ten extra
        // are the interesting half: the proposal read the adults' pattern, where the raised arm
        // uncovers nothing the walking arm was hiding. A child's arm is shorter against a shorter
        // body, so raising it uncovers the girl's hair and both children's trousers, and those
        // masks stop being byte-identical to the walking frame's and stop being shared.
        // 421 in v5.6F: the school is the sixth building family and ships four layers of its
        // own -- `school_fx`, `school_mw`, `school_mg` and `school_snow_fx`. The sixteen vehicle
        // sprites were redrawn in the same release and none of them was added or removed.
        // 444 in v5.11: the towers' short and tall bodies (3 + 3 + 2 layers), the hall's windows by
        // the door (2), the short body's wide snow cap (1), and the three distant houses (4 each).
        // 460 in v5.13: the shop (4 layers), the police station (4) and the fire station, its block and
        // its hose tower each a piece of canvas of its own (8, inventory I-607).
        // 463 in v5.14: the fir's star, a piece of its own, the Christmas bulb (`deco_bulb`) and the wreath (`deco_wreath`).
        // 467 in v5.15: Santa's sleigh, two sprites (`santa_sleigh_scene` and `santa_sleigh_trot`), became five (the sleigh
        // and the reindeer's four poses), and the Halloween moon gained its light (`moon_jack_o_lantern_light`).
        assertEquals("467 sprites are expected", 467, all.size)
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
        // 208 in v5.1: the second flower clump reaches its own edges exactly as the first does --
        // the stems stand on the bottom edge, which is what puts them on the ground line.
        // 206 with the palms redrawn in the same release, and it falls for the reason this test
        // exists to record rather than for waste: the live crown and the frosted one moved onto a
        // canvas built around their declared attachment and are now two of the exemptions above.
        // The dead crown reaches its own bottom edge and the trunk its top and bottom, so those
        // two stay in this count and only two names crossed over.
        // 218 in v5.4H: the twelve child carrying frames' fixed layers stand on their bottom edge,
        // which is what puts a walker's feet on the ground line -- exactly as the twelve adult
        // carrying frames and the twenty-four walking ones already do. The pose's 34 region masks
        // do not reach an edge and cannot; see the exemption above.
        // 214 in v5.6F, and it falls for two reasons that are both constructions rather than
        // waste. The seven «Cut-out» vehicle sheets left the count: a body is drawn a unit inside
        // its canvas so the cut edge's wobble has room, and a glass sheet is drawn a unit *outside*
        // the hole it fills so no seam opens behind the paper -- see `VEHICLE_SHEET`. The school
        // put three back: its fixed art, its wall mask and its snow cap each reach an edge of the
        // canvas the piece was cut to, exactly as the other families' do, and only its glass mask
        // does not.
        // 230 in v5.11: the new pieces reach the canvas they were cut to as every piece does -- the
        // short and the tall towers' first tiers and the tall one's second (fixed art and wall mask
        // each), the short body's wide snow cap, and each distant house's fixed art, wall mask and
        // snow. Sixteen; their glass masks and the hall's bay stamp do not.
        // 229 in v5.12: the bar with the signboard's glass mask no longer reaches its canvas's left
        // edge. Its leftmost glass was the small upstairs window at x -24, cut to the edge of the
        // glass layer; that window is gone (it sat on the lantern, inventory I-509), the two upstairs
        // windows stand right of the lantern, and the lantern's own pane is now the leftmost glass,
        // inside the margin. The fourteen other layers the windows of v5.12 redrew reach, or do not
        // reach, an edge as they did.
        // 242 in v5.13: thirteen of the sixteen new layers reach the canvas they were cut to, as every
        // piece's do; the fire station's wall mask and the police station's two masks do not -- their
        // weights stop short of the canvas their fixed art fills (the registration of the exemption above).
        // 244 in v5.14: the fir A's star reaches its canvas's foot, the Christmas bulb all four of its edges and the
        // wreath's bow the foot of its canvas; the fir's snow, trimmed to whole units, reaches none and is declared above
        // (the fir reaches its own, as the old one did).
        // 245 in v5.15: the old sleigh's two frames reached their left edge; of the five pieces that replace them, three of
        // the reindeer's poses reach an edge of the canvas their units trim them to and the sleigh and the first pose
        // do not, and the moon's light keeps its guard pixel -- the generated pieces' exemption above.
        assertEquals("245 of them reach a canvas edge", 245, touching)
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
        // `(.*_)?` and not `.*_`: the school's figure is one piece with no sub-name, so its
        // layers are `school_fx` and `school_mg` with nothing between -- and so are the shop's and the
        // two stations' since v5.13, whose production names are their pieces' own.
        val NEIGHBOURHOOD_LAYER = Regex("""(house|tower|restaurant|bar|school|shop|fire_station|police_station)_(.*_)?(fx|mw|mg)""")

        /** v5.15's generated pieces trimmed on the grid, or with a guard pixel: see the exemption where it is read. */
        val GRID_TRIMMED = Regex("""santa_sleigh|santa_reindeer_\d|moon_jack_o_lantern_light""")

        /**
         * A v5.6F vehicle sheet: a body's paper, a body's glass, or the appliance's.
         *
         * **The margin is the frame the whole fleet is registered in, and it is the same kind of
         * exemption as the neighbourhood's.** Every vehicle sprite is authored in one coordinate
         * system -- the road at y=37, the nose and tail at the body's own half-length -- and every
         * one of them is blitted at its own viewBox minimum, so sixteen PNGs and about thirty
         * constants in `CarShell` and `SceneObjectRenderer` all read the same numbers. «Cut-out»
         * is what put a margin round them: a body's shell is drawn a unit inside its canvas so the
         * cut edge's wobble has room, and a glass sheet is *deliberately* half a unit larger than
         * the hole it fills on every side plus half a unit of canvas, because the sheet lies behind
         * the paper and a seam would open the moment the two were the same size. Cropping either
         * one would move its origin and every number written against that frame.
         *
         * `firetruck_body` is in this class and carries more than the registration needs: it is
         * authored on a fixed 104x60 canvas and its ink stops 4 units short of the right edge and
         * 6.67 short of the bottom, which is 30 240 B of decoded memory a trailing crop would give
         * back with no origin to compensate. Left as delivered, on purpose: the artwork is the one
         * the maintainer chose from the proposal round's photographs, and 30 240 B do not justify
         * an asset change.
         */
        val VEHICLE_SHEET = Regex("""(car_body|car_window|firetruck_body).*""")
    }

    /**
     * **The grid-trimmed pieces' exemption, held to what it says** (v5.15C2, inventory I-975): the exemption above takes
     * them out of both checks, so a margin grown past the grid's would pass there. The sleigh's five pieces keep 0 to 2 px
     * between their ink and the unit's edge on every side; the moon's light keeps its guard pixel at its left and top and
     * 1 to 3 px at its right and bottom, where its box was rounded up to the grid.
     */
    @Test
    fun `the grid-trimmed pieces keep only the grid's margin`() {
        val seen = mutableListOf<String>()
        for (file in sprites()) {
            val name = file.nameWithoutExtension
            if (!name.matches(GRID_TRIMMED)) continue
            seen += name
            val (left, top, right, bottom) = margins(ImageIO.read(file))
            if (name == "moon_jack_o_lantern_light") {
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
        assertEquals(6, seen.size)
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
