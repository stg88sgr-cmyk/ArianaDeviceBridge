package de.snowworks.ariana.bridge

/**
 * Judge layer for multi-AI decisions.
 *
 * The judge does not trust provider identity or writing style. It compares two
 * candidate answers against a fixed rubric and asks Ariana's active primary
 * provider to produce one explicit verdict and one final answer.
 */
object MultiAiJudge {
    enum class Verdict {
        PRIMARY,
        META,
        MERGE,
    }

    data class Decision(
        val ok: Boolean,
        val verdict: Verdict? = null,
        val judgeProviderId: String? = null,
        val rationale: String? = null,
        val finalReply: String? = null,
        val error: String? = null,
    )

    fun decide(
        task: String,
        primaryReply: String,
        metaReply: String,
    ): Decision {
        if (primaryReply.isBlank() || metaReply.isBlank()) {
            return Decision(ok = false, error = "JUDGE_NEEDS_TWO_CANDIDATES")
        }

        val prompt = buildPrompt(task, primaryReply, metaReply)
        val judged = DialogueRouter.generate(prompt)
        if (!judged.ok || judged.reply.isNullOrBlank()) {
            return Decision(
                ok = false,
                judgeProviderId = judged.providerId,
                error = judged.error ?: "JUDGE_FAILED",
            )
        }

        val parsed = parse(judged.reply)
            ?: return Decision(
                ok = false,
                judgeProviderId = judged.providerId,
                error = "JUDGE_FORMAT_INVALID",
            )

        return Decision(
            ok = true,
            verdict = parsed.verdict,
            judgeProviderId = judged.providerId,
            rationale = parsed.rationale,
            finalReply = parsed.finalReply,
        )
    }

    internal data class ParsedDecision(
        val verdict: Verdict,
        val rationale: String,
        val finalReply: String,
    )

    internal fun buildPrompt(
        task: String,
        primaryReply: String,
        metaReply: String,
    ): String = buildString {
        append("JUDGE: Vergleiche zwei technische Antworten ohne Loyalitaet zu einem Provider. ")
        append("Bewerte Korrektheit, pruefbare Tests/Belege, Anforderungsabdeckung, Sicherheit/Datenschutz ")
        append("und vermeide unnoetige Komplexitaet. Waehle MERGE nur wenn beide wertvolle Teile liefern. ")
        append("Aufgabe: ").append(clean(task).take(180))
        append(" PRIMARY: ").append(clean(primaryReply).take(280))
        append(" META: ").append(clean(metaReply).take(280))
        append(" Ausgabe exakt: VERDICT: PRIMARY|META|MERGE RATIONALE: <kurz> FINAL: <beste vollstaendige Antwort>")
    }.take(DialogueRouter.MAX_INPUT_CHARS)

    internal fun parse(raw: String): ParsedDecision? {
        val text = clean(raw)
        val verdictMatch = Regex("VERDICT\\s*:\\s*(PRIMARY|META|MERGE)\\b", RegexOption.IGNORE_CASE)
            .find(text) ?: return null
        val rationaleMatch = Regex(
            "RATIONALE\\s*:\\s*(.*?)\\s+FINAL\\s*:",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(text) ?: return null
        val finalMatch = Regex(
            "FINAL\\s*:\\s*(.+)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(text) ?: return null

        val verdict = runCatching { Verdict.valueOf(verdictMatch.groupValues[1].uppercase()) }.getOrNull()
            ?: return null
        val rationale = rationaleMatch.groupValues[1].trim().take(300)
        val finalReply = finalMatch.groupValues[1].trim().take(DialogueRouter.MAX_REPLY_CHARS)
        if (rationale.isEmpty() || finalReply.isEmpty()) return null

        return ParsedDecision(
            verdict = verdict,
            rationale = rationale,
            finalReply = finalReply,
        )
    }

    private fun clean(raw: String): String = raw
        .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
