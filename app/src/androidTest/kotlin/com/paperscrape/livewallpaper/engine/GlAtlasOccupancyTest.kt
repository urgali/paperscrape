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
 * device. Since v5.11 the sweep has the distant houses on, at 100 % on every mountain, and the
 * mountains on where a theme ships them off: their twelve drawings are in the atlas too. Since v5.15
 * it also draws what its frames never had: Santa's sleigh held in flight, by day and at night, and the
 * Halloween moon with its light at night (inventory I-934) -- the sleigh in two frames a pose apart, so the
 * reindeer's four poses are all in the atlas (v5.15C2, inventory I-975: one frame put two of them there). And since v5.16
 * Halloween with everything v5.16 added -- the Horseman in flight through his four poses (since v5.17 his rider in three
 * pieces, throwing), the moon laughing, the scarecrows and the decorations -- at night and at noon under the night by day.
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
        assertEquals("the sleigh and the reindeer's four poses in the sweep's atlas", 5, counted.sleighPiecesHeld)
        assertEquals(
            "the drawings of Halloween's things in the sweep's atlas: the Horseman's four poses, his rider's three pieces and " +
                "cape, the laughing moon, the scarecrows, the decorations",
            GlGolden.HALLOWEEN_PIECES.size, counted.halloweenPiecesHeld,
        )
    }
}
