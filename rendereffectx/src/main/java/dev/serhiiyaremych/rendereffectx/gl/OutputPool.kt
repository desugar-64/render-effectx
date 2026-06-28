package dev.serhiiyaremych.rendereffectx.gl

import android.opengl.EGLDisplay

/**
 * A round-robin pool of [OutputTexture] render targets: [next] cycles them so frame N reuses the
 * buffer last touched at N-[capacity]. Rebuilt when the size changes. GL thread only.
 */
internal class OutputPool(private val capacity: Int) {

    private var outputs: Array<OutputTexture> = emptyArray()
    private var index = 0
    private var width = 0
    private var height = 0

    fun next(display: EGLDisplay, width: Int, height: Int): OutputTexture {
        if (this.width != width || this.height != height || outputs.isEmpty()) {
            release()
            outputs = Array(capacity) { OutputTexture(display, width, height) }
            this.width = width
            this.height = height
        }
        return outputs[index].also { index = (index + 1) % outputs.size }
    }

    fun release() {
        outputs.forEach { it.release() }
        outputs = emptyArray()
        index = 0
        width = 0
        height = 0
    }
}
