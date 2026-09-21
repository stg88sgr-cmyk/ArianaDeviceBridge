package de.snowworks.ariana.orchestration

import de.snowworks.ariana.neuro.ResonanceState

/**
 * Provider-neutral X88 orchestration boundary.
 *
 * This layer decides which local capability should handle a task. It does not
 * execute network calls, request Android permissions, or bypass platform policy.
 */
enum class X88TaskKind {
    DIALOGUE,
    CREATION,
    MEMORY,
    DEVICE_ACTION,
}

enum class X88Capability {
    LOCAL_DIALOGUE,
    LOCAL_CREATION,
    LOCAL_MEMORY,
    DEVICE_BRIDGE,
}

data class X88Task(
    val kind: X88TaskKind,
    val text: String,
)

data class X88Route(
    val capability: X88Capability,
    val reason: String,
    val resonance: ResonanceState,
)

class X88ControlPlane(
    private val available: Set<X88Capability> = X88Capability.values().toSet(),
) {
    fun route(task: X88Task, resonance: ResonanceState): X88Route {
        require(task.text.isNotBlank())

        val preferred = when (task.kind) {
            X88TaskKind.DIALOGUE -> X88Capability.LOCAL_DIALOGUE
            X88TaskKind.CREATION -> X88Capability.LOCAL_CREATION
            X88TaskKind.MEMORY -> X88Capability.LOCAL_MEMORY
            X88TaskKind.DEVICE_ACTION -> X88Capability.DEVICE_BRIDGE
        }

        val capability = if (preferred in available) {
            preferred
        } else {
            fallback()
        }

        return X88Route(
            capability = capability,
            reason = if (capability == preferred) {
                "preferred local capability available"
            } else {
                "preferred capability unavailable; bounded fallback selected"
            },
            resonance = resonance,
        )
    }

    private fun fallback(): X88Capability =
        listOf(
            X88Capability.LOCAL_DIALOGUE,
            X88Capability.LOCAL_CREATION,
            X88Capability.LOCAL_MEMORY,
            X88Capability.DEVICE_BRIDGE,
        ).firstOrNull { it in available }
            ?: error("X88 control plane has no available capability")
}
