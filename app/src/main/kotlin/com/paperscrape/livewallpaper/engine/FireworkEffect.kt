package com.paperscrape.livewallpaper.engine

import kotlin.random.Random

/**
 * Periodic firework bursts over the night sky. Purely decorative: [update] advances all active
 * bursts and starts new ones when appropriate (night + theme opt-in), [draw] renders them.
 *
 * **A burst is one sprite now, not eighteen dots.** It used to be a list of 18 `Particle`s per
 * burst, each an angle and a speed, redrawn as a `drawCircle` at an expanding radius with a
 * gravity droop -- 54 circles a frame at the three-burst ceiling, plus a `List` of 18 objects
 * allocated per spawn. The V2 asset set draws the burst as artwork, so the expansion is a
 * `canvas.scale` on the burst's own age and the fade is the blit's alpha.
 *
 * What that costs is the per-burst colour: the sprite carries its own gold-and-red palette, so
 * bursts no longer vary in hue. That is the trade the asset set makes, the same one the
 * skyscraper's window grid makes, and it is recorded rather than worked around.
 *
 * This class has no [android.content.Context] or [SpriteCache] access of its own, so the blit is
 * delegated back to the caller through [draw]'s `spriteDraw`, exactly as [SantaSleighEffect]
 * already does for the sleigh.
 */
class FireworkEffect {

    private class Burst(val x: Float, val y: Float) {
        var age = 0f
    }

    private val bursts = mutableListOf<Burst>()
    private var timeUntilNextSpawn = 2f

    fun update(deltaSeconds: Float, enabled: Boolean, screenWidth: Float, screenHeight: Float) {
        val iterator = bursts.iterator()
        while (iterator.hasNext()) {
            val b = iterator.next()
            b.age += deltaSeconds
            if (b.age > MAX_AGE) iterator.remove()
        }

        if (!enabled) return
        timeUntilNextSpawn -= deltaSeconds
        if (timeUntilNextSpawn <= 0f && bursts.size < MAX_CONCURRENT) {
            spawn(screenWidth, screenHeight)
            timeUntilNextSpawn = 3.5f + Random.nextFloat() * 4f
        }
    }

    private fun spawn(screenWidth: Float, screenHeight: Float) {
        val x = screenWidth * (0.15f + Random.nextFloat() * 0.7f)
        val y = screenHeight * (0.12f + Random.nextFloat() * 0.28f)
        bursts.add(Burst(x, y))
    }

    /**
     * @param spriteDraw receives each live burst's centre, the scale to draw it at (0 at the
     *   instant it goes off, 1 fully expanded) and its fade alpha (1 down to 0). Called once per
     *   burst per frame, only while bursts are alive.
     */
    fun draw(spriteDraw: (x: Float, y: Float, scale: Float, alpha: Float) -> Unit) {
        for (b in bursts) {
            val t = (b.age / MAX_AGE).coerceIn(0f, 1f)
            // The old particles started at the centre and ran outward, so the burst read as
            // opening rather than appearing. A scale that starts near zero reproduces that; the
            // floor stops the first frame being an invisible zero-area blit.
            val scale = MIN_SCALE + (1f - MIN_SCALE) * t
            spriteDraw(b.x, b.y, scale, 1f - t)
        }
    }

    companion object {
        private const val MAX_AGE = 1.4f
        private const val MAX_CONCURRENT = 3

        /** Scale at the instant a burst goes off, before it expands. */
        private const val MIN_SCALE = 0.15f
    }
}
