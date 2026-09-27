package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The count behind the settings screen's "PaperScrape is your wallpaper" (assessment v5.7, row 13).
 *
 * What the class decides is small and is all here: a picker's preview is not the wallpaper, and the
 * answer is "yes" for exactly as long as one engine that is not a preview is alive. Whether the
 * engines report at all is the service's two lines, and whether the count agrees with what the phone
 * shows was measured on the BV6600 in its three states -- ours bound, another wallpaper bound, and
 * ours recorded after a force-stop with no engine attached (ROADMAP row A20).
 */
class WallpaperEngineCensusTest {

    @Test
    fun `nothing running is not the wallpaper`() {
        assertFalse(EngineCensus().isTheWallpaper.value)
    }

    @Test
    fun `a picker's preview engine is not the wallpaper`() {
        val census = EngineCensus()
        census.engineCreated(isPreview = true)
        assertFalse("a preview draws a preview, not the phone's wallpaper", census.isTheWallpaper.value)
        census.engineDestroyed(isPreview = true)
        assertFalse(census.isTheWallpaper.value)
    }

    @Test
    fun `an engine that is not a preview is the wallpaper until it is destroyed`() {
        val census = EngineCensus()
        census.engineCreated(isPreview = false)
        assertTrue(census.isTheWallpaper.value)
        // The picker's preview comes and goes beside it; the answer does not move.
        census.engineCreated(isPreview = true)
        census.engineDestroyed(isPreview = true)
        assertTrue("closing the preview does not unset the wallpaper", census.isTheWallpaper.value)
        census.engineDestroyed(isPreview = false)
        assertFalse(census.isTheWallpaper.value)
    }

    @Test
    fun `two wallpaper engines -- home and a replacement arriving -- stay yes until the last goes`() {
        val census = EngineCensus()
        census.engineCreated(isPreview = false)
        census.engineCreated(isPreview = false)
        census.engineDestroyed(isPreview = false)
        assertTrue(census.isTheWallpaper.value)
        census.engineDestroyed(isPreview = false)
        assertFalse(census.isTheWallpaper.value)
        // A stray destroy cannot drive the count below zero and leave the next engine unseen.
        census.engineDestroyed(isPreview = false)
        census.engineCreated(isPreview = false)
        assertTrue(census.isTheWallpaper.value)
    }
}
