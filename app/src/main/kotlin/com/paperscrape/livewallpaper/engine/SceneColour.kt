package com.paperscrape.livewallpaper.engine

/**
 * The one blend the scene's colour rules are built out of.
 *
 * ### Why this is not `ColorUtils.blendARGB`
 *
 * It is arithmetically that function, channel for channel and cast for cast -- and
 * `SceneColourBlendTest` (instrumented) proves it over a sweep rather than asserting it here.
 * What it is not is a call into `android.graphics.Color`, and that is the whole point: the JVM
 * suite has no Android framework, so every rule that reached `blendARGB` was a rule the host
 * could not evaluate. `ThemePreviewScene` had already worked around it by keeping a private copy
 * of this arithmetic, which is a second definition of "what a colour between two colours is" --
 * exactly the kind of copy `PreviewRendererAgreementTest` exists because of.
 *
 * With the blend here instead, `SceneCustomization.colorFor` and
 * `SceneObjectRenderer.windowGlassColor` both run on the host, so the gallery preview can ask the
 * *real* functions what a building's colour is rather than reimplementing them, and the tests can
 * check the answer. That is this project's own rule -- measure on the host, confirm on the phone
 * -- applied to the one place that was exempt from it.
 *
 * **No pixel moves.** The cast is the same truncating `(int)` on the same float sum, so a colour
 * that came out of `blendARGB` comes out of this identically, which is what the instrumented test
 * is for.
 */
internal object SceneColour {

    /** [from] at `ratio` 0, [to] at 1, each channel interpolated and truncated independently. */
    fun blendArgb(from: Int, to: Int, ratio: Float): Int {
        val inverse = 1f - ratio
        val a = ((from ushr 24 and 0xFF) * inverse + (to ushr 24 and 0xFF) * ratio).toInt()
        val r = ((from ushr 16 and 0xFF) * inverse + (to ushr 16 and 0xFF) * ratio).toInt()
        val g = ((from ushr 8 and 0xFF) * inverse + (to ushr 8 and 0xFF) * ratio).toInt()
        val b = ((from and 0xFF) * inverse + (to and 0xFF) * ratio).toInt()
        return (a shl 24) or (r shl 16) or (g shl 8) or b
    }
}
