package com.paperscrape.livewallpaper.engine

import kotlin.random.Random

/** The kind of object placed in the scene. Drives both drawing shape and reaction behavior. */
enum class SceneObjectType {
    CAR, DOG, HOUSE, TREE,
    // Seasonal / festive additions (step 2)
    SNOWMAN, GIFT, PALM_TREE, PARASOL, SKYSCRAPER, PENGUIN, BALLOON,
    // Easter theme additions (auto-theme-by-date feature)
    EASTER_EGG, BUNNY,
}

/**
 * A stationary (but animated-in-place) object anchored to one of the parallax hill layers,
 * e.g. a dog sitting on the near hill, a house on the mid hill.
 *
 * [tileFractionX] is the object's horizontal position expressed as a fraction (0..1) of the
 * object layer's own tiling width (screen width -- see [PaperRenderer.drawHillLayers] for why
 * this is deliberately narrower than the hill silhouette's own tile).
 */
data class StaticSceneObject(
    val type: SceneObjectType,
    val layer: Int, // which placement row, 0..8 (0 = farthest, 8 = nearest) it's anchored to
    val tileFractionX: Float,
    val scale: Float = 1f,
    val tappable: Boolean = type in TAPPABLE_TYPES,
)

private val TAPPABLE_TYPES = setOf(
    SceneObjectType.DOG,
    SceneObjectType.PENGUIN,
    SceneObjectType.GIFT,
    SceneObjectType.TREE,
    SceneObjectType.SNOWMAN,
    SceneObjectType.PALM_TREE,
    SceneObjectType.BUNNY,
)

/**
 * A car that drives continuously across the screen in its own independent loop,
 * unaffected by home-screen parallax scrolling (it's "alive" on the road, not part of the
 * static background) — this matches the classic "watch the cars drive by" wallpaper feel.
 */
data class CarObject(
    val laneYFraction: Float, // vertical position as a fraction of screen height
    val speedFraction: Float, // screen-widths per second
    val startDelaySeconds: Float,
    val color: Int,
    val reverse: Boolean = false,
)

/** The full set of interactive/decorative objects that belong to one theme's scene. */
data class SceneObjectLayout(
    val staticObjects: List<StaticSceneObject>,
    val cars: List<CarObject>,
)

/**
 * Default per-theme object layouts.
 *
 * Design principle: every theme offers the *same* maximum customization range for the 6
 * user-editable categories (houses, buildings, dogs, cars, umbrellas, trees) -- exactly
 * [CANDIDATES_PER_CATEGORY] candidate slots each, generated uniformly rather than hand-authored
 * per theme. Whether a theme ends up looking like a quiet village or a dense city is entirely
 * up to the user's density sliders in "Scene Objects", never baked into the theme itself. Only
 * the non-editable "flavor" decorations (snowmen, gifts, balloons, penguins, bunnies, Easter
 * eggs) stay theme-specific, since those aren't part of the customizable system and are what
 * actually gives each theme its distinct seasonal identity.
 */
object SceneObjectCatalog {

    /** Same for every theme and every one of the 6 customizable categories -- see the class doc. */
    const val CANDIDATES_PER_CATEGORY = 10

    fun layoutFor(themeId: String, accentColor: Int): SceneObjectLayout {
        if (RandomSceneGenerator.isRandomThemeId(themeId)) {
            return RandomSceneGenerator.generateLayout(themeId, accentColor)
        }
        CustomThemeRegistry.overrideLayoutFor(themeId)?.let { return it }
        val builtinLayout = builtinLayoutFor(themeId, accentColor)
        if (builtinLayout != null) return builtinLayout
        CustomThemeRegistry.customEntry(themeId)?.let { return it.layout }
        return SceneObjectLayout(staticObjects = emptyList(), cars = emptyList())
    }

    // --- Uniform candidate generation for the 6 customizable categories --------------------

    /**
     * Generates [CANDIDATES_PER_CATEGORY] candidate slots for one customizable category, spread
     * across the screen with a little position jitter (so they don't look like a rigid grid) and
     * some scale variety within their assigned layers. Deterministic per (seed) so the same
     * theme always generates the same candidate layout -- never `Random()` without a fixed seed.
     */
    private fun generateStaticCandidates(
        type: SceneObjectType,
        seed: Int,
        layers: IntArray,
        baseScale: Float,
        scaleJitter: Float,
    ): List<StaticSceneObject> {
        val rnd = Random(seed)
        return (0 until CANDIDATES_PER_CATEGORY).map { i ->
            val slot = (i + 0.5f) / CANDIDATES_PER_CATEGORY
            val jitter = (rnd.nextFloat() - 0.5f) * (1f / CANDIDATES_PER_CATEGORY) * 0.7f
            val tileFractionX = (slot + jitter).coerceIn(0.015f, 0.985f)
            val layer = layers[rnd.nextInt(layers.size)]
            val scale = (baseScale - scaleJitter / 2f + rnd.nextFloat() * scaleJitter).coerceAtLeast(0.3f)
            StaticSceneObject(type, layer = layer, tileFractionX = tileFractionX, scale = scale)
        }
    }

    /** Same idea as [generateStaticCandidates] but for cars, which aren't hill-layer-anchored.
     * Lane position is kept in a tight band so all cars visually share one road regardless of
     * how many end up enabled. */
    private fun generateCarCandidates(seed: Int, accentColor: Int): List<CarObject> {
        val rnd = Random(seed)
        return (0 until CANDIDATES_PER_CATEGORY).map { i ->
            CarObject(
                laneYFraction = 0.895f + rnd.nextFloat() * 0.015f,
                speedFraction = 0.05f + rnd.nextFloat() * 0.09f,
                startDelaySeconds = i * 1.8f + rnd.nextFloat() * 1.2f,
                color = accentColor, // live-recolored by SceneCustomization anyway
                reverse = rnd.nextBoolean(),
            )
        }
    }

