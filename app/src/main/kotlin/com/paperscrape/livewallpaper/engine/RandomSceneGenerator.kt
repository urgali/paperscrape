package com.paperscrape.livewallpaper.engine

import android.graphics.Color
import kotlin.random.Random

/**
 * Generates brand-new, never-preset color themes and object layouts on demand — this is what
 * powers the "Randomize" button in settings, mirroring the classic live-wallpaper feature of
 * producing an unlimited number of fresh combinations instead of just cycling fixed presets.
 *
 * Design: a randomized theme's [SceneTheme.id] is the string `"random:<seed>"`. Everything is
 * regenerated deterministically from that seed, so:
 *  - the same generated theme reliably looks the same across app restarts (it's just persisted
 *    as a normal themeId string in [com.paperscrape.livewallpaper.prefs.WallpaperPrefs])
 *  - [ThemeCatalog.byId] and [SceneObjectCatalog.layoutFor] only need one extra branch each to
 *    support it — no separate storage/model needed for "the current random theme".
 */
object RandomSceneGenerator {

    private const val PREFIX = "random:"

    fun isRandomThemeId(id: String): Boolean = id.startsWith(PREFIX)

    /** Call this from the "Randomize" button — produces a fresh, never-seen-before theme id. */
    fun newThemeId(): String = PREFIX + Random.nextInt(0, Int.MAX_VALUE)

    private fun seedOf(themeId: String): Int = themeId.removePrefix(PREFIX).toIntOrNull() ?: 0

    private fun hsv(h: Float, s: Float, v: Float, alpha: Int = 0xFF): Int =
        Color.HSVToColor(alpha, floatArrayOf(((h % 360f) + 360f) % 360f, s.coerceIn(0f, 1f), v.coerceIn(0f, 1f)))

    fun generateTheme(themeId: String): SceneTheme {
        val rnd = Random(seedOf(themeId))

        // Pick a base sky hue, then derive a hill hue that's harmonious (analogous or
        // complementary) rather than a totally unrelated random hue — keeps results looking
        // "designed" instead of muddy.
        val skyHue = rnd.nextFloat() * 360f
        val hillHueOffset = listOf(-40f, -25f, 25f, 40f, 150f, 180f).random(rnd)
        val hillHue = skyHue + hillHueOffset

        val skyDay = intArrayOf(hsv(skyHue, 0.45f, 0.95f), hsv(skyHue, 0.15f, 1f))
        val skyDawn = intArrayOf(hsv(skyHue + 20f, 0.55f, 0.95f), hsv(skyHue + 40f, 0.35f, 0.98f))
        val skyDusk = intArrayOf(hsv(skyHue - 20f, 0.6f, 0.85f), hsv(skyHue + 10f, 0.4f, 0.95f))
        val skyNight = intArrayOf(hsv(skyHue, 0.55f, 0.14f), hsv(skyHue, 0.45f, 0.22f))

        val hillColorsDay = intArrayOf(
            hsv(hillHue, 0.5f, 0.86f),
            hsv(hillHue, 0.58f, 0.68f),
            hsv(hillHue, 0.65f, 0.5f),
        )
        val hillColorsNight = intArrayOf(
            hsv(hillHue, 0.4f, 0.28f),
            hsv(hillHue, 0.4f, 0.2f),
            hsv(hillHue, 0.4f, 0.13f),
        )

        val accentColor = hsv(hillHue + 180f, 0.7f, 0.95f)

        return SceneTheme(
            id = themeId,
            displayName = "Random",
            skyNight = skyNight,
            skyDawn = skyDawn,
            skyDay = skyDay,
            skyDusk = skyDusk,
            hillColorsDay = hillColorsDay,
            hillColorsNight = hillColorsNight,
            sunColor = hsv(48f, 0.25f, 1f),
            moonColor = hsv(230f, 0.15f, 1f),
            starColor = 0xFFFFFFFF.toInt(),
            accentColor = accentColor,
            hasFireworks = rnd.nextFloat() < 0.2f,
        )
    }

    private val OBJECT_POOL = listOf(
        // Dogs have been fully removed from the codebase (not just excluded here) -- they'll be
        // reimplemented from scratch later.
        SceneObjectType.HOUSE, SceneObjectType.TREE,
        SceneObjectType.SNOWMAN, SceneObjectType.GIFT, SceneObjectType.PALM_TREE, SceneObjectType.PARASOL,
        SceneObjectType.SKYSCRAPER, SceneObjectType.PENGUIN,
        SceneObjectType.BUNNY, SceneObjectType.EASTER_EGG,
    )

    fun generateLayout(themeId: String, accentColor: Int): SceneObjectLayout {
        // XOR a constant so the layout's random sequence diverges from the theme's, even though
        // both start from the same seed — avoids the two generators "rhyming" (e.g. always
        // picking the first object when the theme also picked its first hue option).
        val rnd = Random(seedOf(themeId) xor 0x5EED)

        val objectCount = rnd.nextInt(3, 7)
        val staticObjects = (0 until objectCount).map {
            StaticSceneObject(
                type = OBJECT_POOL.random(rnd),
                // The full 0..1 range. It used to stop at 0.375 because anything lower was
                // painted over by the road; SceneSpace places the object band above the road by
                // construction, so the cap has nothing left to protect against.
                depthFraction = rnd.nextFloat(),
                tileFractionX = rnd.nextFloat(),
                // A relative size variation around 1, not a size -- see StaticSceneObject.scale.
                // The chaos themes get a wider spread than the built-in ones on purpose.
                scale = 0.82f + rnd.nextFloat() * 0.36f,
            )
        }

        val carCount = rnd.nextInt(0, 3)
        val cars = (0 until carCount).map { index ->
            // Lane, speed and direction are the canonical ones, not rolls of their own. A random
            // lane y put chaos-theme cars on a band of their own that the road was then stretched
            // to cover, and a random speed inside a lane is what makes a queue collapse into a
            // pack. What stays random here is what the chaos themes are actually for: how many
            // cars there are, what colour they are and what type.
            val nearLane = index % 2 == 0
            CarObject(
                laneYFraction = if (nearLane) SceneSpace.ROAD_LANE_NEAR_Y_FRACTION else SceneSpace.ROAD_LANE_FAR_Y_FRACTION,
                speedFraction = if (nearLane) SceneSpace.CAR_SPEED_NEAR else SceneSpace.CAR_SPEED_FAR,
                startDelaySeconds = SceneObjectCatalog.CAR_LOOP_ENTRY_PROGRESS +
                    SceneObjectCatalog.CAR_LOOP_SPAN * (index / 2) / SceneObjectCatalog.CAR_SLOTS_PER_LANE,
                color = if (index == 0) accentColor else hsv(rnd.nextFloat() * 360f, 0.55f, 0.85f),
                reverse = !nearLane,
                type = if (rnd.nextFloat() < 0.75f) CarType.PLAIN else CarType.entries.random(rnd),
            )
        }

        return SceneObjectLayout(staticObjects = staticObjects, cars = cars)
    }
}
