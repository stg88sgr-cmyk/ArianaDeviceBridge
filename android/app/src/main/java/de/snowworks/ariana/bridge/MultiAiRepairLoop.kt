package de.snowworks.ariana.bridge

/**
 * Pure helper for the bounded Fix -> Test -> Review dialogue loop.
 *
 * This layer never executes arbitrary code or device actions. It creates compact
 * verification/revision prompts and parses the remote review contract. Actual
 * repository/device mutations stay outside the dialogue loop.
 */
object MultiAiRepairLoop {
    const val MAX_ROUNDS = 2

    enum class Status {
        PASS,
        FIX,
    }

    data class Review(
        val status: Status,
        val issues: String,
        val tests: String,
        val candidate: String? = null,
    )

    internal fun buildReviewPrompt(
        task: String,
        draft: String,
        round: Int,
    ): String = buildString {
        append("VERIFY round ").append(round.coerceIn(1, MAX_ROUNDS)).append('/').append(MAX_ROUNDS).append(": ")
        append("Pruefe die technische Antwort gegen die Aufgabe. Teste logisch auf Korrektheit, Randfaelle, ")
        append("Anforderungsabdeckung, Sicherheit/Datenschutz und ob genannte Tests die Behauptungen wirklich tragen. ")
        append("Keine Aktionen ausfuehren. Aufgabe: ").append(clean(task).take(260))
        append(" DRAFT: ").append(clean(draft).take(520))
        append(" Ausgabe exakt: STATUS: PASS|FIX ISSUES: <kurz> TESTS: <konkrete pruefbare Checks> ")
        append("CANDIDATE: <nur bei FIX eine korrigierte vollstaendige Antwort, sonst NONE>")
    }.take(DialogueRouter.MAX_INPUT_CHARS)

    internal fun buildRevisionPrompt(
        task: String,
        draft: String,
        review: Review,
        round: Int,
    ): String = buildString {
        append("REPAIR round ").append(round.coerceIn(1, MAX_ROUNDS)).append('/').append(MAX_ROUNDS).append(": ")
        append("Verbessere die Antwort gezielt anhand des Reviews. Keine neuen unbelegten Behauptungen, ")
        append("keine Geraete- oder Repo-Aktionen ausfuehren. Aufgabe: ").append(clean(task).take(220))
        append(" DRAFT: ").append(clean(draft).take(360))
        append(" ISSUES: ").append(clean(review.issues).take(220))
        append(" TESTS: ").append(clean(review.tests).take(220))
        review.candidate?.takeIf { it.isNotBlank() }?.let {
            append(" REVIEWER_CANDIDATE: ").append(clean(it).take(260))
        }
        append(" Liefere jetzt nur die korrigierte vollstaendige Antwort.")
    }.take(DialogueRouter.MAX_INPUT_CHARS)

    internal fun parseReview(raw: String): Review? {
        val text = clean(raw)
        val statusMatch = Regex("STATUS\\s*:\\s*(PASS|FIX)\\b", RegexOption.IGNORE_CASE)
            .find(text) ?: return null
        val issuesMatch = Regex(
            "ISSUES\\s*:\\s*(.*?)\\s+TESTS\\s*:",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(text) ?: return null
        val testsMatch = Regex(
            "TESTS\\s*:\\s*(.*?)(?:\\s+CANDIDATE\\s*:|$)",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(text) ?: return null
        val candidateMatch = Regex(
            "CANDIDATE\\s*:\\s*(.+)$",
            setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
        ).find(text)

        val status = runCatching { Status.valueOf(statusMatch.groupValues[1].uppercase()) }.getOrNull()
            ?: return null
        val issues = issuesMatch.groupValues[1].trim().take(400)
        val tests = testsMatch.groupValues[1].trim().take(500)
        val rawCandidate = candidateMatch?.groupValues?.get(1)?.trim().orEmpty()
        val candidate = rawCandidate
            .takeUnless { it.isBlank() || it.equals("NONE", ignoreCase = true) }
            ?.take(DialogueRouter.MAX_REPLY_CHARS)

        if (issues.isEmpty() || tests.isEmpty()) return null
        if (status == Status.FIX && candidate.isNullOrBlank()) return null

        return Review(
            status = status,
            issues = issues,
            tests = tests,
            candidate = candidate,
        )
    }

    private fun clean(raw: String): String = raw
        .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
