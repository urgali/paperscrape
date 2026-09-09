package com.paperscrape.livewallpaper.engine

import java.io.File
import javax.imageio.ImageIO
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **What is drawn on the water has to be visible on the water.** v4.26 makes the surface a mirror
 * of the sky, which is the release that most needs this said out loud: the closer a theme's sky
 * and lake colours are, the more the water agrees with everything above it.
 *
 * Two things were failing and both are closed here **by derivation rather than by choice**, which
 * is the whole point of the file — a colour picked by eye is a colour nobody can check.
 *
 * ### 1. The dolphin disappeared
 *
 * v4.25's animal carried `#4A6A84` on a `#15495C` night sea. Measured over **every** surface the
 * eleven dolphin-drawing themes can paint -- the whole day/night sweep rather than its two ends,
 * both twilight branches, clear and storm, and both edges of the mirror -- its worst case was a
 * **CIELab dE of 1.53**. That is below the ~2.3 that is a just-noticeable difference for a large
 * flat field, and this animal is 40 px of moving sprite over textured water, so in practice it was
 * not there at all. The maintainer reported it as gone at night and in the storm, and the
 * measurement agrees.
 *
 * The gate is derived the way the v4.22 method requires: **between the floor the failing element
 * produces and the signal a working one produces.** The floor is the invisible back, 1.53. The
 * signal is the animal's *own belly*, which reads on the same water, in the same lanes, at the same
 * size, and measures 18.78 -- so the belly is deliberately left alone, because moving it would move
 * the gate with it. The gate is the midpoint, **10.16**.
 *
 * Three of the four papers moved to clear it: the back to `#BAA8AE` (17.97), its under-paper to
 * `#917F84` (12.68), and the belly's under-paper to `#E6CED7` (24.72), which at `#B4C9D6` was the
 * one paper still under the gate at 9.11. The animal is lighter, which is what was asked for, and
 * it is lighter by the amount the derivation asks for rather than by an amount that looked right.
 *
 * ### 2. The waterline could vanish into the sky
 *
 * A mirror reflects the sky, so a theme whose sky and lake agree loses the edge between them. The
 * band's top edge is flat by construction — the mountains anchor to its nominal top Y and a wavy
 * edge opens a sliver of bare sky, which is the argument in `drawLakeBand` — so the edge is struck
 * rather than bent. This pins the gap it is struck to, and pins the worst theme it exists for.
 *
 * ### Why CIELab and not the contrast ratio
 *
 * Measured as WCAG luminance contrast, the sailboat's hull scores **1.009** against one of these
 * waters and it is plainly visible: it is orange on blue, and a luminance ratio cannot see hue at
 * all. The dolphin's problem is precisely that it shares the water's hue *and* its lightness, so
 * the metric has to be able to tell those apart. dE76 is the cheapest one that can.
 */
class LakeContrastTest {

    // ------------------------------------------------------------------ what the scene paints

    /**
     * Every water colour the scene can actually paint, for the themes given.
     *
     * Reconstructed from what `drawSky` and `drawLake` do rather than from the theme table: the
     * surface under a sprite is the *mirror*, which is the lake colour carried
     * [PaperRenderer.LAKE_MIRROR_SKY_SHARE] toward the weathered sky at the horizon, and the near
     * edge is the lake colour itself. The day/night blend is swept rather than sampled at its ends,
     * because the twilight bottom colour is a third colour that neither end contains.
     */
    private fun waters(themeIds: List<String>): List<Water> {
        val out = mutableListOf<Water>()
        for (id in themeIds) {
            val c = defaultCustomizationFor(id)
            for (step in 0..20) {
                val blend = step / 20f
                for (progress in listOf(0.25f, 0.75f)) {
                    val skyBottom = skyHorizonColour(c, blend, progress)
                    val skyTop = blendARGB(c.sky.colorNightHigh, c.sky.colorDayHigh, blend)
                    val lake = blendARGB(c.lake.colorNight, c.lake.colorDay, blend)
                    // Where this theme's own shore sits, which is what `updateLakeBandY` computes
                    // and what decides which part of the sky gradient the waterline borders.
                    val shore = SceneSpace.GROUND_SOLID_TOP_Y_FRACTION - 0.16f * c.lake.height.coerceIn(0f, 1f)
                    for (storm in listOf(0f, 1f)) {
                        val horizon = StormAtmosphere.dimSky(skyBottom, storm)
                        val skyAtShore = blendARGB(StormAtmosphere.dimSky(skyTop, storm), horizon, shore)
                        val label = "$id blend=%.2f progress=%.2f %s".format(blend, progress, if (storm > 0f) "storm" else "clear")
                        out += Water(
                            label, skyAtShore,
                            blendARGB(lake, horizon, PaperRenderer.LAKE_MIRROR_SKY_SHARE), lake,
                        )
                    }
                }
            }
        }
        return out
    }

