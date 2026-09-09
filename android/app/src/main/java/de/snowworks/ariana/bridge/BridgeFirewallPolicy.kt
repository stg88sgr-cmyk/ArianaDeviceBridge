package de.snowworks.ariana.bridge

import java.net.InetAddress

/**
 * Fail-closed network policy for the on-device Ariana bridge.
 *
 * The bridge is intentionally reachable only from the same Android device.
 * Wi-Fi, LAN, VPN and wildcard bindings are not permitted here. Keeping these
 * values in one policy object makes accidental exposure easier to detect in CI.
 */
object BridgeFirewallPolicy {
    const val LOOPBACK_HOST = "127.0.0.1"
    const val PORT = 8765
    const val MAX_BODY_BYTES = 8 * 1024
    const val MAX_REQUESTS_PER_MINUTE = 60

    fun bindAddress(): InetAddress {
        val address = InetAddress.getByName(LOOPBACK_HOST)
        check(isPermittedBindAddress(address)) {
            "Refusing non-loopback bridge bind: ${address.hostAddress}"
        }
        return address
    }

    fun isPermittedBindAddress(address: InetAddress): Boolean =
        address.isLoopbackAddress && address.hostAddress == LOOPBACK_HOST

    fun isPermittedPeer(address: InetAddress?): Boolean =
        address != null && address.isLoopbackAddress

    fun endpointLabel(): String = "$LOOPBACK_HOST:$PORT"
}
