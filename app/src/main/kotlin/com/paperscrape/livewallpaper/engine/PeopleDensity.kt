package com.paperscrape.livewallpaper.engine

/**
 * How many pedestrians are on the street right now, given the two densities and the time of day.
 *
 * Pure and separate from the renderer because both halves of it are worth pinning: the crossfade,
 * and the upgrade rule for users who set a density before there were two of them.
 */
object PeopleDensity {

    /**
     * The density in force at [dayBlend], linearly between the two.
     *
     * A crossfade rather than a switch at some threshold: `dayBlend` is the same 0..1 the sky, the
     * hills and every object colour already blend with, so the population changes over the length
     * of dusk exactly as the light does. A threshold would empty the street between two frames,
     * which reads as a bug however correct the two endpoints are.
     */
    fun at(dayDensity: Float, nightDensity: Float, dayBlend: Float): Float {
        val blend = dayBlend.coerceIn(0f, 1f)
        val day = dayDensity.coerceIn(0f, 1f)
        val night = nightDensity.coerceIn(0f, 1f)
        return night + (day - night) * blend
    }

    /**
     * The night density for a user upgrading from a build that had only one.
     *
     * [stored] is null when the preference has never been written, which is exactly the state
     * every pre-v2.12 install is in. Falling back to the daytime density means the scene after the
     * upgrade is the scene before it -- the same number of pedestrians at every hour, as there was
     * when one slider governed both. Any other default would silently change what an existing user
     * had set up, which is not something a settings refactor is entitled to do.
     */
    fun resolveNightDensity(stored: Float?, dayDensity: Float): Float = stored ?: dayDensity
}
