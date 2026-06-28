package dev.serhiiyaremych.rendereffectx

/**
 * The single process-lifetime [RuntimeEffectSession] borrowed by every effect node. `by lazy`
 * (SYNCHRONIZED) constructs exactly once even under races; a CAS-loop could leak GL threads.
 */
internal object SharedRuntimeEffectSession {
    val instance: RuntimeEffectSession by lazy { RuntimeEffectSession() }
}
