package dev.serhiiyaremych.rendereffectx.gl

import android.opengl.GLES20

/**
 * A linked runtime-effect program (fixed passthrough vertex shader + per-effect fragment source).
 * [draw] binds the program, input texture, `iResolution` and float uniforms, then draws [quad].
 * GL thread only.
 */
internal class GlProgram(fragmentSource: String) {

    private val id = GlUtils.linkProgram(VERTEX_SOURCE, fragmentSource)
    private val contentLocation = GLES20.glGetUniformLocation(id, "content")
    private val resolutionLocation = GLES20.glGetUniformLocation(id, "iResolution")
    private val positionLocation = GLES20.glGetAttribLocation(id, "aPosition")
    private val texCoordLocation = GLES20.glGetAttribLocation(id, "aTexCoord")

    fun draw(
        quad: GlQuad,
        inputTexture: Int,
        width: Int,
        height: Int,
        floatUniforms: Map<String, FloatArray>,
    ) {
        GLES20.glUseProgram(id)
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, inputTexture)
        GLES20.glUniform1i(contentLocation, 0)
        GLES20.glUniform2f(resolutionLocation, width.toFloat(), height.toFloat())
        for ((name, values) in floatUniforms) {
            val location = GLES20.glGetUniformLocation(id, name)
            if (location >= 0) {
                when (values.size) {
                    1 -> GLES20.glUniform1f(location, values[0])
                    2 -> GLES20.glUniform2f(location, values[0], values[1])
                    3 -> GLES20.glUniform3f(location, values[0], values[1], values[2])
                    4 -> GLES20.glUniform4f(location, values[0], values[1], values[2], values[3])
                }
            }
        }
        quad.draw(positionLocation, texCoordLocation)
    }

    fun release() {
        GLES20.glDeleteProgram(id)
    }

    private companion object {
        val VERTEX_SOURCE = """
            attribute vec2 aPosition;
            attribute vec2 aTexCoord;
            varying vec2 vTexCoord;
            void main() {
                vTexCoord = aTexCoord;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """.trimIndent()
    }
}
