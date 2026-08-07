package com.paperscrape.livewallpaper.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path

/**
 * A tiny folded-paper bird that glides across the screen when the user taps the wallpaper.
 * Purely decorative and self-contained: spawn it, call [update] each frame, draw while [alive].
 */
class PaperBird(
    startX: Float,
    startY: Float,
    private val screenWidth: Float,
    private val color: Int,
) {
    private var x = startX
    private var y = startY
    private val direction = if (startX < screenWidth / 2f) 1f else -1f
    private val speedX = screenWidth * (0.10f + Math.random().toFloat() * 0.08f) * direction
    private val amplitude = 30f + Math.random().toFloat() * 40f
    private val bobSpeed = 2f + Math.random().toFloat() * 1.5f
    private var age = 0f
    private val maxAge = 6f // seconds

    val alive: Boolean get() = age < maxAge && x in -80f..(screenWidth + 80f)

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = this@PaperBird.color
        style = Paint.Style.FILL
    }
    private val path = Path()

    fun update(deltaSeconds: Float) {
        age += deltaSeconds
        x += speedX * deltaSeconds
        y += kotlin.math.sin(age * bobSpeed) * amplitude * deltaSeconds
    }

    fun draw(canvas: Canvas) {
        val fade = ((maxAge - age) / maxAge).coerceIn(0f, 1f)
        paint.alpha = (255 * fade).toInt()

        val wingFlap = kotlin.math.sin(age * 12f) * 10f
        val size = 22f

        path.reset()
        // Simple chevron "paper crane" silhouette made from two triangles.
        path.moveTo(x, y)
        path.lineTo(x - size * direction, y - size / 2f - wingFlap)
        path.lineTo(x - size * 0.3f * direction, y)
        path.close()
        canvas.drawPath(path, paint)

        path.reset()
        path.moveTo(x, y)
        path.lineTo(x - size * direction, y + size / 2f + wingFlap)
        path.lineTo(x - size * 0.3f * direction, y)
        path.close()
        canvas.drawPath(path, paint)
    }
}
