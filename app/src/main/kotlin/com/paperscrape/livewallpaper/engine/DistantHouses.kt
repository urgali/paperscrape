package com.paperscrape.livewallpaper.engine

/**
 * Where the distant houses stand on a mountain, which drawing each is, which of the houses' two
 * colours it wears and whether its window is lit: on the mountains, off as every theme ships, three
 * drawings mixed, 50 % when turned on (`DESIGN_NOTES.md` §12, decision 52).
 *
 * ### Where
 *
 * On each mountain the scene draws, at [SLOTS] places across its width, each nudged by a coin of its
 * own, on the silhouette's own surface ([surfaceY], `MountainSilhouette`'s parabola), and drawn right
 * after that mountain (`PaperRenderer.drawMountainLayer`): what stands in front of the mountain -- the front
 * layer over the back one, the hills over both -- stands in front of its houses too, and a later mountain of
 * its own layer never covers one (below); it scrolls with its mountain, slower than the town. A house on a
 * slope is sunk by the slope under half its width ([footY]), so its downhill corner stays on the
 * paper instead of hanging over the sky.
 *
 * ### How many, and on which side
 *
 * A mountain carries [count] houses -- the amount times its five places plus a coin of the mountain's own,
 * rounded down, one at least above 0 %: two or three at 50 % (two and a half on average), five at 100 %,
 * fewer only where a place is covered (below) -- and they take its
 * places in an order that **puts them on
 * both sides** ([rank]): one side, the other, the top, then the second place of each side. The side a
 * mountain starts from changes from one mountain to the next, so the mountains that carry one house have it
 * on one side and the next, counted along the whole strip of the layer ([startsLeft]). Raising the amount only adds houses, lowering it only takes them away, the
 * others staying where they stand. With a coin of its own under the amount for each place, a theme could
 * stand all its houses on one side of its mountains (Autumn at 50 %: six on the left, none on the right);
 * both sides are `DESIGN_NOTES.md` §12, decision 52.
 *
 * ### Only where it shows
 *
 * A mountain of the same layer drawn after this one covers what of this one it overlaps, houses included: with both
 * layers raised to 80 % or more, on Autumn the right-hand house of one mountain a tile would stand wholly behind the
 * next, and at 50 % that mountain would show houses on one side only. **A place where a house would not be wholly seen is not given
 * one** ([hiddenPlaces]: any of the house's drawing under such a mountain), and the house takes the next place of
 * the order instead -- **its side's other place first** ([standingMask]: the first side's first free place, the other
 * side's, the top, then each side's second), so a mountain with two houses or more still has them on both sides
 * wherever both its sides have a free place. The places nothing covers keep the order [rank] gives them, so on every
 * mountain no layer of its own covers -- every theme as it ships -- the houses stand where [rank] puts them; the amount only
 * adds and takes away; with every place of a mountain taken -- the houses at 100 % -- a covered one is not drawn, and
 * a mountain with every place covered would carry none (there is none such on the twelve themes' layers at 0 to 100 %
 * on the four screens `DistantHousesTest` measures, 720 x 1440 to a tablet on its side). Only where their own layer
 * leaves them in sight: `DESIGN_NOTES.md` §12, decision 52.
 *
 * ### How large
 *
 * [SceneSpace.distantHousePixelsTall]: a small house at the projection of where it stands, never under
 * the size the projection gives at [SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION]. The mountains stand
 * above that line, where the ground plane's projection gives nothing, so on a mountain every house is
 * that size -- 14 px on the BV6600 -- with no reduction for the mountain behind: smaller, its windows
 * and its snow would fall under the two pixels every drawn detail is held to.
 *
 * Pure, so the JVM suite holds the rules; the drawing is `PaperRenderer.drawDistantHouses`.
 */
internal object DistantHouses {

    /** Where on a mountain's half-width a house may stand: -1 is its left foot, 1 its right. */
    val SLOTS = floatArrayOf(-0.52f, -0.27f, -0.02f, 0.24f, 0.48f)

    /** How far a place moves either way, as a share of the half-width. */
    const val SLOT_JITTER = 0.12f

