package de.snowworks.ariana.orchestration

/**
 * Provider-neutral backend registry for X88 capabilities.
 *
 * The registry stores descriptors only. Execution remains owned by the
 * capability implementation and its existing safety/permission boundaries.
 */
data class X88Backend(
    val id: String,
    val capability: X88Capability,
    val local: Boolean,
) {
    init {
        require(id.isNotBlank())
    }
}

class X88BackendRegistry(
    backends: Collection<X88Backend> = emptyList(),
) {
    private val entries: Map<X88Capability, List<X88Backend>> =
        backends
            .groupBy { it.capability }
            .mapValues { (_, values) ->
                values.distinctBy { it.id }
            }

    fun register(backend: X88Backend): X88BackendRegistry =
        X88BackendRegistry(entries.values.flatten() + backend)

    fun backendsFor(capability: X88Capability): List<X88Backend> =
        entries[capability].orEmpty()

    fun backendFor(capability: X88Capability): X88Backend? =
        backendsFor(capability).firstOrNull()

    fun preferredBackend(
        capability: X88Capability,
        preferLocal: Boolean = true,
    ): X88Backend? =
        if (preferLocal) {
            backendsFor(capability).firstOrNull { it.local }
                ?: backendsFor(capability).firstOrNull()
        } else {
            backendsFor(capability).firstOrNull()
        }

    fun localCapabilities(): Set<X88Capability> =
        entries.filterValues { values -> values.any { it.local } }.keys

    fun ids(): Set<String> =
        entries.values.flatten().map { it.id }.toSet()
}
