package de.snowworks.ariana.bridge

/**
 * Read-only adaptive routing advisor.
 *
 * Shadow mode never changes provider order, provider state, circuit state or credentials.
 * It only turns provider-quality assessments into a conservative recommendation that can
 * be inspected in Health before any future adaptive routing policy is enabled.
 */
object AiAdaptiveRoutingShadow {
    enum class Recommendation {
        KEEP_CURRENT,
        OBSERVE,
        TEST_META_FIRST_FOR_CODE,
        TEST_CLAUDE_FIRST_FOR_SECOND_OPINION,
        CONTAIN_CLOUD,
    }

    data class Plan(
        val recommendation: Recommendation,
        val changesLiveRouting: Boolean = false,
        val claudeLevel: AiProviderQualityAssessment.Level,
        val metaLevel: AiProviderQualityAssessment.Level,
        val reasons: List<String>,
    )

    fun evaluate(
        claude: AiProviderQualityAssessment.Assessment,
        meta: AiProviderQualityAssessment.Assessment,
    ): Plan = evaluate(claude.level, meta.level)

    internal fun evaluate(
        claudeLevel: AiProviderQualityAssessment.Level,
        metaLevel: AiProviderQualityAssessment.Level,
    ): Plan {
        val recommendation: Recommendation
        val reasons = buildList {
            when {
                claudeLevel == AiProviderQualityAssessment.Level.DEGRADED &&
                    metaLevel == AiProviderQualityAssessment.Level.DEGRADED -> {
                    recommendation = Recommendation.CONTAIN_CLOUD
                    add("CLAUDE_DEGRADED")
                    add("META_DEGRADED")
                }

                claudeLevel == AiProviderQualityAssessment.Level.DEGRADED &&
                    metaLevel != AiProviderQualityAssessment.Level.INSUFFICIENT_DATA -> {
                    recommendation = Recommendation.TEST_META_FIRST_FOR_CODE
                    add("CLAUDE_DEGRADED")
                    add("META_AVAILABLE_AS_SHADOW_ALTERNATIVE")
                }

                metaLevel == AiProviderQualityAssessment.Level.DEGRADED &&
                    claudeLevel != AiProviderQualityAssessment.Level.INSUFFICIENT_DATA -> {
                    recommendation = Recommendation.TEST_CLAUDE_FIRST_FOR_SECOND_OPINION
                    add("META_DEGRADED")
                    add("CLAUDE_AVAILABLE_AS_SHADOW_ALTERNATIVE")
                }

                claudeLevel == AiProviderQualityAssessment.Level.INSUFFICIENT_DATA ||
                    metaLevel == AiProviderQualityAssessment.Level.INSUFFICIENT_DATA -> {
                    recommendation = Recommendation.OBSERVE
                    add("INSUFFICIENT_PROVIDER_DATA")
                }

                claudeLevel == AiProviderQualityAssessment.Level.WATCH ||
                    metaLevel == AiProviderQualityAssessment.Level.WATCH -> {
                    recommendation = Recommendation.OBSERVE
                    if (claudeLevel == AiProviderQualityAssessment.Level.WATCH) add("CLAUDE_WATCH")
                    if (metaLevel == AiProviderQualityAssessment.Level.WATCH) add("META_WATCH")
                }

                else -> {
                    recommendation = Recommendation.KEEP_CURRENT
                    add("PROVIDERS_STABLE")
                }
            }
        }

        return Plan(
            recommendation = recommendation,
            changesLiveRouting = false,
            claudeLevel = claudeLevel,
            metaLevel = metaLevel,
            reasons = reasons,
        )
    }
}
