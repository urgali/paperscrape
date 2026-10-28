package com.paperscrape.livewallpaper.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * A saved custom theme: either a full replacement for one of the built-in themes (its [id]
 * matches a [ThemeCatalog.ALL] id, e.g. "christmas") or a fully independent theme the user
 * created from scratch (its [id] looks like "custom:<token>").
 *
 * [theme] and [layout] are complete snapshots of what is drawn. The seasonal decorations the
 * layout carries are read back by [SceneObjectCatalog.layoutFor] (`withSeasonalDecorations`), which
 * lays the whole set of the built-in they came from. Deleting an override is enough for
 * [ThemeCatalog.byId] to fall back to the built-in (the gallery's reset also clears the theme's menu
 * edits, `resetBuiltinToDefault`).
 */
data class CustomThemeEntry(
    val id: String,
    /**
     * The name the user gave this theme, and **the one every screen shows**: the screens read
     * [theme]'s `displayName` ([SceneTheme.displayName]), which [customThemeEntryFromJson] takes from
     * here, and `CustomThemeStore.renameCustomTheme` writes both. A file whose two names differ (a
     * rename that wrote this field alone) is read with this one.
     */
    val name: String,
    val theme: SceneTheme,
    val layout: SceneObjectLayout,
    /** This entry's own scene-object customization (density/visibility/colors), captured at
     * save time. Kept per-entry rather than global so saving one theme's look never affects any
     * other theme's appearance. */
    val customization: SceneCustomization = SceneCustomization.DEFAULT,
)

/** Everything persisted by [com.paperscrape.livewallpaper.prefs.CustomThemeStore]. */
data class CustomThemeData(
    /** Keyed by the built-in themeId being overridden (e.g. "christmas" -> user's version). */
    val overrides: Map<String, CustomThemeEntry> = emptyMap(),
    /** Fully independent user-created themes, unrelated to any built-in id. */
    val customThemes: List<CustomThemeEntry> = emptyList(),
    /**
     * Saved themes this build could not read, carried so that no write can lose them. Nothing
     * draws or lists them; [toJsonString] writes them back byte for byte. See
     * [UnreadableThemeEntry].
     */
    val unreadable: List<UnreadableThemeEntry> = emptyList(),
) {
    companion object {
        val EMPTY = CustomThemeData()
    }
}

/**
 * A saved theme this build cannot read, kept exactly as it was stored.
 *
 * The reader reads entry by entry and carries an entry that fails here, as text, because those bytes
 * are the user's only copy: skipped, the next save would write the document back without it; and
 * one bad entry must not empty the whole document, losing every saved theme.
 *
 * [toJsonString] writes it into a section of its own, `unreadableEntries`: where the entry came from
 * (`overrides` and its key, or `customThemes`), the schema version its bytes were written in, and the
 * entry verbatim. Not in its old place, because the rewritten document is stamped with the current
 * schema -- a later build that learned to read the entry would skip its migrations (1 -> 2 changes
 * what `scale` means) -- and because an override kept under its key would collide with a new one
 * the user saves for the same built-in.
 *
 * Every read tries each item again, migrating it from the version it records; one that reads, and
 * whose place is free, rejoins the saved themes and the next write puts it back where it came from
 * (the path of a later build that restores an object type or relaxes a field). [itemJson] is the
 * whole item as it stands, so every later write leaves it byte-identical.
 */
data class UnreadableThemeEntry(val itemJson: String) {

    /** `"overrides"` or `"customThemes"`, or `null` when the item itself cannot be read. */
    val from: String? get() = parsedItem()?.optString("from")?.takeIf { it.isNotEmpty() }

    /** The built-in id the entry overrode, for an item from `overrides`. */
    val key: String? get() = parsedItem()?.takeIf { it.has("key") }?.optString("key")

    /** The schema its bytes were written in, or `null` when the item does not say. */
    val schemaVersion: Int? get() = parsedItem()?.takeIf { it.has("schemaVersion") }?.optInt("schemaVersion")

    /** The entry exactly as it was stored, or `null` when the item has no `entry`. */
    val entryJson: String? get() = runCatching {
        JsonSpans.objectMembers(itemJson, 0).lastOrNull { it.key == "entry" }?.let { itemJson.substring(it.start, it.end) }
    }.getOrNull()

    private fun parsedItem(): JSONObject? = runCatching { JSONObject(itemJson) }.getOrNull()

    companion object {
        internal const val FROM_OVERRIDES = "overrides"
        internal const val FROM_CUSTOM_THEMES = "customThemes"

        /** An item for an entry read as unreadable just now; [entryText] goes in unchanged. */
        internal fun of(from: String, key: String?, schemaVersion: Int, entryText: String): UnreadableThemeEntry =
            UnreadableThemeEntry(
                buildString {
                    append("{\"from\":").append(JSONObject.quote(from))
                    if (key != null) append(",\"key\":").append(JSONObject.quote(key))
                    append(",\"schemaVersion\":").append(schemaVersion)
                    append(",\"entry\":").append(entryText)
                    append('}')
                },
            )
    }
}

// --- JSON (de)serialization --------------------------------------------------------------
// Hand-rolled with org.json (built into Android, no extra dependency) rather than a
// serialization library, since the data shapes here are small and stable.

fun SceneTheme.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("displayName", displayName)
    put("skyNight", JSONArray(skyNight.toList()))
    put("skyDawn", JSONArray(skyDawn.toList()))
    put("skyDay", JSONArray(skyDay.toList()))
    put("skyDusk", JSONArray(skyDusk.toList()))
    put("hillColorsDay", JSONArray(hillColorsDay.toList()))
    put("hillColorsNight", JSONArray(hillColorsNight.toList()))
    put("sunColor", sunColor)
    put("moonColor", moonColor)
    put("starColor", starColor)
    put("accentColor", accentColor)
    put("hasFireworks", hasFireworks)
    put("hasSantaSleigh", hasSantaSleigh)
}

private fun JSONArray.toIntArray(): IntArray = IntArray(length()) { getInt(it) }

/**
 * A required number that is finite **as the `Float` it is stored as**, or the document is malformed.
 *
 * Android's `org.json` reads the string `"NaN"` as [Double.NaN] from `getDouble`, and `Infinity`
 * likewise. Let through, the value would reach the renderer as a coordinate that is not a number --
 * an object drawn nowhere -- and be **persisted**, surviving restarts and re-exports. Both import
 * paths wrap parsing in `runCatching`, so throwing reports the file as malformed and refuses it.
 * The optional fields fall back instead ([optFinite]).
 */
internal fun JSONObject.requireFinite(name: String): Float {
    val value = getDouble(name)
    // Checked after the narrowing: `1e300` is a finite Double and an infinite Float, which the next
    // `JSONObject.put` (the import's staging copy) refuses with "Forbidden numeric value", closing the
    // app. NaN narrows to NaN, so one check covers both.
    val narrowed = value.toFloat()
    require(narrowed.isFinite()) { "$name is not a finite 32-bit number: $value" }
    return narrowed
}

/**
 * An optional number; anything that is not finite as a `Float` reads as absent and takes [fallback]:
 * a `"NaN"` density is no reason to refuse a whole backup. Checked after the narrowing, as in
 * [requireFinite].
 */
internal fun JSONObject.optFinite(name: String, fallback: Float): Float {
    val narrowed = optDouble(name, fallback.toDouble()).toFloat()
    return if (narrowed.isFinite()) narrowed else fallback
}

fun sceneThemeFromJson(json: JSONObject): SceneTheme = SceneTheme(
    id = json.getString("id"),
    displayName = json.getString("displayName"),
    skyNight = json.getJSONArray("skyNight").toIntArray(),
    skyDawn = json.getJSONArray("skyDawn").toIntArray(),
    skyDay = json.getJSONArray("skyDay").toIntArray(),
    skyDusk = json.getJSONArray("skyDusk").toIntArray(),
    hillColorsDay = json.getJSONArray("hillColorsDay").toIntArray(),
    hillColorsNight = json.getJSONArray("hillColorsNight").toIntArray(),
    sunColor = json.getInt("sunColor"),
    moonColor = json.getInt("moonColor"),
    starColor = json.getInt("starColor"),
    accentColor = json.getInt("accentColor"),
    hasFireworks = json.optBoolean("hasFireworks", false),
    hasSantaSleigh = json.optBoolean("hasSantaSleigh", false),
)

fun StaticSceneObject.toJson(): JSONObject = JSONObject().apply {
    put("type", type.name)
    put("depthFraction", depthFraction.toDouble())
    put("tileFractionX", tileFractionX.toDouble())
    put("scale", scale.toDouble())
    put("silhouette", silhouette)
}

fun staticSceneObjectFromJson(json: JSONObject): StaticSceneObject = StaticSceneObject(
    type = SceneObjectType.valueOf(json.getString("type")),
    // Files written before `depthFraction` carry the discrete row `layer` (0..8), read as the same fraction.
    depthFraction = if (json.has("depthFraction")) {
        json.requireFinite("depthFraction")
    } else {
        json.optInt("layer", 0) / 8f
    },
    tileFractionX = json.requireFinite("tileFractionX"),
    scale = json.optFinite("scale", 1f),
    // Files written before `silhouette` have none: `UNDEALT` hashes the drawing from the position, as the
    // build that saved them did, so they load as the same street with no migration.
    silhouette = json.optInt("silhouette", StaticSceneObject.UNDEALT),
)

