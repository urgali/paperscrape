package com.paperscrape.livewallpaper.engine

import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import kotlin.math.abs

/**
 * The device's wall clock, moved and put back.
 *
 * ### Why a test moves the phone's clock
 *
 * A golden is a claim that a frame is a function of the scene written down beside it. Everything
 * else the harness pins -- the theme, the customisation, the scene clock, the day phase, the
 * scroll, the lightning timer -- it pins by *passing* it, and a check that the pinning worked can
 * be written by passing something different. The **wall clock** is the one input the renderer can
 * reach without being handed it, and there is no in-process seam that makes
 * `System.currentTimeMillis()` answer differently: the only honest way to ask "would this frame
 * look the same tomorrow" is to make it be tomorrow.
 *
 * This is deliberately *not* a fake clock injected into the app. A fake clock would only catch a
 * regression that went through the fake, and the defect this exists for --
 * `PaperRenderer.drawMoonWithPhase` calling `SunPositionCalculator.moonPhase()` while painting,
 * v5.4G -- would have gone straight past one. Moving the real clock catches any clock read on the
 * render path, whatever route it takes to the hardware.
 *
 * ### What it needs, and what happens when it cannot have it
 *
 * `cmd alarm set-time` run through `UiAutomation.executeShellCommand`, which executes as `shell`
 * (uid 2000) -- the same user, and the same command, that works over adb. **`date` does not**:
 * this toybox has no `-s`, and the positional form (`date 091719022026.00`) answers
 * `date: cannot set date: Operation not permitted` for `shell`. Measured on the BV6600 on
 * 2026-09-18; why the two differ was not established, only that they do.
 *
 * Automatic time is switched off for the duration, or the network resets the clock underneath the
 * second render, and switched back to whatever it was on the way out.
 *
 * **If the clock will not move, this throws.** It does not skip, and it does not quietly render
 * the same instant twice: a check that can only say "fine" is the shape of check this project has
 * already shipped three of. See `SceneGolden.assertReproducesOverTime`.
 *
 * ### If a run dies in the middle of one
 *
 * [displacedBy] restores on every exit, so an assertion failure or an exception puts the clock
 * back. **Process death does not**, and a test process killed inside the displaced window leaves
 * the phone six months in the future with automatic time switched off. It is visible immediately
 * and one command to undo:
 *
 * ```
 * adb shell settings put global auto_time 1
 * ```
 *
 * That is the whole of the risk this check takes, and it is taken deliberately: the alternative is
 * a fake clock, which cannot see a regression that does not go through it.
 */
object DeviceClock {

    /** How close the clock has to land for the move to count as having happened. */
    private const val TOLERANCE_MILLIS = 20_000L

    private fun shell(command: String): String {
        val fd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        return FileInputStream(fd.fileDescriptor).use { String(it.readBytes()) }
    }

    private fun setTo(millis: Long) {
        shell("cmd alarm set-time $millis")
        val landed = System.currentTimeMillis()
        if (abs(landed - millis) > TOLERANCE_MILLIS) {
            throw AssertionError(
                "Could not move this device's wall clock: asked for $millis, the clock reads " +
                    "$landed (${abs(landed - millis)} ms away). The reproducibility check in " +
                    "SceneGolden cannot run without it, and it must not pass without running -- " +
                    "see DeviceClock's own notes for what the move needs.",
            )
        }
    }

    /**
     * Runs [body] with the device clock moved forward by [deltaMillis], and puts the clock back
     * afterwards whatever happens.
     *
     * The restore targets *where the clock would have been* -- the instant it was moved from, plus
     * however long [body] took -- rather than the instant it was moved from, so a suite that does
     * this once per golden does not walk the clock backwards a few seconds at a time.
     *
     * **Written out instead of as one `finally`, for two reasons a `finally` gets wrong.**
     *
     *  - A forward move that *failed* must not be undone. `finally` would subtract the
     *    displacement from a clock that never moved and leave the phone six months in the **past**,
     *    while hiding the failure that got there behind a second one.
     *  - A restore that fails must not replace the failure that brought us to it. On the way out of
     *    a failing [body] the restore is attempted and its own error dropped, because a golden's
     *    message is what the reader needs; on the way out of a *passing* one, a clock that will not
     *    come back is the only news there is, and it is thrown.
     */
    fun <T> displacedBy(deltaMillis: Long, body: () -> T): T {
        val autoTime = shell("settings get global auto_time").trim()
        val before = System.currentTimeMillis()
        shell("settings put global auto_time 0")
        try {
            setTo(before + deltaMillis)
        } catch (couldNotMove: Throwable) {
            restore(autoTime, moved = false, deltaMillis = deltaMillis)
            throw couldNotMove
        }
        val result = try {
            body()
        } catch (failed: Throwable) {
            runCatching { restore(autoTime, moved = true, deltaMillis = deltaMillis) }
            throw failed
        }
        restore(autoTime, moved = true, deltaMillis = deltaMillis)
        return result
    }

    private fun restore(autoTime: String, moved: Boolean, deltaMillis: Long) {
        if (moved) setTo(System.currentTimeMillis() - deltaMillis)
        if (autoTime == "1") shell("settings put global auto_time 1")
    }
}
