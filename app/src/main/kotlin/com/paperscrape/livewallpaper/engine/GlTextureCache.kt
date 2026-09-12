package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.opengl.GLES20
import android.opengl.GLUtils

/**
 * Where each sprite lives on the GPU: which texture, which rectangle of it, and how big it is.
 *
 * Every entry is derived data — the pixels it was uploaded from can always be decoded again from
 * resources — so dropping one costs a re-upload and nothing else. That is what makes both the
 * memory-pressure response and [SpriteCache.release] safe.
 *
 * ## Two placements
 *
 * A sprite goes into the shared [GlTextureAtlas] when it fits, and gets a texture of its own when it
 * does not. Callers do not care which: they receive a texture handle and a UV rectangle either way,
 * and a standalone texture simply reports the full `0..1` rectangle.
 *
 * ## One entry per sprite *and level*
 *
 * A sprite is uploaded pre-reduced, at the [SpriteDetailLevel] the draw about to happen asks for, so
 * the GPU samples it at roughly 1:1 instead of minifying it several times over. A sprite drawn at
 * two very different sizes in one scene therefore holds two entries, keyed by `(resId, level)`.
 *
 * That is fewer texels, not more, and by a wide margin — but **the two figures this paragraph used
 * to quote were both stale, and stale in the direction that made the atlas look roomier than it
 * was**: it said "18.5 MiB of texture at level 0, and 1.8 MiB once each sprite carries only the
 * levels its scenes actually draw", when level 0 had reached 31.745 MiB and the reduced figure,
 * measured on the device rather than modelled, is about four times the 1.8.
 *
 * They are not re-typed with fresh numbers here, because re-typing is exactly how they rotted
 * (`BACKLOG_v4_28.md` item 82, and item 63 before it). The measurements live where they can fail:
 * `SpriteDrawScaleTest.uploadedTexelBudget` for what the set uploads, and
 * `SpriteGeometryTest.decodedByteBudget` for what it decodes. The *ratio* the sentence exists to
 * make still holds and is what matters here.
 *
 * ## Why the size is recorded here, and *which* size
 *
 * Recording each sprite's pixel dimensions is what lets the renderer draw an already-uploaded sprite
 * **without touching [SpriteCache] at all**. Previously every blit went through a synchronised cache
 * lookup with an LRU touch, once per sprite per frame, purely to recover a width and a height the
 * GPU already knew. On a star field that was hundreds of monitor acquisitions per frame for
 * information that never changes.
 *
 * The recorded size is the sprite's **authored** size, not the reduced texture's. The quad is built
 * in the sprite's own coordinates and the caller's transform scales it, so a sprite must occupy the
 * same rectangle whichever level backs it — the level changes how many texels are sampled, never how
 * large the sprite is drawn. Recording the reduced size instead would shrink every reduced sprite on
 * screen, which is a silent, level-dependent size bug.
 *
 * ## Storage and threading
 *
 * Parallel primitive arrays with a linear search rather than a `Map<Int, …>`: a map boxes its key on
 * every lookup, and this is the per-blit path. The table holds one entry per shipped sprite per
 * level a scene actually draws it at, and the scan stops at the first match.
 *
 * Every method touches GL state and must run on the render thread with the context current.
 */
internal class GlTextureCache {

    private val atlas = GlTextureAtlas()

    private var resIds = IntArray(INITIAL_CAPACITY)
    private var levels = IntArray(INITIAL_CAPACITY)
    private var handles = IntArray(INITIAL_CAPACITY)
    private var widths = IntArray(INITIAL_CAPACITY)
    private var heights = IntArray(INITIAL_CAPACITY)

    /** `[u0, v0, u1, v1]` per entry, flattened. */
    private var uvs = FloatArray(INITIAL_CAPACITY * 4)

    /**
     * `[left, top, right, bottom]` per entry, as fractions of the **authored** box: where the
     * uploaded texels sit inside the sprite's own canvas.
     *
     * `0, 0, 1, 1` for a sprite whose ink reaches every edge, which is most of them.
     */
    private var content = FloatArray(INITIAL_CAPACITY * 4)
    private var count = 0

    private val scratch = IntArray(1)
    private val scratchRect = FloatArray(4)

    /** How many sprite-and-level entries are currently uploaded. Exposed for diagnostics. */
    val size: Int get() = count

    /**
     * How many entries needed a texture of their own because the atlas would not take them.
     *
     * **Zero is the property v4.29 bought and v4.30 must not spend.** A standalone texture is its
     * own GL allocation and it ends the batch every time the draw order crosses it, which is the
     * cost that made a person in five layers look expensive in the first place. Counted rather than
     * assumed: `GlAtlasOccupancyTest` reads it off a real theme sweep on the device.
     */
    var standaloneCount = 0
        private set

