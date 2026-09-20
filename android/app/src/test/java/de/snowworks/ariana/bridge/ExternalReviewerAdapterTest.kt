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
}
