package de.snowworks.ariana.presence

/**
 * Small immutable snapshot used to verify Ariana Presence on a real device.
 *
 * The goal is to make device proof observable without coupling diagnostics to
 * a concrete TTS vendor or avatar renderer.
 */
data class PresenceDiagnostics(
    val voiceReady: Boolean,
    val voiceEnginePackage: String?,
    val utterancesStarted: Long,
    val rangeCallbacksObserved: Long,
    val speaking: Boolean,
    val currentUtteranceRangeCallbacks: Long = 0L,
) {
    enum class LipSyncMode {
        IDLE,
        TEXT_TIMING,
        FALLBACK_PULSE,
    }

    val lipSyncMode: LipSyncMode
        get() = when {
            !speaking -> LipSyncMode.IDLE
            currentUtteranceRangeCallbacks > 0L -> LipSyncMode.TEXT_TIMING
            else -> LipSyncMode.FALLBACK_PULSE
        }

    fun compactLabel(): String = buildString {
        append(if (voiceReady) "voice-ready" else "voice-starting")
        append(" · ")
        append(voiceEnginePackage ?: "engine-unknown")
        append(" · ")
        append(
            when (lipSyncMode) {
                LipSyncMode.IDLE -> "lip-idle"
                LipSyncMode.TEXT_TIMING -> "lip-text-timing"
                LipSyncMode.FALLBACK_PULSE -> "lip-fallback"
            },
        )
    }
}
