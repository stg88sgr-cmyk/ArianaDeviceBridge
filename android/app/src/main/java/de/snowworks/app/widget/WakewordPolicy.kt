package de.snowworks.app.widget

object WakewordPolicy {
    fun normalize(raw: String): String = raw
        .lowercase()
        .replace(Regex("[^a-z0-9äöüß]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun matches(raw: String): Boolean {
        val text = normalize(raw)
        if (!text.contains("ariana")) return false
        return text.contains("rede mit mir") || text.contains("red mit mir")
    }
}
