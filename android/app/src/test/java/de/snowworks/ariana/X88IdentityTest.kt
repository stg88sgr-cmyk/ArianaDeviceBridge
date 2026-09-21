package de.snowworks.ariana

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X88IdentityTest {
    @Test
    fun identityIsApplicationOwnedAndStable() {
        val identity = X88Identity.current
        assertEquals("ARIANA-X88", identity.projectId)
        assertEquals("X-88", identity.identityId)
        assertEquals("ARIANA", identity.applicationName)
        assertTrue(identity.continuityContract.endsWith("docs/X88-CONTINUITY.md"))
    }
}