    /**
     * [sky] is the sky **immediately above this theme's own shore**, not the horizon colour the
     * water reflects. The two are far apart — 25 units of luma on Tundra — and judging the edge
     * against the wrong end of the sky's gradient produced a line that sat *between* the sky and
     * the water instead of clear of both.
     */
    private data class Water(val label: String, val sky: Int, val mirror: Int, val near: Int)

    /** `drawSky`'s bottom colour, which is what the water reflects and what the edge borders. */
    private fun skyHorizonColour(c: SceneCustomization, blend: Float, progress: Float): Int {
        val twilightWeight = (1f - kotlin.math.abs(blend * 2f - 1f)).coerceIn(0f, 1f)
        val twilight = if (progress < 0.5f) c.sky.colorSunriseLow else c.sky.colorSunsetLow
        val nightToTwilight = blendARGB(c.sky.colorNightLow, twilight, blend)
        return blendARGB(nightToTwilight, c.sky.colorDayLow, (blend - twilightWeight * 0.3f).coerceIn(0f, 1f))
    }

    /**
     * The themes a dolphin can be drawn on.
     *
     * Tundra is excluded and the exclusion is *checked*, not asserted in prose: it is the one theme
     * that switches the animals off, because a pod of dolphins at the edge of the ice is the
     * incoherence `BuiltInThemeCoherenceTest` was written for. If that default ever changes, this
     * list changes with it and the derivation has to be redone — which is what the assertion below
     * makes happen instead of leaving a stale exclusion behind.
     */
    private fun dolphinThemes(): List<String> {
        val all = ThemeCatalog.ALL.map { it.id }
        val off = all.filterNot { defaultCustomizationFor(it).lake.dolphinsVisible }
        assertEquals(
            "the set of themes that hide dolphins has changed, so the dolphin's colour has to be " +
                "re-derived over the new set rather than kept",
            listOf("tundra"),
            off,
        )
        return all - off.toSet()
    }

    // ------------------------------------------------------------------ the assertions

    /**
     * **Every paper of the animal clears the gate, and the gate is between the floor and the signal.**
     *
     * The tones are read off the shipped PNG rather than restated here, so the assertion is about
     * the artwork and not about a constant somebody retyped.
     */
    @Test
    fun `every paper of the dolphin stands off every water it can be drawn on`() {
        val water = waters(dolphinThemes())
        fun worstAgainstWater(colour: Int): Pair<Float, Water> {
            val w = water.minByOrNull { minOf(dE(colour, it.mirror), dE(colour, it.near)) }!!
            return minOf(dE(colour, w.mirror), dE(colour, w.near)) to w
        }

        // The signal arm: the animal's own belly, the paper that already read on this water.
        val signal = worstAgainstWater(lightestOpaqueColour("dolphin_body")).first
        val gate = (FAILING_BACK_WORST_DE + signal) / 2f
        assertTrue(
            "the belly is the signal arm and has to stay clearly above the gate it helps define, " +
                "or the gate is measuring nothing: %.2f against %.2f".format(signal, gate),
            signal > gate * 1.5f,
        )

        // **Every paper, not just the mass.** A "paper" is a flat tone covering at least
        // [PAPER_MINIMUM_SHARE] of the animal's opaque pixels; below that a tone is an antialiased
        // edge between two papers, and holding an edge to a visibility gate would be gating the
        // rasteriser rather than the artwork.
        for ((colour, share) in papers("dolphin_body")) {
            val (worstDe, where) = worstAgainstWater(colour)
            assertTrue(
                "#%06X is %.0f%% of the dolphin and measures dE %.2f from the water at its worst "
                    .format(colour and 0xFFFFFF, share * 100, worstDe) +
                    "(%s), against a gate of %.2f derived between the %.2f the invisible v4.25 back "
                        .format(where.label, gate, FAILING_BACK_WORST_DE) +
                    "produced and the %.2f the belly produces. Change the tone in the generator and "
                        .format(signal) +
                    "re-run the promotion; do not lower this",
                worstDe >= gate,
            )
        }
    }

