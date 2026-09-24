package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.R
import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card does not lie: what it shows, where it shows it, and whether it can be seen.
 *
 * ### Why this test exists
 *
 * Twelve gallery cards shipped for eight releases with forty-nine things wrong with them, and no
 * test noticed, because every test that looked at a card asked the same kind of question:
 * `ThemePreviewSceneTest` asks *which sprites are in the list*, `PreviewRendererAgreementTest`
 * asks *whether a building's parts are the deal the wallpaper would deal*. Neither asks where an
 * object stands, and neither asks whether anything is standing in front of it. So a dolphin could
 * be -- and was -- drawn on the sand, and a bar could be 88 % behind a house, with every
 * assertion green.
 *
 * The three questions below are the ones nobody was asking. They are run against the same two
 * sources the wallpaper itself runs on: `SceneObjectCatalog` plus the customization's own density
 * filters for what the scene draws, and the **alpha channel of the shipped PNGs** for where the
 * ink of a preview object actually falls. Nothing here restates a number from the other side.
 *
 * - **R1, families.** Every family the scene draws at least once per tile appears on the card, and
 *   the card shows no family the scene does not draw. [EXEMPT_FROM_THE_CARD] lists what is left
 *   out on purpose, each with its reason; [HOUR_DEPENDENT] lists what a card may or may not have
 *   because a card is a single hour and the scene is all of them.
 * - **R2, place.** Every boat and every dolphin has its ink inside the water band.
 * - **R3, sight.** Nothing is more than half covered by something drawn after it.
 *
 * ### The measurement
 *
 * An object's box is the union of its parts' **ink** -- the alpha bounding box of each PNG, in
 * sprite pixels over [SpriteBlitter.SPRITE_PIXELS_PER_UNIT], put through the part's own offset and
 * the item's own scale. Not the canvas: a sprite's canvas carries transparent margin that is part
 * of the drawing's placement and no part of what a viewer sees, and measuring canvases would call
 * a dolphin covered that is not and miss one that is.
 *
 * Draw order is [drawOrder], which is `ThemePreview.drawScene`'s order and is asserted against the
 * painter's own structure by the comment there: backdrop above the horizon, the water, backdrop
 * below the horizon, the street, the road's traffic, then the things standing on the ground.
 */
class ThemePreviewTruthTest {

    // ------------------------------------------------------------------ the two exemption lists

    /**
     * Families the scene draws and the card is not expected to, each for a reason that is about
     * the artwork or the animation rather than about the layout.
     *
     * - `PARASOL`: the renderer draws it procedurally. There is no parasol sprite in the library,
     *   and standing in a differently-shaped one would be the card showing something the scene
     *   does not contain -- the one thing `ThemePreviewScenes` must not do.
     *
     * **`SANTA` was the second entry and is not any more.** v5.7A named its reason -- a periodic
     * flyby on a random interval, not a population -- and said in the same breath that the
     * fireworks are periodic in exactly that way and the card draws those. The maintainer settled
     * it: the sleigh goes on the card. So the list is down to the one exemption that rests on the
     * artwork not existing, and `SANTA` is now held to the rule like everything else -- Christmas
     * is the built-in that carries it, and any theme whose `santaEnabled` is on owes it too.
     */
    private val EXEMPT_FROM_THE_CARD = setOf("PARASOL")

    /**
     * Families whose presence depends on the hour rather than on the theme, so neither their
     * absence nor their presence can be held against a card: a card is one moment.
     */
    private val HOUR_DEPENDENT = setOf("SUN", "MOON", "CLOUDS")

    // ------------------------------------------------------------------------------- what draws

