package de.snowworks.ariana.world

import de.snowworks.ariana.neuro.V30NeuroRuntime

/** Process-local attachment point for the symbolic X-88 fantasy world. */
object X88FantasyWorldBootstrap {
    @Volatile
    private var engine: X88FantasyWorldEngine? = null

    @Synchronized
    fun attach(runtime: V30NeuroRuntime): X88FantasyWorldEngine {
        val active = engine ?: X88FantasyWorldEngine().also { engine = it }
        if (active.id !in runtime.fabric.moduleIds()) {
            runtime.fabric.register(active)
        }
        return active
    }

    fun currentOrNull(): X88FantasyWorldEngine? = engine
}
