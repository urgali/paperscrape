package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.floor

/**
 * **The density slider's number is the share of objects standing**.
 *
 * `stableFraction` alone is a grid -- sixteen values in front of the scene, a few dozen behind --
 * so a density compared with it keeps whole steps: 0.65 keeps 11 of 16 in front, 68.75 % where the
 * slider says 65 %. [densityFraction] refines each step with a second draw, so the value
 * [keepCandidate] compares is a real fraction in `[0, 1)` at every depth.
 *
 * What this pins:
 *  1. the mapping, re-derived: over many positions in each band, the share kept is the density;
 *  2. **nothing appears**: at the shipped defaults every object the fraction stands, the grid stands
 *     too, and every one the grid stands and the fraction does not is on the step the threshold cuts
 *     through -- the only kind the grid keeps whole;
 *  3. a layout saved without a scheme key loads on the grid and keeps every object it stored
 *     ([DENSITY_SCHEME_GRID]);
 *  4. the numbers, theme by theme and category by category, printed on every run so a report reads
 *     them from here rather than from a hand count -- and held to the counts the tests above pin, so
 *     the table cannot print one thing while the renderer stands another.
 */
class DensityResolutionTest {

    private val categories = listOf(
        SceneObjectType.HOUSE, SceneObjectType.SKYSCRAPER, SceneObjectType.PARASOL, SceneObjectType.TREE,
        SceneObjectType.PALM_TREE, SceneObjectType.SNOWMAN, SceneObjectType.GIFT, SceneObjectType.PENGUIN,
        SceneObjectType.BUNNY, SceneObjectType.EASTER_EGG, SceneObjectType.PUMPKIN,
    )

    private fun gridFraction(spec: StaticSceneObject): Float {
        val raw = spec.tileFractionX * 7919f + spec.depthFraction * 7919f * 131f + 0f
        return raw - floor(raw)
    }

    @Test
    fun `in every band the share kept is the density, where the grid missed it by up to a step`() {
        // Positions spread over the tile and over each depth band the catalogue deals candidates in.
        val rnd = java.util.Random(134)
        for ((label, band) in listOf("back" to (0.30f to 0.50f), "front" to (0.50f to 0.98f))) {
            val specs = List(40_000) {
                StaticSceneObject(
                    type = SceneObjectType.HOUSE,
                    tileFractionX = rnd.nextFloat(),
                    depthFraction = band.first + rnd.nextFloat() * (band.second - band.first),
                )
            }
            var worstGrid = 0.0
            for (step in 1..19) {
                val density = step * 0.05f
                val kept = specs.count { densityFraction(it) < density } / specs.size.toDouble()
                val grid = specs.count { gridFraction(it) < density } / specs.size.toDouble()
                worstGrid = maxOf(worstGrid, abs(grid - density))
                assertEquals("$label band at density $density", density.toDouble(), kept, 0.01)
            }
            println("density mapping, $label band: fraction kept within 1 point of the slider; the grid was off by up to ${"%.1f".format(worstGrid * 100)} points")
        }
    }

