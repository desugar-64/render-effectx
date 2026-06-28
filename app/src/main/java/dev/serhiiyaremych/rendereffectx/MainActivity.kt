package dev.serhiiyaremych.rendereffectx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.serhiiyaremych.rendereffectx.ui.theme.RenderEffectXTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // `-e scene static` launches a single non-animating effect so idle can be measured
        // (gfxinfo should stop counting frames once it settles); default shows the animated demos.
        val staticScene = intent?.getStringExtra("scene") == "static"
        setContent {
            RenderEffectXTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { padding ->
                    if (staticScene) StaticSceneDemo(Modifier.padding(padding))
                    else Slice1Demo(Modifier.padding(padding))
                }
            }
        }
    }
}

/** One static effect, nothing animating — to verify the render loop idles on static content. */
@Composable
private fun StaticSceneDemo(modifier: Modifier = Modifier) {
    val source = remember { makeSourceBitmap(512, 512) }
    val blur = remember { RuntimeEffect.blur(radius = 3f) }
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Image(source.asImageBitmap(), null, Modifier.fillMaxWidth().runtimeEffect(blur))
    }
}

@Composable
private fun Slice1Demo(modifier: Modifier = Modifier) {
    val source = remember { makeSourceBitmap(512, 512) }

    // Every effect runs through Modifier.runtimeEffect: the composable's content is captured into a
    // HardwareBuffer and processed GPU→GPU. There is no CPU-bitmap input path.
    val passthrough = remember { RuntimeEffect.PASSTHROUGH }
    val tint = remember { RuntimeEffect.tint() }
    val staticBlur = remember { RuntimeEffect.blur(radius = 3f) } // stable instance → static content

    Column(
        modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("original (hardware bitmap)")
        Image(source.asImageBitmap(), null, Modifier.fillMaxWidth())

        Text("passthrough — should be identical to original")
        Image(source.asImageBitmap(), null, Modifier.fillMaxWidth().runtimeEffect(passthrough))

        Text("tint(strength=0.5) — proves we can alter pixels")
        Image(source.asImageBitmap(), null, Modifier.fillMaxWidth().runtimeEffect(tint))

        Text("blur(radius=3) — the recognizable wow")
        Image(source.asImageBitmap(), null, Modifier.fillMaxWidth().runtimeEffect(staticBlur))

        val clock = rememberInfiniteTransition(label = "clock")

        Text("animated wave — uniform-driven, recaptures nothing")
        val wavePhase by clock.animateFloat(
            0f, (2f * Math.PI).toFloat(),
            infiniteRepeatable(tween(1800, easing = LinearEasing)), label = "wave"
        )
        Image(
            source.asImageBitmap(), null,
            Modifier.fillMaxWidth().runtimeEffect(RuntimeEffect.wave(time = wavePhase, amplitude = 16f))
        )

        Text("animated blur — radius drives the same effect pass")
        val blurRadius by clock.animateFloat(
            0.3f, 6f,
            infiniteRepeatable(tween(1200, easing = LinearEasing), RepeatMode.Reverse), label = "blur"
        )
        Image(
            source.asImageBitmap(), null,
            Modifier.fillMaxWidth().runtimeEffect(RuntimeEffect.blur(radius = blurRadius))
        )

        Text("animated stripes — generative effect, ignores content (example, not in lib)")
        val stripesTime by clock.animateFloat(
            0f, (2f * Math.PI).toFloat(),
            infiniteRepeatable(tween(6000, easing = LinearEasing)), label = "stripes"
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(180.dp)
                .runtimeEffect(stripesEffect(time = stripesTime))
        )

        Text("animated CONTENT — recaptured automatically, no invalidate")
        AnimatedContentDemo(clock)
    }
}

/**
 * Plain animated widgets under a single [runtimeEffect]. Nothing tells the effect to update —
 * it tracks the moving box, spinning progress and ticking counter on its own (auto-recapture).
 */
@Composable
private fun AnimatedContentDemo(clock: InfiniteTransition) {
    val shift by clock.animateFloat(
        0f, 1f, infiniteRepeatable(tween(1500, easing = LinearEasing), RepeatMode.Reverse), label = "shift"
    )
    val count by clock.animateFloat(
        0f, 999f, infiniteRepeatable(tween(4000, easing = LinearEasing)), label = "count"
    )
    val blur = remember { RuntimeEffect.blur(radius = 4f) } // stable: content animates, not the effect
    Box(
        Modifier
            .fillMaxWidth()
            .height(180.dp)
            .runtimeEffect(blur)
    ) {
        Box(
            Modifier
                .graphicsLayer {
                    translationX = shift * 220.dp.toPx()
                    translationY = 24.dp.toPx()
                }
                .size(80.dp)
                .background(ComposeColor(0xFFFF5722))
        )
        CircularProgressIndicator(
            Modifier.align(Alignment.Center).size(64.dp), color = ComposeColor(0xFF3F51B5)
        )
        Text(
            "frame ${count.toInt()}",
            Modifier.align(Alignment.BottomEnd).padding(12.dp),
            color = ComposeColor(0xFF1B5E20)
        )
    }
}

/**
 * A throwaway source with gradient + shapes so passthrough-identity and tint are obvious. Drawn on a
 * mutable software bitmap (Canvas can't target HARDWARE), then copied to an immutable hardware bitmap
 * so the demo holds only GPU-backed content.
 */
private fun makeSourceBitmap(w: Int, h: Int): Bitmap {
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.shader = LinearGradient(
        0f, 0f, w.toFloat(), h.toFloat(),
        Color.rgb(255, 120, 0), Color.rgb(0, 80, 200), Shader.TileMode.CLAMP
    )
    canvas.drawRect(0f, 0f, w.toFloat(), h.toFloat(), paint)
    paint.shader = null
    paint.color = Color.WHITE
    canvas.drawCircle(w * 0.3f, h * 0.35f, w * 0.18f, paint)
    paint.color = Color.rgb(40, 40, 40)
    canvas.drawCircle(w * 0.7f, h * 0.65f, w * 0.12f, paint)
    // a top-left red marker so vertical flips are unmistakable
    paint.color = Color.RED
    canvas.drawRect(0f, 0f, w * 0.15f, h * 0.05f, paint)
    return bmp.copy(Bitmap.Config.HARDWARE, false).also { bmp.recycle() }
}
