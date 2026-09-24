package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The buildings and the vehicles each **shipped theme** actually shows, measured on its own seed.
 *
 * ### Why this class exists next to [RealThemeDistributionTest]
 *
 * It is the same argument, one release later and one population along. That class exists because
 * `PedestrianPopulationTest` swept hundreds of synthetic seeds, asserted on the aggregate, and
 * passed while `beach` shipped five girls and a boy -- and its answer was to assert **per theme
 * id, over exactly the seeds the shipped app uses**. v4.2 did that for the people and left the
 * buildings on the arithmetic it had just removed from the people. Measured the same way:
 *
 *  - `autumn` drew **2** of the house catalogue's 10 silhouettes over its whole scrollable width,
 *    and `city` 2; the median theme drew 4 and no theme reached 7.
 *  - **4 of the 10 house silhouettes were drawn by no theme at any density** -- not clumping but
 *    unreachability, caused by `stableFraction`'s `Float` quantisation rather than by the seed
 *    (see [SilhouetteDeal] for the ulp arithmetic).
 *  - four themes drew only one of the skyline's two crowns.
 *  - four themes carried only **2** of the 4 vehicle types.
 *
 * ### The metric is the whole scrollable width
 *
 * The object tile is two screen widths (`tileWidth = screenWidth * 2`), so every kept candidate is
 * reachable by swiping and a count of what stands on one screen at zero scroll answers a different
 * question. Every assertion here is over the kept set, which is the set the user can reach.
 *
 * ### What is asserted, and what deliberately is not
 *
 * Not "every theme shows the whole catalogue": a theme at 65% density keeps 5 house slots and 5
 * slots cannot carry 10 drawings. The bar is the **arithmetic maximum** -- as many distinct
 * silhouettes as the theme has slots, capped by the catalogue -- which is the strongest claim that
 * is true, and it is what makes the count a gate rather than a threshold somebody chose.
 *
 * And **not a change to how many**: [keptHouseCounts] pins each theme's surviving slot count as a
 * literal, because the maintainer's requirement was to change *which* silhouettes a theme shows
 * and not *how many* objects it stands on the street. A repair that widened a category's density
 * would satisfy every variety assertion below and still be the wrong change.
 */
class RealThemeSilhouetteDistributionTest {

    private val themes = ThemeCatalog.ALL

    /**
     * What a candidate is drawn as: its family and the pieces the composer stacks for it.
     *
     * Read off the [NeighbourhoodComposer.Deal] the renderer itself builds, not recomputed from
     * [SilhouetteDeal]'s catalogue -- so this measures the picture rather than restating the
     * arithmetic under test, and a deal that recorded an index the composer then ignored would
     * fail here instead of passing.
     */
    private fun silhouetteOf(spec: StaticSceneObject): String {
        val variant = SceneObjectRenderer.variantFor(spec)
        val family = NeighbourhoodTable.FAMILIES.getValue(variant)
        val deal = NeighbourhoodComposer.Deal()
        NeighbourhoodComposer.deal(family, spec, deal)
        return variant.name + ":" + (0 until deal.size).joinToString("+") { i ->
            deal[i].piece.parts.first().res.toString()
        }
    }

    private fun kept(themeId: String, type: SceneObjectType): List<StaticSceneObject> {
        val c = defaultCustomizationFor(themeId)
        return SceneObjectCatalog.layoutFor(themeId, ThemeCatalog.byId(themeId).accentColor)
            .staticObjects.filter { it.type == type && c.keepCandidate(it) }
    }

    /**
     * How many house slots each theme keeps at its own default density.
     *
     * Pinned as literals so a repair cannot buy variety by standing more houses on the street.
     * `city` keeps 3 of 10 because it is the one theme that lowers the Houses density, to 0.3.
     */
    private val keptHouseCounts = mapOf(
        "sunset" to 8, "autumn" to 5, "winter" to 7, "desert" to 7,
        "christmas" to 6, "new_year" to 5, "beach" to 9, "city" to 3,
        "tundra" to 6, "easter" to 9, "halloween" to 7, "spring" to 9,
    )

