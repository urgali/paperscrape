package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The Headless Horseman flies to the sleigh's rhythm and drops his pumpkins from a fixed pool** (v5.16): the first flight
 * 8 s after the switch, a flight of 9 to 13 s at 10 to 26 % of the screen's height, the next 25 to 60 s after the last
 * ended; pumpkins let fall while he crosses the screen, from 12 to 88 % of the crossing -- the first and the last within a
 * tenth of the width beyond an edge on a screen 720 px wide or more, his middle still off the screen there --, never more at
 * once than the pool holds, a slot taken and given back rather than an object made (inventory I-1025: in the prototype each
 * drop was an object).
 */
class HorsemanFlightTest {

    private val w = 720f
    private val h = 1440f
    private val dt = 1f / 30f

    @Test
    fun `the first flight sets off after 8 seconds, and they come at the sleigh's pace`() {
        val f = HorsemanFlight()
        f.setGroupGeometry(dropHalf = 5f, groupHalfWidth = 60f)
        var frame = 0
        var t = 0f
        val starts = mutableListOf<Float>()
        val lengths = mutableListOf<Float>()
        var wasFlying = false
        var started = 0f
        while (t < 3600f) {
            f.update(dt, enabled = true, sleighDueIn = Float.POSITIVE_INFINITY, screenWidth = w, screenHeight = h)
            // The frames' time counted, not summed: a Float summed frame by frame runs 0.34 % fast past 2 048 s, and a
            // gap of 60 s there read 60.2 (inventory I-1067).
            t = ++frame * dt
            if (f.flying && !wasFlying) { starts += t; started = t; assertTrue("height ${f.flightY / h}", f.flightY / h in 0.10f..0.26f) }
            if (!f.flying && wasFlying) lengths += t - started
            wasFlying = f.flying
            assertTrue("never more pumpkins than the pool: ${f.dropsFalling}", f.dropsFalling <= HorsemanFlight.POOL_SIZE)
        }
        assertEquals("the first at 8 s", 8f, starts.first(), 0.1f)
        for (l in lengths) assertTrue("a flight of $l s", l in 8.9f..13.1f)
        for (i in 1 until starts.size) {
            val gap = starts[i] - starts[i - 1] - lengths[i - 1]
            assertTrue("a gap of $gap s", gap in 24.9f..60.1f)
        }
        assertTrue("about once a minute: ${starts.size} in an hour", starts.size in 49..106)
    }

    @Test
    fun `pumpkins fall only while he crosses the screen, and slots are given back`() {
        val f = HorsemanFlight()
        f.setGroupGeometry(dropHalf = 5f, groupHalfWidth = 60f)
        var dropsSeen = 0
        var previous = 0
        var t = 0f
        var maxInAir = 0
        while (t < 1200f) {
            f.update(dt, true, Float.POSITIVE_INFINITY, w, h)
            t += dt
            val now = f.dropsFalling
            if (now > previous) {
                dropsSeen += now - previous
                assertTrue("let fall at ${f.flightProgress}", f.flightProgress in HorsemanFlight.DROP_FROM..HorsemanFlight.DROP_UNTIL + dt / 9f)
                // While he crosses the screen: every pumpkin just let fall starts over it or just beyond an edge -- the group's
                // middle is off the screen for the first and last 15 % of the crossing, the margin the sleigh's flights keep
                // ([SantaSleighEffect.MIN_MARGIN_PX]) -- never farther than a tenth of the width, and falls on into it.
                for (i in 0 until HorsemanFlight.POOL_SIZE) if (f.dropActive[i] && f.dropAge[i] <= dt) {
                    assertTrue("a pumpkin let fall at x=${f.dropX[i]}", f.dropX[i] in -w * 0.1f..w * 1.1f)
                }
            }
            maxInAir = maxOf(maxInAir, now)
            previous = now
            for (i in 0 until HorsemanFlight.POOL_SIZE) if (f.dropActive[i]) {
                assertTrue("on its way to the pavement", f.dropTargetY[i] / h in SceneSpace.PAVEMENT_FAR_Y_FRACTION..SceneSpace.PAVEMENT_NEAR_Y_FRACTION)
                assertTrue(f.dropDuration[i] in 1.6f..2.1f)
            }
        }
        assertTrue("pumpkins fell: $dropsSeen", dropsSeen > 50)
        assertTrue("two at once at most, with the pool's room to spare: $maxInAir", maxInAir <= 2)
    }

