package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * **A switch shows on only if what it promises is happening** (`DESIGN_NOTES.md` §12, decision 40):
 * Live Weather, the switches that depend on another one, the switches at 0 %; and a theme picked while
 * the calendar chooses asks first. *Notify me* is `NotifySwitchTruthTest`; the palms,
 * `PalmsOnEveryThemeTest` (and where their switch stands, `PalmsSwitchOnTheTreesPageTest`).
 *
 * Every rule is a pure function in [SettingsUiModel], asserted here over its inputs; that the screens
 * draw from it is asserted on their source, as the project does for Compose code it cannot run on
 * the JVM (`MoonPhaseControlTest`): whole lines with the comments taken off.
 */
class SwitchesTellTheTruthTest {

    // ------------------------------------------------------------------ Live Weather

    private fun weather(
        enabled: Boolean = true,
        followRealTime: Boolean = true,
        mode: LocationMode = LocationMode.CUSTOM,
        keyMissing: Boolean = false,
        isTheWallpaper: Boolean = true,
        status: LiveWeatherStatus = LiveWeatherStatus.OK,
        devicePositionUsable: Boolean = true,
    ) = SettingsUiModel.liveWeather(enabled, followRealTime, mode, devicePositionUsable, keyMissing, isTheWallpaper, status)

    @Test
    fun `Live Weather reads on only while real weather can drive the scene`() {
        assertTrue(weather().shownOn)
        assertTrue("a forecast still showing after a failure is real weather", weather(status = LiveWeatherStatus.STALE).shownOn)
        assertTrue("waiting for the first answer is not something to put right", weather(status = LiveWeatherStatus.OFF).shownOn)
        assertTrue("nor is a network that did not answer: the app retries", weather(status = LiveWeatherStatus.FAILED).shownOn)
        val held = mapOf(
            LiveWeatherBlocker.FIXED_HOUR to weather(followRealTime = false),
            LiveWeatherBlocker.NO_LOCATION to weather(mode = LocationMode.OFF),
            LiveWeatherBlocker.MISSING_KEY to weather(keyMissing = true),
            LiveWeatherBlocker.NOT_THE_WALLPAPER to weather(isTheWallpaper = false),
            LiveWeatherBlocker.REJECTED_KEY to weather(status = LiveWeatherStatus.REJECTED_API_KEY),
            LiveWeatherBlocker.LOCATION_UNAVAILABLE to weather(mode = LocationMode.GPS, status = LiveWeatherStatus.NO_LOCATION),
            // Read from the phone by the screen (`DeviceLocationRowTest`).
            LiveWeatherBlocker.LOCATION_NOT_ALLOWED to weather(mode = LocationMode.GPS, devicePositionUsable = false),
        )
        assertEquals("every reason is covered", LiveWeatherBlocker.entries.toSet() - LiveWeatherBlocker.NONE, held.keys)
        for ((blocker, state) in held) {
            assertFalse("$blocker: the switch read on", state.shownOn)
            assertEquals(blocker, state.blocker)
            assertTrue("$blocker: the stored choice must be kept", state.configuredOn)
        }
    }

    @Test
    fun `the first thing to put right is named first`() {
        // Everything missing at once: the fixed hour, which a tap puts right on this screen, first.
        val all = weather(followRealTime = false, mode = LocationMode.OFF, keyMissing = true, isTheWallpaper = false)
        assertEquals(LiveWeatherBlocker.FIXED_HOUR, all.blocker)
        assertEquals(LiveWeatherBlocker.NO_LOCATION, weather(mode = LocationMode.OFF, keyMissing = true, isTheWallpaper = false).blocker)
        assertEquals(LiveWeatherBlocker.MISSING_KEY, weather(keyMissing = true, isTheWallpaper = false).blocker)
        // Another wallpaper before what an engine said: with no engine running, what it said is old.
        assertEquals(
            LiveWeatherBlocker.NOT_THE_WALLPAPER,
            weather(isTheWallpaper = false, status = LiveWeatherStatus.REJECTED_API_KEY).blocker,
        )
    }

    @Test
    fun `an engine's old answer is not a reason while the switch is stored off`() {
        for (status in LiveWeatherStatus.entries) {
            assertEquals("$status", LiveWeatherBlocker.NONE, weather(enabled = false, status = status).blocker)
            assertEquals(LiveWeatherTap.TURN_ON, SettingsUiModel.liveWeatherTap(weather(enabled = false, status = status)))
        }
    }

