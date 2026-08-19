package com.paperscrape.livewallpaper.engine

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.core.graphics.ColorUtils

/**
 * The `android.graphics.Canvas` backend: every [SceneCanvas] operation maps to the Canvas call the
 * renderers used to make directly.
 *
 * It stays in the project for two jobs the GPU backend cannot do:
 *
 *  - the settings screen's live preview draws onto a Compose `Canvas`, where there is no EGL surface
 *    and no GL context to draw into;
 *  - it is the fallback when EGL initialisation fails, so a device that cannot give the wallpaper a
 *    GL context still gets a wallpaper rather than a black screen.
 *
 * The instance is reused across frames and re-pointed at each frame's canvas with [bind], so no
 * per-frame allocation is introduced by the indirection.
 */
class CanvasSceneTarget : SceneCanvas {

    private var canvas: Canvas? = null

    /** Paints owned here because the gradient entry points carry their stops as arguments. */
    private val gradientPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val spritePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val wedgePath = Path()
    private val scratchRect = RectF()

    fun bind(canvas: Canvas) {
        this.canvas = canvas
    }

    fun unbind() {
        canvas = null
    }

    private fun require(): Canvas = canvas ?: error("CanvasSceneTarget used before bind()")

    override fun save() {
        require().save()
    }

    override fun restore() {
        require().restore()
    }

    override fun translate(dx: Float, dy: Float) {
        require().translate(dx, dy)
    }

    override fun scale(sx: Float, sy: Float) {
        require().scale(sx, sy)
    }

    override fun rotate(degrees: Float) {
        require().rotate(degrees)
    }

    override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        require().drawRect(left, top, right, bottom, paint)
    }

    override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) {
        require().drawLine(startX, startY, stopX, stopY, paint)
    }

    override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) {
        require().drawCircle(cx, cy, radius, paint)
    }

    override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) {
        require().drawOval(left, top, right, bottom, paint)
    }

    override fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, paint: Paint) {
        require().drawArc(oval, startAngle, sweepAngle, false, paint)
    }

    override fun drawWedge(
        cx: Float,
        cy: Float,
        radius: Float,
        startAngle: Float,
        sweepAngle: Float,
        paint: Paint,
    ) {
        scratchRect.set(cx - radius, cy - radius, cx + radius, cy + radius)
        wedgePath.reset()
        wedgePath.moveTo(cx, cy)
        wedgePath.arcTo(scratchRect, startAngle, sweepAngle, false)
        wedgePath.close()
        require().drawPath(wedgePath, paint)
    }

    override fun drawShape(shape: SceneShape, paint: Paint) {
        require().drawPath(shape.asPath(), paint)
    }

    override fun drawVerticalGradientShape(
        shape: SceneShape,
        gradientTopY: Float,
        gradientBottomY: Float,
        topColor: Int,
        bottomColor: Int,
        alpha: Int,
    ) {
        gradientPaint.shader = LinearGradient(
            0f, gradientTopY, 0f, gradientBottomY, topColor, bottomColor, Shader.TileMode.CLAMP,
        )
        gradientPaint.alpha = alpha
        require().drawPath(shape.asPath(), gradientPaint)
        gradientPaint.shader = null
        gradientPaint.alpha = 255
    }

    override fun drawVerticalGradientRect(
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        topColor: Int,
        bottomColor: Int,
    ) {
        gradientPaint.shader = LinearGradient(
            0f, top, 0f, bottom, topColor, bottomColor, Shader.TileMode.CLAMP,
        )
        require().drawRect(left, top, right, bottom, gradientPaint)
        gradientPaint.shader = null
    }

    override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) {
        gradientPaint.shader = RadialGradient(
            cx, cy, radius,
            ColorUtils.setAlphaComponent(color, centerAlpha),
            ColorUtils.setAlphaComponent(color, 0),
            Shader.TileMode.CLAMP,
        )
        require().drawCircle(cx, cy, radius, gradientPaint)
        gradientPaint.shader = null
    }

    override fun drawSprite(
        resId: Int,
        source: SpriteSource,
        left: Float,
        top: Float,
        tintColor: Int,
        alpha: Int,
    ) {
        if (alpha <= 0) return
        // White is the MULTIPLY identity, so an untinted sprite skips the filter entirely rather
        // than paying for a no-op one.
        spritePaint.colorFilter = if (tintColor == WHITE) null else TintFilterCache.get(tintColor)
        spritePaint.alpha = alpha
        // Every frame, unavoidably: a Canvas blit needs the pixels themselves. This backend never
        // reports the sprite as uploaded, because it holds no durable copy to justify releasing it.
        require().drawBitmap(source.bitmapFor(resId), left, top, spritePaint)
    }

    private companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
