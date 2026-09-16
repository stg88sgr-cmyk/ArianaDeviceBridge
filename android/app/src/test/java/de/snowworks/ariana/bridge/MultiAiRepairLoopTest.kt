package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAiRepairLoopTest {
    @Test
    fun parsesPassReviewWithoutCandidate() {
        val parsed = MultiAiRepairLoop.parseReview("STATUS: PASS ISSUES: Keine kritischen Fehler. TESTS: Kompiliert und Randfall geprueft. CANDIDATE: NONE")
        assertNotNull(parsed)
        assertEquals(MultiAiRepairLoop.Status.PASS, parsed?.status)
        assertNull(parsed?.candidate)
    }

    @Test
    fun parsesFixReviewWithCandidate() {
        val parsed = MultiAiRepairLoop.parseReview("STATUS: FIX ISSUES: Nullfall fehlt. TESTS: Leere Eingabe und Timeout pruefen. CANDIDATE: Eingabe validieren, Timeout behandeln und Tests ergaenzen.")
        assertNotNull(parsed)
        assertEquals(MultiAiRepairLoop.Status.FIX, parsed?.status)
        assertTrue(parsed?.candidate?.contains("Timeout") == true)
    }

    @Test
    fun rejectsFixWithoutCandidate() {
        val parsed = MultiAiRepairLoop.parseReview("STATUS: FIX ISSUES: Fehler vorhanden. TESTS: Test fehlt. CANDIDATE: NONE")
        assertNull(parsed)
    }

    @Test
    fun promptsStayBounded() {
        val huge = "x".repeat(5000)
        val reviewPrompt = MultiAiRepairLoop.buildReviewPrompt(huge, huge, 1)
        val revisionPrompt = MultiAiRepairLoop.buildRevisionPrompt(
            task = huge,
            draft = huge,
            review = MultiAiRepairLoop.Review(
                status = MultiAiRepairLoop.Status.FIX,
                issues = huge,
                tests = huge,
                candidate = huge,
            ),
            round = 2,
        )
        assertTrue(reviewPrompt.length <= DialogueRouter.MAX_INPUT_CHARS)
        assertTrue(revisionPrompt.length <= DialogueRouter.MAX_INPUT_CHARS)
    }
}
