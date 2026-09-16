package de.snowworks.ariana.bridge

/**
 * Registers the same-device Ariana Core without doing network I/O on Android's UI thread.
 * The actual HTTP call runs later inside DialogueRouter's provider executor.
 */
object LoopbackArianaProviderManager {
    const val PROVIDER_ID = "local-ai:ariana-core"

    @Synchronized
    fun activateIfAvailable(): Boolean {
        if (DialogueRouter.providerId() == PROVIDER_ID) return true
        val provider = LoopbackArianaProvider()
        return DialogueRouter.register(
            providerId = PROVIDER_ID,
            timeoutMs = DialogueRouter.LOCAL_PROVIDER_TIMEOUT_MS,
        ) { text -> provider.generate(text) }
    }

    fun isActive(): Boolean = DialogueRouter.providerId() == PROVIDER_ID
}
