package de.snowworks.ariana.system

/**
 * Chooses the strongest available X88 backend without silently escalating.
 * Availability must be supplied by a trusted platform probe.
 */
class X88BackendResolver {
    fun resolve(
        privilegedSystemAvailable: Boolean,
        appBinderAvailable: Boolean,
    ): X88BackendMode = when {
        privilegedSystemAvailable -> X88BackendMode.PRIVILEGED_SYSTEM
        appBinderAvailable -> X88BackendMode.APP_BINDER
        else -> X88BackendMode.IN_PROCESS
    }
}
