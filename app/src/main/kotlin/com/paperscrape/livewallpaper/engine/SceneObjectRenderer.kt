package com.paperscrape.livewallpaper.engine

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.core.graphics.ColorUtils
import kotlin.math.sin

/**
 * Geometry snapshot for one hill layer, computed by [PaperRenderer] each frame and shared with
 * this renderer so static objects (dog, house, tree) scroll in perfect sync with the ground
 * they're anchored to.
 */
data class LayerGeometry(
    val layer: Int,
    val shiftXWrapped: Float, // same wrapped parallax shift used to draw the hill silhouette
    val tileWidth: Float,     // 2x screen width, see PaperRenderer.buildHillPath
    val groundY: Float,       // y coordinate objects should sit on for this layer
)

private class StaticRuntime(val spec: StaticSceneObject) {
    var reactionTimer = 0f // >0 while a tap reaction animation is playing
    val idleSeed = (spec.tileFractionX * 97f) % 6.28f
}

private class CarRuntime(val spec: CarObject) {
    var progress = -spec.startDelaySeconds // negative = still waiting to start
    var honking = 0f
}

class SceneObjectRenderer(
    layout: SceneObjectLayout,
    private val customization: SceneCustomization = SceneCustomization.DEFAULT,
) {

    private val staticRuntimes = layout.staticObjects
        .filter { spec -> customization.keepCandidate(spec) }
        .map { StaticRuntime(it) }
    private val carRuntimes = layout.cars
        .filter { spec -> customization.keepCar(spec) }
        .map { CarRuntime(it) }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0x33000000
    }
    private val path = Path()
    private var lastScreenWidth = 0f
    private var lastScreenHeight = 0f

    companion object {
        /** All scene elements were sized too small at 1x — this doubles houses, buildings,
         * animals, trees, cars, and the road they drive on, uniformly. */
        const val GLOBAL_OBJECT_SCALE = 2f
    }

    // Base paper colors for animals/houses — intentionally theme-agnostic "cut paper" tones
    // so any color theme reads correctly; only the house window glow uses a warm/cool blend.
    private val dogSpotColor = 0xFFEFE0CE.toInt()
    private val treeTrunkColor = 0xFF7A4B2E.toInt()

    // Seasonal / festive object colors (step 2)
    private val snowColor = 0xFFF7FAFC.toInt()
    private val snowShadeColor = 0xFFD6E2EC.toInt()
    private val giftColors = intArrayOf(0xFFC1443B.toInt(), 0xFF3F7A5C.toInt(), 0xFF3D5A9E.toInt())
    private val ribbonColor = 0xFFF2C230.toInt()
    private val palmTrunkColor = 0xFF9C7A4A.toInt()
    private val parasolPoleColor = 0xFFEFE0CE.toInt()
    private val skyscraperWindowLit = 0xFFFFE79A.toInt()
    private val skyscraperWindowDark = 0xFF2E323C.toInt()
    private val penguinBodyColor = 0xFF2B2B33.toInt()
    private val penguinBellyColor = 0xFFF3F7FB.toInt()
    private val penguinBeakColor = 0xFFF2A65A.toInt()
    private val balloonColors = intArrayOf(0xFFC1443B.toInt(), 0xFFF2C230.toInt(), 0xFF3D5A9E.toInt(), 0xFF3F9E6B.toInt())

    // Easter theme colors
    private val easterEggColors = intArrayOf(0xFFE87FA0.toInt(), 0xFFF2C230.toInt(), 0xFF7EC8E3.toInt(), 0xFFB39DDB.toInt())
    private val easterEggPatternColor = 0xFFFFFFFF.toInt()
    private val bunnyBodyColor = 0xFFF3EAE0.toInt()
    private val bunnyInnerEarColor = 0xFFEFA8B8.toInt()

    // Road (drawn under any cars the theme has)
    private val roadColorDay = 0xFF5B5650.toInt()
    private val roadColorNight = 0xFF29271F.toInt()
    private val roadEdgeColor = 0xFF3D3A33.toInt()
    private val roadLineColor = 0xFFF3E6D0.toInt()

    fun update(deltaSeconds: Float) {
        for (r in staticRuntimes) {
            if (r.reactionTimer > 0f) r.reactionTimer = (r.reactionTimer - deltaSeconds).coerceAtLeast(0f)
        }
        for (c in carRuntimes) {
            c.progress += deltaSeconds * c.spec.speedFraction
            if (c.progress > 1.3f) c.progress = -0.3f // loop with a small off-screen buffer
            if (c.honking > 0f) c.honking = (c.honking - deltaSeconds).coerceAtLeast(0f)
        }
    }

    /** Returns the tapped object's type, if any, and starts its reaction animation. */
    fun tryHandleTap(x: Float, y: Float, layers: Map<Int, LayerGeometry>): SceneObjectType? {
        for (r in staticRuntimes) {
            if (!r.spec.tappable) continue
            val geom = layers[r.spec.layer] ?: continue
            val (objX, objY) = anchorPosition(r.spec, geom)
            val hitRadius = 46f * r.spec.scale
            val dx = x - objX
            val dy = y - (objY - hitRadius * 0.6f)
            if (dx * dx + dy * dy <= hitRadius * hitRadius) {
                r.reactionTimer = 0.6f
                return r.spec.type
            }
        }
        for (c in carRuntimes) {
            if (c.progress < -0.05f || c.progress > 1.05f) continue
            val carX = currentCarX(c, lastScreenWidth)
            val carY = c.spec.laneYFraction * lastScreenHeight
            val dx = x - carX
            val dy = y - (carY - 20f)
            if (dx * dx + dy * dy <= 46f * 46f) {
                c.honking = 0.5f
                return SceneObjectType.CAR
            }
        }
        return null
    }

    private fun currentCarX(c: CarRuntime, screenWidth: Float): Float {
        val margin = 120f
        val travel = screenWidth + margin * 2f
        val rawX = c.progress * travel - margin
        return if (c.spec.reverse) screenWidth - rawX else rawX
    }

    private fun anchorPosition(spec: StaticSceneObject, geom: LayerGeometry): Pair<Float, Float> {
        var x = geom.shiftXWrapped + spec.tileFractionX * geom.tileWidth
        if (x < -geom.tileWidth * 0.5f) x += geom.tileWidth
        return x to geom.groundY
    }

    fun draw(canvas: Canvas, layers: Map<Int, LayerGeometry>, dayBlend: Float, elapsedSeconds: Float, screenWidth: Float, screenHeight: Float) {
        lastScreenWidth = screenWidth
        lastScreenHeight = screenHeight
        for (r in staticRuntimes.sortedBy { it.spec.layer }) {
            val geom = layers[r.spec.layer] ?: continue
            val (x, y) = anchorPosition(r.spec, geom)
            // Draw neighboring tile copies too so objects near the wrap seam never pop in/out.
            drawStaticObject(canvas, r, x, y, dayBlend, elapsedSeconds)
            drawStaticObject(canvas, r, x - geom.tileWidth, y, dayBlend, elapsedSeconds)
            drawStaticObject(canvas, r, x + geom.tileWidth, y, dayBlend, elapsedSeconds)
        }

        drawRoad(canvas, dayBlend, screenWidth, screenHeight)

        for (c in carRuntimes) {
            if (c.progress < -0.05f || c.progress > 1.05f) continue
            drawCar(canvas, c, screenWidth, screenHeight, dayBlend)
        }
    }

    /**
     * Draws a compact row of sample objects (house, building, dog, tree) colored from this
     * renderer's current [customization] -- independent of the normal layered-scene/parallax
     * machinery, so the settings screen can show an immediate, faithful (same drawing code as
     * the real wallpaper) live preview without needing a full scene around it. Cars and parasols
     * are left out of this compact preview (their drawing code depends on lane/road-position and
     * multi-wedge geometry that doesn't suit a small static row) — their colors are still fully
     * live on the actual wallpaper.
     */
    fun drawPreviewPair(canvas: Canvas, screenWidth: Float, screenHeight: Float, dayBlend: Float) {
        val houseRuntime = StaticRuntime(StaticSceneObject(SceneObjectType.HOUSE, layer = 0, tileFractionX = 0f))
        val buildingRuntime = StaticRuntime(StaticSceneObject(SceneObjectType.SKYSCRAPER, layer = 0, tileFractionX = 1f))
        val dogRuntime = StaticRuntime(StaticSceneObject(SceneObjectType.DOG, layer = 0, tileFractionX = 0.5f, scale = 0.7f))
        val treeRuntime = StaticRuntime(StaticSceneObject(SceneObjectType.TREE, layer = 0, tileFractionX = 0.25f, scale = 0.6f))

        canvas.save()
        canvas.translate(screenWidth * 0.20f, screenHeight * 0.88f)
        drawHouse(canvas, houseRuntime, dayBlend)
        canvas.restore()

        canvas.save()
        canvas.translate(screenWidth * 0.45f, screenHeight * 0.94f)
        drawTree(canvas, treeRuntime, elapsed = 0f, dayBlend = dayBlend)
        canvas.restore()

        canvas.save()
        canvas.translate(screenWidth * 0.65f, screenHeight * 0.98f)
        drawDog(canvas, dogRuntime, elapsed = 0f, dayBlend = dayBlend)
        canvas.restore()

        canvas.save()
        canvas.translate(screenWidth * 0.85f, screenHeight * 0.96f)
        drawSkyscraper(canvas, buildingRuntime, dayBlend, 0f)
        canvas.restore()
    }

    /**
     * A simple two-lane road band spanning the full screen width at the cars' lane height.
     * Like the cars themselves, it's independent of home-screen parallax (it belongs to the
     * "road" the cars drive on, not to any particular hill layer).
     */
    private fun drawRoad(canvas: Canvas, dayBlend: Float, screenWidth: Float, screenHeight: Float) {
        if (carRuntimes.isEmpty()) return

        val laneYs = carRuntimes.map { it.spec.laneYFraction * screenHeight }
        val top = laneYs.min() - 10f * GLOBAL_OBJECT_SCALE
        val bottom = laneYs.max() + 24f * GLOBAL_OBJECT_SCALE

        fillPaint.color = ColorUtils.blendARGB(roadColorNight, roadColorDay, dayBlend.coerceIn(0f, 1f))
        canvas.drawRect(0f, top, screenWidth, bottom, fillPaint)

        strokePaint.style = Paint.Style.STROKE
        strokePaint.color = roadEdgeColor
        strokePaint.strokeWidth = 2f * GLOBAL_OBJECT_SCALE
        canvas.drawLine(0f, top, screenWidth, top, strokePaint)
        canvas.drawLine(0f, bottom, screenWidth, bottom, strokePaint)

        // Dashed center line separating the two lanes.
        val midY = (top + bottom) / 2f
        strokePaint.color = roadLineColor
        strokePaint.strokeWidth = 4f * GLOBAL_OBJECT_SCALE
        val dashLen = 26f * GLOBAL_OBJECT_SCALE
        val gapLen = 20f * GLOBAL_OBJECT_SCALE
        var x = 0f
        while (x < screenWidth) {
            canvas.drawLine(x, midY, (x + dashLen).coerceAtMost(screenWidth), midY, strokePaint)
            x += dashLen + gapLen
        }
    }

    private fun drawStaticObject(canvas: Canvas, r: StaticRuntime, x: Float, y: Float, dayBlend: Float, elapsed: Float) {
        if (x < -200f || x > 3000f) return // cheap off-screen skip
        canvas.save()
        canvas.translate(x, y)
        canvas.scale(r.spec.scale * GLOBAL_OBJECT_SCALE, r.spec.scale * GLOBAL_OBJECT_SCALE)

        // Reaction "ease" is shared by both reaction styles: a smooth 0->1->0 bump over the
        // 0.6s window right after a tap.
        val reactionEase = if (r.reactionTimer > 0f) {
            sin(((0.6f - r.reactionTimer) / 0.6f).coerceIn(0f, 1f) * Math.PI.toFloat())
        } else 0f

        // Swaying objects (trees, snowman) react by amplifying their own ambient wobble/sway
        // instead of hopping — a snowman "jumping" would look wrong, so the boost is passed
        // into their draw functions instead of a generic vertical translate.
        val isSwayReactor = r.spec.type == SceneObjectType.TREE ||
            r.spec.type == SceneObjectType.SNOWMAN ||
            r.spec.type == SceneObjectType.PALM_TREE
        if (!isSwayReactor) {
            canvas.translate(0f, -reactionEase * 18f)
        }

        when (r.spec.type) {
            SceneObjectType.DOG -> drawDog(canvas, r, elapsed, dayBlend)
            SceneObjectType.HOUSE -> drawHouse(canvas, r, dayBlend)
            SceneObjectType.TREE -> drawTree(canvas, r, elapsed, reactionEase, dayBlend)
            SceneObjectType.SNOWMAN -> drawSnowman(canvas, r, elapsed, reactionEase)
            SceneObjectType.GIFT -> drawGift(canvas, r)
            SceneObjectType.PALM_TREE -> drawPalmTree(canvas, r, elapsed, reactionEase, dayBlend)
            SceneObjectType.PARASOL -> drawParasol(canvas, r, elapsed, dayBlend)
            SceneObjectType.SKYSCRAPER -> drawSkyscraper(canvas, r, dayBlend, elapsed)
            SceneObjectType.PENGUIN -> drawPenguin(canvas, r, elapsed)
            SceneObjectType.BALLOON -> drawBalloon(canvas, r, elapsed)
            SceneObjectType.EASTER_EGG -> drawEasterEgg(canvas, r)
            SceneObjectType.BUNNY -> drawBunny(canvas, r, elapsed)
            SceneObjectType.CAR -> Unit // cars are drawn separately via drawCar()
        }
        canvas.restore()
    }

    private fun drawDog(canvas: Canvas, r: StaticRuntime, elapsed: Float, dayBlend: Float) {
        val wag = sin(elapsed * 5f + r.idleSeed) * 10f
        fillPaint.color = customization.colorFor(r.spec, dayBlend)
        canvas.drawOval(RectF(-30f, -34f, 30f, -4f), fillPaint)
        canvas.drawCircle(26f, -30f, 14f, fillPaint)
        path.reset(); path.moveTo(32f, -42f); path.lineTo(40f, -50f); path.lineTo(26f, -44f); path.close()
        canvas.drawPath(path, fillPaint)
        for (lx in floatArrayOf(-20f, -6f, 8f, 20f)) {
            canvas.drawRoundRect(RectF(lx - 3f, -8f, lx + 3f, 4f), 2f, 2f, fillPaint)
        }
        path.reset()
        path.moveTo(-28f, -26f)
        path.quadTo(-42f + wag, -30f, -40f + wag, -14f)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 6f
        strokePaint.color = customization.colorFor(r.spec, dayBlend)
        canvas.drawPath(path, strokePaint)
        strokePaint.strokeWidth = 2.5f
        strokePaint.color = 0x33000000
        fillPaint.color = dogSpotColor
        canvas.drawCircle(-6f, -22f, 6f, fillPaint)
    }

    private fun drawHouse(canvas: Canvas, r: StaticRuntime, dayBlend: Float) {
        val wallColor = customization.colorFor(r.spec, dayBlend)
        fillPaint.color = wallColor
        canvas.drawRect(RectF(-36f, -46f, 36f, 0f), fillPaint)
        // Roof is a darkened version of the wall color, so any user-picked house color still
        // reads as coherent instead of clashing with a fixed, unrelated roof tone.
        fillPaint.color = ColorUtils.blendARGB(wallColor, 0xFF1A1410.toInt(), 0.45f)
        path.reset(); path.moveTo(-44f, -46f); path.lineTo(0f, -78f); path.lineTo(44f, -46f); path.close()
        canvas.drawPath(path, fillPaint)
        fillPaint.color = treeTrunkColor
        canvas.drawRect(RectF(-8f, -26f, 8f, 0f), fillPaint)
        val nightGlow = (1f - dayBlend).coerceIn(0f, 1f)
        fillPaint.color = ColorUtils.blendARGB(0xFFB9CBD9.toInt(), 0xFFFFE79A.toInt(), nightGlow)
        canvas.drawRect(RectF(-30f, -40f, -16f, -28f), fillPaint)
        canvas.drawRect(RectF(16f, -40f, 30f, -28f), fillPaint)
    }

    private fun drawTree(canvas: Canvas, r: StaticRuntime, elapsed: Float, reactionBoost: Float = 0f, dayBlend: Float = 1f) {
        val sway = sin(elapsed * 1.1f + r.idleSeed) * (4f + reactionBoost * 16f)
        fillPaint.color = treeTrunkColor
        canvas.drawRect(RectF(-5f, -38f, 5f, 0f), fillPaint)
        fillPaint.color = customization.colorFor(r.spec, dayBlend)
        canvas.save()
        canvas.translate(0f, -40f)
        canvas.rotate(sway)
        canvas.drawCircle(-14f, -6f, 20f, fillPaint)
        canvas.drawCircle(14f, -6f, 20f, fillPaint)
        canvas.drawCircle(0f, -22f, 22f, fillPaint)
        canvas.restore()
    }

    private fun drawSnowman(canvas: Canvas, r: StaticRuntime, elapsed: Float, reactionBoost: Float = 0f) {
        val wobble = sin(elapsed * 1.4f + r.idleSeed) * (2f + reactionBoost * 12f)
        canvas.save()
        canvas.rotate(wobble)
        fillPaint.color = snowColor
        canvas.drawCircle(0f, -14f, 20f, fillPaint) // base
        canvas.drawCircle(0f, -42f, 15f, fillPaint) // torso
        canvas.drawCircle(0f, -64f, 11f, fillPaint) // head
        fillPaint.color = snowShadeColor
        canvas.drawCircle(-6f, -10f, 3f, fillPaint)
        canvas.drawCircle(4f, -38f, 3f, fillPaint)
        // carrot nose
        path.reset(); path.moveTo(11f, -64f); path.lineTo(24f, -62f); path.lineTo(11f, -60f); path.close()
        fillPaint.color = 0xFFE0703A.toInt()
        canvas.drawPath(path, fillPaint)
        // twig arms
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 3f
        strokePaint.color = 0xFF7A4B2E.toInt()
        canvas.drawLine(-15f, -44f, -32f, -54f, strokePaint)
        canvas.drawLine(15f, -44f, 30f, -50f, strokePaint)
        // scarf
        fillPaint.color = 0xFFC1443B.toInt()
        canvas.drawRect(RectF(-12f, -54f, 12f, -48f), fillPaint)
        canvas.restore()
    }

    private fun drawGift(canvas: Canvas, r: StaticRuntime) {
        val color = giftColors[(kotlin.math.abs(r.spec.tileFractionX * 1000).toInt()) % giftColors.size]
        fillPaint.color = color
        canvas.drawRect(RectF(-20f, -30f, 20f, 0f), fillPaint)
        fillPaint.color = ribbonColor
        canvas.drawRect(RectF(-4f, -30f, 4f, 0f), fillPaint)
        canvas.drawRect(RectF(-20f, -18f, 20f, -12f), fillPaint)
        path.reset()
        path.moveTo(0f, -30f); path.lineTo(-10f, -42f); path.lineTo(-2f, -30f); path.close()
        canvas.drawPath(path, fillPaint)
        path.reset()
        path.moveTo(0f, -30f); path.lineTo(10f, -42f); path.lineTo(2f, -30f); path.close()
        canvas.drawPath(path, fillPaint)
    }

    private fun drawPalmTree(canvas: Canvas, r: StaticRuntime, elapsed: Float, reactionBoost: Float = 0f, dayBlend: Float = 1f) {
        val sway = sin(elapsed * 0.9f + r.idleSeed) * (6f + reactionBoost * 18f)
        fillPaint.color = palmTrunkColor
        path.reset()
        path.moveTo(-6f, 0f)
        path.quadTo(-10f + sway * 0.3f, -30f, 2f + sway, -62f)
        path.lineTo(8f + sway, -62f)
        path.quadTo(0f + sway * 0.3f, -30f, 6f, 0f)
        path.close()
        canvas.drawPath(path, fillPaint)

        fillPaint.color = customization.colorFor(r.spec, dayBlend)
        canvas.save()
        canvas.translate(4f + sway, -62f)
        for (i in 0 until 5) {
            canvas.save()
            canvas.rotate(-60f + i * 30f + sway)
            path.reset()
            path.moveTo(0f, 0f)
            path.quadTo(20f, -6f, 34f, 4f)
            path.quadTo(18f, 2f, 0f, 0f)
            path.close()
            canvas.drawPath(path, fillPaint)
            canvas.restore()
        }
        canvas.restore()
    }

    private fun drawParasol(canvas: Canvas, r: StaticRuntime, elapsed: Float, dayBlend: Float) {
        val bob = sin(elapsed * 1.6f + r.idleSeed) * 1.5f
        fillPaint.color = parasolPoleColor
        canvas.drawRect(RectF(-2.5f, -50f, 2.5f, 0f), fillPaint)
        canvas.save()
        canvas.translate(0f, -50f + bob)
        val sweep = 36
        for (i in 0 until 5) {
            fillPaint.color = customization.parasolStripeColor(i, dayBlend)
            path.reset()
            path.moveTo(0f, 0f)
            path.arcTo(RectF(-34f, -34f, 34f, 34f), (180f + i * sweep).toFloat(), sweep.toFloat())
            path.close()
            canvas.drawPath(path, fillPaint)
        }
        canvas.restore()
    }

    private fun drawSkyscraper(canvas: Canvas, r: StaticRuntime, dayBlend: Float, elapsed: Float) {
        val height = 130f * r.spec.scale
        val width = 46f
        fillPaint.color = customization.colorFor(r.spec, dayBlend)
        canvas.drawRect(RectF(-width / 2f, -height, width / 2f, 0f), fillPaint)

        val nightGlow = (1f - dayBlend).coerceIn(0f, 1f)
        val cols = 3
        val rows = (height / 18f).toInt().coerceAtLeast(2)
        for (row in 0 until rows) {
            for (col in 0 until cols) {
                // Deterministic per-window flicker seed so windows don't all light at once.
                val seed = (row * 31 + col * 17 + (r.spec.tileFractionX * 1000).toInt())
                val lit = nightGlow > 0.35f && (seed % 5 != 0)
                fillPaint.color = if (lit) skyscraperWindowLit else skyscraperWindowDark
                val wx = -width / 2f + 8f + col * ((width - 16f) / (cols - 1))
                val wy = -height + 16f + row * 18f
                if (wy > -8f) continue
                canvas.drawRect(RectF(wx - 4f, wy, wx + 4f, wy + 8f), fillPaint)
            }
        }
    }

    private fun drawPenguin(canvas: Canvas, r: StaticRuntime, elapsed: Float) {
        val waddle = sin(elapsed * 4f + r.idleSeed) * 4f
        canvas.save()
        canvas.rotate(waddle * 0.5f)
        fillPaint.color = penguinBodyColor
        canvas.drawOval(RectF(-16f, -46f, 16f, 0f), fillPaint)
        fillPaint.color = penguinBellyColor
        canvas.drawOval(RectF(-9f, -38f, 9f, -4f), fillPaint)
        fillPaint.color = penguinBeakColor
        path.reset(); path.moveTo(-6f, -40f); path.lineTo(0f, -46f); path.lineTo(6f, -40f); path.close()
        canvas.drawPath(path, fillPaint)
        fillPaint.color = penguinBeakColor
        canvas.drawRect(RectF(-10f, 0f, -3f, 4f), fillPaint)
        canvas.drawRect(RectF(3f, 0f, 10f, 4f), fillPaint)
        canvas.restore()
    }

    private fun drawEasterEgg(canvas: Canvas, r: StaticRuntime) {
        val colorIndex = (kotlin.math.abs(r.spec.tileFractionX * 1000).toInt()) % easterEggColors.size
        fillPaint.color = easterEggColors[colorIndex]
        canvas.drawOval(RectF(-16f, -40f, 16f, 0f), fillPaint)
        // simple decorative stripe pattern
        fillPaint.color = easterEggPatternColor
        canvas.drawOval(RectF(-16f, -26f, 16f, -18f), fillPaint)
        canvas.drawCircle(-8f, -10f, 4f, fillPaint)
        canvas.drawCircle(8f, -10f, 4f, fillPaint)
    }

    private fun drawBunny(canvas: Canvas, r: StaticRuntime, elapsed: Float) {
        val earWiggle = sin(elapsed * 3f + r.idleSeed) * 6f
        fillPaint.color = bunnyBodyColor
        canvas.drawOval(RectF(-18f, -26f, 18f, 0f), fillPaint) // body
        canvas.drawCircle(14f, -30f, 12f, fillPaint) // head
        // ears
        canvas.save()
        canvas.translate(10f, -40f)
        canvas.rotate(-8f + earWiggle * 0.3f)
        canvas.drawOval(RectF(-4f, -22f, 4f, 0f), fillPaint)
        fillPaint.color = bunnyInnerEarColor
        canvas.drawOval(RectF(-2f, -18f, 2f, -3f), fillPaint)
        canvas.restore()
        fillPaint.color = bunnyBodyColor
        canvas.save()
        canvas.translate(18f, -40f)
        canvas.rotate(8f - earWiggle * 0.3f)
        canvas.drawOval(RectF(-4f, -22f, 4f, 0f), fillPaint)
        fillPaint.color = bunnyInnerEarColor
        canvas.drawOval(RectF(-2f, -18f, 2f, -3f), fillPaint)
        canvas.restore()
        // tail
        fillPaint.color = 0xFFFFFFFF.toInt()
        canvas.drawCircle(-16f, -6f, 5f, fillPaint)
    }

    private fun drawBalloon(canvas: Canvas, r: StaticRuntime, elapsed: Float) {
        val bob = sin(elapsed * 1.3f + r.idleSeed) * 8f
        val colorIndex = (kotlin.math.abs(r.spec.tileFractionX * 1000).toInt()) % balloonColors.size
        canvas.save()
        canvas.translate(0f, bob - 40f)
        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeWidth = 2f
        strokePaint.color = 0x55000000
        canvas.drawLine(0f, 18f, 0f, 58f, strokePaint)
        fillPaint.color = balloonColors[colorIndex]
        canvas.drawOval(RectF(-16f, -18f, 16f, 18f), fillPaint)
        path.reset()
        path.moveTo(-4f, 17f); path.lineTo(4f, 17f); path.lineTo(0f, 24f); path.close()
        canvas.drawPath(path, fillPaint)
        canvas.restore()
    }

    private fun drawCar(canvas: Canvas, c: CarRuntime, screenWidth: Float, screenHeight: Float, dayBlend: Float) {
        val margin = 120f
        val travel = screenWidth + margin * 2f
        val rawX = c.progress * travel - margin
        val x = if (c.spec.reverse) screenWidth - rawX else rawX
        val y = c.spec.laneYFraction * screenHeight
        val dir = if (c.spec.reverse) -1f else 1f

        canvas.save()
        canvas.translate(x, y)
        canvas.scale(dir * GLOBAL_OBJECT_SCALE, GLOBAL_OBJECT_SCALE)

        fillPaint.color = if (c.honking > 0f) 0xFFFFF3B0.toInt() else customization.colorFor(c.spec, dayBlend)
        val body = RectF(-34f, -22f, 34f, 0f)
        canvas.drawRoundRect(body, 6f, 6f, fillPaint)
        path.reset()
        path.moveTo(-18f, -22f)
        path.lineTo(-8f, -36f)
        path.lineTo(16f, -36f)
        path.lineTo(24f, -22f)
        path.close()
        canvas.drawPath(path, fillPaint)

        fillPaint.color = 0xFFDCEFFA.toInt()
        canvas.drawRect(RectF(-4f, -34f, 13f, -23f), fillPaint)

        fillPaint.color = 0xFF2B2B2B.toInt()
        canvas.drawCircle(-18f, 0f, 8f, fillPaint)
        canvas.drawCircle(18f, 0f, 8f, fillPaint)

        canvas.restore()
    }
}
