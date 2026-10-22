package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * The Christmas decorations of the buildings (v5.14, the set the maintainer chose on 2026-10-14, *«C ma con aggiunta
 * di ghirlanda»*): a string of bulbs along every building's roof, a wreath on every front door, a star on every
 * tower, three bulbs on every distant house -- beside the sill strings of before -- and the fir A's ten lights.
 *
 * **Every part 2 px or more where it stands farthest on the BV6600** (the rule every drawn detail of the scene is
 * held to): what a piece unit is worth -- the projection at the depth the building is drawn at, its size roll, the
 * place's metre -- at the farthest house, place and tower of the twelve built-in streets, and at the farthest tree
 * for the fir's lights, times what each decoration measures in units (a tower's half as large again,
 * `SceneObjectRenderer.TOWER_DECORATION_SCALE`). The blinds' rail and the shutter's box and slats of a shut building
 * (v5.14) are held to it too. The smallest of each is printed (`DECORATION_2PX`). The sill strings' cord is not:
 * it was 1.1 units before and is left as it was (inventory I-718).
 *
 * And the table carries what the line under *Lights on the buildings* promises (*Christmas lights*' until v5.16): a roofline on every building's top piece
 * and on every distant house, a front door on every building. And the wreath's drawing (`deco_wreath`, a blit since
 * the batch ran out of room for it drawn as shapes) is the geometry the renderer's constants say, which the two
 * rules above and `DoorShutterTest`'s *the wreath hangs inside the door* are measured on.
 */
class ChristmasDecorationsTest {

    private val themes = listOf(
        "sunset", "autumn", "winter", "desert", "christmas", "new_year",
        "beach", "city", "tundra", "easter", "halloween", "spring",
    )

    private val screenHeight = 1440f

    private fun pxPerUnit(spec: StaticSceneObject, drawDepth: Float): Float {
        val v = SceneObjectRenderer.variantFor(spec)
        val family = NeighbourhoodTable.FAMILIES.getValue(v)
        return SceneObjectRenderer.effectiveScaleFor(spec, drawDepth, screenHeight) * v.spriteUnitsTall / family.unitsTall
    }

    private class Smallest(var px: Float = Float.MAX_VALUE, var where: String = "")

    @Test
    fun `every decoration is two pixels or more where its building stands farthest`() {
        val house = Smallest()
        val place = Smallest()
        val tower = Smallest()
        val tree = Smallest()
        for (themeId in themes) {
            val c = defaultCustomizationFor(themeId)
            val layout = SceneObjectCatalog.layoutFor(themeId, 0xFF8899AA.toInt())
            val phase = SceneSpace.hillCrestPhase(themeId)
            for (spec in layout.staticObjects) {
                when {
                    spec.type == SceneObjectType.HOUSE -> pxPerUnit(spec, spec.depthFraction).let {
                        if (it < house.px) { house.px = it; house.where = "$themeId at ${spec.depthFraction}" }
                    }
                    StorefrontStreet.isPlace(spec) -> pxPerUnit(spec, spec.depthFraction).let {
                        if (it < place.px) { place.px = it; place.where = "$themeId at ${spec.depthFraction}" }
                    }
                    spec.type == SceneObjectType.SKYSCRAPER -> {
                        val drawn = SceneSpace.towerDrawnDepth(spec.depthFraction, spec.tileFractionX, c.hillsVariation, phase)
                        pxPerUnit(spec, drawn).let { if (it < tower.px) { tower.px = it; tower.where = "$themeId at $drawn" } }
                    }
                    spec.type == SceneObjectType.TREE -> {
                        val px = SceneObjectRenderer.effectiveScaleFor(spec, screenHeight)
                        if (px < tree.px) { tree.px = px; tree.where = "$themeId at ${spec.depthFraction}" }
                    }
                }
            }
        }
        val k = SceneObjectRenderer.TOWER_DECORATION_SCALE
        val sizes = linkedMapOf(
            "bulb" to 2f * SceneObjectRenderer.CHRISTMAS_LIGHT_BULB_RADIUS_UNITS,
            "wreath ring" to SceneObjectRenderer.WREATH_BAND,
            "wreath bow" to 2f * SceneObjectRenderer.WREATH_BOW_RADIUS,
            "blind rail" to SceneObjectRenderer.BLIND_RAIL,
            "shutter box" to DoorShutter.BOX,
            "shutter slat" to DoorShutter.SLAT,
        )
        for ((label, s) in listOf("house" to house, "place among the houses" to place, "tower" to tower)) {
            val scale = if (label == "tower") k else 1f
            for ((what, units) in sizes) {
                // A house is never shut: no blinds, no shutter.
                if (label == "house" && (what.startsWith("blind") || what.startsWith("shutter"))) continue
                val px = s.px * units * scale
                println("DECORATION_2PX %-22s %-12s %.2f units at %.3f px a unit (%s): %.2f px".format(label, what, units * scale, s.px, s.where, px))
                assertTrue("a $what on the farthest $label (${s.where}) is $px px", px >= 2f)
            }
        }
        val star = 2f * SceneObjectRenderer.TOWER_STAR_INNER * k * tower.px
        println("DECORATION_2PX tower star's middle %.2f px".format(star))
        assertTrue("the tower's star's middle is $star px", star >= 2f)
        val firLight = 2f * SceneObjectRenderer.CHRISTMAS_LIGHT_RADIUS_UNITS * tree.px
        println("DECORATION_2PX fir light %.2f px at the farthest tree (%s, %.3f px a unit)".format(firLight, tree.where, tree.px))
        assertTrue("a fir's light is $firLight px", firLight >= 2f)
        println("DECORATION_2PX distant house bulb %.2f px".format(PaperRenderer.DISTANT_BULB_PX))
        assertTrue(PaperRenderer.DISTANT_BULB_PX >= 2f)
    }

