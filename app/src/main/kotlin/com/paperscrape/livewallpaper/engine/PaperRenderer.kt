package com.paperscrape.livewallpaper.engine

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
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
    private val hillPath = Path()

    private var objectRenderer = SceneObjectRenderer(SceneObjectCatalog.layoutFor(theme.id, theme.accentColor))
    private var objectRendererThemeId = theme.id
    private val layerGeometries = HashMap<Int, LayerGeometry>()
    private val fireworkEffect = FireworkEffect()
    private val santaSleighEffect = SantaSleighEffect()

    private fun syncObjectRendererWithTheme() {
        if (objectRendererThemeId != theme.id) {
            objectRenderer = SceneObjectRenderer(SceneObjectCatalog.layoutFor(theme.id, theme.accentColor))
            objectRendererThemeId = theme.id
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

        val radius = screenWidth * 0.055f
        val color = if (dayPhase.isSunVisible) theme.sunColor else theme.moonColor

        celestialPaint.shader = RadialGradient(
            cx, cy, radius * 3.2f,
            ColorUtils.setAlphaComponent(color, 90),
            ColorUtils.setAlphaComponent(color, 0),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, radius * 3.2f, celestialPaint)

        celestialPaint.shader = null
        celestialPaint.color = color
        canvas.drawCircle(cx, cy, radius, celestialPaint)
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
            layerGeometries[layer] = LayerGeometry(
                layer = layer,
                shiftXWrapped = wrappedShift - screenWidth * 0.5f,
                tileWidth = tileWidth,
                groundY = layerTop + layerHeight * 0.40f,
            )

            buildHillPath(hillPath, layer, shiftX, layerTop, layerHeight)

            // Soft drop shadow: draw a slightly-offset darker copy underneath for a "cut paper" feel.
            canvas.save()
            canvas.translate(0f, 6f)
            shadowPaint.alpha = 30
            canvas.drawPath(hillPath, shadowPaint)
            canvas.restore()

            hillPaint.color = color
            canvas.drawPath(hillPath, hillPaint)
        }
    }

    /**
     * Builds a smooth, seeded "hill skyline" path for one layer, wide enough to cover two
     * screen-widths so it can be shifted by [shiftX] (parallax) without ever showing a gap.
     */
    private fun buildHillPath(path: Path, layer: Int, shiftX: Float, top: Float, height: Float) {
        path.reset()
        val rnd = Random(layerSeed(layer))
        val width = screenWidth * 2f
        val segments = 6
        val segmentWidth = width / segments

        // Wrap shiftX into [-width, 0) so the tiled path always covers the visible screen.
        var wrappedShift = shiftX % width
        if (wrappedShift > 0f) wrappedShift -= width
        val startX = wrappedShift - screenWidth * 0.5f

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
