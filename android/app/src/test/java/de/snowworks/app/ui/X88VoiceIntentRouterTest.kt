package de.snowworks.app.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class X88VoiceIntentRouterTest {

    @Test
    fun routesCameraOn() {
        assertEquals(
            X88VoiceIntentRouter.Intent.CAMERA_ON,
            X88VoiceIntentRouter.classify("Ariana, Kamera an"),
        )
    }

    @Test
    fun routesMicrophoneOff() {
        assertEquals(
            X88VoiceIntentRouter.Intent.MICROPHONE_OFF,
            X88VoiceIntentRouter.classify("Mikrofon aus"),
        )
    }

    @Test
    fun routesScreenShare() {
        assertEquals(
            X88VoiceIntentRouter.Intent.SCREEN_ON,
            X88VoiceIntentRouter.classify("Bildschirm teilen"),
        )
    }

    @Test
    fun routesStopAll() {
        assertEquals(
            X88VoiceIntentRouter.Intent.STOP_ALL,
            X88VoiceIntentRouter.classify("Alles stoppen"),
        )
    }

    @Test
    fun routesDeviceStatus() {
        assertEquals(
            X88VoiceIntentRouter.Intent.DEVICE_STATUS,
            X88VoiceIntentRouter.classify("Wie ist dein Gerätestatus?"),
        )
    }
}