    @Test
    fun `at the shipped defaults nothing appears, and only objects on the cut step go`() {
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).staticObjects
            val before = layout.filter { c.keepCandidate(it, DENSITY_SCHEME_GRID) }.toSet()
            val after = layout.filter { c.keepCandidate(it, DENSITY_SCHEME_FRACTION) }.toSet()
            assertTrue("${theme.id}: an object appeared: ${after - before}", before.containsAll(after))
            for (gone in before - after) {
                val density = configDensity(c, gone.type)
                val raw = gone.tileFractionX * 7919f + gone.depthFraction * 7919f * 131f + 0f
                val step = Math.ulp(raw)
                val g = gridFraction(gone)
                assertTrue("${theme.id}: $gone went but was not on the step the density cuts", g < density && density < g + step)
            }
        }
    }

    /**
     * **And the renderer's threshold is the fraction** -- the one this whole file is about. Without
     * this, [keepCandidate] could read the grid and every test above would still pass, since they
     * read [densityFraction] directly. Pinned twice: object for object against the fraction, and as
     * the count it gives at the twelve defaults, [STANDING_AT_DEFAULTS]. The buildings among the
     * houses -- a `SKYSCRAPER` at or past `BUILDING_TOWER_MAX_DEPTH` -- are never thinned, so they are
     * counted and not compared.
     */
    @Test
    fun `keepCandidate reads the fraction, and the defaults stand 326 objects`() {
        var standing = 0
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            for (spec in SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).staticObjects) {
                if (spec.type !in categories) continue
                val kept = c.keepCandidate(spec)
                if (kept) standing++
                val exempt = spec.type == SceneObjectType.SKYSCRAPER && spec.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH
                if (exempt || !configVisible(c, spec.type)) continue
                assertEquals("${theme.id}: $spec", densityFraction(spec) < configDensity(c, spec.type).toDouble(), kept)
            }
        }
        assertEquals("objects standing at the twelve defaults", STANDING_AT_DEFAULTS, standing)
    }

    @Test
    fun `a theme saved before the repair keeps every object it stored`() {
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            val raw = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
            // A layout saved on the grid: the grid's survivors, and no scheme key.
            val stored = raw.copy(staticObjects = raw.staticObjects.filter { c.keepCandidate(it, DENSITY_SCHEME_GRID) })
            val json = stored.toJson().apply { remove("densityScheme") }
            val loaded = sceneObjectLayoutFromJson(json)
            assertEquals("${theme.id}: a pre-v5.8C layout must load on the grid", DENSITY_SCHEME_GRID, loaded.densityScheme)
            assertEquals(
                "${theme.id}: every stored object must still stand",
                loaded.staticObjects,
                loaded.staticObjects.filter { c.keepCandidate(it, loaded.densityScheme) },
            )
            // A layout saved on the fraction round-trips on it, every stored object standing too.
            val now = raw.copy(staticObjects = raw.staticObjects.filter { c.keepCandidate(it) })
            val back = sceneObjectLayoutFromJson(now.toJson())
            assertEquals(DENSITY_SCHEME_FRACTION, back.densityScheme)
            assertEquals(back.staticObjects, back.staticObjects.filter { c.keepCandidate(it, back.densityScheme) })
        }
    }

    /**
     * The table, printed on every run, and held to what the tests above pin: on the fraction the twelve defaults stand
     * [STANDING_AT_DEFAULTS] objects, the count `keepCandidate` gives, and no theme stands more of a category than on the
     * grid ([DENSITY_SCHEME_GRID]) -- a cell `b>a`, grid then fraction, with `a` above `b` is an object that appeared.
     */
    @Test
    fun `the count, theme by theme, before and after`() {
        val header = "theme       " + categories.joinToString(" ") { it.name.take(9).padStart(9) }
        println("objects standing at each theme's defaults: grid (before) -> fraction (after), nominal in brackets")
        println(header)
        val totals = IntArray(3)
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).staticObjects
            val cells = categories.map { type ->
                val of = layout.filter { it.type == type }
                val thinned = of.filter { !(type == SceneObjectType.SKYSCRAPER && it.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH) }
                val b = of.count { c.keepCandidate(it, DENSITY_SCHEME_GRID) }
                val a = of.count { c.keepCandidate(it, DENSITY_SCHEME_FRACTION) }
                val visible = configVisible(c, type)
                val nominal = if (visible) thinned.size * configDensity(c, type) + (of.size - thinned.size) else 0f
                assertTrue("${theme.id}, ${type.name}: $a standing after the repair, $b before", a <= b)
                totals[0] += b; totals[1] += a; totals[2] += (nominal * 100).toInt()
                if (of.isEmpty()) "-".padStart(9) else "$b>$a".padStart(9)
            }
            println(theme.id.padEnd(12) + cells.joinToString(" "))
        }
        println("total: ${totals[0]} -> ${totals[1]} (nominal ${"%.1f".format(totals[2] / 100.0)})")
        assertEquals("the table's total after the repair", STANDING_AT_DEFAULTS, totals[1])
    }

    private fun configDensity(c: SceneCustomization, type: SceneObjectType): Float = when (type) {
        SceneObjectType.HOUSE -> c.houses.density
        SceneObjectType.SKYSCRAPER -> c.buildings.density
        SceneObjectType.PARASOL -> c.parasols.density
        SceneObjectType.TREE, SceneObjectType.PALM_TREE -> c.trees.density
        SceneObjectType.SNOWMAN -> c.snowmen.density
        SceneObjectType.GIFT -> c.gifts.density
        SceneObjectType.PENGUIN -> c.penguins.density
        SceneObjectType.BUNNY -> c.bunnies.density
        SceneObjectType.EASTER_EGG -> c.easterEggs.density
        SceneObjectType.PUMPKIN -> c.pumpkins.density
        else -> 1f
    }

    private fun configVisible(c: SceneCustomization, type: SceneObjectType): Boolean = when (type) {
        SceneObjectType.HOUSE -> c.houses.visible
        SceneObjectType.SKYSCRAPER -> c.buildings.visible
        SceneObjectType.PARASOL -> c.parasols.visible
        SceneObjectType.TREE, SceneObjectType.PALM_TREE -> c.trees.visible
        SceneObjectType.SNOWMAN -> c.snowmen.visible
        SceneObjectType.GIFT -> c.gifts.visible
        SceneObjectType.PENGUIN -> c.penguins.visible
        SceneObjectType.BUNNY -> c.bunnies.visible
        SceneObjectType.EASTER_EGG -> c.easterEggs.visible
        SceneObjectType.PUMPKIN -> c.pumpkins.visible
        else -> true
    }

    private companion object {
        /** Objects standing at the twelve themes' defaults, on the fraction: the count `keepCandidate` gives and the table totals. */
        const val STANDING_AT_DEFAULTS = 326
    }
}