    /**
     * The share of the houses with a lit window at night; the rest are dark glass, as a window of the
     * village left unlit is: a pale window keeping some of the light would read at night as a lit one
     * of cold light.
     */
    const val LIT_SHARE = 0.8f

    /**
     * How far a house's foot is sunk into the mountain beyond what its slope needs, as a share of its
     * height: it stands in the slope rather than on a ledge.
     */
    const val SINK_SHARE = 0.25f

    /** How far either side of its centre a house's foot is measured for the slope: about its half-width. */
    const val HALF_WIDTH_SHARE = 0.55f

    // One channel per question, so no answer follows another.
    private const val CH_COLOUR = 512
    private const val CH_LIT = 513
    private const val CH_DESIGN = 514
    private const val CH_PRESENT = 515

    /** Mixed into each mountain's seed, so the houses' coins share nothing with the mountain's own. */
    private const val SEED_SALT = 0x512

    /**
     * The seed of the houses on mountain [mountainIndex] of the layer seeded [layerSeed]: one mountain,
     * one set of houses, the same on every frame and every device.
     */
    fun seedFor(layerSeed: Int, mountainIndex: Int): Int = (layerSeed * 31 + mountainIndex) xor SEED_SALT

    /** The place at the top of a mountain, between its two sides: [SLOTS]' middle one. */
    const val TOP = 2

    /** The address of a mountain's own coin for [count], on the places' channel: one no place uses. */
    private const val COUNT_COIN = 7

    /**
     * How many houses the mountain seeded [seed] carries at [density]: `floor(density x 5 + coin)`, one at
     * least above 0 -- so a mountain the scene draws carries a house while the row reads on
     * (`DESIGN_NOTES.md` §12, decision 40), wherever it has a place no later mountain covers ([standingMask]) -- and none at 0.
     * Never fewer as the amount rises.
     */
    fun count(seed: Int, density: Float): Int {
        if (density <= 0f) return 0
        val n = kotlin.math.floor(density * SLOTS.size + CandidateNoise.value(seed, COUNT_COIN, CH_PRESENT)).toInt()
        return n.coerceIn(1, SLOTS.size)
    }

    /**
     * Where place [slot] of the mountain seeded [seed] comes in the order its houses take the places, 0
     * first: the side [leftFirst] names, the other side, the [TOP], the first side's other place, the other
     * side's. Of a side's two places the one whose coin is lower comes first.
     */
    fun rank(seed: Int, slot: Int, leftFirst: Boolean): Int {
        if (slot == TOP) return 2
        val left = slot < TOP
        val partner = if (left) 1 - slot else 2 * (TOP + 1) + 1 - slot
        val firstOfSide = CandidateNoise.value(seed, slot, CH_PRESENT) <= CandidateNoise.value(seed, partner, CH_PRESENT)
        val sideFirst = left == leftFirst
        return when {
            firstOfSide && sideFirst -> 0
            firstOfSide -> 1
            sideFirst -> 3
            else -> 4
        }
    }

    /**
     * Whether place [slot] of the mountain seeded [seed] holds a house at [density], the mountain starting
     * from the left side or not ([leftFirst]), the places in [hidden] given none: see [standingMask]. At 0 none.
     */
    fun stands(seed: Int, slot: Int, density: Float, leftFirst: Boolean, hidden: Int = 0): Boolean =
        standingMask(seed, density, leftFirst, hidden) and (1 shl slot) != 0