    /** The uniform 6-category candidate set shared by every theme. [treeType] lets themes like
     * Beach use palm trees instead of plain trees for their "trees" category slots while still
     * sharing the same density/color customization (both map to the same category, see
     * [SceneCustomization]'s `configFor`).
     *
     * Row assignments (0=farthest, 8=nearest, 9 total -- see [PaperRenderer.ROWS_PER_LAYER]):
     * buildings and houses get their own fully disjoint bands -- SKYSCRAPER exclusively on rows
     * 0-2 (the whole farthest hill layer, reading as a city skyline sitting behind the
     * neighborhood) and HOUSE exclusively on rows 4-6, with row 3 deliberately left unassigned
     * as a buffer so the two categories' candidate clouds never share a row (sharing a row was
     * the actual cause of houses and buildings visually clipping through each other -- two
     * independently-randomized categories at the identical depth/Y with no consistent
     * front/behind relationship). Rows 0-2 no longer read as "floating", since object rows are
     * now confined to each layer's guaranteed-solid band -- see
     * [PaperRenderer.HILL_SAFE_ROW_MIN].
     */
    private fun uniformCandidates(
        themeId: String,
        accentColor: Int,
        treeType: SceneObjectType = SceneObjectType.TREE,
    ): SceneObjectLayout {
        val seed = themeId.hashCode()
        val staticObjects =
            generateStaticCandidates(SceneObjectType.SKYSCRAPER, seed + 2, intArrayOf(0, 1, 2), baseScale = 0.9f, scaleJitter = 0.5f) +
                generateStaticCandidates(SceneObjectType.HOUSE, seed + 1, intArrayOf(4, 5, 6), baseScale = 0.85f, scaleJitter = 0.35f) +
                generateStaticCandidates(treeType, seed + 5, intArrayOf(4, 5, 6, 7, 8), baseScale = 0.85f, scaleJitter = 0.35f) +
                generateStaticCandidates(SceneObjectType.PARASOL, seed + 4, intArrayOf(7, 8), baseScale = 0.8f, scaleJitter = 0.25f) +
                generateStaticCandidates(SceneObjectType.DOG, seed + 3, intArrayOf(8), baseScale = 0.85f, scaleJitter = 0.25f)
        val cars = generateCarCandidates(seed + 6, accentColor)
        return SceneObjectLayout(staticObjects = staticObjects, cars = cars)
    }

    private operator fun SceneObjectLayout.plus(flavor: List<StaticSceneObject>): SceneObjectLayout =
        copy(staticObjects = staticObjects + flavor)

    /** Returns null (rather than an empty layout) for unknown ids, so [layoutFor] can tell the
     * difference between "not a built-in id" and "a built-in id with an intentionally empty scene". */
    private fun builtinLayoutFor(themeId: String, accentColor: Int): SceneObjectLayout? = when (themeId) {
        "sunset" -> uniformCandidates(themeId, accentColor)

        "autumn" -> uniformCandidates(themeId, accentColor)

        "winter" -> uniformCandidates(themeId, accentColor) + listOf(
            StaticSceneObject(SceneObjectType.SNOWMAN, layer = 8, tileFractionX = 0.55f),
        )

        "desert" -> uniformCandidates(themeId, accentColor)

        "christmas" -> uniformCandidates(themeId, accentColor) + listOf(
            StaticSceneObject(SceneObjectType.SNOWMAN, layer = 8, tileFractionX = 0.12f),
            StaticSceneObject(SceneObjectType.SNOWMAN, layer = 8, tileFractionX = 0.82f, scale = 0.85f),
            StaticSceneObject(SceneObjectType.GIFT, layer = 8, tileFractionX = 0.45f),
            StaticSceneObject(SceneObjectType.GIFT, layer = 8, tileFractionX = 0.52f, scale = 0.75f),
        )

        "new_year" -> uniformCandidates(themeId, accentColor) + listOf(
            StaticSceneObject(SceneObjectType.BALLOON, layer = 8, tileFractionX = 0.25f),
            StaticSceneObject(SceneObjectType.BALLOON, layer = 8, tileFractionX = 0.72f, scale = 0.8f),
        )

        "beach" -> uniformCandidates(themeId, accentColor, treeType = SceneObjectType.PALM_TREE)

        "city" -> uniformCandidates(themeId, accentColor)

        "tundra" -> uniformCandidates(themeId, accentColor) + listOf(
            StaticSceneObject(SceneObjectType.SNOWMAN, layer = 8, tileFractionX = 0.20f),
            StaticSceneObject(SceneObjectType.PENGUIN, layer = 8, tileFractionX = 0.45f),
            StaticSceneObject(SceneObjectType.PENGUIN, layer = 8, tileFractionX = 0.55f, scale = 0.8f),
        )

        "easter" -> uniformCandidates(themeId, accentColor) + listOf(
            StaticSceneObject(SceneObjectType.EASTER_EGG, layer = 8, tileFractionX = 0.15f),
            StaticSceneObject(SceneObjectType.EASTER_EGG, layer = 8, tileFractionX = 0.35f, scale = 0.75f),
            StaticSceneObject(SceneObjectType.EASTER_EGG, layer = 8, tileFractionX = 0.58f, scale = 0.9f),
            StaticSceneObject(SceneObjectType.BUNNY, layer = 8, tileFractionX = 0.45f),
            StaticSceneObject(SceneObjectType.BUNNY, layer = 8, tileFractionX = 0.80f, scale = 0.85f),
        )

        else -> null
    }
}
