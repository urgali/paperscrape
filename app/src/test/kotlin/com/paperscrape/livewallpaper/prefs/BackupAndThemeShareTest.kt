package com.paperscrape.livewallpaper.prefs

import com.paperscrape.livewallpaper.engine.CUSTOM_THEME_SCHEMA_VERSION
import com.paperscrape.livewallpaper.engine.CustomThemeData
import com.paperscrape.livewallpaper.engine.CustomThemeEntry
import com.paperscrape.livewallpaper.engine.ObjectVariantConfig
import com.paperscrape.livewallpaper.engine.SceneCustomization
import com.paperscrape.livewallpaper.engine.SceneObjectCatalog
import com.paperscrape.livewallpaper.engine.SceneObjectLayout
import com.paperscrape.livewallpaper.engine.customThemeDataFromJsonString
// aliased: this package has its own `toJsonString` for the two file formats.
import com.paperscrape.livewallpaper.engine.toJsonString as themeDataToJsonString
import com.paperscrape.livewallpaper.engine.ThemeCatalog
import com.paperscrape.livewallpaper.engine.defaultCustomizationFor
import com.paperscrape.livewallpaper.location.DeviceLocationKind
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import com.paperscrape.livewallpaper.engine.keptCars
import com.paperscrape.livewallpaper.engine.legacyKeepCar

/**
 * The two file formats: a whole-app backup, and a single shareable theme.
 *
 * They are tested together because the most important thing about them is that they are **not the
 * same format** — separate `kind` markers, separate schema versions, and different contents, with
 * the theme file carrying nothing personal. Half the assertions below are about that boundary.
 *
 * **No test in this file prints an API key.** The key values used are obvious fakes, and where a
 * test needs to prove a key round-tripped it compares rather than logs.
 */
class BackupAndThemeShareTest {

    // ------------------------------------------------------------------ fixtures

    private val busySettings = WallpaperSettings(
        themeId = "beach",
        syncWithRealTime = false,
        useLocationForSunTimes = true,
        useCustomLocation = true,
        deviceLocationKind = DeviceLocationKind.GPS,
        customLocationLatitude = 41.9028f,
        customLocationLongitude = 12.4964f,
        customLocationLabel = "Rome",
        liveWeatherEnabled = true,
        liveWeatherApiKey = "fake-openmeteo-key",
        weatherProviderId = "weatherapi_com",
        weatherApiComApiKey = "fake-weatherapi-key",
        openWeatherApiKey = "fake-openweather-key",
        automaticUpdateCheckEnabled = true,
        fixedHour = 6.5f,
        parallaxStrength = 1.7f,
        scrollBackground = true,
        swipeScroll = false,
        scrollSpeed = 0.42f,
        autoThemeByDate = true,
        // Runtime state a backup must not carry, set to something distinctive.
        resolvedGpsLatitude = 12f,
        resolvedGpsLongitude = 34f,
        deviceFixTimestampMillis = 999L,
        liveWeatherStatus = "ok",
        themeCustomizations = mapOf(
            "beach" to defaultCustomizationFor("beach").copy(
                houses = ObjectVariantConfig(true, 0.21f, 1, 2, 3, 4),
                hillsColorDay = 0x11223344,
            ),
            "winter" to defaultCustomizationFor("winter").copy(hillsColorNight = 0x55667788),
        ),
    )

    private fun entry(id: String, source: String, name: String) = CustomThemeEntry(
        id = id,
        name = name,
        theme = ThemeCatalog.byId(source).copy(id = id, displayName = name),
        layout = SceneObjectCatalog.layoutFor(source, ThemeCatalog.byId(source).accentColor),
        customization = defaultCustomizationFor(source).copy(hillsVariation = 0.33f),
    )

    private val savedThemes = CustomThemeData(
        overrides = mapOf("christmas" to entry("christmas", "christmas", "My Christmas")),
        customThemes = listOf(entry("custom:1", "beach", "Seaside"), entry("custom:2", "city", "Downtown")),
    )

    private fun backup() = AppBackup.from(busySettings, savedThemes, "4.3", 1_700_000_000_000L)

