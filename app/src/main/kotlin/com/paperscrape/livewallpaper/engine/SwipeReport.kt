package com.paperscrape.livewallpaper.engine

/**
 * Whether the home screen has moved the wallpaper with a swipe -- what *Swipe scroll* needs before it
 * can do anything (v5.10E, inventory I-222, the maintainer's *sì* of 2026-09-30 to row 11).
 *
 * *Swipe scroll* follows the offset the home screen reports through `onOffsetsChanged`, and many home
 * screens never report one: the project's own phone has never called it (`CLAUDE.md` §7), and a home
 * screen with a single page has nothing to move. The switch read on there over nothing. Now the
 * wallpaper writes `WallpaperSettings.swipeReported` the first time it sees the home screen's offset
 * **move between pages**, and the switch reads on only after that (`SettingsUiModel.swipeScroll`).
 *
 * **Moved between pages, not called.** A launcher may report its offset once, when the wallpaper
 * attaches, and never again because it has one page; the system's picker reports offsets to its
 * preview while its own screen is dragged; and the framework's own default offsets, a relayout or a
 * rotation can move the value without a swipe. So: an engine that is not a preview ([moved]'s caller
 * decides), a home screen that says it has pages to scroll through (`xOffsetStep` above 0 -- the
 * framework's default is not), and an offset that differs from the first one this engine received with
 * pages by more than [MIN_MOVE] (the read-only review of v5.10E, D5).
 */
internal class SwipeReport {

    private var firstOffset = Float.NaN

    /**
     * Records [xOffset], reported with [xOffsetStep]; true when it is a move, between pages, away from the
     * first offset this engine received with pages.
     */
    fun moved(xOffset: Float, xOffsetStep: Float): Boolean {
        if (!xOffset.isFinite() || !(xOffsetStep > 0f)) return false
        if (firstOffset.isNaN()) {
            firstOffset = xOffset
            return false
        }
        return kotlin.math.abs(xOffset - firstOffset) > MIN_MOVE
    }

    companion object {
        /**
         * The smallest offset change that is a swipe: a thousandth of the home screens' whole width,
         * well under a pixel of any one page and far over a float's rounding of the same value.
         */
        const val MIN_MOVE = 0.001f
    }
}
