package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The number of people at a building's windows is the one it had before v5.12** -- the table the
 * maintainer approved on 2026-10-09 said it stays, with more windows to stand at (*«il resto si a
 * tutto»*); the scene's quantities are his and do not move. A piece that gained windows in v5.12 brings
 * the people it brought before ([BuildingPiece.people]), so every building deals its people over the
 * windows it had (`NeighbourhoodComposer.Deal.peopleWindows`) and stands them at any of the windows it
 * has.
 *
 * [WINDOWS_BEFORE] is each piece's window count before v5.12, read from that release's
 * `NeighbourhoodTable` (the ZIP the 5.12 was built from): a fact about the past, so a literal.
 */
class WindowPeopleCountTest {

    /** Family -> each slot -> each option (then each height): the windows that piece had before v5.12. */
    private val WINDOWS_BEFORE = mapOf(
        SceneSpace.SceneVariant.HOUSE_SMALL to listOf(listOf(1), listOf(1), listOf(0, 0)),
        SceneSpace.SceneVariant.HOUSE_LARGE to listOf(listOf(2), listOf(1), listOf(0, 0, 0)),
        SceneSpace.SceneVariant.TOWER to listOf(listOf(6, /* heights */ 6, 6, 6), listOf(0, 0)),
        SceneSpace.SceneVariant.RESTAURANT to listOf(listOf(3)),
        SceneSpace.SceneVariant.BAR to listOf(listOf(3, 3)),
        SceneSpace.SceneVariant.SCHOOL to listOf(listOf(4)),
    )

    @Test
    fun `every piece brings the people it brought before v5_12`() {
        for ((variant, slots) in WINDOWS_BEFORE) {
            val family = NeighbourhoodTable.FAMILIES.getValue(variant)
            assertEquals("$variant: slots", slots.size, family.slots.size)
            for ((s, counts) in slots.withIndex()) {
                val pieces = family.slots[s].options + family.slots[s].heights
                assertEquals("$variant slot $s: pieces", counts.size, pieces.size)
                for ((i, piece) in pieces.withIndex()) {
                    assertEquals("$variant slot $s piece $i brings the people its ${counts[i]} windows did", counts[i], piece.people)
                    assertTrue("$variant slot $s piece $i: no fewer windows than before", piece.windows.size >= counts[i])
                }
            }
        }
    }

