package dev.serhiiyaremych.rendereffectx.gl

import android.hardware.HardwareBuffer
import android.opengl.EGLDisplay
import android.opengl.GLES20
import androidx.opengl.EGLExt
import androidx.opengl.EGLImageKHR

/**
 * A captured [HardwareBuffer] imported as a sampleable GL_TEXTURE_2D via an EGLImage. Re-imported
 * each frame: API 30 hands a fresh wrapper per acquire with no stable id to cache on. GL thread only.
 */
internal class InputTexture(private val display: EGLDisplay, buffer: HardwareBuffer) {

    private val image: EGLImageKHR = EGLExt.eglCreateImageFromHardwareBuffer(display, buffer)
        ?: error("eglCreateImageFromHardwareBuffer(input) returned null")

    val id: Int = GlUtils.createTexture().also {
        EGLExt.glEGLImageTargetTexture2DOES(GLES20.GL_TEXTURE_2D, image)
    }

    fun release() {
        GLES20.glDeleteTextures(1, intArrayOf(id), 0)
        EGLExt.eglDestroyImageKHR(display, image)
    }
}
