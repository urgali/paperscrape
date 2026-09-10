package com.paperscrape.livewallpaper.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **Rain has to be visible against the sky it falls through.**
 *
 * The maintainer reported the drops as invisible until they reached the hills. The mechanism is the
 * one v4.26 closed for the shoreline, in a second place: **a fixed colour on a background that
 * moves.** A drop's colour is the theme's own rain pair blended by the day phase and nothing else;
 * the sky it crosses changes with the hour, with the twilight branch and with the weather.
 *
 * ### What was measured
 *
 * The twelve built-in themes, the clock swept in **five-minute steps** through the real
 * [SunPositionCalculator] rather than sampled at its ends, clear / live rain / thunderstorm, and
 * thirteen heights down the stretch of screen a drop crosses with sky behind it — the cloud band's
 * own middle, where a drop is born, to the top of the hills. **10 368 situations**, all on the host:
 * this is arithmetic over the themes' own numbers and needs no device.
 *
 * **Eleven of the twelve themes have an hour at which the rain and the sky are the same
 * brightness.** The floor is [FAILING_WORST_GAP], against a median of [MEDIAN_SKY_GAP]. Halloween
 * is the exception and it is not a virtue: its sky is near-black over a hard orange, so a mid-blue
 * rain cannot collide with it.
 *
 * ### Why luma, when the dolphin's gate is CIELab dE
 *
 * `LakeContrastTest` measures a 40 px animal, where sharing a lightness but not a hue still reads.
 * A raindrop is a **1.19 px** stroke at 720x1440. Chromatic acuity collapses at that width, so the
 * metric has to be the one the eye still has there. At the worst case measured here the drop is
 * CIELab dE 20.95 from the sky and 0.00 of luma from it — dE says "clearly different", the phone
 * says "not there", and the phone is right. This is the same choice, and the same Rec. 601
 * weighting, as `PaperRenderer.WATERLINE_MIN_LUMA_GAP`, the project's other struck hairline.
 *
 * ### Where the gate comes from
 *
 * The v4.22 rule, with both arms measured on this sweep: the floor is the failing case, and the
 * signal is the same drop, at the same width and the same alpha, over the background the
 * maintainer reports it becoming visible against — the hills. The gate is the midpoint, and it is
 * [PaperRenderer.PRECIPITATION_MIN_LUMA_GAP].
 *
 * ### Snow
 *
 * Measured over the same sweep and **left alone, with the number**: the worst snow-against-sky
 * separation is [SNOW_WORST_GAP], comfortably above the gate, so the correction resolves to zero
 * for every snow situation that exists. Snow against the *cloud* it is born in is a different
 * measurement with a different answer, and it is recorded in `BACKLOG_v4_27.md` rather than fixed
 * here: a white flake leaving a white cloud is what the fade-in exists for.
 */
class PrecipitationContrastTest {

    // ------------------------------------------------------------------ the measured facts

    /** The worst rain-against-sky separation the twelve themes produce unaided. */
    private val FAILING_WORST_GAP = 0.00f

    /** The median of the same measurement, kept because the gate is *not* derived from it. */
    private val MEDIAN_SKY_GAP = 28.62f

    /** The signal arm: the median separation the same drop makes against the hills. */
    private val SIGNAL_HILL_GAP = 26.94f

    /** Snow's worst against the sky, which is why snow is not corrected. */
    private val SNOW_WORST_GAP = 19.13f

    /** The largest CIELab distance the correction is measured to carry the theme's own rain. */
    private val WORST_COLOUR_SHIFT = 16.19f

    // ------------------------------------------------------------------ what the scene paints

    /** One (theme, clock hour, weather) situation, with everything a drop meets in it. */
    private class Situation(
        val theme: String,
        val hour: Float,
        val weather: String,
        val blend: Float,
        val skyTop: Int,
        val skyBottom: Int,
        val cloud: Int,
        val hill: Int,
    ) {
        // Truncated, not rounded: `%.0f` on 6.583 prints "07", which would name the wrong hour in
        // every message this class produces and in the golden that is pointed at one of them.
        val label: String
            get() = "%s %02d:%02d %s".format(theme, hour.toInt(), ((hour - hour.toInt()) * 60f).toInt(), weather)

        /** `PaperRenderer.skyAbove`, restated: the gradient runs top to bottom of the *screen*. */
        fun skyAt(yFraction: Float): Int = blendARGB(skyTop, skyBottom, yFraction.coerceIn(0f, 1f))
    }

