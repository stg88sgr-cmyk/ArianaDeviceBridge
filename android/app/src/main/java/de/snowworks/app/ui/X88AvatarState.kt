package de.snowworks.app.ui

/**
 * Pure X88 avatar state model. UI/rendering code consumes this state but does
 * not decide how provider or voice events are produced.
 */
enum class X88AvatarState {
    IDLE,
    LISTENING,
    THINKING,
    SPEAKING,
    ATTENTION,
    STOPPED,
}

enum class X88AvatarEvent {
    USER_STARTED_SPEAKING,
    VOICE_INPUT_RECEIVED,
    AI_PROCESSING,
    AI_RESPONSE_READY,
    TTS_STARTED,
    TTS_FINISHED,
    PROVIDER_ERROR,
    STOP_ALL,
    RECOVERY,
    RESET,
}

object X88AvatarStateReducer {
    fun reduce(current: X88AvatarState, event: X88AvatarEvent): X88AvatarState =
        when (event) {
            X88AvatarEvent.USER_STARTED_SPEAKING -> X88AvatarState.LISTENING
            X88AvatarEvent.VOICE_INPUT_RECEIVED,
            X88AvatarEvent.AI_PROCESSING -> X88AvatarState.THINKING
            X88AvatarEvent.AI_RESPONSE_READY -> X88AvatarState.THINKING
            X88AvatarEvent.TTS_STARTED -> X88AvatarState.SPEAKING
            X88AvatarEvent.TTS_FINISHED,
            X88AvatarEvent.RECOVERY,
            X88AvatarEvent.RESET -> X88AvatarState.IDLE
            X88AvatarEvent.PROVIDER_ERROR -> X88AvatarState.ATTENTION
            X88AvatarEvent.STOP_ALL -> X88AvatarState.STOPPED
        }
}