    @Test
    fun `every tap on Live Weather goes where the reason is put right`() {
        val expected = mapOf(
            weather(followRealTime = false) to LiveWeatherTap.FOLLOW_REAL_TIME,
            weather(mode = LocationMode.OFF) to LiveWeatherTap.SHOW_LOCATION,
            weather(mode = LocationMode.NETWORK, status = LiveWeatherStatus.NO_LOCATION) to LiveWeatherTap.SHOW_LOCATION,
            weather(keyMissing = true) to LiveWeatherTap.OPEN_KEY,
            weather(status = LiveWeatherStatus.REJECTED_API_KEY) to LiveWeatherTap.OPEN_KEY,
            weather(isTheWallpaper = false) to LiveWeatherTap.SET_AS_WALLPAPER,
            weather() to LiveWeatherTap.TURN_OFF,
            weather(enabled = false) to LiveWeatherTap.TURN_ON,
            // Stored off and held: the tap stores it and goes to the reason in the same move.
            weather(enabled = false, followRealTime = false) to LiveWeatherTap.FOLLOW_REAL_TIME,
            weather(enabled = false, isTheWallpaper = false) to LiveWeatherTap.SET_AS_WALLPAPER,
        )
        for ((state, tap) in expected) assertEquals("$state", tap, SettingsUiModel.liveWeatherTap(state))
    }

    @Test
    fun `the home screen says Live Weather on only when the switch is`() {
        assertEquals("Live Weather on", SettingsUiModel.homeLiveWeatherLine(weather()))
        assertEquals("Live Weather off", SettingsUiModel.homeLiveWeatherLine(weather(enabled = false)))
        assertEquals(
            "Live Weather off: the scene is at a fixed hour",
            SettingsUiModel.homeLiveWeatherLine(weather(followRealTime = false)),
        )
        for (blocker in LiveWeatherBlocker.entries - LiveWeatherBlocker.NONE) {
            val state = when (blocker) {
                LiveWeatherBlocker.FIXED_HOUR -> weather(followRealTime = false)
                LiveWeatherBlocker.NO_LOCATION -> weather(mode = LocationMode.OFF)
                LiveWeatherBlocker.MISSING_KEY -> weather(keyMissing = true)
                LiveWeatherBlocker.NOT_THE_WALLPAPER -> weather(isTheWallpaper = false)
                LiveWeatherBlocker.REJECTED_KEY -> weather(status = LiveWeatherStatus.REJECTED_API_KEY)
                LiveWeatherBlocker.LOCATION_UNAVAILABLE -> weather(mode = LocationMode.GPS, status = LiveWeatherStatus.NO_LOCATION)
                LiveWeatherBlocker.LOCATION_NOT_ALLOWED -> weather(mode = LocationMode.GPS, devicePositionUsable = false)
                LiveWeatherBlocker.NONE -> error("unreachable")
            }
            val line = SettingsUiModel.homeLiveWeatherLine(state)
            assertTrue("$blocker: $line", line.startsWith("Live Weather off: "))
            assertFalse("$blocker: $line", line.contains("starting"))
        }
    }

    // ------------------------------------------------------------------ depending on another

    @Test
    fun `Realistic Moon Phases is off and locked while the moon is hidden, and its choice comes back`() {
        for (stored in listOf(true, false)) {
            val hidden = SettingsUiModel.moonPhases(stored, halloweenEnabled = false, moonVisible = false)
            assertFalse(hidden.shownOn)
            assertFalse(hidden.interactive)
            assertTrue("the row owes the reason", hidden.needsMoon)
            assertEquals(stored, SettingsUiModel.moonPhases(stored, halloweenEnabled = false, moonVisible = true).shownOn)
            // Halloween is said first when both hold.
            assertFalse(SettingsUiModel.moonPhases(stored, halloweenEnabled = true, moonVisible = false).needsMoon)
        }
    }

    @Test
    fun `a switch that depends on another is off and locked without it, and its choice comes back`() {
        for (stored in listOf(true, false)) {
            assertEquals(DependentSwitchUiState(stored, true), SettingsUiModel.dependentSwitch(stored, available = true))
            assertEquals(DependentSwitchUiState(false, false), SettingsUiModel.dependentSwitch(stored, available = false))
        }
    }

