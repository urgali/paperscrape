package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The distant houses on the mountains (v5.11, inventory I-407; the maintainer's choices of
 * 2026-10-06): off as every theme ships, a stable share of places, all three drawings, the size rule
 * of [SceneSpace.distantHousePixelsTall], and every drawn detail at two pixels or more where they are
 * drawn.
 */
class DistantHousesTest {

    @Test
    fun `every theme ships them off, and data that never knew them reads them off`() {
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            assertFalse(theme.id, c.distantHouses.visible)
            assertEquals(theme.id, DistantHousesConfig.STARTING_DENSITY, c.distantHouses.density, 0f)
        }
        val old = JSONObject(defaultCustomizationFor("autumn").toJson().toString()).apply { remove("distantHouses") }
        assertEquals(DistantHousesConfig.OFF, sceneCustomizationFromJson(old).distantHouses)
        val on = defaultCustomizationFor("autumn").copy(distantHouses = DistantHousesConfig(true, 0.73f))
        assertEquals(on.distantHouses, sceneCustomizationFromJson(JSONObject(on.toJson().toString())).distantHouses)
    }

    /** Lowering the amount takes houses away and moves none; raising it only adds. */
    @Test
    fun `the amount keeps the same places, and lowering it only takes houses away`() {
        val amounts = (0..20).map { it / 20f }
        for (seed in listOf(1, -7, 31_337, "autumn".hashCode(), "city".hashCode())) {
            for (mountain in 0 until PaperRenderer.MOUNTAIN_POOL_SIZE) {
                val s = DistantHouses.seedFor(seed, mountain)
                for (slot in DistantHouses.SLOTS.indices) {
                    var was = false
                    for (amount in amounts) {
                        val now = DistantHouses.stands(s, slot, amount)
                        assertTrue("a house went away as the amount rose", now || !was)
                        was = now
                    }
                    assertFalse("a house at 0 %", DistantHouses.stands(s, slot, 0f))
                    assertTrue("a place empty at 100 %", DistantHouses.stands(s, slot, 1f))
                }
                // Above 0 every mountain carries one, so the row never reads on over none (8.7).
                for (amount in listOf(0.001f, 0.01f, 0.05f)) {
                    assertTrue(
                        "a mountain with no house at $amount",
                        DistantHouses.SLOTS.indices.any { DistantHouses.stands(s, it, amount) },
                    )
                }
            }
        }
    }

    @Test
    fun `the three drawings are mixed, the two colours and the lit windows dealt per house`() {
        val designs = IntArray(3)
        val colours = IntArray(2)
        var lit = 0
        var total = 0
        for (theme in ThemeCatalog.ALL) {
            for (layer in listOf(EffectId.MOUNTAINS_BACK, EffectId.MOUNTAINS_FRONT)) {
                val layerSeed = theme.id.hashCode() xor (layer * -0x61c88647)
                for (mountain in 0 until PaperRenderer.MOUNTAIN_POOL_SIZE) {
                    val s = DistantHouses.seedFor(layerSeed, mountain)
                    for (slot in DistantHouses.SLOTS.indices) {
                        designs[DistantHouses.design(s, slot, 3)]++
                        colours[DistantHouses.colourVariant(s, slot)]++
                        if (DistantHouses.litShare(s, slot) == 1f) lit++
                        total++
                    }
                }
            }
        }
        for (d in designs) assertTrue("each drawing stands: ${designs.toList()}", d > total / 5)
        for (c in colours) assertTrue("both colours: ${colours.toList()}", c > total / 3)
        assertTrue("about four in five windows lit: $lit of $total", lit in (total * 0.7).toInt()..(total * 0.9).toInt())
    }

    /**
     * **The size rule**: a small house at the projection of where it stands, never under the size at
     * 0.6722 of the screen -- 14 px on the BV6600's 1440 -- and on every mountain the scene can draw, the
     * floor's size. The mountains' geometry is `PaperRenderer.drawMountains`': bases on the hill's solid
     * line (the front layer 0.015 below it, the lake's top edge above it when there is water), heights
     * 0.15 and 0.105 of the screen with a jitter of 0.75..1.25; the lowest a house's place can be is the
     * front layer's shortest mountain at the outermost place.
     */
    @Test
    fun `on a mountain every house is the floor's size, 14 px on the BV6600`() {
        val bv6600 = 1440f
        val floor = SceneSpace.distantHousePixelsTall(SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION, bv6600)
        assertTrue("the floor is the maintainer's 14 px: $floor", floor >= 14f)
        assertEquals(14f, floor, 0.01f)
        assertEquals("above the horizon the floor still holds", floor, SceneSpace.distantHousePixelsTall(0.6f, bv6600), 0f)
        assertTrue("below the floor's line it grows with the projection",
            SceneSpace.distantHousePixelsTall(0.70f, bv6600) > floor)
        val base = SceneSpace.GROUND_SOLID_TOP_Y_FRACTION + 0.015f
        val shortest = 0.105f * 0.75f
        val outermost = DistantHouses.SLOTS.maxOf { kotlin.math.abs(it) } + DistantHouses.SLOT_JITTER / 2f
        val lowest = DistantHouses.surfaceY(base, shortest, outermost)
        assertTrue("the lowest place on a mountain, $lowest, is above the floor's line", lowest < SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION)
        // The renderer must still state those numbers, or this bound is about another scene.
        val renderer = File(sourceRoot(), "engine/PaperRenderer.kt").readText()
        assertTrue(renderer.contains("baseYFraction = effectiveBaseYFraction + 0.015f, peakHeightFraction = 0.105f"))
        assertTrue(renderer.contains("val heightJitter = CandidateNoise.range(seed, i, CandidateNoise.CH_HEIGHT, 0.75f, 1.25f)"))
        // And smaller than every building of the village: its farthest house is about 40 px.
        val farthestHouse = SceneSpace.SceneVariant.HOUSE_SMALL.metresTall * SceneSpace.pixelsPerMetre(bv6600) *
            SceneSpace.depthScale(0f)
        assertTrue("$floor against the village's $farthestHouse", floor < farthestHouse / 2f)
    }

    /**
     * **Two pixels**, the floor every drawn detail is held to: each window of each drawing -- the
     * chalet's attic included -- and the snow on each roof, at the size the houses are drawn on the
     * BV6600, measured off the shipped PNGs rather than off the generator's numbers.
     */
    @Test
    fun `every window and every snow cap is at least two pixels at the size they are drawn`() {
        val pixelsTall = SceneSpace.distantHousePixelsTall(SceneSpace.DISTANT_HOUSE_FLOOR_Y_FRACTION, 1440f)
        val stems = listOf("house_distant_cottage", "house_distant_chalet", "house_distant_tall")
        val report = StringBuilder()
        for ((index, stem) in stems.withIndex()) {
            val piece = NeighbourhoodTable.DISTANT_HOUSES[index]
            // A sprite pixel is a third of a unit; a unit is pixelsTall / height screen pixels.
            val screenPerSpritePixel = pixelsTall / piece.height / SpriteBlitter.SPRITE_PIXELS_PER_UNIT
            val windows = opaqueBlobs(ImageIO.read(File(drawableDir(), "${stem}_mg.png")))
            assertTrue("$stem has windows", windows.isNotEmpty())
            for ((w, h) in windows) {
                val side = minOf(w, h) * screenPerSpritePixel
                report.append("%s window %.2f px\n".format(stem, side))
                assertTrue("$stem: a window ${"%.2f".format(side)} px across", side >= 2f)
            }
            val snow = ImageIO.read(File(drawableDir(), "${stem}_snow_fx.png"))
            val middle = snow.width / 2
            val thick = (0 until snow.height).count { (snow.getRGB(middle, it) ushr 24) > 127 } * screenPerSpritePixel
            report.append("%s snow %.2f px\n".format(stem, thick))
            assertTrue("$stem: the snow is ${"%.2f".format(thick)} px thick", thick >= 2f)
        }
        println(report)
    }

    @Test
    fun `nothing is drawn while the switch is off or at 0 percent`() {
        assertFalse(DistantHousesConfig(visible = false, density = 1f).drawsAny)
        assertFalse(DistantHousesConfig(visible = true, density = 0f).drawsAny)
        assertTrue(DistantHousesConfig(visible = true, density = 0.01f).drawsAny)
        val renderer = File(sourceRoot(), "engine/PaperRenderer.kt").readText()
        val draw = renderer.substring(renderer.indexOf("private fun drawDistantHouses("))
        assertTrue(draw.contains("if (!config.drawsAny) return"))
    }

    /** The bounding boxes, in sprite pixels, of the separate opaque shapes of an image. */
    private fun opaqueBlobs(image: java.awt.image.BufferedImage): List<Pair<Int, Int>> {
        val seen = Array(image.height) { BooleanArray(image.width) }
        val out = ArrayList<Pair<Int, Int>>()
        for (y in 0 until image.height) for (x in 0 until image.width) {
            if (seen[y][x] || (image.getRGB(x, y) ushr 24) <= 127) continue
            var x0 = x; var x1 = x; var y0 = y; var y1 = y
            val stack = ArrayDeque<Pair<Int, Int>>()
            stack.add(x to y); seen[y][x] = true
            while (stack.isNotEmpty()) {
                val (cx, cy) = stack.removeLast()
                x0 = minOf(x0, cx); x1 = maxOf(x1, cx); y0 = minOf(y0, cy); y1 = maxOf(y1, cy)
                for ((nx, ny) in listOf(cx + 1 to cy, cx - 1 to cy, cx to cy + 1, cx to cy - 1)) {
                    if (nx !in 0 until image.width || ny !in 0 until image.height || seen[ny][nx]) continue
                    if ((image.getRGB(nx, ny) ushr 24) <= 127) continue
                    seen[ny][nx] = true
                    stack.add(nx to ny)
                }
            }
            out += (x1 - x0 + 1) to (y1 - y0 + 1)
        }
        return out
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/res/drawable-nodpi").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found from ${File(".").absolutePath}")
    }

    private fun drawableDir(): File = File(repoRoot(), "app/src/main/res/drawable-nodpi")

    private fun sourceRoot(): File = File(repoRoot(), "app/src/main/kotlin/com/paperscrape/livewallpaper")
}
