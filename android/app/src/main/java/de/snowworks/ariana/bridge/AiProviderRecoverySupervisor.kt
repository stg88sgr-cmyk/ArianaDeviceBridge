package de.snowworks.ariana.bridge

import android.content.Context
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Process-local, rate-limited automatic recovery for configured cloud providers.
 *
 * No wake lock, foreground service or new Android permission is used. The
 * supervisor only runs while Ariana's app process is alive. It never changes
 * DialogueRouter's active provider and only probes providers that are already
 * RECOVERY READY according to AiProviderHealth.
 */
object AiProviderRecoverySupervisor {
    const val TICK_SECONDS = 30L
    const val MIN_AUTO_PROBE_INTERVAL_MS = 5 * 60_000L

    private const val PREFS = "x88_ai_recovery_supervisor"
    private const val KEY_CLAUDE_LAST = "claude_last_auto_probe_ms"
    private const val KEY_META_LAST = "meta_last_auto_probe_ms"

    private val started = AtomicBoolean(false)
    @Volatile private var executor: ScheduledExecutorService? = null

    fun start(context: Context) {
        if (!started.compareAndSet(false, true)) return
        val app = context.applicationContext
        executor = Executors.newSingleThreadScheduledExecutor { runnable ->
            Thread(runnable, "ArianaAiRecovery").apply { isDaemon = true }
        }.also { scheduler ->
            scheduler.scheduleWithFixedDelay(
                { runCatching { tick(app) } },
                TICK_SECONDS,
                TICK_SECONDS,
                TimeUnit.SECONDS,
            )
        }
    }

    fun stop() {
        started.set(false)
        executor?.shutdownNow()
        executor = null
    }

    internal fun tick(context: Context, nowWallMs: Long = System.currentTimeMillis()) {
        val app = context.applicationContext
        val health = AiHealthReporter.snapshot(app)
        maybeProbe(app, CloudProviderRegistry.Slot.CLAUDE, health.claude, nowWallMs)
        maybeProbe(app, CloudProviderRegistry.Slot.META, health.meta, nowWallMs)
    }

    private fun maybeProbe(
        context: Context,
        slot: CloudProviderRegistry.Slot,
        status: AiHealthReporter.ProviderStatus,
        nowWallMs: Long,
    ) {
        if (!status.configured || !status.recoveryProbeReady || status.halfOpenProbeInFlight) return

        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = when (slot) {
            CloudProviderRegistry.Slot.CLAUDE -> KEY_CLAUDE_LAST
            CloudProviderRegistry.Slot.META -> KEY_META_LAST
        }
        val last = prefs.getLong(key, 0L)
        if (!isDue(last, nowWallMs)) return

        // Persist before network I/O so process death cannot create an immediate retry storm.
        prefs.edit().putLong(key, nowWallMs).apply()
        AiProviderRecoveryProbe.run(context, slot)
    }

    internal fun isDue(lastAttemptWallMs: Long, nowWallMs: Long): Boolean {
        if (lastAttemptWallMs <= 0L) return true
        if (nowWallMs < lastAttemptWallMs) return true
        return nowWallMs - lastAttemptWallMs >= MIN_AUTO_PROBE_INTERVAL_MS
    }
}
