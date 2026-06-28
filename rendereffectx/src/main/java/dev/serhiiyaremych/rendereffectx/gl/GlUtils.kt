package dev.serhiiyaremych.rendereffectx.gl

import android.opengl.GLES20

/** Stateless GL helpers: shader/program building and 2D-texture setup. GL thread only. */
internal object GlUtils {

    /** Compiles and links [vertexSource] + [fragmentSource] into a program. Throws on failure. */
    fun linkProgram(vertexSource: String, fragmentSource: String): Int {
        val vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        val fragment = try {
            compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        } catch (fragmentFailed: RuntimeException) {
            GLES20.glDeleteShader(vertex)
            throw fragmentFailed
        }
        val program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vertex)
        GLES20.glAttachShader(program, fragment)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        GLES20.glDeleteShader(vertex) // freed either way: linked into the program or abandoned
        GLES20.glDeleteShader(fragment)
        check(linked[0] != 0) { "program link failed: ${GLES20.glGetProgramInfoLog(program)}" }
        return program
    }

    /** Generates a GL_TEXTURE_2D with default wrap/filter params, leaving it bound. */
    fun createTexture(): Int {
        val ids = IntArray(1)
        GLES20.glGenTextures(1, ids, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, ids[0])
        setDefaultTextureParams()
        return ids[0]
    }

    /** CLAMP_TO_EDGE wrap + GL_LINEAR filtering on the currently bound GL_TEXTURE_2D. */
    fun setDefaultTextureParams() {
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val ok = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) {
            val log = GLES20.glGetShaderInfoLog(shader)
            GLES20.glDeleteShader(shader)
            error("shader compile failed: $log\n$source")
        }
        return shader
    }
}
