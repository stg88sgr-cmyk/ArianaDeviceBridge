package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class ExternalReviewerAdapterTest {

    @Test
    fun metaConfigProducesProviderNeutralExternalReviewerIdentity() {
        val config = SecureAiProviderStore.Config(
            endpoint = "https://api.meta.ai/v1/chat/completions",
            model = "muse-spark-1.3",
            apiKey = "test-key",
        )

        assertEquals(
            "https-ai:api.meta.ai:muse-spark-1.3",
            MultiAiRouter.externalReviewerProviderId(config),
        )
    }

    @Test
    fun resultExposesProviderNeutralReviewerFieldsWithLegacyAliases() {
        val result = MultiAiRouter.Result(
            ok = true,
            mode = MultiAiRouter.Mode.EXTERNAL_REVIEWER,
            externalReviewerProviderId = "https-ai:test:model",
            externalReviewerReply = "review",
        )

        assertEquals("https-ai:test:model", result.externalReviewerProviderId)
        assertEquals("review", result.externalReviewerReply)
        assertEquals(result.externalReviewerProviderId, result.metaProviderId)
        assertEquals(result.externalReviewerReply, result.metaReply)

    }
}
