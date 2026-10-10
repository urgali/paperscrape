package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.random.Random
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [setAlphaWithoutAllocating] leaves a `Paint` exactly as Android's own `setAlpha` leaves it, for
 * **all 256 values** of the alpha (v5.10B). If one value gave a different colour, the frame would
 * change, and the change would not be allowed in.
 *
 * On the device, because the object of the comparison is the platform's `Paint` -- Android 10's on
 * the BV6600, the version whose `setAlpha` allocates -- and its native half, which only a draw can
 * read. Four readings per value: the colour as an `int` (what `GlSceneTarget` reads), the alpha, the
 * colour as a `long` (API 29+), and the pixel the paint puts on a bitmap over an opaque background
 * (what the `Canvas` backend draws), each against the same paint set the platform's way.
 *
 * Over the colours the scene paints this way -- the porch light, the chimney smoke, the rain and the
 * snow, the stars, the lightning's white, black, a colour that already carries an alpha -- and a
 * spread of random ones; each once on a fresh paint and once walked through all 256 values on the
 * same paint, which is how the draw path uses them (one paint, its alpha changed per object).
 */
@RunWith(AndroidJUnit4::class)
class PaintAlphaEquivalenceTest {

    private val colours: IntArray = intArrayOf(
        0xFFFFD97A.toInt(), // porch light
        0xFFE4E4DC.toInt(), // chimney smoke by day
        0xFF9AA0AA.toInt(), // chimney smoke at night (v5.12)
        0xFFBFC2C3.toInt(), // chimney smoke at dusk, half way between the two (v5.12)
        0xFFFFFFFF.toInt(), // the lightning veil, a star
        0xFF000000.toInt(),
        0xFF9DB7D5.toInt(), // a rain colour
        0x80123456.toInt(), // a colour with an alpha of its own, replaced by the call
        0x00ABCDEF,
    ) + IntArray(25) { Random(20260930 + it).nextInt() }

    private fun assertSamePaint(where: String, expected: Paint, actual: Paint) {
        assertEquals("$where: color", expected.color, actual.color)
        assertEquals("$where: alpha", expected.alpha, actual.alpha)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            assertEquals("$where: colorLong", expected.colorLong, actual.colorLong)
        }
    }

    @Test
    fun theSameColourAndAlphaForEveryValue() {
        var compared = 0
        for (colour in colours) {
            // A fresh paint per value.
            for (alpha in 0..255) {
                val platform = Paint().apply { color = colour; this.alpha = alpha }
                val ours = Paint().apply { color = colour; setAlphaWithoutAllocating(alpha) }
                assertSamePaint("fresh #%08x alpha %d".format(colour, alpha), platform, ours)
                compared++
            }
            // One paint walked through every value, down and up, as the draw path reuses its paints.
            val platform = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
            val ours = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour }
            for (alpha in (255 downTo 0) + (0..255)) {
                platform.alpha = alpha
                ours.setAlphaWithoutAllocating(alpha)
                assertSamePaint("walked #%08x alpha %d".format(colour, alpha), platform, ours)
                compared++
            }
        }
        assertEquals(colours.size * (256 + 512), compared)
    }

    @Test
    fun theSamePixelForEveryValue() {
        val platformBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val oursBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        val platformCanvas = Canvas(platformBitmap)
        val oursCanvas = Canvas(oursBitmap)
        var compared = 0
        for (background in intArrayOf(0xFF3A4F6B.toInt(), 0xFFFFFFFF.toInt(), 0xFF000000.toInt())) {
            for (colour in colours) {
                val platform = Paint().apply { color = colour; style = Paint.Style.FILL }
                val ours = Paint().apply { color = colour; style = Paint.Style.FILL }
                for (alpha in 0..255) {
                    platform.alpha = alpha
                    ours.setAlphaWithoutAllocating(alpha)
                    platformCanvas.drawColor(background)
                    oursCanvas.drawColor(background)
                    platformCanvas.drawRect(0f, 0f, 1f, 1f, platform)
                    oursCanvas.drawRect(0f, 0f, 1f, 1f, ours)
                    assertEquals(
                        "pixel: #%08x at alpha %d over #%08x".format(colour, alpha, background),
                        platformBitmap.getPixel(0, 0),
                        oursBitmap.getPixel(0, 0),
                    )
                    compared++
                }
            }
        }
        assertEquals(3 * colours.size * 256, compared)
        platformBitmap.recycle()
        oursBitmap.recycle()
    }
}
