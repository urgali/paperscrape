package com.paperscrape.livewallpaper.engine

import android.graphics.Paint
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **A steady frame of the wallpaper allocates nothing** (`AI_PROJECT_RULES.md` 5.1; v5.10B).
 *
 * Until v5.10B a frame allocated about 200 objects at night on the BV6600 (v5.10A, ART's allocation
 * tracker on the live wallpaper): Android 10's `Paint.setAlpha`, the crowd rebuilt every frame, one
 * object per lit window, iterators, boxed `Float`s, a `DayPhase`, a pacing plan. The maintainer had
 * them all removed on 2026-09-30, and this is what keeps them removed: every built-in theme by day
 * and by night, the lake at its busiest, rain, snow, a storm driven by Live Weather, the sleigh and
 * the fireworks, each warmed up past the moment the traffic arrives and then drawn for
 * [COUNTED_FRAMES] frames while ART counts what this thread allocates. Since v5.11 also the distant
 * houses on the mountains, by day, at night and under snow, and a scene with everything on at once:
 * the houses at 100 %, opening hours (the lamps and the glass of the shut shops), the lake at its
 * busiest, the decorations and the weather. Since v5.14 the Christmas decorations of the buildings
 * and the fir A, by day and at night, the distant houses' bulbs, and a shut building's blinds and
 * shutter by day.
 *
 * The frames are drawn by the real [PaperRenderer] into a [SceneCanvas] that does nothing, so the
 * count is the scene's own: the two backends allocate nothing per frame of their own (the GL one
 * by construction, its buffers and stacks built once; the allocation tracker on the live wallpaper
 * shows no site in either). The scene scrolls, as the wallpaper does.
 *
 * **Events are not frames.** A firework burst, a gift thrown from the sleigh and a sleigh setting
 * off are an object each when they happen -- every few seconds, not every frame -- so the five
 * scenes that have them (Christmas and New Year's Eve at night, Christmas at noon and at 23:00 with
 * the distant houses -- the sleigh flies by day too -- and everything on at once at Christmas) are
 * held to "fewer objects than a tenth of the frames" and log their exact
 * count; every other scene is held to zero.
 *
 * `Debug.startAllocCounting` is deprecated (its counts are coarse and global), and on API 29 it
 * still counts the objects a thread allocates: [countingWorks] is the positive control that shows
 * it does on the device running the test, before any zero below is believed.
 */
@RunWith(AndroidJUnit4::class)
class FrameAllocationTest {

    private class Scene(
        val name: String,
        val themeId: String,
        val hour: Float,
        val weather: LiveWeatherSnapshot? = null,
        val events: Boolean = false,
        val customise: (SceneCustomization) -> SceneCustomization = { it },
    )

    /**
     * Draws nothing, allocates nothing: the renderer's own allocations are what is counted. It counts the
     * sprites cut to a box -- people walking out of a window or into one (v5.12) -- so a scene that is
     * meant to have somebody walking can be seen to have had them.
     */
    private object NothingCanvas : SceneCanvas {
        var clipped = 0
        /** The shapes drawn: a shut building's shutter is drawn in shapes ([DoorShutter]'s bands, v5.14). */
        var shapes = 0
        override fun drawSpriteClipped(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
            additive: Boolean,
            clipLeft: Float,
            clipTop: Float,
            clipRight: Float,
            clipBottom: Float,
        ) {
            clipped++
        }
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = Unit
        override fun drawDisc(resId: Int, source: SpriteSource, cx: Float, cy: Float, radius: Float, colour: Int, alpha: Int) = Unit
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawWedge(cx: Float, cy: Float, radius: Float, startAngle: Float, sweepAngle: Float, paint: Paint) = Unit
        override fun drawShape(shape: SceneShape, paint: Paint) { shapes++ }
        override fun drawVerticalGradientShape(
            shape: SceneShape,
            gradientTopY: Float,
            gradientBottomY: Float,
            topColor: Int,
            bottomColor: Int,
            alpha: Int,
        ) = Unit
        override fun drawVerticalGradientRect(left: Float, top: Float, right: Float, bottom: Float, topColor: Int, bottomColor: Int) = Unit
        override fun drawRadialGlow(cx: Float, cy: Float, radius: Float, color: Int, centerAlpha: Int) = Unit
        override fun drawSprite(
            resId: Int,
            source: SpriteSource,
            left: Float,
            top: Float,
            tintColor: Int,
            alpha: Int,
            additive: Boolean,
        ) = Unit
    }

