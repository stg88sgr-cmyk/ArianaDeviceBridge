package de.snowworks.ariana.system

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X88VersionTest {

    @Test
    fun versionOrdering_usesSemanticNumbers() {
        assertTrue(X88Version(1, 1, 0) > X88Version(1, 0, 9))
        assertTrue(X88Version(2, 0, 0) > X88Version(1, 99, 99))
    }

    @Test
    fun displayName_keepsX88IndependentFromPlatformBranding() {
        assertEquals("X88 UI 1.0", X88Version(1, 0, 0).displayName())
        assertEquals("X88 UI 1.0 Dev", X88Version(1, 0, 0, X88UpdateChannel.DEV).displayName())
    }
}
