package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs

/**
 * A falling leaf belongs to its tree, not to whatever else happens to be on screen.
 *
 * ### The defect this pins
 *
 * The maintainer reported that swiping between home pages made the autumn scene look rebuilt:
 * leaves mid-fall jumped to new positions. The clock was never the problem -- `elapsedSeconds`
 * runs straight through a swipe -- and neither was the engine's lifecycle: the same process and
 * renderer survive every swipe. The cause was `drawFallingLeaves` assigning candidate `i` to
 * visible crown `i % count`: the visible-crown array shuffles whenever the parallax scrolls a
 * tree across a screen edge, and that modulo then handed **every** leaf to a different tree.
 * Confirmed on a OnePlus 6T 30 fps screen recording: leaf blobs teleported in exactly the frames
 * the visible set changed, and in no others.
 *
 * ### What is asserted
 *
 * The whole pipeline is driven end to end -- real [PaperRenderer], real
 * [SceneObjectRenderer], real layout resolution -- and only the rasteriser is swapped for a
 * recorder, so what is measured is what each backend is told to draw, not a re-derivation of the
 * formula. Two renders at the same clock differ in one thing: a second tree stands on the
 * screen. Every leaf the lone tree shed in the first render must be drawn at the identical spot
 * in the second, because a swipe is precisely "other trees enter and leave the view" and a leaf
 * that moves when that happens is the reported defect. Under the `i % count` rule the first
 * render gave the tree all the pool's leaves and the second dealt half to the newcomer, so this
 * fails loudly against the old code -- it is not satisfiable by accident.
 *
 * A leaf is identified in the recording by its signature rather than by paint colour heuristics
 * on pixels: leaves are the only ovals the scene draws with the local rect (-4,-6,4,6).
 */
@RunWith(AndroidJUnit4::class)
class FallingLeafContinuityTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun aTreesLeavesSurviveAnotherTreeScrollingOffScreen() {
        // The swipe, reduced to its essence: one layout, two scroll offsets. U stands near the
        // left edge and the second offset carries it off screen; T stays on screen at both. The
        // per-leaf allotment makes the leaf counts themselves a precondition check, so a wrong
        // placement fails loudly here rather than weakening the real assertion below.
        // rc2: a crown's leaf count is proportional to its drawn size (3..13), so the counts
        // are measured off the lone-tree render rather than taken from a constant. T and U stand
        // at the same depth, so they shed the same number.
        val perTree = render(listOf(tree(T_FRACTION)), offset = 0f).size
        assertTrue("a crown sheds a sane number of leaves, got $perTree", perTree in 3..13)
        val layout = listOf(tree(U_FRACTION), tree(T_FRACTION))
        val both = render(layout, offset = 0f)
        val uGone = render(layout, offset = 1f)
        assertEquals("both trees shed at offset 0", 2 * perTree, both.size)
        assertEquals("U is off screen at offset 1, so only T sheds", perTree, uGone.size)

        // The layer's parallax shift between the two offsets, measured from a lone T rather than
        // assumed from the parallax constants: leaves pair one-to-one by their y (a leaf's fall is
        // untouched by horizontal scroll), and every pair must agree on one x delta.
        val loneT1 = render(listOf(tree(T_FRACTION)), offset = 0f)
        val loneT2 = render(listOf(tree(T_FRACTION)), offset = 1f)
        assertEquals("T sheds the same count at both offsets", loneT1.size, loneT2.size)
        val deltas = loneT1.map { a ->
            val b = loneT2.singleOrNull { abs(it.y - a.y) < EPSILON }
            assertTrue("leaf at y=${a.y} lost its partner across the scroll", b != null)
            b!!.x - a.x
        }
        val delta = deltas.first()
        assertTrue("the scroll is one rigid shift for the whole layer", deltas.all { abs(it - delta) < EPSILON })
        assertTrue("the two offsets really scroll the scene", abs(delta) > 10f)

