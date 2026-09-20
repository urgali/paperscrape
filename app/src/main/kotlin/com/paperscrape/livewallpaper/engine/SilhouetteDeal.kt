package com.paperscrape.livewallpaper.engine

/**
 * Which of its category's silhouettes each candidate slot was dealt.
 *
 * ### The defect this exists to remove
 *
 * This is [SeededBalance]'s argument applied to the buildings, and the wording of v4.2's own
 * release note applies to them without a change:
 *
 * > the app draws **one** seed -- `themeId.hashCode()`, fixed for as long as the theme is
 * > selected -- and one seed yields at most twelve people. A fair coin flipped six times clumps,
 * > and a frozen seed freezes the clump.
 *
 * v4.2 fixed it for the people and left the buildings where they were, choosing each one's
 * silhouette with `NeighbourhoodComposer.stableFraction(x, d, salt)` -- one hashed value per slot
 * per question, independent of every other slot. On ten candidates that clumps exactly as the
 * coin did: measured on the twelve shipped theme ids, `autumn` showed **2** of the house
 * catalogue's 10 silhouettes over its whole scrollable width and `city` 2, four themes showed
 * three or fewer, and no theme reached six.
 *
 * ### And a second cause, which re-seeding alone would not have touched
 *
 * `stableFraction` is
 *
 * ```
 * val raw = tileFractionX * 7919f + depthFraction * 7919f * 131f + salt
 * return raw - floor(raw)
 * ```
 *
 * in `Float`. The depth term alone reaches ~985 000 for a front-band house, where a `Float`'s ulp
 * is 0.0625, so `raw` before the salt is **a multiple of 1/16** (1/32 in the back band). The salts
 * are `3.7 * index + 11.3` and `5.1 * index + 23.9`, and adding a constant to a value that is
 * already quantised to 1/16 leaves it on the same 1/16 grid, shifted -- so every slot's question
 * is answered off the *same* sixteen-valued number. The roof choice and the storey count were
 * therefore not two decisions but one: **4 of the house catalogue's 10 silhouettes could not be
 * drawn on any theme at any density**, and never were. Two storeys under a turret, two under a
 * mansard, one under a mansard, and the small house's storey-plus-mansard: four drawings that
 * ship in the APK and that nobody has ever seen.
 *
 * A stratified deal removes both causes at once, because it stops reading `stableFraction` for
 * this question at all: the catalogue is enumerated from the table, and [SeededBalance.rankOf]
 * -- an integer hash at full resolution -- says which slot gets which entry.
 *
 * ### What it is not
 *
 * Not a correction pass, which is decision **D-4.2-A** restated: nothing here counts the
 * silhouettes already produced and nothing forces "a turret every N". A slot's silhouette is a
 * pure function of `(seed, slot)` and of nothing else, so lowering a category's density still
 * removes particular slots and leaves every survivor exactly the silhouette it had -- the
 * stability contract [CandidateThreshold] documents survives intact, and the density slider still
 * governs **how many**, never **which**.
 *
 * Not a per-theme rule either. There is no theme id anywhere in this file and no branch that names
 * one: the theme contributes its seed, exactly as it does to the people, and `DESIGN_NOTES.md`'s
 * "a theme's identity lives in its defaults, not in the renderer" is unchanged.
 *
 * ### Where the deal happens
 *
 * At generation, in [SceneObjectCatalog], and the result is carried on
 * [StaticSceneObject.silhouette]. Not recomputed by whoever draws: the family a house belongs to
 * follows from its dealt silhouette, and the layout's own shop-visibility pass measures houses by
 * that family -- so a value the generator and the renderer each derived separately is the
 * preview-drift defect `PreviewRendererAgreementTest` exists to catch, one file further back.
 * [StaticSceneObject.UNDEALT] is what a spec nobody dealt carries, and it keeps the pre-v5.5
 * behaviour for exactly those: custom themes saved by an older build, the random-theme generator,
 * and the flat preview, which declares its own identities.
 */
internal object SilhouetteDeal {

    /**
     * One silhouette of one family: which alternative each of the family's slots takes, and how
     * many times it repeats.
     *
     * The two arrays are indexed by the family's own slot index, so this is the [BuildingFamily]'s
     * deal expressed as data rather than as two hashes -- which is what makes the catalogue
     * countable and therefore what makes "all of them appear" a measurable claim.
     */
    internal class Silhouette(
        val variant: SceneSpace.SceneVariant,
        val choices: IntArray,
        val repeats: IntArray,
    )

    /**
     * Every silhouette [family] can deal, in odometer order over its slots.
     *
     * Derived from the table rather than listed here: adding an alternative or widening a repeat
     * range in [NeighbourhoodTable] grows this catalogue by construction, and a catalogue restated
     * by hand is the kind of second copy that drifts.
     */
    private fun enumerate(variant: SceneSpace.SceneVariant, family: BuildingFamily): List<Silhouette> {
        var out = listOf(Silhouette(variant, IntArray(0), IntArray(0)))
        for (slot in family.slots) {
            val grown = ArrayList<Silhouette>(out.size * slot.options.size * (slot.repeatMax - slot.repeatMin + 1))
            for (prefix in out) {
                for (choice in slot.options.indices) {
                    for (repeats in slot.repeatMin..slot.repeatMax) {
                        grown += Silhouette(
                            variant,
                            prefix.choices + choice,
                            prefix.repeats + repeats,
                        )
                    }
                }
            }
            out = grown
        }
        return out
    }

    private fun enumerate(variant: SceneSpace.SceneVariant): List<Silhouette> =
        enumerate(variant, NeighbourhoodTable.FAMILIES.getValue(variant))

