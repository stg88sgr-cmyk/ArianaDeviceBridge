package de.snowworks.app.widget

object WakewordPolicy {
    fun normalize(raw: String): String = raw
        .lowercase()
        .replace(Regex("[^a-z0-9äöüß]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun matches(raw: String): Boolean {
        val tokens = normalize(raw).split(' ').filter { it.isNotBlank() }
        if (tokens.none { it == "ariana" }) return false
        return tokens.windowed(3).any { parts ->
            parts[0] in setOf("rede", "red") && parts[1] == "mit" && parts[2] == "mir"
        }
    }
}