fun CarObject.toJson(): JSONObject = JSONObject().apply {
    put("laneYFraction", laneYFraction.toDouble())
    put("speedFraction", speedFraction.toDouble())
    put("startDelaySeconds", startDelaySeconds.toDouble())
    put("color", color)
    put("reverse", reverse)
    put("type", type.name)
}

fun carObjectFromJson(json: JSONObject): CarObject = CarObject(
    laneYFraction = json.requireFinite("laneYFraction"),
    speedFraction = json.requireFinite("speedFraction"),
    startDelaySeconds = json.requireFinite("startDelaySeconds"),
    color = json.getInt("color"),
    reverse = json.optBoolean("reverse", false),
    // A file without `type`, or with an unknown one, reads as CarType.PLAIN.
    type = runCatching { CarType.valueOf(json.optString("type", "PLAIN")) }.getOrDefault(CarType.PLAIN),
)

/**
 * **The layout as a saved theme, a theme file or a backup holds it: without the scarecrows.** They stand on the snowmen's
 * places and `SceneObjectCatalog.layoutFor` lays them again at load; a build that does not know the type would refuse the
 * whole layout. The other seasonal decorations stay in the saved street, so a theme saved over itself, under a new name or
 * shared stands them as the theme it came from did (`DESIGN_NOTES.md` §12, decision 89). [toJson] writes this,
 * and a theme saved or shared is built from it.
 */
fun SceneObjectLayout.asSaved(): SceneObjectLayout =
    if (staticObjects.none { it.type == SceneObjectType.SCARECROW }) this
    else copy(staticObjects = staticObjects.filter { it.type != SceneObjectType.SCARECROW })

fun SceneObjectLayout.toJson(): JSONObject = JSONObject().apply {
    // A place the loader moved is written where the theme stored it ([SceneObjectLayout.placesAsSaved]); the scarecrows
    // are not written ([asSaved]).
    put("staticObjects", JSONArray(asSaved().staticObjects.map { (placesAsSaved[it] ?: it).toJson() }))
    put("cars", JSONArray(cars.map { it.toJson() }))
    // Which density rule thinned these objects; a payload without it was thinned with the grid.
    put("densityScheme", densityScheme)
}

fun sceneObjectLayoutFromJson(json: JSONObject): SceneObjectLayout {
    val staticArray = json.getJSONArray("staticObjects")
    val staticObjects = (0 until staticArray.length()).map { staticSceneObjectFromJson(staticArray.getJSONObject(it)) }
    val carsArray = json.getJSONArray("cars")
    val cars = (0 until carsArray.length()).map { carObjectFromJson(carsArray.getJSONObject(it)) }
    // Traffic geometry is recomputed on **every** load, whatever the schema: a stored lane is a copy of a
    // SceneSpace constant, and a copy that disagrees with today's road would drag the painted strip (derived
    // from the layout's lanes) to where it was saved. A migration would fix only the payloads older than
    // itself (SceneObjectCatalog.canonicaliseTraffic).
    // Likewise each place is moved, on every load, off any of the six buildings it would cross
    // (SceneObjectCatalog.placesClearedOnSavedStreet: files can hold places planned for one building), and a
    // place moved remembers where it was stored.
    val cleared = SceneObjectCatalog.placesClearedOnSavedStreet(staticObjects)
    return SceneObjectLayout(
        staticObjects = cleared,
        placesAsSaved = cleared.indices.filter { cleared[it] != staticObjects[it] }.associate { cleared[it] to staticObjects[it] },
        cars = SceneObjectCatalog.canonicaliseTraffic(cars),
        // Absent: a layout written before the key, thinned by the grid and kept on it, so it keeps
        // every object it stored ([DENSITY_SCHEME_GRID]).
        densityScheme = json.optInt("densityScheme", DENSITY_SCHEME_GRID),
    )
}

fun ObjectVariantConfig.toJson(): JSONObject = JSONObject().apply {
    put("visible", visible)
    put("density", density.toDouble())
    put("colorDay1", colorDay1)
    put("colorNight1", colorNight1)
    put("colorDay2", colorDay2)
    put("colorNight2", colorNight2)
    // Written as the storage id; a file without it reads as MANUAL, the colours as stored.
    put("autoMode1", autoMode1.storageId)
    put("autoMode2", autoMode2.storageId)
}

fun objectVariantConfigFromJson(json: JSONObject, default: ObjectVariantConfig): ObjectVariantConfig = ObjectVariantConfig(
    visible = json.optBoolean("visible", default.visible),
    density = json.optFinite("density", default.density),
    // `optInt` with the default, as for the mountains, the lake, the sky, the hills and the birds:
    // `getInt` throws on a present value that is not an integer (`"colorDay1":"nope"`, a truncated
    // download), and these parsers run inside a Compose `scope.launch { }` with no catch, so it would
    // close the settings screen (`ImportParserFuzzTest`); `optInt` with no fallback would read it as
    // 0, transparent. Absent and unreadable take the same path. The theme and car colours use `getInt`
    // on purpose: it throws inside the importers' `runCatching`, a refused file rather than a wrong one.
    colorDay1 = json.optInt("colorDay1", default.colorDay1),
    colorNight1 = json.optInt("colorNight1", default.colorNight1),
    colorDay2 = json.optInt("colorDay2", default.colorDay2),
    colorNight2 = json.optInt("colorNight2", default.colorNight2),
    autoMode1 = AutoColorMode.fromStorageId(json.optString("autoMode1")),
    autoMode2 = AutoColorMode.fromStorageId(json.optString("autoMode2")),
)

