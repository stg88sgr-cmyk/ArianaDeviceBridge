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
    fun registryPrefersLocalAdapterOverRemoteFallback() {
        val remote = object : AiProviderAdapter {
            override val id = "https-ai:test"
            override val timeoutMs = DialogueRouter.PROVIDER_TIMEOUT_MS
            override fun generate(text: String) = "remote"
        }
        val local = object : AiProviderAdapter {
            override val id = "local-ai:test"
            override val timeoutMs = DialogueRouter.LOCAL_PROVIDER_TIMEOUT_MS
            override fun generate(text: String) = "local"
        }

        assertTrue(DialogueRouter.registerAdapter(remote, local = false))
        assertTrue(DialogueRouter.registerAdapter(local, local = true))

        val outcome = DialogueRouter.generate("Test")

        assertTrue(outcome.ok)
        assertEquals("local-ai:test", outcome.providerId)
        assertEquals("local", outcome.reply)
        assertEquals("local-ai:test", DialogueRouter.registry().preferredBackend(
            de.snowworks.ariana.orchestration.X88Capability.LOCAL_DIALOGUE,
        )?.id)

        DialogueRouter.unregister("local-ai:test")
        val fallback = DialogueRouter.generate("Test")
        assertTrue(fallback.ok)
        assertEquals("https-ai:test", fallback.providerId)
        assertEquals("remote", fallback.reply)
        DialogueRouter.unregister("https-ai:test")
    }
}