    /**
     * Sprite name prefix to family, longest prefix first.
     *
     * Every drawable a card can emit must match one of these -- `every sprite the card can draw
     * has a family` asserts it. The rule this replaces had no such assertion and a hole to go with
     * it: its table said `fire_`, the sprites are `firetruck_body` and `firetruck_ladder`, so the
     * fire appliance fell into an "unknown" bucket that both the presence check and the coverage
     * check skipped. A silently unchecked object is worse than an unchecked one.
     */
    private val FAMILY_BY_PREFIX: List<Pair<String, String>> = listOf(
        "house_large_" to "HOUSE_LARGE",
        "house_small_" to "HOUSE_SMALL",
        "ground_flowers" to "FLOWERS",
        "santa_sleigh" to "SANTA",
        "star_sparkle" to "LIGHTS",
        "restaurant_" to "RESTAURANT",
        "firetruck_" to "CAR",
        "palmtree_" to "PALM_TREE",
        "easteregg_" to "EASTER_EGG",
        "sailboat_" to "SAILBOAT",
        "snowman_" to "SNOWMAN",
        "penguin_" to "PENGUIN",
        "pumpkin_" to "PUMPKIN",
        "dolphin_" to "DOLPHIN",
        "firework" to "FIREWORKS",
        "school_" to "SCHOOL",
        "person_" to "PEOPLE",
        "police_" to "CAR",
        "bunny_" to "BUNNY",
        "tower_" to "TOWER",
        "cloud_" to "CLOUDS",
        "taxi_" to "CAR",
        "tree_" to "TREE",
        "gift_" to "GIFT",
        "bird_" to "BIRD",
        "moon_" to "MOON",
        "car_" to "CAR",
        "bar_" to "BAR",
        "sun_" to "SUN",
    )

    private fun familyOf(spriteName: String): String? =
        FAMILY_BY_PREFIX.firstOrNull { spriteName.startsWith(it.first) }?.second

    /** How many of a pool of [pool] candidates survive [density], the renderer's own arithmetic. */
    private fun poolCount(density: Float, pool: Int, salt: Int): Int {
        val offset = CandidateThreshold.offsetFor(salt)
        val fallback = CandidateThreshold.fallbackIndexFor(density, pool, offset)
        return (0 until pool).count { CandidateThreshold.isPresent(it, density, offset, fallback) }
    }

    /**
     * What the wallpaper draws for this theme and customization, per tile, by family.
     *
     * Read through the functions the engine reads -- the catalogue, `keepCandidate`,
     * `palmSpeciesApplied`, `variantFor`, `CarSelection`, `PedestrianPopulation`,
     * `CandidateThreshold` -- so this cannot drift from the scene the way a copied table would.
     */
    private fun sceneFamilies(theme: SceneTheme, c: SceneCustomization, night: Boolean): Map<String, Int> {
        val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
        val out = sortedMapOf<String, Int>()
        fun add(family: String, n: Int) {
            if (n > 0) out[family] = (out[family] ?: 0) + n
        }
        for (raw in layout.staticObjects) {
            if (!c.keepCandidate(raw)) continue
            val spec = c.palmSpeciesApplied(raw)
            val family = when (spec.type) {
                SceneObjectType.HOUSE, SceneObjectType.SKYSCRAPER -> SceneObjectRenderer.variantFor(spec).name
                else -> spec.type.name
            }
            add(family, 1)
        }
        if (c.cars.visible) {
            add("CAR", CarSelection.countFor(if (night) c.carsNightDensity else c.cars.density, layout.cars.size))
        }
        if (c.people.visible) {
            add("PEOPLE", PedestrianPopulation.build(
                theme.id.hashCode(),
                if (night) c.peopleNightDensity else c.people.density,
                SceneSpace.PAVEMENT_NEAR_Y_FRACTION,
                SceneSpace.PAVEMENT_FAR_Y_FRACTION,
            ).size)
        }
        if (c.lake.visible && c.lake.sailboatsVisible) {
            add("SAILBOAT", poolCount(c.lake.sailboatsDensity, PaperRenderer.LAKE_DECORATION_POOL_SIZE, EffectId.SAILBOATS))
        }
        if (c.lake.visible && c.lake.dolphinsVisible) {
            add("DOLPHIN", poolCount(c.lake.dolphinsDensity, PaperRenderer.LAKE_DECORATION_POOL_SIZE, EffectId.DOLPHINS))
        }
        // The flock is a day population: `nightBirds` is off in every built-in, and a card drawn
        // at night owes none unless the customization has turned them on.
        if (c.birds.visible && (!night || c.birds.nightBirds)) {
            add("BIRD", poolCount(c.birds.density, PaperRenderer.BIRD_POOL_SIZE, EffectId.BIRDS))
        }
        if (c.flowersEnabled) add("FLOWERS", SceneObjectRenderer.FLOWER_CLUMP_COUNT)
        // All three `drawChristmasLights` call sites are inside a tree: the fir's, the leafy
        // tree's and the palm's. No trees, no lights -- which is why this reads both flags.
        if (c.christmasDecorationsEnabled && c.trees.visible) add("LIGHTS", 1)
        if (theme.hasFireworks) add("FIREWORKS", 1)
        if (c.santaEnabled) add("SANTA", 1)
        if (c.sun.visible) add("SUN", 1)
        if (c.moon.visible) add("MOON", 1)
        if (c.clouds.visible) add("CLOUDS", 1)
        return out
    }