fun SceneCustomization.toJson(): JSONObject = JSONObject().apply {
    put("houses", houses.toJson())
    put("buildings", buildings.toJson())
    // A payload without `shops` or `publics` is read as `sceneCustomizationFromJson` says.
    put("shops", shops.toJson())
    put("publics", publics.toJson())
    put("cars", cars.toJson())
    put("parasols", parasols.toJson())
    put("people", people.toJson())
    // Beside the people and cars blocks rather than inside them: they belong to the pedestrians and the
    // traffic, not to the ObjectVariantConfig every category shares. A payload without them reads its
    // own daytime density (PeopleDensity.resolveNightDensity's rule).
    put("peopleNightDensity", peopleNightDensity.toDouble())
    put("carsNightDensity", carsNightDensity.toDouble())
    // The opening hours, in three groups; a payload without them reads the defaults (off), and one with
    // the single `businessHours*` setting reads it into each group (`sceneCustomizationFromJson`).
    put("shopHoursEnabled", shopHoursEnabled)
    put("shopOpenHour", shopOpenHour.toDouble())
    put("shopCloseHour", shopCloseHour.toDouble())
    put("towerHoursEnabled", towerHoursEnabled)
    put("towerOpenHour", towerOpenHour.toDouble())
    put("towerCloseHour", towerCloseHour.toDouble())
    put("publicHoursEnabled", publicHoursEnabled)
    put("publicOpenHour", publicOpenHour.toDouble())
    put("publicCloseHour", publicCloseHour.toDouble())
    // The single setting older builds know, still written as the shops', so the shape stays additive
    // ([CUSTOM_THEME_SCHEMA_VERSION]: no field removed, no bump) and an older build reads hours it knows
    // (a newer payload is read best-effort, never rejected). Read here only where a group has no field of its own.
    put("businessHoursEnabled", shopHoursEnabled)
    put("businessOpenHour", shopOpenHour.toDouble())
    put("businessCloseHour", shopCloseHour.toDouble())
    put("trees", trees.toJson())
    put("snowmen", snowmen.toJson())
    put("gifts", gifts.toJson())
    put("penguins", penguins.toJson())
    put("bunnies", bunnies.toJson())
    put("easterEggs", easterEggs.toJson())
    put("pumpkins", pumpkins.toJson())
    put("scarecrows", scarecrows.toJson())
    put("hillsVariation", hillsVariation.toDouble())
    put("snowPiles", snowPiles.toDouble())
    put("leafPiles", leafPiles.toDouble())
    put("hillsColorDay", hillsColorDay)
    put("hillsColorNight", hillsColorNight)
    put("hillsAutoMode", hillsAutoMode.storageId)
    put("mountainsFront", JSONObject().apply {
        put("visible", mountainsFront.visible)
        put("density", mountainsFront.density.toDouble())
        put("colorDay", mountainsFront.colorDay)
        put("colorNight", mountainsFront.colorNight)
        put("autoMode", mountainsFront.autoMode.storageId)
    })
    put("mountainsBack", JSONObject().apply {
        put("visible", mountainsBack.visible)
        put("density", mountainsBack.density.toDouble())
        put("colorDay", mountainsBack.colorDay)
        put("colorNight", mountainsBack.colorNight)
        put("autoMode", mountainsBack.autoMode.storageId)
    })
    // A payload without the distant houses reads them off.
    put("distantHouses", JSONObject().apply {
        put("visible", distantHouses.visible)
        put("density", distantHouses.density.toDouble())
    })
    put("lake", JSONObject().apply {
        put("visible", lake.visible)
        put("colorDay", lake.colorDay)
        put("colorNight", lake.colorNight)
        put("height", lake.height.toDouble())
        put("sailboatsVisible", lake.sailboatsVisible)
        put("sailboatsDensity", lake.sailboatsDensity.toDouble())
        put("dolphinsVisible", lake.dolphinsVisible)
        put("dolphinsDensity", lake.dolphinsDensity.toDouble())
        put("autoMode", lake.autoMode.storageId)
    })
    put("birds", JSONObject().apply {
        put("visible", birds.visible)
        put("density", birds.density.toDouble())
        put("nightBirds", birds.nightBirds)
        put("colors", org.json.JSONArray().apply {
            birds.colors.forEach { c ->
                put(JSONObject().apply { put("color", c.color); put("weight", c.weight.toDouble()) })
            }
        })
    })
    put("stars", JSONObject().apply { put("visible", stars.visible); put("density", stars.density.toDouble()) })
    put("sky", JSONObject().apply {
        put("colorDayHigh", sky.colorDayHigh); put("colorDayLow", sky.colorDayLow)
        put("colorNightHigh", sky.colorNightHigh); put("colorNightLow", sky.colorNightLow)
        put("colorSunriseLow", sky.colorSunriseLow); put("colorSunsetLow", sky.colorSunsetLow)
        put("sunCloudHeight", sky.sunCloudHeight.toDouble())
        put("autoModeHigh", sky.autoModeHigh.storageId); put("autoModeLow", sky.autoModeLow.storageId)
    })
    put("sun", JSONObject().apply { put("visible", sun.visible); put("color", sun.color) })
    put("moon", JSONObject().apply { put("visible", moon.visible); put("color", moon.color); put("realisticPhases", moon.realisticPhases) })
    put("clouds", JSONObject().apply {
        put("visible", clouds.visible); put("density", clouds.density.toDouble())
        put("colorDay", clouds.colorDay); put("colorNight", clouds.colorNight)
        put("autoMode", clouds.autoMode.storageId)
    })
    put("precipitation", JSONObject().apply {
        put("visible", precipitation.visible)
        put("type", precipitation.type.name)
        put("intensity", precipitation.intensity.toDouble())
        put("rainColorDay", precipitation.rainColorDay)
        put("rainColorNight", precipitation.rainColorNight)
        put("snowColorDay", precipitation.snowColorDay)
        put("snowColorNight", precipitation.snowColorNight)
        put("thunderstorm", precipitation.thunderstorm)
        put("rainAutoMode", precipitation.rainAutoMode.storageId)
        put("snowAutoMode", precipitation.snowAutoMode.storageId)
    })
    put("rainbow", JSONObject().apply {
        put("visible", rainbow.visible); put("opacity", rainbow.opacity.toDouble())
    })
    put("fallColorsEnabled", fallColorsEnabled)
    put("winterColorsEnabled", winterColorsEnabled)
    // *Lights on the buildings* is stored under the name older builds read their one Christmas switch from.
    put("christmasDecorationsEnabled", christmasBuildingLightsEnabled)
    put("christmasTreeLightsEnabled", christmasTreeLightsEnabled)
    put("christmasFirsEnabled", christmasFirsEnabled)
    put("flowersEnabled", flowersEnabled)
    put("palmsEnabled", palmsEnabled)
    put("palmsInsteadOfTrees", palmsInsteadOfTrees)
    put("halloweenEnabled", halloweenEnabled)
    put("horrorSkyEnabled", horrorSkyEnabled)
    put("laughingMoonEnabled", laughingMoonEnabled)
    put("nightSkyByDayEnabled", nightSkyByDayEnabled)
    put("stillMoonEnabled", stillMoonEnabled)
    put("halloweenDecorationsEnabled", halloweenDecorationsEnabled)
    put("bloodRainEnabled", bloodRainEnabled)
    put("headlessHorsemanEnabled", headlessHorsemanEnabled)
    put("santaEnabled", santaEnabled)
}

/**
 * A [SceneCustomization] read back from [json] -- a theme's archived edit, a saved theme, a theme file,
 * a backup -- best effort, field by field ([CUSTOM_THEME_SCHEMA_VERSION]). [theme] is the theme the
 * payload belongs to, when the caller knows it: a payload without public buildings' colours reads them
 * against that theme's starting ones ([publicsCarriedOver]). [layout] is the street a saved theme holds; a
 * payload with both the Christmas and the Halloween decorations on is judged on it, or on the street
 * [theme] draws ([DecorationExclusion.resolved]).
 */
