package de.snowworks.ariana.bridge

import java.net.InetAddress
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BridgeFirewallPolicyTest {

    @Test
    fun exactIpv4LoopbackBindIsAllowed() {
        assertTrue(
            BridgeFirewallPolicy.isPermittedBindAddress(
                InetAddress.getByName("127.0.0.1"),
            ),
        )
    }

    @Test
    fun wildcardBindIsRejected() {
        assertFalse(
            BridgeFirewallPolicy.isPermittedBindAddress(
                InetAddress.getByName("0.0.0.0"),
            ),
        )
    }

    @Test
    fun lanBindIsRejected() {
        assertFalse(
            BridgeFirewallPolicy.isPermittedBindAddress(
                InetAddress.getByName("192.168.1.20"),
            ),
        )
    }

    @Test
    fun onlyLoopbackPeersAreAccepted() {
        assertTrue(BridgeFirewallPolicy.isPermittedPeer(InetAddress.getByName("127.0.0.1")))
        assertTrue(BridgeFirewallPolicy.isPermittedPeer(InetAddress.getByName("::1")))
        assertFalse(BridgeFirewallPolicy.isPermittedPeer(InetAddress.getByName("10.0.0.5")))
        assertFalse(BridgeFirewallPolicy.isPermittedPeer(null))
    }
}
