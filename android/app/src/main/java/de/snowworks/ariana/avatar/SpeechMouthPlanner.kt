package de.snowworks.ariana.avatar

/**
 * Lightweight, renderer-neutral mouth-shape planner.
 *
 * Android system TTS can report the text range currently being spoken but it
 * does not expose the generated audio samples to the caller. We therefore use
 * the current spoken range to derive a useful Live2D-style mouth target. A
 * future phoneme/viseme engine can replace this class without changing the
 * presence or renderer APIs.
 */
object SpeechMouthPlanner {

    data class MouthShape(
        val open: Float,
        val form: Float,
    ) {
        fun normalized(): MouthShape = copy(
            open = open.coerceIn(0f, 1f),
            form = form.coerceIn(-1f, 1f),
        )
    }

    val REST = MouthShape(open = 0f, form = 0f)
    val GENERIC = MouthShape(open = 0.42f, form = 0f)

    /**
     * Returns a mouth target from the most relevant vowel in [fragment].
     * `form` follows the common Live2D convention: -1 round, +1 wide/smile.
     */
    fun fromText(fragment: String): MouthShape {
        val vowel = fragment
            .lowercase()
            .lastOrNull { it in "aeiouyäöü" }
            ?: return GENERIC

        return when (vowel) {
            'a' -> MouthShape(open = 0.9f, form = 0.05f)
            'e', 'ä' -> MouthShape(open = 0.5f, form = 0.68f)
            'i', 'y' -> MouthShape(open = 0.38f, form = 0.9f)
            'o', 'ö' -> MouthShape(open = 0.68f, form = -0.72f)
            'u', 'ü' -> MouthShape(open = 0.52f, form = -0.92f)
            else -> GENERIC
        }.normalized()
    }
}
