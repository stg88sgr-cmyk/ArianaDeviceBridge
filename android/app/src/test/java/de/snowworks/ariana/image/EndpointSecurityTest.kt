package de.snowworks.ariana.image

import de.snowworks.ariana.image.generator.EndpointSecurity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EndpointSecurityTest {
    @Test fun allowsLoopbackOnly() {
        assertEquals("127.0.0.1", EndpointSecurity.validate("http://127.0.0.1:8188").host)
        assertEquals("localhost", EndpointSecurity.validate("http://localhost").host)
        assertEquals("::1", EndpointSecurity.validate("http://[::1]:8188").host)
        assertEquals(80, EndpointSecurity.validate("http://localhost").port)
        assertEquals(443, EndpointSecurity.validate("https://localhost").port)
    }

    @Test fun rejectsAuthorityTricksAndRemoteHosts() {
        listOf(
            "http://localhost.evil.com",
            "http://127.0.0.1.evil.com",
            "http://a@localhost",
            "http://a@127.0.0.1",
            "ftp://localhost",
            "http:///path",
            "not a uri",
            "http://localhost:0",
        ).forEach { endpoint ->
            assertTrue("Expected rejection: $endpoint", runCatching { EndpointSecurity.validate(endpoint) }.isFailure)
        }
        assertTrue(EndpointSecurity.isAllowedHost("LOCALHOST"))
        assertTrue(EndpointSecurity.isAllowedHost("[::1]"))
        assertFalse(EndpointSecurity.isAllowedHost("localhost.evil.com"))
        assertFalse(EndpointSecurity.isAllowedHost(null))
    }
}
