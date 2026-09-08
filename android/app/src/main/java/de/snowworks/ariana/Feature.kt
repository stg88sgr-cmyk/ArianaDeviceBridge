package de.snowworks.ariana

enum class Feature(
    val id: String,
    val title: String,
    val purpose: String,
    val needsForegroundService: Boolean,
    val androidSettingsOnly: Boolean,
) {
    CAMERA(
        "camera",
        "Kamera",
        "Ariana darf die Kamera nur nutzen, wenn du eine Sitzung bewusst startest.",
        true,
        false,
    ),
    MICROPHONE(
        "microphone",
        "Mikrofon",
        "Ariana darf das Mikrofon nur für eine von dir gestartete Aufnahme nutzen.",
        true,
        false,
    ),
    NOTIFY_SEND(
        "notify_send",
        "Benachrichtigungen senden",
        "Lokale Hinweise auf diesem Gerät, zum Beispiel für laufende Sitzungen.",
        false,
        false,
    ),
    NOTIFY_READ(
        "notify_read",
        "Benachrichtigungen lesen",
        "Liest Benachrichtigungen anderer Apps nur nach manueller Freigabe in den Systemeinstellungen. Keine Weiterleitung nach außen.",
        false,
        true,
    ),
    SCREEN(
        "screen",
        "Bildschirmübertragung",
        "Jede Sitzung braucht den MediaProjection-Systemdialog. Das Ergebnis wird nicht gespeichert.",
        true,
        false,
    ),
    FILES(
        "files",
        "Dateien und Ordner",
        "Zugriff nur auf Einträge, die du im Systemdialog auswählst.",
        false,
        false,
    ),
    LOCATION(
        "location",
        "Standort",
        "Nur während der Nutzung. Kein Hintergrund-Standort.",
        false,
        false,
    ),
    BLUETOOTH(
        "bluetooth",
        "Bluetooth",
        "Nur wenn eine vorhandene Funktion ein gekoppeltes Gerät braucht.",
        false,
        false,
    ),
}
