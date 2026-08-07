package com.paperscrape.livewallpaper.engine

import android.graphics.Canvas
import android.graphics.Paint
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * A handful of expanding, fading dots that mimic a paper-confetti firework burst.
 * Purely decorative: [update] advances all active bursts, [draw] renders them,
 * [maybeSpawn] periodically starts a new one when appropriate (night + theme opt-in).
 */
class FireworkEffect {

    private data class Particle(val angle: Float, val speed: Float)
    private class Burst(val x: Float, val y: Float, val color: Int, val particles: List<Particle>) {
        var age = 0f
    }

    private val bursts = mutableListOf<Burst>()
    private var timeUntilNextSpawn = 2f
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    private val palette = intArrayOf(
        0xFFF2C230.toInt(), 0xFFC1443B.toInt(), 0xFF3D5A9E.toInt(), 0xFF3F9E6B.toInt(), 0xFFFFFFFF.toInt(),
    )

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
        val color = palette[Random.nextInt(palette.size)]
        val particles = List(18) {
            Particle(
                angle = (it / 18f) * (2f * Math.PI.toFloat()) + Random.nextFloat() * 0.3f,
                speed = 60f + Random.nextFloat() * 60f,
            )
        }
        bursts.add(Burst(x, y, color, particles))
    }

    fun draw(canvas: Canvas) {
        for (b in bursts) {
            val t = (b.age / MAX_AGE).coerceIn(0f, 1f)
            val fade = (1f - t)
            val radius = t * 90f
            paint.color = b.color
            paint.alpha = (255 * fade).toInt().coerceIn(0, 255)
            for (p in b.particles) {
                val px = b.x + cos(p.angle) * radius * (p.speed / 90f)
                val py = b.y + sin(p.angle) * radius * (p.speed / 90f) + t * t * 40f // slight gravity droop
                canvas.drawCircle(px, py, 3.5f * fade + 1f, paint)
            }
        }
    }

    companion object {
        private const val MAX_AGE = 1.4f
        private const val MAX_CONCURRENT = 3
    }
}
