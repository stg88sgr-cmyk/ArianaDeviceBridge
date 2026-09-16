package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MultiAiJudgeTest {
    @Test
    fun parsesMergeVerdictAndFinalReply() {
        val parsed = MultiAiJudge.parse("VERDICT: MERGE RATIONALE: Primary has the safer flow, Meta has the better test. FINAL: Use the safer flow and add the test.")
        assertEquals(MultiAiJudge.Verdict.MERGE, parsed?.verdict)
        assertEquals("Primary has the safer flow, Meta has the better test.", parsed?.rationale)
        assertEquals("Use the safer flow and add the test.", parsed?.finalReply)
    }

    @Test
    fun rejectsMalformedJudgeOutput() {
        assertNull(MultiAiJudge.parse("Both answers look fine."))
        assertNull(MultiAiJudge.parse("VERDICT: PRIMARY FINAL: answer"))
        assertNull(MultiAiJudge.parse("VERDICT: UNKNOWN RATIONALE: nope FINAL: answer"))
    }

    @Test
    fun judgePromptFitsDialogueLimitAndContainsRubric() {
        val longText = "x".repeat(2_000)
        val prompt = MultiAiJudge.buildPrompt(longText, longText, longText)
        assertTrue(prompt.length <= DialogueRouter.MAX_INPUT_CHARS)
        assertTrue(prompt.contains("Korrektheit"))
        assertTrue(prompt.contains("Sicherheit/Datenschutz"))
        assertTrue(prompt.contains("VERDICT: PRIMARY|META|MERGE"))
    }
}