    /** What the v4.25 artwork measured, kept as the floor arm of the derivation above. */
    private val FAILING_BACK_WORST_DE = 1.53f

    /**
     * **The struck edge reaches the gap it is struck to, in every theme.**
     *
     * This is `drawWaterline`'s own arithmetic, run over every water the twelve themes paint. It
     * also records the case the edge exists for, so that the worst theme is a fact in the suite
     * rather than a sentence in a report.
     */
    @Test
    fun `the waterline separates the sky from the water in every theme`() {
        val water = waters(ThemeCatalog.ALL.map { it.id })
        for (w in water) {
            val edge = waterlineColour(w.sky, w.mirror)
            val gap = kotlin.math.abs(luma(edge) - luma(w.sky))
            assertTrue(
                "the waterline is only %.1f of luma clear of the sky at %s, against the %.1f it is "
                    .format(gap, w.label, PaperRenderer.WATERLINE_MIN_LUMA_GAP) + "struck to",
                // One 8-bit step per channel is lost to truncation in the blend, which is worth
                // up to about one unit of luma. The tolerance is that, and nothing else.
                gap >= PaperRenderer.WATERLINE_MIN_LUMA_GAP - 1.1f,
            )
        }
        val worst = water.minByOrNull { dE(it.sky, it.mirror) }!!
        assertTrue(
            "the worst unaided separation between the sky above the shore and the water below it " +
                "is now %.2f at %s; it was 2.16 in Tundra near midday, where the two are 0.1 of "
                    .format(dE(worst.sky, worst.mirror), worst.label) +
                "luma apart and the shore does not exist without the struck line. If this has moved " +
                "a long way then WATERLINE_MIN_LUMA_GAP -- the median separation over the same set " +
                "-- has to be re-derived rather than kept",
            dE(worst.sky, worst.mirror) < 4f,
        )
    }

    /**
     * The arithmetic above is a restatement, so this is what ties it to the renderer.
     *
     * Without it the waterline test would keep passing over a `drawWaterline` that had been
     * deleted, or that had stopped being called: it would be asserting about its own copy. Read
     * back out of the source, the way `OneOccupantRuleTest` reads the window-occupant divisor.
     */
    @Test
    fun `the renderer actually strikes the waterline it is measured on`() {
        val source = File(repoRoot(), "app/src/main/kotlin/com/paperscrape/livewallpaper/engine/PaperRenderer.kt").readText()
        assertTrue("drawWaterline is gone from the renderer", source.contains("private fun drawWaterline("))
        assertTrue("drawLake no longer strikes the waterline", source.contains("drawWaterline(canvas, top)"))
        assertTrue(
            "drawWaterline no longer derives its colour from WATERLINE_MIN_LUMA_GAP, so the gap " +
                "this test measures is not the gap the renderer draws",
            source.substringAfter("private fun drawWaterline(").substringBefore("\n    }")
                .contains("WATERLINE_MIN_LUMA_GAP"),
        )
        assertTrue(
            "the water is no longer a mirror of the sky, so every number in this file was derived " +
                "against a surface the renderer has stopped painting",
            source.contains("skyHorizonColorNow, LAKE_MIRROR_SKY_SHARE"),
        )
    }

