package dev.serhiiyaremych.rendereffectx

import android.hardware.HardwareBuffer

/**
 * Single-in-flight handle to a captured [buffer]. The consumer samples it, then calls [release] once
 * its GL read completes (after the session's glFinish); only then does the producer get it back.
 */
internal class BufferLease(
    val buffer: HardwareBuffer,
    private val onRelease: () -> Unit,
) {
    private var released = false

    fun release() {
        if (!released) {
            released = true
            onRelease()
        }
    }
}