    /**
     * The pumpkins' rhythm in a flight (the prototype's `cDrop=3`, chosen): the first 1.4 s after he sets off -- or at 12 % of
     * the crossing where that comes later --, then one every 1.1 to 1.9 s.
     */
    @Test
    fun `the first pumpkin falls 1_4 s into a flight, then one every 1_1 to 1_9 s`() {
        val f = HorsemanFlight()
        f.setGroupGeometry(dropHalf = 5f, groupHalfWidth = 60f)
        var t = 0f
        var started = -1f
        var last = -1f
        var wasFlying = false
        var firsts = 0
        var gaps = 0
        // A pumpkin's start is its slot's age going back (or the slot taking one up): a pumpkin may land in the frame another
        // sets off, even in the same slot, so neither the count in the air nor a slot's emptiness sees every one.
        val age = FloatArray(HorsemanFlight.POOL_SIZE) { -1f }
        while (t < 1800f) {
            f.update(dt, true, Float.POSITIVE_INFINITY, w, h)
            t += dt
            if (f.flying && !wasFlying) { started = t; last = -1f }
            wasFlying = f.flying
            var fresh = false
            for (i in 0 until HorsemanFlight.POOL_SIZE) {
                val now = if (f.dropActive[i]) f.dropAge[i] else -1f
                if (now >= 0f && (age[i] < 0f || now < age[i])) fresh = true
                age[i] = now
            }
            if (fresh && f.flying) {
                if (last < 0f) {
                    // 1.4 s in, and not before 12 % of the crossing: on a flight longer than 11.7 s the window comes later.
                    assertEquals("the first pumpkin of a ${f.flightDuration} s flight", maxOf(1.4f, 0.12f * f.flightDuration), t - started, 2 * dt)
                    firsts++
                } else {
                    assertTrue("a pumpkin ${t - last} s after the one before", t - last in 1.1f - 2 * dt..1.9f + 2 * dt)
                    gaps++
                }
                last = t
            }
        }
        assertTrue("flights with pumpkins: $firsts, then $gaps more", firsts > 20 && gaps > 100)
    }

    @Test
    fun `held for the tests it drops nothing, and switched off it lands what is falling and starts again at 8 seconds`() {
        val f = HorsemanFlight()
        f.heldProgress = 0.5f
        repeat(300) { f.update(dt, true, Float.POSITIVE_INFINITY, w, h) }
        assertTrue(f.flying)
        assertEquals(0.5f, f.flightProgress, 0f)
        assertEquals(0, f.dropsFalling)
        f.heldProgress = -1f
        repeat(120) { f.update(dt, true, Float.POSITIVE_INFINITY, w, h) }
        assertTrue("one let fall since", f.dropsFalling >= 1)
        f.update(dt, false, Float.POSITIVE_INFINITY, w, h)
        assertFalse(f.flying)
        assertTrue("those falling keep falling", f.dropsFalling >= 1)
        repeat(90) { f.update(dt, false, Float.POSITIVE_INFINITY, w, h) }
        assertEquals("and land", 0, f.dropsFalling)
        assertEquals(HorsemanFlight.FIRST_FLIGHT_SECONDS, f.timeUntilNextFlight, 0f)
    }

    @Test
    fun `the rhythm is the sleigh's, read off its source`() {
        val sleigh = java.io.File(sourceRoot(), "engine/SantaSleighEffect.kt").readText()
        assertTrue(sleigh.contains("timeUntilNextFlight = 25f + Random.nextFloat() * 35f"))
        assertTrue(sleigh.contains("flightDuration = 9f + Random.nextFloat() * 4f"))
        assertTrue(sleigh.contains("flightY = screenHeight * (0.10f + Random.nextFloat() * 0.16f)"))
        assertEquals(25f, HorsemanFlight.GAP_MIN_SECONDS, 0f)
        assertEquals(60f, HorsemanFlight.GAP_MIN_SECONDS + HorsemanFlight.GAP_SPREAD_SECONDS, 0f)
        assertEquals(9f, HorsemanFlight.FLIGHT_MIN_SECONDS, 0f)
        assertEquals(13f, HorsemanFlight.FLIGHT_MIN_SECONDS + HorsemanFlight.FLIGHT_SPREAD_SECONDS, 0f)
    }

