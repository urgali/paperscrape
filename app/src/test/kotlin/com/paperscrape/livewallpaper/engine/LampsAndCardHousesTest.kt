package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two smaller things of v5.11: the door lamps that light with the windows (inventory I-405, I-408,
 * I-409) and the distant houses on the gallery card, which follows their switch as it follows every
 * other (I-407, and the card's rule of v5.10E, I-297).
 */
class LampsAndCardHousesTest {

    @Test
    fun `a lamp is paper by day and the lamp it always was at night`() {
        assertEquals(SceneObjectRenderer.LAMP_UNLIT, SceneObjectRenderer.lampBulbColour(0f))
        assertEquals(0, SceneObjectRenderer.lampGlowAlpha(0f))
        // Fully lit: the bulb and the glow every lamp had at night until v5.11.
        assertEquals(0xFFFFD97A.toInt(), SceneObjectRenderer.lampBulbColour(1f))
        assertEquals(150, SceneObjectRenderer.lampGlowAlpha(1f))
        // On the way, monotone: it lights as the glass does.
        var last = -1
        for (step in 0..10) {
            val alpha = SceneObjectRenderer.lampGlowAlpha(step / 10f)
            assertTrue(alpha >= last)
            last = alpha
        }
    }

    /**
     * The bar's lantern is glass since v5.11 (I-408): it was yellow in the fixed layer, lit at noon. Its
     * light is now in the bar's glass mask, which takes the window's colour -- cool by day, warm at
     * night, dark while the bar is closed -- and none of it is left yellow in the fixed layer.
     */
    @Test
    fun `the bar's lantern is in its glass, not in its fixed layer`() {
        // The chamfered bar carries no yellow but the lantern and its door's handle (its plaque's
        // emblem is red), so the solid yellow left in its fixed layer is the handle alone: a dot of
        // 0.9 units, a handful of sprite pixels, where the lantern was 4.4 x 5 units of them.
        val image = ImageIO.read(File(drawableDir(), "bar_chamfer_fx.png"))
        var yellow = 0
        for (y in 0 until image.height) for (x in 0 until image.width) {
            val argb = image.getRGB(x, y)
            if ((argb ushr 24) == 0xFF && (argb and 0xFFFFFF) == 0xF7CE64) yellow++
        }
        assertTrue("$yellow solid yellow pixels left in the chamfered bar's fixed layer", yellow < 40)
        val generator = File(repoRoot(), "tools/assets/buildings/vocab.py").readText()
        val lantern = generator.substring(generator.indexOf("def lantern("))
        assertTrue(lantern.substringBefore("\n\ndef ").contains("), GLASS, relief=relief"))
    }

    @Test
    fun `the card stands distant houses on its mountains only while the wallpaper would`() {
        val theme = ThemeCatalog.byId("autumn")
        val base = defaultCustomizationFor("autumn")
        fun housesOn(c: SceneCustomization) = ThemePreviewScenes.forTheme(theme, c).peaks.sumOf { it.houses.size }
        assertEquals("off as it ships", 0, housesOn(base))
        val on = base.copy(distantHouses = DistantHousesConfig(visible = true, density = 0.5f))
        val peaks = ThemePreviewScenes.forTheme(theme, on).peaks
        assertTrue("on: one or more on every mountain", peaks.isNotEmpty() && peaks.all { it.houses.isNotEmpty() })
        assertEquals("none at 0 %", 0, housesOn(on.copy(distantHouses = DistantHousesConfig(true, 0f))))
        val noMountains = on.copy(
            mountainsFront = on.mountainsFront.copy(visible = false),
            mountainsBack = on.mountainsBack.copy(visible = false),
        )
        assertEquals("nowhere to stand", 0, housesOn(noMountains))
        // In the houses' colours: every wall mask on the card's distant houses is one of the pair.
        val dayBlend = ThemePreviewScenes.cardPhase(theme, false).dayBlend
        val pair = setOf(on.houses.colorAt(0, dayBlend), on.houses.colorAt(1, dayBlend))
        val glass = setOf(
            SceneObjectRenderer.windowGlassColor(1f - dayBlend),
            SceneObjectRenderer.windowGlassColor((1f - dayBlend) * DistantHouses.UNLIT_GLOW),
        )
        val walls = peaks.flatMap { it.houses }.flatMap { it.parts }.filter { it.added && it.tint != null }
            .map { it.tint!! }.filter { it !in glass }
        assertTrue(walls.isNotEmpty())
        assertTrue("walls $walls not in $pair", walls.all { it in pair })
        // At the card's midnight the windows light by the wallpaper's coin: lit in four houses of five.
        val tints = HashSet<Int>()
        for (t in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(t.id).copy(distantHouses = DistantHousesConfig(visible = true, density = 1f))
            val night = 1f - ThemePreviewScenes.cardPhase(t, true).dayBlend
            for (part in ThemePreviewScenes.forTheme(t, c, forceNight = true).peaks.flatMap { it.houses }.flatMap { it.parts }) {
                if (part.added && part.tint == SceneObjectRenderer.windowGlassColor(night)) tints += 1
                if (part.added && part.tint == SceneObjectRenderer.windowGlassColor(night * DistantHouses.UNLIT_GLOW)) tints += 2
            }
        }
        assertTrue("lit windows on the night cards", 1 in tints)
    }

    private fun repoRoot(): File {
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/res/drawable-nodpi").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found from ${File(".").absolutePath}")
    }

    private fun drawableDir(): File = File(repoRoot(), "app/src/main/res/drawable-nodpi")
}
