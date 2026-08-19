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
 * ## Why the size is recorded here
 *
 * Recording each sprite's pixel dimensions is what lets the renderer draw an already-uploaded sprite
 * **without touching [SpriteCache] at all**. Previously every blit went through a synchronised cache
 * lookup with an LRU touch, once per sprite per frame, purely to recover a width and a height the
 * GPU already knew. On a star field that was hundreds of monitor acquisitions per frame for
 * information that never changes.
 *
 * ## Storage and threading
 *
 * Parallel primitive arrays with a linear search rather than a `Map<Int, …>`: a map boxes its key on
 * every lookup, and this is the per-blit path. The table holds at most the 118 shipped sprites and
 * the scan stops at the first match.
 *
 * Every method touches GL state and must run on the render thread with the context current.
 */
internal class GlTextureCache {

    private val atlas = GlTextureAtlas()

    private var resIds = IntArray(INITIAL_CAPACITY)
    private var handles = IntArray(INITIAL_CAPACITY)
    private var widths = IntArray(INITIAL_CAPACITY)
    private var heights = IntArray(INITIAL_CAPACITY)

    /** `[u0, v0, u1, v1]` per entry, flattened. */
    private var uvs = FloatArray(INITIAL_CAPACITY * 4)
    private var count = 0

    private val scratch = IntArray(1)
    private val scratchRect = FloatArray(4)

    /** How many sprites are currently uploaded. Exposed for diagnostics. */
    val size: Int get() = count

    /**
     * Index of the entry for [resId], or `-1` if it has not been uploaded yet.
     *
     * Allocation-free and, unlike [SpriteCache], not synchronised: this table belongs to one render
     * thread.
     */
    fun find(resId: Int): Int {
        for (i in 0 until count) {
            if (resIds[i] == resId) return i
        }
        return -1
    }

    /**
     * Uploads [bitmap] for [resId] and returns its entry index, or `-1` if it could not be uploaded.
     *
     * A failure is not fatal: the caller skips that sprite for the frame rather than taking the
     * whole scene down, and tries again next frame.
     */
    fun register(resId: Int, bitmap: Bitmap): Int {
        val handle: Int
        if (atlas.add(bitmap, scratchRect)) {
            handle = atlas.textureHandle
        } else {
            handle = uploadStandalone(bitmap)
            if (handle == 0) return -1
            scratchRect[0] = 0f
            scratchRect[1] = 0f
            scratchRect[2] = 1f
            scratchRect[3] = 1f
        }
        if (count == resIds.size) grow()
        val i = count
        resIds[i] = resId
        handles[i] = handle
        widths[i] = bitmap.width
        heights[i] = bitmap.height
        uvs[i * 4] = scratchRect[0]
        uvs[i * 4 + 1] = scratchRect[1]
        uvs[i * 4 + 2] = scratchRect[2]
        uvs[i * 4 + 3] = scratchRect[3]
        count++
        return i
    }

    fun handleAt(index: Int): Int = handles[index]

    fun widthAt(index: Int): Int = widths[index]

    fun heightAt(index: Int): Int = heights[index]

    fun u0At(index: Int): Float = uvs[index * 4]

    fun v0At(index: Int): Float = uvs[index * 4 + 1]

    fun u1At(index: Int): Float = uvs[index * 4 + 2]

    fun v1At(index: Int): Float = uvs[index * 4 + 3]

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
        val index = register(key, bitmap)
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
        handles = handles.copyOf(capacity)
        widths = widths.copyOf(capacity)
        heights = heights.copyOf(capacity)
        uvs = uvs.copyOf(capacity * 4)
    }

    private companion object {
        const val INITIAL_CAPACITY = 128
        const val WHITE = 0xFFFFFFFF.toInt()
    }
}
