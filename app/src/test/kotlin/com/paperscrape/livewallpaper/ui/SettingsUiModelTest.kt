package com.paperscrape.livewallpaper.ui

import com.paperscrape.livewallpaper.location.DeviceLocationKind

import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.prefs.WallpaperSettings
import com.paperscrape.livewallpaper.weather.LiveWeatherStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The v2.9 settings UI presents two pairs of mutually exclusive booleans as one choice each --
 * location source and seasonal palette. Nothing about how either is stored changed, and these
 * tests are what says so: every flag combination reads back as exactly one mode, every mode
 * writes back exactly the flags the two switches used to write, and both round-trip.
 *
 * The ordinal assertions are not decoration. The segmented controls index their options by
 * `enum.ordinal` and read the tap back through `entries[index]`, so reordering either enum would
 * silently swap two settings; these pin the order to the labels shown on screen.
 */
class SettingsUiModelTest {

    // -- Location -----------------------------------------------------------------------------

    @Test
    fun `no location flag set reads as off`() {
        assertEquals(
            LocationMode.OFF,
            SettingsUiModel.locationMode(useDeviceLocation = false, useCustomLocation = false),
        )
    }

    /**
     * An install from before v3.0 stored the device flag and nothing about *which* system it
     * meant. It must read as Network, not GPS: the old single mode asked the network provider
     * first and only reached for GPS if it was disabled, so Network is the behaviour -- and the
     * permission -- those users already had.
     */
    @Test
    fun `a device flag with no stored kind reads as network, not GPS`() {
        assertEquals(
            LocationMode.NETWORK,
            SettingsUiModel.locationMode(useDeviceLocation = true, useCustomLocation = false),
        )
    }

    @Test
    fun `each device kind reads as its own mode`() {
        assertEquals(
            LocationMode.GPS,
            SettingsUiModel.locationMode(true, false, DeviceLocationKind.GPS),
        )
        assertEquals(
            LocationMode.NETWORK,
            SettingsUiModel.locationMode(true, false, DeviceLocationKind.NETWORK),
        )
    }

    @Test
    fun `only the two device modes name a positioning system`() {
        assertEquals(DeviceLocationKind.GPS, SettingsUiModel.deviceKindFor(LocationMode.GPS))
        assertEquals(DeviceLocationKind.NETWORK, SettingsUiModel.deviceKindFor(LocationMode.NETWORK))
        assertEquals(null, SettingsUiModel.deviceKindFor(LocationMode.OFF))
        assertEquals(
            "Custom must never reach a positioning system: it needs no permission at all",
            null,
            SettingsUiModel.deviceKindFor(LocationMode.CUSTOM),
        )
    }

    @Test
    fun `custom flag reads as custom`() {
        assertEquals(
            LocationMode.CUSTOM,
            SettingsUiModel.locationMode(useDeviceLocation = false, useCustomLocation = true),
        )
    }

    /**
     * A state the preferences layer never produces -- both setters clear the other flag -- but a
     * reader still has to resolve it to something rather than throw. The device fix wins because
     * it is the one the wallpaper service actually resolves coordinates from.
     */
    @Test
    fun `both location flags set resolve to a device mode rather than throwing`() {
        assertEquals(
            LocationMode.NETWORK,
            SettingsUiModel.locationMode(useDeviceLocation = true, useCustomLocation = true),
        )
    }

    @Test
    fun `location modes write the same flag pairs the two switches wrote`() {
        assertEquals(false to false, SettingsUiModel.locationFlags(LocationMode.OFF))
        assertEquals(true to false, SettingsUiModel.locationFlags(LocationMode.GPS))
        assertEquals(true to false, SettingsUiModel.locationFlags(LocationMode.NETWORK))
        assertEquals(false to true, SettingsUiModel.locationFlags(LocationMode.CUSTOM))
    }

    @Test
    fun `every location mode round-trips through its flags and kind`() {
        for (mode in LocationMode.entries) {
            val (device, custom) = SettingsUiModel.locationFlags(mode)
            val kind = SettingsUiModel.deviceKindFor(mode) ?: DeviceLocationKind.NETWORK
            assertEquals(mode, SettingsUiModel.locationMode(device, custom, kind))
        }
    }

    /**
     * The segmented control indexes its options by [LocationMode.ordinal], so the enum's order
     * *is* the on-screen order. Reordering the enum without reordering the labels would silently
     * put the user on a different mode from the one they tapped.
     */
    @Test
    fun `location option order matches the segmented control labels`() {
        assertEquals(0, LocationMode.OFF.ordinal) // "Off"
        assertEquals(1, LocationMode.GPS.ordinal) // "GPS"
        assertEquals(2, LocationMode.NETWORK.ordinal) // "Network"
        assertEquals(3, LocationMode.CUSTOM.ordinal) // "Custom"
        assertEquals(4, LocationMode.entries.size)
    }