    // ------------------------------------------------------------------ BCK-07: embedded schema

    /**
     * A backup records which theme schema its entries are in, and honours it on the way back.
     *
     * The embedded entries are `CustomThemeEntry.toJson` documents -- the theme store's own shape --
     * but the import path parsed them directly and never ran the store's migrations. Harmless while
     * no breaking step exists after the backup format shipped; silently wrong the first time one
     * does, because a version 2 payload restored into a version 4 app would be read as version 4.
     */
    @Test
    fun `a backup records the theme schema its entries are written in`() {
        val json = JSONObject(backup().toJsonString())
        assertEquals(
            CUSTOM_THEME_SCHEMA_VERSION,
            json.optInt("customThemeSchemaVersion", -1),
        )
    }

    /**
     * **The dangerous half is the default, and this is the test that pins it.**
     *
     * The only substantive migration, `1 -> 2`, divides every static object's `scale` by its
     * category base scale, and it is not idempotent. Defaulting an unversioned backup to the legacy
     * version would run that division a second time on every backup users already have, shrinking
     * every object in every saved theme. Absent therefore means *current*.
     */
    @Test
    fun `a backup written before the field existed is read as current, not as legacy`() {
        val json = JSONObject(backup().toJsonString())
        json.remove("customThemeSchemaVersion")
        val parsed = parseAppBackup(json.toString())
        assertTrue("an unversioned backup must still parse", parsed is BackupParseResult.Ok)
        val restored = (parsed as BackupParseResult.Ok).backup.customThemeData
        for ((id, original) in savedThemes.overrides) {
            val back = restored.overrides.getValue(id)
            for ((i, obj) in original.layout.staticObjects.withIndex()) {
                assertEquals(
                    "override $id object $i must not be migrated a second time",
                    obj.scale,
                    back.layout.staticObjects[i].scale,
                    1e-5f,
                )
            }
        }
    }

    @Test
    fun `a backup that declares an older theme schema is migrated on the way in`() {
        // The case the field exists for. Version 1 asks for the scale migration, so the restored
        // objects must come back *divided* by their base scale -- proof the step actually ran.
        val json = JSONObject(backup().toJsonString())
        json.put("customThemeSchemaVersion", 1)
        val parsed = parseAppBackup(json.toString())
        assertTrue(parsed is BackupParseResult.Ok)
        val restored = (parsed as BackupParseResult.Ok).backup.customThemeData
        val original = savedThemes.overrides.getValue("christmas").layout.staticObjects
        val back = restored.overrides.getValue("christmas").layout.staticObjects
        val changed = original.indices.any { kotlin.math.abs(original[it].scale - back[it].scale) > 1e-4f }
        assertTrue("declaring version 1 must actually run the scale migration", changed)
    }

    // ------------------------------------------------------------------ backup round trip

    @Test
    fun `a backup round trips every setting it carries`() {
        val parsed = parseAppBackup(backup().toJsonString())
        assertTrue(parsed is BackupParseResult.Ok)
        assertEquals(backup().settings, (parsed as BackupParseResult.Ok).backup.settings)
    }

    @Test
    fun `a backup round trips every theme customization`() {
        val parsed = (parseAppBackup(backup().toJsonString()) as BackupParseResult.Ok).backup
        assertEquals(busySettings.themeCustomizations.keys, parsed.themeCustomizations.keys)
        for ((id, expected) in busySettings.themeCustomizations) {
            assertEquals("customization for $id", expected, parsed.themeCustomizations[id])
        }
    }

    @Test
    fun `a backup round trips overrides and standalone themes`() {
        val parsed = (parseAppBackup(backup().toJsonString()) as BackupParseResult.Ok).backup
        assertEquals(setOf("christmas"), parsed.customThemeData.overrides.keys)
        assertEquals(
            listOf("custom:1", "custom:2"),
            parsed.customThemeData.customThemes.map { it.id }.sorted(),
        )
        assertEquals(
            savedThemes.customThemes.first { it.id == "custom:1" }.customization,
            parsed.customThemeData.customThemes.first { it.id == "custom:1" }.customization,
        )
    }

