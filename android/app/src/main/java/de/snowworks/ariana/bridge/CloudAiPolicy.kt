package de.snowworks.ariana.bridge

/**
 * Privacy gate for every prompt that is about to leave the device.
 *
 * Policy:
 * - device-sensitive material stays local by default;
 * - secrets are redacted before remote use;
 * - direct personal identifiers are redacted before remote use;
 * - ordinary public/code content may pass through.
 *
 * This object is intentionally Android-free so it can be unit-tested on the JVM.
 */
object CloudAiPolicy {
    enum class Classification {
        PUBLIC,
        CODE,
        PERSONAL,
        DEVICE_SENSITIVE,
        SECRET,
    }

    enum class Disposition {
        ALLOW,
        REDACTED,
        LOCAL_ONLY,
    }

    data class Decision(
        val disposition: Disposition,
        val classification: Classification,
        val text: String,
        val redactions: Int = 0,
        val reason: String? = null,
    )

    private data class RedactionRule(
        val pattern: Regex,
        val replacement: String,
    )

    private val deviceSensitivePatterns = listOf(
        Regex("(?i)(?:content|file)://\\S+"),
        Regex("(?i)(?:^|\\s)/(?:data/(?:user|data)|sdcard|storage/emulated)/\\S+"),
        Regex("(?i)\\b(?:imei|android[_ -]?id|device[_ -]?serial|serial[_ -]?number)\\b\\s*[:=]\\s*[\\\"']?[A-Za-z0-9._:-]{6,}"),
        Regex("(?i)\\b(?:lat(?:itude)?|lon(?:gitude)?|lng)\\s*[:=]\\s*[-+]?\\d{1,3}(?:\\.\\d{4,})\\b"),
    )

    private val secretRules = listOf(
        RedactionRule(
            Regex(
                "-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----.*?-----END (?:RSA |EC |OPENSSH )?PRIVATE KEY-----",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
            ),
            "[REDACTED_PRIVATE_KEY]",
        ),
        RedactionRule(
            Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/=-]{12,}"),
            "Bearer [REDACTED_SECRET]",
        ),
        RedactionRule(
            Regex("(?i)\\b(?:github_pat_|gh[pousr]_|sk-proj-|sk-|AIza)[A-Za-z0-9_-]{12,}"),
            "[REDACTED_SECRET]",
        ),
        RedactionRule(
            Regex("(?i)\\b(?:api[_-]?key|access[_-]?token|auth[_-]?token|client[_-]?secret|token|password|passwd|secret)\\s*[:=]\\s*[\\\"']?[^\\s\\\"'`,;]{6,}"),
            "[REDACTED_SECRET_ASSIGNMENT]",
        ),
        RedactionRule(
            Regex("(?i)\\bAuthorization\\s*:\\s*Basic\\s+[A-Za-z0-9+/=]{8,}"),
            "Authorization: Basic [REDACTED_SECRET]",
        ),
    )

    private val personalRules = listOf(
        RedactionRule(
            Regex("(?i)\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b"),
            "[REDACTED_EMAIL]",
        ),
        RedactionRule(
            Regex("(?<![A-Za-z0-9])\\+\\d[\\d ()/-]{7,}\\d"),
            "[REDACTED_PHONE]",
        ),
    )

    private val codeMarkers = listOf(
        "```",
        "package ",
        "import ",
        "class ",
        "object ",
        "fun ",
        "val ",
        "var ",
        "gradle",
        "stacktrace",
        "exception",
        "override ",
        "->",
        "=>",
    )

    fun evaluate(rawText: String): Decision {
        val normalized = normalize(rawText)
        if (normalized.isEmpty()) {
            return Decision(
                disposition = Disposition.ALLOW,
                classification = Classification.PUBLIC,
                text = "",
                reason = "EMPTY_INPUT",
            )
        }

        if (deviceSensitivePatterns.any { it.containsMatchIn(normalized) }) {
            return Decision(
                disposition = Disposition.LOCAL_ONLY,
                classification = Classification.DEVICE_SENSITIVE,
                text = "",
                reason = "CLOUD_POLICY_DEVICE_SENSITIVE_LOCAL_ONLY",
            )
        }

        val (withoutSecrets, secretCount) = redact(normalized, secretRules)
        val (sanitized, personalCount) = redact(withoutSecrets, personalRules)
        val totalRedactions = secretCount + personalCount

        val classification = when {
            secretCount > 0 -> Classification.SECRET
            personalCount > 0 -> Classification.PERSONAL
            looksLikeCode(normalized) -> Classification.CODE
            else -> Classification.PUBLIC
        }

        return Decision(
            disposition = if (totalRedactions > 0) Disposition.REDACTED else Disposition.ALLOW,
            classification = classification,
            text = sanitized.take(DialogueRouter.MAX_INPUT_CHARS),
            redactions = totalRedactions,
            reason = when {
                secretCount > 0 -> "CLOUD_POLICY_SECRETS_REDACTED"
                personalCount > 0 -> "CLOUD_POLICY_PERSONAL_REDACTED"
                else -> null
            },
        )
    }

    private fun redact(input: String, rules: List<RedactionRule>): Pair<String, Int> {
        var output = input
        var count = 0
        for (rule in rules) {
            val hits = rule.pattern.findAll(output).count()
            if (hits == 0) continue
            count += hits
            output = rule.pattern.replace(output, rule.replacement)
        }
        return output to count
    }

    private fun looksLikeCode(text: String): Boolean {
        val lower = text.lowercase()
        return codeMarkers.any { lower.contains(it) }
    }

    private fun normalize(rawText: String): String = rawText
        .replace(Regex("[\\u0000-\\u0008\\u000B\\u000C\\u000E-\\u001F\\u007F]+"), " ")
        .trim()
}
