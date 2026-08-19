package com.paperscrape.livewallpaper.engine

import org.json.JSONArray
import org.json.JSONObject

/**
 * A saved custom theme: either a full replacement for one of the built-in themes (its [id]
 * matches a [ThemeCatalog.ALL] id, e.g. "christmas") or a fully independent theme the user
 * created from scratch (its [id] looks like "custom:<token>").
 *
 * Both [theme] and [layout] are complete snapshots — everything needed to render the scene,
 * with no dependency on the original built-in definition. This is what makes "Reset to default"
 * trivial: it just deletes the override, and [ThemeCatalog.byId] naturally falls back to the
 * hardcoded built-in again.
 */
data class CustomThemeEntry(
    val id: String,
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
) {
    companion object {
        val EMPTY = CustomThemeData()
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
}

fun staticSceneObjectFromJson(json: JSONObject): StaticSceneObject = StaticSceneObject(
    type = SceneObjectType.valueOf(json.getString("type")),
    // Falls back to the old discrete "layer" (0..8 row index) field, converted to an equivalent
    // continuous fraction, for custom themes saved before the continuous-depth refactor -- so an
    // existing user's saved custom theme still loads instead of crashing on a missing key.
    depthFraction = if (json.has("depthFraction")) {
        json.getDouble("depthFraction").toFloat()
    } else {
        json.optInt("layer", 0) / 8f
    },
    tileFractionX = json.getDouble("tileFractionX").toFloat(),
    scale = json.optDouble("scale", 1.0).toFloat(),
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
    laneYFraction = json.getDouble("laneYFraction").toFloat(),
    speedFraction = json.getDouble("speedFraction").toFloat(),
    startDelaySeconds = json.getDouble("startDelaySeconds").toFloat(),
    color = json.getInt("color"),
    reverse = json.optBoolean("reverse", false),
    // optString + runCatching: existing saved custom themes from before this field existed
    // simply get CarType.PLAIN, the same vehicle they always rendered as.
    type = runCatching { CarType.valueOf(json.optString("type", "PLAIN")) }.getOrDefault(CarType.PLAIN),
)

fun SceneObjectLayout.toJson(): JSONObject = JSONObject().apply {
    put("staticObjects", JSONArray(staticObjects.map { it.toJson() }))
    put("cars", JSONArray(cars.map { it.toJson() }))
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
    )
}

fun ObjectVariantConfig.toJson(): JSONObject = JSONObject().apply {
    put("visible", visible)
    put("density", density.toDouble())
    put("colorDay1", colorDay1)
    put("colorNight1", colorNight1)
    put("colorDay2", colorDay2)
    put("colorNight2", colorNight2)
}

fun objectVariantConfigFromJson(json: JSONObject, default: ObjectVariantConfig): ObjectVariantConfig = ObjectVariantConfig(
    visible = json.optBoolean("visible", default.visible),
    density = json.optDouble("density", default.density.toDouble()).toFloat(),
    colorDay1 = if (json.has("colorDay1")) json.getInt("colorDay1") else default.colorDay1,
    colorNight1 = if (json.has("colorNight1")) json.getInt("colorNight1") else default.colorNight1,
    colorDay2 = if (json.has("colorDay2")) json.getInt("colorDay2") else default.colorDay2,
    colorNight2 = if (json.has("colorNight2")) json.getInt("colorNight2") else default.colorNight2,
)

