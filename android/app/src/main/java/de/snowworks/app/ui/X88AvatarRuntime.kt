package de.snowworks.app.ui

import android.os.Looper

/**
 * Bridges voice/provider lifecycle events to the X88 renderer.
 *
 * The runtime is deliberately provider-agnostic: callers emit semantic events,
 * and this class alone maps them to visual avatar modes.
 */
class X88AvatarRuntime(
    private val avatar: X88AvatarView,
    private val eventSink: ((X88AvatarEvent) -> Unit)? = null,
) {
    @Volatile
    var state: X88AvatarState = X88AvatarState.IDLE
        private set

    private val mainLooper = Looper.getMainLooper()

    fun dispatch(event: X88AvatarEvent) {
        state = X88AvatarStateReducer.reduce(state, event)
        eventSink?.invoke(event)
        applyState(state)
    }

    fun setState(next: X88AvatarState) {
        state = next
        applyState(next)
    }

    fun syncVoiceState(message: String) {
        when {
            message.contains("höre", ignoreCase = true) ||
                message.contains("lausche", ignoreCase = true) ->
                dispatch(X88AvatarEvent.USER_STARTED_SPEAKING)
            message.contains("verarbeite", ignoreCase = true) ||
                message.contains("denke", ignoreCase = true) ->
                dispatch(X88AvatarEvent.AI_PROCESSING)
            message.contains("sprich", ignoreCase = true) ->
                dispatch(X88AvatarEvent.TTS_STARTED)
        }
    }

    private fun applyState(next: X88AvatarState) {
        val action = {
            avatar.mode = when (next) {
                X88AvatarState.IDLE -> X88AvatarView.Mode.IDLE
                X88AvatarState.LISTENING -> X88AvatarView.Mode.LISTENING
                X88AvatarState.THINKING -> X88AvatarView.Mode.THINKING
                X88AvatarState.SPEAKING -> X88AvatarView.Mode.SPEAKING
                X88AvatarState.ATTENTION -> X88AvatarView.Mode.ATTENTION
                X88AvatarState.STOPPED -> X88AvatarView.Mode.STOPPED
            }
        }
        if (Looper.myLooper() == mainLooper) action() else avatar.post(action)
    }
}