fun sceneCustomizationFromJson(json: JSONObject?, theme: SceneTheme? = null, layout: SceneObjectLayout? = null): SceneCustomization {
    val defaults = SceneCustomization.DEFAULT
    if (json == null) return defaults
    // A Halloween switch (or the scarecrows) a payload lacks reads as the theme's own starting value when the theme is known
    // (an archived Halloween edit gets the Halloween theme's switches on, as an unedited Halloween does), and off
    // otherwise: a copy saved under a name of its own is a theme of its own id, which starts with them off.
    val asItStarts = theme?.let { defaultCustomizationFor(it.id, it) } ?: defaults
    // *Lights on the buildings* is *Christmas lights*' stored field (see below); the two other Christmas switches read it
    // where they have no field of their own: whoever had *Christmas lights* on finds all three on.
    val christmasLights = if (json.has("christmasDecorationsEnabled")) {
        json.optBoolean("christmasDecorationsEnabled", defaults.christmasBuildingLightsEnabled)
    } else {
        json.optBoolean("winterColorsEnabled", defaults.winterColorsEnabled)
    }
    val christmasTreeLights = json.optBoolean("christmasTreeLightsEnabled", christmasLights)
    val christmasFirs = json.optBoolean("christmasFirsEnabled", christmasLights)
    // The Halloween decorations of a payload without them: the theme's starting value, but off where the Christmas
    // decorations are on, the side chosen first, which wins ([DecorationExclusion]).
    val halloweenDecorations = if (json.has("halloweenDecorationsEnabled")) {
        json.optBoolean("halloweenDecorationsEnabled", false)
    } else {
        asItStarts.halloweenDecorationsEnabled && !(christmasLights || christmasTreeLights || christmasFirs)
    }
    val buildings = json.optJSONObject("buildings")?.let { objectVariantConfigFromJson(it, defaults.buildings) } ?: defaults.buildings
    val shops = json.optJSONObject("shops")?.let { objectVariantConfigFromJson(it, buildings) } ?: buildings
    val shopHoursEnabled = json.optBoolean("shopHoursEnabled", json.optBoolean("businessHoursEnabled", defaults.shopHoursEnabled))
    val shopOpenHour = json.optFinite("shopOpenHour", json.optFinite("businessOpenHour", defaults.shopOpenHour))
    val shopCloseHour = json.optFinite("shopCloseHour", json.optFinite("businessCloseHour", defaults.shopCloseHour))
    return SceneCustomization(
        houses = json.optJSONObject("houses")?.let { objectVariantConfigFromJson(it, defaults.houses) } ?: defaults.houses,
        buildings = buildings,
        // **A payload without `shops` drew its shops in its Buildings colours**, and reads them so, keeping
        // its look; a field missing from a shops block falls back the same way. Visibility and density come
        // with them and are read by nothing (`SceneCustomization.shops`).
        shops = shops,
        // **A payload without `publics` drew its school in the shops' colours.** Shops' colours changed by
        // hand go on the public buildings too; untouched ones give the theme's own pair
        // ([publicsCarriedOver]); without a theme, the shops'.
        publics = json.optJSONObject("publics")?.let { objectVariantConfigFromJson(it, shops) }
            ?: publicsCarriedOver(shops, theme),
        cars = json.optJSONObject("cars")?.let { objectVariantConfigFromJson(it, defaults.cars) } ?: defaults.cars,
        parasols = json.optJSONObject("parasols")?.let { objectVariantConfigFromJson(it, defaults.parasols) } ?: defaults.parasols,
        // A missing category is not a changed one: the default, with no schema step.
        people = json.optJSONObject("people")?.let { objectVariantConfigFromJson(it, defaults.people) } ?: defaults.people,
        peopleNightDensity = json.optFinite(
            "peopleNightDensity",
            json.optJSONObject("people")?.optFinite("density", defaults.people.density)
                ?: defaults.people.density,
        ),
        // Without a night key the night is the payload's own day, not the default's (both night densities).
        carsNightDensity = json.optFinite(
            "carsNightDensity",
            json.optJSONObject("cars")?.optFinite("density", defaults.cars.density)
                ?: defaults.cars.density,
        ),
        // A payload with `businessHours*` alone, the one setting for shops and towers, gives it to each group
        // without a field of its own: whoever had the hours on finds them on in both, at the same hours.
        shopHoursEnabled = shopHoursEnabled,
        shopOpenHour = shopOpenHour,
        shopCloseHour = shopCloseHour,
        towerHoursEnabled = json.optBoolean("towerHoursEnabled", json.optBoolean("businessHoursEnabled", defaults.towerHoursEnabled)),
        towerOpenHour = json.optFinite("towerOpenHour", json.optFinite("businessOpenHour", defaults.towerOpenHour)),
        towerCloseHour = json.optFinite("towerCloseHour", json.optFinite("businessCloseHour", defaults.towerCloseHour)),
        // Without the public buildings' hours a payload kept its school on the shops': they read the shops'.
        publicHoursEnabled = json.optBoolean("publicHoursEnabled", shopHoursEnabled),
        publicOpenHour = json.optFinite("publicOpenHour", shopOpenHour),
        publicCloseHour = json.optFinite("publicCloseHour", shopCloseHour),
        trees = json.optJSONObject("trees")?.let { objectVariantConfigFromJson(it, defaults.trees) } ?: defaults.trees,
        // The seasonal decorations are saved per theme like the categories above, so "Christmas with
        // snowmen off" stays off.
        snowmen = json.optJSONObject("snowmen")?.let { objectVariantConfigFromJson(it, defaults.snowmen) } ?: defaults.snowmen,
        gifts = json.optJSONObject("gifts")?.let { objectVariantConfigFromJson(it, defaults.gifts) } ?: defaults.gifts,
        penguins = json.optJSONObject("penguins")?.let { objectVariantConfigFromJson(it, defaults.penguins) } ?: defaults.penguins,
        bunnies = json.optJSONObject("bunnies")?.let { objectVariantConfigFromJson(it, defaults.bunnies) } ?: defaults.bunnies,
        easterEggs = json.optJSONObject("easterEggs")?.let { objectVariantConfigFromJson(it, defaults.easterEggs) } ?: defaults.easterEggs,
        pumpkins = json.optJSONObject("pumpkins")?.let { objectVariantConfigFromJson(it, defaults.pumpkins) } ?: defaults.pumpkins,
        scarecrows = json.optJSONObject("scarecrows")?.let { objectVariantConfigFromJson(it, asItStarts.scarecrows) }
            ?: asItStarts.scarecrows,
        hillsVariation = json.optFinite("hillsVariation", defaults.hillsVariation),
        snowPiles = json.optFinite("snowPiles", defaults.snowPiles),
        leafPiles = json.optFinite("leafPiles", defaults.leafPiles),
        // The default as the fallback, like every colour around it: without one an unreadable colour is 0, transparent.
        hillsColorDay = json.optInt("hillsColorDay", defaults.hillsColorDay),
        hillsColorNight = json.optInt("hillsColorNight", defaults.hillsColorNight),
        hillsAutoMode = AutoColorMode.fromStorageId(json.optString("hillsAutoMode")),
        mountainsFront = json.optJSONObject("mountainsFront")?.let {
            MountainLayerConfig(
                visible = it.optBoolean("visible", defaults.mountainsFront.visible),
                density = it.optFinite("density", defaults.mountainsFront.density),
                colorDay = it.optInt("colorDay", defaults.mountainsFront.colorDay),
                colorNight = it.optInt("colorNight", defaults.mountainsFront.colorNight),
                autoMode = AutoColorMode.fromStorageId(it.optString("autoMode")),
            )
        } ?: defaults.mountainsFront,
        mountainsBack = json.optJSONObject("mountainsBack")?.let {
            MountainLayerConfig(
                visible = it.optBoolean("visible", defaults.mountainsBack.visible),
                density = it.optFinite("density", defaults.mountainsBack.density),
                colorDay = it.optInt("colorDay", defaults.mountainsBack.colorDay),
                colorNight = it.optInt("colorNight", defaults.mountainsBack.colorNight),
                autoMode = AutoColorMode.fromStorageId(it.optString("autoMode")),
            )
        } ?: defaults.mountainsBack,
        // Off when absent: nobody who updates finds houses on the mountains they did not ask for.
        distantHouses = json.optJSONObject("distantHouses")?.let {
            DistantHousesConfig(
                visible = it.optBoolean("visible", defaults.distantHouses.visible),
                density = it.optFinite("density", defaults.distantHouses.density).coerceIn(0f, 1f),
            )
        } ?: defaults.distantHouses,
        lake = json.optJSONObject("lake")?.let {
            LakeConfig(
                visible = it.optBoolean("visible", defaults.lake.visible),
                colorDay = it.optInt("colorDay", defaults.lake.colorDay),
                colorNight = it.optInt("colorNight", defaults.lake.colorNight),
                height = it.optFinite("height", defaults.lake.height),
                sailboatsVisible = it.optBoolean("sailboatsVisible", defaults.lake.sailboatsVisible),
                sailboatsDensity = it.optFinite("sailboatsDensity", defaults.lake.sailboatsDensity),
                dolphinsVisible = it.optBoolean("dolphinsVisible", defaults.lake.dolphinsVisible),
                dolphinsDensity = it.optFinite("dolphinsDensity", defaults.lake.dolphinsDensity),
                autoMode = AutoColorMode.fromStorageId(it.optString("autoMode")),
            )
        } ?: defaults.lake,
        birds = json.optJSONObject("birds")?.let { b ->
            val colorsArray = b.optJSONArray("colors")
            val colors = if (colorsArray != null) {
                (0 until colorsArray.length()).map { idx ->
                    val c = colorsArray.getJSONObject(idx)
                    // The slot's default colour as the fallback: without one an unreadable colour is 0, transparent.
                    val fallback = (defaults.birds.colors.getOrNull(idx) ?: defaults.birds.colors.first()).color
                    BirdColorWeight(c.optInt("color", fallback), c.optFinite("weight", 0.25f))
                }
            } else {
                defaults.birds.colors
            }
            BirdsConfig(
                visible = b.optBoolean("visible", defaults.birds.visible),
                density = b.optFinite("density", defaults.birds.density),
                nightBirds = b.optBoolean("nightBirds", defaults.birds.nightBirds),
                colors = colors,
            )
        } ?: defaults.birds,
        stars = json.optJSONObject("stars")?.let {
            StarsConfig(
                visible = it.optBoolean("visible", defaults.stars.visible),
                density = it.optFinite("density", defaults.stars.density),
            )
        } ?: defaults.stars,
        sky = json.optJSONObject("sky")?.let {
            SkyConfig(
                colorDayHigh = it.optInt("colorDayHigh", defaults.sky.colorDayHigh),
                colorDayLow = it.optInt("colorDayLow", defaults.sky.colorDayLow),
                colorNightHigh = it.optInt("colorNightHigh", defaults.sky.colorNightHigh),
                colorNightLow = it.optInt("colorNightLow", defaults.sky.colorNightLow),
                colorSunriseLow = it.optInt("colorSunriseLow", defaults.sky.colorSunriseLow),
                colorSunsetLow = it.optInt("colorSunsetLow", defaults.sky.colorSunsetLow),
                sunCloudHeight = it.optFinite("sunCloudHeight", defaults.sky.sunCloudHeight),
                autoModeHigh = AutoColorMode.fromStorageId(it.optString("autoModeHigh")),
                autoModeLow = AutoColorMode.fromStorageId(it.optString("autoModeLow")),
            )
        } ?: defaults.sky,
        sun = json.optJSONObject("sun")?.let {
            SunConfig(
                visible = it.optBoolean("visible", defaults.sun.visible),
                color = it.optInt("color", defaults.sun.color),
            )
        } ?: defaults.sun,
        moon = json.optJSONObject("moon")?.let {
            MoonConfig(
                visible = it.optBoolean("visible", defaults.moon.visible),
                color = it.optInt("color", defaults.moon.color),
                realisticPhases = it.optBoolean("realisticPhases", defaults.moon.realisticPhases),
            )
        } ?: defaults.moon,
        clouds = json.optJSONObject("clouds")?.let {
            CloudsConfig(
                visible = it.optBoolean("visible", defaults.clouds.visible),
                density = it.optFinite("density", defaults.clouds.density),
                colorDay = it.optInt("colorDay", defaults.clouds.colorDay),
                colorNight = it.optInt("colorNight", defaults.clouds.colorNight),
                autoMode = AutoColorMode.fromStorageId(it.optString("autoMode")),
            )
        } ?: defaults.clouds,
        precipitation = json.optJSONObject("precipitation")?.let {
            PrecipitationConfig(
                visible = it.optBoolean("visible", defaults.precipitation.visible),
                type = it.optString("type", defaults.precipitation.type.name).let { name ->
                    runCatching { PrecipitationType.valueOf(name) }.getOrDefault(defaults.precipitation.type)
                },
                intensity = it.optFinite("intensity", defaults.precipitation.intensity),
                rainColorDay = it.optInt("rainColorDay", defaults.precipitation.rainColorDay),
                rainColorNight = it.optInt("rainColorNight", defaults.precipitation.rainColorNight),
                snowColorDay = it.optInt("snowColorDay", defaults.precipitation.snowColorDay),
                snowColorNight = it.optInt("snowColorNight", defaults.precipitation.snowColorNight),
                thunderstorm = it.optBoolean("thunderstorm", defaults.precipitation.thunderstorm),
                rainAutoMode = AutoColorMode.fromStorageId(it.optString("rainAutoMode")),
                snowAutoMode = AutoColorMode.fromStorageId(it.optString("snowAutoMode")),
            )
        } ?: defaults.precipitation,
        rainbow = json.optJSONObject("rainbow")?.let {
            RainbowConfig(
                visible = it.optBoolean("visible", defaults.rainbow.visible),
                opacity = it.optFinite("opacity", defaults.rainbow.opacity),
            )
        } ?: defaults.rainbow,
        fallColorsEnabled = json.optBoolean("fallColorsEnabled", defaults.fallColorsEnabled),
        winterColorsEnabled = json.optBoolean("winterColorsEnabled", defaults.winterColorsEnabled),
        // A payload without `christmasDecorationsEnabled` has one flag for winter and Christmas, the
        // lights hanging off the winter flag: its winter flag is its lights (`christmasLights` above), so
        // a theme saved lit comes back lit.
        christmasBuildingLightsEnabled = christmasLights,
        christmasTreeLightsEnabled = christmasTreeLights,
        christmasFirsEnabled = christmasFirs,
        flowersEnabled = json.optBoolean("flowersEnabled", defaults.flowersEnabled),
        // Absent reads `true`, so a Beach or Desert theme saved without the field keeps its palms rather
        // than oaks (`SceneCustomization.palmsEnabled`).
        palmsEnabled = json.optBoolean("palmsEnabled", defaults.palmsEnabled),
        // **Absent means off**: `palmsEnabled` is `true` on every theme, chosen or not, so it cannot be
        // the switch; a payload without this field comes back with its trees
        // (`SceneCustomization.palmsInsteadOfTrees`).
        palmsInsteadOfTrees = json.optBoolean("palmsInsteadOfTrees", defaults.palmsInsteadOfTrees),
        halloweenEnabled = json.optBoolean("halloweenEnabled", defaults.halloweenEnabled),
        horrorSkyEnabled = json.optBoolean("horrorSkyEnabled", defaults.horrorSkyEnabled),
        laughingMoonEnabled = json.optBoolean("laughingMoonEnabled", asItStarts.laughingMoonEnabled),
        nightSkyByDayEnabled = json.optBoolean("nightSkyByDayEnabled", asItStarts.nightSkyByDayEnabled),
        stillMoonEnabled = json.optBoolean("stillMoonEnabled", asItStarts.stillMoonEnabled),
        halloweenDecorationsEnabled = halloweenDecorations,
        bloodRainEnabled = json.optBoolean("bloodRainEnabled", asItStarts.bloodRainEnabled),
        headlessHorsemanEnabled = json.optBoolean("headlessHorsemanEnabled", asItStarts.headlessHorsemanEnabled),
        santaEnabled = json.optBoolean("santaEnabled", defaults.santaEnabled),
    ).let { read ->
        // Both sides read on by their amounts: judged on the street, where a category above 0 % can keep nothing. Only then
        // is the street worked out -- the saved theme's own, or the one [theme] draws.
        val street = if (DecorationExclusion.christmasOn(read) && DecorationExclusion.halloweenOn(read)) {
            layout ?: theme?.let { SceneObjectCatalog.layoutFor(it.id, it.accentColor) }
        } else {
            null
        }
        DecorationExclusion.resolved(read, asItStarts, street)
    }
}