    /**
     * The stretch a drop crosses with sky behind it: the cloud band's middle — `drawPrecipitation`'s
     * own `fallStartY` — down to the top of the hills, at the default cloud height.
     */
    private val fallTopFraction = CloudBand.precipitationOriginY(1000, SceneCustomization.DEFAULT.sky.sunCloudHeight) / 1000f
    private val horizonFraction = SceneSpace.HILL_LAYER_TOP_FRACTION
    private val heights = (0..12).map { fallTopFraction + (horizonFraction - fallTopFraction) * it / 12f }

    /**
     * Every situation the scene can paint, for the themes given.
     *
     * The clock is swept rather than sampled: the twilight bottom colour is a third colour that
     * neither midnight nor midday contains, and the collisions this file exists for are inside it.
     */
    private fun situations(): List<Situation> {
        val out = mutableListOf<Situation>()
        val weathers = listOf(
            0f to "clear",
            StormAtmosphere.strength(PrecipitationType.RAIN, 0.6f, false, 0.9f) to "live-rain",
            StormAtmosphere.strength(PrecipitationType.RAIN, 0.6f, true, 1f) to "thunderstorm",
        )
        for (theme in ThemeCatalog.ALL) {
            val c = defaultCustomizationFor(theme.id)
            for (step in 0 until 24 * 12) {
                val hour = step / 12f
                val phase = SunPositionCalculator.compute(hour24 = hour)
                val blend = phase.dayBlend.coerceIn(0f, 1f)
                val cloudRaw = blendARGB(c.clouds.colorNight, c.clouds.colorDay, blend)
                for ((storm, name) in weathers) {
                    val (top, bottom) = skyGradient(c, phase, storm)
                    out += Situation(
                        theme.id, hour, name, blend, top, bottom,
                        StormAtmosphere.dimCloud(cloudRaw, storm),
                        blendARGB(c.hillsColorNight, c.hillsColorDay, blend),
                    )
                }
            }
        }
        return out
    }

    /**
     * `drawSky`'s two ends, including the horror branch.
     *
     * The horror sky returns before the weather is applied, so it is never dimmed — restating that
     * here rather than "simplifying" it is what keeps this measurement about the sky that is drawn.
     */
    private fun skyGradient(c: SceneCustomization, phase: SunPositionCalculator.DayPhase, storm: Float): Pair<Int, Int> {
        val blend = phase.dayBlend.coerceIn(0f, 1f)
        if (c.horrorSkyEnabled) {
            return blendARGB(HORROR_TOP_NIGHT, HORROR_TOP_DAY, blend) to
                blendARGB(HORROR_LOW_NIGHT, HORROR_LOW_DAY, blend)
        }
        val twilightWeight = (1f - kotlin.math.abs(blend * 2f - 1f)).coerceIn(0f, 1f)
        val twilight = if (phase.progress < 0.5f) c.sky.colorSunriseLow else c.sky.colorSunsetLow
        val nightToTwilight = blendARGB(c.sky.colorNightLow, twilight, blend)
        val bottom = blendARGB(nightToTwilight, c.sky.colorDayLow, (blend - twilightWeight * 0.3f).coerceIn(0f, 1f))
        val top = blendARGB(c.sky.colorNightHigh, c.sky.colorDayHigh, blend)
        return StormAtmosphere.dimSky(top, storm) to StormAtmosphere.dimSky(bottom, storm)
    }

