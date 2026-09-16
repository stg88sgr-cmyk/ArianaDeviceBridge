package de.snowworks.ariana.xspace

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class XSpacePayloadFingerprintTest {

    @Test
    fun sameReviewedSpeechProducesSameFingerprint() {
        val a = XSpacePayloadFingerprint.forValues(
            action = "xspace_speak",
            text = "  Hallo X Space  ",
        )
        val b = XSpacePayloadFingerprint.forValues(
            action = "XSPACE_SPEAK",
            text = "Hallo X Space",
        )
        assertEquals(a, b)
    }

    @Test
    fun changedSpeechIsRejectedByFingerprintComparison() {
        val approved = XSpacePayloadFingerprint.forValues(
            action = "xspace_speak",
            text = "Freigegebener Text",
        )
        val substituted = XSpacePayloadFingerprint.forValues(
            action = "xspace_speak",
            text = "Anderer Text",
        )
        assertNotEquals(approved, substituted)
    }

    @Test
    fun changedSpaceUrlProducesDifferentFingerprint() {
        val approved = XSpacePayloadFingerprint.forValues(
            action = "xspace_join",
            spaceUrl = "https://x.com/i/spaces/AAAA1111",
        )
        val substituted = XSpacePayloadFingerprint.forValues(
            action = "xspace_join",
            spaceUrl = "https://x.com/i/spaces/BBBB2222",
        )
        assertNotEquals(approved, substituted)
    }

    @Test
    fun speechIsCappedExactlyLikeExecutionPath() {
        val overLimit = "a".repeat(2001)
        val capped = "a".repeat(2000)
        val a = XSpacePayloadFingerprint.forValues("xspace_speak", text = overLimit)
        val b = XSpacePayloadFingerprint.forValues("xspace_speak", text = capped)
        assertEquals(a, b)
    }

    @Test(expected = IllegalStateException::class)
    fun unsupportedApprovalActionFailsClosed() {
        XSpacePayloadFingerprint.forValues("xspace_unknown", text = "x")
    }
}
