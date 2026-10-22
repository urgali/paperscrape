package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The distant houses on the mountains (v5.11, inventory I-407; the maintainer's choices of
 * 2026-10-06): off as every theme ships, a stable share of places, all three drawings, the size rule
 * of [SceneSpace.distantHousePixelsTall], and every drawn detail at two pixels or more where they are
 * drawn.
 */
class DistantHousesTest {

    private companion object {
        /**
         * The BV6600's screen, a larger one, the tallest phones' (21:9: the mountains, sized by the screen's height, crowd a
         * tile twice its width most there) and a tablet on its side (v5.14D): the rule is worked out from the screen.
         */
        val SCREENS = listOf(720f to 1440f, 1080f to 2340f, 1080f to 2520f, 2560f to 1600f)

        /** The mountains as each theme ships them (null), then both layers at 10 to 100 %. */
        val MOUNTAIN_AMOUNTS: List<Float?> = listOf<Float?>(null) + (1..10).map { it / 10f }
    }

    @Test
    fun `every theme ships them off, and data that never knew them reads them off`() {
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            assertFalse(theme.id, c.distantHouses.visible)
            assertEquals(theme.id, DistantHousesConfig.STARTING_DENSITY, c.distantHouses.density, 0f)
        }
        val old = JSONObject(defaultCustomizationFor("autumn").toJson().toString()).apply { remove("distantHouses") }
        assertEquals(DistantHousesConfig.OFF, sceneCustomizationFromJson(old).distantHouses)
        val on = defaultCustomizationFor("autumn").copy(distantHouses = DistantHousesConfig(true, 0.73f))
        assertEquals(on.distantHouses, sceneCustomizationFromJson(JSONObject(on.toJson().toString())).distantHouses)
    }

    /** Lowering the amount takes houses away and moves none; raising it only adds -- whichever side a mountain starts from. */
    @Test
    fun `the amount keeps the same places, and lowering it only takes houses away`() {
        val amounts = (0..20).map { it / 20f }
        for (seed in listOf(1, -7, 31_337, "autumn".hashCode(), "city".hashCode())) {
            for (mountain in 0 until PaperRenderer.MOUNTAIN_POOL_SIZE) {
                val s = DistantHouses.seedFor(seed, mountain)
                for (leftFirst in listOf(true, false)) {
                    for (slot in DistantHouses.SLOTS.indices) {
                        var was = false
                        for (amount in amounts) {
                            val now = DistantHouses.stands(s, slot, amount, leftFirst)
                            assertTrue("a house went away as the amount rose", now || !was)
                            was = now
                        }
                        assertFalse("a house at 0 %", DistantHouses.stands(s, slot, 0f, leftFirst))
                        assertTrue("a place empty at 100 %", DistantHouses.stands(s, slot, 1f, leftFirst))
                    }
                    // Above 0 every mountain carries one, so the row never reads on over none (8.7).
                    for (amount in listOf(0.001f, 0.01f, 0.05f)) {
                        assertTrue(
                            "a mountain with no house at $amount",
                            DistantHouses.SLOTS.indices.any { DistantHouses.stands(s, it, amount, leftFirst) },
                        )
                    }
                }
            }
        }
    }

    /**
     * **How many a mountain carries** (v5.14): `floor(amount x 5 + coin)`, one at least above 0 -- two or three at
     * 50 %, two and a half on average as the places' coins gave until v5.14, all five at 100 % -- never fewer as the
     * amount rises; and the places it stands in are exactly the first that many of its order.
     */
    @Test
    fun `a mountain carries two or three at half, five at full, never fewer as the amount rises`() {
        var sum = 0
        var mountains = 0
        for (seed in (0 until 400).map { it * 7_919 - 31_337 }) {
            val s = DistantHouses.seedFor(seed, seed % PaperRenderer.MOUNTAIN_POOL_SIZE)
            assertEquals(0, DistantHouses.count(s, 0f))
            assertEquals(DistantHouses.SLOTS.size, DistantHouses.count(s, 1f))
            val half = DistantHouses.count(s, 0.5f)
            assertTrue("$half at 50 %", half == 2 || half == 3)
            sum += half
            mountains++
            var was = 0
            for (k in 1..100) {
                val n = DistantHouses.count(s, k / 100f)
                assertTrue("fewer houses at ${k / 100f}", n >= was && n >= 1)
                was = n
                for (leftFirst in listOf(true, false)) {
                    assertEquals(n, DistantHouses.SLOTS.indices.count { DistantHouses.stands(s, it, k / 100f, leftFirst) })
                }
            }
        }
        val mean = sum.toFloat() / mountains
        assertTrue("$mean houses a mountain on average at 50 %", mean in 2.3f..2.7f)
    }

    /**
     * **On both sides** (v5.14, the maintainer: *«case lontane su montagne - sembra che siano solo a sinistra della
     * montagna»*, 2026-10-14; the correction chosen with *«si a tutte»*, 2026-10-15), **and in sight** (v5.14C2, *«Spostala
     * dove si vede»*, 2026-10-16): a mountain's order is one side, the other, the top, then each side's second place, a
     * place a later mountain of the layer covers giving way to its side's other place -- so a mountain with two houses or
     * more has one **in sight** on each side, at every amount, on every mountain of every built-in theme's two layers, with
     * the themes' mountains and with both layers at 10 to 100 %, on every screen of [SCREENS]; and a mountain
     * with one has it on the side it starts from, every other mountain the other, wherever that side has a place in sight.
     * The counts left / top / right **in sight**, theme by theme at 50 % and 100 %, the themes' mountains and both layers at
     * 100 %, are printed (`DISTANT_HOUSES`): the report's table.
     */
    @Test
    fun `every mountain with two houses or more has them on both sides, in sight`() {
        for ((w, h) in SCREENS) for (mountains in MOUNTAIN_AMOUNTS) for (theme in ThemeCatalog.ALL) {
            for (layer in layersOf(theme, mountains, w, h)) {
                for (amount in listOf(0.05f, 0.25f, 0.5f, 0.75f, 1f)) {
                    val sides = IntArray(3)
                    // Along the strip, as the renderer draws it: copies -2 .. 2 of the tile, its mountains in order;
                    // the counts printed are copy 0's, the tile on screen at scroll 0.
                    var previousStart: Boolean? = null
                    for (copy in -2..2) for (shown in 0 until layer.count) {
                        val i = layer.indices[shown]
                        val leftFirst = DistantHouses.startsLeft(copy, shown, layer.count, layer.front)
                        if (previousStart != null) {
                            assertTrue("${theme.id} ${layer.name}: copy $copy mountain $i starts on its neighbour's side", leftFirst != previousStart)
                        }
                        previousStart = leftFirst
                        val s = layer.seeds[i]
                        val hidden = layer.hidden(i)
                        val here = IntArray(3)
                        for (slot in DistantHouses.SLOTS.indices) {
                            if (!DistantHouses.stands(s, slot, amount, leftFirst, hidden)) continue
                            val along = DistantHouses.along(s, slot)
                            val side = if (slot < DistantHouses.TOP) 0 else if (slot > DistantHouses.TOP) 2 else 1
                            assertTrue("${theme.id}: place $slot at $along is not on its side", (side == 0) == (along < -0.1f) && (side == 2) == (along > 0.1f))
                            assertTrue("${theme.id} ${layer.name} mountain $i: a house on a covered place $slot", hidden and (1 shl slot) == 0)
                            here[side]++
                        }
                        val n = here.sum()
                        val what = "${w.toInt()}x${h.toInt()} ${theme.id} mountains ${mountains ?: "as the theme ships"} ${layer.name} mountain $i at $amount: ${here.toList()}"
                        if (n >= 2) assertTrue(what, here[0] > 0 && here[2] > 0)
                        val startSideFree = if (leftFirst) (hidden and 0b00011) != 0b00011 else (hidden and 0b11000) != 0b11000
                        if (n == 1 && startSideFree) assertEquals("$what: the one house on the side the mountain starts from", if (leftFirst) 1 else 0, here[0])
                        if (copy == 0) for (k in 0..2) sides[k] += here[k]
                    }
                    if ((amount == 0.5f || amount == 1f) && w == SCREENS[0].first && (mountains == null || mountains == 1f)) {
                        println("DISTANT_HOUSES ${if (mountains != null) "mountains 100% " else ""}${theme.id} ${layer.name} " +
                            "${(amount * 100).toInt()}% in sight left/top/right ${sides[0]}/${sides[1]}/${sides[2]}")
                    }
                }
            }
        }
    }

    /**
     * **No house where a mountain of its layer drawn after it covers any of it** (v5.14C2, ROADMAP A188; the maintainer's
     * *«Spostala dove si vede»*, 2026-10-16). Measured here without [DistantHouses.hiddenPlaces]: each standing house's
     * drawing -- the ink of its own PNG, at its size there -- sampled on a grid of 9 x 9 points, each point tested against
     * the parabola of every mountain of the layer drawn after its own, on the copies around it. On the twelve built-ins,
     * with their mountains and with both layers at 0 to 100 % in steps of 10, the houses at 50 % and 100 %, on every screen
     * of [SCREENS], two copies of the tile (the side rule repeats every two). Before v5.14C2, 2 houses of
     * 454 stood covered with the layers at 80 to 100 % and the houses at 50 % (measured by a probe outside the repository).
     */
    @Test
    fun `no house stands where a later mountain of its layer covers any of it`() {
        var standing = 0
        var checked = 0
        var moved = 0
        for ((w, h) in SCREENS) for (mountains in MOUNTAIN_AMOUNTS + listOf(0f)) for (theme in ThemeCatalog.ALL) {
            for (layer in layersOf(theme, mountains, w, h)) for (amount in listOf(0.5f, 1f)) for (copy in 0..1) {
                for (shown in 0 until layer.count) {
                    val i = layer.indices[shown]
                    val leftFirst = DistantHouses.startsLeft(copy, shown, layer.count, layer.front)
                    val hidden = layer.hidden(i)
                    if (hidden != 0) moved++
                    val mask = DistantHouses.standingMask(layer.seeds[i], amount, leftFirst, hidden)
                    for (slot in DistantHouses.SLOTS.indices) {
                        if (mask and (1 shl slot) == 0) continue
                        standing++
                        val box = layer.houseBox(i, slot)
                        for (gx in 0..8) for (gy in 0..8) {
                            val px = box[0] + (box[1] - box[0]) * gx / 8f
                            val py = box[2] + (box[3] - box[2]) * gy / 8f
                            for (other in layer.indices) {
                                if (other <= i) continue
                                for (t in -1..1) {
                                    assertFalse(
                                        "${w.toInt()}x${h.toInt()} ${theme.id} mountains ${mountains ?: "as the theme ships"} ${layer.name} houses $amount: " +
                                            "the house of mountain $i, place $slot, is under mountain $other",
                                        layer.covers(other, t, px, py),
                                    )
                                    checked++
                                }
                            }
                        }
                    }
                }
            }
        }
        println("DISTANT_HOUSES in sight: $standing houses standing, $checked points of them tested, $moved mountains with a covered place")
        assertTrue("no mountain had a covered place, so the rule was never asked", moved > 0)
    }

    /**
     * **And the other way round** (v5.14D): every place [DistantHouses.hiddenPlaces] gives no house is one a later mountain of
     * its layer does cover -- a point of the house's ink, on the same 9 x 9 grid, under it -- so the rule moves no house it
     * did not have to; **with each theme's own mountains no place is covered**, so there every house stands where the 5.14's
     * order put it (the option the maintainer chose said so: with the themes' mountains nothing changes -- *«Sì, va bene così
     * (consigliato)»*, 2026-10-16); and **every mountain the scene draws carries a house** at every amount
     * above 0 %, no mountain having all five places covered -- the *Distant houses* row reads on over houses. On every screen
     * of [SCREENS], every theme, its mountains and both layers at 0 to 100 %, two copies of the tile.
     */
    @Test
    fun `a place is given no house only where a later mountain covers it, and every mountain keeps one`() {
        var marked = 0
        for ((w, h) in SCREENS) for (mountains in MOUNTAIN_AMOUNTS + listOf(0f)) for (theme in ThemeCatalog.ALL) {
            for (layer in layersOf(theme, mountains, w, h)) for (copy in 0..1) for (shown in 0 until layer.count) {
                val i = layer.indices[shown]
                val what = "${w.toInt()}x${h.toInt()} ${theme.id} mountains ${mountains ?: "as the theme ships"} ${layer.name} mountain $i"
                val hidden = layer.hidden(i)
                if (mountains == null) assertEquals("$what: a place covered with the theme's own mountains", 0, hidden)
                assertTrue("$what: every place covered", hidden != (1 shl DistantHouses.SLOTS.size) - 1)
                val leftFirst = DistantHouses.startsLeft(copy, shown, layer.count, layer.front)
                for (amount in listOf(0.01f, 0.25f, 0.5f, 0.75f, 1f)) {
                    assertTrue("$what at $amount: no house", DistantHouses.standingMask(layer.seeds[i], amount, leftFirst, hidden) != 0)
                }
                if (copy != 0) continue
                for (slot in DistantHouses.SLOTS.indices) {
                    if (hidden and (1 shl slot) == 0) continue
                    marked++
                    val box = layer.houseBox(i, slot)
                    var under = false
                    for (gx in 0..8) for (gy in 0..8) {
                        val px = box[0] + (box[1] - box[0]) * gx / 8f
                        val py = box[2] + (box[3] - box[2]) * gy / 8f
                        for (other in layer.indices) {
                            if (other <= i) continue
                            for (t in -1..1) if (layer.covers(other, t, px, py)) under = true
                        }
                    }
                    assertTrue("$what: place $slot is given no house, and no later mountain covers its ink", under)
                }
            }
        }
        println("DISTANT_HOUSES covered places, each checked against the ink: $marked")
        assertTrue("no place was covered, so the check was never asked", marked > 0)
    }

    /**
     * **The two covers the twelve themes' mountains never make**, on mountains made up for it: a later mountain over a
     * house's corner and not its middle, and over it from the copy of the tile before -- across the seam, where the
     * renderer draws the layer's last mountain to the left of its first. Neither happens with the built-in themes' layers
     * (their covered houses are covered in the middle, within the copy), so without these a rule that looked only at a
     * house's middle, or only at the copy it stands on, would pass every test above. Wherever the grid of the house's ink
     * finds a point under the later mountain, the place is covered.
     */
    @Test
    fun `a place is covered by a corner of a later mountain, and across the tile's seam`() {
        val h = 1440f
        val tileWidth = 1440f
        val base = h * SceneSpace.GROUND_SOLID_TOP_Y_FRACTION
        val n = PaperRenderer.MOUNTAIN_POOL_SIZE
        var corners = 0
        var seams = 0
        for (seed in 0 until 30) for (step in 0 until 160) for (acrossSeam in listOf(false, true)) {
            val cx = FloatArray(n); val w = FloatArray(n); val ht = FloatArray(n); val seeds = IntArray(n)
            // The first mountain near the tile's start; the later one sweeping to its right on the same copy, or -- wider
            // and taller -- near the tile's end, which the copy before puts over the first's left side.
            cx[0] = if (acrossSeam) 100f else 260f; w[0] = if (acrossSeam) 300f else 320f; ht[0] = 170f
            seeds[0] = DistantHouses.seedFor(seed, 0)
            cx[1] = if (acrossSeam) tileWidth - 160f + step * 1f else 300f + step * 3f
            w[1] = if (acrossSeam) 420f else 300f; ht[1] = if (acrossSeam) 220f else 130f; seeds[1] = DistantHouses.seedFor(seed, 1)
            val layer = Layer("made up", false, listOf(0, 1), cx, w, ht, seeds, base, tileWidth, h)
            val hidden = layer.hidden(0)
            for (slot in DistantHouses.SLOTS.indices) {
                val box = layer.houseBox(0, slot)
                var under = false
                var underHere = false
                for (gx in 0..8) for (gy in 0..8) for (t in -1..1) {
                    if (layer.covers(1, t, box[0] + (box[1] - box[0]) * gx / 8f, box[2] + (box[3] - box[2]) * gy / 8f)) {
                        under = true
                        if (t == 0) underHere = true
                    }
                }
                if (!under) continue
                assertTrue("seed $seed step $step${if (acrossSeam) " across the seam" else ""}: place $slot under the later mountain, not covered", hidden and (1 shl slot) != 0)
                val middle = (-1..1).any { t -> layer.covers(1, t, (box[0] + box[1]) / 2f, (box[2] + box[3]) / 2f) }
                if (!middle) corners++
                if (acrossSeam && !underHere) seams++
            }
        }
        println("DISTANT_HOUSES made-up mountains: $corners places covered at a corner only, $seams across the seam only")
        assertTrue("no corner-only cover made up", corners > 0)
        assertTrue("no cover across the seam made up", seams > 0)
    }

    /**
     * **The order with places covered** ([DistantHouses.standingMask]): never a house on a covered place; with nothing
     * covered the 5.14's own rule; raising the amount only adds houses whatever is covered; a covered place gives way to its
     * side's other place before the top or the other side; and a mountain with a place in sight carries a house above 0 %.
     */
    @Test
    fun `a covered place gives way to its side's other place, and the amount still only adds`() {
        for (seed in (0 until 300).map { it * 7_919 - 31_337 }) {
            val s = DistantHouses.seedFor(seed, seed and 3)
            for (leftFirst in listOf(true, false)) for (hidden in 0 until 32) {
                var was = 0
                for (k in 0..40) {
                    val amount = k / 40f
                    val mask = DistantHouses.standingMask(s, amount, leftFirst, hidden)
                    assertEquals("a house on a covered place", 0, mask and hidden)
                    assertEquals("a house went away as the amount rose", was, mask and was)
                    was = mask
                    if (hidden == 0) {
                        for (slot in DistantHouses.SLOTS.indices) {
                            assertEquals(DistantHouses.rank(s, slot, leftFirst) < DistantHouses.count(s, amount), mask and (1 shl slot) != 0)
                        }
                    }
                    if (amount > 0f && hidden != 0b11111) assertTrue("no house above 0 % with a place in sight", mask != 0)
                    assertEquals(minOf(DistantHouses.count(s, amount), 5 - Integer.bitCount(hidden)), Integer.bitCount(mask))
                }
                // Two houses: one a side wherever both sides have a place in sight.
                val two = (0..100).map { it / 100f }.firstOrNull { DistantHouses.count(s, it) == 2 } ?: continue
                val mask = DistantHouses.standingMask(s, two, leftFirst, hidden)
                val leftFree = hidden and 0b00011 != 0b00011
                val rightFree = hidden and 0b11000 != 0b11000
                if (leftFree && rightFree) assertTrue("two houses, one side: hidden $hidden", mask and 0b00011 != 0 && mask and 0b11000 != 0)
            }
        }
    }

    /**
     * **The box [DistantHouses.hiddenPlaces] takes a house to be** holds its whole drawing: the roofline's ends and
     * [DistantHouses.INK_MARGIN] beyond them, from its top to [DistantHouses.FOOT_INK] below its foot -- measured off each
     * drawing's PNG, its fixed layer and its snow, so a redrawn house wider than its eaves fails here.
     */
    @Test
    fun `the box a covered place is judged by holds each drawing's ink`() {
        for (piece in NeighbourhoodTable.DISTANT_HOUSES) {
            for (part in piece.parts.filter { it.res != 0 && it.role != PartRole.OCCUPANTS }) {
                val name = com.paperscrape.livewallpaper.R.drawable::class.java.fields
                    .first { it.type == Int::class.javaPrimitiveType && it.getInt(null) == part.res }.name
                val ink = inkBox(ImageIO.read(File(drawableDir(), "$name.png")))
                val unit = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
                val left = part.x + ink[0] / unit
                val right = part.x + ink[1] / unit
                val bottom = part.y + ink[3] / unit
                assertTrue("$name reaches $left, left of its box", left >= piece.roofX.first() - DistantHouses.INK_MARGIN)
                assertTrue("$name reaches $right, right of its box", right <= piece.roofX.last() + DistantHouses.INK_MARGIN)
                assertTrue("$name reaches $bottom, below its box", bottom <= DistantHouses.FOOT_INK)
            }
        }
    }

    /** The ink's left, right, top and bottom of an image, in its pixels (right and bottom past the last). */
    private fun inkBox(image: java.awt.image.BufferedImage): IntArray {
        var l = Int.MAX_VALUE; var r = -1; var t = Int.MAX_VALUE; var b = -1
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if ((image.getRGB(x, y) ushr 24) == 0) continue
            l = minOf(l, x); r = maxOf(r, x); t = minOf(t, y); b = maxOf(b, y)
        }
        return intArrayOf(l, r + 1, t, b + 1)
    }

    /**
     * One layer of mountains as `PaperRenderer.drawMountainLayer` lays it out across its tile (its numbers are held to the
     * renderer's own lines below): where each present mountain stands, how wide and tall, the seed of its houses; on a
     * base line of the hill's solid line -- the lake's top edge when there is water -- the front layer 0.015 below.
     */
    private inner class Layer(
        val name: String,
        val front: Boolean,
        val indices: List<Int>,
        val cx: FloatArray,
        val width: FloatArray,
        val height: FloatArray,
        val seeds: IntArray,
        val baseY: Float,
        val tileWidth: Float,
        val screenHeight: Float,
    ) {
        val count get() = indices.size
        private val present = BooleanArray(PaperRenderer.MOUNTAIN_POOL_SIZE).also { p -> indices.forEach { p[it] = true } }

        fun hidden(i: Int): Int = DistantHouses.hiddenPlaces(i, cx, width, height, seeds, present, baseY, tileWidth, screenHeight)

        /** Whether mountain [other], on copy [t] of the tile, covers the point ([px], [py]). */
        fun covers(other: Int, t: Int, px: Float, py: Float): Boolean {
            val half = width[other] / 2f
            val ocx = cx[other] + t * tileWidth
            if (kotlin.math.abs(px - ocx) >= half) return false
            val a = (px - ocx) / half
            return py > baseY - height[other] * (1f - a * a)
        }

        /** The ink of the house at [slot] of mountain [i]: left, right, top, bottom on screen (copy 0). */
        fun houseBox(i: Int, slot: Int): FloatArray {
            val seed = seeds[i]
            val along = DistantHouses.along(seed, slot)
            val surface = DistantHouses.surfaceY(baseY, height[i], along)
            val tall = SceneSpace.distantHousePixelsTall(surface / screenHeight, screenHeight)
            val foot = DistantHouses.footY(surface, DistantHouses.slope(height[i], width[i] / 2f, along), tall)
            val piece = NeighbourhoodTable.DISTANT_HOUSES[DistantHouses.design(seed, slot, NeighbourhoodTable.DISTANT_HOUSES.size)]
            val scale = tall / piece.height
            val px = cx[i] + along * width[i] / 2f
            val ink = INK.getOrPut(piece) {
                val part = piece.parts.first { it.role == PartRole.FIXED }
                val name = com.paperscrape.livewallpaper.R.drawable::class.java.fields
                    .first { it.type == Int::class.javaPrimitiveType && it.getInt(null) == part.res }.name
                val box = inkBox(ImageIO.read(File(drawableDir(), "$name.png")))
                val unit = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
                floatArrayOf(part.x + box[0] / unit, part.x + box[1] / unit, part.y + box[2] / unit, part.y + box[3] / unit)
            }
            return floatArrayOf(px + ink[0] * scale, px + ink[1] * scale, foot + ink[2] * scale, foot + ink[3] * scale)
        }
    }

    private val INK = HashMap<BuildingPiece, FloatArray>()

    /** The two layers of [theme] with its mountains at [mountains] (null: as the theme ships), on a screen [w] x [h]. */
    private fun layersOf(theme: SceneTheme, mountains: Float?, w: Float, h: Float): List<Layer> {
        val c0 = defaultCustomizationFor(theme.id)
        val c = if (mountains == null) c0 else c0.copy(
            mountainsBack = c0.mountainsBack.copy(visible = true, density = mountains),
            mountainsFront = c0.mountainsFront.copy(visible = true, density = mountains),
        )
        val hillTop = SceneSpace.GROUND_SOLID_TOP_Y_FRACTION
        val base = if (c.lake.drawsWater) (h * hillTop - h * 0.16f * c.lake.height.coerceIn(0f, 1f)) / h else hillTop
        val out = mutableListOf<Layer>()
        for ((name, config, salt) in listOf(
            Triple("back", c.mountainsBack, EffectId.MOUNTAINS_BACK),
            Triple("front", c.mountainsFront, EffectId.MOUNTAINS_FRONT),
        )) {
            if (!config.drawsAny) continue
            val front = salt == EffectId.MOUNTAINS_FRONT
            val offset = CandidateThreshold.offsetFor(salt)
            val fallback = CandidateThreshold.fallbackIndexFor(config.density, PaperRenderer.MOUNTAIN_POOL_SIZE, offset)
            val seed = theme.id.hashCode() xor (salt * -0x61c88647)
            val n = PaperRenderer.MOUNTAIN_POOL_SIZE
            val cx = FloatArray(n); val width = FloatArray(n); val height = FloatArray(n); val seeds = IntArray(n)
            val indices = (0 until n).filter { CandidateThreshold.isPresent(it, config.density, offset, fallback) }
            val tileWidth = w * 2f
            val peak = h * if (front) 0.105f else 0.15f
            val baseWidth = h * if (front) 0.175f else 0.25f
            for (i in indices) {
                val tileFractionX = (i + 0.5f) / n + (CandidateNoise.value(seed, i, CandidateNoise.CH_X) - 0.5f) * (1f / n) * 0.5f
                cx[i] = tileFractionX * tileWidth
                height[i] = peak * CandidateNoise.range(seed, i, CandidateNoise.CH_HEIGHT, 0.75f, 1.25f)
                width[i] = baseWidth * CandidateNoise.range(seed, i, CandidateNoise.CH_WIDTH, 0.75f, 1.25f)
                seeds[i] = DistantHouses.seedFor(seed, i)
            }
            out += Layer(name, front, indices, cx, width, height, seeds, h * (base + if (front) 0.015f else 0f), tileWidth, h)
        }
        return out
    }

    /**
     * **The gallery card's houses stand where they show too** (v5.14C2, ROADMAP A188): the card's mountains are fixed, so
     * the rule is asked of every house on every built-in's card with the houses at 30 %, 50 % and 100 %, by day and at
     * night, with the Christmas lights on (the houses' bulbs drawn too; the ink measured is the house's): no part of a house under a later mountain of its own
     * layer -- the wallpaper's rule -- and its middle under no mountain at all drawn after it. The front layer stands in
     * front of the back one on the card as on the wallpaper, where it passes over the back houses as the two scroll: a
     * back house's corner it touches is counted and printed (`DISTANT_HOUSES card`), not held against the card.
     */
    @Test
    fun `the gallery card's houses are not covered by a mountain drawn after them`() {
        var houses = 0
        var corners = 0
        for (theme in ThemeCatalog.ALL) for (amount in listOf(0.3f, 0.5f, 1f)) for (night in listOf(false, true)) {
            val c0 = defaultCustomizationFor(theme.id)
            val c = c0.copy(
                distantHouses = DistantHousesConfig(true, amount),
                christmasBuildingLightsEnabled = true,
                mountainsBack = c0.mountainsBack.copy(visible = true, density = maxOf(c0.mountainsBack.density, 0.5f)),
                mountainsFront = c0.mountainsFront.copy(visible = true, density = maxOf(c0.mountainsFront.density, 0.5f)),
            )
            val peaks = ThemePreviewScenes.forTheme(theme, c, night).peaks
            // The back layer's four, then the front's five (`ThemePreviewScenes.buildPeaks`).
            assertEquals("${theme.id}: the card's mountains", 9, peaks.size)
            val base = ThemePreviewScene.HORIZON_UNITS + 2f
            fun under(p: PreviewPeak, px: Float, py: Float): Boolean {
                if (kotlin.math.abs(px - p.x) >= p.halfWidth) return false
                val a = (px - p.x) / p.halfWidth
                return py > base - (base - p.peakY) * (1f - a * a)
            }
            for ((k, peak) in peaks.withIndex()) for (house in peak.houses) {
                houses++
                val fixed = house.parts.first { !it.isFlat && it.tint == null }
                val name = com.paperscrape.livewallpaper.R.drawable::class.java.fields
                    .first { it.type == Int::class.javaPrimitiveType && it.getInt(null) == fixed.resId }.name
                val ink = inkBox(ImageIO.read(File(drawableDir(), "$name.png")))
                val unit = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
                val left = house.x + house.scale * (fixed.ox + ink[0] / unit)
                val right = house.x + house.scale * (fixed.ox + ink[1] / unit)
                val top = house.y + house.scale * (fixed.oy + ink[2] / unit)
                val bottom = house.y + house.scale * (fixed.oy + ink[3] / unit)
                val what = "${theme.id} at $amount (night $night): the house at x=${house.x}"
                var corner = false
                for ((j, later) in peaks.withIndex()) {
                    if (j <= k) continue
                    val sameLayer = (k < 4) == (j < 4)
                    assertFalse("$what has its middle under the mountain at x=${later.x}", under(later, house.x, (top + bottom) / 2f))
                    for (gx in 0..8) for (gy in 0..8) {
                        val inside = under(later, left + (right - left) * gx / 8f, top + (bottom - top) * gy / 8f)
                        if (sameLayer) assertFalse("$what is under the mountain of its layer at x=${later.x}", inside)
                        if (inside) corner = true
                    }
                }
                if (corner) corners++
            }
        }
        println("DISTANT_HOUSES card: $houses houses on the cards, $corners with a corner behind the front layer")
        assertTrue("no card house was tested", houses > 100)
    }

    /** The layout above is the renderer's: its own lines, read. */
    @Test
    fun `the test's mountains are laid out as the renderer lays them out`() {
        val renderer = File(sourceRoot(), "engine/PaperRenderer.kt").readText()
        val body = renderer.substringAfter("private fun drawMountainLayer(").substringBefore("private fun drawSoftMountain(")
        assertTrue(body.contains("(CandidateNoise.value(seed, i, CandidateNoise.CH_X) - 0.5f) * (1f / MOUNTAIN_POOL_SIZE) * 0.5f"))
        assertTrue(body.contains("mountainTileX[i] = tileFractionX * tileWidth"))
        assertTrue(body.contains("mountainWidth[i] = baseWidth * CandidateNoise.range(seed, i, CandidateNoise.CH_WIDTH, 0.75f, 1.25f)"))
        assertTrue(body.contains("mountainSeed[i] = DistantHouses.seedFor(seed, i)"))
        assertTrue(renderer.contains("theme.id.hashCode() xor (effectOrdinal * -0x61c88647)"))
        assertTrue(renderer.contains("peakHeightFraction = 0.15f, widthOfHeightFraction = 0.25f"))
        assertTrue(renderer.contains("baseYFraction = effectiveBaseYFraction + 0.015f, peakHeightFraction = 0.105f, widthOfHeightFraction = 0.175f"))
        assertTrue(renderer.contains("val bandHeight = screenHeight * 0.16f * lake.height.coerceIn(0f, 1f)"))
    }

    /**
     * **Every other mountain along the whole strip** (v5.14B): with one, two, three or four mountains a tile, on
     * both layers, the mountains in a row across five copies of the tile start from alternate sides -- across the
     * seams too, where a count started again at each tile put two neighbours on one side whenever a tile draws an odd
     * number of them. And the front layer's first mountain starts from the other side than the back layer's.
     */
    @Test
    fun `the side a mountain starts from alternates along the strip, across the seams`() {
        for (perTile in 1..PaperRenderer.MOUNTAIN_POOL_SIZE) for (front in listOf(false, true)) {
            var previous: Boolean? = null
            for (copy in -2..2) for (shown in 0 until perTile) {
                val left = DistantHouses.startsLeft(copy, shown, perTile, front)
                if (previous != null) assertTrue("$perTile a tile, front $front: copy $copy mountain $shown", left != previous)
                previous = left
            }
            assertEquals("the layers start from opposite sides", !front, DistantHouses.startsLeft(0, 0, perTile, front))
        }
    }

    /** The renderer counts the strip as the rule above does: the copy, the mountain's place in the tile, how many a tile. */
    @Test
    fun `the renderer hands each mountain its place along the strip`() {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        var file: java.io.File? = null
        while (dir != null && file == null) {
            file = java.io.File(dir, "app/src/main/kotlin/com/paperscrape/livewallpaper/engine/PaperRenderer.kt").takeIf { it.isFile }
            dir = dir.parentFile
        }
        val body = checkNotNull(file).readText().substringAfter("private fun drawMountainLayer(").substringBefore("private fun drawSoftMountain(")
        assertTrue(body.contains("val tileBias = Math.round((-scrollProgress * screenWidth * parallax - wrappedShift) / tileWidth).toInt()"))
        assertTrue(body.contains("DistantHouses.startsLeft(tileOffset - tileBias, place, perTile, frontLayer)"))
        assertTrue(body.contains("val present = CandidateThreshold.isPresent(i, config.density, effectOffset, fallbackIndex)"))
        assertTrue(body.contains("if (!present) continue\n            perTile++"))
        // And the places a later mountain covers (v5.14C2): worked out from the mountains it has just laid out, handed on.
        assertTrue(body.contains("if (sceneCustomization.distantHouses.drawsAny) hidden.refresh(seed, config.density, baseY, tileWidth)"))
        assertTrue(body.contains("drawDistantHouses(canvas, x, baseY, width, mountainHeight[i], mountainSeed[i], leftFirst, hidden.masks[i], dayPhase.dayBlend)"))
    }

    @Test
    fun `the three drawings are mixed, the two colours and the lit windows dealt per house`() {
        val designs = IntArray(3)
        val colours = IntArray(2)
        var lit = 0
        var total = 0
        for (theme in ThemeCatalog.ALL) {
            for (layer in listOf(EffectId.MOUNTAINS_BACK, EffectId.MOUNTAINS_FRONT)) {
                val layerSeed = theme.id.hashCode() xor (layer * -0x61c88647)
                for (mountain in 0 until PaperRenderer.MOUNTAIN_POOL_SIZE) {
                    val s = DistantHouses.seedFor(layerSeed, mountain)
                    for (slot in DistantHouses.SLOTS.indices) {
                        designs[DistantHouses.design(s, slot, 3)]++
                        colours[DistantHouses.colourVariant(s, slot)]++
                        if (DistantHouses.isLit(s, slot)) lit++
                        total++
                    }
                }
            }
        }
        for (d in designs) assertTrue("each drawing stands: ${designs.toList()}", d > total / 5)
        for (c in colours) assertTrue("both colours: ${colours.toList()}", c > total / 3)
        assertTrue("about four in five windows lit: $lit of $total", lit in (total * 0.7).toInt()..(total * 0.9).toInt())
    }

    /**
     * **The size rule**: a small house at the projection of where it stands, never under the size at
     * 0.6722 of the screen -- 14 px on the BV6600's 1440 -- and on every mountain the scene can draw, the
     * floor's size. The mountains' geometry is `PaperRenderer.drawMountains`': bases on the hill's solid
     * line (the front layer 0.015 below it, the lake's top edge above it when there is water), heights
     * 0.15 and 0.105 of the screen with a jitter of 0.75..1.25; the lowest a house's place can be is the
     * front layer's shortest mountain at the outermost place.
     */
    @Test
    fun `on a mountain every house is the floor's size, 14 px on the BV6600`() {
        val bv6600 = 1440f
        val floor = SceneSpace.distantHousePixelsTall(SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION, bv6600)
        assertTrue("the floor is the maintainer's 14 px: $floor", floor >= 14f)
        assertEquals(14f, floor, 0.01f)
        assertEquals("above the horizon the floor still holds", floor, SceneSpace.distantHousePixelsTall(0.6f, bv6600), 0f)
        assertTrue("below the floor's line it grows with the projection",
            SceneSpace.distantHousePixelsTall(0.70f, bv6600) > floor)
        val base = SceneSpace.GROUND_SOLID_TOP_Y_FRACTION + 0.015f
        val shortest = 0.105f * 0.75f
        val outermost = DistantHouses.SLOTS.maxOf { kotlin.math.abs(it) } + DistantHouses.SLOT_JITTER / 2f
        val lowest = DistantHouses.surfaceY(base, shortest, outermost)
        assertTrue("the lowest place on a mountain, $lowest, is above the floor's line", lowest < SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION)
        // The renderer must still state those numbers, or this bound is about another scene.
        val renderer = File(sourceRoot(), "engine/PaperRenderer.kt").readText()
        assertTrue(renderer.contains("baseYFraction = effectiveBaseYFraction + 0.015f, peakHeightFraction = 0.105f"))
        assertTrue(renderer.contains("mountainHeight[i] = peakHeight * CandidateNoise.range(seed, i, CandidateNoise.CH_HEIGHT, 0.75f, 1.25f)"))
        // And smaller than every building of the village: its farthest house is about 40 px.
        val farthestHouse = SceneSpace.SceneVariant.HOUSE_SMALL.metresTall * SceneSpace.pixelsPerMetre(bv6600) *
            SceneSpace.depthScale(0f)
        assertTrue("$floor against the village's $farthestHouse", floor < farthestHouse / 2f)
    }

    /**
     * **Two pixels**, the floor every drawn detail is held to: each window of each drawing -- the
     * chalet's attic included -- and the snow on each roof, at the size the houses are drawn on the
     * BV6600, measured off the shipped PNGs rather than off the generator's numbers.
     */
    @Test
    fun `every window and every snow cap is at least two pixels at the size they are drawn`() {
        val pixelsTall = SceneSpace.distantHousePixelsTall(SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION, 1440f)
        val stems = listOf("house_distant_cottage", "house_distant_chalet", "house_distant_tall")
        val report = StringBuilder()
        for ((index, stem) in stems.withIndex()) {
            val piece = NeighbourhoodTable.DISTANT_HOUSES[index]
            // A sprite pixel is a third of a unit; a unit is pixelsTall / height screen pixels.
            val screenPerSpritePixel = pixelsTall / piece.height / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
            val windows = opaqueBlobs(ImageIO.read(File(drawableDir(), "${stem}_mg.png")))
            assertTrue("$stem has windows", windows.isNotEmpty())
            for ((w, h) in windows) {
                val side = minOf(w, h) * screenPerSpritePixel
                report.append("%s window %.2f px\n".format(stem, side))
                assertTrue("$stem: a window ${"%.2f".format(side)} px across", side >= 2f)
            }
            val snow = ImageIO.read(File(drawableDir(), "${stem}_snow_fx.png"))
            val middle = snow.width / 2
            val thick = (0 until snow.height).count { (snow.getRGB(middle, it) ushr 24) > 127 } * screenPerSpritePixel
            report.append("%s snow %.2f px\n".format(stem, thick))
            assertTrue("$stem: the snow is ${"%.2f".format(thick)} px thick", thick >= 2f)
        }
        println(report)
    }

    @Test
    fun `nothing is drawn while the switch is off or at 0 percent`() {
        assertFalse(DistantHousesConfig(visible = false, density = 1f).drawsAny)
        assertFalse(DistantHousesConfig(visible = true, density = 0f).drawsAny)
        assertTrue(DistantHousesConfig(visible = true, density = 0.01f).drawsAny)
        val renderer = File(sourceRoot(), "engine/PaperRenderer.kt").readText()
        val draw = renderer.substring(renderer.indexOf("private fun drawDistantHouses("))
        assertTrue(draw.contains("if (!config.drawsAny) return"))
    }

    /** The bounding boxes, in sprite pixels, of the separate opaque shapes of an image. */
    private fun opaqueBlobs(image: java.awt.image.BufferedImage): List<Pair<Int, Int>> {
        val seen = Array(image.height) { BooleanArray(image.width) }
        val out = ArrayList<Pair<Int, Int>>()
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (seen[y][x] || (image.getRGB(x, y) ushr 24) <= 127) continue
            var x0 = x; var x1 = x; var y0 = y; var y1 = y
            val stack = ArrayDeque<Pair<Int, Int>>()
            stack.add(x to y); seen[y][x] = true
            while (stack.isNotEmpty()) {
                val (cx, cy) = stack.removeLast()
                x0 = minOf(x0, cx); x1 = maxOf(x1, cx); y0 = minOf(y0, cy); y1 = maxOf(y1, cy)
                for ((nx, ny) in listOf(cx + 1 to cy, cx - 1 to cy, cx to cy + 1, cx to cy - 1)) {
                    if (nx !in 0 until image.width || ny !in 0 until image.height || seen[ny][nx]) continue
                    if ((image.getRGB(nx, ny) ushr 24) <= 127) continue
                    seen[ny][nx] = true
                    stack.add(nx to ny)
                }
            }
            out += (x1 - x0 + 1) to (y1 - y0 + 1)
        }
        return out
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/res/drawable-nodpi").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found from ${File(".").absolutePath}")
    }

    private fun drawableDir(): File = File(repoRoot(), "app/src/main/res/drawable-nodpi")

    private fun sourceRoot(): File = File(repoRoot(), "app/src/main/kotlin/com/paperscrape/livewallpaper")
}