    @Test
    fun `default settings round trip as faithfully as customised ones`() {
        val plain = AppBackup.from(WallpaperSettings(), CustomThemeData.EMPTY, "4.3", 0L)
        val parsed = (parseAppBackup(plain.toJsonString()) as BackupParseResult.Ok).backup
        assertEquals(plain.settings, parsed.settings)
        assertTrue(parsed.themeCustomizations.isEmpty())
        assertTrue(parsed.customThemeData.customThemes.isEmpty())
    }

    /** Keys are user settings and must survive; the assertion compares, it does not print. */
    @Test
    fun `weather api keys survive a backup round trip`() {
        val parsed = (parseAppBackup(backup().toJsonString()) as BackupParseResult.Ok).backup
        assertEquals(busySettings.liveWeatherApiKey, parsed.settings.liveWeatherApiKey)
        assertEquals(busySettings.weatherApiComApiKey, parsed.settings.weatherApiComApiKey)
        assertEquals(busySettings.openWeatherApiKey, parsed.settings.openWeatherApiKey)
        assertTrue("a backup with keys must declare itself sensitive", parsed.hasSecrets())
        assertFalse(
            "a backup without keys must not",
            AppBackup.from(WallpaperSettings(), CustomThemeData.EMPTY, "4.3", 0L).hasSecrets(),
        )
    }

    @Test
    fun `a custom location survives a backup round trip`() {
        val parsed = (parseAppBackup(backup().toJsonString()) as BackupParseResult.Ok).backup
        assertEquals(41.9028f, parsed.settings.customLocationLatitude, 0.0001f)
        assertEquals(12.4964f, parsed.settings.customLocationLongitude, 0.0001f)
        assertEquals("Rome", parsed.settings.customLocationLabel)
        assertEquals(DeviceLocationKind.GPS, parsed.settings.locationKind())
    }

    /** Runtime state is not a setting, and a backup restored onto another phone must not carry it. */
    @Test
    fun `a backup carries no runtime state`() {
        val json = backup().toJsonString()
        for (forbidden in listOf(
            "resolvedGps", "deviceFixTimestamp", "liveWeatherStatus",
            "pendingCustomization", "updatePrefs", "pendingInstall",
        )) {
            assertFalse("a backup must not carry $forbidden", json.contains(forbidden))
        }
    }

    // ------------------------------------------------------------------ backup import safety

    @Test
    fun `corrupt json is refused without a partial read`() {
        assertEquals(
            BackupImportError.NotJson,
            (parseAppBackup("{ not json at all") as BackupParseResult.Failed).error,
        )
        assertEquals(
            BackupImportError.NotJson,
            (parseAppBackup("") as BackupParseResult.Failed).error,
        )
        assertEquals(
            BackupImportError.NotJson,
            (parseAppBackup(null) as BackupParseResult.Failed).error,
        )
    }

    @Test
    fun `a shared theme file is refused as a backup, by name`() {
        val theme = ThemeShare.of("beach", "Seaside", defaultCustomizationFor("beach"), "4.3", 0L)
        val error = (parseAppBackup(theme.toJsonString()) as BackupParseResult.Failed).error
        assertEquals(BackupImportError.WrongKind(ThemeShare.DOCUMENT_KIND), error)
    }

    @Test
    fun `a backup from a newer schema is refused rather than half-read`() {
        val newer = JSONObject(backup().toJsonString())
            .put("schemaVersion", AppBackup.SCHEMA_VERSION + 1).toString()
        val error = (parseAppBackup(newer) as BackupParseResult.Failed).error
        assertEquals(BackupImportError.TooNew(AppBackup.SCHEMA_VERSION + 1, AppBackup.SCHEMA_VERSION), error)
    }

    @Test
    fun `a backup missing its settings block is refused`() {
        val broken = JSONObject(backup().toJsonString()).apply { remove("settings") }.toString()
        assertEquals(
            BackupImportError.Malformed("settings"),
            (parseAppBackup(broken) as BackupParseResult.Failed).error,
        )
    }

