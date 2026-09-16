package de.snowworks.app.widget

object WakewordPolicy {
    private val names = setOf("ariana", "arianna", "ariane", "arianne")
    private val verbs = setOf("rede", "red", "redet", "sprich", "sprichst")

    fun normalize(raw: String): String = raw
        .lowercase()
        .replace(Regex("[^a-z0-9äöüß]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun matches(raw: String): Boolean {
        val tokens = normalize(raw).split(' ').filter { it.isNotBlank() }
        val nameIndex = tokens.indexOfFirst { it in names }
        if (nameIndex < 0) return false

        for (i in 0 until tokens.lastIndex) {
            if (tokens[i] != "mit" || tokens[i + 1] != "mir") continue
            val verbStart = (i - 3).coerceAtLeast(0)
            val hasVerb = (verbStart until i).any { tokens[it] in verbs }
            if (hasVerb && nameIndex <= i + 1 && i - nameIndex <= 7) return true
        }
        return false
    }
}
