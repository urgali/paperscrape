package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A falling leaf takes one of the palette's colours whatever tree it falls from.**
 *
 * A leaf's colour is the remainder of its candidate by the palette's size, and its candidate is its tree copy's identity
 * times [PaperRenderer.MAX_LEAVES_PER_SOURCE] plus its slot. The identity is the tree's runtime index times 1 000 003 plus
 * its copy: in an `Int`, the 167th object of a theme (runtime index 166) would take the candidate past `Int.MAX_VALUE`,
 * and the copy a tile to the left of the farthest object, numbered -1, would make it negative; either way the remainder
 * would be negative and the palette read at -1 to -3, an exception that ends the wallpaper's process. A built-in theme
 * stands a few dozen objects; an imported theme, or one saved again and again, has no limit. The phone's own proof is
 * `FallingLeafContinuityTest`, which draws a theme of 167 objects.
 */
class FallingLeafColourTest {

    private val palette = 4

    @Test
    fun `every leaf of every tree copy takes a colour of the palette`() {
        val trees = (0..5_000) + listOf(165, 166, 167, 2_147, 2_148, 100_000, 1_000_000, 50_000_000, Int.MAX_VALUE)
        val copies = (-3..3) + listOf(1_000, 100_000)
        for (tree in trees) {
            for (copy in copies) {
                val id = SceneObjectRenderer.leafCopyId(tree, copy)
                for (slot in 0 until PaperRenderer.MAX_LEAVES_PER_SOURCE) {
                    val index = PaperRenderer.fallingLeafColourIndex(PaperRenderer.fallingLeafCandidate(id, slot), palette)
                    assertTrue("tree $tree, copy $copy, leaf $slot: colour $index", index in 0 until palette)
                }
            }
        }
    }

    /** The farthest tree's copy a tile to the left, at the start of the scroll: the identity -1. */
    @Test
    fun `the copy numbered minus one takes the palette's colours in turn`() {
        val id = SceneObjectRenderer.leafCopyId(0, -1)
        assertEquals(-1L, id)
        val colours = (0 until PaperRenderer.MAX_LEAVES_PER_SOURCE).map {
            PaperRenderer.fallingLeafColourIndex(PaperRenderer.fallingLeafCandidate(id, it), palette)
        }
        // -13 + slot: -13 is 3 below a multiple of four, so the slots take 3, 0, 1, 2, 3, ... in turn.
        assertEquals((0 until PaperRenderer.MAX_LEAVES_PER_SOURCE).map { Math.floorMod(-13 + it, palette) }, colours)
    }

    /**
     * **Two trees never share an identity**, however many objects a theme holds: the identity grows with the tree's index
     * for every copy the scroll can reach, so no two trees' leaves are the same leaves.
     */
    @Test
    fun `a tree's identity is its own for any number of objects`() {
        for (tree in listOf(0, 1, 165, 166, 2_147, 2_148, 4_000, 1_000_000, Int.MAX_VALUE - 1)) {
            for (copy in listOf(-1, 0, 1, 500_000)) {
                assertTrue(
                    "tree $tree and the next, copy $copy",
                    SceneObjectRenderer.leafCopyId(tree + 1, -1) > SceneObjectRenderer.leafCopyId(tree, copy),
                )
            }
        }
    }

    /**
     * **Where the `Int` arithmetic gives a valid candidate, a leaf is that candidate's leaf**: the noise reads the
     * candidate's low 32 bits, which are the candidate worked in `Int`, and an `Int` candidate that is not negative gives
     * the same colour.
     */
    @Test
    fun `the leaves of every theme that drew them keep their noise and their colour`() {
        for (tree in 0..3_000) {
            for (copy in 0..3) {
                val asInt = tree * 1_000_003 + copy
                for (slot in 0 until PaperRenderer.MAX_LEAVES_PER_SOURCE) {
                    val candidate = PaperRenderer.fallingLeafCandidate(SceneObjectRenderer.leafCopyId(tree, copy), slot)
                    val before = asInt * PaperRenderer.MAX_LEAVES_PER_SOURCE + slot
                    assertEquals("tree $tree, copy $copy, leaf $slot: the noise's index", before, candidate.toInt())
                    if (before >= 0) {
                        assertEquals(before % palette, PaperRenderer.fallingLeafColourIndex(candidate, palette))
                    }
                }
            }
        }
    }
}