    /**
     * The places of the mountain seeded [seed] that hold a house at [density] (bit `slot` set), the places in [hidden]
     * left empty: its [count] houses take, in turn, the first side's first place, the other side's, the [TOP], the first
     * side's second place and the other side's -- a side's place by its [rank] -- where a place is hidden its side's other
     * place standing for it. With nothing hidden, the places of [rank] under [count]. Counted, nothing allocated.
     */
    fun standingMask(seed: Int, density: Float, leftFirst: Boolean, hidden: Int): Int {
        val houses = count(seed, density)
        if (houses == 0) return 0
        var mask = 0
        if (hidden == 0) {
            // Nothing covered: the places of rank under count, bit for bit.
            for (slot in SLOTS.indices) if (rank(seed, slot, leftFirst) < houses) mask = mask or (1 shl slot)
            return mask
        }
        var firstA = -1
        var secondA = -1
        var firstB = -1
        var secondB = -1
        for (slot in SLOTS.indices) {
            when (rank(seed, slot, leftFirst)) {
                0 -> firstA = slot
                1 -> firstB = slot
                3 -> secondA = slot
                4 -> secondB = slot
            }
        }
        val aFree = (if (isFree(firstA, hidden)) 1 else 0) + (if (isFree(secondA, hidden)) 2 else 0)
        val bFree = (if (isFree(firstB, hidden)) 1 else 0) + (if (isFree(secondB, hidden)) 2 else 0)
        var taken = 0
        // A side's first house: its first place, or its second where the first is hidden.
        val a1 = when { aFree and 1 != 0 -> firstA; aFree and 2 != 0 -> secondA; else -> -1 }
        val b1 = when { bFree and 1 != 0 -> firstB; bFree and 2 != 0 -> secondB; else -> -1 }
        if (a1 >= 0 && taken < houses) { mask = mask or (1 shl a1); taken++ }
        if (b1 >= 0 && taken < houses) { mask = mask or (1 shl b1); taken++ }
        if (hidden and (1 shl TOP) == 0 && taken < houses) { mask = mask or (1 shl TOP); taken++ }
        // A side's second house: its second place, while both are free.
        if (aFree == 3 && taken < houses) { mask = mask or (1 shl secondA); taken++ }
        if (bFree == 3 && taken < houses) { mask = mask or (1 shl secondB) }
        return mask
    }

    /** Whether [slot] is a place -- -1 where a rank is nobody's: two places of a side whose coins tie both rank first -- and not in [hidden]. */
    private fun isFree(slot: Int, hidden: Int): Boolean = slot >= 0 && hidden and (1 shl slot) == 0

    /**
     * The places of mountain [index] of a layer where a house would not be wholly seen (bit `slot` set): some of its drawing
     * under a mountain of the same layer drawn after it -- a later index, on any copy of the tile ([tileWidth] apart), as
     * `PaperRenderer.drawMountainLayer` draws them. The layer's mountains are [cx] (across the tile), [width], [height] and
     * [seeds], those it draws [present]; they stand on [baseY], on a screen [screenHeight] tall. A house's drawing is its
     * box: the roofline's ends ([BuildingPiece.roofX], its eaves) and [INK_MARGIN] beyond them, from its top to [FOOT_INK]
     * below its foot, at its size there ([SceneSpace.distantHousePixelsTall]). A mountain's silhouette is a parabola, so the
     * box is under one exactly where its bottom edge is, at the point of the edge nearest that mountain's peak.
     * Worked out once when the layer changes, not per frame (`PaperRenderer`'s cache).
     */
    fun hiddenPlaces(
        index: Int,
        cx: FloatArray,
        width: FloatArray,
        height: FloatArray,
        seeds: IntArray,
        present: BooleanArray,
        baseY: Float,
        tileWidth: Float,
        screenHeight: Float,
    ): Int {
        var hidden = 0
        val seed = seeds[index]
        val halfWidth = width[index] / 2f
        for (slot in SLOTS.indices) {
            val along = along(seed, slot)
            val surface = surfaceY(baseY, height[index], along)
            val tall = SceneSpace.distantHousePixelsTall(surface / screenHeight, screenHeight)
            val foot = footY(surface, slope(height[index], halfWidth, along), tall)
            val piece = NeighbourhoodTable.DISTANT_HOUSES[design(seed, slot, NeighbourhoodTable.DISTANT_HOUSES.size)]
            val scale = tall / piece.height
            val px = cx[index] + along * halfWidth
            val left = px + (piece.roofX[0] - INK_MARGIN) * scale
            val right = px + (piece.roofX[piece.roofX.size - 1] + INK_MARGIN) * scale
            val bottom = foot + FOOT_INK * scale
            var covered = false
            for (other in index + 1 until cx.size) {
                if (!present[other] || covered) continue
                val otherHalf = width[other] / 2f
                for (copy in -1..1) {
                    val ocx = cx[other] + copy * tileWidth
                    val lo = maxOf(left, ocx - otherHalf)
                    val hi = minOf(right, ocx + otherHalf)
                    if (lo >= hi) continue
                    val x = ocx.coerceIn(lo, hi)
                    val a = (x - ocx) / otherHalf
                    if (bottom > surfaceY(baseY, height[other], a)) covered = true
                }
            }
            if (covered) hidden = hidden or (1 shl slot)
        }
        return hidden
    }

