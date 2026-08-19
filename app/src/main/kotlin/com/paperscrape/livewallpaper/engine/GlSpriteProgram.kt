package com.paperscrape.livewallpaper.engine

import android.opengl.GLES20

/**
 * The single shader program the GPU backend uses.
 *
 * One program covers both textured sprites and flat geometry, because a flat fill is a textured
 * quad sampling a 1x1 opaque white texture. That collapses what would otherwise be two programs and
 * two vertex streams into one, which means a batch is flushed only when the *texture* changes —
 * never because a solid shape happened to sit between two sprites.
 *
 * ## Colour semantics
 *
 * The per-vertex colour carries the runtime tint in `rgb` and the blit alpha in `a`, and the
 * fragment shader combines them as
 *
 * ```
 * gl_FragColor = vec4(tex.rgb * v_Color.rgb, tex.a) * v_Color.a
 * ```
 *
 * which is exactly what `PorterDuffColorFilter(tint, MULTIPLY)` followed by `paint.alpha` produces
 * on the `Canvas` backend: the tint multiplies the sprite's own colour, the sprite's own alpha is
 * kept, and the blit alpha scales the result. Baked-in shading therefore survives the tint here for
 * the same reason it does there, and white remains the identity tint.
 *
 * ## Premultiplied alpha
 *
 * `BitmapFactory` decodes into premultiplied `ARGB_8888` and `GLUtils.texImage2D` uploads those
 * bytes unchanged, so `tex` arrives premultiplied. Multiplying it by an unpremultiplied tint and
 * then by the blit alpha keeps the result premultiplied, which is why the blend function is
 * `GL_ONE, GL_ONE_MINUS_SRC_ALPHA` rather than the more familiar `GL_SRC_ALPHA` pair. Mixing the two
 * conventions is the classic cause of dark fringes around every soft sprite edge, so both halves of
 * this pairing have to move together.
 */
internal class GlSpriteProgram {

    var programHandle = 0
        private set

    private var mvpHandle = 0
    private var positionHandle = 0
    private var texCoordHandle = 0
    private var colorHandle = 0
    private var textureHandle = 0

    /** True once [compile] has produced a usable program in the current context. */
    val isReady: Boolean get() = programHandle != 0

    fun compile(): Boolean {
        release()
        val vertex = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SOURCE)
        if (vertex == 0) return false
        val fragment = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SOURCE)
        if (fragment == 0) {
            GLES20.glDeleteShader(vertex)
            return false
        }
        val program = GLES20.glCreateProgram()
        if (program == 0) {
            GLES20.glDeleteShader(vertex)
            GLES20.glDeleteShader(fragment)
            return false
        }
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val status = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, status, 0)
        // The shaders are attached, so deleting them now only drops this reference; the linked
        // program keeps its own until it is itself deleted.
        GLES20.glDeleteShader(vertex)
        GLES20.glDeleteShader(fragment)
        if (status[0] == 0) {
            GLES20.glDeleteProgram(program)
            return false
        }
        programHandle = program
        mvpHandle = GLES20.glGetUniformLocation(program, "u_MvpMatrix")
        textureHandle = GLES20.glGetUniformLocation(program, "u_Texture")
        positionHandle = GLES20.glGetAttribLocation(program, "a_Position")
        texCoordHandle = GLES20.glGetAttribLocation(program, "a_TexCoord")
        colorHandle = GLES20.glGetAttribLocation(program, "a_Color")
        return true
    }

    fun use() {
        GLES20.glUseProgram(programHandle)
        GLES20.glUniform1i(textureHandle, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(texCoordHandle)
        GLES20.glEnableVertexAttribArray(colorHandle)
    }

    fun setMvpMatrix(matrix: FloatArray) {
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, matrix, 0)
    }

    /**
     * Points the three attributes at the interleaved client-side vertex buffer.
     *
     * Client-side arrays rather than a VBO: the whole buffer is rewritten every frame, so a VBO
     * would add an upload without removing one, and ES 2.0 accepts a `Buffer` here directly.
     */
    fun bindVertexData(buffer: java.nio.FloatBuffer) {
        val stride = FLOATS_PER_VERTEX * BYTES_PER_FLOAT
        buffer.position(0)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(2)
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(4)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, stride, buffer)
        buffer.position(0)
    }

    fun release() {
        if (programHandle != 0) {
            GLES20.glDeleteProgram(programHandle)
            programHandle = 0
        }
    }

    /** Forgets the handle without a GL call, for use after the context is already gone. */
    fun invalidate() {
        programHandle = 0
    }

    private fun loadShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader == 0) return 0
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) {
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    companion object {
        /** x, y, u, v, r, g, b, a. */
        const val FLOATS_PER_VERTEX = 8
        const val BYTES_PER_FLOAT = 4

        private const val VERTEX_SOURCE = """
            uniform mat4 u_MvpMatrix;
            attribute vec2 a_Position;
            attribute vec2 a_TexCoord;
            attribute vec4 a_Color;
            varying vec2 v_TexCoord;
            varying vec4 v_Color;
            void main() {
                v_TexCoord = a_TexCoord;
                v_Color = a_Color;
                gl_Position = u_MvpMatrix * vec4(a_Position, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SOURCE = """
            precision mediump float;
            uniform sampler2D u_Texture;
            varying vec2 v_TexCoord;
            varying vec4 v_Color;
            void main() {
                vec4 tex = texture2D(u_Texture, v_TexCoord);
                gl_FragColor = vec4(tex.rgb * v_Color.rgb, tex.a) * v_Color.a;
            }
        """
    }
}
