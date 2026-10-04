package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **At 0 % the wallpaper draws none of these, which is what lets their switch read off there**
 * (v5.10C, row 8 of the maintainer's table, inventory I-211; the switch side is
 * `SwitchesTellTheTruthTest`). The settings screen may say "None at 0% density" only about things of
 * which the scene really draws none, and may not say it about the ones of which it draws some --
 * so both halves are asserted against the code that decides what stands:
 *
 *  - the static objects through [keepCandidate], on all twelve built-in layouts: houses, trees,
 *    parasols and the six seasonal decorations keep **nothing** at 0; the buildings keep their three
 *    shops (exempt from density, `keepCandidate`'s KDoc), which is why their switch is not one of these;
 *  - the effects that select from a pool through [CandidateThreshold] -- clouds, rain and snow, birds,
 *    sailboats, dolphins -- admit no candidate at 0, for every candidate and every effect offset;
 *  - the stars, by `PaperRenderer.starCountFor`, which the renderer and the settings screen both ask
 *    (none below 1/70, so *Show Stars* reads off there too); the rainbow and the theme's own rain, whose
 *    rule sits inside `PaperRenderer`, by its source: the rainbow returns when its visibility, which
 *    multiplies the opacity, is 0. The renderer cannot be built on the JVM (`ROADMAP.md` B5).
 *
 * And the ones left out, for what they keep at 0: one car (`CarSelection`), the people (their night
 * density is a slider of its own), the mountains (at least one).
 */
class NothingAtZeroTest {

    private val noneAtZero = listOf(
        SceneObjectType.HOUSE, SceneObjectType.TREE, SceneObjectType.PALM_TREE, SceneObjectType.PARASOL,
        SceneObjectType.SNOWMAN, SceneObjectType.GIFT, SceneObjectType.PENGUIN, SceneObjectType.BUNNY,
        SceneObjectType.EASTER_EGG, SceneObjectType.PUMPKIN,
    )

    private fun ObjectVariantConfig.atZero() = copy(visible = true, density = 0f)

    private fun allAtZero(c: SceneCustomization) = c.copy(
        houses = c.houses.atZero(), trees = c.trees.atZero(), parasols = c.parasols.atZero(),
        snowmen = c.snowmen.atZero(), gifts = c.gifts.atZero(), penguins = c.penguins.atZero(),
        bunnies = c.bunnies.atZero(), easterEggs = c.easterEggs.atZero(), pumpkins = c.pumpkins.atZero(),
        buildings = c.buildings.atZero(),
    )

    @Test
    fun `at 0 percent no house, tree, parasol or decoration stands, on any built-in`() {
        // The decorations are not in the built-in streets by default, so a street of every kind of
        // candidate is laid out too: each decoration at each depth, across the tile.
        val everyKind = noneAtZero.flatMapIndexed { i, type ->
            (0 until 10).map { j -> StaticSceneObject(type, depthFraction = j / 10f, tileFractionX = (i * 10 + j) / 100f) }
        }
        for (theme in ThemeCatalog.ALL) {
            val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
            val zero = allAtZero(defaultCustomizationFor(theme.id))
            for (spec in layout.staticObjects + everyKind) {
                if (spec.type !in noneAtZero) continue
                assertFalse("${theme.id}: a ${spec.type} stands at 0 %", zero.keepCandidate(spec, layout.densityScheme))
                assertFalse("${theme.id}: a ${spec.type} stands at 0 % (grid scheme)", zero.keepCandidate(spec, DENSITY_SCHEME_GRID))
            }
            // ...and they do stand above it, or "none" would be saying nothing.
            val some = defaultCustomizationFor(theme.id)
            assertTrue(
                "${theme.id}: nothing stands at the theme's own densities either",
                layout.staticObjects.any { it.type in noneAtZero && some.keepCandidate(it, layout.densityScheme) },
            )
        }
    }

    @Test
    fun `the buildings keep their three shops at 0 percent, so their switch is not one of these`() {
        for (theme in ThemeCatalog.ALL) {
            val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
            val zero = allAtZero(defaultCustomizationFor(theme.id))
            val shops = layout.staticObjects.count {
                it.type == SceneObjectType.SKYSCRAPER && zero.keepCandidate(it, layout.densityScheme)
            }
            assertEquals("${theme.id}: shops standing at 0 %", 3, shops)
        }
    }

    @Test
    fun `no pooled effect admits a candidate at 0 percent`() {
        for (effect in 0 until EffectId.COUNT) {
            val offset = CandidateThreshold.offsetFor(effect)
            for (pool in listOf(1, 8, 41, 64, 200)) {
                val fallback = CandidateThreshold.fallbackIndexFor(0f, pool, offset)
                assertEquals("effect $effect: a fallback candidate at 0 %", -1, fallback)
                for (i in 0 until pool) {
                    assertFalse("effect $effect, candidate $i, at 0 %", CandidateThreshold.isPresent(i, 0f, offset, fallback))
                }
                // Just above 0 at least one is kept -- the fallback -- which is why "0 %" and not "low".
                val tiny = CandidateThreshold.fallbackIndexFor(0.0001f, pool, offset)
                assertTrue((0 until pool).any { CandidateThreshold.isPresent(it, 0.0001f, offset, tiny) })
            }
        }
    }

    @Test
    fun `the stars and the rainbow draw nothing at 0, by the renderer's own rule`() {
        val renderer = source("engine/PaperRenderer.kt")
        assertTrue(
            "the star field must still be counted by starCountFor, which the settings screen asks too",
            renderer.contains("val count = starCountFor(sceneCustomization.stars.density)"),
        )
        assertEquals(0, PaperRenderer.starCountFor(0f))
        assertEquals("below 1/70 no star stands, so Show Stars reads off there too", 0, PaperRenderer.starCountFor(1f / 71f))
        assertEquals(1, PaperRenderer.starCountFor(1f / 70f + 1e-4f))
        assertEquals(PaperRenderer.STAR_COUNT_AT_FULL_DENSITY, PaperRenderer.starCountFor(1f))
        val rainbow = renderer.substring(renderer.indexOf("private fun drawRainbow("))
        assertTrue(
            "the rainbow's visibility must still multiply its opacity, and return at 0",
            rainbow.contains("* rainbow.opacity.coerceIn(0f, 1f)") && rainbow.contains("if (visibility <= 0f) return"),
        )
        val precipitation = renderer.substring(renderer.indexOf("private fun drawPrecipitation("))
        assertTrue(
            "the theme's own rain and snow must still return at 0 intensity",
            precipitation.contains("if (!precip.visible || precip.intensity <= 0f) return"),
        )
    }

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
