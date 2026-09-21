package de.snowworks.ariana.orchestration

import de.snowworks.ariana.bridge.AiProviderAdapter
import de.snowworks.ariana.memory.X88MemoryBridge

/** Describes an AI provider without making the registry depend on its implementation. */
fun AiProviderAdapter.asX88Backend(
    local: Boolean,
    capability: X88Capability = X88Capability.LOCAL_DIALOGUE,
): X88Backend =
    X88Backend(
        id = id,
        capability = capability,
        local = local,
    )

/** Describes the memory boundary without making orchestration own memory storage. */
fun X88MemoryBridge.asX88Backend(
    id: String = "x88-memory",
    local: Boolean = true,
): X88Backend =
    X88Backend(
        id = id,
        capability = X88Capability.LOCAL_MEMORY,
        local = local,
    )