    private val rain: (SceneCustomization) -> SceneCustomization = {
        it.copy(precipitation = it.precipitation.copy(visible = true, type = PrecipitationType.RAIN, intensity = 1f))
    }
    private val snow: (SceneCustomization) -> SceneCustomization = {
        it.copy(precipitation = it.precipitation.copy(visible = true, type = PrecipitationType.SNOW, intensity = 1f))
    }
    private val busyLake: (SceneCustomization) -> SceneCustomization = {
        it.copy(
            lake = it.lake.copy(
                visible = true, height = 0.8f,
                sailboatsVisible = true, sailboatsDensity = 1f, dolphinsVisible = true, dolphinsDensity = 1f,
            ),
        )
    }

    /** The distant houses on, at 100 % (v5.11): every place of every mountain stands a house. */
    private val distantHouses: (SceneCustomization) -> SceneCustomization = {
        it.copy(distantHouses = DistantHousesConfig(visible = true, density = 1f))
    }

    /** The three groups' hours 13:00-20:00 (v5.14): at noon every building that keeps hours is shut (the fire and the police station never close), its blinds and shutter down. */
    private val shutAtNoon: (SceneCustomization) -> SceneCustomization = {
        it.copy(
            shopHoursEnabled = true, shopOpenHour = 13f, shopCloseHour = 20f,
            publicHoursEnabled = true, publicOpenHour = 13f, publicCloseHour = 20f,
            towerHoursEnabled = true, towerOpenHour = 13f, towerCloseHour = 20f,
        )
    }

    /** Everything on at once (v5.11): the distant houses, opening hours, the lake, decorations, rain. */
    private val everything: (SceneCustomization) -> SceneCustomization = { c ->
        val full = { o: ObjectVariantConfig -> o.copy(visible = true, density = 1f) }
        busyLake(rain(distantHouses(c))).copy(
            shopHoursEnabled = true, towerHoursEnabled = true,
            houses = full(c.houses), buildings = full(c.buildings), trees = full(c.trees), parasols = full(c.parasols),
            snowmen = full(c.snowmen), gifts = full(c.gifts), pumpkins = full(c.pumpkins),
            easterEggs = full(c.easterEggs), bunnies = full(c.bunnies), penguins = full(c.penguins),
        )
    }

    private val scenes = listOf(
        Scene("sunset-noon", "sunset", 13f),
        Scene("sunset-night", "sunset", 1f),
        Scene("autumn-noon", "autumn", 12f),
        Scene("autumn-midnight", "autumn", 0f),
        Scene("winter-snow", "winter", 13f, customise = snow),
        Scene("christmas-night", "christmas", 1f, events = true),
        Scene("new-year-night", "new_year", 1f, events = true),
        Scene("beach-day", "beach", 13f),
        Scene("city-evening", "city", 21f),
        Scene("halloween-night", "halloween", 22f),
        Scene("easter-morning", "easter", 11f),
        Scene("spring-rain", "spring", 9f, customise = rain),
        Scene("desert-afternoon", "desert", 15f),
        Scene("tundra-night", "tundra", 2f),
        Scene("lake-busy", "sunset", 13f, customise = busyLake),
        Scene("distant-houses-noon", "autumn", 12f, customise = distantHouses),
        Scene("distant-houses-midnight", "autumn", 0f, customise = distantHouses),
        Scene("distant-houses-snow-night", "winter", 23f, customise = { distantHouses(snow(it)) }),
        // v5.14: the Christmas decorations -- the fir A, the roofs' strings, the wreaths, the towers' stars -- by day and
        // on Autumn with the switch on at night; the distant houses' bulbs; a shut building's blinds and shutter by day.
        Scene("christmas-noon-decorations", "christmas", 12f, events = true),
        Scene("autumn-christmas-lights-night", "autumn", 1f, customise = { it.copy(christmasDecorationsEnabled = true) }),
        Scene("christmas-distant-houses-night", "christmas", 23f, events = true, customise = distantHouses),
        Scene("shut-by-day", "autumn", 12f, customise = shutAtNoon),
        Scene("everything-on-evening", "city", 21f, customise = everything),
        Scene("everything-on-night", "christmas", 1f, events = true, customise = everything),
        Scene(
            "live-storm", "sunset", 13f,
            weather = LiveWeatherSnapshot(
                precipitationType = PrecipitationType.RAIN,
                precipitationIntensity = 1f,
                cloudCoverFraction = 1f,
                isThunderstorm = true,
                fetchedAtMillis = 0L,
            ),
        ),
    )