    /**
     * **He keeps out of Santa's sleigh's way** (inventory I-1048 and I-1068): beside the sleigh's own rhythm ([SleighRhythm]),
     * an hour of each of eight runs, the two are never in the sky together and the sky is empty at least
     * [HorsemanFlight.AFTER_SLEIGH_SECONDS] between them, whichever goes first -- he never sets off with the sleigh up, nor less
     * than that after it has gone, and the sleigh never sets off while he flies, nor less than that after he has gone; a turn
     * of his the sleigh held sets off just that long after it has gone; at the wallpaper's start, where both are due at 8 s,
     * the sleigh goes and he follows 3 s after it has gone. The maintainer's words: *«si a tutte»* to the v5.16B report's
     * recommendation that the Horseman waits until the sleigh has left the sky, and, shown them still meeting when the sleigh
     * set off with him up, *«A: il cavaliere aspetta anche la slitta in arrivo (consigliata)»* (both 2026-10-22).
     */
    @Test
    fun `he and Santa's sleigh are never in the sky together, and a held turn goes 3 s after it`() {
        val after = HorsemanFlight.AFTER_SLEIGH_SECONDS
        assertEquals(3f, after, 0f)
        for (seed in 1..8) {
            val sleigh = SleighRhythm(kotlin.random.Random(seed))
            val f = HorsemanFlight(kotlin.random.Random(1000 + seed))
            f.setGroupGeometry(dropHalf = 5f, groupHalfWidth = 60f)
            var frame = 0
            var t = 0f
            var wasFlying = false
            var landed = -1000f // when the sleigh last went out of the sky
            var heLanded = -1000f // when he last did
            var firstLanded = -1f
            var sleighFirst = -1f
            var firstStart = -1f
            var heldTurn = false
            var held = 0
            var starts = 0
            var sleighStarts = 0
            while (t < 3600f) {
                val wasUp = sleigh.flying
                sleigh.update(dt)
                t = ++frame * dt // counted, not summed, as in the first test
                if (!wasUp && sleigh.flying) {
                    sleighStarts++
                    if (sleighFirst < 0f) sleighFirst = t
                    assertFalse("seed $seed: the sleigh set off at $t s with him up", f.flying)
                    assertTrue("seed $seed: the sleigh set off ${t - heLanded} s after he had gone", t - heLanded >= after - 2 * dt)
                }
                if (wasUp && !sleigh.flying) { landed = t; if (firstLanded < 0f) firstLanded = t }
                f.update(dt, enabled = true, sleighDueIn = sleigh.secondsToFlight, screenWidth = w, screenHeight = h)
                if (!f.flying && f.timeUntilNextFlight == after && sleigh.secondsToFlight <= HorsemanFlight.SKY_NEEDED_SECONDS) {
                    heldTurn = true
                }
                if (f.flying && !wasFlying) {
                    starts++
                    if (firstStart < 0f) firstStart = t
                    assertFalse("seed $seed: he set off at $t s with the sleigh up", sleigh.flying)
                    assertTrue("seed $seed: he set off ${t - landed} s after the sleigh had gone", t - landed >= after - 2 * dt)
                    if (heldTurn) {
                        assertEquals("seed $seed: a held turn at $t s", after, t - landed, 2 * dt)
                        held++
                    }
                    heldTurn = false
                }
                if (!f.flying && wasFlying) heLanded = t
                assertFalse("seed $seed: both in the sky at $t s", f.flying && sleigh.flying)
                wasFlying = f.flying
            }
            assertEquals("seed $seed: both due at 8 s (the sleigh's, read off its source), the sleigh goes first", 8f, sleighFirst, 2 * dt)
            assertEquals("seed $seed: he follows 3 s after the first sleigh has gone", firstLanded + after, firstStart, 2 * dt)
            assertTrue("seed $seed: turns held by the sleigh $held of $starts", held >= 10 && starts >= 40 && sleighStarts >= 49)
        }
    }

