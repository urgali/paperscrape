package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * The occupants against their glass and against the pedestrians, measured off the artwork.
 *
 * rc2 rewrote this file along with the rule it tests (the height table replacing the pane
 * share); rc4 rewrote the artwork under it: the maintainer chose one human language for the
 * scene, so the occupants are frontal busts again -- the pedestrians' own face with a seatbelt
 * -- and this file now measures the `head_car` family where it measured the profiles. What it
 * asserts is unchanged in kind and unweakened in number:
 *
 *  1. **The table is real**: [SceneObjectRenderer.PERSON_HEAD_SPRITE_UNITS] must be what the
 *     walking artwork actually measures, or every occupant derives from a fiction.
 *  2. **The parity holds in the artwork**: a frontal face, drawn at the occupant scale, must
 *     measure what a pedestrian's face does in scene metres, within the +/-10% band of the
 *     acceptance criterion. Measured off the PNGs' own skin pixels, not off the constants.
 *
 *  2b. **And winter is no longer exempt.** rc2 exempted it, and rc4 renewed the exemption at
 *     "0.52 and 0.63 of a pedestrian's visible face". rc5 re-measured it and found the exemption
 *     was comparing the wrong pair: those two numbers are a *winter* bust's visible skin against
 *     a **summer** walker's, and a scarf covers a chin whichever figure wears it. Compared
 *     winter against winter, on the landmark a viewer actually reads -- the crown of the hat
 *     down to the chin, the whole head with its headwear -- the ratios are 0.905 and 0.964, both
 *     inside the same +/-10% band the summer faces are held to. What remains outside the band is
 *     the *visible skin* ratio, and it goes both ways (0.88 for the man, 1.58 for the woman),
 *     which no scale error can produce: the woman's winter walking artwork hides more of her
 *     face than her winter bust does. That is a coverage difference between two drawings, not a
 *     size difference, and it is asserted as such below rather than waived.
 *  3. **The air band holds**: every seasonal member leaves 10-25% of its pane above its head,
 *     with and without the winter hat, on the sedan and on the appliance.
 *  4. **A child is 18/20 of an adult**: the family's crown-to-chin heights measure
 *     [SceneObjectRenderer.HEAD_CAR_HEAD_UNITS] for the adults and 90% of it for the children,
 *     so the one scale constant seats everybody at their own size.
 *  5. **The frontal coverage is the pedestrians' coverage**: for every family x season x skin
 *     combination the walkers ship, the corresponding vehicle head exists on disk -- the rc4
 *     criterion that a person in a car and a person on the pavement are the same cast.
 *
 * None of these reduce to `x == x`: every number on the measured side comes out of a PNG.
 */
class OccupantHeadFitTest {

    // ------------------------------------------------------------------ 1. the table's inputs

    @Test
    fun `the pedestrian head constant is what the walking artwork measures`() {
        // Crown of the hair to the jaw, off person_man_summer_walk0: the topmost hair row and the
        // bottom of the face's skin blob. The walk canvas is 123x255 at 3 px per unit.
        val image = ImageIO.read(File(drawableDir, "person_man_summer_walk0.png"))
        val hairTop = rowsMatching(image) { r, g, b -> near(r, g, b, 0x2B, 0x2A, 0x33) }.first()
        val faceRows = rowsMatching(image) { r, g, b -> near(r, g, b, 0xDC, 0xA9, 0x7C) }
        // The face blob is the first run of skin rows; hands start after a gap.
        var faceBottom = faceRows.first()
        for (y in faceRows) {
            if (y > faceBottom + 3) break
            faceBottom = y
        }
        val headUnits = (faceBottom - hairTop + 1) / 3f
        assertEquals(
            "PERSON_HEAD_SPRITE_UNITS vs the artwork ($hairTop..$faceBottom px)",
            SceneObjectRenderer.PERSON_HEAD_SPRITE_UNITS,
            headUnits,
            0.5f,
        )
    }

