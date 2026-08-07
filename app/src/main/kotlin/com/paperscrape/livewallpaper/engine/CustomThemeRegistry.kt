package com.paperscrape.livewallpaper.engine

import java.util.concurrent.atomic.AtomicReference

/**
 * Holds the latest known [CustomThemeData] in memory so [ThemeCatalog.byId] and
 * [SceneObjectCatalog.layoutFor] can consult it synchronously.
 *
 * Filled from outside (see [com.paperscrape.livewallpaper.engine.PaperWallpaperService] and
 * `SettingsActivity`, both of which collect `CustomThemeStore.dataFlow` and call [update]) --
 * this object itself has no knowledge of DataStore/Context, keeping the engine package free of
 * Android persistence concerns.
 *
 * Starts empty, so there is always a safe, well-defined fallback (the hardcoded built-in
 * themes) before the first DataStore read completes.
 */
object CustomThemeRegistry {

    private val current = AtomicReference(CustomThemeData.EMPTY)
    private val generationCounter = java.util.concurrent.atomic.AtomicInteger(0)

    fun update(data: CustomThemeData) {
        current.set(data)
        generationCounter.incrementAndGet()
    }

    /**
     * Increments every time [update] is called. Anything that caches rendering state keyed by
     * theme id (e.g. [PaperRenderer]'s hill-path cache, [SceneObjectRenderer]'s layout cache)
     * must also key on this, because overriding/resetting a built-in theme changes what that
     * *same* id resolves to without the id itself changing -- an id-only cache would miss it.
     */
    fun generation(): Int = generationCounter.get()

    fun overrideThemeFor(builtinId: String): SceneTheme? = current.get().overrides[builtinId]?.theme

    fun overrideLayoutFor(builtinId: String): SceneObjectLayout? = current.get().overrides[builtinId]?.layout

    fun customEntry(id: String): CustomThemeEntry? = current.get().customThemes.firstOrNull { it.id == id }

    fun customThemes(): List<CustomThemeEntry> = current.get().customThemes

    fun overriddenBuiltinIds(): Set<String> = current.get().overrides.keys

    fun hasOverride(builtinId: String): Boolean = current.get().overrides.containsKey(builtinId)
}
