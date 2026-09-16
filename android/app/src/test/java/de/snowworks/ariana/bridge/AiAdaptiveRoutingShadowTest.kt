package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AiAdaptiveRoutingShadowTest {

    @Test
    fun stableProvidersKeepCurrentPolicy() {
        val plan = AiAdaptiveRoutingShadow.evaluate(
            AiProviderQualityAssessment.Level.STABLE,
            AiProviderQualityAssessment.Level.STABLE,
        )
        assertEquals(AiAdaptiveRoutingShadow.Recommendation.KEEP_CURRENT, plan.recommendation)
        assertFalse(plan.changesLiveRouting)
    }

    @Test
    fun insufficientDataOnlyObserves() {
        val plan = AiAdaptiveRoutingShadow.evaluate(
            AiProviderQualityAssessment.Level.INSUFFICIENT_DATA,
            AiProviderQualityAssessment.Level.STABLE,
        )
        assertEquals(AiAdaptiveRoutingShadow.Recommendation.OBSERVE, plan.recommendation)
        assertFalse(plan.changesLiveRouting)
    }

    @Test
    fun degradedClaudeSuggestsMetaFirstCodeTrial() {
        val plan = AiAdaptiveRoutingShadow.evaluate(
            AiProviderQualityAssessment.Level.DEGRADED,
            AiProviderQualityAssessment.Level.STABLE,
        )
        assertEquals(AiAdaptiveRoutingShadow.Recommendation.TEST_META_FIRST_FOR_CODE, plan.recommendation)
        assertFalse(plan.changesLiveRouting)
    }

    @Test
    fun degradedMetaSuggestsClaudeFirstSecondOpinionTrial() {
        val plan = AiAdaptiveRoutingShadow.evaluate(
            AiProviderQualityAssessment.Level.STABLE,
            AiProviderQualityAssessment.Level.DEGRADED,
        )
        assertEquals(AiAdaptiveRoutingShadow.Recommendation.TEST_CLAUDE_FIRST_FOR_SECOND_OPINION, plan.recommendation)
        assertFalse(plan.changesLiveRouting)
    }

    @Test
    fun bothDegradedSuggestCloudContainmentOnlyInShadow() {
        val plan = AiAdaptiveRoutingShadow.evaluate(
            AiProviderQualityAssessment.Level.DEGRADED,
            AiProviderQualityAssessment.Level.DEGRADED,
        )
        assertEquals(AiAdaptiveRoutingShadow.Recommendation.CONTAIN_CLOUD, plan.recommendation)
        assertFalse(plan.changesLiveRouting)
    }

    @Test
    fun watchStateDoesNotReorderLiveTraffic() {
        val plan = AiAdaptiveRoutingShadow.evaluate(
            AiProviderQualityAssessment.Level.WATCH,
            AiProviderQualityAssessment.Level.STABLE,
        )
        assertEquals(AiAdaptiveRoutingShadow.Recommendation.OBSERVE, plan.recommendation)
        assertFalse(plan.changesLiveRouting)
    }
}
