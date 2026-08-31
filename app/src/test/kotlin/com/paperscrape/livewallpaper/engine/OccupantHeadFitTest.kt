package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * No occupant's head is drawn taller than the glass it sits behind — checked against the artwork.
 *
 * ### Why this test exists
 *
 * `VehiclePedestrianScaleTest` already asserts "a bust is exactly as tall as its glass", and that
 * assertion **cannot fail**: `CAR_HEAD_SCALE` is *defined* as `CAR_GLASS_HEIGHT_UNITS /
 * CAR_HEAD_CONTENT_UNITS`, so `CONTENT * SCALE == GLASS` reduces to `GLASS == GLASS` whatever the
 * constants say. It pins the shape of the rule and nothing about the pictures.
 *
 * What it could not see: `CAR_HEAD_CONTENT_UNITS` and [SceneObjectRenderer.WINDOW_HEAD_CONTENT_UNITS]
 * are hand-written numbers that have to match PNGs nobody re-measures. They stopped matching. The
 * window family's tallest member is 169 px and the constant said 155, so the winter woman's bobble
 * hat was drawn 169/155 of the glass height — pinned at the sill, excess upward, over the roof,
 * with nothing clipping it. On a OnePlus 6T at the near lane that was **3 px above a 27 px window**.
 *
 * So this test reads the sprites. It is the same idea as `SpriteGeometryTest` and
 * `SkyscraperWindowTest`: a number that describes artwork is checked against the artwork.
 *
 * ### The rule
 *
 * One constant per family, and it is the family **maximum**, not a representative. `glass /
 * representative` only bounds the members that are no taller than the representative; `glass /
 * maximum` bounds all of them. Shorter members get a little air above the head, which is what a
 * person behind a window has anyway.
 */
class OccupantHeadFitTest {

    @Test
    fun `no passenger is drawn taller than the sedan's glass`() {
        // The regression this pins, in the units it was measured in. Before v4.15 `woman_winter`
        // came out at 20.72 against a 19-unit glass and stood 3 px over the roof on a OnePlus 6T.
        for ((height, name) in everyContent("person_.*_head_window")) {
            val drawn = height / SpriteBlitter.SPRITE_PIXELS_PER_UNIT * SceneObjectRenderer.CAR_PASSENGER_SCALE
            assertTrue(
                "$name draws $drawn units into a ${SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS}-unit glass",
                drawn <= SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS + 0.001f,
            )
        }
    }

    @Test
    fun `no driver is drawn taller than the sedan's or the fire engine's glass`() {
        for ((height, name) in everyContent("person_.*_head_car")) {
            val units = height / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
            assertTrue(
                "$name overflows the sedan's glass",
                units * SceneObjectRenderer.CAR_HEAD_SCALE <= SceneObjectRenderer.CAR_GLASS_HEIGHT_UNITS + 0.001f,
            )
            assertTrue(
                "$name overflows the fire engine's glass",
                units * SceneObjectRenderer.FIRE_TRUCK_HEAD_SCALE <=
                    SceneObjectRenderer.FIRE_TRUCK_GLASS_HEIGHT_UNITS + 0.001f,
            )
        }
    }

    @Test
    fun `each family's constant is its tallest member, which is what makes the bound hold`() {
        for ((family, constant) in listOf(
            "person_.*_head_car" to SceneObjectRenderer.CAR_HEAD_CONTENT_UNITS,
            "person_.*_head_window" to SceneObjectRenderer.WINDOW_HEAD_CONTENT_UNITS,
        )) {
            val (tallest, name) = tallestContent(family)
            assertEquals(
                "$name is the tallest at $tallest px and the constant must be that, not a representative",
                tallest / SpriteBlitter.SPRITE_PIXELS_PER_UNIT,
                constant,
                0.001f,
            )
        }
    }

    @Test
    fun `the enlarged glass keeps a passenger's head exactly the size it was`() {
        // The point of growing the window instead of shrinking the people. 19/155 was the old
        // passenger scale; 20.72/169 is the new one, and they are the same number.
        assertEquals(
            "a passenger's bust scale must not have moved",
            19f / (155f / SpriteBlitter.SPRITE_PIXELS_PER_UNIT),
            SceneObjectRenderer.CAR_PASSENGER_SCALE,
            0.0005f,
        )
    }

    @Test
    fun `every member of a family still shares one canvas`() {
        // The premise the single anchor rests on, and `normalize.py`'s own rule for these groups.
        // Without it "one origin serves them all" stops being true and the maximum above stops
        // being the right correction.
        for (family in listOf("person_.*_head_car", "person_.*_head_window")) {
            val sizes = matching(family).map { ImageIO.read(it).let { i -> i.width to i.height } }.distinct()
            assertEquals("$family members must share one canvas, found $sizes", 1, sizes.size)
        }
    }

    /** Content height in px and the file it came from, for every member including skin variants. */
    private fun everyContent(family: String): List<Pair<Int, String>> =
        matching(family).map { contentHeight(it) to it.name }

    private fun tallestContent(family: String): Pair<Int, String> =
        everyContent(family).maxByOrNull { it.first } ?: error("no sprites match $family")

    private fun matching(family: String): List<File> {
        val re = Regex("^$family(_skin[0-9])?\\.png$")
        val files = drawableDir.listFiles()?.filter { re.matches(it.name) }.orEmpty()
        require(files.isNotEmpty()) { "no sprites match $family in $drawableDir" }
        return files.sortedBy { it.name }
    }

    /** The alpha bounding box's height, the same measure the constants are written from. */
    private fun contentHeight(file: File): Int {
        val image = ImageIO.read(file) ?: error("could not read $file")
        var top = -1
        var bottom = -1
        for (y in 0 until image.height) {
            var opaque = false
            for (x in 0 until image.width) {
                if (image.getRGB(x, y) ushr 24 != 0) { opaque = true; break }
            }
            if (opaque) {
                if (top < 0) top = y
                bottom = y
            }
        }
        require(top >= 0) { "$file is fully transparent" }
        return bottom - top + 1
    }

    /** Walks up for the module root, the way `SpriteGeometryTest` does. */
    private val drawableDir: File by lazy {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "${prefix}src/main/res/drawable-nodpi")
                if (candidate.isDirectory) return@lazy candidate
            }
            dir = dir.parentFile
        }
        error("could not locate src/main/res/drawable-nodpi from ${File(".").absolutePath}")
    }
}
