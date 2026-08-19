package com.paperscrape.livewallpaper.engine

import android.graphics.Bitmap
import android.graphics.Canvas
import android.opengl.GLES20
import android.opengl.GLUtils

/**
 * One GL texture holding many sprites, packed as they are first drawn.
 *
 * ## Why
 *
 * A batch ends whenever the bound texture changes. With one texture per sprite, a scene object that
 * alternates a sprite blit with a flat detail — a house wall then its windows, a tree trunk then its
 * canopy — ended a batch at every transition, so the frame cost roughly one draw call per part.
 *
 * Packing sprites together removes the transitions rather than reordering around them, which matters
 * because draw order *is* depth order here and cannot be changed. The flat-fill white pixel is
 * packed in as the first entry precisely so that solid geometry shares the atlas too: with both in
 * the same texture, an entire house — sprites and flat details alike — accumulates into a single
 * batch.
 *
 * ## Packing
 *
 * The placement arithmetic lives in [ShelfPacker], which is pure and unit tested; this class only
 * turns a placement into a texture upload.
 *
 * Nothing is packed speculatively. A theme draws far fewer than the full sprite set, so filling the
 * atlas on first draw keeps it to the working set of the scene actually on screen.
 *
 * ## Bleeding
 *
 * Each entry is uploaded with a one-pixel transparent border, so a bilinear sample that strays past
 * an edge finds transparency rather than the neighbouring sprite. At 1:1 the sampling positions land
 * exactly on texel centres and the border is never read at all; it matters only where a sprite is
 * being magnified, which is why the border is uploaded rather than assumed — the contents of a
 * freshly allocated texture are undefined, so an unwritten gap would be whatever was in that memory.
 */
internal class GlTextureAtlas(
    private val size: Int = DEFAULT_SIZE,
    private val maxEntryDimension: Int = DEFAULT_MAX_ENTRY_DIMENSION,
) {

    var textureHandle = 0
        private set

    private val packer = ShelfPacker(size, size, PADDING)

    private val scratch = IntArray(1)

    /** True once the backing texture exists. Allocated on first use, not at context creation. */
    val isAllocated: Boolean get() = textureHandle != 0

    /**
     * Whether [width] x [height] is a candidate for the atlas at all.
     *
     * Large sprites are excluded on purpose. The sleigh alone is 1563x434, and letting it consume a
     * third of a shelf row would evict nothing but would push the many small sprites that actually
     * repeat per frame out into standalone textures — the opposite of what the atlas is for. A
     * large sprite is also almost always drawn once per frame, so it costs a single batch break.
     */
    fun accepts(width: Int, height: Int): Boolean =
        width <= maxEntryDimension && height <= maxEntryDimension && packer.fitsAtAll(width, height)

    /**
     * Packs [bitmap] and writes its texture rectangle into [outRect] as
     * `[u0, v0, u1, v1]` in normalised coordinates.
     *
     * Returns false when the bitmap does not fit or the texture could not be created, in which case
     * the caller falls back to a standalone texture.
     */
    fun add(bitmap: Bitmap, outRect: FloatArray): Boolean {
        if (!accepts(bitmap.width, bitmap.height)) return false
        if (!ensureTexture()) return false

        if (!packer.place(bitmap.width, bitmap.height)) return false
        val contentX = packer.contentX
        val contentY = packer.contentY

        val padded = Bitmap.createBitmap(
            bitmap.width + PADDING * 2, bitmap.height + PADDING * 2, Bitmap.Config.ARGB_8888,
        )
        try {
            // A fresh ARGB_8888 bitmap is fully transparent, so drawing the sprite one pixel in
            // leaves exactly the border this needs. Source-over onto transparency reproduces the
            // source unchanged, so the copy is lossless and stays premultiplied.
            Canvas(padded).drawBitmap(bitmap, PADDING.toFloat(), PADDING.toFloat(), null)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureHandle)
            GLUtils.texSubImage2D(
                GLES20.GL_TEXTURE_2D, 0, contentX - PADDING, contentY - PADDING, padded,
            )
        } finally {
            padded.recycle()
        }

        val inverseSize = 1f / size
        outRect[0] = contentX * inverseSize
        outRect[1] = contentY * inverseSize
        outRect[2] = (contentX + bitmap.width) * inverseSize
        outRect[3] = (contentY + bitmap.height) * inverseSize
        return true
    }

    private fun ensureTexture(): Boolean {
        if (textureHandle != 0) return true
        GLES20.glGenTextures(1, scratch, 0)
        val handle = scratch[0]
        if (handle == 0) return false
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, handle)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        // Storage only; every region is written by texSubImage2D before it can be sampled.
        GLES20.glTexImage2D(
            GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, size, size, 0,
            GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null,
        )
        if (GLES20.glGetError() != GLES20.GL_NO_ERROR) {
            scratch[0] = handle
            GLES20.glDeleteTextures(1, scratch, 0)
            return false
        }
        textureHandle = handle
        packer.reset()
        return true
    }

    /** Deletes the atlas texture. Entries are re-packed on demand from [SpriteCache]. */
    fun clear() {
        if (textureHandle != 0) {
            scratch[0] = textureHandle
            GLES20.glDeleteTextures(1, scratch, 0)
            textureHandle = 0
        }
        packer.reset()
    }

    /** Forgets the handle without a GL call, for a context that has already been destroyed. */
    fun invalidate() {
        textureHandle = 0
        packer.reset()
    }

    companion object {
        /**
         * 2048 is the smallest maximum texture size OpenGL ES 2.0 guarantees on the hardware this
         * app targets, so the atlas needs no runtime capability query to be safe. At RGBA that is
         * 16 MB of texture memory, against the ~16.4 MB the whole sprite set would occupy as
         * individual textures — so this is a rearrangement of that budget rather than an addition
         * to it, and in practice a scene fills only a fraction of it.
         */
        const val DEFAULT_SIZE = 2048

        /** See [accepts]. */
        const val DEFAULT_MAX_ENTRY_DIMENSION = 1024

        /** Transparent border around each entry, in texels. */
        const val PADDING = 1
    }
}
