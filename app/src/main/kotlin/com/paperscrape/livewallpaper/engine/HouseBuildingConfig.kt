package com.paperscrape.livewallpaper.engine

/**
 * Global (theme-independent) rendering settings for houses and commercial buildings, editable
 * from the "Houses & Buildings" screen. These apply on top of whichever theme/custom theme is
 * active: a theme's [SceneObjectLayout] defines *candidate* house/building slots (see
 * [SceneObjectCatalog]), and this config decides how many of them actually show up and what
 * colors they use.
 *
 * Colors are given as two variants per type per time-of-day (matching the classic "day color 1 /
 * day color 2 / night color 1 / night color 2" pattern): each individual house or building
 * instance is deterministically assigned variant 1 or 2 (stable, based on its position — see
 * [SceneObjectRenderer]'s `variantIndexFor`), and blends between that variant's day and night
 * color exactly like the rest of the scene does.
 */
data class HouseBuildingConfig(
    val showHouses: Boolean,
    val showBuildings: Boolean,
    /** 0f..1f — fraction of a theme's candidate house/building slots that actually render. */
    val density: Float,

    val houseColorDay1: Int,
    val houseColorNight1: Int,
    val houseColorDay2: Int,
    val houseColorNight2: Int,

    val buildingColorDay1: Int,
    val buildingColorNight1: Int,
    val buildingColorDay2: Int,
    val buildingColorNight2: Int,
) {
    companion object {
        val DEFAULT = HouseBuildingConfig(
            showHouses = true,
            showBuildings = true,
            density = 1f,
            // Matches the wall color PaperScrape always used before this became configurable.
            houseColorDay1 = 0xFFF3E6D0.toInt(),
            houseColorNight1 = 0xFF6B5F52.toInt(),
            houseColorDay2 = 0xFFE9D6C7.toInt(),
            houseColorNight2 = 0xFF5C4A45.toInt(),
            // Matches the wall color PaperScrape always used before this became configurable.
            buildingColorDay1 = 0xFF454B57.toInt(),
            buildingColorNight1 = 0xFF262A31.toInt(),
            buildingColorDay2 = 0xFF5C6A78.toInt(),
            buildingColorNight2 = 0xFF303842.toInt(),
        )
    }
}

/**
 * Stable per-instance fraction in [0, 1), derived purely from an object's fixed position (never
 * from Random()) so the same object always gets the same value -- both across frames (no
 * flicker) and across [SceneObjectRenderer] rebuilds (no reshuffling when e.g. the density
 * slider moves, only slots crossing the new threshold change).
 */
private fun stableFraction(spec: StaticSceneObject, salt: Float): Float {
    val raw = spec.tileFractionX * 7919f + spec.layer * 131f + salt
    return raw - kotlin.math.floor(raw)
}

/** Whether this candidate house/building slot should actually render, given the current config. */
fun HouseBuildingConfig.keepCandidate(spec: StaticSceneObject): Boolean = when (spec.type) {
    SceneObjectType.HOUSE -> showHouses && stableFraction(spec, salt = 0f) < density
    SceneObjectType.SKYSCRAPER -> showBuildings && stableFraction(spec, salt = 0f) < density
    else -> true
}

/** Which of the 2 color variants (0 or 1) this instance uses. Salted differently from
 * [keepCandidate]'s threshold so density thinning and color-variant assignment don't correlate
 * (which would otherwise make every slot right at the density cutoff share the same variant). */
private fun HouseBuildingConfig.variantIndexFor(spec: StaticSceneObject): Int =
    if (stableFraction(spec, salt = 17.3f) < 0.5f) 0 else 1

fun HouseBuildingConfig.houseColorFor(spec: StaticSceneObject, dayBlend: Float): Int {
    val day = if (variantIndexFor(spec) == 0) houseColorDay1 else houseColorDay2
    val night = if (variantIndexFor(spec) == 0) houseColorNight1 else houseColorNight2
    return androidx.core.graphics.ColorUtils.blendARGB(night, day, dayBlend.coerceIn(0f, 1f))
}

fun HouseBuildingConfig.buildingColorFor(spec: StaticSceneObject, dayBlend: Float): Int {
    val day = if (variantIndexFor(spec) == 0) buildingColorDay1 else buildingColorDay2
    val night = if (variantIndexFor(spec) == 0) buildingColorNight1 else buildingColorNight2
    return androidx.core.graphics.ColorUtils.blendARGB(night, day, dayBlend.coerceIn(0f, 1f))
}
