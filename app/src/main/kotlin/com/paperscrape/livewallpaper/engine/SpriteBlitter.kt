package com.paperscrape.livewallpaper.engine

import android.content.Context

/**
 * Which scale a sprite's pixels were authored at, and therefore how [SpriteBlitter] has to map the
 * bitmap onto the caller's current canvas transform.
 *
 * Two conventions coexist because the sprite sets were produced by different generation scripts
 * with different assumptions, and nothing in a PNG records which one applies to it. Choosing the
 * wrong one renders a sprite exactly [SpriteBlitter.SPRITE_PIXELS_PER_UNIT] times too large or too
 * small, with no error of any kind -- so the convention is passed here as a *named* argument at the
 * point of use, rather than being implied by which of several similarly named helper functions the
 * caller happened to pick.
 *
 * This is an interim arrangement, not the end state: the convention is a property of the asset, so
 * it belongs in the asset's own declared metadata. It cannot live there until the asset pipeline
 * exists (`ROADMAP.md`, Group 3), and until then the caller is the only place that knows.
 */
enum class SpriteScale {
    /**
     * Authored at [SpriteBlitter.SPRITE_PIXELS_PER_UNIT] pixels per local unit -- a fixed
     * oversample that exists so the downscale applied at draw time yields clean antialiased edges.
     * Houses, trees, vehicles, people, seasonal decorations, clouds, and the lake decorations.
     */
    SCENE_UNITS,

    /**
     * Authored at literal on-screen pixel size, so the bitmap is blitted with no division of its
     * own -- at whatever scale the caller's surrounding `canvas.scale()` already established, the
     * same way every raw-pixel procedural shape in the renderer works. Sun, moon, stars and birds.
     * The sleigh belonged here until the V2 asset set redrew it on the authoring grid; it is now
     * a [SCENE_UNITS] sprite scaled by its own call site.
     */
    CANVAS_PIXELS,
}

/**
 * The single sprite-blitting path: every sprite in the app is drawn through [draw] or [drawTinted].
 *
 * Both renderers used to carry their own copies of this, one pair of functions per scale
 * convention, which meant the same four-line blit existed six times over and the paint flags, the
 * tint mode, the paint-state hygiene and the oversample factor each had more than one definition to
 * keep in step. They had already drifted: one copy cleared the colour filter before drawing, one
 * after, one not at all, and only one pair accepted an alpha argument. Nothing visible depended on
 * those differences -- each variant was internally consistent with its own callers -- but each was
 * a way for the next caller to get a different result from an identically named function.
 *
 * The tint colour and the alpha are passed explicitly on every blit rather than left as paint state,
 * so no blit can inherit either from whatever was drawn before it. How they are applied is the
 * backend's business: the `Canvas` backend builds a `PorterDuffColorFilter`, the GPU backend puts
 * the same numbers in the vertex colour.
 *
 * **Tinting uses `PorterDuff.Mode.MULTIPLY`, not `SRC_IN`.** `SRC_IN` replaces every opaque source
 * pixel with one flat colour, discarding the bitmap's own RGB, which means a sprite can carry no
 * baked-in shading at all. The sprites bake in subtle darker "paper fold" mottling -- never
 * anything lighter than the sprite's own base tone -- and `MULTIPLY` (`result = tint * source`,
 * alpha untouched) is what lets that mottling survive the tint as gentle shading instead of being
 * flattened away. The trade-off is that the on-screen colour is no longer bit-exact to the hex the
 * user picked: it is darkened by a few percent wherever mottling sits. The mottling is deliberately
 * low-strength and confined to individual object sprites rather than applied as a full-scene
 * overlay, so the deviation stays minor, but it is real and has never been judged on a device.
 *
 * Nothing here allocates: this runs for every sprite of every frame.
 */
class SpriteBlitter(private val context: Context) : SpriteSource {

    /**
     * The pixels for [resId], decoded on first use.
     *
     * How often this is called is up to the backend: the `Canvas` backend needs it for every blit,
     * the GPU backend once per sprite per GL context.
     */
    override fun bitmapFor(resId: Int): android.graphics.Bitmap = SpriteCache.get(context, resId)

