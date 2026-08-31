package com.paperscrape.livewallpaper.engine

/**
 * Whether a walking figure is drawn with an adult or a child sprite.
 *
 * Independent of [PersonSex] by construction: the two are read from different
 * [CandidateNoise] channels, so an adult is no more likely to be male than female and a group
 * containing an adult is no more likely to contain a boy than a girl. v4.0 could not express that
 * -- see [PedestrianPopulation] for what it did instead.
 */
internal enum class PersonAge { ADULT, CHILD }

/** Whether a walking figure is drawn with a male or female sprite. Independent of [PersonAge]. */
internal enum class PersonSex { MALE, FEMALE }

/**
 * One walking figure, fully resolved.
 *
 * A value type with no Android dependency, so the whole generative system is testable on the JVM
 * without a device: every claim v4.1 makes about variety, independence and depth ordering is a
 * statement about lists of these.
 *
 * [depth] is the single source of truth for draw order -- see [PedestrianPopulation.build], which
 * returns the list already sorted by it.
 */
internal data class Pedestrian(
    /** Which group this figure belongs to. Stable across frames; used only for addressing. */
    val groupIndex: Int,
    /** Position within the group, `0 until groupSize`. */
    val memberIndex: Int,
    val age: PersonAge,
    val sex: PersonSex,
    /**
     * Index into the shipped skin palettes.
     *
     * Chosen on its own [CandidateNoise] channel, independently of [age], [sex] and [direction].
     * See [PedestrianPopulation.SKIN_TONE_COUNT] for what the shipped artwork can currently
     * express.
     */
    val skinIndex: Int,
    /** `+1` walking right, `-1` walking left. Read from its own channel; determines nothing else. */
    val direction: Float,
    /** Fraction of screen height the feet stand at. Larger is nearer the viewer. */
    val rowYFraction: Float,
    /** Where on the ground tile this figure starts its loop, in `[0, 1)`. */
    val startFraction: Float,
    /** Walk-cycle offset, so members of one group are not in lockstep. */
    val phase: Float,
    /**
     * Draw-order key: the ground line the feet stand on, larger meaning nearer the viewer.
     *
     * Derived from the figure's own baseline rather than from its index in any list, which is the
     * v4.0 defect this field exists to close.
     */
    val depth: Float,
) {
    /**
     * Index into the four shipped sprite sets, `[man, woman, boy, girl]`.
     *
     * A *projection* of [age] and [sex], not a source of them. v4.0 picked this index directly and
     * derived nothing -- which is why age and sex could not vary independently there.
     */
    val kindIndex: Int
        get() = when {
            age == PersonAge.ADULT && sex == PersonSex.MALE -> 0
            age == PersonAge.ADULT -> 1
            sex == PersonSex.MALE -> 2
            else -> 3
        }
}

/**
 * Builds the street's walking population for a frame.
 *
 * ### The v4.0 defect this replaces
 *
 * Every attribute of a pedestrian was a function of its candidate index `i`, and the pool held
 * exactly as many candidates as there were sprite kinds:
 *
 * ```
 * val reverse = i % 2 == 1              // direction
 * val near    = i % 2 == 0              // pavement row
 * val kindIdx = i % personKinds.size    // which of man/woman/boy/girl
 * ```
 *
 * With `PEDESTRIAN_COUNT == 4` and four kinds, the periods 2 and 4 lock together and the table is
 * fixed for all time:
 *
 * | i | kind  | row  | direction |
 * |---|-------|------|-----------|
 * | 0 | man   | near | right     |
 * | 1 | woman | far  | left      |
 * | 2 | boy   | near | right     |
 * | 3 | girl  | far  | left      |
 *
 * So one direction was always *man + boy* and the other always *woman + girl*, on every device and
 * in every theme -- not an unlucky seed but arithmetic. Because each sprite carries its own baked
 * palette, the reported "skin tone follows the direction" was this same table seen through the
 * artwork.
 *
 * ### What replaces it
 *
 * Groups, not candidates. The pool still holds [GROUP_COUNT] slots so that density keeps the
 * linear/monotone/stable contract [CandidateThreshold] guarantees for every category, but each
 * surviving slot now yields a *group* of one to three people, and every attribute is read from its
 * own [CandidateNoise] channel:
 *
 *  - group size, direction and row are per-group;
 *  - age, sex and skin are per-member, addressed by `groupIndex * MAX_GROUP_SIZE + memberIndex`.
 *
 * Because [CandidateNoise.value] is addressed rather than consumed, no attribute can perturb
 * another: adding the age channel cannot move anybody's x, and dropping a group at a lower density
 * cannot reshuffle the survivors.
 *
 * ### Determinism
 *
 * Pure. The only inputs are the arguments, and `seed` comes from the theme id's hash exactly as
 * every other effect's does, so the same seed gives the same street on every device and every run
 * while different seeds give different streets. Nothing here reads the clock or any global state.
 */
