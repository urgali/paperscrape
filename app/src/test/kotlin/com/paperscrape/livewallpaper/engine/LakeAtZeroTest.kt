package com.paperscrape.livewallpaper.engine

import com.paperscrape.livewallpaper.ui.AmountTap
import com.paperscrape.livewallpaper.ui.SettingsUiModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **The lake at 0 % height is off, like every other amount at 0** (v5.10E, inventory I-293, the
 * maintainer's *«2 - si»* of 2026-10-04 to the PM's proposal). At 0 % the band was zero pixels tall and
 * *Show Lake* still read on, and the sailboats and dolphins were still laid out on lanes of a band with
 * no height. Now [LakeConfig.drawsWater] is the one rule: the wallpaper draws no lake and nothing on it
 * (`PaperRenderer.updateLakeBandY`), the gallery card none, the switch says so, and its tap puts the
 * theme's own height back ([SettingsUiModel.amountTap]).
 */
class LakeAtZeroTest {

    private fun beachAt(height: Float) = defaultCustomizationFor("beach").let { it.copy(lake = it.lake.copy(height = height)) }

    @Test
    fun `there is water only switched on and above zero height`() {
        val lake = defaultCustomizationFor("beach").lake
        assertTrue(lake.visible && lake.height > 0f)
        assertTrue(lake.drawsWater)
        assertFalse(lake.copy(height = 0f).drawsWater)
        assertFalse(lake.copy(visible = false).drawsWater)
        assertTrue("any height above 0 is water", lake.copy(height = 0.01f).drawsWater)
    }

    @Test
    fun `the gallery card draws no lake and nothing on it at zero`() {
        val beach = ThemeCatalog.byId("beach")
        val full = ThemePreviewScenes.forTheme(beach, beachAt(0.33f))
        assertTrue("Beach's card has its sea", full.hasLake)
        assertTrue("and something on it", full.water.isNotEmpty())
        val zero = ThemePreviewScenes.forTheme(beach, beachAt(0f))
        assertFalse("a sea of no height on the card", zero.hasLake)
        assertTrue("a boat or a dolphin on a sea of no height", zero.water.isEmpty())
    }

    @Test
    fun `the switch reads off at zero and its tap brings the theme's height back`() {
        val c = beachAt(0f)
        val shown = SettingsUiModel.amountSwitch(c.lake.visible, c.lake.height)
        assertFalse(shown.shownOn)
        assertTrue(shown.noneAtZero)
        val tap = SettingsUiModel.amountTap(wanted = true, amount = c.lake.height, defaultAmount = defaultCustomizationFor("beach").lake.height)
        assertEquals(AmountTap.Restore(defaultCustomizationFor("beach").lake.height), tap)
        // And the boats and dolphins lock, as with the lake off.
        val contents = SettingsUiModel.lakeContents(lakeVisible = shown.shownOn, storedSailboatsVisible = true, storedDolphinsVisible = true)
        assertFalse(contents.interactive)
        assertFalse(contents.sailboatsShownOn || contents.dolphinsShownOn)
    }

    /**
     * **No golden draws a lake at 0 %**: every theme ships its lake either off or above 0, so the
     * wallpaper's frames for the built-in themes are what they were (the 33 goldens stand).
     */
    @Test
    fun `no theme ships a lake switched on at zero, so no theme's scene changes`() {
        for (theme in ThemeCatalog.ALL) {
            val lake = defaultCustomizationFor(theme.id).lake
            assertTrue("${theme.id} ships a lake at 0 %", !lake.visible || lake.height > 0f)
        }
    }

    @Test
    fun `the wallpaper, the card and the screen read the one rule`() {
        val renderer = source("engine/PaperRenderer.kt")
        val band = renderer.substringAfter("private fun updateLakeBandY(): Boolean {").substringBefore("val bottom =")
        assertTrue(band.contains("if (!lake.drawsWater) return false"))
        assertFalse("the renderer's old gate", band.contains("if (!lake.visible) return false"))
        val card = source("engine/ThemePreviewScene.kt")
        assertTrue(card.contains("hasLake = c.lake.drawsWater,"))
        assertTrue(card.contains("if (c.lake.drawsWater) lakeLife(c, lake, water)"))
        val world = source("ui/WorldSceneScreen.kt")
        assertTrue(world.contains("if (!lake.drawsWater) return NONE_AT_ZERO_SUMMARY"))
    }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
