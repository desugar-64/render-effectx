package dev.serhiiyaremych.rendereffectx

import android.graphics.Bitmap
import android.os.Handler
import android.view.View
import dev.serhiiyaremych.rendereffectx.gl.OutputPool
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Canvas as ComposeCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.node.CompositionLocalConsumerModifierNode
import androidx.compose.ui.node.DrawModifierNode
import androidx.compose.ui.node.ModifierNodeElement
import androidx.compose.ui.node.currentValueOf
import androidx.compose.ui.node.invalidateDraw
import androidx.compose.ui.node.requireGraphicsContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.toSize
import kotlin.math.roundToInt

/** Applies a [RuntimeEffect] to this composable's own content, reactively (content can animate). */
fun Modifier.runtimeEffect(effect: RuntimeEffect): Modifier = this then RuntimeEffectElement(effect)

private data class RuntimeEffectElement(val effect: RuntimeEffect) :
    ModifierNodeElement<RuntimeEffectNode>() {
    override fun create() = RuntimeEffectNode(effect)

    // Effect changed but content didn't move, so draw() won't re-trigger: request the re-render here.
    override fun update(node: RuntimeEffectNode) {
        node.effect = effect; node.onEffectChanged()
    }
}

private class RuntimeEffectNode(var effect: RuntimeEffect) :
    Modifier.Node(), DrawModifierNode, CompositionLocalConsumerModifierNode {

    private var session: RuntimeEffectSession? = null
    private var pool: OutputPool? = null
    private var layer: GraphicsLayer? = null
    private var capture: LayerBufferCapture? = null
    private val rasterScope = CanvasDrawScope()
    private var view: View? = null

    private var output: ImageBitmap? = null // plain field: read in draw() must NOT auto-invalidate
    private var outputBitmap: Bitmap? = null

    // Superseded output bitmaps awaiting a lagged recycle; recycled on the UI thread in draw().
    private val pendingRecycle = ArrayDeque<Bitmap>()
    private var contentSize = IntSize.Zero
    private var contentDensity = Density(1f)
    private var contentLayoutDirection = LayoutDirection.Ltr

    // Hops just past the current draw/compose pass; remove+post coalesces a frame's requests into one.
    private val renderRunnable = Runnable { renderFrame() }

    // True between installing an output and the draw that presents it, so that draw isn't mistaken
    // for a content change and made to re-trigger a capture (which would self-perpetuate forever).
    private var presentingOutput = false

    private fun requestRender() {
        view?.let {
            it.removeCallbacks(renderRunnable)
            it.post(renderRunnable)
        }
    }

    fun onEffectChanged() = requestRender()

    override fun onAttach() {
        val session = SharedRuntimeEffectSession.instance.also { session = it }
        pool = session.newPool()
        layer = requireGraphicsContext().createGraphicsLayer()
        view = currentValueOf(LocalView)
    }

    override fun onDetach() {
        view?.removeCallbacks(renderRunnable)
        view = null
        capture?.close(); capture = null
        layer?.let { requireGraphicsContext().releaseGraphicsLayer(it) }
        layer = null
        output = null
        outputBitmap = null
        pendingRecycle.forEach { it.recycle() } // already superseded, off-screen — safe now
        pendingRecycle.clear()
        session?.let { s -> pool?.let { s.releasePool(it) } }
        session = null; pool = null
    }

    override fun ContentDrawScope.draw() {
        val layer = layer
        if (layer != null && drawContext.canvas.nativeCanvas.isHardwareAccelerated) {
            contentSize = IntSize(size.width.roundToInt(), size.height.roundToInt())
            layer.record(contentSize) { this@draw.drawContent() }
            contentDensity = Density(density, fontScale)
            contentLayoutDirection = layoutDirection
            recycleSuperseded()
            if (presentingOutput) presentingOutput = false else requestRender()
            output?.let { drawImage(it, dstSize = contentSize) } // upscales downsampled output to fill
                ?: drawLayer(layer) // raw content until first result is ready
        } else {
            drawContent() // no layer / software canvas: pass through
        }
    }

    private fun renderFrame() {
        val layer = layer
        val session = session
        val pool = pool
        val view = view
        val size = contentSize
        if (layer != null && session != null && pool != null && view != null &&
            size.width > 0 && size.height > 0
        ) {
            val renderScale = effect.config.scale
            val captureSize = scaledSize(size, renderScale)
            ensureCapture(captureSize, session.handler).renderAsync(
                recordContent = { canvas ->
                    rasterScope.draw(
                        density = contentDensity,
                        layoutDirection = contentLayoutDirection,
                        canvas = ComposeCanvas(canvas),
                        size = captureSize.toSize()
                    ) {
                        // Downscale full-res layer into the smaller capture buffer, pivot at origin.
                        if (renderScale == 1f) drawLayer(layer)
                        else scale(renderScale, renderScale, pivot = Offset.Zero) { drawLayer(layer) }
                    }
                },
                onCaptured = { capturedLease ->
                    if (capturedLease != null) {
                        session.render(
                            input = capturedLease.buffer,
                            width = captureSize.width,
                            height = captureSize.height,
                            effect = effect,
                            pool = pool
                        ) { effectOutput ->
                            capturedLease.release() // onOutput runs post-glFinish: input fully read
                            if (effectOutput != null) view.post { installOutput(effectOutput) }
                        }
                    }
                }
            )
        }
    }

    private fun scaledSize(size: IntSize, scale: Float): IntSize =
        if (scale >= 1f) size
        else IntSize(
            (size.width * scale).roundToInt().coerceAtLeast(1),
            (size.height * scale).roundToInt().coerceAtLeast(1),
        )

    // UI thread. Queue the replaced bitmap for a lagged recycle — recycling now would pull it out
    // from under a display list still on screen.
    private fun installOutput(bitmap: Bitmap) {
        outputBitmap?.let { pendingRecycle.addLast(it) }
        outputBitmap = bitmap
        output = bitmap.asImageBitmap()
        presentingOutput = true
        invalidateDraw()
    }

    // UI thread, mid-draw: keep RECYCLE_MARGIN un-recycled so the RenderThread can't still be
    // replaying a retained list, then recycle the rest before the pool reuses their buffers.
    private fun recycleSuperseded() {
        while (pendingRecycle.size > RECYCLE_MARGIN) pendingRecycle.removeFirst().recycle()
    }

    private fun ensureCapture(size: IntSize, handler: Handler): LayerBufferCapture {
        val current = capture
        return if (current != null && current.width == size.width && current.height == size.height) {
            current
        } else {
            current?.close()
            LayerBufferCapture(size.width, size.height, handler).also { capture = it }
        }
    }

    private companion object {
        const val RECYCLE_MARGIN = 2 // matches the output pool depth (RenderThread replay lag)
    }
}
