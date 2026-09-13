package de.snowworks.app.ui

import java.util.Locale

/**
 * Deterministic, offline router for the X-88 home surface.
 * It complements the existing VoiceIntentRouter without changing the stable v4.1 screen.
 */
object X88VoiceIntentRouter {

    enum class Intent {
        NONE,
        DEVICE_STATUS,
        OPEN_SETTINGS,
        CAMERA_ON,
        CAMERA_OFF,
        MICROPHONE_ON,
        MICROPHONE_OFF,
        SCREEN_ON,
        SCREEN_OFF,
        STOP_ALL,
        MASTER_ON,
        MASTER_OFF,
    }

    fun classify(text: String): Intent {
        val n = normalize(text)

        return when {
            any(n, "alles stoppen", "stop all", "not aus", "notaus") -> Intent.STOP_ALL

            any(n, "kamera an", "kamera starten", "starte kamera") -> Intent.CAMERA_ON
            any(n, "kamera aus", "kamera stoppen", "stoppe kamera") -> Intent.CAMERA_OFF

            any(
                n,
                "mikrofon an",
                "mikro an",
                "mikrofon starten",
                "starte mikrofon",
            ) -> Intent.MICROPHONE_ON

            any(
                n,
                "mikrofon aus",
                "mikro aus",
                "mikrofon stoppen",
                "stoppe mikrofon",
            ) -> Intent.MICROPHONE_OFF

            any(
                n,
                "bildschirm teilen",
                "bildschirm starten",
                "screen an",
                "screen share starten",
            ) -> Intent.SCREEN_ON

            any(
                n,
                "bildschirm stoppen",
                "bildschirm aus",
                "screen aus",
                "screen share stoppen",
            ) -> Intent.SCREEN_OFF

            any(
                n,
                "master an",
                "geraetezugriff an",
                "geraetezugriff einschalten",
                "ariana einschalten",
            ) -> Intent.MASTER_ON

            any(
                n,
                "master aus",
                "geraetezugriff aus",
                "geraetezugriff ausschalten",
                "ariana geraetezugriff aus",
            ) -> Intent.MASTER_OFF

            any(
                n,
                "einstellungen oeffnen",
                "oeffne einstellungen",
                "android einstellungen",
                "systemeinstellungen oeffnen",
            ) -> Intent.OPEN_SETTINGS

            any(
                n,
                "geraetestatus",
                "geraete status",
                "telefon status",
                "bridge status",
                "status der bridge",
                "wie ist dein zugriff",
                "dein geraetezugriff",
                "status",
            ) -> Intent.DEVICE_STATUS

            else -> Intent.NONE
        }
    }

    fun normalize(text: String): String = text
        .lowercase(Locale.GERMAN)
        .replace('ß', 's')
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private fun any(text: String, vararg patterns: String): Boolean =
        patterns.any(text::contains)
}
