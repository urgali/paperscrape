package com.paperscrape.livewallpaper.engine

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * When winter is on, everything with a roof wears snow; when it is off, nothing does.
 *
 * ### Why this exists
 *
 * The v4.21 snow audit confirmed all five building types and all three plants carry a winter
 * overlay, but it confirmed it by reading `drawStaticObject`'s branches one by one -- which is
 * exactly how a *sixth* building type would ship bare: nothing fails when a new variant's draw
 * function simply never mentions `winterColorsEnabled`. This test states the coverage as a rule
 * over [SceneSpace.SceneVariant] itself, so a variant added tomorrow fails here until somebody
 * either gives it snow or writes down why it has none.
 *
 * ### What is asserted
 *
 * The whole pipeline is driven end to end -- real [PaperRenderer], real [SceneObjectRenderer],
 * real layout resolution, only the rasteriser swapped for a recorder -- twice per placement: once
 * with `winterColorsEnabled` and once without. For every variant in [SNOW_BY_VARIANT] the winter
 * render must blit that overlay, registered to its own building (above the wall's roofline for a
 * building, on the crown's upper half for a plant), and the plain render must blit no overlay
 * from [ALL_ROOF_SNOW] at all. Every other variant must be named in [BARE_BY_DESIGN] with its
 * reason, so the enum is partitioned and a new entry lands in neither set -- which is the failure.
 *
 * The origin constants themselves are pinned by [TreeArtworkAlignmentTest] (cap on crown, pixel
 * for pixel) and `PreviewRendererAgreementTest` (renderer and gallery agree); this test pins that
 * the gate and the coverage exist at all, which neither of those can see.
 */
@RunWith(AndroidJUnit4::class)
class BuildingRoofSnowTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    /**
     * Every winter roof overlay the scene owns; the winter-off render may blit none of these.
     *
     * The buildings' half is **read out of [NeighbourhoodTable]** rather than listed, which is
     * what keeps this test honest now that a building is a stack of pieces: a roof alternative
     * added tomorrow brings its own drift into this set without anybody remembering to add it,
     * and a roof alternative that ships *without* a drift fails [everyBuildingWearsSnowInWinterAndNoneWithoutIt]
     * because its family will have a deal that blits none.
     */
    private val BUILDING_SNOW: Set<Int> = (NeighbourhoodTable.FAMILIES.values + NeighbourhoodTable.STOREFRONTS.values)
        .flatMap { it.slots }
        .flatMap { it.options + it.heights }
        .flatMap { piece -> piece.parts.filter { it.role == PartRole.SNOW }.map { it.res } }
        .toSet()

    private val ALL_ROOF_SNOW = BUILDING_SNOW + setOf(
        R.drawable.tree_canopy_snowcap,
        R.drawable.tree_fir_snow,
        R.drawable.palmtree_fronds_frost,
    )

    /** The snow a family may wear, and the piece whose blit anchors its roofline. */
    private fun buildingRoofed(variant: SceneSpace.SceneVariant): Roofed = familyRoofed(NeighbourhoodTable.FAMILIES.getValue(variant))

    private fun familyRoofed(family: BuildingFamily, walls: Boolean = false): Roofed {
        val snow = family.slots.flatMap { it.options + it.heights }
            .flatMap { piece -> piece.parts.filter { it.role == PartRole.SNOW }.map { it.res } }
            .toSet()
        // The ground floor (or the tower's body): the first fixed card of the first slot, which
        // every deal of the family places, so it is always on screen to measure against. Since
        // v5.11 the tower's body is one of three heights by a coin of its own, so the reference
        // is whichever of the three the deal gave it.
        //
        // **A building among the houses is measured against its walls** (v5.13, `walls`): the six are one
        // piece each, and the fire station's fixed card holds the lamp over its tower's roof, above the
        // snow on the roof's slopes; each wall mask -- the block's and the tower's -- ends at its roofline.
        val first = family.slots.first()
        val reference = if (walls) {
            (first.options + first.heights).flatMap { piece -> piece.parts.filter { it.role == PartRole.WALL_MASK }.map { it.res } }.toSet()
        } else {
            (first.options + first.heights)
                .map { piece -> piece.parts.first { it.role == PartRole.FIXED }.res }
                .toSet()
        }
        return Roofed(snow, reference, aboveTop = true)
    }

    /**
     * variant -> (its winter overlay, the sprite whose blit defines the roofline reference,
     * whether the overlay must clear the reference's top -- true for a building, whose wall top
     * *is* the roofline -- or merely land on its upper half -- the plants, whose snow lies on
     * tiers and fronds below their own apex).
     */
    private data class Roofed(val snow: Set<Int>, val reference: Set<Int>, val aboveTop: Boolean)

    private val SNOW_BY_VARIANT: Map<SceneSpace.SceneVariant, Roofed> =
        NeighbourhoodTable.FAMILIES.keys.associateWith { buildingRoofed(it) } + mapOf(
            SceneSpace.SceneVariant.TREE to
                Roofed(setOf(R.drawable.tree_canopy_snowcap), setOf(R.drawable.tree_trunk), true),
            SceneSpace.SceneVariant.FIR to
                Roofed(setOf(R.drawable.tree_fir_snow), setOf(R.drawable.tree_fir), false),
            SceneSpace.SceneVariant.PALM_TREE to
                Roofed(setOf(R.drawable.palmtree_fronds_frost), setOf(R.drawable.palmtree_trunk), false),
        )

    /** Roofless by design, each with the reason a review accepted. */
    private val BARE_BY_DESIGN: Map<SceneSpace.SceneVariant, String> = mapOf(
        SceneSpace.SceneVariant.PARASOL to
            "a parasol in the snow is a contradiction; every winter theme hides them instead",
        SceneSpace.SceneVariant.SNOWMAN to "is snow",
        SceneSpace.SceneVariant.GIFT to "a hand-placed decoration, set out rather than snowed on",
        SceneSpace.SceneVariant.PENGUIN to "an animal, not a structure",
        SceneSpace.SceneVariant.BUNNY to "an animal, not a structure",
        SceneSpace.SceneVariant.EASTER_EGG to "an Easter decoration; no theme shows it in winter",
        SceneSpace.SceneVariant.PUMPKIN to "a Halloween decoration; no theme shows it in winter",
    )

    @Test
    fun everyVariantIsEitherSnowedOrNamedBare() {
        for (variant in SceneSpace.SceneVariant.entries) {
            assertTrue(
                "$variant has neither a winter overlay in SNOW_BY_VARIANT nor a stated reason " +
                    "in BARE_BY_DESIGN -- a new variant must choose one before it ships",
                SNOW_BY_VARIANT.containsKey(variant) xor BARE_BY_DESIGN.containsKey(variant),
            )
        }
        assertEquals(
            "the two maps must partition the enum exactly",
            SceneSpace.SceneVariant.entries.size,
            SNOW_BY_VARIANT.size + BARE_BY_DESIGN.size,
        )
    }

    /**
     * **A place among the houses wears the snow of whichever building stands in it** (v5.13): the street
     * deals any of the six to any place ([StorefrontStreet]), so each place is rendered once per building,
     * scrolled to the first copy of it the street gives that building, and asked for that building's snow.
     */
    @Test
    fun everyBuildingWearsSnowInWinterAndNoneWithoutIt() {
        for ((variant, roofed) in SNOW_BY_VARIANT) {
            if (variant in PLACES) {
                for (storefront in Storefront.ALL) {
                    val copy = (0 until 200).first {
                        StorefrontStreet.storefrontAt(THEME.hashCode(), StorefrontStreet.placeIndex(it, 0, 1)) == storefront
                    }
                    assertSnowed("$variant/$storefront", layoutFor(variant), familyRoofed(storefront.family, walls = true), christmas = false, copy = copy)
                }
            } else {
                assertSnowed("$variant", layoutFor(variant), roofed, christmas = variant == SceneSpace.SceneVariant.FIR, copy = null)
            }
        }
    }

    /**
     * Renders [layout] with winter on and off -- scrolled so that copy [copy] of its one object stands in the
     * middle of the screen, or at offset 0 -- and asserts [roofed]'s snow on the first and no roof snow on the
     * second.
     */
    private fun assertSnowed(label: String, layout: List<StaticSceneObject>, roofed: Roofed, christmas: Boolean, copy: Int?) {
        // X = U + (f + c) T, U = -s W p, T = 2W: copy c of the object at the screen's middle.
        val offset = copy?.let { ((layout.first().tileFractionX + it) * 2f - 0.5f) / PaperRenderer.HILL_PARALLAX } ?: 0f
        val winter = render(layout, winter = true, christmas = christmas, offset = offset)
        val plain = render(layout, winter = false, christmas = christmas, offset = offset)

        val caps = winter.filter { it.resId in roofed.snow }
        assertTrue(
            "$label should wear one of ${roofed.snow.joinToString { nameOf(it) }} in winter",
            caps.isNotEmpty(),
        )

        val refs = winter.filter { it.resId in roofed.reference }
        assertTrue(
            "$label should blit its reference ${roofed.reference.joinToString { nameOf(it) }}",
            refs.isNotEmpty(),
        )
        for (cap in caps) {
            val ref = refs.minByOrNull { kotlin.math.abs(it.rect.centerX() - cap.rect.centerX()) }!!
            val roofline = if (roofed.aboveTop) ref.rect.top else ref.rect.centerY()
            assertTrue(
                "$label: ${nameOf(cap.resId)} top ${cap.rect.top} should clear its " +
                    "roofline $roofline (reference ${nameOf(ref.resId)})",
                cap.rect.top < roofline,
            )
            // Overlap, not containment: a crown is wider than the trunk that anchors it, so
            // "centre inside the reference" would fail every tree on its own geometry.
            assertTrue(
                "$label: the snow should lie over its own building horizontally",
                cap.rect.right > ref.rect.left && cap.rect.left < ref.rect.right,
            )
        }

        val stray = plain.filter { it.resId in ALL_ROOF_SNOW }
        assertTrue(
            "$label: winter off must draw no roof snow at all, found " +
                stray.joinToString { nameOf(it.resId) },
            stray.isEmpty(),
        )
    }

    private val PLACES = setOf(SceneSpace.SceneVariant.RESTAURANT, SceneSpace.SceneVariant.SCHOOL, SceneSpace.SceneVariant.BAR)

    // ------------------------------------------------------------------ placement

    /**
     * A layout that makes the given variant appear at least once on screen at offset 0.
     *
     * Depth places the three building styles ([SceneObjectRenderer.variantFor] resolves by
     * depth); a house's size is a position hash, so the x is *searched* through the same
     * resolver rather than hand-picked; a fir is a per-tree Christmas roll, so the wood is made
     * wide enough that the roll's own determinism guarantees both outcomes -- the same layout
     * always deals the same firs, asserted in [render].
     */
    private fun layoutFor(variant: SceneSpace.SceneVariant): List<StaticSceneObject> = when (variant) {
        SceneSpace.SceneVariant.TOWER ->
            listOf(StaticSceneObject(SceneObjectType.SKYSCRAPER, 0.10f, 0.25f))
        SceneSpace.SceneVariant.RESTAURANT ->
            listOf(StaticSceneObject(SceneObjectType.SKYSCRAPER, 0.40f, 0.25f))
        SceneSpace.SceneVariant.BAR ->
            listOf(StaticSceneObject(SceneObjectType.SKYSCRAPER, 0.70f, 0.25f))
        // v5.6F: the middle third of the shop band. This test's own doc said a *sixth* building
        // type is exactly what would ship bare, and it caught the school by deriving its coverage
        // from `NeighbourhoodTable.FAMILIES.keys` -- it only needed somewhere to stand.
        SceneSpace.SceneVariant.SCHOOL ->
            listOf(StaticSceneObject(SceneObjectType.SKYSCRAPER, 0.55f, 0.25f))
        SceneSpace.SceneVariant.HOUSE_SMALL -> listOf(houseResolving(SceneSpace.SceneVariant.HOUSE_SMALL))
        SceneSpace.SceneVariant.HOUSE_LARGE -> listOf(houseResolving(SceneSpace.SceneVariant.HOUSE_LARGE))
        SceneSpace.SceneVariant.PALM_TREE ->
            listOf(StaticSceneObject(SceneObjectType.PALM_TREE, 0.5f, 0.25f))
        // One wood serves both tree states: at least one of these deals a fir under the
        // Christmas roll and at least one stays leafy, or the assertions in the caller fail.
        SceneSpace.SceneVariant.TREE, SceneSpace.SceneVariant.FIR ->
            (0 until 12).map { StaticSceneObject(SceneObjectType.TREE, 0.5f, 0.04f + it * 0.04f) }
        else -> throw IllegalArgumentException("no placement for $variant")
    }

    private fun houseResolving(wanted: SceneSpace.SceneVariant): StaticSceneObject {
        for (i in 0 until 40) {
            val candidate = StaticSceneObject(SceneObjectType.HOUSE, 0.5f, 0.15f + i * 0.005f)
            if (SceneObjectRenderer.variantFor(candidate) == wanted) return candidate
        }
        throw AssertionError("no xFraction in the probe range resolves to $wanted")
    }

    // ------------------------------------------------------------------ rendering

    private data class Blit(val resId: Int, val rect: RectF)

    private fun render(
        layout: List<StaticSceneObject>,
        winter: Boolean,
        christmas: Boolean,
        offset: Float = 0f,
    ): List<Blit> {
        val themeId = THEME
        val theme = ThemeCatalog.byId(themeId)
        val defaults = defaultCustomizationFor(themeId)
        val customization = defaults.copy(
            winterColorsEnabled = winter,
            fallColorsEnabled = false,
            christmasDecorationsEnabled = christmas,
            halloweenEnabled = false,
            houses = defaults.houses.copy(visible = true, density = 1f),
            buildings = defaults.buildings.copy(visible = true, density = 1f),
            trees = defaults.trees.copy(visible = true, density = 1f),
            cars = defaults.cars.copy(visible = false),
            people = defaults.people.copy(visible = false),
        )
        CustomThemeRegistry.update(
            CustomThemeData(
                overrides = mapOf(
                    themeId to CustomThemeEntry(
                        id = themeId,
                        name = theme.displayName,
                        theme = theme,
                        layout = SceneObjectLayout(staticObjects = layout, cars = emptyList()),
                        customization = customization,
                    ),
                ),
            ),
        )
        try {
            val recorder = SpriteRecordingCanvas()
            val renderer = PaperRenderer(WIDTH, HEIGHT, context)
            renderer.theme = theme
            renderer.sceneCustomization = customization
            renderer.liveWeatherOverride = null
            renderer.homeScreenOffset = offset
            renderer.swipeScrollEnabled = offset != 0f
            renderer.scrollSpeed = 0f
            renderer.parallaxStrength = 1f
            renderer.draw(recorder, SunPositionCalculator.compute(hour24 = 13f), SceneTime(50.0), 0f)
            return recorder.blits
        } finally {
            CustomThemeRegistry.update(CustomThemeData.EMPTY)
        }
    }

    private fun nameOf(resId: Int): String = context.resources.getResourceEntryName(resId)

    /**
     * A [SceneCanvas] that rasterises nothing and records every sprite blit in screen space --
     * the same transform-tracking recorder as `FallingLeafContinuityTest`'s, pointed at sprites.
     */
    private class SpriteRecordingCanvas : SceneCanvas {
        val blits = mutableListOf<Blit>()
        private val stack = ArrayDeque<Matrix>()
        private var matrix = Matrix()

        override fun save() {
            stack.addLast(Matrix(matrix))
        }

        override fun restore() {
            matrix = stack.removeLast()
        }

        override fun translate(dx: Float, dy: Float) {
            matrix.preTranslate(dx, dy)
        }

        override fun scale(sx: Float, sy: Float) {
            matrix.preScale(sx, sy)
        }

        override fun rotate(degrees: Float) {
            matrix.preRotate(degrees)
        }

        // A sprite cut to a box (v5.12, a person walking out of a window) is recorded as the blit it is.
        override fun drawSpriteClipped(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
            additive: Boolean,
            clipLeft: Float,
            clipTop: Float,
            clipRight: Float,
            clipBottom: Float,
        ) = drawSprite(resId, source, left, top, tintColor, alpha, additive)

        override fun drawSprite(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
            additive: Boolean,
        ) {
            // SCENE_UNITS art arrives through SpriteBlitter with its 1/3 grid scale already on
            // the canvas, so the rect is built in raw sprite pixels and mapped -- the same
            // convention note FallingLeafContinuityTest's recorder carries.
            val bitmap = source.bitmapFor(resId)
            val rect = RectF(left, top, left + bitmap.width, top + bitmap.height)
            matrix.mapRect(rect)
            blits.add(Blit(resId, rect))
        }

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = Unit
        override fun drawDisc(resId: Int, source: SpriteSource, cx: Float, cy: Float, radius: Float, colour: Int, alpha: Int) = Unit
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawWedge(
            cx: Float,
            cy: Float,
            radius: Float,
            startAngle: Float,
            sweepAngle: Float,
            paint: Paint,
        ) = Unit

        override fun drawShape(shape: SceneShape, paint: Paint) = Unit
        override fun drawVerticalGradientShape(
            shape: SceneShape,
            gradientTopY: Float,
            gradientBottomY: Float,
            topColor: Int,
            bottomColor: Int,
            alpha: Int,
        ) = Unit

        override fun drawVerticalGradientRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            topColor: Int,
            bottomColor: Int,
        ) = Unit

        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) = Unit
    }

    private companion object {
        /** The theme every render uses: its seed is the street's. */
        const val THEME = "sunset"
        const val WIDTH = 1080
        const val HEIGHT = 2400
    }
}
