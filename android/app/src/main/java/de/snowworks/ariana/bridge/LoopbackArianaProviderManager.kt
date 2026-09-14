package de.snowworks.ariana.bridge

/** Prefer the already-running Termux Ariana Core when reachable on loopback. */
object LoopbackArianaProviderManager {
    const val PROVIDER_ID = "local-ai:ariana-core"

    @Synchronized
    fun activateIfAvailable(): Boolean {
        val provider = LoopbackArianaProvider()
        if (!provider.isHealthy()) {
            if (DialogueRouter.providerId() == PROVIDER_ID) {
                DialogueRouter.unregister()
            }
            return false
        }
        return DialogueRouter.register(
            providerId = PROVIDER_ID,
            timeoutMs = DialogueRouter.LOCAL_PROVIDER_TIMEOUT_MS,
        ) { text -> provider.generate(text) }
    }

    fun isActive(): Boolean = DialogueRouter.providerId() == PROVIDER_ID
}