    /** Rows the atlas has reached into, and entries packed into it. Diagnostics only. */
    val atlasRowsUsed: Int get() = atlas.rowsUsed

    val atlasPackedCount: Int get() = atlas.packedCount

    /**
     * Index of the entry for [resId] at [level], or `-1` if that pairing has not been uploaded yet.
     *
     * Allocation-free and, unlike [SpriteCache], not synchronised: this table belongs to one render
     * thread.
     */
    fun find(resId: Int, level: Int): Int {
        for (i in 0 until count) {
            if (resIds[i] == resId && levels[i] == level) return i
        }
        return -1
    }

    /**
     * Uploads [bitmap] for [resId], reduced to [level], and returns its entry index, or `-1` if it
     * could not be uploaded.
     *
     * A failure is not fatal: the caller skips that sprite for the frame rather than taking the
     * whole scene down, and tries again next frame.
     */
    fun register(resId: Int, level: Int, bitmap: Bitmap): Int {
        val full = reduce(bitmap, level)
        // **Cropped after the reduction, never before it.**
        //
        // Uploading the empty texels around a sprite's ink is the single largest avoidable item in
        // the texture budget once a figure is drawn in layers: a mask for one region covers a few
        // tenths of its canvas and the rest is transparent. Cropping the *PNG* instead would be
        // cheaper still -- it would cut the decoded bytes too -- but it is not exact:
        // `SpriteDetailLevel.reduced` truncates, so a 117-wide canvas reduced twice is 29 texels of
        // 4.0345 authored pixels each while a 96-wide crop of it is 24 texels of 4.0000, and the two
        // layers drift apart across the sprite. Measured: dE 5.6 at the device's level and 14.4 one
        // level up.
        //
        // Cropping *after* the reduction has no such step to match, because the crop's texels **are**
        // the canvas's texels -- the same halvings produced them. The only thing the crop changes is
        // what the bilinear tap reads just outside the ink, and there it reads the transparent texel
        // [GlTextureAtlas.PADDING] puts between two entries, which is what it would have read from
        // the sprite's own transparent border anyway.
        val reduced = cropToContent(full)
        try {
            val handle: Int
            if (atlas.add(reduced, scratchRect)) {
                handle = atlas.textureHandle
            } else {
                handle = uploadStandalone(reduced)
                if (handle == 0) return -1
                standaloneCount++
                scratchRect[0] = 0f
                scratchRect[1] = 0f
                scratchRect[2] = 1f
                scratchRect[3] = 1f
            }
            if (count == resIds.size) grow()
            val i = count
            resIds[i] = resId
            levels[i] = level
            handles[i] = handle
            // The authored size, not the reduced one. See the class comment: the quad must not move.
            widths[i] = bitmap.width
            heights[i] = bitmap.height
            uvs[i * 4] = scratchRect[0]
            uvs[i * 4 + 1] = scratchRect[1]
            uvs[i * 4 + 2] = scratchRect[2]
            uvs[i * 4 + 3] = scratchRect[3]
            // The crop is measured on the reduced bitmap and reported as a fraction of the authored
            // box, because that is the frame the quad is built in and the two differ by the very
            // truncation this avoids depending on.
            val fw = full.width.toFloat()
            val fh = full.height.toFloat()
            content[i * 4] = cropX / fw
            content[i * 4 + 1] = cropY / fh
            content[i * 4 + 2] = (cropX + reduced.width) / fw
            content[i * 4 + 3] = (cropY + reduced.height) / fh
            count++
            return i
        } finally {
            if (reduced !== full) reduced.recycle()
            if (full !== bitmap) full.recycle()
        }
    }

    /** Where [cropToContent] took its last crop from, in the reduced bitmap's own texels. */
    private var cropX = 0
    private var cropY = 0

