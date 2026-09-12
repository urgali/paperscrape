package com.paperscrape.livewallpaper.engine

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Every sprite the built-in themes draw still fits in **one** atlas, with the people in layers.
 *
 * ### What this is guarding
 *
 * v4.29 rewrote the packer and the result was 64 standalone textures becoming 0: everything the
 * twelve-theme walk draws is packed into one 2048x2048 allocation. That is what makes v4.30's
 * layers cost nothing -- [GlSceneTarget] ends a batch only when the texture changes, and with no
 * standalone texture left it never changes, so a person drawn in five layers is five sets of six
 * vertices in the batch that was already open (`GlDrawCallTest` counts that).
 *
 * v4.30 adds 195 files to the set, of which 167 are region masks, and the obvious risk is that they
 * push something out of the atlas and quietly put the standalone textures back. They do not, and the
 * reason is the same change that cut the texture budget: `GlTextureCache` crops the transparent
 * border away **after** the reduction, so a mask occupies the rectangle its ink occupies rather than
 * its whole canvas.
 *
 * ### Why a theme sweep and not one scene
 *
 * No frame draws the whole set, and the atlas is a per-process allocation that accumulates across
 * everything the process has drawn. Sweeping the built-in themes in one context is the closest a
 * test can get to what a wallpaper left running does, which is the case v4.29 measured on the
 * device.
 */
@RunWith(AndroidJUnit4::class)
class GlAtlasOccupancyTest {

    @Test
    fun theWholeThemeSweepPacksIntoOneAtlas() {
        val counted = GlGolden.sweepThemes(ThemeCatalog.ALL.map { theme -> theme.id })
        println(
            "GlAtlasOccupancyTest: ${counted.entries} sprite-and-level entries, " +
                "${counted.standalone} standalone, ${counted.rowsUsed}/2048 atlas rows used",
        )
        assertEquals(
            "the theme sweep needed ${counted.standalone} textures outside the atlas. Every one of " +
                "them ends the batch wherever the draw order crosses it, which is the cost v4.29 " +
                "removed and v4.30's layers are only free because of",
            0,
            counted.standalone,
        )
    }
}
