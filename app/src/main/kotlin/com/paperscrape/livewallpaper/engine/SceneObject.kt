package com.paperscrape.livewallpaper.engine

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
 * *tiled* hill-layer width (which is 2x the screen width, see [PaperRenderer.buildHillPath]).
 * This way the object scrolls perfectly in sync with the ground it's standing on.
 */
data class StaticSceneObject(
    val type: SceneObjectType,
    val layer: Int, // which hill layer (0 = far, 2 = near) it's anchored to
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

/** Default per-theme object layouts. Kept separate from [SceneTheme] so art direction (step 2:
 * distinct seasonal/festive scenes) can swap these independently of the color palette later. */
object SceneObjectCatalog {

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

    /** Returns null (rather than an empty layout) for unknown ids, so [layoutFor] can tell the
     * difference between "not a built-in id" and "a built-in id with an intentionally empty scene". */
    private fun builtinLayoutFor(themeId: String, accentColor: Int): SceneObjectLayout? = when (themeId) {
        "sunset" -> SceneObjectLayout(
            staticObjects = listOf(
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.22f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.42f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.60f, scale = 0.9f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.85f, scale = 0.75f),
                StaticSceneObject(SceneObjectType.TREE, layer = 2, tileFractionX = 0.08f),
                StaticSceneObject(SceneObjectType.TREE, layer = 1, tileFractionX = 0.55f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.DOG, layer = 2, tileFractionX = 0.38f),
                StaticSceneObject(SceneObjectType.DOG, layer = 2, tileFractionX = 0.68f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.95f, scale = 0.55f),
                StaticSceneObject(SceneObjectType.PARASOL, layer = 2, tileFractionX = 0.52f, scale = 0.7f),
            ),
            cars = listOf(
                CarObject(laneYFraction = 0.895f, speedFraction = 0.09f, startDelaySeconds = 0f, color = accentColor),
                CarObject(laneYFraction = 0.895f, speedFraction = 0.07f, startDelaySeconds = 5f, color = 0xFF6FA8DC.toInt(), reverse = true),
            ),
        )

        "autumn" -> SceneObjectLayout(
            staticObjects = listOf(
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.40f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.15f, scale = 0.8f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.58f, scale = 0.9f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.90f, scale = 0.7f),
                StaticSceneObject(SceneObjectType.TREE, layer = 2, tileFractionX = 0.12f),
                StaticSceneObject(SceneObjectType.TREE, layer = 2, tileFractionX = 0.62f),
                StaticSceneObject(SceneObjectType.TREE, layer = 1, tileFractionX = 0.78f, scale = 0.8f),
                StaticSceneObject(SceneObjectType.DOG, layer = 2, tileFractionX = 0.30f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.05f, scale = 0.5f),
                StaticSceneObject(SceneObjectType.PARASOL, layer = 2, tileFractionX = 0.48f, scale = 0.7f),
            ),
            cars = listOf(
                CarObject(laneYFraction = 0.9f, speedFraction = 0.08f, startDelaySeconds = 2f, color = accentColor),
            ),
        )

        "winter" -> SceneObjectLayout(
            staticObjects = listOf(
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.30f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.50f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.68f, scale = 1.0f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.92f, scale = 0.7f),
                StaticSceneObject(SceneObjectType.TREE, layer = 2, tileFractionX = 0.10f, scale = 0.9f),
                StaticSceneObject(SceneObjectType.TREE, layer = 2, tileFractionX = 0.85f, scale = 0.75f),
                StaticSceneObject(SceneObjectType.SNOWMAN, layer = 2, tileFractionX = 0.55f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.08f, scale = 0.55f),
                StaticSceneObject(SceneObjectType.PARASOL, layer = 2, tileFractionX = 0.40f, scale = 0.65f),
                StaticSceneObject(SceneObjectType.DOG, layer = 2, tileFractionX = 0.78f, scale = 0.85f),
            ),
            cars = listOf(
                CarObject(laneYFraction = 0.9f, speedFraction = 0.06f, startDelaySeconds = 1f, color = accentColor),
            ),
        )

        "desert" -> SceneObjectLayout(
            staticObjects = listOf(
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.10f, scale = 0.9f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.35f, scale = 0.8f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.75f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.TREE, layer = 2, tileFractionX = 0.20f, scale = 0.7f),
                StaticSceneObject(SceneObjectType.TREE, layer = 1, tileFractionX = 0.60f, scale = 0.6f),
                StaticSceneObject(SceneObjectType.DOG, layer = 2, tileFractionX = 0.45f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.90f, scale = 0.5f),
                StaticSceneObject(SceneObjectType.PARASOL, layer = 2, tileFractionX = 0.55f, scale = 0.7f),
            ),
            cars = listOf(
                CarObject(laneYFraction = 0.9f, speedFraction = 0.11f, startDelaySeconds = 0f, color = accentColor),
            ),
        )

        "christmas" -> SceneObjectLayout(
            staticObjects = listOf(
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.26f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.10f, scale = 0.8f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.42f, scale = 0.9f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.72f, scale = 0.75f),
                StaticSceneObject(SceneObjectType.SNOWMAN, layer = 2, tileFractionX = 0.12f),
                StaticSceneObject(SceneObjectType.SNOWMAN, layer = 2, tileFractionX = 0.82f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.GIFT, layer = 2, tileFractionX = 0.45f),
                StaticSceneObject(SceneObjectType.GIFT, layer = 2, tileFractionX = 0.52f, scale = 0.75f),
                StaticSceneObject(SceneObjectType.TREE, layer = 1, tileFractionX = 0.65f, scale = 0.9f),
                StaticSceneObject(SceneObjectType.DOG, layer = 2, tileFractionX = 0.30f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.95f, scale = 0.5f),
                StaticSceneObject(SceneObjectType.PARASOL, layer = 2, tileFractionX = 0.58f, scale = 0.65f),
            ),
            cars = listOf(
                CarObject(laneYFraction = 0.9f, speedFraction = 0.05f, startDelaySeconds = 3f, color = accentColor),
            ),
        )

        "new_year" -> SceneObjectLayout(
            staticObjects = listOf(
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.15f, scale = 1.1f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.35f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 0, tileFractionX = 0.60f, scale = 1.3f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 0, tileFractionX = 0.05f, scale = 0.9f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 0, tileFractionX = 0.48f, scale = 1.0f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.85f, scale = 0.95f),
                StaticSceneObject(SceneObjectType.BALLOON, layer = 2, tileFractionX = 0.25f),
                StaticSceneObject(SceneObjectType.BALLOON, layer = 2, tileFractionX = 0.72f, scale = 0.8f),
                StaticSceneObject(SceneObjectType.DOG, layer = 2, tileFractionX = 0.50f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.30f, scale = 0.75f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.78f, scale = 0.7f),
                StaticSceneObject(SceneObjectType.PARASOL, layer = 2, tileFractionX = 0.62f, scale = 0.65f),
                StaticSceneObject(SceneObjectType.TREE, layer = 1, tileFractionX = 0.10f, scale = 0.6f),
            ),
            cars = listOf(
                CarObject(laneYFraction = 0.9f, speedFraction = 0.10f, startDelaySeconds = 1.5f, color = accentColor),
            ),
        )

        "beach" -> SceneObjectLayout(
            staticObjects = listOf(
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.05f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.92f, scale = 0.8f),
                StaticSceneObject(SceneObjectType.PALM_TREE, layer = 1, tileFractionX = 0.15f),
                StaticSceneObject(SceneObjectType.PALM_TREE, layer = 2, tileFractionX = 0.70f, scale = 0.9f),
                StaticSceneObject(SceneObjectType.PARASOL, layer = 2, tileFractionX = 0.35f),
                StaticSceneObject(SceneObjectType.PARASOL, layer = 2, tileFractionX = 0.50f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.DOG, layer = 2, tileFractionX = 0.85f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.98f, scale = 0.5f),
            ),
            cars = listOf(
                CarObject(laneYFraction = 0.9f, speedFraction = 0.07f, startDelaySeconds = 1f, color = accentColor),
            ),
        )

        "city" -> SceneObjectLayout(
            staticObjects = listOf(
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 0, tileFractionX = 0.10f, scale = 1.2f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 0, tileFractionX = 0.30f, scale = 0.9f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.55f, scale = 1.1f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.80f, scale = 0.8f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 0, tileFractionX = 0.20f, scale = 1.0f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.42f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 0, tileFractionX = 0.68f, scale = 1.15f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.90f, scale = 0.9f),
                StaticSceneObject(SceneObjectType.DOG, layer = 2, tileFractionX = 0.40f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.06f, scale = 0.75f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.96f, scale = 0.7f),
                StaticSceneObject(SceneObjectType.PARASOL, layer = 2, tileFractionX = 0.50f, scale = 0.6f),
                StaticSceneObject(SceneObjectType.TREE, layer = 2, tileFractionX = 0.60f, scale = 0.55f),
            ),
            cars = listOf(
                CarObject(laneYFraction = 0.895f, speedFraction = 0.14f, startDelaySeconds = 0f, color = accentColor),
                CarObject(laneYFraction = 0.895f, speedFraction = 0.12f, startDelaySeconds = 2.5f, color = 0xFFDCDCDC.toInt(), reverse = true),
                CarObject(laneYFraction = 0.895f, speedFraction = 0.16f, startDelaySeconds = 4f, color = 0xFFF3D34A.toInt()),
            ),
        )

        "tundra" -> SceneObjectLayout(
            staticObjects = listOf(
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.65f, scale = 0.8f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.15f, scale = 0.7f),
                StaticSceneObject(SceneObjectType.SNOWMAN, layer = 2, tileFractionX = 0.20f),
                StaticSceneObject(SceneObjectType.PENGUIN, layer = 2, tileFractionX = 0.45f),
                StaticSceneObject(SceneObjectType.PENGUIN, layer = 2, tileFractionX = 0.55f, scale = 0.8f),
                StaticSceneObject(SceneObjectType.TREE, layer = 1, tileFractionX = 0.75f, scale = 0.6f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.92f, scale = 0.5f),
                StaticSceneObject(SceneObjectType.PARASOL, layer = 2, tileFractionX = 0.35f, scale = 0.6f),
                StaticSceneObject(SceneObjectType.DOG, layer = 2, tileFractionX = 0.85f, scale = 0.75f),
            ),
            cars = listOf(
                CarObject(laneYFraction = 0.9f, speedFraction = 0.06f, startDelaySeconds = 2f, color = accentColor),
            ),
        )

        "easter" -> SceneObjectLayout(
            staticObjects = listOf(
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.24f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 2, tileFractionX = 0.48f, scale = 0.8f),
                StaticSceneObject(SceneObjectType.HOUSE, layer = 1, tileFractionX = 0.90f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.TREE, layer = 1, tileFractionX = 0.62f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.EASTER_EGG, layer = 2, tileFractionX = 0.15f),
                StaticSceneObject(SceneObjectType.EASTER_EGG, layer = 2, tileFractionX = 0.35f, scale = 0.75f),
                StaticSceneObject(SceneObjectType.EASTER_EGG, layer = 2, tileFractionX = 0.58f, scale = 0.9f),
                StaticSceneObject(SceneObjectType.BUNNY, layer = 2, tileFractionX = 0.45f),
                StaticSceneObject(SceneObjectType.BUNNY, layer = 2, tileFractionX = 0.80f, scale = 0.85f),
                StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 1, tileFractionX = 0.05f, scale = 0.5f),
                StaticSceneObject(SceneObjectType.PARASOL, layer = 2, tileFractionX = 0.68f, scale = 0.65f),
                StaticSceneObject(SceneObjectType.DOG, layer = 2, tileFractionX = 0.55f, scale = 0.8f),
            ),
            cars = listOf(
                CarObject(laneYFraction = 0.9f, speedFraction = 0.06f, startDelaySeconds = 0.5f, color = accentColor),
            ),
        )

        else -> null
    }
}
