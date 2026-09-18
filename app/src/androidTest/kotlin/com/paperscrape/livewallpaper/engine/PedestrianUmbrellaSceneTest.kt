package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.max

/**
 * **One umbrella per walking figure -- every adult and every child -- counted on pixels.**
 *
 * ### What this file asserts, and what it used to
 *
 * It was written for v5.4's first pass, when a **share** of the adults carried, and all it could
 * ask of the pixels was *"is the added area over a floor"*: an area cannot say how many umbrellas
 * made it. The maintainer's rule needs a count, so this counts.
 *
 * v5.4E then made it *"one canopy per adult figure on screen and **none over a child**"*, because
 * the children had no carry pose to be drawn in. **v5.4H drew it, so the second half is inverted.**
 * What a rainy frame must show now is one canopy per figure, whatever its family, and that is what
 * is asserted below, figure by figure, on the rendered frame.
 *
 * ### The third question, which is new and is the one with teeth
 *
 * "Is there something above this figure's head" was enough while the answer for a child was meant
 * to be *no*. As a test of *yes* it is far too easy: the shipped adult grip hangs the canopy 59.5
 * units above the feet, and a child is 54 units tall, so **giving a child the adult's grip also
 * puts something above its head** -- floating in the air with the handle across its face, which is
 * the photograph in `V5_4E_REPORT.md` §1.1 and precisely the defect this pass exists to fix.
 *
 * So each figure is asked **how far above its own head the addition starts** ([CANOPY_MAX_GAP]).
 * A canopy is carried, not hung: its lower edge overlaps the head it covers -- the adult's bottom
 * edge sits at 81 units against a head-top of 82.3, the child's 70 % canopy at 53.3 against 54.0 --
 * so the gap is zero on a frame that is right and is seven pixels at this viewport on a child
 * wearing an adult's grip. That is the assertion the grip mutation dies on.
 *
 * ### How a figure and its umbrella are counted
 *
 * Not by colour: five of the scene's own colours *are* [PedestrianCarry.PALETTE], so a parasol, a
 * hedge and an outline all match it.
 *
 * Every scene is rendered **four ways in lockstep** off one clock -- rainy and dry, each with the
 * pedestrians switched on and off -- so each sample compares the same instant of the same street
 * four ways and nothing is attributable to drift. Subtracting the people-less frame from the
 * peopled one leaves exactly the people; the rain is drawn identically in both and subtracts away
 * with everything else.
 *
 *  - the **dry** subtraction gives the bare figures. Connected components of it are the walkers on
 *    screen, and a component's height says which family it is: at this viewport an adult measures
 *    19.9 to 21.6 px from feet to head-top and a child 13.0 to 14.1, so [ADULT_MIN_HEIGHT] sits in
 *    an empty gap rather than on a boundary. That is the drawn artwork talking -- a child's ink
 *    reaches 54 of the sprite canvas's 84 units where an adult's reaches 82.7;
 *  - the **rainy** subtraction gives the same figures wearing their umbrellas. What it has that the
 *    dry one does not -- the raised arm, the handle and the canopy -- is the umbrella, and it sits
 *    above and around the figure it belongs to.
 *
 * So each dry figure is looked up in the rainy frame over its own columns, and asked whether
 * anything was added above its head. An adult must have something there; a child must have
 * nothing. Figures touching a screen edge are skipped -- a half-drawn walker is a half-measured
 * height -- and so is any child standing under a neighbouring adult's canopy, which is not evidence
 * about the child. A canopy is **not** centred on its owner: the grip sits 12 units to the leading
 * side of the 39-unit sprite canvas and the canopy is 48 wide, so it reaches **16.5 units past the
 * walker on the leading side** and stops **7.5 units short** on the trailing one, mirroring with
 * the figure. At this viewport the leading overhang is 4.3 px.
 *
 * ### The sampling, and why it is a stretch rather than a frame
 *
 * Whether a given walker is on screen at a given instant is a fact about where it is walking, and
 * an umbrella may only go up while no copy of its walker is visible ([PedestrianCarry.nextCarrying]
 * -- the off-screen rule). A walker crosses a whole tile in `1 / PEDESTRIAN_SPEED_NEAR` = 38.5 s
 * and is out of sight for about half of it, so the warm-up runs 55 s of scene time before anything
 * is measured and the last 10 s are sampled. Every sample must satisfy the rule; a single frame
 * would be a claim about one instant.
 */
