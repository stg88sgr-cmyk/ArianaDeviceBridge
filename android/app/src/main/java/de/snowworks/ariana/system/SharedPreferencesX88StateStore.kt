package de.snowworks.ariana.system

import android.content.Context

class SharedPreferencesX88StateStore(
    context: Context,
) : X88StateStore {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): X88PersistentState {
        val capabilities = prefs.getStringSet(KEY_CAPABILITIES, emptySet()).orEmpty()
            .mapNotNull { raw -> runCatching { X88Capability.valueOf(raw) }.getOrNull() }
            .toSet()
        val channel = prefs.getString(KEY_CHANNEL, X88UpdateChannel.STABLE.name)
            ?.let { raw -> runCatching { X88UpdateChannel.valueOf(raw) }.getOrNull() }
            ?: X88UpdateChannel.STABLE
        return X88PersistentState(
            enabledCapabilities = capabilities,
            updateChannel = channel,
        )
    }

    override fun save(state: X88PersistentState) {
        prefs.edit()
            .putStringSet(KEY_CAPABILITIES, state.enabledCapabilities.map { it.name }.toSet())
            .putString(KEY_CHANNEL, state.updateChannel.name)
            .apply()
    }

    override fun clear() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val PREFS = "x88_system_core"
        private const val KEY_CAPABILITIES = "enabled_capabilities"
        private const val KEY_CHANNEL = "update_channel"
    }
}
