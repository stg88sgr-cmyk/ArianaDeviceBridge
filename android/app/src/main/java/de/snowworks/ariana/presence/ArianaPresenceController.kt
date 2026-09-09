package de.snowworks.ariana.presence

import android.content.Context
import de.snowworks.ariana.voice.ArianaVoiceEngine
import java.io.Closeable

/**
 * First composition point for Ariana's on-device presence layer.
 *
 * Phase 1 owns voice. Avatar rendering, lip-sync, wake-word and presence UI
 * will attach here later so the rest of the app talks to one stable surface.
 */
class ArianaPresenceController(
    context: Context,
    voiceListener: ArianaVoiceEngine.Listener = ArianaVoiceEngine.Listener.NOOP,
) : Closeable {

    private val voice = ArianaVoiceEngine(context, voiceListener)

    fun say(text: String, interruptCurrentSpeech: Boolean = true): Boolean =
        voice.speak(text = text, flush = interruptCurrentSpeech)

    fun stopSpeaking() {
        voice.stop()
    }

    fun isVoiceReady(): Boolean = voice.isReady()

    override fun close() {
        voice.close()
    }
}
