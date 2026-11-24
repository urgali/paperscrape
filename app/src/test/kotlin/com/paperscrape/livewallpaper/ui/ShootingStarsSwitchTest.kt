package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.PaperRenderer
import com.paperscrape.livewallpaper.engine.StarsConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The *Shooting stars* switch shows on only while the wallpaper draws them** (`DESIGN_NOTES.md` §12, decision 40):
 * [SettingsUiModel.shootingStars], off and locked while *Show Stars* reads off, the stored choice kept, and on exactly
 * when [PaperRenderer.shootingStarsDrawn] says the scene draws them. That the Stars page draws from the rule is read off
 * its source, as the project does for Compose it cannot run on the JVM: whole lines, the comments taken off.
 */
class ShootingStarsSwitchTest {

    private val densities = listOf(0f, 0.01f, 1f / 70f, 0.02f, 0.5f, 1f)

    @Test
    fun `on only with Show Stars on, a star in the field, and the switch on`() {
        assertTrue(SettingsUiModel.shootingStars(StarsConfig(visible = true, density = 1f, shooting = true)).shownOn)
        val stored = SettingsUiModel.shootingStars(StarsConfig(visible = true, density = 1f, shooting = false))
        assertFalse(stored.shownOn)
        assertTrue("turned off by hand it can be turned on again", stored.interactive)
        for (shooting in listOf(true, false)) {
            for (held in listOf(
                StarsConfig(visible = false, density = 1f, shooting = shooting),
                StarsConfig(visible = true, density = 0f, shooting = shooting),
                // Below 1/70 the field holds no star, and Show Stars reads off there.
                StarsConfig(visible = true, density = 0.01f, shooting = shooting),
            )) {
                val state = SettingsUiModel.shootingStars(held)
                assertFalse("$held: the switch read on with no star shown", state.shownOn)
                assertFalse("$held: locked while Show Stars reads off", state.interactive)
            }
        }
    }

    @Test
    fun `the switch reads on exactly when the wallpaper draws shooting stars`() {
        for (visible in listOf(true, false)) for (shooting in listOf(true, false)) for (density in densities) {
            val stars = StarsConfig(visible = visible, density = density, shooting = shooting)
            assertEquals("$stars", PaperRenderer.shootingStarsDrawn(stars), SettingsUiModel.shootingStars(stars).shownOn)
        }
    }

    @Test
    fun `the Stars page draws the switch from the rule, under the stars' own controls`() {
        val world = source("ui/WorldSceneScreen.kt")
        val page = world.substringAfter("private fun StarsSubScreen(").substringBefore("\n@Composable")
        val lines = page.lines().map { it.substringBefore("//").trim() }
        for (wiring in listOf(
            "val shooting = SettingsUiModel.shootingStars(customization.stars)",
            "title = SHOOTING_STARS_TITLE,",
            "subtitle = if (shooting.interactive) SHOOTING_STARS_LINE else SHOOTING_STARS_NEEDS_STARS_LINE,",
            "checked = shooting.shownOn,",
            "enabled = shooting.interactive,",
            "onCheckedChange = { scope.launch { prefs.setShootingStarsVisible(it, forThemeId) } },",
        )) {
            assertTrue("the Stars page does not draw: $wiring", lines.contains(wiring))
        }
        val showStars = lines.indexOf("title = \"Show Stars\",")
        assertTrue("the Stars page's Show Stars row", showStars >= 0)
        assertTrue("after Show Stars", lines.indexOf("title = SHOOTING_STARS_TITLE,") > showStars)
        // One place in the whole app moves the switch: the Stars page.
        val callers = uiDir().listFiles { f -> f.name.endsWith(".kt") }!!.filter { it.readText().contains("prefs.setShootingStarsVisible(") }
        assertEquals(listOf("WorldSceneScreen.kt"), callers.map { it.name })
    }

    /** The words decided for the switch (decision 114), compared whole. */
    @Test
    fun `the switch says the decided words`() {
        assertEquals("Shooting stars", SHOOTING_STARS_TITLE)
        assertEquals(
            "A streak of light crossing the night sky now and then, about once every three minutes, behind the clouds.",
            SHOOTING_STARS_LINE,
        )
        assertEquals("Needs Show Stars above; your choice is kept until then.", SHOOTING_STARS_NEEDS_STARS_LINE)
    }

    /**
     * **Behind the clouds and above the stars and the moon** (decision 114): in the wallpaper's frame
     * ([PaperRenderer.draw]) the shooting star is drawn after every call that draws the stars and the sun or moon, and
     * right before the clouds. Whole lines of the renderer, the comments taken off.
     */
    @Test
    fun `the shooting star is drawn after the stars and the moon and right before the clouds`() {
        val renderer = source("engine/PaperRenderer.kt").lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }
        val start = renderer.indexOf("fun draw(canvas: SceneCanvas, framePhase: SunPositionCalculator.DayPhase, elapsedSeconds: SceneTime, deltaSeconds: Float) {")
        assertTrue("PaperRenderer.draw", start >= 0)
        val end = (start + 1 until renderer.size).firstOrNull { renderer[it].startsWith("fun ") || renderer[it].startsWith("private fun ") }
            ?: renderer.size
        val draw = renderer.subList(start, end)
        val shooting = draw.indexOf("drawShootingStar(canvas, dayPhase, elapsedSeconds)")
        assertTrue("draw draws the shooting star", shooting >= 0)
        assertEquals("once a frame", 1, draw.count { it.startsWith("drawShootingStar(") })
        assertEquals("right before the clouds", "drawClouds(canvas, dayPhase, elapsedSeconds, deltaSeconds)", draw[shooting + 1])
        val sky = draw.withIndex().filter { (_, line) -> line.startsWith("drawStars(") || line.startsWith("drawCelestialBody(") }
        assertTrue("the stars and the sun or moon, on both of draw's paths", sky.count { it.value.startsWith("drawStars(") } >= 2 && sky.count { it.value.startsWith("drawCelestialBody(") } >= 2)
        for ((at, line) in sky) assertTrue("`$line` comes before the shooting star", at < shooting)
    }

    /** The gallery card is a still picture: it draws no shooting star, and the switch is not read there. */
    @Test
    fun `the gallery card does not read the switch`() {
        for (path in listOf("engine/ThemePreviewScene.kt", "ui/ThemePreview.kt")) {
            val text = source(path)
            assertFalse("$path reads the shooting stars", text.contains("shooting", ignoreCase = true))
        }
    }

    private fun uiDir(): java.io.File {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/ui"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isDirectory) return f
            }
            dir = dir.parentFile
        }
        error("sources not found: $suffix")
    }

    private fun source(path: String): String = java.io.File(uiDir().parentFile, path).readText()
}
