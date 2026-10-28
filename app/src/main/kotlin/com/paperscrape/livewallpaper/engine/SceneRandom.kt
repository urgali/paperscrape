package com.paperscrape.livewallpaper.engine

import kotlin.random.Random

/**
 * The random source of the sky's visitors: Santa's sleigh and its gifts, the fireworks and the Headless Horseman's flights.
 * `Random.Default` in the app. A test that draws the same frames twice puts a seeded one here before it builds a renderer,
 * so the visitors fly the same way both times.
 */
internal object SceneRandom {
    @JvmField
    var source: Random = Random.Default
}