    /** Objects this thread allocated while [block] ran. */
    @Suppress("DEPRECATION")
    private inline fun allocationsDuring(block: () -> Unit): Int {
        Debug.startAllocCounting()
        Debug.resetThreadAllocCount()
        block()
        val count = Debug.getThreadAllocCount()
        Debug.stopAllocCounting()
        return count
    }

    @Test
    fun countingWorks() {
        val kept = arrayOfNulls<Any>(1_000)
        val counted = allocationsDuring { for (i in kept.indices) kept[i] = Any() }
        assertTrue("ART counted $counted objects for 1 000 allocated: the counts below mean nothing", counted >= 1_000)
        assertEquals(1_000, kept.count { it != null })
    }

    @Test
    fun aSteadyFrameAllocatesNothing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = StringBuilder()
        val failures = ArrayList<String>()
        for (scene in scenes) {
            val renderer = PaperRenderer(WIDTH, HEIGHT, context)
            renderer.theme = ThemeCatalog.byId(scene.themeId)
            renderer.sceneCustomization = scene.customise(defaultCustomizationFor(scene.themeId))
            renderer.liveWeatherOverride = scene.weather
            renderer.lightningStrikesEnabled = false
            renderer.scrollSpeed = 0.15f
            val dayPhase = SunPositionCalculator.compute(hour24 = scene.hour)
            var time = SceneTime(0.0)
            repeat(WARM_UP_FRAMES) {
                time += FRAME
                renderer.draw(NothingCanvas, dayPhase, time, FRAME)
            }
            val objects = allocationsDuring {
                repeat(COUNTED_FRAMES) {
                    time += FRAME
                    renderer.draw(NothingCanvas, dayPhase, time, FRAME)
                }
            }
            val line = "${scene.name}: $objects objects over $COUNTED_FRAMES frames"
            report.appendLine(line)
            Log.i(TAG, line)
            val allowed = if (scene.events) COUNTED_FRAMES / 10 else 0
            if (objects > allowed) failures += "$line (allowed $allowed)"
        }
        assertTrue("a steady frame allocated:\n${failures.joinToString("\n")}\n\nall scenes:\n$report", failures.isEmpty())
    }

    /**
     * **And while somebody walks** (v5.12): a person walking out of a window or into one is drawn cut
     * to the pane, at a position worked out per frame ([WindowWalk]) -- and that allocates nothing
     * either. Two kinds of walk, each seen to happen ([NothingCanvas.clipped] counts the cut blits):
     *
     *  - **the roster's**: Autumn at night, started where a person is walking from window to window --
     *    found by drawing frames a second apart until one has a cut blit;
     *  - **the hours'**: the shops' and the towers' hours on, and the hour moved on a minute at a time
     *    across a closing -- as the real clock moves it, if faster -- so the doorways walk their people out;
     *    then on past it until the glass is dark and the blinds and the shutters are down (v5.14C); swept once
     *    uncounted first, for the first uses of the code the minutes reach (see there).
     */
    @Test
    fun aFrameWithPeopleWalkingAllocatesNothing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        fun renderer(customise: (SceneCustomization) -> SceneCustomization = { it }) = PaperRenderer(WIDTH, HEIGHT, context).apply {
            theme = ThemeCatalog.byId("autumn")
            sceneCustomization = customise(defaultCustomizationFor("autumn"))
            lightningStrikesEnabled = false
            scrollSpeed = 0f
        }
        val night = SunPositionCalculator.compute(hour24 = 23f)

        // The roster: the first second at which somebody is seen walking -- from 30 s on, so the warm-up
        // before it never starts the scene's clock below zero, which the wallpaper's never is.
        val probe = renderer()
        var start = -1.0
        for (second in 30..900) {
            NothingCanvas.clipped = 0
            probe.draw(NothingCanvas, night, SceneTime(second.toDouble()), FRAME)
            if (NothingCanvas.clipped > 0) { start = second.toDouble(); break }
        }
        assertTrue("nobody walked from window to window in 15 minutes of Autumn", start > 0.0)
        check(start - 1.0 - WARM_UP_FRAMES * FRAME >= 0.0)
        val walking = renderer()
        var time = SceneTime(start - 1.0 - WARM_UP_FRAMES * FRAME)
        repeat(WARM_UP_FRAMES) { time += FRAME; walking.draw(NothingCanvas, night, time, FRAME) }
        NothingCanvas.clipped = 0
        val rosterObjects = allocationsDuring {
            repeat(COUNTED_FRAMES) { time += FRAME; walking.draw(NothingCanvas, night, time, FRAME) }
        }
        val rosterCut = NothingCanvas.clipped
        Log.i(TAG, "walking from window to window: $rosterObjects objects over $COUNTED_FRAMES frames, $rosterCut cut blits")

        // The hours: open 09:00-20:00, the hour moved on from 18:40 to 20:10 a minute every ten frames,
        // each minute's day phase made before the count (a DayPhase is an object).
        //
        // **Swept once uncounted, on a renderer of its own, before the count** (v5.12C). The minutes move
        // the people's colour clock, and a pedestrian's colours re-dealt across them run code that no frame
        // of the warm-up at one minute runs -- `PeopleColours.keepingSpread`, whose first run in the process
        // resolves the string constant of a parameter's null check: one object for the life of the process,
        // no frame's. Counted alone this sweep found it (1 in 910 frames, on the v5.12B2 build too, ART's
        // allocation tracker naming the site); in the whole suite a test before it had run that code. The
        // count is the second sweep's, on a fresh renderer: every frame's, and only those.
        val hoursOn: (SceneCustomization) -> SceneCustomization = {
            it.copy(shopHoursEnabled = true, shopOpenHour = 9f, shopCloseHour = 20f, towerHoursEnabled = true, towerOpenHour = 9f, towerCloseHour = 20f)
        }
        val minutes = (0..90).map { SunPositionCalculator.compute(hour24 = 18f + (40 + it) / 60f) }
        val firstUses = renderer(hoursOn)
        var firstClock = SceneTime(0.0)
        repeat(WARM_UP_FRAMES) { firstClock += FRAME; firstUses.draw(NothingCanvas, minutes[0], firstClock, FRAME) }
        val firstSweepObjects = allocationsDuring {
            for (minute in minutes.indices) {
                val phase = minutes[minute]
                repeat(10) { firstClock += FRAME; firstUses.draw(NothingCanvas, phase, firstClock, FRAME) }
            }
            repeat(SHUT_FRAMES) { firstClock += FRAME; firstUses.draw(NothingCanvas, minutes.last(), firstClock, FRAME) }
        }
        val closing = renderer(hoursOn)
        var clock = SceneTime(0.0)
        repeat(WARM_UP_FRAMES) { clock += FRAME; closing.draw(NothingCanvas, minutes[0], clock, FRAME) }
        NothingCanvas.clipped = 0
        val hoursObjects = allocationsDuring {
            for (minute in minutes.indices) {
                val phase = minutes[minute]
                repeat(10) { clock += FRAME; closing.draw(NothingCanvas, phase, clock, FRAME) }
            }
        }
        val hoursCut = NothingCanvas.clipped
        Log.i(TAG, "walking out at closing: $hoursObjects objects over ${minutes.size * 10} frames, $hoursCut cut blits (the uncounted first sweep: $firstSweepObjects)")

        // And on past the closing (v5.14C): at 20:10, as long as the slowest walk out takes and the glass's two seconds
        // after it, so the glass goes dark and the blinds and the shutters come down while counting -- the sweep above,
        // ten frames a minute, ends a few seconds into the walking out. Counted on the same renderer, in step.
        NothingCanvas.shapes = 0
        closing.draw(NothingCanvas, minutes.last(), clock, 0f)
        val shapesStillOpen = NothingCanvas.shapes
        val shutObjects = allocationsDuring {
            repeat(SHUT_FRAMES) { clock += FRAME; closing.draw(NothingCanvas, minutes.last(), clock, FRAME) }
        }
        NothingCanvas.shapes = 0
        closing.draw(NothingCanvas, minutes.last(), clock, 0f)
        val shapesShut = NothingCanvas.shapes
        Log.i(TAG, "after the closing: $shutObjects objects over $SHUT_FRAMES frames; shapes in a frame $shapesStillOpen -> $shapesShut")

        // And the opening (v5.14): 08:50 to 09:30, the glass lighting at 09:00 over its two seconds and the shutters
        // and blinds going up with it, then the people walking in -- ten frames a minute, so the walks of the opening
        // minute run on into the next ones. Swept once uncounted first, on a renderer of its own, as above.
        val mornings = (0..40).map { SunPositionCalculator.compute(hour24 = 8f + (50 + it) / 60f) }
        val firstMorning = renderer(hoursOn)
        var morningClock = SceneTime(0.0)
        repeat(WARM_UP_FRAMES) { morningClock += FRAME; firstMorning.draw(NothingCanvas, mornings[0], morningClock, FRAME) }
        for (minute in mornings.indices) repeat(10) { morningClock += FRAME; firstMorning.draw(NothingCanvas, mornings[minute], morningClock, FRAME) }
        val opening = renderer(hoursOn)
        var openClock = SceneTime(0.0)
        repeat(WARM_UP_FRAMES) { openClock += FRAME; opening.draw(NothingCanvas, mornings[0], openClock, FRAME) }
        NothingCanvas.clipped = 0
        val openingObjects = allocationsDuring {
            for (minute in mornings.indices) repeat(10) { openClock += FRAME; opening.draw(NothingCanvas, mornings[minute], openClock, FRAME) }
        }
        val openingCut = NothingCanvas.clipped
        Log.i(TAG, "walking in at opening: $openingObjects objects over ${mornings.size * 10} frames, $openingCut cut blits")

        assertTrue("the roster's walk was not drawn while counting ($rosterCut cut blits)", rosterCut > 0)
        assertTrue("nobody walked out at closing while counting ($hoursCut cut blits)", hoursCut > 0)
        assertTrue("nobody walked in at opening while counting ($openingCut cut blits)", openingCut > 0)
        assertEquals("a frame with somebody walking from window to window allocated", 0, rosterObjects)
        assertEquals("a frame with somebody walking out at closing allocated", 0, hoursObjects)
        assertTrue("no shutter came down after the closing (shapes $shapesStillOpen -> $shapesShut)", shapesShut > shapesStillOpen)
        assertEquals("a frame with the glass going dark and the shutters coming down allocated", 0, shutObjects)
        assertEquals("a frame with the glass lighting and somebody walking in at opening allocated", 0, openingObjects)
    }

    /**
     * **And while the places among the houses come and go** (v5.13B2): a place that leaves the screen comes
     * back as another copy of the street, with the building the street deals that copy ([StorefrontStreet]).
     * Autumn by day with the people at 100 %, swept by the swipe offset across three tiles of the street -- six
     * screens, so each of the five places leaves the screen and comes back as another copy three times -- one
     * frame of a fiftieth of a tile each. The sweep is drawn once uncounted over the three tiles before, for the
     * first uses; the counted one meets copies the scene has not drawn yet.
     */
    @Test
    fun aFrameAsThePlacesComeBackAllocatesNothing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val renderer = PaperRenderer(WIDTH, HEIGHT, context).apply {
            theme = ThemeCatalog.byId("autumn")
            sceneCustomization = defaultCustomizationFor("autumn").let { it.copy(people = it.people.copy(visible = true, density = 1f)) }
            lightningStrikesEnabled = false
            scrollSpeed = 0f
            swipeScrollEnabled = true
        }
        val noon = SunPositionCalculator.compute(hour24 = 12f)
        // X = U + (f + c) T, U = -s W p, T = 2W (BuildingRoofSnowTest): one tile of the street is 2 / s of the offset.
        val tile = 2f / PaperRenderer.HILL_PARALLAX
        var time = SceneTime(120.0)
        fun sweep(fromTile: Int) {
            for (i in 0 until SWEEP_FRAMES) {
                renderer.homeScreenOffset = (fromTile + 3f * i / SWEEP_FRAMES) * tile
                time += FRAME
                renderer.draw(NothingCanvas, noon, time, FRAME)
            }
        }
        repeat(WARM_UP_FRAMES) { time += FRAME; renderer.draw(NothingCanvas, noon, time, FRAME) }
        sweep(0)
        val objects = allocationsDuring { sweep(3) }
        Log.i(TAG, "places coming back: $objects objects over $SWEEP_FRAMES frames, three tiles of the street")
        assertEquals("a frame as a place came back as another copy allocated", 0, objects)
    }

    private companion object {
        const val TAG = "FrameAllocationTest"
        const val WIDTH = 720
        const val HEIGHT = 1440
        const val FRAME = 1f / 30f

        /** Past the traffic's arrival (`SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES`, 390) and every lazy first use. */
        const val WARM_UP_FRAMES = 420
        const val COUNTED_FRAMES = 150

        /** Forty seconds: the slowest walk out at a closing (28.5 s) and the glass's two seconds after it, with room. */
        const val SHUT_FRAMES = 1200

        /** Three tiles of the street at a fiftieth of a tile a frame. */
        const val SWEEP_FRAMES = 150
    }
}