    /**
     * **Every theme shares one rain pair and one snow pair, and this is why that may be assumed.**
     *
     * [rainAt] and [snowAt] read `SceneCustomization.DEFAULT`, not the theme's own configuration,
     * because no built-in theme overrides either pair — the twelve differ in their skies and not in
     * their weather. If one ever did, every number in this file would be about a colour that theme
     * does not use, silently, so the assumption is checked rather than commented.
     */
    @Test
    fun `no theme overrides the precipitation colours the measurement assumes`() {
        val d = SceneCustomization.DEFAULT.precipitation
        for (theme in ThemeCatalog.ALL) {
            val p = defaultCustomizationFor(theme.id).precipitation
            assertEquals("${theme.id} overrides the day rain colour", d.rainColorDay, p.rainColorDay)
            assertEquals("${theme.id} overrides the night rain colour", d.rainColorNight, p.rainColorNight)
            assertEquals("${theme.id} overrides the day snow colour", d.snowColorDay, p.snowColorDay)
            assertEquals("${theme.id} overrides the night snow colour", d.snowColorNight, p.snowColorNight)
        }
    }

    private fun rainAt(blend: Float): Int {
        val p = SceneCustomization.DEFAULT.precipitation
        return blendARGB(p.rainColorNight, p.rainColorDay, blend)
    }

    private fun snowAt(blend: Float): Int {
        val p = SceneCustomization.DEFAULT.precipitation
        return blendARGB(p.snowColorNight, p.snowColorDay, blend)
    }

    /**
     * The separation the eye is given: the stroke is composited at [alpha], and Rec. 601 luma is
     * linear in the channels, so the seen difference is the colour difference scaled by the alpha.
     */
    private fun seenGap(drop: Int, background: Int, alpha: Int): Float =
        kotlin.math.abs(luma(drop) - luma(background)) * (alpha / 255f)

    // ------------------------------------------------------------------ the assertions

    /**
     * **The floor and the signal are both facts of this tree, not sentences in a report.**
     *
     * If either moves — a theme's sky is retuned, the rain's default pair is changed, the fall
     * stretch moves — the gate stops being the midpoint between them and has to be re-derived
     * rather than kept.
     */
    @Test
    fun `the gate sits between the sky the rain vanishes into and the hills it reads on`() {
        val sits = situations()
        val worstPerSituation = sits.map { s ->
            val drop = rainAt(s.blend)
            heights.minOf { seenGap(drop, s.skyAt(it), PaperRenderer.RAIN_ALPHA) }
        }.sorted()
        val hillGaps = sits.map { seenGap(rainAt(it.blend), it.hill, PaperRenderer.RAIN_ALPHA) }.sorted()

        val floor = worstPerSituation.first()
        val median = worstPerSituation[worstPerSituation.size / 2]
        val signal = hillGaps[hillGaps.size / 2]

        assertEquals(
            "the worst rain-against-sky separation has moved; the gate is derived from it and has " +
                "to be re-derived rather than kept",
            FAILING_WORST_GAP, floor, 0.05f,
        )
        assertEquals(
            "the median rain-against-hill separation is the signal arm of the gate and has moved",
            SIGNAL_HILL_GAP, signal, 0.5f,
        )
        assertEquals("the median rain-against-sky separation has moved", MEDIAN_SKY_GAP, median, 0.5f)
        assertEquals(
            "PRECIPITATION_MIN_LUMA_GAP is the midpoint between the floor and the signal and no " +
                "longer is: floor %.2f, signal %.2f".format(floor, signal),
            (floor + signal) / 2f, PaperRenderer.PRECIPITATION_MIN_LUMA_GAP, 0.3f,
        )
        assertTrue(
            "the signal arm has to stay clearly above the gate it helps define, or the gate is " +
                "measuring nothing: %.2f against %.2f".format(signal, PaperRenderer.PRECIPITATION_MIN_LUMA_GAP),
            signal > PaperRenderer.PRECIPITATION_MIN_LUMA_GAP * 1.5f,
        )
    }