    // ------------------------------------------------------- houses

    @Test
    fun `the density slider still decides how many houses each theme stands on the street`() {
        for (theme in themes) {
            assertEquals(
                "${theme.id} keeps a different number of house slots than it shipped with",
                keptHouseCounts.getValue(theme.id),
                kept(theme.id, SceneObjectType.HOUSE).size,
            )
        }
    }

    @Test
    fun `every built-in theme shows as many distinct house silhouettes as it has slots`() {
        for (theme in themes) {
            val houses = kept(theme.id, SceneObjectType.HOUSE)
            val distinct = houses.map { silhouetteOf(it) }.toSet()
            assertEquals(
                "${theme.id} repeats a house silhouette while another is unused -- " +
                    "${houses.size} slots, ${distinct.size} distinct: $distinct",
                minOf(houses.size, SilhouetteDeal.HOUSES.size),
                distinct.size,
            )
        }
    }

    /**
     * The four drawings nobody had ever seen.
     *
     * Two storeys under a turret, two under a mansard, one under a mansard, and the small house's
     * storey-plus-mansard shipped in the APK and were drawn by no theme at any density, because
     * the roof choice and the storey count were read off the same sixteen-valued number. The union
     * over the twelve shipped ids is the test that they are drawings and not dead assets.
     */
    @Test
    fun `every house silhouette in the catalogue is drawn by some shipped theme`() {
        val union = themes.flatMap { theme ->
            kept(theme.id, SceneObjectType.HOUSE).map { silhouetteOf(it) }
        }.toSet()
        assertEquals(
            "house silhouettes that no shipped theme draws: " +
                "${SilhouetteDeal.HOUSES.size - union.size} of ${SilhouetteDeal.HOUSES.size}",
            SilhouetteDeal.HOUSES.size,
            union.size,
        )
    }

    @Test
    fun `every built-in theme stands both house sizes on its street`() {
        for (theme in themes) {
            val families = kept(theme.id, SceneObjectType.HOUSE)
                .map { SceneObjectRenderer.variantFor(it) }.toSet()
            assertEquals(
                "${theme.id} has only one size of house on its whole scrollable width -- $families",
                setOf(SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE),
                families,
            )
        }
    }

    // ------------------------------------------------------- the skyline

    /**
     * Both tower crowns on every theme's skyline, with no exception.
     *
     * The deal gives the tower catalogue's slots a permutation of ranks, so with seven slots and
     * two crowns every theme is *dealt* three of one and four of the other. What a theme then
     * *shows* is what its density keeps, and the two are independent by design: the stability
     * contract in [SilhouetteDeal] forbids counting what has already been drawn, because a deal
     * that corrected itself would change a survivor's silhouette when a slider moved.
     *
     * **v5.6F to v5.7E: `christmas` showed one crown, and this test pinned it by name.** The
     * school made the shop band three bands, so one more shop-band candidate stays a shop and one
     * fewer is demoted to the skyline: the tower catalogue went from **8 slots to 7**, and every
     * rank in it moved. Christmas was dealt `1 1 0 0 0 1 1` and its 65 % density dropped slots 2,
     * 3 and 4 -- all three of its spires -- leaving four domes.
     *
     * **v5.7F repaired it in the theme's defaults, not in the deal** (item 139): Christmas's
     * building density is 67 %, which keeps the spire at slot 4 and nothing else. Every repair of
     * the deal itself re-dealt other themes' towers -- the cheapest, ranking over a phantom eighth
     * slot, changed 27 crowns across nine themes -- so the deal is exactly what it was. The set is
     * asserted empty rather than the check being deleted, so a later layout change that clumps a
     * theme again fails here by name.
     */
    @Test
    fun `every built-in theme shows both tower crowns`() {
        val singleCrown = sortedSetOf<String>()
        for (theme in themes) {
            val towers = kept(theme.id, SceneObjectType.SKYSCRAPER)
                .filter { SceneObjectRenderer.variantFor(it) == SceneSpace.SceneVariant.TOWER }
            assertTrue("${theme.id} keeps fewer than two towers, so this is vacuous", towers.size >= 2)
            if (towers.map { silhouetteOf(it) }.toSet().size < SilhouetteDeal.TOWERS.size) {
                singleCrown += theme.id
            }
        }
        assertEquals(
            "these themes show one crown on every tower they keep",
            sortedSetOf<String>(), singleCrown,
        )
    }

