package com.paperscrape.livewallpaper.engine

/**
 * Reusable per-category settings: visibility, density, and 2 color variants (each with a
 * day/night version) -- the same customization "shape" applied uniformly to every object
 * category below rather than duplicated per type.
 *
 * Colors: each individual instance of the category is deterministically assigned variant 1 or 2
 * (stable, based on its position -- see [SceneObjectRenderer]'s `variantIndexFor`), and blends
 * between that variant's day and night color exactly like the rest of the scene does.
 */
data class ObjectVariantConfig(
    val visible: Boolean,
    /** 0f..1f — fraction of a theme's candidate slots for this category that actually render. */
    val density: Float,
    val colorDay1: Int,
    val colorNight1: Int,
    val colorDay2: Int,
    val colorNight2: Int,
)

/**
 * Global (theme-independent) rendering settings for every customizable object category, editable
 * from the "Scene Objects" screen. These apply on top of whichever theme/custom theme is active:
 * a theme's [SceneObjectLayout] defines *candidate* slots (see [SceneObjectCatalog]), and this
 * config decides how many of them actually show up and what colors they use.
 */
data class SceneCustomization(
    val houses: ObjectVariantConfig,
    val buildings: ObjectVariantConfig,
    val dogs: ObjectVariantConfig,
    val cars: ObjectVariantConfig,
    val parasols: ObjectVariantConfig,
    val trees: ObjectVariantConfig,
) {
    companion object {
        val DEFAULT = SceneCustomization(
            houses = ObjectVariantConfig(
                visible = true,
                density = 1f,
                // Matches the wall color PaperScrape always used before this became configurable.
                colorDay1 = 0xFFF3E6D0.toInt(),
                colorNight1 = 0xFF6B5F52.toInt(),
                colorDay2 = 0xFFE9D6C7.toInt(),
                colorNight2 = 0xFF5C4A45.toInt(),
            ),
            buildings = ObjectVariantConfig(
                visible = true,
                density = 1f,
                // Matches the wall color PaperScrape always used before this became configurable.
                colorDay1 = 0xFF454B57.toInt(),
                colorNight1 = 0xFF262A31.toInt(),
                colorDay2 = 0xFF5C6A78.toInt(),
                colorNight2 = 0xFF303842.toInt(),
            ),
            dogs = ObjectVariantConfig(
                visible = true,
                density = 1f,
                // Matches the fixed color PaperScrape always used before this became configurable.
                colorDay1 = 0xFFC9834A.toInt(),
                colorNight1 = 0xFF7A5230.toInt(),
                colorDay2 = 0xFF8C8C94.toInt(),
                colorNight2 = 0xFF4A4A50.toInt(),
            ),
            cars = ObjectVariantConfig(
                visible = true,
                density = 1f,
                colorDay1 = 0xFFF2A65A.toInt(),
                colorNight1 = 0xFFB5651D.toInt(),
                colorDay2 = 0xFF6FA8DC.toInt(),
                colorNight2 = 0xFF3D6B94.toInt(),
            ),
            parasols = ObjectVariantConfig(
                visible = true,
                density = 1f,
                // Matches the fixed colors PaperScrape always used before this became configurable.
                colorDay1 = 0xFFFF7043.toInt(),
                colorNight1 = 0xFFB5502E.toInt(),
                colorDay2 = 0xFFF7FAFC.toInt(),
                colorNight2 = 0xFFAEB4B8.toInt(),
            ),
            trees = ObjectVariantConfig(
                visible = true,
                density = 1f,
                // Matches the fixed foliage color PaperScrape always used before this became configurable.
                colorDay1 = 0xFF8AA25C.toInt(),
                colorNight1 = 0xFF3F4A2A.toInt(),
                colorDay2 = 0xFF3F9E6B.toInt(),
                colorNight2 = 0xFF244A34.toInt(),
            ),
        )
    }
}

/**
 * Stable per-instance fraction in [0, 1), derived purely from an object's fixed position (never
 * from Random()) so the same object always gets the same value -- both across frames (no
 * flicker) and across [SceneObjectRenderer] rebuilds (no reshuffling when e.g. a density slider
 * moves, only slots crossing the new threshold change).
 */
private fun stableFraction(spec: StaticSceneObject, salt: Float): Float {
    val raw = spec.tileFractionX * 7919f + spec.layer * 131f + salt
    return raw - kotlin.math.floor(raw)
}

private fun stableFraction(spec: CarObject, salt: Float): Float {
    val raw = spec.laneYFraction * 7919f + spec.startDelaySeconds * 131f + salt
    return raw - kotlin.math.floor(raw)
}

/** Which category config governs a given object type, or null for types with no customization. */
private fun SceneCustomization.configFor(type: SceneObjectType): ObjectVariantConfig? = when (type) {
    SceneObjectType.HOUSE -> houses
    SceneObjectType.SKYSCRAPER -> buildings
    SceneObjectType.DOG -> dogs
    SceneObjectType.PARASOL -> parasols
    SceneObjectType.TREE, SceneObjectType.PALM_TREE -> trees
    else -> null
}

/** Whether this candidate slot should actually render, given the current config. Types with no
 * customization (snowmen, gifts, penguins, etc.) are always kept. */
fun SceneCustomization.keepCandidate(spec: StaticSceneObject): Boolean {
    val config = configFor(spec.type) ?: return true
    return config.visible && stableFraction(spec, salt = 0f) < config.density
}

fun SceneCustomization.keepCar(spec: CarObject): Boolean =
    cars.visible && stableFraction(spec, salt = 0f) < cars.density

/** Which of the 2 color variants (0 or 1) this instance uses. Salted differently from
 * [keepCandidate]'s threshold so density thinning and color-variant assignment don't correlate. */
private fun variantIndexFor(spec: StaticSceneObject): Int =
    if (stableFraction(spec, salt = 17.3f) < 0.5f) 0 else 1

private fun variantIndexFor(spec: CarObject): Int =
    if (stableFraction(spec, salt = 17.3f) < 0.5f) 0 else 1

private fun blend(config: ObjectVariantConfig, variant: Int, dayBlend: Float): Int {
    val day = if (variant == 0) config.colorDay1 else config.colorDay2
    val night = if (variant == 0) config.colorNight1 else config.colorNight2
    return androidx.core.graphics.ColorUtils.blendARGB(night, day, dayBlend.coerceIn(0f, 1f))
}

fun SceneCustomization.colorFor(spec: StaticSceneObject, dayBlend: Float): Int {
    val config = configFor(spec.type) ?: return 0xFFFFFFFF.toInt()
    return blend(config, variantIndexFor(spec), dayBlend)
}

fun SceneCustomization.colorFor(spec: CarObject, dayBlend: Float): Int = blend(cars, variantIndexFor(spec), dayBlend)

/** The parasol's 5 wedges alternate between the two configured colors (not a per-instance
 * variant pick like other categories, since a single parasol shows both colors as stripes). */
fun SceneCustomization.parasolStripeColor(wedgeIndex: Int, dayBlend: Float): Int =
    blend(parasols, wedgeIndex % 2, dayBlend)
