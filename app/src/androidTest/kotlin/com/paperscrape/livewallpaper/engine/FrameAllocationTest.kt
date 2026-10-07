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
 * busiest, the decorations and the weather.
 *
 * The frames are drawn by the real [PaperRenderer] into a [SceneCanvas] that does nothing, so the
 * count is the scene's own: the two backends allocate nothing per frame of their own (the GL one
 * by construction, its buffers and stacks built once; the allocation tracker on the live wallpaper
 * shows no site in either). The scene scrolls, as the wallpaper does.
 *
 * **Events are not frames.** A firework burst, a gift thrown from the sleigh and a sleigh setting
 * off are an object each when they happen -- every few seconds, not every frame -- so the two
 * scenes that have them are held to "fewer objects than a tenth of the frames" and log their exact
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

    /** Draws nothing, allocates nothing: the renderer's own allocations are what is counted. */
    private object NothingCanvas : SceneCanvas {
        override fun save() = Unit
        override fun restore() = Unit
        override fun translate(dx: Float, dy: Float) = Unit
        override fun scale(sx: Float, sy: Float) = Unit
        override fun rotate(degrees: Float) = Unit
        override fun drawRect(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawLine(startX: Float, startY: Float, stopX: Float, stopY: Float, paint: Paint) = Unit
        override fun drawCircle(cx: Float, cy: Float, radius: Float, paint: Paint) = Unit
        override fun drawOval(left: Float, top: Float, right: Float, bottom: Float, paint: Paint) = Unit
        override fun drawWedge(cx: Float, cy: Float, radius: Float, startAngle: Float, sweepAngle: Float, paint: Paint) = Unit
        override fun drawShape(shape: SceneShape, paint: Paint) = Unit
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

    /** Everything on at once (v5.11): the distant houses, opening hours, the lake, decorations, rain. */
    private val everything: (SceneCustomization) -> SceneCustomization = { c ->
        val full = { o: ObjectVariantConfig -> o.copy(visible = true, density = 1f) }
        busyLake(rain(distantHouses(c))).copy(
            businessHoursEnabled = true,
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

    private companion object {
        const val TAG = "FrameAllocationTest"
        const val WIDTH = 720
        const val HEIGHT = 1440
        const val FRAME = 1f / 30f

        /** Past the traffic's arrival (`SharedGoldenScenes.TRAFFIC_WARM_UP_FRAMES`, 390) and every lazy first use. */
        const val WARM_UP_FRAMES = 420
        const val COUNTED_FRAMES = 150
    }
}