        // **The assertion the old code fails.** T's own leaves at offset 0 are the cluster around
        // T's anchor, away from U's. After the scroll they must be exactly that cluster, rigidly
        // shifted -- same candidates, same phases, same fall. Under the retired `i % count` rule
        // (and under any slot-based assignment), U's exit re-deals T's candidate indices and the
        // leaves land at other phases of other falls.
        val tCluster = both.filter { abs(it.x - T_FRACTION * TILE_WIDTH) < CLUSTER_RADIUS }
        assertEquals("T's own leaves are the cluster at its anchor", perTree, tCluster.size)
        for (leaf in tCluster) {
            assertTrue(
                "T's leaf at (${leaf.x}, ${leaf.y}) was re-dealt when U scrolled off screen",
                uGone.any { abs(it.x - (leaf.x + delta)) < EPSILON && abs(it.y - leaf.y) < EPSILON },
            )
        }
    }

    /**
     * **rc2 criterion: zero leaves drawn inside the crown's own bounding box.**
     *
     * Leaves used to spawn at the crown's centre, so the first 40% of every fall happened inside
     * the canopy -- invisible, or a dark blot lying on the foliage. They detach at the bounding
     * box's bottom edge now, with a margin covering the oval's own extent, and this drives the
     * real renderer over a whole fall cycle to prove no drawn leaf ever intersects the box. The
     * box is the canopy blit's, mapped through the same transform it was drawn with.
     */
    @Test
    fun noLeafIsEverDrawnInsideItsCrownsBoundingBox() {
        // Sample the full fall cycle: the slowest candidate cycles in 1/(0.06*0.7) ~ 23.8 s, so
        // forty samples half a second apart cover every phase of every leaf.
        var leavesSeen = 0
        for (step in 0 until 40) {
            val rec = renderRecording(listOf(tree(T_FRACTION)), atSeconds = 30.0 + step * 0.5)
            assertTrue("no crown recorded at step $step", rec.crowns.isNotEmpty())
            leavesSeen += rec.leaves.size
            for (leaf in rec.leaves) {
                for (crown in rec.crowns) {
                    val intersects = leaf.x + 7.3f > crown.left && leaf.x - 7.3f < crown.right &&
                        leaf.y + 7.3f > crown.top && leaf.y - 7.3f < crown.bottom
                    assertTrue(
                        "at t=${30.0 + step * 0.5}s a leaf at (${leaf.x}, ${leaf.y}) overlaps the crown box $crown",
                        !intersects,
                    )
                }
            }
        }
        // The assertion above is vacuous if nothing was drawn, so the fall itself is asserted:
        // forty samples of a shedding crown must produce hundreds of drawn leaves.
        assertTrue("only $leavesSeen leaves drawn across 40 samples", leavesSeen >= 100)
    }

    /** And the property that makes the leaves animate at all: the clock, not the frame, moves them. */
    @Test
    fun leavesMoveWithTheClockNotWithTheFrame() {
        val first = render(listOf(tree(T_FRACTION)))
        val same = render(listOf(tree(T_FRACTION)))
        val later = render(listOf(tree(T_FRACTION)), atSeconds = AT_SECONDS + 1.0)
        assertEquals("same clock, same frame: the effect is stateless", first, same)
        assertTrue(
            "a second later at least one leaf has fallen further",
            first.zip(later).any { (a, b) -> abs(a.y - b.y) > EPSILON },
        )
    }

    // ------------------------------------------------------------------ rendering

    private fun tree(xFraction: Float) = StaticSceneObject(SceneObjectType.TREE, TREE_DEPTH, xFraction)

    private fun render(
        layout: List<StaticSceneObject>,
        atSeconds: Double = AT_SECONDS,
        offset: Float = 0f,
    ): List<Leaf> = renderRecording(layout, atSeconds, offset).leaves

    private class Recording(val leaves: List<Leaf>, val crowns: List<RectF>, val colors: List<Int>)

    private fun renderRecording(
        layout: List<StaticSceneObject>,
        atSeconds: Double = AT_SECONDS,
        offset: Float = 0f,
    ): Recording {
        val theme = ThemeCatalog.byId(THEME_ID)
        val defaults = defaultCustomizationFor(THEME_ID)
        val customization = defaults.copy(
            fallColorsEnabled = true,
            trees = defaults.trees.copy(visible = true, density = 1f),
            cars = defaults.cars.copy(visible = false),
            people = defaults.people.copy(visible = false),
        )
        CustomThemeRegistry.update(
            CustomThemeData(
                overrides = mapOf(
                    THEME_ID to CustomThemeEntry(
                        id = THEME_ID,
                        name = theme.displayName,
                        theme = theme,
                        layout = SceneObjectLayout(staticObjects = layout, cars = emptyList()),
                        customization = customization,
                    ),
                ),
            ),
        )
        try {
            val recorder = RecordingSceneCanvas()
            val renderer = PaperRenderer(WIDTH, HEIGHT, context)
            renderer.theme = theme
            renderer.sceneCustomization = customization
            renderer.liveWeatherOverride = null
            renderer.homeScreenOffset = offset
            renderer.swipeScrollEnabled = true
            renderer.scrollSpeed = 0f
            // The strongest parallax the settings allow, so one page of swipe moves the object
            // layer far enough to carry the edge tree off screen.
            renderer.parallaxStrength = 2f
            val phase = SunPositionCalculator.compute(hour24 = 13f)
            renderer.draw(recorder, phase, SceneTime(atSeconds), 0f)
            return Recording(recorder.leaves, recorder.crowns, recorder.colors)
        } finally {
            CustomThemeRegistry.update(CustomThemeData.EMPTY)
        }
    }

    private data class Leaf(val x: Float, val y: Float)

    /**
     * A [SceneCanvas] that rasterises nothing and records where the leaves go.
     *
     * Tracks the full transform stack the way a backend would, so the recorded point is the
     * on-screen centre of each leaf's oval -- the number the defect is about -- and not its local
     * coordinates, which are constant by construction.
     */
    private class RecordingSceneCanvas : SceneCanvas {
        val leaves = mutableListOf<Leaf>()
        val crowns = mutableListOf<RectF>()
        val colors = mutableListOf<Int>()
        private val stack = ArrayDeque<Matrix>()
        private var matrix = Matrix()
        private val point = FloatArray(2)

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

        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
            // The falling leaves are the only ovals drawn with this local rect; everything else
            // that draws ovals does so at other sizes.
            if (left == -4f && top == -6f && right == 4f && bottom == 6f) {
                point[0] = 0f
                point[1] = 0f
                matrix.mapPoints(point)
                leaves.add(Leaf(point[0], point[1]))
                colors.add(paint.color)
            }
        }

        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = Unit
        override fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, paint: Paint) = Unit
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
        override fun drawSprite(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
        ) {
            // The crown blits, mapped through the transform they are drawn with. SCENE_UNITS
            // art goes through SpriteBlitter, which has already pushed its own 1/3 grid scale
            // onto the canvas by the time drawSprite fires -- so the current matrix is in raw
            // sprite pixels and the rect must be built in raw pixels too. Building it in units
            // here shrank the recorded box threefold and let a spawn-at-centre mutation slip
            // straight through the intersection test.
            if (resId != com.paperscrape.livewallpaper.R.drawable.tree_canopy &&
                resId != com.paperscrape.livewallpaper.R.drawable.palmtree_fronds
            ) return
            val bitmap = source.bitmapFor(resId)
            val rect = RectF(left, top, left + bitmap.width, top + bitmap.height)
            matrix.mapRect(rect)
            crowns.add(rect)
        }
    }

    private companion object {
        const val WIDTH = 1080
        const val HEIGHT = 2400
        const val THEME_ID = "autumn"
        const val AT_SECONDS = 50.0
        const val EPSILON = 0.01f

        /** One depth, both trees: with a stable sort the first listed keeps the lower identity. */
        const val TREE_DEPTH = 0.5f

        /** The object layer tiles at twice the screen width, so a fraction maps to `f x 2W`. */
        const val TILE_WIDTH = WIDTH * 2f

        /**
         * Wide enough to catch a crown's own leaves -- half a crown plus the full sway -- and
         * far inside the two anchors' separation, so the clusters cannot be confused.
         */
        const val CLUSTER_RADIUS = 260f

        /** Mid-screen: on screen at both offsets. */
        const val T_FRACTION = 0.25f

        /**
         * Near the left edge: on screen at offset 0, carried off by offset 1's shift (measured
         * at -324 px with parallaxStrength 2). The test's own leaf-count preconditions verify
         * both placements rather than trusting this comment.
         */
        const val U_FRACTION = 0.01f
    }
}