    @Test
    fun `a fresh install starts with no location source`() {
        val defaults = WallpaperSettings()
        assertEquals(
            LocationMode.OFF,
            SettingsUiModel.locationMode(defaults.useLocationForSunTimes, defaults.useCustomLocation),
        )
    }

    // -- Seasonal palette ---------------------------------------------------------------------

    @Test
    fun `no palette flag set reads as none`() {
        assertEquals(
            SeasonalPalette.NONE,
            SettingsUiModel.seasonalPalette(fallColorsEnabled = false, winterColorsEnabled = false),
        )
    }

    @Test
    fun `fall flag reads as autumn`() {
        assertEquals(
            SeasonalPalette.AUTUMN,
            SettingsUiModel.seasonalPalette(fallColorsEnabled = true, winterColorsEnabled = false),
        )
    }

    @Test
    fun `winter flag reads as winter`() {
        assertEquals(
            SeasonalPalette.WINTER,
            SettingsUiModel.seasonalPalette(fallColorsEnabled = false, winterColorsEnabled = true),
        )
    }

    @Test
    fun `both palette flags set resolve to winter rather than throwing`() {
        assertEquals(
            SeasonalPalette.WINTER,
            SettingsUiModel.seasonalPalette(fallColorsEnabled = true, winterColorsEnabled = true),
        )
    }

    @Test
    fun `palettes write the same flag pairs the two switches wrote`() {
        assertEquals(false to false, SettingsUiModel.seasonalPaletteFlags(SeasonalPalette.NONE))
        assertEquals(true to false, SettingsUiModel.seasonalPaletteFlags(SeasonalPalette.AUTUMN))
        assertEquals(false to true, SettingsUiModel.seasonalPaletteFlags(SeasonalPalette.WINTER))
    }

    @Test
    fun `every palette round-trips through its flags`() {
        for (palette in SeasonalPalette.entries) {
            val (fall, winter) = SettingsUiModel.seasonalPaletteFlags(palette)
            assertEquals(palette, SettingsUiModel.seasonalPalette(fall, winter))
        }
    }

    @Test
    fun `palette option order matches the segmented control labels`() {
        assertEquals(0, SeasonalPalette.NONE.ordinal) // "None"
        assertEquals(1, SeasonalPalette.AUTUMN.ordinal) // "Autumn"
        assertEquals(2, SeasonalPalette.WINTER.ordinal) // "Winter"
    }

    @Test
    fun `the default customization wears no seasonal palette`() {
        val defaults = SceneCustomization.DEFAULT
        assertEquals(
            SeasonalPalette.NONE,
            SettingsUiModel.seasonalPalette(defaults.fallColorsEnabled, defaults.winterColorsEnabled),
        )
    }

    // -- Live Weather (P1-1) -------------------------------------------------------------------
    //
    // The bug these pin was reachable and persistent: Live Weather on, then Location set to Off
    // (or "Follow real time" switched off). The switch went disabled while reading on, World &
    // scene locked clouds and precipitation behind it, and every instruction on screen told the
    // user to do the one thing the UI would not let them do.

    private fun liveWeather(
        enabled: Boolean = true,
        followRealTime: Boolean = true,
        mode: LocationMode = LocationMode.GPS,
        status: LiveWeatherStatus = LiveWeatherStatus.OK,
        keyMissing: Boolean = false,
        isTheWallpaper: Boolean = true,
        devicePositionUsable: Boolean = true,
    ) = SettingsUiModel.liveWeather(enabled, followRealTime, mode, devicePositionUsable, keyMissing, isTheWallpaper, status)

    /**
     * **No dead end, in the shape it has since v5.10C.** Until then this said "a switch that is on
     * can always be switched off", and the switch showed the stored flag. It shows on only while
     * real weather can drive the scene now, so the stored "on" behind a switch drawn off is not
     * something the user can reach directly -- and that is safe only because nothing locks anything
     * while it is drawn off: World & scene's clouds are read-only only while `drivingTheScene`. So:
     * drawn on, a tap turns it off; drawn off, nothing is driving the scene. Every combination.
     */
    @Test
    fun `a Live Weather switch drawn on can be switched off, and one drawn off locks nothing`() {
        for (enabled in listOf(true, false)) for (followRealTime in listOf(true, false)) for (mode in LocationMode.entries)
            for (status in LiveWeatherStatus.entries) for (keyMissing in listOf(true, false)) for (wallpaper in listOf(true, false)) {
                val state = liveWeather(enabled, followRealTime, mode, status, keyMissing, wallpaper)
                val label = "on=$enabled real=$followRealTime $mode $status key-missing=$keyMissing wallpaper=$wallpaper"
                if (state.shownOn) {
                    assertEquals(label, LiveWeatherTap.TURN_OFF, SettingsUiModel.liveWeatherTap(state))
                } else {
                    assertFalse("$label: drawn off but locking World & scene", state.drivingTheScene)
                    assertNotEquals("$label: drawn off, and the tap would turn it off", LiveWeatherTap.TURN_OFF, SettingsUiModel.liveWeatherTap(state))
                }
            }
    }

