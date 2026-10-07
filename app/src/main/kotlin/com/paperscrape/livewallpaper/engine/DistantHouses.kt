package com.paperscrape.livewallpaper.engine

/**
 * Where the distant houses stand on a mountain, which drawing each is, which of the houses' two
 * colours it wears and whether its window is lit (v5.11, inventory I-407; the maintainer's choices of
 * 2026-10-06: on the mountains, off as every theme ships, three drawings mixed, 50 % when turned on).
 *
 * ### Where
 *
 * On each mountain the scene draws, at [SLOTS] places across its width, each nudged by a coin of its
 * own, on the silhouette's own surface ([surfaceY], `MountainSilhouette`'s parabola), and drawn right
 * after that mountain (`PaperRenderer.drawMountainLayer`): the mountain in front of it covers it as it
 * covers the mountain behind, and it scrolls with its mountain, slower than the town. A house on a
 * slope is sunk by the slope under half its width ([footY]), so its downhill corner stays on the
 * paper instead of hanging over the sky.
 *
 * ### How many
 *
 * The amount is a share of those places, kept by a threshold each place draws once ([stands]): the
 * same places stand at the same amount, and lowering it takes houses away without moving the others.
 *
 * ### How large
 *
 * [SceneSpace.distantHousePixelsTall]: a small house at the projection of where it stands, never under
 * the size the projection gives at [SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION]. The mountains stand
 * above that line, where the ground plane's projection gives nothing, so on a mountain every house is
 * that size -- 14 px on the BV6600 -- with no reduction for the mountain behind: smaller, its windows
 * and its snow fell under the two pixels every drawn detail is held to.
 *
 * Pure, so the JVM suite holds the rules; the drawing is `PaperRenderer.drawDistantHouses`.
 */
internal object DistantHouses {

    /** Where on a mountain's half-width a house may stand: -1 is its left foot, 1 its right. */
    val SLOTS = floatArrayOf(-0.52f, -0.27f, -0.02f, 0.24f, 0.48f)

    /** How far a place moves either way, as a share of the half-width. */
    const val SLOT_JITTER = 0.12f

    /** The share of the houses with a lit window at night; the rest show a glimmer of it. */
    const val LIT_SHARE = 0.8f

    /** How lit the window of a house that is not lit is, at full night. */
    const val UNLIT_GLOW = 0.15f

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

    /**
     * Whether place [slot] of the mountain seeded [seed] holds a house at [density]: its coin under
     * the amount -- so lowering the amount only takes houses away, and raising it only adds -- and,
     * above 0, always the mountain's [firstPlace], so a mountain the scene draws carries at least one
     * house while the row reads on (`AI_PROJECT_RULES.md` 8.7, the rule `CandidateThreshold`'s
     * fallback keeps for the pooled effects). At 0 none.
     */
    fun stands(seed: Int, slot: Int, density: Float): Boolean =
        density > 0f && (CandidateNoise.value(seed, slot, CH_PRESENT) < density || slot == firstPlace(seed))

    /** The place of the mountain seeded [seed] whose coin is lowest: the first to stand as the amount rises. */
    fun firstPlace(seed: Int): Int {
        var best = 0
        var lowest = Float.MAX_VALUE
        for (slot in SLOTS.indices) {
            val coin = CandidateNoise.value(seed, slot, CH_PRESENT)
            if (coin < lowest) {
                lowest = coin
                best = slot
            }
        }
        return best
    }

    /** Where place [slot] stands across its mountain, -1 (left foot) .. 1 (right foot). */
    fun along(seed: Int, slot: Int): Float =
        SLOTS[slot] + (CandidateNoise.value(seed, slot, CandidateNoise.CH_X) - 0.5f) * SLOT_JITTER

    /** Which of [count] drawings the house at [slot] is: the three are mixed on every mountain. */
    fun design(seed: Int, slot: Int, count: Int): Int =
        (CandidateNoise.value(seed, slot, CH_DESIGN) * count).toInt().coerceIn(0, count - 1)

    /** Which of the houses' two colours (0 = Color 1, 1 = Color 2) the house at [slot] wears. */
    fun colourVariant(seed: Int, slot: Int): Int = if (CandidateNoise.value(seed, slot, CH_COLOUR) < 0.5f) 0 else 1

    /** How lit the window of the house at [slot] is at full night: 1, or [UNLIT_GLOW] for one in five. */
    fun litShare(seed: Int, slot: Int): Float = if (CandidateNoise.value(seed, slot, CH_LIT) < LIT_SHARE) 1f else UNLIT_GLOW

    /** The mountain's surface at [along] of its half-width: `MountainSilhouette`'s parabola. */
    fun surfaceY(baseY: Float, height: Float, along: Float): Float = baseY - height * (1f - along * along)

    /** How steep the surface is there, in pixels down per pixel across. */
    fun slope(height: Float, halfWidth: Float, along: Float): Float = kotlin.math.abs(2f * height * along / halfWidth)

    /** Where a house [pixelsTall] tall stands on a surface at [surfaceY] of steepness [slope]. */
    fun footY(surfaceY: Float, slope: Float, pixelsTall: Float): Float =
        surfaceY + slope * pixelsTall * HALF_WIDTH_SHARE + pixelsTall * SINK_SHARE
}
