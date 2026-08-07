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
    put("layer", layer)
    put("tileFractionX", tileFractionX.toDouble())
    put("scale", scale.toDouble())
    put("tappable", tappable)
}

fun staticSceneObjectFromJson(json: JSONObject): StaticSceneObject = StaticSceneObject(
    type = SceneObjectType.valueOf(json.getString("type")),
    layer = json.getInt("layer"),
    tileFractionX = json.getDouble("tileFractionX").toFloat(),
    scale = json.optDouble("scale", 1.0).toFloat(),
    tappable = json.optBoolean("tappable", false),
)

fun CarObject.toJson(): JSONObject = JSONObject().apply {
    put("laneYFraction", laneYFraction.toDouble())
    put("speedFraction", speedFraction.toDouble())
    put("startDelaySeconds", startDelaySeconds.toDouble())
    put("color", color)
    put("reverse", reverse)
}

fun carObjectFromJson(json: JSONObject): CarObject = CarObject(
    laneYFraction = json.getDouble("laneYFraction").toFloat(),
    speedFraction = json.getDouble("speedFraction").toFloat(),
    startDelaySeconds = json.getDouble("startDelaySeconds").toFloat(),
    color = json.getInt("color"),
    reverse = json.optBoolean("reverse", false),
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
    return SceneObjectLayout(staticObjects = staticObjects, cars = cars)
}

fun CustomThemeEntry.toJson(): JSONObject = JSONObject().apply {
    put("id", id)
    put("name", name)
    put("theme", theme.toJson())
    put("layout", layout.toJson())
}

fun customThemeEntryFromJson(json: JSONObject): CustomThemeEntry = CustomThemeEntry(
    id = json.getString("id"),
    name = json.getString("name"),
    theme = sceneThemeFromJson(json.getJSONObject("theme")),
    layout = sceneObjectLayoutFromJson(json.getJSONObject("layout")),
)

fun CustomThemeData.toJsonString(): String {
    val root = JSONObject()
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
