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
 * - **R4, colour and landscape** (v5.8B). The card is one real moment of the wallpaper's day, and
 *   every colour it paints the landscape with is the wallpaper's own rule at that moment: the sky
 *   from [SkyGradient], the hills, the mountains and the water blended on the moment's `dayBlend`,
 *   the road from `SceneObjectRenderer.roadColor` -- and there only when
 *   `SceneObjectRenderer.drawsRoad` says the scene has one. R1 to R3 held the objects to the scene
 *   and left all of this to the card, and the card had drifted: Sunset's sky coral where the
 *   wallpaper is never coral, the road a cold grey the wallpaper does not use, dunes on the Desert,
 *   and a sky edit that never reached the card at all.
 * - **R5, the floor** (v5.8E, V3-47). A card car's body ends on the floor the card gives it,
 *   `PREVIEW_CAR_FLOOR_DROP` below its ground line, measured off the body's PNG. The constant that
 *   placed them still described the v4.19 bodies, and all 24 cars of the twelve cards stood 1.75
 *   card units above it.
 * - **R6, the species** (v5.8E, V3-48). A card shows a fir only where the wallpaper can stand one
 *   (`SceneObjectRenderer.drawsFirs`). R1 counts a fir as a tree, so it passed Tundra's card, which
 *   drew a fir for its sparse wood while its wallpaper draws snowy oaks.
 * - **R7, the objects' colours** (v5.9G, inventory I-38). The trees, the cars and the decorations
 *   wear the colours `colorFor` gives at the card's moment, the palms the shade `nightShadeFor`
 *   gives, the clouds `drawClouds`' pair on the same blend, and the windows the glass
 *   `drawNeighbourhoodBuilding` lights, a business's by its opening hours. R4 had stopped at the building
 *   walls, and on every night card everything in front of them kept its noon colours.
 * - **R8, the fixed art on a tinted body** (v5.9G). A pumpkin is carved where `halloweenEnabled`
 *   carves the wallpaper's, and a penguin's belly is the wallpaper's white.
 *
 * The runs cover the twelve built-ins, customizations no built-in ships (including every
 * landscape colour edited), and **a theme saved under a `custom:` id**, which is where the card's
 * old rule -- palms by theme name -- said oaks while the wallpaper drew palms.
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
            val spec = c.palmSpeciesApplied(raw, layout.hasPalmSlots())
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
     * a lake at the bottom of its range and one at the top, the palms switch (both halves), a theme with its
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
            // v5.10C: the switch on a theme that plants none, the three the maintainer is shown.
            "christmas with palms in place of its trees" to
                (ThemeCatalog.byId("christmas") to defaultCustomizationFor("christmas").copy(palmsInsteadOfTrees = true)),
            "autumn with palms in place of its trees" to
                (ThemeCatalog.byId("autumn") to defaultCustomizationFor("autumn").copy(palmsInsteadOfTrees = true)),
            "winter with palms in place of its trees" to
                (ThemeCatalog.byId("winter") to defaultCustomizationFor("winter").copy(palmsInsteadOfTrees = true)),
            // v5.10C2: the firs stay firs among the palms, wherever the Christmas layer stands them.
            "autumn with the Christmas lights and palms in place of its trees" to
                (ThemeCatalog.byId("autumn") to defaultCustomizationFor("autumn").copy(palmsInsteadOfTrees = true, christmasDecorationsEnabled = true)),
            "christmas with palms, its woodland thinned to a scattering" to
                (ThemeCatalog.byId("christmas") to defaultCustomizationFor("christmas").let {
                    it.copy(palmsInsteadOfTrees = true, trees = it.trees.copy(density = 0.2f))
                }),
            // The wallpaper lights a palm too; until v5.10C the card's palm had no lights to show.
            "beach with the Christmas lights on" to
                (beach to defaultCustomizationFor("beach").copy(christmasDecorationsEnabled = true)),
            "beach with its sea turned off" to
                (beach to defaultCustomizationFor("beach").let { it.copy(lake = it.lake.copy(visible = false)) }),
            "tundra with the Christmas decorations on" to
                (tundra to defaultCustomizationFor("tundra").copy(christmasDecorationsEnabled = true)),
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

    /**
     * R5: every car on the card ends where the card says its floor is. The fire appliance is not a
     * car body and stands at the renderer's own two origins instead, so it is not asked.
     */
    private fun checkFloor(label: String, theme: SceneTheme, c: SceneCustomization, forceNight: Boolean?): List<String> {
        val scene = ThemePreviewScenes.forTheme(theme, c, forceNight)
        val out = mutableListOf<String>()
        for (item in scene.cars) {
            for (part in item.parts) {
                if (!nameOf(part.resId).startsWith("car_body_")) continue
                val ink = inkOf(part.resId) ?: continue
                val floor = item.y + item.scale * (part.oy + ink.bottom)
                val declared = item.y + item.scale * ThemePreviewScenes.PREVIEW_CAR_FLOOR_DROP
                if (kotlin.math.abs(floor - declared) > CARD_FLOOR_TOLERANCE_UNITS) {
                    out += "$label: R5 the ${nameOf(part.resId)} at x=%.0f ends at %.2f, %.2f card units %s the floor the card gives it (%.2f)"
                        .format(item.x, floor, kotlin.math.abs(floor - declared), if (floor < declared) "above" else "below", declared)
                }
            }
        }
        return out
    }

    /** R6: no fir on a card whose wallpaper stands none. */
    private fun checkSpecies(label: String, theme: SceneTheme, c: SceneCustomization, forceNight: Boolean?): List<String> {
        val scene = ThemePreviewScenes.forTheme(theme, c, forceNight)
        val firs = drawOrder(scene).count { item -> item.parts.any { nameOf(it.resId).startsWith("tree_fir") } }
        if (firs == 0 || SceneObjectRenderer.drawsFirs(c)) return emptyList()
        return listOf("$label: R6 the card shows $firs fir(s) and the wallpaper stands none (no Christmas decorations)")
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
    fun `R5 every card car ends on the floor the card gives it`() {
        val fails = mutableListOf<String>()
        var cars = 0
        for ((theme, c) in builtIns()) {
            fails += checkFloor(theme.id, theme, c, null)
            cars += ThemePreviewScenes.forTheme(theme, c).cars.count { item -> item.parts.any { nameOf(it.resId).startsWith("car_body_") } }
        }
        // Two per card: a rule that found no car body to measure would pass over nothing.
        assertEquals("the car bodies R5 measured on the twelve cards", 24, cars)
        report(fails)
    }

    @Test
    fun `R6 a card shows a fir only where its wallpaper can stand one`() {
        val fails = mutableListOf<String>()
        for ((theme, c) in builtIns()) {
            fails += checkSpecies(theme.id, theme, c, null)
            fails += checkSpecies("${theme.id} (forced night)", theme, c, true)
        }
        for ((label, pair) in custom()) fails += checkSpecies(label, pair.first, pair.second, null)
        report(fails)
        // And the rule is not vacuous: the one built-in with the decorations on does draw its fir.
        val christmas = ThemeCatalog.byId("christmas")
        assertTrue(
            "Christmas's card has no fir, so R6 is only ever asked about cards without one",
            drawOrder(ThemePreviewScenes.forTheme(christmas, defaultCustomizationFor("christmas")))
                .any { item -> item.parts.any { nameOf(it.resId).startsWith("tree_fir") } },
        )
    }

    @Test
    fun `the three rules hold on the night the World and scene strip can force`() {
        val fails = mutableListOf<String>()
        for ((theme, c) in builtIns()) {
            val label = "${theme.id} (forced night)"
            fails += checkFamilies(label, theme, c, true)
            fails += checkWater(label, theme, c, true)
            fails += checkVisible(label, theme, c, true)
            fails += checkFloor(label, theme, c, true)
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
            fails += checkFloor(label, theme, c, null)
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

    /** A theme saved from Beach, the way `snapshotEntry` saves one: its layout, a `custom:` id. */
    private fun <T> withSavedBeach(block: (SceneTheme, SceneCustomization) -> T): T {
        val beach = ThemeCatalog.byId("beach")
        val id = "custom:truth-test-beach"
        val entry = CustomThemeEntry(
            id = id,
            name = "Saved beach",
            theme = beach.copy(id = id, displayName = "Saved beach"),
            layout = SceneObjectCatalog.layoutFor("beach", beach.accentColor),
            customization = defaultCustomizationFor("beach"),
        )
        CustomThemeRegistry.update(CustomThemeData.EMPTY.copy(customThemes = listOf(entry)))
        try {
            return block(entry.theme, entry.customization)
        } finally {
            CustomThemeRegistry.update(CustomThemeData.EMPTY)
        }
    }

    /** Every landscape colour the card paints, moved off its default, on one theme. */
    private fun editedLandscape(): Pair<SceneTheme, SceneCustomization> {
        val autumn = ThemeCatalog.byId("autumn")
        val c = defaultCustomizationFor("autumn")
        return autumn to c.copy(
            sky = c.sky.copy(
                colorDayHigh = 0xFF123456.toInt(), colorDayLow = 0xFF654321.toInt(),
                colorNightHigh = 0xFF0A0B0C.toInt(), colorNightLow = 0xFF1C1D1E.toInt(),
                colorSunriseLow = 0xFFAA0011.toInt(), colorSunsetLow = 0xFF11AA00.toInt(),
            ),
            hillsColorDay = 0xFF778899.toInt(), hillsColorNight = 0xFF112233.toInt(),
            mountainsBack = c.mountainsBack.copy(visible = true, colorDay = 0xFF405060.toInt(), colorNight = 0xFF102030.toInt()),
            mountainsFront = c.mountainsFront.copy(visible = true, colorDay = 0xFF506070.toInt(), colorNight = 0xFF203040.toInt()),
            lake = c.lake.copy(visible = true, colorDay = 0xFF2080C0.toInt(), colorNight = 0xFF102040.toInt()),
        )
    }

    /**
     * R4: the landscape of the card is the wallpaper's own at the card's moment.
     *
     * "The card's moment" is itself held to the wallpaper: it must be what
     * `SunPositionCalculator.compute` gives for a clock hour, not a blend made up for a picture.
     */
    private fun checkLandscape(label: String, theme: SceneTheme, c: SceneCustomization, forceNight: Boolean?): List<String> {
        val night = forceNight ?: (c.horrorSkyEnabled || theme.hasFireworks)
        val layout = SceneObjectCatalog.layoutFor(theme.id, theme.accentColor)
        val scene = ThemePreviewScenes.forTheme(theme, c, forceNight)
        val phase = ThemePreviewScenes.cardPhase(theme, night)
        val out = mutableListOf<String>()
        // A whole hour, because that is what the fixed-time slider can set: the card's moment is
        // one a user can put the wallpaper at and compare.
        val hours = (0 until 24).map { it.toFloat() }
        if (hours.none { SunPositionCalculator.compute(it) == phase }) {
            out += "$label: R4 the card's moment is no hour the fixed-time slider can set"
        }
        val d = phase.dayBlend
        fun blend(night: Int, day: Int) = SceneColour.blendArgb(night, day, d)
        fun hex(v: Int) = "%08X".format(v)
        fun same(what: String, expected: Int, actual: Int) {
            if (expected != actual) out += "$label: R4 $what is ${hex(actual)} on the card, ${hex(expected)} in the wallpaper"
        }
        if (c.horrorSkyEnabled) {
            same("the sky's top", SkyGradient.horrorTop(d), scene.skyTop)
            same("the sky's horizon", SkyGradient.horrorBottom(d), scene.skyBottom)
        } else {
            same("the sky's top", SkyGradient.top(c.sky, d), scene.skyTop)
            same("the sky's horizon", SkyGradient.bottom(c.sky, d, phase.progress), scene.skyBottom)
        }
        same("the hills", blend(c.hillsColorNight, c.hillsColorDay), scene.groundColour)
        if (c.lake.visible) same("the water", blend(c.lake.colorNight, c.lake.colorDay), scene.lake.colour)
        val mountainColours = buildSet {
            if (c.mountainsBack.visible) add(blend(c.mountainsBack.colorNight, c.mountainsBack.colorDay))
            if (c.mountainsFront.visible) add(blend(c.mountainsFront.colorNight, c.mountainsFront.colorDay))
        }
        for (peak in scene.peaks) {
            if (peak.colour !in mountainColours) out += "$label: R4 a mountain is ${hex(peak.colour)}, which neither layer is"
        }
        if (mountainColours.isNotEmpty() && scene.peaks.isEmpty()) out += "$label: R4 the scene has mountains and the card none"
        val road = SceneObjectRenderer.drawsRoad(layout, c)
        if (road != scene.hasRoad) out += "$label: R4 the scene ${if (road) "has" else "has no"} road and the card ${if (scene.hasRoad) "has" else "has no"} road"
        if (road) same("the road", SceneObjectRenderer.roadColor(d), scene.roadColour)
        for (star in scene.dots.filter { !it.front }) {
            same("a star", PaperRenderer.STAR_POINT_COLOR, star.colour)
        }
        val palms = scene.items.any { item -> item.parts.any { nameOf(it.resId).startsWith("palmtree_") } }
        // Whether a palm stands in the scene, read off the slots it keeps (v5.10C2). It was the switch and
        // the trees' visibility, and that stopped being the same thing when the firs began to stay firs
        // among the palms: Christmas at 20 % keeps two tree slots and both are firs, so the switch is on
        // over a scene with no palm in it.
        val scenePalms = c.trees.visible && layout.staticObjects.any {
            c.keepCandidate(it) && c.palmSpeciesApplied(it, layout.hasPalmSlots()).type == SceneObjectType.PALM_TREE
        }
        if (palms != scenePalms) out += "$label: R4 the scene ${if (scenePalms) "has" else "has no"} palms and the card ${if (palms) "has" else "has no"}"
        return out
    }

    @Test
    fun `R4 the card's sky, hills, mountains, water and road are the wallpaper's`() {
        val fails = mutableListOf<String>()
        for ((theme, c) in builtIns()) {
            fails += checkLandscape(theme.id, theme, c, null)
            fails += checkLandscape("${theme.id} (forced night)", theme, c, true)
        }
        for ((label, pair) in custom()) fails += checkLandscape(label, pair.first, pair.second, null)
        val (autumn, edited) = editedLandscape()
        fails += checkLandscape("autumn with every landscape colour edited", autumn, edited, null)
        fails += checkLandscape("autumn with every landscape colour edited (forced night)", autumn, edited, true)
        val noCars = defaultCustomizationFor("autumn").let { it.copy(cars = it.cars.copy(visible = false)) }
        fails += checkLandscape("autumn with its cars switched off", autumn, noCars, null)
        report(fails)
    }

    @Test
    fun `the six rules hold on a theme saved under a custom id`() {
        val fails = withSavedBeach { theme, c ->
            val label = "a theme saved from Beach"
            checkFamilies(label, theme, c, null) + checkWater(label, theme, c, null) +
                checkVisible(label, theme, c, null) + checkLandscape(label, theme, c, null) +
                checkFloor(label, theme, c, null) + checkSpecies(label, theme, c, null)
        }
        report(fails)
    }

    @Test
    fun `an edited sky reaches the card`() {
        // The whole of D1 in one case: the card used to paint every day sky from the theme's old
        // arrays, so an edit to the six sky colours never showed on it.
        val (autumn, edited) = editedLandscape()
        val scene = ThemePreviewScenes.forTheme(autumn, edited)
        assertEquals(0xFF123456.toInt(), scene.skyTop)
        assertEquals(0xFF654321.toInt(), scene.skyBottom)
    }

    @Test
    fun `rain and snow are painted over the card, stars behind it`() {
        val winter = ThemeCatalog.byId("winter")
        val c = defaultCustomizationFor("winter")
        assertTrue("winter snows by default", c.precipitation.visible)
        val snow = ThemePreviewScenes.forTheme(winter, c).dots
        assertTrue(snow.isNotEmpty() && snow.all { it.front })
        val stars = ThemePreviewScenes.forTheme(winter, c, forceNight = true).dots.filter { !it.front }
        assertTrue("a night card has stars in the sky pass", stars.isNotEmpty())
    }

    /**
     * R7 (v5.9G, inventory I-38): the card's trees, cars, decorations and clouds wear the
     * wallpaper's colours at the card's moment, and its palms the wallpaper's shade.
     *
     * R4 held the landscape and the building walls to the wallpaper's `dayBlend`, and everything
     * standing in front of them kept `colorDay1` / `colorDay2`: on the two midnight cards and on the
     * World & scene strip's night the trees, the cars, the snowmen, the gifts, the penguins, the
     * rabbits, the eggs and the pumpkins had their noon colours, and the palms their noon light,
     * while the wallpaper blends every one of them toward night. The expected values are asked of
     * the wallpaper's own functions -- `colorFor` for a tinted body, `nightShadeFor` for the palm --
     * over one instance of each of the category's two variants, so the card may wear either of the
     * user's two colours but no colour the wallpaper would not paint at that hour. The clouds have
     * one pair and are drawn only on a day card, so only Sunset's 19:00 (dayBlend 0.80) tells them
     * apart.
     *
     * Two things are left out, each because the wallpaper leaves it out too: the autumn canopy,
     * whose fall palette is the same at every hour on both sides, and the fixed art -- trunks, bare
     * branches, the fir, snow caps, ribbons, faces -- which does not dim (item 123).
     */
    private fun checkObjectColours(label: String, theme: SceneTheme, c: SceneCustomization, forceNight: Boolean?, seen: MutableMap<String, Int>? = null): List<String> {
        val night = forceNight ?: (c.horrorSkyEnabled || theme.hasFireworks)
        val scene = ThemePreviewScenes.forTheme(theme, c, forceNight)
        val d = ThemePreviewScenes.cardPhase(theme, night).dayBlend
        val out = mutableListOf<String>()
        fun hex(v: Int?) = if (v == null) "none" else "%08X".format(v)
        fun paints(type: SceneObjectType) = variantSpecs(type).map { c.colorFor(it, d) }.toSet()
        val palmShades = variantSpecs(SceneObjectType.PALM_TREE).map { c.nightShadeFor(it, d) }.toSet()
        val carPaints = VARIANT_CARS.map { c.colorFor(it, d) }.toSet()
        // `drawNeighbourhoodBuilding`'s glass: a house lit by the night alone, a business by the
        // night times its opening hours at the card's own hour ([BusinessHours], 1 with the toggle off).
        val openness = BusinessHours.opennessAt(c.businessHoursEnabled, c.businessOpenHour, c.businessCloseHour, ThemePreviewScenes.cardHour(theme, night))
        for (item in drawOrder(scene)) {
            val house = item.parts.any { familyOf(nameOf(it.resId))?.startsWith("HOUSE_") == true }
            for (part in item.parts.filter { it.resId in GLASS_MASKS }) {
                val expected = SceneObjectRenderer.windowGlassColor(if (house) 1f - d else (1f - d) * openness)
                if (part.tint != expected) {
                    out += "$label: R7 a ${if (house) "house's" else "business's"} window at x=%.0f is ${hex(part.tint)} on the card, the wallpaper lights it ${hex(expected)} at dayBlend %.3f, openness %.3f"
                        .format(item.x, d, openness)
                }
                seen?.let { val k = if (house) "HOUSE_GLASS" else if (openness < 1f && d < 1f) "CLOSED_BUSINESS_GLASS" else "BUSINESS_GLASS"; it[k] = (it[k] ?: 0) + 1 }
            }
            for (part in item.parts) {
                val name = nameOf(part.resId)
                val type = TINTED_BODIES[name]
                when {
                    name == "tree_canopy" && c.fallColorsEnabled -> Unit
                    type != null -> {
                        val allowed = paints(type)
                        if (part.tint !in allowed) {
                            out += "$label: R7 a $name at x=%.0f is ${hex(part.tint)} on the card, the wallpaper paints ${allowed.joinToString(" or ") { hex(it) }} at dayBlend %.3f"
                                .format(item.x, d)
                        }
                        seen?.let { it[type.name] = (it[type.name] ?: 0) + 1 }
                    }
                    name.startsWith("car_body_") -> {
                        if (part.tint !in carPaints) {
                            out += "$label: R7 a $name at x=%.0f is ${hex(part.tint)} on the card, the wallpaper paints ${carPaints.joinToString(" or ") { hex(it) }} at dayBlend %.3f"
                                .format(item.x, d)
                        }
                        seen?.let { it["CAR"] = (it["CAR"] ?: 0) + 1 }
                    }
                    name == "cloud_body" -> {
                        // `PaperRenderer.drawClouds`' pair on the moment's dayBlend, with the storm's
                        // dimming at zero: the card has no forecast.
                        val expected = SceneColour.blendArgb(c.clouds.colorNight, c.clouds.colorDay, d)
                        if (part.tint != expected) {
                            out += "$label: R7 a cloud is ${hex(part.tint)} on the card, the wallpaper paints ${hex(expected)} at dayBlend %.3f".format(d)
                        }
                        seen?.let { it["CLOUDS"] = (it["CLOUDS"] ?: 0) + 1 }
                    }
                    name.startsWith("palmtree_") -> {
                        if (part.tint != null || part.shade !in palmShades) {
                            out += "$label: R7 a $name at x=%.0f is shaded ${hex(part.shade)} (tint ${hex(part.tint)}) on the card, the wallpaper shades it ${palmShades.joinToString(" or ") { hex(it) }} at dayBlend %.3f"
                                .format(item.x, d)
                        }
                        seen?.let { it["PALM_TREE"] = (it["PALM_TREE"] ?: 0) + 1 }
                    }
                }
            }
        }
        return out
    }

    /** Every decoration a user can turn on, on, and every night colour R7 reads moved off its default. */
    private fun editedNightColours(): Pair<SceneTheme, SceneCustomization> {
        val beach = ThemeCatalog.byId("beach")
        val c = defaultCustomizationFor("beach")
        fun ObjectVariantConfig.edited(seed: Int) = copy(
            visible = true,
            colorNight1 = (0xFF000000.toInt() or (seed * 0x010203)),
            colorNight2 = (0xFF000000.toInt() or (seed * 0x030201)),
        )
        return beach to c.copy(
            trees = c.trees.edited(0x11), cars = c.cars.edited(0x12),
            snowmen = c.snowmen.edited(0x13), gifts = c.gifts.edited(0x14), penguins = c.penguins.edited(0x15),
            bunnies = c.bunnies.edited(0x16), easterEggs = c.easterEggs.edited(0x17), pumpkins = c.pumpkins.edited(0x18),
        )
    }

    @Test
    fun `R7 the card's trees, cars, decorations and clouds wear the wallpaper's colours at the card's moment`() {
        val fails = mutableListOf<String>()
        val seenAtNight = sortedMapOf<String, Int>()
        for ((theme, c) in builtIns()) {
            fails += checkObjectColours(theme.id, theme, c, null)
            fails += checkObjectColours("${theme.id} (forced night)", theme, c, true, seenAtNight)
            fails += checkObjectColours("${theme.id} (forced day)", theme, c, false)
        }
        for ((label, pair) in custom()) {
            fails += checkObjectColours(label, pair.first, pair.second, null)
            fails += checkObjectColours("$label (forced night)", pair.first, pair.second, true, seenAtNight)
        }
        val (beach, edited) = editedNightColours()
        fails += checkObjectColours("beach with every decoration out and its night colours edited (forced night)", beach, edited, true, seenAtNight)
        fails += checkObjectColours("beach with every decoration out and its night colours edited", beach, edited, null)
        val (autumn, landscape) = editedLandscape()
        fails += checkObjectColours("autumn with every landscape colour edited (forced night)", autumn, landscape, true)
        // Opening hours on, 09:00 to 18:00: the midnight and 19:00 cards find the businesses shut.
        for (id in listOf("autumn", "sunset", "new_year")) {
            val theme = ThemeCatalog.byId(id)
            val hours = defaultCustomizationFor(id).copy(businessHoursEnabled = true, businessOpenHour = 9f, businessCloseHour = 18f)
            fails += checkObjectColours("$id with opening hours 9-18", theme, hours, null, seenAtNight)
            fails += checkObjectColours("$id with opening hours 9-18 (forced night)", theme, hours, true, seenAtNight)
        }
        println("v5.9G R7: parts checked on night cards, by category: $seenAtNight")
        for ((theme, _) in builtIns()) {
            val night = defaultCustomizationFor(theme.id).let { it.horrorSkyEnabled || theme.hasFireworks }
            println("v5.9G R7: ${theme.id} card's moment dayBlend = %.4f".format(ThemePreviewScenes.cardPhase(theme, night).dayBlend))
        }
        report(fails)
        // Not vacuous: every category R7 reads was asked about at night at least once.
        for (category in listOf("TREE", "CAR", "PALM_TREE", "SNOWMAN", "GIFT", "PENGUIN", "BUNNY", "EASTER_EGG", "PUMPKIN", "HOUSE_GLASS", "CLOSED_BUSINESS_GLASS")) {
            assertTrue("R7 never met a $category on a night card: $seenAtNight", (seenAtNight[category] ?: 0) > 0)
        }
    }

    /**
     * R8 (v5.9G, on the maintainer's answer of 2026-09-29): the fixed art the wallpaper lays on a
     * tinted body is on the card too -- a pumpkin is carved where `halloweenEnabled` carves the
     * wallpaper's, and a penguin's belly is the wallpaper's white. The card drew smooth pumpkins on
     * Halloween under a carved moon, and a belly of `#F7FAFC` where the wallpaper paints `#F3F7FB`.
     */
    private fun checkFixedArt(label: String, theme: SceneTheme, c: SceneCustomization, forceNight: Boolean?, seen: MutableMap<String, Int>): List<String> {
        val scene = ThemePreviewScenes.forTheme(theme, c, forceNight)
        val out = mutableListOf<String>()
        for (item in drawOrder(scene)) {
            val names = item.parts.map { nameOf(it.resId) }
            if ("pumpkin_body" in names) {
                val carved = "pumpkin_face" in names
                if (carved != c.halloweenEnabled) {
                    out += "$label: R8 a pumpkin at x=%.0f is ${if (carved) "carved" else "smooth"} on the card and ${if (c.halloweenEnabled) "carved" else "smooth"} in the wallpaper".format(item.x)
                }
                seen[if (carved) "carved pumpkin" else "smooth pumpkin"] = (seen[if (carved) "carved pumpkin" else "smooth pumpkin"] ?: 0) + 1
            }
            for (part in item.parts.filter { nameOf(it.resId) == "penguin_belly" }) {
                if (part.tint != SceneObjectRenderer.PENGUIN_BELLY_COLOR) {
                    out += "$label: R8 a penguin's belly at x=%.0f is %08X on the card, %08X in the wallpaper".format(item.x, part.tint, SceneObjectRenderer.PENGUIN_BELLY_COLOR)
                }
                seen["penguin belly"] = (seen["penguin belly"] ?: 0) + 1
            }
        }
        return out
    }

    @Test
    fun `R8 a card's pumpkin is carved where the wallpaper carves it, and a penguin's belly is the wallpaper's white`() {
        val fails = mutableListOf<String>()
        val seen = sortedMapOf<String, Int>()
        for ((theme, c) in builtIns()) {
            for (forceNight in listOf(null, true)) fails += checkFixedArt("${theme.id} ($forceNight)", theme, c, forceNight, seen)
        }
        for ((label, pair) in custom()) fails += checkFixedArt(label, pair.first, pair.second, null, seen)
        val (beach, edited) = editedNightColours()
        fails += checkFixedArt("beach with every decoration out", beach, edited, null, seen)
        fails += checkFixedArt("beach with every decoration out, on Halloween", beach, edited.copy(halloweenEnabled = true), null, seen)
        report(fails)
        println("v5.9G R8: $seen")
        for (what in listOf("carved pumpkin", "smooth pumpkin", "penguin belly")) {
            assertTrue("R8 never met a $what: $seen", (seen[what] ?: 0) > 0)
        }
    }

    @Test
    fun `the wallpaper paints its trees, cars, decorations, clouds, pumpkins and penguins through the functions R7 and R8 hold the card to`() {
        // R7 compares the card with `colorFor`, `nightShadeFor` and the clouds' blend. That is only a comparison with
        // the wallpaper if the wallpaper paints with them, so its own draw functions are read.
        val objects = source("engine/SceneObjectRenderer.kt")
        fun body(name: String): String = objects.substring(objects.indexOf("private fun $name("))
            .let { it.substring(0, it.indexOf("\n    }\n") + 6) }
        for (name in listOf("drawTree", "drawSnowman", "drawGift", "drawPenguin", "drawBunny", "drawEasterEgg", "drawPumpkin")) {
            assertTrue("$name no longer paints customization.colorFor(r.spec, dayBlend)", body(name).contains("customization.colorFor(r.spec, dayBlend)"))
        }
        assertTrue("drawPalmTree no longer shades with nightShadeFor", body("drawPalmTree").contains("customization.nightShadeFor(r.spec, dayBlend)"))
        assertTrue("drawCar no longer paints a plain car with colorFor", body("drawCar").contains("customization.colorFor(c.spec, dayBlend)"))
        val building = body("drawNeighbourhoodBuilding")
        assertTrue("drawNeighbourhoodBuilding no longer lights a business by its opening hours",
            building.contains("WindowBuildingKind.HOUSE) night else night * businessOpenness") && building.contains("windowGlassColor(glassNight)"))
        // R8's two pieces of fixed art, read the same way.
        val pumpkin = body("drawPumpkin")
        assertTrue("drawPumpkin no longer carves on halloweenEnabled",
            pumpkin.contains("if (customization.halloweenEnabled)") && pumpkin.contains("R.drawable.pumpkin_face, -19f, -30f"))
        assertTrue("drawPenguin no longer paints the belly with penguinBellyColor", body("drawPenguin").contains("R.drawable.penguin_belly, -9f, -38f, penguinBellyColor"))
        assertTrue("penguinBellyColor is no longer PENGUIN_BELLY_COLOR", objects.contains("private val penguinBellyColor = PENGUIN_BELLY_COLOR"))
        // And the card's painter hands a palm's shade to the blitter, or R7's shade never reaches
        // the screen: the painter is Compose and has no JVM test of its own.
        assertTrue("ThemePreview's painter no longer passes a part's shade to the blitter",
            source("ui/ThemePreview.kt").contains("SpriteScale.SCENE_UNITS, part.alpha, part.shade)"))
        val renderer = source("engine/PaperRenderer.kt")
        val drawClouds = renderer.substring(renderer.indexOf("private fun drawClouds("))
            .let { it.substring(0, it.indexOf("\n    }\n") + 6) }
        assertTrue("drawClouds no longer blends its pair on the day phase", drawClouds.contains("blendColor(clouds.colorNight, clouds.colorDay, dayPhase.dayBlend)"))
    }

    @Test
    fun `the wallpaper paints its sky, road and mountains through the rules R4 holds the card to`() {
        // R4 compares the card with SkyGradient, roadColor/drawsRoad and MountainSilhouette. That is
        // only a comparison with the wallpaper if the wallpaper paints with them, so the renderers'
        // own source is read: a copy of the arithmetic reappearing there would make R4 a comparison
        // of the card with itself.
        val renderer = source("engine/PaperRenderer.kt")
        val drawSky = renderer.substring(renderer.indexOf("private fun drawSky("), renderer.indexOf("private fun drawStars("))
        for (call in listOf("SkyGradient.top(", "SkyGradient.bottom(", "SkyGradient.horrorTop(", "SkyGradient.horrorBottom(")) {
            assertTrue("drawSky no longer calls $call", drawSky.contains(call))
        }
        assertTrue("drawSky computes a blend of its own again", !drawSky.contains("blendColor("))
        val soft = renderer.substring(renderer.indexOf("private fun drawSoftMountain("))
            .let { it.substring(0, it.indexOf("\n    }\n") + 6) }
        assertTrue(soft.contains("MountainSilhouette.leftHalf(") && soft.contains("MountainSilhouette.rightHalf("))
        val objects = source("engine/SceneObjectRenderer.kt")
        val drawRoad = objects.substring(objects.indexOf("private fun drawRoad("))
            .let { it.substring(0, it.indexOf("\n    }\n") + 6) }
        assertTrue("drawRoad no longer asks drawsRoad", drawRoad.contains("drawsRoad(layout, customization)"))
        assertTrue("drawRoad no longer paints roadColor", drawRoad.contains("roadColor(dayBlend)"))
    }

    private fun source(path: String): String {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/$path")
                if (candidate.isFile) return candidate.readText()
            }
            dir = dir.parentFile
        }
        error("could not locate $path")
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

        /**
         * How far a car body's last row of ink may miss its floor: a twentieth of a card unit, which
         * is float rounding and not a pixel (the card is 316 px for 320 units on the BV6600). The
         * error R5 exists for was 1.75.
         */
        const val CARD_FLOOR_TOLERANCE_UNITS = 0.05f
        val WATER_FAMILIES = setOf("SAILBOAT", "DOLPHIN")

        /** R7: every glass mask a building of the neighbourhood can be dealt. */
        val GLASS_MASKS: Set<Int> = NeighbourhoodTable.FAMILIES.values.flatMap { it.slots }.flatMap { it.options }
            .flatMap { piece -> piece.parts.filter { it.role == PartRole.GLASS_MASK } }.map { it.res }.toSet()

        /** R7: the card's tinted bodies, and the category whose two colours each one wears. */
        val TINTED_BODIES = mapOf(
            "tree_canopy" to SceneObjectType.TREE,
            "snowman_body" to SceneObjectType.SNOWMAN,
            "gift_box" to SceneObjectType.GIFT,
            "penguin_body" to SceneObjectType.PENGUIN,
            "bunny_body" to SceneObjectType.BUNNY,
            "easteregg_shell" to SceneObjectType.EASTER_EGG,
            "pumpkin_body" to SceneObjectType.PUMPKIN,
        )

        private val PROBE_1 = 0xFF000001.toInt()
        private val PROBE_2 = 0xFF000002.toInt()

        private fun ObjectVariantConfig.probed() = copy(colorDay1 = PROBE_1, colorDay2 = PROBE_2)

        /** Every category R7 reads with two day colours no theme has, so `colorFor` at noon names the variant. */
        private val PROBE: SceneCustomization = SceneCustomization.DEFAULT.run {
            copy(
                trees = trees.probed(), cars = cars.probed(), snowmen = snowmen.probed(), gifts = gifts.probed(),
                penguins = penguins.probed(), bunnies = bunnies.probed(), easterEggs = easterEggs.probed(),
                pumpkins = pumpkins.probed(),
            )
        }

        private val VARIANT_SPECS = HashMap<SceneObjectType, List<StaticSceneObject>>()

        /**
         * One instance of each of [type]'s two variants, Color 1 first, told apart by the
         * wallpaper's own coin through `colorFor` -- not by a copy of the coin, which is private
         * to `SceneCustomization.kt` and should stay so.
         */
        fun variantSpecs(type: SceneObjectType): List<StaticSceneObject> = VARIANT_SPECS.getOrPut(type) {
            val byColour = HashMap<Int, StaticSceneObject>()
            var i = 0
            while (byColour.size < 2) {
                val spec = StaticSceneObject(type, depthFraction = 0.5f, tileFractionX = (i + 0.5f) / 1000f)
                byColour.putIfAbsent(PROBE.colorFor(spec, 1f), spec)
                check(++i < 1000) { "no position deals both variants of $type" }
            }
            listOf(byColour.getValue(PROBE_1), byColour.getValue(PROBE_2))
        }

        /** The same, for a car: the coin reads its lane and its start delay. */
        val VARIANT_CARS: List<CarObject> by lazy {
            val byColour = HashMap<Int, CarObject>()
            var i = 0
            while (byColour.size < 2) {
                val car = CarObject(laneYFraction = 0.5f, speedFraction = 0.1f, startDelaySeconds = i.toFloat(), color = 0)
                byColour.putIfAbsent(PROBE.colorFor(car, 1f), car)
                check(++i < 1000) { "no car deals both variants" }
            }
            listOf(byColour.getValue(PROBE_1), byColour.getValue(PROBE_2))
        }

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