    /**
     * The item-139 repair adds one tower to Christmas and moves nothing else anywhere.
     *
     * Pinned as counts because "the density keeps one more slot" is only a repair if it is
     * exactly one, and exactly a tower: at 65 % Christmas stood 4 towers, at 67 % it stands 5, and
     * every other theme stands what it stood before. A later nudge past the next threshold
     * (0.6719, a dome) would satisfy the crown test above and still be a different skyline.
     */
    @Test
    fun `christmas stands five towers and every other theme the towers it had`() {
        val keptTowers = mapOf(
            "sunset" to 4, "autumn" to 6, "winter" to 4, "desert" to 7,
            "christmas" to 5, "new_year" to 6, "beach" to 5, "city" to 7,
            "tundra" to 5, "easter" to 5, "halloween" to 4, "spring" to 4,
        )
        for (theme in themes) {
            val towers = kept(theme.id, SceneObjectType.SKYSCRAPER)
                .filter { SceneObjectRenderer.variantFor(it) == SceneSpace.SceneVariant.TOWER }
            assertEquals("${theme.id} keeps a different number of towers", keptTowers.getValue(theme.id), towers.size)
        }
    }

    /**
     * The corner bar is one building per theme, so its two frontages cannot both stand on one
     * street -- the pool is a single slot and the catalogue has two entries.
     *
     * What is assertable, and what the rotation term in [SilhouetteDeal.indexFor] exists for, is
     * that the choice still varies **across** themes: a rank over one slot is always 0, so a deal
     * without the rotation would have put the same frontage on all twelve.
     */
    @Test
    fun `both bar frontages are used across the twelve themes`() {
        val fronts = themes.mapNotNull { theme ->
            kept(theme.id, SceneObjectType.SKYSCRAPER)
                .firstOrNull { SceneObjectRenderer.variantFor(it) == SceneSpace.SceneVariant.BAR }
                ?.let { silhouetteOf(it) }
        }.toSet()
        assertEquals("one bar frontage is never used: $fronts", SilhouetteDeal.BARS.size, fronts.size)
    }

    // ------------------------------------------------------- the road