    /**
     * Releases the decoded pixels once a backend holds a durable copy of them.
     *
     * Only the GPU backend calls this, and only after a successful texture upload. Holding both the
     * bitmap and the texture was up to ~17 MB of heap duplicating what the GPU already had, and the
     * cache re-decodes on demand, so being wrong here costs one decode.
     */
    override fun onSpriteUploaded(resId: Int) {
        SpriteCache.release(resId)
    }

    /**
     * Blits the sprite's own baked-in colours as-is, positioned so that the bitmap's local origin
     * (pixel 0,0) lands at [originX]/[originY] in the caller's own coordinate space.
     *
     * For sprites whose colour is fixed art baked into the PNG at generation time -- a palm tree
     * trunk, a taxi's chequer stripe -- rather than a mask meant to be tinted at runtime.
     *
     * [alpha] exists for fixed art that fades rather than recolours: the rainbow's daylight
     * fade, a firework burst dying out, a lightning bolt decaying. Those used to be procedural
     * shapes whose paint carried the alpha; as sprites they still need it, and routing them
     * through [drawTinted] with a white tint would say "tinted with the identity colour", which
     * is exactly the wording the fixed-art/tintable split exists to make unambiguous.
     */
    fun draw(
        canvas: SceneCanvas,
        resId: Int,
        originX: Float,
        originY: Float,
        scale: SpriteScale,
        alpha: Int = 255,
    ) {
        blit(canvas, resId, originX, originY, scale, UNTINTED, alpha)
    }

    /**
     * Same placement as [draw], with the sprite multiplied by [tintColor] and drawn at [alpha].
     *
     * For sprites that are user-recolourable or theme-driven: house walls, tree canopies, clouds,
     * the sun and the moon.
     */
    fun drawTinted(
        canvas: SceneCanvas,
        resId: Int,
        originX: Float,
        originY: Float,
        scale: SpriteScale,
        tintColor: Int,
        alpha: Int = 255,
    ) {
        blit(canvas, resId, originX, originY, scale, tintColor, alpha)
    }

    /**
     * The one place a sprite bitmap actually reaches the canvas.
     *
     * [SpriteScale.SCENE_UNITS] divides the oversample back out with a single `canvas.scale()` and
     * pre-multiplies the origin by the same factor, so the `drawBitmap` call itself needs no
     * per-call `Matrix`. [SpriteScale.CANVAS_PIXELS] blits straight through: it deliberately does
     * not take the save/scale/restore path with a factor of 1, which would be arithmetically
     * identical but would add canvas work to every sky sprite of every frame for no result.
     */
    private fun blit(
        canvas: SceneCanvas,
        resId: Int,
        originX: Float,
        originY: Float,
        scale: SpriteScale,
        tintColor: Int,
        alpha: Int,
    ) {
        when (scale) {
            SpriteScale.SCENE_UNITS -> {
                canvas.save()
                canvas.scale(1f / SPRITE_PIXELS_PER_UNIT, 1f / SPRITE_PIXELS_PER_UNIT)
                canvas.drawSprite(
                    resId,
                    this,
                    originX * SPRITE_PIXELS_PER_UNIT,
                    originY * SPRITE_PIXELS_PER_UNIT,
                    tintColor,
                    alpha,
                )
                canvas.restore()
            }

            SpriteScale.CANVAS_PIXELS -> canvas.drawSprite(resId, this, originX, originY, tintColor, alpha)
        }
    }

    companion object {
        /**
         * The `MULTIPLY` identity: multiplying a sprite by white leaves it exactly as authored, so
         * an untinted blit and a white-tinted one are the same operation and need no separate path.
         */
        private const val UNTINTED = 0xFFFFFFFF.toInt()

        /**
         * The oversample baked into every [SpriteScale.SCENE_UNITS] sprite: it is authored at 3x
         * the size it is drawn at on screen, for clean antialiased edges after this exact
         * downscale.
         *
         * The value is baked into the sprites themselves at generation time, so this constant and
         * the generation scripts must always agree -- changing it invalidates the whole asset set
         * rather than rescaling it. It is defined here, once: each renderer used to keep its own
         * copy, with a comment instructing the reader to keep the two in sync by hand.
         */
        const val SPRITE_PIXELS_PER_UNIT = 3f
    }
}
