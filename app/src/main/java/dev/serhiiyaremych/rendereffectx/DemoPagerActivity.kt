package dev.serhiiyaremych.rendereffectx

import android.graphics.Color as AndroidColor
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.serhiiyaremych.rendereffectx.ui.theme.RenderEffectXTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Landscape demo scene — a [HorizontalPager] of three mini-demos, each applying one animatable GPU
 * effect directly to live Compose content via [Modifier.runtimeEffect]. This is the scene recorded
 * for the README / social material. Locked to landscape in the manifest.
 *
 *   Page 0 · Frost  — gradient blur on a "now playing" card; breathes + toggles.
 *   Page 1 · Glitch — digital glitch burst on a profile card; idle jitter + button trigger.
 *   Page 2 · Chroma — chromatic-aberration ripple on a photo tile; tap to send a shockwave.
 */
class DemoPagerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Transparent system bars with light icons — the dark scene draws straight through them, so
        // no white nav bar and no dark-on-dark status icons during the recording.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
        )
        // Fill the whole landscape display, including any display cutout.
        window.attributes = window.attributes.apply {
            layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        setContent { RenderEffectXTheme(darkTheme = true, dynamicColor = false) { DemoPager() } }
    }
}

private val PageBackground = Color(0xFF0B0B0F)
private val CardWidth = 300.dp
private val CardHeight = 380.dp
private val CardShape = RoundedCornerShape(26.dp)

@Composable
private fun DemoPager() {
    val pagerState = rememberPagerState(pageCount = { 3 })
    Box(Modifier.fillMaxSize().background(PageBackground)) {
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> SignalPage()
                1 -> GlitchPage()
                else -> ChromaPage()
            }
        }
        PageDots(
            count = 3,
            current = pagerState.currentPage,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(WindowInsets.safeDrawing.asPaddingValues().calculateBottomPadding() + 14.dp),
        )
    }
}

/**
 * Landscape two-column frame: a text/controls panel on the left, the (large) effect card on the
 * right. Built for horizontal orientation — the card gets the vertical room it needs instead of
 * being squeezed between a header and a footer.
 */
@Composable
private fun DemoScaffold(
    index: String,
    title: String,
    subtitle: String,
    insets: PaddingValues,
    controls: @Composable () -> Unit,
    card: @Composable () -> Unit,
) {
    Row(
        Modifier
            .fillMaxSize()
            .padding(insets)
            .padding(horizontal = 40.dp, vertical = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(
            Modifier.weight(1f).fillMaxHeight().padding(end = 32.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(index, color = Color.White.copy(alpha = 0.3f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
            Text(title, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            Text(subtitle, color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(30.dp))
            controls()
        }
        Box(
            Modifier.weight(1f).fillMaxHeight(),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.size(CardWidth, CardHeight)) { card() }
        }
    }
}

// ── Page 0 · Frost ─────────────────────────────────────────────────────────────────────────────
@Composable
private fun SignalPage() {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val clock = rememberInfiniteTransition(label = "crt")
    val time by clock.animateFloat(
        0f, 600f, infiniteRepeatable(tween(600_000, easing = LinearEasing)), label = "time",
    )

    DemoScaffold(
        index = "01 · CRT",
        title = "CRT television",
        subtitle = "The whole player runs through an old-TV pass — screen curvature, scanlines, a rolling bar and chromatic edges. Auto-animated.",
        insets = insets,
        controls = { Text("auto · no touch needed", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp) },
    ) {
        NowPlayingCard(
            Modifier
                .fillMaxSize()
                .clip(CardShape)
                .runtimeEffect(crtEffect(time = time)),
        )
    }
}

@Composable
private fun NowPlayingCard(modifier: Modifier = Modifier) {
    Column(modifier.background(Color(0xFF16161E))) {
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF5F6D), Color(0xFFFF8E53), Color(0xFF845EC2)),
                    ),
                ),
        ) {
            Text(
                "♫",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 64.sp,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Column(Modifier.padding(16.dp)) {
            Text("Midnight Haze", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("desugar_64", color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.18f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(0.42f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1:24", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                Text("3:15", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
            }
        }
    }
}

// ── Page 1 · Glitch ──────────────────────────────────────────────────────────────────────────
@Composable
private fun GlitchPage() {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val scope = rememberCoroutineScope()
    val burst = remember { Animatable(0f) }

    val clock = rememberInfiniteTransition(label = "glitch")
    val time by clock.animateFloat(
        0f, 60f, infiniteRepeatable(tween(60_000, easing = LinearEasing)), label = "time",
    )
    val idle by clock.animateFloat(
        0.16f, 0.30f,
        infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Reverse), label = "idle",
    )
    val intensity = maxOf(idle, burst.value)

    DemoScaffold(
        index = "02 · GLITCH",
        title = "Glitch burst",
        subtitle = "Channel split, tearing and scanlines — tap to fire a burst that decays back to clean.",
        insets = insets,
        controls = {
            Button(
                onClick = { scope.launch { burst.snapTo(1f); burst.animateTo(0f, tween(1100, easing = LinearEasing)) } },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE5375A)),
            ) { Text("GLITCH") }
        },
    ) {
        ProfileCard(
            Modifier
                .fillMaxSize()
                .clip(CardShape)
                .runtimeEffect(glitchEffect(time = time, intensity = intensity)),
        )
    }
}