    /** `PaperRenderer.drawWaterline`, restated: the narrow property that has to hold. */
    private fun waterlineColour(sky: Int, surface: Int): Int {
        val skyLuma = luma(sky)
        val surfaceLuma = luma(surface)
        val towardWhite = surfaceLuma >= skyLuma
        val target = if (towardWhite) skyLuma + PaperRenderer.WATERLINE_MIN_LUMA_GAP
        else skyLuma - PaperRenderer.WATERLINE_MIN_LUMA_GAP
        val anchorLuma = if (towardWhite) 255f else 0f
        val t = ((target - surfaceLuma) / (anchorLuma - surfaceLuma)).coerceIn(0f, 1f)
        return blendARGB(surface, if (towardWhite) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), t)
    }

    // ------------------------------------------------------------------ colour arithmetic

    /**
     * `ColorUtils.blendARGB`, written out.
     *
     * Not called: it reaches `android.graphics.Color`, which on the unit-test classpath is the
     * mockable android.jar and throws "not mocked" (`CLAUDE.md` §7). Restating a library function's
     * arithmetic is not the duplication that rule warns about — what must not be restated is this
     * project's own decisions, and those are all read back out of `PaperRenderer` above.
     */
    private fun blendARGB(from: Int, to: Int, ratio: Float): Int {
        val r = ratio.coerceIn(0f, 1f)
        val inverse = 1f - r
        val a = (((from ushr 24) and 0xFF) * inverse + ((to ushr 24) and 0xFF) * r).toInt()
        val rr = (((from shr 16) and 0xFF) * inverse + ((to shr 16) and 0xFF) * r).toInt()
        val g = (((from shr 8) and 0xFF) * inverse + ((to shr 8) and 0xFF) * r).toInt()
        val b = ((from and 0xFF) * inverse + (to and 0xFF) * r).toInt()
        return (a shl 24) or (rr shl 16) or (g shl 8) or b
    }

    /** Rec. 601 luma, the weighting `StormAtmosphere.dim` and `drawWaterline` use. */
    private fun luma(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000f
    }

    private fun lab(color: Int): Triple<Float, Float, Float> {
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

    /** CIE76 colour difference. Cheap, and it can see hue, which is the whole requirement. */
    private fun dE(a: Int, b: Int): Float {
        val (l1, a1, b1) = lab(a)
        val (l2, a2, b2) = lab(b)
        return kotlin.math.sqrt((l1 - l2) * (l1 - l2) + (a1 - a2) * (a1 - a2) + (b1 - b2) * (b1 - b2))
    }

    // ------------------------------------------------------------------ reading the artwork

    private fun opaquePixels(name: String): List<Int> {
        val image = ImageIO.read(File(drawableDir(), "$name.png"))
        val out = ArrayList<Int>(image.width * image.height)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                if ((argb ushr 24) and 0xFF == 0xFF) out += argb or (0xFF shl 24)
            }
        }
        assertTrue("$name has no opaque pixels", out.isNotEmpty())
        return out
    }

    /** The largest mass of one colour: the animal's back, which is what has to stand off. */
    private fun dominantOpaqueColour(name: String): Int =
        opaquePixels(name).groupingBy { it }.eachCount().maxByOrNull { it.value }!!.key

    /** The pale paper on the same animal: the belly, which is the signal arm of the derivation. */
    private fun lightestOpaqueColour(name: String): Int =
        papers(name).maxByOrNull { luma(it.first) }!!.first

    /** Every flat tone covering at least [PAPER_MINIMUM_SHARE] of the sprite, with its share. */
    private fun papers(name: String): List<Pair<Int, Float>> {
        val pixels = opaquePixels(name)
        val found = pixels.groupingBy { it }.eachCount()
            .filterValues { it >= pixels.size * PAPER_MINIMUM_SHARE }
            .map { it.key to it.value.toFloat() / pixels.size }
        assertTrue("$name resolves to ${found.size} papers, which is not a paper-cut sprite", found.size >= 3)
        return found
    }

    private val PAPER_MINIMUM_SHARE = 0.05f

    private fun drawableDir(): File = File(repoRoot(), "app/src/main/res/drawable-nodpi")

    private fun repoRoot(): File {
        var dir = File(".").absoluteFile
        while (dir.parentFile != null) {
            if (File(dir, "app/src/main/res/drawable-nodpi").isDirectory) return dir
            dir = dir.parentFile
        }
        error("repository root not found from ${File(".").absolutePath}")
    }
}
