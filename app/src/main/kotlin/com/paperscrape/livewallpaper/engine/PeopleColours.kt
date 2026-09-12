package com.paperscrape.livewallpaper.engine

/**
 * What colour each of a person's four regions is wearing, and **when that is allowed to change**
 * (v4.30).
 *
 * ### What this replaces
 *
 * The people used to be shipped one PNG per skin tone and picked with
 * `CandidateNoise.value(themeId.hashCode(), address, channel)`. That seed is a **constant per
 * theme**, so the street was dealt once and never re-dealt: on `city` the three men were all light
 * and the three children all dark, for as long as the theme was on. Six of the forty-eight
 * family-and-theme pairs collapsed onto a single tone -- which is exactly what chance predicts for
 * three figures over three tones, and the point is that it was one hand of cards held forever
 * rather than that the deal was unfair.
 *
 * Now the colour is a function of *(theme, person, crossing)* and a walker is dealt a fresh one
 * each time it crosses. Age, sex, group, lane, direction and speed are **not** re-dealt:
 * `PedestrianPopulation.build` stays a pure function of theme and density, and the crossing number
 * enters nothing but the colours.
 *
 * ### Deterministic, and why it is not real randomness
 *
 * At a given instant a given walker is a given colour, on every device and every run, so the
 * goldens keep working -- `people-skin` in particular, which is the only automatic check the
 * colour system has and would be the first casualty of a real random source. Real randomness would
 * also look the same: in both cases the colour only ever changes off screen.
 *
 * ### The clock, not the uptime
 *
 * The crossing is counted off the scene's own clock hour -- the one that moved the sun -- and not
 * off `elapsedSeconds`, which `PaperWallpaperService` starts at zero and accumulates from frame
 * deltas. Counting off uptime would deal the same opening hand after every restart of the process.
 */
internal object PeopleColours {

    // Channels, on [CandidateNoise]'s own numbering. One per region so that adding a region cannot
    // disturb the ones already in use.
    const val CH_SKIN = 40
    const val CH_HEAD = 41
    const val CH_OUTFIT = 42

    /**
     * The three skin tones, in the order they have always been indexed.
     *
     * They are the shipped ones: `generate_skin_variants.py`'s `TONES`, which the recoloured PNGs
     * carried until v4.30 stopped shipping them.
     */
    val SKIN = intArrayOf(
        0xFFF0C9A6.toInt(),
        0xFFDCA97C.toInt(),
        0xFFA9714B.toInt(),
    )

    /** Hair, for a bare head. The maintainer's three: black, brown, blond. */
    val HAIR = intArrayOf(
        0xFF2B2A33.toInt(),
        0xFF8C5A38.toInt(),
        0xFFC98F5A.toInt(),
    )

    /** Caps and hats, for a covered head. The maintainer's three: red, blue, green. */
    val CAP = intArrayOf(
        0xFFE4623E.toInt(),
        0xFF3E6FA8.toInt(),
        0xFF3F8A4A.toInt(),
    )

    /**
     * The outfits: **a shirt and its trousers, chosen together**.
     *
     * Two independent lists would put a yellow shirt over cream trousers as readily as over
     * charcoal, and a street dealt that way reads as a costume box rather than as people. So the
     * unit dealt is the pair, the way [PedestrianCarry.PALETTE] is a list of canopies rather than a
     * range of hues -- and, like it, every colour here is one the wallpaper already prints: the
     * lake's teal, the parasol's red, the hedge's green, the gift ribbon's yellow, the winter
     * coat's slate, and for the lower halves the shipped cream, the boy's blue, the shipped
     * charcoal, the shoe's brown and the belt's tan.
     *
     * Five pairs rather than three so the axis does not read as three uniforms; five is also what
     * the umbrellas settled on, for the same reason.
     *
     * Flattened as `[top0, bottom0, top1, bottom1, ...]`: this is read per figure per frame and a
     * list of pairs would be a list of objects.
     */
    val OUTFITS = intArrayOf(
        0xFF4E9FB5.toInt(), 0xFFEFDFC4.toInt(),   // lake teal over cream
        0xFFE4623E.toInt(), 0xFF3A3F4A.toInt(),   // parasol red over charcoal
        0xFF5FA85A.toInt(), 0xFF3E6FA8.toInt(),   // hedge green over blue
        0xFFF7CE64.toInt(), 0xFF5A3E2B.toInt(),   // ribbon yellow over brown
        0xFF47698F.toInt(), 0xFFC6AC78.toInt(),   // winter slate over tan
    )

    /** How many outfits [OUTFITS] holds. */
    val OUTFIT_COUNT = OUTFITS.size / 2

    /**
     * The marker a walker's held crossing carries before it has been drawn once.
     *
     * Not a value [crossingOf] can return: it is the renderer's "not dealt yet", so a figure takes
     * the current crossing the first time it is seen instead of waiting to leave the screen once.
     */
    const val UNDEALT = Int.MIN_VALUE

