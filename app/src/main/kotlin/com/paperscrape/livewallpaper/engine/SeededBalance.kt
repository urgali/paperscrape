package com.paperscrape.livewallpaper.engine

/**
 * Seeded selection that stays balanced on the handful of seeds a shipped app actually draws.
 *
 * ### The defect this exists to remove
 *
 * v4.1 chose every attribute of a person by comparing one hashed value against a constant:
 * `noise(seed, addr, CH_SEX) < 0.5` and so on. That is a fair coin, and over millions of seeds it
 * produces a perfect distribution -- which is what v4.1's tests measured, and why they passed.
 *
 * But a wallpaper never draws millions of seeds. It draws **one**. The seed is the theme id's
 * hash, it never changes, and the street it produces is at most twelve people. A fair coin flipped
 * a handful of times clumps, and because the seed is frozen the clump is frozen with it: the
 * `beach` theme's six pedestrians came out five girls and a boy, every one of them on the darkest
 * skin tone, with no adult of either sex -- on every device, in every session, for ever. The value
 * was reachable; it simply never came up, and never would.
 *
 * Independent draws also cannot express "at least one of each". Ten of the twelve built-in themes
 * were missing at least one of {adult male, adult female, boy, girl, a skin tone, a direction},
 * and `tundra` had all ten of its people walking the same way.
 *
 * ### What replaces it
 *
 * Stratified selection. The multiset of values handed out across a small pool is fixed, and the
 * seed decides **which slot gets which** -- a seeded permutation rather than a seeded coin per
 * slot. Every value still comes from the seed and nothing else, but a pool of four can no longer
 * come out four-of-a-kind.
 *
 * This is not a new idea in this codebase, and deliberately so: [CandidateThreshold] already
 * argues exactly this case for density -- *"A hashed threshold would be independent per candidate,
 * which for a small pool clumps badly: with four candidates at 50% you would often get one or
 * three rather than two."* Density got the low-discrepancy treatment in v3; the attributes did
 * not, and this is that same correction applied to the axis that was missed.
 *
 * ### What it is not
 *
 * Not a corrective override. Nothing here inspects the people already produced and forces the next
 * one to balance them; there is no running counter, no "every Nth person is male", no rule that
 * reads one attribute to choose another. A slot's value is a pure function of `(seed, slot)` and
 * of nothing else, so lowering the density still removes particular slots and leaves the rest
 * exactly as they were -- the stability contract [CandidateThreshold] documents survives intact.
 */
internal object SeededBalance {

    /**
     * Where [slot] falls in a seeded ordering of all [slotCount] slots -- `0` first, `slotCount-1`
     * last.
     *
     * Sorting slots by an independent random key is the standard way to draw a uniform
     * permutation, and computing one slot's rank directly rather than materialising the whole
     * order keeps this allocation-free on the draw path: it costs [slotCount] hashes and no
     * objects. Handing the ranks out as values -- rank 0 and 1 get one thing, 2 and 3 the other --
     * is what makes the split exact rather than merely expected.
     *
     * A slot's own address is `slot * addressStride + addressOffset`, which is how each caller's
     * existing addressing is expressed rather than replaced: pedestrians pass the group index with
     * stride 1, a group's *m*-th members pass stride [PedestrianPopulation.MAX_GROUP_SIZE] and
     * offset *m*, and a building's windows pass the stride and offset its own window address
     * already uses. The addresses are therefore unchanged from v4.1; only the decision made with
     * them is.
     *
     * Ties break on the slot index, so the result is a total order derivable from the seed alone.
     */
    fun rankOf(
        seed: Int,
        channel: Int,
        slot: Int,
        slotCount: Int,
        addressStride: Int,
        addressOffset: Int,
    ): Int {
        val key = CandidateNoise.value(seed, slot * addressStride + addressOffset, channel)
        var rank = 0
        for (other in 0 until slotCount) {
            if (other == slot) continue
            val otherKey = CandidateNoise.value(seed, other * addressStride + addressOffset, channel)
            if (otherKey < key || (otherKey == key && other < slot)) rank++
        }
        return rank
    }

    /**
     * How many of [slotCount] slots are drawn at [rate], as a whole number.
     *
     * `slotCount * rate` is rarely an integer, so the fractional part is settled by one seeded
     * draw: three windows at 40% become one occupant four times in five and two the fifth time,
     * whose mean is exactly 1.2. **The declared rate is preserved exactly** -- which matters,
     * because `WindowOccupantsTest` pins each building kind's rate and a selection that quietly
     * moved it would be changing the scene's density under another name.
     *
     * What it removes is the tail. Three independent coins at 40% leave a building empty 21.6% of
     * the time, and with roughly one bar per theme that is most of the reason nobody was ever seen
     * behind commercial glass. Here a three-pane frontage holds one or two people and never none.
     */
    fun drawCount(seed: Int, channel: Int, index: Int, slotCount: Int, rate: Float): Int {
        if (rate <= 0f || slotCount <= 0) return 0
        if (rate >= 1f) return slotCount
        val expected = slotCount * rate
        val whole = kotlin.math.floor(expected)
        val extra = if (CandidateNoise.value(seed, index, channel) < expected - whole) 1 else 0
        return (whole.toInt() + extra).coerceIn(0, slotCount)
    }
}