    @Test
    fun `the occupant scales are the table rule and nothing else`() {
        // The shape of the rule, so a fourth hand-tuned scale cannot reappear: seat-fitted table
        // head, in the vehicle's own units, over the artwork's adult head.
        val tableHeadMetres = SceneSpace.PERSON_METRES_TALL *
            SceneObjectRenderer.PERSON_HEAD_SPRITE_UNITS / SceneSpace.PERSON_SPRITE_UNITS_TALL
        assertEquals(
            "sedan occupant scale",
            SceneObjectRenderer.OCCUPANT_SEATED_FIT * tableHeadMetres /
                (SceneSpace.CAR_METRES_TALL / SceneSpace.CAR_SPRITE_UNITS_TALL) /
                SceneObjectRenderer.HEAD_CAR_HEAD_UNITS,
            SceneObjectRenderer.CAR_OCCUPANT_SCALE,
            0.0001f,
        )
        assertEquals(
            "appliance occupant scale",
            SceneObjectRenderer.OCCUPANT_SEATED_FIT * tableHeadMetres /
                (SceneSpace.FIRE_TRUCK_METRES_TALL / SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL) /
                SceneObjectRenderer.HEAD_CAR_HEAD_UNITS,
            SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE,
            0.0001f,
        )
        // And the seat fit itself stays inside the acceptance band's own tolerance.
        assertTrue(
            "OCCUPANT_SEATED_FIT within +/-10% of the standing head",
            SceneObjectRenderer.OCCUPANT_SEATED_FIT in 0.9f..1.1f,
        )
    }

    // ------------------------------------------------------------------ 2. face parity

    @Test
    fun `a summer occupant's face measures a pedestrian's face in scene metres`() {
        // The pedestrian's face, off the walking artwork, in metres.
        val walk = ImageIO.read(File(drawableDir, "person_man_summer_walk0.png"))
        val walkFaceRows = rowsMatching(walk) { r, g, b -> near(r, g, b, 0xDC, 0xA9, 0x7C) }
        var walkFaceBottom = walkFaceRows.first()
        for (y in walkFaceRows) {
            if (y > walkFaceBottom + 3) break
            walkFaceBottom = y
        }
        val pedestrianFaceMetres = (walkFaceBottom - walkFaceRows.first() + 1) / 3f *
            SceneSpace.PERSON_METRES_TALL / SceneSpace.PERSON_SPRITE_UNITS_TALL

        // Every summer frontal face, in metres, through both vehicle scales. Winter is exempt
        // deliberately -- see the class comment: beanie over the forehead, scarf over the chin.
        for ((name, skin) in listOf(
            "person_man_summer_head_car_skin1" to intArrayOf(0xDC, 0xA9, 0x7C),
            "person_woman_summer_head_car_skin0" to intArrayOf(0xF0, 0xC9, 0xA6),
        )) {
            val face = faceHeightUnits(name, skin)
            for ((vehicle, scale, metresPerUnit) in listOf(
                Triple("sedan", SceneObjectRenderer.CAR_OCCUPANT_SCALE, SceneSpace.CAR_METRES_TALL / SceneSpace.CAR_SPRITE_UNITS_TALL),
                Triple(
                    "appliance",
                    SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE,
                    SceneSpace.FIRE_TRUCK_METRES_TALL / SceneSpace.FIRE_TRUCK_SPRITE_UNITS_TALL,
                ),
            )) {
                val occupantFaceMetres = face * scale * metresPerUnit
                val ratio = occupantFaceMetres / pedestrianFaceMetres
                assertTrue(
                    "$name in the $vehicle: face ${"%.3f".format(occupantFaceMetres)} m vs " +
                        "pedestrian ${"%.3f".format(pedestrianFaceMetres)} m -- ratio ${"%.3f".format(ratio)}",
                    ratio in 0.9f..1.1f,
                )
            }
        }
    }