fun CustomThemeEntry.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("theme", theme.toJson())
    put("layout", layout.toJson())
    put("customization", customization.toJson())
}

fun customThemeEntryFromJson(json: JSONObject): CustomThemeEntry {
    val name = json.getString("name")
    val layout = sceneObjectLayoutFromJson(json.getJSONObject("layout"))
    return CustomThemeEntry(
        id = json.getString("id"),
        name = name,
        // The name every screen shows is the entry's own ([CustomThemeEntry.name]), not the theme's copy,
        // which a rename that wrote `name` alone left behind.
        theme = sceneThemeFromJson(json.getJSONObject("theme")).copy(displayName = name),
        layout = layout,
        customization = sceneCustomizationFromJson(json.optJSONObject("customization"), sceneThemeFromJson(json.getJSONObject("theme")), layout),
    )
}

// --- Schema versioning -------------------------------------------------------------------

/**
 * Current schema version written by [CustomThemeData.toJsonString]. Bumped for a *breaking* change
 * (a field that changes type or meaning, or is removed), with its step in [migrateCustomThemeJson]
 * and a test on a fixture of the old shape; an additive field needs no bump, every read being defensive.
 */
const val CUSTOM_THEME_SCHEMA_VERSION = 5

/**
 * The version of a payload with no `schemaVersion` key, written before the version was recorded.
 * Versions 0 and 1 are the same shape (1 marks where the version starts being recorded), so
 * 0 -> 1 is a no-op by construction.
 */
const val CUSTOM_THEME_SCHEMA_VERSION_LEGACY = 0

/**
 * Reads the schema version of a persisted payload without parsing the rest of it. Returns
 * [CUSTOM_THEME_SCHEMA_VERSION_LEGACY] for pre-versioning data, and `null` if the payload is
 * absent or not parseable as JSON at all.
 */
fun readCustomThemeSchemaVersion(raw: String?): Int? {
    if (raw.isNullOrBlank()) return null
    return try {
        JSONObject(raw).optInt("schemaVersion", CUSTOM_THEME_SCHEMA_VERSION_LEGACY)
    } catch (_: Exception) {
        null
    }
}

/** The custom-theme schema of a backup that records none: see [migrateEmbeddedCustomThemes]. */
const val BACKUP_SCHEMA_BEFORE_THE_FIELD = 3

/**
 * Runs the custom-theme migrations over a document that embeds `overrides` and `customThemes`: a
 * whole-app backup holds entries in the theme store's shape, and without the store's migrations an
 * older payload would be read as current.
 *
 * The version a backup records is the one to migrate *from*. A backup that records none was written
 * by an app whose theme schema was 3, and is read as **3** ([BACKUP_SCHEMA_BEFORE_THE_FIELD]): not
 * legacy 0, which would re-run `1 -> 2` and divide every object's scale by its base scale a second
 * time, and not current, which would skip `3 -> 4` and `4 -> 5` (the duplicate repair, the school).
 * `BackupAndThemeShareTest` pins both.
 */
fun migrateEmbeddedCustomThemes(root: JSONObject, fromVersion: Int) {
    migrateCustomThemeJson(root, fromVersion)
}

/**
 * Brings a parsed payload up to [CUSTOM_THEME_SCHEMA_VERSION], mutating [root] in place, and
 * returns the version the payload is at afterwards.
 *
 * Payloads newer than this build understands are passed through untouched rather than rejected:
 * a user who installs an older APK over a newer one would otherwise lose every saved theme. The
 * reader is defensive about unknown and missing keys, so a forward-read gives "the parts this build
 * understands". Saved again by this build, such a payload loses the fields it did not understand,
 * and the entries it read are stamped with this build's [CUSTOM_THEME_SCHEMA_VERSION]; only the
 * entries it could not read keep their schema ([UnreadableThemeEntry]). Both are accepted, and are
 * why this function is the single place a migration is registered.
 */
private fun migrateCustomThemeJson(root: JSONObject, fromVersion: Int): Int {
    // Payloads at or ahead of the current version are passed through untouched.
    if (fromVersion >= CUSTOM_THEME_SCHEMA_VERSION) return fromVersion

    // 0 -> 1: identical shape, so there is nothing to rewrite. See
    // CUSTOM_THEME_SCHEMA_VERSION_LEGACY for why that is deliberate.

    // 1 -> 2: `StaticSceneObject.scale` holds the category's whole base size in version 1 and a
    // relative variation around 1 since. It still parses, so unmigrated it would draw objects half
    // again too large rather than fail.
    if (fromVersion < 2) {
        forEachEntry(root) { entry ->
            val layout = entry.optJSONObject("layout") ?: return@forEachEntry
            migrateStaticScalesToVariations(layout.optJSONArray("staticObjects"))
        }
    }

    // 2 -> 3: nothing to rewrite. The traffic lanes are recanonicalised on every load at any version
    // (`sceneObjectLayoutFromJson`), because a migration fixes only the payloads older than itself. The
    // bump records that a version 2 payload may hold lane coordinates no road the app draws matches.

    // 3 -> 4: two things a stored theme can carry that its generator does not produce. One-shot
    // repairs, not a canonicalise-on-load: a saved theme's content is the user's, and is rewritten once
    // for a defect the app produced, not second-guessed on every read.
    //
    //  - **Duplicate storefronts**: the catalogue emits one shop per storefront variant a tile
    //    ([SceneObjectCatalog.singleShopPerVariant]); a payload older than schema 4 can hold two.
    //  - **Duplicate special vehicles**: the per-type cap applies where the types are rolled
    //    ([SceneObjectCatalog.capSpecialsToOnePerType]), which repairs nothing a stored theme holds.
    //
    // **Neither step may fail the read**: `customThemeDataFromJsonString` turns an exception from here
    // into `CustomThemeData.EMPTY`, every saved theme lost. Each entry is repaired inside its own
    // `runCatching`, and anything unexpected leaves that one entry as it was found.
    if (fromVersion < 4) {
        forEachEntry(root) { entry ->
            runCatching {
                val layout = entry.optJSONObject("layout") ?: return@runCatching
                migrateDuplicateStorefronts(layout.optJSONArray("staticObjects"))
                migrateDuplicateSpecialVehicles(layout.optJSONArray("cars"))
            }
        }
    }

    // 4 -> 5: the school. The shop band is three thirds with a school in the middle one; a street of
    // schema 4 has a restaurant and a bar and nothing in the middle third, so the school would never
    // appear. Such streets get it, one-shot for the same reason as 3 -> 4.
    //
    // **It adds; it does not move** ([SceneObjectCatalog.missingSchoolFor] says why nothing can be
    // promoted). After 3 -> 4 on purpose: the storefront repair may leave a building in the middle
    // third of a very old payload, and then there is nothing to add. Guarded as 3 -> 4 is.
    if (fromVersion < 5) {
        forEachEntry(root) { entry ->
            runCatching {
                val layout = entry.optJSONObject("layout") ?: return@runCatching
                addMissingSchool(layout.optJSONArray("staticObjects"))
            }
        }
    }

    // Future breaking changes add one step each, in order, above this line:
    //   if (fromVersion < 6) { ...rewrite root...; }
    root.put("schemaVersion", CUSTOM_THEME_SCHEMA_VERSION)
    return CUSTOM_THEME_SCHEMA_VERSION
}

