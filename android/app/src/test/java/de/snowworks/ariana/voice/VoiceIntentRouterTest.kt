package de.snowworks.ariana.voice

import org.junit.Assert.assertEquals
import org.junit.Test

class VoiceIntentRouterTest {

    @Test
    fun recognizesDeviceStatusPhrases() {
        assertEquals(
            VoiceIntentRouter.Intent.DEVICE_STATUS,
            VoiceIntentRouter.classify("Ariana, Gerätestatus."),
        )
        assertEquals(
            VoiceIntentRouter.Intent.DEVICE_STATUS,
            VoiceIntentRouter.classify("Wie ist dein Zugriff auf das Telefon?"),
        )
        assertEquals(
            VoiceIntentRouter.Intent.DEVICE_STATUS,
            VoiceIntentRouter.classify("Sag mir den Bridge-Status"),
        )
    }

    @Test
    fun recognizesOpenSettingsPhrases() {
        assertEquals(
            VoiceIntentRouter.Intent.OPEN_SETTINGS,
            VoiceIntentRouter.classify("Ariana, Einstellungen öffnen."),
        )
        assertEquals(
            VoiceIntentRouter.Intent.OPEN_SETTINGS,
            VoiceIntentRouter.classify("Öffne die Systemeinstellungen"),
        )
    }

    @Test
    fun ordinaryDialogueIsNotCaptured() {
        assertEquals(
            VoiceIntentRouter.Intent.NONE,
            VoiceIntentRouter.classify("Erzähl mir etwas über Musik."),
        )
        assertEquals(
            VoiceIntentRouter.Intent.NONE,
            VoiceIntentRouter.classify("Was hältst du von diesem Bild?"),
        )
    }

    @Test
    fun normalizationHandlesGermanCharactersAndPunctuation() {
        assertEquals(
            "oeffne bitte die geraete einstellungen",
            VoiceIntentRouter.normalize("Öffne bitte die Geräte-Einstellungen!"),
        )
    }
}
