package com.paperscrape.livewallpaper.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * A saved custom theme: either a full replacement for one of the built-in themes (its [id]
 * matches a [ThemeCatalog.ALL] id, e.g. "christmas") or a fully independent theme the user
 * created from scratch (its [id] looks like "custom:<token>").
 *
 * Both [theme] and [layout] are complete snapshots — everything needed to render the scene
 * except the seasonal-decoration slots, which [SceneObjectCatalog.layoutFor] deals from the
 * entry's id at load, as it does for a built-in. This is what makes "Reset to default" trivial:
 * it just deletes the override, and [ThemeCatalog.byId] naturally falls back to the
 * hardcoded built-in again.
 */
data class CustomThemeEntry(
    val id: String,
    /**
     * The name the user gave this theme, and **the one every screen shows** (v5.9B, I-01).
     *
     * [theme] carries a second copy, `displayName`, and that copy is what the screens actually read
     * -- the gallery card, the preview at the top of the settings, the Theme row, Seasons and
     * World & scene all go through [SceneTheme.displayName], most of them via
     * [ThemeCatalog.byId]. "Rename" used to write this field alone, from v1.0 to v5.9A, so a renamed
     * theme kept its old name everywhere the user looked while the file held the new one. The two
     * are now one fact: [customThemeEntryFromJson] reads `displayName` from here, which also puts
     * right every theme renamed before the repair, and `CustomThemeStore.renameCustomTheme` writes
     * both.
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
 * A saved theme this build cannot read, kept exactly as it was stored (item 18, v5.9A).
 *
 * ### Why it is kept rather than skipped
 *
 * Until v5.9A one unreadable entry made the whole document read as [CustomThemeData.EMPTY]: every
 * saved theme vanished from the Themes screen, a wallpaper set to one of them fell back to Sunset,
 * and every later edit was dropped, because `CustomThemeStore.update` refuses to write over a
 * document it cannot read (BCK-05). Skipping the entry instead would have been worse -- the next
 * save would write the document back without it, and those bytes are the only copy the user has.
 * So the reader now reads entry by entry, and an entry that fails is carried here, as text.
 *
 * ### Where it lives on disk, and why not where it was
 *
 * [toJsonString] writes it into a section of its own, `unreadableEntries`, as one item holding
 * where the entry came from (`overrides` and its key, or `customThemes`), the schema version its
 * bytes were written in, and the entry itself verbatim. It is moved out of `overrides` and
 * `customThemes` for two reasons. The rewritten document is stamped with the current schema, and
 * an entry written under another one would be mislabelled in place -- a later build that learned
 * to read it would skip its migrations (1 -> 2 changes what `scale` means). And an override kept
 * under its key would collide with a new override the user saves for the same built-in: one of the
 * two would have to go.
 *
 * ### How it comes back
 *
 * Every read tries each item again, migrating it from the version it records. One that reads, and
 * whose place is free, rejoins the other saved themes; the next write puts it back where it came
 * from. Nothing in this build can make an entry readable that was not, so today that is the path a
 * later build takes -- one that restores an object type or relaxes a field that made the entry fail.
 *
 * [itemJson] is the whole item as it stands in the section, which is what makes a second, third and
 * hundredth write leave it byte-identical.
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
 * **BCK-03.** `org.json` coerces strings, so the literal `"NaN"` in a theme or backup file reads
 * back as [Double.NaN] from `getDouble`, and `Infinity` likewise. Nothing downstream checked: the
 * value went into a `depthFraction` or a `laneYFraction`, through `SceneSpace`'s arithmetic, and out
 * to the renderer as a coordinate that is not a number. Every comparison against it is false, so an
 * object silently stops being drawn or is drawn nowhere, and — the part that makes it worth fixing —
 * the poisoned value is **persisted**, so it survives restarts and re-exports until the key is
 * rewritten by hand.
 *
 * Throwing is the right answer for a required field: both import paths already wrap parsing in
 * `runCatching`, so the file is reported as malformed and refused, which is what a file containing
 * `"NaN"` deserves. See [optFinite] for the optional fields, which fall back instead.
 */
internal fun JSONObject.requireFinite(name: String): Float {
    val value = getDouble(name)
    // Checked after the narrowing, not before (v5.8). `1e300` is a finite Double and an infinite
    // Float, so checking the Double let it through, and the value the app then held could not be
    // written back: the next `JSONObject.put` -- the import's own staging copy -- threw
    // "Forbidden numeric value: Infinity" from a coroutine with no catch, and the app closed. A
    // 173-byte backup did it on the BV6600 (assessment v5.7, registri/72). NaN narrows to NaN, so
    // one check still covers both.
    val narrowed = value.toFloat()
    require(narrowed.isFinite()) { "$name is not a finite 32-bit number: $value" }
    return narrowed
}

/**
 * An optional number; anything that is not finite as a `Float` reads as absent and takes [fallback].
 *
 * The counterpart to [requireFinite] for fields that already have a default. A `"NaN"` density is
 * not a reason to refuse a whole backup — the file is still readable, that one value is not — so it
 * takes the default the field would have had if the key were missing. Checked after the narrowing
 * for the reason [requireFinite] gives: `1e300` is finite until it becomes a `Float`.
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
    // Falls back to the old discrete "layer" (0..8 row index) field, converted to an equivalent
    // continuous fraction, for custom themes saved before the continuous-depth refactor -- so an
    // existing user's saved custom theme still loads instead of crashing on a missing key.
    depthFraction = if (json.has("depthFraction")) {
        json.requireFinite("depthFraction")
    } else {
        json.optInt("layer", 0) / 8f
    },
    tileFractionX = json.requireFinite("tileFractionX"),
    scale = json.optFinite("scale", 1f),
    // Absent from every payload written before v5.5, and the fallback is what those payloads
    // need: `UNDEALT` means "hash the drawing from the position", which is exactly what the build
    // that saved them did. So an existing custom theme loads as the same street it was saved as,
    // with no migration and no schema bump -- the same shape `CarObject.type` was added in.
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
    // optString + runCatching: existing saved custom themes from before this field existed
    // simply get CarType.PLAIN, the same vehicle they always rendered as.
    type = runCatching { CarType.valueOf(json.optString("type", "PLAIN")) }.getOrDefault(CarType.PLAIN),
)

fun SceneObjectLayout.toJson(): JSONObject = JSONObject().apply {
    put("staticObjects", JSONArray(staticObjects.map { it.toJson() }))
    put("cars", JSONArray(cars.map { it.toJson() }))
    // Which density rule thinned these objects (v5.8C). Additive: a payload without it was
    // written before the key existed, by an app that thinned with the grid.
    put("densityScheme", densityScheme)
}

fun sceneObjectLayoutFromJson(json: JSONObject): SceneObjectLayout {
    val staticArray = json.getJSONArray("staticObjects")
    val staticObjects = (0 until staticArray.length()).map { staticSceneObjectFromJson(staticArray.getJSONObject(it)) }
    val carsArray = json.getJSONArray("cars")
    val cars = (0 until carsArray.length()).map { carObjectFromJson(carsArray.getJSONObject(it)) }
    // Traffic geometry is recomputed on **every** load, whatever schema version the
    // payload carries. A stored lane coordinate is a copy of a SceneSpace constant,
    // and that constant has moved three times since custom themes started saving it;
    // a copy that disagrees with the current road drags the road back to where it was
    // saved, because the painted strip is derived from the layout's own lanes. Doing
    // this in a schema migration only ever fixes the payloads written before the
    // migration -- see SceneObjectCatalog.canonicaliseTraffic for why that is not
    // enough.
    return SceneObjectLayout(
        staticObjects = staticObjects,
        cars = SceneObjectCatalog.canonicaliseTraffic(cars),
        // Absent means a layout saved before v5.8C, thinned by the grid: kept on it so it keeps
        // every object it stored. See [DENSITY_SCHEME_GRID].
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
    // Written as the storage id, and read back with a MANUAL fallback below, so a theme saved
    // before automatic pairs existed reads exactly as it always did. No schema bump is needed:
    // every reader here is already an `opt*` with a default.
    put("autoMode1", autoMode1.storageId)
    put("autoMode2", autoMode2.storageId)
}

fun objectVariantConfigFromJson(json: JSONObject, default: ObjectVariantConfig): ObjectVariantConfig = ObjectVariantConfig(
    visible = json.optBoolean("visible", default.visible),
    density = json.optFinite("density", default.density),
    // `optInt` with the default, like the mountains, the lake and the sky. These four read
    // `getInt` until v5.3, and `getInt` **throws** when the key is present but is not an integer,
    // where `optInt` falls back. That is the whole of the v5.3B audit's S2: a backup with
    // `"colorDay1":"nope"` in it -- 129 bytes, and no attacker needed, just a truncated download
    // -- threw out through `sceneCustomizationFromJson` and closed the settings screen, because
    // both parsers' call sites are inside a Compose `scope.launch { }` with no catch.
    // `ImportParserFuzzTest` is the check that found it and it stays: it went from 221 escaping
    // throwables to 0 on these four lines alone.
    //
    // `optInt(name, fallback)` also drops the `has` guard, which was doing nothing the fallback
    // does not already do -- absent and unreadable now take the same path, which is the one the
    // document deserves either way.
    //
    // The hills and the bird colours did not, until v5.8C: `optInt` with no fallback turned a
    // present but unreadable colour into 0, which is transparent. The theme and car colours use
    // `getInt`, which throws inside the importers' `runCatching` -- a refused file, not a wrong one.
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
    put("cars", cars.toJson())
    put("parasols", parasols.toJson())
    put("people", people.toJson())
    // Written alongside the people block rather than inside it: it belongs to the pedestrians,
    // not to ObjectVariantConfig, which every other category shares. A theme saved before v2.12
    // has no such key, and reading falls back to that theme's own daytime density -- so an old
    // saved theme keeps behaving exactly as it did.
    put("peopleNightDensity", peopleNightDensity.toDouble())
    // The cars' pair of the same field, beside the cars block for the same reason. A theme saved
    // before v4.22 has no such key, and reading falls back to that theme's own daytime car
    // density -- the upgrade rule PeopleDensity.resolveNightDensity states, applied to a payload.
    put("carsNightDensity", carsNightDensity.toDouble())
    // The business hours (v4.22). Absent from every older payload; reading falls back to the
    // defaults, whose toggle-off state renders identically to the feature not existing.
    put("businessHoursEnabled", businessHoursEnabled)
    put("businessOpenHour", businessOpenHour.toDouble())
    put("businessCloseHour", businessCloseHour.toDouble())
    put("trees", trees.toJson())
    put("snowmen", snowmen.toJson())
    put("gifts", gifts.toJson())
    put("penguins", penguins.toJson())
    put("bunnies", bunnies.toJson())
    put("easterEggs", easterEggs.toJson())
    put("pumpkins", pumpkins.toJson())
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
    put("christmasDecorationsEnabled", christmasDecorationsEnabled)
    put("flowersEnabled", flowersEnabled)
    put("palmsEnabled", palmsEnabled)
    put("halloweenEnabled", halloweenEnabled)
    put("horrorSkyEnabled", horrorSkyEnabled)
    put("santaEnabled", santaEnabled)
}

fun sceneCustomizationFromJson(json: JSONObject?): SceneCustomization {
    val defaults = SceneCustomization.DEFAULT
    if (json == null) return defaults
    return SceneCustomization(
        houses = json.optJSONObject("houses")?.let { objectVariantConfigFromJson(it, defaults.houses) } ?: defaults.houses,
        buildings = json.optJSONObject("buildings")?.let { objectVariantConfigFromJson(it, defaults.buildings) } ?: defaults.buildings,
        cars = json.optJSONObject("cars")?.let { objectVariantConfigFromJson(it, defaults.cars) } ?: defaults.cars,
        parasols = json.optJSONObject("parasols")?.let { objectVariantConfigFromJson(it, defaults.parasols) } ?: defaults.parasols,
        // Absent from every payload written before v76.12, which is why it falls back to the
        // default rather than needing a schema step: a missing category is not a changed one.
        people = json.optJSONObject("people")?.let { objectVariantConfigFromJson(it, defaults.people) } ?: defaults.people,
        peopleNightDensity = json.optFinite(
            "peopleNightDensity",
            json.optJSONObject("people")?.optFinite("density", defaults.people.density)
                ?: defaults.people.density,
        ),
        // Same fallback shape as the people's: a pre-v4.22 payload has no night key, and its
        // night traffic must be its day traffic -- the payload's own, not the default's.
        carsNightDensity = json.optFinite(
            "carsNightDensity",
            json.optJSONObject("cars")?.optFinite("density", defaults.cars.density)
                ?: defaults.cars.density,
        ),
        businessHoursEnabled = json.optBoolean("businessHoursEnabled", defaults.businessHoursEnabled),
        businessOpenHour = json.optFinite("businessOpenHour", defaults.businessOpenHour),
        businessCloseHour = json.optFinite("businessCloseHour", defaults.businessCloseHour),
        trees = json.optJSONObject("trees")?.let { objectVariantConfigFromJson(it, defaults.trees) } ?: defaults.trees,
        // Seasonal decorations ARE part of a saved custom theme's JSON now -- per-theme editable
        // and saveable exactly like the structural categories above (see the ObjectCategory doc
        // comment in WallpaperPrefs.kt), so saving "Christmas with snowmen turned off" needs to
        // actually persist that choice, not silently drop it.
        snowmen = json.optJSONObject("snowmen")?.let { objectVariantConfigFromJson(it, defaults.snowmen) } ?: defaults.snowmen,
        gifts = json.optJSONObject("gifts")?.let { objectVariantConfigFromJson(it, defaults.gifts) } ?: defaults.gifts,
        penguins = json.optJSONObject("penguins")?.let { objectVariantConfigFromJson(it, defaults.penguins) } ?: defaults.penguins,
        bunnies = json.optJSONObject("bunnies")?.let { objectVariantConfigFromJson(it, defaults.bunnies) } ?: defaults.bunnies,
        easterEggs = json.optJSONObject("easterEggs")?.let { objectVariantConfigFromJson(it, defaults.easterEggs) } ?: defaults.easterEggs,
        pumpkins = json.optJSONObject("pumpkins")?.let { objectVariantConfigFromJson(it, defaults.pumpkins) } ?: defaults.pumpkins,
        hillsVariation = json.optFinite("hillsVariation", defaults.hillsVariation),
        snowPiles = json.optFinite("snowPiles", defaults.snowPiles),
        leafPiles = json.optFinite("leafPiles", defaults.leafPiles),
        // With the default as the fallback, like every colour around it: until v5.8C this was
        // `has` + `optInt` with no fallback, so a present but unreadable colour imported as 0 --
        // fully transparent hills (v5.8B comment audit).
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
                    // The default colour of this slot as the fallback (v5.8C): `optInt` with none
                    // turned an unreadable colour into 0, a transparent bird.
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
        // Absent from every payload written before the winter/Christmas split (v2.0), and every
        // payload since writes it. Before the split "the lights hung off the winter flag"
        // (RELEASE_HISTORY, v2.0), so such a payload's winter flag *is* its lights: a theme saved
        // with the winter presentation on showed them and gets them back, one saved with it off
        // showed none and gets none. Until v5.8C this fell back to [SceneCustomization.DEFAULT]
        // (off), so a Christmas theme saved lit came back dark (v5.8B comment audit).
        christmasDecorationsEnabled = if (json.has("christmasDecorationsEnabled")) {
            json.optBoolean("christmasDecorationsEnabled", defaults.christmasDecorationsEnabled)
        } else {
            json.optBoolean("winterColorsEnabled", defaults.winterColorsEnabled)
        },
        flowersEnabled = json.optBoolean("flowersEnabled", defaults.flowersEnabled),
        // Absent from every payload written before v5.1, and the default it falls back to is
        // `true` precisely so that a Beach or Desert theme saved before then keeps its palms
        // rather than coming back planted with oaks. See `SceneCustomization.palmsEnabled`.
        palmsEnabled = json.optBoolean("palmsEnabled", defaults.palmsEnabled),
        halloweenEnabled = json.optBoolean("halloweenEnabled", defaults.halloweenEnabled),
        horrorSkyEnabled = json.optBoolean("horrorSkyEnabled", defaults.horrorSkyEnabled),
        santaEnabled = json.optBoolean("santaEnabled", defaults.santaEnabled),
    )
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
    return CustomThemeEntry(
        id = json.getString("id"),
        name = name,
        // The name every screen shows is the entry's own (v5.9B, I-01): see [CustomThemeEntry.name].
        // Read this way rather than trusted from the file because every "Rename" before v5.9B wrote
        // `name` alone and left the theme's copy behind.
        theme = sceneThemeFromJson(json.getJSONObject("theme")).copy(displayName = name),
        layout = sceneObjectLayoutFromJson(json.getJSONObject("layout")),
        customization = sceneCustomizationFromJson(json.optJSONObject("customization")),
    )
}

// --- Schema versioning -------------------------------------------------------------------

/**
 * Current schema version written by [CustomThemeData.toJsonString].
 *
 * Bump this whenever a *breaking* change is made to the persisted shape — a field that changes
 * type or meaning, or one that is removed. Purely additive changes (a new optional field with a
 * sensible default) do not need a bump, because every read below is already defensive.
 *
 * When bumping, add the corresponding step to [migrateCustomThemeJson] and a test that loads a
 * fixture of the old shape and asserts the migrated result.
 */
const val CUSTOM_THEME_SCHEMA_VERSION = 5

/**
 * Version reported for data written before schema versioning existed (v73 and earlier). Such
 * payloads simply have no `schemaVersion` key.
 *
 * Version 0 and version 1 describe the *same* shape: version 1 exists to mark the point from
 * which the version is actually recorded, so that a future breaking change has a reliable
 * baseline to migrate from. Migrating 0 -> 1 is therefore a no-op by construction, not by
 * oversight.
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

/**
 * Runs the custom-theme migrations over a document that embeds `overrides` and `customThemes`.
 *
 * **BCK-07.** A whole-app backup carries theme entries written by `CustomThemeEntry.toJson`, the
 * same shape the theme store holds, but the import path parsed them directly and never ran the
 * store's migrations. Harmless while no breaking step exists after the backup format shipped, and
 * silently wrong the first time one does: a version 2 payload restored into a version 4 app would
 * be read as if it were version 4.
 *
 * The version a backup records is the one to migrate *from*. A backup that records none was written
 * before this existed (v4.15), by an app whose theme schema was 3. It is read as current, not
 * legacy: the legacy default of 0 would re-run `1 -> 2`, which divides every object's scale by its
 * base scale a second time, so **that default would corrupt every backup in existence**. Nor is it
 * current: backups exist since v4.3 and the field since v4.15, and schema 3 held for that whole
 * span (3 -> 4 came with v4.20), so absent means **3** ([BACKUP_SCHEMA_BEFORE_THE_FIELD]). Until
 * v5.8C it was read as current, which skipped `3 -> 4` and `4 -> 5`: such a backup restored its
 * saved themes without the duplicate-storefront and duplicate-vehicle repair and without the
 * school (v5.8B comment audit). `BackupAndThemeShareTest` pins both halves.
 */
/** The custom-theme schema of a backup that records none: see [migrateEmbeddedCustomThemes]. */
const val BACKUP_SCHEMA_BEFORE_THE_FIELD = 3

fun migrateEmbeddedCustomThemes(root: JSONObject, fromVersion: Int) {
    migrateCustomThemeJson(root, fromVersion)
}

/**
 * Brings a parsed payload up to [CUSTOM_THEME_SCHEMA_VERSION], mutating [root] in place, and
 * returns the version the payload is at afterwards.
 *
 * Payloads newer than this build understands are passed through untouched rather than rejected.
 * A user who installs an older APK over a newer one would otherwise lose every saved theme,
 * which is far worse than silently ignoring fields this build has no concept of. The reader
 * below is defensive about unknown and missing keys, so a forward-read degrades to "the parts
 * this build understands" instead of failing.
 *
 * Note the one real consequence of that choice: if such a payload is then *saved* again by this
 * older build, the fields it did not understand are not written back. That is accepted, and is
 * why this function is the single place a future migration must be registered.
 */
private fun migrateCustomThemeJson(root: JSONObject, fromVersion: Int): Int {
    // Payloads at or ahead of the current version are passed through untouched.
    if (fromVersion >= CUSTOM_THEME_SCHEMA_VERSION) return fromVersion

    // 0 -> 1: identical shape, so there is nothing to rewrite. See
    // CUSTOM_THEME_SCHEMA_VERSION_LEGACY for why that is deliberate.

    // 1 -> 2: `StaticSceneObject.scale` changed meaning, from the category's whole base
    // size to a relative variation around 1.
    //
    // Breaking in the quiet way -- the field is still a float and still parses, it just means
    // something else now, so a payload left unmigrated renders as a scene of objects half again
    // too large rather than as a parse failure.
    if (fromVersion < 2) {
        forEachEntry(root) { entry ->
            val layout = entry.optJSONObject("layout") ?: return@forEachEntry
            migrateStaticScalesToVariations(layout.optJSONArray("staticObjects"))
        }
    }

    // 2 -> 3: the traffic lanes moved, and this is no longer a migration's business.
    //
    // Version 2 canonicalised lanes as a migration step, which fixed the payloads written
    // before it and nothing after -- and the lanes moved twice more, in v76.6 and v76.7, so
    // a theme saved on either renders with its road pulled back over the pavement and its
    // pedestrians walking on tarmac. `sceneObjectLayoutFromJson` now recanonicalises on every
    // load, at any version, which is why there is nothing to rewrite here.
    //
    // The bump is not decoration. It records that a version 2 payload may hold lane
    // coordinates that no longer describe any road the app draws, so a future reader knows
    // those numbers were already advisory when it was written.

    // 3 -> 4: two things a stored theme can carry that its generator can no longer produce.
    //
    // Both are the same shape of problem -- a generator was fixed and the themes saved before the
    // fix were not -- and both are genuinely one-shot, which is what makes them migrations rather
    // than another canonicalise-on-load: the *content* of a saved theme is the user's, and
    // rewriting it on every read would be the app second-guessing them forever. Rewriting it once,
    // for a defect the app itself shipped, is a repair.
    //
    //  - **Duplicate storefronts** (item 9 of `BACKLOG_v4_19.md`). Pass six made the catalogue emit
    //    exactly one restaurant and one bar per tile ([SceneObjectCatalog.singleShopPerVariant])
    //    and never migrated what was already saved, so a theme older than that can still show two
    //    of the same shop on one tile.
    //  - **Duplicate special vehicles** (items 11 and 14, closed for new themes in v4.20). The same
    //    story one release later: the per-type cap is applied where the types are rolled, which
    //    does nothing for a theme rolled before it existed.
    //
    // **Neither step may be able to fail the read.** `customThemeDataFromJsonString` turns any
    // exception from here into `CustomThemeData.EMPTY`, which is every saved theme the user has;
    // the DataStore corruption fixed in v3.1 is the reminder of what that costs. So each entry is
    // repaired inside its own `runCatching` and anything unexpected leaves that entry exactly as it
    // was found. The failure mode is "this one theme is not repaired", never "there are no themes".
    if (fromVersion < 4) {
        forEachEntry(root) { entry ->
            runCatching {
                val layout = entry.optJSONObject("layout") ?: return@runCatching
                migrateDuplicateStorefronts(layout.optJSONArray("staticObjects"))
                migrateDuplicateSpecialVehicles(layout.optJSONArray("cars"))
            }
        }
    }

    // 4 -> 5: the school a street saved before v5.6 never had (item 141, v5.7F).
    //
    // The same shape as 3 -> 4 -- a generator was changed and the themes saved before the change
    // were not -- and one-shot for the same reason: the content of a saved theme is the user's.
    // v5.6 split the shop band into three and put a school in the middle third; a street saved
    // before that has a restaurant and a bar and nothing in the middle third, so the school never
    // appears however the sliders are set. The maintainer's decision of 2026-09-21 is that these
    // streets get it (*"voglio che prendano la scuola"*).
    //
    // **It adds; it does not move.** See [SceneObjectCatalog.missingSchoolFor] for why a saved
    // street has nothing that could be promoted without taking a building the user can see off
    // their skyline. Runs after 3 -> 4 on purpose: the storefront repair may itself leave a
    // building in the middle third of a very old payload, and then there is nothing to add.
    //
    // Guarded exactly as 3 -> 4 is: a failure leaves that one theme as it was found.
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
 * Reads the stored objects the way the loader does ([staticSceneObjectFromJson]) because the
 * placement measures each one's drawn extent, and a street that cannot be read whole is left
 * alone rather than repaired from part of itself: the loader would reject it anyway, and a school
 * placed against half a street could stand on a house nobody measured. Idempotent, because a
 * second run finds the school it added in the middle third.
 *
 * Two callers, one rule: the 4 -> 5 step above, for the themes the store saved before v5.6, and
 * `parseThemeShare`, for a theme **file** exported before v5.6 and imported now (since v5.9F,
 * inventory I-93: the maintainer's decision of 2026-09-21 that those streets get the school covers
 * the files too, his answer of 2026-09-28). A file carries no store version, and the share format's
 * own version (1) has never changed, so the file cannot say it is old: it is simply given the school
 * if it has none, which a street from v5.6 on always has.
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
 * The same rule and the same arithmetic as [SceneObjectCatalog.singleShopPerVariant], applied to a
 * stored layout instead of a generated one: of the shop-band candidates
 * (`depthFraction >= SceneSpace.BUILDING_TOWER_MAX_DEPTH`), the depth-middle one of each of the
 * three bands (see [SceneSpace.RESTAURANT_MAX_DEPTH]) stays a shop, and every other one keeps its
 * slot, its x and its size and takes a tower depth interleaved across the tower band.
 *
 * **v5.6F added the third band here too, and this file is not one of the six places the pass
 * brief listed.** It is a seventh, and it is the one a compiler cannot find: the two halves were
 * written out as two `filter` calls rather than read from a table, so adding a storefront leaves
 * this migration keeping one shop per *half*-band -- which, on a payload that carries two
 * candidates in the same new third, is two identical schools in one frame, the exact defect this
 * function exists to remove. No layout this app has ever generated can produce that (a generated
 * shop stands at 0.444 or 0.711, which are in different thirds), so it is a hole rather than a
 * bug; it is closed because "the same rule and the same arithmetic" above is a claim, not a hope.
 *
 * Only `depthFraction` moves, and only for the surplus. A shop's depth *is* what makes it a shop
 * (see [SceneSpace.BUILDING_TOWER_MAX_DEPTH]), so this changes what a building is drawn as and
 * nothing else about the theme -- not its colours, not its density, not how many buildings it has.
 *
 * Idempotent by construction: after it runs there is at most one shop per third of the shop band,
 * so a second run finds nothing to move. That matters because a payload is migrated on every load
 * until the user next saves it.
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
 * The road side of the same story: [SceneObjectCatalog.capSpecialsToOnePerType] caps the types
 * where they are rolled, which repairs nothing that was rolled before it existed -- and eight of
 * the twelve *shipped* themes were in that state, so a user's saved copies of them are too.
 *
 * The first of each type is the one kept, matching the generator exactly, so a repaired theme and a
 * freshly generated one agree. Only `type` changes: the surplus vehicle keeps its lane, its slot,
 * its speed and its colour, so the theme still has the same number of cars on the same road.
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
 * Visits every saved theme in a payload, whether it overrides a built-in id or stands alone.
 *
 * Migrations that rewrite a theme's contents need both collections, and the two are stored under
 * different shapes -- an object keyed by built-in id, and a plain array -- so walking them is
 * worth doing once rather than at each step.
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
 * Converts each object's absolute `scale` into the relative size variation that replaced it.
 *
 * Before Group 4 the field carried the category's entire base size, so a house was saved at about
 * 1.5 and a tree at about 1.3 -- numbers that only meant anything next to the per-category base
 * scale they were rolled around. Dividing by that base recovers the variation the value was always
 * expressing, and [SceneSpace.legacyBaseScaleFor] is the only remaining record of what those bases
 * were.
 *
 * An unreadable or absent type falls back to leaving the value alone, which is wrong by at most
 * the base scale and is still a scene rather than a lost theme.
 */
private fun migrateStaticScalesToVariations(objects: JSONArray?) {
    if (objects == null) return
    for (i in 0 until objects.length()) {
        val obj = objects.optJSONObject(i) ?: continue
        val type = runCatching { SceneObjectType.valueOf(obj.optString("type")) }.getOrNull() ?: continue
        val legacyBase = SceneSpace.legacyBaseScaleFor(type)
        if (legacyBase <= 0f) continue
        // Migration reads the *old* absolute scale and divides it into the relative one. A
        // non-finite value here would write a non-finite value straight back into the store, so it
        // takes the base scale, which is what the object was drawn at before the axis existed.
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
    // A document with nothing unreadable in it is written exactly as it always was.
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
 * Puts the cars back into a built-in override that was saved without them, or without all of them.
 *
 * ### What it repairs, and why it has to exist
 *
 * Until this release, saving a theme wrote `rawLayout.cars.filter { keepCar(it) }` into the entry, so a
 * theme saved while the Cars density was low -- or while Cars were switched off -- was stored with
 * an **empty car list**. `SceneObjectRenderer.hasRoad` is `layout.cars.isNotEmpty()`, so that
 * theme lost its road and all its traffic permanently: raising the density afterwards filters a
 * list that has nothing left in it, while the settings screen goes on reporting "On - 100%"
 * because the *customization* is intact. It is the layout that was damaged.
 *
 * The save path no longer does that. This repairs the installs where it already happened, which
 * the fix alone cannot reach.
 *
 * ### The guard, which is deliberately narrow
 *
 * All of these must hold, or the entry is returned untouched:
 *
 *  1. it is a **built-in override** -- the key of the `overrides` map, not a standalone theme;
 *  2. the **built-in it overrides still defines cars** ([SceneObjectCatalog.builtinCarsFor],
 *     which reads the original and not the override);
 *  3. its `layout.cars` holds **fewer cars than that canonical list**;
 *  4. and, when the stored list is not empty, it is **exactly what the old save path would have
 *     written** at this entry's own baked density -- see [oldSaveWouldHaveWritten].
 *
 * Anything less certain is left alone: a standalone custom theme has no canonical layout to
 * compare against, so **it is never speculatively repaired**, and a built-in whose own definition
 * has no cars is not given any.
 *
 * ### Why a partial list is repaired too (v4.4)
 *
 * The empty list is the visible half of the defect. The other half is a list the old save path
 * *thinned* rather than emptied -- measured on the real ten-car layout, saving at 65% wrote 8
 * cars, at 50% wrote 6, at 20% wrote 1. Those keep a road, so they were not what the original
 * report was about, but they are damaged in the same way and just as permanently: the inventory
 * is capped for ever, so raising the density afterwards can never bring the missing traffic
 * back, and a list thinned to a single car canonicalises onto **one** lane, which leaves the
 * painted road derived from half a lane pair.
 *
 * It is repaired because it can be *proved* rather than assumed, on two independent grounds.
 * First, by enumeration of the writers: the only thing that ever puts a layout into `overrides`
 * is `snapshotEntry`, and a backup restore of data that came from it -- a theme *import* is
 * always a new standalone theme and never an override -- so for a built-in override a partial
 * car list has no author but the old save path. Second, by reconstruction:
 * [oldSaveWouldHaveWritten] rebuilds that author's output and requires an exact match before
 * anything is written.
 *
 * ### What it does not touch
 *
 * Only `layout.cars`. Not the customization -- so the density, the visibility and every colour the
 * user chose survive exactly, and a theme repaired while its density was 10% still shows 10% of
 * the traffic, now on a road. Not the name, not the theme colours, not the static objects.
 *
 * ### Idempotent by construction
 *
 * After a repair, condition 3 no longer holds, so running it again is a no-op. That is what makes
 * it safe to run on **every load** rather than as a one-off migration -- see
 * [customThemeDataFromJsonString], which is the single funnel every reader of this data goes
 * through, including the wallpaper service starting with no UI in sight.
 */
fun CustomThemeData.repairBuiltInOverrides(): CustomThemeData {
    if (overrides.isEmpty()) return this
    var changed = false
    val repaired = overrides.mapValues { (builtinId, entry) ->
        // Each override is repaired on its own: a repair that throws leaves that one theme as it
        // was read, and never costs the others (item 18, v5.9A).
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
 * The car list the pre-v4.3 save path would have written for [canonical] under [customization],
 * as it comes back off disk.
 *
 * `snapshotEntry` stored `rawLayout.cars.filter { keepCar(it) }` **and** the very customization it
 * filtered with, in the same entry, and every load then runs the list through
 * [SceneObjectCatalog.canonicaliseTraffic]. So a damaged entry carries its own proof: rebuilding
 * that expression from the canonical list and the entry's own baked customization has to
 * reproduce the stored list exactly, car for car.
 *
 * That is what turns the partial-inventory repair from a guess into a check. [legacyKeepCar] is a
 * threshold on a fixed per-car fraction, so the old filter could only ever emit one of eleven
 * nested subsets of a ten-car list; requiring an exact match against the one the entry's own
 * density selects refuses everything else, including any list this build cannot account for. If
 * the canonical layout is ever regenerated differently, the match simply stops succeeding and
 * nothing is written -- the failure mode is "leave it alone", which is the right one.
 *
 * **[legacyKeepCar], not the live selection.** The predicate here is the *author's*: the pre-v4.3
 * save path filtered with the threshold selection every release before v4.22 shipped, so its
 * output can only be reconstructed by that same frozen expression. v4.22 moved rendering to a
 * distributed count ([CarSelection]); following it here would silently stop every pre-v4.3
 * damaged install from matching, and the repair would never run again.
 */
private fun oldSaveWouldHaveWritten(
    canonical: List<CarObject>,
    customization: SceneCustomization,
): List<CarObject> =
    SceneObjectCatalog.canonicaliseTraffic(canonical.filter { customization.legacyKeepCar(it) })

/**
 * The stored blob, or `null` if there is one and it cannot be read at all.
 *
 * The distinction [customThemeDataFromJsonString] deliberately hides -- an absent store and a
 * document that is not JSON both read as `CustomThemeData.EMPTY`, which is what a *reader* wants. A
 * read-modify-write needs to tell them apart or it overwrites the second with a document derived
 * from nothing; see `CustomThemeStore.update` for what that cost.
 *
 * **Since v5.9A (item 18) `null` means the document itself, not one theme in it.** A document whose
 * entries are unreadable one by one reads with those entries set aside in
 * [CustomThemeData.unreadable], and writing it back keeps them, so an edit no longer has to be
 * refused for their sake.
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
 * Reads a saved-themes document **entry by entry** (item 18, v5.9A).
 *
 * `null` only when [raw] is not a JSON object at all, or the migrations fail on it as a whole (none
 * can today: each step that reads a theme's contents is guarded per entry). Otherwise every entry
 * that reads is returned as before, and every one that does not is kept in
 * [CustomThemeData.unreadable] with its bytes as they stand in [raw] -- **before** the migrations
 * below rewrite the parsed copy -- and the schema version those bytes are in. Until v5.9A the whole
 * read sat in one `catch`, so one damaged entry read as "no saved themes at all".
 *
 * Kept entries that an earlier write put in the `unreadableEntries` section are tried again on every
 * read: see [recoverUnreadable].
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
    // An explicit `null` holds nothing, so it reads as absent, as it always has.
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
    // Repaired on the way out, never on the way in: the bytes on disk are left as they are and
    // the fix is applied to what the app uses. That is one fewer write on a startup path, it
    // covers data that arrives later from a backup import just as well, and being idempotent
    // it needs no schema bump and no migration entry. See [repairBuiltInOverrides].
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