    /** Forward compatibility: a field this build has never heard of must not break the import. */
    @Test
    fun `unknown fields from a future build are ignored, not rejected`() {
        val withExtras = JSONObject(backup().toJsonString()).apply {
            put("somethingFromTheFuture", "hello")
            getJSONObject("settings").put("aSettingThatDoesNotExistYet", 42)
        }.toString()
        val parsed = parseAppBackup(withExtras)
        assertTrue("a future field made the import fail", parsed is BackupParseResult.Ok)
        assertEquals(backup().settings, (parsed as BackupParseResult.Ok).backup.settings)
    }

    /** A field the file omits falls back to the running default rather than to zero. */
    @Test
    fun `an incomplete settings block falls back to defaults, field by field`() {
        val stripped = JSONObject(backup().toJsonString()).apply {
            getJSONObject("settings").remove("scrollSpeed")
            getJSONObject("settings").remove("swipeScroll")
        }.toString()
        val parsed = (parseAppBackup(stripped) as BackupParseResult.Ok).backup
        assertEquals(WallpaperSettings().scrollSpeed, parsed.settings.scrollSpeed, 0.0001f)
        assertEquals(WallpaperSettings().swipeScroll, parsed.settings.swipeScroll)
        // and everything present is still exactly what the file said
        assertEquals(0.42f, backup().settings.scrollSpeed, 0.0001f)
        assertEquals("Rome", parsed.settings.customLocationLabel)
    }

    // ------------------------------------------------------------------ theme share

    @Test
    fun `three different built-in themes round trip whole`() {
        for (id in listOf("beach", "winter", "halloween")) {
            val custom = defaultCustomizationFor(id).copy(
                hillsColorDay = 0x0A0B0C0D,
                cars = ObjectVariantConfig(true, 0.5f, 9, 8, 7, 6),
                santaEnabled = true,
            )
            val share = ThemeShare.of(id, "Shared $id", custom, "4.3", 7L)
            val parsed = parseThemeShare(share.toJsonString())
            assertTrue("$id did not parse", parsed is ThemeParseResult.Ok)
            val back = (parsed as ThemeParseResult.Ok).share
            assertEquals("$id name", "Shared $id", back.name)
            assertEquals("$id source", id, back.sourceThemeId)
            assertEquals("$id customization", custom, back.customization)
            assertEquals("$id sky", share.theme.skyDay.toList(), back.theme.skyDay.toList())
            assertEquals("$id static objects", share.layout.staticObjects.size, back.layout.staticObjects.size)
            assertEquals("$id cars", share.layout.cars.size, back.layout.cars.size)
        }
    }

    @Test
    fun `a standalone custom theme round trips whole`() {
        val source = entry("custom:1", "beach", "Seaside")
        val share = ThemeShare(
            schemaVersion = ThemeShare.SCHEMA_VERSION,
            appVersionName = "4.3",
            exportedAtMillis = 1L,
            sourceThemeId = source.id,
            name = source.name,
            theme = source.theme,
            layout = source.layout,
            customization = source.customization,
        )
        val back = (parseThemeShare(share.toJsonString()) as ThemeParseResult.Ok).share
        assertEquals(source.customization, back.customization)
        assertEquals(source.layout.staticObjects.size, back.layout.staticObjects.size)
        assertEquals(source.theme.accentColor, back.theme.accentColor)
    }

