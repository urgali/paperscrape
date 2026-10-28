package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Debug
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.paperscrape.livewallpaper.weather.LiveWeatherSnapshot
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * **A steady frame of the wallpaper allocates nothing**: nothing new is allocated per frame on a draw path.
 *
 * Every built-in theme by day and by night, the lake at its busiest, rain, snow, a storm driven by Live Weather, the
 * sleigh and the fireworks, the distant houses on the mountains by day, at night and under snow, the Christmas
 * decorations of the buildings and the fir A by day and at night with the distant houses' bulbs, a shut building's
 * blinds and shutter by day, Halloween with everything it draws, and a scene with everything on at once -- the houses
 * at 100 %, opening hours (the lamps and the glass of the shut shops), the lake at its busiest, the decorations and the
 * weather. Each is warmed up past the moment the traffic arrives and then drawn for [COUNTED_FRAMES] frames while ART
 * counts what this thread allocates. A frame that allocates feeds the collector at the frame rate, and its pauses show
 * as dropped frames.
 *
 * The frames are drawn by the real [PaperRenderer] into a [SceneCanvas] that does nothing, so the
 * count is the scene's own; the scene scrolls, as the wallpaper does. **And on the two targets that
 * draw** ([aSteadyFrameOnTheGpuAllocatesNothing], [aSteadyFrameOnTheCanvasAllocatesNothing]): a few
 * of the same scenes drawn into a [GlSceneTarget] on a pbuffer of the device's GPU and into a
 * [CanvasSceneTarget] on a bitmap, where everything the scene hands a backend -- the sprites looked up
 * in its cache, the strokes, the fills -- is counted with the scene. A frame that allocates only
 * there would pass a count on a canvas that does nothing. Each writes its counts to
 * `golden-output/frame_allocation_gpu.txt` and `frame_allocation_canvas.txt` ([GlGolden.outputDir])
 * before it asserts.
 *
 * *# of Stars* at 0 is a scene of its own (`autumn-midnight-no-stars`): the empty star field is the
 * field asked for, not one to build again at every frame.
 *
 * **Events are not frames.** A firework burst, a gift thrown from the sleigh and a sleigh setting
 * off are an object each when they happen -- every few seconds, not every frame -- so the seven
 * scenes that have them (Christmas and New Year's Eve at night, Christmas at noon and at 23:00 with
 * the distant houses -- the sleigh flies by day too -- everything on at once at Christmas, and
 * Christmas at noon and at night with the sleigh held in flight in every counted frame) are
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
        /** From 0 to 1, the sleigh held in flight at that point of its crossing in every frame; negative, as it flies. */
        val sleighHeld: Float = -1f,
        /**
         * The Headless Horseman set off at the start of the count, at the part of his crossing pumpkins are thrown in:
         * his pumpkins on fire come from a fixed pool, so even the frames that throw one allocate nothing.
         */
        val horsemanFlying: Boolean = false,
        /** Where the scene's clock starts: 52 s puts the Halloween moon's laugh, at 66 s, in the counted frames. */
        val startSeconds: Double = 0.0,
        val customise: (SceneCustomization) -> SceneCustomization = { it },
    )

    /**
     * Draws nothing, allocates nothing: the renderer's own allocations are what is counted. It counts the
     * sprites cut to a box -- people walking out of a window or into one -- so a scene that is
     * meant to have somebody walking can be seen to have had them.
     */
    private object NothingCanvas : SceneCanvas {
        var clipped = 0
        /** The shapes drawn: a shut building's shutter is drawn in shapes ([DoorShutter]'s bands). */
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

    /** *# of Stars* at 0: no star in the sky. */
    private val noStars: (SceneCustomization) -> SceneCustomization = {
        it.copy(stars = it.stars.copy(density = 0f))
    }

    /** The distant houses on, at 100 %: every place of every mountain stands a house. */
    private val distantHouses: (SceneCustomization) -> SceneCustomization = {
        it.copy(distantHouses = DistantHousesConfig(visible = true, density = 1f))
    }

    /** The three groups' hours 13:00-20:00: at noon every building that keeps hours is shut (the fire and the police station never close), its blinds and shutter down. */
    private val shutAtNoon: (SceneCustomization) -> SceneCustomization = {
        it.copy(
            shopHoursEnabled = true, shopOpenHour = 13f, shopCloseHour = 20f,
            publicHoursEnabled = true, publicOpenHour = 13f, publicCloseHour = 20f,
            towerHoursEnabled = true, towerOpenHour = 13f, towerCloseHour = 20f,
        )
    }

    /** Everything on at once: the distant houses, opening hours, the lake, decorations, rain. */
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
        // *# of Stars* at 0: the empty field is the field asked for, not one to build again at every frame.
        Scene("autumn-midnight-no-stars", "autumn", 0f, customise = noStars),
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
        // The Christmas decorations -- the fir A, the roofs' strings, the wreaths, the towers' stars -- by day and
        // on Autumn with the switches on at night; the distant houses' bulbs; a shut building's blinds and shutter by day.
        Scene("christmas-noon-decorations", "christmas", 12f, events = true),
        Scene("autumn-christmas-lights-night", "autumn", 1f, customise = { it.copy(christmasBuildingLightsEnabled = true, christmasTreeLightsEnabled = true, christmasFirsEnabled = true) }),
        Scene("christmas-distant-houses-night", "christmas", 23f, events = true, customise = distantHouses),
        Scene("shut-by-day", "autumn", 12f, customise = shutAtNoon),
        // The sleigh held in flight in every counted frame, by day and at night, so its drawing is in the count: a
        // flight of 9 to 13 s every 34 to 73 s can otherwise miss the 150 frames, and `Paint.alpha =` in its
        // drawing would allocate on Android 10.
        Scene("christmas-sleigh-noon", "christmas", 12f, events = true, sleighHeld = 0.5f),
        Scene("christmas-sleigh-night", "christmas", 1f, events = true, sleighHeld = 0.5f),
        // Halloween -- the cobwebs (one a building, tinted by its walls) and the jack-o'-lanterns, the laughing moon
        // laughing in the counted frames, the Horseman in flight throwing his pumpkins on fire from his hand, his arm's
        // gesture and the pumpkin growing in his hand included --, under the blood rain at night with the scarecrows at
        // 100 %, every snowman's place the frame reaches holding one, as in the golden `halloween-scarecrows-and-snowmen`,
        // and at noon with the night by day in force (the theme ships it on); both with the still moon as the theme ships
        // it. And the still moon without the night by day at dusk, coming out where it stands, a little transparent.
        Scene("halloween-everything-night", "halloween", 22f, horsemanFlying = true, startSeconds = 52.0, customise = {
            rain(it).copy(scarecrows = it.scarecrows.copy(visible = true, density = 1f))
        }),
        Scene("halloween-night-by-day-noon", "halloween", 12f, horsemanFlying = true, startSeconds = 52.0, customise = rain),
        Scene("halloween-still-moon-dusk", "halloween", 20.3f, horsemanFlying = true, customise = { it.copy(nightSkyByDayEnabled = false) }),
        // The cobwebs turning from their day colour to their night one, 24 minutes before the sunset,
        // the houses' first colour across the band (`HalloweenDecorations.webTintAt`).
        Scene("halloween-cobwebs-turning", "halloween", 19.6f, customise = { it.copy(nightSkyByDayEnabled = false) }),
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
            renderer.holdSantaSleighForTest(scene.sleighHeld)
            val dayPhase = SunPositionCalculator.compute(hour24 = scene.hour)
            var time = SceneTime(scene.startSeconds)
            repeat(WARM_UP_FRAMES) {
                time += FRAME
                renderer.draw(NothingCanvas, dayPhase, time, FRAME)
            }
            if (scene.horsemanFlying) renderer.startHeadlessHorsemanFlightForTest()
            val dropsBefore = renderer.horsemanPumpkinsFallingForTest()
            var dropsSeen = 0
            val objects = allocationsDuring {
                repeat(COUNTED_FRAMES) {
                    time += FRAME
                    renderer.draw(NothingCanvas, dayPhase, time, FRAME)
                    // The most pumpkins in the air in any counted frame: the read allocates nothing (a count over the pool).
                    // At the last frame alone the pool can be empty -- one lands up to 0.3 s before the next sets off.
                    if (scene.horsemanFlying) dropsSeen = maxOf(dropsSeen, renderer.horsemanPumpkinsFallingForTest())
                }
            }
            if (scene.horsemanFlying) {
                assertTrue("${scene.name}: no pumpkin fell from the Horseman ($dropsBefore before, at most $dropsSeen in a frame)", dropsSeen > 0 || dropsBefore > 0)
            }
            val line = "${scene.name}: $objects objects over $COUNTED_FRAMES frames" +
                if (scene.horsemanFlying) ", the Horseman's pumpkins in the air at most: $dropsSeen" else ""
            report.appendLine(line)
            Log.i(TAG, line)
            val allowed = if (scene.events) COUNTED_FRAMES / 10 else 0
            if (objects > allowed) failures += "$line (allowed $allowed)"
        }
        assertTrue("a steady frame allocated:\n${failures.joinToString("\n")}\n\nall scenes:\n$report", failures.isEmpty())
    }

    /**
     * The scenes counted on the two targets that draw: Autumn by day and at night as it ships, and at night with no star;
     * the rain, in lines; Christmas at night, its decorations and the sleigh's events.
     */
    private val targetScenes = listOf("autumn-noon", "autumn-midnight", "autumn-midnight-no-stars", "spring-rain", "christmas-night")
        .map { name -> scenes.single { it.name == name } }

    /**
     * **A steady frame drawn on the GPU allocates nothing**: the scenes of [targetScenes] drawn into a [GlSceneTarget] on a
     * pbuffer of the device's own GPU ([GlGolden.countPrimitive]'s context, at [GlGolden.WIDTH] x [GlGolden.HEIGHT]), each
     * frame between `beginFrame` and `endFrame` as the render thread draws it -- so the batch, the atlas's lookups and the
     * flush are counted with the scene. The counts go to `golden-output/frame_allocation_gpu.txt` before the assertion.
     */
    @Test
    fun aSteadyFrameOnTheGpuAllocatesNothing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = StringBuilder()
        val failures = ArrayList<String>()
        GlGolden.countPrimitive { canvas ->
            val gl = canvas as GlSceneTarget
            for (scene in targetScenes) {
                val dayPhase = SunPositionCalculator.compute(hour24 = scene.hour)
                // The same frames twice, each by a renderer of its own: the first loads every drawing they show into the
                // atlas once, as a wallpaper does the first time a drawing comes on screen; the second's are counted.
                var objects = 0L
                for (pass in 0..1) {
                    val renderer = configured(PaperRenderer(GlGolden.WIDTH, GlGolden.HEIGHT, context), scene)
                    var time = SceneTime(scene.startSeconds)
                    repeat(WARM_UP_FRAMES) {
                        time += FRAME
                        gl.beginFrame()
                        renderer.draw(gl, dayPhase, time, FRAME)
                        gl.endFrame()
                    }
                    val counted = allocationsDuring {
                        repeat(COUNTED_FRAMES) {
                            time += FRAME
                            gl.beginFrame()
                            renderer.draw(gl, dayPhase, time, FRAME)
                            gl.endFrame()
                        }
                    }
                    if (pass == 1) objects = counted.toLong()
                }
                val line = "${scene.name}: $objects objects over $COUNTED_FRAMES frames on the GPU target, ${gl.drawCalls} draws in the last"
                report.appendLine(line)
                Log.i(TAG, line)
                val allowed = if (scene.events) COUNTED_FRAMES / 10 else 0
                if (objects > allowed) failures += "$line (allowed $allowed)"
            }
        }
        File(GlGolden.outputDir(), "frame_allocation_gpu.txt").writeText(report.toString())
        assertTrue("a steady frame on the GPU allocated:\n${failures.joinToString("\n")}\n\nall scenes:\n$report", failures.isEmpty())
    }

    /**
     * **A steady frame drawn on a Canvas allocates nothing**: the scenes of [targetScenes] drawn into a [CanvasSceneTarget]
     * bound to a bitmap of the phone's size -- the fallback when the GPU is given up on, and the settings' previews. The
     * counts go to `golden-output/frame_allocation_canvas.txt` before the assertion, so the number exists whatever it is.
     */
    @Test
    fun aSteadyFrameOnTheCanvasAllocatesNothing() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val report = StringBuilder()
        val failures = ArrayList<String>()
        val bitmap = Bitmap.createBitmap(WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
        val target = CanvasSceneTarget()
        target.bind(Canvas(bitmap))
        try {
            for (scene in targetScenes) {
                val dayPhase = SunPositionCalculator.compute(hour24 = scene.hour)
                // The same frames twice, each by a renderer of its own: the first decodes every drawing they show into the
                // sprite cache once, as a wallpaper does the first time a drawing comes on screen; the second's are counted.
                var objects = 0L
                for (pass in 0..1) {
                    val renderer = configured(PaperRenderer(WIDTH, HEIGHT, context), scene)
                    var time = SceneTime(scene.startSeconds)
                    repeat(WARM_UP_FRAMES) {
                        time += FRAME
                        renderer.draw(target, dayPhase, time, FRAME)
                    }
                    val counted = allocationsDuring {
                        repeat(COUNTED_FRAMES) {
                            time += FRAME
                            renderer.draw(target, dayPhase, time, FRAME)
                        }
                    }
                    if (pass == 1) objects = counted.toLong()
                }
                val line = "${scene.name}: $objects objects over $COUNTED_FRAMES frames on the Canvas target"
                report.appendLine(line)
                Log.i(TAG, line)
                val allowed = if (scene.events) COUNTED_FRAMES / 10 else 0
                if (objects > allowed) failures += "$line (allowed $allowed)"
            }
        } finally {
            target.unbind()
            bitmap.recycle()
        }
        File(GlGolden.outputDir(), "frame_allocation_canvas.txt").writeText(report.toString())
        assertTrue("a steady frame on the Canvas allocated:\n${failures.joinToString("\n")}\n\nall scenes:\n$report", failures.isEmpty())
    }

    /** A renderer set up as [scene] asks, as [aSteadyFrameAllocatesNothing] sets up its own. */
    private fun configured(renderer: PaperRenderer, scene: Scene): PaperRenderer {
        renderer.theme = ThemeCatalog.byId(scene.themeId)
        renderer.sceneCustomization = scene.customise(defaultCustomizationFor(scene.themeId))
        renderer.liveWeatherOverride = scene.weather
        renderer.lightningStrikesEnabled = false
        renderer.scrollSpeed = 0.15f
        renderer.holdSantaSleighForTest(scene.sleighHeld)
        return renderer
    }

    /**
     * **And while somebody walks**: a person walking out of a window or into one is drawn cut
     * to the pane, at a position worked out per frame ([WindowWalk]) -- and that allocates nothing
     * either. Two kinds of walk, each seen to happen ([NothingCanvas.clipped] counts the cut blits):
     *
     *  - **the roster's**: Autumn at night, started where a person is walking from window to window --
     *    found by drawing frames a second apart until one has a cut blit;
     *  - **the hours'**: the shops' and the towers' hours on, and the hour moved on a minute at a time
     *    across a closing -- as the real clock moves it, if faster -- so the doorways walk their people out;
     *    then on past it until the glass is dark and the blinds and the shutters are down; swept once
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
        // **Swept once uncounted, on a renderer of its own, before the count.** The minutes move
        // the people's colour clock, and a pedestrian's colours re-dealt across them run code that no frame
        // of the warm-up at one minute runs -- `PeopleColours.keepingSpread`, whose first run in the process
        // resolves the string constant of a parameter's null check: one object for the life of the process,
        // no frame's, counted only when this test runs alone. The count is the second sweep's, on a fresh
        // renderer: every frame's, and only those.
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

        // And on past the closing: at 20:10, as long as the slowest walk out takes and the glass's two seconds
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

        // And the opening: 08:50 to 09:30, the glass lighting at 09:00 over its two seconds and the shutters
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
     * **And while the places among the houses come and go**: a place that leaves the screen comes
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

        /** Forty seconds: the slowest walk out at a closing and the glass's two seconds after it, with room. */
        const val SHUT_FRAMES = 1200

        /** Three tiles of the street at a fiftieth of a tile a frame. */
        const val SWEEP_FRAMES = 150
    }
}
