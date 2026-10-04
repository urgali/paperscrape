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

    /**
     * Blits one burst: its centre, the scale to draw it at ([MIN_SCALE], 0.15, at the instant it
     * goes off, 1 fully expanded) and its fade alpha (1 down to 0).
     *
     * A `fun interface` with `Float` parameters rather than a `(Float, Float, Float, Float) -> Unit`:
     * a Kotlin function type is a generic `Function4`, and calling one boxes all four floats -- four
     * `Float` objects per burst per frame while fireworks were going off (v5.10A).
     */
    fun interface BurstBlit {
        fun blit(x: Float, y: Float, scale: Float, alpha: Float)
    }

    private class Burst(val x: Float, val y: Float) {
        var age = 0f
    }

    private val bursts = mutableListOf<Burst>()
    private var timeUntilNextSpawn = 2f

    fun update(deltaSeconds: Float, enabled: Boolean, screenWidth: Float, screenHeight: Float) {
        // By index, from the end so a removal does not skip the next burst: an iterator here was
        // an object every frame, bursts or none (v5.10A). `removeAt` keeps the others in order.
        for (i in bursts.size - 1 downTo 0) {
            val b = bursts[i]
            b.age += deltaSeconds
            if (b.age > MAX_AGE) bursts.removeAt(i)
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
     * @param spriteDraw blits each live burst (see [BurstBlit]). Called once per burst per frame,
     *   only while bursts are alive.
     */
    fun draw(spriteDraw: BurstBlit) {
        // By index: a `for (b in bursts)` builds an iterator every frame (v5.8C).
        for (i in bursts.indices) {
            val b = bursts[i]
            val t = (b.age / MAX_AGE).coerceIn(0f, 1f)
            // The old particles started at the centre and ran outward, so the burst read as
            // opening rather than appearing. A scale that starts near zero reproduces that; the
            // floor stops the first frame being an invisible zero-area blit.
            val scale = MIN_SCALE + (1f - MIN_SCALE) * t
            spriteDraw.blit(b.x, b.y, scale, 1f - t)
        }
    }

    companion object {
        private const val MAX_AGE = 1.4f
        private const val MAX_CONCURRENT = 3

        /** Scale at the instant a burst goes off, before it expands. */
        private const val MIN_SCALE = 0.15f
    }
}