fun SceneCustomization.toJson(): JSONObject = JSONObject().apply {
    put("houses", houses.toJson())
    put("buildings", buildings.toJson())
    put("cars", cars.toJson())
    put("parasols", parasols.toJson())
    put("people", people.toJson())
    put("trees", trees.toJson())
    put("snowmen", snowmen.toJson())
    put("gifts", gifts.toJson())
    put("balloons", balloons.toJson())
    put("penguins", penguins.toJson())
    put("bunnies", bunnies.toJson())
    put("easterEggs", easterEggs.toJson())
    put("pumpkins", pumpkins.toJson())
    put("hillsVariation", hillsVariation.toDouble())
    put("hillsColorDay", hillsColorDay)
    put("hillsColorNight", hillsColorNight)
    put("mountainsFront", JSONObject().apply {
        put("visible", mountainsFront.visible)
        put("density", mountainsFront.density.toDouble())
        put("colorDay", mountainsFront.colorDay)
        put("colorNight", mountainsFront.colorNight)
    })
    put("mountainsBack", JSONObject().apply {
        put("visible", mountainsBack.visible)
        put("density", mountainsBack.density.toDouble())
        put("colorDay", mountainsBack.colorDay)
        put("colorNight", mountainsBack.colorNight)
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
    })
    put("sun", JSONObject().apply { put("visible", sun.visible); put("color", sun.color) })
    put("moon", JSONObject().apply { put("visible", moon.visible); put("color", moon.color); put("realisticPhases", moon.realisticPhases) })
    put("clouds", JSONObject().apply {
        put("visible", clouds.visible); put("density", clouds.density.toDouble())
        put("colorDay", clouds.colorDay); put("colorNight", clouds.colorNight)
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
    })
    put("rainbow", JSONObject().apply {
        put("visible", rainbow.visible); put("opacity", rainbow.opacity.toDouble())
    })
    put("fallColorsEnabled", fallColorsEnabled)
    put("winterColorsEnabled", winterColorsEnabled)
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
        trees = json.optJSONObject("trees")?.let { objectVariantConfigFromJson(it, defaults.trees) } ?: defaults.trees,
        // Seasonal decorations ARE part of a saved custom theme's JSON now -- per-theme editable
        // and saveable exactly like the structural categories above (see the ObjectCategory doc
        // comment in WallpaperPrefs.kt), so saving "Christmas with snowmen turned off" needs to
        // actually persist that choice, not silently drop it.
        snowmen = json.optJSONObject("snowmen")?.let { objectVariantConfigFromJson(it, defaults.snowmen) } ?: defaults.snowmen,
        gifts = json.optJSONObject("gifts")?.let { objectVariantConfigFromJson(it, defaults.gifts) } ?: defaults.gifts,
        balloons = json.optJSONObject("balloons")?.let { objectVariantConfigFromJson(it, defaults.balloons) } ?: defaults.balloons,
        penguins = json.optJSONObject("penguins")?.let { objectVariantConfigFromJson(it, defaults.penguins) } ?: defaults.penguins,
        bunnies = json.optJSONObject("bunnies")?.let { objectVariantConfigFromJson(it, defaults.bunnies) } ?: defaults.bunnies,
        easterEggs = json.optJSONObject("easterEggs")?.let { objectVariantConfigFromJson(it, defaults.easterEggs) } ?: defaults.easterEggs,
        pumpkins = json.optJSONObject("pumpkins")?.let { objectVariantConfigFromJson(it, defaults.pumpkins) } ?: defaults.pumpkins,
        hillsVariation = json.optDouble("hillsVariation", defaults.hillsVariation.toDouble()).toFloat(),
        hillsColorDay = if (json.has("hillsColorDay")) json.optInt("hillsColorDay") else defaults.hillsColorDay,
        hillsColorNight = if (json.has("hillsColorNight")) json.optInt("hillsColorNight") else defaults.hillsColorNight,
        mountainsFront = json.optJSONObject("mountainsFront")?.let {
            MountainLayerConfig(
                visible = it.optBoolean("visible", defaults.mountainsFront.visible),
                density = it.optDouble("density", defaults.mountainsFront.density.toDouble()).toFloat(),
                colorDay = it.optInt("colorDay", defaults.mountainsFront.colorDay),
                colorNight = it.optInt("colorNight", defaults.mountainsFront.colorNight),
            )
        } ?: defaults.mountainsFront,
        mountainsBack = json.optJSONObject("mountainsBack")?.let {
            MountainLayerConfig(
                visible = it.optBoolean("visible", defaults.mountainsBack.visible),
                density = it.optDouble("density", defaults.mountainsBack.density.toDouble()).toFloat(),
                colorDay = it.optInt("colorDay", defaults.mountainsBack.colorDay),
                colorNight = it.optInt("colorNight", defaults.mountainsBack.colorNight),
            )
        } ?: defaults.mountainsBack,
        lake = json.optJSONObject("lake")?.let {
            LakeConfig(
                visible = it.optBoolean("visible", defaults.lake.visible),
                colorDay = it.optInt("colorDay", defaults.lake.colorDay),
                colorNight = it.optInt("colorNight", defaults.lake.colorNight),
                height = it.optDouble("height", defaults.lake.height.toDouble()).toFloat(),
                sailboatsVisible = it.optBoolean("sailboatsVisible", defaults.lake.sailboatsVisible),
                sailboatsDensity = it.optDouble("sailboatsDensity", defaults.lake.sailboatsDensity.toDouble()).toFloat(),
                dolphinsVisible = it.optBoolean("dolphinsVisible", defaults.lake.dolphinsVisible),
                dolphinsDensity = it.optDouble("dolphinsDensity", defaults.lake.dolphinsDensity.toDouble()).toFloat(),
            )
        } ?: defaults.lake,
        birds = json.optJSONObject("birds")?.let { b ->
            val colorsArray = b.optJSONArray("colors")
            val colors = if (colorsArray != null) {
                (0 until colorsArray.length()).map { idx ->
                    val c = colorsArray.getJSONObject(idx)
                    BirdColorWeight(c.optInt("color"), c.optDouble("weight", 0.25).toFloat())
                }
            } else {
                defaults.birds.colors
            }
            BirdsConfig(
                visible = b.optBoolean("visible", defaults.birds.visible),
                density = b.optDouble("density", defaults.birds.density.toDouble()).toFloat(),
                nightBirds = b.optBoolean("nightBirds", defaults.birds.nightBirds),
                colors = colors,
            )
        } ?: defaults.birds,
        stars = json.optJSONObject("stars")?.let {
            StarsConfig(
                visible = it.optBoolean("visible", defaults.stars.visible),
                density = it.optDouble("density", defaults.stars.density.toDouble()).toFloat(),
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
                sunCloudHeight = it.optDouble("sunCloudHeight", defaults.sky.sunCloudHeight.toDouble()).toFloat(),
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
                density = it.optDouble("density", defaults.clouds.density.toDouble()).toFloat(),
                colorDay = it.optInt("colorDay", defaults.clouds.colorDay),
                colorNight = it.optInt("colorNight", defaults.clouds.colorNight),
            )
        } ?: defaults.clouds,
        precipitation = json.optJSONObject("precipitation")?.let {
            PrecipitationConfig(
                visible = it.optBoolean("visible", defaults.precipitation.visible),
                type = it.optString("type", defaults.precipitation.type.name).let { name ->
                    runCatching { PrecipitationType.valueOf(name) }.getOrDefault(defaults.precipitation.type)
                },
                intensity = it.optDouble("intensity", defaults.precipitation.intensity.toDouble()).toFloat(),
                rainColorDay = it.optInt("rainColorDay", defaults.precipitation.rainColorDay),
                rainColorNight = it.optInt("rainColorNight", defaults.precipitation.rainColorNight),
                snowColorDay = it.optInt("snowColorDay", defaults.precipitation.snowColorDay),
                snowColorNight = it.optInt("snowColorNight", defaults.precipitation.snowColorNight),
                thunderstorm = it.optBoolean("thunderstorm", defaults.precipitation.thunderstorm),
            )
        } ?: defaults.precipitation,
        rainbow = json.optJSONObject("rainbow")?.let {
            RainbowConfig(
                visible = it.optBoolean("visible", defaults.rainbow.visible),
                opacity = it.optDouble("opacity", defaults.rainbow.opacity.toDouble()).toFloat(),
            )
        } ?: defaults.rainbow,
        fallColorsEnabled = json.optBoolean("fallColorsEnabled", defaults.fallColorsEnabled),
        winterColorsEnabled = json.optBoolean("winterColorsEnabled", defaults.winterColorsEnabled),
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

fun customThemeEntryFromJson(json: JSONObject): CustomThemeEntry = CustomThemeEntry(
    id = json.getString("id"),
    name = json.getString("name"),
    theme = sceneThemeFromJson(json.getJSONObject("theme")),
    layout = sceneObjectLayoutFromJson(json.getJSONObject("layout")),
    customization = sceneCustomizationFromJson(json.optJSONObject("customization")),
)

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
const val CUSTOM_THEME_SCHEMA_VERSION = 3

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

    // Future breaking changes add one step each, in order, above this line:
    //   if (fromVersion < 4) { ...rewrite root...; }
    root.put("schemaVersion", CUSTOM_THEME_SCHEMA_VERSION)
    return CUSTOM_THEME_SCHEMA_VERSION
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
        val legacyScale = obj.optDouble("scale", legacyBase.toDouble())
        obj.put("scale", legacyScale / legacyBase)
    }
}


