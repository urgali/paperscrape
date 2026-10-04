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
 * **A switch shows on only if what it promises is happening** (v5.10C) -- the maintainer's rule of
 * 2026-09-29, *«l'utente normale non legge, vede il toggle e si arrabbia perché non funziona»*, applied
 * by his decisions of 2026-09-30 to rows 4, 7, 8 and 9 of the v5.10A table: Live Weather, the switches
 * that depend on another one, the switches at 0 %, and a theme picked while the calendar chooses.
 * Row 1, *Notify me*, is `NotifySwitchTruthTest`; the palms, `PalmsOnEveryThemeTest` (and where their
 * switch stands since v5.10C2, `PalmsSwitchOnTheTreesPageTest`).
 *
 * Every rule is a pure function in [SettingsUiModel], asserted here over its inputs; that the screens
 * draw from it is asserted on their source, as the project does for Compose code it cannot run on
 * the JVM (`MoonPhaseControlTest`).
 */
class SwitchesTellTheTruthTest {

    // ------------------------------------------------------------------ Live Weather (row 4)

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
            // v5.10D: read from the phone by the screen (`DeviceLocationRowTest`).
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

    // ------------------------------------------------------------------ depending on another (row 7)

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
        // combination. The intensity is not in it -- the renderer flashes at 0 % too (I-292).
        for (stored in listOf(true, false)) for (visible in listOf(true, false)) for (isRain in listOf(true, false)) {
            val ui = SettingsUiModel.thunderstorm(stored, precipitationVisible = visible, precipitationIsRain = isRain)
            val flashes = com.paperscrape.livewallpaper.engine.LiveWeatherSceneRules.stormActive(null, visible, isRain, stored)
            assertEquals("stored=$stored visible=$visible rain=$isRain", flashes, ui.shownOn)
            assertEquals("locked exactly where it can do nothing", visible && isRain, ui.interactive)
        }
        // Under a forecast the row is not drawn since v5.10D (it stood locked with the stored value,
        // which could read on under a clear sky): `ForecastWeatherControlsTest`.
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

    // ------------------------------------------------------------------ at 0 % (row 8)

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
            christmasDecorationsEnabled = false, santaEnabled = false, halloweenEnabled = false,
            horrorSkyEnabled = false, flowersEnabled = false,
        )
        assertEquals(0, SettingsUiModel.decorationsOn(none))
        val snowmenAtZero = none.copy(snowmen = none.snowmen.copy(visible = true, density = 0f))
        assertEquals("snowmen at 0% counted as on", 0, SettingsUiModel.decorationsOn(snowmenAtZero))
        val snowmen = none.copy(snowmen = none.snowmen.copy(visible = true, density = 0.3f))
        assertEquals(1, SettingsUiModel.decorationsOn(snowmen))
        // The palms are not a decoration since v5.10C2: `PalmsSwitchOnTheTreesPageTest`.
    }

    // ------------------------------------------------------------------ the calendar (row 9)

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
        val weather = source("ui/WeatherTimeScreen.kt")
        assertTrue(weather.contains("checked = liveWeather.shownOn,"))
        assertTrue(weather.contains("SettingsUiModel.liveWeatherTap(liveWeather)"))
        assertFalse("the raw flag must not reach the switch", weather.contains("checked = settings.liveWeatherEnabled,"))
        for (tap in LiveWeatherTap.entries) assertTrue("$tap not handled", weather.contains("LiveWeatherTap.${tap.name} ->"))

        val home = source("ui/SettingsScreen.kt")
        assertTrue(home.contains("supportingIsAccent = liveWeather.shownOn,"))
        assertTrue(home.contains("SettingsUiModel.pickNeedsCalendarQuestion(calendarThemeId, pickedThemeId = null)"))
        assertTrue(home.contains("SettingsUiModel.decorationsOn(customization)"))

        val gallery = source("ui/ThemeGalleryScreen.kt")
        assertEquals("both card kinds go through the question", 2, Regex("""onSelect = \{ pick\(""").findAll(gallery).count())
        assertFalse(gallery.contains("onSelect = { scope.launch { prefs.setTheme("))

        val world = source("ui/WorldSceneScreen.kt")
        for (rule in listOf("SettingsUiModel.thunderstorm(", "SettingsUiModel.dependentSwitch(customization.birds.nightBirds", "moonVisible = customization.moon.visible")) {
            assertTrue("World & scene does not use $rule", world.contains(rule))
        }
        // Every amount switch of World & scene and Seasons goes through the 0 % rule -- the lake's
        // height too since v5.10E (inventory I-293).
        assertEquals(8, Regex("""SettingsUiModel\.amountTap\(""").findAll(world).count())
        val seasons = source("ui/SeasonsScreen.kt")
        assertTrue(seasons.contains("checked = shown.shownOn,"))
        val section = source("ui/SceneCategorySections.kt")
        for (noneAtZero in listOf("ObjectCategory.HOUSES", "ObjectCategory.TREES", "ObjectCategory.PARASOLS")) {
            assertTrue("$noneAtZero is not marked none-at-zero", Regex("""category = $noneAtZero,[^)]*noneAtZero = true""").containsMatchIn(world))
        }
        assertTrue(section.contains("checked = if (noneAtZero) shown.shownOn else config.visible,"))
    }

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
