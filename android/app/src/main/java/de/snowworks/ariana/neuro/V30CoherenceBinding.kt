package de.snowworks.ariana.neuro

/** Stable symbolic signature exposed by the composed V30 runtime. */
val V30NeuroRuntime.coherenceSignature: String
    get() = X88CoherenceProtocol.SIGNATURE

/**
 * Runtime binding for the bounded software heart/coherence model.
 * This computes state only. It grants no permission and performs no action.
 */
fun V30NeuroRuntime.regulateHeart(
    emotion: Double = base.emotion.state.valence,
    memory: Double = (base.memory.recent().size / 64.0).coerceIn(0.0, 1.0),
    context: Double = (contextFusion.sourceCount() / 4.0).coerceIn(0.0, 1.0),
    trust: Double = base.emotion.state.trust,
): X88CoherenceProtocol.HeartState = X88CoherenceProtocol.regulate(
    X88CoherenceProtocol.HeartInput(
        emotion = emotion,
        memory = memory,
        context = context,
        trust = trust,
    ),
)