/**
 * Appends the school a stored street is missing, or leaves it exactly as it is.
 *
 * Reads the stored objects the way the loader does ([staticSceneObjectFromJson]), because the
 * placement measures each one's drawn extent; a street that cannot be read whole is left alone (the
 * loader rejects it anyway, and a school placed against half a street could stand on a house nobody
 * measured). Idempotent: a second run finds the school in the middle third.
 *
 * Two callers, one rule: the 4 -> 5 step above, and `parseThemeShare` for an imported theme
 * **file**, which gets the school too. A file carries no store version and the share format has one
 * version of its own (1), so a file without a school is given one.
 */
internal fun addMissingSchool(objects: JSONArray?) {
    if (objects == null) return
    val parsed = (0 until objects.length()).map { staticSceneObjectFromJson(objects.getJSONObject(it)) }
    val school = SceneObjectCatalog.missingSchoolFor(parsed) ?: return
    objects.put(school.toJson())
}

/**
 * Keeps one commercial building per storefront variant and moves the rest to tower depths.
 *
 * The rule of [SceneObjectCatalog.singleShopPerVariant] applied to a stored layout, as the one-shot
 * repair of a payload older than schema 4 (a street saved at schema 5, with two places in a third,
 * never reaches it). Of the shop-band candidates (`depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH`),
 * the depth-middle one of each of the three bands ([SceneSpace.RESTAURANT_MAX_DEPTH],
 * [SceneSpace.SCHOOL_MAX_DEPTH]) stays a shop; every other keeps its slot, x and size and takes a tower
 * depth interleaved across the tower band. The three bands are written out here, not read from a
 * table: a new storefront band must be added here too, or two candidates in one band stay two
 * identical shops.
 *
 * Only `depthFraction` moves, only for the surplus: a shop's depth is what makes it a shop, so this
 * changes what a building is drawn as and nothing else. Idempotent -- at most one shop per band
 * remains -- which matters because a payload is migrated on every load until the user next saves it.
 */
private fun migrateDuplicateStorefronts(objects: JSONArray?) {
    if (objects == null) return
    val shopIndices = (0 until objects.length()).filter { i ->
        val obj = objects.optJSONObject(i) ?: return@filter false
        obj.optString("type") == SceneObjectType.SKYSCRAPER.name &&
            obj.optFinite("depthFraction", 0f) >= SceneSpace.BUILDING_TOWER_MAX_DEPTH
    }
    if (shopIndices.size <= 1) return
    fun depthOf(i: Int) = objects.getJSONObject(i).optFinite("depthFraction", 0f)
    fun middleByDepth(indices: List<Int>): Int? =
        indices.sortedBy { depthOf(it) }.let { if (it.isEmpty()) null else it[it.size / 2] }
    fun inBand(from: Float, to: Float): Int? =
        middleByDepth(shopIndices.filter { depthOf(it) >= from && depthOf(it) < to })
    val kept = setOfNotNull(
        inBand(SceneSpace.BUILDING_TOWER_MAX_DEPTH, SceneSpace.RESTAURANT_MAX_DEPTH),
        inBand(SceneSpace.RESTAURANT_MAX_DEPTH, SceneSpace.SCHOOL_MAX_DEPTH),
        inBand(SceneSpace.SCHOOL_MAX_DEPTH, Float.POSITIVE_INFINITY),
    )
    val demoted = shopIndices.filterNot { it in kept }
    if (demoted.isEmpty()) return
    demoted.forEachIndexed { rank, i ->
        objects.getJSONObject(i).put(
            "depthFraction",
            (SceneSpace.BUILDING_TOWER_MAX_DEPTH * (2 * rank + 1) / (2f * demoted.size)).toDouble(),
        )
    }
}

/**
 * Demotes a stored theme's surplus special vehicles to [CarType.PLAIN], one of each type kept.
 *
 * [SceneObjectCatalog.capSpecialsToOnePerType] caps the types where they are rolled, which repairs
 * nothing a saved theme already holds. The first of each type is kept, as the generator does, so a
 * repaired theme and a generated one agree. Only `type` changes: the surplus vehicle keeps its lane,
 * slot, speed and colour.
 */
private fun migrateDuplicateSpecialVehicles(cars: JSONArray?) {
    if (cars == null || cars.length() <= 1) return
    val seen = HashSet<String>()
    for (i in 0 until cars.length()) {
        val car = cars.optJSONObject(i) ?: continue
        val type = car.optString("type", CarType.PLAIN.name)
        if (type == CarType.PLAIN.name) continue
        if (CarType.entries.none { it.name == type }) continue
        if (!seen.add(type)) car.put("type", CarType.PLAIN.name)
    }
}

/**
 * Visits every saved theme in a payload: the overrides (an object keyed by built-in id) and the
 * custom themes (an array).
 */
private inline fun forEachEntry(root: JSONObject, body: (JSONObject) -> Unit) {
    root.optJSONObject("overrides")?.let { overrides ->
        val keys = overrides.keys()
        while (keys.hasNext()) {
            overrides.optJSONObject(keys.next())?.let(body)
        }
    }
    root.optJSONArray("customThemes")?.let { array ->
        for (i in 0 until array.length()) {
            array.optJSONObject(i)?.let(body)
        }
    }
}

/**
 * Converts each object's absolute `scale` into the relative size variation. In schema 1 the field
 * holds the category's whole base size (a house about 1.5, a tree about 1.3); dividing by that base
 * ([SceneSpace.legacyBaseScaleFor], the only record of those bases) gives the variation. An
 * unreadable or absent type leaves the value alone: wrong by at most the base scale, still a scene.
 */
private fun migrateStaticScalesToVariations(objects: JSONArray?) {
    if (objects == null) return
    for (i in 0 until objects.length()) {
        val obj = objects.optJSONObject(i) ?: continue
        val type = runCatching { SceneObjectType.valueOf(obj.optString("type")) }.getOrNull() ?: continue
        val legacyBase = SceneSpace.legacyBaseScaleFor(type)
        if (legacyBase <= 0f) continue
        // A non-finite old scale would be written straight back into the store: it takes the base
        // scale, a variation of 1.
        val legacyScale = obj.optFinite("scale", legacyBase)
        obj.put("scale", (legacyScale / legacyBase).toDouble())
    }
}


/** The section [CustomThemeData.unreadable] is written to. See [UnreadableThemeEntry]. */
internal const val UNREADABLE_ENTRIES_KEY = "unreadableEntries"

fun CustomThemeData.toJsonString(): String {
    val root = JSONObject()
    // Written first so it is present even in a payload that is later truncated by a storage
    // failure, which makes a partially-written file identifiable rather than merely corrupt.
    root.put("schemaVersion", CUSTOM_THEME_SCHEMA_VERSION)
    val overridesJson = JSONObject()
    overrides.forEach { (builtinId, entry) -> overridesJson.put(builtinId, entry.toJson()) }
    root.put("overrides", overridesJson)
    root.put("customThemes", JSONArray(customThemes.map { it.toJson() }))
    // A document with nothing unreadable in it is written without the section.
    if (unreadable.isEmpty()) return root.toString()
    // The kept entries go in as text, not as parsed JSON: `org.json` re-serialises what it parses
    // (numbers, escapes, key order), and those bytes are the only copy the user has. So the
    // section is written as a placeholder string -- letters, digits and hyphens, which neither
    // org.json implementation escapes -- and the placeholder is then replaced by the items. The
    // token is random and checked to occur exactly once, so a theme name cannot be mistaken for it.
    while (true) {
        val token = "paperscrape-unreadable-" + java.util.UUID.randomUUID()
        root.put(UNREADABLE_ENTRIES_KEY, token)
        val text = root.toString()
        val quoted = "\"" + token + "\""
        val at = text.indexOf(quoted)
        check(at >= 0) { "the placeholder was escaped by the serialiser" }
        if (at != text.lastIndexOf(quoted)) continue
        val section = unreadable.joinToString(separator = ",", prefix = "[", postfix = "]") { it.itemJson }
        return text.substring(0, at) + section + text.substring(at + quoted.length)
    }
}