@Composable
private fun ProfileCard(modifier: Modifier = Modifier) {
    Box(modifier.background(Color(0xFF101822))) {
        // Real photo as the profile background (the glitch then tears an actual image).
        Image(
            painter = painterResource(R.drawable.profile_android_sunrise),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.matchParentSize(),
        )
        // Bottom-weighted scrim so the name and chips stay readable over the photo.
        Box(
            Modifier
                .matchParentSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color(0x22000000),
                        0.45f to Color(0x55000000),
                        1.0f to Color(0xEE000000),
                    ),
                ),
        )
        Column(
            Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom,
        ) {
            Box(
                Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF00F2FE), Color(0xFF4FACFE)))),
                contentAlignment = Alignment.Center,
            ) { Text("S Y", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold) }
            Spacer(Modifier.height(10.dp))
            Text("Serhii Y.", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(2.dp))
            Text("@desugar_64", color = Color(0xFF7FD0FF), fontSize = 13.sp)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Pill("API 29+")
                Pill("GPU")
                Pill("∞ FX")
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

@Composable
private fun Pill(text: String) {
    Text(
        text,
        color = Color.White,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

// ── Page 2 · Chroma ──────────────────────────────────────────────────────────────────────────
@Composable
private fun ChromaPage() {
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(1f) }            // 1 = finished (no ring on screen)
    var center by remember { mutableStateOf(Offset(0.5f, 0.5f)) }

    fun ripple(at: Offset) {
        center = at
        scope.launch {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(1500, easing = LinearOutSlowInEasing))
        }
    }
    // Idle pulse from the centre so the page is never static between taps.
    LaunchedEffect(Unit) {
        while (true) {
            delay(3200)
            if (progress.value >= 1f) ripple(Offset(0.5f, 0.5f))
        }
    }

    val radius = progress.value * 1.4f
    val strength = 1f - progress.value

    DemoScaffold(
        index = "03 · CHROMA",
        title = "Chromatic ripple",
        subtitle = "Tap the photo — an RGB shockwave ripples out from your finger, then fades.",
        insets = insets,
        controls = { Text("tap anywhere on the photo →", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp) },
    ) {
        PhotoTile(
            Modifier
                .fillMaxSize()
                .clip(CardShape)
                .pointerInput(Unit) {
                    detectTapGestures { pos ->
                        ripple(Offset(pos.x / size.width, pos.y / size.height))
                    }
                }
                .runtimeEffect(
                    chromaticRippleEffect(
                        centerX = center.x,
                        centerY = center.y,
                        radius = radius,
                        strength = strength,
                    ),
                ),
        )
    }
}

@Composable
private fun PhotoTile(modifier: Modifier = Modifier) {
    // A layered "sunset" so chromatic edges are easy to read: sky gradient, sun disc, dark hills.
    Box(modifier.background(Brush.verticalGradient(listOf(Color(0xFF2B5876), Color(0xFFFF7E5F), Color(0xFFFEB47B))))) {
        Box(
            Modifier
                .align(Alignment.Center)
                .padding(top = 24.dp)
                .size(96.dp)
                .clip(CircleShape)
                .background(Brush.radialGradient(listOf(Color(0xFFFFF1A8), Color(0xFFFFD25F)))),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(70.dp)
                .background(Brush.verticalGradient(listOf(Color(0xFF20142B), Color(0xFF000000)))),
        )
        Text(
            "render-effectx",
            color = Color.White.copy(alpha = 0.85f),
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.align(Alignment.BottomStart).padding(12.dp),
        )
    }
}

// ── Pager dots ───────────────────────────────────────────────────────────────────────────────
@Composable
private fun PageDots(count: Int, current: Int, modifier: Modifier = Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(count) { i ->
            Box(
                Modifier
                    .size(if (i == current) 9.dp else 7.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = if (i == current) 0.9f else 0.3f)),
            )
        }
    }
}