    /**
     * **rc5: the winter head, hat and scarf and all, reads at the winter pedestrian's scale.**
     *
     * The criterion the maintainer asked for in as many words, and the exemption it replaces.
     * The landmark is the same on both drawings and needs no palette knowledge beyond the skin:
     * the top of the content -- which for a winter figure is the crown of the hat -- down to the
     * chin, where the face's contiguous run of skin ends. That is the head block a viewer sees.
     *
     * Both figures are measured in scene metres through their own projections, so nothing here
     * is a comparison of pixels: the walker through
     * [SceneSpace.PERSON_METRES_TALL]/[SceneSpace.PERSON_SPRITE_UNITS_TALL], the bust through
     * [SceneObjectRenderer.CAR_OCCUPANT_SCALE] and the car's own metres per unit.
     *
     * The second assertion is the one that keeps this honest: the visible-skin ratio is recorded
     * as a fact and bounded loosely, because it is a property of how much face each drawing
     * leaves uncovered. Bounding it tightly would be asserting that two winter outfits cover the
     * same amount of chin, which is not a scale criterion and not something the scene needs.
     */
    @Test
    fun `a winter occupant's head with its headwear measures a winter pedestrian's`() {
        for ((who, skin) in listOf("man" to SKIN_OF["man"]!!, "woman" to SKIN_OF["woman"]!!)) {
            val walkBlock = headBlockMetres("person_${who}_winter_walk0", skin, PEDESTRIAN_METRES_PER_UNIT)
            val carBlock = headBlockMetres(carHeadFile(who, "winter"), skin, occupantMetresPerUnit())
            val ratio = carBlock / walkBlock
            assertTrue(
                "$who in winter: the occupant's head with headwear is ${"%.4f".format(carBlock)} m " +
                    "against the pedestrian's ${"%.4f".format(walkBlock)} m -- ratio " +
                    "${"%.3f".format(ratio)}, outside the +/-10% band",
                ratio in 0.9f..1.1f,
            )
            // Recorded, not waived: the same two figures' *visible* skin, which differs because
            // the two drawings cover different amounts of face. It runs 0.88 (man) to 1.58
            // (woman); a band that admits both is a band that says "this is not a size".
            val walkFace = faceHeightUnits("person_${who}_winter_walk0", skin) * PEDESTRIAN_METRES_PER_UNIT
            val carFace = faceHeightUnits(carHeadFile(who, "winter"), skin) * occupantMetresPerUnit()
            val faceRatio = carFace / walkFace
            assertTrue(
                "$who in winter: visible-skin ratio ${"%.3f".format(faceRatio)} -- if this ever " +
                    "leaves 0.5..2.0 the artwork's coverage has changed, not its scale",
                faceRatio in 0.5f..2.0f,
            )
        }
    }

    // ------------------------------------------------------------------ 3. the air band

    @Test
    fun `every occupant leaves the right band of air above the head, hat or no hat`() {
        // Content height off each PNG's alpha box, times the vehicle scale, against the pane.
        //
        // **v4.19 splits the band by family, because children now ride.** A child's bust is drawn
        // shorter than an adult's inside the same canvas -- that is what makes a child read as a
        // child -- so a child seated in the same pane necessarily leaves *more* air above their
        // head. That is correct: a child sits lower in a car. Holding them to the adults' ceiling
        // would mean either scaling children up to adult size or shrinking the pane until the
        // adults hit the roof, and both are the v4.18 mistake of bending the artwork to a number.
        //
        // The floor is the one that matters and it is the same for everybody: 10%, below which a
        // head touches the roof line. The ceiling says the cabin is not a fishbowl, and a child
        // is allowed the extra 5 points their own proportions produce.
        for (name in HEAD_CAR_FAMILY) {
            val isChild = "boy" in name || "girl" in name
            val ceiling = if (isChild) 0.30f else 0.25f
            val content = contentHeightUnits(name)
            val sedanAir = 1f - content * SceneObjectRenderer.CAR_OCCUPANT_SCALE /
                SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS
            assertTrue(
                "$name in a car leaves ${"%.1f".format(sedanAir * 100)}% of air, " +
                    "outside 10%..${"%.0f".format(ceiling * 100)}%",
                sedanAir in 0.10f..ceiling,
            )
            if ("man" in name || "woman" in name) {
                val cabAir = 1f - content * SceneObjectRenderer.FIRE_TRUCK_OCCUPANT_SCALE /
                    SceneObjectRenderer.FIRE_TRUCK_GLASS_HEIGHT_UNITS
                assertTrue(
                    "$name in the appliance leaves ${"%.1f".format(cabAir * 100)}% of air",
                    cabAir in 0.10f..0.25f,
                )
            }
        }
    }

    // ------------------------------------------------------------------ 4. a child is 18/20

