package de.snowworks.ariana.sidecar

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

class SidecarChatStore(context: Context) {
    companion object {
        private const val PREFS = "x88_sidecar_chats_v1"
        private const val KEY_TOKEN = "relay_token"
        private const val MAX_MESSAGES = 48
        private const val MAX_TEXT_CHARS = 4_000
    }

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(channel: SidecarChannel): List<SidecarMessage> {
        val raw = prefs.getString(historyKey(channel), null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val role = when (item.optString("role")) {
                        SidecarMessage.Role.USER.wireName -> SidecarMessage.Role.USER
                        SidecarMessage.Role.ASSISTANT.wireName -> SidecarMessage.Role.ASSISTANT
                        else -> continue
                    }
                    val text = item.optString("text").trim().take(MAX_TEXT_CHARS)
                    if (text.isBlank()) continue
                    add(
                        SidecarMessage(
                            role = role,
                            text = text,
                            timestampMs = item.optLong("timestampMs", 0L).coerceAtLeast(0L),
                        ),
                    )
                }
            }.takeLast(MAX_MESSAGES)
        }.getOrDefault(emptyList())
    }

    fun append(channel: SidecarChannel, message: SidecarMessage) {
        val cleaned = message.copy(text = message.text.trim().take(MAX_TEXT_CHARS))
        if (cleaned.text.isBlank()) return
        save(channel, (load(channel) + cleaned).takeLast(MAX_MESSAGES))
    }

    fun clear(channel: SidecarChannel) {
        prefs.edit().remove(historyKey(channel)).apply()
    }

    fun relayToken(): String = prefs.getString(KEY_TOKEN, "").orEmpty().trim()

    fun setRelayToken(value: String) {
        prefs.edit().putString(KEY_TOKEN, value.trim().take(256)).apply()
    }

    private fun save(channel: SidecarChannel, messages: List<SidecarMessage>) {
        val array = JSONArray()
        messages.forEach { message ->
            array.put(
                JSONObject()
                    .put("role", message.role.wireName)
                    .put("text", message.text)
                    .put("timestampMs", message.timestampMs),
            )
        }
        prefs.edit().putString(historyKey(channel), array.toString()).apply()
    }

    private fun historyKey(channel: SidecarChannel) = "history_${channel.wireId}"
}