    /**
     * **The case that was reported, kept as a number.**
     *
     * Autumn at 08:00 with the cloud cover at 90 % and rain falling. It measures **5.97** when the
     * cover and the rain come from Live Weather — which also weathers the sky through
     * `StormAtmosphere.dimSky`, and is what brings the two together — and 19.38 when the rain is
     * the theme's own manual setting, where the sky is not weathered at all. Both are recorded
     * because the difference is the whole reason the sweep covers all three weathers rather than
     * the reported one: the same theme at the same hour fails or does not fail depending on which
     * path put the rain there.
     */
    @Test
    fun `the reported case measures what it was reported to be, and is corrected`() {
        val sits = situations()
        val need = PaperRenderer.PRECIPITATION_MIN_LUMA_GAP * 255f / PaperRenderer.RAIN_ALPHA
        fun worstAt(weather: String): Pair<Float, Float> {
            val s = sits.single { it.theme == "autumn" && kotlin.math.abs(it.hour - 8f) < 0.01f && it.weather == weather }
            val drop = rainAt(s.blend)
            val before = heights.minOf { seenGap(drop, s.skyAt(it), PaperRenderer.RAIN_ALPHA) }
            val after = heights.minOf { seenGap(drawnFor(drop, s, need), s.skyAt(it), PaperRenderer.RAIN_ALPHA) }
            return before to after
        }
        val (liveBefore, liveAfter) = worstAt("live-rain")
        val (manualBefore, _) = worstAt("clear")
        assertEquals("the reported case has moved: Autumn 08:00 under live rain", 5.97f, liveBefore, 0.2f)
        assertEquals(
            "Autumn 08:00 with the rain set by hand no longer measures what it did; the pair of " +
                "numbers is the evidence that the weather path is what brings the two together",
            19.38f, manualBefore, 0.2f,
        )
        assertTrue(
            "the reported case is no longer corrected: %.2f".format(liveAfter),
            liveAfter >= PaperRenderer.PRECIPITATION_MIN_LUMA_GAP - 1.1f,
        )
    }

    /**
     * **Eleven of the twelve themes lose the rain entirely, and the twelfth is not virtuous.**
     *
     * Recorded as an assertion so the case the correction exists for stays a fact in the suite.
     */
    @Test
    fun `every theme but the horror sky has an hour at which the rain is the sky's own brightness`() {
        val sits = situations()
        val collapsing = ThemeCatalog.ALL.map { it.id }.filter { id ->
            sits.filter { it.theme == id }.minOf { s ->
                val drop = rainAt(s.blend)
                heights.minOf { seenGap(drop, s.skyAt(it), PaperRenderer.RAIN_ALPHA) }
            } < 1f
        }
        assertEquals(
            "the set of themes whose rain reaches the sky's own brightness has changed, so the " +
                "measurement behind PRECIPITATION_MIN_LUMA_GAP has to be redone",
            ThemeCatalog.ALL.map { it.id } - setOf("halloween"),
            collapsing,
        )
    }

    /**
     * **The correction reaches the gap everywhere, and stops there.**
     *
     * This is `PaperRenderer.standOffFromSky`'s own arithmetic run over every situation and every
     * height. It also pins what the correction *costs*: how much of the sweep it touches at all,
     * and how far it is allowed to move the colour the user picked.
     */
    @Test
    fun `the corrected rain clears the gap on every sky, and the theme's colour survives it`() {
        val sits = situations()
        val gap = PaperRenderer.PRECIPITATION_MIN_LUMA_GAP
        val needed = gap * 255f / PaperRenderer.RAIN_ALPHA
        var touched = 0
        var worstAfter = Float.MAX_VALUE
        var worstShift = 0f
        for (s in sits) {
            val drop = rainAt(s.blend)
            val corrected = drawnFor(drop, s, needed)
            var any = false
            for (h in heights) {
                val sky = s.skyAt(h)
                if (corrected != drop) any = true
                val after = seenGap(corrected, sky, PaperRenderer.RAIN_ALPHA)
                worstAfter = minOf(worstAfter, after)
                worstShift = maxOf(worstShift, dE(corrected, drop))
                assertTrue(
                    "the corrected rain is only %.2f of luma clear of the sky at %s, height %.2f, " .format(after, s.label, h) +
                        "against the %.2f it is derived to".format(gap),
                    // One 8-bit step per channel is lost to truncation in the blend, worth up to
                    // about one unit of luma. The tolerance is that, and nothing else.
                    after >= gap - 1.1f,
                )
            }
            if (any) touched++
        }
        assertTrue(
            "the correction now touches %d of %d situations; it was about a quarter when the gate "
                .format(touched, sits.size) +
                "was derived at 21.8%, and a correction that reaches most of the sweep is a " +
                "palette change rather than a rescue",
            touched < sits.size / 3,
        )
        // **What the correction is allowed to cost, pinned as a fact rather than described.**
        // The largest carry is New Year at 08:40, where `#7EB2DF` is drawn `#6189AB`: the same
        // blue, held down, because that theme's sky spans the drop's own brightness from the cloud
        // band to the hills and one colour has to clear all of it. If a future change makes the
        // correction louder than this, it is a change to how the rain looks and has to be judged
        // as one rather than absorbed.
        assertEquals(
            "the largest colour the correction carries has moved from the %.2f it was derived with"
                .format(WORST_COLOUR_SHIFT),
            WORST_COLOUR_SHIFT, worstShift, 0.5f,
        )
    }

