package dev.serhiiyaremych.rendereffectx.gl

import android.hardware.HardwareBuffer
import android.opengl.EGLDisplay
import android.opengl.GLES20
import android.opengl.GLES30
import androidx.opengl.EGLExt
import androidx.opengl.EGLImageKHR

/**
 * A GPU render target backed by a [HardwareBuffer]: an EGLImage-imported color texture plus its FBO.
 * GL thread only.
 */
internal class OutputTexture(private val display: EGLDisplay, val width: Int, val height: Int) {

    val buffer: HardwareBuffer = HardwareBuffer.create(
        width, height, HardwareBuffer.RGBA_8888, 1,
        HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT
    )

    private val image: EGLImageKHR = EGLExt.eglCreateImageFromHardwareBuffer(display, buffer)
        ?: error("eglCreateImageFromHardwareBuffer(output) returned null")

    private val texture: Int = GlUtils.createTexture().also {
        EGLExt.glEGLImageTargetTexture2DOES(GLES20.GL_TEXTURE_2D, image)
    }

    private val fbo: Int = IntArray(1).also { GLES20.glGenFramebuffers(1, it, 0) }[0]

    init {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glFramebufferTexture2D(
            GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0, GLES20.GL_TEXTURE_2D, texture, 0
        )
        val status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER)
        check(status == GLES20.GL_FRAMEBUFFER_COMPLETE) { "FBO incomplete: $status" }
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0)
    }

    fun bind() {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, fbo)
        GLES20.glViewport(0, 0, width, height)
        // pass overwrites all pixels; invalidate spares tile GPUs the load (ES 3.0 — the lib's GL floor)
        GLES30.glInvalidateFramebuffer(GLES30.GL_FRAMEBUFFER, 1, DISCARD, 0)
    }

    fun release() {
        GLES20.glDeleteFramebuffers(1, intArrayOf(fbo), 0)
        GLES20.glDeleteTextures(1, intArrayOf(texture), 0)
        EGLExt.eglDestroyImageKHR(display, image)
        buffer.close()
    }

    private companion object {
        val DISCARD = intArrayOf(GLES30.GL_COLOR_ATTACHMENT0) // reused, avoids per-frame alloc
    }
}