    @Test
    fun `Thunderstorm reads on exactly when the renderer flashes, and is left to the forecast while it drives`() {
        // Held to the renderer's own rule, not to a sentence: `stormActive`'s theme path, every
        // combination. The intensity is not in it -- the renderer flashes at 0 % too.
        for (stored in listOf(true, false)) for (visible in listOf(true, false)) for (isRain in listOf(true, false)) {
            val ui = SettingsUiModel.thunderstorm(stored, precipitationVisible = visible, precipitationIsRain = isRain)
            val flashes = com.paperscrape.livewallpaper.engine.LiveWeatherSceneRules.stormActive(null, visible, isRain, stored)
            assertEquals("stored=$stored visible=$visible rain=$isRain", flashes, ui.shownOn)
            assertEquals("locked exactly where it can do nothing", visible && isRain, ui.interactive)
        }
        // Under a forecast the row is not drawn: locked with the stored value, it could read on
        // under a clear sky (`ForecastWeatherControlsTest`).
    }

    @Test
    fun `Palms read on only where a palm stands - the switch on, and trees to stand in`() {
        val autumn = defaultCustomizationFor("autumn").copy(palmsInsteadOfTrees = true)
        assertTrue(SettingsUiModel.palms(autumn, layoutPlantsPalms = false, onlyFirsWouldStand = false).shownOn)
        for (noTrees in listOf(
            autumn.copy(trees = autumn.trees.copy(visible = false)),
            autumn.copy(trees = autumn.trees.copy(density = 0f)),
        )) {
            val held = SettingsUiModel.palms(noTrees, layoutPlantsPalms = false, onlyFirsWouldStand = false)
            assertFalse("no tree to stand in, and the switch read on", held.shownOn)
            assertFalse(held.interactive)
        }
        // Beach the same way: its palms are its trees.
        val beach = defaultCustomizationFor("beach")
        assertTrue(SettingsUiModel.palms(beach, layoutPlantsPalms = true, onlyFirsWouldStand = false).shownOn)
        assertFalse(SettingsUiModel.palms(beach.copy(trees = beach.trees.copy(visible = false)), layoutPlantsPalms = true, onlyFirsWouldStand = false).shownOn)
    }

    // ------------------------------------------------------------------ at 0 %

    @Test
    fun `a switch at 0 percent reads off, says so, and comes back with the slider`() {
        assertEquals(AmountSwitchUiState(shownOn = false, noneAtZero = true), SettingsUiModel.amountSwitch(true, 0f))
        assertEquals(AmountSwitchUiState(shownOn = true, noneAtZero = false), SettingsUiModel.amountSwitch(true, 0.01f))
        assertEquals(AmountSwitchUiState(shownOn = false, noneAtZero = false), SettingsUiModel.amountSwitch(false, 0.5f))
        assertEquals(AmountSwitchUiState(shownOn = false, noneAtZero = true), SettingsUiModel.amountSwitch(false, 0f))
    }

    @Test
    fun `turning on at 0 percent brings the theme's own amount back, and nothing else touches it`() {
        assertEquals(AmountTap.Restore(0.65f), SettingsUiModel.amountTap(wanted = true, amount = 0f, defaultAmount = 0.65f))
        assertEquals(
            AmountTap.Restore(SettingsUiModel.FALLBACK_AMOUNT),
            SettingsUiModel.amountTap(wanted = true, amount = 0f, defaultAmount = 0f),
        )
        assertEquals(AmountTap.SetVisible(true), SettingsUiModel.amountTap(wanted = true, amount = 0.3f, defaultAmount = 0.65f))
        assertEquals(AmountTap.SetVisible(false), SettingsUiModel.amountTap(wanted = false, amount = 0.3f, defaultAmount = 0.65f))
        assertEquals(AmountTap.SetVisible(false), SettingsUiModel.amountTap(wanted = false, amount = 0f, defaultAmount = 0.65f))
    }

    @Test
    fun `no built-in theme ships one of these at 0, so the fallback is never what a user sees`() {
        for (theme in ThemeCatalog.ALL) {
            val d = defaultCustomizationFor(theme.id)
            val amounts = mapOf(
                "houses" to d.houses.density, "trees" to d.trees.density, "parasols" to d.parasols.density,
                "snowmen" to d.snowmen.density, "gifts" to d.gifts.density, "penguins" to d.penguins.density,
                "bunnies" to d.bunnies.density, "eggs" to d.easterEggs.density, "pumpkins" to d.pumpkins.density,
                "stars" to d.stars.density, "birds" to d.birds.density, "clouds" to d.clouds.density,
                "rain/snow" to d.precipitation.intensity, "rainbow" to d.rainbow.opacity,
                "sailboats" to d.lake.sailboatsDensity, "dolphins" to d.lake.dolphinsDensity,
            )
            for ((name, amount) in amounts) assertTrue("${theme.id} ships $name at 0", amount > 0f)
        }
    }

