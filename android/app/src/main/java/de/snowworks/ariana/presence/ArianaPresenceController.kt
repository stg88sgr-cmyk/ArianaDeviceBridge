package de.snowworks.ariana.presence

import android.content.Context
import de.snowworks.ariana.avatar.AvatarDriver
import de.snowworks.ariana.avatar.AvatarState
import de.snowworks.ariana.voice.ArianaVoiceEngine
import java.io.Closeable

/**
 * Stable composition point for Ariana's on-device presence layer.
 *
 * Voice, avatar state and later hearing/wake-word components meet here so the
 * rest of the app does not depend on a specific TTS engine or avatar renderer.
 */
class ArianaPresenceController(
    context: Context,
    private val avatar: AvatarDriver = AvatarDriver.NOOP,
    private val voiceListener: ArianaVoiceEngine.Listener = ArianaVoiceEngine.Listener.NOOP,
) : Closeable {

    @Volatile
    private var avatarState = AvatarState()

    private val voice = ArianaVoiceEngine(
        context = context,
        listener = object : ArianaVoiceEngine.Listener {
            override fun onReady() {
                voiceListener.onReady()
            }

            override fun onSpeakingChanged(speaking: Boolean) {
                updateAvatar {
                    it.copy(
                        speaking = speaking,
                        // Binary placeholder until viseme/amplitude lip-sync lands.
                        mouthOpen = if (speaking) 0.35f else 0f,
                        coreGlow = if (speaking) 0.9f else 0.65f,
                    )
                }
                voiceListener.onSpeakingChanged(speaking)
            }

            override fun onError(message: String) {
                voiceListener.onError(message)
            }
        },
    )

    fun say(text: String, interruptCurrentSpeech: Boolean = true): Boolean =
        voice.speak(text = text, flush = interruptCurrentSpeech)

    fun stopSpeaking() {
        voice.stop()
    }

    fun isVoiceReady(): Boolean = voice.isReady()

    fun currentAvatarState(): AvatarState = avatarState

    fun setExpression(expression: AvatarState.Expression) {
        updateAvatar { it.copy(expression = expression) }
    }

    fun setLook(x: Float, y: Float) {
        updateAvatar { it.copy(eyeX = x, eyeY = y) }
    }

    fun setHeadPose(yaw: Float, pitch: Float, roll: Float) {
        updateAvatar {
            it.copy(
                headYaw = yaw,
                headPitch = pitch,
                headRoll = roll,
            )
        }
    }

    fun showAvatar() {
        avatar.show()
        avatar.apply(avatarState)
    }

    fun hideAvatar() {
        avatar.hide()
    }

    @Synchronized
    private fun updateAvatar(transform: (AvatarState) -> AvatarState) {
        val next = transform(avatarState).normalized()
        avatarState = next
        avatar.apply(next)
    }

    override fun close() {
        voice.close()
        avatar.close()
    }
}