    @Test
    fun `adult heads measure the constant and child heads measure ninety percent of it`() {
        // Crown of the hair to the chin: the content top and the bottom of the head's skin
        // blob, per member, in the sprite's own units. Summer members only, for the same reason
        // parity is summer-only: the winter artwork wears its headwear over the crown and its
        // scarf or hood over the chin, so neither landmark is the bare head's (the winter
        // members' total height is pinned by the air-band test above instead).
        for (name in HEAD_CAR_FAMILY.filter { "summer" in it }) {
            val image = ImageIO.read(File(drawableDir, "$name.png"))
            val skin = SKIN_OF.entries.first { name.startsWith("person_${it.key}_") }.value
            val crown = contentTopUnits(name)
            // The chin ends the face's contiguous run of skin rows: stray anti-aliased blends
            // further down (a belt edge meeting a warm dress reads as skin within the colour
            // tolerance) are not the chin, exactly as the walker's face measurement stops at
            // the first gap before the hands.
            val skinRows = rowsMatching(image) { r, g, b -> near(r, g, b, skin[0], skin[1], skin[2]) }
            var chinRow = skinRows.first()
            for (y in skinRows) {
                if (y > chinRow + 3) break
                chinRow = y
            }
            val chin = chinRow / 3f
            val head = chin - crown
            val expected = if ("boy" in name || "girl" in name) {
                SceneObjectRenderer.HEAD_CAR_HEAD_UNITS * 0.9f
            } else {
                SceneObjectRenderer.HEAD_CAR_HEAD_UNITS
            }
            assertEquals("$name crown-to-chin", expected, head, 1.5f)
        }
    }

    // ------------------------------------------------------------------ 5. coverage parity

    @Test
    fun `every family, season and skin the pedestrians have exists as a vehicle head`() {
        // The rc4 criterion verbatim: the walkers define the cast, the vehicle heads must cover
        // it. Skin variants are the generated _skin0.._skin2 the walkers rotate through; the
        // base sprite is each character's own tone and ships alongside, for both sets alike.
        for (who in listOf("man", "woman", "boy", "girl")) {
            for (season in listOf("summer", "winter")) {
                val walkBase = "person_${who}_${season}_walk0"
                assertTrue("$walkBase.png missing", File(drawableDir, "$walkBase.png").isFile)
                val carBase = "person_${who}_${season}_head_car"
                // v4.19 deleted the four adult base drawings and v4.20 the two boy ones --
                // duplicates of one of their own skin copies that no draw path could reach -- so
                // coverage is asserted on the tone files, which are what the renderer actually
                // blits. See `carHeadFile`.
                assertTrue(
                    "${carHeadFile(who, season)}.png missing: the pedestrians offer " +
                        "$who/$season and the vehicles do not",
                    File(drawableDir, "${carHeadFile(who, season)}.png").isFile,
                )
                for (skin in 0..2) {
                    if (File(drawableDir, "${walkBase}_skin$skin.png").isFile) {
                        assertTrue(
                            "${carBase}_skin$skin.png missing: pedestrians carry this tone and " +
                                "the vehicles do not",
                            File(drawableDir, "${carBase}_skin$skin.png").isFile,
                        )
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ plumbing

    /** Metres per unit for a walking figure and for a seated bust, from the size table. */
    private fun occupantMetresPerUnit(): Float =
        SceneSpace.CAR_METRES_TALL / SceneSpace.CAR_SPRITE_UNITS_TALL *
            SceneObjectRenderer.CAR_OCCUPANT_SCALE

    /**
     * Crown of the headwear down to the chin, in the sprite's own units times [metresPerUnit].
     *
     * The crown is the content top, which for a winter figure is the top of the hat; the chin
     * ends the face's first contiguous run of skin rows, the same rule the walker's own face
     * measurement uses so a hand further down cannot be mistaken for a jaw.
     */
    private fun headBlockMetres(name: String, skin: IntArray, metresPerUnit: Float): Float {
        val image = ImageIO.read(File(drawableDir, "$name.png"))
        val crown = contentTopUnits(name)
        val rows = rowsMatching(image) { r, g, b -> near(r, g, b, skin[0], skin[1], skin[2]) }
        var chin = rows.first()
        for (y in rows) {
            if (y > chin + 3) break
            chin = y
        }
        return ((chin + 1) / 3f - crown) * metresPerUnit
    }

    private fun faceHeightUnits(name: String, skin: IntArray): Float {
        val image = ImageIO.read(File(drawableDir, "$name.png"))
        val rows = rowsMatching(image) { r, g, b -> near(r, g, b, skin[0], skin[1], skin[2]) }
        return (rows.last() - rows.first() + 1) / 3f
    }

    private fun contentHeightUnits(name: String): Float {
        val image = ImageIO.read(File(drawableDir, "$name.png"))
        var top = -1
        var bottom = -1
        for (y in 0 until image.height) {
            var opaque = false
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr 24 != 0) { opaque = true; break }
            }
            if (opaque) { if (top < 0) top = y; bottom = y }
        }
        require(top >= 0) { "$name is fully transparent" }
        return (bottom - top + 1) / 3f
    }

    private fun contentTopUnits(name: String): Float {
        val image = ImageIO.read(File(drawableDir, "$name.png"))
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr 24 != 0) return y / 3f
            }
        }
        error("$name is fully transparent")
    }

