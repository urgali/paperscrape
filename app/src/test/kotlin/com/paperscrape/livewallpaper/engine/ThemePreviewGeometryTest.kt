package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gallery card and the strip at the top of World & scene are one preview system.
 *
 * They drifted apart in v2.9 and this is what stops it recurring: both go through
 * [ThemePreviewGeometry] and [ThemePreviewScenes], so a change to the shape, the scale or the
 * composition reaches both or neither. The tests are about those shared parameters, not about
 * pixels -- a screenshot test here would fail on every legitimate artwork change and tell nobody
 * anything.
 */
class ThemePreviewGeometryTest {

    @Test
    fun `the preview is four by three`() {
        assertEquals(4f / 3f, ThemePreviewGeometry.ASPECT_RATIO, 0.0001f)
        assertEquals(
            ThemePreviewScene.WIDTH_UNITS / ThemePreviewScene.HEIGHT_UNITS,
            ThemePreviewGeometry.ASPECT_RATIO,
            0.0001f,
        )
    }

    /** A card at the gallery's own width: two columns, 16 dp margins, a 12 dp gap on 360 dp. */
    @Test
    fun `a gallery card maps the whole scene onto its width`() {
        val cardWidthPx = 158f
        val scale = ThemePreviewGeometry.scaleFor(cardWidthPx)
        assertEquals(cardWidthPx, ThemePreviewScene.WIDTH_UNITS * scale, 0.01f)
        assertEquals(ThemePreviewScene.HEIGHT_UNITS * scale, ThemePreviewGeometry.heightFor(cardWidthPx), 0.01f)
    }

    /** The World & scene strip, full width on the same screen. */
    @Test
    fun `a full-width strip uses the same rule, only larger`() {
        val stripWidthPx = 328f
        val cardScale = ThemePreviewGeometry.scaleFor(158f)
        val stripScale = ThemePreviewGeometry.scaleFor(stripWidthPx)
        assertTrue("a wider container must scale up", stripScale > cardScale)
        // Same rule, so the ratio of the scales is exactly the ratio of the widths: neither call
        // site applies a crop, a zoom or a fitting factor of its own.
        assertEquals(stripWidthPx / 158f, stripScale / cardScale, 0.0001f)
    }

    @Test
    fun `scale is uniform, so nothing is stretched or cropped at any size`() {
        for (width in listOf(64f, 158f, 328f, 360f, 720f, 1440f)) {
            val scale = ThemePreviewGeometry.scaleFor(width)
            val height = ThemePreviewGeometry.heightFor(width)
            assertEquals("width $width", width, ThemePreviewScene.WIDTH_UNITS * scale, 0.01f)
            assertEquals("width $width", height, ThemePreviewScene.HEIGHT_UNITS * scale, 0.01f)
            assertEquals("width $width", ThemePreviewGeometry.ASPECT_RATIO, width / height, 0.0001f)
        }
    }

    @Test
    fun `every object stays inside the composed area at any container size`() {
        val scene = ThemePreviewScenes.forTheme(ThemeCatalog.byId("winter"), defaultCustomizationFor("winter"))
        for (width in listOf(158f, 328f, 720f)) {
            val scale = ThemePreviewGeometry.scaleFor(width)
            val heightPx = ThemePreviewGeometry.heightFor(width)
            for (item in scene.backdrop + scene.items + scene.cars + scene.ground) {
                assertTrue("object above the top at $width", item.y * scale >= -1f)
                assertTrue("object below the bottom at $width", item.y * scale <= heightPx)
            }
        }
    }

    /**
     * The same theme and the same configuration produce the same scene wherever it is shown. The
     * call site is not an input, which is what "one preview system" means in practice.
     */
    @Test
    fun `both call sites get an identical scene for identical inputs`() {
        val theme = ThemeCatalog.byId("spring")
        val customization = defaultCustomizationFor("spring")
        val gallery = ThemePreviewScenes.forTheme(theme, customization)
        val worldAndScene = ThemePreviewScenes.forTheme(theme, customization)
        assertEquals(gallery, worldAndScene)
    }

    /**
     * The one thing World & scene adds: it can ask for the night palette, because half the colours
     * edited on the screens below it are night colours. The gallery never passes it, so a card is
     * unaffected.
     */
    @Test
    fun `asking for night changes the sky and the ground, and nothing else structural`() {
        val theme = ThemeCatalog.byId("spring")
        val customization = defaultCustomizationFor("spring")
        val day = ThemePreviewScenes.forTheme(theme, customization, forceNight = false)
        val night = ThemePreviewScenes.forTheme(theme, customization, forceNight = true)

        assertTrue("night must not look like day", night.skyTop != day.skyTop)
        assertEquals(customization.hillsColorNight, night.groundColour)
        assertEquals(customization.hillsColorDay, day.groundColour)
        // The scene is the same scene: same objects, same places.
        assertEquals(day.items.map { it.x to it.y }, night.items.map { it.x to it.y })
        assertEquals(day.hasLake, night.hasLake)
    }

    @Test
    fun `omitting the override leaves each theme showing its own hour`() {
        val spring = ThemeCatalog.byId("spring")
        val newYear = ThemeCatalog.byId("new_year")
        assertEquals(
            ThemePreviewScenes.forTheme(spring, defaultCustomizationFor("spring"), forceNight = false),
            ThemePreviewScenes.forTheme(spring, defaultCustomizationFor("spring")),
        )
        // New Year is a night theme on its own account, so its default and its night render agree.
        assertEquals(
            ThemePreviewScenes.forTheme(newYear, defaultCustomizationFor("new_year"), forceNight = true),
            ThemePreviewScenes.forTheme(newYear, defaultCustomizationFor("new_year")),
        )
    }
}