    /**
     * [reduced] with its fully transparent border removed, or [reduced] itself when it has none.
     *
     * Scans the alpha channel once per upload -- an upload happens once per sprite per level for the
     * life of the process, not per frame. A bitmap with no opaque texel at all is returned whole:
     * there is nothing to centre a crop on, and the atlas is perfectly able to hold it.
     */
    private fun cropToContent(reduced: Bitmap): Bitmap {
        val w = reduced.width
        val h = reduced.height
        if (w * h > pixelScratch.size) pixelScratch = IntArray(w * h)
        reduced.getPixels(pixelScratch, 0, w, 0, 0, w, h)
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                if (pixelScratch[row + x] ushr 24 == 0) continue
                if (x < left) left = x
                if (x > right) right = x
                if (y < top) top = y
                bottom = y
            }
        }
        if (right < 0) {
            cropX = 0
            cropY = 0
            return reduced
        }
        if (left == 0 && top == 0 && right == w - 1 && bottom == h - 1) {
            cropX = 0
            cropY = 0
            return reduced
        }
        cropX = left
        cropY = top
        return Bitmap.createBitmap(reduced, left, top, right - left + 1, bottom - top + 1)
    }

    private var pixelScratch = IntArray(0)

    /**
     * [bitmap] halved [level] times, or [bitmap] itself at level 0.
     *
     * Halved repeatedly rather than scaled once to the final size: one filtered step from a seventh
     * of the way down reads four source pixels out of forty-nine and is the very aliasing this
     * exists to remove, whereas each halving averages the four pixels that become one. That is the
     * same reduction a mip chain performs, done where the atlas and the ES 2.0 non-power-of-two
     * rules cannot interfere with it.
     *
     * `filter = true` is what makes a halving an average rather than a drop of every other pixel.
     * The source is premultiplied and stays premultiplied, so no colour bleeds out of a transparent
     * edge.
     */
    private fun reduce(bitmap: Bitmap, level: Int): Bitmap {
        if (level <= 0) return bitmap
        var current = bitmap
        for (step in 1..level) {
            val width = SpriteDetailLevel.reduced(bitmap.width, step)
            val height = SpriteDetailLevel.reduced(bitmap.height, step)
            if (width == current.width && height == current.height) break
            val next = Bitmap.createScaledBitmap(current, width, height, true)
            if (current !== bitmap) current.recycle()
            current = next
        }
        return current
    }

    fun handleAt(index: Int): Int = handles[index]

    fun widthAt(index: Int): Int = widths[index]

    fun heightAt(index: Int): Int = heights[index]

    fun u0At(index: Int): Float = uvs[index * 4]

    fun v0At(index: Int): Float = uvs[index * 4 + 1]

    fun u1At(index: Int): Float = uvs[index * 4 + 2]

    fun v1At(index: Int): Float = uvs[index * 4 + 3]

    /** Where this entry's uploaded texels start inside the authored box, as a fraction of it. */
    fun contentLeftAt(index: Int): Float = content[index * 4]

    fun contentTopAt(index: Int): Float = content[index * 4 + 1]

    fun contentRightAt(index: Int): Float = content[index * 4 + 2]

    fun contentBottomAt(index: Int): Float = content[index * 4 + 3]

    /**
     * Packs a 1x1 opaque white pixel under [key] and returns its entry index, or `-1` on failure.
     *
     * White is the identity for both the `MULTIPLY` tint and the flat-fill path, so sampling this
     * entry turns a solid colour into an ordinary textured quad. Registering it in the *atlas*
     * rather than as its own texture is the point: it puts flat geometry and sprites in the same
     * texture, so a solid detail drawn between two sprites no longer ends the batch.
     *
     * [key] is a sentinel rather than a real drawable id — there is no white-pixel resource, and
     * inventing one would put a file in `res/` that only this line would ever read.
     */
    fun registerWhitePixel(key: Int): Int {
        val bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        bitmap.setPixel(0, 0, WHITE)
        val index = register(key, 0, bitmap)
        bitmap.recycle()
        return index
    }

    private fun uploadStandalone(bitmap: Bitmap): Int {
        GLES20.glGenTextures(1, scratch, 0)
        val handle = scratch[0]
        if (handle == 0) return 0
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, handle)
        // CLAMP_TO_EDGE and a non-mipmapped minification filter are not a preference: ES 2.0 only
        // supports non-power-of-two textures under exactly these settings, and the sprite set is
        // authored to its content boxes rather than to power-of-two canvases.
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)
        return handle
    }

    /** Deletes every texture, the atlas included. Sprites are re-uploaded on next use. */
    fun clear() {
        val atlasHandle = atlas.textureHandle
        for (i in 0 until count) {
            // Standalone textures only: every entry packed into the atlas shares its single handle,
            // which the atlas deletes itself.
            if (handles[i] != atlasHandle && handles[i] != 0) {
                scratch[0] = handles[i]
                GLES20.glDeleteTextures(1, scratch, 0)
            }
        }
        atlas.clear()
        count = 0
    }

    /**
     * Forgets every handle **without** a GL call, for a context that has already been destroyed.
     *
     * Deleting names from a dead context is at best a no-op and at worst deletes a name that the
     * next context has since handed to something else.
     */
    fun invalidate() {
        atlas.invalidate()
        count = 0
    }

    private fun grow() {
        val capacity = resIds.size * 2
        resIds = resIds.copyOf(capacity)
        levels = levels.copyOf(capacity)
        handles = handles.copyOf(capacity)
        widths = widths.copyOf(capacity)
        heights = heights.copyOf(capacity)
        uvs = uvs.copyOf(capacity * 4)
        content = content.copyOf(capacity * 4)
    }

    private companion object {
        const val INITIAL_CAPACITY = 128
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