/**
 * Puts the cars back into a built-in override saved without them, or without all of them.
 *
 * Files written by an older save path hold `rawLayout.cars.filter { keepCar(it) }`, so a theme saved
 * with Cars low or off has an **empty or thinned car list**: empty, it loses the road
 * (`SceneObjectRenderer.hasRoad`) and all traffic for good while the settings say "On - 100%";
 * thinned, its traffic is capped for ever, one car deriving the painted road from half a lane pair.
 *
 * **The guard is narrow**: an override of a built-in that defines cars
 * ([SceneObjectCatalog.builtinCarsFor] reads the original), holding fewer than that list and -- if
 * not empty -- exactly what the old save path wrote at the entry's own density
 * ([oldSaveWouldHaveWritten]). The only writer of an override is `snapshotEntry` (an import is
 * always a standalone theme), so such a list has no other author. A standalone theme has nothing to
 * compare against and is never repaired. Only `layout.cars` changes: a theme repaired at 10 % shows
 * 10 % of the traffic, on a road. Idempotent, so it runs on **every load**
 * ([customThemeDataFromJsonString], the one funnel every reader goes through).
 */
fun CustomThemeData.repairBuiltInOverrides(): CustomThemeData {
    if (overrides.isEmpty()) return this
    var changed = false
    val repaired = overrides.mapValues { (builtinId, entry) ->
        // Each override is repaired on its own: a repair that throws leaves that one theme as it
        // was read, and never costs the others.
        runCatching {
            val canonical = SceneObjectCatalog.builtinCarsFor(builtinId, entry.theme.accentColor)
            if (canonical.isEmpty()) return@runCatching entry
            val stored = entry.layout.cars
            // A whole inventory, or more than one: nothing to put back, and nothing this understands.
            if (stored.size >= canonical.size) return@runCatching entry
            // A *partial* inventory is only repaired once it has been re-derived and matched. See
            // [oldSaveWouldHaveWritten].
            if (stored.isNotEmpty() && stored != oldSaveWouldHaveWritten(canonical, entry.customization)) {
                return@runCatching entry
            }
            changed = true
            entry.copy(layout = entry.layout.copy(cars = SceneObjectCatalog.canonicaliseTraffic(canonical)))
        }.getOrDefault(entry)
    }
    return if (changed) copy(overrides = repaired) else this
}

/**
 * The car list the old save path would have written for [canonical] under [customization], as it
 * comes back off disk. That path stored `rawLayout.cars.filter { keepCar(it) }` **and** the
 * customization it filtered with, and every load runs the list through
 * [SceneObjectCatalog.canonicaliseTraffic]: so a damaged entry carries its own proof, and only an
 * exact, car-for-car match is repaired. [legacyKeepCar] is a threshold on a fixed per-car fraction
 * (one of eleven nested subsets of a ten-car list), so anything else -- or a canonical layout
 * generated differently -- fails the match and is left alone.
 *
 * **[legacyKeepCar], not the live selection ([CarSelection])**: the old path's output is
 * reconstructed only by its own frozen expression; the live one would match no damaged entry.
 */
private fun oldSaveWouldHaveWritten(
    canonical: List<CarObject>,
    customization: SceneCustomization,
): List<CarObject> =
    SceneObjectCatalog.canonicaliseTraffic(canonical.filter { customization.legacyKeepCar(it) })

/**
 * The stored blob, or `null` if there is one and it cannot be read at all.
 *
 * [customThemeDataFromJsonString] reads an absent store and a document that is not JSON alike, as
 * `CustomThemeData.EMPTY`, which is what a reader wants; a read-modify-write must tell them apart, or
 * it overwrites the second with a document derived from nothing (`CustomThemeStore.update`). `null`
 * means the document, not one theme in it: entries unreadable one by one are set aside in
 * [CustomThemeData.unreadable] and written back, so an edit is not refused for their sake.
 */
fun customThemeDataOrNull(raw: String?): CustomThemeData? {
    if (raw.isNullOrBlank()) return CustomThemeData.EMPTY
    return readCustomThemeDocument(raw)
}

fun customThemeDataFromJsonString(raw: String?): CustomThemeData {
    if (raw.isNullOrBlank()) return CustomThemeData.EMPTY
    // A document that is not JSON should never crash the wallpaper -- it reads as "nothing saved".
    return readCustomThemeDocument(raw) ?: CustomThemeData.EMPTY
}

/**
 * Reads a saved-themes document **entry by entry**, so one damaged entry never reads as no saved
 * themes at all.
 *
 * `null` only when [raw] is not a JSON object, or the migrations fail on it as a whole (none can
 * today: each step that reads a theme's contents is guarded per entry). Every entry that reads is
 * returned; every one that does not is kept in [CustomThemeData.unreadable] with its bytes as they
 * stand in [raw] -- **before** the migrations rewrite the parsed copy -- and the schema those bytes
 * are in. Entries already in the `unreadableEntries` section are tried again ([recoverUnreadable]).
 */
private fun readCustomThemeDocument(raw: String): CustomThemeData? {
    val root = try {
        JSONObject(raw)
    } catch (_: Exception) {
        return null
    }
    val version = root.optInt("schemaVersion", CUSTOM_THEME_SCHEMA_VERSION_LEGACY)
    // The text of anything that fails is taken from [raw] as it was written. Where the strict
    // scanner cannot follow the text (org.json also accepts comments and unquoted names), the kept
    // text falls back to a re-serialisation of an unmigrated parse: the same content, not the same
    // bytes. Both are worked out only if something fails.
    val spans by lazy { runCatching { DocumentSpans.of(raw) }.getOrNull() }
    val pristine by lazy { JSONObject(raw) }
    fun keptText(span: JsonSpans.Span?, value: () -> Any?): String =
        span?.let { raw.substring(it.start, it.end) } ?: JsonSpans.serialise(value())

    try {
        migrateCustomThemeJson(root, version)
    } catch (_: Exception) {
        return null
    }

    val unreadable = mutableListOf<UnreadableThemeEntry>()
    val overrides = LinkedHashMap<String, CustomThemeEntry>()
    // An explicit `null` holds nothing, so it reads as absent.
    fun present(value: Any?): Boolean = value != null && value != JSONObject.NULL
    val overridesValue = root.opt("overrides")
    if (overridesValue is JSONObject) {
        for (key in overridesValue.keys()) {
            val entry = runCatching { customThemeEntryFromJson(overridesValue.getJSONObject(key)) }.getOrNull()
            if (entry != null) {
                overrides[key] = entry
            } else {
                val span = spans?.overrides?.lastOrNull { it.key == key }?.let { JsonSpans.Span(it.start, it.end) }
                val text = keptText(span) { pristine.optJSONObject("overrides")?.opt(key) }
                unreadable += UnreadableThemeEntry.of(UnreadableThemeEntry.FROM_OVERRIDES, key, version, text)
            }
        }
    } else if (present(overridesValue)) {
        // Present but not an object: kept whole. It has no key to go back under, so it stays
        // aside for good, which is still better than being written over.
        unreadable += UnreadableThemeEntry.of(
            UnreadableThemeEntry.FROM_OVERRIDES, null, version, keptText(spans?.overridesValue) { pristine.opt("overrides") },
        )
    }
    val customThemes = mutableListOf<CustomThemeEntry>()
    val customThemesValue = root.opt("customThemes")
    if (customThemesValue is JSONArray) {
        for (i in 0 until customThemesValue.length()) {
            val entry = runCatching { customThemeEntryFromJson(customThemesValue.getJSONObject(i)) }.getOrNull()
            if (entry != null) {
                customThemes += entry
            } else {
                val text = keptText(spans?.customThemes?.getOrNull(i)) { pristine.optJSONArray("customThemes")?.opt(i) }
                unreadable += UnreadableThemeEntry.of(UnreadableThemeEntry.FROM_CUSTOM_THEMES, null, version, text)
            }
        }
    } else if (present(customThemesValue)) {
        unreadable += UnreadableThemeEntry.of(
            UnreadableThemeEntry.FROM_CUSTOM_THEMES, null, version, keptText(spans?.customThemesValue) { pristine.opt("customThemes") },
        )
    }
    // Items an earlier write set aside, in their own text. A section that is not an array is kept
    // as one item, so it is never lost either.
    val section = root.opt(UNREADABLE_ENTRIES_KEY)
    val earlier = if (section is JSONArray) {
        (0 until section.length()).map { i ->
            UnreadableThemeEntry(keptText(spans?.unreadableEntries?.getOrNull(i)) { pristine.optJSONArray(UNREADABLE_ENTRIES_KEY)?.opt(i) })
        }
    } else if (present(section)) {
        listOf(UnreadableThemeEntry(keptText(spans?.unreadableEntriesValue) { pristine.opt(UNREADABLE_ENTRIES_KEY) }))
    } else {
        emptyList()
    }
    val kept = recoverUnreadable(earlier, overrides, customThemes)
    // Repaired on the way out, never on the way in: the bytes on disk stay as they are, a startup
    // makes no write, and data from a backup import is covered alike ([repairBuiltInOverrides]).
    return CustomThemeData(overrides = overrides, customThemes = customThemes, unreadable = unreadable + kept)
        .repairBuiltInOverrides()
}