    /** Every field of a customization, not a sample of them. */
    @Test
    fun `every scene customization property survives a theme round trip`() {
        val everything = defaultCustomizationFor("beach").copy(
            houses = ObjectVariantConfig(false, 0.11f, 1, 2, 3, 4),
            buildings = ObjectVariantConfig(true, 0.22f, 5, 6, 7, 8),
            cars = ObjectVariantConfig(false, 0.33f, 9, 10, 11, 12),
            parasols = ObjectVariantConfig(true, 0.44f, 13, 14, 15, 16),
            people = ObjectVariantConfig(false, 0.55f, 17, 18, 19, 20),
            peopleNightDensity = 0.66f,
            trees = ObjectVariantConfig(true, 0.77f, 21, 22, 23, 24),
            snowmen = ObjectVariantConfig(true, 0.12f, 25, 26, 27, 28),
            gifts = ObjectVariantConfig(true, 0.13f, 29, 30, 31, 32),
            penguins = ObjectVariantConfig(true, 0.14f, 33, 34, 35, 36),
            bunnies = ObjectVariantConfig(true, 0.15f, 37, 38, 39, 40),
            easterEggs = ObjectVariantConfig(true, 0.16f, 41, 42, 43, 44),
            pumpkins = ObjectVariantConfig(true, 0.17f, 45, 46, 47, 48),
            hillsVariation = 0.88f,
            hillsColorDay = 101,
            hillsColorNight = 102,
            fallColorsEnabled = true,
            winterColorsEnabled = true,
            christmasDecorationsEnabled = true,
            flowersEnabled = true,
            halloweenEnabled = true,
            horrorSkyEnabled = true,
            santaEnabled = true,
        )
        val share = ThemeShare.of("beach", "Everything", everything, "4.3", 0L)
        val back = (parseThemeShare(share.toJsonString()) as ThemeParseResult.Ok).share
        assertEquals(everything, back.customization)
    }

    /** The file must be shareable without sharing anything about its author. */
    @Test
    fun `a shared theme carries nothing personal`() {
        val json = ThemeShare.of("beach", "Seaside", defaultCustomizationFor("beach"), "4.3", 0L).toJsonString()
        for (forbidden in listOf(
            "ApiKey", "apiKey", "customLocation", "Latitude", "Longitude",
            "scrollSpeed", "swipeScroll", "parallax", "autoThemeByDate",
            "automaticUpdateCheck", "liveWeather", "syncWithRealTime",
        )) {
            assertFalse("a shared theme must not carry $forbidden", json.contains(forbidden))
        }
    }

    @Test
    fun `a backup file is refused as a theme, by name`() {
        val error = (parseThemeShare(backup().toJsonString()) as ThemeParseResult.Failed).error
        assertEquals(ThemeImportError.WrongKind(AppBackup.DOCUMENT_KIND), error)
    }

    @Test
    fun `a theme from a newer schema is refused`() {
        val newer = JSONObject(
            ThemeShare.of("beach", "S", defaultCustomizationFor("beach"), "4.3", 0L).toJsonString(),
        ).put("schemaVersion", ThemeShare.SCHEMA_VERSION + 1).toString()
        assertEquals(
            ThemeImportError.TooNew(ThemeShare.SCHEMA_VERSION + 1, ThemeShare.SCHEMA_VERSION),
            (parseThemeShare(newer) as ThemeParseResult.Failed).error,
        )
    }

    @Test
    fun `a theme missing its scene is refused`() {
        val base = ThemeShare.of("beach", "S", defaultCustomizationFor("beach"), "4.3", 0L).toJsonString()
        assertEquals(
            ThemeImportError.Malformed("theme"),
            (parseThemeShare(JSONObject(base).apply { remove("theme") }.toString()) as ThemeParseResult.Failed).error,
        )
        assertEquals(
            ThemeImportError.Malformed("layout"),
            (parseThemeShare(JSONObject(base).apply { remove("layout") }.toString()) as ThemeParseResult.Failed).error,
        )
    }

    @Test
    fun `unknown fields in a theme file are ignored`() {
        val share = ThemeShare.of("beach", "Seaside", defaultCustomizationFor("beach"), "4.3", 0L)
        val withExtras = JSONObject(share.toJsonString()).put("futureField", listOf(1, 2, 3).toString()).toString()
        val parsed = parseThemeShare(withExtras)
        assertTrue(parsed is ThemeParseResult.Ok)
        assertEquals(share.customization, (parsed as ThemeParseResult.Ok).share.customization)
    }

    // ------------------------------------------------------------------ importing a theme

