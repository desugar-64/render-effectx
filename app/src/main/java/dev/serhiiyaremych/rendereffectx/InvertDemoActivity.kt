package dev.serhiiyaremych.rendereffectx

import android.graphics.Color as AndroidColor
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.serhiiyaremych.rendereffectx.ui.theme.RenderEffectXTheme

/**
 * The simplest possible scene, on its own: one card (the Jetpack Compose logo on white) with the
 * invert shader bound to a slider, so dragging mixes the content toward its inverse — white card to
 * black, logo colours flipped. This is the visual companion to the README's invert example; the
 * shader here is that snippet plus a single `amount` uniform.
 *
 * Launch standalone: `am start -n dev.serhiiyaremych.rendereffectx/.InvertDemoActivity`.
 * When untouched the slider auto-sweeps 0→1→0 (a clean loop for capture); touch takes it over.
 */
class InvertDemoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        setContent { RenderEffectXTheme(darkTheme = true, dynamicColor = false) { InvertScene() } }
    }
}

private val SceneBackground = Color(0xFF0B0B0F)

private val INVERT_SRC = """
    precision mediump float;
    uniform sampler2D content;
    uniform float amount;            // 0 = original, 1 = fully inverted
    varying vec2 vTexCoord;
    void main() {
        vec4 c = texture2D(content, vTexCoord);
        gl_FragColor = vec4(mix(c.rgb, 1.0 - c.rgb, amount), c.a);
    }
""".trimIndent()

private fun invertEffect(amount: Float): RuntimeEffect =
    RuntimeEffect(INVERT_SRC).apply { setFloatUniform("amount", amount) }

@Composable
private fun InvertScene() {
    // Auto-sweep when untouched (perfect loop for the README capture); a drag overrides it.
    val sweep by rememberInfiniteTransition(label = "invert").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1800, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "amount",
    )
    var manual by remember { mutableStateOf<Float?>(null) }
    val amount = manual ?: sweep

    Box(Modifier.fillMaxSize().background(SceneBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The card is the captured content; the whole thing — white field included — inverts.
            Box(
                Modifier
                    .size(240.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .runtimeEffect(invertEffect(amount)),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.fillMaxSize().background(Color.White))
                Image(
                    painter = painterResource(R.drawable.compose_logo),
                    contentDescription = "Jetpack Compose logo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(150.dp),
                )
            }

            Spacer(Modifier.height(28.dp))
            Text(
                "invert  ${"%.2f".format(amount)}",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(4.dp))
            Slider(
                value = amount,
                onValueChange = { manual = it },
                modifier = Modifier.width(240.dp).padding(horizontal = 4.dp),
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = Color.White,
                    inactiveTrackColor = Color.White.copy(alpha = 0.25f),
                ),
            )
        }
    }
}
