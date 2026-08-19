package com.paperscrape.livewallpaper.engine

/**
 * A color palette that fully describes one "paper cutout" scene.
 *
 * All hex colors are ARGB ints, ready to feed to [android.graphics.Paint.setColor].
 * Sky colors are given for the four phases of the day; the renderer interpolates
 * smoothly between them based on the current sun elevation (see [SunPositionCalculator]).
 */
data class SceneTheme(
    val id: String,
    val displayName: String,

    // Sky gradient top->bottom colors for each phase of the day.
    val skyNight: IntArray,
    val skyDawn: IntArray,
    val skyDay: IntArray,
    val skyDusk: IntArray,

    // Layered hill / silhouette colors, ordered from farthest (index 0) to nearest.
    val hillColorsDay: IntArray,
    val hillColorsNight: IntArray,

    val sunColor: Int,
    val moonColor: Int,
    val starColor: Int,
    val accentColor: Int, // used for touch-spawned paper birds / leaves

    /** When true, PaperRenderer periodically launches a firework burst at night. */
    val hasFireworks: Boolean = false,

    /** When true, Santa's sleigh periodically flies across the sky (Christmas theme only). */
    val hasSantaSleigh: Boolean = false,
) {
    override fun equals(other: Any?): Boolean = other is SceneTheme && other.id == id
    override fun hashCode(): Int = id.hashCode()
}

/** Built-in themes. Add new ones here — the settings screen picks them up automatically. */
object ThemeCatalog {

    val SUNSET = SceneTheme(
        id = "sunset",
        displayName = "Sunset",
        skyNight = intArrayOf(0xFF0B0E2E.toInt(), 0xFF1B1B3A.toInt()),
        skyDawn = intArrayOf(0xFFFF9E7D.toInt(), 0xFFFFD59E.toInt()),
        skyDay = intArrayOf(0xFF6EC6FF.toInt(), 0xFFCDEFFF.toInt()),
        skyDusk = intArrayOf(0xFFFF7A59.toInt(), 0xFFFFC98B.toInt()),
        hillColorsDay = intArrayOf(0xFFF2A65A.toInt(), 0xFFD9812F.toInt(), 0xFFB5651D.toInt()),
        hillColorsNight = intArrayOf(0xFF2E2A55.toInt(), 0xFF211E44.toInt(), 0xFF161533.toInt()),
        sunColor = 0xFFFFE3B0.toInt(),
        moonColor = 0xFFEFEFFF.toInt(),
        starColor = 0xFFFFFFFF.toInt(),
        accentColor = 0xFFFF8552.toInt(),
    )

    val AUTUMN = SceneTheme(
        id = "autumn",
        displayName = "Autumn",
        skyNight = intArrayOf(0xFF15181F.toInt(), 0xFF23222E.toInt()),
        skyDawn = intArrayOf(0xFFE5A45A.toInt(), 0xFFF6D9A8.toInt()),
        skyDay = intArrayOf(0xFF9FC2D9.toInt(), 0xFFE6E2C8.toInt()),
        skyDusk = intArrayOf(0xFFB5651D.toInt(), 0xFFE8B15C.toInt()),
        hillColorsDay = intArrayOf(0xFFC97B3D.toInt(), 0xFFA25B2A.toInt(), 0xFF7A4322.toInt()),
        hillColorsNight = intArrayOf(0xFF3A3226.toInt(), 0xFF2C2620.toInt(), 0xFF1E1A16.toInt()),
        sunColor = 0xFFFFDCA0.toInt(),
        moonColor = 0xFFE9E4D8.toInt(),
        starColor = 0xFFFFF6E0.toInt(),
        accentColor = 0xFFE0703A.toInt(),
    )

    val WINTER = SceneTheme(
        id = "winter",
        displayName = "Winter",
        skyNight = intArrayOf(0xFF0B1A2A.toInt(), 0xFF15263B.toInt()),
        skyDawn = intArrayOf(0xFFA9C7E0.toInt(), 0xFFE7EEF5.toInt()),
        skyDay = intArrayOf(0xFF8FC3E8.toInt(), 0xFFEAF6FF.toInt()),
        skyDusk = intArrayOf(0xFF6E8FB0.toInt(), 0xFFCBD9E8.toInt()),
        hillColorsDay = intArrayOf(0xFFE9F1F7.toInt(), 0xFFC9D9E6.toInt(), 0xFFA7BFD3.toInt()),
        hillColorsNight = intArrayOf(0xFF27364A.toInt(), 0xFF1D2A3B.toInt(), 0xFF14202E.toInt()),
        sunColor = 0xFFFFFDF2.toInt(),
        moonColor = 0xFFF3F8FF.toInt(),
        starColor = 0xFFFFFFFF.toInt(),
        accentColor = 0xFF6FA8DC.toInt(),
    )

