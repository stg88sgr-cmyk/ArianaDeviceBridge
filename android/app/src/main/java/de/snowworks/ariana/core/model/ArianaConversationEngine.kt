package de.snowworks.ariana.core.model

import android.content.Context
import de.snowworks.ariana.core.AuditLog
import de.snowworks.ariana.core.MemoryVault

/**
 * Controlled conversation path:
 * user text -> bounded local memory selection -> protocol payload -> gated router.
 */
class ArianaConversationEngine(context: Context) {
    data class PreparedRequest(
        val provider: ModelProviderStore.Provider,
        val userText: String,
        val memory: MemoryContextBuilder.Selection,
        val payloadJson: String,
    )

    data class Reply(
        val text: String,
        val statusCode: Int,
        val providerId: String,
        val memoryRecordIds: List<String>,
    )

    private val app = context.applicationContext
    private val vault = MemoryVault(app)
    private val providerStore = ModelProviderStore(app)
    private val contextBuilder = MemoryContextBuilder(vault)
    private val router = ModelRouter(app)
    private val audit = AuditLog(app)

    fun prepare(userText: String, systemText: String = DEFAULT_SYSTEM_TEXT): PreparedRequest {
        require(userText.isNotBlank()) { "Message must not be empty." }
        val provider = providerStore.active() ?: error("No active model provider configured.")
        val memory = contextBuilder.select(userText)
        val payload = OpenAiCompatibleCodec.requestJson(
            model = provider.model,
            systemText = systemText,
            userText = userText,
            memoryContext = memory.renderedText,
        )
        return PreparedRequest(provider, userText, memory, payload)
    }

    /** The caller should invoke this only after a visible user send action. */
    fun send(prepared: PreparedRequest): Reply {
        val active = providerStore.active() ?: error("Model provider was disabled after preparation. Prepare again.")
        require(
            active.id == prepared.provider.id &&
                active.endpoint == prepared.provider.endpoint &&
                active.model == prepared.provider.model,
        ) { "Model provider changed after preparation. Prepare again before sending." }

        val response = router.sendJson(prepared.payloadJson)
        val text = OpenAiCompatibleCodec.responseText(response.body)
        audit.append(
            AuditLog.Event(
                category = "conversation",
                action = "model_reply",
                decision = "OK",
                reason = "${prepared.memory.records.size} local memory records selected",
                provider = prepared.provider.label,
            ),
        )
        return Reply(
            text = text,
            statusCode = response.statusCode,
            providerId = response.providerId,
            memoryRecordIds = prepared.memory.records.map { it.id },
        )
    }

    companion object {
        private const val DEFAULT_SYSTEM_TEXT =
            "You are the language engine attached to a user-controlled local X-Ariana core. " +
                "Be truthful about uncertainty. Do not claim device actions, memories, permissions, or network access that are not present in the supplied context."
    }
}
