package de.snowworks.ariana.system

import android.content.Context

/**
 * Process-wide access point for the canonical X-88 runtime.
 *
 * Only preference-level state is restored from disk. Runtime authority such as
 * master enablement, active session ids, emergency state and quarantine state
 * remains process-local and starts fail-closed after process death.
 */
object X88Runtime {
    @Volatile
    private var instance: X88SystemGateway? = null

    @Volatile
    private var genesisInstance: X88GenesisCode? = null

    fun gateway(context: Context): X88SystemGateway {
        instance?.let { return it }
        return synchronized(this) {
            instance ?: PersistingX88SystemGateway(
                delegate = InProcessX88SystemGateway(),
                store = SharedPreferencesX88StateStore(context.applicationContext),
            ).also { instance = it }
        }
    }

    /**
     * Returns the process-wide Genesis reader bound to the exact same canonical
     * gateway used by UI, bridge and device execution. Genesis is observational
     * and never grants runtime authority.
     */
    fun genesis(context: Context): X88GenesisCode {
        genesisInstance?.let { return it }
        return synchronized(this) {
            genesisInstance ?: X88GenesisCode(gateway(context)).also {
                genesisInstance = it
            }
        }
    }
}
