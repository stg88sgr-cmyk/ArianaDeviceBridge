package de.snowworks.ariana.orchestration

import de.snowworks.ariana.bridge.AiProviderAdapter
import de.snowworks.ariana.memory.DefaultX88MemoryBridge
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class X88BackendBindingsTest {
    @Test
    fun providerAdapterBecomesDialogueBackendDescriptor() {
        val provider = object : AiProviderAdapter {
            override val id = "local-dialogue"
            override val timeoutMs = 1_000L
            override fun generate(text: String) = text
        }

        val backend = provider.asX88Backend(local = true)

        assertEquals("local-dialogue", backend.id)
        assertEquals(X88Capability.LOCAL_DIALOGUE, backend.capability)
        assertTrue(backend.local)
    }

    @Test
    fun memoryBridgeBecomesLocalMemoryBackendDescriptor() {
        val backend = DefaultX88MemoryBridge().asX88Backend()

        assertEquals("x88-memory", backend.id)
        assertEquals(X88Capability.LOCAL_MEMORY, backend.capability)
        assertTrue(backend.local)
    }

    @Test
    fun registryPrefersLocalProviderButKeepsRemoteFallback() {
        val local = X88Backend("local-dialogue", X88Capability.LOCAL_DIALOGUE, true)
        val remote = X88Backend("remote-dialogue", X88Capability.LOCAL_DIALOGUE, false)
        val registry = X88BackendRegistry(listOf(remote, local))

        assertEquals("local-dialogue", registry.preferredBackend(X88Capability.LOCAL_DIALOGUE)?.id)
        assertEquals(2, registry.backendsFor(X88Capability.LOCAL_DIALOGUE).size)
    }
}
