package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a static object decides which drawing it is, and therefore which size it is.
 *
 * The two used to be decided in different places: `draw` computed a scale for "a house" and the
 * drawing function then chose between a small and a large one, so the size and the picture came
 * from different assumptions and the difference had to be absorbed by a `canvas.scale` correction
 * inside the drawing. Resolving the variant once is what removed those corrections, which makes
 * the resolution itself worth pinning.
 */
class SceneVariantResolutionTest {

    private fun spec(
        type: SceneObjectType,
        depth: Float = 0.5f,
        x: Float = 0.5f,
        scale: Float = 1f,
    ) = StaticSceneObject(type, depthFraction = depth, tileFractionX = x, scale = scale)

    @Test
    fun `every object type resolves to a variant`() {
        // A missing branch would be a compile error inside `variantFor`, but a type mapped to the
        // wrong variant would silently draw one thing at another thing's size.
        SceneObjectType.entries.forEach { type ->
            val variant = SceneObjectRenderer.variantFor(spec(type))
            assertTrue("${type.name} resolves to a variant with no size", variant.baseScale > 0f)
        }
        assertEquals(SceneSpace.SceneVariant.TREE, SceneObjectRenderer.variantFor(spec(SceneObjectType.TREE)))
        assertEquals(SceneSpace.SceneVariant.PALM_TREE, SceneObjectRenderer.variantFor(spec(SceneObjectType.PALM_TREE)))
        assertEquals(SceneSpace.SceneVariant.BALLOON, SceneObjectRenderer.variantFor(spec(SceneObjectType.BALLOON)))
    }

    @Test
    fun `a variant choice is stable for a given candidate`() {
        // It is picked from the candidate's own position, never from a shared random stream, so
        // the same house is the same house on every frame and after every rebuild.
        val house = spec(SceneObjectType.HOUSE, x = 0.37f)
        val first = SceneObjectRenderer.variantFor(house)
        repeat(20) { assertEquals(first, SceneObjectRenderer.variantFor(house)) }
    }

    @Test
    fun `both house sizes are reachable`() {
        val variants = (0 until 40)
            .map { SceneObjectRenderer.variantFor(spec(SceneObjectType.HOUSE, x = it / 40f)) }
            .toSet()
        assertEquals(
            setOf(SceneSpace.SceneVariant.HOUSE_SMALL, SceneSpace.SceneVariant.HOUSE_LARGE),
            variants,
        )
    }

    @Test
    fun `towers sit behind the village and shops among it`() {
        // The reason this is decided by depth rather than by a position hash: a four-metre bar has
        // no business on the skyline, and a twenty-metre tower none among the front gardens.
        val far = SceneObjectRenderer.variantFor(
            spec(SceneObjectType.SKYSCRAPER, depth = SceneSpace.BUILDING_TOWER_MAX_DEPTH - 0.05f),
        )
        assertEquals(SceneSpace.SceneVariant.TOWER, far)

        val nearVariants = (0 until 40)
            .map { SceneObjectRenderer.variantFor(spec(SceneObjectType.SKYSCRAPER, depth = 0.8f, x = it / 40f)) }
            .toSet()
        assertEquals(
            setOf(SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.BAR),
            nearVariants,
        )
    }

    // --- The scale pipeline ---------------------------------------------------------------

    @Test
    fun `the same object is drawn smaller the farther away it stands`() {
        val near = SceneObjectRenderer.effectiveScaleFor(spec(SceneObjectType.TREE, depth = 1f), 2400f)
        val far = SceneObjectRenderer.effectiveScaleFor(spec(SceneObjectType.TREE, depth = 0f), 2400f)
        assertTrue(far < near)
        assertEquals(SceneSpace.depthScale(0f) / SceneSpace.depthScale(1f), far / near, 0.0001f)
    }

    @Test
    fun `size variation scales an object without changing its category`() {
        val plain = SceneObjectRenderer.effectiveScaleFor(spec(SceneObjectType.TREE, x = 0.5f), 2400f)
        val bigger = SceneObjectRenderer.effectiveScaleFor(spec(SceneObjectType.TREE, x = 0.5f, scale = 1.08f), 2400f)
        assertEquals(1.08f, bigger / plain, 0.0001f)
    }

    @Test
    fun `objects scale with the viewport`() {
        // Sizes used to be absolute canvas pixels while every ground line was a fraction of screen
        // height, so the composition only worked at one screen size.
        val ref = SceneObjectRenderer.effectiveScaleFor(spec(SceneObjectType.HOUSE), 2400f)
        val half = SceneObjectRenderer.effectiveScaleFor(spec(SceneObjectType.HOUSE), 1200f)
        assertEquals(0.5f, half / ref, 0.0001f)
    }

    @Test
    fun `a tree at the front of the scene is drawn taller than a house at the same depth`() {
        // The categories are finally comparable, which is the whole point of deriving their scales
        // from declared heights instead of authoring one multiplier per category against whatever
        // internal scale its own sprite happened to use.
        val depth = 0.9f
        val treeVariant = SceneSpace.SceneVariant.TREE
        val houseVariant = SceneSpace.SceneVariant.HOUSE_SMALL
        val treePx = treeVariant.spriteUnitsTall *
            SceneObjectRenderer.effectiveScaleFor(spec(SceneObjectType.TREE, depth = depth), 2400f)
        val housePx = houseVariant.spriteUnitsTall *
            SceneObjectRenderer.effectiveScaleFor(
                StaticSceneObject(
                    SceneObjectType.HOUSE,
                    depthFraction = depth,
                    // A tileFractionX that resolves to the small house, so the comparison is
                    // against the variant whose height is being cited.
                    tileFractionX = pickSmallHouseX(),
                    scale = 1f,
                ),
                2400f,
            )
        assertTrue(treePx > housePx)
    }

    private fun pickSmallHouseX(): Float =
        (0 until 100).map { it / 100f }
            .first { SceneObjectRenderer.variantFor(spec(SceneObjectType.HOUSE, x = it)) == SceneSpace.SceneVariant.HOUSE_SMALL }
}