internal object PedestrianPopulation {

    /**
     * How many group slots the density thresholds are drawn from.
     *
     * Deliberately still four, the pool size v4.0 used for individual pedestrians: the density
     * slider keeps mapping onto the same five outcomes it did before, so the setting behaves the
     * way an existing user's muscle memory expects. What changed is that a surviving slot is now a
     * group of one to three rather than exactly one person.
     */
    const val GROUP_COUNT = 4

    /** Largest group the street will produce. */
    const val MAX_GROUP_SIZE = 3

    /**
     * How many skin tones the artwork can express.
     *
     * Three, and every one of them exists as real artwork for every character: 96 sprite variants
     * generated by `tools/generate_skin_variants.py`, which moves the single flat colour each
     * character's skin is painted in and verifies that every other colour keeps its exact pixel
     * mask. Clothes, hair, eyes, outlines, proportions, poses and animation are therefore
     * identical across the tone axis rather than merely intended to be.
     *
     * All three are shipped PaperScrape paint -- the woman's, the man's and the boy's own skin
     * colours -- so the palette is the artwork's own and the variants cannot drift out of style.
     *
     * A fourth, deeper tone was generated and dropped after review. At the depth needed to read
     * as a distinct tone it converged on the woman's brown hair and on the shared outline, and a
     * face whose skin matches its own hair stops reading as a face; lightening it far enough to
     * separate put it on top of the boy's brown. A fourth tone needs an art pass on hair and
     * outlines, not another recolour.
     *
     * v4.1's first batch shipped this as `1` with the honest note that the raster art could not
     * express more. The follow-up batch made the artwork, so the constant is now the real count.
     *
     * **Not a user setting, and deliberately not one.** No preference, slider, DataStore key or
     * UI control selects a tone; the only input is [CH_SKIN] on the seed. `SkinToneTest` fails if
     * a preference for it ever appears.
     */
    const val SKIN_TONE_COUNT = 3

    /**
     * Pedestrians' own threshold offset.
     *
     * v4.0 passed `PEDESTRIAN_THRESHOLD_SALT = 6151` to [CandidateThreshold.offsetFor], which
     * expects an [EffectId] *ordinal* and computes `(ordinal + 0.5) / COUNT`. That returned
     * `683.5`, far outside the `[0, 1)` the offset is meant to live in. It survived only because
     * the thresholds take a fractional part -- and `frac(683.5)` is exactly `0.5`, which is
     * `MOUNTAINS_BACK`'s offset. The salt whose stated job was to decorrelate pedestrians from
     * every other category had instead pinned them to one.
     *
     * Fixed here rather than by adding an ordinal and bumping `EffectId.COUNT`: `offsetFor`
     * divides by that count, so raising it would move every other category's thresholds and change
     * clouds, birds, sailboats and dolphins in every theme. This constant keeps the fix inside the
     * people system. `2/18` is a maximally-separated point on the nine-offset grid -- `1/18` from
     * its two nearest neighbours, which is the best any value outside the grid can do.
     */
    const val THRESHOLD_OFFSET = 2f / 18f

    // Channels. Distinct from CandidateNoise's own CH_* ids so that a person's attributes cannot
    // alias any existing effect's, and distinct from each other so that age cannot bias sex.
    private const val CH_GROUP_SIZE = 21
    private const val CH_DIRECTION = 22
    private const val CH_ROW = 23
    private const val CH_START = 24
    private const val CH_KIND = 25
    private const val CH_SKIN = 27
    private const val CH_MEMBER_PHASE = 28
    private const val CH_MEMBER_OFFSET = 29
    private const val CH_MEMBER_ROW = 30

    // v4.2. The two axes whose value count does not divide the slot count evenly need one more
    // draw to say *which* value gets the spare slot -- otherwise the spare would always fall on
    // value 0 and every street would carry an extra person of the palest tone.
    private const val CH_SKIN_ROTATION = 31
    private const val CH_SIZE_ROTATION = 32

