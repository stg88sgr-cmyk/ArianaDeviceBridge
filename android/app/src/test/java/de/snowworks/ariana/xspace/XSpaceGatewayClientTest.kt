package de.snowworks.ariana.xspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class XSpaceGatewayClientTest {

    @Test
    fun acceptsCanonicalXSpaceUrls() {
        assertTrue(XSpaceGatewayClient.isValidSpaceUrl("https://x.com/i/spaces/1YqKDkWmQpVGV"))
        assertTrue(XSpaceGatewayClient.isValidSpaceUrl("https://www.x.com/i/spaces/abcd_1234"))
        assertTrue(XSpaceGatewayClient.isValidSpaceUrl("https://twitter.com/i/spaces/AbCd-1234?ref=share"))
    }

    @Test
    fun rejectsNonSpaceAndRemoteControlUrls() {
        assertFalse(XSpaceGatewayClient.isValidSpaceUrl("http://x.com/i/spaces/abcd1234"))
        assertFalse(XSpaceGatewayClient.isValidSpaceUrl("https://example.com/i/spaces/abcd1234"))
        assertFalse(XSpaceGatewayClient.isValidSpaceUrl("https://x.com/home"))
        assertFalse(XSpaceGatewayClient.isValidSpaceUrl("https://x.com/i/spaces/a"))
    }

    @Test
    fun speakTextIsTrimmedAndBounded() {
        assertEquals("hello", XSpaceGatewayClient.normalizeSpeakText("  hello  "))
        assertEquals("x".repeat(2000), XSpaceGatewayClient.normalizeSpeakText("x".repeat(2500)))
        assertEquals("", XSpaceGatewayClient.normalizeSpeakText("   "))
    }
}
