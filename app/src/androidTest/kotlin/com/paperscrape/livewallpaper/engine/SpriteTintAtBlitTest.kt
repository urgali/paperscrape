package com.paperscrape.livewallpaper.engine

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The other half of the tint gate: **what the blit does**, not what the PNG holds.
 *
 * `SpriteTintClassTest` reads the shipped artwork and asks whether each sprite is a colourless
 * mask or finished art. That is one of the two things the split is about, and on its own it can
 * only ever be half of it, because a PNG does not record how it is drawn. Its two lists of names
 * are the call sites *restated* -- a hand copy, kept in step by hand -- so the failure it cannot
 * see is a call site that starts multiplying a finished sprite by a colour: the artwork it reads
 * is unchanged, both of its assertions still hold, and the scene quietly draws two hues
 * compounded. That is the same defect the V2 asset set would have caused, and the same one the
 * lake decorations shipped in its mirror image.
 *
 * This closes it from the other side. The scene is driven through a recording canvas, every blit
 * is caught with the tint it was given, and the sprite's own pixels then say which rule applies:
 *
 *  * **finished art must be blitted with the identity.** `MULTIPLY` can only darken, so any tint
 *    other than white applied to a sprite that carries its own colours darkens or shifts them --
 *    a green frond times an autumn orange is mud.
 *  * **a mask must never be blitted with the identity.** White is the `MULTIPLY` identity, so a
 *    mask drawn with it renders as the white silhouette `dolphin_body` and `sailboat_hull`
 *    shipped as.
 *
 * Neither rule needs a list of names: the artwork decides which of the two a sprite is, and the
 * recorder says what was done to it. A sprite that changes class and whose call site does not
 * move with it fails here whichever way round the change was made.
 */
@RunWith(AndroidJUnit4::class)
class SpriteTintAtBlitTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun finishedArtIsBlittedWithTheIdentityAndMasksNeverAre() {
        val recorder = TintRecorder()
        // Every built-in theme, by day and by night: a theme decides which objects exist at all,
        // and the day blend decides which of a category's two colours a call site passes, so one
        // scene at one hour would leave most of the tint call sites unvisited.
        for (theme in ThemeCatalog.ALL) {
            val customization = defaultCustomizationFor(theme.id)
            val layout = RandomSceneGenerator.generateLayout(theme.id, theme.accentColor)
            for (dayBlend in listOf(1f, 0f)) {
                SceneObjectRenderer(layout, customization, context, theme.id).draw(
                    recorder,
                    GroundGeometry(shiftXWrapped = 0f, tileWidth = WIDTH),
                    dayBlend = dayBlend,
                    elapsedSeconds = SceneTime(120.0),
                    screenWidth = WIDTH,
                    screenHeight = HEIGHT,
                )
            }
        }
        // A floor, not a count: this fixture reaches 69 distinct sprites, and the failure mode
        // this guards against is a fixture that quietly stops drawing -- a green run over four
        // sprites would look exactly like a green run over seventy.
        assertTrue(
            "only ${recorder.tints.size} sprites were blitted; this fixture reaches 69",
            recorder.tints.size >= 60,
        )

        val compounded = ArrayList<String>()
        val silhouettes = ArrayList<String>()
        for ((resId, tints) in recorder.tints) {
            val name = context.resources.getResourceEntryName(resId)
            val carriesColour = carriesColour(resId)
            val offending = if (carriesColour) {
                tints.filter { it != IDENTITY }
            } else {
                tints.filter { it == IDENTITY }
            }
            if (offending.isEmpty()) continue
            val shown = offending.take(3).joinToString { "#%08X".format(it) }
            if (carriesColour) {
                compounded += "$name carries its own colours and is blitted tinted $shown"
            } else {
                silhouettes += "$name is a colourless mask and is blitted with the identity tint"
            }
        }
        assertEquals(
            "finished art multiplied by a colour compounds two hues: $compounded",
            emptyList<String>(), compounded,
        )
        assertEquals(
            "a mask blitted with white draws as a silhouette: $silhouettes",
            emptyList<String>(), silhouettes,
        )
    }

    /** Whether the shipped PNG has an opaque pixel whose channels are not all equal. */
    private fun carriesColour(resId: Int): Boolean {
        val options = BitmapFactory.Options().apply { inScaled = false }
        val bitmap = BitmapFactory.decodeResource(context.resources, resId, options)
            ?: throw AssertionError("resource $resId could not be decoded")
        try {
            for (y in 0 until bitmap.height) {
                for (x in 0 until bitmap.width) {
                    val pixel = bitmap.getPixel(x, y)
                    if ((pixel ushr 24) == 0) continue
                    val r = (pixel shr 16) and 0xFF
                    val g = (pixel shr 8) and 0xFF
                    val b = pixel and 0xFF
                    if (r != g || g != b) return true
                }
            }
        } finally {
            bitmap.recycle()
        }
        return false
    }

    /** Records the tint every blit was given, and ignores everything else. */
    private class TintRecorder : SceneCanvas {
        val tints = LinkedHashMap<Int, MutableSet<Int>>()
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = Unit
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawArc(oval: RectF, startAngle: Float, sweepAngle: Float, paint: Paint) = Unit
        override fun drawWedge(
            cx: Float,
            cy: Float,
            radius: Float,
            startAngle: Float,
            sweepAngle: Float,
            paint: Paint,
        ) = Unit
        override fun drawShape(shape: SceneShape, paint: Paint) = Unit
        override fun drawVerticalGradientShape(
            shape: SceneShape,
            gradientTopY: Float,
            gradientBottomY: Float,
            topColor: Int,
            bottomColor: Int,
            alpha: Int,
        ) = Unit
        override fun drawVerticalGradientRect(
            left: Float,
            top: Float,
            right: Float,
            bottom: Float,
            topColor: Int,
            bottomColor: Int,
        ) = Unit
        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) = Unit
        override fun drawSprite(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
            additive: Boolean,
        ) {
            tints.getOrPut(resId) { LinkedHashSet() }.add(tintColor)
        }
    }

    private companion object {
        /** A real phone's viewport; nothing here depends on it beyond the objects fitting in it. */
        const val WIDTH = 1080f
        const val HEIGHT = 2400f

        /** White: the `MULTIPLY` identity, and therefore "the sprite's own colours, unchanged". */
        const val IDENTITY = 0xFFFFFFFF.toInt()
    }
}