    /** What the card draws, by family: one object counts once for each family among its parts. */
    private fun cardFamilies(scene: ThemePreviewScene): Map<String, Int> {
        val out = sortedMapOf<String, Int>()
        for (item in drawOrder(scene)) {
            for (family in item.parts.mapNotNull { familyOf(nameOf(it.resId)) }.toSet()) {
                out[family] = (out[family] ?: 0) + 1
            }
        }
        return out
    }

    // ------------------------------------------------------------------------- where the ink is

    /** `ThemePreview.drawScene`'s order, which is the order that decides what covers what. */
    private fun drawOrder(scene: ThemePreviewScene): List<PreviewItem> = buildList {
        val horizon = ThemePreviewScene.HORIZON_UNITS
        addAll(scene.backdrop.filter { it.y < horizon })
        addAll(scene.water)
        addAll(scene.backdrop.filter { it.y >= horizon })
        addAll(scene.items)
        addAll(scene.cars)
        addAll(scene.ground)
    }

    private data class Box(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val area: Float get() = (right - left) * (bottom - top)
    }

    /** The union of an item's parts' ink, in card units. Null when no part has any ink. */
    private fun inkBox(item: PreviewItem): Box? {
        var left = Float.MAX_VALUE
        var top = Float.MAX_VALUE
        var right = -Float.MAX_VALUE
        var bottom = -Float.MAX_VALUE
        for (part in item.parts) {
            val ink = inkOf(part.resId) ?: continue
            left = minOf(left, item.x + item.scale * (part.ox + ink.left))
            top = minOf(top, item.y + item.scale * (part.oy + ink.top))
            right = maxOf(right, item.x + item.scale * (part.ox + ink.right))
            bottom = maxOf(bottom, item.y + item.scale * (part.oy + ink.bottom))
        }
        return if (left > right) null else Box(left, top, right, bottom)
    }

    private fun overlapFraction(covered: Box, over: Box): Float {
        val left = maxOf(covered.left, over.left)
        val top = maxOf(covered.top, over.top)
        val right = minOf(covered.right, over.right)
        val bottom = minOf(covered.bottom, over.bottom)
        if (right <= left || bottom <= top) return 0f
        if (covered.area <= 0f) return 0f
        return ((right - left) * (bottom - top)) / covered.area
    }

    // --------------------------------------------------------------------------------- the runs

    /** The twelve built-ins, each with the customization it actually ships with. */
    private fun builtIns(): List<Pair<SceneTheme, SceneCustomization>> =
        ThemeCatalog.ALL.map { it to defaultCustomizationFor(it.id) }

