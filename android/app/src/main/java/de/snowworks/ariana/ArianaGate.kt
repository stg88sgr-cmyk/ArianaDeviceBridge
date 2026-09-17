package de.snowworks.ariana

import android.content.Context
import de.snowworks.ariana.system.X88Runtime

/**
 * Master switch and stop-all latch backed by the canonical X-88 runtime.
 * Android permissions remain separate and are never granted by this gate.
 *
 * Runtime authority is intentionally ephemeral: after process death the
 * canonical runtime starts with master disabled and no active emergency or
 * session authority restored from disk.
 */
class ArianaGate(context: Context) {
    private val gateway = X88Runtime.gateway(context.applicationContext)

    val isMasterEnabled: Boolean
        get() = gateway.state().masterEnabled

    val isBlocked: Boolean
        get() = gateway.state().let { state ->
            state.emergencyStopActive || state.quarantineActive
        }

    /**
     * Applies an explicit master-switch decision.
     *
     * Re-enabling after STOP ALL first clears the emergency latch, then performs
     * a separate master-enable transition. Quarantine cannot be cleared here.
     * Returns true only when the requested state was actually reached.
     */
    fun setMasterEnabled(enabled: Boolean): Boolean {
        if (enabled && gateway.state().emergencyStopActive) {
            gateway.clearEmergencyStop()
        }
        gateway.setMasterEnabled(enabled)
        return gateway.state().masterEnabled == enabled
    }

    fun blockAfterStopAll() {
        gateway.emergencyStop()
    }

    fun assertCanUse(): ArianaResult<Unit> {
        if (isBlocked) {
            return ArianaResult.Err(
                ArianaError(
                    ArianaError.Code.BLOCKED,
                    "Alles stoppen ist aktiv. Schalte Ariana-Gerätezugriff wieder ein.",
                ),
            )
        }
        if (!isMasterEnabled) {
            return ArianaResult.Err(
                ArianaError(
                    ArianaError.Code.MASTER_OFF,
                    "Ariana-Gerätezugriff ist aus. Der Schalter ersetzt keine Android-Berechtigung.",
                ),
            )
        }
        return ArianaResult.Ok(Unit)
    }
}
