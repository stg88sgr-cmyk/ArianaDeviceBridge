package de.snowworks.ariana.system

/**
 * Centralizes backend selection so privileged execution is fail-closed.
 */
class X88TrustedBackendSelector(
    private val resolver: X88BackendResolver = X88BackendResolver(),
) {
    fun select(
        privilegedAttestation: X88PrivilegedAttestation?,
        appBinderAvailable: Boolean,
    ): X88BackendMode {
        val privilegedAvailable = privilegedAttestation?.let {
            it.trusted && it.isStructurallyValid()
        } == true

        return resolver.resolve(
            privilegedSystemAvailable = privilegedAvailable,
            appBinderAvailable = appBinderAvailable,
        )
    }
}
