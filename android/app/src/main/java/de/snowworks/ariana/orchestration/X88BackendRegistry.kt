package de.snowworks.ariana.orchestration

/**
 * Provider-neutral backend registry for X88 capabilities.
 *
 * Backends are descriptors only. Execution remains owned by the capability
 * implementation and its existing safety/permission boundaries.
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
    private val entries = backends
        .distinctBy { it.id }
        .associateBy { it.capability }

    fun register(backend: X88Backend): X88BackendRegistry =
        X88BackendRegistry(entries.values + backend)

    fun backendFor(capability: X88Capability): X88Backend? = entries[capability]

    fun localCapabilities(): Set<X88Capability> =
        entries.values.filter { it.local }.map { it.capability }.toSet()

    fun ids(): Set<String> = entries.values.map { it.id }.toSet()
}