    @Test
    fun `every building's top piece carries a roofline, every distant house too, every building a front door`() {
        val families = NeighbourhoodTable.FAMILIES.entries.map { it.key.name to it.value } +
            NeighbourhoodTable.STOREFRONTS.entries.map { it.key.name to it.value }
        for ((name, family) in families) {
            val last = family.slots.last()
            for (piece in last.options + last.heights) {
                assertTrue("$name's top piece has no roofline", piece.roofX.size >= 4 && piece.roofX.size == piece.roofY.size)
                assertTrue("$name's top is not above its roofline", piece.topY <= piece.roofY.minOrNull()!! + 1e-4f)
            }
            for (piece in family.slots.first().options + family.slots.first().heights) {
                assertTrue("$name has no front door", piece.door != null)
            }
        }
        for (piece in NeighbourhoodTable.DISTANT_HOUSES) {
            assertTrue("a distant house carries fewer than three roof points", piece.roofX.size >= 3)
        }
    }

    @Test
    fun `the wreath's drawing is the ring and the bow the constants say`() {
        var dir: File? = File(".").absoluteFile
        var svg: File? = null
        while (dir != null && svg == null) {
            svg = File(dir, "tools/assets/sources/svg/deco_wreath.svg").takeIf { it.isFile }
            dir = dir.parentFile
        }
        val text = checkNotNull(svg) { "deco_wreath.svg not found" }.readText()
        fun attr(element: String, name: String): Float =
            Regex("""$name="([-0-9.]+)"""").find(element)!!.groupValues[1].toFloat()
        val circles = Regex("""<circle[^>]*/>""").findAll(text).map { it.value }.toList()
        assertEquals("a ring and a bow", 2, circles.size)
        val (ring, bow) = circles
        val h = SceneObjectRenderer.WREATH_HALF
        assertTrue("the canvas is centred on the ring, ${2 * h} units a side", text.contains("""viewBox="${fmt(-h)} ${fmt(-h)} ${fmt(2 * h)} ${fmt(2 * h)}""""))
        assertTrue("three pixels a unit", text.contains("""width="${(6 * h).toInt()}" height="${(6 * h).toInt()}""""))
        assertEquals(0f, attr(ring, "cx"), 0f)
        assertEquals(0f, attr(ring, "cy"), 0f)
        assertEquals(SceneObjectRenderer.WREATH_RADIUS, attr(ring, "r"), 0f)
        assertEquals(SceneObjectRenderer.WREATH_BAND, attr(ring, "stroke-width"), 0f)
        assertEquals(0f, attr(bow, "cx"), 0f)
        assertEquals(SceneObjectRenderer.WREATH_BOW_DROP, attr(bow, "cy"), 0f)
        assertEquals(SceneObjectRenderer.WREATH_BOW_RADIUS, attr(bow, "r"), 0f)
        val reach = maxOf(SceneObjectRenderer.WREATH_RADIUS + SceneObjectRenderer.WREATH_BAND / 2f, SceneObjectRenderer.WREATH_BOW_DROP + SceneObjectRenderer.WREATH_BOW_RADIUS)
        assertTrue("the drawing fits its canvas", reach <= h)
    }

    /** A tower's star: ten directions 36 degrees apart, the first straight up, a point and a notch in turn. */
    @Test
    fun `a tower's star points up, its ten corners 36 degrees apart`() {
        val c = SceneObjectRenderer.STAR_COS
        val s = SceneObjectRenderer.STAR_SIN
        assertEquals(10, c.size)
        assertEquals(0f, c[0], 1e-6f)
        assertEquals("the first point straight up (y grows down)", -1f, s[0], 1e-6f)
        for (i in 0 until 10) {
            assertEquals("a direction is a unit", 1f, c[i] * c[i] + s[i] * s[i], 1e-5f)
            val j = (i + 1) % 10
            assertEquals("36 degrees to the next", Math.cos(Math.toRadians(36.0)).toFloat(), c[i] * c[j] + s[i] * s[j], 1e-5f)
        }
    }

    private fun fmt(v: Float): String = if (v == v.toInt().toFloat()) v.toInt().toString() else v.toString()
}