    /**
     * Customizations no built-in ships, chosen to move every number the card branches on: the two
     * building densities that switch the skyline, the tree densities that switch the tree line,
     * a lake at the bottom of its range and one at the top, the palms switch, a theme with its
     * water turned off under it, and one with every decoration a user can turn on turned on.
     */
    private fun custom(): List<Pair<String, Pair<SceneTheme, SceneCustomization>>> {
        val sunset = ThemeCatalog.byId("sunset")
        val beach = ThemeCatalog.byId("beach")
        val tundra = ThemeCatalog.byId("tundra")
        val spring = ThemeCatalog.byId("spring")
        val base = defaultCustomizationFor("sunset")
        return listOf(
            "sunset, a city's density of buildings" to
                (sunset to base.copy(buildings = base.buildings.copy(density = 1f))),
            "sunset, woodland thinned to a scattering" to
                (sunset to base.copy(trees = base.trees.copy(density = 0.2f))),
            "sunset, woodland at its thickest" to
                (sunset to base.copy(trees = base.trees.copy(density = 1f))),
            "sunset, a pond at the bottom of its range with boats and dolphins on it" to
                (sunset to base.copy(lake = base.lake.copy(
                    visible = true, height = 0.1f, sailboatsVisible = true, dolphinsVisible = true,
                ))),
            "sunset, open water at full height" to
                (sunset to base.copy(lake = base.lake.copy(
                    visible = true, height = 1f, sailboatsVisible = true, dolphinsVisible = true,
                ))),
            "beach with its palms switched off" to
                (beach to defaultCustomizationFor("beach").copy(palmsEnabled = false)),
            "beach with its sea turned off" to
                (beach to defaultCustomizationFor("beach").let { it.copy(lake = it.lake.copy(visible = false)) }),
            "tundra with the night birds its flock does not have" to
                (tundra to defaultCustomizationFor("tundra").let { it.copy(birds = it.birds.copy(nightBirds = true)) }),
            "spring with no houses and no people" to
                (spring to defaultCustomizationFor("spring").let {
                    it.copy(houses = it.houses.copy(visible = false), people = it.people.copy(visible = false))
                }),
            "spring in fall colours with pumpkins out" to
                (spring to defaultCustomizationFor("spring").let {
                    it.copy(fallColorsEnabled = true, pumpkins = it.pumpkins.copy(visible = true))
                }),
        )
    }

    private fun checkFamilies(label: String, theme: SceneTheme, c: SceneCustomization, forceNight: Boolean?): List<String> {
        val night = forceNight ?: (c.horrorSkyEnabled || theme.hasFireworks)
        val scene = ThemePreviewScenes.forTheme(theme, c, forceNight)
        val expected = sceneFamilies(theme, c, night)
        val actual = cardFamilies(scene)
        val out = mutableListOf<String>()
        for ((family, count) in expected) {
            if (family in EXEMPT_FROM_THE_CARD || family in HOUR_DEPENDENT) continue
            if (actual[family] == null) out += "$label: R1 the scene draws $count $family and the card shows none"
        }
        for (family in actual.keys) {
            if (family in HOUR_DEPENDENT) continue
            if (expected[family] == null) out += "$label: R1 the card shows $family and the scene draws none"
        }
        return out
    }

    private fun checkWater(label: String, theme: SceneTheme, c: SceneCustomization, forceNight: Boolean?): List<String> {
        val scene = ThemePreviewScenes.forTheme(theme, c, forceNight)
        val out = mutableListOf<String>()
        for (item in drawOrder(scene)) {
            val families = item.parts.mapNotNull { familyOf(nameOf(it.resId)) }.toSet()
            if (families.none { it in WATER_FAMILIES }) continue
            val what = families.first { it in WATER_FAMILIES }
            val box = inkBox(item) ?: continue
            if (!scene.hasLake) {
                out += "$label: R2 a $what is drawn on a card with no water"
                continue
            }
            // **The waterline, not the whole body**, and the first draft of this rule had it
            // wrong: it asked for every pixel to be inside the band, and a sailboat's sail is
            // above the water in the scene too -- `gatherLakeDecorations`' own comment says a
            // sail stands about four lane widths above its waterline, and a dolphin is drawn
            // mid-leap, climbing out of it. What is true of both, and is the thing that went
            // wrong, is where the object *meets* the water: the bottom of its ink is the line it
            // floats on, and that line belongs inside the band. Below the band's floor is an
            // animal on the ground; above its surface is one in the air.
            //
            // A unit of slack, because the band's edge is a fill and the ink's edge is a
            // measurement, and a hull whose last row of pixels touches the waterline is afloat.
            val waterline = box.bottom
            if (waterline > scene.lake.bottom + CARD_WATERLINE_TOLERANCE_UNITS) {
                val out_ = waterline - scene.lake.bottom
                out += "$label: R2 a $what floats %.1f units below the water's floor (%.0f %% of its body is on the ground); ink %.1f..%.1f, water %.1f..%.1f"
                    .format(out_, 100f * out_ / (box.bottom - box.top), box.top, box.bottom, scene.lake.top, scene.lake.bottom)
            }
            if (waterline < scene.lake.top - CARD_WATERLINE_TOLERANCE_UNITS) {
                out += "$label: R2 a $what floats %.1f units above the water's surface; ink %.1f..%.1f, water %.1f..%.1f"
                    .format(scene.lake.top - waterline, box.top, box.bottom, scene.lake.top, scene.lake.bottom)
            }
        }
        return out
    }

