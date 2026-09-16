package de.snowworks.ariana.universal.v2

import android.content.Context
import de.snowworks.ariana.ArianaGate

object ArianaUniversalRuntimeV2 {
    val stateHolder = ArianaRuntimeStateHolder()
    val taskQueue = ArianaTaskQueue()
    val decisionEngine = ArianaRuntimeDecisionEngine()

    @Volatile
    private var initialized = false

    fun initialize(context: Context) {
        if (initialized) return
        synchronized(this) {
            if (initialized) return
            val gate = ArianaGate(context.applicationContext)
            stateHolder.update {
                it.copy(
                    masterEnabled = gate.isMasterEnabled,
                    emergencyStopActive = gate.isBlocked,
                    mode = if (gate.isBlocked) ArianaMode.GUARDIAN else ArianaMode.HOME,
                )
            }
            initialized = true
        }
    }
}
