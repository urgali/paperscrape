package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.BURST_CAP
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.CREAM_COLOUR
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.FLOWER_CENTRE
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.FLOWER_HOLD
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.FLOWER_LAYERS
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.FLOWER_TURN
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.GOLD
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.KIND_WILLOW
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.LAYER_IN
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.LAYER_OUT
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.LAYER_PETALS
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.LAYER_WIDTH
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.PIECES
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.RISE
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.SPARK
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.SPARK_HEAD
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.STYLE_FLOWER
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.STYLE_LIGHT
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.STYLE_PAPER
import com.paperscrape.livewallpaper.engine.FireworkEffect.Companion.TWO_PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Draws [FireworkEffect]'s rockets and bursts, frame by frame, from what the effect holds.
 *
 * **Two kinds of mark and one glow, all in the frame's one batch on the GPU**: discs of the atlas ([SpriteBlitter.drawDisc],
 * `deco_bulb`, 6 corners each) and four-point shapes ([SceneCanvas.drawShape], convex, 6 corners each) for the rockets,
 * the pieces, the sparks' streaks and the flower's petals; and, for the sparks with light, two radial glows
 * ([SceneCanvas.drawFadingGlow]: on the GPU a fan from the atlas's white point): no `drawCircle`, no texture of its own, no other blend.
 *
 * **The glows are drawn at one place and one size** ([GLOW_RADIUS] round the origin) and moved and scaled onto the burst
 * by the canvas: on a Canvas a glow is a gradient from `GradientShaderCache`, keyed by its centre, radius and colour (the
 * fading alpha is the paint's, [SceneCanvas.drawFadingGlow]), so a glow keyed by where a burst happens to be would be a new
 * gradient for every burst; keyed by colour alone, the shows' glows are fourteen gradients built once (the sky's and the
 * halo's tone of each of the six paper colours and of the willow's gold). Their alpha moves in steps of
 * [GLOW_ALPHA_STEP], so the drawing matches the videos approved for decision 118.
 *
 * Nothing is allocated while drawing: the paint and the shape are built once, the effect's arrays are read by index and
 * the alpha is set with [setAlphaWithoutAllocating].
 */
internal class FireworkDrawer(private val sprites: SpriteBlitter) {

    private val shape = SceneShape(8)
    private val corners = FloatArray(8)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    fun draw(canvas: SceneCanvas, fx: FireworkEffect) {
        val unit = fx.unit
        for (b in 0 until BURST_CAP) {
            if (!fx.burstLive[b]) continue
            if (fx.burstRocket[b]) {
                drawRocket(canvas, fx, b, unit)
                continue
            }
            when (fx.burstStyle[b]) {
                STYLE_FLOWER -> drawFlower(canvas, fx, b)
                STYLE_PAPER -> drawPaper(canvas, fx, b, unit)
                else -> drawSparks(canvas, fx, b, unit, fx.burstStyle[b] == STYLE_LIGHT)
            }
        }
    }

    /** Where the rocket is at [u] of its rise (0..1): fast off the ground, slowing to the top. */
    private fun rocketY(fx: FireworkEffect, b: Int, u: Float): Float {
        val e = 1f - (1f - u) * (1f - u)
        return fx.burstStartY[b] + (fx.burstY[b] - fx.burstStartY[b]) * e
    }

    private fun drawRocket(canvas: SceneCanvas, fx: FireworkEffect, b: Int, unit: Float) {
        val age = fx.burstAge[b]
        val u = (age / RISE).coerceIn(0f, 1f)
        val rot = fx.burstRotation[b]
        val x = fx.burstX[b] + sin(age * ROCKET_WOBBLE_RATE + rot) * ROCKET_WOBBLE * unit
        val y = rocketY(fx, b, u)
        val style = fx.burstStyle[b]
        if (style == STYLE_PAPER || style == STYLE_FLOWER) {
            // Paper: a cream head and five bits of its trail, smaller and fainter below.
            for (k in ROCKET_TRAIL downTo 1) {
                val back = k * ROCKET_TRAIL_STEP
                val yk = rocketY(fx, b, (u - back).coerceAtLeast(0f)) + k * 2f * unit
                val xk = fx.burstX[b] + sin((age - back) * ROCKET_WOBBLE_RATE + rot) * ROCKET_WOBBLE * unit
                disc(canvas, xk, yk, (3f - k * 0.38f) * unit, CREAM_COLOUR, (210 - k * 34).coerceAtLeast(30))
            }
            disc(canvas, x, y, 3.2f * unit, CREAM_COLOUR, 255)
        } else {
            // Sparks: a streak from the head down, in the burst's colour, and the head.
            val c = fx.burstColour[b]
            val ty = rocketY(fx, b, (u - 0.16f).coerceAtLeast(0f)) + 10f * unit
            streak(canvas, x, y, fx.burstX[b], ty, 2.4f * unit, 0.6f * unit, SPARK[c], 150)
            disc(canvas, x, y, 2.3f * unit, SPARK_HEAD[c], 255)
        }
    }

    /** Full for the first half of a life, then down to nothing as the square of the rest. */
    private fun fade(age: Float, life: Float): Float {
        val h = age / life
        if (h >= 1f) return 0f
        val q = ((h - 0.5f) / 0.5f).coerceIn(0f, 1f)
        return 1f - q * q
    }

    private fun drawPaper(canvas: SceneCanvas, fx: FireworkEffect, b: Int, unit: Float) {
        val base = b * PIECES
        val age = fx.burstAge[b]
        val gravity = fx.burstGravity[b]
        for (k in 0 until fx.burstCount[b]) {
            val i = base + k
            val life = fx.pieceLife[i]
            val a = fade(age, life)
            if (a <= 0f) continue
            val r = PIECE_RADIUS * unit * fx.pieceSize[i] * (1f - 0.35f * age / life)
            val colour = FireworkEffect.paperColour(fx.pieceColour[i])
            val px = fx.pieceX[i]
            val py = fx.pieceY[i]
            val vx = fx.pieceVx[i]
            val vy = fx.pieceVy[i]
            // The trail: the same paper left behind along the way it came, smaller and fainter.
            for (t in fx.pieceTrail[i] downTo 1) {
                val back = t * TRAIL_STEP
                val tx = px - vx * back
                val ty = py - (vy - gravity * back * 0.5f) * back
                val tr = r * (1f - t * 0.14f).coerceAtLeast(0.35f)
                disc(canvas, tx, ty, tr, colour, (a * 255 * (1f - t * 0.2f).coerceAtLeast(0.2f)).toInt())
            }
            disc(canvas, px, py, r, colour, (a * 255).toInt())
        }
    }

    private fun drawSparks(canvas: SceneCanvas, fx: FireworkEffect, b: Int, unit: Float, light: Boolean) {
        val base = b * PIECES
        val age = fx.burstAge[b]
        if (light) {
            // The light: the sky round the burst lit for a moment, and a halo round it while it opens.
            val ci = if (fx.burstKind[b] == KIND_WILLOW) GOLD else fx.burstColour[b]
            val reach = fx.burstReach[b]
            if (age < SKY_GLOW_SECONDS) {
                val q = 1f - age / SKY_GLOW_SECONDS
                glow(canvas, fx.burstX[b], fx.burstY[b], reach * SKY_GLOW_REACH, SPARK_HEAD[ci], steppedAlpha(SKY_GLOW_ALPHA * q))
            }
            if (age < HALO_SECONDS) {
                val q = 1f - age / HALO_SECONDS
                glow(canvas, fx.burstX[b], fx.burstY[b], reach * HALO_REACH, SPARK[ci], steppedAlpha(HALO_ALPHA * q * q))
            }
        }
        for (k in 0 until fx.burstCount[b]) {
            val i = base + k
            val life = fx.pieceLife[i]
            val a = fade(age, life)
            if (a <= 0f) continue
            val ci = fx.pieceColour[i]
            val size = fx.pieceSize[i]
            val px = fx.pieceX[i]
            val py = fx.pieceY[i]
            val len = fx.pieceTrail[i].coerceAtLeast(1) * 0.045f + 0.06f
            val w = 2.8f * unit * size
            streak(canvas, px, py, px - fx.pieceVx[i] * len, py - fx.pieceVy[i] * len, w, w * 0.25f, SPARK[ci], (a * 190).toInt())
            // The crackle: in the last third the heads go off and on, each on its own beat.
            if (age / life > 0.62f) {
                val beat = ((age * 15f).toInt() + (fx.piecePhase[i] * 7f).toInt()) % 3
                if (beat == 0) continue
            }
            // The head in the burst's colour, a lighter core inside it.
            disc(canvas, px, py, 2.4f * unit * size, SPARK[ci], (a * 255).toInt())
            disc(canvas, px, py, 1.15f * unit * size, SPARK_HEAD[ci], (a * 255).toInt())
        }
    }

    private fun drawFlower(canvas: SceneCanvas, fx: FireworkEffect, b: Int) {
        val age = fx.burstAge[b]
        val reach = fx.burstReach[b]
        val cx = fx.burstX[b]
        val cy = fx.burstY[b]
        if (!fx.burstBroken[b]) {
            // Open, layer after layer with a little overshoot, and turning.
            val turn = fx.burstRotation[b] + FLOWER_TURN * age
            for (layer in 0 until FLOWER_LAYERS) {
                val o = easeOutBack(((age - layer * LAYER_DELAY) / FLOWER_OPEN_SECONDS).coerceIn(0f, 1f))
                if (o <= 0f) continue
                val count = LAYER_PETALS[layer]
                val step = TWO_PI / count
                val colour = FireworkEffect.paperColour(FireworkEffect.flowerLayerColour(fx.burstColour[b], fx.burstColour2[b], layer))
                for (k in 0 until count) {
                    val a = turn + step * k + layer * step * 0.5f
                    petal(canvas, cx, cy, a, reach * LAYER_IN[layer] * o, reach * LAYER_OUT[layer] * o, reach * LAYER_WIDTH[layer] * o, colour, 255)
                }
            }
            val o = easeOutBack((age / CENTRE_OPEN_SECONDS).coerceIn(0f, 1f))
            disc(canvas, cx, cy, reach * FLOWER_CENTRE * o, FireworkEffect.paperColour(fx.burstColour2[b]), 255)
            return
        }
        val t = age - FLOWER_HOLD
        val base = b * PIECES
        for (k in 0 until fx.burstCount[b]) {
            val i = base + k
            val a = fade(t, fx.pieceLife[i])
            if (a <= 0f) continue
            // A piece of paper turning as it falls: it spins, and seen edge on it narrows.
            val angle = fx.pieceAngle[i] + fx.pieceSpin[i] * t
            val flip = abs(cos(fx.piecePhase[i] + 3.4f * t)).coerceAtLeast(0.22f)
            val half = fx.pieceLength[i] * 0.5f
            petal(canvas, fx.pieceX[i], fx.pieceY[i], angle, -half, half, fx.pieceWidth[i] * flip, FireworkEffect.paperColour(fx.pieceColour[i]), (a * 255).toInt())
        }
    }

    private fun easeOutBack(x: Float): Float {
        if (x <= 0f) return 0f
        val y = x - 1f
        return 1f + (OVERSHOOT + 1f) * y * y * y + OVERSHOOT * y * y
    }

    private fun disc(canvas: SceneCanvas, cx: Float, cy: Float, r: Float, colour: Int, alpha: Int) {
        if (alpha <= 0 || r <= 0f) return
        sprites.drawDisc(canvas, cx, cy, r, colour, alpha.coerceAtMost(255))
    }

    /** A glow of [radius] round ([cx], [cy]): drawn at [GLOW_RADIUS] round the origin, moved and scaled there (see the class). */
    private fun glow(canvas: SceneCanvas, cx: Float, cy: Float, radius: Float, colour: Int, alpha: Int) {
        if (alpha <= 0 || radius <= 0f) return
        val s = radius / GLOW_RADIUS
        canvas.save()
        canvas.translate(cx, cy)
        canvas.scale(s, s)
        canvas.drawFadingGlow(0f, 0f, GLOW_RADIUS, colour, alpha)
        canvas.restore()
    }

    private fun steppedAlpha(alpha: Float): Int = ((alpha.toInt() + GLOW_ALPHA_STEP / 2) / GLOW_ALPHA_STEP) * GLOW_ALPHA_STEP

    /** A tapering streak from ([hx], [hy]), [headWidth] wide, to ([tx], [ty]), [tailWidth] wide: four points, convex. */
    @Suppress("LongParameterList")
    private fun streak(canvas: SceneCanvas, hx: Float, hy: Float, tx: Float, ty: Float, headWidth: Float, tailWidth: Float, colour: Int, alpha: Int) {
        if (alpha <= 0) return
        var dx = hx - tx
        var dy = hy - ty
        val l = sqrt(dx * dx + dy * dy)
        if (l < 0.5f) return
        dx /= l
        dy /= l
        val nx = -dy
        val ny = dx
        shape.moveTo(hx + nx * headWidth * 0.5f, hy + ny * headWidth * 0.5f)
        shape.lineTo(tx + nx * tailWidth * 0.5f, ty + ny * tailWidth * 0.5f)
        shape.lineTo(tx - nx * tailWidth * 0.5f, ty - ny * tailWidth * 0.5f)
        shape.lineTo(hx - nx * headWidth * 0.5f, hy - ny * headWidth * 0.5f)
        shape.close()
        fill(canvas, colour, alpha)
    }

    /** A petal ([FireworkEffect.petalCorners]): a four-point shape. */
    @Suppress("LongParameterList")
    private fun petal(canvas: SceneCanvas, cx: Float, cy: Float, angle: Float, rIn: Float, rOut: Float, halfWidth: Float, colour: Int, alpha: Int) {
        if (alpha <= 0 || halfWidth <= 0.05f) return
        FireworkEffect.petalCorners(cx, cy, angle, rIn, rOut, halfWidth, corners)
        shape.moveTo(corners[0], corners[1])
        shape.lineTo(corners[2], corners[3])
        shape.lineTo(corners[4], corners[5])
        shape.lineTo(corners[6], corners[7])
        shape.close()
        fill(canvas, colour, alpha)
    }

    private fun fill(canvas: SceneCanvas, colour: Int, alpha: Int) {
        paint.color = colour or OPAQUE
        paint.setAlphaWithoutAllocating(alpha)
        canvas.drawShape(shape, paint)
    }

    private companion object {
        const val OPAQUE = 0xFF shl 24

        /** A paper piece's radius at size 1, and how far back along its way each trail disc stands, in seconds. */
        const val PIECE_RADIUS = 3.8f
        const val TRAIL_STEP = 0.05f

        /** The rocket's sway (px at the reference width, and radians per second) and its paper trail. */
        const val ROCKET_WOBBLE = 1.2f
        const val ROCKET_WOBBLE_RATE = 19f
        const val ROCKET_TRAIL = 5
        const val ROCKET_TRAIL_STEP = 0.045f

        /** How long each layer of the flower takes to open, how far behind the one outside it, and the middle disc's. */
        const val FLOWER_OPEN_SECONDS = 0.42f
        const val LAYER_DELAY = 0.09f
        const val CENTRE_OPEN_SECONDS = 0.3f
        const val OVERSHOOT = 1.4f

        /** The halo round a burst with light: its reach (times the burst's), its life and its alpha at the start. */
        const val HALO_REACH = 1.45f
        const val HALO_SECONDS = 1.3f
        const val HALO_ALPHA = 120f

        /** The sky lit round it, wider and briefer. */
        const val SKY_GLOW_REACH = 3.2f
        const val SKY_GLOW_SECONDS = 0.7f
        const val SKY_GLOW_ALPHA = 50f

        /** The glows' one drawn radius, and the step their alpha moves in, as in the approved videos (see the class). */
        const val GLOW_RADIUS = 100f
        const val GLOW_ALPHA_STEP = 8
    }
}
