package de.snowworks.ariana.voice

/**
 * Small generation token used to invalidate delayed SpeechRecognizer retries.
 * A delayed callback may only recreate a recognizer while its token is current.
 */
internal class VoiceRetryGate {
    private var generation: Int = 0

    fun next(): Int {
        generation += 1
        return generation
    }

    fun invalidate() {
        generation += 1
    }

    fun isCurrent(token: Int): Boolean = token == generation
}
