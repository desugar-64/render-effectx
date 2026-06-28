package dev.serhiiyaremych.rendereffectx

import android.graphics.Bitmap
import android.graphics.ColorSpace
import android.hardware.HardwareBuffer
import android.opengl.EGL14
import android.opengl.EGLDisplay
import android.opengl.GLES20
import android.os.Handler
import android.os.Looper
import androidx.graphics.opengl.GLRenderer
import dev.serhiiyaremych.rendereffectx.gl.GlProgram
import dev.serhiiyaremych.rendereffectx.gl.GlQuad
import dev.serhiiyaremych.rendereffectx.gl.InputTexture
import dev.serhiiyaremych.rendereffectx.gl.OutputPool
import java.util.concurrent.CountDownLatch

/**
 * Shared GL core for every effect node: one long-lived EGL context on one GL thread ([GLRenderer])
 * plus a program cache keyed by shader source. Capture, buffer acquire and effect render all run on
 * that single thread, which is safe only because [render] is fire-and-forget. Each node owns its own
 * output [OutputPool]; the session owns only the thread-bound shareable state.
 */
internal class RuntimeEffectSession : AutoCloseable {

    private val glRenderer = GLRenderer().apply { start("rfx") }

    /** Handler on GLRenderer's GL thread; ImageReader listeners post here to land on that thread. */
    val handler: Handler = onGl { Handler(Looper.myLooper()!!) }

    private val display: EGLDisplay get() = EGL14.eglGetCurrentDisplay()

    private val quad = GlQuad() // client-side, read-only at draw: safe to share across nodes
    private val programs = HashMap<String, GlProgram>() // fragment source -> linked program

    fun newPool(): OutputPool = OutputPool(POOL_SIZE)

    /**
     * Read [input] (the borrowed captured frame) into a fresh [pool] output buffer (GPU→GPU) and
     * deliver it wrapped as a Bitmap (null on failure) to [onOutput]. [onOutput] runs on the GL thread
     * after glFinish, so the input read is done and the caller may release its lease from inside it.
     */
    fun render(
        input: HardwareBuffer, width: Int, height: Int, effect: RuntimeEffect, pool: OutputPool,
        onOutput: (Bitmap?) -> Unit,
    ) {
        glRenderer.execute {
            val outputBitmap = runCatching {
                val inputTexture = InputTexture(display, input)
                val output = pool.next(display, width, height)
                output.bind()
                programFor(effect.source).draw(quad, inputTexture.id, width, height, effect.floatUniforms)
                // glFinish, not a fence: A/B (gfxinfo) showed no reliable win, and this is provably correct.
                GLES20.glFinish()
                inputTexture.release()
                wrap(output.buffer)
            }.getOrNull()
            onOutput(outputBitmap)
        }
    }

    fun releasePool(pool: OutputPool) {
        glRenderer.execute { pool.release() }
    }

    override fun close() {
        onGl {
            programs.values.forEach { it.release() }
            programs.clear()
        }
        glRenderer.stop(cancelPending = false)
    }

    // Run on the GL thread and block. Init (handler capture) and close only — never per frame.
    private fun <T> onGl(block: () -> T): T {
        var result: Result<T>? = null
        val latch = CountDownLatch(1)
        glRenderer.execute { result = runCatching(block); latch.countDown() }
        latch.await()
        return result!!.getOrThrow()
    }

    // Fresh wrapper each frame: pooled buffers are reused, but Compose needs a new Bitmap identity.
    private fun wrap(buffer: HardwareBuffer): Bitmap =
        Bitmap.wrapHardwareBuffer(buffer, ColorSpace.get(ColorSpace.Named.SRGB))
            ?: error("wrapHardwareBuffer returned null")

    private fun programFor(source: String): GlProgram = programs.getOrPut(source) { GlProgram(source) }

    private companion object {
        // 2-deep measured best: 3-deep regressed jank ~1%→14% (HWUI texture-cache thrash). See DESIGN.md.
        const val POOL_SIZE = 2
    }
}