    val DESERT = SceneTheme(
        id = "desert",
        displayName = "Desert",
        skyNight = intArrayOf(0xFF1A1230.toInt(), 0xFF2B1F45.toInt()),
        skyDawn = intArrayOf(0xFFF3B562.toInt(), 0xFFFCE0A8.toInt()),
        skyDay = intArrayOf(0xFF8FD6E8.toInt(), 0xFFFDF3D6.toInt()),
        skyDusk = intArrayOf(0xFFD9622F.toInt(), 0xFFF6B25C.toInt()),
        hillColorsDay = intArrayOf(0xFFE8B15C.toInt(), 0xFFD68F3D.toInt(), 0xFFB5651D.toInt()),
        hillColorsNight = intArrayOf(0xFF3E2E4A.toInt(), 0xFF2D2138.toInt(), 0xFF1E1626.toInt()),
        sunColor = 0xFFFFF0C4.toInt(),
        moonColor = 0xFFEFE6FF.toInt(),
        starColor = 0xFFFFF6E0.toInt(),
        accentColor = 0xFFE0A23D.toInt(),
    )

    val CHRISTMAS = SceneTheme(
        id = "christmas",
        displayName = "Christmas",
        skyNight = intArrayOf(0xFF0A1330.toInt(), 0xFF152048.toInt()),
        skyDawn = intArrayOf(0xFFAFC2E0.toInt(), 0xFFE9F0FA.toInt()),
        skyDay = intArrayOf(0xFF8FBCE8.toInt(), 0xFFEAF4FF.toInt()),
        skyDusk = intArrayOf(0xFF5A6FA8.toInt(), 0xFFC9D6EE.toInt()),
        hillColorsDay = intArrayOf(0xFFF3F7FB.toInt(), 0xFFD9E4F0.toInt(), 0xFFB9CCE3.toInt()),
        hillColorsNight = intArrayOf(0xFF23305A.toInt(), 0xFF1A2447.toInt(), 0xFF121A35.toInt()),
        sunColor = 0xFFFFFDF2.toInt(),
        moonColor = 0xFFF3F8FF.toInt(),
        starColor = 0xFFFFFFFF.toInt(),
        accentColor = 0xFFC1443B.toInt(), // festive red for the touch bird
        hasSantaSleigh = true,
    )

    val NEW_YEAR = SceneTheme(
        id = "new_year",
        displayName = "New Year's Eve",
        skyNight = intArrayOf(0xFF0B0B1F.toInt(), 0xFF181832.toInt()),
        skyDawn = intArrayOf(0xFF3A3A66.toInt(), 0xFF6B6B9E.toInt()),
        skyDay = intArrayOf(0xFF6E86C7.toInt(), 0xFFB9C6E8.toInt()),
        skyDusk = intArrayOf(0xFF2E2856.toInt(), 0xFF5B4E8C.toInt()),
        hillColorsDay = intArrayOf(0xFF6B6B85.toInt(), 0xFF4E4E66.toInt(), 0xFF35354A.toInt()),
        hillColorsNight = intArrayOf(0xFF1C1C30.toInt(), 0xFF141425.toInt(), 0xFF0D0D1A.toInt()),
        sunColor = 0xFFFFE9B0.toInt(),
        moonColor = 0xFFEDEBFF.toInt(),
        starColor = 0xFFFFFFFF.toInt(),
        accentColor = 0xFFF2C230.toInt(), // gold, like confetti
        hasFireworks = true,
    )

    val BEACH = SceneTheme(
        id = "beach",
        displayName = "Beach",
        skyNight = intArrayOf(0xFF0E2A3D.toInt(), 0xFF184057.toInt()),
        skyDawn = intArrayOf(0xFFFFB98F.toInt(), 0xFFFFE3C4.toInt()),
        skyDay = intArrayOf(0xFF4FC3E8.toInt(), 0xFFE0F7FA.toInt()),
        skyDusk = intArrayOf(0xFFFF8A65.toInt(), 0xFFFFD59E.toInt()),
        hillColorsDay = intArrayOf(0xFF2FAE9E.toInt(), 0xFFEFD9A3.toInt(), 0xFFE3C685.toInt()),
        hillColorsNight = intArrayOf(0xFF123B4A.toInt(), 0xFF2E2E44.toInt(), 0xFF3D3A2E.toInt()),
        sunColor = 0xFFFFF3B0.toInt(),
        moonColor = 0xFFEFF6FF.toInt(),
        starColor = 0xFFFFFFFF.toInt(),
        accentColor = 0xFFFF7043.toInt(),
    )

