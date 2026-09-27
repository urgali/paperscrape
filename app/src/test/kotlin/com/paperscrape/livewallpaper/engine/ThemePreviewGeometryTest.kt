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
     * The card is a pure function of what it is given: the same theme and customization build the
     * same scene every time, on every theme, day and forced night. That is what lets the composable
     * `remember` it and what makes "one preview system" mean anything.
     *
     * **This used to be called "both call sites get an identical scene for identical inputs"**, and
     * it called the same function twice with the same arguments -- a tautology, which could not
     * see the thing its name was about: the gallery passing a *different* customization from the
     * one World & scene shows. It did, until v5.8B (the saved copy instead of the edits in
     * progress). The name was the lie; what it checks is this, and the call sites are held by the
     * next test.
     */
    @Test
    fun `the card is a pure function of its inputs`() {
        for (theme in ThemeCatalog.ALL) {
            val customization = defaultCustomizationFor(theme.id)
            for (night in listOf(null, true, false)) {
                assertEquals(
                    theme.id,
                    ThemePreviewScenes.forTheme(theme, customization, night),
                    ThemePreviewScenes.forTheme(theme, customization, night),
                )
            }
        }
    }

    /**
     * The gallery's cards are given the customization the wallpaper would draw -- the same
     * `CustomThemeRegistry.resolveActiveCustomization` the home and World & scene previews and the
     * engine use -- and not the saved copy alone, which is what they had until v5.8B. Read from the
     * screen's source, because the coupling is a Compose call site no JVM test can run.
     */
    @Test
    fun `the gallery resolves each card's customization the way the wallpaper does`() {
        val gallery = source("ui/ThemeGalleryScreen.kt")
        assertTrue(gallery.contains("CustomThemeRegistry.resolveActiveCustomization("))
        val cards = Regex("""ThemeCard\(\s*theme = [^,]+,\s*customization = ([^,]+),""").findAll(gallery).map { it.groupValues[1] }.toList()
        assertEquals("both card grids", 2, cards.size)
        assertTrue("every card takes the resolved customization: $cards", cards.all { it.startsWith("cardCustomization(") })
    }

    private fun source(path: String): String {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val candidate = java.io.File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/$path")
                if (candidate.isFile) return candidate.readText()
            }
            dir = dir.parentFile
        }
        error("could not locate $path")
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
