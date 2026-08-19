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

    /** The saved entry (override or standalone custom theme) for this id, if any. */
    private fun entryFor(id: String): CustomThemeEntry? =
        current.get().overrides[id] ?: current.get().customThemes.firstOrNull { it.id == id }

    /**
     * Resolves which [SceneCustomization] should actually apply when rendering [themeId] right
     * now. Priority:
     *  1. If [themeId] is the theme currently being live-edited ([pendingThemeId] matches it),
     *     the in-progress (not yet saved) [pendingCustomization] always wins -- even if this
     *     theme already has a saved override/custom entry, since the user is actively editing
     *     *that exact* theme right now and needs to see their changes reflected immediately, and
     *     "Replace with current" needs to actually pick up what they just changed rather than
     *     re-saving a stale snapshot from a previous edit.
     *  2. Otherwise, a saved theme (override or standalone custom) uses its own baked-in
     *     customization from when it was last saved -- editing scene objects for a *different*
     *     theme must never change how this one already looks.
     *  3. Otherwise, the theme's untouched default look.
     */
    fun resolveActiveCustomization(
        themeId: String,
        pendingCustomization: SceneCustomization,
        pendingThemeId: String?,
    ): SceneCustomization {
        if (themeId == pendingThemeId) return pendingCustomization
        entryFor(themeId)?.let { return it.customization }
        return defaultCustomizationFor(themeId)
    }
}