    /**
     * **The one artefact the design has, bounded so it cannot grow.**
     *
     * The correction carries the colour toward white or black, and which of the two is priced by how
     * far each has to go: the cheaper wins. When the sky's own band crosses the drop's brightness,
     * the price crosses too and the choice flips, taking the drawn rain from a lighter blue to a
     * darker one in one step. It is not avoidable — the two branches are disjoint by construction,
     * which is the same argument that forbids a per-height correction, moved from space into time —
     * so what can be done is bound how often it happens.
     *
     * **Measured: twice a day per theme, both at the ends of the day, in clear weather** — Sunset
     * 06:09→06:15 and 19:39→19:45, and the same shape in every other theme. Twice is what a
     * monotone sky costs; a third flip would mean something non-monotone had entered the derivation
     * and the design would need re-examining rather than the number raising.
     */
    @Test
    fun `the drawn colour changes direction at most twice a day in any theme`() {
        val sits = situations()
        val need = PaperRenderer.PRECIPITATION_MIN_LUMA_GAP * 255f / PaperRenderer.RAIN_ALPHA
        for (theme in ThemeCatalog.ALL.map { it.id }) {
            for (weather in listOf("clear", "live-rain", "thunderstorm")) {
                val day = sits.filter { it.theme == theme && it.weather == weather }.sortedBy { it.hour }
                var flips = 0
                var previous: Float? = null
                for (s in day) {
                    val drawn = luma(drawnFor(rainAt(s.blend), s, need))
                    if (previous != null && kotlin.math.abs(drawn - previous) > 20f) flips++
                    previous = drawn
                }
                assertTrue(
                    "the rain's drawn colour jumps $flips times across a day on $theme under " +
                        "$weather; two is what a sky that brightens and darkens once costs, and " +
                        "more means the derivation has stopped being monotone in the hour",
                    flips <= 2,
                )
            }
        }
    }

    /**
     * **Snow is measured and left alone.**
     *
     * The maintainer asked for the same measurement for snow and for the number if it does not have
     * the problem. It does not: the correction resolves to zero everywhere.
     */
    @Test
    fun `snow never reaches the sky's brightness, so the correction never fires on it`() {
        val sits = situations()
        val needed = PaperRenderer.PRECIPITATION_MIN_LUMA_GAP * 255f / PaperRenderer.SNOW_ALPHA
        var worst = Float.MAX_VALUE
        for (s in sits) {
            val flake = snowAt(s.blend)
            val drawn = drawnFor(flake, s, needed)
            for (h in heights) {
                val sky = s.skyAt(h)
                worst = minOf(worst, seenGap(flake, sky, PaperRenderer.SNOW_ALPHA))
                assertEquals(
                    "snow was corrected at ${s.label}, height %.2f — the gate was derived on the ".format(h) +
                        "premise that it never has to be",
                    flake, drawn,
                )
            }
        }
        assertEquals(
            "snow's worst separation from the sky has moved; the decision not to correct it rests " +
                "on this number",
            SNOW_WORST_GAP, worst, 0.2f,
        )
    }

