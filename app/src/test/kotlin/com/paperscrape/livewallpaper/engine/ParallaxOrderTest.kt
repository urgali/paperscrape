package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Farther layers slide slower** (v5.8C). The lake slid at 0.25 of the scroll, faster than the
 * hills in front of it (0.15): while scrolling its sparkles passed the land before them and the
 * water read as nearer than the hills (v5.8B comment audit).
 */
class ParallaxOrderTest {
    @Test
    fun `mountains, lake and hills slide in the order of their depth`() {
        val order = listOf(
            "back mountains" to PaperRenderer.MOUNTAINS_BACK_PARALLAX,
            "front mountains" to PaperRenderer.MOUNTAINS_FRONT_PARALLAX,
            "lake" to PaperRenderer.LAKE_PARALLAX,
            "hills" to PaperRenderer.HILL_PARALLAX,
        )
        for ((far, near) in order.zipWithNext()) {
            assertTrue("${far.first} (${far.second}) must slide slower than ${near.first} (${near.second})", far.second < near.second)
        }
    }
}