class PedestrianUmbrellaSceneTest {

    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    private val width = 360
    private val height = 800

    /**
     * Warm-up, and the step it advances by.
     *
     * A walker crosses a whole tile in 38.5 s and is off screen for about half of it, so 55 s of
     * scene time gives every walker its chance at least once. The step is coarser than a real frame
     * on purpose -- the carry state is sampled per draw, not per second, and 0.5 s still puts about
     * forty samples inside each off-screen stretch -- because 55 s at the wallpaper's own 30 fps
     * would be 1 650 renders per scene.
     */
    private val warmUpSteps = 110
    private val warmUpStep = 0.5f

    /** The last 10 s of the warm-up is measured, not just rendered. */
    private val sampleEvery = 4
    private val sampleFrom = 90

    /** The band the walkers and their canopies occupy: the two pavement rows and the air above. */
    private val bandTop get() = (height * 0.68f).toInt()
    private val bandBottom get() = (height * 0.84f).toInt()

    /**
     * Feet-to-head height, in pixels of this viewport, above which a figure is an adult.
     *
     * Derived from the artwork rather than chosen. An adult's ink reaches 82.0-83.3 of the sprite
     * canvas's 84 units and a child's 53.7-58.0, and the scale a walker is drawn at is
     * `PERSON_BASE_SCALE * perspectiveScaleAt(row) * sceneScale(800)`.
     *
     * **The row is not one of two values.** `PedestrianPopulation` jitters it by
     * `MEMBER_ROW_SPREAD` = 0.012 and clamps the result to the pavement band, so `row` runs from
     * about 0.783 to `SceneSpace.roadTopYFraction()`, and the scale with it -- 0.2200 to 0.2798
     * rather than the 0.2405 and 0.2612 the two nominal rows alone would give. Over that whole band
     * an adult measures **18.2 to 23.1 px** and a child **11.9 to 15.1 px**, so the gap is 15.1 to
     * 18.2 and 17 sits inside it. The narrower ranges the two nominal rows imply would have put the
     * threshold in the same place, but they are not the ranges this scene produces: a child
     * measured **15** in the run that found the edge-clipping defect above, which is over the top of
     * the un-jittered child range and would have been read as an adult by a tighter number.
     */
    private val ADULT_MIN_HEIGHT = 17

    /**
     * How far, in pixels, to look outside a figure's own columns for its canopy.
     *
     * The canopy reaches 16.5 sprite units past the walker on its leading side, which is 4.3 px at
     * this viewport's near row; 7 rounds that up with room for the anti-aliased edge. It is used
     * twice and asymmetrically on purpose: as a small margin when asking whether a figure has a
     * canopy, and multiplied by four as the wide exclusion zone that decides whether a child is
     * standing clear of every adult -- there, being generous means measuring fewer children rather
     * than crediting an adult's canopy to one.
     */
    private val CANOPY_OVERHANG = 7

    /**
     * How far above a figure's own head, in pixels, its canopy may start.
     *
     * **The assertion that tells a carried umbrella from a hung one**, and the one the shipped
     * grip's own history makes necessary. A canopy sits *on* the figure it belongs to: the adult's
     * lower edge is at 81 units above the feet against a head-top of 82.3, and the child's 70 %
     * canopy at 53.3 against 54.0 -- both overlap, so the gap on a correct frame is **zero**, and
     * the handle's tip rises 0.7 units past the head before the canopy even starts.
     *
     * Give a child the adult's grip instead -- (31.5, 24.5), crown 1.0 -- and the canopy's lower
     * edge lands 81 units up on a figure 54 units tall: a gap of **27 units, 7.1 px at this
     * viewport's near row**, which is the umbrella floating in the air with the handle down the
     * child's face. 3 is well above the zero a right frame measures, well under that, and leaves
     * room for the anti-aliased edge and for the row jitter that makes a near figure larger than a
     * far one.
     */
    private val CANOPY_MAX_GAP = 3

    private fun rain() = LiveWeatherSnapshot(
        precipitationType = PrecipitationType.RAIN,
        precipitationIntensity = 0.6f,
        cloudCoverFraction = 1f,
        isThunderstorm = false,
        fetchedAtMillis = 0L,
    )

    private fun snow() = LiveWeatherSnapshot(
        precipitationType = PrecipitationType.SNOW,
        precipitationIntensity = 0.6f,
        cloudCoverFraction = 1f,
        isThunderstorm = false,
        fetchedAtMillis = 0L,
    )