    val CITY = SceneTheme(
        id = "city",
        displayName = "Big City",
        skyNight = intArrayOf(0xFF15161F.toInt(), 0xFF23253A.toInt()),
        skyDawn = intArrayOf(0xFFB08FA0.toInt(), 0xFFE8C9C4.toInt()),
        skyDay = intArrayOf(0xFF7C93B0.toInt(), 0xFFCBD6E0.toInt()),
        skyDusk = intArrayOf(0xFF8A5A78.toInt(), 0xFFE0A6A0.toInt()),
        hillColorsDay = intArrayOf(0xFF5B6270.toInt(), 0xFF454B57.toInt(), 0xFF2E323C.toInt()),
        hillColorsNight = intArrayOf(0xFF1B1D26.toInt(), 0xFF15161D.toInt(), 0xFF0F1015.toInt()),
        sunColor = 0xFFFFE9C6.toInt(),
        moonColor = 0xFFE6EAF5.toInt(),
        starColor = 0xFFE9E9F5.toInt(),
        accentColor = 0xFFF2A65A.toInt(),
    )

    val TUNDRA = SceneTheme(
        id = "tundra",
        displayName = "Tundra",
        skyNight = intArrayOf(0xFF14213D.toInt(), 0xFF223A5E.toInt()),
        skyDawn = intArrayOf(0xFF9FB8D9.toInt(), 0xFFE3EEF7.toInt()),
        skyDay = intArrayOf(0xFF9AD1E8.toInt(), 0xFFF2FAFF.toInt()),
        skyDusk = intArrayOf(0xFF5C7FA6.toInt(), 0xFFB6CBE0.toInt()),
        hillColorsDay = intArrayOf(0xFFEFF6FA.toInt(), 0xFFD3E3EE.toInt(), 0xFFAFC7DA.toInt()),
        hillColorsNight = intArrayOf(0xFF2B3F5C.toInt(), 0xFF20304A.toInt(), 0xFF17233A.toInt()),
        sunColor = 0xFFFFFFFA.toInt(),
        moonColor = 0xFFEFF5FF.toInt(),
        starColor = 0xFFFFFFFF.toInt(),
        accentColor = 0xFF6FA8DC.toInt(),
    )

    val EASTER = SceneTheme(
        id = "easter",
        displayName = "Easter",
        skyNight = intArrayOf(0xFF241B3D.toInt(), 0xFF362A54.toInt()),
        skyDawn = intArrayOf(0xFFFAD1E6.toInt(), 0xFFFCEAF2.toInt()),
        skyDay = intArrayOf(0xFFAEE0F2.toInt(), 0xFFF3FBEF.toInt()),
        skyDusk = intArrayOf(0xFFE3A9D8.toInt(), 0xFFF7D9C4.toInt()),
        hillColorsDay = intArrayOf(0xFFB8E0A0.toInt(), 0xFF9BD088.toInt(), 0xFF7EBE6F.toInt()),
        hillColorsNight = intArrayOf(0xFF3A4A38.toInt(), 0xFF2C3A2B.toInt(), 0xFF1F291E.toInt()),
        sunColor = 0xFFFFF3C4.toInt(),
        moonColor = 0xFFEFE6FF.toInt(),
        starColor = 0xFFFFFFFF.toInt(),
        accentColor = 0xFFE87FA0.toInt(), // spring pink for the touch bird
    )

    val ALL: List<SceneTheme> = listOf(SUNSET, AUTUMN, WINTER, DESERT, CHRISTMAS, NEW_YEAR, BEACH, CITY, TUNDRA, EASTER)

    fun byId(id: String?): SceneTheme {
        if (id != null && RandomSceneGenerator.isRandomThemeId(id)) {
            return RandomSceneGenerator.generateTheme(id)
        }
        if (id != null) {
            // A user-saved replacement for a built-in theme takes priority over the hardcoded
            // default; "Reset to default" is just removing that override (see CustomThemeStore).
            CustomThemeRegistry.overrideThemeFor(id)?.let { return it }
        }
        ALL.firstOrNull { it.id == id }?.let { return it }
        if (id != null) {
            // Fully independent custom theme (id looks like "custom:<token>").
            CustomThemeRegistry.customEntry(id)?.let { return it.theme }
        }
        return SUNSET
    }
}
