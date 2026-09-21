package de.snowworks.ariana.memory

import android.content.Context

class SharedPreferencesX88MemoryStore(
    context: Context,
) : X88MemoryStore {

    private val prefs = context.applicationContext
        .getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    override fun load(): List<X88MemoryItem> =
        X88MemoryArchiveCodec.decode(prefs.getString(KEY_ARCHIVE, "") ?: "")

    override fun save(items: List<X88MemoryItem>) {
        prefs.edit()
            .putString(KEY_ARCHIVE, X88MemoryArchiveCodec.encode(items))
            .apply()
    }

    override fun clear() {
        prefs.edit().remove(KEY_ARCHIVE).apply()
    }

    companion object {
        private const val PREFS = "x88_memory"
        private const val KEY_ARCHIVE = "memory_archive"
    }
}
