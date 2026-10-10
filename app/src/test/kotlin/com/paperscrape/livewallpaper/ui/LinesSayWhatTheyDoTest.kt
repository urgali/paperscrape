package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.BirdColorWeight
import com.paperscrape.livewallpaper.engine.BirdsConfig
import com.paperscrape.livewallpaper.engine.CarSelection
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectRenderer
import com.paperscrape.livewallpaper.engine.SceneObjectType
import com.paperscrape.livewallpaper.engine.SceneSpace
import com.paperscrape.livewallpaper.engine.SwipeReport
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.engine.keepCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * **Every line says what its control does** (v5.10E, inventory I-216…I-219, I-222, I-224…I-226: row 11
 * of the v5.10A table, the maintainer's *sì* of 2026-09-30 -- *«dopo ogni riga dice quello che fa»*).
 *
 * Each line is held to the rule it describes, not to itself: the buildings' slider to
 * [keepCandidate], the cars' bottom to [CarSelection.countFor] and their switch to
 * [SceneObjectRenderer.drawsRoad], the birds' shares to [BirdsConfig.pickColor], the Christmas lights to
 * [SceneObjectRenderer.standsAsFir], *Swipe scroll* to what the wallpaper records ([SwipeReport]), and
 * the sliders that lock to the switch above them.
 */
class LinesSayWhatTheyDoTest {

    // ---------------------------------------------------------------- the buildings (I-216)

    @Test
    fun `the buildings' slider thins the towers and the shops stay, as its words say`() {
        val c = defaultCustomizationFor("city").let { it.copy(buildings = it.buildings.copy(density = 0f)) }
        val layout = SceneObjectCatalog.layoutFor("city", ThemeCatalog.byId("city").accentColor)
        val buildings = layout.staticObjects.filter { it.type == SceneObjectType.SKYSCRAPER }
        val (shops, towers) = buildings.partition { it.depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH }
        assertTrue(shops.isNotEmpty() && towers.isNotEmpty())
        assertTrue("at 0 % every shop stands", shops.all { c.keepCandidate(it, layout.densityScheme) })
        assertTrue("and no tower", towers.none { c.keepCandidate(it, layout.densityScheme) })
        // So the slider is called what it moves, and the line says what stays.
        val world = source("ui/WorldSceneScreen.kt")
        assertTrue(world.contains("""densityLabel = "Towers","""))
        assertTrue(BUILDINGS_DENSITY_LINE.contains("towers") && BUILDINGS_DENSITY_LINE.contains("shops") && BUILDINGS_DENSITY_LINE.contains("always stay"))
        // Off is off: with Show Buildings off nothing stands, which is why the line names the switch.
        val off = c.copy(buildings = c.buildings.copy(visible = false))
        assertTrue(shops.none { off.keepCandidate(it, layout.densityScheme) })
        assertTrue(BUILDINGS_DENSITY_LINE.contains("while Show Buildings is on"))
    }

    // ---------------------------------------------------------------- the cars (I-217)

    @Test
    fun `the cars' bottom says one car, because one car drives`() {
        assertEquals("the rule: one car at 0 %", 1, CarSelection.countFor(0f, 10))
        assertEquals("0% - one car", carDensityValue(0f))
        assertEquals("1%", carDensityValue(0.01f))
        assertEquals("100%", carDensityValue(1f))
    }

    @Test
    fun `Show Cars off takes the road away, and its line says so`() {
        val layout = SceneObjectCatalog.layoutFor("city", ThemeCatalog.byId("city").accentColor)
        val on = defaultCustomizationFor("city")
        val off = on.copy(cars = on.cars.copy(visible = false))
        assertTrue(SceneObjectRenderer.drawsRoad(layout, on))
        assertFalse("the rule: no road with the cars off", SceneObjectRenderer.drawsRoad(layout, off))
        val world = source("ui/WorldSceneScreen.kt")
        assertTrue(world.contains("""switchSubtitle = "Off takes the cars and the road away","""))
        assertFalse("the line that called it an empty road", world.contains("an empty road is \" +"))
        assertTrue(world.contains("densityValue = ::carDensityValue,"))
    }

    // ---------------------------------------------------------------- the birds (I-218)

    @Test
    fun `each bird colour shows its share of the flock, and the shares add up to 100`() {
        assertEquals(listOf(25, 25, 25, 25), SettingsUiModel.birdColorShares(listOf(1f, 1f, 1f, 1f)))
        assertEquals("one at the top, three at the bottom: all of them", listOf(100, 0, 0, 0), SettingsUiModel.birdColorShares(listOf(1f, 0f, 0f, 0f)))
        assertEquals("all at the bottom: pickColor's first colour", listOf(100, 0, 0, 0), SettingsUiModel.birdColorShares(listOf(0f, 0f, 0f, 0f)))
        assertEquals(listOf(40, 20, 20, 20), SettingsUiModel.birdColorShares(listOf(0.4f, 0.2f, 0.2f, 0.2f)))
        for (weights in listOf(listOf(0.3f, 0.3f, 0.3f, 0f), listOf(0.1f, 0.7f, 0.33f, 0.05f), listOf(1f, 1f, 1f, 0.5f))) {
            assertEquals("$weights", 100, SettingsUiModel.birdColorShares(weights).sum())
        }
    }

    @Test
    fun `the shares are what pickColor draws`() {
        val colours = listOf(0xFF000001.toInt(), 0xFF000002.toInt(), 0xFF000003.toInt(), 0xFF000004.toInt())
        for (weights in listOf(listOf(0.4f, 0.2f, 0.3f, 0.1f), listOf(1f, 0f, 0.5f, 0.5f), listOf(0f, 0f, 0f, 0f))) {
            val birds = BirdsConfig(true, 0.5f, false, colours.zip(weights) { c, w -> BirdColorWeight(c, w) })
            val n = 100_000
            val drawn = IntArray(4)
            for (i in 0 until n) drawn[colours.indexOf(birds.pickColor((i + 0.5f) / n))]++
            val shares = SettingsUiModel.birdColorShares(weights)
            for (k in 0 until 4) {
                assertEquals("$weights colour ${k + 1}", shares[k].toDouble(), drawn[k] * 100.0 / n, 1.0)
            }
        }
        // And the screen prints the share, not the weight.
        val world = source("ui/WorldSceneScreen.kt")
        assertTrue(world.contains("""Text("Color ${'$'}{index + 1}: ${'$'}share% of the birds""""))
        assertFalse(world.contains("""Text("Color ${'$'}{index + 1}: ${'$'}{(shown * 100).toInt()}""""))
    }

    // ---------------------------------------------------------------- the banner (I-219)

    @Test
    fun `the banner says the changes stay with the theme, on both screens`() {
        val banner = SettingsUiModel.themeEditsBanner("Autumn")
        assertTrue(banner.contains("stay with it when you switch themes"))
        assertFalse("it read as: unsaved changes are lost", banner.contains("Keep this look by saving"))
        assertTrue(source("ui/WorldSceneScreen.kt").contains("SettingsBanner(SettingsUiModel.themeEditsBanner(themeName))"))
        assertTrue(source("ui/SeasonsScreen.kt").contains("SettingsBanner(SettingsUiModel.themeEditsBanner(themeName))"))
    }

    // ---------------------------------------------------------------- Swipe scroll (I-222)

    @Test
    fun `Swipe scroll is on only once the home screen has moved the wallpaper with a swipe`() {
        val never = SettingsUiModel.swipeScroll(stored = true, swipeReported = false)
        assertFalse("on over a home screen that never reports a swipe", never.shownOn)
        assertFalse(never.interactive)
        assertTrue(SettingsUiModel.swipeScroll(stored = true, swipeReported = true).shownOn)
        assertFalse("the user's own off stays off", SettingsUiModel.swipeScroll(stored = false, swipeReported = true).shownOn)
    }

    @Test
    fun `a swipe is the offset moving between pages, not the first report`() {
        val r = SwipeReport()
        val step = 0.25f // five pages
        assertFalse("the first offset is where the home screen is, not a swipe", r.moved(0.5f, step))
        assertFalse("the same again", r.moved(0.5f, step))
        assertFalse("a float's rounding", r.moved(0.5f + SwipeReport.MIN_MOVE / 2, step))
        assertFalse(r.moved(Float.NaN, step))
        assertTrue("a page along", r.moved(0.75f, step))
        assertTrue("and back", r.moved(0.25f, step))
    }

    /**
     * **No pages, no swipe** (the read-only review of v5.10E, D5): an offset moved by Android's own default
     * (a step of -1: the home screen never said it has pages) or by a relayout of one page is not a swipe.
     */
    @Test
    fun `an offset that moves without pages to scroll through is not a swipe`() {
        val r = SwipeReport()
        for (step in listOf(-1f, 0f, Float.NaN)) {
            assertFalse(r.moved(0f, step))
            assertFalse("moved with step $step", r.moved(0.5f, step))
        }
        // The first offset with pages is the reference, not the ones before it.
        assertFalse(r.moved(0.5f, 1f))
        assertTrue(r.moved(0f, 1f))
    }

    @Test
    fun `the wallpaper records a swipe once, never from the picker's preview, and a backup does not carry it`() {
        val engine = source("engine/PaperWallpaperService.kt")
        assertTrue(engine.contains("if (!isPreview && swipeReport.moved(xOffset, xOffsetStep) && !settings.swipeReported &&"))
        // Written by this engine once, and again only if that write failed (the review's D4).
        assertTrue(engine.contains("runCatching { prefs.setSwipeReported() }.onFailure { swipeReportSent = false }"))
        assertEquals("written in one place", 1, Regex("""setSwipeReported\(""").findAll(engine).count())
        val backup = source("prefs/AppBackup.kt")
        assertFalse("runtime state of this phone, like the GPS fix", backup.contains("swipeReported"))
        val prefs = source("prefs/WallpaperPrefs.kt")
        assertEquals("read, and written by its one setter", 2, Regex("""Keys\.SWIPE_REPORTED""").findAll(prefs).count())
    }

    // ---------------------------------------------------------------- Christmas lights (I-224)

    @Test
    fun `Christmas lights says it makes firs and lights the sills, as the drawing does`() {
        val c = defaultCustomizationFor("autumn")
        val layout = SceneObjectCatalog.layoutFor("autumn", ThemeCatalog.byId("autumn").accentColor)
        val trees = layout.staticObjects.filter { it.type == SceneObjectType.TREE }
        val lit = c.copy(christmasDecorationsEnabled = true)
        assertTrue("with the lights on, some trees stand as firs", trees.any { SceneObjectRenderer.standsAsFir(it, lit) })
        assertTrue("and none without", trees.none { SceneObjectRenderer.standsAsFir(it, c.copy(christmasDecorationsEnabled = false)) })
        for (word in listOf("lights on the trees", "windowsills", "one tree in three becomes a Christmas fir")) {
            assertTrue("the line does not say '$word'", CHRISTMAS_LIGHTS_LINE.contains(word))
        }
        assertTrue(source("ui/SeasonsScreen.kt").contains("supporting = CHRISTMAS_LIGHTS_LINE,"))
    }

    // ---------------------------------------------------------------- Weather effects, the release page (I-225)

    @Test
    fun `Weather effects says where it goes, and the release page button is called what it opens`() {
        val weather = source("ui/WeatherTimeScreen.kt")
        val row = weather.substringAfter("title = \"Weather effects\",").substringBefore("onClick =")
        assertTrue("the row names the screen it opens", row.contains("Opens World & scene"))
        val home = source("ui/SettingsScreen.kt")
        assertFalse(home.contains("Text(\"Check project page\")"))
        val button = home.substringBefore("{ Text(\"Open release page\") }").takeLast(200)
        assertTrue("and it opens this release's page", button.contains("update.releasePageUrl"))
        // The row's own navigation: World & scene, opened from here.
        assertTrue(home.contains("worldOpenedFrom = SettingsDestination.WEATHER\n                destination = SettingsDestination.WORLD"))
    }

    // ---------------------------------------------------------------- the dry thunderstorm (I-292)

    /**
     * **Kept, and the comments say so** (the maintainer, 2026-10-04: *«nel mondo vero può esistere un
     * temporale senza pioggia, quindi va bene»*). The theme's storm flashes with Rain chosen whatever the
     * intensity -- `stormActive` takes none -- and two comments said it needed rain falling.
     */
    @Test
    fun `the thunderstorm without rain is meant, and the comments no longer say otherwise`() {
        assertTrue(
            com.paperscrape.livewallpaper.engine.LiveWeatherSceneRules.stormActive(
                liveIsThunderstorm = null, themePrecipitationVisible = true, themePrecipitationIsRain = true, themeThunderstorm = true,
            ),
        )
        val rules = source("engine/LiveWeatherSceneRules.kt")
        assertTrue(rules.contains("a thunderstorm without rain, and that"))
        assertFalse(rules.contains("has always been gated on rain actually falling"))
        val mapper = source("weather/WeatherSnapshotMapper.kt")
        assertFalse(mapper.contains("own storm toggle has always\n            // required rain to be falling"))
        assertTrue(mapper.contains("a dry thunderstorm the maintainer kept"))
    }

    // ---------------------------------------------------------------- the sliders that lock (I-226)

    /**
     * **Locked by the stored switch, never by the shown one**: at 0 % the switch reads off because of the
     * slider (`amountSwitch`), and the slider is how the amount comes back. So every slider that locks
     * asks [SettingsUiModel.amountSliderEnabled] of a stored flag -- a `.visible`, `Visible` -- and none of
     * a shown state (the review's W1: a slider locked with `shown.shownOn` passed every test before).
     */
    @Test
    fun `an amount slider locks on its stored switch, so it stays live at zero`() {
        assertTrue(SettingsUiModel.amountSliderEnabled(storedVisible = true))
        assertFalse(SettingsUiModel.amountSliderEnabled(storedVisible = false))
        var asked = 0
        for (file in listOf("ui/WorldSceneScreen.kt", "ui/SceneCategorySections.kt")) {
            for (m in Regex("""SettingsUiModel\.amountSliderEnabled\(([^)]*)\)""").findAll(source(file))) {
                val flag = m.groupValues[1].trim()
                assertTrue("$file asks the slider's lock of '$flag', which is not a stored switch", flag.endsWith("visible") || flag.endsWith("Visible"))
                asked++
            }
        }
        assertTrue("every amount slider of the scene's screens ($asked)", asked >= 13)
    }

    @Test
    fun `Parallax strength and Scroll the background too lock while nothing scrolls`() {
        val noSwipe = SettingsUiModel.swipeScroll(stored = true, swipeReported = false)
        val swipe = SettingsUiModel.swipeScroll(stored = true, swipeReported = true)
        assertTrue("the drift scrolls", SettingsUiModel.sceneScrolls(0.15f, noSwipe))
        assertFalse("still, and no swipe reported", SettingsUiModel.sceneScrolls(0f, noSwipe))
        assertTrue("still, but swipes move it", SettingsUiModel.sceneScrolls(0f, swipe))
        assertFalse("swipes reported and Swipe scroll off", SettingsUiModel.sceneScrolls(0f, SettingsUiModel.swipeScroll(false, true)))
        val world = source("ui/WorldSceneScreen.kt")
        assertTrue(world.contains("enabled = scrolls,"))
        assertTrue(world.contains("SettingsUiModel.dependentSwitch(settings.scrollBackground, available = scrolls)"))
    }

    /**
     * **The census** (v5.10E): every slider on the scene's screens either follows a switch on its page
     * (`enabled =`) or is one of the sliders that always act. A slider added later without either fails
     * here, which is what keeps I-226 from coming back with a new name.
     */
    @Test
    fun `every slider of the scene's screens locks with its switch, or always acts`() {
        val alwaysActs = setOf(
            "Scroll speed", // the drift itself
            "Sun/Cloud Height", // the arc of the sun and the moon, and the clouds' band
            "Variation", // the hills' outline
            "Snow piles", "Leaf piles", // shown only under their palette
            "Open from", "Until", // shown only while their group's opening hours read on
        )
        for (file in listOf("ui/WorldSceneScreen.kt", "ui/SceneCategorySections.kt", "ui/SeasonsScreen.kt")) {
            val text = source(file)
            for (call in Regex("""(PreferenceSlider|SettingsSliderRow)\(""").findAll(text)) {
                // The call's own argument list, to its closing parenthesis.
                var depth = 0
                var end = call.range.last
                while (true) {
                    when (text[end]) { '(' -> depth++; ')' -> depth-- }
                    if (depth == 0) break
                    end++
                }
                val args = text.substring(call.range.first, end + 1)
                val title = Regex("""title = "([^"]+)"""").find(args)?.groupValues?.get(1)
                    ?: Regex("""Text\("([^"${'$'}:]+)""").find(args)?.groupValues?.get(1)?.trim()
                    ?: "?"
                val lock = Regex("""enabled = ([^\n]*)""").find(args)?.groupValues?.get(1)
                assertTrue("$file: the slider '$title' neither locks nor always acts", lock != null || title in alwaysActs)
                if (lock != null) {
                    assertFalse("$file: '$title' locks on a shown state ($lock): it would lock at its own 0 %", lock.contains("shownOn") || lock.contains("noneAtZero"))
                }
            }
        }
    }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: File? = File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
