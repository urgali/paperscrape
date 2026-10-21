package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.imageio.ImageIO

/**
 * Pins where Santa's sleigh group of v5.15 lands and how large it is drawn, against what actually ships.
 *
 * The group is five sprites placed by [SantaSleighTable] -- the sleigh with Santa and the presents, and the reindeer in
 * four poses -- which `tools/assets/sleigh/build_sleigh.py` writes from the geometry it draws the SVGs with
 * (`tests/test_sleigh.py` holds that the table is the generator's output). What is asserted here is what the renderer
 * relies on, read from the PNGs' own pixels:
 *
 *  - **the sleigh's top is Santa's hat**: the content's first row, through [SantaSleighTable.SLEIGH_Y], is
 *    [SantaSleighTable.SANTA_TOP], the height the size is measured from;
 *  - **the group's extent is its pieces'**: [SantaSleighTable.LEFT] is the leftmost content of the leading reindeer in
 *    any pose, [SantaSleighTable.RIGHT] the sleigh's right edge -- what the effect's margins keep a flight out of sight
 *    with (`SantaSleighDrawer.halfWidthPx`);
 *  - **the four poses share one frame and are four drawings**: each pose's body sits on the reindeer's middle, so the
 *    gallop does not jump, and no two poses are the same picture;
 *  - **the size is in proportion to the people** ([SantaSleighScale]): on every screen Santa is
 *    [SantaSleighTable.SANTA_TO_PEDESTRIAN] times an adult walking on the near row of the pavement, never past 3 px a
 *    unit; on the BV6600 the narrowest band the generator draws ([SantaSleighTable.SANTA_MIN_BAND_UNITS]) and both
 *    cords are 2 px or more.
 */
class SantaSleighOriginTest {

    private companion object {
        val POSES = (0..3).map { "santa_reindeer_$it" }

        val drawableDir: File by lazy {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                for (prefix in listOf("", "app/")) {
                    val candidate = File(dir, "${prefix}src/main/res/drawable-nodpi")
                    if (candidate.isDirectory) return@lazy candidate
                }
                dir = dir.parentFile
            }
            throw AssertionError("could not locate src/main/res/drawable-nodpi from ${File(".").absolutePath}")
        }

