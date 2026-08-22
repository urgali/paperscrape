package com.paperscrape.livewallpaper.engine

/**
 * The two clock times that bound today's light, and whether they came from a real position
 * (**P2-6**).
 *
 * ### Why these three values are one object
 *
 * They used to be three separate fields on the wallpaper engine — `sunriseHour`, `sunsetHour` and
 * `hasFixLocation` — written together by the location callbacks on the main thread and read
 * together by [PaperWallpaperService]'s frame callback on the **render** thread. Three plain
 * fields, no `@Volatile`, no `queueEvent`, while every one of their neighbours on that class
 * (`settings`, `lastLocationFix`, `lastWeatherFetchMillis`, `lastWeatherFetchLocation`,
 * `publishedWeatherStatus`) already carried `@Volatile` and a comment saying why. These three were
 * simply missed.
 *
 * Two distinct defects followed, and only the second is obvious:
 *
 *  - **Visibility.** Nothing established a happens-before edge between the write and the read. The
 *    render thread reads them in a hot loop, so it is entitled to keep reading a stale copy
 *    indefinitely — a located sunrise that never arrives.
 *  - **Coherence, which `@Volatile` on each field would *not* have fixed.** Three separate writes
 *    are three separate publications whatever they are marked, so a reader can land between them
 *    and take the new sunrise with the old sunset. That pair belongs to no location: a fix moving
 *    from Florence to Reykjavík would briefly give the scene a day length neither city has, and
 *    `dayLengthHours` feeds the whole day/night blend, the sun's arc and the terminator.
 *
 * Making the three fields `@Volatile` would therefore have bought the first property and left the
 * second, which is why this is a value object instead. One `@Volatile` reference to an immutable
 * triple publishes all three at once: the reader's single read either sees the whole previous day
 * or the whole new one, never a mixture, and the volatile pair gives the visibility edge as well.
 *
 * ### Why not `queueEvent`
 *
 * The renderer's own scene state goes through `GlRenderThread.queueEvent`, which is the right
 * model for it and is untouched here. These three are not renderer state: they are engine state
 * that the frame callback consults on its way *into* the renderer, and they are also read by the
 * location path itself on the main thread. Routing them through the render thread would mean the
 * main thread could no longer ask "do we have a fix yet" without a round trip, for no gain — the
 * value is a plain immutable snapshot, which is the cheapest safe way to share one.
 *
 * ### Cost
 *
 * One allocation per location fix, which arrives at most every few minutes, and one volatile read
 * per frame in place of three plain reads. Nothing is locked, and the draw path itself never sees
 * any of it.
 */
internal class SolarDay private constructor(
    val sunriseHour: Float,
    val sunsetHour: Float,
    /** Whether [sunriseHour] and [sunsetHour] came from a position rather than from the defaults. */
    val hasFix: Boolean,
) {

    companion object {

        /**
         * No position known: the same 6:00/20:00 the engine fell back to before, carried in the
         * snapshot itself.
         *
         * Holding the defaults here rather than at the read site is what keeps the reader down to
         * one field access. The previous code read `hasFixLocation` and then chose between the
         * stored hour and a literal, which is three reads of two independently-written fields —
         * the shape the coherence problem lived in.
         */
        val NONE = SolarDay(sunriseHour = 6f, sunsetHour = 20f, hasFix = false)

        /** Today's light at a known position. */
        fun located(sunriseHour: Float, sunsetHour: Float): SolarDay =
            SolarDay(sunriseHour, sunsetHour, hasFix = true)
    }
}
