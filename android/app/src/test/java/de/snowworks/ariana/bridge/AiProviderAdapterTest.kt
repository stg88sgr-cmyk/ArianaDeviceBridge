package de.snowworks.ariana.bridge

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiProviderAdapterTest {

    @After
    fun tearDown() {
        DialogueRouter.unregister()
    }

    @Test
    fun registersAdapterThroughProviderNeutralBoundary() {
        val adapter = object : AiProviderAdapter {
            override val id = "test-adapter"
            override val timeoutMs = DialogueRouter.PROVIDER_TIMEOUT_MS
            override fun generate(text: String) = "adapter:$text"
        }

        assertTrue(DialogueRouter.register(adapter))

        val outcome = DialogueRouter.generate("Hallo")

        assertTrue(outcome.ok)
        assertEquals("test-adapter", outcome.providerId)
        assertEquals("adapter:Hallo", outcome.reply)
    }
}
