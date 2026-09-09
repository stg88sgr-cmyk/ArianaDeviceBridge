package de.snowworks.ariana.avatar

/**
 * Hot-swappable avatar output used to move Ariana from the Motion Probe to a
 * real Live2D renderer without restarting the Presence controller.
 *
 * The router owns the currently installed driver, remembers the latest state
 * and visibility, and replays both whenever a new driver is installed.
 */
class AvatarDriverRouter(
    initial: AvatarDriver = AvatarDriver.NOOP,
) : AvatarDriver {

    private val lock = Any()

    @Volatile
    private var delegate: AvatarDriver = initial

    @Volatile
    private var latestState = AvatarState()

    @Volatile
    private var visible = false

    @Volatile
    private var closed = false

    override fun apply(state: AvatarState) {
        val normalized = state.normalized()
        val current = synchronized(lock) {
            if (closed) return
            latestState = normalized
            delegate
        }
        current.apply(normalized)
    }

    override fun show() {
        val current = synchronized(lock) {
            if (closed || visible) return
            visible = true
            delegate
        }
        current.show()
    }

    override fun hide() {
        val current = synchronized(lock) {
            if (closed || !visible) return
            visible = false
            delegate
        }
        current.hide()
    }

    /**
     * Replaces the active renderer and immediately restores the current Ariana
     * state and visibility on the new one. The old renderer is closed after the
     * hand-off completes.
     */
    fun install(next: AvatarDriver) {
        val previous: AvatarDriver
        val state: AvatarState
        val shouldShow: Boolean

        synchronized(lock) {
            if (closed) {
                next.close()
                return
            }
            if (next === delegate) return
            previous = delegate
            delegate = next
            state = latestState
            shouldShow = visible
        }

        next.apply(state)
        if (shouldShow) next.show() else next.hide()
        previous.close()
    }

    fun currentDriver(): AvatarDriver = delegate

    fun currentState(): AvatarState = latestState

    fun isVisible(): Boolean = visible

    override fun close() {
        val current = synchronized(lock) {
            if (closed) return
            closed = true
            val active = delegate
            delegate = AvatarDriver.NOOP
            active
        }
        current.close()
    }
}
