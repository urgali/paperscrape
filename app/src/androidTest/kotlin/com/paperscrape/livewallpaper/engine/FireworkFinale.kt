package com.paperscrape.livewallpaper.engine

import kotlin.random.Random

/**
 * **The fireworks' first finale, on a seeded `SceneRandom.source`**, for the tests that draw its fullest moment
 * (`GlDrawCallTest`, `FrameAllocationTest`).
 *
 * The show is the same on every run with the source seeded ([FireworkEffect]): on New Year's Eve, where nothing else in
 * the sky draws from the source (no sleigh, no Horseman), the renderer's fireworks are those of a bare [FireworkEffect] run
 * frame for frame. So [seed] is found here, once, by running the bare effect: the first seed whose first finale holds a
 * burst of the light ([FireworkEffect.STYLE_LIGHT], its halo and its sky glow), so the frames these tests count draw the
 * glows too, whatever the program's arithmetic becomes.
 */
internal object FireworkFinale {

    /** Frames of the show, at the wallpaper's 30 a second, from its start to [seconds] in. */
    fun frames(seconds: Double): Int = Math.round(seconds / FireworkEffect.FRAME_SECONDS).toInt()

    /**
     * Where the counted frames end: 4.5 s after the first finale's first burst. The last of its seven went off 1.8 s
     * before ([FireworkEffect.FINALE_STEP] apart), so the counted frames hold every burst of the finale in the sky, the
     * last ones with their pieces still falling: not the whole life of the last pieces, which can last up to
     * [FireworkEffect.FLOWER_HOLD] + [FireworkEffect.CONFETTI_LIFE] + [FireworkEffect.FLOWER_TAIL] after their burst.
     */
    const val END_SECONDS = FireworkEffect.FIRST_FINALE_AT + 4.5

    val seed: Int by lazy {
        val saved = SceneRandom.source
        try {
            (1..200).firstOrNull { candidate ->
                SceneRandom.source = Random(candidate)
                val effect = FireworkEffect()
                var light = false
                effect.watcher = FireworkEffect.LaunchWatcher { _, _, _, _, source, style, _, _, _ ->
                    if (source == FireworkEffect.EVENT_FINALE && style == FireworkEffect.STYLE_LIGHT) light = true
                }
                effect.advanceForTest(frames(END_SECONDS), 720f, 1440f)
                light
            } ?: error("no seed from 1 to 200 gives the first finale a burst of the light")
        } finally {
            SceneRandom.source = saved
        }
    }

    /** Runs [block] with `SceneRandom.source` seeded with [seed], and puts the source back. */
    inline fun <T> seeded(block: () -> T): T {
        val saved = SceneRandom.source
        SceneRandom.source = Random(seed)
        try {
            return block()
        } finally {
            SceneRandom.source = saved
        }
    }
}