    @Test
    fun `the decorations counted on are the ones drawn`() {
        val none = SceneCustomization.DEFAULT.copy(
            christmasBuildingLightsEnabled = false, christmasTreeLightsEnabled = false, christmasFirsEnabled = false, santaEnabled = false, halloweenEnabled = false,
            horrorSkyEnabled = false, flowersEnabled = false,
        )
        assertEquals(0, SettingsUiModel.decorationsOn(none))
        val snowmenAtZero = none.copy(snowmen = none.snowmen.copy(visible = true, density = 0f))
        assertEquals("snowmen at 0% counted as on", 0, SettingsUiModel.decorationsOn(snowmenAtZero))
        val snowmen = none.copy(snowmen = none.snowmen.copy(visible = true, density = 0.3f))
        assertEquals(1, SettingsUiModel.decorationsOn(snowmen))
        // The palms are not a decoration: `PalmsSwitchOnTheTreesPageTest`.
    }

    /**
     * **A decoration its switch shows off and locked is not counted on** (`DESIGN_NOTES.md` §12, decision 77). With
     * *Show Trees* off, *Lights on the trees* and *Christmas firs* read off and
     * locked on their page; with no house and no building, *Halloween decorations*, and *Lights on the buildings* too while no
     * house stands on the mountains either: the home screen's line counts each as its switch shows it, as Seasons &
     * decorations does -- Christmas as it ships with the trees off is 4 on, the two of the trees not among them, not 6.
     * Every built-in theme, every switch on, under each way of having nothing to hang them on: the count is
     * the count with the four set to what their switches show.
     */
    @Test
    fun `a decoration locked for want of a tree or a house is not counted on`() {
        val christmas = defaultCustomizationFor("christmas")
        assertEquals("Christmas as it ships", 6, SettingsUiModel.decorationsOn(christmas))
        assertEquals("Christmas with the trees off: the lights on the trees and the firs read off", 4,
            SettingsUiModel.decorationsOn(christmas.copy(trees = christmas.trees.copy(visible = false))))
        assertEquals("the trees at 0 %", 4, SettingsUiModel.decorationsOn(christmas.copy(trees = christmas.trees.copy(density = 0f))))
        for (theme in ThemeCatalog.ALL) {
            val base = defaultCustomizationFor(theme.id)
            val ways = listOf(
                "as it ships" to base,
                "no trees" to base.copy(trees = base.trees.copy(visible = false)),
                "no houses, no buildings" to base.copy(houses = base.houses.copy(visible = false), buildings = base.buildings.copy(visible = false)),
                "nothing on the mountains either" to base.copy(
                    houses = base.houses.copy(visible = false), buildings = base.buildings.copy(visible = false),
                    distantHouses = base.distantHouses.copy(visible = false),
                ),
            )
            for ((way, c0) in ways) for (halloweenSide in listOf(false, true)) {
                val c = if (halloweenSide) {
                    c0.copy(halloweenDecorationsEnabled = true, christmasBuildingLightsEnabled = false, christmasTreeLightsEnabled = false, christmasFirsEnabled = false)
                } else {
                    c0.copy(halloweenDecorationsEnabled = false, christmasBuildingLightsEnabled = true, christmasTreeLightsEnabled = true, christmasFirsEnabled = true)
                }
                val shown = c.copy(
                    christmasBuildingLightsEnabled = SettingsUiModel.christmasBuildingDecoration(c.christmasBuildingLightsEnabled, c).shownOn,
                    christmasTreeLightsEnabled = SettingsUiModel.christmasTreeDecoration(c.christmasTreeLightsEnabled, c).shownOn,
                    christmasFirsEnabled = SettingsUiModel.christmasTreeDecoration(c.christmasFirsEnabled, c).shownOn,
                    halloweenDecorationsEnabled = SettingsUiModel.halloweenDecorations(c).shownOn,
                )
                assertEquals("${theme.id}, $way, ${if (halloweenSide) "Halloween's" else "Christmas's"}", SettingsUiModel.decorationsOn(shown), SettingsUiModel.decorationsOn(c))
            }
        }
        val noHouses = christmas.copy(
            houses = christmas.houses.copy(visible = false), buildings = christmas.buildings.copy(visible = false),
            distantHouses = christmas.distantHouses.copy(visible = false),
        )
        assertEquals("Christmas with nothing to hang the buildings' lights on", 5, SettingsUiModel.decorationsOn(noHouses))
        val halloween = defaultCustomizationFor("halloween")
        val bare = halloween.copy(houses = halloween.houses.copy(visible = false), buildings = halloween.buildings.copy(visible = false))
        assertEquals("Halloween's decorations with no house or building", SettingsUiModel.decorationsOn(halloween) - 1, SettingsUiModel.decorationsOn(bare))
    }

