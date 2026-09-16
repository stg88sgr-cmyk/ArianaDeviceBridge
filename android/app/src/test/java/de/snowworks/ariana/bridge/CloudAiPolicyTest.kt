package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudAiPolicyTest {
    @Test
    fun keepsDevicePathsLocal() {
        val decision = CloudAiPolicy.evaluate(
            "Pruefe bitte /data/user/0/de.snowworks.app/files/private.db",
        )

        assertEquals(CloudAiPolicy.Disposition.LOCAL_ONLY, decision.disposition)
        assertEquals(CloudAiPolicy.Classification.DEVICE_SENSITIVE, decision.classification)
        assertEquals("CLOUD_POLICY_DEVICE_SENSITIVE_LOCAL_ONLY", decision.reason)
        assertTrue(decision.text.isEmpty())
    }

    @Test
    fun keepsPreciseCoordinatesLocal() {
        val decision = CloudAiPolicy.evaluate("latitude=52.123456 longitude=13.654321")

        assertEquals(CloudAiPolicy.Disposition.LOCAL_ONLY, decision.disposition)
        assertEquals(CloudAiPolicy.Classification.DEVICE_SENSITIVE, decision.classification)
    }

    @Test
    fun redactsSecretsAndPersonalIdentifiers() {
        val secret = "sk-proj-ABCDEFGHIJKLMNOPQRSTUVWXYZ123456"
        val email = "person@example.com"
        val decision = CloudAiPolicy.evaluate(
            "api_key=$secret contact=$email",
        )

        assertEquals(CloudAiPolicy.Disposition.REDACTED, decision.disposition)
        assertEquals(CloudAiPolicy.Classification.SECRET, decision.classification)
        assertTrue(decision.redactions >= 2)
        assertFalse(decision.text.contains(secret))
        assertFalse(decision.text.contains(email))
        assertTrue(decision.text.contains("REDACTED"))
    }

    @Test
    fun allowsOrdinaryCodeAfterClassification() {
        val decision = CloudAiPolicy.evaluate(
            "package demo\nclass Example { fun run(): String = \"ok\" }",
        )

        assertEquals(CloudAiPolicy.Disposition.ALLOW, decision.disposition)
        assertEquals(CloudAiPolicy.Classification.CODE, decision.classification)
        assertTrue(decision.text.contains("class Example"))
    }

    @Test
    fun redactsBearerTokenWithoutBlockingSafeContext() {
        val decision = CloudAiPolicy.evaluate(
            "curl -H 'Authorization: Bearer abcdefghijklmnopqrstuvwxyz123456' https://example.invalid",
        )

        assertEquals(CloudAiPolicy.Disposition.REDACTED, decision.disposition)
        assertEquals(CloudAiPolicy.Classification.SECRET, decision.classification)
        assertFalse(decision.text.contains("abcdefghijklmnopqrstuvwxyz123456"))
    }
}
