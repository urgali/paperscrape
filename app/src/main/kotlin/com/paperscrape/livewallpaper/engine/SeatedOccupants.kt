package com.paperscrape.livewallpaper.engine

/**
 * Who is in a car: which families, which skin tones, which outfits -- dealt, not hashed.
 *
 * ### The defect this replaces
 *
 * Until v4.20 each of these came out of `driverSeed % n`, where
 * `driverSeed = abs((laneYFraction * 7919 + startDelaySeconds * 131).toInt())`. That looks like a
 * mixture and is not one, for the same reason `CarShell`'s avalanche hash was not: the two fields
 * carry exactly ten values between them, so `driverSeed` does too -- and those ten are
 * **6865, 6907, 6949, 6991, 7033, 6643, 6685, 6727, 6769, 6811**. They are an arithmetic
 * progression with a common difference of 42, so every one of them is odd, so `driverSeed % 2` is
 * **1 for all ten**.
 *
 * Index 1 is the woman. Measured across the twelve shipped themes' 120 cars:
 *
 * - the driver was a **woman in every single car**, and no man has ever driven;
 * - the passenger was only ever a man (60) or a girl (60), so **no boy has ever ridden**, despite
 *   v4.19 seating children specifically so that all four families could;
 * - the driver's tone came out 5 / 3 / 2 across the ten, the same imbalance the bodies had.
 *
 * None of that is visible as a failure: every car has two plausible people in it. It is visible as
 * a street where every driver is the same person, which is what v4.18's "one man and one woman in
 * every car" was supposed to have stopped being.
 *
 * ### What replaces it
 *
 * The same answer as [CarShell]: the ten identities are enumerated and dealt from a table, because
 * with ten items a distribution is arithmetic rather than a matter of mixing quality. Every deal
 * here is as even as ten allows (5/5, 4/3/3, or 3/3/2/2), and each is ordered so that no lane shows
 * the same value at two consecutive queue positions.
 *
 * Stability is unchanged and is still the property that matters: all of this is a pure function of
 * [SceneObjectCatalog.candidateIndexOf], which reads a candidate's own immutable lane and queue
 * slot, so an occupant cannot change while the car crosses the screen, while the home screen is
 * swiped, or when another car enters the frame.
 */
internal object SeatedOccupants {

    /** Index into [PeopleLayerTable.CAR]'s families: man, woman, boy, girl. */
    const val MAN = 0
    const val WOMAN = 1
    const val BOY = 2
    const val GIRL = 3

    /**
     * The driver, who is always an adult -- five men and five women across the ten identities.
     *
     * Laid out so the two lanes never put the same family at the same queue position and neither
     * lane repeats one at consecutive positions: the near lane runs man, woman, man, woman, man and
     * the far lane the opposite.
     */
    private val DRIVER_KIND = intArrayOf(
        MAN, WOMAN, // queue slot 0: near, far
        WOMAN, MAN, // slot 1
        MAN, WOMAN, // slot 2
        WOMAN, MAN, // slot 3
        MAN, WOMAN, // slot 4
    )

    /**
     * The passenger, who is **never the driver's own family** -- three men, three girls, two women,
     * two boys.
     *
     * The rule is v4.18's and is unchanged: two occupants of one family would be the same hair, the
     * same clothing and the same silhouette, so the car would read as one person drawn twice. What
     * changes is that it is now dealt rather than derived from `(driver + 1 + seed % 3) % 4`, which
     * with a constant driver could only ever produce two of the four families.
     */
    private val PASSENGER_KIND = intArrayOf(
        WOMAN, MAN, //  slot 0: near, far
        BOY, GIRL, //   slot 1
        WOMAN, BOY, //  slot 2
        MAN, GIRL, //   slot 3
        GIRL, MAN, //   slot 4
    )

    /** The driver's tone: four, three and three of the three shipped tones. */
    private val DRIVER_SKIN = intArrayOf(0, 2, 1, 0, 2, 1, 0, 2, 1, 0)

    /** The passenger's tone, dealt on its own order so a car is not two of the same. */
    private val PASSENGER_SKIN = intArrayOf(1, 0, 2, 1, 0, 2, 1, 0, 2, 1)

    /**
     * What the driver wears: each of the five [PeopleColours.OUTFITS] twice across the ten.
     *
     * Until v5.8C there was one outfit table for both seats, holding only indices 0 and 1. That was
     * right while an index meant "this family's garment or the other adult family's" -- the same
     * index then put a man and a woman in two different colours -- and it stopped being right when
     * v4.30 made the index an entry of the shared [PeopleColours.OUTFITS]: from then on the two
     * occupants of every two-seat car wore identical colours, and only the teal and the red outfit
     * ever appeared in traffic (found by the v5.8B comment audit). Now each seat has its own deal.
     */
    private val DRIVER_OUTFIT = intArrayOf(
        0, 1, // slot 0: near, far
        2, 3, // slot 1
        4, 0, // slot 2
        1, 2, // slot 3
        3, 4, // slot 4
    )

    /**
     * What the passenger wears: the driver's deal moved on by two, so every outfit also appears
     * twice here and **the passenger never wears the driver's outfit** -- the one-person-drawn-twice
     * look the family rule exists to avoid. A child wears a dealt outfit like an adult, exactly as a
     * child on the pavement does ([PeopleColours.outfit] deals all five to every family).
     */
    private val PASSENGER_OUTFIT = IntArray(DRIVER_OUTFIT.size) { (DRIVER_OUTFIT[it] + 2) % PeopleColours.OUTFIT_COUNT }

    fun driverKind(spec: CarObject): Int = DRIVER_KIND[SceneObjectCatalog.candidateIndexOf(spec)]

    fun passengerKind(spec: CarObject): Int = PASSENGER_KIND[SceneObjectCatalog.candidateIndexOf(spec)]

    fun driverSkin(spec: CarObject): Int = DRIVER_SKIN[SceneObjectCatalog.candidateIndexOf(spec)]

    fun passengerSkin(spec: CarObject): Int = PASSENGER_SKIN[SceneObjectCatalog.candidateIndexOf(spec)]

    fun driverOutfit(spec: CarObject): Int = DRIVER_OUTFIT[SceneObjectCatalog.candidateIndexOf(spec)]

    fun passengerOutfit(spec: CarObject): Int = PASSENGER_OUTFIT[SceneObjectCatalog.candidateIndexOf(spec)]

    /** Every deal, for the tests that check they are balanced and readable. */
    internal val DEALS: Map<String, IntArray> = mapOf(
        "driver family" to DRIVER_KIND,
        "passenger family" to PASSENGER_KIND,
        "driver tone" to DRIVER_SKIN,
        "passenger tone" to PASSENGER_SKIN,
        "driver outfit" to DRIVER_OUTFIT,
        "passenger outfit" to PASSENGER_OUTFIT,
    )
}
