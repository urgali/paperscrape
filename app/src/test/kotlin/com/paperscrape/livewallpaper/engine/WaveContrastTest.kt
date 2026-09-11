package com.paperscrape.livewallpaper.engine

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The wave's two papers measured against the water they lie on, over every theme that shows a
 * lake, the clock in five-minute steps and the three weathers that draw waves.
 *
 * A wave (v4.28) is two tintable masks over the mirror: a **body** (the face under the lip) and the
 * **foam**. The first proposal tinted the body toward black by a fixed 40 of luma and the foam
 * toward white by 60, both chosen by eye, and at night the body became a black silhouette -- a
 * rock. This is the v4.27 rain problem again: one direction does not hold along the day, and the
 * numbers have to be placed between a measured floor and a measured signal rather than chosen.
 *
 * **The floor.** The mirror is not one colour: it is a vertical gradient from the sky-share tone
 * at the far edge to the lake colour at the near edge, so the water *under one wave* already
 * varies in luma over the wave's own height. A body that differs from the surface by less than
 * that is not distinguishable from the gradient. The floor is that span, measured over the sweep.
 *
 * **The signal.** The daytime frame the maintainer read as a wave ("di giorno passa"): Beach at
 * noon under theme rain, where the body gap was 40 and the foam gap 60. Those two numbers are the
 * signal, one per paper.
 *
 * **The gate** sits halfway between floor and signal, as `PRECIPITATION_MIN_LUMA_GAP` does.
 *
 * **The direction** is the cheaper carry, exactly `standOffFromSky`'s rule: toward black while
 * the surface is bright enough that black is the shorter road, toward white otherwise. The foam
 * always sits above the body by the foam gate, so the two papers never trade places.
 *
 * **What v4.28 added to the derivation, and why.** The gate is a *floor* -- the least that can be
 * told apart from the gradient -- and the phase-3 photographs aimed the renderer at it everywhere.
 * By day, low sun, rain and storm that was judged enough; at night the three shapes came out
 * "discreet". So the night end of [WaveTint.gapAt] is the **signal** rather than the gate: the
 * other end of the same derivation, the gap that was actually read as a wave. No new number was
 * invented -- what moved is which measured end the renderer aims at, and when. The tests below
 * assert the floor still holds at every point of the crossfade.
 */
class WaveContrastTest {

    private class Situation(
        val theme: String,
        val hour: Float,
        val weather: String,
        val mirrorTop: Int,
        val lake: Int,
        val bandPx: Float,
    ) {
        val label get() = "$theme %02d:%02d %s".format(hour.toInt(), ((hour % 1f) * 60).toInt(), weather)
        val dayBlend: Float get() = SunPositionCalculator.compute(hour24 = hour).dayBlend.coerceIn(0f, 1f)
    }

    /**
     * The fraction of the band one wave stands over, **as the shipped gates were derived**.
     *
     * The number is the phase-2 shape on the Beach band -- 36 units on a 9 m, 132-unit canvas is
     * 51.5 px against Beach's 345.6 px of water -- and it is kept here as the *provenance of the
     * shipped constants*, reproduced rather than asserted from memory so the derivation can be
     * checked. What the shape that actually ships does to this number is measured separately, in
     * `the shipped shape stands over more of the band than the derivation assumed`, and it is
     * larger: that is item 83 of `BACKLOG_v4_28.md` and it is not fixed here, because raising the
     * day gate would change every daytime frame the maintainer approved from the phase-3
     * photographs, and no photograph exists of the raised one.
     */
    private val derivationBandFraction = 0.15f

    /** How tall the shipped wave really stands, in pixels on the reference screen, at its largest. */
    private val shippedWaveHeightPx =
        PaperRenderer.WAVE_UNITS_TALL *
            (PaperRenderer.WAVE_METRES_LONG * SceneSpace.LAKE_PIXELS_PER_METRE / PaperRenderer.WAVE_UNITS_WIDE) *
            SceneSpace.sceneScale(REFERENCE_SCREEN_HEIGHT) * WAVE_MAX_LANE_SCALE

    private val all: List<Situation> by lazy { situations() }