    /** How weather reaches the scene: through the forecast, through the theme, or not at all. */
    private enum class Source { LIVE, THEME, NONE }

    private class Run(
        val renderer: PaperRenderer,
        val bitmap: Bitmap,
        val target: CanvasSceneTarget = CanvasSceneTarget(),
    )

    private fun newRun(
        themeId: String,
        source: Source,
        type: PrecipitationType,
        people: Boolean,
        intensity: Float,
        thunderstorm: Boolean,
    ): Run {
        val renderer = PaperRenderer(width, height, context)
        renderer.theme = ThemeCatalog.byId(themeId)
        renderer.sceneCustomization = defaultCustomizationFor(themeId).let { c ->
            c.copy(
                people = c.people.copy(visible = people),
                precipitation = c.precipitation.copy(
                    visible = source == Source.THEME,
                    type = type,
                    intensity = if (source == Source.THEME) intensity else c.precipitation.intensity,
                    thunderstorm = source == Source.THEME && thunderstorm,
                ),
            )
        }
        renderer.liveWeatherOverride = when (source) {
            Source.LIVE ->
                if (type == PrecipitationType.RAIN) {
                    rain().copy(precipitationIntensity = intensity, isThunderstorm = thunderstorm)
                } else {
                    snow().copy(precipitationIntensity = intensity)
                }
            Source.THEME, Source.NONE -> null
        }
        renderer.homeScreenOffset = 0f
        renderer.swipeScrollEnabled = false
        renderer.scrollSpeed = 0f
        renderer.parallaxStrength = 1f
        renderer.lightningStrikesEnabled = false
        return Run(renderer, Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888))
    }

    private fun step(run: Run, phase: SunPositionCalculator.DayPhase, seconds: Double) {
        run.target.bind(Canvas(run.bitmap))
        run.renderer.draw(run.target, phase, SceneTime(seconds), warmUpStep)
        run.target.unbind()
    }

    /** The band, as a boolean mask of where the two frames differ: with people against without. */
    private fun peopleMask(withPeople: Bitmap, withoutPeople: Bitmap): Array<BooleanArray> {
        val h = bandBottom - bandTop
        val mask = Array(h) { BooleanArray(width) }
        for (row in 0 until h) {
            val y = bandTop + row
            for (x in 0 until width) {
                val p = withPeople.getPixel(x, y)
                val q = withoutPeople.getPixel(x, y)
                val d = max(
                    abs(((p shr 16) and 0xFF) - ((q shr 16) and 0xFF)),
                    max(
                        abs(((p shr 8) and 0xFF) - ((q shr 8) and 0xFF)),
                        abs((p and 0xFF) - (q and 0xFF)),
                    ),
                )
                if (d > 8) mask[row][x] = true
            }
        }
        return mask
    }

    /**
     * One connected run of people-pixels: a figure, in band coordinates.
     *
     * `inner` so that [touchesEdge] can read [width] rather than repeat it. A figure clipped by a
     * screen edge is a half-measured height, and this test classifies figures *by* height, so the
     * edge is load-bearing and a second copy of 360 would be a way for it to go quietly wrong.
     */
    private inner class Blob(var x0: Int, var x1: Int, var y0: Int, var y1: Int, var area: Int) {
        val heightPx get() = y1 - y0 + 1
        val touchesEdge get() = x0 <= 0 || x1 >= width - 1
    }

    /** Eight-connected components of [mask], smaller than [MIN_BLOB_AREA] discarded as antialiasing. */
    private fun blobs(mask: Array<BooleanArray>): List<Blob> {
        val h = mask.size
        val seen = Array(h) { BooleanArray(width) }
        val out = mutableListOf<Blob>()
        val stackY = IntArray(h * width)
        val stackX = IntArray(h * width)
        for (sy in 0 until h) for (sx in 0 until width) {
            if (!mask[sy][sx] || seen[sy][sx]) continue
            var top = 0
            stackY[0] = sy; stackX[0] = sx; top = 1
            seen[sy][sx] = true
            val b = Blob(sx, sx, sy, sy, 0)
            while (top > 0) {
                top--
                val y = stackY[top]; val x = stackX[top]
                b.area++
                if (x < b.x0) b.x0 = x
                if (x > b.x1) b.x1 = x
                if (y < b.y0) b.y0 = y
                if (y > b.y1) b.y1 = y
                for (dy in -1..1) for (dx in -1..1) {
                    val ny = y + dy; val nx = x + dx
                    if (ny in 0 until h && nx in 0 until width && mask[ny][nx] && !seen[ny][nx]) {
                        seen[ny][nx] = true
                        stackY[top] = ny; stackX[top] = nx; top++
                    }
                }
            }
            if (b.area >= MIN_BLOB_AREA) out += b
        }
        return out
    }

    /** Under this many pixels a component is an antialiased fragment, not a walker. */
    private val MIN_BLOB_AREA = 12

    /**
     * What one sampled frame says about one street.
     *
     * [children] is every measurable child and [childrenCarrying] how many have a canopy over them
     * -- their own or, for one walking beside an adult, the adult's. [loneChildren] is the subset
     * standing clear of every adult's overhang, and [loneChildrenCarrying] is therefore the count
     * that cannot be satisfied by somebody else's umbrella.
     *
     * [worstGap] is the largest distance measured between any figure's head and the bottom of what
     * was added above it, with [worstGapLabel] naming where -- see [CANOPY_MAX_GAP].
     */
    private class Frame(
        val step: Int,
        val adults: Int,
        val adultsCarrying: Int,
        val children: Int,
        val childrenCarrying: Int,
        val loneChildren: Int,
        val loneChildrenCarrying: Int,
        val worstGap: Int,
        val worstGapLabel: String,
        val addedPixels: Int,
    )

    /**
     * Reads one sampled instant: which figures are on the pavement, and which of them have
     * something above their heads that the dry frame does not have.
     */
    private fun readFrame(
        step: Int, wetOn: Bitmap, wetOff: Bitmap, dryOn: Bitmap, dryOff: Bitmap,
    ): Frame {
        val dry = peopleMask(dryOn, dryOff)
        val wet = peopleMask(wetOn, wetOff)
        var added = 0
        for (row in dry.indices) for (x in 0 until width) if (wet[row][x] && !dry[row][x]) added++

        val all = blobs(dry)
        val measurable = all.filter { !it.touchesEdge }
        val adults = measurable.filter { it.heightPx >= ADULT_MIN_HEIGHT }
        val children = measurable.filter { it.heightPx < ADULT_MIN_HEIGHT }

        fun addedAbove(b: Blob): Int {
            var n = 0
            for (row in 0 until b.y0) {
                for (x in (b.x0 - CANOPY_OVERHANG).coerceAtLeast(0)..
                    (b.x1 + CANOPY_OVERHANG).coerceAtMost(width - 1)) {
                    if (wet[row][x] && !dry[row][x]) n++
                }
            }
            return n
        }

        /**
         * Rows between the top of [b] and the lowest added pixel above it, or -1 if there is none.
         *
         * The *lowest* added row rather than the highest: what is being measured is where the thing
         * over this figure begins, and a taller neighbour's canopy further up cannot make that
         * number smaller.
         */
        fun gapAbove(b: Blob): Int {
            for (row in b.y0 - 1 downTo 0) {
                for (x in (b.x0 - CANOPY_OVERHANG).coerceAtLeast(0)..
                    (b.x1 + CANOPY_OVERHANG).coerceAtMost(width - 1)) {
                    if (wet[row][x] && !dry[row][x]) return b.y0 - row - 1
                }
            }
            return -1
        }

        // A child standing under a neighbouring adult's canopy is not evidence about the child:
        // the canopy overhangs its owner. Only children clear of every adult's column range are
        // asked the question.
        //
        // **The shade is cast by `all`, not by `adults`, and that is not a slip.** A figure clipped
        // by a screen edge is dropped from `measurable` because its height cannot be trusted -- but
        // its canopy is still drawn, and still overhangs whoever is standing beside it. The full
        // suite found exactly that: a walker clipped at x=359 in `desert` put the left tip of its
        // canopy at x=350..354, nineteen pixels inside the window of a child at x=340..347 that
        // nothing was shading, and this test's v5.4E form -- `childrenCarryNothing` -- reported a
        // child holding an umbrella that belonged to the adult next to it. Height survives a
        // horizontal clip even when width does not, so `heightPx` still identifies who could be
        // carrying.
        //
        // **v5.4H inverted the assertion and the filter is still needed, for the mirror reason.**
        // Every child carries now, so a neighbour's canopy no longer produces a false *positive*
        // about a rule -- it produces a false positive about *whose umbrella it is*, which is the
        // only thing that distinguishes a drawn child pose from an adult standing close by.
        val shaded = all.filter { it.heightPx >= ADULT_MIN_HEIGHT }
            .map { (it.x0 - CANOPY_OVERHANG * 4)..(it.x1 + CANOPY_OVERHANG * 4) }
        val lone = children.filter { c -> shaded.none { it.first <= c.x1 && c.x0 <= it.last } }

        var worstGap = -1
        var worstLabel = ""
        for (b in measurable) {
            val gap = gapAbove(b)
            if (gap > worstGap) {
                worstGap = gap
                worstLabel = (if (b.heightPx >= ADULT_MIN_HEIGHT) "adult" else "child") +
                    " ${b.heightPx}px at x=${b.x0}..${b.x1}"
            }
        }

        return Frame(
            step = step,
            adults = adults.size,
            adultsCarrying = adults.count { addedAbove(it) >= CANOPY_MIN_PIXELS },
            children = children.size,
            childrenCarrying = children.count { addedAbove(it) >= CANOPY_MIN_PIXELS },
            loneChildren = lone.size,
            loneChildrenCarrying = lone.count { addedAbove(it) >= CANOPY_MIN_PIXELS },
            worstGap = worstGap,
            worstGapLabel = worstLabel,
            addedPixels = added,
        )
    }

    /**
     * How many added pixels over a figure's head count as an umbrella.
     *
     * A near-row canopy is 48 sprite units across and 24 tall at 0.2612 px per unit, so about
     * 12 x 6 px of ink plus its handle -- call it forty pixels at the smallest. Eight is well under
     * anything a canopy can be and well over the antialiasing the subtraction leaves behind, which
     * measures zero on a dry frame against itself.
     */
    private val CANOPY_MIN_PIXELS = 8

    /**
     * Every sampled instant of one street, warmed up first.
     *
     * [everyFrame] reads **all twenty** frames of the measured window instead of five of them, and
     * one test asks for it. **It was added because a mutation escaped without it.** Handing a child an umbrella by
     * dropping the artwork guard from `wanted` alone leaves the guard inside `carrying`, which
     * `nextCarrying` is fed as `current` -- so the canopy is drawn on the first on-screen frame and
     * the state is reset to false on that same frame, and the child's umbrella exists for **one
     * frame each time it re-enters the screen**. Measured on the mutated build over 110 consecutive
     * frames: `desert` 2, `spring` 1, `city` 7. Five samples out of twenty saw none of them.
     *
     * A one-frame flicker is not the rule being broken, but it is a child with an umbrella in it,
     * and a test of "children carry nothing" that cannot see one is not measuring what it says.
     *
     * **v5.4H narrowed it from all 110 frames to all twenty of the window**, because its assertion
     * is now the positive one and the frames outside the window are the warm-up -- see the comment
     * at the `measure` line. Four times the coverage of the sparse sampling, on consecutive frames,
     * which is the property that catches a flicker; the 90 it gives up are frames in which a bare
     * figure is the rule working rather than failing.
     */
    private fun sample(
        themeId: String,
        source: Source,
        type: PrecipitationType = PrecipitationType.RAIN,
        night: Boolean = false,
        intensity: Float = 0.6f,
        thunderstorm: Boolean = false,
        everyFrame: Boolean = false,
    ): List<Frame> {
        val wetOn = newRun(themeId, source, type, true, intensity, thunderstorm)
        val wetOff = newRun(themeId, source, type, false, intensity, thunderstorm)
        val dryOn = newRun(themeId, Source.NONE, type, true, intensity, false)
        val dryOff = newRun(themeId, Source.NONE, type, false, intensity, false)
        val phase = if (night) GoldenScene.night() else GoldenScene.day()
        var seconds = 120.0
        val out = mutableListOf<Frame>()
        for (i in 0 until warmUpSteps) {
            step(wetOn, phase, seconds)
            step(wetOff, phase, seconds)
            step(dryOn, phase, seconds)
            step(dryOff, phase, seconds)
            // **The warm-up is rendered and not measured, and with v5.4H's assertion inverted that
            // is load-bearing rather than tidy.** A walker that is on screen when the scene starts
            // finishes its crossing bare-headed -- `nextCarrying` may only move out of sight, which
            // is the whole of the off-screen rule -- so early frames legitimately contain figures
            // with nothing over them. Measured on the shipped build: reading all 110 frames of
            // `desert` finds a lone child with no umbrella on **23** of them, and they are
            // **steps 0 to 22** -- one contiguous run at the very start, the first 11.5 s of scene
            // time, nothing after. v5.4E could read all 110 because its assertion was negative and
            // a bare child satisfied it.
            val measure =
                if (everyFrame) i >= sampleFrom else i >= sampleFrom && (i - sampleFrom) % sampleEvery == 0
            if (measure) {
                out += readFrame(i, wetOn.bitmap, wetOff.bitmap, dryOn.bitmap, dryOff.bitmap)
            }
            seconds += warmUpStep.toDouble()
        }
        return out
    }

    /**
     * The assertion the rule reduces to, over a sampled stretch of one street.
     *
     * **One canopy per walking figure, adults and children**, and each of them close enough over
     * its owner's head to have been carried rather than hung there ([CANOPY_MAX_GAP]).
     *
     * The children are counted twice on purpose. Every measurable child must have something over
     * it -- that is *"nobody walks through the rain bare-headed"*, and for a child beside an adult
     * the adult's canopy is a true answer to it. Then the children standing clear of every adult's
     * overhang are counted again on their own, and that second count is the one no neighbour's
     * umbrella can satisfy: it is the evidence that the child pose is being drawn, and it is what
     * this whole pass is for.
     */
    private fun assertEveryWalkerCarries(label: String, frames: List<Frame>) {
        assertTrue(
            "$label: no adult was ever on screen, so nothing was measured",
            frames.sumOf { it.adults } > 0,
        )
        assertTrue(
            "$label: no child was ever on screen, so the half this pass is about was not measured",
            frames.sumOf { it.children } > 0,
        )
        val bareAdults = frames.filter { it.adultsCarrying != it.adults }
        assertTrue(
            "$label: an adult walked through the rain with no umbrella -- " +
                bareAdults.joinToString { "${it.adultsCarrying}/${it.adults} carrying" },
            bareAdults.isEmpty(),
        )
        val bareChildren = frames.filter { it.childrenCarrying != it.children }
        assertTrue(
            "$label: a child walked through the rain with nothing over its head -- " +
                bareChildren.joinToString { "${it.childrenCarrying}/${it.children} covered" },
            bareChildren.isEmpty(),
        )
        assertTrue(
            "$label: no child was ever clear of an adult's canopy, so nothing was measured about " +
                "a child's own umbrella",
            frames.sumOf { it.loneChildren } > 0,
        )
        val borrowed = frames.filter { it.loneChildrenCarrying != it.loneChildren }
        assertTrue(
            "$label: a child standing clear of every adult had no umbrella of its own -- " +
                borrowed.joinToString { "${it.loneChildrenCarrying}/${it.loneChildren} carrying" },
            borrowed.isEmpty(),
        )
        val hung = frames.filter { it.worstGap > CANOPY_MAX_GAP }
        assertTrue(
            "$label: an umbrella is hanging in the air above its owner rather than being carried " +
                "-- " + hung.joinToString { "${it.worstGap} px over its ${it.worstGapLabel}" },
            hung.isEmpty(),
        )
    }

    /**
     * **The maintainer's rule, on live-weather rain: every adult on the pavement is carrying.**
     *
     * `spring` is the street the previous round photographed and the smallest the built-in set
     * produces -- two adults among seven figures -- so it is both the theme that had none and the
     * one where a single missed walker is most visible.
     */
    @Test
    fun liveWeatherRainPutsAnUmbrellaOnEveryAdult() {
        assertEveryWalkerCarries("spring, live rain", sample("spring", Source.LIVE))
    }

    /** **And so does the theme's own rain**, which is the half the maintainer's report doubted. */
    @Test
    fun theThemesOwnRainPutsAnUmbrellaOnEveryAdult() {
        assertEveryWalkerCarries("spring, theme rain", sample("spring", Source.THEME))
    }

    /**
     * The two sources raise **the same** umbrellas -- not "both over a floor", the same count on
     * the same instants. It is the property a "two sources, one read" defect would break, and the
     * street is the busiest of the twelve so the count it compares is a real one.
     */
    @Test
    fun theTwoSourcesRaiseTheSameUmbrellas() {
        val live = sample("winter", Source.LIVE)
        val theme = sample("winter", Source.THEME)
        assertEveryWalkerCarries("winter, live rain", live)
        assertEveryWalkerCarries("winter, theme rain", theme)
        assertEquals(
            "the same street, whichever source says it is raining",
            live.map { it.adultsCarrying }, theme.map { it.adultsCarrying },
        )
    }

    /**
     * **Any rain is rain**: the lightest drizzle the scene will draw and a full thunderstorm put up
     * the same umbrellas as the ordinary shower the other tests use.
     *
     * This is the half of the instruction that says *"qualsiasi pioggia"*, and it is a real question
     * rather than a restatement -- the storm gate is a separate predicate from the rain one, and
     * `PaperRenderer` keeps them apart on purpose.
     */
    @Test
    fun drizzleAndThunderstormBothCount() {
        assertEveryWalkerCarries("city, drizzle", sample("city", Source.LIVE, intensity = 0.05f))
        assertEveryWalkerCarries(
            "city, thunderstorm",
            sample("city", Source.LIVE, intensity = 1f, thunderstorm = true),
        )
    }

    /** A rainy night is still rain, and a walker at night is still a walker. */
    @Test
    fun umbrellasGoUpAtNightAsWell() {
        assertEveryWalkerCarries("city at night", sample("city", Source.LIVE, night = true))
    }

    /**
     * **Every child carries one of its own, on every frame of the warm-up, and it shows on the
     * pixels.**
     *
     * Until v5.4H this test was `childrenCarryNothing` and asserted the opposite. That was never a
     * rule: `PeopleLayerTable.CARRY` had no pose for a child, so there was nothing to draw, and the
     * test's own comment said that the day the pose existed it should go red. It did.
     *
     * `desert` is the street it has always read, and it is the right one for the question: three of
     * its five children walk in a group of their own (`ggg`), so it has children in the clear on
     * nearly every frame rather than only where the deal happens to separate one.
     *
     * **This one reads every frame of the warm-up, not five of the last twenty**, and [sample]'s
     * own doc says which mutation made that necessary. The direction has flipped with the
     * assertion and the reason has not: a one-frame flicker was what escaped five samples out of
     * twenty then, and a child whose umbrella is missing for one frame in a hundred and ten is
     * exactly as invisible to a sparse sample now.
     */
    @Test
    fun everyChildCarriesOneOfItsOwn() {
        val frames = sample("desert", Source.LIVE, everyFrame = true)
        assertTrue(
            "no child was ever in the clear, so nothing was measured",
            frames.sumOf { it.loneChildren } > 0,
        )
        assertEquals(
            "a child standing clear of every adult was drawn with no umbrella, on these steps of " +
                "the measured window: " +
                frames.filter { it.loneChildrenCarrying != it.loneChildren }.map { it.step },
            0, frames.count { it.loneChildrenCarrying != it.loneChildren },
        )
        assertEquals(
            "and this many lone children were bare-headed in total",
            0, frames.sumOf { it.loneChildren - it.loneChildrenCarrying },
        )
        assertEquals(
            "an umbrella was hanging above a figure instead of over it, on this many frames",
            0, frames.count { it.worstGap > CANOPY_MAX_GAP },
        )
    }

    /**
     * Nothing is carried through snow -- the other direction, which a floor alone would let a
     * renderer satisfy by painting canopies whenever anything falls.
     *
     * **Exactly zero added pixels**, not "no umbrella found": the people in a snowing frame are the
     * same pixels as the people in a dry one, because [PedestrianCarry.wantsUmbrella] is given the
     * renderer's own rain predicate and snow is not rain.
     */
    @Test
    fun snowRaisesNoUmbrella() {
        val frames = sample("winter", Source.LIVE, type = PrecipitationType.SNOW)
        assertEquals(
            "snow is not rain, and raised an umbrella anyway",
            0, frames.sumOf { it.addedPixels },
        )
        assertEquals("nor half of one", 0, frames.sumOf { it.adultsCarrying })
    }

    /**
     * And a clear sky raises none either, measured the same way: a dry street's people are the same
     * pixels as a dry street's people.
     */
    @Test
    fun aClearSkyRaisesNoUmbrella() {
        val frames = sample("spring", Source.NONE)
        assertEquals(
            "an umbrella in a clear sky",
            0, frames.sumOf { it.addedPixels },
        )
    }
}
