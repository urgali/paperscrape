package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A card rains and snows in the wallpaper's colours: the theme's own, at the card's moment, and
 * the rain carried off the card's own sky.**
 *
 * The wallpaper paints precipitation in the theme's night colour blended toward its day colour by
 * the day blend (`PaperRenderer.drawPrecipitation`). The gallery card took the day colour whatever
 * moment it showed, which was harmless while every card was a day card and stopped being so when
 * Halloween's and New Year's Eve's cards became midnight ones -- item 73 of `BACKLOG_v4_27.md` had
 * named exactly this as *"the paragraph to come back to"* (v5.9B, inventory row I-03).
 *
 * The wallpaper then carries its rain off the sky it falls through
 * (`PrecipitationContrast.standOffFromSky`); the card did not, and on four cards of twelve the rain
 * was the brightness of the card's own sky and all but vanished (v5.9F, inventory row I-04: Big City
 * and Beach at noon, Sunset at seven, New Year's Eve as the World & scene strip shows it by day).
 * The card now does what the wallpaper does, for the rain; the snow keeps the theme's colour (see
 * `ThemePreviewScenes.cardRainColour` for why).
 *
 * Only what the card is made of is checked here, not pixels: a card is a data description (see
 * [ThemePreviewScene]), and its rain and snow are the dots painted over everything.
 */
class PreviewPrecipitationColourTest {

    private fun precipitationDots(themeId: String, type: PrecipitationType, forceNight: Boolean? = null): Set<Int> {
        val base = defaultCustomizationFor(themeId)
        val c = base.copy(precipitation = base.precipitation.copy(visible = true, type = type, intensity = 1f))
        val scene = ThemePreviewScenes.forTheme(ThemeCatalog.byId(themeId), c, forceNight = forceNight)
        // The precipitation is the only front layer of small dots; the falling leaves are larger.
        return scene.dots.filter { it.front && it.radius < 1f }.map { it.colour }.toSet()
    }

    private fun custom(themeId: String) = defaultCustomizationFor(themeId).precipitation

    @Test
    fun `the two night cards are drawn at midnight, where the day blend is zero`() {
        for (id in listOf("halloween", "new_year")) {
            val c = defaultCustomizationFor(id)
            assertTrue("$id is not a night card", c.horrorSkyEnabled || ThemeCatalog.byId(id).hasFireworks)
        }
        assertEquals(0f, ThemePreviewScenes.cardPhase(ThemeCatalog.byId("halloween"), night = true).dayBlend, 0f)
    }

    /**
     * New Year's Eve's night rain is already far from its night sky and is drawn exactly; Halloween's
     * is the brightness of the orange at the bottom of its sky and is carried off it.
     */
    @Test
    fun `rain on a night card is the theme's night rain, carried off the card's sky`() {
        assertEquals("new_year rain", setOf(custom("new_year").rainColorNight), precipitationDots("new_year", PrecipitationType.RAIN))
        val card = card("halloween", PrecipitationType.RAIN)
        val drawn = precipitationDots("halloween", PrecipitationType.RAIN).single()
        assertEquals(
            "halloween rain",
            ThemePreviewScenes.cardRainColour(custom("halloween").rainColorNight, card.skyTop, card.skyBottom),
            drawn,
        )
        assertTrue("halloween's night rain had to move", drawn != custom("halloween").rainColorNight)
    }

    @Test
    fun `snow on a night card is the theme's night snow`() {
        for (id in listOf("halloween", "new_year")) {
            assertEquals("$id snow", setOf(custom(id).snowColorNight), precipitationDots(id, PrecipitationType.SNOW))
        }
    }

    /**
     * The guard: a day card whose rain already stands clear of its sky, at noon, is exactly what it
     * was, bit for bit; and the snow on a noon card is the theme's own day snow. (Sunset's card is at
     * seven, where the day blend is not 1: `the snow on a card is never corrected` covers it.)
     */
    @Test
    fun `a day card keeps the day colours where they are already clear`() {
        for (id in listOf("autumn", "winter", "desert", "tundra", "easter", "spring")) {
            assertEquals("$id rain", setOf(custom(id).rainColorDay), precipitationDots(id, PrecipitationType.RAIN))
        }
        for (id in listOf("autumn", "winter", "beach", "city", "desert")) {
            assertEquals("$id snow", setOf(custom(id).snowColorDay), precipitationDots(id, PrecipitationType.SNOW))
        }
    }

