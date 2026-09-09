package de.snowworks.ariana.core.model

import org.json.JSONArray
import org.json.JSONObject

/**
 * Minimal codec for OpenAI-compatible chat-completions style endpoints.
 * This is a protocol adapter, not a hard-coded OpenAI connection.
 */
object OpenAiCompatibleCodec {
    private const val MAX_USER_CHARS = 64 * 1024
    private const val MAX_SYSTEM_CHARS = 32 * 1024
    private const val MAX_MEMORY_CHARS = 64 * 1024
    private const val MAX_RESPONSE_CHARS = 256 * 1024

    fun requestJson(
        model: String,
        systemText: String,
        userText: String,
        memoryContext: String = "",
    ): String {
        require(model.isNotBlank()) { "Model name is required." }
        require(userText.length <= MAX_USER_CHARS) { "User message is too long." }
        require(systemText.length <= MAX_SYSTEM_CHARS) { "System text is too long." }
        require(memoryContext.length <= MAX_MEMORY_CHARS) { "Memory context is too long." }

        val messages = JSONArray()
        val combinedSystem = buildString {
            append(systemText.trim())
            if (memoryContext.isNotBlank()) {
                append("\n\nLOCAL MEMORY CONTEXT\n")
                append(memoryContext.trim())
                append("\n\nUse this context only when relevant. Do not claim certainty beyond the supplied context.")
            }
        }
        if (combinedSystem.isNotBlank()) {
            messages.put(JSONObject().put("role", "system").put("content", combinedSystem))
        }
        messages.put(JSONObject().put("role", "user").put("content", userText))

        return JSONObject()
            .put("model", model)
            .put("messages", messages)
            .put("stream", false)
            .toString()
    }

    fun responseText(json: String): String {
        val root = JSONObject(json)
        val choices = root.optJSONArray("choices") ?: error("Response has no choices array.")
        require(choices.length() > 0) { "Response choices are empty." }
        val first = choices.getJSONObject(0)
        val message = first.optJSONObject("message") ?: error("Response has no message object.")
        val content = message.optString("content")
        require(content.isNotBlank()) { "Model returned an empty message." }
        require(content.length <= MAX_RESPONSE_CHARS) { "Model response text is too long." }
        return content
    }
}