    /**
     * How many of the four group slots take the first of a two-valued attribute.
     *
     * Two, so direction, row, age and sex each split the pool exactly in half. This is the whole
     * of "both directions always occur" and "an adult of each sex always occurs": it is a property
     * of the arithmetic rather than something a seed has to be lucky enough to produce.
     */
    private const val HALF_OF_POOL = GROUP_COUNT / 2

    /**
     * How many of the four sprite kinds are adults, in [Pedestrian.kindIndex]'s own order.
     *
     * That order is `[man, woman, boy, girl]`, so the first two are the adults and the even
     * indices are the males. A kind rank can therefore be read straight back as an age and a sex
     * without a lookup table -- and `Pedestrian.kindIndex` recomputes exactly the rank it came
     * from, which is what keeps the projection honest.
     */
    private const val ADULT_KINDS = 2

    /** How far apart, as a fraction of a ground tile, members of one group walk. */
    private const val MEMBER_SPACING = 0.018f

    /**
     * How much a group member's row may differ from its group's, as a fraction of screen height.
     *
     * Small and non-zero on purpose. Zero would put every member of a group on one ground line, so
     * overlapping figures would tie on depth and their order would fall back to the tie-break --
     * correct, but flat. This spread lets members genuinely stand in front of one another, which
     * is what makes a group read as a group rather than as a row of cut-outs.
     */
    private const val MEMBER_ROW_SPREAD = 0.012f

