package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.BirdColorWeight
import com.paperscrape.livewallpaper.engine.BirdsConfig
import com.paperscrape.livewallpaper.engine.CarSelection
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectRenderer
import com.paperscrape.livewallpaper.engine.SceneObjectType
import com.paperscrape.livewallpaper.engine.SceneSpace
import com.paperscrape.livewallpaper.engine.Storefront
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
 * **Every line says what its control does** (`DESIGN_NOTES.md` §12, decision 45).
 *
 * Each line is held to the rule it describes, not to itself: the buildings' slider to
 * [keepCandidate], the cars' bottom to [CarSelection.countFor] and their switch to
 * [SceneObjectRenderer.drawsRoad], the birds' shares to [BirdsConfig.pickColor], the Christmas firs' line to
 * [SceneObjectRenderer.standsAsFir], *Swipe scroll* to what the wallpaper records ([SwipeReport]), and
 * the sliders that lock to the switch above them.
 */
class LinesSayWhatTheyDoTest {

    // ---------------------------------------------------------------- the buildings

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

    // ---------------------------------------------------------------- the cars

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

    // ---------------------------------------------------------------- the birds

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

    // ---------------------------------------------------------------- the banner

    @Test
    fun `the banner says the changes stay with the theme, on both screens`() {
        val banner = SettingsUiModel.themeEditsBanner("Autumn")
        assertTrue(banner.contains("stay with it when you switch themes"))
        assertFalse("it read as: unsaved changes are lost", banner.contains("Keep this look by saving"))
        assertTrue(source("ui/WorldSceneScreen.kt").contains("SettingsBanner(SettingsUiModel.themeEditsBanner(themeName))"))
        assertTrue(source("ui/SeasonsScreen.kt").contains("SettingsBanner(SettingsUiModel.themeEditsBanner(themeName))"))
    }