fun CustomThemeData.toJsonString(): String {
    val root = JSONObject()
    // Written first so it is present even in a payload that is later truncated by a storage
    // failure, which makes a partially-written file identifiable rather than merely corrupt.
    root.put("schemaVersion", CUSTOM_THEME_SCHEMA_VERSION)
    val overridesJson = JSONObject()
    overrides.forEach { (builtinId, entry) -> overridesJson.put(builtinId, entry.toJson()) }
    root.put("overrides", overridesJson)
    root.put("customThemes", JSONArray(customThemes.map { it.toJson() }))
    return root.toString()
}

fun customThemeDataFromJsonString(raw: String?): CustomThemeData {
    if (raw.isNullOrBlank()) return CustomThemeData.EMPTY
    return try {
        val root = JSONObject(raw)
        val version = root.optInt("schemaVersion", CUSTOM_THEME_SCHEMA_VERSION_LEGACY)
        migrateCustomThemeJson(root, version)
        val overridesJson = root.optJSONObject("overrides") ?: JSONObject()
        val overrides = mutableMapOf<String, CustomThemeEntry>()
        overridesJson.keys().forEach { key ->
            overrides[key] = customThemeEntryFromJson(overridesJson.getJSONObject(key))
        }
        val customArray = root.optJSONArray("customThemes") ?: JSONArray()
        val customThemes = (0 until customArray.length()).map { customThemeEntryFromJson(customArray.getJSONObject(it)) }
        CustomThemeData(overrides = overrides, customThemes = customThemes)
    } catch (_: Exception) {
        // Corrupt/unexpected data should never crash the wallpaper -- fall back to "nothing saved".
        CustomThemeData.EMPTY
    }
}