    /**
     * The house catalogue: **one catalogue spanning both families**, small houses first.
     *
     * One rather than two, because two would leave the maintainer's requirement unmet. The
     * family a slot resolved to used to come from a hash of its `tileFractionX`, and that hash
     * clumps the same way everything else on one seed clumps: measured, `spring` drew 8 small
     * houses and 2 large, `city` and `winter` 8 large and 2 small, and **`autumn` kept 5 houses
     * at its default density and all five were large** -- a theme with no small house anywhere on
     * its scrollable width. Dealing the two families' silhouettes separately would have left that
     * exactly as it is, since it can only ever redistribute within a family. Dealt as one
     * catalogue, every theme carries the same 4 small and 6 large in a seeded arrangement, which
     * is "all the variants on every theme" said in the only arithmetic that delivers it.
     */
    val HOUSES: List<Silhouette> =
        enumerate(SceneSpace.SceneVariant.HOUSE_SMALL) + enumerate(SceneSpace.SceneVariant.HOUSE_LARGE)

    /** The skyline's two crowns, spire and dome. */
    val TOWERS: List<Silhouette> = enumerate(SceneSpace.SceneVariant.TOWER)

    /** The trattoria's one pavilion: a catalogue of one, which is why it needs no deal. */
    val RESTAURANTS: List<Silhouette> = enumerate(SceneSpace.SceneVariant.RESTAURANT)

    /** The corner bar's two frontages, signboard and chamfer. */
    val BARS: List<Silhouette> = enumerate(SceneSpace.SceneVariant.BAR)

    /**
     * Which catalogue governs [spec], or null for a type whose drawing has no alternatives.
     *
     * A house's catalogue does **not** depend on its family, which is what keeps this free of the
     * circularity the one-catalogue decision would otherwise create: the family is read off the
     * dealt silhouette, so it cannot also be an input to choosing one. A commercial building's
     * does depend on its depth -- `SceneObjectRenderer.variantFor` decides tower/restaurant/bar
     * that way and will go on deciding it that way, because a four-metre bar has no business on
     * the skyline whatever a seed says.
     */
    fun catalogueFor(spec: StaticSceneObject): List<Silhouette>? = when (spec.type) {
        SceneObjectType.HOUSE -> HOUSES
        SceneObjectType.SKYSCRAPER -> when {
            spec.depthFraction < SceneSpace.BUILDING_TOWER_MAX_DEPTH -> TOWERS
            spec.depthFraction < SceneSpace.SHOP_VARIANT_DEPTH_SPLIT -> RESTAURANTS
            else -> BARS
        }
        else -> null
    }

    /** The family a dealt house belongs to, or null if this slot carries no deal. */
    fun houseVariantOrNull(silhouette: Int): SceneSpace.SceneVariant? =
        HOUSES.getOrNull(silhouette)?.variant

    /**
     * The catalogue index slot [slot] of [slotCount] is dealt.
     *
     * `(rotation + rank) % size`, which is [PedestrianPopulation]'s own expression for the same
     * problem -- its `(skinRotation + rankAmongMembers(...)) % SKIN_TONE_COUNT` -- and not a
     * second mechanism. The two terms do different work and both are needed:
     *
     *  - **[SeededBalance.rankOf] is what stops the clumping.** Ranks are a permutation of
     *    `0 until slotCount`, so where there are at least as many slots as catalogue entries every
     *    entry is dealt at least once, by construction rather than on average. Ten house slots
     *    against ten house silhouettes is therefore all ten, on every theme.
     *  - **The rotation is what a one-slot population needs.** A rank over a single slot is always
     *    0, so without it the corner bar would wear the same frontage on all twelve themes --
     *    which would be *worse* than the hash it replaces, the one thing this deal is not allowed
     *    to be. The rotation is one draw on the theme's seed and nothing else, so the bar varies
     *    by theme and stays fixed within one.
     */
    fun indexFor(catalogue: List<Silhouette>, seed: Int, slot: Int, slotCount: Int): Int {
        if (catalogue.size <= 1) return 0
        val rotation = (CandidateNoise.value(seed, 0, CH_ROTATION) * catalogue.size)
            .toInt().coerceIn(0, catalogue.size - 1)
        val rank = SeededBalance.rankOf(
            seed, CH_SILHOUETTE, slot, slotCount, addressStride = 1, addressOffset = 0,
        )
        return (rotation + rank) % catalogue.size
    }

    /**
     * [candidates] with each slot's silhouette recorded on it.
     *
     * Slots are numbered **within their own catalogue** -- the eight towers of a theme are slots
     * 0..7 of the tower catalogue and its one bar is slot 0 of the bar catalogue -- so each
     * population's deal is independent of how many of the others there happen to be. Types with no
     * catalogue pass through untouched and keep [StaticSceneObject.UNDEALT].
     */
    fun dealtAcross(candidates: List<StaticSceneObject>, seed: Int): List<StaticSceneObject> {
        val catalogues = candidates.map { catalogueFor(it) }
        val total = HashMap<List<Silhouette>, Int>()
        for (catalogue in catalogues) {
            if (catalogue != null) total[catalogue] = (total[catalogue] ?: 0) + 1
        }
        val seen = HashMap<List<Silhouette>, Int>()
        return candidates.mapIndexed { index, spec ->
            val catalogue = catalogues[index] ?: return@mapIndexed spec
            val slot = seen[catalogue] ?: 0
            seen[catalogue] = slot + 1
            spec.copy(silhouette = indexFor(catalogue, seed, slot, total.getValue(catalogue)))
        }
    }

    /** Which slot gets which silhouette. */
    private const val CH_SILHOUETTE = 50

    /** Where a catalogue shorter than its slot count starts, so one-slot populations still vary. */
    private const val CH_ROTATION = 51
}
