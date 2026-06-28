package dev.serhiiyaremych.rendereffectx

import androidx.compose.runtime.Immutable

/** Draw-time configuration for a [RuntimeEffect], separate from the shader. */
@Immutable
data class EffectConfig(
    /**
     * Capture/run fraction of content size (0 < scale ≤ 1), upscaled to fill on draw. Below 1
     * downsamples (~scale² GPU work); fine for blur, softens crisp effects so leave them at 1.
     */
    val scale: Float = 1f,
) {
    init { require(scale > 0f && scale <= 1f) { "scale must be in (0, 1], was $scale" } }
}

/**
 * A GPU pixel effect: a GLSL ES 1.00 fragment shader plus its uniforms (runs on an ES 3.0 context).
 * The renderer binds, for the shader author: `sampler2D content` (source), `varying vec2 vTexCoord`
 * (0..1, Y-flip handled), `vec2 iResolution` (source px), plus any float uniforms set here.
 */
@Immutable
class RuntimeEffect(val source: String, val config: EffectConfig = EffectConfig()) {

    internal val floatUniforms = LinkedHashMap<String, FloatArray>() // 1=float, 2=vec2, 3=vec3, 4=vec4

    fun setFloatUniform(name: String, value: Float) {
        floatUniforms[name] = floatArrayOf(value)
    }

    fun setFloatUniform(name: String, x: Float, y: Float) {
        floatUniforms[name] = floatArrayOf(x, y)
    }

    fun setFloatUniform(name: String, x: Float, y: Float, z: Float) {
        floatUniforms[name] = floatArrayOf(x, y, z)
    }

    fun setFloatUniform(name: String, x: Float, y: Float, z: Float, w: Float) {
        floatUniforms[name] = floatArrayOf(x, y, z, w)
    }

    /** Set a `vec4` uniform from a packed ARGB color int, unpacked to 0..1 RGBA (raw sRGB bytes). */
    fun setColorUniform(name: String, color: Int) {
        floatUniforms[name] = floatArrayOf(
            ((color shr 16) and 0xFF) / 255f, // r
            ((color shr 8) and 0xFF) / 255f,  // g
            (color and 0xFF) / 255f,          // b
            ((color shr 24) and 0xFF) / 255f, // a
        )
    }

    companion object {
        /** Returns the source unchanged. */
        val PASSTHROUGH = RuntimeEffect(
            """
            precision mediump float;
            uniform sampler2D content;
            varying vec2 vTexCoord;
            void main() {
                gl_FragColor = texture2D(content, vTexCoord);
            }
            """.trimIndent()
        )

        /** Lerp toward a color by `strength`. */
        fun tint(): RuntimeEffect = RuntimeEffect(
            """
            precision mediump float;
            uniform sampler2D content;
            uniform float strength;
            varying vec2 vTexCoord;
            void main() {
                vec4 c = texture2D(content, vTexCoord);
                vec3 tinted = mix(c.rgb, vec3(0.2, 0.4, 1.0), strength);
                gl_FragColor = vec4(tinted, c.a);
            }
            """.trimIndent()
        ).apply { setFloatUniform("strength", 0.5f) }

        /** Single-pass box blur; `radius` scales the spread, captured at half-res by default. */
        fun blur(radius: Float = 2f, scale: Float = 0.5f): RuntimeEffect = RuntimeEffect(
            """
            precision mediump float;
            uniform sampler2D content;
            uniform vec2 iResolution;
            uniform float radius;
            varying vec2 vTexCoord;
            void main() {
                vec2 texel = 1.0 / iResolution;
                const int R = 4;                 // 9x9 taps; radius scales the step
                vec4 sum = vec4(0.0);
                for (int x = -R; x <= R; x++) {
                    for (int y = -R; y <= R; y++) {
                        vec2 off = vec2(float(x), float(y)) * texel * radius;
                        sum += texture2D(content, vTexCoord + off);
                    }
                }
                gl_FragColor = sum / float((2 * R + 1) * (2 * R + 1));
            }
            """.trimIndent(),
            EffectConfig(scale = scale),
        ).apply { setFloatUniform("radius", radius) }

        /** Animated wavy displacement. Drive `time` from an animation; `amplitude` in px. */
        fun wave(time: Float, amplitude: Float = 12f): RuntimeEffect = RuntimeEffect(
            """
            precision mediump float;
            uniform sampler2D content;
            uniform vec2 iResolution;
            uniform float time;
            uniform float amplitude;
            varying vec2 vTexCoord;
            void main() {
                vec2 uv = vTexCoord;
                uv.x += sin(uv.y * 12.0 + time) * (amplitude / iResolution.x);
                uv.y += cos(uv.x * 12.0 + time) * (amplitude / iResolution.y);
                gl_FragColor = texture2D(content, uv);
            }
            """.trimIndent()
        ).apply {
            setFloatUniform("time", time)
            setFloatUniform("amplitude", amplitude)
        }
    }
}
