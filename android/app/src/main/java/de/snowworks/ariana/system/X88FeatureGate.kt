package de.snowworks.ariana.system

import de.snowworks.ariana.Feature

/**
 * Connects user-visible device features to the canonical X-88 capability gate.
 *
 * Android runtime permissions and per-session confirmation remain separate.
 * This gate only answers whether X-88's own runtime policy currently allows
 * execution after an explicit feature-start request has authorized the
 * corresponding capability.
 */
class X88FeatureGate(
    private val gateway: X88SystemGateway,
) {
    fun authorizeForExplicitStart(feature: Feature): Boolean {
        val before = gateway.state()
        if (!before.masterEnabled ||
            before.emergencyStopActive ||
            before.quarantineActive ||
            before.security.gate12CircuitBreaker != GateStatus.GREEN
        ) {
            return false
        }

        val capability = feature.toX88Capability()
        gateway.enable(capability)
        return gateway.state().canExecute(capability)
    }

    fun isAuthorized(feature: Feature): Boolean {
        val capability = feature.toX88Capability()
        return gateway.state().canExecute(capability)
    }

    fun revoke(feature: Feature): X88CoreState =
        gateway.disable(feature.toX88Capability())
}

fun Feature.toX88Capability(): X88Capability = when (this) {
    Feature.CAMERA -> X88Capability.CAMERA
    Feature.MICROPHONE -> X88Capability.MICROPHONE
    Feature.SCREEN -> X88Capability.SCREEN_CAPTURE
    Feature.FILES -> X88Capability.FILES
    Feature.NOTIFY_SEND, Feature.NOTIFY_READ -> X88Capability.NOTIFICATIONS
    Feature.LOCATION -> X88Capability.LOCATION
    Feature.BLUETOOTH -> X88Capability.BLUETOOTH
}