    /**
     * An imported theme is a new theme, and it stays working when the built-in it came from moves.
     *
     * The second half is the point of shipping the resolved scene rather than a reference: the
     * entry's own theme and layout are compared with the *packaged* ones, not looked up again.
     */
    @Test
    fun `an imported theme is new, independent and never an overwrite`() {
        val share = ThemeShare.of("beach", "Seaside", defaultCustomizationFor("beach").copy(hillsColorDay = 7), "4.3", 0L)
        val imported = share.asNewCustomTheme("custom:99")

        assertEquals("custom:99", imported.id)
        assertNotEquals("an import must not reuse the source id", share.sourceThemeId, imported.id)
        assertEquals("custom:99", imported.theme.id)
        assertEquals(share.customization, imported.customization)
        assertEquals(share.layout.staticObjects.size, imported.layout.staticObjects.size)
        assertEquals(share.theme.skyNight.toList(), imported.theme.skyNight.toList())
    }

    @Test
    fun `importing the same file twice gives two themes, not one replaced`() {
        val share = ThemeShare.of("beach", "Seaside", defaultCustomizationFor("beach"), "4.3", 0L)
        val a = share.asNewCustomTheme("custom:1")
        val b = share.asNewCustomTheme("custom:2")
        assertNotEquals(a.id, b.id)
        assertEquals(a.customization, b.customization)
    }

    @Test
    fun `a duplicate name is disambiguated rather than refused`() {
        assertEquals("Seaside", uniqueThemeName("Seaside", emptyList()))
        assertEquals("Seaside (2)", uniqueThemeName("Seaside", listOf("Seaside")))
        assertEquals("Seaside (3)", uniqueThemeName("Seaside", listOf("Seaside", "Seaside (2)")))
        assertEquals("Other", uniqueThemeName("Other", listOf("Seaside", "Seaside (2)")))
    }

    // ------------------------------------------------------ the car inventory travels whole

    /**
     * A theme saved at a low car density must not arrive somewhere else with an empty road.
     *
     * The defect this guards was in the *save* path, not in either file format — but both formats
     * carry a `SceneObjectLayout`, so both are places a thinned car list could be laundered into
     * looking canonical. The assertion is the same on each side: the inventory is whole, the
     * customization still says 10%, and `hasRoad` is true.
     */
    @Test
    fun `a theme saved at a low car density keeps its whole car inventory through a share round trip`() {
        val thinned = defaultCustomizationFor("beach").let { it.copy(cars = it.cars.copy(density = 0.1f)) }
        val raw = SceneObjectCatalog.layoutFor("beach", ThemeCatalog.byId("beach").accentColor)
        val share = ThemeShare.of("beach", "Thinned beach", thinned, "4.3", 0L)

        val back = (parseThemeShare(share.toJsonString()) as ThemeParseResult.Ok).share
        assertEquals("the shared car inventory was thinned", raw.cars.size, back.layout.cars.size)
        assertEquals("the shared density was not preserved", 0.1f, back.customization.cars.density, 0.0001f)
        assertTrue("a shared theme arrived without a road", back.layout.cars.isNotEmpty())

        val imported = back.asNewCustomTheme("custom:imported")
        assertEquals("the imported theme lost its car inventory", raw.cars.size, imported.layout.cars.size)
        assertEquals("the imported theme lost the density", 0.1f, imported.customization.cars.density, 0.0001f)
        assertTrue("the imported theme has no road", imported.layout.cars.isNotEmpty())
        // and it still shows only the 10% of traffic the author had on screen
        assertTrue(
            "the import put all the traffic back",
            imported.customization.keptCars(imported.layout.cars, imported.theme.id.hashCode()).size < raw.cars.size,
        )
    }

    /** The same, through a whole-app backup, which carries overrides and standalone themes. */
    @Test
    fun `a backup preserves a saved theme's whole car inventory`() {
        val thinned = defaultCustomizationFor("beach").let { it.copy(cars = it.cars.copy(density = 0.1f)) }
        val raw = SceneObjectCatalog.layoutFor("beach", ThemeCatalog.byId("beach").accentColor)
        val entry = CustomThemeEntry(
            id = "beach",
            name = "Beach",
            theme = ThemeCatalog.byId("beach"),
            layout = raw,
            customization = thinned,
        )
        val withTheme = AppBackup.from(
            WallpaperSettings(),
            CustomThemeData(overrides = mapOf("beach" to entry), customThemes = listOf(entry.copy(id = "custom:1"))),
            "4.3",
            0L,
        )
        val parsed = (parseAppBackup(withTheme.toJsonString()) as BackupParseResult.Ok).backup
        assertEquals(raw.cars.size, parsed.customThemeData.overrides.getValue("beach").layout.cars.size)
        assertEquals(raw.cars.size, parsed.customThemeData.customThemes.single().layout.cars.size)
        assertEquals(
            0.1f,
            parsed.customThemeData.overrides.getValue("beach").customization.cars.density,
            0.0001f,
        )
    }

