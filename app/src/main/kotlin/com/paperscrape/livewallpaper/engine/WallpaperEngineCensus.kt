package com.paperscrape.livewallpaper.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Whether this process is drawing the phone's wallpaper right now: at least one
 * [PaperWallpaperService] engine alive that is not a picker's preview.
 *
 * It exists for one sentence of the settings screen. The button under the preview read "Set as
 * wallpaper" whatever the phone showed, so someone who had just set PaperScrape from the system
 * picker was invited to do it again (assessment v5.7, row 13). It now says so when it already is.
 *
 * **Counted from the engines, not asked of `WallpaperManager.getWallpaperInfo()`.** That call
 * answers the wrong thing in a state a user can reach: after an Android "Force stop", setting
 * PaperScrape again from the system picker records it as the wallpaper and binds nothing -- the
 * home screen stays blank, `dumpsys wallpaper` shows our component with `mEngine=null` -- and
 * `getWallpaperInfo()` returns our component all the same, because it reads the connection the
 * system opened, not the engine that should have come of it (`WallpaperManagerService`,
 * `connection.mInfo`, API 29 and API 34 alike). An engine exists in this process only once the
 * system has attached one, which is the `mEngine` the command line checks, and the wallpaper
 * service runs in the app's own process (the manifest gives it no `android:process`), so the
 * settings screen can count them. The preview engine the picker runs is left out: it draws a
 * preview, not the wallpaper. Measured on the BV6600 in all three states (ROADMAP row A20).
 *
 * **It also counts the engines drawing with `Canvas`** (v5.10B), previews included: those read
 * [SpriteCache] on every frame, so it is the one thing that stops the settings screen giving the
 * cache back when it closes (`SettingsActivity.onStop`). A GL engine uploads each sprite and lets
 * the bitmap go; a `Canvas` one would have to decode its whole scene again at once.
 *
 * Main thread only: engine lifecycle callbacks are delivered there, and Compose reads it there.
 */
internal class EngineCensus {
    private var drawing = 0
    private var canvasEngines = 0
    private val state = MutableStateFlow(false)

    /** True while an engine of this process, preview or not, draws with the `Canvas` fallback. */
    val anyCanvasEngine: Boolean get() = canvasEngines > 0

    /** An engine gave up on GL and draws with `Canvas` from now on; see [anyCanvasEngine]. */
    fun engineFellBackToCanvas() {
        canvasEngines++
    }

    /** An engine that had fallen back to `Canvas` is gone. */
    fun canvasEngineDestroyed() {
        canvasEngines = (canvasEngines - 1).coerceAtLeast(0)
    }

    /** True while at least one engine that is not a preview is alive. */
    val isTheWallpaper: StateFlow<Boolean> = state.asStateFlow()

    fun engineCreated(isPreview: Boolean) {
        if (isPreview) return
        drawing++
        state.value = true
    }

    fun engineDestroyed(isPreview: Boolean) {
        if (isPreview) return
        drawing = (drawing - 1).coerceAtLeast(0)
        state.value = drawing > 0
    }
}

/** The one census of this process: [PaperWallpaperService]'s engines report to it, the settings screen reads it. */
internal val WallpaperEngineCensus = EngineCensus()
