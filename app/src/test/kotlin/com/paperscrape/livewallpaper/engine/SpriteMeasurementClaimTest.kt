package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * REN-07: the numbers written about sprites are the numbers the sprites have.
 *
 * The audit found several load-bearing comments describing artwork that no longer ships -- a bird
 * "90x42" that is 90x24, a dolphin "360x225" that is 345x174, a canopy "270x252" that is 246x222,
 * and a window-head canvas "60 units wide" that is 53. The live constants were mostly right; the
 * comments were what a future edit would be derived from, which is the whole risk.
 *
 * This reads the PNGs so the corrected numbers cannot rot again, and separates the two kinds of
 * claim: a **measurement** must match the artwork, and a **tuned value** must not pretend to be one.
 */
class SpriteMeasurementClaimTest {

    @Test
    fun `the sprites are the sizes the comments now say`() {
        val expected = mapOf(
            // v4.26: 90x24 until the bird was redrawn as concept A "Colomba" and reduced to the
            // size it is actually read at -- ~48 px on screen, about 1.3 person heights. The flap
            // axis moved with it, from canvas row 18 to row 15, and BIRD_SPRITE_ORIGIN_X/Y_PX
            // moved to (-25, -15) in the same change: the origin *is* the axis the flap mirrors
            // about, so a canvas that changes height without it moves the bird.
            "bird_body" to (51 to 21),
            "dolphin_body" to (345 to 174),
            // v4.21 redrew the crown: 303x198 px = 101x66 u, the "Quercia larga" cushion.
            "tree_canopy" to (303 to 198),
            // v4.25: 159x171 until the window family was redrawn on a canvas trimmed to what it
            // covers, then 141x171, and 147x171 once the proportion pass gave the head back the
            // width it had -- 49 units wide instead of 53, three of them off the left, compensated
            // at WINDOW_HEAD_ANCHOR_X_UNITS in the same change.
            "person_man_summer_head_window" to (147 to 171),
        )
        for ((name, size) in expected) {
            val image = ImageIO.read(File(drawableDir(), "$name.png"))
            assertEquals("$name width", size.first, image.width)
            assertEquals("$name height", size.second, image.height)
        }
    }

    @Test
    fun `the window occupant divisor is a tuned value, not the canvas width`() {
        // The distinction the comment used to get wrong. 60 is deliberate and the canvas is the
        // canvas; asserting both is what stops somebody "fixing" one into the other. The canvas
        // moved to 49 units in v4.25 -- 53 before it was trimmed, 47 before the head was given
        // back its width -- and the divisor did not, which is the point: they were never the same
        // number, and the gap between them is still there.
        val canvasUnits = ImageIO.read(File(drawableDir(), "person_man_summer_head_window.png")).width /
            SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        assertEquals("the canvas really is 49 units", 49f, canvasUnits, 0.001f)
        assertEquals(
            "the divisor is 60 and is not the canvas width",
            60f,
            SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS,
            0.001f,
        )
        assertTrue(
            "so an occupant is drawn narrower than the nominal 85% of its pane",
            canvasUnits / SceneObjectRenderer.WINDOW_OCCUPANT_DIVISOR_UNITS < 1f,
        )
    }

    @Test
    fun `a size attributed to a named sprite is that sprite's size`() {
        // The precise form of the rule. A comment may quote a size that no longer ships -- the
        // cloud origin explains its own defect by naming the 768x510 canvas that never existed --
        // so what is checked is a size *attributed to a sprite*: `name` ... `NxM px`. That is the
        // shape every stale measurement in REN-07 had, and the shape a new one would have.
        // `px` stays **mandatory**, and v4.29 tried dropping it and put it back. The stale claim
        // the santa crop report had already reported and this test had not caught --
        // "`santa_sleigh_scene` is 624x168 with a content box of ..." -- names no unit at all, and
        // making the unit optional to reach it immediately mis-read
        // "`house_shared_window` is 22x21", which is a true statement in **local units** about a
        // 66x63 px sprite. A comment that says NxM without a unit is genuinely ambiguous here,
        // because both frames are in daily use three lines apart.
        //
        // So the rule is the other way round: a claim about a sprite's pixels must be *written in
        // the shape this reads*, and v4.29 rewrote the sleigh's two comments into it rather than
        // widening the pattern until it produced noise. `BACKLOG_v4_29.md` item 90 records that,
        // and records what is still uncovered: a pixel claim that omits its unit is invisible.
        val claim = Regex("""`([a-z0-9_]+)(?:\.png)?`[^`\n]{0,40}?is (\d{2,4})x(\d{2,4}) px""")
        var checked = 0
        for (file in kotlinSources()) {
            val source = file.readText()
            for (match in claim.findAll(source)) {
                val sprite = File(drawableDir(), match.groupValues[1] + ".png")
                if (!sprite.isFile) continue
                val image = ImageIO.read(sprite)
                assertEquals(
                    file.name + " says " + match.groupValues[1] + " is " +
                        match.groupValues[2] + "x" + match.groupValues[3],
                    image.width.toString() + "x" + image.height,
                    match.groupValues[2] + "x" + match.groupValues[3],
                )
                checked++
            }
        }
        assertTrue("the pattern matched nothing, so this test proves nothing", checked > 0)
    }

    /**
     * Every Kotlin source under `src/main`, because a hand-written file list is the failure this
     * test exists to catch, happening to this test.
     *
     * Until v4.29 the scan above named two files -- `SceneObjectRenderer.kt` and
     * `PaperRenderer.kt` -- and `BACKLOG_v4_28.md` item 82 then found three stale load-bearing
     * numbers in `GlTextureAtlas.kt` and `GlTextureCache.kt`, which were simply not being read.
     * That is the same shape as the stale golden-class list in `CLAUDE.md` §5, which cost v4.28 a
     * missed `SkyWaterGoldenTest`: **ask the tree, do not keep the list.** Walking the directory
     * costs a few milliseconds and cannot go out of date.
     */
    private fun kotlinSources(): List<File> =
        walkUp("src/main/kotlin").walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()

    private fun drawableDir(): File = walkUp("src/main/res/drawable-nodpi")

    private fun sourceDir(): File = walkUp("src/main/kotlin/com/paperscrape/livewallpaper/engine")

    private fun walkUp(suffix: String): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, prefix + suffix)
                if (candidate.isDirectory) return candidate
            }
            dir = dir.parentFile
        }
        error("could not locate " + suffix)
    }
}