    /**
     * The arithmetic above is a restatement, so this is what ties it to the renderer.
     *
     * Without it every assertion in this file would keep passing over a `drawPrecipitation` that had
     * stopped deriving anything: it would be asserting about its own copy. Read back out of the
     * source, the way `LakeContrastTest` reads the waterline.
     */
    @Test
    fun `the renderer actually derives the colour it is measured on`() {
        val source = File(repoRoot(), "app/src/main/kotlin/com/paperscrape/livewallpaper/engine/PaperRenderer.kt").readText()
        assertTrue("standOffFromSky is gone from the renderer", source.contains("private fun standOffFromSky("))
        val body = source.substringAfter("private fun drawPrecipitation(").substringBefore("\n    }")
        assertTrue(
            "drawPrecipitation no longer calls standOffFromSky, so the drop's colour is a constant " +
                "again and every number in this file is about nothing",
            body.contains("standOffFromSky(themeColour, dropLuma, skyLumaLow, skyLumaHigh, neededColourGap)"),
        )
        assertTrue(
            "drawPrecipitation no longer divides the gap by the alpha it draws at, so the gap this " +
                "file measures on the composited stroke is not the gap the renderer reaches",
            body.contains("PRECIPITATION_MIN_LUMA_GAP * 255f / baseAlpha"),
        )
        assertTrue(
            "the correction is no longer derived against both ends of the fall, so it clears one " +
                "sky for a drop that crosses many",
            body.contains("skyLumaAtFallStart") && body.contains("skyLumaAtHorizon") &&
                body.contains("skyLumaLow") && body.contains("skyLumaHigh"),
        )
        assertTrue(
            "the sky's own colours are recorded before the horror branch returns, or a lake and " +
                "the rain under a horror sky are both derived against a colour never computed",
            source.substringAfter("if (sceneCustomization.horrorSkyEnabled) {")
                .substringBefore("\n            return").contains("skyTopColorNow = horrorTop"),
        )
    }

    /**
     * **The worst case, named, so a golden can be pointed at it.**
     *
     * v4.26 learned that a derivation with no frame behind it is a derivation nobody will see fail:
     * no committed golden portrayed the shoreline the struck waterline exists for. This names the
     * theme, the clock hour and the weather the rain correction exists for, and
     * `SceneGoldenTest.rainWorstSky` draws exactly it.
     */
    @Test
    fun `the worst rain sky is the one the golden portrays`() {
        val sits = situations()
        val worst = sits.minByOrNull { s ->
            val drop = rainAt(s.blend)
            heights.minOf { seenGap(drop, s.skyAt(it), PaperRenderer.RAIN_ALPHA) }
        }!!
        assertEquals("the worst theme has moved", "easter", worst.theme)
        assertTrue(
            "the worst hour has moved off the golden's own %.2f: it is now %.2f"
                .format(GOLDEN_WORST_HOUR, worst.hour),
            kotlin.math.abs(worst.hour - GOLDEN_WORST_HOUR) < 0.25f,
        )
    }

    /** The clock hour `SceneGoldenTest.rainWorstSky` is drawn at. */
    private val GOLDEN_WORST_HOUR = 6.583f

    // ------------------------------------------------------------------ colour arithmetic