    /**
     * How far a distant house's drawing reaches past the ends of its roofline, in its units: the eaves are the roofline's
     * ends and the paper's edge is a third to two thirds of a unit beyond them on the three drawings (`DistantHousesTest`
     * measures it off the PNGs).
     */
    const val INK_MARGIN = 1f

    /** How far below its foot a distant house's drawing reaches, in its units: 1.33 to 1.67 on the three (measured the same way). */
    const val FOOT_INK = 2f

    /**
     * Whether the houses of a mountain start from the left side: every other mountain **along the whole strip of
     * its layer** -- the [shown]-th (0 first) of the [perTile] mountains a tile of the layer draws, on copy [copy]
     * of the tile (0 the copy on screen at scroll 0) -- the front layer starting from the other side than the back.
     * Counted along the strip and not tile by tile: a count that started again at each tile would put two neighbours
     * on one side at every seam when a tile draws an odd number of mountains, and with one a tile every mountain of
     * the layer on the same side. With an even number a tile the two counts
     * agree. A copy keeps its number while it is on screen, so no house changes side under the eyes.
     */
    fun startsLeft(copy: Int, shown: Int, perTile: Int, frontLayer: Boolean): Boolean =
        Math.floorMod(copy * perTile + shown + if (frontLayer) 1 else 0, 2) == 0

    /** Where place [slot] stands across its mountain, -1 (left foot) .. 1 (right foot). */
    fun along(seed: Int, slot: Int): Float =
        SLOTS[slot] + (CandidateNoise.value(seed, slot, CandidateNoise.CH_X) - 0.5f) * SLOT_JITTER

    /** Which of [count] drawings the house at [slot] is: the three are mixed on every mountain. */
    fun design(seed: Int, slot: Int, count: Int): Int =
        (CandidateNoise.value(seed, slot, CH_DESIGN) * count).toInt().coerceIn(0, count - 1)

    /** Which of the houses' two colours (0 = Color 1, 1 = Color 2) the house at [slot] wears. */
    fun colourVariant(seed: Int, slot: Int): Int = if (CandidateNoise.value(seed, slot, CH_COLOUR) < 0.5f) 0 else 1

    /** Whether the window of the house at [slot] is lit at night: four houses in five ([LIT_SHARE]). */
    fun isLit(seed: Int, slot: Int): Boolean = CandidateNoise.value(seed, slot, CH_LIT) < LIT_SHARE

    /**
     * The glass of the house at [slot] at [nightGlow]: the lit window's, or -- one in five -- the dark
     * glass of a window left unlit, both from the day's glass as every window in the scene.
     */
    fun glassColour(seed: Int, slot: Int, nightGlow: Float): Int =
        if (isLit(seed, slot)) SceneObjectRenderer.windowGlassColor(nightGlow) else SceneObjectRenderer.unlitWindowGlassColor(nightGlow)

    /** The mountain's surface at [along] of its half-width: `MountainSilhouette`'s parabola. */
    fun surfaceY(baseY: Float, height: Float, along: Float): Float = baseY - height * (1f - along * along)

    /** How steep the surface is there, in pixels down per pixel across. */
    fun slope(height: Float, halfWidth: Float, along: Float): Float = kotlin.math.abs(2f * height * along / halfWidth)

    /** Where a house [pixelsTall] tall stands on a surface at [surfaceY] of steepness [slope]. */
    fun footY(surfaceY: Float, slope: Float, pixelsTall: Float): Float =
        surfaceY + slope * pixelsTall * HALF_WIDTH_SHARE + pixelsTall * SINK_SHARE
}
