package de.snowworks.app.ui

/**
 * Maps the existing X-88 UI state machine onto optional glTF animation clips.
 *
 * The current V2 model can be completely static; this policy deliberately treats
 * missing clips as a valid state so the 3D surface can ship before the final rig.
 */
object X88AvatarAnimationPolicy {
    fun candidates(mode: X88AvatarView.Mode): List<String> = when (mode) {
        X88AvatarView.Mode.IDLE -> listOf("Idle_Breath", "Idle", "idle")
        X88AvatarView.Mode.LISTENING -> listOf("Listening", "Listen", "Idle_Breath", "Idle")
        X88AvatarView.Mode.THINKING -> listOf("Thinking", "Think", "Idle_Breath", "Idle")
        X88AvatarView.Mode.SPEAKING -> listOf("Speaking", "Talk", "Talking", "Idle_Breath", "Idle")
        X88AvatarView.Mode.ATTENTION -> listOf("Attention", "Alert", "Idle_Breath", "Idle")
        X88AvatarView.Mode.STOPPED -> emptyList()
    }

    fun pickAnimationIndex(
        mode: X88AvatarView.Mode,
        availableNames: List<String>,
    ): Int? {
        if (mode == X88AvatarView.Mode.STOPPED || availableNames.isEmpty()) return null
        val candidates = candidates(mode)
        return candidates.firstNotNullOfOrNull { wanted ->
            availableNames.indexOfFirst { it.equals(wanted, ignoreCase = true) }
                .takeIf { it >= 0 }
        }
    }
}