    /**
     * `PaperRenderer.standOffFromSky`, restated.
     *
     * Not called: it is private to a class that needs a `Context`, and reaching it would mean
     * making the renderer testable, which is `ROADMAP.md` B5. What must not be restated is the
     * project's own decisions, and those are read back out of `PaperRenderer` above.
     */
    private fun standOffFromSky(
        base: Int,
        baseLuma: Float,
        skyLumaLow: Float,
        skyLumaHigh: Float,
        neededGap: Float,
    ): Int {
        if (baseLuma >= skyLumaHigh + neededGap || baseLuma <= skyLumaLow - neededGap) return base
        val whiteTarget = skyLumaHigh + neededGap
        val blackTarget = skyLumaLow - neededGap
        val whiteSpan = 255f - baseLuma
        val blackSpan = -baseLuma
        val tWhite = if (whiteSpan <= 0f) Float.MAX_VALUE else (whiteTarget - baseLuma) / whiteSpan
        val tBlack = if (blackSpan >= 0f) Float.MAX_VALUE else (blackTarget - baseLuma) / blackSpan
        val towardWhite = tWhite <= tBlack
        val t = (if (towardWhite) tWhite else tBlack).coerceIn(0f, 1f)
        if (t <= 0f) return base
        return blendARGB(base, if (towardWhite) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), t)
    }

    /** What the renderer draws for [s]: one colour for the whole fall. */
    private fun drawnFor(base: Int, s: Situation, neededGap: Float): Int {
        val a = luma(s.skyAt(fallTopFraction))
        val b = luma(s.skyAt(horizonFraction))
        return standOffFromSky(base, luma(base), minOf(a, b), maxOf(a, b), neededGap)
    }

    private fun repoRoot(): File {
        // `dir` is declared nullable and walked to null rather than tested through `parentFile`:
        // the older shape in this suite compiles with a "Java type mismatch: inferred type is
        // 'File?'" warning on every copy of it, because `parentFile` is a platform type.
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            if (File(dir, "app/src/main/res/drawable-nodpi").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found from ${File(".").absolutePath}")
    }

    private companion object {
        /** `PaperRenderer`'s horror sky, which is `private` to its companion. */
        const val HORROR_TOP_NIGHT = 0xFF07060A.toInt()
        const val HORROR_TOP_DAY = 0xFF1A1020.toInt()
        const val HORROR_LOW_NIGHT = 0xFFB03A06.toInt()
        const val HORROR_LOW_DAY = 0xFFF07A10.toInt()

        /** `ColorUtils.blendARGB`, written out: it reaches `android.graphics.Color`, which throws
         * "not mocked" on the unit-test classpath (`CLAUDE.md` §7). */
        fun blendARGB(from: Int, to: Int, ratio: Float): Int {
            val r = ratio.coerceIn(0f, 1f)
            val inverse = 1f - r
            val a = (((from ushr 24) and 0xFF) * inverse + ((to ushr 24) and 0xFF) * r).toInt()
            val rr = (((from shr 16) and 0xFF) * inverse + ((to shr 16) and 0xFF) * r).toInt()
            val g = (((from shr 8) and 0xFF) * inverse + ((to shr 8) and 0xFF) * r).toInt()
            val b = ((from and 0xFF) * inverse + (to and 0xFF) * r).toInt()
            return (a shl 24) or (rr shl 16) or (g shl 8) or b
        }

        /** Rec. 601 luma, the weighting `StormAtmosphere.dim` and `drawWaterline` use. */
        fun luma(color: Int): Float {
            val r = (color shr 16) and 0xFF
            val g = (color shr 8) and 0xFF
            val b = color and 0xFF
            return (r * 299 + g * 587 + b * 114) / 1000f
        }

        fun lab(color: Int): Triple<Float, Float, Float> {
            fun linear(v: Int): Float {
                val c = v / 255f
                return if (c <= 0.04045f) c / 12.92f else Math.pow(((c + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
            }
            val r = linear((color shr 16) and 0xFF)
            val g = linear((color shr 8) and 0xFF)
            val b = linear(color and 0xFF)
            val x = (0.4124f * r + 0.3576f * g + 0.1805f * b) / 0.95047f
            val y = 0.2126f * r + 0.7152f * g + 0.0722f * b
            val z = (0.0193f * r + 0.1192f * g + 0.9505f * b) / 1.08883f
            fun f(t: Float) = if (t > 0.008856f) Math.cbrt(t.toDouble()).toFloat() else 7.787f * t + 16f / 116f
            val fx = f(x)
            val fy = f(y)
            val fz = f(z)
            return Triple(116f * fy - 16f, 500f * (fx - fy), 200f * (fy - fz))
        }

        /** CIE76 colour difference, used here only to bound how far the theme's colour is moved. */
        fun dE(a: Int, b: Int): Float {
            val (l1, a1, b1) = lab(a)
            val (l2, a2, b2) = lab(b)
            return kotlin.math.sqrt((l1 - l2) * (l1 - l2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2))
        }
    }
}
