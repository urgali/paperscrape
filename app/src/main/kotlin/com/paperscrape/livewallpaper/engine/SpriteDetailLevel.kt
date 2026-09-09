package com.paperscrape.livewallpaper.engine

/**
 * Which pre-reduced copy of a sprite to sample, given how much the scene is about to shrink it.
 *
 * ## Why a sprite needs more than one copy
 *
 * Every sprite is authored well above the size it is drawn at: the [SpriteBlitter.SPRITE_PIXELS_PER_UNIT]
 * oversample is deliberate, and on top of it the viewport scale and the ground plane's perspective
 * shrink it again. Measured on the device, an adult walker arrives on screen at about a seventh of
 * its authored size and a bust behind a window at between a seventh and a twenty-sixth.
 *
 * A GPU asked to minify that far with one bilinear tap reads four texels out of the forty-nine or
 * more that fall inside the pixel, and *which* four depends on where the sprite happens to sit this
 * frame. As the scene scrolls, that choice changes every frame and detail below a few source pixels
 * appears and disappears. It is why the artwork carries a "nothing thinner than three units" rule:
 * the rule is a workaround for the sampling, not a drawing decision.
 *
 * The fix is to hand the GPU pixels it can sample at roughly 1:1, by reducing the bitmap on the CPU
 * once, before upload. One reduction per sprite is **not** enough, and the measurement is what says
 * so: within a single window-bust sprite the drawn size varies by 3.7x across a scene, so a copy
 * sized for the largest instance still leaves the smallest one minifying 3.7x. So the sprite gets a
 * small chain of halvings and the level is chosen per draw, from that draw's own factor.
 *
 * ## Why not GL mipmaps
 *
 * Three reasons, each sufficient on its own. Sprites share one [GlTextureAtlas] with a single
 * transparent texel between neighbours, and a generated mip level blends 2^L x 2^L blocks — at the
 * levels this scene actually reaches, a sprite would be mixed with whatever the packer put beside
 * it. `GL_TEXTURE_MAX_LEVEL`, which would cap the chain before that happens, does not exist in
 * OpenGL ES 2.0. And a standalone sprite texture is non-power-of-two, which ES 2.0 permits only
 * with a non-mipmapped minification filter.
 *
 * Reducing on the CPU sidesteps all three, and it *shrinks* texture memory instead of adding the
 * third that a mip chain costs: a level-3 copy is a sixty-fourth of the texels.
 *
 * ## How deep to reduce, which is not the obvious answer
 *
 * The copy is aimed at **twice** the size the sprite is drawn at, not at the drawn size itself.
 *
 * Aiming at the drawn size looks right and measures badly. A copy already at screen resolution
 * leaves the bilinear tap nothing to average: every sub-pixel position has to be interpolated
 * between neighbouring texels, which is blurrier than the correct answer *and* still changes with
 * the position. Keeping roughly two texels per screen pixel gives the tap — which reads two texels
 * per axis — a footprint to average that matches the pixel it is filling.
 *
 * That was measured rather than reasoned into place, by comparing one bilinear tap against a proper
 * area filter at eight sub-pixel phases and splitting the error into the part that is the same at
 * every phase (softening) and the part that moves with the phase (flicker). Over eleven shipped
 * sprites at their measured on-screen sizes, aiming at twice the drawn size cuts flicker by 52% and
 * softening by 30%, and every sprite improves on both. Aiming at the drawn size, or at the nearest
 * power of two to it, is **worse than doing nothing**: it trades a little flicker for a lot of blur.
 *
 * So the level is the one whose residual factor is nearest 0.5, which puts the residual in
 * `[1/(2*sqrt(2)), 1/sqrt(2)]` — the copy is always between 1.4x and 2.8x the drawn size, and is
 * never smaller than it.
 */
internal object SpriteDetailLevel {

    /**
     * Deepest reduction offered. The worst case measured on the device is a bust behind a window at
     * 26x, which asks for level 4; level 5 is one more than anything the scene reaches, and the cap
     * is what stops an unforeseen transform from asking for a copy with no pixels left in it.
     */
    const val MAX_LEVEL = 5

    /**
     * `1/(2*sqrt(2))`: the point below which the *next* halving lands closer to the target residual
     * of 0.5 than this one does. See the class comment for why the target is 0.5 and not 1.
     */
    private const val ROUNDING_THRESHOLD = 0.35355339f

    /**
     * The level whose copy holds closest to two texels per screen pixel, where [factor] is screen
     * pixels per sprite pixel — exactly what the draw transform's uniform scale already is.
     *
     * A loop rather than a logarithm: it runs at most [MAX_LEVEL] times, this is the per-blit path,
     * and it needs no floating-point library call at all.
     */
    fun levelFor(factor: Float): Int {
        if (factor >= ROUNDING_THRESHOLD) return 0
        var scaled = factor
        var level = 0
        while (scaled < ROUNDING_THRESHOLD && level < MAX_LEVEL) {
            scaled *= 2f
            level++
        }
        return level
    }

    /**
     * [size] halved [level] times, never below one pixel.
     *
     * Clamping rather than refusing: a sprite thinner than the level asks for still has to be drawn,
     * and a one-pixel row of it is the honest answer at that size.
     */
    fun reduced(size: Int, level: Int): Int {
        val shifted = size shr level
        return if (shifted < 1) 1 else shifted
    }
}