    private fun situations(): List<Situation> {
        val out = mutableListOf<Situation>()
        val weathers = listOf(
            0f to "theme-rain",
            StormAtmosphere.strength(PrecipitationType.RAIN, 0.6f, false, 0.9f) to "live-rain",
            StormAtmosphere.strength(PrecipitationType.RAIN, 0.6f, true, 1f) to "thunderstorm",
        )
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            if (!c.lake.visible) continue
            for (step in 0 until 24 * 12) {
                val hour = step / 12f
                val phase = SunPositionCalculator.compute(hour24 = hour)
                val blend = phase.dayBlend.coerceIn(0f, 1f)
                val lake = blendARGB(c.lake.colorNight, c.lake.colorDay, blend)
                // The band the renderer actually draws for this theme: `drawLake`'s own expression.
                val bandPx = REFERENCE_SCREEN_HEIGHT * 0.16f * c.lake.height.coerceIn(0f, 1f)
                for ((storm, name) in weathers) {
                    val horizon = StormAtmosphere.dimSky(skyHorizon(c, phase), storm)
                    out += Situation(theme.id, hour, name, blendARGB(lake, horizon, PaperRenderer.LAKE_MIRROR_SKY_SHARE), lake, bandPx)
                }
            }
        }
        return out
    }

    private fun skyHorizon(c: SceneCustomization, phase: SunPositionCalculator.DayPhase): Int {
        val blend = phase.dayBlend.coerceIn(0f, 1f)
        if (c.horrorSkyEnabled) return blendARGB(0xFFB03A06.toInt(), 0xFFF07A10.toInt(), blend)
        val twilightWeight = (1f - kotlin.math.abs(blend * 2f - 1f)).coerceIn(0f, 1f)
        val twilight = if (phase.progress < 0.5f) c.sky.colorSunriseLow else c.sky.colorSunsetLow
        val nightToTwilight = blendARGB(c.sky.colorNightLow, twilight, blend)
        return blendARGB(nightToTwilight, c.sky.colorDayLow, (blend - twilightWeight * 0.3f).coerceIn(0f, 1f))
    }

    // ------------------------------------------------------------------ the derivation

    /** The luma the water varies by under one wave, at its worst over the sweep: the floor. */
    private fun floor(): Pair<Float, Situation> {
        var worst = 0f
        var at = all.first()
        for (s in all) {
            val span = kotlin.math.abs(luma(s.mirrorTop) - luma(s.lake)) * derivationBandFraction
            if (span > worst) { worst = span; at = s }
        }
        return worst to at
    }

    private fun signalSituation(): Situation =
        all.first { it.theme == "beach" && it.weather == "theme-rain" && kotlin.math.abs(it.hour - 12f) < 0.01f }

    /** The first proposal's body rule at the signal frame: min(40, 0.45 * luma). */
    private fun phase2BodyGap(surfaceLuma: Float) = kotlin.math.min(40f, surfaceLuma * 0.45f)

    @Test
    fun `the gates sit between the gradient under a wave and the frame that read as a wave`() {
        val (floorGap, floorAt) = floor()
        val signal = signalSituation()
        val signalLuma = luma(signal.mirrorTop)
        val bodySignal = phase2BodyGap(signalLuma)
        val foamSignal = 60f
        val bodyGate = (floorGap + bodySignal) / 2f
        val foamGate = (floorGap + foamSignal) / 2f
        println("WAVE floor %.2f at %s | signal body %.2f foam %.2f at %s (surface luma %.2f) | gates body %.2f foam %.2f"
            .format(floorGap, floorAt.label, bodySignal, foamSignal, signal.label, signalLuma, bodyGate, foamGate))
        assertTrue("the floor must be below the signal or the derivation is meaningless", floorGap < bodySignal)
        assertTrue(kotlin.math.abs(bodyGate - PaperRenderer.WAVE_BODY_LUMA_GAP) < 0.05f)
        assertTrue(kotlin.math.abs(foamGate - PaperRenderer.WAVE_FOAM_LUMA_GAP) < 0.05f)
    }

    /**
     * The night target is the signal, not a third number.
     *
     * This is the whole of what v4.28 changed about the arithmetic, so it is asserted rather than
     * left in a comment: if someone later wants a stronger night sea they have to move the signal,
     * which means re-reading the frame the signal came from.
     */
    @Test
    fun `the night target is the measured signal and the day target is the gate`() {
        val signal = signalSituation()
        val signalLuma = luma(signal.mirrorTop)
        assertTrue(
            "the night body target must be the signal itself",
            kotlin.math.abs(PaperRenderer.WAVE_BODY_LUMA_GAP_NIGHT - phase2BodyGap(signalLuma)) < 0.05f,
        )
        assertTrue(
            "the night foam target must be the signal itself",
            kotlin.math.abs(PaperRenderer.WAVE_FOAM_LUMA_GAP_NIGHT - 60f) < 0.05f,
        )
        assertTrue(
            "full day must aim at the gate the photographs approved",
            kotlin.math.abs(WaveTint.gapAt(1f, PaperRenderer.WAVE_BODY_LUMA_GAP, PaperRenderer.WAVE_BODY_LUMA_GAP_NIGHT) - PaperRenderer.WAVE_BODY_LUMA_GAP) < 0.001f,
        )
        assertTrue(
            "full night must aim at the signal",
            kotlin.math.abs(WaveTint.gapAt(0f, PaperRenderer.WAVE_BODY_LUMA_GAP, PaperRenderer.WAVE_BODY_LUMA_GAP_NIGHT) - PaperRenderer.WAVE_BODY_LUMA_GAP_NIGHT) < 0.001f,
        )
        // The floor holds at every point of the crossfade, which is what makes the gate a floor.
        for (step in 0..100) {
            val d = step / 100f
            assertTrue(
                "the aimed body gap dropped under the gate at dayBlend $d",
                WaveTint.gapAt(d, PaperRenderer.WAVE_BODY_LUMA_GAP, PaperRenderer.WAVE_BODY_LUMA_GAP_NIGHT) >= PaperRenderer.WAVE_BODY_LUMA_GAP - 0.001f,
            )
            assertTrue(
                "the aimed foam gap dropped under the gate at dayBlend $d",
                WaveTint.gapAt(d, PaperRenderer.WAVE_FOAM_LUMA_GAP, PaperRenderer.WAVE_FOAM_LUMA_GAP_NIGHT) >= PaperRenderer.WAVE_FOAM_LUMA_GAP - 0.001f,
            )
        }
    }

    @Test
    fun `one direction for the body does not hold along the day, and the cheaper side does`() {
        var darkestSurface = 255f
        var darkestAt: Situation? = null
        var blackOnlyFails = 0
        var flipsWorstTheme = ""
        var flipsWorst = 0
        var inverted = 0
        val invertedThemes = sortedSetOf<String>()
        val perTheme = all.groupBy { it.theme to it.weather }
        for ((key, list) in perTheme) {
            var flips = 0
            var last: Boolean? = null
            for (s in list.sortedBy { it.hour }) {
                val l = luma(s.mirrorTop)
                // The gaps the renderer actually aims at in this situation.
                val bodyGate = WaveTint.gapAt(s.dayBlend, PaperRenderer.WAVE_BODY_LUMA_GAP, PaperRenderer.WAVE_BODY_LUMA_GAP_NIGHT)
                val foamGate = WaveTint.gapAt(s.dayBlend, PaperRenderer.WAVE_FOAM_LUMA_GAP, PaperRenderer.WAVE_FOAM_LUMA_GAP_NIGHT)
                if (l < darkestSurface) { darkestSurface = l; darkestAt = s }
                // the first proposal: always toward black -> the body luma
                val phase2Body = l - phase2BodyGap(l)
                if (phase2Body < 40f) blackOnlyFails++
                val towardBlack = WaveTint.bodyTowardBlack(l, bodyGate)
                if (last != null && last != towardBlack) flips++
                last = towardBlack
                if (WaveTint.inverted(l, foamGate)) { inverted++; invertedThemes += s.theme }
                val body = WaveTint.bodyLuma(l, bodyGate, foamGate)
                val foam = WaveTint.foamLuma(l, bodyGate, foamGate)
                assertTrue("${s.label}: body ${"%.1f".format(body)} within the gate of surface ${"%.1f".format(l)}", kotlin.math.abs(body - l) >= bodyGate - 0.01f)
                assertTrue("${s.label}: foam ${"%.1f".format(foam)} within the gate of surface ${"%.1f".format(l)}", kotlin.math.abs(foam - l) >= foamGate - 0.01f)
                assertTrue("${s.label}: foam ${"%.1f".format(foam)} and body ${"%.1f".format(body)} closer than the body gate", kotlin.math.abs(foam - body) >= bodyGate - 0.01f)
                assertTrue("${s.label}: a luma left the 0..255 range", body in 0f..255f && foam in 0f..255f)
            }
            if (flips > flipsWorst) { flipsWorst = flips; flipsWorstTheme = "${key.first} ${key.second}" }
        }
        println("WAVE darkest surface luma %.2f at %s | black-only body under 40 luma in %d of %d situations | most direction flips per day %d (%s) | papers inverted in %d situations, themes %s"
            .format(darkestSurface, darkestAt?.label, blackOnlyFails, all.size, flipsWorst, flipsWorstTheme, inverted, invertedThemes))
        assertTrue("the black-only rule really does black out at night", blackOnlyFails > 0)
        assertTrue("the direction changes at most twice a day", flipsWorst <= 2)
    }

    /**
     * **The inverted case, named and counted so it cannot drift unnoticed.**
     *
     * Where the surface passes `255 - foamGap` there is no white left for the foam to reach, so the
     * papers invert: the foam goes dark and the body darker still. The sweep finds it on exactly
     * one theme -- Tundra, whose water is ice (`#BFE3EE`) -- around dawn, and raising the night
     * target widens the window it happens in, because a larger foam gap runs out of white sooner.
     * That widening is the price of the night sea and it is measured here rather than discovered
     * later; v4.28 looked at the worst of these situations on the device (see the pass report).
     *
     * The assertion is that the arrangement is still *correct* wherever it fires -- both gaps kept,
     * order kept -- and that it stays confined to a theme with no boats and no dolphins on it.
     */
    @Test
    fun `where the water is too pale for foam the papers invert, and only there`() {
        var count = 0
        val themes = sortedSetOf<String>()
        var palest = 0f
        var palestAt: Situation? = null
        for (s in all) {
            val l = luma(s.mirrorTop)
            val foamGate = WaveTint.gapAt(s.dayBlend, PaperRenderer.WAVE_FOAM_LUMA_GAP, PaperRenderer.WAVE_FOAM_LUMA_GAP_NIGHT)
            if (!WaveTint.inverted(l, foamGate)) continue
            count++
            themes += s.theme
            if (l > palest) { palest = l; palestAt = s }
            val bodyGate = WaveTint.gapAt(s.dayBlend, PaperRenderer.WAVE_BODY_LUMA_GAP, PaperRenderer.WAVE_BODY_LUMA_GAP_NIGHT)
            val body = WaveTint.bodyLuma(l, bodyGate, foamGate)
            val foam = WaveTint.foamLuma(l, bodyGate, foamGate)
            assertTrue("${s.label}: inverted foam must still be the lighter of the two papers", foam > body)
            assertTrue("${s.label}: inverted foam must keep its gap from the surface", l - foam >= foamGate - 0.01f)
            assertTrue("${s.label}: inverted body must keep its gap from the foam", foam - body >= bodyGate - 0.01f)
            assertTrue("${s.label}: a luma left the 0..255 range", body in 0f..255f && foam in 0f..255f)
        }
        // **How the inverted case is actually reached, which is not how the sweep is labelled.**
        // The three columns are storm *strengths*, not weather settings: "theme-rain" is strength
        // zero, meaning no storm dimming. Tundra's own precipitation is snow, so a wave is never
        // drawn there by default at all -- the case is reached when a **user** switches Tundra's
        // precipitation to rain, which draws rain without dimming the sky. The two live columns dim
        // it, and a dimmed Tundra never gets pale enough to invert: every one of the situations
        // below is in the undimmed column. That is why the frame photographed for this case is
        // Tundra with rain switched on by hand rather than under live weather.
        val dimmedInversions = all.count { s ->
            s.weather != "theme-rain" &&
                WaveTint.inverted(luma(s.mirrorTop), WaveTint.gapAt(s.dayBlend, PaperRenderer.WAVE_FOAM_LUMA_GAP, PaperRenderer.WAVE_FOAM_LUMA_GAP_NIGHT))
        }
        println("WAVE inverted papers in %d of %d situations, themes %s, palest surface %.2f at %s | of those, %d occur with the sky dimmed by a storm"
            .format(count, all.size, themes, palest, palestAt?.label, dimmedInversions))
        assertTrue(
            "every inverted situation must be an undimmed one: a storm darkens the surface, so if " +
                "one appears in a dimmed column the arithmetic has moved and the photographed case " +
                "is no longer the worst one. Found $dimmedInversions",
            dimmedInversions == 0,
        )
        assertTrue("the inverted case must still occur, or this test is measuring nothing", count > 0)
        assertTrue(
            "the inverted case is a property of ice-coloured water, not of the rule: if it spreads " +
                "to another theme the wave has to be looked at there too. Found on $themes",
            themes == sortedSetOf("tundra"),
        )
    }

    /**
     * **What the shipped shape really stands over, recorded because it is larger than the
     * derivation above assumed.**
     *
     * The gates come from a floor measured with the phase-2 wave on Beach, where a wave covers
     * 0.15 of the band. WA3 "Tubo" is taller, and two of the three themes that draw a lake draw a
     * much shallower one -- so on those the wave stands over more than half the water and the
     * gradient under it varies by more than the gate it is asked to beat. This test does not fix
     * that; it pins the numbers so the situation cannot quietly get worse, and `BACKLOG_v4_28.md`
     * item 83 carries the decision, which needs photographs rather than arithmetic.
     *
     * Only three built-in themes show a lake by default, which is also why the sweep is 2 592
     * situations and not more: 3 themes x 288 five-minute steps x 3 weathers.
     */
    @Test
    fun `the shipped shape stands over more of the band than the derivation assumed`() {
        val lakeThemes = ThemeCatalog.ALL.map { it.id to defaultCustomizationFor(it.id) }
            .filter { it.second.lake.visible }
        assertTrue("the sweep must cover exactly the themes that draw a lake", lakeThemes.size == 3)
        assertTrue("and that is what makes the sweep 2 592 situations", all.size == lakeThemes.size * 24 * 12 * 3)
        var worstFloor = 0f
        var worstTheme = ""
        for ((id, c) in lakeThemes) {
            val band = REFERENCE_SCREEN_HEIGHT * 0.16f * c.lake.height.coerceIn(0f, 1f)
            val fraction = (shippedWaveHeightPx / band).coerceAtMost(1f)
            val span = all.filter { it.theme == id }.maxOf { kotlin.math.abs(luma(it.mirrorTop) - luma(it.lake)) }
            val localFloor = span * fraction
            println("WAVE SHAPE %-8s lake height %.2f -> band %.1f px, wave %.1f px = %.3f of it; worst gradient %.2f luma, local floor %.2f"
                .format(id, c.lake.height, band, shippedWaveHeightPx, fraction, span, localFloor))
            if (localFloor > worstFloor) { worstFloor = localFloor; worstTheme = id }
        }
        println("WAVE SHAPE worst local floor %.2f on %s, against the shipped body gate %.2f"
            .format(worstFloor, worstTheme, PaperRenderer.WAVE_BODY_LUMA_GAP))
        assertTrue(
            "v4.28 measured the worst local floor at 33.40 on spring. If this has grown, the wave " +
                "is standing over even more of the water than item 83 recorded and the item needs " +
                "re-reading, not a new tolerance here. Got $worstFloor on $worstTheme",
            worstFloor <= 33.5f,
        )
        assertTrue(
            "this test exists because the local floor exceeds the day gate; if it stops doing so, " +
                "item 83 is closed and this test should say so instead",
            worstFloor > PaperRenderer.WAVE_BODY_LUMA_GAP,
        )
    }

    private companion object {
        /** The reference viewport the scene's own pixel figures are quoted at. */
        const val REFERENCE_SCREEN_HEIGHT = 2400f

        /** The largest a wave slot is scaled by its lane in `gatherWaves`: 0.85 + 0.3. */
        const val WAVE_MAX_LANE_SCALE = 1.15f

        fun blendARGB(from: Int, to: Int, ratio: Float): Int {
            val r = ratio.coerceIn(0f, 1f)
            val inverse = 1f - r
            val a = (((from ushr 24) and 0xFF) * inverse + ((to ushr 24) and 0xFF) * r).toInt()
            val rr = (((from shr 16) and 0xFF) * inverse + ((to shr 16) and 0xFF) * r).toInt()
            val g = (((from shr 8) and 0xFF) * inverse + ((to shr 8) and 0xFF) * r).toInt()
            val b = ((from and 0xFF) * inverse + (to and 0xFF) * r).toInt()
            return (a shl 24) or (rr shl 16) or (g shl 8) or b
        }

        fun luma(color: Int): Float {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            return (r * 299 + g * 587 + b * 114) / 1000f
        }
    }
}
