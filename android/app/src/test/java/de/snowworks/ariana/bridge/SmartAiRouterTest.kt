package de.snowworks.ariana.bridge

import org.junit.Assert.assertEquals
import org.junit.Test

class SmartAiRouterTest {
    @Test
    fun routesCodeToClaudeClass() {
        assertEquals(
            SmartAiRouter.TaskClass.CODE_ARCHITECTURE,
            SmartAiRouter.classify("Pruefe bitte diesen Kotlin Code und den Gradle Build Fehler"),
        )
    }

    @Test
    fun routesSensitiveTextLocal() {
        assertEquals(
            SmartAiRouter.TaskClass.PRIVATE_LOCAL,
            SmartAiRouter.classify("Das soll nur lokal bleiben und enthaelt mein Passwort"),
        )
    }

    @Test
    fun routesSecondOpinionToReviewClass() {
        assertEquals(
            SmartAiRouter.TaskClass.SECOND_OPINION,
            SmartAiRouter.classify("Mach bitte einen Gegencheck und gib mir eine zweite Meinung"),
        )
    }

    @Test
    fun ordinaryDialogueStaysGeneral() {
        assertEquals(
            SmartAiRouter.TaskClass.GENERAL,
            SmartAiRouter.classify("Wie planen wir den naechsten Schritt?"),
        )
    }
}
