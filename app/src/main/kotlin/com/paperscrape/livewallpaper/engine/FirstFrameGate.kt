package com.paperscrape.livewallpaper.engine

/**
 * Holds an engine's first frame until the scene it would draw is the user's (v5.10B).
 *
 * An engine starts with the settings of a fresh install -- Sunset, the real hour -- because its
 * preferences are read asynchronously, and the renderer is built as soon as the surface exists. Until
 * v5.10B the render thread drew from that at once: every start of the wallpaper (an update, a
 * reboot, a change of wallpaper) showed **Sunset for about a tenth of a second** before the user's
 * own theme, 5 starts out of 5 on the BV6600, and spent ~0.38 s decoding and uploading the artwork of
 * a scene it then threw away (v5.10A, filmed). The same held for a saved theme until the saved themes
 * had been read.
 *
 * So nothing is drawn until **both** have arrived -- the settings and the saved themes, each on its
 * first emission -- or until the engine has waited [WAIT_LIMIT_MS] for them, the backstop for a store
 * that never answers: a wallpaper that stays black would be worse than one that starts on the wrong
 * theme. [onOpen] runs once, when the gate opens, so the engine can start the frames it was holding.
 *
 * Main thread only: the two collectors and the timeout all run there. `FirstFrameGateTest` covers
 * the order of the three events.
 */
internal class FirstFrameGate(private val onOpen: () -> Unit) {

    private var settingsArrived = false
    private var savedThemesArrived = false

    /** True once the engine may draw; it never closes again. */
    var isOpen = false
        private set

    /** The first settings are published to the scene (queued on the thread that draws it). */
    fun settingsArrived() {
        settingsArrived = true
        if (savedThemesArrived) open()
    }

    /** The first saved themes are published to the scene. */
    fun savedThemesArrived() {
        savedThemesArrived = true
        if (settingsArrived) open()
    }

    /** [WAIT_LIMIT_MS] have passed since the engine was created: draw with whatever has arrived. */
    fun waitedTooLong() = open()

    private fun open() {
        if (isOpen) return
        isOpen = true
        onOpen()
    }

    companion object {
        /**
         * How long an engine waits for its settings before drawing anyway. Both stores normally
         * answer within tens of milliseconds of the engine's creation (v5.10A: the settings about
         * 60 ms after the renderer was built); this only matters when one of them does not.
         */
        const val WAIT_LIMIT_MS = 2_000L
    }
}
