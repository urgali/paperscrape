package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What each building declares it is tall, against what its call site actually blits.
 *
 * ### The gap this closes
 *
 * `SceneSpace.SceneVariant` states a real height in metres and a drawn height in local units, and
 * every test that reasons about the scene's proportions divides one by the other. None of them
 * checks the second number against the drawing, so the table can be internally perfect and still
 * describe a building nobody draws — the way `TREE` declared 122 units for a tree that measured 118
 * until v3.7 caught it, and the tree was reading 9.48 m while claiming 9.8.
 *
 * The table's own rule for what counts is quoted here because it is the whole question:
 * *"the drawn extent that carries the object's identity, not necessarily its whole bounding box: a
 * shop's height is its wall, not the top of the sign hanging above it"*.
 *
 * ### The four that agree, and the one that does not
 *
 * Houses, restaurant and bar declare exactly their wall-plus-roof extent. `TOWER` declares **196**,
 * and 196 is where its **mast** ends — the aerial `drawSkyscraperBuilding` strokes from
 * `-height - 32` to `-height - 46`, with a lamp 2.5 units wide on the tip. The building itself —
 * facade plus setback — is **182**. By the rule above the mast is the tower's hanging sign, and the
 * restaurant's declaration excludes exactly that.
 *
 * The consequence is measurable: the scale is `metres * pixelsPerMetre / spriteUnitsTall`, so a
 * tower governed by 196 draws its 182 units of building at 182/196 of the size its 16.8 m claims.
 * **It reads as 15.6 m** — 7.1% short — next to houses that read exactly what they declare.
 *
 * This test does not decide it. Correcting the declaration to `(15.6f, 182f)` leaves the picture
 * pixel-identical (the metres-per-unit is the same 0.0857, so the window-size reasoning in `TOWER`'s
 * own comment survives) and makes the table true. Correcting the drawing instead — keeping 16.8 m
 * over 182 units — makes the tower 7.7% taller than it is today, which is a change to the skyline
 * and the maintainer's call. Both are written down here as numbers so the choice is not made by
 * whoever edits the table next.
 */
class BuildingHeightDeclarationTest {

    @Test
    fun `houses, restaurant and bar declare exactly what they draw`() {
        // origin y of the tallest blit in each call site, read off SceneObjectRenderer.
        val declared = mapOf(
            SceneSpace.SceneVariant.HOUSE_SMALL to 110f,  // house_small_roof at -110
            SceneSpace.SceneVariant.HOUSE_LARGE to 145f,  // house_large_roof at -145
            SceneSpace.SceneVariant.RESTAURANT to 96f,    // restaurant_wall  at -96
            SceneSpace.SceneVariant.BAR to 92f,           // bar_wall         at -92
        )
        for ((variant, drawn) in declared) {
            assertEquals("$variant draws $drawn units", drawn, variant.spriteUnitsTall, 0.001f)
        }
    }

    @Test
    fun `the tower declares its building and not its mast`() {
        val facade = SkyscraperSpriteLayout.HEIGHT
        val setbackUnits = spriteUnitsTall("skyscraper_setback")
        assertEquals(
            "the setback sits exactly on the facade's top",
            setbackUnits,
            -SkyscraperSpriteLayout.SETBACK_DY,
            0.001f,
        )
        val building = facade + setbackUnits
        assertEquals("facade plus setback", 182f, building, 0.001f)
        assertEquals(
            "TOWER must declare its building, not the mast tip at ${facade + 46f}",
            building,
            SceneSpace.SceneVariant.TOWER.spriteUnitsTall,
            0.001f,
        )
    }

    @Test
    fun `correcting the tower moved no pixel`() {
        // The whole reason this was safe to change. The scale a variant is drawn at is
        // `metres * pixelsPerMetre / spriteUnitsTall`; 15.6/182 and the old 16.8/196 are the same
        // metres-per-unit, so the same 3.857 px per unit comes out and the skyline is untouched.
        // If a later edit moves one of the two numbers without the other, this fails.
        assertEquals(
            "the tower's pixels-per-unit",
            3.857142f,
            SceneSpace.SceneVariant.TOWER.metresTall * SceneSpace.PIXELS_PER_METRE_AT_REFERENCE /
                SceneSpace.SceneVariant.TOWER.spriteUnitsTall,
            0.0001f,
        )
    }

    @Test
    fun `no variant declares more than it draws`() {
        // The general form of the rule, over every building the renderer has a blit for. The tower
        // was the only one that failed it, and it failed by 7.1%.
        val drawn = mapOf(
            SceneSpace.SceneVariant.HOUSE_SMALL to 110f,
            SceneSpace.SceneVariant.HOUSE_LARGE to 145f,
            SceneSpace.SceneVariant.RESTAURANT to 96f,
            SceneSpace.SceneVariant.BAR to 92f,
            SceneSpace.SceneVariant.TOWER to 182f,
        )
        for ((variant, units) in drawn) {
            assertEquals(
                "$variant declares ${variant.spriteUnitsTall} and draws $units",
                units,
                variant.spriteUnitsTall,
                0.001f,
            )
        }
    }

    /** A sprite's canvas height in local units. */
    private fun spriteUnitsTall(name: String): Float {
        val file = File(drawableDir, "$name.png")
        require(file.isFile) { "$file does not exist" }
        return ImageIO.read(file).height / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
    }

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