    /**
     * **The four cards of inventory I-04, and every other one.** On every card, at its own moment and
     * -- for the two night cards -- also by day, as the World & scene strip can show them, the rain
     * stands clear of the card's own sky by the wallpaper's gap as it is composited (the dot's alpha,
     * 230 of 255). "Clear" is measured on the whole gradient, sampled finely; the blend lands on whole
     * channel values, which can leave it short of the gap by up to a level of luma, so the floor is
     * the gap less one. Today's four were 0.44-1.41 (v5.9D).
     */
    @Test
    fun `on every card the rain stands clear of the card's own sky`() {
        val alpha = ThemePreviewScenes.PRECIPITATION_DOT_ALPHA / 255f
        val floor = PaperRenderer.PRECIPITATION_MIN_LUMA_GAP - 1f
        for (theme in ThemeCatalog.ALL) {
            val night = defaultCustomizationFor(theme.id).let { it.horrorSkyEnabled || theme.hasFireworks }
            for (force in if (night) listOf<Boolean?>(null, false) else listOf<Boolean?>(null)) {
                val scene = card(theme.id, PrecipitationType.RAIN, force)
                val rain = precipitationDots(theme.id, PrecipitationType.RAIN, force).single()
                val gap = (0..64).minOf { k ->
                    kotlin.math.abs(
                        PrecipitationContrast.rec601Luma(rain) -
                            PrecipitationContrast.rec601Luma(SceneColour.blendArgb(scene.skyTop, scene.skyBottom, k / 64f)),
                    )
                } * alpha
                assertTrue("${theme.id} (${force ?: "its own moment"}): the rain is $gap of luma from its sky", gap >= floor)
            }
        }
    }

    /** Where the rain vanished it moves, and it moves from the theme's own colour, not to another. */
    @Test
    fun `the four cards where the rain vanished draw it apart from their sky`() {
        for ((id, force) in listOf("city" to null, "beach" to null, "sunset" to null, "new_year" to false)) {
            val c = custom(id)
            val scene = card(id, PrecipitationType.RAIN, force)
            val phase = ThemePreviewScenes.cardPhase(ThemeCatalog.byId(id), force ?: false)
            val theme = SceneColour.blendArgb(c.rainColorNight, c.rainColorDay, phase.dayBlend)
            val drawn = precipitationDots(id, PrecipitationType.RAIN, force).single()
            assertTrue("$id: the rain is still the theme's colour", drawn != theme)
            assertEquals("$id", ThemePreviewScenes.cardRainColour(theme, scene.skyTop, scene.skyBottom), drawn)
        }
    }

    /** The snow is the theme's own on every card: the rule that lifts the rain would grey it. */
    @Test
    fun `the snow on a card is never corrected`() {
        for (theme in ThemeCatalog.ALL) {
            val c = custom(theme.id)
            val nightCard = defaultCustomizationFor(theme.id).let { it.horrorSkyEnabled || theme.hasFireworks }
            val phase = ThemePreviewScenes.cardPhase(theme, nightCard)
            assertEquals(
                theme.id,
                setOf(SceneColour.blendArgb(c.snowColorNight, c.snowColorDay, phase.dayBlend)),
                precipitationDots(theme.id, PrecipitationType.SNOW),
            )
        }
    }

    private fun card(themeId: String, type: PrecipitationType, forceNight: Boolean? = null): ThemePreviewScene {
        val base = defaultCustomizationFor(themeId)
        val c = base.copy(precipitation = base.precipitation.copy(visible = true, type = type, intensity = 1f))
        return ThemePreviewScenes.forTheme(ThemeCatalog.byId(themeId), c, forceNight = forceNight)
    }

    /** And the World & scene strip's night toggle, which draws any theme at midnight, follows the same rule. */
    @Test
    fun `any theme forced to night rains in its night colour`() {
        val scene = card("autumn", PrecipitationType.RAIN, forceNight = true)
        assertEquals(
            setOf(ThemePreviewScenes.cardRainColour(custom("autumn").rainColorNight, scene.skyTop, scene.skyBottom)),
            precipitationDots("autumn", PrecipitationType.RAIN, forceNight = true),
        )
    }
}
