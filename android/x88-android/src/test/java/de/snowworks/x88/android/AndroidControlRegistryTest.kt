package de.snowworks.x88.android

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AndroidControlRegistryTest {
    @Test
    fun unknownActionsAreDeniedByDefault() {
        val registry = AndroidControlRegistry()
        assertFalse(registry.isAllowed(AndroidAction.READ_DEVICE_INFO))
        assertFalse(registry.isAllowed(AndroidAction.LIST_APPS))
    }

    @Test
    fun explicitlyAllowedActionPassesPolicy() {
        val registry = AndroidControlRegistry(setOf(AndroidAction.READ_DEVICE_INFO))
        assertTrue(registry.isAllowed(AndroidAction.READ_DEVICE_INFO))
        assertFalse(registry.isAllowed(AndroidAction.OPEN_APP))
    }
}
