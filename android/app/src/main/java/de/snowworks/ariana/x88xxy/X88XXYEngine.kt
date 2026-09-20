package de.snowworks.ariana.x88xxy

/**
 * Small, reversible state-machine core for X88 XXY.
 *
 * This layer only coordinates state transitions. Device permissions, model
 * providers, media generation, network access, and persistence stay outside
 * the core so they can be tested and replaced independently.
 */
class X88XXYEngine(
    initialState: X88XXYState = X88XXYState()
) {
    var state: X88XXYState = initialState
        private set

    fun advance(event: String): X88XXYState {
        require(event.isNotBlank()) { "event must not be blank" }

        val nextStage = when (state.stage) {
            X88XXYStage.IDENTITY -> X88XXYStage.CONNECTION
            X88XXYStage.CONNECTION -> X88XXYStage.IVWZ
            X88XXYStage.IVWZ -> X88XXYStage.TRANSMUTATION
            X88XXYStage.TRANSMUTATION -> X88XXYStage.CREATION
            X88XXYStage.CREATION -> X88XXYStage.MANIFESTATION
            X88XXYStage.MANIFESTATION -> X88XXYStage.VERIFICATION
            X88XXYStage.VERIFICATION -> {
                require(state.verified) { "verification required before integration" }
                X88XXYStage.INTEGRATION
            }
            X88XXYStage.INTEGRATION -> X88XXYStage.EVOLUTION
            X88XXYStage.EVOLUTION -> X88XXYStage.IDENTITY
        }

        state = state.copy(
            stage = nextStage,
            revision = state.revision + 1L,
            lastEvent = event
        )
        return state
    }

    fun markVerified(): X88XXYState {
        require(state.stage == X88XXYStage.VERIFICATION) {
            "verification can only be marked in VERIFICATION stage"
        }
        state = state.copy(
            verified = true,
            revision = state.revision + 1L,
            lastEvent = "verification-passed"
        )
        return state
    }

    fun reset(): X88XXYState {
        state = X88XXYState(
            revision = state.revision + 1L,
            lastEvent = "reset"
        )
        return state
    }
}