    private fun rowsMatching(image: java.awt.image.BufferedImage, match: (Int, Int, Int) -> Boolean): List<Int> {
        val rows = mutableListOf<Int>()
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val p = image.getRGB(x, y)
                if (p ushr 24 < 200) continue
                if (match((p shr 16) and 0xFF, (p shr 8) and 0xFF, p and 0xFF)) {
                    rows.add(y)
                    break
                }
            }
        }
        require(rows.isNotEmpty()) { "no matching rows" }
        return rows
    }

    private fun near(r: Int, g: Int, b: Int, tr: Int, tg: Int, tb: Int) =
        kotlin.math.abs(r - tr) <= 10 && kotlin.math.abs(g - tg) <= 10 && kotlin.math.abs(b - tb) <= 10

    private val drawableDir: File by lazy {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "${prefix}src/main/res/drawable-nodpi")
                if (candidate.isDirectory) return@lazy candidate
            }
            dir = dir.parentFile
        }
        error("could not locate src/main/res/drawable-nodpi from ${File(".").absolutePath}")
    }

    private companion object {
        val HEAD_CAR_FAMILY = listOf(
            "person_man_summer_head_car_skin1", "person_man_winter_head_car_skin1",
            "person_woman_summer_head_car_skin0", "person_woman_winter_head_car_skin0",
            "person_boy_summer_head_car_skin2", "person_boy_winter_head_car_skin2",
            "person_girl_summer_head_car", "person_girl_winter_head_car",
        )

        /** Metres per unit of a walking figure's own canvas, from the size table. */
        const val PEDESTRIAN_METRES_PER_UNIT =
            SceneSpace.PERSON_METRES_TALL / SceneSpace.PERSON_SPRITE_UNITS_TALL

        val SKIN_OF = mapOf(
            "man" to intArrayOf(0xDC, 0xA9, 0x7C),
            "woman" to intArrayOf(0xF0, 0xC9, 0xA6),
            "boy" to intArrayOf(0xA9, 0x71, 0x4B),
            "girl" to intArrayOf(0xEF, 0xB9, 0x94),
        )

        /**
         * Which `_skinN` file carries each family's own tone -- the one the deleted base drawing
         * used to be.
         *
         * v4.19 removed the four adult `person_*_head_car` bases and v4.20 the two boy ones: each
         * was byte-identical in pixels to one of its own skin copies and no draw path could reach
         * it, so together they were 446 688 B of the decoded-sprite budget spent on duplicates
         * (item 7 of `BACKLOG_v4_19.md`, closed in `BACKLOG_v4_20.md`). The measurements here move
         * to the surviving copy rather than to an arbitrary tone, which is why the map is not
         * simply skin0 everywhere: the man's base was skin1, the woman's skin0 and the boy's
         * skin2. Verified pixel-by-pixel against the deleted files before they were removed --
         * v4.20 re-measured its two at zero differing pixels, and re-derived the other tones from
         * the heir to check they come back byte-identical.
         */
        val BASE_SKIN_OF = mapOf("man" to 1, "woman" to 0, "boy" to 2)

        /**
         * The shipped file a family's own tone lives in.
         *
         * Only the **girl's** base drawings still ship, and for a reason worth keeping: hers is
         * not a duplicate of any of her tones. Her own painted skin is a fourth colour, so
         * regenerating her other tones from `_skin0` instead moves 165-406 anti-aliased pixels
         * against zero for every base that was retired. Her measurements therefore stay on the
         * base; every other family's move to the copy its base was byte-identical to.
         */
        fun carHeadFile(who: String, season: String): String =
            BASE_SKIN_OF[who]?.let { "person_${who}_${season}_head_car_skin$it" }
                ?: "person_${who}_${season}_head_car"
    }
}
