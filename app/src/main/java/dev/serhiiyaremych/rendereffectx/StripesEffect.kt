package dev.serhiiyaremych.rendereffectx

/**
 * Example effect — lives in the app, not the library. A generative animated-stripes shader ported
 * from JetLagged's AGSL `StripesShaderBackground`, written natively against our GLSL ES 2.0 contract
 * (no AGSL preprocessing). It ignores `content` and exercises the vec4 [RuntimeEffect.setColorUniform]
 * plus the built-in `iResolution`.
 *
 * Drive [time] from an animation; [stripeColor] / [backgroundColor] are packed ARGB ints
 * (e.g. [android.graphics.Color]).
 */
fun stripesEffect(
    time: Float,
    stripeColor: Int = 0xFF4488FF.toInt(),
    backgroundColor: Int = 0xFF101018.toInt(),
): RuntimeEffect = RuntimeEffect(STRIPES_SRC).apply {
    setFloatUniform("time", time)
    setColorUniform("stripeColor", stripeColor)
    setColorUniform("backgroundColor", backgroundColor)
    setFloatUniform("backgroundLuminance", luminanceOf(backgroundColor))
}

/** Rec. 709 relative luminance of a packed ARGB color, 0..1. */
private fun luminanceOf(color: Int): Float {
    val r = ((color shr 16) and 0xFF) / 255f
    val g = ((color shr 8) and 0xFF) / 255f
    val b = (color and 0xFF) / 255f
    return 0.2126f * r + 0.7152f * g + 0.0722f * b
}

private val STRIPES_SRC = """
    precision highp float;
    uniform float time;
    uniform float backgroundLuminance;
    uniform vec4  backgroundColor;
    uniform vec4  stripeColor;
    uniform vec2  iResolution;
    varying vec2  vTexCoord;

    float calculateColorMultiplier(float yCoord, float factor, bool fadeToDark) {
        float result = step(yCoord, 1.0 + factor * 2.0) - step(yCoord, factor - 0.1);
        if (fadeToDark) result *= -2.4;
        return result;
    }

    void main() {
        const float speedMultiplier     = 1.5;
        const float waves               = 7.0;
        const float waveCurveMultiplier = 4.3;
        const float energyMultiplier    = 0.1;
        const float backgroundTolerance = 0.1;

        vec2  fragCoord  = vec2(vTexCoord.x, 1.0 - vTexCoord.y) * iResolution;
        vec2  uv         = fragCoord / iResolution;
        float energy     = waves * energyMultiplier;
        float timeOffset = time * speedMultiplier;
        vec3  rgbColor   = stripeColor.rgb;
        float hAdjustment    = uv.x * waveCurveMultiplier;
        float loopMultiplier = 0.7 / waves;
        vec3  loopColor   = (vec3(1.0) - rgbColor) / waves;
        bool  fadeToDark  = backgroundLuminance < 0.5;

        for (float i = 1.0; i <= waves; i += 1.0) {
            float loopFactor = i * loopMultiplier;
            float curve = sin((timeOffset + hAdjustment) * energy) * (1.0 - loopFactor) * 0.05;
            rgbColor += loopColor * calculateColorMultiplier(uv.y, loopFactor, fadeToDark);
            uv.y += curve;
        }

        if (fadeToDark) {
            if (all(lessThanEqual(rgbColor, vec3(backgroundTolerance)))) rgbColor = backgroundColor.rgb;
        } else {
            if (all(greaterThanEqual(rgbColor, vec3(1.0 - backgroundTolerance)))) rgbColor = backgroundColor.rgb;
        }

        gl_FragColor = vec4(rgbColor, 1.0);
    }
""".trimIndent()
