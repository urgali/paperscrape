package com.paperscrape.livewallpaper.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import com.paperscrape.livewallpaper.engine.CanvasSceneTarget
import com.paperscrape.livewallpaper.engine.PreviewItem
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneShape
import com.paperscrape.livewallpaper.engine.SceneTheme
import com.paperscrape.livewallpaper.engine.SpriteBlitter
import com.paperscrape.livewallpaper.engine.SpriteScale
import com.paperscrape.livewallpaper.engine.ThemePreviewGeometry
import com.paperscrape.livewallpaper.engine.ThemePreviewScene
import com.paperscrape.livewallpaper.engine.ThemePreviewScenes
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import kotlin.math.sin

/**
 * A theme's preview: a real mini scene, drawn from the shipping sprites with the theme's own
 * palette and the customization it actually carries.
 *
 * **Deliberately not a live wallpaper in a card.** There is no GL context, no animation, no timer
 * and no per-frame state: [ThemePreviewScenes.forTheme] builds a plain description once, kept
 * across recompositions by `remember`, and this composable replays it. Sprite bitmaps come from
 * the process-wide `SpriteCache` the settings preview already uses, so twelve cards decode one
 * shared set rather than twelve. The whole cost of a card is roughly twenty static blits, paid on
 * composition and on scroll, and nothing at rest.
 *
 * What it replaced was a sky gradient, a circle for the sun and a rectangle for the hills -- honest
 * about the palette and silent about everything else, which left six of the twelve built-in themes
 * looking alike in the gallery.
 */
@Composable
internal fun ThemeScenePreview(
    theme: SceneTheme,
    modifier: Modifier = Modifier,
    customization: SceneCustomization? = null,
    forceNight: Boolean? = null,
) {
    val context = LocalContext.current
    val resolved = customization ?: remember(theme.id) { defaultCustomizationFor(theme.id) }
    val scene = remember(theme.id, resolved, forceNight) {
        ThemePreviewScenes.forTheme(theme, resolved, forceNight)
    }
    // One blitter, one canvas adapter, one paint and one shape per preview, built once: nothing
    // below allocates while drawing.
    val blitter = remember(context) { SpriteBlitter(context) }
    val target = remember { CanvasSceneTarget() }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val shape = remember { SceneShape() }

    Canvas(modifier = modifier) {
        val scale = ThemePreviewGeometry.scaleFor(size.width)
        drawIntoCanvas { canvas ->
            target.bind(canvas.nativeCanvas)
            target.save()
            target.scale(scale, scale)
            drawScene(scene, target, blitter, paint, shape)
            target.restore()
            target.unbind()
        }
    }
}

private fun drawScene(
    scene: ThemePreviewScene,
    target: CanvasSceneTarget,
    blitter: SpriteBlitter,
    paint: Paint,
    shape: SceneShape,
) {
    val w = ThemePreviewScene.WIDTH_UNITS
    val h = ThemePreviewScene.HEIGHT_UNITS
    val horizon = ThemePreviewScene.HORIZON_UNITS

    // Sky, then the band below it flooded with the sky's low colour so the hill silhouette's
    // troughs have something behind them.
    target.drawVerticalGradientRect(0f, 0f, w, horizon, scene.skyTop, scene.skyBottom)
    paint.color = scene.skyBottom
    target.drawRect(0f, horizon - 1f, w, h, paint)

    for (dot in scene.dots) {
        if (dot.y > horizon - 8f && dot.radius < 1f) continue // stars stay in the sky
        paint.color = dot.colour
        paint.alpha = dot.alpha
        target.drawCircle(dot.x, dot.y, dot.radius, paint)
        paint.alpha = 255
    }

    for (item in scene.backdrop.filter { it.y < horizon }) drawItem(item, target, blitter)

    for (peak in scene.peaks) {
        paint.color = peak.colour
        shape.reset()
        if (peak.dune) {
            // A dune is the same silhouette with its shoulders rounded off, so the desert's
            // horizon does not read as an alpine ridge.
            shape.moveTo(peak.x - peak.halfWidth, horizon + 2f)
            shape.lineTo(peak.x - peak.halfWidth * 0.55f, peak.peakY + (horizon - peak.peakY) * 0.35f)
            shape.lineTo(peak.x, peak.peakY + (horizon - peak.peakY) * 0.15f)
            shape.lineTo(peak.x + peak.halfWidth * 0.6f, peak.peakY + (horizon - peak.peakY) * 0.45f)
            shape.lineTo(peak.x + peak.halfWidth, horizon + 2f)
        } else {
            shape.moveTo(peak.x - peak.halfWidth, horizon + 2f)
            shape.lineTo(peak.x, peak.peakY)
            shape.lineTo(peak.x + peak.halfWidth, horizon + 2f)
        }
        shape.close()
        target.drawShape(shape, paint)
    }

    // The hills: one silhouette with a gentle wave, matching the single hill layer the scene
    // actually draws.
    paint.color = scene.groundColour
    shape.reset()
    shape.moveTo(0f, h)
    shape.lineTo(0f, horizon + 4f)
    var i = 0
    while (i <= 32) {
        val x = i * (w / 32f)
        shape.lineTo(x, horizon + 2f + 3f * sin(i * 0.7f + 1.2f) + 1.6f * sin(i * 1.9f))
        i++
    }
    shape.lineTo(w, horizon + 4f)
    shape.lineTo(w, h)
    shape.close()
    target.drawShape(shape, paint)

    if (scene.hasLake) {
        paint.color = scene.lake.colour
        target.drawRect(0f, scene.lake.top, w, scene.lake.bottom, paint)
    }

    for (item in scene.backdrop.filter { it.y >= horizon }) drawItem(item, target, blitter)
    for (item in scene.items) drawItem(item, target, blitter)

    if (scene.hasRoad) {
        paint.color = scene.roadColour
        target.drawRect(0f, ROAD_TOP, w, ROAD_BOTTOM, paint)
        paint.color = ROAD_LINE_COLOUR
        var x = 4f
        while (x < w) {
            target.drawRect(x, (ROAD_TOP + ROAD_BOTTOM) / 2f - 0.8f, x + 16f, (ROAD_TOP + ROAD_BOTTOM) / 2f + 0.8f, paint)
            x += 36f
        }
        for (item in scene.cars) drawItem(item, target, blitter)
    }

    for (item in scene.ground) drawItem(item, target, blitter)

    // Snow and falling leaves are drawn over the scene, the way the real precipitation layer is.
    for (dot in scene.dots) {
        if (dot.radius < 1f) continue
        paint.color = dot.colour
        paint.alpha = dot.alpha
        target.drawCircle(dot.x, dot.y, dot.radius, paint)
        paint.alpha = 255
    }
}

private fun drawItem(item: PreviewItem, target: CanvasSceneTarget, blitter: SpriteBlitter) {
    target.save()
    target.translate(item.x, item.y)
    target.scale(item.scale, item.scale)
    for (part in item.parts) {
        if (part.tint != null) {
            blitter.drawTinted(target, part.resId, part.ox, part.oy, SpriteScale.SCENE_UNITS, part.tint, part.alpha)
        } else {
            blitter.draw(target, part.resId, part.ox, part.oy, SpriteScale.SCENE_UNITS, part.alpha)
        }
    }
    target.restore()
}

private const val ROAD_TOP = 207f
private const val ROAD_BOTTOM = 229f
private const val ROAD_LINE_COLOUR = 0xFFE8E2D2.toInt()
