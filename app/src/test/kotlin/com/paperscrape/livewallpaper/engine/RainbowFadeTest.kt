package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The arithmetic under [RainbowFade] (`DESIGN_NOTES.md` §12, decision 110): the rainbow leaves and comes back with the
 * forecast over [CloudCoverFade.FADE_SECONDS], the clouds' own constant, each way; halfway it is half drawn; it arrives
 * exactly; the first frame snaps; and a change of direction halfway starts from where the level is. `RainbowCrossfadeTest`
 * draws it on the phone.
 */
class RainbowFadeTest {

    private val dt = 1f / 30f

    /** Frames until the level reaches [target], each step held to one frame's share of the fade. */
    private fun framesTo(fade: RainbowFade, allowed: Boolean, from: Float): Int {
        val target = if (allowed) 1f else 0f
        var previous = from
        var frames = 0
        while (true) {
            val now = fade.levelToward(allowed, dt)
            assertTrue(
                "the level moved ${kotlin.math.abs(now - previous)} in one frame, past the ${dt / CloudCoverFade.FADE_SECONDS} a frame allows",
                kotlin.math.abs(now - previous) <= dt / CloudCoverFade.FADE_SECONDS + 1e-6f,
            )
            previous = now
            frames++
            if (now == target) return frames
            assertTrue("never arrived at $target in $frames frames", frames < 10_000)
        }
    }

    /** 45 at 30 frames a second; rounded, as 1.5 / (1 / 30) in `Float` is a hair under 45. */
    private val fadeFrames = kotlin.math.round(CloudCoverFade.FADE_SECONDS / dt).toInt()

    @Test
    fun `the first frame draws what it is handed, either way`() {
        assertEquals(1f, RainbowFade().levelToward(true, dt), 0f)
        assertEquals(0f, RainbowFade().levelToward(false, dt), 0f)
    }

    @Test
    fun `it leaves over the clouds' fade time and arrives at exactly nothing`() {
        val fade = RainbowFade()
        fade.levelToward(true, dt)
        val frames = framesTo(fade, allowed = false, from = 1f)
        assertTrue("left in $frames frames, the clouds take about $fadeFrames", frames in (fadeFrames - 1)..(fadeFrames + 1))
        assertEquals(0f, fade.levelToward(false, dt), 0f)
    }

    @Test
    fun `it comes back over the clouds' fade time and arrives at exactly all of it`() {
        val fade = RainbowFade()
        fade.levelToward(false, dt)
        val frames = framesTo(fade, allowed = true, from = 0f)
        assertTrue("came back in $frames frames, the clouds take about $fadeFrames", frames in (fadeFrames - 1)..(fadeFrames + 1))
        assertEquals(1f, fade.levelToward(true, dt), 0f)
    }

    @Test
    fun `halfway through the fade it is half drawn`() {
        val out = RainbowFade()
        out.levelToward(true, dt)
        assertEquals(0.5f, out.levelToward(false, CloudCoverFade.FADE_SECONDS / 2f), 1e-6f)
        val back = RainbowFade()
        back.levelToward(false, dt)
        assertEquals(0.5f, back.levelToward(true, CloudCoverFade.FADE_SECONDS / 2f), 1e-6f)
    }

    @Test
    fun `the weather changing again halfway turns back from where it is, with no jump`() {
        val fade = RainbowFade()
        fade.levelToward(true, dt)
        var level = 1f
        repeat(fadeFrames / 2) { level = fade.levelToward(false, dt) }
        assertTrue("halfway: $level", level > 0.4f && level < 0.6f)
        val turned = fade.levelToward(true, dt)
        assertEquals("one frame back up from where it was", level + dt / CloudCoverFade.FADE_SECONDS, turned, 1e-6f)
        // It goes back up only the way it came: the frames to arrive are those it had gone down.
        val frames = framesTo(fade, allowed = true, from = turned)
        assertTrue("back in $frames frames after the turn", frames in (fadeFrames / 2 - 2)..(fadeFrames / 2 + 1))
    }

    @Test
    fun `a settled level stays put and a hidden wallpaper's frames move nothing`() {
        val fade = RainbowFade()
        fade.levelToward(false, dt)
        repeat(100) { assertEquals(0f, fade.levelToward(false, dt), 0f) }
        // No frame drawn while hidden: the next frame's delta is a frame's, and the fade plays from where it was.
        assertEquals(0f, fade.levelToward(true, 0f), 0f)
        assertEquals(dt / CloudCoverFade.FADE_SECONDS, fade.levelToward(true, dt), 1e-6f)
    }
}
