package de.snowworks.ariana.avatar

/**
 * Renderer-neutral state for Ariana's visual presence.
 *
 * Values are normalized so VTube Studio, Live2D Cubism and a later native
 * renderer can all consume the same logical state without leaking one
 * renderer's parameter names through the rest of the app.
 */
data class AvatarState(
    val expression: Expression = Expression.NEUTRAL,
    val headYaw: Float = 0f,
    val headPitch: Float = 0f,
    val headRoll: Float = 0f,
    val eyeX: Float = 0f,
    val eyeY: Float = 0f,
    val leftEyeOpen: Float = 1f,
    val rightEyeOpen: Float = 1f,
    val mouthOpen: Float = 0f,
    val breath: Float = 0.5f,
    val coreGlow: Float = 0.65f,
    val speaking: Boolean = false,
) {
    enum class Expression {
        NEUTRAL,
        SOFT_SMILE,
        HAPPY,
        FOCUSED,
        STERN,
        SURPRISED,
    }

    fun normalized(): AvatarState = copy(
        headYaw = headYaw.coerceIn(-1f, 1f),
        headPitch = headPitch.coerceIn(-1f, 1f),
        headRoll = headRoll.coerceIn(-1f, 1f),
        eyeX = eyeX.coerceIn(-1f, 1f),
        eyeY = eyeY.coerceIn(-1f, 1f),
        leftEyeOpen = leftEyeOpen.coerceIn(0f, 1f),
        rightEyeOpen = rightEyeOpen.coerceIn(0f, 1f),
        mouthOpen = mouthOpen.coerceIn(0f, 1f),
        breath = breath.coerceIn(0f, 1f),
        coreGlow = coreGlow.coerceIn(0f, 1f),
    )
}