        /** The generator, for the one number it states about the phone and the app computes again. */
        val generator: File by lazy {
            var dir: File? = drawableDir
            while (dir != null && !File(dir, "tools/assets/sleigh/build_sleigh.py").isFile) dir = dir.parentFile
            File(dir ?: throw AssertionError("tools/assets/sleigh/build_sleigh.py not found"), "tools/assets/sleigh/build_sleigh.py")
        }
    }

    /** The alpha bounding box of what the sprite actually draws, right and bottom exclusive. */
    private data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int)

    private fun contentBox(name: String): Box {
        val file = File(drawableDir, "$name.png")
        assertTrue("${file.path} does not exist", file.isFile)
        val image = ImageIO.read(file)
        var left = image.width
        var top = image.height
        var right = 0
        var bottom = 0
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                if ((image.getRGB(x, y) ushr 24) < 128) continue
                if (x < left) left = x
                if (y < top) top = y
                if (x >= right) right = x + 1
                if (y >= bottom) bottom = y + 1
            }
        }
        assertTrue("$name draws nothing at all", right > left && bottom > top)
        return Box(left, top, right, bottom)
    }

    private fun pixels(name: String): List<Int> {
        val image = ImageIO.read(File(drawableDir, "$name.png"))
        return IntArray(image.width * image.height).also { image.getRGB(0, 0, image.width, image.height, it, 0, image.width) }.toList()
    }

    @Test
    fun `the sleigh's first row is Santa's hat, the height the size is measured from`() {
        val top = SantaSleighTable.SLEIGH_Y + contentBox("santa_sleigh").top / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        assertEquals("the sleigh's content starts where SANTA_TOP says", SantaSleighTable.SANTA_TOP, top, 0.7f)
        assertTrue("Santa stands above the rim", SantaSleighTable.SANTA_BOTTOM - SantaSleighTable.SANTA_TOP > 30f)
    }

    @Test
    fun `the group's extent is its pieces'`() {
        val unit = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        val leftmost = POSES.indices.minOf { p ->
            SantaSleighTable.LEAD_X + SantaSleighTable.DEER_X[p] + contentBox(POSES[p]).left / unit
        }
        assertEquals("LEFT is the leading reindeer's leftmost paper", leftmost, SantaSleighTable.LEFT, 1f)
        val right = SantaSleighTable.SLEIGH_X + contentBox("santa_sleigh").right / unit
        assertEquals("RIGHT is the sleigh's right edge", right, SantaSleighTable.RIGHT, 1f)
    }

    @Test
    fun `the four poses share the reindeer's frame and are four drawings`() {
        // Each pose's content is drawn about the reindeer's middle: its centre, through DEER_X/Y, within a few units of
        // the others', so the reindeer does not jump from one pose to the next.
        val unit = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
        val centres = POSES.indices.map { p ->
            val b = contentBox(POSES[p])
            (SantaSleighTable.DEER_X[p] + (b.left + b.right) / 2f / unit) to (SantaSleighTable.DEER_Y[p] + (b.top + b.bottom) / 2f / unit)
        }
        for ((p, c) in centres.withIndex()) {
            assertEquals("pose $p across", centres[0].first, c.first, 6f)
            assertEquals("pose $p down", centres[0].second, c.second, 6f)
        }
        val drawings = POSES.map { pixels(it) }
        for (i in drawings.indices) for (j in i + 1 until drawings.size) {
            assertNotEquals("poses $i and $j are the same picture", drawings[i], drawings[j])
        }
        assertEquals(4, SantaSleighTable.DEER_RES.size)
    }

    @Test
    fun `Santa is in the same proportion to the people on every screen`() {
        for (height in floatArrayOf(1440f, 2340f, 2400f, 3200f)) {
            val sc = SantaSleighScale.pxPerUnit(height)
            val santa = SantaSleighScale.SANTA_UNITS_TALL * sc
            // An adult on the near row of the pavement as the street draws one: its 80 units of sprite at the walkers'
            // scale there (`SceneObjectRenderer`, read below) -- not the sleigh's own reading, metres through the
            // projection, which until v5.15C2 this compared with itself (inventory I-975).
            val pedestrian = SceneSpace.PERSON_SPRITE_UNITS_TALL * SceneSpace.PERSON_BASE_SCALE *
                SceneSpace.perspectiveScaleAt(SceneSpace.PAVEMENT_NEAR_Y_FRACTION) * SceneSpace.sceneScale(height)
            assertEquals("the sleigh's pedestrian on a screen $height px tall", pedestrian, SantaSleighScale.pedestrianPx(height), 0.001f)
            if (sc < SantaSleighScale.MAX_PX_PER_UNIT) {
                assertEquals("on a screen $height px tall", SantaSleighTable.SANTA_TO_PEDESTRIAN, santa / pedestrian, 0.001f)
            }
            assertTrue("never past the sprites' own 3 px a unit", sc <= SantaSleighScale.MAX_PX_PER_UNIT)
        }
        // The street draws its walkers at that scale.
        val street = File(drawableDir.parentFile!!.parentFile, "kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt").readText()
        assertTrue(street.contains("val s = SceneSpace.PERSON_BASE_SCALE * SceneSpace.perspectiveScaleAt(rowYFraction) * sceneScale"))
        // The BV6600's adult on the near row, which the generator thickens the drawing for, is the app's own.
        val stated = Regex("""PEDESTRIAN_PX_BV6600 = 1\.75 \* 45\.0 \* \(1440 / 2400\) \* \(\(0\.807 - 0\.655\) / \(0\.846 - 0\.655\)\)""")
        assertTrue("build_sleigh.py's pedestrian is no longer SceneSpace's", stated.containsMatchIn(generator.readText()))
        assertEquals(1.75f * 45f * 0.6f * ((0.807f - 0.655f) / (0.846f - 0.655f)), SantaSleighScale.pedestrianPx(1440f), 0.01f)
    }

    @Test
    fun `the renderer draws the group, and sizes its gifts and margins, at that scale`() {
        val renderer = File(drawableDir.parentFile!!.parentFile, "kotlin/com/paperscrape/livewallpaper/engine/PaperRenderer.kt").readText()
        val geometry = renderer.substring(renderer.indexOf("private fun updateSleighGeometry()")).substringBefore("\n    }\n")
        assertTrue(geometry.contains("val sc = SantaSleighScale.pxPerUnit(screenHeight.toFloat())"))
        assertTrue(geometry.contains("sleighPxPerUnit = sc"))
        assertTrue(geometry.contains("giftHalf = SANTA_GIFT_HALF_UNITS * sc"))
        assertTrue(geometry.contains("groupHalfWidth = santaSleighDrawer.halfWidthPx(sc)"))
        assertTrue("the geometry is brought up to date before the effect moves", renderer.contains("updateSleighGeometry()\n        santaSleighEffect.update("))
        assertTrue(renderer.contains("santaSleighDrawer.draw(canvas, x, y, dir, alpha, effectElapsed, sleighPxPerUnit)"))
    }

    @Test
    fun `a gift leaves from the presents behind Santa and goes the way the sleigh flies, both ways`() {
        val sc = SantaSleighScale.pxPerUnit(1440f)
        val sackDx = (SantaSleighTable.SACK_X - SantaSleighDrawer.MIDDLE) * sc
        val x = 400f
        assertEquals("a reversed flight runs right to left, the drawings' own way", -1f, SantaSleighEffect.motion(reverse = true))
        assertEquals(1f, SantaSleighEffect.motion(reverse = false))
        for (reverse in listOf(true, false)) {
            // The direction the effect hands the drawer: +1 keeps the drawings (flying left), -1 mirrors them.
            val dir = -SantaSleighEffect.motion(reverse)
            val sack = SantaSleighDrawer.screenX(x, SantaSleighTable.SACK_X, dir, sc)
            assertEquals("reverse=$reverse: the gift leaves where the drawer puts the presents", sack, SantaSleighEffect.giftStartX(x, sackDx, reverse), 0.01f)
            val santa = SantaSleighDrawer.screenX(x, SantaSleighTable.HAND_X, dir, sc)
            val rudolph = SantaSleighDrawer.screenX(x, SantaSleighTable.LEAD_X, dir, sc)
            val motion = SantaSleighEffect.motion(reverse)
            assertTrue("reverse=$reverse: the presents trail Santa", (sack - santa) * motion < 0f)
            assertTrue("reverse=$reverse: Rudolph leads", (rudolph - santa) * motion > 0f)
        }
        // And the effect throws them so, and hands the drawer the same direction it moves the flight point in.
        val effect = File(drawableDir.parentFile!!.parentFile, "kotlin/com/paperscrape/livewallpaper/engine/SantaSleighEffect.kt").readText()
        val spawn = effect.substring(effect.indexOf("private fun spawnFallingGift(")).substringBefore("\n    }\n")
        assertTrue(spawn.contains("x = giftStartX(x, sackDxPx, reverse),"))
        assertTrue(spawn.contains("/ flightDuration * motion(reverse)"))
        assertTrue(effect.contains("val dir = -motion(reverse)"))
        assertTrue("a reversed flight runs right to left", effect.contains("return if (reverse) screenWidth - raw else raw"))
    }

    @Test
    fun `on the BV6600 the narrowest band and both cords are two pixels or more`() {
        val sc = SantaSleighScale.pxPerUnit(1440f)
        assertTrue("the drawing's narrowest band: ${SantaSleighTable.SANTA_MIN_BAND_UNITS * sc} px", SantaSleighTable.SANTA_MIN_BAND_UNITS * sc >= 2f)
        assertTrue("the reins: ${SantaSleighTable.SANTA_REIN_UNITS * sc} px", SantaSleighTable.SANTA_REIN_UNITS * sc >= 2f)
        assertTrue("the traces: ${SantaSleighTable.SANTA_TRACE_UNITS * sc} px", SantaSleighTable.SANTA_TRACE_UNITS * sc >= 2f)
        // A gift's ribbon: drawn never narrower than MIN_RIBBON_PX, which is 2 px -- the draw holds the floor, so the
        // test reads it there (until v5.15C2 it asserted `max(x, 2) >= 2`, true whatever was drawn; inventory I-975).
        assertTrue(SantaSleighEffect.MIN_RIBBON_PX >= 2f)
        val effect = File(drawableDir.parentFile!!.parentFile, "kotlin/com/paperscrape/livewallpaper/engine/SantaSleighEffect.kt").readText()
        assertTrue("the gift's ribbon is drawn with its floor", effect.contains("val r = maxOf(h * RIBBON_OF_HALF, MIN_RIBBON_PX * 0.5f)"))
    }
}
