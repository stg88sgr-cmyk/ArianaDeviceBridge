package de.snowworks.ariana.bridge

import android.content.Context
import java.util.Locale

/**
 * Small app-private memory store for explicit, durable Ariana facts.
 *
 * The store intentionally keeps only a user name plus a bounded list of explicit
 * facts. It never persists the full conversation transcript.
 */
class LocalMemoryStore(context: Context) {
    data class Snapshot(
        val userName: String?,
        val facts: List<String>,
    ) {
        val itemCount: Int
            get() = facts.size + if (userName.isNullOrBlank()) 0 else 1
    }

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun snapshot(): Snapshot {
        val userName = prefs.getString(KEY_USER_NAME, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        val count = prefs.getInt(KEY_FACT_COUNT, 0).coerceIn(0, MAX_FACTS)
        val facts = (0 until count).mapNotNull { index ->
            prefs.getString(factKey(index), null)
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }

        return Snapshot(userName = userName, facts = facts)
    }

    fun setUserName(value: String) {
        val clean = clean(value, MAX_NAME_CHARS) ?: return
        prefs.edit().putString(KEY_USER_NAME, clean).apply()
    }

    fun rememberFact(value: String) {
        val clean = clean(value, MAX_FACT_CHARS) ?: return
        val facts = snapshot().facts.toMutableList()
        val key = comparisonKey(clean)
        facts.removeAll { comparisonKey(it) == key }
        facts.add(clean)
        while (facts.size > MAX_FACTS) facts.removeAt(0)
        persistFacts(facts)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    private fun persistFacts(facts: List<String>) {
        val editor = prefs.edit().putInt(KEY_FACT_COUNT, facts.size)
        for (index in 0 until MAX_FACTS) {
            editor.remove(factKey(index))
        }
        facts.forEachIndexed { index, fact ->
            editor.putString(factKey(index), fact)
        }
        editor.apply()
    }

    private fun clean(value: String, maxChars: Int): String? = value
        .replace(Regex("[\\u0000-\\u001f\\u007f]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
        .trimEnd('.', ',', '!', '?', ';', ':')
        .take(maxChars)
        .takeIf { it.length >= 2 }

    private fun comparisonKey(value: String): String = value
        .lowercase(Locale.GERMAN)
        .replace(Regex("[^a-z0-9äöüß]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun factKey(index: Int) = "fact_$index"

    private companion object {
        const val PREFS_NAME = "ariana_local_memory_v1"
        const val KEY_USER_NAME = "user_name"
        const val KEY_FACT_COUNT = "fact_count"
        const val MAX_FACTS = 8
        const val MAX_NAME_CHARS = 40
        const val MAX_FACT_CHARS = 180
    }
}
