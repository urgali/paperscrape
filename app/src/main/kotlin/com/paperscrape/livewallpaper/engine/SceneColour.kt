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

    /**
     * The neutral grey that says how lit something is at [dayBlend], given the [day] and [night]
     * colours of whatever it stands among.
     *
     * **This exists because fixed art cannot dim.** Everything the scene tints dims at dusk as a
     * side effect of the tint: its colour is interpolated between the user's day and night values
     * and multiplied over a mask. A sprite that carries its own colours has no tint to
     * interpolate, and until v5.1 nothing supplied it one, so the palms stood at full midday
     * green under a midnight sky -- measured at (107,168,79) at 13:00 and the same at 23:00,
     * while the house wall beside them fell to 0.40 of its own daytime value.
     *
     * **The factor is derived, not chosen.** The pair itself says how much light that category
     * loses between noon and midnight: the ratio of the two colours' luminances. Taking that and
     * making a grey of it means a palm dims by the same factor as the tree standing next to it,
     * and goes on doing so after the user edits the pair.
     *
     * Luminance is Rec. 709 weights over the **encoded** channels rather than linearised ones,
     * because that is the space `MULTIPLY` works in: multiplying artwork by this grey reproduces
     * the drop the pair itself makes, not a drop that would be right in some other space. With
     * the shipped tree colours the two variants give 0.457 and 0.480.
     *
     * Grey and not the night colour, which is the whole distinction: a shade says how lit, a tint
     * says what colour, and a palm is not the user's tree green. At [dayBlend] 1 this is opaque
     * white -- the `MULTIPLY` identity -- so a shaded blit at noon is byte for byte the untinted
     * blit it replaces.
     */
    fun neutralShade(day: Int, night: Int, dayBlend: Float): Int {
        val dayLevel = encodedLuminance(day)
        // A day colour with no luminance at all is a black object, and there is nothing for night
        // to be a fraction of. Leave the artwork alone rather than divide by zero.
        val ratio = if (dayLevel <= 0f) 1f else (encodedLuminance(night) / dayLevel).coerceIn(0f, 1f)
        val level = (255f * (ratio + (1f - ratio) * dayBlend.coerceIn(0f, 1f))).toInt().coerceIn(0, 255)
        return (0xFF shl 24) or (level shl 16) or (level shl 8) or level
    }

    /** Rec. 709 weights over the packed channels as stored, for the reason [neutralShade] gives. */
    internal fun encodedLuminance(color: Int): Float =
        0.2126f * (color ushr 16 and 0xFF) + 0.7152f * (color ushr 8 and 0xFF) + 0.0722f * (color and 0xFF)

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
