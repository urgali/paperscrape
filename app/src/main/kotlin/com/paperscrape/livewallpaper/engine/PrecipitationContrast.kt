package com.paperscrape.livewallpaper.engine

/**
 * How far the rain's colour is carried away from the sky it falls across -- the one rule
 * `PaperRenderer.drawPrecipitation` draws with and `PrecipitationContrastTest` measures.
 *
 * ### Why this is here and not in the renderer
 *
 * It was a private function of `PaperRenderer` until v5.9C (inventory row I-46), and the test
 * measured a **copy** of it, because the renderer needs a `Context` and cannot be built on the JVM
 * (`ROADMAP.md` B5). A copy is a second definition of the rule: a change to the renderer's would
 * leave every assertion in the test passing about the old one. The function never needed the
 * renderer -- it is arithmetic on four numbers and a colour -- so it lives where both can call it,
 * and the blend is [SceneColour.blendArgb], which is `ColorUtils.blendARGB` channel for channel and
 * cast for cast (`SceneColourBlendTest`) without the call into `android.graphics.Color` that the
 * JVM suite cannot make. The colour the renderer draws is the colour it drew before, to the bit.
 */
internal object PrecipitationContrast {

    /**
     * Rec. 601 luma of [color]'s RGB, the weighting the rule is stated in: the same integer weights
     * as the renderer's own `rec601Luma` (the rain, the waterline and the waves) and
     * [StormAtmosphere.dim]. The gallery card's rain reads it here (`ThemePreviewScenes.cardRainColour`).
     */
    fun rec601Luma(color: Int): Float {
        val r = (color shr 16) and 0xFF
        val g = (color shr 8) and 0xFF
        val b = color and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000f
    }

    /**
     * [base] carried toward white or black until it is [neededGap] of luma clear of **every** sky
     * between [skyLumaLow] and [skyLumaHigh], by exactly enough and no further.
     *
     * The same shape as the renderer's waterline (`PaperRenderer.drawWaterline`) and for the same
     * reason: a hairline drawn on a surface that moves cannot have a fixed colour, and the honest
     * fix is to state the separation it needs and derive the colour from it. [neededGap] is a gap on
     * the *colour*, already divided by the alpha the stroke is painted at, so what the eye is given
     * is [PaperRenderer.PRECIPITATION_MIN_LUMA_GAP].
     *
     * ### Why one colour for the whole fall, and not one per height
     *
     * A drop crosses a gradient, so the obvious answer is to correct it against the sky at its own
     * height. **That answer cannot exist.** The sky's luma is monotone down the fall and the drop's
     * is constant, so whenever the two are close the sky crosses the drop somewhere inside it: above
     * the crossing the drop is the brighter of the two, below it the darker. A correction that
     * clears the gap everywhere must therefore sit above the sky at one end of the fall and below it
     * at the other, and those two branches never meet — any such function has a step in it. A step
     * means lighter-than-sky rain above one line and darker-than-sky rain below it, in the same
     * frame, which is a worse artefact than the one being fixed. One colour per frame is the only
     * form that is both continuous and clear of the whole band, and what it costs is that a height
     * where the rain was already fine is carried along with the height where it was not.
     *
     * The direction is the cheaper of the two, priced by how far each has to carry the colour. A
     * drop already clear of the whole band returns [base] unchanged and is therefore bit-identical
     * to what v4.26 drew — which is what lets the golden set move only on the scenes where the sky
     * and the rain actually collided.
     */
    fun standOffFromSky(
        base: Int,
        baseLuma: Float,
        skyLumaLow: Float,
        skyLumaHigh: Float,
        neededGap: Float,
    ): Int {
        if (baseLuma >= skyLumaHigh + neededGap || baseLuma <= skyLumaLow - neededGap) return base
        val whiteTarget = skyLumaHigh + neededGap
        val blackTarget = skyLumaLow - neededGap
        val whiteSpan = 255f - baseLuma
        val blackSpan = -baseLuma
        val tWhite = if (whiteSpan <= 0f) Float.MAX_VALUE else (whiteTarget - baseLuma) / whiteSpan
        val tBlack = if (blackSpan >= 0f) Float.MAX_VALUE else (blackTarget - baseLuma) / blackSpan
        val towardWhite = tWhite <= tBlack
        val t = (if (towardWhite) tWhite else tBlack).coerceIn(0f, 1f)
        if (t <= 0f) return base
        return SceneColour.blendArgb(base, if (towardWhite) 0xFFFFFFFF.toInt() else 0xFF000000.toInt(), t)
    }
}