    @Test
    fun `every built-in theme carries all four vehicle types`() {
        for (theme in themes) {
            val c = defaultCustomizationFor(theme.id)
            val cars = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).cars
            val types = c.keptCars(cars, theme.id.hashCode()).map { it.type }.toSet()
            assertEquals(
                "${theme.id} never shows one of the four vehicle types -- $types",
                CarType.entries.toSet(),
                types,
            )
        }
    }

    @Test
    fun `the declared vehicle weights are dealt exactly, not approximated`() {
        // 70/10/10/10 over ten slots is seven plain and one of each special. Asserted as a count
        // rather than as a distribution, because an exact deal is the whole difference from the
        // roll it replaced.
        for (theme in themes) {
            val counts = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).cars
                .groupingBy { it.type }.eachCount()
            assertEquals("${theme.id} plain cars", 7, counts[CarType.PLAIN])
            assertEquals("${theme.id} patrol cars", 1, counts[CarType.POLICE])
            assertEquals("${theme.id} taxis", 1, counts[CarType.TAXI])
            assertEquals("${theme.id} fire engines", 1, counts[CarType.FIRE_TRUCK])
        }
    }

    @Test
    fun `every built-in theme carries all three civilian bodies`() {
        // The shell deal is a fixed table indexed by candidate slot and was never seeded, so this
        // has always held. Asserted because the census that found the other three defects had to
        // establish it, and a property nobody checks is a property that can be lost.
        for (theme in themes) {
            val shells = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).cars
                .filter { it.type == CarType.PLAIN }
                .map { CarShell.forCar(it) }.toSet()
            assertEquals("${theme.id} is missing a civilian body -- $shells", 3, shells.size)
        }
    }

    // ------------------------------------------------------- stability

    /**
     * The deal is recorded on the layout, which is what makes it a function of `(seed, slot)`.
     *
     * This is the assertion that carries decision **D-4.2-A**, and it is stated structurally
     * because that is the only way a JVM test can state it: `layoutFor` takes no customization, so
     * a silhouette recorded there **cannot** be a function of the density, of which slots
     * survived, or of anything else read later -- and [StaticSceneObject] is an immutable data
     * class, so nothing downstream can amend it. A deal computed anywhere further along would
     * leave these candidates at [StaticSceneObject.UNDEALT] and fail here.
     *
     * **This assertion replaced a weaker one that no mutation could kill.** The first draft of
     * this class also read the same layout at several densities and checked that the survivors
     * kept their silhouettes -- which reads like the decision and is a tautology: `layoutFor`
     * takes no customization, so a value recorded there cannot vary with density, and all seven
     * mutations run against this class left that test green. It was removed rather than kept as
     * a gate that cannot fail. What is left is the cause, which `M3` (deal nothing) does kill.
     */
    @Test
    fun `every building candidate of a built-in theme carries a deal recorded at generation`() {
        for (theme in themes) {
            val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
            for (type in listOf(SceneObjectType.HOUSE, SceneObjectType.SKYSCRAPER)) {
                val undealt = layout.staticObjects
                    .filter { it.type == type && it.silhouette == StaticSceneObject.UNDEALT }
                assertTrue(
                    "${theme.id} has ${undealt.size} $type candidates with no recorded deal, " +
                        "so their drawing is decided somewhere other than the generator",
                    undealt.isEmpty(),
                )
            }
            // And nothing else is dealt: a parasol or a tree has no catalogue, and a deal recorded
            // on one would be a number nobody reads.
            val strayed = layout.staticObjects.filter {
                it.type != SceneObjectType.HOUSE && it.type != SceneObjectType.SKYSCRAPER &&
                    it.silhouette != StaticSceneObject.UNDEALT
            }
            assertTrue("${theme.id} recorded a deal on ${strayed.map { it.type }}", strayed.isEmpty())
        }
    }

    /**
     * A layout nobody dealt keeps the pre-v5.5 drawing.
     *
     * The compatibility path is the whole reason a custom theme saved by an older build still loads
     * as the street it was saved as, and it is the path the flat preview draws on. Asserted by
     * hashing the same candidate both ways: with no deal recorded the silhouette must be the one
     * `stableFraction` picks, and it must not silently become catalogue entry 0.
     */
    @Test
    fun `an undealt candidate is still drawn from its position`() {
        for (x in 0 until 40) {
            val spec = StaticSceneObject(
                SceneObjectType.HOUSE, depthFraction = 0.33f, tileFractionX = x / 40f,
            )
            assertEquals(StaticSceneObject.UNDEALT, spec.silhouette)
            val variant = SceneObjectRenderer.variantFor(spec)
            val family = NeighbourhoodTable.FAMILIES.getValue(variant)
            val dealt = NeighbourhoodComposer.Deal()
            val hashed = NeighbourhoodComposer.Deal()
            NeighbourhoodComposer.deal(family, spec, dealt)
            NeighbourhoodComposer.deal(family, spec.tileFractionX, spec.depthFraction, hashed)
            assertEquals("x=${x / 40f} piece count", hashed.size, dealt.size)
            assertEquals("x=${x / 40f} window count", hashed.windowCount, dealt.windowCount)
            for (i in 0 until hashed.size) {
                assertEquals("x=${x / 40f} piece $i", hashed[i].piece, dealt[i].piece)
                assertEquals("x=${x / 40f} baseY $i", hashed[i].baseY, dealt[i].baseY, 0f)
            }
        }
    }
}