    @Test
    fun `case A - location switched off with Live Weather on`() {
        val state = liveWeather(enabled = true, mode = LocationMode.OFF, status = LiveWeatherStatus.NO_LOCATION)

        assertFalse("nothing to fetch for", state.canBeTurnedOn)
        assertFalse("so the switch reads off", state.shownOn)
        assertEquals(LiveWeatherBlocker.NO_LOCATION, state.blocker)
        assertEquals("and its tap goes to the location", LiveWeatherTap.SHOW_LOCATION, SettingsUiModel.liveWeatherTap(state))
        assertFalse("and the scene is on the theme's own weather", state.drivingTheScene)
    }

    @Test
    fun `case B - follow real time switched off with Live Weather on`() {
        // The status is what an engine published before the hour was fixed, with the settings
        // screen in front of it: it has not been on screen since to say otherwise.
        val state = liveWeather(enabled = true, followRealTime = false, status = LiveWeatherStatus.OK)

        assertFalse(state.canBeTurnedOn)
        assertFalse("the switch read on over a fixed hour until v5.10C", state.shownOn)
        assertEquals(LiveWeatherBlocker.FIXED_HOUR, state.blocker)
        assertEquals(LiveWeatherTap.FOLLOW_REAL_TIME, SettingsUiModel.liveWeatherTap(state))
        assertFalse("an old OK must not lock World & scene", state.drivingTheScene)
    }

    @Test
    fun `only a forecast actually in effect may claim to be driving the scene`() {
        // OK and STALE are the two states with a snapshot behind them; the rest are the states in
        // which LiveWeatherStatus itself already says the theme's weather is showing.
        assertTrue(liveWeather(status = LiveWeatherStatus.OK).drivingTheScene)
        assertTrue(liveWeather(status = LiveWeatherStatus.STALE).drivingTheScene)
        for (status in listOf(
            LiveWeatherStatus.OFF,
            LiveWeatherStatus.NO_LOCATION,
            LiveWeatherStatus.MISSING_API_KEY,
            LiveWeatherStatus.FAILED,
        )) {
            assertFalse("$status is not a forecast", liveWeather(status = status).drivingTheScene)
        }
    }

    @Test
    fun `driving the scene and running on the theme's weather are exact opposites while on`() {
        // The contradiction the two screens used to show at once: World & scene said "Driven by
        // Live Weather" while Weather & time showed the fallback banner. They now read one fact.
        for (status in LiveWeatherStatus.entries) {
            val driving = liveWeather(status = status).drivingTheScene
            if (status == LiveWeatherStatus.OFF) continue // "not reported yet", claimed by neither
            assertEquals("$status", status.isRunningOnThemeWeather, !driving)
        }
    }

    @Test
    fun `the switch being off means nothing is driving the scene, whatever the last status said`() {
        for (status in LiveWeatherStatus.entries) {
            assertFalse(liveWeather(enabled = false, status = status).drivingTheScene)
        }
    }

    // ---- the two reset rows (v5.8C) ----------------------------------------------------------

    /**
     * **Each reset row counts what it resets** (v5.8C, the maintainer's decision of 2026-09-25).
     * The one row there was counted only saved versions, so a user who had edited two themes from
     * the menus read "No built-in theme has your edits" beside a disabled button (assessment v5.7
     * row 4).
     */
    @Test
    fun `edits made from the menus light the edits row, saved versions light the saved row`() {
        val themes = com.paperscrape.livewallpaper.engine.ThemeCatalog.ALL
        fun defaults(id: String) = com.paperscrape.livewallpaper.engine.defaultCustomizationFor(id)
        val autumnSaved = com.paperscrape.livewallpaper.engine.CustomThemeEntry(
            id = "autumn", name = "My autumn",
            theme = com.paperscrape.livewallpaper.engine.ThemeCatalog.byId("autumn"),
            layout = com.paperscrape.livewallpaper.engine.SceneObjectLayout(emptyList(), emptyList()),
            customization = defaults("autumn").copy(hillsVariation = 0.4f),
        )
        val state = SettingsUiModel.themeResetState(
            builtIns = themes,
            overrides = mapOf("autumn" to autumnSaved),
            themeCustomizations = mapOf(
                "beach" to defaults("beach").copy(hillsVariation = 0.2f),          // edited
                "winter" to defaults("winter"),                                    // touched, moved back
                "autumn" to autumnSaved.customization,                             // the saved version itself
                "desert" to defaults("desert").let { it.copy(stars = it.stars.copy(visible = !it.stars.visible)) }, // edited
            ),
        )
        assertEquals(listOf("Autumn"), state.savedVersions)
        assertEquals(listOf("Desert", "Beach"), state.currentEdits)   // the gallery's order
        assertEquals(listOf("desert", "beach"), state.currentEditIds)

        val none = SettingsUiModel.themeResetState(themes, emptyMap(), emptyMap())
        assertEquals(emptyList<String>(), none.savedVersions)
        assertEquals(emptyList<String>(), none.currentEdits)
    }

