package de.snowworks.ariana.voice

import java.util.Locale

/** Deterministic, offline routing for device-control phrases. */
object VoiceIntentRouter {
    enum class Intent {
        NONE,
        DEVICE_STATUS,
        OPEN_SETTINGS,
    }

    fun classify(text: String): Intent {
        val normalized = normalize(text)

        if (OPEN_SETTINGS_PATTERNS.any(normalized::contains)) {
            return Intent.OPEN_SETTINGS
        }
        if (DEVICE_STATUS_PATTERNS.any(normalized::contains)) {
            return Intent.DEVICE_STATUS
        }
        return Intent.NONE
    }

    fun normalize(text: String): String = text
        .lowercase(Locale.GERMAN)
        .replace("ß", "ss")
        .replace("ä", "ae")
        .replace("ö", "oe")
        .replace("ü", "ue")
        .replace(Regex("[^a-z0-9 ]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()

    private val OPEN_SETTINGS_PATTERNS = listOf(
        "einstellungen oeffnen",
        "oeffne einstellungen",
        "oeffne die einstellungen",
        "android einstellungen",
        "handy einstellungen",
        "telefon einstellungen",
        "systemeinstellungen oeffnen",
        "oeffne systemeinstellungen",
        "oeffne die systemeinstellungen",
    )

    private val DEVICE_STATUS_PATTERNS = listOf(
        "geraetestatus",
        "geraete status",
        "status vom telefon",
        "status des telefons",
        "telefon status",
        "status vom geraet",
        "status des geraets",
        "bridge status",
        "status der bridge",
        "dein geraetezugriff",
        "wie ist dein zugriff",
    )
}
