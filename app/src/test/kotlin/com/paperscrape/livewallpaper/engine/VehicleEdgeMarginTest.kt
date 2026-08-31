package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REN-06: a vehicle leaves the screen only once it has actually left the screen.
 *
 * The entry and exit margin was a flat `120f` canvas pixels while a vehicle's width scales with the
 * viewport, so the two cross at a tall enough screen: the fire engine's scaled half-width passes
 * 120 px at about 3900 px of screen height, and beyond that the far end of a vehicle is still on
 * screen when its copy is declared gone. Nothing shipping is that tall -- the OnePlus 6T is 2340
 * and the tallest flagships are near 3200 -- so nothing was visibly wrong, and a bound expressed in
 * the wrong unit is one device away from being wrong.
 */
class VehicleEdgeMarginTest {

    private fun halfWidthPx(screenHeight: Float): Float =
        SceneObjectRenderer.FIRE_TRUCK_HALF_WIDTH_UNITS *
            SceneSpace.FIRE_TRUCK_BASE_SCALE *
            SceneSpace.perspectiveScaleAt(SceneSpace.ROAD_LANE_NEAR_Y_FRACTION) *
            SceneSpace.sceneScale(screenHeight)

    private fun margin(screenHeight: Float): Float =
        maxOf(SceneObjectRenderer.LEGACY_EDGE_MARGIN_PX, halfWidthPx(screenHeight) * 1.1f)

    @Test
    fun `the margin clears the widest vehicle at every plausible viewport`() {
        // Including two nobody sells, because that is the point: the rule holds rather than the
        // number happening to be big enough.
        for (height in listOf(800f, 1920f, 2340f, 2400f, 3200f, 3900f, 4800f)) {
            assertTrue(
                "at ${height}px the margin ${margin(height)} does not clear a " +
                    "${halfWidthPx(height)}px half-width",
                margin(height) > halfWidthPx(height),
            )
        }
    }

    @Test
    fun `the flat 120 does not clear it, which is the finding`() {
        // The defect, stated as the measurement that found it. If this ever stops failing, the
        // vehicle artwork or the scale table changed and the margin should be revisited.
        assertTrue(
            "a flat 120px margin should be too small at 3900px -- half-width is ${halfWidthPx(3900f)}",
            halfWidthPx(3900f) > SceneObjectRenderer.LEGACY_EDGE_MARGIN_PX,
        )
    }

    @Test
    fun `nothing moves at the viewports that exist today`() {
        // The compatibility half: on real screens the derived value is below the old floor, so the
        // floor is what is used and every vehicle enters and leaves exactly where it always has.
        for (height in listOf(2340f, 2400f)) {
            assertEquals(
                "the margin at ${height}px must still be the old 120",
                SceneObjectRenderer.LEGACY_EDGE_MARGIN_PX,
                margin(height),
                0.001f,
            )
        }
    }

    @Test
    fun `the declared half-width matches the artwork`() {
        val truck = java.io.File(drawableDir(), "firetruck_body.png")
        val width = javax.imageio.ImageIO.read(truck).width / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        assertEquals(
            "FIRE_TRUCK_HALF_WIDTH_UNITS must be half of firetruck_body's canvas",
            width / 2f,
            SceneObjectRenderer.FIRE_TRUCK_HALF_WIDTH_UNITS,
            0.001f,
        )
    }

    private fun drawableDir(): java.io.File {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = java.io.File(dir, prefix + "src/main/res/drawable-nodpi")
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate src/main/res/drawable-nodpi")
    }
}
