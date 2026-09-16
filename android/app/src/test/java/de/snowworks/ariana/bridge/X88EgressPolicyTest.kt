package de.snowworks.ariana.bridge

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class X88EgressPolicyTest {

    @Test
    fun metaExecution_allowsMetaHttps443() {
        val decision = X88EgressPolicy.evaluate(
            actor = X88EgressPolicy.Actor.LUNA_XXY,
            purpose = X88EgressPolicy.Purpose.META_EXECUTION,
            endpoint = "https://api.meta.ai/v1/chat/completions",
        )

        assertTrue(decision.allowed)
    }

    @Test
    fun metaExecution_blocksUnknownHost() {
        val decision = X88EgressPolicy.evaluate(
            actor = X88EgressPolicy.Actor.LUNA_XXY,
            purpose = X88EgressPolicy.Purpose.META_EXECUTION,
            endpoint = "https://example.com/v1/chat/completions",
        )

        assertFalse(decision.allowed)
    }

    @Test
    fun metaExecution_blocksPlainHttp() {
        val decision = X88EgressPolicy.evaluate(
            actor = X88EgressPolicy.Actor.LUNA_XXY,
            purpose = X88EgressPolicy.Purpose.META_EXECUTION,
            endpoint = "http://api.meta.ai/v1/chat/completions",
        )

        assertFalse(decision.allowed)
    }

    @Test
    fun localBridge_allowsLoopbackOnly() {
        val allowed = X88EgressPolicy.evaluate(
            actor = X88EgressPolicy.Actor.LUNA_XXY,
            purpose = X88EgressPolicy.Purpose.LOCAL_BRIDGE,
            endpoint = "http://127.0.0.1:8765/action",
        )
        val blocked = X88EgressPolicy.evaluate(
            actor = X88EgressPolicy.Actor.LUNA_XXY,
            purpose = X88EgressPolicy.Purpose.LOCAL_BRIDGE,
            endpoint = "http://192.168.1.20:8765/action",
        )

        assertTrue(allowed.allowed)
        assertFalse(blocked.allowed)
    }
}
