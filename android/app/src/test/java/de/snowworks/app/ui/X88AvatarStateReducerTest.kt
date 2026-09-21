package de.snowworks.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class X88AvatarStateReducerTest {
    @Test
    fun conversationLifecycle_mapsToExpectedStates() {
        var state = X88AvatarState.IDLE
        state = X88AvatarStateReducer.reduce(state, X88AvatarEvent.USER_STARTED_SPEAKING)
        assertEquals(X88AvatarState.LISTENING, state)

        state = X88AvatarStateReducer.reduce(state, X88AvatarEvent.VOICE_INPUT_RECEIVED)
        assertEquals(X88AvatarState.THINKING, state)

        state = X88AvatarStateReducer.reduce(state, X88AvatarEvent.TTS_STARTED)
        assertEquals(X88AvatarState.SPEAKING, state)

        state = X88AvatarStateReducer.reduce(state, X88AvatarEvent.TTS_FINISHED)
        assertEquals(X88AvatarState.IDLE, state)
    }

    @Test
    fun safetyEvents_overrideNormalConversationState() {
        assertEquals(
            X88AvatarState.STOPPED,
            X88AvatarStateReducer.reduce(
                X88AvatarState.SPEAKING,
                X88AvatarEvent.STOP_ALL,
            ),
        )
        assertEquals(
            X88AvatarState.ATTENTION,
            X88AvatarStateReducer.reduce(
                X88AvatarState.THINKING,
                X88AvatarEvent.PROVIDER_ERROR,
            ),
        )
        assertEquals(
            X88AvatarState.IDLE,
            X88AvatarStateReducer.reduce(
                X88AvatarState.ATTENTION,
                X88AvatarEvent.RECOVERY,
            ),
        )
    }
}
