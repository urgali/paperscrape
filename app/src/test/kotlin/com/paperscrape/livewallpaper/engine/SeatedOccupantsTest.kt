package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The people in the cars are dealt evenly, and the deals read as a mixture.
 *
 * The companion to [VehicleShellRotationTest], for the same reason and against the same defect:
 * every choice about a seated occupant used to come out of `driverSeed % n` over a seed with ten
 * possible values, all of them odd, and the result was a street in which **every car was driven by
 * a woman** and no boy ever rode. See [SeatedOccupants] for the measurement.
 *
 * The population is not sampled here. There are ten identities and this enumerates all ten, which
 * is a complete statement rather than a confident one.
 */
class SeatedOccupantsTest {

    private val identities: List<CarObject> = (0 until SceneObjectCatalog.CAR_SLOTS_PER_LANE)
        .flatMap { slot ->
            listOf(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION, SceneSpace.ROAD_LANE_FAR_Y_FRACTION).map { lane ->
                CarObject(
                    laneYFraction = lane,
                    speedFraction = 0f,
                    startDelaySeconds = SceneObjectCatalog.CAR_LOOP_ENTRY_PROGRESS +
                        SceneObjectCatalog.CAR_LOOP_SPAN * slot / SceneObjectCatalog.CAR_SLOTS_PER_LANE,
                    color = 0,
                )
            }
        }

    @Test
    fun `the ten identities really are ten distinct slots`() {
        assertEquals(
            "candidateIndexOf must be a bijection onto 0..9, or every deal below is addressing the wrong row",
            (0..9).toList(),
            identities.map { SceneObjectCatalog.candidateIndexOf(it) }.sorted(),
        )
    }

    /**
     * Every deal is as even as ten items allow.
     *
     * Ten over two is five and five; ten over three is four, three and three; ten over four is
     * three, three, two and two. Anything further from that is a deal that could have been more
     * even and was not, which is precisely the 5/3/2 the hash produced.
     */
    @Test
    fun `every deal is as even as ten allows`() {
        for ((name, deal) in SeatedOccupants.DEALS) {
            val counts = deal.toList().groupingBy { it }.eachCount().values.sorted()
            val values = counts.size
            val expected = (0 until values).map { i -> deal.size / values + if (i < deal.size % values) 1 else 0 }
                .sorted()
            assertEquals(
                "the '$name' deal is $counts over $values values, and the most even ten allows is $expected",
                expected, counts,
            )
        }
    }

    /**
     * And no lane shows the same answer at two consecutive queue positions.
     *
     * Balance on its own would be satisfied by a repeating cycle, which is the objection v4.19
     * raised against a plain modulo and was right to raise. Cars in a lane arrive about 3.6 s
     * apart, so consecutive positions are the ones a viewer actually sees in sequence.
     */
    @Test
    fun `no deal repeats itself down a lane`() {
        for ((name, deal) in SeatedOccupants.DEALS) {
            for (laneBit in 0..1) {
                val queue = (0 until SceneObjectCatalog.CAR_SLOTS_PER_LANE).map { deal[it * 2 + laneBit] }
                for (i in 1 until queue.size) {
                    assertTrue(
                        "the '$name' deal repeats ${queue[i]} at queue positions ${i - 1} and $i " +
                            "of the ${if (laneBit == 0) "near" else "far"} lane: $queue",
                        queue[i] != queue[i - 1],
                    )
                }
            }
        }
    }

    /** The passenger is never the driver's own family, on every one of the ten. */
    @Test
    fun `no identity seats two of the same family`() {
        for (spec in identities) {
            assertTrue(
                "slot ${SceneObjectCatalog.candidateIndexOf(spec)} seats two of the same family",
                SeatedOccupants.driverKind(spec) != SeatedOccupants.passengerKind(spec),
            )
        }
    }

    /** The driver is an adult, and both adults actually drive. */
    @Test
    fun `both adults drive and all four families ride`() {
        val drivers = identities.map { SeatedOccupants.driverKind(it) }
        val passengers = identities.map { SeatedOccupants.passengerKind(it) }
        assertEquals("the driver must always be an adult", emptyList<Int>(), drivers.filter { it > 1 })
        assertEquals("both adults must drive", setOf(SeatedOccupants.MAN, SeatedOccupants.WOMAN), drivers.toSet())
        assertEquals(
            "all four families must ride -- the hash this replaces only ever seated two of them",
            setOf(SeatedOccupants.MAN, SeatedOccupants.WOMAN, SeatedOccupants.BOY, SeatedOccupants.GIRL),
            passengers.toSet(),
        )
    }

    /**
     * **The two occupants of a car never wear the same colours, and all five outfits drive.**
     *
     * Asserted on the colours [PeopleColours] paints, not on the indices: until v5.8C this test
     * modelled an outfit as "own family's garment or the other adult's", which the colours had
     * stopped being in v4.30, so it stayed green while every car carried two people in one outfit
     * and only two of the five outfits ever reached the road (v5.8B comment audit).
     */
    @Test
    fun `the two seats of a car never wear the same colours`() {
        for (spec in identities) {
            val d = SeatedOccupants.driverOutfit(spec)
            val p = SeatedOccupants.passengerOutfit(spec)
            assertTrue(
                "slot ${SceneObjectCatalog.candidateIndexOf(spec)} dresses both seats alike (outfits $d and $p)",
                PeopleColours.top(d) != PeopleColours.top(p) && PeopleColours.bottom(d) != PeopleColours.bottom(p),
            )
        }
        for ((seat, outfits) in listOf(
            "driver" to identities.map { SeatedOccupants.driverOutfit(it) },
            "passenger" to identities.map { SeatedOccupants.passengerOutfit(it) },
        )) {
            assertEquals(
                "every one of the ${PeopleColours.OUTFIT_COUNT} outfits must reach the $seat's seat",
                (0 until PeopleColours.OUTFIT_COUNT).toSet(), outfits.toSet(),
            )
        }
    }

    /** Every index the deals produce addresses a row that exists. */
    @Test
    fun `every dealt index is in range`() {
        for (spec in identities) {
            assertTrue(SeatedOccupants.driverSkin(spec) in 0..2)
            assertTrue(SeatedOccupants.passengerSkin(spec) in 0..2)
            assertTrue(SeatedOccupants.driverOutfit(spec) in 0 until PeopleColours.OUTFIT_COUNT)
            assertTrue(SeatedOccupants.passengerOutfit(spec) in 0 until PeopleColours.OUTFIT_COUNT)
        }
    }
}
