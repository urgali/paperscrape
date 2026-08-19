package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
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

    private data class FallingGift(
        val x: Float,
        val startY: Float,
        val targetY: Float,
        val fallDuration: Float,
        var age: Float = 0f,
        val colorIndex: Int,
    ) {
        /** Eased (progress²) fall -- starts slow right after being tossed, accelerates like
         * gravity, and lands exactly on [targetY] after [fallDuration], regardless of screen
         * size (both Y values are already absolute pixel positions computed from the actual
         * screen height at spawn time). */
        val y: Float
            get() {
                val progress = (age / fallDuration).coerceIn(0f, 1f)
                return startY + (targetY - startY) * progress * progress
            }
    }

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
    private val giftBowShape = SceneShape(8)

    private val reindeerColor = 0xFF7A4B2E.toInt()
    private val antlerColor = 0xFF5A3A22.toInt()
    private val sleighColor = 0xFFB5342A.toInt()
    private val santaCoatColor = 0xFFC1443B.toInt()
    private val santaFaceColor = 0xFFF0C79A.toInt()
    private val santaTrimColor = 0xFFF7FAFC.toInt()
    private val giftBoxColors = intArrayOf(0xFFC1443B.toInt(), 0xFF3D5A9E.toInt(), 0xFF3F7A5C.toInt())
    private val giftRibbonColor = 0xFFF2C230.toInt()

    fun update(deltaSeconds: Float, enabled: Boolean, screenWidth: Float, screenHeight: Float) {
        // Falling gifts keep animating even if the flight itself is disabled mid-drop.
        val giftIterator = fallingGifts.iterator()
        while (giftIterator.hasNext()) {
            val g = giftIterator.next()
            g.age += deltaSeconds
            if (g.age >= g.fallDuration) giftIterator.remove()
        }

        if (!enabled) {
            flying = false
            return
        }

        if (flying) {
            flightProgress += deltaSeconds / flightDuration
            giftDropTimer -= deltaSeconds
            if (giftDropTimer <= 0f && flightProgress in 0.1f..0.9f) {
                spawnFallingGift(screenWidth, screenHeight)
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

    private fun spawnFallingGift(screenWidth: Float, screenHeight: Float) {
        val x = currentX(screenWidth)
        // Falls all the way down to roughly curb/house level -- matching the same
        // road-safe placement band static houses use (see SceneObjectCatalog's row
        // comments in SceneObject.kt) -- rather than vanishing on a fixed timer/speed that
        // left it stranded high in the sky regardless of how tall the screen actually is.
        val targetY = screenHeight * (0.83f + Random.nextFloat() * 0.03f)
        fallingGifts.add(
            FallingGift(
                x = x,
                startY = flightY + 10f,
                targetY = targetY,
                fallDuration = 1.6f + Random.nextFloat() * 0.5f,
                colorIndex = Random.nextInt(giftBoxColors.size),
            ),
        )
    }

    private fun currentX(screenWidth: Float): Float {
        val margin = 160f
        val travel = screenWidth + margin * 2f
        val raw = flightProgress * travel - margin
        return if (reverse) screenWidth - raw else raw
    }

    /** Fades in over the first ~8% of the flight and back out over the last ~8%, instead of the
     * group just vanishing the instant it crosses off-canvas. */
    private fun edgeFadeAlpha(progress: Float): Float {
        val fadeZone = 0.08f
        return when {
            progress < fadeZone -> (progress / fadeZone).coerceIn(0f, 1f)
            progress > 1f - fadeZone -> ((1f - progress) / fadeZone).coerceIn(0f, 1f)
            else -> 1f
        }
    }

    /** Combines a base ARGB color with an extra alpha multiplier. Used instead of
     * `Canvas.saveLayer` for the edge fade -- saveLayer allocates an offscreen buffer on every
     * call, which is expensive enough to cause visible stutter when done every frame while the
     * sleigh is flying. Multiplying each paint's own alpha is essentially free by comparison. */
    private fun fadeColor(color: Int, alpha: Float): Int {
        val baseAlpha = (color ushr 24) and 0xFF
        val newAlpha = (baseAlpha * alpha).toInt().coerceIn(0, 255)
        return (newAlpha shl 24) or (color and 0x00FFFFFF)
    }

    /**
     * Aesthetic-pass batch 5: the sleigh+reindeer+Santa group itself is now a single sprite
     * blit (`santa_sleigh_scene.png`) instead of the vector paths this used to draw every frame
     * -- but this class has no [android.content.Context]/[SpriteCache] access of its own, so
     * rather than plumbing that all the way in here, the actual bitmap draw is delegated back
     * to the caller (which already has it) via [spriteDraw]. Falling gifts stay vector here --
     * they're cheap (one rect + a ribbon cross + two small triangles, only while any are
     * actually falling) and their per-gift fade/rotation state lives entirely in this class.
     *
     * [spriteDraw] receives the sleigh's current center (x, y), its facing direction (+1/-1,
     * already accounting for [reverse]), and its current fade alpha (0..1) -- called once per
     * frame only while actually flying and not fully faded out.
     */
    fun draw(canvas: SceneCanvas, elapsedSeconds: SceneTime, screenWidth: Float, spriteDraw: (x: Float, y: Float, dir: Float, alpha: Float) -> Unit) {
        for (g in fallingGifts) {
            val progress = (g.age / g.fallDuration).coerceIn(0f, 1f)
            // Fades in quickly right after being thrown, stays solid through the flight, then
            // fades out over the last bit of the fall so it reads as "arriving" near the house
            // rather than blinking out.
            val fade = when {
                progress < 0.08f -> progress / 0.08f
                progress > 0.85f -> ((1f - progress) / 0.15f).coerceIn(0f, 1f)
                else -> 1f
            }
            drawFallingGift(canvas, g, fade)
        }

        if (!flying) return
        val x = currentX(screenWidth)
        // The V2 sleigh faces left -- the reindeer are drawn at the left end of the sprite and
        // pull away from the sleigh -- so the unflipped blit is the leftward-travelling one. The
        // sign was not touched when the artwork was replaced, which is why Santa flew backwards.
        val dir = if (reverse) 1f else -1f
        val bob = elapsedSeconds.sinAt(3f) * 4f
        val fadeAlpha = edgeFadeAlpha(flightProgress)
        if (fadeAlpha <= 0.01f) return // fully faded out, nothing to draw
        spriteDraw(x, flightY + bob, dir, fadeAlpha)
    }

    private fun drawFallingGift(canvas: SceneCanvas, g: FallingGift, fade: Float) {
        canvas.save()
        canvas.translate(g.x, g.y)
        canvas.rotate(g.age * 90f)
        fillPaint.color = fadeColor(giftBoxColors[g.colorIndex], fade)
        canvas.drawRect(-11f, -11f, 11f, 11f, fillPaint)
        // Ribbon cross + a small bow on top, matching the static gift-under-the-tree look --
        // a flat, unadorned square read as a bomb/mine rather than a wrapped present.
        fillPaint.color = fadeColor(giftRibbonColor, fade)
        canvas.drawRect(-2f, -11f, 2f, 11f, fillPaint)
        canvas.drawRect(-11f, -2f, 11f, 2f, fillPaint)
        giftBowShape.moveTo(0f, -11f); giftBowShape.lineTo(-5f, -17f); giftBowShape.lineTo(-1f, -11f); giftBowShape.close()
        canvas.drawShape(giftBowShape, fillPaint)
        giftBowShape.moveTo(0f, -11f); giftBowShape.lineTo(5f, -17f); giftBowShape.lineTo(1f, -11f); giftBowShape.close()
        canvas.drawShape(giftBowShape, fillPaint)
        canvas.restore()
    }

}
