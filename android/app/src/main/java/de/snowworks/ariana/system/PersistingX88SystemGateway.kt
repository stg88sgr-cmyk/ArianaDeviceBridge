package de.snowworks.ariana.system

/**
 * Persists user-selected capability preferences while deliberately keeping
 * runtime authority (master switch, session id, emergency state) ephemeral.
 */
class PersistingX88SystemGateway(
    private val delegate: X88SystemGateway,
    private val store: X88StateStore,
) : X88SystemGateway by delegate {

    init {
        store.load().enabledCapabilities.forEach(delegate::enable)
        delegate.setMasterEnabled(false)
    }

    override fun enable(capability: X88Capability): X88CoreState {
        val state = delegate.enable(capability)
        persist(state)
        return state
    }

    override fun disable(capability: X88Capability): X88CoreState {
        val state = delegate.disable(capability)
        persist(state)
        return state
    }

    override fun emergencyStop(): X88CoreState {
        val state = delegate.emergencyStop()
        delegate.setMasterEnabled(false)
        return state
    }

    private fun persist(state: X88CoreState) {
        val previous = store.load()
        store.save(previous.copy(enabledCapabilities = state.enabledCapabilities))
    }
}
