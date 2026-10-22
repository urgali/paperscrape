package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The scarecrows stand in the snowmen's places** (v5.16; the maintainer, 2026-10-21: *«wickedmen devono avere stesso
 * posizionamento»*, and at the v5.16A table the places' choice B, *«il resto, si a tutte»*): one candidate on each
 * snowman's, the same x, depth and size; which of them stand is a coin of their own, so the snowmen at their density
 * still stand where they stood and a scarecrow where its own coin says; where both of one place stand, the scarecrow is
 * drawn aside. And they are never part of a saved layout: a build before v5.16 refuses a type it does not know.
 */
class ScarecrowPlacesTest {

    private fun layout(id: String) = SceneObjectCatalog.layoutFor(id, ThemeCatalog.byId(id).accentColor)

    @Test
    fun `every theme lays a scarecrow on every snowman's place, and nowhere else`() {
        for (theme in ThemeCatalog.ALL) {
            val objects = layout(theme.id).staticObjects
            val snowmen = objects.filter { it.type == SceneObjectType.SNOWMAN }
            val scarecrows = objects.filter { it.type == SceneObjectType.SCARECROW }
            assertEquals(theme.id, snowmen.size, scarecrows.size)
            assertEquals(theme.id, snowmen.map { Triple(it.tileFractionX, it.depthFraction, it.scale) }, scarecrows.map { Triple(it.tileFractionX, it.depthFraction, it.scale) })
        }
    }

    @Test
    fun `their own coin keeps about the share the density says, places of their own, and a density moved adds or removes only`() {
        var kept = 0
        var both = 0
        var snowmenKept = 0
        var total = 0
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id).copy(
                scarecrows = SceneCustomization.SCARECROWS_OFF.copy(visible = true, density = 0.5f),
                snowmen = SceneCustomization.DEFAULT.snowmen.copy(visible = true, density = 0.5f),
            )
            val objects = layout(theme.id).staticObjects
            for (spec in objects.filter { it.type == SceneObjectType.SCARECROW }) {
                total++
                // The snowman of the place, as the layout holds it: the one standing on the scarecrow's x and depth.
                val twin = objects.single { it.type == SceneObjectType.SNOWMAN && it.tileFractionX == spec.tileFractionX && it.depthFraction == spec.depthFraction }
                val scarecrow = c.keepCandidate(spec)
                val snowman = c.keepCandidate(twin)
                if (scarecrow) kept++
                if (snowman) snowmenKept++
                if (scarecrow && snowman) both++
                assertEquals("aside exactly where the snowman stands too", snowman, c.scarecrowStandsAside(spec))
            }
        }
        assertTrue("about half kept: $kept of $total", kept in (total * 0.3).toInt()..(total * 0.7).toInt())
        assertTrue("not the snowmen's own places: $both of $kept with a snowman", both < kept && both < snowmenKept)
        assertTrue("some stand aside: $both", both > 0)
        // And the renderer stands them aside by that rule, worked out once when its objects are built.
        var dir: java.io.File? = java.io.File(".").absoluteFile
        var renderer: String? = null
        while (dir != null && renderer == null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/engine/SceneObjectRenderer.kt")
                if (f.isFile) renderer = f.readText()
            }
            dir = dir.parentFile
        }
        assertTrue(renderer!!.contains("r.scarecrowAside = customization.scarecrowStandsAside(spec, layout.densityScheme)"))
        assertTrue(renderer.contains("if (r.scarecrowAside) canvas.translate(SCARECROW_ASIDE_UNITS, 0f)"))
        // A density moved: the scarecrows of the lower density are among those of the higher, none moves.
        for (theme in ThemeCatalog.ALL) {
            val specs = layout(theme.id).staticObjects.filter { it.type == SceneObjectType.SCARECROW }
            var previous = emptySet<StaticSceneObject>()
            for (density in listOf(0f, 0.2f, 0.5f, 0.8f, 1f)) {
                val c = SceneCustomization.DEFAULT.copy(scarecrows = SceneCustomization.SCARECROWS_OFF.copy(visible = true, density = density))
                val now = specs.filter { c.keepCandidate(it) }.toSet()
                assertTrue("${theme.id} at $density", now.containsAll(previous))
                previous = now
            }
            assertEquals("${theme.id}: all at 100 %", specs.toSet(), previous)
        }
        // And off, none.
        val off = SceneCustomization.DEFAULT
        assertTrue(layout("halloween").staticObjects.filter { it.type == SceneObjectType.SCARECROW }.none { off.keepCandidate(it) })
    }

    @Test
    fun `the snowmen keep the places they had`() {
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id).copy(snowmen = SceneCustomization.DEFAULT.snowmen.copy(visible = true, density = 0.5f))
            val withScarecrows = c.copy(scarecrows = SceneCustomization.SCARECROWS_OFF.copy(visible = true, density = 1f))
            val snowmen = layout(theme.id).staticObjects.filter { it.type == SceneObjectType.SNOWMAN }
            assertEquals(theme.id, snowmen.filter { c.keepCandidate(it) }, snowmen.filter { withScarecrows.keepCandidate(it) })
        }
    }

    @Test
    fun `a saved layout carries no scarecrow, and reads back without one`() {
        val l = layout("halloween")
        assertTrue(l.staticObjects.any { it.type == SceneObjectType.SCARECROW })
        val json = l.toJson()
        assertFalse("SCARECROW written", json.toString().contains("SCARECROW"))
        val back = sceneObjectLayoutFromJson(json)
        assertEquals(l.staticObjects.count { it.type != SceneObjectType.SCARECROW }, back.staticObjects.size)
    }

    @Test
    fun `a scarecrow is drawn at 2_4 m, the size table's row, on the snowmen's depths`() {
        assertEquals(2.4f, SceneSpace.SceneVariant.SCARECROW.metresTall, 0f)
        assertEquals(ScarecrowTable.UNITS_TALL, SceneSpace.SceneVariant.SCARECROW.spriteUnitsTall, 0f)
        val spec = layout("halloween").staticObjects.first { it.type == SceneObjectType.SCARECROW }
        assertEquals(SceneSpace.SceneVariant.SCARECROW, SceneObjectRenderer.variantFor(spec))
        // Taller than the snowman whose place it shares, by 2.4 m against 1.68.
        val scarecrowPx = SceneObjectRenderer.effectiveScaleFor(spec, 1440f) * ScarecrowTable.UNITS_TALL
        val snowmanPx = SceneObjectRenderer.effectiveScaleFor(spec.copy(type = SceneObjectType.SNOWMAN), 1440f) * SceneSpace.SceneVariant.SNOWMAN.spriteUnitsTall
        assertEquals(2.4f / SceneSpace.SceneVariant.SNOWMAN.metresTall, scarecrowPx / snowmanPx, 0.01f)
    }
}