    /**
     * The street's population, **sorted far-to-near**.
     *
     * The caller draws the list in order, so the last element -- the nearest -- lands on top. That
     * single guarantee is what closes the reported overlap defect, and it holds between members of
     * one group exactly as it holds between groups, because the sort sees a flat list of people
     * and never considers which group they came from.
     *
     * Ties break on `groupIndex` then `memberIndex`: deterministic, and reproducible from the seed
     * alone rather than from whatever order the loops happened to run in.
     */
    fun build(
        seed: Int,
        density: Float,
        nearRowYFraction: Float,
        farRowYFraction: Float,
    ): List<Pedestrian> {
        if (density <= 0f) return emptyList()
        val fallbackIndex = CandidateThreshold.fallbackIndexFor(density, GROUP_COUNT, THRESHOLD_OFFSET)
        // Which of the three sizes gets the fourth slot, and which of the three tones gets the
        // spare in each stratum. Seeded, so it is not always the same one.
        val sizeRotation = (CandidateNoise.value(seed, 0, CH_SIZE_ROTATION) * MAX_GROUP_SIZE)
            .toInt().coerceIn(0, MAX_GROUP_SIZE - 1)
        val people = ArrayList<Pedestrian>(GROUP_COUNT * MAX_GROUP_SIZE)
        for (g in 0 until GROUP_COUNT) {
            if (!CandidateThreshold.isPresent(g, density, THRESHOLD_OFFSET, fallbackIndex)) continue
            // Sizes are dealt from {1, 2, 3} across the four slots rather than rolled per slot, so
            // a street always shows a lone walker, a pair and a trio instead of, as `autumn` and
            // `christmas` did, four groups that happen to be only ones and threes.
            val size = 1 + (sizeRotation + rankAmongGroups(seed, CH_GROUP_SIZE, g)) % MAX_GROUP_SIZE
            // Direction is its own rank, read from its own channel. It is deliberately used for
            // nothing else in this function: that is the whole of `direction != composition`.
            // Two groups walk each way, which is what `tundra` -- ten people, all rightward --
            // could not manage when each group flipped its own coin.
            val direction = if (rankAmongGroups(seed, CH_DIRECTION, g) < HALF_OF_POOL) 1f else -1f
            val groupRow =
                if (rankAmongGroups(seed, CH_ROW, g) < HALF_OF_POOL) nearRowYFraction else farRowYFraction
            val groupStart = CandidateNoise.value(seed, g, CH_START)
            for (m in 0 until size) {
                // Each member gets its own address in the noise, so member 2's sex is not a
                // function of member 1's and a group of three is not three copies of a pattern.
                // The four groups' m-th members form one stratum, and each attribute is dealt
                // across that stratum: the four group leaders are always two adults and two
                // children, two male and two female, and carry all three tones between them.
                val addr = g * MAX_GROUP_SIZE + m
                // One rank, four kinds, four slots: each stratum deals `[man, woman, boy, girl]`
                // out whole, so every street carries an adult of each sex and a child of each sex
                // however few people it has. Age and sex are read back off that deal, which makes
                // them *exactly* independent -- each is 50/50 and their joint is 25% by
                // construction -- where two separate half-and-half deals left the pairing to
                // chance and produced a boy-less `beach`, `autumn` and `easter`.
                val kind = rankAmongMembers(seed, CH_KIND, g, m)
                val age = if (kind < ADULT_KINDS) PersonAge.ADULT else PersonAge.CHILD
                val sex = if (kind % 2 == 0) PersonSex.MALE else PersonSex.FEMALE
                val skinRotation = (CandidateNoise.value(seed, m, CH_SKIN_ROTATION) * SKIN_TONE_COUNT)
                    .toInt().coerceIn(0, SKIN_TONE_COUNT - 1)
                val skin = (skinRotation + rankAmongMembers(seed, CH_SKIN, g, m)) % SKIN_TONE_COUNT
                // Members trail the group's anchor along its own direction of travel, so a group
                // walking left is not mirrored into walking backwards.
                val spread = CandidateNoise.range(seed, addr, CH_MEMBER_OFFSET, 0.6f, 1.4f)
                var start = (groupStart - direction * m * MEMBER_SPACING * spread) % 1f
                if (start < 0f) start += 1f
                val rowJitter = CandidateNoise.range(seed, addr, CH_MEMBER_ROW, -MEMBER_ROW_SPREAD, MEMBER_ROW_SPREAD)
                // **REN-08: the jitter may not put anyone on the road.** The near pavement row is
                // 0.807 and the spread is +-0.012, so a jittered figure could stand at 0.819 while
                // the road's painted top edge is at 0.8178 -- about 3 px of foot on the tarmac at
                // 2340 px, on the row where the figures are largest and it shows most. The band the
                // rows themselves define is the wrong bound here because it knows nothing about the
                // road; `SceneSpace.roadTopYFraction()` is the thing that must not be crossed, and
                // it is the same function the road is drawn from, so the two cannot drift apart.
                val row = (groupRow + rowJitter).coerceIn(
                    minOf(nearRowYFraction, farRowYFraction) - MEMBER_ROW_SPREAD,
                    minOf(
                        maxOf(nearRowYFraction, farRowYFraction) + MEMBER_ROW_SPREAD,
                        SceneSpace.roadTopYFraction(),
                    ),
                )
                people += Pedestrian(
                    groupIndex = g,
                    memberIndex = m,
                    age = age,
                    sex = sex,
                    skinIndex = skin,
                    direction = direction,
                    rowYFraction = row,
                    startFraction = start,
                    phase = CandidateNoise.range(seed, addr, CH_MEMBER_PHASE, 0f, 1f),
                    // The feet's ground line, and nothing else. Not the index, not the group, not
                    // the order of creation.
                    depth = row,
                )
            }
        }
        // Far first, near last. `compareBy` is a stable sort, but the explicit index tie-break
        // means the result does not depend on that guarantee either.
        people.sortWith(compareBy({ it.depth }, { it.groupIndex }, { it.memberIndex }))
        return people
    }

    /**
     * Group [g]'s place in a seeded ordering of the four group slots, on [channel].
     *
     * The address is the group index, exactly the address v4.1 hashed. What changed is that the
     * value is compared against its peers instead of against a constant.
     */
    private fun rankAmongGroups(seed: Int, channel: Int, g: Int): Int =
        SeededBalance.rankOf(seed, channel, g, GROUP_COUNT, addressStride = 1, addressOffset = 0)

    /**
     * The place of group [g]'s member [m] among the four groups' *m*-th members, on [channel].
     *
     * The stratum is deliberate. A group's size is not known when its members' attributes are
     * chosen -- and must not be, or size would bias composition -- so the pool an attribute is
     * dealt across has to be one that exists whatever the sizes turn out to be. The four leaders
     * always exist, so dealing across them guarantees the balance where it is always visible;
     * strata 1 and 2 are dealt the same way among however many groups reach them.
     *
     * The address is `g * MAX_GROUP_SIZE + m`, exactly v4.1's.
     */
    private fun rankAmongMembers(seed: Int, channel: Int, g: Int, m: Int): Int =
        SeededBalance.rankOf(seed, channel, g, GROUP_COUNT, addressStride = MAX_GROUP_SIZE, addressOffset = m)
}
