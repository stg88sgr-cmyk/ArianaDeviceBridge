package de.snowworks.ariana.system

data class X88Command(
    val id: String,
    val capability: X88Capability,
    val sessionId: String?,
    val action: String,
    val arguments: Map<String, String> = emptyMap(),
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(action.isNotBlank()) { "action must not be blank" }
    }
}
