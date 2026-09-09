package de.snowworks.ariana.presence

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import de.snowworks.ariana.avatar.AvatarDriver
import de.snowworks.ariana.avatar.AvatarState
import de.snowworks.ariana.avatar.IdleMotionPlanner
import de.snowworks.ariana.avatar.SpeechMouthPlanner
import de.snowworks.ariana.voice.ArianaVoiceEngine
import java.io.Closeable
import kotlin.math.sin

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

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var closed = false

    @Volatile
    private var mouthTarget = SpeechMouthPlanner.REST

    @Volatile
    private var mouthAnimationStartedAt = 0L

    @Volatile
    private var baseEyeX = 0f

    @Volatile
    private var baseEyeY = 0f

    @Volatile
    private var baseHeadYaw = 0f

    @Volatile
    private var baseHeadPitch = 0f

    @Volatile
    private var baseHeadRoll = 0f

    private val idleAnimationStartedAt = SystemClock.uptimeMillis()

    private val idleTicker = object : Runnable {
        override fun run() {
            if (closed) return

            val speaking = avatarState.speaking
            val elapsed = SystemClock.uptimeMillis() - idleAnimationStartedAt
            val frame = IdleMotionPlanner.sample(elapsedMs = elapsed, speaking = speaking)

            updateAvatar { current ->
                val moved = current.copy(
                    headYaw = baseHeadYaw + frame.headYawOffset,
                    headPitch = baseHeadPitch + frame.headPitchOffset,
                    headRoll = baseHeadRoll + frame.headRollOffset,
                    eyeX = baseEyeX + frame.eyeXOffset,
                    eyeY = baseEyeY + frame.eyeYOffset,
                    leftEyeOpen = frame.eyeOpen,
                    rightEyeOpen = frame.eyeOpen,
                )

                if (speaking) {
                    moved
                } else {
                    moved.copy(
                        breath = frame.breath,
                        coreGlow = frame.coreGlow,
                    )
                }
            }

            if (!closed) mainHandler.postDelayed(this, IDLE_TICK_MS)
        }
    }

    private val mouthTicker = object : Runnable {
        override fun run() {
            if (closed || !avatarState.speaking) return

            val elapsed = SystemClock.uptimeMillis() - mouthAnimationStartedAt
            val wave = ((sin(elapsed / MOUTH_WAVE_MS) + 1.0) * 0.5).toFloat()
            val pulse = 0.64f + (wave * 0.36f)
            val target = mouthTarget

            updateAvatar {
                it.copy(
                    mouthOpen = (target.open * pulse).coerceIn(0f, 1f),
                    mouthForm = target.form,
                    breath = (0.48f + wave * 0.18f).coerceIn(0f, 1f),
                    coreGlow = (0.78f + wave * 0.18f).coerceIn(0f, 1f),
                )
            }
            if (!closed) mainHandler.postDelayed(this, MOUTH_TICK_MS)
        }
    }

    private val voice = ArianaVoiceEngine(
        context = context,
        listener = object : ArianaVoiceEngine.Listener {
            override fun onReady() {
                voiceListener.onReady()
            }

            override fun onSpeakingChanged(speaking: Boolean) {
                mainHandler.post {
                    if (closed) return@post
                    if (speaking) {
                        mouthTarget = SpeechMouthPlanner.GENERIC
                        mouthAnimationStartedAt = SystemClock.uptimeMillis()
                        updateAvatar {
                            it.copy(
                                speaking = true,
                                mouthOpen = 0.28f,
                                mouthForm = 0f,
                                coreGlow = 0.86f,
                            )
                        }
                        mainHandler.removeCallbacks(mouthTicker)
                        mainHandler.post(mouthTicker)
                    } else {
                        mainHandler.removeCallbacks(mouthTicker)
                        mouthTarget = SpeechMouthPlanner.REST
                        updateAvatar {
                            it.copy(
                                speaking = false,
                                mouthOpen = 0f,
                                mouthForm = 0f,
                                breath = 0.5f,
                                coreGlow = 0.65f,
                            )
                        }
                    }
                }
                voiceListener.onSpeakingChanged(speaking)
            }

            override fun onUtteranceRange(text: String, start: Int, end: Int) {
                if (closed) return
                val safeStart = start.coerceIn(0, text.length)
                val safeEnd = end.coerceIn(safeStart, text.length)
                val fragment = text.substring(safeStart, safeEnd)
                mouthTarget = SpeechMouthPlanner.fromText(fragment)
                voiceListener.onUtteranceRange(text, safeStart, safeEnd)
            }

            override fun onError(message: String) {
                voiceListener.onError(message)
            }
        },
    )

    init {
        mainHandler.post(idleTicker)
    }

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
        baseEyeX = x.coerceIn(-1f, 1f)
        baseEyeY = y.coerceIn(-1f, 1f)
        updateAvatar { it.copy(eyeX = baseEyeX, eyeY = baseEyeY) }
    }

    fun setHeadPose(yaw: Float, pitch: Float, roll: Float) {
        baseHeadYaw = yaw.coerceIn(-1f, 1f)
        baseHeadPitch = pitch.coerceIn(-1f, 1f)
        baseHeadRoll = roll.coerceIn(-1f, 1f)
        updateAvatar {
            it.copy(
                headYaw = baseHeadYaw,
                headPitch = baseHeadPitch,
                headRoll = baseHeadRoll,
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
        if (closed) return
        val next = transform(avatarState).normalized()
        avatarState = next
        avatar.apply(next)
    }

    override fun close() {
        closed = true
        mainHandler.removeCallbacksAndMessages(null)
        voice.close()
        avatar.close()
    }

    private companion object {
        const val IDLE_TICK_MS = 64L
        const val MOUTH_TICK_MS = 48L
        const val MOUTH_WAVE_MS = 88.0
    }
}
