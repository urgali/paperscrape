package com.paperscrape.livewallpaper.engine

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws one frame of the paper-cutout landscape into the given canvas.
 *
 * The scene is built from:
 *  - a vertical sky gradient that blends across day/night phases
 *  - a scattering of stars (visible at night only, twinkling)
 *  - a sun or moon disc following an arc across the sky
 *  - N layers of "paper" hills, each with its own parallax speed and a soft drop shadow,
 *    which together create the classic layered paper-cutout look.
 *
 * The renderer is stateless between frames except for the star field, which is generated
 * once per screen size and reused (so stars don't jump around every frame).
 */
class PaperRenderer(
    private var screenWidth: Int,
    private var screenHeight: Int,
) {
    var theme: SceneTheme = ThemeCatalog.SUNSET
    var homeScreenOffset: Float = 0f // 0..1 across all home screen pages
    var parallaxStrength: Float = 1f
    var sceneCustomization: SceneCustomization = SceneCustomization.DEFAULT

    private val skyPaint = Paint()
    private val celestialPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val starPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val hillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x22000000
    }

    private data class Star(val x: Float, val y: Float, val radius: Float, val phase: Float)

    private var stars: List<Star> = emptyList()

    // Cached, unshifted hill silhouettes — one per layer, rebuilt only when the theme or screen
    // size changes (see rebuildHillPathsIfNeeded). Parallax is then applied purely via
    // canvas.translate() at draw time, which is essentially free, instead of recomputing every
    // control point with fresh Random() calls on every single frame. That per-frame rebuild was
    // the main source of stutter during home-screen swipes, when CPU is already busy with the
    // launcher's own transition animation.
    private val baseHillPaths = arrayOfNulls<Path>(3)
    private var cachedPathsThemeId: String? = null
    private var cachedPathsWidth = -1
    private var cachedPathsHeight = -1

    private var objectRenderer = SceneObjectRenderer(SceneObjectCatalog.layoutFor(theme.id, theme.accentColor), sceneCustomization)
    private var objectRendererThemeId = theme.id
    private var objectRendererGeneration = CustomThemeRegistry.generation()
    private var objectRendererConfig = sceneCustomization
    private val layerGeometries = HashMap<Int, LayerGeometry>()
    private val fireworkEffect = FireworkEffect()
    private val santaSleighEffect = SantaSleighEffect()

    private fun syncObjectRendererWithTheme() {
        val currentGeneration = CustomThemeRegistry.generation()
        if (objectRendererThemeId != theme.id ||
            objectRendererGeneration != currentGeneration ||
            objectRendererConfig != sceneCustomization
        ) {
            objectRenderer = SceneObjectRenderer(SceneObjectCatalog.layoutFor(theme.id, theme.accentColor), sceneCustomization)
            objectRendererThemeId = theme.id
            objectRendererGeneration = currentGeneration
            objectRendererConfig = sceneCustomization
        }
    }

    // Layer configuration: baseHeightFraction = how tall the layer is relative to screen height,
    // parallaxFactor = how much the layer shifts with home-screen scrolling (farther = slower).
    private val layerCount = 3
    private val parallaxFactors = floatArrayOf(0.15f, 0.35f, 0.6f)
    private val heightFractions = floatArrayOf(0.34f, 0.30f, 0.27f)
    private val yOffsets = floatArrayOf(0.50f, 0.60f, 0.70f) // top of each layer, as fraction of height

    // Deterministic per-layer "noise" seed so the silhouette shape is stable across frames
    // but different per layer/theme.
    private fun layerSeed(layer: Int): Long = (theme.id.hashCode().toLong() * 31 + layer)

    fun onSizeChanged(width: Int, height: Int) {
        screenWidth = width
        screenHeight = height
        regenerateStars()
    }

    private fun regenerateStars() {
        val rnd = Random(42)
        stars = List(70) {
            Star(
                x = rnd.nextFloat() * screenWidth,
                y = rnd.nextFloat() * screenHeight * 0.55f,
                radius = 1f + rnd.nextFloat() * 1.8f,
                phase = rnd.nextFloat() * 6.28f,
            )
        }
    }

    fun draw(canvas: Canvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: Float, deltaSeconds: Float) {
        if (stars.isEmpty()) regenerateStars()
        syncObjectRendererWithTheme()
        rebuildHillPathsIfNeeded()
        drawSky(canvas, dayPhase)
        drawStars(canvas, dayPhase, elapsedSeconds)
        drawCelestialBody(canvas, dayPhase)
        drawHillLayers(canvas, dayPhase)
        objectRenderer.update(deltaSeconds)
        objectRenderer.draw(canvas, layerGeometries, dayPhase.dayBlend, elapsedSeconds, screenWidth.toFloat(), screenHeight.toFloat())

        val fireworksEnabled = theme.hasFireworks && dayPhase.dayBlend < 0.35f
        fireworkEffect.update(deltaSeconds, fireworksEnabled, screenWidth.toFloat(), screenHeight.toFloat())
        fireworkEffect.draw(canvas)

        santaSleighEffect.update(deltaSeconds, theme.hasSantaSleigh, screenWidth.toFloat(), screenHeight.toFloat())
        santaSleighEffect.draw(canvas, elapsedSeconds, screenWidth.toFloat())
    }

    /** Forwards a tap to the scene objects (dog, penguin, gift, car...) so the caller can play a reaction sound. */
    fun handleTap(x: Float, y: Float): SceneObjectType? = objectRenderer.tryHandleTap(x, y, layerGeometries)

    private fun blendColor(night: Int, day: Int, blend: Float): Int =
        ColorUtils.blendARGB(night, day, blend.coerceIn(0f, 1f))

    private fun drawSky(canvas: Canvas, dayPhase: SunPositionCalculator.DayPhase) {
        // Pick the two reference palettes to blend between based on progress within the day.
        val (topNight, botNight) = theme.skyNight[0] to theme.skyNight.getOrElse(1) { theme.skyNight[0] }
        val (topDawnDusk, botDawnDusk) = if (dayPhase.progress < 0.5f) {
            theme.skyDawn[0] to theme.skyDawn.getOrElse(1) { theme.skyDawn[0] }
        } else {
            theme.skyDusk[0] to theme.skyDusk.getOrElse(1) { theme.skyDusk[0] }
        }
        val (topDay, botDay) = theme.skyDay[0] to theme.skyDay.getOrElse(1) { theme.skyDay[0] }

        // Blend night -> twilight -> day using dayBlend, with a twilight bump near the terminator.
        val twilightWeight = (1f - kotlin.math.abs(dayPhase.dayBlend * 2f - 1f)).coerceIn(0f, 1f)
        val nightToTwilightTop = blendColor(topNight, topDawnDusk, dayPhase.dayBlend.coerceIn(0f, 1f))
        val top = blendColor(nightToTwilightTop, topDay, (dayPhase.dayBlend - twilightWeight * 0.3f).coerceIn(0f, 1f))
        val nightToTwilightBot = blendColor(botNight, botDawnDusk, dayPhase.dayBlend.coerceIn(0f, 1f))
        val bottom = blendColor(nightToTwilightBot, botDay, (dayPhase.dayBlend - twilightWeight * 0.3f).coerceIn(0f, 1f))

        skyPaint.shader = LinearGradient(
            0f, 0f, 0f, screenHeight.toFloat(),
            top, bottom, Shader.TileMode.CLAMP
        )
        canvas.drawRect(0f, 0f, screenWidth.toFloat(), screenHeight.toFloat(), skyPaint)
    }

    private fun drawStars(canvas: Canvas, dayPhase: SunPositionCalculator.DayPhase, elapsedSeconds: Float) {
        val visibility = (1f - dayPhase.dayBlend * 1.6f).coerceIn(0f, 1f)
        if (visibility <= 0f) return
        for (star in stars) {
            val twinkle = 0.5f + 0.5f * sin(elapsedSeconds * 1.5f + star.phase)
            starPaint.color = theme.starColor
            starPaint.alpha = (255 * visibility * twinkle).toInt().coerceIn(0, 255)
            canvas.drawCircle(star.x, star.y, star.radius, starPaint)
        }
    }

    private fun drawCelestialBody(canvas: Canvas, dayPhase: SunPositionCalculator.DayPhase) {
        val margin = screenWidth * 0.12f
        val cx = margin + dayPhase.celestialX * (screenWidth - 2 * margin)
        val horizonY = screenHeight * 0.62f
        val riseHeight = screenHeight * 0.42f
        val cy = horizonY - dayPhase.celestialY * riseHeight

        val radius = screenWidth * 0.055f * 2f // doubled -- was too small to read clearly
        val color = if (dayPhase.isSunVisible) theme.sunColor else theme.moonColor

        celestialPaint.shader = RadialGradient(
            cx, cy, radius * 3.2f,
            ColorUtils.setAlphaComponent(color, 90),
            ColorUtils.setAlphaComponent(color, 0),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 3.2f, celestialPaint)
        celestialPaint.shader = null

        if (dayPhase.isSunVisible) {
            celestialPaint.color = color
            canvas.drawCircle(cx, cy, radius, celestialPaint)
        } else {
            drawMoonWithPhase(canvas, cx, cy, radius, color)
        }
    }

    /**
     * Renders the moon disc following its real phase (new/crescent/quarter/gibbous/full) rather
     * than a plain circle, using the classic "half-disc + variable-width terminator ellipse"
     * technique: the ellipse's horizontal half-width is `radius * |cos(phaseAngle)|`, which
     * naturally collapses to a hairline at the quarters (correct: exact half-moon) and to the
     * full radius at new/full moon (correct: fully dark / fully round).
     */
    private fun drawMoonWithPhase(canvas: Canvas, cx: Float, cy: Float, radius: Float, litColor: Int) {
        val phase = SunPositionCalculator.moonPhase()
        val angle = phase * 2f * kotlin.math.PI.toFloat()
        val cosA = kotlin.math.cos(angle)
        val illuminated = (1f - cosA) / 2f
        val darkColor = ColorUtils.blendARGB(litColor, 0xFF10101A.toInt(), 0.82f)

        // Always-visible faint dark disc (the unlit hemisphere, like real earthshine).
        celestialPaint.color = darkColor
        canvas.drawCircle(cx, cy, radius, celestialPaint)
        if (illuminated <= 0.001f) return // new moon: nothing further to draw

        val waxing = phase < 0.5f
        val halfDisc = Path().apply {
            moveTo(cx, cy - radius)
            if (waxing) {
                arcTo(RectF(cx - radius, cy - radius, cx + radius, cy + radius), -90f, 180f) // right half
            } else {
                arcTo(RectF(cx - radius, cy - radius, cx + radius, cy + radius), -90f, -180f) // left half
            }
            close()
        }

        val bulgeHalfWidth = radius * kotlin.math.abs(cosA)
        val bulgeOval = RectF(cx - bulgeHalfWidth, cy - radius, cx + bulgeHalfWidth, cy + radius)
        val isCrescent = (waxing && phase < 0.25f) || (!waxing && phase > 0.75f)

        celestialPaint.color = litColor
        canvas.drawPath(halfDisc, celestialPaint)
        celestialPaint.color = if (isCrescent) darkColor else litColor
        canvas.drawOval(bulgeOval, celestialPaint)
    }

    private fun rebuildHillPathsIfNeeded() {
        if (cachedPathsThemeId == theme.id && cachedPathsWidth == screenWidth && cachedPathsHeight == screenHeight) {
            return
        }
        for (layer in 0 until layerCount) {
            val layerTop = screenHeight * yOffsets[layer]
            val layerHeight = screenHeight * heightFractions[layer]
            val path = baseHillPaths[layer] ?: Path().also { baseHillPaths[layer] = it }
            buildBaseHillPath(path, layer, layerTop, layerHeight)
        }
        cachedPathsThemeId = theme.id
        cachedPathsWidth = screenWidth
        cachedPathsHeight = screenHeight
    }

    private fun drawHillLayers(canvas: Canvas, dayPhase: SunPositionCalculator.DayPhase) {
        val hillColors = theme.hillColorsDay
        val hillColorsNight = theme.hillColorsNight

        for (layer in 0 until layerCount) {
            val color = blendColor(
                hillColorsNight.getOrElse(layer) { hillColorsNight.last() },
                hillColors.getOrElse(layer) { hillColors.last() },
                dayPhase.dayBlend,
            )

            val parallax = parallaxFactors[layer] * parallaxStrength
            val shiftX = -homeScreenOffset * screenWidth * parallax
            val layerTop = screenHeight * yOffsets[layer]
            val layerHeight = screenHeight * heightFractions[layer]

            val tileWidth = screenWidth * 2f
            var wrappedShift = shiftX % tileWidth
            if (wrappedShift > 0f) wrappedShift -= tileWidth

            // Objects use their own, *screen-width* tiling period here -- deliberately decoupled
            // from the hill's wider tileWidth above. Hills want a wide period for non-repetitive
            // organic shape variety, but objects need every tileFractionX to map to a distinct,
            // non-overlapping on-screen position so they're all visible together at rest (not
            // "half of them, depending on scroll position", which was the bug in the version
            // before this one, and folding the wide hill period down to one screen -- the first
            // attempted fix -- caused *different* tileFractionX values to collide/overlap
            // instead, since e.g. 0.10 and 0.60 are exactly one screen-width apart in the wide
            // period and would land on the same pixel once folded).
            var objectShiftWrapped = shiftX % screenWidth
            if (objectShiftWrapped > 0f) objectShiftWrapped -= screenWidth

            layerGeometries[layer] = LayerGeometry(
                layer = layer,
                shiftXWrapped = objectShiftWrapped,
                tileWidth = screenWidth,
                groundY = layerTop + layerHeight * 0.40f,
            )

            val path = baseHillPaths[layer] ?: continue

            // Soft drop shadow: draw a slightly-offset darker copy underneath for a "cut paper" feel.
            canvas.save()
            canvas.translate(wrappedShift, 6f)
            shadowPaint.alpha = 30
            canvas.drawPath(path, shadowPaint)
            canvas.restore()

            canvas.save()
            canvas.translate(wrappedShift, 0f)
            hillPaint.color = color
            canvas.drawPath(path, hillPaint)
            canvas.restore()
        }
    }

    /**
     * Builds a smooth, seeded "hill skyline" path for one layer, wide enough to cover two
     * screen-widths, anchored at the wrappedShift=0 reference position. Parallax scrolling is
     * applied later via canvas.translate() rather than baked into the path coordinates, so this
     * only needs to run once per theme/size change instead of every frame.
     */
    private fun buildBaseHillPath(path: Path, layer: Int, top: Float, height: Float) {
        path.reset()
        val rnd = Random(layerSeed(layer))
        val width = screenWidth * 2f
        val segments = 6
        val segmentWidth = width / segments
        val startX = -screenWidth * 0.5f

        path.moveTo(startX, top + height)
        path.lineTo(startX, top + height * (0.55f + rnd.nextFloat() * 0.2f))

        var x = startX
        var prevY = top + height * (0.55f + rnd.nextFloat() * 0.2f)
        repeat(segments + 2) {
            val nextX = x + segmentWidth
            val peakY = top + height * (0.15f + rnd.nextFloat() * 0.55f)
            val ctrl1X = x + segmentWidth * 0.35f
            val ctrl2X = x + segmentWidth * 0.65f
            path.cubicTo(ctrl1X, prevY, ctrl2X, peakY, nextX, peakY)
            prevY = peakY
            x = nextX
        }

        path.lineTo(x, top + height)
        path.close()
    }
}