    /**
     * With *Santa* off the renderer hands him an infinite wait ([Float.POSITIVE_INFINITY]): his rhythm is then his own (the
     * first test), whatever the sleigh's countdown was left at when it was switched off.
     */
    @Test
    fun `a sleigh that is not due holds nothing`() {
        val f = HorsemanFlight(kotlin.random.Random(7))
        var frame = 0
        var t = 0f
        var first = -1f
        while (t < 30f && first < 0f) {
            f.update(dt, enabled = true, sleighDueIn = Float.POSITIVE_INFINITY, screenWidth = w, screenHeight = h)
            t = ++frame * dt
            if (f.flying) first = t
        }
        assertEquals("the first flight at 8 s", HorsemanFlight.FIRST_FLIGHT_SECONDS, first, 2 * dt)
        // And a sleigh due a moment after his longest flight and the gap does not hold him either.
        val g = HorsemanFlight(kotlin.random.Random(7))
        repeat((HorsemanFlight.FIRST_FLIGHT_SECONDS / dt).toInt() + 2) {
            g.update(dt, true, HorsemanFlight.SKY_NEEDED_SECONDS + 0.5f, w, h)
        }
        assertTrue("set off with the sky his", g.flying)
    }

    /**
     * The renderer updates the sleigh first and hands the Horseman, in the same frame, how soon it is in the sky -- with *Santa*
     * off, never (I-1048, I-1068): a Horseman updated before the sleigh, or told nothing, would set off with it at the
     * wallpaper's start, where both are due at 8 s; one told the countdown of a sleigh switched off would wait for ever.
     */
    @Test
    fun `the renderer updates the sleigh first and hands the Horseman how soon it flies`() {
        val renderer = java.io.File(sourceRoot(), "engine/PaperRenderer.kt").readText()
        val sleighAt = renderer.indexOf("santaSleighEffect.update(")
        val horsemanAt = renderer.indexOf("headlessHorsemanEffect.update(")
        assertEquals("one update of each", sleighAt, renderer.lastIndexOf("santaSleighEffect.update("))
        assertEquals("one update of each", horsemanAt, renderer.lastIndexOf("headlessHorsemanEffect.update("))
        assertTrue("the sleigh first: $sleighAt, $horsemanAt", sleighAt in 0 until horsemanAt)
        assertTrue(
            "how soon the sleigh flies, with Santa off never",
            renderer.contains(
                "val sleighDueIn = if (sceneCustomization.santaEnabled) santaSleighEffect.secondsToFlight else Float.POSITIVE_INFINITY",
            ),
        )
        val due = renderer.indexOf("val sleighDueIn = ")
        assertTrue("worked out after the sleigh's update, before his", due in sleighAt until horsemanAt)
        // The call's arguments, split at its own commas: the third is what the Horseman reads as the sleigh's. (The two
        // brackets are written as escapes, so `UnitFrameTest`'s reading of statements is not thrown by an open one.)
        val openBracket = '\u0028'
        val closeBracket = '\u0029'
        val open = renderer.indexOf(openBracket, horsemanAt)
        var depth = 0
        var close = open
        while (true) {
            when (renderer[close]) { openBracket -> depth++; closeBracket -> { depth--; if (depth == 0) break } }
            close++
        }
        val args = ArrayList<String>()
        depth = 0
        var from = open + 1
        for (k in open + 1 until close) when (renderer[k]) {
            openBracket -> depth++
            closeBracket -> depth--
            ',' -> if (depth == 0) { args += renderer.substring(from, k).trim(); from = k + 1 }
        }
        args += renderer.substring(from, close).trim()
        assertEquals("the Horseman is handed it third: $args", "sleighDueIn", args[2])
        assertEquals("his own switch second: $args", "sceneCustomization.headlessHorsemanEnabled", args[1])
        val sleigh = java.io.File(sourceRoot(), "engine/SantaSleighEffect.kt").readText()
        assertTrue(
            "0 in the sky, its countdown while it waits",
            sleigh.contains("internal val secondsToFlight: Float get() = if (flying) 0f else maxOf(0f, timeUntilNextFlight)"),
        )
    }

