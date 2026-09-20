package de.snowworks.ariana.bridge

/** Bridges any OpenAI-compatible HTTPS endpoint into the external-reviewer boundary. */
class HttpsExternalReviewerAdapter(
    private val config: SecureAiProviderStore.Config,
    override val id: String,
) : AiProviderAdapter {
    override val timeoutMs: Long = MultiAiRouter.META_TIMEOUT_MS

    override fun generate(text: String): String =
        HttpsDialogueProvider(config).generate(text)
}
