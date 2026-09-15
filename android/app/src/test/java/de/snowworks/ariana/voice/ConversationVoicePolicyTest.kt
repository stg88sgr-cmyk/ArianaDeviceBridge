package de.snowworks.ariana.voice

import org.junit.Assert.assertTrue
import org.junit.Test

class ConversationVoicePolicyTest {
    @Test
    fun transientSpeechMessagesRemainRecoverable() {
        val messages = listOf(
            "Keine Sprache erkannt.",
            "Ich habe nichts eindeutig verstanden.",
        )
        assertTrue(messages.all { VoiceRecoveryPolicy.isRecoverableMessage(it) })
    }

    @Test
    fun hardFailuresDoNotMasqueradeAsRecoverable() {
        val messages = listOf(
            "Mikrofon-Berechtigung fehlt.",
            "Audiofehler bei der Spracherkennung.",
            "Lokaler Spracherkennungsdienst meldet einen Fehler.",
        )
        assertTrue(messages.none { VoiceRecoveryPolicy.isRecoverableMessage(it) })
    }
}
