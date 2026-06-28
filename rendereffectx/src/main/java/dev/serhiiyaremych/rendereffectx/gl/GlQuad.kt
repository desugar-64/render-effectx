package dev.serhiiyaremych.rendereffectx.gl

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The static fullscreen quad: four interleaved (x, y, u, v) vertices as a triangle strip, in a
 * client-side buffer (no GL handle). UV matches clip-space Y (no flip). GL thread only.
 */
internal class GlQuad {

    private val vertices = ByteBuffer.allocateDirect(QUAD.size * FLOAT_BYTES)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply { put(QUAD); position(0) }

    fun draw(positionLocation: Int, texCoordLocation: Int) {
        vertices.position(0)
        GLES20.glVertexAttribPointer(positionLocation, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, vertices)
        GLES20.glEnableVertexAttribArray(positionLocation)
        vertices.position(2)
        GLES20.glVertexAttribPointer(texCoordLocation, 2, GLES20.GL_FLOAT, false, STRIDE_BYTES, vertices)
        GLES20.glEnableVertexAttribArray(texCoordLocation)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
    }

    private companion object {
        const val FLOAT_BYTES = 4
        const val STRIDE_BYTES = 4 * FLOAT_BYTES // pos.xy + uv.xy

        val QUAD = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )
    }
}
