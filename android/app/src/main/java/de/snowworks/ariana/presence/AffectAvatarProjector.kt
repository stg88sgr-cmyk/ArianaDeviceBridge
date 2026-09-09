package de.snowworks.ariana.presence

import de.snowworks.ariana.avatar.AvatarState

/** Maps bounded Presence affect signals into visible avatar choices. */
object AffectAvatarProjector {

    fun apply(affect: PresenceAffectState, current: AvatarState): AvatarState {
        val a = affect.normalized()

        val expression = when {
            a.tension >= 0.72f && a.focus >= 0.55f -> AvatarState.Expression.STERN
            a.curiosity >= 0.78f && a.tension < 0.45f -> AvatarState.Expression.SURPRISED
            a.focus >= 0.80f -> AvatarState.Expression.FOCUSED
            a.warmth >= 0.80f && a.playfulness >= 0.58f -> AvatarState.Expression.HAPPY
            a.warmth >= 0.68f -> AvatarState.Expression.SOFT_SMILE
            else -> AvatarState.Expression.NEUTRAL
        }

        val glow = (
            0.48f +
                a.warmth * 0.20f +
                a.confidence * 0.12f +
                a.socialAttention * 0.10f -
                a.tension * 0.08f
            ).coerceIn(0f, 1f)

        return current.copy(
            expression = expression,
            coreGlow = glow,
        ).normalized()
    }
}
