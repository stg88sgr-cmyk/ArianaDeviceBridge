package de.snowworks.ariana.bridge

/**
 * Small in-process circuit breaker for remote AI providers.
 *
 * Only transient transport/upstream failures count against health. Configuration,
 * authentication and request-validation errors stay visible but do not poison
 * provider health. After the cooldown, exactly one half-open probe is allowed.
 */
object AiProviderHealth {
    const val FAILURE_THRESHOLD = 3
    const val COOLDOWN_MS = 60_000L

    data class Snapshot(
        val providerId: String,
        val consecutiveFailures: Int,
        val circuitOpen: Boolean,
        val openUntilMs: Long,
        val halfOpenProbeInFlight: Boolean,
        val lastError: String? = null,
    )

    private data class State(
        var consecutiveFailures: Int = 0,
        var openUntilMs: Long = 0L,
        var halfOpenProbeInFlight: Boolean = false,
        var lastError: String? = null,
    )

    private val states = linkedMapOf<String, State>()

    @Synchronized
    fun acquireAttempt(providerId: String, nowMs: Long = monotonicNowMs()): Boolean {
        val state = states.getOrPut(providerId) { State() }
        if (state.openUntilMs == 0L) return true
        if (nowMs < state.openUntilMs) return false
        if (state.halfOpenProbeInFlight) return false

        state.halfOpenProbeInFlight = true
        return true
    }

    @Synchronized
    fun recordSuccess(providerId: String) {
        states.remove(providerId)
    }

    @Synchronized
    fun recordFailure(providerId: String, errorCode: String, nowMs: Long = monotonicNowMs()) {
        val state = states.getOrPut(providerId) { State() }
        state.lastError = errorCode

        if (!isTransient(errorCode)) {
            state.halfOpenProbeInFlight = false
            return
        }

        val failedHalfOpenProbe = state.halfOpenProbeInFlight
        state.halfOpenProbeInFlight = false
        state.consecutiveFailures += 1

        if (failedHalfOpenProbe || state.consecutiveFailures >= FAILURE_THRESHOLD) {
            state.openUntilMs = nowMs + COOLDOWN_MS
            state.consecutiveFailures = maxOf(state.consecutiveFailures, FAILURE_THRESHOLD)
        }
    }

    @Synchronized
    fun snapshot(providerId: String, nowMs: Long = monotonicNowMs()): Snapshot {
        val state = states[providerId] ?: State()
        return Snapshot(
            providerId = providerId,
            consecutiveFailures = state.consecutiveFailures,
            circuitOpen = state.openUntilMs > nowMs,
            openUntilMs = state.openUntilMs,
            halfOpenProbeInFlight = state.halfOpenProbeInFlight,
            lastError = state.lastError,
        )
    }

    @Synchronized
    fun reset(providerId: String? = null) {
        if (providerId == null) states.clear() else states.remove(providerId)
    }

    internal fun isTransient(errorCode: String): Boolean = errorCode in setOf(
        "PROVIDER_REMOTE_TIMEOUT",
        "PROVIDER_DNS_FAILED",
        "PROVIDER_TLS_FAILED",
        "PROVIDER_NETWORK_FAILED",
        "PROVIDER_RATE_LIMITED",
        "PROVIDER_UPSTREAM_FAILED",
        "PROVIDER_HTTP_FAILED",
        "PROVIDER_FAILED",
        "META_PROVIDER_FAILED",
    )

    private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L
}