    @Test
    fun `a line names its themes the way a sentence would`() {
        assertEquals("", SettingsUiModel.namesInProse(emptyList()))
        assertEquals("Autumn", SettingsUiModel.namesInProse(listOf("Autumn")))
        assertEquals("Autumn and Beach", SettingsUiModel.namesInProse(listOf("Autumn", "Beach")))
        assertEquals("Autumn, Beach and Winter", SettingsUiModel.namesInProse(listOf("Autumn", "Beach", "Winter")))
    }
    // -- Automatic theme by date, with a gap in the calendar (inventory I-06, v5.9F) ------------

    /**
     * The factory calendar covers every day, and the line says so; a calendar with a gap -- which
     * the calendar screen allows, and names -- does not, and the line must not say it does. Until
     * v5.9F both screens printed "for every day of the year" whatever the calendar held.
     */
    @Test
    fun `the automatic theme line says every day only when the calendar covers every day`() {
        val factory = com.paperscrape.livewallpaper.engine.SeasonalCalendar.DEFAULT
        assertEquals(
            "The calendar picks a theme for every day of the year, overriding your own pick",
            SettingsUiModel.autoThemeLine(factory, overridingYourPick = true),
        )
        assertEquals(
            "The calendar picks a theme for every day of the year",
            SettingsUiModel.autoThemeLine(factory, overridingYourPick = false),
        )
        // Spring moved to start on 10 March: 1-9 March have no season under them.
        val gapped = factory.withSpan(
            com.paperscrape.livewallpaper.engine.CalendarWindow.SPRING,
            com.paperscrape.livewallpaper.engine.CalendarSpan(3, 10, 5, 31),
        )
        for (overriding in listOf(true, false)) {
            val line = SettingsUiModel.autoThemeLine(gapped, overridingYourPick = overriding)
            assertFalse(line, line.contains("every day of the year"))
            assertTrue(line, line.contains("the 9 days no season covers, your own pick shows unless a holiday covers them"))
            assertEquals(line, overriding, line.contains("overriding your own pick"))
        }
        val oneDay = factory.withSpan(
            com.paperscrape.livewallpaper.engine.CalendarWindow.SPRING,
            com.paperscrape.livewallpaper.engine.CalendarSpan(3, 2, 5, 31),
        )
        assertTrue(SettingsUiModel.autoThemeLine(oneDay, false).contains("On the day no season covers"))
    }

    @Test
    fun `the gallery's caption names the other days only when there are some`() {
        val factory = com.paperscrape.livewallpaper.engine.SeasonalCalendar.DEFAULT
        assertEquals(
            "While this is on, the calendar picks the theme. The theme you choose below is the one used whenever you turn it off.",
            SettingsUiModel.autoThemeCaption(factory),
        )
        val gapped = factory.withSpan(
            com.paperscrape.livewallpaper.engine.CalendarWindow.WINTER,
            com.paperscrape.livewallpaper.engine.CalendarSpan(1, 5, 2, 29),
        )
        assertTrue(SettingsUiModel.autoThemeCaption(gapped).contains("on the days it covers"))
    }

    /** Both screens read the line from here: no copy of the old fixed text is left in the UI. */
    @Test
    fun `no screen prints the fixed every-day line any more`() {
        for (screen in listOf("SettingsScreen.kt", "ThemeGalleryScreen.kt")) {
            var dir: java.io.File? = java.io.File(".").absoluteFile
            var code: String? = null
            while (dir != null && code == null) {
                for (prefix in listOf("", "app/")) {
                    val f = java.io.File(dir, "${prefix}src/main/kotlin/com/paperscrape/livewallpaper/ui/$screen")
                    if (f.isFile) code = f.readText()
                }
                dir = dir.parentFile
            }
            requireNotNull(code) { "could not locate $screen" }
            assertFalse(screen, code.contains("\"The calendar picks a theme for every day of the year"))
            assertTrue(screen, code.contains("SettingsUiModel.autoThemeLine(settings.seasonalCalendar"))
        }
    }
}
