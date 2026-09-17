package de.snowworks.ariana.system

/**
 * Persisted X88 preferences intentionally exclude active sessions, emergency
 * state and a live master-enable flag. Runtime authority must be re-established
 * after process or device restart.
 */
data class X88PersistentState(
    val enabledCapabilities: Set<X88Capability> = emptySet(),
    val updateChannel: X88UpdateChannel = X88UpdateChannel.STABLE,
)

interface X88StateStore {
    fun load(): X88PersistentState
    fun save(state: X88PersistentState)
    fun clear()
}
