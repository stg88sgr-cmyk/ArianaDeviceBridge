package de.snowworks.ariana.bridge

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DialogueRouterTest {

    @After
    fun tearDown() {
        DialogueRouter.unregister()
    }

    @Test
    fun reportsUnavailableProvider() {
        DialogueRouter.unregister()

        val outcome = DialogueRouter.generate("Hallo Ariana")

        assertFalse(outcome.ok)
        assertEquals("PROVIDER_UNAVAILABLE", outcome.error)
    }

    @Test
    fun sanitizesInputAndReply() {
        var received = ""
        assertTrue(
            DialogueRouter.register("test-local") { text ->
                received = text
                "  Antwort\n   lokal  "
            },
        )

        val outcome = DialogueRouter.generate("  Hallo\n\tAriana  ")

        assertTrue(outcome.ok)
        assertEquals("test-local", outcome.providerId)
        assertEquals("Hallo Ariana", received)
        assertEquals("Antwort lokal", outcome.reply)
    }

    @Test
    fun preservesTypedProviderErrorCode() {
        assertTrue(
            DialogueRouter.register("test-local") {
                throw DialogueRouter.ProviderException("ARIANA_CORE_UNAVAILABLE")
            },
        )

        val outcome = DialogueRouter.generate("Test")

        assertFalse(outcome.ok)
        assertEquals("ARIANA_CORE_UNAVAILABLE", outcome.error)
    }

    @Test
    fun rejectsEmptyProviderReply() {
        assertTrue(DialogueRouter.register("test-local") { "   " })

        val outcome = DialogueRouter.generate("Test")

        assertFalse(outcome.ok)
        assertEquals("EMPTY_REPLY", outcome.error)
    }

    @Test
    fun rejectsBlankInputBeforeProviderCall() {
        var called = false
        assertTrue(
            DialogueRouter.register("test-local") {
                called = true
                "unused"
            },
        )

        val outcome = DialogueRouter.generate(" \n\t ")

        assertFalse(outcome.ok)
        assertEquals("INVALID_INPUT", outcome.error)
        assertFalse(called)
    }
    @Test
    fun memoryAwareGenerationIncludesRelevantX88Context() {
        val store = FakeMemoryStore()
        val access = de.snowworks.ariana.memory.ArianaX88MemoryAccess(
            de.snowworks.ariana.memory.X88MemoryRepository(
                de.snowworks.ariana.memory.DefaultX88MemoryBridge(),
                store
            )
        )
        access.remember(
            de.snowworks.ariana.memory.ImportedMemory(
                content = "X88 persistent context",
                source = de.snowworks.ariana.memory.X88MemoryItem.Source.ARIANA,
                capturedAtEpochMs = 1L,
                project = "Ariana/X88"
            )
        )

        var received = ""
        assertTrue(DialogueRouter.register("test-local") { text ->
            received = text
            "Antwort"
        })

        val outcome = DialogueRouter.generateWithMemory("Hallo Ariana", access, "Ariana/X88")

        assertTrue(outcome.ok)
        assertTrue(received.contains("X88 persistent context"))
        assertTrue(received.contains("Hallo Ariana"))
    }

    private class FakeMemoryStore : de.snowworks.ariana.memory.X88MemoryStore {
        private var items: List<de.snowworks.ariana.memory.X88MemoryItem> = emptyList()
        override fun load() = items
        override fun save(items: List<de.snowworks.ariana.memory.X88MemoryItem>) { this.items = items }
        override fun clear() { items = emptyList() }
    }

}
