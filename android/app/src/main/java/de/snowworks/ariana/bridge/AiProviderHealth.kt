package de.snowworks.ariana.bridge

import android.content.Context

/**
 * Small circuit breaker for remote AI providers.
 *
 * Only transient transport/upstream failures count against health. Configuration,
 * authentication and request-validation errors stay visible but do not poison
 * provider health. After the cooldown, exactly one half-open probe is allowed.
 *
 * Recovery metadata is persisted after initialize(context), so process death does
 * not erase failure counters or an active cooldown. Half-open in-flight state is
 * intentionally never persisted because no network request survives process death.
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
        val recoveryProbeReady: Boolean,
        val cooldownRemainingMs: Long,
        val lastError: String? = null,
    )

    private data class State(
        var consecutiveFailures: Int = 0,
        var openUntilMs: Long = 0L,
        var halfOpenProbeInFlight: Boolean = false,
        var lastError: String? = null,
    )

    private val states = linkedMapOf<String, State>()
    @Volatile private var persistence: AiProviderHealthPersistence? = null

    @Synchronized
    fun initialize(context: Context) {
        val store = AiProviderHealthPersistence(context.applicationContext)
        persistence = store
        states.clear()
        val monotonicNow = monotonicNowMs()
        val wallNow = System.currentTimeMillis()
        store.loadAll().forEach { record ->
            val remaining = (record.wallOpenUntilMs - wallNow).coerceAtLeast(0L)
            val restoredOpenUntil = when {
                record.wallOpenUntilMs <= 0L -> 0L
                remaining > 0L -> monotonicNow + remaining
                else -> monotonicNow
            }
            states[record.providerId] = State(
                consecutiveFailures = record.consecutiveFailures,
                openUntilMs = restoredOpenUntil,
                halfOpenProbeInFlight = false,
                lastError = record.lastError,
            )
        }
    }

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
        persistence?.remove(providerId)
    }

    @Synchronized
    fun recordFailure(providerId: String, errorCode: String, nowMs: Long = monotonicNowMs()) {
        if (!isTransient(errorCode)) {
            states.remove(providerId)
            persistence?.remove(providerId)
            return
        }

        val state = states.getOrPut(providerId) { State() }
        val failedHalfOpenProbe = state.halfOpenProbeInFlight
        state.halfOpenProbeInFlight = false
        state.lastError = errorCode
        state.consecutiveFailures += 1

        if (failedHalfOpenProbe || state.consecutiveFailures >= FAILURE_THRESHOLD) {
            state.openUntilMs = nowMs + COOLDOWN_MS
            state.consecutiveFailures = maxOf(state.consecutiveFailures, FAILURE_THRESHOLD)
        }
        persist(providerId, state, nowMs)
    }

    @Synchronized
    fun snapshot(providerId: String, nowMs: Long = monotonicNowMs()): Snapshot {
        val state = states[providerId] ?: State()
        val circuitOpen = state.openUntilMs > nowMs
        val recoveryProbeReady = state.openUntilMs > 0L && !circuitOpen && !state.halfOpenProbeInFlight
        return Snapshot(
            providerId = providerId,
            consecutiveFailures = state.consecutiveFailures,
            circuitOpen = circuitOpen,
            openUntilMs = state.openUntilMs,
            halfOpenProbeInFlight = state.halfOpenProbeInFlight,
            recoveryProbeReady = recoveryProbeReady,
            cooldownRemainingMs = if (circuitOpen) state.openUntilMs - nowMs else 0L,
            lastError = state.lastError,
        )
    }

    @Synchronized
    fun reset(providerId: String? = null) {
        if (providerId == null) {
            states.clear()
            persistence?.clear()
        } else {
            states.remove(providerId)
            persistence?.remove(providerId)
        }
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
        "CLAUDE_PROVIDER_FAILED",
    )

    private fun persist(providerId: String, state: State, nowMs: Long) {
        val store = persistence ?: return
        val remaining = (state.openUntilMs - nowMs).coerceAtLeast(0L)
        val wallOpenUntil = if (state.openUntilMs > 0L) System.currentTimeMillis() + remaining else 0L
        store.save(
            AiProviderHealthPersistence.Record(
                providerId = providerId,
                consecutiveFailures = state.consecutiveFailures,
                wallOpenUntilMs = wallOpenUntil,
                lastError = state.lastError,
            ),
        )
    }

    private fun monotonicNowMs(): Long = System.nanoTime() / 1_000_000L
}
