package de.snowworks.ariana.bridge

import android.content.Context
import android.content.SharedPreferences
import java.util.Locale

/**
 * App-private, durable Ariana memory.
 *
 * Memory v2 keeps the existing v1 user-name slot and migrates legacy facts into
 * five bounded local sections. The full conversation transcript is never stored.
 */
class LocalMemoryStore(context: Context) {
    enum class Category(val storageKey: String, val label: String) {
        PERSON("person", "Person"),
        PREFERENCES("preferences", "Vorlieben"),
        PROJECTS("projects", "Projekte"),
        DEVICES("devices", "Geräte"),
        DECISIONS("decisions", "Entscheidungen"),
    }

    data class Snapshot(
        val userName: String?,
        val person: List<String>,
        val preferences: List<String>,
        val projects: List<String>,
        val devices: List<String>,
        val decisions: List<String>,
    ) {
        val facts: List<String>
            get() = person + preferences + projects + devices + decisions

        val itemCount: Int
            get() = facts.size + if (userName.isNullOrBlank()) 0 else 1

        fun facts(category: Category): List<String> = when (category) {
            Category.PERSON -> person
            Category.PREFERENCES -> preferences
            Category.PROJECTS -> projects
            Category.DEVICES -> devices
            Category.DECISIONS -> decisions
        }

        fun count(category: Category): Int = facts(category).size
    }

    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE,
    )

    fun snapshot(): Snapshot {
        migrateV1IfNeeded()
        return readSnapshot()
    }

    fun setUserName(value: String) {
        val clean = clean(value, MAX_NAME_CHARS) ?: return
        prefs.edit().putString(KEY_USER_NAME, clean).apply()
    }

    fun rememberFact(value: String, category: Category = classify(value)) {
        val clean = clean(value, MAX_FACT_CHARS) ?: return
        migrateV1IfNeeded()

        val current = readSnapshot()
        val sections = Category.entries.associateWith { current.facts(it).toMutableList() }.toMutableMap()
        val key = comparisonKey(clean)

        sections.values.forEach { list ->
            list.removeAll { comparisonKey(it) == key }
        }

        val target = sections.getValue(category)
        target.add(clean)
        while (target.size > MAX_FACTS_PER_CATEGORY) target.removeAt(0)
        persistSections(sections)
    }

    fun clear() {
        prefs.edit().clear().apply()
    }

    fun classify(value: String): Category {
        val normalized = comparisonKey(value)

        if (containsAny(
                normalized,
                "entschieden", "beschlossen", "entscheidung", "ab jetzt", "kuenftig",
                "künftig", "soll künftig", "prioritaet", "priorität", "wichtigstes ziel",
            )
        ) return Category.DECISIONS

        if (containsAny(
                normalized,
                "handy", "telefon", "smartphone", "samsung", "galaxy", "s23", "android",
                "tablet", "computer", "pc", "laptop", "fernseher", "tv", "waipu", "geraet", "gerät",
            )
        ) return Category.DEVICES

        if (containsAny(
                normalized,
                "projekt", "app", "ariana", "snowcore", "github", "repository", "repo",
                "software", "code", "bauen", "entwickeln", "entwicklung",
            )
        ) return Category.PROJECTS

        if (containsAny(
                normalized,
                "lieblings", "mag ", "mag ich", "liebe ", "bevorzuge", "bevorzugt", "gefaellt",
                "gefällt", "hasse", "vorliebe", "geschmack",
            )
        ) return Category.PREFERENCES

        return Category.PERSON
    }

    private fun migrateV1IfNeeded() {
        if (prefs.getBoolean(KEY_V2_MIGRATED, false)) return

        val legacyCount = prefs.getInt(KEY_FACT_COUNT_V1, 0).coerceIn(0, MAX_LEGACY_FACTS)
        val sections = Category.entries.associateWith { mutableListOf<String>() }.toMutableMap()

        for (index in 0 until legacyCount) {
            val fact = prefs.getString(legacyFactKey(index), null)
                ?.let { clean(it, MAX_FACT_CHARS) }
                ?: continue
            val category = classify(fact)
            val list = sections.getValue(category)
            if (list.none { comparisonKey(it) == comparisonKey(fact) }) list.add(fact)
        }

        sections.values.forEach { list ->
            while (list.size > MAX_FACTS_PER_CATEGORY) list.removeAt(0)
        }

        val editor = prefs.edit().putBoolean(KEY_V2_MIGRATED, true)
        writeSections(sections, editor)
        editor.remove(KEY_FACT_COUNT_V1)
        for (index in 0 until MAX_LEGACY_FACTS) editor.remove(legacyFactKey(index))
        editor.apply()
    }

    private fun readSnapshot(): Snapshot {
        val userName = prefs.getString(KEY_USER_NAME, null)
            ?.trim()
            ?.takeIf { it.isNotBlank() }

        fun read(category: Category): List<String> {
            val count = prefs.getInt(countKey(category), 0).coerceIn(0, MAX_FACTS_PER_CATEGORY)
            return (0 until count).mapNotNull { index ->
                prefs.getString(factKey(category, index), null)
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
            }
        }

        return Snapshot(
            userName = userName,
            person = read(Category.PERSON),
            preferences = read(Category.PREFERENCES),
            projects = read(Category.PROJECTS),
            devices = read(Category.DEVICES),
            decisions = read(Category.DECISIONS),
        )
    }

    private fun persistSections(sections: Map<Category, List<String>>) {
        val editor = prefs.edit()
        writeSections(sections, editor)
        editor.apply()
    }

    private fun writeSections(
        sections: Map<Category, List<String>>,
        editor: SharedPreferences.Editor,
    ) {
        Category.entries.forEach { category ->
            val facts = sections[category].orEmpty().takeLast(MAX_FACTS_PER_CATEGORY)
            editor.putInt(countKey(category), facts.size)
            for (index in 0 until MAX_FACTS_PER_CATEGORY) editor.remove(factKey(category, index))
            facts.forEachIndexed { index, fact -> editor.putString(factKey(category, index), fact) }
        }
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
        .replace("ß", "ss")
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace(Regex("[^a-z0-9]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun containsAny(value: String, vararg needles: String): Boolean =
        needles.any { comparisonKey(it) in value }

    private fun countKey(category: Category) = "v2_${category.storageKey}_count"
    private fun factKey(category: Category, index: Int) = "v2_${category.storageKey}_$index"
    private fun legacyFactKey(index: Int) = "fact_$index"

    private companion object {
        const val PREFS_NAME = "ariana_local_memory_v1"
        const val KEY_USER_NAME = "user_name"
        const val KEY_V2_MIGRATED = "memory_v2_migrated"
        const val KEY_FACT_COUNT_V1 = "fact_count"
        const val MAX_LEGACY_FACTS = 8
        const val MAX_FACTS_PER_CATEGORY = 5
        const val MAX_NAME_CHARS = 40
        const val MAX_FACT_CHARS = 140
    }
}