    private fun checkVisible(label: String, theme: SceneTheme, c: SceneCustomization, forceNight: Boolean?): List<String> {
        val scene = ThemePreviewScenes.forTheme(theme, c, forceNight)
        val painted = drawOrder(scene)
        val boxes = painted.map { inkBox(it) }
        val families = painted.map { item -> item.parts.mapNotNull { familyOf(nameOf(it.resId)) }.toSet() }
        val out = mutableListOf<String>()
        for (i in painted.indices) {
            val box = boxes[i] ?: continue
            val family = families[i].firstOrNull { it in COVERABLE } ?: continue
            var worst = 0f
            var worstBy = ""
            for (j in i + 1 until painted.size) {
                val other = boxes[j] ?: continue
                val fraction = overlapFraction(box, other)
                if (fraction > worst) {
                    worst = fraction
                    worstBy = families[j].firstOrNull() ?: "something"
                }
            }
            if (worst > 0.5f) {
                out += "$label: R3 the $family at x=%.0f is %.0f %% covered by the $worstBy drawn after it"
                    .format(painted[i].x, 100f * worst)
            }
        }
        return out
    }

    // ------------------------------------------------------------------------------- the checks

    @Test
    fun `every sprite the card can draw has a family`() {
        val unknown = sortedSetOf<String>()
        for ((theme, c) in builtIns() + custom().map { it.second }) {
            for (forceNight in listOf(null, true)) {
                for (item in drawOrder(ThemePreviewScenes.forTheme(theme, c, forceNight))) {
                    for (part in item.parts) {
                        val name = nameOf(part.resId)
                        if (familyOf(name) == null) unknown += name
                    }
                }
            }
        }
        assertEquals(
            "these sprites are on a card and FAMILY_BY_PREFIX does not name them, so both the " +
                "presence rule and the coverage rule silently skip whatever draws them",
            emptySet<String>(),
            unknown.toSet(),
        )
    }

    @Test
    fun `R1 every family the scene draws is on the card, and nothing else is`() {
        val fails = mutableListOf<String>()
        for ((theme, c) in builtIns()) fails += checkFamilies(theme.id, theme, c, null)
        report(fails)
    }

    @Test
    fun `R2 every boat and every dolphin has its ink inside the water`() {
        val fails = mutableListOf<String>()
        for ((theme, c) in builtIns()) fails += checkWater(theme.id, theme, c, null)
        report(fails)
    }

    @Test
    fun `R3 nothing is more than half covered by what is drawn after it`() {
        val fails = mutableListOf<String>()
        for ((theme, c) in builtIns()) fails += checkVisible(theme.id, theme, c, null)
        report(fails)
    }

    @Test
    fun `the three rules hold on the night the World and scene strip can force`() {
        val fails = mutableListOf<String>()
        for ((theme, c) in builtIns()) {
            val label = "${theme.id} (forced night)"
            fails += checkFamilies(label, theme, c, true)
            fails += checkWater(label, theme, c, true)
            fails += checkVisible(label, theme, c, true)
        }
        report(fails)
    }

