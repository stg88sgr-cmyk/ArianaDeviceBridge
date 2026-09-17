package de.snowworks.ariana.sidecar

enum class SidecarChannel(
    val wireId: String,
    val title: String,
    val purpose: String,
) {
    OBSERVE(
        wireId = "observe",
        title = "ECK 1 · OBSERVE",
        purpose = "Beobachten, ordnen, analysieren. Keine Meta-AI-Abhängigkeit.",
    ),
    BUILD(
        wireId = "build",
        title = "ECK 2 · BUILD",
        purpose = "Bauen, entwerfen, umsetzen. Eigener Gesprächsverlauf.",
    ),
    VERIFY(
        wireId = "verify",
        title = "ECK 3 · VERIFY",
        purpose = "Prüfen, gegenchecken, Fehler finden. Eigener Gesprächsverlauf.",
    );

    companion object {
        fun fromWireId(value: String?): SidecarChannel =
            entries.firstOrNull { it.wireId == value } ?: OBSERVE
    }
}

data class SidecarMessage(
    val role: Role,
    val text: String,
    val timestampMs: Long = System.currentTimeMillis(),
) {
    enum class Role(val wireName: String) {
        USER("user"),
        ASSISTANT("assistant"),
    }
}
