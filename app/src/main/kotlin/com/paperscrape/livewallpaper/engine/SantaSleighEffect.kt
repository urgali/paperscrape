package com.paperscrape.livewallpaper.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import kotlin.math.sin
import kotlin.random.Random

/**
 * Every so often (random interval), Santa's sleigh — pulled by two reindeer — glides across
 * the upper sky, dropping a little trail of falling gifts as it goes. Purely decorative and
 * self-contained, following the same "periodic event" pattern as [FireworkEffect].
 *
 * Drawn as a flat paper-cutout silhouette (matching the rest of the scene's art style) rather
 * than a detailed illustration — small, warm, and legible at wallpaper scale.
 */
class SantaSleighEffect {

    private data class FallingGift(var x: Float, var y: Float, val vy: Float, var age: Float = 0f)

    private var flying = false
    private var flightProgress = 0f // 0..1 across the screen
    private var flightDuration = 10f
    private var flightY = 0f
    private var reverse = false
    private var timeUntilNextFlight = 8f
    private var giftDropTimer = 0f
    private val fallingGifts = mutableListOf<FallingGift>()

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF3D2B1F.toInt()
    }
    private val path = Path()

    private val reindeerColor = 0xFF7A4B2E.toInt()
    private val antlerColor = 0xFF5A3A22.toInt()
    private val sleighColor = 0xFFB5342A.toInt()
    private val sackColor = 0xFF3F7A5C.toInt()
    private val santaCoatColor = 0xFFC1443B.toInt()
    private val santaFaceColor = 0xFFF0C79A.toInt()
    private val santaTrimColor = 0xFFF7FAFC.toInt()
    private val giftColors = intArrayOf(0xFFF2C230.toInt(), 0xFF3D5A9E.toInt(), 0xFF3F9E6B.toInt())

    fun update(deltaSeconds: Float, enabled: Boolean, screenWidth: Float, screenHeight: Float) {
        // Falling gifts keep animating even if the flight itself is disabled mid-drop.
        val giftIterator = fallingGifts.iterator()
        while (giftIterator.hasNext()) {
            val g = giftIterator.next()
            g.age += deltaSeconds
            g.y += g.vy * deltaSeconds
            if (g.age > 2.5f || g.y > screenHeight) giftIterator.remove()
        }

        if (!enabled) {
            flying = false
            return
        }

        if (flying) {
            flightProgress += deltaSeconds / flightDuration
            giftDropTimer -= deltaSeconds
            if (giftDropTimer <= 0f && flightProgress in 0.1f..0.9f) {
                spawnFallingGift(screenWidth)
                giftDropTimer = 0.9f + Random.nextFloat() * 0.6f
            }
            if (flightProgress >= 1f) {
                flying = false
                timeUntilNextFlight = 25f + Random.nextFloat() * 35f // next flight in ~25-60s
            }
        } else {
            timeUntilNextFlight -= deltaSeconds
            if (timeUntilNextFlight <= 0f) {
                startFlight(screenHeight)
            }
        }
    }

    private fun startFlight(screenHeight: Float) {
        flying = true
        flightProgress = 0f
        flightDuration = 9f + Random.nextFloat() * 4f
        flightY = screenHeight * (0.10f + Random.nextFloat() * 0.16f)
        reverse = Random.nextBoolean()
        giftDropTimer = 1.2f
    }

    private fun spawnFallingGift(screenWidth: Float) {
        val x = currentX(screenWidth)
        fallingGifts.add(FallingGift(x = x, y = flightY + 10f, vy = 70f + Random.nextFloat() * 30f))
    }

    private fun currentX(screenWidth: Float): Float {
        val margin = 160f
        val travel = screenWidth + margin * 2f
        val raw = flightProgress * travel - margin
        return if (reverse) screenWidth - raw else raw
    }

    fun draw(canvas: Canvas, elapsedSeconds: Float, screenWidth: Float) {
        for (g in fallingGifts) {
            val fade = (1f - (g.age / 2.5f)).coerceIn(0f, 1f)
            fillPaint.color = giftColors[((g.x + g.y).toInt()) % giftColors.size]
            fillPaint.alpha = (255 * fade).toInt().coerceIn(0, 255)
            canvas.save()
            canvas.translate(g.x, g.y)
            canvas.rotate(g.age * 90f)
            canvas.drawRect(RectF(-12f, -12f, 12f, 12f), fillPaint)
            canvas.restore()
        }

        if (!flying) return
        val x = currentX(screenWidth)
        val dir = if (reverse) -1f else 1f
        val bob = sin(elapsedSeconds * 3f) * 4f

        canvas.save()
        canvas.translate(x, flightY + bob)
        canvas.scale(dir * 2f, 2f)
        drawReindeer(canvas, offsetX = -150f, elapsedSeconds)
        drawReindeer(canvas, offsetX = -95f, elapsedSeconds)
        drawSleighAndSanta(canvas)
        canvas.restore()
    }

    private fun drawReindeer(canvas: Canvas, offsetX: Float, elapsedSeconds: Float) {
        val gallop = sin(elapsedSeconds * 8f + offsetX) * 6f
        canvas.save()
        canvas.translate(offsetX, gallop * 0.2f)

        fillPaint.color = reindeerColor
        canvas.drawOval(RectF(-20f, -8f, 20f, 8f), fillPaint) // body
        canvas.drawCircle(22f, -6f, 8f, fillPaint) // head
        // antlers
        strokePaint.color = antlerColor
        strokePaint.strokeWidth = 2f
        canvas.drawLine(26f, -12f, 32f, -20f, strokePaint)
        canvas.drawLine(26f, -12f, 20f, -20f, strokePaint)
        canvas.drawLine(29f, -16f, 34f, -14f, strokePaint)
        // legs (galloping)
        strokePaint.color = reindeerColor
        strokePaint.strokeWidth = 3f
        canvas.drawLine(-14f, 6f, -18f + gallop, 18f, strokePaint)
        canvas.drawLine(10f, 6f, 14f - gallop, 18f, strokePaint)
        // harness line back to the sleigh
        strokePaint.color = 0xFF5A3A22.toInt()
        strokePaint.strokeWidth = 1.5f
        canvas.drawLine(-20f, 0f, -40f, 2f, strokePaint)
        canvas.restore()
    }

    private fun drawSleighAndSanta(canvas: Canvas) {
        // Sleigh body: a simple curved paper-cutout runner shape.
        fillPaint.color = sleighColor
        path.reset()
        path.moveTo(-14f, 6f)
        path.quadTo(-20f, -14f, -6f, -14f)
        path.lineTo(16f, -14f)
        path.quadTo(24f, -14f, 22f, 2f)
        path.quadTo(20f, 10f, 10f, 10f)
        path.lineTo(-10f, 10f)
        path.quadTo(-16f, 10f, -14f, 6f)
        path.close()
        canvas.drawPath(path, fillPaint)

        // Runner curl at the front.
        strokePaint.color = sleighColor
        strokePaint.strokeWidth = 3f
        path.reset()
        path.moveTo(16f, 8f)
        path.quadTo(26f, 8f, 24f, -4f)
        canvas.drawPath(path, strokePaint)

        // Gift sack peeking out the back.
        fillPaint.color = sackColor
        canvas.drawOval(RectF(-10f, -22f, 4f, -8f), fillPaint)

        // Santa: round head + coat, sitting, one arm raised mid-throw.
        fillPaint.color = santaCoatColor
        canvas.drawOval(RectF(-2f, -24f, 14f, -6f), fillPaint) // coat/torso
        fillPaint.color = santaFaceColor
        canvas.drawCircle(9f, -26f, 6f, fillPaint) // head
        fillPaint.color = santaTrimColor
        canvas.drawCircle(9f, -31f, 5f, fillPaint) // hat pom + brim base
        fillPaint.color = santaCoatColor
        path.reset()
        path.moveTo(4f, -31f)
        path.quadTo(9f, -42f, 16f, -33f)
        path.close()
        canvas.drawPath(path, fillPaint) // hat cone
        fillPaint.color = santaTrimColor
        canvas.drawCircle(15f, -33f, 2.5f, fillPaint) // pom-pom

        // Raised, waving/throwing arm.
        strokePaint.color = santaCoatColor
        strokePaint.strokeWidth = 4f
        canvas.drawLine(11f, -20f, 20f, -30f, strokePaint)
        fillPaint.color = santaFaceColor
        canvas.drawCircle(20f, -30f, 2.5f, fillPaint) // hand

        // Small smile.
        strokePaint.color = 0xFF7A4A2E.toInt()
        strokePaint.strokeWidth = 1.5f
        canvas.drawLine(7f, -24f, 10f, -23f, strokePaint)
    }
}