    @Test
    fun `the three rules hold on customizations no built-in ships`() {
        val fails = mutableListOf<String>()
        for ((label, pair) in custom()) {
            val (theme, c) = pair
            fails += checkFamilies(label, theme, c, null)
            fails += checkWater(label, theme, c, null)
            fails += checkVisible(label, theme, c, null)
        }
        report(fails)
    }

    /**
     * The school stands where the catalogue puts it, which is the one identity this file can
     * check against the scene rather than against itself.
     */
    @Test
    fun `the card's school is at the depth the catalogue gives every theme's school`() {
        val depths = ThemeCatalog.ALL.flatMap { theme ->
            SceneObjectCatalog.layoutFor(theme.id, theme.accentColor).staticObjects
                .filter { SceneObjectRenderer.variantFor(it) == SceneSpace.SceneVariant.SCHOOL }
                .map { it.depthFraction }
        }.toSet()
        assertEquals("every theme's school is at one depth", 1, depths.size)
        assertEquals(
            "the card's declared school depth is the catalogue's",
            depths.first(),
            ThemePreviewScenes.PreviewIdentity.SCHOOL_DEPTH,
            0.0005f,
        )
    }

    private fun report(fails: List<String>) {
        assertTrue(
            "${fails.size} place(s) where the card does not say what the scene does:\n" +
                fails.joinToString("\n") { "  - $it" },
            fails.isEmpty(),
        )
    }

    private companion object {
        /**
         * How far a waterline may miss the band and still count as afloat.
         *
         * `CARD_`, not `LAKE_`: this is a length on the card's own 320x240 canvas, and
         * `UnitFrameTest` resolves a `LAKE_` prefix to the lake sprite's own units.
         */
        const val CARD_WATERLINE_TOLERANCE_UNITS = 1f
        val WATER_FAMILIES = setOf("SAILBOAT", "DOLPHIN")

        /** The families whose one job is to be seen: a star or a snowflake is not one of them. */
        val COVERABLE = setOf(
            "TOWER", "RESTAURANT", "SCHOOL", "BAR", "HOUSE_LARGE", "HOUSE_SMALL",
            "TREE", "PALM_TREE", "PEOPLE", "CAR", "SAILBOAT", "DOLPHIN",
            "SNOWMAN", "GIFT", "PENGUIN", "BUNNY", "EASTER_EGG", "PUMPKIN",
        )

        private val NAMES: Map<Int, String> = R.drawable::class.java.fields
            .filter { it.type == Int::class.javaPrimitiveType }
            .associate { it.getInt(null) to it.name }

        fun nameOf(resId: Int): String = NAMES[resId] ?: "id$resId"

        private val INK = HashMap<Int, Box?>()

        /** The alpha bounding box of a shipped PNG, in the sprite's own local units. */
        fun inkOf(resId: Int): Box? = INK.getOrPut(resId) {
            val file = File(DRAWABLES, "${nameOf(resId)}.png")
            if (!file.isFile) return@getOrPut null
            val image = ImageIO.read(file) ?: return@getOrPut null
            var left = Int.MAX_VALUE
            var top = Int.MAX_VALUE
            var right = Int.MIN_VALUE
            var bottom = Int.MIN_VALUE
            for (y in 0 until image.height) {
                for (x in 0 until image.width) {
                    if ((image.getRGB(x, y) ushr 24) == 0) continue
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
            if (left > right) return@getOrPut null
            val unit = SpriteBlitter.SPRITE_PIXELS_PER_UNIT
            Box(left / unit, top / unit, (right + 1) / unit, (bottom + 1) / unit)
        }

        val DRAWABLES: File by lazy {
            var dir: File? = File(".").absoluteFile
            while (dir != null) {
                for (prefix in listOf("", "app/")) {
                    val candidate = File(dir, "${prefix}src/main/res/drawable-nodpi")
                    if (candidate.isDirectory) return@lazy candidate
                }
                dir = dir.parentFile
            }
            error("could not locate src/main/res/drawable-nodpi")
        }
    }
}
