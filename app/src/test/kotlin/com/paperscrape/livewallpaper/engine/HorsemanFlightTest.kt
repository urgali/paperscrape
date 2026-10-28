package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **The Headless Horseman flies to the sleigh's rhythm and throws his pumpkins from his hand, from a fixed pool**
 * (DESIGN_NOTES.md §12, decisions 79 and 84): the first flight 8 s after the switch, a flight of 9 to 13 s at 10 to 26 % of
 * the screen's height, the next 25 to 60 s after the last ended; pumpkins thrown while he crosses the screen, from 12 to
 * 88 % of the crossing -- the first and the last within a tenth of the width beyond an edge on a screen 720 px wide or
 * more, his middle still off the screen there --, never more at once than the pool holds, a slot taken and given back
 * rather than an object made, so a throw allocates nothing. And the throw: the arm winds back, swings forward and lets the
 * pumpkin go from his hand, in both ways of the flight; the pumpkin goes on with the group's speed and a push, rises a few
 * pixels and lands on the pavement; his hand stays empty a moment and a new one grows in it.
 */
class HorsemanFlightTest {

    private val w = 720f
    private val h = 1440f
    private val dt = 1f / 30f

    /** The group's scale on the BV6600 ([HeadlessHorsemanScale.pxPerUnit] at 1 440 px). */
    private val BV6600_PX_PER_UNIT = HeadlessHorsemanScale.pxPerUnit(1440f)