    /**
     * A stable choice in `0 until count` for one attribute of one person on one crossing.
     *
     * The crossing enters through the seed rather than through the index, because the index is the
     * person's address and the channel is the attribute: folding a fourth axis into either of those
     * would make two different figures share a value whenever their addresses happened to differ by
     * the crossing. [CandidateNoise] finishes with an avalanche, and it avalanches the seed as
     * thoroughly as the index, so consecutive crossings are as unrelated as consecutive addresses.
     */
    fun pick(seed: Int, address: Int, channel: Int, crossing: Int, count: Int): Int {
        val value = CandidateNoise.value(crossingSeed(seed, crossing), address, channel)
        return (value * count).toInt().coerceIn(0, count - 1)
    }

    /**
     * The theme's seed moved on by [crossing].
     *
     * Exposed because the umbrella's colour is dealt from the same crossing through
     * [PedestrianCarry]'s own channel, and two ways of folding the crossing in would be two things
     * to keep in step.
     */
    fun crossingSeed(seed: Int, crossing: Int): Int = seed + crossing * CROSSING_SALT

    /**
     * The tone index this walker wears on this crossing: its dealt [base], **rotated**.
     *
     * Rotated rather than re-rolled, because the deal underneath is worth keeping.
     * `PedestrianPopulation` hands out tones as a stratified rank -- the members of a stratum carry
     * all three between them -- so a street of four people carries a spread instead of leaving it
     * to three independent rolls, which is biased in the small and is what produced a boy-less
     * `beach` before ranks came in. A rotation is a bijection over the three tones: it moves the
     * deal without flattening it, so figures on one crossing keep exactly the spread they were
     * dealt and no tone is ever favoured.
     *
     * The part a rotation cannot keep is the spread *between* figures once they are on different
     * crossings, and that is what re-dealing is for.
     */
    fun toneIndex(base: Int, seed: Int, address: Int, crossing: Int): Int =
        (base + pick(seed, address, CH_SKIN, crossing, SKIN.size)) % SKIN.size

    /** The skin colour this person wears on this crossing, from a tone index. */
    fun skin(toneIndex: Int): Int = SKIN[toneIndex]

    /**
     * What this person has on their head on this crossing: a hair colour if the head is bare, a cap
     * colour if it is covered.
     *
     * [worn] is a fact about the drawing and comes from `PeopleLayerTable.HEAD_WORN`. Nobody's cap
     * is taken off to make room for hair -- the region is whatever is actually up there, and the
     * palette follows it.
     */
    fun head(seed: Int, address: Int, crossing: Int, worn: Boolean): Int {
        val palette = if (worn) CAP else HAIR
        return palette[pick(seed, address, CH_HEAD, crossing, palette.size)]
    }

    /** Which outfit this person is wearing on this crossing; index into [OUTFITS] pairs. */
    fun outfit(seed: Int, address: Int, crossing: Int): Int =
        pick(seed, address, CH_OUTFIT, crossing, OUTFIT_COUNT)

    /** The shirt or coat colour of outfit [index]. */
    fun top(index: Int): Int = OUTFITS[index * 2]

    /** The trouser colour of outfit [index]. */
    fun bottom(index: Int): Int = OUTFITS[index * 2 + 1]

    /**
     * How many whole crossings this walker has made by [clockSeconds].
     *
     * The same expression the walk itself uses -- `startFraction + direction * (time * speed +
     * phase)` -- without the `% 1` that turns it into a position. A step in this number is exactly
     * a wrap of `tileFraction`, so it happens once per crossing and not more.
     *
     * **It is not, by itself, a moment when nothing is on screen**, and the plan this release was
     * written from says it is. A walker exists at `x + k * tileWidth` for every whole `k`, so the
     * wrap hands the figure from one copy to the next and does not move it; whether that instant
     * is visible depends on `shiftXWrapped`, which scrolls. `PedestrianTileWrapTest` measures it:
     * over a scroll period the wrap is on screen for a little over half of it. So the caller does
     * with this what v4.28 does with the umbrella -- holds it until the walker's own draw pass
     * reports nothing drawn -- and the guarantee comes from the cull that actually happened rather
     * than from an argument about the geometry.
     */
    fun crossingOf(
        clockSeconds: Double,
        speed: Float,
        phase: Float,
        startFraction: Float,
        direction: Float,
    ): Int = kotlin.math.floor(startFraction + direction * (clockSeconds * speed + phase)).toInt()

    /**
     * The scene clock as a number of seconds, from the hour that moved the sun.
     *
     * Wraps once a day, which is all that is asked of it: what matters is that it advances in real
     * time and survives a restart of the process. With a fixed hour it does not advance at all,
     * and that is the correct reading of a frozen sun -- at a given instant, a given colour.
     */
    fun clockSeconds(hour24: Float): Double = hour24.toDouble() * 3600.0

    /**
     * Folded into the seed rather than added to it, so that crossing `n` of one theme is not
     * crossing `n + 1` of the theme whose hash is one higher.
     */
    private const val CROSSING_SALT = 0x27D4EB2F
}