    // ------------------------------------------------------------------ the calendar

    @Test
    fun `a pick the calendar would hide asks first, and only that one`() {
        assertTrue("shuffle while the calendar chooses", SettingsUiModel.pickNeedsCalendarQuestion("autumn", pickedThemeId = null))
        assertTrue("another card", SettingsUiModel.pickNeedsCalendarQuestion("autumn", "beach"))
        assertFalse("the calendar's own card: it already shows", SettingsUiModel.pickNeedsCalendarQuestion("autumn", "autumn"))
        assertFalse("no calendar today", SettingsUiModel.pickNeedsCalendarQuestion(null, "beach"))
        assertFalse("no calendar, shuffle", SettingsUiModel.pickNeedsCalendarQuestion(null, null))
    }

    // ------------------------------------------------------------------ the wiring

    @Test
    fun `the screens draw these switches from the rules`() {
        val weather = codeLines("ui/WeatherTimeScreen.kt")
        assertTrue(weather.contains("checked = liveWeather.shownOn,"))
        assertTrue(weather.contains("when (SettingsUiModel.liveWeatherTap(liveWeather)) {"))
        assertFalse("the raw flag must not reach the switch", weather.any { "checked = settings.liveWeatherEnabled" in it })
        for (tap in LiveWeatherTap.entries) assertTrue("$tap not handled", weather.any { it.startsWith("LiveWeatherTap.${tap.name} ->") })

        val home = codeLines("ui/SettingsScreen.kt")
        assertTrue(home.contains("supportingIsAccent = liveWeather.shownOn,"))
        assertTrue(home.contains("if (SettingsUiModel.pickNeedsCalendarQuestion(calendarThemeId, pickedThemeId = null)) {"))
        assertTrue(home.contains("return when (val decorations = SettingsUiModel.decorationsOn(customization, themeLayout)) {"))
        assertTrue(home.contains("val standing = SettingsUiModel.standing(themeLayout, customization)"))

        val gallery = codeLines("ui/ThemeGalleryScreen.kt")
        assertEquals(
            "both card kinds go through the question, and nothing else picks a card",
            listOf("onSelect = { pick(builtin.id) },", "onSelect = { pick(entry.id) },"),
            gallery.filter { it.startsWith("onSelect = ") },
        )

        val world = codeLines("ui/WorldSceneScreen.kt")
        for (rule in listOf(
            "val storm = SettingsUiModel.thunderstorm(",
            "val nightBirds = SettingsUiModel.dependentSwitch(customization.birds.nightBirds, available = birds.shownOn)",
            "moonVisible = customization.moon.visible,",
        )) {
            assertTrue("World & scene does not use $rule", world.contains(rule))
        }
        // Every amount switch of World & scene and Seasons goes through the 0 % rule -- the lake's
        // height too.
        assertEquals(8, world.count { it.startsWith("SettingsUiModel.amountTap(") })
        assertTrue(codeLines("ui/SeasonsScreen.kt").contains("checked = shown.shownOn,"))
        val worldCode = world.joinToString("\n")
        for (noneAtZero in listOf("ObjectCategory.HOUSES", "ObjectCategory.TREES", "ObjectCategory.PARASOLS")) {
            assertTrue("$noneAtZero is not marked none-at-zero", Regex("""category = $noneAtZero,[^)]*noneAtZero = true""").containsMatchIn(worldCode))
        }
        assertTrue(
            codeLines("ui/SceneCategorySections.kt")
                .contains("checked = if (noneOnTheme) false else if (noneAtZero) shown.shownOn else config.visible,"),
        )
    }

    /** The app's source file [path], its lines trimmed, without comments: a line comment cut off, a comment line dropped. */
    private fun codeLines(path: String): List<String> =
        source(path).lines().map { it.trim() }
            .filterNot { it.startsWith("*") || it.startsWith("/*") || it.startsWith("//") }
            .map { it.substringBefore(" //").trim() }
            .filter { it.isNotEmpty() }

    private fun source(path: String): String {
        val suffix = "src/main/kotlin/com/paperscrape/livewallpaper/$path"
        var dir: java.io.File? = java.io.File(".").absoluteFile
        while (dir != null) {
            for (prefix in listOf("", "app/")) {
                val f = java.io.File(dir, "$prefix$suffix")
                if (f.isFile) return f.readText()
            }
            dir = dir.parentFile
        }
        error("source not found: $suffix")
    }
}
