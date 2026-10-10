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
