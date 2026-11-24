package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * ***Fireworks*, a switch on every theme** (`DESIGN_NOTES.md` §12, decision 103; the maintainer, 2026-11-07: *«interruttore
 * fuochi ok, da mettere nella sezione seasonal»*): on as New Year's Eve ships, off on the other eleven built-ins; a theme
 * file or a saved theme without the field reads its theme's own; the wallpaper and the gallery card read the switch.
 */
class FireworksSwitchTest {

    private val FRAME = FireworkEffect.FRAME_SECONDS

    @Test
    fun `on as New Year's Eve ships, off on the others, and as a theme's own hasFireworks says`() {
        for (theme in ThemeCatalog.ALL) assertEquals(theme.id, theme.id == "new_year", defaultCustomizationFor(theme.id).fireworksEnabled)
        val withFireworks = ThemeCatalog.byId("autumn").copy(id = "custom:f", hasFireworks = true)
        assertTrue(defaultCustomizationFor(withFireworks.id, withFireworks).fireworksEnabled)
        assertFalse(defaultCustomizationFor("custom:g", ThemeCatalog.byId("autumn").copy(id = "custom:g")).fireworksEnabled)
    }

    @Test
    fun `a theme file keeps the switch, and one without it reads its theme's own`() {
        val newYear = ThemeCatalog.byId("new_year")
        val autumn = ThemeCatalog.byId("autumn")
        val on = defaultCustomizationFor("autumn").copy(fireworksEnabled = true)
        assertTrue(sceneCustomizationFromJson(on.toJson(), autumn).fireworksEnabled)
        val off = defaultCustomizationFor("new_year").copy(fireworksEnabled = false)
        assertFalse(sceneCustomizationFromJson(off.toJson(), newYear).fireworksEnabled)
        assertTrue(sceneCustomizationFromJson(off.toJson().apply { remove("fireworksEnabled") }, newYear).fireworksEnabled)
        assertFalse(sceneCustomizationFromJson(on.toJson().apply { remove("fireworksEnabled") }, autumn).fireworksEnabled)
    }

    @Test
    fun `the wallpaper and the card draw them by the switch, the card only at night`() {
        val renderer = source("PaperRenderer.kt")
        assertTrue("val fireworksEnabled = sceneCustomization.fireworksEnabled && dayPhase.dayBlend < 0.35f" in codeLines(renderer))
        assertTrue("fireworkEffect.update(deltaSeconds, fireworksEnabled, screenWidth.toFloat(), screenHeight.toFloat())" in codeLines(renderer))
        assertFalse(renderer.contains("theme.hasFireworks"))
        val card = source("ThemePreviewScene.kt")
        assertTrue("if (c.fireworksEnabled && night) {" in codeLines(card))
    }

    /**
     * **Off -- the switch off, or the day -- nothing goes off, and the show waits**: the effect, handed `enabled` false for a
     * minute, launches nothing and its clock stands; handed true, its first rocket leaves one second before the first burst's
     * instant, two seconds into the fireworks, as if the minute had not been. A rocket in the sky when the night ends still
     * bursts and falls.
     */
    @Test
    fun `off, nothing goes off and the show waits`() {
        val saved = SceneRandom.source
        SceneRandom.source = kotlin.random.Random(103)
        try {
            val effect = FireworkEffect()
            var launches = 0
            effect.watcher = FireworkEffect.LaunchWatcher { _, _, _, _, _, _, _, _, _ -> launches++ }
            repeat(1800) { effect.update(FRAME, false, 720f, 1440f) }
            assertEquals("launches while off", 0, launches)
            assertEquals("the show's clock while off", 0.0, effect.show, 0.0)
            var firstAt = -1.0
            var frames = 0
            while (launches == 0 && frames < 300) {
                effect.update(FRAME, true, 720f, 1440f)
                frames++
                if (launches > 0) firstAt = effect.show
            }
            assertEquals("the first rocket, a second before the first burst", FireworkEffect.FIRST_SINGLE_AT - FireworkEffect.RISE, firstAt, FRAME.toDouble())
            // Off again with the rocket rising: it goes on, bursts and falls; nothing new leaves.
            repeat(300) { effect.update(FRAME, false, 720f, 1440f) }
            assertEquals("launches after the night ended", 1, launches)
            assertEquals("the burst ended", 0, effect.liveBursts())
        } finally {
            SceneRandom.source = saved
        }
    }

    private fun source(name: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/engine/$name"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            File(dir, suffix).takeIf { it.isFile }?.let { return it.readText() }
            File(dir, "app/$suffix").takeIf { it.isFile }?.let { return it.readText() }
            dir = dir.parentFile
        }
        error("no $name")
    }

    /** [text]'s lines, trimmed, with comment lines, trailing `//` comments and blank lines left out. */
    private fun codeLines(text: String): List<String> =
        text.lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }
}
