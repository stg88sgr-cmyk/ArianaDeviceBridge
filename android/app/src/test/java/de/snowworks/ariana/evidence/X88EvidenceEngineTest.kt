package de.snowworks.ariana.evidence

import org.junit.Assert.*
import org.junit.Test

class X88EvidenceEngineTest {
    @Test fun verifiedStageRequiresEvidence() {
        val e = X88EvidenceEngine(clock = { 1234L })
        e.record(37, EvidenceKind.TEST, EvidenceStatus.VERIFIED,
            "snapshot-recovery", "Runtime state restored.", "InneresWerdenPersistenceTest")
        assertTrue(e.report(37).verified)
    }

    @Test fun failedEvidencePreventsVerification() {
        val e = X88EvidenceEngine()
        e.record(37, EvidenceKind.BUILD, EvidenceStatus.VERIFIED,
            "compile", "Compilation completed.", "CI")
        e.record(37, EvidenceKind.TEST, EvidenceStatus.FAILED,
            "runtime", "Runtime test failed.", "CI")
        assertFalse(e.report(37).verified)
    }

    @Test fun artifactHashIsStable() {
        val e = X88EvidenceEngine(clock = { 1L })
        val a = e.record(37, EvidenceKind.BUILD, EvidenceStatus.VERIFIED,
            "apk", "Artifact captured.", "CI", "apk".toByteArray())
        val b = e.record(37, EvidenceKind.BUILD, EvidenceStatus.VERIFIED,
            "apk", "Artifact captured.", "CI", "apk".toByteArray())
        assertEquals(a.id, b.id)
        assertEquals(a.artifactSha256, b.artifactSha256)
    }
}