    @Test
    fun `the new windows are places to stand, not more people`() {
        // The pieces that gained windows: the storeys, the school's upper floor, the two bars.
        val small = NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.HOUSE_SMALL).slots[1].options[0]
        val large = NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.HOUSE_LARGE).slots[1].options[0]
        val school = NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.SCHOOL).slots[0].options[0]
        val bars = NeighbourhoodTable.FAMILIES.getValue(SceneSpace.SceneVariant.BAR).slots[0].options
        assertEquals(2, small.windows.size)
        assertEquals(3, large.windows.size)
        assertEquals(8, school.windows.size)
        assertEquals(listOf(4, 5), bars.map { it.windows.size })
    }

    @Test
    fun `every building of the twelve streets has the people it had`() {
        val lines = StringBuilder()
        var buildings = 0
        var people = 0
        for (theme in ThemeCatalog.ALL) {
            val seed = theme.id.hashCode()
            var themePeople = 0
            for (spec in SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).staticObjects) {
                if (spec.type != SceneObjectType.HOUSE && spec.type != SceneObjectType.SKYSCRAPER) continue
                val variant = SceneObjectRenderer.variantFor(spec)
                val family = NeighbourhoodTable.FAMILIES[variant] ?: continue
                val deal = NeighbourhoodComposer.Deal()
                NeighbourhoodComposer.deal(family, spec, deal)
                // The windows the same pieces had before v5.12.
                var before = 0
                for (index in 0 until deal.size) before += windowsBefore(family, deal[index].piece)
                assertEquals("${theme.id} $variant: dealt over the windows it had", before, deal.peopleWindows)
                val buildingSeed = SceneObjectRenderer.buildingSeedOf(spec)
                val now = WindowOccupants.occupantCount(seed, buildingSeed, deal.peopleWindows, family.kind)
                assertEquals("${theme.id} $variant", WindowOccupants.occupantCount(seed, buildingSeed, before, family.kind), now)
                buildings++; people += now; themePeople += now
            }
            lines.append("${theme.id}: $themePeople people at the windows of its candidates\n")
        }
        println("v5.12 P1: $buildings buildings, $people people at their windows in all (the same as before v5.12)\n$lines")
        assertTrue(buildings > 100)
    }

    /**
     * The restaurant, the school and the bar of each built-in street in the 5.12: people at the windows over the
     * windows they were dealt over, and the colour (Color 1 or 2) their coin gave them -- read off the 5.12's tree
     * with the 5.12's own code, a fact about the past, so a literal.
     */
    private val STOOD_5_12 = mapOf(
        "sunset" to listOf("1/3c2", "3/4c1", "1/3c1"),
        "autumn" to listOf("1/3c2", "2/4c1", "1/3c2"),
        "winter" to listOf("1/3c2", "2/4c2", "1/3c2"),
        "desert" to listOf("1/3c1", "2/4c1", "1/3c2"),
        "christmas" to listOf("1/3c2", "2/4c2", "2/3c1"),
        "new_year" to listOf("2/3c1", "3/4c1", "1/3c1"),
        "beach" to listOf("1/3c2", "2/4c1", "1/3c1"),
        "city" to listOf("1/3c2", "2/4c1", "2/3c2"),
        "tundra" to listOf("1/3c2", "3/4c2", "2/3c2"),
        "easter" to listOf("1/3c2", "2/4c2", "1/3c2"),
        "halloween" to listOf("1/3c1", "3/4c2", "1/3c1"),
        "spring" to listOf("1/3c2", "3/4c2", "1/3c1"),
    )

    /**
     * **The restaurant, the school and the bar keep the people and the colour coin they had in the 5.12, on every
     * built-in street** (v5.13). The places were planned again for six buildings and moved along the street, and the
     * people at a building are dealt from where it stands: counted against the 5.12 theme by theme, seven of the
     * twelve streets had a building with one person more or fewer, until each of the three took its identity from
     * where it stood in the 5.12 (`StorefrontStreet.identities`). The number of people at a building is one of the
     * quantities the maintainer keeps.
     */
    @Test
    fun `the restaurant, the school and the bar keep their 5_12 people and colour on every built-in street`() {
        for (theme in ThemeCatalog.ALL) {
            val seed = theme.id.hashCode()
            val c = defaultCustomizationFor(theme.id)
            val ids = StorefrontStreet.identities(StorefrontStreet.places(SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)), theme.id)!!
            for (sf in listOf(Storefront.RESTAURANT, Storefront.SCHOOL, Storefront.BAR)) {
                val deal = NeighbourhoodComposer.Deal()
                SceneObjectRenderer.dealStorefront(sf.family, sf, ids[sf.ordinal], deal)
                val people = WindowOccupants.occupantCount(seed, SceneObjectRenderer.buildingSeedOf(ids[sf.ordinal]), deal.peopleWindows, sf.family.kind)
                val coin = if (c.storefrontWall(ids[sf.ordinal], sf, 1f) == c.storefrontColours(sf).colorDay1) 1 else 2
                assertEquals("${theme.id} $sf", STOOD_5_12.getValue(theme.id)[sf.ordinal], "$people/${deal.peopleWindows}c$coin")
            }
        }
        // And the wallpaper asks for them so: the renderer hands its theme to the identities, on the built-in
        // street's own layout (read in its source, the renderer needing a phone).
        var dir: java.io.File? = java.io.File(".").absoluteFile
        var text = ""
        while (dir != null && text.isEmpty()) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt")
                if (f.isFile) text = f.readText()
            }
            dir = dir.parentFile
        }
        assertTrue(text.contains("StorefrontStreet.identities(\n        layout, themeId.takeIf { CustomThemeRegistry.overrideLayoutFor(it) == null },"))
    }

    /**
     * The shop, the fire station and the police station as v5.13B gave them -- people over the windows they are
     * dealt across, and which of their group's two colours (`c1`, `c2`) -- read from the 5.13B tree with its own
     * code, by a probe run on it in v5.13B2.
     */
    private val NEW_THREE_5_13B: Map<String, List<String>> = mapOf(
        "sunset" to listOf("1/3c1", "1/2c2", "1/3c1"),
        "autumn" to listOf("1/3c1", "1/2c1", "2/3c2"),
        "winter" to listOf("1/3c1", "1/2c2", "1/3c2"),
        "desert" to listOf("1/3c1", "1/2c2", "1/3c2"),
        "christmas" to listOf("1/3c2", "1/2c2", "2/3c1"),
        "new_year" to listOf("2/3c1", "1/2c2", "1/3c2"),
        "beach" to listOf("1/3c1", "0/2c1", "1/3c1"),
        "city" to listOf("2/3c2", "1/2c2", "2/3c1"),
        "tundra" to listOf("1/3c1", "0/2c2", "1/3c1"),
        "easter" to listOf("1/3c2", "1/2c1", "2/3c2"),
        "halloween" to listOf("1/3c2", "1/2c1", "2/3c2"),
        "spring" to listOf("1/3c1", "1/2c1", "1/3c1"),
    )

    /**
     * **All six keep the people and the colour coin v5.13B gave them, on every built-in street** (v5.13B2). The
     * plan of the five places moved every place along the street, and the people at a building are dealt from
     * its identity: the identities are the ones v5.13B gave (`StorefrontStreet.identities`), so the restaurant,
     * the school and the bar keep the 5.12's (the test above) and the three new buildings v5.13B's.
     */
    @Test
    fun `the shop and the two stations keep their v5_13B people and colour on every built-in street`() {
        for (theme in ThemeCatalog.ALL) {
            val seed = theme.id.hashCode()
            val c = defaultCustomizationFor(theme.id)
            val ids = StorefrontStreet.identities(StorefrontStreet.places(SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)), theme.id)!!
            for ((k, sf) in listOf(Storefront.SHOP, Storefront.FIRE_STATION, Storefront.POLICE_STATION).withIndex()) {
                val deal = NeighbourhoodComposer.Deal()
                SceneObjectRenderer.dealStorefront(sf.family, sf, ids[sf.ordinal], deal)
                val people = WindowOccupants.occupantCount(seed, SceneObjectRenderer.buildingSeedOf(ids[sf.ordinal]), deal.peopleWindows, sf.family.kind)
                val coin = if (c.storefrontWall(ids[sf.ordinal], sf, 1f) == c.storefrontColours(sf).colorDay1) 1 else 2
                assertEquals("${theme.id} $sf", NEW_THREE_5_13B.getValue(theme.id)[k], "$people/${deal.peopleWindows}c$coin")
            }
        }
    }

    private fun windowsBefore(family: BuildingFamily, piece: BuildingPiece): Int {
        val variant = NeighbourhoodTable.FAMILIES.entries.first { it.value === family }.key
        val slots = WINDOWS_BEFORE.getValue(variant)
        for ((s, counts) in slots.withIndex()) {
            val pieces = family.slots[s].options + family.slots[s].heights
            val i = pieces.indexOfFirst { it === piece }
            if (i >= 0) return counts[i]
        }
        error("$variant: a piece of no slot")
    }
}
