package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shade a fixed-art sprite is drawn under, which before v5.1 was always noon.
 *
 * **The defect this pins is that nothing was wrong with any one sprite.** Every object that dims
 * at dusk dims as a side effect of being *tinted*: its colour is interpolated between the user's
 * day and night values and multiplied over a mask. A sprite drawn in its own colours has no tint
 * to interpolate, so it kept midday values under a midnight sky and no per-sprite check could see
 * it -- the pixels were exactly as authored, which is what fixed art is supposed to be. Measured
 * on the shipped palms: a frond read (107,168,79) at 13:00 and (107,168,79) at 23:00, while the
 * wall of the house beside it fell to 0.40 of its own daytime value.
 *
 * [SceneColour.neutralShade] is the answer, and what is worth testing about it is that the factor
 * is **derived from the scene's own pair** rather than picked: change the pair and the shade
 * follows, which is the property that keeps a palm and the tree next to it losing their light
 * together.
 *
 * Only the palms are shaded in v5.1. The rest of the fixed-art library still stands lit at
 * midnight, deliberately and recorded as a defect rather than swept in here, because each of those
 * sprites needs its own judgement about what its night looks like.
 */
class NightShadeTest {

    private fun spec(type: SceneObjectType, x: Float = 0.5f) =
        StaticSceneObject(type, depthFraction = 0.5f, tileFractionX = x)

    private fun level(shade: Int) = shade ushr 16 and 0xFF

    @Test
    fun `noon is the multiply identity, so a shaded blit is the blit it replaces`() {
        // Not "close to white": exactly white. A shade that was 254 at midday would mean every
        // fixed-art sprite in the scene changed colour the day this shipped, which is not what a
        // night fix is allowed to do.
        val c = SceneCustomization.DEFAULT
        assertEquals(0xFFFFFFFF.toInt(), SceneColour.neutralShade(0xFF8AA25C.toInt(), 0xFF3F4A2A.toInt(), 1f))
        assertEquals(0xFFFFFFFF.toInt(), c.nightShadeFor(spec(SceneObjectType.PALM_TREE), 1f))
    }

    @Test
    fun `midnight is the ratio the category's own pair carries`() {
        // The shipped tree pair, variant 1: (138,162,92) by day and (63,74,42) by night. Rec. 709
        // over the encoded channels gives 151.84 and 69.34, a ratio of 0.4566, so 116 of 255.
        val shade = SceneColour.neutralShade(0xFF8AA25C.toInt(), 0xFF3F4A2A.toInt(), 0f)
        assertEquals(116, level(shade))
        // Variant 2: (63,158,107) and (36,74,52) -- 134.12 and 64.32, a ratio of 0.4796.
        assertEquals(122, level(SceneColour.neutralShade(0xFF3F9E6B.toInt(), 0xFF244A34.toInt(), 0f)))
    }

    @Test
    fun `the shade follows the pair rather than a constant`() {
        // The point of deriving it. A user who edits their trees to a night that is barely darker
        // than their day gets a palm that barely darkens, and one who edits it to near-black gets
        // a palm that nearly disappears -- the same two answers the tree beside it gives.
        val barely = SceneColour.neutralShade(0xFF8AA25C.toInt(), 0xFF879E59.toInt(), 0f)
        val deep = SceneColour.neutralShade(0xFF8AA25C.toInt(), 0xFF0A0C08.toInt(), 0f)
        assertTrue("a near-identical pair should barely shade: ${level(barely)}", level(barely) > 240)
        assertTrue("a near-black night should shade hard: ${level(deep)}", level(deep) < 40)
    }

    @Test
    fun `a shade is grey, because it says how lit and not what colour`() {
        // If this ever returns something coloured it has become a tint, and a tint over finished
        // art compounds two colours -- which is the thing `SpriteTintClassTest` exists about.
        val c = SceneCustomization.DEFAULT
        for (step in 0..10) {
            for (x in listOf(0.1f, 0.37f, 0.62f, 0.9f)) {
                val shade = c.nightShadeFor(spec(SceneObjectType.PALM_TREE, x), step / 10f)
                val r = shade ushr 16 and 0xFF
                val g = shade ushr 8 and 0xFF
                val b = shade and 0xFF
                assertEquals("red and green differ at blend ${step / 10f}", r, g)
                assertEquals("green and blue differ at blend ${step / 10f}", g, b)
                assertEquals("a shade must stay opaque", 0xFF, shade ushr 24 and 0xFF)
            }
        }
    }

    @Test
    fun `it darkens monotonically from midnight to noon`() {
        val c = SceneCustomization.DEFAULT
        var previous = -1
        for (step in 0..20) {
            val here = level(c.nightShadeFor(spec(SceneObjectType.PALM_TREE), step / 20f))
            assertTrue("the shade went backwards at ${step / 20f}", here >= previous)
            previous = here
        }
        assertEquals(255, previous)
    }

    @Test
    fun `a palm reads darker at midnight than at noon, which is the whole defect`() {
        val c = SceneCustomization.DEFAULT
        val palm = spec(SceneObjectType.PALM_TREE)
        assertTrue(
            "the palm is drawn at full daylight at midnight, which is what v5.1 fixed",
            level(c.nightShadeFor(palm, 0f)) < level(c.nightShadeFor(palm, 1f)),
        )
        // And it is the *tree* category's pair it reads, not some palm-only constant: a palm and
        // the tree it is standing in for are one category, and turning the palms off must not
        // change how dark the scene gets.
        assertEquals(
            c.nightShadeFor(palm, 0f),
            c.nightShadeFor(StaticSceneObject(SceneObjectType.TREE, 0.5f, 0.5f), 0f),
        )
    }

    @Test
    fun `a black day colour is left alone rather than dividing by zero`() {
        // There is no "fraction of no light". Full daylight is the honest answer and it is also
        // the safe one: the artwork is drawn as authored, which is what it did before v5.1.
        assertEquals(0xFFFFFFFF.toInt(), SceneColour.neutralShade(0xFF000000.toInt(), 0xFF000000.toInt(), 0f))
    }

    @Test
    fun `a night brighter than its day cannot brighten the artwork`() {
        // MULTIPLY cannot add light, so the ratio is clamped rather than wrapped: a user who puts
        // a pale night against a dark day gets a palm that does not dim, not one that overflows.
        assertEquals(
            0xFFFFFFFF.toInt(),
            SceneColour.neutralShade(0xFF203020.toInt(), 0xFFE0F0E0.toInt(), 0f),
        )
    }

    @Test
    fun `a type with no category is drawn as authored`() {
        // CAR has no `ObjectVariantConfig` reachable from a static spec, and a missing pair is not
        // an excuse to invent a darkness.
        assertEquals(
            0xFFFFFFFF.toInt(),
            SceneCustomization.DEFAULT.nightShadeFor(spec(SceneObjectType.CAR), 0f),
        )
    }
}