    @Test
    fun `the first flight sets off after 8 seconds, and they come at the sleigh's pace`() {
        val f = HorsemanFlight()
        f.setGroupGeometry(flameHalf = 5f, groupHalfWidth = 60f, pxPerUnit = 1.235f)
        var frame = 0
        var t = 0f
        val starts = mutableListOf<Float>()
        val lengths = mutableListOf<Float>()
        var wasFlying = false
        var started = 0f
        while (t < 3600f) {
            f.update(dt, enabled = true, sleighDueIn = Float.POSITIVE_INFINITY, screenWidth = w, screenHeight = h)
            // The frames' time counted, not summed: a Float summed frame by frame runs 0.34 % fast past 2 048 s, where a
            // gap of 60 s reads 60.2.
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
        f.setGroupGeometry(flameHalf = 5f, groupHalfWidth = 60f, pxPerUnit = 1.235f)
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
                assertTrue("let fall at ${f.flightProgress}", f.flightProgress in HorsemanFlight.DROP_FROM..HorsemanFlight.DROP_UNTIL + dt / 9f + 1e-5f)
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
     * The pumpkins' rhythm in a flight: the first 1.4 s after he sets off -- or at 12 % of the crossing where that comes
     * later --, then one every 1.1 to 1.9 s.
     */
    @Test
    fun `the first pumpkin falls 1_4 s into a flight, then one every 1_1 to 1_9 s`() {
        val f = HorsemanFlight()
        f.setGroupGeometry(flameHalf = 5f, groupHalfWidth = 60f, pxPerUnit = 1.235f)
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

    /**
     * His gap, his flight and his height are the sleigh's, each read off the sleigh's own line. **Whole lines, a trailing
     * comment cut off**, because a check by substring lets a changed line through: the height with 120 px added after it
     * is another line, and a sleigh flying lower than his band is not the band he flies in.
     */
    @Test
    fun `the rhythm is the sleigh's, read off its source`() {
        val sleigh = sourceLines("engine/SantaSleighEffect.kt")
        for (line in listOf(
            "timeUntilNextFlight = 25f + SceneRandom.source.nextFloat() * 35f",
            "flightDuration = 9f + SceneRandom.source.nextFloat() * 4f",
            "flightY = screenHeight * (0.10f + SceneRandom.source.nextFloat() * 0.16f)",
        )) assertTrue("the sleigh's line: $line", line in sleigh)
        assertEquals(25f, HorsemanFlight.GAP_MIN_SECONDS, 0f)
        assertEquals(60f, HorsemanFlight.GAP_MIN_SECONDS + HorsemanFlight.GAP_SPREAD_SECONDS, 0f)
        assertEquals(9f, HorsemanFlight.FLIGHT_MIN_SECONDS, 0f)
        assertEquals(13f, HorsemanFlight.FLIGHT_MIN_SECONDS + HorsemanFlight.FLIGHT_SPREAD_SECONDS, 0f)
        assertEquals(0.10f, HorsemanFlight.FLIGHT_Y_MIN, 0f)
        assertEquals(0.16f, HorsemanFlight.FLIGHT_Y_SPREAD, 0f)
    }

    /**
     * **He keeps out of Santa's sleigh's way** (DESIGN_NOTES.md §12, decision 79; the maintainer, 2026-10-22: *«A: il
     * cavaliere aspetta anche la slitta in arrivo (consigliata)»*): beside the sleigh's own rhythm ([SleighRhythm]), an hour
     * of each of eight runs, the two are never in the sky together and the sky is empty at least
     * [HorsemanFlight.AFTER_SLEIGH_SECONDS] between them, whichever goes first -- he never sets off with the sleigh up, nor less
     * than that after it has gone, and the sleigh never sets off while he flies, nor less than that after he has gone; a turn
     * of his the sleigh held sets off just that long after it has gone; at the wallpaper's start, where both are due at 8 s,
     * the sleigh goes and he follows 3 s after it has gone.
     */
    @Test
    fun `he and Santa's sleigh are never in the sky together, and a held turn goes 3 s after it`() {
        val after = HorsemanFlight.AFTER_SLEIGH_SECONDS
        assertEquals(3f, after, 0f)
        for (seed in 1..8) {
            val sleigh = SleighRhythm(kotlin.random.Random(seed))
            val f = HorsemanFlight(kotlin.random.Random(1000 + seed))
            f.setGroupGeometry(flameHalf = 5f, groupHalfWidth = 60f, pxPerUnit = 1.235f)
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
     * off, never: a Horseman updated before the sleigh, or told nothing, would set off with it at the
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
            "val sleighDueIn = if (sceneCustomization.santaEnabled) santaSleighEffect.secondsToFlight else Float.POSITIVE_INFINITY" in
                sourceLines("engine/PaperRenderer.kt"),
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
        assertTrue(
            "0 in the sky, its countdown while it waits",
            "internal val secondsToFlight: Float get() = if (flying) 0f else maxOf(0f, timeUntilNextFlight)" in
                sourceLines("engine/SantaSleighEffect.kt"),
        )
    }

    /** A flight run on a seeded draw at the BV6600's size and scale; [each] is handed it after every frame. */
    private fun run(seconds: Float, seed: Int, each: (HorsemanFlight, Float) -> Unit) {
        val f = HorsemanFlight(kotlin.random.Random(seed))
        f.setGroupGeometry(flameHalf = 5f, groupHalfWidth = 60f, pxPerUnit = BV6600_PX_PER_UNIT)
        var frame = 0
        var t = 0f
        while (t < seconds) {
            f.update(dt, true, Float.POSITIVE_INFINITY, w, h)
            t = ++frame * dt
            each(f, t)
        }
    }

    /** The slot a pumpkin was just thrown into in this frame, or -1: its age is the frame's or less, and his hand is empty since. */
    private fun justThrown(f: HorsemanFlight): Int {
        if (f.sinceThrow > 0f) return -1
        for (i in 0 until HorsemanFlight.POOL_SIZE) if (f.dropActive[i] && f.dropAge[i] == 0f) return i
        return -1
    }

    /**
     * **Thrown from his hand, in both ways of the flight**: a pumpkin starts where the pumpkin in his hand is with his arm
     * at [HorsemanFlight.RELEASE_DEGREES] -- his seat as last drawn, the arm's pivot, the hand turned about it -- and
     * flying left (the drawings' way) that is ahead of the group's middle and above his seat, flying right the same point
     * mirrored: the two ways' offsets from the group's middle are each other's mirror. Thrown, it keeps the group's facing,
     * mirrored with him flying right.
     */
    @Test
    fun `a pumpkin leaves his hand where the arm lets it go, in both ways of the flight`() {
        val offsets = HashMap<Boolean, Pair<Float, Float>>()
        var throws = 0
        run(1200f, 11) { f, _ ->
            val i = justThrown(f)
            if (i < 0) return@run
            throws++
            val dx = f.dropX[i] - f.currentX(w)
            val dy = f.dropStartY[i] - (f.flightY + f.bobPx)
            assertEquals("x from his hand", f.handX(w, HorsemanFlight.RELEASE_DEGREES), f.dropX[i], 0.01f)
            assertEquals("y from his hand", f.handY(HorsemanFlight.RELEASE_DEGREES), f.dropStartY[i], 0.01f)
            // In the drawings' frame his hand at the release is ahead of the group's middle (to the left) and high above it.
            val ahead = if (f.reverse) -dx else dx
            assertTrue("ahead of the middle, flying ${if (f.reverse) "left" else "right"}: $dx", ahead > 5f && ahead < 15f)
            assertTrue("high above the middle: $dy", dy < -40f && dy > -70f)
            // And it keeps the facing the group is drawn with as it falls: +1 flying left, the drawings' way, -1 flying right.
            assertEquals("the group's facing", if (f.reverse) 1f else -1f, f.dropDir[i], 0f)
            // Turned as his arm held it as it let it go, then turning as it falls.
            assertEquals("turned as his arm held it", f.armDegrees(), f.dropDegrees(i), 0.01f)
            f.dropAge[i] = 1f
            assertEquals("turning as it falls", HorsemanFlight.RELEASE_DEGREES + HeadlessHorsemanEffect.DROP_TURN_DEGREES_PER_SECOND, f.dropDegrees(i), 0.01f)
            f.dropAge[i] = 0f
            offsets[f.reverse] = dx to dy
        }
        assertTrue("throws seen: $throws", throws > 100)
        val left = offsets.getValue(true)
        val right = offsets.getValue(false)
        assertEquals("mirrored across the middle", left.first, -right.first, 0.01f)
        assertEquals("at one height", left.second, right.second, 0.01f)
        // From his seat as last drawn: a seat a unit forward and up moves his hand a unit forward and up, in either way.
        for (reverse in listOf(false, true)) {
            val g = HorsemanFlight(kotlin.random.Random(if (reverse) 7 else 8))
            g.setGroupGeometry(5f, 60f, BV6600_PX_PER_UNIT)
            while (!g.flying || g.reverse != reverse) g.update(dt, true, Float.POSITIVE_INFINITY, w, h)
            val x0 = g.handX(w, HorsemanFlight.RELEASE_DEGREES)
            val y0 = g.handY(HorsemanFlight.RELEASE_DEGREES)
            g.seatX -= 1f
            g.seatY -= 1f
            val forward = if (reverse) -1f else 1f
            assertEquals("a unit forward", forward * BV6600_PX_PER_UNIT, g.handX(w, HorsemanFlight.RELEASE_DEGREES) - x0, 0.01f)
            assertEquals("a unit up", -BV6600_PX_PER_UNIT, g.handY(HorsemanFlight.RELEASE_DEGREES) - y0, 0.01f)
        }
        // And the renderer hands it the seat it drew him on, every frame he is drawn; the pumpkins in the air are drawn turned.
        val renderer = java.io.File(sourceRoot(), "engine/PaperRenderer.kt").readLines().map { it.substringBefore("//").trim() }
        assertTrue("the seat drawn", "flight.seatX = headlessHorsemanDrawer.seatX(effectElapsed)" in renderer)
        assertTrue("with his bob", "flight.seatY = headlessHorsemanDrawer.seatY(effectElapsed)" in renderer)
        val effect = java.io.File(sourceRoot(), "engine/HeadlessHorsemanEffect.kt").readLines().map { it.substringBefore("//").trim() }
        assertTrue("a pumpkin in the air drawn at its turn", "lanternDraw.blit(x, y, f.dropDir[i], f.dropDegrees(i), a)" in effect)
    }

    /**
     * **Thrown, it goes on and down to the pavement**: it leaves at the group's speed and a push forward of
     * [HorsemanFlight.THROW_FORWARD_UNITS_PER_SECOND] (70 px a second on the BV6600) and up of
     * [HorsemanFlight.THROW_UP_UNITS_PER_SECOND] (110), which the fall loses; it rises a few pixels -- 4 to 16 on the BV6600 --,
     * then falls and lands where it was aimed, on the pavement's band, at the end of its 1.6 to 2.1 s, ahead of where it
     * was thrown in the flight's way.
     */
    @Test
    fun `thrown, it goes on with the group and a push, rises a few pixels and lands on the pavement`() {
        var checked = 0
        run(900f, 5) { f, _ ->
            val i = justThrown(f)
            if (i < 0) return@run
            val m = SantaSleighEffect.motion(f.reverse)
            val groupSpeed = (w + f.marginPx * 2f) / f.flightDuration * m
            assertEquals("the group's speed and the push", groupSpeed + m * 70f, f.dropVx[i], 0.5f)
            assertEquals("up", 110f, f.dropVy[i], 0.5f)
            // Its path, worked out from the slot, over its whole fall.
            val start = f.dropStartY[i]
            var highest = start
            var lastY = start
            var turned = false
            val n = 200
            val age = f.dropAge[i]
            for (k in 0..n) {
                f.dropAge[i] = f.dropDuration[i] * k / n
                val y = f.dropYNow(i)
                highest = minOf(highest, y)
                if (y > lastY) turned = true
                if (turned) assertTrue("never up again once falling", y >= lastY - 0.01f)
                lastY = y
            }
            assertEquals("lands where it was aimed", f.dropTargetY[i], lastY, 0.5f)
            assertTrue("on the pavement", lastY / h in SceneSpace.PAVEMENT_FAR_Y_FRACTION..SceneSpace.PAVEMENT_NEAR_Y_FRACTION)
            assertTrue("rises a few pixels: ${start - highest}", start - highest in 4f..16f)
            f.dropAge[i] = f.dropDuration[i]
            assertTrue("lands ahead of where it was thrown, the flight's way", (f.dropXNow(i) - f.dropX[i]) * m > 50f)
            f.dropAge[i] = age
            checked++
        }
        assertTrue("throws checked: $checked", checked > 50)
    }

    /**
     * **The arm's throw** (`HorsemanFlight.armDegrees`): at rest 0 until [HorsemanFlight.WIND_SECONDS] before the throw;
     * wound back to [HorsemanFlight.WIND_BACK_DEGREES] (28, towards the tail) [HorsemanFlight.SWING_SECONDS] before it; at
     * [HorsemanFlight.RELEASE_DEGREES] (-32, forward) as the pumpkin leaves; back at rest [HorsemanFlight.ARM_BACK_SECONDS]
     * after. **Every throw has its gesture**, the first of a flight longer than 11.7 s included, which waits for 12 % of the
     * crossing: the arm is wound back to 26 degrees or more in the 0.4 s before each (28 at its most, between two frames).
     */
    @Test
    fun `every throw is the arm winding back, swinging forward and letting go, then coming back`() {
        val history = ArrayDeque<Float>()
        var throws = 0
        var sinceLast = -1f
        var longFirsts = 0
        var throwsThisFlight = 0
        var woundUp = false
        run(1800f, 3) { f, _ ->
            val arm = f.armDegrees()
            if (!f.flying) throwsThisFlight = 0
            // Never a wind-up without its throw: from the moment the arm is wound back, the next thing is a throw, not the
            // arm back at rest with the pumpkin still in his hand.
            if (arm >= 26f) woundUp = true
            if (woundUp && justThrown(f) < 0 && arm == 0f) throw AssertionError("wound back and came to rest without throwing (${f.flightProgress})")
            if (justThrown(f) >= 0) {
                woundUp = false
                throws++
                if (throwsThisFlight == 0 && f.flightDuration > 11.7f) longFirsts++
                throwsThisFlight++
                assertEquals("lets go at the release", HorsemanFlight.RELEASE_DEGREES, arm, 0.01f)
                val window = history.toList().takeLast((HorsemanFlight.WIND_SECONDS / dt).toInt())
                assertTrue("wound back before it: ${window.maxOrNull()}", (window.maxOrNull() ?: 0f) >= 26f)
                assertTrue("from rest", window.first() < 3f)
                sinceLast = 0f
            } else if (sinceLast >= 0f) {
                sinceLast += dt
                // Back at rest from the end of its return to the next throw's wind-up (the next throw 1.1 s after at the soonest).
                val nextWind = HorsemanFlight.DROP_EVERY_MIN - HorsemanFlight.WIND_SECONDS
                if (sinceLast in HorsemanFlight.ARM_BACK_SECONDS + dt..nextWind - dt) assertEquals("back at rest", 0f, arm, 0.001f)
            }
            assertTrue("within the throw's angles: $arm", arm in HorsemanFlight.RELEASE_DEGREES - 0.01f..HorsemanFlight.WIND_BACK_DEGREES + 0.01f)
            history.addLast(arm)
            if (history.size > 60) history.removeFirst()
        }
        assertTrue("throws: $throws, first throws of long flights: $longFirsts", throws > 150 && longFirsts > 3)
        // The wind-up's shape on one throw, frame by frame: up to the most, then down through 0 to the release.
        val f = HorsemanFlight(kotlin.random.Random(1))
        f.setGroupGeometry(5f, 60f, BV6600_PX_PER_UNIT)
        val angles = ArrayList<Float>()
        var t = 0f
        var thrown = false
        while (!thrown && t < 120f) {
            f.update(dt, true, Float.POSITIVE_INFINITY, w, h)
            t += dt
            angles += f.armDegrees()
            thrown = justThrown(f) >= 0
        }
        val last = angles.takeLast((HorsemanFlight.WIND_SECONDS / dt).toInt() + 1)
        // Eased: the arm leaves rest slowly, as a hand does -- its first frame of the wind-up moves it under 2 degrees.
        val start = last.indexOfFirst { it > 0f }
        assertTrue("it eases out of rest: ${last[start]}", last[start] < 2f)
        val top = last.indexOf(last.max())
        for (k in 1..top) assertTrue("winding back", last[k] >= last[k - 1] - 0.001f)
        for (k in top + 1 until last.size) assertTrue("swinging forward", last[k] <= last[k - 1] + 0.001f)
        assertEquals("the most, 28, between two frames", HorsemanFlight.WIND_BACK_DEGREES, last.max(), 2f)
    }

    /**
     * **A wound arm always throws, to the end of the part of the crossing he throws in**: a throw that falls due between two
     * frames, the second already past [HorsemanFlight.DROP_UNTIL], is thrown on that frame -- the moment it fell due,
     * `flightProgress + dropTimer / flightDuration`, is what is held to the window, as [HorsemanFlight.secondsToThrow] holds
     * it --, so the arm that wound up for it lets it go rather than coming back to rest with it. Whenever a frame ends with
     * the next throw due within a frame, the next frame throws. Few flights meet the end of the window so: the runs, over
     * 2 000 flights, must meet it at least 15 times, or the case is not being exercised.
     */
    @Test
    fun `a throw due by the end of the part he throws in is thrown, on a frame past it too`() {
        var throws = 0
        var late = 0
        var flights = 0
        for (seed in 21..24) {
            var dueThisFrame = false
            var wasFlying = false
            run(30000f, seed) { f, _ ->
                val thrown = justThrown(f) >= 0
                if (f.flying && !wasFlying) flights++
                wasFlying = f.flying
                if (dueThisFrame) assertTrue("due within this frame and not thrown, at ${f.flightProgress} of the crossing", thrown)
                if (thrown) {
                    throws++
                    if (f.flightProgress > HorsemanFlight.DROP_UNTIL) late++
                }
                val ahead = f.secondsToThrow()
                dueThisFrame = ahead >= 0f && ahead < dt - 1e-4f
            }
        }
        assertTrue("flights: $flights, throws: $throws, thrown on a frame past the window: $late", flights > 2000 && throws > 10000 && late >= 15)
    }

    /**
     * **The gesture is the one chosen** (DESIGN_NOTES.md §12, decision 84; the maintainer, 2026-10-24: *«Z3: col gesto
     * (consigliata)»*): back 28 degrees in 0.25 s, forward to -32 in 0.15 s where it lets go, back in 0.45 s; the hand empty
     * 0.25 s, then the new pumpkin grows from a third of its size in 0.35 s; the push 70 px a second forward and 110 up on the
     * BV6600. And the pumpkins in the air are drawn in front of the group.
     */
    @Test
    fun `the gesture and the throw are the ones filmed, and the pumpkins in the air are drawn in front of him`() {
        assertEquals(28f, HorsemanFlight.WIND_BACK_DEGREES, 0f)
        assertEquals(-32f, HorsemanFlight.RELEASE_DEGREES, 0f)
        assertEquals(0.25f, HorsemanFlight.WIND_SECONDS - HorsemanFlight.SWING_SECONDS, 1e-6f)
        assertEquals(0.15f, HorsemanFlight.SWING_SECONDS, 0f)
        assertEquals(0.45f, HorsemanFlight.ARM_BACK_SECONDS, 0f)
        assertEquals(0.25f, HorsemanFlight.EMPTY_SECONDS, 0f)
        assertEquals(0.6f, HorsemanFlight.EMPTY_SECONDS + HorsemanFlight.GROW_SECONDS, 1e-6f)
        assertEquals("70 px a second forward on the BV6600", 70f, HorsemanFlight.THROW_FORWARD_UNITS_PER_SECOND * BV6600_PX_PER_UNIT, 0.5f)
        assertEquals("110 up", 110f, HorsemanFlight.THROW_UP_UNITS_PER_SECOND * BV6600_PX_PER_UNIT, 0.5f)
        val effect = java.io.File(sourceRoot(), "engine/HeadlessHorsemanEffect.kt").readLines().map { it.substringBefore("//").trim() }
        val drawStart = effect.indexOf("fun draw(canvas: SceneCanvas, elapsedSeconds: SceneTime, screenWidth: Float, riderDraw: RiderBlit, lanternDraw: LanternBlit) {")
        assertTrue("the effect's draw", drawStart >= 0)
        val body = effect.drop(drawStart).take(14)
        val group = body.indexOfFirst { it.startsWith("riderDraw.blit(") }
        val drops = body.indexOfFirst { it.startsWith("for (i in 0 until HorsemanFlight.POOL_SIZE) if (f.dropActive[i]) drawDrop(canvas, i, lanternDraw)") }
        assertTrue("the group, then the pumpkins in the air in front of it: $group, $drops", group in 0 until drops)
        assertEquals("the pumpkins drawn once", 1, body.count { it.contains("drawDrop(canvas, i, lanternDraw)") })
    }

    /**
     * **His hand empty, then a new pumpkin**: whole while he holds it up; none for [HorsemanFlight.EMPTY_SECONDS] after a
     * throw; then one growing from a third of its size to the whole in [HorsemanFlight.GROW_SECONDS]: whole again 0.6 s
     * after the throw, before the next (1.1 s at the soonest).
     */
    @Test
    fun `his hand is empty after a throw, and a new pumpkin grows in it from a third of its size`() {
        var throws = 0
        var since = -1f
        var before = 1f
        run(900f, 9) { f, _ ->
            val k = f.handPumpkinScale()
            if (justThrown(f) >= 0) {
                throws++
                assertEquals("whole just before", 1f, before, 0f)
                since = 0f
            } else if (since >= 0f) {
                since += dt
            }
            if (since in 0f..HorsemanFlight.EMPTY_SECONDS - dt) assertEquals("empty at $since", 0f, k, 0f)
            if (since in HorsemanFlight.EMPTY_SECONDS + dt..HorsemanFlight.EMPTY_SECONDS + HorsemanFlight.GROW_SECONDS - dt) {
                assertTrue("growing at $since: $k", k > 1f / 3f - 0.001f && k < 1f)
            }
            if (since >= HorsemanFlight.EMPTY_SECONDS + HorsemanFlight.GROW_SECONDS + dt) assertEquals("whole again", 1f, k, 0f)
            before = k
        }
        assertTrue("throws: $throws", throws > 50)
        val g = HorsemanFlight()
        assertEquals("a flight starts with it whole", 1f, g.handPumpkinScale(), 0f)
        assertEquals("the new one starts at a third", 1f / 3f, HorsemanFlight.GROWS_FROM, 0f)
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
                "timeUntilNextFlight = 25f + SceneRandom.source.nextFloat() * 35f",
                "flightDuration = 9f + SceneRandom.source.nextFloat() * 4f",
                "if (timeUntilNextFlight <= 0f) {",
            )) assertTrue("the sleigh's rhythm: $line", line in sleigh)
        }
    }

    /**
     * **The rider stands as tall as Santa, 1.3 pedestrians** (DESIGN_NOTES.md §12, decision 79; the maintainer, 2026-10-21:
     * *«cavaliere deve diventare 1,3 come babbo natale»*, and with the sizes beside the sleigh *«A: alto come Babbo
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

    /** The lines of the app's source file [path] (under the package's root), each trimmed and cut at a `//` comment. */
    private fun sourceLines(path: String): List<String> =
        java.io.File(sourceRoot(), path).readLines().map { it.substringBefore("//").trim() }

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