    /** And a damaged override inside a backup is repaired the moment it reaches the store. */
    @Test
    fun `a damaged override restored from a backup is repaired on load`() {
        val raw = SceneObjectCatalog.layoutFor("beach", ThemeCatalog.byId("beach").accentColor)
        val damaged = CustomThemeEntry(
            id = "beach",
            name = "Beach",
            theme = ThemeCatalog.byId("beach"),
            layout = SceneObjectLayout(staticObjects = raw.staticObjects, cars = emptyList()),
            customization = defaultCustomizationFor("beach"),
        )
        val data = CustomThemeData(overrides = mapOf("beach" to damaged))
        // What BackupRepository.import writes, then what the next read of the store produces.
        val reloaded = customThemeDataFromJsonString(data.themeDataToJsonString())
        assertEquals(raw.cars.size, reloaded.overrides.getValue("beach").layout.cars.size)
    }

    /**
     * A backup carrying a *thinned* override is repaired on load too (v4.4).
     *
     * The empty list was the visible half of the defect; a list the old save path merely thinned
     * is the other half, and a backup taken before the fix is exactly how one arrives on an
     * install that never had the damage itself. The repair runs where every reader goes through,
     * so restoring is enough — nothing in `BackupRepository` needs to know about it.
     */
    @Test
    fun `a thinned override restored from a backup is repaired on load`() {
        val raw = SceneObjectCatalog.layoutFor("beach", ThemeCatalog.byId("beach").accentColor)
        val thinned = defaultCustomizationFor("beach").let { it.copy(cars = it.cars.copy(density = 0.5f)) }
        // Exactly what the pre-v4.3 save path wrote: the filtered list and the density it filtered with.
        val damaged = CustomThemeEntry(
            id = "beach",
            name = "Beach",
            theme = ThemeCatalog.byId("beach"),
            layout = SceneObjectLayout(
                staticObjects = raw.staticObjects,
                // legacyKeepCar: the fixture reconstructs what the pre-v4.3 save path wrote, and
                // that path filtered with the threshold selection of its own era.
                cars = raw.cars.filter { thinned.legacyKeepCar(it) },
            ),
            customization = thinned,
        )
        assertTrue("the fixture is not thinned", damaged.layout.cars.size in 1 until raw.cars.size)

        val backup = AppBackup.from(
            WallpaperSettings(),
            CustomThemeData(overrides = mapOf("beach" to damaged)),
            "4.4",
            0L,
        )
        val parsed = (parseAppBackup(backup.toJsonString()) as BackupParseResult.Ok).backup
        // What the next read of the store produces, which is where the repair lives.
        val reloaded = customThemeDataFromJsonString(parsed.customThemeData.themeDataToJsonString())
        val restored = reloaded.overrides.getValue("beach")
        assertEquals("the thinned inventory was not restored", raw.cars.size, restored.layout.cars.size)
        assertEquals("the restore rewrote the density", 0.5f, restored.customization.cars.density, 0.0001f)
        assertEquals("the restore rewrote the customization", thinned, restored.customization)
    }

    @Test
    fun `the two formats do not share a version number or a marker`() {
        assertNotEquals(AppBackup.DOCUMENT_KIND, ThemeShare.DOCUMENT_KIND)
        val backupJson = JSONObject(backup().toJsonString())
        val themeJson = JSONObject(
            ThemeShare.of("beach", "S", defaultCustomizationFor("beach"), "4.3", 0L).toJsonString(),
        )
        assertEquals(AppBackup.DOCUMENT_KIND, backupJson.getString("kind"))
        assertEquals(ThemeShare.DOCUMENT_KIND, themeJson.getString("kind"))
    }
}
