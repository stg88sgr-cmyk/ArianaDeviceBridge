package de.snowworks.ariana.orchestration

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class X88BackendRegistryTest {
    @Test
    fun keepsMultipleBackendsPerCapabilityInRegistrationOrder() {
        val registry = X88BackendRegistry(
            listOf(
                X88Backend("local-dialogue", X88Capability.LOCAL_DIALOGUE, local = true),
                X88Backend("cloud-dialogue", X88Capability.LOCAL_DIALOGUE, local = false),
                X88Backend("local-memory", X88Capability.LOCAL_MEMORY, local = true),
            ),
        )

        assertEquals(
            listOf("local-dialogue", "cloud-dialogue"),
            registry.backendsFor(X88Capability.LOCAL_DIALOGUE).map { it.id },
        )
        assertEquals("local-dialogue", registry.preferredBackend(X88Capability.LOCAL_DIALOGUE)?.id)
        assertEquals(setOf(X88Capability.LOCAL_DIALOGUE, X88Capability.LOCAL_MEMORY), registry.localCapabilities())
        assertTrue(registry.ids().contains("cloud-dialogue"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsBlankBackendId() {
        X88Backend("", X88Capability.LOCAL_DIALOGUE, local = true)
    }

    @Test
    fun registerReturnsNewRegistryWithoutMutatingOriginal() {
        val original = X88BackendRegistry()
        val updated = original.register(
            X88Backend("device-bridge", X88Capability.DEVICE_BRIDGE, local = true),
        )

        assertTrue(original.backendFor(X88Capability.DEVICE_BRIDGE) == null)
        assertEquals("device-bridge", updated.backendFor(X88Capability.DEVICE_BRIDGE)?.id)
    }
}
