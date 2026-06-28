package dev.serhiiyaremych.rendereffectx

/**
 * Demo effects — they live in the app, not the library, exactly like [stripesEffect]. Each is a
 * small GLSL ES 2.0 fragment shader written against the [RuntimeEffect] contract: `sampler2D
 * content` (the captured composable), `varying vec2 vTexCoord` (0..1, Y-flip handled), `vec2
 * iResolution` (source px), plus the float uniforms set below.
 *
 * All three follow the established animation pattern: rebuild the effect every frame with new
 * uniform values. The session caches the compiled program by source string, so only the uniforms
 * change — animating these is cheap.
 */

// ── CRT ──────────────────────────────────────────────────────────────────────────────────────
/**
 * CRT television — runs the whole player card through an old-TV pass: barrel screen curvature,
 * chromatic-aberration edges, scanlines, a rolling refresh bar, an occasional vertical glitch bar
 * and a vignette. Purely time-driven; drive [time] from an animation. No interaction.
 *
 * Adapted from the Shadertoy shader "CRT" — https://www.shadertoy.com/view/tfdBzj — ported from its
 * GLSL ES 3.0 (`mainImage`/`iChannel0`/`texture`) form to this project's GLSL ES 2.0 contract
 * (`content`/`vTexCoord`/`texture2D`). The screen-edge mask is written to alpha, so the curved
 * screen fades into the dark page like a bezel.
 */
fun crtEffect(time: Float): RuntimeEffect =
    RuntimeEffect(CRT_SRC).apply {
        setFloatUniform("time", time)
    }

private val CRT_SRC = """
    precision mediump float;
    uniform sampler2D content;
    uniform vec2  iResolution;
    uniform float time;
    varying vec2  vTexCoord;

    void main() {
        vec2 uv = vTexCoord;

        // Occasional vertical glitch bar with a wavy horizontal displacement.
        float randomVal = fract(sin(time * 0.3) * 43758.5453);
        if (randomVal > 0.5) {
            float barPos = 0.1 + 0.8 * fract(sin(time * 1.2) * 12345.6789);
            if (abs(uv.x - barPos) < 0.03) {
                uv.x += sin(uv.y * 100.0 + time * 15.0) * 0.003;
            }
        }

        // CRT barrel curvature.
        vec2 centeredUV = (uv - 0.5) * 2.0;
        float r2 = dot(centeredUV, centeredUV);
        vec2 distortedUV = centeredUV * (1.0 + 0.03 * r2) * 0.5 + 0.5;

        // Soft screen-edge mask → written to alpha so the curved screen fades into the bezel.
        vec2 borderDist = min(distortedUV, 1.0 - distortedUV);
        float edgeMask = smoothstep(0.0, 0.02, borderDist.x) * smoothstep(0.0, 0.02, borderDist.y);

        // Chromatic aberration on the screen.
        float r = texture2D(content, distortedUV + vec2(0.003, 0.0)).r;
        float g = texture2D(content, distortedUV).g;
        float b = texture2D(content, distortedUV + vec2(-0.003, 0.0)).b;
        vec3 col = vec3(r, g, b) * edgeMask;

        // Scanlines.
        float scanLine = sin(uv.y * iResolution.y * 0.5 * 3.14159);
        col *= 0.6 + 0.4 * scanLine;

        // Rolling refresh bar.
        float roll = fract(uv.y + time * 0.3);
        float movingLine = smoothstep(0.0, 0.1, roll) - smoothstep(0.1, 0.2, roll);
        col += movingLine * 0.2;

        // Vignette.
        col *= 1.0 - length(centeredUV) * 0.3;

        gl_FragColor = vec4(col, edgeMask);
    }
""".trimIndent()

// ── Glitch ───────────────────────────────────────────────────────────────────────────────────
/**
 * Digital glitch burst — horizontal bands jump sideways, the RGB channels separate, scanlines and
 * stray bright blocks flicker. [intensity] (0..1) gates everything, so a burst that decays to 0
 * lands back on a clean image. [time] drives the choppy, stepped motion. Code + video only.
 */
fun glitchEffect(time: Float, intensity: Float): RuntimeEffect =
    RuntimeEffect(GLITCH_SRC).apply {
        setFloatUniform("time", time)
        setFloatUniform("intensity", intensity)
    }

