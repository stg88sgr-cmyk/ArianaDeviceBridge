package de.snowworks.ariana.orchestration

import de.snowworks.ariana.memory.DefaultX88MemoryBridge
import de.snowworks.ariana.memory.ImportedMemory
import de.snowworks.ariana.memory.X88MemoryItem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class X88MemoryCapabilityAdapterTest {
    @Test
    fun catalogExposesLocalMemoryBackend() {
        val catalog = X88MemoryBackendCatalog(
            listOf(X88MemoryCapabilityAdapter("memory-local", DefaultX88MemoryBridge()))
        )

        val backend = catalog.preferred()

        assertNotNull(backend)
        assertEquals("memory-local", backend.descriptor.id)
        assertEquals(X88Capability.LOCAL_MEMORY, backend.descriptor.capability)
        assertTrue(backend.descriptor.local)
    }

    @Test
    fun adapterDelegatesImportAndDuplicatePolicyToBridge() {
        val adapter = X88MemoryCapabilityAdapter("memory-local", DefaultX88MemoryBridge())
        val input = ImportedMemory(
            content = "  hello   X88  ",
            source = X88MemoryItem.Source.ARIANA,
            capturedAtEpochMs = 1L,
        )

        val first = adapter.import(input)
        val second = adapter.import(input)

        assertNotNull(first)
        assertEquals("hello X88", first.content)
        assertNull(second)
    }

    @Test
    fun catalogRegistrationIsImmutable() {
        val first = X88MemoryCapabilityAdapter("memory-a", DefaultX88MemoryBridge())
        val second = X88MemoryCapabilityAdapter("memory-b", DefaultX88MemoryBridge())
        val original = X88MemoryBackendCatalog(listOf(first))
        val expanded = original.register(second)

        assertEquals(listOf("memory-a"), original.backends().map { it.descriptor.id })
        assertEquals(listOf("memory-a", "memory-b"), expanded.backends().map { it.descriptor.id })

        val removed = expanded.unregister("memory-a")
        assertEquals(listOf("memory-b"), removed.backends().map { it.descriptor.id })
    }

    @Test
    fun controlPlaneRoutesMemoryTaskToLocalMemoryCapability() {
        val plane = X88ControlPlane(setOf(X88Capability.LOCAL_MEMORY))
        val route = plane.route(
            X88Task(X88TaskKind.MEMORY, "remember this"),
            de.snowworks.ariana.neuro.ResonanceState(),
        )

        assertEquals(X88Capability.LOCAL_MEMORY, route.capability)
    }
}
