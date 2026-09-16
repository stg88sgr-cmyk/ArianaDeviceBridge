package de.snowworks.ariana.voice

import android.speech.SpeechRecognizer

internal object VoiceRecoveryPolicy {
    fun isRecoverableMessage(message: String): Boolean =
        message.startsWith("Keine Sprache erkannt") ||
            message.startsWith("Ich habe nichts eindeutig verstanden")

    fun shouldRebuildRecognizer(error: Int): Boolean =
        error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY ||
            error == SpeechRecognizer.ERROR_CLIENT ||
            error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED
}