private val GLITCH_SRC = """
    precision mediump float;
    uniform sampler2D content;
    uniform vec2  iResolution;
    uniform float time;
    uniform float intensity;  // 0..1, decays after a trigger
    varying vec2  vTexCoord;

    float hash(vec2 p) {
        return fract(sin(dot(p, vec2(127.1, 311.7))) * 43758.5453);
    }

    void main() {
        vec2 uv = vTexCoord;
        float t = floor(time * 18.0);          // faster stepped time → more frantic

        // Fine horizontal bands jump sideways; most bands move while intensity is high — big tears.
        float band = floor(uv.y * 40.0);
        float move = step(0.30, hash(vec2(band, t * 1.3)));
        float jump = (hash(vec2(band, t)) - 0.5) * 0.65 * intensity * move;
        uv.x += jump;                          // clamp sampling smears the edge — reads as a tear

        // A coarse second layer of blocky displacement so whole chunks shear.
        float blockRow = floor(uv.y * 11.0);
        uv.x += (hash(vec2(blockRow, t * 0.7)) - 0.5) * 0.22 * intensity
                * step(0.5, hash(vec2(blockRow, t)));

        // Occasional vertical roll/tear so the whole frame jumps now and then.
        uv.y += (hash(vec2(7.0, t)) - 0.5) * 0.10 * intensity * step(0.8, hash(vec2(3.0, t)));

        // RGB channel separation grows with intensity.
        float sep = 0.045 * intensity;
        float cr = texture2D(content, vec2(uv.x + sep, uv.y)).r;
        float cg = texture2D(content, uv).g;
        float cb = texture2D(content, vec2(uv.x - sep, uv.y)).b;
        vec3 col = vec3(cr, cg, cb);

        // Scanlines + bright/dark noise blocks flickering across the bands.
        float scan = 1.0 - 0.28 * intensity * step(0.5, fract(uv.y * iResolution.y * 0.5));
        col *= scan;
        float nz = hash(vec2(band, t + 7.0));
        col += intensity * step(0.82, nz) * 0.7;          // more frequent bright blocks
        col *= 1.0 - intensity * step(0.85, hash(vec2(band, t + 19.0))) * 0.85; // more dropout blocks
        // fine static grain sprinkled over everything while it glitches
        col += intensity * (hash(vec2(uv.x * 180.0, t + 31.0)) - 0.5) * 0.18;

        gl_FragColor = vec4(col, texture2D(content, uv).a);
    }
""".trimIndent()

// ── Chroma ───────────────────────────────────────────────────────────────────────────────────
/**
 * Chromatic-aberration shockwave — a ring radiates from a touch point, splitting the RGB channels
 * along the radial direction as it passes. Drive one tap as: [radius] expands 0 → ~1.4 while
 * [strength] decays 1 → 0. [centerX]/[centerY] are the tap in 0..1 texture space.
 */
fun chromaticRippleEffect(
    centerX: Float,
    centerY: Float,
    radius: Float,
    strength: Float,
): RuntimeEffect = RuntimeEffect(CHROMATIC_RIPPLE_SRC).apply {
    setFloatUniform("center", centerX, centerY)
    setFloatUniform("radius", radius)
    setFloatUniform("strength", strength)
}

private val CHROMATIC_RIPPLE_SRC = """
    precision mediump float;
    uniform sampler2D content;
    uniform vec2  iResolution;
    uniform vec2  center;     // tap point, 0..1
    uniform float radius;     // current ring radius, expands outward
    uniform float strength;   // 0..1, decays as the ring fades
    varying vec2  vTexCoord;

    void main() {
        vec2 uv  = vTexCoord;
        vec2 dir = uv - center;
        float aspect = iResolution.x / iResolution.y;
        float dist = length(vec2(dir.x * aspect, dir.y));   // aspect-correct so the ring stays round

        // A soft gaussian ring at `radius`; channels split where it currently is.
        float ring   = exp(-pow((dist - radius) * 6.0, 2.0));
        float amount = ring * strength;

        vec2 n   = normalize(dir + 1e-5);
        float off = amount * 0.075;                         // wider channel split → more vivid
        vec3 col;
        col.r = texture2D(content, uv + n * off).r;
        col.g = texture2D(content, uv + n * off * 0.3).g;   // green lags slightly for a richer fringe
        col.b = texture2D(content, uv - n * off).b;
        col += amount * 0.22;                               // brighter crest highlight

        gl_FragColor = vec4(col, texture2D(content, uv).a);
    }
""".trimIndent()