    /**
     * Santa's sleigh's flight as [SantaSleighEffect] keeps it, which holds a `Paint` and so cannot be made on the JVM: the
     * first 8 s after the start, a flight of 9 to 13 s from setting off to out of sight, the next 25 to 60 s after it; each
     * number read off its source ([checkAgainstTheSleigh]).
     */
    private class SleighRhythm(private val random: kotlin.random.Random) {
        val firstFlightSeconds = 8f
        var flying = false
            private set
        private var progress = 0f
        private var duration = 10f
        private var untilNext = firstFlightSeconds

        init { checkAgainstTheSleigh() }

        /** As [SantaSleighEffect.secondsToFlight]. */
        val secondsToFlight: Float get() = if (flying) 0f else maxOf(0f, untilNext)

        fun update(dt: Float) {
            if (flying) {
                progress += dt / duration
                if (progress >= 1f) { flying = false; untilNext = 25f + random.nextFloat() * 35f }
            } else {
                untilNext -= dt
                if (untilNext <= 0f) { flying = true; progress = 0f; duration = 9f + random.nextFloat() * 4f }
            }
        }

        private fun checkAgainstTheSleigh() {
            var dir: java.io.File? = java.io.File(".").absoluteFile
            var file: java.io.File? = null
            while (dir != null && file == null) {
                for (prefix in listOf("", "app/")) {
                    val f = java.io.File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/engine/SantaSleighEffect.kt")
                    if (f.isFile) file = f
                }
                dir = dir.parentFile
            }
            // Whole lines, a trailing comment cut off: a line merely holding one of these (`-= deltaSeconds * 2f`) is another.
            val sleigh = (file ?: throw AssertionError("SantaSleighEffect.kt not found")).readLines()
                .map { it.substringBefore("//").trim() }
            for (line in listOf(
                "private var timeUntilNextFlight = 8f",
                "timeUntilNextFlight -= deltaSeconds",
                "flightProgress = 0f",
                "flightProgress += deltaSeconds / flightDuration",
                "} else if (flightProgress >= 1f) {",
                "timeUntilNextFlight = 25f + Random.nextFloat() * 35f",
                "flightDuration = 9f + Random.nextFloat() * 4f",
                "if (timeUntilNextFlight <= 0f) {",
            )) assertTrue("the sleigh's rhythm: $line", line in sleigh)
        }
    }

    /**
     * **The rider stands as tall as Santa, 1.3 pedestrians** (decision 79; the maintainer, 2026-10-21: *«cavaliere deve
     * diventare 1,3 come babbo natale»*, and mid-round with the sizes beside the sleigh *«A: alto come Babbo
     * (consigliata)»*): what is seen of him, collar to boot, on every screen where neither group is held at its sprites'
     * detail -- 49 px on the BV6600, Santa's.
     */
    @Test
    fun `the rider stands as tall as Santa, 1_3 pedestrians, on every screen`() {
        for (h in listOf(800f, 1440f, 2340f)) {
            // Each in screen pixels, from its own group's units: the two frames meet only as pixels.
            val pedestrian = SantaSleighScale.pedestrianPx(h)
            val riderPx = riderPx(h)
            val santaPx = santaPx(h)
            assertEquals("$h px: the rider in pedestrians", 1.3f, riderPx / pedestrian, 0.01f)
            assertEquals("$h px: the rider against Santa", santaPx, riderPx, santaPx * 0.01f)
        }
        assertEquals("on the BV6600", 49f, riderPx(1440f), 0.5f)
    }

    /** The rider's height on a screen [h] px tall, in its pixels. */
    private fun riderPx(h: Float): Float = HeadlessHorsemanScale.RIDER_UNITS_TALL * HeadlessHorsemanScale.pxPerUnit(h)

    /** Santa's height on a screen [h] px tall, in its pixels. */
    private fun santaPx(h: Float): Float = SantaSleighScale.SANTA_UNITS_TALL * SantaSleighScale.pxPerUnit(h)

    private fun sourceRoot(): java.io.File {
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper")
                if (f.isDirectory) return f
            }
            dir = dir.parentFile
        }
        throw AssertionError("source root not found")
    }
}