/**
 * Puts back every kept entry that now reads and whose place is free; returns the ones that stay.
 *
 * An item is tried only when it says where it came from and which schema its bytes are in: the entry
 * is migrated from that version on a copy, exactly as it would have been in its own document, and
 * then read. An override goes back under its key unless the user has saved another one for that
 * built-in since -- the newer one is theirs and wins, and the old one stays aside. A standalone theme
 * goes back at the end of the list unless a theme with its id is already there. Everything else --
 * an item that cannot be parsed, a whole container kept by [readCustomThemeDocument], an entry that
 * still does not read -- stays exactly as it is.
 */
private fun recoverUnreadable(
    items: List<UnreadableThemeEntry>,
    overrides: MutableMap<String, CustomThemeEntry>,
    customThemes: MutableList<CustomThemeEntry>,
): List<UnreadableThemeEntry> = items.filterNot { item ->
    runCatching {
        val parsed = JSONObject(item.itemJson)
        if (!parsed.has("schemaVersion")) return@runCatching false
        val version = parsed.getInt("schemaVersion")
        val entry = parsed.optJSONObject("entry") ?: return@runCatching false
        when (parsed.optString("from")) {
            UnreadableThemeEntry.FROM_OVERRIDES -> {
                if (!parsed.has("key")) return@runCatching false
                val key = parsed.getString("key")
                if (key in overrides) return@runCatching false
                val single = JSONObject().put("overrides", JSONObject().put(key, JSONObject(entry.toString())))
                migrateCustomThemeJson(single, version)
                overrides[key] = customThemeEntryFromJson(single.getJSONObject("overrides").getJSONObject(key))
                true
            }
            UnreadableThemeEntry.FROM_CUSTOM_THEMES -> {
                val single = JSONObject().put("customThemes", JSONArray().put(JSONObject(entry.toString())))
                migrateCustomThemeJson(single, version)
                val recovered = customThemeEntryFromJson(single.getJSONArray("customThemes").getJSONObject(0))
                if (customThemes.any { it.id == recovered.id }) return@runCatching false
                customThemes += recovered
                true
            }
            else -> false
        }
    }.getOrDefault(false)
}

/**
 * Where each top-level value of a saved-themes document stands in its text, and each entry inside
 * `overrides`, `customThemes` and `unreadableEntries`. Throws if the text is not strict JSON.
 */
internal class DocumentSpans private constructor(
    val overridesValue: JsonSpans.Span?,
    val overrides: List<JsonSpans.Member>,
    val customThemesValue: JsonSpans.Span?,
    val customThemes: List<JsonSpans.Span>,
    val unreadableEntriesValue: JsonSpans.Span?,
    val unreadableEntries: List<JsonSpans.Span>,
) {
    companion object {
        fun of(text: String): DocumentSpans {
            val top = JsonSpans.objectMembers(text, JsonSpans.skipSpace(text, 0))
            // The last occurrence of a name is the one org.json keeps.
            fun value(name: String) = top.lastOrNull { it.key == name }?.let { JsonSpans.Span(it.start, it.end) }
            fun membersOf(span: JsonSpans.Span?) =
                if (span != null && text[span.start] == '{') JsonSpans.objectMembers(text, span.start) else emptyList()
            fun elementsOf(span: JsonSpans.Span?) =
                if (span != null && text[span.start] == '[') JsonSpans.arrayElements(text, span.start) else emptyList()
            val overrides = value("overrides")
            val customThemes = value("customThemes")
            val unreadable = value(UNREADABLE_ENTRIES_KEY)
            return DocumentSpans(
                overridesValue = overrides,
                overrides = membersOf(overrides),
                customThemesValue = customThemes,
                customThemes = elementsOf(customThemes),
                unreadableEntriesValue = unreadable,
                unreadableEntries = elementsOf(unreadable),
            )
        }
    }
}

/**
 * A strict JSON scanner that reports **where** values stand in a text instead of parsing them.
 *
 * `org.json` has no way to hand back the text a value was read from, and the kept entries must be
 * written back as that text (see [UnreadableThemeEntry]). This follows RFC 8259 and nothing more:
 * org.json's leniencies (comments, unquoted names, `=` and `;` as separators) make it throw, and the
 * caller then falls back to [serialise].
 */
internal object JsonSpans {

    /** `text.substring(start, end)` is one whole JSON value. */
    data class Span(val start: Int, val end: Int)

    /** One `"name": value` pair of an object; [key] is the name decoded. */
    data class Member(val key: String, val start: Int, val end: Int)

    fun skipSpace(text: String, from: Int): Int {
        var i = from
        while (i < text.length && text[i] in " \t\n\r") i++
        return i
    }

    /** The index just past the value that starts at [start]. */
    fun valueEnd(text: String, start: Int): Int {
        require(start < text.length) { "value expected at $start" }
        return when (text[start]) {
            '{' -> objectEnd(text, start)
            '[' -> arrayEnd(text, start)
            '"' -> stringEnd(text, start)
            't' -> literalEnd(text, start, "true")
            'f' -> literalEnd(text, start, "false")
            'n' -> literalEnd(text, start, "null")
            else -> numberEnd(text, start)
        }
    }

    fun objectMembers(text: String, start: Int): List<Member> {
        require(text[start] == '{') { "object expected at $start" }
        val members = mutableListOf<Member>()
        var i = skipSpace(text, start + 1)
        if (i < text.length && text[i] == '}') return members
        while (true) {
            require(i < text.length && text[i] == '"') { "name expected at $i" }
            val nameEnd = stringEnd(text, i)
            val key = decode(text, i, nameEnd)
            i = skipSpace(text, nameEnd)
            require(i < text.length && text[i] == ':') { "':' expected at $i" }
            val valueStart = skipSpace(text, i + 1)
            val valueEnd = valueEnd(text, valueStart)
            members += Member(key, valueStart, valueEnd)
            i = skipSpace(text, valueEnd)
            require(i < text.length) { "unterminated object" }
            if (text[i] == '}') return members
            require(text[i] == ',') { "',' expected at $i" }
            i = skipSpace(text, i + 1)
        }
    }

    fun arrayElements(text: String, start: Int): List<Span> {
        require(text[start] == '[') { "array expected at $start" }
        val elements = mutableListOf<Span>()
        var i = skipSpace(text, start + 1)
        if (i < text.length && text[i] == ']') return elements
        while (true) {
            val end = valueEnd(text, i)
            elements += Span(i, end)
            i = skipSpace(text, end)
            require(i < text.length) { "unterminated array" }
            if (text[i] == ']') return elements
            require(text[i] == ',') { "',' expected at $i" }
            i = skipSpace(text, i + 1)
        }
    }

    /** [value] as JSON text, for a value whose own text could not be followed. */
    fun serialise(value: Any?): String {
        // Wrapped in an array and unwrapped, because neither org.json serialises a bare value.
        val text = JSONArray().put(value ?: JSONObject.NULL).toString()
        return text.substring(1, text.length - 1)
    }

    private fun objectEnd(text: String, start: Int): Int {
        val members = objectMembers(text, start)
        val afterLast = members.lastOrNull()?.end ?: (start + 1)
        return skipSpace(text, afterLast) + 1
    }

    private fun arrayEnd(text: String, start: Int): Int {
        val elements = arrayElements(text, start)
        val afterLast = elements.lastOrNull()?.end ?: (start + 1)
        return skipSpace(text, afterLast) + 1
    }

    private fun stringEnd(text: String, start: Int): Int {
        var i = start + 1
        while (i < text.length) {
            when (text[i]) {
                '\\' -> i += 2
                '"' -> return i + 1
                else -> i++
            }
        }
        throw IllegalArgumentException("unterminated string at $start")
    }

    private fun literalEnd(text: String, start: Int, literal: String): Int {
        require(text.startsWith(literal, start)) { "'$literal' expected at $start" }
        return start + literal.length
    }

    private fun numberEnd(text: String, start: Int): Int {
        var i = start
        while (i < text.length && (text[i].isDigit() || text[i] in "+-.eE")) i++
        require(i > start) { "value expected at $start" }
        return i
    }

    private fun decode(text: String, start: Int, end: Int): String = buildString {
        var i = start + 1
        while (i < end - 1) {
            val c = text[i]
            if (c != '\\') {
                append(c); i++; continue
            }
            when (val e = text[i + 1]) {
                'u' -> { append(text.substring(i + 2, i + 6).toInt(16).toChar()); i += 6 }
                'b' -> { append('\b'); i += 2 }
                'f' -> { append('\u000C'); i += 2 }
                'n' -> { append('\n'); i += 2 }
                'r' -> { append('\r'); i += 2 }
                't' -> { append('\t'); i += 2 }
                else -> { append(e); i += 2 }
            }
        }
    }
}
