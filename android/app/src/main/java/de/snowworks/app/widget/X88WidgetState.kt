package de.snowworks.app.widget

data class X88WidgetState(
    val masterEnabled: Boolean,
    val blocked: Boolean,
    val cameraActive: Boolean,
    val microphoneActive: Boolean,
    val screenActive: Boolean,
) {
    enum class Mode { ACTIVE, OFF, BLOCKED }

    val mode: Mode
        get() = when {
            blocked -> Mode.BLOCKED
            masterEnabled -> Mode.ACTIVE
            else -> Mode.OFF
        }

    val headline: String
        get() = when (mode) {
            Mode.ACTIVE -> "MASTER AN"
            Mode.OFF -> "MASTER AUS"
            Mode.BLOCKED -> "STOP ALL"
        }

    val detail: String
        get() {
            val active = listOfNotNull(
                "CAM".takeIf { cameraActive },
                "MIC".takeIf { microphoneActive },
                "SCREEN".takeIf { screenActive },
            )
            return when {
                blocked -> "Gerätezugriff blockiert · zum Freigeben App öffnen"
                !masterEnabled -> "Lokaler Gerätezugriff ist ausgeschaltet"
                active.isEmpty() -> "Bereit · keine Medien-Sitzung aktiv"
                else -> "Aktiv · ${active.joinToString(" · ")}"
            }
        }
}