    // ---------------------------------------------------------------- Swipe scroll

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
     * **No pages, no swipe**: an offset moved by Android's own default
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
        // Written by this engine once, and again only if that write failed.
        assertTrue(engine.contains("runCatching { prefs.setSwipeReported() }.onFailure { swipeReportSent = false }"))
        assertEquals("written in one place", 1, Regex("""setSwipeReported\(""").findAll(engine).count())
        val backup = source("prefs/AppBackup.kt")
        assertFalse("runtime state of this phone, like the GPS fix", backup.contains("swipeReported"))
        val prefs = source("prefs/WallpaperPrefs.kt")
        assertEquals("read, and written by its one setter", 2, Regex("""Keys\.SWIPE_REPORTED""").findAll(prefs).count())
    }

    // ---------------------------------------------------------------- the Christmas switches

    /**
     * **Three switches** (`DESIGN_NOTES.md` §12, decision 76): each line says everything its own switch draws, and each
     * switch is the one the renderer reads where it draws that.
     */
    @Test
    fun `each Christmas switch says what its own switch draws`() {
        val c = defaultCustomizationFor("autumn")
        val layout = SceneObjectCatalog.layoutFor("autumn", ThemeCatalog.byId("autumn").accentColor)
        val trees = layout.staticObjects.filter { it.type == SceneObjectType.TREE }
        assertTrue("with Christmas firs on, some trees stand as firs", trees.any { SceneObjectRenderer.standsAsFir(it, c.copy(christmasFirsEnabled = true)) })
        assertTrue(
            "and none without, whatever the lights say",
            trees.none {
                SceneObjectRenderer.standsAsFir(
                    it,
                    c.copy(christmasFirsEnabled = false, christmasBuildingLightsEnabled = true, christmasTreeLightsEnabled = true),
                )
            },
        )
        for (word in listOf(
            "along the roofs", "windowsills", "a wreath on every front door", "a star on every tower", "each house on the mountains",
        )) {
            assertTrue("the buildings' line does not say '$word'", CHRISTMAS_BUILDING_LIGHTS_LINE.contains(word))
        }
        // And what it does not: Halloween strips the leafy trees, and the wallpaper hangs no light on the bare branches
        // (`SceneObjectRenderer.drawTree` returns before them).
        for (word in listOf("lights on the trees", "palms", "none on the bare branches halloween leaves")) {
            assertTrue("the trees' line does not say '$word'", CHRISTMAS_TREE_LIGHTS_LINE.lowercase().contains(word))
        }
        for (word in listOf("one tree in three becomes a Christmas fir", "its lights", "a star on top", "presents at its foot")) {
            assertTrue("the firs' line does not say '$word'", CHRISTMAS_FIRS_LINE.lowercase().contains(word.lowercase()))
        }
        val renderer = source("engine/SceneObjectRenderer.kt")
        assertTrue(renderer.contains("if (customization.christmasBuildingLightsEnabled) drawDecorations(canvas, r, elapsed, deal, family.kind)"))
        assertEquals("the sills' strings too", 1, Regex("""if \(customization\.christmasBuildingLightsEnabled\) \{\s+ChristmasDecorations\.forEachSillString""").findAll(renderer).count())
        assertEquals("the trees' and the palms' lights", 2, Regex("""if \(customization\.christmasTreeLightsEnabled\) \{""").findAll(renderer).count())
        assertTrue(renderer.contains("fun drawsFirs(customization: SceneCustomization): Boolean = customization.christmasFirsEnabled"))
        assertTrue("the distant houses' bulbs", source("engine/PaperRenderer.kt").contains("val christmas = sceneCustomization.christmasBuildingLightsEnabled"))
        val seasons = source("ui/SeasonsScreen.kt")
        for (line in listOf("CHRISTMAS_BUILDING_LIGHTS_LINE", "CHRISTMAS_TREE_LIGHTS_LINE", "CHRISTMAS_FIRS_LINE")) {
            assertTrue("the page shows $line", seasons.contains("line = $line,"))
        }
    }

    // ---------------------------------------------------------------- the Halloween switches

    /**
     * *Halloween* does its four things under its own line; every other thing on the Halloween page -- the scarecrows with
     * their amount among them, and *Moon stays in place*, its row just under *Night sky by day* -- has a switch of its own,
     * and each line says what the drawing does (`DESIGN_NOTES.md` §12, decisions 75 and 86).
     */
    @Test
    fun `each Halloween switch says what its own switch draws`() {
        assertEquals(
            "The moon with a carved face lit from inside, bare branches on the leafy trees, withered palms, and a face carved on every pumpkin",
            HALLOWEEN_LINE,
        )
        assertTrue(LAUGHING_MOON_LINE.contains("About once a minute"))
        assertEquals("once a minute", 60.0, com.paperscrape.livewallpaper.engine.LaughingMoon.EVERY_SECONDS, 0.0)
        assertTrue(NIGHT_SKY_BY_DAY_LINE.contains("the Halloween moon takes the sun's place"))
        assertTrue(NIGHT_SKY_BY_DAY_LINE.contains("The shops keep their real opening hours"))
        // One cobweb a roof and no bat (`DESIGN_NOTES.md` §12, decision 85).
        assertEquals(
            "A cobweb in a corner of every roof and a jack-o'-lantern by every front door, lit at night, on the houses and the shops, open or shut",
            HALLOWEEN_DECORATIONS_LINE,
        )
        assertFalse("no bat any more", HALLOWEEN_DECORATIONS_LINE.contains("bat"))
        val decorates = com.paperscrape.livewallpaper.engine.HalloweenDecorations::decorates
        assertTrue(decorates(com.paperscrape.livewallpaper.engine.WindowBuildingKind.HOUSE, null))
        for (shop in listOf(Storefront.RESTAURANT, Storefront.BAR, Storefront.SHOP)) assertTrue("$shop", decorates(shop.family.kind, shop))
        for (other in listOf(Storefront.SCHOOL, Storefront.FIRE_STATION, Storefront.POLICE_STATION)) assertFalse("$other", decorates(other.family.kind, other))
        assertFalse("no tower", decorates(com.paperscrape.livewallpaper.engine.WindowBuildingKind.SKYSCRAPER, null))
        assertTrue(SCARECROWS_LINE.contains("where the snowmen stand"))
        // Thrown from his hand (`DESIGN_NOTES.md` §12, decisions 79 and 84).
        assertEquals(
            "The Headless Horseman gallops across the sky about once a minute, throwing pumpkins on fire from his hand",
            HEADLESS_HORSEMAN_LINE,
        )
        // The still moon, its row right under *Night sky by day*, its line all it does (`DESIGN_NOTES.md` §12, decision 86).
        assertEquals(
            "The Halloween moon stays still, low over the roofs under the clouds, instead of crossing the sky. " +
                "Without Night sky by day it comes out after sunset and fades before sunrise, where it stands.",
            STILL_MOON_LINE,
        )
        val seasons = source("ui/SeasonsScreen.kt")
        val nightRow = seasons.indexOf("title = \"Night sky by day\",")
        val stillRow = seasons.indexOf("title = \"Moon stays in place\",")
        val horrorRow = seasons.indexOf("title = \"Horror sky\",")
        assertTrue("the still moon's row right under the night by day's: $nightRow $stillRow $horrorRow", stillRow in nightRow..horrorRow)
        assertFalse("another row between the night by day's and the still moon's", seasons.substring(nightRow + 1, stillRow).contains("title = \""))
        assertTrue("greyed by the carved moon's rule", seasons.contains("supporting = halloweenMoonLine(STILL_MOON_LINE, stillMoon),"))
        assertTrue(seasons.contains("onCheckedChange = { scope.launch { prefs.setStillMoonEnabled(it, forThemeId) } },"))
        assertTrue(BLOOD_RAIN_LINE.contains("twice as thick"))
        assertEquals(2f, com.paperscrape.livewallpaper.engine.PaperRenderer.BLOOD_RAIN_THICKER, 0f)
        assertTrue(BLOOD_RAIN_LINE.contains("snow stays snow"))
    }

    // ---------------------------------------------------------------- Weather effects, the release page

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

    // ---------------------------------------------------------------- the dry thunderstorm

    /**
     * **Meant, and the comments say so** (`DESIGN_NOTES.md` §12, decision 44: *«nel mondo vero può esistere
     * un temporale senza pioggia, quindi va bene»*, 2026-10-04). The theme's storm flashes with Rain chosen
     * whatever the intensity -- `stormActive` takes none -- and the comments of `LiveWeatherSceneRules` and
     * `WeatherSnapshotMapper` say so, not that it needs rain falling.
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
        assertTrue(mapper.contains("a dry\n            // thunderstorm, `DESIGN_NOTES.md` §12, decision 44."))
    }

    // ---------------------------------------------------------------- the sliders that lock

    /**
     * **Locked by the stored switch, never by the shown one**: at 0 % the switch reads off because of the
     * slider (`amountSwitch`), and the slider is how the amount comes back. So every slider that locks
     * asks [SettingsUiModel.amountSliderEnabled] of a stored flag -- a `.visible`, `Visible` -- and none of
     * a shown state, which would lock it at its own 0 %.
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
     * **The census**: every slider on the scene's screens either follows a switch on its page
     * (`enabled =`) or is one of the sliders that always act. A slider added without either fails here.
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

    // ---------------------------------------------------------------- Santa and Halloween

    @Test
    fun `Santa's line says about once a minute, the rhythm the sleigh keeps`() {
        // The rhythm stays and the line says it (`DESIGN_NOTES.md` §12, decision 73). The next flight sets off 25 to 60 s
        // after the last one ends and a flight lasts 9 to 13 s: a pass every 34 to 73 s, about once a minute.
        val effect = source("engine/SantaSleighEffect.kt")
        assertTrue(effect.contains("timeUntilNextFlight = 25f + SceneRandom.source.nextFloat() * 35f"))
        // And the flight's own length, the other half of "a pass every 34 to 73 s".
        assertTrue(effect.contains("flightDuration = 9f + SceneRandom.source.nextFloat() * 4f"))
        assertTrue(source("ui/SeasonsScreen.kt").contains("supporting = \"Santa's sleigh flies across the sky about once a minute, dropping gifts\""))
    }

    @Test
    fun `Halloween's line names the four things its switch does`() {
        // One switch, four changes (`DESIGN_NOTES.md` §12, decision 75): the moon's carved face with its light,
        // the bare trees, the withered palms, the carved pumpkins.
        val objects = source("engine/SceneObjectRenderer.kt")
        for (sprite in listOf("tree_dead_branches", "palmtree_fronds_dead", "pumpkin_face")) {
            assertTrue("the street no longer draws $sprite", objects.contains("R.drawable.$sprite"))
        }
        assertTrue(source("engine/PaperRenderer.kt").contains("R.drawable.moon_jack_o_lantern_light"))
        // The row shows the line through its constant, where the Halloween page names its lines.
        val seasons = source("ui/SeasonsScreen.kt")
        val row = seasons.substring(seasons.indexOf("title = \"Halloween\",")).substringBefore("checked =")
        assertTrue(row.contains("supporting = HALLOWEEN_LINE,"))
        val line = HALLOWEEN_LINE
        for (word in listOf("moon", "lit from inside", "bare branches on the leafy trees", "withered palms", "every pumpkin")) {
            assertTrue("the Halloween line no longer says '$word': $line", line.contains(word))
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
