package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A fir stays a fir among the palms** (v5.10C2) -- the maintainer, 2026-10-03, on the photographs of
 * v5.10C's Christmas with palms: *«no, le palme in natale devono sovrascrivere gli alberi normali, gli
 * abeti sono del tema e tali devono rimanere»*.
 *
 * A fir is not a slot type. It is a tree slot standing as a fir while the Christmas layer is on, one
 * tree in three by its seed (`SceneObjectRenderer.standsAsFir`), and v5.10C made every tree slot of a
 * theme without palms a palm -- so the firs went with the trees. The species pass now leaves a fir a
 * tree, which the drawing makes the fir it was. The Christmas layer puts firs on any theme, so the rule
 * is held on all ten themes without palms of their own, not on Christmas alone; Beach and Desert,
 * whose palm slots are palms whatever the layer says, are what they were.
 */
class FirsAmongThePalmsTest {

    private val palmless = ThemeCatalog.ALL.filter { !layoutOf(it).hasPalmSlots() }

    private fun layoutOf(theme: SceneTheme) = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)

    private fun drawn(layout: SceneObjectLayout, c: SceneCustomization) =
        layout.staticObjects.map { c.palmSpeciesApplied(it, layout.hasPalmSlots()) }

    @Test
    fun `with palms on and the Christmas layer on, every fir stays a fir and every other tree is a palm`() {
        assertEquals("the ten themes without palms, every one of them", 10, palmless.size)
        var firs = 0
        var palms = 0
        for (theme in palmless) {
            val layout = layoutOf(theme)
            val c = defaultCustomizationFor(theme.id).copy(palmsInsteadOfTrees = true, christmasDecorationsEnabled = true)
            var firsHere = 0
            for ((before, after) in layout.staticObjects.zip(drawn(layout, c))) {
                when {
                    before.type != SceneObjectType.TREE ->
                        assertEquals("${theme.id}: a ${before.type} changed with the palms", before, after)
                    SceneObjectRenderer.standsAsFir(before, c) -> {
                        firsHere++
                        assertEquals("${theme.id}: a fir became a palm", before, after)
                    }
                    else -> {
                        palms++
                        assertEquals("${theme.id}: an ordinary tree is not a palm, in its own place", before.copy(type = SceneObjectType.PALM_TREE), after)
                    }
                }
            }
            assertTrue("${theme.id} has no fir slot, so it says nothing", firsHere > 0)
            firs += firsHere
        }
        assertTrue("palms $palms, firs $firs: the palms are the ordinary trees, the larger part", palms > firs)
    }

    @Test
    fun `with the Christmas layer off there is no fir to keep, and every tree is a palm`() {
        for (theme in palmless) {
            val layout = layoutOf(theme)
            val c = defaultCustomizationFor(theme.id).copy(palmsInsteadOfTrees = true, christmasDecorationsEnabled = false)
            assertEquals("${theme.id} kept a tree with no fir to stand as", 0, drawn(layout, c).count { it.type == SceneObjectType.TREE })
        }
    }

    @Test
    fun `the fir rule is the drawing's own - a tree slot, the Christmas layer, one tree in three`() {
        var trees = 0
        var firs = 0
        for (theme in palmless) {
            val lit = defaultCustomizationFor(theme.id).copy(christmasDecorationsEnabled = true)
            val unlit = lit.copy(christmasDecorationsEnabled = false)
            for (spec in layoutOf(theme).staticObjects) {
                assertFalse("no fir without the Christmas layer", SceneObjectRenderer.standsAsFir(spec, unlit))
                if (spec.type != SceneObjectType.TREE) {
                    assertFalse("a ${spec.type} stood as a fir", SceneObjectRenderer.standsAsFir(spec, lit))
                    continue
                }
                trees++
                if (SceneObjectRenderer.standsAsFir(spec, lit)) firs++
            }
        }
        val share = firs.toFloat() / trees
        assertTrue("firs $firs of $trees trees: about one in three", share in 0.2f..0.47f)
        // And the drawing asks the same seed through the same hash: one place for each, so the slot the
        // species pass keeps a tree is the slot `drawTree` makes a fir.
        val renderer = source("engine/SceneObjectRenderer.kt")
        assertTrue(renderer.contains("val idleSeed = SceneObjectRenderer.idleSeedOf(spec)"))
        assertTrue(renderer.contains("private fun standsAsFir(r: StaticRuntime): Boolean = drawsFirs(customization) && isFirSeed(r.idleSeed)"))
        assertEquals("one fir hash", 1, Regex("""\(idleSeed \* 100000f\)\.toInt\(\) \* 0x9E3779B1""").findAll(renderer).count())
        assertEquals("one idle seed", 1, Regex("""\* 97f\) % 6\.28f""").findAll(renderer).count())
    }

    @Test
    fun `the firs stand where they stood before v5_10C2, on every built-in`() {
        // The rule as `drawTree` asked it through v5.10C, copied here on purpose: the one fixed point the
        // shared `standsAsFir` is held to. No golden scene has the Christmas layer on, so a hash that moved
        // the firs would pass every other test -- a third of the trees, just not the same third.
        fun firBefore(spec: StaticSceneObject): Boolean {
            val idleSeed = (spec.tileFractionX * 97f) % 6.28f
            var h = (idleSeed * 100000f).toInt() * 0x9E3779B1.toInt()
            h = h xor (h ushr 16)
            return (h and 0x7FFFFFFF) % 3 == 0
        }
        var firs = 0
        for (theme in ThemeCatalog.ALL) {
            val lit = defaultCustomizationFor(theme.id).copy(christmasDecorationsEnabled = true)
            for (spec in layoutOf(theme).staticObjects.filter { it.type == SceneObjectType.TREE }) {
                assertEquals("${theme.id}: the fir at ${spec.tileFractionX} moved", firBefore(spec), SceneObjectRenderer.standsAsFir(spec, lit))
                if (firBefore(spec)) firs++
            }
        }
        assertTrue("no fir on any built-in, so this says nothing", firs > 0)
    }

    @Test
    fun `where every tree left is a fir the Palms switch has no palm to put, and says so`() {
        val christmasLayout = layoutOf(ThemeCatalog.byId("christmas"))
        val christmas = defaultCustomizationFor("christmas").copy(palmsInsteadOfTrees = true)
        val thinned = christmas.copy(trees = christmas.trees.copy(density = 0.2f))
        assertFalse("Christmas as it ships has palms to stand", christmasLayout.keepsOnlyFirsUnderPalms(christmas))
        assertTrue("Christmas at 20 % keeps only firs", christmasLayout.keepsOnlyFirsUnderPalms(thinned))
        assertFalse("no lights, no firs", christmasLayout.keepsOnlyFirsUnderPalms(thinned.copy(christmasDecorationsEnabled = false)))
        assertFalse("no trees is the trees' own reason", christmasLayout.keepsOnlyFirsUnderPalms(thinned.copy(trees = thinned.trees.copy(visible = false))))
        val beach = defaultCustomizationFor("beach").let { it.copy(christmasDecorationsEnabled = true, trees = it.trees.copy(density = 0.2f)) }
        assertFalse("Beach's palm slots are palms whatever the layer says", layoutOf(ThemeCatalog.byId("beach")).keepsOnlyFirsUnderPalms(beach))
        // It is the scene's own answer: on every theme and every tenth of the density, "only firs" is
        // exactly a scene with the switch on that keeps trees and stands no palm.
        for (theme in ThemeCatalog.ALL) {
            val layout = layoutOf(theme)
            for (tenth in 1..10) {
                val c = defaultCustomizationFor(theme.id).let {
                    it.copy(christmasDecorationsEnabled = true, palmsEnabled = true, palmsInsteadOfTrees = true, trees = it.trees.copy(density = tenth / 10f))
                }
                val kept = layout.staticObjects.filter { c.keepCandidate(it, layout.densityScheme) }
                    .map { c.palmSpeciesApplied(it, layout.hasPalmSlots()) }
                    .filter { it.type == SceneObjectType.TREE || it.type == SceneObjectType.PALM_TREE }
                val expected = kept.isNotEmpty() && kept.none { it.type == SceneObjectType.PALM_TREE }
                assertEquals("${theme.id} at ${tenth * 10} %", expected, layout.keepsOnlyFirsUnderPalms(c.copy(palmsInsteadOfTrees = false, palmsEnabled = false)))
            }
        }
    }

    @Test
    fun `Beach and Desert - the Christmas layer changes no species, palms on or off`() {
        for (id in listOf("beach", "desert")) {
            val layout = SceneObjectCatalog.layoutFor(id, ThemeCatalog.byId(id).accentColor)
            assertTrue(layout.hasPalmSlots())
            for (palmsEnabled in listOf(true, false)) for (other in listOf(true, false)) {
                val c = defaultCustomizationFor(id).copy(palmsEnabled = palmsEnabled, palmsInsteadOfTrees = other)
                assertEquals(
                    "$id, palms $palmsEnabled: the Christmas layer moved a species",
                    drawn(layout, c.copy(christmasDecorationsEnabled = false)),
                    drawn(layout, c.copy(christmasDecorationsEnabled = true)),
                )
            }
            // Their palm slots stay palms under the lights, as they did through v5.10C.
            val lit = defaultCustomizationFor(id).copy(christmasDecorationsEnabled = true)
            assertEquals(0, drawn(layout, lit).count { it.type == SceneObjectType.TREE })
        }
    }

    @Test
    fun `the gallery card keeps its fir among the palms, and adds none where the scene has none`() {
        fun card(id: String, change: (SceneCustomization) -> SceneCustomization) =
            ThemePreviewScenes.forTheme(ThemeCatalog.byId(id), change(defaultCustomizationFor(id)))
        // Christmas, and a theme that is not Christmas with its lights on: a fir and a palm.
        for ((label, scene) in listOf(
            "christmas with palms" to card("christmas") { it.copy(palmsInsteadOfTrees = true) },
            "autumn with the lights and palms" to card("autumn") { it.copy(palmsInsteadOfTrees = true, christmasDecorationsEnabled = true) },
        )) {
            assertTrue("$label: the fir is gone", scene.contains(R.drawable.tree_fir))
            assertTrue("$label: no palm", scene.contains(R.drawable.palmtree_trunk))
            assertFalse("$label: a broadleaf tree left", scene.contains(R.drawable.tree_canopy))
        }
        // No lights, no fir.
        val autumn = card("autumn") { it.copy(palmsInsteadOfTrees = true, christmasDecorationsEnabled = false) }
        assertFalse(autumn.contains(R.drawable.tree_fir))
        // Beach under the lights: its palms, no fir, as before; with its palms off, its fir, as before.
        val beach = card("beach") { it.copy(christmasDecorationsEnabled = true) }
        assertFalse("Beach's palms grew a fir", beach.contains(R.drawable.tree_fir))
        assertTrue(beach.contains(R.drawable.palmtree_trunk))
        val beachOaks = card("beach") { it.copy(christmasDecorationsEnabled = true, palmsEnabled = false) }
        assertTrue(beachOaks.contains(R.drawable.tree_fir))
        assertFalse(beachOaks.contains(R.drawable.palmtree_trunk))
    }

    // ------------------------------------------------------------------ helpers

    private fun ThemePreviewScene.contains(resId: Int): Boolean =
        (backdrop + items + cars + ground).any { item -> item.parts.any { it.resId == resId } }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
