package dev.serhiiyaremych.rendereffectx

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.HardwareRenderer
import android.graphics.ImageFormat
import android.graphics.PorterDuff
import android.graphics.RenderNode
import android.hardware.HardwareBuffer
import android.media.Image
import android.media.ImageReader
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.concurrent.CountDownLatch

/**
 * Captures recorded content to a GPU [HardwareBuffer] with no CPU readback: a [RenderNode] rendered by
 * a [HardwareRenderer] into an [ImageReader]'s GPU-sampled surface. The `Free → Writing → Leased` gate
 * keeps at most one render in flight, so the caller needs no pending/capturing flags.
 */
internal class LayerBufferCapture(
    val width: Int, val height: Int, private val handler: Handler,
) : AutoCloseable {

    private val lock = Any()
    private val content = RenderNode("rfx-capture").apply { setPosition(0, 0, width, height) }
    private val reader = ImageReader.newInstance(
        width, height, ImageFormat.PRIVATE, IMAGE_COUNT, HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
    )
    private val renderer = HardwareRenderer().apply {
        isOpaque = true
        setName("rfx-capture")
        setContentRoot(content)
        setSurface(reader.surface)
        start()
    }

    private sealed interface State {
        data object Free : State
        data class Writing(val onCaptured: (BufferLease?) -> Unit) : State
        data class Leased(val image: Image) : State
        data object Closed : State
    }

    private var state: State = State.Free

    init {
        reader.setOnImageAvailableListener({ acquire() }, handler) // acquire runs on the session thread
    }

    /**
     * Record content and kick a GPU render. The captured input [BufferLease] is delivered to
     * [onCaptured] on the session thread (null on failure). Returns false without recording if a
     * render is already in flight.
     */
    fun renderAsync(recordContent: (Canvas) -> Unit, onCaptured: (BufferLease?) -> Unit): Boolean {
        val accepted = synchronized(lock) {
            (state == State.Free).also { free -> if (free) state = State.Writing(onCaptured) }
        }
        if (accepted) {
            val recorded = runCatching {
                content.setPosition(0, 0, width, height)
                content.record(width, height) { canvas ->
                    canvas.drawColor(Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
                    recordContent(canvas)
                }
            }
            if (recorded.isFailure) {
                Log.w(TAG, "record failed", recorded.exceptionOrNull())
                fail()
            } else {
                val status = renderer.createRenderRequest().setWaitForPresent(false).syncAndDraw()
                if (status != HardwareRenderer.SYNC_OK) {
                    Log.w(TAG, "syncAndDraw failed: $status")
                    fail()
                }
            }
        }
        return accepted
    }

    // The produced image arrived: lease its buffer to the waiting consumer, else drop it and reset.
    private fun acquire() {
        val image = runCatching { reader.acquireNextImage() }.getOrNull()
        if (image != null) {
            val buffer = image.hardwareBuffer
            val onCaptured = synchronized(lock) {
                val writing = state as? State.Writing
                if (writing != null && buffer != null) {
                    state = State.Leased(image)
                    writing.onCaptured
                } else null
            }
            if (onCaptured != null) {
                onCaptured(lease(buffer!!, image))
            } else {
                buffer?.close()
                image.close()
                if (buffer == null) fail()
            }
        } else {
            fail()
        }
    }

    // No acquire fence on API 30 (Image.getFence is 33+); rely on implicit BufferQueue sync.
    private fun lease(buffer: HardwareBuffer, image: Image): BufferLease =
        BufferLease(buffer, onRelease = {
            fun recycle() {
                buffer.close()
                image.close()
                synchronized(lock) { if (state is State.Leased) state = State.Free }
            }
            // release() usually runs on the session thread (render's onResult); post only if not.
            if (Looper.myLooper() === handler.looper) recycle() else handler.post(::recycle)
        })

    // Reset the gate and notify the waiting consumer of failure so it never wedges in Writing.
    private fun fail() {
        val notify = synchronized(lock) {
            (state as? State.Writing)?.also { state = State.Free }?.onCaptured
        }
        notify?.invoke(null)
    }

    override fun close() {
        synchronized(lock) { state = State.Closed }
        // The thread is the shared session's; post teardown and block so a resize can't race the old
        // renderer/reader. Only ever called from the UI thread.
        val torn = CountDownLatch(1)
        handler.post {
            renderer.stop()
            renderer.destroy()
            reader.close()
            torn.countDown()
        }
        torn.await()
        content.discardDisplayList()
    }

    private companion object {
        const val TAG = "rfx"
        const val IMAGE_COUNT = 2 // two buffers so HWUI's dequeue never stalls
    }
}

private inline fun RenderNode.record(width: Int, height: Int, block: (Canvas) -> Unit) {
    val canvas = beginRecording(width, height)
    try {
        block(canvas)
    } finally {
        endRecording()
    }
}
